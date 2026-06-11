package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery.openalex", name = ["enabled"], havingValue = "true")
class OpenAlexDataSource(
    private val restTemplate: RestTemplate,
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
            AuthorEnrichment(
                hIndex = response.path("summary_stats").path("h_index").let { if (it.isInt) it.asInt() else null },
                citationCount = response.path("cited_by_count").let { if (it.isInt) it.asInt() else null },
                worksCount = response.path("works_count").let { if (it.isInt) it.asInt() else null }
            )
        } catch (e: Exception) {
            log.debug("OpenAlex author enrichment failed for {}: {}", openAlexAuthorId, e.message)
            null
        }
    }

    fun enrichAuthorByOrcid(orcid: String): AuthorEnrichment? {
        val searchUrl = "${properties.baseUrl}/authors?filter=orcid:$orcid" +
            if (properties.politeEmail.isNotBlank()) "&mailto=${properties.politeEmail}" else ""
        return try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            val searchResponse = restTemplate.getForObject(searchUrl, JsonNode::class.java)
            val authorId = searchResponse?.path("results")?.get(0)?.path("id")?.asText(null)
                ?.removePrefix("https://openalex.org/")
            if (authorId != null) enrichAuthor(authorId) else null
        } catch (e: Exception) {
            log.debug("OpenAlex ORCID lookup failed for {}: {}", orcid, e.message)
            null
        }
    }
}

data class AuthorEnrichment(val hIndex: Int?, val citationCount: Int?, val worksCount: Int?)
