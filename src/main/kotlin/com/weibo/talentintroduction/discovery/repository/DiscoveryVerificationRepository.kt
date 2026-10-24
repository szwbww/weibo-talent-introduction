package com.weibo.talentintroduction.discovery.repository

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.security.MessageDigest
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.service.DiscoveryVerificationRules
import com.weibo.talentintroduction.discovery.service.VerificationSnapshot
import com.weibo.talentintroduction.discovery.service.VerificationWorkerEvidence
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/** Database-only seam. Call public transactional methods through the Spring bean.
 * Lock order is run -> admission (caller, when needed) -> item -> attempt.
 * No HTTP/ES work may be performed while these locks are held.
 */
@Repository
class DiscoveryVerificationRepository(private val jdbc: JdbcTemplate) {
    data class ReplayAdmission(val identityHash: String, val decision: String, val revision: Long, val policyVersion: String?)
    data class ReplayExpected(val item: VerificationItem, val admission: ReplayAdmission?, val evidenceJson: String,
        val snapshotSha256: String = replayHash(item.snapshotJson),
        val evidenceSha256: String = replayHash(item.evidenceJson),
        val reasonsSha256: String = replayHash(item.reasonsJson))
    data class ReplayReceipt(val itemId: Long, val status: String)


    fun replayAdmission(docId: String, locked: Boolean = false): ReplayAdmission? =
        jdbc.query("""SELECT identity_hash,decision,revision,policy_version FROM expert_discovery_admission
            WHERE expert_doc_id=? ${if (locked) "FOR UPDATE" else ""}""",
            RowMapper { rs, _ -> ReplayAdmission(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4)) }, docId).singleOrNull()

    /** Short database-only CAS. Explicit outer transactions are mandatory for non-Spring CLI callers. */
    @Transactional
    fun requeuePendingForEvidenceReplay(executionId: Long, expectedItems: List<ReplayExpected>, now: Instant): List<ReplayReceipt> {
        require(TransactionSynchronizationManager.isActualTransactionActive()) { "Replay requires a transaction" }
        require(expectedItems.size in 1..100 && expectedItems.map { it.item.id }.distinct().size == expectedItems.size)
        require(expectedItems.all { it.item.ownerExecutionId == executionId && it.item.reviewEpoch == 0L })
        val run = lockRun(executionId)
        require(run.recheckJson == null) { "Legacy replay cannot modify rechecks" }
        val admissions = expectedItems.map { it.item.expertDocId }.distinct().sorted().associateWith { replayAdmission(it, true) }
        val current = expectedItems.sortedBy { it.item.id }.associate { it.item.id to lockedItem(it.item.id) }
        fun stale(expected: ReplayExpected): Boolean {
            val item = current[expected.item.id]
            return item != expected.item || replayHash(item?.snapshotJson) != expected.snapshotSha256 ||
                replayHash(item?.evidenceJson) != expected.evidenceSha256 ||
                replayHash(item?.reasonsJson) != expected.reasonsSha256 || admissions[expected.item.expertDocId] != expected.admission
        }
        // A repeated manifest must not be rejected merely because its first application queued work.
        if (expectedItems.all(::stale)) return expectedItems.map { ReplayReceipt(it.item.id, "STALE_OR_ALREADY_APPLIED") }
        val finished = jdbc.queryForObject("SELECT COUNT(*) FROM task_execution WHERE id=? AND finished_at IS NOT NULL",
            Long::class.java, executionId) == 1L
        val active = jdbc.queryForObject("""SELECT COUNT(*) FROM discovery_verification_item WHERE owner_execution_id=?
            AND state IN ('RESERVED','QUEUED','RUNNING','RETRY_WAIT')""", Long::class.java, executionId)!!
        if (!run.inputClosed || !finished || active != 0L)
            return expectedItems.map { ReplayReceipt(it.item.id, "RUN_NOT_SETTLED") }
        val mapper = replayMapper
        val receipts = expectedItems.map { expected ->
            val item = current[expected.item.id]
            val admission = admissions[expected.item.expertDocId]
            val status = when {
                stale(expected) -> "STALE_OR_ALREADY_APPLIED"
                item == null || item.ownerExecutionId != executionId -> "WRONG_RUN"
                admission != null && (admission.decision != "NEEDS_REVIEW" || admission.identityHash != item.identityHash) -> "PROTECTED_ADMISSION"
                item.state != VerificationState.COMPLETED || item.outcome != VerificationOutcome.PENDING ||
                    item.activatedAt == null || item.admissionRevision != null ||
                    item.projectionState != VerificationProjection.NOT_REQUIRED || item.leaseToken != null ||
                    item.leaseUntil != null || item.personMatchedAt != null || item.apolloPersonId != null -> "INELIGIBLE"
                mapper.readTree(item.reasonsJson ?: "[]").any {
                    it.asText() in setOf("IDENTITY_CHANGED", "FACT_CHANGED", "SOURCE_MISSING", "PROTECTED", "APPLICATION_PRESENT") ||
                        it.asText().startsWith("PROTECTED_") || it.asText().endsWith("_PROTECTED")
                } -> "PROTECTED_REASON"
                else -> {
                    require(mapper.readTree(expected.evidenceJson) == mapper.readTree(replayEvidence(item.evidenceJson))) {
                        "Only progress.stage may change"
                    }
                    "QUEUED"
                }
            }
            ReplayReceipt(expected.item.id, status)
        }
        receipts.filter { it.status == "QUEUED" }.forEach { receipt ->
            val expected = expectedItems.single { it.item.id == receipt.itemId }
            check(jdbc.update("""UPDATE discovery_verification_item SET state='QUEUED',outcome=NULL,
                qualification='NOT_EVALUATED',route='NONE',completed_at=NULL,next_attempt_at=NULL,lease_token=NULL,
                lease_until=NULL,evidence_json=?,reasons_json='[]',generation=generation+1,updated_at=?
                WHERE id=? AND generation=?""", expected.evidenceJson, now.verificationUtc(), receipt.itemId, expected.item.generation) == 1)
        }
        if (receipts.any { it.status == "QUEUED" }) bump(executionId, now)
        return receipts
    }

    /** Historical ERROR recovery is queue-only; unlike evidence replay it never changes the saved stage. */
    @Transactional
    fun requeueErrorsForCreditRecovery(executionId: Long, expectedItems: List<ReplayExpected>, now: Instant): List<ReplayReceipt> {
        require(TransactionSynchronizationManager.isActualTransactionActive()) { "Recovery requires a transaction" }
        require(expectedItems.size in 1..100 && expectedItems.map { it.item.id }.distinct().size == expectedItems.size)
        require(expectedItems.all { it.item.ownerExecutionId == executionId && it.item.reviewEpoch == 0L })
        val run = lockRun(executionId)
        require(run.recheckJson == null) { "Legacy recovery cannot modify rechecks" }
        val admissions = expectedItems.map { it.item.expertDocId }.distinct().sorted().associateWith { replayAdmission(it, true) }
        val current = expectedItems.sortedBy { it.item.id }.associate { expected ->
            expected.item.id to jdbc.query("SELECT * FROM discovery_verification_item WHERE id=? FOR UPDATE",
                itemMapper, expected.item.id).singleOrNull()
        }
        fun stale(expected: ReplayExpected): Boolean {
            val item = current[expected.item.id]
            return item != expected.item || replayHash(item?.snapshotJson) != expected.snapshotSha256 ||
                replayHash(item?.evidenceJson) != expected.evidenceSha256 ||
                replayHash(item?.reasonsJson) != expected.reasonsSha256 || admissions[expected.item.expertDocId] != expected.admission
        }
        if (expectedItems.all(::stale)) return expectedItems.map { ReplayReceipt(it.item.id, "STALE_OR_ALREADY_APPLIED") }
        val finished = jdbc.queryForObject("SELECT COUNT(*) FROM task_execution WHERE id=? AND finished_at IS NOT NULL",
            Long::class.java, executionId) == 1L
        val active = jdbc.queryForObject("""SELECT COUNT(*) FROM discovery_verification_item WHERE owner_execution_id=?
            AND state IN ('RESERVED','QUEUED','RUNNING','RETRY_WAIT')""", Long::class.java, executionId)!!
        if (!run.inputClosed || !finished || active != 0L)
            return expectedItems.map { ReplayReceipt(it.item.id, if (stale(it)) "STALE_OR_ALREADY_APPLIED" else "RUN_NOT_SETTLED") }
        val receipts = expectedItems.map { expected ->
            if (stale(expected)) return@map ReplayReceipt(expected.item.id, "STALE_OR_ALREADY_APPLIED")
            val item = requireNotNull(current[expected.item.id])
            val admission = admissions[item.expertDocId]
            val reasons = runCatching { replayMapper.readTree(item.reasonsJson ?: "[]") }.getOrNull()
            val status = when {
                admission != null && (admission.decision != "NEEDS_REVIEW" || admission.identityHash != item.identityHash) -> "PROTECTED_ADMISSION"
                reasons?.isArray != true || reasons.any { !it.isTextual } -> "INELIGIBLE"
                reasons.any {
                    it.asText() in setOf("IDENTITY_CHANGED", "FACT_CHANGED", "SOURCE_MISSING", "PROTECTED", "APPLICATION_PRESENT") ||
                        it.asText().startsWith("PROTECTED_") || it.asText().endsWith("_PROTECTED")
                } -> "PROTECTED_REASON"
                item.state != VerificationState.ERROR || item.activatedAt == null || item.admissionRevision != null ||
                    item.projectionState != VerificationProjection.NOT_REQUIRED || item.leaseToken != null || item.leaseUntil != null ||
                    item.personMatchedAt != null || item.apolloPersonId != null || expected.evidenceJson != item.evidenceJson -> "INELIGIBLE"
                !recoverableDetailCursor(item) -> "INELIGIBLE"
                else -> "QUEUED"
            }
            ReplayReceipt(item.id, status)
        }
        receipts.filter { it.status == "QUEUED" }.forEach { receipt ->
            val item = requireNotNull(current[receipt.itemId])
            check(jdbc.update("""UPDATE discovery_verification_item SET state='QUEUED',outcome=NULL,
                qualification='NOT_EVALUATED',route='NONE',completed_at=NULL,next_attempt_at=NULL,lease_token=NULL,
                lease_until=NULL,reasons_json='[]',generation=generation+1,updated_at=?
                WHERE id=? AND generation=? AND state='ERROR'""", now.verificationUtc(), item.id, item.generation) == 1)
        }
        if (receipts.any { it.status == "QUEUED" }) bump(executionId, now)
        return receipts
    }

    private fun recoverableDetailCursor(item: VerificationItem): Boolean = runCatching {
        val last = findAttempts(item.id).lastOrNull() ?: return false
        if (last.provider != VerificationProvider.APOLLO || last.queryKind != VerificationQueryKind.DETAIL ||
            last.httpStatus != 422 || last.completedAt == null ||
            last.resultCode !in setOf("REQUEST_INVALID", "APOLLO_HTTP_422_UNCLASSIFIED", "APOLLO_CREDITS_EXHAUSTED")) return false
        val tree = replayMapper.readTree(requireNotNull(item.evidenceJson))
        if (!tree.isObject || !tree.path("progress").isObject || !tree.path("search").isObject || !tree.path("queries").isArray) return false
        val payload = replayMapper.treeToValue(tree, VerificationWorkerEvidence::class.java)
        val queries = payload.queries ?: return false
        val search = payload.search ?: return false
        if (payload.progress.stage != "APOLLO_SEARCH" || queries.isEmpty() || payload.progress.queryIndex != queries.size ||
            search.matchingVersion != 2 || search.detailsComplete) return false
        val snapshot = replayMapper.readValue(item.snapshotJson, VerificationSnapshot::class.java)
        if (snapshot.identityHash != item.identityHash || snapshot.factFingerprint != item.factFingerprint ||
            payload.evidence.any { it.identityHash != item.identityHash || it.factFingerprint != item.factFingerprint }) return false
        val person = replayMapper.readTree(last.requestJson).path("personId").takeIf { it.isTextual }?.asText() ?: return false
        val rules = DiscoveryVerificationRules()
        val names = rules.allowedNames(snapshot, payload.evidence)
        val candidates = search.candidates.filter {
            rules.nameMatches(it, names, search.queryNames, masked = true, ruleVersion = payload.ruleVersion) ||
                payload.ruleVersion >= 1 && rules.emailNameMatches(snapshot, payload.evidence, it, search.institutionAliasVersion, masked = true)
        }
        val strong = candidates.filter { rules.matchReason(snapshot, payload.evidence, it, null, search.institutionAliasVersion) in
            setOf("APOLLO_CURRENT_INSTITUTION_MATCH", "APOLLO_HISTORICAL_INSTITUTION_MATCH", "APOLLO_PROFESSOR_MATCH") }
        strong.ifEmpty { candidates }.any {
            it.id == person && it.id !in search.detailsCompleted && !(it in strong && (it.fullName != null || it.lastName != null))
        }
    }.getOrDefault(false)

    /** Reporting queries deliberately have no locks, writes, or recovery side effects. */
    data class PromotionDocuments(val managedDocIds: Set<String>, val projectedSources: Map<String, String>)

    /** One read snapshot supplies both the legacy exclusion set and actual new projection events. */
    fun reportingPromotionDocuments(executionId: Long): PromotionDocuments {
        val managed = mutableSetOf<String>()
        val projected = mutableMapOf<String, String>()
        jdbc.query("""SELECT i.expert_doc_id,i.source_name,
            (i.admission_revision IS NOT NULL AND i.projection_state='PROJECTED') AS projected
            FROM discovery_verification_item i FORCE INDEX (idx_dvi_run_batch)
            JOIN discovery_verification_run r ON r.execution_id=i.owner_execution_id
            WHERE i.owner_execution_id=? AND r.mode='ENFORCE' AND i.activated_at IS NOT NULL
            ORDER BY i.id DESC""", RowCallbackHandler { rs ->
            val docId = rs.getString("expert_doc_id")
            managed.add(docId)
            // Descending item id makes the latest qualifying event's source deterministic.
            if (rs.getBoolean("projected")) projected.putIfAbsent(docId, rs.getString("source_name"))
        }, executionId)
        return PromotionDocuments(managed, projected)
    }

    fun reportingCollectionStatuses(ids: List<Long>): Map<Long, String> {
        require(ids.isNotEmpty() && ids.size <= 50 && ids.all { it > 0 })
        return jdbc.query("SELECT id,status FROM task_execution WHERE id IN (${ids.joinToString(",") { "?" }})",
            RowMapper { rs, _ -> rs.getLong("id") to rs.getString("status") }, *ids.toTypedArray()).toMap()
    }

    fun reportingRuns(ids: List<Long>): Map<Long, VerificationRun> {
        require(ids.isNotEmpty() && ids.size <= 50 && ids.all { it > 0 })
        return jdbc.query("SELECT * FROM discovery_verification_run WHERE execution_id IN (${ids.joinToString(",") { "?" }})",
            runMapper, *ids.toTypedArray()).associateBy { it.executionId }
    }
    /** Registered attempts, not successful requests or billed credits. Pending dispatch overlaps its channel. */
    fun reportingAttemptChannels(ids: List<Long>): Map<Long, VerificationAttemptChannels> {
        require(ids.isNotEmpty() && ids.size <= 50 && ids.all { it > 0 })
        return jdbc.query("""SELECT r.execution_id,
            COALESCE(SUM(a.provider='APOLLO' AND a.query_kind IN ('BROAD','NARROW','VARIANT')),0) AS search,
            COALESCE(SUM(a.provider='APOLLO' AND a.query_kind='DETAIL'),0) AS detail,
            COALESCE(SUM(a.id IS NOT NULL AND NOT (a.provider='APOLLO'
                AND a.query_kind IN ('BROAD','NARROW','VARIANT','DETAIL'))),0) AS other,
            COALESCE(SUM(a.result_code='DISPATCH_RESERVED'),0) AS pendingDispatch
            FROM discovery_verification_run r
            LEFT JOIN discovery_verification_item i ON i.owner_execution_id=r.execution_id
            LEFT JOIN discovery_verification_attempt a ON a.item_id=i.id
            WHERE r.execution_id IN (${ids.joinToString(",") { "?" }})
            GROUP BY r.execution_id ORDER BY r.execution_id""",
            RowMapper { rs, _ -> rs.getLong("execution_id") to VerificationAttemptChannels(
                rs.getLong("search"), rs.getLong("detail"), rs.getLong("other"), rs.getLong("pendingDispatch")) },
            *ids.toTypedArray()).toMap()
    }


    fun reportingCounts(filter: VerificationReportFilter, batches: Boolean = false, executionIds: List<Long>? = null): Map<Long, VerificationCounts> {
        require(executionIds == null || executionIds.isNotEmpty() && executionIds.size <= 50 && executionIds.all { it > 0 })
        val (where, args) = if (executionIds == null) reportingWhere(filter) else
            "i.owner_execution_id IN (${executionIds.joinToString(",") { "?" }})" to executionIds
        fun sum(condition: String, alias: String) = "COALESCE(SUM(CASE WHEN $condition THEN 1 ELSE 0 END),0) AS $alias"
        val admitted = "i.activated_at IS NOT NULL AND r.mode='ENFORCE' AND i.admission_revision IS NOT NULL"
        val active = "i.activated_at IS NOT NULL"
        val columns = listOf(
            sum(active, "profiles"),
            sum("$admitted AND i.outcome IN ('DIRECT_CLEAR','APOLLO_CLEAR','APOLLO_UNCLEAR')", "autoAdmitted"),
            sum("$admitted AND i.outcome='DIRECT_CLEAR'", "directAdmitted"),
            sum("$admitted AND i.outcome IN ('APOLLO_CLEAR','APOLLO_UNCLEAR')", "apolloAdmitted"),
            sum("$active AND i.outcome IN ('DIRECT_CLEAR','APOLLO_CLEAR')", "clear"),
            sum("$active AND i.outcome='APOLLO_UNCLEAR'", "unclear"),
            sum("$active AND i.outcome='PENDING'", "pending"),
            sum("$active AND i.state='ERROR'", "errors"),
            sum("$active AND i.state IN ('QUEUED','RUNNING','RETRY_WAIT')", "queued"),
            sum("$active AND i.outcome='PROTECTED'", "protected"),
            sum("$active AND i.projection_state='PENDING'", "projectionPending"),
            sum("$active AND i.projection_state='ERROR'", "projectionErrors"),
            sum("$active AND i.apollo_entered_at IS NOT NULL", "apolloEntered"),
            sum("$active AND i.search_completed_at IS NOT NULL", "apolloSearched"),
            sum("$active AND i.candidate_found_at IS NOT NULL", "apolloCandidates"),
            sum("$active AND i.person_matched_at IS NOT NULL", "apolloMatched"),
            """COALESCE(SUM(CASE WHEN $active THEN
                (SELECT COUNT(*) FROM discovery_verification_attempt a WHERE a.item_id=i.id AND a.provider='APOLLO')
                ELSE 0 END),0) AS apolloHttpAttempts""",
            sum("i.activated_at IS NULL AND i.state='ERROR'", "registrationErrors"),
            sum("i.state='CANCELLED'", "cancelled")
        ).joinToString(",")
        val group = when { batches -> "i.batch_number"; executionIds != null -> "i.owner_execution_id"; else -> "0" }
        val grouping = if (batches || executionIds != null) "GROUP BY $group ORDER BY $group" else ""
        return jdbc.query("""SELECT $group AS batch,$columns FROM discovery_verification_item i
            JOIN discovery_verification_run r ON r.execution_id=i.owner_execution_id WHERE $where
            ${if (batches) "AND i.activated_at IS NOT NULL" else ""} $grouping""",
            RowMapper { rs, _ -> rs.getLong("batch") to VerificationCounts(
                rs.getLong("profiles"), rs.getLong("autoAdmitted"), rs.getLong("directAdmitted"),
                rs.getLong("apolloAdmitted"), rs.getLong("clear"), rs.getLong("unclear"), rs.getLong("pending"),
                rs.getLong("errors"), rs.getLong("queued"), rs.getLong("protected"), rs.getLong("projectionPending"),
                rs.getLong("projectionErrors"), rs.getLong("apolloEntered"), rs.getLong("apolloSearched"),
                rs.getLong("apolloCandidates"), rs.getLong("apolloMatched"), rs.getLong("apolloHttpAttempts"),
                rs.getLong("registrationErrors"), rs.getLong("cancelled"))
            }, *args.toTypedArray()).toMap()
    }

    fun reportingSuggestions(filter: VerificationReportFilter, batches: Boolean = false,
        executionIds: List<Long>? = null): Map<Long, Triple<Long, Long, Long>> {
        require(executionIds == null || executionIds.isNotEmpty() && executionIds.size <= 50 && executionIds.all { it > 0 })
        val (where, args) = if (executionIds == null) reportingWhere(filter) else
            "i.owner_execution_id IN (${executionIds.joinToString(",") { "?" }})" to executionIds
        val group = when { batches -> "i.batch_number"; executionIds != null -> "i.owner_execution_id"; else -> "0" }
        return jdbc.query("""SELECT $group AS bucket,
            COALESCE(SUM(i.outcome IN ('DIRECT_CLEAR','APOLLO_CLEAR','APOLLO_UNCLEAR')),0) AS admitted,
            COALESCE(SUM(i.outcome='DIRECT_CLEAR'),0) AS direct,
            COALESCE(SUM(i.outcome IN ('APOLLO_CLEAR','APOLLO_UNCLEAR')),0) AS apollo
            FROM discovery_verification_item i JOIN discovery_verification_run r ON r.execution_id=i.owner_execution_id
            WHERE $where AND i.activated_at IS NOT NULL AND r.mode='AUDIT'
            ${if (batches || executionIds != null) "GROUP BY $group ORDER BY $group" else ""}""",
            RowMapper { rs, _ -> rs.getLong("bucket") to Triple(rs.getLong("admitted"), rs.getLong("direct"), rs.getLong("apollo")) },
            *args.toTypedArray()).toMap()
    }

    /** Hash the complete reason set: grouping TEXT directly can truncate comparisons at MySQL max_sort_length. */
    fun reportingReasons(filter: VerificationReportFilter): List<Pair<String, Long>> {
        val (where, args) = reportingWhere(filter)
        return jdbc.query("""SELECT MAX(i.reasons_json) AS reasons_json,COUNT(*) AS n FROM discovery_verification_item i
            WHERE $where AND i.activated_at IS NOT NULL AND i.reasons_json IS NOT NULL GROUP BY SHA2(i.reasons_json,256)""",
            RowMapper { rs, _ -> rs.getString("reasons_json") to rs.getLong("n") }, *args.toTypedArray())
    }

    fun reportingBatchTimes(executionId: Long): Map<Int, Instant> =
        jdbc.query("""SELECT batch_number,MAX(updated_at) AS updated_at FROM discovery_verification_item
            WHERE owner_execution_id=? AND activated_at IS NOT NULL GROUP BY batch_number ORDER BY batch_number""",
            RowMapper { rs, _ -> rs.getInt("batch_number") to rs.time("updated_at")!! }, executionId).toMap()

    fun reportingBatchReasons(executionId: Long): List<Triple<Int, String, Long>> =
        jdbc.query("""SELECT batch_number,MAX(reasons_json) AS reasons_json,COUNT(*) AS n
            FROM discovery_verification_item WHERE owner_execution_id=? AND activated_at IS NOT NULL
            AND reasons_json IS NOT NULL GROUP BY batch_number,SHA2(reasons_json,256) ORDER BY batch_number""",
            RowMapper { rs, _ -> Triple(rs.getInt("batch_number"), rs.getString("reasons_json"), rs.getLong("n")) }, executionId)

    fun reportingRecordTotal(filter: VerificationReportFilter): Long {
        val (where, args) = reportingWhere(filter)
        return jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item i WHERE $where AND i.activated_at IS NOT NULL",
            Long::class.java, *args.toTypedArray())!!
    }

    fun reportingRecords(filter: VerificationReportFilter, page: Int, size: Int): List<VerificationItem> {
        require(page >= 0 && size in 1..100)
        val (where, args) = reportingWhere(filter)
        // Prevent ORDER BY id/LIMIT from choosing an unrelated-owner PRIMARY scan on large runs.
        return jdbc.query("""SELECT i.* FROM discovery_verification_item i FORCE INDEX (idx_dvi_run_batch) WHERE $where AND i.activated_at IS NOT NULL
            ORDER BY i.id LIMIT ? OFFSET ?""", itemMapper, *(args + listOf(size, page.toLong() * size)).toTypedArray())
    }

    private fun reportingWhere(filter: VerificationReportFilter): Pair<String, List<Any>> {
        val clauses = mutableListOf("i.owner_execution_id=?")
        val args = mutableListOf<Any>(filter.executionId)
        when (filter.route) {
            "APOLLO" -> clauses += "i.apollo_entered_at IS NOT NULL"
            "DIRECT" -> { clauses += "i.route=?"; args += filter.route }
        }
        when (filter.result) {
            "CLEAR" -> clauses += "i.outcome IN ('DIRECT_CLEAR','APOLLO_CLEAR')"
            "UNCLEAR" -> clauses += "i.outcome='APOLLO_UNCLEAR'"
            "PENDING" -> clauses += "i.outcome='PENDING'"
            "ERROR" -> clauses += "i.state='ERROR'"
            "QUEUED" -> clauses += "i.state IN ('QUEUED','RUNNING','RETRY_WAIT')"
            "PROTECTED" -> clauses += "i.outcome='PROTECTED'"
            "CANCELLED" -> clauses += "i.state='CANCELLED'"
        }
        filter.batchNumber?.let { clauses += "i.batch_number=?"; args += it }
        if (filter.q.isNotEmpty()) {
            // Use ! as SQL escape, escaping literal !, %, _, and backslash explicitly.
            val q = "%" + filter.q.replace("!", "!!").replace("\\", "!\\").replace("%", "!%").replace("_", "!_") + "%"
            clauses += """(i.expert_doc_id LIKE ? ESCAPE '!' OR
                CONCAT_WS(' ',JSON_UNQUOTE(JSON_EXTRACT(i.snapshot_json,'$.profile.givenNames')),
                    JSON_UNQUOTE(JSON_EXTRACT(i.snapshot_json,'$.profile.familyNames'))) LIKE ? ESCAPE '!' OR
                JSON_UNQUOTE(JSON_EXTRACT(i.snapshot_json,'$.profile.email')) LIKE ? ESCAPE '!' OR
                JSON_UNQUOTE(JSON_EXTRACT(i.snapshot_json,'$.profile.institution')) LIKE ? ESCAPE '!')"""
            repeat(4) { args += q }
        }
        return clauses.joinToString(" AND ") to args
    }

    @Transactional
    fun createRun(executionId: Long, mode: VerificationMode, apolloRequested: Boolean, settingsJson: String, now: Instant): VerificationRun {
        require(executionId > 0 && settingsJson.isNotBlank())
        jdbc.update("INSERT IGNORE INTO discovery_verification_run (execution_id,mode,policy_version,apollo_requested,settings_json,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
            executionId, mode.name, mode.policyVersion, apolloRequested, settingsJson, now.verificationUtc(), now.verificationUtc())
        val run = lockRun(executionId)
        require(run.recheckJson == null && run.mode == mode && run.apolloRequested == apolloRequested && run.settingsJson == settingsJson) { "Run snapshot is immutable" }
        return run
    }

    @Transactional
    fun createRecheckRun(executionId: Long, mode: VerificationMode, apolloRequested: Boolean,
        settingsJson: String, spec: RecheckSpec, now: Instant): VerificationRun {
        require(executionId > 0 && settingsJson.isNotBlank())
        require(jdbc.queryForObject("SELECT COUNT(*) FROM task_execution WHERE id=?", Long::class.java, executionId) == 1L) {
            "Recheck requires a real owner execution"
        }
        val json = replayMapper.writeValueAsString(RecheckRun(spec))
        jdbc.update("""INSERT IGNORE INTO discovery_verification_run
            (execution_id,mode,policy_version,apollo_requested,settings_json,recheck_json,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?)""", executionId, mode.name, mode.policyVersion, apolloRequested,
            settingsJson, json, now.verificationUtc(), now.verificationUtc())
        val run = lockRun(executionId)
        if (run.mode != mode || run.apolloRequested != apolloRequested || run.settingsJson != settingsJson ||
            run.recheckJson == null || recheck(run).spec != spec) {
            throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Recheck spec is immutable")
        }
        return run
    }

    fun recheck(run: VerificationRun): RecheckRun =
        replayMapper.readValue(requireNotNull(run.recheckJson) { "Not a recheck run" }, RecheckRun::class.java)

    /** Hash exactly the persisted frozen inputs, in stable identity order. Never consult live ES. */
    fun recheckScopeHash(executionId: Long): String {
        val rows = jdbc.query("""SELECT expert_doc_id,identity_hash,policy_version,review_epoch,snapshot_json,fact_fingerprint
            FROM discovery_verification_item WHERE owner_execution_id=? ORDER BY expert_doc_id,identity_hash,policy_version,review_epoch""",
            RowMapper { rs, _ -> listOf(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                rs.getString(5), rs.getString(6)) }, executionId)
        return replayHash(replayMapper.writeValueAsString(rows))
    }

    /** Only control can change; the immutable spec is copied from the locked database row. */
    @Transactional
    fun compareAndSetRecheckControl(executionId: Long, expectedRevision: Long, control: RecheckControl, now: Instant): Boolean {
        val run = lockRun(executionId)
        if (run.revision != expectedRevision) return false
        val current = recheck(run)
        val allowed = when (current.control.phase) {
            RecheckPhase.PREPARING -> setOf(RecheckPhase.READY, RecheckPhase.FAILED, RecheckPhase.INTERRUPTED, RecheckPhase.CANCELLING)
            RecheckPhase.READY -> setOf(RecheckPhase.RUNNING, RecheckPhase.FAILED, RecheckPhase.INTERRUPTED, RecheckPhase.CANCELLING)
            RecheckPhase.RUNNING -> setOf(RecheckPhase.COMPLETED, RecheckPhase.FAILED, RecheckPhase.INTERRUPTED, RecheckPhase.CANCELLING)
            RecheckPhase.CANCELLING -> setOf(RecheckPhase.CANCELLED)
            else -> emptySet()
        }
        require(control.phase in allowed) { "Invalid recheck phase transition" }
        if (current.control.scopeHash != null) require(control.scopeHash == current.control.scopeHash) { "Frozen scope hash is immutable" }
        else if (control.phase != RecheckPhase.READY) require(control.scopeHash == null)
        if (control.phase == RecheckPhase.READY) {
            require(control.scopeHash == recheckScopeHash(executionId)) { "Scope hash mismatch" }
            require(jdbc.queryForObject("""SELECT COUNT(*) FROM discovery_verification_item
                WHERE owner_execution_id=? AND (review_epoch<>? OR state<>'RESERVED')""",
                Long::class.java, executionId, executionId) == 0L) { "Scope is not fully frozen" }
        }
        if (control.phase in setOf(RecheckPhase.COMPLETED, RecheckPhase.CANCELLED)) {
            require(jdbc.queryForObject("""SELECT COUNT(*) FROM discovery_verification_item
                WHERE owner_execution_id=? AND state IN ('RESERVED','QUEUED','RUNNING','RETRY_WAIT')""",
                Long::class.java, executionId) == 0L) { "Unsettled recheck items" }
        }
        if (control.phase == RecheckPhase.RUNNING) require(run.inputClosed)
        return jdbc.update("""UPDATE discovery_verification_run SET recheck_json=?,input_closed=?,revision=revision+1,updated_at=?
            WHERE execution_id=? AND revision=?""", replayMapper.writeValueAsString(current.copy(control = control)),
            run.inputClosed || control.phase == RecheckPhase.READY, now.verificationUtc(), executionId, expectedRevision) == 1
    }

    @Transactional
    fun reserveRecheckItem(input: VerificationReservation, now: Instant): VerificationItem {
        val run = lockRun(input.ownerExecutionId)
        val config = recheck(run)
        require(run.policyVersion == input.policyVersion)
        val existing = jdbc.query("""SELECT * FROM discovery_verification_item WHERE expert_doc_id=?
            AND identity_hash=? AND policy_version=? AND review_epoch=? FOR UPDATE""", itemMapper,
            input.expertDocId, input.identityHash, input.policyVersion, run.executionId).singleOrNull()
        if (existing != null) {
            require(existing.snapshotJson == input.snapshotJson && existing.factFingerprint == input.factFingerprint &&
                existing.sourceName == input.sourceName && existing.sourceItemKey == input.sourceItemKey) { "Frozen review input is immutable" }
            return existing
        }
        require(!run.inputClosed && config.control.phase == RecheckPhase.PREPARING)
        jdbc.update("""INSERT INTO discovery_verification_item
            (owner_execution_id,expert_doc_id,identity_hash,policy_version,review_epoch,source_name,source_item_key,
            batch_number,snapshot_json,fact_fingerprint,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,0,?,?,?,?)""", run.executionId, input.expertDocId, input.identityHash,
            input.policyVersion, run.executionId, input.sourceName, input.sourceItemKey, input.snapshotJson,
            input.factFingerprint, now.verificationUtc(), now.verificationUtc())
        val item = jdbc.query("""SELECT * FROM discovery_verification_item WHERE expert_doc_id=?
            AND identity_hash=? AND policy_version=? AND review_epoch=?""", itemMapper,
            input.expertDocId, input.identityHash, input.policyVersion, run.executionId).single()
        bump(run.executionId, now)
        return item
    }

    /** Full audit history is deliberately separate from business admission reads. */
    fun findHistoryByDocIds(docIds: Collection<String>): List<VerificationItem> {
        require(docIds.size <= 500)
        docIds.forEach { verificationText(it, 128) }
        if (docIds.isEmpty()) return emptyList()
        return jdbc.query("SELECT * FROM discovery_verification_item WHERE expert_doc_id IN (${docIds.joinToString(",") { "?" }}) ORDER BY id",
            itemMapper, *docIds.toTypedArray())
    }

    fun readReserved(executionId: Long, limit: Int): List<VerificationItem> {
        require(limit in 1..500)
        val run = findRun(executionId) ?: return emptyList()
        if (!recheckRunning(run)) return emptyList()
        return jdbc.query("""SELECT * FROM discovery_verification_item WHERE owner_execution_id=? AND review_epoch=?
            AND state='RESERVED' ORDER BY id LIMIT ?""", itemMapper, executionId, executionId, limit)
    }

    @Transactional
    fun cancelRecheckItem(itemId: Long, now: Instant): Boolean {
        val item = lockItem(itemId)
        require(item.reviewEpoch > 0)
        require(recheck(lockRun(item.ownerExecutionId)).control.phase in
            setOf(RecheckPhase.CANCELLING, RecheckPhase.INTERRUPTED, RecheckPhase.FAILED))
        if (item.admissionRevision != null || item.state == VerificationState.CANCELLED) return false
        jdbc.update("""UPDATE discovery_verification_item SET state='CANCELLED',outcome=NULL,route='NONE',
            completed_at=?,lease_token=NULL,lease_until=NULL,next_attempt_at=NULL,updated_at=? WHERE id=?
            AND admission_revision IS NULL""", now.verificationUtc(), now.verificationUtc(), itemId)
        jdbc.update("""UPDATE discovery_verification_step SET state='CANCELLED',finished_at=?
            WHERE item_id=? AND state='RUNNING'""", now.verificationUtc(), itemId)
        bump(item.ownerExecutionId, now)
        return true
    }

    private fun recheckRunning(run: VerificationRun): Boolean =
        run.recheckJson != null && run.inputClosed && recheck(run).control.phase == RecheckPhase.RUNNING

    private fun initializeSteps(itemId: Long, now: Instant) {
        for (step in 1..8) jdbc.update("""INSERT IGNORE INTO discovery_verification_step
            (item_id,step_no,attempt_no,state,created_at) VALUES (?,?,1,'NOT_STARTED',?)""", itemId, step, now.verificationUtc())
    }

    fun findSteps(itemId: Long): List<VerificationStep> = jdbc.query(
        "SELECT * FROM discovery_verification_step WHERE item_id=? ORDER BY step_no,attempt_no", stepMapper, itemId)

    /** A retry appends a new attempt. A completed attempt is never reused or rewritten. */
    @Transactional
    fun beginStep(itemId: Long, token: String, generation: Long, stepNo: Int, inputJson: String,
        versionJson: String, now: Instant): VerificationStep? {
        require(stepNo in 1..8 && inputJson.isNotBlank() && versionJson.isNotBlank())
        val item = lockItem(itemId)
        if (!owns(item, token, generation, now)) return null
        initializeSteps(itemId, now)
        val previous = findSteps(itemId).last { it.stepNo == stepNo }
        check(previous.state != VerificationStepState.RUNNING) { "Step attempt already running" }
        val hash = replayHash(inputJson)
        if (previous.state == VerificationStepState.NOT_STARTED) {
            jdbc.update("""UPDATE discovery_verification_step SET state='RUNNING',input_hash=?,input_json=?,version_json=?,started_at=?
                WHERE id=? AND state='NOT_STARTED'""", hash, inputJson, versionJson, now.verificationUtc(), previous.id)
        } else {
            jdbc.update("""INSERT INTO discovery_verification_step
                (item_id,step_no,attempt_no,state,input_hash,input_json,version_json,started_at,created_at)
                VALUES (?,?,?,'RUNNING',?,?,?,?,?)""", itemId, stepNo, previous.attemptNo + 1,
                hash, inputJson, versionJson, now.verificationUtc(), now.verificationUtc())
        }
        bump(item.ownerExecutionId, now)
        return findSteps(itemId).last { it.stepNo == stepNo }
    }

    @Transactional
    fun finishStep(itemId: Long, token: String, generation: Long, stepId: Long, state: VerificationStepState,
        outputJson: String?, reasonJson: String?, now: Instant): Boolean {
        require(state.terminal)
        val item = lockItem(itemId)
        if (!owns(item, token, generation, now)) return false
        val step = findSteps(itemId).singleOrNull { it.id == stepId } ?: return false
        if (step.state != VerificationStepState.RUNNING) return false
        require(now >= step.startedAt!!)
        val changed = jdbc.update("""UPDATE discovery_verification_step SET state=?,output_json=?,reason_json=?,finished_at=?
            WHERE id=? AND item_id=? AND state='RUNNING'""", state.name, outputJson, reasonJson, now.verificationUtc(), stepId, itemId)
        if (changed == 1) bump(item.ownerExecutionId, now)
        return changed == 1
    }

    @Transactional
    fun reserveItem(input: VerificationReservation, now: Instant): VerificationItem {
        require(findRun(input.ownerExecutionId)?.recheckJson == null) { "Ordinary reservation requires an ordinary run" }
        // Read duplicate identity without locks first: never lock two runs in one transaction.
        val existing = identity(input)
        if (existing != null) return checkedIdentity(existing, input)
        val run = lockRun(input.ownerExecutionId)
        require(run.recheckJson == null && run.policyVersion == input.policyVersion && !run.inputClosed)
        val inserted = jdbc.update("""INSERT IGNORE INTO discovery_verification_item
            (owner_execution_id,expert_doc_id,identity_hash,policy_version,review_epoch,source_name,source_item_key,batch_number,snapshot_json,fact_fingerprint,created_at,updated_at)
            VALUES (?,?,?,?,0,?,?,0,?,?,?,?)""", input.ownerExecutionId, input.expertDocId, input.identityHash,
            input.policyVersion, input.sourceName, input.sourceItemKey, input.snapshotJson, input.factFingerprint,
            now.verificationUtc(), now.verificationUtc())
        val item = jdbc.query("SELECT * FROM discovery_verification_item WHERE expert_doc_id=? AND identity_hash=? AND policy_version=? AND review_epoch=0 FOR UPDATE", itemMapper,
            input.expertDocId, input.identityHash, input.policyVersion).single()
        checkedIdentity(item, input)
        if (inserted == 1) bump(input.ownerExecutionId, now)
        return item
    }

    @Transactional
    fun activateItem(itemId: Long, batchNumber: Int, now: Instant): VerificationItem {
        require(batchNumber > 0)
        val item = lockItem(itemId)
        if (item.reviewEpoch > 0) require(recheckRunning(lockRun(item.ownerExecutionId)))
        if (item.state == VerificationState.RESERVED) {
            jdbc.update("UPDATE discovery_verification_item SET state='QUEUED',batch_number=?,activated_at=?,updated_at=? WHERE id=? AND state='RESERVED'",
                batchNumber, now.verificationUtc(), now.verificationUtc(), itemId)
            if (item.reviewEpoch > 0) initializeSteps(item.id, now)
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
        jdbc.query("SELECT DISTINCT expert_doc_id,policy_version FROM discovery_verification_item WHERE $BUSINESS_ITEMS AND expert_doc_id IN (${docIds.joinToString(",") { "?" }})",
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
            "SELECT * FROM discovery_verification_item WHERE $BUSINESS_ITEMS AND expert_doc_id IN (${docIds.joinToString(",") { "?" }}) ORDER BY id",
            itemMapper, *docIds.toTypedArray()
        )
    }

    @Transactional
    fun claimDue(now: Instant, leaseSeconds: Long): VerificationItem? {
        require(leaseSeconds > 0)
        val candidates = jdbc.query("""SELECT i.* FROM discovery_verification_item i
            JOIN discovery_verification_run r ON r.execution_id=i.owner_execution_id
            WHERE (i.review_epoch=0 OR (r.input_closed=1 AND JSON_UNQUOTE(JSON_EXTRACT(r.recheck_json,'$.control.phase'))='RUNNING'))
            AND (i.state='QUEUED' OR (i.state='RETRY_WAIT' AND i.next_attempt_at<=?) OR (i.state='RUNNING' AND i.lease_until<=?)) ORDER BY i.id LIMIT 100""",
            itemMapper, now.verificationUtc(), now.verificationUtc())
        // One run per transaction, even when all of its candidates lost a race.
        val owner = candidates.firstOrNull()?.ownerExecutionId ?: return null
        val run = lockRun(owner)
        if (run.recheckJson != null && !recheckRunning(run)) return null
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
        // Apollo quotas are enforced by the provider (429/Retry-After). Legacy quota
        // arguments remain compatible with existing callers/configuration, but do not cap Apollo.
        if (request.provider != VerificationProvider.APOLLO && item.requestCount >= maxRequestsPerItem)
            return VerificationAttemptReservation(deferred = VerificationBudgetReason.ITEM_LIMIT)
        if (request.provider == VerificationProvider.APOLLO && request.queryKind == VerificationQueryKind.DETAIL) {
            val retryAt = detailCreditsRetryAt()
            if (retryAt != null && now < retryAt)
                return VerificationAttemptReservation(deferred = VerificationBudgetReason.CREDITS_WAIT, retryAt = retryAt)
        }
        jdbc.update("""INSERT INTO discovery_verification_attempt (item_id,request_key,provider,query_kind,page_number,attempt_number,requested_at,result_code,request_json)
            VALUES (?,?,?,?,?,?,?,'DISPATCH_RESERVED',?)""", itemId, request.requestKey, request.provider.name, request.queryKind.name,
            request.pageNumber, request.attemptNumber, now.verificationUtc(), request.requestJson)
        val id = jdbc.queryForObject("SELECT id FROM discovery_verification_attempt WHERE request_key=?", Long::class.java, request.requestKey)!!
        jdbc.update("UPDATE discovery_verification_item SET request_count=request_count+1,updated_at=? WHERE id=?", now.verificationUtc(), itemId)
        bump(item.ownerExecutionId, now)
        return VerificationAttemptReservation(attemptId = id)
    }

    /** Current locking reads are essential: acquiring GET_LOCK must not reuse an earlier RR snapshot.
     * A success reserved before the fault completed cannot reopen the channel, even if it completed late.
     * Unfinished reservations occupy a probe window too; no process-local state is involved.
     */
    private fun detailCreditsRetryAt(): Instant? {
        val fault = jdbc.query("""SELECT id,completed_at FROM discovery_verification_attempt
            WHERE provider='APOLLO' AND query_kind='DETAIL' AND result_code='APOLLO_CREDITS_EXHAUSTED'
            AND completed_at IS NOT NULL ORDER BY completed_at DESC,id DESC LIMIT 1 FOR UPDATE""",
            RowMapper { rs, _ -> rs.getLong("id") to rs.time("completed_at")!! }).singleOrNull() ?: return null
        val success = jdbc.query("""SELECT id FROM discovery_verification_attempt WHERE provider='APOLLO'
            AND query_kind='DETAIL' AND id>? AND requested_at>? AND completed_at IS NOT NULL
            AND http_status=200 AND result_code='OK' ORDER BY id DESC LIMIT 1 FOR UPDATE""",
            RowMapper { rs, _ -> rs.getLong("id") }, fault.first, fault.second.verificationUtc())
        if (success.isNotEmpty()) return null
        val probe = jdbc.query("""SELECT requested_at FROM discovery_verification_attempt WHERE provider='APOLLO'
            AND query_kind='DETAIL' AND id>? AND requested_at>? ORDER BY id DESC LIMIT 1 FOR UPDATE""",
            RowMapper { rs, _ -> rs.time("requested_at")!! }, fault.first, fault.second.verificationUtc()).singleOrNull()
        return maxOf(fault.second, probe ?: fault.second).plusSeconds(1800)
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
    fun saveFailure(itemId: Long, token: String, generation: Long, result: VerificationResult,
        transientAttempts: Int, now: Instant): Boolean {
        require(result.state == VerificationState.ERROR)
        val item = lockItem(itemId)
        if (!owns(item, token, generation, now)) return false
        require(transientAttempts >= item.transientAttempts)
        jdbc.update("UPDATE discovery_verification_item SET transient_attempts=? WHERE id=? AND lease_token=? AND generation=? AND state='RUNNING'",
            transientAttempts, itemId, token, generation)
        check(saveEvidenceAndOutcome(itemId, token, generation, result, now))
        return true
    }

    @Transactional
    fun markProjection(itemId: Long, admissionRevision: Long, projection: VerificationProjection, error: String?, now: Instant): Boolean {
        require(admissionRevision >= 0)
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
        return jdbc.query("SELECT * FROM discovery_verification_item WHERE review_epoch=0 AND state='RESERVED' ORDER BY id LIMIT ?", itemMapper, limit)
    }

    /** Recovery may terminate an unactivated registration only after its owner's input is closed. */
    @Transactional
    fun failReserved(itemId: Long, reasonsJson: String, now: Instant): Boolean {
        val item = lockItem(itemId)
        if (item.reviewEpoch != 0L || item.state != VerificationState.RESERVED || !findRun(item.ownerExecutionId)!!.inputClosed) return false
        jdbc.update("UPDATE discovery_verification_item SET state='ERROR',reasons_json=?,completed_at=?,updated_at=? WHERE id=?", reasonsJson, now.verificationUtc(), now.verificationUtc(), itemId)
        bump(item.ownerExecutionId, now)
        return true
    }


    /** Called by the separate commit bean: no ES/HTTP while the fixed SQL lock order is held. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    fun lockForCommit(itemId: Long): VerificationItem {
        val item = findItem(itemId) ?: error("Verification item not found")
        lockRun(item.ownerExecutionId)
        jdbc.query("SELECT expert_doc_id FROM expert_discovery_admission WHERE expert_doc_id=? FOR UPDATE",
            RowMapper { rs, _ -> rs.getString(1) }, item.expertDocId)
        val locked = lockedItem(itemId)
        check(locked.reviewEpoch == 0L || recheckRunning(lockRun(locked.ownerExecutionId)) ||
            locked.admissionRevision != null) { "Recheck is not running" }
        return locked
    }

    @Transactional
    fun saveProgress(itemId: Long, token: String, generation: Long, evidenceJson: String,
        enteredAt: Instant?, searchedAt: Instant?, candidatesAt: Instant?, now: Instant): Boolean {
        val item = lockItem(itemId)
        if (!owns(item, token, generation, now)) return false
        jdbc.update("""UPDATE discovery_verification_item SET evidence_json=?,apollo_entered_at=COALESCE(apollo_entered_at,?),
            search_completed_at=COALESCE(search_completed_at,?),candidate_found_at=COALESCE(candidate_found_at,?),updated_at=?
            WHERE id=? AND lease_token=? AND generation=? AND state='RUNNING'""",
            evidenceJson, enteredAt?.verificationUtc(), searchedAt?.verificationUtc(), candidatesAt?.verificationUtc(),
            now.verificationUtc(), itemId, token, generation)
        bump(item.ownerExecutionId, now)
        return true
    }

    /** Completion and cursor are one commit, acknowledged before any subsequent wire request. */
    @Transactional
    fun completeAttemptAndProgress(itemId: Long, token: String, generation: Long, attemptId: Long,
        httpStatus: Int?, resultCode: String, responseJson: String?, responseSha256: String?,
        evidenceJson: String, enteredAt: Instant?, searchedAt: Instant?, candidatesAt: Instant?, now: Instant): Boolean {
        if (!completeAttempt(itemId, token, generation, attemptId, httpStatus, resultCode, responseJson, responseSha256, now)) return false
        check(saveProgress(itemId, token, generation, evidenceJson, enteredAt, searchedAt, candidatesAt, now))
        return true
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    fun attachAdmission(itemId: Long, revision: Long, now: Instant) {
        require(revision >= 0)
        val item = lockItem(itemId)
        check(item.reviewEpoch == 0L || recheckRunning(lockRun(item.ownerExecutionId))) { "Recheck is not running" }
        check(jdbc.update("""UPDATE discovery_verification_item SET admission_revision=?,projection_state='PENDING',updated_at=?
            WHERE id=? AND state='COMPLETED' AND admission_revision IS NULL
            AND policy_version=? AND outcome IN ('DIRECT_CLEAR','APOLLO_CLEAR','APOLLO_UNCLEAR')""",
            revision, now.verificationUtc(), itemId, VerificationMode.ENFORCE.policyVersion) == 1)
        bump(findItem(itemId)!!.ownerExecutionId, now)
    }

    fun listProjectionDue(limit: Int): List<VerificationItem> {
        require(limit in 1..100)
        return jdbc.query("""SELECT * FROM discovery_verification_item WHERE state='COMPLETED' AND admission_revision IS NOT NULL
            AND projection_state IN ('PENDING','ERROR') AND (projection_error IS NULL OR projection_error<>'SUPERSEDED_BY_REVIEW')
            ORDER BY updated_at,id LIMIT ?""", itemMapper, limit)
    }

    fun findAttempts(itemId: Long): List<VerificationAttempt> = jdbc.query(
        "SELECT * FROM discovery_verification_attempt WHERE item_id=? ORDER BY id", attemptMapper, itemId)

    /** Same-run successful HTTP ledger only; no cache rows and no synthetic attempts. */
    fun findSuccessfulDetails(ownerExecutionId: Long, personIds: Collection<String>): Map<String, VerificationAttempt> {
        require(ownerExecutionId > 0)
        if (personIds.isEmpty()) return emptyMap()
        val found = linkedMapOf<String, VerificationAttempt>()
        personIds.distinct().chunked(500).forEach { ids ->
            val attempts = jdbc.query("""SELECT a.* FROM discovery_verification_item i FORCE INDEX (idx_dvi_run_batch)
                JOIN discovery_verification_attempt a FORCE INDEX (idx_dva_item) ON a.item_id=i.id
                WHERE i.owner_execution_id=? AND a.provider='APOLLO' AND a.query_kind='DETAIL'
                AND a.completed_at IS NOT NULL AND a.http_status=200 AND a.result_code='OK'
                AND a.response_json IS NOT NULL AND a.response_sha256 IS NOT NULL
                AND JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(a.request_json),a.request_json,'{}'),'$.personId'))
                    IN (${ids.joinToString(",") { "?" }}) ORDER BY a.id""", attemptMapper,
                *(listOf(ownerExecutionId) + ids).toTypedArray())
            attempts.forEach attemptLoop@ { attempt ->
                // response_sha256 identifies the original HTTP body; the saved parsed JSON is not that body.
                val candidate = runCatching {
                    replayMapper.readValue(attempt.responseJson, com.weibo.talentintroduction.discovery.service.ApolloCandidate::class.java)
                }.getOrNull() ?: return@attemptLoop
                val requested = replayMapper.readTree(attempt.requestJson).path("personId").asText()
                if (candidate.id == requested && candidate.id in ids) found.putIfAbsent(candidate.id, attempt)
            }
        }
        return found
    }
    private fun identity(input: VerificationReservation) = jdbc.query("SELECT * FROM discovery_verification_item WHERE expert_doc_id=? AND identity_hash=? AND policy_version=? AND review_epoch=0",
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
        item.state == VerificationState.RUNNING && item.leaseToken == token && item.generation == generation &&
            item.leaseUntil?.let { it > now } == true &&
            (item.reviewEpoch == 0L || recheckRunning(findRun(item.ownerExecutionId)!!))
    private fun bump(owner: Long, now: Instant) {
        jdbc.update("UPDATE discovery_verification_run SET revision=revision+1,updated_at=? WHERE execution_id=?", now.verificationUtc(), owner)
    }

    companion object {
        private const val BUSINESS_ITEMS = "(review_epoch=0 OR (policy_version='APV_20261018_V1_ENFORCE' AND admission_revision IS NOT NULL))"
        private val replayMapper = jacksonObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        fun replayHash(value: String?): String = MessageDigest.getInstance("SHA-256")
            .digest((value ?: "\u0000").toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        /** The caller cannot alter provider evidence, search cursors, or any other progress member. */
        fun replayEvidence(value: String?): String {
            val tree = replayMapper.readTree(requireNotNull(value)) as? ObjectNode
                ?: throw IllegalArgumentException("Replay evidence must be an object")
            val progress = tree.get("progress")
            require(progress == null || progress is ObjectNode)
            val next = (progress?.deepCopy<ObjectNode>() ?: replayMapper.createObjectNode())
            next.put("stage", "DECIDE")
            tree.set<ObjectNode>("progress", next)
            return tree.toString()
        }
        private fun ResultSet.time(name: String): Instant? = getTimestamp(name)?.toLocalDateTime()?.verificationInstant()
        private fun ResultSet.nullLong(name: String): Long? = getLong(name).let { if (wasNull()) null else it }
        private val stepMapper = RowMapper { rs: ResultSet, _: Int -> VerificationStep(
            rs.getLong("id"), rs.getLong("item_id"), rs.getInt("step_no"), rs.getInt("attempt_no"),
            VerificationStepState.valueOf(rs.getString("state")), rs.getString("input_hash"), rs.getString("input_json"),
            rs.getString("output_json"), rs.getString("reason_json"), rs.getString("version_json"),
            rs.time("started_at"), rs.time("finished_at"), rs.time("created_at")!!) }
        private val attemptMapper = RowMapper { rs: ResultSet, _: Int -> VerificationAttempt(
            rs.getLong("id"), rs.getLong("item_id"), rs.getString("request_key"),
            VerificationProvider.valueOf(rs.getString("provider")), VerificationQueryKind.valueOf(rs.getString("query_kind")),
            rs.getInt("page_number"), rs.getInt("attempt_number"), rs.time("requested_at")!!, rs.time("completed_at"),
            rs.getInt("http_status").let { if (rs.wasNull()) null else it }, rs.getString("result_code"),
            rs.getString("request_json"), rs.getString("response_json"), rs.getString("response_sha256")) }
        private val runMapper = RowMapper { rs: ResultSet, _: Int -> VerificationRun(rs.getLong("execution_id"), VerificationMode.valueOf(rs.getString("mode")),
            rs.getString("policy_version"), rs.getBoolean("apollo_requested"), rs.getString("settings_json"), rs.getBoolean("input_closed"), rs.getLong("revision"), rs.time("created_at")!!, rs.time("updated_at")!!, rs.getString("recheck_json")) }
        private val itemMapper = RowMapper { rs: ResultSet, _: Int -> VerificationItem(
            rs.getLong("id"), rs.getLong("owner_execution_id"), rs.getString("expert_doc_id"), rs.getString("identity_hash"), rs.getString("policy_version"),
            rs.getString("source_name"), rs.getString("source_item_key"), rs.getInt("batch_number"), rs.getString("snapshot_json"), rs.getString("fact_fingerprint"),
            VerificationState.valueOf(rs.getString("state")), rs.getString("outcome")?.let(VerificationOutcome::valueOf), VerificationQualification.valueOf(rs.getString("qualification")),
            VerificationRoute.valueOf(rs.getString("route")), rs.time("apollo_entered_at"), rs.time("search_completed_at"), rs.time("candidate_found_at"), rs.time("person_matched_at"),
            rs.getString("apollo_person_id"), rs.getString("evidence_json"), rs.getString("reasons_json"), rs.getInt("request_count"), rs.getInt("transient_attempts"), rs.getLong("generation"),
            rs.getString("lease_token"), rs.time("lease_until"), rs.time("next_attempt_at"), rs.nullLong("admission_revision"), VerificationProjection.valueOf(rs.getString("projection_state")),
            rs.getString("projection_error"), rs.time("activated_at"), rs.time("completed_at"), rs.time("created_at")!!, rs.time("updated_at")!!, rs.getLong("review_epoch")) }
    }
}

data class VerificationAttemptChannels(val search: Long, val detail: Long, val other: Long, val pendingDispatch: Long)

data class VerificationReportFilter(
    val executionId: Long, val route: String = "ALL", val result: String = "ALL",
    val q: String = "", val batchNumber: Int? = null
) {
    init {
        require(executionId > 0)
        require(route in setOf("ALL", "DIRECT", "APOLLO"))
        require(result in setOf("ALL", "CLEAR", "UNCLEAR", "PENDING", "ERROR", "QUEUED", "PROTECTED", "CANCELLED"))
        require(q.length <= 100 && (batchNumber == null || batchNumber > 0))
    }
}
