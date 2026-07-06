package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.template.domain.MailComposeTemplate
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.template.service.MailComposeTemplateDetail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class ManualExpertMailServiceTest {
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailSenderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val mailDeliveryService = Mockito.mock(MailDeliveryService::class.java)
    private val mailComposeTemplateService = Mockito.mock(MailComposeTemplateService::class.java)
    private val mailRecordQaRuleRepository = Mockito.mock(MailRecordQaRuleRepository::class.java)
    private val conversationStateService = Mockito.mock(ConversationStateService::class.java)
    private val mailContentService = MailContentService()
    private val service = ManualExpertMailService(
        expertContactRepository,
        mailRecordRepository,
        mailRecordQaRuleRepository,
        mailSenderAccountService,
        mailSenderAccountRepository,
        mailDeliveryService,
        mailComposeTemplateService,
        mailContentService,
        conversationStateService
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

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    private fun stubAccount(
        todaySentCount: Int = 0,
        dailySendLimit: Int = 100
    ) = MailSenderAccount(
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
        imapPassword = "p",
        dailySendLimit = dailySendLimit,
        todaySentCount = todaySentCount
    )

    private fun stubTemplateSend(account: MailSenderAccount) {
        val delivered = DeliveredMail(messageId = "msg-1", status = "SUCCESS")

        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
        Mockito.`when`(mailComposeTemplateService.getById(10L)).thenReturn(
            MailComposeTemplateDetail(
                id = 10,
                templateCode = "INTRODUCTION",
                templateName = "Introduction",
                subject = "Intro Subject",
                description = null,
                mailType = "INTRODUCTION",
                enabled = true,
                blocks = emptyList(),
                createdAt = null,
                updatedAt = null
            )
        )
        Mockito.`when`(
            mailComposeTemplateService.render(
                eqValue(10L),
                anyValue(emptyMap<String, String>()),
                Mockito.anyInt()
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Intro Subject",
                body = "Intro Body",
                mailType = "INTRODUCTION"
            )
        )
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
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

    @Test
    fun `sendManualMail does not increment todaySentCount`() {
        val account = stubAccount(todaySentCount = 5)
        stubTemplateSend(account)

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        val captor = ArgumentCaptor.forClass(MailSenderAccount::class.java)
        Mockito.verify(mailSenderAccountRepository).save(captor.capture())
        assertEquals(5, captor.value.todaySentCount)
        assertNotNull(captor.value.lastSentAt)
    }

    @Test
    fun `sendManualMail succeeds when only enabled account is at daily limit`() {
        val account = stubAccount(todaySentCount = 100, dailySendLimit = 100)
        stubTemplateSend(account)

        val result = service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        assertEquals("SUCCESS", result.sendStatus)
        Mockito.verify(mailSenderAccountService).selectAccountForManualSending()
        Mockito.verify(mailSenderAccountService, Mockito.never()).selectAccountForSending()
    }

    @Test
    fun `sendManualMail sends compose template as html with plain text fallback`() {
        val account = stubAccount()
        stubTemplateSend(account)
        Mockito.`when`(
            mailComposeTemplateService.render(
                eqValue(10L),
                anyValue(emptyMap<String, String>()),
                Mockito.anyInt()
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Intro Subject",
                body = "First paragraph.\n\nSecond paragraph.",
                mailType = "INTRODUCTION"
            )
        )

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        val captor = ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(mailDeliveryService).send(
            eqValue(account),
            captor.capture() ?: ComposedMail("stub", "stub", "stub")
        )
        assertTrue(captor.value.html)
        assertEquals("<p>First paragraph.</p><p>Second paragraph.</p>", captor.value.body)
        assertEquals("First paragraph.\n\nSecond paragraph.", captor.value.text)
    }

    @Test
    fun `sendManualMail rejects bare QA optionType`() {
        val account = stubAccount()
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)

        val ex = assertThrows<IllegalArgumentException> {
            service.sendManualMail(
                1,
                ManualMailSendCommand(optionType = "QA", optionValue = "10", senderAccountCode = null)
            )
        }

        assertTrue(ex.message!!.contains("Unsupported manual mail option type"))
        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )
        Mockito.verify(mailRecordRepository, Mockito.never()).save(anyValue(stubMailRecord))
        Mockito.verify(conversationStateService, Mockito.never()).transition(
            anyValue(contact),
            anyValue(ConversationStatus.INTRO_SENT),
            anyValue("MANUAL_MAIL_INTRODUCTION"),
            anyValue("MANUAL_MAIL"),
            anyValue(LocalDateTime.now()),
            anyValue { contact }
        )
    }

    @Test
    fun `sendManualMail with explicit disabled account succeeds`() {
        val disabledAccount = stubAccount().copy(accountCode = "disabled_sender", enabled = false)
        stubTemplateSend(disabledAccount)
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("disabled_sender")).thenReturn(disabledAccount)

        val result = service.sendManualMail(
            1,
            ManualMailSendCommand(
                optionType = "COMPOSE_TEMPLATE",
                optionValue = "10",
                senderAccountCode = "disabled_sender"
            )
        )

        assertEquals("SUCCESS", result.sendStatus)
        assertEquals("disabled_sender", result.senderAccountCode)
        Mockito.verify(mailSenderAccountService).getManualSendAccount("disabled_sender")
        Mockito.verify(mailSenderAccountService, Mockito.never()).selectAccountForManualSending()
        Mockito.verify(mailDeliveryService).send(
            eqValue(disabledAccount),
            anyValue(ComposedMail("stub", "stub", "stub"))
        )
    }

    @Test
    fun `sendManualMail succeeds when selectAccountForManualSending returns disabled account`() {
        val disabledAccount = stubAccount().copy(enabled = false)
        stubTemplateSend(disabledAccount)

        val result = service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        assertEquals("SUCCESS", result.sendStatus)
        assertEquals("sender", result.senderAccountCode)
        Mockito.verify(mailSenderAccountService).selectAccountForManualSending()
        Mockito.verify(mailDeliveryService).send(
            eqValue(disabledAccount),
            anyValue(ComposedMail("stub", "stub", "stub"))
        )
    }

    @Test
    fun `sendManualMail rejects simulator account`() {
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("SIMULATOR_NOOP"))
            .thenThrow(IllegalStateException("Mail sender account is not allowed for manual send: SIMULATOR_NOOP"))

        val ex = assertThrows<IllegalStateException> {
            service.sendManualMail(
                1,
                ManualMailSendCommand(
                    optionType = "COMPOSE_TEMPLATE",
                    optionValue = "10",
                    senderAccountCode = "SIMULATOR_NOOP"
                )
            )
        }

        assertTrue(ex.message!!.contains("SIMULATOR_NOOP"))
        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(stubAccount()), anyValue(ComposedMail("stub", "stub", "stub"))
        )
        Mockito.verify(mailRecordRepository, Mockito.never()).save(anyValue(stubMailRecord))
    }

    @Test
    fun `listSendOptions returns only compose templates`() {
        Mockito.`when`(mailComposeTemplateService.listEnabled()).thenReturn(
            listOf(
                MailComposeTemplate(
                    id = 10,
                    templateCode = "INTRODUCTION",
                    templateName = "Introduction",
                    subject = "Intro",
                    mailType = "INTRODUCTION",
                    enabled = true
                )
            )
        )

        val options = service.listSendOptions()

        assertTrue(options.isNotEmpty())
        assertTrue(options.all { it.optionType == "COMPOSE_TEMPLATE" })
        assertTrue(options.none { it.optionType == "QA" })
    }
}
