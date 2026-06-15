package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.AcademicFilterProperties
import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class CandidateEligibilityServiceTest {
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
    fun `candidate must have non chinese country and valid email by default`() {
        val svc = service()
        assertTrue(svc.isEligibleForCandidateIndex(expert(age = null, degree = null)))
        assertFalse(svc.isEligibleForCandidateIndex(expert(nationality = "China")))
        assertFalse(svc.isEligibleForCandidateIndex(expert(email = "invalid-email")))
    }

    @Test
    fun `doctoral degree filter is enforced only when enabled`() {
        val svc = service(CandidateFilterProperties(requireDoctoralDegree = true))
        assertTrue(svc.isEligibleForCandidateIndex(expert(degree = "PhD")))
        assertFalse(svc.isEligibleForCandidateIndex(expert(degree = null)))
        assertFalse(svc.isEligibleForCandidateIndex(expert(degree = "Master")))
    }

    @Test
    fun `age filter is enforced only when enabled`() {
        val svc = service(CandidateFilterProperties(enableAgeFilter = true, maxAgeExclusive = 70))
        assertTrue(svc.isEligibleForCandidateIndex(expert(age = 69)))
        assertFalse(svc.isEligibleForCandidateIndex(expert(age = 70)))
        assertFalse(svc.isEligibleForCandidateIndex(expert(age = null)))
    }

    private fun expert(
        email: String = "expert@example.com",
        degree: String? = "Doctoral Degree",
        age: Int? = 45,
        nationality: String = "United States"
    ): ExpertProfile =
        ExpertProfile(
            orcidId = "0000-0001",
            email = email,
            givenNames = "Ada",
            familyNames = "Lovelace",
            country = nationality,
            keyword = "computer science",
            employment = "University",
            age = age,
            degree = degree,
            nationality = nationality
        )
}
