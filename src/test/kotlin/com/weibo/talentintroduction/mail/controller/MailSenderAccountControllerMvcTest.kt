package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.service.BounceRateMonitorService
import com.weibo.talentintroduction.mail.service.MailAccountConnectivityService
import com.weibo.talentintroduction.mail.service.MailSenderAccountCreateCommand
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailSenderAccountUpdateCommand
import com.weibo.talentintroduction.mail.service.SelfCheckResult
import com.weibo.talentintroduction.mail.service.SenderAccountSelfCheckService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.http.MediaType
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

    @MockBean
    private lateinit var bounceRateMonitorService: BounceRateMonitorService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `listAccounts response exposes auto-pause fields`() {
        val accounts = listOf(
            account("a1", autoSendPaused = true, autoSendPausedReason = "SELF_CHECK_FAILED:boom"),
            account("a2", autoSendPaused = false)
        )
        Mockito.`when`(service.listAccounts()).thenReturn(accounts)
        Mockito.`when`(service.effectiveDailyLimitFor(accounts[0])).thenReturn(100)
        Mockito.`when`(service.effectiveDailyLimitFor(accounts[1])).thenReturn(80)
        Mockito.`when`(bounceRateMonitorService.isHardBounceRateHigh("a1")).thenReturn(true)
        Mockito.`when`(bounceRateMonitorService.isHardBounceRateHigh("a2")).thenReturn(false)

        mockMvc.perform(get("/api/mail/sender-accounts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].accountCode").value("a1"))
            .andExpect(jsonPath("$[0].autoSendPaused").value(true))
            .andExpect(jsonPath("$[0].autoSendPausedReason").value("SELF_CHECK_FAILED:boom"))
            .andExpect(jsonPath("$[0].effectiveDailyLimit").value(100))
            .andExpect(jsonPath("$[0].hardBounceRateHigh").value(true))
            .andExpect(jsonPath("$[1].accountCode").value("a2"))
            .andExpect(jsonPath("$[1].autoSendPaused").value(false))
            .andExpect(jsonPath("$[1].autoSendPausedReason").isEmpty)
            .andExpect(jsonPath("$[1].effectiveDailyLimit").value(80))
            .andExpect(jsonPath("$[1].hardBounceRateHigh").value(false))
    }

    @Test
    fun `resumeAutoSend endpoint delegates to service`() {
        val account = account("a1")
        Mockito.`when`(service.getAccount("a1")).thenReturn(account)
        Mockito.`when`(service.effectiveDailyLimitFor(account)).thenReturn(100)

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
        val account = account("a1")
        Mockito.`when`(service.getAccount("a1")).thenReturn(account)
        Mockito.`when`(service.effectiveDailyLimitFor(account)).thenReturn(100)

        mockMvc.perform(post("/api/mail/sender-accounts/a1/self-check"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accountCode").value("a1"))
            .andExpect(jsonPath("$.passed").value(true))
            .andExpect(jsonPath("$.fromCache").value(false))
    }

    @Test
    fun `deleteAccount endpoint delegates to service`() {
        mockMvc.perform(delete("/api/mail/sender-accounts/a1"))
            .andExpect(status().isNoContent)

        Mockito.verify(service).deleteAccount("a1")
    }

    @Test
    fun `createAccount binds and echoes the inboundMailboxCode json property`() {
        val created = account("alias", inboundMailboxCode = "owner")
        Mockito.`when`(
            service.createAccount(
                Mockito.any(MailSenderAccountCreateCommand::class.java) ?: anyCreateCommand()
            )
        ).thenReturn(created)
        Mockito.`when`(service.effectiveDailyLimitFor(created)).thenReturn(100)
        Mockito.`when`(bounceRateMonitorService.isHardBounceRateHigh("alias")).thenReturn(false)

        mockMvc.perform(
            post("/api/mail/sender-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestBody(inboundMailboxCode = "owner"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accountCode").value("alias"))
            .andExpect(jsonPath("$.inboundMailboxCode").value("owner"))

        val captor = ArgumentCaptor.forClass(MailSenderAccountCreateCommand::class.java)
        Mockito.verify(service).createAccount(captor.capture() ?: anyCreateCommand())
        assertEquals("owner", captor.value.inboundMailboxCode)
    }

    @Test
    fun `createAccount keeps legacy payloads without the ownership field independent`() {
        val created = account("alias")
        Mockito.`when`(
            service.createAccount(
                Mockito.any(MailSenderAccountCreateCommand::class.java) ?: anyCreateCommand()
            )
        ).thenReturn(created)
        Mockito.`when`(service.effectiveDailyLimitFor(created)).thenReturn(100)
        Mockito.`when`(bounceRateMonitorService.isHardBounceRateHigh("alias")).thenReturn(false)

        mockMvc.perform(
            post("/api/mail/sender-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestBody())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.inboundMailboxCode").isEmpty)

        val captor = ArgumentCaptor.forClass(MailSenderAccountCreateCommand::class.java)
        Mockito.verify(service).createAccount(captor.capture() ?: anyCreateCommand())
        assertEquals(null, captor.value.inboundMailboxCode)
    }

    @Test
    fun `updateAccount round-trips the inboundMailboxCode json property`() {
        val updated = account("alias", inboundMailboxCode = "owner")
        Mockito.`when`(
            service.updateAccount(
                Mockito.eq("alias") ?: "alias",
                Mockito.any(MailSenderAccountUpdateCommand::class.java) ?: anyUpdateCommand()
            )
        ).thenReturn(updated)
        Mockito.`when`(service.effectiveDailyLimitFor(updated)).thenReturn(100)
        Mockito.`when`(bounceRateMonitorService.isHardBounceRateHigh("alias")).thenReturn(false)

        mockMvc.perform(
            put("/api/mail/sender-accounts/alias")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestBody(inboundMailboxCode = "owner"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.inboundMailboxCode").value("owner"))

        val captor = ArgumentCaptor.forClass(MailSenderAccountUpdateCommand::class.java)
        Mockito.verify(service).updateAccount(Mockito.eq("alias") ?: "alias", captor.capture() ?: anyUpdateCommand())
        assertEquals("owner", captor.value.inboundMailboxCode)
    }

    @Test
    fun `updateAccount maps an illegal shared inbox relation to 400`() {
        Mockito.`when`(
            service.updateAccount(
                Mockito.eq("alias") ?: "alias",
                Mockito.any(MailSenderAccountUpdateCommand::class.java) ?: anyUpdateCommand()
            )
        ).thenThrow(IllegalArgumentException("共享收件箱主账号不能指向自己：alias"))

        mockMvc.perform(
            put("/api/mail/sender-accounts/alias")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestBody(inboundMailboxCode = "alias"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("共享收件箱主账号不能指向自己：alias"))
    }

    private fun createRequestBody(inboundMailboxCode: String? = null): String =
        objectMapper.writeValueAsString(
            mutableMapOf<String, Any?>(
                "accountCode" to "alias",
                "senderEmail" to "alias@qftechtalent.com",
                "senderName" to "alias",
                "senderTitle" to "Customer Care Officer",
                "senderDisplayName" to "alias",
                "teamName" to "Qingfei Tech Talent Team",
                "countryName" to "China",
                "smtpHost" to "smtp.example.com",
                "smtpPort" to 465,
                "smtpUsername" to "alias@qftechtalent.com",
                "smtpPassword" to "secret",
                "imapHost" to "imap.example.com",
                "imapPort" to 993,
                "imapUsername" to "alias@qftechtalent.com",
                "imapPassword" to "secret",
                "strategyWeight" to 100,
                "dailySendLimit" to 100,
                "enabled" to true
            ).apply {
                if (inboundMailboxCode != null) put("inboundMailboxCode", inboundMailboxCode)
            }
        )

    private fun updateRequestBody(inboundMailboxCode: String? = null): String =
        objectMapper.writeValueAsString(
            mutableMapOf<String, Any?>(
                "senderEmail" to "alias@qftechtalent.com",
                "senderName" to "alias",
                "senderTitle" to "Customer Care Officer",
                "senderDisplayName" to "alias",
                "teamName" to "Qingfei Tech Talent Team",
                "countryName" to "China",
                "smtpHost" to "smtp.example.com",
                "smtpPort" to 465,
                "smtpUsername" to "alias@qftechtalent.com",
                "smtpPassword" to null,
                "imapHost" to "imap.example.com",
                "imapPort" to 993,
                "imapUsername" to "alias@qftechtalent.com",
                "imapPassword" to null,
                "strategyWeight" to 100,
                "dailySendLimit" to 100,
                "todaySentCount" to 0,
                "enabled" to true
            ).apply {
                if (inboundMailboxCode != null) put("inboundMailboxCode", inboundMailboxCode)
            }
        )

    /** Mockito 匹配器返回 null，Kotlin 非空参数需要真实占位实参（同 createAccount/updateAccount 的既有写法）。 */
    private fun anyCreateCommand(): MailSenderAccountCreateCommand =
        MailSenderAccountCreateCommand(
            accountCode = "__any__",
            senderEmail = "__any__@qftechtalent.com",
            senderName = "__any__",
            senderTitle = null,
            senderDisplayName = null,
            teamName = null,
            countryName = null,
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "__any__",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "__any__",
            imapPassword = "secret"
        )

    private fun anyUpdateCommand(): MailSenderAccountUpdateCommand =
        MailSenderAccountUpdateCommand(
            senderEmail = "__any__@qftechtalent.com",
            senderName = "__any__",
            senderTitle = null,
            senderDisplayName = null,
            teamName = null,
            countryName = null,
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "__any__",
            smtpPassword = null,
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "__any__",
            imapPassword = null,
            strategyWeight = 100,
            dailySendLimit = 100,
            todaySentCount = 0,
            enabled = false
        )

    private fun account(
        accountCode: String,
        autoSendPaused: Boolean = false,
        autoSendPausedReason: String? = null,
        autoSendPausedAt: LocalDateTime? = null,
        inboundMailboxCode: String? = null
    ): MailSenderAccount =
        MailSenderAccount(
            accountCode = accountCode,
            senderEmail = "$accountCode@qftechtalent.com",
            senderName = accountCode,
            senderTitle = "Customer Care Officer",
            senderDisplayName = accountCode,
            teamName = "Qingfei Tech Talent Team",
            countryName = "China",
            inboundMailboxCode = inboundMailboxCode,
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
