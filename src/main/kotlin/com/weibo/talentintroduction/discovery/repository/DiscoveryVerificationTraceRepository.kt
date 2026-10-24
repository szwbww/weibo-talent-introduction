package com.weibo.talentintroduction.discovery.repository

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** A missing trace is historical absence, not eight manufactured successes. Reads never initialize it. */
data class VerificationTraceRead(val recorded: Boolean, val steps: List<VerificationStep>, val blockingStep: Int?)

@Repository
class DiscoveryVerificationTraceRepository(private val jdbc: JdbcTemplate) {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    fun read(itemId: Long): VerificationTraceRead {
        val rows = jdbc.query("SELECT * FROM discovery_verification_step WHERE item_id=? ORDER BY step_no,attempt_no", rowMapper, itemId)
        val latest = rows.groupBy { it.stepNo }.values.map { it.last() }
        val blocker = latest.filter { step -> step.state == VerificationStepState.ERROR ||
            step.reasonJson?.let { mapper.readTree(it).path("value").path("blocking").asBoolean(false) } == true }.minOfOrNull { it.stepNo }
        return VerificationTraceRead(rows.isNotEmpty(), rows, blocker)
    }

    /** Called at actual activation only; RESERVED and old completed items must remain without rows. */
    @Transactional
    fun initSteps(itemId: Long, now: Instant): Boolean {
        val item = lock(itemId) ?: return false
        if (item.activatedAt == null || item.state !in setOf("QUEUED", "RUNNING", "RETRY_WAIT")) return false
        initialize(itemId, now)
        return true
    }

    @Transactional
    fun startStep(itemId: Long, token: String, generation: Long, stepNo: Int, inputJson: String,
        versionJson: String, now: Instant): VerificationStep? {
        require(stepNo in 1..8)
        val item = lock(itemId) ?: return null
        if (!owns(item, token, generation, now)) return null
        initialize(itemId, now)
        return start(itemId, stepNo, inputJson, versionJson, generation, now)
    }

    @Transactional
    fun finishStep(itemId: Long, token: String, generation: Long, stepId: Long, state: VerificationStepState,
        outputJson: String?, reasonJson: String?, now: Instant): Boolean {
        require(state.terminal)
        val item = lock(itemId) ?: return false
        if (!owns(item, token, generation, now)) return false
        val step = read(itemId).steps.singleOrNull { it.id == stepId } ?: return false
        if (mapper.readTree(step.versionJson).path("generation").asLong(-1) != generation) return false
        return finish(step, state, outputJson, reasonJson, now)
    }

    /** Commit owns run -> item locks and the still-live lease. Finish after releasing that lease in the same transaction. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    fun finishCommittedStep(itemId: Long, generation: Long, stepId: Long, state: VerificationStepState,
        outputJson: String, reasonJson: String?, now: Instant): Boolean {
        val item = lock(itemId) ?: return false
        if (item.state != "COMPLETED" || item.generation != generation) return false

        val step = read(itemId).steps.singleOrNull { it.id == stepId } ?: return false
        if (mapper.readTree(step.versionJson).path("generation").asLong(-1) != generation) return false
        return finish(step, state, outputJson, reasonJson, now)
    }
    @Transactional
    fun finishFailure(itemId: Long, generation: Long, stepId: Long, outputJson: String, now: Instant): Boolean {
        val item = lock(itemId) ?: return false
        if (item.state != "ERROR" || item.generation != generation) return false
        val step = read(itemId).steps.singleOrNull { it.id == stepId } ?: return false
        if (mapper.readTree(step.versionJson).path("generation").asLong(-1) != generation) return false
        return finish(step, VerificationStepState.SUCCESS, outputJson, null, now)
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    fun cancelSteps(itemId: Long, now: Instant) {
        val item = lock(itemId) ?: return
        check(item.state == "CANCELLED")
        // These zero-duration observations mean never started, not completed historical work.
        jdbc.update("""UPDATE discovery_verification_step SET state='SKIPPED',input_hash=?,input_json='{}',
            reason_json='{"codes":["CANCELLED_BEFORE_START"]}',started_at=?,finished_at=?
            WHERE item_id=? AND state='NOT_STARTED'""", DiscoveryVerificationRepository.replayHash("{}"),
            now.verificationUtc(), now.verificationUtc(), itemId)
        jdbc.update("""UPDATE discovery_verification_step SET state='CANCELLED',
            reason_json='{"codes":["RUN_CANCELLED"]}',finished_at=? WHERE item_id=? AND state='RUNNING'""", now.verificationUtc(), itemId)
    }

    /** Projection is authorized by the persisted admission receipt, never by a new worker/provider lease. */
    @Transactional
    fun startProjection(itemId: Long, revision: Long, now: Instant): VerificationStep? {
        val item = lock(itemId) ?: return null
        if (item.state != "COMPLETED" || item.admissionRevision != revision ||
            item.projection !in setOf("PENDING", "ERROR") || !read(itemId).recorded) return null
        val last = read(itemId).steps.lastOrNull { it.stepNo == 8 }
        if (last?.state == VerificationStepState.RUNNING && last.startedAt!!.plusSeconds(300) > now) return null
        if (last?.state == VerificationStepState.RUNNING)
            finish(last, VerificationStepState.ERROR, "{}", "{\"codes\":[\"PROJECTION_INTERRUPTED\"]}", now)
        return start(itemId, 8, "{\"persistedRevision\":$revision,\"providerCalls\":0}",
            "{\"operation\":\"PROJECTION_ONLY\"}", item.generation, now)
    }

