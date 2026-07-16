package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.llm.service.AiReplyReviewAuditService
import com.weibo.talentintroduction.llm.service.AiReviewConfirmation
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.CompositionSuggestResult
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.qa.service.QaReplyComposer
import com.weibo.talentintroduction.qa.service.QaRuleMatch
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import com.weibo.talentintroduction.reply.service.SnippetType
import com.weibo.talentintroduction.reply.service.AckOption
import com.weibo.talentintroduction.reply.service.ManualReplyFrame
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.domain.ContentVariantOwnerType
import com.weibo.talentintroduction.variant.service.ContentVariantService
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
    private val mailContentService: MailContentService,
    private val replySnippetService: ReplySnippetService,
    private val mailVariableService: MailVariableService,
    private val contentVariantService: ContentVariantService,
    private val aiReplyReviewAuditService: AiReplyReviewAuditService
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
        operatorName: String?,
        useVariants: Boolean = false
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

        val account = resolvePendingReplyAccount(senderAccountCode, record.senderAccountCode)
        val seed = variantSeedFor(contact)
        val replyBody = resolveRuleBody(rule, seed, useVariants)

        val plainBody = mailVariableService.renderForContact(replyBody, account, contact)
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
        operatorName: String?,
        qaRuleIds: List<Long>? = null,
        suggestedRuleIds: List<Long>? = null,
        ackSnippetId: Long? = null,
        edited: Boolean? = null,
        freeTextPreview: String? = null,
        useVariants: Boolean = false,
        templateTextBody: String? = null,
        templateHtmlBody: String? = null,
        replySource: String? = null,
        aiReviewConfirmation: AiReviewConfirmation? = null
    ): PendingMailSendResult {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val contactId = record.expertContactId
            ?: error("Inbound mail not bound to a contact")
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }

        require(subject.isNotBlank()) { "Subject is required" }
        require(htmlBody.isNotBlank()) { "HTML body is required" }

        aiReplyReviewAuditService.validateConfirmationForSend(
            inboundProcessingId = inboundProcessingId,
            draftIdentity = aiReviewConfirmation?.draftIdentity,
            confirmedReviewKeys = aiReviewConfirmation?.confirmedReviewKeys ?: emptyList(),
            operatorNote = aiReviewConfirmation?.operatorNote ?: ""
        )

        val carriesQa = !qaRuleIds.isNullOrEmpty()
        val primaryRuleId = if (carriesQa) {
            val rules = qaRuleIds!!.map { ruleId ->
                val rule = qaRuleRepository.findById(ruleId)
                    .orElseThrow { error("QA rule not found: $ruleId") }
                require(rule.enabled) { "QA rule is disabled: $ruleId" }
                rule
            }
            val matches = rules.map { QaRuleMatch(rule = it, matchedKeywordCount = 1) }
            requireNotNull(QaReplyComposer.selectPrimary(matches).rule.id)
        } else {
            null
        }

        val account = resolvePendingReplyAccount(senderAccountCode, record.senderAccountCode)

        // Always-on final render gate (I-1..I-4): every manual-rich request validates and
        // re-renders with the resolved account/contact. Optional template* fields prefer
        // AI adoption raw; otherwise editor text/html are treated as the raw input.
        val rawText = templateTextBody?.takeIf { it.isNotBlank() }
            ?: textBody?.takeIf { it.isNotBlank() }
            ?: mailBodyCleaner.clean(htmlBody)
        val rawHtmlFromTemplate = templateHtmlBody?.takeIf { it.isNotBlank() }
        mailVariableService.requireValidPlaceholders(rawText)
        if (rawHtmlFromTemplate != null) {
            mailVariableService.requireValidPlaceholders(rawHtmlFromTemplate)
        } else if (templateTextBody.isNullOrBlank()) {
            // No text template either — validate editor HTML as raw (blocks ${typo} in rich body).
            mailVariableService.requireValidPlaceholders(htmlBody)
        }

        val renderedText = mailVariableService.renderForContact(rawText, account, contact)
        val finalTextBody = renderedText
        val finalHtmlBody = when {
            rawHtmlFromTemplate != null ->
                mailVariableService.renderHtmlForContact(rawHtmlFromTemplate, account, contact)
            !templateTextBody.isNullOrBlank() ->
                // Plain AI text template: derive HTML from rendered text (escapes via plainTextToHtml).
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

        if (aiReviewConfirmation?.draftIdentity != null || replySource == "AI_DRAFT") {
            aiReplyReviewAuditService.recordConfirmed(
                inboundProcessingId = inboundProcessingId,
                contactId = contactId,
                mailRecordId = mailRecordId,
                confirmation = aiReviewConfirmation,
                operatorName = operatorName
            )
        }

        if (carriesQa) {
            qaRuleIds!!.forEachIndexed { ordinal, qaRuleId ->
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
                before = mapOf("inboundProcessingId" to inboundProcessingId),
                after = mapOf(
                    "mailRecordId" to mailRecordId,
                    "qaRuleIds" to qaRuleIds,
                    "suggestedRuleIds" to (suggestedRuleIds ?: emptyList()),
                    "ackSnippetId" to ackSnippetId,
                    "edited" to (edited ?: false),
                    "freeTextPreview" to freeTextPreview,
                    "sendStatus" to delivered.status,
                    "subject" to mail.subject,
                    "bodyPreviewText" to bodyPreviewText
                ),
                operatorName = operatorName,
                note = "Manual rich reply with QA rules sent for inbound processing $inboundProcessingId"
            )
        } else {
            operatorActionLogService.record(
                targetType = "INBOUND_MAIL_PROCESSING",
                targetId = inboundProcessingId,
                actionType = OperatorActionType.SEND_MANUAL_RICH_REPLY,
                expertContactId = contactId,
                inboundProcessingId = inboundProcessingId,
                before = mapOf("inboundProcessingId" to inboundProcessingId),
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

    fun suggestComposedReply(inboundProcessingId: Long, useVariants: Boolean = false): CompositionSuggestResult {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val messageBody = record.cleanedBody?.takeIf { it.isNotBlank() } ?: record.body.orEmpty()
        val base = qaMatchService.suggestComposition(messageBody)
        if (!useVariants) {
            return base
        }
        val contact = record.expertContactId?.let { contactId ->
            expertContactRepository.findById(contactId).orElse(null)
        } ?: return base
        val seed = variantSeedFor(contact)
        return base.copy(
            suggestedRules = base.suggestedRules.map { rule ->
                rule.copy(replyBody = resolveSuggestRuleBody(rule.id, rule.replyBody, seed, useVariants = true))
            },
            rulesByCategory = base.rulesByCategory.map { group ->
                group.copy(
                    rules = group.rules.map { rule ->
                        rule.copy(replyBody = resolveSuggestRuleBody(rule.id, rule.replyBody, seed, useVariants = true))
                    }
                )
            }
        )
    }

    fun resolveManualFrameForInbound(inboundProcessingId: Long, useVariants: Boolean = false): ManualReplyFrame {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val contact = record.expertContactId?.let { contactId ->
            expertContactRepository.findById(contactId).orElse(null)
        }
        val seed = contact?.let { variantSeedFor(it) } ?: 0
        return resolveManualFrame(seed, useVariants)
    }

    @Transactional
    fun sendManualComposedReply(
        inboundProcessingId: Long,
        qaRuleIds: List<Long>,
        overrideTextBody: String?,
        freeTextBody: String?,
        ackSnippetId: Long?,
        senderAccountCode: String?,
        operatorName: String?,
        useVariants: Boolean = false
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

        val seed = variantSeedFor(contact)
        val resolvedRules = resolveRules(rules, seed, useVariants)
        val matches = resolvedRules.map { QaRuleMatch(rule = it, matchedKeywordCount = 1) }
        val frame = resolveManualFrame(seed, useVariants)
        val ack = resolveAckContent(ackSnippetId, seed, useVariants)
        val composed = QaReplyComposer.composeInOperatorOrder(
            matches = matches,
            salutation = frame.salutation,
            ack = ack,
            greeting = frame.greeting,
            closing = frame.closing
        )
        val primary = QaReplyComposer.selectPrimary(matches)
        val primaryRuleId = requireNotNull(primary.rule.id)

        val composedWithFreeText = appendFreeText(composed.replyBody, freeTextBody)
        val finalBody = overrideTextBody?.takeIf { it.isNotBlank() } ?: composedWithFreeText
        val edited = overrideTextBody?.takeIf { it.isNotBlank() }?.let { it != composedWithFreeText } ?: false
        val freeTextPreview = freeTextBody?.trim()?.takeIf { it.isNotBlank() }?.take(200)
        val subject = composed.replySubject ?: "Re: ${record.subject.orEmpty()}".trim()

        val account = resolvePendingReplyAccount(senderAccountCode, record.senderAccountCode)

        val renderedBody = mailVariableService.renderForContact(finalBody, account, contact)
        val mail = ComposedMail(
            to = contact.expertEmail,
            subject = subject,
            body = mailContentService.plainTextToHtml(renderedBody),
            html = true,
            text = renderedBody
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
                body = renderedBody,
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
                "ackSnippetId" to ackSnippetId,
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

    private fun resolvePendingReplyAccount(
        requestedAccountCode: String?,
        inboundSenderAccountCode: String
    ) = mailSenderAccountService.getManualSendAccount(
        requestedAccountCode?.takeIf { it.isNotBlank() } ?: inboundSenderAccountCode
    )

    private fun appendFreeText(composedBody: String, freeTextBody: String?): String {
        val free = freeTextBody?.trim().orEmpty()
        if (free.isBlank()) {
            return composedBody
        }
        return if (composedBody.isBlank()) free else "$composedBody\n\n$free"
    }

    private fun variantSeedFor(contact: ExpertContact): Int =
        MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)

    private fun resolveRuleBody(rule: QaRule, seed: Int, useVariants: Boolean): String {
        val ruleId = rule.id ?: return rule.replyBody
        return contentVariantService.resolveBody(
            ownerType = ContentVariantOwnerType.QA_RULE,
            ownerId = ruleId,
            mainBody = rule.replyBody,
            seed = seed,
            useVariants = useVariants
        )
    }

    private fun resolveSuggestRuleBody(ruleId: Long, replyBody: String, seed: Int, useVariants: Boolean): String =
        contentVariantService.resolveBody(
            ownerType = ContentVariantOwnerType.QA_RULE,
            ownerId = ruleId,
            mainBody = replyBody,
            seed = seed,
            useVariants = useVariants
        )

    private fun resolveRules(rules: List<QaRule>, seed: Int, useVariants: Boolean): List<QaRule> =
        rules.map { rule -> rule.copy(replyBody = resolveRuleBody(rule, seed, useVariants)) }

    private fun resolveManualFrame(seed: Int, useVariants: Boolean): ManualReplyFrame {
        val frame = replySnippetService.resolveManualFrame()
        if (!useVariants) {
            return frame
        }
        return ManualReplyFrame(
            salutation = resolveDefaultSnippetText(SnippetType.SALUTATION, frame.salutation, seed),
            greeting = resolveDefaultSnippetText(SnippetType.GREETING, frame.greeting, seed),
            closing = resolveDefaultSnippetText(SnippetType.CLOSING, frame.closing, seed),
            ackOptions = frame.ackOptions.map { ack ->
                AckOption(
                    id = ack.id,
                    content = contentVariantService.resolveBody(
                        ownerType = ContentVariantOwnerType.REPLY_SNIPPET,
                        ownerId = ack.id,
                        mainBody = ack.content,
                        seed = seed,
                        useVariants = true
                    )
                )
            }
        )
    }

    private fun resolveDefaultSnippetText(type: SnippetType, current: String?, seed: Int): String? {
        if (current.isNullOrBlank()) {
            return current
        }
        val defaultSnippet = replySnippetService.listByType(type.name)
            .firstOrNull { it.snippet.isDefault && it.snippet.enabled }
            ?.snippet
            ?: return current
        val snippetId = defaultSnippet.id ?: return current
        return contentVariantService.resolveBody(
            ownerType = ContentVariantOwnerType.REPLY_SNIPPET,
            ownerId = snippetId,
            mainBody = defaultSnippet.content,
            seed = seed,
            useVariants = true
        )
    }

    private fun resolveAckContent(ackSnippetId: Long?, seed: Int, useVariants: Boolean): String? {
        val ack = replySnippetService.resolveAck(ackSnippetId) ?: return null
        if (!useVariants || ackSnippetId == null) {
            return ack
        }
        return contentVariantService.resolveBody(
            ownerType = ContentVariantOwnerType.REPLY_SNIPPET,
            ownerId = ackSnippetId,
            mainBody = ack,
            seed = seed,
            useVariants = true
        )
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
    val templateHtmlBody: String? = null,
    val replySource: String? = null,
    val aiReviewConfirmation: AiReviewConfirmation? = null
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
