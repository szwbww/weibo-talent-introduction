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
import com.weibo.talentintroduction.llm.service.AiReplyContext
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyDraftReadiness
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyHighRiskClaimValidator
import com.weibo.talentintroduction.llm.service.QaFactSelectionService
import com.weibo.talentintroduction.llm.service.RequestFactItem
import com.weibo.talentintroduction.llm.service.RequestGroundingStatus
import com.weibo.talentintroduction.llm.service.ResolvedQaRules
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
import org.mockito.Mockito
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
        manualReplySendAttemptService
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

        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.", operatorName = "op",
                qaRuleIds = listOf(10L)
            )
        }
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

        assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L, senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>We guarantee 10 million RMB with no fees.</p>",
                textBody = "We guarantee 10 million RMB with no fees.",
                operatorName = "op", qaRuleIds = listOf(10L)
            )
        }
        Mockito.verifyNoInteractions(mailDeliveryService, manualReplySendAttemptService)
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
