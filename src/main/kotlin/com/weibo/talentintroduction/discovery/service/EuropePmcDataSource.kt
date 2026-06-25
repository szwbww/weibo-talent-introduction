package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Service
class EuropePmcDataSource(
    @Qualifier("europePmcRestTemplate")
    private val restTemplate: RestTemplate,
    private val properties: EuropePmcProperties
) : AcademicDataSource {

    private val log = LoggerFactory.getLogger(EuropePmcDataSource::class.java)

    override val sourceName = "EUROPE_PMC"
    override val emailExtractionMethod = "FULLTEXT_XML"
    override val maxPapersPerSource: Int
        get() = properties.maxPapersPerSource

    override fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult {
        if (!properties.enabled) {
            log.debug("Europe PMC data source is disabled, returning empty result")
            return PaperSearchResult(emptyList(), null, 0)
        }

        val query = buildQuery(criteria)
        val uri = UriComponentsBuilder.fromHttpUrl("${properties.baseUrl}/search")
            .queryParam("query", query)
            .queryParam("resultType", "core")
            .queryParam("pageSize", criteria.pageSize.toString())
            .queryParam("cursorMark", criteria.cursor ?: "*")
            .queryParam("format", "json")
            .build()
            .encode()
            .toUri()

        val response = try {
            if (properties.requestDelayMs > 0) {
                Thread.sleep(properties.requestDelayMs)
            }
            restTemplate.getForObject(uri, JsonNode::class.java)
        } catch (e: Exception) {
            log.error("Europe PMC search failed: {}", e.message)
            throw e
        }

        return parsePaperSearchResult(response)
    }

    fun fetchFullTextXml(pmcId: String): ByteArray? {
        if (!properties.enabled) {
            log.debug("Europe PMC data source is disabled, returning null")
            return null
        }
        val url = "${properties.baseUrl}/$pmcId/fullTextXML"
        return try {
            if (properties.requestDelayMs > 0) {
                Thread.sleep(properties.requestDelayMs)
            }
            restTemplate.getForObject(url, ByteArray::class.java)
        } catch (e: Exception) {
            log.debug("Failed to fetch full text XML for {}: {}", pmcId, e.message)
            null
        }
    }

    fun extractEmailsFromFullText(pmcId: String): List<AuthorEmail> {
        if (!properties.enabled) {
            log.debug("Europe PMC data source is disabled, returning empty")
            return emptyList()
        }
        val bytes = fetchFullTextXml(pmcId) ?: return emptyList()
        return try {
            JatsXmlEmailParser.parse(bytes)
        } catch (e: Exception) {
            log.debug("Failed to parse JATS XML for {}: {}", pmcId, e.message)
            emptyList()
        }
    }

    override fun extractAuthorEmails(paper: PaperMetadata): EmailExtractionOutcome {
        if (!properties.enabled) {
            return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "SOURCE_DISABLED")
        }
        val pmcId = paper.pmcId
        if (pmcId == null) {
            return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_PMC_ID")
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

    private fun buildQuery(criteria: PaperSearchCriteria): String {
        val parts = mutableListOf<String>()

        parts += "IN_EPMC:y"

        if (criteria.openAccessOnly) {
            parts += "OPEN_ACCESS:y"
        }

        parts += "PUB_YEAR:[${criteria.publicationYearFrom} TO ${criteria.publicationYearTo}]"

        if (criteria.keywords.isNotEmpty()) {
            val kw = criteria.keywords.joinToString(" OR ") { "\"$it\"" }
            parts += "(TITLE:($kw) OR KW:($kw))"
        }

        if (criteria.affiliationKeywords.isNotEmpty()) {
            val aff = criteria.affiliationKeywords.joinToString(" OR ") { "\"$it\"" }
            parts += "AFF:($aff)"
        }

        return parts.joinToString(" AND ")
    }

    private fun parsePaperSearchResult(response: JsonNode?): PaperSearchResult {
        if (response == null) return PaperSearchResult(emptyList(), null, 0)

        val resultList = response.path("resultList").path("result")
        val nextCursor = response.path("nextCursorMark").asText(null)
        val totalResults = response.path("hitCount").asLong(0)

        val papers = resultList.mapNotNull { node ->
            try {
                val pmcId = node.path("pmcid").asText(null)
                val pmid = node.path("pmid").asText(null)
                val doi = node.path("doi").asText(null)
                val title = node.path("title").asText("")
                val pubYear = node.path("pubYear").asText("0").toIntOrNull() ?: 0
                val journal = node.path("journalTitle").asText(null)

                val authors = node.path("authorList").path("author").map { authorNode ->
                    val orcid = authorNode.path("authorId")
                        .let { idNode ->
                            if (idNode.path("type").asText("") == "ORCID") idNode.path("value").asText(null)
                            else null
                        }

                    PaperAuthor(
                        givenNames = authorNode.path("firstName").asText(null),
                        familyNames = authorNode.path("lastName").asText(null),
                        orcidId = orcid,
                        affiliation = parseAffiliation(authorNode),
                        isCorresponding = false,
                        email = authorNode.path("authorEmail").asText(null)
                    )
                }

                PaperMetadata(
                    pmcId = pmcId,
                    pmid = pmid,
                    doi = doi,
                    title = title,
                    pubYear = pubYear,
                    journal = journal,
                    authors = authors,
                    source = sourceName
                )
            } catch (e: Exception) {
                log.debug("Failed to parse paper: {}", e.message)
                null
            }
        }

        return PaperSearchResult(papers, nextCursor, totalResults)
    }

    private fun parseAffiliation(authorNode: JsonNode): String? {
        val nested = authorNode.path("authorAffiliationDetailsList").path("authorAffiliation")
        if (nested.isArray && nested.size() > 0) {
            val affiliations = nested.mapNotNull { affNode ->
                affNode.path("affiliation").asText(null)
            }.distinct()
            if (affiliations.isNotEmpty()) return affiliations.joinToString("; ")
        }

        return authorNode.path("affiliation").asText(null)
    }
}
