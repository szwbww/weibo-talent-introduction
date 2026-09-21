package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.CoreProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
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

    /**
     * I-1: CORE 分页游标 = 「主题组分片 + 单年 + offset」。
     *
     * 供应商的 searchId 只对单次 scroll 会话有效，跨运行/跨日不可复用，因此游标里绝不出现它；
     * offset 分页是本次实测过的推进方式（审计记录：同查询 offset=0/2 得到两组无交集 ID）。
     * 注意：offset 对变化中的源不提供快照一致性，跨运行续跑只保证「不再反复首批」。
     */
    data class CoreCursor(val topic: Int, val year: Int, val offset: Int) {
        fun encode(): String = "$topic|$year|$offset"

        companion object {
            /**
             * 只采信本编码写出的值。历史/非法值（含曾经用过的 scrollId 与裸数字）一律按「无游标」处理，
             * 即从首分片重新开始；绝不把外来字符串当成分片游标使用。
             */
            fun parse(value: String?): CoreCursor? {
                val parts = value?.split('|') ?: return null
                if (parts.size != 3) return null
                val topic = parts[0].toIntOrNull() ?: return null
                val year = parts[1].toIntOrNull() ?: return null
                val offset = parts[2].toIntOrNull() ?: return null
                if (topic < 0 || offset < 0) return null
                return CoreCursor(topic, year, offset)
            }
        }
    }

    /**
     * I-1/I-4: 一页 CORE 结果。
     *
     * - [result] 沿用 [PaperSearchResult] 语义：`nextCursor` 是下一个分片游标（`null` 表示所有分片已遍历），
     *   绝不是供应商的 searchId。
     * - [windowLimit] 为 true 表示本页所在分片触达供应商 offset 窗口：该分片就此停止、游标已切到下一分片，
     *   [uncoveredTail] 是该分片未被覆盖的尾部条数（只做记录，不冒充穷尽）。
     */
    data class CoreSearchPage(val result: PaperSearchResult, val windowLimit: Boolean, val uncoveredTail: Long)

    /**
     * I-1/I-4: 每次请求可用的最大 offset（保守防护，不是供应商保证）。
     * 达到边界即停止该分片并切下一分片；完整拉取窗口之外另需官方契约验证（CP-4）。
     */
    companion object {
        const val MAX_OFFSET = 9000
    }

    /**
     * I-1: 返回一页（分片 + offset 协议）。页空或原始返回不足一页即分片结束；offset 越过 [MAX_OFFSET]
     * 即分片因窗口停止。[searchPapers] 只是本方法的 [PaperSearchResult] 视图。
     */
    fun searchCorePage(criteria: PaperSearchCriteria): CoreSearchPage {
        val pageSize = criteria.pageSize.coerceAtLeast(1)
        val shard = CoreCursor.parse(criteria.cursor)
            ?: CoreCursor(topic = 0, year = criteria.publicationYearFrom, offset = 0)
        val body = mapOf(
            "q" to buildQuery(criteria, shard.year),
            "limit" to pageSize,
            "offset" to shard.offset
        )
        val response = try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            restTemplate.exchange("${properties.baseUrl}/search/works", HttpMethod.POST,
                HttpEntity(body, coreHeaders()), JsonNode::class.java).body
        } catch (e: Exception) {
            log.error("CORE search failed: {}", e.message)
            throw e
        }

        val parsed = parseSearchResponse(response)
        val nextOffset = shard.offset + parsed.rawCount
        val shardFinished = parsed.rawCount < pageSize
        val windowLimit = !shardFinished && nextOffset > MAX_OFFSET
        val next = if (shardFinished || windowLimit) nextShard(shard, criteria) else shard.copy(offset = nextOffset)
        val uncoveredTail = if (windowLimit) (parsed.totalResults - nextOffset).coerceAtLeast(0) else 0
        if (windowLimit) {
            log.warn(
                "CORE 分片 (topic={}, year={}) 触达供应商 offset 窗口 {}：本分片停止，未覆盖尾部 {} 条，改从下一分片继续",
                shard.topic, shard.year, MAX_OFFSET, uncoveredTail
            )
        }
        return CoreSearchPage(PaperSearchResult(parsed.papers, next?.encode(), parsed.totalResults), windowLimit, uncoveredTail)
    }

    override fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult = searchCorePage(criteria).result

    /**
     * I-3: 目录主题词必须用**显式括号 OR** 合并后与单年份 AND。
     * 手动关键词永远优先并保持改动前的 AND 语义；无关键词也无目录主题（scope 为 null/未知）时沿用 `*` 通配。
     */
    private fun buildQuery(criteria: PaperSearchCriteria, year: Int): String {
        val topicClause = when {
            criteria.keywords.isNotEmpty() -> criteria.keywords.joinToString(" AND ")
            else -> topicGroups(criteria).firstOrNull()?.joinToString(" OR ")
        }
        return if (topicClause == null) "* AND yearPublished=$year" else "($topicClause) AND yearPublished=$year"
    }

    /**
     * I-1: 主题组分片列表。手动关键词是一组（保持既有 AND 语义），scope 目录主题词是一组（OR 合并）；
     * 二者都没有时没有分片可查，游标停在 topic=0。
     */
    private fun topicGroups(criteria: PaperSearchCriteria): List<List<String>> = when {
        criteria.keywords.isNotEmpty() -> listOf(criteria.keywords)
        else -> SubjectScopeCatalog.coreKeywords(criteria.subjectScope)
            .takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
    }

    /** I-1: 分片推进顺序 = 同主题组内逐年 +1，主题组用尽后换下一组；没有下一分片返回 null（本次已遍历全部）。 */
    private fun nextShard(shard: CoreCursor, criteria: PaperSearchCriteria): CoreCursor? {
        if (shard.year < criteria.publicationYearTo) return CoreCursor(shard.topic, shard.year + 1, 0)
        val nextTopic = shard.topic + 1
        return if (nextTopic < topicGroups(criteria).size) {
            CoreCursor(nextTopic, criteria.publicationYearFrom, 0)
        } else {
            null
        }
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

    /** I-1: 一页响应的解析结果。[rawCount] 是原始返回条数（推进 offset 的唯一依据），不是可收录论文数。 */
    private data class CoreParseResult(val papers: List<PaperMetadata>, val totalResults: Long, val rawCount: Int)

    private fun parseSearchResponse(response: JsonNode?): CoreParseResult {
        if (response == null) return CoreParseResult(emptyList(), 0, 0)
        val results = response.path("results")
        val totalResults = response.path("totalHits").asLong(0)
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
        return CoreParseResult(papers, totalResults, results.size())
    }

    private fun coreHeaders() = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        set("Authorization", "Bearer ${properties.apiKey}")
    }
}
