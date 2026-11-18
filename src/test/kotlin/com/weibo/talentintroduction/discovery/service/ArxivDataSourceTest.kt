package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ArxivProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.web.client.RestTemplate
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

class ArxivDataSourceTest {
    private val pdfExtractor = Mockito.mock(PdfEmailExtractor::class.java)

    /** 一次搜索的观测点：DataSource、被 mock 的 RestTemplate、发出的 URL 模板。 */
    private inner class Harness(val dataSource: ArxivDataSource, val restTemplate: RestTemplate) {
        val searchUrls = mutableListOf<String>()

        fun stubSearch(status: HttpStatus = HttpStatus.OK, body: String = OK_FEED) {
            Mockito.`when`(
                restTemplate.exchange(
                    Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.any(), Mockito.eq(String::class.java)
                )
            ).thenAnswer { invocation ->
                searchUrls.add(invocation.arguments[0] as String)
                if (body.isEmpty()) ResponseEntity.status(status).build<String>()
                else ResponseEntity.status(status).body(body)
            }
        }

        fun capturedSearchUrl(criteria: PaperSearchCriteria): String {
            stubSearch()
            dataSource.searchPapers(criteria)
            return searchUrls.single()
        }
    }

    private fun harness(properties: ArxivProperties = ArxivProperties(enabled = true, requestDelayMs = 0)): Harness {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        return Harness(ArxivDataSource(restTemplate, properties, pdfExtractor), restTemplate)
    }

    /**
     * I-2 断言边界：真实 RestTemplate + MockRestServiceServer，断言的是真正发出的请求 URI。
     * legacy 配置里的官方 http 入口在这里被观察到，规范化不是「内部函数被调用」。
     */
    private fun capturedRequestUri(properties: ArxivProperties): URI {
        val restTemplate = RestTemplate()
        val captured = mutableListOf<URI>()
        MockRestServiceServer.bindTo(restTemplate).build()
            .expect(RequestMatcher { request ->
                captured.add(request.uri)
                true
            })
            .andRespond(MockRestResponseCreators.withSuccess(OK_FEED, MediaType.APPLICATION_XML))
        ArxivDataSource(restTemplate, properties, pdfExtractor)
            .searchPapers(PaperSearchCriteria(publicationYearFrom = 2020, publicationYearTo = 2026))
        return captured.single()
    }

    @Test
    fun `searchPapers issues HTTPS for both the default and the legacy http base url`() {
        val fromDefault = capturedRequestUri(ArxivProperties(enabled = true, requestDelayMs = 0))
        val fromLegacy = capturedRequestUri(
            ArxivProperties(enabled = true, baseUrl = "http://export.arxiv.org/api", requestDelayMs = 0)
        )

        for (uri in listOf(fromDefault, fromLegacy)) {
            assertEquals("https", uri.scheme, "arXiv 出站必须走 HTTPS: $uri")
            assertEquals("export.arxiv.org", uri.host)
            assertTrue(uri.rawQuery.contains("search_query=all:*"), "无关键词时必须保持改动前的 all:* 查询: $uri")
        }
    }

    @Test
    fun `DOM parses Atom entry elements correctly`() {
        val atomXml = javaClass.classLoader.getResource("arxiv/atom-response-sample.xml")!!.readText()
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val doc = factory.newDocumentBuilder()
            .parse(java.io.ByteArrayInputStream(atomXml.toByteArray(Charsets.UTF_8)))
        val entries = doc.getElementsByTagName("entry")
        assertTrue(entries.length > 0, "DOM should find entry elements, found ${entries.length}")

        val entry = entries.item(0) as org.w3c.dom.Element
        val publishedNodes = entry.getElementsByTagName("published")
        assertTrue(publishedNodes.length > 0, "Should find published element, found ${publishedNodes.length}")
        assertEquals("2024-01-15T10:00:00Z", publishedNodes.item(0).textContent)
    }

    @Test
    fun `searchPapers uses all-star query when no keywords and scope null`() {
        // I4-2 锚点断言：无关键词 + subjectScope == null 时保持改动前的 all:* 兜底。
        val url = harness().capturedSearchUrl(PaperSearchCriteria())
        assertTrue(url.contains("search_query=all:*"), "null scope must keep pre-change all-star query")
    }

