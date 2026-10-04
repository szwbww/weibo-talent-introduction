# Code baseline

HEAD: e28e53fd898edd62905a0d45a6bf90396b18b1bf

Line numbers refer to this snapshot; implementation must recheck working-tree changes.

## generator: src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt

```text
42: class MeetingConfirmationService(
43:     private val inboundMailProcessingRepository: InboundMailProcessingRepository,
44:     private val expertContactRepository: ExpertContactRepository,
45:     private val mailSenderAccountService: MailSenderAccountService,
46:     private val mailComposeTemplateService: MailComposeTemplateService,
47:     private val mailContentService: MailContentService,
48:     private val mailVariableService: MailVariableService
49: ) {
50: 
51:     fun options(processingId: Long, contactId: Long, senderAccountCode: String?): MeetingOptionsResponse {
52:         val processing = requireProcessingForContact(processingId, contactId)
53:         findContact(contactId)
54:         val account = resolveAccount(processing.senderAccountCode, senderAccountCode)
55:         return MeetingOptionsResponse(
56:             targetKey = targetKey(contactId, account.accountCode),
57:             resolvedAccountCode = account.accountCode,
58:             generatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
59:             defaultZoneId = MeetingConfirmationDomain.DEFAULT_ZONE_ID
60:         )
61:     }
62: 
63:     fun timeZones(date: LocalDate): List<MeetingTimeZoneOption> {
64:         val noonUtc = date.atTime(12, 0).toInstant(ZoneOffset.UTC)
65:         val zoneIds = catalogZoneIds()
66:         val orderedIds = COMMON_ZONE_IDS.filter { it in zoneIds } +
67:             (zoneIds - COMMON_ZONE_IDS.toSet()).sorted()
68:         return orderedIds.map { id ->
69:             val offset = ZoneId.of(id).rules.getOffset(noonUtc)
70:             val metadata = TIME_ZONE_CATALOG[id]
71:                 ?: throw IllegalStateException("时区中文目录缺少条目：$id")
72:             MeetingTimeZoneOption(
73:                 id = id,
74:                 labelZh = metadata.labelZh,
75:                 aliases = metadata.aliases,
76:                 offsetLabel = formatUtcOffset(offset.totalSeconds),
77:                 offsetSeconds = offset.totalSeconds
78:             )
79:         }
80:     }
81: 
82:     fun preview(processingId: Long, request: MeetingPreviewRequest): MeetingPreviewResponse {
83:         val processing = requireProcessingForContact(processingId, request.contactId)
84:         val contact = findContact(request.contactId)
85:         val account = resolveAccount(processing.senderAccountCode, request.senderAccountCode)
86:         return validateAndBuild(processingId, contact, account, request.meeting)
87:     }
88: 
89:     /**
90:      * 纯时间/模板生成边界：preview 先读目标/账号再调用本方法；03 传已解析的
91:      * 真实实例并在本方法内再次校验 processing 归属（IP-2）。
92:      *
93:      * 正文只走通用 `MEETING_INVITATION` 模板链路（I-1），变量只额外覆盖
94:      * `meeting_time`/`zoom_url`（I-2）；输入里的旧模板/称呼/签名字段不再读取。
95:      */
96:     fun validateAndBuild(
97:         processingId: Long,
98:         contact: ExpertContact,
99:         account: MailSenderAccount,
100:         input: MeetingInput
101:     ): MeetingPreviewResponse {
102:         val contactId = contact.id ?: throw IllegalArgumentException("Expert contact id is required")
103:         requireProcessingForContact(processingId, contactId)
104: 
105:         val zoomUrl = validateZoomUrl(input.zoomUrl)
106:         val zoneIdRaw = validateZoneId(input.zoneId)
107:         val zone = ZoneId.of(zoneIdRaw)
108:         val startLocal = parseLocalDateTime(input.startLocal)
109:         val endLocal = parseLocalDateTime(input.endLocal)
110:         validateYearRange(startLocal, endLocal)
111: 
112:         val startZoned = resolveLocalTime(zone, startLocal)
113:         val endZoned = resolveLocalTime(zone, endLocal)
114:         val startInstant = startZoned.toInstant()
115:         val endInstant = endZoned.toInstant()
116:         val durationMinutes = Duration.between(startInstant, endInstant).toMinutes()
117:         if (durationMinutes !in 1..MAX_DURATION_MINUTES) {
118:             throw IllegalArgumentException(MSG_DURATION_INVALID)
119:         }
120: 
121:         val meetingTime = meetingTimeText(zoneIdRaw, zone, startZoned, endZoned)
122:         val chinaTime = chinaTimeText(startInstant, endInstant)
123: 
124:         val variables = meetingTemplateVariables(contact, account, meetingTime, zoomUrl)
125:         val textBody = renderMeetingBody(contact, variables)
126:         val htmlBody = renderHtml(textBody, zoomUrl)
127:         if (htmlBody.length > MAX_BODY_CHARS) {
128:             throw IllegalArgumentException("会议邮件 HTML 正文不能超过 $MAX_BODY_CHARS 字符")
129:         }
130: 
131:         val salutation = addressee(variables, contact)
132:         val signature = signature(variables)
133:         val normalizedRecipient = contact.expertEmail.lowercase().trim()
134:         val semanticSha256 = semanticSha256(
135:             processingId = processingId,
136:             contactId = contactId,
137:             accountCode = account.accountCode,
138:             normalizedRecipient = normalizedRecipient,
139:             salutation = salutation,
140:             zoneId = zoneIdRaw,
141:             startInstant = startInstant,
142:             endInstant = endInstant,
143:             zoomUrl = zoomUrl,
144:             signature = signature
145:         )
146:         val uid = semanticSha256.take(32) + "@qingfei-calendar"
147:         val generatedAt = freezeGeneratedAt(input.generatedAt)
148:         val icsText = buildIcs(
149:             uid = uid,
150:             dtstamp = generatedAt,
151:             start = startInstant,
152:             end = endInstant,
153:             summary = "Meeting with $salutation | Qingfei Tech Talent Team",
154:             description = "$meetingTime\n\nJoin Zoom meeting:\n$zoomUrl\n\n$signature",
155:             location = zoomUrl,
156:             url = zoomUrl
157:         )
158:         val icsBytes = icsText.toByteArray(Charsets.UTF_8)
159:         if (icsBytes.size > MAX_ICS_BYTES) {
160:             throw IllegalArgumentException("会议日历内容超出 64KiB 限制")
161:         }
162:         val sha256 = sha256Hex(icsBytes)
163:         val filename = buildCalendarFilename(startZoned.toLocalDate(), salutation)
164: 
165:         val echoMeeting = if (input.generatedAt.isBlank()) {
166:             input.copy(generatedAt = generatedAt.toString())
167:         } else {
168:             input.copy(generatedAt = input.generatedAt.trim())
169:         }
170:         return MeetingPreviewResponse(
171:             targetKey = targetKey(contactId, account.accountCode),
172:             resolvedAccountCode = account.accountCode,
173:             meeting = echoMeeting,
174:             textBody = textBody,
175:             htmlBody = htmlBody,
176:             meetingTime = meetingTime,
177:             startUtc = startInstant.toString(),
178:             endUtc = endInstant.toString(),
179:             chinaTime = chinaTime,
180:             durationMinutes = durationMinutes.toInt(),
181:             attachment = MeetingCalendarAttachment(
182:                 filename = filename,
183:                 contentType = MeetingConfirmationDomain.CALENDAR_CONTENT_TYPE,
184:                 icsText = icsText,
185:                 byteLength = icsBytes.size,
186:                 sha256 = sha256,
187:                 semanticSha256 = semanticSha256
188:             )
189:         )
190:     }
191: 
```

```text
330:     private fun validateZoneId(raw: String): String {
331:         val zoneId = raw.trim()
332:         if (zoneId !in catalogZoneIds()) {
333:             throw IllegalArgumentException(MSG_ZONE_INVALID)
334:         }
335:         return zoneId
336:     }
337: 
338:     private fun parseLocalDateTime(raw: String): LocalDateTime {
339:         val value = raw.trim()
340:         if (value.isEmpty() || !LOCAL_DATETIME_PATTERN.matches(value)) {
341:             throw IllegalArgumentException(MSG_TIME_MISSING)
342:         }
343:         return try {
344:             LocalDateTime.parse(value)
345:         } catch (ex: DateTimeParseException) {
346:             throw IllegalArgumentException(MSG_TIME_MISSING)
347:         }
348:     }
349: 
350:     private fun validateYearRange(start: LocalDateTime, end: LocalDateTime) {
351:         if (start.year !in MIN_YEAR..MAX_YEAR || end.year !in MIN_YEAR..MAX_YEAR) {
352:             throw IllegalArgumentException("会议日期须在 $MIN_YEAR 至 $MAX_YEAR 年之间")
353:         }
354:     }
355: 
356:     /** 0 个合法偏移=跳时；2 个=回拨；1 个正常。两端独立换算，不静默取某偏移。 */
357:     private fun resolveLocalTime(zone: ZoneId, local: LocalDateTime): java.time.ZonedDateTime {
358:         val offsets = zone.rules.getValidOffsets(local)
359:         val offset = when (offsets.size) {
360:             0 -> throw IllegalArgumentException(MSG_DST_GAP)
361:             1 -> offsets[0]
362:             else -> throw IllegalArgumentException(MSG_DST_OVERLAP)
363:         }
364:         return java.time.ZonedDateTime.of(local, offset)
365:     }
366: 
367:     private fun freezeGeneratedAt(raw: String): Instant {
368:         val value = raw.trim()
369:         if (value.isEmpty()) {
370:             return Instant.now().truncatedTo(ChronoUnit.SECONDS)
371:         }
372:         if (!value.endsWith("Z")) {
373:             throw IllegalArgumentException("generatedAt 必须是 UTC ISO-8601 时间（秒精度）")
374:         }
375:         return try {
376:             Instant.parse(value).truncatedTo(ChronoUnit.SECONDS)
377:         } catch (ex: DateTimeParseException) {
378:             throw IllegalArgumentException("generatedAt 必须是 UTC ISO-8601 时间（秒精度）")
379:         }
380:     }
381: 
382:     // ───────────────────────── 时间文本（I-3） ─────────────────────────
383: 
384:     private fun meetingTimeText(zoneIdRaw: String, zone: ZoneId, start: java.time.ZonedDateTime, end: java.time.ZonedDateTime): String {
385:         val startOffset = start.offset.totalSeconds
386:         val endOffset = end.offset.totalSeconds
387:         val offsetInfo = if (startOffset == endOffset) {
388:             formatUtcOffset(startOffset)
389:         } else {
390:             "${formatUtcOffset(startOffset)} → ${formatUtcOffset(endOffset)}"
391:         }
392:         val label = zoneLabel(zone, zoneIdRaw)
393:         val startFullDate = start.format(FULL_DATE_FORMAT)
394:         val endFullDate = end.format(FULL_DATE_FORMAT)
395:         val startTime = start.format(TIME_FORMAT)
396:         val endTime = end.format(TIME_FORMAT)
397:         return if (start.toLocalDate() == end.toLocalDate()) {
398:             "$startFullDate, from $startTime to $endTime $label ($offsetInfo)"
399:         } else {
400:             "$startFullDate, from $startTime to $endFullDate, at $endTime $label ($offsetInfo)"
401:         }
402:     }
403: 
404:     private fun zoneLabel(zone: ZoneId, zoneIdRaw: String): String = when (zoneIdRaw) {
405:         "Europe/Istanbul" -> "Türkiye Time"
406:         "Asia/Shanghai" -> "China Standard Time"
407:         else -> zone.id.substringAfterLast('/').replace('_', ' ') + " Time"
408:     }
409: 
410:     private fun chinaTimeText(start: Instant, end: Instant): String =
411:         chinaLine(start) + " – " + chinaLine(end)
412: 
413:     private fun chinaLine(instant: Instant): String {
414:         val local = instant.atZone(SHANGHAI)
415:         return String.format(
416:             "%04d/%02d/%02d %s %02d:%02d",
417:             local.year, local.monthValue, local.dayOfMonth,
418:             WEEKDAY_ZH[local.dayOfWeek] ?: "",
419:             local.hour, local.minute
420:         )
421:     }
422: 
423:     private fun formatUtcOffset(totalSeconds: Int): String {
424:         val sign = if (totalSeconds < 0) "-" else "+"
425:         val abs = kotlin.math.abs(totalSeconds)
426:         val hours = abs / 3600
427:         val minutes = (abs % 3600) / 60
428:         return if (minutes == 0) {
429:             "UTC$sign$hours"
430:         } else {
431:             "UTC$sign$hours:%02d".format(minutes)
432:         }
433:     }
434: 
435:     /** 先 MailContentService.plainTextToHtml 安全转义，再只把已 escape 的完整 Zoom URL 文本替换为同文字安全 a 标签。 */
436:     private fun renderHtml(textBody: String, zoomUrl: String): String {
437:         val escapedUrl = escapeHtml(zoomUrl)
```

