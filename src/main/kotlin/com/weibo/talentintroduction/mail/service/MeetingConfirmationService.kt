package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 专家会议确认 · 只读生成器（fast-p 01）。
 *
 * 全部入口（options/timeZones/preview/validateAndBuild）都只读：
 * 不触发 SMTP、send_attempt、mail_record、meeting_schedule、processing 状态、
 * 绑定或计数写入（I-1）。目标 processing 必须存在且其 expertContactId 等于
 * 请求 contactId；账号取 requested 非空值，否则来信账号（与 Pending 相同）。
 *
 * 本服务不调用需要写库的旧 meeting 服务；结构化配置是唯一输入，客户端不能
 * 直传任意 ICS/filename/MIME（I-5）；同源时间对生成文本/中国时间/ICS（I-3/I-6）。
 *
 * 正文来源唯一：通用 `MEETING_INVITATION` 模板（`renderByCode`，含回复片段与
 * 自定义文本），变量只额外覆盖 `meeting_time`/`zoom_url`（I-1/I-2）。
 */
@Service
class MeetingConfirmationService(
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailComposeTemplateService: MailComposeTemplateService,
    private val mailContentService: MailContentService,
    private val mailVariableService: MailVariableService
) {

    fun options(processingId: Long, contactId: Long, senderAccountCode: String?): MeetingOptionsResponse {
        val processing = requireProcessingForContact(processingId, contactId)
        findContact(contactId)
        val account = resolveAccount(processing.senderAccountCode, senderAccountCode)
        return MeetingOptionsResponse(
            targetKey = targetKey(contactId, account.accountCode),
            resolvedAccountCode = account.accountCode,
            generatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            defaultZoneId = MeetingConfirmationDomain.DEFAULT_ZONE_ID
        )
    }

    fun timeZones(date: LocalDate): List<MeetingTimeZoneOption> {
        val noonUtc = date.atTime(12, 0).toInstant(ZoneOffset.UTC)
        val zoneIds = catalogZoneIds()
        val orderedIds = COMMON_ZONE_IDS.filter { it in zoneIds } +
            (zoneIds - COMMON_ZONE_IDS.toSet()).sorted()
        return orderedIds.map { id ->
            val offset = ZoneId.of(id).rules.getOffset(noonUtc)
            MeetingTimeZoneOption(
                id = id,
                labelZh = ZONE_CHINESE[id] ?: id,
                aliases = ZONE_ALIASES[id]?.split(' ')?.filter { it.isNotBlank() } ?: emptyList(),
                offsetLabel = formatUtcOffset(offset.totalSeconds),
                offsetSeconds = offset.totalSeconds
            )
        }
    }

    fun preview(processingId: Long, request: MeetingPreviewRequest): MeetingPreviewResponse {
        val processing = requireProcessingForContact(processingId, request.contactId)
        val contact = findContact(request.contactId)
        val account = resolveAccount(processing.senderAccountCode, request.senderAccountCode)
        return validateAndBuild(processingId, contact, account, request.meeting)
    }

    /**
     * 纯时间/模板生成边界：preview 先读目标/账号再调用本方法；03 传已解析的
     * 真实实例并在本方法内再次校验 processing 归属（IP-2）。
     *
     * 正文只走通用 `MEETING_INVITATION` 模板链路（I-1），变量只额外覆盖
     * `meeting_time`/`zoom_url`（I-2）；输入里的旧模板/称呼/签名字段不再读取。
     */
    fun validateAndBuild(
        processingId: Long,
        contact: ExpertContact,
        account: MailSenderAccount,
        input: MeetingInput
    ): MeetingPreviewResponse {
        val contactId = contact.id ?: throw IllegalArgumentException("Expert contact id is required")
        requireProcessingForContact(processingId, contactId)

        val zoomUrl = validateZoomUrl(input.zoomUrl)
        val zoneIdRaw = validateZoneId(input.zoneId)
        val zone = ZoneId.of(zoneIdRaw)
        val startLocal = parseLocalDateTime(input.startLocal)
        val endLocal = parseLocalDateTime(input.endLocal)
        validateYearRange(startLocal, endLocal)

        val startZoned = resolveLocalTime(zone, startLocal)
        val endZoned = resolveLocalTime(zone, endLocal)
        val startInstant = startZoned.toInstant()
        val endInstant = endZoned.toInstant()
        val durationMinutes = Duration.between(startInstant, endInstant).toMinutes()
        if (durationMinutes !in 1..MAX_DURATION_MINUTES) {
            throw IllegalArgumentException(MSG_DURATION_INVALID)
        }

        val meetingTime = meetingTimeText(zoneIdRaw, zone, startZoned, endZoned)
        val chinaTime = chinaTimeText(startInstant, endInstant)

        val variables = meetingTemplateVariables(contact, account, meetingTime, zoomUrl)
        val textBody = renderMeetingBody(contact, variables)
        val htmlBody = renderHtml(textBody, zoomUrl)
        if (htmlBody.length > MAX_BODY_CHARS) {
            throw IllegalArgumentException("会议邮件 HTML 正文不能超过 $MAX_BODY_CHARS 字符")
        }

        val salutation = addressee(variables, contact)
        val signature = signature(variables)
        val normalizedRecipient = contact.expertEmail.lowercase().trim()
        val semanticSha256 = semanticSha256(
            processingId = processingId,
            contactId = contactId,
            accountCode = account.accountCode,
            normalizedRecipient = normalizedRecipient,
            salutation = salutation,
            zoneId = zoneIdRaw,
            startInstant = startInstant,
            endInstant = endInstant,
            zoomUrl = zoomUrl,
            signature = signature
        )
        val uid = semanticSha256.take(32) + "@qingfei-calendar"
        val generatedAt = freezeGeneratedAt(input.generatedAt)
        val icsText = buildIcs(
            uid = uid,
            dtstamp = generatedAt,
            start = startInstant,
            end = endInstant,
            summary = "Meeting with $salutation | Qingfei Tech Talent Team",
            description = "$meetingTime\n\nJoin Zoom meeting:\n$zoomUrl\n\n$signature",
            location = zoomUrl,
            url = zoomUrl
        )
        val icsBytes = icsText.toByteArray(Charsets.UTF_8)
        if (icsBytes.size > MAX_ICS_BYTES) {
            throw IllegalArgumentException("会议日历内容超出 64KiB 限制")
        }
        val sha256 = sha256Hex(icsBytes)
        val filename = buildCalendarFilename(startZoned.toLocalDate(), salutation)

        val echoMeeting = if (input.generatedAt.isBlank()) {
            input.copy(generatedAt = generatedAt.toString())
        } else {
            input.copy(generatedAt = input.generatedAt.trim())
        }
        return MeetingPreviewResponse(
            targetKey = targetKey(contactId, account.accountCode),
            resolvedAccountCode = account.accountCode,
            meeting = echoMeeting,
            textBody = textBody,
            htmlBody = htmlBody,
            meetingTime = meetingTime,
            startUtc = startInstant.toString(),
            endUtc = endInstant.toString(),
            chinaTime = chinaTime,
            durationMinutes = durationMinutes.toInt(),
            attachment = MeetingCalendarAttachment(
                filename = filename,
                contentType = MeetingConfirmationDomain.CALENDAR_CONTENT_TYPE,
                icsText = icsText,
                byteLength = icsBytes.size,
                sha256 = sha256,
                semanticSha256 = semanticSha256
            )
        )
    }

    // ───────────────────────── 身份 / 只读解析 ─────────────────────────

    private fun requireProcessingForContact(processingId: Long, contactId: Long): com.weibo.talentintroduction.mail.domain.InboundMailProcessing {
        val processing = inboundMailProcessingRepository.findById(processingId)
            .orElseThrow { NoSuchElementException("Inbound mail processing not found: $processingId") }
        val boundContactId = processing.expertContactId
            ?: throw IllegalArgumentException("Inbound mail not bound to a contact")
        if (boundContactId != contactId) {
            throw IllegalArgumentException(
                "会议确认对象与来信处理不匹配：processing $processingId 绑定 expertContact $boundContactId，请求 contactId $contactId"
            )
        }
        return processing
    }

    private fun findContact(contactId: Long): ExpertContact =
        expertContactRepository.findById(contactId)
            .orElseThrow { NoSuchElementException("Expert contact not found: $contactId") }

    /** 与 PendingMailOperationService.resolvePendingReplyAccount 同式：requested 非空即用，否则来信账号。 */
    private fun resolveAccount(inboundSenderAccountCode: String, requestedAccountCode: String?): MailSenderAccount =
        mailSenderAccountService.getManualSendAccount(
            requestedAccountCode?.takeIf { it.isNotBlank() } ?: inboundSenderAccountCode
        )

    private fun targetKey(contactId: Long, accountCode: String): String = "$contactId:$accountCode"

    // ───────────────────────── 正文渲染（I-1/I-2） ─────────────────────────

    /**
     * 通用模板变量（与普通人工单发同序）：专家画像 → sender/expert/unsubscribe
     * 变量，再加入会议值。模板配置采用现有 `${meeting_time}`、`${zoom_url}`。
     *
     * I-2：除通用 map 的键之外只允许新增/覆盖 `meeting_time` 与 `zoom_url`；
     * 不注入任何其他全局或会议专用变量。
     */
    private fun meetingTemplateVariables(
        contact: ExpertContact,
        account: MailSenderAccount,
        meetingTime: String,
        zoomUrl: String
    ): Map<String, String> {
        val expert = mailVariableService.resolveExpertProfileFor(contact)
        val base = mailVariableService.buildVariables(
            account, expert, contact.expertEmail, previewFallbacks = false, contact = contact
        )
        return base + mapOf(
            "meeting_time" to meetingTime,
            "zoom_url" to zoomUrl
        )
    }

    /** 唯一正文来源：通用 `MEETING_INVITATION`（REPLY_SNIPPET + CUSTOM_TEXT 全序）。 */
    private fun renderMeetingBody(contact: ExpertContact, variables: Map<String, String>): String {
        val rendered = try {
            mailComposeTemplateService.renderByCode(
                MEETING_INVITATION_TEMPLATE_CODE,
                variables,
                MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)
            )
        } catch (ex: IllegalStateException) {
            throw IllegalArgumentException(MSG_TEMPLATE_UNAVAILABLE)
        }
        val text = rendered.body.trim()
        if (text.isEmpty()) {
            throw IllegalArgumentException(MSG_TEMPLATE_UNAVAILABLE)
        }
        if (text.length > MAX_BODY_CHARS) {
            throw IllegalArgumentException("会议邮件正文不能超过 $MAX_BODY_CHARS 字符")
        }
        return text
    }

    /** ICS 称呼与模板正文同源：ES 画像姓名 → 联系人姓名 → 固定兜底。 */
    private fun addressee(variables: Map<String, String>, contact: ExpertContact): String =
        listOf("expertName", "expertFamilyName")
            .firstNotNullOfOrNull { key -> variables[key]?.trim()?.takeIf { it.isNotBlank() } }
            ?: contact.expertName?.trim()?.takeIf { it.isNotBlank() }
            ?: MeetingConfirmationDomain.DEFAULT_ADDRESSEE

    /** ICS 签名与模板变量同源（senderName/senderTitle、teamName/countryName）。 */
    private fun signature(variables: Map<String, String>): String {
        fun value(key: String) = variables[key]?.trim().orEmpty()
        val line1 = listOf(value("senderName"), value("senderTitle"))
            .filter { it.isNotBlank() }
            .joinToString(", ")
        val line2 = listOf(value("teamName"), value("countryName"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return listOf(line1, line2).filter { it.isNotBlank() }.joinToString("\n")
    }

    // ───────────────────────── 输入校验（I-4/I-5） ─────────────────────────
    private fun validateZoomUrl(raw: String): String {
        val url = raw.trim()
        if (url.isEmpty()) {
            throw IllegalArgumentException("请填写 Zoom 会议链接")
        }
        if (url.length > 2048) {
            throw IllegalArgumentException("Zoom 会议链接不能超过 2048 字符")
        }
        if (url.any { it.isWhitespace() || it.isISOControl() }) {
            throw IllegalArgumentException("Zoom 会议链接不能包含空格或控制字符")
        }
        rejectTemplateChars(url, "Zoom 会议链接")
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw invalidZoomUrl()
        }
        val uri = try {
            URI(url)
        } catch (ex: Exception) {
            throw invalidZoomUrl()
        }
        if (uri.userInfo != null || uri.fragment != null || uri.host == null) {
            throw invalidZoomUrl()
        }
        val host = uri.host.lowercase(Locale.ROOT)
        val allowedHost = ALLOWED_ZOOM_HOSTS.any { host == it || host.endsWith(".$it") }
        if (!allowedHost) {
            throw invalidZoomUrl()
        }
        val path = uri.path ?: ""
        val meetingPath = ZOOM_JOIN_PATH.matches(path) || ZOOM_MY_PATH.matches(path)
        if (!meetingPath) {
            throw invalidZoomUrl()
        }
        return url
    }

    private fun invalidZoomUrl(): IllegalArgumentException =
        IllegalArgumentException("请输入有效的 Zoom 会议链接")

    private fun rejectTemplateChars(value: String, label: String) {
        if (value.contains("\${") || value.contains("{{") || value.contains("}}")) {
            throw IllegalArgumentException("$label 不能包含模板变量字符，防止后续变量渲染二次解释")
        }
    }

    private fun validateZoneId(raw: String): String {
        val zoneId = raw.trim()
        if (zoneId !in catalogZoneIds()) {
            throw IllegalArgumentException(MSG_ZONE_INVALID)
        }
        return zoneId
    }

    private fun parseLocalDateTime(raw: String): LocalDateTime {
        val value = raw.trim()
        if (value.isEmpty() || !LOCAL_DATETIME_PATTERN.matches(value)) {
            throw IllegalArgumentException(MSG_TIME_MISSING)
        }
        return try {
            LocalDateTime.parse(value)
        } catch (ex: DateTimeParseException) {
            throw IllegalArgumentException(MSG_TIME_MISSING)
        }
    }

    private fun validateYearRange(start: LocalDateTime, end: LocalDateTime) {
        if (start.year !in MIN_YEAR..MAX_YEAR || end.year !in MIN_YEAR..MAX_YEAR) {
            throw IllegalArgumentException("会议日期须在 $MIN_YEAR 至 $MAX_YEAR 年之间")
        }
    }

    /** 0 个合法偏移=跳时；2 个=回拨；1 个正常。两端独立换算，不静默取某偏移。 */
    private fun resolveLocalTime(zone: ZoneId, local: LocalDateTime): java.time.ZonedDateTime {
        val offsets = zone.rules.getValidOffsets(local)
        val offset = when (offsets.size) {
            0 -> throw IllegalArgumentException(MSG_DST_GAP)
            1 -> offsets[0]
            else -> throw IllegalArgumentException(MSG_DST_OVERLAP)
        }
        return java.time.ZonedDateTime.of(local, offset)
    }

    private fun freezeGeneratedAt(raw: String): Instant {
        val value = raw.trim()
        if (value.isEmpty()) {
            return Instant.now().truncatedTo(ChronoUnit.SECONDS)
        }
        if (!value.endsWith("Z")) {
            throw IllegalArgumentException("generatedAt 必须是 UTC ISO-8601 时间（秒精度）")
        }
        return try {
            Instant.parse(value).truncatedTo(ChronoUnit.SECONDS)
        } catch (ex: DateTimeParseException) {
            throw IllegalArgumentException("generatedAt 必须是 UTC ISO-8601 时间（秒精度）")
        }
    }

    // ───────────────────────── 时间文本（I-3） ─────────────────────────

    private fun meetingTimeText(zoneIdRaw: String, zone: ZoneId, start: java.time.ZonedDateTime, end: java.time.ZonedDateTime): String {
        val startOffset = start.offset.totalSeconds
        val endOffset = end.offset.totalSeconds
        val offsetInfo = if (startOffset == endOffset) {
            formatUtcOffset(startOffset)
        } else {
            "${formatUtcOffset(startOffset)} → ${formatUtcOffset(endOffset)}"
        }
        val label = zoneLabel(zone, zoneIdRaw)
        val startFullDate = start.format(FULL_DATE_FORMAT)
        val endFullDate = end.format(FULL_DATE_FORMAT)
        val startTime = start.format(TIME_FORMAT)
        val endTime = end.format(TIME_FORMAT)
        return if (start.toLocalDate() == end.toLocalDate()) {
            "$startFullDate, from $startTime to $endTime $label ($offsetInfo)"
        } else {
            "$startFullDate, from $startTime to $endFullDate, at $endTime $label ($offsetInfo)"
        }
    }

    private fun zoneLabel(zone: ZoneId, zoneIdRaw: String): String = when (zoneIdRaw) {
        "Europe/Istanbul" -> "Türkiye Time"
        "Asia/Shanghai" -> "China Standard Time"
        else -> zone.id.substringAfterLast('/').replace('_', ' ') + " Time"
    }

    private fun chinaTimeText(start: Instant, end: Instant): String =
        chinaLine(start) + " – " + chinaLine(end)

    private fun chinaLine(instant: Instant): String {
        val local = instant.atZone(SHANGHAI)
        return String.format(
            "%04d/%02d/%02d %s %02d:%02d",
            local.year, local.monthValue, local.dayOfMonth,
            WEEKDAY_ZH[local.dayOfWeek] ?: "",
            local.hour, local.minute
        )
    }

    private fun formatUtcOffset(totalSeconds: Int): String {
        val sign = if (totalSeconds < 0) "-" else "+"
        val abs = kotlin.math.abs(totalSeconds)
        val hours = abs / 3600
        val minutes = (abs % 3600) / 60
        return if (minutes == 0) {
            "UTC$sign$hours"
        } else {
            "UTC$sign$hours:%02d".format(minutes)
        }
    }

    /** 先 MailContentService.plainTextToHtml 安全转义，再只把已 escape 的完整 Zoom URL 文本替换为同文字安全 a 标签。 */
    private fun renderHtml(textBody: String, zoomUrl: String): String {
        val escapedUrl = escapeHtml(zoomUrl)
        val html = mailContentService.plainTextToHtml(textBody)
        return html.replace(
            escapedUrl,
            """<a href="$escapedUrl" target="_blank" rel="noopener noreferrer">$escapedUrl</a>"""
        )
    }

    /** 与 MailContentService 私有 escapeHtml 完全同序，保证 URL 文本能精确命中。 */
    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    // ───────────────────────── ICS（I-6） ─────────────────────────

    private fun buildIcs(
        uid: String,
        dtstamp: Instant,
        start: Instant,
        end: Instant,
        summary: String,
        description: String,
        location: String,
        url: String
    ): String {
        val contentLines = listOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Qingfei Tech Talent Team//Meeting Confirmation//EN",
            "CALSCALE:GREGORIAN",
            "BEGIN:VEVENT",
            "UID:$uid",
            "DTSTAMP:${basicUtc(dtstamp)}",
            "DTSTART:${basicUtc(start)}",
            "DTEND:${basicUtc(end)}",
            "SUMMARY:${escapeText(summary)}",
            "DESCRIPTION:${escapeText(description)}",
            "LOCATION:${escapeText(location)}",
            "URL:$url",
            "STATUS:CONFIRMED",
            "TRANSP:OPAQUE",
            "END:VEVENT",
            "END:VCALENDAR"
        )
        return contentLines.joinToString("\r\n") { foldContentLine(it) } + "\r\n"
    }

    private fun basicUtc(instant: Instant): String =
        instant.atOffset(ZoneOffset.UTC).format(BASIC_UTC_FORMAT)

    /** 按码点累计 UTF-8 字节折行：超 75 字节换 CRLF+空格续行；续行空格算 1 字节；不截断码点。 */
    private fun foldContentLine(content: String): String {
        if (content.isEmpty()) {
            return ""
        }
        val sb = StringBuilder(content.length + 8)
        var byteCount = 0
        var index = 0
        while (index < content.length) {
            val codePoint = content.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val segment = content.substring(index, index + charCount)
            val segmentBytes = segment.toByteArray(Charsets.UTF_8).size
            if (byteCount + segmentBytes > ICS_LINE_MAX_BYTES) {
                sb.append("\r\n ")
                byteCount = 1
            }
            sb.append(segment)
            byteCount += segmentBytes
            index += charCount
        }
        return sb.toString()
    }

    /** TEXT 转义：先反斜线，再换行/分号/逗号。 */
    private fun escapeText(value: String): String {
        val sb = StringBuilder(value.length + 16)
        value.forEach { ch ->
            when (ch) {
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                ';' -> sb.append("\\;")
                ',' -> sb.append("\\,")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun buildCalendarFilename(date: LocalDate, salutation: String): String {
        val token = salutation
            .map { ch -> if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-') ch else '-' }
            .joinToString("")
            .replace(DASH_RUN, "-")
            .trim('-')
        val safe = token.take(60).trimEnd('-')
        return "meeting-$date-${safe.ifEmpty { "expert" }}.ics"
    }

    // ───────────────────────── 语义摘要（I-6） ─────────────────────────

    /** 长度前缀编码（4 字节大端 UTF-8 字节数），新域标记 meeting-calendar-v1。
     *  字段顺序固定：processingId, contactId, accountCode, normalizedRecipient,
     *  expertSalutation, zoneId, startInstant, endInstant, zoomUrl, senderSignature。 */
    private fun semanticSha256(
        processingId: Long,
        contactId: Long,
        accountCode: String,
        normalizedRecipient: String,
        salutation: String,
        zoneId: String,
        startInstant: Instant,
        endInstant: Instant,
        zoomUrl: String,
        signature: String
    ): String {
        val data = ByteArrayOutputStream()
        appendLengthPrefix(data, "meeting-calendar-v1")
        appendLengthPrefix(data, processingId.toString())
        appendLengthPrefix(data, contactId.toString())
        appendLengthPrefix(data, accountCode)
        appendLengthPrefix(data, normalizedRecipient)
        appendLengthPrefix(data, salutation)
        appendLengthPrefix(data, zoneId)
        appendLengthPrefix(data, startInstant.toString())
        appendLengthPrefix(data, endInstant.toString())
        appendLengthPrefix(data, zoomUrl)
        appendLengthPrefix(data, signature)
        return sha256Hex(data.toByteArray())
    }

    private fun appendLengthPrefix(data: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val len = bytes.size
        data.write(len shr 24)
        data.write((len shr 16) and 0xff)
        data.write((len shr 8) and 0xff)
        data.write(len and 0xff)
        data.write(bytes)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun catalogZoneIds(): Set<String> =
        ZoneId.getAvailableZoneIds()
            .filterTo(mutableSetOf()) { it.contains('/') && !it.startsWith("Etc/") }
            .apply { add("UTC") }

    companion object {
        /** 会议正文唯一来源：通用邀请模板 code（I-1）。 */
        const val MEETING_INVITATION_TEMPLATE_CODE = "MEETING_INVITATION"

        private const val MSG_TEMPLATE_UNAVAILABLE = "会议模板不可用，请重新选择或检查模板变量"
        private const val MSG_ZONE_INVALID = "请选择有效会议时区"
        private const val MSG_TIME_MISSING = "请填写日期和起止时间"
        private const val MSG_DURATION_INVALID = "会议时长须大于 0 且不超过 24 小时"
        private const val MSG_DST_GAP = "该当地时间不存在，请避开夏令时跳时区间"
        private const val MSG_DST_OVERLAP = "该当地时间出现两次，请选择不处于夏令时回拨区间的时间"

        private const val MAX_BODY_CHARS = 20_000
        private const val MAX_DURATION_MINUTES = 1440
        private const val MAX_ICS_BYTES = 64 * 1024
        private const val ICS_LINE_MAX_BYTES = 75
        private const val MIN_YEAR = 1900
        private const val MAX_YEAR = 2100

        private val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")
        private val LOCAL_DATETIME_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$""")
        private val DASH_RUN = Regex("-+")
        private val ZOOM_JOIN_PATH = Regex("""^/j/[^/]+$""")
        private val ZOOM_MY_PATH = Regex("""^/my/[^/]+$""")

        private val ALLOWED_ZOOM_HOSTS = listOf("zoom.us", "zoom.com", "zoom.com.cn")

        private val FULL_DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
        private val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        private val BASIC_UTC_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

        private val WEEKDAY_ZH: Map<DayOfWeek, String> = mapOf(
            DayOfWeek.MONDAY to "周一",
            DayOfWeek.TUESDAY to "周二",
            DayOfWeek.WEDNESDAY to "周三",
            DayOfWeek.THURSDAY to "周四",
            DayOfWeek.FRIDAY to "周五",
            DayOfWeek.SATURDAY to "周六",
            DayOfWeek.SUNDAY to "周日"
        )

        /** 中文展示标签逐字迁移自已确认 preview（zoneChinese 映射）。 */
        private val ZONE_CHINESE: Map<String, String> = mapOf(
            "Europe/Istanbul" to "土耳其 · 伊斯坦布尔",
            "Asia/Shanghai" to "中国 · 北京 / 上海",
            "Europe/London" to "英国 · 伦敦",
            "Europe/Berlin" to "德国 · 柏林",
            "America/New_York" to "美国东部 · 纽约",
            "America/Los_Angeles" to "美国西部 · 洛杉矶",
            "Asia/Tokyo" to "日本 · 东京",
            "Asia/Kolkata" to "印度 · 加尔各答",
            "Asia/Calcutta" to "印度 · 加尔各答",
            "Australia/Sydney" to "澳大利亚 · 悉尼",
            "Asia/Hong_Kong" to "中国 · 香港",
            "Asia/Singapore" to "新加坡",
            "Europe/Paris" to "法国 · 巴黎",
            "Europe/Moscow" to "俄罗斯 · 莫斯科",
            "America/Toronto" to "加拿大 · 多伦多",
            "Pacific/Auckland" to "新西兰 · 奥克兰",
            "Asia/Dubai" to "阿联酋 · 迪拜"
        )

        /** 搜索别名逐字迁移自已确认 preview（zoneAliases 映射），空格分词为 token。 */
        private val ZONE_ALIASES: Map<String, String> = mapOf(
            "Europe/Istanbul" to "土耳其 伊斯坦布尔 Turkey Türkiye Istanbul",
            "Asia/Shanghai" to "中国 北京 上海 China Beijing Shanghai",
            "Europe/London" to "英国 伦敦 UK London",
            "Europe/Berlin" to "德国 柏林 Germany Berlin",
            "America/New_York" to "美国东部 纽约 US Eastern New York",
            "America/Los_Angeles" to "美国西部 洛杉矶 US Pacific Los Angeles",
            "Asia/Tokyo" to "日本 东京 Japan Tokyo",
            "Asia/Kolkata" to "印度 加尔各答 India Kolkata",
            "Asia/Calcutta" to "印度 加尔各答 India Kolkata Calcutta",
            "Australia/Sydney" to "澳大利亚 悉尼 Australia Sydney",
            "Asia/Hong_Kong" to "中国 香港 Hong Kong",
            "Asia/Singapore" to "新加坡 Singapore",
            "Europe/Paris" to "法国 巴黎 France Paris",
            "Europe/Moscow" to "俄罗斯 莫斯科 Russia Moscow",
            "America/Toronto" to "加拿大 多伦多 Canada Toronto",
            "Pacific/Auckland" to "新西兰 奥克兰 New Zealand Auckland",
            "Asia/Dubai" to "阿联酋 迪拜 UAE Dubai"
        )

        /** 固定常用表先行（与映射同序）；余者 id 字典序；UTC 由目录并入。 */
        private val COMMON_ZONE_IDS = listOf(
            "Europe/Istanbul",
            "Asia/Shanghai",
            "Europe/London",
            "Europe/Berlin",
            "America/New_York",
            "America/Los_Angeles",
            "Asia/Tokyo",
            "Asia/Kolkata",
            "Asia/Calcutta",
            "Australia/Sydney",
            "Asia/Hong_Kong",
            "Asia/Singapore",
            "Europe/Paris",
            "Europe/Moscow",
            "America/Toronto",
            "Pacific/Auckland",
            "Asia/Dubai"
        )
    }
}
