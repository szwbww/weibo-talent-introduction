package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarInput
import com.weibo.talentintroduction.llm.service.TrustReplyDiagnostics
import com.weibo.talentintroduction.llm.service.TrustReplyItemVersion
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.mail.controller.ConversationManualRichReplyRequest
import java.util.UUID

/**
 * fast-p 01（总 I-1/I-3/I-4/I-10）：人工回复延迟任务的持久模型。
 *
 * 仅持久化显式白名单字段；原请求仅用于恢复，不接受客户端传入的 verified 字段。
 * 任务时间一律为 epoch 毫秒；本文件不做时钟读取。
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
    val inboundProcessingId: Long?,
    val subject: String,
    val html: String,
    val text: String,
    val qaRuleIds: List<Long>,
    val ragFactCodes: List<String>,
    val assembly: DelayedReplyAssemblySnapshot?,
    val templateTextBody: String?,
    val calendarAttachment: CalendarAttachmentSnapshot?,
    val meetingEvent: DelayedMeetingEventSnapshot?,
    val outboundAttachments: List<OutboundAttachmentSnapshot>,
    /** Restore-only request; historical v1 snapshots omit it. Never used as delivery evidence. */
    val originalInput: PendingManualRichReplyRequest? = null,
    val originalConversationInput: ConversationManualRichReplyRequest? = null
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
class DelayedManualReplySnapshotException(
    message: String,
    cause: Throwable? = null,
    val sizeLimitExceeded: Boolean = false
) : IllegalArgumentException(message, cause)

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

    // Tighten only the newly added restore request; historical frozen-payload decoding stays unchanged.
    private val originalInputMapper = mapper.copy().apply {
        disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
        coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
    }
    private val originalInputReader = originalInputMapper.readerFor(PendingManualRichReplyRequest::class.java)
    private val originalConversationInputReader = originalInputMapper.readerFor(ConversationManualRichReplyRequest::class.java)

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
            val root = mapper.readTree(json)
            root.path("restore").get("originalInput")?.takeUnless { it.isNull }?.let {
                originalInputReader.readValue<PendingManualRichReplyRequest>(it.traverse())
            }
            root.path("restore").get("originalConversationInput")?.takeUnless { it.isNull }?.let {
                originalConversationInputReader.readValue<ConversationManualRichReplyRequest>(it.traverse())
            }
            mapper.treeToValue(root, DelayedManualReplySnapshot::class.java)
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
                if (restore.inboundProcessingId != payload.inboundProcessingId) {
                    fail("restore.inboundProcessingId must match payload.inboundProcessingId")
                }
                if (payload.inboundProcessingId != null) {
                    requirePositive(payload.inboundProcessingId, "restore.inboundProcessingId")
                    if (restore.originalConversationInput != null) fail("Inbound restore cannot carry conversation input")
                } else {
                    if (restore.originalInput != null) fail("Conversation restore cannot carry inbound input")
                    val input = restore.originalConversationInput ?: fail("Original conversation request is required")
                    if (input.draftRef != null) fail("Legacy conversation cannot carry a draft reference")
                    requireUuid(input.requestId.trim(), "restore.originalConversationInput.requestId")
                    if (UUID.fromString(input.requestId.trim()).toString() != payload.idempotencyRequestId)
                        fail("Conversation request identity does not match frozen payload")
                    if ((input.accountScope?.length ?: 0) > MAX_ACCOUNT_CODE_LENGTH ||
                        (!input.accountScope.isNullOrBlank() && input.accountScope != payload.accountCode))
                        fail("Conversation account scope does not match frozen account")
                    input.anchorMailRecordId?.let {
                        requirePositive(it, "restore.originalConversationInput.anchorMailRecordId")
                        if (payload.sourceAnchor != "MAIL_RECORD:$it") fail("Conversation anchor does not match frozen source")
                    }
                    requireText(input.subject.trim(), "restore.originalConversationInput.subject", MAX_SUBJECT_LENGTH)
                    requireNonEmpty(input.htmlBody, "restore.originalConversationInput.htmlBody")
                    if (input.subject.trim() != restore.subject || input.htmlBody != restore.html ||
                        input.textBody.orEmpty() != restore.text) fail("Conversation raw content does not match restore")
                    if (input.attachmentIds != payload.outboundAttachments.map { it.id } ||
                        restore.outboundAttachments != payload.outboundAttachments)
                        fail("Conversation attachment order does not match frozen payload")
                    if (restore.qaRuleIds.isNotEmpty() || restore.ragFactCodes.isNotEmpty() ||
                        restore.assembly != null || restore.templateTextBody != null ||
                        restore.calendarAttachment != null || restore.meetingEvent != null)
                        fail("Conversation restore cannot carry inbound evidence")
                }
                requireText(restore.subject, "restore.subject", MAX_SUBJECT_LENGTH)
                requireNonEmpty(restore.html, "restore.html")
                requireRuleIds(restore.qaRuleIds, "restore.qaRuleIds")
                if (restore.ragFactCodes.size > MAX_RAG_FACT_CODES) fail("restore.ragFactCodes exceeds $MAX_RAG_FACT_CODES")
                if (restore.ragFactCodes.any { it.isBlank() }) fail("restore.ragFactCodes contains a blank code")
                restore.assembly?.let { validateAssembly(it, "restore.assembly") }
                restore.originalInput?.let { input ->
                    requireText(input.subject.trim(), "restore.originalInput.subject", MAX_SUBJECT_LENGTH)
                    requireNonEmpty(input.htmlBody, "restore.originalInput.htmlBody")
                    input.qaRuleIds?.let { requireRuleIds(it, "restore.originalInput.qaRuleIds") }
                    input.suggestedRuleIds?.let { requireRuleIds(it, "restore.originalInput.suggestedRuleIds") }
                    if (input.attachmentIds.size > MAX_ATTACHMENTS ||
                        input.attachmentIds.distinct().size != input.attachmentIds.size) fail("Invalid restore attachment ids")
                    input.attachmentIds.forEach { requireUuid(it, "restore.originalInput.attachmentIds") }
                    if ((input.ragFactCodes?.size ?: 0) > MAX_RAG_FACT_CODES) fail("Too many restore facts")
                    input.trustReplyAssembly?.let { request ->
                        requirePositive(request.source.sourceId, "restore.originalInput.assembly.sourceId")
                        request.requestedFactIds?.let { requireRuleIds(it, "restore.originalInput.assembly.factIds") }
                        request.requestFactSelections?.forEach {
                            requireText(it.requestKey, "restore.originalInput.assembly.requestKey", 200)
                            requireRuleIds(it.factRuleIds, "restore.originalInput.assembly.requestFacts")
                        }
                        request.frameSnapshot?.selection?.let { frame ->
                            listOfNotNull(frame.salutationSnippetId, frame.greetingSnippetId, frame.ackSnippetId, frame.closingSnippetId)
                                .forEach { requirePositive(it, "restore.originalInput.assembly.frame") }
                        }
                        request.finalParagraphs.forEach {
                            if (it.factIds.size > MAX_RULE_IDS) fail("Too many restore paragraph facts")
                        }
                        if (request.lockedItems.size > MAX_RULE_IDS ||
                            (request.requestFactSelections?.size ?: 0) > MAX_RULE_IDS ||
                            request.finalParagraphs.size > MAX_RULE_IDS || request.operatorFacts.size > MAX_RULE_IDS)
                            fail("Too many restore assembly items")
                    }
                }
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
            throw DelayedManualReplySnapshotException(
                "Delayed manual reply snapshot exceeds $DELAYED_MANUAL_REPLY_MAX_SNAPSHOT_BYTES bytes",
                sizeLimitExceeded = true
            )
        }
    }

    private fun fail(message: String): Nothing = throw DelayedManualReplySnapshotException(message)
}

