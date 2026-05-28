package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class BatchAutoMailReplyServiceTest {
    private val accountService = Mockito.mock(MailSenderAccountService::class.java)
    private val autoReplyService = Mockito.mock(AutoMailReplyService::class.java)
    private val service = BatchAutoMailReplyService(accountService, autoReplyService)

    @Test
    fun `polls all enabled accounts and aggregates results`() {
        Mockito.`when`(accountService.listEnabledAccounts()).thenReturn(
            listOf(account("a1"), account("a2"))
        )
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a1", 5))
            .thenReturn(AutoMailReplyBatchResult(fetched = 1, recorded = 1, replied = 1, manualReview = 0))
        Mockito.`when`(autoReplyService.receiveAndAutoReply("a2", 5))
            .thenReturn(AutoMailReplyBatchResult(fetched = 2, recorded = 2, replied = 1, manualReview = 1))

        val result = service.receiveAndAutoReplyAll(5)

        assertEquals(2, result.accountCount)
        assertEquals(3, result.fetched)
        assertEquals(3, result.recorded)
        assertEquals(2, result.replied)
        assertEquals(1, result.manualReview)
    }

    private fun account(accountCode: String): MailSenderAccount =
        MailSenderAccount(
            accountCode = accountCode,
            senderEmail = "$accountCode@qftechtalent.com",
            senderName = accountCode,
            senderTitle = "Customer Care Officer",
            senderDisplayName = accountCode,
            teamName = "Qingfei Tech Talent Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "$accountCode@qftechtalent.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "$accountCode@qftechtalent.com",
            imapPassword = "secret"
        )
}
