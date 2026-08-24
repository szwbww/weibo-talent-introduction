package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.llm.service.AiReplyAction
import com.weibo.talentintroduction.llm.service.AiReplyActionPolicy
import com.weibo.talentintroduction.llm.service.AiReplyContext
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyDraftReadiness
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyHighRiskClaimValidator
import com.weibo.talentintroduction.llm.service.ExplicitSelectionPartition
import com.weibo.talentintroduction.llm.service.QaFactSelectionService
import com.weibo.talentintroduction.llm.service.RequestFactItem
import com.weibo.talentintroduction.llm.service.RequestGroundingStatus
import com.weibo.talentintroduction.llm.service.ResolvedQaRules
import com.weibo.talentintroduction.llm.service.ResolvedTrustReplySource
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleRequest
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleResponse
import com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplyItemVersion
import com.weibo.talentintroduction.llm.service.TrustReplyLockedItemRequest
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.llm.service.TrustReplySourceType
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchException
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchService
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerArchiveStatus
import com.weibo.talentintroduction.llm.service.VerifiedTrustReplyAssembly
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexArchiveResult
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.service.ContentVariantService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.InOrder
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.LocalDateTime
import java.util.Optional

class PendingMailOperationServiceTrustWorkbenchTest {
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)
    private val expertIndexLevelOperationService = Mockito.mock(ExpertIndexLevelOperationService::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailDeliveryService = Mockito.mock(MailDeliveryService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailRecordQaRuleRepository = Mockito.mock(MailRecordQaRuleRepository::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val qaCategoryRepository = Mockito.mock(QaCategoryRepository::class.java)
    private val qaFactSelectionService = Mockito.mock(QaFactSelectionService::class.java)
    private val aiReplyDraftService = Mockito.mock(AiReplyDraftService::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val renderTemplateService = MailComposeTemplateService(
        Mockito.mock(com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository::class.java),
        Mockito.mock(com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository::class.java),
        qaRuleRepository,
        Mockito.mock(com.weibo.talentintroduction.reply.repository.ReplySnippetRepository::class.java),
        ObjectMapper(),
        Mockito.mock(MailVariableService::class.java),
        expertContactRepository,
        mailSenderAccountService,
        ContentVariantService(
            Mockito.mock(com.weibo.talentintroduction.variant.repository.ContentVariantRepository::class.java),
            MailPlaceholderService()
        )
    )
    private val mailVariableService = MailVariableService(expertSearchService, renderTemplateService)
    private val manualReplySendAttemptService = Mockito.mock(ManualReplySendAttemptService::class.java)
    private val trustReplyWorkbenchService = Mockito.mock(TrustReplyWorkbenchService::class.java)
    private val unsupportedAnswerIndexService = Mockito.mock(UnsupportedAnswerIndexService::class.java)
    private val emailSuppressionService = Mockito.mock(EmailSuppressionService::class.java)
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
        emailSuppressionService
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
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", null, true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = listOf(10L),
                    promptRuleIds = listOf(10L),
                    requestFacts = emptyList()
                )
            )
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender-1")).thenReturn(senderAccount())
    }

    @Test
    fun `evaluate returns server canonical fact ids and readiness`() {
        val resolved = ResolvedQaRules(
            sendQaRuleIds = listOf(10L),
            promptRuleIds = listOf(10L),
            requestFacts = listOf(
                RequestFactItem(1, "Can I work remotely?", listOf(10L), RequestGroundingStatus.GROUNDED)
            )
        )
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", null, true))
            .thenReturn(resolved.copy(sendQaRuleIds = listOf(10L)))
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", listOf(10L), true))
            .thenReturn(resolved)
        Mockito.`when`(aiReplyDraftService.resolveDraftReadinessForSelection(resolved.requestFacts, resolved.sendQaRuleIds))
            .thenReturn(AiReplyDraftReadiness.READY)
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(listOf(qaRule(10L)))

        val result = service.evaluateComposedReply(100L, listOf(10L))

        assertEquals(listOf(10L), result.canonicalFactIds)
        assertEquals("READY", result.draftReadiness)
        assertEquals(1, result.requestCoverage.size)
    }

    // 计划 04 (T4.2): 全部失效不再 422 —— 走可二次确认（I-7），确认后发送成功。
    @Test
    fun `sendManualRichReply blocks on QA facts all invalid`() {
        val rule = qaRule(10L)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", listOf(10L), true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = emptyList(),
                    promptRuleIds = emptyList(),
                    requestFacts = emptyList()
                )
            )

        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.", operatorName = "op",
                qaRuleIds = listOf(10L)
            )
        }
        assertTrue(ex.findings.map { it.code }.contains("QA_FACTS_ALL_INVALID"))
        // I-9: 全部失效是 NORMAL 级，不要求逐字强确认。
        assertTrue(ex.findings.none { it.severity == SafetySeverity.STRONG })
        Mockito.verifyNoInteractions(mailDeliveryService)

        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-all-invalid@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-all-invalid@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(
                anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-all-invalid@weibo.com>")
            )
        ).thenReturn(500L)

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L, senderAccountCode = null,
            subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.", operatorName = "op",
            qaRuleIds = listOf(10L), safetyWarningConfirmed = true
        )

        assertEquals("SENT", result.sendStatus)
        Mockito.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    // 计划 04 (T4.2): select() 抛 IllegalArgumentException 时不再 400，降级为
    // 可确认风险 QA_FACT_NOT_MATCHING_REQUEST；确认后发送成功且
    // payload.canonicalQaRuleIds 等于运营入参的可选子集（I-3，保序）。
    @Test
    fun `sendManualRichReply degrades select failure to confirmable unmatched finding`() {
        val rule = qaRule(10L)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", listOf(10L, 11L), true))
            .thenThrow(
                IllegalArgumentException(
                    "Selected QA rules do not match any request in the inbound email: [11]"
                )
            )
        Mockito.`when`(
            qaFactSelectionService.partitionExplicitSelection("Can I work remotely?", listOf(10L, 11L))
        ).thenReturn(
            ExplicitSelectionPartition(
                selectable = listOf(10L),
                unavailable = emptyList(),
                unmatched = listOf(11L),
                noRequests = false
            )
        )
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", listOf(10L), true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = listOf(10L),
                    promptRuleIds = listOf(10L),
                    requestFacts = emptyList()
                )
            )

        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.", operatorName = "op",
                qaRuleIds = listOf(10L, 11L)
            )
        }
        assertTrue(ex.findings.map { it.code }.contains("QA_FACT_NOT_MATCHING_REQUEST"))
        assertTrue(ex.findings.none { it.severity == SafetySeverity.STRONG })
        Mockito.verifyNoInteractions(mailDeliveryService)

        val payloadHolder = mutableListOf<ManualReplySendAttemptService.SendPayload>()
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-unmatched@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.doAnswer { invocation ->
            payloadHolder += invocation.getArgument<ManualReplySendAttemptService.SendPayload>(0)
            claim
        }.`when`(manualReplySendAttemptService).prepareAndClaim(anyValue(sendPayload()))
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-unmatched@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(
                anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-unmatched@weibo.com>")
            )
        ).thenReturn(500L)

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L, senderAccountCode = null,
            subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.", operatorName = "op",
            qaRuleIds = listOf(10L, 11L), safetyWarningConfirmed = true
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(listOf(10L), payloadHolder.single().canonicalQaRuleIds)
        assertEquals(10L, payloadHolder.single().primaryRuleId)
        Mockito.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    // 计划 04 (T4.2, I-4/IP-1): 可选子集为空时降级为 QA_FACT_UNAVAILABLE，
    // 确认后发送成功且 payload.canonicalQaRuleIds 为空 —— finalizeSuccess 的
    // isNotEmpty() 守卫使 mail_record_qa_rule 零行、matchedQaRuleId = null，
    // 但 carriesQa 仍为 true（审计记 SEND_MANUAL_COMPOSED_REPLY）。
    @Test
    fun `sendManualRichReply with empty selectable subset keeps audit qa association empty`() {
        val rule = qaRule(10L)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", listOf(10L), true))
            .thenThrow(IllegalArgumentException("QA rule is disabled: 10"))
        Mockito.`when`(
            qaFactSelectionService.partitionExplicitSelection("Can I work remotely?", listOf(10L))
        ).thenReturn(
            ExplicitSelectionPartition(
                selectable = emptyList(),
                unavailable = listOf(10L),
                unmatched = emptyList(),
                noRequests = false
            )
        )

        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.", operatorName = "op",
                qaRuleIds = listOf(10L)
            )
        }
        assertTrue(ex.findings.map { it.code }.contains("QA_FACT_UNAVAILABLE"))
        assertTrue(ex.findings.map { it.code }.contains("QA_FACTS_ALL_INVALID"))
        assertTrue(ex.findings.none { it.severity == SafetySeverity.STRONG })
        Mockito.verifyNoInteractions(mailDeliveryService)

        val payloadHolder = mutableListOf<ManualReplySendAttemptService.SendPayload>()
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-unavailable@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.doAnswer { invocation ->
            payloadHolder += invocation.getArgument<ManualReplySendAttemptService.SendPayload>(0)
            claim
        }.`when`(manualReplySendAttemptService).prepareAndClaim(anyValue(sendPayload()))
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-unavailable@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(
                anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-unavailable@weibo.com>")
            )
        ).thenReturn(500L)

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L, senderAccountCode = null,
            subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.", operatorName = "op",
            qaRuleIds = listOf(10L), safetyWarningConfirmed = true
        )

        assertEquals("SENT", result.sendStatus)
        val sent = payloadHolder.single()
        assertEquals(emptyList<Long>(), sent.canonicalQaRuleIds)
        assertEquals(null, sent.primaryRuleId)
        // I-4: carriesQa 仍按 qaRuleIds 判定为 true，审计口径不变。
        Mockito.verify(manualReplySendAttemptService).recordSendAudit(
            Mockito.eq(100L), Mockito.eq(1L), Mockito.eq(500L),
            eqValue(emptyList<Long>()), Mockito.eq(true),
            anyValue(DeliveredMail("", "")), eqValue("Re: Test"),
            Mockito.anyString(), eqValue("op"), anyValue(inbound()),
            anyValue(emptyList<Long>()), anyValue(false), Mockito.anyString()
        )
        Mockito.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    // 计划 04 (IP-2 收敛): 同一 (inboundText, factRuleIds) 组合，预检产
    // AI_REPLY_PREFLIGHT_SOURCE_CHANGED 警告（不阻断），发送产
    // MANUAL_SEND_SAFETY_BLOCKED（可确认）—— 两者都不再 400/422，方向一致。
    @Test
    fun `preflight and send agree on degraded selection as warning not hard failure`() {
        val rule = qaRule(10L)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", listOf(10L, 11L), true))
            .thenThrow(
                IllegalArgumentException(
                    "Selected QA rules do not match any request in the inbound email: [11]"
                )
            )
        Mockito.`when`(
            qaFactSelectionService.partitionExplicitSelection("Can I work remotely?", listOf(10L, 11L))
        ).thenReturn(
            ExplicitSelectionPartition(
                selectable = listOf(10L),
                unavailable = emptyList(),
                unmatched = listOf(11L),
                noRequests = false
            )
        )
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", listOf(10L), true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = listOf(10L),
                    promptRuleIds = listOf(10L),
                    requestFacts = emptyList()
                )
            )
        Mockito.`when`(
            aiReplyDraftService.resolveDraftReadinessForSelection(
                Mockito.anyList<RequestFactItem>(), Mockito.anyList<Long>()
            )
        ).thenReturn(AiReplyDraftReadiness.READY)
        Mockito.`when`(
            aiReplyDraftService.buildEvidenceSnapshotForSelection(Mockito.anyList<Long>())
        ).thenReturn(Triple("evidence-v1", emptyList(), emptyList()))

        val preflight = service.preflightEditedAiReply(
            inboundProcessingId = 100L,
            factRuleIds = listOf(10L, 11L),
            expectedEvidenceSetVersion = "",
            textBody = "Remote work is possible."
        )
        assertEquals("WARNING", preflight.status)
        assertTrue(preflight.warningCodes.contains("AI_REPLY_PREFLIGHT_SOURCE_CHANGED"))

        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.", operatorName = "op",
                qaRuleIds = listOf(10L, 11L)
            )
        }
        assertTrue(ex.findings.map { it.code }.contains("QA_FACT_NOT_MATCHING_REQUEST"))
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `sendManualRichReply blocks on high risk content in final validation text`() {
        val rule = qaRule(10L)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", listOf(10L), true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = listOf(10L),
                    promptRuleIds = listOf(10L),
                    requestFacts = listOf(
                        RequestFactItem(1, "Salary?", listOf(10L), RequestGroundingStatus.GROUNDED)
                    )
                )
            )

        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>We guarantee 10 million RMB with no fees.</p>",
                textBody = "We guarantee 10 million RMB with no fees.",
                operatorName = "op", qaRuleIds = listOf(10L)
            )
        }
        assertTrue(ex.findings.isNotEmpty())
        Mockito.verifyNoInteractions(mailDeliveryService, manualReplySendAttemptService)
    }

    @Test
    fun `sendManualRichReply sends after operator confirms claim warning`() {
        val rule = qaRule(10L)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", null, true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = emptyList(),
                    promptRuleIds = emptyList(),
                    requestFacts = emptyList()
                )
            )
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-confirmed@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-confirmed@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(
                anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-confirmed@weibo.com>")
            )
        ).thenReturn(500L)

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L, senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>We guarantee 10 million RMB with no fees.</p>",
            textBody = "We guarantee 10 million RMB with no fees.",
            operatorName = "op", qaRuleIds = null,
            safetyWarningConfirmed = true
        )

        assertEquals("SENT", result.sendStatus)
        Mockito.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    @Test
    fun `unauthorized CV request yields materials-not-allowed finding and sends after confirm`() {
        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>Before arranging a Zoom meeting, could you please send me your CV?</p>",
                textBody = "Before arranging a Zoom meeting, could you please send me your CV?",
                operatorName = "op"
            )
        }
        assertTrue(ex.findings.any { it.code == AiReplyActionPolicy.CODE_ACTION_MATERIALS_NOT_ALLOWED })
        Mockito.verifyNoInteractions(mailDeliveryService)

        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-cv@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-cv@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(
                anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-cv@weibo.com>")
            )
        ).thenReturn(500L)

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L, senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>Before arranging a Zoom meeting, could you please send me your CV?</p>",
            textBody = "Before arranging a Zoom meeting, could you please send me your CV?",
            operatorName = "op",
            safetyWarningConfirmed = true
        )

        assertEquals("SENT", result.sendStatus)
        Mockito.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    @Test
    fun `manual send accepts an operator authorised materials request`() {
        val compliant =
            "If you would like to proceed, you are welcome to share your CV at your convenience " +
                "so that we can carry out an initial eligibility review."
        val assembly = liveAssembly().copy(
            lockedItems = listOf(liveAssembly().lockedItems.single().copy(answerText = compliant))
        )
        val assembled = assembledResponse(operatorDirectedVersion())
        // 03 (I-1): 发送前服务端重算验证；授权来自已验证 versions（I-5）。
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly))
            .thenReturn(verified(assembled))
        Mockito.`when`(trustReplyWorkbenchService.operatorAuthorizedActionsFromVerifiedVersions(assembled.itemVersions))
            .thenReturn(setOf(AiReplyAction.REQUEST_MATERIALS))
        stubSuccessfulSend()

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>$compliant</p>",
            textBody = compliant,
            operatorName = "op",
            templateTextBody = "different-template",
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        Mockito.verify(trustReplyWorkbenchService).operatorAuthorizedActionsFromVerifiedVersions(assembled.itemVersions)
    }

    // 03 (I-1): assembly 指向其他来信时，在任何发送副作用（含 suppression、claim、
    // 授权推导）之前稳定 422 拒绝 —— 不再像旧实现那样退化为「未授权 + safety 拦截」。
    @Test
    fun `manual send ignores an assembly that points at another inbound`() {
        val compliant =
            "If you would like to proceed, you are welcome to share your CV at your convenience " +
                "so that we can carry out an initial eligibility review."
        val assembly = liveAssembly().copy(
            source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 99L)
        )

        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>$compliant</p>",
                textBody = compliant,
                operatorName = "op",
                trustReplyAssembly = assembly
            )
        }

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status)
        Mockito.verifyNoInteractions(
            trustReplyWorkbenchService,
            manualReplySendAttemptService,
            mailDeliveryService,
            emailSuppressionService
        )
    }

    @Test
    fun `operator authorisation does not override a blocking trust gap`() {
        val compliant =
            "If you would like to proceed, you are welcome to share your CV at your convenience " +
                "so that we can carry out an initial eligibility review."
        val assembly = liveAssembly()
        val assembled = assembledResponse(operatorDirectedVersion())
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly))
            .thenReturn(verified(assembled))
        Mockito.`when`(trustReplyWorkbenchService.operatorAuthorizedActionsFromVerifiedVersions(assembled.itemVersions))
            .thenReturn(setOf(AiReplyAction.REQUEST_MATERIALS))
        Mockito.`when`(aiReplyDraftService.hasBlockingTrustGapForSelection(Mockito.anyList<RequestFactItem>()))
            .thenReturn(true)

        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>$compliant</p>",
                textBody = compliant,
                operatorName = "op",
                trustReplyAssembly = assembly
            )
        }

        assertTrue(ex.findings.any { it.code == AiReplyActionPolicy.CODE_ACTION_MATERIALS_NOT_ALLOWED })
    }

    @Test
    fun `sendManualRichReply collects all matching findings not just first`() {
        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>Could you share your CV? Please rest assured that we guarantee everything.</p>",
                textBody = "Could you share your CV? Please rest assured that we guarantee everything.",
                operatorName = "op"
            )
        }
        assertTrue(ex.findings.size >= 2)
        assertTrue(ex.findings.any { it.code == AiReplyActionPolicy.CODE_ACTION_MATERIALS_NOT_ALLOWED })
        assertTrue(ex.findings.any { it.code == AiReplyHighRiskClaimValidator.WARNING_CLAIM_TRUST_RHETORIC })
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `passport request requires strong typed confirmation to send`() {
        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>Please also send your passport copy for verification.</p>",
                textBody = "Please also send your passport copy for verification.",
                operatorName = "op",
                safetyWarningConfirmed = true
            )
        }
        assertTrue(ex.findings.any { it.severity == SafetySeverity.STRONG })
        Mockito.verifyNoInteractions(mailDeliveryService)

        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-passport@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-passport@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(
                anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-passport@weibo.com>")
            )
        ).thenReturn(500L)

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L, senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>Please also send your passport copy for verification.</p>",
            textBody = "Please also send your passport copy for verification.",
            operatorName = "op",
            safetyWarningConfirmed = true,
            strongConfirmationText = "确认发送"
        )

        assertEquals("SENT", result.sendStatus)
        Mockito.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    @Test
    fun `strong typed confirmation alone cannot bypass the first confirmation`() {
        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>Please also send your passport copy for verification.</p>",
                textBody = "Please also send your passport copy for verification.",
                operatorName = "op",
                safetyWarningConfirmed = false,
                strongConfirmationText = "确认发送"
            )
        }
        assertTrue(ex.findings.isNotEmpty())
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `audit note records overridden safety codes without matching sentence`() {
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-audit@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-audit@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(
                anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-audit@weibo.com>")
            )
        ).thenReturn(500L)

        val noteHolder = mutableListOf<String>()
        Mockito.doAnswer { invocation ->
            noteHolder += invocation.getArgument<String>(12)
            null
        }.`when`(manualReplySendAttemptService).recordSendAudit(
            anyValue(100L), anyValue(1L), anyValue(500L),
            anyValue(emptyList<Long>()), anyValue(false),
            anyValue(DeliveredMail("", "")), anyValue("Re: Test"),
            anyValue("preview"), anyValue("op"), anyValue(inbound()),
            anyValue(emptyList<Long>()), anyValue(false), anyValue("note")
        )

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L, senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>Before arranging a Zoom meeting, could you please send me your CV?</p>",
            textBody = "Before arranging a Zoom meeting, could you please send me your CV?",
            operatorName = "op",
            safetyWarningConfirmed = true
        )

        assertEquals("SENT", result.sendStatus)
        val note = noteHolder.single()
        assertTrue(note.contains(AiReplyActionPolicy.CODE_ACTION_MATERIALS_NOT_ALLOWED))
        assertTrue(!note.contains("your CV"))
    }

    @Test
    fun `sendManualRichReply rejects suppressed recipient before claiming an attempt`() {
        Mockito.`when`(emailSuppressionService.isSuppressed(contact.expertEmail)).thenReturn(true)

        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Hello",
                htmlBody = "<p>Thank you for your patience.</p>",
                textBody = "Thank you for your patience.",
                operatorName = "op"
            )
        }

        // I-5 + IP-3: 前置拦截在 prepareAndClaim 之前 —— 400 而非 409「发送状态未知」。
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
        assertEquals("收件人已退订，禁止外发：${contact.expertEmail}", ex.reason)
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).prepareAndClaim(anyValue(sendPayload()))
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    @Test
    fun `sendManualRichReply never finalizes DELIVERY_UNKNOWN for suppressed recipient`() {
        Mockito.`when`(emailSuppressionService.isSuppressed(contact.expertEmail)).thenReturn(true)

        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Hello",
                htmlBody = "<p>Thank you for your patience.</p>",
                textBody = "Thank you for your patience.",
                operatorName = "op"
            )
        }

        // I-2/I-5: 抑制拒发绝不走 finalizeFailure —— attempt 行不得被烧成 DELIVERY_UNKNOWN。
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).finalizeFailure(
            anyValue(sendPayload()),
            Mockito.anyLong(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any()
        )
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).prepareAndClaim(anyValue(sendPayload()))
    }

    @Test
    fun `sendManualRichReply allows pure manual text without QA facts`() {
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", null, true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = emptyList(),
                    promptRuleIds = emptyList(),
                    requestFacts = emptyList()
                )
            )
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-abc@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>"))
        ).thenReturn(500L)

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L, senderAccountCode = null,
            subject = "Re: Hello",
            htmlBody = "<p>Thank you for your patience.</p>",
            textBody = "Thank you for your patience.",
            operatorName = "op"
        )

        assertEquals("SENT", result.sendStatus)
        Mockito.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    @Test
    fun `sendManualRichReply succeeds with QA facts and returns SENT`() {
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
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-abc@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>"))
        ).thenReturn(500L)

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L, senderAccountCode = null,
            subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.", operatorName = "op",
            qaRuleIds = listOf(10L)
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals("<manual-rich-abc@weibo.com>", result.messageId)
        Mockito.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
        Mockito.verify(manualReplySendAttemptService).recordSendAudit(
            Mockito.eq(100L), Mockito.eq(1L), Mockito.eq(500L),
            eqValue(listOf(10L)), Mockito.eq(true),
            anyValue(DeliveredMail("", "")), eqValue("Re: Test"),
            Mockito.anyString(), eqValue("op"), anyValue(inbound()),
            anyValue(emptyList<Long>()), anyValue(false), anyValue("")
        )
    }

    @Test
    fun `sendManualRichReply dedups same payload and returns SENT without SMTP`() {
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
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.DEDUP_SENT
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L, senderAccountCode = null,
            subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.", operatorName = "op",
            qaRuleIds = listOf(10L)
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals("<manual-rich-abc@weibo.com>", result.messageId)
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `sendManualRichReply throws 409 for delivery unknown`() {
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
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.UNKNOWN
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)

        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.", operatorName = "op",
                qaRuleIds = listOf(10L)
            )
        }
        assertEquals(409, ex.status.value())
        assertTrue(ex.reason!!.contains("请勿重复发送"))
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `sendManualRichReply throws 409 for in progress`() {
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
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.IN_PROGRESS
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)

        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.", operatorName = "op",
                qaRuleIds = listOf(10L)
            )
        }
        assertEquals(409, ex.status.value())
        assertTrue(ex.reason!!.contains("请勿重复发送"))
    }

    @Test
    fun `sendManualRichReply throws 422 for permanent failure attempt`() {
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
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.PERMANENT_FAILED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)

        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.", operatorName = "op",
                qaRuleIds = listOf(10L)
            )
        }
        assertEquals(422, ex.status.value())
    }

    @Test
    fun `sendManualRichReply throws 503 for safe retry delivery failure`() {
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
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(
            DeliveredMail(
                messageId = "<manual-rich-abc@weibo.com>",
                status = "FAILED",
                errorCategory = com.weibo.talentintroduction.mail.domain.SmtpErrorCategory.TRANSIENT,
                smtpResponseCode = 451,
                errorDetail = "try later"
            )
        )
        Mockito.`when`(
            manualReplySendAttemptService.finalizeFailure(
                anyValue(sendPayload()), Mockito.eq(1L), Mockito.anyString(), Mockito.anyString(), Mockito.any()
            )
        ).thenReturn(501L)

        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.", operatorName = "op",
                qaRuleIds = listOf(10L)
            )
        }
        assertEquals(503, ex.status.value())
    }

    @Test
    fun `sendManualRichReply blocks on subject length over 255`() {
        val longSubject = "A".repeat(256)
        Mockito.`when`(qaRuleRepository.findById(Mockito.anyLong())).thenReturn(Optional.of(qaRule(10L)))
        Mockito.`when`(qaFactSelectionService.select(Mockito.anyString(), Mockito.any(), Mockito.anyBoolean()))
            .thenReturn(
                ResolvedQaRules(sendQaRuleIds = listOf(10L), promptRuleIds = listOf(10L), requestFacts = emptyList())
            )

        assertThrows(IllegalArgumentException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = longSubject, htmlBody = "<p>Test</p>",
                textBody = "Test", operatorName = "op", qaRuleIds = listOf(10L)
            )
        }
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `sendManualRichReply blocks on validation text over 20000 chars`() {
        val rule = qaRule(10L)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(qaFactSelectionService.select(Mockito.anyString(), Mockito.any(), Mockito.anyBoolean()))
            .thenReturn(
                ResolvedQaRules(sendQaRuleIds = listOf(10L), promptRuleIds = listOf(10L), requestFacts = emptyList())
            )
        val longText = "A".repeat(20001)

        assertThrows(IllegalArgumentException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>$longText</p>",
                textBody = longText, operatorName = "op", qaRuleIds = listOf(10L)
            )
        }
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `sendManualRichReply archives live unsupported answers after finalize and audit`() {
        val assembly = liveAssembly()
        val eligible = operatorDirectedVersion()
        val assembled = assembledResponse(eligible)
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        Mockito.`when`(trustReplyWorkbenchService.resolveSource(assembly.source)).thenReturn(liveResolvedSource())
        Mockito.`when`(
            unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
                Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
                Mockito.anyList<TrustReplyItemVersion>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(Instant::class.java) ?: Instant.EPOCH
            )
        ).thenReturn(UnsupportedAnswerIndexArchiveResult(UnsupportedAnswerArchiveStatus.SAVED, 1, 0))
        stubSuccessfulSend()

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>${assembled.renderedDraftText}</p>",
            textBody = assembled.renderedDraftText,
            operatorName = " operator-a ",
            templateTextBody = assembled.rawDraftText,
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.SAVED, result.unsupportedAnswerArchiveStatus)
        assertEquals(1, result.unsupportedAnswerArchivedCount)
        val order = Mockito.inOrder(mailDeliveryService, manualReplySendAttemptService, unsupportedAnswerIndexService)
        order.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
        order.verify(manualReplySendAttemptService).finalizeSuccess(anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>"))
        order.verify(manualReplySendAttemptService).recordSendAudit(
            Mockito.eq(100L), Mockito.eq(1L), Mockito.eq(500L),
            anyValue(emptyList<Long>()), Mockito.eq(false),
            anyValue(DeliveredMail("", "")), Mockito.anyString(),
            Mockito.anyString(), eqValue(" operator-a "), anyValue(inbound()),
            anyValue(emptyList<Long>()), anyValue(false), anyValue("")
        )
        order.verify(unsupportedAnswerIndexService).archiveLiveCanonicalVersions(
            Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
            eqValue(listOf(eligible)),
            eqValue("500"),
            eqValue("operator-a"),
            Mockito.any(Instant::class.java) ?: Instant.EPOCH
        )
    }

    // 03a (I-3): the archive-time server re-assembly no longer pre-checks the
    // whole-draft expectedEvidenceSetVersion; a stale expected value is
    // accepted and the per-item revalidation (here satisfied) decides.
    @Test
    fun `sendManualRichReply accepts replay with stale expected evidence version when items revalidate`() {
        val assembly = liveAssembly().copy(expectedEvidenceSetVersion = "stale-aggregate")
        val eligible = operatorDirectedVersion()
        val assembled = assembledResponse(eligible)
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        Mockito.`when`(trustReplyWorkbenchService.resolveSource(assembly.source)).thenReturn(liveResolvedSource())
        Mockito.`when`(
            unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
                Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
                Mockito.anyList<TrustReplyItemVersion>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(Instant::class.java) ?: Instant.EPOCH
            )
        ).thenReturn(UnsupportedAnswerIndexArchiveResult(UnsupportedAnswerArchiveStatus.SAVED, 1, 0))
        stubSuccessfulSend()

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>${assembled.renderedDraftText}</p>",
            textBody = assembled.renderedDraftText,
            operatorName = "op",
            templateTextBody = assembled.rawDraftText,
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.SAVED, result.unsupportedAnswerArchiveStatus)
        Mockito.verify(trustReplyWorkbenchService).verifyAssembly(assembly)
        Mockito.verify(unsupportedAnswerIndexService).archiveLiveCanonicalVersions(
            Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
            eqValue(listOf(eligible)),
            eqValue("500"),
            eqValue("op"),
            Mockito.any(Instant::class.java) ?: Instant.EPOCH
        )
    }

    // 03b (T5/I-4/I-6): the context fingerprint is observational only. An
    // assembly whose locked item was generated under an older context
    // fingerprint (training knowledge / mail history changed after locking)
    // still sends and archives: context never gates the manual-reply path,
    // never enters versionId or the evidence identity, and the re-assembly
    // comparison is unaffected.
    @Test
    fun `sendManualRichReply accepts assembly locked under an older context fingerprint`() {
        val base = liveAssembly()
        val assembly = base.copy(
            lockedItems = base.lockedItems.map { locked -> locked.copy(contextVersion = "context-old") }
        )
        val eligible = operatorDirectedVersion()
        val assembled = assembledResponse(eligible)
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        Mockito.`when`(trustReplyWorkbenchService.resolveSource(assembly.source)).thenReturn(liveResolvedSource())
        Mockito.`when`(
            unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
                Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
                Mockito.anyList<TrustReplyItemVersion>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(Instant::class.java) ?: Instant.EPOCH
            )
        ).thenReturn(UnsupportedAnswerIndexArchiveResult(UnsupportedAnswerArchiveStatus.SAVED, 1, 0))
        stubSuccessfulSend()

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>${assembled.renderedDraftText}</p>",
            textBody = assembled.renderedDraftText,
            operatorName = "op",
            templateTextBody = assembled.rawDraftText,
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.SAVED, result.unsupportedAnswerArchiveStatus)
        Mockito.verify(trustReplyWorkbenchService).verifyAssembly(assembly)
        Mockito.verify(unsupportedAnswerIndexService).archiveLiveCanonicalVersions(
            Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
            eqValue(listOf(eligible)),
            eqValue("500"),
            eqValue("op"),
            Mockito.any(Instant::class.java) ?: Instant.EPOCH
        )
    }

    @Test
    fun `sendManualRichReply without assembly does not archive`() {
        stubSuccessfulSend()
        val result = service.sendManualRichReply(
            inboundProcessingId = 100L, senderAccountCode = null,
            subject = "Re: Hello",
            htmlBody = "<p>Thank you for your patience.</p>",
            textBody = "Thank you for your patience.",
            operatorName = "op"
        )
        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.NOT_APPLICABLE, result.unsupportedAnswerArchiveStatus)
        Mockito.verifyNoInteractions(trustReplyWorkbenchService, unsupportedAnswerIndexService)
    }

    @Test
    fun `sendManualRichReply returns SENT with failed archive when replay mismatches`() {
        val assembly = liveAssembly()
        val assembled = assembledResponse(operatorDirectedVersion())
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        stubSuccessfulSend()

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>${assembled.renderedDraftText}</p>",
            textBody = assembled.renderedDraftText,
            operatorName = "op",
            templateTextBody = "different-template",
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.FAILED, result.unsupportedAnswerArchiveStatus)
        Mockito.verify(trustReplyWorkbenchService).verifyAssembly(assembly)
        Mockito.verifyNoInteractions(unsupportedAnswerIndexService)
    }

    @Test
    fun `sendManualRichReply keeps SENT when archive throws`() {
        val assembly = liveAssembly()
        val assembled = assembledResponse(operatorDirectedVersion())
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        Mockito.`when`(trustReplyWorkbenchService.resolveSource(assembly.source)).thenReturn(liveResolvedSource())
        Mockito.`when`(
            unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
                Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
                Mockito.anyList<TrustReplyItemVersion>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(Instant::class.java) ?: Instant.EPOCH
            )
        ).thenThrow(IllegalStateException("es unavailable"))
        stubSuccessfulSend()

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>${assembled.renderedDraftText}</p>",
            textBody = assembled.renderedDraftText,
            operatorName = "op",
            templateTextBody = assembled.rawDraftText,
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.FAILED, result.unsupportedAnswerArchiveStatus)
        assertEquals(1, result.unsupportedAnswerArchiveFailedCount)
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).finalizeFailure(
            anyValue(sendPayload()),
            Mockito.anyLong(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString()
        )
    }

    @Test
    fun `sendManualRichReply dedup archives without smtp`() {
        val assembly = liveAssembly()
        val eligible = operatorDirectedVersion()
        val assembled = assembledResponse(eligible)
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        Mockito.`when`(trustReplyWorkbenchService.resolveSource(assembly.source)).thenReturn(liveResolvedSource())
        Mockito.`when`(
            unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
                Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
                Mockito.anyList<TrustReplyItemVersion>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(Instant::class.java) ?: Instant.EPOCH
            )
        ).thenReturn(UnsupportedAnswerIndexArchiveResult(UnsupportedAnswerArchiveStatus.SAVED, 1, 0))
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.DEDUP_SENT
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload()))).thenReturn(claim)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(
            MailRecord(
                id = 700L,
                expertContactId = 1L,
                direction = "OUTBOUND",
                mailType = "MANUAL_RICH_REPLY",
                messageId = "<manual-rich-abc@weibo.com>",
                inReplyTo = null,
                subject = "Re: Test",
                body = "body",
                matchedQaRuleId = null,
                sendStatus = "SENT",
                receivedAt = null,
                sentAt = LocalDateTime.now()
            )
        )

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>${assembled.renderedDraftText}</p>",
            textBody = assembled.renderedDraftText,
            operatorName = "op",
            templateTextBody = assembled.rawDraftText,
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.SAVED, result.unsupportedAnswerArchiveStatus)
        Mockito.verifyNoInteractions(mailDeliveryService)
        Mockito.verify(unsupportedAnswerIndexService).archiveLiveCanonicalVersions(
            Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
            eqValue(listOf(eligible)),
            eqValue("700"),
            eqValue("op"),
            Mockito.any(Instant::class.java) ?: Instant.EPOCH
        )
    }

    @Test
    fun `sendManualRichReply delivery failure does not archive`() {
        val assembly = liveAssembly()
        val assembled = assembledResponse(operatorDirectedVersion())
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload()))).thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(
            DeliveredMail(
                messageId = "<manual-rich-abc@weibo.com>",
                status = "FAILED",
                errorCategory = com.weibo.talentintroduction.mail.domain.SmtpErrorCategory.TRANSIENT,
                smtpResponseCode = 451,
                errorDetail = "try later"
            )
        )
        Mockito.`when`(
            manualReplySendAttemptService.finalizeFailure(
                anyValue(sendPayload()), Mockito.eq(1L), Mockito.anyString(), Mockito.anyString(), Mockito.any()
            )
        ).thenReturn(501L)

        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.", operatorName = "op",
                trustReplyAssembly = assembly
            )
        }
        Mockito.verify(trustReplyWorkbenchService).operatorAuthorizedActionsFromVerifiedVersions(assembled.itemVersions)
        Mockito.verifyNoInteractions(unsupportedAnswerIndexService)
    }

    // 03 (I-1): assembly source 与本次来信不符时，在 claim 前稳定 422 拒绝（不再
    // 是「发送成功后归档失败」）；source 校验先于 verifyAssembly，服务零交互。
    @Test
    fun `sendManualRichReply returns SENT with failed archive when source mismatches`() {
        val assembly = liveAssembly().copy(
            source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 99L)
        )
        val assembled = assembledResponse(operatorDirectedVersion())

        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>${assembled.renderedDraftText}</p>",
                textBody = assembled.renderedDraftText,
                operatorName = "op",
                templateTextBody = assembled.rawDraftText,
                trustReplyAssembly = assembly
            )
        }

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status)
        Mockito.verifyNoInteractions(
            trustReplyWorkbenchService,
            manualReplySendAttemptService,
            mailDeliveryService,
            unsupportedAnswerIndexService
        )
    }

    // 03 (I-1/I-7): stale assembly 在 claim 前稳定 409 失败，不烧 attempt、不归档。
    @Test
    fun `sendManualRichReply rejects stale assembly replay before claim`() {
        val assembly = liveAssembly()
        val assembled = assembledResponse(operatorDirectedVersion())
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly))
            .thenThrow(TrustReplyWorkbenchException(HttpStatus.CONFLICT, "TRUST_REPLY_SOURCE_STALE"))

        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>${assembled.renderedDraftText}</p>",
                textBody = assembled.renderedDraftText,
                operatorName = "op",
                templateTextBody = assembled.rawDraftText,
                trustReplyAssembly = assembly
            )
        }

        assertEquals(HttpStatus.CONFLICT, ex.status)
        Mockito.verify(trustReplyWorkbenchService).verifyAssembly(assembly)
        Mockito.verifyNoInteractions(
            manualReplySendAttemptService,
            mailDeliveryService,
            unsupportedAnswerIndexService
        )
    }

    @Test
    fun `sendManualRichReply returns SENT with failed archive when rendered text mismatches`() {
        val assembly = liveAssembly()
        val assembled = assembledResponse(operatorDirectedVersion())
            .copy(renderedDraftText = "Authoritative rendered mismatch")
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        stubSuccessfulSend()

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>We will follow up next week.</p>",
            textBody = "We will follow up next week.",
            operatorName = "op",
            templateTextBody = assembled.rawDraftText,
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.FAILED, result.unsupportedAnswerArchiveStatus)
        Mockito.verify(trustReplyWorkbenchService).verifyAssembly(assembly)
        Mockito.verifyNoInteractions(unsupportedAnswerIndexService)
    }

    @Test
    fun `sendManualRichReply returns SENT with not applicable when no eligible operator directed versions`() {
        val assembly = liveAssembly()
        val acknowledgement = operatorDirectedVersion().copy(
            handling = TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
            answerText = "We will check and follow up."
        )
        val assembled = assembledResponse(acknowledgement)
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        stubSuccessfulSend()

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>${assembled.renderedDraftText}</p>",
            textBody = assembled.renderedDraftText,
            operatorName = "op",
            templateTextBody = assembled.rawDraftText,
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.NOT_APPLICABLE, result.unsupportedAnswerArchiveStatus)
        Mockito.verify(trustReplyWorkbenchService).verifyAssembly(assembly)
        Mockito.verifyNoInteractions(unsupportedAnswerIndexService)
    }

    @Test
    fun `sendManualRichReply with assembly does not archive on delivery unknown claim`() {
        val assembly = liveAssembly()
        val assembled = assembledResponse(operatorDirectedVersion())
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.UNKNOWN
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload()))).thenReturn(claim)

        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>",
                textBody = "Test", operatorName = "op",
                trustReplyAssembly = assembly
            )
        }
        Mockito.verify(trustReplyWorkbenchService).operatorAuthorizedActionsFromVerifiedVersions(assembled.itemVersions)
        Mockito.verifyNoInteractions(unsupportedAnswerIndexService, mailDeliveryService)
    }

    @Test
    fun `sendManualRichReply with assembly does not archive on in progress claim`() {
        val assembly = liveAssembly()
        val assembled = assembledResponse(operatorDirectedVersion())
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.IN_PROGRESS
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload()))).thenReturn(claim)

        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>",
                textBody = "Test", operatorName = "op",
                trustReplyAssembly = assembly
            )
        }
        Mockito.verify(trustReplyWorkbenchService).operatorAuthorizedActionsFromVerifiedVersions(assembled.itemVersions)
        Mockito.verifyNoInteractions(unsupportedAnswerIndexService, mailDeliveryService)
    }

    @Test
    fun `sendManualRichReply with assembly does not archive on permanent failure claim`() {
        val assembly = liveAssembly()
        val assembled = assembledResponse(operatorDirectedVersion())
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.PERMANENT_FAILED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload()))).thenReturn(claim)

        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>",
                textBody = "Test", operatorName = "op",
                trustReplyAssembly = assembly
            )
        }
        Mockito.verify(trustReplyWorkbenchService).operatorAuthorizedActionsFromVerifiedVersions(assembled.itemVersions)
        Mockito.verifyNoInteractions(unsupportedAnswerIndexService, mailDeliveryService)
    }

    @Test
    fun `sendManualRichReply with assembly does not archive when finalize success fails`() {
        val assembly = liveAssembly()
        val assembled = assembledResponse(operatorDirectedVersion())
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", null, true))
            .thenReturn(ResolvedQaRules(emptyList(), emptyList(), emptyList()))
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload()))).thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-abc@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>"))
        ).thenThrow(IllegalStateException("finalize failed"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeFailure(
                anyValue(sendPayload()), Mockito.eq(1L), Mockito.anyString(), Mockito.anyString(), Mockito.any()
            )
        ).thenReturn(501L)

        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>${assembled.renderedDraftText}</p>",
                textBody = assembled.renderedDraftText,
                operatorName = "op",
                templateTextBody = assembled.rawDraftText,
                trustReplyAssembly = assembly
            )
        }
        Mockito.verify(trustReplyWorkbenchService).operatorAuthorizedActionsFromVerifiedVersions(assembled.itemVersions)
        Mockito.verifyNoInteractions(unsupportedAnswerIndexService)
    }

    // 03 (I-2/I-4/I-6): 工作台 matrix 含 intent mismatch 事实（20L）时，发送入口
    // 不再对 explicit ids 调 legacy select()；verified canonical ids（含 mismatch
    // 事实）原样进入 safety 与 SendPayload；发送后不再二次 assemble。
    @Test
    fun `sendManualRichReply with verified assembly sends canonical facts without legacy reselect`() {
        val assembly = liveAssembly()
        val eligible = operatorDirectedVersion()
        val assembled = assembledResponse(eligible).copy(canonicalFactIds = listOf(10L, 20L))
        val mismatchSelection = ResolvedQaRules(
            sendQaRuleIds = listOf(10L, 20L),
            promptRuleIds = listOf(10L, 20L),
            requestFacts = listOf(
                RequestFactItem(
                    index = 1,
                    requestText = "Can I work remotely?",
                    factRuleIds = listOf(10L, 20L),
                    status = RequestGroundingStatus.GROUNDED,
                    intentMatchedFactRuleIds = listOf(10L),
                    intentMismatchFactRuleIds = listOf(20L)
                )
            )
        )
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly))
            .thenReturn(VerifiedTrustReplyAssembly(response = assembled, selection = mismatchSelection))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(qaRule(10L)))
        Mockito.`when`(qaRuleRepository.findById(20L)).thenReturn(Optional.of(qaRule(20L)))
        // serverSuggestedFactIds 审计对照仍走 select(null)，但不参与 canonical facts。
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", null, true))
            .thenReturn(ResolvedQaRules(sendQaRuleIds = listOf(10L), promptRuleIds = listOf(10L), requestFacts = emptyList()))
        // 03 (阶段 4): 归档复用发送前已验证结果（本测试顺带钉住该路径）。
        Mockito.`when`(trustReplyWorkbenchService.resolveSource(assembly.source)).thenReturn(liveResolvedSource())
        Mockito.`when`(
            unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
                Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
                Mockito.anyList<TrustReplyItemVersion>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(Instant::class.java) ?: Instant.EPOCH
            )
        ).thenReturn(UnsupportedAnswerIndexArchiveResult(UnsupportedAnswerArchiveStatus.SAVED, 1, 0))

        val payloadHolder = mutableListOf<ManualReplySendAttemptService.SendPayload>()
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.doAnswer { invocation ->
            payloadHolder += invocation.getArgument<ManualReplySendAttemptService.SendPayload>(0)
            claim
        }.`when`(manualReplySendAttemptService).prepareAndClaim(anyValue(sendPayload()))
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-abc@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(
                anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>")
            )
        ).thenReturn(500L)

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>${assembled.renderedDraftText}</p>",
            textBody = assembled.renderedDraftText,
            operatorName = "op",
            qaRuleIds = listOf(10L, 20L),
            templateTextBody = assembled.rawDraftText,
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        // I-2/I-6: canonical ids 原样进入 SendPayload，含 intent mismatch 事实。
        assertEquals(listOf(10L, 20L), payloadHolder.single().canonicalQaRuleIds)
        assertEquals(10L, payloadHolder.single().primaryRuleId)
        // 03: 发送入口不调 legacy select(explicitIds)；只做审计对照的 select(null)。
        Mockito.verify(qaFactSelectionService, Mockito.never())
            .select("Can I work remotely?", listOf(10L, 20L), true)
        Mockito.verify(qaFactSelectionService).select("Can I work remotely?", null, true)
        // 03: 删除发送后二次 assemble —— 只调用一次 verifyAssembly。
        Mockito.verify(trustReplyWorkbenchService).verifyAssembly(assembly)
        Mockito.verify(trustReplyWorkbenchService, Mockito.never()).assemble(anyValue(liveAssembly()))
        // I-5: 授权来自已验证 versions。
        Mockito.verify(trustReplyWorkbenchService).operatorAuthorizedActionsFromVerifiedVersions(assembled.itemVersions)
        Mockito.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    // 03 (I-2): 客户端 qaRuleIds 任一缺失/增加/乱序，都在 suppression 与 claim 之前
    // 稳定 422 失败；绝不静默采纳客户端 ids。
    @Test
    fun `sendManualRichReply rejects client qaRuleIds not equal to verified canonical before claim`() {
        val assembly = liveAssembly()
        val assembled = assembledResponse(operatorDirectedVersion()).copy(canonicalFactIds = listOf(10L, 20L))
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))

        val missing = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test",
                operatorName = "op", qaRuleIds = listOf(10L), trustReplyAssembly = assembly
            )
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, missing.status)

        val reordered = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test",
                operatorName = "op", qaRuleIds = listOf(20L, 10L), trustReplyAssembly = assembly
            )
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, reordered.status)

        val extra = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test",
                operatorName = "op", qaRuleIds = listOf(10L, 20L, 30L), trustReplyAssembly = assembly
            )
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, extra.status)

        val absent = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test",
                operatorName = "op", qaRuleIds = null, trustReplyAssembly = assembly
            )
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, absent.status)

        // I-1: 全部失败发生在 suppression 与 claim 之前，未产生任何发送副作用。
        Mockito.verify(trustReplyWorkbenchService, Mockito.times(4)).verifyAssembly(assembly)
        Mockito.verifyNoInteractions(
            emailSuppressionService,
            manualReplySendAttemptService,
            mailDeliveryService
        )
    }

    // 03 (I-1/I-7): tampered assembly（版本不匹配等）在 claim 前稳定 422 失败，不烧 attempt。
    @Test
    fun `sendManualRichReply rejects tampered assembly versions before claim`() {
        val assembly = liveAssembly()
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly))
            .thenThrow(TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_ITEM_VERSION_INVALID"))

        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test",
                operatorName = "op", trustReplyAssembly = assembly
            )
        }

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status)
        Mockito.verifyNoInteractions(manualReplySendAttemptService, mailDeliveryService, emailSuppressionService)
    }

    // 03 (I-4): 可信 assembly 只替换事实选择数据源 —— 渲染后正文的高风险 claim 校验
    // 仍照常触发并需要原有确认；确认后发送成功，safety 全程不调 select(explicitIds)。
    @Test
    fun `sendManualRichReply with assembly still runs full safety and requires confirmation`() {
        val assembly = liveAssembly()
        val assembled = assembledResponse(operatorDirectedVersion()).copy(canonicalFactIds = listOf(10L))
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(qaRule(10L)))
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", null, true))
            .thenReturn(ResolvedQaRules(sendQaRuleIds = listOf(10L), promptRuleIds = listOf(10L), requestFacts = emptyList()))

        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>We guarantee 10 million RMB with no fees.</p>",
                textBody = "We guarantee 10 million RMB with no fees.",
                operatorName = "op", qaRuleIds = listOf(10L), trustReplyAssembly = assembly
            )
        }
        assertTrue(ex.findings.isNotEmpty())
        Mockito.verifyNoInteractions(mailDeliveryService, manualReplySendAttemptService)

        stubSuccessfulSend()
        val result = service.sendManualRichReply(
            inboundProcessingId = 100L, senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>We guarantee 10 million RMB with no fees.</p>",
            textBody = "We guarantee 10 million RMB with no fees.",
            operatorName = "op", qaRuleIds = listOf(10L), trustReplyAssembly = assembly,
            safetyWarningConfirmed = true
        )

        assertEquals("SENT", result.sendStatus)
        Mockito.verify(qaFactSelectionService, Mockito.never())
            .select("Can I work remotely?", listOf(10L), true)
        Mockito.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    // 03 (I-5): operator action 授权只来自通过 verifyAssembly 的已验证 versions；
    // 客户端 lockedItems 声称的索要材料动作（旧实现会因此授权）不再生效，fail-closed。
    @Test
    fun `sendManualRichReply derives operator authorization from verified versions not client locked items`() {
        val compliant =
            "If you would like to proceed, you are welcome to share your CV at your convenience " +
                "so that we can carry out an initial eligibility review."
        val assembly = liveAssembly().copy(
            lockedItems = listOf(liveAssembly().lockedItems.single().copy(answerText = compliant))
        )
        val assembled = assembledResponse(operatorDirectedVersion())
        // 未 stub operatorAuthorizedActions(versions) → 默认空集（fail-closed）。
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))

        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>$compliant</p>",
                textBody = compliant,
                operatorName = "op",
                trustReplyAssembly = assembly
            )
        }

        assertTrue(ex.findings.any { it.code == AiReplyActionPolicy.CODE_ACTION_MATERIALS_NOT_ALLOWED })
        Mockito.verify(trustReplyWorkbenchService).operatorAuthorizedActionsFromVerifiedVersions(assembled.itemVersions)
        Mockito.verify(trustReplyWorkbenchService, Mockito.never())
            .operatorAuthorizedActions(assembly.lockedItems)
        Mockito.verifyNoInteractions(mailDeliveryService, manualReplySendAttemptService)
    }

    private fun stubSuccessfulSend() {
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", null, true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = emptyList(),
                    promptRuleIds = emptyList(),
                    requestFacts = emptyList()
                )
            )
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-abc@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>"))
        ).thenReturn(500L)
    }

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

    // 03: 把服务端已验证结果包装为 VerifiedTrustReplyAssembly；selection 用 canonical
    // ids 直接构造（空 requestFacts），assembly 路径不再触发 legacy select()。
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

    private fun liveResolvedSource() = com.weibo.talentintroduction.llm.service.ResolvedTrustReplySource(
        source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100L),
        contact = contact,
        inboundText = "Can I work remotely?",
        subject = "Question",
        messageId = "in-1",
        senderAccountCode = "sender-1",
        profileText = "",
        mailHistory = "",
        contextWarnings = emptyList(),
        researchProfileSufficient = true,
        sourceVersion = "live-v1"
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

    private fun sendPayload() = ManualReplySendAttemptService.SendPayload(
        orcidId = contact.orcidId,
        contactId = requireNotNull(contact.id),
        inboundProcessingId = 100L,
        accountCode = "sender-1",
        normalizedRecipient = contact.expertEmail,
        subject = "Re: Test",
        finalText = "Test",
        finalHtml = "<p>Test</p>",
        inReplyTo = "in-1",
        canonicalQaRuleIds = emptyList(),
        primaryRuleId = null
    )

    private fun composedMail() = ComposedMail(
        to = contact.expertEmail,
        subject = "Re: Test",
        body = "<p>Test</p>",
        html = true,
        text = "Test",
        messageId = "<manual-rich-abc@weibo.com>"
    )

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

}
