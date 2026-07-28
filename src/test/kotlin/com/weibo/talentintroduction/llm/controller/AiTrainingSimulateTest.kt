package com.weibo.talentintroduction.llm.controller

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchResult
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.llm.service.AiPromptConfigEffectiveDto
import com.weibo.talentintroduction.llm.service.AiPromptConfigService
import com.weibo.talentintroduction.llm.service.AiReplyContext
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.FreeFormPromptDefaults
import com.weibo.talentintroduction.llm.service.AiReplyContextBuilder
import com.weibo.talentintroduction.llm.service.AiReplyDraftPreviewService
import com.weibo.talentintroduction.llm.service.QaFactSelectionService
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyDraftResult
import com.weibo.talentintroduction.llm.service.AiReplyMode
import com.weibo.talentintroduction.llm.service.AiReplyGroundedDraftMaterializer
import com.weibo.talentintroduction.llm.service.AiReplyGroundedContentPlanner
import com.weibo.talentintroduction.llm.service.AiReplyHighRiskClaimValidator
import com.weibo.talentintroduction.llm.service.AiReplyPointByPointComposer
import com.weibo.talentintroduction.llm.service.AiTrainingQaDto
import com.weibo.talentintroduction.llm.service.AiTrainingQaService
import com.weibo.talentintroduction.llm.service.AiTrainingDialogueService
import com.weibo.talentintroduction.llm.service.AiTrainingDialogueView
import com.weibo.talentintroduction.llm.service.AiTrainingEvaluationResponse
import com.weibo.talentintroduction.llm.service.AiTrainingEvaluationService
import com.weibo.talentintroduction.llm.service.AiTrainingEvaluationRequest
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleRequest
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.llm.service.TrustReplySourceType
import com.weibo.talentintroduction.llm.service.LlmDraftClient
import com.weibo.talentintroduction.mail.domain.MailRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.InboundMailTagService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.reply.service.ManualReplyFrame
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Optional

