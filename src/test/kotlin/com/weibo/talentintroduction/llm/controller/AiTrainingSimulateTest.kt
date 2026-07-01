package com.weibo.talentintroduction.llm.controller

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.service.AiPromptConfigService
import com.weibo.talentintroduction.llm.service.AiReplyContextBuilder
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiTrainingQaService
import com.weibo.talentintroduction.llm.service.LlmDraftClient
import com.weibo.talentintroduction.llm.service.LlmStitchService
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
    private lateinit var qaMatchService: QaMatchService

    @MockBean
    private lateinit var qaRuleRepository: QaRuleRepository

    @MockBean
    private lateinit var replySnippetService: ReplySnippetService

    @MockBean
    private lateinit var llmDraftClientProvider: ObjectProvider<LlmDraftClient>

    @BeforeEach
    fun setUp() {
        Mockito.`when`(llmDraftClientProvider.getIfAvailable()).thenReturn(null)
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

        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
        Mockito.verifyNoInteractions(qaMatchService)
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
        subject: String = "Question"
    ) = MailRecord(
        id = 99L,
        expertContactId = contactId,
        direction = "INBOUND",
        mailType = "REPLY",
        subject = subject,
        body = "What is the funding?",
        cleanedBody = "What is the funding?",
        messageId = null,
        inReplyTo = null,
        matchedQaRuleId = null,
        sendStatus = null,
        receivedAt = null,
        sentAt = null
    )
}