    @Test
    fun `searchPapers builds OR-joined category query for RND_TARGET scope`() {
        val url = harness().capturedSearchUrl(PaperSearchCriteria(subjectScope = SubjectScopeCatalog.RND_TARGET))
        assertTrue(
            url.contains("search_query=cat:cs*+OR+cat:eess*+OR+cat:cond-mat*+OR+cat:physics*"),
            "RND_TARGET must map to arXiv category prefixes, got: $url"
        )
    }

    @Test
    fun `searchPapers prefers keywords over subjectScope`() {
        val url = harness().capturedSearchUrl(
            PaperSearchCriteria(keywords = listOf("deep learning"), subjectScope = SubjectScopeCatalog.RND_TARGET)
        )
        assertTrue(url.contains("search_query=all:\"deep+learning\""), "keywords branch must stay verbatim")
        assertFalse(url.contains("cat:"), "subjectScope must not leak into the keywords branch")
    }

    @Test
    fun `searchPapers keywords branch is unaffected by scope`() {
        val harness = harness()
        harness.stubSearch()
        harness.dataSource.searchPapers(
            PaperSearchCriteria(keywords = listOf("deep learning"), subjectScope = SubjectScopeCatalog.RND_TARGET)
        )
        harness.dataSource.searchPapers(
            PaperSearchCriteria(keywords = listOf("deep learning"), subjectScope = null)
        )
        assertEquals(
            harness.searchUrls[0], harness.searchUrls[1],
            "keywords present must produce identical query regardless of scope"
        )
    }

    @Test
    fun `searchPapers keeps year and cursor paging parameters`() {
        val url = harness().capturedSearchUrl(
            PaperSearchCriteria(
                publicationYearFrom = 2021,
                publicationYearTo = 2023,
                pageSize = 25,
                cursor = "50"
            )
        )
        assertTrue(url.contains("start=50"), "cursor must drive the arXiv start offset: $url")
        assertTrue(url.contains("max_results=25"), "page size must be requested: $url")
    }

    @Test
    fun `searchPapers fails explicitly on an empty redirect response`() {
        // I-2：线上实测的 HTTP 301 空体必须显式失败，绝不能被当成「零结果正常翻页」。
        val harness = harness(ArxivProperties(enabled = true, baseUrl = "http://export.arxiv.org/api", requestDelayMs = 0))
        harness.stubSearch(status = HttpStatus.MOVED_PERMANENTLY, body = "")

        val failure = assertThrows(IllegalStateException::class.java) {
            harness.dataSource.searchPapers(PaperSearchCriteria())
        }
        assertEquals("ARXIV_EMPTY_OR_REDIRECT", failure.message)
    }

    @Test
    fun `searchPapers fails explicitly on a blank body with ok status`() {
        val harness = harness()
        harness.stubSearch(status = HttpStatus.OK, body = "")

        val failure = assertThrows(IllegalStateException::class.java) {
            harness.dataSource.searchPapers(PaperSearchCriteria())
        }
        assertEquals("ARXIV_EMPTY_OR_REDIRECT", failure.message)
    }

    @Test
    fun `searchPapers fails explicitly on a non-Atom payload`() {
        val harness = harness()
        harness.stubSearch(body = "<html><body>upstream proxy error</body></html>")

        assertThrows(IllegalStateException::class.java) {
            harness.dataSource.searchPapers(PaperSearchCriteria())
        }
    }

    @Test
    fun `searchPapers fails explicitly on a malformed Atom payload`() {
        val harness = harness()
        harness.stubSearch(body = "<feed xmlns=\"http://www.w3.org/2005/Atom\"><entry>")

        assertThrows(IllegalStateException::class.java) {
            harness.dataSource.searchPapers(PaperSearchCriteria())
        }
    }

    @Test
    fun `searchPapers fails explicitly on an arXiv error entry`() {
        // 线上形态（2026-09-21 实测）：错误以单个 entry 返回，其 id 指向 arxiv.org/api/errors#。
        val harness = harness()
        harness.stubSearch(body = ERROR_FEED)

        val failure = assertThrows(IllegalStateException::class.java) {
            harness.dataSource.searchPapers(PaperSearchCriteria())
        }
        assertEquals("ARXIV_ERROR_ENTRY", failure.message)
    }

