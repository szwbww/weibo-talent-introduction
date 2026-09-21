package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.OrcidProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
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

    /**
     * I-2: 一次 ORCID 检索的结果。
     *
     * - [records] 只含带公开邮箱、可进入收录流程的记录（邮箱过滤保持改动前语义）。
     * - [rawCount] 是本次原始返回条数（**含无公开邮箱者**）：offset 只能按它推进，
     *   「一整页都没有公开邮箱」时也必须前进，否则会在原地反复首批。
     * - [nextCursor] 是下一次请求该用的分片游标；`null` 表示所有分片已遍历（可判穷尽）。
     */
    data class OrcidSearchPage(val records: List<OrcidRecord>, val nextCursor: String?, val rawCount: Int)

    /**
     * I-2: ORCID 分片游标 = 「主题种子分片 + offset」。手动关键词只有一个分片（topic 恒为 0），
     * 默认研发范围按目录主题种子逐片检索；分片主题必须随游标持久化，跨运行才知道该查哪一片。
     */
    data class OrcidCursor(val topic: Int, val offset: Int) {
        fun encode(): String = "$topic|$offset"

        companion object {
            /** 兼容 c2 写下的裸数字 offset（当时只有一个分片）；其余非法值按「无游标」处理。 */
            fun parse(value: String?): OrcidCursor? {
                if (value.isNullOrBlank()) return null
                val bare = value.toIntOrNull()
                if (bare != null) return if (bare < 0) null else OrcidCursor(0, bare)
                val parts = value.split('|')
                if (parts.size != 2) return null
                val topic = parts[0].toIntOrNull() ?: return null
                val offset = parts[1].toIntOrNull() ?: return null
                if (topic < 0 || offset < 0) return null
                return OrcidCursor(topic, offset)
            }
        }
    }

    companion object {
        /** ORCID 公开检索的关键词字段名；主题种子只作检索约束，不参与国籍/机构判定（I-3）。 */
        private const val ORCID_KEYWORD_FIELD = "keyword"

        /**
         * I-2/I-4: ORCID Public API 检索窗口上限 —— 公开检索只可遍历 start 0..9999 共 10000 条
         * （docs: info.orcid.org「API Tutorial: Searching the ORCID registry」，Member API 不受此限）。
         * 达到边界即停止该分片并切下一分片，绝不无限递增 offset。
         */
        const val MAX_OFFSET = 9999
    }

    fun searchOrcidPage(criteria: PaperSearchCriteria): OrcidSearchPage {
        val shards = queryShards(criteria)
        if (shards.isEmpty()) {
            // I-2: 无关键词且无 scope 主题种子 = 明确跳过（不发请求，也不谎报穷尽/失败）。
            log.info("[ORCID] 无关键词且无学科范围主题种子，本次跳过检索")
            return OrcidSearchPage(emptyList(), null, 0)
        }

        val cursor = OrcidCursor.parse(criteria.cursor) ?: OrcidCursor(0, 0)
        val topic = cursor.topic.coerceIn(0, shards.size - 1)
        val query = shards[topic]
        val rows = minOf(criteria.pageSize, properties.maxRecordsPerRun).coerceAtLeast(1)

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "${properties.baseUrl}/expanded-search/?q=$encodedQuery&start=${cursor.offset}&rows=$rows"

        val response = try {
            if (properties.requestDelayMs > 0) Thread.sleep(properties.requestDelayMs)
            restTemplate.getForObject(url, JsonNode::class.java)
        } catch (e: Exception) {
            log.error("ORCID search failed: {}", e.message)
            throw e
        }

        val rawCount = response?.path("expanded-result")?.size() ?: 0
        val nextOffset = cursor.offset + rawCount
        // I-2: 页空或原始返回不足一页即分片结束；I-4: offset 越过公开检索窗口即停止该分片。
        val shardFinished = rawCount < rows
        val windowLimit = !shardFinished && nextOffset > MAX_OFFSET
        val next = if (shardFinished || windowLimit) {
            if (topic + 1 < shards.size) OrcidCursor(topic + 1, 0) else null
        } else {
            OrcidCursor(topic, nextOffset)
        }
        if (windowLimit) {
            log.warn(
                "ORCID 分片 topic={} 触达公开检索窗口 {}：本分片停止，未覆盖尾部 {} 条，改从下一分片继续",
                topic, MAX_OFFSET, (MAX_OFFSET + 1 - nextOffset).coerceAtLeast(0)
            )
        }
        return OrcidSearchPage(parseOrcidRecords(response), next?.encode(), rawCount)
    }

    /**
     * I-2: 只关心记录集合、不推进分页的调用方（如按 orcid 反查邮箱）用这个视图；
     * 分页推进必须走 [searchOrcidPage]，否则拿不到 [OrcidSearchPage.rawCount] 与 [OrcidSearchPage.nextCursor]。
     */
    fun searchOrcidRecords(criteria: PaperSearchCriteria): List<OrcidRecord> = searchOrcidPage(criteria).records

    /**
     * I-2/I-3: 检索分片。
     * 操作端关键词永远优先（保持改动前的引号 AND 语义，只有一个分片）；关键词为空时用目录里的 scope
     * 主题种子，每个种子一片，检索 ORCID 的公开研究关键词字段，不把公司名或机构地址当国籍。
     */
    private fun queryShards(criteria: PaperSearchCriteria): List<String> = when {
        criteria.keywords.isNotEmpty() ->
            listOf(criteria.keywords.joinToString(" AND ") { "\"$it\"" })
        else ->
            SubjectScopeCatalog.orcidSeedKeywords(criteria.subjectScope)
                .map { "$ORCID_KEYWORD_FIELD:\"$it\"" }
    }

    private fun parseOrcidRecords(response: JsonNode?): List<OrcidRecord> {
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
