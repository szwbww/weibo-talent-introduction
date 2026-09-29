package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `getStats reports 9 of 160 with one count per repository and one shared cutoff`() {
        val hardCalls = mutableListOf<LocalDateTime>()
        val sentCalls = mutableListOf<LocalDateTime>()
        val statsService = BounceRateMonitorService(
            countingBounceRepository(9L, hardCalls),
            countingSentRepository(160L, sentCalls)
        )

        val before = LocalDateTime.now()
        val stats = statsService.getStats("acct1")
        val after = LocalDateTime.now()

        assertEquals(9L, stats.hardBounceCount)
        assertEquals(160L, stats.sentCount)
        assertEquals(0.05625, stats.rate ?: -1.0, 1e-12)
        assertTrue(stats.sampleSufficient)
        assertEquals(7, stats.windowDays)
        assertTrue(stats.high)

        // I-1：每个 COUNT 恰好一次，且两次复用同一 since = now - 7 天。
        assertEquals(1, hardCalls.size)
        assertEquals(1, sentCalls.size)
        assertEquals(hardCalls[0], sentCalls[0])
        assertFalse(hardCalls[0].isBefore(before.minusDays(7)))
        assertFalse(hardCalls[0].isAfter(after.minusDays(7)))
    }

    @Test
    fun `getStats reports 0 of 0 as insufficient sample instead of a fake zero rate`() {
        stubCounts(0L, 0L)

        val stats = service.getStats("acct1")

        assertEquals(0L, stats.hardBounceCount)
        assertEquals(0L, stats.sentCount)
        assertNull(stats.rate)
        assertFalse(stats.sampleSufficient)
        assertFalse(stats.high)
    }

    @Test
    fun `getStats reports 2 of 19 as insufficient sample just below the floor`() {
        stubCounts(2L, 19L)

        val stats = service.getStats("acct1")

        assertEquals(2L, stats.hardBounceCount)
        assertEquals(19L, stats.sentCount)
        assertNull(stats.rate)
        assertFalse(stats.sampleSufficient)
        assertFalse(stats.high)
    }

    @Test
    fun `getStats reports 1 of 20 as exactly five percent without a warning`() {
        stubCounts(1L, 20L)

        val stats = service.getStats("acct1")

        assertEquals(0.05, stats.rate ?: -1.0, 1e-12)
        assertTrue(stats.sampleSufficient)
        assertFalse(stats.high)
    }

    @Test
    fun `getStats reports 2 of 20 as ten percent with a warning`() {
        stubCounts(2L, 20L)

        val stats = service.getStats("acct1")

        assertEquals(0.1, stats.rate ?: -1.0, 1e-12)
        assertTrue(stats.sampleSufficient)
        assertTrue(stats.high)
    }

    @Test
    fun `getStats reports 30 of 20 as 1 point 5 without capping the rate at one`() {
        stubCounts(30L, 20L)

        val stats = service.getStats("acct1")

        // I-2：分子分母可能不是同一批邮件，比率不封顶 100%。
        assertEquals(1.5, stats.rate ?: -1.0, 1e-12)
        assertTrue(stats.sampleSufficient)
        assertTrue(stats.high)
    }

    @Test
    fun `getStats uses the requested window days for the shared cutoff`() {
        val hardCalls = mutableListOf<LocalDateTime>()
        val sentCalls = mutableListOf<LocalDateTime>()
        val statsService = BounceRateMonitorService(
            countingBounceRepository(3L, hardCalls),
            countingSentRepository(40L, sentCalls)
        )

        val before = LocalDateTime.now()
        val stats = statsService.getStats("acct1", 3)
        val after = LocalDateTime.now()

        assertEquals(3, stats.windowDays)
        assertEquals(hardCalls[0], sentCalls[0])
        assertFalse(hardCalls[0].isBefore(before.minusDays(3)))
        assertFalse(hardCalls[0].isAfter(after.minusDays(3)))
    }

    @Test
    fun `checkAndWarn keeps the custom window days and threshold behavior`() {
        val hardCalls = mutableListOf<LocalDateTime>()
        val sentCalls = mutableListOf<LocalDateTime>()
        val statsService = BounceRateMonitorService(
            countingBounceRepository(1L, hardCalls),
            countingSentRepository(20L, sentCalls)
        )

        val before = LocalDateTime.now()
        // 1/20 = 0.05 > 自定义阈值 0.01，仍按调用方给出的 windowDays 查询。
        assertEquals(0.05, statsService.checkAndWarn("acct1", windowDays = 3, threshold = 0.01), 1e-12)
        assertFalse(hardCalls[0].isBefore(before.minusDays(3)))
        assertEquals(hardCalls[0], sentCalls[0])
    }

    @Test
    fun `checkAndWarn keeps minus one on a low sample`() {
        stubCounts(2L, 10L)

        assertEquals(-1.0, service.checkAndWarn("acct1"), 0.0001)
    }

    /** 记录每次调用的 since 并返回固定计数；用于证明「恰好一次」而无 Mockito verify 语义依赖。 */
    private fun countingBounceRepository(
        hardBounces: Long,
        calls: MutableList<LocalDateTime>
    ): BounceRecordRepository {
        val repository = Mockito.mock(BounceRecordRepository::class.java)
        Mockito.`when`(
            repository.countHardBouncesSince(
                ArgumentMatchers.anyString() ?: "acct1",
                ArgumentMatchers.any(LocalDateTime::class.java) ?: LocalDateTime.now()
            )
        ).thenAnswer { invocation ->
            calls.add(invocation.getArgument(1))
            hardBounces
        }
        return repository
    }

    private fun countingSentRepository(
        sent: Long,
        calls: MutableList<LocalDateTime>
    ): MailRecordRepository {
        val repository = Mockito.mock(MailRecordRepository::class.java)
        Mockito.`when`(
            repository.countSentByAccountSince(
                ArgumentMatchers.anyString() ?: "acct1",
                ArgumentMatchers.any(LocalDateTime::class.java) ?: LocalDateTime.now()
            )
        ).thenAnswer { invocation ->
            calls.add(invocation.getArgument(1))
            sent
        }
        return repository
    }

    private fun anyTime(): LocalDateTime =
        ArgumentMatchers.any(LocalDateTime::class.java) ?: LocalDateTime.now()
}
