package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.sample.DiscoverySampleReplay
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths

/** Pure parser fixtures; private replay consumes frozen sample packages, never downloads. */
class DiscoveryPaperLayoutRegressionTest {
    @Test fun `synthetic actual double column parser smoke`() = LayoutPdfFixtures.smoke()
    @Test fun `unique layout owners survive edge coordinates and segment boundaries`() {
        LayoutPdfFixtures.child03Observations()
    }

    @Test fun `private original corpus replay explicitly skips when unavailable`() {
        val root = System.getenv("DISCOVERY_LAYOUT_FROZEN_SAMPLES")
        assumeTrue(!root.isNullOrBlank(), "SKIPPED: exact original294 frozen raw corpus unavailable; no real-effect claim")
        val output = System.getenv("DISCOVERY_LAYOUT_REPLAY_OUTPUT")
        require(!output.isNullOrBlank()) { "Private output path required" }
        require(Files.isDirectory(Paths.get(root!!)))
        assertEquals(0, DiscoverySampleReplay.run(arrayOf("replay", "--samples", root, "--output", output!!)))
    }

    @Test fun `30711 original bytes recover evidenced syntax and traverse the actual consumer`() {
        val ids = setOf(31372L, 31375L, 31398L, 31494L, 31992L, 32137L, 32689L, 31479L)
        val manifest = ReprocessOriginalInputs.manifest()
        val replay = DiscoveryPaperIdentityReplayTool.replay(ReprocessOriginalInputs.directory, manifest, ids) {
            fail<Unit>("Original-byte replay must never use the network")
        }
        assertEquals(ids, replay.map { (it["recordId"] as Number).toLong() }.toSet())
        for (row in replay) assertTrue((row["newNames"] as List<*>).contains(row["expectedName"]), row.toString())
        for (id in ids) {
            val input = ReprocessOriginalInputs.load(id)
            val parsed = input.extractor.extractEmailsFromBytes(input.bytes, input.authors, null)
            val named = parsed.filter { it.givenNames != null && it.familyNames != null }
            assertTrue(named.isNotEmpty(), "$id")
            assertTrue(named.all { com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.validEvidence(it.identityEvidence) })
            assertReprocessConsumer(named, input.metadata)
        }
    }

    @Test fun `new named contact continuations cannot cross columns pages authors or sections`() {
        val a = LayoutPdfFixtures.authors
        fun resolve(rows: List<LayoutPdfFixtures.Row>) = extractOwnershipContent(
            LayoutPdfFixtures.bytes(rows), org.springframework.http.MediaType.APPLICATION_PDF, a).emails
        val prefix = "*Corresponding author: Alice Smith, Department of Science,"
        val cases = listOf(
            listOf(LayoutPdfFixtures.Row(prefix, 600f), LayoutPdfFixtures.Row("University. Email: alice@sample.edu", 612f, 340f)),
            listOf(LayoutPdfFixtures.Row(prefix, 600f), LayoutPdfFixtures.Row("Unknown Owner is with Another Institute", 612f),
                LayoutPdfFixtures.Row("Email: alice@sample.edu", 624f)),
            listOf(LayoutPdfFixtures.Row(prefix, 600f), LayoutPdfFixtures.Row("Abstract", 612f),
                LayoutPdfFixtures.Row("Email: alice@sample.edu", 624f)),
            listOf(LayoutPdfFixtures.Row("References", 580f), LayoutPdfFixtures.Row("Alice Smith <alice@sample.edu>", 600f)),
            listOf(LayoutPdfFixtures.Row("alice@sample.edu", 600f), LayoutPdfFixtures.Row("(Alice Smith)", 612f, 340f)),
            listOf(LayoutPdfFixtures.Row("alice@sample.edu", 600f), LayoutPdfFixtures.Row("(Alice Smith)", 20f, page = 1)),
            listOf(LayoutPdfFixtures.Row("Alice Smith and Bob Jones (alice@sample.edu)", 600f))
        )
        cases.forEachIndexed { index, rows -> assertTrue(resolve(rows).all { it.givenNames == null }, "negative $index") }
        val positive = resolve(listOf(LayoutPdfFixtures.Row(prefix, 600f),
            LayoutPdfFixtures.Row("University. Email: alice@sample.edu", 612f)))
        assertEquals("Alice", positive.single().givenNames)
        val wrapped = resolve(listOf(LayoutPdfFixtures.Row("alice@sample.edu", 600f),
            LayoutPdfFixtures.Row("(Alice Smith)", 612f)))
        assertEquals("Alice", wrapped.single().givenNames)
    }

