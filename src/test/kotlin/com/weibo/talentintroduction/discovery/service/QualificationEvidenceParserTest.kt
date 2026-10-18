package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class QualificationEvidenceParserTest {
    private val parser = QualificationEvidenceParser(ruleClock)
    private val mapper = jacksonObjectMapper()
    private fun document(endpoint: String, body: String) = OrcidEvidenceDocument(endpoint, mapper.readTree(body), "d".repeat(64), ruleClock.instant())
    private fun affiliation(role: String, source: String = "\"source-client-id\":{\"path\":\"APP-OFFICIAL\"}", end: String = "null", visibility: String = "PUBLIC") = """{
        "role-title":"$role", "organization":{"name":"Example University","disambiguated-organization":{"disambiguation-source":"ROR","disambiguated-organization-identifier":"01"}},
        "visibility":"$visibility", "source":{$source}, "start-date":{"year":{"value":"2020"}},"end-date":$end,
        "last-modified-date":{"value":1609459200000},"arbitrary-secret":"must-not-retain"
    }"""
    @Test fun `source visibility temporal fields and pointers are retained without raw response`() {
        val docs = listOf(document("employment/1", affiliation("Professor")),
            document("employment/2", affiliation("Professor", "\"source-orcid\":{\"path\":\"0000-0002-1825-0097\"}", "{\"year\":{\"value\":\"2022\"}}")),
            document("employment/3", affiliation("Professor", visibility = "PRIVATE")))
        val facts = parser.parse(ruleSnapshot(), OrcidEvidenceRead(docs, true, emptyList()))
        assertEquals(2, facts.size)
        assertEquals(AssertionSource.ORGANIZATION, facts[0].assertionSource)
        assertEquals(TemporalScope.CURRENT, facts[0].temporalScope)
        assertEquals(AssertionSource.SELF, facts[1].assertionSource)
        assertEquals(TemporalScope.HISTORICAL, facts[1].temporalScope)
        assertNotNull(facts[0].sourceUpdatedAt)
        assertTrue(facts[0].excerpt!!.contains("APP-OFFICIAL"))
        assertTrue(facts.all { !it.toString().contains("must-not-retain") })
    }
    @Test fun `end date is not degree award and doctoral students never qualify as completed`() {
        listOf("PhD", "PhD student completed coursework", "PhD candidate", "Pursuing doctorate", "博士在读").forEach { assertFalse(parser.completedDoctorate(it), it) }
        listOf("Ph.D. awarded", "Doctor of Philosophy conferred", "DPhil completed", "已获博士学位").forEach { assertTrue(parser.completedDoctorate(it), it) }
        val doc = document("education/1", affiliation("PhD", end = "{\"year\":{\"value\":\"2022\"}}"))
        val fact = parser.parse(ruleSnapshot(), OrcidEvidenceRead(listOf(doc), true, emptyList())).single()
        assertFalse(mapper.readTree(fact.value).path("completed").asBoolean())
        assertEquals(VerificationQualification.UNKNOWN, DiscoveryVerificationRules(ruleClock).evaluate(ruleSnapshot(), listOf(fact)).qualification)
    }
    @Test fun `negated completion or award cannot establish a doctorate even at age 39`() {
        listOf("PhD not completed", "PhD not yet awarded", "Doctor of Philosophy has not been conferred",
            "DPhil never completed", "PhD incomplete", "未获博士学位", "博士尚未毕业").forEach { role ->
            assertFalse(parser.completedDoctorate(role), role)
            val fact = parser.parse(ruleSnapshot(), OrcidEvidenceRead(
                listOf(document("education/1", affiliation(role))), true, emptyList())).single()
            assertFalse(mapper.readTree(fact.value).path("completed").asBoolean(), role)
            val result = DiscoveryVerificationRules(ruleClock).evaluate(ruleSnapshot(),
                listOf(fact, ruleFact("birthDate", "1987-10-18")))
            assertEquals(VerificationQualification.UNKNOWN, result.qualification, role)
            assertEquals(VerificationOutcome.PENDING, result.outcome, role)
            assertTrue(result.missingFields.contains("completedDoctorate"), role)
        }
    }
    @Test fun `name aliases stay tied to bound public person and missing fields do not become facts`() {
        val doc = document("person", """{"name":{"given-names":{"value":"Ada"},"family-name":{"value":"Lee"},"visibility":"PUBLIC"},"other-names":{"other-name":[{"content":"Ada M Lee","visibility":"PUBLIC"},{"content":"Private Name","visibility":"PRIVATE"}]},"emails":{"email":[{"email":"never-retained@example.invalid"}]}}""")
        val facts = parser.parse(ruleSnapshot(), OrcidEvidenceRead(listOf(doc), true, emptyList()))
        assertEquals(listOf("name", "otherName"), facts.map { it.field })
        assertFalse(facts.toString().contains("never-retained"))
        assertEquals(emptyList<VerificationEvidence>(), parser.parse(ruleSnapshot(), OrcidEvidenceRead(listOf(document("person", "{}")), true, emptyList())))
    }
    @Test fun `duplicate source material is not independent evidence and unknown source cannot establish qualification`() {
        val doc = document("employment/1", affiliation("Professor", source = ""))
        val read = OrcidEvidenceRead(listOf(doc, doc), true, emptyList())
        val facts = parser.parse(ruleSnapshot(), read)
        assertEquals(1, facts.size)
        assertEquals(AssertionSource.UNKNOWN, facts.single().assertionSource)
        assertEquals(VerificationQualification.UNKNOWN, DiscoveryVerificationRules(ruleClock).evaluate(ruleSnapshot(), facts).qualification)
    }
}
