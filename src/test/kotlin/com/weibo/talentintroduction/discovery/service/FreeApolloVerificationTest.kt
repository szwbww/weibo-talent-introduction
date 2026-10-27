package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.discovery.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FreeApolloVerificationTest {
    private val rules = DiscoveryVerificationRules(ruleClock)
    private val candidate = ApolloCandidate("one", "Ada", "Lee", null, "Ada Lee", null, VerificationInstitution("Example University"))
    private fun page(org: String? = null, people: List<ApolloCandidate> = listOf(candidate), total: Long = people.size.toLong(), name: String = "Ada Lee") =
        ApolloQueryResult(ApolloSearchQuery(name, institution = org?.let(::VerificationInstitution)), ApolloSearchPage(people, total, 1), 123L)
    private fun search(vararg pages: ApolloQueryResult) = ApolloSearchEvidence(candidates = pages.flatMap { it.result.candidates }.distinctBy { it.id },
        matchingVersion = 2, institutionAliasVersion = 2, freeSearchOnly = true, firstPages = pages.toList())
    private fun evaluate(s: ApolloSearchEvidence, evidence: List<VerificationEvidence> = listOf(ruleJob()), version: Int = 4) =
        rules.evaluate(ruleSnapshot(), evidence, s, ruleVersion = version)

    @Test fun `free unique name institution is clear and name only is unclear without paid detail`() {
        val narrow = evaluate(search(page("Example University")))
        assertEquals(VerificationOutcome.APOLLO_CLEAR, narrow.outcome)
        assertEquals(listOf("APOLLO_FREE_NAME_INSTITUTION_UNIQUE"), narrow.reasonCodes)
        assertEquals(VerificationQualification.UNKNOWN, narrow.qualification)
        val broad = evaluate(search(page()))
        assertEquals(VerificationOutcome.APOLLO_UNCLEAR, broad.outcome)
        assertEquals("one", broad.matchedPersonId)
        assertEquals("APOLLO_FREE_NAME_UNIQUE", broad.trace!!.candidates.single().chosenReason)
    }
    @Test fun `total entries variants identity conflicts and incomplete responses cannot masquerade as unique`() {
        assertEquals(VerificationOutcome.PENDING, evaluate(search(page(total = 2))).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(search(page(people = emptyList(), total = 1))).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(search()).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(search(page(people = listOf(candidate.copy(fullName = "Other Person", firstName = "Other", lastName = "Person"))))).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(search(page(people = listOf(candidate.copy(orcid = "0000-0001-5109-3700"))))).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(search(page("Example University", listOf(candidate.copy(institution = VerificationInstitution("Other University")))))).outcome)
        val alias = ruleFact("name", "Ada L. Lee")
        assertEquals(VerificationOutcome.PENDING, evaluate(search(page()), listOf(alias)).outcome)
        assertEquals(VerificationOutcome.APOLLO_CLEAR, evaluate(search(page(total = 20), page("Example University"))).outcome)
    }
    @Test fun `new fields roundtrip and older rule version retains its paid detail requirement`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        val saved = search(page("Example University"))
        assertEquals(evaluate(saved), evaluate(mapper.readValue(mapper.writeValueAsString(saved))))
        assertEquals(VerificationOutcome.PENDING, evaluate(saved, version = 3).outcome)
        assertEquals(VerificationOutcome.PENDING, evaluate(saved.copy(freeSearchOnly = false)).outcome)
        assertFalse(mapper.readValue<ApolloSearchEvidence>("{}").freeSearchOnly)
        val paid = saved.copy(freeSearchOnly = false, complete = true, broadComplete = true, detailsComplete = true, queryNames = setOf("Ada Lee"))
        assertEquals(VerificationOutcome.APOLLO_CLEAR, evaluate(paid).outcome)
    }
    @Test fun `retained first pages reject failed detail and malformed records without fabricating requests`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        val q = page()
        val a = VerificationAttempt(123, 42, "request", VerificationProvider.APOLLO, VerificationQueryKind.BROAD, 1, 1,
            ruleClock.instant(), ruleClock.instant(), 200, "OK", mapper.writeValueAsString(q.query), mapper.writeValueAsString(q.result), "a".repeat(64))
        assertEquals(listOf(q), FreeApolloVerification.retainedPages(listOf(a)))
        assertTrue(FreeApolloVerification.retainedPages(listOf(a.copy(httpStatus = 422), a.copy(queryKind = VerificationQueryKind.DETAIL), a.copy(responseJson = "{}"))).isEmpty())
    }
    @Test fun `title decorations recognize current professor evidence but preserve negative history and old policy`() {
        listOf("Associate Professor (Tenured)", "Professor & Director", "Chair Professor", "Allan and Myra Ferguson Professor", "Scientific Staff, Assistant Professor, Universitair docent").forEach { role ->
            assertEquals(VerificationOutcome.DIRECT_CLEAR, rules.evaluate(ruleSnapshot(), listOf(ruleJob(role, AssertionSource.SELF)), ruleVersion = 4).outcome, role)
            assertEquals(VerificationOutcome.PENDING, rules.evaluate(ruleSnapshot(), listOf(ruleJob(role)), ruleVersion = 3).outcome, role)
            assertEquals(VerificationOutcome.PENDING, rules.evaluate(ruleSnapshot(), listOf(ruleJob(role, temporal = TemporalScope.HISTORICAL)), ruleVersion = 4).outcome)
        }
        listOf("Assistant to Professor", "Former Professor", "Professor emeritus", "Not a Professor", "Professor qualification", "PhD student with Professor Smith", "Researcher supervised by Professor Smith").forEach { role ->
            assertEquals(VerificationOutcome.PENDING, rules.evaluate(ruleSnapshot(), listOf(ruleJob(role)), ruleVersion = 4).outcome, role)
        }
    }
}
