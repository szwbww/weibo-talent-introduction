package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
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
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val service = PendingMailOperationService(
        inboundMailProcessingRepository,
        expertContactRepository,
        expertOperatorStatusService,
        expertIndexLevelOperationService,
        mailSenderAccountService,
        mailDeliveryService,
        mailRecordRepository,
        operatorActionLogService,
        qaRuleRepository
    )

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    private val contact = ExpertContact(
        id = 1,
        campaignId = 1,
        orcidId = "ORCID-001",
        expertEmail = "expert@example.com",
        expertName = "Expert",
        currentStatus = "INTRO_SENT",
        operatorStatus = "CONTACTED"
    )

    private fun inbound(id: Long, contactId: Long? = null) = InboundMailProcessing(
        id = id,
        senderAccountCode = "account1",
        imapUid = 100,
        messageId = "msg-$id",
        inReplyTo = null,
        fromEmail = "expert@example.com",
        subject = "Test",
        receivedAt = LocalDateTime.now(),
        processStatus = "MANUAL_REVIEW",
        processReason = "CONTACT_NOT_FOUND",
        reasonType = "UNMATCHED_CONTACT",
        expertContactId = contactId
    )

    @Test
    fun `change status rejects unbound inbound`() {
        val record = inbound(1)
        Mockito.`when`(inboundMailProcessingRepository.findById(1L))
            .thenReturn(Optional.of(record))

        val ex = assertThrows(IllegalStateException::class.java) {
            service.changeOperatorStatus(1, "REPLIED", "op", null)
        }
        assertTrue(ex.message!!.contains("not bound"))
    }

    @Test
    fun `change status calls ExpertOperatorStatusService and writes log`() {
        val record = inbound(1, 1)
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
    fun `change index level rejects unbound inbound`() {
        val record = inbound(1)
        Mockito.`when`(inboundMailProcessingRepository.findById(1L))
            .thenReturn(Optional.of(record))

        val ex = assertThrows(IllegalStateException::class.java) {
            service.changeIndexLevel(1, "APPLICATION", "op", null)
        }
        assertTrue(ex.message!!.contains("not bound"))
    }

    @Test
    fun `change index level calls ExpertIndexLevelOperationService`() {
        val record = inbound(1, 1)
        Mockito.`when`(inboundMailProcessingRepository.findById(1L))
            .thenReturn(Optional.of(record))
        Mockito.`when`(expertIndexLevelOperationService.changeLevel(
            anyValue(1L), anyValue("APPLICATION"), anyValue("op"), anyValue("note"), anyValue(1L)
        )).thenReturn(contact.copy(currentIndexLevel = "APPLICATION", applicationIndexed = true))

        val result = service.changeIndexLevel(1, "APPLICATION", "op", "note")

        assertEquals("APPLICATION", result.currentIndexLevel)
    }

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
    fun `send qa reply writes SEND_QA_REPLY log`() {
        val record = inbound(1, 1)
        val rule = QaRule(
            id = 10,
            categoryId = 1,
            keywords = "test",
            replySubject = "QA Subject",
            replyBody = "QA Body",
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
        Mockito.`when`(mailSenderAccountService.selectAccountForSending()).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))))
            .thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord))).thenAnswer {
            it.getArgument<MailRecord>(0).copy(id = 200)
        }

        val result = service.sendQaReply(1, 10, null, "op")

        assertEquals("SUCCESS", result.sendStatus)
        Mockito.verify(mailRecordRepository).save(anyValue(stubMailRecord))
    }

    @Test
    fun `send manual rich reply writes SEND_MANUAL_RICH_REPLY log`() {
        val record = inbound(1, 1)
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
        Mockito.`when`(mailSenderAccountService.selectAccountForSending()).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))))
            .thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord))).thenAnswer {
            it.getArgument<MailRecord>(0).copy(id = 201)
        }

        val result = service.sendManualRichReply(1, null, "Hello", "<p>Hello</p>", null, "op")

        assertEquals("SUCCESS", result.sendStatus)
        assertEquals("MANUAL_RICH_REPLY", result.mailType)
        Mockito.verify(mailRecordRepository).save(anyValue(stubMailRecord))
    }

    @Test
    fun `mark resolved updates status and writes log`() {
        val record = inbound(1, 1)
        Mockito.`when`(inboundMailProcessingRepository.findById(1L))
            .thenReturn(Optional.of(record))
        Mockito.`when`(inboundMailProcessingRepository.save(anyValue(record))).thenAnswer {
            it.getArgument<InboundMailProcessing>(0)
        }
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
}
