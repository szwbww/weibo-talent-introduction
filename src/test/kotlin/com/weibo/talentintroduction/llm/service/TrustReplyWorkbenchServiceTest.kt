package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.service.TrustReplySourceType.LIVE_INBOUND
import com.weibo.talentintroduction.llm.service.TrustReplySourceType.TRAINING_MAIL
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import java.time.LocalDateTime
import java.util.Optional

class TrustReplyWorkbenchServiceTest {
    private val mailRecords = Mockito.mock(MailRecordRepository::class.java)
    private val inboundProcessing = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val contacts = Mockito.mock(ExpertContactRepository::class.java)
    private val trainingQa = Mockito.mock(AiTrainingQaService::class.java)
    private val contextService = Mockito.mock(AiReplyContextService::class.java)
    private val factSelection = Mockito.mock(QaFactSelectionService::class.java)
    private val qaRules = Mockito.mock(QaRuleRepository::class.java)
    private val draftService = Mockito.mock(AiReplyDraftService::class.java)
    private val previewService = Mockito.mock(AiReplyDraftPreviewService::class.java)
    private val auditService = Mockito.mock(AiReplyReviewAuditService::class.java)
    private val pointByPointComposer = Mockito.mock(AiReplyPointByPointComposer::class.java)
    private val claimValidator = Mockito.mock(AiReplyHighRiskClaimValidator::class.java)

    private lateinit var service: TrustReplyWorkbenchService

    @BeforeEach
    fun setUp() {
        service = TrustReplyWorkbenchService(
            mailRecordRepository = mailRecords,
            inboundMailProcessingRepository = inboundProcessing,
            expertContactRepository = contacts,
            aiTrainingQaService = trainingQa,
            aiReplyContextService = contextService,
            qaFactSelectionService = factSelection,
            qaRuleRepository = qaRules,
            aiReplyDraftService = draftService,
            aiReplyDraftPreviewService = previewService,
            aiReplyReviewAuditService = auditService,
            llmProperties = LlmProperties(enabled = true),
            aiReplyPointByPointComposer = pointByPointComposer,
            claimValidator = claimValidator
        )
        Mockito.`when`(trainingQa.buildKnowledgeContext(Mockito.anyString())).thenReturn("")
        Mockito.`when`(
            contextService.build(
                Mockito.any(ExpertContact::class.java) ?: contact(),
                Mockito.anyList<MailRecord>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
            )
        ).thenReturn(
            AiReplyContext(
                profileText = "Name: Test",
                mailHistory = "history",
                contextWarnings = emptyList(),
                researchProfileSufficient = true
            )
        )
    }

