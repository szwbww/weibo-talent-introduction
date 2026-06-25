package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
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
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.discovery.repository.DiscoverySourceCursorRepository
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

class ExpertDiscoveryServiceTest {
    private lateinit var revalidationService: ExpertRevalidationService
    private lateinit var europePmc: EuropePmcDataSource
    private lateinit var openAlexProvider: ObjectProvider<OpenAlexDataSource>
    private lateinit var crossrefProvider: ObjectProvider<CrossrefDataSource>
    private lateinit var arxivProvider: ObjectProvider<ArxivDataSource>
    private lateinit var pmcOaProvider: ObjectProvider<PmcOaDataSource>
    private lateinit var orcidProvider: ObjectProvider<OrcidDataSource>
    private lateinit var coreProvider: ObjectProvider<CoreDataSource>
    private lateinit var emailValidationService: EmailValidationService
    private lateinit var eligibilityService: CandidateEligibilityService
    private lateinit var indexWriterService: ExpertIndexWriterService
    private lateinit var indexService: ExpertIndexService
    private lateinit var expertSearchService: ExpertSearchService
    private lateinit var restTemplate: RestTemplate
    private lateinit var progressStore: TaskProgressStore
    private lateinit var cursorRepository: DiscoverySourceCursorRepository
    private val discoveryProperties = ExpertDiscoveryProperties(
        enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 200
    )
    private val objectMapper = ObjectMapper()
    private val esProperties = ElasticsearchProperties(
        baseUrl = "https://es.example.com:9200",
        username = "elastic", password = "secret",
        rawIndexName = "orcid_info",
        candidateIndexName = "orcid_info_candidate",
        applicationIndexName = "orcid_info_application"
    )

    @BeforeEach
    fun setUp() {
        europePmc = Mockito.mock(EuropePmcDataSource::class.java)
        @Suppress("UNCHECKED_CAST")
        openAlexProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<OpenAlexDataSource>
        @Suppress("UNCHECKED_CAST")
        crossrefProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<CrossrefDataSource>
        @Suppress("UNCHECKED_CAST")
        arxivProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<ArxivDataSource>
        @Suppress("UNCHECKED_CAST")
        pmcOaProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<PmcOaDataSource>
        @Suppress("UNCHECKED_CAST")
        orcidProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<OrcidDataSource>
        @Suppress("UNCHECKED_CAST")
        coreProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<CoreDataSource>
        emailValidationService = Mockito.mock(EmailValidationService::class.java)
        eligibilityService = Mockito.mock(CandidateEligibilityService::class.java)
        indexWriterService = Mockito.mock(ExpertIndexWriterService::class.java)
        indexService = Mockito.mock(ExpertIndexService::class.java)
        expertSearchService = Mockito.mock(ExpertSearchService::class.java)
        revalidationService = Mockito.mock(ExpertRevalidationService::class.java)
        restTemplate = Mockito.mock(RestTemplate::class.java)
        progressStore = Mockito.mock(TaskProgressStore::class.java)
        cursorRepository = Mockito.mock(DiscoverySourceCursorRepository::class.java)

        DiscoveryMockHelper.stubSourceInfo(europePmc)
        Mockito.doReturn(null).`when`(openAlexProvider).getIfAvailable()
        Mockito.doReturn(null).`when`(crossrefProvider).getIfAvailable()
        Mockito.doReturn(null).`when`(arxivProvider).getIfAvailable()
        Mockito.doReturn(null).`when`(pmcOaProvider).getIfAvailable()
        Mockito.doReturn(null).`when`(orcidProvider).getIfAvailable()
        Mockito.doReturn(null).`when`(coreProvider).getIfAvailable()
        DiscoveryMockHelper.stubEsHeadNotFound(restTemplate)
        Mockito.doReturn("orcid_info").`when`(indexService)
            .indexName(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.RAW)
        Mockito.doReturn("orcid_info_candidate").`when`(indexService)
            .indexName(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.CANDIDATE)
        Mockito.doReturn("orcid_info_application").`when`(indexService)
            .indexName(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.APPLICATION)
    }

    private fun createService(props: ExpertDiscoveryProperties = discoveryProperties): ExpertDiscoveryService {
        return ExpertDiscoveryService(
            europePmc, openAlexProvider, crossrefProvider, arxivProvider,
            pmcOaProvider, orcidProvider, coreProvider,
            emailValidationService, eligibilityService,
            indexWriterService, indexService, revalidationService, expertSearchService, restTemplate, esProperties,
            props, objectMapper, progressStore, cursorRepository
        )
    }

