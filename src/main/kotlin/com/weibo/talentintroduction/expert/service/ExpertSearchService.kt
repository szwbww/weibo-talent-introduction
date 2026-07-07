package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.Base64

@Service
class ExpertSearchService(
    private val restTemplate: RestTemplate,
    private val properties: ElasticsearchProperties,
    private val expertIndexService: ExpertIndexService
) {
    companion object {
        val ALLOWED_HAS_FIELDS = setOf("employment", "degree", "institution", "researchFields", "patentTitles")

        fun notContactedWithEmailFilters(emailDomain: String? = null): List<Map<String, Any>> {
            val filters = mutableListOf(
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
            return filters
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
        hasField: List<String>? = null
    ): ExpertSearchResult {
        require(size in 1..1000) { "size must be between 1 and 1000" }
        require(from >= 0) { "from must be >= 0" }

        val filters = buildExpertFilters(
            tag, operatorStatus, emailDomain, region, hIndexMin, citationCountMin, recentYears, hasField
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
            institution = source.nullableText("institution"),
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
            enrichmentSource = source.nullableText("enrichmentSource")
        )
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
            "researchFields", "institution",
            "emailSource", "emailVerifiedLevel",
            "dataSource", "externalIds", "worksCount",
            "tags",
            "updatedAt",
            "operatorStatus",
            "recentWorkTitles", "patentTitles", "enrichedAt", "enrichmentSource"
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
        hasField: List<String>? = null
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
            filters.add(mapOf("exists" to mapOf("field" to field)))
        }

        return filters
    }

    private fun regionFilter(region: String): Map<String, Any> {
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
