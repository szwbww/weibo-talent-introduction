package com.weibo.talentintroduction.discovery.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiscoveryResultTest {

    private fun sourceStats(
        name: String,
        indexed: Int = 0,
        sourceFailureCount: Int = 0,
        pendingWork: Boolean = false
    ) = SourceStats(
        sourceName = name,
        extractionMethod = "FULLTEXT_XML",
        indexed = indexed,
        sourceFailureCount = sourceFailureCount,
        pendingWork = pendingWork
    )

    private fun result(cancelled: Boolean = false, vararg sources: SourceStats): DiscoveryResult {
        val stats = DiscoveryStats()
        sources.forEach { stats.bySource[it.sourceName] = it }
        stats.refreshGlobalCounts()
        return DiscoveryResult(triggeredBy = "TEST", stats = stats, wasCancelled = cancelled)
    }

    @Test
    fun `record is FAILED when every attempted source terminally failed`() {
        val result = result(
            false,
            sourceStats("EUROPE_PMC", sourceFailureCount = 1, pendingWork = true),
            sourceStats("OPENALEX", sourceFailureCount = 1, pendingWork = true)
        )

        assertEquals("FAILED", result.taskFinalStatus)
        assertEquals(2, result.stats.sourceFailures)
    }

    @Test
    fun `record is PARTIAL_SUCCESS when one source failed and another ran healthy`() {
        val result = result(
            false,
            sourceStats("EUROPE_PMC", sourceFailureCount = 1, pendingWork = true),
            sourceStats("ORCID", indexed = 4)
        )

        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
        assertEquals(4, result.taskSuccessCount)
    }

    @Test
    fun `record is PARTIAL_SUCCESS when a quota stop leaves resumable work`() {
        val result = result(false, sourceStats("OPENALEX", pendingWork = true))

        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
        assertEquals(0, result.stats.sourceFailures, "额度延期不是搜索失败")
    }

    @Test
    fun `record is SUCCESS for a genuine empty result and for a run without sources`() {
        assertEquals("SUCCESS", result(false, sourceStats("EUROPE_PMC")).taskFinalStatus)
        assertEquals("SUCCESS", result(false).taskFinalStatus)
    }

    @Test
    fun `record is CANCELLED even when sources failed`() {
        val cancelled = result(true, sourceStats("EUROPE_PMC", sourceFailureCount = 1))

        assertEquals("CANCELLED", cancelled.taskFinalStatus)
    }

    @Test
    fun `failure count adds terminal source errors and never inflates expert failures`() {
        val stats = DiscoveryStats()
        stats.bySource["EUROPE_PMC"] = sourceStats("EUROPE_PMC", indexed = 3, sourceFailureCount = 1, pendingWork = true)
        stats.refreshGlobalCounts()
        val result = DiscoveryResult("TEST", stats)

        assertEquals(3, result.taskSuccessCount, "indexed 仍是 success_count")
        assertEquals(1, result.taskFailureCount, "整源终止错误只算 1 个源失败，不按专家数放大")
    }

    @Test
    fun `progress status mirrors the record status with COMPLETED for SUCCESS`() {
        assertEquals("COMPLETED", DiscoveryTerminalStatus.toProgressStatus("SUCCESS"))
        assertEquals("PARTIAL_SUCCESS", DiscoveryTerminalStatus.toProgressStatus("PARTIAL_SUCCESS"))
        assertEquals("FAILED", DiscoveryTerminalStatus.toProgressStatus("FAILED"))
        assertEquals("CANCELLED", DiscoveryTerminalStatus.toProgressStatus("CANCELLED"))
    }
}
