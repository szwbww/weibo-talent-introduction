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
    @Test fun `public case variants retain provenance and every nonpublic shape fails closed`() {
        listOf("public", "PUBLIC", "PuBlIc").forEach { visibility ->
            val person = document("person", """{"name":{"given-names":{"value":"Ada"},"family-name":{"value":"Lee"},"visibility":"$visibility"}}""")
            val job = document("employment/1", affiliation("Professor", visibility = visibility))
            val facts = parser.parse(ruleSnapshot(), OrcidEvidenceRead(listOf(person, job), true, emptyList()))
            assertEquals(listOf("name", "employment"), facts.map { it.field })
            facts.forEach {
                assertEquals("d".repeat(64), it.responseSha256)
                assertEquals(ruleClock.instant(), it.fetchedAt)
                assertTrue(it.sourceUrl.endsWith(if (it.field == "name") "/person" else "/employment/1"))
                assertEquals(visibility, mapper.readTree(it.excerpt).path("visibility").asText())
            }
            assertEquals("/name", facts.first().jsonPointer)
        }
        listOf("\"private\"", "\"limited\"", "null", "12", "{}", "true", null).forEach { visibility ->
            val job = mapper.readTree(affiliation("Professor")) as com.fasterxml.jackson.databind.node.ObjectNode
            val name = mapper.readTree("""{"given-names":{"value":"Ada"}}""") as com.fasterxml.jackson.databind.node.ObjectNode
            if (visibility == null) job.remove("visibility") else {
                job.set<com.fasterxml.jackson.databind.JsonNode>("visibility", mapper.readTree(visibility))
                name.set<com.fasterxml.jackson.databind.JsonNode>("visibility", mapper.readTree(visibility))
            }
            assertTrue(parser.parse(ruleSnapshot(), OrcidEvidenceRead(listOf(document("employment/1", job.toString()),
                document("person", """{"name":$name}""")), true, emptyList())).isEmpty(), "$visibility")
        }
    }

    @Test fun `endpoint rebuild equals callback accumulation is idempotent and removes rejected cached facts only`() {
        val first = document("employment/1", affiliation("Researcher", visibility = "public"))
        val second = first.copy(endpoint = "employment/2")
        val docs = listOf(first, second)
        val callback = docs.flatMap { parser.parse(ruleSnapshot(), OrcidEvidenceRead(listOf(it), true, emptyList())) }.distinct()
        val rebuilt = parser.rebuild(ruleSnapshot(), docs, emptyList())
        assertEquals(2, rebuilt.size)
        assertEquals(callback, rebuilt)
        assertEquals(rebuilt, parser.rebuild(ruleSnapshot(), docs, rebuilt))
        val uncached = ruleFact("otherName", "Old public alias").copy(sourceUrl = "https://uncached.invalid/person")
        val external = rebuilt.first().copy(provider = "OTHER")
        val privateDoc = first.copy(body = mapper.readTree(affiliation("Professor", visibility = "private")))
        val remaining = parser.rebuild(ruleSnapshot(), listOf(privateDoc), rebuilt + uncached + external)
        assertEquals(listOf(rebuilt[1], uncached, external), remaining)
        assertEquals(remaining, parser.rebuild(ruleSnapshot(), emptyList(), remaining))
        assertThrows(IllegalArgumentException::class.java) {
            parser.rebuild(ruleSnapshot(), listOf(first.copy(body = mapper.readTree("""{"path":"/0000-0001-5109-3700/employment/1"}"""))), rebuilt)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.rebuild(ruleSnapshot(), docs, listOf(uncached.copy(identityHash = "e".repeat(64))))
        }
    }
}
