package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewAction
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewBatchDetail
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewConfirmItemView
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewConfirmResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewDecision
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewExpertPage
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewExpertRow
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewItemState
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewPrepareItemView
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewPrepareRequest
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewPrepareResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewReasonSnapshot
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewRevokeResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewSnapshot
import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryReviewItem
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewApplyOutcome
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRevokeOutcome
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

/** 02（I-3）：提交的固定快照已变化 / 撤销对象已不是当前有效决策 —— 映射为 409。 */
class DiscoveryReviewConflictException(message: String) : IllegalStateException(message)

/** 02（I-3）：查询超出服务端预算 —— 映射为 503，绝不返回不完整总数。 */
class DiscoveryReviewTimeoutException(message: String) : IllegalStateException(message)

/**
 * 02（I-1～I-4）：深度发现审核的查询 / 准备 / 确认 / 撤销。
 *
 * 边界：读取真实 ES source（[ExpertIndexWriterService.readDiscoveryDocument]）用于展示事实与身份
 * 绑定；持久化只写 [DiscoveryReviewRepository] 的两张表；**不做任何 ES 晋升**（04 接投影），
 * **不发送邮件**，actor 只由调用方从登录会话取得。资格判定复用 01 的
 * [DiscoveryAdmissionPolicy]，本片不另写规则。
 */
