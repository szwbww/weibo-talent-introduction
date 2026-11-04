package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.llm.service.TrustReplyDiagnostics
import com.weibo.talentintroduction.llm.service.TrustReplyItemVersion
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import java.util.UUID

/**
 * fast-p 01（总 I-1/I-3/I-4/I-10）：人工回复延迟任务的持久模型。
 *
 * 本文件只声明服务端内部构造的值对象与快照编解码。不引用后续阶段的 service/controller，
 * 也不接受客户端传入的 verified 字段。任务时间一律为 epoch 毫秒；本文件不做时钟读取。
 */

/** 固定业务常量：首次受理到可投递的等待时间。不可通过配置修改。 */
const val DELAYED_MANUAL_REPLY_DELAY_MS = 60_000L

/** 快照协议版本；未知版本必须 fail closed。 */
const val DELAYED_MANUAL_REPLY_SCHEMA_VERSION = 1

/** 快照 UTF-8 有效上限（新设计上限；超出由入口返回 413 且不入队）。 */
const val DELAYED_MANUAL_REPLY_MAX_SNAPSHOT_BYTES = 4 * 1024 * 1024

/** 六态生命周期；既有 attempt 枚举不新增同名值（I-10）。 */
enum class DelayedManualReplyStatus { WAITING, SENDING, CANCELLED, SENT, FAILED, UNKNOWN }

enum class DelayedManualReplyOriginKind { DRAFT, LEGACY, REVIEW }