```text
529:     private fun buildCalendarFilename(date: LocalDate, salutation: String): String {
530:         val token = salutation
531:             .map { ch -> if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-') ch else '-' }
532:             .joinToString("")
533:             .replace(DASH_RUN, "-")
534:             .trim('-')
535:         val safe = token.take(60).trimEnd('-')
536:         return "meeting-$date-${safe.ifEmpty { "expert" }}.ics"
537:     }
538: 
539:     // ───────────────────────── 语义摘要（I-6） ─────────────────────────
540: 
541:     /** 长度前缀编码（4 字节大端 UTF-8 字节数），新域标记 meeting-calendar-v1。
542:      *  字段顺序固定：processingId, contactId, accountCode, normalizedRecipient,
543:      *  expertSalutation, zoneId, startInstant, endInstant, zoomUrl, senderSignature。 */
544:     private fun semanticSha256(
545:         processingId: Long,
546:         contactId: Long,
547:         accountCode: String,
548:         normalizedRecipient: String,
549:         salutation: String,
550:         zoneId: String,
551:         startInstant: Instant,
552:         endInstant: Instant,
553:         zoomUrl: String,
554:         signature: String
555:     ): String {
556:         val data = ByteArrayOutputStream()
557:         appendLengthPrefix(data, "meeting-calendar-v1")
558:         appendLengthPrefix(data, processingId.toString())
559:         appendLengthPrefix(data, contactId.toString())
560:         appendLengthPrefix(data, accountCode)
561:         appendLengthPrefix(data, normalizedRecipient)
562:         appendLengthPrefix(data, salutation)
563:         appendLengthPrefix(data, zoneId)
564:         appendLengthPrefix(data, startInstant.toString())
565:         appendLengthPrefix(data, endInstant.toString())
566:         appendLengthPrefix(data, zoomUrl)
567:         appendLengthPrefix(data, signature)
568:         return sha256Hex(data.toByteArray())
569:     }
570: 
```

```text
586:     private fun catalogZoneIds(): Set<String> =
587:         ZoneId.getAvailableZoneIds()
588:             .filterTo(mutableSetOf()) { it.contains('/') && !it.startsWith("Etc/") }
589:             .apply { add("UTC") }
590: 
591:     companion object {
592:         /** 会议正文唯一来源：通用邀请模板 code（I-1）。 */
593:         const val MEETING_INVITATION_TEMPLATE_CODE = "MEETING_INVITATION"
594: 
595:         private const val MSG_TEMPLATE_UNAVAILABLE = "会议模板不可用，请重新选择或检查模板变量"
596:         private const val MSG_ZONE_INVALID = "请选择有效会议时区"
597:         private const val MSG_TIME_MISSING = "请填写日期和起止时间"
598:         private const val MSG_DURATION_INVALID = "会议时长须大于 0 且不超过 24 小时"
599:         private const val MSG_DST_GAP = "该当地时间不存在，请避开夏令时跳时区间"
600:         private const val MSG_DST_OVERLAP = "该当地时间出现两次，请选择不处于夏令时回拨区间的时间"
601: 
602:         private const val MAX_BODY_CHARS = 20_000
603:         private const val MAX_DURATION_MINUTES = 1440
604:         private const val MAX_ICS_BYTES = 64 * 1024
605:         private const val ICS_LINE_MAX_BYTES = 75
606:         private const val MIN_YEAR = 1900
607:         private const val MAX_YEAR = 2100
608: 
609:         private val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")
610:         private val LOCAL_DATETIME_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$""")
611:         private val DASH_RUN = Regex("-+")
612:         private val ZOOM_JOIN_PATH = Regex("""^/j/[^/]+$""")
613:         private val ZOOM_MY_PATH = Regex("""^/my/[^/]+$""")
614: 
615:         private val ALLOWED_ZOOM_HOSTS = listOf("zoom.us", "zoom.com", "zoom.com.cn")
616: 
617:         private val FULL_DATE_FORMAT: DateTimeFormatter =
618:             DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
619:         private val TIME_FORMAT: DateTimeFormatter =
620:             DateTimeFormatter.ofPattern("h:mm a", Locale.US)
621:         private val BASIC_UTC_FORMAT: DateTimeFormatter =
622:             DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
623: 
624:         private val WEEKDAY_ZH: Map<DayOfWeek, String> = mapOf(
625:             DayOfWeek.MONDAY to "周一",
626:             DayOfWeek.TUESDAY to "周二",
627:             DayOfWeek.WEDNESDAY to "周三",
628:             DayOfWeek.THURSDAY to "周四",
629:             DayOfWeek.FRIDAY to "周五",
630:             DayOfWeek.SATURDAY to "周六",
631:             DayOfWeek.SUNDAY to "周日"
632:         )
633: 
634:         private data class TimeZoneCatalogEntry(
635:             val labelZh: String,
636:             val aliases: List<String>
637:         )
638: 
639:         /**
640:          * 由 Unicode CLDR 生成并随应用发布的离线目录。运行时不联网；目录缺条目时
641:          * 显式失败，防止 JDK tzdata 新增时区后重新退化为英文 ID。
642:          */
643:         private val TIME_ZONE_CATALOG: Map<String, TimeZoneCatalogEntry> by lazy {
644:             val properties = Properties()
645:             MeetingConfirmationService::class.java.classLoader
646:                 .getResourceAsStream("meeting-timezones-zh.properties")
647:                 ?.use { input ->
648:                     InputStreamReader(input, StandardCharsets.UTF_8).use(properties::load)
649:                 }
650:                 ?: throw IllegalStateException("找不到时区中文目录资源")
651:             properties.stringPropertyNames().associateWith { id ->
652:                 val parts = properties.getProperty(id).split('\t', limit = 2)
653:                 require(parts.size == 2 && parts[0].isNotBlank()) { "时区中文目录格式错误：$id" }
654:                 val aliases = parts[1].split('\u001f').filter { it.isNotBlank() }
655:                 require(aliases.isNotEmpty()) { "时区中文目录缺少搜索别名：$id" }
656:                 TimeZoneCatalogEntry(parts[0], aliases)
657:             }
658:         }
659: 
660:         /** 固定常用表先行（与映射同序）；余者 id 字典序；UTC 由目录并入。 */
661:         private val COMMON_ZONE_IDS = listOf(
662:             "Europe/Istanbul",
663:             "Asia/Shanghai",
664:             "Europe/London",
665:             "Europe/Berlin",
666:             "America/New_York",
667:             "America/Los_Angeles",
668:             "Asia/Tokyo",
669:             "Asia/Kolkata",
670:             "Asia/Calcutta",
671:             "Australia/Sydney",
672:             "Asia/Hong_Kong",
673:             "Asia/Singapore",
674:             "Europe/Paris",
675:             "Europe/Moscow",
676:             "America/Toronto",
677:             "Pacific/Auckland",
678:             "Asia/Dubai"
679:         )
680:     }
681: }
```

## models: src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationModels.kt

```text
15: object MeetingConfirmationDomain {
16:     /** 专用模板 code 与 mailType（同值，I-2）。 */
17:     const val MANUAL_MEETING_CONFIRMATION = "MANUAL_MEETING_CONFIRMATION"
18: 
19:     /** 表单无默认猜测时的明确应用默认时区（I-4），不推断专家所在地。 */
20:     const val DEFAULT_ZONE_ID = "Asia/Shanghai"
21: 
22:     /** 会议邮件与 ICS 正文确实拿不到专家姓名时的唯一兜底称呼（与 `${expertFamilyName|Colleague}` 同词）。 */
23:     const val DEFAULT_ADDRESSEE = "Colleague"
24: 
25:     const val CALENDAR_CONTENT_TYPE = "text/calendar; charset=UTF-8"
26: 
27:     const val CALENDAR_SCHEMA_VERSION = 1
28: 
29:     /** 快照 filename 固定形态（I-6；Codec 校验同款）。 */
30:     val CALENDAR_FILENAME_REGEX = Regex("""^meeting-[0-9]{4}-[0-9]{2}-[0-9]{2}-[A-Za-z0-9-]{1,60}\.ics$""")
31: }
32: 
33: /**
34:  * 会议配置输入（打开/编辑草稿时冻结；不写回模板）。
35:  *
36:  * 正文由通用 `MEETING_INVITATION` 模板链路渲染（I-1/I-2），因此前四项只作为
37:  * 历史草稿/旧请求的兼容字段保留默认值，服务端不再读取；新请求只发时区、
38:  * 起止本地时间、Zoom 链接与 generatedAt。
39:  */
40: data class MeetingInput(
41:     /** 兼容字段：旧专用模板 id；服务端忽略。 */
42:     val templateId: Long = 0L,
43:     /** 兼容字段：旧模板正文快照；服务端忽略。 */
44:     val templateBody: String = "",
45:     /** 兼容字段：旧专家称呼；称呼改用模板变量渲染。 */
46:     val expertSalutation: String = "",
47:     /** 服务端目录中的 IANA 区域或 UTC。 */
48:     val zoneId: String,
49:     /** YYYY-MM-DDTHH:mm，分钟精度。 */
50:     val startLocal: String,
51:     /** YYYY-MM-DDTHH:mm，分钟精度。 */
52:     val endLocal: String,
53:     /** 已创建的 Zoom 会议链接（含入会密码参数）。 */
54:     val zoomUrl: String,
55:     /** 兼容字段：旧发件签名；签名改用模板变量渲染。 */
56:     val senderSignature: String = "",
57:     /** UTC ISO Instant（精度秒）；options 发出、已有草稿复用。 */
58:     val generatedAt: String
59: )
60: 
61: data class MeetingPreviewRequest(
62:     val contactId: Long,
63:     /** 请求账号，可省：缺省用来信账号（与 Pending 相同）。 */
64:     val senderAccountCode: String? = null,
65:     val meeting: MeetingInput
66: )
67: 
68: data class MeetingOptionsResponse(
69:     /** 目标键（联系人+解析后账号作用域），options 与 preview 同源。 */
70:     val targetKey: String,
71:     val resolvedAccountCode: String,
72:     /** UTC ISO Instant（精度秒）。 */
73:     val generatedAt: String,
74:     val defaultZoneId: String
75: )
76: 
77: data class MeetingTimeZoneOption(
78:     val id: String,
79:     /** 中文展示标签；未映射 id 用英文 id 兜底（不宣称全球城市都有中文翻译）。 */
80:     val labelZh: String,
81:     /** 搜索别名（含中英文与常见异名）；未映射 id 为空。 */
82:     val aliases: List<String>,
83:     /** 该 date 12:00 UTC 的偏移展示（仅列表辅助，不作会议时间换算结果）。 */
84:     val offsetLabel: String,
85:     val offsetSeconds: Int
86: )
87: 
88: data class MeetingCalendarAttachment(
89:     val filename: String,
90:     val contentType: String,
```

```text
119:  * 不能作为发送请求附件直传。
120:  */
121: data class CalendarAttachmentSnapshot(
122:     val schemaVersion: Int,
123:     val filename: String,
124:     val contentType: String,
125:     val icsText: String,
126:     val sha256: String,
127:     val semanticSha256: String
128: )
129: 
130: /**
131:  * 快照 JSON 编解码（Jackson Kotlin mapper，固定字段）。
132:  *
133:  * parse 拒绝：空串、schemaVersion!=1、非固定 contentType、非法 filename、
134:  * UTF-8 >64KiB、sha256≠icsText 字节摘要、semanticSha256 非 64 位小写 hex；
135:  * 损坏一律返回 null（不抛到时间线 500）。serialize 只接受同样有效快照，
136:  * 失败立即抛 IllegalArgumentException。
137:  */
138: object CalendarAttachmentCodec {
139: 
140:     private val mapper = ObjectMapper()
141:         .registerModule(KotlinModule.Builder().build())
142:         .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
143: 
144:     private const val MAX_ICS_BYTES = 64 * 1024
145: 
146:     private val HEX64 = Regex("^[0-9a-f]{64}$")
147: 
148:     fun serialize(snapshot: CalendarAttachmentSnapshot): String {
149:         validate(snapshot)
150:         return try {
151:             mapper.writeValueAsString(snapshot)
152:         } catch (ex: Exception) {
153:             throw IllegalArgumentException("Calendar attachment snapshot cannot be serialized", ex)
154:         }
155:     }
156: 
157:     fun parseOrNull(json: String?): CalendarAttachmentSnapshot? {
158:         if (json.isNullOrBlank()) {
159:             return null
160:         }
161:         return try {
162:             val snapshot = mapper.readValue(json, CalendarAttachmentSnapshot::class.java)
163:             validate(snapshot)
164:             snapshot
165:         } catch (ex: Exception) {
166:             null
167:         }
168:     }
169: 
170:     private fun validate(snapshot: CalendarAttachmentSnapshot) {
171:         require(snapshot.schemaVersion == MeetingConfirmationDomain.CALENDAR_SCHEMA_VERSION) {
172:             "Unsupported calendar snapshot schemaVersion: ${snapshot.schemaVersion}"
173:         }
174:         require(snapshot.contentType == MeetingConfirmationDomain.CALENDAR_CONTENT_TYPE) {
175:             "Unsupported calendar contentType: ${snapshot.contentType}"
176:         }
177:         require(MeetingConfirmationDomain.CALENDAR_FILENAME_REGEX.matches(snapshot.filename)) {
178:             "Illegal calendar filename: ${snapshot.filename}"
179:         }
180:         val bytes = snapshot.icsText.toByteArray(Charsets.UTF_8)
181:         require(bytes.size <= MAX_ICS_BYTES) {
182:             "Calendar icsText exceeds 64KiB UTF-8"
183:         }
184:         val expectedSha = sha256Hex(bytes)
185:         require(snapshot.sha256 == expectedSha) {
186:             "Calendar sha256 does not match icsText bytes"
187:         }
188:         require(HEX64.matches(snapshot.semanticSha256)) {
189:             "Calendar semanticSha256 must be 64 lowercase hex characters"
190:         }
191:     }
192: 
193:     private fun sha256Hex(bytes: ByteArray): String {
194:         val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
195:         return digest.joinToString("") { "%02x".format(it) }
196:     }
197: }
```

