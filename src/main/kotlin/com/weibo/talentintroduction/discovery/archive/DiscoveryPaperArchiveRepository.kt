package com.weibo.talentintroduction.discovery.archive

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class DiscoveryPaperArchiveRepository(private val jdbc: JdbcTemplate, manager: PlatformTransactionManager) {
    private val tx = TransactionTemplate(manager)
    private val mapper = com.fasterxml.jackson.databind.ObjectMapper()
    fun <T : Any> transaction(action: () -> T): T = tx.execute { action() }!!
    fun setting(): ArchiveSetting = jdbc.query("SELECT * FROM discovery_paper_archive_setting WHERE id=1", RowMapper { r, _ ->
        ArchiveSetting(r.getLong("revision"), r.getBoolean("enabled"), r.getLong("max_bytes"), r.getLong("min_free_bytes"), time(r, "updated_at"))
    }).firstOrNull()?.also { it.validate() } ?: ArchiveSetting()
    fun saveSetting(request: ArchiveSettingsRequest, now: Instant): ArchiveSetting = transaction {
        ArchiveSetting(request.revision, request.enabled, request.maxBytes, request.minFreeBytes).validate()
        val old = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_paper_archive_setting WHERE id=1", Long::class.java)!!
        if (old == 0L) {
            if (request.revision != 0L) throw ArchiveException("REVISION_CONFLICT", 409)
            try { jdbc.update("INSERT INTO discovery_paper_archive_setting VALUES (1,?,?,?,?,?)", request.enabled, request.maxBytes, request.minFreeBytes, 1L, db(now)) }
            catch (e: org.springframework.dao.DuplicateKeyException) { throw ArchiveException("REVISION_CONFLICT", 409) }
        } else if (jdbc.update("UPDATE discovery_paper_archive_setting SET enabled=?,max_bytes=?,min_free_bytes=?,revision=revision+1,updated_at=? WHERE id=1 AND revision=?",
                request.enabled, request.maxBytes, request.minFreeBytes, db(now), request.revision) != 1) throw ArchiveException("REVISION_CONFLICT", 409)
        setting()
    }
    fun beginRecord(executionId: Long, source: String, key: String, metadata: String, now: Instant): ArchiveRecord = transaction {
        jdbc.update("INSERT INTO discovery_paper_archive_record (execution_id,source_name,paper_key,metadata_json,created_at,updated_at) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
            executionId, source, key, metadata, db(now), db(now))
        jdbc.query("SELECT * FROM discovery_paper_archive_record WHERE execution_id=? AND source_name=? AND paper_key=?", recordMapper, executionId, source, key).single()
    }
    fun record(id: Long): ArchiveRecord? = jdbc.query("SELECT * FROM discovery_paper_archive_record WHERE id=?", recordMapper, id).firstOrNull()
    fun records(): List<ArchiveRecord> = jdbc.query("SELECT * FROM discovery_paper_archive_record ORDER BY id", recordMapper)
    fun beginAttempt(recordId: Long, key: String, kind: ArchiveAttemptKind, url: String?, now: Instant): ArchiveAttempt = transaction {
        jdbc.update("INSERT INTO discovery_paper_archive_attempt (record_id,attempt_key,kind,source_url,status,created_at,updated_at) VALUES (?,?,?,?,'STARTED',?,?) ON DUPLICATE KEY UPDATE id=id",
            recordId, key, kind.name, url, db(now), db(now))
        jdbc.query("SELECT * FROM discovery_paper_archive_attempt WHERE attempt_key=?", attemptMapper, key).single().also {
            if (it.recordId != recordId || it.kind != kind) throw ArchiveException("ATTEMPT_IDENTITY_CONFLICT", 409)
        }
    }
    fun attempt(id: Long): ArchiveAttempt? = jdbc.query("SELECT * FROM discovery_paper_archive_attempt WHERE id=?", attemptMapper, id).firstOrNull()
    fun attempts(recordId: Long): List<ArchiveAttempt> = jdbc.query("SELECT * FROM discovery_paper_archive_attempt WHERE record_id=? ORDER BY id", attemptMapper, recordId)
    fun references(hash: String): List<ArchiveAttempt> = jdbc.query("SELECT * FROM discovery_paper_archive_attempt WHERE blob_sha256=? AND status='SAVED' ORDER BY id", attemptMapper, hash)
    fun blobs(): List<ArchiveBlob> = jdbc.query("SELECT * FROM discovery_paper_archive_blob ORDER BY sha256", blobMapper)
    fun blob(hash: String): ArchiveBlob? = jdbc.query("SELECT * FROM discovery_paper_archive_blob WHERE sha256=?", blobMapper, hash).firstOrNull()
    fun saveBody(attemptId: Long, hash: String, bytes: Long, media: String, reused: Boolean, now: Instant) = transaction {
        val a = attempt(attemptId) ?: throw ArchiveException("NOT_FOUND", 404)
        if (a.status != ArchiveAttemptStatus.STARTED) throw ArchiveException("ATTEMPT_FINISHED", 409)
        jdbc.update("INSERT INTO discovery_paper_archive_blob VALUES (?,?,?,'AVAILABLE',?) ON DUPLICATE KEY UPDATE size_bytes=VALUES(size_bytes),media_type=VALUES(media_type),state='AVAILABLE'", hash, bytes, media, db(now))
        jdbc.update("UPDATE discovery_paper_archive_attempt SET status='SAVED',blob_sha256=?,completed_bytes=?,reused=?,reason_code=NULL,updated_at=? WHERE id=? AND status='STARTED'", hash, bytes, reused, db(now), attemptId)
        touch(a.recordId, now)
    }
    fun finishAttempt(id: Long, status: ArchiveAttemptStatus, reason: String?, httpStatus: Int?, bytes: Long, now: Instant) = transaction {
        val a = attempt(id) ?: throw ArchiveException("NOT_FOUND", 404)
        if (a.status == ArchiveAttemptStatus.STARTED) {
            jdbc.update("UPDATE discovery_paper_archive_attempt SET status=?,reason_code=?,http_status=?,completed_bytes=?,updated_at=? WHERE id=? AND status='STARTED'", status.name, reason, httpStatus, bytes, db(now), id)
            touch(a.recordId, now)
        }
        attempt(id)!!
    }
    fun finishExtraction(id: Long, json: String, now: Instant): ArchiveRecord = transaction {
        val current = jdbc.query("SELECT * FROM discovery_paper_archive_record WHERE id=? FOR UPDATE", recordMapper, id).firstOrNull()
            ?: throw ArchiveException("NOT_FOUND", 404)
        val incoming = mapper.readTree(json)
        val previous = current.outcomeJson?.let { mapper.readTree(it) }
        // Legacy direct callers retain their immutable baseline semantics. Real capture envelopes update latest.
        val merged = if (previous == null) incoming else if (incoming.has("latest")) {
            (previous.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()).also {
                it.set<com.fasterxml.jackson.databind.JsonNode>("latest", incoming.get("latest"))
            }
        } else previous
        jdbc.update("UPDATE discovery_paper_archive_record SET outcome_json=?,revision=revision+1,updated_at=? WHERE id=?",
            mapper.writeValueAsString(merged), db(now), id)
        record(id)!!
    }
    fun saveAnalysis(id: Long, revision: Long, json: String, now: Instant): ArchiveRecord = transaction {
        if (jdbc.update("UPDATE discovery_paper_archive_record SET analysis_json=?,revision=revision+1,updated_at=? WHERE id=? AND revision=?", json, db(now), id, revision) != 1) throw ArchiveException("REVISION_CONFLICT", 409)
        record(id)!!
    }
    fun protect(id: Long, revision: Long, value: Boolean, now: Instant): ArchiveRecord = transaction {
        if (jdbc.update("UPDATE discovery_paper_archive_record SET protected=?,revision=revision+1,updated_at=? WHERE id=? AND revision=?", value, db(now), id, revision) != 1) throw ArchiveException("REVISION_CONFLICT", 409)
        record(id)!!
    }
    fun blobState(hash: String, state: ArchiveBlobState) { jdbc.update("UPDATE discovery_paper_archive_blob SET state=? WHERE sha256=?", state.name, hash) }
    fun finishDelete(hash: String, now: Instant) = transaction {
        references(hash).map { it.recordId }.distinct().forEach { touch(it, now) }
        jdbc.update("UPDATE discovery_paper_archive_attempt SET status='DELETED',updated_at=? WHERE blob_sha256=? AND status='SAVED'", db(now), hash)
        // Historical attempts retain their hash and immutable success bytes/reuse flag.
        blobState(hash, ArchiveBlobState.MISSING)
        true
    }
    fun deleteUnreferencedBlob(hash: String) {
        // Historical DELETED attempts still reference this row, so retain its metadata.
        blobState(hash, ArchiveBlobState.MISSING)
    }
    fun list(query: ArchiveQuery): Pair<List<ArchiveRecord>, Long> {
        val clauses = mutableListOf("1=1"); val args = mutableListOf<Any>()
        query.executionId?.let { clauses += "r.execution_id=?"; args += it }
        query.protected?.let { clauses += "r.protected=?"; args += it }
        query.q?.takeIf { it.isNotEmpty() }?.let {
            clauses += "(r.paper_key LIKE ? ESCAPE '!' OR JSON_UNQUOTE(JSON_EXTRACT(r.metadata_json,'$.title')) LIKE ? ESCAPE '!' OR JSON_UNQUOTE(JSON_EXTRACT(r.metadata_json,'$.doi')) LIKE ? ESCAPE '!')"
            val escaped = "%" + it.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
            repeat(3) { args += escaped }
        }
        query.status?.let { clauses += "($STATE_SQL)=?"; args += it.name }
        query.hasGain?.let {
            clauses += "r.analysis_json IS NOT NULL AND JSON_UNQUOTE(JSON_EXTRACT(r.analysis_json,'$.baselineCoverage'))='COMPLETE' AND JSON_EXTRACT(r.analysis_json,'$.delta') IS NOT NULL AND JSON_TYPE(JSON_EXTRACT(r.analysis_json,'$.delta'))<>'NULL' AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(r.analysis_json,'$.deltaAddedBindings')) AS SIGNED)>0)=?"
            args += it
        }
        val where = clauses.joinToString(" AND ")
        val total = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_paper_archive_record r WHERE $where", Long::class.java, *args.toTypedArray())!!
        val rows = jdbc.query("SELECT r.* FROM discovery_paper_archive_record r WHERE $where ORDER BY r.id DESC LIMIT ? OFFSET ?", recordMapper, *(args + query.size + query.page.toLong() * query.size).toTypedArray())
        return rows to total
    }
    fun counts(executionId: Long?): ArchiveCounts {
        val where = if (executionId == null) "1=1" else "r.execution_id=?"
        val args = if (executionId == null) emptyArray() else arrayOf<Any>(executionId)
        val papers = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_paper_archive_record r WHERE $where", Long::class.java, *args)!!
        val totals = jdbc.queryForMap("SELECT COUNT(*) attempts,COALESCE(SUM(a.status IN ('SAVED','DELETED')),0) saved,COALESCE(SUM(a.status IN ('SAVED','DELETED') AND a.reused=0),0) fresh,COALESCE(SUM(a.status IN ('SAVED','DELETED') AND a.reused=1),0) reused,COALESCE(SUM(a.status IN ('DOWNLOAD_FAILED','TOO_LARGE','SAVE_ERROR')),0) failed FROM discovery_paper_archive_attempt a JOIN discovery_paper_archive_record r ON r.id=a.record_id WHERE $where", *args)
        fun n(key: String) = (totals[key] as Number).toLong()
        val notStored = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_paper_archive_record r WHERE $where AND ($STATE_SQL)='NOT_STORED'", Long::class.java, *args)!!
        return ArchiveCounts(papers, n("attempts"), n("saved"), n("fresh"), n("reused"), n("failed"), notStored)
    }
    private fun touch(id: Long, now: Instant) { jdbc.update("UPDATE discovery_paper_archive_record SET revision=revision+1,updated_at=? WHERE id=?", db(now), id) }
    companion object {
        private val STATE_SQL = """CASE
            WHEN EXISTS(SELECT 1 FROM discovery_paper_archive_attempt a WHERE a.record_id=r.id AND a.status='SAVE_ERROR') THEN 'SAVE_ERROR'
            WHEN EXISTS(SELECT 1 FROM discovery_paper_archive_attempt a JOIN discovery_paper_archive_blob b ON b.sha256=a.blob_sha256 WHERE a.record_id=r.id AND a.status='SAVED' AND b.state='AVAILABLE') THEN 'STORED'
            WHEN EXISTS(SELECT 1 FROM discovery_paper_archive_attempt a WHERE a.record_id=r.id AND a.status='STARTED') THEN 'PROCESSING'
            WHEN EXISTS(SELECT 1 FROM discovery_paper_archive_attempt a WHERE a.record_id=r.id AND a.status='DELETED') AND NOT EXISTS(SELECT 1 FROM discovery_paper_archive_attempt a WHERE a.record_id=r.id AND a.status<>'DELETED') THEN 'DELETED'
            ELSE 'NOT_STORED' END"""
        private fun db(now: Instant) = LocalDateTime.ofInstant(now, ZoneOffset.UTC)
        private fun time(r: ResultSet, key: String) = r.getObject(key, LocalDateTime::class.java).toInstant(ZoneOffset.UTC)
        private val recordMapper = RowMapper { r: ResultSet, _: Int -> ArchiveRecord(r.getLong("id"), r.getLong("execution_id"), r.getString("source_name"), r.getString("paper_key"), r.getString("metadata_json"), r.getString("outcome_json"), r.getString("analysis_json"), r.getBoolean("protected"), r.getLong("revision"), time(r,"created_at"), time(r,"updated_at")) }
        private val blobMapper = RowMapper { r: ResultSet, _: Int -> ArchiveBlob(r.getString("sha256"), r.getLong("size_bytes"), r.getString("media_type"), ArchiveBlobState.valueOf(r.getString("state")), time(r,"created_at")) }
        private val attemptMapper = RowMapper { r: ResultSet, _: Int -> ArchiveAttempt(r.getLong("id"),r.getLong("record_id"),r.getString("attempt_key"),ArchiveAttemptKind.valueOf(r.getString("kind")),r.getString("source_url"),ArchiveAttemptStatus.valueOf(r.getString("status")),r.getString("blob_sha256"),r.getBoolean("reused"),r.getObject("http_status")?.let { (it as Number).toInt() },r.getString("reason_code"),r.getLong("completed_bytes"),time(r,"created_at"),time(r,"updated_at")) }
    }
}
