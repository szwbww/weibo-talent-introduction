package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.CrossrefProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder

@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery.crossref", name = ["enabled"], havingValue = "true")
class CrossrefDataSource(
    private val restTemplate: RestTemplate,
    private val properties: CrossrefProperties,
    private val unpaywallClient: UnpaywallClient,
    private val pdfEmailExtractor: PdfEmailExtractor
) : AcademicDataSource {

    private val log = LoggerFactory.getLogger(CrossrefDataSource::class.java)

    init {
        require(unpaywallClient.isConfigured()) {
            "Crossref data source is enabled but Unpaywall email is not configured; " +
                "set talent-introduction.expert-discovery.unpaywall.email"
        }
    }

    override val sourceName = "CROSSREF"
    override val emailExtractionMethod = "PDF_PARSE"
    override val maxPapersPerSource get() = properties.maxPapersPerSource

    override fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult {
        val keywordQuery = if (criteria.keywords.isNotEmpty()) {
            criteria.keywords.joinToString(" ") { it }
        } else {
            null
        }

        val filterParts = mutableListOf<String>()
        filterParts.add("from-pub-date:${criteria.publicationYearFrom}-01-01")
        filterParts.add("until-pub-date:${criteria.publicationYearTo}-12-31")
        filterParts.add("has-full-text:true")

        val filter = filterParts.joinToString(",")

        val urlBuilder = StringBuilder("${properties.baseUrl}/works")
        val params = mutableListOf<String>()
        if (keywordQuery != null) {
            params.add("query=${URLEncoder.encode(keywordQuery, "UTF-8")}")
        }
        params.add("filter=${URLEncoder.encode(filter, "UTF-8")}")
        params.add("rows=${criteria.pageSize}")
        params.add("cursor=${criteria.cursor ?: "*"}")
        if (properties.politeEmail.isNotBlank()) {
            params.add("mailto=${URLEncoder.encode(properties.politeEmail, "UTF-8")}")
        }
        urlBuilder.append("?").append(params.joinToString("&"))

        val response = try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            restTemplate.getForObject(urlBuilder.toString(), JsonNode::class.java)
        } catch (e: Exception) {
            log.error("Crossref search failed: {}", e.message)
            throw e
        }

        return parseResponse(response)
    }

    override fun extractAuthorEmails(paper: PaperMetadata): EmailExtractionOutcome {
        val doi = paper.doi
        if (doi == null) {
            return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_DOI")
        }

        val pdfUrl = unpaywallClient.findPdfUrl(doi)
        if (pdfUrl == null) {
            return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_OA_LOCATION", httpRequests = 1)
        }

        val result = pdfEmailExtractor.extract(pdfUrl, paper.authors, sourceName)
        return result.copy(httpRequests = result.httpRequests + 1) // +1 for Unpaywall lookup
    }

    private fun parseResponse(response: JsonNode?): PaperSearchResult {
        if (response == null) return PaperSearchResult(emptyList(), null, 0)

        val message = response.path("message")
        val nextCursor = message.path("next-cursor").asText(null)
        val totalResults = message.path("total-results").asLong(0)

        val papers = message.path("items").mapNotNull { node ->
            try {
                val doi = node.path("DOI").asText(null)
                val title = node.path("title").firstOrNull()?.asText("") ?: ""
                val pubYear = node.path("published-print").path("date-parts")
                    .firstOrNull()?.firstOrNull()?.asInt()
                    ?: node.path("created").path("date-parts")
                        .firstOrNull()?.firstOrNull()?.asInt()
                    ?: 0
                val journalContainer = node.path("container-title").firstOrNull()
                val journal = if (journalContainer != null && !journalContainer.isNull) journalContainer.asText() else null

                val authors = node.path("author").map { authorNode ->
                    val given = authorNode.path("given").asText(null)
                    val family = authorNode.path("family").asText(null)
                    val orcid = authorNode.path("ORCID").asText(null)
                        ?.removePrefix("https://orcid.org/")
                    val affiliation = authorNode.path("affiliation").firstOrNull()
                        ?.path("name")?.asText(null)

                    PaperAuthor(
                        givenNames = given,
                        familyNames = family,
                        orcidId = orcid,
                        affiliation = affiliation,
                        isCorresponding = false
                    )
                }

                PaperMetadata(
                    pmcId = null,
                    pmid = null,
                    doi = doi,
                    title = title,
                    pubYear = pubYear,
                    journal = journal,
                    authors = authors,
                    source = sourceName
                )
            } catch (e: Exception) {
                log.debug("Failed to parse Crossref work: {}", e.message)
                null
            }
        }

        return PaperSearchResult(papers, nextCursor, totalResults)
    }
}
