package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.AcademicFilterProperties
import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

class CandidateEligibilityServiceEnhancedTest {

    private val emailValidationService = mock(EmailValidationService::class.java)
    private val service = CandidateEligibilityService(
        CandidateFilterProperties(), AcademicFilterProperties(), emailValidationService
    )

    @Test
    fun `evaluateEligibility returns reject reasons for multiple failures`() {
        val expert = ExpertProfile(
            orcidId = "", email = "bad", givenNames = "Test", familyNames = "User",
            country = "CN", keyword = null, employment = null, nationality = "Chinese"
        )
        val result = service.evaluateEligibility(expert)
        assertFalse(result.eligible)
        assertTrue(result.rejectReasons.contains("MISSING_ORCID"))
        assertTrue(result.rejectReasons.contains("INVALID_EMAIL_FORMAT"))
        assertTrue(result.rejectReasons.contains("CHINESE_NATIONALITY"))
    }

    @Test
    fun `evaluateEligibility passes for valid expert`() {
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, nationality = "British"
        )
        val result = service.evaluateEligibility(expert)
        assertTrue(result.eligible)
        assertTrue(result.rejectReasons.isEmpty())
    }

    @Test
    fun `disposable email rejected when requireValidEmail is true`() {
        `when`(emailValidationService.isDisposableEmail("user@guerrillamail.com")).thenReturn(true)

        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "user@guerrillamail.com",
            givenNames = "Test", familyNames = "User", country = "US",
            keyword = null, employment = null
        )
        val result = service.evaluateEligibility(expert)
        assertFalse(result.eligible)
        assertTrue(result.rejectReasons.contains("DISPOSABLE_EMAIL"))
    }

    @Test
    fun `isEligibleForCandidateIndex backward compatible`() {
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null
        )
        assertTrue(service.isEligibleForCandidateIndex(expert))
    }

    @Test
    fun `h-index filter works when enabled`() {
        val hIndexService = CandidateEligibilityService(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableHIndexFilter = true, minHIndex = 10),
            emailValidationService
        )
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, hIndex = 3
        )
        val result = hIndexService.evaluateEligibility(expert)
        assertFalse(result.eligible)
        assertTrue(result.rejectReasons.contains("H_INDEX_TOO_LOW"))
    }

    @Test
    fun `h-index filter passes when above threshold`() {
        val hIndexService = CandidateEligibilityService(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableHIndexFilter = true, minHIndex = 10),
            emailValidationService
        )
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, hIndex = 15
        )
        val result = hIndexService.evaluateEligibility(expert)
        assertTrue(result.eligible)
    }

    @Test
    fun `citation count filter rejects when below threshold`() {
        val citService = CandidateEligibilityService(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableCitationFilter = true, minCitationCount = 100),
            emailValidationService
        )
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, citationCount = 30
        )
        val result = citService.evaluateEligibility(expert)
        assertFalse(result.eligible)
        assertTrue(result.rejectReasons.contains("CITATION_COUNT_TOO_LOW"))
    }

    @Test
    fun `activity filter rejects inactive expert`() {
        val actService = CandidateEligibilityService(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableActivityFilter = true, recentYearsThreshold = 5),
            emailValidationService
        )
        val cutoff = java.time.Year.now().value - 5
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, lastPublicationYear = cutoff - 1
        )
        val result = actService.evaluateEligibility(expert)
        assertFalse(result.eligible)
        assertTrue(result.rejectReasons.contains("INACTIVE"))
    }

    @Test
    fun `activity filter passes at exact cutoff year`() {
        val actService = CandidateEligibilityService(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableActivityFilter = true, recentYearsThreshold = 5),
            emailValidationService
        )
        val cutoff = java.time.Year.now().value - 5
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, lastPublicationYear = cutoff
        )
        val result = actService.evaluateEligibility(expert)
        assertTrue(result.eligible)
    }

    @Test
    fun `activity filter rejects null lastPublicationYear`() {
        val actService = CandidateEligibilityService(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableActivityFilter = true, recentYearsThreshold = 5),
            emailValidationService
        )
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, lastPublicationYear = null
        )
        val result = actService.evaluateEligibility(expert)
        assertFalse(result.eligible)
        assertTrue(result.rejectReasons.contains("INACTIVE"))
    }

    @Test
    fun `activity filter disabled with null year passes`() {
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, lastPublicationYear = null
        )
        val result = service.evaluateEligibility(expert)
        assertTrue(result.eligible)
    }

    @Test
    fun `activity filter passes for recent publication`() {
        val actService = CandidateEligibilityService(
            CandidateFilterProperties(requireDoctoralDegree = false, requireValidEmail = false),
            AcademicFilterProperties(enableActivityFilter = true, recentYearsThreshold = 5),
            emailValidationService
        )
        val year = java.time.Year.now().value
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, lastPublicationYear = year
        )
        val result = actService.evaluateEligibility(expert)
        assertTrue(result.eligible)
    }

    @Test
    fun `academic filters disabled by default`() {
        val expert = ExpertProfile(
            orcidId = "0000-0001-2345-6789", email = "john@oxford.ac.uk",
            givenNames = "John", familyNames = "Smith", country = "GB",
            keyword = null, employment = null, hIndex = 0, citationCount = 0, lastPublicationYear = 2000
        )
        val result = service.evaluateEligibility(expert)
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