    @Transactional
    fun finishProjection(itemId: Long, revision: Long, stepId: Long, outputJson: String, error: String?, now: Instant): Boolean {
        val item = lock(itemId) ?: return false
        if (item.state != "COMPLETED" || item.admissionRevision != revision) return false
        val step = read(itemId).steps.lastOrNull { it.stepNo == 8 } ?: return false
        if (step.id != stepId) return false
        return finish(step, if (error == null) VerificationStepState.SUCCESS else VerificationStepState.ERROR,
            outputJson, mapper.writeValueAsString(mapOf("codes" to listOfNotNull(error))), now)
    }

    private fun initialize(itemId: Long, now: Instant) {
        for (step in 1..8) jdbc.update("""INSERT IGNORE INTO discovery_verification_step
            (item_id,step_no,attempt_no,state,created_at) VALUES (?,?,1,'NOT_STARTED',?)""", itemId, step, now.verificationUtc())
    }

    private fun start(itemId: Long, stepNo: Int, input: String, versions: String, generation: Long, now: Instant): VerificationStep {
        var previous = read(itemId).steps.last { it.stepNo == stepNo }
        if (previous.state == VerificationStepState.RUNNING) {
            check(mapper.readTree(previous.versionJson).path("generation").asLong(-1) != generation) { "Step attempt already running" }
            finish(previous, VerificationStepState.ERROR, "{}", "{\"codes\":[\"LEASE_REPLACED\"]}", now)
            previous = read(itemId).steps.last { it.stepNo == stepNo }
        }
        val ref = "item:$itemId/evidence_json;attempts:item:$itemId"
        val bounded = boundedJson(input, ref)
        val version = mapper.createObjectNode().put("generation", generation)
        version.set<JsonNode>("versions", mapper.readTree(boundedJson(versions, ref)))
        if (previous.state == VerificationStepState.NOT_STARTED) {
            jdbc.update("""UPDATE discovery_verification_step SET state='RUNNING',input_hash=?,input_json=?,version_json=?,started_at=?
                WHERE id=? AND state='NOT_STARTED'""", DiscoveryVerificationRepository.replayHash(input), bounded,
                version.toString(), now.verificationUtc(), previous.id)
        } else jdbc.update("""INSERT INTO discovery_verification_step
            (item_id,step_no,attempt_no,state,input_hash,input_json,version_json,started_at,created_at)
            VALUES (?,?,?,'RUNNING',?,?,?,?,?)""", itemId, stepNo, previous.attemptNo + 1,
            DiscoveryVerificationRepository.replayHash(input), bounded, version.toString(), now.verificationUtc(), now.verificationUtc())
        return read(itemId).steps.last { it.stepNo == stepNo }
    }

