package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.*
import java.time.Instant

class DiscoveryPaperBackfillServiceTest {
    @TempDir lateinit var root: Path
    private class Target : HistoricalBindingsTarget {
        var begun = 0; var validated = 0
        val run = VerificationRun(40001, VerificationMode.AUDIT, VerificationMode.AUDIT.policyVersion, false, "{}",
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        override fun validate(frozen: DiscoveryHistoricalReplayTool.Frozen) { validated++ }
        override fun begin(manifestHash: String) = DiscoveryVerificationExecution(40001, run).also { begun++ }
        override fun resume(executionId: Long) = DiscoveryVerificationExecution(executionId, run)
        override fun validatePaper(frozen: DiscoveryHistoricalReplayTool.Frozen, paper: DiscoveryHistoricalReplayTool.Paper, emails: List<AuthorEmail>) {}
        override fun recheckExisting(frozen: DiscoveryHistoricalReplayTool.Frozen, paper: DiscoveryHistoricalReplayTool.Paper, emails: List<AuthorEmail>) = emptyList<Long>()
        override fun finish(context: DiscoveryVerificationExecution, cancelled: Boolean, failed: Boolean) {}
    }
    private fun consumer(): ExpertDiscoveryService = Mockito.mock(ExpertDiscoveryService::class.java, org.mockito.stubbing.Answer { invocation ->
        if (invocation.method.name.startsWith("consumeArchivedPaper")) {
            val authors = invocation.getArgument<List<AuthorEmail>>(1)
            val context = invocation.getArgument<DiscoveryVerificationExecution>(4)
            assertNotNull(context.archivedPaper)
            context.archivedPaper!!.parsed.requireMatches(invocation.getArgument(0), authors, context.archivedPaper!!.source)
            ConsumeOutcomeResult(indexedExperts = authors.size)
        } else Mockito.RETURNS_DEFAULTS.answer(invocation)
    })
    @Test fun `wrong hash no writes parser owned permits and durable completed receipts`() {
        val frozen = HistoricalReplayFixtures.freeze(root, listOf(HistoricalReplayFixtures.paper(root)))
        val target = Target(); val consumer = consumer()
        val service = DiscoveryPaperBackfillService(consumer, DiscoveryHistoricalReplayTool.analyzer(), target)
        assertThrows(IllegalArgumentException::class.java) { service.apply(frozen, "0".repeat(64)) }
        assertEquals(0, target.begun); assertTrue(Mockito.mockingDetails(consumer).invocations.isEmpty())
        val first = service.apply(frozen, frozen.hash)
        assertEquals(1, first.newRaw); assertEquals(1, first.completed)
        val second = DiscoveryPaperBackfillService(consumer, DiscoveryHistoricalReplayTool.analyzer(), target).apply(frozen, frozen.hash)
        assertEquals(0, second.newRaw); assertEquals(1, second.skippedCompleted); assertEquals(1, target.begun)
        assertEquals(2, target.validated)
        assertEquals(1, Mockito.mockingDetails(consumer).invocations.size)
    }
    @Test fun `cancel resumes fixed unfinished papers and preserves retryable missing receipt`() {
        val papers = listOf(HistoricalReplayFixtures.paper(root, 1), HistoricalReplayFixtures.paper(root, 2, email = "eve@fixture.edu"),
            HistoricalReplayFixtures.paper(root, 3, missing = true))
        val frozen = HistoricalReplayFixtures.freeze(root, papers)
        val consumer = consumer(); val target = Target(); val service = DiscoveryPaperBackfillService(consumer, DiscoveryHistoricalReplayTool.analyzer(), target)
        var checks = 0
        val first = service.apply(frozen, frozen.hash) { ++checks > 2 }
        assertTrue(first.cancelled); assertEquals(1, first.newRaw)
        val second = service.apply(frozen, frozen.hash)
        assertFalse(second.cancelled); assertEquals(1, second.newRaw); assertEquals(1, second.skippedCompleted)
        assertEquals("SOURCE_MISSING", second.receipts.single { it.retryable }.reason)
        val third = service.apply(frozen, frozen.hash)
        assertEquals(0, third.newRaw); assertEquals(2, third.skippedCompleted); assertEquals(1, third.failed)
        assertEquals(2, Files.list(frozen.directory.resolve("bindings/3-3")).use { paths -> paths.filter { it.toString().endsWith(".json") }.count() }.toInt())
        HistoricalReplayFixtures.paper(root, 4)
        assertEquals(3, frozen.manifest.scope.papers.size)
    }
    @Test fun `permit rejects forged author and changed retained bytes`() {
        val paper = HistoricalReplayFixtures.paper(root)
        val parsed = ParsedArchivedPaper.parse(paper, DiscoveryHistoricalReplayTool.analyzer())
        assertThrows(IllegalArgumentException::class.java) { parsed.requireMatches(paper.metadata,
            parsed.emails.map { it.copy(givenNames = "Forged") }, paper.source) }
        Files.write(Paths.get(paper.path), "changed".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { parsed.requireMatches(paper.metadata, parsed.emails, paper.source) }
    }
}
