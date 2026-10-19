package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

/** Deterministic matrix for the pure institution subject contract; optional offline inventory audit. */
class DiscoveryInstitutionNormalizerTest {
    private val mapper = jacksonObjectMapper()
    private val fixtures = mapper.readTree(javaClass.getResourceAsStream("/discovery/institution-subject-cases.json"))

    private fun source(value: String) = InstitutionSource.valueOf(value)

    @Test fun `fixed real cases resolve exactly the documented subjects and reasons`() {
        val rows = mutableListOf<Map<String, Any?>>()
        fixtures.path("cases").forEach { case ->
            val caseId = case.path("caseId").asText()
            val record = DiscoveryInstitutionNormalizer.resolve(case.path("raw").asText(),
                source(case.path("source").asText()), "fixture:$caseId")
            assertEquals(case.path("status").asText(), record.status, caseId)
            assertEquals(case.path("reason").asText(), record.reason, caseId)
            assertEquals(case.path("subjects").map { it.asText() }, record.subjects, caseId)
            case.path("removedContains").forEach { expected ->
                assertTrue(record.removedParts.any { it.text == expected.asText() },
                    "$caseId missing removed part ${expected.asText()} in ${record.removedParts}")
            }
            assertTrue(record.rawSha256.matches(Regex("[a-f0-9]{64}")), caseId)
            if (record.status == DiscoveryInstitutionNormalizer.RESOLVED) {
                assertEquals(1, record.subjects.size, caseId)
                assertTrue(case.path("raw").asText().contains(record.subjects.single()), caseId)
            }
            println("institutionCase=$caseId source=${record.source} status=${record.status} subjects=${record.subjects} reason=${record.reason} removed=${record.removedParts.map { it.text + ":" + it.rule }}")
            rows += linkedMapOf("caseId" to caseId, "kind" to case.path("kind").asText(), "source" to record.source,
                "raw" to maskEmails(record.raw), "rawSha256" to record.rawSha256, "status" to record.status,
                "subjects" to record.subjects, "reason" to record.reason,
                "removedParts" to record.removedParts.map { mapOf("text" to it.text, "rule" to it.rule) })
        }
        assertEquals(14, rows.size)
        writeFixtureOutputs(rows)
    }

    @Test fun `fixture comparisons only match complete compatible subjects`() {
        fixtures.path("comparisons").forEach { case ->
            val left = VerificationInstitution(case.path("left").asText(), case.path("leftStandardId").takeIf { it.isTextual }?.asText())
            val right = VerificationInstitution(case.path("right").asText(), case.path("rightStandardId").takeIf { it.isTextual }?.asText())
            val result = DiscoveryInstitutionNormalizer.compare(left, right,
                source(case.path("leftSource").asText("PROFILE")), source(case.path("rightSource").asText("APOLLO_CANDIDATE")))
            assertEquals(case.path("expected").asText(), result.name, case.path("caseId").asText())
            println("institutionCompare=${case.path("caseId").asText()} expected=${case.path("expected").asText()} actual=$result")
        }
    }

    @Test fun `duplicate blocks deduplicate regardless of segment order and unicode is normalized`() {
        val first = "Department of Education, Seoul National University, Seoul, South Korea"
        val second = "Department of Education, Seoul National University, 1 Gwanak-ro, Gwanak-gu, Seoul, South Korea"
        val forward = DiscoveryInstitutionNormalizer.resolve("$first; $second", InstitutionSource.ORCID_EMPLOYMENT, "a")
        val reverse = DiscoveryInstitutionNormalizer.resolve("$second; $first", InstitutionSource.ORCID_EMPLOYMENT, "a")
        assertEquals(listOf("Seoul National University"), forward.subjects)
        assertEquals(forward.subjects.map(DiscoveryInstitutionNormalizer::canonical),
            reverse.subjects.map(DiscoveryInstitutionNormalizer::canonical))
        val wide = DiscoveryInstitutionNormalizer.resolve("Ｕｎｉｖｅｒｓｉｔｙ　ｏｆ　Ｃａｌｉｆｏｒｎｉａ，　Ｌｏｓ　Ａｎｇｅｌｅｓ",
            InstitutionSource.ORCID_EMPLOYMENT, "unicode")
        assertEquals("RESOLVED", wide.status)
        assertEquals("University of California, Los Angeles", wide.subjects.single())
    }

