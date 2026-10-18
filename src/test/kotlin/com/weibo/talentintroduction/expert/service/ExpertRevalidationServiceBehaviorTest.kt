package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.AcademicFilterProperties
import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

class ExpertRevalidationServiceBehaviorTest {

    private val searchService = mock(ExpertSearchService::class.java)
    private val writerService = mock(ExpertIndexWriterService::class.java)
    private val emailValidationService = mock(EmailValidationService::class.java).also {
        `when`(it.isDisposableEmail(anyString())).thenReturn(false)
    }
    private val filterService = mock(EligibilityFilterService::class.java).also {
        `when`(it.getCandidateFilter()).thenReturn(CandidateFilterProperties())
        `when`(it.getAcademicFilter()).thenReturn(AcademicFilterProperties())
    }
    private val eligibilityService = CandidateEligibilityService(filterService, emailValidationService)
    private val progressStore = mock(TaskProgressStore::class.java)
    private val service = ExpertRevalidationService(
        searchService, eligibilityService, emailValidationService, writerService, progressStore, filterService
    )
    private fun <T : Any> match(value: T): T = org.mockito.ArgumentMatchers.eq(value) ?: value
    private fun anyClassification(): com.weibo.talentintroduction.expert.domain.ExpertClassification =
        org.mockito.ArgumentMatchers.any(com.weibo.talentintroduction.expert.domain.ExpertClassification::class.java)
            ?: ExpertClassificationService().classify(validExpert("DOC", "researcher@example.org"))
    private fun anySnapshot(): ExpertIndexWriterService.DiscoverySnapshot =
        org.mockito.ArgumentMatchers.any(ExpertIndexWriterService.DiscoverySnapshot::class.java)
            ?: ExpertIndexWriterService.DiscoverySnapshot(emptyMap(), 0, 0)
    private fun anyReasons(): List<String> = org.mockito.ArgumentMatchers.anyList<String>() ?: emptyList()

    private fun validExpert(orcidId: String, email: String, country: String = "GB", esDocId: String? = null): ExpertProfile =
        ExpertProfile(
            esDocId = esDocId, orcidId = orcidId, email = email, givenNames = "Test", familyNames = "User",
            country = country, keyword = null, employment = null
        )

