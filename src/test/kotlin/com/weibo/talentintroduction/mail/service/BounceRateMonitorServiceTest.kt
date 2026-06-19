package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.time.LocalDateTime

class BounceRateMonitorServiceTest {
    private val bounceRecordRepository = Mockito.mock(BounceRecordRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val service = BounceRateMonitorService(
        bounceRecordRepository,
        mailRecordRepository,
        mailSenderAccountService
    )

    @Test
    fun `pauses account when hard bounce rate exceeds threshold`() {
        Mockito.`when`(
            bounceRecordRepository.countHardBouncesSince(
                ArgumentMatchers.eq("acct1") ?: "acct1",
                anyTime()
            )
        ).thenReturn(2L)
        Mockito.`when`(
            mailRecordRepository.countSentByAccountSince(
                ArgumentMatchers.eq("acct1") ?: "acct1",
                anyTime()
            )
        ).thenReturn(20L)

        val rate = service.checkAndPause("acct1")

        assertEquals(0.1, rate, 0.0001)
        Mockito.verify(mailSenderAccountService).pauseAutoSend(
            ArgumentMatchers.eq("acct1") ?: "acct1",
            ArgumentMatchers.eq("BOUNCE_RATE_HIGH:10.00%") ?: ""
        )
    }

    @Test
    fun `does not pause when sample size is below minimum`() {
        Mockito.`when`(
            bounceRecordRepository.countHardBouncesSince(
                ArgumentMatchers.eq("acct1") ?: "acct1",
                anyTime()
            )
        ).thenReturn(2L)
        Mockito.`when`(
            mailRecordRepository.countSentByAccountSince(
                ArgumentMatchers.eq("acct1") ?: "acct1",
                anyTime()
            )
        ).thenReturn(10L)

        val rate = service.checkAndPause("acct1")

        assertEquals(-1.0, rate, 0.0001)
        Mockito.verify(mailSenderAccountService, Mockito.never()).pauseAutoSend(
            ArgumentMatchers.anyString() ?: "",
            ArgumentMatchers.anyString() ?: ""
        )
    }

    @Test
    fun `does not pause when there are no hard bounces`() {
        Mockito.`when`(
            bounceRecordRepository.countHardBouncesSince(
                ArgumentMatchers.eq("acct1") ?: "acct1",
                anyTime()
            )
        ).thenReturn(0L)
        Mockito.`when`(
            mailRecordRepository.countSentByAccountSince(
                ArgumentMatchers.eq("acct1") ?: "acct1",
                anyTime()
            )
        ).thenReturn(50L)

        val rate = service.checkAndPause("acct1")

        assertEquals(0.0, rate, 0.0001)
        Mockito.verify(mailSenderAccountService, Mockito.never()).pauseAutoSend(
            ArgumentMatchers.anyString() ?: "",
            ArgumentMatchers.anyString() ?: ""
        )
    }

    private fun anyTime(): LocalDateTime =
        ArgumentMatchers.any(LocalDateTime::class.java) ?: LocalDateTime.now()
}
