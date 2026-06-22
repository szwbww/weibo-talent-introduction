package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.ElasticsearchProperties
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
        emailDomain: String? = null
    ): ExpertSearchResult {
        require(size in 1..1000) { "size must be between 1 and 1000" }
        require(from >= 0) { "from must be >= 0" }

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
            operatorStatus = source.nullableText("operatorStatus")
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
            "operatorStatus"
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
    fun aggregateEmailDomains(level: ExpertIndexLevel): List<EmailDomainCount> {
        val requestBody = mapOf(
            "size" to 0,
            "query" to mapOf(
                "exists" to mapOf("field" to "email")
            ),
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