## controller: src/main/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationController.kt

```text
1: package com.weibo.talentintroduction.mail.controller
2: 
3: import com.weibo.talentintroduction.mail.service.MeetingConfirmationService
4: import com.weibo.talentintroduction.mail.service.MeetingOptionsResponse
5: import com.weibo.talentintroduction.mail.service.MeetingPreviewRequest
6: import com.weibo.talentintroduction.mail.service.MeetingPreviewResponse
7: import com.weibo.talentintroduction.mail.service.MeetingTimeZoneOption
8: import org.springframework.format.annotation.DateTimeFormat
9: import org.springframework.web.bind.annotation.GetMapping
10: import org.springframework.web.bind.annotation.PathVariable
11: import org.springframework.web.bind.annotation.PostMapping
12: import org.springframework.web.bind.annotation.RequestBody
13: import org.springframework.web.bind.annotation.RequestMapping
14: import org.springframework.web.bind.annotation.RequestParam
15: import org.springframework.web.bind.annotation.RestController
16: import java.time.LocalDate
17: 
18: /**
19:  * 专家会议确认 · 只读接口（fast-p 01）：options / time-zones / preview。
20:  *
21:  * 三个入口均沿既有 AuthInterceptor（/api 前缀路径），无登录豁免；只读，不触发任何
22:  * SMTP/写库（I-1）。NoSuchElementException → 404、IllegalArgumentException
23:  * → 400 由 GlobalExceptionHandler 统一映射；本控制器不抛会落入通用 500 的
24:  * ResponseStatusException。
25:  */
26: @RestController
27: @RequestMapping("/api/mail")
28: class MeetingConfirmationController(
29:     private val meetingConfirmationService: MeetingConfirmationService
30: ) {
31: 
32:     @GetMapping("/unmatched-inbound/{processingId}/meeting-confirmation/options")
33:     fun options(
34:         @PathVariable processingId: Long,
35:         @RequestParam("contactId") contactId: Long?,
36:         @RequestParam("senderAccountCode") senderAccountCode: String?
37:     ): MeetingOptionsResponse {
38:         requireNotNull(contactId) { "contactId is required" }
39:         return meetingConfirmationService.options(processingId, contactId, senderAccountCode)
40:     }
41: 
42:     @GetMapping("/meeting-confirmation/time-zones")
43:     fun timeZones(
44:         @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?
45:     ): List<MeetingTimeZoneOption> {
46:         requireNotNull(date) { "date is required, ISO-8601 e.g. 2026-09-11" }
47:         return meetingConfirmationService.timeZones(date)
48:     }
49: 
50:     @PostMapping("/unmatched-inbound/{processingId}/meeting-confirmation/preview")
51:     fun preview(
52:         @PathVariable processingId: Long,
53:         @RequestBody request: MeetingPreviewRequest
54:     ): MeetingPreviewResponse =
55:         meetingConfirmationService.preview(processingId, request)
56: }
```

## country-source: src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationCatalog.kt

```text
1: package com.weibo.talentintroduction.mail.service
2: 
3: import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
4: import org.springframework.core.io.ClassPathResource
5: import org.springframework.stereotype.Service
6: import java.time.DateTimeException
7: import java.time.ZoneId
8: 
9: /**
10:  * 随包国家/时区目录（plan 01 / c1，I-2/I-3）。
11:  *
12:  * 唯一数据来源是 classpath 上的 `contact-country-timezones.json` 快照（证据目录
13:  * `country-timezones.proposed.json` 的逐字副本）：不联网查位置、不解析
14:  * `expert_contact.country` 文本、不回退 UTC、不静默丢弃无法识别的时区、不新增国家
15:  * 自动识别器。加载即校验；任何结构非法都抛出确定的配置错误（启动即失败），
16:  * 绝不提供一个「差不多」的目录。
17:  *
18:  * 顺序即资源快照顺序：国家顺序、国家内时区顺序都不重排、不按偏移去重。
19:  */
20: @Service
21: class ExpertContactLocationCatalog {
22: 
23:     private val catalog: ContactCountryTimezoneCatalog = load()
24: 
25:     /** 完整目录（GET /api/mail/contact-locations/countries 的响应模型）。 */
26:     fun all(): ContactCountryTimezoneCatalog = catalog
27: 
28:     /** 按大写 ISO 二字码查国家；未知代码返回 null（调用方决定 400，绝不猜国家）。 */
29:     fun country(code: String): ContactCountryEntry? = catalog.find(code)
30: 
31:     private fun load(): ContactCountryTimezoneCatalog {
32:         val resource = ClassPathResource(RESOURCE_PATH)
33:         if (!resource.exists()) {
34:             configError("缺少目录资源 $RESOURCE_PATH")
35:         }
36:         val parsed = try {
37:             resource.inputStream.use { jacksonObjectMapper().readValue(it, ContactCountryTimezoneCatalog::class.java) }
38:         } catch (ex: Exception) {
39:             configError("目录资源 $RESOURCE_PATH 无法解析：${ex.message}", ex)
40:         }
41:         validate(parsed)
42:         return parsed
43:     }
44: 
45:     private fun validate(catalog: ContactCountryTimezoneCatalog) {
46:         if (catalog.sourceVersion.isBlank()) configError("目录缺少 sourceVersion")
47:         if (catalog.sourceUrl.isBlank()) configError("目录缺少 sourceUrl")
48:         if (catalog.defaultPolicy.isBlank()) configError("目录缺少 defaultPolicy")
49:         if (catalog.countries.isEmpty()) configError("目录没有任何国家")
50:         val codes = mutableSetOf<String>()
51:         catalog.countries.forEach { country ->
52:             if (!ISO_ALPHA2.matches(country.code)) configError("国家代码不是大写 ISO 二字码：${country.code}")
53:             if (!codes.add(country.code)) configError("国家代码重复：${country.code}")
54:             if (country.labelZh.isBlank()) configError("国家 ${country.code} 缺少展示名")
55:             if (country.zones.isEmpty()) configError("国家 ${country.code} 没有任何时区")
56:             val zoneIds = mutableSetOf<String>()
57:             country.zones.forEach { zone ->
58:                 if (!zoneIds.add(zone.id)) configError("国家 ${country.code} 时区重复：${zone.id}")
59:                 if (zone.labelZh.isBlank()) configError("国家 ${country.code} 的时区 ${zone.id} 缺少展示名")
60:                 try {
61:                     ZoneId.of(zone.id)
62:                 } catch (ex: DateTimeException) {
63:                     configError("国家 ${country.code} 的时区 id 无法解析：${zone.id}", ex)
64:                 }
65:             }
66:             if (country.defaultZoneId !in zoneIds) {
67:                 configError("国家 ${country.code} 的默认时区 ${country.defaultZoneId} 不在该国的 zones 中")
68:             }
69:         }
70:     }
71: 
72:     private fun configError(message: String, cause: Throwable? = null): Nothing =
73:         throw IllegalStateException("$ERROR_PREFIX$message", cause)
74: 
75:     private companion object {
76:         const val RESOURCE_PATH = "contact-country-timezones.json"
77:         const val ERROR_PREFIX = "国家时区目录配置错误："
78:         val ISO_ALPHA2 = Regex("^[A-Z]{2}$")
79:     }
80: }
```

## send-rebuild: src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt

```text
584:         // processing 调用 01 validateAndBuild：digest 不一致 400（配置已变化）；模板禁用
585:         // 沿 01 拒绝；01 生成的完整会议正文必须连续存在于最终 text 与
586:         // htmlToPlainText(finalHtml) 的规范化文本（不是只检索日期/链接关键词），否则 400。
587:         // 最终正文仍取人工编辑器。
588:         val rebuiltMeeting: MeetingPreviewResponse? = if (meeting != null) {
589:             require(source.inboundProcessingId != null) {
590:                 "Meeting attachment requires an inbound processing context"
591:             }
592:             val rebuilt = try {
593:                 meetingConfirmationService.validateAndBuild(
594:                     processingId = source.inboundProcessingId,
595:                     contact = source.contact,
596:                     account = source.account,
597:                     input = meeting
598:                 )
599:             } catch (ex: IllegalArgumentException) {
600:                 throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message ?: "会议配置无效")
601:             }
602:             if (rebuilt.attachment.sha256 != previewAttachmentSha256) {
603:                 throw ResponseStatusException(HttpStatus.BAD_REQUEST, "会议配置已变化，请重新预览")
604:             }
605:             val expected = normalizeBodyText(rebuilt.textBody)
606:             val finalTextNormalized = normalizeBodyText(finalTextBody)
607:             val finalHtmlPlainNormalized = normalizeBodyText(mailContentService.htmlToPlainText(finalHtmlBody))
608:             if (!finalTextNormalized.contains(expected) || !finalHtmlPlainNormalized.contains(expected)) {
609:                 throw ResponseStatusException(
610:                     HttpStatus.BAD_REQUEST,
611:                     "会议正文与附件不一致，请编辑会议后重新生成，或移除日历附件"
612:                 )
613:             }
614:             rebuilt
615:         } else {
616:             null
617:         }
618:         // 01 返回的同一份附件数据只构造一次快照实例，SendPayload 与 ComposedMail 共用。
619:         val calendarSnapshot: CalendarAttachmentSnapshot? = rebuiltMeeting?.let { rebuilt ->
620:             CalendarAttachmentSnapshot(
621:                 schemaVersion = MeetingConfirmationDomain.CALENDAR_SCHEMA_VERSION,
622:                 filename = rebuilt.attachment.filename,
623:                 contentType = rebuilt.attachment.contentType,
624:                 icsText = rebuilt.attachment.icsText,
625:                 sha256 = rebuilt.attachment.sha256,
626:                 semanticSha256 = rebuilt.attachment.semanticSha256
627:             )
628:         }
629:         // fast-p 02 (I-1)：结构化排期输入同样来自这一次 validateAndBuild —— 只取已校验的
630:         // startUtc/endUtc/zoomUrl，绝不重新生成 ICS、不重新取当前时间、不接受浏览器排期
631:         // 对象；与 calendarSnapshot 同有同无（均仅在有 meeting 时为非 null）。
632:         val meetingCalendarInput: MeetingCalendarInput? = rebuiltMeeting?.let { rebuilt ->
633:             MeetingCalendarInput(
634:                 startUtc = Instant.parse(rebuilt.startUtc),
635:                 endUtc = Instant.parse(rebuilt.endUtc),
636:                 meetingLink = rebuilt.meeting.zoomUrl
637:             )
638:         }
639:         val findings = collectSafetyFindings(
640:             verificationText = finalValidationText,
```

```text
688:                 requireNotNull(source.anchorMailRecordId) {
689:                     "Conversation rich reply requires a real anchor mail record"
690:                 }.let { "${ManualReplySendAttemptService.SOURCE_ANCHOR_PREFIX}$it" }
691:             } else null,
692:             idempotencyRequestId = source.requestId,
693:             // 03 (I-3): 与 ComposedMail 共用同一 01 快照实例，绝不独立生成第二份。
694:             calendarAttachment = calendarSnapshot,
695:             // fast-p 02 (I-1)：同一次重算产出的结构化排期输入；无会议恒 null。
696:             meetingEvent = meetingCalendarInput,
697:             // 06 (I-1)：与 ComposedMail.outboundAttachments 同一文件集合导出的有序快照；
698:             // 05 的 finalize 四分支据此写 mail_record.outbound_attachments_json。
```

```text
728:                     text = finalTextBody,
729:                     messageId = claim.messageId,
730:                     // 03 (I-2/I-3): 带会议日历的新分支用真实来信 processing.messageId 作
731:                     // SMTP 线程头（inReplyTo/references 同一来源）；会议只由来信路径产生，
732:                     // 无会议时回落该路径原形态（来信 SMTP 头恒 null；会话回信 = 锚点头），
733:                     // 不因本合并顺带改变旧邮件线程形态。
734:                     inReplyTo = if (calendarSnapshot != null) source.inboundRecord?.messageId
735:                         else source.smtpInReplyTo,
736:                     references = if (calendarSnapshot != null) source.inboundRecord?.messageId
737:                         else source.smtpReferences,
738:                     calendarAttachment = calendarSnapshot,
739:                     // 06 (I-1)：同一文件集合的原件（已核尺寸/hash），SMTP 在 ICS 之后按
740:                     // 选择顺序以 multipart/mixed 携带。
```

## save: src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt

