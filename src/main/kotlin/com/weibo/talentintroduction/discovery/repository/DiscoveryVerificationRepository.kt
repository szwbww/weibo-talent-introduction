package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.discovery.domain.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Database-only seam. Call public transactional methods through the Spring bean.
 * Lock order is run -> admission (caller, when needed) -> item -> attempt.
 * No HTTP/ES work may be performed while these locks are held.
 */
@Repository
class DiscoveryVerificationRepository(private val jdbc: JdbcTemplate) {
    @Transactional
    fun createRun(executionId: Long, mode: VerificationMode, apolloRequested: Boolean, settingsJson: String, now: Instant): VerificationRun {
        require(executionId > 0 && settingsJson.isNotBlank())
        jdbc.update("INSERT IGNORE INTO discovery_verification_run (execution_id,mode,policy_version,apollo_requested,settings_json,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
            executionId, mode.name, mode.policyVersion, apolloRequested, settingsJson, now.verificationUtc(), now.verificationUtc())
        val run = lockRun(executionId)
        require(run.mode == mode && run.apolloRequested == apolloRequested && run.settingsJson == settingsJson) { "Run snapshot is immutable" }
        return run
    }

    @Transactional
    fun reserveItem(input: VerificationReservation, now: Instant): VerificationItem {
        // Read duplicate identity without locks first: never lock two runs in one transaction.
        val existing = identity(input)
        if (existing != null) return checkedIdentity(existing, input)
        val run = lockRun(input.ownerExecutionId)
        require(run.policyVersion == input.policyVersion && !run.inputClosed)
        val inserted = jdbc.update("""INSERT IGNORE INTO discovery_verification_item
            (owner_execution_id,expert_doc_id,identity_hash,policy_version,source_name,source_item_key,batch_number,snapshot_json,fact_fingerprint,created_at,updated_at)
            VALUES (?,?,?,?,?,?,0,?,?,?,?)""", input.ownerExecutionId, input.expertDocId, input.identityHash,
            input.policyVersion, input.sourceName, input.sourceItemKey, input.snapshotJson, input.factFingerprint,
            now.verificationUtc(), now.verificationUtc())
        val item = jdbc.query("SELECT * FROM discovery_verification_item WHERE expert_doc_id=? AND identity_hash=? AND policy_version=? FOR UPDATE", itemMapper,
            input.expertDocId, input.identityHash, input.policyVersion).single()
        checkedIdentity(item, input)
        if (inserted == 1) bump(input.ownerExecutionId, now)
        return item
    }

    @Transactional
    fun activateItem(itemId: Long, batchNumber: Int, now: Instant): VerificationItem {
        require(batchNumber > 0)
        val item = lockItem(itemId)
        if (item.state == VerificationState.RESERVED) {
            jdbc.update("UPDATE discovery_verification_item SET state='QUEUED',batch_number=?,activated_at=?,updated_at=? WHERE id=? AND state='RESERVED'",
                batchNumber, now.verificationUtc(), now.verificationUtc(), itemId)
            bump(item.ownerExecutionId, now)
        }
        return findItem(itemId)!!
    }

    @Transactional
    fun closeInput(executionId: Long, now: Instant): Boolean {
        val run = lockRun(executionId)
        if (run.inputClosed) return false
        jdbc.update("UPDATE discovery_verification_run SET input_closed=1,revision=revision+1,updated_at=? WHERE execution_id=?", now.verificationUtc(), executionId)
        return true
    }

