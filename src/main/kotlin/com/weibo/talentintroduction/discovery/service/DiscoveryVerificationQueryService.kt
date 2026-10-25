package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.discovery.repository.VerificationReportFilter
import com.weibo.talentintroduction.discovery.repository.VerificationAttemptChannels
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/** Reads SQL/configuration and the optional worker's current authentication latch only. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class DiscoveryVerificationQueryService(
    private val repository: DiscoveryVerificationRepository,
    private val admissions: DiscoveryReviewRepository,
    private val properties: DiscoveryVerificationProperties,
    private val worker: ObjectProvider<DiscoveryVerificationWorker>? = null
) {
    private val mapper = jacksonObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    private val rules = DiscoveryVerificationRules()

    fun capabilities(): VerificationCapabilities {
        val reasons = mutableListOf<String>()
        if (!properties.enabled) reasons += "VERIFICATION_DISABLED"
        if (properties.apolloApiKey.isBlank()) reasons += "APOLLO_API_KEY_MISSING"
        if (worker?.getIfAvailable()?.isAuthBlocked() == true) reasons += "CONFIG_AUTH"
        return VerificationCapabilities(properties.enabled, properties.workerEnabled, properties.mode,
            reasons.isEmpty(), properties.apolloDefault, reasons, properties.countrySupplementEnabled)
    }

    fun summary(executionIds: List<Long>): List<VerificationSummary> {
        require(executionIds.isNotEmpty() && executionIds.size <= 50 && executionIds.all { it > 0 })
        val ids = executionIds.distinct()
        val runs = repository.reportingRuns(ids)
        val statuses = repository.reportingCollectionStatuses(ids)
        val counts = repository.reportingCounts(VerificationReportFilter(ids.first()), executionIds = ids)
        val suggestions = repository.reportingSuggestions(VerificationReportFilter(ids.first()), executionIds = ids)
        val attempts = repository.reportingAttemptChannels(ids)
        val asOf = Instant.now()
        return ids.map { id -> summaryRow(id, runs[id], counts[id] ?: VerificationCounts(), statuses[id], asOf,
            suggestions[id], attempts[id]) }
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
        val updatedAt = repository.reportingBatchTimes(executionId)
        val batchReasons = repository.reportingBatchReasons(executionId).groupBy { it.first }
        var cumulative = 0L
        val cumulativeProfiles = grouped.associate { (batch, value) ->
            cumulative += value.profiles
            batch to cumulative
        }
        val start = (page.toLong() * size).coerceAtMost(grouped.size.toLong()).toInt()
        val items = grouped.drop(start).take(size).map { (batch, value) ->
            VerificationBatch(batch.toInt(), value, suggested(run, suggestions[batch]),
                cumulativeProfiles.getValue(batch), updatedAt[batch.toInt()],
                aggregateReasons(batchReasons[batch.toInt()].orEmpty().map { it.second to it.third }))
        }
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

    /** Audit reads never reuse the admission-facing filtered repository query. */
    fun history(docIds: Collection<String>): List<VerificationItem> = repository.findHistoryByDocIds(docIds)

    fun item(itemId: Long): VerificationItemDetail {
        require(itemId > 0)
        val item = repository.findItem(itemId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Verification item not found")
        val run = repository.findRun(item.ownerExecutionId) ?: error("Verification run missing")
        val admission = admissions.findAdmissions(listOf(item.expertDocId)).singleOrNull()
        val snapshot = mapper.readValue<VerificationSnapshot>(item.snapshotJson)
        val payload = workerPayload(item)
        return VerificationItemDetail(record(item, admission?.decision, admission?.revision, snapshot, payload), item.ownerExecutionId,
            item.state, item.outcome, item.qualification, snapshot.profile.identityVerification,
            snapshot.profile.employment, snapshot.profile.institutionEvidence, snapshot.boundOrcid,
            payload?.evidence ?: emptyList(), reasonCodes(item).map { VerificationReason(it, reasonLabel(it), 1) },
            repository.findAttempts(itemId).map { VerificationAttemptSummary(it.id, it.provider, it.queryKind,
                it.pageNumber, it.attemptNumber, it.requestedAt, it.completedAt, it.httpStatus, it.resultCode, it.responseSha256,
                VerificationDiagnostics.read(it.responseJson)) },
            item.admissionRevision, item.projectionError, run.mode, run.revision, Instant.now(),
            item.apolloPersonId, VerificationApolloStages(item.apolloEnteredAt, item.searchCompletedAt,
                item.candidateFoundAt, item.personMatchedAt), payload?.search?.matchingVersion ?: 1,
            payload?.search?.candidates?.firstOrNull { it.id == item.apolloPersonId },
            payload?.search?.academicResearchFields, payload?.diagnostics?.let { saved ->
                saved.copy(decision = saved.decision?.takeIf { it.traceOrigin == "RECORDED" }) .bounded()
            }, item.reviewEpoch, run.recheckJson?.let { repository.recheck(run) }, repository.findSteps(itemId))
    }

    fun requireRecheck(id: Long): VerificationRun {
        require(id > 0)
        return repository.findRun(id)?.takeIf { it.recheckJson != null }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "RECHECK_NOT_FOUND")
    }

    fun requireRecheckItem(runId: Long, itemId: Long): VerificationItem {
        requireRecheck(runId)
        require(itemId > 0)
        return repository.findItem(itemId)?.takeIf { it.ownerExecutionId == runId && it.reviewEpoch == runId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "RECHECK_ITEM_NOT_FOUND")
    }

    fun recheckRun(id: Long): RecheckRunView = recheckView(requireRecheck(id))

    private fun recheckView(run: VerificationRun): RecheckRunView {
        val frozen = repository.recheck(run)
        val scope = mapper.readTree(frozen.spec.scope)
        return RecheckRunView(run.executionId, run.executionId, frozen.spec.requestKey, frozen.spec.actor,
            frozen.control.phase, run.mode, frozen.spec.evidenceMode, run.revision, frozen.control.scopeHash,
            scope.path("specHash").asText(null), scope.path("scope").asText(), scope.get("filter"),
            run.inputClosed, repository.recheckCounts(run.executionId), repository.recheckTask(run.executionId),
            frozen.spec.evaluatedAt, frozen.spec.codeVersion, frozen.spec.parserVersion,
            mapper.readTree(run.settingsJson), run.createdAt, run.updatedAt)
    }

    fun recheckRuns(page: Int, size: Int, docId: String?): RecheckPage<RecheckRunView> {
        recheckPage(page, size)
        docId?.let { verificationText(it, 128) }
        val result = repository.recheckRuns(page, size, docId)
        return RecheckPage(result.first, page, size, result.second.map(::recheckView))
    }

    fun latestRechecks(docIds: List<String>): Map<String, RecheckRunView?> {
        require(docIds.size in 1..100)
        docIds.forEach { verificationText(it, 128) }
        val latest = repository.latestRechecks(docIds.distinct())
        val runs = latest.values.distinct().associateWith { recheckRun(it) }
        return docIds.distinct().associateWith { latest[it]?.let(runs::getValue) }
    }

    fun recheckItems(id: Long, page: Int, size: Int, state: VerificationState?): RecheckPage<RecheckItemView> {
        requireRecheck(id)
        recheckPage(page, size)
        val result = repository.recheckItems(id, page, size, state)
        val current = admissions.findAdmissions(result.second.map { it.expertDocId }).associateBy { it.expertDocId }
        return RecheckPage(result.first, page, size, result.second.map { recheckItemView(it, current[it.expertDocId]) })
    }

    fun recheckItem(id: Long, itemId: Long): RecheckItemView {
        val item = requireRecheckItem(id, itemId)
        return recheckItemView(item, admissions.findAdmission(item.expertDocId))
    }

    private fun recheckItemView(item: VerificationItem, current: ExpertDiscoveryAdmission?): RecheckItemView {
        val root = mapper.readTree(item.snapshotJson)
        val review = root.path("reviewInput")
        val trace = repository.trace(item.id)
        val latest = trace.steps.groupBy { it.stepNo }.mapValues { it.value.last() }
        fun value(json: String?): com.fasterxml.jackson.databind.JsonNode? = json?.let { mapper.readTree(it) }?.let {
            if (it.has("value")) it.get("value") else it
        }
        val combined = value(latest[7]?.outputJson)
        val saved = value(latest[8]?.outputJson)
        val allReasons = (reasonCodes(item) + trace.steps.flatMap { step ->
            val reason = value(step.reasonJson)
            if (reason?.isArray == true) reason.map { it.asText() }
            else reason?.path("codes")?.takeIf { it.isArray }?.map { it.asText() }.orEmpty()
        }).distinct()
        return RecheckItemView(item.id, item.ownerExecutionId, item.expertDocId, item.batchNumber, item.state,
            root.path("profile").path("givenNames").asText(null), root.path("profile").path("familyNames").asText(null),
            root.path("profile").path("email").asText(null), item.identityHash, item.factFingerprint,
            mapper.valueToTree(mapOf("decision" to review.path("beforeDecision"),
                "revision" to review.path("expectedAdmissionRevision"))),
            combined?.get("proposed"), current?.let { RecheckAdmissionView(it.decision, it.revision) },
            if (item.admissionRevision == null) null else RecheckAdmissionView("AUTO_PASSED", item.admissionRevision),
            item.outcome, item.qualification, item.route, item.projectionState, item.projectionError,
            item.admissionRevision != null, item.requestCount, trace, allReasons, review.path("paperSourceRefs"),
            review.path("evidenceHash").asText(null), review.path("baselineItemId").takeIf { it.isIntegralNumber }?.longValue(),
            latest.values.mapNotNull { value(it.versionJson) }.distinct(), saved, item.updatedAt,
            recheckEvidence(item, review.path("paperSourceRefs")))
    }

    /** Only saved, identity-bound facts; no archive reads, re-parsing or fallback to snapshot evidence. */
    private fun recheckEvidence(item: VerificationItem, refs: com.fasterxml.jackson.databind.JsonNode): com.fasterxml.jackson.databind.JsonNode {
        val payload = workerPayload(item)
        val facts = payload?.evidence.orEmpty().filter {
            it.identityHash == item.identityHash && it.factFingerprint == item.factFingerprint
        }
        val state = when {
            item.evidenceJson == null -> "MISSING"
            payload == null -> "MALFORMED"
            facts.isEmpty() -> "EMPTY"
            else -> "RECORDED"
        }
        val locator = Regex("^/archive/([1-9][0-9]*)/attempt/([1-9][0-9]*)/page/([0-9]+)/paragraph/([0-9]+)/year/([0-9]+)$")
        val projected = facts.take(100).map { fact ->
            val parts = fact.jsonPointer?.let { locator.matchEntire(it)?.groupValues }
            val record = parts?.get(1)?.toLongOrNull()
            val attempt = parts?.get(2)?.toLongOrNull()
            val ref = refs.takeIf { it.isArray }?.firstOrNull {
                it.path("archiveRecordId").isIntegralNumber && it.path("archiveRecordId").asLong() == record &&
                    it.path("attemptId").isIntegralNumber && it.path("attemptId").asLong() == attempt &&
                    it.path("bodySha256").asText() == fact.responseSha256
            }?.let { savedRef ->
                mapOf("archiveRecordId" to record, "attemptId" to attempt, "bodySha256" to fact.responseSha256,
                    "sourceExecutionId" to savedRef.path("sourceExecutionId").longValue(),
                    "sourceName" to savedRef.path("sourceName").asText(), "sourceItemKey" to savedRef.path("sourceItemKey").asText())
            }
            mapOf("provider" to fact.provider, "field" to fact.field, "value" to fact.value,
                "excerpt" to fact.excerpt, "sourceUrl" to fact.sourceUrl, "responseSha256" to fact.responseSha256,
                "fetchedAt" to fact.fetchedAt.toString(), "sourceUpdatedAt" to fact.sourceUpdatedAt?.toString(),
                "assertionSource" to fact.assertionSource, "temporalScope" to fact.temporalScope,
                "jsonPointer" to fact.jsonPointer,
                "page" to parts?.get(3)?.toIntOrNull()?.takeIf { it > 0 },
                "paragraph" to parts?.get(4)?.toIntOrNull()?.takeIf { it > 0 }, "sourceRef" to ref)
        }
        return mapper.valueToTree(mapOf("state" to state, "total" to facts.size,
            "truncated" to (facts.size > projected.size), "items" to projected))
    }

    /** Manifest is paginated frozen identity, never a current ES lookup or an unbounded summary field. */
    fun recheckManifest(id: Long, page: Int, size: Int): RecheckPage<RecheckManifestItem> {
        requireRecheck(id)
        recheckPage(page, size)
        val result = repository.recheckItems(id, page, size)
        return RecheckPage(result.first, page, size, result.second.map { item ->
            val input = mapper.readTree(item.snapshotJson).path("reviewInput")
            RecheckManifestItem(item.id, item.expertDocId, item.identityHash, item.factFingerprint, item.reviewEpoch,
                input.get("expectedAdmissionRevision"), input.path("evidenceHash").asText(null),
                input.path("paperSourceRefs"), item.state)
        })
    }

    private fun recheckPage(page: Int, size: Int) { require(page >= 0 && size in setOf(20, 50, 100)) }

    private fun summaryRow(id: Long, run: VerificationRun?, counts: VerificationCounts, status: String?, asOf: Instant,
        suggestion: Triple<Long, Long, Long>?, attempts: VerificationAttemptChannels?) =
        VerificationSummary(id, if (run == null) "NOT_RECORDED" else "RECORDED", if (run == null) null else counts,
            run?.let { lifecycle(it, counts) }, run?.revision, asOf, run?.mode, status ?: if (run != null) "EXPIRED" else null,
            run?.let { suggested(it, suggestion) }, run?.let { apolloAccess(it) }, attempts?.takeIf { run != null })

    /** A damaged snapshot is unknown, never an implicit free-mode snapshot or a read of current settings. */
    private fun apolloAccess(run: VerificationRun): FrozenApolloAccess? = try {
        run.frozenApolloAccess(mapper)
    } catch (_: com.fasterxml.jackson.core.JsonProcessingException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun lifecycle(run: VerificationRun, counts: VerificationCounts): String {
        if (run.recheckJson != null) return repository.recheck(run).control.phase.name
        return when {
            counts.queued > 0 -> "ACTIVE"
            !run.inputClosed -> "COLLECTING"
            else -> "COMPLETED"
        }
    }

    private fun suggested(run: VerificationRun, values: Triple<Long, Long, Long>?): VerificationSuggestions? =
        if (run.mode != VerificationMode.AUDIT) null else VerificationSuggestions(
            values?.first ?: 0, values?.second ?: 0, values?.third ?: 0)

    private fun record(item: VerificationItem, decision: String?, revision: Long?,
        snapshot: VerificationSnapshot = mapper.readValue(item.snapshotJson),
        payload: VerificationWorkerEvidence? = workerPayload(item)
    ): VerificationRecord {
        // Use the shared pure rules only for the explanation of missing fields, never to rewrite the recorded result.
        // A damaged per-item normalization must not fail the whole list: keep the saved state and flag the field.
        val missing = if (payload == null) listOf("institutionSubject") else try {
            rules.evaluate(snapshot, payload.evidence, payload.search,
                institutionNormalization = payload.institutionNormalization, ruleVersion = payload.ruleVersion).missingFields
        } catch (_: IllegalArgumentException) {
            listOf("institutionSubject")
        }
        return VerificationRecord(item.id, item.expertDocId, snapshot.profile.displayName, snapshot.profile.email,
            snapshot.profile.institution, item.sourceName, item.batchNumber, item.route,
            item.outcome?.name ?: when (item.state) {
                VerificationState.ERROR -> "ERROR"
                VerificationState.CANCELLED -> "CANCELLED"
                else -> "QUEUED"
            },
            missing, reasonCodes(item), item.updatedAt, decision, item.projectionState, revision, item.qualification,
            reasonCodes(item).firstOrNull { it in MATCH_REASONS }?.let { reason ->
                reasonLabel(reason) + if (DiscoveryInstitutionNormalizer.TUM_ALIAS_REASON in reasonCodes(item))
                    " · TUM 官网英德名称对应" else ""
            },
            payload?.search?.matchingVersion ?: 1, item.reviewEpoch)
    }

    /** Malformed optional evidence is a per-row rendering problem; snapshot/SQL failures remain fatal. */
    private fun workerPayload(item: VerificationItem): VerificationWorkerEvidence? = try {
        item.evidenceJson?.let { mapper.readValue<VerificationWorkerEvidence>(it) } ?: VerificationWorkerEvidence()
    } catch (_: com.fasterxml.jackson.core.JsonProcessingException) {
        null
    }

    private fun reasonCodes(item: VerificationItem): List<String> = item.reasonsJson?.let { mapper.readValue<List<String>>(it).distinct() } ?: emptyList()
    private fun reasons(filter: VerificationReportFilter): List<VerificationReason> =
        aggregateReasons(repository.reportingReasons(filter))

    private fun aggregateReasons(rows: List<Pair<String, Long>>): List<VerificationReason> {
        val totals = sortedMapOf<String, Long>()
        rows.forEach { (json, count) ->
            mapper.readValue<List<String>>(json).distinct().forEach { totals[it] = (totals[it] ?: 0) + count }
        }
        return totals.map { (code, count) -> VerificationReason(code, reasonLabel(code), count) }
    }
    private fun validatePage(page: Int, size: Int) { require(page >= 0 && size in 1..100) }
    private fun reasonLabel(code: String): String = REASON_LABELS[code] ?: code

    companion object {
        private val MATCH_REASONS = setOf("APOLLO_CURRENT_INSTITUTION_MATCH", "APOLLO_HISTORICAL_INSTITUTION_MATCH",
            "APOLLO_PROFESSOR_MATCH", "APOLLO_SPECIALTY_MATCH")
        private val REASON_LABELS = mapOf(
            "APOLLO_MATCH_POLICY_V2" to "匹配准入规则 V2",
            DiscoveryInstitutionNormalizer.TUM_ALIAS_REASON to "机构别名：慕尼黑工业大学（TUM 官网英德名称，V1）",
            "APOLLO_CURRENT_INSTITUTION_MATCH" to "当前任职匹配",
            "APOLLO_HISTORICAL_INSTITUTION_MATCH" to "历史任职匹配",
            "APOLLO_PROFESSOR_MATCH" to "教授职称匹配",
            "APOLLO_SPECIALTY_MATCH" to "专业辅助匹配",
            "QUALIFICATION_MET" to "资格已明确", "QUALIFICATION_EVIDENCE_MISSING" to "资格证据缺失",
            "IDENTITY_AMBIGUOUS" to "身份存在歧义", "APOLLO_NO_MATCH" to "未找到候选人",
            "APOLLO_PERSON_MATCHED" to "已确认 Apollo 本人", "IDENTITY_EVIDENCE_MISSING" to "身份证据缺失",
            "CURRENT_INSTITUTION_EVIDENCE_MISSING" to "当前机构证据缺失", "IDENTITY_NOT_MATCHED" to "身份未匹配",
            "AGE_REQUIREMENT_NOT_MET" to "年龄及职称条件不符", "EVIDENCE_CONFLICT" to "证据冲突",
            "NAME_CONFLICT" to "姓名冲突", "AGE_CONFLICT" to "年龄证据冲突", "INSTITUTION_CONFLICT" to "机构冲突",
            "ORCID_CONFLICT" to "ORCID 冲突", "APOLLO_ID_CONFLICT" to "Apollo 标识冲突",
            "FACT_CHANGED" to "输入事实已变化", "PROTECTED" to "保护跳过", "SEARCH_INCOMPLETE" to "查询尚未完成",
            "SEARCH_PAGE_LIMIT" to "查询未完成：达到单次查询页数上限",
            "ITEM_REQUEST_LIMIT" to "核验未完成：达到单档案请求上限",
            "EVIDENCE_INCOMPLETE" to "证据尚未完整", "CONFIG_AUTH" to "接口认证配置错误",
            "REQUEST_INVALID" to "接口请求无效", "RESPONSE_INVALID" to "接口响应无效",
            "APOLLO_CREDITS_EXHAUSTED" to "额度不足，等待恢复", "CREDITS_WAIT" to "额度不足，等待恢复",
            "PAID_DETAILS_DISABLED" to "付费详情未启用，证据仍不完整",
            "SUPERSEDED_BY_REVIEW" to "已由人工审核决定取代")
    }
}

