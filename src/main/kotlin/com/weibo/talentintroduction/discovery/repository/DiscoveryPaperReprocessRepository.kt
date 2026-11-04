package com.weibo.talentintroduction.discovery.repository

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.sql.ResultSet
import java.sql.Statement
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/** The only writer of the two audit tables. Every worker write locks and checks its run fence. */
@Repository
class DiscoveryPaperReprocessRepository(
    private val jdbc: JdbcTemplate,
    manager: PlatformTransactionManager,
    private val mapper: ObjectMapper,
    private val tasks: TaskExecutionRepository
) {
    private val tx = TransactionTemplate(manager)
    @Suppress("UNCHECKED_CAST")
    private fun <T> transaction(action: () -> T): T = tx.execute { action() } as T
    private fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    fun watermark(sourceId: Long): ScopeWatermark {
        require(sourceId > 0)
        val source = jdbc.queryForList("SELECT status FROM task_execution WHERE id=?", String::class.java, sourceId).firstOrNull()
            ?: throw ReprocessException("SOURCE_NOT_FOUND", 404)
        if (source !in SOURCE_TERMINAL) throw ReprocessException("SOURCE_RUNNING", 409)
        val records = jdbc.queryForMap("""SELECT COUNT(*) n, COALESCE(MAX(id),0) upper_id,
            COALESCE(SUM(revision),0) revisions,
            COALESCE(SUM(CAST(CONV(SUBSTR(SHA2(CONCAT(id,':',revision,':',updated_at),256),1,15),16,10) AS DECIMAL(65,0))),0) fingerprint
            FROM discovery_paper_archive_record WHERE execution_id=?""", sourceId)
        // Metadata is not loaded by preview. Record revisions cover its writers; all attempt/blob
        // state is additionally fingerprinted because blobState does not touch record.revision.
        val attempts = jdbc.queryForMap("""SELECT COUNT(*) n, COALESCE(MAX(a.id),0) upper_id,
            COALESCE(SUM(CAST(CONV(SUBSTR(SHA2(CONCAT_WS(':',a.id,a.record_id,a.kind,a.status,
                COALESCE(a.blob_sha256,''),a.updated_at,a.completed_bytes,COALESCE(b.state,''),
                COALESCE(b.size_bytes,0),COALESCE(b.media_type,'')),256),1,15),16,10) AS DECIMAL(65,0))),0) fingerprint
            FROM discovery_paper_archive_attempt a JOIN discovery_paper_archive_record r ON r.id=a.record_id
            LEFT JOIN discovery_paper_archive_blob b ON b.sha256=a.blob_sha256 WHERE r.execution_id=?""", sourceId)
        fun number(row: Map<String, Any>, key: String) = (row.getValue(key) as Number).toLong()
        val upper = number(records, "upper_id"); val count = number(records, "n")
        val revision = number(records, "revisions"); val attemptMax = number(attempts, "upper_id")
        val attemptCount = number(attempts, "n"); val attemptSum = attempts.getValue("fingerprint").toString()
        val hash = sha(listOf(sourceId, source, upper, count, revision, records.getValue("fingerprint"),
            attemptMax, attemptCount, attemptSum).joinToString("|"))
        return ScopeWatermark(sourceId, source, upper, attemptMax, count, revision, attemptCount, attemptSum, hash)
    }

    /** Same full-source record scope and descriptor eligibility as freezePage, including deleted/error records. */
    fun missingSourcePapers(sourceId: Long): Long = jdbc.queryForObject("""SELECT COUNT(*)
        FROM discovery_paper_archive_record r WHERE r.execution_id=? AND NOT EXISTS(
            SELECT 1 FROM discovery_paper_archive_attempt a
            JOIN discovery_paper_archive_blob b ON b.sha256=a.blob_sha256
            WHERE a.record_id=r.id AND a.status='SAVED' AND b.state='AVAILABLE')""", Long::class.java, sourceId)!!

    fun create(request: CreateReprocessRequest, requestHash: String, scope: JsonNode, owner: String): ReprocessRun {
        require(requestHash.matches(HASH) && owner.isNotBlank() && owner.length <= 64)
        require(UUID.fromString(request.requestKey).toString() == request.requestKey)
        val frozenScope = scope.deepCopy<JsonNode>()
        val encoded = json(frozenScope, "INPUT_TOO_LARGE")
        try {
            return transaction {
                byRequestKey(request.requestKey)?.let { return@transaction sameRequest(it, requestHash) }
                active()?.let { return@transaction it }
                val current = watermark(request.sourceExecutionId)
                if (current.scopeHash != request.expectedScopeHash || frozenScope.path("scopeHash").asText() != current.scopeHash ||
                    frozenScope.path("upperRecordId").asLong(-1) != current.upperRecordId)
                    throw ReprocessException("SCOPE_CHANGED", 409)
                val time = now()
                val key = GeneratedKeyHolder()
                jdbc.update({ connection -> connection.prepareStatement("""INSERT INTO task_execution
                    (task_type,trigger_type,status,request_payload,started_at,created_at,updated_at,owner_token,heartbeat_at)
                    VALUES ('DISCOVERY_PAPER_REPROCESS','MANUAL','RUNNING',?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS).also {
                    it.setString(1, mapper.writeValueAsString(request)); it.setObject(2, taskTime(time)); it.setObject(3, taskTime(time))
                    it.setObject(4, taskTime(time)); it.setString(5, owner); it.setObject(6, taskTime(time))
                } }, key)
                val id = key.key!!.toLong()
                jdbc.update("""INSERT INTO discovery_paper_reprocess_run
                    (execution_id,source_execution_id,request_key,request_hash,active_key,mode,state,phase,scope_json,created_at,updated_at)
                    VALUES (?,?,?,?,'PAPER_REPROCESS',?,'PREPARING','FREEZE',?,?,?)""",
                    id, request.sourceExecutionId, request.requestKey, requestHash, request.mode.name, encoded, db(time), db(time))
                find(id)!!
            }
        } catch (e: DuplicateKeyException) {
            // The losing transaction (including its task row) has rolled back before observing a winner.
            byRequestKey(request.requestKey)?.let { return sameRequest(it, requestHash) }
            active()?.let { return it }
            throw e
        }
    }

    private fun sameRequest(run: ReprocessRun, hash: String): ReprocessRun {
        if (run.requestHash != hash) throw ReprocessException("REQUEST_KEY_CONFLICT", 409)
        return run
    }

    fun find(id: Long): ReprocessRun? = jdbc.query("SELECT * FROM discovery_paper_reprocess_run WHERE execution_id=?", runMapper, id).firstOrNull()
    fun active(): ReprocessRun? = jdbc.query("SELECT * FROM discovery_paper_reprocess_run WHERE active_key='PAPER_REPROCESS'", runMapper).firstOrNull()
    fun byRequestKey(key: String): ReprocessRun? = jdbc.query("SELECT * FROM discovery_paper_reprocess_run WHERE request_key=?", runMapper, key).firstOrNull()

    fun runs(sourceId: Long?, active: Boolean?, requestKey: String?, page: Int, size: Int): ReprocessPage<ReprocessRun> {
        page(page, size)
        val clauses = mutableListOf("1=1"); val args = mutableListOf<Any>()
        sourceId?.let { require(it > 0); clauses += "source_execution_id=?"; args += it }
        active?.let { clauses += if (it) "active_key IS NOT NULL" else "active_key IS NULL" }
        requestKey?.let { clauses += "request_key=?"; args += it }
        val where = clauses.joinToString(" AND ")
        val count = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_paper_reprocess_run WHERE $where", Long::class.java, *args.toTypedArray())!!
        val rows = jdbc.query("SELECT * FROM discovery_paper_reprocess_run WHERE $where ORDER BY execution_id DESC LIMIT ? OFFSET ?",
            runMapper, *(args + size + page.toLong() * size).toTypedArray())
        return ReprocessPage(rows, count, page, size)
    }

    fun items(runId: Long, kind: ReprocessKind?, page: Int, size: Int): ReprocessPage<ReprocessItem> {
        page(page, size)
        val where = "execution_id=?" + if (kind == null) "" else " AND kind=?"
        val args = if (kind == null) listOf(runId) else listOf(runId, kind.name)
        val count = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_paper_reprocess_item WHERE $where", Long::class.java, *args.toTypedArray())!!
        val rows = jdbc.query("SELECT * FROM discovery_paper_reprocess_item WHERE $where ORDER BY id LIMIT ? OFFSET ?",
            itemMapper, *(args + size + page.toLong() * size).toTypedArray())
        return ReprocessPage(rows, count, page, size)
    }

    fun item(runId: Long, itemId: Long): ReprocessItem? = jdbc.query(
        "SELECT * FROM discovery_paper_reprocess_item WHERE execution_id=? AND id=?", itemMapper, runId, itemId).firstOrNull()

    fun paper(runId: Long, recordId: Long): ReprocessItem? = jdbc.query(
        "SELECT * FROM discovery_paper_reprocess_item WHERE execution_id=? AND kind='PAPER' AND item_key=?",
        itemMapper, runId, recordId.toString()).singleOrNull()

    /** SQL permission, never supplied by an HTTP caller. Keep this lock before verification run/item locks. */
    fun requirePermission(parent: ReprocessRun): ReprocessRun = transaction {
        val current = owned(parent.executionId, requireNotNull(parent.leaseToken), parent.generation)
            ?: throw java.util.concurrent.CancellationException("PAPER_REPROCESS_LEASE_LOST")
        if (current.mode != ReprocessMode.ENFORCE || current.cancelRequested || current.state != ReprocessState.RUNNING ||
            current.scope.path("codeVersion") != parent.scope.path("codeVersion"))
            throw java.util.concurrent.CancellationException("PAPER_REPROCESS_PERMISSION_LOST")
        current
    }

    /** An action receipt is durable while its TARGET remains RUNNING. input_json is never rewritten. */
    fun receipt(parent: ReprocessRun, target: ReprocessItem, result: JsonNode): Boolean = transaction {
        requirePermission(parent)
        require(target.kind == ReprocessKind.TARGET && target.executionId == parent.executionId)
        val time = now()
        jdbc.update("""UPDATE discovery_paper_reprocess_item SET result_json=?,revision=revision+1,updated_at=?
            WHERE execution_id=? AND id=? AND state='RUNNING' AND lease_token=? AND generation=? AND lease_until>?""",
            json(result, "RESULT_TOO_LARGE", true), db(time), parent.executionId, target.id, target.leaseToken,
            target.generation, db(time)) == 1
    }

    fun claimTarget(parent: ReprocessRun, id: Long): ReprocessItem? = transaction {
        requirePermission(parent)
        val time = now()
        val changed = jdbc.update("""UPDATE discovery_paper_reprocess_item SET state='RUNNING',lease_token=?,lease_until=?,
            generation=?,revision=revision+1,updated_at=? WHERE execution_id=? AND id=? AND kind='TARGET' AND
            state='PENDING'""",
            UUID.randomUUID().toString(), db(time.plusSeconds(60)), parent.generation, db(time), parent.executionId, id)
        if (changed == 1) item(parent.executionId, id) else null
    }

    fun releaseTarget(parent: ReprocessRun, target: ReprocessItem): Boolean = transaction {
        requirePermission(parent)
        jdbc.update("""UPDATE discovery_paper_reprocess_item SET state='PENDING',lease_token=NULL,lease_until=NULL,
            revision=revision+1,updated_at=? WHERE execution_id=? AND id=? AND state='RUNNING' AND lease_token=? AND generation=?""",
            db(now()), parent.executionId, target.id, target.leaseToken, target.generation) == 1
    }

    /** REVIEW is a durable wait, not completion. Workers may only act while a poll owns a live parent lease. */
    fun waitForReview(parent: ReprocessRun, reason: String, releaseLease: Boolean = false): Boolean = transaction {
        requirePermission(parent)
        val scope = find(parent.executionId)!!.scope.deepCopy<ObjectNode>().put("reasonCode", reason)
        jdbc.update("""UPDATE discovery_paper_reprocess_item SET state='PENDING',lease_token=NULL,lease_until=NULL,
            revision=revision+1,updated_at=? WHERE execution_id=? AND kind='TARGET' AND state='RUNNING'""",
            db(now()), parent.executionId)
        jdbc.update("""UPDATE discovery_paper_reprocess_run SET phase='REVIEW',scope_json=?,
            lease_token=IF(?,NULL,lease_token),lease_until=IF(?,NULL,lease_until),
            revision=revision+1,updated_at=? WHERE execution_id=?""",
            json(scope, "INPUT_TOO_LARGE"), releaseLease, releaseLease, db(now()), parent.executionId)
        true
    }

    /** true means freezing is complete; each transaction reads at most 100 archive records. */
    fun freezePage(runId: Long, token: String, generation: Long): Boolean {
        var changed = false
        val complete = transaction {
            val run = owned(runId, token, generation) ?: return@transaction false
            if (run.state != ReprocessState.PREPARING || run.cancelRequested) return@transaction false
            if (run.scope.path("frozen").asBoolean()) return@transaction true
            val scope = run.scope.deepCopy<ObjectNode>()
            val cursor = scope.path("cursor").asLong(0)
            val records = jdbc.queryForList("""SELECT id,source_name,paper_key,revision,metadata_json,outcome_json FROM discovery_paper_archive_record
                WHERE execution_id=? AND id>? AND id<=? ORDER BY id LIMIT 100""",
                run.sourceExecutionId, cursor, scope.path("upperRecordId").asLong())
            val time = now()
            records.forEach { record ->
                val id = (record.getValue("id") as Number).toLong()
                val metadata = record.getValue("metadata_json").toString()
                val input = mapper.createObjectNode().put("schemaVersion", 1).put("recordId", id)
                    .put("revision", (record.getValue("revision") as Number).toLong()).put("metadataHash", sha(metadata))
                    .put("sourceExecutionId", run.sourceExecutionId).put("sourceName", record.getValue("source_name").toString())
                    .put("paperKey", record.getValue("paper_key").toString())
                input.set<JsonNode>("metadata", parseObject(metadata, "INVALID_SOURCE_JSON"))
                val outcome = record["outcome_json"]?.toString()?.let { parseObject(it, "INVALID_SOURCE_JSON") }
                input.set<JsonNode>("first", outcome?.get("first") ?: outcome ?: mapper.nullNode())
                val bodies = input.putArray("bodies")
                jdbc.queryForList("""SELECT a.id,a.blob_sha256,a.kind,b.media_type
                    FROM discovery_paper_archive_attempt a JOIN discovery_paper_archive_blob b ON b.sha256=a.blob_sha256
                    WHERE a.record_id=? AND a.status='SAVED' AND b.state='AVAILABLE' ORDER BY a.id""", id).forEach { body ->
                    bodies.addObject().put("attemptId", (body.getValue("id") as Number).toLong())
                        .put("hash", body.getValue("blob_sha256").toString()).put("kind", body.getValue("kind").toString())
                        .put("mediaType", body.getValue("media_type").toString())
                }
                if (run.leaseUntil?.isAfter(now()) != true) throw ReprocessException("LEASE_LOST", 409)
                insertItem(runId, ReprocessKind.PAPER, id.toString(), input, time)
                scope.put("cursor", id)
            }
            scope.put("frozenCount", scope.path("frozenCount").asLong(0) + records.size)
            val more = jdbc.queryForObject("""SELECT COUNT(*) FROM discovery_paper_archive_record
                WHERE execution_id=? AND id>? AND id<=?""", Long::class.java,
                run.sourceExecutionId, scope.path("cursor").asLong(0), scope.path("upperRecordId").asLong())!! > 0
            if (!more) {
                val matches = try { watermark(run.sourceExecutionId).scopeHash == scope.path("scopeHash").asText() }
                    catch (_: ReprocessException) { false }
                if (!matches) {
                    finishLocked(run, ReprocessState.FAILED, "SCOPE_CHANGED", time, requireLive = true)
                    changed = true
                    return@transaction true
                }
                val manifestHash = manifest(runId)
                scope.put("frozen", true).put("manifestHash", manifestHash).put("sourcesHash", manifestHash)
            }
            if (run.leaseUntil?.isAfter(now()) != true) throw ReprocessException("LEASE_LOST", 409)
            jdbc.update("UPDATE discovery_paper_reprocess_run SET scope_json=?,revision=revision+1,updated_at=? WHERE execution_id=?",
                json(scope, "INPUT_TOO_LARGE"), db(time), runId)
            !more
        }
        if (changed) throw ReprocessException("SCOPE_CHANGED", 409)
        return complete
    }

    private fun manifest(runId: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var cursor = 0L
        while (true) {
            val rows = jdbc.queryForList("SELECT id,item_key,input_json FROM discovery_paper_reprocess_item WHERE execution_id=? AND kind='PAPER' AND id>? ORDER BY id LIMIT 100", runId, cursor)
            if (rows.isEmpty()) break
            rows.forEach {
                digest.update((it.getValue("item_key").toString() + ":" + sha(it.getValue("input_json").toString()) + "\n").toByteArray(Charsets.UTF_8))
                cursor = (it.getValue("id") as Number).toLong()
            }
        }
        return hex(digest.digest())
    }

    fun claimRun(owner: String): ReprocessRun? {
        require(owner.isNotBlank() && owner.length <= 64)
        return transaction {
            val run = jdbc.query("SELECT * FROM discovery_paper_reprocess_run WHERE active_key='PAPER_REPROCESS' FOR UPDATE", runMapper).firstOrNull()
                ?: return@transaction null
            val time = now()
            if (run.leaseUntil?.isAfter(time) == true) return@transaction null
            if (run.leaseToken != null && run.state != ReprocessState.PREPARING) {
                finishLocked(run, ReprocessState.INTERRUPTED, "EXECUTION_LOST", time)
                return@transaction null
            }
            val token = UUID.randomUUID().toString()
            jdbc.update("""UPDATE discovery_paper_reprocess_run SET lease_token=?,lease_until=?,generation=generation+1,
                revision=revision+1,updated_at=? WHERE execution_id=?""", token, db(time.plusSeconds(60)), db(time), run.executionId)
            jdbc.update("""UPDATE task_execution SET owner_token=?,status=?,heartbeat_at=?,finished_at=NULL,updated_at=?
                WHERE id=? AND task_type='DISCOVERY_PAPER_REPROCESS'""",
                token, if (run.cancelRequested) "CANCELLING" else "RUNNING", taskTime(time), taskTime(time), run.executionId)
            find(run.executionId)
        }
    }

    fun heartbeat(runId: Long, token: String, generation: Long): Boolean = transaction {
        val run = owned(runId, token, generation) ?: return@transaction false
        val time = now(); val until = db(time.plusSeconds(60))
        jdbc.update("UPDATE discovery_paper_reprocess_run SET lease_until=?,updated_at=? WHERE execution_id=?", until, db(time), runId)
        jdbc.update("""UPDATE discovery_paper_reprocess_item SET lease_until=?,updated_at=?
            WHERE execution_id=? AND state='RUNNING' AND generation=?""", until, db(time), runId, run.generation)
        if (tasks.heartbeatExecution(runId, token, taskTime(time)) != 1) throw ReprocessException("TASK_OWNERSHIP_LOST", 409)
        progress(runId, time)
        true
    }

    fun claimItem(runId: Long, token: String, generation: Long, kind: ReprocessKind): ReprocessItem? = transaction {
        val run = owned(runId, token, generation) ?: return@transaction null
        if (run.state != ReprocessState.RUNNING || run.cancelRequested || !run.scope.path("frozen").asBoolean()) return@transaction null
        if (jdbc.queryForObject("SELECT COUNT(*) FROM discovery_paper_reprocess_item WHERE execution_id=? AND state='RUNNING'", Long::class.java, runId)!! != 0L)
            return@transaction null
        val item = jdbc.query("SELECT * FROM discovery_paper_reprocess_item WHERE execution_id=? AND kind=? AND state='PENDING' ORDER BY id LIMIT 1 FOR UPDATE",
            itemMapper, runId, kind.name).firstOrNull() ?: return@transaction null
        val time = now()
        jdbc.update("""UPDATE discovery_paper_reprocess_item SET state='RUNNING',lease_token=?,lease_until=?,generation=?,
            revision=revision+1,updated_at=? WHERE id=? AND state='PENDING'""", UUID.randomUUID().toString(), db(time.plusSeconds(60)), generation, db(time), item.id)
        item(runId, item.id)
    }

    fun completeItem(runId: Long, runToken: String, runGeneration: Long, itemId: Long, itemToken: String,
                     itemGeneration: Long, state: ReprocessItemState, result: JsonNode): Boolean = transaction {
        val run = owned(runId, runToken, runGeneration) ?: return@transaction false
        require(state in ITEM_TERMINAL)
        var actualState = state
        val encoded = try { json(result, "RESULT_TOO_LARGE", result = true) }
            catch (e: ReprocessException) {
                if (e.code != "RESULT_TOO_LARGE") throw e
                actualState = ReprocessItemState.ERROR
                json(errorResult("RESULT_TOO_LARGE", ReprocessItemState.ERROR, item(runId, itemId)?.input), "RESULT_TOO_LARGE", result = true)
            }
        val time = now()
        if (run.leaseUntil?.isAfter(time) != true || itemGeneration != runGeneration) return@transaction false
        val written = jdbc.update("""UPDATE discovery_paper_reprocess_item SET state=?,result_json=?,lease_token=NULL,lease_until=NULL,
            revision=revision+1,updated_at=?,completed_at=? WHERE execution_id=? AND id=? AND state='RUNNING'
            AND lease_token=? AND generation=? AND lease_until>?""",
            actualState.name, encoded, db(time), db(time), runId, itemId, itemToken, itemGeneration, db(time)) == 1
        if (written) progress(runId, time)
        written
    }

    fun addTarget(runId: Long, token: String, generation: Long, key: String, input: JsonNode): Boolean = transaction {
        val run = owned(runId, token, generation) ?: return@transaction false
        require(key.matches(HASH))
        if (run.state != ReprocessState.RUNNING || run.cancelRequested || !run.scope.path("frozen").asBoolean()) return@transaction false
        if (jdbc.queryForObject("SELECT COUNT(*) FROM discovery_paper_reprocess_item WHERE execution_id=? AND kind='PAPER' AND state IN ('PENDING','RUNNING')", Long::class.java, runId)!! != 0L)
            throw ReprocessException("PAPERS_NOT_PARSED", 409)
        val encoded = json(input, "INPUT_TOO_LARGE")
        val existing = jdbc.query("SELECT * FROM discovery_paper_reprocess_item WHERE execution_id=? AND kind='TARGET' AND item_key=?", itemMapper, runId, key).firstOrNull()
        if (existing != null) {
            // Compare persisted JSON values, not the caller's incidental IntNode/LongNode classes.
            if (existing.input != mapper.readTree(encoded)) throw ReprocessException("IMMUTABLE_INPUT_CONFLICT", 409)
            return@transaction true
        }
        if (run.leaseUntil?.isAfter(now()) != true) return@transaction false
        insertEncodedItem(runId, ReprocessKind.TARGET, key, encoded, now())
        true
    }

    fun setPhase(runId: Long, token: String, generation: Long, phase: PaperReprocessPhase): Boolean = transaction {
        val run = owned(runId, token, generation) ?: return@transaction false
        if (run.cancelRequested || run.state == ReprocessState.CANCELLING) return@transaction false
        if (phase != PaperReprocessPhase.FREEZE && !run.scope.path("frozen").asBoolean()) throw ReprocessException("SCOPE_NOT_FROZEN", 409)
        jdbc.update("UPDATE discovery_paper_reprocess_run SET phase=?,state=?,revision=revision+1,updated_at=? WHERE execution_id=?",
            phase.name, if (phase == PaperReprocessPhase.FREEZE) "PREPARING" else "RUNNING", db(now()), runId)
        true
    }

    fun finish(runId: Long, token: String, generation: Long, state: ReprocessState, reasonCode: String? = null): Boolean = transaction {
        val run = owned(runId, token, generation) ?: return@transaction false
        require(state !in ACTIVE)
        if (state == ReprocessState.COMPLETED || state == ReprocessState.COMPLETED_WITH_ERRORS) {
            if (!run.scope.path("frozen").asBoolean() || jdbc.queryForObject("SELECT COUNT(*) FROM discovery_paper_reprocess_item WHERE execution_id=? AND state IN ('PENDING','RUNNING')", Long::class.java, runId)!! != 0L)
                throw ReprocessException("ITEMS_NOT_FINISHED", 409)
            if (run.cancelRequested) throw ReprocessException("CANCEL_REQUESTED", 409)
            if (state == ReprocessState.COMPLETED && jdbc.queryForObject("SELECT COUNT(*) FROM discovery_paper_reprocess_item WHERE execution_id=? AND state='ERROR'", Long::class.java, runId)!! != 0L)
                throw ReprocessException("ITEM_ERRORS", 409)
        }
        finishLocked(run, state, reasonCode, now(), requireLive = true)
        true
    }

    private fun finishLocked(run: ReprocessRun, state: ReprocessState, reason: String?, time: Instant, requireLive: Boolean = false) {
        if (requireLive && run.leaseUntil?.isAfter(now()) != true) throw ReprocessException("LEASE_LOST", 409)
        val itemState = if (state == ReprocessState.CANCELLED) ReprocessItemState.CANCELLED else ReprocessItemState.ERROR
        val code = reason ?: if (state == ReprocessState.CANCELLED) "CANCELLED" else state.name
        terminalizeItems(run.executionId, setOf(ReprocessItemState.PENDING, ReprocessItemState.RUNNING), itemState, code, time)
        if (requireLive && run.leaseUntil?.isAfter(now()) != true) throw ReprocessException("LEASE_LOST", 409)
        val scope = run.scope.deepCopy<ObjectNode>()
        reason?.let { scope.put("reasonCode", it.take(64)) }
        jdbc.update("""UPDATE discovery_paper_reprocess_run SET state=?,active_key=NULL,scope_json=?,lease_token=NULL,
            lease_until=NULL,revision=revision+1,updated_at=?,finished_at=? WHERE execution_id=?""",
            state.name, json(scope, "INPUT_TOO_LARGE"), db(time), db(time), run.executionId)
        val summary = summary(run.executionId)
        val taskState = when (state) {
            ReprocessState.COMPLETED -> "SUCCESS"
            ReprocessState.CANCELLED -> "CANCELLED"
            ReprocessState.INTERRUPTED -> "INTERRUPTED"
            else -> "FAILED"
        }
        val result = mapper.valueToTree<ObjectNode>(summary).put("state", state.name)
        // An expired worker still has its old task owner; run lock prevents it from racing recovery.
        val owner = run.leaseToken ?: jdbc.queryForObject("SELECT owner_token FROM task_execution WHERE id=?", String::class.java, run.executionId)
        if (owner == null) throw ReprocessException("TASK_OWNERSHIP_LOST", 409)
        val processed = (summary.paperComplete + summary.paperErrors + summary.paperSkipped).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val errors = summary.paperErrors.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val summaryJson = mapper.writeValueAsString(result)
        if (tasks.finishOwned(run.executionId, owner, taskState, summaryJson, processed, errors, reason, taskTime(time)) != 1) {
            // The shared task reaper may have interrupted this same expired owner first. Reconcile
            // that row without reviving the execution or accepting a different owner's result.
            if (state != ReprocessState.INTERRUPTED || jdbc.update("""UPDATE task_execution
                SET result_summary=?,success_count=?,failure_count=?,error_message=?,updated_at=?
                WHERE id=? AND owner_token=? AND status='INTERRUPTED'""",
                    summaryJson, processed, errors, reason, taskTime(time), run.executionId, owner) != 1)
                throw ReprocessException("TASK_OWNERSHIP_LOST", 409)
        }
    }

    fun cancel(runId: Long, revision: Long): ReprocessRun = transaction {
        val run = locked(runId) ?: throw ReprocessException("RUN_NOT_FOUND", 404)
        if (run.revision != revision) throw ReprocessException("REVISION_CONFLICT", 409)
        if (run.state !in ACTIVE) throw ReprocessException("RUN_NOT_ACTIVE", 409)
        val time = now()
        jdbc.update("""UPDATE discovery_paper_reprocess_run SET state='CANCELLING',cancel_requested=1,
            revision=revision+1,updated_at=? WHERE execution_id=?""", db(time), runId)
        terminalizeItems(runId, setOf(ReprocessItemState.PENDING), ReprocessItemState.CANCELLED, "CANCELLED", time)
        jdbc.update("UPDATE task_execution SET status='CANCELLING',updated_at=? WHERE id=? AND status='RUNNING'", taskTime(time), runId)
        find(runId)!!
    }

    fun retry(runId: Long, revision: Long, versions: JsonNode): ReprocessRun {
        try {
            return transaction {
                val run = locked(runId) ?: throw ReprocessException("RUN_NOT_FOUND", 404)
                run.scope.path("retryExecutionId").takeIf { it.isIntegralNumber }?.let {
                    return@transaction find(it.asLong()) ?: throw ReprocessException("RUN_NOT_FOUND", 404)
                }
                if (run.revision != revision) throw ReprocessException("REVISION_CONFLICT", 409)
                if (run.state !in setOf(ReprocessState.COMPLETED_WITH_ERRORS, ReprocessState.FAILED, ReprocessState.INTERRUPTED, ReprocessState.CANCELLED))
                    throw ReprocessException("RETRY_NOT_ALLOWED", 409)
                if (versions.get("parserVersions") != run.scope.get("parserVersions") || versions.get("codeVersion") != run.scope.get("codeVersion"))
                    throw ReprocessException("VERSION_CHANGED", 409)
                if (!run.scope.path("frozen").asBoolean() && watermark(run.sourceExecutionId).scopeHash != run.scope.path("scopeHash").asText())
                    throw ReprocessException("SCOPE_CHANGED", 409)
                if (active() != null) throw ReprocessException("ACTIVE_RUN_EXISTS", 409)
                validateItems(runId)
                val counts = summary(runId)
                if (counts.targets > 0 && counts.paperErrors > 0)
                    return@transaction retryWithNewEvidence(run)
                val time = now()
                // Targets are derived only after every paper has parsed. Unfinished candidates must
                // be regenerated, never overwritten in place with new refs after an error retry.
                if (run.mode == ReprocessMode.AUDIT) {
                    jdbc.update("DELETE FROM discovery_paper_reprocess_item WHERE execution_id=? AND kind='TARGET' AND state<>'COMPLETE'", runId)
                    jdbc.update("""UPDATE discovery_paper_reprocess_item SET state='PENDING',result_json=NULL,lease_token=NULL,
                        lease_until=NULL,generation=generation+1,revision=revision+1,updated_at=?,completed_at=NULL
                        WHERE execution_id=? AND state NOT IN ('COMPLETE','SKIPPED')""", db(time), runId)
                } else {
                    // Explicit retry may need a missing ordinary reservation after the first pass
                    // sealed input. Reopen only this parent's owned run, never a foreign/recheck run.
                    // Successful apply receipts remain sealed and are observed without replay.
                    jdbc.update("""UPDATE discovery_verification_run SET input_closed=0,revision=revision+1,updated_at=?
                        WHERE execution_id=? AND recheck_json IS NULL
                        AND JSON_UNQUOTE(JSON_EXTRACT(settings_json,'$.paperReprocessParentId'))=?
                        AND EXISTS(SELECT 1 FROM discovery_paper_reprocess_item i WHERE i.execution_id=?
                            AND i.kind='TARGET' AND i.state NOT IN ('COMPLETE','SKIPPED')
                            AND JSON_EXTRACT(i.result_json,'$.applicationError')=true
                            AND COALESCE(JSON_EXTRACT(i.result_json,'$.applied'),false)=false)""",
                        db(time), runId, runId.toString(), runId)
                    // The first pass terminalizes RAW-missing reservations so the parent can
                    // report its caught failure. Only explicit retry may resume that same,
                    // never-activated owned identity; committed verification is never reset.
                    jdbc.update("""UPDATE discovery_verification_item v
                        JOIN discovery_verification_run r ON r.execution_id=v.owner_execution_id
                        JOIN discovery_paper_reprocess_item t ON t.execution_id=r.execution_id
                            AND JSON_EXTRACT(t.result_json,'$.verificationItemId')=v.id
                        SET v.state='RESERVED',v.reasons_json=NULL,v.completed_at=NULL,
                            v.generation=v.generation+1,v.updated_at=?
                        WHERE r.execution_id=? AND r.recheck_json IS NULL
                            AND JSON_UNQUOTE(JSON_EXTRACT(r.settings_json,'$.paperReprocessParentId'))=?
                            AND v.review_epoch=0 AND v.state='ERROR' AND v.activated_at IS NULL
                            AND v.admission_revision IS NULL AND JSON_LENGTH(v.reasons_json)=1
                            AND JSON_UNQUOTE(JSON_EXTRACT(v.reasons_json,'$[0]'))='REGISTRATION_RAW_MISSING'
                            AND t.kind='TARGET' AND t.state NOT IN ('COMPLETE','SKIPPED')
                            AND JSON_EXTRACT(t.result_json,'$.applicationError')=true
                            AND COALESCE(JSON_EXTRACT(t.result_json,'$.applied'),false)=false""",
                        db(time), runId, runId.toString())
                    // An explicit retry also resumes this parent's cancelled but unfinished ordinary
                    // qualification. Cancellation receipts, completed peers and prior evidence stay
                    // durable; the same item is requeued once and cannot be requeued twice.
                    jdbc.update("""UPDATE discovery_verification_item v
                        JOIN discovery_verification_run r ON r.execution_id=v.owner_execution_id
                        JOIN discovery_paper_reprocess_item t ON t.execution_id=r.execution_id AND t.kind='TARGET'
                        SET v.state='QUEUED',v.outcome=NULL,v.route='NONE',v.qualification='NOT_EVALUATED',
                            v.completed_at=NULL,v.lease_token=NULL,v.lease_until=NULL,v.next_attempt_at=NULL,
                            v.generation=v.generation+1,v.updated_at=?
                        WHERE r.execution_id=? AND r.recheck_json IS NULL
                            AND JSON_UNQUOTE(JSON_EXTRACT(r.settings_json,'$.paperReprocessParentId'))=?
                            AND v.review_epoch=0 AND v.state='CANCELLED' AND v.admission_revision IS NULL
                            AND t.state NOT IN ('COMPLETE','SKIPPED')
                            AND (JSON_EXTRACT(t.result_json,'$.verificationItemId')=v.id
                                OR v.expert_doc_id=COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.docId')),'null'),
                                    NULLIF(JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.proposedDocId')),'null')))""",
                        db(time), runId, runId.toString())
                    jdbc.update("""UPDATE discovery_paper_reprocess_item SET state='PENDING',lease_token=NULL,
                        lease_until=NULL,generation=generation+1,revision=revision+1,updated_at=?,completed_at=NULL
                        WHERE execution_id=? AND state NOT IN ('COMPLETE','SKIPPED')""", db(time), runId)
                }
                val state = if (run.scope.path("frozen").asBoolean()) "RUNNING" else "PREPARING"
                jdbc.update("""UPDATE discovery_paper_reprocess_run SET active_key='PAPER_REPROCESS',state=?,phase=?,cancel_requested=0,
                    lease_token=NULL,lease_until=NULL,generation=generation+1,revision=revision+1,updated_at=?,finished_at=NULL WHERE execution_id=?""",
                    state, if (state == "PREPARING") "FREEZE" else "READ", db(time), runId)
                jdbc.update("""UPDATE task_execution SET status='RUNNING',finished_at=NULL,heartbeat_at=?,updated_at=?,
                    error_message=NULL,interruption_reason_code=NULL,interruption_reason_detail=NULL,result_summary=NULL WHERE id=?""", taskTime(time), taskTime(time), runId)
                progress(runId, time)
                find(runId)!!
            }
        } catch (_: DuplicateKeyException) { throw ReprocessException("ACTIVE_RUN_EXISTS", 409) }
    }

    /** A new evidence union gets its own receipts and review groups. Old applied targets stay immutable. */
    private fun retryWithNewEvidence(previous: ReprocessRun): ReprocessRun {
        val time = now()
        val key = GeneratedKeyHolder()
        val owner = UUID.randomUUID().toString()
        jdbc.update({ connection -> connection.prepareStatement("""INSERT INTO task_execution
            (task_type,trigger_type,status,request_payload,started_at,created_at,updated_at,owner_token,heartbeat_at)
            VALUES ('DISCOVERY_PAPER_REPROCESS','MANUAL','RUNNING',?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS).also {
            it.setString(1, mapper.writeValueAsString(mapOf("retryOf" to previous.executionId)))
            it.setObject(2, taskTime(time)); it.setObject(3, taskTime(time)); it.setObject(4, taskTime(time))
            it.setString(5, owner); it.setObject(6, taskTime(time))
        } }, key)
        val id = key.key!!.toLong()
        val scope = previous.scope.deepCopy<ObjectNode>().put("retryOf", previous.executionId)
        jdbc.update("""INSERT INTO discovery_paper_reprocess_run
            (execution_id,source_execution_id,request_key,request_hash,active_key,mode,state,phase,scope_json,created_at,updated_at)
            VALUES (?,?,?,?,'PAPER_REPROCESS',?,'PREPARING','READ',?,?,?)""",
            id, previous.sourceExecutionId, UUID.randomUUID().toString(), previous.requestHash,
            previous.mode.name, json(scope, "INPUT_TOO_LARGE"), db(time), db(time))
        jdbc.update("""INSERT INTO discovery_paper_reprocess_item
            (execution_id,kind,item_key,state,input_json,result_json,created_at,updated_at,completed_at)
            SELECT ?,kind,item_key,IF(state IN ('COMPLETE','SKIPPED'),state,'PENDING'),input_json,
                IF(state IN ('COMPLETE','SKIPPED'),result_json,NULL),?,?,
                IF(state IN ('COMPLETE','SKIPPED'),completed_at,NULL)
            FROM discovery_paper_reprocess_item WHERE execution_id=? AND kind='PAPER'""",
            id, db(time), db(time), previous.executionId)
        val linked = previous.scope.deepCopy<ObjectNode>().put("retryExecutionId", id)
        jdbc.update("UPDATE discovery_paper_reprocess_run SET scope_json=?,revision=revision+1 WHERE execution_id=?",
            json(linked, "INPUT_TOO_LARGE"), previous.executionId)
        return find(id)!!
    }

    fun releaseRejected(runId: Long, token: String, generation: Long): Boolean = transaction {
        if (owned(runId, token, generation) == null) return@transaction false
        jdbc.update("""UPDATE discovery_paper_reprocess_run SET lease_token=NULL,lease_until=NULL,
            generation=generation+1,revision=revision+1,updated_at=? WHERE execution_id=?""", db(now()), runId)
        true
    }

    fun summary(runId: Long): ReprocessSummary {
        if (jdbc.queryForObject("""SELECT COUNT(*) FROM discovery_paper_reprocess_item WHERE execution_id=?
            AND (kind NOT IN ('PAPER','TARGET') OR state NOT IN ('PENDING','RUNNING','COMPLETE','ERROR','SKIPPED','CANCELLED'))""",
                Long::class.java, runId)!! != 0L) throw ReprocessException("INVALID_STATE", 409)
        val run = find(runId)
        val row = jdbc.queryForMap("""SELECT COALESCE(SUM(kind='PAPER'),0) papers,
            COALESCE(SUM(kind='PAPER' AND state='COMPLETE'),0) paper_complete,
            COALESCE(SUM(kind='PAPER' AND state='ERROR'),0) paper_errors,
            COALESCE(SUM(kind='PAPER' AND state='CANCELLED'),0) paper_cancelled,
            COALESCE(SUM(kind='PAPER' AND state='SKIPPED'),0) paper_skipped,
            COALESCE(SUM(kind='TARGET'),0) targets, COALESCE(SUM(kind='TARGET' AND state='COMPLETE'),0) target_complete,
            COALESCE(SUM(kind='TARGET' AND state='ERROR'),0) target_errors,
            COALESCE(SUM(CASE WHEN kind='TARGET' THEN COALESCE(JSON_EXTRACT(result_json,'$.actualNew'),0) ELSE 0 END),0) actual_new,
            COALESCE(SUM(CASE WHEN kind='TARGET' THEN COALESCE(JSON_EXTRACT(result_json,'$.actualUpdated'),0) ELSE 0 END),0) actual_updated,
            COALESCE(SUM(CASE WHEN kind='TARGET' THEN COALESCE(JSON_EXTRACT(result_json,'$.actualAdmitted'),0) ELSE 0 END),0) actual_admitted
            FROM discovery_paper_reprocess_item WHERE execution_id=?""", runId)
        fun n(key: String) = (row.getValue(key) as Number).toLong()
        val business = if (run?.mode == ReprocessMode.ENFORCE) jdbc.queryForMap("""SELECT
            COALESCE(SUM(COALESCE(JSON_EXTRACT(t.result_json,'$.alreadyAdmitted'),0)=0 AND EXISTS(
                SELECT 1 FROM discovery_verification_item v JOIN discovery_verification_run r ON r.execution_id=v.owner_execution_id
                WHERE JSON_EXTRACT(r.settings_json,'$.paperReprocessParentId')=? AND v.admission_revision IS NOT NULL
                AND v.expert_doc_id=COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.docId')),
                    JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.proposedDocId'))))),0) admitted,
            COALESCE(SUM(EXISTS(SELECT 1 FROM discovery_verification_item v JOIN discovery_verification_run r ON r.execution_id=v.owner_execution_id
                WHERE JSON_EXTRACT(r.settings_json,'$.paperReprocessParentId')=? AND
                v.expert_doc_id=JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.docId')) AND
                JSON_UNQUOTE(JSON_EXTRACT(v.snapshot_json,'$.identitySupplement.phase'))='APPLIED' AND
                JSON_UNQUOTE(JSON_EXTRACT(v.snapshot_json,'$.identitySupplement.repairReceipt.reason'))='REPAIRED')),0) repaired
            FROM discovery_paper_reprocess_item t WHERE t.execution_id=? AND t.kind='TARGET'""", runId, runId, runId) else null
        // Join only this target's owned verification item. Never count foreign reviews of the same
        // document, and never multiply a target by the number of attempts/reviews for that document.
        val classified = jdbc.queryForMap("""SELECT
            COALESCE(SUM(t.kind='PAPER' AND (JSON_LENGTH(JSON_EXTRACT(t.input_json,'$.bodies'))=0 OR
                JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.reasonCode')) IN ('SOURCE_MISSING','SOURCE_DELETED'))),0) missing,
            COALESCE(SUM(t.kind='TARGET' AND (
                v.state IN ('RESERVED','QUEUED','RUNNING','RETRY_WAIT') OR v.outcome='PENDING' OR
                (v.id IS NULL AND t.state IN ('PENDING','RUNNING') AND (
                    JSON_EXTRACT(t.result_json,'$.needsRecheck')=true OR
                    JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.targetOutcome'))='NEW')))),0) pending,
            COALESCE(SUM(t.kind='TARGET' AND JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.targetOutcome'))
                IN ('NEW','REPAIRED','UNCHANGED','CONFLICT','PROTECTED','SKIPPED','ERROR')),0) outcome_known,
            -- AUDIT persists candidate proposals with businessExistenceEvaluated=false; the proposal
            -- count must still be shown, explicitly as an overlapping candidate (not a business write).
            COALESCE(SUM(t.kind='TARGET' AND JSON_TYPE(JSON_EXTRACT(t.result_json,'$.wouldCreate'))='BOOLEAN'),0) create_known,
            COALESCE(SUM(t.kind='TARGET' AND JSON_EXTRACT(t.result_json,'$.wouldCreate')=true),0) would_create,
            COALESCE(SUM(t.kind='TARGET' AND JSON_TYPE(JSON_EXTRACT(t.result_json,'$.wouldRepair'))='BOOLEAN'),0) repair_known,
            COALESCE(SUM(t.kind='TARGET' AND JSON_EXTRACT(t.result_json,'$.wouldRepair')=true),0) would_repair,
            COALESCE(SUM(t.kind='TARGET' AND JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.targetOutcome'))='UNCHANGED'
                AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(v.snapshot_json,'$.identitySupplement.phase')),'')
                    NOT IN ('CONFLICT','PROTECTED')
                AND NOT (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(v.snapshot_json,'$.identitySupplement.phase')),'')='APPLIED'
                    AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(v.snapshot_json,'$.identitySupplement.repairReceipt.reason')),'')='REPAIRED')),0) unchanged,
            COALESCE(SUM(t.kind='TARGET' AND (
                JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.targetOutcome'))='CONFLICT' OR
                JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.reasonCode'))='CONFLICT' OR
                JSON_UNQUOTE(JSON_EXTRACT(v.snapshot_json,'$.identitySupplement.phase'))='CONFLICT')),0) conflict,
            COALESCE(SUM(t.kind='TARGET' AND (
                JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.targetOutcome'))='PROTECTED' OR v.outcome='PROTECTED' OR
                JSON_UNQUOTE(JSON_EXTRACT(v.snapshot_json,'$.identitySupplement.phase'))='PROTECTED')),0) protected,
            COALESCE(SUM(t.kind='TARGET' AND v.admission_revision IS NOT NULL AND v.projection_state='ERROR'),0) sync_failure,
            COALESCE(SUM(t.kind='TARGET' AND v.qualification='MET'),0) qualification_met,
            COALESCE(SUM(t.kind='TARGET' AND v.qualification='UNKNOWN'),0) qualification_unknown,
            COALESCE(SUM(t.kind='TARGET' AND v.qualification='NOT_MET'),0) qualification_not_met,
            COALESCE(SUM(t.kind='TARGET' AND v.qualification='CONFLICT'),0) qualification_conflict,
            COALESCE(SUM(t.kind='TARGET' AND v.qualification='NOT_EVALUATED'),0) qualification_not_evaluated,
            COALESCE(SUM(t.kind='TARGET' AND v.id IS NULL),0) qualification_unavailable
            FROM discovery_paper_reprocess_item t
            LEFT JOIN discovery_verification_item v ON t.kind='TARGET' AND v.id=(
                SELECT vi.id FROM discovery_verification_item vi
                JOIN discovery_verification_run vr ON vr.execution_id=vi.owner_execution_id
                WHERE JSON_EXTRACT(vr.settings_json,'$.paperReprocessParentId')=t.execution_id
                    AND vi.expert_doc_id=COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.docId')),'null'),
                        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(t.result_json,'$.proposedDocId')),'null'))
                    AND CASE WHEN JSON_TYPE(JSON_EXTRACT(t.result_json,'$.verificationItemId'))='INTEGER'
                        THEN vi.id=JSON_EXTRACT(t.result_json,'$.verificationItemId')
                            AND vi.owner_execution_id=JSON_EXTRACT(t.result_json,'$.verificationRunId')
                        ELSE vi.owner_execution_id=COALESCE(
                            JSON_EXTRACT(t.result_json,'$.childRunId'),t.execution_id) END
                ORDER BY vi.id DESC LIMIT 1)
            WHERE t.execution_id=?""", runId)
        fun c(key: String) = (classified.getValue(key) as Number).toLong()
        val enforce = run?.mode == ReprocessMode.ENFORCE
        val totalKnown = run?.scope?.path("frozen")?.asBoolean() == true
        val targetTotalKnown = totalKnown && n("paper_complete") + n("paper_errors") + n("paper_skipped") == n("papers") &&
            run?.phase in setOf(PaperReprocessPhase.APPLY, PaperReprocessPhase.REVIEW, PaperReprocessPhase.SAVE)
        fun proposal(known: String, count: String): Long? =
            if (c(known) == n("targets") && (n("targets") > 0 || targetTotalKnown)) c(count) else null
        return ReprocessSummary(n("papers"), n("paper_complete"), n("paper_errors"), n("paper_cancelled"), n("targets"), n("target_complete"), n("target_errors"),
            if (enforce) n("actual_new").coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0,
            if (enforce) (business?.get("repaired") as? Number)?.toInt() ?: n("actual_updated").coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0,
            if (enforce) (business?.get("admitted") as? Number)?.toInt() ?: n("actual_admitted").coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0,
            pendingVerification = if (enforce) c("pending") else 0,
            wouldCreate = proposal("create_known", "would_create"), wouldRepair = proposal("repair_known", "would_repair"),
            unchanged = proposal("outcome_known", "unchanged"), conflict = c("conflict"),
            protected = proposal("outcome_known", "protected"), syncFailure = c("sync_failure"),
            paperSkipped = n("paper_skipped"), missingSourcePapers = c("missing"),
            qualification = ReprocessQualificationSummary(c("qualification_met"), c("qualification_unknown"),
                c("qualification_not_met"), c("qualification_conflict"), c("qualification_not_evaluated"), c("qualification_unavailable")),
            totalKnown = totalKnown, targetTotalKnown = targetTotalKnown)
    }

    private fun progress(runId: Long, time: Instant) {
        val counts = summary(runId)
        tasks.updateProgressCounts(runId, (counts.paperComplete + counts.paperErrors + counts.paperSkipped).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            counts.paperErrors.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), taskTime(time))
    }

    /** Validate before bulk mutation, so future/corrupt evidence remains untouched for diagnosis. */
    private fun validateItems(runId: Long) {
        var cursor = 0L
        while (true) {
            val rows = jdbc.query("SELECT * FROM discovery_paper_reprocess_item WHERE execution_id=? AND id>? ORDER BY id LIMIT 100",
                itemMapper, runId, cursor)
            if (rows.isEmpty()) return
            cursor = rows.last().id
        }
    }

    private fun terminalizeItems(runId: Long, from: Set<ReprocessItemState>, state: ReprocessItemState, reason: String, time: Instant) {
        var cursor = 0L
        while (true) {
            val rows = jdbc.query("SELECT * FROM discovery_paper_reprocess_item WHERE execution_id=? AND id>? ORDER BY id LIMIT 100",
                itemMapper, runId, cursor)
            if (rows.isEmpty()) return
            rows.forEach { item ->
                if (item.state in from) {
                    val preserved = if (item.kind == ReprocessKind.TARGET && item.result != null)
                        item.result.deepCopy<ObjectNode>().put("interruptionReason", reason.take(64)) else errorResult(reason, state, item.input)
                    val result = json(preserved, "RESULT_TOO_LARGE", result = true)
                    if (jdbc.update("""UPDATE discovery_paper_reprocess_item SET state=?,result_json=?,lease_token=NULL,lease_until=NULL,
                        revision=revision+1,updated_at=?,completed_at=? WHERE execution_id=? AND id=? AND state=?""",
                            state.name, result, db(time), db(time), runId, item.id, item.state.name) != 1)
                        throw ReprocessException("REVISION_CONFLICT", 409)
                }
            }
            cursor = rows.last().id
        }
    }

    private fun locked(id: Long): ReprocessRun? = jdbc.query("SELECT * FROM discovery_paper_reprocess_run WHERE execution_id=? FOR UPDATE", runMapper, id).firstOrNull()
    private fun owned(id: Long, token: String, generation: Long): ReprocessRun? = locked(id)?.takeIf {
        it.state in ACTIVE && it.leaseToken == token && it.generation == generation && it.leaseUntil?.isAfter(now()) == true
    }
    private fun insertItem(runId: Long, kind: ReprocessKind, key: String, input: JsonNode, time: Instant) =
        insertEncodedItem(runId, kind, key, json(input, "INPUT_TOO_LARGE"), time)
    private fun insertEncodedItem(runId: Long, kind: ReprocessKind, key: String, encoded: String, time: Instant) {
        jdbc.update("""INSERT INTO discovery_paper_reprocess_item
            (execution_id,kind,item_key,state,input_json,created_at,updated_at) VALUES (?,?,?,'PENDING',?,?,?)""",
            runId, kind.name, key, encoded, db(time), db(time))
    }

    private fun parseObject(text: String, code: String): JsonNode = try {
        mapper.readTree(text)?.takeIf { it.isObject } ?: throw ReprocessException(code, 409)
    } catch (e: ReprocessException) { throw e } catch (_: Exception) { throw ReprocessException(code, 409) }

    private fun json(value: JsonNode, tooLarge: String, result: Boolean = false): String {
        val text = mapper.writeValueAsString(value)
        if (text.toByteArray(Charsets.UTF_8).size > MAX_JSON) throw ReprocessException(tooLarge, 413)
        validate(value, result)
        return text
    }
    private fun readJson(text: String, result: Boolean = false): JsonNode {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_JSON) throw ReprocessException(if (result) "RESULT_TOO_LARGE" else "INPUT_TOO_LARGE", 413)
        return parseObject(text, "INVALID_SCHEMA").also { validate(it, result) }
    }
    private fun validate(value: JsonNode, result: Boolean) {
        val version = value.get("schemaVersion")
        if (!value.isObject || version == null || !version.isIntegralNumber || !version.canConvertToInt() || version.intValue() != 1)
            throw ReprocessException("INVALID_SCHEMA", 409)
        if (result) {
            val trace = value.get("trace")
            if (trace == null || !trace.isArray || trace.size() != 6) throw ReprocessException("INVALID_TRACE", 409)
            trace.forEachIndexed { index, step ->
                val number = step.get("stepNo")
                if (!step.isObject || number == null || !number.isIntegralNumber || !number.canConvertToInt() || number.intValue() != index + 1 ||
                    !step.path("state").isTextual || step.path("state").asText() !in TRACE_STATES)
                    throw ReprocessException("INVALID_TRACE", 409)
            }
        }
    }
    private fun errorResult(reason: String, state: ReprocessItemState, input: JsonNode? = null): ObjectNode =
        mapper.createObjectNode().put("schemaVersion", 1).put("reasonCode", reason.take(64)).put("reason", when (reason) {
            "RESULT_TOO_LARGE" -> "解析结果超过存储上限，已保留来源引用，未截断有效证据"
            "SCOPE_CHANGED" -> "来源范围已变化，本次处理已停止"
            "CANCELLED" -> "任务已取消，已完成的结果仍保留"
            "INTERRUPTED", "EXECUTION_LOST" -> "任务执行已中断，未完成项需显式重试"
            "FAILED" -> "任务处理失败，请查看任务详情"
            else -> "工作项未能完成，请结合错误代码查看任务详情"
        }.take(256)).also { result ->
            val trace = result.putArray("trace")
            (1..6).forEach { step -> trace.addObject().put("stepNo", step).put("state", if (state == ReprocessItemState.CANCELLED) "CANCELLED" else if (step == 1) "ERROR" else "NOT_STARTED").put("reasonCode", reason.take(64)) }
            val attempts = result.putArray("attempts")
            input?.path("bodies")?.forEach { body ->
                attempts.addObject().put("attemptId", body.path("attemptId").asLong()).put("hash", body.path("hash").asText()).put("state", state.name)
            }
            input?.path("paperRefs")?.forEach { reference ->
                reference.path("attemptIds").forEach { attempt ->
                    attempts.addObject().put("attemptId", attempt.asLong()).put("hash", reference.path("bodyHash").asText()).put("state", state.name)
                }
            }
        }
    private inline fun <reified T : Enum<T>> enum(value: String): T = try { enumValueOf<T>(value) }
        catch (_: IllegalArgumentException) { throw ReprocessException("INVALID_STATE", 409) }
    private val runMapper = RowMapper { r: ResultSet, _: Int ->
        val state = enum<ReprocessState>(r.getString("state"))
        if ((state in ACTIVE && r.getString("active_key") != "PAPER_REPROCESS") || (state !in ACTIVE && r.getString("active_key") != null))
            throw ReprocessException("INVALID_STATE", 409)
        ReprocessRun(r.getLong("execution_id"), r.getLong("source_execution_id"), r.getString("request_key"), r.getString("request_hash"),
            enum(r.getString("mode")), state, enum(r.getString("phase")), readJson(r.getString("scope_json")), r.getLong("revision"),
            r.getString("lease_token"), time(r, "lease_until"), r.getLong("generation"), r.getBoolean("cancel_requested"),
            time(r, "created_at")!!, time(r, "updated_at")!!, time(r, "finished_at"))
    }
    private val itemMapper = RowMapper { r: ResultSet, _: Int -> ReprocessItem(r.getLong("id"), r.getLong("execution_id"), enum(r.getString("kind")),
        r.getString("item_key"), enum(r.getString("state")), readJson(r.getString("input_json")), r.getString("result_json")?.let { readJson(it, true) },
        r.getString("lease_token"), time(r, "lease_until"), r.getLong("generation"), r.getLong("revision")) }

    companion object {
        private const val MAX_JSON = 4 * 1024 * 1024
        private val HASH = Regex("[0-9a-f]{64}")
        private val SOURCE_TERMINAL = setOf("SUCCESS", "PARTIAL_SUCCESS", "FAILED", "INTERRUPTED", "CANCELLED")
        private val ACTIVE = setOf(ReprocessState.PREPARING, ReprocessState.RUNNING, ReprocessState.CANCELLING)
        private val ITEM_TERMINAL = setOf(ReprocessItemState.COMPLETE, ReprocessItemState.ERROR, ReprocessItemState.SKIPPED, ReprocessItemState.CANCELLED)
        private val TRACE_STATES = setOf("NOT_STARTED", "RUNNING", "SUCCESS", "INSUFFICIENT", "SKIPPED", "ERROR", "CANCELLED")
        private fun page(page: Int, size: Int) { require(page >= 0 && size in 1..100) }
        private fun taskTime(time: Instant): LocalDateTime = LocalDateTime.ofInstant(time, java.time.ZoneId.systemDefault())
        private fun db(time: Instant): LocalDateTime = LocalDateTime.ofInstant(time, ZoneOffset.UTC)
        private fun time(r: ResultSet, column: String): Instant? = r.getObject(column, LocalDateTime::class.java)?.toInstant(ZoneOffset.UTC)
        private fun sha(text: String): String = hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
