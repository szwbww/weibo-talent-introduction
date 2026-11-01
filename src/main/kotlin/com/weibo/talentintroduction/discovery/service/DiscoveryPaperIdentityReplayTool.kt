package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.BoundedHttpExecutor
import com.weibo.talentintroduction.config.PdfExtractionProperties
import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.web.client.ResponseExtractor
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

/** File-only replay. No application context, repositories, scheduling or write-service graph. */
object DiscoveryPaperIdentityReplayTool {
    private val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    internal fun normalizedName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun fingerprint(value: String) = sha(value.toByteArray()).take(24)
    private fun name(node: JsonNode): String = listOf("givenNames", "familyNames")
        .map { node.path(it).asText("") }.joinToString(" ").trim()
    private fun complete(node: JsonNode) = listOf("givenNames", "familyNames").all { node.path(it).asText("").isNotBlank() }

    /** These traps also make accidental future download calls fail closed. */
    internal fun analyzer(settings: PdfExtractionProperties, onNetwork: () -> Unit = {}): PaperBodyAnalyzer {
        fun denied(): Nothing { onNetwork(); error("OFFLINE_NETWORK_FORBIDDEN") }
        val rest = RestTemplate(ClientHttpRequestFactory { _, _ -> denied() })
        val bounded = object : BoundedHttpExecutor {
            override fun <T : Any> getForObject(base: RestTemplate, url: String, responseType: Class<T>,
                connectCapMs: Long, readCapMs: Long, deadline: Instant?): T? = denied()
            override fun <T> execute(base: RestTemplate, uri: URI, connectCapMs: Long, readCapMs: Long,
                deadline: Instant?, responseExtractor: ResponseExtractor<T>): T? = denied()
        }
        return PaperBodyAnalyzer(PdfEmailExtractor(rest, PlainTextEmailExtractor(), settings, bounded),
            PaperQualificationEvidenceExtractor(), mapper)
    }

    private fun inventory(snapshot: JsonNode?, emailHash: String): Map<String, Any?> {
        if (snapshot == null || !snapshot.isObject) return mapOf("status" to "INVENTORY_MISSING")
        val hits = snapshot.path("hits").filter { hit ->
            val email = hit.path("_source").path("email")
            (if (email.isArray) email.toList() else listOf(email)).any {
                fingerprint(it.asText().trim().lowercase(Locale.ROOT)) == emailHash }
        }
        val contacted = snapshot.path("contacts").any {
            fingerprint(it.path("email").asText().trim().lowercase(Locale.ROOT)) == emailHash }
        val documents = hits.map { hit ->
            val source = hit.path("_source")
            val docId = hit.path("_id").asText()
            val admission = snapshot.path("admissions").firstOrNull { it.path("docId").asText() == docId }
            val protected = contacted || hit.path("_index").asText().endsWith("_application") ||
                source.path("operatorStatus").asText("").isNotBlank() ||
                admission?.path("decision")?.asText()?.startsWith("MANUAL") == true
            mapOf("docId" to docId, "index" to hit.path("_index").asText(), "version" to hit.path("_version").asLong(),
                "seqNo" to hit.path("_seq_no").asLong(), "primaryTerm" to hit.path("_primary_term").asLong(),
                "admission" to admission, "protected" to protected,
                "status" to when {
                    protected -> "MANUAL_CONTACT_OR_APPLICATION_PROTECTED"
                    !complete(source) -> "EMPTY_NAME"
                    !source.path("identityVerification").isObject || !DiscoveryIdentity.allowedSource(source) -> "COMPLETE_NAME_MISSING_PROOF"
                    else -> "COMPLETE"
                })
        }
        return mapOf("status" to when {
            documents.isEmpty() -> "NOT_CREATED"
            hits.map { it.path("_id").asText() }.distinct().size > 1 -> "MULTIPLE_DOCUMENT_CONFLICT"
            documents.any { it["protected"] == true } -> "MANUAL_CONTACT_OR_APPLICATION_PROTECTED"
            else -> documents.first()["status"]
        }, "documents" to documents)
    }

