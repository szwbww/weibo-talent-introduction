package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.security.MessageDigest

/**
 * 专家会议确认专用域（fast-p 01）。
 *
 * 本文件只定义请求/响应 DTO 与内存快照，不新增任何数据表。日历附件快照是
 * server-only 值：由本阶段同一生成器创建，JSON 仅在 02 持久化到
 * mail_record.calendar_attachment_json；客户端不能直传任意 downloadUrl。
 */
object MeetingConfirmationDomain {
    /** 专用模板 code 与 mailType（同值，I-2）。 */
    const val MANUAL_MEETING_CONFIRMATION = "MANUAL_MEETING_CONFIRMATION"

    /** 表单无默认猜测时的明确应用默认时区（I-4），不推断专家所在地。 */
    const val DEFAULT_ZONE_ID = "Asia/Shanghai"

    const val CALENDAR_CONTENT_TYPE = "text/calendar; charset=UTF-8"

    const val CALENDAR_SCHEMA_VERSION = 1

    /** 快照 filename 固定形态（I-6；Codec 校验同款）。 */
    val CALENDAR_FILENAME_REGEX = Regex("""^meeting-[0-9]{4}-[0-9]{2}-[0-9]{2}-[A-Za-z0-9-]{1,60}\.ics$""")
}

/** 会议配置输入（打开/编辑草稿时冻结；不写回模板）。 */
data class MeetingInput(
    /** 专用模板 id（MANUAL_MEETING_CONFIRMATION，启用）。 */
    val templateId: Long,
    /** 从该模板取出的正文快照；允许比库模板新/旧，不回写模板。 */
    val templateBody: String,
    /** 人工称呼（Dear 后），原样可改，不猜测职称/姓氏。 */
    val expertSalutation: String,
    /** 服务端目录中的 IANA 区域或 UTC。 */
    val zoneId: String,
    /** YYYY-MM-DDTHH:mm，分钟精度。 */
    val startLocal: String,
    /** YYYY-MM-DDTHH:mm，分钟精度。 */
    val endLocal: String,
    /** 已创建的 Zoom 会议链接（含入会密码参数）。 */
    val zoomUrl: String,
    /** 发件签名（多行允许 LF；CRLF 统一 LF）。 */
    val senderSignature: String,
    /** UTC ISO Instant（精度秒）；options 发出、已有草稿复用。 */
    val generatedAt: String
)

data class MeetingPreviewRequest(
    val contactId: Long,
    /** 请求账号，可省：缺省用来信账号（与 Pending 相同）。 */
    val senderAccountCode: String? = null,
    val meeting: MeetingInput
)

/** options 模板目录条目（只含本专用 type 的启用模板）。 */
data class MeetingTemplateOption(
    val id: Long,
    val name: String,
    val body: String
)

data class MeetingOptionsResponse(
    /** 目标键（联系人+解析后账号作用域），options 与 preview 同源。 */
    val targetKey: String,
    val resolvedAccountCode: String,
    /** 专家称呼默认值：expert_contact.expertName 原文（可改；无值留空）。 */
    val expertSalutation: String,
    /** 默认签名：由账号 senderName/title、teamName/countryName 拼接（不伪造职位）。 */
    val senderSignature: String,
    /** UTC ISO Instant（精度秒）。 */
    val generatedAt: String,
    val defaultZoneId: String,
    val templates: List<MeetingTemplateOption>
)

data class MeetingTimeZoneOption(
    val id: String,
    /** 中文展示标签；未映射 id 用英文 id 兜底（不宣称全球城市都有中文翻译）。 */
    val labelZh: String,
    /** 搜索别名（含中英文与常见异名）；未映射 id 为空。 */
    val aliases: List<String>,
    /** 该 date 12:00 UTC 的偏移展示（仅列表辅助，不作会议时间换算结果）。 */
    val offsetLabel: String,
    val offsetSeconds: Int
)

data class MeetingCalendarAttachment(
    val filename: String,
    val contentType: String,
    val icsText: String,
    val byteLength: Int,
    val sha256: String,
    val semanticSha256: String
)

data class MeetingPreviewResponse(
    val targetKey: String,
    val resolvedAccountCode: String,
    /** 回传本次输入（原值；generatedAt 为空时回传服务端冻结值）。 */
    val meeting: MeetingInput,
    val textBody: String,
    val htmlBody: String,
    /** 英文会议时间行（Locale.US；同日/跨日/偏移变化格式固定）。 */
    val meetingTime: String,
    val startUtc: String,
    val endUtc: String,
    /** 中国时间（Asia/Shanghai，固定 zh-CN 映射）。 */
    val chinaTime: String,
    val durationMinutes: Int,
    val attachment: MeetingCalendarAttachment
)

/**
 * 日历附件规范化快照（02 持久化契约；schemaVersion=1）。
 *
 * 字段全部固定；sha256 是 icsText 的 UTF-8 字节摘要；semanticSha256 是
 * 固定规范语义摘要（不含 generatedAt/模板文字/随机数/filename）。
 * 不能作为发送请求附件直传。
 */
data class CalendarAttachmentSnapshot(
    val schemaVersion: Int,
    val filename: String,
    val contentType: String,
    val icsText: String,
    val sha256: String,
    val semanticSha256: String
)

/**
 * 快照 JSON 编解码（Jackson Kotlin mapper，固定字段）。
 *
 * parse 拒绝：空串、schemaVersion!=1、非固定 contentType、非法 filename、
 * UTF-8 >64KiB、sha256≠icsText 字节摘要、semanticSha256 非 64 位小写 hex；
 * 损坏一律返回 null（不抛到时间线 500）。serialize 只接受同样有效快照，
 * 失败立即抛 IllegalArgumentException。
 */
object CalendarAttachmentCodec {

    private val mapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)

    private const val MAX_ICS_BYTES = 64 * 1024

    private val HEX64 = Regex("^[0-9a-f]{64}$")

    fun serialize(snapshot: CalendarAttachmentSnapshot): String {
        validate(snapshot)
        return try {
            mapper.writeValueAsString(snapshot)
        } catch (ex: Exception) {
            throw IllegalArgumentException("Calendar attachment snapshot cannot be serialized", ex)
        }
    }

    fun parseOrNull(json: String?): CalendarAttachmentSnapshot? {
        if (json.isNullOrBlank()) {
            return null
        }
        return try {
            val snapshot = mapper.readValue(json, CalendarAttachmentSnapshot::class.java)
            validate(snapshot)
            snapshot
        } catch (ex: Exception) {
            null
        }
    }

    private fun validate(snapshot: CalendarAttachmentSnapshot) {
        require(snapshot.schemaVersion == MeetingConfirmationDomain.CALENDAR_SCHEMA_VERSION) {
            "Unsupported calendar snapshot schemaVersion: ${snapshot.schemaVersion}"
        }
        require(snapshot.contentType == MeetingConfirmationDomain.CALENDAR_CONTENT_TYPE) {
            "Unsupported calendar contentType: ${snapshot.contentType}"
        }
        require(MeetingConfirmationDomain.CALENDAR_FILENAME_REGEX.matches(snapshot.filename)) {
            "Illegal calendar filename: ${snapshot.filename}"
        }
        val bytes = snapshot.icsText.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_ICS_BYTES) {
            "Calendar icsText exceeds 64KiB UTF-8"
        }
        val expectedSha = sha256Hex(bytes)
        require(snapshot.sha256 == expectedSha) {
            "Calendar sha256 does not match icsText bytes"
        }
        require(HEX64.matches(snapshot.semanticSha256)) {
            "Calendar semanticSha256 must be 64 lowercase hex characters"
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
