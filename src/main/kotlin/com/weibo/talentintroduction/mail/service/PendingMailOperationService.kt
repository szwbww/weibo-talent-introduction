package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.CompositionSuggestResult
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.qa.service.QaReplyComposer
import com.weibo.talentintroduction.qa.service.QaRuleMatch
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PendingMailOperationService(
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val expertOperatorStatusService: ExpertOperatorStatusService,
    private val expertIndexLevelOperationService: ExpertIndexLevelOperationService,
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailDeliveryService: MailDeliveryService,
    private val mailRecordRepository: MailRecordRepository,
    private val mailRecordQaRuleRepository: MailRecordQaRuleRepository,
    private val operatorActionLogService: OperatorActionLogService,
    private val qaRuleRepository: QaRuleRepository,
    private val qaMatchService: QaMatchService,
    private val mailBodyCleaner: MailBodyCleaner,
    private val mailContentService: MailContentService
) {
    @Transactional
    fun changeOperatorStatus(
        inboundProcessingId: Long,
        operatorStatus: String,
        operatorName: String?,
        note: String?
    ): ExpertContact {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val contactId = record.expertContactId
            ?: error("Inbound mail not bound to a contact")
        return expertOperatorStatusService.changeStatus(
            contactId = contactId,
            targetStatus = operatorStatus,
            operatorName = operatorName,
            note = note,
            inboundProcessingId = inboundProcessingId
        )
    }

    @Transactional
    fun changeIndexLevel(
        inboundProcessingId: Long,
        targetLevel: String,
        operatorName: String?,
        note: String?
    ): ExpertContact {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val contactId = record.expertContactId
            ?: error("Inbound mail not bound to a contact")
        return expertIndexLevelOperationService.changeLevel(
            contactId = contactId,
            targetLevel = targetLevel,
            operatorName = operatorName,
            note = note,
            inboundProcessingId = inboundProcessingId
        )
    }

    @Transactional
    fun sendQaReply(
        inboundProcessingId: Long,
        qaRuleId: Long,
        senderAccountCode: String?,
        operatorName: String?
    ): PendingMailSendResult {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val contactId = record.expertContactId
            ?: error("Inbound mail not bound to a contact")
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }

        val rule = qaRuleRepository.findById(qaRuleId)
            .orElseThrow { error("QA rule not found: $qaRuleId") }
        require(rule.enabled) { "QA rule is disabled: $qaRuleId" }

        val account = senderAccountCode
            ?.takeIf { it.isNotBlank() }
            ?.let(mailSenderAccountService::getEnabledAccount)
            ?: mailSenderAccountService.selectAccountForManualSending()

        val plainBody = rule.replyBody
        val mail = ComposedMail(
            to = contact.expertEmail,
            subject = rule.replySubject ?: "Re: ${record.subject.orEmpty()}".trim(),
            body = mailContentService.plainTextToHtml(plainBody),
            html = true,
            text = plainBody
        )
        val delivered = mailDeliveryService.send(account, mail)
        val now = LocalDateTime.now()

        val saved = mailRecordRepository.save(
            MailRecord(
                expertContactId = contactId,
                direction = "OUTBOUND",
                mailType = "MANUAL_QA_REPLY",
                senderAccountCode = account.accountCode,
                triggeredBy = TriggeredBy.OPERATOR,
                sourceInboundId = null,
                messageId = delivered.messageId,
                inReplyTo = record.messageId,
                subject = mail.subject,
                body = plainBody,
                matchedQaRuleId = rule.id,
                sendStatus = delivered.status,
                receivedAt = null,
                sentAt = now,
                createdAt = now
            )
        )

        operatorActionLogService.record(
            targetType = "INBOUND_MAIL_PROCESSING",
            targetId = inboundProcessingId,
            actionType = OperatorActionType.SEND_QA_REPLY,
            expertContactId = contactId,
            inboundProcessingId = inboundProcessingId,
            before = mapOf("inboundProcessingId" to inboundProcessingId),
            after = mapOf(
                "mailRecordId" to saved.id,
                "qaRuleId" to qaRuleId,
                "qaRuleName" to rule.displayName,
                "sendStatus" to delivered.status,
                "subject" to mail.subject,
                "bodyPreviewText" to plainBody.take(500)
            ),
            operatorName = operatorName,
            note = "QA reply sent for inbound processing $inboundProcessingId"
        )

        return PendingMailSendResult(
            contactId = contactId,
            senderAccountCode = account.accountCode,
            mailType = "MANUAL_QA_REPLY",
            subject = mail.subject,
            sendStatus = delivered.status,
            messageId = delivered.messageId
        )
    }

    @Transactional
    fun sendManualRichReply(
        inboundProcessingId: Long,
        senderAccountCode: String?,
        subject: String,
        htmlBody: String,
        textBody: String?,
        operatorName: String?
    ): PendingMailSendResult {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val contactId = record.expertContactId
            ?: error("Inbound mail not bound to a contact")
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }

        require(subject.isNotBlank()) { "Subject is required" }
        require(htmlBody.isNotBlank()) { "HTML body is required" }

        val account = senderAccountCode
            ?.takeIf { it.isNotBlank() }
            ?.let(mailSenderAccountService::getEnabledAccount)
            ?: mailSenderAccountService.selectAccountForManualSending()

        val mail = ComposedMail(
            to = contact.expertEmail,
            subject = subject,
            body = htmlBody,
            html = true
        )
        val delivered = mailDeliveryService.send(account, mail)
        val now = LocalDateTime.now()

        val saved = mailRecordRepository.save(
            MailRecord(
                expertContactId = contactId,
                direction = "OUTBOUND",
                mailType = "MANUAL_RICH_REPLY",
                senderAccountCode = account.accountCode,
                triggeredBy = TriggeredBy.OPERATOR,
                sourceInboundId = null,
                messageId = delivered.messageId,
                inReplyTo = record.messageId,
                subject = mail.subject,
                body = textBody ?: htmlBody,
                matchedQaRuleId = null,
                sendStatus = delivered.status,
                receivedAt = null,
                sentAt = now,
                createdAt = now
            )
        )

        operatorActionLogService.record(
            targetType = "INBOUND_MAIL_PROCESSING",
            targetId = inboundProcessingId,
            actionType = OperatorActionType.SEND_MANUAL_RICH_REPLY,
            expertContactId = contactId,
            inboundProcessingId = inboundProcessingId,
            before = mapOf("inboundProcessingId" to inboundProcessingId),
            after = mapOf(
                "mailRecordId" to saved.id,
                "sendStatus" to delivered.status,
                "subject" to mail.subject,
                "bodyPreviewText" to (textBody?.ifBlank { mailBodyCleaner.clean(htmlBody) } ?: mailBodyCleaner.clean(htmlBody)).take(500)
            ),
            operatorName = operatorName,
            note = "Manual rich reply sent for inbound processing $inboundProcessingId"
        )

        return PendingMailSendResult(
            contactId = contactId,
            senderAccountCode = account.accountCode,
            mailType = "MANUAL_RICH_REPLY",
            subject = mail.subject,
            sendStatus = delivered.status,
            messageId = delivered.messageId
        )
    }

    fun suggestComposedReply(inboundProcessingId: Long): CompositionSuggestResult {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val messageBody = record.cleanedBody?.takeIf { it.isNotBlank() } ?: record.body.orEmpty()
        return qaMatchService.suggestComposition(messageBody)
    }

    @Transactional
    fun sendManualComposedReply(
        inboundProcessingId: Long,
        qaRuleIds: List<Long>,
        overrideTextBody: String?,
        freeTextBody: String?,
        senderAccountCode: String?,
        operatorName: String?
    ): PendingMailSendResult {
        require(qaRuleIds.isNotEmpty()) { "At least one QA rule is required" }

        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val contactId = record.expertContactId
            ?: error("Inbound mail not bound to a contact")
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }

        val messageBody = record.cleanedBody?.takeIf { it.isNotBlank() } ?: record.body.orEmpty()
        val suggest = qaMatchService.suggestComposition(messageBody)
        val suggestedRuleIds = suggest.suggestedRuleIds

        val rules = qaRuleIds.map { ruleId ->
            val rule = qaRuleRepository.findById(ruleId)
                .orElseThrow { error("QA rule not found: $ruleId") }
            require(rule.enabled) { "QA rule is disabled: $ruleId" }
            rule
        }

        val matches = rules.map { QaRuleMatch(rule = it, matchedKeywordCount = 1) }
        val composed = QaReplyComposer.composeInOperatorOrder(matches)
        val primary = QaReplyComposer.selectPrimary(matches)
        val primaryRuleId = requireNotNull(primary.rule.id)

        val composedWithFreeText = appendFreeText(composed.replyBody, freeTextBody)
        val finalBody = overrideTextBody?.takeIf { it.isNotBlank() } ?: composedWithFreeText
        val edited = overrideTextBody?.takeIf { it.isNotBlank() }?.let { it != composedWithFreeText } ?: false
        val freeTextPreview = freeTextBody?.trim()?.takeIf { it.isNotBlank() }?.take(200)
        val subject = composed.replySubject ?: "Re: ${record.subject.orEmpty()}".trim()

        val account = senderAccountCode
            ?.takeIf { it.isNotBlank() }
            ?.let(mailSenderAccountService::getEnabledAccount)
            ?: mailSenderAccountService.selectAccountForManualSending()

        val mail = ComposedMail(
            to = contact.expertEmail,
            subject = subject,
            body = mailContentService.plainTextToHtml(finalBody),
            html = true,
            text = finalBody
        )
        val delivered = mailDeliveryService.send(account, mail)
        val now = LocalDateTime.now()

        val saved = mailRecordRepository.save(
            MailRecord(
                expertContactId = contactId,
                direction = "OUTBOUND",
                mailType = "MANUAL_COMPOSED_REPLY",
                senderAccountCode = account.accountCode,
                triggeredBy = TriggeredBy.OPERATOR,
                sourceInboundId = null,
                messageId = delivered.messageId,
                inReplyTo = record.messageId,
                subject = mail.subject,
                body = finalBody,
                matchedQaRuleId = primaryRuleId,
                sendStatus = delivered.status,
                receivedAt = null,
                sentAt = now,
                createdAt = now
            )
        )

        val mailRecordId = saved.id ?: error("Mail record id is required")
        qaRuleIds.forEachIndexed { ordinal, qaRuleId ->
            mailRecordQaRuleRepository.save(
                MailRecordQaRule(
                    mailRecordId = mailRecordId,
                    qaRuleId = qaRuleId,
                    ordinal = ordinal
                )
            )
        }

        operatorActionLogService.record(
            targetType = "INBOUND_MAIL_PROCESSING",
            targetId = inboundProcessingId,
            actionType = OperatorActionType.SEND_MANUAL_COMPOSED_REPLY,
            expertContactId = contactId,
            inboundProcessingId = inboundProcessingId,
            before = mapOf(
                "inboundProcessingId" to inboundProcessingId,
                "suggestedRuleIds" to suggestedRuleIds
            ),
            after = mapOf(
                "mailRecordId" to mailRecordId,
                "qaRuleIds" to qaRuleIds,
                "suggestedRuleIds" to suggestedRuleIds,
                "edited" to edited,
                "freeTextPreview" to freeTextPreview,
                "sendStatus" to delivered.status,
                "subject" to mail.subject,
                "bodyPreviewText" to finalBody.take(500)
            ),
            operatorName = operatorName,
            note = "Manual composed reply sent for inbound processing $inboundProcessingId"
        )

        return PendingMailSendResult(
            contactId = contactId,
            senderAccountCode = account.accountCode,
            mailType = "MANUAL_COMPOSED_REPLY",
            subject = mail.subject,
            sendStatus = delivered.status,
            messageId = delivered.messageId
        )
    }

    private fun appendFreeText(composedBody: String, freeTextBody: String?): String {
        val free = freeTextBody?.trim().orEmpty()
        if (free.isBlank()) {
            return composedBody
        }
        return if (composedBody.isBlank()) free else "$composedBody\n\n$free"
    }

    @Transactional
    fun markResolved(
        inboundProcessingId: Long,
        resolvedBy: String?,
        operatorName: String?,
        note: String?
    ) {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        require(record.processStatus == "MANUAL_REVIEW") { "Record $inboundProcessingId is not in MANUAL_REVIEW" }

        val actualOperator = operatorName?.takeIf { it.isNotBlank() } ?: resolvedBy ?: "UNKNOWN"
        val now = LocalDateTime.now()
        inboundMailProcessingRepository.save(
            record.copy(
                processStatus = "PROCESSED",
                processReason = "MANUAL_RESOLVED",
                reasonType = "MANUAL_RESOLVED",
                resolvedBy = actualOperator,
                resolvedAt = now,
                updatedAt = now
            )
        )

        val contactId = record.expertContactId
        if (contactId != null) {
            val remaining = inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(
                contactId, "MANUAL_REVIEW"
            )
            if (remaining == 0L) {
                expertContactRepository.findById(contactId).ifPresent { contact ->
                    if (contact.needsManualAttention) {
                        expertContactRepository.save(contact.copy(needsManualAttention = false))
                    }
                }
            }
        }

        operatorActionLogService.record(
            targetType = "INBOUND_MAIL_PROCESSING",
            targetId = inboundProcessingId,
            actionType = OperatorActionType.MARK_INBOUND_RESOLVED,
            expertContactId = contactId,
            inboundProcessingId = inboundProcessingId,
            before = mapOf(
                "processStatus" to "MANUAL_REVIEW",
                "processReason" to record.processReason,
                "reasonType" to record.reasonType
            ),
            after = mapOf(
                "processStatus" to "PROCESSED",
                "processReason" to "MANUAL_RESOLVED",
                "reasonType" to "MANUAL_RESOLVED"
            ),
            operatorName = actualOperator,
            note = note
        )
    }
}

data class PendingMailSendResult(
    val contactId: Long,
    val senderAccountCode: String,
    val mailType: String,
    val subject: String,
    val sendStatus: String,
    val messageId: String?
)

data class PendingQaReplyRequest(
    val qaRuleId: Long,
    val senderAccountCode: String?,
    val operatorName: String?
)

data class PendingManualRichReplyRequest(
    val senderAccountCode: String?,
    val subject: String,
    val htmlBody: String,
    val textBody: String?,
    val operatorName: String?
)

data class ComposedReplyRequest(
    val qaRuleIds: List<Long>,
    val overrideTextBody: String?,
    val freeTextBody: String? = null,
    val senderAccountCode: String?,
    val operatorName: String?
)