    @Test
    fun `parseAtomResponse parses Atom XML`() {
        val atomXml = javaClass.classLoader.getResource("arxiv/atom-response-sample.xml")!!.readText()
        val criteria = PaperSearchCriteria(publicationYearFrom = 2020, publicationYearTo = 2026)
        val result = harness().dataSource.parseAtomResponse(atomXml, criteria)

        assertEquals(2, result.papers.size)
        assertEquals(2L, result.totalResults)
        assertEquals("ARXIV", result.papers[0].source)
        assertEquals("arXiv:2401.00001", result.papers[0].doi)
        assertEquals(2024, result.papers[0].pubYear)
    }

    @Test
    fun `parseAtomResponse filters by publication year`() {
        val atomXml = javaClass.classLoader.getResource("arxiv/atom-response-sample.xml")!!.readText()
        val criteria = PaperSearchCriteria(publicationYearFrom = 2025, publicationYearTo = 2026)
        val result = harness().dataSource.parseAtomResponse(atomXml, criteria)
        assertEquals(0, result.papers.size)
        // 样例共 2 条且 totalResults=2，整页被年份过滤掉但页码已到底。
        assertNull(result.nextCursor)
    }

    @Test
    fun `parseAtomResponse keeps the nextCursor of a year-filtered page`() {
        // c2 契约：年份过滤后的空页必须仍给出按原始条目数推导的 nextCursor，翻页不能假装穷尽。
        val criteria = PaperSearchCriteria(publicationYearFrom = 2025, publicationYearTo = 2026)
        val result = harness().dataSource.parseAtomResponse(FILTERED_FEED, criteria)

        assertEquals(0, result.papers.size)
        assertEquals("2", result.nextCursor)
        assertEquals(200L, result.totalResults)
    }

    @Test
    fun `parseAtomResponse rejects an empty body`() {
        assertThrows(IllegalStateException::class.java) {
            harness().dataSource.parseAtomResponse(null, PaperSearchCriteria())
        }
        assertThrows(IllegalStateException::class.java) {
            harness().dataSource.parseAtomResponse("   ", PaperSearchCriteria())
        }
    }

    private fun feed(start: Int, total: Int, vararg dates: String): String {
        val entries = dates.mapIndexed { i, date ->
            "<entry><id>http://arxiv.org/abs/${start + i}</id><published>$date</published></entry>"
        }.joinToString("")
        return """<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">
            <opensearch:totalResults>$total</opensearch:totalResults><opensearch:startIndex>$start</opensearch:startIndex>
            $entries</feed>"""
    }