    @Test fun `five hundred character ORCID projection and over limit raw never continue matching`() {
        val prefix = "Gachon University, "
        val truncated = prefix + "A".repeat(500 - prefix.length)
        assertEquals(500, truncated.length)
        val orcid = DiscoveryInstitutionNormalizer.resolve(truncated, InstitutionSource.ORCID_EMPLOYMENT, "truncated")
        assertEquals("UNRESOLVED", orcid.status)
        assertEquals("INPUT_MAY_BE_TRUNCATED", orcid.reason)
        assertTrue(orcid.subjects.isEmpty())
        val oversized = DiscoveryInstitutionNormalizer.resolve("X".repeat(4097), InstitutionSource.PROFILE, "limit")
        assertEquals("UNRESOLVED", oversized.status)
        assertEquals("INPUT_LIMIT", oversized.reason)
        assertNull(oversized.raw)
        assertEquals(DiscoveryInstitutionNormalizer.sha256("X".repeat(4097)), oversized.rawSha256)
        val missing = DiscoveryInstitutionNormalizer.resolve(null, InstitutionSource.PROFILE, "profile.institution")
        assertEquals("MISSING", missing.reason)
        assertNull(missing.raw)
        val emailOnly = DiscoveryInstitutionNormalizer.resolve("a@example.invalid", InstitutionSource.PROFILE, "email")
        assertEquals("UNRESOLVED", emailOnly.status)
        assertTrue(emailOnly.subjects.isEmpty())
    }

    @Test fun `query hint requires exactly one distinct resolved employment subject`() {
        assertEquals("Gachon University", DiscoveryInstitutionNormalizer.buildInputs(null,
            listOf(employment("e1", "Gachon University"), employment("e2", "Gachon University")), emptyList()).queryInstitution)
        assertNull(DiscoveryInstitutionNormalizer.buildInputs(null,
            listOf(employment("e1", "Gachon University"), employment("e2", "Other University")), emptyList()).queryInstitution)
        assertNull(DiscoveryInstitutionNormalizer.buildInputs(null,
            listOf(employment("e1", "Gachon University"), employment("e2", "ACHISINA")), emptyList()).queryInstitution)
        assertNull(DiscoveryInstitutionNormalizer.buildInputs(null, emptyList(), emptyList()).queryInstitution)
        val context = DiscoveryInstitutionNormalizer.buildInputs("Gachon University",
            listOf(employment("e1", "Gachon University")), listOf(InstitutionCandidateInput("person-2", "Other University"),
                InstitutionCandidateInput("person-1", "Gachon University")))
        assertEquals(listOf("PROFILE", "ORCID_EMPLOYMENT", "APOLLO_CANDIDATE", "APOLLO_CANDIDATE"), context.inputs.map { it.source })
        assertEquals(listOf("person-1", "person-2"), context.inputs.drop(2).map { it.sourceRef })
    }

    @Test fun `unknown version source reference raw hash subject or missing candidate is rejected`() {
        val expected = DiscoveryInstitutionNormalizer.buildInputs("Gachon University",
            listOf(employment("e1", "Gachon University")),
            listOf(InstitutionCandidateInput("person-1", "Gachon University")))
        DiscoveryInstitutionNormalizer.validate(expected, expected)
        val tampered = listOf(
            expected.copy(version = "INST_SUBJECT_V2"),
            expected.copy(queryInstitution = "Other University"),
            expected.copy(inputs = expected.inputs.mapIndexed { index, input ->
                if (index == 0) input.copy(subjects = listOf("Other University")) else input
            }),
            expected.copy(inputs = expected.inputs.mapIndexed { index, input ->
                if (index == 1) input.copy(raw = "Other University") else input
            }),
            expected.copy(inputs = expected.inputs.mapIndexed { index, input ->
                if (index == 1) input.copy(sourceRef = "evidence:forged") else input
            }),
            expected.copy(inputs = expected.inputs.mapIndexed { index, input ->
                if (index == 2) input.copy(rawSha256 = "0".repeat(64)) else input
            }),
            expected.copy(inputs = expected.inputs.filter { it.source != "APOLLO_CANDIDATE" }),
            expected.copy(inputs = expected.inputs + InstitutionInputNormalization("APOLLO_CANDIDATE", "person-9",
                "Gachon University", DiscoveryInstitutionNormalizer.sha256("Gachon University"), "RESOLVED",
                listOf("Gachon University"), "SUBJECT_EXTRACTED")))
        tampered.forEachIndexed { index, value ->
            assertThrows(IllegalArgumentException::class.java, { DiscoveryInstitutionNormalizer.validate(expected, value) }, "tamper $index")
        }
        DiscoveryInstitutionNormalizer.validate(expected, expected.copy(inputs = expected.inputs.dropLast(1)), includeCandidates = false)
    }

