package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.service.MailAccountConnectivityResult
import com.weibo.talentintroduction.mail.service.MailAccountConnectivityService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.SelfCheckResult
import com.weibo.talentintroduction.mail.service.SenderAccountSelfCheckService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(MailSenderAccountController::class)
class MailSenderAccountControllerMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: MailSenderAccountService

    @MockBean
    private lateinit var connectivityService: MailAccountConnectivityService

    @MockBean
    private lateinit var selfCheckService: SenderAccountSelfCheckService

    @Test
    fun `listAccounts response exposes auto-pause fields`() {
        Mockito.`when`(service.listAccounts()).thenReturn(
            listOf(
                account("a1", autoSendPaused = true, autoSendPausedReason = "SELF_CHECK_FAILED:boom"),
                account("a2", autoSendPaused = false)
            )
        )

        mockMvc.perform(get("/api/mail/sender-accounts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].accountCode").value("a1"))
            .andExpect(jsonPath("$[0].autoSendPaused").value(true))
            .andExpect(jsonPath("$[0].autoSendPausedReason").value("SELF_CHECK_FAILED:boom"))
            .andExpect(jsonPath("$[1].accountCode").value("a2"))
            .andExpect(jsonPath("$[1].autoSendPaused").value(false))
            .andExpect(jsonPath("$[1].autoSendPausedReason").isEmpty)
    }

    @Test
    fun `resumeAutoSend endpoint delegates to service`() {
        Mockito.`when`(service.getAccount("a1")).thenReturn(account("a1"))

        mockMvc.perform(post("/api/mail/sender-accounts/a1/resume-auto-send"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accountCode").value("a1"))
            .andExpect(jsonPath("$.autoSendPaused").value(false))

        Mockito.verify(service).resumeAutoSend("a1")
    }

    @Test
    fun `selfCheck endpoint delegates to selfCheckService and returns result`() {
        Mockito.`when`(selfCheckService.checkSendable(Mockito.any(MailSenderAccount::class.java) ?: account("__any__")))
            .thenReturn(SelfCheckResult(accountCode = "a1", passed = true, message = null, fromCache = false))
        Mockito.`when`(service.getAccount("a1")).thenReturn(account("a1"))

        mockMvc.perform(post("/api/mail/sender-accounts/a1/self-check"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accountCode").value("a1"))
            .andExpect(jsonPath("$.passed").value(true))
            .andExpect(jsonPath("$.fromCache").value(false))
    }

    private fun account(
        accountCode: String,
        autoSendPaused: Boolean = false,
        autoSendPausedReason: String? = null,
        autoSendPausedAt: LocalDateTime? = null
    ): MailSenderAccount =
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
            imapPassword = "secret",
            autoSendPaused = autoSendPaused,
            autoSendPausedReason = autoSendPausedReason,
            autoSendPausedAt = autoSendPausedAt
        )
}
