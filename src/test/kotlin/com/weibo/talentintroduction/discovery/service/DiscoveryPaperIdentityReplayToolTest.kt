package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
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

    // Reconciliation acceptance lives in the existing CLI: identity evidence, not totals, decides.
    private val bodySha = "a".repeat(64)
    private val targetEmail = "target.author@example.edu"
    private val emailHash = emailFingerprint(targetEmail)
    private val nameHash = "c".repeat(24)
    private val expectedDoc = "0000-0002-0178-9985"

    private fun emailFingerprint(value: String) =
        DiscoveryIdentity.hash(DiscoveryIdentity.normalizedEmail(value)).take(24)

    private fun combination(): ObjectNode = mapper.createObjectNode().put("recordId", 1).put("bodySha256", bodySha)
        .put("page", 1).put("emailHash", emailHash).put("nameHash", nameHash)
        .put("authorKey", "A1").put("auditRule", "TEST_ONLY").put("personGroup", "1")
        .put("sameBatchStatus", "test").put("negative", false)

    private fun inputs(dir: Path, withInventory: Boolean = true): Path {
        Files.createDirectories(dir.resolve("audit"))
        val manifest = mapper.createObjectNode().put("schemaVersion", 1).put("sourceDirectory", dir.toString())
            .put("executionId", 30711)
        manifest.putArray("records"); manifest.putArray("negativeCandidates")
        manifest.putArray("combinations").add(combination())
        val audit = "archive_record_id,blob_hash_fingerprint,email_sha256_prefix24,name_sha256_prefix24,page\n" +
            "1," + DiscoveryPaperIdentityReplayTool.sha(bodySha.toByteArray()).take(24) + ",$emailHash,$nameHash,1\n"
        Files.write(dir.resolve("audit/confirmed-bindings.csv"), audit.toByteArray())
        manifest.putObject("auditSha256").put("confirmed-bindings.csv", DiscoveryPaperIdentityReplayTool.sha(audit.toByteArray()))
        Files.write(dir.resolve("manifest.json"), mapper.writeValueAsBytes(manifest))
        val receipt = mapper.createObjectNode()
        receipt.put("manifestSha256", DiscoveryPaperIdentityReplayTool.sha(Files.readAllBytes(dir.resolve("manifest.json"))))
        receipt.put("networkCalls", 0).put("businessWriteServicesConstructed", 0)
        val row = receipt.putArray("rows").addObject().put("recordId", 1).put("bodySha256", bodySha).put("page", 1)
            .put("emailHash", emailHash).put("nameHash", nameHash).put("status", "REPRODUCED").put("reason", "TEST_ONLY")
        row.putArray("newNames")
        if (withInventory) row.putObject("inventory").put("status", "COMPLETE").putArray("documents")
            .addObject().put("docId", expectedDoc).put("protected", false).put("status", "COMPLETE_NAME_MISSING_PROOF")
        Files.write(dir.resolve("old.json"), mapper.writeValueAsBytes(receipt))
        Files.write(dir.resolve("runtime.json"), "{}".toByteArray())
        return dir
    }

    private fun target(outcome: String?, docId: String?,
        email: JsonNode? = mapper.nodeFactory.textNode(targetEmail),
        candidateStatus: String? = null): ObjectNode = mapper.createObjectNode().also { value ->
        outcome?.let { value.put("targetOutcome", it) }
        docId?.let { value.put("docId", it) }
        // Absence of the key is a real counterexample; a JSON null is expressed with nullNode().
        email?.let { value.set<JsonNode>("email", it) }
        // A run candidate may self-report a flat replay `status`; the CLI origin must still win.
        value.putArray("candidates").add(combination().also { it.remove("page"); candidateStatus?.let { s -> it.put("status", s) } })
    }

    private fun runExport(dir: Path, vararg targets: ObjectNode): Path = dir.resolve("run.json").also {
        val root = mapper.createObjectNode().put("schemaVersion", 1)
        root.putObject("run")
        val array = root.putArray("targets")
        targets.forEach { target ->
            val entry = array.addObject()
            target["email"]?.let { value -> entry.set<JsonNode>("email", value.deepCopy()) }
            entry.set<JsonNode>("candidates", target["candidates"].deepCopy())
            target["targetOutcome"]?.let { value -> entry.put("targetOutcome", value.asText()) }
            target["docId"]?.let { value -> entry.put("docId", value.asText()) }
        }
        Files.write(it, mapper.writeValueAsBytes(root))
    }

    private fun csvFields(line: String): List<String> {
        val fields = mutableListOf<String>(); val value = StringBuilder(); var quoted = false; var index = 0
        while (index < line.length) {
            val char = line[index]
            if (quoted) {
                if (char == '"') { if (index + 1 < line.length && line[index + 1] == '"') { value.append('"'); index++ } else quoted = false }
                else value.append(char)
            } else when (char) { '"' -> quoted = true; ',' -> { fields += value.toString(); value.clear() }; else -> value.append(char) }
            index++
        }
        fields += value.toString(); return fields
    }

    private fun reconcile(dir: Path, extra: List<String>): Map<String, String> {
        val process = ProcessBuilder(listOf("python3", "scripts/discovery_admission_probe/reconcile_paper_identity_replay.py",
            "--manifest", dir.resolve("manifest.json").toString(), "--old-replay", dir.resolve("old.json").toString(),
            "--audit-directory", dir.resolve("audit").toString(), "--csv", dir.resolve("out.csv").toString(),
            "--report", dir.resolve("out.md").toString(), "--runtime-evidence", dir.resolve("runtime.json").toString()) + extra)
            .redirectErrorStream(true).start()
        val output = process.inputStream.use { it.readBytes().decodeToString() }
        assertEquals(0, process.waitFor(), output)
        val lines = Files.readAllLines(dir.resolve("out.csv"))
        assertEquals(2, lines.size, output)
        val header = csvFields(lines[0])
        return header.zip(csvFields(lines[1])).toMap()
    }

    @Test fun `reconciliation rejects invalid actual identities instead of accepting totals`() {
        fun case(name: String, vararg targets: ObjectNode): Map<String, String> {
            val dir = inputs(Files.createDirectories(root.resolve(name)))
            return reconcile(dir, listOf("--reprocess-run", runExport(dir, *targets).toString()))
        }
        val wrong = case("wrong-doc", target("REPAIRED", "wrong-doc"))
        assertEquals("REGRESSION", wrong["classification"]); assertEquals("INVALID", wrong["actualEvidence"])
        val duplicate = case("duplicate-targets", target("REPAIRED", expectedDoc), target("REPAIRED", expectedDoc))
        assertEquals("REGRESSION", duplicate["classification"])
        val missing = case("missing-outcome", target(null, "wrong-doc"))
        assertEquals("REGRESSION", missing["classification"])
        val skipped = case("skipped-wrong-doc", target("SKIPPED", "wrong-doc"))
        assertEquals("REGRESSION", skipped["classification"])
        val control = case("valid-control", target("REPAIRED", expectedDoc))
        assertEquals("EXPECTED_MATCH", control["classification"]); assertEquals("BOUND", control["actualEvidence"])
        // A skipped business outcome may prove the source identity but never a business/admission success.
        val skippedValid = case("skipped-valid-identity", target("SKIPPED", expectedDoc))
        assertEquals("EXPECTED_MATCH", skippedValid["classification"]); assertEquals("SKIPPED", skippedValid["actualTargetOutcome"])
    }

    @Test fun `run evidence origin is decided by CLI input, never candidate self-reported status`() {
        fun case(name: String, email: JsonNode?): Map<String, String> {
            val dir = inputs(Files.createDirectories(root.resolve(name)))
            return reconcile(dir, listOf("--reprocess-run", runExport(dir,
                target("REPAIRED", expectedDoc, email, "ALREADY_RECOVERED")).toString()))
        }
        // A candidate self-reporting a flat replay status must not turn a real run into a flat success:
        // the actual target address is still enforced, and invalid rows stay INVALID/REGRESSION.
        val wrong = case("run-status-wrong-email", mapper.nodeFactory.textNode("different.address@wrong.example"))
        assertEquals("REGRESSION", wrong["classification"]); assertEquals("INVALID", wrong["actualEvidence"])
        val missing = case("run-status-missing-email", null)
        assertEquals("REGRESSION", missing["classification"]); assertEquals("INVALID", missing["actualEvidence"])
        val nullEmail = case("run-status-null-email", mapper.nodeFactory.nullNode())
        assertEquals("REGRESSION", nullEmail["classification"]); assertEquals("INVALID", nullEmail["actualEvidence"])
        // With the correct actual address the same self-reported status still binds as a real run.
        val valid = case("run-status-valid", mapper.nodeFactory.textNode(targetEmail))
        assertEquals("EXPECTED_MATCH", valid["classification"]); assertEquals("BOUND", valid["actualEvidence"])
    }

    @Test fun `reconciliation requires the actual target address to reproduce the audited fingerprint`() {
        fun case(name: String, email: JsonNode?): Map<String, String> {
            val dir = inputs(Files.createDirectories(root.resolve(name)))
            return reconcile(dir, listOf("--reprocess-run", runExport(dir, target("REPAIRED", expectedDoc, email)).toString()))
        }
        val valid = case("email-valid", mapper.nodeFactory.textNode(targetEmail))
        assertEquals("EXPECTED_MATCH", valid["classification"]); assertEquals("BOUND", valid["actualEvidence"])
        // Trim and locale-independent lowercase are the only normalization; the identity still matches.
        val normalized = case("email-case-space", mapper.nodeFactory.textNode("  " + targetEmail.uppercase() + " "))
        assertEquals("EXPECTED_MATCH", normalized["classification"]); assertEquals("BOUND", normalized["actualEvidence"])
        // The candidate self-reports the audited emailHash, so only the actual target address exposes the gap.
        val wrong = case("email-wrong", mapper.nodeFactory.textNode("different.address@wrong.example"))
        assertEquals("REGRESSION", wrong["classification"]); assertEquals("INVALID", wrong["actualEvidence"])
        val absent = case("email-absent", null)
        assertEquals("REGRESSION", absent["classification"]); assertEquals("INVALID", absent["actualEvidence"])
        val nullEmail = case("email-null", mapper.nodeFactory.nullNode())
        assertEquals("REGRESSION", nullEmail["classification"]); assertEquals("INVALID", nullEmail["actualEvidence"])
        val blank = case("email-blank", mapper.nodeFactory.textNode("   "))
        assertEquals("REGRESSION", blank["classification"]); assertEquals("INVALID", blank["actualEvidence"])
        val nonString = case("email-non-string", mapper.nodeFactory.numberNode(42))
        assertEquals("REGRESSION", nonString["classification"]); assertEquals("INVALID", nonString["actualEvidence"])
    }

    @Test fun `reconciliation rejects a conflicting replay binding and keeps honest absence`() {
        val dir = inputs(Files.createDirectories(root.resolve("replay-conflict")))
        val receipt = mapper.createObjectNode()
        receipt.put("manifestSha256", DiscoveryPaperIdentityReplayTool.sha(Files.readAllBytes(dir.resolve("manifest.json"))))
        receipt.put("networkCalls", 0).put("businessWriteServicesConstructed", 0)
        receipt.putArray("rows").addObject().put("recordId", 1).put("bodySha256", bodySha).put("page", 1)
            .put("emailHash", emailHash).put("nameHash", nameHash).put("status", "AMBIGUOUS")
            .put("reason", "CONFLICTING_PRODUCTION_BINDING").putArray("newNames").add("Wrong Name")
        Files.write(dir.resolve("new.json"), mapper.writeValueAsBytes(receipt))
        val conflicted = reconcile(dir, listOf("--new-replay", dir.resolve("new.json").toString()))
        assertEquals("REGRESSION", conflicted["classification"])
        assertEquals("INVALID", conflicted["actualEvidence"])
        val absent = inputs(Files.createDirectories(root.resolve("honest-absence")))
        val absentReceipt = mapper.createObjectNode()
        absentReceipt.put("manifestSha256", DiscoveryPaperIdentityReplayTool.sha(Files.readAllBytes(absent.resolve("manifest.json"))))
        absentReceipt.put("networkCalls", 0).put("businessWriteServicesConstructed", 0)
        absentReceipt.putArray("rows").addObject().put("recordId", 1).put("bodySha256", bodySha).put("page", 1)
            .put("emailHash", emailHash).put("nameHash", nameHash).put("status", "NOT_REPRODUCED").put("reason", "TEST_ONLY")
            .putArray("newNames")
        Files.write(absent.resolve("new.json"), mapper.writeValueAsBytes(absentReceipt))
        val honest = reconcile(absent, listOf("--new-replay", absent.resolve("new.json").toString()))
        assertEquals("EXPECTED_MISSING", honest["classification"]); assertEquals("ABSENT", honest["actualEvidence"])
        // A legitimate flat --new-replay receipt carries no target address and stays valid.
        val flatDir = inputs(Files.createDirectories(root.resolve("flat-replay-valid")))
        val validReceipt = mapper.createObjectNode()
        validReceipt.put("manifestSha256", DiscoveryPaperIdentityReplayTool.sha(Files.readAllBytes(flatDir.resolve("manifest.json"))))
        validReceipt.put("networkCalls", 0).put("businessWriteServicesConstructed", 0)
        validReceipt.putArray("rows").addObject().put("recordId", 1).put("bodySha256", bodySha).put("page", 1)
            .put("emailHash", emailHash).put("nameHash", nameHash).put("status", "ALREADY_RECOVERED")
            .put("reason", "TEST_ONLY").putArray("newNames")
        Files.write(flatDir.resolve("new.json"), mapper.writeValueAsBytes(validReceipt))
        val flat = reconcile(flatDir, listOf("--new-replay", flatDir.resolve("new.json").toString()))
        assertEquals("EXPECTED_MATCH", flat["classification"]); assertEquals("BOUND", flat["actualEvidence"])
    }
}