    private fun local(root: Path, relative: String): Path {
        require(relative.isNotBlank()) { "SOURCE_PATH_MISSING" }
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root) && !Path.of(relative).isAbsolute) { "SOURCE_PATH_ESCAPE" }
        if (Files.exists(path)) require(path.toRealPath().startsWith(root.toRealPath())) { "SOURCE_PATH_ESCAPE" }
        return path
    }

    internal fun replay(root: Path, manifest: JsonNode, recordIds: Set<Long> = emptySet(),
        onNetwork: () -> Unit = {}): List<Map<String, Any?>> {
        require(manifest.path("schemaVersion").asInt() == 1) { "MANIFEST_VERSION" }
        val combinations = manifest.path("combinations").toList()
        fun key(c: JsonNode) = listOf("recordId", "bodySha256", "emailHash", "nameHash", "page").map { c.path(it).asText() }
        require(combinations.map(::key).distinct().size == combinations.size) { "DUPLICATE_COMBINATION" }
        val records = manifest.path("records").toList()
        require(records.map { it.path("recordId").asLong() }.distinct().size == records.size) { "DUPLICATE_RECORD" }
        val byId = records.associateBy { it.path("recordId").asLong() }
        val settingsNode = manifest.path("parserConfig")
        val settings = mapper.treeToValue(settingsNode.path("settings"), PdfExtractionProperties::class.java)
        val parser = analyzer(settings, onNetwork)
        val output = mutableListOf<Map<String, Any?>>()
        for ((recordId, cases) in combinations.filter { recordIds.isEmpty() || it.path("recordId").asLong() in recordIds }
            .groupBy { it.path("recordId").asLong() }) {
            val started = System.nanoTime()
            val record = byId[recordId]
            var missing: String? = null
            var metadata: JsonNode? = null
            var inventorySnapshot: JsonNode? = null
            var first: JsonNode? = null
            var parsed: PaperBodyComputation? = null
            var pageTexts = emptyMap<Int, String>()
            var selected = emptyList<Int>()
            var contacts = emptyList<PdfAuthorContactLayout.Contact>()
            try {
                require(record != null) { "RECORD_SOURCE_MISSING" }
                require(settingsNode.path("historicalEvidenceAvailable").asBoolean()) { "ORIGINAL_CONFIG_MISSING" }
                val sourceBytes = Files.readAllBytes(local(root, record.path("sourceFile").asText()))
                require(sha(sourceBytes) == record.path("sourceSha256").asText()) { "SOURCE_HASH_MISMATCH" }
                val source = mapper.readTree(sourceBytes)
                inventorySnapshot = source.get("inventory")
                val metadataRaw = source.path("metadataRaw").asText()
                require(sha(metadataRaw.toByteArray()) == record.path("metadataSha256").asText()) { "METADATA_HASH_MISMATCH" }
                metadata = mapper.readTree(metadataRaw)
                require(metadata.path("authors").isArray) { "AUTHORS_METADATA_MISSING" }
                val outcomeRaw = source.path("outcomeRaw").asText()
                require(sha(outcomeRaw.toByteArray()) == record.path("outcomeSha256").asText()) { "OUTCOME_HASH_MISMATCH" }
                first = mapper.readTree(outcomeRaw).path("first")
                require(first.path("outcome").path("emails").isArray && first.path("attemptIds").any {
                    it.asLong() == record.path("attemptId").asLong() }) { "FIRST_OUTCOME_MISSING" }
                val body = Files.readAllBytes(local(root, record.path("bodyFile").asText()))
                require(sha(body) == record.path("bodySha256").asText()) { "BODY_HASH_MISMATCH" }
                require(body.size.toLong() == record.path("sizeBytes").asLong()) { "BODY_SIZE_MISMATCH" }
                require(cases.all { it.path("bodySha256").asText() == record.path("bodySha256").asText() }) { "COMBINATION_BODY_MISMATCH" }
                parsed = parser.parse(PaperBodyInput(record.path("bodySha256").asText(), ArchiveAttemptKind.valueOf(record.path("kind").asText()), body), metadata)
                require(parsed.format == "PDF") { "NOT_PDF" }
                PDDocument.load(body).use { document ->
                    selected = PdfAuthorContactLayout.selectedPages(document.numberOfPages, settings.maxPages, settings.tailPages)
                    val texts = mutableMapOf<Int, String>()
                    contacts = PdfAuthorContactLayout.collect(document, settings.maxPages,
                        metadata.path("authors").map { mapper.treeToValue(it, PaperAuthor::class.java) }, settings.tailPages,
                        onPage = { page, text -> texts[page] = text })
                    // Out-of-range audited pages are diagnostic only, never fed back to the production parser.
                    for (page in cases.map { it.path("page").asInt() }.distinct()) {
                        if (page in 1..document.numberOfPages && page !in texts) {
                            val stripper = PDFTextStripper(); stripper.startPage = page; stripper.endPage = page
                            texts[page] = stripper.getText(document)
                        }
                    }
                    pageTexts = texts
                }
            } catch (e: Exception) {
                missing = when (e) {
                    is java.nio.file.NoSuchFileException -> "SOURCE_FILE_MISSING"
                    else -> e.message?.take(180) ?: e.javaClass.simpleName
                }
            }
            val elapsed = (System.nanoTime() - started) / 1_000_000
            for (c in cases) {
                val emailHash = c.path("emailHash").asText()
                val nameHash = c.path("nameHash").asText()
                val before = first?.path("outcome")?.path("emails")?.filter {
                    fingerprint(it.path("email").asText().trim().lowercase(Locale.ROOT)) == emailHash }.orEmpty()
                val authors = metadata?.path("authors")?.filter { fingerprint(normalizedName(name(it))) == nameHash }.orEmpty()
                val expected = authors.singleOrNull()
                val actual = parsed?.emails?.filter { fingerprint(it.email.trim().lowercase(Locale.ROOT)) == emailHash }.orEmpty()
                val actualNodes = actual.map { mapper.valueToTree<JsonNode>(it) }
                val address = before.map { it.path("email").asText().trim().lowercase(Locale.ROOT) }.distinct().singleOrNull()
                val page = c.path("page").asInt()
                val text = pageTexts[page].orEmpty()
                val emailCovered = address != null && text.lowercase(Locale.ROOT).contains(address)
                val nameCovered = expected != null && normalizedName(text).contains(normalizedName(name(expected)))
                val bound = actualNodes.any { complete(it) && fingerprint(normalizedName(name(it))) == nameHash }
                val conflicting = actualNodes.any { complete(it) && fingerprint(normalizedName(name(it))) != nameHash }
                val matchingContacts = contacts.filter { fingerprint(it.email.trim().lowercase(Locale.ROOT)) == emailHash }
                val reason = when {
                    missing != null -> missing
                    expected == null -> "METADATA_NAME_NOT_UNIQUE"
                    before.size != 1 -> "BASELINE_EMAIL_NOT_UNIQUE"
                    c.path("negative").asBoolean() -> "UNCONFIRMED_COLUMN_NEGATIVE"
                    conflicting -> "CONFLICTING_PRODUCTION_BINDING"
                    bound -> "CURRENT_BASELINE_RECOVERS_BINDING"
                    page !in selected -> "AUDITED_PAGE_OUTSIDE_PRODUCTION_SELECTION"
                    !emailCovered -> "EMAIL_NOT_LITERAL_IN_PDFBOX_PAGE"
                    !nameCovered -> "NAME_NOT_LITERAL_IN_PDFBOX_PAGE"
                    matchingContacts.isEmpty() -> "NO_PRODUCTION_LAYOUT_CONTACT"
                    else -> "LAYOUT_CONTACT_NOT_ACCEPTED_BY_RESOLVER"
                }
                val status = when {
                    missing != null -> "SOURCE_MISSING"
                    expected == null || before.size != 1 || conflicting || c.path("negative").asBoolean() -> "AMBIGUOUS"
                    bound -> "ALREADY_RECOVERED"
                    parsed?.reasons?.isNotEmpty() != false -> "NOT_REPRODUCED"
                    page !in selected || (emailCovered && nameCovered) -> "REPRODUCED"
                    else -> "NOT_REPRODUCED"
                }
                output += linkedMapOf("recordId" to recordId, "attemptId" to record?.path("attemptId")?.asLong(),
                    "bodySha256" to c.path("bodySha256").asText(), "page" to page,
                    "authorKey" to c.path("authorKey").asText(), "emailHash" to emailHash, "nameHash" to nameHash,
                    "metadataAuthorPresent" to (expected != null), "oldNames" to before.map(::name),
                    "expectedName" to expected?.let(::name), "newNames" to actualNodes.map(::name),
                    "oldParserVersion" to first?.path("parserVersion"), "replayParserVersion" to parser.versions(),
                    "selectedPages" to selected, "pdfboxEmailCovered" to emailCovered, "pdfboxNameCovered" to nameCovered,
                    "pdfboxPageSha256" to pageTexts[page]?.let { sha(it.toByteArray()) },
                    "productionRules" to matchingContacts.map { it.rule ?: "MARKER_OR_PARAGRAPH_CONTACT" }.distinct(),
                    "method" to "PdfAuthorContactLayout.collect -> SourceAuthorEmailResolver.resolvePdf",
                    "status" to status, "reason" to reason, "elapsedFileMs" to elapsed,
                    "auditRule" to c.path("auditRule").asText(), "negative" to c.path("negative").asBoolean(),
                    "inventory" to inventory(inventorySnapshot, emailHash))
            }
        }
        return output
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size in 3..4) { "Usage: DiscoveryPaperIdentityReplayTool SOURCE_DIRECTORY MANIFEST OUTPUT_JSON [recordId,...]" }
        val root = Path.of(args[0]).toRealPath()
        val manifestPath = Path.of(args[1]).toRealPath()
        val output = Path.of(args[2]).toAbsolutePath().normalize()
        require(!Files.exists(output)) { "RECEIPT_ALREADY_EXISTS" }
        require(output != manifestPath && !output.startsWith(root)) { "OUTPUT_MUST_NOT_OVERWRITE_INPUT" }
        val manifestBytes = Files.readAllBytes(manifestPath)
        val records = args.getOrNull(3)?.split(',')?.map { it.toLong() }?.toSet().orEmpty()
        var networkCalls = 0
        val rows = replay(root, mapper.readTree(manifestBytes), records) { networkCalls++ }
        check(networkCalls == 0) { "NETWORK_TRAP_TRIGGERED" }
        Files.write(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(mapOf(
            "manifestSha256" to sha(manifestBytes), "networkCalls" to networkCalls,
            "businessWriteServicesConstructed" to 0, "rows" to rows)))
        println("replayed=${rows.size} networkCalls=$networkCalls output=$output")
    }
}
