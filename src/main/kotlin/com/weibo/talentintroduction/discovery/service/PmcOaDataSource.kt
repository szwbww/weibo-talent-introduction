package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.PmcOaProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
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
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery.pmc-oa", name = ["enabled"], havingValue = "true")
class PmcOaDataSource(
    private val restTemplate: RestTemplate,
    private val properties: PmcOaProperties
) : AcademicDataSource {

    private val log = LoggerFactory.getLogger(PmcOaDataSource::class.java)

    override val sourceName = "PMC_OA"
    override val emailExtractionMethod = "FULLTEXT_XML"
    override val maxPapersPerSource: Int
        get() = properties.maxPapersPerSource

    override fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult {
        val query = buildQuery(criteria)
        val offset = criteria.cursor?.toIntOrNull() ?: 0
        val url = buildUrl(properties.baseUrl, query, criteria.pageSize, offset)

        val response = try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            restTemplate.getForObject(url, JsonNode::class.java)
        } catch (e: Exception) {
            log.error("PMC OA search failed: {}", e.message)
            throw e
        }

        return parseSearchResponse(response, criteria)
    }

    override fun extractAuthorEmails(paper: PaperMetadata): EmailExtractionOutcome {
        val pmcId = paper.pmcId
        if (pmcId == null) {
            return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_PMC_ID", httpRequests = 0)
        }

        val xml = fetchFullTextXml(pmcId)
        if (xml == null) {
            return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "FULLTEXT_FETCH_FAILED", httpRequests = 1)
        }

        return try {
            val emails = JatsXmlEmailParser.parse(xml)
            if (emails.isEmpty()) {
                EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_EMAIL_IN_FULLTEXT", httpRequests = 1)
            } else {
                EmailExtractionOutcome(emails, emailExtractionMethod, null, httpRequests = 1)
            }
        } catch (e: Exception) {
            log.debug("Failed to parse JATS XML for {}: {}", pmcId, e.message)
            EmailExtractionOutcome(emptyList(), emailExtractionMethod, "XML_PARSE_FAILED", httpRequests = 1)
        }
    }

    private fun fetchFullTextXml(pmcId: String): ByteArray? {
        val url = "${properties.baseUrl}/efetch.fcgi?db=pmc&id=$pmcId&rettype=xml"
        return try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            restTemplate.getForObject(url, ByteArray::class.java)
        } catch (e: Exception) {
            log.debug("Failed to fetch full text XML for {}: {}", pmcId, e.message)
            null
        }
    }

    private fun buildQuery(criteria: PaperSearchCriteria): String {
        val parts = mutableListOf<String>()
        parts.add("open access[filter]")
        parts.add("\"${criteria.publicationYearFrom}/01/01\"[PDAT] : \"${criteria.publicationYearTo}/12/31\"[PDAT]")
        if (criteria.keywords.isNotEmpty()) {
            val kw = criteria.keywords.joinToString(" OR ") { "\"$it\"" }
            parts.add("($kw)")
        }
        return parts.joinToString(" AND ")
    }

    private fun buildUrl(baseUrl: String, query: String, pageSize: Int, offset: Int): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/esearch.fcgi?db=pmc&term=$encodedQuery&retmax=$pageSize&retstart=$offset&retmode=json"
        return if (properties.apiKey.isNotBlank()) {
            "$url&api_key=${properties.apiKey}"
        } else url
    }

    private fun parseSearchResponse(response: JsonNode?, criteria: PaperSearchCriteria): PaperSearchResult {
        if (response == null) return PaperSearchResult(emptyList(), null, 0)

        val result = response.path("esearchresult")
        val totalResults = result.path("count").asLong(0)
        val idList = result.path("idlist")
        val ids = (0 until idList.size()).map { idList.get(it).asText() }

        val currentOffset = criteria.cursor?.toIntOrNull() ?: 0
        val nextCursor = if (currentOffset + ids.size < totalResults) {
            (currentOffset + ids.size).toString()
        } else null

        val papers = ids.map { id ->
            PaperMetadata(
                pmcId = id,
                pmid = null,
                doi = null,
                title = "",
                pubYear = 0,
                journal = null,
                authors = emptyList(),
                source = sourceName
            )
        }

        return PaperSearchResult(papers, nextCursor, totalResults)
    }
}
