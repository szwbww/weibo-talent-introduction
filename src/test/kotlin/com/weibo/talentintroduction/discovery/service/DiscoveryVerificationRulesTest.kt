package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

val ruleClock: Clock = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC)
fun ruleSnapshot(): VerificationSnapshot {
    val proof = DiscoveryIdentity.verified("ada@example.invalid", "Ada", "Lee", "ORCID_RECORD_SHA256:" + "a".repeat(64), "0000-0002-1825-0097", null)
    return VerificationSnapshot("b".repeat(64), "c".repeat(64), ExpertProfile(orcidId = "0000-0002-1825-0097", email = "ada@example.invalid",
        givenNames = "Ada", familyNames = "Lee", country = null, keyword = null, employment = null,
        institution = "Example University", identityVerification = proof))
}
fun ruleFact(field: String, value: String, source: AssertionSource = AssertionSource.ORGANIZATION,
    temporal: TemporalScope = TemporalScope.CURRENT) = VerificationEvidence("b".repeat(64), "c".repeat(64), "ORCID",
    "https://pub.orcid.org/v3.0/0000-0002-1825-0097/employments", ruleClock.instant(), "d".repeat(64), field, value, source, temporal)
fun ruleJob(role: String = "Researcher", source: AssertionSource = AssertionSource.ORGANIZATION,
    temporal: TemporalScope = TemporalScope.CURRENT, institution: String = "Example University") = ruleFact("employment",
    jacksonObjectMapper().writeValueAsString(mapOf("orcid" to "0000-0002-1825-0097", "role" to role, "institution" to institution)), source, temporal)
fun ruleSearch(masked: Boolean = false, count: Int = 1, complete: Boolean = true, institution: String = "Example University") = ApolloSearchEvidence(
    (1..count).map { ApolloCandidate("person-$it", "Ada", if (masked) null else "Lee", if (masked) "L**" else null,
        if (masked) null else "Ada Lee", "Professor", VerificationInstitution(institution)) }, complete, complete, setOf("Ada Lee"))