/**
 * fast-p 02（T1/I-4）：运行时冻结准备结果 → 持久快照的显式白名单工厂。
 *
 * 只读取下列明确字段；`MailSenderAccount`/`InboundMailProcessing`/`PreparedManualReply.source`
 * 的联系人实体、SMTP 凭据、附件原件字节与绝对路径**都不进入快照**。worker 按账号码/联系
 * 人 id 重新读取实体，信封内容只来自冻结正文与 ICS。
 *
 * 该工厂不做时钟、数据库或网络访问；输出仍需经 [DelayedManualReplySnapshotCodec.encode]
 * 的严格校验。LEGACY 分别保存来信或会话原输入，实际投递来源始终来自冻结 payload/source。
 */
internal object DelayedManualReplySnapshotFactory {

    fun fromPrepared(prepared: PreparedManualReply): DelayedManualReplySnapshot {
        val payload = prepared.payload
        val evidence = prepared.evidence
        val assembly = evidence.verifiedAssembly?.let { verified ->
            val response = verified.response
            DelayedReplyAssemblySnapshot(
                source = response.source,
                itemVersions = response.itemVersions,
                finalParagraphByRequestKey = response.finalParagraphByRequestKey,
                diagnostics = response.diagnostics
            )
        }
        val payloadSnapshot = DelayedSendPayloadSnapshot(
            orcidId = payload.orcidId,
            contactId = payload.contactId,
            inboundProcessingId = payload.inboundProcessingId,
            accountCode = payload.accountCode,
            normalizedRecipient = payload.normalizedRecipient,
            subject = payload.subject,
            finalText = payload.finalText,
            finalHtml = payload.finalHtml,
            inReplyTo = payload.inReplyTo,
            canonicalQaRuleIds = payload.canonicalQaRuleIds,
            primaryRuleId = payload.primaryRuleId,
            sourceAnchor = payload.sourceAnchor,
            idempotencyRequestId = payload.idempotencyRequestId,
            calendarAttachment = payload.calendarAttachment,
            meetingEvent = meetingEventOf(payload.meetingEvent),
            outboundAttachments = payload.outboundAttachments
        )
        return DelayedManualReplySnapshot(
            schemaVersion = DELAYED_MANUAL_REPLY_SCHEMA_VERSION,
            payload = payloadSnapshot,
            source = DelayedReplySourceSnapshot(
                smtpInReplyTo = prepared.source.smtpInReplyTo,
                smtpReferences = prepared.source.smtpReferences,
                anchorMailRecordId = prepared.source.anchorMailRecordId,
                // 真实来源 Message-ID：来信路径 = processing.messageId；会话路径 = 锚点记录。
                sourceMessageId = prepared.source.inboundRecord?.messageId?.takeIf { it.isNotBlank() }
                    ?: prepared.source.identityAnchor?.messageId?.takeIf { it.isNotBlank() },
                inboundProcessingId = payload.inboundProcessingId,
                accountCode = payload.accountCode,
                recipient = payload.normalizedRecipient
            ),
            restore = restoreOf(prepared, assembly),
            evidence = DelayedReplyEvidenceSnapshot(
                carriesQa = evidence.carriesQa,
                canonicalFactIds = evidence.canonicalFactIds,
                serverSuggestedFactIds = evidence.serverSuggestedFactIds,
                edited = evidence.edited ?: false,
                ragFactCodes = evidence.ragFactCodes.orEmpty(),
                ragCorpusFingerprint = evidence.ragFingerprintAtSend.orEmpty(),
                assembly = assembly,
                templateTextBody = evidence.templateTextBody,
                auditNote = prepared.auditNote.take(MAX_AUDIT_TEXT_LENGTH),
                bodyPreviewText = prepared.bodyPreviewText.take(MAX_AUDIT_TEXT_LENGTH),
                operator = (prepared.operatorName ?: prepared.authenticatedUsername ?: "UNKNOWN")
                    .trim()
                    .ifEmpty { "UNKNOWN" }
                    .take(MAX_OPERATOR_LENGTH)
            ),
            confirmation = DelayedReplyConfirmationSnapshot(
                safetyWarningConfirmed = prepared.safetyWarningConfirmed,
                strongConfirmationRequired = prepared.requiresStrong,
                strongConfirmationText = prepared.strongConfirmationText?.takeIf { it.isNotBlank() },
                verifiedContentSha256 = verifiedContentSha256(payloadSnapshot)
            )
        )
    }

