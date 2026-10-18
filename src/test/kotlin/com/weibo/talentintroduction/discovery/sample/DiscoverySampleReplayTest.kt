package com.weibo.talentintroduction.discovery.sample

import com.weibo.talentintroduction.config.PdfExtractionProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.service.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.*
import java.util.zip.ZipInputStream

class DiscoverySampleReplayTest {
    @TempDir lateinit var temp: Path
    private val mapper = DiscoverySampleJson.mapper
    private val authors = listOf(PaperAuthor("Alice", "Smith", null, null, true))
    private val xml = """<article><front><article-meta><contrib-group><contrib contrib-type="author"><name><surname>Smith</surname><given-names>Alice</given-names></name><email>alice@example.org</email><email>extra@example.org</email></contrib></contrib-group></article-meta></front></article>""".toByteArray()
    private fun fixture(root: Path, bytes: ByteArray? = xml, kind: DiscoverySampleAttemptKind = DiscoverySampleAttemptKind.PMC_XML,
                        contentType: String? = null, paperAuthors: List<PaperAuthor> = authors,
                        title: String = "synthetic replay"): Path {
        val paper = PaperMetadata(null, null, null, title, 2026, null, paperAuthors, "OPENALEX")
        val input = DiscoverySampleJson.hash(DiscoverySampleJson.canonical(paper))
        val id = DiscoverySampleJson.hash("9001\nOPENALEX\n$input")
        val dir = Files.createDirectories(root.resolve("9001").resolve(id))
        val attempt = DiscoverySampleAttempt(1, kind, "https://example.org/body", DiscoverySampleJson.hash("https://example.org/body"),
            1, if (kind == DiscoverySampleAttemptKind.PDF_URL) (if (bytes == null) 403 else 200) else null, contentType, null,
            if (bytes == null) DiscoverySampleBodyStatus.NOT_AVAILABLE else DiscoverySampleBodyStatus.SAVED,
            if (bytes == null) null else "body-01.bin", bytes?.let(DiscoverySampleJson::hash), bytes?.size?.toLong(),
            if (kind == DiscoverySampleAttemptKind.PDF_URL) PdfExtractionProperties() else null)
        val manifest = DiscoverySampleManifest(sampleId = id, inputSha256 = input, capturedAt = "2026-10-18T00:00:00Z",
            context = DiscoverySampleContext(9001, "OPENALEX", DiscoverySampleEntry.SYNC, 1, null), paper = paper,
            urlFingerprints = listOf(null), bucket = DiscoverySampleBucket.SUCCESS_CONTROL, unresolvedAuthorEmailCount = 0,
            outcome = null, extractionExceptionClass = null, attempts = listOf(attempt), attemptsTruncated = false,
            bodyBytesRetained = bytes?.size?.toLong() ?: 0)
        Files.write(dir.resolve("sample.json"), mapper.writeValueAsBytes(manifest))
        if (bytes != null) Files.write(dir.resolve("body-01.bin"), bytes)
        return dir
    }
    private fun run(vararg args: String) = DiscoverySampleReplay.run(args)
    private fun json(path: Path) = mapper.readTree(Files.readAllBytes(path))
    private fun replay(root: Path, out: Path): Int = run("replay", "--samples", root.toString(), "--output", out.toString())
    private fun hashes(root: Path) = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.sorted().toArray()
        .associate { val p = it as Path; root.relativize(p).toString() to DiscoverySampleJson.hash(Files.readAllBytes(p)) } }

    @Test fun `offline acceptance exports readonly parser results and labelled metrics`() {
        val base = System.getProperty("sample.acceptanceDir")?.let { Paths.get(it) } ?: temp
        val fixtures = base.resolve("replay-fixtures")
        val reports = Files.createDirectories(base.resolve("replay"))
        // Overwrite only this test's deterministic synthetic packages, never captured inputs.
        val good = fixtures.resolve("good"); fixture(good)
        val noBody = fixtures.resolve("no-body"); fixture(noBody, null, DiscoverySampleAttemptKind.PDF_URL)
        val corrupt = fixtures.resolve("hash-mismatch"); val bad = fixture(corrupt)
        Files.write(bad.resolve("body-01.bin"), "corrupt".toByteArray())
        val unsupported = fixtures.resolve("unsupported"); val unsupportedDir = fixture(unsupported)
        val unsupportedJson = json(unsupportedDir.resolve("sample.json")) as com.fasterxml.jackson.databind.node.ObjectNode
        unsupportedJson.put("schemaVersion", 2)
        Files.write(unsupportedDir.resolve("sample.json"), mapper.writeValueAsBytes(unsupportedJson))
        val before = hashes(fixtures)
        val a = reports.resolve("A.json"); val b = reports.resolve("B.json")
        assertEquals(0, replay(good, a)); assertEquals(0, replay(good, b))
        fun stable(path: Path): com.fasterxml.jackson.databind.JsonNode {
            val node = json(path) as com.fasterxml.jackson.databind.node.ObjectNode
            node.remove("generatedAt")
            node["bodies"].forEach { (it as com.fasterxml.jackson.databind.node.ObjectNode).remove("elapsedMs") }
            return node
        }
        assertEquals(stable(a), stable(b))
        val list = reports.resolve("list.json")
        assertEquals(0, run("list", "--samples", good.toString(), "--execution-id", "9001", "--output", list.toString()))
        assertEquals(1, json(list)["savedSamples"].asInt()); assertEquals(1, json(list)["savedBodies"].asInt())
        val body = json(a)["bodies"].single()
        assertEquals(mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(DiscoverySampleReplay.sortedBindings(JatsXmlEmailParser.parse(xml))), body["bindings"])
        assertEquals(0, body["networkCalls"].asInt()); assertEquals("PARSER_ONLY", json(a)["scope"].asText())
        val diff = reports.resolve("diff.json")
        assertEquals(0, run("compare", "--before", a.toString(), "--after", b.toString(), "--output", diff.toString()))
        assertEquals(0, json(diff)["added"].asInt()); assertEquals(0, json(diff)["removed"].asInt()); assertEquals(0, json(diff)["changed"].asInt())
        assertTrue(json(diff)["accuracy"].isNull); assertEquals(0, json(diff)["labelledBodies"].asInt())
        val labels = reports.resolve("labels.json")
        Files.write(labels, mapper.writeValueAsBytes(mapOf("reportVersion" to 1, "bodies" to listOf(mapOf(
            "inputSha256" to body["inputSha256"].asText(), "bodySha256" to body["bodySha256"].asText(),
            "bindings" to listOf(mapOf("email" to "alice@example.org", "givenNames" to "Alice", "familyNames" to "Smith")))))))
        val labelled = reports.resolve("labelled-diff.json")
        assertEquals(0, run("compare", "--before", a.toString(), "--after", b.toString(), "--labels", labels.toString(), "--output", labelled.toString()))
        for (name in listOf("beforeMetrics", "afterMetrics")) {
            val metrics = json(labelled)[name]; assertEquals(1, metrics["TP"].asInt()); assertEquals(1, metrics["FP"].asInt())
            assertEquals(0, metrics["FN"].asInt()); assertEquals(0.5, metrics["precision"].asDouble()); assertEquals(1.0, metrics["recall"].asDouble())
        }
        val exits = linkedMapOf<String, Int>()
        for (name in listOf("good", "no-body", "hash-mismatch", "unsupported")) exits[name] = replay(fixtures.resolve(name), reports.resolve("$name.json"))
        assertEquals(mapOf("good" to 0, "no-body" to 0, "hash-mismatch" to 2, "unsupported" to 2), exits)
        assertEquals("NO_BODY", json(reports.resolve("no-body.json"))["bodies"].single()["status"].asText())
        assertEquals(0, json(reports.resolve("no-body.json"))["bodies"].single()["parserCalls"].asInt())
        assertEquals(403, json(reports.resolve("no-body.json"))["bodies"].single()["parserConfig"]["httpStatus"].asInt())
        assertEquals("HASH_MISMATCH", json(reports.resolve("hash-mismatch.json"))["errors"].single()["code"].asText())
        assertEquals("UNSUPPORTED_SCHEMA", json(reports.resolve("unsupported.json"))["errors"].single()["code"].asText())
        assertEquals(2, run("compare", "--before", a.toString(), "--after", reports.resolve("no-body.json").toString(), "--output", reports.resolve("input-mismatch.json").toString()))
        assertEquals("INPUT_MISMATCH", json(reports.resolve("input-mismatch.json"))["status"].asText())
        assertEquals(before, hashes(fixtures))
        Files.write(reports.resolve("hashes-before.json"), mapper.writeValueAsBytes(before))
        Files.write(reports.resolve("hashes-after.json"), mapper.writeValueAsBytes(hashes(fixtures)))
        val entity = """<!DOCTYPE article [<!ENTITY remote SYSTEM "http://127.0.0.1:1/must-not-fetch">]><article><front><article-meta><p>&remote;</p></article-meta></front></article>""".toByteArray()
        assertTrue(JatsXmlEmailParser.parse(entity).isEmpty())
        val entityRoot = temp.resolve("entity"); fixture(entityRoot, entity)
        assertEquals(0, replay(entityRoot, temp.resolve("entity.json")))
        assertTrue(json(temp.resolve("entity.json"))["bodies"].single()["bindings"].isEmpty)
        Files.write(reports.resolve("cases.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(mapOf(
            "offline" to mapOf("networkCalls" to 0, "businessWrites" to 0, "scope" to "PARSER_ONLY", "proof" to "rejecting request factory; local body executor; no application context"),
            "xml-external-entity" to mapOf("passed" to true, "bindings" to 0),
            "read-only" to mapOf("inputHashesUnchanged" to (before == hashes(fixtures))), "fixtureExitCodes" to exits,
            "labels" to labels.toString(), "labelledReport" to labelled.toString())))
    }

    @Test fun `html and unchanged real PDF match direct production parser field for field`() {
        val html = "<html><body>Alice Smith alice@example.org</body></html>".toByteArray()
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(requireNotNull(javaClass.getResourceAsStream("/discovery/pdf-contact-coverage.zip"))).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
        }
        val metadata = mapper.readTree(requireNotNull(entries["sources/W4288039037/metadata.json"]))
        val realAuthors = metadata["authorships"].map { val name = it["author"]["display_name"].asText()
            PaperAuthor(name.substringBeforeLast(' '), name.substringAfterLast(' '), null, null, false) }
        for ((name, bytes, known) in listOf(Triple("html", html, authors), Triple("pdf", requireNotNull(entries["sources/W4288039037/source.pdf"]), realAuthors))) {
            val type = if (name == "pdf") "application/pdf" else "text/html"
            val root = temp.resolve(name); fixture(root, bytes, DiscoverySampleAttemptKind.PDF_URL, type, known)
            val out = temp.resolve("$name.json"); assertEquals(0, replay(root, out))
            val executor = DiscoverySampleReplay.LocalBodyExecutor("https://example.org/body", bytes, 200, type)
            val rest = DiscoverySampleReplay.rejectingRestTemplate()
            val direct = PdfEmailExtractor(rest, PlainTextEmailExtractor(), PdfExtractionProperties(), executor)
                .extract("https://example.org/body", known, "OFFLINE", deadline = null)
            assertEquals(mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(DiscoverySampleReplay.sortedBindings(direct.emails)), json(out)["bodies"].single()["bindings"])
            assertEquals(direct.failureReason, json(out)["bodies"].single()["parseFailure"].takeUnless { it.isNull }?.asText())
            assertEquals(1, executor.calls)
        }
    }

    @Test fun `strict path schema limits races and output isolation reject before parsing`() {
        val root = temp.resolve("samples"); val dir = fixture(root)
        assertEquals(64, run("unknown")); assertEquals(64, run("list", "--samples", root.toString(), "--output", "x", "--bogus", "x"))
        assertEquals(2, replay(root, root.resolve("report.json"))); assertFalse(Files.exists(root.resolve("report.json")))
        val outside = temp.resolve("outside"); Files.write(outside, xml)
        Files.delete(dir.resolve("body-01.bin")); Files.createSymbolicLink(dir.resolve("body-01.bin"), outside)
        val out = temp.resolve("symlink.json"); assertEquals(2, replay(root, out)); assertEquals("INVALID_PATH", json(out)["errors"].single()["code"].asText())
        Files.delete(dir.resolve("body-01.bin")); Files.write(dir.resolve("body-01.bin"), xml)
        DiscoverySampleReplay.beforeBodyRead = { Files.delete(it) }
        try { assertEquals(2, replay(root, temp.resolve("expired.json"))) } finally { DiscoverySampleReplay.beforeBodyRead = null }
        assertEquals("EXPIRED_OR_REMOVED", json(temp.resolve("expired.json"))["errors"].single()["code"].asText())
        fixture(root)
        val tree = json(dir.resolve("sample.json")) as com.fasterxml.jackson.databind.node.ObjectNode
        (tree["attempts"][0] as com.fasterxml.jackson.databind.node.ObjectNode).put("bodyFile", "../../outside")
        Files.write(dir.resolve("sample.json"), mapper.writeValueAsBytes(tree))
        assertEquals(2, replay(root, temp.resolve("escape.json"))); assertEquals("INVALID_PATH", json(temp.resolve("escape.json"))["errors"].single()["code"].asText())
        fixture(root)
        Files.write(dir.resolve("body-01.bin"), ByteArray(10_485_761))
        assertEquals(2, replay(root, temp.resolve("large.json"))); assertEquals("BODY_TOO_LARGE", json(temp.resolve("large.json"))["errors"].single()["code"].asText())
        fixture(root)
        val missing = json(dir.resolve("sample.json")) as com.fasterxml.jackson.databind.node.ObjectNode; missing.remove("attempts")
        Files.write(dir.resolve("sample.json"), mapper.writeValueAsBytes(missing))
        assertEquals(2, replay(root, temp.resolve("missing.json")))
        fixture(root)
        val limited = json(dir.resolve("sample.json")) as com.fasterxml.jackson.databind.node.ObjectNode
        val attempt = limited["attempts"][0] as com.fasterxml.jackson.databind.node.ObjectNode
        attempt.put("bodyStatus", "BODY_LIMIT"); attempt.putNull("bodyFile"); attempt.putNull("bodySha256")
        limited.put("bodyBytesRetained", 0)
        Files.write(dir.resolve("sample.json"), mapper.writeValueAsBytes(limited))
        assertEquals(0, replay(root, temp.resolve("limited.json")))
        assertEquals("BODY_LIMIT", json(temp.resolve("limited.json"))["bodies"].single()["status"].asText())
        Files.write(dir.resolve("sample.json"), ByteArray(1_048_577))
        val other = fixture(root, title = "valid sibling")
        assertNotEquals(dir, other)
        assertEquals(2, replay(root, temp.resolve("mixed.json")))
        assertEquals("METADATA_LIMIT", json(temp.resolve("mixed.json"))["errors"].single()["code"].asText())
        assertEquals(1, json(temp.resolve("mixed.json"))["bodies"].size())
        val malformedRoot = temp.resolve("malformed")
        fixture(malformedRoot, "<article>".toByteArray())
        assertEquals(2, replay(malformedRoot, temp.resolve("malformed.json")))
        assertEquals("PARSE_ERROR", json(temp.resolve("malformed.json"))["bodies"].single()["status"].asText())
    }

    @Test fun `comparison exposes duplicates conflicts all fields and validates labels inputs configuration`() {
        val root = temp.resolve("samples"); fixture(root)
        val a = temp.resolve("A.json"); val b = temp.resolve("B.json"); val out = temp.resolve("diff.json")
        assertEquals(0, replay(root, a))
        val original = json(a)
        fun compare(tree: com.fasterxml.jackson.databind.JsonNode, labels: Path? = null): Int {
            Files.write(b, mapper.writeValueAsBytes(tree))
            val args = mutableListOf("compare", "--before", a.toString(), "--after", b.toString(), "--output", out.toString())
            if (labels != null) args.addAll(listOf("--labels", labels.toString()))
            return run(*args.toTypedArray())
        }
        val changed = original.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        (changed["bodies"][0]["bindings"][0] as com.fasterxml.jackson.databind.node.ObjectNode).put("institutionName", "Changed")
        assertEquals(0, compare(changed)); assertEquals(1, json(out)["changed"].asInt())
        val duplicates = original.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        (duplicates["bodies"][0]["bindings"] as com.fasterxml.jackson.databind.node.ArrayNode).add(duplicates["bodies"][0]["bindings"][0])
        assertEquals(0, compare(duplicates)); assertFalse(json(out)["conflicts"].isEmpty)
        val mismatched = original.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        (mismatched["bodies"][0] as com.fasterxml.jackson.databind.node.ObjectNode).put("inputSha256", "0".repeat(64))
        assertEquals(2, compare(mismatched)); assertEquals("INPUT_MISMATCH", json(out)["status"].asText())
        val config = original.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        (config["bodies"][0]["parserConfig"] as com.fasterxml.jackson.databind.node.ObjectNode).put("postprocessing", "DIFFERENT")
        assertEquals(2, compare(config))
        val version = original.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("reportVersion", 2)
        assertEquals(2, compare(version)); assertEquals("INPUT_MISMATCH", json(out)["status"].asText())
        val labels = temp.resolve("bad-labels.json")
        Files.write(labels, mapper.writeValueAsBytes(mapOf("reportVersion" to 1, "bodies" to listOf(mapOf("inputSha256" to "0".repeat(64), "bodySha256" to "0".repeat(64), "bindings" to emptyList<Any>())))))
        assertEquals(2, compare(original, labels)); assertEquals("INPUT_MISMATCH", json(out)["status"].asText())

        var deniedRequests = 0
        val rest = DiscoverySampleReplay.rejectingRestTemplate { deniedRequests++ }
        assertThrows(Exception::class.java) { rest.getForObject("https://example.org/forbidden", String::class.java) }
        assertEquals(1, deniedRequests)
        val executor = DiscoverySampleReplay.LocalBodyExecutor("https://example.org/body", xml, 200, "text/html")
        assertThrows(Exception::class.java) {
            executor.getForObject(rest, "https://example.org/forbidden", String::class.java, 1, 1, null)
        }
        assertEquals(1, executor.violations); assertEquals(0, executor.calls)
    }
}
