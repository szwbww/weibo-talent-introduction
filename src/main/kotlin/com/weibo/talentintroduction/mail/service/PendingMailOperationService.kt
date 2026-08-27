package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
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
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleRequest
import com.weibo.talentintroduction.llm.service.TrustReplyItemVersion
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplySourceType
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchException
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchService
import com.weibo.talentintroduction.llm.service.VerifiedTrustReplyAssembly
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerArchiveStatus
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexArchiveResult
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexService
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.CategoryRulesGroup
import com.weibo.talentintroduction.qa.service.CompositionSuggestResult
import com.weibo.talentintroduction.qa.service.GapItem
import com.weibo.talentintroduction.qa.service.SuggestQaRule
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.slf4j.LoggerFactory
import java.time.Instant
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
    private val mailVariableService: MailVariableService,
    private val manualReplySendAttemptService: ManualReplySendAttemptService,
    private val trustReplyWorkbenchService: TrustReplyWorkbenchService,
    private val unsupportedAnswerIndexService: UnsupportedAnswerIndexService,
    private val emailSuppressionService: EmailSuppressionService
) {
    companion object {
        private val log = LoggerFactory.getLogger(PendingMailOperationService::class.java)
        const val AI_REPLY_PREFLIGHT_SOURCE_CHANGED = "AI_REPLY_PREFLIGHT_SOURCE_CHANGED"
        const val AI_REPLY_PREFLIGHT_NO_EVIDENCE = "AI_REPLY_PREFLIGHT_NO_EVIDENCE"
        // 计划 04 (T2.7): 显式 QA 选择在发送路径被降级为可确认风险时的诊断码。
        const val QA_FACT_NOT_MATCHING_REQUEST = "QA_FACT_NOT_MATCHING_REQUEST"
        const val QA_FACT_UNAVAILABLE = "QA_FACT_UNAVAILABLE"
        const val QA_FACT_NO_EXTRACTABLE_REQUEST = "QA_FACT_NO_EXTRACTABLE_REQUEST"
        private val HREF_EXTRACTOR = Regex(
            """href\s*=\s*(?:"([^"]+)"|'([^']+)'|([^\s>"']+))""",
            RegexOption.IGNORE_CASE
        )
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
        trustReplyAssembly: TrustReplyAssembleRequest? = null,
        safetyWarningConfirmed: Boolean = false,
        strongConfirmationText: String? = null
    ): PendingMailSendResult {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
        val contactId = record.expertContactId
            ?: error("Inbound mail not bound to a contact")
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }

        require(subject.isNotBlank()) { "Subject is required" }
        require(htmlBody.isNotBlank()) { "HTML body is required" }
        val trimmedSubject = subject.trim()
        require(trimmedSubject.length <= 255) { "Subject exceeds 255 characters" }

        val inboundText = inboundMessageBody(record)
        val researchProfileSufficient = resolveResearchProfileSufficient(contact, inboundText)

        // 03 (I-1): 可信 workbench assembly 必须在任何发送副作用（suppression /
        // prepareAndClaim / SMTP / DB 成功落库）之前完成服务端重算验证；source 必须
        // 指向本次来信，验证失败在 claim 前稳定失败，且不烧掉 attempt（I-7）。
        val verifiedAssembly = trustReplyAssembly?.let { assembly ->
            if (assembly.source.sourceType != TrustReplySourceType.LIVE_INBOUND ||
                assembly.source.sourceId != inboundProcessingId
            ) {
                throw ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Trust reply assembly must target the current inbound"
                )
            }
            try {
                trustReplyWorkbenchService.verifyAssembly(assembly)
                    ?: error("Trust reply assembly verification returned no result")
            } catch (ex: TrustReplyWorkbenchException) {
                throw ResponseStatusException(ex.status, ex.code)
            }
        }
        // 03 (I-2): 有 assembly 时 canonical facts 只来自服务端重算；客户端
        // qaRuleIds 必须与 verified canonical facts 逐元素相等（任一缺失/增加/乱序都
        // 在 claim 前失败），绝不静默采纳客户端 ids、不回退自动推荐、不部分删减。
        if (verifiedAssembly != null && qaRuleIds.orEmpty() != verifiedAssembly.response.canonicalFactIds) {
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "qaRuleIds must equal the server-verified canonical fact ids"
            )
        }
        // 03 (阶段 2.3): carriesQa —— assembly 路径按 verified canonical 是否非空；
        // 无 assembly 的 legacy 路径保留「客户端提交过 qaRuleIds」的既有判据。
        val carriesQa = if (verifiedAssembly != null) {
            verifiedAssembly.response.canonicalFactIds.isNotEmpty()
        } else {
            !qaRuleIds.isNullOrEmpty()
        }
        val factResolution = if (verifiedAssembly != null) {
            // 03 (I-2/I-6): 无 degraded codes，canonical ids 原样进入 safety 与 SendPayload。
            CanonicalFactResolution(verifiedAssembly.response.canonicalFactIds, emptyList())
        } else if (carriesQa) {
            canonicalizeFactRuleIds(inboundText, qaRuleIds!!, researchProfileSufficient)
        } else {
            CanonicalFactResolution(emptyList(), emptyList())
        }
        val canonicalFactIds = factResolution.canonicalFactIds
        val serverSuggestedFactIds = if (carriesQa) {
            qaFactSelectionService.select(inboundText, null, researchProfileSufficient).sendQaRuleIds
        } else {
            emptyList()
        }
        val primaryRuleId = canonicalFactIds.firstOrNull()

        val account = resolvePendingReplyAccount(senderAccountCode, record.senderAccountCode)

        mailVariableService.requireValidPlaceholders(trimmedSubject)
        val renderedSubject = mailVariableService.renderForContact(trimmedSubject, account, contact)
        require(renderedSubject.isNotBlank()) { "Rendered subject is empty" }
        require(renderedSubject.length <= 255) { "Rendered subject exceeds 255 characters: ${renderedSubject.length}" }

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

        val finalValidationText = buildFinalValidationText(renderedSubject, finalTextBody, finalHtmlBody)
        require(finalValidationText.isNotBlank()) { "Final validation text is empty after rendering" }
        require(finalValidationText.length <= 20000) {
            "Final validation text exceeds 20,000 characters (${finalValidationText.length})"
        }
        mailVariableService.requireValidPlaceholders(finalTextBody)
        mailVariableService.requireValidPlaceholders(finalHtmlBody)

        // 03 (I-5): operator action 授权只来自通过 verifyAssembly 的 locked versions
        // （服务端重算结果），绝不再直接读取客户端 lockedItems；assembly 无效时
        // verifiedAssembly 为 null 且发送已失败，授权集合自然为空（fail-closed）。
        val operatorAuthorized = verifiedAssembly
            ?.let { trustReplyWorkbenchService.operatorAuthorizedActionsFromVerifiedVersions(it.response.itemVersions) }
            .orEmpty()

        val findings = collectSafetyFindings(
            verificationText = finalValidationText,
            carriesQa = carriesQa,
            canonicalFactIds = canonicalFactIds,
            contact = contact,
            inboundText = inboundText,
            researchProfileSufficient = researchProfileSufficient,
            operatorAuthorizedActions = operatorAuthorized,
            degradedFactCodes = factResolution.degradedCodes,
            // 03 (I-4): 可信 assembly 路径复用服务端已验证 selection 作为事实选择数据源，
            // 禁止再次调用 qaFactSelectionService.select() 做语义重筛。
            verifiedSelection = verifiedAssembly?.selection
        )
        val requiresStrong = findings.any { it.severity == SafetySeverity.STRONG }
        if (findings.isNotEmpty() && !safetyWarningConfirmed) {
            throw ManualSendSafetyBlockedException(findings)
        }
        if (requiresStrong && strongConfirmationText?.trim() != "确认发送") {
            throw ManualSendSafetyBlockedException(findings)
        }

        val payload = ManualReplySendAttemptService.SendPayload(
            orcidId = contact.orcidId,
            contactId = contactId,
            inboundProcessingId = inboundProcessingId,
            accountCode = account.accountCode,
            normalizedRecipient = contact.expertEmail.lowercase().trim(),
            subject = renderedSubject,
            finalText = finalTextBody,
            finalHtml = finalHtmlBody,
            inReplyTo = record.messageId,
            canonicalQaRuleIds = canonicalFactIds,
            primaryRuleId = primaryRuleId
        )

        val inReplyTo = record.messageId
        if (inReplyTo != null && inReplyTo.length > 255) {
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "inReplyTo exceeds 255 characters"
            )
        }

        // I-5: 幂等占位（prepareAndClaim）之前必须判抑制，禁止把发送尝试烧成 DELIVERY_UNKNOWN。
        if (emailSuppressionService.isSuppressed(contact.expertEmail)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "收件人已退订，禁止外发：${contact.expertEmail}"
            )
        }

        val claim = manualReplySendAttemptService.prepareAndClaim(payload)

        return when (claim.result) {
            ManualReplySendAttemptService.ClaimResult.CLAIMED,
            ManualReplySendAttemptService.ClaimResult.SAFE_RETRY_CLAIMED -> {
                val mail = ComposedMail(
                    to = contact.expertEmail,
                    subject = renderedSubject,
                    body = finalHtmlBody,
                    html = true,
                    text = finalTextBody,
                    messageId = claim.messageId
                )
                val bodyPreviewText = (finalTextBody.ifBlank { mailBodyCleaner.clean(finalHtmlBody) }
                    .takeIf { it.isNotBlank() } ?: mailBodyCleaner.clean(finalHtmlBody)).take(500)

                try {
                    val delivered = mailDeliveryService.send(account, mail)
                    val classification = classifyDelivery(delivered)

                    if (classification.isSent) {
                        val mailRecordId = try {
                            val id = manualReplySendAttemptService.finalizeSuccess(
                                payload = payload,
                                attemptId = claim.attemptId,
                                messageId = claim.messageId
                            )
                            manualReplySendAttemptService.recordSendAudit(
                                inboundProcessingId = inboundProcessingId,
                                contactId = contactId,
                                mailRecordId = id,
                                canonicalFactIds = canonicalFactIds,
                                carriesQa = carriesQa,
                                delivered = delivered,
                                sendSubject = renderedSubject,
                                bodyPreviewText = bodyPreviewText,
                                operatorName = operatorName,
                                inboundRecord = record,
                                serverSuggestedFactIds = serverSuggestedFactIds,
                                edited = edited,
                                // 04 (I-1/I-7): 仅在该次发送存在 verified assembly 时把服务端
                                // 诊断附加到既有发送 action；无 assembly（纯人工 rich reply、
                                // legacy QA 发送）传 null，after payload 逐字不变。
                                trustReplyDiagnostics = verifiedAssembly?.response?.diagnostics,
                                note = buildString {
                                    append("Manual rich reply sent for inbound processing $inboundProcessingId")
                                    if (findings.isNotEmpty()) {
                                        append("; safety findings confirmed: ")
                                        val codes = findings.map { it.code }
                                        append(codes.take(10).joinToString(","))
                                        if (codes.size > 10) {
                                            append("+").append(codes.size)
                                        }
                                    }
                                    if (requiresStrong) {
                                        append("; strong confirmation typed")
                                    }
                                }
                            )
                            id
                        } catch (finalizeEx: Exception) {
                            log.warn("finalizeSuccess failed for attempt {}: {}", claim.attemptId, finalizeEx.message)
                            try {
                                manualReplySendAttemptService.finalizeFailure(
                                    payload = payload,
                                    attemptId = claim.attemptId,
                                    messageId = claim.messageId,
                                    resultStatus = MailSendAttemptStatus.DELIVERY_UNKNOWN,
                                    errorSummary = "finalize_failure:${finalizeEx.message?.take(400).orEmpty()}"
                                )
                            } catch (finalizeFailEx: Exception) {
                                log.error("finalizeFailure to UNKNOWN also failed for attempt {}: {}",
                                    claim.attemptId, finalizeFailEx.message)
                            }
                            throw ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
                            )
                        }
                        val archive = archiveLiveUnsupportedAnswers(
                            inboundProcessingId = inboundProcessingId,
                            templateTextBody = templateTextBody,
                            finalTextBody = finalTextBody,
                            operatorName = operatorName,
                            outboundMailRecordId = mailRecordId,
                            verifiedAssembly = verifiedAssembly
                        )
                        PendingMailSendResult(
                            contactId = contactId,
                            senderAccountCode = account.accountCode,
                            mailType = "MANUAL_RICH_REPLY",
                            subject = renderedSubject,
                            sendStatus = "SENT",
                            messageId = claim.messageId,
                            unsupportedAnswerArchiveStatus = archive.status,
                            unsupportedAnswerArchivedCount = archive.archivedCount,
                            unsupportedAnswerArchiveFailedCount = archive.failedCount
                        )
                    } else {
                        manualReplySendAttemptService.finalizeFailure(
                            payload = payload,
                            attemptId = claim.attemptId,
                            messageId = claim.messageId,
                            resultStatus = classification.attemptStatus,
                            errorSummary = classification.errorSummary
                        )
                        if (classification.isUnknown) {
                            throw ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
                            )
                        }
                        if (classification.isSafeRetry) {
                            throw ResponseStatusException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "发送暂时失败，可安全重试"
                            )
                        }
                        throw ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "发送失败，请修改内容后重试"
                        )
                    }
                } catch (deliveryEx: Exception) {
                    when (deliveryEx) {
                        is ResponseStatusException -> throw deliveryEx
                        else -> {
                            log.warn("Unchecked delivery error for attempt {}: {}", claim.attemptId, deliveryEx.message)
                            try {
                                manualReplySendAttemptService.finalizeFailure(
                                    payload = payload,
                                    attemptId = claim.attemptId,
                                    messageId = claim.messageId,
                                    resultStatus = MailSendAttemptStatus.DELIVERY_UNKNOWN,
                                    errorSummary = "delivery_error:${deliveryEx.message?.take(400).orEmpty()}"
                                )
                            } catch (finalizeEx: Exception) {
                                log.error("finalizeFailure to UNKNOWN failed for attempt {}: {}",
                                    claim.attemptId, finalizeEx.message)
                            }
                            throw ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
                            )
                        }
                    }
                }
            }

            ManualReplySendAttemptService.ClaimResult.DEDUP_SENT -> {
                val existingRecord = mailRecordRepository.findByMailSendAttemptId(claim.attemptId)
                val archive = archiveLiveUnsupportedAnswers(
                    inboundProcessingId = inboundProcessingId,
                    templateTextBody = templateTextBody,
                    finalTextBody = finalTextBody,
                    operatorName = operatorName,
                    outboundMailRecordId = existingRecord?.id,
                    verifiedAssembly = verifiedAssembly
                )
                PendingMailSendResult(
                    contactId = contactId,
                    senderAccountCode = payload.accountCode,
                    mailType = "MANUAL_RICH_REPLY",
                    subject = renderedSubject,
                    sendStatus = "SENT",
                    messageId = existingRecord?.messageId ?: claim.messageId,
                    unsupportedAnswerArchiveStatus = archive.status,
                    unsupportedAnswerArchivedCount = archive.archivedCount,
                    unsupportedAnswerArchiveFailedCount = archive.failedCount
                )
            }

            ManualReplySendAttemptService.ClaimResult.IN_PROGRESS ->
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
                )

            ManualReplySendAttemptService.ClaimResult.UNKNOWN ->
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "发送状态未知，请勿重复发送 (Message-ID: ${claim.messageId})"
                )

            ManualReplySendAttemptService.ClaimResult.PERMANENT_FAILED ->
                throw ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "该内容已发送失败，请修改内容后重试"
                )
        }
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

    private data class CanonicalFactResolution(
        val canonicalFactIds: List<Long>,
        val degradedCodes: List<String>   // 有序、去重
    )

    // 计划 04 (T2.1): 显式 QA 选择的唯一放宽接缝（I-1）。select() 本身逐字不变；
    // 仅当它抛 IllegalArgumentException（validateExplicitSelection /
    // validateExplicitRulesMatchRequests 的异常）时降级为可确认的风险：
    // - I-2: 只捕获 IllegalArgumentException，真故障（DB/IO/ResponseStatusException）向上抛；
    // - I-3: 降级产出的 canonicalFactIds 只能是运营选择的子集（partition.selectable
    //   再经一次真 select()），永不回退自动全集；
    // - I-5: 子集仍走真 select()，用其 sendQaRuleIds 作为取证源；
    // - I-6: 子集为空时直接返回 emptyList()，禁止调 select(emptyList())。
    private fun canonicalizeFactRuleIds(
        inboundText: String,
        requestedRuleIds: List<Long>,
        researchProfileSufficient: Boolean
    ): CanonicalFactResolution {
        try {
            return CanonicalFactResolution(
                qaFactSelectionService.select(inboundText, requestedRuleIds, researchProfileSufficient).sendQaRuleIds,
                emptyList()
            )
        } catch (ex: IllegalArgumentException) {
            val partition = qaFactSelectionService.partitionExplicitSelection(inboundText, requestedRuleIds)
            val codes = mutableListOf<String>()
            if (partition.noRequests) {
                codes += QA_FACT_NO_EXTRACTABLE_REQUEST
            }
            if (partition.unmatched.isNotEmpty()) {
                codes += QA_FACT_NOT_MATCHING_REQUEST
            }
            if (partition.unavailable.isNotEmpty()) {
                codes += QA_FACT_UNAVAILABLE
            }
            val ids = if (partition.selectable.isEmpty()) {
                emptyList()
            } else {
                try {
                    qaFactSelectionService.select(inboundText, partition.selectable, researchProfileSufficient)
                        .sendQaRuleIds
                } catch (innerEx: IllegalArgumentException) {
                    codes += QA_FACT_UNAVAILABLE
                    emptyList()
                }
            }
            return CanonicalFactResolution(ids, codes.distinct())
        }
    }

    private fun resolveResearchProfileSufficient(contact: ExpertContact, inboundText: String): Boolean {
        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(requireNotNull(contact.id))
        val context = aiReplyContextService.build(contact, records, inboundText, "")
        return context.researchProfileSufficient
    }

    private fun inboundMessageBody(record: com.weibo.talentintroduction.mail.domain.InboundMailProcessing): String =
        record.cleanedBody?.takeIf { it.isNotBlank() } ?: record.body.orEmpty()

    private fun archiveLiveUnsupportedAnswers(
        inboundProcessingId: Long,
        templateTextBody: String?,
        finalTextBody: String,
        operatorName: String?,
        outboundMailRecordId: Long?,
        // 03 (阶段 4): 直接复用发送前 verifyAssembly 的已验证结果；不再发送成功后
        // 二次 assemble（避免前后两次解析漂移）。
        verifiedAssembly: VerifiedTrustReplyAssembly?
    ): UnsupportedAnswerIndexArchiveResult {
        if (verifiedAssembly == null || outboundMailRecordId == null) {
            return UnsupportedAnswerIndexArchiveResult()
        }
        val assembled = verifiedAssembly.response
        val operatorDirectedCount = assembled.itemVersions.count {
            it.handling == TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT
        }
        return try {
            val rawTemplate = templateTextBody?.takeIf { it.isNotBlank() }
                ?: return UnsupportedAnswerIndexArchiveResult()
            // 03 (阶段 4): 仅当实际发送正文仍等于 assembly 产物时才归档 operator-directed
            // 样本；运营编辑正文可正常发送但不归档。
            if (assembled.rawDraftText != rawTemplate || assembled.renderedDraftText != finalTextBody) {
                return failedArchive(operatorDirectedCount)
            }
            val eligibleVersions = assembled.itemVersions.filter(::isArchiveEligibleOperatorDirectedVersion)
            if (eligibleVersions.isEmpty()) {
                return UnsupportedAnswerIndexArchiveResult()
            }
            val resolved = trustReplyWorkbenchService.resolveSource(assembled.source)
            unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
                source = resolved,
                versions = eligibleVersions,
                qualificationId = outboundMailRecordId.toString(),
                approvedBy = normalizeArchiveOperatorName(operatorName),
                createdAt = Instant.now()
            )
        } catch (error: TrustReplyWorkbenchException) {
            log.warn(
                "Unsupported answer archive rejected for inbound {}: {}",
                inboundProcessingId,
                error.code
            )
            failedArchive(operatorDirectedCount)
        } catch (error: Exception) {
            log.warn(
                "Unsupported answer archive failed for inbound {}: {}",
                inboundProcessingId,
                error.javaClass.simpleName
            )
            failedArchive(operatorDirectedCount.coerceAtLeast(eligibleFailureCount(verifiedAssembly)))
        }
    }

    private fun failedArchive(failedCount: Int): UnsupportedAnswerIndexArchiveResult =
        UnsupportedAnswerIndexArchiveResult(
            status = UnsupportedAnswerArchiveStatus.FAILED,
            failedCount = failedCount.coerceAtLeast(1)
        )

    private fun eligibleFailureCount(verifiedAssembly: VerifiedTrustReplyAssembly): Int =
        verifiedAssembly.response.itemVersions.count {
            it.handling == TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT
        }.coerceAtLeast(1)

    private fun isArchiveEligibleOperatorDirectedVersion(version: TrustReplyItemVersion): Boolean =
        version.handling == TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT &&
            version.generationKind == TrustReplyItemGenerationKind.AI_GENERATED &&
            version.requestText.isNotBlank() &&
            version.operatorInstruction.isNotBlank() &&
            version.answerText.isNotBlank()

    private fun normalizeArchiveOperatorName(value: String?): String {
        val normalized = value?.trim().orEmpty()
        return normalized.ifEmpty { "UNKNOWN" }.take(128)
    }

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

    internal data class ManualReplyDeliveryClassification(
        val attemptStatus: String,
        val isSent: Boolean,
        val isSafeRetry: Boolean,
        val isUnknown: Boolean,
        val errorSummary: String?
    )

    private fun buildFinalValidationText(subject: String, finalText: String, finalHtml: String): String {
        val htmlPlain = mailContentService.htmlToPlainText(finalHtml)
        val hrefs = HREF_EXTRACTOR.findAll(finalHtml).map { m ->
            m.groupValues[1].takeIf { it.isNotEmpty() }
                ?: m.groupValues[2].takeIf { it.isNotEmpty() }
                ?: m.groupValues[3]
        }.toList()
        return listOfNotNull(
            subject.takeIf { it.isNotBlank() },
            finalText.takeIf { it.isNotBlank() },
            htmlPlain.takeIf { it.isNotBlank() },
            hrefs.takeIf { it.isNotEmpty() }?.joinToString(" ")
        ).joinToString(" ")
    }

    private fun collectSafetyFindings(
        verificationText: String,
        carriesQa: Boolean,
        canonicalFactIds: List<Long>,
        contact: ExpertContact,
        inboundText: String,
        researchProfileSufficient: Boolean,
        operatorAuthorizedActions: Set<AiReplyAction>,
        // 计划 04 (T2.4): 发送路径显式 QA 选择被降级时产生的诊断码（默认空，预检不传——
        // 预检已有 AI_REPLY_PREFLIGHT_SOURCE_CHANGED，不重复报，N-6）。
        degradedFactCodes: List<String> = emptyList(),
        // 03 (I-4): 可信 assembly 路径复用服务端已验证 selection（非 null 时禁止再次
        // 调用 qaFactSelectionService.select()）；legacy 路径保持 null 与既有 strict
        // select 行为逐字一致。
        verifiedSelection: ResolvedQaRules? = null
    ): List<SafetyFinding> {
        val findings = mutableListOf<SafetyFinding>()
        fun add(code: String, sentence: String? = null) {
            if (findings.none { it.code == code }) {
                findings += SafetyFinding(
                    code = code,
                    severity = if (code == AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL) {
                        SafetySeverity.STRONG
                    } else {
                        SafetySeverity.NORMAL
                    },
                    sentence = sentence
                )
            }
        }

        // I-9: 新增码走默认 NORMAL（一次确认弹窗即可，不要求输入「确认发送」）。
        degradedFactCodes.forEach { add(it) }

        if (carriesQa && canonicalFactIds.isNotEmpty()) {
            val claimValidation = aiReplyHighRiskClaimValidator.validatePlainText(
                verificationText, canonicalFactIds
            )
            if (!claimValidation.valid) {
                claimValidation.warningCodes.forEach { add(it) }
                if (claimValidation.warningCodes.isEmpty()) {
                    add("CLAIM_VALIDATION_FAILED")
                }
            }
        } else if (carriesQa && canonicalFactIds.isEmpty()) {
            add("QA_FACTS_ALL_INVALID")
        } else {
            if (aiReplyHighRiskClaimValidator.containsHallucinatedNumberOrUrl(verificationText, "")) {
                add(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HALLUCINATED_FACT)
            }
            if (aiReplyHighRiskClaimValidator.containsUnbackedHighRiskDeclarations(verificationText, "")) {
                add(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HIGH_RISK_UNBACKED)
            }
        }

        if (aiReplyHighRiskClaimValidator.containsTrustRhetoric(verificationText)) {
            add(AiReplyHighRiskClaimValidator.WARNING_CLAIM_TRUST_RHETORIC)
        }

        val selection = verifiedSelection ?: if (canonicalFactIds.isNotEmpty()) {
            qaFactSelectionService.select(inboundText, canonicalFactIds, researchProfileSufficient)
        } else {
            qaFactSelectionService.select(inboundText, null, researchProfileSufficient)
        }
        val hasBlockingTrust = aiReplyDraftService.hasBlockingTrustGapForSelection(selection.requestFacts)
        if (hasBlockingTrust &&
            aiReplyHighRiskClaimValidator.containsConfidentialitySubstitute(verificationText)
        ) {
            add(AiReplyHighRiskClaimValidator.WARNING_CLAIM_CONFIDENTIALITY_SUBSTITUTE)
        }

        for (fact in selection.requestFacts) {
            for (intent in fact.intents) {
                val isAgencyOrCompany = intent.intentKey.startsWith("agency.") ||
                    intent.intentKey.startsWith("company.")
                val isEnterprise = intent.intentKey.startsWith("enterprise.")
                if (isAgencyOrCompany || isEnterprise) {
                    val evidenceIds = intent.evidenceRuleIds
                    if (evidenceIds.isNotEmpty()) {
                        val sourceText = aiReplyHighRiskClaimValidator.resolveSourceText(evidenceIds)
                        if (sourceText != null) {
                            if (isAgencyOrCompany) {
                                if (aiReplyHighRiskClaimValidator.isRoleDisclosureRequired(sourceText) &&
                                    !aiReplyHighRiskClaimValidator.containsRoleDisclosure(verificationText)
                                ) {
                                    add(AiReplyHighRiskClaimValidator.WARNING_CLAIM_ROLE_DISCLOSURE_OMITTED)
                                }
                            }
                            if (isEnterprise) {
                                if (aiReplyHighRiskClaimValidator.isEnterpriseUncertaintyRequired(sourceText) &&
                                    (aiReplyHighRiskClaimValidator.containsEnterpriseCertainty(verificationText) ||
                                        !aiReplyHighRiskClaimValidator.containsEnterpriseUncertainty(verificationText))
                                ) {
                                    add(AiReplyHighRiskClaimValidator.WARNING_CLAIM_ENTERPRISE_UNGROUNDED)
                                }
                            }
                        }
                    }
                }
            }
        }

        val restrictedActions = AiReplyActionPolicy.restrictForTrustState(
            AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList()) + operatorAuthorizedActions,
            hasBlockingTrust
        )
        val violations = AiReplyActionPolicy.findViolations(verificationText, restrictedActions)
        violations.forEach { violation ->
            if (violation.code != null) {
                add(violation.code, violation.sentence)
            }
        }

        return findings
    }

    private fun classifyDelivery(delivered: DeliveredMail): ManualReplyDeliveryClassification {
        return when {
            delivered.status == "SENT" && delivered.errorCategory == SmtpErrorCategory.SUCCESS ->
                ManualReplyDeliveryClassification(
                    attemptStatus = MailSendAttemptStatus.SENT,
                    isSent = true, isSafeRetry = false, isUnknown = false,
                    errorSummary = null
                )
            delivered.errorCategory == SmtpErrorCategory.TRANSIENT &&
                delivered.smtpResponseCode != null &&
                delivered.smtpResponseCode in 400..499 ->
                ManualReplyDeliveryClassification(
                    attemptStatus = MailSendAttemptStatus.FAILED_SAFE_TO_RETRY,
                    isSent = false, isSafeRetry = true, isUnknown = false,
                    errorSummary = delivered.errorDetail
                )
            delivered.errorCategory == SmtpErrorCategory.PERMANENT &&
                delivered.smtpResponseCode != null &&
                delivered.smtpResponseCode in 500..599 ->
                ManualReplyDeliveryClassification(
                    attemptStatus = MailSendAttemptStatus.FAILED,
                    isSent = false, isSafeRetry = false, isUnknown = false,
                    errorSummary = delivered.errorDetail
                )
            delivered.errorCategory == SmtpErrorCategory.INFRASTRUCTURE &&
                delivered.errorDetail?.startsWith("AUTH_FAILED:") == true ->
                ManualReplyDeliveryClassification(
                    attemptStatus = MailSendAttemptStatus.FAILED_SAFE_TO_RETRY,
                    isSent = false, isSafeRetry = true, isUnknown = false,
                    errorSummary = delivered.errorDetail
                )
            else ->
                ManualReplyDeliveryClassification(
                    attemptStatus = MailSendAttemptStatus.DELIVERY_UNKNOWN,
                    isSent = false, isSafeRetry = false, isUnknown = true,
                    errorSummary = delivered.errorDetail
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

    @Transactional
    fun cancelResolved(
        inboundProcessingId: Long,
        operatorName: String?,
        note: String?
    ) {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Inbound mail processing not found: $inboundProcessingId"
                )
            }
        if (record.processStatus != "PROCESSED" ||
            record.processReason != "MANUAL_RESOLVED" ||
            record.reasonType != "MANUAL_RESOLVED"
        ) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Record $inboundProcessingId is not manually resolved and cannot be cancelled"
            )
        }

        val actualOperator = operatorName?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
        val now = LocalDateTime.now()
        val updated = inboundMailProcessingRepository.reopenManualResolved(inboundProcessingId, now)
        if (updated != 1) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Record $inboundProcessingId changed concurrently and cannot be cancelled"
            )
        }

        val contactId = record.expertContactId
        if (contactId != null) {
            expertContactRepository.findById(contactId).ifPresent { contact ->
                if (!contact.needsManualAttention) {
                    expertContactRepository.save(contact.copy(needsManualAttention = true))
                }
            }
        }

        operatorActionLogService.record(
            targetType = "INBOUND_MAIL_PROCESSING",
            targetId = inboundProcessingId,
            actionType = OperatorActionType.CANCEL_INBOUND_RESOLVED,
            expertContactId = contactId,
            inboundProcessingId = inboundProcessingId,
            before = mapOf(
                "processStatus" to record.processStatus,
                "processReason" to record.processReason,
                "reasonType" to record.reasonType,
                "resolvedBy" to record.resolvedBy,
                "resolvedAt" to record.resolvedAt
            ),
            after = mapOf(
                "processStatus" to "MANUAL_REVIEW",
                "processReason" to "MANUAL_REOPENED",
                "reasonType" to null,
                "resolvedBy" to null,
                "resolvedAt" to null
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

        val safetyFindings = collectSafetyFindings(
            verificationText = textBody,
            carriesQa = factRuleIds.isNotEmpty(),
            canonicalFactIds = canonicalFactIds,
            contact = contact,
            inboundText = inboundText,
            researchProfileSufficient = researchProfileSufficient,
            // I-8: 预检没有 assembly 入参，改从持久化快照推导；读不到即空集（fail-closed）。
            operatorAuthorizedActions = trustReplyWorkbenchService.operatorAuthorizedActions(
                TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, inboundProcessingId)
            )
        )
        safetyFindings.forEach { finding ->
            if (finding.code !in warningCodes) {
                warningCodes += finding.code
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
    val messageId: String?,
    val unsupportedAnswerArchiveStatus: UnsupportedAnswerArchiveStatus = UnsupportedAnswerArchiveStatus.NOT_APPLICABLE,
    val unsupportedAnswerArchivedCount: Int = 0,
    val unsupportedAnswerArchiveFailedCount: Int = 0
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
    val trustReplyAssembly: TrustReplyAssembleRequest? = null,
    val safetyWarningConfirmed: Boolean = false,
    val strongConfirmationText: String? = null
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

enum class SafetySeverity {
    NORMAL,
    STRONG
}

data class SafetyFinding(
    val code: String,
    val severity: SafetySeverity,
    val sentence: String?
)

class ManualSendSafetyBlockedException(
    val findings: List<SafetyFinding>
) : RuntimeException("发送内容安全校验未通过")
