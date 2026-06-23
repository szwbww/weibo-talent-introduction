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
}
