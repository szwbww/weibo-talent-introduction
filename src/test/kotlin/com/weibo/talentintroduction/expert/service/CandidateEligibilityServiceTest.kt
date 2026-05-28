package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CandidateEligibilityServiceTest {
    private val service = CandidateEligibilityService(CandidateFilterProperties())

    @Test
    fun `candidate must have non chinese country and valid email by default`() {
        assertTrue(service.isEligibleForCandidateIndex(expert(age = null, degree = null)))
        assertFalse(service.isEligibleForCandidateIndex(expert(nationality = "China")))
        assertFalse(service.isEligibleForCandidateIndex(expert(email = "invalid-email")))
    }

    @Test
    fun `doctoral degree filter is enforced only when enabled`() {
        val degreeFilteredService = CandidateEligibilityService(
            CandidateFilterProperties(requireDoctoralDegree = true)
        )

        assertTrue(degreeFilteredService.isEligibleForCandidateIndex(expert(degree = "PhD")))
        assertFalse(degreeFilteredService.isEligibleForCandidateIndex(expert(degree = null)))
        assertFalse(degreeFilteredService.isEligibleForCandidateIndex(expert(degree = "Master")))
    }

    @Test
    fun `age filter is enforced only when enabled`() {
        val ageFilteredService = CandidateEligibilityService(
            CandidateFilterProperties(enableAgeFilter = true, maxAgeExclusive = 70)
        )

        assertTrue(ageFilteredService.isEligibleForCandidateIndex(expert(age = 69)))
        assertFalse(ageFilteredService.isEligibleForCandidateIndex(expert(age = 70)))
        assertFalse(ageFilteredService.isEligibleForCandidateIndex(expert(age = null)))
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
