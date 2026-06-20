package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.config.WarmupStep
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SenderWarmupServiceTest {
    private val defaultSteps = listOf(
        WarmupStep(1, 20),
        WarmupStep(3, 40),
        WarmupStep(5, 80),
        WarmupStep(8, 160),
        WarmupStep(12, 320)
    )

    @Test
    fun `disabled warmup returns dailySendLimit unchanged`() {
        val service = SenderWarmupService(WarmupProperties(enabled = false))
        val account = account(dailySendLimit = 500, createdAt = LocalDateTime.now())

        assertEquals(500, service.effectiveDailyLimit(account))
    }

    @Test
    fun `day 1 account returns lowest ramp step`() {
        val service = SenderWarmupService(WarmupProperties(enabled = true, steps = defaultSteps))
        val now = LocalDateTime.of(2026, 6, 20, 12, 0)
        val account = account(dailySendLimit = 500, createdAt = now)

        assertEquals(20, service.effectiveDailyLimit(account, now))
    }

    @Test
    fun `day 10 account returns higher ramp step`() {
        val service = SenderWarmupService(WarmupProperties(enabled = true, steps = defaultSteps))
        val now = LocalDateTime.of(2026, 6, 20, 12, 0)
        val account = account(dailySendLimit = 500, createdAt = now.minusDays(9))

        assertEquals(160, service.effectiveDailyLimit(account, now))
    }

    @Test
    fun `ramp step above dailySendLimit caps at dailySendLimit`() {
        val service = SenderWarmupService(WarmupProperties(enabled = true, steps = defaultSteps))
        val now = LocalDateTime.of(2026, 6, 20, 12, 0)
        val account = account(dailySendLimit = 50, createdAt = now.minusDays(9))

        assertEquals(50, service.effectiveDailyLimit(account, now))
    }

    @Test
    fun `null createdAt returns dailySendLimit`() {
        val service = SenderWarmupService(WarmupProperties(enabled = true, steps = defaultSteps))
        val account = account(dailySendLimit = 200, createdAt = null)

        assertEquals(200, service.effectiveDailyLimit(account))
    }

    private fun account(
        dailySendLimit: Int,
        createdAt: LocalDateTime?
    ): MailSenderAccount =
        MailSenderAccount(
            accountCode = "a1",
            senderEmail = "a1@qftechtalent.com",
            senderName = "a1",
            senderTitle = null,
            senderDisplayName = null,
            teamName = null,
            countryName = null,
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "a1@qftechtalent.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "a1@qftechtalent.com",
            imapPassword = "secret",
            dailySendLimit = dailySendLimit,
            createdAt = createdAt
        )
}
