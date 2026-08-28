package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Base64

@Service
class ExpertSearchService(
    private val restTemplate: RestTemplate,
    private val properties: ElasticsearchProperties,
    private val expertIndexService: ExpertIndexService
) {
    private val log = LoggerFactory.getLogger(ExpertSearchService::class.java)
    companion object {
        val ALLOWED_HAS_FIELDS = setOf("employment", "degree", "institution", "researchFields", "patentTitles", "recentWorkTitles")

        private val CLASSIFIED_AT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val CLASSIFIED_AT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        /**
         * Fields whose ES mapping type is `keyword`, so an empty-string term can
         * reliably exclude documents holding blank values. `text` fields are not
         * listed here: `term ""` against them matches tokenized content and may
         * wrongly exclude documents that have values (I-12).
         */
        val BLANK_EXCLUDABLE_FIELDS = setOf(
            "researchFields", "recentWorkTitles", "patentTitles", "degree", "country"
        )

        /**
         * Existence filter for a hasField-style query. For `keyword` fields the
         * `exists` check is combined with `must_not term ""` so blank values do
         * not count as present; for `text` fields a bare `exists` is used (I-9).
         */
        fun fieldPresenceFilter(field: String): Map<String, Any> =
            if (field in BLANK_EXCLUDABLE_FIELDS) {
                mapOf(
                    "bool" to mapOf(
                        "must" to listOf(mapOf("exists" to mapOf("field" to field))),
                        "must_not" to listOf(mapOf("term" to mapOf(field to "")))
                    )
                )
            } else {
                mapOf("exists" to mapOf("field" to field))
            }

        /**
         * I4a-2: 门禁字段之间是 AND —— 每个字段产出一个独立 filter，由调用方平铺进
         * bool.filter。空集合返回空列表（I4a-1）。
         * 调用方必须已把字段裁剪到 [ALLOWED_HAS_FIELDS] 之内（I4a-3）；此处仍保留
         * require 作为兜底，越界即 fail-fast，不静默忽略。
         */
        fun fieldPresenceFilters(fields: List<String>): List<Map<String, Any>> =
            fields.distinct().map {
                require(it in ALLOWED_HAS_FIELDS) { "Invalid gate ES field: $it" }
                fieldPresenceFilter(it)
            }

        val ALLOWED_DISCIPLINES = setOf("STEM", "HUMANITIES", "UNCLASSIFIED")

        fun disciplineFilter(discipline: String): Map<String, Any> {
            require(discipline in ALLOWED_DISCIPLINES) { "Invalid discipline: $discipline" }
            return when (discipline) {
                "UNCLASSIFIED" -> mapOf(
                    "bool" to mapOf(
                        "must_not" to listOf(mapOf("exists" to mapOf("field" to "disciplineCategory")))
                    )
                )
                else -> mapOf("term" to mapOf("disciplineCategory" to discipline))
            }
        }

        /**
         * I1-1: 研发类型筛选取值白名单 —— 唯一权威声明处。
         * 从 [ExpertType] 枚举派生（禁止手写六值名单，M-2）；`UNCLASSIFIED` 是字面量，
         * 语义为 `expertClassification.type` 字段**不存在**（见 [expertTypePredicate]）。
         */
        val ALLOWED_EXPERT_TYPES: Set<String> =
            ExpertType.values().map { it.name }.toSet() + "UNCLASSIFIED"

        /**
         * I1-2/I1-3: N 个研发类型取 OR，产出**单个** bool.should + minimum_should_match:1
         * 的 filter 项；空集合返回 null（I1-2，调用方不得追加，禁止产出
         * `should: [] + minimum_should_match: 1`）。逐字照 [operatorStatusesFilter] 的
         * map/trim/filter/distinct 结构。
         */
        fun expertTypesFilter(types: List<String>): Map<String, Any>? {
            val values = types.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (values.isEmpty()) return null
            values.forEach { require(it in ALLOWED_EXPERT_TYPES) { "Invalid expert type: $it" } }
            return mapOf(
                "bool" to mapOf(
                    "should" to values.map { expertTypePredicate(it) },
                    "minimum_should_match" to 1
                )
            )
        }

        /**
         * I4-2: 发信目标的 fail-closed 表达 —— 类型集合为空时追加它，命中恒为 0。
         * 只允许用在 INTRODUCTION 的发信目标查询上；列表页等"空 = 不限"的调用点禁止使用。
         */
        val MATCH_NONE_FILTER: Map<String, Any> = mapOf(
            "bool" to mapOf("must_not" to listOf(mapOf("match_all" to emptyMap<String, Any>())))
        )

        /**
         * I1-1: 单个研发类型的**纯谓词** —— 只判定类型本身，不得在分支内混入
         * `exists email` 之类的 AND 语义条件，否则其余 should 分支会绕过它
         * （K-batch-multi-value-filter-seams）。`UNCLASSIFIED` = `expertClassification.type`
         * 字段不存在（must_not exists），不是某个字符串 term。
         */
        private fun expertTypePredicate(type: String): Map<String, Any> =
            if (type == "UNCLASSIFIED") {
                mapOf(
                    "bool" to mapOf(
                        "must_not" to listOf(mapOf("exists" to mapOf("field" to "expertClassification.type")))
                    )
                )
            } else {
                mapOf("term" to mapOf("expertClassification.type" to type))
            }

        fun regionFilter(region: String): Map<String, Any> {
            if (region == CountryContinentMapping.REGION_OTHER) {
                val knownValues = CountryContinentMapping.allKnownEsTermValues().toList()
                return mapOf(
                    "bool" to mapOf(
                        "should" to listOf(
                            mapOf(
                                "bool" to mapOf(
                                    "must" to listOf(mapOf("exists" to mapOf("field" to "country"))),
                                    "must_not" to listOf(mapOf("terms" to mapOf("country" to knownValues)))
                                )
                            ),
                            mapOf(
                                "bool" to mapOf(
                                    "must" to listOf(mapOf("exists" to mapOf("field" to "nationality"))),
                                    "must_not" to listOf(mapOf("terms" to mapOf("nationality" to knownValues)))
                                )
                            )
                        ),
                        "minimum_should_match" to 1
                    )
                )
            }

            val countryValues = CountryContinentMapping.countriesForRegion(region).toList()
            return mapOf(
                "bool" to mapOf(
                    "should" to listOf(
                        mapOf("terms" to mapOf("country" to countryValues)),
                        mapOf("terms" to mapOf("nationality" to countryValues))
                    ),
                    "minimum_should_match" to 1
                )
            )
        }

        /** 多选地区：并集（should + minimum_should_match 1）。空列表返回 null 表示不限制。 */
        fun regionsFilter(regions: List<String>): Map<String, Any>? {
            if (regions.isEmpty()) return null
            regions.forEach { require(it in CountryContinentMapping.allRegions()) { "Invalid region: $it" } }
            if (regions.size == 1) return regionFilter(regions.first())
            return mapOf(
                "bool" to mapOf(
                    "should" to regions.map { regionFilter(it) },
                    "minimum_should_match" to 1
                )
            )
        }

        /**
         * I2a-3: N 个邮箱域取 OR，产出**单个** filter 项；空集合返回 null（I2a-2，
         * 调用方不得追加）。照 [regionsFilter] 的 should + minimum_should_match 范式。
         */
        fun emailDomainsFilter(emailDomains: List<String>): Map<String, Any>? {
            val domains = emailDomains.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (domains.isEmpty()) return null
            return mapOf(
                "bool" to mapOf(
                    "should" to domains.map {
                        mapOf("wildcard" to mapOf("email" to mapOf("value" to "*@$it")))
                    },
                    "minimum_should_match" to 1
                )
            )
        }

        /**
         * 多域版 [notContactedWithEmailFilters]。**旧单值重载保持原样不动**（N2a-2）——
         * 专家列表等路径仍在用它。
         */
        fun notContactedWithEmailDomainsFilters(
            emailDomains: List<String> = emptyList(),
            discipline: String? = null
        ): List<Map<String, Any>> {
            val filters = mutableListOf<Map<String, Any>>(
                mapOf("exists" to mapOf("field" to "email")),
                mapOf("bool" to mapOf(
                    "must_not" to listOf(
                        mapOf("exists" to mapOf("field" to "operatorStatus")),
                        mapOf("term" to mapOf("operatorStatus" to "EMAIL_INVALID"))
                    )
                ))
            )
            emailDomainsFilter(emailDomains)?.let { filters.add(it) }
            if (!discipline.isNullOrBlank()) {
                filters.add(disciplineFilter(discipline))
            }
            return filters
        }

        fun notContactedWithEmailFilters(
            emailDomain: String? = null,
            discipline: String? = null
        ): List<Map<String, Any>> {
            val filters = mutableListOf<Map<String, Any>>(
                mapOf("exists" to mapOf("field" to "email")),
                mapOf("bool" to mapOf(
                    "must_not" to listOf(
                        mapOf("exists" to mapOf("field" to "operatorStatus")),
                        mapOf("term" to mapOf("operatorStatus" to "EMAIL_INVALID"))
                    )
                ))
            )
            if (!emailDomain.isNullOrBlank()) {
                filters.add(mapOf("wildcard" to mapOf("email" to mapOf("value" to "*@$emailDomain"))))
            }
            if (!discipline.isNullOrBlank()) {
                filters.add(disciplineFilter(discipline))
            }
            return filters
        }

        /**
         * I-3: NOT_CONTACTED 语义唯一 —— 复用 [notContactedWithEmailFilters] 的 must_not exists
         * 表达（= ES 文档无 operatorStatus 字段），其余状态走 term。两处活体旁路
         * （buildEsFiltersForLevel / matchesExpert）共用此实现，
         * 禁止在别处另写 `term operatorStatus=NOT_CONTACTED`。
         */
        fun operatorStatusFilter(status: String): List<Map<String, Any>> {
            if (status == "NOT_CONTACTED") {
                return notContactedWithEmailFilters(null)
            }
            return listOf(mapOf("term" to mapOf("operatorStatus" to status)))
        }

        /**
         * I3a-2: 单个状态的**纯谓词** —— 只判定状态本身，不夹带 exists email /
         * EMAIL_INVALID 排除等 AND 语义条件，因此可安全放进 bool.should 分支。
         *
         * NOT_CONTACTED = ES 文档无 operatorStatus 字段（I3a-1）。与
         * [notContactedWithEmailFilters] 的 `must_not [exists, term EMAIL_INVALID]` 逻辑等价：
         * `term operatorStatus=EMAIL_INVALID` 蕴含 `exists operatorStatus`，
         * 故 NOT(exists) AND NOT(term) ≡ NOT(exists)。
         */
        fun operatorStatusPredicate(status: String): Map<String, Any> =
            if (status == "NOT_CONTACTED") {
                mapOf("bool" to mapOf(
                    "must_not" to listOf(mapOf("exists" to mapOf("field" to "operatorStatus")))
                ))
            } else {
                mapOf("term" to mapOf("operatorStatus" to status))
            }

        /**
         * I3a-3: N 个状态取 OR，产出**单个** filter 项；空集合返回 null（调用方不得追加）。
         * 照 [regionsFilter] / [emailDomainsFilter] 的 should + minimum_should_match 范式。
         */
        fun operatorStatusesFilter(statuses: List<String>): Map<String, Any>? {
            val values = statuses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (values.isEmpty()) return null
            return mapOf(
                "bool" to mapOf(
                    "should" to values.map { operatorStatusPredicate(it) },
                    "minimum_should_match" to 1
                )
            )
        }
    }

    fun searchExperts(
        size: Int,
        level: ExpertIndexLevel,
        tag: String? = null,
        sortBy: String? = null,
        from: Int = 0,
        operatorStatus: String? = null,
        emailDomain: String? = null,
        region: String? = null,
        hIndexMin: Int? = null,
        citationCountMin: Int? = null,
        recentYears: Int? = null,
        hasField: List<String>? = null,
        discipline: String? = null,
        expertTypes: List<String> = emptyList()
    ): ExpertSearchResult {
        require(size in 1..1000) { "size must be between 1 and 1000" }
        require(from >= 0) { "from must be >= 0" }

        val filters = buildExpertFilters(
            tag, operatorStatus, emailDomain, region, hIndexMin, citationCountMin, recentYears, hasField, discipline, expertTypes
        )

        val query = if (filters.isEmpty()) {
            mapOf("match_all" to emptyMap<String, Any>())
        } else {
            mapOf("bool" to mapOf("filter" to filters))
        }

        val sort = if (sortBy == "updatedAt") {
            listOf(dateSort("updatedAt"))
        } else {
            sortFields(level)
        }

        val requestBody = mapOf(
            "from" to from,
            "size" to size,
            // ES 默认 track_total_hits=10000，超过 1 万条时 total 固定为 10000（relation=gte）；
            // 这里需要精确总数用于前端分页
            "track_total_hits" to true,
            "_source" to sourceFields(),
            "query" to query,
            "sort" to sort
        )

        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return ExpertSearchResult(emptyList(), 0L)

        val totalHits = response.path("hits").path("total").path("value").asLong(0L)
        val experts = response.path("hits")
            .path("hits")
            .map { hit -> toExpertProfile(hit) }
        return ExpertSearchResult(experts = experts, totalHits = totalHits)
    }

    fun searchExpertsWithEmail(
        size: Int,
        level: ExpertIndexLevel = ExpertIndexLevel.CANDIDATE
    ): ExpertSearchResult {
        require(size in 1..1000) { "size must be between 1 and 1000" }

        val requestBody = mapOf(
            "size" to size,
            "_source" to sourceFields(),
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf("exists" to mapOf("field" to "email"))
                    )
                )
            ),
            "sort" to sortFields(level)
        )

        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return ExpertSearchResult(emptyList(), 0L)

        val totalHits = response.path("hits").path("total").path("value").asLong(0L)
        val experts = response.path("hits")
            .path("hits")
            .map { hit -> toExpertProfile(hit) }
        return ExpertSearchResult(experts = experts, totalHits = totalHits)
    }

    fun scrollExperts(
        level: ExpertIndexLevel,
        batchSize: Int = 500,
        handler: (List<ExpertProfile>) -> Boolean
    ) {
        scrollExperts(level, batchSize) { batch, _, _ -> handler(batch) }
    }

    fun scrollExperts(
        level: ExpertIndexLevel,
        batchSize: Int = 500,
        handler: (batch: List<ExpertProfile>, batchNumber: Int, totalHits: Long) -> Boolean
    ) {
        val index = expertIndexService.indexName(level)
        var scrollId: String? = null

        try {
            val initialUrl = "${properties.baseUrl}/$index/_search?scroll=5m"
            val requestBody = mapOf(
                "size" to batchSize,
                "_source" to sourceFields(),
                "query" to mapOf("match_all" to emptyMap<String, Any>()),
                "sort" to listOf(mapOf("_doc" to "asc"))
            )
            var response = restTemplate.exchange(
                initialUrl,
                HttpMethod.POST,
                HttpEntity(requestBody, headers()),
                JsonNode::class.java
            ).body ?: return

            scrollId = response.path("_scroll_id").asText()
            val totalHits = response.path("hits").path("total").path("value").asLong(0)
            var batchNumber = 0

            do {
                val hits = response.path("hits").path("hits")
                if (hits.isEmpty) break

                batchNumber++
                val experts = hits.map { hit -> toExpertProfile(hit) }
                val shouldContinue = handler(experts, batchNumber, totalHits)
                if (!shouldContinue) break
                if (hits.size() < batchSize) break

                response = restTemplate.exchange(
                    "${properties.baseUrl}/_search/scroll",
                    HttpMethod.POST,
                    HttpEntity(mapOf("scroll" to "5m", "scroll_id" to scrollId), headers()),
                    JsonNode::class.java
                ).body ?: break

                scrollId = response.path("_scroll_id").asText()
            } while (true)
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

    private fun toExpertProfile(hit: JsonNode): ExpertProfile {
        val source = hit.path("_source").takeUnless(JsonNode::isMissingNode) ?: hit
        val esDocId = hit.path("_id").asText(null)
        return ExpertProfile(
            esDocId = esDocId,
            orcidId = source.nullableText("orcidId")
                ?: source.nullableText("orcid")
                ?: source.nullableText("id")
                ?: "",
            email = source.nullableText("email"),
            givenNames = source.nullableText("givenNames"),
            familyNames = source.nullableText("familyNames"),
            country = source.nullableText("country"),
            keyword = source.nullableText("keyword"),
            employment = source.nullableText("employment"),
            age = source.path("age").let { if (it.isInt) it.asInt() else null },
            degree = source.nullableText("degree"),
            nationality = source.nullableText("nationality"),
            hIndex = source.path("hIndex").let { if (it.isInt) it.asInt() else null },
            citationCount = source.path("citationCount").let { if (it.isInt) it.asInt() else null },
            lastPublicationYear = source.path("lastPublicationYear").let { if (it.isInt) it.asInt() else null },
            researchFields = source.nullableText("researchFields"),
            disciplineCategory = source.nullableText("disciplineCategory"),
            institution = source.nullableText("institution"),
            institutionType = source.nullableText("institutionType")?.takeIf { it.isNotBlank() },
            emailSource = source.nullableText("emailSource"),
            emailVerifiedLevel = source.path("emailVerifiedLevel").let { if (it.isInt) it.asInt() else null },
            dataSource = source.nullableText("dataSource"),
            externalIds = source.path("externalIds").let { if (it.isObject) it.toString() else null },
            worksCount = source.path("worksCount").let { if (it.isInt) it.asInt() else null },
            tags = source.path("tags").takeIf { it.isArray }
                ?.map { it.asText() }
                ?.filter { it.isNotBlank() },
            updatedAt = source.nullableText("updatedAt"),
            operatorStatus = source.nullableText("operatorStatus"),
            recentWorkTitles = source.path("recentWorkTitles").takeIf { it.isArray }
                ?.map { it.asText() }?.filter { it.isNotBlank() },
            patentTitles = source.path("patentTitles").takeIf { it.isArray }
                ?.map { it.asText() }?.filter { it.isNotBlank() },
            enrichedAt = source.nullableText("enrichedAt"),
            enrichmentSource = source.nullableText("enrichmentSource"),
            expertClassification = parseExpertClassification(source.path("expertClassification"))
        )
    }

    /**
     * 显式解析 ES 中的 `expertClassification` 对象（I1-5）。缺失/null → null；
     * 未知 `type` 记录 warn 并将整个对象视为 null（fail closed，绝不悄悄映射为 UNKNOWN）。
     * `sendable` 不读自 ES：领域 getter 恒由 type 派生，ES 中不可信的 sendable 值无法覆盖。
     */
    private fun parseExpertClassification(node: JsonNode): ExpertClassification? {
        if (node.isMissingNode || node.isNull) return null
        val typeName = node.nullableText("type") ?: return null
        val type = try {
            ExpertType.valueOf(typeName)
        } catch (e: IllegalArgumentException) {
            log.warn(
                "Unknown expertClassification type '{}'; treating whole classification as null (fail closed)",
                typeName
            )
            return null
        }
        val productionScore = node.path("productionScore").takeIf { it.isIntegralNumber }?.asInt() ?: return null
        val researchScore = node.path("researchScore").takeIf { it.isIntegralNumber }?.asInt() ?: return null
        val positiveEvidence = stringArrayOrNull(node, "positiveEvidence") ?: return null
        val negativeEvidence = stringArrayOrNull(node, "negativeEvidence") ?: return null
        val version = node.nullableText("version") ?: return null
        val sourceFingerprint = node.nullableText("sourceFingerprint") ?: return null
        val classifiedAt = parseClassifiedAt(node.path("classifiedAt")) ?: return null
        return ExpertClassification(
            type = type,
            productionScore = productionScore,
            researchScore = researchScore,
            positiveEvidence = positiveEvidence,
            negativeEvidence = negativeEvidence,
            version = version,
            sourceFingerprint = sourceFingerprint,
            classifiedAt = classifiedAt
        )
    }

    private fun stringArrayOrNull(node: JsonNode, field: String): List<String>? {
        val array = node.path(field)
        if (array.isMissingNode || array.isNull) return null
        if (!array.isArray) return null
        if (!array.all(JsonNode::isTextual)) return null
        return array.map { it.asText() }
    }

    /** 支持 mapping 声明的三种格式：`yyyy-MM-dd HH:mm:ss`、`yyyy-MM-dd`、epoch_millis。 */
    private fun parseClassifiedAt(node: JsonNode): LocalDateTime? {
        if (node.isMissingNode || node.isNull) return null
        if (node.isIntegralNumber) {
            return try {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(node.asLong()), ZoneId.systemDefault())
            } catch (e: DateTimeException) {
                null
            }
        }
        if (!node.isTextual) return null
        val raw = node.asText()
        return try {
            LocalDateTime.parse(raw, CLASSIFIED_AT_TIME_FORMATTER)
        } catch (e: DateTimeParseException) {
            try {
                LocalDate.parse(raw, CLASSIFIED_AT_DATE_FORMATTER).atStartOfDay()
            } catch (e2: DateTimeParseException) {
                null
            }
        }
    }

    private fun JsonNode.nullableText(field: String): String? {
        val node = path(field)
        return if (node.isMissingNode || node.isNull) null else node.asText()
    }

    private fun sourceFields(): List<String> =
        listOf(
            "orcidId", "orcid", "id",
            "email", "givenNames", "familyNames",
            "country", "keyword", "employment",
            "age", "degree", "nationality",
            "hIndex", "citationCount", "lastPublicationYear",
            "researchFields", "disciplineCategory", "institution", "institutionType",
            "emailSource", "emailVerifiedLevel",
            "dataSource", "externalIds", "worksCount",
            "tags",
            "updatedAt",
            "operatorStatus",
            "recentWorkTitles", "patentTitles", "enrichedAt", "enrichmentSource",
            "expertClassification"
        )

    private fun sortFields(level: ExpertIndexLevel): List<Map<String, Any>> =
        when (level) {
            ExpertIndexLevel.CANDIDATE ->
                listOf(dateSort("candidateValidatedAt"))

            ExpertIndexLevel.APPLICATION ->
                listOf(dateSort("applicationPromotedAt"), dateSort("lastReplyAt"))

            ExpertIndexLevel.RAW ->
                listOf(mapOf("_doc" to mapOf("order" to "asc")))
        }

    private fun dateSort(field: String): Map<String, Any> =
        mapOf(
            field to mapOf(
                "order" to "desc",
                "missing" to "_last",
                "unmapped_type" to "date"
            )
        )

    private fun headers(): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set(HttpHeaders.AUTHORIZATION, basicAuthHeader())
        }

    private fun basicAuthHeader(): String {
        val raw = "${properties.username}:${properties.password}"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        return "Basic $encoded"
    }

    fun searchByOrcidIds(
        orcidIds: List<String>,
        level: ExpertIndexLevel = ExpertIndexLevel.CANDIDATE
    ): List<ExpertProfile> {
        if (orcidIds.isEmpty()) return emptyList()
        val index = expertIndexService.indexName(level)
        val requestBody = mapOf(
            "size" to orcidIds.size,
            "_source" to sourceFields(),
            "query" to mapOf(
                "terms" to mapOf(
                    "orcidId" to orcidIds
                )
            )
        )
        val response = restTemplate.exchange(
            "${properties.baseUrl}/$index/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return emptyList()

        return response.path("hits")
            .path("hits")
            .map { hit -> toExpertProfile(hit) }
    }

    fun searchExpertsFiltered(
        level: ExpertIndexLevel,
        filters: List<Map<String, Any>>,
        from: Int,
        size: Int
    ): List<ExpertProfile> {
        require(size in 1..1000) { "size must be between 1 and 1000" }
        require(from >= 0) { "from must be >= 0" }

        val query = if (filters.isEmpty()) {
            mapOf("match_all" to emptyMap<String, Any>())
        } else {
            mapOf("bool" to mapOf("filter" to filters))
        }

        val requestBody = mapOf(
            "from" to from,
            "size" to size,
            "_source" to sourceFields(),
            "query" to query,
            "sort" to sortFields(level)
        )

        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return emptyList()

        return response.path("hits")
            .path("hits")
            .map { hit -> toExpertProfile(hit) }
    }

    fun countExperts(
        level: ExpertIndexLevel,
        filters: List<Map<String, Any>> = emptyList()
    ): Long {
        val query = if (filters.isEmpty()) {
            mapOf("match_all" to emptyMap<String, Any>())
        } else {
            mapOf("bool" to mapOf("filter" to filters))
        }
        val requestBody = mapOf("query" to query)
        val index = expertIndexService.indexName(level)
        val response = restTemplate.exchange(
            "${properties.baseUrl}/$index/_count",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body
        return response?.path("count")?.asLong(0L) ?: 0L
    }

    fun scrollExpertsFiltered(
        level: ExpertIndexLevel,
        filters: List<Map<String, Any>>,
        batchSize: Int = 500,
        handler: (List<ExpertProfile>) -> Boolean
    ) {
        val index = expertIndexService.indexName(level)
        var scrollId: String? = null

        try {
            val initialUrl = "${properties.baseUrl}/$index/_search?scroll=5m"
            val query = if (filters.isEmpty()) {
                mapOf("match_all" to emptyMap<String, Any>())
            } else {
                mapOf("bool" to mapOf("filter" to filters))
            }
            val requestBody = mapOf(
                "size" to batchSize,
                "_source" to sourceFields(),
                "query" to query,
                "sort" to listOf(mapOf("_doc" to "asc"))
            )
            var response = restTemplate.exchange(
                initialUrl,
                HttpMethod.POST,
                HttpEntity(requestBody, headers()),
                JsonNode::class.java
            ).body ?: return

            scrollId = response.path("_scroll_id").asText()
            val totalHits = response.path("hits").path("total").path("value").asLong(0)
            var batchNumber = 0

            do {
                val hits = response.path("hits").path("hits")
                if (hits.isEmpty) break

                batchNumber++
                val experts = hits.map { hit -> toExpertProfile(hit) }
                val shouldContinue = handler(experts)
                if (!shouldContinue) break
                if (hits.size() < batchSize) break

                response = restTemplate.exchange(
                    "${properties.baseUrl}/_search/scroll",
                    HttpMethod.POST,
                    HttpEntity(mapOf("scroll" to "5m", "scroll_id" to scrollId), headers()),
                    JsonNode::class.java
                ).body ?: break

                scrollId = response.path("_scroll_id").asText()
            } while (true)
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

    fun searchAfterExpertsFiltered(
        level: ExpertIndexLevel,
        filters: List<Map<String, Any>>,
        batchSize: Int = 500,
        handler: (List<ExpertProfile>) -> Boolean
    ) {
        val index = expertIndexService.indexName(level)
        var searchAfter: String? = null

        while (true) {
            val query = if (filters.isEmpty()) {
                mapOf("match_all" to emptyMap<String, Any>())
            } else {
                mapOf("bool" to mapOf("filter" to filters))
            }
            val requestBody = mutableMapOf<String, Any>(
                "size" to batchSize,
                "_source" to sourceFields(),
                "query" to query,
                "sort" to listOf(mapOf("orcidId" to "asc"))
            )
            if (searchAfter != null) {
                requestBody["search_after"] = listOf(searchAfter)
            }

            val response = restTemplate.exchange(
                "${properties.baseUrl}/$index/_search",
                HttpMethod.POST,
                HttpEntity(requestBody, headers()),
                JsonNode::class.java
            ).body ?: break

            val hits = response.path("hits").path("hits")
            if (hits.isEmpty) break

            val experts = hits.map { hit -> toExpertProfile(hit) }
            if (!handler(experts)) break
            if (hits.size() < batchSize) break

            searchAfter = hits[hits.size() - 1].path("sort").get(0).asText()
        }
    }

    fun findByOrcidId(orcidId: String, level: ExpertIndexLevel): ExpertProfile? {
        require(orcidId.isNotBlank()) { "orcidId must not be blank" }

        val requestBody = mapOf(
            "size" to 1,
            "_source" to sourceFields(),
            "query" to mapOf("term" to mapOf("orcidId" to orcidId))
        )

        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return null

        val hits = response.path("hits").path("hits")
        if (!hits.isArray || hits.isEmpty) {
            return null
        }
        return toExpertProfile(hits[0])
    }

    fun countByFieldPresence(
        level: ExpertIndexLevel,
        fields: List<String>,
        mode: FieldPresenceMode
    ): Long {
        if (fields.isEmpty()) {
            return countExperts(level, emptyList())
        }
        return countExperts(level, buildFieldPresenceFilters(fields, mode))
    }

    fun findRandomByFieldPresence(
        level: ExpertIndexLevel,
        fields: List<String>,
        mode: FieldPresenceMode
    ): ExpertProfile? {
        val innerQuery = buildFieldPresenceQuery(fields, mode)
        val requestBody = mapOf(
            "size" to 20,
            "_source" to sourceFields(),
            "query" to mapOf(
                "function_score" to mapOf(
                    "query" to innerQuery,
                    "random_score" to emptyMap<String, Any>()
                )
            )
        )

        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return null

        val experts = response.path("hits")
            .path("hits")
            .map { hit -> toExpertProfile(hit) }
        if (experts.isEmpty()) {
            return null
        }
        if (mode == FieldPresenceMode.SATISFY_ALL && fields.isNotEmpty()) {
            val candidates = experts.filter { profile ->
                fields.all { field -> profile.hasNonBlankEsField(field) }
            }
            return candidates.randomOrNull()
        }
        return experts.random()
    }

    private fun buildFieldPresenceQuery(fields: List<String>, mode: FieldPresenceMode): Map<String, Any> =
        if (fields.isEmpty()) {
            mapOf("match_all" to emptyMap<String, Any>())
        } else if (mode == FieldPresenceMode.MISSING_ANY) {
            val bool = buildFieldPresenceFilters(fields, mode).single()["bool"] as Map<String, Any>
            mapOf("bool" to bool)
        } else {
            mapOf("bool" to mapOf("filter" to buildFieldPresenceFilters(fields, mode)))
        }

    private fun buildFieldPresenceFilters(fields: List<String>, mode: FieldPresenceMode): List<Map<String, Any>> =
        when (mode) {
            FieldPresenceMode.SATISFY_ALL -> fields.map { field ->
                fieldPresenceFilter(field)
            }
            FieldPresenceMode.MISSING_ANY -> listOf(
                mapOf(
                    "bool" to mapOf(
                        "should" to fields.map { field ->
                            mapOf(
                                "bool" to mapOf(
                                    "must_not" to listOf(mapOf("exists" to mapOf("field" to field)))
                                )
                            )
                        },
                        "minimum_should_match" to 1
                    )
                )
            )
        }

    private fun ExpertProfile.hasNonBlankEsField(esField: String): Boolean =
        when (esField) {
            "familyNames" -> !familyNames.isNullOrBlank()
            "institution" -> !institution.isNullOrBlank()
            "keyword" -> !keyword.isNullOrBlank()
            "employment" -> !employment.isNullOrBlank()
            "researchFields" -> !researchFields.isNullOrBlank()
            "country" -> !country.isNullOrBlank()
            "degree" -> !degree.isNullOrBlank()
            "hIndex" -> hIndex != null
            "worksCount" -> worksCount != null
            "lastPublicationYear" -> lastPublicationYear != null
            "recentWorkTitles" -> !recentWorkTitles.isNullOrEmpty()
            "patentTitles" -> !patentTitles.isNullOrEmpty()
            else -> true
        }

    fun aggregateTags(
        level: ExpertIndexLevel,
        operatorStatus: String? = null,
        emailDomain: String? = null,
        region: String? = null
    ): List<TagCount> {
        val filters = buildExpertFilters(tag = null, operatorStatus, emailDomain, region)
        val query = if (filters.isEmpty()) {
            mapOf("match_all" to emptyMap<String, Any>())
        } else {
            mapOf("bool" to mapOf("filter" to filters))
        }

        val requestBody = mapOf(
            "size" to 0,
            "query" to query,
            "aggs" to mapOf(
                "tags" to mapOf(
                    "terms" to mapOf(
                        "field" to "tags",
                        "size" to 100
                    )
                )
            )
        )

        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return emptyList()

        val buckets = response.path("aggregations")
            .path("tags")
            .path("buckets")

        return buckets.map { bucket ->
            TagCount(
                tag = bucket.path("key").asText(),
                count = bucket.path("doc_count").asLong()
            )
        }
    }

    fun aggregateRegions(
        level: ExpertIndexLevel,
        tag: String? = null,
        operatorStatus: String? = null,
        emailDomain: String? = null
    ): List<RegionCount> {
        val filters = buildExpertFilters(tag, operatorStatus, emailDomain, region = null)
        val query = if (filters.isEmpty()) {
            mapOf("match_all" to emptyMap<String, Any>())
        } else {
            mapOf("bool" to mapOf("filter" to filters))
        }

        val requestBody = mapOf(
            "size" to 0,
            "query" to query,
            "aggs" to mapOf(
                "countries" to mapOf(
                    "terms" to mapOf(
                        "field" to "country",
                        "size" to 500
                    )
                )
            )
        )

        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return emptyRegionCounts()

        val buckets = response.path("aggregations")
            .path("countries")
            .path("buckets")

        val regionCounts = mutableMapOf<String, Long>()
        buckets.forEach { bucket ->
            val key = bucket.path("key").asText()
            val count = bucket.path("doc_count").asLong()
            val mappedRegion = CountryContinentMapping.toRegion(key)
            regionCounts[mappedRegion] = regionCounts.getOrDefault(mappedRegion, 0L) + count
        }

        return CountryContinentMapping.allRegions().map { regionName ->
            RegionCount(region = regionName, count = regionCounts.getOrDefault(regionName, 0L))
        }
    }

    private fun emptyRegionCounts(): List<RegionCount> =
        CountryContinentMapping.allRegions().map { RegionCount(it, 0L) }

    private fun buildExpertFilters(
        tag: String?,
        operatorStatus: String?,
        emailDomain: String?,
        region: String?,
        hIndexMin: Int? = null,
        citationCountMin: Int? = null,
        recentYears: Int? = null,
        hasField: List<String>? = null,
        discipline: String? = null,
        expertTypes: List<String> = emptyList()
    ): MutableList<Map<String, Any>> {
        val filters = mutableListOf<Map<String, Any>>()

        if (!tag.isNullOrBlank()) {
            filters.add(mapOf("term" to mapOf("tags" to tag)))
        }

        if (!operatorStatus.isNullOrBlank()) {
            when (operatorStatus) {
                "NOT_CONTACTED" -> {
                    filters.addAll(notContactedWithEmailFilters(null))
                }
                else -> {
                    filters.add(mapOf("term" to mapOf("operatorStatus" to operatorStatus)))
                }
            }
        }

        if (!emailDomain.isNullOrBlank()) {
            filters.add(mapOf("wildcard" to mapOf("email" to mapOf("value" to "*@$emailDomain"))))
        }

        if (!region.isNullOrBlank()) {
            filters.add(regionFilter(region))
        }

        hIndexMin?.let { filters.add(mapOf("range" to mapOf("hIndex" to mapOf("gte" to it)))) }
        citationCountMin?.let { filters.add(mapOf("range" to mapOf("citationCount" to mapOf("gte" to it)))) }
        recentYears?.let {
            val cutoff = java.time.Year.now().value - it
            filters.add(mapOf("range" to mapOf("lastPublicationYear" to mapOf("gte" to cutoff))))
        }
        hasField?.forEach { field ->
            require(field in ALLOWED_HAS_FIELDS) { "Invalid hasField: $field" }
            filters.add(fieldPresenceFilter(field))
        }

        if (!discipline.isNullOrBlank()) {
            filters.add(disciplineFilter(discipline))
        }

        expertTypesFilter(expertTypes)?.let { filters.add(it) }

        return filters
    }

    fun aggregateEmailDomains(
        level: ExpertIndexLevel,
        tag: String? = null,
        operatorStatus: String? = null,
        region: String? = null
    ): List<EmailDomainCount> {
        val filters = buildExpertFilters(tag, operatorStatus, emailDomain = null, region)
        filters.add(mapOf("exists" to mapOf("field" to "email")))
        val requestBody = mapOf(
            "size" to 0,
            "query" to mapOf("bool" to mapOf("filter" to filters)),
            "aggs" to mapOf(
                "email_domains" to mapOf(
                    "terms" to mapOf(
                        "field" to "email",
                        "size" to 10000,
                        "script" to mapOf(
                            "source" to "doc['email'].value.substring(doc['email'].value.indexOf('@') + 1)"
                        )
                    )
                )
            )
        )

        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return emptyList()

        val buckets = response.path("aggregations")
            .path("email_domains")
            .path("buckets")

        return buckets.map { bucket ->
            EmailDomainCount(
                domain = bucket.path("key").asText(),
                count = bucket.path("doc_count").asLong()
            )
        }
    }
    /**
     * I2-4: 旧首发链路专用 —— filter 只有两项：exists email 与研发类型集合。
     * 不得追加任何其他条件（主计划 M-1：唯一收口点）。
     * 调用方保证 expertTypes 非空（I2-2），故这里不处理空集合。
     */
    fun searchExpertsByTypesWithEmail(
        size: Int,
        level: ExpertIndexLevel = ExpertIndexLevel.CANDIDATE,
        expertTypes: List<String>
    ): ExpertSearchResult {
        require(size in 1..1000) { "size must be between 1 and 1000" }
        val typesFilter = expertTypesFilter(expertTypes)
            ?: throw IllegalArgumentException("expertTypes must not be empty")

        val requestBody = mapOf(
            "size" to size,
            "_source" to sourceFields(),
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf("exists" to mapOf("field" to "email")),
                        typesFilter
                    )
                )
            ),
            "sort" to sortFields(level)
        )

        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return ExpertSearchResult(emptyList(), 0L)

        val totalHits = response.path("hits").path("total").path("value").asLong(0L)
        val experts = response.path("hits")
            .path("hits")
            .map { hit -> toExpertProfile(hit) }
        return ExpertSearchResult(experts = experts, totalHits = totalHits)
    }

}

data class ExpertSearchResult(
    val experts: List<ExpertProfile>,
    val totalHits: Long
)

data class EmailDomainCount(
    val domain: String,
    val count: Long
)

data class RegionCount(
    val region: String,
    val count: Long
)

data class TagCount(
    val tag: String,
    val count: Long
)

enum class FieldPresenceMode {
    SATISFY_ALL,
    MISSING_ANY
}
