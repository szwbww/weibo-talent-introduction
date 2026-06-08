package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.AcademicFilterProperties
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class ExpertIndexPromotionServiceTest {
    private val properties = ElasticsearchProperties(
        baseUrl = "https://es.example.com:9200",
        username = "elastic",
        password = "secret",
        rawIndexName = "orcid_info",
        candidateIndexName = "orcid_info_candidate",
        applicationIndexName = "orcid_info_application"
    )
    private val service = ExpertIndexPromotionService(
        ExpertIndexService(
            properties,
            Mockito.mock(org.springframework.web.client.RestTemplate::class.java),
            com.fasterxml.jackson.databind.ObjectMapper()
        ),
        CandidateEligibilityService(
            CandidateFilterProperties(),
            AcademicFilterProperties(),
            Mockito.mock(EmailValidationService::class.java)
        )
    )

    @Test
    fun `candidate validation requires level two eligibility`() {
        assertTrue(service.validateForCandidate(expert(email = "expert@example.com")))
        assertFalse(service.validateForCandidate(expert(email = null)))
    }

    @Test
    fun `application validation requires reply`() {
        assertFalse(service.validateForApplication(expert(email = "expert@example.com"), hasReply = false))
        assertTrue(service.validateForApplication(expert(email = "expert@example.com"), hasReply = true))
    }

    private fun expert(email: String?): ExpertProfile =
        ExpertProfile(
            orcidId = "0000-0001",
            email = email,
            givenNames = "Ada",
            familyNames = "Lovelace",
            country = null,
            keyword = null,
            employment = null,
            age = null,
            degree = null,
            nationality = "United States"
        )
}