    @Test
    fun `legacy deep offsets recover the last readable minute then resume the bounded query`() {
        for (cursor in listOf("10000", "12000")) {
            val rest = RestTemplate()
            val server = MockRestServiceServer.bindTo(rest).build()
            val query = "cat:cs*+OR+cat:eess*+OR+cat:cond-mat*+OR+cat:physics*"
            val bounded = "($query)+AND+submittedDate:%5B202001010000+TO+202611041643%5D"
            val order = "&sortBy=submittedDate&sortOrder=descending"
            fun expect(queryAndPage: String, body: String) {
                server.expect(RequestMatcher { request -> assertEquals("search_query=$queryAndPage$order", request.uri.rawQuery) })
                    .andRespond(MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_XML))
            }
            val date = "2026-11-04T16:42:53Z"
            expect("$query&start=9999&max_results=1", feed(9999, 15000, date))
            expect("$bounded&start=0&max_results=1", feed(0, 2, date))
            expect("$bounded&start=1&max_results=1", feed(1, 2, "2025-12-31T23:59:59Z"))
            val source = ArxivDataSource(rest, ArxivProperties(requestDelayMs = 0), pdfExtractor)
            val criteria = PaperSearchCriteria(pageSize = 1, cursor = cursor, subjectScope = SubjectScopeCatalog.RND_TARGET)
            val first = source.searchPapers(criteria)
            assertEquals("t1:202611041643:1", first.nextCursor)
            assertEquals(2026, first.papers.single().pubYear)
            val second = source.searchPapers(criteria.copy(cursor = first.nextCursor))
            assertEquals(2025, second.papers.single().pubYear)
            assertNull(second.nextCursor)
            server.verify()
        }
    }

    @Test
    fun `last readable offset clamps page size and rolls forward into the older time range`() {
        val h = harness()
        h.stubSearch(body = feed(9999, 12000, "2026-11-04T16:42:00Z"))
        val first = h.dataSource.searchPapers(PaperSearchCriteria(cursor = "9999"))
        assertTrue(h.searchUrls.single().contains("&start=9999&max_results=1&"))
        assertEquals("t1:202611041643:0", first.nextCursor)
        val second = h.dataSource.parseAtomResponse(feed(9999, 12000, "2024-12-31T23:59:59Z"),
            PaperSearchCriteria(cursor = "t1:202611041643:9999"))
        assertEquals("t1:202501010000:0", second.nextCursor)
    }

    @Test
    fun `filtered boundary page clamps to the requested years and ends below the lower bound`() {
        val ds = harness().dataSource
        val criteria = PaperSearchCriteria(publicationYearFrom = 2020, publicationYearTo = 2022, cursor = "9999")
        val tooNew = ds.parseAtomResponse(feed(9999, 12000, "2026-11-04T16:42:53Z"), criteria)
        assertTrue(tooNew.papers.isEmpty())
        assertEquals("t1:202301010000:0", tooNew.nextCursor)
        val tooOld = ds.parseAtomResponse(feed(9999, 12000, "2019-12-31T23:55:00Z"), criteria)
        assertNull(tooOld.nextCursor)
    }

    @Test
    fun `unpageable minute corrupt cursors and inconsistent responses fail before advancement`() {
        val h = harness()
        val timestamp = "2026-11-04T16:42:53Z"
        val stalled = assertThrows(IllegalStateException::class.java) {
            h.dataSource.parseAtomResponse(feed(9999, 20000, timestamp), PaperSearchCriteria(cursor = "t1:202611041643:9999"))
        }
        assertEquals("ARXIV_TIME_WINDOW_STALLED", stalled.message)
        for (cursor in listOf("x", "-1", "t1:202611041643:10000")) {
            assertThrows(IllegalArgumentException::class.java) { h.dataSource.searchPapers(PaperSearchCriteria(cursor = cursor)) }
        }
        assertNull(h.dataSource.parseAtomResponse(feed(9999, 2), PaperSearchCriteria(cursor = "9999")).nextCursor)
        for (badFeed in listOf(feed(9999, -1), feed(9999, 0, timestamp), feed(9999, 20000), feed(0, 20000, timestamp), ERROR_FEED.replace("#start_must_be_non-negative", ""))) {
            assertThrows(IllegalStateException::class.java) {
                h.dataSource.parseAtomResponse(badFeed, PaperSearchCriteria(cursor = "9999"))
            }
        }
    }

    @Test
    fun `extractAuthorEmails delegates to PDF extractor`() {
        // R-1（V-1）：与 Crossref 用例同理 —— 按 c10 的 5 参数 JVM 签名打桩，委托断言保持原样。
        Mockito.doReturn(EmailExtractionOutcome(emptyList(), "PDF_PARSE", "NO_EMAIL_IN_TEXT"))
            .`when`(pdfExtractor).extract(
                Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.any(), Mockito.any()
            )

        val paper = PaperMetadata(null, null, "arXiv:2401.00001", "Test", 2024, null, emptyList(), "ARXIV")
        val result = harness().dataSource.extractAuthorEmails(paper)
        assertEquals("NO_EMAIL_IN_TEXT", result.failureReason)
    }

    @Test
    fun `extractAuthorEmails returns NO_DOI when no arxiv id`() {
        val paper = PaperMetadata(null, null, null, "Test", 2024, null, emptyList(), "ARXIV")
        val result = harness().dataSource.extractAuthorEmails(paper)
        assertEquals("NO_DOI", result.failureReason)
    }

    private companion object {
        const val OK_FEED = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <opensearch:totalResults xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">0</opensearch:totalResults>
</feed>"""

        const val ERROR_FEED = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <opensearch:totalResults xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">1</opensearch:totalResults>
  <entry>
    <id>https://arxiv.org/api/errors#start_must_be_non-negative</id>
    <title>Error</title>
  </entry>
</feed>"""

        const val FILTERED_FEED = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <opensearch:totalResults xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">200</opensearch:totalResults>
  <entry>
    <id>http://arxiv.org/abs/2401.00001</id>
    <title>Old paper one</title>
    <published>2024-01-15T10:00:00Z</published>
  </entry>
  <entry>
    <id>http://arxiv.org/abs/2401.00002</id>
    <title>Old paper two</title>
    <published>2024-01-16T10:00:00Z</published>
  </entry>
</feed>"""
    }
}
