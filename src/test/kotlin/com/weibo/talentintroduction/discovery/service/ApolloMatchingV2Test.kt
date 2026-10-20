package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.discovery.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApolloMatchingV2Test {
    private val rules = DiscoveryVerificationRules(ruleClock)
    private fun candidate(title: String? = null, institution: String = "Other University") =
        ApolloCandidate("person-1", "Ada", "Lee", null, "Ada Lee", title, VerificationInstitution(institution))
    private fun search(vararg candidates: ApolloCandidate, fields: String? = null) = ApolloSearchEvidence(
        candidates.toList(), true, true, setOf("Ada Lee"), matchingVersion = 2, detailsComplete = true,
        academicResearchFields = fields)
    private fun evaluate(search: ApolloSearchEvidence) = rules.evaluate(ruleSnapshot(), emptyList(), search)

    @Test fun `four authorized routes remain distinct from qualification`() {
        val current = candidate(institution = "Example University")
        val historical = candidate().copy(employmentHistory = listOf(ApolloEmployment(
            VerificationInstitution("Example University"), "Researcher", false, "2010-01-01", "2020-01-01")))
        val professor = candidate("Professor of Photonics")
        val specialty = candidate("Research Scientist in Photonics")
        listOf(current to "APOLLO_CURRENT_INSTITUTION_MATCH", historical to "APOLLO_HISTORICAL_INSTITUTION_MATCH",
            professor to "APOLLO_PROFESSOR_MATCH", specialty to "APOLLO_SPECIALTY_MATCH").forEach { (person, reason) ->
            val result = evaluate(search(person, fields = "Photonics"))
            assertEquals(if (person == specialty) VerificationOutcome.APOLLO_UNCLEAR else VerificationOutcome.APOLLO_CLEAR, result.outcome)
            assertEquals(VerificationQualification.UNKNOWN, result.qualification)
            assertTrue(reason in result.reasonCodes)
            assertEquals(person.id, result.matchedPersonId)
        }
    }
    @Test fun `name only ambiguity incomplete search and unresolved masked alternatives cannot admit`() {
        assertEquals(VerificationOutcome.PENDING, evaluate(search(candidate())).outcome)
        val one = candidate("Professor")
        assertEquals(listOf("IDENTITY_AMBIGUOUS"), evaluate(search(one, one.copy(id = "person-2"))).reasonCodes)
        val masked = candidate("Professor").copy(id = "masked", lastName = null, fullName = null, obfuscatedLastName = "L**")
        assertEquals(VerificationOutcome.PENDING, evaluate(search(one, masked)).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(search(masked)).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(search(one).copy(detailsComplete = false)).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(search(one).copy(complete = false)).outcome)
    }
    @Test fun `professor condition uses role not incidental mentions and professional terms match whole phrases`() {
        listOf("Professor", "Associate Professor", "Professor of Photonics", "Assistant Professor in Physics", "副教授").forEach {
            assertEquals(VerificationOutcome.APOLLO_CLEAR, evaluate(search(candidate(it))).outcome, it)
        }
        listOf("Assistant to Professor", "Professor Emeritus", "Former Professor", "Researcher in Professor Lee lab").forEach {
            assertEquals(VerificationOutcome.PENDING, evaluate(search(candidate(it))).outcome, it)
        }
        assertEquals(VerificationOutcome.PENDING, evaluate(search(candidate("Engineering Researcher"), fields = "Engineering")).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(search(candidate("Chair of Biology"), fields = "AI")).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(search(candidate("Astrophysics researcher"), fields = "Physics")).outcome)
    }
    @Test fun `future or undated history and wrong identity cannot qualify`() {
        val history = ApolloEmployment(VerificationInstitution("Example University"), "Researcher", false, "2020-01-01", "2030-01-01")
        listOf(history, history.copy(endDate = null)).forEach {
            assertEquals(VerificationOutcome.PENDING, evaluate(search(candidate().copy(employmentHistory = listOf(it)))).outcome)
        }
        assertEquals(listOf("ORCID_CONFLICT"), evaluate(search(candidate("Professor").copy(orcid = "0000-0001-5109-3700"))).reasonCodes)
        assertEquals(VerificationOutcome.PENDING, evaluate(search(candidate("Professor").copy(fullName = "Other Person"))).outcome)
    }
    @Test fun `legacy payload retains original classification and new payload survives serialization`() {
        val legacy = ruleSearch()
        assertEquals(VerificationOutcome.APOLLO_UNCLEAR, rules.evaluate(ruleSnapshot(), listOf(ruleJob()), legacy).outcome)
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val saved = search(candidate("Professor of Physics"), fields = "Physics")
        val roundtrip: ApolloSearchEvidence = mapper.readValue(mapper.writeValueAsString(saved))
        assertEquals(evaluate(saved), evaluate(roundtrip))
        assertEquals(1, mapper.readValue<ApolloSearchEvidence>("{}").matchingVersion)
        assertThrows(IllegalArgumentException::class.java) { evaluate(saved.copy(matchingVersion = 99)) }
    }
    @Test fun `Apollo variable length surname mask is a query hint never sufficient alone`() {
        val masked = ApolloCandidate("r", "Robert", null, "Ha***d", null, "Professor of Photonics", VerificationInstitution("University of Glasgow"))
        assertTrue(rules.nameMatches(masked, setOf("Robert H. Hadfield", "Robert Hadfield"), setOf("Robert Hadfield"), true))
        assertFalse(rules.nameMatches(masked, setOf("Robert Hadfield"), setOf("Robert Hadfield"), false))
        assertFalse(rules.nameMatches(masked, setOf("Robert Smith"), setOf("Robert Smith"), true))
        val strong = candidate("Professor")
        assertEquals(VerificationOutcome.APOLLO_CLEAR, evaluate(search(strong,
            candidate("IT Director").copy(id = "unrelated", fullName = null, lastName = null, obfuscatedLastName = "L**"))).outcome)
    }
    @Test fun `observed Duncan and Robert API detail shapes qualify with middle initial source names`() {
        data class Example(val given: String, val family: String, val full: String, val orcid: String, val institution: String,
            val orcidInstitution: String, val title: String, val personId: String)
        val examples = listOf(
            Example("Duncan", "S. Sutherland", "Duncan Sutherland", "0000-0002-5045-9915", "Aarhus University", "Aarhus Universitet", "Professor", "61382888870a120001be1c7e"),
            Example("Robert", "H. Hadfield", "Robert Hadfield", "0000-0002-8084-4187", "University of Glasgow", "University of Glasgow", "Professor of Photonics", "54a418c97468692cf02b022c"))
        examples.forEach { example ->
            val proof = com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.verified("fixture@example.invalid",
                example.given, example.family, "ORCID_RECORD_SHA256:" + "a".repeat(64), example.orcid, null)
            val snapshot = ruleSnapshot().let { it.copy(profile = it.profile.copy(givenNames = example.given,
                familyNames = example.family, email = "fixture@example.invalid", orcidId = example.orcid,
                institution = example.institution, identityVerification = proof)) }
            val person = ApolloCandidate(example.personId, example.given, example.full.substringAfter(' '), null,
                example.full, example.title, VerificationInstitution(example.institution))
            val facts = listOf(ruleFact("name", example.full), ruleFact("employment", jacksonObjectMapper().writeValueAsString(
                mapOf("orcid" to example.orcid, "institution" to example.orcidInstitution, "role" to example.title)), AssertionSource.SELF))
            val result = rules.evaluate(snapshot, facts, search(person).copy(queryNames = setOf(snapshot.fullName, example.full)))
            assertEquals(VerificationOutcome.APOLLO_CLEAR, result.outcome, example.full)
            assertEquals(example.personId, result.matchedPersonId)
            assertTrue("APOLLO_CURRENT_INSTITUTION_MATCH" in result.reasonCodes)
        }
    }
    @Test fun `new matching result accepts unknown qualification without weakening legacy contract`() {
        val json = jacksonObjectMapper().writeValueAsString(VerificationWorkerEvidence(search = search(candidate("Professor"))))
        VerificationResult(VerificationState.COMPLETED, VerificationOutcome.APOLLO_CLEAR, VerificationQualification.UNKNOWN,
            VerificationRoute.APOLLO, json, "[]", personMatchedAt = ruleClock.instant(), apolloPersonId = "person-1")
        assertThrows(IllegalArgumentException::class.java) {
            VerificationResult(VerificationState.COMPLETED, VerificationOutcome.APOLLO_CLEAR, VerificationQualification.UNKNOWN,
                VerificationRoute.APOLLO, "{}", "[]", personMatchedAt = ruleClock.instant())
        }
    }
    @Test fun `institution subject extraction handles department address without a translation guess`() {
        val result = evaluate(search(candidate(institution = "Department of Physics, Example University, United Kingdom")))
        assertEquals(VerificationOutcome.APOLLO_CLEAR, result.outcome)
        assertTrue("APOLLO_CURRENT_INSTITUTION_MATCH" in result.reasonCodes)
    }
}