    private fun finish(step: VerificationStep, state: VerificationStepState, output: String?, reasons: String?, now: Instant): Boolean {
        require(state.terminal)
        if (step.state != VerificationStepState.RUNNING || now < step.startedAt!!) return false
        val ref = "item:${step.itemId}/evidence_json;attempts:item:${step.itemId}"
        return jdbc.update("""UPDATE discovery_verification_step SET state=?,output_json=?,reason_json=?,finished_at=?
            WHERE id=? AND item_id=? AND state='RUNNING'""", state.name, output?.let { boundedJson(it, ref) },
            reasons?.let { boundedJson(it, ref) }, now.verificationUtc(), step.id, step.itemId) == 1
    }

    /** Short excerpts and references only. Truncation never masquerades as a complete evidence list. */
    fun boundedJson(json: String, fullRef: String): String {
        val root = mapper.readTree(json)
        var truncated = false
        fun clean(node: JsonNode): JsonNode = when {
            node.isObject -> mapper.createObjectNode().also { target ->
                node.fields().forEachRemaining { (key, value) ->
                    if (key.lowercase().replace("_", "") in setOf("apikey", "authorization", "headers", "body", "rawbody", "password")) {
                        truncated = true; target.put(key, "[REDACTED]")
                    } else target.set<JsonNode>(key, clean(value))
                }
            }
            node.isArray -> mapper.createArrayNode().also { target ->
                if (node.size() > 100) truncated = true
                node.take(100).forEach { target.add(clean(it)) }
            }
            node.isTextual && node.textValue().length > 2048 -> {
                truncated = true; mapper.nodeFactory.textNode(node.textValue().take(2048))
            }
            else -> node
        }
        val value = clean(root)
        fun envelope(include: Boolean) = mapper.createObjectNode().put("truncated", truncated || !include)
            .put("originalBytes", json.toByteArray(Charsets.UTF_8).size).put("count", if (root.isContainerNode) root.size() else 1)
            .put("fullRef", fullRef).also { if (include) it.set<JsonNode>("value", value) }.toString()
        val result = envelope(true)
        return if (result.toByteArray(Charsets.UTF_8).size <= 65536) result else envelope(false)
    }

    private data class Owner(val state: String, val token: String?, val generation: Long, val until: Instant?,
        val activatedAt: Instant?, val admissionRevision: Long?, val projection: String, val allowed: Boolean)
    private fun lock(itemId: Long): Owner? {
        val owner = jdbc.query("SELECT owner_execution_id FROM discovery_verification_item WHERE id=?",
            RowMapper { rs, _ -> rs.getLong(1) }, itemId).singleOrNull() ?: return null
        val controls = jdbc.query("SELECT recheck_json,input_closed FROM discovery_verification_run WHERE execution_id=? FOR UPDATE",
            RowMapper { rs, _ -> rs.getString(1) to rs.getBoolean(2) }, owner).single()
        val allowed = controls.first == null || controls.second && mapper.readTree(controls.first).path("control").path("phase").asText() == "RUNNING"
        return jdbc.query("SELECT * FROM discovery_verification_item WHERE id=? FOR UPDATE", RowMapper { rs, _ ->
            Owner(rs.getString("state"), rs.getString("lease_token"), rs.getLong("generation"),
                rs.getTimestamp("lease_until")?.toLocalDateTime()?.verificationInstant(), rs.getTimestamp("activated_at")?.toLocalDateTime()?.verificationInstant(),
                rs.getLong("admission_revision").let { if (rs.wasNull()) null else it }, rs.getString("projection_state"), allowed)
        }, itemId).single()
    }
    private fun owns(item: Owner, token: String, generation: Long, now: Instant) = item.allowed &&
        item.state == "RUNNING" && item.token == token && item.generation == generation && item.until?.let { it > now } == true

    private val rowMapper = RowMapper { rs: java.sql.ResultSet, _: Int -> VerificationStep(
        rs.getLong("id"), rs.getLong("item_id"), rs.getInt("step_no"), rs.getInt("attempt_no"),
        VerificationStepState.valueOf(rs.getString("state")), rs.getString("input_hash"), rs.getString("input_json"),
        rs.getString("output_json"), rs.getString("reason_json"), rs.getString("version_json"),
        rs.getTimestamp("started_at")?.toLocalDateTime()?.verificationInstant(), rs.getTimestamp("finished_at")?.toLocalDateTime()?.verificationInstant(),
        rs.getTimestamp("created_at").toLocalDateTime().verificationInstant()) }
}