```text
155:         appendLengthPrefix(data, firstSegment)
156:         appendLengthPrefix(data, payload.contactId.toString())
157:         appendLengthPrefix(data, payload.orcidId)
158:         appendLengthPrefix(data, payload.accountCode)
159:         appendLengthPrefix(data, payload.normalizedRecipient)
160:         appendLengthPrefix(data, payload.subject)
161:         appendLengthPrefix(data, payload.finalText)
162:         appendLengthPrefix(data, payload.finalHtml)
163:         appendLengthPrefix(data, payload.inReplyTo ?: "")
164:         appendLengthPrefix(data, payload.canonicalQaRuleIds.joinToString(","))
165:         // fast-p 02 (I-2)：calendar 只在非 null 时于全部原字段之后追加两段
166:         // （域标记 + 01 生成器语义 sha256）；null 时以上 11 段字节流与旧实现
167:         // 逐字一致，shortKey 仍为 MANUAL_RICH:+32。
168:         payload.calendarAttachment?.let { calendar ->
169:             appendLengthPrefix(data, "meeting-calendar-v1")
170:             appendLengthPrefix(data, calendar.semanticSha256)
171:         }
172:         // fast-p 05 (I-2)：通用附件只在非空时于既有全部段之后追加独立版本段
173:         // （域标记 + 数量 + 逐项 filename/contentType/byteLength/sha256，全部长度前缀
174:         // 编码，不用带分隔符的字符串拼接）。上传 UUID/磁盘路径/上传时间不进入
```

```text
340:     fun finalizeSuccess(payload: SendPayload, attemptId: Long, messageId: String): Long {
341:         val attempt = attemptRepository.findById(attemptId).orElseThrow {
342:             IllegalStateException("Mail send attempt not found: $attemptId")
343:         }
344:         require(attempt.status == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
345:             "Cannot finalize success: attempt $attemptId is not DELIVERY_IN_PROGRESS (current: ${attempt.status})"
346:         }
347: 
348:         val now = LocalDateTime.now()
349:         val bodyText = payload.finalText.ifBlank { null }
350:         val mailBody = bodyText ?: payload.finalHtml
351:         // fast-p 02 (I-1/I-4)：与本次发送同一实例的规范快照 JSON；null 显式清空
352:         // （不沿用安全失败记录的旧附件）。
353:         val snapshotJson = payload.calendarAttachment?.let { CalendarAttachmentCodec.serialize(it) }
354:         // fast-p 05 (I-1/I-4)：本次发送的有序通用附件快照 JSON；空列表显式 null
355:         // （唯一 absence 形态，绝不写 []/空串，也不沿用上一次尝试的旧快照）。
356:         val outboundSnapshotJson = payload.outboundAttachments
357:             .takeIf { it.isNotEmpty() }
358:             ?.let { OutboundAttachmentSnapshotCodec.serialize(it) }
359:         val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
360: 
361:         val mailRecord = if (existingRecord != null) {
362:             existingRecord.copy(
363:                 senderAccountCode = payload.accountCode,
364:                 messageId = messageId,
365:                 inReplyTo = payload.inReplyTo,
366:                 subject = payload.subject,
367:                 body = mailBody,
368:                 matchedQaRuleId = payload.primaryRuleId,
369:                 sendStatus = "SENT",
370:                 sentAt = now,
371:                 errorSummary = null,
372:                 calendarAttachmentJson = snapshotJson,
373:                 outboundAttachmentsJson = outboundSnapshotJson
374:             )
375:         } else {
376:             MailRecord(
377:                 expertContactId = payload.contactId,
378:                 direction = "OUTBOUND",
379:                 mailType = "MANUAL_RICH_REPLY",
380:                 senderAccountCode = payload.accountCode,
381:                 triggeredBy = TriggeredBy.OPERATOR,
382:                 sourceInboundId = null,
383:                 messageId = messageId,
384:                 inReplyTo = payload.inReplyTo,
385:                 subject = payload.subject,
386:                 body = mailBody,
387:                 matchedQaRuleId = payload.primaryRuleId,
388:                 sendStatus = "SENT",
389:                 receivedAt = null,
390:                 sentAt = now,
391:                 mailSendAttemptId = attemptId,
392:                 createdAt = existingRecord?.createdAt ?: now,
393:                 calendarAttachmentJson = snapshotJson,
394:                 outboundAttachmentsJson = outboundSnapshotJson
395:             )
396:         }
397:         val savedRecord = mailRecordRepository.save(mailRecord)
398:         val mailRecordId = requireNotNull(savedRecord.id)
399: 
400:         if (payload.canonicalQaRuleIds.isNotEmpty()) {
401:             payload.canonicalQaRuleIds.forEachIndexed { ordinal, qaRuleId ->
402:                 mailRecordQaRuleRepository.save(
403:                     MailRecordQaRule(
404:                         mailRecordId = mailRecordId,
405:                         qaRuleId = qaRuleId,
406:                         ordinal = ordinal
407:                     )
408:                 )
409:             }
410:         }
411: 
412:         // fast-p 02 (I-2)：真实 SENT mail_record 取得 id 之后、attempt 标 SENT 之前创建
413:         // 排期 —— 与成功落库同一 REQUIRES_NEW 事务提交，二者同成功或同回滚（排期写失败
414:         // 绝不返回发送成功）。普通发送/无会议回信 meetingEvent 为 null，完全跳过。
415:         // 01 createFromSentMail 校验来源邮件（OUTBOUND/MANUAL_RICH_REPLY/SENT/带日历附件）
416:         // 与结构化输入；校验失败同样回滚整个成功事务（不静默吞掉排期写入错误）。
417:         payload.meetingEvent?.let { event ->
418:             meetingCalendarService.createFromSentMail(savedRecord, event)
419:         }
420: 
421:         attemptRepository.updateStatusAndError(
422:             id = attemptId,
423:             status = MailSendAttemptStatus.SENT,
424:             errorSummary = null,
```

```text
432:     fun finalizeFailure(
433:         payload: SendPayload,
434:         attemptId: Long,
435:         messageId: String,
436:         resultStatus: String,
437:         errorSummary: String?
438:     ): Long {
439:         val attempt = attemptRepository.findById(attemptId).orElseThrow {
440:             IllegalStateException("Mail send attempt not found: $attemptId")
441:         }
442:         require(attempt.status == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
443:             "Cannot finalize failure: attempt $attemptId is not DELIVERY_IN_PROGRESS (current: ${attempt.status})"
444:         }
445: 
446:         val now = LocalDateTime.now()
447:         val bodyText = payload.finalText.ifBlank { null }
448:         val mailBody = bodyText ?: payload.finalHtml
449:         val boundedError = errorSummary?.take(MAX_ERROR_SUMMARY_LENGTH)
450:         // fast-p 02 (I-1/I-4)：安全失败同样持久化本次快照 JSON；null 显式清空。
451:         val snapshotJson = payload.calendarAttachment?.let { CalendarAttachmentCodec.serialize(it) }
452:         // fast-p 05 (I-1/I-4)：安全失败同样持久化本次有序通用附件快照；空显式 null，
453:         // 绝不沿用上一次尝试（成功或失败）的旧快照。
454:         val outboundSnapshotJson = payload.outboundAttachments
455:             .takeIf { it.isNotEmpty() }
456:             ?.let { OutboundAttachmentSnapshotCodec.serialize(it) }
457:         val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)
458: 
459:         val mailRecord = if (existingRecord != null) {
460:             existingRecord.copy(
461:                 senderAccountCode = payload.accountCode,
462:                 messageId = messageId,
463:                 inReplyTo = payload.inReplyTo,
464:                 subject = payload.subject,
465:                 body = mailBody,
466:                 matchedQaRuleId = payload.primaryRuleId,
467:                 sendStatus = "FAILED",
468:                 sentAt = null,
469:                 errorSummary = boundedError,
470:                 calendarAttachmentJson = snapshotJson,
471:                 outboundAttachmentsJson = outboundSnapshotJson
472:             )
473:         } else {
474:             MailRecord(
475:                 expertContactId = payload.contactId,
476:                 direction = "OUTBOUND",
477:                 mailType = "MANUAL_RICH_REPLY",
478:                 senderAccountCode = payload.accountCode,
479:                 triggeredBy = TriggeredBy.OPERATOR,
480:                 sourceInboundId = null,
481:                 messageId = messageId,
482:                 inReplyTo = payload.inReplyTo,
483:                 subject = payload.subject,
484:                 body = mailBody,
485:                 matchedQaRuleId = payload.primaryRuleId,
486:                 sendStatus = "FAILED",
487:                 receivedAt = null,
488:                 sentAt = null,
489:                 errorSummary = boundedError,
490:                 mailSendAttemptId = attemptId,
491:                 createdAt = now,
492:                 calendarAttachmentJson = snapshotJson,
493:                 outboundAttachmentsJson = outboundSnapshotJson
494:             )
495:         }
496:         val savedRecord = mailRecordRepository.save(mailRecord)
497:         val mailRecordId = requireNotNull(savedRecord.id)
498: 
499:         attemptRepository.updateStatusAndError(
500:             id = attemptId,
```

## timeline: src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt

```text
391:     private fun calendarAttachmentOf(
392:         row: ConversationMessageSqlRow,
393:         record: MailRecord?
394:     ): ConversationCalendarAttachment? {
395:         // I-3/I-4: 只对真实 MAIL_RECORD 行附加 —— INBOUND 行即使与 outbound 数值 id 相同
396:         // 也绝不读 outbound 快照（source 碰撞防护与下载端同款显式归属检查）。
397:         if (row.source != MailboxConversationRepository.SOURCE_MAIL_RECORD ||
398:             record == null ||
399:             record.expertContactId != row.expertContactId ||
400:             record.senderAccountCode != row.accountCode
401:         ) {
402:             return null
403:         }
404:         val snapshot = CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson) ?: return null
405:         val id = requireNotNull(record.id)
406:         return ConversationCalendarAttachment(
407:             filename = snapshot.filename,
408:             byteLength = snapshot.icsText.toByteArray(Charsets.UTF_8).size,
409:             downloadUrl = "$CONVERSATION_API_BASE/${row.expertContactId}/messages/$id/calendar-attachment"
410:         )
411:     }
412: 
```

## download: src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt

```text
40:         private const val SEND_STATUS_SENT = "SENT"
41:         private const val MANUAL_RICH_REPLY_MAIL_TYPE = "MANUAL_RICH_REPLY"
42:         private const val NOT_AVAILABLE_MESSAGE = "日历附件不可用"
43:     }
44: 
45:     @GetMapping("/{contactId}/messages/{mailRecordId}/calendar-attachment")
46:     fun download(
47:         @PathVariable contactId: Long,
48:         @PathVariable mailRecordId: Long
49:     ): ResponseEntity<ByteArray> {
50:         val record = mailRecordRepository.findById(mailRecordId).orElse(null)
51:         val snapshot = if (record == null ||
52:             record.direction != MailboxConversationRepository.DIRECTION_OUTBOUND ||
53:             record.mailType != MANUAL_RICH_REPLY_MAIL_TYPE ||
54:             record.sendStatus != SEND_STATUS_SENT ||
55:             record.expertContactId != contactId
56:         ) {
57:             null
58:         } else {
59:             CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson)
60:         }
61:         val accountInScope = record?.senderAccountCode != null &&
62:             record.senderAccountCode in mailSenderAccountRepository
63:                 .findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
64:                 .map { it.accountCode }
65:                 .toSet()
66:         if (snapshot == null || !accountInScope) {
67:             // 统一 404「日历附件不可用」：走 GlobalExceptionHandler 的 NOT_FOUND 通道
68:             // （与既有 "Expert contact not found" 同款约定），不落通用 500。
69:             throw NoSuchElementException(NOT_AVAILABLE_MESSAGE)
70:         }
71:         val bytes = snapshot.icsText.toByteArray(Charsets.UTF_8)
72:         val headers = HttpHeaders().apply {
73:             contentType = MediaType.parseMediaType(snapshot.contentType)
74:             contentDisposition = ContentDisposition.attachment()
75:                 .filename(snapshot.filename, StandardCharsets.UTF_8)
76:                 .build()
77:             contentLength = bytes.size.toLong()
78:             // 契约固定文案 private,no-store（不允许缓存）。
79:             set(HttpHeaders.CACHE_CONTROL, "private,no-store")
80:         }
81:         headers.set("X-Content-Type-Options", "nosniff")
82:         return ResponseEntity(bytes, headers, HttpStatus.OK)
83:     }
84: }
```

## smtp: src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt

```text
95:             // 选择顺序。构造用 javax.mail MIME API，禁止自拼 MIME 字符串；
96:             // 不重复第二个 html/plain，日历不做 METHOD/文本正文替代；通用附件一律
97:             // Disposition=ATTACHMENT、快照 contentType、快照 filename 的原件字节
98:             // （不改变字节、不展开压缩包、不把任意文件按 text/calendar 发送）。
99:             val mixed = javax.mail.internet.MimeMultipart("mixed")
100:             mixed.addBodyPart(originalBodyPart(wireMail))
101:             if (calendar != null) {
102:                 // fast-p 02 (I-3): 快照 icsText 的 UTF-8 字节
103:                 // (text/calendar; charset=UTF-8、attachment disposition、安全 filename)。
104:                 mixed.addBodyPart(javax.mail.internet.MimeBodyPart().apply {
105:                     dataHandler = javax.activation.DataHandler(
106:                         javax.mail.util.ByteArrayDataSource(
107:                             calendar.icsText.toByteArray(Charsets.UTF_8),
108:                             calendar.contentType
109:                         )
110:                     )
111:                     fileName = calendar.filename
112:                     disposition = javax.mail.Part.ATTACHMENT
113:                 })
114:             }
115:             outboundAttachments.forEach { file ->
116:                 mixed.addBodyPart(javax.mail.internet.MimeBodyPart().apply {
117:                     dataHandler = javax.activation.DataHandler(
118:                         javax.mail.util.ByteArrayDataSource(file.bytes, file.snapshot.contentType)
119:                     )
```