    fun findRun(executionId: Long): VerificationRun? {
        require(executionId > 0)
        return jdbc.query("SELECT * FROM discovery_verification_run WHERE execution_id=?", runMapper, executionId).singleOrNull()
    }
    fun findItem(itemId: Long): VerificationItem? {
        require(itemId > 0)
        return jdbc.query("SELECT * FROM discovery_verification_item WHERE id=?", itemMapper, itemId).singleOrNull()
    }
    fun findManagedContexts(docIds: Collection<String>): Map<String, VerificationContext> {
        require(docIds.size <= 500)
        docIds.forEach { verificationText(it, 128) }
        if (docIds.isEmpty()) return emptyMap()
        val result = docIds.associateWith { VerificationContext.UNMANAGED }.toMutableMap()
        jdbc.query("SELECT DISTINCT expert_doc_id,policy_version FROM discovery_verification_item WHERE expert_doc_id IN (${docIds.joinToString(",") { "?" }})",
            RowMapper { rs, _ -> rs.getString(1) to rs.getString(2) }, *docIds.toTypedArray()).forEach { (doc, policy) ->
            val context = when (policy) {
                VerificationMode.ENFORCE.policyVersion -> VerificationContext.MANAGED_ENFORCE
                VerificationMode.AUDIT.policyVersion -> VerificationContext.MANAGED_AUDIT
                else -> throw IllegalStateException("Unknown verification policy")
            }
            if (result[doc] != VerificationContext.MANAGED_ENFORCE) result[doc] = context
        }
        // Database failures propagate, never become UNMANAGED.
        return result
    }

    /** One evidence read for a bounded batch of real ES document IDs, including old identities. */
    fun findItemsByDocIds(docIds: Collection<String>): List<VerificationItem> {
        require(docIds.size <= 500)
        docIds.forEach { verificationText(it, 128) }
        if (docIds.isEmpty()) return emptyList()
        return jdbc.query(
            "SELECT * FROM discovery_verification_item WHERE expert_doc_id IN (${docIds.joinToString(",") { "?" }}) ORDER BY id",
            itemMapper, *docIds.toTypedArray()
        )
    }

    @Transactional
    fun claimDue(now: Instant, leaseSeconds: Long): VerificationItem? {
        require(leaseSeconds > 0)
        val candidates = jdbc.query("""SELECT * FROM discovery_verification_item WHERE
            state='QUEUED' OR (state='RETRY_WAIT' AND next_attempt_at<=?) OR (state='RUNNING' AND lease_until<=?) ORDER BY id LIMIT 100""",
            itemMapper, now.verificationUtc(), now.verificationUtc())
        // One run per transaction, even when all of its candidates lost a race.
        val owner = candidates.firstOrNull()?.ownerExecutionId ?: return null
        lockRun(owner)
        for (candidate in candidates.filter { it.ownerExecutionId == owner }) {
            val item = lockedItem(candidate.id)
            if (!(item.state == VerificationState.QUEUED ||
                    item.state == VerificationState.RETRY_WAIT && item.nextAttemptAt?.let { it <= now } == true ||
                    item.state == VerificationState.RUNNING && item.leaseUntil?.let { it <= now } == true)) continue
            jdbc.update("UPDATE discovery_verification_item SET state='RUNNING',lease_token=?,generation=generation+1,lease_until=?,next_attempt_at=NULL,updated_at=? WHERE id=?",
                UUID.randomUUID().toString(), now.plusSeconds(leaseSeconds).verificationUtc(), now.verificationUtc(), item.id)
            bump(owner, now)
            return findItem(item.id)
        }
        return null
    }

    @Transactional
    fun renewLease(itemId: Long, token: String, generation: Long, now: Instant, leaseSeconds: Long): Boolean {
        require(leaseSeconds > 0)
        val item = lockItem(itemId)
        if (!owns(item, token, generation, now)) return false
        jdbc.update("UPDATE discovery_verification_item SET lease_until=?,updated_at=? WHERE id=? AND lease_token=? AND generation=? AND state='RUNNING'",
            now.plusSeconds(leaseSeconds).verificationUtc(), now.verificationUtc(), itemId, token, generation)
        bump(item.ownerExecutionId, now)
        return true
    }

