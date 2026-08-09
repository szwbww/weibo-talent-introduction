package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.BatchOutcomeReasonCodes
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.OutcomeAccumulator
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.config.UnsubscribeProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.template.service.MailComposeTemplateDetail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

/**
 * Gate behaviour of the manual-compose path (I-1, I-2, I-3, I-5, I-6).
 *
 * The service under test is wired with a REAL MailVariableService (mocked ES lookup
 * + real unsubscribe token service) so the variables map is genuinely produced by
 * MailVariableService.buildVariables — no sender-only local map (I-1).
 */
class ManualExpertMailServiceGateTest {
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailSenderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val mailDeliveryService = Mockito.mock(MailDeliveryService::class.java)
    private val mailComposeTemplateService = Mockito.mock(MailComposeTemplateService::class.java)
    private val mailRecordQaRuleRepository = Mockito.mock(MailRecordQaRuleRepository::class.java)
    private val conversationStateService = Mockito.mock(ConversationStateService::class.java)
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)

    private val mailVariableService = MailVariableService(
        expertSearchService,
        mailComposeTemplateService,
        UnsubscribeTokenService(UnsubscribeProperties(baseUrl = "https://example.com", secret = "secret"))
    )

    private val service = ManualExpertMailService(
        expertContactRepository,
        mailRecordRepository,
        mailRecordQaRuleRepository,
        mailSenderAccountService,
        mailSenderAccountRepository,
        mailDeliveryService,
        mailComposeTemplateService,
        MailContentService(),
        conversationStateService,
        personalizationGateService = PersonalizationGateService(),
        mailVariableService = mailVariableService
    )

    private val contact = ExpertContact(
        id = 1,
        campaignId = 1,
        orcidId = "0000-0001-2345-6789",
        expertEmail = "expert@test.com",
        expertName = "Expert",
        currentStatus = "INTRO_SENT",
        operatorStatus = "CONTACTED",
        currentIndexLevel = "CANDIDATE"
    )

    private val expert = ExpertProfile(
        orcidId = "0000-0001-2345-6789",
        email = "expert@test.com",
        givenNames = "Ada",
        familyNames = "Lovelace",
        country = "UK",
        keyword = "computing",
        employment = "Professor",
        researchFields = "Machine Learning, Data Mining, NLP",
        institution = "Oxford"
    )

    private val account = MailSenderAccount(
        accountCode = "sender",
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
        imapPassword = "p"
    )

    private val stubMailRecord = MailRecord(
        expertContactId = 0,
        direction = "OUTBOUND",
        mailType = "stub",
        messageId = null,
        inReplyTo = null,
        subject = null,
        body = null,
        matchedQaRuleId = null,
        sendStatus = null,
        receivedAt = null,
        sentAt = null
    )

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun stubTemplate(enabled: Boolean = true) {
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-2345-6789", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)
        Mockito.`when`(mailComposeTemplateService.getById(10L)).thenReturn(
            MailComposeTemplateDetail(
                id = 10,
                templateCode = "INTRODUCTION",
                templateName = "Introduction",
                subject = "Intro Subject",
                description = null,
                mailType = "INTRODUCTION",
                subjectVariants = null,
                enabled = enabled,
                blocks = emptyList(),
                createdAt = null,
                updatedAt = null
            )
        )
        Mockito.`when`(
            mailComposeTemplateService.renderWithVariables(Mockito.anyString(), Mockito.anyMap())
        ).thenAnswer { invocation ->
            val text = invocation.getArgument<String>(0)
            val vars = invocation.getArgument<Map<String, String>>(1)
            vars.entries.fold(text) { acc, (key, value) -> acc.replace("\${$key}", value) }
        }
    }

    private fun stubSendSuccess() {
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(DeliveredMail(messageId = "msg-1", status = "SUCCESS"))
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 100) }
        Mockito.`when`(conversationStateService.transition(
            anyValue(contact),
            eqValue(ConversationStatus.INTRO_SENT),
            eqValue("MANUAL_MAIL_INTRODUCTION"),
            eqValue("MANUAL_MAIL"),
            anyValue(LocalDateTime.now()),
            anyValue { contact }
        )).thenAnswer { invocation ->
            val base = invocation.getArgument<ExpertContact>(0)
            val mutator = invocation.getArgument<(ExpertContact) -> ExpertContact>(5)
            mutator(base)
        }
        Mockito.`when`(mailSenderAccountRepository.save(anyValue(account)))
            .thenAnswer { it.getArgument<MailSenderAccount>(0) }
    }

    // ── I-1: variables come from MailVariableService.buildVariables and reach the body ──

    @Test
    fun `composeComposeTemplate injects real unsubscribe url and primary research field`() {
        stubTemplate()
        stubSendSuccess()
        val expectedSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)
        Mockito.`when`(
            mailComposeTemplateService.render(
                eqValue(10L),
                anyValue(emptyMap<String, String>()),
                eqValue(expectedSeed)
            )
        ).thenAnswer { invocation ->
            val vars = invocation.getArgument<Map<String, String>>(1)
            ComposeTemplateRenderResult(
                subject = "Subject: ${vars["primaryResearchField"]}",
                body = "Unsubscribe: ${vars["unsubscribeUrl"]}\nField: ${vars["primaryResearchField"]}",
                mailType = "INTRODUCTION"
            )
        }

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        val captor = ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(mailDeliveryService).send(
            eqValue(account),
            captor.capture() ?: ComposedMail("stub", "stub", "stub")
        )
        // real signed unsubscribe link (I-1)
        assertTrue(captor.value.text!!.startsWith("Unsubscribe: https://example.com/u/unsubscribe?token="))
        assertTrue(captor.value.body!!.startsWith("Unsubscribe: https://example.com/u/unsubscribe?token="))
        // derived primary research field = first segment of researchFields (I-7)
        assertTrue(captor.value.text!!.contains("Field: Machine Learning"))
        assertTrue(captor.value.subject.contains("Machine Learning"))
        assertTrue(!captor.value.text!!.contains("\${"))
    }

    // ── I-2: placeholder residue aborts before SMTP ──

    @Test
    fun `placeholder residue prevents mailDeliveryService send`() {
        stubTemplate()
        val expectedSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)
        Mockito.`when`(
            mailComposeTemplateService.render(
                eqValue(10L),
                anyValue(emptyMap<String, String>()),
                eqValue(expectedSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Intro Subject",
                body = "Body with \${unresolved} token",
                mailType = "INTRODUCTION"
            )
        )

        val ex = assertThrows<PlaceholderResidueException> {
            service.sendManualMail(
                1,
                ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
            )
        }

        assertTrue(ex.message!!.contains("\${unresolved}"))
        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )
    }

    // ── I-3 / I-5: required variable falling back blocks the send with the exact keys ──

    @Test
    fun `gate blocks send when required key falls back to default`() {
        val sparseExpert = expert.copy(recentWorkTitles = null)
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-2345-6789", ExpertIndexLevel.CANDIDATE))
            .thenReturn(sparseExpert)
        stubTemplate()
        val expectedSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)
        Mockito.`when`(
            mailComposeTemplateService.render(
                eqValue(10L),
                anyValue(emptyMap<String, String>()),
                eqValue(expectedSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Intro Subject",
                body = "Body",
                mailType = "INTRODUCTION",
                rawTexts = listOf("Subject: \${recentWorkTitle|Untitled}")
            )
        )
        Mockito.`when`(mailComposeTemplateService.effectiveRequiredKeys(10L))
            .thenReturn(listOf("recentWorkTitle"))

        val ex = assertThrows<PersonalizationGateException> {
            service.sendManualMail(
                1,
                ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
            )
        }

        assertEquals(listOf("recentWorkTitle"), ex.missingKeys)
        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )
        Mockito.verify(mailRecordRepository, Mockito.never()).save(anyValue(stubMailRecord))
    }

    // ── I-4: no required keys configured → gate does not interfere ──

    @Test
    fun `send succeeds when effectiveRequiredKeys is empty`() {
        stubTemplate()
        stubSendSuccess()
        val expectedSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)
        Mockito.`when`(
            mailComposeTemplateService.render(
                eqValue(10L),
                anyValue(emptyMap<String, String>()),
                eqValue(expectedSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Intro Subject",
                body = "Intro Body",
                mailType = "INTRODUCTION"
            )
        )
        Mockito.`when`(mailComposeTemplateService.effectiveRequiredKeys(10L)).thenReturn(emptyList())

        val result = service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        assertEquals("SUCCESS", result.sendStatus)
        Mockito.verify(mailDeliveryService).send(
            eqValue(account),
            anyValue(ComposedMail("stub", "stub", "stub"))
        )
    }

    // ── I-6: gate hits are recorded as skipped, never as failure ──

    @Test
    fun `gate hit records PERSONALIZATION_INCOMPLETE as skipped without failure`() {
        val accumulator = OutcomeAccumulator(target = 3)
        accumulator.recordSkipped(
            BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE,
            "个性化字段缺失（recentWorkTitle）：expert@test.com"
        )

        assertTrue(accumulator.skippedReasonsMap().containsKey(BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE))
        assertEquals(1, accumulator.skippedReasonsMap()[BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE])
        assertEquals(0, accumulator.failure)
        assertEquals(1, accumulator.skipped)
        assertEquals(0, accumulator.success)
        assertEquals("个性化字段缺失", BatchOutcomeReasonCodes.label(BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE))
    }
}
