package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

class PlainTextEmailExtractorTest {
    private val extractor = PlainTextEmailExtractor()
    private val mapper = jacksonObjectMapper()
    private fun fixture(): JsonNode = mapper.readTree(
        requireNotNull(javaClass.getResourceAsStream("/discovery/email-text-recall.json")).use { it.readBytes() }
    )

    @Test
    fun `extracts standard emails`() {
        val result = extractor.extract("Contact: john.smith@oxford.ac.uk, jane.doe@cam.ac.uk")
        assertEquals(setOf("john.smith@oxford.ac.uk", "jane.doe@cam.ac.uk"), result.toSet())
    }

    @Test
    fun `deobfuscates at and dot patterns`() {
        assertEquals(setOf("alice@oxford.ac.uk", "bob@university.edu"),
            extractor.extract("alice(at)oxford(dot)ac(dot)uk bob{at}university.edu").toSet())
    }

    @Test
    fun `filters configured and operational blacklists`() {
        assertEquals(listOf("researcher@gmail.com"),
            extractor.extract("journals@springer.com researcher@gmail.com support@elsevier.com"))
        assertEquals(listOf("real@oxford.ac.uk"), extractor.extract("user@example.com real@oxford.ac.uk"))
    }

    @Test
    fun `extracts each exact original brace expression and writes observed acceptance evidence`() {
        val fixture = fixture()
        val brace = fixture.path("sourceBrace")
        val braceResults = brace.path("cases").map { record ->
            val actual = extractor.extract(record.path("text").asText())
            assertEquals(record.path("expected").map { it.asText() }, actual, record.path("paper").asText())
            mapOf("paper" to record.path("paper").asText(), "input" to record.path("text").asText(), "actual" to actual)
        }
        assertEquals(30, braceResults.sumOf { (it["actual"] as List<*>).size })
        val wrapped = fixture.path("wrappedSource")
        val wrappedActual = extractor.extract(wrapped.path("text").asText())
        assertEquals(wrapped.path("expected").map { it.asText() }, wrappedActual)
        val syntheticResults = fixture.path("syntheticControls").map { record ->
            val actual = extractor.extract(record.path("text").asText())
            assertEquals(record.path("expected").map { it.asText() }, actual)
            mapOf("kind" to record.path("kind").asText(), "input" to record.path("text").asText(), "actual" to actual)
        }
        val fixtureBytes = requireNotNull(javaClass.getResourceAsStream("/discovery/email-text-recall.json")).use { it.readBytes() }
        val fixtureSha = MessageDigest.getInstance("SHA-256").digest(fixtureBytes).joinToString("") { "%02x".format(it) }
        val report = mapOf(
            "plan" to "03",
            "fixture" to mapOf("path" to "src/test/resources/discovery/email-text-recall.json", "sha256" to fixtureSha),
            "sourceBrace" to mapOf("sourcePath" to brace.path("sourcePath").asText(), "sourceSha256" to brace.path("sourceSha256").asText(), "results" to braceResults),
            "wrappedSource" to mapOf("paper" to wrapped.path("paper").asText(), "sourcePath" to wrapped.path("sourcePath").asText(), "sourceSha256" to wrapped.path("sourceSha256").asText(), "actual" to wrappedActual),
            "syntheticControls" to syntheticResults,
            "observed" to mapOf("braceExpansionCount" to braceResults.sumOf { (it["actual"] as List<*>).size }, "wrappedEmails" to wrappedActual)
        )
        val output = Paths.get("target/discovery-plan-acceptance/03.json")
        Files.createDirectories(output.parent)
        Files.write(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report))
        assertTrue(Files.size(output) > 0)
    }

    @Test
    fun `repairs only immediate one line mailbox wrap and rejects malformed brace members`() {
        assertEquals(listOf("opaque@uni.edu"), extractor.extract("opaque@\nuni.edu"))
        assertTrue(extractor.extract("opaque@\n\nuni.edu").isEmpty())
        assertTrue(extractor.extract("opaque@\nintervening\nuni.edu").isEmpty())
        assertTrue(extractor.extract("{opaque,,second}@uni.edu").isEmpty())
        assertTrue(extractor.extract("{opaque,{second}}@uni.edu").isEmpty())
    }

    @Test
    fun `contact normalization changes only supported obfuscations and preserves paragraphs`() {
        val original = "Jane Doe: opaque(at)uni.edu\nOther contact: opaque@other.edu"
        val normalized = extractor.normalizeContactText(original)
        assertEquals("Jane Doe: opaque@uni.edu\nOther contact: opaque@other.edu", normalized)
        assertTrue(original.contains('\n'))
        assertTrue(normalized.contains('\n'))
    }
}
