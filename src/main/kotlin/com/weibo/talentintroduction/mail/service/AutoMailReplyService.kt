package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.OperatorStatus
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.handoff.domain.ManualHandoff
import com.weibo.talentintroduction.handoff.repository.ManualHandoffRepository
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.InboundIntent
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.InboundIntentRepository
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.campaign.service.MeetingScheduleService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AutoMailReplyService(
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailReceiveService: MailReceiveService,
    private val mailDeliveryService: MailDeliveryService,
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val mailRecordQaRuleRepository: MailRecordQaRuleRepository,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val inboundIntentRepository: InboundIntentRepository,
    private val manualHandoffRepository: ManualHandoffRepository,
    private val mailAttachmentService: MailAttachmentService,
    private val mailBodyCleaner: MailBodyCleaner,
    private val inboundIntentClassifier: InboundIntentClassifier,
    private val mailComposeTemplateService: MailComposeTemplateService,
    private val groundedAutoReplyDecisionService: GroundedAutoReplyDecisionService,
    private val conversationStateService: ConversationStateService,
    private val meetingScheduleService: MeetingScheduleService,
    private val expertEmailAliasService: ExpertEmailAliasService,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val automaticApplicationPromotionService: AutomaticApplicationPromotionService,
    private val expertOperatorStatusService: ExpertOperatorStatusService,
    private val bounceDetector: BounceDetector,
    private val bounceCollectionService: BounceCollectionService,
    private val bounceRateMonitorService: BounceRateMonitorService,
    private val emailSuppressionService: EmailSuppressionService,
    private val selfCheckProbeDetector: SelfCheckProbeDetector,
    private val dmarcReportDetector: DmarcReportDetector,
    private val dmarcReportIngestService: DmarcReportIngestService,
    private val mailContentService: MailContentService,
    private val mailInboxCursorService: MailInboxCursorService,
    private val autoReplySettingService: AutoReplySettingService,
    private val inboundMailTagService: InboundMailTagService,
    private val mailVariableService: MailVariableService
) {
    private val log = LoggerFactory.getLogger(AutoMailReplyService::class.java)
    private val duplicateInboundWindowMinutes = 30L

    @org.springframework.transaction.annotation.Transactional
    fun processSingle(
        account: MailSenderAccount,
        received: ReceivedMail,
        skipImapAck: Boolean = false
    ): SinglePipelineResult {
        val accountCode = account.accountCode
        if (inboundMailProcessingRepository.findBySenderAccountCodeAndImapUid(accountCode, received.imapUid) != null) {
            if (!skipImapAck) mailReceiveService.markSeen(account, received.imapUid)
            return SinglePipelineResult.duplicate(received.imapUid)
        }

        val contact = expertEmailAliasService.findContactByEmailOrAlias(received.from)
        if (contact == null) {
            val cleanedBody = mailBodyCleaner.clean(received.body)
            val inboundProcessing = confirmManualReviewWithBody(
                account = account,
                received = received,
                expertContactId = null,
                reason = "CONTACT_NOT_FOUND",
                reasonType = "UNMATCHED_CONTACT",
                cleanedBody = cleanedBody,
                skipImapAck = skipImapAck
            )
            val inboundProcessingId = inboundProcessing.id ?: error("Inbound mail processing id is required")
            mailAttachmentService.saveUnmatchedAttachments(inboundProcessingId, received.attachments)
            return SinglePipelineResult(
                outcome = SinglePipelineOutcome.UNMATCHED_CONTACT,
                recorded = false,
                reason = "CONTACT_NOT_FOUND"
            )
        }
        val contactId = contact.id ?: error("Expert contact id is required")
        if (!hasIntroductionInquiry(contactId)) {
            confirmManualReview(account, received, contactId, "INTRODUCTION_NOT_SENT", "UNCLEAR_INTENT", skipImapAck)
            return SinglePipelineResult(
                outcome = SinglePipelineOutcome.INTRODUCTION_NOT_SENT,
                recorded = false,
                expertContactId = contactId,
                reason = "INTRODUCTION_NOT_SENT"
            )
        }

        if (!autoReplySettingService.isGlobalEnabled()) {
            val cleanedBody = mailBodyCleaner.clean(received.body)
            val inboundRecord = saveMailRecord(account, contactId, received, cleanedBody)
            val inboundMailRecordId = inboundRecord.id ?: error("Inbound mail record id is required")
            mailAttachmentService.saveInboundAttachments(
                expertContactId = contactId,
                mailRecordId = inboundMailRecordId,
                attachments = received.attachments
            )
            applyPromotionAndStatus(
                contact = contact,
                receivedAt = received.receivedAt,
                inboundMailRecordId = inboundMailRecordId,
                attachmentCount = received.attachments.size
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
            captureUnsubscribeIfPresent(received.from, cleanedBody)
            confirmProcessed(
                account = account,
                received = received,
                expertContactId = contactId,
                status = "MANUAL_REVIEW",
                reason = "GLOBAL_AUTO_REPLY_DISABLED",
                reasonType = "GLOBAL_AUTO_REPLY_DISABLED",
                skipImapAck = skipImapAck,
                cleanedBody = cleanedBody
            )
            return SinglePipelineResult(
                outcome = SinglePipelineOutcome.GLOBAL_AUTO_REPLY_DISABLED,
                recorded = true,
                expertContactId = contactId,
                inboundMailRecordId = inboundMailRecordId,
                intentCode = intent.intentCode,
                autoAction = intent.autoAction,
                matchedKeywords = intent.matchedKeywords,
                newStatus = contact.currentStatus,
                previousStatus = contact.currentStatus,
                reason = "GLOBAL_AUTO_REPLY_DISABLED"
            )
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
            val disabledContact = applyPromotionAndStatus(
                contact = contact,
                receivedAt = received.receivedAt,
                inboundMailRecordId = inboundMailRecordId,
                attachmentCount = received.attachments.size
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
            captureUnsubscribeIfPresent(received.from, cleanedBody)
            if (disabledContact.currentStatus == ConversationStatus.MANUAL_HANDOFF.name) {
                if (!disabledContact.needsManualAttention) {
                    expertContactRepository.save(disabledContact.copy(needsManualAttention = true))
                }
                createManualHandoffIfAbsent(contactId, reason, "Auto-reply skipped: contact already in MANUAL_HANDOFF")
                confirmProcessed(account, received, contactId, "MANUAL_REVIEW", reason, "UNCLEAR_INTENT", skipImapAck)
            } else {
                markManualReview(
                    contact = disabledContact,
                    received = received,
                    status = ConversationStatus.MANUAL_HANDOFF,
                    reason = reason,
                    note = "Auto-reply skipped: $reason. Status: ${disabledContact.currentStatus}"
                )
                confirmProcessed(account, received, contactId, "MANUAL_REVIEW", reason, "UNCLEAR_INTENT", skipImapAck)
            }
            return SinglePipelineResult(
                outcome = if (!contact.autoReplyEnabled) SinglePipelineOutcome.AUTO_REPLY_DISABLED
                          else SinglePipelineOutcome.MANUAL_HANDOFF_STATUS,
                recorded = true,
                expertContactId = contactId,
                inboundMailRecordId = inboundMailRecordId,
                intentCode = intent.intentCode,
                autoAction = intent.autoAction,
                matchedKeywords = intent.matchedKeywords,
                newStatus = ConversationStatus.MANUAL_HANDOFF.name,
                previousStatus = disabledContact.currentStatus,
                reason = reason
            )
        }

        val cleanedBody = mailBodyCleaner.clean(received.body)
        val duplicateInbound = mailRecordRepository.findRecentDuplicateInbound(
            expertContactId = contactId,
            senderAccountCode = account.accountCode,
            subject = received.subject,
            cleanedBody = cleanedBody,
            since = received.receivedAt.minusMinutes(duplicateInboundWindowMinutes),
            receivedAt = received.receivedAt
        )
        if (duplicateInbound != null) {
            val inboundRecord = saveMailRecord(account, contactId, received, cleanedBody)
            val inboundMailRecordId = inboundRecord.id ?: error("Inbound mail record id is required")
            mailAttachmentService.saveInboundAttachments(
                expertContactId = contactId,
                mailRecordId = inboundMailRecordId,
                attachments = received.attachments
            )
            confirmProcessed(
                account = account,
                received = received,
                expertContactId = contactId,
                status = "PROCESSED",
                reason = "DUPLICATE_INBOUND_MESSAGE",
                reasonType = "DUPLICATE_INBOUND_MESSAGE",
                skipImapAck = skipImapAck,
                cleanedBody = cleanedBody
            )
            return SinglePipelineResult(
                outcome = SinglePipelineOutcome.DUPLICATE_INBOUND_MESSAGE,
                recorded = true,
                expertContactId = contactId,
                inboundMailRecordId = inboundMailRecordId,
                reason = "DUPLICATE_INBOUND_MESSAGE"
            )
        }
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
        val inboundMailRecordId = inboundMailRecord.id ?: error("Inbound mail record id is required")

        val savedDocuments = mailAttachmentService.saveInboundAttachments(
            expertContactId = contactId,
            mailRecordId = inboundMailRecordId,
            attachments = received.attachments
        )
        val effectiveContact = applyPromotionAndStatus(
            contact = contact,
            receivedAt = received.receivedAt,
            inboundMailRecordId = inboundMailRecordId,
            attachmentCount = savedDocuments.size
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
        captureUnsubscribeIfPresent(received.from, cleanedBody)

        if (intent.intentCode == InboundIntentCode.MEETING_TIME_PROVIDED || intent.intentCode == InboundIntentCode.MEETING_REQUESTED) {
            meetingScheduleService.extractAndCreate(contactId, inboundMailRecord)
        }

        if (!account.enabled) {
            markManualReview(
                contact = effectiveContact,
                received = received,
                status = ConversationStatus.MANUAL_HANDOFF,
                reason = "ACCOUNT_AUTO_SEND_DISABLED",
                note = "Auto-send disabled for account ${account.accountCode}"
            )
            confirmManualReviewWithBody(
                account = account,
                received = received,
                expertContactId = contactId,
                reason = "ACCOUNT_AUTO_SEND_DISABLED",
                reasonType = "UNCLEAR_INTENT",
                cleanedBody = cleanedBody,
                skipImapAck = skipImapAck
            )
            return SinglePipelineResult(
                outcome = SinglePipelineOutcome.MANUAL_REVIEW_BY_INTENT,
                recorded = true,
                expertContactId = contactId,
                inboundMailRecordId = inboundMailRecordId,
                intentCode = intent.intentCode,
                autoAction = intent.autoAction,
                matchedKeywords = intent.matchedKeywords,
                newStatus = ConversationStatus.MANUAL_HANDOFF.name,
                previousStatus = contact.currentStatus,
                reason = "ACCOUNT_AUTO_SEND_DISABLED"
            )
        }

        when (intent.autoAction) {
            AutoIntentAction.MANUAL_REVIEW -> {
                val reason = manualReviewReason(intent.intentCode)
                markManualReview(
                    contact = effectiveContact,
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
                    cleanedBody = cleanedBody,
                    skipImapAck = skipImapAck
                )
                return SinglePipelineResult(
                    outcome = SinglePipelineOutcome.MANUAL_REVIEW_BY_INTENT,
                    recorded = true,
                    expertContactId = contactId,
                    inboundMailRecordId = inboundMailRecordId,
                    intentCode = intent.intentCode,
                    autoAction = intent.autoAction,
                    matchedKeywords = intent.matchedKeywords,
                    newStatus = ConversationStatus.MANUAL_HANDOFF.name,
                    previousStatus = contact.currentStatus,
                    reason = reason
                )
            }

            AutoIntentAction.CLOSE -> {
                markManualReview(
                    contact = effectiveContact,
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
                    cleanedBody = cleanedBody,
                    skipImapAck = skipImapAck
                )
                return SinglePipelineResult(
                    outcome = SinglePipelineOutcome.CLOSED_BY_INTENT,
                    recorded = true,
                    expertContactId = contactId,
                    inboundMailRecordId = inboundMailRecordId,
                    intentCode = intent.intentCode,
                    autoAction = intent.autoAction,
                    matchedKeywords = intent.matchedKeywords,
                    newStatus = ConversationStatus.MANUAL_HANDOFF.name,
                    previousStatus = contact.currentStatus,
                    reason = "INTENT_${intent.intentCode.name}"
                )
            }

            AutoIntentAction.SEND_MEETING_INVITATION -> {
                if (hasMeetingInvitation(contactId)) {
                    markManualReview(
                        contact = effectiveContact,
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
                        cleanedBody = cleanedBody,
                        skipImapAck = skipImapAck
                    )
                    return SinglePipelineResult(
                        outcome = SinglePipelineOutcome.MEETING_ALREADY_SENT,
                        recorded = true,
                        expertContactId = contactId,
                        inboundMailRecordId = inboundMailRecordId,
                        intentCode = intent.intentCode,
                        autoAction = intent.autoAction,
                        matchedKeywords = intent.matchedKeywords,
                        newStatus = ConversationStatus.MEETING_SCHEDULING.name,
                        previousStatus = contact.currentStatus,
                        reason = "MEETING_INVITATION_ALREADY_SENT"
                    )
                }

                if (blockedByUnsubscribe(effectiveContact, received, received.from, "MEETING_INVITATION")) {
                    confirmManualReviewWithBody(
                        account = account,
                        received = received,
                        expertContactId = contactId,
                        reason = "RECIPIENT_UNSUBSCRIBED",
                        reasonType = "RECIPIENT_UNSUBSCRIBED",
                        cleanedBody = cleanedBody,
                        skipImapAck = skipImapAck
                    )
                    return SinglePipelineResult(
                        outcome = SinglePipelineOutcome.MANUAL_REVIEW_BY_INTENT,
                        recorded = true,
                        expertContactId = contactId,
                        inboundMailRecordId = inboundMailRecordId,
                        intentCode = intent.intentCode,
                        autoAction = intent.autoAction,
                        matchedKeywords = intent.matchedKeywords,
                        newStatus = ConversationStatus.MANUAL_HANDOFF.name,
                        previousStatus = contact.currentStatus,
                        reason = "RECIPIENT_UNSUBSCRIBED"
                    )
                }

                val meetingRecord = sendMeetingInvitation(account, effectiveContact, contactId, received, inboundMailRecordId)
                val meetingContact = conversationStateService.transition(
                    contact = effectiveContact,
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
                expertOperatorStatusService.updateAutomatically(meetingContact, OperatorStatus.INVITED, "MEETING_INVITATION_SENT")
                confirmProcessed(account, received, contactId, "PROCESSED", "AUTO_MEETING_INVITED", "AUTO_MEETING_INVITED", skipImapAck)
                return SinglePipelineResult(
                    outcome = SinglePipelineOutcome.MEETING_INVITED,
                    recorded = true,
                    expertContactId = contactId,
                    inboundMailRecordId = inboundMailRecordId,
                    outboundMailRecordId = meetingRecord.id,
                    intentCode = intent.intentCode,
                    autoAction = intent.autoAction,
                    matchedKeywords = intent.matchedKeywords,
                    newStatus = ConversationStatus.MEETING_SCHEDULING.name,
                    previousStatus = contact.currentStatus,
                    replySendStatus = meetingRecord.sendStatus,
                    reason = "MEETING_INVITATION_SENT"
                )
            }

            AutoIntentAction.QA -> Unit
        }

        val decision = groundedAutoReplyDecisionService.decide(cleanedBody, received.subject)
        if (!decision.readyToSend) {
            val manualReason = decision.reason
            markManualReview(
                contact = effectiveContact,
                received = received,
                status = ConversationStatus.MANUAL_HANDOFF,
                reason = manualReason,
                note = "Subject: ${received.subject.orEmpty()}\n\n${cleanedBody.take(1200)}"
            )
            confirmManualReviewWithBody(
                account = account,
                received = received,
                expertContactId = contactId,
                reason = manualReason,
                reasonType = manualReason,
                cleanedBody = cleanedBody,
                skipImapAck = skipImapAck
            )
            return SinglePipelineResult(
                outcome = SinglePipelineOutcome.QA_NO_MATCH,
                recorded = true,
                expertContactId = contactId,
                inboundMailRecordId = inboundMailRecordId,
                intentCode = intent.intentCode,
                autoAction = intent.autoAction,
                matchedKeywords = intent.matchedKeywords,
                newStatus = ConversationStatus.MANUAL_HANDOFF.name,
                previousStatus = contact.currentStatus,
                reason = manualReason
            )
        }

        if (blockedByUnsubscribe(effectiveContact, received, received.from, "QA")) {
            confirmManualReviewWithBody(
                account = account,
                received = received,
                expertContactId = contactId,
                reason = "RECIPIENT_UNSUBSCRIBED",
                reasonType = "RECIPIENT_UNSUBSCRIBED",
                cleanedBody = cleanedBody,
                skipImapAck = skipImapAck
            )
            return SinglePipelineResult(
                outcome = SinglePipelineOutcome.MANUAL_REVIEW_BY_INTENT,
                recorded = true,
                expertContactId = contactId,
                inboundMailRecordId = inboundMailRecordId,
                intentCode = intent.intentCode,
                autoAction = intent.autoAction,
                matchedKeywords = intent.matchedKeywords,
                newStatus = ConversationStatus.MANUAL_HANDOFF.name,
                previousStatus = contact.currentStatus,
                reason = "RECIPIENT_UNSUBSCRIBED"
            )
        }

        val plainBody = mailVariableService.renderForContact(
            requireNotNull(decision.rawDraftText) { "Grounded auto reply draft text is required" },
            account,
            contact
        )
        val reply = ComposedMail(
            to = received.from,
            subject = decision.subject,
            body = mailContentService.plainTextToHtml(plainBody),
            html = true,
            text = plainBody,
            messageId = OutboundMessageIdFactory.newId("auto-reply", contactId.toString(), account.senderEmail)
        )
        val delivered = mailDeliveryService.send(account, reply)
        val now = LocalDateTime.now()

        val outboundRecord = mailRecordRepository.save(
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
                body = plainBody,
                cleanedBody = null,
                matchedQaRuleId = decision.qaRuleIds.firstOrNull(),
                sendStatus = delivered.status,
                receivedAt = null,
                sentAt = now,
                createdAt = now
            )
        )

        val outboundRecordId = outboundRecord.id ?: error("Outbound mail record id is required")
        decision.qaRuleIds.forEachIndexed { ordinal, qaRuleId ->
            mailRecordQaRuleRepository.save(
                MailRecordQaRule(
                    mailRecordId = outboundRecordId,
                    qaRuleId = qaRuleId,
                    ordinal = ordinal
                )
            )
        }

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
        confirmProcessed(account, received, contactId, "PROCESSED", "QA_AUTO_REPLIED", "AUTO_QA_REPLIED", skipImapAck)
        return SinglePipelineResult(
            outcome = SinglePipelineOutcome.QA_REPLIED,
            recorded = true,
            expertContactId = contactId,
            inboundMailRecordId = inboundMailRecordId,
            outboundMailRecordId = outboundRecord.id,
            intentCode = intent.intentCode,
            autoAction = intent.autoAction,
            matchedKeywords = intent.matchedKeywords,
            newStatus = ConversationStatus.QA_AUTO_REPLIED.name,
            previousStatus = contact.currentStatus,
            replySendStatus = delivered.status,
            reason = "QA_AUTO_REPLIED"
        )
    }

    fun receiveAndAutoReply(accountCode: String, maxMessages: Int): AutoMailReplyBatchResult {
        val account = mailSenderAccountService.getAutoReceiveAccount(accountCode)
        val stored = mailInboxCursorService.get(accountCode)
        var fetch = mailReceiveService.fetchInboundSince(account, stored.lastUid, maxMessages)
        var start = mailInboxCursorService.resolveStart(stored, fetch.uidValidity)
        if (start == 0L && stored.lastUid > 0L) {
            fetch = mailReceiveService.fetchInboundSince(account, 0, maxMessages)
        }

        var recorded = 0
        var replied = 0
        var manualReview = 0
        var meetingInvitations = 0
        val repliedExperts = mutableListOf<RepliedExpertInfo>()
        val handledUids = mutableSetOf<Long>()
        val fetchedUids = fetch.mails.map { it.imapUid }

        fetch.mails.forEach { mail ->
            try {
                if (selfCheckProbeDetector.isSelfCheckProbe(mail.from, mail.subject, account.senderEmail)) {
                    mailReceiveService.markSeen(account, mail.imapUid)
                    log.debug("Discarded self-check probe: uid={}", mail.imapUid)
                    handledUids.add(mail.imapUid)
                    return@forEach
                }
                val bounceSignal = bounceDetector.detect(mail.from, mail.subject, mail.body)
                if (bounceSignal != null) {
                    bounceCollectionService.ingest(
                        signal = bounceSignal,
                        senderAccountCode = account.accountCode,
                        bounceMessageId = mail.messageId,
                        from = mail.from,
                        subject = mail.subject,
                        receivedAt = mail.receivedAt
                    )
                    mailReceiveService.markSeen(account, mail.imapUid)
                    log.debug("Ingested bounce during auto-reply poll: uid={}", mail.imapUid)
                    handledUids.add(mail.imapUid)
                    return@forEach
                }
                if (dmarcReportDetector.isDmarcAggregateReport(mail.from, mail.subject, mail.attachments)) {
                    try {
                        dmarcReportIngestService.ingest(mail.attachments)
                    } catch (e: Exception) {
                        log.warn("DMARC parse failed uid={}", mail.imapUid, e)
                    }
                    mailReceiveService.markSeen(account, mail.imapUid)
                    handledUids.add(mail.imapUid)
                    return@forEach
                }
                val r = processSingle(account, mail, skipImapAck = false)
                handledUids.add(mail.imapUid)
                if (r.recorded) {
                    recorded++
                    repliedExperts.add(
                        RepliedExpertInfo(
                            expertContactId = r.expertContactId,
                            expertEmail = mail.from,
                            expertName = r.expertContactId
                                ?.let(expertContactRepository::findById)
                                ?.map { it.expertName }
                                ?.orElse(null),
                            outcome = r.outcome.name
                        )
                    )
                }
                if (r.outcome == SinglePipelineOutcome.QA_REPLIED) replied++
                if (r.outcome == SinglePipelineOutcome.MEETING_INVITED) meetingInvitations++
                if (r.outcome in MANUAL_REVIEW_OUTCOMES) manualReview++
            } catch (e: Exception) {
                log.error(
                    "Failed to process inbound mail uid={} account={}",
                    mail.imapUid,
                    accountCode,
                    e
                )
            }
        }

        mailInboxCursorService.advance(
            accountCode = accountCode,
            currentUidValidity = fetch.uidValidity,
            fetchedUids = fetchedUids,
            handledUids = handledUids,
            oldStart = start
        )

        val bounceResult = bounceCollectionService.collectBounces(account)
        if (bounceResult.collected > 0) {
            log.info(
                "Collected {} bounces for account {} after auto-reply",
                bounceResult.collected,
                accountCode
            )
        }
        bounceRateMonitorService.checkAndPause(accountCode)

        return AutoMailReplyBatchResult(
            fetched = fetch.mails.size,
            recorded = recorded,
            replied = replied,
            manualReview = manualReview,
            meetingInvitations = meetingInvitations,
            repliedExperts = repliedExperts
        )
    }

    fun processByUids(accountCode: String, uids: List<Long>): List<SinglePipelineResult> {
        val account = mailSenderAccountService.getAutoReceiveAccount(accountCode)
        val mailsByUid = mailReceiveService.fetchByUids(account, uids).associateBy { it.imapUid }
        return uids.map { uid ->
            val mail = mailsByUid[uid]
                ?: return@map SinglePipelineResult(
                    outcome = SinglePipelineOutcome.UNMATCHED_CONTACT,
                    recorded = false,
                    reason = "UID_NOT_FOUND:$uid"
                )
            processSingle(account, mail, skipImapAck = false)
        }
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

    private fun applyPromotionAndStatus(
        contact: ExpertContact,
        receivedAt: LocalDateTime,
        inboundMailRecordId: Long,
        attachmentCount: Int
    ): ExpertContact {
        var promoted = contact
        promoted = automaticApplicationPromotionService.promoteByMaterialIfNeeded(
            promoted, receivedAt, inboundMailRecordId, attachmentCount
        )
        promoted = automaticApplicationPromotionService.promoteByReplyCountIfNeeded(
            promoted, receivedAt, inboundMailRecordId
        )
        return expertOperatorStatusService.updateAutomatically(promoted, OperatorStatus.REPLIED, "REPLY_RECEIVED")
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

    private fun blockedByUnsubscribe(
        contact: ExpertContact,
        received: ReceivedMail,
        recipient: String,
        scene: String
    ): Boolean {
        if (!emailSuppressionService.isSuppressed(recipient)) return false
        log.info("Recipient {} unsubscribed, skip auto send ({})", recipient, scene)
        markManualReview(
            contact = contact,
            received = received,
            status = ConversationStatus.MANUAL_HANDOFF,
            reason = "RECIPIENT_UNSUBSCRIBED",
            note = "Auto send skipped: recipient unsubscribed ($scene)"
        )
        return true
    }

    private fun captureUnsubscribeIfPresent(senderEmail: String, cleanedBody: String?) {
        if (emailSuppressionService.looksLikeUnsubscribe(cleanedBody)) {
            emailSuppressionService.suppress(
                senderEmail,
                SuppressionSource.INBOUND_REPLY,
                "inbound reply unsubscribe"
            )
        }
    }

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
        contact: ExpertContact,
        contactId: Long,
        received: ReceivedMail,
        sourceInboundId: Long
    ): MailRecord {
        val variantSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)
        val rendered = mailComposeTemplateService.renderByCode(
            templateCode = "MEETING_INVITATION",
            variables = mailTemplateVariables(account),
            variantSeed = variantSeed
        )
        val mail = ComposedMail(
            to = received.from,
            subject = rendered.subject.ifBlank { "Re: ${received.subject.orEmpty()}".trim() },
            body = rendered.body,
            messageId = OutboundMessageIdFactory.newId("meeting-invitation", contact.orcidId, account.senderEmail)
        )
        val delivered = mailDeliveryService.send(account, mail)
        val now = LocalDateTime.now()
        val saved = mailRecordRepository.save(
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
        return saved
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
        reasonType: String? = "UNCLEAR_INTENT",
        skipImapAck: Boolean = false
    ) {
        confirmProcessed(account, received, expertContactId, "MANUAL_REVIEW", reason, reasonType, skipImapAck)
    }

    private fun confirmManualReviewWithBody(
        account: MailSenderAccount,
        received: ReceivedMail,
        expertContactId: Long?,
        reason: String,
        reasonType: String?,
        cleanedBody: String,
        skipImapAck: Boolean = false
    ): InboundMailProcessing {
        val now = LocalDateTime.now()
        val saved = inboundMailProcessingRepository.save(
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
        applyAutoTags(saved, cleanedBody, received.body)
        if (!skipImapAck) mailReceiveService.markSeen(account, received.imapUid)
        return saved
    }

    private fun applyAutoTags(
        saved: InboundMailProcessing,
        cleanedBody: String?,
        fallbackBody: String?
    ) {
        runCatching {
            val tagBody = cleanedBody ?: saved.body ?: fallbackBody
            if (!tagBody.isNullOrBlank()) {
                saved.id?.let { inboundMailTagService.autoApplyQaTags(it, tagBody) }
            }
        }.onFailure { log.warn("auto tag failed for inbound ${saved.id}", it) }
    }

    private fun confirmProcessed(
        account: MailSenderAccount,
        received: ReceivedMail,
        expertContactId: Long?,
        status: String,
        reason: String,
        reasonType: String? = null,
        skipImapAck: Boolean = false,
        body: String? = null,
        cleanedBody: String? = null
    ) {
        val now = LocalDateTime.now()
        val saved = inboundMailProcessingRepository.save(
            InboundMailProcessing(
                senderAccountCode = account.accountCode,
                imapUid = received.imapUid,
                messageId = received.messageId,
                inReplyTo = received.inReplyTo,
                fromEmail = received.from,
                subject = received.subject,
                body = body ?: received.body,
                cleanedBody = cleanedBody,
                receivedAt = received.receivedAt,
                processStatus = status,
                processReason = reason,
                reasonType = reasonType,
                expertContactId = expertContactId,
                createdAt = now,
                updatedAt = now
            )
        )
        applyAutoTags(saved, cleanedBody, body ?: received.body)
        if (!skipImapAck) mailReceiveService.markSeen(account, received.imapUid)
    }
}

enum class SinglePipelineOutcome {
    DUPLICATE_IMAP_UID,
    DUPLICATE_INBOUND_MESSAGE,
    UNMATCHED_CONTACT,
    INTRODUCTION_NOT_SENT,
    GLOBAL_AUTO_REPLY_DISABLED,
    AUTO_REPLY_DISABLED,
    MANUAL_HANDOFF_STATUS,
    QA_REPLIED,
    QA_NO_MATCH,
    MEETING_INVITED,
    MEETING_ALREADY_SENT,
    MANUAL_REVIEW_BY_INTENT,
    CLOSED_BY_INTENT
}

data class SinglePipelineResult(
    val outcome: SinglePipelineOutcome,
    val recorded: Boolean = false,
    val expertContactId: Long? = null,
    val inboundMailRecordId: Long? = null,
    val outboundMailRecordId: Long? = null,
    val intentCode: InboundIntentCode? = null,
    val autoAction: AutoIntentAction? = null,
    val matchedKeywords: List<String> = emptyList(),
    val newStatus: String? = null,
    val previousStatus: String? = null,
    val manualHandoffId: Long? = null,
    val meetingScheduleId: Long? = null,
    val reason: String? = null,
    val replySendStatus: String? = null
) {
    companion object {
        fun duplicate(imapUid: Long) = SinglePipelineResult(
            outcome = SinglePipelineOutcome.DUPLICATE_IMAP_UID,
            recorded = false, expertContactId = null, inboundMailRecordId = null,
            outboundMailRecordId = null, intentCode = null, autoAction = null,
            matchedKeywords = emptyList(), newStatus = null, previousStatus = null,
            manualHandoffId = null, meetingScheduleId = null,
            reason = "DUPLICATE_IMAP_UID:$imapUid", replySendStatus = null
        )
    }
}

val MANUAL_REVIEW_OUTCOMES = setOf(
    SinglePipelineOutcome.UNMATCHED_CONTACT,
    SinglePipelineOutcome.INTRODUCTION_NOT_SENT,
    SinglePipelineOutcome.GLOBAL_AUTO_REPLY_DISABLED,
    SinglePipelineOutcome.AUTO_REPLY_DISABLED,
    SinglePipelineOutcome.MANUAL_HANDOFF_STATUS,
    SinglePipelineOutcome.MANUAL_REVIEW_BY_INTENT,
    SinglePipelineOutcome.QA_NO_MATCH,
    SinglePipelineOutcome.CLOSED_BY_INTENT,
    SinglePipelineOutcome.MEETING_ALREADY_SENT
)

data class RepliedExpertInfo(
    val expertContactId: Long?,
    val expertEmail: String?,
    val expertName: String?,
    val outcome: String
)

data class AutoMailReplyBatchResult(
    val fetched: Int,
    val recorded: Int,
    val replied: Int,
    val manualReview: Int,
    val meetingInvitations: Int = 0,
    val repliedExperts: List<RepliedExpertInfo> = emptyList()
)