## calendar: src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingCalendarService.kt

```text
48:         val id = repository.insert(
49:             MeetingCalendarEvent(
50:                 expertContactId = contactId,
51:                 startsAtUtc = input.startUtc,
52:                 endsAtUtc = input.endUtc,
53:                 meetingLink = input.meetingLink,
54:                 note = normalizeNote(note),
55:                 status = ACTIVE,
56:                 createdAt = now,
57:                 updatedAt = now
58:             )
59:         )
60:         return repository.findById(id) ?: error("Inserted meeting calendar event not found: $id")
61:     }
62: 
63:     @Transactional(propagation = Propagation.MANDATORY)
64:     fun createFromSentMail(record: MailRecord, input: MeetingCalendarInput): MeetingCalendarEventRepository.EventRow {
65:         val recordId = record.id ?: throw IllegalArgumentException("Sent mail record id is required")
66:         require(record.direction == "OUTBOUND") { "Calendar source mail must be OUTBOUND" }
67:         require(record.mailType == "MANUAL_RICH_REPLY") { "Calendar source mail type must be MANUAL_RICH_REPLY" }
68:         require(record.sendStatus == "SENT") { "Calendar source mail must be SENT" }
69:         require(CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson) != null) {
70:             "Calendar source mail must contain a valid calendar attachment"
71:         }
72:         requireContact(record.expertContactId)
73:         validateInput(input.copy(meetingLink = normalizeMeetingLink(input.meetingLink)))
74: 
75:         repository.findBySourceMailRecordId(recordId)?.let { existing ->
76:             return existingSourceFor(record, existing)
77:         }
78: 
79:         val now = Instant.now().truncatedTo(MICROS)
80:         val event = MeetingCalendarEvent(
81:             expertContactId = record.expertContactId,
82:             sourceMailRecordId = recordId,
83:             startsAtUtc = input.startUtc,
84:             endsAtUtc = input.endUtc,
85:             meetingLink = normalizeMeetingLink(input.meetingLink),
86:             status = ACTIVE,
87:             createdAt = now,
88:             updatedAt = now
89:         )
90:         return try {
91:             val id = repository.insert(event)
92:             repository.findById(id) ?: error("Inserted meeting calendar event not found: $id")
93:         } catch (ex: DataIntegrityViolationException) {
94:             repository.findBySourceMailRecordIdForUpdate(recordId)?.let { existingSourceFor(record, it) } ?: throw ex
95:         }
96:     }
97: 
98:     private fun existingSourceFor(
99:         record: MailRecord,
100:         existing: MeetingCalendarEventRepository.EventRow
101:     ): MeetingCalendarEventRepository.EventRow {
102:         require(existing.event.expertContactId == record.expertContactId) {
```

## frontend: src/main/resources/static/meeting-confirmation.js

```text
104:     function filterZones(zones, query) {
105:         var list = Array.isArray(zones) ? zones : [];
106:         var q = normalizeZoneText(query);
107:         if (!q) return list.slice();
108:         var qSeconds = parseUtcOffsetSeconds(q);
109:         return list.filter(function (zone) {
110:             if (!zone || typeof zone !== "object") return false;
111:             var id = normalizeZoneText(zone.id);
112:             var label = normalizeZoneText(zone.labelZh);
113:             var offset = normalizeZoneText(zone.offsetLabel);
114:             var aliasText = normalizeZoneText((Array.isArray(zone.aliases) ? zone.aliases : []).join(" "));
115:             if (id.indexOf(q) !== -1 || label.indexOf(q) !== -1 ||
116:                 aliasText.indexOf(q) !== -1 || offset.indexOf(q) !== -1) {
117:                 return true;
118:             }
119:             if (qSeconds !== null && Number(zone.offsetSeconds) === qSeconds) return true;
120:             return false;
121:         });
122:     }
123: 
```

```text
258:             open: false,
259:             phase: "idle",
260:             seq: 0,
261:             configSeq: 0,
262:             previewSeq: 0,
263:             options: null,
264:             zones: [],
265:             zoneDate: "",
266:             selectedZone: null,
267:             zoneListOpen: false,
268:             activeZoneIndex: -1,
269:             editing: false,
270:             query: "",
271:             latestPreviewReady: false,
272:             preview: null,
273:             previewPending: false,
274:             previewNetworkError: false,
275:             formRevision: 0,
276:             isEdit: false,
277:             flow: "fill",
278:             mode: "append",
279:             savedMeeting: null,
280:             blobUrl: "",
281:             openCtx: null,
282:             triggerEl: null,
283:             startPassedShown: false,
284:             retryAction: null
285:         };
286:         var previewTimer = null;
287:         var loadStatusEl = null;
288:         var controllerHandle = null;
289: 
290:         function el(id) {
291:             if (!doc || typeof doc.getElementById !== "function") return null;
292:             return doc.getElementById(id);
293:         }
294: 
```

```text
403:         function zoneLabel(zone) {
404:             if (!zone) return "";
405:             var offset = zone.offsetLabel ? " (" + zone.offsetLabel + ")" : "";
406:             return String(zone.labelZh || zone.id || "") + offset;
407:         }
408: 
409:         function renderZoneOptions() {
410:             var list = el("meetingZoneOptions");
411:             var input = el("meetingZoneSearch");
412:             if (!list || !input) return;
413:             var zones = filterZones(state.zones, state.query);
414:             if (zones.length === 0) {
415:                 list.innerHTML = '<div class="meeting-zone-empty">没有匹配的时区，请尝试英文城市名或 UTC+3。</div>';
416:                 input.setAttribute("aria-expanded", "true");
417:                 input.removeAttribute("aria-activedescendant");
418:                 list.hidden = false;
419:                 return;
420:             }
421:             var selectedId = state.selectedZone ? state.selectedZone.id : "";
422:             var html = zones.map(function (zone, index) {
423:                 var selected = String(zone.id) === String(selectedId);
424:                 var offset = zone.offsetLabel || "";
425:                 var check = selected ? " ✓" : "";
426:                 return '<button type="button" id="meeting-zone-option-' + index +
427:                     '" role="option" aria-selected="' + (selected ? "true" : "false") +
428:                     '" data-zone="' + escapeHtml(zone.id) + '" tabindex="-1">' +
429:                     '<span><span data-role="zone-label">' + escapeHtml(zone.labelZh || zone.id) +
430:                     "</span><small>" + escapeHtml(zone.id) + "</small></span>" +
431:                     '<span data-role="zone-offset">' + escapeHtml(offset) + check + "</span></button>";
432:             }).join("");
433:             list.innerHTML = html;
434:             input.setAttribute("aria-expanded", "true");
435:             list.hidden = false;
436:             updateActiveOption();
437:         }
438: 
439:         function optionButtons() {
440:             var list = el("meetingZoneOptions");
441:             if (!list || typeof list.querySelectorAll !== "function") return [];
442:             return Array.prototype.slice.call(list.querySelectorAll('button[role="option"]'));
443:         }
444: 
445:         function updateActiveOption() {
446:             var input = el("meetingZoneSearch");
447:             var buttons = optionButtons();
448:             buttons.forEach(function (button, index) {
449:                 if (index === state.activeZoneIndex) button.classList.add("focused");
450:                 else button.classList.remove("focused");
451:             });
452:             if (!input) return;
453:             if (state.activeZoneIndex >= 0 && state.activeZoneIndex < buttons.length) {
454:                 input.setAttribute("aria-activedescendant", "meeting-zone-option-" + state.activeZoneIndex);
455:             } else {
456:                 input.removeAttribute("aria-activedescendant");
457:             }
458:         }
459: 
460:         function closeZoneList(restoreLabel) {
461:             var list = el("meetingZoneOptions");
462:             var input = el("meetingZoneSearch");
463:             if (list) list.hidden = true;
464:             if (input) {
465:                 input.setAttribute("aria-expanded", "false");
466:                 input.removeAttribute("aria-activedescendant");
467:             }
468:             state.zoneListOpen = false;
469:             state.activeZoneIndex = -1;
470:             if (restoreLabel && !state.editing && input && state.selectedZone) {
471:                 input.value = zoneLabel(state.selectedZone);
472:             }
473:         }
474: 
475:         function openZoneList() {
476:             var list = el("meetingZoneOptions");
477:             if (!list) return;
478:             renderZoneOptions();
479:             state.zoneListOpen = true;
480:         }
481: 
482:         function selectZone(zone) {
483:             state.selectedZone = zone;
484:             state.editing = false;
485:             state.query = "";
486:             state.activeZoneIndex = -1;
487:             var input = el("meetingZoneSearch");
488:             if (input) {
489:                 input.value = zoneLabel(zone);
490:                 input.removeAttribute("aria-activedescendant");
491:             }
492:             closeZoneList(false);
493:             var hint = el("meetingZoneHint");
494:             if (hint) hint.textContent = String(zone.id) + " · 日期和时间均按此时区填写";
495:             formChanged();
496:         }
497: 
```

```text
524:             if (!zoneSelectedValue()) issues.meetingZoneSearch = "请选择会议时区";
525:             var startLocal = startLocalValue();
526:             var endLocal = endLocalValue();
527:             if (!startLocal) {
528:                 issues.meetingDate = issues.meetingDate || "请填写开始日期";
529:                 issues.meetingStart = "请填写开始时间";
530:             }
531:             if (!endLocal) {
532:                 issues.meetingEndDate = issues.meetingEndDate || "请填写结束日期";
533:                 issues.meetingEnd = "请填写结束时间";
534:             }
535:             if (startLocal && endLocal && startLocal >= endLocal) {
536:                 issues.meetingEnd = "结束时间必须晚于开始时间";
537:             }
538:             if (!zoomValue()) issues.meetingUrl = "请填写 Zoom 会议链接";
539:             else if (!validUrl(zoomValue())) issues.meetingUrl = "请输入有效的 Zoom 会议链接";
540:             var complete = !!startLocal && !!endLocal && startLocal < endLocal &&
541:                 !!zoomValue() && !!zoneSelectedValue() && validUrl(zoomValue());
542:             return { complete: complete, issues: issues };
543:         }
544: 
545:         function zoneSelectedValue() {
546:             return state.selectedZone ? String(state.selectedZone.id) : "";
547:         }
548: 
549:         function zoomValue() {
550:             return fieldValue("meetingUrl").trim();
551:         }
552: 
553:         function startLocalValue() {
554:             var date = fieldValue("meetingDate");
555:             var time = fieldValue("meetingStart");
556:             return date && time ? date + "T" + time : "";
557:         }
558: 
559:         function endLocalValue() {
560:             var date = fieldValue("meetingEndDate");
561:             var time = fieldValue("meetingEnd");
562:             return date && time ? date + "T" + time : "";
563:         }
564: 
565:         function applyAriaInvalid(issues) {
566:             if (!dialog || typeof dialog.querySelectorAll !== "function") return;
567:             Array.prototype.forEach.call(dialog.querySelectorAll("[aria-invalid]"), function (node) {
568:                 node.removeAttribute("aria-invalid");
569:             });
570:             Object.keys(issues).forEach(function (id) {
571:                 var node = el(id);
572:                 if (node) node.setAttribute("aria-invalid", "true");
573:             });
574:         }
575: 
576:         // ---- 预览（T2：300ms debounce；序号防过期响应覆盖） ----
577: 
578:         function buildMeetingInput() {
579:             var generatedAt = "";
580:             if (state.savedMeeting && state.savedMeeting.input && state.savedMeeting.input.generatedAt) {
581:                 generatedAt = String(state.savedMeeting.input.generatedAt);
582:             } else if (state.options && state.options.generatedAt) {
583:                 generatedAt = String(state.options.generatedAt);
584:             }
585:             return {
586:                 zoneId: zoneSelectedValue(),
587:                 startLocal: startLocalValue(),
588:                 endLocal: endLocalValue(),
589:                 zoomUrl: zoomValue(),
590:                 generatedAt: generatedAt
591:             };
592:         }
593: 
594:         function canPreview() {
595:             return !!state.options && localIssues().complete;
596:         }
597: 
598:         function schedulePreview() {
599:             if (previewTimer) {
600:                 if (typeof clearTimeout === "function") clearTimeout(previewTimer);
601:                 previewTimer = null;
602:             }
603:             state.formRevision += 1;
604:             state.latestPreviewReady = false;
605:             state.preview = null;
606:             disableDownloadLink();
607:             setPreviewPaneIdle();
608:             var apply = applyButton();
609:             if (apply) apply.disabled = true;
610:             applyAriaInvalid(localIssues().issues);
```

