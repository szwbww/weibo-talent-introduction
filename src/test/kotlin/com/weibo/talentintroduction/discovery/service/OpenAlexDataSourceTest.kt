package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.OpenAlexBudgetDeferredException
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.config.OpenAlexRequestPolicy
import com.weibo.talentintroduction.config.PdfExtractionProperties
import com.weibo.talentintroduction.config.Permit
import com.weibo.talentintroduction.config.PolicyTimeSource
import com.weibo.talentintroduction.config.RequestKind
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResponseExtractor
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.time.Instant

class OpenAlexDataSourceTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val europePmc = Mockito.mock(EuropePmcDataSource::class.java)
    private val pdfExtractor = Mockito.mock(PdfEmailExtractor::class.java)
    private val unpaywallClient = Mockito.mock(UnpaywallClient::class.java)
    private val downloadAttempts = mutableListOf<String>()
    private val downloadDeadlines = mutableListOf<Instant>()
    private val properties = OpenAlexProperties(
        enabled = true,
        politeEmail = "",
        baseUrl = "https://api.openalex.org",
        requestDelayMs = 0
    )
    private val mapper = ObjectMapper()

    /** Keeps the shared quota policy off the wall clock so no test pays a real rate-limit sleep. */
    private class TestTimeSource : PolicyTimeSource {
        private var nanos: Long = 0

        override fun now(): Instant = Instant.parse("2026-09-21T02:41:17Z")

        override fun nanoTime(): Long = nanos

        override fun sleep(ms: Long) {
            nanos += ms * 1_000_000L
        }
    }

    private val policy = OpenAlexRequestPolicy(properties, TestTimeSource())
    private val dataSource = OpenAlexDataSource(restTemplate, properties, europePmc, pdfExtractor, unpaywallClient, policy)

    @Test
    fun `searchPapers parses OpenAlex works response`() {
        val sampleJson = javaClass.classLoader
            .getResource("openalex/works-response-sample.json")!!.readText()
        val response = mapper.readTree(sampleJson)

        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(response))

        val criteria = PaperSearchCriteria(keywords = listOf("deep learning"))
        val result = dataSource.searchPapers(criteria)

        assertEquals(1, result.papers.size)
        assertEquals("AoE/EDE5MzM5NTMy", result.nextCursor)
        assertEquals(2L, result.totalResults)

        val paper = result.papers[0]
        assertEquals("OPENALEX", paper.source)
        assertEquals("PMC9876543", paper.pmcId)
        assertEquals("36543210", paper.pmid)
        assertEquals("10.1038/s41586-024-00001-2", paper.doi)
        assertEquals("Deep Learning for Climate Prediction", paper.title)
        assertEquals(2024, paper.pubYear)
        assertEquals("Nature", paper.journal)
        assertEquals(2, paper.authors.size)

        assertEquals("John", paper.authors[0].givenNames)
        assertEquals("Smith", paper.authors[0].familyNames)
        assertEquals("0000-0001-2345-6789", paper.authors[0].orcidId)
        assertEquals("University of Oxford", paper.authors[0].affiliation)
        assertTrue(paper.authors[0].isCorresponding)
        assertEquals("education", paper.authors[0].institutionType)
        assertEquals("Alice", paper.authors[1].givenNames)
        assertEquals("Jones", paper.authors[1].familyNames)
        assertNull(paper.authors[1].orcidId)
        assertEquals("company", paper.authors[1].institutionType)
    }

    @Test
    fun `enrichAuthor returns academic metrics`() {
        val sampleJson = javaClass.classLoader
            .getResource("openalex/author-response-sample.json")!!.readText()
        val response = mapper.readTree(sampleJson)

        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(response))

        val result = dataSource.enrichAuthor("A1234567")
        assertNotNull(result)
        assertEquals(18, result!!.hIndex)
        assertEquals(1200, result.citationCount)
        assertEquals(45, result.worksCount)
        assertEquals("nonprofit", result.institutionType)
        // I1-1/I1-2：最大有产出 year = 2010；2022 的 works_count = 0 不算发表年，不得选它。
        assertEquals(2010, result.lastPublicationYear)
    }

    @Test
    fun `enrichAuthor lastPublicationYear is max works_count year regardless of array order (I1-1)`() {
        // 数组为降序：2022 在前、2018 在后，且含一个 works_count = 0 的更大年份 2025。
        // I1-1：取所有 works_count > 0 的 year 最大值（=2022），不得取数组首项。
        // I1-2：2025 的 works_count = 0 不算发表年。
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[],
               "counts_by_year":[
                 {"year":2022,"works_count":2,"oa_works_count":0,"cited_by_count":10},
                 {"year":2018,"works_count":5,"oa_works_count":4,"cited_by_count":50},
                 {"year":2025,"works_count":0,"oa_works_count":0,"cited_by_count":99}
               ]}"""
        )
        assertEquals(2022, dataSource.enrichAuthor("A1")!!.lastPublicationYear)
    }

    @Test
    fun `enrichAuthor lastPublicationYear null when counts_by_year missing (I1-3)`() {
        stubAuthorEnrichment("""{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[]}""")
        assertNull(dataSource.enrichAuthor("A1")!!.lastPublicationYear)
    }

    @Test
    fun `enrichAuthor lastPublicationYear null when counts_by_year empty (I1-3)`() {
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[],
               "counts_by_year":[]}"""
        )
        assertNull(dataSource.enrichAuthor("A1")!!.lastPublicationYear)
    }

    @Test
    fun `enrichAuthor lastPublicationYear null when all works_count zero (I1-3)`() {
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[],
               "counts_by_year":[
                 {"year":2018,"works_count":0,"oa_works_count":0,"cited_by_count":5},
                 {"year":2022,"works_count":0,"oa_works_count":0,"cited_by_count":8}
               ]}"""
        )
        assertNull(dataSource.enrichAuthor("A1")!!.lastPublicationYear)
    }

    @Test
    fun `buildFilter includes is_oa and publication_year`() {
        val response = mapper.readTree("""{"meta":{"count":0,"next_cursor":null},"results":[]}""")
        val urlCaptor = mutableListOf<String>()

        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenAnswer { invocation ->
            urlCaptor.add(invocation.arguments[0] as String)
            ResponseEntity.ok(response)
        }

        val criteria = PaperSearchCriteria(
            publicationYearFrom = 2022,
            publicationYearTo = 2025,
            openAccessOnly = true,
            excludeCountries = listOf("CN")
        )
        dataSource.searchPapers(criteria)

        val url = urlCaptor.single()
        assertTrue(url.contains("is_oa:true"), "Filter should contain is_oa:true")
        assertTrue(url.contains("publication_year:2022-2025"), "Filter should contain year range")
        assertTrue(url.contains("authorships.institutions.country_code:!CN"), "Filter should exclude CN")
    }

    @Test
    fun `buildFilter is byte-identical to pre-change when subjectScope is null`() {
        // I4-2 锚点断言：subjectScope == null 时查询串必须与改动前逐字相同（硬编码字面量，不得用被测代码反算）。
        val response = mapper.readTree("""{"meta":{"count":0,"next_cursor":null},"results":[]}""")
        val urlCaptor = mutableListOf<String>()

        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenAnswer { invocation ->
            urlCaptor.add(invocation.arguments[0] as String)
            ResponseEntity.ok(response)
        }

        val criteria = PaperSearchCriteria(
            publicationYearFrom = 2022,
            publicationYearTo = 2025,
            openAccessOnly = true,
            excludeCountries = listOf("CN"),
            subjectScope = null
        )
        dataSource.searchPapers(criteria)

        assertEquals(
            "https://api.openalex.org/works?filter=is_oa:true,publication_year:2022-2025," +
                "authorships.institutions.country_code:!CN&per_page=100&cursor=*",
            urlCaptor.single()
        )
    }

    @Test
    fun `buildFilter appends RND_TARGET field lock after existing parts`() {
        // I4-1/I4-3: RND_TARGET 下原有三段（is_oa、publication_year、country_code:!CN）位置与内容不变，
        // 学科片段追加在其后（硬编码字面量）。
        val response = mapper.readTree("""{"meta":{"count":0,"next_cursor":null},"results":[]}""")
        val urlCaptor = mutableListOf<String>()

        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenAnswer { invocation ->
            urlCaptor.add(invocation.arguments[0] as String)
            ResponseEntity.ok(response)
        }

        val criteria = PaperSearchCriteria(
            publicationYearFrom = 2022,
            publicationYearTo = 2025,
            openAccessOnly = true,
            excludeCountries = listOf("CN"),
            subjectScope = SubjectScopeCatalog.RND_TARGET
        )
        dataSource.searchPapers(criteria)

        assertEquals(
            "https://api.openalex.org/works?filter=is_oa:true,publication_year:2022-2025," +
                "authorships.institutions.country_code:!CN,primary_topic.field.id:22|31|17|25|21|15" +
                "&per_page=100&cursor=*",
            urlCaptor.single()
        )
    }

    @Test
    fun `searchPapers handles API error gracefully`() {
        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenThrow(RuntimeException("API unavailable"))

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException::class.java) { dataSource.searchPapers(PaperSearchCriteria()) }
    }

    @Test
    fun `enrichAuthorByOrcidWithReason returns RateLimited on HTTP 429`() {
        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenThrow(
            HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders(), ByteArray(0), null
            )
        )

        val outcome = dataSource.enrichAuthorByOrcidWithReason("0000-0001-2345-6789")
        assertInstanceOf(EnrichmentOutcome.RateLimited::class.java, outcome)
    }

    @Test
    fun `enrichAuthorByOrcidWithReason returns ApiError on HTTP 500`() {
        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenThrow(
            HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", HttpHeaders(), ByteArray(0), null
            )
        )

        val outcome = dataSource.enrichAuthorByOrcidWithReason("0000-0001-2345-6789")
        assertInstanceOf(EnrichmentOutcome.ApiError::class.java, outcome)
    }

    @Test
    fun `enrichAuthor rethrows 429 instead of returning null`() {
        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenThrow(
            HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders(), ByteArray(0), null
            )
        )

        assertThrows(HttpClientErrorException::class.java) {
            dataSource.enrichAuthor("A1234567")
        }
    }

    @Test
    fun `batchEnrichByOrcids parses multiple authors from search response`() {
        val batchJson = """
            {
              "meta": {"count": 3},
              "results": [
                {
                  "orcid": "https://orcid.org/0000-0001",
                  "works_count": 10,
                  "cited_by_count": 100,
                  "summary_stats": {"h_index": 5},
                  "topics": [{"display_name": "AI", "count": 3}],
                  "last_known_institutions": [{"display_name": "Big Corp", "type": "company"}],
                  "counts_by_year": [
                    {"year": 2015, "works_count": 1, "oa_works_count": 1, "cited_by_count": 20},
                    {"year": 2020, "works_count": 3, "oa_works_count": 2, "cited_by_count": 60},
                    {"year": 2023, "works_count": 0, "oa_works_count": 0, "cited_by_count": 5}
                  ],
                  "works_api_url": "https://api.openalex.org/works?filter=author.id:A1"
                },
                {
                  "orcid": "https://orcid.org/0000-0002",
                  "works_count": 20,
                  "cited_by_count": 200,
                  "summary_stats": {"h_index": 8},
                  "topics": [],
                  "works_api_url": "https://api.openalex.org/works?filter=author.id:A2"
                },
                {
                  "orcid": "https://orcid.org/0000-0003",
                  "works_count": 30,
                  "cited_by_count": 300,
                  "summary_stats": {"h_index": 12},
                  "topics": [],
                  "works_api_url": "https://api.openalex.org/works?filter=author.id:A3"
                }
              ]
            }
        """.trimIndent()
        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(mapper.readTree(batchJson)))

        val orcids = listOf("0000-0001", "0000-0002", "0000-0003", "0000-0004", "0000-0005")
        val outcomes = dataSource.batchEnrichByOrcids(orcids)

        assertEquals(5, outcomes.size)
        assertInstanceOf(EnrichmentOutcome.Success::class.java, outcomes["0000-0001"])
        assertInstanceOf(EnrichmentOutcome.Success::class.java, outcomes["0000-0002"])
        assertInstanceOf(EnrichmentOutcome.Success::class.java, outcomes["0000-0003"])
        assertEquals(EnrichmentOutcome.NotFound, outcomes["0000-0004"])
        assertEquals(EnrichmentOutcome.NotFound, outcomes["0000-0005"])

        val first = outcomes["0000-0001"] as EnrichmentOutcome.Success
        assertEquals(5, first.data.hIndex)
        assertNull(first.data.recentWorkTitles)
        assertNull(first.data.patentTitles)
        assertEquals("company", first.data.institutionType)
        // 批量路径共用 parseAuthorEnrichmentFromNode：同样解析 counts_by_year（I1-1/I1-2）。
        assertEquals(2020, first.data.lastPublicationYear)
    }

    @Test
    fun `batchEnrichByOrcids returns RateLimited for all orcids on HTTP 429`() {
        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenThrow(
            HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders(), ByteArray(0), null
            )
        )

        val orcids = listOf("0000-0001", "0000-0002")
        val outcomes = dataSource.batchEnrichByOrcids(orcids)

        assertTrue(outcomes.values.all { it is EnrichmentOutcome.RateLimited })
    }

    @Test
    fun `batchEnrichByOrcids skips works and patents when disabled`() {
        val batchJson = """
            {
              "meta": {"count": 1},
              "results": [
                {
                  "orcid": "https://orcid.org/0000-0001",
                  "works_count": 10,
                  "cited_by_count": 100,
                  "summary_stats": {"h_index": 5},
                  "topics": [],
                  "works_api_url": "https://api.openalex.org/works?filter=author.id:A1"
                }
              ]
            }
        """.trimIndent()
        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(mapper.readTree(batchJson)))

        dataSource.batchEnrichByOrcids(listOf("0000-0001"))

        Mockito.verify(restTemplate, Mockito.times(1)).exchange(
            Mockito.anyString(),
            Mockito.eq(HttpMethod.GET),
            Mockito.nullable(HttpEntity::class.java),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `batchEnrichByOrcids fetches works when enabled`() {
        val worksEnabledSource = OpenAlexDataSource(
            restTemplate,
            properties.copy(fetchWorksEnabled = true),
            europePmc,
            pdfExtractor,
            unpaywallClient,
            policy
        )
        val batchJson = """
            {
              "meta": {"count": 1},
              "results": [
                {
                  "orcid": "https://orcid.org/0000-0001",
                  "works_count": 10,
                  "cited_by_count": 100,
                  "summary_stats": {"h_index": 5},
                  "topics": [],
                  "works_api_url": "https://api.openalex.org/works?filter=author.id:A1"
                }
              ]
            }
        """.trimIndent()
        val worksJson = """
            {"results":[{"title":"Recent Paper"}]}
        """.trimIndent()
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/authors?"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(mapper.readTree(batchJson)))
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/works?"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(mapper.readTree(worksJson)))

        val outcomes = worksEnabledSource.batchEnrichByOrcids(listOf("0000-0001"))

        val success = outcomes["0000-0001"] as EnrichmentOutcome.Success
        assertEquals(listOf("Recent Paper"), success.data.recentWorkTitles)
        Mockito.verify(restTemplate, Mockito.times(2)).exchange(
            Mockito.anyString(),
            Mockito.eq(HttpMethod.GET),
            Mockito.nullable(HttpEntity::class.java),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `enrichAuthor disciplineCategory null when no topics`() {
        stubAuthorEnrichment("""{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[]}""")
        assertNull(dataSource.enrichAuthor("A1")!!.disciplineCategory)
    }

    @Test
    fun `enrichAuthor disciplineCategory null when all domains unknown`() {
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[
              {"display_name":"X","count":5,"domain":{"display_name":"Arts"}},
              {"display_name":"Y","count":9,"domain":{"display_name":"Unknown Domain"}}
            ]}"""
        )
        assertNull(dataSource.enrichAuthor("A1")!!.disciplineCategory)
    }

    @Test
    fun `enrichAuthor disciplineCategory HUMANITIES when Social outweighs Physical`() {
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[
              {"display_name":"Physics","count":10,"domain":{"display_name":"Physical Sciences"}},
              {"display_name":"Sociology","count":15,"domain":{"display_name":"Social Sciences"}}
            ]}"""
        )
        assertEquals("HUMANITIES", dataSource.enrichAuthor("A1")!!.disciplineCategory)
    }

    @Test
    fun `enrichAuthor disciplineCategory STEM when Physical plus Health outweigh Social`() {
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[
              {"display_name":"Physics","count":10,"domain":{"display_name":"Physical Sciences"}},
              {"display_name":"Medicine","count":6,"domain":{"display_name":"Health Sciences"}},
              {"display_name":"Sociology","count":15,"domain":{"display_name":"Social Sciences"}}
            ]}"""
        )
        assertEquals("STEM", dataSource.enrichAuthor("A1")!!.disciplineCategory)
    }

    @Test
    fun `enrichAuthor disciplineCategory STEM on tie`() {
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[
              {"display_name":"Physics","count":10,"domain":{"display_name":"Physical Sciences"}},
              {"display_name":"Sociology","count":10,"domain":{"display_name":"Social Sciences"}}
            ]}"""
        )
        assertEquals("STEM", dataSource.enrichAuthor("A1")!!.disciplineCategory)
    }

    @Test
    fun `enrichAuthor disciplineCategory ignores unknown domains in sum`() {
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[
              {"display_name":"Physics","count":5,"domain":{"display_name":"Physical Sciences"}},
              {"display_name":"Art","count":100,"domain":{"display_name":"Arts"}},
              {"display_name":"Sociology","count":8,"domain":{"display_name":"Social Sciences"}}
            ]}"""
        )
        assertEquals("HUMANITIES", dataSource.enrichAuthor("A1")!!.disciplineCategory)
    }

    @Test
    fun `enrichAuthor disciplineCategory uses all topics not top5`() {
        // 4 Physical (sum=15) first + 2 Social (1+49=50). Full-sum → HUMANITIES.
        // Wrong take(5) on array order keeps Physical+tiny Social → STEM.
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[
              {"display_name":"P1","count":4,"domain":{"display_name":"Physical Sciences"}},
              {"display_name":"P2","count":4,"domain":{"display_name":"Physical Sciences"}},
              {"display_name":"P3","count":4,"domain":{"display_name":"Physical Sciences"}},
              {"display_name":"P4","count":3,"domain":{"display_name":"Physical Sciences"}},
              {"display_name":"S1","count":1,"domain":{"display_name":"Social Sciences"}},
              {"display_name":"S2","count":49,"domain":{"display_name":"Social Sciences"}}
            ]}"""
        )
        assertEquals("HUMANITIES", dataSource.enrichAuthor("A1")!!.disciplineCategory)
    }

    @Test
    fun `enrichAuthor disciplineCategory STEM for Life Sciences`() {
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[
              {"display_name":"Biology","count":12,"domain":{"display_name":"Life Sciences"}}
            ]}"""
        )
        assertEquals("STEM", dataSource.enrichAuthor("A1")!!.disciplineCategory)
    }

    private fun stubAuthorEnrichment(json: String) {
        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(mapper.readTree(json)))
    }

    @Test
    fun `batchEnrichByOrcids keeps base Success when works fetch is rate limited`() {
        val worksEnabledSource = OpenAlexDataSource(
            restTemplate,
            properties.copy(fetchWorksEnabled = true),
            europePmc,
            pdfExtractor,
            unpaywallClient,
            policy
        )
        val batchJson = """
            {
              "meta": {"count": 2},
              "results": [
                {
                  "orcid": "https://orcid.org/0000-0001",
                  "works_count": 10,
                  "cited_by_count": 100,
                  "summary_stats": {"h_index": 5},
                  "topics": [],
                  "works_api_url": "https://api.openalex.org/works?filter=author.id:A1"
                },
                {
                  "orcid": "https://orcid.org/0000-0002",
                  "works_count": 20,
                  "cited_by_count": 200,
                  "summary_stats": {"h_index": 8},
                  "topics": [],
                  "works_api_url": "https://api.openalex.org/works?filter=author.id:A2"
                }
              ]
            }
        """.trimIndent()
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/authors?"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(mapper.readTree(batchJson)))
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/works?"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenThrow(
            HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders(), ByteArray(0), null
            )
        )

        val outcomes = worksEnabledSource.batchEnrichByOrcids(listOf("0000-0001", "0000-0002"))

        val first = outcomes["0000-0001"] as EnrichmentOutcome.Success
        assertEquals(5, first.data.hIndex)
        assertNull(first.data.recentWorkTitles)
        assertNull(first.data.patentTitles)
        assertTrue(first.titlesFailed, "限流只影响可单独重试的标题，基础事实仍可用且必须标记标题待重试")
        assertInstanceOf(EnrichmentOutcome.RateLimited::class.java, outcomes["0000-0002"])
    }

    @Test
    fun `works path takes institutionType from first institution same object as affiliation (I5a-2)`() {
        stubWorksResponse(
            """
            {
              "meta": {"count": 1, "next_cursor": null},
              "results": [{
                "id": "https://openalex.org/W1",
                "authorships": [{
                  "author": {"display_name": "Jane Doe"},
                  "institutions": [
                    {"display_name": "First University", "type": "education"},
                    {"display_name": "Second Lab", "type": "company"}
                  ],
                  "is_corresponding": true
                }]
              }]
            }
            """.trimIndent()
        )
        val author = dataSource.searchPapers(PaperSearchCriteria()).papers.single().authors.single()
        assertEquals("First University", author.affiliation)
        assertEquals("education", author.institutionType)
    }

    @Test
    fun `works path institutionType null when institutions empty (I5a-3)`() {
        stubWorksResponse(
            """
            {
              "meta": {"count": 1, "next_cursor": null},
              "results": [{
                "id": "https://openalex.org/W1",
                "authorships": [{
                  "author": {"display_name": "Jane Doe"},
                  "institutions": [],
                  "is_corresponding": true
                }]
              }]
            }
            """.trimIndent()
        )
        val author = dataSource.searchPapers(PaperSearchCriteria()).papers.single().authors.single()
        assertNull(author.institutionType)
        assertNull(author.affiliation)
    }

    @Test
    fun `works path institutionType null when type key missing (I5a-3)`() {
        stubWorksResponse(
            """
            {
              "meta": {"count": 1, "next_cursor": null},
              "results": [{
                "id": "https://openalex.org/W1",
                "authorships": [{
                  "author": {"display_name": "Jane Doe"},
                  "institutions": [{"display_name": "Some Lab"}],
                  "is_corresponding": true
                }]
              }]
            }
            """.trimIndent()
        )
        val author = dataSource.searchPapers(PaperSearchCriteria()).papers.single().authors.single()
        assertNull(author.institutionType)
        assertEquals("Some Lab", author.affiliation)
    }

    @Test
    fun `works path institutionType null when type empty string (I5a-3)`() {
        stubWorksResponse(
            """
            {
              "meta": {"count": 1, "next_cursor": null},
              "results": [{
                "id": "https://openalex.org/W1",
                "authorships": [{
                  "author": {"display_name": "Jane Doe"},
                  "institutions": [{"display_name": "Some Lab", "type": ""}],
                  "is_corresponding": true
                }]
              }]
            }
            """.trimIndent()
        )
        val author = dataSource.searchPapers(PaperSearchCriteria()).papers.single().authors.single()
        assertNull(author.institutionType)
    }

    @Test
    fun `authors path takes institutionType from first last_known_institution (I5a-2)`() {
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[],
               "last_known_institutions":[
                 {"display_name":"First Lab", "type":"company"},
                 {"display_name":"Second Lab", "type":"education"}
               ]}"""
        )
        assertEquals("company", dataSource.enrichAuthor("A1")!!.institutionType)
    }

    @Test
    fun `authors path institutionType null when last_known_institutions missing (I5a-3)`() {
        stubAuthorEnrichment("""{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[]}""")
        assertNull(dataSource.enrichAuthor("A1")!!.institutionType)
    }

    @Test
    fun `authors path institutionType null when last_known_institutions empty (I5a-3)`() {
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[],
               "last_known_institutions":[]}"""
        )
        assertNull(dataSource.enrichAuthor("A1")!!.institutionType)
    }

    @Test
    fun `authors path institutionType null when type empty string (I5a-3)`() {
        stubAuthorEnrichment(
            """{"works_count":1,"cited_by_count":1,"summary_stats":{"h_index":1},"topics":[],
               "last_known_institutions":[{"display_name":"Some Lab","type":""}]}"""
        )
        assertNull(dataSource.enrichAuthor("A1")!!.institutionType)
    }

    @Test
    fun `searchPapers keeps the OpenAlex author id of every authorship (I-1)`() {
        // I-1: 作者 ID 随作者对象保留（规范为 A+数字），但绝不参与 ES _id / orcidId 语义。
        stubWorksResponse(
            """
            {
              "meta": {"count": 1, "next_cursor": null},
              "results": [{
                "id": "https://openalex.org/W1",
                "authorships": [
                  {"author": {"id": "https://openalex.org/A5023888391", "display_name": "John Smith",
                              "orcid": "https://orcid.org/0000-0001-2345-6789"},
                   "institutions": [{"display_name": "University of Oxford", "type": "education"}],
                   "is_corresponding": true},
                  {"author": {"id": "A5086928770", "display_name": "Alice Jones"}, "institutions": []},
                  {"author": {"id": "https://openalex.org/W9", "display_name": "Bob NoId"}, "institutions": []}
                ]
              }]
            }
            """.trimIndent()
        )

        val authors = dataSource.searchPapers(PaperSearchCriteria()).papers.single().authors

        assertEquals("A5023888391", authors[0].openAlexAuthorId)
        assertEquals("A5086928770", authors[1].openAlexAuthorId)
        assertNull(authors[2].openAlexAuthorId, "非 A+数字 的 ID 不得被当作者身份保留")
    }

    @Test
    fun `PMC extraction attaches the OpenAlex author id on an exact ORCID match (I-2)`() {
        val paper = pmcPaper(
            listOf(
                PaperAuthor("John", "Smith", "0000-0001-2345-6789", "Oxford, UK", true,
                    openAlexAuthorId = "A5023888391")
            )
        )
        Mockito.doReturn(
            EmailExtractionOutcome(
                listOf(AuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001-2345-6789")),
                "SEARCH_FIELD", null
            )
        ).`when`(europePmc).extractAuthorEmails(eqValue(paper), Mockito.any())

        val outcome = dataSource.extractAuthorEmails(paper)

        assertEquals("FULLTEXT_XML", outcome.methodUsed)
        assertEquals("A5023888391", outcome.emails.single().openAlexAuthorId)
    }

    @Test
    fun `PMC extraction does not attach the author id when several authors share the ORCID (I-2)`() {
        val paper = pmcPaper(
            listOf(
                PaperAuthor("John", "Smith", "0000-0001-2345-6789", "Oxford, UK", true,
                    openAlexAuthorId = "A5023888391"),
                PaperAuthor("Johnny", "Smith", "0000-0001-2345-6789", "Cambridge, UK", false,
                    openAlexAuthorId = "A5086928770")
            )
        )
        Mockito.doReturn(
            EmailExtractionOutcome(
                listOf(AuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001-2345-6789")),
                "SEARCH_FIELD", null
            )
        ).`when`(europePmc).extractAuthorEmails(eqValue(paper), Mockito.any())

        val outcome = dataSource.extractAuthorEmails(paper)

        assertNull(outcome.emails.single().openAlexAuthorId, "ORCID 命中多个不同作者 ID 时不得任选一个")
    }

    @Test
    fun `PMC extraction does not attach the author id without a matching ORCID (I-2)`() {
        val paper = pmcPaper(
            listOf(
                PaperAuthor("John", "Smith", "0000-0001-2345-6789", "Oxford, UK", true,
                    openAlexAuthorId = "A5023888391")
            )
        )
        Mockito.doReturn(
            EmailExtractionOutcome(
                listOf(
                    AuthorEmail("alice@oxford.ac.uk", "Alice", "Jones", false, null, null),
                    AuthorEmail("carol@oxford.ac.uk", "Carol", "King", false, null, "0000-9999-9999-9999")
                ),
                "SEARCH_FIELD", null
            )
        ).`when`(europePmc).extractAuthorEmails(eqValue(paper), Mockito.any())

        val outcome = dataSource.extractAuthorEmails(paper)

        assertEquals(2, outcome.emails.size)
        assertTrue(outcome.emails.all { it.openAlexAuthorId == null }, "无 ORCID 或 ORCID 不匹配时不得补接作者 ID")
    }

    // ------------------------------------------------------------------
    // c10（I-1/I-2/I-3）：有界回退链与漏斗分层
    // ------------------------------------------------------------------

    @Test
    fun `searchPapers keeps the other open pdf locations as fallback candidates (I-1)`() {
        stubWorksResponse(
            """
            {
              "meta": {"count": 1, "next_cursor": null},
              "results": [{
                "id": "https://openalex.org/W1",
                "best_oa_location": {"is_oa": true, "pdf_url": "https://primary.example/paper.pdf"},
                "locations": [
                  {"is_oa": true, "pdf_url": "https://primary.example/paper.pdf"},
                  {"is_oa": true, "pdf_url": "https://repo.example/copy.pdf"},
                  {"is_oa": false, "pdf_url": "https://paywall.example/paper.pdf"},
                  {"is_oa": true, "pdf_url": "https://repo.example/copy.pdf"},
                  {"is_oa": true, "pdf_url": "ftp://repo.example/copy.pdf"}
                ],
                "authorships": []
              }]
            }
            """.trimIndent()
        )

        val paper = dataSource.searchPapers(PaperSearchCriteria()).papers.single()

        assertEquals("https://primary.example/paper.pdf", paper.downloadUrl)
        assertEquals(
            listOf("https://repo.example/copy.pdf"),
            paper.candidateDownloadUrls,
            "只保留 is_oa 的公开 http(s) 备用地址，去掉首选本身、付费墙、重复项与非 http 协议"
        )
    }

    @Test
    fun `a dead primary address falls back to the next open pdf and counts one paper once (V-1, I-1, I-3)`() {
        val primary = "https://primary.example/gone.pdf"
        val fallback = "https://repo.example/copy.pdf"
        val author = PaperAuthor("John", "Smith", "0000-0001", "Oxford, UK", true, openAlexAuthorId = "A5023888391")
        stubDownloads(
            primary to failedDownload("PDF_DOWNLOAD_FAILED", "HTTP_404"),
            fallback to successfulDownload("john.smith@oxford.ac.uk", author)
        )

        val outcome = dataSource.extractAuthorEmails(
            openAlexPaper(downloadUrl = primary, candidates = listOf(fallback), authors = listOf(author))
        )

        assertEquals("PDF_PARSE", outcome.methodUsed)
        assertNull(outcome.failureReason)
        assertEquals("john.smith@oxford.ac.uk", outcome.emails.single().email)
        assertEquals("A5023888391", outcome.emails.single().openAlexAuthorId)
        assertEquals(2, outcome.httpRequests, "1 篇论文 2 次下载尝试：论文计数仍是 1，尝试次数单独计")
        Mockito.verify(unpaywallClient, Mockito.never()).findPdfUrls(Mockito.anyString(), Mockito.any())
    }

    @Test
    fun `the fallback chain stops after three addresses and shares one deadline (V-1, I-1)`() {
        val urls = (1..4).map { "https://repo.example/$it.pdf" }
        stubDownloads(
            urls[0] to failedDownload("PDF_DOWNLOAD_FAILED", "HTTP_404"),
            urls[1] to failedDownload("PDF_DOWNLOAD_FAILED", "HTTP_403"),
            urls[2] to failedDownload("PDF_DOWNLOAD_FAILED", "HTTP_5XX")
        )

        val outcome = dataSource.extractAuthorEmails(
            openAlexPaper(downloadUrl = urls[0], candidates = urls.drop(1))
        )

        assertEquals(3, outcome.httpRequests)
        assertEquals("PDF_DOWNLOAD_FAILED", outcome.failureReason)
        assertEquals("HTTP_5XX", outcome.downloadFailureCategory)
        assertEquals(false, outcome.fulltextObtained)
        assertEquals(urls.take(3), downloadAttempts, "第三个地址失败后不再访问第四个（访问第四个会直接抛错）")
        assertEquals(
            1, downloadDeadlines.distinct().size,
            "单篇所有地址共享同一个 deadline，不是每个地址各给一份"
        )
    }

    @Test
    fun `a repeated url is attempted only once (V-1)`() {
        val primary = "https://primary.example/paper.pdf"
        val second = "https://repo.example/copy.pdf"
        stubDownloads(
            primary to failedDownload("PDF_DOWNLOAD_FAILED", "HTTP_404"),
            second to successfulDownload("john.smith@oxford.ac.uk")
        )

        val outcome = dataSource.extractAuthorEmails(
            openAlexPaper(downloadUrl = primary, candidates = listOf(primary, second))
        )

        assertEquals("john.smith@oxford.ac.uk", outcome.emails.single().email)
        assertEquals(listOf(primary, second), downloadAttempts, "重复地址只尝试一次")
        assertEquals(2, outcome.httpRequests)
    }

    @Test
    fun `unpaywall is consulted only when the open locations yielded nothing (I-1)`() {
        val primary = "https://primary.example/paper.pdf"
        val unpaywallUrl = "https://unpaywall.example/copy.pdf"
        stubDownloads(primary to successfulDownload("john.smith@oxford.ac.uk"))

        dataSource.extractAuthorEmails(openAlexPaper(downloadUrl = primary, doi = "10.1/x"))

        Mockito.verify(unpaywallClient, Mockito.never()).findPdfUrls(Mockito.anyString(), Mockito.any())

        // 首选失效且没有其他 OA 地址时才问 Unpaywall，并把它给出的开放位置当作下一个候选。
        Mockito.doReturn(true).`when`(unpaywallClient).isConfigured()
        stubDownloads(
            primary to failedDownload("PDF_DOWNLOAD_FAILED", "HTTP_404"),
            unpaywallUrl to successfulDownload("john.smith@oxford.ac.uk")
        )
        Mockito.doReturn(listOf(unpaywallUrl)).`when`(unpaywallClient).findPdfUrls(eqValue("10.1/x"), Mockito.any())

        val outcome = dataSource.extractAuthorEmails(openAlexPaper(downloadUrl = primary, doi = "10.1/x"))

        assertEquals("john.smith@oxford.ac.uk", outcome.emails.single().email)
        assertEquals(3, outcome.httpRequests, "2 次下载尝试 + 1 次 Unpaywall 查询")
        Mockito.verify(unpaywallClient, Mockito.times(1)).findPdfUrls(eqValue("10.1/x"), Mockito.any())
    }

    @Test
    fun `an expired shared deadline prevents any download (I-1)`() {
        // c10（I-1）：共享总时限是单篇自己的约束，过期即停手并按 TIMEOUT 上报，不是来源耗尽。
        val outcome = dataSource.extractAuthorEmails(
            openAlexPaper(downloadUrl = "https://primary.example/paper.pdf"),
            Instant.now().minusSeconds(1)
        )

        assertEquals("PDF_DOWNLOAD_FAILED", outcome.failureReason)
        assertEquals("TIMEOUT", outcome.downloadFailureCategory)
        assertEquals(false, outcome.fulltextObtained)
        assertEquals(0, outcome.httpRequests)
        Mockito.verifyNoInteractions(pdfExtractor)
    }

    @Test
    fun `the XML stage shares the same per-paper deadline and no later stage runs after expiry (R-4, V-4)`() {
        // V-4：XML 阶段以前完全不受共享总时限约束。现在它必须拿到同一个 deadline；时限已过时
        // 连 URL 下载和 Unpaywall 查询都不能再发生（整篇按既有 TIMEOUT 类别收口）。
        val author = PaperAuthor("John", "Smith", "0000-0001", "Oxford, UK", true, openAlexAuthorId = "A5023888391")
        val paper = pmcPaper(listOf(author)).copy(
            doi = "10.1/x", downloadUrl = "https://primary.example/paper.pdf"
        )
        val fixedDeadline = Instant.parse("2026-09-21T02:41:17Z")
        val xmlDeadlines = mutableListOf<Instant>()
        Mockito.doAnswer { invocation: InvocationOnMock ->
            xmlDeadlines += invocation.getArgument<Instant>(1)
            EmailExtractionOutcome(
                emptyList(), "FULLTEXT_XML", "FULLTEXT_FETCH_FAILED", httpRequests = 0,
                fulltextObtained = false, downloadFailureCategory = "TIMEOUT"
            )
        }.`when`(europePmc).extractAuthorEmails(eqValue(paper), Mockito.any())

        val outcome = dataSource.extractAuthorEmails(paper, fixedDeadline)

        assertEquals(listOf(fixedDeadline), xmlDeadlines, "XML 阶段必须拿到同一个单篇共享 deadline")
        assertEquals("TIMEOUT", outcome.downloadFailureCategory)
        assertEquals(0, outcome.httpRequests)
        Mockito.verifyNoInteractions(pdfExtractor)
        Mockito.verify(unpaywallClient, Mockito.never()).findPdfUrls(Mockito.anyString(), Mockito.any())
    }

    @Test
    fun `the unpaywall lookup receives the same per-paper deadline as the downloads (R-4, V-4)`() {
        val primary = "https://primary.example/gone.pdf"
        val unpaywallUrl = "https://unpaywall.example/copy.pdf"
        val fixedDeadline = Instant.parse("2099-01-01T00:00:00Z")
        Mockito.doReturn(true).`when`(unpaywallClient).isConfigured()
        val lookupDeadlines = mutableListOf<Instant>()
        Mockito.doAnswer { invocation: InvocationOnMock ->
            lookupDeadlines += invocation.getArgument<Instant>(1)
            listOf(unpaywallUrl)
        }.`when`(unpaywallClient).findPdfUrls(Mockito.anyString(), Mockito.any())
        stubDownloads(
            primary to failedDownload("PDF_DOWNLOAD_FAILED", "HTTP_404"),
            unpaywallUrl to successfulDownload("john.smith@oxford.ac.uk")
        )

        val outcome = dataSource.extractAuthorEmails(
            openAlexPaper(downloadUrl = primary, doi = "10.1/x"), fixedDeadline
        )

        assertEquals("john.smith@oxford.ac.uk", outcome.emails.single().email)
        assertEquals(listOf(fixedDeadline), lookupDeadlines, "Unpaywall 阶段拿到的是同一个共享 deadline")
        assertTrue(downloadDeadlines.all { it == fixedDeadline }, "URL 阶段同样只用这一个 deadline")
    }

    @Test
    fun `an expired budget issues no unpaywall lookup at all (R-4, V-4)`() {
        Mockito.doReturn(true).`when`(unpaywallClient).isConfigured()

        val outcome = dataSource.extractAuthorEmails(openAlexPaper(doi = "10.1/x"), Instant.now().minusSeconds(1))

        assertEquals("TIMEOUT", outcome.downloadFailureCategory)
        assertEquals(0, outcome.httpRequests)
        Mockito.verify(unpaywallClient, Mockito.never()).findPdfUrls(Mockito.anyString(), Mockito.any())
        Mockito.verifyNoInteractions(pdfExtractor)
    }

    @Test
    fun `a paper without any address keeps the previous NO_PMC_ID semantics (I-4)`() {
        val outcome = dataSource.extractAuthorEmails(openAlexPaper())

        assertEquals("NO_PMC_ID", outcome.failureReason)
        assertEquals(0, outcome.httpRequests)
        Mockito.verifyNoInteractions(pdfExtractor)
    }

    @Test
    fun `a metered fulltext download reports its quota headers to the shared policy (c1 O-1)`() {
        // c1 的 O-1：fulltextDownloadCount() 之前没有生产写入方 —— 下载真正发生时把 provider 响应头交回 policy。
        Mockito.doAnswer { invocation: InvocationOnMock ->
            invocation.getArgument<(HttpHeaders) -> Unit>(4)(
                HttpHeaders().apply { set(OpenAlexRequestPolicy.CREDITS_USED_HEADER, "100") }
            )
            successfulDownload("john.smith@oxford.ac.uk")
        }.`when`(pdfExtractor).extract(
            Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.any(), Mockito.any()
        )

        dataSource.extractAuthorEmails(openAlexPaper(downloadUrl = "https://content.openalex.org/works/W1.pdf"))

        assertEquals(1L, policy.fulltextDownloadCount(), "计量内容下载必须计入共享额度，而不是停在死计数器里")
    }

    @Test
    fun `an external host without openalex quota headers leaves the shared policy untouched (c1 O-1)`() {
        // 外部开放全文站点不带 provider 配额头：既不算额度消耗，也不触发无谓退避。
        Mockito.doAnswer { invocation: InvocationOnMock ->
            invocation.getArgument<(HttpHeaders) -> Unit>(4)(
                HttpHeaders().apply { set(HttpHeaders.CONTENT_TYPE, "application/pdf") }
            )
            successfulDownload("john.smith@oxford.ac.uk")
        }.`when`(pdfExtractor).extract(
            Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.any(), Mockito.any()
        )

        dataSource.extractAuthorEmails(openAlexPaper(downloadUrl = "https://repo.example/copy.pdf"))

        assertEquals(0L, policy.fulltextDownloadCount())
        assertEquals(0L, policy.listRequestCount())
    }

    @Test
    fun `a fallback pdf never attaches an identity when the authors are ambiguous (V-1, I-2)`() {
        // 端到端：首选 404、备用 PDF 真的被解析，但同名歧义下不得附带任何学术身份。
        val downloadRestTemplate = Mockito.mock(RestTemplate::class.java)
        val realExtractor = PdfEmailExtractor(downloadRestTemplate, PlainTextEmailExtractor(), PdfExtractionProperties())
        val chainDataSource = OpenAlexDataSource(
            restTemplate, properties, europePmc, realExtractor, unpaywallClient, policy
        )
        val primary = "https://primary.example/gone.pdf"
        val fallback = "https://repo.example/copy.pdf"
        val pdfBytes = javaClass.classLoader.getResource("pdf/standard.pdf")!!.readBytes()
        val ambiguousAuthors = listOf(
            PaperAuthor("John", "Smith", "0000-0001", "Oxford, UK", true, openAlexAuthorId = "A5023888391"),
            PaperAuthor("John", "Smith", "0000-0002", "Cambridge, UK", false, openAlexAuthorId = "A5086928770")
        )

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val uri = invocation.getArgument<URI>(0)
            if (uri.toString() == primary) throw HttpClientErrorException(HttpStatus.NOT_FOUND)
            val responseExtractor = invocation.getArgument<ResponseExtractor<*>>(3)
            val mockResponse = Mockito.mock(ClientHttpResponse::class.java)
            Mockito.doReturn(HttpHeaders().apply { contentType = MediaType.APPLICATION_PDF })
                .`when`(mockResponse).headers
            Mockito.doReturn(java.io.ByteArrayInputStream(pdfBytes)).`when`(mockResponse).body
            responseExtractor.extractData(mockResponse)
        }.`when`(downloadRestTemplate).execute(
            Mockito.any(URI::class.java), Mockito.eq(HttpMethod.GET), Mockito.any(), Mockito.any(ResponseExtractor::class.java)
        )

        val outcome = chainDataSource.extractAuthorEmails(
            openAlexPaper(downloadUrl = primary, candidates = listOf(fallback), authors = ambiguousAuthors)
        )

        assertEquals("PDF_PARSE", outcome.methodUsed)
        assertNull(outcome.failureReason)
        assertTrue(outcome.emails.any { it.email == "john.smith@oxford.ac.uk" })
        assertTrue(outcome.emails.all { it.orcidId == null && it.openAlexAuthorId == null }, "备用版本的同名歧义不得被当身份")
        assertEquals(2, outcome.httpRequests)
    }

    /** Mockito 的 eq(any) 返回 null，Kotlin 非空参数会触发空检查 —— 用真实值兜底（本仓库既有习惯）。 */
    private fun <T : Any> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun openAlexPaper(
        downloadUrl: String? = null,
        candidates: List<String> = emptyList(),
        authors: List<PaperAuthor> = emptyList(),
        doi: String? = null
    ) = PaperMetadata(
        pmcId = null, pmid = null, doi = doi, title = "Test", pubYear = 2024, journal = null,
        authors = authors, source = "OPENALEX", downloadUrl = downloadUrl, candidateDownloadUrls = candidates
    )

    private fun failedDownload(reason: String, category: String? = null) = EmailExtractionOutcome(
        emptyList(), "PDF_PARSE", reason, httpRequests = 1,
        fulltextObtained = false, downloadFailureCategory = category
    )

    private fun successfulDownload(email: String, author: PaperAuthor? = null) = EmailExtractionOutcome(
        listOf(
            AuthorEmail(
                email, author?.givenNames, author?.familyNames, false, author?.affiliation,
                author?.orcidId, author?.institutionType, author?.openAlexAuthorId
            )
        ),
        "PDF_PARSE", null, httpRequests = 1, fulltextObtained = true
    )

    /**
     * 记录每次下载尝试（顺序、地址、共享 deadline）并按地址返回结果；没有显式安排的地址一旦被访问即
     * 让测试失败 —— 这比 verify 更能证明「第四个地址根本没被访问」。
     */
    private fun stubDownloads(vararg outcomesByUrl: Pair<String, EmailExtractionOutcome>) {
        val outcomes = outcomesByUrl.toMap()
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val url = invocation.getArgument<String>(0)
            downloadAttempts += url
            downloadDeadlines += invocation.getArgument<Instant>(3)
            outcomes[url] ?: throw AssertionError("unexpected download attempt: $url")
        }.`when`(pdfExtractor).extract(
            Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.any(), Mockito.any()
        )
    }

    private fun pmcPaper(authors: List<PaperAuthor>) = PaperMetadata(
        pmcId = "PMC9876543", pmid = null, doi = null, title = "Test", pubYear = 2024,
        journal = null, authors = authors, source = "OPENALEX"
    )

    private fun stubWorksResponse(json: String) {
        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(mapper.readTree(json)))
    }

    private fun stubJsonWithHeaders(json: String, headers: HttpHeaders) {
        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity(mapper.readTree(json), headers, HttpStatus.OK))
    }

    /** Spends budget through the policy exactly like a real caller: reserve a slot, then reconcile the response. */
    private fun consumeCredits(requests: Int) {
        repeat(requests) {
            assertEquals(Permit.Allowed, policy.beforeRequest(RequestKind.DISCOVERY))
            policy.recordResponse(HttpHeaders())
        }
    }

    @Test
    fun `searchPapers marks DISCOVERY and reconciles the real quota headers (I-2)`() {
        val headers = HttpHeaders()
        headers.set(OpenAlexRequestPolicy.REMAINING_HEADER, "989")
        headers.set(OpenAlexRequestPolicy.LIMIT_HEADER, "1000")
        headers.set(OpenAlexRequestPolicy.CREDITS_USED_HEADER, "1")
        headers.set(OpenAlexRequestPolicy.RESET_HEADER, "76723")
        stubJsonWithHeaders("""{"meta":{"count":0,"next_cursor":null},"results":[]}""", headers)

        dataSource.searchPapers(PaperSearchCriteria())

        assertEquals(989, policy.remainingCredits())
        assertEquals(1, policy.listRequestCount())
    }

    @Test
    fun `discovery defers on the reserved share while new-expert enrichment still runs (I-3, V-3)`() {
        consumeCredits(800)
        val enrichmentJson = """{"works_count":4,"cited_by_count":9,"summary_stats":{"h_index":3},"topics":[]}"""
        stubAuthorEnrichment(enrichmentJson)

        // New-expert enrichment is the only consumer allowed inside the reserved 20%.
        assertEquals(3, dataSource.enrichAuthor("A1", RequestKind.NEW_ENRICHMENT)!!.hIndex)

        val deferred = assertThrows(OpenAlexBudgetDeferredException::class.java) {
            dataSource.searchPapers(PaperSearchCriteria())
        }
        assertEquals(Instant.parse("2026-09-22T00:00:00Z"), deferred.resetAt)

        // Legacy backfill callers are history enrichment: lowest priority, never inside the reserve.
        assertThrows(OpenAlexBudgetDeferredException::class.java) { dataSource.enrichAuthor("A1") }
        assertThrows(OpenAlexBudgetDeferredException::class.java) { dataSource.batchEnrichByOrcids(listOf("0000-0001")) }
    }

    @Test
    fun `a failed call releases its reservation without counting a call (I-2)`() {
        Mockito.`when`(
            restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenThrow(RuntimeException("network down"))

        assertThrows(RuntimeException::class.java) { dataSource.searchPapers(PaperSearchCriteria()) }

        assertEquals(999, policy.remainingCredits())
        assertEquals(0, policy.listRequestCount())
        assertEquals(Permit.Allowed, policy.beforeRequest(RequestKind.NEW_ENRICHMENT))
    }

    // ── 子计划 06：按作者 ID 批量补全与可单独重试的附加标题（I-1、I-3、V-3）──

    @Test
    fun `batchEnrichByAuthorIds queries by author id and only maps canonical ids (I-1)`() {
        val batchJson = """
            {
              "meta": {"count": 3},
              "results": [
                {"id": "https://openalex.org/A5023888391", "works_count": 10, "cited_by_count": 100,
                 "summary_stats": {"h_index": 7}, "topics": []},
                {"id": "A5086928770", "works_count": 3, "cited_by_count": 9,
                 "summary_stats": {"h_index": 2}, "topics": []},
                {"id": "https://openalex.org/W9", "works_count": 1, "cited_by_count": 1,
                 "summary_stats": {"h_index": 1}, "topics": []}
              ]
            }
        """.trimIndent()
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/authors?filter=openalex:"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(mapper.readTree(batchJson)))

        val outcomes = dataSource.batchEnrichByAuthorIds(listOf("A5023888391", "A5086928770", "A999"))

        assertEquals(3, outcomes.size)
        assertEquals(7, (outcomes["A5023888391"] as EnrichmentOutcome.Success).data.hIndex)
        assertEquals(2, (outcomes["A5086928770"] as EnrichmentOutcome.Success).data.hIndex)
        assertEquals(EnrichmentOutcome.NotFound, outcomes["A999"])
        // 响应里的 W9 不是作者身份：既不给 A999 也不给任何其他身份，绝不错配。
        Mockito.verify(restTemplate).exchange(
            Mockito.contains("filter=openalex:A5023888391|A5086928770|A999"),
            Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `batchEnrichByAuthorIds never attributes a non-canonical response id (I-1)`() {
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/authors?filter=openalex:"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(
            ResponseEntity.ok(mapper.readTree("""{"results":[{"id":"https://openalex.org/W9","works_count":1}]}"""))
        )

        val outcomes = dataSource.batchEnrichByAuthorIds(listOf("A5023888391"))

        assertEquals(EnrichmentOutcome.NotFound, outcomes["A5023888391"])
    }

    @Test
    fun `enrichAuthor uses the same titles switches as the batch path (V-3)`() {
        val authorJson = """
            {"works_count": 2, "cited_by_count": 5, "summary_stats": {"h_index": 2}, "topics": [],
             "works_api_url": "https://api.openalex.org/works?filter=author.id:A1"}
        """.trimIndent()
        stubAuthorEnrichment(authorJson)

        val enrichment = dataSource.enrichAuthor("A1")

        assertNull(enrichment!!.recentWorkTitles)
        assertNull(enrichment.patentTitles)
        // 开关全关：单人路径与批量路径一致地只发一次作者请求（取消无条件查专利/论文）。
        Mockito.verify(restTemplate, Mockito.times(1)).exchange(
            Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `enrichAuthor fetches recent works only when the switch is on (V-3)`() {
        val worksEnabledSource = OpenAlexDataSource(
            restTemplate,
            properties.copy(fetchWorksEnabled = true),
            europePmc,
            pdfExtractor,
            unpaywallClient,
            policy
        )
        stubAuthorEnrichment(
            """{"works_count": 2, "cited_by_count": 5, "summary_stats": {"h_index": 2}, "topics": [],
                "works_api_url": "https://api.openalex.org/works?filter=author.id:A1"}"""
        )
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/works?"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(mapper.readTree("""{"results":[{"title":"Paper A"}]}""")))

        val enrichment = worksEnabledSource.enrichAuthor("A1")

        assertEquals(listOf("Paper A"), enrichment!!.recentWorkTitles)
        assertNull(enrichment.patentTitles)
        Mockito.verify(restTemplate, Mockito.times(2)).exchange(
            Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `batchEnrichByOrcids keeps base facts usable when the titles call fails (I-3)`() {
        val worksEnabledSource = OpenAlexDataSource(
            restTemplate,
            properties.copy(fetchWorksEnabled = true),
            europePmc,
            pdfExtractor,
            unpaywallClient,
            policy
        )
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/authors?"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(
            ResponseEntity.ok(
                mapper.readTree(
                    """
                    {
                      "meta": {"count": 1},
                      "results": [
                        {"orcid": "https://orcid.org/0000-0001", "works_count": 10, "cited_by_count": 100,
                         "summary_stats": {"h_index": 5}, "topics": [],
                         "works_api_url": "https://api.openalex.org/works?filter=author.id:A1"}
                      ]
                    }
                    """.trimIndent()
                )
            )
        )
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/works?"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))

        val outcome = worksEnabledSource.batchEnrichByOrcids(listOf("0000-0001"))["0000-0001"]

        val success = outcome as EnrichmentOutcome.Success
        assertEquals(5, success.data.hIndex, "标题请求失败不得丢掉基础事实")
        assertNull(success.data.recentWorkTitles)
        assertTrue(success.titlesFailed, "标题子请求失败必须可识别（可单独重试）")
    }

    @Test
    fun `batchEnrichByOrcids treats an empty titles result as success not failure (I-3)`() {
        val worksEnabledSource = OpenAlexDataSource(
            restTemplate,
            properties.copy(fetchWorksEnabled = true),
            europePmc,
            pdfExtractor,
            unpaywallClient,
            policy
        )
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/authors?"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(
            ResponseEntity.ok(
                mapper.readTree(
                    """
                    {
                      "meta": {"count": 1},
                      "results": [
                        {"orcid": "https://orcid.org/0000-0001", "works_count": 10, "cited_by_count": 100,
                         "summary_stats": {"h_index": 5}, "topics": [],
                         "works_api_url": "https://api.openalex.org/works?filter=author.id:A1"}
                      ]
                    }
                    """.trimIndent()
                )
            )
        )
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/works?"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(mapper.readTree("""{"results":[]}""")))

        val success = worksEnabledSource.batchEnrichByOrcids(listOf("0000-0001"))["0000-0001"]
            as EnrichmentOutcome.Success

        assertNull(success.data.recentWorkTitles)
        assertFalse(success.titlesFailed, "空结果只是该作者没有作品，不是请求失败")
    }

    @Test
    fun `enrichAuthorByOrcidWithReason keeps a 404 not-found apart from a retryable failure (V-3)`() {
        val searchJson = """{"results":[{"id":"https://openalex.org/A1"}]}"""
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/authors?filter=orcid:"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(mapper.readTree(searchJson)))
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/authors/A1"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))

        assertInstanceOf(
            EnrichmentOutcome.ApiError::class.java,
            dataSource.enrichAuthorByOrcidWithReason("0000-0001"),
            "服务端/网络失败必须可重试，不能混进「查无此人」"
        )

        Mockito.reset(restTemplate)
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/authors?filter=orcid:"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(ResponseEntity.ok(mapper.readTree(searchJson)))
        Mockito.`when`(
            restTemplate.exchange(Mockito.contains("/authors/A1"), Mockito.eq(HttpMethod.GET), Mockito.nullable(HttpEntity::class.java), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenThrow(
            HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders(), ByteArray(0), null)
        )

        assertEquals(EnrichmentOutcome.NotFound, dataSource.enrichAuthorByOrcidWithReason("0000-0001"))
    }
}