    private fun paper(pmcId: String, title: String, pubYear: Int = 2024) =
        PaperMetadata(pmcId = pmcId, pmid = "pmid-$pmcId", doi = "10.0/$pmcId",
            title = title, pubYear = pubYear, journal = "Nature",
            authors = listOf(PaperAuthor("John", "Smith", "0000-0001", "Oxford, UK")),
            source = "EUROPE_PMC")

    @Test
    fun `discover processes papers and extracts emails`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test Paper")

        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(AuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "john@oxford.ac.uk", EmailValidationResult(3, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val result = svc.discover(PaperSearchCriteria(), "TEST")

        assertEquals(1, result.stats.totalPapers)
        assertEquals(0, result.stats.noEmailPapers)
        assertEquals(1, result.stats.indexed)
        assertEquals(1, result.stats.promoted)
    }

    @Test
    fun `discover counts papers without emails`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test").copy(pmcId = null)

        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_PMC_ID")

        val result = svc.discover(PaperSearchCriteria(), "TEST")

        assertEquals(1, result.stats.totalPapers)
        assertEquals(1, result.stats.noEmailPapers)
        assertEquals(0, result.stats.indexed)
    }

    @Test
    fun `discover skips duplicate emails`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")

        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(AuthorEmail("dup@example.com", "A", "B", false, null, null)))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "dup@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 1)

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        assertEquals(1, result.stats.duplicates)
        assertEquals(0, result.stats.indexed)
    }

    @Test
    fun `discover respects maxPapersPerRun limit`() {
        val limitedProperties = ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 2, maxAuthorsPerRun = 100)
        val svc = createService(limitedProperties)
        val papers = (1..5).map { paper("PMC$it", "Paper $it") }
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, null, 5))
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_EMAIL_IN_FULLTEXT")

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        assertEquals(2, result.stats.totalPapers)
        assertEquals(2, result.stats.noEmailPapers)
    }

    @Test
    fun `discover rejects invalid email`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(AuthorEmail("bad-email", "X", "Y", false, null, null)))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "bad-email", EmailValidationResult(0, false, "INVALID_FORMAT"))

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        assertEquals(1, result.stats.emailRejected)
        assertEquals(0, result.stats.indexed)
    }

    @Test
    fun `discover filters non-eligible and counts reasons`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(AuthorEmail("filtered@example.com", "A", "B", false, "China", null)))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "filtered@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityFalse(eligibilityService, listOf("CHINESE_NATIONALITY"))

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        assertEquals(1, result.stats.indexed)
        assertEquals(1, result.stats.filtered)
        assertEquals(0, result.stats.promoted)
        assertEquals(1, result.stats.filterReasons["CHINESE_NATIONALITY"])
    }

    @Test
    fun `promotion failure increments promotionFailed not promoted`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(AuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "john@oxford.ac.uk", EmailValidationResult(3, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        Mockito.doThrow(RuntimeException("ES write failed"))
            .`when`(restTemplate).exchange(
                Mockito.contains("orcid_info_candidate/_doc/"),
                Mockito.eq(org.springframework.http.HttpMethod.PUT),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        assertEquals(1, result.stats.indexed)
        assertEquals(0, result.stats.promoted)
        assertEquals(1, result.stats.promotionFailed)
    }

    @Test
    fun `dedup search error counts dedupErrors and skips`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(AuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "john@oxford.ac.uk", EmailValidationResult(3, true))

        Mockito.doThrow(org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR))
            .`when`(restTemplate).exchange(
                Mockito.contains("/_search"),
                Mockito.eq(org.springframework.http.HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        assertEquals(0, result.stats.indexed)
        assertTrue(result.stats.dedupErrors >= 1)
    }

    @Test
    fun `discover normalizes email to lowercase`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(AuthorEmail("John@Oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "John@Oxford.ac.uk", EmailValidationResult(3, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        assertEquals(1, result.stats.indexed)
    }

    @Test
    fun `discover respects maxAuthorsPerRun limit`() {
        val limitedProperties = ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 2)
        val svc = createService(limitedProperties)
        val p1 = paper("PMC1", "Paper 1")

        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(
                AuthorEmail("a1@example.com", "A", "One", false, null, null),
                AuthorEmail("a2@example.com", "B", "Two", false, null, null)
            ))

        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "a1@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "a2@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        assertEquals(2, result.stats.indexed)
    }

    @Test
    fun `no ORCID expert indexed but not promoted due to MISSING_ORCID`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(AuthorEmail("no-orcid@example.com", "No", "Orcid", false, "Some Lab", null)))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "no-orcid@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityFalse(eligibilityService, listOf("MISSING_ORCID"))

        val result = svc.discover(PaperSearchCriteria(), "TEST")

        assertEquals(1, result.stats.indexed)
        assertEquals(0, result.stats.promoted)
        assertEquals(1, result.stats.filtered)
        assertEquals(1, result.stats.filterReasons["MISSING_ORCID"])
    }

    @Test
    fun `circuit breaker trips after 5 consecutive 429s with apiRequests equal to 5`() {
        val svc = createService()
        DiscoveryMockHelper.stubSearchPapersThrows(europePmc,
            org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS))

        val result = svc.discover(PaperSearchCriteria(cursor = "0"), "TEST")
        val sourceStats = result.stats.bySource["EUROPE_PMC"]
        assertEquals(5, sourceStats?.apiRequests)
        assertEquals(1, sourceStats?.failureReasons?.get("CIRCUIT_BREAKER"))
    }

    @Test
    fun `circuit breaker trips after 5 consecutive 503s with apiRequests equal to 5`() {
        val svc = createService()
        DiscoveryMockHelper.stubSearchPapersThrows(europePmc,
            org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))

        val result = svc.discover(PaperSearchCriteria(cursor = "0"), "TEST")
        val sourceStats = result.stats.bySource["EUROPE_PMC"]
        assertEquals(5, sourceStats?.apiRequests)
        assertEquals(1, sourceStats?.failureReasons?.get("CIRCUIT_BREAKER"))
    }

    @Test
    fun `normal timeout request counts toward apiRequests`() {
        val svc = createService()
        DiscoveryMockHelper.stubSearchPapersThrows(europePmc, RuntimeException("timeout"))

        val result = svc.discover(PaperSearchCriteria(cursor = "0"), "TEST")
        val sourceStats = result.stats.bySource["EUROPE_PMC"]
        assertEquals(1, sourceStats?.apiRequests)
        assertEquals(1, sourceStats?.failureReasons?.get("SEARCH_FAILED"))
    }

    @Test
    fun `per-source maxPapersPerSource limit is enforced`() {
        val limitedProperties = ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 100)
        val svc = createService(limitedProperties)
        val papers = (1..5).map { paper("PMC$it", "Paper $it") }
        DiscoveryMockHelper.stubMaxPapersPerSource(europePmc, 3)
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, null, 5))
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_EMAIL_IN_FULLTEXT")

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        assertEquals(3, result.stats.totalPapers)
        val sourceStats = result.stats.bySource["EUROPE_PMC"]
        assertEquals(3, sourceStats?.papersSearched)
    }

    @Test
    fun `NO_EMAIL_IN_FULLTEXT increments both fulltextObtained and noEmailInFulltext`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_EMAIL_IN_FULLTEXT")

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        val sourceStats = result.stats.bySource["EUROPE_PMC"]
        assertEquals(1, sourceStats?.fulltextObtained)
        assertEquals(1, sourceStats?.noEmailInFulltext)
    }

    @Test
    fun `FULLTEXT_FETCH_FAILED does not increment fulltextObtained`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmailsOutcome(europePmc,
            EmailExtractionOutcome(emptyList(), "FULLTEXT_XML", "FULLTEXT_FETCH_FAILED", httpRequests = 1))

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        val sourceStats = result.stats.bySource["EUROPE_PMC"]
        assertEquals(0, sourceStats?.fulltextObtained)
        assertEquals(1, sourceStats?.failureReasons?.get("FULLTEXT_FETCH_FAILED"))
    }

    @Test
    fun `batchRejectReasons contains per-batch delta not cumulative totals`() {
        val svc = createService()
        val p1 = paper("PMC1", "Paper 1")
        val p2 = paper("PMC2", "Paper 2")
        val p3 = paper("PMC3", "Paper 3")

        DiscoveryMockHelper.stubSearchPapersSequence(europePmc,
            PaperSearchResult(listOf(p1, p2), "cursor2", 3),
            PaperSearchResult(listOf(p3), null, 3)
        )

        DiscoveryMockHelper.stubExtractAuthorEmailsSequence(europePmc,
            EmailExtractionOutcome(emptyList(), "FULLTEXT_XML", "NO_EMAIL_IN_FULLTEXT", httpRequests = 1),
            EmailExtractionOutcome(emptyList(), "FULLTEXT_XML", "NO_EMAIL_IN_FULLTEXT", httpRequests = 1),
            EmailExtractionOutcome(
                listOf(AuthorEmail("dup@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")),
                "FULLTEXT_XML", null, httpRequests = 1
            )
        )

        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "dup@oxford.ac.uk", EmailValidationResult(3, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 1)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val captured = mutableListOf<TaskProgress>()
        DiscoveryMockHelper.captureProgressUpdates(progressStore, captured)

        svc.discover(PaperSearchCriteria(), "TEST")

        val batchProgress = captured.filter {
            it.status == "RUNNING" && it.details?.get("currentSource") == "EUROPE_PMC"
        }.sortedBy { it.batchNumber }

        assertEquals(2, batchProgress.size)
        assertEquals(mapOf("NO_EMAIL_IN_FULLTEXT" to 2), batchProgress[0].batchRejectReasons)
        assertEquals(mapOf("DUPLICATE" to 1), batchProgress[1].batchRejectReasons)
    }

    @Test
    fun `PDF_DOWNLOAD_FAILED does not increment fulltextObtained`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmailsOutcome(europePmc,
            EmailExtractionOutcome(emptyList(), "PDF_PARSE", "PDF_DOWNLOAD_FAILED", httpRequests = 1))

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        val sourceStats = result.stats.bySource["EUROPE_PMC"]
        assertEquals(0, sourceStats?.fulltextObtained)
        assertEquals(1, sourceStats?.pdfDownloadFailed)
    }

    @Test
    fun `ORCID progress uses same unit for processedCount and totalCount`() {
        val svc = createService(ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 200))
        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        DiscoveryMockHelper.stubOrcidRecordToAuthorEmails(orcid)
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 25)

        val records = (1..10).map { OrcidDataSource.OrcidRecord(
            orcidId = "0000-000$it", givenNames = "Test", familyNames = "$it",
            emails = listOf("test$it@example.com"), institutionName = "Univ", country = null
        )}
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, records)

        for (i in 1..10) {
            DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test$i@example.com", EmailValidationResult(2, true))
        }
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val captured = mutableListOf<TaskProgress>()
        DiscoveryMockHelper.captureProgressUpdates(progressStore, captured)

        svc.discover(PaperSearchCriteria(), "TEST")

        val orcidProgress = captured.find {
            it.status == "RUNNING" && it.details?.get("currentSource") == "ORCID"
        }
        assertNotNull(orcidProgress, "Should have RUNNING progress with currentSource=ORCID")
        assertEquals(10, orcidProgress!!.processedCount)
        assertEquals(25, orcidProgress.totalCount)
        assertTrue(orcidProgress.processedCount <= orcidProgress.totalCount)
    }

    @Test
    fun `ORCID batchProcessed equals actually processed records when API returns more than limit`() {
        val svc = createService()
        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        DiscoveryMockHelper.stubOrcidRecordToAuthorEmails(orcid)
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 5)

        val records = (1..20).map { OrcidDataSource.OrcidRecord(
            orcidId = "0000-000$it", givenNames = "Test", familyNames = "$it",
            emails = listOf("test$it@example.com"), institutionName = "Univ", country = null
        )}
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, records)

        for (i in 1..5) {
            DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test$i@example.com", EmailValidationResult(2, true))
        }
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val captured = mutableListOf<TaskProgress>()
        DiscoveryMockHelper.captureProgressUpdates(progressStore, captured)

        svc.discover(PaperSearchCriteria(), "TEST")

        val orcidProgress = captured.find {
            it.status == "RUNNING" && it.details?.get("currentSource") == "ORCID"
        }
        assertNotNull(orcidProgress, "Should have RUNNING progress with currentSource=ORCID")
        assertEquals(5, orcidProgress!!.batchProcessed,
            "batchProcessed should only count actually processed records, not API response size")
        assertEquals(5, orcidProgress.processedCount)
        assertEquals(5, orcidProgress.totalCount)
    }

    @Test
    fun `ORCID batchRejected equals batchProcessed minus batchPassed`() {
        val svc = createService()
        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        DiscoveryMockHelper.stubOrcidRecordToAuthorEmails(orcid)
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 10)

        val records = (1..5).map { OrcidDataSource.OrcidRecord(
            orcidId = "0000-000$it", givenNames = "Test", familyNames = "$it",
            emails = listOf("test$it@example.com"), institutionName = "Univ", country = null
        )}
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, records)

        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test1@example.com", EmailValidationResult(0, false))
        for (i in 2..5) {
            DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test$i@example.com", EmailValidationResult(2, true))
        }
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val captured = mutableListOf<TaskProgress>()
        DiscoveryMockHelper.captureProgressUpdates(progressStore, captured)

        svc.discover(PaperSearchCriteria(), "TEST")

        val orcidProgress = captured.find {
            it.status == "RUNNING" && it.details?.get("currentSource") == "ORCID"
        }
        assertNotNull(orcidProgress, "Should have RUNNING progress with currentSource=ORCID")
        assertEquals(5, orcidProgress!!.batchProcessed)
        assertEquals(4, orcidProgress.batchPassed)
        assertEquals(1, orcidProgress.batchRejected)
        assertEquals(orcidProgress.batchProcessed, orcidProgress.batchPassed + orcidProgress.batchRejected)
    }

    @Test
    fun `ORCID progress not corrupted by preceding Europe PMC papers`() {
        val svc = createService(ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 200))
        // First, stub Europe PMC to process 3 papers
        val papers = (1..3).map { paper("PMC$it", "Paper $it") }
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, null, 3))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            (1..3).map { AuthorEmail("emc$it@oxford.ac.uk", "Author", "$it", true, "Oxford, UK", "0000-000$it") }
        )
        for (i in 1..3) {
            DiscoveryMockHelper.stubValidateEmail(emailValidationService, "emc$i@oxford.ac.uk", EmailValidationResult(2, true))
        }
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        // Then, stub ORCID to process 10 records
        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        DiscoveryMockHelper.stubOrcidRecordToAuthorEmails(orcid)
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 25)
        val orcidRecords = (1..10).map { OrcidDataSource.OrcidRecord(
            orcidId = "0000-000$it", givenNames = "O", familyNames = "$it",
            emails = listOf("or$it@univ.edu"), institutionName = "Univ", country = null
        )}
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, orcidRecords)
        for (i in 1..10) {
            DiscoveryMockHelper.stubValidateEmail(emailValidationService, "or$i@univ.edu", EmailValidationResult(2, true))
        }

        val captured = mutableListOf<TaskProgress>()
        DiscoveryMockHelper.captureProgressUpdates(progressStore, captured)

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        assertEquals(13, result.stats.totalPapers)

        val orcidProgress = captured.find {
            it.status == "RUNNING" && it.details?.get("currentSource") == "ORCID"
        }
        assertNotNull(orcidProgress)
        assertEquals(10, orcidProgress!!.processedCount,
            "ORCID processedCount must be 10 (ORCID records), not 13 (global papers)")
        assertEquals(25, orcidProgress.totalCount)
        assertTrue(orcidProgress.processedCount <= orcidProgress.totalCount)
    }

    @Test
    fun `ORCID batch stops at global author limit without counting unprocessed records`() {
        val svc = createService(ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 2))
        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        DiscoveryMockHelper.stubOrcidRecordToAuthorEmails(orcid)
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 100)

        // API returns 10 records, each with one valid email
        val records = (1..10).map { OrcidDataSource.OrcidRecord(
            orcidId = "0000-000$it", givenNames = "Test", familyNames = "$it",
            emails = listOf("test$it@example.com"), institutionName = "Univ", country = null
        )}
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, records)

        for (i in 1..10) {
            DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test$i@example.com", EmailValidationResult(2, true))
        }
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val captured = mutableListOf<TaskProgress>()
        DiscoveryMockHelper.captureProgressUpdates(progressStore, captured)

        svc.discover(PaperSearchCriteria(), "TEST")

        val orcidProgress = captured.find {
            it.status == "RUNNING" && it.details?.get("currentSource") == "ORCID"
        }
        assertNotNull(orcidProgress)
        assertEquals(2, orcidProgress!!.batchProcessed,
            "Only 2 records should be processed before hitting maxAuthorsPerRun=2")
        assertEquals(2, orcidProgress.processedCount)
        assertEquals(
            orcidProgress.batchProcessed,
            orcidProgress.batchPassed + orcidProgress.batchRejected,
            "batchProcessed must equal batchPassed + batchRejected"
        )
    }

    @Test
    fun `enrichExistingExperts stops at maxExperts limit`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val experts = (1..5).map { i ->
            com.weibo.talentintroduction.expert.domain.ExpertProfile(
                orcidId = "0000-000$i", email = "e$i@example.com",
                givenNames = "Test", familyNames = "$i",
                country = "US", keyword = null, employment = null
            )
        }
        val enrichment = AuthorEnrichment(hIndex = 10, citationCount = 100, worksCount = 5)

        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(experts))

        Mockito.doReturn(enrichment).`when`(openAlex).enrichAuthorByOrcid(Mockito.anyString())
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(org.springframework.http.HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = svc.enrichExistingExperts(maxExperts = 2)
        assertEquals(2, result.enriched)
        assertEquals(0, result.failed)
    }

    @Test
    fun `promoteRawToCandidateWithEmail skips when CANDIDATE already exists`() {
        val svc = createService()
        DiscoveryMockHelper.stubEsCandidateHeadExists(restTemplate)

        val rawExpert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-0001-raw", email = null,
            givenNames = "Test", familyNames = "User",
            country = "US", keyword = null, employment = null
        )
        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(listOf(rawExpert)))

        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        val record = OrcidDataSource.OrcidRecord(
            orcidId = "0000-0001-raw", givenNames = "Test", familyNames = "User",
            emails = listOf("test@example.com"), institutionName = "Univ", country = null
        )
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, listOf(record))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsRawDocGet(restTemplate)

        svc.discover(PaperSearchCriteria(), "TEST")

        Mockito.verify(restTemplate, Mockito.never()).exchange(
            Mockito.contains("orcid_info_candidate/_doc/"),
            Mockito.eq(org.springframework.http.HttpMethod.PUT),
            Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `tryGetEmailFromOrcid skips when orcidId does not match record`() {
        val svc = createService()
        DiscoveryMockHelper.stubEsCandidateHeadExists(restTemplate)

        val rawExpert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-0001-raw", email = null,
            givenNames = "Test", familyNames = "User",
            country = "US", keyword = null, employment = null
        )
        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(listOf(rawExpert)))

        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        val mismatchedRecord = OrcidDataSource.OrcidRecord(
            orcidId = "0000-0009-other", givenNames = "Other", familyNames = "Person",
            emails = listOf("other@example.com"), institutionName = "OtherUniv", country = null
        )
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, listOf(mismatchedRecord))

        svc.discover(PaperSearchCriteria(), "TEST")

        Mockito.verify(restTemplate, Mockito.never()).exchange(
            Mockito.contains("orcid_info/_update/"),
            Mockito.eq(org.springframework.http.HttpMethod.POST),
            Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `backfillRawEmailsAndPromote stops at 100 attempts when CANDIDATE already exists`() {
        val svc = createService()
        DiscoveryMockHelper.stubEsCandidateHeadExists(restTemplate)

        val experts = (1..101).map { i ->
            com.weibo.talentintroduction.expert.domain.ExpertProfile(
                orcidId = "0000-00%02d-raw".format(i), email = null,
                givenNames = "Test", familyNames = "$i",
                country = "US", keyword = null, employment = null
            )
        }
        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(experts))

        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        val record = OrcidDataSource.OrcidRecord(
            orcidId = "0000-0001-raw", givenNames = "Test", familyNames = "User",
            emails = listOf("test@example.com"), institutionName = "Univ", country = null
        )
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, listOf(record))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test@example.com", EmailValidationResult(2, true))

        svc.discover(PaperSearchCriteria(), "TEST")

        DiscoveryMockHelper.verifyOrcidSearchRecordsCalled(orcid, 100)
    }

    @Test
    fun `promoteRawToCandidateWithEmail promotes when CANDIDATE does not exist`() {
        val svc = createService()

        val rawExpert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-0001-raw", email = null,
            givenNames = "Test", familyNames = "User",
            country = "US", keyword = null, employment = null
        )
        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(listOf(rawExpert)))

        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        val record = OrcidDataSource.OrcidRecord(
            orcidId = "0000-0001-raw", givenNames = "Test", familyNames = "User",
            emails = listOf("test@example.com"), institutionName = "Univ", country = null
        )
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, listOf(record))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsRawUpdate(restTemplate)
        DiscoveryMockHelper.stubEsRawDocGet(restTemplate)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)

        svc.discover(PaperSearchCriteria(), "TEST")

        DiscoveryMockHelper.verifyCandidatePutCalled(restTemplate, 1)
    }

    @Test
    fun `promoteRawToCandidateWithEmail fails closed on HEAD server error`() {
        val svc = createService()
        DiscoveryMockHelper.stubEsHeadServerError(restTemplate)

        val rawExpert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-0001-raw", email = null,
            givenNames = "Test", familyNames = "User",
            country = "US", keyword = null, employment = null
        )
        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(listOf(rawExpert)))

        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        val record = OrcidDataSource.OrcidRecord(
            orcidId = "0000-0001-raw", givenNames = "Test", familyNames = "User",
            emails = listOf("test@example.com"), institutionName = "Univ", country = null
        )
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, listOf(record))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsRawUpdate(restTemplate)

        svc.discover(PaperSearchCriteria(), "TEST")

        DiscoveryMockHelper.verifyCandidatePutNeverCalled(restTemplate)
    }

    @Test
    fun `tryGetEmailFromOrcid matches URL form orcidId`() {
        val svc = createService()

        val rawExpert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "https://orcid.org/0000-0001-2345", email = null,
            givenNames = "Test", familyNames = "User",
            country = "US", keyword = null, employment = null
        )
        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(listOf(rawExpert)))

        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        val matchedRecord = OrcidDataSource.OrcidRecord(
            orcidId = "https://orcid.org/0000-0001-2345", givenNames = "Test", familyNames = "User",
            emails = listOf("test@example.com"), institutionName = "Univ", country = null
        )
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, listOf(matchedRecord))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test@example.com", EmailValidationResult(2, true))

        svc.discover(PaperSearchCriteria(), "TEST")

        DiscoveryMockHelper.verifyRawUpdateCalled(restTemplate, 1)
    }

    @Test
    fun `backfillRawEmailsAndPromote selects first valid email`() {
        val svc = createService()
        DiscoveryMockHelper.stubEsCandidateHeadExists(restTemplate)

        val rawExpert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-0001-raw", email = null,
            givenNames = "Test", familyNames = "User",
            country = "US", keyword = null, employment = null
        )
        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(listOf(rawExpert)))

        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        val record = OrcidDataSource.OrcidRecord(
            orcidId = "0000-0001-raw", givenNames = "Test", familyNames = "User",
            emails = listOf("invalid@tmp.com", "valid@uni.edu"), institutionName = "Univ", country = null
        )
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, listOf(record))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "invalid@tmp.com", EmailValidationResult(0, false))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "valid@uni.edu", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsRawUpdate(restTemplate)

        svc.discover(PaperSearchCriteria(), "TEST")

        DiscoveryMockHelper.verifyRawUpdateCalled(restTemplate, 1)
    }

    @Test
    fun `cancel after ORCID returns does not write RAW or CANDIDATE`() {
        val svc = createService()
        DiscoveryMockHelper.stubCancelledAfterNCalls(progressStore, 2)

        val rawExpert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-0001-raw", email = null,
            givenNames = "Test", familyNames = "User",
            country = "US", keyword = null, employment = null
        )
        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(listOf(rawExpert)))

        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        val record = OrcidDataSource.OrcidRecord(
            orcidId = "0000-0001-raw", givenNames = "Test", familyNames = "User",
            emails = listOf("test@example.com"), institutionName = "Univ", country = null
        )
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, listOf(record))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsRawUpdate(restTemplate)

        svc.discover(PaperSearchCriteria(), "TEST")

        DiscoveryMockHelper.verifyRawUpdateCalled(restTemplate, 0)
    }

    @Test
    fun `cancel after RAW update does not touch CANDIDATE`() {
        val svc = createService()
        DiscoveryMockHelper.stubCancelledAfterNCalls(progressStore, 4)

        val rawExpert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-0001-raw", email = null,
            givenNames = "Test", familyNames = "User",
            country = "US", keyword = null, employment = null
        )
        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(listOf(rawExpert)))

        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        val record = OrcidDataSource.OrcidRecord(
            orcidId = "0000-0001-raw", givenNames = "Test", familyNames = "User",
            emails = listOf("test@example.com"), institutionName = "Univ", country = null
        )
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, listOf(record))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsRawUpdate(restTemplate)
        DiscoveryMockHelper.stubEsRawDocGet(restTemplate)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)

        svc.discover(PaperSearchCriteria(), "TEST")

        DiscoveryMockHelper.verifyCandidatePutNeverCalled(restTemplate)
    }

    @Test
    fun `tryGetEmailFromOrcid matches bare orcidId against URL result`() {
        val svc = createService()

        val rawExpert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-0001-2345", email = null,
            givenNames = "Test", familyNames = "User",
            country = "US", keyword = null, employment = null
        )
        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(listOf(rawExpert)))

        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        val matchedRecord = OrcidDataSource.OrcidRecord(
            orcidId = "https://orcid.org/0000-0001-2345", givenNames = "Test", familyNames = "User",
            emails = listOf("test@example.com"), institutionName = "Univ", country = null
        )
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, listOf(matchedRecord))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test@example.com", EmailValidationResult(2, true))

        svc.discover(PaperSearchCriteria(), "TEST")

        DiscoveryMockHelper.verifyRawUpdateCalled(restTemplate, 1)
    }

    @Test
    fun `tryGetEmailFromOrcid matches URL orcidId against bare result`() {
        val svc = createService()

        val rawExpert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "https://orcid.org/0000-0001-2345", email = null,
            givenNames = "Test", familyNames = "User",
            country = "US", keyword = null, employment = null
        )
        ScrollExpertsMockHelper.stubScrollExperts(expertSearchService, listOf(listOf(rawExpert)))

        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        val matchedRecord = OrcidDataSource.OrcidRecord(
            orcidId = "0000-0001-2345", givenNames = "Test", familyNames = "User",
            emails = listOf("test@example.com"), institutionName = "Univ", country = null
        )
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, listOf(matchedRecord))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test@example.com", EmailValidationResult(2, true))

        svc.discover(PaperSearchCriteria(), "TEST")

        DiscoveryMockHelper.verifyRawUpdateCalled(restTemplate, 1)
    }

    @Test
    fun `partial batch does not advance cursor to nextCursor`() {
        // P1-1: 数据源返回 3 篇且 maxPapersPerRun=1，断言保存的游标不等于 batch.nextCursor
        val limitedProperties = ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 1, maxAuthorsPerRun = 200)
        val svc = createService(limitedProperties)
        val papers = (1..3).map { paper("PMC$it", "Paper $it") }
        val batchNextCursor = "page2-cursor"
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, batchNextCursor, 3))
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_EMAIL_IN_FULLTEXT")

        val captor = ArgumentCaptor.forClass(com.weibo.talentintroduction.discovery.domain.DiscoverySourceCursor::class.java)

        svc.discover(PaperSearchCriteria(), "TEST")

        // 验证 EUROPE_PMC 的 cursorValue 不等于 batch.nextCursor；ORCID 也会保存游标
        Mockito.verify(cursorRepository, Mockito.atLeastOnce()).save(captor.capture())
        val savedCursor = captor.allValues.single { it.sourceName == "EUROPE_PMC" }
        assertNotEquals(batchNextCursor, savedCursor.cursorValue,
            "Partial batch must NOT save nextCursor='$batchNextCursor'; " +
            "saved cursor was '${savedCursor.cursorValue}' which would skip unprocessed papers")
    }

    @Test
    fun `partial batch due to sourceLimit does not advance cursor`() {
        // P1-1 变体: 单源限额触发部分批次
        val props = ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 200)
        val svc = createService(props)
        val papers = (1..5).map { paper("PMC$it", "Paper $it") }
        val batchNextCursor = "next-page"
        DiscoveryMockHelper.stubMaxPapersPerSource(europePmc, 2)
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, batchNextCursor, 5))
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_EMAIL_IN_FULLTEXT")

        val captor = ArgumentCaptor.forClass(com.weibo.talentintroduction.discovery.domain.DiscoverySourceCursor::class.java)

        svc.discover(PaperSearchCriteria(), "TEST")

        Mockito.verify(cursorRepository, Mockito.atLeastOnce()).save(captor.capture())
        val savedCursor = captor.allValues.single { it.sourceName == "EUROPE_PMC" }
        assertNotEquals(batchNextCursor, savedCursor.cursorValue,
            "When sourceLimit causes partial batch, cursor must not advance to '$batchNextCursor'")
    }

    @Test
    fun `batch numbers are globally unique and monotonic across sources`() {
        val props = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 200, includeRawScan = false
        )
        val svc = createService(props)

        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        Mockito.doReturn("OPENALEX").`when`(openAlex).sourceName
        Mockito.doReturn("FULLTEXT_XML").`when`(openAlex).emailExtractionMethod
        DiscoveryMockHelper.stubMaxPapersPerSource(openAlex, 500)

        val epmcP1 = paper("PMC1", "EPMC Paper 1")
        val epmcP2 = paper("PMC2", "EPMC Paper 2")
        DiscoveryMockHelper.stubSearchPapersSequence(europePmc,
            PaperSearchResult(listOf(epmcP1), "epmc-cursor", 2),
            PaperSearchResult(listOf(epmcP2), null, 2)
        )
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_EMAIL_IN_FULLTEXT")

        val oaPaper = PaperMetadata(
            pmcId = null, pmid = null, doi = "10.0/W1", title = "OA Paper", pubYear = 2024,
            journal = "Nature", authors = listOf(PaperAuthor("Jane", "Doe", "0000-0002", "MIT, US")),
            source = "OPENALEX"
        )
        Mockito.doReturn(PaperSearchResult(listOf(oaPaper), null, 1))
            .`when`(openAlex).searchPapers(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(openAlex, "NO_PMC_ID")

        val captured = mutableListOf<TaskProgress>()
        DiscoveryMockHelper.captureProgressUpdates(progressStore, captured)

        svc.discover(PaperSearchCriteria(), "TEST", includeRawScan = false)

        val batchProgress = captured.filter { it.status == "RUNNING" && it.batchNumber > 0 }
        val batchNumbers = batchProgress.map { it.batchNumber }

        assertEquals(listOf(1, 2, 3), batchNumbers, "batchNumber must be globally continuous across sources")
        assertEquals(batchNumbers.size, batchNumbers.toSet().size, "batchNumber must be unique within execution")

        val epmcLogs = batchProgress.filter { it.details?.get("currentSource") == "EUROPE_PMC" }
        val openAlexLogs = batchProgress.filter { it.details?.get("currentSource") == "OPENALEX" }
        assertEquals(2, epmcLogs.size)
        assertEquals(1, openAlexLogs.size)
        assertTrue(epmcLogs.all { it.message?.contains("批次 1") == true || it.message?.contains("批次 2") == true })
        assertTrue(openAlexLogs.single().message?.contains("批次 1") == true)
    }
}
