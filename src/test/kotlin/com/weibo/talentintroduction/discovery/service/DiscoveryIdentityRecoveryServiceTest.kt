package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.config.OpenAlexBudgetDeferredException
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.IdentityVerification
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertPromotionAuditService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

/**
 * 02（I-1/I-2/I-3）：真实 [ExpertIndexWriterService] + HTTP 替身 —— 跨路径回源、空名条件写入、
 * 冲突/旧学术保护、hash 变化 409 与部分写入。**不 mock writer**：新增的「只填空姓名 + proof」条件补丁
 * 本身就是被验收的事实。
 */
class DiscoveryIdentityRecoveryServiceTest {

    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val mapper = ObjectMapper()
    private val properties = ElasticsearchProperties(
        baseUrl = "https://es.example.com:9200",
        username = "elastic", password = "secret",
        rawIndexName = "orcid_info",
        candidateIndexName = "orcid_info_candidate",
        applicationIndexName = "orcid_info_application"
    )
    private val indexService = ExpertIndexService(properties, restTemplate, mapper)
    private val writer = ExpertIndexWriterService(
        restTemplate, properties, indexService, mapper,
        Mockito.mock(ExpertPromotionAuditService::class.java),
        Mockito.mock(ExpertContactRepository::class.java)
    )
    private val repairService = DiscoveryIdentityRepairService(writer)
    private val europePmc = Mockito.mock(EuropePmcDataSource::class.java)
    private val openAlex = Mockito.mock(OpenAlexDataSource::class.java)
    @Suppress("UNCHECKED_CAST")
    private val openAlexProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<OpenAlexDataSource>
    private val aliasService = Mockito.mock(ExpertEmailAliasService::class.java)
    private val contactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val admissionPolicy = Mockito.mock(DiscoveryAdmissionPolicy::class.java)
    private val reviewService = Mockito.mock(DiscoveryReviewService::class.java)
    private val personRegistrationService = Mockito.mock(DiscoveryPersonRegistrationService::class.java)
    private val service = DiscoveryIdentityRecoveryService(
        writer, repairService, europePmc, openAlexProvider, aliasService,
        contactRepository, admissionPolicy, reviewService, personRegistrationService = personRegistrationService
    )

    private class LayerDoc(var source: MutableMap<String, Any?>, var seq: Long)

    private val docs = linkedMapOf<String, LayerDoc>()
    private val updateUrls = mutableListOf<String>()
    private val updateBodies = mutableListOf<Map<String, Any?>>()
    private var failUpdateLevel: String? = null

    private fun key(level: ExpertIndexLevel, id: String) = "${level.name}/$id"

    private fun levelOf(indexName: String): ExpertIndexLevel? =
        ExpertIndexLevel.values().firstOrNull { indexService.indexName(it) == indexName }

    private fun put(level: ExpertIndexLevel, id: String, source: MutableMap<String, Any?>, seq: Long = 1L) {
        docs[key(level, id)] = LayerDoc(source, seq)
    }

    /**
     * `Mockito.any()` 对 Kotlin 非空参数会返回 null 并在调用点触发 null 检查；用真实占位值注册匹配器
     * （既有测试同款手法）。
     */
    private fun <T> anyNonNull(value: T): T {
        Mockito.any<T>()
        return value
    }

    /** 同 [anyNonNull]：`Mockito.eq(...)` 对 Kotlin 非空参数返回 null，用真实值占位。 */
    private fun <T> eqNonNull(value: T): T {
        Mockito.eq(value)
        return value
    }

    private val profilePlaceholder = com.weibo.talentintroduction.expert.domain.ExpertProfile(
        orcidId = "", email = null, givenNames = null, familyNames = null,
        country = null, keyword = null, employment = null
    )

    private val paperPlaceholder = PaperMetadata(
        pmcId = null, pmid = null, doi = null, title = "", pubYear = 0,
        journal = null, authors = emptyList(), source = "PLACEHOLDER"
    )

