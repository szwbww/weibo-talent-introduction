package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.AcademicFilterProperties
import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

class ExpertRevalidationServiceBehaviorTest {

    private val searchService = mock(ExpertSearchService::class.java)
    private val writerService = mock(ExpertIndexWriterService::class.java)
    private val emailValidationService = mock(EmailValidationService::class.java).also {
        `when`(it.isDisposableEmail(anyString())).thenReturn(false)
    }
    private val eligibilityService = CandidateEligibilityService(
        CandidateFilterProperties(), AcademicFilterProperties(), emailValidationService
    )
    private val service = ExpertRevalidationService(
        searchService, eligibilityService, emailValidationService, writerService
    )

    private fun validExpert(orcidId: String, email: String, country: String = "GB"): ExpertProfile =
        ExpertProfile(
            orcidId = orcidId, email = email, givenNames = "Test", familyNames = "User",
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

        val result = service.promoteEligibleRawExperts(5)
        assertEquals(1, result.stats.alreadyPromoted)
        assertEquals(0, result.stats.promoted)
    }

    @Test
    fun `existenceCheckFailed on HEAD exception then skips promote`() {
        val expert = validExpert("0001", "user@oxford.ac.uk")
        `when`(emailValidationService.validate("user@oxford.ac.uk"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "0001"))
            .thenThrow(RuntimeException("ES 5xx"))
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.promoteEligibleRawExperts(5)
        assertEquals(1, result.stats.existenceCheckFailed)
        assertEquals(0, result.stats.promoted)
    }

    @Test
    fun `empty ORCID filtered without calling documentExists`() {
        val expert = validExpert("", "user@oxford.ac.uk")
        `when`(emailValidationService.validate("user@oxford.ac.uk"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.promoteEligibleRawExperts(5)
        assertEquals(1, result.stats.filtered)
        assertEquals(0, result.stats.promoted)
        assertEquals(0, result.stats.existenceCheckFailed)
        assertEquals(0, result.stats.alreadyPromoted)
    }

    @Test
    fun `promotionFailed on RAW read failure`() {
        val expert = validExpert("0001", "user@oxford.ac.uk")
        `when`(emailValidationService.validate("user@oxford.ac.uk"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "0001")).thenReturn(false)
        `when`(writerService.readRawDocument("0001")).thenReturn(null)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(listOf(expert)))

        val result = service.promoteEligibleRawExperts(5)
        assertEquals(1, result.stats.promotionFailed)
        assertEquals(0, result.stats.promoted)
    }

    @Test
    fun `stops at maxPromotions limit`() {
        val experts = (1..5).map { validExpert("000$it", "user$it@oxford.ac.uk") }
        experts.forEach { e ->
            `when`(emailValidationService.validate(e.email!!))
                .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(2, true))
        }
        // first 3 get checked, only first 2 promoted before hitting limit
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "0001")).thenReturn(false)
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "0002")).thenReturn(false)
        `when`(writerService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, "0003")).thenReturn(false)
        ScrollExpertsMockHelper.stubReadRawDocument(writerService,
            mapOf("orcidId" to "x", "email" to "x@x.com", "givenNames" to "A", "familyNames" to "B"))
        ScrollExpertsMockHelper.stubWriteCandidateDocument(writerService, true)
        ScrollExpertsMockHelper.stubScrollExperts(searchService, listOf(experts.take(3), experts.drop(3)))

        val result = service.promoteEligibleRawExperts(maxPromotions = 2)
        assertEquals(2, result.stats.promoted)
        assertEquals(2, result.stats.total)
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
}