    @Test fun `optional inventory audit fails on invalid input and never silently skips an explicit path`() {
        val input = System.getProperty("institutionAuditInput")
        val output = System.getProperty("institutionAuditOutput")
        if (input == null && output == null) return
        require(input != null && output != null) { "Both institutionAuditInput and institutionAuditOutput are required" }
        val source = Paths.get(input)
        require(Files.isRegularFile(source)) { "Institution audit input must be an existing file: $input" }
        val target = Paths.get(output)
        require(!Files.exists(target)) { "Institution audit output must not already exist: $output" }
        val bytes = Files.readAllBytes(source)
        val root = mapper.readTree(bytes)
        val records = when {
            root.isArray -> root
            root.path("items").isArray -> root.path("items")
            root.path("records").isArray -> root.path("records")
            root.path("profiles").isArray -> root.path("profiles")
            else -> throw IllegalArgumentException("Institution audit input has no array of records")
        }
        var withValue = 0
        var missing = 0
        val statuses = linkedMapOf("RESOLVED" to 0, "AMBIGUOUS" to 0, "UNRESOLVED" to 0)
        val reasons = sortedMapOf<String, Int>()
        var truncated = 0
        var overLimit = 0
        var removed = 0
        val cases = StringBuilder()
        records.forEach { node ->
            val raw = listOf("institution", "institutionName", "rawInstitution", "institutions", "organization")
                .firstNotNullOfOrNull { key ->
                    val value = node.get(key) ?: return@firstNotNullOfOrNull null
                    when {
                        value.isTextual && value.asText().isNotBlank() -> value.asText()
                        value.isArray -> value.mapNotNull {
                            it.takeIf { text -> text.isTextual && text.asText().isNotBlank() }?.asText()
                        }.firstOrNull()
                        else -> null
                    }
                }
            if (raw == null) missing++ else withValue++
            val record = DiscoveryInstitutionNormalizer.resolve(raw, InstitutionSource.PROFILE, "audit")
            statuses[record.status] = (statuses[record.status] ?: 0) + 1
            reasons[record.reason] = (reasons[record.reason] ?: 0) + 1
            if (record.reason == "INPUT_MAY_BE_TRUNCATED") truncated++
            if (record.reason == "INPUT_LIMIT") overLimit++
            removed += record.removedParts.size
            cases.append(mapper.writeValueAsString(mapOf("raw" to maskEmails(raw), "status" to record.status,
                "subjects" to record.subjects, "reason" to record.reason))).append('\n')
        }
        Files.createDirectories(target)
        val summary = linkedMapOf<String, Any>(
            "schemaVersion" to 1, "version" to DiscoveryInstitutionNormalizer.VERSION,
            "input" to input, "inputSha256" to sha(bytes), "records" to records.size(),
            "withValue" to withValue, "missing" to missing, "statuses" to statuses, "reasons" to reasons,
            "truncated" to truncated, "inputLimit" to overLimit, "removedParts" to removed,
            "identityImpact" to "NOT_EVALUATED", "note" to "Extraction distribution only, not an identity or accuracy rate.")
        Files.writeString(target.resolve("summary.json"), mapper.writeValueAsString(summary))
        Files.writeString(target.resolve("cases.jsonl"), cases.toString())
        println("institutionAudit inputSha256=${sha(bytes)} records=${records.size()} withValue=$withValue missing=$missing statuses=$statuses")
    }

    private fun employment(ref: String, raw: String) = InstitutionEmploymentInput(ref, raw)

    private fun writeFixtureOutputs(rows: List<Map<String, Any?>>) {
        val directory = Paths.get("target", "institution-subject-fixtures")
        Files.createDirectories(directory)
        val summary = linkedMapOf<String, Any>(
            "schemaVersion" to 1, "version" to DiscoveryInstitutionNormalizer.VERSION,
            "fixtureSha256" to sha(javaClass.getResourceAsStream("/discovery/institution-subject-cases.json").readBytes()),
            "cases" to rows.size,
            "statuses" to rows.groupingBy { it["status"] }.eachCount(),
            "identityImpact" to "NOT_EVALUATED",
            "description" to "Deterministic extraction matrix; not a production accuracy claim.")
        Files.writeString(directory.resolve("summary.json"), mapper.writeValueAsString(summary))
        Files.writeString(directory.resolve("cases.jsonl"), rows.joinToString("\n") { mapper.writeValueAsString(it) } + "\n")
    }

    private fun maskEmails(value: String?): String? = value?.replace(
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<email>")

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
