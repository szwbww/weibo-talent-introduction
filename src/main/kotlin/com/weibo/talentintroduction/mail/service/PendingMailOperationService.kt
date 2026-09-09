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
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.MailRecordRagFact
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRagFactRepository
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
import com.weibo.talentintroduction.rag.service.RagKnowledgeBase
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
    private val emailSuppressionService: EmailSuppressionService,
    // 03 (T1/I-1): 会议日历校验/重建 —— 发送时用服务端解析后的真实 account/contact/
    // processing 与 01 validateAndBuild 重算（禁止默认 null 让生产依赖悄悄消失）。
    private val meetingConfirmationService: MeetingConfirmationService,
    // 03b (I-40/I-41/I-43): RAG 发送路径的协作件。可空默认 null 仅为让既有的直接
    // 构造单元测试（不触碰 RAG 分支）保持零改动；Spring 运行时按主构造器完整注入，
    // RAG 分支入口 requireNotNull 防御性校验。
    private val mailRecordRagFactRepository: MailRecordRagFactRepository? = null,
    private val ragKnowledgeBase: RagKnowledgeBase? = null
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
        strongConfirmationText: String? = null,
        // 03b (I-39~I-43): RAG 证据 —— fact_code 字符串列表 + 生成草稿时下发的语料指纹。
        // 追加在参数末尾，既有调用点零改动；与 trustReplyAssembly 互斥（I-39）。
        ragFactCodes: List<String>? = null,
        ragCorpusFingerprint: String? = null,
        // 03 (T1/I-1/I-2): 已预览会议配置与预览快照 sha256。meeting 与
        // previewAttachmentSha256 必须同时出现/同时为空（否则 400）；非空时在最终变量
        // 渲染后用真实 processing/contact/account 走 01 validateAndBuild 重算核对。
        meeting: MeetingInput? = null,
        previewAttachmentSha256: String? = null
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

        // 03 (T1/I-1): meeting 与 previewAttachmentSha256 必须同时出现或同时为空。
        // 半配置请求直接 400，绝不静默降级为无附件发送。
        if (meeting == null && previewAttachmentSha256 != null ||
            meeting != null && previewAttachmentSha256 == null
        ) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "会议附件配置不完整，请重新预览")
        }

        val inboundText = inboundMessageBody(record)
        val researchProfileSufficient = resolveResearchProfileSufficient(contact, inboundText)

        // 03b (I-39): 三条发送路径互斥 —— 旧工作台 assembly 与 RAG fact_code 证据同时出现
        // → 400 SEND_EVIDENCE_SOURCE_CONFLICT，绝不任选其一（否则 mail_record_qa_rule 与
        // mail_record_rag_fact 各写一份、互相矛盾）。判定先于下方 assembly 服务端重算。
        val ragMode = ragFactCodes != null
        if (trustReplyAssembly != null && ragMode) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "SEND_EVIDENCE_SOURCE_CONFLICT")
        }
        // 03b (I-41/I-40): RAG 发送门禁 —— 指纹缺失 → 400 RAG_FINGERPRINT_REQUIRED；
        // 与当前语料指纹不符 → 409 RAG_CORPUS_STALE（不自动重新生成、不静默放行）；
        // 每个 fact_code 必须存在且 enabled → 否则 422 RAG_FACT_CODE_UNKNOWN。
        // 校验只对 RagKnowledgeBase 快照进行（I-40/G-4），绝不读 qa_rule / legacy_rule_id。
        val ragFingerprintAtSend: String? = if (ragMode) {
            val ragKb = requireNotNull(ragKnowledgeBase) {
                "RagKnowledgeBase is not wired for RAG send"
            }
            val supplied = ragCorpusFingerprint?.takeIf { it.isNotBlank() }
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "RAG_FINGERPRINT_REQUIRED")
            if (supplied != ragKb.fingerprint()) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "RAG_CORPUS_STALE")
            }
            val enabledFactCodes = ragKb.snapshot().facts
                .asSequence()
                .filter { it.enabled }
                .map { it.factCode }
                .toHashSet()
            for (factCode in ragFactCodes.orEmpty()) {
                if (factCode !in enabledFactCodes) {
                    throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "RAG_FACT_CODE_UNKNOWN")
                }
            }
            supplied
        } else {
            null
        }

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
        // 无 assembly 的 legacy 路径保留「客户端提交过 qaRuleIds」的既有判据；
        // 03b (I-47 ①): RAG 路径按是否携带 fact_code 判定（审计据此区分动作类型），
        // 但 safety 调用点显式传 false —— RAG 不携带 QA 证据。
        val carriesQa = if (verifiedAssembly != null) {
            verifiedAssembly.response.canonicalFactIds.isNotEmpty()
        } else if (ragMode) {
            ragFactCodes.orEmpty().isNotEmpty()
        } else {
            !qaRuleIds.isNullOrEmpty()
        }
        val factResolution = if (verifiedAssembly != null) {
            // 03 (I-2/I-6): 无 degraded codes，canonical ids 原样进入 safety 与 SendPayload。
            CanonicalFactResolution(verifiedAssembly.response.canonicalFactIds, emptyList())
        } else if (ragMode) {
            // 03b (I-40): RAG 证据是 fact_code 字符串，不产生 Long ids；绝不调用
            // canonicalizeFactRuleIds / 读取 qa_rule（G-4）。
            CanonicalFactResolution(emptyList(), emptyList())
        } else if (carriesQa) {
            canonicalizeFactRuleIds(inboundText, qaRuleIds!!, researchProfileSufficient)
        } else {
            CanonicalFactResolution(emptyList(), emptyList())
        }
        val canonicalFactIds = factResolution.canonicalFactIds
        val serverSuggestedFactIds = if (ragMode) {
            // 03b (I-40): 不调用 qaFactSelectionService.select()。
            emptyList()
        } else if (carriesQa) {
            qaFactSelectionService.select(inboundText, null, researchProfileSufficient).sendQaRuleIds
        } else {
            emptyList()
        }
        // 03b (I-43): RAG 路径 matched_qa_rule_id 恒 null，绝不用 legacy_rule_id 兜底。
        val primaryRuleId = if (ragMode) null else canonicalFactIds.firstOrNull()

        val account = resolvePendingReplyAccount(senderAccountCode, record.senderAccountCode)

        val manualSource = ManualRichSendSource(
            contact = contact,
            contactId = contactId,
            inboundProcessingId = inboundProcessingId,
            inboundRecord = record,
            account = account,
            accountCode = account.accountCode,
            persistInReplyTo = record.messageId,
            smtpInReplyTo = null,
            smtpReferences = null,
            anchorMailRecordId = null,
            requestId = null
        )
        return executeManualRichSend(
            source = manualSource,
            rawSubject = subject,
            htmlBody = htmlBody,
            textBody = textBody,
            operatorName = operatorName,
            safetyWarningConfirmed = safetyWarningConfirmed,
            strongConfirmationText = strongConfirmationText,
            evidence = ManualReplyEvidenceContext(
                verifiedAssembly = verifiedAssembly,
                carriesQa = carriesQa,
                canonicalFactIds = canonicalFactIds,
                serverSuggestedFactIds = serverSuggestedFactIds,
                degradedFactCodes = factResolution.degradedCodes,
                ragMode = ragMode,
                ragFactCodes = ragFactCodes,
                ragFingerprintAtSend = ragFingerprintAtSend,
                templateTextBody = templateTextBody,
                templateHtmlBody = templateHtmlBody,
                operatorAuthorizedActions = verifiedAssembly
                    ?.let { trustReplyWorkbenchService.operatorAuthorizedActionsFromVerifiedVersions(it.response.itemVersions) }
                    .orEmpty(),
                edited = edited,
                inboundText = inboundText,
                researchProfileSufficient = researchProfileSufficient
            ),
            meeting = meeting,
            previewAttachmentSha256 = previewAttachmentSha256
        )
    }

    /**
     * 会话级自由回信入口（T2）：联系人有真实 SENT 出站锚点才开放；锚点账号即发件账号。
     * 幂等（I-4）：先按 requestId 收敛已完成 attempt，再查锚点；无成功发件在 claim/SMTP
     * 前返回 422 CONVERSATION_SENT_ANCHOR_NOT_FOUND（I-1/I-10）。DTO 不携带
     * senderAccountCode/QA/RAG —— 本方法只接收可空 accountScope 约束锚点查询（I-6）。
     */
    fun sendConversationManualRichReply(
        contactId: Long,
        requestId: String,
        accountScope: String?,
        subject: String,
        htmlBody: String,
        textBody: String?,
        operatorName: String?,
        safetyWarningConfirmed: Boolean = false,
        strongConfirmationText: String? = null
    ): PendingMailSendResult {
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }
        require(subject.isNotBlank()) { "Subject is required" }
        require(htmlBody.isNotBlank()) { "HTML body is required" }
        require(subject.trim().length <= 255) { "Subject exceeds 255 characters" }
        require(requestId.isNotBlank()) { "requestId is required" }
        val canonicalRequestId = try {
            manualReplySendAttemptService.canonicalConversationRequestId(requestId)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("requestId must be a valid UUID", ex)
        }

        // I-4：已完成 attempt 先收敛 —— SENT 时直接返回原结果，绝不重查锚点/再次投递
        // （刚发出的信已成为最新成功发件也不得再次 SMTP）。
        val completed = manualReplySendAttemptService.findCompletedByRequestId(
            contact.orcidId, canonicalRequestId
        )
        if (completed != null) {
            val record = completed.mailRecord
            return PendingMailSendResult(
                contactId = contactId,
                senderAccountCode = record.senderAccountCode ?: completed.attemptAccountCode,
                mailType = "MANUAL_RICH_REPLY",
                subject = record.subject ?: subject,
                sendStatus = "SENT",
                messageId = record.messageId ?: completed.attemptMessageId
            )
        }

        // I-1：真实 SENT 出站锚点（排除空账号/模拟器）；accountScope 非空时只在该账号内找，
        // scope 下无成功发件不回退其他账号（I-6）。锚点查询在任何 claim/SMTP 之前。
        val anchor = mailRecordRepository.findLatestSentOutboundAnchor(
            contactId = contactId,
            accountScope = accountScope?.takeIf { it.isNotBlank() },
            excludedAccountCode = MailSenderAccountService.SIMULATOR_ACCOUNT_CODE
        ) ?: throw ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "CONVERSATION_SENT_ANCHOR_NOT_FOUND"
        )
        val accountCode = requireNotNull(anchor.senderAccountCode) {
            "Conversation anchor mail record has no sender account"
        }
        val account = mailSenderAccountService.getManualSendAccount(accountCode)

        // I-7：Message-ID 仅在 trim 后非空且 <=255 时进入落库 inReplyTo 与 SMTP 线程头；
        // 缺失/超长不取消资格 —— 线程头为空仍发送（I-3：引用链只来自真实锚点）。
        val anchorMessageId = anchor.messageId
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 255 }
        val smtpReferences = if (anchorMessageId != null) {
            listOfNotNull(anchor.inReplyTo?.trim()?.takeIf { it.isNotBlank() }, anchorMessageId)
                .joinToString(" ")
        } else null

        val source = ManualRichSendSource(
            contact = contact,
            contactId = contactId,
            inboundProcessingId = null,
            inboundRecord = null,
            account = account,
            accountCode = accountCode,
            persistInReplyTo = anchorMessageId,
            smtpInReplyTo = anchorMessageId,
            smtpReferences = smtpReferences,
            anchorMailRecordId = requireNotNull(anchor.id) { "Conversation anchor mail record has no id" },
            requestId = canonicalRequestId
        )
        return executeManualRichSend(
            source = source,
            rawSubject = subject,
            htmlBody = htmlBody,
            textBody = textBody,
            operatorName = operatorName,
            safetyWarningConfirmed = safetyWarningConfirmed,
            strongConfirmationText = strongConfirmationText,
            evidence = ManualReplyEvidenceContext(runInboundSemanticChecks = false)
        )
    }

    /**
     * 人工富文本发送共同实现（T2.4）：两条路径共享「占位符校验 → 最终渲染 → 最终文本
     * 安全检查 → suppression → claim → SMTP → 分类 → 持久化 → after-commit audit」
     * 生命周期（I-9）。来信路径（source.inboundProcessingId != null）保留 QA/RAG/
     * assembly/archive/旧审计语义（I-8）；会话回信路径（null）只跑通用纯文本风险检查，
     * 不运行依赖来信语义的 QA/意图检查，审计 target 为联系人（I-11）。
     */
    private fun executeManualRichSend(
        source: ManualRichSendSource,
        rawSubject: String,
        htmlBody: String,
        textBody: String?,
        operatorName: String?,
        safetyWarningConfirmed: Boolean,
        strongConfirmationText: String?,
        evidence: ManualReplyEvidenceContext,
        // 03 (T1/I-1/I-2): 已预览会议配置与预览快照 sha256（仅来信路径携带；会话回信
        // 路径不传，保持默认 null）。非空时在最终变量渲染后用真实 processing/contact/
        // account 走 01 validateAndBuild 重算核对，两者必须同时出现/同时为空。
        meeting: MeetingInput? = null,
        previewAttachmentSha256: String? = null
    ): PendingMailSendResult {
        val contact = source.contact
        require(rawSubject.isNotBlank()) { "Subject is required" }
        require(htmlBody.isNotBlank()) { "HTML body is required" }
        val trimmedSubject = rawSubject.trim()
        require(trimmedSubject.length <= 255) { "Subject exceeds 255 characters" }

        mailVariableService.requireValidPlaceholders(trimmedSubject)
        val renderedSubject = mailVariableService.renderForContact(trimmedSubject, source.account, contact)
        require(renderedSubject.isNotBlank()) { "Rendered subject is empty" }
        require(renderedSubject.length <= 255) { "Rendered subject exceeds 255 characters: ${renderedSubject.length}" }

        val rawText = evidence.templateTextBody?.takeIf { it.isNotBlank() }
            ?: textBody?.takeIf { it.isNotBlank() }
            ?: mailBodyCleaner.clean(htmlBody)
        val rawHtmlFromTemplate = evidence.templateHtmlBody?.takeIf { it.isNotBlank() }
        mailVariableService.requireValidPlaceholders(rawText)
        if (rawHtmlFromTemplate != null) {
            mailVariableService.requireValidPlaceholders(rawHtmlFromTemplate)
        } else if (evidence.templateTextBody.isNullOrBlank()) {
            mailVariableService.requireValidPlaceholders(htmlBody)
        }

        val renderedText = mailVariableService.renderForContact(rawText, source.account, contact)
        val finalTextBody = renderedText
        val finalHtmlBody = when {
            rawHtmlFromTemplate != null ->
                mailVariableService.renderHtmlForContact(rawHtmlFromTemplate, source.account, contact)
            !evidence.templateTextBody.isNullOrBlank() ->
                mailContentService.plainTextToHtml(renderedText)
            else ->
                mailVariableService.renderHtmlForContact(htmlBody, source.account, contact)
        }

        val finalValidationText = buildFinalValidationText(renderedSubject, finalTextBody, finalHtmlBody)
        require(finalValidationText.isNotBlank()) { "Final validation text is empty after rendering" }
        require(finalValidationText.length <= 20000) {
            "Final validation text exceeds 20,000 characters (${finalValidationText.length})"
        }
        mailVariableService.requireValidPlaceholders(finalTextBody)
        mailVariableService.requireValidPlaceholders(finalHtmlBody)

        // 03 (T1/I-1/I-2): 会议附件重建与正文核对 —— 位于最终变量渲染之后、Safety/claim/
        // SMTP 之前。会议仅由来信路径产生（meeting 来自 unmatched-inbound 01 预览，
        // 会话回信路径恒不携带）；用服务端解析后的真实 account/recipient/contact 与
        // processing 调用 01 validateAndBuild：digest 不一致 400（配置已变化）；模板禁用
        // 沿 01 拒绝；01 生成的完整会议正文必须连续存在于最终 text 与
        // htmlToPlainText(finalHtml) 的规范化文本（不是只检索日期/链接关键词），否则 400。
        // 最终正文仍取人工编辑器。
        val calendarSnapshot: CalendarAttachmentSnapshot? = if (meeting != null) {
            require(source.inboundProcessingId != null) {
                "Meeting attachment requires an inbound processing context"
            }
            val rebuilt = try {
                meetingConfirmationService.validateAndBuild(
                    processingId = source.inboundProcessingId,
                    contact = source.contact,
                    account = source.account,
                    input = meeting
                )
            } catch (ex: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message ?: "会议配置无效")
            }
            if (rebuilt.attachment.sha256 != previewAttachmentSha256) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "会议配置已变化，请重新预览")
            }
            val expected = normalizeBodyText(rebuilt.textBody)
            val finalTextNormalized = normalizeBodyText(finalTextBody)
            val finalHtmlPlainNormalized = normalizeBodyText(mailContentService.htmlToPlainText(finalHtmlBody))
            if (!finalTextNormalized.contains(expected) || !finalHtmlPlainNormalized.contains(expected)) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "会议正文与附件不一致，请编辑会议后重新生成，或移除日历附件"
                )
            }
            // 01 返回的同一份附件数据只构造一次快照实例，SendPayload 与 ComposedMail 共用。
            CalendarAttachmentSnapshot(
                schemaVersion = MeetingConfirmationDomain.CALENDAR_SCHEMA_VERSION,
                filename = rebuilt.attachment.filename,
                contentType = rebuilt.attachment.contentType,
                icsText = rebuilt.attachment.icsText,
                sha256 = rebuilt.attachment.sha256,
                semanticSha256 = rebuilt.attachment.semanticSha256
            )
        } else {
            null
        }
        val findings = collectSafetyFindings(
            verificationText = finalValidationText,
            // 03b (I-47 ①): RAG 路径 carriesQa 显式传 false（RAG 不携带 QA 证据）。
            carriesQa = if (evidence.ragMode) false else evidence.carriesQa,
            canonicalFactIds = evidence.canonicalFactIds,
            contact = contact,
            inboundText = evidence.inboundText,
            researchProfileSufficient = evidence.researchProfileSufficient,
            operatorAuthorizedActions = evidence.operatorAuthorizedActions,
            degradedFactCodes = evidence.degradedFactCodes,
            // 03 (I-4): 可信 assembly 路径复用服务端已验证 selection，禁止再次语义重筛。
            verifiedSelection = evidence.verifiedAssembly?.selection,
            // 03b (I-47): RAG 发送整段绕开 QA selection/trust-gap/intent 链。
            ragSend = evidence.ragMode,
            // T2.7: 会话回信路径在通用纯文本检查后返回，不跑来信语义检查。
            runInboundSemanticChecks = evidence.runInboundSemanticChecks
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
            contactId = source.contactId,
            inboundProcessingId = source.inboundProcessingId,
            accountCode = source.accountCode,
            normalizedRecipient = contact.expertEmail.lowercase().trim(),
            subject = renderedSubject,
            finalText = finalTextBody,
            finalHtml = finalHtmlBody,
            inReplyTo = source.persistInReplyTo,
            canonicalQaRuleIds = evidence.canonicalFactIds,
            primaryRuleId = if (evidence.ragMode) null else evidence.canonicalFactIds.firstOrNull(),
            // I-3：会话回信锚点只以真实 mail_record.id 表达，绝不伪造 inbound id。
            sourceAnchor = if (source.inboundProcessingId == null) {
                requireNotNull(source.anchorMailRecordId) {
                    "Conversation rich reply requires a real anchor mail record"
                }.let { "${ManualReplySendAttemptService.SOURCE_ANCHOR_PREFIX}$it" }
            } else null,
            idempotencyRequestId = source.requestId,
            // 03 (I-3): 与 ComposedMail 共用同一 01 快照实例，绝不独立生成第二份。
            calendarAttachment = calendarSnapshot
        )

        val persistInReplyTo = source.persistInReplyTo
        if (persistInReplyTo != null && persistInReplyTo.length > 255) {
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
                    messageId = claim.messageId,
                    // 03 (I-2/I-3): 带会议日历的新分支用真实来信 processing.messageId 作
                    // SMTP 线程头（inReplyTo/references 同一来源）；会议只由来信路径产生，
                    // 无会议时回落该路径原形态（来信 SMTP 头恒 null；会话回信 = 锚点头），
                    // 不因本合并顺带改变旧邮件线程形态。
                    inReplyTo = if (calendarSnapshot != null) source.inboundRecord?.messageId
                        else source.smtpInReplyTo,
                    references = if (calendarSnapshot != null) source.inboundRecord?.messageId
                        else source.smtpReferences,
                    calendarAttachment = calendarSnapshot
                )
                val bodyPreviewText = (finalTextBody.ifBlank { mailBodyCleaner.clean(finalHtmlBody) }
                    .takeIf { it.isNotBlank() } ?: mailBodyCleaner.clean(finalHtmlBody)).take(500)

                try {
                    val delivered = mailDeliveryService.send(source.account, mail)
                    val classification = classifyDelivery(delivered)

                    if (classification.isSent) {
                        val mailRecordId = try {
                            val id = manualReplySendAttemptService.finalizeSuccess(
                                payload = payload,
                                attemptId = claim.attemptId,
                                messageId = claim.messageId
                            )
                            if (source.inboundProcessingId != null) {
                                manualReplySendAttemptService.recordSendAudit(
                                    inboundProcessingId = source.inboundProcessingId,
                                    contactId = source.contactId,
                                    mailRecordId = id,
                                    canonicalFactIds = evidence.canonicalFactIds,
                                    carriesQa = evidence.carriesQa,
                                    delivered = delivered,
                                    sendSubject = renderedSubject,
                                    bodyPreviewText = bodyPreviewText,
                                    operatorName = operatorName,
                                    inboundRecord = requireNotNull(source.inboundRecord) {
                                        "Inbound rich reply audit requires the inbound processing record"
                                    },
                                    serverSuggestedFactIds = evidence.serverSuggestedFactIds,
                                    edited = evidence.edited,
                                    // 04 (I-1/I-7): 仅在该次发送存在 verified assembly 时附加诊断。
                                    trustReplyDiagnostics = evidence.verifiedAssembly?.response?.diagnostics,
                                    note = auditNote(inboundProcessingId = source.inboundProcessingId, contactId = source.contactId, findings = findings, requiresStrong = requiresStrong)
                                )
                            } else {
                                manualReplySendAttemptService.recordConversationSendAudit(
                                    contactId = source.contactId,
                                    anchorMailRecordId = requireNotNull(source.anchorMailRecordId) {
                                        "Conversation rich reply audit requires the anchor mail record"
                                    },
                                    mailRecordId = id,
                                    delivered = delivered,
                                    sendSubject = renderedSubject,
                                    bodyPreviewText = bodyPreviewText,
                                    operatorName = operatorName,
                                    note = auditNote(inboundProcessingId = null, contactId = source.contactId, findings = findings, requiresStrong = requiresStrong)
                                )
                            }
                            // 03b (I-42): 发送成功后按请求中 ragFactCodes 的原始顺序写入
                            // mail_record_rag_fact 存证；RAG 路径 canonicalFactIds 恒为空，
                            // 绝不写 mail_record_qa_rule。
                            if (evidence.ragFactCodes != null) {
                                val ragEvidenceRepo = requireNotNull(mailRecordRagFactRepository) {
                                    "MailRecordRagFactRepository is not wired for RAG send"
                                }
                                val fingerprintAtSend = requireNotNull(evidence.ragFingerprintAtSend) {
                                    "RAG fingerprint must be present after gate validation"
                                }
                                ragEvidenceRepo.saveAll(
                                    evidence.ragFactCodes.mapIndexed { ordinal, factCode ->
                                        MailRecordRagFact(
                                            mailRecordId = id,
                                            factCode = factCode,
                                            ordinal = ordinal,
                                            corpusFingerprint = fingerprintAtSend
                                        )
                                    }
                                )
                            }
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
                        // 归档只属来信语义链（verifiedAssembly 非空才可能产生样本）；会话回信
                        // 路径 verifiedAssembly 恒 null，直接返回默认 NOT_APPLICABLE（I-8/I-11）。
                        val archive = if (source.inboundProcessingId != null) {
                            archiveLiveUnsupportedAnswers(
                                inboundProcessingId = source.inboundProcessingId,
                                templateTextBody = evidence.templateTextBody,
                                finalTextBody = finalTextBody,
                                operatorName = operatorName,
                                outboundMailRecordId = mailRecordId,
                                verifiedAssembly = evidence.verifiedAssembly
                            )
                        } else {
                            UnsupportedAnswerIndexArchiveResult()
                        }
                        PendingMailSendResult(
                            contactId = source.contactId,
                            senderAccountCode = source.accountCode,
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
                val archive = if (source.inboundProcessingId != null) {
                    archiveLiveUnsupportedAnswers(
                        inboundProcessingId = source.inboundProcessingId,
                        templateTextBody = evidence.templateTextBody,
                        finalTextBody = finalTextBody,
                        operatorName = operatorName,
                        outboundMailRecordId = existingRecord?.id,
                        verifiedAssembly = evidence.verifiedAssembly
                    )
                } else {
                    UnsupportedAnswerIndexArchiveResult()
                }
                PendingMailSendResult(
                    contactId = source.contactId,
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

    private fun auditNote(
        inboundProcessingId: Long?,
        contactId: Long,
        findings: List<SafetyFinding>,
        requiresStrong: Boolean
    ): String = buildString {
        if (inboundProcessingId != null) {
            append("Manual rich reply sent for inbound processing $inboundProcessingId")
        } else {
            append("Manual rich reply sent for conversation of contact $contactId")
        }
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
            if (templateTextBody.isNullOrBlank()) {
                return UnsupportedAnswerIndexArchiveResult()
            }
            // c6 (T-2 / I-4): 去掉「正文一字未改才归档」门槛——运营编辑过正文的样本
            // 同样照常归档（由 service 在文档上置 editedByOperator = true，A-2）。
            // Repair R-2 (V-2)：资格判定统一走 service 的权威 isArchiveEligible
            // （四种 handling × 两种 generationKind，operatorInstruction 可选）。
            val eligibleVersions = assembled.itemVersions.filter { version ->
                unsupportedAnswerIndexService.isArchiveEligible(version)
            }
            if (eligibleVersions.isEmpty()) {
                return UnsupportedAnswerIndexArchiveResult()
            }
            // 归档失败不得阻断主流程：source 解析异常（含 null 返回）按归档失败处理，
            // 绝不把异常抛出到发送主流程（A-7 回归语义）。
            val resolved = requireNotNull(trustReplyWorkbenchService.resolveSource(assembled.source)) {
                "Unsupported answer archive source could not be resolved for inbound $inboundProcessingId"
            }
            unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
                source = resolved,
                versions = eligibleVersions,
                qualificationId = outboundMailRecordId.toString(),
                approvedBy = normalizeArchiveOperatorName(operatorName),
                createdAt = Instant.now(),
                // Repair R-1 (V-3)：逐条确定性映射 → finalParagraphText（步骤 03 权威段落）。
                finalParagraphs = assembled.finalParagraphByRequestKey
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

    // Repair R-2 (V-2)：失败归档计数对齐权威资格判定（旧口径只数
    // ANSWER_FROM_OPERATOR_INPUT，与放宽后的允许集合不一致）。
    private fun eligibleFailureCount(verifiedAssembly: VerifiedTrustReplyAssembly): Int =
        verifiedAssembly.response.itemVersions.count { version ->
            unsupportedAnswerIndexService.isArchiveEligible(version)
        }.coerceAtLeast(1)

    // Repair R-2 (V-2)：线上侧资格判定统一委托 service 的权威 isArchiveEligible
    // （四种 handling × 两种 generationKind，operatorInstruction 可选）；旧窄化过滤
    // （仅 ANSWER_FROM_OPERATOR_INPUT × AI_GENERATED + 必填说明）已废弃。
    private fun isArchiveEligibleOperatorDirectedVersion(version: TrustReplyItemVersion): Boolean =
        unsupportedAnswerIndexService.isArchiveEligible(version)

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

    /** 03 (I-1)：会议正文一致性核对用的规范化 —— CRLF→LF、NBSP→space、
     *  所有连续空白→单空格、trim；expected/finalText/htmlToPlainText(finalHtml) 同款。 */
    private fun normalizeBodyText(value: String): String =
        value.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

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
        verifiedSelection: ResolvedQaRules? = null,
        // 03b (I-47): RAG 发送时整段绕开 QA selection/trust-gap/intent 链（见方法体
        // 守卫处注释）。既有两条路径不传（默认 false），逻辑逐字不变。
        ragSend: Boolean = false,
        // T2.7: 会话（无来信锚点）回信路径传 false —— 数字/URL、高风险声明、信任话术
        // 通用检查跑完后立即返回，不跑依赖来信语义的 QA selection/trust-gap/intent 门禁。
        // 来信路径默认 true，行为逐字不变。
        runInboundSemanticChecks: Boolean = true
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

        // 03b (I-47): RAG 发送整段短路 —— 下面的 selection 求值、
        // hasBlockingTrustGapForSelection、intent 遍历与 AiReplyActionPolicy 门禁
        // 全部依赖 qa_rule（I-40/G-4），RAG 路径不适用（D-15）。上面的纯文本检查
        // （hallucinated number/url、unbacked high-risk、trust rhetoric）已执行完毕
        // 并保留 —— 它们不读 qa_rule，且是二次确认弹窗（safetyWarningConfirmed /
        // strongConfirmationText）的触发源（D-1：人是唯一的门）。
        if (ragSend || !runInboundSemanticChecks) {
            return findings
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

// ---------------------------------------------------------------------------
// 人工富文本发送共同实现的最小 source/evidence 上下文（T2.4）。私有文件级类型，
// 只被 PendingMailOperationService 使用。
// ---------------------------------------------------------------------------

/**
 * 发送生命周期最小 source context：联系人、可空 inbound id、可空锚点 mail record id、
 * 账号 code、落库 inReplyTo、SMTP inReplyTo/references（T2.4）。会话回信路径
 * inboundProcessingId = null 且携带真实 requestId/anchor；来信路径反向（I-3/I-4）。
 */
private data class ManualRichSendSource(
    val contact: ExpertContact,
    val contactId: Long,
    val inboundProcessingId: Long?,
    val inboundRecord: com.weibo.talentintroduction.mail.domain.InboundMailProcessing?,
    val account: MailSenderAccount,
    val accountCode: String,
    /** 落库 mail_record.inReplyTo 与 SendPayload.inReplyTo 的值（线程锚点）。 */
    val persistInReplyTo: String?,
    /** SMTP In-Reply-To 头；来信路径恒 null（I-8 保持现状），会话路径 = 锚点 Message-ID。 */
    val smtpInReplyTo: String?,
    /** SMTP References 头；会话路径 = 锚点自身 inReplyTo + 锚点 Message-ID（I-7）。 */
    val smtpReferences: String?,
    /** 会话回信审计 before 的锚点 mail record id（I-11）；来信路径恒 null。 */
    val anchorMailRecordId: Long?,
    /** 会话回信幂等 requestId（已规范化 UUID）；来信路径恒 null（I-4）。 */
    val requestId: String?
)

/**
 * 来信语义侧的可选证据/归档上下文：QA/RAG/assembly/archive 与模板正文只属于来信路径；
 * 会话回信路径用全默认值（canonical 空、不归档、不跑来信语义检查）。
 */
private data class ManualReplyEvidenceContext(
    val verifiedAssembly: VerifiedTrustReplyAssembly? = null,
    val carriesQa: Boolean = false,
    val canonicalFactIds: List<Long> = emptyList(),
    val serverSuggestedFactIds: List<Long> = emptyList(),
    val degradedFactCodes: List<String> = emptyList(),
    val ragMode: Boolean = false,
    val ragFactCodes: List<String>? = null,
    val ragFingerprintAtSend: String? = null,
    val templateTextBody: String? = null,
    val templateHtmlBody: String? = null,
    val operatorAuthorizedActions: Set<AiReplyAction> = emptySet(),
    val edited: Boolean? = null,
    val inboundText: String = "",
    val researchProfileSufficient: Boolean = false,
    /** T2.7：false 时只跑数字/URL、高风险声明、信任话术通用检查后返回。来信默认 true。 */
    val runInboundSemanticChecks: Boolean = true
)

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
    // 03b (I-39~I-43): RAG 证据字段 —— 与 trustReplyAssembly 互斥（服务端 400
    // SEND_EVIDENCE_SOURCE_CONFLICT）；既有 qaRuleIds 字段与类型不动。
    val ragFactCodes: List<String>? = null,
    val ragCorpusFingerprint: String? = null,
    val safetyWarningConfirmed: Boolean = false,
    val strongConfirmationText: String? = null,
    // 03 (T1/I-1): 已预览会议配置 + 预览快照 sha256；服务端要求两者同时出现/同时为空。
    val meeting: MeetingInput? = null,
    val previewAttachmentSha256: String? = null
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