data class VerificationCapabilities(val enabled: Boolean, val workerEnabled: Boolean, val mode: VerificationMode,
    val ready: Boolean, val apolloDefault: Boolean, val reasonCodes: List<String>,
    val countrySupplementEnabled: Boolean = false, val apolloMatchingVersion: Int = 2)
data class VerificationSuggestions(val autoAdmitted: Long, val directAdmitted: Long, val apolloAdmitted: Long)
data class VerificationReason(val code: String, val label: String, val count: Long)
data class VerificationSummary(val executionId: Long, val coverage: String, val counts: VerificationCounts?,
    val lifecycle: String?, val snapshotVersion: Long?, val asOf: Instant, val mode: VerificationMode?,
    val collectionStatus: String?, val suggested: VerificationSuggestions?,
    val apolloAccess: FrozenApolloAccess? = null, val apolloAttempts: VerificationAttemptChannels? = null)
data class VerificationBatch(val batchNumber: Int, val counts: VerificationCounts, val suggested: VerificationSuggestions?,
    val cumulativeProfiles: Long, val updatedAt: Instant?, val reasons: List<VerificationReason>)
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
    val qualification: VerificationQualification, val matchingLabel: String? = null, val matchingVersion: Int = 1,
    val reviewEpoch: Long = 0)