    @Test fun `inline reference headings preserve clues without named ownership`() {
        for (heading in listOf("References", "Bibliography")) {
            val rows = listOf(LayoutPdfFixtures.Row("$heading: Alice Smith (opaque@sample.edu)", 600f))
            val result = extractOwnershipContent(LayoutPdfFixtures.bytes(rows),
                org.springframework.http.MediaType.APPLICATION_PDF, LayoutPdfFixtures.authors).emails.single()
            assertEquals("opaque@sample.edu", result.email)
            assertNull(result.givenNames)
            assertNull(result.identityEvidence)
        }
    }

    @Test fun `explicit split domain contact stays within physical boundaries`() {
        val anchor = LayoutPdfFixtures.Row("Alice Smith: opaque@", 600f)
        val domain = LayoutPdfFixtures.Row("sample.edu", 612f)
        fun resolve(rows: List<LayoutPdfFixtures.Row>) = extractOwnershipContent(
            LayoutPdfFixtures.bytes(rows), org.springframework.http.MediaType.APPLICATION_PDF,
            LayoutPdfFixtures.authors).emails
        for (rows in listOf(listOf(anchor, domain),
            listOf(LayoutPdfFixtures.Row("Alice Smith: opaque@sample.edu", 600f)))) {
            val result = resolve(rows).single()
            assertEquals("opaque@sample.edu", result.email)
            assertEquals("Alice", result.givenNames)
            assertTrue(com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.validEvidence(result.identityEvidence))
        }
        val contact = LayoutPdfFixtures.contacts(listOf(anchor, domain)).single()
        assertEquals("Alice Smith: opaque@\nsample.edu", contact.contactText)
        val negatives = listOf(
            listOf(anchor, domain.copy(x = 340f)),
            listOf(anchor, domain.copy(y = 20f, page = 1)),
            listOf(anchor, domain.copy(y = 630f)),
            listOf(anchor, LayoutPdfFixtures.Row("Bob Jones", 612f), domain.copy(y = 624f)),
            listOf(anchor, LayoutPdfFixtures.Row("Unknown Owner is with Another Institute", 612f), domain.copy(y = 624f)),
            listOf(anchor, LayoutPdfFixtures.Row("Abstract", 612f), domain.copy(y = 624f)),
            listOf(anchor, LayoutPdfFixtures.Row("References", 612f), domain.copy(y = 624f)),
            listOf(anchor, LayoutPdfFixtures.Row("Bibliography: cited work", 612f), domain.copy(y = 624f))
        )
        negatives.forEachIndexed { index, rows ->
            assertTrue(resolve(rows).all { it.givenNames == null && it.identityEvidence == null }, "boundary $index")
            assertTrue(LayoutPdfFixtures.contacts(rows).isEmpty(), "physical boundary $index")
        }
    }