@Service
class DiscoveryReviewService(
    private val repository: DiscoveryReviewRepository,
    private val policy: DiscoveryAdmissionPolicy,
    private val writer: ExpertIndexWriterService,
    private val restTemplate: RestTemplate,
    private val properties: ElasticsearchProperties,
    private val expertIndexService: ExpertIndexService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC()
) {

    // ── 查询（I-2/I-3） ───────────────────────────────────────────────────────

    /**
     * `GET /experts`：服务端过滤，不只过滤当前页。
     *
     * 无跨 MySQL 过滤（issue/decision）时直接按 ES 分页 + 精确 total；有跨 MySQL 过滤时按
     * 500 一批扫描全部基础命中，批量取准入结论后精确过滤/计数，再切页。扫描有超时/取消边界，
     * 超时抛 [DiscoveryReviewTimeoutException]（明确错误，不给不完整总数）。
     */
    fun listExperts(
        levelRaw: String?,
        tag: String?,
        from: Int,
        size: Int,
        q: String?,
        issue: String?,
        decision: String?
    ): DiscoveryReviewExpertPage {
        require(from >= 0) { "from 必须 >= 0" }
        require(size in 1..MAX_PAGE_SIZE) { "size 必须在 1～$MAX_PAGE_SIZE" }
        val level = parseLevel(levelRaw)
        val decisionFilter = decision?.takeIf { it.isNotBlank() && it != FILTER_ALL }
        val issueFilter = issue?.takeIf { it.isNotBlank() && it != FILTER_ALL }
        if (decisionFilter == null && issueFilter == null) {
            val page = esSearchPage(level, tag, q, from, size)
            return DiscoveryReviewExpertPage(page.total, from, size, enrich(page.hits, level))
        }
        val deadline = System.nanoTime() + SCAN_TIMEOUT_NANOS
        val matched = mutableListOf<DiscoveryReviewExpertRow>()
        var offset = 0
        while (true) {
            checkScanBudget(deadline)
            if (offset >= ES_MAX_WINDOW) {
                throw DiscoveryReviewTimeoutException("审核范围超过服务端可精确统计窗口，请缩小筛选范围后重试")
            }
            val page = esSearchPage(level, tag, q, offset, SCAN_BATCH_SIZE)
            if (page.hits.isEmpty()) break
            matched += enrich(page.hits, level).filter {
                matchesDecision(it, decisionFilter) && matchesIssue(it, issueFilter)
            }
            offset += page.hits.size
            if (offset >= page.total || page.hits.size < SCAN_BATCH_SIZE) break
        }
        return DiscoveryReviewExpertPage(
            total = matched.size.toLong(),
            from = from,
            size = size,
            experts = matched.drop(from).take(size)
        )
    }

    fun history(docId: String, limit: Int = HISTORY_LIMIT): List<ExpertDiscoveryReviewItem> {
        require(docId.isNotBlank()) { "docId 必填" }
        return repository.findHistory(docId, limit.coerceIn(1, HISTORY_LIMIT))
    }

    fun batchDetail(batchKey: String): DiscoveryReviewBatchDetail {
        require(batchKey.isNotBlank()) { "batchKey 必填" }
        val items = repository.findItemsByBatch(batchKey)
        require(items.isNotEmpty()) { "批次不存在：$batchKey" }
        return DiscoveryReviewBatchDetail(batchKey, repository.batchStateCounts(batchKey), items)
    }

    // ── 准备（I-3/I-4） ──────────────────────────────────────────────────────

    fun prepare(request: DiscoveryReviewPrepareRequest, actor: String): DiscoveryReviewPrepareResult {
        require(actor.isNotBlank()) { "缺少操作者身份" }
        val scope = request.scope?.trim().orEmpty().ifEmpty { SCOPE_IDS }
        require(scope == SCOPE_IDS) { "仅支持 scope=$SCOPE_IDS" }
        val action = parseAction(request.action)
        require(action != DiscoveryReviewAction.REVOKE) { "REVOKE 请使用 /items/{id}/revoke" }
        val docIds = request.docIds.map { it.trim() }.filter { it.isNotEmpty() }
        require(docIds.isNotEmpty()) { "docIds 不能为空" }
        require(docIds.size <= MAX_BATCH_SIZE) { "单批最多 $MAX_BATCH_SIZE 人" }
        require(docIds.distinct().size == docIds.size) { "docIds 不得重复" }
        val note = request.note?.trim()?.takeIf { it.isNotEmpty() }
        if (action == DiscoveryReviewAction.REJECT) {
            require(!note.isNullOrEmpty()) { "拒绝必须填写备注" }
        }
        if (note != null) require(note.length <= NOTE_MAX) { "备注最多 $NOTE_MAX 字符" }

        val level = parseLevel(request.level)
        val now = LocalDateTime.now(clock)
        val batchKey = UUID.randomUUID().toString().replace("-", "")
        val inserted = mutableListOf<ExpertDiscoveryReviewItem>()

        for (docId in docIds) {
            val snapshot = writer.readDiscoveryDocument(level, docId)
                ?: throw IllegalArgumentException("ES 文档不存在：$docId")
            val profile = writer.discoveryProfile(docId, snapshot.source)
            val automatic = policy.evaluate(profile)
            val identityHash = identityHashOf(docId, profile)
            repository.initializeAdmission(docId, identityHash, automatic.status.name, automatic.policyVersion, now)
            val currentRevision = repository.findAdmission(docId)?.revision ?: 0L
            val expectedRevision = request.expectedRevisions[docId] ?: currentRevision

            val snapshotJson = objectMapper.writeValueAsString(
                DiscoveryReviewSnapshot(
                    docId = docId,
                    level = level.name,
                    orcidId = profile.orcidId,
                    email = profile.email,
                    givenNames = profile.givenNames,
                    familyNames = profile.familyNames,
                    institution = profile.institution,
                    country = profile.country,
                    researchFields = profile.researchFields,
                    disciplineCategory = profile.disciplineCategory,
                    institutionEvidence = profile.institutionEvidence,
                    filterResult = profile.filterResult,
                    esSeqNo = snapshot.seqNo,
                    esPrimaryTerm = snapshot.primaryTerm,
                    preparedAt = now.toString()
                )
            )
            val reasonJson = objectMapper.writeValueAsString(
                DiscoveryReviewReasonSnapshot(
                    automaticStatus = automatic.status.name,
                    blockingReasons = automatic.blockingReasons,
                    hints = automatic.hints,
                    policyVersion = automatic.policyVersion
                )
            )
            val itemId = repository.insertItem(
                batchKey = batchKey,
                expertDocId = docId,
                sourceLevel = level.name,
                identityHash = identityHash,
                snapshotHash = DiscoveryIdentity.hash(snapshotJson),
                expectedRevision = expectedRevision,
                action = action.name,
                state = DiscoveryReviewItemState.STAGED.name,
                snapshotJson = snapshotJson,
                reasonSnapshotJson = reasonJson,
                actor = actor,
                note = note,
                previousItemId = null,
                executionId = request.executionId,
                now = now
            )
            inserted += ExpertDiscoveryReviewItem(
                id = itemId, batchKey = batchKey, expertDocId = docId, sourceLevel = level.name,
                identityHash = identityHash, snapshotHash = DiscoveryIdentity.hash(snapshotJson),
                expectedRevision = expectedRevision, action = action.name,
                state = DiscoveryReviewItemState.STAGED.name, snapshotJson = snapshotJson,
                reasonSnapshotJson = reasonJson, actor = actor, note = note, previousItemId = null,
                executionId = request.executionId, errorCode = null, createdAt = now,
                confirmedAt = null, appliedAt = null
            )
        }

        return DiscoveryReviewPrepareResult(
            batchKey = batchKey,
            batchHash = computeBatchHash(inserted),
            itemCount = inserted.size,
            items = inserted.map {
                DiscoveryReviewPrepareItemView(
                    it.expertDocId, it.identityHash, it.expectedRevision, it.snapshotHash, it.state
                )
            }
        )
    }

    // ── 确认（I-3/I-4） ──────────────────────────────────────────────────────

    /**
     * `POST /batches/{batchKey}/confirm`：只接受固定快照（batchHash 由服务端从已存项重算），
     * 逐项独立事务应用；版本或身份不符记 `STALE` 且不覆盖当前结论；重复确认幂等。
     */
    fun confirm(batchKey: String, batchHash: String?, actor: String): DiscoveryReviewConfirmResult {
        require(actor.isNotBlank()) { "缺少操作者身份" }
        require(!batchHash.isNullOrBlank()) { "batchHash 必填" }
        val items = repository.findItemsByBatch(batchKey)
        require(items.isNotEmpty()) { "批次不存在：$batchKey" }
        if (computeBatchHash(items) != batchHash) {
            throw DiscoveryReviewConflictException("批次快照已变化，请重新准备")
        }
        var applied = 0
        var stale = 0
        var failed = 0
        var skipped = 0
        val views = mutableListOf<DiscoveryReviewConfirmItemView>()
        for (item in items) {
            val state = item.state
            if (state != DiscoveryReviewItemState.STAGED.name && state != DiscoveryReviewItemState.READY.name) {
                // 终态项在重复确认时返回同一分类（幂等），不再重复应用。
                when (state) {
                    DiscoveryReviewItemState.APPLIED.name -> applied++
                    DiscoveryReviewItemState.STALE.name -> stale++
                    DiscoveryReviewItemState.FAILED.name -> failed++
                    else -> skipped++
                }
                views += DiscoveryReviewConfirmItemView(item.id, item.expertDocId, state, item.errorCode)
                continue
            }
            val level = parseLevel(item.sourceLevel)
            val snapshot = writer.readDiscoveryDocument(level, item.expertDocId)
            if (snapshot == null) {
                repository.markItemFailed(item.id, "SOURCE_MISSING", LocalDateTime.now(clock))
                failed++
                views += DiscoveryReviewConfirmItemView(item.id, item.expertDocId, DiscoveryReviewItemState.FAILED.name, "SOURCE_MISSING")
                continue
            }
            val profile = writer.discoveryProfile(item.expertDocId, snapshot.source)
            val currentIdentityHash = identityHashOf(item.expertDocId, profile)
            val decision = DiscoveryReviewDecision.forAction(parseAction(item.action)).name
            val outcome = repository.applyItem(
                itemId = item.id,
                currentIdentityHash = currentIdentityHash,
                decision = decision,
                policyVersion = DiscoveryAdmissionPolicy.POLICY_VERSION,
                now = LocalDateTime.now(clock)
            )
            when (outcome) {
                DiscoveryReviewApplyOutcome.APPLIED, DiscoveryReviewApplyOutcome.ALREADY_APPLIED -> {
                    applied++
                    views += DiscoveryReviewConfirmItemView(item.id, item.expertDocId, DiscoveryReviewItemState.APPLIED.name)
                }
                DiscoveryReviewApplyOutcome.STALE -> {
                    stale++
                    views += DiscoveryReviewConfirmItemView(item.id, item.expertDocId, DiscoveryReviewItemState.STALE.name, "STALE")
                }
                DiscoveryReviewApplyOutcome.SKIPPED -> {
                    skipped++
                    views += DiscoveryReviewConfirmItemView(item.id, item.expertDocId, item.state)
                }
                DiscoveryReviewApplyOutcome.NOT_FOUND -> {
                    failed++
                    views += DiscoveryReviewConfirmItemView(item.id, item.expertDocId, DiscoveryReviewItemState.FAILED.name, "NOT_FOUND")
                }
            }
        }
        return DiscoveryReviewConfirmResult(
            batchKey = batchKey,
            total = items.size,
            applied = applied,
            stale = stale,
            failed = failed,
            skipped = skipped,
            items = views
        )
    }

    // ── 撤销（I-2） ──────────────────────────────────────────────────────────

    /**
     * `POST /items/{id}/revoke`：只撤销当前仍然有效的人工决策（否则 409），撤销时**重新运行**
     * 自动校验并按结果回写当前结论（未通过回 `NEEDS_REVIEW`）。
     */
    fun revoke(itemId: Long, actor: String, note: String?): DiscoveryReviewRevokeResult {
        require(actor.isNotBlank()) { "缺少操作者身份" }
        val item = repository.findItem(itemId) ?: throw NoSuchElementException("审核项不存在：$itemId")
        if (item.action == DiscoveryReviewAction.REVOKE.name) {
            throw DiscoveryReviewConflictException("撤销动作不能再次撤销")
        }
        val trimmedNote = note?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedNote != null) require(trimmedNote.length <= NOTE_MAX) { "备注最多 $NOTE_MAX 字符" }

        val level = parseLevel(item.sourceLevel)
        val snapshot = writer.readDiscoveryDocument(level, item.expertDocId)
            ?: throw IllegalStateException("ES 文档不存在，无法重新运行自动校验：${item.expertDocId}")
        val profile = writer.discoveryProfile(item.expertDocId, snapshot.source)
        val automatic = policy.evaluate(profile)
        val identityHash = identityHashOf(item.expertDocId, profile)
        val now = LocalDateTime.now(clock)
        val snapshotJson = objectMapper.writeValueAsString(
            DiscoveryReviewSnapshot(
                docId = item.expertDocId,
                level = level.name,
                orcidId = profile.orcidId,
                email = profile.email,
                givenNames = profile.givenNames,
                familyNames = profile.familyNames,
                institution = profile.institution,
                country = profile.country,
                researchFields = profile.researchFields,
                disciplineCategory = profile.disciplineCategory,
                institutionEvidence = profile.institutionEvidence,
                filterResult = profile.filterResult,
                esSeqNo = snapshot.seqNo,
                esPrimaryTerm = snapshot.primaryTerm,
                preparedAt = now.toString()
            )
        )
        val reasonJson = objectMapper.writeValueAsString(
            DiscoveryReviewReasonSnapshot(
                automaticStatus = automatic.status.name,
                blockingReasons = automatic.blockingReasons,
                hints = automatic.hints,
                policyVersion = automatic.policyVersion
            )
        )
        val (outcome, newItemId) = repository.revokeCurrent(
            currentItemId = itemId,
            actor = actor,
            note = trimmedNote,
            newDecision = automatic.status.name,
            currentIdentityHash = identityHash,
            policyVersion = automatic.policyVersion,
            sourceLevel = level.name,
            snapshotHash = DiscoveryIdentity.hash(snapshotJson),
            snapshotJson = snapshotJson,
            reasonSnapshotJson = reasonJson,
            now = now
        )
        if (outcome != DiscoveryReviewRevokeOutcome.APPLIED || newItemId == null) {
            throw DiscoveryReviewConflictException("该决策已不是当前有效决策，无法撤销")
        }
        val revision = repository.findAdmission(item.expertDocId)?.revision ?: 0L
        return DiscoveryReviewRevokeResult(newItemId, item.expertDocId, automatic.status.name, revision)
    }

    // ── ES 读取 + 准入查询（03 将抽为 ScanService） ───────────────────────────

    private fun enrich(hits: List<EsHit>, level: ExpertIndexLevel): List<DiscoveryReviewExpertRow> {
        val admissions = repository.findAdmissions(hits.map { it.docId }).associateBy { it.expertDocId }
        val reviewedItemIds = admissions.values.mapNotNull { it.decisionItemId }
        val reviewedItems = repository.findItemsByIds(reviewedItemIds).associateBy { it.id }
        return hits.map { hit ->
            val profile = writer.discoveryProfile(hit.docId, hit.source)
            val automatic = policy.evaluate(profile)
            val currentIdentity = identityHashOf(hit.docId, profile)
            val stored = admissions[hit.docId]
            val identityChanged = stored != null && stored.identityHash != currentIdentity
            val useStored = stored != null && !identityChanged
            val decision = if (useStored) stored!!.decision else automatic.status.name
            val decisionManual = useStored && stored!!.decisionEnum.manual
            val reviewedItem = if (decisionManual) stored!!.decisionItemId?.let { reviewedItems[it] } else null
            DiscoveryReviewExpertRow(
                docId = hit.docId,
                level = level.name,
                orcidId = profile.orcidId,
                email = profile.email,
                givenNames = profile.givenNames,
                familyNames = profile.familyNames,
                institution = profile.institution,
                country = profile.country,
                researchFields = profile.researchFields,
                disciplineCategory = profile.disciplineCategory,
                institutionEvidence = profile.institutionEvidence,
                filterResult = profile.filterResult,
                tags = profile.tags.orEmpty(),
                automaticStatus = automatic.status.name,
                automaticReasons = automatic.blockingReasons,
                automaticHints = automatic.hints,
                revision = stored?.revision ?: 0L,
                decision = decision,
                decisionManual = decisionManual,
                identityChanged = identityChanged,
                reviewedActor = reviewedItem?.actor,
                reviewedAt = reviewedItem?.appliedAt,
                addressWarning = addressWarning(profile.email)
            )
        }
    }

    private data class EsHit(val docId: String, val source: Map<String, Any?>)

    private data class EsPage(val hits: List<EsHit>, val total: Long)

    private fun esSearchPage(
        level: ExpertIndexLevel,
        tag: String?,
        q: String?,
        from: Int,
        size: Int
    ): EsPage {
        val filters = mutableListOf<Map<String, Any>>()
        tag?.trim()?.takeIf { it.isNotEmpty() }?.let { filters += mapOf("term" to mapOf("tags" to it)) }
        q?.trim()?.takeIf { it.isNotEmpty() }?.let { query ->
            val escaped = query.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?")
            val wildcard = "*$escaped*"
            filters += mapOf(
                "bool" to mapOf(
                    "should" to listOf("email", "givenNames", "familyNames", "orcidId", "institution")
                        .map { mapOf("wildcard" to mapOf(it to mapOf("value" to wildcard))) },
                    "minimum_should_match" to 1
                )
            )
        }
        val queryClause = if (filters.isEmpty()) {
            mapOf("match_all" to emptyMap<String, Any>())
        } else {
            mapOf("bool" to mapOf("filter" to filters))
        }
        val requestBody = mapOf(
            "from" to from,
            "size" to size,
            "track_total_hits" to true,
            "_source" to SOURCE_FIELDS,
            "query" to queryClause,
            "sort" to listOf(mapOf("_doc" to mapOf("order" to "asc")))
        )
        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return EsPage(emptyList(), 0L)
        val hits = response.path("hits").path("hits").map { hit ->
            @Suppress("UNCHECKED_CAST")
            EsHit(
                hit.path("_id").asText(""),
                objectMapper.convertValue(hit.path("_source"), Map::class.java) as Map<String, Any?>
            )
        }
        return EsPage(hits, response.path("hits").path("total").path("value").asLong(0L))
    }

    private fun headers(): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            val raw = "${properties.username}:${properties.password}"
            set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8)))
        }

    // ── 纯函数辅助 ───────────────────────────────────────────────────────────

    private fun identityHashOf(docId: String, profile: ExpertProfile): String =
        DiscoveryReviewIdentity.hash(docId, profile.email, profile.givenNames, profile.familyNames)

    private fun computeBatchHash(items: List<ExpertDiscoveryReviewItem>): String =
        DiscoveryIdentity.hash(
            items.sortedBy { it.expertDocId }
                .joinToString("\n") { "${it.expertDocId}|${it.expectedRevision}|${it.identityHash}|${it.snapshotHash}|${it.action}" }
        )

    private fun matchesDecision(row: DiscoveryReviewExpertRow, filter: String?): Boolean =
        filter == null || row.decision == filter

    private fun matchesIssue(row: DiscoveryReviewExpertRow, filter: String?): Boolean = when (filter) {
        null -> true
        FILTER_ANY -> row.automaticReasons.isNotEmpty()
        else -> row.automaticReasons.any { it.code == filter }
    }

    private fun checkScanBudget(deadline: Long) {
        if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted) {
            throw DiscoveryReviewTimeoutException("审核查询超出服务端时间预算，请缩小范围后重试")
        }
    }

    private fun addressWarning(email: String?): String? = when {
        email.isNullOrBlank() -> "无可投递地址"
        !EMAIL_REGEX.matches(email.trim()) -> "邮箱格式非法"
        else -> null
    }

    private fun parseLevel(raw: String?): ExpertIndexLevel {
        val value = raw?.trim()?.uppercase().orEmpty().ifEmpty { ExpertIndexLevel.RAW.name }
        return try {
            ExpertIndexLevel.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("不支持的 level：$raw")
        }
    }

    private fun parseAction(raw: String?): DiscoveryReviewAction {
        val value = raw?.trim()?.uppercase().orEmpty()
        return try {
            DiscoveryReviewAction.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("不支持的 action：$raw")
        }
    }

    companion object {
        const val MAX_BATCH_SIZE = 1000
        const val MAX_PAGE_SIZE = 100
        const val NOTE_MAX = 1000
        const val HISTORY_LIMIT = 200
        const val FILTER_ALL = "ALL"
        const val FILTER_ANY = "ANY"
        const val SCOPE_IDS = "IDS"
        private const val SCAN_BATCH_SIZE = 500
        private const val ES_MAX_WINDOW = 10_000
        private const val SCAN_TIMEOUT_NANOS = 20_000_000_000L
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val SOURCE_FIELDS = listOf(
            "orcidId", "email", "givenNames", "familyNames", "country", "age", "degree", "nationality",
            "hIndex", "citationCount", "lastPublicationYear", "researchFields", "researchFieldIds",
            "disciplineCategory", "institution", "institutionType", "emailSource", "externalIds",
            "identityVerification", "institutionEvidence", "filterResult", "tags", "expertClassification"
        )
    }
}
