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
import com.weibo.talentintroduction.llm.controller.IntentCoverageResponse
import com.weibo.talentintroduction.llm.controller.RequestCoverageItem
import com.weibo.talentintroduction.llm.service.AiReplyAction
import com.weibo.talentintroduction.llm.service.AiReplyActionPolicy
import com.weibo.talentintroduction.llm.service.AiReplyDraftReadiness
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyHighRiskClaimValidator
import com.weibo.talentintroduction.llm.service.QaFactSelectionService
import com.weibo.talentintroduction.llm.service.RequestFactItem
import com.weibo.talentintroduction.llm.service.ResolvedQaRules
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.CategoryRulesGroup
import com.weibo.talentintroduction.qa.service.CompositionSuggestResult
import com.weibo.talentintroduction.qa.service.GapItem
import com.weibo.talentintroduction.qa.service.SuggestQaRule
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.slf4j.LoggerFactory
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
    private val qaCategoryRepository: QaCategoryRepository,
    private val qaFactSelectionService: QaFactSelectionService,
    private val aiReplyDraftService: AiReplyDraftService,
    private val aiReplyContextService: AiReplyContextService,
    private val aiReplyHighRiskClaimValidator: AiReplyHighRiskClaimValidator,
    private val mailBodyCleaner: MailBodyCleaner,
    private val mailContentService: MailContentService,
    private val mailVariableService: MailVariableService
) {
    companion object {
        private val log = LoggerFactory.getLogger(PendingMailOperationService::class.java)
        const val AI_REPLY_PREFLIGHT_SOURCE_CHANGED = "AI_REPLY_PREFLIGHT_SOURCE_CHANGED"
        const val AI_REPLY_PREFLIGHT_NO_EVIDENCE = "AI_REPLY_PREFLIGHT_NO_EVIDENCE"
    }

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
    fun sendManualRichReply(
        inboundProcessingId: Long,
        senderAccountCode: String?,
        subject: String,
        htmlBody: String,
        textBody: String?,
        operatorName: String?,
        qaRuleIds: List<Long>? = null,
        suggestedRuleIds: List<Long>? = null,
        ackSnippetId: Long? = null,
        edited: Boolean? = null,
        freeTextPreview: String? = null,
        useVariants: Boolean = false,
        templateTextBody: String? = null,
        templateHtmlBody: String? = null
    ): PendingMailSendResult {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val contactId = record.expertContactId
            ?: error("Inbound mail not bound to a contact")
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }

        require(subject.isNotBlank()) { "Subject is required" }
        require(htmlBody.isNotBlank()) { "HTML body is required" }

        val inboundText = inboundMessageBody(record)
        val researchProfileSufficient = resolveResearchProfileSufficient(contact, inboundText)
        val carriesQa = !qaRuleIds.isNullOrEmpty()
        val canonicalFactIds = if (carriesQa) {
            canonicalizeFactRuleIds(inboundText, qaRuleIds!!, researchProfileSufficient)
        } else {
            emptyList()
        }
        val serverSuggestedFactIds = if (carriesQa) {
            qaFactSelectionService.select(inboundText, null, researchProfileSufficient).sendQaRuleIds
        } else {
            emptyList()
        }
        val primaryRuleId = canonicalFactIds.firstOrNull()

        val account = resolvePendingReplyAccount(senderAccountCode, record.senderAccountCode)

        val rawText = templateTextBody?.takeIf { it.isNotBlank() }
            ?: textBody?.takeIf { it.isNotBlank() }
            ?: mailBodyCleaner.clean(htmlBody)
        val rawHtmlFromTemplate = templateHtmlBody?.takeIf { it.isNotBlank() }
        mailVariableService.requireValidPlaceholders(rawText)
        if (rawHtmlFromTemplate != null) {
            mailVariableService.requireValidPlaceholders(rawHtmlFromTemplate)
        } else if (templateTextBody.isNullOrBlank()) {
            mailVariableService.requireValidPlaceholders(htmlBody)
        }

        if (carriesQa) {
            val validation = aiReplyHighRiskClaimValidator.validatePlainText(rawText, canonicalFactIds)
            if (!validation.valid) {
                throw IllegalArgumentException("AI_REPLY_FACT_VALIDATION_FAILED")
            }
        }

        val renderedText = mailVariableService.renderForContact(rawText, account, contact)
        val finalTextBody = renderedText
        val finalHtmlBody = when {
            rawHtmlFromTemplate != null ->
                mailVariableService.renderHtmlForContact(rawHtmlFromTemplate, account, contact)
            !templateTextBody.isNullOrBlank() ->
                mailContentService.plainTextToHtml(renderedText)
            else ->
                mailVariableService.renderHtmlForContact(htmlBody, account, contact)
        }

        val mail = ComposedMail(
            to = contact.expertEmail,
            subject = subject,
            body = finalHtmlBody,
            html = true,
            text = finalTextBody
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
                body = finalTextBody ?: finalHtmlBody,
                matchedQaRuleId = primaryRuleId,
                sendStatus = delivered.status,
                receivedAt = null,
                sentAt = now,
                createdAt = now
            )
        )

        val bodyPreviewText = (finalTextBody?.ifBlank { mailBodyCleaner.clean(finalHtmlBody) }
            ?: mailBodyCleaner.clean(finalHtmlBody)).take(500)
        val mailRecordId = saved.id ?: error("Mail record id is required")

        if (carriesQa) {
            canonicalFactIds.forEachIndexed { ordinal, qaRuleId ->
                mailRecordQaRuleRepository.save(
                    MailRecordQaRule(
                        mailRecordId = mailRecordId,
                        qaRuleId = qaRuleId,
                        ordinal = ordinal
                    )
                )
            }
            scheduleManualRichReplyAudit(
                inboundProcessingId = inboundProcessingId,
                contactId = contactId,
                mailRecordId = mailRecordId,
                actionType = OperatorActionType.SEND_MANUAL_COMPOSED_REPLY,
                after = mapOf(
                    "mailRecordId" to mailRecordId,
                    "canonicalFactIds" to canonicalFactIds,
                    "serverSuggestedFactIds" to serverSuggestedFactIds,
                    "qaRuleIds" to canonicalFactIds,
                    "suggestedRuleIds" to serverSuggestedFactIds,
                    "draftGenerationState" to null,
                    "edited" to (edited ?: false),
                    "sendStatus" to delivered.status,
                    "subject" to mail.subject,
                    "bodyPreviewText" to bodyPreviewText
                ),
                operatorName = operatorName,
                note = "Manual rich reply with QA facts sent for inbound processing $inboundProcessingId"
            )
        } else {
            scheduleManualRichReplyAudit(
                inboundProcessingId = inboundProcessingId,
                contactId = contactId,
                mailRecordId = mailRecordId,
                actionType = OperatorActionType.SEND_MANUAL_RICH_REPLY,
                after = mapOf(
                    "mailRecordId" to mailRecordId,
                    "sendStatus" to delivered.status,
                    "subject" to mail.subject,
                    "bodyPreviewText" to bodyPreviewText
                ),
                operatorName = operatorName,
                note = "Manual rich reply sent for inbound processing $inboundProcessingId"
            )
        }

        return PendingMailSendResult(
            contactId = contactId,
            senderAccountCode = account.accountCode,
            mailType = "MANUAL_RICH_REPLY",
            subject = mail.subject,
            sendStatus = delivered.status,
            messageId = delivered.messageId
        )
    }

    fun suggestComposedReply(inboundProcessingId: Long): TrustWorkbenchSuggestResult {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val inboundText = inboundMessageBody(record)
        val contactId = record.expertContactId
            ?: error("Inbound mail not bound to a contact")
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }
        val researchProfileSufficient = resolveResearchProfileSufficient(contact, inboundText)
        val autoSelection = qaFactSelectionService.select(inboundText, null, researchProfileSufficient)
        return buildTrustWorkbenchSuggest(inboundText, autoSelection, autoSelection.sendQaRuleIds)
    }

    fun evaluateComposedReply(
        inboundProcessingId: Long,
        factRuleIds: List<Long>
    ): TrustWorkbenchEvaluateResult {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val inboundText = inboundMessageBody(record)
        val contactId = record.expertContactId
            ?: error("Inbound mail not bound to a contact")
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }
        val researchProfileSufficient = resolveResearchProfileSufficient(contact, inboundText)
        val autoSuggested = qaFactSelectionService.select(inboundText, null, researchProfileSufficient).sendQaRuleIds
        val selection = qaFactSelectionService.select(inboundText, factRuleIds, researchProfileSufficient)
        val readiness = aiReplyDraftService.resolveDraftReadinessForSelection(
            selection.requestFacts,
            selection.sendQaRuleIds
        )
        return TrustWorkbenchEvaluateResult(
            canonicalFactIds = selection.sendQaRuleIds,
            suggestedFactIds = autoSuggested,
            draftReadiness = readiness.name,
            requestCoverage = toRequestCoverage(selection.requestFacts),
            gapDetected = selection.requestFacts.any {
                it.status.name == "UNSUPPORTED" || it.status.name == "PARTIAL"
            }
        )
    }

    private fun buildTrustWorkbenchSuggest(
        inboundText: String,
        selection: ResolvedQaRules,
        suggestedFactIds: List<Long>
    ): TrustWorkbenchSuggestResult {
        val readiness = aiReplyDraftService.resolveDraftReadinessForSelection(
            selection.requestFacts,
            selection.sendQaRuleIds
        )
        val matchableRules = qaRuleRepository.findAllEnabledOrdered()
            .filter { it.answerBody.trim().isNotBlank() && it.isMatchable() }
        val categories = qaCategoryRepository.findAll().filter { it.enabled }
        val rulesByCategory = categories.map { category ->
            val categoryId = requireNotNull(category.id)
            CategoryRulesGroup(
                categoryId = categoryId,
                categoryCode = category.categoryCode,
                categoryName = category.categoryName,
                composeOrder = category.composeOrder,
                rules = matchableRules
                    .filter { it.categoryId == categoryId }
                    .map { it.toTrustSuggestRule() }
            )
        }.sortedBy { it.composeOrder }
        val gapItems = selection.requestFacts.map { fact ->
            GapItem(
                text = fact.requestText,
                candidateRuleIds = fact.factRuleIds
            )
        }
        return TrustWorkbenchSuggestResult(
            suggestedRuleIds = suggestedFactIds,
            suggestedRules = matchableRules
                .filter { it.id in suggestedFactIds }
                .map { it.toTrustSuggestRule() },
            rulesByCategory = rulesByCategory,
            gapItems = gapItems,
            gapDetected = gapItems.any { it.candidateRuleIds.isEmpty() },
            matchedCategoryIds = matchableRules.map { it.categoryId }.distinct(),
            draftReadiness = readiness.name,
            requestCoverage = toRequestCoverage(selection.requestFacts),
            inboundText = inboundText
        )
    }

    private fun canonicalizeFactRuleIds(
        inboundText: String,
        requestedRuleIds: List<Long>,
        researchProfileSufficient: Boolean
    ): List<Long> {
        val selection = qaFactSelectionService.select(inboundText, requestedRuleIds, researchProfileSufficient)
        return selection.sendQaRuleIds
    }

    private fun resolveResearchProfileSufficient(contact: ExpertContact, inboundText: String): Boolean {
        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(requireNotNull(contact.id))
        val context = aiReplyContextService.build(contact, records, inboundText, "")
        return context.researchProfileSufficient
    }

    private fun inboundMessageBody(record: com.weibo.talentintroduction.mail.domain.InboundMailProcessing): String =
        record.cleanedBody?.takeIf { it.isNotBlank() } ?: record.body.orEmpty()

    private fun toRequestCoverage(requestFacts: List<RequestFactItem>): List<RequestCoverageItem> =
        requestFacts.map { fact ->
            RequestCoverageItem(
                index = fact.index,
                requestText = fact.requestText,
                status = fact.status.name,
                factRuleIds = fact.factRuleIds,
                intents = fact.intents.map { intent ->
                    IntentCoverageResponse(
                        intentKey = intent.intentKey,
                        title = intent.title,
                        status = intent.status,
                        evidenceRuleIds = intent.evidenceRuleIds,
                        missingEvidenceKeys = intent.missingEvidenceKeys,
                        requiresResearchContext = intent.requiresResearchContext
                    )
                }
            )
        }

    private fun QaRule.toTrustSuggestRule() = SuggestQaRule(
        id = requireNotNull(id),
        categoryId = categoryId,
        displayName = displayName,
        sectionTitle = sectionTitle,
        replySubject = replySubject,
        replyBody = "",
        keywords = keywords,
        replyPolicy = replyPolicyEnum().name
    )

    private fun resolvePendingReplyAccount(
        requestedAccountCode: String?,
        inboundSenderAccountCode: String
    ) = mailSenderAccountService.getManualSendAccount(
        requestedAccountCode?.takeIf { it.isNotBlank() } ?: inboundSenderAccountCode
    )

    private fun scheduleManualRichReplyAudit(
        inboundProcessingId: Long,
        contactId: Long,
        mailRecordId: Long,
        actionType: OperatorActionType,
        after: Map<String, Any?>,
        operatorName: String?,
        note: String
    ) {
        val auditTask = {
            recordManualRichReplyAudit(
                inboundProcessingId = inboundProcessingId,
                contactId = contactId,
                mailRecordId = mailRecordId,
                actionType = actionType,
                after = after,
                operatorName = operatorName,
                note = note
            )
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    auditTask()
                }
            })
        } else {
            auditTask()
        }
    }

    private fun recordManualRichReplyAudit(
        inboundProcessingId: Long,
        contactId: Long,
        mailRecordId: Long,
        actionType: OperatorActionType,
        after: Map<String, Any?>,
        operatorName: String?,
        note: String
    ) {
        try {
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
                "Failed to record manual rich reply audit for inbound {} mailRecord {}: {}",
                inboundProcessingId,
                mailRecordId,
                ex.message,
                ex
            )
        }
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

    fun preflightEditedAiReply(
        inboundProcessingId: Long,
        factRuleIds: List<Long>,
        expectedEvidenceSetVersion: String,
        textBody: String
    ): AiReplyPreflightResult {
        require(textBody.isNotBlank() && textBody.length <= 20000) {
            "textBody must be non-empty and <= 20000 characters"
        }
        require(factRuleIds.size <= 50 && factRuleIds.all { it > 0 } && factRuleIds.size == factRuleIds.distinct().size) {
            "factRuleIds must be positive, deduplicated, and <= 50 items"
        }
        require(expectedEvidenceSetVersion.isEmpty() || expectedEvidenceSetVersion.length <= 128) {
            "expectedEvidenceSetVersion must be <= 128 characters"
        }
        require(expectedEvidenceSetVersion.isEmpty() || AiReplyDraftService.PREFLIGHT_VERSION_CHARSET.matches(expectedEvidenceSetVersion)) {
            "expectedEvidenceSetVersion contains invalid characters"
        }

        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val inboundText = inboundMessageBody(record)
        val contactId = record.expertContactId
            ?: error("Inbound mail not bound to a contact")
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }
        val researchProfileSufficient = resolveResearchProfileSufficient(contact, inboundText)

        val warningCodes = mutableListOf<String>()

        val selection = if (factRuleIds.isNotEmpty()) {
            try {
                qaFactSelectionService.select(inboundText, factRuleIds, researchProfileSufficient)
            } catch (ex: Exception) {
                warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
                qaFactSelectionService.select(inboundText, null, researchProfileSufficient)
            }
        } else {
            qaFactSelectionService.select(inboundText, null, researchProfileSufficient)
        }

        val canonicalFactIds = if (factRuleIds.isNotEmpty()) selection.sendQaRuleIds else emptyList()
        val readiness = aiReplyDraftService.resolveDraftReadinessForSelection(
            selection.requestFacts,
            canonicalFactIds
        )
        val (currentEvidenceSetVersion, _, _) = aiReplyDraftService.buildEvidenceSnapshotForSelection(canonicalFactIds)

        if (expectedEvidenceSetVersion.isNotBlank() && expectedEvidenceSetVersion != currentEvidenceSetVersion) {
            if (AI_REPLY_PREFLIGHT_SOURCE_CHANGED !in warningCodes) {
                warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
            }
        }

        if (factRuleIds.isNotEmpty()) {
            val validCount = canonicalFactIds.size
            val requestedCount = factRuleIds.size
            if (validCount < requestedCount) {
                if (AI_REPLY_PREFLIGHT_SOURCE_CHANGED !in warningCodes) {
                    warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
                }
            }

            for (ruleId in factRuleIds) {
                val rule = try {
                    qaRuleRepository.findById(ruleId).orElse(null)
                } catch (_: Exception) {
                    null
                }
                if (rule == null || !rule.enabled) {
                    if (AI_REPLY_PREFLIGHT_SOURCE_CHANGED !in warningCodes) {
                        warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
                    }
                    continue
                }
                if (rule.answerBody.isBlank()) {
                    if (AI_REPLY_PREFLIGHT_SOURCE_CHANGED !in warningCodes) {
                        warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
                    }
                }
                if (rule.replyPolicyEnum() == QaReplyPolicy.NEVER) {
                    if (AI_REPLY_PREFLIGHT_SOURCE_CHANGED !in warningCodes) {
                        warningCodes += AI_REPLY_PREFLIGHT_SOURCE_CHANGED
                    }
                }
            }
        } else {
            warningCodes += AI_REPLY_PREFLIGHT_NO_EVIDENCE
        }

        val claimValidation = aiReplyHighRiskClaimValidator.validatePlainText(textBody, canonicalFactIds)
        if (!claimValidation.valid) {
            warningCodes += claimValidation.warningCodes
        }

        if (aiReplyHighRiskClaimValidator.containsTrustRhetoric(textBody)) {
            warningCodes += AiReplyHighRiskClaimValidator.WARNING_CLAIM_TRUST_RHETORIC
        }

        val hasBlockingTrust = aiReplyDraftService.hasBlockingTrustGapForSelection(selection.requestFacts)
        if (hasBlockingTrust && aiReplyHighRiskClaimValidator.containsConfidentialitySubstitute(textBody)) {
            warningCodes += AiReplyHighRiskClaimValidator.WARNING_CLAIM_CONFIDENTIALITY_SUBSTITUTE
        }

        for (fact in selection.requestFacts) {
            for (intent in fact.intents) {
                val isAgencyOrCompany = intent.intentKey.startsWith("agency.") || intent.intentKey.startsWith("company.")
                val isEnterprise = intent.intentKey.startsWith("enterprise.")
                if (isAgencyOrCompany || isEnterprise) {
                    val evidenceIds = intent.evidenceRuleIds
                    if (evidenceIds.isNotEmpty()) {
                        val sourceText = aiReplyHighRiskClaimValidator.resolveSourceText(evidenceIds)
                        if (sourceText != null) {
                            if (isAgencyOrCompany) {
                                if (aiReplyHighRiskClaimValidator.isRoleDisclosureRequired(sourceText) &&
                                    !aiReplyHighRiskClaimValidator.containsRoleDisclosure(textBody)
                                ) {
                                    warningCodes += AiReplyHighRiskClaimValidator.WARNING_CLAIM_ROLE_DISCLOSURE_OMITTED
                                }
                            }
                            if (isEnterprise) {
                                if (aiReplyHighRiskClaimValidator.isEnterpriseUncertaintyRequired(sourceText) &&
                                    (aiReplyHighRiskClaimValidator.containsEnterpriseCertainty(textBody) ||
                                        !aiReplyHighRiskClaimValidator.containsEnterpriseUncertainty(textBody))
                                ) {
                                    warningCodes += AiReplyHighRiskClaimValidator.WARNING_CLAIM_ENTERPRISE_UNGROUNDED
                                }
                            }
                        }
                    }
                }
            }
        }

        val allowedActions = AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList())
        val restrictedActions = AiReplyActionPolicy.restrictForTrustState(allowedActions, hasBlockingTrust)
        val violations = AiReplyActionPolicy.findViolations(textBody, restrictedActions)
        violations.forEach { violation ->
            val code = violation.code
            if (code != null && code !in warningCodes) {
                warningCodes += code
            }
        }

        val checkedTextHash = AiReplyDraftService.sha256Hex(textBody)
        val distinctWarnings = warningCodes.distinct()

        val status = if (distinctWarnings.isEmpty()) "PASS" else "WARNING"

        return AiReplyPreflightResult(
            status = status,
            warningCodes = distinctWarnings,
            canonicalFactIds = canonicalFactIds,
            evidenceReadiness = readiness.name,
            currentEvidenceSetVersion = currentEvidenceSetVersion,
            checkedTextHash = checkedTextHash
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
    val operatorName: String?,
    val useVariants: Boolean = false
)

