package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.BoundedFulltextHttp
import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.config.FetchRetry
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
import java.time.Instant

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
        return (fetchFullTextXml(pmcId, null) as? XmlFetchResult.Bytes)?.payload
    }

    /** R-1（V-4）：XML 抓取结果 —— 区分「没取到内容」与「预算已尽、根本没发请求」，计数因此不会撒谎。 */
    private sealed class XmlFetchResult {
        class Bytes(val payload: ByteArray) : XmlFetchResult()
        class Failed(val requestsIssued: Int) : XmlFetchResult()
        class BudgetExhausted(val requestsIssued: Int) : XmlFetchResult()
    }

    /**
     * R-1（V-4）：XML 阶段在**同一个**单篇共享预算内完成 —— 已过期不发请求；真实请求的连接/读取超时
     * 取 `min(既有配置, 当时剩余预算)`，所以一个慢响应（连接、响应头或响应体）都只会被截断到剩余预算，
     * 不会把调用方拖过总时限；重试前同样重新判断预算，过期即停手（不再发多余请求）。
     */
    private fun fetchFullTextXml(pmcId: String, deadline: Instant?): XmlFetchResult {
        val url = "${properties.baseUrl}/$pmcId/fullTextXML"
        var requestsIssued = 0
        return try {
            if (properties.requestDelayMs > 0) {
                val remaining = BoundedFulltextHttp.remainingMsOrUnbounded(deadline)
                if (remaining != BoundedFulltextHttp.UNBOUNDED_REMAINING_MS && remaining <= properties.requestDelayMs) {
                    return XmlFetchResult.BudgetExhausted(requestsIssued)
                }
                Thread.sleep(properties.requestDelayMs)
            }
            val payload = FetchRetry.retryOnRecoverableIo<ByteArray?>(
                maxRetries = properties.maxRetries,
                initialBackoffMs = properties.retryBackoffMs
            ) {
                val remaining = BoundedFulltextHttp.remainingMsOrUnbounded(deadline)
                if (remaining <= 0L) return@retryOnRecoverableIo null
                requestsIssued++
                // R-1（V-4）：连接/读取超时取 min(既有配置, 剩余预算)，响应体也在同一个绝对 deadline
                // 内读完 —— 细水长流的 XML 会被截断，而不是永远读下去。
                BoundedFulltextHttp.getForObject(
                    restTemplate, url, ByteArray::class.java,
                    properties.connectTimeoutMs.toLong(), properties.readTimeoutMs.toLong(), deadline
                )
            }
            if (payload == null) XmlFetchResult.BudgetExhausted(requestsIssued) else XmlFetchResult.Bytes(payload)
        } catch (e: Exception) {
            log.debug("Failed to fetch full text XML for {}: {}", pmcId, e.message)
            val expired = deadline != null && !Instant.now().isBefore(deadline)
            if (expired) {
                XmlFetchResult.BudgetExhausted(requestsIssued)
            } else {
                XmlFetchResult.Failed(requestsIssued.coerceAtLeast(1))
            }
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

    override fun extractAuthorEmails(paper: PaperMetadata): EmailExtractionOutcome =
        extractAuthorEmails(paper, null)

    /**
     * R-4（V-4）：带共享剩余时限的 XML 阶段入口（OpenAlex 全文回退链传入单篇总时限）。
     *
     * 时限已过时**一个请求都不发**，并按既有的 `TIMEOUT` 类别上报（不是新的失败类别、也不是
     * `FULLTEXT_FETCH_FAILED`）—— 调用方据此停止后续 URL / Unpaywall 阶段。
     * 传 `null` 时行为与原来的 [extractAuthorEmails] 完全一致。
     */
    fun extractAuthorEmails(paper: PaperMetadata, deadline: Instant?): EmailExtractionOutcome {
        if (!properties.enabled) {
            return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "SOURCE_DISABLED")
        }

        val searchEmails = paper.authors.mapNotNull { author ->
            val email = author.email?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            AuthorEmail(
                email = email,
                givenNames = author.givenNames,
                familyNames = author.familyNames,
                isCorresponding = author.isCorresponding,
                affiliation = author.affiliation,
                orcidId = author.orcidId,
                institutionType = author.institutionType
            )
        }
        // Search metadata alone remains an unverified clue; prefer explicit fulltext ownership.
        if (searchEmails.isNotEmpty() && paper.pmcId == null) {
            return EmailExtractionOutcome(searchEmails, "SEARCH_FIELD", null, httpRequests = 0)
        }

        val pmcId = paper.pmcId
        if (pmcId == null) {
            return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_PMC_ID")
        }

        if (deadline != null && !Instant.now().isBefore(deadline)) {
            log.debug("[{}] shared fulltext deadline expired before the XML stage for {}", sourceName, pmcId)
            return EmailExtractionOutcome(
                emptyList(), emailExtractionMethod, "FULLTEXT_FETCH_FAILED",
                httpRequests = 0, fulltextObtained = false,
                downloadFailureCategory = FULLTEXT_FAILURE_TIMEOUT
            )
        }

        val xml = when (val fetched = fetchFullTextXml(pmcId, deadline)) {
            is XmlFetchResult.Bytes -> fetched.payload
            is XmlFetchResult.BudgetExhausted -> return EmailExtractionOutcome(
                emptyList(), emailExtractionMethod, "FULLTEXT_FETCH_FAILED",
                httpRequests = fetched.requestsIssued, fulltextObtained = false,
                downloadFailureCategory = FULLTEXT_FAILURE_TIMEOUT
            )
            is XmlFetchResult.Failed -> return EmailExtractionOutcome(
                emptyList(), emailExtractionMethod, "FULLTEXT_FETCH_FAILED",
                httpRequests = fetched.requestsIssued, fulltextObtained = false
            )
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
