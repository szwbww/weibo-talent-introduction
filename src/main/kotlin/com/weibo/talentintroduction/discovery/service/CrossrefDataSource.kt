package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.CrossrefProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
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
        // I-3: 操作端关键词永远优先，并且保持改动前的 `query` 参数不变；
        // 没有关键词时才用目录里的研发主题词，走 `query.bibliographic`（检索文献题录字段，降低噪音）。
        // scope 为 null/未知时目录返回空列表，保持改动前的「无 query 参数」行为。
        val operatorKeyword = criteria.keywords.takeIf { it.isNotEmpty() }?.joinToString(" ")
        val topicSeed = if (operatorKeyword == null) {
            SubjectScopeCatalog.crossrefQueries(criteria.subjectScope)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" ")
        } else {
            null
        }

        val filterParts = mutableListOf<String>()
        filterParts.add("from-pub-date:${criteria.publicationYearFrom}-01-01")
        filterParts.add("until-pub-date:${criteria.publicationYearTo}-12-31")
        filterParts.add("has-full-text:true")

        val filter = filterParts.joinToString(",")

        // I-1/V-1: 每个参数按组件编码恰好一次，随后以「已编码」状态交给 UriComponentsBuilder，
        // 最后只把 URI 对象交给 RestTemplate。绝不能预编码后走 String 重载：那会把 `%` 当模板再编码一次，
        // 远端收到字面 %3A/%2C 后返回 400（2026-09-21 生产故障根因）。
        // URLEncoder 的 form 语义与 Crossref（servlet 侧）的解码互逆：空格→`+`、`+`→`%2B`、`:`→`%3A`、`%`→`%25`。
        val builder = UriComponentsBuilder.fromHttpUrl("${properties.baseUrl}/works")
        if (operatorKeyword != null) builder.queryParam("query", encodeComponent(operatorKeyword))
        if (topicSeed != null) builder.queryParam("query.bibliographic", encodeComponent(topicSeed))
        builder.queryParam("filter", encodeComponent(filter))
        builder.queryParam("rows", encodeComponent(criteria.pageSize.toString()))
        builder.queryParam("cursor", encodeComponent(criteria.cursor ?: "*"))
        if (properties.politeEmail.isNotBlank()) {
            builder.queryParam("mailto", encodeComponent(properties.politeEmail))
        }
        val uri = builder.build(true).toUri()

        val response = try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            restTemplate.getForObject(uri, JsonNode::class.java)
        } catch (e: Exception) {
            log.error("Crossref search failed: {}", e.message)
            throw e
        }

        return parseResponse(response)
    }

    /** I-1：单个查询参数值的唯一编码点。调用方不得再编码，也不得把结果拼进 URI 模板字符串。 */
    private fun encodeComponent(value: String): String = URLEncoder.encode(value, "UTF-8")

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
