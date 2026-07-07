package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery.openalex", name = ["enabled"], havingValue = "true")
class OpenAlexDataSource(
    @Qualifier("openAlexRestTemplate") private val restTemplate: RestTemplate,
    private val properties: OpenAlexProperties,
    private val europePmc: EuropePmcDataSource,
    private val pdfEmailExtractor: PdfEmailExtractor
) : AcademicDataSource {

    private val log = LoggerFactory.getLogger(OpenAlexDataSource::class.java)

    override val sourceName = "OPENALEX"
    override val emailExtractionMethod = "FULLTEXT_XML"
    override val maxPapersPerSource: Int
        get() = properties.maxPapersPerSource

    override fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult {
        val filter = buildFilter(criteria)
        var url = "${properties.baseUrl}/works?filter=$filter&per_page=${criteria.pageSize}"
        if (criteria.cursor != null) url += "&cursor=${criteria.cursor}"
        else url += "&cursor=*"
        if (properties.politeEmail.isNotBlank()) url += "&mailto=${properties.politeEmail}"

        val response = try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            restTemplate.getForObject(url, JsonNode::class.java)
        } catch (e: Exception) {
            log.error("OpenAlex search failed: {}", e.message)
            throw e
        }
        return parseResponse(response)
    }

    override fun extractAuthorEmails(paper: PaperMetadata): EmailExtractionOutcome {
        if (paper.pmcId != null) {
            val europePmcOutcome = europePmc.extractAuthorEmails(paper)
            return europePmcOutcome.copy(methodUsed = "FULLTEXT_XML")
        }
        if (paper.downloadUrl != null) {
            val result = pdfEmailExtractor.extract(paper.downloadUrl, paper.authors, sourceName)
            return result.copy(methodUsed = "PDF_PARSE")
        }
        return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_PMC_ID")
    }

    private fun buildFilter(criteria: PaperSearchCriteria): String {
        val parts = mutableListOf<String>()
        if (criteria.openAccessOnly) parts += "is_oa:true"
        parts += "publication_year:${criteria.publicationYearFrom}-${criteria.publicationYearTo}"
        if (criteria.excludeCountries.isNotEmpty()) {
            for (country in criteria.excludeCountries) parts += "authorships.institutions.country_code:!$country"
        }
        if (criteria.keywords.isNotEmpty()) {
            parts += "title_and_abstract.search:${criteria.keywords.joinToString("|")}"
        }
        return parts.joinToString(",")
    }

    private fun parseResponse(response: JsonNode?): PaperSearchResult {
        if (response == null) return PaperSearchResult(emptyList(), null, 0)
        val nextCursor = response.path("meta").path("next_cursor").asText(null)
        val totalResults = response.path("meta").path("count").asLong(0)
        val papers = response.path("results").mapNotNull { node ->
            try {
                val doi = node.path("doi").asText(null)?.removePrefix("https://doi.org/")
                val pmcId = node.path("ids").path("pmcid")?.asText(null)
                    ?.removePrefix("https://www.ncbi.nlm.nih.gov/pmc/articles/")
                val pmid = node.path("ids").path("pmid")?.asText(null)
                    ?.removePrefix("https://pubmed.ncbi.nlm.nih.gov/")
                val pdfUrl = node.path("best_oa_location").path("pdf_url").asText(null)
                val authors = node.path("authorships").map { authorship ->
                    val author = authorship.path("author")
                    val orcid = author.path("orcid").asText(null)?.removePrefix("https://orcid.org/")
                    val nameParts = author.path("display_name").asText("").split(" ", limit = 2)
                    val institution = authorship.path("institutions").firstOrNull()
                    PaperAuthor(
                        givenNames = nameParts.getOrNull(0), familyNames = nameParts.getOrNull(1),
                        orcidId = orcid, affiliation = institution?.path("display_name")?.asText(null),
                        isCorresponding = authorship.path("is_corresponding").asBoolean(false)
                    )
                }
                PaperMetadata(pmcId = pmcId, pmid = pmid, doi = doi,
                    title = node.path("title").asText(""), pubYear = node.path("publication_year").asInt(0),
                    journal = node.path("primary_location").path("source").path("display_name").asText(null),
                    authors = authors, source = sourceName, downloadUrl = pdfUrl)
            } catch (e: Exception) { log.debug("Failed to parse OpenAlex: {}", e.message); null }
        }
        return PaperSearchResult(papers, nextCursor, totalResults)
    }

    fun enrichAuthor(openAlexAuthorId: String): AuthorEnrichment? {
        val url = "${properties.baseUrl}/authors/$openAlexAuthorId" +
            if (properties.politeEmail.isNotBlank()) "?mailto=${properties.politeEmail}" else ""
        return try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            val response = restTemplate.getForObject(url, JsonNode::class.java) ?: return null
            parseAuthorEnrichmentFromNode(response, fetchWorksAndPatents = true)
        } catch (e: HttpStatusCodeException) {
            val code = e.statusCode.value()
            if (code == 429 || code == 503) throw e
            log.debug("OpenAlex author enrichment failed for {}: {} (HTTP {})", openAlexAuthorId, e.message, code)
            null
        } catch (e: Exception) {
            log.debug("OpenAlex author enrichment failed for {}: {}", openAlexAuthorId, e.message)
            null
        }
    }

    private fun fetchRecentWorks(worksUrl: String, limit: Int): List<String>? {
        val url = "$worksUrl?sort=publication_year:desc&per_page=$limit&select=title,publication_year" +
            if (properties.politeEmail.isNotBlank()) "&mailto=${properties.politeEmail}" else ""
        return try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            val response = restTemplate.getForObject(url, JsonNode::class.java) ?: return null
            response.path("results")
                .mapNotNull { it.path("title").asText(null)?.takeIf { title -> title.isNotBlank() } }
                .takeIf { it.isNotEmpty() }
        } catch (e: HttpStatusCodeException) {
            val code = e.statusCode.value()
            if (code == 429 || code == 503) throw e
            log.debug("OpenAlex recent works fetch failed for {}: {} (HTTP {})", worksUrl, e.message, code)
            null
        } catch (e: Exception) {
            log.debug("OpenAlex recent works fetch failed for {}: {}", worksUrl, e.message)
            null
        }
    }

    private fun fetchPatents(worksUrl: String, limit: Int): List<String>? {
        val url = "$worksUrl?filter=type:patent&per_page=$limit&select=title,publication_year" +
            if (properties.politeEmail.isNotBlank()) "&mailto=${properties.politeEmail}" else ""
        return try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            val response = restTemplate.getForObject(url, JsonNode::class.java) ?: return null
            response.path("results")
                .mapNotNull { it.path("title").asText(null)?.takeIf { title -> title.isNotBlank() } }
                .takeIf { it.isNotEmpty() }
        } catch (e: HttpStatusCodeException) {
            val code = e.statusCode.value()
            if (code == 429 || code == 503) throw e
            log.debug("OpenAlex patents fetch failed for {}: {} (HTTP {})", worksUrl, e.message, code)
            null
        } catch (e: Exception) {
            log.debug("OpenAlex patents fetch failed for {}: {}", worksUrl, e.message)
            null
        }
    }

    fun enrichAuthorByOrcid(orcid: String): AuthorEnrichment? {
        return when (val outcome = enrichAuthorByOrcidWithReason(orcid)) {
            is EnrichmentOutcome.Success -> outcome.data
            else -> null
        }
    }

    fun enrichAuthorByOrcidWithReason(orcid: String): EnrichmentOutcome {
        val searchUrl = "${properties.baseUrl}/authors?filter=orcid:$orcid" +
            if (properties.politeEmail.isNotBlank()) "&mailto=${properties.politeEmail}" else ""
        return try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            val searchResponse = restTemplate.getForObject(searchUrl, JsonNode::class.java)
            val authorId = searchResponse?.path("results")?.get(0)?.path("id")?.asText(null)
                ?.removePrefix("https://openalex.org/")
            if (authorId == null) {
                return EnrichmentOutcome.NotFound
            }
            val enrichment = enrichAuthor(authorId)
            if (enrichment != null) EnrichmentOutcome.Success(enrichment) else EnrichmentOutcome.NotFound
        } catch (e: HttpStatusCodeException) {
            val code = e.statusCode.value()
            if (code == 429 || code == 503) {
                val retryAfter = e.responseHeaders?.getFirst("Retry-After")?.toLongOrNull()?.times(1000)
                return EnrichmentOutcome.RateLimited(retryAfter)
            }
            log.debug("OpenAlex ORCID lookup failed for {}: {} (HTTP {})", orcid, e.message, code)
            EnrichmentOutcome.ApiError("HTTP $code: ${e.message}")
        } catch (e: Exception) {
            log.debug("OpenAlex ORCID lookup failed for {}: {}", orcid, e.message)
            EnrichmentOutcome.ApiError(e.message ?: "unknown error")
        }
    }

    fun batchEnrichByOrcids(orcids: List<String>): Map<String, EnrichmentOutcome> {
        if (orcids.isEmpty()) return emptyMap()

        val filterValue = orcids.joinToString("|")
        val url = "${properties.baseUrl}/authors?filter=orcid:$filterValue&per_page=${orcids.size}" +
            if (properties.politeEmail.isNotBlank()) "&mailto=${properties.politeEmail}" else ""

        if (properties.enrichmentDelayMs > 0) Thread.sleep(properties.enrichmentDelayMs)

        val response = try {
            restTemplate.getForObject(url, JsonNode::class.java)
        } catch (e: HttpStatusCodeException) {
            val code = e.statusCode.value()
            if (code == 429 || code == 503) {
                val retryAfter = e.responseHeaders?.getFirst("Retry-After")?.toLongOrNull()?.times(1000)
                return orcids.associateWith { EnrichmentOutcome.RateLimited(retryAfter) }
            }
            return orcids.associateWith { EnrichmentOutcome.ApiError("HTTP $code") }
        } catch (e: Exception) {
            return orcids.associateWith { EnrichmentOutcome.ApiError(e.message ?: "unknown") }
        }

        val foundEntries = mutableListOf<Pair<String, JsonNode>>()
        response?.path("results")?.forEach { node ->
            val orcid = node.path("orcid").asText(null)
                ?.removePrefix("https://orcid.org/") ?: return@forEach
            foundEntries += orcid to node
        }

        val results = mutableMapOf<String, EnrichmentOutcome>()
        for (orcid in orcids) {
            if (foundEntries.none { it.first == orcid }) {
                results[orcid] = EnrichmentOutcome.NotFound
            }
        }

        val needsWorksOrPatents = properties.fetchWorksEnabled || properties.fetchPatentsEnabled
        for ((index, entry) in foundEntries.withIndex()) {
            val (orcid, node) = entry
            val worksApiUrl = node.path("works_api_url").asText(null)
            val baseEnrichment = parseAuthorEnrichmentFromNode(node, fetchWorksAndPatents = false)

            if (!needsWorksOrPatents || worksApiUrl == null) {
                results[orcid] = EnrichmentOutcome.Success(baseEnrichment)
                continue
            }

            try {
                val recentWorkTitles = if (properties.fetchWorksEnabled) fetchRecentWorks(worksApiUrl, 3) else null
                val patentTitles = if (properties.fetchPatentsEnabled) fetchPatents(worksApiUrl, 3) else null
                results[orcid] = EnrichmentOutcome.Success(
                    baseEnrichment.copy(recentWorkTitles = recentWorkTitles, patentTitles = patentTitles)
                )
            } catch (e: HttpStatusCodeException) {
                val code = e.statusCode.value()
                if (code == 429 || code == 503) {
                    val retryAfter = e.responseHeaders?.getFirst("Retry-After")?.toLongOrNull()?.times(1000)
                    results[orcid] = EnrichmentOutcome.Success(baseEnrichment)
                    val rateLimited = EnrichmentOutcome.RateLimited(retryAfter)
                    for (remaining in foundEntries.drop(index + 1)) {
                        if (remaining.first !in results) {
                            results[remaining.first] = rateLimited
                        }
                    }
                    break
                }
                results[orcid] = EnrichmentOutcome.Success(baseEnrichment)
            }
        }

        return orcids.associateWith { results[it] ?: EnrichmentOutcome.NotFound }
    }

    private fun parseAuthorEnrichmentFromNode(node: JsonNode, fetchWorksAndPatents: Boolean): AuthorEnrichment {
        val topics = node.path("topics")
            .takeIf { it.isArray }
            ?.sortedByDescending { it.path("count").asInt(0) }
            ?.take(5)
            ?.mapNotNull { it.path("display_name").asText(null) }
        val worksUrl = node.path("works_api_url").asText(null)
        val recentWorkTitles = if (fetchWorksAndPatents && worksUrl != null) fetchRecentWorks(worksUrl, limit = 3) else null
        val patentTitles = if (fetchWorksAndPatents && worksUrl != null) fetchPatents(worksUrl, limit = 3) else null
        return AuthorEnrichment(
            hIndex = node.path("summary_stats").path("h_index").let { if (it.isInt) it.asInt() else null },
            citationCount = node.path("cited_by_count").let { if (it.isInt) it.asInt() else null },
            worksCount = node.path("works_count").let { if (it.isInt) it.asInt() else null },
            topics = topics,
            recentWorkTitles = recentWorkTitles,
            patentTitles = patentTitles
        )
    }
}

sealed class EnrichmentOutcome {
    data class Success(val data: AuthorEnrichment) : EnrichmentOutcome()
    object NotFound : EnrichmentOutcome()
    data class ApiError(val message: String) : EnrichmentOutcome()
    data class RateLimited(val retryAfterMs: Long? = null) : EnrichmentOutcome()
}

data class AuthorEnrichment(
    val hIndex: Int?,
    val citationCount: Int?,
    val worksCount: Int?,
    val topics: List<String>? = null,
    val recentWorkTitles: List<String>? = null,
    val patentTitles: List<String>? = null
)