data class PendingManualRichReplyRequest(
    val senderAccountCode: String?,
    val subject: String,
    val htmlBody: String,
    val textBody: String?,
    val operatorName: String?,
    val qaRuleIds: List<Long>? = null,
    val suggestedRuleIds: List<Long>? = null,
    val ackSnippetId: Long? = null,
    val edited: Boolean? = null,
    val freeTextPreview: String? = null,
    val useVariants: Boolean = false,
    val templateTextBody: String? = null,
    val templateHtmlBody: String? = null
)

data class ComposedReplyRequest(
    val qaRuleIds: List<Long>,
    val overrideTextBody: String?,
    val freeTextBody: String? = null,
    val ackSnippetId: Long? = null,
    val senderAccountCode: String?,
    val operatorName: String?,
    val useVariants: Boolean = false
)

data class ComposedReplyEvaluateRequest(
    val factRuleIds: List<Long>
)

data class TrustWorkbenchSuggestResult(
    val suggestedRuleIds: List<Long>,
    val suggestedRules: List<SuggestQaRule>,
    val rulesByCategory: List<CategoryRulesGroup>,
    val gapItems: List<GapItem>,
    val gapDetected: Boolean,
    val matchedCategoryIds: List<Long>,
    val draftReadiness: String,
    val requestCoverage: List<RequestCoverageItem>,
    val inboundText: String
)

data class TrustWorkbenchEvaluateResult(
    val canonicalFactIds: List<Long>,
    val suggestedFactIds: List<Long>,
    val draftReadiness: String,
    val requestCoverage: List<RequestCoverageItem>,
    val gapDetected: Boolean
)

data class AiReplyPreflightRequest(
    val factRuleIds: List<Long> = emptyList(),
    val expectedEvidenceSetVersion: String = "",
    val textBody: String
)

data class AiReplyPreflightResult(
    val status: String,
    val warningCodes: List<String>,
    val canonicalFactIds: List<Long>,
    val evidenceReadiness: String,
    val currentEvidenceSetVersion: String,
    val checkedTextHash: String
)