    private fun restoreOf(
        prepared: PreparedManualReply,
        assembly: DelayedReplyAssemblySnapshot?
    ): DelayedReplyRestore {
        prepared.reviewSendRef?.let { ref ->
            val draft = ref.draft
            return DelayedReviewRestore(
                reviewId = draft.id,
                version = draft.version,
                actor = ref.actor,
                confirmationEventId = ref.confirmationEventId,
                subject = draft.subject,
                html = draft.html,
                text = draft.text,
                accountCode = draft.accountCode,
                contactId = draft.contactId,
                targetKind = draft.targetKind,
                targetId = draft.targetId,
                sendRequestId = requireNotNull(draft.sendRequestId) {
                    "Approved review draft has no sendRequestId"
                },
                approvedContextToken = requireNotNull(draft.approvedContextToken) {
                    "Approved review draft has no approvedContextToken"
                }
            )
        }
        prepared.draftSendRef?.let { ref ->
            return DelayedDraftRestore(
                owner = ref.owner,
                draftId = ref.id,
                draftVersion = ref.version,
                contactId = ref.target.contactId,
                targetKind = ref.target.kind.name,
                processingId = ref.target.processingId,
                accountScope = ref.target.accountScope,
                subject = ref.content.subject,
                html = ref.content.html,
                text = ref.content.text,
                contextJson = MailReplyDraftContextCodec.serialize(ref.content.context),
                sendAttemptId = ref.sendAttemptId,
                sendVersion = ref.sendVersion
            )
        }
        val inboundId = prepared.payload.inboundProcessingId
        if (inboundId != null && prepared.originalInput == null ||
            inboundId == null && prepared.originalConversationInput == null)
            throw DelayedManualReplySnapshotException("Original legacy request is required")
        return DelayedLegacyRestore(
            inboundProcessingId = inboundId,
            subject = prepared.rawSubject,
            html = prepared.rawHtmlBody,
            text = prepared.rawTextBody.orEmpty(),
            qaRuleIds = prepared.evidence.canonicalFactIds,
            ragFactCodes = prepared.evidence.ragFactCodes.orEmpty(),
            assembly = assembly,
            templateTextBody = prepared.evidence.templateTextBody,
            calendarAttachment = prepared.payload.calendarAttachment,
            meetingEvent = meetingEventOf(prepared.payload.meetingEvent),
            outboundAttachments = prepared.payload.outboundAttachments,
            originalInput = prepared.originalInput,
            originalConversationInput = prepared.originalConversationInput
        )
    }

