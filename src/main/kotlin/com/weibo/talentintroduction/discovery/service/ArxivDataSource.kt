package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ArxivProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.io.StringReader
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery.arxiv", name = ["enabled"], havingValue = "true")
class ArxivDataSource(
    private val restTemplate: RestTemplate,
    private val properties: ArxivProperties,
    private val pdfEmailExtractor: PdfEmailExtractor
) : AcademicDataSource {

    private val log = LoggerFactory.getLogger(ArxivDataSource::class.java)

    /**
     * I-2: 出站一律 HTTPS。旧配置/环境变量里的官方 http 入口（`http://export.arxiv.org/api`）
     * 在生产实测返回 301 空体，必须先规范化，不能让「跳转」被当成「没有数据」。
     */
    private val baseUrl = properties.baseUrl.replaceFirst(Regex("^http://"), "https://")

    override val sourceName = "ARXIV"
    override val emailExtractionMethod = "PDF_PARSE"
    override val maxPapersPerSource get() = properties.maxPapersPerSource

    override fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult {
        val keywordQuery = if (criteria.keywords.isNotEmpty()) {
            criteria.keywords.joinToString("+AND+") { "all:\"${URLEncoder.encode(it, "UTF-8")}\"" }
        } else {
            // I4-2: 无关键词且无学科分类时兜底 "all:*"，与改动前逐字相同。
            val cats = SubjectScopeCatalog.arxivCategories(criteria.subjectScope)
            if (cats.isNotEmpty()) {
                cats.joinToString("+OR+") { "cat:$it*" }
            } else {
                "all:*"
            }
        }

        val start = criteria.cursor?.toIntOrNull() ?: 0
        val url = "$baseUrl/query?" +
            "search_query=$keywordQuery" +
            "&start=$start" +
            "&max_results=${criteria.pageSize}" +
            "&sortBy=submittedDate" +
            "&sortOrder=descending"

        val response = try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            restTemplate.exchange(url, HttpMethod.GET, null, String::class.java)
        } catch (e: Exception) {
            log.error("arXiv search failed: {}", e.message)
            throw e
        }

        // I-2: 301 空体是故障，不是「零结果」。必须显式失败，交给上层记 SEARCH_FAILED 并保留游标。
        if (response.statusCode.is3xxRedirection || response.body.isNullOrBlank()) {
            throw IllegalStateException("ARXIV_EMPTY_OR_REDIRECT")
        }

        return parseAtomResponse(response.body, criteria)
    }

    override fun extractAuthorEmails(paper: PaperMetadata): EmailExtractionOutcome {
        val arxivId = paper.doi?.removePrefix("arXiv:") ?: paper.pmcId
        if (arxivId == null) {
            return EmailExtractionOutcome(emptyList(), emailExtractionMethod, "NO_DOI")
        }

        val pdfUrl = "https://arxiv.org/pdf/$arxivId"
        return pdfEmailExtractor.extract(pdfUrl, paper.authors, sourceName)
    }

    fun parseAtomResponse(xml: String?, criteria: PaperSearchCriteria): PaperSearchResult {
        // I-2: 空体属于故障（301 空体 / 空 200），不是「零结果」。
        if (xml.isNullOrBlank()) throw IllegalStateException("ARXIV_EMPTY_OR_REDIRECT")

        val doc = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            builder.parse(org.xml.sax.InputSource(StringReader(xml)))
        } catch (e: Exception) {
            log.error("Failed to parse arXiv Atom response: {}", e.message)
            throw IllegalStateException("ARXIV_INVALID_XML", e)
        }

        // I-2: 非 Atom 载荷（例如代理返回的 HTML 错误页）不得被当成正常零结果。
        if (doc.documentElement == null || doc.documentElement.tagName != "feed") {
            throw IllegalStateException("ARXIV_NON_ATOM_PAYLOAD")
        }

        val entries = doc.getElementsByTagName("entry")

        // I-2: arXiv 的错误以单个 entry 返回，其 <id> 指向 arxiv.org/api/errors#（2026-09-21 线上实测形态）。
        for (i in 0 until entries.length) {
            val entryId = (entries.item(i) as? org.w3c.dom.Element)
                ?.getElementsByTagName("id")?.item(0)?.textContent
            if (entryId != null && entryId.contains("/api/errors#")) {
                throw IllegalStateException("ARXIV_ERROR_ENTRY")
            }
        }

        val totalResultsNodes = doc.getElementsByTagName("opensearch:totalResults")
        val totalResults = if (totalResultsNodes.length > 0) {
            totalResultsNodes.item(0).textContent.trim().toLongOrNull() ?: 0L
        } else 0L

        val rawEntryCount = entries.length
        val papers = mutableListOf<PaperMetadata>()

        for (i in 0 until rawEntryCount) {
            val entry = entries.item(i) as org.w3c.dom.Element
            val id = entry.getElementsByTagName("id").item(0)?.textContent?.trim()
                ?.removePrefix("http://arxiv.org/abs/")
            val title = entry.getElementsByTagName("title").item(0)?.textContent?.trim() ?: ""
            val published = entry.getElementsByTagName("published").item(0)?.textContent?.trim()
            val pubYear = published?.substring(0, 4)?.toIntOrNull() ?: 0

            if (pubYear < criteria.publicationYearFrom || pubYear > criteria.publicationYearTo) {
                continue
            }

            val authorNodes = entry.getElementsByTagName("author")
            val authors = mutableListOf<PaperAuthor>()
            for (j in 0 until authorNodes.length) {
                val authorEl = authorNodes.item(j) as org.w3c.dom.Element
                val name = authorEl.getElementsByTagName("name").item(0)?.textContent?.trim()
                if (name != null) {
                    val parts = name.split(" ", limit = 2)
                    authors.add(PaperAuthor(
                        givenNames = parts.getOrNull(0),
                        familyNames = parts.getOrNull(1),
                        orcidId = null,
                        affiliation = null,
                        isCorresponding = false
                    ))
                }
            }

            papers.add(PaperMetadata(
                pmcId = null,
                pmid = null,
                doi = id?.let { "arXiv:$it" },
                title = title,
                pubYear = pubYear,
                journal = "arXiv",
                authors = authors,
                source = sourceName
            ))
        }

        // c2 契约：nextCursor 由原始条目数推导，年份过滤把整页清空时也必须继续翻页。
        val start = criteria.cursor?.toIntOrNull() ?: 0
        val nextCursor = if (rawEntryCount > 0 && start + rawEntryCount < totalResults) {
            (start + rawEntryCount).toString()
        } else null

        return PaperSearchResult(papers, nextCursor, totalResults)
    }
}
