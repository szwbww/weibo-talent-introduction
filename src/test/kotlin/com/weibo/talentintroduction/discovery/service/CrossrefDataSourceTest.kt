package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.CrossrefProperties
import com.weibo.talentintroduction.config.UnpaywallProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
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
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.net.URLDecoder

class CrossrefDataSourceTest {
    private val properties = CrossrefProperties(
        enabled = true, politeEmail = "test@example.com", requestDelayMs = 0
    )
    private val unpaywallProperties = UnpaywallProperties(
        email = "test@example.com", requestDelayMs = 0
    )
    private val pdfExtractor = Mockito.mock(PdfEmailExtractor::class.java)
    private val mapper = ObjectMapper()

    /**
     * I-1 断言边界：真实 RestTemplate + MockRestServiceServer。
     * 记录的是 RestTemplate 真正发出的请求 URI，而不是 mock 收到的字符串参数，
     * 因此「预编码 + String 重载再编码一次」这类缺陷无法逃过断言。
     */
    private fun wireSearch(body: String): Pair<CrossrefDataSource, List<URI>> {
        val restTemplate = RestTemplate()
        val captured = mutableListOf<URI>()
        MockRestServiceServer.bindTo(restTemplate).build()
            .expect(RequestMatcher { request ->
                captured.add(request.uri)
                true
            })
            .andRespond(MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON))
        val dataSource = CrossrefDataSource(
            restTemplate, properties, UnpaywallClient(restTemplate, unpaywallProperties), pdfExtractor
        )
        return dataSource to captured
    }

    /** 按 servlet 的 form 解码语义还原远端真正看到的值（`+` → 空格、`%XX` 解一次）。 */
    private fun decodedParams(uri: URI): Map<String, String> =
        uri.rawQuery.split("&").associate { pair ->
            val separator = pair.indexOf('=')
            URLDecoder.decode(pair.substring(0, separator), "UTF-8") to
                URLDecoder.decode(pair.substring(separator + 1), "UTF-8")
        }

    private fun searchUri(criteria: PaperSearchCriteria): URI {
        val (dataSource, captured) = wireSearch("""{"message":{"items":[]}}""")
        dataSource.searchPapers(criteria)
        return captured.single()
    }

    @Test
    fun `searchPapers encodes each component exactly once at the request boundary`() {
        // V-1：filter 的 : 和 , 必须原样到达；cursor 内的 + / = 必须保留；中文关键词只编码一次。
        val cursor = "MTA1JTJGMTA1Ny0w+AbC/def=="
        val uri = searchUri(PaperSearchCriteria(keywords = listOf("材料 科学"), cursor = cursor))
        val params = decodedParams(uri)

        assertEquals("/works", uri.path)
        assertEquals(
            "from-pub-date:2020-01-01,until-pub-date:2026-12-31,has-full-text:true",
            params["filter"]
        )
        assertEquals("100", params["rows"])
        assertEquals(cursor, params["cursor"], "cursor 必须逐字往返，不能被二次编码或当成 form 空白")
        assertEquals("材料 科学", params["query"])
        assertEquals("test@example.com", params["mailto"])
        // 线上故障指纹（调查 2026-09-21）：二次编码后远端收到字面 %253A / %252C / %25E6。
        assertFalse(uri.rawQuery.contains("%253A"), "filter 的冒号被二次编码: $uri")
        assertFalse(uri.rawQuery.contains("%252C"), "filter 的逗号被二次编码: $uri")
        assertFalse(uri.rawQuery.contains("%25E6"), "中文关键词被二次编码: $uri")
    }

    @Test
    fun `searchPapers uses the catalogue topic seeds as query bibliographic when no keyword is given`() {
        // I-3: 默认研发检索必须带目录主题词（走 query.bibliographic 题录字段降低噪音），
        // 而不是下发 filter-only 的全领域查询。
        val uri = searchUri(PaperSearchCriteria(subjectScope = SubjectScopeCatalog.RND_TARGET))
        val params = decodedParams(uri)

        assertEquals(
            SubjectScopeCatalog.crossrefQueries(SubjectScopeCatalog.RND_TARGET).joinToString(" "),
            params["query.bibliographic"]
        )
        assertFalse(params.containsKey("query"), "主题种子不得同时下发自由文本 query")
    }

    @Test
    fun `searchPapers lets the operator keyword win over the catalogue topic seeds`() {
        val uri = searchUri(
            PaperSearchCriteria(keywords = listOf("perovskite solar cell"), subjectScope = SubjectScopeCatalog.RND_TARGET)
        )
        val params = decodedParams(uri)

        assertEquals("perovskite solar cell", params["query"], "人工关键词保持改动前的 query 参数")
        assertFalse(params.containsKey("query.bibliographic"), "人工关键词不被目录主题覆盖")
    }

    @Test
    fun `searchPapers keeps the pre-change filter-only query when scope is unknown`() {
        // 手动入口（/run、/run/by-keyword 不传 scope）必须与改动前逐字一致：没有 query 参数。
        val uri = searchUri(PaperSearchCriteria())

        assertFalse(decodedParams(uri).containsKey("query"), "null scope 不得引入新的 query 参数: $uri")
    }

    @Test
    fun `searchPapers omits mailto when polite email is blank`() {
        val blank = CrossrefProperties(enabled = true, politeEmail = "", requestDelayMs = 0)
        val restTemplate = RestTemplate()
        val captured = mutableListOf<URI>()
        MockRestServiceServer.bindTo(restTemplate).build()
            .expect(RequestMatcher { request ->
                captured.add(request.uri)
                true
            })
            .andRespond(MockRestResponseCreators.withSuccess("""{"message":{"items":[]}}""", MediaType.APPLICATION_JSON))
        val dataSource = CrossrefDataSource(
            restTemplate, blank, UnpaywallClient(restTemplate, unpaywallProperties), pdfExtractor
        )

        dataSource.searchPapers(PaperSearchCriteria())

        assertFalse(decodedParams(captured.single()).containsKey("mailto"))
    }

    @Test
    fun `searchPapers parses Crossref works response`() {
        val response = mapOf(
            "message" to mapOf(
                "next-cursor" to "cursor-abc",
                "total-results" to 150,
                "items" to listOf(
                    mapOf(
                        "DOI" to "10.1234/test.1",
                        "title" to listOf("Machine Learning Advances"),
                        "published-print" to mapOf("date-parts" to listOf(listOf(2024))),
                        "container-title" to listOf("Nature"),
                        "author" to listOf(
                            mapOf(
                                "given" to "Alice",
                                "family" to "Wang",
                                "ORCID" to "https://orcid.org/0000-0001-0000-0001",
                                "affiliation" to listOf(mapOf("name" to "Tsinghua University"))
                            )
                        )
                    )
                )
            )
        )
        val (dataSource, _) = wireSearch(mapper.writeValueAsString(response))

        val result = dataSource.searchPapers(PaperSearchCriteria(keywords = listOf("machine learning")))

        assertEquals(1, result.papers.size)
        assertEquals("cursor-abc", result.nextCursor)
        assertEquals(150L, result.totalResults)

        val paper = result.papers[0]
        assertEquals("CROSSREF", paper.source)
        assertEquals("10.1234/test.1", paper.doi)
        assertEquals("Machine Learning Advances", paper.title)
        assertEquals(2024, paper.pubYear)
        assertEquals("Nature", paper.journal)
        assertEquals(1, paper.authors.size)
        assertEquals("Alice", paper.authors[0].givenNames)
        assertEquals("Wang", paper.authors[0].familyNames)
        assertEquals("0000-0001-0000-0001", paper.authors[0].orcidId)
        assertEquals("Tsinghua University", paper.authors[0].affiliation)
    }

    @Test
    fun `extractAuthorEmails returns NO_DOI when doi is null`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = CrossrefDataSource(
            restTemplate, properties, UnpaywallClient(restTemplate, unpaywallProperties), pdfExtractor
        )
        val paper = PaperMetadata(null, null, null, "Test", 2024, null, emptyList(), "CROSSREF")
        val result = dataSource.extractAuthorEmails(paper)
        assertEquals("NO_DOI", result.failureReason)
    }

    @Test
    fun `extractAuthorEmails returns NO_OA_LOCATION when Unpaywall finds no PDF`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        Mockito.doReturn(null)
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        val dataSource = CrossrefDataSource(
            restTemplate, properties, UnpaywallClient(restTemplate, unpaywallProperties), pdfExtractor
        )

        val paper = PaperMetadata(null, null, "10.1234/test", "Test", 2024, null, emptyList(), "CROSSREF")
        val result = dataSource.extractAuthorEmails(paper)
        assertEquals("NO_OA_LOCATION", result.failureReason)
    }

    @Test
    fun `extractAuthorEmails delegates to PDF extractor when PDF url found`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val unpaywallResponse = mapOf(
            "best_oa_location" to mapOf("url_for_pdf" to "http://example.com/paper.pdf")
        )
        Mockito.doReturn(mapper.readTree(mapper.writeValueAsString(unpaywallResponse)))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        // R-1（V-1）：c10 之后 JVM 签名是 5 个参数（末两个带默认值的 deadline / 响应头回调），
        // 只写 3 个 matcher 会留下未完成的 Mockito 状态并污染后续用例；三个调用点都按真实签名的
        // 全部参数打桩，断言（委托结果与失败原因）保持原样。
        Mockito.doReturn(EmailExtractionOutcome(emptyList(), "PDF_PARSE", "NO_EMAIL_IN_TEXT"))
            .`when`(pdfExtractor).extract(
                Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.any(), Mockito.any()
            )

        val dataSource = CrossrefDataSource(
            restTemplate, properties, UnpaywallClient(restTemplate, unpaywallProperties), pdfExtractor
        )
        val paper = PaperMetadata(null, null, "10.1234/test", "Test", 2024, null, emptyList(), "CROSSREF")
        val result = dataSource.extractAuthorEmails(paper)
        assertEquals("NO_EMAIL_IN_TEXT", result.failureReason)
    }

    @Test
    fun `init throws when unpaywall email is blank`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val unpaywallProps = UnpaywallProperties(email = "", requestDelayMs = 0)
        val unpaywall = UnpaywallClient(restTemplate, unpaywallProps)
        val crossrefProps = CrossrefProperties(enabled = true, politeEmail = "test@example.com", requestDelayMs = 0)
        assertThrows(IllegalArgumentException::class.java) {
            CrossrefDataSource(restTemplate, crossrefProps, unpaywall, pdfExtractor)
        }
    }

    @Test
    fun `init succeeds when unpaywall email is configured`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val unpaywallProps = UnpaywallProperties(email = "test@example.com", requestDelayMs = 0)
        val unpaywall = UnpaywallClient(restTemplate, unpaywallProps)
        val crossrefProps = CrossrefProperties(enabled = true, politeEmail = "test@example.com", requestDelayMs = 0)
        assertNotNull(CrossrefDataSource(restTemplate, crossrefProps, unpaywall, pdfExtractor))
    }

    @Test
    fun `searchPapers returns an empty exhausted page when Crossref reports no items`() {
        val (dataSource, _) = wireSearch("""{"message":{"next-cursor":null,"total-results":0,"items":[]}}""")

        val result = dataSource.searchPapers(PaperSearchCriteria())

        assertTrue(result.papers.isEmpty())
        assertNull(result.nextCursor)
        assertEquals(0L, result.totalResults)
    }
}
