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
import com.weibo.talentintroduction.mail.domain.AutoReplyConfidenceLog
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.InboundIntentRepository
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.AutoReplyConfidenceLogRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.campaign.service.MeetingScheduleService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
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
    private val mailVariableService: MailVariableService,
    private val autoReplyConfidenceLogRepository: AutoReplyConfidenceLogRepository,
    /** 附件传输登记服务（04：最终 bridge 完整性由 [MailAttachmentService.bridgeInboundProcessing]
     *  在确认点核验；本依赖按计划纳入构造，供 05 机器报告登记复用，不另加范围外参数）。 */
    private val attachmentTransferService: AttachmentTransferService,
    /** 单信处理事务边界：实际执行主体（含全部现有 DB 写）显式包裹，覆盖同类与外部调用。 */
    private val transactionTemplate: TransactionTemplate
) {
    private val log = LoggerFactory.getLogger(AutoMailReplyService::class.java)
    private val duplicateInboundWindowMinutes = 30L

    /**
     * 单信处理入口（I-2）：整个实际执行主体在显式事务内完成（同类
     * receiveAndAutoReply/processByUids 与外部调用一律生效）；事务成功提交后才在
     * 单一确认点 markSeen/纳入游标成功集（skipImapAck 仍生效）。事务失败/异常时
     * 邮件保持未读、不推进游标，重试收敛；已确认 UID 重复到达走 DUPLICATE_IMAP_UID，
     * 绝不重跑自动回复。
     *
     * 已知历史边界（仅记录，不由本计划消除）：SMTP 发送是事务内副作用——网络发送
     * 成功但后续 DB 失败回滚时存在已发未记风险，沿用人工核对流程。
     */
    fun processSingle(
        account: MailSenderAccount,
        received: ReceivedMail,
        skipImapAck: Boolean = false
    ): SinglePipelineResult {
        val result = transactionTemplate.execute {
            processSingleCore(account, received)
        } ?: error(
            "processSingle transaction produced no result: account=${account.accountCode} uid=${received.imapUid}"
        )
        if (!skipImapAck) mailReceiveService.markSeen(account, received.imapUid)
        return result
    }

    private fun processSingleCore(
        account: MailSenderAccount,
        received: ReceivedMail
    ): SinglePipelineResult {
        val accountCode = account.accountCode
        // I-1：新接收必须携带真实 UIDVALIDITY（0 仅历史未知）。未知远端 source
        // fail-closed——不落 processing、不 markSeen，绝不反写 0/猜测代际。
        require(received.uidValidity > 0) {
            "Inbound receipt for account=$accountCode uid=${received.imapUid} carries no positive UIDVALIDITY; refusing to record an unknown remote source"
        }
        // 真实远端身份判重（V120 唯一键 account/uid_validity/uid）：已确认 UID 重复
        // 到达只 markSeen，绝不重跑自动回复。
        if (inboundMailProcessingRepository.findBySenderAccountCodeAndUidValidityAndImapUid(
                accountCode,
                received.uidValidity,
                received.imapUid
            ) != null
        ) {
            return SinglePipelineResult.duplicate(received.imapUid)
        }
        // 历史 0 代际行（V120 之前，uid_validity=0 只表示代际未知）：仅当同 account/uid
        // 且非空 Message-ID、from、秒级 receivedAt 全部吻合才认领同一代际（视为已处理）。
        var legacyUidUnverifiable = false
        val legacySameUid = inboundMailProcessingRepository
            .findBySenderAccountCodeAndImapUid(accountCode, received.imapUid)
        if (legacySameUid != null && legacySameUid.uidValidity == 0L) {
            if (sameMessageGeneration(legacySameUid, received)) {
                return SinglePipelineResult.duplicate(received.imapUid)
            }
            legacyUidUnverifiable = true
        }

        val contact = expertEmailAliasService.findContactByEmailOrAlias(received.from)
        // I-4：正文被有界截断（03 元数据模式）→ 一律人工 BODY_TRUNCATED，禁自动回复。
        if (received.bodyTruncated) {
            return reviewManualOnly(
                account = account,
                received = received,
                expertContactId = contact?.id,
                reason = "BODY_TRUNCATED",
                outcome = SinglePipelineOutcome.BODY_TRUNCATED
            )
        }
        // I-1：历史 0 代际行且同 UID 信息不足核验 → 转人工 LEGACY_UID_UNVERIFIABLE：
        // 不盲目吞信/自动回复，不把未知远端 source 写回旧行。
        if (legacyUidUnverifiable) {
            return reviewManualOnly(
                account = account,
                received = received,
                expertContactId = contact?.id,
                reason = "LEGACY_UID_UNVERIFIABLE",
                outcome = SinglePipelineOutcome.LEGACY_UID_UNVERIFIABLE
            )
        }
        if (contact == null) {
            val cleanedBody = mailBodyCleaner.clean(received.body)
            val inboundProcessing = confirmManualReviewWithBody(
                account = account,
                received = received,
                expertContactId = null,
                reason = "CONTACT_NOT_FOUND",
                reasonType = "UNMATCHED_CONTACT",
                cleanedBody = cleanedBody,
                bridgeMetadata = false
            )
            val inboundProcessingId = inboundProcessing.id ?: error("Inbound mail processing id is required")
            registerProcessingOwnerMaterials(inboundProcessingId, received, expertContactId = null)
            return SinglePipelineResult(
                outcome = SinglePipelineOutcome.UNMATCHED_CONTACT,
                recorded = false,
                reason = "CONTACT_NOT_FOUND"
            )
        }
        val contactId = contact.id ?: error("Expert contact id is required")
        if (!hasIntroductionInquiry(contactId)) {
            val processing = confirmManualReview(
                account = account,
                received = received,
                expertContactId = contactId,
                reason = "INTRODUCTION_NOT_SENT",
                reasonType = "UNCLEAR_INTENT",
                bridgeMetadata = false
            )
            val processingId = processing.id ?: error("Inbound mail processing id is required")
            registerProcessingOwnerMaterials(processingId, received, expertContactId = contactId)
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
            captureUnsubscribeIfPresent(received.from, received.subject, cleanedBody)
            confirmProcessed(
                account = account,
                received = received,
                expertContactId = contactId,
                status = "MANUAL_REVIEW",
                reason = "GLOBAL_AUTO_REPLY_DISABLED",
                reasonType = "GLOBAL_AUTO_REPLY_DISABLED",
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
            captureUnsubscribeIfPresent(received.from, received.subject, cleanedBody)
            if (disabledContact.currentStatus == ConversationStatus.MANUAL_HANDOFF.name) {
                if (!disabledContact.needsManualAttention) {
                    expertContactRepository.save(disabledContact.copy(needsManualAttention = true))
                }
                createManualHandoffIfAbsent(contactId, reason, "Auto-reply skipped: contact already in MANUAL_HANDOFF")
                confirmProcessed(account, received, contactId, "MANUAL_REVIEW", reason, "UNCLEAR_INTENT")
            } else {
                markManualReview(
                    contact = disabledContact,
                    received = received,
                    status = ConversationStatus.MANUAL_HANDOFF,
                    reason = reason,
                    note = "Auto-reply skipped: $reason. Status: ${disabledContact.currentStatus}"
                )
                confirmProcessed(account, received, contactId, "MANUAL_REVIEW", reason, "UNCLEAR_INTENT")
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
        captureUnsubscribeIfPresent(received.from, received.subject, cleanedBody)

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
                    cleanedBody = cleanedBody
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
                    cleanedBody = cleanedBody
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
                        cleanedBody = cleanedBody
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
                        cleanedBody = cleanedBody
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
                confirmProcessed(account, received, contactId, "PROCESSED", "AUTO_MEETING_INVITED", "AUTO_MEETING_INVITED")
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

        val decision = groundedAutoReplyDecisionService.decide(
            inboundText = cleanedBody,
            inboundSubject = received.subject,
            contact = effectiveContact,
            currentInboundMessageId = received.messageId
        )
        decision.confidence?.let { score ->
            runCatching {
                autoReplyConfidenceLogRepository.save(
                    AutoReplyConfidenceLog(
                        expertContactId = contactId,
                        inboundMailRecordId = inboundMailRecordId,
                        senderAccountCode = account.accountCode,
                        inboundMessageId = received.messageId,
                        crs = score.crs,
                        coverageScore = score.coverageScore,
                        evidenceScore = score.evidenceScore,
                        consistencyScore = score.consistencyScore,
                        historyScore = score.historyScore,
                        requestCount = score.requestCount,
                        unsupportedCount = score.unsupportedCount,
                        partialCount = score.partialCount,
                        verifiedRuleCount = score.verifiedRuleCount,
                        warningCount = score.warningCount,
                        draftReadiness = decision.draftReadiness.name,
                        generationState = decision.generationState.name,
                        decisionReason = decision.reason,
                        readyToSend = decision.readyToSend,
                        tier = "SHADOW",
                        createdAt = LocalDateTime.now()
                    )
                )
            }.onFailure { log.warn("Failed to persist auto-reply confidence log: {}", it.message) }
        }
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
        confirmProcessed(account, received, contactId, "PROCESSED", "QA_AUTO_REPLIED", "AUTO_QA_REPLIED")
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

    /**
     * 单账号检查（05）：可选 [onPhase] 报告接收/处理阶段（READING_METADATA /
     * PROCESSING_MAIL，供 Batch/Controller 发布账号进度）；[isCancelled] 只在
     * 安全边界（每封邮件处理前）停止后续邮件，绝不中断已开始的业务/SMTP 事务
     * （不把邮件发送回滚当作可用取消方案）。接收窗口预算由 [ImapMailReceiveService]
     * 按账号执行，本方法不把 120s 称为含 LLM/SMTP 的整任务 SLA。
     */
    fun receiveAndAutoReply(
        accountCode: String,
        maxMessages: Int,
        onPhase: ((String) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): AutoMailReplyBatchResult {
        val account = mailSenderAccountService.getAutoReceiveAccount(accountCode)
        val stored = mailInboxCursorService.get(accountCode)
        onPhase?.invoke(AccountAutoMailReplyPhases.READING_METADATA)
        var fetch = mailReceiveService.fetchInboundSince(account, stored.lastUid, maxMessages)
        var start = mailInboxCursorService.resolveStart(stored, fetch.uidValidity)
        if (start == 0L && stored.lastUid > 0L) {
            onPhase?.invoke(AccountAutoMailReplyPhases.READING_METADATA)
            fetch = mailReceiveService.fetchInboundSince(account, 0, maxMessages)
        }

        var recorded = 0
        var replied = 0
        var manualReview = 0
        var meetingInvitations = 0
        val repliedExperts = mutableListOf<RepliedExpertInfo>()
        val handledUids = mutableSetOf<Long>()
        val fetchedUids = fetch.mails.map { it.imapUid }

        onPhase?.invoke(AccountAutoMailReplyPhases.PROCESSING_MAIL)
        for (mail in fetch.mails) {
            // I-2：取消只在安全边界（本封邮件尚未开始处理）停止后续邮件；不中断进行中的事务。
            if (isCancelled?.invoke() == true) {
                log.info("Auto reply cancelled at a safe boundary for account {}", accountCode)
                break
            }
            try {
                if (selfCheckProbeDetector.isSelfCheckProbe(mail.from, mail.subject, account.senderEmail)) {
                    mailReceiveService.markSeen(account, mail.imapUid)
                    log.debug("Discarded self-check probe: uid={}", mail.imapUid)
                    handledUids.add(mail.imapUid)
                    continue
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
                    continue
                }
                if (dmarcReportDetector.isDmarcAggregateReport(mail.from, mail.subject, mail.attachments)) {
                    // I-3（metadata 模式）：content=null 的附件不能交给原 ingest（无字节可解压，
                    // parse-null 会被静默跳过 = 丢报表）。改经 02 队列登记 SYSTEM 的 DMARC 获取请求
                    // （purpose=DMARC、无 attachmentId、无专家附件/文档），不在检查线程等待下载/解析；
                    // 每个源索引行持久化且明确入队后才确认该 UID。legacy 模式保持原内联 ingest。
                    val attachments = mail.attachments
                    if (attachments.isNotEmpty() && attachments.all { it.content == null }) {
                        queueDmarcTransfers(account, mail, attachments)
                    } else {
                        try {
                            dmarcReportIngestService.ingest(attachments)
                        } catch (e: Exception) {
                            log.warn("DMARC parse failed uid={}", mail.imapUid, e)
                        }
                    }
                    mailReceiveService.markSeen(account, mail.imapUid)
                    handledUids.add(mail.imapUid)
                    continue
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
        bounceRateMonitorService.checkAndWarn(accountCode)

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

    /**
     * I-3（metadata 模式 DMARC）：把报表附件逐件登记为 purpose=DMARC 的源索引行并
     * 由 SYSTEM 请求入队（02 队列），不在检查线程等待下载/解析。任何一步失败都会
     * 抛出让调用方不确认该 UID（不 markSeen、游标不推进），下次检查重试收敛；
     * 已入队/已 STORED 的重复登记幂等返回，不新建任务。
     */
    private fun queueDmarcTransfers(
        account: MailSenderAccount,
        received: ReceivedMail,
        attachments: List<ReceivedMailAttachment>
    ) {
        for (attachment in attachments) {
            val source = attachment.source
                ?: error(
                    "metadata-mode DMARC attachment must carry a remote source descriptor " +
                        "(file=${attachment.fileName})"
                )
            val registration = attachmentTransferService.register(
                AttachmentTransferService.RegisterTransferRequest(
                    purpose = MailAttachmentTransfer.PURPOSE_DMARC,
                    accountCode = account.accountCode,
                    folder = source.folder,
                    uidValidity = source.uidValidity,
                    imapUid = source.uid,
                    partPath = source.partPath,
                    messageId = source.messageId,
                    fileName = attachment.fileName,
                    contentType = attachment.contentType,
                    encodedSize = source.encodedSize,
                    disposition = source.disposition
                )
            )
            val transferId = registration.transfer.id
                ?: error("registered DMARC transfer has no id")
            val queued = attachmentTransferService.enqueueTransferByIds(
                listOf(transferId),
                AttachmentTransferService.SYSTEM_REQUESTER
            )
            val item = queued.items.singleOrNull()
                ?: error("DMARC transfer enqueue produced no item for transferId=$transferId")
            if (item.errorCode != null) {
                error(
                    "DMARC transfer could not be queued for uid=${received.imapUid} " +
                        "part=${source.partPath}: ${item.errorCode}"
                )
            }
            log.info(
                "Queued DMARC report transfer id={} uid={} part={} file={}",
                transferId,
                received.imapUid,
                source.partPath,
                attachment.fileName
            )
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

    private fun captureUnsubscribeIfPresent(senderEmail: String, subject: String?, cleanedBody: String?) {
        val source = emailSuppressionService.detectUnsubscribeSource(subject, cleanedBody) ?: return
        emailSuppressionService.suppress(
            senderEmail,
            source,
            if (source == SuppressionSource.MAILTO) "mailto unsubscribe" else "inbound reply unsubscribe"
        )
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

    /**
     * 确认入口：事务内保存 processing 行（markSeen 不在本方法内——由 processSingle
     * 在事务成功提交后的单一确认点执行）。bridgeMetadata=true（已匹配分支）时在
     * 同一事务内把本信已登记的 metadata 附件 transfer 行桥接 processing.id（I-2：
     * 缺任一 metadata 附件登记即抛错，本信不能被确认；旧 content 附件由旧路径落库、
     * 由 MailAttachmentService.bridgeInboundProcessing 跳过，不要求 transfer 行）；
     * 未匹配/无首信/来源存疑分支先 false 建行，附件随后以 processing 为 owner
     * 直接登记。
     */
    private fun confirmManualReview(
        account: MailSenderAccount,
        received: ReceivedMail,
        expertContactId: Long?,
        reason: String,
        reasonType: String? = "UNCLEAR_INTENT",
        bridgeMetadata: Boolean = true
    ): InboundMailProcessing =
        confirmProcessed(account, received, expertContactId, "MANUAL_REVIEW", reason, reasonType, bridgeMetadata)

    private fun confirmManualReviewWithBody(
        account: MailSenderAccount,
        received: ReceivedMail,
        expertContactId: Long?,
        reason: String,
        reasonType: String?,
        cleanedBody: String,
        bridgeMetadata: Boolean = true
    ): InboundMailProcessing {
        val now = LocalDateTime.now()
        val saved = inboundMailProcessingRepository.save(
            InboundMailProcessing(
                senderAccountCode = account.accountCode,
                uidValidity = received.uidValidity,
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
        val savedId = saved.id ?: error("Inbound mail processing id is required")
        if (bridgeMetadata) {
            mailAttachmentService.bridgeInboundProcessing(savedId, received.attachments)
        }
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
        bridgeMetadata: Boolean = true,
        body: String? = null,
        cleanedBody: String? = null
    ): InboundMailProcessing {
        val now = LocalDateTime.now()
        val saved = inboundMailProcessingRepository.save(
            InboundMailProcessing(
                senderAccountCode = account.accountCode,
                uidValidity = received.uidValidity,
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
        val savedId = saved.id ?: error("Inbound mail processing id is required")
        if (bridgeMetadata) {
            mailAttachmentService.bridgeInboundProcessing(savedId, received.attachments)
        }
        return saved
    }

    // ------------------------------------------------------------------
    // 04 新增路由辅助
    // ------------------------------------------------------------------

    /** 人工专属路由（正文截断/历史 0 代际无法核验）：建 processing 后以 processing
     *  owner 登记附件（已知专家同时建 ExpertDocument）；不触发自动回复/状态迁移。 */
    private fun reviewManualOnly(
        account: MailSenderAccount,
        received: ReceivedMail,
        expertContactId: Long?,
        reason: String,
        outcome: SinglePipelineOutcome
    ): SinglePipelineResult {
        val cleanedBody = mailBodyCleaner.clean(received.body)
        val processing = confirmManualReviewWithBody(
            account = account,
            received = received,
            expertContactId = expertContactId,
            reason = reason,
            reasonType = null,
            cleanedBody = cleanedBody,
            bridgeMetadata = false
        )
        val processingId = processing.id ?: error("Inbound mail processing id is required")
        registerProcessingOwnerMaterials(processingId, received, expertContactId)
        return SinglePipelineResult(
            outcome = outcome,
            recorded = true,
            expertContactId = expertContactId,
            reason = reason
        )
    }

    /** processing-owner 附件登记（metadata 直接建 transfer 行并携带本 processing id）。 */
    private fun registerProcessingOwnerMaterials(
        inboundProcessingId: Long,
        received: ReceivedMail,
        expertContactId: Long?
    ) {
        mailAttachmentService.saveUnmatchedAttachments(
            inboundProcessingId = inboundProcessingId,
            attachments = received.attachments,
            expertContactId = expertContactId
        )
    }

    /**
     * I-1 代际认领：仅当旧行非空 Message-ID 且与来信相同、from 相同、receivedAt
     * 秒级相同，才认为同 account/uid 的历史 0 代际行覆盖当前来信。
     */
    private fun sameMessageGeneration(
        legacy: InboundMailProcessing,
        received: ReceivedMail
    ): Boolean {
        val legacyMessageId = legacy.messageId
        if (legacyMessageId == null || received.messageId == null || legacyMessageId != received.messageId) {
            return false
        }
        if (legacy.fromEmail != received.from) {
            return false
        }
        val second = java.time.temporal.ChronoUnit.SECONDS
        return legacy.receivedAt.truncatedTo(second) == received.receivedAt.truncatedTo(second)
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
    CLOSED_BY_INTENT,
    /** 历史 0 代际行且同 UID 无法核验 → MANUAL_REVIEW/LEGACY_UID_UNVERIFIABLE。 */
    LEGACY_UID_UNVERIFIABLE,
    /** 正文被有界截断（03）→ MANUAL_REVIEW/BODY_TRUNCATED，禁自动回复。 */
    BODY_TRUNCATED
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
    SinglePipelineOutcome.MEETING_ALREADY_SENT,
    SinglePipelineOutcome.LEGACY_UID_UNVERIFIABLE,
    SinglePipelineOutcome.BODY_TRUNCATED
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