```text
792:                 refreshLoadStatus(null, null);
793:             }
794:             schedulePreview();
795:         }
796: 
797:         function onMeetingDateChanged() {
798:             var date = fieldValue("meetingDate");
799:             var endValue = fieldValue("meetingEndDate");
800:             if (date && (!endValue || endValue < date)) {
801:                 var endDate = el("meetingEndDate");
802:                 if (endDate) endDate.value = date;
803:             }
804:             var zoneDate = date || chinaToday();
805:             if (zoneDate !== state.zoneDate) {
806:                 loadZones(zoneDate);
807:             }
808:             formChanged();
809:         }
810: 
811:         function chinaToday() {
812:             try {
813:                 var fmt = new Intl.DateTimeFormat("en-CA", {
814:                     timeZone: "Asia/Shanghai",
815:                     year: "numeric", month: "2-digit", day: "2-digit"
816:                 });
817:                 var parts = fmt.formatToParts(new Date());
818:                 var map = {};
819:                 parts.forEach(function (part) { map[part.type] = part.value; });
820:                 if (map.year && map.month && map.day) {
821:                     return map.year + "-" + map.month + "-" + map.day;
822:                 }
823:             } catch (e) { /* fallthrough */ }
824:             var now = new Date();
825:             var month = String(now.getMonth() + 1);
826:             var day = String(now.getDate());
827:             return now.getFullYear() + "-" + (month.length === 1 ? "0" + month : month) + "-" +
828:                 (day.length === 1 ? "0" + day : day);
829:         }
830: 
831:         // ---- 配置加载（options / time-zones 并行；seq 保护） ----
832: 
833:         function loadZones(zoneDate) {
834:             if (!zoneDate) return;
835:             var mySeq = ++state.configSeq;
836:             state.zoneDate = zoneDate;
837:             var url = "/api/mail/meeting-confirmation/time-zones?date=" + encodeURIComponent(zoneDate);
838:             apiFn(url).then(function (data) {
839:                 if (state.disposed || !state.open || mySeq !== state.configSeq) return;
840:                 var zones = Array.isArray(data) ? data : [];
841:                 state.zones = zones;
842:                 if (state.selectedZone) {
843:                     var kept = null;
844:                     zones.forEach(function (zone) {
845:                         if (String(zone.id) === String(state.selectedZone.id)) kept = zone;
846:                     });
847:                     if (kept) {
848:                         state.selectedZone = kept;
849:                         var input = el("meetingZoneSearch");
850:                         if (input) input.value = zoneLabel(kept);
851:                     }
852:                 }
853:                 if (state.zoneListOpen) renderZoneOptions();
854:             }).catch(function () {
855:                 // 目录重载失败不阻断表单；保留旧目录（I-5 不隐式改 zone）
856:             });
857:         }
858: 
859:         function loadConfig() {
860:             var mySeq = ++state.configSeq;
861:             state.phase = "config-loading";
862:             state.configError = false;
863:             state.options = null;
864:             setFieldsDisabled(true);
865:             showStatus("正在加载会议配置…", null);
866:             var contactId = Number(state.openCtx.contactId);
867:             var processingId = Number(state.openCtx.processingId);
868:             var optsUrl = "/api/mail/unmatched-inbound/" + processingId +
869:                 "/meeting-confirmation/options?contactId=" + contactId +
870:                 "&senderAccountCode=" + encodeURIComponent(state.openCtx.senderAccountCode || "");
871:             var saved = state.savedMeeting && state.savedMeeting.input ? state.savedMeeting.input : null;
872:             var zoneDate = saved && saved.startLocal ? String(saved.startLocal).slice(0, 10) : chinaToday();
873:             state.zoneDate = zoneDate;
874:             var zonesUrl = "/api/mail/meeting-confirmation/time-zones?date=" + encodeURIComponent(zoneDate);
875:             Promise.all([
876:                 apiFn(optsUrl),
877:                 apiFn(zonesUrl)
878:             ]).then(function (results) {
879:                 if (state.disposed || !state.open || mySeq !== state.configSeq) return;
880:                 var optionsData = results[0] || {};
881:                 var zonesData = results[1];
882:                 state.options = optionsData;
883:                 state.zones = Array.isArray(zonesData) ? zonesData : [];
884:                 populateFormFromOptions();
885:                 state.phase = "ready";
886:                 setFieldsDisabled(false);
887:                 refreshLoadStatus(null, null);
888:                 var context = el("meetingContext");
889:                 if (context) {
890:                     context.textContent = state.openCtx.expertLabel + " · 回复账号 " +
891:                         String(optionsData.resolvedAccountCode || state.openCtx.senderAccountCode || "-");
892:                 }
893:                 // 初始无值时先跑一次便利校验/占位（不请求）
894:                 schedulePreview();
895:                 syncApplyState();
896:             }).catch(function () {
897:                 if (state.disposed || !state.open || mySeq !== state.configSeq) return;
898:                 state.phase = "config-error";
899:                 state.configError = true;
900:                 state.options = null;
901:                 state.zones = [];
902:                 setFieldsDisabled(true);
903:                 showStatus("会议配置加载失败，请重试", "config");
904:             });
905:         }
906: 
907:         function populateFormFromOptions() {
908:             var saved = state.savedMeeting && state.savedMeeting.input ? state.savedMeeting.input : null;
909:             var date = el("meetingDate");
910:             var start = el("meetingStart");
911:             var endDate = el("meetingEndDate");
912:             var end = el("meetingEnd");
913:             if (saved) {
914:                 var startLocal = String(saved.startLocal || "");
915:                 var endLocal = String(saved.endLocal || "");
916:                 if (startLocal.indexOf("T") !== -1) {
917:                     if (date) date.value = startLocal.slice(0, 10);
918:                     if (start) start.value = startLocal.slice(11, 16);
919:                 }
920:                 if (endLocal.indexOf("T") !== -1) {
921:                     if (endDate) endDate.value = endLocal.slice(0, 10);
922:                     if (end) end.value = endLocal.slice(11, 16);
923:                 }
924:             } else {
925:                 if (date) date.value = "";
926:                 if (start) start.value = "";
927:                 if (endDate) endDate.value = "";
928:                 if (end) end.value = "";
929:             }
930:             var zoneId = saved ? String(saved.zoneId || "") : String(state.options.defaultZoneId || "");
931:             var urlInput = el("meetingUrl");
932:             if (urlInput) {
933:                 urlInput.value = saved ? String(saved.zoomUrl || "") : "";
934:             }
935:             state.selectedZone = null;
936:             if (zoneId) {
937:                 var found = null;
938:                 (state.zones || []).forEach(function (zone) {
939:                     if (String(zone.id) === zoneId) found = zone;
940:                 });
941:                 if (found) {
942:                     state.selectedZone = found;
943:                 } else {
944:                     // 目录缺该 id：以 id 兜底占位（不发明数据；仍可在预览时报错）
945:                     state.selectedZone = { id: zoneId, labelZh: zoneId, aliases: [], offsetLabel: "", offsetSeconds: 0 };
946:                 }
947:             }
948:             var search = el("meetingZoneSearch");
949:             if (search) search.value = state.selectedZone ? zoneLabel(state.selectedZone) : "";
950:             var hint = el("meetingZoneHint");
951:             if (hint) {
952:                 hint.textContent = state.selectedZone
953:                     ? String(state.selectedZone.id) + " · 日期和时间均按此时区填写"
954:                     : "下方日期和时间均按所选时区填写。";
955:             }
956:         }
957: 
```

```text
1099: 
1100:         function bindFormEvents() {
1101:             var ids = ["meetingStart", "meetingEnd", "meetingEndDate", "meetingUrl"];
1102:             ids.forEach(function (id) {
1103:                 var node = el(id);
1104:                 if (node) node.addEventListener("input", function () { formChanged(); });
1105:             });
1106:             var date = el("meetingDate");
1107:             if (date) date.addEventListener("input", onMeetingDateChanged);
1108:             var insertSelect = el("insertMode");
1109:             if (insertSelect) insertSelect.addEventListener("change", function () {
1110:                 state.mode = String(insertSelect.value || "append");
1111:                 refreshLoadStatus(null, null);
1112:                 syncApplyState();
1113:             });
1114:         }
1115: 
1116:         function dialogClickOutside(event) {
```

```text
1290:                 state.seq += 1;
1291:                 state.openCtx = {
1292:                     ownerKey: String(opts.ownerKey || ""),
1293:                     targetKey: String(opts.targetKey || ""),
1294:                     contactId: opts.contactId,
1295:                     processingId: opts.processingId,
1296:                     senderAccountCode: String(opts.senderAccountCode || ""),
1297:                     expertLabel: String(opts.expertLabel || ""),
1298:                     editorHtml: String(opts.editorHtml || ""),
1299:                     editorText: String(opts.editorText || ""),
1300:                     editorRevision: opts.editorRevision == null ? 0 : Number(opts.editorRevision)
1301:                 };
1302:                 state.savedMeeting = opts.savedMeeting ? deepCopy(opts.savedMeeting) : null;
1303:                 state.isEdit = !!state.savedMeeting;
1304:                 state.selectedZone = null;
1305:                 state.mode = "append";
1306:                 state.startPassedShown = false;
1307:                 state.latestPreviewReady = false;
1308:                 state.preview = null;
1309:                 state.previewNetworkError = false;
1310:                 state.query = "";
1311:                 state.editing = false;
1312:                 state.zoneListOpen = false;
1313:                 revokePreviewBlob();
1314:                 state.triggerEl = null;
1315:                 if (doc.activeElement && doc.activeElement.nodeType === 1) {
1316:                     state.triggerEl = doc.activeElement;
1317:                 }
1318:                 if (!dialog && !buildDialog()) return null;
1319:                 ["meetingDate", "meetingStart", "meetingEndDate", "meetingEnd",
1320:                     "meetingUrl"].forEach(function (id) {
1321:                         var node = el(id);
1322:                         if (node) node.value = "";
1323:                     });
1324:                 var insertSelect = el("insertMode");
1325:                 if (insertSelect) insertSelect.value = "append";
1326:                 state.flow = effectiveFlow();
1327:                 var selectLabel = el("insertModeLabel");
1328:                 if (selectLabel) {
```

```text
1375:     // S-2 弹窗 DOM（层级/id/文案逐字契约；动态容器由绑定表填充）
1376:     // ------------------------------------------------------------------
1377: 
1378:     var DIALOG_HTML =
1379:         '<form id="meetingForm" novalidate>' +
1380:         '<header class="meeting-head">' +
1381:         '<div>' +
1382:         '<h2 id="meetingTitle">专家会议确认</h2>' +
1383:         '<p id="meetingContext">生成确认邮件与日历附件</p>' +
1384:         "</div>" +
1385:         '<button type="button" class="meeting-close" id="closeMeeting" aria-label="关闭会议确认">×</button>' +
1386:         "</header>" +
1387:         '<div class="meeting-grid">' +
1388:         '<div class="meeting-form">' +
1389:         '<p id="meetingLoadStatus" class="meeting-status" role="status" aria-live="polite" hidden>' +
1390:         "<span></span>" +
1391:         '<button type="button" class="meeting-link" id="retryMeeting" hidden>重试</button>' +
1392:         "</p>" +
1393:         '<div class="meeting-zone-field">' +
1394:         '<label id="meetingZoneLabel" for="meetingZoneSearch">会议时区</label>' +
1395:         '<div class="meeting-zone-control">' +
1396:         '<input id="meetingZoneSearch" type="search" role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="meetingZoneOptions" aria-labelledby="meetingZoneLabel" autocomplete="off" placeholder="搜索国家、城市、时区或 UTC 偏移">' +
1397:         '<button type="button" id="toggleZone" aria-label="展开时区选项">⌄</button>' +
1398:         "</div>" +
1399:         '<div id="meetingZoneOptions" class="meeting-zone-options" role="listbox" aria-label="会议时区选项" hidden></div>' +
1400:         '<small id="meetingZoneHint">下方日期和时间均按所选时区填写。</small>' +
1401:         "</div>" +
1402:         '<div class="meeting-fields">' +
1403:         '<label>开始日期<input type="date" id="meetingDate" required></label>' +
1404:         '<label>开始时间<input type="time" id="meetingStart" required step="60"></label>' +
1405:         "</div>" +
1406:         '<div class="meeting-fields">' +
1407:         '<label>结束日期<input type="date" id="meetingEndDate" required></label>' +
1408:         '<label>结束时间<input type="time" id="meetingEnd" required step="60"></label>' +
1409:         "</div>" +
1410:         '<div class="meeting-clock" id="meetingClock" aria-live="polite"></div>' +
1411:         '<label>Zoom 会议链接<input type="url" id="meetingUrl" required maxlength="2048" placeholder="https://zoom.us/j/…">' +
1412:         "<small>粘贴已创建的会议链接，包含入会密码参数。</small>" +
1413:         "</label>" +
1414:         '<label id="insertModeLabel" hidden>正文已有内容<select id="insertMode">' +
1415:         '<option value="append">保留原文，追加确认邮件</option>' +
1416:         '<option value="replace">替换整篇正文</option>' +
1417:         "</select>" +
1418:         "<small>请检查原文是否包含其他会议时间。</small>" +
1419:         "</label>" +
1420:         "</div>" +
```

## host: src/main/resources/static/mailbox-chat.js

