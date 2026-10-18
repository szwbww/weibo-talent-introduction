package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.discovery.repository.VerificationReportFilter
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/** No provider, ES, task progress, or write service is available to this reader. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class DiscoveryVerificationQueryService(
    private val repository: DiscoveryVerificationRepository,
    private val admissions: DiscoveryReviewRepository,
    private val properties: DiscoveryVerificationProperties
) {
    private val mapper = jacksonObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    private val rules = DiscoveryVerificationRules()

    fun capabilities(): VerificationCapabilities {
        val reasons = mutableListOf<String>()
        if (!properties.enabled) reasons += "VERIFICATION_DISABLED"
        if (properties.apolloApiKey.isBlank()) reasons += "APOLLO_API_KEY_MISSING"
        return VerificationCapabilities(properties.enabled, properties.workerEnabled, properties.mode,
            reasons.isEmpty(), properties.apolloDefault, reasons)
    }

    fun summary(executionIds: List<Long>): List<VerificationSummary> {
        require(executionIds.isNotEmpty() && executionIds.size <= 50 && executionIds.all { it > 0 })
        val ids = executionIds.distinct()
        val runs = repository.reportingRuns(ids)
        val statuses = repository.reportingCollectionStatuses(ids)
        val counts = repository.reportingCounts(VerificationReportFilter(ids.first()), executionIds = ids)
        val suggestions = repository.reportingSuggestions(VerificationReportFilter(ids.first()), executionIds = ids)
        val asOf = Instant.now()
        return ids.map { id -> summaryRow(id, runs[id], counts[id] ?: VerificationCounts(), statuses[id], asOf, suggestions[id]) }
    }

    fun batches(executionId: Long, page: Int, size: Int): VerificationBatches {
        validatePage(page, size)
        val run = repository.findRun(executionId)
        val filter = VerificationReportFilter(executionId)
        val status = repository.reportingCollectionStatuses(listOf(executionId))[executionId]
        val asOf = Instant.now()
        if (run == null) return VerificationBatches("NOT_RECORDED", null, emptyList(), null, null, asOf, null, status)
        val counts = repository.reportingCounts(filter).getValue(0L)
        val grouped = repository.reportingCounts(filter, batches = true).entries.toList()
        val suggestions = if (run.mode == VerificationMode.AUDIT) repository.reportingSuggestions(filter, batches = true) else emptyMap()
        val start = (page.toLong() * size).coerceAtMost(grouped.size.toLong()).toInt()
        val items = grouped.drop(start).take(size).map { (batch, value) -> VerificationBatch(batch.toInt(), value, suggested(run, suggestions[batch])) }
        return VerificationBatches("RECORDED", grouped.size.toLong(), items, counts, run.revision, asOf,
            run.mode, status ?: "EXPIRED", lifecycle(run, counts), suggested(run, repository.reportingSuggestions(filter)[0L]))
    }

    fun records(filter: VerificationReportFilter, page: Int, size: Int): VerificationRecords {
        validatePage(page, size)
        val run = repository.findRun(filter.executionId)
        val status = repository.reportingCollectionStatuses(listOf(filter.executionId))[filter.executionId]
        val asOf = Instant.now()
        if (run == null) return VerificationRecords("NOT_RECORDED", null, emptyList(), null, null, asOf, null, status)
        val counts = repository.reportingCounts(filter).getValue(0L)
        val runCounts = if (filter == VerificationReportFilter(filter.executionId)) counts else
            repository.reportingCounts(VerificationReportFilter(filter.executionId)).getValue(0L)
        val total = repository.reportingRecordTotal(filter)
        val items = repository.reportingRecords(filter, page, size)
        val current = admissions.findAdmissions(items.map { it.expertDocId }).associateBy { it.expertDocId }
        return VerificationRecords("RECORDED", total, items.map { item ->
            val admission = current[item.expertDocId]
            record(item, admission?.decision, admission?.revision)
        }, counts, run.revision, asOf, run.mode, status ?: "EXPIRED", lifecycle(run, runCounts),
            reasons(filter), suggested(run, repository.reportingSuggestions(filter)[0L]))
    }

    fun item(itemId: Long): VerificationItemDetail {
        require(itemId > 0)
        val item = repository.findItem(itemId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Verification item not found")
        val run = repository.findRun(item.ownerExecutionId) ?: error("Verification run missing")
        val admission = admissions.findAdmissions(listOf(item.expertDocId)).singleOrNull()
        val snapshot = mapper.readValue<VerificationSnapshot>(item.snapshotJson)
        val payload = item.evidenceJson?.let { mapper.readValue<VerificationWorkerEvidence>(it) } ?: VerificationWorkerEvidence()
        return VerificationItemDetail(record(item, admission?.decision, admission?.revision, snapshot, payload), item.ownerExecutionId,
            item.state, item.outcome, item.qualification, snapshot.profile.identityVerification,
            snapshot.profile.employment, snapshot.profile.institutionEvidence, snapshot.boundOrcid,
            payload.evidence, reasonCodes(item).map { VerificationReason(it, reasonLabel(it), 1) },
            repository.findAttempts(itemId).map { VerificationAttemptSummary(it.id, it.provider, it.queryKind,
                it.pageNumber, it.attemptNumber, it.requestedAt, it.completedAt, it.httpStatus, it.resultCode, it.responseSha256) },
            item.admissionRevision, item.projectionError, run.mode, run.revision, Instant.now())
    }

    private fun summaryRow(id: Long, run: VerificationRun?, counts: VerificationCounts, status: String?, asOf: Instant,
        suggestion: Triple<Long, Long, Long>?) =
        VerificationSummary(id, if (run == null) "NOT_RECORDED" else "RECORDED", if (run == null) null else counts,
            run?.let { lifecycle(it, counts) }, run?.revision, asOf, run?.mode, status ?: if (run != null) "EXPIRED" else null,
            run?.let { suggested(it, suggestion) })

    private fun lifecycle(run: VerificationRun, counts: VerificationCounts) = when {
        counts.queued > 0 -> "ACTIVE"
        !run.inputClosed -> "COLLECTING"
        else -> "COMPLETED"
    }

    private fun suggested(run: VerificationRun, values: Triple<Long, Long, Long>?): VerificationSuggestions? =
        if (run.mode != VerificationMode.AUDIT) null else VerificationSuggestions(
            values?.first ?: 0, values?.second ?: 0, values?.third ?: 0)

    private fun record(item: VerificationItem, decision: String?, revision: Long?,
        snapshot: VerificationSnapshot = mapper.readValue(item.snapshotJson),
        payload: VerificationWorkerEvidence = item.evidenceJson?.let { mapper.readValue<VerificationWorkerEvidence>(it) } ?: VerificationWorkerEvidence()
    ): VerificationRecord {
        // Use the shared pure rules only for the explanation of missing fields, never to rewrite the recorded result.
        val missing = rules.evaluate(snapshot, payload.evidence, payload.search).missingFields
        return VerificationRecord(item.id, item.expertDocId, snapshot.profile.displayName, snapshot.profile.email,
            snapshot.profile.institution, item.sourceName, item.batchNumber, item.route,
            item.outcome?.name ?: if (item.state == VerificationState.ERROR) "ERROR" else "QUEUED",
            missing, reasonCodes(item), item.updatedAt, decision, item.projectionState, revision, item.qualification)
    }

    private fun reasonCodes(item: VerificationItem): List<String> = item.reasonsJson?.let { mapper.readValue<List<String>>(it).distinct() } ?: emptyList()
    private fun reasons(filter: VerificationReportFilter): List<VerificationReason> {
        val totals = sortedMapOf<String, Long>()
        repository.reportingReasons(filter).forEach { (json, count) ->
            mapper.readValue<List<String>>(json).distinct().forEach { totals[it] = (totals[it] ?: 0) + count }
        }
        return totals.map { (code, count) -> VerificationReason(code, reasonLabel(code), count) }
    }
    private fun validatePage(page: Int, size: Int) { require(page >= 0 && size in 1..100) }
    private fun reasonLabel(code: String): String = REASON_LABELS[code] ?: code

    companion object {
        private val REASON_LABELS = mapOf(
            "QUALIFICATION_MET" to "资格已明确", "QUALIFICATION_EVIDENCE_MISSING" to "资格证据缺失",
            "IDENTITY_AMBIGUOUS" to "身份存在歧义", "APOLLO_NO_MATCH" to "未找到候选人",
            "APOLLO_PERSON_MATCHED" to "已确认 Apollo 本人", "IDENTITY_EVIDENCE_MISSING" to "身份证据缺失",
            "CURRENT_INSTITUTION_EVIDENCE_MISSING" to "当前机构证据缺失", "IDENTITY_NOT_MATCHED" to "身份未匹配",
            "AGE_REQUIREMENT_NOT_MET" to "年龄及职称条件不符", "EVIDENCE_CONFLICT" to "证据冲突",
            "NAME_CONFLICT" to "姓名冲突", "AGE_CONFLICT" to "年龄证据冲突", "INSTITUTION_CONFLICT" to "机构冲突",
            "ORCID_CONFLICT" to "ORCID 冲突", "APOLLO_ID_CONFLICT" to "Apollo 标识冲突",
            "FACT_CHANGED" to "输入事实已变化", "PROTECTED" to "保护跳过", "SEARCH_INCOMPLETE" to "查询尚未完成",
            "EVIDENCE_INCOMPLETE" to "证据尚未完整", "CONFIG_AUTH" to "接口认证配置错误",
            "REQUEST_INVALID" to "接口请求无效", "RESPONSE_INVALID" to "接口响应无效",
            "SUPERSEDED_BY_REVIEW" to "已由人工审核决定取代")
    }
}

data class VerificationCapabilities(val enabled: Boolean, val workerEnabled: Boolean, val mode: VerificationMode,
    val ready: Boolean, val apolloDefault: Boolean, val reasonCodes: List<String>)
data class VerificationSuggestions(val autoAdmitted: Long, val directAdmitted: Long, val apolloAdmitted: Long)
data class VerificationReason(val code: String, val label: String, val count: Long)
data class VerificationSummary(val executionId: Long, val coverage: String, val counts: VerificationCounts?,
    val lifecycle: String?, val snapshotVersion: Long?, val asOf: Instant, val mode: VerificationMode?,
    val collectionStatus: String?, val suggested: VerificationSuggestions?)
data class VerificationBatch(val batchNumber: Int, val counts: VerificationCounts, val suggested: VerificationSuggestions?)
data class VerificationBatches(val coverage: String, val totalBatches: Long?, val items: List<VerificationBatch>,
    val runCounts: VerificationCounts?, val snapshotVersion: Long?, val asOf: Instant, val mode: VerificationMode?,
    val collectionStatus: String?, val lifecycle: String? = null, val suggested: VerificationSuggestions? = null)
data class VerificationRecords(val coverage: String, val total: Long?, val items: List<VerificationRecord>,
    val counts: VerificationCounts?, val snapshotVersion: Long?, val asOf: Instant, val mode: VerificationMode?,
    val collectionStatus: String?, val lifecycle: String? = null, val reasons: List<VerificationReason> = emptyList(),
    val suggested: VerificationSuggestions? = null)
data class VerificationRecord(val itemId: Long, val docId: String, val name: String, val email: String?,
    val institution: String?, val source: String, val batchNumber: Int, val route: VerificationRoute,
    val resultLabel: String, val missingFields: List<String>, val reasonCodes: List<String>, val updatedAt: Instant,
    val currentAdmissionDecision: String?, val projectionState: VerificationProjection, val currentAdmissionRevision: Long?,
    val qualification: VerificationQualification)
data class VerificationAttemptSummary(val id: Long, val provider: VerificationProvider, val queryKind: VerificationQueryKind,
    val pageNumber: Int, val attemptNumber: Int, val requestedAt: Instant, val completedAt: Instant?,
    val httpStatus: Int?, val resultCode: String, val responseSha256: String?)
data class VerificationItemDetail(val record: VerificationRecord, val executionId: Long, val state: VerificationState,
    val outcome: VerificationOutcome?, val qualification: VerificationQualification,
    val identity: com.weibo.talentintroduction.expert.domain.IdentityVerification?, val originalEmployment: String?,
    val institutionEvidence: String?, val boundOrcid: String?, val evidence: List<VerificationEvidence>,
    val reasons: List<VerificationReason>, val attempts: List<VerificationAttemptSummary>, val admissionRevision: Long?,
    val projectionError: String?, val mode: VerificationMode, val snapshotVersion: Long, val asOf: Instant)
