package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class MailSenderAccountServiceTest {
    private val repository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val service = MailSenderAccountService(repository)

    @Test
    fun `selects enabled account with highest weighted remaining capacity`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("hot", strategyWeight = 100, dailySendLimit = 100, todaySentCount = 95),
                account("balanced", strategyWeight = 80, dailySendLimit = 100, todaySentCount = 10),
                account("exhausted", strategyWeight = 200, dailySendLimit = 100, todaySentCount = 100)
            )
        )

        val selected = service.selectAccountForSending()

        assertEquals("balanced", selected.accountCode)
    }

    @Test
    fun `creates sender account when account code is unique`() {
        Mockito.`when`(repository.existsByAccountCode("new_account")).thenReturn(false)
        Mockito.`when`(repository.save(Mockito.any(MailSenderAccount::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as MailSenderAccount }

        val created = service.createAccount(
            MailSenderAccountCreateCommand(
                accountCode = "new_account",
                senderEmail = "new_account@qftechtalent.com",
                senderName = "New Account",
                senderTitle = "Customer Care Officer",
                senderDisplayName = "New Account",
                teamName = "Qingfei Tech Talent Team",
                countryName = "China",
                smtpHost = "smtp.example.com",
                smtpPort = 465,
                smtpUsername = "new_account@qftechtalent.com",
                smtpPassword = "secret",
                imapHost = "imap.example.com",
                imapPort = 993,
                imapUsername = "new_account@qftechtalent.com",
                imapPassword = "secret",
                strategyWeight = 120,
                dailySendLimit = 80
            )
        )

        assertEquals("new_account", created.accountCode)
        assertEquals(120, created.strategyWeight)
        assertEquals(80, created.dailySendLimit)
        assertNotNull(created.createdAt)
        assertNotNull(created.updatedAt)
    }

    @Test
    fun `disables sender account`() {
        Mockito.`when`(repository.findByAccountCode("a1"))
            .thenReturn(account("a1", strategyWeight = 100, dailySendLimit = 100, todaySentCount = 0))
        Mockito.`when`(repository.save(Mockito.any(MailSenderAccount::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as MailSenderAccount }

        val disabled = service.setEnabled("a1", false)

        assertFalse(disabled.enabled)
    }

    @Test
    fun `resets today sent count`() {
        Mockito.`when`(repository.findByAccountCode("a1"))
            .thenReturn(account("a1", strategyWeight = 100, dailySendLimit = 100, todaySentCount = 25))
        Mockito.`when`(repository.save(Mockito.any(MailSenderAccount::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as MailSenderAccount }

        val reset = service.resetTodaySentCount("a1")

        assertEquals(0, reset.todaySentCount)
    }

    @Test
    fun `listAutoReceiveAccounts returns enabled real accounts only`() {
        Mockito.`when`(repository.findAllByEnabledTrueAndAccountCodeNot("SIMULATOR_NOOP")).thenReturn(
            listOf(account("a1"), account("a2"))
        )

        val result = service.listAutoReceiveAccounts()

        assertEquals(2, result.size)
        assertTrue(result.all { it.enabled })
        assertTrue(result.none { it.accountCode == "SIMULATOR_NOOP" })
    }

    @Test
    fun `listAutoReceiveAccounts excludes SIMULATOR_NOOP`() {
        Mockito.`when`(repository.findAllByEnabledTrueAndAccountCodeNot("SIMULATOR_NOOP")).thenReturn(
            emptyList()
        )

        val result = service.listAutoReceiveAccounts()

        assertEquals(0, result.size)
    }

    @Test
    fun `getAutoReceiveAccount returns enabled real account`() {
        Mockito.`when`(repository.findByAccountCode("real_acct")).thenReturn(
            account("real_acct", enabled = true)
        )

        val result = service.getAutoReceiveAccount("real_acct")

        assertEquals("real_acct", result.accountCode)
    }

    @Test
    fun `getAutoReceiveAccount rejects SIMULATOR_NOOP`() {
        Mockito.`when`(repository.findByAccountCode("SIMULATOR_NOOP")).thenReturn(
            account("SIMULATOR_NOOP", enabled = true)
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            service.getAutoReceiveAccount("SIMULATOR_NOOP")
        }
        assertTrue(ex.message!!.contains("not allowed"))
    }

    @Test
    fun `getAutoReceiveAccount rejects disabled account`() {
        Mockito.`when`(repository.findByAccountCode("disabled_acct")).thenReturn(
            account("disabled_acct", enabled = false)
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            service.getAutoReceiveAccount("disabled_acct")
        }
        assertTrue(ex.message!!.contains("disabled"))
    }

    @Test
    fun `getAutoReceiveAccount rejects unknown account`() {
        Mockito.`when`(repository.findByAccountCode("unknown")).thenReturn(null)

        val ex = assertThrows(IllegalStateException::class.java) {
            service.getAutoReceiveAccount("unknown")
        }
        assertTrue(ex.message!!.contains("not found"))
    }

    @Test
    fun `listEnabledAccounts unchanged returns all enabled including simulator`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(account("a1"), account("SIMULATOR_NOOP"))
        )

        val result = service.listEnabledAccounts()

        assertEquals(2, result.size)
        assertTrue(result.any { it.accountCode == "SIMULATOR_NOOP" })
    }

    private fun account(
        accountCode: String,
        strategyWeight: Int = 100,
        dailySendLimit: Int = 100,
        todaySentCount: Int = 0,
        enabled: Boolean = true
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
            strategyWeight = strategyWeight,
            dailySendLimit = dailySendLimit,
            todaySentCount = todaySentCount,
            enabled = enabled
        )
}
