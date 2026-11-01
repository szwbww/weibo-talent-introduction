package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class DiscoveryPaperIdentityReplayToolTest {
    @TempDir lateinit var root: Path
    private val mapper = jacksonObjectMapper()
    private fun hash(value: String) = DiscoveryPaperIdentityReplayTool.sha(value.toByteArray())
    private fun fixture(): ObjectNode {
        val body = ByteArrayOutputStream().use { out ->
            PDDocument().use { doc ->
                val page = PDPage(); doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(40f, 740f)
                    stream.showText("Alice Smith alice.smith@example.edu")
                    stream.endText()
                }
                doc.save(out)
            }; out.toByteArray()
        }
        val metadata = """{"authors":[{"givenNames":"Alice","familyNames":"Smith","orcidId":null,"affiliation":null,"openAlexAuthorId":"A1"}],"pubYear":2025}"""
        val outcome = """{"first":{"attemptIds":[7],"parserVersion":{"pdfLayout":20261024},"outcome":{"emails":[{"email":"alice.smith@example.edu","givenNames":null,"familyNames":null}]}}}"""
        val source = mapper.writeValueAsBytes(mapOf("metadataRaw" to metadata, "outcomeRaw" to outcome))
        Files.write(root.resolve("body.bin"), body); Files.write(root.resolve("source.json"), source)
        val bodyHash = DiscoveryPaperIdentityReplayTool.sha(body)
        return mapper.valueToTree(mapOf("schemaVersion" to 1,
            "parserConfig" to mapOf("historicalEvidenceAvailable" to true, "settings" to mapOf("maxPages" to 2, "tailPages" to 1)),
            "records" to listOf(mapOf("recordId" to 1, "attemptId" to 7, "bodySha256" to bodyHash,
                "sizeBytes" to body.size, "kind" to "PDF_URL", "bodyFile" to "body.bin", "sourceFile" to "source.json",
                "sourceSha256" to DiscoveryPaperIdentityReplayTool.sha(source), "metadataSha256" to hash(metadata), "outcomeSha256" to hash(outcome))),
            "combinations" to listOf(mapOf("recordId" to 1, "bodySha256" to bodyHash,
                "emailHash" to hash("alice.smith@example.edu").take(24), "nameHash" to hash("alice smith").take(24),
                "authorKey" to "A1", "page" to 1, "auditRule" to "TEST_ONLY"))))
    }
    @Test fun `real PDF path retains one row and performs no network or source writes`() {
        val manifest = fixture()
        val before = Files.readAllBytes(root.resolve("body.bin"))
        val source = Files.readAllBytes(root.resolve("source.json"))
        var calls = 0
        val rows = DiscoveryPaperIdentityReplayTool.replay(root, manifest) { calls++; error("NETWORK") }
        assertEquals(1, rows.size); assertEquals(0, calls)
        assertEquals(true, rows.single()["pdfboxEmailCovered"])
        assertEquals(true, rows.single()["metadataAuthorPresent"])
        assertNotEquals("SOURCE_MISSING", rows.single()["status"])
        assertArrayEquals(before, Files.readAllBytes(root.resolve("body.bin")))
        assertArrayEquals(source, Files.readAllBytes(root.resolve("source.json")))
    }
    @Test fun `one byte corruption never becomes a reproduced case`() {
        val manifest = fixture(); val path = root.resolve("body.bin")
        val bytes = Files.readAllBytes(path); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); Files.write(path, bytes)
        val row = DiscoveryPaperIdentityReplayTool.replay(root, manifest).single()
        assertEquals("SOURCE_MISSING", row["status"]); assertEquals("BODY_HASH_MISMATCH", row["reason"])
    }
    @Test fun `missing original configuration and metadata fail closed`() {
        val manifest = fixture()
        (manifest["parserConfig"] as ObjectNode).put("historicalEvidenceAvailable", false)
        assertEquals("ORIGINAL_CONFIG_MISSING", DiscoveryPaperIdentityReplayTool.replay(root, manifest).single()["reason"])
        (manifest["parserConfig"] as ObjectNode).put("historicalEvidenceAvailable", true)
        (manifest["records"][0] as ObjectNode).put("metadataSha256", "wrong")
        assertEquals("METADATA_HASH_MISMATCH", DiscoveryPaperIdentityReplayTool.replay(root, manifest).single()["reason"])
    }
    @Test fun `missing files and unsafe paths retain failure rows`() {
        val manifest = fixture(); Files.delete(root.resolve("body.bin"))
        assertEquals("SOURCE_FILE_MISSING", DiscoveryPaperIdentityReplayTool.replay(root, manifest).single()["reason"])
        (manifest["records"][0] as ObjectNode).put("bodyFile", "../other.pdf")
        assertEquals("SOURCE_PATH_ESCAPE", DiscoveryPaperIdentityReplayTool.replay(root, manifest).single()["reason"])
    }
    @Test fun `negative cases cannot enter repair truth and duplicate keys are rejected`() {
        val manifest = fixture(); (manifest["combinations"][0] as ObjectNode).put("negative", true)
        assertEquals("AMBIGUOUS", DiscoveryPaperIdentityReplayTool.replay(root, manifest).single()["status"])
        (manifest["combinations"] as com.fasterxml.jackson.databind.node.ArrayNode).add(manifest["combinations"][0].deepCopy<JsonNode>())
        assertThrows(IllegalArgumentException::class.java) { DiscoveryPaperIdentityReplayTool.replay(root, manifest) }
    }
    @Test fun `CLI writes only its receipt and refuses overwriting`() {
        val manifest = fixture(); val outer = Files.createTempDirectory("paper-replay-test-")
        try {
            val input = outer.resolve("manifest.json"); val output = outer.resolve("receipt.json")
            Files.write(input, mapper.writeValueAsBytes(manifest))
            DiscoveryPaperIdentityReplayTool.main(arrayOf(root.toString(), input.toString(), output.toString()))
            val receipt = mapper.readTree(Files.readAllBytes(output))
            assertEquals(0, receipt["networkCalls"].asInt())
            assertEquals(0, receipt["businessWriteServicesConstructed"].asInt())
            assertEquals(1, receipt["rows"].size())
            assertThrows(IllegalArgumentException::class.java) {
                DiscoveryPaperIdentityReplayTool.main(arrayOf(root.toString(), input.toString(), output.toString()))
            }
        } finally { Files.list(outer).use { it.forEach(Files::delete) }; Files.delete(outer) }
    }
}
