package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.service.BatchSendConfig
import com.weibo.talentintroduction.campaign.service.BatchSendSettingService
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.time.LocalDateTime

class SenderAccountSelfCheckServiceTest {
    private val repository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val configService = Mockito.mock(BatchSendSettingService::class.java)
    private val probeSender = Mockito.mock(SelfCheckProbeSender::class.java)
    private val fixedNow = LocalDateTime.of(2026, 6, 18, 10, 0, 0)
    private val timeProvider = { fixedNow }
    private val service = SenderAccountSelfCheckService(repository, configService, probeSender, timeProvider)

    @Test
    fun `checkSendable returns passed result and invokes probe on first call`() {
        Mockito.`when`(configService.getConfig()).thenReturn(batchConfig(selfCheckTtlMinutes = 30))

        val result = service.checkSendable(account("a1"))

        assertTrue(result.passed)
        assertFalse(result.fromCache)
        assertEquals("a1", result.accountCode)
        Mockito.verify(probeSender).sendProbe(anyAccount())
    }

    @Test
    fun `checkSendable returns cached result without probing on second call within TTL`() {
        Mockito.`when`(configService.getConfig()).thenReturn(batchConfig(selfCheckTtlMinutes = 30))

        service.checkSendable(account("a1"))
        val second = service.checkSendable(account("a1"))

        assertTrue(second.passed)
        assertTrue(second.fromCache)
        Mockito.verify(probeSender, Mockito.times(1)).sendProbe(anyAccount())
    }

    @Test
    fun `checkSendable pauses account with SELF_CHECK_FAILED reason when probe throws`() {
        Mockito.`when`(configService.getConfig()).thenReturn(batchConfig(selfCheckTtlMinutes = 30))
        Mockito.doThrow(RuntimeException("smtp timeout")).`when`(probeSender).sendProbe(anyAccount())

        val result = service.checkSendable(account("a1"))

        assertFalse(result.passed)
        assertFalse(result.fromCache)
        assertTrue(result.message!!.startsWith("SELF_CHECK_FAILED:"))
        assertTrue(result.message!!.contains("smtp timeout"))
        Mockito.verify(repository).pauseAutoSend(
            ArgumentMatchers.eq("a1") ?: "a1",
            ArgumentMatchers.startsWith("SELF_CHECK_FAILED:") ?: "",
            ArgumentMatchers.eq(fixedNow) ?: fixedNow
        )
    }

    @Test
    fun `checkSendable does not increment today_sent_count when probe fails`() {
        Mockito.`when`(configService.getConfig()).thenReturn(batchConfig(selfCheckTtlMinutes = 30))
        Mockito.doThrow(RuntimeException("boom")).`when`(probeSender).sendProbe(anyAccount())

        service.checkSendable(account("a1"))

        Mockito.verify(repository, Mockito.never())
            .incrementTodaySentCount(anyString() ?: "", anyTime() ?: fixedNow)
    }

    @Test
    fun `checkSendable does not pause account when probe succeeds`() {
        Mockito.`when`(configService.getConfig()).thenReturn(batchConfig(selfCheckTtlMinutes = 30))

        service.checkSendable(account("a1"))

        Mockito.verify(repository, Mockito.never())
            .pauseAutoSend(anyString() ?: "", anyString() ?: "", anyTime() ?: fixedNow)
    }

    @Test
    fun `invalidate forces next checkSendable to re-probe`() {
        Mockito.`when`(configService.getConfig()).thenReturn(batchConfig(selfCheckTtlMinutes = 30))

        service.checkSendable(account("a1"))
        service.invalidate("a1")
        service.checkSendable(account("a1"))

        Mockito.verify(probeSender, Mockito.times(2)).sendProbe(anyAccount())
    }

    @Test
    fun `checkSendable re-probes when cached entry is expired by TTL`() {
        Mockito.`when`(configService.getConfig()).thenReturn(batchConfig(selfCheckTtlMinutes = 0))

        service.checkSendable(account("a1"))
        val second = service.checkSendable(account("a1"))

        assertFalse(second.fromCache)
        Mockito.verify(probeSender, Mockito.times(2)).sendProbe(anyAccount())
    }

    private fun batchConfig(selfCheckTtlMinutes: Int): BatchSendConfig =
        BatchSendConfig(
            autoEnabled = false,
            cron = "0 0 0 * * ?",
            dailyCap = 1000,
            roundSize = 50,
            perMailIntervalMs = 1000L,
            perRoundIntervalMs = 60000L,
            selfCheckTtlMinutes = selfCheckTtlMinutes
        )

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

    private fun anyAccount(): MailSenderAccount =
        ArgumentMatchers.any(MailSenderAccount::class.java) ?: account("__any__")

    private fun anyString(): String =
        ArgumentMatchers.anyString() ?: "__any__"

    private fun anyTime(): LocalDateTime =
        ArgumentMatchers.any(LocalDateTime::class.java) ?: fixedNow
}