    @Test fun `superscript ownership still counts unknown signatures and shared markers`() {
        fun pdf(shared: Boolean) = PDDocument().use { doc ->
            doc.addPage(PDPage())
            PDPageContentStream(doc, doc.getPage(0)).use { stream ->
                fun text(value: String, x: Float, y: Float, size: Float) {
                    stream.beginText(); stream.setFont(PDType1Font.HELVETICA, size)
                    stream.newLineAtOffset(x, y); stream.showText(value); stream.endText()
                }
                text("Alice Smith", 40f, 700f, 12f)
                text("1,*", 102f, 705.2f, 8f)
                if (shared) { text("Unknown Owner", 180f, 700f, 12f); text("2,*", 270f, 705.2f, 8f) }
                text("Department of Science", 40f, 675f, 10f)
                text("*alice@sample.edu", 40f, 600f, 10f)
            }
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        for (shared in listOf(false, true)) {
            val emails = extractOwnershipContent(pdf(shared), org.springframework.http.MediaType.APPLICATION_PDF,
                listOf(LayoutPdfFixtures.authors.first())).emails
            assertEquals(if (shared) null else "Alice", emails.single().givenNames)
        }
    }
}

internal object LayoutPdfFixtures {
    val authors = listOf(PaperAuthor("Alice", "Smith", null, null, false), PaperAuthor("Bob", "Jones", null, null, false))
    data class Row(val text: String, val y: Float, val x: Float = 40f, val page: Int = 0)
    fun bytes(rows: List<Row>): ByteArray = PDDocument().use { doc ->
        repeat((rows.maxOfOrNull { it.page } ?: 0) + 1) { doc.addPage(PDPage()) }
        for (page in 0 until doc.numberOfPages) PDPageContentStream(doc, doc.getPage(page)).use { stream ->
            for (row in rows.filter { it.page == page }) {
                stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 10f)
                stream.newLineAtOffset(row.x, 792f - row.y); stream.showText(row.text); stream.endText()
            }
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }
    fun contacts(rows: List<Row>, roster: List<PaperAuthor> = authors): List<PdfAuthorContactLayout.Contact> =
        PDDocument.load(bytes(rows)).use { PdfAuthorContactLayout.collect(it, 2, roster, 1) }
            .filter { it.rule != null }
    fun smoke() {
        val rows = listOf(Row("Alice Smith", 300f), Row("alice@sample.edu", 312f),
            Row("Bob Jones", 300f, 340f), Row("bob@sample.edu", 312f, 340f))
        PDDocument.load(bytes(rows)).use { document ->
            val contacts = PdfAuthorContactLayout.collect(document, 2, authors, 1)
            val text = org.apache.pdfbox.text.PDFTextStripper().getText(document)
            val resolved = SourceAuthorEmailResolver.resolvePdf(text, authors, contacts)
            assertEquals(setOf("Alice", "Bob"), resolved.mapNotNull { it.givenNames }.toSet())
            assertEquals(setOf("alice@sample.edu", "bob@sample.edu"), resolved.map { it.email }.toSet())
        }
        assertTrue(contacts(listOf(Row("Alice Smith", 300f), Row("alice@sample.edu", 312f, 340f))).isEmpty())
        val conflictingRows = listOf(Row("Alice Smith", 300f), Row("shared@sample.edu", 312f),
            Row("Bob Jones", 300f, 340f), Row("shared@sample.edu", 312f, 340f))
        PDDocument.load(bytes(conflictingRows)).use { document ->
            val contacts = PdfAuthorContactLayout.collect(document, 2, authors, 1)
            assertEquals(setOf(0, 1), contacts.map { it.authorIndex }.toSet())
            val text = org.apache.pdfbox.text.PDFTextStripper().getText(document)
            val shared = SourceAuthorEmailResolver.resolvePdf(text, authors, contacts).single()
            assertEquals("shared@sample.edu", shared.email)
            assertNull(shared.givenNames)
            assertNull(shared.identityEvidence)
        }
    }

