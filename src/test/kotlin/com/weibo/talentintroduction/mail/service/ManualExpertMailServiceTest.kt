package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.template.repository.MailTemplateRepository
import com.weibo.talentintroduction.template.service.MailTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
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
    private val mailTemplateRepository = Mockito.mock(MailTemplateRepository::class.java)
    private val mailTemplateService = Mockito.mock(MailTemplateService::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val conversationStateService = Mockito.mock(ConversationStateService::class.java)
    private val service = ManualExpertMailService(
        expertContactRepository,
        mailRecordRepository,
        mailSenderAccountService,
        mailSenderAccountRepository,
        mailDeliveryService,
        mailTemplateRepository,
        mailTemplateService,
        qaRuleRepository,
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

    private fun stubQaSend(account: MailSenderAccount) {
        val rule = QaRule(
            id = 10,
            categoryId = 1,
            keywords = "test",
            replySubject = "QA Subject",
            replyBody = "QA Body",
            displayName = "Test QA Rule",
            enabled = true
        )
        val delivered = DeliveredMail(messageId = "msg-1", status = "SUCCESS")

        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(rule))
        Mockito.`when`(mailSenderAccountService.selectAccountForManualSending()).thenReturn(account)
        Mockito.`when`(mailDeliveryService.send(
            anyValue(account), anyValue(ComposedMail("stub", "stub", "stub"))
        )).thenReturn(delivered)
        Mockito.`when`(mailRecordRepository.save(anyValue(stubMailRecord)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 100) }
        Mockito.`when`(conversationStateService.transition(
            anyValue(contact),
            eqValue(ConversationStatus.QA_AUTO_REPLIED),
            eqValue("MANUAL_MAIL_MANUAL_QA_REPLY"),
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
        stubQaSend(account)

        service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "QA", optionValue = "10", senderAccountCode = null)
        )

        val captor = ArgumentCaptor.forClass(MailSenderAccount::class.java)
        Mockito.verify(mailSenderAccountRepository).save(captor.capture())
        assertEquals(5, captor.value.todaySentCount)
        assertNotNull(captor.value.lastSentAt)
    }

    @Test
    fun `sendManualMail succeeds when only enabled account is at daily limit`() {
        val account = stubAccount(todaySentCount = 100, dailySendLimit = 100)
        stubQaSend(account)

        val result = service.sendManualMail(
            1,
            ManualMailSendCommand(optionType = "QA", optionValue = "10", senderAccountCode = null)
        )

        assertEquals("SUCCESS", result.sendStatus)
        Mockito.verify(mailSenderAccountService).selectAccountForManualSending()
        Mockito.verify(mailSenderAccountService, Mockito.never()).selectAccountForSending()
    }
}
