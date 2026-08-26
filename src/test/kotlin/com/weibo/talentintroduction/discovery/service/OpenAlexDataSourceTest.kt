package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate

class OpenAlexDataSourceTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val europePmc = Mockito.mock(EuropePmcDataSource::class.java)
    private val pdfExtractor = Mockito.mock(PdfEmailExtractor::class.java)
    private val properties = OpenAlexProperties(
        enabled = true,
        politeEmail = "",
        baseUrl = "https://api.openalex.org",
        requestDelayMs = 0
    )
    private val mapper = ObjectMapper()
    private val dataSource = OpenAlexDataSource(restTemplate, properties, europePmc, pdfExtractor)

    @Test
    fun `searchPapers parses OpenAlex works response`() {
        val sampleJson = javaClass.classLoader
            .getResource("openalex/works-response-sample.json")!!.readText()
        val response = mapper.readTree(sampleJson)

        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(response)

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
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(response)

        val result = dataSource.enrichAuthor("A1234567")
        assertNotNull(result)
        assertEquals(18, result!!.hIndex)
        assertEquals(1200, result.citationCount)
        assertEquals(45, result.worksCount)
        assertEquals("nonprofit", result.institutionType)
    }

    @Test
    fun `buildFilter includes is_oa and publication_year`() {
        val response = mapper.readTree("""{"meta":{"count":0,"next_cursor":null},"results":[]}""")
        val urlCaptor = mutableListOf<String>()

        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenAnswer { invocation ->
            urlCaptor.add(invocation.arguments[0] as String)
            response
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
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenAnswer { invocation ->
            urlCaptor.add(invocation.arguments[0] as String)
            response
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
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenAnswer { invocation ->
            urlCaptor.add(invocation.arguments[0] as String)
            response
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
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenThrow(RuntimeException("API unavailable"))

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException::class.java) { dataSource.searchPapers(PaperSearchCriteria()) }
    }

    @Test
    fun `enrichAuthorByOrcidWithReason returns RateLimited on HTTP 429`() {
        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
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
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
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
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
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
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(mapper.readTree(batchJson))

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
    }

    @Test
    fun `batchEnrichByOrcids returns RateLimited for all orcids on HTTP 429`() {
        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
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
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(mapper.readTree(batchJson))

        dataSource.batchEnrichByOrcids(listOf("0000-0001"))

        Mockito.verify(restTemplate, Mockito.times(1)).getForObject(
            Mockito.anyString(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `batchEnrichByOrcids fetches works when enabled`() {
        val worksEnabledSource = OpenAlexDataSource(
            restTemplate,
            properties.copy(fetchWorksEnabled = true),
            europePmc,
            pdfExtractor
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
            restTemplate.getForObject(Mockito.contains("/authors?"), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(mapper.readTree(batchJson))
        Mockito.`when`(
            restTemplate.getForObject(Mockito.contains("/works?"), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(mapper.readTree(worksJson))

        val outcomes = worksEnabledSource.batchEnrichByOrcids(listOf("0000-0001"))

        val success = outcomes["0000-0001"] as EnrichmentOutcome.Success
        assertEquals(listOf("Recent Paper"), success.data.recentWorkTitles)
        Mockito.verify(restTemplate, Mockito.times(2)).getForObject(
            Mockito.anyString(),
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
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(mapper.readTree(json))
    }

    @Test
    fun `batchEnrichByOrcids keeps base Success when works fetch is rate limited`() {
        val worksEnabledSource = OpenAlexDataSource(
            restTemplate,
            properties.copy(fetchWorksEnabled = true),
            europePmc,
            pdfExtractor
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
            restTemplate.getForObject(Mockito.contains("/authors?"), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(mapper.readTree(batchJson))
        Mockito.`when`(
            restTemplate.getForObject(Mockito.contains("/works?"), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
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

    private fun stubWorksResponse(json: String) {
        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(mapper.readTree(json))
    }
}
