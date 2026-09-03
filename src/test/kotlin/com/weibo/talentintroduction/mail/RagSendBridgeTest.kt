package com.weibo.talentintroduction.mail

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.llm.service.AiReplyContext
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyHighRiskClaimValidator
import com.weibo.talentintroduction.llm.service.QaFactSelectionService
import com.weibo.talentintroduction.llm.service.ResolvedQaRules
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleRequest
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleResponse
import com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplyItemVersion
import com.weibo.talentintroduction.llm.service.TrustReplyLockedItemRequest
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.llm.service.TrustReplySourceType
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchService
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexArchiveResult
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexService
import com.weibo.talentintroduction.llm.service.VerifiedTrustReplyAssembly
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecordRagFact
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRagFactRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.ManualReplySendAttemptService
import com.weibo.talentintroduction.mail.service.ManualSendSafetyBlockedException
import com.weibo.talentintroduction.mail.service.MailBodyCleaner
import com.weibo.talentintroduction.mail.service.MailContentService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.rag.domain.RagFact
import com.weibo.talentintroduction.rag.service.RagCorpusSnapshot
import com.weibo.talentintroduction.rag.service.RagKnowledgeBase
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import com.weibo.talentintroduction.variant.service.ContentVariantService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.Optional

/**
 * 03b (T6): RAG 草稿采用 → 人工发送桥接 —— 第三条发送路径（fact_code + 语料指纹）。
 *
 * 覆盖验收：
 * - I-39: assembly 与 ragFactCodes 同时出现 → 400 SEND_EVIDENCE_SOURCE_CONFLICT；
 *   RAG 成功发送后 mail_record_qa_rule 零新增、零交互。
 * - I-40: RAG 分支只对 RagKnowledgeBase 快照校验 fact_code；qaRuleRepository 与
 *   qaFactSelectionService 零交互（canonicalizeFactRuleIds 不在 RAG 分支内执行）。
 * - I-41: 指纹缺失 → 400 RAG_FINGERPRINT_REQUIRED；指纹不符 → 409 RAG_CORPUS_STALE，
 *   且未产生任何发送尝试。
 * - I-42: 存证行按请求顺序落库，重复 code 不去重、不排序。
 * - I-43: mail_record.matched_qa_rule_id 置 null（SendPayload.primaryRuleId = null）。
 * - I-47: RAG 发送的 findings 不含 QA_FACTS_ALL_INVALID；qaFactSelectionService /
 *   aiReplyDraftService 零交互；纯文本检查（虚构数字）仍触发
 *   AI_REPLY_CLAIM_HALLUCINATED_FACT 并要求二次确认。
 * - 回归: 旧 assembly 路径与 legacy qaRuleIds 路径冒烟（行为未变），且都不写
 *   mail_record_rag_fact。
 *
 * 全部为 mocked 单元测试 —— 不依赖 docker / 真实 LLM / 网络，plain 套件全绿。
 */
