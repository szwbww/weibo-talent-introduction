package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.archive.ArchiveMysqlFixture
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.Executor

class DiscoveryReviewQuerySnapshotServiceIT {
    companion object {
        @JvmStatic @BeforeAll fun migrate() {
            ResourceDatabasePopulator(ClassPathResource("db/migration/V165__discovery_review_query_snapshot.sql")).execute(ArchiveMysqlFixture.dataSource)
        }
    }
    @BeforeEach fun clean() { ArchiveMysqlFixture.jdbc.update("DELETE FROM discovery_review_query") }
    private val filter = DiscoveryReviewScanService.DiscoveryReviewFilter(ExpertIndexLevel.RAW, "discovered", null, null, "NEEDS_REVIEW")
    private fun service(f: CountsFixture, work: MutableList<Runnable>) = DiscoveryReviewQuerySnapshotService(f.scan,
        ArchiveMysqlFixture.jdbc, ArchiveMysqlFixture.mapper, TransactionTemplate(DataSourceTransactionManager(ArchiveMysqlFixture.dataSource)),
        Executor { work.add(it) })

    @Test fun `all 15340 pending including uninitialized are paginated once beyond ten thousand`() {
        val f = CountsFixture(); val work = mutableListOf<Runnable>(); val service = service(f, work)
        val first = service.page(filter, 0, 20, false)
        assertEquals("QUEUED", first.queryStatus); assertNull(first.total)
        repeat(3) { assertEquals(first.queryId, service.page(filter, 0, 20, false).queryId) }
        assertEquals(1, work.size); work.removeAt(0).run()
        val ready = service.page(filter, 0, 20, false)
        assertEquals("READY", ready.queryStatus); assertEquals(15340L, ready.total)
        assertNotNull(java.time.OffsetDateTime.parse(ready.asOf))
        val all = (0 until ready.total!!.toInt() step 100).flatMap { offset -> service.page(filter, offset, 100, false).experts }
        assertEquals(15340, all.size); assertEquals(15340, all.map { it.docId }.toSet().size)
        assertTrue(all.all { it.decision == "NEEDS_REVIEW" }); assertEquals(15140, all.count { !it.initialized })
        assertEquals(1, f.searches); assertEquals(1, f.clears)
        assertTrue(f.businessCalls.all { it in setOf("findAdmissions", "findItemsByIds") })
        // Browser/process re-entry can read READY without scheduling another scan.
        val restarted = service(f, work)
        assertEquals(ready.queryId, restarted.page(filter, 15000, 20, false).queryId)
        assertTrue(work.isEmpty())
        assertNotEquals(ready.queryId, restarted.page(filter, 0, 20, true).queryId)
        assertEquals(1, work.size)
    }

    @Test fun `partial failure never exposes an incomplete total and refresh explicitly retries`() {
        val f = CountsFixture(); f.failPage = 11
        val work = mutableListOf<Runnable>(); val service = service(f, work)
        service.page(filter, 0, 20, false); work.removeAt(0).run()
        val failed = service.page(filter, 0, 20, false)
        assertEquals("FAILED", failed.queryStatus); assertNull(failed.total); assertTrue(failed.experts.isEmpty())
        assertTrue(failed.matched > 0); assertNotNull(failed.queryError); assertTrue(work.isEmpty())
        f.failPage = 0; service.page(filter, 0, 20, true); work.removeAt(0).run()
        assertEquals("READY", service.page(filter, 0, 20, false).queryStatus)
    }

    @Test fun `restart during queued work becomes explicit failure and expired cache removes rows`() {
        val f = CountsFixture(); val work = mutableListOf<Runnable>(); val first = service(f, work)
        first.page(filter, 0, 20, false)
        val restarted = service(f, mutableListOf())
        assertEquals("FAILED", restarted.page(filter, 0, 20, false).queryStatus)
        work.removeAt(0).run() // stale job cannot switch FAILED to RUNNING
        assertEquals("FAILED", restarted.page(filter, 0, 20, false).queryStatus)
    }
}