```text
3834:         // S-5（fast-p 04）：仅新 calendarAttachment 非空的 SENT OUTBOUND MAIL_RECORD，
3835:         // 在 bodyHtml 后、原 attachmentHtml 前插已发送日历卡；元数据只消费 03 响应。
3836:         function contextPathValue() {
3837:             return (instance.options && instance.options.contextPath)
3838:                 ? String(instance.options.contextPath)
3839:                 : "";
3840:         }
3841: 
3842:         function sentMeetingAttachmentHtml(message) {
3843:             const ca = message.calendarAttachment;
3844:             if (!ca) return "";
3845:             const sizeKb = (Number(ca.byteLength) || 0) / 1024;
3846:             const metaText = "日历事件 · " + sizeKb.toFixed(1) + " KB";
3847:             const href = contextPathValue() + String(ca.downloadUrl || "");
3848:             return `
3849:                 <div class="${mcCls("file")}" data-role="sent-meeting-attachment">
3850:                     <span class="${mcCls("file-icon")}" aria-hidden="true">ICS</span>
3851:                     <div class="${mcCls("file-main")}">
3852:                         <strong><span data-role="filename">${escapeText(ca.filename || "")}</span><span class="${mcCls("badge")}" data-state="sent">已发送日历</span></strong>
3853:                         <small data-role="file-meta">${escapeText(metaText)}</small>
3854:                         <div class="${mcCls("file-actions")}"><a class="${mcCls("link")}" data-role="calendar-download" data-action="mc-download-sent-meeting" href="${escapeText(href)}" download>下载 ICS</a></div>
3855:                     </div>
3856:                 </div>
3857:             `;
3858:         }
```

```text
7134:             if (!composeEl) return;
7135:             const container = composeEl.querySelector ? composeEl.querySelector('[data-role="meeting-attachment"]') : null;
7136:             if (!container) return;
7137:             const meeting = manualMeetingSnapshot();
7138:             container.innerHTML = meeting ? meetingCardInnerHtml(meeting) : "";
7139:             container.setAttribute("data-state", meeting && meeting.state === "stale" ? "stale" : "ready");
7140:             if (!meeting) {
7141:                 revokeMeetingBlob();
7142:                 return;
7143:             }
7144:             const filenameNode = container.querySelector('[data-role="filename"]');
7145:             if (filenameNode) filenameNode.textContent = meetingAttachmentFilename(meeting);
7146:             const metaNode = container.querySelector('[data-role="file-meta"]');
7147:             if (metaNode) metaNode.textContent = meetingCardMetaText(meeting);
7148:             const download = container.querySelector('[data-action="mc-download-meeting"]');
7149:             revokeMeetingBlob();
7150:             if (download) {
7151:                 const ics = meeting.preview && meeting.preview.attachment
7152:                     ? String(meeting.preview.attachment.icsText || "")
7153:                     : "";
7154:                 const filename = meetingAttachmentFilename(meeting) || "meeting.ics";
7155:                 const url = createMeetingBlobUrl(ics);
7156:                 if (url) {
7157:                     instance.meeting.lastBlobUrl = url;
7158:                     download.setAttribute("href", url);
7159:                     download.setAttribute("download", filename);
7160:                 } else {
```

```text
7418:         function downloadSentMeetingAttachment(button) {
7419:             const article = (button && typeof button.closest === "function")
7420:                 ? button.closest(".mc-message")
7421:                 : null;
7422:             const key = article && article.dataset ? article.dataset.messageKey : "";
7423:             const message = key ? messageByKey(key) : null;
7424:             const ca = message && message.calendarAttachment ? message.calendarAttachment : null;
7425:             if (!ca) return;
7426:             const adapter = hostFn("mcHostDownloadCalendar");
7427:             if (!adapter) {
7428:                 hostShowStatus("日历下载能力不可用", "error");
7429:                 return;
7430:             }
7431:             adapter(String(ca.downloadUrl || ""), String(ca.filename || "")).catch((err) => {
7432:                 if (instance.disposed) return;
7433:                 hostShowStatus(err && err.message ? err.message : "日历附件下载失败", "error");
7434:             });
7435:         }
7436: 
7437:         function adoptAssembly(processingId, assembly) {
7438:             if (instance.manual.mode !== "inbound" || Number(instance.manual.targetProcessingId) !== Number(processingId)) return;
7439:             const composeEl = manualComposeEl();
7440:             if (!composeEl) return;
7441:             const inputs = manualInputs(composeEl);
7442:             if (!inputs) return;
7443:             // 全文替换采用前：先移除旧会议附件与快照（I-3/I-6），正文由 assembly 覆盖
7444:             const hadMeeting = !!manualMeetingSnapshot();
7445:             if (hadMeeting) revokeMeetingBlob();
7446:             const assemblyText = normalizeManualTextLineBreaks(
7447:                 (assembly && (assembly.renderedDraftText || assembly.rawDraftText || assembly.text)) || ""
7448:             );
```

## styles: src/main/resources/static/meeting-confirmation.css

```text
1: /* 专家会议确认：只限新组件及新生成正文块。 */
2: .mail-chat .button.meeting-trigger{color:#1e40af;border-color:#b8cef5;background:#eef4ff;gap:7px;margin-left:3px}
3: .mail-chat .button.meeting-trigger:hover{background:#e5eeff;border-color:#93b4ec}
4: .mail-chat .button.meeting-trigger:active{background:#dbeafe}
5: .meeting-icon{display:inline-flex;font-size:16px;line-height:1}
6: .meeting-dialog{margin:auto;inset:0;width:min(1080px,calc(100vw - 40px));max-height:calc(100dvh - 40px);padding:0;border:1px solid #d5dfed;border-radius:14px;background:#fff;color:#334155;font-family:var(--font-body);font-size:12px;line-height:1.6;box-shadow:0 24px 100px #172c473d;overflow:auto;overscroll-behavior:contain}
7: .meeting-dialog::backdrop{background:#182a464f;backdrop-filter:blur(2px)}
8: .meeting-dialog *{box-sizing:border-box}
9: .meeting-dialog [hidden],.mail-chat .meeting-attachment[hidden]{display:none!important}
10: .meeting-dialog form{margin:0;padding:0}
11: .meeting-head{display:flex;align-items:center;justify-content:space-between;padding:20px 24px;border-bottom:1px solid #e2e8f0;gap:16px;position:sticky;top:0;background:#fff;z-index:2}
12: .meeting-head h2{margin:0;font-size:19px;font-weight:600;color:#334155;line-height:1.4}
13: .meeting-head p{margin:6px 0 0;font-size:12px;color:#64748b;line-height:1.6;overflow-wrap:anywhere}
14: .meeting-close{display:inline-flex;align-items:center;justify-content:center;flex:none;width:28px;height:28px;padding:0;border:0;border-radius:5px;background:transparent;color:#91a1b7;font:inherit;font-size:21px;line-height:1;cursor:pointer}
15: .meeting-close:hover{background:#edf3ff;color:#2451b9}
16: .meeting-close:active{background:#dbeafe}
17: .meeting-grid{display:grid;grid-template-columns:45% 55%}
18: .meeting-form{padding:18px 24px;border-right:1px solid #e2e8f0;min-width:0}
19: .meeting-preview{padding:22px 24px;background:#f8faff;min-width:0}
20: .meeting-form label{display:flex;flex-direction:column;gap:6px;font-size:12px;line-height:18px;color:#52647e;margin-bottom:12px;font-weight:500;letter-spacing:0;text-transform:none;min-width:0}
21: .meeting-form input,.meeting-form select,.meeting-form textarea{min-width:0;width:100%;height:36px;min-height:36px;margin:0;padding:7px 10px;font:inherit;font-weight:400;color:#334155;border:1px solid #d7e0ed;border-radius:7px;background:#fff;box-shadow:none}
22: .meeting-form textarea{height:70px;resize:vertical;line-height:1.6}
23: .meeting-form input::placeholder,.meeting-form textarea::placeholder{color:#94a3b8;opacity:1}
24: .meeting-form small{font-size:11px;font-weight:400;color:#76859b;line-height:16px}
25: .meeting-form :is(input,textarea,select):hover:not(:disabled){border-color:#93b4ec}
26: .meeting-form :is(input,textarea,select)[aria-invalid=true]{border-color:#e11d48;background:#fff8fa}
27: .meeting-form :is(input,textarea):read-only{background:#f8faff}
28: .meeting-form :is(input,textarea,select):disabled{opacity:.55;cursor:not-allowed;background:#f1f5f9}
29: .meeting-fields{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:12px}
30: .meeting-clock{margin:-3px 0 14px;padding:11px 12px;border:1px solid #dce7fa;border-radius:7px;background:#f0f5ff;font-size:12px;line-height:1.65;color:#47658b}
31: .meeting-clock b{font-weight:500;color:#234f99}
32: .meeting-preview h3{font-size:12px;font-weight:600;margin:0 0 12px;color:#536680;display:flex;align-items:center;justify-content:space-between;gap:8px;line-height:1.6}
33: .meeting-preview h3>span:last-child{font-weight:400;color:#73859c;font-size:11px}
34: .meeting-step{display:inline-block;color:#1e40af;background:#eff5ff;border:1px solid #d6e3f8;border-radius:5px;padding:3px 7px;font-size:11px;margin-right:9px}
35: .meeting-paper{background:#fff;border:1px solid #dce4ef;border-radius:9px;padding:20px 22px;font-size:13px;line-height:1.85;overflow-wrap:anywhere;min-height:342px;color:#334155}
36: .meeting-paper p{margin:0 0 14px;color:inherit;font-size:inherit;line-height:inherit}
37: .meeting-paper p:last-child{margin:0}
38: .meeting-paper a,.mail-chat .meeting-body-block a{color:#2563b1;text-decoration:underline;overflow-wrap:anywhere}
39: .meeting-paper a:hover,.mail-chat .meeting-body-block a:hover{color:#1e40af}
40: .meeting-paper a:active,.mail-chat .meeting-body-block a:active{color:#172554}
41: .meeting-file{display:flex;align-items:flex-start;gap:11px;padding:13px 14px;margin-top:14px;border:1px solid #d8e3f2;border-radius:8px;background:#fff;min-width:0}
42: .meeting-file-icon{flex:none;display:grid;place-items:center;width:36px;height:42px;background:#edf4ff;border:1px solid #cbdcf6;border-radius:6px;font-size:11px;color:#3964a4;font-weight:600}
43: .meeting-file-main{flex:1;min-width:0}
44: .meeting-file-main strong{display:block;font-size:12px;font-weight:500;overflow-wrap:anywhere;color:#435b7c;line-height:1.6}
45: .meeting-file-main small{display:block;font-size:11px;color:#788ba5;margin-top:5px;line-height:1.6}
46: .meeting-file-actions{display:flex;flex-wrap:wrap;gap:14px;align-items:center;margin-top:9px}
47: .meeting-link{display:inline-flex;align-items:center;border:0;border-radius:3px;background:none;color:#315fa7;font-family:inherit;font-size:12px;line-height:1.6;cursor:pointer;padding:0;text-decoration:none;white-space:nowrap}
48: .meeting-link:hover{color:#244ca9;text-decoration:underline}
49: .meeting-link:active{color:#172554;background:#edf3ff}
50: .meeting-raw{font-family:ui-monospace,monospace;font-size:11px;line-height:1.6;color:#62768e;white-space:pre-wrap;overflow-wrap:anywhere;max-height:160px;overflow:auto;padding:12px;border:1px solid #dae3ef;border-radius:7px;background:#fff;margin:12px 0 0}
51: .meeting-bottom{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:16px 24px;border-top:1px solid #e2e8f0;background:#fff;position:sticky;bottom:0;z-index:1}
52: .meeting-bottom p{font-size:12px;line-height:1.6;color:#76859b;margin:0}
53: .meeting-bottom>div{display:flex;gap:9px;flex:none}
54: .meeting-dialog .button{min-height:36px;height:36px;font-size:12px;padding:0 15px}
55: .meeting-error{font-size:12px;line-height:1.6;padding:10px 14px;color:#be123c;border:1px solid #fecdd3;background:#fff1f2;border-radius:7px;margin:12px 0;overflow-wrap:anywhere}
56: .meeting-status{font-size:12px;line-height:1.6;padding:10px 14px;color:#47658b;border:1px solid #dce7fa;background:#f0f5ff;border-radius:7px;margin:0 0 12px}
57: .meeting-status .meeting-link{margin-left:10px}
58: .meeting-note{font-size:11px;line-height:1.7;color:#73859d;margin:12px 0 0}
59: .meeting-template{margin:0 0 14px;font-size:12px}
60: .meeting-template summary{color:#466795;cursor:pointer;font-size:12px;line-height:1.6}
61: .meeting-template summary:hover{color:#1e40af}
62: .meeting-template summary:active{color:#172554}
63: .meeting-form .meeting-template textarea{height:245px;margin:12px 0 8px;font-family:ui-monospace,monospace;font-size:11px}
64: .meeting-template code{font-size:11px;color:#466795}
65: .meeting-template p{font-size:11px;color:#73859d;line-height:1.8;margin:8px 0}
66: .meeting-badge{display:inline-flex;align-items:center;margin-left:6px;padding:2px 6px;border:1px solid #d6e3f8;border-radius:5px;background:#eff5ff;color:#1e40af;font-size:10px;line-height:1.6;font-weight:400;vertical-align:middle}
67: .meeting-badge[data-state=stale]{border-color:#fed7aa;background:#fff7ed;color:#b45309}
68: .meeting-badge[data-state=sent]{border-color:#a7f3d0;background:#ecfdf5;color:#059669}
69: .mail-chat .meeting-attachment:empty{display:none}
70: .mail-chat .meeting-attachment .meeting-file{margin:0}
71: .mail-chat .meeting-draft-note{font-size:11px;color:#70829a;margin:7px 0 0;line-height:1.6}
72: .mail-chat .meeting-body-block{font-size:13px;line-height:1.85;color:#334155;overflow-wrap:anywhere}
73: .mail-chat .meeting-body-block p{font-size:inherit;line-height:inherit;color:inherit;margin:0 0 14px}
74: .mail-chat .meeting-body-block p:last-child{margin:0}
75: .mail-chat .meeting-attachment[data-state=stale] .meeting-file{border-color:#fed7aa;background:#fffcf7}
76: .meeting-zone-field{position:relative;margin-bottom:14px}
77: .meeting-zone-field>label{margin-bottom:6px}
78: .meeting-zone-control{display:flex;position:relative}
79: .meeting-zone-control input{padding-right:40px}
80: .meeting-zone-control>button{position:absolute;right:1px;top:1px;width:34px;height:34px;padding:0;border:0;background:#f7faff;color:#627ca5;border-radius:0 6px 6px 0;font:inherit;cursor:pointer}
81: .meeting-zone-control>button:hover{background:#edf3ff;color:#1e40af}
82: .meeting-zone-control>button:active{background:#dbeafe}
83: .meeting-zone-field>small{display:block;margin-top:6px}
84: .meeting-zone-options{position:absolute;top:64px;left:0;right:0;max-height:252px;overflow:auto;overscroll-behavior:contain;z-index:10;border:1px solid #cbd9ed;border-radius:8px;background:#fff;box-shadow:0 10px 25px #223c6226;padding:5px}
85: .meeting-zone-options>button{width:100%;display:flex;align-items:center;justify-content:space-between;gap:10px;padding:9px 10px;background:#fff;border:0;border-radius:5px;text-align:left;color:#334155;font:inherit;font-size:12px;line-height:1.6;cursor:pointer}
86: .meeting-zone-options>button:hover,.meeting-zone-options>button.focused{background:#eff5ff}
87: .meeting-zone-options>button:active{background:#dbeafe}
88: .meeting-zone-options>button[aria-selected=true]{background:#eaf1ff;color:#1e40af}
89: .meeting-zone-options small{display:block;margin-top:2px;font-size:11px;color:#7b8ba2;line-height:1.5}
90: .meeting-zone-options>button>span:last-child{white-space:nowrap;color:#5b769e}
91: .meeting-zone-empty{padding:15px;color:#718198;font-size:12px;line-height:1.6}
92: .meeting-dialog :is(button,a,input,select,textarea,summary):focus-visible,.mail-chat :is(.meeting-trigger,.meeting-link):focus-visible{outline:2px solid #82a8e8;outline-offset:2px}
93: .meeting-dialog :is(button,.button):disabled,.meeting-link:disabled,.meeting-link[aria-disabled=true],.mail-chat .meeting-trigger:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none;text-decoration:none}
94: .meeting-dialog .button:disabled::after,.mail-chat .meeting-trigger:disabled::after{display:none}
95: .meeting-dialog :is(button,.button):disabled:hover,.meeting-link:disabled:hover,.meeting-link[aria-disabled=true]:hover{filter:none;transform:none;box-shadow:none}
96: .meeting-dialog .button:not(.primary):disabled,.meeting-dialog .button:not(.primary):disabled:hover{background-color:transparent;color:#1e293b;border-color:rgba(15,23,42,.11)}
97: .meeting-dialog .button.primary:disabled,.meeting-dialog .button.primary:disabled:hover{background-image:linear-gradient(180deg,#3b82f6,#1e40af);background-color:#1e40af;border-color:transparent;color:#fff}
98: .meeting-link:disabled,.meeting-link:disabled:hover,.meeting-link[aria-disabled=true],.meeting-link[aria-disabled=true]:hover{color:#315fa7;background:none}
99: .mail-chat .button.meeting-trigger:disabled,.mail-chat .button.meeting-trigger:disabled:hover{color:#1e40af;border-color:#b8cef5;background:#eef4ff}
100: .mail-chat [data-role=manual-compose][data-meeting-sending=true] .mc-editor{background:#f8faff;cursor:wait}
101: @media(max-width:800px){.meeting-grid{grid-template-columns:minmax(0,1fr)}.meeting-form{padding:18px;border-right:0;border-bottom:1px solid #e2e8f0}.meeting-preview{padding:18px}.meeting-dialog{width:calc(100vw - 20px);max-height:calc(100dvh - 20px)}.meeting-bottom{flex-wrap:wrap;padding:12px 18px}.meeting-head{padding:16px 18px}.meeting-head h2{font-size:17px}.meeting-bottom>div{margin-left:auto}.meeting-fields{gap:9px}.meeting-file{flex-wrap:wrap}}
102: @media(max-width:420px){.meeting-head,.meeting-form,.meeting-preview{padding:14px}.meeting-paper{padding:16px}.meeting-bottom{padding:12px 14px}.meeting-bottom>div{width:100%;justify-content:flex-end}.meeting-bottom .button{padding:0 10px}.meeting-zone-options{max-height:220px}}
103: @media(prefers-reduced-motion:reduce){.meeting-dialog *,.mail-chat .meeting-trigger,.mail-chat .meeting-link{transition:none!important;scroll-behavior:auto!important}.meeting-dialog .button:hover,.meeting-dialog .button:active,.mail-chat .meeting-trigger:hover,.mail-chat .meeting-trigger:active{transform:none}}
```

