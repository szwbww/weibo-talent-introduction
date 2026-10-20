package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DiscoverySelfAndNameTest {
    private val rules = DiscoveryVerificationRules(ruleClock)
    private fun evaluate(snapshot: VerificationSnapshot, facts: List<VerificationEvidence>, search: ApolloSearchEvidence? = null) =
        rules.evaluate(snapshot, facts, search, ruleVersion = 1)
    private fun snapshot(first: String, last: String, email: String, institution: String = "Rice University") = ruleSnapshot().let {
        it.copy(profile = it.profile.copy(givenNames = first, familyNames = last, email = email, institution = institution,
            identityVerification = DiscoveryIdentity.verified(email, first, last, "ORCID_RECORD_SHA256:" + "a".repeat(64), "0000-0002-1825-0097", null)))
    }
    private fun candidate(first: String, last: String, institution: String = "Rice University", title: String = "Researcher") =
        ApolloCandidate("candidate", first, last, null, "$first $last", title, VerificationInstitution(institution))
    private fun search(vararg candidates: ApolloCandidate) = ApolloSearchEvidence(candidates.toList(), true, true,
        setOf("Ashutosh Sabharwal", "Sabharwal"), matchingVersion = 2, detailsComplete = true)

    @Test fun `historical evidence without version retains institutional only qualification`() {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().findAndRegisterModules()
        val legacy = mapper.readValue("{}", VerificationWorkerEvidence::class.java)
        assertEquals(0, legacy.ruleVersion)
        assertEquals(VerificationOutcome.PENDING, rules.evaluate(ruleSnapshot(), listOf(ruleJob("Professor", AssertionSource.SELF)), ruleVersion = legacy.ruleVersion).outcome)
        assertFalse(rules.sameName("Max Christian Lemme", "Max C. Lemme", 0))
    }
    @Test fun `self professor qualifies without changing evidence provenance`() {
        val fact = ruleJob("Professor", AssertionSource.SELF)
        val result = evaluate(ruleSnapshot(), listOf(fact))
        assertEquals(VerificationOutcome.DIRECT_CLEAR, result.outcome)
        assertEquals(AssertionSource.SELF, result.evidence.single().assertionSource)
        assertEquals(VerificationOutcome.PENDING, evaluate(ruleSnapshot(), listOf(fact.copy(assertionSource = AssertionSource.UNKNOWN))).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(ruleSnapshot(), listOf(fact.copy(temporalScope = TemporalScope.HISTORICAL))).outcome)
    }
    @Test fun `self doctorate still needs age and older researcher still needs professor`() {
        val phd = ruleFact("education", """{"role":"PhD awarded","completed":true}""", AssertionSource.SELF)
        assertEquals(VerificationOutcome.PENDING, evaluate(ruleSnapshot(), listOf(phd)).outcome)
        assertEquals(VerificationOutcome.DIRECT_CLEAR, evaluate(ruleSnapshot(), listOf(phd, ruleFact("birthDate", "1990-01-01", AssertionSource.SELF))).outcome)
        assertEquals(VerificationQualification.NOT_MET, evaluate(ruleSnapshot(), listOf(phd, ruleFact("birthDate", "1980-01-01", AssertionSource.SELF))).qualification)
    }
    @Test fun `middle initials and German spelling retain first and surname checks`() {
        assertTrue(rules.sameName("Max Christian Lemme", "Max C. Lemme"))
        assertTrue(rules.sameName("Max C. Lemme", "Max Christian Lemme"))
        assertTrue(rules.sameName("Markus Kaestner", "Markus Kästner"))
        assertFalse(rules.sameName("Max Daniel Lemme", "Max C. Lemme"))
        assertFalse(rules.sameName("Mark Kaestner", "Markus Kästner"))
        assertFalse(rules.sameName("M Lemme", "Max Lemme"))
        assertFalse(rules.sameName("Ashu Sabharwal", "Ashutosh Sabharwal"))
    }
    @Test fun `middle initial ORCID primary name no longer blocks qualification`() {
        val s = snapshot("Max C.", "Lemme", "max@example.invalid")
        assertEquals(VerificationOutcome.DIRECT_CLEAR, evaluate(s, listOf(ruleFact("name", "Max Christian Lemme"), ruleJob("Professor", AssertionSource.SELF))).outcome)
    }
    @Test fun `nickname evidence must match the entire compound family name`() {
        val s = snapshot("Alexander", "van den Berg", "alex@rice.edu")
        assertEquals(VerificationOutcome.PENDING, evaluate(s, emptyList(), search(candidate("Alex", "Berg"))).outcome)
        assertEquals(VerificationOutcome.APOLLO_CLEAR, evaluate(s, emptyList(), search(candidate("Alex", "van den Berg"))).outcome)
    }
    @Test fun `email corroborated given name plus full surname and institution identifies Ashu`() {
        val s = snapshot("Ashutosh", "Sabharwal", "ashu@rice.edu")
        val result = evaluate(s, emptyList(), search(candidate("Ashu", "Sabharwal")))
        assertEquals(VerificationOutcome.APOLLO_CLEAR, result.outcome)
        assertTrue(result.reasonCodes.contains("APOLLO_EMAIL_GIVEN_NAME_MATCH"))
        assertEquals(VerificationOutcome.PENDING, evaluate(s, emptyList(), search(candidate("Sage", "Sabharwal"))).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(s, emptyList(), search(candidate("Ashu", "Sabharwal", "Other University", "Professor"))).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(s, emptyList(), search(candidate("Ashu", "Sharma"))).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(s, emptyList(), search(candidate("Ashu", "Sabharwal"), candidate("Ashu", "Sabharwal").copy(id="second"))).outcome)
    }
}
