package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

const val MAIL_REPLY_DRAFT_MAX_BYTES = 1024 * 1024

enum class MailReplyDraftKind { INBOUND, OUTBOUND }
enum class MailReplyDraftState { ACTIVE, SENT, DISCARDED }
data class MailReplyDraftTarget(val contactId: Long, val kind: MailReplyDraftKind, val processingId: Long = 0, val accountScope: String = "")
data class MailReplyDraftRef(val id: Long, val version: Long)
/** Only server validation may construct this projection; it never accepts a client owner. */
internal data class ValidatedDraftSendRef(
    val owner: String, val id: Long, val version: Long, val target: MailReplyDraftTarget,
    val content: MailReplyDraftContent, val sendAttemptId: Long?, val sendVersion: Long?
)
data class MailReplyDraftQa(val ragFactCodes: List<String> = emptyList(), val ragCorpusFingerprint: String = "", val baselineText: String = "")
data class MailReplyDraftMeetingInput(val zoneId: String = "", val startLocal: String = "", val endLocal: String = "", val zoomUrl: String = "", val generatedAt: String = "")
data class MailReplyDraftCalendarAttachment(val filename: String = "", val contentType: String = "", val icsText: String = "", val byteLength: Long = 0, val sha256: String = "", val semanticSha256: String = "")
data class MailReplyDraftMeetingPreview(
    val htmlBody: String = "", val textBody: String = "", val attachment: MailReplyDraftCalendarAttachment? = null,
    val startUtc: String = "", val endUtc: String = "", val meetingTime: String = "", val chinaTime: String = "", val durationMinutes: Int = 0
)
data class MailReplyDraftMeeting(
    val input: MailReplyDraftMeetingInput? = null, val preview: MailReplyDraftMeetingPreview? = null,
    val blockHtml: String = "", val blockText: String = "", val state: String = "", val revision: Long = 0
)
data class MailReplyDraftAttachmentItem(
    val key: String, val state: String, val id: String? = null, val filename: String = "",
    val contentType: String = "", val byteLength: Long = 0, val sha256: String = "", val error: String? = null
)
data class MailReplyDraftAttachments(val revision: Long = 0, val items: List<MailReplyDraftAttachmentItem> = emptyList())
data class MailReplyDraftContext(
    val schemaVersion: Int = 1, val qa: MailReplyDraftQa? = null, val requestId: String? = null,
    val meeting: MailReplyDraftMeeting? = null, val meetingAccountCode: String? = null,
    val followUpAnchorMailRecordId: Long? = null, val outboundAttachmentDraft: MailReplyDraftAttachments? = null
)
data class MailReplyDraftContent(val subject: String = "", val html: String = "", val text: String = "", val context: MailReplyDraftContext = MailReplyDraftContext())
data class MailReplyDraftSaveRequest(val expectedVersion: Long, val content: MailReplyDraftContent, val reopen: Boolean = false)
data class MailReplyDraftRow(
    val id: Long, val username: String, val target: MailReplyDraftTarget, val version: Long, val state: MailReplyDraftState,
    val subject: String?, val html: String?, val text: String?, val contextJson: String?,
    val sendAttemptId: Long?, val sendVersion: Long?, val createdAt: LocalDateTime, val updatedAt: LocalDateTime,
    val contactExists: Boolean = true, val expertName: String? = null, val expertEmail: String? = null, val sendAttemptStatus: String? = null
)
data class MailReplyDraftDetail(
    val id: Long? = null, val version: Long = 0, val state: MailReplyDraftState? = null,
    val target: MailReplyDraftTarget? = null, val content: MailReplyDraftContent? = null,
    val createdAt: LocalDateTime? = null, val updatedAt: LocalDateTime? = null,
    val contactExists: Boolean = true, val expertName: String? = null, val expertEmail: String? = null,
    val sendAttemptId: Long? = null, val sendVersion: Long? = null, val sendAttemptStatus: String? = null,
    val attachmentDownloads: Map<String, String> = emptyMap(), val attachmentsSendable: Boolean = true
)
data class MailReplyDraftListItem(
    val id: Long, val version: Long, val state: MailReplyDraftState, val target: MailReplyDraftTarget,
    val subject: String?, val preview: String, val updatedAt: LocalDateTime,
    val contactExists: Boolean, val expertName: String?, val expertEmail: String?
)
data class MailReplyDraftList(val items: List<MailReplyDraftListItem>, val total: Long, val page: Int, val size: Int)
data class MailReplyDraftSummary(val contactId: Long, val count: Long)
data class MailReplyDraftSummaries(val items: List<MailReplyDraftSummary>, val total: Long)

/** Controller-local errors, deliberately not a new global exception protocol. */
class MailReplyDraftException(val status: HttpStatus, val code: String, override val message: String,
    val currentVersion: Long? = null, val currentState: MailReplyDraftState? = null) : IllegalStateException(message)

object MailReplyDraftContextCodec {
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
    /** Use this codec at the HTTP boundary too: global Jackson coercion must not truncate CAS versions. */
    fun parseSaveRequest(node: JsonNode): MailReplyDraftSaveRequest {
        val version = node.get("expectedVersion")
        if (version == null || !version.isIntegralNumber || !version.canConvertToLong()) invalid("非法 expectedVersion")
        val schema = node.path("content").path("context").get("schemaVersion")
        if (schema != null && (!schema.isIntegralNumber || !schema.canConvertToInt() || schema.asInt() != 1)) invalid("不支持的草稿 schemaVersion")
        return try {
            mapper.treeToValue(node, MailReplyDraftSaveRequest::class.java).also { validate(it.content.context) }
        } catch (e: MailReplyDraftException) { throw e } catch (e: Exception) { invalid("非法草稿快照") }
    }
    fun serialize(context: MailReplyDraftContext): String {
        validate(context)
        return mapper.writeValueAsString(context)
    }
    fun parse(json: String): MailReplyDraftContext = try {
        mapper.readValue(json, MailReplyDraftContext::class.java).also(::validate)
    } catch (e: MailReplyDraftException) { throw e } catch (e: Exception) { invalid("非法草稿上下文") }
    fun validate(context: MailReplyDraftContext) {
        if (context.schemaVersion != 1) invalid("不支持的草稿 schemaVersion")
        val attachments = context.outboundAttachmentDraft ?: return
        if (attachments.revision < 0 || attachments.items.size > MAX_FILES || attachments.items.map { it.key }.distinct().size != attachments.items.size) invalid("非法附件快照")
        if (attachments.items.any { it.key.isBlank() || it.state !in setOf("ready", "uploading", "failed") || it.byteLength < 0 || (it.state == "ready" && it.id.isNullOrBlank()) }) invalid("非法附件快照")
        val ids = attachments.items.mapNotNull { it.id }
        if (ids.distinct().size != ids.size) invalid("重复附件 id")
    }
    private fun invalid(message: String): Nothing = throw MailReplyDraftException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DRAFT", message)
}