class RagSendBridgeTest {
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)
    private val expertIndexLevelOperationService = Mockito.mock(ExpertIndexLevelOperationService::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailDeliveryService = Mockito.mock(com.weibo.talentintroduction.mail.service.MailDeliveryService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailRecordQaRuleRepository = Mockito.mock(MailRecordQaRuleRepository::class.java)
    private val mailRecordRagFactRepository = Mockito.mock(MailRecordRagFactRepository::class.java)
    private val operatorActionLogService = Mockito.mock(com.weibo.talentintroduction.audit.service.OperatorActionLogService::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val qaCategoryRepository = Mockito.mock(QaCategoryRepository::class.java)
    private val qaFactSelectionService = Mockito.mock(QaFactSelectionService::class.java)
    private val aiReplyDraftService = Mockito.mock(AiReplyDraftService::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val manualReplySendAttemptService = Mockito.mock(ManualReplySendAttemptService::class.java)
    private val trustReplyWorkbenchService = Mockito.mock(TrustReplyWorkbenchService::class.java)
    private val unsupportedAnswerIndexService = Mockito.mock(UnsupportedAnswerIndexService::class.java)
    private val emailSuppressionService = Mockito.mock(com.weibo.talentintroduction.mail.service.EmailSuppressionService::class.java)
    private val ragKnowledgeBase = Mockito.mock(RagKnowledgeBase::class.java)

    private val renderTemplateService = MailComposeTemplateService(
        Mockito.mock(MailComposeTemplateRepository::class.java),
        Mockito.mock(MailComposeTemplateBlockRepository::class.java),
        qaRuleRepository,
        Mockito.mock(ReplySnippetRepository::class.java),
        ObjectMapper(),
        Mockito.mock(MailVariableService::class.java),
        expertContactRepository,
        mailSenderAccountService,
        ContentVariantService(
            Mockito.mock(ContentVariantRepository::class.java),
            com.weibo.talentintroduction.mail.service.MailPlaceholderService()
        )
    )
    private val mailVariableService = MailVariableService(expertSearchService, renderTemplateService)

    private val service = PendingMailOperationService(
        inboundMailProcessingRepository,
        expertContactRepository,
        expertOperatorStatusService,
        expertIndexLevelOperationService,
        mailSenderAccountService,
        mailDeliveryService,
        mailRecordRepository,
        mailRecordQaRuleRepository,
        operatorActionLogService,
        qaRuleRepository,
        qaCategoryRepository,
        qaFactSelectionService,
        aiReplyDraftService,
        aiReplyContextService,
        AiReplyHighRiskClaimValidator(qaRuleRepository),
        MailBodyCleaner(),
        MailContentService(),
        mailVariableService,
        manualReplySendAttemptService,
        trustReplyWorkbenchService,
        unsupportedAnswerIndexService,
        emailSuppressionService,
        mailRecordRagFactRepository = mailRecordRagFactRepository,
        ragKnowledgeBase = ragKnowledgeBase
    )

    private val contact = ExpertContact(
        id = 1,
        campaignId = 1,
        orcidId = "orcid-1",
        expertEmail = "expert@test.com",
        expertName = "Expert",
        currentStatus = "INTRO_SENT",
        operatorStatus = "CONTACTED",
        currentIndexLevel = "CANDIDATE"
    )

    private val fingerprint = "e62421a42c432cf3"

    private fun ragFact(code: String, enabled: Boolean = true) = RagFact(
        factCode = code,
        area = code.substringAfter("-").substringBefore("-"),
        seq = code.takeLast(3).toInt(),
        title = "Title $code",
        category = "C",
        questionVariants = "",
        keywords = "",
        answer = "Answer body for $code.",
        coverageKeys = "",
        replyPolicy = "AUTO",
        status = "APPROVED",
        riskLevel = "LOW",
        renderMode = "COMPOSE",
        sourceRefs = "",
        legacyRuleId = null,
        enabled = enabled,
        sortOrder = 1
    )

    private fun inbound() = InboundMailProcessing(
        id = 100L,
        senderAccountCode = "sender-1",
        imapUid = 1L,
        messageId = "in-1",
        fromEmail = "expert@test.com",
        subject = "Question",
        body = "Can I work remotely?",
        cleanedBody = "Can I work remotely?",
        receivedAt = LocalDateTime.now(),
        processStatus = "MANUAL_REVIEW",
        processReason = "QA_NO_MATCH",
        expertContactId = 1L
    )

    private fun senderAccount() = MailSenderAccount(
        accountCode = "sender-1",
        senderEmail = "sender@test.com",
        senderName = "Sender",
        senderTitle = "Title",
        senderDisplayName = "Sender",
        teamName = "Team",
        countryName = "CN",
        smtpHost = "smtp.test.com",
        smtpPort = 465,
        smtpUsername = "u",
        smtpPassword = "p",
        imapHost = "imap.test.com",
        imapPort = 993,
        imapUsername = "u",
        imapPassword = "p",
        enabled = true
    )

    private fun qaRule(id: Long) = QaRule(
        id = id,
        categoryId = 1,
        keywords = "remote",
        replyBody = "legacy",
        answerBody = "Remote work is possible.",
        replySubject = null,
        replyPolicy = QaReplyPolicy.AUTO.name,
        enabled = true
    )

    private fun sendPayload() = ManualReplySendAttemptService.SendPayload(
        orcidId = contact.orcidId,
        contactId = requireNotNull(contact.id),
        inboundProcessingId = 100L,
        accountCode = "sender-1",
        normalizedRecipient = contact.expertEmail,
        subject = "Re: Test",
        finalText = "Remote work is possible.",
        finalHtml = "<p>Remote work is possible.</p>",
        inReplyTo = "in-1",
        canonicalQaRuleIds = emptyList(),
        primaryRuleId = null
    )

    private fun composedMail() = com.weibo.talentintroduction.mail.service.ComposedMail(
        to = contact.expertEmail,
        subject = "Re: Test",
        body = "<p>Remote work is possible.</p>",
        html = true,
        text = "Remote work is possible.",
        messageId = "<manual-rich-abc@weibo.com>"
    )

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun claim() = ManualReplySendAttemptService.ClaimedAttempt(
        attemptId = 1L,
        messageId = "<manual-rich-abc@weibo.com>",
        result = ManualReplySendAttemptService.ClaimResult.CLAIMED
    )

    @BeforeEach
    fun setUp() {
        Mockito.`when`(inboundMailProcessingRepository.findById(100L)).thenReturn(Optional.of(inbound()))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Can I work remotely?", ""))
            .thenReturn(
                AiReplyContext(
                    profileText = "",
                    mailHistory = "",
                    contextWarnings = emptyList(),
                    researchProfileSufficient = true
                )
            )
        Mockito.`when`(qaCategoryRepository.findAll()).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.findByOrcidId(contact.orcidId, ExpertIndexLevel.CANDIDATE))
            .thenReturn(
                ExpertProfile(
                    orcidId = contact.orcidId,
                    email = contact.expertEmail,
                    givenNames = null,
                    familyNames = "Expert",
                    country = null,
                    keyword = null,
                    employment = null
                )
            )
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender-1")).thenReturn(senderAccount())
        // I-41: 快照指纹 + 快照事实集（enabled 门禁）。KB-NOPE-001 存在但 disabled。
        Mockito.`when`(ragKnowledgeBase.fingerprint()).thenReturn(fingerprint)
        val facts = listOf(
            ragFact("KB-FUND-033"),
            ragFact("KB-COMP-007"),
            ragFact("KB-PROG-002"),
            ragFact("KB-NOPE-001", enabled = false)
        )
        Mockito.`when`(ragKnowledgeBase.snapshot()).thenReturn(
            RagCorpusSnapshot(
                facts = facts,
                phraseGroups = emptyList(),
                intentCoverage = emptyList(),
                mandatoryRules = emptyList(),
                exclusions = emptyList(),
                fingerprint = fingerprint
            )
        )
    }

    /** 标准成功发送桩：claim → SMTP SENT → finalizeSuccess 500 → 捕获 payload。 */
    private fun stubSuccessfulSend(): MutableList<ManualReplySendAttemptService.SendPayload> {
        val capturedPayloads = mutableListOf<ManualReplySendAttemptService.SendPayload>()
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim())
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(com.weibo.talentintroduction.mail.service.DeliveredMail(
            messageId = "<manual-rich-abc@weibo.com>", status = "SENT"
        ))
        Mockito.doAnswer { inv ->
            capturedPayloads += inv.getArgument<ManualReplySendAttemptService.SendPayload>(0)
            500L
        }.`when`(manualReplySendAttemptService)
            .finalizeSuccess(anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>"))
        // Mockito.any() 返回 null（platform），直传 Kotlin 非空形参会触发
        // "any(...) must not be null"；用 anyValue 包一层（与既有测试同款技巧）。
        Mockito.`when`(unsupportedAnswerIndexService.isArchiveEligible(anyValue(operatorDirectedVersion())))
            .thenReturn(false)
        return capturedPayloads
    }

    // ------------------------------------------------------------------
    // I-39: 互斥
    // ------------------------------------------------------------------

    @Test
    fun `assembly plus ragFactCodes conflicts with 400 SEND_EVIDENCE_SOURCE_CONFLICT and no send side effect`() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = "sender-1",
                subject = "Re: Test",
                htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.",
                operatorName = "op",
                trustReplyAssembly = liveAssembly(),
                ragFactCodes = listOf("KB-FUND-033"),
                ragCorpusFingerprint = fingerprint
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
        assertEquals("SEND_EVIDENCE_SOURCE_CONFLICT", ex.reason)
        Mockito.verifyNoInteractions(mailDeliveryService)
        Mockito.verifyNoInteractions(manualReplySendAttemptService)
        Mockito.verifyNoInteractions(qaFactSelectionService)
        Mockito.verifyNoInteractions(mailRecordRagFactRepository)
        Mockito.verifyNoInteractions(mailRecordQaRuleRepository)
        // I-39: 冲突在 assembly 服务端重算之前判定 —— verifyAssembly 不被调用。
        Mockito.verifyNoInteractions(trustReplyWorkbenchService)
    }

    // ------------------------------------------------------------------
    // I-41: 指纹门禁
    // ------------------------------------------------------------------

    @Test
    fun `rag send without fingerprint fails 400 RAG_FINGERPRINT_REQUIRED without any attempt`() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = "sender-1",
                subject = "Re: Test",
                htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.",
                operatorName = "op",
                ragFactCodes = listOf("KB-FUND-033"),
                ragCorpusFingerprint = null
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
        assertEquals("RAG_FINGERPRINT_REQUIRED", ex.reason)
        Mockito.verifyNoInteractions(mailDeliveryService)
        Mockito.verifyNoInteractions(manualReplySendAttemptService)
        Mockito.verifyNoInteractions(mailRecordRagFactRepository)
    }

    @Test
    fun `rag send with stale fingerprint fails 409 RAG_CORPUS_STALE without any attempt`() {
        Mockito.`when`(ragKnowledgeBase.fingerprint()).thenReturn("other-fingerprint")
        val ex = assertThrows(ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = "sender-1",
                subject = "Re: Test",
                htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.",
                operatorName = "op",
                ragFactCodes = listOf("KB-FUND-033"),
                ragCorpusFingerprint = fingerprint
            )
        }
        assertEquals(HttpStatus.CONFLICT, ex.status)
        assertEquals("RAG_CORPUS_STALE", ex.reason)
        Mockito.verifyNoInteractions(mailDeliveryService)
        Mockito.verifyNoInteractions(manualReplySendAttemptService)
        Mockito.verifyNoInteractions(mailRecordRagFactRepository)
    }

    // ------------------------------------------------------------------
    // I-40: fact_code 快照门禁（存在且 enabled）
    // ------------------------------------------------------------------

    @Test
    fun `rag send with unknown fact code fails 422 RAG_FACT_CODE_UNKNOWN`() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = "sender-1",
                subject = "Re: Test",
                htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.",
                operatorName = "op",
                ragFactCodes = listOf("KB-FUND-033", "KB-XXX-999"),
                ragCorpusFingerprint = fingerprint
            )
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status)
        assertEquals("RAG_FACT_CODE_UNKNOWN", ex.reason)
        Mockito.verifyNoInteractions(mailDeliveryService)
        Mockito.verifyNoInteractions(manualReplySendAttemptService)
    }

    @Test
    fun `rag send with disabled fact code fails 422 RAG_FACT_CODE_UNKNOWN`() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = "sender-1",
                subject = "Re: Test",
                htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.",
                operatorName = "op",
                ragFactCodes = listOf("KB-NOPE-001"),
                ragCorpusFingerprint = fingerprint
            )
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status)
        assertEquals("RAG_FACT_CODE_UNKNOWN", ex.reason)
        Mockito.verifyNoInteractions(mailDeliveryService)
        Mockito.verifyNoInteractions(manualReplySendAttemptService)
    }

    // ------------------------------------------------------------------
    // RAG 成功路径：I-39 / I-40 / I-42 / I-43 / I-47
    // ------------------------------------------------------------------

    @Test
    fun `rag send succeeds writes ordered rag fact evidence and never touches qa_rule`() {
        val capturedPayloads = stubSuccessfulSend()
        val savedRows = mutableListOf<MailRecordRagFact>()
        Mockito.doAnswer { inv ->
            savedRows += inv.getArgument<Iterable<MailRecordRagFact>>(0).toList()
            savedRows
        }.`when`(mailRecordRagFactRepository).saveAll(Mockito.anyIterable())

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = "sender-1",
            subject = "Re: Test",
            htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.",
            operatorName = "op",
            ragFactCodes = listOf("KB-FUND-033", "KB-COMP-007", "KB-PROG-002"),
            ragCorpusFingerprint = fingerprint
        )

        assertEquals("SENT", result.sendStatus)
        // I-42: 有序存证（ordinal 与请求下标一一对应，携带发出时的指纹）。
        assertEquals(
            listOf("KB-FUND-033" to 0, "KB-COMP-007" to 1, "KB-PROG-002" to 2),
            savedRows.map { it.factCode to it.ordinal }
        )
        assertTrue(savedRows.all { it.mailRecordId == 500L })
        assertTrue(savedRows.all { it.corpusFingerprint == fingerprint })
        // I-43: 发送 payload 不携带 primaryRuleId（matched_qa_rule_id 置 null）。
        val payload = capturedPayloads.single()
        assertNull(payload.primaryRuleId)
        assertEquals(emptyList<Long>(), payload.canonicalQaRuleIds)
        // I-39: 本路径绝不写 mail_record_qa_rule。
        Mockito.verifyNoInteractions(mailRecordQaRuleRepository)
        // I-40/I-47: RAG 分支对 qa_rule / selection / draft 链零交互。
        Mockito.verifyNoInteractions(qaRuleRepository)
        Mockito.verifyNoInteractions(qaFactSelectionService)
        Mockito.verifyNoInteractions(aiReplyDraftService)
        // 审计仍按证据发送记录（carriesQa=true → SEND_MANUAL_COMPOSED_REPLY），
        // canonicalFactIds 与 serverSuggestedFactIds 均为空（不伪造 Long ids）。
        Mockito.verify(manualReplySendAttemptService).recordSendAudit(
            Mockito.eq(100L), Mockito.eq(1L), Mockito.eq(500L),
            eqValue(emptyList<Long>()), Mockito.eq(true),
            anyValue(com.weibo.talentintroduction.mail.service.DeliveredMail("", "")),
            eqValue("Re: Test"),
            Mockito.anyString(), eqValue("op"), anyValue(inbound()),
            eqValue(emptyList<Long>()), anyValue(false), anyValue(""), anyValue(null)
        )
    }

    @Test
    fun `rag send preserves duplicate fact codes in request order without dedupe`() {
        stubSuccessfulSend()
        val savedRows = mutableListOf<MailRecordRagFact>()
        Mockito.doAnswer { inv ->
            savedRows += inv.getArgument<Iterable<MailRecordRagFact>>(0).toList()
            savedRows
        }.`when`(mailRecordRagFactRepository).saveAll(Mockito.anyIterable())

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = "sender-1",
            subject = "Re: Test",
            htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.",
            operatorName = "op",
            ragFactCodes = listOf("KB-FUND-033", "KB-COMP-007", "KB-FUND-033", "KB-FUND-033"),
            ragCorpusFingerprint = fingerprint
        )

        assertEquals("SENT", result.sendStatus)
        // I-42: 不排序、不去重 —— 原样落 4 行，ordinal 0..3。
        assertEquals(
            listOf("KB-FUND-033" to 0, "KB-COMP-007" to 1, "KB-FUND-033" to 2, "KB-FUND-033" to 3),
            savedRows.map { it.factCode to it.ordinal }
        )
    }

    // ------------------------------------------------------------------
    // I-47: safety 链绕开 + 纯文本检查保留（二次确认触发源）
    // ------------------------------------------------------------------

    @Test
    fun `rag send with hallucinated number still requires confirmation and shows no QA code`() {
        stubSuccessfulSend()
        val hallucinatedBody = "We confirm the programme provides EUR 1,200,000 in funding."
        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = "sender-1",
                subject = "Re: Test",
                htmlBody = "<p>$hallucinatedBody</p>",
                textBody = hallucinatedBody,
                operatorName = "op",
                ragFactCodes = listOf("KB-FUND-033"),
                ragCorpusFingerprint = fingerprint
            )
        }
        val codes = ex.findings.map { it.code }
        assertTrue(
            codes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HALLUCINATED_FACT),
            "pure-text hallucinated-number check must still fire, was: $codes"
        )
        assertTrue(
            !codes.contains("QA_FACTS_ALL_INVALID"),
            "RAG findings must not contain QA_FACTS_ALL_INVALID, was: $codes"
        )
        // I-47: selection/trust-gap 链零交互（blocked 前也没有任何发送尝试）。
        Mockito.verifyNoInteractions(mailDeliveryService)
        Mockito.verifyNoInteractions(manualReplySendAttemptService)
        Mockito.verifyNoInteractions(qaRuleRepository)
        Mockito.verifyNoInteractions(qaFactSelectionService)
        Mockito.verifyNoInteractions(aiReplyDraftService)

        // 二次确认后照常发出（safetyWarningConfirmed=true 单次确认即可，severity 为 NORMAL）。
        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = "sender-1",
            subject = "Re: Test",
            htmlBody = "<p>$hallucinatedBody</p>",
            textBody = hallucinatedBody,
            operatorName = "op",
            ragFactCodes = listOf("KB-FUND-033"),
            ragCorpusFingerprint = fingerprint,
            safetyWarningConfirmed = true
        )
        assertEquals("SENT", result.sendStatus)
    }

    // ------------------------------------------------------------------
    // 回归冒烟：旧路径行为未变
    // ------------------------------------------------------------------

    @Test
    fun `legacy qaRuleIds path still sends with canonicalized facts and never writes rag fact rows`() {
        val rule = qaRule(10L)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", listOf(10L), true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = listOf(10L),
                    promptRuleIds = listOf(10L),
                    requestFacts = emptyList()
                )
            )
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", null, true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = listOf(10L),
                    promptRuleIds = listOf(10L),
                    requestFacts = emptyList()
                )
            )
        val capturedPayloads = stubSuccessfulSend()

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = "sender-1",
            subject = "Re: Test",
            htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.",
            operatorName = "op",
            qaRuleIds = listOf(10L)
        )

        assertEquals("SENT", result.sendStatus)
        // 旧路径保持 canonical 关联（I-43 的兜底只属于旧路径语义）。
        val payload = capturedPayloads.single()
        assertEquals(listOf(10L), payload.canonicalQaRuleIds)
        assertEquals(10L, payload.primaryRuleId)
        // I-39: 旧路径绝不写 mail_record_rag_fact。
        Mockito.verifyNoInteractions(mailRecordRagFactRepository)
        Mockito.verify(manualReplySendAttemptService).recordSendAudit(
            Mockito.eq(100L), Mockito.eq(1L), Mockito.eq(500L),
            eqValue(listOf(10L)), Mockito.eq(true),
            anyValue(com.weibo.talentintroduction.mail.service.DeliveredMail("", "")),
            eqValue("Re: Test"),
            Mockito.anyString(), eqValue("op"), anyValue(inbound()),
            eqValue(listOf(10L)), anyValue(false), anyValue(""), anyValue(null)
        )
    }

    @Test
    fun `trust assembly path still sends after server re-verification and never writes rag fact rows`() {
        val version = operatorDirectedVersion()
        val assembled = assembledResponse(version)
        Mockito.`when`(
            trustReplyWorkbenchService.verifyAssembly(anyValue(liveAssembly()))
        ).thenReturn(verified(assembled))
        stubSuccessfulSend()

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = "sender-1",
            subject = "Re: Test",
            htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.",
            operatorName = "op",
            trustReplyAssembly = liveAssembly(),
            qaRuleIds = emptyList()
        )

        assertEquals("SENT", result.sendStatus)
        Mockito.verify(trustReplyWorkbenchService).verifyAssembly(anyValue(liveAssembly()))
        Mockito.verifyNoInteractions(mailRecordRagFactRepository)
    }

    // ------------------------------------------------------------------
    // 旧 assembly 构件（与 UnmatchedInboundTrustWorkbenchTest 同形的最小版本）
    // ------------------------------------------------------------------

    private fun operatorDirectedVersion() = TrustReplyItemVersion(
        versionId = "live-version-1",
        requestKey = "live-request-1",
        handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
        answerText = "We will follow up next week.",
        claims = emptyList(),
        model = "DEEPSEEK_V4_FLASH",
        generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
        evidenceSetVersion = "evidence-v1",
        sourceVersion = "live-v1",
        operatorInstructionHash = "hash-1",
        requestIndex = 0,
        requestText = "When will you follow up?",
        operatorInstruction = "Please say we will follow up next week."
    )

    private fun liveAssembly(): TrustReplyAssembleRequest {
        val source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100L)
        val version = operatorDirectedVersion()
        return TrustReplyAssembleRequest(
            source = source,
            expectedSourceVersion = "live-v1",
            expectedEvidenceSetVersion = "evidence-v1",
            lockedItems = listOf(
                TrustReplyLockedItemRequest(
                    requestKey = version.requestKey,
                    versionId = version.versionId,
                    handling = version.handling,
                    answerText = version.answerText,
                    claims = version.claims,
                    model = version.model,
                    generationKind = version.generationKind,
                    evidenceSetVersion = version.evidenceSetVersion,
                    sourceVersion = version.sourceVersion,
                    operatorInstructionHash = version.operatorInstructionHash,
                    operatorInstruction = version.operatorInstruction
                )
            )
        )
    }

    private fun verified(assembled: TrustReplyAssembleResponse): VerifiedTrustReplyAssembly =
        VerifiedTrustReplyAssembly(
            response = assembled,
            selection = ResolvedQaRules(
                sendQaRuleIds = assembled.canonicalFactIds,
                promptRuleIds = assembled.canonicalFactIds,
                requestFacts = emptyList()
            )
        )

    private fun assembledResponse(version: TrustReplyItemVersion): TrustReplyAssembleResponse {
        val body = "We will follow up next week."
        return TrustReplyAssembleResponse(
            source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100L),
            sourceVersion = "live-v1",
            evidenceSetVersion = "evidence-v1",
            rawDraftText = body,
            renderedDraftText = body,
            draftHash = "draft-hash",
            canonicalFactIds = emptyList(),
            itemVersions = listOf(version)
        )
    }
}
