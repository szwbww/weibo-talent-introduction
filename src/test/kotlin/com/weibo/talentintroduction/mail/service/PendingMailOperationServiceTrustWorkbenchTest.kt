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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.transaction.support.TransactionSynchronizationManager
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
        mailVariableService
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
    fun `sendManualRichReply rejects unbacked high risk claim in final text`() {
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

        assertThrows(IllegalArgumentException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>We guarantee 10 million RMB with no fees.</p>",
                textBody = "We guarantee 10 million RMB with no fees.",
                operatorName = "op",
                qaRuleIds = listOf(10L),
                suggestedRuleIds = listOf(99L)
            )
        }
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `sendManualRichReply writes canonical fact audit not client suggested ids`() {
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
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer {
            it.getArgument<MailRecord>(0).copy(id = 500L)
        }
        Mockito.`when`(
            mailDeliveryService.send(
                anyValue(senderAccount()),
                anyValue(ComposedMail("", "", ""))
            )
        ).thenReturn(DeliveredMail(messageId = "out-1", status = "SUCCESS"))

        service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.",
            operatorName = "op",
            qaRuleIds = listOf(10L),
            suggestedRuleIds = listOf(99L)
        )

        val logCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        Mockito.verify(operatorActionLogService).record(
            eqValue("INBOUND_MAIL_PROCESSING"),
            eqValue(100L),
            eqValue(OperatorActionType.SEND_MANUAL_COMPOSED_REPLY),
            eqValue(1L),
            eqValue(100L),
            anyValue(null),
            logCaptor.capture(),
            eqValue("op"),
            anyValue(""),
            anyValue(null)
        )
        assertEquals(listOf(10L), logCaptor.value!!["canonicalFactIds"])
        assertEquals(listOf(10L), logCaptor.value!!["serverSuggestedFactIds"])
    }

    @Test
    fun `sendManualRichReply defers audit until transaction afterCommit when synchronization active`() {
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
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer {
            it.getArgument<MailRecord>(0).copy(id = 500L)
        }
        Mockito.`when`(
            mailDeliveryService.send(
                anyValue(senderAccount()),
                anyValue(ComposedMail("", "", ""))
            )
        ).thenReturn(DeliveredMail(messageId = "out-1", status = "SUCCESS"))

        TransactionSynchronizationManager.initSynchronization()
        try {
            val result = service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.",
                operatorName = "op",
                qaRuleIds = listOf(10L)
            )

            assertEquals("SUCCESS", result.sendStatus)
            Mockito.verifyNoInteractions(operatorActionLogService)
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            Mockito.verify(operatorActionLogService).record(
                eqValue("INBOUND_MAIL_PROCESSING"),
                eqValue(100L),
                eqValue(OperatorActionType.SEND_MANUAL_COMPOSED_REPLY),
                eqValue(1L),
                eqValue(100L),
                anyValue(null),
                anyValue(null),
                eqValue("op"),
                anyValue(""),
                anyValue(null)
            )
        } finally {
            TransactionSynchronizationManager.clear()
        }
    }

    @Test
    fun `sendManualRichReply succeeds when audit logging fails`() {
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
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer {
            it.getArgument<MailRecord>(0).copy(id = 500L)
        }
        Mockito.`when`(
            mailDeliveryService.send(
                anyValue(senderAccount()),
                anyValue(ComposedMail("", "", ""))
            )
        ).thenReturn(DeliveredMail(messageId = "out-1", status = "SUCCESS"))
        Mockito.doThrow(RuntimeException("audit down"))
            .`when`(operatorActionLogService)
            .record(
                eqValue("INBOUND_MAIL_PROCESSING"),
                eqValue(100L),
                eqValue(OperatorActionType.SEND_MANUAL_COMPOSED_REPLY),
                eqValue(1L),
                eqValue(100L),
                anyValue(null),
                anyValue(null),
                eqValue("op"),
                anyValue(""),
                anyValue(null)
            )

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.",
            operatorName = "op",
            qaRuleIds = listOf(10L)
        )

        assertEquals("SUCCESS", result.sendStatus)
        Mockito.verify(mailDeliveryService).send(
            anyValue(senderAccount()),
            anyValue(ComposedMail("", "", ""))
        )
        Mockito.verify(mailRecordRepository).save(Mockito.any(MailRecord::class.java))
        Mockito.verify(mailRecordQaRuleRepository).save(Mockito.any())
        Mockito.verify(operatorActionLogService).record(
            eqValue("INBOUND_MAIL_PROCESSING"),
            eqValue(100L),
            eqValue(OperatorActionType.SEND_MANUAL_COMPOSED_REPLY),
            eqValue(1L),
            eqValue(100L),
            anyValue(null),
            anyValue(null),
            eqValue("op"),
            anyValue(""),
            anyValue(null)
        )
    }

    @Test
    fun `sendManualRichReply succeeds when deferred audit logging fails after commit`() {
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
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer {
            it.getArgument<MailRecord>(0).copy(id = 500L)
        }
        Mockito.`when`(
            mailDeliveryService.send(
                anyValue(senderAccount()),
                anyValue(ComposedMail("", "", ""))
            )
        ).thenReturn(DeliveredMail(messageId = "out-1", status = "SUCCESS"))
        Mockito.doThrow(RuntimeException("audit down"))
            .`when`(operatorActionLogService)
            .record(
                eqValue("INBOUND_MAIL_PROCESSING"),
                eqValue(100L),
                eqValue(OperatorActionType.SEND_MANUAL_COMPOSED_REPLY),
                eqValue(1L),
                eqValue(100L),
                anyValue(null),
                anyValue(null),
                eqValue("op"),
                anyValue(""),
                anyValue(null)
            )

        TransactionSynchronizationManager.initSynchronization()
        try {
            val result = service.sendManualRichReply(
                inboundProcessingId = 100L,
                senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>Remote work is possible.</p>",
                textBody = "Remote work is possible.",
                operatorName = "op",
                qaRuleIds = listOf(10L)
            )

            assertEquals("SUCCESS", result.sendStatus)
            Mockito.verify(mailDeliveryService).send(
                anyValue(senderAccount()),
                anyValue(ComposedMail("", "", ""))
            )
            Mockito.verify(mailRecordRepository).save(Mockito.any(MailRecord::class.java))
            Mockito.verify(mailRecordQaRuleRepository).save(Mockito.any())
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        } finally {
            TransactionSynchronizationManager.clear()
        }
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

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue
}