    /** Generated structural fixtures, not a substitute for replaying the private original corpus. */
    fun child03Observations(): List<String> {
        val bernd = PaperAuthor("Bernd", "Bayerlein", null, null, false)
        val bob = PaperAuthor("Bob", "Jones", null, null, false)
        val roster = listOf(bernd, bob)
        val observations = mutableListOf<String>()
        fun observe(label: String, rows: List<Row>, expected: String?) {
            PDDocument.load(bytes(rows)).use { document ->
                val contacts = PdfAuthorContactLayout.collect(document, 2, roster, 1)
                val text = org.apache.pdfbox.text.PDFTextStripper().getText(document)
                val resolved = SourceAuthorEmailResolver.resolvePdf(text, roster, contacts)
                val address = resolved.single { it.email == "bernd@sample.edu" }
                val valid = com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.validEvidence(address.identityEvidence)
                assertEquals(expected, address.givenNames, label)
                assertEquals(expected != null, valid, label)
                observations += "$label email=${address.email} owner=${address.givenNames} ${address.familyNames} validEvidence=$valid contacts=${contacts.size}"
            }
        }
        val header = Row("Bernd Bayerlein* and Bob Jones", 72f, 32f)
        observe("left32Bottom96", listOf(header, Row("Department of Science", 95f, 32f),
            Row("*bernd@sample.edu", 792f * .96f, 32f)), "Bernd")
        observe("inlineAffiliationMarker", listOf(header, Row("Institute of Science. *bernd@sample.edu", 700f, 32f)), "Bernd")
        observe("sameContactNextLine", listOf(header, Row("*Email:", 700f, 32f),
            Row("bernd@sample.edu", 712f, 32f)), "Bernd")
        observe("wrappedContactDomain", listOf(header, Row("*Email: bernd@", 700f, 32f),
            Row("sample.edu", 712f, 32f)), "Bernd")
        observe("sharedMarker", listOf(Row("Bernd Bayerlein* and Bob Jones*", 72f, 32f),
            Row("*bernd@sample.edu", 760f, 32f)), null)
        observe("unknownMarkerOwner", listOf(Row("Bernd Bayerlein* and Unknown Owner*", 72f, 32f),
            Row("*bernd@sample.edu", 760f, 32f)), null)
        observe("damagedSignature", listOf(Row("Bernd Bayerlein* and Bob Jones?", 72f, 32f),
            Row("*bernd@sample.edu", 760f, 32f)), null)
        observe("referenceMailbox", listOf(header, Row("References", 600f, 32f),
            Row("*bernd@sample.edu", 760f, 32f)), null)
        observe("nextAuthorBoundary", listOf(header, Row("*Email:", 700f, 32f),
            Row("Bob Jones is with Another Institute", 712f, 32f), Row("bernd@sample.edu", 724f, 32f)), null)
        observe("unknownNextAuthorBoundary", listOf(header, Row("*Email:", 700f, 32f),
            Row("Unknown Owner", 712f, 32f), Row("bernd@sample.edu", 724f, 32f)), null)
        observe("emptyParagraphBoundary", listOf(header, Row("*Email:", 700f, 32f),
            Row("bernd@sample.edu", 736f, 32f)), null)
        observe("nextMarkerBoundary", listOf(header, Row("*Email:", 700f, 32f),
            Row("†bernd@sample.edu", 712f, 32f)), null)
        return observations
    }
    @JvmStatic fun main(args: Array<String>) { smoke(); println("PURE_PARSER_SMOKE resolved=2 crossColumnRejected=1 conflictingOwnerRejected=1 network=0 businessWrites=0") }
}

/** External original bytes are immutable inputs, not regenerated fixtures. */
internal object ReprocessOriginalInputs {
    private val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    val directory = Paths.get(System.getenv("PAPER_REPROCESS_ORIGINALS")
        ?: "${System.getProperty("user.home")}/.local/share/paper-reprocess-30711")
    fun manifest(): com.fasterxml.jackson.databind.JsonNode {
        assumeTrue(Files.isDirectory(directory), "SKIPPED: original-byte #30711 corpus unavailable")
        val bytes = Files.readAllBytes(Paths.get("docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json"))
        assertEquals("de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c", fixtureSha256(bytes))
        return mapper.readTree(bytes)
    }
    data class Input(val bytes: ByteArray, val metadata: com.fasterxml.jackson.databind.JsonNode,
        val first: com.fasterxml.jackson.databind.JsonNode, val authors: List<PaperAuthor>, val extractor: PdfEmailExtractor)
    fun load(recordId: Long): Input {
        val manifest = manifest()
        val row = manifest.path("records").single { it.path("recordId").asLong() == recordId }
        val snapshotBytes = Files.readAllBytes(directory.resolve(row.path("sourceFile").asText()))
        assertEquals(row.path("sourceSha256").asText(), fixtureSha256(snapshotBytes))
        val snapshot = mapper.readTree(snapshotBytes)
        val metadataRaw = snapshot.path("metadataRaw").asText()
        val outcomeRaw = snapshot.path("outcomeRaw").asText()
        assertEquals(row.path("metadataSha256").asText(), fixtureSha256(metadataRaw.toByteArray()))
        assertEquals(row.path("outcomeSha256").asText(), fixtureSha256(outcomeRaw.toByteArray()))
        val bytes = Files.readAllBytes(directory.resolve(row.path("bodyFile").asText()))
        assertEquals(row.path("bodySha256").asText(), fixtureSha256(bytes))
        val metadata = mapper.readTree(metadataRaw)
        val authors = metadata.path("authors").map { mapper.treeToValue(it, PaperAuthor::class.java) }
        val properties = mapper.treeToValue(manifest.path("parserConfig").path("settings"),
            com.weibo.talentintroduction.config.PdfExtractionProperties::class.java)
        val network = org.springframework.web.client.RestTemplate(org.springframework.http.client.ClientHttpRequestFactory { _, _ ->
            error("ORIGINAL_BYTE_TEST_NETWORK_FORBIDDEN")
        })
        val bounded = org.mockito.Mockito.mock(com.weibo.talentintroduction.config.BoundedHttpExecutor::class.java) {
            error("ORIGINAL_BYTE_TEST_NETWORK_FORBIDDEN")
        }
        return Input(bytes, metadata, mapper.readTree(outcomeRaw).path("first"), authors,
            PdfEmailExtractor(network, PlainTextEmailExtractor(), properties, bounded))
    }
}

private inline fun <reified T : Any> reprocessMock(): T = org.mockito.Mockito.mock(T::class.java)

/** Real queue decoder/consumer and identity gate, with the existing email gate deliberately rejecting.
 * No HTTP, database, enrollment or admission writes are possible in this test. */
internal fun assertReprocessConsumer(emails: List<com.weibo.talentintroduction.discovery.domain.AuthorEmail>,
    metadata: com.fasterxml.jackson.databind.JsonNode) {
    val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    val validator = reprocessMock<com.weibo.talentintroduction.expert.service.EmailValidationService>()
    org.mockito.Mockito.`when`(validator.validate(org.mockito.Mockito.anyString()))
        .thenReturn(com.weibo.talentintroduction.expert.domain.EmailValidationResult(0, false, "TEST_GATE_REJECT"))
    val network = reprocessMock<org.springframework.web.client.RestTemplate>()
    val writer = reprocessMock<com.weibo.talentintroduction.expert.service.ExpertIndexWriterService>()
    val eligibility = reprocessMock<com.weibo.talentintroduction.expert.service.CandidateEligibilityService>()
    val service = ExpertDiscoveryService(reprocessMock(), reprocessMock(), reprocessMock(), reprocessMock(),
        reprocessMock(), reprocessMock(), reprocessMock(), validator, eligibility, writer, reprocessMock(),
        reprocessMock(), reprocessMock(), com.weibo.talentintroduction.expert.service.ExpertClassificationService(), network,
        com.weibo.talentintroduction.config.ElasticsearchProperties("https://offline.invalid", "", "", "raw", "candidate", "application"),
        com.weibo.talentintroduction.config.ExpertDiscoveryProperties(maxAuthorsPerRun = 1000),
        com.weibo.talentintroduction.config.OpenAlexProperties(), mapper, reprocessMock(), reprocessMock(),
        reprocessMock(), reprocessMock(), java.util.concurrent.Executor { it.run() },
        com.weibo.talentintroduction.config.EuropePmcProperties())
    val payload = mapper.writeValueAsString(com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome(
        emails, "PDF_PARSE", identityRuleVersion = 20261004))
    val record = OrcidDataSource.OrcidRecord("0000-0002-1825-0097", "Alice", "Smith", emptyList(), null, null)
    val paperPayload = mapper.writeValueAsString(mapper.copy()
        .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .treeToValue(metadata, com.weibo.talentintroduction.discovery.domain.PaperMetadata::class.java))
    for ((unit, body) in listOf("PAPER" to paperPayload, "RECORD" to mapper.writeValueAsString(record))) {
        val envelope = QueuedItemEnvelope(if (unit == "PAPER") "OPENALEX" else "ORCID", "original-byte-test",
            "DOI", unit, 1, body, body.toByteArray().size.toLong(), true)
        val result = service.consumeQueuedItem(envelope, payload, DiscoveryVerificationExecution(null))
        assertNull(result.unrecoverableReason)
        assertTrue(result.succeeded)
        assertEquals(0, result.indexedExperts)
        assertEquals(0, result.promoted)
    }
    for (email in emails) org.mockito.Mockito.verify(validator, org.mockito.Mockito.times(2)).validate(email.email)
    org.mockito.Mockito.verifyNoInteractions(network, writer, eligibility)
    assertEquals(20260925, com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.VERSION)
    assertEquals(20261004, com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.EXTRACTION_VERSION)
}

/** A real file-only CLI smoke, separate from JUnit and without a Spring/production service graph. */
object PaperReprocessParserSmoke {
    @JvmStatic fun main(args: Array<String>) {
        val input = ReprocessOriginalInputs.load(31375)
        val result = input.extractor.extractEmailsFromBytes(input.bytes, input.authors, null)
        val named = result.filter { !it.givenNames.isNullOrBlank() && !it.familyNames.isNullOrBlank() }
        check(named.any { it.givenNames == "Atia‐tul" && it.familyNames == "Wahab" })
        check(named.all { com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.validEvidence(it.identityEvidence) })
        assertReprocessConsumer(named, input.metadata)
        println("PARSER01_RUNTIME originalRecord=31375 named=${named.size} identityVersion=20260925 extractionVersion=20261004 parserVersion=${PdfAuthorContactLayout.RULE_VERSION} consumer=PAPER,RECORD emailGate=REJECT indexed=0 promoted=0 network=0 businessWrites=0")
    }
}
