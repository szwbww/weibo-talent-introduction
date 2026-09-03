package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.time.LocalDateTime

class BounceRateMonitorServiceTest {
    private val bounceRecordRepository = Mockito.mock(BounceRecordRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val service = BounceRateMonitorService(
        bounceRecordRepository,
        mailRecordRepository
    )

    private fun stubCounts(hardBounces: Long, sent: Long) {
        Mockito.`when`(
            bounceRecordRepository.countHardBouncesSince(
                ArgumentMatchers.eq("acct1") ?: "acct1",
                anyTime()
            )
        ).thenReturn(hardBounces)
        Mockito.`when`(
            mailRecordRepository.countSentByAccountSince(
                ArgumentMatchers.eq("acct1") ?: "acct1",
                anyTime()
            )
        ).thenReturn(sent)
    }

    @Test
    fun `flags high at ten percent rate 2 of 20`() {
        stubCounts(2L, 20L)

        assertEquals(0.1, service.calculateHardBounceRate("acct1"), 0.0001)
        assertTrue(service.isHardBounceRateHigh("acct1"))
    }

    @Test
    fun `does not flag at exactly five percent 1 of 20`() {
        stubCounts(1L, 20L)

        assertEquals(0.05, service.calculateHardBounceRate("acct1"), 0.0001)
        assertFalse(service.isHardBounceRateHigh("acct1"))
    }

    @Test
    fun `below minimum sample returns minus one and does not flag`() {
        stubCounts(2L, 10L)

        assertEquals(-1.0, service.calculateHardBounceRate("acct1"), 0.0001)
        assertFalse(service.isHardBounceRateHigh("acct1"))
    }

    @Test
    fun `zero hard bounces of 50 returns zero and does not flag`() {
        stubCounts(0L, 50L)

        assertEquals(0.0, service.calculateHardBounceRate("acct1"), 0.0001)
        assertFalse(service.isHardBounceRateHigh("acct1"))
    }

    @Test
    fun `checkAndWarn on 2 of 20 returns rate and has no sender-account collaborator to mutate`() {
        stubCounts(2L, 20L)

        assertEquals(0.1, service.checkAndWarn("acct1"), 0.0001)
    }

    private fun anyTime(): LocalDateTime =
        ArgumentMatchers.any(LocalDateTime::class.java) ?: LocalDateTime.now()
}
