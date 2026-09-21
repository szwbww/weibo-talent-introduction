package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.OrcidProperties
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.client.RestTemplate
import java.net.URLDecoder

class OrcidDataSourceTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val properties = OrcidProperties(enabled = true, requestDelayMs = 0)
    private val mapper = ObjectMapper()
    private val dataSource = OrcidDataSource(restTemplate, properties)

    /** 真实发出的 URL（断言请求边界：start/rows/查询串）。 */
    private val requests = mutableListOf<String>()

    private fun stubResponses(vararg responses: String) {
        var index = 0
        Mockito.doAnswer { invocation ->
            requests.add(invocation.getArgument<String>(0))
            val body = responses[minOf(index, responses.size - 1)]
            index++
            mapper.readTree(body)
        }.`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(JsonNode::class.java))
    }

    private fun expandedSearchResponse(vararg records: Pair<String, List<String>>): String {
        val root = mapper.createObjectNode()
        val results = root.putArray("expanded-result")
        records.forEach { (orcidId, emails) ->
            val node = results.addObject()
            node.put("orcid-id", orcidId)
            node.put("given-names", "John")
            node.put("family-names", "Smith")
            val emailArray = node.putArray("email")
            emails.forEach { emailArray.add(it) }
            node.putArray("institution-name").add("Oxford University")
        }
        return mapper.writeValueAsString(root)
    }

    private fun recordsWithoutEmail(count: Int): String =
        expandedSearchResponse(*Array(count) { i -> "0000-0001-%04d".format(i) to emptyList<String>() })

    private fun decodedQuery(url: String): String {
        val queryPart = url.substringAfter("?q=").substringBefore("&")
        return URLDecoder.decode(queryPart, "UTF-8")
    }

    private fun startParam(url: String): String = url.substringAfter("&start=").substringBefore("&")

    @Test
    fun `searchOrcidPage advances by the raw count when a whole page has no public email`() {
        // I-2: 一整页都没有公开邮箱时不能终止搜索、也不能停住 offset —— 必须按原始条数前进。
        stubResponses(recordsWithoutEmail(100))

        val page = dataSource.searchOrcidPage(
            PaperSearchCriteria(keywords = listOf("machine learning"), pageSize = 100)
        )

        assertTrue(page.records.isEmpty(), "无公开邮箱的记录不进入可收录集合")
        assertEquals(100, page.rawCount, "rawCount 必须是原始返回条数（含无邮箱者）")
        assertEquals("0|100", page.nextCursor, "整页无邮箱也要按原始条数推进 offset")
    }

    @Test
    fun `searchOrcidPage rotates to the next topic shard after the last page of a shard`() {
        // 原始返回不足一页 = 该分片末页，切下一个主题分片；分片主题随游标持久化。
        stubResponses(expandedSearchResponse("0000-0001-0001" to listOf("a@ox.ac.uk")))

        val page = dataSource.searchOrcidPage(
            PaperSearchCriteria(subjectScope = SubjectScopeCatalog.RND_TARGET, pageSize = 100)
        )

        assertEquals(1, page.rawCount)
        assertEquals("1|0", page.nextCursor, "第一个主题分片结束应切到第二个主题分片起点")
        assertEquals("keyword:\"engineering\"", decodedQuery(requests.single()))
        assertEquals("0", startParam(requests.single()))
    }

    @Test
    fun `searchOrcidPage ends after the last topic shard`() {
        stubResponses(expandedSearchResponse("0000-0001-0001" to listOf("a@ox.ac.uk")))

        val page = dataSource.searchOrcidPage(
            PaperSearchCriteria(subjectScope = SubjectScopeCatalog.RND_TARGET, pageSize = 100,
                cursor = "5|0")
        )

        assertNull(page.nextCursor, "第六个主题分片是最后一个分片")
        assertEquals("keyword:\"physics\"", decodedQuery(requests.single()), "游标里的分片主题决定本次查询")
    }

    @Test
    fun `searchOrcidPage stops the shard at the public search window`() {
        // I-4: ORCID 公开检索窗口是 start 0..9999；越过即停止该分片，不无限递增 offset。
        stubResponses((1..100).joinToString(",", prefix = """{"expanded-result":[""", postfix = "]}") {
            """{"orcid-id":"0000-0001-%04d","email":["a$it@ox.ac.uk"]}""".format(it)
        })

        val page = dataSource.searchOrcidPage(
            PaperSearchCriteria(subjectScope = SubjectScopeCatalog.RND_TARGET, pageSize = 100, cursor = "0|9900")
        )

        assertEquals(100, page.rawCount)
        assertEquals("1|0", page.nextCursor, "触达窗口后切下一分片，而不是继续 offset=10000")
    }

    @Test
    fun `searchOrcidPage skips without a request when no keyword and no scope seed exist`() {
        // V-2: 无 scope、无关键词 = 明确跳过（不发请求），也绝不谎报穷尽或失败。
        val page = dataSource.searchOrcidPage(PaperSearchCriteria(keywords = emptyList(), subjectScope = null))

        assertTrue(page.records.isEmpty())
        assertEquals(0, page.rawCount)
        assertNull(page.nextCursor)
        assertTrue(requests.isEmpty(), "无关键词且无主题种子时不得发出请求")
    }

    @Test
    fun `searchOrcidPage lets operator keywords win over the scope seeds`() {
        // V-3: 人工指定关键词不被目录主题覆盖，且保持改动前的引号 AND 语义（手工入口行为不变）。
        stubResponses(expandedSearchResponse("0000-0001-0001" to listOf("a@ox.ac.uk")))

        dataSource.searchOrcidPage(
            PaperSearchCriteria(keywords = listOf("machine learning", "robotics"),
                subjectScope = SubjectScopeCatalog.RND_TARGET)
        )

        val query = decodedQuery(requests.single())
        assertEquals("\"machine learning\" AND \"robotics\"", query)
        assertFalse(query.contains("keyword:"), "有操作端关键词时不得再叠加目录主题种子")
    }

    @Test
    fun `searchOrcidRecords returns the records view for non-paging callers`() {
        // 按 orcid 反查邮箱的调用方只关心记录集合，继续走这个视图（空关键词+scope 时按主题种子检索）。
        stubResponses("""{"expanded-result": [
            {"orcid-id": "0000-0001-0000-0001", "given-names": "John", "family-names": "Smith",
             "email": ["john@oxford.ac.uk"], "institution-name": ["Oxford University"]},
            {"orcid-id": "0000-0002-0000-0002", "email": [], "institution-name": ["Cambridge"]}
        ]}""")

        val records = dataSource.searchOrcidRecords(PaperSearchCriteria(keywords = listOf("orcid:0000-0001-0000-0001"), pageSize = 5))

        assertEquals(1, records.size, "无公开邮箱的记录不进入可收录集合")
        assertEquals("0000-0001-0000-0001", records[0].orcidId)
        assertEquals("John", records[0].givenNames)
        assertEquals("Smith", records[0].familyNames)
        assertEquals(listOf("john@oxford.ac.uk"), records[0].emails)
        assertEquals("Oxford University", records[0].institutionName)
    }

    @Test
    fun `orcidRecordToAuthorEmails converts record correctly`() {
        val record = OrcidDataSource.OrcidRecord(
            orcidId = "0000-0001-0000-0001",
            givenNames = "John",
            familyNames = "Smith",
            emails = listOf("john@oxford.ac.uk", "jsmith@oxford.ac.uk"),
            institutionName = "Oxford University",
            country = null
        )
        val emails = dataSource.orcidRecordToAuthorEmails(record)
        assertEquals(2, emails.size)
        assertEquals("john@oxford.ac.uk", emails[0].email)
        assertEquals("John", emails[0].givenNames)
        assertEquals("Smith", emails[0].familyNames)
        assertEquals("0000-0001-0000-0001", emails[0].orcidId)
        assertEquals("Oxford University", emails[0].affiliation)
    }

    @Test
    fun `searchOrcidPage throws on API error`() {
        Mockito.doThrow(RuntimeException("API unavailable"))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(JsonNode::class.java))

        assertThrows(RuntimeException::class.java) {
            dataSource.searchOrcidPage(PaperSearchCriteria(keywords = listOf("test")))
        }
    }
}
