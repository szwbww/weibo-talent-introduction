package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.OperatorStatus
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
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
import org.junit.jupiter.api.Assertions.assertNull
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
    private val senderAccountBindingService = Mockito.mock(SenderAccountBindingService::class.java)
    private val expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)
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
        conversationStateService,
        senderAccountBindingService,
        expertOperatorStatusService
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
        val expectedSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)

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
                subjectVariants = null,
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
                eqValue(expectedSeed)
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

    private fun stubReminderSend(account: MailSenderAccount, anchor: MailRecord?) {
        val delivered = DeliveredMail(messageId = "msg-reminder", status = "SUCCESS")
        val expectedSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)

        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
        Mockito.`when`(mailComposeTemplateService.getById(20L)).thenReturn(
            MailComposeTemplateDetail(
                id = 20,
                templateCode = "MATERIAL_REMINDER",
                templateName = "Material Reminder Email",
                subject = "Gentle Follow-up on the Requested Materials",
                description = null,
                mailType = "MATERIAL_REMINDER",
                subjectVariants = null,
                enabled = true,
                blocks = emptyList(),
                createdAt = null,
                updatedAt = null
            )
        )
        Mockito.`when`(
            mailComposeTemplateService.render(
                eqValue(20L),
                anyValue(emptyMap<String, String>()),
                eqValue(expectedSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Gentle Follow-up on the Requested Materials",
                body = "Reminder body",
                mailType = "MATERIAL_REMINDER"
            )
        )
        Mockito.`when`(mailRecordRepository.findLatestInboundByExpertContactId(1L)).thenReturn(anchor)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 200) }
        Mockito.`when`(conversationStateService.transition(
            anyValue(contact),
            eqValue(ConversationStatus.INTRO_SENT),
            eqValue("MANUAL_MAIL_MATERIAL_REMINDER"),
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

    private fun inboundAnchor(
        messageId: String?,
        subject: String? = "Original Subject",
        inReplyTo: String? = null
    ) = MailRecord(
        expertContactId = 1,
        direction = "INBOUND",
        mailType = "GENERAL",
        messageId = messageId,
        inReplyTo = inReplyTo,
        subject = subject,
        body = "Hello",
        matchedQaRuleId = null,
        sendStatus = null,
        receivedAt = LocalDateTime.now(),
        sentAt = null
    )

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
    fun `sendManualMail throws when bound account is disabled`() {
        val boundContact = contact.copy(
            boundSenderAccountCode = "sender",
            senderAccountBoundAt = LocalDateTime.now()
        )
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(boundContact))
        Mockito.`when`(
            senderAccountBindingService.resolveForSend(
                anyValue(boundContact),
                eqValue(true),
                eqValue(false)
            )
        ).thenThrow(BoundSenderAccountUnavailableException(1, "sender", "DISABLED"))

        val ex = assertThrows<BoundSenderAccountUnavailableException> {
            service.sendManualMail(
                1,
                ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
            )
        }

        assertEquals("DISABLED", ex.reason)
        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(stubAccount()), anyValue(ComposedMail("stub", "stub", "stub"))
        )
    }

    @Test
    fun `sendManualMail uses bound account and never calls selectAccountForManualSending`() {
        val boundContact = contact.copy(
            boundSenderAccountCode = "sender",
            senderAccountBoundAt = LocalDateTime.now()
        )
        val boundAccount = stubAccount()
        stubTemplateSend(boundAccount)
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(boundContact))
        Mockito.`when`(
            senderAccountBindingService.resolveForSend(
                anyValue(boundContact),
                eqValue(true),
                eqValue(false)
            )
        ).thenReturn(boundAccount)

        val result = service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        assertEquals("SUCCESS", result.sendStatus)
        assertEquals("sender", result.senderAccountCode)
        Mockito.verify(senderAccountBindingService).resolveForSend(anyValue(boundContact), eqValue(true), eqValue(false))
        Mockito.verify(mailSenderAccountService, Mockito.never()).selectAccountForManualSending()
        Mockito.verify(senderAccountBindingService, Mockito.never()).bindIfAbsent(
            anyValue(1L), anyValue("sender"), anyValue(LocalDateTime.now())
        )
        Mockito.verify(mailDeliveryService).send(
            eqValue(boundAccount),
            anyValue(ComposedMail("stub", "stub", "stub"))
        )
    }

    @Test
    fun `sendManualMail throws when requested code conflicts with binding`() {
        val boundContact = contact.copy(
            boundSenderAccountCode = "sender",
            senderAccountBoundAt = LocalDateTime.now()
        )
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(boundContact))

        val ex = assertThrows<IllegalArgumentException> {
            service.sendManualMail(
                1,
                ManualMailSendCommand(
                    optionType = "COMPOSE_TEMPLATE",
                    optionValue = "10",
                    senderAccountCode = "other"
                )
            )
        }

        assertTrue(ex.message!!.contains("发件账号与专家绑定不一致"))
        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(stubAccount()), anyValue(ComposedMail("stub", "stub", "stub"))
        )
    }

    @Test
    fun `sendManualMail falls back to selection and binds when contact has no binding`() {
        val account = stubAccount()
        stubTemplateSend(account)

        val result = service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        assertEquals("SUCCESS", result.sendStatus)
        Mockito.verify(mailSenderAccountService).selectAccountForManualSending()
        Mockito.verify(senderAccountBindingService).bindIfAbsent(
            eqValue(1L), eqValue("sender"), anyValue(LocalDateTime.now())
        )
    }

    @Test
    fun `sendManualMail allows auto-paused bound account`() {
        val boundContact = contact.copy(
            boundSenderAccountCode = "sender",
            senderAccountBoundAt = LocalDateTime.now()
        )
        val pausedAccount = stubAccount().copy(autoSendPaused = true)
        stubTemplateSend(pausedAccount)
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(boundContact))
        Mockito.`when`(
            senderAccountBindingService.resolveForSend(
                anyValue(boundContact),
                eqValue(true),
                eqValue(false)
            )
        ).thenReturn(pausedAccount)

        val result = service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        assertEquals("SUCCESS", result.sendStatus)
        Mockito.verify(mailDeliveryService).send(
            eqValue(pausedAccount),
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
    fun `sendManualMail passes variant seed derived from expert contact`() {
        val account = stubAccount()
        stubTemplateSend(account)
        val expectedSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        Mockito.verify(mailComposeTemplateService).render(
            eqValue(10L),
            anyValue(emptyMap<String, String>()),
            eqValue(expectedSeed)
        )
    }

    @Test
    fun `sendManualMail uses deterministic variant seed for same expert`() {
        val account = stubAccount()
        val expectedSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)
        stubTemplateSend(account)

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )
        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        Mockito.verify(mailComposeTemplateService, Mockito.times(2)).render(
            eqValue(10L),
            anyValue(emptyMap<String, String>()),
            eqValue(expectedSeed)
        )
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
                ),
                MailComposeTemplate(
                    id = 20,
                    templateCode = "MATERIAL_REMINDER",
                    templateName = "Material Reminder Email",
                    subject = "Gentle Follow-up on the Requested Materials",
                    mailType = "MATERIAL_REMINDER",
                    enabled = true
                )
            )
        )

        val options = service.listSendOptions()

        assertTrue(options.isNotEmpty())
        assertTrue(options.all { it.optionType == "COMPOSE_TEMPLATE" })
        assertTrue(options.none { it.optionType == "QA" })
        assertTrue(options.none { it.optionType == "TEMPLATE" })

        val intro = options.first { it.optionValue == "10" }
        assertEquals("INTRODUCTION", intro.templateCode)
        assertEquals("INTRODUCTION", intro.mailType)

        val reminder = options.first { it.optionValue == "20" }
        assertEquals("MATERIAL_REMINDER", reminder.templateCode)
        assertEquals("MATERIAL_REMINDER", reminder.mailType)
        assertEquals("Material Reminder Email", reminder.optionName)
    }

    @Test
    fun `sendManualMail MATERIAL_REMINDER keeps current status and records outbound mail`() {
        val waitingContact = contact.copy(currentStatus = "WAITING_REPLY")
        val account = stubAccount()
        val expectedSeed = MailComposeTemplateService.variantSeedFor(waitingContact.orcidId, waitingContact.expertEmail)
        val delivered = DeliveredMail(messageId = "msg-reminder", status = "SUCCESS")

        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(waitingContact))
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
        Mockito.`when`(mailComposeTemplateService.getById(20L)).thenReturn(
            MailComposeTemplateDetail(
                id = 20,
                templateCode = "MATERIAL_REMINDER",
                templateName = "Material Reminder Email",
                subject = "Gentle Follow-up on the Requested Materials",
                description = null,
                mailType = "MATERIAL_REMINDER",
                subjectVariants = null,
                enabled = true,
                blocks = emptyList(),
                createdAt = null,
                updatedAt = null
            )
        )
        Mockito.`when`(
            mailComposeTemplateService.render(
                eqValue(20L),
                anyValue(emptyMap<String, String>()),
                eqValue(expectedSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Gentle Follow-up on the Requested Materials",
                body = "Reminder body",
                mailType = "MATERIAL_REMINDER"
            )
        )
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 200) }
        Mockito.`when`(conversationStateService.transition(
            anyValue(waitingContact),
            eqValue(ConversationStatus.WAITING_REPLY),
            eqValue("MANUAL_MAIL_MATERIAL_REMINDER"),
            eqValue("MANUAL_MAIL"),
            anyValue(LocalDateTime.now()),
            anyValue { waitingContact }
        )).thenAnswer { invocation ->
            val base = invocation.getArgument<ExpertContact>(0)
            val mutator = invocation.getArgument<(ExpertContact) -> ExpertContact>(5)
            mutator(base)
        }
        Mockito.`when`(mailSenderAccountRepository.save(anyValue(account)))
            .thenAnswer { it.getArgument<MailSenderAccount>(0) }

        val result = service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "20", senderAccountCode = null)
        )

        assertEquals("MATERIAL_REMINDER", result.mailType)
        assertEquals("SUCCESS", result.sendStatus)

        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
        assertEquals("OUTBOUND", recordCaptor.value.direction)
        assertEquals("MATERIAL_REMINDER", recordCaptor.value.mailType)
        assertEquals("OPERATOR", recordCaptor.value.triggeredBy)

        Mockito.verify(conversationStateService).transition(
            anyValue(waitingContact),
            eqValue(ConversationStatus.WAITING_REPLY),
            eqValue("MANUAL_MAIL_MATERIAL_REMINDER"),
            eqValue("MANUAL_MAIL"),
            anyValue(LocalDateTime.now()),
            anyValue { waitingContact }
        )
    }

    @Test
    fun `MATERIAL_REMINDER threads onto latest inbound message id`() {
        val account = stubAccount()
        stubReminderSend(account, inboundAnchor(messageId = "<inbound-1@test.com>"))

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "20", senderAccountCode = null)
        )

        val captor = ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(mailDeliveryService).send(
            eqValue(account),
            captor.capture() ?: ComposedMail("stub", "stub", "stub")
        )
        assertEquals("<inbound-1@test.com>", captor.value.inReplyTo)
        assertEquals("<inbound-1@test.com>", captor.value.references)
        assertEquals("Re: Original Subject", captor.value.subject)
    }

    @Test
    fun `MATERIAL_REMINDER without inbound anchor sends without thread headers`() {
        val account = stubAccount()
        stubReminderSend(account, null)

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "20", senderAccountCode = null)
        )

        val captor = ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(mailDeliveryService).send(
            eqValue(account),
            captor.capture() ?: ComposedMail("stub", "stub", "stub")
        )
        assertNull(captor.value.inReplyTo)
        assertNull(captor.value.references)
        assertEquals("Gentle Follow-up on the Requested Materials", captor.value.subject)
        assertTrue(!captor.value.subject.startsWith("Re: "))
    }

    @Test
    fun `MATERIAL_REMINDER strips stacked reply prefixes`() {
        val account = stubAccount()
        stubReminderSend(account, inboundAnchor(messageId = "<inbound-1@test.com>", subject = "Re: RE: 回复: Hello"))

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "20", senderAccountCode = null)
        )

        val captor = ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(mailDeliveryService).send(
            eqValue(account),
            captor.capture() ?: ComposedMail("stub", "stub", "stub")
        )
        assertEquals("Re: Hello", captor.value.subject)
    }

    @Test
    fun `MATERIAL_REMINDER truncates oversized subject to 255`() {
        val account = stubAccount()
        stubReminderSend(account, inboundAnchor(messageId = "<inbound-1@test.com>", subject = "M".repeat(300)))

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "20", senderAccountCode = null)
        )

        val captor = ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(mailDeliveryService).send(
            eqValue(account),
            captor.capture() ?: ComposedMail("stub", "stub", "stub")
        )
        assertEquals(255, captor.value.subject.length)
        assertTrue(captor.value.subject.startsWith("Re: "))
    }

    @Test
    fun `MATERIAL_REMINDER skips threading when anchor message id exceeds 255 chars`() {
        val account = stubAccount()
        stubReminderSend(account, inboundAnchor(messageId = "m".repeat(256), subject = "Long id anchor"))

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "20", senderAccountCode = null)
        )

        val captor = ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(mailDeliveryService).send(
            eqValue(account),
            captor.capture() ?: ComposedMail("stub", "stub", "stub")
        )
        assertNull(captor.value.inReplyTo)
        assertNull(captor.value.references)
        assertEquals("Gentle Follow-up on the Requested Materials", captor.value.subject)
        assertTrue(!captor.value.subject.startsWith("Re: "))
    }

    @Test
    fun `MATERIAL_REMINDER references includes anchor inReplyTo when present`() {
        val account = stubAccount()
        stubReminderSend(
            account,
            inboundAnchor(messageId = "<inbound-1@test.com>", inReplyTo = "<inbound-0@test.com>")
        )

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "20", senderAccountCode = null)
        )

        val captor = ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(mailDeliveryService).send(
            eqValue(account),
            captor.capture() ?: ComposedMail("stub", "stub", "stub")
        )
        assertEquals("<inbound-1@test.com>", captor.value.inReplyTo)
        assertEquals("<inbound-0@test.com> <inbound-1@test.com>", captor.value.references)
    }

    @Test
    fun `non-reminder compose template does not query inbound anchor`() {
        val account = stubAccount()
        stubTemplateSend(account)

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        Mockito.verify(mailRecordRepository, Mockito.never())
            .findLatestInboundByExpertContactId(Mockito.anyLong())
    }

    @Test
    fun `sendManualMail persists inReplyTo matching the sent header`() {
        val account = stubAccount()
        stubReminderSend(
            account,
            inboundAnchor(messageId = "<inbound-1@test.com>", inReplyTo = "<inbound-0@test.com>")
        )

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "20", senderAccountCode = null)
        )

        val mailCaptor = ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(mailDeliveryService).send(
            eqValue(account),
            mailCaptor.capture() ?: ComposedMail("stub", "stub", "stub")
        )
        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
        assertEquals(mailCaptor.value.inReplyTo, recordCaptor.value.inReplyTo)

        stubReminderSend(account, null)
        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "20", senderAccountCode = null)
        )
        val secondMailCaptor = ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(mailDeliveryService, Mockito.times(2)).send(
            eqValue(account),
            secondMailCaptor.capture() ?: ComposedMail("stub", "stub", "stub")
        )
        val secondRecordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository, Mockito.times(2)).save(secondRecordCaptor.capture())
        assertNull(secondMailCaptor.value.inReplyTo)
        assertNull(secondRecordCaptor.value.inReplyTo)
    }

    @Test
    fun `sendManualMail sets uuid message id for compose template`() {
        val account = stubAccount()
        stubTemplateSend(account)

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        val captor = ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(mailDeliveryService).send(
            eqValue(account),
            captor.capture() ?: ComposedMail("stub", "stub", "stub")
        )
        assertTrue(Regex("""^<reminder-\d+-[0-9a-f-]{36}@.+>$""").matches(captor.value.messageId!!))
    }

    @Test
    fun `sendManualMail advances operator status to CONTACTED for INTRODUCTION`() {
        val account = stubAccount()
        stubTemplateSend(account)

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "10", senderAccountCode = null)
        )

        Mockito.verify(expertOperatorStatusService).updateAutomatically(
            anyValue(contact),
            eqValue(OperatorStatus.CONTACTED),
            eqValue("MANUAL_MAIL_INTRODUCTION")
        )
    }

    @Test
    fun `sendManualMail advances operator status to INVITED for MEETING_INVITATION`() {
        val account = stubAccount()
        stubMeetingInvitationSend(account)

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "30", senderAccountCode = null)
        )

        Mockito.verify(expertOperatorStatusService).updateAutomatically(
            anyValue(contact),
            eqValue(OperatorStatus.INVITED),
            eqValue("MANUAL_MAIL_MEETING_INVITATION")
        )
    }

    @Test
    fun `sendManualMail leaves operator status untouched for COMPOSE_TEMPLATE`() {
        val account = stubAccount()
        stubComposeTemplateSend(account)

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "COMPOSE_TEMPLATE", optionValue = "40", senderAccountCode = null)
        )

        Mockito.verifyNoInteractions(expertOperatorStatusService)
    }

    private fun stubMeetingInvitationSend(account: MailSenderAccount) {
        val delivered = DeliveredMail(messageId = "msg-meeting", status = "SUCCESS")
        val expectedSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)

        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
        Mockito.`when`(mailComposeTemplateService.getById(30L)).thenReturn(
            MailComposeTemplateDetail(
                id = 30,
                templateCode = "MEETING_INVITATION",
                templateName = "Meeting Invitation",
                subject = "Meeting Invitation Subject",
                description = null,
                mailType = "MEETING_INVITATION",
                subjectVariants = null,
                enabled = true,
                blocks = emptyList(),
                createdAt = null,
                updatedAt = null
            )
        )
        Mockito.`when`(
            mailComposeTemplateService.render(
                eqValue(30L),
                anyValue(emptyMap<String, String>()),
                eqValue(expectedSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Meeting Invitation Subject",
                body = "Meeting Invitation Body",
                mailType = "MEETING_INVITATION"
            )
        )
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 300) }
        Mockito.`when`(conversationStateService.transition(
            anyValue(contact),
            eqValue(ConversationStatus.MEETING_SCHEDULING),
            eqValue("MANUAL_MAIL_MEETING_INVITATION"),
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

    private fun stubComposeTemplateSend(account: MailSenderAccount) {
        val delivered = DeliveredMail(messageId = "msg-compose", status = "SUCCESS")
        val expectedSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)

        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
        Mockito.`when`(mailComposeTemplateService.getById(40L)).thenReturn(
            MailComposeTemplateDetail(
                id = 40,
                templateCode = "COMPOSE_TEMPLATE",
                templateName = "Compose Template",
                subject = "Compose Subject",
                description = null,
                mailType = "COMPOSE_TEMPLATE",
                subjectVariants = null,
                enabled = true,
                blocks = emptyList(),
                createdAt = null,
                updatedAt = null
            )
        )
        Mockito.`when`(
            mailComposeTemplateService.render(
                eqValue(40L),
                anyValue(emptyMap<String, String>()),
                eqValue(expectedSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Compose Subject",
                body = "Compose Body",
                mailType = "COMPOSE_TEMPLATE"
            )
        )
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 400) }
        Mockito.`when`(conversationStateService.transition(
            anyValue(contact),
            eqValue(ConversationStatus.INTRO_SENT),
            eqValue("MANUAL_MAIL_COMPOSE_TEMPLATE"),
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
}
