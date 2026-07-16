package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.llm.service.AiReplyReviewAuditService
import com.weibo.talentintroduction.llm.service.AiReviewConfirmation
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.CategoryRulesGroup
import com.weibo.talentintroduction.qa.service.CompositionSuggestResult
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.qa.service.QaReplyComposer
import com.weibo.talentintroduction.qa.service.SuggestQaRule
import com.weibo.talentintroduction.reply.domain.ReplySnippet
import com.weibo.talentintroduction.reply.service.AckOption
import com.weibo.talentintroduction.reply.service.ManualReplyFrame
import com.weibo.talentintroduction.reply.service.ReplySnippetDetail
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import com.weibo.talentintroduction.reply.service.SnippetType
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.domain.ContentVariant
import com.weibo.talentintroduction.variant.domain.ContentVariantOwnerType
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class PendingMailOperationServiceTest {
    private val jsonMapper = ObjectMapper().registerKotlinModule()

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
    private val qaMatchService = Mockito.mock(QaMatchService::class.java)
    private val mailBodyCleaner = Mockito.mock(MailBodyCleaner::class.java)
    private val mailContentService = MailContentService()
    private val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val renderTemplateService = MailComposeTemplateService(
        Mockito.mock(MailComposeTemplateRepository::class.java),
        Mockito.mock(MailComposeTemplateBlockRepository::class.java),
        Mockito.mock(QaRuleRepository::class.java),
        Mockito.mock(ReplySnippetRepository::class.java),
        ObjectMapper(),
        Mockito.mock(MailVariableService::class.java),
        expertContactRepository,
        mailSenderAccountService,
        ContentVariantService(Mockito.mock(ContentVariantRepository::class.java), MailPlaceholderService())
    )
    private val mailVariableService = MailVariableService(expertSearchService, renderTemplateService)
    private val contentVariantRepository = Mockito.mock(ContentVariantRepository::class.java)
    private val contentVariantService = ContentVariantService(
        contentVariantRepository,
        MailPlaceholderService()
    )
    private val aiReplyReviewAuditService = Mockito.mock(AiReplyReviewAuditService::class.java)
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
        qaMatchService,
        mailBodyCleaner,
        mailContentService,
        replySnippetService,
        mailVariableService,
        contentVariantService,
        aiReplyReviewAuditService
    )

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNullable(): T = Mockito.any<T>() ?: null as T

    private fun <T> eqValue(value: T): T =
        Mockito.eq(value) ?: value

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

    private fun inbound(expertContactId: Long? = null): InboundMailProcessing =
        InboundMailProcessing(
            id = 1,
            senderAccountCode = "sender",
            imapUid = 1,
            messageId = "msg-1",
            inReplyTo = null,
            fromEmail = "expert@test.com",
            subject = "Re: Test",
            body = "body",
            cleanedBody = "body",
            receivedAt = LocalDateTime.now().minusHours(1),
            processStatus = "MANUAL_REVIEW",
            processReason = "QA_NO_MATCH",
            reasonType = "QA_NO_MATCH",
            expertContactId = expertContactId
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

    @Test
    fun `change status calls ExpertOperatorStatusService and writes log`() {
        val record = inbound(1)
        Mockito.`when`(inboundMailProcessingRepository.findById(1L))
            .thenReturn(Optional.of(record))
        Mockito.`when`(expertOperatorStatusService.changeStatus(
            anyValue(1L), anyValue("REPLIED"), anyValue("op"), anyValue("test note"), anyValue(1L)
        )).thenReturn(contact.copy(operatorStatus = "REPLIED"))

        val result = service.changeOperatorStatus(1, "REPLIED", "op", "test note")

        assertEquals("REPLIED", result.operatorStatus)
        Mockito.verify(expertOperatorStatusService).changeStatus(
            eqValue(1L), eqValue("REPLIED"), eqValue("op"), eqValue("test note"), eqValue(1L)
        )
    }

    @Test
    fun `change status rejects unbound inbound`() {
        val record = inbound(null)
        Mockito.`when`(inboundMailProcessingRepository.findById(1L))
            .thenReturn(Optional.of(record))

        val ex = assertThrows(IllegalStateException::class.java) {
            service.changeOperatorStatus(1, "REPLIED", "op", null)
        }
        assertTrue(ex.message!!.contains("not bound"))
        Mockito.verify(expertOperatorStatusService, Mockito.never())
            .changeStatus(
                anyValue(1L), anyValue(""), anyValue(""), anyValue(""), anyValue(0L)
            )
    }

    @Test
    fun `change index level rejects unbound inbound`() {
        val record = inbound(null)
        Mockito.`when`(inboundMailProcessingRepository.findById(1L))
            .thenReturn(Optional.of(record))

        val ex = assertThrows(IllegalStateException::class.java) {
            service.changeIndexLevel(1, "APPLICATION", "op", null)
        }
        assertTrue(ex.message!!.contains("not bound"))
    }

    @Test
    fun `change index level calls ExpertIndexLevelOperationService`() {
        val record = inbound(1)
        Mockito.`when`(inboundMailProcessingRepository.findById(1L))
            .thenReturn(Optional.of(record))
        Mockito.`when`(expertIndexLevelOperationService.changeLevel(
            anyValue(1L), anyValue("APPLICATION"), anyValue("op"), anyValue("note"), anyValue(1L)
        )).thenReturn(contact.copy(currentIndexLevel = "APPLICATION", applicationIndexed = true))

        val result = service.changeIndexLevel(1, "APPLICATION", "op", "note")

        assertEquals("APPLICATION", result.currentIndexLevel)
    }

    @Test
    fun `send qa reply writes log with correct fields`() {
        val record = inbound(1)
        val rule = QaRule(
            id = 10,
            categoryId = 1,
            keywords = "test",
            replySubject = "QA Subject",
            replyBody = "QA Body",
            displayName = "Test QA Rule",
            enabled = true
        )
        val account = MailSenderAccount(
            accountCode = "sender", senderEmail = "sender@test.com",
            senderName = "Sender", senderTitle = "Title", senderDisplayName = "Sender",
            teamName = "Team", countryName = "CN",
            smtpHost = "smtp.test.com", smtpPort = 465,
            smtpUsername = "u", smtpPassword = "p",
            imapHost = "imap.test.com", imapPort = 993,
            imapUsername = "u", imapPassword = "p"
        )
        val delivered = DeliveredMail(messageId = "msg-1", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 200) }

        val result = service.sendQaReply(1, 10, null, "op")

        assertEquals("SUCCESS", result.sendStatus)

        val sentMail = sentMails.single()
        assertEquals(true, sentMail.html)
        assertEquals("QA Body", sentMail.text)
        assertEquals(mailContentService.plainTextToHtml("QA Body"), sentMail.body)

        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
        assertEquals("QA Body", recordCaptor.value.body)

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        Mockito.verify(operatorActionLogService).record(
            anyValue(""), anyValue(0L), anyValue(OperatorActionType.SEND_QA_REPLY),
            anyValue(0L), anyValue(0L),
            anyValue(null), captor.capture(),
            anyValue(""), anyValue(""), anyValue(null)
        )
        val after = captor.value!!
        assertEquals(200L, after["mailRecordId"])
        assertEquals(10L, after["qaRuleId"])
        assertEquals("Test QA Rule", after["qaRuleName"])
        assertEquals("SUCCESS", after["sendStatus"])
        assertEquals("QA Subject", after["subject"])
        assertEquals("QA Body", after["bodyPreviewText"])
    }

    @Test
    fun `send qa reply renders expert placeholders in outbound body`() {
        val record = inbound(1)
        val rule = QaRule(
            id = 10,
            categoryId = 1,
            keywords = "test",
            replySubject = "QA Subject",
            replyBody = "Dear \${expertFamilyName}, answer here.",
            displayName = "Test QA Rule",
            enabled = true
        )
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-render", status = "SUCCESS")
        val renderedBody = "Dear Lovelace, answer here."

        stubExpertProfile(contact.orcidId, familyNames = "Lovelace")
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 204) }

        service.sendQaReply(1, 10, null, "op")

        val sentMail = sentMails.single()
        assertEquals(renderedBody, sentMail.text)
        assertEquals(mailContentService.plainTextToHtml(renderedBody), sentMail.body)
        assertFalse(sentMail.text!!.contains("\${"))
        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
        assertEquals(renderedBody, recordCaptor.value.body)
    }

    @Test
    fun `send qa reply succeeds when only enabled account is at daily limit`() {
        val record = inbound(1)
        val rule = QaRule(
            id = 10,
            categoryId = 1,
            keywords = "test",
            replySubject = "QA Subject",
            replyBody = "QA Body",
            displayName = "Test QA Rule",
            enabled = true
        )
        val account = stubAccount().copy(todaySentCount = 100, dailySendLimit = 100)
        val delivered = DeliveredMail(messageId = "msg-limit", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 203) }

        val result = service.sendQaReply(1, 10, null, "op")

        assertEquals("SUCCESS", result.sendStatus)
        Mockito.verify(mailSenderAccountService).getManualSendAccount("sender")
        Mockito.verify(mailSenderAccountService, Mockito.never()).selectAccountForManualSending()
        Mockito.verify(mailSenderAccountService, Mockito.never()).selectAccountForSending()
    }

    @Test
    fun `send manual rich reply writes log with plain text body preview`() {
        val record = inbound(1)
        val account = MailSenderAccount(
            accountCode = "sender", senderEmail = "sender@test.com",
            senderName = "Sender", senderTitle = "Title", senderDisplayName = "Sender",
            teamName = "Team", countryName = "CN",
            smtpHost = "smtp.test.com", smtpPort = 465,
            smtpUsername = "u", smtpPassword = "p",
            imapHost = "imap.test.com", imapPort = 993,
            imapUsername = "u", imapPassword = "p"
        )
        val delivered = DeliveredMail(messageId = "msg-2", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 201) }
        Mockito.`when`(mailBodyCleaner.clean("<p>Hello</p>")).thenReturn("Hello")

        val result = service.sendManualRichReply(1, null, "Hello", "<p>Hello</p>", null, "op")

        assertEquals("SUCCESS", result.sendStatus)
        assertEquals("MANUAL_RICH_REPLY", result.mailType)

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        Mockito.verify(operatorActionLogService).record(
            anyValue(""), anyValue(0L), anyValue(OperatorActionType.SEND_MANUAL_RICH_REPLY),
            anyValue(0L), anyValue(0L),
            anyValue(null), captor.capture(),
            anyValue(""), anyValue(""), anyValue(null)
        )
        val after = captor.value!!
        assertEquals(201L, after["mailRecordId"])
        assertEquals("SUCCESS", after["sendStatus"])
        assertEquals("Hello", after["subject"])
        assertEquals("Hello", after["bodyPreviewText"])
    }

    @Test
    fun `audit log body preview truncated to 500 chars`() {
        val longHtml = "x".repeat(1000)
        val record = inbound(1)
        val account = MailSenderAccount(
            accountCode = "sender", senderEmail = "sender@test.com",
            senderName = "Sender", senderTitle = "Title", senderDisplayName = "Sender",
            teamName = "Team", countryName = "CN",
            smtpHost = "smtp.test.com", smtpPort = 465,
            smtpUsername = "u", smtpPassword = "p",
            imapHost = "imap.test.com", imapPort = 993,
            imapUsername = "u", imapPassword = "p"
        )
        val delivered = DeliveredMail(messageId = "msg-3", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 202) }
        Mockito.`when`(mailBodyCleaner.clean(longHtml)).thenReturn(longHtml)

        service.sendManualRichReply(1, null, "Subject", longHtml, null, "op")

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        Mockito.verify(operatorActionLogService).record(
            anyValue(""), anyValue(0L), anyValue(OperatorActionType.SEND_MANUAL_RICH_REPLY),
            anyValue(0L), anyValue(0L),
            anyValue(null), captor.capture(),
            anyValue(""), anyValue(""), anyValue(null)
        )
        val after = captor.value!!
        val preview = after["bodyPreviewText"] as String
        assertEquals(500, preview.length)
    }

    @Test
    fun `send manual rich reply with qa rule ids writes associations and composed audit log`() {
        val record = inbound(1)
        val rule1 = qaRule(10, 1, "Body one", priority = 50)
        val rule2 = qaRule(11, 2, "Body two", priority = 100)
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-rich-qa", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule1))
        Mockito.`when`(qaRuleRepository.findById(11L)).thenReturn(Optional.of(rule2))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 301) }
        Mockito.`when`(mailRecordQaRuleRepository.save(anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))))
            .thenAnswer { invocation ->
                val arg = invocation.getArgument<MailRecordQaRule>(0)
                arg.copy(id = arg.ordinal + 1L)
            }
        Mockito.`when`(mailBodyCleaner.clean("<p>Composed</p>")).thenReturn("Composed")

        val result = service.sendManualRichReply(
            inboundProcessingId = 1,
            senderAccountCode = null,
            subject = "Subject",
            htmlBody = "<p>Composed</p>",
            textBody = "Composed body",
            operatorName = "op",
            qaRuleIds = listOf(10, 11),
            suggestedRuleIds = listOf(10, 11),
            ackSnippetId = 100L,
            edited = true,
            freeTextPreview = "free snippet"
        )

        assertEquals("SUCCESS", result.sendStatus)
        assertEquals("MANUAL_RICH_REPLY", result.mailType)

        val mailCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(mailCaptor.capture())
        assertEquals(10L, mailCaptor.value.matchedQaRuleId)

        val qaRuleCaptor = ArgumentCaptor.forClass(MailRecordQaRule::class.java)
        Mockito.verify(mailRecordQaRuleRepository, Mockito.times(2)).save(qaRuleCaptor.capture())
        val savedAssociations = qaRuleCaptor.allValues
        assertEquals(listOf(10L, 11L), savedAssociations.map { it.qaRuleId })
        assertEquals(listOf(0, 1), savedAssociations.map { it.ordinal })

        @Suppress("UNCHECKED_CAST")
        val afterCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        Mockito.verify(operatorActionLogService).record(
            anyValue(""), anyValue(0L), anyValue(OperatorActionType.SEND_MANUAL_COMPOSED_REPLY),
            anyValue(0L), anyValue(0L),
            anyValue(null), afterCaptor.capture(),
            anyValue(""), anyValue(""), anyValue(null)
        )
        val after = afterCaptor.value!!
        assertEquals(listOf(10L, 11L), after["qaRuleIds"])
        assertEquals(listOf(10L, 11L), after["suggestedRuleIds"])
        assertEquals(100L, after["ackSnippetId"])
        assertEquals(true, after["edited"])
        assertEquals("free snippet", after["freeTextPreview"])
    }

    @Test
    fun `send manual rich reply without qa rule ids skips associations and uses rich reply log`() {
        val record = inbound(1)
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-rich-plain", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 302) }
        Mockito.`when`(mailBodyCleaner.clean("<p>Plain</p>")).thenReturn("Plain")

        service.sendManualRichReply(
            inboundProcessingId = 1,
            senderAccountCode = null,
            subject = "Hello",
            htmlBody = "<p>Plain</p>",
            textBody = "Plain",
            operatorName = "op",
            qaRuleIds = emptyList()
        )

        val mailCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(mailCaptor.capture())
        assertEquals(null, mailCaptor.value.matchedQaRuleId)

        Mockito.verify(mailRecordQaRuleRepository, Mockito.never()).save(
            anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))
        )

        @Suppress("UNCHECKED_CAST")
        val afterCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        Mockito.verify(operatorActionLogService).record(
            anyValue(""), anyValue(0L), anyValue(OperatorActionType.SEND_MANUAL_RICH_REPLY),
            anyValue(0L), anyValue(0L),
            anyValue(null), afterCaptor.capture(),
            anyValue(""), anyValue(""), anyValue(null)
        )
    }

    @Test
    fun `mark resolved updates status and writes log with correct fields`() {
        val record = inbound(1)
        Mockito.`when`(inboundMailProcessingRepository.findById(1L))
            .thenReturn(Optional.of(record))
        Mockito.`when`(inboundMailProcessingRepository.save(anyValue(record)))
            .thenAnswer { it.getArgument<InboundMailProcessing>(0) }
        Mockito.`when`(inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(
            anyValue(1L), anyValue("MANUAL_REVIEW")
        )).thenReturn(1)

        service.markResolved(1, "op", "op", "done")

        val captor = ArgumentCaptor.forClass(InboundMailProcessing::class.java)
        Mockito.verify(inboundMailProcessingRepository).save(captor.capture())
        assertEquals("PROCESSED", captor.value.processStatus)
        assertEquals("MANUAL_RESOLVED", captor.value.processReason)
        assertEquals("op", captor.value.resolvedBy)
    }

    private fun stubExpertProfile(
        orcidId: String,
        familyNames: String? = "Lovelace",
        institution: String? = "Oxford"
    ) {
        val profile = ExpertProfile(
            orcidId = orcidId,
            email = "expert@test.com",
            givenNames = "Ada",
            familyNames = familyNames,
            country = "UK",
            keyword = null,
            employment = null,
            researchFields = null,
            institution = institution
        )
        Mockito.`when`(expertSearchService.findByOrcidId(orcidId, ExpertIndexLevel.CANDIDATE))
            .thenReturn(profile)
    }

    private fun qaRule(
        id: Long,
        categoryId: Long,
        body: String,
        subject: String? = "Subject $id",
        enabled: Boolean = true,
        priority: Int = 100
    ) = QaRule(
        id = id,
        categoryId = categoryId,
        keywords = "kw$id",
        replySubject = subject,
        replyBody = body,
        displayName = "Rule $id",
        sectionTitle = "Section $id",
        enabled = enabled,
        priority = priority
    )

    private fun stubAccount() = MailSenderAccount(
        accountCode = "sender", senderEmail = "sender@test.com",
        senderName = "Sender", senderTitle = "Title", senderDisplayName = "Sender",
        teamName = "Team", countryName = "CN",
        smtpHost = "smtp.test.com", smtpPort = 465,
        smtpUsername = "u", smtpPassword = "p",
        imapHost = "imap.test.com", imapPort = 993,
        imapUsername = "u", imapPassword = "p"
    )

    private fun stubSuggest(suggestedRuleIds: List<Long> = emptyList()) = CompositionSuggestResult(
        suggestedRuleIds = suggestedRuleIds,
        suggestedRules = emptyList(),
        rulesByCategory = emptyList(),
        gapItems = emptyList(),
        gapDetected = false,
        matchedCategoryIds = emptyList()
    )

    private fun stubDefaultFrame(
        salutation: String? = "Dear Professor,",
        greeting: String? = QaReplyComposer.GREETING,
        closing: String? = QaReplyComposer.CLOSING,
        ackOptions: List<AckOption> = listOf(
            AckOption(id = 100L, content = "Thank you for sharing your CV.")
        )
    ) {
        Mockito.`when`(replySnippetService.resolveManualFrame()).thenReturn(
            ManualReplyFrame(
                salutation = salutation,
                greeting = greeting,
                closing = closing,
                ackOptions = ackOptions
            )
        )
    }

    private fun stubResolveAck() {
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.anyLong())).thenReturn(null)
    }

    private fun stubQaRuleVariant(ruleId: Long, variantContent: String) {
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                ruleId
            )
        ).thenReturn(
            listOf(
                ContentVariant(
                    id = 900L + ruleId,
                    ownerType = ContentVariantOwnerType.QA_RULE,
                    ownerId = ruleId,
                    variantOrder = 10,
                    content = variantContent
                )
            )
        )
    }

    private fun resolvedQaRuleBody(ruleId: Long, mainBody: String): String {
        val seed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)
        return contentVariantService.resolveBody(
            ContentVariantOwnerType.QA_RULE,
            ruleId,
            mainBody,
            seed,
            useVariants = true
        )
    }

    private fun stubDefaultSnippetByType(type: String, snippetId: Long, mainContent: String) {
        val snippet = ReplySnippet(
            id = snippetId,
            snippetType = type,
            content = mainContent,
            isDefault = true,
            enabled = true
        )
        Mockito.`when`(replySnippetService.listByType(type)).thenReturn(
            listOf(ReplySnippetDetail(snippet = snippet, variants = emptyList()))
        )
    }

    private fun stubReplySnippetVariant(snippetId: Long, variantContent: String) {
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.REPLY_SNIPPET,
                snippetId
            )
        ).thenReturn(
            listOf(
                ContentVariant(
                    id = 800L + snippetId,
                    ownerType = ContentVariantOwnerType.REPLY_SNIPPET,
                    ownerId = snippetId,
                    variantOrder = 10,
                    content = variantContent
                )
            )
        )
    }

    private fun stubFrameSnippetVariants(
        salutationMain: String = "MAIN salutation",
        greetingMain: String = "MAIN greeting",
        closingMain: String = "MAIN closing",
        ackMain: String = "MAIN ack",
        salutationVariant: String = "VARIANT salutation",
        greetingVariant: String = "VARIANT greeting",
        closingVariant: String = "VARIANT closing",
        ackVariant: String = "VARIANT ack",
        ackId: Long = 100L,
        salutationId: Long = 200L,
        greetingId: Long = 202L,
        closingId: Long = 204L
    ) {
        stubDefaultFrame(
            salutation = salutationMain,
            greeting = greetingMain,
            closing = closingMain,
            ackOptions = listOf(AckOption(id = ackId, content = ackMain))
        )
        stubDefaultSnippetByType(SnippetType.SALUTATION.name, salutationId, salutationMain)
        stubDefaultSnippetByType(SnippetType.GREETING.name, greetingId, greetingMain)
        stubDefaultSnippetByType(SnippetType.CLOSING.name, closingId, closingMain)
        Mockito.`when`(replySnippetService.resolveAck(ackId)).thenReturn(ackMain)
        stubReplySnippetVariant(salutationId, salutationVariant)
        stubReplySnippetVariant(greetingId, greetingVariant)
        stubReplySnippetVariant(closingId, closingVariant)
        stubReplySnippetVariant(ackId, ackVariant)
    }

    @Test
    fun `request DTOs default useVariants to false when field is omitted in JSON`() {
        val qaReply = jsonMapper.readValue(
            """{"qaRuleId":1,"senderAccountCode":null,"operatorName":"op"}""",
            PendingQaReplyRequest::class.java
        )
        assertFalse(qaReply.useVariants)

        val richReply = jsonMapper.readValue(
            """{"senderAccountCode":"s","subject":"Sub","htmlBody":"<p>x</p>","textBody":"x","operatorName":"op"}""",
            PendingManualRichReplyRequest::class.java
        )
        assertFalse(richReply.useVariants)
        assertEquals(null, richReply.templateTextBody)
        assertEquals(null, richReply.templateHtmlBody)

        val composed = jsonMapper.readValue(
            """{"qaRuleIds":[1,2],"overrideTextBody":null,"senderAccountCode":"s","operatorName":"op"}""",
            ComposedReplyRequest::class.java
        )
        assertFalse(composed.useVariants)
    }

    @Test
    fun `resolveManualFrameForInbound returns snippet main bodies when useVariants is false`() {
        stubFrameSnippetVariants()
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(inbound(1)))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))

        val frame = service.resolveManualFrameForInbound(1, useVariants = false)

        assertEquals("MAIN salutation", frame.salutation)
        assertEquals("MAIN greeting", frame.greeting)
        assertEquals("MAIN closing", frame.closing)
        assertEquals("MAIN ack", frame.ackOptions.single().content)
    }

    @Test
    fun `resolveManualFrameForInbound returns snippet variant bodies when useVariants is true`() {
        stubFrameSnippetVariants()
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(inbound(1)))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))

        val frame = service.resolveManualFrameForInbound(1, useVariants = true)

        assertEquals("VARIANT salutation", frame.salutation)
        assertEquals("VARIANT greeting", frame.greeting)
        assertEquals("VARIANT closing", frame.closing)
        assertEquals("VARIANT ack", frame.ackOptions.single().content)
    }

    @Test
    fun `sendManualComposedReply includes snippet variants in skeleton order when useVariants is true`() {
        val record = inbound(1)
        val rule = qaRule(10, 1, "RULE body")
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-frame-variant", status = "SUCCESS")
        val ackId = 100L

        stubFrameSnippetVariants(ackId = ackId)
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaMatchService.suggestComposition("body")).thenReturn(stubSuggest(listOf(10)))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 213) }
        Mockito.`when`(mailRecordQaRuleRepository.save(anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))))
            .thenAnswer { it.getArgument<MailRecordQaRule>(0).copy(id = 1) }

        service.sendManualComposedReply(1, listOf(10), null, null, ackId, null, "op", useVariants = true)

        val text = sentMails.single().text!!

        assertTrue(text.contains("VARIANT salutation"))
        assertTrue(text.contains("VARIANT ack"))
        assertTrue(text.contains("VARIANT greeting"))
        assertTrue(text.contains("RULE body"))
        assertTrue(text.contains("VARIANT closing"))
        assertTrue(text.indexOf("VARIANT salutation") < text.indexOf("VARIANT ack"))
        assertTrue(text.indexOf("VARIANT ack") < text.indexOf("VARIANT greeting"))
        assertTrue(text.indexOf("VARIANT greeting") < text.indexOf("RULE body"))
        assertTrue(text.indexOf("RULE body") < text.indexOf("VARIANT closing"))
    }

    @Test
    fun `send qa reply uses main body when useVariants is false`() {
        val record = inbound(1)
        val rule = qaRule(10, 1, "MAIN body")
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-main", status = "SUCCESS")

        stubQaRuleVariant(10, "VARIANT body")
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 210) }

        service.sendQaReply(1, 10, null, "op", useVariants = false)

        assertEquals("MAIN body", sentMails.single().text)
    }

    @Test
    fun `suggest and send manual composed reply use same variant text when useVariants is true`() {
        val record = inbound(1).copy(cleanedBody = "question")
        val rule = qaRule(10, 1, "MAIN body")
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-variant", status = "SUCCESS")
        val suggestRule = SuggestQaRule(
            id = 10,
            categoryId = 1,
            displayName = "Rule 10",
            sectionTitle = "Section 10",
            replySubject = "Subject 10",
            replyBody = "MAIN body",
            keywords = "kw10"
        )

        stubQaRuleVariant(10, "VARIANT body")
        stubDefaultFrame(salutation = null, greeting = null, closing = null, ackOptions = emptyList())
        stubResolveAck()
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaMatchService.suggestComposition("question")).thenReturn(
            stubSuggest(listOf(10)).copy(suggestedRules = listOf(suggestRule))
        )
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 211) }
        Mockito.`when`(mailRecordQaRuleRepository.save(anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))))
            .thenAnswer { it.getArgument<MailRecordQaRule>(0).copy(id = 1) }

        val suggest = service.suggestComposedReply(1, useVariants = true)
        val expectedBody = resolvedQaRuleBody(10, "MAIN body")

        assertEquals(expectedBody, suggest.suggestedRules.single().replyBody)

        service.sendManualComposedReply(1, listOf(10), null, null, null, null, "op", useVariants = true)

        assertEquals(expectedBody, sentMails.single().text)
    }

    @Test
    fun `send manual composed reply keeps override text body over variant resolution`() {
        val record = inbound(1)
        val rule = qaRule(10, 1, "MAIN body")
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-override", status = "SUCCESS")

        stubQaRuleVariant(10, "VARIANT body")
        stubDefaultFrame(salutation = null, greeting = null, closing = null, ackOptions = emptyList())
        stubResolveAck()
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaMatchService.suggestComposition("body")).thenReturn(stubSuggest(listOf(10)))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 212) }
        Mockito.`when`(mailRecordQaRuleRepository.save(anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))))
            .thenAnswer { it.getArgument<MailRecordQaRule>(0).copy(id = 1) }

        service.sendManualComposedReply(
            1,
            listOf(10),
            overrideTextBody = "Operator edited body",
            freeTextBody = null,
            ackSnippetId = null,
            senderAccountCode = null,
            operatorName = "op",
            useVariants = true
        )

        assertEquals("Operator edited body", sentMails.single().text)
    }

    @Test
    fun `send manual composed reply composes multiple rules and saves associations`() {
        val record = inbound(1)
        val rule1 = qaRule(10, 1, "Body one", priority = 50)
        val rule2 = qaRule(11, 2, "Body two", priority = 100)
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-composed", status = "SUCCESS")

        stubDefaultFrame()
        stubResolveAck()
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaMatchService.suggestComposition("body")).thenReturn(stubSuggest(listOf(10, 11)))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule1))
        Mockito.`when`(qaRuleRepository.findById(11L)).thenReturn(Optional.of(rule2))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 300) }
        Mockito.`when`(mailRecordQaRuleRepository.save(anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))))
            .thenAnswer { invocation ->
                val arg = invocation.getArgument<MailRecordQaRule>(0)
                arg.copy(id = arg.ordinal + 1L)
            }

        val result = service.sendManualComposedReply(1, listOf(10, 11), null, null, null, null, "op")

        assertEquals("SUCCESS", result.sendStatus)
        assertEquals("MANUAL_COMPOSED_REPLY", result.mailType)
        assertTrue(result.subject.contains("Subject"))

        val sentMail = sentMails.single()
        assertEquals(true, sentMail.html)
        assertNotNull(sentMail.text)
        assertFalse(sentMail.text!!.contains("<p>"))
        assertTrue(sentMail.body.contains("<p>"))

        val mailCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(mailCaptor.capture())
        assertEquals(10L, mailCaptor.value.matchedQaRuleId)
        assertEquals("MANUAL_COMPOSED_REPLY", mailCaptor.value.mailType)
        assertTrue(mailCaptor.value.body!!.contains("Body one"))
        assertTrue(mailCaptor.value.body!!.contains("Body two"))

        val qaRuleCaptor = ArgumentCaptor.forClass(MailRecordQaRule::class.java)
        Mockito.verify(mailRecordQaRuleRepository, Mockito.times(2)).save(qaRuleCaptor.capture())
        val savedAssociations = qaRuleCaptor.allValues
        assertEquals(listOf(10L, 11L), savedAssociations.map { it.qaRuleId })
        assertEquals(listOf(0, 1), savedAssociations.map { it.ordinal })

        @Suppress("UNCHECKED_CAST")
        val afterCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        Mockito.verify(operatorActionLogService).record(
            anyValue(""), anyValue(0L), anyValue(OperatorActionType.SEND_MANUAL_COMPOSED_REPLY),
            anyValue(0L), anyValue(0L),
            anyValue(null), afterCaptor.capture(),
            anyValue(""), anyValue(""), anyValue(null)
        )
        val after = afterCaptor.value!!
        assertEquals(listOf(10L, 11L), after["qaRuleIds"])
        assertEquals(false, after["edited"])
    }

    @Test
    fun `send manual composed reply renders expert placeholders in outbound body`() {
        val record = inbound(1)
        val rule = qaRule(10, 1, "At \${institution}, here is the detail.")
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-render-composed", status = "SUCCESS")
        val renderedBody = "At Oxford, here is the detail."

        stubExpertProfile(contact.orcidId, institution = "Oxford")
        stubDefaultFrame()
        stubResolveAck()
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaMatchService.suggestComposition("body")).thenReturn(stubSuggest(listOf(10)))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 301) }
        Mockito.`when`(mailRecordQaRuleRepository.save(anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))))
            .thenAnswer { invocation ->
                val arg = invocation.getArgument<MailRecordQaRule>(0)
                arg.copy(id = 1L)
            }

        service.sendManualComposedReply(1, listOf(10), null, null, null, null, "op")

        val sentMail = sentMails.single()
        assertTrue(sentMail.text!!.contains("Oxford"))
        assertFalse(sentMail.text!!.contains("\${institution"))
        val mailCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(mailCaptor.capture())
        assertTrue(mailCaptor.value.body!!.contains("Oxford"))
        assertFalse(mailCaptor.value.body!!.contains("\${"))
    }

    @Test
    fun `send manual composed reply rejects disabled rule`() {
        val record = inbound(1)
        val disabledRule = qaRule(10, 1, "Body", enabled = false)

        stubDefaultFrame()
        stubResolveAck()
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaMatchService.suggestComposition("body")).thenReturn(stubSuggest())
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(disabledRule))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.sendManualComposedReply(1, listOf(10), null, null, null, null, "op")
        }
        assertTrue(ex.message!!.contains("disabled"))
        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(stubAccount()), anyValue(ComposedMail("stub", "stub", "stub"))
        )
    }

    @Test
    fun `send manual composed reply marks edited when override differs`() {
        val record = inbound(1)
        val rule = qaRule(10, 1, "Body one")
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-edited", status = "SUCCESS")

        stubDefaultFrame()
        stubResolveAck()
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaMatchService.suggestComposition("body")).thenReturn(stubSuggest(listOf(10)))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 301) }
        Mockito.`when`(mailRecordQaRuleRepository.save(anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))))
            .thenAnswer { it.getArgument<MailRecordQaRule>(0).copy(id = 1) }

        service.sendManualComposedReply(1, listOf(10), "Custom edited body", null, null, null, "op")

        @Suppress("UNCHECKED_CAST")
        val afterCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        Mockito.verify(operatorActionLogService).record(
            anyValue(""), anyValue(0L), anyValue(OperatorActionType.SEND_MANUAL_COMPOSED_REPLY),
            anyValue(0L), anyValue(0L),
            anyValue(null), afterCaptor.capture(),
            anyValue(""), anyValue(""), anyValue(null)
        )
        assertEquals(true, afterCaptor.value!!["edited"])
    }

    @Test
    fun `send manual composed reply preserves operator qaRuleIds order in body`() {
        val record = inbound(1)
        val ruleA = qaRule(10, 1, "Body A", priority = 10)
        val ruleB = qaRule(11, 2, "Body B", priority = 100)
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-order", status = "SUCCESS")

        stubDefaultFrame()
        stubResolveAck()
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaMatchService.suggestComposition("body")).thenReturn(stubSuggest(listOf(10, 11)))
        Mockito.`when`(qaRuleRepository.findById(11L)).thenReturn(Optional.of(ruleB))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(ruleA))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 302) }
        Mockito.`when`(mailRecordQaRuleRepository.save(anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))))
            .thenAnswer { it.getArgument<MailRecordQaRule>(0).copy(id = 1) }

        service.sendManualComposedReply(1, listOf(11, 10), null, null, null, null, "op")

        val htmlBody = sentMails.single().body
        assertTrue(htmlBody.indexOf("Body B") < htmlBody.indexOf("Body A"))

        val mailCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(mailCaptor.capture())
        val body = mailCaptor.value.body!!
        assertTrue(body.indexOf("Body B") < body.indexOf("Body A"))

        val qaRuleCaptor = ArgumentCaptor.forClass(MailRecordQaRule::class.java)
        Mockito.verify(mailRecordQaRuleRepository, Mockito.times(2)).save(qaRuleCaptor.capture())
        assertEquals(listOf(11L, 10L), qaRuleCaptor.allValues.map { it.qaRuleId })
        assertEquals(listOf(0, 1), qaRuleCaptor.allValues.map { it.ordinal })
    }

    @Test
    fun `send manual composed reply includes ack after salutation when ackSnippetId provided`() {
        val record = inbound(1)
        val rule = qaRule(10, 1, "Body one")
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-ack", status = "SUCCESS")

        stubDefaultFrame()
        Mockito.`when`(replySnippetService.resolveAck(null)).thenReturn(null)
        Mockito.`when`(replySnippetService.resolveAck(100L)).thenReturn("Thank you for sharing your CV.")
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaMatchService.suggestComposition("body")).thenReturn(stubSuggest(listOf(10)))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 303) }
        Mockito.`when`(mailRecordQaRuleRepository.save(anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))))
            .thenAnswer { it.getArgument<MailRecordQaRule>(0).copy(id = 1) }

        service.sendManualComposedReply(1, listOf(10), null, null, 100L, null, "op")

        val text = sentMails.single().text!!
        val salIndex = text.indexOf("Dear Professor,")
        val ackIndex = text.indexOf("Thank you for sharing your CV.")
        val greetIndex = text.indexOf(QaReplyComposer.GREETING)
        val bodyIndex = text.indexOf("Body one")
        assertTrue(salIndex >= 0)
        assertTrue(salIndex < ackIndex)
        assertTrue(ackIndex < greetIndex)
        assertTrue(greetIndex < bodyIndex)
        assertEquals(text, sentMails.single().text)
        assertTrue(text.contains("\n\n"))
    }

    @Test
    fun `send manual composed reply omits ack when ackSnippetId is null or invalid`() {
        val record = inbound(1)
        val rule = qaRule(10, 1, "Body one")
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-no-ack", status = "SUCCESS")

        stubDefaultFrame()
        Mockito.`when`(replySnippetService.resolveAck(null)).thenReturn(null)
        Mockito.`when`(replySnippetService.resolveAck(999L)).thenReturn(null)
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaMatchService.suggestComposition("body")).thenReturn(stubSuggest(listOf(10)))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 304) }
        Mockito.`when`(mailRecordQaRuleRepository.save(anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))))
            .thenAnswer { it.getArgument<MailRecordQaRule>(0).copy(id = 1) }

        service.sendManualComposedReply(1, listOf(10), null, null, null, null, "op")
        assertFalse(sentMails.single().text!!.contains("Thank you for sharing your CV."))

        sentMails.clear()
        service.sendManualComposedReply(1, listOf(10), null, null, 999L, null, "op")
        assertFalse(sentMails.single().text!!.contains("Thank you for sharing your CV."))
    }

    @Test
    fun `send qa reply uses inbound sender account when request account is null`() {
        val record = inbound(1).copy(senderAccountCode = "LiLei")
        val rule = QaRule(
            id = 10, categoryId = 1, keywords = "test",
            replySubject = "QA Subject", replyBody = "QA Body",
            displayName = "Test QA Rule", enabled = true
        )
        val account = stubAccount().copy(accountCode = "LiLei")
        val delivered = DeliveredMail(messageId = "msg-lilei", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("LiLei")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 203) }

        service.sendQaReply(1, 10, null, "op")

        Mockito.verify(mailSenderAccountService).getManualSendAccount("LiLei")
        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
        assertEquals("LiLei", recordCaptor.value.senderAccountCode)
    }

    @Test
    fun `send manual rich reply uses explicit sender account when provided`() {
        val record = inbound(1).copy(senderAccountCode = "LiLei")
        val lukaiAccount = stubAccount().copy(accountCode = "LuKai")
        val delivered = DeliveredMail(messageId = "msg-lukai", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("LuKai")).thenReturn(lukaiAccount)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(lukaiAccount), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 201) }
        Mockito.`when`(mailBodyCleaner.clean("<p>Hello</p>")).thenReturn("Hello")

        service.sendManualRichReply(1, "LuKai", "Hello", "<p>Hello</p>", null, "op")

        Mockito.verify(mailSenderAccountService).getManualSendAccount("LuKai")
        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
        assertEquals("LuKai", recordCaptor.value.senderAccountCode)
    }

    @Test
    fun `send manual composed reply uses inbound sender account when request account is null`() {
        val record = inbound(1).copy(senderAccountCode = "LiLei", cleanedBody = "question")
        val rule = qaRule(10, 1, "Composed Body")
        val account = stubAccount().copy(accountCode = "LiLei")
        val delivered = DeliveredMail(messageId = "msg-composed", status = "SUCCESS")

        stubDefaultFrame()
        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaMatchService.suggestComposition("question")).thenReturn(stubSuggest(listOf(10)))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("LiLei")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 204) }
        Mockito.`when`(mailRecordQaRuleRepository.save(anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))))
            .thenAnswer { it.getArgument<MailRecordQaRule>(0).copy(id = 1) }

        service.sendManualComposedReply(1, listOf(10), null, null, null, null, "op")

        Mockito.verify(mailSenderAccountService).getManualSendAccount("LiLei")
        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
        assertEquals("LiLei", recordCaptor.value.senderAccountCode)
    }

    @Test
    fun `send manual rich reply works with disabled inbound account`() {
        val record = inbound(1).copy(senderAccountCode = "LiLei")
        val disabledAccount = stubAccount().copy(accountCode = "LiLei", enabled = false)
        val delivered = DeliveredMail(messageId = "msg-disabled", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("LiLei")).thenReturn(disabledAccount)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(disabledAccount), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 205) }
        Mockito.`when`(mailBodyCleaner.clean("<p>Hi</p>")).thenReturn("Hi")

        val result = service.sendManualRichReply(1, null, "Hi", "<p>Hi</p>", null, "op")

        assertEquals("SUCCESS", result.sendStatus)
        assertEquals("LiLei", result.senderAccountCode)
    }

    @Test
    fun `send manual rich reply with templateTextBody re-renders for final sender account`() {
        val record = inbound(1).copy(senderAccountCode = "accountA")
        val accountB = stubAccount().copy(
            accountCode = "accountB",
            senderName = "Bob B",
            senderEmail = "bob@test.com"
        )
        val delivered = DeliveredMail(messageId = "msg-tpl-b", status = "SUCCESS")
        stubExpertProfile(contact.orcidId)

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("accountB")).thenReturn(accountB)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(accountB), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 401) }

        val result = service.sendManualRichReply(
            inboundProcessingId = 1,
            senderAccountCode = "accountB",
            subject = "Hello",
            htmlBody = "<p>Hi from Alice A</p>",
            textBody = "Hi from Alice A",
            operatorName = "op",
            templateTextBody = "Hi from \${senderName}"
        )

        assertEquals("SUCCESS", result.sendStatus)
        assertEquals("accountB", result.senderAccountCode)
        val sentMail = sentMails.single()
        assertEquals("Hi from Bob B", sentMail.text)
        assertEquals(mailContentService.plainTextToHtml("Hi from Bob B"), sentMail.body)
        assertFalse(sentMail.text!!.contains("\${"))
        assertFalse(sentMail.text!!.contains("Alice"))
        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
        assertEquals("Hi from Bob B", recordCaptor.value.body)
    }

    @Test
    fun `send manual rich reply without template fields re-renders placeholders for final sender`() {
        val record = inbound(1).copy(senderAccountCode = "accountA")
        val accountB = stubAccount().copy(
            accountCode = "accountB",
            senderName = "Bob B",
            senderEmail = "bob@test.com"
        )
        val delivered = DeliveredMail(messageId = "msg-no-tpl-render", status = "SUCCESS")
        stubExpertProfile(contact.orcidId)

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("accountB")).thenReturn(accountB)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(accountB), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 402) }

        val result = service.sendManualRichReply(
            inboundProcessingId = 1,
            senderAccountCode = "accountB",
            subject = "Hello",
            htmlBody = "<p>Hi \${senderName}</p>",
            textBody = "Hi \${senderName}",
            operatorName = "op"
        )

        assertEquals("SUCCESS", result.sendStatus)
        assertEquals("accountB", result.senderAccountCode)
        val sentMail = sentMails.single()
        assertEquals("Hi Bob B", sentMail.text)
        assertTrue(sentMail.body.contains("Bob B"))
        assertFalse(sentMail.body.contains("\${"))
        assertFalse(sentMail.text!!.contains("\${"))
        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
        assertEquals("Hi Bob B", recordCaptor.value.body)
        @Suppress("UNCHECKED_CAST")
        val logCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        Mockito.verify(operatorActionLogService).record(
            anyValue(""), anyValue(0L), anyValue(OperatorActionType.SEND_MANUAL_RICH_REPLY),
            anyValue(0L), anyValue(0L),
            anyValue(null), logCaptor.capture(),
            anyValue(""), anyValue(""), anyValue(null)
        )
        assertEquals("Hi Bob B", logCaptor.value!!["bodyPreviewText"])
    }

    @Test
    fun `send manual rich reply without template fields rejects unknown token before delivery`() {
        val record = inbound(1)
        val account = stubAccount()

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)

        assertThrows(IllegalArgumentException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 1,
                senderAccountCode = null,
                subject = "Hello",
                htmlBody = "<p>Hello \${unknownKey}</p>",
                textBody = "Hello \${unknownKey}",
                operatorName = "op"
            )
        }

        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )
        Mockito.verify(mailRecordRepository, Mockito.never()).save(anyValue(stubMailRecord))
    }

    @Test
    fun `send manual rich reply rejects unknown token in templateTextBody`() {
        val record = inbound(1)
        val account = stubAccount()

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)

        assertThrows(IllegalArgumentException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 1,
                senderAccountCode = null,
                subject = "Hello",
                htmlBody = "<p>Hi</p>",
                textBody = "Hi",
                operatorName = "op",
                templateTextBody = "Hello \${unknownKey}"
            )
        }

        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )
        Mockito.verify(mailRecordRepository, Mockito.never()).save(anyValue(stubMailRecord))
    }

    @Test
    fun `send manual rich reply with templateHtmlBody uses renderHtmlForContact`() {
        val record = inbound(1)
        val account = stubAccount().copy(senderName = "A&B <Team>")
        val delivered = DeliveredMail(messageId = "msg-tpl-html", status = "SUCCESS")
        stubExpertProfile(contact.orcidId, familyNames = "Lovelace")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 403) }

        service.sendManualRichReply(
            inboundProcessingId = 1,
            senderAccountCode = null,
            subject = "Hello",
            htmlBody = "<p>stale preview</p>",
            textBody = "stale preview",
            operatorName = "op",
            templateTextBody = "Hi from \${senderName}",
            templateHtmlBody = "<p>Hi from \${senderName}</p>"
        )

        val sentMail = sentMails.single()
        assertEquals("Hi from A&B <Team>", sentMail.text)
        assertEquals("<p>Hi from A&amp;B &lt;Team&gt;</p>", sentMail.body)
        assertTrue(sentMail.body.startsWith("<p>"))
        assertTrue(sentMail.body.endsWith("</p>"))
        assertFalse(sentMail.body.contains("\${"))
        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
        assertEquals("Hi from A&B <Team>", recordCaptor.value.body)
    }

    @Test
    fun `send manual rich reply with special senderName in templateHtmlBody escapes delivery HTML`() {
        val record = inbound(1)
        val account = stubAccount().copy(senderName = "Tom & Jerry <HQ>")
        val delivered = DeliveredMail(messageId = "msg-tpl-escape", status = "SUCCESS")
        stubExpertProfile(contact.orcidId)

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            delivered
        }
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 404) }

        service.sendManualRichReply(
            inboundProcessingId = 1,
            senderAccountCode = null,
            subject = "Hello",
            htmlBody = "<p>preview</p>",
            textBody = "From \${senderName}",
            operatorName = "op",
            templateHtmlBody = "<div>From \${senderName}</div>"
        )

        val sentMail = sentMails.single()
        assertEquals("<div>From Tom &amp; Jerry &lt;HQ&gt;</div>", sentMail.body)
        // hasTemplate without templateTextBody: text falls back to textBody then renderForContact
        assertEquals("From Tom & Jerry <HQ>", sentMail.text)
        assertFalse(sentMail.text!!.contains("&amp;"))
    }

    @Test
    fun `send manual rich reply with template and qaRuleIds keeps composed audit contract`() {
        val record = inbound(1)
        val rule1 = qaRule(10, 1, "Body A")
        val rule2 = qaRule(11, 1, "Body B")
        val account = stubAccount().copy(senderName = "Bob")
        val delivered = DeliveredMail(messageId = "msg-tpl-qa", status = "SUCCESS")
        stubExpertProfile(contact.orcidId)

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule1))
        Mockito.`when`(qaRuleRepository.findById(11L)).thenReturn(Optional.of(rule2))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 405) }
        Mockito.`when`(mailRecordQaRuleRepository.save(anyValue(MailRecordQaRule(mailRecordId = 0, qaRuleId = 0, ordinal = 0))))
            .thenAnswer { invocation ->
                val arg = invocation.getArgument<MailRecordQaRule>(0)
                arg.copy(id = arg.ordinal + 1L)
            }

        val result = service.sendManualRichReply(
            inboundProcessingId = 1,
            senderAccountCode = null,
            subject = "Subject",
            htmlBody = "<p>stale</p>",
            textBody = "stale",
            operatorName = "op",
            qaRuleIds = listOf(10, 11),
            suggestedRuleIds = listOf(10, 11),
            templateTextBody = "Hi from \${senderName}"
        )

        assertEquals("SUCCESS", result.sendStatus)
        assertEquals("MANUAL_RICH_REPLY", result.mailType)

        val qaRuleCaptor = ArgumentCaptor.forClass(MailRecordQaRule::class.java)
        Mockito.verify(mailRecordQaRuleRepository, Mockito.times(2)).save(qaRuleCaptor.capture())
        assertEquals(listOf(10L, 11L), qaRuleCaptor.allValues.map { it.qaRuleId })
        assertEquals(listOf(0, 1), qaRuleCaptor.allValues.map { it.ordinal })

        @Suppress("UNCHECKED_CAST")
        val afterCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        Mockito.verify(operatorActionLogService).record(
            anyValue(""), anyValue(0L), anyValue(OperatorActionType.SEND_MANUAL_COMPOSED_REPLY),
            anyValue(0L), anyValue(0L),
            anyValue(null), afterCaptor.capture(),
            anyValue(""), anyValue(""), anyValue(null)
        )
        assertEquals(listOf(10L, 11L), afterCaptor.value!!["qaRuleIds"])

        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(recordCaptor.capture())
        assertEquals("Hi from Bob", recordCaptor.value.body)
    }

    @Test
    fun `send manual rich reply rejects when gate validateConfirmationForSend throws`() {
        val record = inbound(1)
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-blocked-reject", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 400) }
        Mockito.`when`(mailBodyCleaner.clean("<p>Body</p>")).thenReturn("Body")

        Mockito.doThrow(IllegalArgumentException("must provide draftIdentity"))
            .`when`(aiReplyReviewAuditService)
            .validateConfirmationForSend(anyValue(0L), anyNullable(), anyValue(emptyList<String>()), anyValue(""))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.sendManualRichReply(
                inboundProcessingId = 1,
                senderAccountCode = null,
                subject = "Test",
                htmlBody = "<p>Body</p>",
                textBody = "Body",
                operatorName = "op",
                aiReviewConfirmation = null
            )
        }
        assertTrue(ex.message!!.contains("must provide draftIdentity"))
        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(stubAccount()), anyValue(ComposedMail("stub", "stub", "stub"))
        )
    }

    @Test
    fun `send manual rich reply with valid draftIdentity confirmation sends and records audit`() {
        val record = inbound(1)
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-review-ok", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 401) }
        Mockito.`when`(mailBodyCleaner.clean("<p>Body</p>")).thenReturn("Body")

        val confirmation = AiReviewConfirmation(
            draftIdentity = "uuid-123",
            confirmedReviewKeys = listOf("1:a"),
            operatorNote = "Reviewed"
        )

        val result = service.sendManualRichReply(
            inboundProcessingId = 1,
            senderAccountCode = null,
            subject = "Test",
            htmlBody = "<p>Body</p>",
            textBody = "Body",
            operatorName = "op",
            aiReviewConfirmation = confirmation
        )

        assertEquals("SUCCESS", result.sendStatus)
        Mockito.verify(aiReplyReviewAuditService).recordConfirmed(
            anyValue(0L), anyValue(0L), anyValue(401L),
            anyValue(confirmation), anyValue("op")
        )
    }

    @Test
    fun `send manual rich reply does not record audit when no draft identity`() {
        val record = inbound(1)
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-manual", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 403) }
        Mockito.`when`(mailBodyCleaner.clean("<p>Body</p>")).thenReturn("Body")

        service.sendManualRichReply(
            inboundProcessingId = 1,
            senderAccountCode = null,
            subject = "Test",
            htmlBody = "<p>Body</p>",
            textBody = "Body",
            operatorName = "op",
            replySource = null,
            aiReviewConfirmation = null
        )

        Mockito.verify(aiReplyReviewAuditService, Mockito.never()).recordConfirmed(
            anyValue(0L), anyValue(0L), anyValue(0L),
            anyNullable(), anyValue("op")
        )
    }

    @Test
    fun `gate validateConfirmationForSend is always called regardless of aiReviewConfirmation`() {
        val record = inbound(1)
        val account = stubAccount()
        val delivered = DeliveredMail(messageId = "msg-gate-always", status = "SUCCESS")

        Mockito.`when`(inboundMailProcessingRepository.findById(1L)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender")).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 404) }
        Mockito.`when`(mailBodyCleaner.clean("<p>Body</p>")).thenReturn("Body")

        // No aiReviewConfirmation at all
        service.sendManualRichReply(
            inboundProcessingId = 1,
            senderAccountCode = null,
            subject = "Test",
            htmlBody = "<p>Body</p>",
            textBody = "Body",
            operatorName = "op"
        )

        Mockito.verify(aiReplyReviewAuditService).validateConfirmationForSend(
            anyValue(1L), anyNullable(), anyValue(emptyList<String>()), anyValue("")
        )
    }

    @Test
    fun `request DTO deserializes draftIdentity confirmation`() {
        val json = """
            {
                "senderAccountCode": "s",
                "subject": "Sub",
                "htmlBody": "<p>x</p>",
                "textBody": "x",
                "operatorName": "op",
                "replySource": "AI_DRAFT",
                "aiReviewConfirmation": {
                    "draftIdentity": "abc-123-def",
                    "confirmedReviewKeys": ["1:a"],
                    "operatorNote": "checked"
                }
            }
        """.trimIndent()
        val req = jsonMapper.readValue(json, PendingManualRichReplyRequest::class.java)
        assertEquals("AI_DRAFT", req.replySource)
        assertNotNull(req.aiReviewConfirmation)
        assertEquals("abc-123-def", req.aiReviewConfirmation!!.draftIdentity)
        assertEquals(listOf("1:a"), req.aiReviewConfirmation!!.confirmedReviewKeys)
        assertEquals("checked", req.aiReviewConfirmation!!.operatorNote)
    }
}
