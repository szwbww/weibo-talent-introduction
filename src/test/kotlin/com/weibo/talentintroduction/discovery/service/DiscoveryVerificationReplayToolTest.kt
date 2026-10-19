package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class DiscoveryVerificationReplayToolTest {
    @TempDir lateinit var root: Path
    private val tool = DiscoveryVerificationReplayTool
    @org.junit.jupiter.api.BeforeEach fun canonicalRoot() { root = root.toRealPath() }
    @Test fun `bounded explicit unique cohort and stage only transform`() {
        listOf(emptyList(), (1L..101L).toList(), listOf(1L, 1L), listOf(0L)).forEach { ids ->
            assertThrows(IllegalArgumentException::class.java) { tool.validateIds(ids) }
        }
        val original = """{"evidence":[],"search":{"complete":true},"orcidCompleted":[{"endpoint":"person"}],"progress":{"stage":"DONE","page":3},"extra":7}"""
        val expected = tool.mapper.readTree(original) as ObjectNode
        (expected.get("progress") as ObjectNode).put("stage", "DECIDE")
        assertEquals(expected, tool.mapper.readTree(DiscoveryVerificationRepository.replayEvidence(original)))
        assertThrows(Exception::class.java) { DiscoveryVerificationRepository.replayEvidence("broken") }
    }
    @Test fun `atomic restricted files cannot be replaced and symlinks are refused`() {
        val directory = root.resolve("private"); Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        val file = directory.resolve("before.json")
        tool.atomicWrite(file, "{}".toByteArray())
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file))
        assertThrows(IllegalArgumentException::class.java) { tool.atomicWrite(file, "other".toByteArray()) }
        val link = root.resolve("link"); Files.createSymbolicLink(link, directory)
        assertThrows(IllegalArgumentException::class.java) { tool.atomicWrite(link.resolve("bad"), byteArrayOf()) }
        assertEquals("{}", Files.readString(file))
    }
    @Test fun `corrupt manifest and bad digest are rejected without exposing credentials`() {
        val directory = root.resolve("private"); Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        val path = directory.resolve("manifest.json"); val invalid = "not-json".toByteArray(); tool.atomicWrite(path, invalid)
        assertThrows(Exception::class.java) { tool.load(path, tool.sha(invalid)) }
        assertThrows(IllegalArgumentException::class.java) { tool.load(path, "a".repeat(64)) }
        val source = Files.readString(Path.of("src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayTool.kt"))
        assertFalse(source.contains("SpringApplication")); assertFalse(source.contains("@Service")); assertFalse(source.contains("@Scheduled"))
        assertFalse(source.contains("printStackTrace")); assertFalse(source.contains("println(e"))
    }
    @Test fun `offline stable entry preserves clock provider dates and source classifications`() {
        val snapshot = ruleSnapshot()
        val doc = OrcidEvidenceDocument("employments", tool.mapper.readTree("""{"affiliation-group":[{"summaries":[{"employment-summary":{"visibility":"public","organization":{"name":"Example University"},"role-title":"Professor","source":{"source-orcid":{"path":"0000-0002-1825-0097"}},"start-date":{"year":{"value":"2020"}},"end-date":null}}]}]}"""), "d".repeat(64), ruleClock.instant().minusSeconds(100))
        val payload = VerificationWorkerEvidence(search = ruleSearch(), orcidCompleted = listOf(OrcidEndpointCompletion("employments", doc)))
        val input = tool.mapper.valueToTree<ObjectNode>(mapOf("items" to listOf(mapOf("id" to 1,
            "snapshotJson" to tool.mapper.writeValueAsString(snapshot), "evidenceJson" to tool.mapper.writeValueAsString(payload), "reasonsJson" to "[\"NAME_CONFLICT\"]"))))
        val first = DiscoveryVerificationReplayTool.Evaluator.evaluateInput(input, ruleClock.instant(), "fixed")
        assertEquals(first, DiscoveryVerificationReplayTool.Evaluator.evaluateInput(input, ruleClock.instant(), "fixed"))
        assertEquals("APOLLO_UNCLEAR", first.single().outcome)
        assertEquals(mapOf("employment:SELF:CURRENT" to 1), first.single().factCounts)
        assertTrue(first.single().historicalNameConflict)
        assertEquals("PENDING", first.single().localOutcome)
        assertEquals(ruleClock.instant().minusSeconds(100), doc.fetchedAt)
    }
    @Test fun `standalone database failures redact URL username and password`() {
        val ids = root.resolve("ids.json"); Files.writeString(ids, "[1]")
        val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("java.class.path"), DiscoveryVerificationReplayTool::class.java.name,
            "freeze", "--execution-id", "1", "--item-ids", ids.toString(), "--output", root.resolve("output").toString())
            .redirectErrorStream(true)
        val secret = "UNIQUE_PRIVATE_CREDENTIAL_5d243c"
        process.environment().putAll(mapOf("DB_URL" to "jdbc:missing:$secret", "DB_USERNAME" to secret, "DB_PASSWORD" to secret))
        val child = process.start()
        val output = child.inputStream.bufferedReader().readText()
        assertEquals(2, child.waitFor()); assertTrue(output.contains("REPLAY_FAILED")); assertFalse(output.contains(secret))
    }
}