class DiscoveryVerificationRulesTest {
    private val rules = DiscoveryVerificationRules(ruleClock)
    @Test fun `fixed annotated fixtures replay identically with auditable fields`() {
        val fixtures = jacksonObjectMapper().readTree(javaClass.getResourceAsStream("/discovery/apollo-verification-cases.json"))
        fixtures.path("cases").forEach { case ->
            val facts = mutableListOf<VerificationEvidence>()
            if (case.path("job").asBoolean(true)) facts += ruleJob(case.path("role").asText("Researcher"),
                AssertionSource.valueOf(case.path("source").asText("ORGANIZATION")), TemporalScope.valueOf(case.path("scope").asText("CURRENT")))
            if (case.path("doctorate").asBoolean()) facts += ruleFact("education", """{"role":"PhD awarded","completed":true}""")
            if (case.has("age")) facts += ruleFact("birthDate", "${2026 - case.path("age").asInt()}-10-18")
            val search = if (case.path("apollo").asBoolean()) ruleSearch(case.path("masked").asBoolean(), case.path("count").asInt(1), case.path("complete").asBoolean(true), case.path("institution").asText("Example University")) else null
            val first = rules.evaluate(ruleSnapshot(), facts, search)
            assertEquals(first, rules.evaluate(ruleSnapshot(), facts, search))
            assertEquals(case.path("qualification").asText(), first.qualification.name, case.path("caseId").asText())
            assertEquals(case.path("outcome").asText(), first.outcome.name, case.path("caseId").asText())
            assertTrue(first.reasonCodes.contains(case.path("reason").asText()), case.path("caseId").asText())
            println("caseId=${case.path("caseId").asText()} route=${first.route} qualification=${first.qualification} reasons=${first.reasonCodes} evidence=${first.evidence.map { it.field + ":" + it.assertionSource + ":" + it.temporalScope }} missing=${first.missingFields}")
        }
        assertTrue(fixtures.path("apolloShapes").path("masked").path("people").first().has("last_name_obfuscated"))
        assertFalse(fixtures.path("apolloShapes").path("masked").path("people").first().has("email"))
    }
    @Test fun `strict name normalization allows known middle omission but no invented variants`() {
        assertTrue(rules.sameName("Ada Lee", "ＡＤＡ  M. Lee"))
        assertFalse(rules.sameName("Ada X Lee", "Ada Lee"))
        assertFalse(rules.sameName("A Lee", "Ada Lee"))
        assertFalse(rules.sameName("Ada Lin", "Ada Lee"))
        assertFalse(rules.sameInstitution(VerificationInstitution("Example University"), VerificationInstitution("Example Technical University")))
        assertTrue(rules.sameInstitution(VerificationInstitution("Example University", "ROR:1"), VerificationInstitution("Different label", "ROR:1")))
        assertFalse(rules.sameInstitution(VerificationInstitution("Example University", "ROR:1"), VerificationInstitution("Example University", "ROR:2")))
        assertTrue(rules.sameInstitution(VerificationInstitution("Example University", aliases = setOf("EU")), VerificationInstitution("EU")))
    }
    @Test fun `identity proof and field fingerprint cannot be repaired by Apollo`() {
        val snapshot = ruleSnapshot()
        val bad = snapshot.copy(profile = snapshot.profile.copy(email = "other@example.invalid"))
        assertEquals("IDENTITY_EVIDENCE_MISSING", rules.evaluate(bad, listOf(ruleJob()), ruleSearch()).reasonCodes.single())
        assertEquals(VerificationQualification.CONFLICT, rules.evaluate(snapshot, listOf(ruleJob().copy(factFingerprint = "e".repeat(64))), ruleSearch()).qualification)
        assertEquals(VerificationQualification.CONFLICT, rules.evaluate(snapshot, listOf(ruleJob().copy(conflicts = listOf("contradictory employment"))), ruleSearch()).qualification)
        assertEquals(VerificationQualification.CONFLICT, rules.evaluate(snapshot, listOf(ruleFact("name", "Other Person")), ruleSearch()).qualification)
        val original = jacksonObjectMapper().writeValueAsString(snapshot)
        rules.evaluate(snapshot, listOf(ruleJob()), ruleSearch())
        assertEquals(original, jacksonObjectMapper().writeValueAsString(snapshot))
    }
    @Test fun `professor title exact allowlist rejects historical and incidental references`() {
        listOf("Professor", "Prof.", "Assistant Professor", "Associate Professor", "Teaching Professor", "Adjunct Professor", "教授", "副教授").forEach { title ->
            assertEquals(VerificationOutcome.DIRECT_CLEAR, rules.evaluate(ruleSnapshot(), listOf(ruleJob(title))).outcome, title)
        }
        listOf("Professor Emeritus", "Former Professor", "PhD student", "Researcher in Professor Lee lab", "Assistant to Professor").forEach { title ->
            assertEquals(VerificationQualification.UNKNOWN, rules.evaluate(ruleSnapshot(), listOf(ruleJob(title))).qualification, title)
        }
        assertEquals(VerificationQualification.UNKNOWN, rules.evaluate(ruleSnapshot(), emptyList()).qualification)
    }
    @Test fun `birth conflict aliases and Apollo ID conflict are explicit not extra independent support`() {
        val facts = listOf(ruleJob(), ruleFact("birthDate", "1987-10-18"), ruleFact("birthDate", "1986-10-18"))
        assertEquals(VerificationQualification.CONFLICT, rules.evaluate(ruleSnapshot(), facts, ruleSearch()).qualification)
        assertEquals(listOf("APOLLO_ID_CONFLICT"), rules.evaluate(ruleSnapshot(), listOf(ruleJob()), ruleSearch().copy(conflictingIds = setOf("person-1"))).reasonCodes)
        assertEquals(VerificationOutcome.PROTECTED, rules.evaluate(ruleSnapshot(), emptyList(), protected = true).outcome)
        val self = ruleJob("Professor", AssertionSource.SELF)
        assertEquals(VerificationQualification.UNKNOWN, rules.evaluate(ruleSnapshot(), listOf(self, self)).qualification)
    }
    @Test fun `matching primary name cannot mask conflicting primary name while aliases remain supported`() {
        val facts = listOf(ruleJob("Professor"), ruleFact("name", "Ada Lee"), ruleFact("name", "Other Person"))
        listOf(null, ruleSearch()).forEach { search ->
            val result = rules.evaluate(ruleSnapshot(), facts, search)
            assertEquals(VerificationQualification.CONFLICT, result.qualification)
            assertEquals(VerificationOutcome.PENDING, result.outcome)
            assertEquals(listOf("NAME_CONFLICT"), result.reasonCodes)
        }
        assertEquals(VerificationOutcome.DIRECT_CLEAR, rules.evaluate(ruleSnapshot(),
            listOf(ruleJob("Professor"), ruleFact("name", "ＡＤＡ, Lee"), ruleFact("otherName", "Other Person"))).outcome)
        val snapshot = ruleSnapshot()
        val proof = DiscoveryIdentity.verified("ada@example.invalid", "Ada M", "Lee",
            "ORCID_RECORD_SHA256:" + "a".repeat(64), "0000-0002-1825-0097", null)
        val middleName = snapshot.copy(profile = snapshot.profile.copy(givenNames = "Ada M", identityVerification = proof))
        assertEquals(VerificationOutcome.DIRECT_CLEAR, rules.evaluate(middleName,
            listOf(ruleJob("Professor"), ruleFact("name", "Ada Lee"), ruleFact("name", "Ada M Lee"))).outcome)
        val aliasCandidate = ruleSearch().candidates.single().copy(firstName = "Other", lastName = "Person", fullName = "Other Person")
        assertEquals(VerificationOutcome.APOLLO_UNCLEAR, rules.evaluate(snapshot,
            listOf(ruleJob(), ruleFact("name", "Ada Lee"), ruleFact("otherName", "Other Person")),
            ruleSearch().copy(candidates = listOf(aliasCandidate))).outcome)
    }
    @Test fun `contradictory ORCID is identical for full and split explicit names`() {
        val search = ruleSearch()
        val wrong = search.candidates.single().copy(id = "wrong-orcid", orcid = "0000-0001-5109-3700")
        val results = listOf(wrong, wrong.copy(fullName = null)).map { candidate ->
            rules.evaluate(ruleSnapshot(), listOf(ruleJob()), search.copy(candidates = search.candidates + candidate))
        }
        assertEquals(results.first(), results.last())
        results.forEach { result ->
            assertEquals(VerificationQualification.CONFLICT, result.qualification)
            assertEquals(VerificationOutcome.PENDING, result.outcome)
            assertEquals(listOf("ORCID_CONFLICT"), result.reasonCodes)
        }
    }
}