/** 表行投影。字段与 `delayed_manual_reply` 列一一对应，时间均为 epoch 毫秒（I-1）。 */
data class DelayedManualReplyRow(
    val id: Long,
    val sendAttemptId: Long,
    val expertContactId: Long,
    val accountCode: String,
    val actorUsername: String,
    val originKind: DelayedManualReplyOriginKind,
    val originId: Long?,
    val originVersion: Long?,
    val revision: Long,
    val status: DelayedManualReplyStatus,
    val dueAtMs: Long,
    val dispatchToken: String?,
    val claimedAtMs: Long?,
    val subject: String,
    val textBody: String,
    val snapshotJson: String,
    val publicErrorCode: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

/**
 * 新任务插入参数。首次排队只接受 WAITING：`subject/text_body` 与快照同源派生，
 * `due_at_ms` 必须等于 `created_at_ms + DELAYED_MANUAL_REPLY_DELAY_MS`（I-1）。
 */
data class DelayedManualReplyInsert(
    val sendAttemptId: Long,
    val expertContactId: Long,
    val accountCode: String,
    val actorUsername: String,
    val originKind: DelayedManualReplyOriginKind,
    val originId: Long?,
    val originVersion: Long?,
    val dueAtMs: Long,
    val snapshot: DelayedManualReplySnapshot,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

/** claim 赢家令牌；仅 JVM 内部使用，不进入 HTTP DTO（I-3）。 */
internal data class DelayedDispatchRef(
    val jobId: Long,
    val revision: Long,
    val dispatchToken: String
)

/** 04T3 公开投影字段；serverNowMs 由响应生成，restore 不直出内部 evidence。 */
data class DelayedManualReplyView(
    val id: Long,
    val revision: Long,
    val status: DelayedManualReplyStatus,
    val createdAtMs: Long,
    val dueAtMs: Long,
    val serverNowMs: Long,
    val originKind: DelayedManualReplyOriginKind,
    val originId: Long?,
    val originVersion: Long?,
    val canCancel: Boolean,
    val canRetry: Boolean,
    val publicErrorCode: String?,
    val publicErrorMessage: String?,
    val sentMailRecordId: Long?
)

/** 撤回/重发请求：必须携带客户端看到的代次（I-3）。 */
data class DelayedManualReplyActionRequest(val expectedRevision: Long)

/** 结构化会议排期（快照内用毫秒表示 UTC 时刻）。 */
data class DelayedMeetingEventSnapshot(
    val startUtcMs: Long,
    val endUtcMs: Long,
    val meetingLink: String?
)

/** 原 SendPayload 的显式白名单映射；列表顺序与 SendPayload 完全一致（I-4）。 */
data class DelayedSendPayloadSnapshot(
    val orcidId: String,
    val contactId: Long,
    val inboundProcessingId: Long?,
    val accountCode: String,
    val normalizedRecipient: String,
    val subject: String,
    val finalText: String,
    val finalHtml: String,
    val inReplyTo: String?,
    val canonicalQaRuleIds: List<Long>,
    val primaryRuleId: Long?,
    val sourceAnchor: String?,
    val idempotencyRequestId: String?,
    val calendarAttachment: CalendarAttachmentSnapshot?,
    val meetingEvent: DelayedMeetingEventSnapshot?,
    val outboundAttachments: List<OutboundAttachmentSnapshot>
)

/**
 * 来源身份：只存恢复所需的线程/来源引用与账号码。
 * 不存 `MailSenderAccount` 实体、SMTP 密码或任何凭据（I-4）；worker 按 id/code 重新取实体。
 */
data class DelayedReplySourceSnapshot(
    val smtpInReplyTo: String?,
    val smtpReferences: String?,
    val anchorMailRecordId: Long?,
    val sourceMessageId: String?,
    val inboundProcessingId: Long?,
    val accountCode: String,
    val recipient: String
)

/** 普通草稿恢复：逐字段映射 `ValidatedDraftSendRef`，不依赖 internal var 默认序列化。 */
data class DelayedDraftRestore(
    val owner: String,
    val draftId: Long,
    val draftVersion: Long,
    val contactId: Long,
    val targetKind: String,
    val processingId: Long,
    val accountScope: String,
    val subject: String,
    val html: String,
    val text: String,
    val contextJson: String,
    val sendAttemptId: Long?,
    val sendVersion: Long?
) : DelayedReplyRestore

/** 已审核稿恢复：APPROVED 冻结内容与审核身份；不提供可编辑副本。 */
data class DelayedReviewRestore(
    val reviewId: Long,
    val version: Long,
    val actor: String,
    val confirmationEventId: Long?,
    val subject: String,
    val html: String,
    val text: String,
    val accountCode: String,
    val contactId: Long,
    val targetKind: String,
    val targetId: Long,
    val sendRequestId: String,
    val approvedContextToken: String
) : DelayedReplyRestore

/** 归档输入：从既有已验证 assembly 结果映射，不接受客户端声称的 verified 字段。 */
data class DelayedReplyAssemblySnapshot(
    val source: TrustReplySourceRef,
    val itemVersions: List<TrustReplyItemVersion>,
    val finalParagraphByRequestKey: Map<String, String>,
    val diagnostics: TrustReplyDiagnostics?
)

/** 旧入口恢复：保留原始输入供原详情页显式恢复，不强塞进普通草稿 context。 */
data class DelayedLegacyRestore(
    val inboundProcessingId: Long,
    val subject: String,
    val html: String,
    val text: String,
    val qaRuleIds: List<Long>,
    val ragFactCodes: List<String>,
    val assembly: DelayedReplyAssemblySnapshot?,
    val templateTextBody: String?,
    val calendarAttachment: CalendarAttachmentSnapshot?,
    val meetingEvent: DelayedMeetingEventSnapshot?,
    val outboundAttachments: List<OutboundAttachmentSnapshot>
) : DelayedReplyRestore

/** restore 判别联合；未知 kind 由 Jackson 解码失败，即 fail closed。 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(value = DelayedDraftRestore::class, name = "DRAFT"),
    JsonSubTypes.Type(value = DelayedReviewRestore::class, name = "REVIEW"),
    JsonSubTypes.Type(value = DelayedLegacyRestore::class, name = "LEGACY")
)
sealed interface DelayedReplyRestore

/** 审计/归档输入；`edited` 等为服务端观察到的事实，不接受客户端上报。 */
data class DelayedReplyEvidenceSnapshot(
    val carriesQa: Boolean,
    val canonicalFactIds: List<Long>,
    val serverSuggestedFactIds: List<Long>,
    val edited: Boolean,
    val ragFactCodes: List<String>,
    val ragCorpusFingerprint: String,
    val assembly: DelayedReplyAssemblySnapshot?,
    val templateTextBody: String?,
    val auditNote: String?,
    val bodyPreviewText: String?,
    val operator: String
)

/** 本次已通过的安全确认与已验证内容摘要；重发不得对改变后的正文复用。 */
data class DelayedReplyConfirmationSnapshot(
    val safetyWarningConfirmed: Boolean,
    val strongConfirmationRequired: Boolean,
    val strongConfirmationText: String?,
    val verifiedContentSha256: String
)

/** schemaVersion=1 完整冻结快照。 */
data class DelayedManualReplySnapshot(
    val schemaVersion: Int,
    val payload: DelayedSendPayloadSnapshot,
    val source: DelayedReplySourceSnapshot,
    val restore: DelayedReplyRestore,
    val evidence: DelayedReplyEvidenceSnapshot,
    val confirmation: DelayedReplyConfirmationSnapshot
)

/** 快照编解码失败（含未知版本、缺关键字段、超限）。入口映射为固定公开错误码。 */
class DelayedManualReplySnapshotException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/**
 * 显式白名单 JSON 编解码。
 *
 * 严格模式：未知属性、未知 schemaVersion、缺失关键字段、非法 ID/枚举/null 组合一律失败。
 * 不使用 Spring 全局 ObjectMapper，避免全局配置漂移改变持久化字节。
 */
object DelayedManualReplySnapshotCodec {

    private const val CALENDAR_ICS_MAX_BYTES = 64 * 1024
    private const val MAX_SUBJECT_LENGTH = 255
    private const val MAX_ACCOUNT_CODE_LENGTH = 100
    private const val MAX_ACTOR_LENGTH = 64
    private const val MAX_RECIPIENT_LENGTH = 320
    private const val MAX_ORCID_LENGTH = 100
    private const val MAX_FILENAME_LENGTH = 255
    private const val MAX_CONTENT_TYPE_LENGTH = 128
    private const val MAX_RAG_FACT_CODES = 2_000
    private const val MAX_RULE_IDS = 2_000
    private const val MAX_ATTACHMENTS = 50

    private val HEX64 = Regex("^[0-9a-f]{64}$")
    private val MAIL_RECORD_ANCHOR = Regex("^MAIL_RECORD:([0-9]{1,18})$")

    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(
            KotlinModule.Builder()
                .enable(KotlinFeature.StrictNullChecks)
                .build()
        )
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
        .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, false)

    fun encode(snapshot: DelayedManualReplySnapshot): String {
        validate(snapshot)
        val json = try {
            mapper.writeValueAsString(snapshot)
        } catch (ex: Exception) {
            throw DelayedManualReplySnapshotException("Delayed manual reply snapshot cannot be serialized", ex)
        }
        requireSize(json)
        return json
    }

    /** 解码并校验；未知版本/关键字段缺失直接抛出（调用方映射 SNAPSHOT_INVALID）。 */
    fun decode(json: String): DelayedManualReplySnapshot {
        requireSize(json)
        val snapshot = try {
            mapper.readValue<DelayedManualReplySnapshot>(json)
        } catch (ex: Exception) {
            throw DelayedManualReplySnapshotException("Delayed manual reply snapshot cannot be decoded", ex)
        }
        validate(snapshot)
        return snapshot
    }

    /** 读取路径的宽松入口：损坏快照返回 null，由调用方拒绝投递。 */
    fun decodeOrNull(json: String?): DelayedManualReplySnapshot? {
        if (json.isNullOrBlank()) return null
        return try {
            decode(json)
        } catch (_: DelayedManualReplySnapshotException) {
            null
        }
    }

    fun validate(snapshot: DelayedManualReplySnapshot) {
        if (snapshot.schemaVersion != DELAYED_MANUAL_REPLY_SCHEMA_VERSION) {
            fail("Unsupported snapshot schemaVersion=${snapshot.schemaVersion}")
        }
        validatePayload(snapshot.payload)
        validateSource(snapshot.payload, snapshot.source)
        validateRestore(snapshot.payload, snapshot.restore)
        validateEvidence(snapshot.evidence)
        validateConfirmation(snapshot.confirmation)
    }

    private fun validatePayload(payload: DelayedSendPayloadSnapshot) {
        requireText(payload.orcidId, "payload.orcidId", MAX_ORCID_LENGTH)
        requirePositive(payload.contactId, "payload.contactId")
        requireText(payload.accountCode, "payload.accountCode", MAX_ACCOUNT_CODE_LENGTH)
        requireText(payload.normalizedRecipient, "payload.normalizedRecipient", MAX_RECIPIENT_LENGTH)
        if (!payload.normalizedRecipient.contains('@')) {
            fail("payload.normalizedRecipient is not an email address")
        }
        requireText(payload.subject, "payload.subject", MAX_SUBJECT_LENGTH)
        requireNonEmpty(payload.finalText, "payload.finalText")
        requireNonEmpty(payload.finalHtml, "payload.finalHtml")
        requireRuleIds(payload.canonicalQaRuleIds, "payload.canonicalQaRuleIds")
        payload.primaryRuleId?.let {
            if (it <= 0) fail("payload.primaryRuleId must be positive")
        }

        val inbound = payload.inboundProcessingId
        val anchor = payload.sourceAnchor
        if ((inbound != null) == (anchor != null)) {
            fail("payload must carry exactly one of inboundProcessingId/sourceAnchor")
        }
        if (inbound != null) {
            if (inbound <= 0) fail("payload.inboundProcessingId must be positive")
            if (payload.idempotencyRequestId != null) {
                fail("payload.idempotencyRequestId must be null for inbound replies")
            }
        } else {
            val match = MAIL_RECORD_ANCHOR.matchEntire(anchor!!) ?: fail("payload.sourceAnchor is malformed")
            if (match.groupValues[1].toLong() <= 0) fail("payload.sourceAnchor record id must be positive")
            requireUuid(payload.idempotencyRequestId, "payload.idempotencyRequestId")
        }

        val calendar = payload.calendarAttachment
        val event = payload.meetingEvent
        if ((calendar == null) != (event == null)) {
            fail("payload.calendarAttachment and payload.meetingEvent must both be present or both absent")
        }
        calendar?.let { validateCalendar(it) }
        event?.let {
            if (it.startUtcMs <= 0 || it.endUtcMs <= 0) fail("payload.meetingEvent times must be positive")
            if (it.startUtcMs >= it.endUtcMs) fail("payload.meetingEvent start must precede end")
            it.meetingLink?.let { link -> requireText(link, "payload.meetingEvent.meetingLink", 2_048) }
        }

        if (payload.outboundAttachments.size > MAX_ATTACHMENTS) {
            fail("payload.outboundAttachments exceeds $MAX_ATTACHMENTS")
        }
        val attachmentIds = mutableSetOf<String>()
        payload.outboundAttachments.forEachIndexed { index, attachment ->
            validateOutboundAttachment(attachment, "payload.outboundAttachments[$index]")
            if (!attachmentIds.add(attachment.id)) {
                fail("payload.outboundAttachments contains duplicate id")
            }
        }
    }

    private fun validateCalendar(calendar: CalendarAttachmentSnapshot) {
        if (calendar.schemaVersion != 1) fail("calendarAttachment.schemaVersion must be 1")
        requireText(calendar.filename, "calendarAttachment.filename", MAX_FILENAME_LENGTH)
        requireText(calendar.contentType, "calendarAttachment.contentType", MAX_CONTENT_TYPE_LENGTH)
        requireNonEmpty(calendar.icsText, "calendarAttachment.icsText")
        if (calendar.icsText.toByteArray(Charsets.UTF_8).size > CALENDAR_ICS_MAX_BYTES) {
            fail("calendarAttachment.icsText exceeds $CALENDAR_ICS_MAX_BYTES bytes")
        }
        requireHex64(calendar.sha256, "calendarAttachment.sha256")
        requireHex64(calendar.semanticSha256, "calendarAttachment.semanticSha256")
    }

    private fun validateOutboundAttachment(attachment: OutboundAttachmentSnapshot, path: String) {
        if (attachment.schemaVersion != 1) fail("$path.schemaVersion must be 1")
        requireUuid(attachment.id, "$path.id")
        requireText(attachment.filename, "$path.filename", MAX_FILENAME_LENGTH)
        requireText(attachment.contentType, "$path.contentType", MAX_CONTENT_TYPE_LENGTH)
        if (attachment.byteLength <= 0) fail("$path.byteLength must be positive")
        requireHex64(attachment.sha256, "$path.sha256")
    }

    private fun validateSource(payload: DelayedSendPayloadSnapshot, source: DelayedReplySourceSnapshot) {
        if (source.accountCode != payload.accountCode) fail("source.accountCode must match payload.accountCode")
        if (source.recipient != payload.normalizedRecipient) fail("source.recipient must match payload.normalizedRecipient")
        if (source.inboundProcessingId != payload.inboundProcessingId) {
            fail("source.inboundProcessingId must match payload.inboundProcessingId")
        }
        source.anchorMailRecordId?.let { id ->
            val expected = payload.sourceAnchor?.let { MAIL_RECORD_ANCHOR.matchEntire(it)?.groupValues?.get(1)?.toLong() }
            if (expected != id) fail("source.anchorMailRecordId must match payload.sourceAnchor")
        }
        if (payload.sourceAnchor != null && source.anchorMailRecordId == null) {
            fail("source.anchorMailRecordId is required for conversation replies")
        }
        source.smtpInReplyTo?.let { requireText(it, "source.smtpInReplyTo", 998) }
        source.smtpReferences?.let {
            if (it.length > 8_000) fail("source.smtpReferences is too long")
        }
        source.sourceMessageId?.let { requireText(it, "source.sourceMessageId", 998) }
    }

    private fun validateRestore(payload: DelayedSendPayloadSnapshot, restore: DelayedReplyRestore) {
        when (restore) {
            is DelayedDraftRestore -> {
                requireText(restore.owner, "restore.owner", MAX_ACTOR_LENGTH)
                requirePositive(restore.draftId, "restore.draftId")
                requirePositive(restore.draftVersion, "restore.draftVersion")
                if (restore.contactId != payload.contactId) fail("restore.contactId must match payload.contactId")
                requireDraftTargetKind(restore.targetKind)
                if (restore.processingId < 0) fail("restore.processingId must not be negative")
                if (restore.accountScope.length > MAX_ACCOUNT_CODE_LENGTH) fail("restore.accountScope is too long")
                requireNonEmpty(restore.html, "restore.html")
                requireNonEmpty(restore.contextJson, "restore.contextJson")
                requireJsonObject(restore.contextJson, "restore.contextJson")
                if ((restore.sendAttemptId == null) != (restore.sendVersion == null)) {
                    fail("restore.sendAttemptId and restore.sendVersion must both be present or both absent")
                }
                restore.sendAttemptId?.let { requirePositive(it, "restore.sendAttemptId") }
                restore.sendVersion?.let { requirePositive(it, "restore.sendVersion") }
            }
            is DelayedReviewRestore -> {
                requirePositive(restore.reviewId, "restore.reviewId")
                requirePositive(restore.version, "restore.version")
                requireText(restore.actor, "restore.actor", MAX_ACTOR_LENGTH)
                restore.confirmationEventId?.let { requirePositive(it, "restore.confirmationEventId") }
                requireText(restore.subject, "restore.subject", MAX_SUBJECT_LENGTH)
                requireNonEmpty(restore.html, "restore.html")
                requireNonEmpty(restore.text, "restore.text")
                if (restore.accountCode != payload.accountCode) fail("restore.accountCode must match payload.accountCode")
                if (restore.contactId != payload.contactId) fail("restore.contactId must match payload.contactId")
                requireDraftTargetKind(restore.targetKind)
                requirePositive(restore.targetId, "restore.targetId")
                requireUuid(restore.sendRequestId, "restore.sendRequestId")
                requireHex64(restore.approvedContextToken, "restore.approvedContextToken")
            }
            is DelayedLegacyRestore -> {
                requirePositive(restore.inboundProcessingId, "restore.inboundProcessingId")
                if (restore.inboundProcessingId != payload.inboundProcessingId) {
                    fail("restore.inboundProcessingId must match payload.inboundProcessingId")
                }
                requireText(restore.subject, "restore.subject", MAX_SUBJECT_LENGTH)
                requireNonEmpty(restore.html, "restore.html")
                requireRuleIds(restore.qaRuleIds, "restore.qaRuleIds")
                if (restore.ragFactCodes.size > MAX_RAG_FACT_CODES) fail("restore.ragFactCodes exceeds $MAX_RAG_FACT_CODES")
                if (restore.ragFactCodes.any { it.isBlank() }) fail("restore.ragFactCodes contains a blank code")
                restore.assembly?.let { validateAssembly(it, "restore.assembly") }
                restore.templateTextBody?.let { requireNonEmpty(it, "restore.templateTextBody") }
                restore.calendarAttachment?.let { validateCalendar(it) }
                restore.meetingEvent?.let {
                    if (it.startUtcMs <= 0 || it.endUtcMs <= 0 || it.startUtcMs >= it.endUtcMs) {
                        fail("restore.meetingEvent times are invalid")
                    }
                }
                restore.outboundAttachments.forEachIndexed { index, attachment ->
                    validateOutboundAttachment(attachment, "restore.outboundAttachments[$index]")
                }
            }
        }
    }

    private fun validateEvidence(evidence: DelayedReplyEvidenceSnapshot) {
        requireRuleIds(evidence.canonicalFactIds, "evidence.canonicalFactIds")
        requireRuleIds(evidence.serverSuggestedFactIds, "evidence.serverSuggestedFactIds")
        if (evidence.ragFactCodes.size > MAX_RAG_FACT_CODES) fail("evidence.ragFactCodes exceeds $MAX_RAG_FACT_CODES")
        if (evidence.ragFactCodes.any { it.isBlank() }) fail("evidence.ragFactCodes contains a blank code")
        if (evidence.ragCorpusFingerprint.length > 256) fail("evidence.ragCorpusFingerprint is too long")
        evidence.assembly?.let { validateAssembly(it, "evidence.assembly") }
        evidence.templateTextBody?.let { requireNonEmpty(it, "evidence.templateTextBody") }
        evidence.auditNote?.let { if (it.length > 4_000) fail("evidence.auditNote is too long") }
        evidence.bodyPreviewText?.let { if (it.length > 4_000) fail("evidence.bodyPreviewText is too long") }
        requireText(evidence.operator, "evidence.operator", MAX_ACTOR_LENGTH)
    }

    private fun validateAssembly(assembly: DelayedReplyAssemblySnapshot, path: String) {
        if (assembly.source.sourceId <= 0) fail("$path.source.sourceId must be positive")
        assembly.itemVersions.forEachIndexed { index, version ->
            requireText(version.versionId, "$path.itemVersions[$index].versionId", 200)
            requireText(version.requestKey, "$path.itemVersions[$index].requestKey", 200)
        }
        assembly.finalParagraphByRequestKey.forEach { (key, paragraph) ->
            requireText(key, "$path.finalParagraphByRequestKey.key", 200)
            requireNonEmpty(paragraph, "$path.finalParagraphByRequestKey.value")
        }
        assembly.diagnostics?.let { diagnostics ->
            if (diagnostics.schemaVersion != TrustReplyDiagnostics.SCHEMA_VERSION) {
                fail("$path.diagnostics.schemaVersion is unsupported")
            }
        }
    }

    private fun validateConfirmation(confirmation: DelayedReplyConfirmationSnapshot) {
        requireHex64(confirmation.verifiedContentSha256, "confirmation.verifiedContentSha256")
        if (confirmation.strongConfirmationRequired) {
            if (confirmation.strongConfirmationText.isNullOrBlank()) {
                fail("confirmation.strongConfirmationText is required when strong confirmation is required")
            }
        }
    }

    private fun requireDraftTargetKind(value: String) {
        try {
            MailReplyDraftKind.valueOf(value)
        } catch (_: IllegalArgumentException) {
            fail("restore.targetKind is unsupported")
        }
    }

    private fun requireRuleIds(ids: List<Long>, path: String) {
        if (ids.size > MAX_RULE_IDS) fail("$path exceeds $MAX_RULE_IDS")
        if (ids.any { it <= 0 }) fail("$path contains a non-positive id")
    }

    private fun requireUuid(value: String?, path: String) {
        if (value.isNullOrBlank()) fail("$path is required")
        try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            fail("$path is not a UUID")
        }
    }

    private fun requireHex64(value: String, path: String) {
        if (!HEX64.matches(value)) fail("$path must be 64 lowercase hex characters")
    }

    private fun requireJsonObject(value: String, path: String) {
        try {
            if (!mapper.readTree(value).isObject) fail("$path must be a JSON object")
        } catch (ex: DelayedManualReplySnapshotException) {
            throw ex
        } catch (_: Exception) {
            fail("$path is not valid JSON")
        }
    }

    private fun requireText(value: String, path: String, maxLength: Int) {
        if (value.isBlank()) fail("$path is required")
        if (value.length > maxLength) fail("$path exceeds $maxLength characters")
    }

    private fun requireNonEmpty(value: String, path: String) {
        if (value.isEmpty()) fail("$path is required")
    }

    private fun requirePositive(value: Long, path: String) {
        if (value <= 0) fail("$path must be positive")
    }

    private fun requireSize(json: String) {
        if (json.toByteArray(Charsets.UTF_8).size > DELAYED_MANUAL_REPLY_MAX_SNAPSHOT_BYTES) {
            fail("Delayed manual reply snapshot exceeds $DELAYED_MANUAL_REPLY_MAX_SNAPSHOT_BYTES bytes")
        }
    }

    private fun fail(message: String): Nothing = throw DelayedManualReplySnapshotException(message)
}
