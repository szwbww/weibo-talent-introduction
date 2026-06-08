package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.expert.domain.EmailValidationResult
import com.weibo.talentintroduction.expert.service.CandidateEligibilityService
import com.weibo.talentintroduction.expert.service.EmailValidationService
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.expert.service.ScrollExpertsMockHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

class ExpertDiscoveryServiceTest {
    private val europePmc = Mockito.mock(EuropePmcDataSource::class.java)
    private val openAlexProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<OpenAlexDataSource>
    private val emailValidationService = Mockito.mock(EmailValidationService::class.java)
    private val eligibilityService = Mockito.mock(CandidateEligibilityService::class.java)
    private val indexWriterService = Mockito.mock(ExpertIndexWriterService::class.java)
    private val indexService = Mockito.mock(ExpertIndexService::class.java)
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val esProperties = ElasticsearchProperties(
        baseUrl = "https://es.example.com:9200",
        username = "elastic", password = "secret",
        rawIndexName = "orcid_info",
        candidateIndexName = "orcid_info_candidate",
        applicationIndexName = "orcid_info_application"
    )
    private val discoveryProperties = ExpertDiscoveryProperties(
        enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 200
    )
    private val objectMapper = ObjectMapper()
    private val service = ExpertDiscoveryService(
        europePmc, openAlexProvider, emailValidationService, eligibilityService,
        indexWriterService, indexService, expertSearchService, restTemplate, esProperties,
        discoveryProperties, objectMapper
    )

    init {
        Mockito.`when`(openAlexProvider.getIfAvailable()).thenReturn(null)
        DiscoveryMockHelper.stubEsHeadNotFound(restTemplate)
        Mockito.`when`(indexService.indexName(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.RAW)).thenReturn("orcid_info")
        Mockito.`when`(indexService.indexName(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.CANDIDATE)).thenReturn("orcid_info_candidate")
        Mockito.`when`(indexService.indexName(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.APPLICATION)).thenReturn("orcid_info_application")
    }

    private fun paper(pmcId: String, title: String, pubYear: Int = 2024) =
        PaperMetadata(pmcId = pmcId, pmid = "pmid-$pmcId", doi = "10.0/$pmcId",
            title = title, pubYear = pubYear, journal = "Nature",
            authors = listOf(PaperAuthor("John", "Smith", "0000-0001", "Oxford, UK")),
            source = "EUROPE_PMC")