data class VerificationAttemptSummary(val id: Long, val provider: VerificationProvider, val queryKind: VerificationQueryKind,
    val pageNumber: Int, val attemptNumber: Int, val requestedAt: Instant, val completedAt: Instant?,
    val httpStatus: Int?, val resultCode: String, val responseSha256: String?, val diagnostic: VerificationDiagnostic? = null)
data class VerificationItemDetail(val record: VerificationRecord, val executionId: Long, val state: VerificationState,
    val outcome: VerificationOutcome?, val qualification: VerificationQualification,
    val identity: com.weibo.talentintroduction.expert.domain.IdentityVerification?, val originalEmployment: String?,
    val institutionEvidence: String?, val boundOrcid: String?, val evidence: List<VerificationEvidence>,
    val reasons: List<VerificationReason>, val attempts: List<VerificationAttemptSummary>, val admissionRevision: Long?,
    val projectionError: String?, val mode: VerificationMode, val snapshotVersion: Long, val asOf: Instant,
    val apolloPersonId: String?, val apolloStages: VerificationApolloStages, val matchingVersion: Int = 1,
    val matchedCandidate: ApolloCandidate? = null, val academicResearchFields: String? = null,
    val diagnostics: WorkerDiagnostics? = null, val reviewEpoch: Long = 0,
    val recheck: RecheckRun? = null, val steps: List<VerificationStep> = emptyList())