@WebMvcTest(AiTrainingController::class)
@Import(
    AiReplyDraftService::class,
    QaFactSelectionService::class,
    AiReplyPointByPointComposer::class,
    AiReplyGroundedDraftMaterializer::class,
    AiReplyHighRiskClaimValidator::class,
    AiReplyDraftPreviewService::class,
    AiReplyGroundedContentPlanner::class
)
@EnableConfigurationProperties(LlmProperties::class)
@TestPropertySource(properties = ["talent-introduction.llm.enabled=false"])
class AiTrainingSimulateTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var aiTrainingQaService: AiTrainingQaService

    @MockBean
    private lateinit var aiPromptConfigService: AiPromptConfigService

    @MockBean
    private lateinit var aiReplyContextBuilder: AiReplyContextBuilder

    @MockBean
    private lateinit var aiReplyContextService: AiReplyContextService

    @SpyBean
    private lateinit var aiReplyDraftService: AiReplyDraftService

    @MockBean
    private lateinit var expertContactRepository: ExpertContactRepository

    @MockBean
    private lateinit var mailRecordRepository: MailRecordRepository

    @MockBean
    private lateinit var expertSearchService: ExpertSearchService

    @MockBean
    private lateinit var inboundMailTagService: InboundMailTagService

    @MockBean
    private lateinit var inboundMailProcessingRepository: InboundMailProcessingRepository

    @MockBean
    private lateinit var qaMatchService: QaMatchService

    @MockBean
    private lateinit var qaRuleRepository: QaRuleRepository

    @MockBean
    private lateinit var replySnippetService: ReplySnippetService

    @MockBean
    private lateinit var llmDraftClientProvider: ObjectProvider<LlmDraftClient>

    @MockBean
    private lateinit var aiTrainingDialogueService: AiTrainingDialogueService

    @MockBean
    private lateinit var aiTrainingEvaluationService: AiTrainingEvaluationService

    @MockBean
    private lateinit var mailVariableService: MailVariableService

    @MockBean
    private lateinit var mailSenderAccountRepository: MailSenderAccountRepository

    @BeforeEach
    fun setUp() {
        Mockito.`when`(llmDraftClientProvider.getIfAvailable()).thenReturn(null)
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(emptyList())
        Mockito.`when`(aiPromptConfigService.getEffectiveFreeFormSystemPrompt(Mockito.anyString()))
            .thenAnswer { invocation -> invocation.getArgument(0) }
        Mockito.`when`(aiPromptConfigService.getEffectiveDto())
            .thenReturn(
                AiPromptConfigEffectiveDto(
                    freeFormSystemPrompt = FreeFormPromptDefaults.defaultFreeFormSystemPrompt(),
                    constraints = null,
                    updatedAt = null,
                    isCustom = false
                )
            )
        Mockito.`when`(replySnippetService.resolveManualFrame()).thenReturn(
            ManualReplyFrame(
                salutation = "Dear Professor,",
                greeting = null,
                closing = "Best regards,",
                ackOptions = emptyList()
            )
        )
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
    }

    @Test
    fun `simulate returns deterministic draft without persisting mail records when llm disabled`() {
        val contact = sampleContact()
        val inbound = sampleInbound()
        stubSimulateReadPath(contact, inbound)
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("What is the funding?"))
            .thenReturn("Topic: Funding\nAnswer: Up to 12M RMB")
        Mockito.`when`(
            aiReplyContextService.build(
                contact,
                listOf(inbound),
                "What is the funding?",
                "Topic: Funding\nAnswer: Up to 12M RMB"
            )
        ).thenReturn(
            AiReplyContext(
                profileText = "Name: Dr. Test\nTraining knowledge base:\nTopic: Funding\nAnswer: Up to 12M RMB",
                mailHistory = "[INBOUND] Question",
                contextWarnings = emptyList()
            )
        )
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expertContactId":10}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.draftText").isNotEmpty)
            .andExpect(jsonPath("$.renderedDraftText").isNotEmpty)
            .andExpect(jsonPath("$.usedLlm").value(false))
            .andExpect(jsonPath("$.llmEnabled").value(false))
            .andExpect(jsonPath("$.generationState").value("FALLBACK_LLM_DISABLED"))
            .andExpect(jsonPath("$.mode").value("QA_GROUNDED"))
            .andExpect(jsonPath("$.injectedDialogRefs").isArray)
            .andExpect(jsonPath("$.injectedDialogRefs").isEmpty)
            .andExpect(jsonPath("$.qaRuleIds").isArray)
            .andExpect(jsonPath("$.qaRuleIds").isEmpty)
            .andExpect(jsonPath("$.requestCount").value(1))
            .andExpect(jsonPath("$.contextWarnings").isArray)
            .andExpect(jsonPath("$.contextWarnings").value(org.hamcrest.Matchers.hasItem("AI_REPLY_PREVIEW_ACCOUNT_NOT_FOUND")))
            .andExpect(jsonPath("$.selectedModel").value("DEEPSEEK_V4_FLASH"))
            .andExpect(jsonPath("$.requestCoverage").isArray)
            .andExpect(jsonPath("$.requestCoverage.length()").value(1))
            .andExpect(jsonPath("$.draftReadiness").value("BLOCKED"))

        Mockito.verify(aiTrainingQaService).buildKnowledgeContext("What is the funding?")
        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `simulate echoes selectedModel for flash pro and rejects unknown`() {
        val contact = sampleContact()
        val inbound = sampleInbound()
        stubSimulateReadPath(contact, inbound)
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("What is the funding?")).thenReturn("")
        Mockito.`when`(
            aiReplyContextService.build(contact, listOf(inbound), "What is the funding?", "")
        ).thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expertContactId":10,"model":"DEEPSEEK_V4_FLASH"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.selectedModel").value("DEEPSEEK_V4_FLASH"))

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expertContactId":10,"model":"DEEPSEEK_V4_PRO"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.selectedModel").value("DEEPSEEK_V4_PRO"))

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expertContactId":10,"model":"DEEPSEEK_UNKNOWN"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `simulate with matched rules returns QA_GROUNDED with qaRuleIds in response`() {
        val contact = sampleContact()
        val inbound = sampleInbound(body = "what is the application process?")
        stubSimulateReadPath(contact, inbound)
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("what is the application process?")).thenReturn("")
        Mockito.`when`(
            aiReplyContextService.build(
                contact,
                listOf(inbound),
                "what is the application process?",
                ""
            )
        ).thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))
        val rule = com.weibo.talentintroduction.qa.domain.QaRule(
            id = 9L,
            categoryId = 1,
            keywords = "application,process",
            replySubject = "Application process",
            replyBody = "First, you submit the required materials.",
            answerBody = "First, you submit the required materials.",
            enabled = true
        )
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(listOf(rule))
        Mockito.`when`(qaRuleRepository.findById(9L)).thenReturn(Optional.of(rule))

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expertContactId":10}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mode").value("QA_GROUNDED"))
            .andExpect(jsonPath("$.draftText").value(org.hamcrest.Matchers.containsString("First, you submit the required materials.")))
            .andExpect(jsonPath("$.qaRuleIds").isArray)
            .andExpect(jsonPath("$.qaRuleIds[0]").value(9))
            .andExpect(jsonPath("$.requestCoverage").isArray)

        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `simulate exposes requestCoverage from per-request fact matrix`() {
        val contact = sampleContact()
        val inbound = sampleInbound(body = "- What is salary?\n- What are the deliverables?")
        stubSimulateReadPath(contact, inbound)
        val inboundText = "- What is salary?\n- What are the deliverables?"
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext(inboundText)).thenReturn("")
        Mockito.`when`(
            aiReplyContextService.build(contact, listOf(inbound), inboundText, "")
        ).thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        val salaryRule = com.weibo.talentintroduction.qa.domain.QaRule(
            id = 1L,
            categoryId = 1,
            keywords = "salary",
            replySubject = "Salary",
            replyBody = "Salary is competitive.",
            answerBody = "Salary is competitive.",
            enabled = true,
            replyPolicy = com.weibo.talentintroduction.qa.domain.QaReplyPolicy.AUTO.name,
            coverageKeys = "finance.government_funding"
        )
        val deliverRule = com.weibo.talentintroduction.qa.domain.QaRule(
            id = 2L,
            categoryId = 1,
            keywords = "deliverables",
            replySubject = "Scope",
            replyBody = "High-level project overview.",
            answerBody = "High-level project overview.",
            enabled = true,
            replyPolicy = com.weibo.talentintroduction.qa.domain.QaReplyPolicy.AUTO.name,
            coverageKeys = "role.deliverables"
        )
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(listOf(salaryRule, deliverRule))
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(deliverRule))

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expertContactId":10}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.requestCoverage").isArray)
            .andExpect(jsonPath("$.requestCoverage.length()").value(2))
            .andExpect(jsonPath("$.requestCoverage[0].index").value(1))
            .andExpect(jsonPath("$.requestCoverage[0].status").value("GROUNDED"))
            .andExpect(jsonPath("$.requestCoverage[0].factRuleIds[0]").value(1))
            .andExpect(jsonPath("$.requestCoverage[0].requiresResearchContext").doesNotExist())
            .andExpect(jsonPath("$.requestCoverage[1].index").value(2))
            .andExpect(jsonPath("$.requestCoverage[1].status").value("GROUNDED"))
            .andExpect(jsonPath("$.requestCoverage[1].factRuleIds[0]").value(2))
            .andExpect(jsonPath("$.requestCoverage[1].requiresResearchContext").doesNotExist())
            .andExpect(jsonPath("$.requestCoverage[1].intents").isArray)
            .andExpect(jsonPath("$.groundedRequestCount").value(2))
            .andExpect(jsonPath("$.draftReadiness").value("BLOCKED"))
            .andExpect(jsonPath("$.unsupportedRequests").isEmpty)

        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `simulate with dialogue keywords keeps deterministic fallback when llm disabled`() {
        val contact = sampleContact()
        val inbound = sampleInbound(body = "Are you accredited through another agency?")
        stubSimulateReadPath(contact, inbound)
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Are you accredited through another agency?"))
            .thenReturn("Topic: Agency trust\nAnswer: Standard reply")
        Mockito.`when`(
            aiReplyContextService.build(
                contact,
                listOf(inbound),
                "Are you accredited through another agency?",
                "Topic: Agency trust\nAnswer: Standard reply"
            )
        ).thenReturn(
            AiReplyContext(
                profileText = "Name: Dr. Test\nTraining knowledge base:\nTopic: Agency trust\nAnswer: Standard reply",
                mailHistory = "[INBOUND] Question",
                contextWarnings = emptyList()
            )
        )
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expertContactId":10}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.draftText").value(org.hamcrest.Matchers.containsString("LLM 未生成")))
            .andExpect(jsonPath("$.draftText").value(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("transparent disbursement")
            )))
            .andExpect(jsonPath("$.usedLlm").value(false))
            .andExpect(jsonPath("$.qaRuleIds").isArray)
            .andExpect(jsonPath("$.qaRuleIds").isEmpty)
            .andExpect(jsonPath("$.draftReadiness").value("BLOCKED"))

        Mockito.verifyNoInteractions(aiTrainingDialogueService)
    }

    @Test
    fun `simulate with mailRecordId selects exact mail and never calls findLatestInbound`() {
        val contact = sampleContact()
        val exactMail = sampleInbound(id = 77L, body = "Exact mail body for funding?")
        Mockito.`when`(mailRecordRepository.findById(77L)).thenReturn(Optional.of(exactMail))
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(listOf(exactMail))
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Exact mail body for funding?")).thenReturn("")
        Mockito.`when`(
            aiReplyContextService.build(contact, listOf(exactMail), "Exact mail body for funding?", "")
        ).thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mailRecordId":77}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mode").value("QA_GROUNDED"))
            .andExpect(jsonPath("$.qaRuleIds").isArray)
            .andExpect(jsonPath("$.requestCount").isNumber)
            .andExpect(jsonPath("$.contextWarnings").isArray)
            .andExpect(jsonPath("$.injectedDialogRefs").isArray)

        Mockito.verify(mailRecordRepository, Mockito.never()).findLatestInboundByExpertContactId(Mockito.anyLong())
        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `simulate with mailRecordId returns 400 when mail not found`() {
        Mockito.`when`(mailRecordRepository.findById(999L)).thenReturn(Optional.empty())

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mailRecordId":999}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `simulate with mailRecordId returns 400 when mail is OUTBOUND`() {
        val outbound = MailRecord(
            id = 55L,
            expertContactId = 10L,
            direction = "OUTBOUND",
            mailType = "INTRODUCTION",
            subject = "Hi",
            body = "Dear expert",
            cleanedBody = null,
            messageId = null,
            inReplyTo = null,
            matchedQaRuleId = null,
            sendStatus = null,
            receivedAt = null,
            sentAt = null
        )
        Mockito.`when`(mailRecordRepository.findById(55L)).thenReturn(Optional.of(outbound))

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mailRecordId":55}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `simulate with mailRecordId returns 400 when contact missing`() {
        val mail = sampleInbound(id = 66L)
        Mockito.`when`(mailRecordRepository.findById(66L)).thenReturn(Optional.of(mail))
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.empty())

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mailRecordId":66}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `simulate with expertContactId fallback calls findLatestInbound once`() {
        val contact = sampleContact()
        val inbound = sampleInbound()
        stubSimulateReadPath(contact, inbound)
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("What is the funding?")).thenReturn("")
        Mockito.`when`(
            aiReplyContextService.build(contact, listOf(inbound), "What is the funding?", "")
        ).thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expertContactId":10}""")
        )
            .andExpect(status().isOk)

        Mockito.verify(mailRecordRepository, Mockito.times(1)).findLatestInboundByExpertContactId(10L)
        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `listDialogues returns seeded dialogue views`() {
        val views = (1..10).map { index ->
            AiTrainingDialogueView(
                sourceRef = "DIALOG_$index",
                title = "Dialogue $index",
                keywords = "keyword$index",
                turnCount = index + 2,
                enabled = true
            )
        }
        Mockito.`when`(aiTrainingDialogueService.listViews()).thenReturn(views)

        mockMvc.perform(get("/api/ai-training/dialogues"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(10))
            .andExpect(jsonPath("$[0].sourceRef").value("DIALOG_1"))
            .andExpect(jsonPath("$[0].title").value("Dialogue 1"))
            .andExpect(jsonPath("$[0].keywords").value("keyword1"))
            .andExpect(jsonPath("$[0].turnCount").value(3))
            .andExpect(jsonPath("$[0].enabled").value(true))
    }

    @Test
    fun `listSimulateMails returns all inbound mails without filters`() {
        val contact = sampleContact()
        val inbound = sampleInbound()
        Mockito.`when`(
            mailRecordRepository.findInboundMailsForSimulation(true, listOf(-1L), null, null, 20, 0)
        ).thenReturn(listOf(inbound))
        Mockito.`when`(
            mailRecordRepository.countInboundMailsForSimulation(true, listOf(-1L), null, null)
        ).thenReturn(1L)
        Mockito.`when`(expertContactRepository.findAllById(listOf(10L))).thenReturn(listOf(contact))
        Mockito.`when`(inboundMailProcessingRepository.findAllByExpertContactId(10L)).thenReturn(emptyList())
        Mockito.`when`(inboundMailTagService.listTagsBatch(emptyList())).thenReturn(emptyMap())

        mockMvc.perform(get("/api/ai-training/simulate/mails"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].expertContactId").value(10))
            .andExpect(jsonPath("$.items[0].mailRecordId").value(99))
            .andExpect(jsonPath("$.items[0].body").value("What is the funding?"))

        Mockito.verify(mailRecordRepository).findInboundMailsForSimulation(true, listOf(-1L), null, null, 20, 0)
    }

    @Test
    fun `listSimulateMails filters by inbound tag key`() {
        val contact = sampleContact()
        val inbound = sampleInbound()
        Mockito.`when`(
            mailRecordRepository.findInboundMailsForSimulation(true, listOf(-1L), 3L, null, 20, 0)
        ).thenReturn(listOf(inbound))
        Mockito.`when`(
            mailRecordRepository.countInboundMailsForSimulation(true, listOf(-1L), 3L, null)
        ).thenReturn(1L)
        Mockito.`when`(expertContactRepository.findAllById(listOf(10L))).thenReturn(listOf(contact))
        Mockito.`when`(inboundMailProcessingRepository.findAllByExpertContactId(10L)).thenReturn(emptyList())
        Mockito.`when`(inboundMailTagService.listTagsBatch(emptyList())).thenReturn(emptyMap())

        mockMvc.perform(get("/api/ai-training/simulate/mails?inboundTagKey=qa:3"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))

        Mockito.verify(mailRecordRepository).findInboundMailsForSimulation(true, listOf(-1L), 3L, null, 20, 0)
    }

    @Test
    fun `listSimulateMails filters by expert tag through ES orcid lookup`() {
        val contact = sampleContact()
        val inbound = sampleInbound()
        Mockito.`when`(
            expertSearchService.searchExperts(1000, ExpertIndexLevel.CANDIDATE, "verified")
        ).thenReturn(ExpertSearchResult(experts = listOf(sampleExpertProfile("verified")), totalHits = 1))
        Mockito.`when`(
            expertSearchService.searchExperts(1000, ExpertIndexLevel.APPLICATION, "verified")
        ).thenReturn(ExpertSearchResult(emptyList(), 0))
        Mockito.`when`(expertContactRepository.findByOrcidIdIn(listOf("0000-0000-0000-0001")))
            .thenReturn(listOf(contact))
        Mockito.`when`(
            mailRecordRepository.findInboundMailsForSimulation(false, listOf(10L), null, null, 20, 0)
        ).thenReturn(listOf(inbound))
        Mockito.`when`(
            mailRecordRepository.countInboundMailsForSimulation(false, listOf(10L), null, null)
        ).thenReturn(1L)
        Mockito.`when`(expertContactRepository.findAllById(listOf(10L))).thenReturn(listOf(contact))
        Mockito.`when`(
            expertSearchService.searchByOrcidIds(listOf("0000-0000-0000-0001"), ExpertIndexLevel.CANDIDATE)
        ).thenReturn(listOf(sampleExpertProfile("verified")))
        Mockito.`when`(
            expertSearchService.searchByOrcidIds(listOf("0000-0000-0000-0001"), ExpertIndexLevel.APPLICATION)
        ).thenReturn(emptyList())
        Mockito.`when`(inboundMailProcessingRepository.findAllByExpertContactId(10L)).thenReturn(emptyList())
        Mockito.`when`(inboundMailTagService.listTagsBatch(emptyList())).thenReturn(emptyMap())

        mockMvc.perform(get("/api/ai-training/simulate/mails?expertTag=verified"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].expertTags[0]").value("verified"))

        Mockito.verify(mailRecordRepository).findInboundMailsForSimulation(false, listOf(10L), null, null, 20, 0)
    }

    @Test
    fun `getEffectivePromptConfig returns default values`() {
        Mockito.`when`(aiPromptConfigService.getEffectiveDto()).thenReturn(
            AiPromptConfigEffectiveDto(
                freeFormSystemPrompt = FreeFormPromptDefaults.defaultFreeFormSystemPrompt(),
                constraints = null,
                updatedAt = null,
                isCustom = false
            )
        )

        mockMvc.perform(get("/api/ai-training/prompt-config/effective"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isCustom").value(false))
            .andExpect(jsonPath("$.freeFormSystemPrompt").isNotEmpty)
    }

    @Test
    fun `getEffectivePromptConfig returns custom values`() {
        Mockito.`when`(aiPromptConfigService.getEffectiveDto()).thenReturn(
            AiPromptConfigEffectiveDto(
                freeFormSystemPrompt = "Custom only",
                constraints = "Line one",
                updatedAt = "2026-07-02T10:00:00",
                isCustom = true
            )
        )

        mockMvc.perform(get("/api/ai-training/prompt-config/effective"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isCustom").value(true))
            .andExpect(jsonPath("$.freeFormSystemPrompt").value("Custom only"))
            .andExpect(jsonPath("$.constraints").value("Line one"))
    }

    @Test
    fun `createQa creates manual import entry`() {
        Mockito.`when`(
            aiTrainingQaService.create(
                "中介角色定位",
                "Are you a mediator?",
                "We are an authorized agency.",
                "mediator,intermediary"
            )
        ).thenReturn(
            AiTrainingQaDto(
                id = 42L,
                topic = "中介角色定位",
                question = "Are you a mediator?",
                answer = "We are an authorized agency.",
                keywords = "mediator,intermediary",
                source = "MANUAL_IMPORT",
                sourceRef = "MANUAL:123",
                enabled = true,
                createdAt = null,
                updatedAt = null
            )
        )

        mockMvc.perform(
            post("/api/ai-training/qa")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "topic": "中介角色定位",
                      "question": "Are you a mediator?",
                      "answer": "We are an authorized agency.",
                      "keywords": "mediator,intermediary"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.source").value("MANUAL_IMPORT"))
    }

    @Test
    fun `updateQa updates existing entry`() {
        Mockito.`when`(
            aiTrainingQaService.update(
                7L,
                "信息来源渠道",
                "How did you find me?",
                "Through public academic databases.",
                "how did you find me"
            )
        ).thenReturn(
            AiTrainingQaDto(
                id = 7L,
                topic = "信息来源渠道",
                question = "How did you find me?",
                answer = "Through public academic databases.",
                keywords = "how did you find me",
                source = "MANUAL_IMPORT",
                sourceRef = "HOW_FOUND_ME",
                enabled = true,
                createdAt = null,
                updatedAt = "2026-07-02T10:00:00"
            )
        )

        mockMvc.perform(
            put("/api/ai-training/qa/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "topic": "信息来源渠道",
                      "question": "How did you find me?",
                      "answer": "Through public academic databases.",
                      "keywords": "how did you find me"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.answer").value("Through public academic databases."))
    }

    @Test
    fun `deleteQa removes entry`() {
        mockMvc.perform(delete("/api/ai-training/qa/9"))
            .andExpect(status().isOk)

        Mockito.verify(aiTrainingQaService).delete(9L)
    }

    @Test
    fun `listSimulateExperts preserves repository contact order`() {
        val newer = sampleContact(id = 2L, name = "Two", email = "two@example.com")
        val older = sampleContact(id = 1L, name = "One", email = "one@example.com")
        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail(null, 50))
            .thenReturn(listOf(2L, 1L))
        Mockito.`when`(expertContactRepository.findAllById(listOf(2L, 1L)))
            .thenReturn(listOf(newer, older))
        Mockito.`when`(mailRecordRepository.findLatestInboundByExpertContactId(2L))
            .thenReturn(sampleInbound(contactId = 2L, subject = "Latest two"))
        Mockito.`when`(mailRecordRepository.findLatestInboundByExpertContactId(1L))
            .thenReturn(sampleInbound(contactId = 1L, subject = "Latest one"))

        mockMvc.perform(get("/api/ai-training/simulate/experts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].contactId").value(2))
            .andExpect(jsonPath("$[0].lastSubject").value("Latest two"))
            .andExpect(jsonPath("$[1].contactId").value(1))
            .andExpect(jsonPath("$[1].lastSubject").value("Latest one"))

        Mockito.verify(mailRecordRepository).findExpertContactIdsWithInboundMail(null, 50)
    }

    @Test
    fun `listSimulateExperts passes keyword filter to repository`() {
        val contact = sampleContact(id = 5L, name = "Alice Expert", email = "alice@example.com")
        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail("alice", 50))
            .thenReturn(listOf(5L))
        Mockito.`when`(expertContactRepository.findAllById(listOf(5L))).thenReturn(listOf(contact))
        Mockito.`when`(mailRecordRepository.findLatestInboundByExpertContactId(5L))
            .thenReturn(sampleInbound(contactId = 5L, subject = "Alice question"))

        mockMvc.perform(get("/api/ai-training/simulate/experts?keyword=alice"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].contactId").value(5))
            .andExpect(jsonPath("$[0].expertName").value("Alice Expert"))

        Mockito.verify(mailRecordRepository).findExpertContactIdsWithInboundMail("alice", 50)
    }

    @Test
    fun `listSimulateExperts returns one entry per contact id from repository`() {
        val contact = sampleContact()
        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail(null, 50))
            .thenReturn(listOf(10L))
        Mockito.`when`(expertContactRepository.findAllById(listOf(10L))).thenReturn(listOf(contact))
        Mockito.`when`(mailRecordRepository.findLatestInboundByExpertContactId(10L))
            .thenReturn(sampleInbound())

        mockMvc.perform(get("/api/ai-training/simulate/experts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].contactId").value(10))
    }

    private fun stubSimulateReadPath(contact: ExpertContact, inbound: MailRecord) {
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findLatestInboundByExpertContactId(10L)).thenReturn(inbound)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(listOf(inbound))
    }

    private fun sampleExpertProfile(tag: String) = ExpertProfile(
        orcidId = "0000-0000-0000-0001",
        email = "test@example.com",
        givenNames = "Dr.",
        familyNames = "Test",
        country = null,
        keyword = null,
        employment = null,
        tags = listOf(tag)
    )

    private fun sampleContact(
        id: Long = 10L,
        name: String = "Dr. Test",
        email: String = "test@example.com"
    ) = ExpertContact(
        id = id,
        campaignId = 1L,
        orcidId = "0000-0000-0000-0001",
        expertName = name,
        expertEmail = email,
        currentStatus = "WAITING_REPLY"
    )

    private fun sampleInbound(
        id: Long = 99L,
        contactId: Long = 10L,
        subject: String = "Question",
        body: String = "What is the funding?",
        messageId: String? = null
    ) = MailRecord(
        id = id,
        expertContactId = contactId,
        direction = "INBOUND",
        mailType = "REPLY",
        subject = subject,
        body = body,
        cleanedBody = body,
        messageId = messageId,
        inReplyTo = null,
        matchedQaRuleId = null,
        sendStatus = null,
        receivedAt = null,
        sentAt = null
    )

    @Test
    fun `simulate passes current inbound messageId to context service`() {
        val contact = sampleContact()
        val inbound = sampleInbound(
            id = 77L,
            body = "Funding?",
            messageId = "<train-msg-id@example.com>"
        )
        Mockito.`when`(mailRecordRepository.findById(77L)).thenReturn(Optional.of(inbound))
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(listOf(inbound))
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Funding?")).thenReturn("")

        var capturedMessageId: String? = "unset"
        Mockito.doAnswer { invocation ->
            capturedMessageId = invocation.getArgument(4) as String?
            AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList())
        }.`when`(aiReplyContextService).build(
            contact, listOf(inbound), "Funding?", "", "<train-msg-id@example.com>"
        )
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mailRecordId":77}""")
        )
            .andExpect(status().isOk)

        assertEquals("<train-msg-id@example.com>", capturedMessageId)
    }

    @Test
    fun `simulate passes final filtered history to draft service`() {
        val contact = sampleContact()
        val current = sampleInbound(
            id = 88L,
            body = "TRAIN_CURRENT_EXCLUDED",
            messageId = " <TRAIN-CURRENT@example.com> "
        )
        val oldInbound = sampleInbound(
            id = 86L,
            body = "TRAIN_OLD_INCLUDED",
            messageId = "train-old@example.com"
        )
        val sentOutbound = MailRecord(
            id = 87L,
            expertContactId = 10L,
            direction = "OUTBOUND",
            mailType = "REPLY",
            messageId = "train-sent@example.com",
            inReplyTo = null,
            subject = "Sent",
            body = "TRAIN_SENT_INCLUDED",
            cleanedBody = "TRAIN_SENT_INCLUDED",
            matchedQaRuleId = null,
            sendStatus = "SENT",
            receivedAt = null,
            sentAt = null
        )
        val failedOutbound = sentOutbound.copy(
            id = 89L,
            messageId = "train-failed@example.com",
            body = "TRAIN_FAILED_EXCLUDED",
            cleanedBody = "TRAIN_FAILED_EXCLUDED",
            sendStatus = "FAILED"
        )
        val pendingOutbound = sentOutbound.copy(
            id = 90L,
            messageId = "train-pending@example.com",
            body = "TRAIN_PENDING_EXCLUDED",
            cleanedBody = "TRAIN_PENDING_EXCLUDED",
            sendStatus = "PENDING"
        )
        val records = listOf(oldInbound, sentOutbound, failedOutbound, pendingOutbound, current)
        Mockito.`when`(mailRecordRepository.findById(88L)).thenReturn(Optional.of(current))
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(records)
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("TRAIN_CURRENT_EXCLUDED")).thenReturn("")
        Mockito.doAnswer { invocation ->
            val history = AiReplyContextBuilder().buildMailHistory(records, invocation.getArgument(4))
            AiReplyContext(profileText = "Name: Dr. Test", mailHistory = history, contextWarnings = emptyList())
        }.`when`(aiReplyContextService).build(
            contact, records, "TRAIN_CURRENT_EXCLUDED", "", " <TRAIN-CURRENT@example.com> "
        )
        var capturedHistory: String? = null
        Mockito.doAnswer { invocation ->
            capturedHistory = invocation.getArgument(5)
            AiReplyDraftResult("draft", true, emptyList(), AiReplyMode.FREE_FORM)
        }.`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any(),
            Mockito.anyBoolean()
        )

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mailRecordId":88}""")
        ).andExpect(status().isOk)

        assertTrue(capturedHistory!!.contains("TRAIN_OLD_INCLUDED"))
        assertTrue(capturedHistory!!.contains("TRAIN_SENT_INCLUDED"))
        assertTrue(!capturedHistory!!.contains("TRAIN_FAILED_EXCLUDED"))
        assertTrue(!capturedHistory!!.contains("TRAIN_PENDING_EXCLUDED"))
        assertTrue(!capturedHistory!!.contains("TRAIN_CURRENT_EXCLUDED"))
    }

    @Test
    fun `evaluation endpoint accepts the complete assembly input and returns only evaluation result`() {
        Mockito.`when`(aiTrainingEvaluationService.save(anyNonNull(AiTrainingEvaluationRequest(
            assembly = TrustReplyAssembleRequest(
                source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
                expectedSourceVersion = "source-v1",
                expectedEvidenceSetVersion = "evidence-v1",
                lockedItems = emptyList()
            ),
            rating = "NEEDS_IMPROVEMENT",
            note = "too long",
            operatorName = "operator-a"
        ))))
            .thenReturn(AiTrainingEvaluationResponse(456L, "NEEDS_IMPROVEMENT", "2026-07-28T20:00:00"))

        mockMvc.perform(
            post("/api/ai-training/simulate/evaluations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedSourceVersion":"source-v1","expectedEvidenceSetVersion":"evidence-v1","lockedItems":[],"rating":"NEEDS_IMPROVEMENT","note":"too long","operatorName":"operator-a"}"""
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.evaluationId").value(456))
            .andExpect(jsonPath("$.rating").value("NEEDS_IMPROVEMENT"))
            .andExpect(jsonPath("$.createdAt").value("2026-07-28T20:00:00"))
            .andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize<Any>(3)))

        Mockito.verify(aiTrainingEvaluationService).save(anyNonNull(AiTrainingEvaluationRequest(
            assembly = TrustReplyAssembleRequest(
                source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
                expectedSourceVersion = "source-v1",
                expectedEvidenceSetVersion = "evidence-v1",
                lockedItems = emptyList()
            ),
            rating = "NEEDS_IMPROVEMENT",
            note = "too long",
            operatorName = "operator-a"
        )))
    }

    @Test
    fun `evaluation endpoint rejects non canonical source before service`() {
        mockMvc.perform(
            post("/api/ai-training/simulate/evaluations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"source":{"sourceType":"LIVE_INBOUND","sourceId":123},"expectedSourceVersion":"source-v1","expectedEvidenceSetVersion":"evidence-v1","lockedItems":[],"rating":"UNUSABLE"}"""
                )
        ).andExpect(status().isUnprocessableEntity)

        Mockito.verify(aiTrainingEvaluationService, Mockito.never()).save(anyNonNull(AiTrainingEvaluationRequest(
            assembly = TrustReplyAssembleRequest(
                source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
                expectedSourceVersion = "source-v1",
                expectedEvidenceSetVersion = "evidence-v1",
                lockedItems = emptyList()
            ),
            rating = "UNUSABLE"
        )))
    }

    private fun <T> anyNonNull(defaultValue: T): T = Mockito.any<T>() ?: defaultValue
}
