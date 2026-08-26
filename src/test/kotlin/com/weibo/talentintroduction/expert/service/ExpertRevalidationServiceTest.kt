package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.AcademicFilterProperties
import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.config.ExpertClassificationProperties
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.expert.domain.EmailValidationResult
import com.weibo.talentintroduction.expert.domain.PromotionScanResult
import com.weibo.talentintroduction.expert.domain.PromotionScanStats
import com.weibo.talentintroduction.expert.domain.RevalidationResult
import com.weibo.talentintroduction.expert.domain.RevalidationStats
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ExpertRevalidationServiceTest {

    @Test
    fun `RevalidationResult includes demotionFailed in failureCount`() {
        val stats = RevalidationStats(total = 10, passed = 5, demoted = 2, demotionFailed = 1)
        val result = RevalidationResult(stats)
        assertEquals(5, result.taskSuccessCount)
        assertEquals(3, result.taskFailureCount) // demoted + demotionFailed
    }

    @Test
    fun `PromotionScanResult includes existenceCheckFailed in failureCount`() {
        val stats = PromotionScanStats(
            total = 20, promoted = 5, filtered = 10,
            emailRejected = 2, promotionFailed = 1, existenceCheckFailed = 1
        )
        val result = PromotionScanResult(stats)
        assertEquals(5, result.taskSuccessCount)
        assertEquals(14, result.taskFailureCount) // filtered + emailRejected + promotionFailed + existenceCheckFailed
    }

    @Test
    fun `RevalidationStats tracks demotion reasons`() {
        val stats = RevalidationStats()
        stats.demotionReasons.merge("INVALID_EMAIL_FORMAT", 1) { a, b -> a + b }
        stats.demotionReasons.merge("CHINESE_NATIONALITY", 1) { a, b -> a + b }
        stats.demotionReasons.merge("INVALID_EMAIL_FORMAT", 1) { a, b -> a + b }
        assertEquals(2, stats.demotionReasons["INVALID_EMAIL_FORMAT"])
        assertEquals(1, stats.demotionReasons["CHINESE_NATIONALITY"])
    }

    @Test
    fun `RevalidationResult zero total has SUCCESS final status`() {
        val result = RevalidationResult(RevalidationStats(total = 0))
        assertEquals("SUCCESS", result.taskFinalStatus)
    }

    @Test
    fun `PromotionScanResult with all failures has FAILED status`() {
        val stats = PromotionScanStats(
            total = 10, promoted = 0, filtered = 8, emailRejected = 2
        )
        val result = PromotionScanResult(stats)
        assertEquals(0, result.taskSuccessCount)
        assertEquals(10, result.taskFailureCount)
        assertEquals("FAILED", result.taskFinalStatus)
    }

    @Test
    fun `RevalidationResult all success returns SUCCESS`() {
        val stats = RevalidationStats(total = 5, passed = 5, demoted = 0, demotionFailed = 0)
        val result = RevalidationResult(stats)
        assertEquals("SUCCESS", result.taskFinalStatus)
    }

    @Test
    fun `RevalidationResult all failure returns FAILED`() {
        val stats = RevalidationStats(total = 10, passed = 0, demoted = 8, demotionFailed = 2)
        val result = RevalidationResult(stats)
        assertEquals("FAILED", result.taskFinalStatus)
    }

    @Test
    fun `RevalidationResult partial success returns PARTIAL_SUCCESS`() {
        val stats = RevalidationStats(total = 10, passed = 5, demoted = 3, demotionFailed = 2)
        val result = RevalidationResult(stats)
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
    }

    @Test
    fun `PromotionScanResult all success returns SUCCESS`() {
        val stats = PromotionScanStats(total = 5, promoted = 5)
        val result = PromotionScanResult(stats)
        assertEquals("SUCCESS", result.taskFinalStatus)
    }

    @Test
    fun `PromotionScanResult partial success returns PARTIAL_SUCCESS`() {
        val stats = PromotionScanStats(
            total = 10, promoted = 3, filtered = 5, emailRejected = 1, promotionFailed = 1
        )
        val result = PromotionScanResult(stats)
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
    }

    // ── 子计划 03：快速晋升分类门禁（I3-1 ~ I3-6）──

    private val searchService = mock(ExpertSearchService::class.java)
    private val writerService = mock(ExpertIndexWriterService::class.java)
    private val emailValidationService = mock(EmailValidationService::class.java)
    private val filterService = mock(EligibilityFilterService::class.java).also {
        `when`(it.getCandidateFilter()).thenReturn(CandidateFilterProperties())
        `when`(it.getAcademicFilter()).thenReturn(AcademicFilterProperties())
    }
    private val eligibilityService = CandidateEligibilityService(filterService, emailValidationService)
    private val progressStore = mock(TaskProgressStore::class.java)
    private val classificationService = mock(ExpertClassificationService::class.java)

    private fun validExpert(orcidId: String, email: String, country: String = "GB", esDocId: String? = null): ExpertProfile =
        ExpertProfile(
            esDocId = esDocId, orcidId = orcidId, email = email, givenNames = "Test", familyNames = "User",
            country = country, keyword = null, employment = null
        )

    private fun classification(type: ExpertType): ExpertClassification =
        ExpertClassification(
            type = type,
            productionScore = 0,
            researchScore = 0,
            positiveEvidence = emptyList(),
            negativeEvidence = emptyList(),
            version = "rnd-v2-2026",
            sourceFingerprint = "fp-0001",
            classifiedAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        )

    private fun serviceWith(
        classificationService: ExpertClassificationService = this.classificationService,
        gateEnabled: Boolean = false
    ): ExpertRevalidationService = ExpertRevalidationService(
        searchService, eligibilityService, emailValidationService, writerService, progressStore, filterService,
        classificationService, ExpertClassificationProperties(promotionGateEnabled = gateEnabled)
    )

    private fun <T> eqValue(value: T): T = eq(value) ?: value

    private fun <T> captureValue(captor: ArgumentCaptor<T>, defaultValue: T): T = captor.capture() ?: defaultValue

    private fun stubPromotionPath(expert: ExpertProfile) {
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, expert.orcidId)).thenReturn(false)
        `when`(writerService.readRawDocument(expert.orcidId)).thenReturn(
            mapOf("orcidId" to expert.orcidId, "email" to expert.email.orEmpty())
        )
        `when`(emailValidationService.validate(expert.email.orEmpty()))
            .thenReturn(EmailValidationResult(2, true))
        ScrollExpertsMockHelper.stubWriteCandidateDocument(writerService, true)
    }

    @Test
    fun `gate on rejects SERVICE_ONLY with CLASSIFICATION filter reason`() {
        val expert = validExpert("0001", "user@oxford.ac.uk")
        `when`(classificationService.classify(expert)).thenReturn(classification(ExpertType.SERVICE_ONLY))
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = serviceWith(gateEnabled = true).promoteEligibleRawExperts()

        assertEquals(1, result.stats.total)
        assertEquals(0, result.stats.promoted)
        assertEquals(1, result.stats.filtered)
        assertEquals(1, result.stats.filterReasons["CLASSIFICATION:SERVICE_ONLY"])
    }

    @Test
    fun `gate on rejects OUT_OF_SCOPE with CLASSIFICATION filter reason`() {
        val expert = validExpert("0002", "user2@oxford.ac.uk")
        `when`(classificationService.classify(expert)).thenReturn(classification(ExpertType.OUT_OF_SCOPE))
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = serviceWith(gateEnabled = true).promoteEligibleRawExperts()

        assertEquals(0, result.stats.promoted)
        assertEquals(1, result.stats.filtered)
        assertEquals(1, result.stats.filterReasons["CLASSIFICATION:OUT_OF_SCOPE"])
    }

    @Test
    fun `gate on passes UNKNOWN`() {
        val expert = validExpert("0003", "user3@oxford.ac.uk")
        `when`(classificationService.classify(expert)).thenReturn(classification(ExpertType.UNKNOWN))
        stubPromotionPath(expert)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = serviceWith(gateEnabled = true).promoteEligibleRawExperts()

        assertEquals(1, result.stats.promoted)
        assertEquals(0, result.stats.filtered)
    }

    @Test
    fun `gate on passes PRODUCTION_RND ACADEMIC_RND and HYBRID_RND`() {
        val experts = listOf(
            validExpert("0004", "a@ox.ac.uk") to ExpertType.PRODUCTION_RND,
            validExpert("0005", "b@ox.ac.uk") to ExpertType.ACADEMIC_RND,
            validExpert("0006", "c@ox.ac.uk") to ExpertType.HYBRID_RND
        )
        experts.forEach { (e, t) ->
            `when`(classificationService.classify(e)).thenReturn(classification(t))
            stubPromotionPath(e)
        }
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(experts.map { it.first }))

        val result = serviceWith(gateEnabled = true).promoteEligibleRawExperts()

        assertEquals(3, result.stats.promoted)
        assertEquals(0, result.stats.filtered)
        assertFalse(result.stats.filterReasons.keys.any { it.startsWith("CLASSIFICATION:") })
    }

    @Test
    fun `gate off passes SERVICE_ONLY with no CLASSIFICATION reasons`() {
        val expert = validExpert("0007", "user7@oxford.ac.uk")
        `when`(classificationService.classify(expert)).thenReturn(classification(ExpertType.SERVICE_ONLY))
        stubPromotionPath(expert)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = serviceWith(gateEnabled = false).promoteEligibleRawExperts()

        assertEquals(1, result.stats.promoted)
        assertEquals(0, result.stats.filtered)
        assertFalse(result.stats.filterReasons.keys.any { it.startsWith("CLASSIFICATION:") })
    }

    @Test
    fun `gate off still writes expertClassification into promoted doc`() {
        val expert = validExpert("0008", "user8@oxford.ac.uk")
        val cls = classification(ExpertType.SERVICE_ONLY)
        `when`(classificationService.classify(expert)).thenReturn(cls)
        val node = ObjectMapper().createObjectNode().put("type", "SERVICE_ONLY")
        `when`(writerService.classificationNode(cls)).thenReturn(node)
        stubPromotionPath(expert)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = serviceWith(gateEnabled = false).promoteEligibleRawExperts()

        assertEquals(1, result.stats.promoted)
        @Suppress("UNCHECKED_CAST")
        val docCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any>>
        verify(writerService).writeCandidateDocument(eqValue("0008"), captureValue(docCaptor, emptyMap<String, Any>()))
        val doc = docCaptor.value
        assertTrue(doc.containsKey("expertClassification"))
        assertSame(node, doc["expertClassification"])
    }

    @Test
    fun `gate on passes Robert Bosch GmbH plus 2024 profile as UNKNOWN (I3-1 regression anchor)`() {
        val profile = ExpertProfile(
            esDocId = null, orcidId = "0009", email = "rnd@bosch.de", givenNames = "Test", familyNames = "User",
            country = "DE", keyword = null, employment = "Robert Bosch GmbH", age = null, degree = null,
            nationality = null, hIndex = null, citationCount = null, lastPublicationYear = 2024,
            researchFields = null, disciplineCategory = null, institution = null, emailSource = null,
            emailVerifiedLevel = null, dataSource = null, externalIds = null, worksCount = null,
            tags = null, updatedAt = null, operatorStatus = null, recentWorkTitles = null,
            patentTitles = null, enrichedAt = null, enrichmentSource = null, expertClassification = null
        )
        val realClassifier = ExpertClassificationService(
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"))
        )
        assertEquals(ExpertType.UNKNOWN, realClassifier.classify(profile).type)
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "0009")).thenReturn(false)
        `when`(writerService.readRawDocument("0009")).thenReturn(
            mapOf("orcidId" to "0009", "email" to "rnd@bosch.de", "employment" to "Robert Bosch GmbH")
        )
        `when`(emailValidationService.validate("rnd@bosch.de")).thenReturn(EmailValidationResult(2, true))
        ScrollExpertsMockHelper.stubWriteCandidateDocument(writerService, true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(profile)))

        val result = serviceWith(classificationService = realClassifier, gateEnabled = true).promoteEligibleRawExperts()

        assertEquals(1, result.stats.promoted)
        assertEquals(0, result.stats.filtered)
    }

    @Test
    fun `promoted doc preserves raw fields and adds only classification plus existing overlay keys`() {
        val expert = validExpert("0010", "user10@oxford.ac.uk")
        val cls = classification(ExpertType.UNKNOWN)
        `when`(classificationService.classify(expert)).thenReturn(cls)
        val node = ObjectMapper().createObjectNode().put("type", "UNKNOWN")
        `when`(writerService.classificationNode(cls)).thenReturn(node)
        val rawDoc = mapOf(
            "orcidId" to "0010",
            "email" to "user10@oxford.ac.uk",
            "employment" to "Acme GmbH",
            "institution" to "Acme GmbH",
            "tags" to listOf("discovered", "verified"),
            "country" to "DE"
        )
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "0010")).thenReturn(false)
        `when`(writerService.readRawDocument("0010")).thenReturn(rawDoc)
        `when`(emailValidationService.validate("user10@oxford.ac.uk")).thenReturn(EmailValidationResult(2, true))
        ScrollExpertsMockHelper.stubWriteCandidateDocument(writerService, true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = serviceWith(gateEnabled = true).promoteEligibleRawExperts()

        assertEquals(1, result.stats.promoted)
        @Suppress("UNCHECKED_CAST")
        val docCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any>>
        verify(writerService).writeCandidateDocument(eqValue("0010"), captureValue(docCaptor, emptyMap<String, Any>()))
        val doc = docCaptor.value

        assertEquals("0010", doc["orcidId"])
        assertEquals(rawDoc["email"], doc["email"])
        assertEquals(rawDoc["employment"], doc["employment"])
        assertEquals(rawDoc["institution"], doc["institution"])
        assertEquals(listOf("discovered", "verified", "auto_promoted"), doc["tags"])
        assertNotNull(doc["candidateValidatedAt"])
        assertNotNull(doc["updatedAt"])
        assertSame(node, doc["expertClassification"])
        val expectedKeys = rawDoc.keys + setOf("candidateValidatedAt", "updatedAt", "tags", "expertClassification")
        assertEquals(expectedKeys, doc.keys)
    }

    @Test
    fun `eligibility failure never calls classify (I3-6)`() {
        val fs = mock(EligibilityFilterService::class.java).also {
            `when`(it.getCandidateFilter()).thenReturn(CandidateFilterProperties(requireOrcid = true))
            `when`(it.getAcademicFilter()).thenReturn(AcademicFilterProperties())
        }
        val elig = CandidateEligibilityService(fs, emailValidationService)
        val service = ExpertRevalidationService(
            searchService, elig, emailValidationService, writerService, progressStore, fs,
            classificationService, ExpertClassificationProperties(promotionGateEnabled = true)
        )
        val expert = validExpert("", "user11@oxford.ac.uk")
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.promoteEligibleRawExperts()

        assertEquals(1, result.stats.filtered)
        assertEquals(1, result.stats.filterReasons["MISSING_ORCID"])
        verifyNoInteractions(classificationService)
    }
}
