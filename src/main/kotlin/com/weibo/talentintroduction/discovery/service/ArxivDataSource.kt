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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.w3c.dom.Element
import org.w3c.dom.NodeList
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

        var position = Position.decode(criteria.cursor)
        if (position.offset >= RESULT_WINDOW) {
            // Old numeric checkpoints refer to the unrestricted query. Recover its last readable
            // timestamp, including for older offsets beyond the window (conservative replay).
            val anchor = fetchPage(keywordQuery, RESULT_WINDOW - 1, 1)
            val upper = if (anchor.entries.length == 0) yearEnd(criteria) else upperAfter(anchor, criteria)
            if (upper < yearStart(criteria)) return PaperSearchResult(emptyList(), null, anchor.total)
            position = Position(upper, 0)
        }
        val query = position.upper?.let { upper ->
            "($keywordQuery)+AND+submittedDate:[${MINUTE.format(yearStart(criteria))}+TO+${MINUTE.format(upper)}]"
        } ?: keywordQuery
        val limit = minOf(criteria.pageSize, RESULT_WINDOW - position.offset, MAX_PAGE_SIZE)
        require(limit > 0) { "ARXIV_INVALID_PAGE_SIZE" }
        return result(fetchPage(query, position.offset, limit), criteria, position)
    }

    // A null upper bound retains the exact meaning of legacy numeric checkpoints.
    private data class Position(val upper: LocalDateTime?, val offset: Int) {
        fun encode(): String = upper?.let { "t1:${MINUTE.format(it)}:$offset" } ?: offset.toString()

        companion object {
            fun decode(cursor: String?): Position {
                if (cursor == null) return Position(null, 0)
                cursor.toIntOrNull()?.takeIf { it >= 0 }?.let { return Position(null, it) }
                val parts = cursor.split(':')
                require(parts.size == 3 && parts[0] == "t1") { "ARXIV_INVALID_CURSOR" }
                val offset = parts[2].toIntOrNull()
                require(offset != null && offset in 0 until RESULT_WINDOW) { "ARXIV_INVALID_CURSOR" }
                return Position(LocalDateTime.parse(parts[1], MINUTE), offset)
            }
        }
    }

    private data class AtomPage(val entries: NodeList, val total: Long)

    private fun yearStart(criteria: PaperSearchCriteria) = LocalDateTime.of(criteria.publicationYearFrom, 1, 1, 0, 0)

    private fun yearEnd(criteria: PaperSearchCriteria) = LocalDateTime.of(criteria.publicationYearTo, 12, 31, 23, 59).plusMinutes(1)

    private fun upperAfter(page: AtomPage, criteria: PaperSearchCriteria): LocalDateTime {
        val last = page.entries.item(page.entries.length - 1) as Element
        val published = last.getElementsByTagName("published").item(0)?.textContent?.trim()
            ?: throw IllegalStateException("ARXIV_MISSING_BOUNDARY_DATE")
        // arXiv interprets minute precision as :00. Rounding up replays ties at the boundary.
        val minuteAfter = LocalDateTime.ofInstant(Instant.parse(published), ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        return minOf(minuteAfter, yearEnd(criteria))
    }

    private fun fetchPage(query: String, start: Int, limit: Int): AtomPage {
        val url = "$baseUrl/query?search_query=$query&start=$start&max_results=$limit" +
            "&sortBy=submittedDate&sortOrder=descending"
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

        return readPage(response.body, start)
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
        val position = Position.decode(criteria.cursor)
        return result(readPage(xml, position.offset), criteria, position)
    }

    private fun readPage(xml: String?, start: Int): AtomPage {
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
            if (entryId != null && entryId.substringBefore('#').endsWith("/api/errors")) {
                throw IllegalStateException("ARXIV_ERROR_ENTRY")
            }
        }

        val total = doc.getElementsByTagName("opensearch:totalResults").item(0)?.textContent?.trim()?.toLongOrNull()
            ?: throw IllegalStateException("ARXIV_MISSING_TOTAL")
        check(total >= 0 && (entries.length == 0 || total >= start.toLong() + entries.length)) { "ARXIV_INCONSISTENT_TOTAL" }
        val reportedStart = doc.getElementsByTagName("opensearch:startIndex").item(0)?.textContent?.trim()
        check(reportedStart == null || reportedStart.toIntOrNull() == start) { "ARXIV_UNEXPECTED_START" }
        check(entries.length > 0 || start >= total) { "ARXIV_UNEXPECTED_EMPTY_PAGE" }
        return AtomPage(entries, total)
    }

    private fun result(page: AtomPage, criteria: PaperSearchCriteria, position: Position): PaperSearchResult {
        val entries = page.entries
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
        val nextOffset = position.offset + rawEntryCount
        val nextCursor = when {
            rawEntryCount == 0 || nextOffset >= page.total -> null
            nextOffset < RESULT_WINDOW -> position.copy(offset = nextOffset).encode()
            else -> {
                val upper = upperAfter(page, criteria)
                check(position.upper == null || upper < position.upper) { "ARXIV_TIME_WINDOW_STALLED" }
                if (upper < yearStart(criteria)) null else Position(upper, 0).encode()
            }
        }
        return PaperSearchResult(papers, nextCursor, page.total)
    }

    private companion object {
        const val RESULT_WINDOW = 10_000
        const val MAX_PAGE_SIZE = 2_000
        val MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMMddHHmm")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT)
    }
}
