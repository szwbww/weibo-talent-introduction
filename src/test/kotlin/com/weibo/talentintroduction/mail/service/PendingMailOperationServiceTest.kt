package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.CategoryRulesGroup
import com.weibo.talentintroduction.qa.service.CompositionSuggestResult
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.qa.service.QaReplyComposer
import com.weibo.talentintroduction.reply.service.AckOption
import com.weibo.talentintroduction.reply.service.ManualReplyFrame
import com.weibo.talentintroduction.reply.service.ReplySnippetService
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
        replySnippetService
    )

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

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
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
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
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 203) }

        val result = service.sendQaReply(1, 10, null, "op")

        assertEquals("SUCCESS", result.sendStatus)
        Mockito.verify(mailSenderAccountService).selectAccountForManualSending()
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
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
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
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
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
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
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
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
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
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
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
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
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
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
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
}
