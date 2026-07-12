package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.llm.service.AiReplyContext
import com.weibo.talentintroduction.llm.service.AiReplyContextBuilder
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyDraftResult
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyMode
import com.weibo.talentintroduction.llm.service.AiTrainingQaService
import com.weibo.talentintroduction.llm.service.LlmStitchService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.AutoReplyPreviewService
import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.mail.service.UnmatchedInboundMailService
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class UnmatchedInboundAiReplyTurnKnowledgeTest {
    private val unmatchedInboundMailService = Mockito.mock(UnmatchedInboundMailService::class.java)
    private val expertEmailAliasService = Mockito.mock(ExpertEmailAliasService::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val pendingMailOperationService = Mockito.mock(PendingMailOperationService::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val llmStitchService = Mockito.mock(LlmStitchService::class.java)
    private val autoReplyPreviewService = Mockito.mock(AutoReplyPreviewService::class.java)
    private val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
    private val aiReplyDraftService = Mockito.mock(AiReplyDraftService::class.java)
    private val aiReplyContextBuilder = AiReplyContextBuilder()
    private val aiTrainingQaService = Mockito.mock(AiTrainingQaService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)

    private val controller = UnmatchedInboundMailController(
        unmatchedInboundMailService,
        expertEmailAliasService,
        expertContactRepository,
        pendingMailOperationService,
        operatorActionLogService,
        llmStitchService,
        autoReplyPreviewService,
        replySnippetService,
        aiReplyDraftService,
        aiReplyContextBuilder,
        aiTrainingQaService,
        mailRecordRepository,
        aiReplyContextService
    )

    private val contact = ExpertContact(
        id = 10L,
        campaignId = 1L,
        orcidId = "0000-0000-0000-0001",
        expertName = "Dr. Test",
        expertEmail = "expert@test.com",
        currentStatus = "WAITING_REPLY"
    )

    @Test
    fun `aiReplyTurn injects training knowledge into expert profile via context service`() {
        val detail = InboundMailProcessing(
            id = 1L,
            senderAccountCode = "a1",
            imapUid = 1L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Question",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(1L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello"))
            .thenReturn("Topic: office mascot\nAnswer: QINGFEI-PANDA")
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(false)

        val expectedProfile = "Name: Dr. Test\nTraining knowledge base:\nTopic: office mascot\nAnswer: QINGFEI-PANDA"
        val expectedContext = AiReplyContext(
            profileText = expectedProfile,
            mailHistory = "",
            contextWarnings = emptyList()
        )
        // Use exact values (not matchers) to avoid Kotlin non-null parameter check issues
        Mockito.`when`(
            aiReplyContextService.build(
                contact,
                emptyList(),
                "Hello",
                "Topic: office mascot\nAnswer: QINGFEI-PANDA"
            )
        ).thenReturn(expectedContext)

        var capturedProfile: String? = null
        var capturedWarnings: List<String>? = null
        Mockito.doAnswer { invocation ->
            capturedProfile = invocation.getArgument(4)
            capturedWarnings = invocation.getArgument(7)
            AiReplyDraftResult(
                draftText = "draft",
                usedLlm = false,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM
            )
        }.`when`(aiReplyDraftService).generate(
            Mockito.anyString(),
            Mockito.anyList(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.anyBoolean(),
            Mockito.anyList()
        )

        controller.aiReplyTurn(1L, AiReplyTurnRequest())

        assertTrue(capturedProfile!!.contains("Training knowledge base:"))
        assertTrue(capturedProfile!!.contains("QINGFEI-PANDA"))
        assertEquals(emptyList<String>(), capturedWarnings)
        Mockito.verify(aiTrainingQaService).buildKnowledgeContext("Hello")
    }

    @Test
    fun `aiReplyTurn response includes new coverage fields`() {
        val detail = InboundMailProcessing(
            id = 2L,
            senderAccountCode = "a1",
            imapUid = 2L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Query",
            body = "Question text",
            cleanedBody = "Question text",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(2L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Question text")).thenReturn("")
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(false)

        // Use exact values (not matchers) to avoid Kotlin non-null parameter check issues
        Mockito.`when`(
            aiReplyContextService.build(contact, emptyList(), "Question text", "")
        ).thenReturn(
            AiReplyContext(
                profileText = "Name: Dr. Test",
                mailHistory = "",
                contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
            )
        )

        Mockito.doReturn(
            AiReplyDraftResult(
                draftText = "reply draft",
                usedLlm = false,
                qaRuleIds = listOf(5L),
                mode = AiReplyMode.QA_MATCHED,
                requestCount = 1,
                groundedRequestCount = 0,
                unsupportedRequests = listOf("research scope"),
                contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"),
                fewShotDialogRefs = listOf("DIALOG_1")
            )
        ).`when`(aiReplyDraftService).generate(
            Mockito.anyString(),
            Mockito.anyList(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.anyBoolean(),
            Mockito.anyList()
        )

        val response = controller.aiReplyTurn(2L, AiReplyTurnRequest())

        assertEquals(listOf(5L), response.qaRuleIds)
        assertEquals("QA_MATCHED", response.mode)
        assertEquals(1, response.requestCount)
        assertEquals(0, response.groundedRequestCount)
        assertEquals(listOf("research scope"), response.unsupportedRequests)
        assertEquals(listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"), response.contextWarnings)
        assertEquals(listOf("DIALOG_1"), response.injectedDialogRefs)
    }

    @Test
    fun `aiReplyTurn maps all shared AiReplyDraftResult fields to response identically`() {
        val detail = InboundMailProcessing(
            id = 5L,
            senderAccountCode = "a1",
            imapUid = 5L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Multi-question",
            body = "Multiple questions",
            cleanedBody = "Multiple questions",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(5L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Multiple questions")).thenReturn("")
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(false)
        Mockito.`when`(
            aiReplyContextService.build(contact, emptyList(), "Multiple questions", "")
        ).thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        val sourceResult = AiReplyDraftResult(
            draftText = "Grounded reply body",
            usedLlm = true,
            qaRuleIds = listOf(1L, 3L),
            mode = AiReplyMode.QA_GROUNDED,
            requestCount = 2,
            groundedRequestCount = 1,
            unsupportedRequests = listOf("research scope question"),
            contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"),
            fewShotDialogRefs = listOf("DIALOG_42")
        )
        Mockito.doReturn(sourceResult).`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList()
        )

        val response = controller.aiReplyTurn(5L, AiReplyTurnRequest())

        assertEquals(sourceResult.mode.name, response.mode)
        assertEquals(sourceResult.qaRuleIds, response.qaRuleIds)
        assertEquals(sourceResult.requestCount, response.requestCount)
        assertEquals(sourceResult.groundedRequestCount, response.groundedRequestCount)
        assertEquals(sourceResult.unsupportedRequests, response.unsupportedRequests)
        assertEquals(sourceResult.contextWarnings, response.contextWarnings)
        assertEquals(sourceResult.fewShotDialogRefs, response.injectedDialogRefs)
    }

    @Test
    fun `aiReplyTurn throws when expertContactId is null`() {
        val detail = InboundMailProcessing(
            id = 3L,
            senderAccountCode = "a1",
            imapUid = 3L,
            messageId = null,
            fromEmail = "unknown@test.com",
            subject = "?",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = null
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(3L)).thenReturn(detail)

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            controller.aiReplyTurn(3L, AiReplyTurnRequest())
        }
    }
}
