package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.AcademicFilterProperties
import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

class CandidateEligibilityServiceEnhancedTest {

    private val emailValidationService = mock(EmailValidationService::class.java)

    private fun service(
        candidate: CandidateFilterProperties = CandidateFilterProperties(),
        academic: AcademicFilterProperties = AcademicFilterProperties()
    ): CandidateEligibilityService {
        val filterService = mock(EligibilityFilterService::class.java)
        `when`(filterService.getCandidateFilter()).thenReturn(candidate)
        `when`(filterService.getAcademicFilter()).thenReturn(academic)
        return CandidateEligibilityService(filterService, emailValidationService)
    }

    @Test
    fun `evaluateEligibility returns reject reasons for multiple failures`() {
        val svc = service()
        val expert = ExpertProfile(
            orcidId = "", email = "bad", givenNames = "Test", familyNames = "User",
            country = "CN", keyword = null, employment = null, nationality = "Chinese"
        )
        val result = svc.evaluateEligibility(expert)
        assertFalse(result.eligible)
        assertTrue(result.rejectReasons.contains("MISSING_ORCID"))
        assertTrue(result.rejectReasons.contains("INVALID_EMAIL_FORMAT"))
        assertTrue(result.rejectReasons.contains("CHINESE_NATIONALITY"))
    }

    @Test
    fun `evaluateEligibility passes for valid expert`() {
        val svc = service()
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, nationality = "British"
        )
        val result = svc.evaluateEligibility(expert)
        assertTrue(result.eligible)
        assertTrue(result.rejectReasons.isEmpty())
    }

    @Test
    fun `disposable email rejected when requireValidEmail is true`() {
        `when`(emailValidationService.isDisposableEmail("user@guerrillamail.com")).thenReturn(true)
        val svc = service()
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "user@guerrillamail.com",
            givenNames = "Test", familyNames = "User", country = "US",
            keyword = null, employment = null
        )
        val result = svc.evaluateEligibility(expert)
        assertFalse(result.eligible)
        assertTrue(result.rejectReasons.contains("DISPOSABLE_EMAIL"))
    }

    @Test
    fun `isEligibleForCandidateIndex backward compatible`() {
        val svc = service()
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null
        )
        assertTrue(svc.isEligibleForCandidateIndex(expert))
    }

    @Test
    fun `h-index filter works when enabled`() {
        val svc = service(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableHIndexFilter = true, minHIndex = 10)
        )
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, hIndex = 3
        )
        val result = svc.evaluateEligibility(expert)
        assertFalse(result.eligible)
        assertTrue(result.rejectReasons.contains("H_INDEX_TOO_LOW"))
    }

    @Test
    fun `h-index filter passes when above threshold`() {
        val svc = service(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableHIndexFilter = true, minHIndex = 10)
        )
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, hIndex = 15
        )
        val result = svc.evaluateEligibility(expert)
        assertTrue(result.eligible)
    }

    @Test
    fun `citation count filter rejects when below threshold`() {
        val svc = service(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableCitationFilter = true, minCitationCount = 100)
        )
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, citationCount = 30
        )
        val result = svc.evaluateEligibility(expert)
        assertFalse(result.eligible)
        assertTrue(result.rejectReasons.contains("CITATION_COUNT_TOO_LOW"))
    }

    @Test
    fun `activity filter rejects inactive expert`() {
        val svc = service(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableActivityFilter = true, recentYearsThreshold = 5)
        )
        val cutoff = java.time.Year.now().value - 5
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, lastPublicationYear = cutoff - 1
        )
        val result = svc.evaluateEligibility(expert)
        assertFalse(result.eligible)
        assertTrue(result.rejectReasons.contains("INACTIVE"))
    }

    @Test
    fun `activity filter passes at exact cutoff year`() {
        val svc = service(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableActivityFilter = true, recentYearsThreshold = 5)
        )
        val cutoff = java.time.Year.now().value - 5
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, lastPublicationYear = cutoff
        )
        val result = svc.evaluateEligibility(expert)
        assertTrue(result.eligible)
    }

    @Test
    fun `activity filter rejects null lastPublicationYear`() {
        val svc = service(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableActivityFilter = true, recentYearsThreshold = 5)
        )
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, lastPublicationYear = null
        )
        val result = svc.evaluateEligibility(expert)
        assertFalse(result.eligible)
        assertTrue(result.rejectReasons.contains("INACTIVE"))
    }

    @Test
    fun `activity filter disabled with null year passes`() {
        val svc = service()
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, lastPublicationYear = null
        )
        val result = svc.evaluateEligibility(expert)
        assertTrue(result.eligible)
    }

    @Test
    fun `activity filter passes for recent publication`() {
        val svc = service(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableActivityFilter = true, recentYearsThreshold = 5)
        )
        val year = java.time.Year.now().value
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, lastPublicationYear = year
        )
        val result = svc.evaluateEligibility(expert)
        assertTrue(result.eligible)
    }

    @Test
    fun `academic filters disabled by default`() {
        val svc = service()
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, hIndex = 0, citationCount = 0, lastPublicationYear = 2000
        )
        val result = svc.evaluateEligibility(expert)
        assertTrue(result.eligible)
    }

    @Test
    fun `EligibilityResult companion pass and reject`() {
        val pass = com.weibo.talentintroduction.expert.domain.EligibilityResult.pass()
        assertTrue(pass.eligible)
        assertTrue(pass.rejectReasons.isEmpty())

        val reject = com.weibo.talentintroduction.expert.domain.EligibilityResult.reject("A", "B")
        assertFalse(reject.eligible)
        assertEquals(2, reject.rejectReasons.size)
        assertTrue(reject.rejectReasons.contains("A"))
    }
}
