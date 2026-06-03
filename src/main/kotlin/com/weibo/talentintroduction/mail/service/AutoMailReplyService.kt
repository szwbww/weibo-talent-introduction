package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.handoff.domain.ManualHandoff
import com.weibo.talentintroduction.handoff.repository.ManualHandoffRepository
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.InboundIntent
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.InboundIntentRepository
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.campaign.service.MeetingScheduleService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.template.service.MailTemplateService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

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
    private val mailAttachmentService: MailAttachmentService,
    private val mailBodyCleaner: MailBodyCleaner,
    private val inboundIntentClassifier: InboundIntentClassifier,
    private val mailTemplateService: MailTemplateService,
    private val qaMatchService: QaMatchService,
    private val conversationStateService: ConversationStateService,
    private val meetingScheduleService: MeetingScheduleService,
    private val expertEmailAliasService: ExpertEmailAliasService,
    private val expertIndexWriterService: ExpertIndexWriterService
) {
    private val log = LoggerFactory.getLogger(AutoMailReplyService::class.java)

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

            val contact = expertEmailAliasService.findContactByEmailOrAlias(received.from)
            if (contact == null) {
                val cleanedBody = mailBodyCleaner.clean(received.body)
                confirmManualReviewWithBody(
                    account = account,
                    received = received,
                    expertContactId = null,
                    reason = "CONTACT_NOT_FOUND",
                    reasonType = "UNMATCHED_CONTACT",
                    cleanedBody = cleanedBody
                )
                manualReview += 1
                return@forEach
            }
            val contactId = contact.id ?: error("Expert contact id is required")
            if (!hasIntroductionInquiry(contactId)) {
                confirmManualReview(account, received, contactId, "INTRODUCTION_NOT_SENT", "UNCLEAR_INTENT")
                manualReview += 1
                return@forEach
            }

            if (!contact.autoReplyEnabled ||
                contact.currentStatus == ConversationStatus.MANUAL_HANDOFF.name
            ) {
                val reason = when {
                    !contact.autoReplyEnabled -> "AUTO_REPLY_DISABLED"
                    else -> "MANUAL_HANDOFF_STATUS"
                }
                val cleanedBody = mailBodyCleaner.clean(received.body)
                val inboundRecord = saveMailRecord(account, contactId, received, cleanedBody)
                val inboundMailRecordId = inboundRecord.id ?: error("Inbound mail record id is required")
                mailAttachmentService.saveInboundAttachments(
                    expertContactId = contactId,
                    mailRecordId = inboundMailRecordId,
                    attachments = received.attachments
                )
                val classifiedIntent = inboundIntentClassifier.classify(cleanedBody, received.subject)
                val intent = effectiveIntent(classifiedIntent, received.attachments)
                inboundIntentRepository.save(
                    InboundIntent(
                        mailRecordId = inboundMailRecordId,
                        expertContactId = contactId,
                        intentCode = intent.intentCode.name,
                        confidence = intent.confidence,
                        matchedKeywords = intent.matchedKeywords.joinToString(",").ifBlank { null },
                        autoAction = intent.autoAction.name,
                        createdAt = LocalDateTime.now()
                    )
                )
                recorded += 1
                if (contact.currentStatus == ConversationStatus.MANUAL_HANDOFF.name) {
                    if (!contact.needsManualAttention) {
                        expertContactRepository.save(contact.copy(needsManualAttention = true))
                    }
                    createManualHandoffIfAbsent(contactId, reason, "Auto-reply skipped: contact already in MANUAL_HANDOFF")
                    confirmProcessed(account, received, contactId, "MANUAL_REVIEW", reason, "UNCLEAR_INTENT")
                } else {
                    markManualReview(
                        contact = contact,
                        received = received,
                        status = ConversationStatus.MANUAL_HANDOFF,
                        reason = reason,
                        note = "Auto-reply skipped: $reason. Status: ${contact.currentStatus}"
                    )
                    confirmProcessed(account, received, contactId, "MANUAL_REVIEW", reason, "UNCLEAR_INTENT")
                }
                manualReview += 1
                return@forEach
            }

            val cleanedBody = mailBodyCleaner.clean(received.body)
            val inboundMailRecord = mailRecordRepository.save(
                MailRecord(
                    expertContactId = contactId,
                    direction = "INBOUND",
                    mailType = "REPLY",
                    senderAccountCode = account.accountCode,
                    triggeredBy = null,
                    sourceInboundId = null,
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
            val inboundMailRecordId = inboundMailRecord.id ?: error("Inbound mail record id is required")

            val savedDocuments = mailAttachmentService.saveInboundAttachments(
                expertContactId = contactId,
                mailRecordId = inboundMailRecordId,
                attachments = received.attachments
            )
            val classifiedIntent = inboundIntentClassifier.classify(cleanedBody, received.subject)
            val intent = effectiveIntent(classifiedIntent, received.attachments)
            inboundIntentRepository.save(
                InboundIntent(
                    mailRecordId = inboundMailRecordId,
                    expertContactId = contactId,
                    intentCode = intent.intentCode.name,
                    confidence = intent.confidence,
                    matchedKeywords = intent.matchedKeywords.joinToString(",").ifBlank { null },
                    autoAction = intent.autoAction.name,
                    createdAt = LocalDateTime.now()
                )
            )

            if (intent.intentCode == InboundIntentCode.MEETING_TIME_PROVIDED || intent.intentCode == InboundIntentCode.MEETING_REQUESTED) {
                meetingScheduleService.extractAndCreate(contactId, inboundMailRecord)
            }

            when (intent.autoAction) {
                AutoIntentAction.MANUAL_REVIEW -> {
                    val reason = manualReviewReason(intent.intentCode)
                    val promoted = promoteIfFirstReply(contact, received, inboundMailRecordId)
                    markManualReview(
                        contact = promoted ?: contact,
                        received = received,
                        status = manualReviewStatus(intent.intentCode),
                        reason = reason,
                        note = manualReviewNote(received, cleanedBody, intent, savedDocuments.size)
                    )
                    confirmManualReviewWithBody(
                        account = account,
                        received = received,
                        expertContactId = contactId,
                        reason = reason,
                        reasonType = "UNCLEAR_INTENT",
                        cleanedBody = cleanedBody
                    )
                    manualReview += 1
                    return@forEach
                }

                AutoIntentAction.CLOSE -> {
                    markManualReview(
                        contact = contact,
                        received = received,
                        status = ConversationStatus.MANUAL_HANDOFF,
                        reason = "INTENT_${intent.intentCode.name}",
                        note = manualReviewNote(received, cleanedBody, intent, savedDocuments.size)
                    )
                    val reasonType = if (intent.intentCode == InboundIntentCode.NOT_INTERESTED)
                        "NOT_INTERESTED" else "UNCLEAR_INTENT"
                    confirmManualReviewWithBody(
                        account = account,
                        received = received,
                        expertContactId = contactId,
                        reason = "INTENT_${intent.intentCode.name}",
                        reasonType = reasonType,
                        cleanedBody = cleanedBody
                    )
                    manualReview += 1
                    return@forEach
                }

                AutoIntentAction.SEND_MEETING_INVITATION -> {
                    if (hasMeetingInvitation(contactId)) {
                        val promoted = promoteIfFirstReply(contact, received, inboundMailRecordId)
                        markManualReview(
                            contact = promoted ?: contact,
                            received = received,
                            status = ConversationStatus.MEETING_SCHEDULING,
                            reason = "CONFIRM_MEETING",
                            note = manualReviewNote(received, cleanedBody, intent, savedDocuments.size)
                        )
                        confirmManualReviewWithBody(
                            account = account,
                            received = received,
                            expertContactId = contactId,
                            reason = "MEETING_INVITATION_ALREADY_SENT",
                            reasonType = "UNCLEAR_INTENT",
                            cleanedBody = cleanedBody
                        )
                        manualReview += 1
                        return@forEach
                    }

                    sendMeetingInvitation(account, contactId, received, inboundMailRecordId)
                    val promoted = promoteIfFirstReply(contact, received, inboundMailRecordId)
                    val meetingContact = conversationStateService.transition(
                        contact = promoted ?: contact,
                        toStatus = ConversationStatus.MEETING_SCHEDULING,
                        reason = "MEETING_INVITATION_SENT",
                        source = "AUTO_REPLY"
                    ) {
                        it.copy(
                            lastReplyAt = received.receivedAt,
                            lastMailAt = LocalDateTime.now()
                        )
                    }
                    if (meetingContact.applicationIndexed) {
                        expertIndexWriterService.syncApplicationStatus(meetingContact, "MEETING_INVITATION_SENT")
                    }
                    confirmProcessed(
                        account,
                        received,
                        contactId,
                        "PROCESSED",
                        "AUTO_MEETING_INVITED",
                        "AUTO_MEETING_INVITED"
                    )
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
                    status = ConversationStatus.MANUAL_HANDOFF,
                    reason = "QA_NO_MATCH",
                    note = "Subject: ${received.subject.orEmpty()}\n\n${cleanedBody.take(1200)}"
                )
                confirmManualReviewWithBody(
                    account = account,
                    received = received,
                    expertContactId = contactId,
                    reason = "QA_NO_MATCH",
                    reasonType = "QA_NO_MATCH",
                    cleanedBody = cleanedBody
                )
                manualReview += 1; return@forEach
            }

            val promoted = promoteIfFirstReply(contact, received, inboundMailRecordId)
            val effectiveContact = promoted ?: contact
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
                    senderAccountCode = account.accountCode,
                    triggeredBy = TriggeredBy.SYSTEM,
                    sourceInboundId = inboundMailRecordId,
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

            val qaContact = conversationStateService.transition(
                contact = effectiveContact,
                toStatus = ConversationStatus.QA_AUTO_REPLIED,
                reason = "QA_AUTO_REPLIED",
                source = "AUTO_REPLY",
                now = now
            ) {
                it.copy(
                    lastReplyAt = received.receivedAt,
                    lastMailAt = now
                )
            }
            if (qaContact.applicationIndexed) {
                expertIndexWriterService.syncApplicationStatus(qaContact, "QA_AUTO_REPLIED")
            }
            confirmProcessed(account, received, contactId, "PROCESSED", "QA_AUTO_REPLIED", "AUTO_QA_REPLIED")
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

    private fun saveMailRecord(
        account: MailSenderAccount,
        contactId: Long,
        received: ReceivedMail,
        cleanedBody: String
    ): MailRecord = mailRecordRepository.save(
        MailRecord(
            expertContactId = contactId,
            direction = "INBOUND",
            mailType = "REPLY",
            senderAccountCode = account.accountCode,
            triggeredBy = null,
            sourceInboundId = null,
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

    private fun promoteIfFirstReply(contact: ExpertContact, received: ReceivedMail, sourceInboundId: Long): ExpertContact? {
        if (contact.applicationIndexed) return null
        if (contact.orcidId.isBlank()) return null
        val now = received.receivedAt
        val firstReplyAt = contact.firstReplyAt ?: now
        val firstReplyInstant = firstReplyAt.toInstant(ZoneId.systemDefault().rules.getOffset(firstReplyAt))
        val saved = expertContactRepository.save(
            contact.copy(firstReplyAt = firstReplyAt)
        )
        try {
            val ok = expertIndexWriterService.promoteToApplication(
                orcid = contact.orcidId,
                contact = saved,
                firstReplyAt = firstReplyInstant,
                sourceInboundId = sourceInboundId,
                triggeredBy = TriggeredBy.SYSTEM
            )
            if (ok) {
                return expertContactRepository.save(saved.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
            }
        } catch (e: Exception) {
            log.warn("ES promotion failed for contact {} (orcid={}), will retry on reindex", contact.id, contact.orcidId, e)
        }
        return saved
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
        val updated = conversationStateService.transition(
            contact = contact,
            toStatus = status,
            reason = reason,
            source = "AUTO_REPLY"
        ) {
            it.copy(
                lastReplyAt = received.receivedAt,
                manualHandoffRequired = true,
                autoReplyEnabled = false,
                needsManualAttention = true
            )
        }
        if (updated.applicationIndexed) {
            expertIndexWriterService.syncApplicationStatus(updated, reason)
        }
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

    private fun manualReviewStatus(@Suppress("UNUSED_PARAMETER") intentCode: InboundIntentCode): ConversationStatus =
        ConversationStatus.MANUAL_HANDOFF

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
        intent: InboundIntentClassification,
        savedDocumentCount: Int
    ): String =
        listOf(
            "Intent: ${intent.intentCode.name}",
            "Confidence: ${intent.confidence}",
            "Matched keywords: ${intent.matchedKeywords.joinToString(",").ifBlank { "-" }}",
            "Saved documents: $savedDocumentCount",
            "Subject: ${received.subject.orEmpty()}",
            "",
            cleanedBody
        ).joinToString("\n").take(2000)

    private fun effectiveIntent(
        classified: InboundIntentClassification,
        attachments: List<ReceivedMailAttachment>
    ): InboundIntentClassification {
        if (attachments.isEmpty() || classified.intentCode != InboundIntentCode.UNKNOWN) {
            return classified
        }
        val attachmentIntent = mailAttachmentService.inferPrimaryIntentFromAttachments(attachments)
            ?: return classified
        return InboundIntentClassification(
            intentCode = attachmentIntent,
            confidence = 80,
            matchedKeywords = attachments.map { it.fileName },
            autoAction = AutoIntentAction.MANUAL_REVIEW
        )
    }

    private fun sendMeetingInvitation(
        account: MailSenderAccount,
        contactId: Long,
        received: ReceivedMail,
        sourceInboundId: Long
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
                senderAccountCode = account.accountCode,
                triggeredBy = TriggeredBy.SYSTEM,
                sourceInboundId = sourceInboundId,
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
        reason: String,
        reasonType: String? = "UNCLEAR_INTENT"
    ) {
        confirmProcessed(account, received, expertContactId, "MANUAL_REVIEW", reason, reasonType)
    }

    private fun confirmManualReviewWithBody(
        account: MailSenderAccount,
        received: ReceivedMail,
        expertContactId: Long?,
        reason: String,
        reasonType: String?,
        cleanedBody: String
    ) {
        val now = LocalDateTime.now()
        inboundMailProcessingRepository.save(
            InboundMailProcessing(
                senderAccountCode = account.accountCode,
                imapUid = received.imapUid,
                messageId = received.messageId,
                inReplyTo = received.inReplyTo,
                fromEmail = received.from,
                subject = received.subject,
                body = received.body,
                cleanedBody = cleanedBody,
                receivedAt = received.receivedAt,
                processStatus = "MANUAL_REVIEW",
                processReason = reason,
                reasonType = reasonType,
                expertContactId = expertContactId,
                createdAt = now,
                updatedAt = now
            )
        )
        mailReceiveService.markSeen(account, received.imapUid)
    }

    private fun confirmProcessed(
        account: MailSenderAccount,
        received: ReceivedMail,
        expertContactId: Long?,
        status: String,
        reason: String,
        reasonType: String? = null
    ) {
        val now = LocalDateTime.now()
        inboundMailProcessingRepository.save(
            InboundMailProcessing(
                senderAccountCode = account.accountCode,
                imapUid = received.imapUid,
                messageId = received.messageId,
                inReplyTo = received.inReplyTo,
                fromEmail = received.from,
                subject = received.subject,
                body = received.body,
                receivedAt = received.receivedAt,
                processStatus = status,
                processReason = reason,
                reasonType = reasonType,
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
