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
import com.weibo.talentintroduction.llm.service.FreeFormPromptDefaults
import com.weibo.talentintroduction.llm.service.AiReplyContextBuilder
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiTrainingQaDto
import com.weibo.talentintroduction.llm.service.AiTrainingQaService
import com.weibo.talentintroduction.llm.service.AiTrainingDialogueService
import com.weibo.talentintroduction.llm.service.AiTrainingDialogueView
import com.weibo.talentintroduction.llm.service.LlmDraftClient
import com.weibo.talentintroduction.llm.service.LlmStitchService
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.InboundMailTagService
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.reply.service.ManualReplyFrame
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
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
@Import(AiReplyDraftService::class, LlmStitchService::class)
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

    @BeforeEach
    fun setUp() {
        Mockito.`when`(llmDraftClientProvider.getIfAvailable()).thenReturn(null)
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(emptyList())
        Mockito.`when`(aiPromptConfigService.getEffectiveFreeFormSystemPrompt(Mockito.anyString()))
            .thenAnswer { invocation -> invocation.getArgument(0) }
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
        Mockito.`when`(
            aiReplyContextBuilder.appendKnowledgeToProfile("Name: Dr. Test", "Topic: Funding\nAnswer: Up to 12M RMB")
        ).thenReturn(
            "Name: Dr. Test\nTraining knowledge base:\nTopic: Funding\nAnswer: Up to 12M RMB"
        )
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext())
            .thenReturn("Topic: Funding\nAnswer: Up to 12M RMB")
        Mockito.`when`(qaMatchService.suggestComposition(Mockito.anyString())).thenReturn(
            com.weibo.talentintroduction.qa.service.CompositionSuggestResult(
                suggestedRuleIds = emptyList(),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = emptyList(),
                gapDetected = false,
                matchedCategoryIds = emptyList()
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
            .andExpect(jsonPath("$.draftText").value(org.hamcrest.Matchers.containsString("12M RMB")))
            .andExpect(jsonPath("$.usedLlm").value(false))
            .andExpect(jsonPath("$.llmEnabled").value(false))
            .andExpect(jsonPath("$.mode").value("FREE_FORM"))
            .andExpect(jsonPath("$.injectedDialogRefs").isArray)
            .andExpect(jsonPath("$.injectedDialogRefs").isEmpty)
            .andExpect(jsonPath("$.qaRuleIds").doesNotExist())

        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `simulate with matched rules returns QA_MATCHED without qaRuleIds in response`() {
        val contact = sampleContact()
        val inbound = sampleInbound(body = "what is the application process?")
        stubSimulateReadPath(contact, inbound)
        Mockito.`when`(
            aiReplyContextBuilder.appendKnowledgeToProfile("Name: Dr. Test", "")
        ).thenReturn("Name: Dr. Test")
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext()).thenReturn("")
        Mockito.`when`(qaMatchService.suggestComposition("what is the application process?")).thenReturn(
            com.weibo.talentintroduction.qa.service.CompositionSuggestResult(
                suggestedRuleIds = listOf(9L),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = emptyList(),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        val rule = com.weibo.talentintroduction.qa.domain.QaRule(
            id = 9L,
            categoryId = 1,
            keywords = "application process",
            replySubject = "Application process",
            replyBody = "First, you submit the required materials.",
            enabled = true
        )
        Mockito.`when`(qaRuleRepository.findById(9L)).thenReturn(Optional.of(rule))

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expertContactId":10}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mode").value("QA_MATCHED"))
            .andExpect(jsonPath("$.draftText").value(org.hamcrest.Matchers.containsString("First, you submit the required materials.")))
            .andExpect(jsonPath("$.qaRuleIds").doesNotExist())

        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `simulate with dialogue keywords keeps deterministic fallback when llm disabled`() {
        val contact = sampleContact()
        val inbound = sampleInbound(body = "Are you accredited through another agency?")
        stubSimulateReadPath(contact, inbound)
        Mockito.`when`(
            aiReplyContextBuilder.appendKnowledgeToProfile("Name: Dr. Test", "Topic: Agency trust\nAnswer: Standard reply")
        ).thenReturn(
            "Name: Dr. Test\nTraining knowledge base:\nTopic: Agency trust\nAnswer: Standard reply"
        )
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext())
            .thenReturn("Topic: Agency trust\nAnswer: Standard reply")
        Mockito.`when`(qaMatchService.suggestComposition(Mockito.anyString())).thenReturn(
            com.weibo.talentintroduction.qa.service.CompositionSuggestResult(
                suggestedRuleIds = emptyList(),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = emptyList(),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        mockMvc.perform(
            post("/api/ai-training/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expertContactId":10}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.draftText").value(org.hamcrest.Matchers.containsString("Standard reply")))
            .andExpect(jsonPath("$.draftText").value(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("transparent disbursement")
            )))
            .andExpect(jsonPath("$.usedLlm").value(false))
            .andExpect(jsonPath("$.qaRuleIds").doesNotExist())

        Mockito.verifyNoInteractions(aiTrainingDialogueService)
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
        Mockito.`when`(aiReplyContextBuilder.buildExpertProfile(contact)).thenReturn("Name: Dr. Test")
        Mockito.`when`(aiReplyContextBuilder.buildMailHistory(listOf(inbound))).thenReturn("[INBOUND] Question")
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
        contactId: Long = 10L,
        subject: String = "Question",
        body: String = "What is the funding?"
    ) = MailRecord(
        id = 99L,
        expertContactId = contactId,
        direction = "INBOUND",
        mailType = "REPLY",
        subject = subject,
        body = body,
        cleanedBody = body,
        messageId = null,
        inReplyTo = null,
        matchedQaRuleId = null,
        sendStatus = null,
        receivedAt = null,
        sentAt = null
    )
}