    @Test
    fun `training source reads exact inbound mail and never falls back to latest`() {
        val exact = mail(id = 11L, body = "first")
        val latest = mail(id = 12L, body = "different")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact, latest))

        val resolved = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L))

        assertEquals("first", resolved.inboundText)
        assertEquals(TRAINING_MAIL, resolved.source.sourceType)
        Mockito.verify(mailRecords, Mockito.never()).findLatestInboundByExpertContactId(Mockito.anyLong())
    }

    @Test
    fun `live source reads exact inbound processing and prefers cleaned body`() {
        val exact = InboundMailProcessing(
            id = 21L,
            senderAccountCode = "sender-1",
            imapUid = 99L,
            messageId = "<live@example.com>",
            fromEmail = "expert@example.com",
            subject = "Live subject",
            body = "raw body",
            cleanedBody = "clean body",
            receivedAt = LocalDateTime.of(2026, 7, 28, 10, 0),
            processStatus = "MANUAL_REVIEW",
            processReason = "needs review",
            expertContactId = 7L
        )
        Mockito.`when`(inboundProcessing.findById(21L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(emptyList())

        val resolved = service.resolveSource(TrustReplySourceRef(LIVE_INBOUND, 21L))

        assertEquals("clean body", resolved.inboundText)
        assertEquals("sender-1", resolved.senderAccountCode)
        assertEquals(LIVE_INBOUND, resolved.source.sourceType)
        Mockito.verify(inboundProcessing).findById(21L)
    }

    @Test
    fun `training source rejects outbound mail and missing contact`() {
        val outbound = mail(id = 11L, direction = "OUTBOUND")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(outbound))
        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L))
        }

        val inbound = mail(id = 12L)
        Mockito.`when`(mailRecords.findById(12L)).thenReturn(Optional.of(inbound))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.empty())
        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 12L))
        }
    }

    @Test
    fun `source version is stable and changes when source body changes`() {
        val exact = mail(id = 11L, body = "first")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))

        val first = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion
        val second = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion
        assertEquals(first, second)

        val changed = exact.copy(body = "changed")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(changed))
        assertNotEquals(first, service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion)
    }

    @Test
    fun `bootstrap uses canonical selection and common model catalog`() {
        val exact = mail(id = 11L)
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val facts = ResolvedQaRules(
            sendQaRuleIds = listOf(9L),
            promptRuleIds = listOf(9L),
            requestFacts = listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)),
            requestCount = 1,
            groundedRequestCount = 1
        )
        Mockito.`when`(factSelection.select("first", listOf(9L), true)).thenReturn(facts)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(qaRules.findAllEnabledOrdered()).thenReturn(listOf(
            QaRule(
                id = 9L,
                categoryId = 3L,
                keywords = "what",
                replySubject = null,
                replyBody = "",
                answerBody = "answer",
                displayName = "What"
            )
        ))

        val bootstrap = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            requestedFactIds = listOf(9L)
        ))

        assertEquals(listOf(9L), bootstrap.canonicalFactIds)
        assertEquals(listOf("DEEPSEEK_V4_FLASH", "DEEPSEEK_V4_PRO"), bootstrap.availableModels)
        assertEquals("DEEPSEEK_V4_FLASH", bootstrap.defaultModel)
        assertEquals("evidence-v1", bootstrap.evidenceSetVersion)
        assertEquals(1, bootstrap.requestCoverage.size)
        assertEquals(
            listOf(TrustReplyRuleMetadata(9L, "What", 3L)),
            bootstrap.rulesByCategory
        )
        Mockito.verify(factSelection).select("first", listOf(9L), true)
    }

    @Test
    fun `training generation returns raw and rendered text without audit`() {
        val exact = mail(id = 11L)
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val result = AiReplyDraftResult(
            draftText = "raw {{expert.name}}",
            usedLlm = false,
            qaRuleIds = emptyList(),
            mode = AiReplyMode.QA_GROUNDED,
            requestCount = 1,
            generationState = AiReplyGenerationState.FALLBACK_LLM_DISABLED
        )
        Mockito.`when`(
            draftService.generate(
                inboundText = "first",
                operatorTurns = emptyList(),
                qaRuleIds = null,
                operatorInstruction = null,
                expertProfile = "Name: Test",
                mailHistory = "history",
                contextWarnings = emptyList(),
                replyModel = null,
                researchProfileSufficient = true
            )
        ).thenReturn(result)
        Mockito.`when`(previewService.preview("raw {{expert.name}}", contact(), null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered Test", emptyList()))

        val response = service.generate(
            TrustReplyGenerationRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedSourceVersion = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion
            )
        )

        assertEquals("raw {{expert.name}}", response.draftText)
        assertEquals("rendered Test", response.renderedDraftText)
        assertEquals(AiReplyDraftService.sha256Hex("raw {{expert.name}}"), response.draftHash)
        Mockito.verifyNoInteractions(auditService)
    }

    @Test
    fun `generation rejects stale source before calling draft service`() {
        val exact = mail(id = 11L)
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))

        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.generate(
                TrustReplyGenerationRequest(
                    source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                    expectedSourceVersion = "stale"
                )
            )
        }
        Mockito.verifyNoInteractions(draftService)
    }

    private fun contact() = ExpertContact(
        id = 7L,
        campaignId = 1L,
        orcidId = "0000-0000",
        expertEmail = "test@example.com",
        expertName = "Test"
    )

    private fun mail(
        id: Long,
        body: String = "first",
        direction: String = "INBOUND"
    ) = MailRecord(
        id = id,
        expertContactId = 7L,
        direction = direction,
        mailType = "REPLY",
        senderAccountCode = null,
        messageId = "<$id@example.com>",
        inReplyTo = null,
        subject = "Subject",
        body = body,
        cleanedBody = null,
        matchedQaRuleId = null,
        sendStatus = null,
        receivedAt = LocalDateTime.of(2026, 7, 28, 10, 0),
        sentAt = null
    )
}
