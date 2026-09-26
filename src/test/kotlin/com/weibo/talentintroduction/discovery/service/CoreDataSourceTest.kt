package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.CoreProperties
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class CoreDataSourceTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val plainTextExtractor = PlainTextEmailExtractor()
    private val pdfExtractor = Mockito.mock(PdfEmailExtractor::class.java)
    private val mapper = ObjectMapper()
    private val properties = CoreProperties(enabled = true, apiKey = "test-key", requestDelayMs = 0)
    private val dataSource = CoreDataSource(restTemplate, properties, plainTextExtractor, pdfExtractor)

    /** 真实发出的请求（URL + body），断言「请求边界」而不是内部调用。 */
    private data class SentRequest(val url: String, val body: Map<*, *>)

    private val sent = mutableListOf<SentRequest>()

    /** 按顺序返回给定响应；每次请求都记录 URL 与 body。 */
    private fun stubSearchResponses(vararg responses: String) {
        var index = 0
        Mockito.doAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            val entity = invocation.getArgument<Any>(2) as HttpEntity<*>
            sent.add(SentRequest(url, entity.body as Map<*, *>))
            val body = responses[minOf(index, responses.size - 1)]
            index++
            ResponseEntity.ok(mapper.readTree(body))
        }.`when`(restTemplate).exchange(
            Mockito.anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(),
            Mockito.eq(JsonNode::class.java)
        )
    }

    private fun worksResponse(rawCount: Int, totalHits: Long, fullText: Boolean = false): String {
        val root = mapper.createObjectNode()
        root.put("totalHits", totalHits)
        val results = root.putArray("results")
        for (i in 1..rawCount) {
            val node = results.addObject()
            node.put("doi", "10.1234/core.$i")
            node.put("title", "Paper $i")
            node.put("yearPublished", 2020)
            node.put("publisher", "Nature")
            if (fullText) node.put("fullText", "Contact: author$i@ox.ac.uk")
        }
        return mapper.writeValueAsString(root)
    }

    @Test
    fun `searchPapers pages by offset and advances by the raw returned count`() {
        // I-1: 稳定进度用 offset/limit，游标保存分片与 offset；页满推进原始返回数量。
        // 改动前这里走 scroll=true + scrollId，scroll 会话跨运行失效导致每天反复首批。
        stubSearchResponses(worksResponse(rawCount = 2, totalHits = 250, fullText = true))

        val criteria = PaperSearchCriteria(
            pageSize = 2, publicationYearFrom = 2020, publicationYearTo = 2026,
            subjectScope = SubjectScopeCatalog.RND_TARGET
        )
        val result = dataSource.searchPapers(criteria)

        val request = sent.single()
        assertFalse(request.url.contains("scroll"), "不得再调用 scroll 接口: ${request.url}")
        assertFalse(request.body.containsKey("scroll"), "不得再下发 scroll 参数")
        assertFalse(request.body.containsKey("scrollId"), "不得再下发 scrollId")
        assertEquals(0, request.body["offset"], "首页 offset 必须是 0")
        assertEquals(2, request.body["limit"])
        assertEquals(
            "(engineering OR materials OR computer science OR chemical OR energy OR physics) AND yearPublished=2020",
            request.body["q"],
            "I-3: 目录主题词必须用显式括号 OR 合并后与单年份 AND"
        )

        assertEquals(2, result.papers.size)
        assertEquals(250L, result.totalResults)
        assertEquals("Paper 1", result.papers[0].title)
        assertEquals(2020, result.papers[0].pubYear)
        assertEquals("Nature", result.papers[0].journal)
        assertEquals("Contact: author1@ox.ac.uk", result.papers[0].fullText)
        assertEquals("0|2020|2", result.nextCursor, "页满按原始返回条数推进 offset")
    }

    @Test
    fun `searchPapers resumes from the persisted shard cursor instead of restarting at page one`() {
        // V-1: 跨运行续 offset —— 进入游标 0|2020|2 时请求必须直接从 offset=2 开始。
        stubSearchResponses(worksResponse(rawCount = 2, totalHits = 250))

        val result = dataSource.searchPapers(
            PaperSearchCriteria(
                pageSize = 2, cursor = "0|2020|2",
                subjectScope = SubjectScopeCatalog.RND_TARGET
            )
        )

        assertEquals(2, sent.single().body["offset"], "续跑必须从上次 offset 继续，不能再发 offset=0")
        assertEquals("0|2020|4", result.nextCursor)
    }

    @Test
    fun `searchPapers rotates to the next yearly shard on the real last page`() {
        // I-1: 实际末页（原始返回不足一页）结束该分片，切到下一年的分片。
        stubSearchResponses(worksResponse(rawCount = 1, totalHits = 250))

        val result = dataSource.searchPapers(
            PaperSearchCriteria(pageSize = 2, cursor = "0|2020|8", publicationYearTo = 2026)
        )

        assertEquals("0|2021|0", result.nextCursor, "2020 分片结束后应切到 2021 分片起点")
    }

    @Test
    fun `searchPapers ends the source after the last yearly shard`() {
        stubSearchResponses(worksResponse(rawCount = 1, totalHits = 250))

        val result = dataSource.searchPapers(
            PaperSearchCriteria(pageSize = 2, cursor = "0|2026|8", publicationYearFrom = 2020, publicationYearTo = 2026)
        )

        assertNull(result.nextCursor, "最后一个分片结束后没有下一分片")
    }

    @Test
    fun `searchPapers stops the shard at the vendor window and reports the uncovered tail`() {
        // V-1/I-4: 到 9000 记录窗口限制 —— 该分片就地停止（不再发出更大的 offset），
        // 未覆盖尾部被显式报告，游标切到下一分片而不是假装穷尽。
        stubSearchResponses(worksResponse(rawCount = 100, totalHits = 200_000))

        val criteria = PaperSearchCriteria(
            pageSize = 100, cursor = "0|2020|9000",
            publicationYearFrom = 2020, publicationYearTo = 2026,
            subjectScope = SubjectScopeCatalog.RND_TARGET
        )
        val page = dataSource.searchCorePage(criteria)

        assertEquals(1, sent.size, "分片到界后不得再发请求")
        assertEquals(9000, sent.single().body["offset"])
        assertTrue(page.windowLimit, "触达窗口必须被显式标记")
        assertEquals(200_000L - 9100L, page.uncoveredTail, "未覆盖尾部 = 总命中 - 已覆盖")
        assertEquals("0|2021|0", page.result.nextCursor, "窗口分片停止后切下一分片")
        assertFalse(page.uncoveredTail == 0L, "不得把窗口截断当成完整覆盖")
    }

    @Test
    fun `searchPapers keeps a legacy scroll cursor out of the paging state`() {
        // I-1/I-4: 曾经用过的 scrollId 不得被当成分片游标挪用，也不得回传给调用方。
        stubSearchResponses(worksResponse(rawCount = 2, totalHits = 250))

        val result = dataSource.searchPapers(
            PaperSearchCriteria(pageSize = 2, cursor = "scroll:scroll-abc",
                subjectScope = SubjectScopeCatalog.RND_TARGET)
        )

        assertEquals(0, sent.single().body["offset"], "非法游标按「无游标」处理，从首分片重开")
        assertEquals("0|2020|2", result.nextCursor)
    }

    @Test
    fun `searchPapers lets operator keywords win and keeps their AND semantics`() {
        // V-3: 人工指定关键词不被目录主题覆盖。
        stubSearchResponses(worksResponse(rawCount = 1, totalHits = 10))

        dataSource.searchPapers(
            PaperSearchCriteria(
                pageSize = 2, keywords = listOf("graph neural network", "recommendation"),
                subjectScope = SubjectScopeCatalog.RND_TARGET
            )
        )

        assertEquals(
            "(graph neural network AND recommendation) AND yearPublished=2020",
            sent.single().body["q"]
        )
    }

    @Test
    fun `searchPapers keeps the wildcard query without keywords and without scope`() {
        stubSearchResponses(worksResponse(rawCount = 1, totalHits = 10))

        dataSource.searchPapers(PaperSearchCriteria(pageSize = 2))

        assertEquals("* AND yearPublished=2020", sent.single().body["q"])
    }

    @Test
    fun `extractAuthorEmails uses fullText field`() {
        val paper = com.weibo.talentintroduction.discovery.domain.PaperMetadata(
            null, null, "10.1234/test",
            "Test Title", 2024, null, emptyList(), "CORE",
            fullText = "Contact researcher@univ.edu, support@springer.com"
        )
        val result = dataSource.extractAuthorEmails(paper)

        assertTrue(result.emails.isNotEmpty())
        assertTrue(result.emails.any { it.email == "researcher@univ.edu" })
    }

    @Test
    fun `extractAuthorEmails returns NO_FULLTEXT when no fullText or downloadUrl`() {
        val paper = com.weibo.talentintroduction.discovery.domain.PaperMetadata(
            null, null, null,
            "Test", 2024, null, emptyList(), "CORE"
        )
        val result = dataSource.extractAuthorEmails(paper)
        assertEquals("NO_FULLTEXT", result.failureReason)
    }

    @Test
    fun `extractAuthorEmails does not bind identity on a first-initial match (I-2)`() {
        // I-2: CORE 全文本里的 jsmith 无法区分两位 Smith —— 不得把某一人的 ORCID/作者ID 绑上去。
        val paper = corePaper(
            "Contact: jsmith@ox.ac.uk",
            listOf(
                PaperAuthor("John", "Smith", "0000-0001", "Oxford, UK", true, openAlexAuthorId = "A5023888391"),
                PaperAuthor("James", "Smith", "0000-0002", "Cambridge, UK", false, openAlexAuthorId = "A5086928770")
            )
        )

        val email = dataSource.extractAuthorEmails(paper).emails.single()

        assertEquals("jsmith@ox.ac.uk", email.email)
        assertNull(email.givenNames)
        assertNull(email.familyNames)
        assertNull(email.orcidId)
        assertNull(email.openAlexAuthorId)
    }

    @Test
    fun `extractAuthorEmails binds an explicit full-name contact entry (I-2)`() {
        val paper = corePaper(
            "Contact: Jane Doe: jane.doe@univ.edu",
            listOf(
                PaperAuthor("John", "Smith", "0000-0001", "Oxford, UK", true, openAlexAuthorId = "A5023888391"),
                PaperAuthor("Jane", "Doe", "0000-0002", "Cambridge, UK", true, openAlexAuthorId = "A5086928770")
            )
        )

        val email = dataSource.extractAuthorEmails(paper).emails.single()

        assertEquals("Jane", email.givenNames)
        assertEquals("Doe", email.familyNames)
        assertEquals("0000-0002", email.orcidId)
        assertEquals("A5086928770", email.openAlexAuthorId)
    }

    @Test
    fun `extractAuthorEmails does not infer sole-author mailbox ownership (I-2)`() {
        val paper = corePaper(
            "Contact: single.author@uni.edu",
            listOf(PaperAuthor("Single", "Author", "0000-0009", "Some Lab", true, openAlexAuthorId = "A999"))
        )

        val email = dataSource.extractAuthorEmails(paper).emails.single()

        assertNull(email.givenNames)
        assertNull(email.familyNames)
        assertNull(email.orcidId)
        assertNull(email.openAlexAuthorId)
    }

    @Test
    fun `CORE source response preserves contact boundaries through parsing`() {
        stubSearchResponses("""{"totalHits":1,"results":[{"title":"Contacts","yearPublished":2024,
            "authors":[{"name":"Jane Doe"},{"name":"John Smith"}],
            "fullText":"Jane Doe: opaque@uni.edu\nJohn Smith\n\nthirdparty@uni.edu"}]}""")
        val paper = dataSource.searchPapers(PaperSearchCriteria(pageSize = 2)).papers.single()
        val result = dataSource.extractAuthorEmails(paper)
        assertEquals("Jane", result.emails.first { it.email == "opaque@uni.edu" }.givenNames)
        assertNull(result.emails.first { it.email == "thirdparty@uni.edu" }.givenNames)
        assertNotNull(result.emails.first().identityEvidence)
    }

    @Test
    fun `CORE PDF fallback uses the same real contact parser`() {
        val realPdf = Mockito.mock(PdfEmailExtractor::class.java)
        Mockito.`when`(realPdf.extract(Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.any(), Mockito.any()))
            .thenAnswer { invocation -> extractOwnershipContent(ownershipPdf("Jane Doe: opaque@uni.edu"),
                org.springframework.http.MediaType.APPLICATION_PDF, invocation.getArgument(1)) }
        val core = CoreDataSource(restTemplate, properties, plainTextExtractor, realPdf)
        val result = core.extractAuthorEmails(corePaper("No email here", listOf(
            PaperAuthor("Jane", "Doe", null, null, false))).copy(downloadUrl = "https://paper.test/pdf"))
        assertEquals("Jane", result.emails.single().givenNames)
    }

    private fun corePaper(fullText: String, authors: List<PaperAuthor>) =
        com.weibo.talentintroduction.discovery.domain.PaperMetadata(
            null, null, "10.1234/test", "Test Title", 2024, null, authors, "CORE", fullText = fullText
        )

    @Test
    fun `searchPapers handles error gracefully`() {
        Mockito.doThrow(RuntimeException("timeout"))
            .`when`(restTemplate).exchange(
                Mockito.anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(),
                Mockito.eq(JsonNode::class.java)
            )
        assertThrows(RuntimeException::class.java) { dataSource.searchPapers(PaperSearchCriteria()) }
    }

    @Test
    fun `init throws when apiKey is blank`() {
        val blankProperties = CoreProperties(enabled = true, apiKey = "", requestDelayMs = 0)
        assertThrows(IllegalArgumentException::class.java) {
            CoreDataSource(restTemplate, blankProperties, plainTextExtractor, pdfExtractor)
        }
    }

    @Test
    fun `init succeeds when apiKey is non-blank`() {
        val validProperties = CoreProperties(enabled = true, apiKey = "valid-key", requestDelayMs = 0)
        assertNotNull(CoreDataSource(restTemplate, validProperties, plainTextExtractor, pdfExtractor))
    }
}
