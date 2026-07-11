package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.llm.service.AiReplyContextBuilder
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
        mailRecordRepository
    )

    @Test
    fun `aiReplyTurn injects training knowledge into expert profile`() {
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
        val contact = ExpertContact(
            id = 10L,
            campaignId = 1L,
            orcidId = "0000-0000-0000-0001",
            expertName = "Dr. Test",
            expertEmail = "expert@test.com",
            currentStatus = "WAITING_REPLY"
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(1L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext())
            .thenReturn("Topic: office mascot\nAnswer: QINGFEI-PANDA")
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(false)

        var capturedProfile: String? = null
        Mockito.`when`(
            aiReplyDraftService.generate(
                Mockito.anyString(),
                Mockito.anyList(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.anyBoolean()
            )
        ).thenAnswer { invocation ->
            capturedProfile = invocation.getArgument(4)
            AiReplyDraftResult(
                draftText = "draft",
                usedLlm = false,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM
            )
        }

        controller.aiReplyTurn(1L, AiReplyTurnRequest())

        assertTrue(capturedProfile!!.contains("Training knowledge base:"))
        assertTrue(capturedProfile!!.contains("QINGFEI-PANDA"))
    }
}