    @Test
    fun `discover processes papers and extracts emails`() {
        val p1 = paper("PMC1", "Test Paper")
        val p2 = paper("PMC2", "No PMC paper").copy(pmcId = null)

        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1, p2), null, 2))
        DiscoveryMockHelper.stubExtractEmails(europePmc, "PMC1",
            listOf(AuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "john@oxford.ac.uk", EmailValidationResult(3, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val result = service.discover(PaperSearchCriteria(), "TEST")

        assertEquals(2, result.stats.totalPapers)
        assertEquals(1, result.stats.noEmailPapers)
        assertEquals(1, result.stats.indexed)
        assertEquals(1, result.stats.promoted)
    }

    @Test
    fun `discover skips duplicate emails`() {
        val p1 = paper("PMC1", "Test")

        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractEmails(europePmc, "PMC1",
            listOf(AuthorEmail("dup@example.com", "A", "B", false, null, null)))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "dup@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 1)

        val result = service.discover(PaperSearchCriteria(), "TEST")
        assertEquals(1, result.stats.duplicates)
        assertEquals(0, result.stats.indexed)
    }

    @Test
    fun `discover respects maxPapersPerRun limit`() {
        val limitedProperties = ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 2, maxAuthorsPerRun = 100)
        val limitedService = ExpertDiscoveryService(
            europePmc, openAlexProvider, emailValidationService, eligibilityService,
            indexWriterService, indexService, expertSearchService, restTemplate, esProperties,
            limitedProperties, objectMapper
        )
        val papers = (1..5).map { paper("PMC$it", "Paper $it") }
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, null, 5))
        for (i in 1..5) DiscoveryMockHelper.stubExtractEmails(europePmc, "PMC$i", emptyList())

        val result = limitedService.discover(PaperSearchCriteria(), "TEST")
        assertEquals(2, result.stats.totalPapers)
        assertEquals(2, result.stats.noEmailPapers)
    }

    @Test
    fun `discover rejects invalid email`() {
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractEmails(europePmc, "PMC1",
            listOf(AuthorEmail("bad-email", "X", "Y", false, null, null)))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "bad-email", EmailValidationResult(0, false, "INVALID_FORMAT"))

        val result = service.discover(PaperSearchCriteria(), "TEST")
        assertEquals(1, result.stats.emailRejected)
        assertEquals(0, result.stats.indexed)
    }

    @Test
    fun `discover filters non-eligible and counts reasons`() {
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractEmails(europePmc, "PMC1",
            listOf(AuthorEmail("filtered@example.com", "A", "B", false, "China", null)))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "filtered@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityFalse(eligibilityService, listOf("CHINESE_NATIONALITY"))

        val result = service.discover(PaperSearchCriteria(), "TEST")
        assertEquals(1, result.stats.indexed)
        assertEquals(1, result.stats.filtered)
        assertEquals(0, result.stats.promoted)
        assertEquals(1, result.stats.filterReasons["CHINESE_NATIONALITY"])
    }

    @Test
    fun `promotion failure increments promotionFailed not promoted`() {
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractEmails(europePmc, "PMC1",
            listOf(AuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "john@oxford.ac.uk", EmailValidationResult(3, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        // Use doThrow to avoid Mockito stubbing conflict
        Mockito.doThrow(RuntimeException("ES write failed"))
            .`when`(restTemplate).exchange(
                Mockito.contains("orcid_info_candidate/_doc/"),
                Mockito.eq(org.springframework.http.HttpMethod.PUT),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = service.discover(PaperSearchCriteria(), "TEST")
        assertEquals(1, result.stats.indexed)
        assertEquals(0, result.stats.promoted)
        assertEquals(1, result.stats.promotionFailed)
    }

    @Test
    fun `dedup search error counts dedupErrors and skips`() {
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractEmails(europePmc, "PMC1",
            listOf(AuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "john@oxford.ac.uk", EmailValidationResult(3, true))

        Mockito.doThrow(org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR))
            .`when`(restTemplate).exchange(
                Mockito.contains("/_search"),
                Mockito.eq(org.springframework.http.HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = service.discover(PaperSearchCriteria(), "TEST")
        assertEquals(0, result.stats.indexed)
        assertTrue(result.stats.dedupErrors >= 1)
    }

    @Test
    fun `discover normalizes email to lowercase`() {
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractEmails(europePmc, "PMC1",
            listOf(AuthorEmail("John@Oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "John@Oxford.ac.uk", EmailValidationResult(3, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val result = service.discover(PaperSearchCriteria(), "TEST")
        assertEquals(1, result.stats.indexed)
    }

    @Test
    fun `discover respects maxAuthorsPerRun limit`() {
        val limitedProperties = ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 2)
        val limitedService = ExpertDiscoveryService(
            europePmc, openAlexProvider, emailValidationService, eligibilityService,
            indexWriterService, indexService, expertSearchService, restTemplate, esProperties,
            limitedProperties, objectMapper
        )
        val p1 = paper("PMC1", "Paper 1")
        val p2 = paper("PMC2", "Paper 2")

        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1, p2), null, 2))
        DiscoveryMockHelper.stubExtractEmails(europePmc, "PMC1",
            listOf(
                AuthorEmail("a1@example.com", "A", "One", false, null, null),
                AuthorEmail("a2@example.com", "B", "Two", false, null, null)
            ))
        DiscoveryMockHelper.stubExtractEmails(europePmc, "PMC2",
            listOf(AuthorEmail("a3@example.com", "C", "Three", false, null, null)))

        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "a1@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "a2@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)

        val result = limitedService.discover(PaperSearchCriteria(), "TEST")
        assertEquals(2, result.stats.indexed)
    }

    @Test
    fun `no ORCID expert indexed but not promoted due to MISSING_ORCID`() {
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        // Author with no ORCID
        DiscoveryMockHelper.stubExtractEmails(europePmc, "PMC1",
            listOf(AuthorEmail("no-orcid@example.com", "No", "Orcid", false, "Some Lab", null)))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "no-orcid@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        // Eligibility rejects due to MISSING_ORCID
        DiscoveryMockHelper.stubEligibilityFalse(eligibilityService, listOf("MISSING_ORCID"))

        val result = service.discover(PaperSearchCriteria(), "TEST")

        assertEquals(1, result.stats.indexed)
        assertEquals(0, result.stats.promoted)
        assertEquals(1, result.stats.filtered)
        assertEquals(1, result.stats.filterReasons["MISSING_ORCID"])
    }

    @Test
    fun `enrichExistingExperts stops at maxExperts limit`() {
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.`when`(openAlexProvider.getIfAvailable()).thenReturn(openAlex)

        val experts = (1..5).map { i ->
            com.weibo.talentintroduction.expert.domain.ExpertProfile(
                orcidId = "0000-000$i", email = "e$i@example.com",
                givenNames = "Test", familyNames = "$i",
                country = "US", keyword = null, employment = null
            )
        }
        val enrichment = AuthorEnrichment(hIndex = 10, citationCount = 100, worksCount = 5)

        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(experts))

        Mockito.`when`(openAlex.enrichAuthorByOrcid(Mockito.anyString())).thenReturn(enrichment)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(org.springframework.http.HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = service.enrichExistingExperts(maxExperts = 2)
        assertEquals(2, result.enriched)
        assertEquals(0, result.failed)
    }
}
