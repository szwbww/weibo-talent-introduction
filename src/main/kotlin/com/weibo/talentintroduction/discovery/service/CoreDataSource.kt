package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.CoreProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery.core", name = ["enabled"], havingValue = "true")
class CoreDataSource(
    private val restTemplate: RestTemplate,
    private val properties: CoreProperties,
    private val plainTextExtractor: PlainTextEmailExtractor,
    private val pdfEmailExtractor: PdfEmailExtractor
) : AcademicDataSource {

    private val log = LoggerFactory.getLogger(CoreDataSource::class.java)

    init {
        require(properties.apiKey.isNotBlank()) {
            "CORE data source is enabled but talent-introduction.expert-discovery.core.api-key is blank"
        }
    }

    override val sourceName = "CORE"
    override val emailExtractionMethod = "FULLTEXT_TEXT"
    override val maxPapersPerSource get() = properties.maxPapersPerSource

    override fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult {
        val cursor = criteria.cursor
        if (cursor != null) return scrollNextPage(cursor)
        return doInitialSearch(criteria)
    }

    private fun doInitialSearch(criteria: PaperSearchCriteria): PaperSearchResult {
        val queryStr = if (criteria.keywords.isNotEmpty()) criteria.keywords.joinToString(" AND ") else "*"
        val fullQuery = "$queryStr AND yearPublished>=${criteria.publicationYearFrom} AND yearPublished<=${criteria.publicationYearTo}"
        val body = mapOf("q" to fullQuery, "limit" to criteria.pageSize, "scroll" to true)
        val headers = coreHeaders()
        val response = try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            restTemplate.exchange("${properties.baseUrl}/search/works", HttpMethod.POST,
                HttpEntity(body, headers), JsonNode::class.java).body
        } catch (e: Exception) { log.error("CORE search failed: {}", e.message); throw e }
        return parseSearchResponse(response)
    }

    private fun scrollNextPage(scrollId: String): PaperSearchResult {
        val headers = coreHeaders()
        val body = mapOf("scrollId" to scrollId)
        val response = try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            restTemplate.exchange("${properties.baseUrl}/search/scroll", HttpMethod.POST,
                HttpEntity(body, headers), JsonNode::class.java).body
        } catch (e: Exception) { log.error("CORE scroll failed: {}", e.message); throw e }
        return parseSearchResponse(response)
    }

    override fun extractAuthorEmails(paper: PaperMetadata): EmailExtractionOutcome {
        if (paper.fullText != null) {
            val emails = plainTextExtractor.extract(paper.fullText, emptyList())
            if (emails.isNotEmpty()) {
                return EmailExtractionOutcome(
                    associateEmails(emails, paper.authors), "FULLTEXT_TEXT", null, httpRequests = 0
                )
            }
            // fullText had no emails, try PDF if available
            if (paper.downloadUrl != null) {
                return pdfEmailExtractor.extract(paper.downloadUrl, paper.authors, sourceName)
            }
            return EmailExtractionOutcome(emptyList(), "FULLTEXT_TEXT", "NO_EMAIL_IN_FULLTEXT", httpRequests = 0)
        }
        if (paper.downloadUrl != null) {
            return pdfEmailExtractor.extract(paper.downloadUrl, paper.authors, sourceName)
        }
        return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_FULLTEXT")
    }

    private fun associateEmails(emails: List<String>, authors: List<PaperAuthor>): List<AuthorEmail> {
        val uniqueEmails = emails.distinct()
        return uniqueEmails.map { email ->
            val localPart = email.substringBefore("@").lowercase()
            val matched = authors.firstOrNull { author ->
                val family = author.familyNames?.lowercase()?.takeIf { it.isNotBlank() } ?: return@firstOrNull false
                val given = author.givenNames?.lowercase()?.takeIf { it.isNotBlank() } ?: ""
                localPart.contains(family) || (given.isNotBlank() && localPart.contains(given)) || localPart.contains(family.take(1))
            }
            if (matched != null) AuthorEmail(email, matched.givenNames, matched.familyNames, matched.isCorresponding, matched.affiliation, matched.orcidId)
            else if (authors.size == 1 && uniqueEmails.size == 1) AuthorEmail(email, authors[0].givenNames, authors[0].familyNames, authors[0].isCorresponding, authors[0].affiliation, authors[0].orcidId)
            else AuthorEmail(email, null, null, false, null, null)
        }
    }

    private fun parseSearchResponse(response: JsonNode?): PaperSearchResult {
        if (response == null) return PaperSearchResult(emptyList(), null, 0)
        val results = response.path("results")
        val totalResults = response.path("totalHits").asLong(0)
        val scrollId = response.path("scrollId").asText(null)
        val papers = (0 until results.size()).mapNotNull { i ->
            try {
                val node = results[i]
                val doi = node.path("doi").asText(null)
                val title = node.path("title").asText("Unknown Title")
                val pubYear = node.path("yearPublished").asInt(0)
                val fullText = node.path("fullText").asText(null)
                val downloadUrl = node.path("downloadUrl").asText(null)
                val authors = node.path("authors").map { authorNode ->
                    val name = authorNode.path("name").asText("")
                    val parts = name.split(" ", limit = 2)
                    PaperAuthor(givenNames = parts.getOrNull(0), familyNames = parts.getOrNull(1),
                        orcidId = null, affiliation = null, isCorresponding = false)
                }
                PaperMetadata(pmcId = null, pmid = null, doi = doi, title = title,
                    pubYear = pubYear, journal = node.path("publisher").asText(null),
                    authors = authors, source = sourceName, fullText = fullText, downloadUrl = downloadUrl)
            } catch (e: Exception) { log.debug("Failed to parse CORE work: {}", e.message); null }
        }
        return PaperSearchResult(papers, scrollId, totalResults)
    }

    private fun coreHeaders() = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        set("Authorization", "Bearer ${properties.apiKey}")
    }
}