    /**
     * 结构化排期输入：只搬运同一次 validateAndBuild 已核对的 UTC 时刻与链接，
     * 绝不重新取当前时间、重新生成 ICS 或接受浏览器排期对象。
     */
    private fun meetingEventOf(event: MeetingCalendarInput?): DelayedMeetingEventSnapshot? =
        event?.let {
            DelayedMeetingEventSnapshot(
                startUtcMs = it.startUtc.toEpochMilli(),
                endUtcMs = it.endUtc.toEpochMilli(),
                meetingLink = it.meetingLink?.takeIf { link -> link.isNotBlank() }
            )
        }

    /**
     * 已验证内容摘要：final subject/text/html + ICS 语义摘要 + 附件 sha256 顺序。
     * 重发/撤回恢复必须以同一份正文为准，绝不把改变后的内容复用旧确认。
     */
    private fun verifiedContentSha256(payload: DelayedSendPayloadSnapshot): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        fun feed(value: String?) {
            val bytes = (value ?: "").toByteArray(Charsets.UTF_8)
            digest.update(
                byteArrayOf(
                    (bytes.size ushr 24).toByte(),
                    (bytes.size ushr 16).toByte(),
                    (bytes.size ushr 8).toByte(),
                    bytes.size.toByte()
                )
            )
            digest.update(bytes)
        }
        feed(payload.subject)
        feed(payload.finalText)
        feed(payload.finalHtml)
        feed(payload.calendarAttachment?.semanticSha256)
        payload.outboundAttachments.forEach { feed(it.sha256) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val MAX_AUDIT_TEXT_LENGTH = 4_000
    private const val MAX_OPERATOR_LENGTH = 64
}