data class VerificationApolloStages(val enteredAt: Instant?, val searchedAt: Instant?,
    val candidateFoundAt: Instant?, val matchedAt: Instant?)

data class RecheckPage<T>(val total: Long, val page: Int, val size: Int, val items: List<T>)
data class RecheckRunView(val runId: Long, val taskId: Long, val requestKey: String, val actor: String,
    val phase: RecheckPhase, val mode: VerificationMode, val evidenceMode: RecheckEvidenceMode, val revision: Long,
    val rosterHash: String?, val specHash: String?, val scope: String, val filter: com.fasterxml.jackson.databind.JsonNode?,
    val inputClosed: Boolean, val counts: com.weibo.talentintroduction.discovery.repository.RecheckCounts,
    val prepareTask: com.weibo.talentintroduction.discovery.repository.RecheckTask?, val evaluatedAt: Instant,
    val codeVersion: String, val parserVersion: String, val settings: com.fasterxml.jackson.databind.JsonNode,
    val createdAt: Instant, val updatedAt: Instant)
data class RecheckAdmissionView(val decision: String, val revision: Long)
data class RecheckItemView(val itemId: Long, val runId: Long, val docId: String, val batchNumber: Int,
    val state: VerificationState, val givenNames: String?, val familyNames: String?, val email: String?,
    val identityHash: String, val factFingerprint: String, val before: com.fasterxml.jackson.databind.JsonNode,
    val proposed: com.fasterxml.jackson.databind.JsonNode?, val current: RecheckAdmissionView?,
    val applied: RecheckAdmissionView?, val outcome: VerificationOutcome?, val qualification: VerificationQualification,
    val route: VerificationRoute, val projection: VerificationProjection, val projectionError: String?,
    val actualAdmitted: Boolean, val requestCount: Int,
    val trace: com.weibo.talentintroduction.discovery.repository.VerificationTraceRead, val allReasons: List<String>,
    val sourceRefs: com.fasterxml.jackson.databind.JsonNode, val evidenceHash: String?, val baselineItemId: Long?,
    val versions: List<com.fasterxml.jackson.databind.JsonNode>, val recordedResult: com.fasterxml.jackson.databind.JsonNode?,
    val updatedAt: Instant, val evidence: com.fasterxml.jackson.databind.JsonNode? = null)
data class RecheckManifestItem(val itemId: Long, val docId: String, val identityHash: String,
    val factFingerprint: String, val reviewEpoch: Long, val expectedAdmissionRevision: com.fasterxml.jackson.databind.JsonNode?,
    val evidenceHash: String?, val sourceRefs: com.fasterxml.jackson.databind.JsonNode, val state: VerificationState)
