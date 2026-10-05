package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewExpertPage
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewExpertRow
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewIdentity
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
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
import java.util.Base64

/**
 * 03（I-1/I-2）：审核名单的**唯一** ES 读取 + 服务端筛选点。
 *
 * 「列表」(`GET /experts`) 与「全页快照」(`prepare scope=ALL_MATCHING`) 共用同一份
 * `level/tag/q/issue/decision` 语义与同一份 admission 批量合并逻辑，04 的存量初始化也只复用这里，
 * 不另写第二套条件（计划 §实现方案 1 / 下游接口）。
 *
 * 硬约束：
 * - 全页快照走服务端 ES scroll，每批默认 500，**每批只读一次 admission**，逐人查库被禁止；
 * - 全页快照只收「当前筛选内 `NEEDS_REVIEW`」，HOLD/REJECTED/已批准不被默认覆盖（I-2）。
 */
@Service
class DiscoveryReviewScanService(
    private val repository: DiscoveryReviewRepository,
    private val policy: DiscoveryAdmissionPolicy,
    private val writer: ExpertIndexWriterService,
    private val restTemplate: RestTemplate,
    private val properties: ElasticsearchProperties,
    private val expertIndexService: ExpertIndexService,
    private val objectMapper: ObjectMapper
) {

    /** 统一筛选条件：列表与全页快照共用（`decision` 为 null 表示不限）。 */
    data class DiscoveryReviewFilter(
        val level: ExpertIndexLevel,
        val tag: String?,
        val q: String?,
        val issue: String?,
        val decision: String?
    )

    /** 一条完整 ES 命中：真实 `_id` + 全量 `_source` + 真实 seq/term（禁止用缺字段的旧 DTO 冒充事实）。 */
    data class ScanHit(
        val docId: String,
        val source: Map<String, Any?>,
        val seqNo: Long,
        val primaryTerm: Long
    )

    /** 全页扫描的一行：事实来源 + 准入结论所需的完整档案。 */
    data class ScanExpert(
        val docId: String,
        val level: ExpertIndexLevel,
        val source: Map<String, Any?>,
        val seqNo: Long,
        val primaryTerm: Long,
        val profile: ExpertProfile,
        val row: DiscoveryReviewExpertRow
    )

    private data class EsPage(val hits: List<ScanHit>, val total: Long)

    // ── 列表（与原 02 服务逐字同义） ─────────────────────────────────────────

    /**
     * `GET /experts`：服务端过滤，不只过滤当前页。
     *
     * 无跨 MySQL 过滤（issue/decision）时直接按 ES 分页 + 精确 total；有跨 MySQL 过滤时按
     * 500 一批扫描全部基础命中，批量取准入结论后精确过滤/计数，再切页。扫描有超时/取消边界，
     * 超时抛 [DiscoveryReviewTimeoutException]（明确错误，不给不完整总数）。
     */
    fun listPage(
        level: ExpertIndexLevel,
        tag: String?,
        from: Int,
        size: Int,
        q: String?,
        issue: String?,
        decision: String?
    ): DiscoveryReviewExpertPage {
        require(from >= 0) { "from 必须 >= 0" }
        require(size in 1..MAX_PAGE_SIZE) { "size 必须在 1～$MAX_PAGE_SIZE" }
        val filter = DiscoveryReviewFilter(
            level = level,
            tag = tag,
            q = q,
            issue = issue,
            decision = decision?.takeIf { it.isNotBlank() && it != FILTER_ALL }
        )
        val issueFilter = issue?.takeIf { it.isNotBlank() && it != FILTER_ALL }
        if (filter.decision == null && issueFilter == null) {
            val page = searchPage(filter, from, size)
            return DiscoveryReviewExpertPage(page.total, from, size, enrich(page.hits, level).map { it.row })
        }
        val deadline = System.nanoTime() + SCAN_TIMEOUT_NANOS
        val matched = mutableListOf<ScanExpert>()
        var offset = 0
        while (true) {
            checkScanBudget(deadline)
            if (offset >= ES_MAX_WINDOW) {
                throw DiscoveryReviewTimeoutException("审核范围超过服务端可精确统计窗口，请缩小筛选范围后重试")
            }
            val page = searchPage(filter, offset, SCAN_BATCH_SIZE)
            if (page.hits.isEmpty()) break
            matched += enrich(page.hits, level).filter { matchesDecision(it.row, filter.decision) && matchesIssue(it.row, issueFilter) }
            offset += page.hits.size
            if (offset >= page.total || page.hits.size < SCAN_BATCH_SIZE) break
        }
        return DiscoveryReviewExpertPage(
            total = matched.size.toLong(),
            from = from,
            size = size,
            experts = matched.drop(from).take(size).map { it.row }
        )
    }

    // ── 全页快照扫描（I-1） ──────────────────────────────────────────────────

    /**
     * 服务端 ES scroll 全量扫描：每批默认 500，读到一批即回调 [handler]（返回 false 提前停止）。
     * 无论成功/失败/提前停止，`finally` 一定清理 scroll 上下文（与
     * `ExpertSearchService.scrollExpertsFiltered` 同款 5m scroll + finally clear）。
     */
    fun scanAll(
        filter: DiscoveryReviewFilter,
        batchSize: Int = SCAN_BATCH_SIZE,
        handler: (List<ScanExpert>) -> Boolean
    ) {
        require(batchSize in 1..MAX_SCAN_BATCH) { "batchSize 必须在 1～$MAX_SCAN_BATCH" }
        val index = expertIndexService.indexName(filter.level)
        val decisionFilter = filter.decision?.takeIf { it.isNotBlank() && it != FILTER_ALL }
        val issueFilter = filter.issue?.takeIf { it.isNotBlank() && it != FILTER_ALL }
        var scrollId: String? = null
        try {
            var response: JsonNode? = restTemplate.exchange(
                "${properties.baseUrl}/$index/_search?scroll=$SCROLL_TTL",
                HttpMethod.POST,
                HttpEntity(searchBody(filter, batchSize, from = null), headers()),
                JsonNode::class.java
            ).body
            scrollId = response?.path("_scroll_id")?.asText()?.takeIf { it.isNotBlank() }
            while (response != null) {
                val hits = parseHits(response)
                if (hits.isEmpty()) break
                val experts = enrich(hits, filter.level).filter {
                    matchesDecision(it.row, decisionFilter) && matchesIssue(it.row, issueFilter)
                }
                if (experts.isNotEmpty() && !handler(experts)) break
                if (hits.size < batchSize) break
                response = restTemplate.exchange(
                    "${properties.baseUrl}/_search/scroll",
                    HttpMethod.POST,
                    HttpEntity(mapOf("scroll" to SCROLL_TTL, "scroll_id" to scrollId), headers()),
                    JsonNode::class.java
                ).body
                response?.path("_scroll_id")?.asText()?.takeIf { it.isNotBlank() }?.let { scrollId = it }
            }
        } finally {
            if (scrollId != null) {
                try {
                    restTemplate.exchange(
                        "${properties.baseUrl}/_search/scroll",
                        HttpMethod.DELETE,
                        HttpEntity(mapOf("scroll_id" to scrollId), headers()),
                        JsonNode::class.java
                    )
                } catch (_: Exception) {}
            }
        }
    }

    // ── 内部：ES 查询 / 命中解析 / admission 批量合并 ─────────────────────────

    private fun searchPage(filter: DiscoveryReviewFilter, from: Int, size: Int): EsPage {
        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(filter.level)}/_search",
            HttpMethod.POST,
            HttpEntity(searchBody(filter, size, from = from), headers()),
            JsonNode::class.java
        ).body ?: return EsPage(emptyList(), 0L)
        return EsPage(parseHits(response), response.path("hits").path("total").path("value").asLong(0L))
    }

    private fun searchBody(filter: DiscoveryReviewFilter, size: Int, from: Int?): Map<String, Any> {
        val filters = mutableListOf<Map<String, Any>>()
        filter.tag?.trim()?.takeIf { it.isNotEmpty() }?.let { filters += mapOf("term" to mapOf("tags" to it)) }
        filter.q?.trim()?.takeIf { it.isNotEmpty() }?.let { query ->
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
        val body = mutableMapOf<String, Any>(
            "size" to size,
            "_source" to SOURCE_FIELDS,
            "query" to queryClause,
            "sort" to listOf(mapOf("_doc" to mapOf("order" to "asc")))
        )
        if (from != null) body["from"] = from
        return body
    }

    private fun parseHits(response: JsonNode): List<ScanHit> =
        response.path("hits").path("hits").map { hit ->
            @Suppress("UNCHECKED_CAST")
            ScanHit(
                docId = hit.path("_id").asText(""),
                source = objectMapper.convertValue(hit.path("_source"), Map::class.java) as Map<String, Any?>,
                seqNo = hit.path("_seq_no").asLong(0L),
                primaryTerm = hit.path("_primary_term").asLong(0L)
            )
        }

    /** 每批一次 admission 读取；批次内逐人只做纯计算。 */
    private fun enrich(hits: List<ScanHit>, level: ExpertIndexLevel): List<ScanExpert> {
        if (hits.isEmpty()) return emptyList()
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
            ScanExpert(
                docId = hit.docId,
                level = level,
                source = hit.source,
                seqNo = hit.seqNo,
                primaryTerm = hit.primaryTerm,
                profile = profile,
                row = DiscoveryReviewExpertRow(
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
                    initialized = stored != null,
                    decision = decision,
                    decisionManual = decisionManual,
                    identityChanged = identityChanged,
                    reviewedActor = reviewedItem?.actor,
                    reviewedAt = reviewedItem?.appliedAt,
                    addressWarning = addressWarning(profile.email)
                )
            )
        }
    }

    private fun identityHashOf(docId: String, profile: ExpertProfile): String =
        DiscoveryReviewIdentity.hash(docId, profile.email, profile.givenNames, profile.familyNames)

    private fun matchesDecision(row: DiscoveryReviewExpertRow, filter: String?): Boolean =
        if (filter == "UNINITIALIZED") !row.initialized else filter == null || row.decision == filter

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

    private fun headers(): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            val raw = "${properties.username}:${properties.password}"
            set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8)))
        }

    companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_SCAN_BATCH = 500
        const val SCAN_BATCH_SIZE = 500
        const val FILTER_ALL = "ALL"
        const val FILTER_ANY = "ANY"
        private const val SCROLL_TTL = "5m"
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
