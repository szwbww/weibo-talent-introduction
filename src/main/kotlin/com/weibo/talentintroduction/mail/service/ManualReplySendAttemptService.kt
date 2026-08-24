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
    }

    data class SendPayload(
        val orcidId: String,
        val contactId: Long,
        val inboundProcessingId: Long,
        val accountCode: String,
        val normalizedRecipient: String,
        val subject: String,
        val finalText: String,
        val finalHtml: String,
        val inReplyTo: String?,
        val canonicalQaRuleIds: List<Long>,
        val primaryRuleId: Long?
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
        val data = ByteArrayOutputStream()
        appendLengthPrefix(data, SCHEMA_VERSION.toString())
        appendLengthPrefix(data, payload.inboundProcessingId.toString())
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
        return Fingerprint(
            fullHex = fullHex,
            shortKey = MANUAL_RICH_MAIL_TYPE_PREFIX + fullHex.take(32),
            messageId = messageId
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
        val auditTask = {
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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        auditTask()
                    }
                }
            )
        } else {
            auditTask()
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
