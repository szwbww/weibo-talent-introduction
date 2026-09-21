package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.OpenAlexBudgetDeferredException
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.config.DiscoveryExecutorConfig
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.DiscoverySourceCursor
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import com.weibo.talentintroduction.expert.domain.EmailValidationResult
import com.weibo.talentintroduction.expert.service.CandidateEligibilityService
import com.weibo.talentintroduction.expert.service.EmailValidationService
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertClassificationService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.expert.service.ScrollExpertsMockHelper
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.discovery.repository.DiscoverySourceCursorRepository
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import java.util.concurrent.Executors

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
    private val expertClassificationService = ExpertClassificationService()
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
    private val openAlexProperties = OpenAlexProperties(
        enabled = true,
        enrichmentBatchSize = 50
    )

    private fun <T : Any> eqValue(value: T): T = Mockito.eq(value) ?: value

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

    private fun createService(
        props: ExpertDiscoveryProperties = discoveryProperties,
        executor: Executor = Executor { it.run() },
        openAlexProps: OpenAlexProperties = openAlexProperties,
        europePmcProps: EuropePmcProperties = EuropePmcProperties()
    ): ExpertDiscoveryService {
        return ExpertDiscoveryService(
            europePmc, openAlexProvider, crossrefProvider, arxivProvider,
            pmcOaProvider, orcidProvider, coreProvider,
            emailValidationService, eligibilityService,
            indexWriterService, indexService, revalidationService, expertSearchService, expertClassificationService, restTemplate, esProperties,
            props, openAlexProps, objectMapper, progressStore, cursorRepository, executor,
            europePmcProps
        )
    }

    private fun paper(pmcId: String, title: String, pubYear: Int = 2024) =
        PaperMetadata(pmcId = pmcId, pmid = "pmid-$pmcId", doi = "10.0/$pmcId",
            title = title, pubYear = pubYear, journal = "Nature",
            authors = listOf(PaperAuthor("John", "Smith", "0000-0001", "Oxford, UK")),
            source = "EUROPE_PMC")

    /** 内存游标表：读得到本轮写入，可断言最终落盘的检查点。 */
    private val storedCheckpoints = mutableMapOf<String, DiscoverySourceCursor>()
    private var cursorStoreInstalled = false

    private fun installInMemoryCursorStore() {
        if (cursorStoreInstalled) return
        cursorStoreInstalled = true
        Mockito.doAnswer { invocation ->
            val entity = invocation.getArgument(0) as DiscoverySourceCursor
            storedCheckpoints[entity.sourceName] = entity
            entity
        }.`when`(cursorRepository).save(Mockito.any(DiscoverySourceCursor::class.java))
        Mockito.doAnswer { invocation ->
            storedCheckpoints[invocation.getArgument<String>(0)]
        }.`when`(cursorRepository).findBySourceName(Mockito.anyString())
    }

    /** 预置一行 v2 检查点；未预置的 key 视为无检查点。 */
    private fun stubStoredCheckpoint(
        sourceName: String,
        cursor: String?,
        criteria: PaperSearchCriteria = PaperSearchCriteria(),
        exhausted: Boolean = false
    ) {
        installInMemoryCursorStore()
        val key = DiscoveryCheckpointCodec.sourceKey(sourceName, criteria)
        storedCheckpoints[key] = DiscoverySourceCursor(
            id = 1L,
            sourceName = key,
            cursorValue = DiscoveryCheckpointCodec.encode(cursor, exhausted)
        )
    }

    /** 本轮运行结束时该 key 落盘的检查点。 */
    private fun storedCheckpoint(key: String): SourceCheckpoint {
        val entry = storedCheckpoints[key]
        assertNotNull(entry, "没有为 key=$key 落盘任何检查点")
        return decodedCheckpoint(entry!!)
    }

    /** 该来源本轮最终落盘的行（持久化边界证据）。 */
    private fun storedRowFor(
        sourceName: String,
        criteria: PaperSearchCriteria = PaperSearchCriteria()
    ): DiscoverySourceCursor {
        val key = DiscoveryCheckpointCodec.sourceKey(sourceName, criteria)
        val entry = storedCheckpoints[key]
        assertNotNull(entry, "没有为 key=$key 落盘任何检查点")
        return entry!!
    }

    private fun storedCheckpointFor(
        sourceName: String,
        criteria: PaperSearchCriteria = PaperSearchCriteria()
    ): SourceCheckpoint = decodedCheckpoint(storedRowFor(sourceName, criteria))

    /**
     * 用 Answer 记录该来源真实收到的请求条件并返回固定结果。
     * Kotlin 声明的非空参数上 Mockito.any()/ArgumentCaptor.capture() 会插入空检查，故必须用 Answer。
     */
    private fun stubAndRecordRequests(
        source: AcademicDataSource,
        result: PaperSearchResult
    ): MutableList<PaperSearchCriteria> {
        val seen = mutableListOf<PaperSearchCriteria>()
        Mockito.doAnswer { invocation ->
            seen.add(invocation.getArgument(0) as PaperSearchCriteria)
            result
        }.`when`(source).searchPapers(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
        return seen
    }

    private fun runAndCapture(
        svc: ExpertDiscoveryService,
        criteria: PaperSearchCriteria = PaperSearchCriteria()
    ): Pair<DiscoveryResult, List<DiscoverySourceCursor>> {
        val captor = ArgumentCaptor.forClass(DiscoverySourceCursor::class.java)
        val result = svc.discover(criteria, "TEST")
        Mockito.verify(cursorRepository, Mockito.atLeastOnce()).save(captor.capture())
        return result to captor.allValues
    }

    private fun savedCheckpoints(saved: List<DiscoverySourceCursor>, sourceName: String): List<DiscoverySourceCursor> =
        saved.filter { it.sourceName.startsWith("$sourceName:v2:") }

    private fun decodedCheckpoint(entry: DiscoverySourceCursor): SourceCheckpoint {
        val decoded = DiscoveryCheckpointCodec.decode(entry.cursorValue)
        assertNotNull(decoded, "saved value must be a v2 envelope, was '${entry.cursorValue}'")
        return decoded!!
    }

    private fun stubSource(source: AcademicDataSource, name: String) {
        Mockito.doReturn(name).`when`(source).sourceName
        Mockito.doReturn("FULLTEXT_XML").`when`(source).emailExtractionMethod
        Mockito.doReturn(10).`when`(source).maxPapersPerSource
        // 不 stub searchPapers：Mockito 返回 null，discoverFromSource 以 batch == null 空批次退出，
        // bySource 条目仍会创建 —— 这足以验证 resolveEnabledSources 的启用集合。
    }

    @Test
    fun `resolveEnabledSources excludes EUROPE_PMC when disabled even with empty sources`() {
        // I4-4 核心断言（当前缺陷的回归测试）：EUROPE_PMC_ENABLED=false + sources 为空（定时发现路径）
        // 时，Europe PMC 不得加入启用源列表。修复前 :209 的 add 从不读 enabled，此处必含 EUROPE_PMC。
        val svc = createService(europePmcProps = EuropePmcProperties(enabled = false))

        val result = svc.discover(PaperSearchCriteria(), "TEST")

        assertFalse(result.stats.bySource.containsKey("EUROPE_PMC"),
            "Europe PMC must not run when europePmcProperties.enabled=false")
    }

    @Test
    fun `resolveEnabledSources keeps all six sources when enabled and scope null`() {
        // I4-2：enabled=true + subjectScope == null 时结果与改动前一致（六源全在）。
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        val crossref = Mockito.mock(CrossrefDataSource::class.java)
        val arxiv = Mockito.mock(ArxivDataSource::class.java)
        val pmcOa = Mockito.mock(PmcOaDataSource::class.java)
        val core = Mockito.mock(CoreDataSource::class.java)
        stubSource(openAlex, "OPENALEX")
        stubSource(crossref, "CROSSREF")
        stubSource(arxiv, "ARXIV")
        stubSource(pmcOa, "PMC_OA")
        stubSource(core, "CORE")
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        Mockito.doReturn(crossref).`when`(crossrefProvider).getIfAvailable()
        Mockito.doReturn(arxiv).`when`(arxivProvider).getIfAvailable()
        Mockito.doReturn(pmcOa).`when`(pmcOaProvider).getIfAvailable()
        Mockito.doReturn(core).`when`(coreProvider).getIfAvailable()

        val svc = createService()
        val result = svc.discover(PaperSearchCriteria(), "TEST")

        val sourceNames = result.stats.bySource.keys
        assertTrue(sourceNames.containsAll(listOf("EUROPE_PMC", "PMC_OA", "OPENALEX", "CROSSREF", "CORE", "ARXIV")),
            "null scope must keep all six sources, got: $sourceNames")
    }

    @Test
    fun `resolveEnabledSources excludes EUROPE_PMC and PMC_OA under RND_TARGET`() {
        // I4-3：RND_TARGET 下生物医学两源「本次不参与」，其余四源保留。
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        val crossref = Mockito.mock(CrossrefDataSource::class.java)
        val arxiv = Mockito.mock(ArxivDataSource::class.java)
        val pmcOa = Mockito.mock(PmcOaDataSource::class.java)
        val core = Mockito.mock(CoreDataSource::class.java)
        stubSource(openAlex, "OPENALEX")
        stubSource(crossref, "CROSSREF")
        stubSource(arxiv, "ARXIV")
        stubSource(pmcOa, "PMC_OA")
        stubSource(core, "CORE")
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        Mockito.doReturn(crossref).`when`(crossrefProvider).getIfAvailable()
        Mockito.doReturn(arxiv).`when`(arxivProvider).getIfAvailable()
        Mockito.doReturn(pmcOa).`when`(pmcOaProvider).getIfAvailable()
        Mockito.doReturn(core).`when`(coreProvider).getIfAvailable()

        val svc = createService()
        val result = svc.discover(
            PaperSearchCriteria(subjectScope = SubjectScopeCatalog.RND_TARGET),
            "TEST"
        )

        val sourceNames = result.stats.bySource.keys
        assertFalse(sourceNames.contains("EUROPE_PMC"), "RND_TARGET must exclude EUROPE_PMC, got: $sourceNames")
        assertFalse(sourceNames.contains("PMC_OA"), "RND_TARGET must exclude PMC_OA, got: $sourceNames")
        assertTrue(sourceNames.containsAll(listOf("OPENALEX", "CROSSREF", "CORE", "ARXIV")),
            "RND_TARGET must keep the other four sources, got: $sourceNames")
    }

    @Test
    fun `resolveEnabledSources scope exclusion overrides manual sources selection`() {
        // I4-3 语义固定：手动指定 sources=["EUROPE_PMC"] 且 enabled=true 时，
        // subjectScope=RND_TARGET 仍排除 Europe PMC —— scope 排除优先于手动指定。
        val svc = createService()
        val result = svc.discover(
            PaperSearchCriteria(sources = listOf("EUROPE_PMC"), subjectScope = "RND_TARGET"),
            "TEST"
        )

        assertFalse(result.stats.bySource.containsKey("EUROPE_PMC"),
            "scope exclusion must override manual sources selection")
    }

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
    fun `discover writes institutionType into RAW index map (I5a-3 I5a-4)`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test Paper")

        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(AuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001",
                institutionType = "education")))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "john@oxford.ac.uk", EmailValidationResult(3, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
        val capturedMaps = mutableListOf<Map<String, Any?>>()
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedMaps.add(invocation.getArgument(1) as Map<String, Any?>)
            true
        }.`when`(indexWriterService).indexToRaw(Mockito.anyString(), Mockito.anyMap())

        svc.discover(PaperSearchCriteria(), "TEST")

        val map = capturedMaps.single()
        assertEquals("education", map["institutionType"])
        // 回归：键集合 = 改动前集合 + 仅新增 institutionType 一项（I5a-4 语义，逐字锚定改动前键集）。
        val preChangeKeys = setOf(
            "orcidId", "email", "givenNames", "familyNames", "country", "keyword", "employment",
            "institution", "lastPublicationYear", "emailSource", "emailVerifiedLevel", "dataSource",
            "externalIds", "discoveredAt", "updatedAt", "filterResult", "filterRejectReason", "tags"
        )
        assertEquals(preChangeKeys + "institutionType", map.keys)
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
    fun `enrichExistingExperts processes all pending experts without limit`() {
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

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(experts))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 5L, 5L)
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val orcids = invocation.arguments[0] as List<String>
            orcids.associateWith { EnrichmentOutcome.Success(enrichment) }
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        DiscoveryMockHelper.stubEsEnrichmentHeadExists(restTemplate)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(org.springframework.http.HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = svc.enrichExistingExperts()
        assertEquals(5, result.enriched)
        assertEquals(0, result.failed)
    }

    @Test
    fun `enrichExistingExperts skips expert enriched exactly 30 days ago`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 0L)
        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, emptyList())

        val result = svc.enrichExistingExperts()

        assertEquals(0, result.enriched)
        assertEquals(0, result.failed)
        Mockito.verify(openAlex, Mockito.never()).batchEnrichByOrcids(Mockito.anyList())
    }

    @Test
    fun `enrichExistingExperts re-enriches expert enriched 31 days ago`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val enrichedAt = LocalDate.now().minusDays(31).format(DateTimeFormatter.ISO_LOCAL_DATE) + " 10:00:00"
        val expert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-0031", email = "e31@example.com",
            givenNames = "Test", familyNames = "ThirtyOne",
            country = "US", keyword = null, employment = null,
            enrichedAt = enrichedAt
        )
        val enrichment = AuthorEnrichment(hIndex = 12, citationCount = 200, worksCount = 8)

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(listOf(expert)))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val orcids = invocation.arguments[0] as List<String>
            orcids.associateWith { EnrichmentOutcome.Success(enrichment) }
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        DiscoveryMockHelper.stubEsEnrichmentHeadExists(restTemplate)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(org.springframework.http.HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = svc.enrichExistingExperts()

        assertEquals(1, result.enriched)
        assertEquals(0, result.failed)
        Mockito.verify(openAlex).batchEnrichByOrcids(listOf("0000-0031"))
    }

    @Test
    fun `enrichExistingExperts uses batch API`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val experts = (1..500).map { i ->
            com.weibo.talentintroduction.expert.domain.ExpertProfile(
                orcidId = String.format("0000-%04d", i), email = "e$i@example.com",
                givenNames = "Test", familyNames = "$i",
                country = "US", keyword = null, employment = null
            )
        }
        val enrichment = AuthorEnrichment(hIndex = 10, citationCount = 100, worksCount = 5)

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(experts))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 500L, 500L)
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val orcids = invocation.arguments[0] as List<String>
            orcids.associateWith { EnrichmentOutcome.Success(enrichment) }
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        DiscoveryMockHelper.stubEsEnrichmentHeadExists(restTemplate)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(org.springframework.http.HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = svc.enrichExistingExperts()

        assertEquals(500, result.enriched)
        Mockito.verify(openAlex, Mockito.times(10)).batchEnrichByOrcids(Mockito.anyList())
    }

    @Test
    fun `enrichExistingExperts retries RateLimited batch without counting failures`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val experts = (1..55).map { i ->
            com.weibo.talentintroduction.expert.domain.ExpertProfile(
                orcidId = String.format("0000-%04d", i), email = "e$i@example.com",
                givenNames = "Test", familyNames = "$i",
                country = "US", keyword = null, employment = null
            )
        }
        val enrichment = AuthorEnrichment(hIndex = 10, citationCount = 100, worksCount = 5)
        var batchCalls = 0

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(experts))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 55L, 55L)
        Mockito.doAnswer { invocation ->
            batchCalls++
            @Suppress("UNCHECKED_CAST")
            val orcids = invocation.arguments[0] as List<String>
            if (batchCalls == 1) {
                orcids.associateWith { EnrichmentOutcome.RateLimited(10L) }
            } else {
                orcids.associateWith { EnrichmentOutcome.Success(enrichment) }
            }
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        DiscoveryMockHelper.stubEsEnrichmentHeadExists(restTemplate)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(org.springframework.http.HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = svc.enrichExistingExperts()

        assertEquals(55, result.enriched)
        assertEquals(0, result.failed)
        assertEquals(null, result.failureReasons["RATE_LIMITED"])
        assertEquals(3, batchCalls)
    }

    @Test
    fun `enrichExistingExperts trips circuit breaker only in ABORT mode`() {
        val abortProps = openAlexProperties.copy(
            enrichmentRateLimitMode = "ABORT",
            enrichmentMaxBackoffMs = 1L
        )
        val svc = createService(openAlexProps = abortProps)
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val experts = (1..250).map { i ->
            com.weibo.talentintroduction.expert.domain.ExpertProfile(
                orcidId = String.format("0000-%04d", i), email = "e$i@example.com",
                givenNames = "Test", familyNames = "$i",
                country = "US", keyword = null, employment = null
            )
        }

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(experts))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 250L, 250L)
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val orcids = invocation.arguments[0] as List<String>
            orcids.associateWith { EnrichmentOutcome.RateLimited(null) }
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())

        val result = svc.enrichExistingExperts()

        assertEquals(1, result.failureReasons["CIRCUIT_BREAKER"])
        assertEquals(null, result.failureReasons["RATE_LIMITED"])
        assertEquals(0, result.enriched)
        assertEquals(0, result.failed)
        assertEquals("FAILED", result.taskFinalStatus)
        ScrollExpertsMockHelper.verifyEnrichmentProgressContainsStatus(progressStore, "FAILED")
        Mockito.verify(openAlex, Mockito.times(5)).batchEnrichByOrcids(Mockito.anyList())
    }

    @Test
    fun `enrichExistingExperts WAIT mode does not trip circuit breaker after many rate limits`() {
        val svc = createService(openAlexProps = openAlexProperties.copy(enrichmentMaxBackoffMs = 10L))
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-0001", email = "e1@example.com",
            givenNames = "Test", familyNames = "One",
            country = "US", keyword = null, employment = null
        )
        val enrichment = AuthorEnrichment(hIndex = 10, citationCount = 100, worksCount = 5)
        var batchCalls = 0

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(listOf(expert)))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doAnswer {
            batchCalls++
            if (batchCalls <= 10) {
                mapOf("0000-0001" to EnrichmentOutcome.RateLimited(1L))
            } else {
                mapOf("0000-0001" to EnrichmentOutcome.Success(enrichment))
            }
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        DiscoveryMockHelper.stubEsEnrichmentHeadExists(restTemplate)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(org.springframework.http.HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = svc.enrichExistingExperts()

        assertEquals(null, result.failureReasons["CIRCUIT_BREAKER"])
        assertEquals(1, result.enriched)
        assertEquals(11, batchCalls)
    }

    @Test
    fun `enrichExistingExperts cancels during rate limit backoff`() {
        val svc = createService(openAlexProps = openAlexProperties.copy(enrichmentMaxBackoffMs = 5_000L))
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-0001", email = "e1@example.com",
            givenNames = "Test", familyNames = "One",
            country = "US", keyword = null, employment = null
        )
        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(listOf(expert)))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doReturn(mapOf("0000-0001" to EnrichmentOutcome.RateLimited(null)))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        ScrollExpertsMockHelper.stubEnrichmentCancelOnBackoffMessage(progressStore)

        val result = svc.enrichExistingExperts()

        assertTrue(result.wasCancelled)
        assertEquals(0, result.enriched)
    }

    @Test
    fun `enrichExistingExperts uses frozen cutoff and excludes EMAIL- in filters`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val experts = listOf(
            com.weibo.talentintroduction.expert.domain.ExpertProfile(
                orcidId = "0000-0001", email = "e1@example.com",
                givenNames = "Test", familyNames = "One",
                country = "US", keyword = null, employment = null
            ),
            com.weibo.talentintroduction.expert.domain.ExpertProfile(
                orcidId = "0000-0002", email = "e2@example.com",
                givenNames = "Test", familyNames = "Two",
                country = "US", keyword = null, employment = null
            )
        )

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(
            expertSearchService,
            listOf(listOf(experts[0]), listOf(experts[1]))
        )
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 2L, 2L)
        Mockito.doReturn(emptyMap<String, EnrichmentOutcome>())
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())

        svc.enrichExistingExperts()

        ScrollExpertsMockHelper.verifyCountExpertsFilterContains(expertSearchService, "EMAIL-")
    }

    @Test
    fun `enrichExistingExperts INSTITUTION_TYPE_BACKFILL scope uses backfill filter excluding EMAIL- (I5a2-1 I5a2-3)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 0L, 0L)
        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, emptyList())

        val result = svc.enrichExistingExperts(EnrichmentScope.INSTITUTION_TYPE_BACKFILL)

        assertEquals(0, result.enriched)
        assertEquals(0, result.failed)
        val filters = ScrollExpertsMockHelper.captureNonEmptyCountExpertsFilters(expertSearchService)
        @Suppress("UNCHECKED_CAST")
        val bool = filters[0]["bool"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val must = bool["must"] as List<Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val mustNot = bool["must_not"] as List<Map<String, Any>>
        fun existsField(clause: Map<String, Any>): String? =
            (clause["exists"] as? Map<*, *>)?.get("field") as? String
        assertTrue(must.any { existsField(it) == "enrichedAt" }, "backfill must must include exists enrichedAt")
        assertTrue(mustNot.any { existsField(it) == "institutionType" }, "backfill must_not must include exists institutionType")
        assertTrue(filters.toString().contains("EMAIL-"), "backfill filter must exclude EMAIL- prefix")
    }

    @Test
    fun `enrichExistingExperts LAST_PUBLICATION_YEAR_BACKFILL scope uses backfill filter excluding EMAIL- (I1-5)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 0L, 0L)
        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, emptyList())

        val result = svc.enrichExistingExperts(EnrichmentScope.LAST_PUBLICATION_YEAR_BACKFILL)

        assertEquals(0, result.enriched)
        assertEquals(0, result.failed)
        val filters = ScrollExpertsMockHelper.captureNonEmptyCountExpertsFilters(expertSearchService)
        @Suppress("UNCHECKED_CAST")
        val bool = filters[0]["bool"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val must = bool["must"] as List<Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val mustNot = bool["must_not"] as List<Map<String, Any>>
        fun existsField(clause: Map<String, Any>): String? =
            (clause["exists"] as? Map<*, *>)?.get("field") as? String
        assertTrue(must.any { existsField(it) == "enrichedAt" }, "backfill must must include exists enrichedAt")
        assertTrue(mustNot.any { existsField(it) == "lastPublicationYear" }, "backfill must_not must include exists lastPublicationYear")
        assertTrue(filters.toString().contains("EMAIL-"), "backfill filter must exclude EMAIL- prefix")
    }

    @Test
    fun `getEnrichmentStats reports institutionTypePending backfill count (A5-1)`() {
        val svc = createService()
        ScrollExpertsMockHelper.stubEnrichmentStatsCounts(expertSearchService, 10L, 3L, 6L)

        val stats = svc.getEnrichmentStats()

        assertEquals(3L, stats.pending)
        assertEquals(6L, stats.enrichedLast30d)
        assertEquals(10L, stats.total)
        assertEquals(3L, stats.institutionTypePending)
        // I1-5 口径与 institutionType 补采同款（无 gte 的 exists 过滤器），同样落到 pending 桩值。
        assertEquals(3L, stats.lastPublicationYearPending)
    }

    @Test
    fun `enrichExistingExperts resumes from where it left off on next run`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 100L, 0L)
        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, emptyList())

        val result = svc.enrichExistingExperts()

        assertEquals(0, result.enriched)
        assertEquals(0, result.failed)
        Mockito.verify(openAlex, Mockito.never()).batchEnrichByOrcids(Mockito.anyList())
    }

    @Test
    fun `enrichExistingExperts writes disciplineCategory STEM to ES update doc`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-STEM", email = "stem@example.com",
            givenNames = "Test", familyNames = "Stem",
            country = "US", keyword = null, employment = null
        )
        val enrichment = AuthorEnrichment(
            hIndex = 10, citationCount = 100, worksCount = 5,
            disciplineCategory = "STEM"
        )

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(listOf(expert)))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doReturn(mapOf("0000-STEM" to EnrichmentOutcome.Success(enrichment)))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        DiscoveryMockHelper.stubEsEnrichmentHeadExists(restTemplate)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        svc.enrichExistingExperts()

        @Suppress("UNCHECKED_CAST")
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<*>>
        Mockito.verify(restTemplate, Mockito.atLeastOnce()).exchange(
            Mockito.contains("/_update/"),
            Mockito.eq(HttpMethod.POST),
            entityCaptor.capture(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        @Suppress("UNCHECKED_CAST")
        val doc = (entityCaptor.value.body as Map<*, *>)["doc"] as Map<*, *>
        assertEquals("STEM", doc["disciplineCategory"])
    }

    @Test
    fun `enrichExistingExperts omits disciplineCategory key when null`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-NULL", email = "null@example.com",
            givenNames = "Test", familyNames = "Null",
            country = "US", keyword = null, employment = null
        )
        val enrichment = AuthorEnrichment(
            hIndex = 10, citationCount = 100, worksCount = 5,
            disciplineCategory = null
        )

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(listOf(expert)))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doReturn(mapOf("0000-NULL" to EnrichmentOutcome.Success(enrichment)))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        DiscoveryMockHelper.stubEsEnrichmentHeadExists(restTemplate)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        svc.enrichExistingExperts()

        @Suppress("UNCHECKED_CAST")
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<*>>
        Mockito.verify(restTemplate, Mockito.atLeastOnce()).exchange(
            Mockito.contains("/_update/"),
            Mockito.eq(HttpMethod.POST),
            entityCaptor.capture(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        @Suppress("UNCHECKED_CAST")
        val doc = (entityCaptor.value.body as Map<*, *>)["doc"] as Map<*, *>
        assertFalse(doc.containsKey("disciplineCategory"))
    }

    @Test
    fun `enrichExistingExperts omits institutionType key when null (I5a-3)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-NULL", email = "null@example.com",
            givenNames = "Test", familyNames = "Null",
            country = "US", keyword = null, employment = null
        )
        val enrichment = AuthorEnrichment(
            hIndex = 10, citationCount = 100, worksCount = 5,
            disciplineCategory = null
        )

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(listOf(expert)))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doReturn(mapOf("0000-NULL" to EnrichmentOutcome.Success(enrichment)))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        DiscoveryMockHelper.stubEsEnrichmentHeadExists(restTemplate)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        svc.enrichExistingExperts()

        @Suppress("UNCHECKED_CAST")
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<*>>
        Mockito.verify(restTemplate, Mockito.atLeastOnce()).exchange(
            Mockito.contains("/_update/"),
            Mockito.eq(HttpMethod.POST),
            entityCaptor.capture(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        @Suppress("UNCHECKED_CAST")
        val doc = (entityCaptor.value.body as Map<*, *>)["doc"] as Map<*, *>
        assertFalse(doc.containsKey("institutionType"))
    }

    @Test
    fun `enrichExistingExperts writes classification from enriched academic fields`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        val expert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-CLASSIFY", email = "classify@example.com",
            givenNames = "Test", familyNames = "Classify",
            country = "US", keyword = null, employment = null
        )
        val enrichment = AuthorEnrichment(
            hIndex = 24, citationCount = 100, worksCount = 25,
            topics = listOf("Quantum computing"),
            recentWorkTitles = listOf("Novel quantum algorithms"),
            lastPublicationYear = 2026
        )

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(listOf(expert)))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doReturn(mapOf("0000-CLASSIFY" to EnrichmentOutcome.Success(enrichment)))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        DiscoveryMockHelper.stubEsEnrichmentHeadExists(restTemplate)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        svc.enrichExistingExperts()

        @Suppress("UNCHECKED_CAST")
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<*>>
        Mockito.verify(restTemplate, Mockito.atLeastOnce()).exchange(
            Mockito.contains("/_update/"), Mockito.eq(HttpMethod.POST), entityCaptor.capture(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        @Suppress("UNCHECKED_CAST")
        val doc = (entityCaptor.value.body as Map<*, *>)["doc"] as Map<*, *>
        val classification = doc["expertClassification"]
            as com.weibo.talentintroduction.expert.domain.ExpertClassification
        assertEquals(com.weibo.talentintroduction.expert.domain.ExpertType.ACADEMIC_RND, classification.type)
    }

    @Test
    fun `enrichExistingExperts writes institutionType unconditionally overwriting prior value (I5a-8)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-COMP", email = "comp@example.com",
            givenNames = "Test", familyNames = "Comp",
            country = "US", keyword = null, employment = null,
            institutionType = "education"
        )
        // I5a-8: 文档已有 institutionType=education，enrichment 返回 company → _update + doc 局部覆盖为 company。
        // 单元层断言：doc 体无条件携带 institutionType=company（无「已有值则跳过」保护），覆盖由 ES _update 语义完成。
        val enrichment = AuthorEnrichment(
            hIndex = 10, citationCount = 100, worksCount = 5,
            disciplineCategory = "STEM", institutionType = "company"
        )

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(listOf(expert)))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doReturn(mapOf("0000-COMP" to EnrichmentOutcome.Success(enrichment)))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        DiscoveryMockHelper.stubEsEnrichmentHeadExists(restTemplate)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        svc.enrichExistingExperts()

        @Suppress("UNCHECKED_CAST")
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<*>>
        Mockito.verify(restTemplate, Mockito.atLeastOnce()).exchange(
            Mockito.contains("/_update/"),
            Mockito.eq(HttpMethod.POST),
            entityCaptor.capture(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        @Suppress("UNCHECKED_CAST")
        val doc = (entityCaptor.value.body as Map<*, *>)["doc"] as Map<*, *>
        assertEquals("company", doc["institutionType"])
    }

    @Test
    fun `enrichExistingExperts writes lastPublicationYear unconditionally overwriting prior value (I1-4)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-YEAR", email = "year@example.com",
            givenNames = "Test", familyNames = "Year",
            country = "US", keyword = null, employment = null,
            lastPublicationYear = 2015
        )
        // I1-4: 文档已有 lastPublicationYear=2015（发现时由 paper.pubYear 写入），
        // enrichment 返回 2026 → _update + doc 局部覆盖为 2026。
        // 单元层断言：doc 体无条件携带 lastPublicationYear=2026（无「已有值则跳过」保护），覆盖由 ES _update 语义完成。
        val enrichment = AuthorEnrichment(
            hIndex = 10, citationCount = 100, worksCount = 5,
            disciplineCategory = "STEM", lastPublicationYear = 2026
        )

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(listOf(expert)))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doReturn(mapOf("0000-YEAR" to EnrichmentOutcome.Success(enrichment)))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        DiscoveryMockHelper.stubEsEnrichmentHeadExists(restTemplate)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        svc.enrichExistingExperts()

        @Suppress("UNCHECKED_CAST")
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<*>>
        Mockito.verify(restTemplate, Mockito.atLeastOnce()).exchange(
            Mockito.contains("/_update/"),
            Mockito.eq(HttpMethod.POST),
            entityCaptor.capture(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        @Suppress("UNCHECKED_CAST")
        val doc = (entityCaptor.value.body as Map<*, *>)["doc"] as Map<*, *>
        assertEquals(2026, doc["lastPublicationYear"])
    }

    @Test
    fun `enrichExistingExperts omits lastPublicationYear key when null (I1-3)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = com.weibo.talentintroduction.expert.domain.ExpertProfile(
            orcidId = "0000-NOYEAR", email = "noyear@example.com",
            givenNames = "Test", familyNames = "NoYear",
            country = "US", keyword = null, employment = null,
            lastPublicationYear = 2015
        )
        // I1-3: 派生值为 null（无 counts_by_year）时 doc 不写入该键，避免覆盖发现时的真实值。
        val enrichment = AuthorEnrichment(
            hIndex = 10, citationCount = 100, worksCount = 5,
            disciplineCategory = "STEM", lastPublicationYear = null
        )

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(listOf(expert)))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doReturn(mapOf("0000-NOYEAR" to EnrichmentOutcome.Success(enrichment)))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList())
        DiscoveryMockHelper.stubEsEnrichmentHeadExists(restTemplate)
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(),
                Mockito.eq(HttpMethod.POST),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        svc.enrichExistingExperts()

        @Suppress("UNCHECKED_CAST")
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<*>>
        Mockito.verify(restTemplate, Mockito.atLeastOnce()).exchange(
            Mockito.contains("/_update/"),
            Mockito.eq(HttpMethod.POST),
            entityCaptor.capture(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        @Suppress("UNCHECKED_CAST")
        val doc = (entityCaptor.value.body as Map<*, *>)["doc"] as Map<*, *>
        assertFalse(doc.containsKey("lastPublicationYear"))
    }

    @Test
    fun `getEnrichmentStats filter should includes disciplineCategory backfill clause`() {
        val svc = createService()
        ScrollExpertsMockHelper.stubEnrichmentStatsCounts(expertSearchService, 10L, 3L, 6L)

        val stats = svc.getEnrichmentStats()

        assertEquals(6L, stats.enrichedLast30d)

        val filters = ScrollExpertsMockHelper.captureNonEmptyCountExpertsFilters(expertSearchService)
        @Suppress("UNCHECKED_CAST")
        val should = (filters[0]["bool"] as Map<String, Any>)["should"] as List<*>
        assertEquals(3, should.size)
        @Suppress("UNCHECKED_CAST")
        val backfillBool = (should[2] as Map<String, Any>)["bool"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val must = backfillBool["must"] as List<Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val mustNot = backfillBool["must_not"] as List<Map<String, Any>>
        fun existsField(clause: Map<String, Any>): String? =
            (clause["exists"] as? Map<*, *>)?.get("field") as? String
        assertTrue(must.any { existsField(it) == "enrichedAt" }, "must must include exists enrichedAt")
        assertTrue(must.any { existsField(it) == "researchFields" }, "must must include exists researchFields")
        assertTrue(mustNot.any { existsField(it) == "disciplineCategory" }, "must_not must include exists disciplineCategory")
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
        // P1-1: 数据源返回 3 篇且 maxPapersPerRun=1，断言保存的检查点不等于 batch.nextCursor
        val limitedProperties = ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 1, maxAuthorsPerRun = 200)
        val svc = createService(limitedProperties)
        val papers = (1..3).map { paper("PMC$it", "Paper $it") }
        val batchNextCursor = "page2-cursor"
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, batchNextCursor, 3))
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_EMAIL_IN_FULLTEXT")

        val (result, saved) = runAndCapture(svc)

        val decoded = decodedCheckpoint(savedCheckpoints(saved, "EUROPE_PMC").last())
        assertNotEquals(batchNextCursor, decoded.cursor,
            "Partial batch must NOT save nextCursor='$batchNextCursor'; " +
            "saved cursor was '${decoded.cursor}' which would skip unprocessed papers")
        assertNull(decoded.cursor, "部分页必须保留进入该页的 cursor（本用例进入该页时为 null）")
        assertEquals(1, result.stats.pendingSources, "部分页意味着仍有可续跑工作")
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

        val (result, saved) = runAndCapture(svc)

        val decoded = decodedCheckpoint(savedCheckpoints(saved, "EUROPE_PMC").last())
        assertNotEquals(batchNextCursor, decoded.cursor,
            "When sourceLimit causes partial batch, cursor must not advance to '$batchNextCursor'")
        assertNull(decoded.cursor)
        assertEquals(1, result.stats.pendingSources)
    }

    @Test
    fun `first page TLS failure keeps the entering cursor`() {
        // V-1: 原 cursor=C7，第一请求 TLS 失败后仍保存 C7
        val svc = createService()
        val criteria = PaperSearchCriteria()
        stubStoredCheckpoint("EUROPE_PMC", "C7", criteria)
        DiscoveryMockHelper.stubSearchPapersThrows(europePmc, ResourceAccessException("TLS handshake failed"))

        val (result, saved) = runAndCapture(svc, criteria)

        val decoded = storedCheckpointFor("EUROPE_PMC", criteria)
        assertEquals("C7", decoded.cursor, "首请求失败必须保留进入该页的 cursor")
        assertFalse(decoded.exhausted, "TLS 失败绝不等于穷尽")
        assertEquals(0L, storedRowFor("EUROPE_PMC", criteria).papersProcessedTotal, "首请求失败没有消费任何页")
        assertEquals(1, savedCheckpoints(saved, "EUROPE_PMC").size, "首请求就失败，只有运行结束那一次落盘")
        assertEquals(1, result.stats.sourceFailures, "I-4: 终止性源错误必须计入 failure_count")
        assertTrue(result.taskFailureCount > 0, "V-3: 故障下 failure_count 非 0")
        assertEquals("FAILED", result.taskFinalStatus, "V-3: 唯一启用来源全失败即 FAILED")
        assertEquals(DiscoveryStopReason.SEARCH_FAILED, result.stats.bySource["EUROPE_PMC"]?.stopReason)
    }

    @Test
    fun `second page failure resumes at the second page entry and persists at the page boundary`() {
        // V-1: 第二页失败只回到第二页；I-1: 完整消费第一页后立即落盘
        val svc = createService()
        val criteria = PaperSearchCriteria()
        stubStoredCheckpoint("EUROPE_PMC", "C1", criteria)
        val page1 = (1..2).map { paper("PMC$it", "Paper $it") }
        Mockito.doReturn(PaperSearchResult(page1, "C2", 4))
            .doThrow(ResourceAccessException("TLS handshake failed"))
            .`when`(europePmc).searchPapers(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_EMAIL_IN_FULLTEXT")

        val (result, saved) = runAndCapture(svc, criteria)

        val entries = savedCheckpoints(saved, "EUROPE_PMC")
        assertTrue(entries.size >= 2,
            "完整消费第一页后必须立即落盘，再进入第二页请求；实际落盘 ${entries.size} 次")
        val firstEntry = decodedCheckpoint(entries.first())
        assertEquals("C2", firstEntry.cursor, "第一页消费完立刻把第二页入口写进检查点")
        assertFalse(firstEntry.exhausted)
        val decoded = storedCheckpointFor("EUROPE_PMC", criteria)
        assertEquals("C2", decoded.cursor, "第二页失败后必须停在第二页入口，不能清空游标")
        assertFalse(decoded.exhausted)
        assertEquals(2L, storedRowFor("EUROPE_PMC", criteria).papersProcessedTotal, "只累计第一页消费的 2 篇")
        assertEquals(2, result.stats.totalPapers)
        assertEquals(1, result.stats.sourceFailures)
    }

    @Test
    fun `empty page with next cursor keeps paging`() {
        // V-2: 过滤后空页且 nextCursor!=null 必须继续翻页
        val svc = createService()
        installInMemoryCursorStore()
        DiscoveryMockHelper.stubSearchPapersSequence(
            europePmc,
            PaperSearchResult(emptyList(), "E2", 0),
            PaperSearchResult((1..2).map { paper("PMC$it", "Paper $it") }, null, 2)
        )
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_EMAIL_IN_FULLTEXT")

        val result = svc.discover(PaperSearchCriteria(), "TEST")

        assertEquals(2, result.stats.totalPapers, "空页带 nextCursor 时必须继续翻页到下一批记录")
        val decoded = storedCheckpointFor("EUROPE_PMC")
        assertTrue(decoded.exhausted, "翻到底才记为 EXHAUSTED")
        assertNull(decoded.cursor)
        assertEquals(0, result.stats.pendingSources)
    }

    @Test
    fun `empty result without cursor is exhausted not a failure`() {
        // V-2/V-3: 无记录且无 cursor 才判穷尽；真实空结果 = SUCCESS
        val svc = createService()
        installInMemoryCursorStore()
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(emptyList(), null, 0))

        val result = svc.discover(PaperSearchCriteria(), "TEST")

        val decoded = storedCheckpointFor("EUROPE_PMC")
        assertTrue(decoded.exhausted)
        assertNull(decoded.cursor)
        assertEquals(0, result.stats.sourceFailures)
        assertEquals(0, result.taskFailureCount)
        assertEquals("SUCCESS", result.taskFinalStatus)
    }

    @Test
    fun `different keywords persist to different checkpoint keys`() {
        // V-2 / A-3: 不同关键词不共用检查点
        val svc = createService()
        installInMemoryCursorStore()
        val criteriaA = PaperSearchCriteria(keywords = listOf("keyword-A"))
        val criteriaB = PaperSearchCriteria(keywords = listOf("keyword-B"))
        val keyA = DiscoveryCheckpointCodec.sourceKey("EUROPE_PMC", criteriaA)
        val keyB = DiscoveryCheckpointCodec.sourceKey("EUROPE_PMC", criteriaB)
        assertNotEquals(keyA, keyB)
        assertTrue(keyA.length <= 50 && keyB.length <= 50, "I-2: key 必须放下 VARCHAR(50)")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(emptyList(), null, 0))

        svc.discover(criteriaA, "TEST")
        val stateA = storedCheckpoint(keyA)
        svc.discover(criteriaB, "TEST")

        assertTrue(storedCheckpoints.containsKey(keyA), "关键词 A 的检查点必须写在自己的 key 上")
        assertTrue(storedCheckpoints.containsKey(keyB), "关键词 B 的检查点必须写在自己的 key 上")
        assertEquals(stateA, storedCheckpoint(keyA), "运行 B 不得改写 A 的检查点")
    }

    @Test
    fun `legacy plain source name rows stay untouched and are never adopted`() {
        // I-2: 旧条件写下的历史行只作备份，不静默挪用
        val svc = createService()
        installInMemoryCursorStore()
        storedCheckpoints["EUROPE_PMC"] = DiscoverySourceCursor(
            id = 9L, sourceName = "EUROPE_PMC", cursorValue = "LEGACY-C7"
        )
        val seen = stubAndRecordRequests(europePmc, PaperSearchResult(emptyList(), null, 0))

        svc.discover(PaperSearchCriteria(), "TEST")

        assertTrue(seen.isNotEmpty(), "来源必须被请求过")
        assertNull(seen.first().cursor, "旧 source_name 行的游标不得被挪用")
        assertEquals("LEGACY-C7", storedCheckpoints["EUROPE_PMC"]?.cursorValue, "旧行不得被改写，只作备份")
        assertTrue(
            storedCheckpoints.keys.any { it.startsWith("EUROPE_PMC:v2:") },
            "本次运行的检查点必须写在自己的 v2 key 上"
        )
    }

    @Test
    fun `v2 key holding a legacy raw cursor value is not adopted`() {
        // I-2: 无法解码的值一律按「无检查点」处理
        val svc = createService()
        val criteria = PaperSearchCriteria()
        val key = DiscoveryCheckpointCodec.sourceKey("EUROPE_PMC", criteria)
        installInMemoryCursorStore()
        storedCheckpoints[key] = DiscoverySourceCursor(id = 3L, sourceName = key, cursorValue = "C7")
        val seen = stubAndRecordRequests(europePmc, PaperSearchResult(emptyList(), null, 0))

        svc.discover(criteria, "TEST")

        assertTrue(seen.isNotEmpty(), "来源必须被请求过")
        assertNull(seen.first().cursor, "裸游标值不是 v2 envelope，不得作为检查点使用")
    }

    @Test
    fun `V-3 all attempted sources failing yields FAILED with non-zero source failures`() {
        val svc = createService()
        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 25)
        Mockito.doThrow(ResourceAccessException("TLS handshake failed"))
            .`when`(orcid).searchOrcidRecords(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
        DiscoveryMockHelper.stubSearchPapersThrows(europePmc, ResourceAccessException("TLS handshake failed"))

        val result = svc.discover(PaperSearchCriteria(), "TEST")

        assertEquals(2, result.stats.attemptedSources)
        assertEquals(2, result.stats.failedSources)
        assertEquals(2, result.stats.sourceFailures)
        assertEquals(2, result.taskFailureCount, "failure_count 增加终止性源错误数量")
        assertEquals("FAILED", result.taskFinalStatus)
    }

    @Test
    fun `V-3 one failed source plus one healthy source yields PARTIAL_SUCCESS`() {
        val svc = createService()
        DiscoveryMockHelper.stubSearchPapersThrows(europePmc, ResourceAccessException("TLS handshake failed"))

        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        DiscoveryMockHelper.stubOrcidRecordToAuthorEmails(orcid)
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 25)
        val records = (1..2).map {
            OrcidDataSource.OrcidRecord(
                orcidId = "0000-000$it", givenNames = "Test", familyNames = "$it",
                emails = listOf("test$it@example.com"), institutionName = "Univ", country = null
            )
        }
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, records)
        for (i in 1..2) {
            DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test$i@example.com", EmailValidationResult(2, true))
        }
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val result = svc.discover(PaperSearchCriteria(), "TEST")

        assertEquals(2, result.stats.attemptedSources)
        assertEquals(1, result.stats.failedSources)
        assertEquals(2, result.stats.indexed, "健康来源仍然产出专家")
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
    }

    @Test
    fun `budget deferred stop keeps the entering cursor and is not a search failure`() {
        // c1 契约: 额度延期是配额停止原因，不是搜索失败，也不清空进度
        val svc = createService()
        val criteria = PaperSearchCriteria()
        stubStoredCheckpoint("OPENALEX", "C7", criteria)
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        Mockito.doReturn("OPENALEX").`when`(openAlex).sourceName
        Mockito.doReturn("FULLTEXT_XML").`when`(openAlex).emailExtractionMethod
        Mockito.doReturn(100).`when`(openAlex).maxPapersPerSource
        Mockito.doThrow(OpenAlexBudgetDeferredException(Instant.parse("2026-09-22T00:00:00Z")))
            .`when`(openAlex).searchPapers(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())

        val (result, _) = runAndCapture(svc, criteria)

        val decoded = storedCheckpointFor("OPENALEX", criteria)
        assertEquals("C7", decoded.cursor, "额度延期必须保留进入该页的 cursor")
        assertFalse(decoded.exhausted, "额度延期绝不置 exhausted")
        assertEquals(0, result.stats.sourceFailures, "额度延期不计入 failure_count")
        assertEquals(1, result.stats.pendingSources)
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
        assertEquals(DiscoveryStopReason.BUDGET_DEFERRED, result.stats.bySource["OPENALEX"]?.stopReason)
    }

    @Test
    fun `page with failed RAW persistence keeps the entering cursor for replay`() {
        // I-1: 未完成 RAW 持久化的页不得推进检查点
        val svc = createService()
        val criteria = PaperSearchCriteria()
        stubStoredCheckpoint("EUROPE_PMC", "C1", criteria)
        val page = (1..2).map { paper("PMC$it", "Paper $it") }
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(page, "C2", 4))
        DiscoveryMockHelper.stubExtractAuthorEmails(
            europePmc, listOf(AuthorEmail("rawfail@example.com", "A", "B", false, null, "0000-0009"))
        )
        DiscoveryMockHelper.stubValidateEmail(
            emailValidationService, "rawfail@example.com", EmailValidationResult(2, true)
        )
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, false)

        val (result, _) = runAndCapture(svc, criteria)

        val decoded = storedCheckpointFor("EUROPE_PMC", criteria)
        assertEquals("C1", decoded.cursor, "页内 RAW 写入失败时不得把检查点推进到 nextCursor")
        assertFalse(decoded.exhausted)
        assertEquals(2, result.stats.rawWriteFailed)
        assertEquals(1, result.stats.pendingSources)
        assertEquals(DiscoveryStopReason.RAW_WRITE_INCOMPLETE, result.stats.bySource["EUROPE_PMC"]?.stopReason)
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

    @Test
    fun `parallel fetch produces same stats as serial run`() {
        fun runWithConcurrency(concurrency: Int): com.weibo.talentintroduction.discovery.domain.DiscoveryStats {
            setUp()
            val paperCount = 30
            val papers = (1..paperCount).map { paper("PMC$it", "Paper $it") }
            val props = ExpertDiscoveryProperties(
                enabled = true,
                maxPapersPerRun = paperCount,
                maxAuthorsPerRun = paperCount,
                includeRawScan = false,
                fetchConcurrency = concurrency
            )
            val executor: Executor = if (concurrency <= 1) Executor { it.run() } else Executors.newFixedThreadPool(concurrency)
            val svc = createService(props, executor)

            DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, null, paperCount.toLong()))
            val outcomes = papers.mapIndexed { index, _ ->
                if (index % 3 == 0) {
                    EmailExtractionOutcome(emptyList(), "FULLTEXT_XML", "NO_EMAIL_IN_FULLTEXT")
                } else {
                    EmailExtractionOutcome(
                        listOf(AuthorEmail("author$index@example.com", "A", "B$index", false, null, null)),
                        "FULLTEXT_XML",
                        null
                    )
                }
            }.toTypedArray()
            DiscoveryMockHelper.stubExtractAuthorEmailsSequence(europePmc, *outcomes)
            Mockito.doAnswer { invocation ->
                val email = invocation.arguments[0] as String
                EmailValidationResult(2, true)
            }.`when`(emailValidationService).validate(Mockito.anyString())
            DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
            DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
            DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
            DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

            return svc.discover(PaperSearchCriteria(), "TEST", includeRawScan = false).stats
        }

        val serial = runWithConcurrency(1)
        val parallel = runWithConcurrency(4)

        assertEquals(serial.totalPapers, parallel.totalPapers)
        assertEquals(serial.indexed, parallel.indexed)
        assertEquals(serial.promoted, parallel.promoted)
        assertEquals(serial.filtered, parallel.filtered)
        assertEquals(serial.duplicates, parallel.duplicates)
        assertEquals(serial.emailRejected, parallel.emailRejected)
        assertEquals(serial.noEmailPapers, parallel.noEmailPapers)
        val serialSource = serial.bySource["EUROPE_PMC"]!!
        val parallelSource = parallel.bySource["EUROPE_PMC"]!!
        assertEquals(serialSource.emailsValid, parallelSource.emailsValid)
        assertEquals(serialSource.authorsExtracted, parallelSource.authorsExtracted)
        assertEquals(serialSource.fulltextAttempted, parallelSource.fulltextAttempted)
    }

    @Test
    fun `managed executor accepts batch larger than fetchConcurrency`() {
        val paperCount = 12
        val fetchConcurrency = 4
        val props = ExpertDiscoveryProperties(
            enabled = true,
            maxPapersPerRun = paperCount,
            maxAuthorsPerRun = paperCount,
            includeRawScan = false,
            fetchConcurrency = fetchConcurrency
        )
        val managedExecutor = DiscoveryExecutorConfig(props).discoveryFetchExecutor()
        val papers = (1..paperCount).map { paper("PMC$it", "Paper $it") }

        fun runDiscovery(executor: java.util.concurrent.Executor): com.weibo.talentintroduction.discovery.domain.DiscoveryStats {
            setUp()
            val svc = createService(props, executor)
            DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, null, paperCount.toLong()))
            val outcomes = papers.mapIndexed { index, _ ->
                if (index % 2 == 0) {
                    EmailExtractionOutcome(emptyList(), "FULLTEXT_XML", "NO_EMAIL_IN_FULLTEXT")
                } else {
                    EmailExtractionOutcome(
                        listOf(AuthorEmail("author$index@example.com", "A", "B$index", false, null, null)),
                        "FULLTEXT_XML",
                        null
                    )
                }
            }.toTypedArray()
            DiscoveryMockHelper.stubExtractAuthorEmailsSequence(europePmc, *outcomes)
            Mockito.doReturn(EmailValidationResult(2, true))
                .`when`(emailValidationService).validate(Mockito.anyString())
            DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
            DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
            DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
            DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
            return svc.discover(PaperSearchCriteria(), "TEST", includeRawScan = false).stats
        }

        val serial = runDiscovery(Executor { it.run() })
        val managed = runDiscovery(managedExecutor)

        assertEquals(serial.totalPapers, managed.totalPapers)
        assertEquals(serial.indexed, managed.indexed)
        assertEquals(serial.promoted, managed.promoted)
        assertEquals(serial.noEmailPapers, managed.noEmailPapers)
    }

    @Test
    fun `parallel fetch respects maxPapersPerRun and only counts consumed papers`() {
        val batchSize = 5
        val maxPapers = 2
        val papers = (1..batchSize).map { paper("PMC$it", "Paper $it") }
        val props = ExpertDiscoveryProperties(
            enabled = true,
            maxPapersPerRun = maxPapers,
            maxAuthorsPerRun = 100,
            includeRawScan = false,
            fetchConcurrency = 4
        )
        val managedExecutor = DiscoveryExecutorConfig(props).discoveryFetchExecutor()

        fun runDiscovery(executor: Executor): com.weibo.talentintroduction.discovery.domain.DiscoveryStats {
            setUp()
            val svc = createService(props, executor)
            DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, null, batchSize.toLong()))
            DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_EMAIL_IN_FULLTEXT")
            return svc.discover(PaperSearchCriteria(), "TEST", includeRawScan = false).stats
        }

        val serial = runDiscovery(Executor { it.run() })
        val parallel = runDiscovery(managedExecutor)
        val serialSource = serial.bySource["EUROPE_PMC"]!!
        val parallelSource = parallel.bySource["EUROPE_PMC"]!!

        assertEquals(maxPapers, serial.totalPapers)
        assertEquals(serial.totalPapers, parallel.totalPapers)
        assertEquals(serial.indexed, parallel.indexed)
        assertEquals(maxPapers, serialSource.papersSearched)
        assertEquals(serialSource.papersSearched, parallelSource.papersSearched)
        assertEquals(maxPapers, serialSource.fulltextAttempted)
        assertEquals(serialSource.fulltextAttempted, parallelSource.fulltextAttempted)
    }

    @Test
    fun `parallel fetch stops at maxAuthorsPerRun without counting unconsumed papers`() {
        val batchSize = 4
        val maxAuthors = 2
        val papers = (1..batchSize).map { paper("PMC$it", "Paper $it") }
        val props = ExpertDiscoveryProperties(
            enabled = true,
            maxPapersPerRun = 100,
            maxAuthorsPerRun = maxAuthors,
            includeRawScan = false,
            fetchConcurrency = 4
        )
        val managedExecutor = DiscoveryExecutorConfig(props).discoveryFetchExecutor()

        fun runDiscovery(executor: Executor): com.weibo.talentintroduction.discovery.domain.DiscoveryStats {
            setUp()
            val svc = createService(props, executor)
            DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, null, batchSize.toLong()))
            val outcomes = papers.mapIndexed { index, _ ->
                EmailExtractionOutcome(
                    listOf(AuthorEmail("author$index@example.com", "A", "B$index", false, null, null)),
                    "FULLTEXT_XML",
                    null
                )
            }.toTypedArray()
            DiscoveryMockHelper.stubExtractAuthorEmailsSequence(europePmc, *outcomes)
            for (index in 0 until batchSize) {
                DiscoveryMockHelper.stubValidateEmail(
                    emailValidationService,
                    "author$index@example.com",
                    EmailValidationResult(2, true)
                )
            }
            DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
            DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
            DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
            DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
            return svc.discover(PaperSearchCriteria(), "TEST", includeRawScan = false).stats
        }

        val serial = runDiscovery(Executor { it.run() })
        val parallel = runDiscovery(managedExecutor)
        val serialSource = serial.bySource["EUROPE_PMC"]!!
        val parallelSource = parallel.bySource["EUROPE_PMC"]!!

        assertEquals(maxAuthors, serial.indexed)
        assertEquals(serial.indexed, parallel.indexed)
        assertEquals(maxAuthors, serialSource.papersSearched)
        assertEquals(serialSource.papersSearched, parallelSource.papersSearched)
        assertEquals(maxAuthors, serialSource.fulltextAttempted)
        assertEquals(serialSource.fulltextAttempted, parallelSource.fulltextAttempted)
        assertTrue(serialSource.papersSearched < batchSize)
    }
}
