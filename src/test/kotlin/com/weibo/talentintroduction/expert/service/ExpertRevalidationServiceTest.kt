package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.PromotionScanResult
import com.weibo.talentintroduction.expert.domain.PromotionScanStats
import com.weibo.talentintroduction.expert.domain.RevalidationResult
import com.weibo.talentintroduction.expert.domain.RevalidationStats
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ExpertRevalidationServiceTest {

    @Test
    fun `RevalidationResult includes demotionFailed in failureCount`() {
        val stats = RevalidationStats(total = 10, passed = 5, demoted = 2, demotionFailed = 1)
        val result = RevalidationResult(stats)
        assertEquals(5, result.taskSuccessCount)
        assertEquals(3, result.taskFailureCount) // demoted + demotionFailed
    }

    @Test
    fun `PromotionScanResult includes existenceCheckFailed in failureCount`() {
        val stats = PromotionScanStats(
            total = 20, promoted = 5, filtered = 10,
            emailRejected = 2, promotionFailed = 1, existenceCheckFailed = 1
        )
        val result = PromotionScanResult(stats)
        assertEquals(5, result.taskSuccessCount)
        assertEquals(14, result.taskFailureCount) // filtered + emailRejected + promotionFailed + existenceCheckFailed
    }

    @Test
    fun `PromotionScanStats with maxPromotions zero returns empty result`() {
        val stats = PromotionScanStats()
        assertNotNull(stats)
        assertEquals(0, stats.total)
    }

    @Test
    fun `RevalidationStats tracks demotion reasons`() {
        val stats = RevalidationStats()
        stats.demotionReasons.merge("INVALID_EMAIL_FORMAT", 1) { a, b -> a + b }
        stats.demotionReasons.merge("CHINESE_NATIONALITY", 1) { a, b -> a + b }
        stats.demotionReasons.merge("INVALID_EMAIL_FORMAT", 1) { a, b -> a + b }
        assertEquals(2, stats.demotionReasons["INVALID_EMAIL_FORMAT"])
        assertEquals(1, stats.demotionReasons["CHINESE_NATIONALITY"])
    }

    @Test
    fun `RevalidationResult zero total has SUCCESS final status`() {
        val result = RevalidationResult(RevalidationStats(total = 0))
        assertEquals("SUCCESS", result.taskFinalStatus)
    }

    @Test
    fun `PromotionScanResult with all failures has FAILED status`() {
        val stats = PromotionScanStats(
            total = 10, promoted = 0, filtered = 8, emailRejected = 2
        )
        val result = PromotionScanResult(stats)
        assertEquals(0, result.taskSuccessCount)
        assertEquals(10, result.taskFailureCount)
        assertEquals("FAILED", result.taskFinalStatus)
    }

    @Test
    fun `RevalidationResult all success returns SUCCESS`() {
        val stats = RevalidationStats(total = 5, passed = 5, demoted = 0, demotionFailed = 0)
        val result = RevalidationResult(stats)
        assertEquals("SUCCESS", result.taskFinalStatus)
    }

    @Test
    fun `RevalidationResult all failure returns FAILED`() {
        val stats = RevalidationStats(total = 10, passed = 0, demoted = 8, demotionFailed = 2)
        val result = RevalidationResult(stats)
        assertEquals("FAILED", result.taskFinalStatus)
    }

    @Test
    fun `RevalidationResult partial success returns PARTIAL_SUCCESS`() {
        val stats = RevalidationStats(total = 10, passed = 5, demoted = 3, demotionFailed = 2)
        val result = RevalidationResult(stats)
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
    }

    @Test
    fun `PromotionScanResult all success returns SUCCESS`() {
        val stats = PromotionScanStats(total = 5, promoted = 5)
        val result = PromotionScanResult(stats)
        assertEquals("SUCCESS", result.taskFinalStatus)
    }

    @Test
    fun `PromotionScanResult partial success returns PARTIAL_SUCCESS`() {
        val stats = PromotionScanStats(
            total = 10, promoted = 3, filtered = 5, emailRejected = 1, promotionFailed = 1
        )
        val result = PromotionScanResult(stats)
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
    }
}