## shared-clock: src/main/resources/static/world-clock.js

```text
1: /* 全球北京时间与时区对照（fast-p 01 独立组件）：纯换算 + 只读目录 + 全局宿主。
2:  *
3:  * 普通 script（非 ES module），必须在 app.js 之后加载：目录经宿主 window.api() 读取，
4:  * 筛选复用宿主 MailboxMeeting.filterZones，DOM 模板逐字取自计划 S-4，动态文本一律
5:  * 走 textContent。不写业务状态、不持久化、不新增后端接口；浏览器在 DOMContentLoaded
6:  * 自动挂载，Node 测试经 module.exports 载入纯函数与 mount。 */
7: (function (global) {
8:     "use strict";
9: 
10:     var CATALOG_PATH = "/api/mail/meeting-confirmation/time-zones?date=";
11:     var BEIJING_OFFSET_SECONDS = 28800;
12:     var BEIJING_OFFSET_MS = BEIJING_OFFSET_SECONDS * 1000;
13:     var MIN_YEAR = 2000;
14:     var MAX_YEAR = 2100;
```

## generator-script: scripts/generate_meeting_timezone_catalog.py

```text
85:     for node in root.iter("type"):
86:         aliases = node.attrib.get("alias", "").split()
87:         description = node.attrib.get("description", "").strip()
88:         if not aliases or not description:
89:             continue
90:         for zone_id in aliases:
91:             records[zone_id] = description
92:             aliases_by_zone[zone_id] = aliases
93:     return records, aliases_by_zone
94: 
95: 
96: def split_description(description, english_territories):
97:     city, separator, country = description.rpartition(", ")
98:     if separator and country in english_territories:
99:         return city, country
100:     return description, ""
101: 
102: 
103: def unique(values):
104:     return list(dict.fromkeys(value for value in values if value))
105: 
106: 
107: def first_city(cities, zone_id, aliases):
108:     return next((cities[alias] for alias in [zone_id, *aliases] if alias in cities), "")
109: 
110: 
111: def entry_for(zone_id, zh_cities, en_cities, records, aliases_by_zone, zh_countries, en_countries):
112:     if zone_id == "UTC":
113:         return UTC_ENTRY
114:     description = records.get(zone_id, "")
115:     record_aliases = aliases_by_zone.get(zone_id, [])
116:     fallback_city = zone_id.rsplit("/", 1)[-1].replace("_", " ")
117:     english_city, english_country = split_description(description, set(en_countries.values()))
118:     english_city = english_city or first_city(en_cities, zone_id, record_aliases) or fallback_city
119:     chinese_city = first_city(zh_cities, zone_id, record_aliases)
120:     chinese_country = next(
121:         (zh_countries[code] for code, name in en_countries.items() if name == english_country and code in zh_countries),
122:         "",
123:     )
124:     if chinese_country and chinese_city:
125:         label = f"{chinese_country} · {chinese_city}"
126:     elif chinese_city:
127:         label = chinese_city
128:     elif chinese_country:
129:         label = chinese_country
130:     else:
131:         label = "系统时区 · " + fallback_city
132:     label = COMPATIBILITY_LABELS.get(zone_id, label)
133:     aliases = unique([
134:         chinese_country,
135:         chinese_city,
136:         english_country,
137:         english_city,
138:         description,
139:         zone_id,
140:         *COMPATIBILITY_ALIASES.get(zone_id, []),
141:     ])
142:     return label, aliases
143: 
144: 
145: def main():
146:     parser = argparse.ArgumentParser()
147:     parser.add_argument("--zone-ids", required=True, type=Path)
148:     parser.add_argument("--zh", required=True, type=Path)
149:     parser.add_argument("--en", required=True, type=Path)
150:     parser.add_argument("--timezone", required=True, type=Path)
151:     parser.add_argument("--output", required=True, type=Path)
152:     args = parser.parse_args()
153: 
154:     zone_ids = [line.strip() for line in args.zone_ids.read_text(encoding="utf-8").splitlines() if line.strip()]
155:     zone_ids = unique([*zone_ids, *RUNTIME_COMPATIBILITY_ZONE_IDS, "UTC"])
156:     zh_root = ET.parse(args.zh).getroot()
157:     en_root = ET.parse(args.en).getroot()
158:     records, aliases_by_zone = timezone_records(ET.parse(args.timezone).getroot())
159:     zh_countries = texts_by_type(zh_root, "territory")
160:     en_countries = texts_by_type(en_root, "territory")
161:     zh_cities = zone_cities(zh_root)
162:     en_cities = zone_cities(en_root)
163: 
164:     catalog = {
165:         zone_id: entry_for(zone_id, zh_cities, en_cities, records, aliases_by_zone, zh_countries, en_countries)
166:         for zone_id in sorted(zone_ids)
167:     }
168:     args.output.parent.mkdir(parents=True, exist_ok=True)
169:     lines = [
170:         "# Generated from Unicode CLDR common/main/{zh,en}.xml and common/bcp47/timezone.xml.",
171:         "# Regenerate with scripts/generate_meeting_timezone_catalog.py; do not edit by hand.",
172:     ]
173:     for zone_id, (label, aliases) in catalog.items():
174:         lines.append(f"{zone_id}={label}\t" + "\u001f".join(aliases))
175:     args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
176:     print(json.dumps({"entries": len(catalog), "output": str(args.output)}, ensure_ascii=False))
177: 
178: 
179: if __name__ == "__main__":
180:     main()
```

## schema-calendar: src/main/resources/db/migration/V123__add_mail_record_calendar_attachment.sql

```text
1: -- ============================================================================
2: -- V123 mail_record 会议日历附件存档列（fast-p 02）
3: --
4: -- 只新增一列：calendar_attachment_json LONGTEXT NULL。
5: --   * NULL = 无会议日历（唯一 absence 形态；禁止用空串/{} 表示无附件，I-1）；
6: --   * 非 NULL 必须是 01 CalendarAttachmentSnapshot 规范快照 JSON
7: --     （schemaVersion=1，UTF-8 字节 sha256 与语义 sha256 均与 icsText 一致），
8: --     由应用层 CalendarAttachmentCodec 严格生成/读取验证（I-1），本迁移不设
9: --     CHECK（应用 01 codec 是唯一校验方）。
10: -- 历史行不回填（旧行保持 NULL），不加默认值、不加索引。
11: -- ============================================================================
12: 
13: ALTER TABLE mail_record
14:     ADD COLUMN calendar_attachment_json LONGTEXT NULL;
```

## schema-schedule: src/main/resources/db/migration/V125__create_meeting_calendar_event.sql

```text
1: CREATE TABLE meeting_calendar_event (
2:     id BIGINT PRIMARY KEY AUTO_INCREMENT,
3:     expert_contact_id BIGINT NOT NULL,
4:     source_mail_record_id BIGINT NULL,
5:     starts_at_utc DATETIME(6) NOT NULL,
6:     ends_at_utc DATETIME(6) NOT NULL,
7:     meeting_link VARCHAR(1024) NULL,
8:     note VARCHAR(200) NULL,
9:     cancel_reason VARCHAR(200) NULL,
10:     status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
11:     created_at DATETIME(6) NOT NULL,
12:     updated_at DATETIME(6) NOT NULL,
13:     KEY idx_meeting_calendar_status_start_id (status, starts_at_utc, id),
14:     KEY idx_meeting_calendar_contact_status_start_id (expert_contact_id, status, starts_at_utc, id),
15:     UNIQUE KEY uk_meeting_calendar_source_mail (source_mail_record_id),
16:     CONSTRAINT fk_meeting_calendar_contact
17:         FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE RESTRICT,
18:     CONSTRAINT fk_meeting_calendar_source_mail
19:         FOREIGN KEY (source_mail_record_id) REFERENCES mail_record(id) ON DELETE RESTRICT
20: );
```
