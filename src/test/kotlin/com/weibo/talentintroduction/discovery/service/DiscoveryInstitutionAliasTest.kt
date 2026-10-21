package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DiscoveryInstitutionAliasTest {
    private val rules = DiscoveryVerificationRules(ruleClock)
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val english = VerificationInstitution("Technical University of Munich")
    private val german = VerificationInstitution("Technische Universität München")

    @Test fun `verified bilingual names match only with frozen alias version`() {
        assertNotEquals(InstitutionComparison.MATCH, DiscoveryInstitutionNormalizer.compare(english, german))
        assertEquals(InstitutionComparison.MATCH, DiscoveryInstitutionNormalizer.compareWithAliases(english, german, 1))
        assertEquals(InstitutionComparison.MATCH, DiscoveryInstitutionNormalizer.compareWithAliases(german, english, 1))
        assertEquals(InstitutionComparison.MATCH, DiscoveryInstitutionNormalizer.compareWithAliases(
            english.copy(name = "Department of Physics, Technical University of Munich, Germany"), german, 1))
        listOf("University of Munich", "Ludwig-Maximilians-Universität München", "Technical University of Berlin",
            "TUM", "Technical University of Munich and Example University", "Technical University of Munich Asia").forEach {
            assertNotEquals(InstitutionComparison.MATCH, DiscoveryInstitutionNormalizer.compareWithAliases(VerificationInstitution(it), german, 1), it)
        }
        assertEquals(InstitutionComparison.DIFFERENT, DiscoveryInstitutionNormalizer.compareWithAliases(
            english.copy(standardId = "ROR:a"), german.copy(standardId = "ROR:b"), 1))
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryInstitutionNormalizer.compareWithAliases(english, german, DiscoveryInstitutionNormalizer.ALIAS_VERSION + 1)
        }
    }

    @Test fun `Patrick and Nils observed histories qualify without changing qualification or legacy result`() {
        listOf(Triple("Patrick", "Ebel", "2023-01-01"), Triple("Nils", "Quetschlich", "2025-08-01")).forEach { (first, last, end) ->
            val email = "$first.$last@example.invalid"
            val snapshot = ruleSnapshot().let { it.copy(profile = it.profile.copy(givenNames = first, familyNames = last,
                email = email, institution = english.name, identityVerification = DiscoveryIdentity.verified(email, first, last,
                    "SOURCE_SHA256:" + "a".repeat(64), null, null))) }
            val person = ApolloCandidate("person", first, last, null, "$first $last", "Researcher", VerificationInstitution("Google"),
                employmentHistory = listOf(ApolloEmployment(german, "Researcher", false, "2019-01-01", end)))
            val old = ApolloSearchEvidence(listOf(person), true, true, setOf("$first $last"), matchingVersion = 2, detailsComplete = true)
            val fresh = old.copy(institutionAliasVersion = 1)
            assertEquals(VerificationOutcome.PENDING, rules.evaluate(snapshot, emptyList(), old).outcome)
            val result = rules.evaluate(snapshot, emptyList(), fresh)
            assertEquals(VerificationOutcome.APOLLO_CLEAR, result.outcome)
            assertEquals(VerificationQualification.UNKNOWN, result.qualification)
            assertTrue("APOLLO_HISTORICAL_INSTITUTION_MATCH" in result.reasonCodes)
            assertTrue(DiscoveryInstitutionNormalizer.TUM_ALIAS_REASON in result.reasonCodes)
            assertEquals(result, rules.evaluate(snapshot, emptyList(), mapper.readValue<ApolloSearchEvidence>(mapper.writeValueAsString(fresh))))
            assertEquals(0, mapper.readValue<ApolloSearchEvidence>("{}").institutionAliasVersion)
            assertEquals(listOf("IDENTITY_AMBIGUOUS"), rules.evaluate(snapshot, emptyList(), fresh.copy(candidates = listOf(person, person.copy(id = "other")))).reasonCodes)
            val future = person.copy(employmentHistory = listOf(person.employmentHistory.single().copy(endDate = "2030-01-01")))
            assertEquals(VerificationOutcome.PENDING, rules.evaluate(snapshot, emptyList(), fresh.copy(candidates = listOf(future))).outcome)
            val current = person.copy(institution = german, employmentHistory = emptyList())
            assertTrue("APOLLO_CURRENT_INSTITUTION_MATCH" in rules.evaluate(snapshot, emptyList(), fresh.copy(candidates = listOf(current))).reasonCodes)
        }
    }
}
