package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewExpertRow
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** A bounded SQL-backed read cache, not a review task or a source of write authority.
 * Rows persist across browser navigation. Restarted work fails explicitly; READY snapshots remain readable.
 */
@Service
class DiscoveryReviewQuerySnapshotService(
    private val scan: DiscoveryReviewScanService, private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper, private val tx: TransactionTemplate,
    @Qualifier("discoveryReviewQueryExecutor") private val executor: Executor
) {
    private val owner = UUID.randomUUID().toString()
    private data class Snapshot(val generation: String, val owner: String, val state: String, val matched: Long,
        val error: String?, val updated: LocalDateTime, val expires: LocalDateTime)
    private val rowMapper = RowMapper { r, _ -> Snapshot(r.getString("generation"), r.getString("owner_token"),
        r.getString("state"), r.getLong("matched"), r.getString("error_message"),
        r.getTimestamp("updated_at").toLocalDateTime(), r.getTimestamp("expires_at").toLocalDateTime()) }

    @Synchronized
    fun page(filter: DiscoveryReviewScanService.DiscoveryReviewFilter, from: Int, size: Int, refresh: Boolean): ReviewQueryPage {
        require(from >= 0 && size in 1..100)
        require(filter.q == null || filter.q.length <= 100)
        require(filter.issue == null || filter.issue.length <= 100)
        require(filter.decision == null || filter.decision in setOf("ALL", "UNINITIALIZED", "AUTO_PASSED", "NEEDS_REVIEW",
            "MANUAL_APPROVED", "LEGACY_APPROVED", "HOLD", "REJECTED"))
        val key = DiscoveryVerificationRepository.replayHash(mapper.writeValueAsString(filter))
        val now = LocalDateTime.now()
        jdbc.update("DELETE FROM discovery_review_query WHERE expires_at<?", now)
        var snapshot = jdbc.query("SELECT * FROM discovery_review_query WHERE query_key=?", rowMapper, key).firstOrNull()
        if (snapshot != null && snapshot.state in setOf("QUEUED", "RUNNING") && snapshot.owner != owner) {
            jdbc.update("UPDATE discovery_review_query SET state='FAILED',error_message='列表生成被重启中断，请刷新重试' WHERE query_key=? AND generation=?",
                key, snapshot.generation)
            snapshot = snapshot.copy(state = "FAILED", error = "列表生成被重启中断，请刷新重试")
        }
        if (snapshot == null || refresh && snapshot.state !in setOf("QUEUED", "RUNNING")) {
            val generation = UUID.randomUUID().toString()
            tx.executeWithoutResult {
                jdbc.update("DELETE FROM discovery_review_query WHERE query_key=?", key)
                if (jdbc.queryForObject("SELECT COUNT(*) FROM discovery_review_query", Long::class.java)!! >= 32)
                    throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "审核查询繁忙，请稍后刷新；全量重审仍可独立提交")
                jdbc.update("INSERT INTO discovery_review_query(query_key,generation,owner_token,state,started_at,updated_at,expires_at) VALUES (?,?,?,'QUEUED',?,?,?)",
                    key, generation, owner, now, now, now.plusMinutes(10))
            }
            try { executor.execute { build(generation, filter) } }
            catch (_: RejectedExecutionException) {
                jdbc.update("UPDATE discovery_review_query SET state='FAILED',error_message='审核查询排队已满，请稍后刷新' WHERE generation=?", generation)
            }
            snapshot = jdbc.query("SELECT * FROM discovery_review_query WHERE query_key=?", rowMapper, key).single()
        }
        val current = requireNotNull(snapshot)
        val rows = if (current.state == "READY") jdbc.query(
            "SELECT row_json FROM discovery_review_query_row WHERE generation=? AND ordinal>=? ORDER BY ordinal LIMIT ?",
            RowMapper { r, _ -> mapper.readValue(r.getString(1), DiscoveryReviewExpertRow::class.java) }, current.generation, from, size) else emptyList()
        return ReviewQueryPage(if (current.state == "READY") current.matched else null, from, size, rows, current.state,
            current.generation, current.matched, current.updated.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString(), current.error)
    }

    private fun build(generation: String, filter: DiscoveryReviewScanService.DiscoveryReviewFilter) {
        try {
            if (jdbc.update("UPDATE discovery_review_query SET state='RUNNING' WHERE generation=? AND state='QUEUED'", generation) != 1) return
            var matched = 0L
            scan.scanAll(filter, 500) { experts ->
                tx.executeWithoutResult {
                    if (jdbc.queryForObject("SELECT COUNT(*) FROM discovery_review_query WHERE generation=? AND state='RUNNING'", Long::class.java, generation) != 1L)
                        throw CancellationException("Query expired")
                    val start = matched
                    jdbc.batchUpdate("INSERT INTO discovery_review_query_row(generation,ordinal,row_json) VALUES (?,?,?)",
                        experts.mapIndexed { index, expert -> arrayOf<Any>(generation, start + index, mapper.writeValueAsString(expert.row)) })
                    matched += experts.size
                    val now = LocalDateTime.now()
                    jdbc.update("UPDATE discovery_review_query SET matched=?,updated_at=?,expires_at=? WHERE generation=?",
                        matched, now, now.plusMinutes(10), generation)
                }
                true
            }
            jdbc.update("UPDATE discovery_review_query SET state='READY',updated_at=? WHERE generation=? AND state='RUNNING'", LocalDateTime.now(), generation)
        } catch (_: CancellationException) {
            // Expired/superseded read cache, never touches expert records.
        } catch (ex: Exception) {
            jdbc.update("UPDATE discovery_review_query SET state='FAILED',error_message=?,updated_at=? WHERE generation=?",
                "审核列表生成失败（${ex.javaClass.simpleName}），请刷新重试", LocalDateTime.now(), generation)
        }
    }
}

data class ReviewQueryPage(val total: Long?, val from: Int, val size: Int, val experts: List<DiscoveryReviewExpertRow>,
    val queryStatus: String, val queryId: String, val matched: Long, val asOf: String, val queryError: String?)
