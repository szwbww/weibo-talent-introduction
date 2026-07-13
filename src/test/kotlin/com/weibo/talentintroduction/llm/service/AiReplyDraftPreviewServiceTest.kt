package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.mail.service.RenderPreviewResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AiReplyDraftPreviewServiceTest {

    private val mailVariableService = Mockito.mock(MailVariableService::class.java)
    private val mailSenderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val service = AiReplyDraftPreviewService(mailVariableService, mailSenderAccountRepository)

    private val contact = ExpertContact(
        id = 10L,
        campaignId = 1L,
        orcidId = "0000-0000-0000-0001",
        expertName = "Dr. Test",
        expertEmail = "test@example.com",
        currentStatus = "WAITING_REPLY"
    )

    private val account = MailSenderAccount(
        accountCode = "a1",
        senderEmail = "sender@example.com",
        senderName = "Sender",
        senderTitle = null,
        senderDisplayName = "Sender",
        teamName = null,
        countryName = null,
        smtpHost = "smtp.example.com",
        smtpPort = 587,
        smtpUsername = "u",
        smtpPassword = "p",
        imapHost = "imap.example.com",
        imapPort = 993,
        imapUsername = "u",
        imapPassword = "p"
    )

    @Test
    fun `blank account code returns raw with account-not-found warning`() {
        val raw = "Hello \${senderName}"
        val result = service.preview(raw, contact, "  ")
        assertEquals(raw, result.renderedText)
        assertEquals(listOf(AiReplyDraftPreviewService.WARNING_ACCOUNT_NOT_FOUND), result.warningCodes)
        Mockito.verifyNoInteractions(mailVariableService)
        Mockito.verifyNoInteractions(mailSenderAccountRepository)
    }

    @Test
    fun `null account code returns raw with account-not-found warning`() {
        val raw = "Hello"
        val result = service.preview(raw, contact, null)
        assertEquals(raw, result.renderedText)
        assertEquals(listOf(AiReplyDraftPreviewService.WARNING_ACCOUNT_NOT_FOUND), result.warningCodes)
        Mockito.verifyNoInteractions(mailVariableService)
    }

    @Test
    fun `missing account returns raw with account-not-found warning`() {
        val raw = "Hello \${senderName}"
        Mockito.`when`(mailSenderAccountRepository.findByAccountCode("a1")).thenReturn(null)
        val result = service.preview(raw, contact, "a1")
        assertEquals(raw, result.renderedText)
        assertEquals(listOf(AiReplyDraftPreviewService.WARNING_ACCOUNT_NOT_FOUND), result.warningCodes)
        Mockito.verifyNoInteractions(mailVariableService)
    }

    @Test
    fun `valid account renders preview without warnings`() {
        val raw = "Hello \${senderName}"
        Mockito.`when`(mailSenderAccountRepository.findByAccountCode("a1")).thenReturn(account)
        Mockito.`when`(mailVariableService.renderPreview(raw, account, contact)).thenReturn(
            RenderPreviewResult(
                rendered = "Hello Sender",
                fallbackKeys = emptyList(),
                variables = emptyList(),
                invalidTokens = emptyList()
            )
        )
        val result = service.preview(raw, contact, "a1")
        assertEquals("Hello Sender", result.renderedText)
        assertEquals(emptyList<String>(), result.warningCodes)
    }

    @Test
    fun `invalid placeholders add warning but keep rendered text`() {
        val raw = "Hello \${bogus}"
        Mockito.`when`(mailSenderAccountRepository.findByAccountCode("a1")).thenReturn(account)
        Mockito.`when`(mailVariableService.renderPreview(raw, account, contact)).thenReturn(
            RenderPreviewResult(
                rendered = "Hello \${bogus}",
                fallbackKeys = emptyList(),
                variables = emptyList(),
                invalidTokens = listOf("\${bogus}")
            )
        )
        val result = service.preview(raw, contact, "a1")
        assertEquals("Hello \${bogus}", result.renderedText)
        assertEquals(listOf(AiReplyDraftPreviewService.WARNING_INVALID_PLACEHOLDER), result.warningCodes)
    }
}
