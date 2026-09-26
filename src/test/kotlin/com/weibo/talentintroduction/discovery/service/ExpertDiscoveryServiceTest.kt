package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.CoreProperties
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.OpenAlexBudgetDeferredException
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.config.OrcidProperties
import com.weibo.talentintroduction.config.RequestKind
import com.weibo.talentintroduction.config.DiscoveryExecutorConfig
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.DiscoverySourceCursor
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.ExpertAcademicEnrichmentJob
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.discovery.domain.SourceUnit
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import com.weibo.talentintroduction.expert.domain.EmailValidationResult
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.CandidateEligibilityService
import com.weibo.talentintroduction.expert.service.EmailValidationService
import com.weibo.talentintroduction.expert.service.ExpertIdGenerator
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertClassificationService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.expert.service.ScrollExpertsMockHelper
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.expert.service.PromotionOutcome
import com.weibo.talentintroduction.discovery.repository.DiscoverySourceCursorRepository
import com.weibo.talentintroduction.discovery.repository.ExpertAcademicEnrichmentJobRepository
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class ExpertDiscoveryServiceTest {

    private fun verifiedAuthorEmail(email: String, givenNames: String?, familyNames: String?, isCorresponding: Boolean,
        affiliation: String?, orcidId: String?, institutionType: String? = null, openAlexAuthorId: String? = null): AuthorEmail =
        AuthorEmail(email, givenNames, familyNames, isCorresponding, affiliation, orcidId, institutionType, openAlexAuthorId,
            "JATS_SHA256:" + "a".repeat(64))
    private fun currentExtraction(emails: List<AuthorEmail>, methodUsed: String?, failureReason: String? = null,
        httpRequests: Int = 0, fulltextObtained: Boolean? = null, downloadFailureCategory: String? = null): EmailExtractionOutcome =
        EmailExtractionOutcome(emails, methodUsed, failureReason, httpRequests, fulltextObtained, downloadFailureCategory,
            com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.VERSION)
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
    private lateinit var enrichmentJobService: ExpertAcademicEnrichmentJobService
    private lateinit var enrichmentJobRepository: ExpertAcademicEnrichmentJobRepository
    private val discoveryProperties = ExpertDiscoveryProperties(
        enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 200
    )
    private val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
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

    /** 同上：any(Class) 返回 null，非空 LocalDateTime 参数需要真实值兜底。 */
    private fun anyDateTime(): LocalDateTime = Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()

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
        enrichmentJobService = Mockito.mock(ExpertAcademicEnrichmentJobService::class.java)
        enrichmentJobRepository = Mockito.mock(ExpertAcademicEnrichmentJobRepository::class.java)

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
            props, openAlexProps, objectMapper, progressStore, cursorRepository, enrichmentJobService,
            enrichmentJobRepository, executor, europePmcProps
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

    /**
     * ORCID 双入口 stub（c4）：
     * - 发现循环走分页视图 [OrcidDataSource.searchOrcidPage]：首页返回给定封装，随后返回空页；
     * - 「按 orcid 反查邮箱」仍走记录视图 [OrcidDataSource.searchOrcidRecords]（保持改动前行为）。
     */
    private fun stubOrcid(
        orcid: OrcidDataSource,
        records: List<OrcidDataSource.OrcidRecord>,
        nextCursor: String? = null
    ) {
        DiscoveryMockHelper.stubOrcidSearchRecords(orcid, records)
        Mockito.doReturn(OrcidDataSource.OrcidSearchPage(records, nextCursor, records.size))
            .doReturn(OrcidDataSource.OrcidSearchPage(emptyList(), null, 0))
            .`when`(orcid).searchOrcidPage(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
    }

    /** 真实 CORE 数据源 + 记录每次请求 body 的 RestTemplate（断在请求边界，而非内部调用）。 */
    private class StubCore(vararg responses: String, maxPapersPerSource: Int) {
        val requests = mutableListOf<Map<*, *>>()
        val source: CoreDataSource

        init {
            val mapper = ObjectMapper()
            val template = Mockito.mock(RestTemplate::class.java)
            var index = 0
            Mockito.doAnswer { invocation ->
                val entity = invocation.getArgument<Any>(2) as HttpEntity<*>
                requests.add(entity.body as Map<*, *>)
                val body = responses[minOf(index, responses.size - 1)]
                index++
                ResponseEntity.ok(mapper.readTree(body))
            }.`when`(template).exchange(
                Mockito.anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
            source = CoreDataSource(
                template, CoreProperties(enabled = true, apiKey = "test-key", requestDelayMs = 0,
                    maxPapersPerSource = maxPapersPerSource),
                PlainTextEmailExtractor(), Mockito.mock(PdfEmailExtractor::class.java)
            )
        }
    }

    /** CORE 一页响应：[rawCount] 条结果（无 fullText，不会产出邮箱），[totalHits] 是供应商总命中数。 */
    private fun coreWorksBody(rawCount: Int, totalHits: Long): String {
        val root = objectMapper.createObjectNode()
        root.put("totalHits", totalHits)
        val results = root.putArray("results")
        for (i in 1..rawCount) {
            val node = results.addObject()
            node.put("doi", "10.1234/core.$i")
            node.put("title", "CORE Paper $i")
            node.put("yearPublished", 2020)
        }
        return objectMapper.writeValueAsString(root)
    }

    /** ORCID 一页响应：[rawCount] 条原始记录，只有第一条带公开邮箱（可选）。 */
    private fun orcidPageBody(rawCount: Int, publicEmail: String? = null): String {
        val root = objectMapper.createObjectNode()
        val results = root.putArray("expanded-result")
        for (i in 1..rawCount) {
            val node = results.addObject()
            node.put("orcid-id", "0000-0001-%04d".format(i))
            node.put("given-names", "Test")
            node.put("family-names", "Expert$i")
            val emails = node.putArray("email")
            if (i == 1 && publicEmail != null) emails.add(publicEmail)
            node.putArray("institution-name").add("Test University")
        }
        return objectMapper.writeValueAsString(root)
    }

    private fun urlStart(url: String): String = url.substringAfter("&start=").substringBefore("&")

    private fun urlQuery(url: String): String =
        java.net.URLDecoder.decode(url.substringAfter("?q=").substringBefore("&"), "UTF-8")

    @Test
    fun `discovery rejects unproven authors but admits source evidence without a historical blacklist`() {
        val svc = createService()
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(paper("PMC-BLOCK", "Ownership")), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc, listOf(
            AuthorEmail("guess@example.org", "Guess", "Owner", true, null, null),
            verifiedAuthorEmail("hanlei1974@sina.com", "New", "Name", true, null, null)))
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "hanlei1974@sina.com", EmailValidationResult(3, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        val result = svc.discover(PaperSearchCriteria(), "TEST")
        assertEquals(1, result.stats.indexed)
        assertEquals(1, result.stats.promoted)
        assertEquals(1, result.stats.bySource.getValue("EUROPE_PMC").failureReasons["IDENTITY_UNRESOLVED"])
        assertFalse(result.stats.bySource.getValue("EUROPE_PMC").failureReasons.containsKey("IDENTITY_DELETED_BLOCKED"))
        Mockito.verify(indexWriterService, Mockito.times(1)).indexToRaw(Mockito.anyString(), Mockito.anyMap())
    }

    @Test
    fun `legacy extraction cache is rejected before interpreting old author identities`() {
        val svc = createService()
        val envelope = QueuedItemEnvelope("EUROPE_PMC", "old-key", "PMCID", "PAPER", 1, "{}", 2, true)
        val outcome = svc.consumeQueuedItem(envelope, """{"emails":[],"methodUsed":"FULLTEXT_XML"}""", null)
        assertEquals("IDENTITY_EXTRACTION_VERSION_UNSUPPORTED", outcome.unrecoverableReason)
        Mockito.verify(indexWriterService, Mockito.never()).indexToRaw(Mockito.anyString(), Mockito.anyMap())
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

        // c9：六源基础份额合计 = 100 + 5×10 = 150，run 级目标必须能覆盖它，否则启动校验拒绝。
        val svc = createService(
            ExpertDiscoveryProperties(
                enabled = true, maxPapersPerRun = 1_000, maxAuthorsPerRun = 1_000, includeRawScan = false
            )
        )
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
            listOf(verifiedAuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")))
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
            listOf(verifiedAuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001",
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
        assertEquals(preChangeKeys + setOf("institutionType", "identityVerification"), map.keys)
    }

    @Test
    fun `discover merges the OpenAlex author id into externalIds without dropping the import ids (I-1, I-3)`() {
        // I-1/I-3: 作者 ID 只是 externalIds 的一个子键 —— 主键仍是 ORCID，其他导入 ID 一个都不能丢。
        val svc = createService()
        val p1 = paper("PMC1", "Test Paper")

        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(verifiedAuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001",
                openAlexAuthorId = "A5023888391")))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "john@oxford.ac.uk", EmailValidationResult(3, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val capturedIds = mutableListOf<String>()
        val capturedMaps = mutableListOf<Map<String, Any?>>()
        Mockito.doAnswer { invocation ->
            capturedIds.add(invocation.getArgument(0))
            @Suppress("UNCHECKED_CAST")
            capturedMaps.add(invocation.getArgument(1) as Map<String, Any?>)
            true
        }.`when`(indexWriterService).indexToRaw(Mockito.anyString(), Mockito.anyMap())

        svc.discover(PaperSearchCriteria(), "TEST")

        assertEquals(ExpertIdGenerator.generate(null, "john@oxford.ac.uk"), capturedIds.single(), "新记录用邮箱键；真实ORCID独立保存")
        assertEquals(
            mapOf(
                "pmcId" to "PMC1", "doi" to "10.0/PMC1", "pmid" to "pmid-PMC1",
                "orcid" to "0000-0001", "openAlexAuthorId" to "A5023888391"
            ),
            capturedMaps.single()["externalIds"] as Map<*, *>
        )
    }

    @Test
    fun `discover keeps the EMAIL-* primary key and promotes the author id to CANDIDATE (V-1)`() {
        // V-1: 有作者 ID 无 ORCID 仍存 EMAIL-* 主键；子键随整份文档晋升后仍在。
        val svc = createService()
        val p1 = paper("PMC1", "Test Paper")

        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(verifiedAuthorEmail("no-orcid@oxford.ac.uk", "No", "Orcid", false, "Some Lab", null,
                openAlexAuthorId = "A5086928770")))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "no-orcid@oxford.ac.uk", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val capturedIds = mutableListOf<String>()
        val capturedMaps = mutableListOf<Map<String, Any?>>()
        Mockito.doAnswer { invocation ->
            capturedIds.add(invocation.getArgument(0))
            @Suppress("UNCHECKED_CAST")
            capturedMaps.add(invocation.getArgument(1) as Map<String, Any?>)
            true
        }.`when`(indexWriterService).indexToRaw(Mockito.anyString(), Mockito.anyMap())

        val candidateDocs = mutableListOf<Map<String, Any?>>()
        Mockito.doAnswer { invocation ->
            val entity = invocation.getArgument<HttpEntity<*>>(2)
            @Suppress("UNCHECKED_CAST")
            candidateDocs.add(entity.body as Map<String, Any?>)
            ResponseEntity.ok(objectMapper.createObjectNode())
        }.`when`(restTemplate).exchange(
            Mockito.contains("orcid_info_candidate/_doc/"), Mockito.eq(HttpMethod.PUT), Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )

        val result = svc.discover(PaperSearchCriteria(), "TEST")

        assertEquals(1, result.stats.promoted, "资格通过后必须真的晋升，否则下面的 CANDIDATE 断言会漏掉")
        assertTrue(capturedIds.single().startsWith("EMAIL-"), "无 ORCID 时主键必须是 EMAIL-*")
        val rawIds = capturedMaps.single()["externalIds"] as Map<*, *>
        assertEquals("A5086928770", rawIds["openAlexAuthorId"])
        assertFalse(rawIds.containsKey("orcid"), "没有 ORCID 就不得凭空写一个")

        val candidateIds = candidateDocs.single()["externalIds"] as Map<*, *>
        assertEquals("A5086928770", candidateIds["openAlexAuthorId"], "晋升 CANDIDATE 后子键必须仍在")
    }

    @Test
    fun `discover never stores a non-canonical author id in externalIds (I-1)`() {
        // I-1 的下游契约：externalIds.openAlexAuthorId 只能是 A+数字；其他形状一律不写。
        val svc = createService()
        val p1 = paper("PMC1", "Test Paper")

        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(verifiedAuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", null,
                openAlexAuthorId = "https://openalex.org/W1234567")))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "john@oxford.ac.uk", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)

        val capturedMaps = mutableListOf<Map<String, Any?>>()
        Mockito.doAnswer { invocation ->
            capturedMaps.add(invocation.getArgument<Any>(1) as Map<String, Any?>)
            true
        }.`when`(indexWriterService).indexToRaw(Mockito.anyString(), Mockito.anyMap())

        svc.discover(PaperSearchCriteria(), "TEST")

        val externalIds = capturedMaps.single()["externalIds"] as Map<*, *>
        assertFalse(externalIds.containsKey("openAlexAuthorId"), "非 A+数字 的作者 ID 不得入库")
        assertEquals("PMC1", externalIds["pmcId"])
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
            listOf(verifiedAuthorEmail("dup@example.com", "A", "B", false, null, null)))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "dup@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 1)

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        assertEquals(1, result.stats.duplicates)
        assertEquals(0, result.stats.indexed)
    }

    @Test
    fun `discover respects maxPapersPerRun limit`() {
        // c9（I-1）: run 级目标（200）与单页（100）是两件事 —— 目标覆盖两源各一页，
        // 而不是「只处理 100 篇」。到界按全局 cap 命名，不算来源失败。
        val limitedProperties = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 200, maxAuthorsPerRun = 2_000, includeRawScan = false
        )
        val svc = createService(limitedProperties)
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        stubSource(openAlex, "OPENALEX")
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        val epmcRequests = stubPaperSourceRuns(europePmc, 500, pageOf(100, "NEXT-EPMC"))
        stubPaperSourceRuns(openAlex, 500, pageOf(100, "NEXT-OPENALEX", offset = 100))

        val result = svc.discover(PaperSearchCriteria(), "TEST", includeRawScan = false)

        assertEquals(200, result.stats.totalPapers)
        assertEquals(100, result.stats.bySource["EUROPE_PMC"]?.papersSearched)
        assertEquals(100, result.stats.bySource["OPENALEX"]?.papersSearched)
        assertEquals(1, epmcRequests.size, "全局 cap 到界即停，不再发第二次请求")
        assertEquals(DiscoveryStopReason.GLOBAL_PAPER_LIMIT, result.stats.bySource["EUROPE_PMC"]?.stopReason)
        assertEquals(2, result.stats.pendingSources, "全局 cap 停止仍有可续跑工作")
        assertEquals(200, result.stats.noEmailPapers)
    }

    @Test
    fun `discover rejects invalid email`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmails(europePmc,
            listOf(verifiedAuthorEmail("bad-email", "X", "Y", false, null, null)))
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
            listOf(verifiedAuthorEmail("filtered@example.com", "A", "B", false, "China", null)))
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
            listOf(verifiedAuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")))
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
            listOf(verifiedAuthorEmail("john@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")))
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
            listOf(verifiedAuthorEmail("John@Oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")))
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
                verifiedAuthorEmail("a1@example.com", "A", "One", false, null, null),
                verifiedAuthorEmail("a2@example.com", "B", "Two", false, null, null)
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
            listOf(verifiedAuthorEmail("no-orcid@example.com", "No", "Orcid", false, "Some Lab", null)))
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
            currentExtraction(emptyList(), "FULLTEXT_XML", "FULLTEXT_FETCH_FAILED", httpRequests = 1))

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
            currentExtraction(emptyList(), "FULLTEXT_XML", "NO_EMAIL_IN_FULLTEXT", httpRequests = 1),
            currentExtraction(emptyList(), "FULLTEXT_XML", "NO_EMAIL_IN_FULLTEXT", httpRequests = 1),
            currentExtraction(
                listOf(verifiedAuthorEmail("dup@oxford.ac.uk", "John", "Smith", true, "Oxford, UK", "0000-0001")),
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
        assertEquals(mapOf("DUPLICATE" to 1, "IDENTITY_EXISTING_UNVERIFIED" to 1), batchProgress[1].batchRejectReasons)
    }

    @Test
    fun `PDF_DOWNLOAD_FAILED does not increment fulltextObtained`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmailsOutcome(europePmc,
            currentExtraction(emptyList(), "PDF_PARSE", "PDF_DOWNLOAD_FAILED", httpRequests = 1))

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        val sourceStats = result.stats.bySource["EUROPE_PMC"]
        assertEquals(0, sourceStats?.fulltextObtained)
        assertEquals(1, sourceStats?.pdfDownloadFailed)
    }

    @Test
    fun `NO_EMAIL_IN_HTML counts as content obtained and keeps its own reason (I-3)`() {
        // c10（I-3）：取到 HTML 就是获取内容成功，但仍单列 NO_EMAIL_IN_HTML —— HTML 不保证是论文全文。
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmailsOutcome(europePmc,
            currentExtraction(
                emptyList(), "HTML_FALLBACK", "NO_EMAIL_IN_HTML",
                httpRequests = 1, fulltextObtained = true
            ))

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        val sourceStats = result.stats.bySource["EUROPE_PMC"]
        assertEquals(1, sourceStats?.fulltextObtained)
        assertEquals(1, sourceStats?.noEmailInFulltext)
        assertEquals(1, sourceStats?.failureReasons?.get("NO_EMAIL_IN_HTML"))
        assertEquals(0, sourceStats?.pdfDownloadFailed)
    }

    @Test
    fun `an explicit not-obtained declaration is not content obtained (I-3)`() {
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmailsOutcome(europePmc,
            currentExtraction(emptyList(), "PDF_PARSE", null, httpRequests = 1, fulltextObtained = false))

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        val sourceStats = result.stats.bySource["EUROPE_PMC"]
        assertEquals(0, sourceStats?.fulltextObtained)
        assertEquals(0, sourceStats?.noEmailInFulltext)
    }

    @Test
    fun `an adapter without the declaration keeps the previous derivation (I-4, V-3)`() {
        // 旧适配器（EuropePMC/CORE/arXiv/Crossref）不声明 fulltextObtained：推导必须与改动前逐字一致。
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmailsOutcome(europePmc,
            currentExtraction(emptyList(), "FULLTEXT_XML", null))

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        val sourceStats = result.stats.bySource["EUROPE_PMC"]
        assertEquals(1, sourceStats?.fulltextObtained)
        assertEquals(1, sourceStats?.noEmailInFulltext)
    }

    @Test
    fun `download failure categories reach the per-source details summary (I-3)`() {
        // c10（I-3）：失败类别要能出现在任务 details_json 的 bySource.failureReasons 里，运营才能按桶看。
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmailsOutcome(europePmc,
            currentExtraction(
                emptyList(), "PDF_PARSE", "PDF_DOWNLOAD_FAILED",
                httpRequests = 1, fulltextObtained = false, downloadFailureCategory = "HTTP_403"
            ))

        val captured = mutableListOf<TaskProgress>()
        DiscoveryMockHelper.captureProgressUpdates(progressStore, captured)

        svc.discover(PaperSearchCriteria(), "TEST")

        val sourceDetails = (captured.last().details?.get("bySource") as Map<*, *>)["EUROPE_PMC"] as Map<*, *>
        val failureReasons = sourceDetails["failureReasons"] as Map<*, *>
        assertEquals(1, failureReasons["HTTP_403"])
        assertEquals(1, failureReasons["PDF_DOWNLOAD_FAILED"])
        assertEquals(1, sourceDetails["pdfDownloadFailed"])
        assertEquals(0, sourceDetails["fulltextObtained"])
    }

    @Test
    fun `one paper with two download attempts counts one paper and two requests (V-1, I-3)`() {
        // c10（I-1/I-3）：回退两次的同一篇论文只算 1 篇，下载尝试数单独计（这里体现为 httpRequests）。
        val svc = createService()
        val p1 = paper("PMC1", "Test")
        DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(listOf(p1), null, 1))
        DiscoveryMockHelper.stubExtractAuthorEmailsOutcome(europePmc,
            currentExtraction(
                listOf(verifiedAuthorEmail("a1@example.com", "A", "One", false, null, null)),
                "PDF_PARSE", null, httpRequests = 2, fulltextObtained = true
            ))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "a1@example.com", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)

        val result = svc.discover(PaperSearchCriteria(), "TEST")
        val sourceStats = result.stats.bySource["EUROPE_PMC"]
        assertEquals(1, sourceStats?.papersSearched)
        assertEquals(1, sourceStats?.fulltextAttempted)
        assertEquals(1, sourceStats?.fulltextObtained)
        assertEquals(1, sourceStats?.indexed)
        assertEquals(3, sourceStats?.apiRequests, "1 次搜索请求 + 2 次下载尝试：同一篇绝不因回退被计成两篇")
    }

    @Test
    fun `ORCID progress uses same unit for processedCount and totalCount`() {
        val svc = createService(ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 200))
        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        Mockito.doCallRealMethod().`when`(orcid).orcidRecordToAuthorEmails((Mockito.any(OrcidDataSource.OrcidRecord::class.java) ?: OrcidDataSource.OrcidRecord("test", "A", "B", emptyList(), null, null)))
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 25)

        val records = (1..10).map { OrcidDataSource.OrcidRecord(
            orcidId = "0000-000$it", givenNames = "Test", familyNames = "$it",
            emails = listOf("test$it@example.com"), institutionName = "Univ", country = null
        )}
        stubOrcid(orcid, records)

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
        Mockito.doCallRealMethod().`when`(orcid).orcidRecordToAuthorEmails((Mockito.any(OrcidDataSource.OrcidRecord::class.java) ?: OrcidDataSource.OrcidRecord("test", "A", "B", emptyList(), null, null)))
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 5)

        val records = (1..20).map { OrcidDataSource.OrcidRecord(
            orcidId = "0000-000$it", givenNames = "Test", familyNames = "$it",
            emails = listOf("test$it@example.com"), institutionName = "Univ", country = null
        )}
        stubOrcid(orcid, records)

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
        Mockito.doCallRealMethod().`when`(orcid).orcidRecordToAuthorEmails((Mockito.any(OrcidDataSource.OrcidRecord::class.java) ?: OrcidDataSource.OrcidRecord("test", "A", "B", emptyList(), null, null)))
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 10)

        val records = (1..5).map { OrcidDataSource.OrcidRecord(
            orcidId = "0000-000$it", givenNames = "Test", familyNames = "$it",
            emails = listOf("test$it@example.com"), institutionName = "Univ", country = null
        )}
        stubOrcid(orcid, records)

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
            (1..3).map { verifiedAuthorEmail("emc$it@oxford.ac.uk", "Author", "$it", true, "Oxford, UK", "0000-000$it") }
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
        Mockito.doCallRealMethod().`when`(orcid).orcidRecordToAuthorEmails((Mockito.any(OrcidDataSource.OrcidRecord::class.java) ?: OrcidDataSource.OrcidRecord("test", "A", "B", emptyList(), null, null)))
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 25)
        val orcidRecords = (1..10).map { OrcidDataSource.OrcidRecord(
            orcidId = "0000-000$it", givenNames = "O", familyNames = "$it",
            emails = listOf("or$it@univ.edu"), institutionName = "Univ", country = null
        )}
        stubOrcid(orcid, orcidRecords)
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
        Mockito.doCallRealMethod().`when`(orcid).orcidRecordToAuthorEmails((Mockito.any(OrcidDataSource.OrcidRecord::class.java) ?: OrcidDataSource.OrcidRecord("test", "A", "B", emptyList(), null, null)))
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 100)

        // API returns 10 records, each with one valid email
        val records = (1..10).map { OrcidDataSource.OrcidRecord(
            orcidId = "0000-000$it", givenNames = "Test", familyNames = "$it",
            emails = listOf("test$it@example.com"), institutionName = "Univ", country = null
        )}
        stubOrcid(orcid, records)

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
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
    fun `hasDueEnrichmentJobs 只读探针不领取、不推进、不写任何任务状态 (R-3, V-3)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        val due = ExpertAcademicEnrichmentJob(
            id = 5L, expertDocId = "A-1", source = "OPENALEX",
            status = ExpertAcademicEnrichmentJob.STATUS_PENDING, attempts = 0,
            nextAttemptAt = LocalDateTime.now().minusMinutes(1)
        )
        Mockito.doReturn(listOf(due)).`when`(enrichmentJobRepository)
            .findDueCandidates(eqValue(1), anyDateTime())

        assertTrue(svc.hasDueEnrichmentJobs(), "有到期任务时探针为真")

        // 只读：不写租约、不改状态、也不经 07 的领取入口
        Mockito.verify(enrichmentJobRepository, Mockito.never()).claimById(
            Mockito.anyLong(), Mockito.anyString(), anyDateTime(),
            anyDateTime()
        )
        Mockito.verify(enrichmentJobService, Mockito.never())
            .claimDue(Mockito.anyInt(), anyDateTime())
        Mockito.verify(enrichmentJobRepository, Mockito.never())
            .insertIfAbsent(Mockito.anyString(), Mockito.anyString(), Mockito.any(), anyDateTime())
    }

    @Test
    fun `hasDueEnrichmentJobs 在没有 OpenAlex 或没有到期任务时为假且不落任何领取动作 (R-3, V-3)`() {
        val withoutOpenAlex = createService()
        assertFalse(withoutOpenAlex.hasDueEnrichmentJobs(), "OpenAlex 未启用时不存在可领取的任务")
        Mockito.verify(enrichmentJobRepository, Mockito.never())
            .findDueCandidates(Mockito.anyInt(), anyDateTime())

        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        Mockito.doReturn(emptyList<ExpertAcademicEnrichmentJob>()).`when`(enrichmentJobRepository)
            .findDueCandidates(eqValue(1), anyDateTime())

        assertFalse(svc.hasDueEnrichmentJobs(), "没有到期任务时探针为假")

        Mockito.verify(enrichmentJobRepository, Mockito.never())
            .claimById(Mockito.anyLong(), Mockito.anyString(), anyDateTime(),
                anyDateTime())
        Mockito.verify(enrichmentJobService, Mockito.never())
            .claimDue(Mockito.anyInt(), anyDateTime())
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
        Mockito.verify(openAlex, Mockito.never()).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
        Mockito.verify(openAlex).batchEnrichByOrcids(
            eqValue(listOf("0000-0031")), eqValue(RequestKind.HISTORY_ENRICHMENT)
        )
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
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
        Mockito.verify(openAlex, Mockito.times(10)).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))

        val result = svc.enrichExistingExperts()

        assertEquals(1, result.failureReasons["CIRCUIT_BREAKER"])
        assertEquals(null, result.failureReasons["RATE_LIMITED"])
        assertEquals(0, result.enriched)
        assertEquals(0, result.failed)
        assertEquals("FAILED", result.taskFinalStatus)
        ScrollExpertsMockHelper.verifyEnrichmentProgressContainsStatus(progressStore, "FAILED")
        Mockito.verify(openAlex, Mockito.times(5)).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))

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
        Mockito.verify(openAlex, Mockito.never()).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
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
        stubOrcid(orcid, listOf(record))
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
        stubOrcid(orcid, listOf(mismatchedRecord))

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
        stubOrcid(orcid, listOf(record))
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
        stubOrcid(orcid, listOf(record))
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
        stubOrcid(orcid, listOf(record))
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
        stubOrcid(orcid, listOf(matchedRecord))
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
        stubOrcid(orcid, listOf(record))
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
        stubOrcid(orcid, listOf(record))
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
        stubOrcid(orcid, listOf(record))
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
        stubOrcid(orcid, listOf(matchedRecord))
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
        stubOrcid(orcid, listOf(matchedRecord))
        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "test@example.com", EmailValidationResult(2, true))

        svc.discover(PaperSearchCriteria(), "TEST")

        DiscoveryMockHelper.verifyRawUpdateCalled(restTemplate, 1)
    }

    @Test
    fun `partial batch does not advance cursor to nextCursor`() {
        // P1-1 + c9：run 级额度（150）不是整页倍数时，第二页只消费一半，
        // 检查点必须停在进入第二页的 cursor，绝不能跳到第三页（也不得跳过未处理论文）。
        val limitedProperties = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 150, maxAuthorsPerRun = 2_000, includeRawScan = false
        )
        val svc = createService(limitedProperties)
        val page1 = pageOf(100, "C2")
        val page2 = pageOf(100, "C3", offset = 100)
        installInMemoryCursorStore()
        Mockito.doReturn(page1).doReturn(page2)
            .`when`(europePmc).searchPapers(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_EMAIL_IN_FULLTEXT")

        val (result, saved) = runAndCapture(svc)

        val decoded = decodedCheckpoint(savedCheckpoints(saved, "EUROPE_PMC").last())
        assertNotEquals("C3", decoded.cursor,
            "额度到界导致的半页必须保留进入该页的 cursor，否则会跳过未处理论文")
        assertEquals("C2", decoded.cursor, "第二页消费一半，续跑从第二页入口开始")
        assertEquals(150, result.stats.totalPapers)
        assertEquals(150L, storedRowFor("EUROPE_PMC").papersProcessedTotal)
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
            .`when`(orcid).searchOrcidPage(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
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
        Mockito.doCallRealMethod().`when`(orcid).orcidRecordToAuthorEmails((Mockito.any(OrcidDataSource.OrcidRecord::class.java) ?: OrcidDataSource.OrcidRecord("test", "A", "B", emptyList(), null, null)))
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 25)
        val records = (1..2).map {
            OrcidDataSource.OrcidRecord(
                orcidId = "0000-000$it", givenNames = "Test", familyNames = "$it",
                emails = listOf("test$it@example.com"), institutionName = "Univ", country = null
            )
        }
        stubOrcid(orcid, records)
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
        // c9：两个启用来源（EPMC + OpenAlex）各一页基础份额需要 ≥200 的 run 级目标。
        val svc = createService(
            ExpertDiscoveryProperties(
                enabled = true, maxPapersPerRun = 300, maxAuthorsPerRun = 2_000, includeRawScan = false
            )
        )
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
            europePmc, listOf(verifiedAuthorEmail("rawfail@example.com", "A", "B", false, null, "0000-0009"))
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
        // c9：run 级目标必须覆盖两个来源的基础份额（各一页 = 200），否则启动校验会拒绝该配置。
        val props = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 300, maxAuthorsPerRun = 200, includeRawScan = false
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
            // c9：run 级目标必须覆盖本人来源的基础份额，因此本源上限同页大小取 paperCount。
            DiscoveryMockHelper.stubMaxPapersPerSource(europePmc, paperCount)
            val executor: Executor = if (concurrency <= 1) Executor { it.run() } else Executors.newFixedThreadPool(concurrency)
            val svc = createService(props, executor)

            DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, null, paperCount.toLong()))
            val outcomes = papers.mapIndexed { index, _ ->
                if (index % 3 == 0) {
                    currentExtraction(emptyList(), "FULLTEXT_XML", "NO_EMAIL_IN_FULLTEXT")
                } else {
                    currentExtraction(
                        listOf(verifiedAuthorEmail("author$index@example.com", "A", "B$index", false, null, null)),
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
            // c9：run 级目标必须覆盖本人来源的基础份额，因此本源上限同页大小取 paperCount。
            DiscoveryMockHelper.stubMaxPapersPerSource(europePmc, paperCount)
            DiscoveryMockHelper.stubSearchPapers(europePmc, PaperSearchResult(papers, null, paperCount.toLong()))
            val outcomes = papers.mapIndexed { index, _ ->
                if (index % 2 == 0) {
                    currentExtraction(emptyList(), "FULLTEXT_XML", "NO_EMAIL_IN_FULLTEXT")
                } else {
                    currentExtraction(
                        listOf(verifiedAuthorEmail("author$index@example.com", "A", "B$index", false, null, null)),
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
            // c9：全局上限 2 必须覆盖本人来源的基础份额，故本源上限同为 2（页内 5 篇只消费 2 篇）。
            DiscoveryMockHelper.stubMaxPapersPerSource(europePmc, maxPapers)
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
                currentExtraction(
                    listOf(verifiedAuthorEmail("author$index@example.com", "A", "B$index", false, null, null)),
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

    // ---------------- c4: CORE offset 分页与 ORCID 原始记录翻页 ----------------

    private fun c4Props() = ExpertDiscoveryProperties(
        enabled = true, maxPapersPerRun = 1_000, maxAuthorsPerRun = 1_000
    )

    @Test
    fun `CORE resumes from the persisted shard offset instead of restarting at page one`() {
        // V-1：跨运行续 offset。改动前 CORE 的 scrollId 不持久化，每次运行都从首批重来。
        val criteria = PaperSearchCriteria(
            pageSize = 100, publicationYearFrom = 2020, publicationYearTo = 2026,
            subjectScope = SubjectScopeCatalog.RND_TARGET, sources = listOf("CORE")
        )
        stubStoredCheckpoint("CORE", "0|2020|100", criteria)
        val core = StubCore(coreWorksBody(rawCount = 100, totalHits = 5_000), maxPapersPerSource = 100)
        Mockito.doReturn(core.source).`when`(coreProvider).getIfAvailable()

        val (result, saved) = runAndCapture(createService(c4Props()), criteria)

        assertEquals(1, core.requests.size, "本源限额用尽即停，只应发出进入该页的一次请求")
        assertEquals(100, core.requests.single()["offset"], "续跑必须从检查点 offset=100 开始，不能再发 offset=0")
        val checkpoint = decodedCheckpoint(savedCheckpoints(saved, "CORE").last())
        assertEquals("0|2020|200", checkpoint.cursor, "页满按原始返回条数推进并落盘")
        assertEquals(CheckpointState.ACTIVE, checkpoint.state)
        assertEquals(100, result.stats.bySource["CORE"]?.papersSearched)
    }

    @Test
    fun `CORE records WINDOW_LIMIT and rotates the shard at the vendor offset window`() {
        // V-1/I-4：到 9000 记录窗口限制 —— 该分片停止、切下一分片、WINDOW_LIMIT 落进来源明细，
        // 既不冒充穷尽也不继续递增 offset。
        val criteria = PaperSearchCriteria(
            pageSize = 100, publicationYearFrom = 2020, publicationYearTo = 2021,
            subjectScope = SubjectScopeCatalog.RND_TARGET, sources = listOf("CORE")
        )
        stubStoredCheckpoint("CORE", "0|2020|9000", criteria)
        val core = StubCore(coreWorksBody(rawCount = 100, totalHits = 500_000), maxPapersPerSource = 100)
        Mockito.doReturn(core.source).`when`(coreProvider).getIfAvailable()

        val (result, saved) = runAndCapture(createService(c4Props()), criteria)

        assertEquals(1, core.requests.size, "分片到界后不得再对该分片发请求")
        assertEquals(9000, core.requests.single()["offset"])
        val checkpoint = decodedCheckpoint(savedCheckpoints(saved, "CORE").last())
        assertEquals("0|2021|0", checkpoint.cursor, "窗口分片停止后游标切到下一分片")
        assertEquals(CheckpointState.ACTIVE, checkpoint.state)

        val coreStats = result.stats.bySource["CORE"]
        assertEquals(1, coreStats?.failureReasons?.get(DiscoveryStopReason.WINDOW_LIMIT),
            "WINDOW_LIMIT 必须以来源明细形式记录")
        assertEquals(DiscoveryStopReason.SOURCE_LIMIT, coreStats?.stopReason)
        assertTrue(coreStats?.pendingWork == true, "窗口分片已切走，仍有可续跑工作，不能标记为已穷尽")
    }

    @Test
    fun `CORE sends the catalogue topics with explicit parentheses and keeps operator keywords`() {
        // V-3/I-3：默认研发范围下发的主题词带显式括号 OR；人工关键词不被覆盖。
        val criteria = PaperSearchCriteria(
            pageSize = 100, subjectScope = SubjectScopeCatalog.RND_TARGET, sources = listOf("CORE")
        )
        val core = StubCore(coreWorksBody(rawCount = 1, totalHits = 1), maxPapersPerSource = 100)
        Mockito.doReturn(core.source).`when`(coreProvider).getIfAvailable()

        runAndCapture(createService(c4Props()), criteria)

        assertEquals(
            "(engineering OR materials OR computer science OR chemical OR energy OR physics) AND yearPublished=2020",
            core.requests.first()["q"]
        )
        assertTrue(core.requests.all { (it["q"] as String).contains("engineering OR materials") },
            "每个年份分片都必须带同一组显式括号 OR 主题词")

        val manualCriteria = criteria.copy(keywords = listOf("perovskite solar cell"))
        val manualCore = StubCore(coreWorksBody(rawCount = 1, totalHits = 1), maxPapersPerSource = 100)
        Mockito.doReturn(manualCore.source).`when`(coreProvider).getIfAvailable()

        runAndCapture(createService(c4Props()), manualCriteria)

        assertEquals("(perovskite solar cell) AND yearPublished=2020", manualCore.requests.first()["q"])
    }

    @Test
    fun `CORE query shards per year use independent checkpoints`() {
        // V-1：查询年份变更用独立检查点，互不覆盖。
        val criteria2020 = PaperSearchCriteria(
            pageSize = 100, publicationYearFrom = 2020, publicationYearTo = 2020,
            subjectScope = SubjectScopeCatalog.RND_TARGET, sources = listOf("CORE")
        )
        val criteria2021 = criteria2020.copy(publicationYearFrom = 2021, publicationYearTo = 2021)
        installInMemoryCursorStore()
        val core = StubCore(
            coreWorksBody(rawCount = 100, totalHits = 5_000),
            coreWorksBody(rawCount = 100, totalHits = 5_000),
            maxPapersPerSource = 100
        )
        Mockito.doReturn(core.source).`when`(coreProvider).getIfAvailable()

        val svc = createService(c4Props())
        svc.discover(criteria2020, "TEST")
        svc.discover(criteria2021, "TEST")

        assertEquals("0|2020|100", storedCheckpointFor("CORE", criteria2020).cursor)
        assertEquals("0|2021|100", storedCheckpointFor("CORE", criteria2021).cursor,
            "另一年份的检查点必须写在独立 key 上")
        assertNotEquals(
            DiscoveryCheckpointCodec.sourceKey("CORE", criteria2020),
            DiscoveryCheckpointCodec.sourceKey("CORE", criteria2021)
        )
        assertEquals("2020", (core.requests[0]["q"] as String).substringAfter("yearPublished="))
        assertEquals("2021", (core.requests[1]["q"] as String).substringAfter("yearPublished="))
    }

    @Test
    fun `ORCID pages past a whole page without public emails and still acquires the next page expert`() {
        // V-2/I-2：首页 100 条无公开邮箱、次页 1 条有公开邮箱 —— 必须覆盖两页并最终收录 1 人。
        val criteria = PaperSearchCriteria(
            pageSize = 100, subjectScope = SubjectScopeCatalog.RND_TARGET, sources = listOf("ORCID")
        )
        val template = Mockito.mock(RestTemplate::class.java)
        val urls = mutableListOf<String>()
        Mockito.doAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            urls.add(url)
            objectMapper.readTree(
                when {
                    url.contains("start=0") && urlQuery(url) == "keyword:\"engineering\"" -> orcidPageBody(rawCount = 100)
                    url.contains("start=100") -> orcidPageBody(rawCount = 1, publicEmail = "found@ox.ac.uk")
                    else -> """{"expanded-result": []}"""
                }
            )
        }.`when`(template).getForObject(
            Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        Mockito.doReturn(OrcidDataSource(template, OrcidProperties(enabled = true, requestDelayMs = 0)))
            .`when`(orcidProvider).getIfAvailable()

        DiscoveryMockHelper.stubValidateEmail(emailValidationService, "found@ox.ac.uk", EmailValidationResult(2, true))
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val (result, saved) = runAndCapture(createService(c4Props()), criteria)

        assertEquals(1, result.stats.bySource["ORCID"]?.indexed, "次页的 1 位专家必须被收录")
        assertEquals(1, result.stats.bySource["ORCID"]?.papersSearched)
        assertEquals(listOf("0", "100"), urls.take(2).map { urlStart(it) },
            "整页无公开邮箱后 offset 必须继续前进到 100")
        assertEquals("keyword:\"engineering\"", urlQuery(urls[0]))
        assertEquals("keyword:\"engineering\"", urlQuery(urls[1]))
        assertEquals(
            listOf("keyword:\"materials\"", "keyword:\"computer science\"", "keyword:\"chemical\"",
                "keyword:\"energy\"", "keyword:\"physics\""),
            urls.drop(2).map { urlQuery(it) },
            "主题分片用尽后必须切到下一个主题分片（而非停在原地）"
        )
        assertTrue(urls.drop(2).all { urlStart(it) == "0" }, "每个新分片都从 offset=0 开始")
        assertEquals(urls.size, result.stats.bySource["ORCID"]?.apiRequests)
        assertEquals(CheckpointState.EXHAUSTED, decodedCheckpoint(savedCheckpoints(saved, "ORCID").last()).state,
            "全部分片遍历完才判穷尽，下一个扫描周期可重开")
    }

    @Test
    fun `ORCID skips without a request when no keyword and no scope seed exist`() {
        // V-2：空关键词 + 无 scope = 明确跳过（不发请求），不谎报失败。
        val criteria = PaperSearchCriteria(pageSize = 100, sources = listOf("ORCID"))
        val template = Mockito.mock(RestTemplate::class.java)
        Mockito.doReturn(OrcidDataSource(template, OrcidProperties(enabled = true, requestDelayMs = 0)))
            .`when`(orcidProvider).getIfAvailable()

        val result = createService(c4Props()).discover(criteria, "TEST")

        Mockito.verify(template, Mockito.never())
            .getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        assertEquals(0, result.stats.bySource["ORCID"]?.papersSearched)
        assertEquals(0, result.stats.sourceFailures, "跳过不是失败")
    }

    // ---------------- c6: 定向补全与三层结果契约 ----------------

    private fun c6Expert(orcidId: String, esDocId: String? = null, externalIds: String? = null) =
        com.weibo.talentintroduction.expert.domain.ExpertProfile(
            esDocId = esDocId, orcidId = orcidId, email = "e@example.com",
            givenNames = "Test", familyNames = "User",
            country = "US", keyword = null, employment = null, externalIds = externalIds
        )

    /** 让某一层的 HEAD 返回 200（其余层继续沿用 setUp 的 404）。 */
    private fun stubLayerExists(indexName: String) {
        Mockito.doReturn(ResponseEntity.ok<Void>(null))
            .`when`(restTemplate).exchange(
                Mockito.contains("$indexName/_doc/"), Mockito.eq(HttpMethod.HEAD), Mockito.any(),
                Mockito.eq(Void::class.java)
            )
    }

    /** 只有 RAW 层存在：CANDIDATE/APPLICATION 的 HEAD 由 setUp 里的 stubEsHeadNotFound 保持 404。 */
    private fun stubRawLayerOnly() = stubLayerExists("orcid_info")

    private fun captureAcademicUpdateBody(): Map<*, *> {
        @Suppress("UNCHECKED_CAST")
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<*>>
        Mockito.verify(restTemplate, Mockito.atLeastOnce()).exchange(
            Mockito.contains("/_update/"), Mockito.eq(HttpMethod.POST), entityCaptor.capture(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        return (entityCaptor.value.body as Map<*, *>)["doc"] as Map<*, *>
    }

    @Test
    fun `enrichProfiles prefers the trusted author id and keys results by the real esDocId (I-1)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        // 作者 ID 与 ORCID 同时存在：只用作者 ID；EMAIL-* 主键既不是 ORCID 也不发查询。
        val authorIdExpert = c6Expert(
            "0000-0001", esDocId = "DOC-A",
            externalIds = """{"openAlexAuthorId":"https://openalex.org/A5023888391","orcid":"0000-0001"}"""
        )
        val orcidOnlyExpert = c6Expert("0000-0002", esDocId = "DOC-B")
        val noIdentityExpert = c6Expert("EMAIL-noid", esDocId = "DOC-C")
        val enrichment = AuthorEnrichment(hIndex = 10, citationCount = 100, worksCount = 5)

        Mockito.doReturn(mapOf("A5023888391" to EnrichmentOutcome.Success(enrichment)))
            .`when`(openAlex).batchEnrichByAuthorIds(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        Mockito.doReturn(mapOf("0000-0002" to EnrichmentOutcome.Success(enrichment)))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        stubRawLayerOnly()
        stubAcademicUpdateOk()

        val outcomes = svc.enrichProfiles(listOf(authorIdExpert, orcidOnlyExpert, noIdentityExpert))

        assertEquals(setOf("DOC-A", "DOC-B", "DOC-C"), outcomes.keys, "结果必须以真实 esDocId 为键")
        assertInstanceOf(ProfileEnrichmentOutcome.Success::class.java, outcomes["DOC-A"])
        assertInstanceOf(ProfileEnrichmentOutcome.Success::class.java, outcomes["DOC-B"])
        assertEquals(ProfileEnrichmentOutcome.NoId, outcomes["DOC-C"], "无可靠身份 = NO_ID")

        Mockito.verify(openAlex)
            .batchEnrichByAuthorIds(eqValue(listOf("A5023888391")), eqValue(RequestKind.HISTORY_ENRICHMENT))
        Mockito.verify(openAlex)
            .batchEnrichByOrcids(eqValue(listOf("0000-0002")), eqValue(RequestKind.HISTORY_ENRICHMENT))
        Mockito.verify(openAlex, Mockito.never())
            .batchEnrichByOrcids(eqValue(listOf("EMAIL-noid")), eqValue(RequestKind.HISTORY_ENRICHMENT))
        Mockito.verify(openAlex, Mockito.never())
            .batchEnrichByAuthorIds(eqValue(listOf("0000-0001")), eqValue(RequestKind.HISTORY_ENRICHMENT))
    }

    @Test
    fun `discovery enrichment never treats the old business key or unverified external ids as identity`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        val expert = c6Expert("0000-0002-1825-0097", esDocId = "OLD-DOC",
            externalIds = """{"openAlexAuthorId":"A5023888391"}""").copy(emailSource = "PAPER_FULLTEXT")

        assertEquals(ProfileEnrichmentOutcome.NoId, svc.enrichProfiles(listOf(expert))["OLD-DOC"])
        Mockito.verify(openAlex, Mockito.never()).batchEnrichByAuthorIds(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        Mockito.verify(openAlex, Mockito.never()).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
    }

    @Test
    fun `verified discovery enrichment uses proof author id and binds the update to the same identity`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        val proof = DiscoveryIdentity.verified("e@example.com", "Test", "User",
            "JATS_SHA256:" + "a".repeat(64), null, "A5023888391")
        val expert = c6Expert("0000-0002-1825-0097", esDocId = "OLD-DOC",
            externalIds = """{"openAlexAuthorId":"A9999999999"}""")
            .copy(emailSource = "PAPER_FULLTEXT", identityVerification = proof)
        Mockito.doReturn(mapOf("A5023888391" to EnrichmentOutcome.Success(AuthorEnrichment(hIndex = 10, citationCount = 100, worksCount = 5))))
            .`when`(openAlex).batchEnrichByAuthorIds(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        stubRawLayerOnly()
        stubAcademicUpdateOk()

        assertInstanceOf(ProfileEnrichmentOutcome.Success::class.java, svc.enrichProfiles(listOf(expert))["OLD-DOC"])
        Mockito.verify(openAlex).batchEnrichByAuthorIds(eqValue(listOf("A5023888391")), eqValue(RequestKind.HISTORY_ENRICHMENT))
        Mockito.verify(openAlex, Mockito.never()).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        val captor = ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.verify(restTemplate).exchange(Mockito.contains("/_update/OLD-DOC"), Mockito.eq(HttpMethod.POST),
            captor.capture(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        val body = captor.value.body as Map<*, *>
        assertFalse(body.containsKey("upsert"))
        val script = body["script"] as Map<*, *>
        val params = script["params"] as Map<*, *>
        assertEquals(proof, params["identity"])
        assertEquals(mapOf("openAlexAuthorId" to "A9999999999"), params["externalIds"])
        assertTrue(script["source"].toString().contains("ctx.op = 'none'"))
    }

    @Test
    fun `enrichProfiles splits more than 100 identities into bounded mixed batches (V-1)`() {
        val svc = createService(openAlexProps = openAlexProperties.copy(enrichmentBatchSize = 100))
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val authorIdExperts = (1..100).map {
            c6Expert("0000-A$it", esDocId = "DOC-A$it", externalIds = """{"openAlexAuthorId":"A50$it"}""")
        }
        val orcidExperts = (1..101).map { c6Expert(String.format("0000-%04d", it), esDocId = "DOC-O$it") }
        val experts = authorIdExperts + orcidExperts

        val authorIdBatches = mutableListOf<List<String>>()
        val orcidBatches = mutableListOf<List<String>>()
        val enrichment = AuthorEnrichment(hIndex = 10, citationCount = 100, worksCount = 5)
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val ids = invocation.arguments[0] as List<String>
            authorIdBatches += ids
            ids.associateWith { EnrichmentOutcome.Success(enrichment) }
        }.`when`(openAlex).batchEnrichByAuthorIds(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val ids = invocation.arguments[0] as List<String>
            orcidBatches += ids
            ids.associateWith { EnrichmentOutcome.Success(enrichment) }
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        stubRawLayerOnly()
        stubAcademicUpdateOk()

        val outcomes = svc.enrichProfiles(experts)

        assertEquals(201, outcomes.size)
        assertEquals(listOf(100), authorIdBatches.map { it.size })
        assertEquals(listOf(100, 1), orcidBatches.map { it.size })
        assertTrue(outcomes.keys.containsAll(authorIdExperts.map { it.esDocId }))
        assertTrue(outcomes.keys.containsAll(orcidExperts.map { it.esDocId }))
    }

    @Test
    fun `enrichProfiles reports per-layer results without creating a missing layer (V-2, I-2)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = c6Expert("0000-RAWONLY", esDocId = "DOC-RAW")
        Mockito.doReturn(mapOf("0000-RAWONLY" to EnrichmentOutcome.Success(AuthorEnrichment(10, 100, 5))))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        stubRawLayerOnly()
        stubAcademicUpdateOk()

        val outcome = svc.enrichProfiles(listOf(expert))["DOC-RAW"]

        val success = outcome as ProfileEnrichmentOutcome.Success
        assertEquals(LayerUpdateStatus.UPDATED, success.layers.raw)
        assertEquals(LayerUpdateStatus.ABSENT, success.layers.candidate)
        assertEquals(LayerUpdateStatus.ABSENT, success.layers.application)
        // 只在现存层做 _update；绝不 PUT /_doc/ 创建缺失层（尤其不创建 APPLICATION）。
        Mockito.verify(restTemplate, Mockito.times(1)).exchange(
            Mockito.contains("/_update/"), Mockito.eq(HttpMethod.POST), Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        Mockito.verify(restTemplate, Mockito.never()).exchange(
            Mockito.anyString(), Mockito.eq(HttpMethod.PUT), Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `enrichProfiles never writes null facts over existing values (I-2)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = c6Expert("0000-NULLFACTS", esDocId = "DOC-NULL")
        // OpenAlex 未给出指标：这些键必须整体缺席，否则 _update 会用 null 擦掉存量值。
        Mockito.doReturn(mapOf("0000-NULLFACTS" to EnrichmentOutcome.Success(AuthorEnrichment(null, null, null))))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        stubRawLayerOnly()
        stubAcademicUpdateOk()

        svc.enrichProfiles(listOf(expert))

        val doc = captureAcademicUpdateBody()
        for (key in listOf("hIndex", "citationCount", "worksCount", "researchFields", "disciplineCategory",
            "recentWorkTitles", "patentTitles", "institutionType", "lastPublicationYear")) {
            assertFalse(doc.containsKey(key), "null 事实不得写入 $key")
        }
        assertTrue(doc.values.none { it == null }, "更新体里不得有任何 null 值")
    }

    @Test
    fun `enrichProfiles reports Partial when an existing layer write fails (V-2, I-2)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = c6Expert("0000-PARTIAL", esDocId = "DOC-PARTIAL")
        Mockito.doReturn(mapOf("0000-PARTIAL" to EnrichmentOutcome.Success(AuthorEnrichment(10, 100, 5))))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        stubRawLayerOnly()
        stubLayerExists("orcid_info_candidate")
        stubAcademicUpdateOk()
        // 只有 CANDIDATE 层的 _update 失败（RAW 成功、APPLICATION 不存在）。
        Mockito.doThrow(RuntimeException("ES 5xx"))
            .`when`(restTemplate).exchange(
                Mockito.contains("/orcid_info_candidate/_update/"), Mockito.eq(HttpMethod.POST), Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val outcome = svc.enrichProfiles(listOf(expert))["DOC-PARTIAL"]

        val partial = outcome as ProfileEnrichmentOutcome.Partial
        assertEquals(LayerUpdateStatus.UPDATED, partial.layers.raw)
        assertEquals(LayerUpdateStatus.FAILED, partial.layers.candidate)
        assertEquals(LayerUpdateStatus.ABSENT, partial.layers.application)
        assertTrue(partial.layers.hasFailedLayer())
        assertTrue(partial.layers.updatedAnyLayer())
    }

    @Test
    fun `enrichProfiles reports Partial when the optional recent-titles fetch fails and Success after recovery (I-3)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = c6Expert("0000-TITLES", esDocId = "DOC-TITLES")
        val base = AuthorEnrichment(hIndex = 10, citationCount = 100, worksCount = 5)
        var calls = 0
        Mockito.doAnswer { invocation ->
            calls++
            @Suppress("UNCHECKED_CAST")
            val ids = invocation.arguments[0] as List<String>
            if (calls == 1) {
                // 首次：基础事实成功、最近论文标题子请求失败（可单独重试）。
                ids.associateWith { EnrichmentOutcome.Success(base, titlesFailed = true) }
            } else {
                ids.associateWith {
                    EnrichmentOutcome.Success(base.copy(recentWorkTitles = listOf("Recovered Paper")))
                }
            }
        }.`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        stubRawLayerOnly()
        stubAcademicUpdateOk()

        val first = svc.enrichProfiles(listOf(expert))["DOC-TITLES"] as ProfileEnrichmentOutcome.Partial
        assertTrue(first.recentWorksFailed)
        assertFalse(first.layers.hasFailedLayer(), "基础事实已写入，不得当作层失败")
        assertFalse(captureAcademicUpdateBody().containsKey("recentWorkTitles"))

        val second = svc.enrichProfiles(listOf(expert))["DOC-TITLES"]
        assertInstanceOf(ProfileEnrichmentOutcome.Success::class.java, second)
        assertEquals(listOf("Recovered Paper"), captureAcademicUpdateBody()["recentWorkTitles"])
    }

    @Test
    fun `enrichProfiles surfaces quota deferral as Deferred instead of a retryable error (c1 contract)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val resetAt = Instant.parse("2026-09-22T00:00:00Z")
        Mockito.doThrow(OpenAlexBudgetDeferredException(resetAt))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))

        val outcomes = svc.enrichProfiles(
            listOf(c6Expert("0000-0001", esDocId = "DOC-1"), c6Expert("0000-0002", esDocId = "DOC-2"))
        )

        assertEquals(2, outcomes.size)
        assertTrue(outcomes.values.all { it is ProfileEnrichmentOutcome.Deferred })
        assertEquals(resetAt, (outcomes["DOC-1"] as ProfileEnrichmentOutcome.Deferred).resetAt)
        assertEquals(resetAt, (outcomes["DOC-2"] as ProfileEnrichmentOutcome.Deferred).resetAt)
        Mockito.verify(restTemplate, Mockito.never()).exchange(
            Mockito.contains("/_update/"), Mockito.eq(HttpMethod.POST), Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `enrichExistingExperts counts a partially written expert as failed (I-2)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = c6Expert("0000-PARTIAL", esDocId = "DOC-PARTIAL")
        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(listOf(expert)))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doReturn(mapOf("0000-PARTIAL" to EnrichmentOutcome.Success(AuthorEnrichment(10, 100, 5))))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        stubRawLayerOnly()
        stubLayerExists("orcid_info_candidate")
        stubAcademicUpdateOk()
        Mockito.doThrow(RuntimeException("ES 5xx"))
            .`when`(restTemplate).exchange(
                Mockito.contains("/orcid_info_candidate/_update/"), Mockito.eq(HttpMethod.POST), Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = svc.enrichExistingExperts()

        assertEquals(0, result.enriched)
        assertEquals(1, result.failed)
        assertEquals(1, result.failureReasons["ES_UPDATE_FAILED"])
    }

    @Test
    fun `enrichExistingExperts reports quota deferral without counting failures (I-5)`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(
            expertSearchService, listOf(listOf(c6Expert("0000-0001", esDocId = "DOC-1")))
        )
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doThrow(OpenAlexBudgetDeferredException(Instant.parse("2026-09-22T00:00:00Z")))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))

        val result = svc.enrichExistingExperts()

        assertTrue(result.budgetDeferred)
        assertEquals(0, result.enriched)
        assertEquals(0, result.failed)
        assertEquals(1, result.failureReasons["BUDGET_DEFERRED"])
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
        ScrollExpertsMockHelper.verifyEnrichmentProgressContainsStatus(progressStore, "PARTIAL_SUCCESS")
    }

    @Test
    fun `enrichExistingExperts counts a RAW-only update as success (I-2)`() {
        // I-2 缺陷复现：RAW 层写入成功、CANDIDATE/APPLICATION 不存在时，旧实现按 candidateUpdated=false
        // 记失败（原始层成功误报失败）。修复后必须记成功，且只能更新真实存在的层。
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()

        val expert = c6Expert("0000-RAWONLY")
        ScrollExpertsMockHelper.stubSearchAfterExpertsFiltered(expertSearchService, listOf(listOf(expert)))
        ScrollExpertsMockHelper.stubCountExperts(expertSearchService, 1L, 1L)
        Mockito.doReturn(mapOf("0000-RAWONLY" to EnrichmentOutcome.Success(AuthorEnrichment(10, 100, 5))))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        stubRawLayerOnly()
        stubAcademicUpdateOk()

        val result = svc.enrichExistingExperts()

        assertEquals(1, result.enriched, "RAW-only 补全成功不得误报失败")
        assertEquals(0, result.failed)
        Mockito.verify(restTemplate, Mockito.times(1)).exchange(
            Mockito.contains("/_update/"), Mockito.eq(HttpMethod.POST), Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    // ------------------------------------------------------------------
    // c8（08）：RAW 先落地再入队 / 自动与人工共用的补全批次
    // ------------------------------------------------------------------

    private fun anyLocalDateTime(): LocalDateTime = anyDateTime() ?: LocalDateTime.now()

    private fun anyOutcome(): ProfileEnrichmentOutcome =
        Mockito.any(ProfileEnrichmentOutcome::class.java) ?: ProfileEnrichmentOutcome.NoId

    private fun enrichmentJob(
        id: Long,
        docId: String,
        source: String,
        leaseToken: String,
        attempts: Int = 0
    ) = ExpertAcademicEnrichmentJob(
        id = id,
        expertDocId = docId,
        source = source,
        discoveryExecutionId = 42L,
        status = ExpertAcademicEnrichmentJob.STATUS_RUNNING,
        attempts = attempts,
        nextAttemptAt = LocalDateTime.now(),
        leaseToken = leaseToken,
        leaseUntil = LocalDateTime.now().plusMinutes(10)
    )

    /** RAW 层对所有文档存在；CANDIDATE 层只对 [candidateDocs] 中的文档存在；APPLICATION 恒不存在。 */
    private fun stubLayerPresence(candidateDocs: Set<String> = emptySet()) {
        Mockito.doAnswer { invocation ->
            val url = invocation.getArgument(0) as String
            val exists = when {
                url.contains("/orcid_info_candidate/_doc/") -> candidateDocs.any { url.endsWith(it) }
                url.contains("/orcid_info_application/_doc/") -> false
                url.contains("/orcid_info/_doc/") -> true
                else -> false
            }
            if (exists) ResponseEntity.ok().build<Void>() else throw HttpClientErrorException(HttpStatus.NOT_FOUND)
        }.`when`(restTemplate).exchange(
            Mockito.anyString(), Mockito.eq(HttpMethod.HEAD), Mockito.any(),
            Mockito.eq(Void::class.java)
        )
    }

    /** 让去重命中返回匹配文档的真实 `_id`（I-1 的补建任务依据）。 */
    private fun stubDedupHit(docId: String) {
        val body = objectMapper.readTree("""{"hits":{"total":{"value":1},"hits":[{"_id":"$docId"}]}}""")
        val hit = body.path("hits").path("hits").first() as com.fasterxml.jackson.databind.node.ObjectNode
        hit.set<com.fasterxml.jackson.databind.JsonNode>("_source", objectMapper.valueToTree(mapOf(
            "email" to "replay@example.com", "givenNames" to "A", "familyNames" to "B",
            "identityVerification" to com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.verified(
                "replay@example.com", "A", "B", "JATS_SHA256:" + "a".repeat(64), null, null))))
        Mockito.doReturn(ResponseEntity.ok(body) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.contains("/_search"), Mockito.eq(HttpMethod.POST), Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
    }

    @Test
    fun `论文路径在 RAW 落库成功后按真实 _id 入队并带上发现执行 id（I-1 I-4）`() {
        val svc = createService()
        DiscoveryMockHelper.stubSearchPapers(
            europePmc, PaperSearchResult(listOf(paper("PMC1", "Paper 1")), null, 1)
        )
        DiscoveryMockHelper.stubExtractAuthorEmails(
            europePmc, listOf(verifiedAuthorEmail("enqueue@example.com", "A", "B", false, null, "0000-0007"))
        )
        DiscoveryMockHelper.stubValidateEmail(
            emailValidationService, "enqueue@example.com", EmailValidationResult(2, true)
        )
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        Mockito.doReturn(77L).`when`(progressStore).getCurrentExecutionId("EXPERT_DISCOVERY")

        val result = svc.discover(PaperSearchCriteria(), "TEST", includeRawScan = false)

        assertEquals(1, result.stats.indexed)
        assertEquals(0, result.stats.duplicates)
        Mockito.verify(enrichmentJobService).enqueue(eqValue(ExpertIdGenerator.generate(null, "enqueue@example.com")), eqValue("EUROPE_PMC"), eqValue(77L))
    }

    @Test
    fun `ORCID 路径在 RAW 落库成功后按真实 _id 入队（I-1）`() {
        val svc = createService()
        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        Mockito.doCallRealMethod().`when`(orcid).orcidRecordToAuthorEmails((Mockito.any(OrcidDataSource.OrcidRecord::class.java) ?: OrcidDataSource.OrcidRecord("test", "A", "B", emptyList(), null, null)))
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 10)
        stubOrcid(
            orcid,
            listOf(
                OrcidDataSource.OrcidRecord(
                    orcidId = "0000-0005", givenNames = "Test", familyNames = "User",
                    emails = listOf("orcid-job@example.com"), institutionName = "Univ", country = null
                )
            )
        )
        DiscoveryMockHelper.stubValidateEmail(
            emailValidationService, "orcid-job@example.com", EmailValidationResult(2, true)
        )
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        Mockito.doReturn(77L).`when`(progressStore).getCurrentExecutionId("EXPERT_DISCOVERY")

        val result = svc.discover(PaperSearchCriteria(), "TEST", includeRawScan = false)

        assertEquals(1, result.stats.bySource["ORCID"]?.indexed)
        Mockito.verify(enrichmentJobService).enqueue(eqValue(ExpertIdGenerator.generate(null, "orcid-job@example.com")), eqValue("ORCID"), eqValue(77L))
    }

    @Test
    fun `补全入队失败保留当前页，重放按匹配 _id 补建任务且只新增 1 人（I-1）`() {
        // I-1 缺陷复现：RAW 已落库但入队失败时，旧实现既没有任务也不保留页（跨 ES/MySQL 崩溃窗口漏任务）。
        val criteria = PaperSearchCriteria()
        val first = createService()
        stubStoredCheckpoint("EUROPE_PMC", "C1", criteria)
        val page = PaperSearchResult(listOf(paper("PMC1", "Paper 1")), "C2", 4)
        DiscoveryMockHelper.stubSearchPapersSequence(europePmc, page, PaperSearchResult(emptyList(), null, 4))
        DiscoveryMockHelper.stubExtractAuthorEmails(
            europePmc, listOf(verifiedAuthorEmail("replay@example.com", "A", "B", false, null, null))
        )
        DiscoveryMockHelper.stubValidateEmail(
            emailValidationService, "replay@example.com", EmailValidationResult(2, true)
        )
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        Mockito.doReturn(77L).`when`(progressStore).getCurrentExecutionId("EXPERT_DISCOVERY")
        Mockito.doThrow(RuntimeException("MySQL down"))
            .`when`(enrichmentJobService).enqueue(Mockito.anyString(), Mockito.anyString(), Mockito.any())

        val (firstResult, _) = runAndCapture(first, criteria)

        val writtenDocId = ExpertIdGenerator.generate(null, "replay@example.com")
        assertEquals(1, firstResult.stats.indexed, "RAW 落库成功：第一轮新增 1 人")
        assertEquals(0, firstResult.stats.duplicates)
        assertEquals(
            DiscoveryStopReason.ENQUEUE_INCOMPLETE,
            firstResult.stats.bySource["EUROPE_PMC"]?.stopReason,
            "入队失败必须给出自己的停止原因"
        )
        assertEquals(1, firstResult.stats.pendingSources)
        val afterFailure = storedCheckpointFor("EUROPE_PMC", criteria)
        assertEquals("C1", afterFailure.cursor, "入队失败不得把检查点推进到 nextCursor")
        assertFalse(afterFailure.exhausted)
        // 第一轮确实尝试过用真实 `_id` 入队（失败被记为可重试的持久化缺口）
        Mockito.verify(enrichmentJobService).enqueue(eqValue(writtenDocId), eqValue("EUROPE_PMC"), eqValue(77L))

        // 重放同一页：已是重复命中 → 只按匹配文档真实 _id 补建任务，不算新增、不重写专家
        val replay = createService()
        stubStoredCheckpoint("EUROPE_PMC", "C1", criteria)
        DiscoveryMockHelper.stubSearchPapersSequence(
            europePmc, page, PaperSearchResult(emptyList(), null, 4)
        )
        DiscoveryMockHelper.stubValidateEmail(
            emailValidationService, "replay@example.com", EmailValidationResult(2, true)
        )
        stubDedupHit(writtenDocId)
        // 第二轮数据库恢复：同一个幂等入队这次成功
        Mockito.doNothing().`when`(enrichmentJobService)
            .enqueue(Mockito.anyString(), Mockito.anyString(), Mockito.any())
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)
        Mockito.doReturn(77L).`when`(progressStore).getCurrentExecutionId("EXPERT_DISCOVERY")

        val (replayResult, _) = runAndCapture(replay, criteria)

        assertEquals(0, replayResult.stats.indexed, "重放不得重复计新增")
        assertEquals(1, replayResult.stats.duplicates)
        Mockito.verify(enrichmentJobService, Mockito.times(2))
            .enqueue(eqValue(writtenDocId), eqValue("EUROPE_PMC"), eqValue(77L))
        assertEquals("C2", storedCheckpointFor("EUROPE_PMC", criteria).cursor, "补建任务成功后检查点才推进")
    }

    @Test
    fun `补全批次领取上限钳制到 100 条（I-2）`() {
        val props = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 200, autoEnrichmentBatchSize = 500
        )
        val svc = createService(props)
        Mockito.doReturn(Mockito.mock(OpenAlexDataSource::class.java)).`when`(openAlexProvider).getIfAvailable()
        Mockito.doReturn(emptyList<ExpertAcademicEnrichmentJob>())
            .`when`(enrichmentJobService).claimDue(Mockito.anyInt(), anyLocalDateTime())

        val claimed = svc.claimDueEnrichmentJobs(props.autoEnrichmentBatchSize)

        assertTrue(claimed.isEmpty(), "配置超上限时也只是一次领取，不是每日总量")
        Mockito.verify(enrichmentJobService).claimDue(eqValue(100), anyLocalDateTime())
    }

    @Test
    fun `OpenAlex 未启用时不领取任何任务（不烧任务重试预算）`() {
        val svc = createService()

        val claimed = svc.claimDueEnrichmentJobs(100)

        assertTrue(claimed.isEmpty())
        Mockito.verify(enrichmentJobService, Mockito.never()).claimDue(Mockito.anyInt(), anyLocalDateTime())
    }

    @Test
    fun `DISCOVERY_PENDING 领取到期任务，只对成功且 RAW-only 的专家定向复评（I-3 I-4）`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        val jobs = listOf(
            enrichmentJob(1L, "0000-RAWONLY", "EUROPE_PMC", "token-1"),
            enrichmentJob(2L, "0000-CAND", "ORCID", "token-2")
        )
        Mockito.doReturn(jobs).`when`(enrichmentJobService).claimDue(Mockito.anyInt(), anyLocalDateTime())
        Mockito.doReturn(listOf(c6Expert("0000-RAWONLY", esDocId = "0000-RAWONLY"), c6Expert("0000-CAND", esDocId = "0000-CAND")))
            .`when`(expertSearchService).findByDocumentIds(eqValue(ExpertIndexLevel.RAW), Mockito.anyList())
        Mockito.doReturn(
            mapOf(
                "0000-RAWONLY" to EnrichmentOutcome.Success(AuthorEnrichment(hIndex = 9, citationCount = 90, worksCount = 4)),
                "0000-CAND" to EnrichmentOutcome.Success(AuthorEnrichment(hIndex = 5, citationCount = 50, worksCount = 3))
            )
        ).`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.HISTORY_ENRICHMENT))
        stubLayerPresence(candidateDocs = setOf("0000-CAND"))
        stubAcademicUpdateOk()
        Mockito.doReturn(PromotionOutcome.Promoted)
            .`when`(revalidationService).revalidateEnrichedRaw(eqValue("0000-RAWONLY"))
        Mockito.doReturn(true)
            .`when`(enrichmentJobService).complete(Mockito.anyLong(), Mockito.anyString(), anyOutcome())
        val captured = mutableListOf<TaskProgress>()
        DiscoveryMockHelper.captureProgressUpdates(progressStore, captured)

        val result = svc.enrichExistingExperts(EnrichmentScope.DISCOVERY_PENDING)

        assertEquals(2, result.enriched)
        assertEquals(0, result.failed)
        Mockito.verify(enrichmentJobService).complete(eqValue(1L), eqValue("token-1"), anyOutcome())
        Mockito.verify(enrichmentJobService).complete(eqValue(2L), eqValue("token-2"), anyOutcome())
        Mockito.verify(revalidationService, Mockito.times(1)).revalidateEnrichedRaw(eqValue("0000-RAWONLY"))
        Mockito.verify(revalidationService, Mockito.never()).revalidateEnrichedRaw(eqValue("0000-CAND"))

        // I-4：逐源入队/成功/待补/未匹配计数进既有进度 details
        val batchProgress = captured.last { it.details?.containsKey("bySource") == true }
        @Suppress("UNCHECKED_CAST")
        val bySource = batchProgress.details!!["bySource"] as Map<String, Map<String, Int>>
        assertEquals(1, bySource["EUROPE_PMC"]?.get("enqueued"))
        assertEquals(1, bySource["EUROPE_PMC"]?.get("succeeded"))
        assertEquals(1, bySource["ORCID"]?.get("succeeded"))
        assertEquals(0, bySource["EUROPE_PMC"]?.get("failed"))

        // 附加计数同时出现在 stats 响应里（历史任务详情仍是当时快照）
        assertEquals(2, svc.getEnrichmentStats().autoEnrichment?.succeeded)
    }

    @Test
    fun `额度延期只把任务记为待补而不算失败（I-2 I-5）`() {
        val svc = createService()
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        val job = enrichmentJob(1L, "0000-0001", "OPENALEX", "token-1")
        Mockito.doReturn(listOf(c6Expert("0000-0001", esDocId = "0000-0001")))
            .`when`(expertSearchService).findByDocumentIds(eqValue(ExpertIndexLevel.RAW), Mockito.anyList())
        val resetAt = Instant.parse("2026-09-22T00:00:00Z")
        Mockito.doThrow(OpenAlexBudgetDeferredException(resetAt))
            .`when`(openAlex).batchEnrichByOrcids(Mockito.anyList(), eqValue(RequestKind.NEW_ENRICHMENT))
        Mockito.doReturn(true)
            .`when`(enrichmentJobService).complete(Mockito.anyLong(), Mockito.anyString(), anyOutcome())

        val result = svc.processClaimedEnrichmentJobBatch(listOf(job), RequestKind.NEW_ENRICHMENT)

        assertEquals(0, result.succeeded)
        assertEquals(0, result.failed)
        assertEquals(1, result.pending, "额度延期是待补，不是失败")
        assertTrue(result.budgetDeferred)
        assertEquals(resetAt.toString(), result.deferredUntil)
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
        val captor = ArgumentCaptor.forClass(ProfileEnrichmentOutcome::class.java)
        Mockito.verify(enrichmentJobService).complete(
            eqValue(1L), eqValue("token-1"), captor.capture() ?: ProfileEnrichmentOutcome.NoId
        )
        val outcome = captor.value
        assertInstanceOf(ProfileEnrichmentOutcome.Deferred::class.java, outcome)
        assertEquals(resetAt, (outcome as ProfileEnrichmentOutcome.Deferred).resetAt)
    }

    @Test
    fun `RAW 文档整体读不到时不写任何任务终态，任务留给租约恢复（I-2）`() {
        val svc = createService()
        Mockito.doReturn(Mockito.mock(OpenAlexDataSource::class.java)).`when`(openAlexProvider).getIfAvailable()
        val job = enrichmentJob(1L, "0000-0001", "EUROPE_PMC", "token-1")
        Mockito.doReturn(emptyList<ExpertProfile>())
            .`when`(expertSearchService).findByDocumentIds(eqValue(ExpertIndexLevel.RAW), Mockito.anyList())

        val ex = assertThrows(IllegalStateException::class.java) {
            svc.processClaimedEnrichmentJobBatch(listOf(job), RequestKind.NEW_ENRICHMENT)
        }

        assertTrue(ex.message!!.contains("RAW 文档读取失败"))
        Mockito.verify(enrichmentJobService, Mockito.never()).complete(Mockito.anyLong(), Mockito.anyString(), anyOutcome())
    }

    // ---------------- c9: 运行级公平额度、全局上限与时间预算 ----------------

    /** c9：模拟论文源的运行级配置（本源上限）+ 记录每次真实请求条件的固定页。 */
    private fun stubPaperSourceRuns(
        source: AcademicDataSource,
        cap: Int,
        page: PaperSearchResult
    ): MutableList<PaperSearchCriteria> {
        DiscoveryMockHelper.stubMaxPapersPerSource(source, cap)
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(source, "NO_EMAIL_IN_FULLTEXT")
        return stubAndRecordRequests(source, page)
    }

    /** c9：一页 [count] 篇（[offset] 让各来源的 paperId 不互相覆盖），[nextCursor] 为 null 表示该来源穷尽。 */
    private fun pageOf(count: Int, nextCursor: String?, offset: Int = 0): PaperSearchResult =
        PaperSearchResult(
            (1..count).map { paper("PMC${offset + it}", "Paper ${offset + it}") },
            nextCursor,
            count.toLong()
        )

    /** c9：四个启用论文源（EPMC 恒在），本源上限给定，且都返回同一种页。 */
    private fun enableFourPaperSources(
        cap: Int,
        page: PaperSearchResult
    ): Map<String, MutableList<PaperSearchCriteria>> {
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        val crossref = Mockito.mock(CrossrefDataSource::class.java)
        val arxiv = Mockito.mock(ArxivDataSource::class.java)
        stubSource(openAlex, "OPENALEX")
        stubSource(crossref, "CROSSREF")
        stubSource(arxiv, "ARXIV")
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        Mockito.doReturn(crossref).`when`(crossrefProvider).getIfAvailable()
        Mockito.doReturn(arxiv).`when`(arxivProvider).getIfAvailable()
        return mapOf(
            "EUROPE_PMC" to stubPaperSourceRuns(europePmc, cap, page),
            "OPENALEX" to stubPaperSourceRuns(openAlex, cap, page),
            "CROSSREF" to stubPaperSourceRuns(crossref, cap, page),
            "ARXIV" to stubPaperSourceRuns(arxiv, cap, page)
        )
    }

    private val fourSourceCriteria =
        PaperSearchCriteria(sources = listOf("EUROPE_PMC", "OPENALEX", "CROSSREF", "ARXIV"))

    @Test
    fun `公平额度：全局上限恰好覆盖各来源一页时每源各发一次请求（I-1 I-2）`() {
        // A-1 等价：globalCap=400 = 四源各一页 100；不是第一个来源独占 400。
        val props = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 400, maxAuthorsPerRun = 2_000, includeRawScan = false
        )
        val svc = createService(props)
        val requests = enableFourPaperSources(cap = 200, page = pageOf(100, "NEXT"))
        val captured = mutableListOf<TaskProgress>()
        DiscoveryMockHelper.captureProgressUpdates(progressStore, captured)

        val result = svc.discover(fourSourceCriteria, "TEST", includeRawScan = false)

        assertEquals(400, result.stats.totalPapers, "运行级目标 400 与单页 100 是两件事（I-1）")
        assertEquals(100, result.stats.bySource["OPENALEX"]?.papersSearched, "主源必须让出后来源的基础份额")
        requests.forEach { (name, seen) ->
            assertEquals(1, seen.size, "$name 必须恰好发出进入第一页的一次请求")
            assertEquals(100, result.stats.bySource[name]?.papersSearched, "$name 的基础份额是一页")
            assertEquals(100, result.stats.bySource[name]?.runBudget, "$name 的 run 额度必须是 min(cap, 全局剩余-保留)")
        }
        // 报告边界：run 额度与计量单位进既有进度 details（不加列、不改前端）
        val lastDetails = captured.last { it.details?.containsKey("bySource") == true }.details!!
        @Suppress("UNCHECKED_CAST")
        val reported = lastDetails["bySource"] as Map<String, Map<String, Any>>
        assertEquals(100, reported["OPENALEX"]?.get("runBudget"))
        assertEquals(SourceUnit.PAPER.name, reported["OPENALEX"]?.get("unit"))
        assertEquals(0, result.stats.sourceFailures)
    }

    @Test
    fun `穷尽来源释放的剩余额度由后来源在各自上限内用尽（I-2）`() {
        val props = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 400, maxAuthorsPerRun = 2_000, includeRawScan = false
        )
        val svc = createService(props)
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        val crossref = Mockito.mock(CrossrefDataSource::class.java)
        val arxiv = Mockito.mock(ArxivDataSource::class.java)
        stubSource(openAlex, "OPENALEX")
        stubSource(crossref, "CROSSREF")
        stubSource(arxiv, "ARXIV")
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        Mockito.doReturn(crossref).`when`(crossrefProvider).getIfAvailable()
        Mockito.doReturn(arxiv).`when`(arxivProvider).getIfAvailable()
        // EPMC 只有 40 篇且无下一页 = 真实穷尽，释放 60 篇基础份额给后来源。
        stubPaperSourceRuns(europePmc, 200, pageOf(40, null))
        val openAlexRequests = stubPaperSourceRuns(openAlex, 200, pageOf(100, "NEXT-OPENALEX", offset = 100))
        stubPaperSourceRuns(crossref, 200, pageOf(100, "NEXT-CROSSREF", offset = 200))
        stubPaperSourceRuns(arxiv, 200, pageOf(100, "NEXT-ARXIV", offset = 300))

        val result = svc.discover(fourSourceCriteria, "TEST", includeRawScan = false)

        assertEquals(400, result.stats.totalPapers, "释放的额度必须被用尽，且不得越过全局上限")
        assertEquals(40, result.stats.bySource["EUROPE_PMC"]?.papersSearched)
        assertEquals(DiscoveryStopReason.EXHAUSTED, result.stats.bySource["EUROPE_PMC"]?.stopReason)
        assertEquals(160, result.stats.bySource["OPENALEX"]?.runBudget, "释放的 60 篇进入同一次运行的后来源配额")
        assertEquals(160, result.stats.bySource["OPENALEX"]?.papersSearched)
        assertEquals(2, openAlexRequests.size, "160 篇 = 一页 + 半页，半页不推进游标")
        assertEquals(100, result.stats.bySource["CROSSREF"]?.papersSearched, "后来源仍拿到基础份额")
        assertEquals(100, result.stats.bySource["ARXIV"]?.papersSearched)
        assertEquals(
            DiscoveryStopReason.GLOBAL_PAPER_LIMIT,
            result.stats.bySource["ARXIV"]?.stopReason,
            "全局 cap 到界必须自己命名，不能算成来源失败"
        )
    }

    @Test
    fun `全局上限不足各来源基础份额时启动校验失败且不发任何请求（I-1）`() {
        val props = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 300, maxAuthorsPerRun = 2_000, includeRawScan = false
        )
        val svc = createService(props)
        val requests = enableFourPaperSources(cap = 200, page = pageOf(100, "NEXT"))

        val error = assertThrows(IllegalArgumentException::class.java) {
            svc.discover(fourSourceCriteria, "TEST", includeRawScan = false)
        }

        assertTrue(error.message!!.contains("EXPERT_DISCOVERY_MAX_PAPERS"),
            "配置错误必须指向可调旋钮：${error.message}")
        assertTrue(error.message!!.contains("400"), "必须报出各来源基础份额合计：${error.message}")
        requests.forEach { (name, seen) -> assertTrue(seen.isEmpty(), "$name 在启动校验失败时不得发请求") }
        Mockito.verify(cursorRepository, Mockito.never()).save(Mockito.any(DiscoverySourceCursor::class.java))
    }

    @Test
    fun `手动只选 arXiv 时全额使用自身额度且不碰 OpenAlex（I-2 V-2）`() {
        val props = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 400, maxAuthorsPerRun = 2_000, includeRawScan = false
        )
        val svc = createService(props)
        val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        stubSource(openAlex, "OPENALEX")
        Mockito.doReturn(openAlex).`when`(openAlexProvider).getIfAvailable()
        val openAlexRequests = stubPaperSourceRuns(openAlex, 200, pageOf(100, "NEXT-OPENALEX"))
        val arxiv = Mockito.mock(ArxivDataSource::class.java)
        stubSource(arxiv, "ARXIV")
        Mockito.doReturn(arxiv).`when`(arxivProvider).getIfAvailable()
        val arxivRequests = stubPaperSourceRuns(arxiv, 2_000, pageOf(100, "NEXT-ARXIV"))

        val result = svc.discover(PaperSearchCriteria(sources = listOf("ARXIV")), "TEST", includeRawScan = false)

        assertEquals(setOf("ARXIV"), result.stats.bySource.keys, "人工少源任务只分配所选来源")
        assertEquals(400, result.stats.bySource["ARXIV"]?.runBudget)
        assertEquals(400, result.stats.bySource["ARXIV"]?.papersSearched, "手选单源不受其他来源保留份额影响")
        assertEquals(4, arxivRequests.size)
        assertTrue(openAlexRequests.isEmpty(), "未被选中的 OpenAlex 不得发出任何请求")
    }

    @Test
    fun `时间预算到点停在进入页并记录 TIME_BUDGET（I-3）`() {
        val props = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 1_000, maxAuthorsPerRun = 2_000,
            includeRawScan = false, timeBudget = Duration.ofMillis(1_000)
        )
        val svc = createService(props)
        val criteria = PaperSearchCriteria()
        stubStoredCheckpoint("EUROPE_PMC", "C1", criteria)
        val seen = mutableListOf<PaperSearchCriteria>()
        val page1 = pageOf(100, "C2")
        val page2 = pageOf(100, "C3", offset = 100)
        Mockito.doAnswer { invocation ->
            seen.add(invocation.getArgument(0) as PaperSearchCriteria)
            // 第二页请求本身耗时超过预算：到点必须停在进入该页的 C2，不得推进到 C3。
            if (seen.size > 1) Thread.sleep(1_500)
            if (seen.size == 1) page1 else page2
        }.`when`(europePmc).searchPapers(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
        DiscoveryMockHelper.stubExtractAuthorEmailsEmpty(europePmc, "NO_EMAIL_IN_FULLTEXT")

        val (result, _) = runAndCapture(svc, criteria)

        assertEquals(listOf("C1", "C2"), seen.map { it.cursor }, "时间预算到点后不得再发出第三次请求")
        assertEquals(100, result.stats.totalPapers, "超时那一页不得被算成已消费")
        val decoded = storedCheckpointFor("EUROPE_PMC", criteria)
        assertEquals("C2", decoded.cursor, "超时必须停在进入该页的 cursor，绝不推进到 C3")
        assertFalse(decoded.exhausted, "时间预算不是穷尽")
        assertEquals(DiscoveryStopReason.TIME_BUDGET, result.stats.bySource["EUROPE_PMC"]?.stopReason)
        assertEquals(1, result.stats.pendingSources, "时间预算停止仍有可续跑工作")
    }

    @Test
    fun `0 的配置不会被当成无限量（I-1）`() {
        val zeroGlobal = createService(
            ExpertDiscoveryProperties(enabled = true, maxPapersPerRun = 0, maxAuthorsPerRun = 200, includeRawScan = false)
        )
        val globalError = assertThrows(IllegalArgumentException::class.java) {
            zeroGlobal.discover(PaperSearchCriteria(), "TEST")
        }
        assertTrue(globalError.message!!.contains("全局论文上限"), "必须报清楚是哪个旋钮：${globalError.message}")

        val zeroTime = createService(
            ExpertDiscoveryProperties(
                enabled = true, maxPapersPerRun = 400, maxAuthorsPerRun = 200,
                includeRawScan = false, timeBudget = Duration.ZERO
            )
        )
        val timeError = assertThrows(IllegalArgumentException::class.java) {
            zeroTime.discover(PaperSearchCriteria(), "TEST")
        }
        assertTrue(timeError.message!!.contains("时间预算"), "必须报清楚是哪个旋钮：${timeError.message}")
        Mockito.verify(europePmc, Mockito.never())
            .searchPapers(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
    }

    @Test
    fun `本源上限为 0 的来源不发请求也不计论文（I-1）`() {
        val props = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 400, maxAuthorsPerRun = 2_000, includeRawScan = false
        )
        val svc = createService(props)
        val requests = stubPaperSourceRuns(europePmc, 0, pageOf(1, "NEXT"))

        val result = svc.discover(PaperSearchCriteria(), "TEST", includeRawScan = false)

        assertEquals(0, result.stats.bySource["EUROPE_PMC"]?.runBudget)
        assertEquals(0, result.stats.totalPapers, "0 不是无限量")
        assertTrue(requests.isEmpty(), "额度为 0 的来源不得发出请求")
        assertEquals(DiscoveryStopReason.SOURCE_LIMIT, result.stats.bySource["EUROPE_PMC"]?.stopReason)
    }

    @Test
    fun `ORCID 记录与论文数在汇总里分列且限额独立（I-2）`() {
        val props = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 400, maxAuthorsPerRun = 200, includeRawScan = false
        )
        val svc = createService(props)
        stubPaperSourceRuns(europePmc, 500, pageOf(2, null))
        val orcid = Mockito.mock(OrcidDataSource::class.java)
        Mockito.doReturn(orcid).`when`(orcidProvider).getIfAvailable()
        DiscoveryMockHelper.stubOrcidSourceName(orcid)
        Mockito.doCallRealMethod().`when`(orcid).orcidRecordToAuthorEmails((Mockito.any(OrcidDataSource.OrcidRecord::class.java) ?: OrcidDataSource.OrcidRecord("test", "A", "B", emptyList(), null, null)))
        DiscoveryMockHelper.stubOrcidMaxRecordsPerRun(orcid, 1_000)
        val records = (1..3).map {
            OrcidDataSource.OrcidRecord(
                orcidId = "0000-000$it", givenNames = "Test", familyNames = "$it",
                emails = listOf("test$it@example.com"), institutionName = "Univ", country = null
            )
        }
        stubOrcid(orcid, records)
        for (i in 1..3) {
            DiscoveryMockHelper.stubValidateEmail(
                emailValidationService, "test$i@example.com", EmailValidationResult(2, true)
            )
        }
        DiscoveryMockHelper.stubEsDedupSearch(restTemplate, 0)
        DiscoveryMockHelper.stubIndexToRaw(indexWriterService, true)
        DiscoveryMockHelper.stubEsCandidatePut(restTemplate, true)
        DiscoveryMockHelper.stubEligibilityTrue(eligibilityService)

        val result = svc.discover(
            PaperSearchCriteria(sources = listOf("EUROPE_PMC", "ORCID")), "TEST", includeRawScan = false
        )

        val orcidStats = result.stats.bySource["ORCID"]!!
        assertEquals(SourceUnit.RECORD, orcidStats.unit)
        assertEquals(1_000, orcidStats.runBudget, "ORCID 记录限额独立于论文全局上限，不是 400")
        assertEquals(3, orcidStats.papersSearched)
        assertEquals(2, result.stats.bySource["EUROPE_PMC"]?.papersSearched)
        assertEquals(SourceUnit.PAPER, result.stats.bySource["EUROPE_PMC"]?.unit)
        val summary = result.summaryText!!
        assertTrue(summary.contains("论文 2"), "论文数不得混入 ORCID 记录：$summary")
        assertTrue(summary.contains("ORCID 记录 3"), "ORCID 记录必须单列：$summary")
    }

    private fun stubAcademicUpdateOk() {
        Mockito.doReturn(ResponseEntity.ok(objectMapper.createObjectNode()) as ResponseEntity<*>)
            .`when`(restTemplate).exchange(
                Mockito.anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
    }
}