    @Test
    fun `demoted on invalid email with successful delete`() {
        val expert = validExpert("0001", "bad-email")
        `when`(emailValidationService.validate("bad-email"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(0, false, "INVALID_FORMAT"))
        `when`(writerService.removeFromCandidateIndex("0001")).thenReturn(true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.revalidateCandidates()
        assertEquals(1, result.stats.total)
        assertEquals(1, result.stats.demoted)
        assertEquals(0, result.stats.demotionFailed)
    }

    @Test
    fun `revalidate deletes and tags by esDocId when present`() {
        val invalidExpert = validExpert("0001", "bad-email", esDocId = "ORCID-0001")
        val validExpert = validExpert("0002", "john@oxford.ac.uk", esDocId = "EMAIL-abcd")
        `when`(emailValidationService.validate("bad-email"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(0, false, "INVALID_FORMAT"))
        `when`(emailValidationService.validate("john@oxford.ac.uk"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        `when`(writerService.removeFromCandidateIndex("ORCID-0001")).thenReturn(true)
        `when`(writerService.addTag("EMAIL-abcd", "verified", ExpertIndexLevel.CANDIDATE)).thenReturn(true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(invalidExpert, validExpert)))

        service.revalidateCandidates()

        verify(writerService).removeFromCandidateIndex("ORCID-0001")
        verify(writerService, never()).removeFromCandidateIndex("0001")
        verify(writerService).addTag("EMAIL-abcd", "verified", ExpertIndexLevel.CANDIDATE)
        verify(writerService, never()).addTag("0002", "verified", ExpertIndexLevel.CANDIDATE)
    }

    @Test
    fun `demotionFailed on delete failure then continues`() {
        val e1 = validExpert("0001", "bad1")
        val e2 = validExpert("0002", "bad2")
        `when`(emailValidationService.validate("bad1"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(0, false, "INVALID_FORMAT"))
        `when`(emailValidationService.validate("bad2"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(0, false, "INVALID_FORMAT"))
        `when`(writerService.removeFromCandidateIndex("0001")).thenReturn(false)
        `when`(writerService.removeFromCandidateIndex("0002")).thenReturn(true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(e1, e2)))

        val result = service.revalidateCandidates()
        assertEquals(2, result.stats.total)
        assertEquals(1, result.stats.demoted)
        assertEquals(1, result.stats.demotionFailed)
        // deletion failure should NOT increment demotionReasons
        assertEquals(1, result.stats.demotionReasons["EMAIL:INVALID_FORMAT"])
    }

    @Test
    fun `passed expert counted correctly`() {
        val expert = validExpert("0001", "john@oxford.ac.uk")
        `when`(emailValidationService.validate("john@oxford.ac.uk"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.revalidateCandidates()
        assertEquals(1, result.stats.passed)
    }

    @Test
    fun `multiple eligibility reject reasons accumulated`() {
        val expert = ExpertProfile(
            orcidId = "0001", email = null, givenNames = "Test", familyNames = "User",
            country = "CN", keyword = null, employment = null, nationality = "Chinese"
        )
        `when`(emailValidationService.validate(""))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(0, false, "EMPTY_EMAIL"))
        `when`(writerService.removeFromCandidateIndex("0001")).thenReturn(true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.revalidateCandidates()
        assertEquals(1, result.stats.demoted)
        assertTrue(result.stats.demotionReasons.containsKey("EMAIL:EMPTY_EMAIL"))
    }

    @Test
    fun `alreadyPromoted counted when exists in candidate`() {
        val expert = validExpert("0001", "user@oxford.ac.uk")
        `when`(emailValidationService.validate("user@oxford.ac.uk"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "0001")).thenReturn(true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.promoteEligibleRawExperts()
        assertEquals(1, result.stats.alreadyPromoted)
        assertEquals(0, result.stats.promoted)
    }

    @Test
    fun `promote raw uses esDocId for HEAD read and write`() {
        val expert = validExpert("0001", "user@oxford.ac.uk", esDocId = "ORCID-0001")
        `when`(emailValidationService.validate("user@oxford.ac.uk"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "ORCID-0001")).thenReturn(false)
        `when`(writerService.readRawDocument("ORCID-0001"))
            .thenReturn(mapOf("orcidId" to "0001", "email" to "user@oxford.ac.uk", "givenNames" to "A", "familyNames" to "B"))
        ScrollExpertsMockHelper.stubWriteCandidateDocument(writerService, true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.promoteEligibleRawExperts()

        assertEquals(1, result.stats.promoted)
        verify(writerService).documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "ORCID-0001")
        verify(writerService, never()).documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "0001")
        verify(writerService).readRawDocument("ORCID-0001")
        verify(writerService, never()).readRawDocument("0001")
        ScrollExpertsMockHelper.verifyWriteCandidateDocumentWithDocIdAndOrcid(writerService, "ORCID-0001", "ORCID-0001")
        ScrollExpertsMockHelper.verifyNeverWriteCandidateDocumentWithDocId(writerService, "0001")
    }

    @Test
    fun `existenceCheckFailed on HEAD exception then skips promote`() {
        val expert = validExpert("0001", "user@oxford.ac.uk")
        `when`(emailValidationService.validate("user@oxford.ac.uk"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "0001"))
            .thenThrow(RuntimeException("ES 5xx"))
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.promoteEligibleRawExperts()
        assertEquals(1, result.stats.existenceCheckFailed)
        assertEquals(0, result.stats.promoted)
    }

    @Test
    fun `empty ORCID not filtered when requireOrcid disabled`() {
        val expert = validExpert("", "user@oxford.ac.uk")
        `when`(emailValidationService.validate("user@oxford.ac.uk"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "")).thenReturn(true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.promoteEligibleRawExperts()
        assertEquals(0, result.stats.filtered)
        assertEquals(0, result.stats.promoted)
        assertEquals(1, result.stats.alreadyPromoted)
        verify(writerService).documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "")
    }

    @Test
    fun `empty ORCID filtered when requireOrcid enabled without calling documentExists`() {
        `when`(filterService.getCandidateFilter()).thenReturn(CandidateFilterProperties(requireOrcid = true))
        val expert = validExpert("", "user@oxford.ac.uk")
        `when`(emailValidationService.validate("user@oxford.ac.uk"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.promoteEligibleRawExperts()
        assertEquals(1, result.stats.filtered)
        assertEquals(0, result.stats.promoted)
        assertEquals(0, result.stats.existenceCheckFailed)
        assertEquals(0, result.stats.alreadyPromoted)
        verifyNoInteractions(writerService)
    }

    @Test
    fun `promotionFailed on RAW read failure`() {
        val expert = validExpert("0001", "user@oxford.ac.uk")
        `when`(emailValidationService.validate("user@oxford.ac.uk"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "0001")).thenReturn(false)
        `when`(writerService.readRawDocument("0001")).thenReturn(null)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.promoteEligibleRawExperts()
        assertEquals(1, result.stats.promotionFailed)
        assertEquals(0, result.stats.promoted)
    }

    @Test
    fun `multi-batch scroll processes all`() {
        val batch1 = (1..3).map { validExpert("A00$it", "a$it@ox.ac.uk") }
        val batch2 = (4..5).map { validExpert("B00$it", "b$it@ox.ac.uk") }
        (batch1 + batch2).forEach { e ->
            `when`(emailValidationService.validate(e.email!!))
                .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        }
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(batch1, batch2))

        val result = service.revalidateCandidates()
        assertEquals(5, result.stats.total)
        assertEquals(5, result.stats.passed)
    }

    @Test
    fun `non-discovery revalidation never consults the admission service (04)`() {
        val review = mock(com.weibo.talentintroduction.discovery.service.DiscoveryReviewService::class.java)
        val svc = ExpertRevalidationService(
            searchService, eligibilityService, emailValidationService, writerService, progressStore, filterService,
            discoveryReviewService = review
        )
        val expert = validExpert("0001", "john@oxford.ac.uk")
        `when`(emailValidationService.validate("john@oxford.ac.uk"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = svc.revalidateCandidates()

        assertEquals(1, result.stats.passed)
        verifyNoInteractions(review)
    }

    private fun serviceWithEmailFilterOff(): ExpertRevalidationService {
        val fs = mock(EligibilityFilterService::class.java).also {
            `when`(it.getCandidateFilter()).thenReturn(CandidateFilterProperties(requireValidEmail = false))
            `when`(it.getAcademicFilter()).thenReturn(AcademicFilterProperties())
        }
        val elig = CandidateEligibilityService(fs, emailValidationService)
        return ExpertRevalidationService(searchService, elig, emailValidationService, writerService, progressStore, fs)
    }

    @Test
    fun `revalidate skips email validation when requireValidEmail is false`() {
        val svc = serviceWithEmailFilterOff()
        val expert = validExpert("0001", "bad-email")
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = svc.revalidateCandidates()
        assertEquals(1, result.stats.total)
        assertEquals(1, result.stats.passed)
        assertEquals(0, result.stats.demoted)
        verify(emailValidationService, never()).validate(anyString())
    }

    @Test
    fun `promoteRaw does not count emailRejected when requireValidEmail is false`() {
        val svc = serviceWithEmailFilterOff()
        val expert = validExpert("0001", "bad-email")
        val emptyResult = com.weibo.talentintroduction.expert.domain.EmailValidationResult(0, false, "INVALID_FORMAT")
        `when`(emailValidationService.validate("bad-email")).thenReturn(emptyResult)
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "0001")).thenReturn(false)
        ScrollExpertsMockHelper.stubReadRawDocument(writerService,
            mapOf("orcidId" to "x", "email" to "x@x.com", "givenNames" to "A", "familyNames" to "B"))
        ScrollExpertsMockHelper.stubWriteCandidateDocument(writerService, true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = svc.promoteEligibleRawExperts()
        assertEquals(1, result.stats.promoted)
        assertEquals(0, result.stats.emailRejected)
        verify(emailValidationService, never()).validate(anyString())
    }

    private fun discoverySource(orcidId: String = "HISTORICAL-ORCID"): Map<String, Any?> {
        val proof = com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.verified(
            "researcher@example.org", "Test", "User", "JATS_SHA256:" + "a".repeat(64), null, "A42"
        )
        return mapOf(
            "orcidId" to orcidId, "email" to "researcher@example.org",
            "givenNames" to "Test", "familyNames" to "User", "emailSource" to "PAPER_FULLTEXT",
            "identityVerification" to com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().convertValue(proof, Map::class.java),
            "researchFieldIds" to listOf("22"), "institution" to "University", "lastPublicationYear" to 2026
        )
    }

    @Test
    fun `discovery promotion rejects full email validation failure despite valid format and research`() {
        val source = discoverySource()
        val snapshot = ExpertIndexWriterService.DiscoverySnapshot(source, 3, 2)
        val profile = validExpert("HISTORICAL-ORCID", "researcher@example.org", esDocId = "OLD-DOC").copy(
            emailSource = "PAPER_FULLTEXT", researchFieldIds = listOf("22"), institution = "University",
            lastPublicationYear = 2026
        )
        `when`(writerService.readDiscoveryDocument(ExpertIndexLevel.RAW, "OLD-DOC")).thenReturn(snapshot)
        `when`(writerService.discoveryProfile("OLD-DOC", source)).thenReturn(profile)
        `when`(emailValidationService.validate("researcher@example.org"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, false, "NO_MX_RECORD"))
        `when`(writerService.reconcileDiscoveryCandidate(
            match("OLD-DOC"), match(snapshot), anyClassification(), anyReasons(), match(false)
        )).thenReturn(true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(profile)))

        val result = service.promoteEligibleRawExperts()

        assertEquals(0, result.stats.promoted)
        assertEquals(0, result.stats.alreadyPromoted)
        assertEquals(1, result.stats.filtered)
        assertEquals(1, result.stats.filterReasons["EMAIL:NO_MX_RECORD"])
        verify(emailValidationService).validate("researcher@example.org")
        verify(writerService).reconcileDiscoveryCandidate(
            match("OLD-DOC"), match(snapshot), anyClassification(),
            match(listOf("EMAIL:NO_MX_RECORD")), match(false)
        )
    }

    @Test
    fun `discovery candidate revalidation rejects invalid email through the same gate`() {
        val source = discoverySource()
        val snapshot = ExpertIndexWriterService.DiscoverySnapshot(source, 3, 2)
        val profile = validExpert("HISTORICAL-ORCID", "researcher@example.org", esDocId = "OLD-DOC").copy(
            emailSource = "PAPER_FULLTEXT", researchFieldIds = listOf("22"), institution = "University",
            lastPublicationYear = 2026
        )
        `when`(writerService.readDiscoveryDocument(ExpertIndexLevel.RAW, "OLD-DOC")).thenReturn(snapshot)
        `when`(writerService.discoveryProfile("OLD-DOC", source)).thenReturn(profile)
        `when`(emailValidationService.validate("researcher@example.org"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, false, "NO_MX_RECORD"))
        `when`(writerService.reconcileDiscoveryCandidate(
            match("OLD-DOC"), match(snapshot), anyClassification(), anyReasons(), match(false)
        )).thenReturn(true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(profile)))

        val result = service.revalidateCandidates()

        assertEquals(0, result.stats.passed)
        assertEquals(1, result.stats.demoted)
        assertEquals(1, result.stats.demotionReasons["EMAIL:NO_MX_RECORD"])
        verify(writerService).reconcileDiscoveryCandidate(
            match("OLD-DOC"), match(snapshot), anyClassification(),
            match(listOf("EMAIL:NO_MX_RECORD")), match(false)
        )
    }

    @Test
    fun `discovery validation exception is retryable and never writes a candidate`() {
        val source = discoverySource()
        val snapshot = ExpertIndexWriterService.DiscoverySnapshot(source, 3, 2)
        `when`(writerService.readDiscoveryDocument(ExpertIndexLevel.RAW, "OLD-DOC")).thenReturn(snapshot)
        `when`(writerService.discoveryProfile("OLD-DOC", source)).thenReturn(
            validExpert("HISTORICAL-ORCID", "researcher@example.org").copy(
                emailSource = "PAPER_FULLTEXT", researchFieldIds = listOf("22")
            )
        )
        `when`(emailValidationService.validate("researcher@example.org")).thenThrow(IllegalStateException("MX unavailable"))

        assertEquals(PromotionOutcome.WriteFailed, service.revalidateDiscovery("OLD-DOC"))
        verify(writerService, never()).reconcileDiscoveryCandidate(
            match("OLD-DOC"), anySnapshot(), anyClassification(), anyReasons(), match(false)
        )
    }

    @Test
    fun `discovery email check disabled preserves professional admission`() {
        val source = discoverySource()
        val snapshot = ExpertIndexWriterService.DiscoverySnapshot(source, 3, 2)
        `when`(writerService.readDiscoveryDocument(ExpertIndexLevel.RAW, "OLD-DOC")).thenReturn(snapshot)
        `when`(writerService.discoveryProfile("OLD-DOC", source)).thenReturn(
            validExpert("HISTORICAL-ORCID", "researcher@example.org").copy(
                emailSource = "PAPER_FULLTEXT", researchFieldIds = listOf("22"),
                institution = "University", lastPublicationYear = 2026
            )
        )
        `when`(writerService.reconcileDiscoveryCandidate(
            match("OLD-DOC"), match(snapshot), anyClassification(), anyReasons(), match(false)
        )).thenReturn(true)

        assertEquals(PromotionOutcome.Promoted, serviceWithEmailFilterOff().revalidateDiscovery("OLD-DOC"))
        verify(emailValidationService, never()).validate(anyString())
    }

    @Test
    fun `discovery valid email permits historical document promotion`() {
        val source = discoverySource()
        val snapshot = ExpertIndexWriterService.DiscoverySnapshot(source, 3, 2)
        `when`(writerService.readDiscoveryDocument(ExpertIndexLevel.RAW, "OLD-DOC")).thenReturn(snapshot)
        `when`(writerService.discoveryProfile("OLD-DOC", source)).thenReturn(
            validExpert("HISTORICAL-ORCID", "researcher@example.org").copy(
                emailSource = "PAPER_FULLTEXT", researchFieldIds = listOf("22"),
                institution = "University", lastPublicationYear = 2026
            )
        )
        `when`(emailValidationService.validate("researcher@example.org"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(3, true))
        `when`(writerService.reconcileDiscoveryCandidate(
            match("OLD-DOC"), match(snapshot), anyClassification(), anyReasons(), match(false)
        )).thenReturn(true)

        assertEquals(PromotionOutcome.Promoted, service.revalidateDiscovery("OLD-DOC"))
        verify(writerService).reconcileDiscoveryCandidate(
            match("OLD-DOC"), match(snapshot), anyClassification(), match(emptyList()), match(false)
        )
    }

    @Test
    fun `discovery revalidation admits only after current RAW has target research evidence`() {
        val proof = com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.verified(
            "researcher@example.org", "Test", "User", "JATS_SHA256:" + "a".repeat(64), null, "A42"
        )
        val base = mapOf<String, Any?>(
            "orcidId" to "DOC", "email" to "researcher@example.org", "givenNames" to "Test",
            "familyNames" to "User", "emailSource" to "PAPER_FULLTEXT",
            "identityVerification" to com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().convertValue(proof, Map::class.java),
            "institution" to "University", "lastPublicationYear" to 2026
        )
        val unknown = ExpertIndexWriterService.DiscoverySnapshot(base, 1, 1)
        val supported = ExpertIndexWriterService.DiscoverySnapshot(base + ("researchFieldIds" to listOf("22")), 2, 1)
        `when`(writerService.readDiscoveryDocument(ExpertIndexLevel.RAW, "DOC"))
            .thenReturn(unknown, supported)
        `when`(writerService.discoveryProfile("DOC", base))
            .thenReturn(validExpert("DOC", "researcher@example.org").copy(
                emailSource = "PAPER_FULLTEXT", institution = "University", lastPublicationYear = 2026
            ))
        `when`(writerService.discoveryProfile("DOC", supported.source))
            .thenReturn(validExpert("DOC", "researcher@example.org").copy(
                emailSource = "PAPER_FULLTEXT", institution = "University", lastPublicationYear = 2026,
                researchFieldIds = listOf("22")
            ))
        `when`(writerService.reconcileDiscoveryCandidate(
            match("DOC"), anySnapshot(), anyClassification(), anyReasons(), match(false)
        )).thenReturn(true)
        `when`(emailValidationService.validate("researcher@example.org"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(3, true))
        val first = service.revalidateDiscovery("DOC")
        assertEquals(PromotionOutcome.Promoted, first)
        val second = service.revalidateDiscovery("DOC")
        assertEquals(PromotionOutcome.Promoted, second)
        verify(writerService).reconcileDiscoveryCandidate(
            match("DOC"), match(unknown), anyClassification(), match(emptyList()), match(false)
        )
        verify(writerService).reconcileDiscoveryCandidate(
            match("DOC"), match(supported), anyClassification(), match(emptyList()), match(false)
        )
    }

    @Test
    fun `application discovery still updates RAW qualification without touching candidate`() {
        val proof = com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.verified(
            "researcher@example.org", "Test", "User", "JATS_SHA256:" + "b".repeat(64), null, "A42"
        )
        val source = mapOf<String, Any?>(
            "orcidId" to "APP", "email" to "researcher@example.org", "givenNames" to "Test",
            "familyNames" to "User", "emailSource" to "PAPER_FULLTEXT",
            "identityVerification" to com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().convertValue(proof, Map::class.java)
        )
        val raw = ExpertIndexWriterService.DiscoverySnapshot(source, 1, 1)
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.APPLICATION, "APP")).thenReturn(true)
        `when`(writerService.readDiscoveryDocument(ExpertIndexLevel.RAW, "APP")).thenReturn(raw)
        `when`(writerService.discoveryProfile("APP", source)).thenReturn(
            validExpert("APP", "researcher@example.org").copy(emailSource = "PAPER_FULLTEXT")
        )
        `when`(writerService.reconcileDiscoveryCandidate(
            match("APP"), match(raw), anyClassification(), match(emptyList()), match(true)
        )).thenReturn(true)
        `when`(emailValidationService.validate("researcher@example.org"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(3, true))
        assertEquals(PromotionOutcome.AlreadyPresent, service.revalidateDiscovery("APP"))
        verify(writerService, never()).readDiscoveryDocument(ExpertIndexLevel.CANDIDATE, "APP")
    }

    @Test
    fun `managed direct and Apollo unclear survive candidate revalidation without delete HTTP`() {
        for (apollo in listOf(false, true)) {
            val f = com.weibo.talentintroduction.discovery.service.AdmissionGateFixture()
            val rest = org.springframework.web.client.RestTemplate()
            val server = org.springframework.test.web.client.MockRestServiceServer.bindTo(rest).build()
            val props = com.weibo.talentintroduction.config.ElasticsearchProperties("http://es", "u", "p", "raw", "cand", "app")
            val indices = mock(ExpertIndexService::class.java)
            `when`(indices.indexName(ExpertIndexLevel.RAW)).thenReturn("raw")
            `when`(indices.indexName(ExpertIndexLevel.CANDIDATE)).thenReturn("cand")
            `when`(indices.indexName(ExpertIndexLevel.APPLICATION)).thenReturn("app")
            val writer = ExpertIndexWriterService(rest, props, indices, f.mapper,
                mock(ExpertPromotionAuditService::class.java),
                mock(com.weibo.talentintroduction.campaign.repository.ExpertContactRepository::class.java))
            @Suppress("UNCHECKED_CAST")
            val source = f.mapper.convertValue(f.profile, Map::class.java) as Map<String, Any?>
            val profile = writer.discoveryProfile(f.docId, source)
            val item = f.item(profile, facts = listOf(com.weibo.talentintroduction.discovery.service.ruleJob(if (apollo) "Researcher" else "Professor")),
                search = if (apollo) com.weibo.talentintroduction.discovery.service.ruleSearch() else null)
            f.load(item)
            val repo = mock(com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository::class.java)
            val now = java.time.LocalDateTime.ofInstant(com.weibo.talentintroduction.discovery.service.ruleClock.instant(), java.time.ZoneOffset.UTC)
            val admission = com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryAdmission(
                f.docId, item.identityHash, "AUTO_PASSED", 1L, null,
                com.weibo.talentintroduction.discovery.service.DiscoveryVerificationAdmissionGate.POLICY_VERSION, now, now)
            `when`(repo.findAdmission(f.docId)).thenReturn(admission)
            `when`(repo.findAdmissions(anyCollection<String>() ?: emptyList())).thenReturn(listOf(admission))
            val review = com.weibo.talentintroduction.discovery.service.DiscoveryReviewService(
                repo, f.policy, writer, rest, props, indices, f.mapper)
            val svc = ExpertRevalidationService(searchService, f.eligibility, f.email, writer, progressStore, f.filters,
                discoveryReviewService = review, admissionPolicy = f.policy, verificationGate = f.gate)
            fun expectHead() {
                server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://es/app/_doc/${f.docId}"))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(org.springframework.http.HttpMethod.HEAD))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.NOT_FOUND))
            }
            fun expectGet(index: String) {
                server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://es/$index/_doc/${f.docId}"))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(org.springframework.http.HttpMethod.GET))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        f.mapper.writeValueAsString(mapOf("_source" to source, "_seq_no" to 1, "_primary_term" to 1)), org.springframework.http.MediaType.APPLICATION_JSON))
            }
            expectHead()
            expectGet("raw")
            expectHead()
            expectGet("cand")
            assertEquals(PromotionOutcome.AlreadyPresent, svc.revalidateDiscovery(f.docId))
            server.verify() // Strict ordered expectations: any candidate DELETE/PUT is an unexpected HTTP failure.
            verify(f.repository).findItemsByDocIds(setOf(f.docId))
            assertEquals("AUTO_PASSED", review.resolveAdmission(f.docId, profile).decision)
        }
    }

    @Test
    fun `managed ledger failure never falls through to old candidate reconciliation`() {
        val f = com.weibo.talentintroduction.discovery.service.AdmissionGateFixture()
        val source = discoverySource()
        `when`(writerService.readDiscoveryDocument(ExpertIndexLevel.RAW, f.docId))
            .thenReturn(ExpertIndexWriterService.DiscoverySnapshot(source, 1, 1))
        `when`(writerService.discoveryProfile(f.docId, source)).thenReturn(f.profile)
        `when`(f.repository.findItemsByDocIds(setOf(f.docId)))
            .thenThrow(org.springframework.dao.DataAccessResourceFailureException("offline"))
        val svc = ExpertRevalidationService(searchService, f.eligibility, f.email, writerService, progressStore, f.filters,
            admissionPolicy = f.policy, verificationGate = f.gate)
        assertEquals(PromotionOutcome.WriteFailed, svc.revalidateDiscovery(f.docId))
        verify(writerService, never()).reconcileDiscoveryCandidate(anyString(), anySnapshot(), anyClassification(), anyReasons(), anyBoolean())
        verify(writerService, never()).projectDiscoveryCandidate(anyString(), anyMap<String, Any?>() ?: emptyMap(), anyString())
    }

    @Test
    fun `managed pending and new hard blockers cannot create a candidate`() {
        for (hardBlock in listOf(false, true)) {
            val f = com.weibo.talentintroduction.discovery.service.AdmissionGateFixture()
            val profile = if (hardBlock) f.profile.copy(institutionEvidence = "INVALID") else f.profile
            val item = f.item(profile, facts = if (hardBlock) listOf(com.weibo.talentintroduction.discovery.service.ruleJob("Professor")) else emptyList())
            f.load(item)
            val repo = mock(com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository::class.java)
            val now = java.time.LocalDateTime.ofInstant(com.weibo.talentintroduction.discovery.service.ruleClock.instant(), java.time.ZoneOffset.UTC)
            val admission = com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryAdmission(
                f.docId, item.identityHash, "NEEDS_REVIEW", 1, null,
                com.weibo.talentintroduction.discovery.service.DiscoveryVerificationAdmissionGate.POLICY_VERSION, now, now)
            `when`(repo.findAdmission(f.docId)).thenReturn(admission)
            `when`(repo.findAdmissions(anyCollection<String>() ?: emptyList())).thenReturn(listOf(admission))
            val source = discoverySource()
            `when`(writerService.readDiscoveryDocument(ExpertIndexLevel.RAW, f.docId))
                .thenReturn(ExpertIndexWriterService.DiscoverySnapshot(source, 1, 1))
            `when`(writerService.discoveryProfile(f.docId, source)).thenReturn(profile)
            `when`(writerService.reconcileDiscoveryCandidate(match(f.docId), anySnapshot(), anyClassification(), anyReasons(), match(false))).thenReturn(true)
            val rest = mock(org.springframework.web.client.RestTemplate::class.java)
            val props = com.weibo.talentintroduction.config.ElasticsearchProperties("http://es", "u", "p", "raw", "cand", "app")
            val indices = mock(ExpertIndexService::class.java)
            val review = com.weibo.talentintroduction.discovery.service.DiscoveryReviewService(repo, f.policy, writerService, rest, props, indices, f.mapper)
            val svc = ExpertRevalidationService(searchService, f.eligibility, f.email, writerService, progressStore, f.filters,
                discoveryReviewService = review, admissionPolicy = f.policy, verificationGate = f.gate)
            val outcome = svc.revalidateDiscovery(f.docId)
            assertTrue(outcome is PromotionOutcome.Rejected, outcome.toString())
            val codes = (outcome as PromotionOutcome.Rejected).reasons
            assertTrue(codes.contains(if (hardBlock) "INSTITUTION_EVIDENCE_INVALID" else "QUALIFICATION_EVIDENCE_MISSING"), codes.toString())
            verify(writerService, never()).projectDiscoveryCandidate(anyString(), anyMap<String, Any?>() ?: emptyMap(), anyString())
        }
    }
}
