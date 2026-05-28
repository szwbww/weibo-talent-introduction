package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.handoff.domain.ManualHandoff
import com.weibo.talentintroduction.handoff.repository.ManualHandoffRepository
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.InboundIntent
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundIntentRepository
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.template.service.MailTemplateService
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AutoMailReplyService(
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailReceiveService: MailReceiveService,
    private val mailDeliveryService: MailDeliveryService,
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val inboundIntentRepository: InboundIntentRepository,
    private val manualHandoffRepository: ManualHandoffRepository,
    private val mailBodyCleaner: MailBodyCleaner,
    private val inboundIntentClassifier: InboundIntentClassifier,
    private val mailTemplateService: MailTemplateService,
    private val qaMatchService: QaMatchService
) {
    fun receiveAndAutoReply(accountCode: String, maxMessages: Int): AutoMailReplyBatchResult {
        val account = mailSenderAccountService.getEnabledAccount(accountCode)
        val receivedMails = mailReceiveService.fetchUnread(account, maxMessages)
        var recorded = 0
        var replied = 0
        var manualReview = 0
        var meetingInvitations = 0

        receivedMails.forEach { received ->
            if (inboundMailProcessingRepository.findBySenderAccountCodeAndImapUid(accountCode, received.imapUid) != null) {
                mailReceiveService.markSeen(account, received.imapUid)
                return@forEach
            }

            val contact = expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(received.from)
            if (contact == null) {
                confirmManualReview(account, received, null, "CONTACT_NOT_FOUND")
                manualReview += 1
                return@forEach
            }
            val contactId = contact.id ?: error("Expert contact id is required")
            if (!hasIntroductionInquiry(contactId)) {
                confirmManualReview(account, received, contactId, "INTRODUCTION_NOT_SENT")
                manualReview += 1
                return@forEach
            }

            val cleanedBody = mailBodyCleaner.clean(received.body)
            val inboundMailRecord = mailRecordRepository.save(
                MailRecord(
                    expertContactId = contactId,
                    direction = "INBOUND",
                    mailType = "REPLY",
                    messageId = received.messageId,
                    inReplyTo = received.inReplyTo,
                    subject = received.subject,
                    body = received.body,
                    cleanedBody = cleanedBody,
                    matchedQaRuleId = null,
                    sendStatus = null,
                    receivedAt = received.receivedAt,
                    sentAt = null,
                    createdAt = LocalDateTime.now()
                )
            )
            recorded += 1

            val intent = inboundIntentClassifier.classify(cleanedBody, received.subject)
            inboundIntentRepository.save(
                InboundIntent(
                    mailRecordId = inboundMailRecord.id ?: error("Inbound mail record id is required"),
                    expertContactId = contactId,
                    intentCode = intent.intentCode.name,
                    confidence = intent.confidence,
                    matchedKeywords = intent.matchedKeywords.joinToString(",").ifBlank { null },
                    autoAction = intent.autoAction.name,
                    createdAt = LocalDateTime.now()
                )
            )

            when (intent.autoAction) {
                AutoIntentAction.MANUAL_REVIEW -> {
                    val reason = manualReviewReason(intent.intentCode)
                    markManualReview(
                        contact = contact,
                        received = received,
                        status = manualReviewStatus(intent.intentCode),
                        reason = reason,
                        note = manualReviewNote(received, cleanedBody, intent)
                    )
                    confirmProcessed(account, received, contactId, "MANUAL_REVIEW", reason)
                    manualReview += 1
                    return@forEach
                }

                AutoIntentAction.CLOSE -> {
                    expertContactRepository.save(
                        contact.copy(
                            currentStatus = ConversationStatus.CLOSED.name,
                            lastReplyAt = received.receivedAt,
                            closedReason = "INTENT_${intent.intentCode.name}",
                            updatedAt = LocalDateTime.now()
                        )
                    )
                    confirmProcessed(account, received, contactId, "PROCESSED", "INTENT_${intent.intentCode.name}")
                    return@forEach
                }

                AutoIntentAction.SEND_MEETING_INVITATION -> {
                    if (hasMeetingInvitation(contactId)) {
                        markManualReview(
                            contact = contact,
                            received = received,
                            status = ConversationStatus.MEETING_SCHEDULING,
                            reason = "CONFIRM_MEETING",
                            note = manualReviewNote(received, cleanedBody, intent)
                        )
                        confirmProcessed(account, received, contactId, "MANUAL_REVIEW", "MEETING_INVITATION_ALREADY_SENT")
                        manualReview += 1
                        return@forEach
                    }

                    sendMeetingInvitation(account, contactId, received)
                    expertContactRepository.save(
                        contact.copy(
                            currentStatus = ConversationStatus.MEETING_SCHEDULING.name,
                            lastReplyAt = received.receivedAt,
                            lastMailAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now()
                        )
                    )
                    confirmProcessed(account, received, contactId, "PROCESSED", "MEETING_INVITATION_SENT")
                    meetingInvitations += 1
                    return@forEach
                }

                AutoIntentAction.QA -> Unit
            }

            val match = qaMatchService.match(cleanedBody)
            if (match == null || !match.autoReplyEnabled || match.handoffRequired) {
                markManualReview(
                    contact = contact,
                    received = received,
                    status = ConversationStatus.MANUAL_REVIEW,
                    reason = "QA_MANUAL_REVIEW",
                    note = "Subject: ${received.subject.orEmpty()}\n\n${cleanedBody.take(1200)}"
                )
                confirmProcessed(account, received, contactId, "MANUAL_REVIEW", "QA_MANUAL_REVIEW")
                manualReview += 1
                return@forEach
            }

            val reply = ComposedMail(
                to = received.from,
                subject = match.replySubject ?: "Re: ${received.subject.orEmpty()}".trim(),
                body = match.replyBody
            )
            val delivered = mailDeliveryService.send(account, reply)
            val now = LocalDateTime.now()

            mailRecordRepository.save(
                MailRecord(
                    expertContactId = contactId,
                    direction = "OUTBOUND",
                    mailType = "QA_REPLY",
                    messageId = delivered.messageId,
                    inReplyTo = received.messageId,
                    subject = reply.subject,
                    body = reply.body,
                    cleanedBody = null,
                    matchedQaRuleId = match.ruleId,
                    sendStatus = delivered.status,
                    receivedAt = null,
                    sentAt = now,
                    createdAt = now
                )
            )

            expertContactRepository.save(
                contact.copy(
                    currentStatus = ConversationStatus.QA_AUTO_REPLIED.name,
                    lastReplyAt = received.receivedAt,
                    lastMailAt = now,
                    updatedAt = now
                )
            )
            confirmProcessed(account, received, contactId, "PROCESSED", "QA_AUTO_REPLIED")
            replied += 1
        }

        return AutoMailReplyBatchResult(
            fetched = receivedMails.size,
            recorded = recorded,
            replied = replied,
            manualReview = manualReview,
            meetingInvitations = meetingInvitations
        )
    }

    private fun hasIntroductionInquiry(contactId: Long): Boolean =
        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
            expertContactId = contactId,
            direction = "OUTBOUND",
            mailType = "INTRODUCTION"
        )

    private fun hasMeetingInvitation(contactId: Long): Boolean =
        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
            expertContactId = contactId,
            direction = "OUTBOUND",
            mailType = "MEETING_INVITATION"
        )

    private fun markManualReview(
        contact: ExpertContact,
        received: ReceivedMail,
        status: ConversationStatus,
        reason: String,
        note: String?
    ) {
        val contactId = contact.id ?: error("Expert contact id is required")
        createManualHandoffIfAbsent(contactId, reason, note)
        expertContactRepository.save(
            contact.copy(
                currentStatus = status.name,
                lastReplyAt = received.receivedAt,
                manualHandoffRequired = true,
                updatedAt = LocalDateTime.now()
            )
        )
    }

    private fun createManualHandoffIfAbsent(contactId: Long, reason: String, note: String?) {
        val existing = manualHandoffRepository
            .findFirstByExpertContactIdAndReasonAndHandoffStatusOrderByUpdatedAtDesc(contactId, reason, "PENDING")
        if (existing != null) {
            return
        }

        val now = LocalDateTime.now()
        manualHandoffRepository.save(
            ManualHandoff(
                expertContactId = contactId,
                reason = reason,
                handoffStatus = "PENDING",
                assignedTo = null,
                note = note?.take(2000),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun manualReviewStatus(intentCode: InboundIntentCode): ConversationStatus =
        when (intentCode) {
            InboundIntentCode.MEETING_TIME_PROVIDED,
            InboundIntentCode.MEETING_REQUESTED -> ConversationStatus.MEETING_SCHEDULING

            InboundIntentCode.CV_ATTACHED,
            InboundIntentCode.DOCS_ATTACHED,
            InboundIntentCode.PASSPORT_UPDATED -> ConversationStatus.MATERIALS_PARTIAL

            else -> ConversationStatus.MANUAL_REVIEW
        }

    private fun manualReviewReason(intentCode: InboundIntentCode): String =
        when (intentCode) {
            InboundIntentCode.MEETING_TIME_PROVIDED,
            InboundIntentCode.MEETING_REQUESTED -> "CONFIRM_MEETING"

            InboundIntentCode.CV_ATTACHED,
            InboundIntentCode.DOCS_ATTACHED,
            InboundIntentCode.PASSPORT_UPDATED -> "REVIEW_DOCUMENT"

            InboundIntentCode.ASK_FUNDING,
            InboundIntentCode.ASK_CONFIDENTIALITY -> "HANDLE_RISKY_QUESTION"

            else -> "REVIEW_INBOUND_INTENT_${intentCode.name}"
        }

    private fun manualReviewNote(
        received: ReceivedMail,
        cleanedBody: String,
        intent: InboundIntentClassification
    ): String =
        listOf(
            "Intent: ${intent.intentCode.name}",
            "Confidence: ${intent.confidence}",
            "Matched keywords: ${intent.matchedKeywords.joinToString(",").ifBlank { "-" }}",
            "Subject: ${received.subject.orEmpty()}",
            "",
            cleanedBody
        ).joinToString("\n").take(2000)

    private fun sendMeetingInvitation(
        account: MailSenderAccount,
        contactId: Long,
        received: ReceivedMail
    ) {
        val rendered = mailTemplateService.render(
            templateCode = "MEETING_INVITATION",
            variables = mailTemplateVariables(account)
        )
        val mail = ComposedMail(
            to = received.from,
            subject = rendered.subject ?: "Re: ${received.subject.orEmpty()}".trim(),
            body = rendered.body
        )
        val delivered = mailDeliveryService.send(account, mail)
        val now = LocalDateTime.now()
        mailRecordRepository.save(
            MailRecord(
                expertContactId = contactId,
                direction = "OUTBOUND",
                mailType = "MEETING_INVITATION",
                messageId = delivered.messageId,
                inReplyTo = received.messageId,
                subject = mail.subject,
                body = mail.body,
                cleanedBody = null,
                matchedQaRuleId = null,
                sendStatus = delivered.status,
                receivedAt = null,
                sentAt = now,
                createdAt = now
            )
        )
    }

    private fun mailTemplateVariables(account: MailSenderAccount): Map<String, String> =
        mapOf(
            "senderEmail" to account.senderEmail,
            "senderName" to account.senderName,
            "senderTitle" to account.senderTitle.orEmpty(),
            "teamName" to account.teamName.orEmpty(),
            "countryName" to account.countryName.orEmpty(),
            "senderDisplayName" to account.senderDisplayName.orEmpty()
        )

    private fun confirmManualReview(
        account: MailSenderAccount,
        received: ReceivedMail,
        expertContactId: Long?,
        reason: String
    ) {
        confirmProcessed(account, received, expertContactId, "MANUAL_REVIEW", reason)
    }

    private fun confirmProcessed(
        account: MailSenderAccount,
        received: ReceivedMail,
        expertContactId: Long?,
        status: String,
        reason: String
    ) {
        val now = LocalDateTime.now()
        inboundMailProcessingRepository.save(
            InboundMailProcessing(
                senderAccountCode = account.accountCode,
                imapUid = received.imapUid,
                messageId = received.messageId,
                fromEmail = received.from,
                subject = received.subject,
                receivedAt = received.receivedAt,
                processStatus = status,
                processReason = reason,
                expertContactId = expertContactId,
                createdAt = now,
                updatedAt = now
            )
        )
        mailReceiveService.markSeen(account, received.imapUid)
    }
}

data class AutoMailReplyBatchResult(
    val fetched: Int,
    val recorded: Int,
    val replied: Int,
    val manualReview: Int,
    val meetingInvitations: Int = 0
)