    @BeforeEach
    fun setUp() {
        Mockito.`when`(openAlexProvider.getIfAvailable()).thenReturn(openAlex)
        Mockito.`when`(reviewService.resolveAdmission(Mockito.anyString(), anyNonNull(profilePlaceholder)))
            .thenReturn(DiscoveryReviewResolvedAdmission("", null, false, false, false, false, 0L, null))
        Mockito.`when`(reviewService.recordAutomaticConclusion(Mockito.anyString(), anyNonNull(profilePlaceholder), anyNonNull(automaticPassed())))
            .thenReturn(DiscoveryReviewAdmissionOutcome("", "AUTO_PASSED", true, false, true, 1L, emptyList()))
        Mockito.`when`(admissionPolicy.evaluate(anyNonNull(profilePlaceholder)))
            .thenReturn(automaticPassed())
        Mockito.doAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            val method = invocation.getArgument<HttpMethod>(1)
            val body = (invocation.getArgument<Any?>(2) as? HttpEntity<*>)?.body
            when {
                method == HttpMethod.POST && url.contains("/_search") -> searchResponse(body)
                method == HttpMethod.GET && url.contains("/_doc/") -> getResponse(url)
                method == HttpMethod.POST && url.contains("/_update/") -> updateResponse(url, body)
                else -> throw IllegalStateException("unexpected request $method $url")
            }
        }.`when`(restTemplate).exchange(
            Mockito.anyString(), Mockito.any(HttpMethod::class.java), Mockito.any(), Mockito.eq(JsonNode::class.java)
        )
        Mockito.`when`(europePmc.fetchFullTextXml(anyNonNull("PMC1234567")))
            .thenReturn(jatsXml())
    }

    @Suppress("UNCHECKED_CAST")
    private fun searchResponse(body: Any?): ResponseEntity<JsonNode> {
        val query = body as Map<String, Any?>
        val term = (query["query"] as Map<String, Any?>)["term"] as Map<String, Any?>
        val email = term["email"]
        val hits = docs.entries.mapNotNull { (storedKey, doc) ->
            val level = ExpertIndexLevel.valueOf(storedKey.substringBefore('/'))
            val id = storedKey.substringAfter('/')
            if (doc.source["email"] == email) {
                mapOf("_index" to indexService.indexName(level), "_id" to id, "_source" to doc.source)
            } else null
        }
        return ResponseEntity.ok(mapper.valueToTree(mapOf(
            "hits" to mapOf("total" to mapOf("value" to hits.size), "hits" to hits)
        )))
    }

    private fun getResponse(url: String): ResponseEntity<JsonNode> {
        val indexName = url.removePrefix("${properties.baseUrl}/").substringBefore("/_doc/")
        val id = url.substringAfter("/_doc/").substringBefore('?')
        val level = levelOf(indexName) ?: throw IllegalStateException("unknown index $indexName")
        val doc = docs[key(level, id)] ?: throw HttpClientErrorException(HttpStatus.NOT_FOUND)
        return ResponseEntity.ok(mapper.valueToTree(mapOf(
            "_source" to doc.source, "_seq_no" to doc.seq, "_primary_term" to 1L
        )))
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateResponse(url: String, body: Any?): ResponseEntity<JsonNode> {
        updateUrls += url
        val indexName = url.removePrefix("${properties.baseUrl}/").substringBefore("/_update/")
        val id = url.substringAfter("/_update/").substringBefore('?')
        val level = levelOf(indexName) ?: throw IllegalStateException("unknown index $indexName")
        val doc = (body as Map<String, Any?>)["doc"] as Map<String, Any?>
        updateBodies += doc
        if (failUpdateLevel == level.name) throw HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR)
        val stored = docs[key(level, id)] ?: throw HttpClientErrorException(HttpStatus.NOT_FOUND)
        val ifSeqNo = url.substringAfter("if_seq_no=").substringBefore('&').toLong()
        if (ifSeqNo != stored.seq) throw HttpClientErrorException(HttpStatus.CONFLICT)
        doc.forEach { (field, value) -> stored.source[field] = value }
        stored.seq += 1
        return ResponseEntity.ok(mapper.valueToTree(mapOf(
            "result" to "updated", "_seq_no" to stored.seq, "_primary_term" to 1L
        )))
    }

    // ── 夹具 ────────────────────────────────────────────────────────────────

    /**
     * 最小可解析 JATS：`article-id[pub-id-type=pmcid]` 声明 PMC 编号 + `contrib` 直挂邮箱，
     * 由**真实** [JatsXmlEmailParser] 解析；`given=null` 时作者缺完整姓名 → 无有效证据。
     */
    private fun jatsXml(
        pmcId: String = "PMC1234567",
        email: String = "jane@uni.edu",
        given: String? = "Jane",
        family: String? = "Doe",
        orcid: String? = null
    ): ByteArray {
        val name = buildString {
            append("<name>")
            if (family != null) append("<surname>$family</surname>")
            if (given != null) append("<given-names>$given</given-names>")
            append("</name>")
        }
        val contributorId =
            if (orcid != null) "<contrib-id contrib-id-type=\"orcid\">https://orcid.org/$orcid</contrib-id>" else ""
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <article><front><article-meta>
              <article-id pub-id-type="pmcid">$pmcId</article-id>
              <contrib-group content-type="author">
                <contrib contrib-type="author">$name$contributorId<email>$email</email></contrib>
              </contrib-group>
            </article-meta></front></article>
        """.trimIndent().toByteArray()
    }

    /** 归档真实原字节（`PMC7759461.xml`，article-id=PMC7759461），SHA256 见证据报告。 */
    private fun archivedPmcFixture(): ByteArray {
        val archive = requireNotNull(javaClass.getResourceAsStream("/discovery/xml-route-recall.zip")).use { it.readBytes() }
        return java.util.zip.ZipInputStream(archive.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name.endsWith("round2/PMC7759461.xml") }
            zip.readBytes()
        }
    }

    /** 真实 [EuropePmcDataSource] + 仅替身 HTTP 边界（返回归档原字节）。 */
    private fun realPmcDataSource(bytes: ByteArray): EuropePmcDataSource {
        val http = Mockito.mock(RestTemplate::class.java)
        Mockito.`when`(http.getForObject(Mockito.anyString(), eqNonNull(ByteArray::class.java))).thenReturn(bytes)
        return EuropePmcDataSource(http, EuropePmcProperties(requestDelayMs = 0, maxRetries = 0))
    }

    /** 换用另一来源边的等价服务实例（共享同一 writer/接缝），用于真实 URL 抓取路径。 */
    private fun serviceWith(pmc: EuropePmcDataSource) = DiscoveryIdentityRecoveryService(
        writer, repairService, pmc, openAlexProvider, aliasService,
        contactRepository, admissionPolicy, reviewService, personRegistrationService = personRegistrationService
    )

    private fun discoveredSource(
        email: String = "jane@uni.edu",
        given: String? = null,
        family: String? = null,
        externalIds: Map<String, String> = mapOf("pmcId" to "PMC1234567"),
        proof: IdentityVerification? = null,
        extra: Map<String, Any?> = emptyMap()
    ): MutableMap<String, Any?> = mutableMapOf<String, Any?>(
        "email" to email, "givenNames" to given, "familyNames" to family,
        "emailSource" to "PAPER_FULLTEXT", "tags" to listOf("discovered"),
        "externalIds" to externalIds
    ).apply {
        if (proof != null) put("identityVerification", proof)
        putAll(extra)
    }

    private fun automaticPassed(): com.weibo.talentintroduction.discovery.domain.AutomaticAdmissionResult =
        com.weibo.talentintroduction.discovery.domain.AutomaticAdmissionResult(
            com.weibo.talentintroduction.discovery.domain.DiscoveryAdmissionStatus.AUTO_PASSED,
            emptyList(), emptyList(), "test",
            java.time.Instant.parse("2026-10-13T00:00:00Z"),
            com.weibo.talentintroduction.discovery.domain.AdmissionConfigSnapshot(
                requireOrcid = false, requireValidEmail = false, requireDoctoralDegree = false,
                excludeChineseNationality = false, enableAgeFilter = false, maxAgeExclusive = 0,
                enableHIndexFilter = false, minHIndex = 0, enableCitationFilter = false,
                minCitationCount = 0, enableActivityFilter = false, recentYearsThreshold = 0
            )
        )

    private fun previewOf(status: RecoveryStatus = RecoveryStatus.RECOVERABLE): RecoveryPreview {
        val preview = service.preview("EMAIL-jane@uni.edu")
        assertEquals(status, preview.status, "preview=${preview.reason} blocked=${preview.blockedReasons}")
        return preview
    }

    // ── I-1/I-2/I-3 §跨路径恢复 ────────────────────────────────────────────

    @Test
    fun `empty-name discovery is recovered from the precise source across layers`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        put(ExpertIndexLevel.CANDIDATE, id, discoveredSource())

        val preview = previewOf()
        assertNotNull(preview.snapshotHash)
        assertNotNull(preview.proposalHash)
        assertTrue(preview.layers.first { it.level == ExpertIndexLevel.RAW }.present)
        assertEquals(2, preview.layers.count { it.present })
        assertEquals("Jane", preview.proposedGivenNames)
        assertEquals("JATS_SHA256", preview.evidenceSource)

        val applied = service.apply(id, preview.snapshotHash, preview.proposalHash)

        assertEquals(RecoveryApplyStatus.APPLIED, applied.status, "reason=${applied.reason}")
        assertEquals(2, updateUrls.size)
        assertTrue(updateUrls.all { it.contains("if_seq_no=") && it.contains("if_primary_term=1") }, updateUrls.toString())
        assertTrue(updateBodies.all {
            it.containsKey("givenNames") && it.containsKey("familyNames") && it.containsKey("identityVerification")
        }, updateBodies.toString())
        assertEquals("Jane", docs[key(ExpertIndexLevel.RAW, id)]!!.source["givenNames"])
        assertEquals("Doe", docs[key(ExpertIndexLevel.CANDIDATE, id)]!!.source["familyNames"])
        // I-2：名字补齐改变 admission identity_hash —— 以既有接缝重算。
        Mockito.verify(reviewService).recordAutomaticConclusion(eqNonNull(id), anyNonNull(profilePlaceholder), anyNonNull(automaticPassed()))
    }

    @Test
    fun `a successful recovery refreshes the person registration (I-1)`() {
        Mockito.doReturn(DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.CREATED))
            .`when`(personRegistrationService).refresh(Mockito.anyString(), Mockito.anyString())
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        put(ExpertIndexLevel.CANDIDATE, id, discoveredSource())
        val preview = previewOf()

        val applied = service.apply(id, preview.snapshotHash, preview.proposalHash)

        assertEquals(RecoveryApplyStatus.APPLIED, applied.status, "reason=${applied.reason}")
        Mockito.verify(personRegistrationService).refresh(anyNonNull("IDENTITY_RECOVERY"), eqNonNull(id))
    }

    @Test
    fun `a preview writes nothing to ES`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        previewOf()
        assertTrue(updateUrls.isEmpty())
        assertTrue(docs.values.all { it.source["identityVerification"] == null })
    }

    @Test
    fun `a changed document between preview and apply returns STALE and writes nothing`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        val preview = previewOf()
        // 预览后档案被改动：seq 推进。
        docs[key(ExpertIndexLevel.RAW, id)]!!.seq += 5

        val applied = service.apply(id, preview.snapshotHash, preview.proposalHash)

        assertEquals(RecoveryApplyStatus.STALE, applied.status)
        assertTrue(updateUrls.isEmpty())
        Mockito.verify(reviewService, Mockito.never()).recordAutomaticConclusion(Mockito.anyString(), anyNonNull(profilePlaceholder), anyNonNull(automaticPassed()))
    }

    @Test
    fun `partial layer failure is reported as PARTIAL and never refreshes admission`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        put(ExpertIndexLevel.CANDIDATE, id, discoveredSource())
        failUpdateLevel = ExpertIndexLevel.RAW.name
        val preview = previewOf()

        val applied = service.apply(id, preview.snapshotHash, preview.proposalHash)

        assertEquals(RecoveryApplyStatus.PARTIAL, applied.status)
        assertTrue(applied.layers.any { it.outcome == IdentityRepairLayerOutcome.WRITE_FAILED })
        assertNull(docs[key(ExpertIndexLevel.RAW, id)]!!.source["identityVerification"])
        assertEquals("Jane", docs[key(ExpertIndexLevel.CANDIDATE, id)]!!.source["givenNames"])
        Mockito.verify(reviewService, Mockito.never()).recordAutomaticConclusion(Mockito.anyString(), anyNonNull(profilePlaceholder), anyNonNull(automaticPassed()))
    }

    // ── I-2 §保护范围 ──────────────────────────────────────────────────────

    @Test
    fun `legacy academic fields block automatic recovery with zero writes`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource(extra = mapOf("hIndex" to 5)))

        val preview = previewOf(RecoveryStatus.NEEDS_MANUAL)

        assertEquals("LEGACY_ACADEMIC_FIELDS", preview.reason)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `an existing proof is never a recovery target`() {
        val id = "EMAIL-jane@uni.edu"
        val proof = IdentityVerification(status = "VERIFIED", version = 1, email = "jane@uni.edu")
        put(ExpertIndexLevel.RAW, id, discoveredSource(proof = proof))

        assertEquals("EXISTING_PROOF", previewOf(RecoveryStatus.NEEDS_MANUAL).reason)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `a conflicting stored name is only suggested for manual handling`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource(given = "Janet"))

        assertEquals("NAME_CONFLICT", previewOf(RecoveryStatus.NEEDS_MANUAL).reason)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `a fully-named document with a missing proof is routed to the 01 repair path`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource(given = "Jane", family = "Doe"))

        assertEquals("ROUTE_TO_REPAIR", previewOf(RecoveryStatus.ROUTE_TO_REPAIR).reason)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `an alias-only contact stops automatic recovery`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        Mockito.`when`(aliasService.findContactByEmailOrAlias("jane@uni.edu"))
            .thenReturn(com.weibo.talentintroduction.campaign.domain.ExpertContact(
                id = 9L, campaignId = 1L, orcidId = "other", expertEmail = "alias@other.edu", expertName = "Jane"
            ))

        assertEquals("CONTACT_PRESENT", previewOf(RecoveryStatus.NEEDS_MANUAL).reason)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `a contact by business key blocks automatic recovery`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        Mockito.`when`(contactRepository.existsByOrcidId(id)).thenReturn(true)

        assertEquals("CONTACT_PRESENT", previewOf(RecoveryStatus.NEEDS_MANUAL).reason)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `a manual admission decision blocks automatic recovery`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        Mockito.`when`(reviewService.resolveAdmission(Mockito.anyString(), anyNonNull(profilePlaceholder)))
            .thenReturn(DiscoveryReviewResolvedAdmission(id, "HOLD", false, true, true, false, 3L, null))

        assertEquals("MANUAL_DECISION", previewOf(RecoveryStatus.NEEDS_MANUAL).reason)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `a conflicting author id from the source stops recovery`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource(externalIds = mapOf("pmcId" to "PMC1234567", "orcid" to "0000-0002-1825-0097")))
        Mockito.`when`(europePmc.fetchFullTextXml(anyNonNull("PMC1234567")))
            .thenReturn(jatsXml(orcid = "0000-0002-1825-0099"))

        assertEquals("AUTHOR_ID_CONFLICT", previewOf(RecoveryStatus.NEEDS_MANUAL).reason)
        assertTrue(updateUrls.isEmpty())
    }

    // ── I-1 §来源与预算 ────────────────────────────────────────────────────

    @Test
    fun `a document without a precise source locator is refused`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource(externalIds = emptyMap()))

        assertEquals("SOURCE_LOCATOR_MISSING", previewOf(RecoveryStatus.SOURCE_LOCATOR_MISSING).reason)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `a source that cannot confirm the target email is not a proof`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        Mockito.`when`(europePmc.fetchFullTextXml(anyNonNull("PMC1234567")))
            .thenReturn(jatsXml(email = "someone-else@uni.edu"))

        assertEquals("SOURCE_NO_TARGET_EMAIL", previewOf(RecoveryStatus.NEEDS_MANUAL).reason)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `an invalid evidence digest is not a proof`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        // 作者缺完整姓名 → 真实解析器不产出有效来源证据。
        Mockito.`when`(europePmc.fetchFullTextXml(anyNonNull("PMC1234567")))
            .thenReturn(jatsXml(given = null))

        assertEquals("SOURCE_EVIDENCE_INVALID", previewOf(RecoveryStatus.NEEDS_MANUAL).reason)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `a deferred budget is reported as DEFERRED not as a rejection`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        Mockito.`when`(europePmc.fetchFullTextXml(anyNonNull("PMC1234567")))
            .thenThrow(OpenAlexBudgetDeferredException(
                com.weibo.talentintroduction.config.DeferredReason.DAILY_BUDGET,
                java.time.Instant.parse("2026-10-14T00:00:00Z")
            ))

        val preview = service.preview(id)
        assertEquals(RecoveryStatus.DEFERRED, preview.status)
        assertEquals("BUDGET_DEFERRED", preview.reason)
        assertTrue(updateUrls.isEmpty())
    }

    // ── I-3/V-4/V-19 §故障显式与续跑检查点 ────────────────────────────────

    @Test
    fun `a protection read failure stops immediately with zero identity writes`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        Mockito.`when`(contactRepository.existsByOrcidId(Mockito.anyString()))
            .thenThrow(RuntimeException("contact store down"))

        val preview = service.preview(id)
        assertEquals(RecoveryStatus.SOURCE_UNAVAILABLE, preview.status)
        assertEquals("CONTACT_CHECK_FAILED", preview.reason)

        val applied = service.apply(id, preview.snapshotHash, preview.proposalHash)
        assertFalse(applied.status == RecoveryApplyStatus.APPLIED)
        assertTrue(updateUrls.isEmpty(), "保护读取异常不得 fail-open 写入身份")
    }

    @Test
    fun `a partial layer failure is resumed from the same checkpoint after the fault clears`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        put(ExpertIndexLevel.CANDIDATE, id, discoveredSource())
        failUpdateLevel = ExpertIndexLevel.RAW.name
        val firstPreview = previewOf()

        val first = service.apply(id, firstPreview.snapshotHash, firstPreview.proposalHash)
        assertEquals(RecoveryApplyStatus.PARTIAL, first.status)
        assertNull(docs[key(ExpertIndexLevel.RAW, id)]!!.source["identityVerification"])
        assertNotNull(docs[key(ExpertIndexLevel.CANDIDATE, id)]!!.source["identityVerification"])

        // 故障解除后按**同一原申请**续跑：已完成层（本次目标值）不再被视为 existing proof。
        failUpdateLevel = null
        val resumedPreview = previewOf()
        val applied = service.apply(id, resumedPreview.snapshotHash, resumedPreview.proposalHash)

        assertEquals(RecoveryApplyStatus.APPLIED, applied.status, "reason=${applied.reason}")
        assertNotNull(docs[key(ExpertIndexLevel.RAW, id)]!!.source["identityVerification"])
        assertEquals(
            1, updateUrls.count { it.contains("/orcid_info_candidate/_update/") },
            "已达目标值的层不重写"
        )
    }

    @Test
    fun `a concurrent third proof value is never overwritten on resume`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        put(ExpertIndexLevel.CANDIDATE, id, discoveredSource())
        failUpdateLevel = ExpertIndexLevel.RAW.name
        val firstPreview = previewOf()
        assertEquals(RecoveryApplyStatus.PARTIAL, service.apply(id, firstPreview.snapshotHash, firstPreview.proposalHash).status)

        // 第三方并发写入一个**非本次目标值**的 proof。
        val thirdParty = IdentityVerification(
            status = "VERIFIED", version = 1, email = "jane@uni.edu",
            source = "ORCID_RECORD_SHA256", evidenceHash = "b".repeat(64)
        )
        docs[key(ExpertIndexLevel.CANDIDATE, id)]!!.source["identityVerification"] = thirdParty
        failUpdateLevel = null
        val writesBefore = updateUrls.size

        val preview = service.preview(id)
        assertEquals(RecoveryStatus.NEEDS_MANUAL, preview.status)
        assertEquals("EXISTING_PROOF", preview.reason)

        val applied = service.apply(id, preview.snapshotHash, preview.proposalHash)
        assertFalse(applied.status == RecoveryApplyStatus.APPLIED)
        assertEquals(writesBefore, updateUrls.size, "第三方值下不得有任何身份写")
        assertEquals(thirdParty, docs[key(ExpertIndexLevel.CANDIDATE, id)]!!.source["identityVerification"], "第三方值不被覆盖")
        assertNull(docs[key(ExpertIndexLevel.RAW, id)]!!.source["identityVerification"])
    }

    @Test
    fun `a person registration failure is resumable from the existing ES facts`() {
        Mockito.`when`(personRegistrationService.refresh(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.FAILED, "REGISTRATION_EXCEPTION"))
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        put(ExpertIndexLevel.CANDIDATE, id, discoveredSource())
        val firstPreview = previewOf()

        val first = service.apply(id, firstPreview.snapshotHash, firstPreview.proposalHash)
        assertEquals(RecoveryApplyStatus.PARTIAL, first.status, "登记失败不得报完整 APPLIED")
        assertEquals("REGISTRATION_FAILED", first.reason)
        assertNotNull(docs[key(ExpertIndexLevel.RAW, id)]!!.source["identityVerification"])
        assertNotNull(docs[key(ExpertIndexLevel.CANDIDATE, id)]!!.source["identityVerification"])

        // 登记故障解除后按同一原申请续跑：层事实已在 ES，只补登记与准入重验。
        Mockito.`when`(personRegistrationService.refresh(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.CREATED, "person-1"))
        val resumedPreview = previewOf()
        val applied = service.apply(id, resumedPreview.snapshotHash, resumedPreview.proposalHash)

        assertEquals(RecoveryApplyStatus.APPLIED, applied.status, "reason=${applied.reason}")
        Mockito.verify(reviewService).recordAutomaticConclusion(eqNonNull(id), anyNonNull(profilePlaceholder), anyNonNull(automaticPassed()))
    }

    @Test
    fun `an admission reverify failure is resumable instead of a complete apply (V-4)`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, discoveredSource())
        put(ExpertIndexLevel.CANDIDATE, id, discoveredSource())
        Mockito.doThrow(RuntimeException("admission service down"))
            .`when`(reviewService)
            .recordAutomaticConclusion(Mockito.anyString(), anyNonNull(profilePlaceholder), anyNonNull(automaticPassed()))
        val firstPreview = previewOf()

        val first = service.apply(id, firstPreview.snapshotHash, firstPreview.proposalHash)

        assertEquals(RecoveryApplyStatus.PARTIAL, first.status, "准入重验失败不得报完整 APPLIED")
        assertEquals("ADMISSION_REVERIFY_FAILED", first.reason)
        assertNotNull(docs[key(ExpertIndexLevel.RAW, id)]!!.source["identityVerification"])
        assertNotNull(docs[key(ExpertIndexLevel.CANDIDATE, id)]!!.source["identityVerification"])

        // 准入故障解除后按同一原申请续跑：层事实已在 ES，只补登记与准入重验。
        Mockito.doReturn(DiscoveryReviewAdmissionOutcome("", "AUTO_PASSED", true, false, true, 1L, emptyList()))
            .`when`(reviewService)
            .recordAutomaticConclusion(Mockito.anyString(), anyNonNull(profilePlaceholder), anyNonNull(automaticPassed()))
        val resumedPreview = previewOf()
        val applied = service.apply(id, resumedPreview.snapshotHash, resumedPreview.proposalHash)

        assertEquals(RecoveryApplyStatus.APPLIED, applied.status, "reason=${applied.reason}")
        assertEquals("AUTO_PASSED", applied.admissionDecision)
    }

    // ── I-1/V-20 §PMCID 与原文编号绑定 ─────────────────────────────────────

    @Test
    fun `an archived PMC article replays positively when the locator matches the declared id`() {
        val email = "stefanv@berkeley.edu"
        val id = "EMAIL-$email"
        put(ExpertIndexLevel.RAW, id, discoveredSource(email = email, externalIds = mapOf("pmcId" to "PMC7759461")))
        val realSourceService = serviceWith(realPmcDataSource(archivedPmcFixture()))

        val preview = realSourceService.preview(id)
        assertEquals(RecoveryStatus.RECOVERABLE, preview.status, "reason=${preview.reason}")
        assertEquals("JATS_SHA256", preview.evidenceSource)

        val applied = realSourceService.apply(id, preview.snapshotHash, preview.proposalHash)
        assertEquals(RecoveryApplyStatus.APPLIED, applied.status, "reason=${applied.reason}")
        assertNotNull(docs[key(ExpertIndexLevel.RAW, id)]!!.source["identityVerification"])
    }

    @Test
    fun `a stored locator the archived JATS does not declare blocks the proof`() {
        val email = "stefanv@berkeley.edu"
        val id = "EMAIL-$email"
        // 存量 locator=PMC1234567，而归档原字节的 article-id 是 PMC7759461。
        put(ExpertIndexLevel.RAW, id, discoveredSource(email = email, externalIds = mapOf("pmcId" to "PMC1234567")))
        val realSourceService = serviceWith(realPmcDataSource(archivedPmcFixture()))

        val preview = realSourceService.preview(id)
        assertEquals(RecoveryStatus.SOURCE_IDENTITY_CONFLICT, preview.status, "reason=${preview.reason}")
        assertEquals("SOURCE_ARTICLE_ID_MISMATCH", preview.reason)

        val applied = realSourceService.apply(id, preview.snapshotHash, preview.proposalHash)
        assertFalse(applied.status == RecoveryApplyStatus.APPLIED)
        assertTrue(updateUrls.isEmpty())
        assertNull(docs[key(ExpertIndexLevel.RAW, id)]!!.source["identityVerification"], "来源编号不符不得签出 proof")
    }
}