    @Transactional
    fun reserveAttemptWithBudget(itemId: Long, token: String, generation: Long, request: VerificationAttemptRequest,
                                 requestsPerMinute: Int, requestsPerDay: Int, maxRequestsPerItem: Int, now: Instant): VerificationAttemptReservation {
        require(requestsPerMinute > 0 && requestsPerDay > 0 && maxRequestsPerItem > 0)
        val item = lockItem(itemId)
        if (!owns(item, token, generation, now)) return VerificationAttemptReservation(deferred = VerificationBudgetReason.LEASE_LOST)
        // Lock is retained through COMMIT, not merely through INSERT. Same bound JDBC connection.
        if (request.provider == VerificationProvider.APOLLO) {
            val acquired = jdbc.execute(ConnectionCallback<Boolean> { connection ->
                connection.prepareStatement("SELECT GET_LOCK('discovery_verification_apollo_budget',1)").use { statement ->
                    statement.executeQuery().use { rs -> rs.next(); rs.getInt(1) == 1 }
                }.also { acquired ->
                    if (acquired) TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                        override fun afterCompletion(status: Int) {
                            connection.prepareStatement("SELECT RELEASE_LOCK('discovery_verification_apollo_budget')").use { it.execute() }
                        }
                    })
                }
            }) == true
            if (!acquired) return VerificationAttemptReservation(deferred = VerificationBudgetReason.LOCK_BUSY)
        }
        val duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_attempt WHERE request_key=? FOR UPDATE", Long::class.java, request.requestKey)!!
        if (duplicate > 0) return VerificationAttemptReservation(deferred = VerificationBudgetReason.DUPLICATE_REQUEST)
        if (item.requestCount >= maxRequestsPerItem) return VerificationAttemptReservation(deferred = VerificationBudgetReason.ITEM_LIMIT)
        if (request.provider == VerificationProvider.APOLLO) {
            // Current reads are required even inside an outer REPEATABLE_READ transaction:
            // acquiring a named lock does not refresh an earlier MVCC snapshot.
            val start = LocalDate.ofInstant(now, ZoneOffset.UTC).atStartOfDay()
            val day = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_attempt WHERE provider='APOLLO' AND requested_at>=? AND requested_at<? FOR UPDATE",
                Long::class.java, start, start.plusDays(1))!!
            if (day >= requestsPerDay) return VerificationAttemptReservation(deferred = VerificationBudgetReason.DAY_LIMIT)
            val minute = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_attempt WHERE provider='APOLLO' AND requested_at>? AND requested_at<=? FOR UPDATE",
                Long::class.java, now.minusSeconds(60).verificationUtc(), now.verificationUtc())!!
            if (minute >= requestsPerMinute) return VerificationAttemptReservation(deferred = VerificationBudgetReason.MINUTE_LIMIT)
        }
        jdbc.update("""INSERT INTO discovery_verification_attempt (item_id,request_key,provider,query_kind,page_number,attempt_number,requested_at,result_code,request_json)
            VALUES (?,?,?,?,?,?,?,'DISPATCH_RESERVED',?)""", itemId, request.requestKey, request.provider.name, request.queryKind.name,
            request.pageNumber, request.attemptNumber, now.verificationUtc(), request.requestJson)
        val id = jdbc.queryForObject("SELECT id FROM discovery_verification_attempt WHERE request_key=?", Long::class.java, request.requestKey)!!
        jdbc.update("UPDATE discovery_verification_item SET request_count=request_count+1,updated_at=? WHERE id=?", now.verificationUtc(), itemId)
        bump(item.ownerExecutionId, now)
        return VerificationAttemptReservation(attemptId = id)
    }

    @Transactional
    fun completeAttempt(itemId: Long, token: String, generation: Long, attemptId: Long, httpStatus: Int?, resultCode: String,
                        responseJson: String?, responseSha256: String?, now: Instant): Boolean {
        verificationText(resultCode, 64)
        require(httpStatus == null || httpStatus in 100..599)
        responseSha256?.let { verificationHash(it) }
        val item = lockItem(itemId)
        if (!owns(item, token, generation, now)) return false
        val changed = jdbc.update("""UPDATE discovery_verification_attempt SET completed_at=?,http_status=?,result_code=?,response_json=?,response_sha256=?
            WHERE id=? AND item_id=? AND completed_at IS NULL""", now.verificationUtc(), httpStatus, resultCode, responseJson, responseSha256, attemptId, itemId)
        if (changed == 1) bump(item.ownerExecutionId, now)
        return changed == 1
    }

    @Transactional
    fun saveEvidenceAndOutcome(itemId: Long, token: String, generation: Long, result: VerificationResult, now: Instant): Boolean {
        val item = lockItem(itemId)
        if (!owns(item, token, generation, now)) return false
        if (result.admissionRevision != null) require(item.policyVersion == VerificationMode.ENFORCE.policyVersion)
        val changed = jdbc.update("""UPDATE discovery_verification_item SET state=?,outcome=?,qualification=?,route=?,evidence_json=?,reasons_json=?,
            apollo_entered_at=COALESCE(apollo_entered_at,?),search_completed_at=COALESCE(search_completed_at,?),candidate_found_at=COALESCE(candidate_found_at,?),
            person_matched_at=COALESCE(person_matched_at,?),apollo_person_id=?,admission_revision=?,projection_state=?,completed_at=?,updated_at=?,lease_token=NULL,lease_until=NULL
            WHERE id=? AND lease_token=? AND generation=? AND state='RUNNING'""", result.state.name, result.outcome?.name, result.qualification.name,
            result.route.name, result.evidenceJson, result.reasonsJson, result.apolloEnteredAt?.verificationUtc(), result.searchCompletedAt?.verificationUtc(),
            result.candidateFoundAt?.verificationUtc(), result.personMatchedAt?.verificationUtc(), result.apolloPersonId, result.admissionRevision,
            result.projectionState.name, now.verificationUtc(), now.verificationUtc(), itemId, token, generation)
        if (changed == 1) bump(item.ownerExecutionId, now)
        return changed == 1
    }

    @Transactional
    fun markProjection(itemId: Long, admissionRevision: Long, projection: VerificationProjection, error: String?, now: Instant): Boolean {
        require(admissionRevision > 0)
        require(error == null || error.length <= 128)
        require(projection != VerificationProjection.NOT_REQUIRED)
        val item = lockItem(itemId)
        if (item.admissionRevision != admissionRevision || item.state != VerificationState.COMPLETED) return false
        if (item.projectionState in setOf(VerificationProjection.PROJECTED, VerificationProjection.ALREADY_PRESENT, VerificationProjection.APPLICATION_PRESENT)) return false
        jdbc.update("UPDATE discovery_verification_item SET projection_state=?,projection_error=?,updated_at=? WHERE id=?", projection.name, error, now.verificationUtc(), itemId)
        bump(item.ownerExecutionId, now)
        return true
    }

    @Transactional
    fun reschedule(itemId: Long, token: String, generation: Long, nextAttemptAt: Instant, transientAttempts: Int,
                   evidenceJson: String, reasonsJson: String, now: Instant): Boolean {
        require(nextAttemptAt >= now && transientAttempts >= 0)
        val item = lockItem(itemId)
        if (!owns(item, token, generation, now)) return false
        require(transientAttempts >= item.transientAttempts)
        jdbc.update("""UPDATE discovery_verification_item SET state='RETRY_WAIT',next_attempt_at=?,transient_attempts=?,evidence_json=?,reasons_json=?,
            lease_token=NULL,lease_until=NULL,updated_at=? WHERE id=? AND lease_token=? AND generation=? AND state='RUNNING'""",
            nextAttemptAt.verificationUtc(), transientAttempts, evidenceJson, reasonsJson, now.verificationUtc(), itemId, token, generation)
        bump(item.ownerExecutionId, now)
        return true
    }

    fun listReserved(limit: Int): List<VerificationItem> {
        require(limit in 1..500)
        return jdbc.query("SELECT * FROM discovery_verification_item WHERE state='RESERVED' ORDER BY id LIMIT ?", itemMapper, limit)
    }

    /** Recovery may terminate an unactivated registration only after its owner's input is closed. */
    @Transactional
    fun failReserved(itemId: Long, reasonsJson: String, now: Instant): Boolean {
        val item = lockItem(itemId)
        if (item.state != VerificationState.RESERVED || !findRun(item.ownerExecutionId)!!.inputClosed) return false
        jdbc.update("UPDATE discovery_verification_item SET state='ERROR',reasons_json=?,completed_at=?,updated_at=? WHERE id=?", reasonsJson, now.verificationUtc(), now.verificationUtc(), itemId)
        bump(item.ownerExecutionId, now)
        return true
    }

    private fun identity(input: VerificationReservation) = jdbc.query("SELECT * FROM discovery_verification_item WHERE expert_doc_id=? AND identity_hash=? AND policy_version=?",
        itemMapper, input.expertDocId, input.identityHash, input.policyVersion).singleOrNull()
    private fun checkedIdentity(item: VerificationItem, input: VerificationReservation): VerificationItem {
        check(item.expertDocId == input.expertDocId && item.identityHash == input.identityHash && item.policyVersion == input.policyVersion) { "Identity collision" }
        return item
    }
    private fun lockRun(id: Long): VerificationRun {
        require(id > 0)
        return jdbc.query("SELECT * FROM discovery_verification_run WHERE execution_id=? FOR UPDATE", runMapper, id).singleOrNull()
            ?: throw IllegalStateException("Verification run not found")
    }
    private fun lockItem(id: Long): VerificationItem {
        val item = findItem(id) ?: throw IllegalArgumentException("Verification item not found")
        lockRun(item.ownerExecutionId)
        return lockedItem(id)
    }
    private fun lockedItem(id: Long) = jdbc.query("SELECT * FROM discovery_verification_item WHERE id=? FOR UPDATE", itemMapper, id).single()
    private fun owns(item: VerificationItem, token: String, generation: Long, now: Instant): Boolean =
        item.state == VerificationState.RUNNING && item.leaseToken == token && item.generation == generation && item.leaseUntil?.let { it > now } == true
    private fun bump(owner: Long, now: Instant) {
        jdbc.update("UPDATE discovery_verification_run SET revision=revision+1,updated_at=? WHERE execution_id=?", now.verificationUtc(), owner)
    }

    companion object {
        private fun ResultSet.time(name: String): Instant? = getTimestamp(name)?.toLocalDateTime()?.verificationInstant()
        private fun ResultSet.nullLong(name: String): Long? = getLong(name).let { if (wasNull()) null else it }
        private val runMapper = RowMapper { rs: ResultSet, _: Int -> VerificationRun(rs.getLong("execution_id"), VerificationMode.valueOf(rs.getString("mode")),
            rs.getString("policy_version"), rs.getBoolean("apollo_requested"), rs.getString("settings_json"), rs.getBoolean("input_closed"), rs.getLong("revision"), rs.time("created_at")!!, rs.time("updated_at")!!) }
        private val itemMapper = RowMapper { rs: ResultSet, _: Int -> VerificationItem(
            rs.getLong("id"), rs.getLong("owner_execution_id"), rs.getString("expert_doc_id"), rs.getString("identity_hash"), rs.getString("policy_version"),
            rs.getString("source_name"), rs.getString("source_item_key"), rs.getInt("batch_number"), rs.getString("snapshot_json"), rs.getString("fact_fingerprint"),
            VerificationState.valueOf(rs.getString("state")), rs.getString("outcome")?.let(VerificationOutcome::valueOf), VerificationQualification.valueOf(rs.getString("qualification")),
            VerificationRoute.valueOf(rs.getString("route")), rs.time("apollo_entered_at"), rs.time("search_completed_at"), rs.time("candidate_found_at"), rs.time("person_matched_at"),
            rs.getString("apollo_person_id"), rs.getString("evidence_json"), rs.getString("reasons_json"), rs.getInt("request_count"), rs.getInt("transient_attempts"), rs.getLong("generation"),
            rs.getString("lease_token"), rs.time("lease_until"), rs.time("next_attempt_at"), rs.nullLong("admission_revision"), VerificationProjection.valueOf(rs.getString("projection_state")),
            rs.getString("projection_error"), rs.time("activated_at"), rs.time("completed_at"), rs.time("created_at")!!, rs.time("updated_at")!!) }
    }
}
