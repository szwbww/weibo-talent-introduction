package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.llm.service.TrustReplyDiagnostics
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID

@Service
class ManualReplySendAttemptService(
    private val attemptRepository: MailSendAttemptRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val mailRecordQaRuleRepository: MailRecordQaRuleRepository,
    private val operatorActionLogService: OperatorActionLogService
) {
    companion object {
        private val log = LoggerFactory.getLogger(ManualReplySendAttemptService::class.java)
        private const val SCHEMA_VERSION = 1
        private const val FINGERPRINT_CONTENT_TYPE = "application/x-manual-rich-fingerprint-v1"
        private const val MANUAL_RICH_MAIL_TYPE_PREFIX = "MANUAL_RICH:"
        private const val MESSAGE_ID_TEMPLATE = "<manual-rich-%s@weibo.com>"
        private const val MAX_ERROR_SUMMARY_LENGTH = 500
        // 无来信会话自由回信的 attempt 短键：requestId 派生、内容无关（I-4）。
        // mail_type 列宽 VARCHAR(50)：前缀 20 字符 + sha256 前 30 位 = 恰好 50。
        const val CONVERSATION_REQUEST_PREFIX = "MANUAL_RICH_REQUEST:"
        const val CONVERSATION_REQUEST_HEX_LENGTH = 30
        /** 会话回信线程/审计锚点类型前缀（真实 mail_record，绝不伪造 inbound id）。 */
        const val SOURCE_ANCHOR_PREFIX = "MAIL_RECORD:"
    }

    data class SendPayload(
        val orcidId: String,
        val contactId: Long,
        /** 来信路径 = 真实 inbound_mail_processing.id；会话回信路径 = null（I-3）。 */
        val inboundProcessingId: Long?,
        val accountCode: String,
        val normalizedRecipient: String,
        val subject: String,
        val finalText: String,
        val finalHtml: String,
        val inReplyTo: String?,
        val canonicalQaRuleIds: List<Long>,
        val primaryRuleId: Long?,
        /**
         * 会话回信路径的真实线程锚点（"MAIL_RECORD:<mailRecord.id>"，I-3）。
         * 来信路径必须为 null；与 inboundProcessingId 恰有一个非空。
         */
        val sourceAnchor: String? = null,
        /** 会话回信幂等 requestId（可被 UUID.fromString 解析，I-4）；来信路径恒 null。 */
        val idempotencyRequestId: String? = null
    )

    /** findCompletedByRequestId 命中的已完成会话回信（attempt SENT + 唯一 mail_record）。 */
    data class CompletedOutboundReply(
        val attemptId: Long,
        val attemptMessageId: String,
        val attemptAccountCode: String,
        val mailRecord: MailRecord
    )

    data class Fingerprint(
        val fullHex: String,
        val shortKey: String,
        val messageId: String
    )

    enum class ClaimResult {
        CLAIMED,
        DEDUP_SENT,
        SAFE_RETRY_CLAIMED,
        IN_PROGRESS,
        UNKNOWN,
        PERMANENT_FAILED,
    }

    data class ClaimedAttempt(
        val attemptId: Long,
        val messageId: String,
        val result: ClaimResult
    )

    fun computeFingerprint(payload: SendPayload): Fingerprint {
        val messageId = String.format(MESSAGE_ID_TEMPLATE, UUID.randomUUID().toString().replace("-", ""))
        // I-5: 来信路径未带新字段时，原首段仍是原始数字 inboundProcessingId.toString()，
        // 后续字段与顺序逐字不变，部署前后同一来信/正文产生同一 fullHex/shortKey。
        // I-3/I-4: 会话回信路径首段写真实 sourceAnchor（"MAIL_RECORD:<id>"），
        // 短键改由 requestId 派生；两路径指纹身份互不混淆。
        val inboundId = payload.inboundProcessingId
        val firstSegment = when {
            inboundId != null -> {
                require(payload.sourceAnchor == null) {
                    "Mail send attempt fingerprint conflict: sourceAnchor must be null when inboundProcessingId is set"
                }
                require(payload.idempotencyRequestId == null) {
                    "Mail send attempt fingerprint conflict: idempotencyRequestId must be null when inboundProcessingId is set"
                }
                inboundId.toString()
            }
            else -> {
                val sourceAnchor = requireNotNull(payload.sourceAnchor) {
                    "Mail send attempt requires sourceAnchor when no inbound processing id"
                }
                val requestId = requireNotNull(payload.idempotencyRequestId) {
                    "Mail send attempt requires idempotencyRequestId when no inbound processing id"
                }
                // 防御性双检：入口已用 UUID.fromString(...).toString() 规范化，此处仍须可解析
                UUID.fromString(requestId.trim())
                sourceAnchor
            }
        }
        val data = ByteArrayOutputStream()
        appendLengthPrefix(data, SCHEMA_VERSION.toString())
        appendLengthPrefix(data, firstSegment)
        appendLengthPrefix(data, payload.contactId.toString())
        appendLengthPrefix(data, payload.orcidId)
        appendLengthPrefix(data, payload.accountCode)
        appendLengthPrefix(data, payload.normalizedRecipient)
        appendLengthPrefix(data, payload.subject)
        appendLengthPrefix(data, payload.finalText)
        appendLengthPrefix(data, payload.finalHtml)
        appendLengthPrefix(data, payload.inReplyTo ?: "")
        appendLengthPrefix(data, payload.canonicalQaRuleIds.joinToString(","))
        val fullHex = sha256Hex(data.toByteArray())
        val shortKey = if (inboundId != null) {
            MANUAL_RICH_MAIL_TYPE_PREFIX + fullHex.take(32)
        } else {
            conversationRequestShortKey(requireNotNull(payload.idempotencyRequestId))
        }
        return Fingerprint(
            fullHex = fullHex,
            shortKey = shortKey,
            messageId = messageId
        )
    }

    /** requestId 规范化：trim 后必须可被 UUID.fromString 解析，输出 canonical 小写形式。 */
    fun canonicalConversationRequestId(value: String): String =
        UUID.fromString(value.trim()).toString()

    /**
     * 会话回信 attempt 短键：MANUAL_RICH_REQUEST: + sha256(canonicalRequestId UTF-8) 前
     * CONVERSATION_REQUEST_HEX_LENGTH 位。内容无关，同一 requestId 恒定（I-4）。
     */
    fun conversationRequestShortKey(requestId: String): String {
        val canonical = canonicalConversationRequestId(requestId)
        return CONVERSATION_REQUEST_PREFIX +
            sha256Hex(canonical.toByteArray(Charsets.UTF_8)).take(CONVERSATION_REQUEST_HEX_LENGTH)
    }

    /**
     * 会话回信幂等收敛（I-4）：按 requestId 短键读取已完成 attempt；仅当 attempt 为 SENT
     * 且其唯一 mail_record 存在时返回。其余状态（IN_PROGRESS/UNKNOWN/FAILED…）返回空，
     * 由调用方继续既有 claim/碰撞/fail-closed 逻辑（I-10）。
     */
    fun findCompletedByRequestId(orcidId: String, requestId: String): CompletedOutboundReply? {
        val shortKey = conversationRequestShortKey(requestId)
        val attempt = attemptRepository.findByOrcidIdAndMailType(orcidId, shortKey) ?: return null
        if (attempt.status != MailSendAttemptStatus.SENT) return null
        val attemptId = attempt.id ?: return null
        val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
        return CompletedOutboundReply(
            attemptId = attemptId,
            attemptMessageId = attempt.messageId,
            attemptAccountCode = attempt.accountCode,
            mailRecord = record
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun prepareAndClaim(payload: SendPayload): ClaimedAttempt {
        val fingerprint = computeFingerprint(payload)
        val now = LocalDateTime.now()

        attemptRepository.insertIgnore(
            orcidId = payload.orcidId,
            mailType = fingerprint.shortKey,
            accountCode = payload.accountCode,
            messageId = fingerprint.messageId,
            status = MailSendAttemptStatus.PREPARED,
            recipient = payload.normalizedRecipient,
            subject = payload.subject,
            body = fingerprint.fullHex,
            contentType = FINGERPRINT_CONTENT_TYPE,
            createdAt = now,
            updatedAt = now
        )

        val attempt = attemptRepository.findByOrcidIdAndMailTypeForUpdate(
            payload.orcidId, fingerprint.shortKey
        ) ?: throw IllegalStateException(
            "Mail send attempt not found after reservation: orcidId=${payload.orcidId} mailType=${fingerprint.shortKey}"
        )

        if (attempt.contentType != FINGERPRINT_CONTENT_TYPE) {
            throw IllegalArgumentException(
                "Mail send attempt fingerprint collision: unexpected contentType=${attempt.contentType}"
            )
        }
        if (attempt.body != fingerprint.fullHex) {
            throw IllegalArgumentException(
                "Mail send attempt fingerprint collision: full hash mismatch"
            )
        }
        if (attempt.recipient != payload.normalizedRecipient) {
            throw IllegalArgumentException(
                "Mail send attempt fingerprint collision: recipient mismatch"
            )
        }

        return when (attempt.status) {
            MailSendAttemptStatus.PREPARED -> {
                val affected = attemptRepository.claimStatus(
                    requireNotNull(attempt.id),
                    MailSendAttemptStatus.PREPARED,
                    MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
                    now
                )
                if (affected <= 0) {
                    throw IllegalStateException("CAS claim failed for attempt ${attempt.id}")
                }
                ClaimedAttempt(
                    attemptId = requireNotNull(attempt.id),
                    messageId = attempt.messageId,
                    result = ClaimResult.CLAIMED
                )
            }

            MailSendAttemptStatus.SENT ->
                ClaimedAttempt(
                    attemptId = requireNotNull(attempt.id),
                    messageId = attempt.messageId,
                    result = ClaimResult.DEDUP_SENT
                )

            MailSendAttemptStatus.FAILED_SAFE_TO_RETRY -> {
                val affected = attemptRepository.claimStatus(
                    requireNotNull(attempt.id),
                    MailSendAttemptStatus.FAILED_SAFE_TO_RETRY,
                    MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
                    now
                )
                if (affected <= 0) {
                    throw IllegalStateException("CAS claim for safe retry failed for attempt ${attempt.id}")
                }
                ClaimedAttempt(
                    attemptId = requireNotNull(attempt.id),
                    messageId = attempt.messageId,
                    result = ClaimResult.SAFE_RETRY_CLAIMED
                )
            }

            MailSendAttemptStatus.DELIVERY_IN_PROGRESS ->
                ClaimedAttempt(
                    attemptId = requireNotNull(attempt.id),
                    messageId = attempt.messageId,
                    result = ClaimResult.IN_PROGRESS
                )

            MailSendAttemptStatus.DELIVERY_UNKNOWN ->
                ClaimedAttempt(
                    attemptId = requireNotNull(attempt.id),
                    messageId = attempt.messageId,
                    result = ClaimResult.UNKNOWN
                )

            else ->
                ClaimedAttempt(
                    attemptId = requireNotNull(attempt.id),
                    messageId = attempt.messageId,
                    result = ClaimResult.PERMANENT_FAILED
                )
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun finalizeSuccess(payload: SendPayload, attemptId: Long, messageId: String): Long {
        val attempt = attemptRepository.findById(attemptId).orElseThrow {
            IllegalStateException("Mail send attempt not found: $attemptId")
        }
        require(attempt.status == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
            "Cannot finalize success: attempt $attemptId is not DELIVERY_IN_PROGRESS (current: ${attempt.status})"
        }

        val now = LocalDateTime.now()
        val bodyText = payload.finalText.ifBlank { null }
        val mailBody = bodyText ?: payload.finalHtml
        val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)

        val mailRecord = if (existingRecord != null) {
            existingRecord.copy(
                senderAccountCode = payload.accountCode,
                messageId = messageId,
                inReplyTo = payload.inReplyTo,
                subject = payload.subject,
                body = mailBody,
                matchedQaRuleId = payload.primaryRuleId,
                sendStatus = "SENT",
                sentAt = now,
                errorSummary = null
            )
        } else {
            MailRecord(
                expertContactId = payload.contactId,
                direction = "OUTBOUND",
                mailType = "MANUAL_RICH_REPLY",
                senderAccountCode = payload.accountCode,
                triggeredBy = TriggeredBy.OPERATOR,
                sourceInboundId = null,
                messageId = messageId,
                inReplyTo = payload.inReplyTo,
                subject = payload.subject,
                body = mailBody,
                matchedQaRuleId = payload.primaryRuleId,
                sendStatus = "SENT",
                receivedAt = null,
                sentAt = now,
                mailSendAttemptId = attemptId,
                createdAt = existingRecord?.createdAt ?: now
            )
        }
        val savedRecord = mailRecordRepository.save(mailRecord)
        val mailRecordId = requireNotNull(savedRecord.id)

        if (payload.canonicalQaRuleIds.isNotEmpty()) {
            payload.canonicalQaRuleIds.forEachIndexed { ordinal, qaRuleId ->
                mailRecordQaRuleRepository.save(
                    MailRecordQaRule(
                        mailRecordId = mailRecordId,
                        qaRuleId = qaRuleId,
                        ordinal = ordinal
                    )
                )
            }
        }

        attemptRepository.updateStatusAndError(
            id = attemptId,
            status = MailSendAttemptStatus.SENT,
            errorSummary = null,
            now = now
        )

        return mailRecordId
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun finalizeFailure(
        payload: SendPayload,
        attemptId: Long,
        messageId: String,
        resultStatus: String,
        errorSummary: String?
    ): Long {
        val attempt = attemptRepository.findById(attemptId).orElseThrow {
            IllegalStateException("Mail send attempt not found: $attemptId")
        }
        require(attempt.status == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
            "Cannot finalize failure: attempt $attemptId is not DELIVERY_IN_PROGRESS (current: ${attempt.status})"
        }

        val now = LocalDateTime.now()
        val bodyText = payload.finalText.ifBlank { null }
        val mailBody = bodyText ?: payload.finalHtml
        val boundedError = errorSummary?.take(MAX_ERROR_SUMMARY_LENGTH)
        val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)

        val mailRecord = if (existingRecord != null) {
            existingRecord.copy(
                senderAccountCode = payload.accountCode,
                messageId = messageId,
                inReplyTo = payload.inReplyTo,
                subject = payload.subject,
                body = mailBody,
                matchedQaRuleId = payload.primaryRuleId,
                sendStatus = "FAILED",
                sentAt = null,
                errorSummary = boundedError
            )
        } else {
            MailRecord(
                expertContactId = payload.contactId,
                direction = "OUTBOUND",
                mailType = "MANUAL_RICH_REPLY",
                senderAccountCode = payload.accountCode,
                triggeredBy = TriggeredBy.OPERATOR,
                sourceInboundId = null,
                messageId = messageId,
                inReplyTo = payload.inReplyTo,
                subject = payload.subject,
                body = mailBody,
                matchedQaRuleId = payload.primaryRuleId,
                sendStatus = "FAILED",
                receivedAt = null,
                sentAt = null,
                errorSummary = boundedError,
                mailSendAttemptId = attemptId,
                createdAt = now
            )
        }
        val savedRecord = mailRecordRepository.save(mailRecord)
        val mailRecordId = requireNotNull(savedRecord.id)

        attemptRepository.updateStatusAndError(
            id = attemptId,
            status = resultStatus,
            errorSummary = boundedError,
            now = now
        )

        return mailRecordId
    }

    fun recordSendAudit(
        inboundProcessingId: Long,
        contactId: Long,
        mailRecordId: Long,
        canonicalFactIds: List<Long>,
        carriesQa: Boolean,
        delivered: DeliveredMail,
        sendSubject: String,
        bodyPreviewText: String,
        operatorName: String?,
        inboundRecord: com.weibo.talentintroduction.mail.domain.InboundMailProcessing,
        serverSuggestedFactIds: List<Long>,
        edited: Boolean?,
        note: String,
        // 04 (I-1/I-7): 服务端权威有界诊断，仅由发送方在 verified assembly 存在时传入；
        // null 时 after payload 保持既有字段逐字不变，不写伪造诊断。不进入 SendPayload
        // 或 attempt 幂等键，不新增 action row/action type。
        trustReplyDiagnostics: TrustReplyDiagnostics? = null
    ) {
        fun auditTask() {
            try {
                val actionType = if (carriesQa) {
                    OperatorActionType.SEND_MANUAL_COMPOSED_REPLY
                } else {
                    OperatorActionType.SEND_MANUAL_RICH_REPLY
                }
                val baseAfter = if (carriesQa) {
                    mapOf(
                        "mailRecordId" to mailRecordId,
                        "canonicalFactIds" to canonicalFactIds,
                        "serverSuggestedFactIds" to serverSuggestedFactIds,
                        "qaRuleIds" to canonicalFactIds,
                        "suggestedRuleIds" to serverSuggestedFactIds,
                        "draftGenerationState" to null,
                        "edited" to (edited ?: false),
                        "sendStatus" to delivered.status,
                        "subject" to sendSubject,
                        "bodyPreviewText" to bodyPreviewText
                    )
                } else {
                    mapOf(
                        "mailRecordId" to mailRecordId,
                        "sendStatus" to delivered.status,
                        "subject" to sendSubject,
                        "bodyPreviewText" to bodyPreviewText
                    )
                }
                // 04 (I-1): 两条既有分支都可携带诊断 —— 「工作台无事实但完成发送」仍能
                // 记录 unrecognized/unsupported 诊断到 SEND_MANUAL_RICH_REPLY。
                val after = if (trustReplyDiagnostics != null) {
                    baseAfter + ("trustReplyDiagnostics" to trustReplyDiagnostics)
                } else {
                    baseAfter
                }
                operatorActionLogService.record(
                    targetType = "INBOUND_MAIL_PROCESSING",
                    targetId = inboundProcessingId,
                    actionType = actionType,
                    expertContactId = contactId,
                    inboundProcessingId = inboundProcessingId,
                    before = mapOf("inboundProcessingId" to inboundProcessingId),
                    after = after,
                    operatorName = operatorName,
                    note = note
                )
            } catch (ex: Exception) {
                log.warn(
                    "Failed to record send audit for inbound {} mailRecord {}: {}",
                    inboundProcessingId, mailRecordId, ex.message, ex
                )
            }
        }
        runAfterCommit(::auditTask)
    }

    /**
     * 会话（无来信锚点）回信审计（I-11）：动作类型固定 SEND_MANUAL_RICH_REPLY，
     * target 为联系人；before 记录锚点 mail record；after 字段沿用纯人工分支的
     * mailRecordId/sendStatus/subject/bodyPreviewText；inbound_processing_id 恒 NULL。
     * 与 recordSendAudit 共享 after-commit 调度与 best-effort 吞吐（I-8）。
     */
    fun recordConversationSendAudit(
        contactId: Long,
        anchorMailRecordId: Long,
        mailRecordId: Long,
        delivered: DeliveredMail,
        sendSubject: String,
        bodyPreviewText: String,
        operatorName: String?,
        note: String
    ) {
        fun auditTask() {
            try {
                val before = mapOf("anchorMailRecordId" to anchorMailRecordId)
                val after = mapOf(
                    "mailRecordId" to mailRecordId,
                    "sendStatus" to delivered.status,
                    "subject" to sendSubject,
                    "bodyPreviewText" to bodyPreviewText
                )
                operatorActionLogService.record(
                    targetType = "EXPERT_CONTACT",
                    targetId = contactId,
                    actionType = OperatorActionType.SEND_MANUAL_RICH_REPLY,
                    expertContactId = contactId,
                    inboundProcessingId = null,
                    before = before,
                    after = after,
                    operatorName = operatorName,
                    note = note
                )
            } catch (ex: Exception) {
                log.warn(
                    "Failed to record conversation send audit for contact {} mailRecord {}: {}",
                    contactId, mailRecordId, ex.message, ex
                )
            }
        }
        runAfterCommit(::auditTask)
    }

    private fun runAfterCommit(task: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        task()
                    }
                }
            )
        } else {
            task()
        }
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
}
