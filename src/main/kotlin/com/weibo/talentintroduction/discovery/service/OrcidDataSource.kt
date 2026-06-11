package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.OrcidProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder

@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery.orcid", name = ["enabled"], havingValue = "true")
class OrcidDataSource(
    private val restTemplate: RestTemplate,
    private val properties: OrcidProperties
) {

    private val log = LoggerFactory.getLogger(OrcidDataSource::class.java)

    val sourceName = "ORCID"
    val maxRecordsPerRun get() = properties.maxRecordsPerRun

    data class OrcidRecord(
        val orcidId: String,
        val givenNames: String?,
        val familyNames: String?,
        val emails: List<String>,
        val institutionName: String?,
        val country: String?
    )

    fun searchOrcidRecords(criteria: PaperSearchCriteria): List<OrcidRecord> {
        val keywordQuery = if (criteria.keywords.isNotEmpty()) {
            criteria.keywords.joinToString(" AND ") { "\"$it\"" }
        } else {
            null
        }

        val queryParts = mutableListOf<String>()
        if (keywordQuery != null) queryParts.add(keywordQuery)
        val query = queryParts.joinToString(" AND ")

        if (query.isBlank()) return emptyList()

        val start = criteria.cursor?.toIntOrNull() ?: 0
        val maxResults = minOf(criteria.pageSize, properties.maxRecordsPerRun)

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "${properties.baseUrl}/expanded-search/?q=$encodedQuery&start=$start&rows=$maxResults"

        val response = try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            restTemplate.getForObject(url, JsonNode::class.java)
        } catch (e: Exception) {
            log.error("ORCID search failed: {}", e.message)
            throw e
        }

        return parseOrcidResponse(response)
    }

    private fun parseOrcidResponse(response: JsonNode?): List<OrcidRecord> {
        if (response == null) return emptyList()

        val results = response.path("expanded-result")
        return (0 until results.size()).mapNotNull { i ->
            try {
                val node = results[i]
                val orcidId = node.path("orcid-id").asText(null) ?: return@mapNotNull null

                val emails = mutableListOf<String>()
                val emailNodes = node.path("email")
                if (emailNodes.isArray) {
                    for (j in 0 until emailNodes.size()) {
                        val email = emailNodes[j].asText(null)
                        if (!email.isNullOrBlank()) emails.add(email)
                    }
                }

                if (emails.isEmpty()) null
                else {
                    OrcidRecord(
                        orcidId = orcidId,
                        givenNames = node.path("given-names").asText(null),
                        familyNames = node.path("family-names").asText(null),
                        emails = emails,
                        institutionName = node.path("institution-name").firstOrNull()?.asText(null),
                        country = null
                    )
                }
            } catch (e: Exception) {
                log.debug("Failed to parse ORCID record: {}", e.message)
                null
            }
        }
    }

    fun orcidRecordToAuthorEmails(record: OrcidRecord): List<AuthorEmail> {
        return record.emails.map { email ->
            AuthorEmail(
                email = email,
                givenNames = record.givenNames,
                familyNames = record.familyNames,
                isCorresponding = false,
                affiliation = record.institutionName,
                orcidId = record.orcidId
            )
        }
    }
}
