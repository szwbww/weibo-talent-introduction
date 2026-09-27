package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.zip.ZipInputStream

internal fun htmlContactEntries(): Map<String, ByteArray> {
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(requireNotNull(SourceAuthorEmailResolverTest::class.java.getResourceAsStream(
        "/discovery/html-contact-recall.zip"))).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            entries[entry.name] = zip.readBytes()
        }
    }
    return entries
}

internal fun htmlContactAuthors(metadata: JsonNode): List<PaperAuthor> = metadata.path("authorships").map {
    val name = it.path("author").path("display_name").asText()
    PaperAuthor(name.substringBeforeLast(' '), name.substringAfterLast(' '),
        it.path("author").path("orcid").asText(null)?.substringAfterLast('/'), null,
        it.path("is_corresponding").asBoolean(),
        openAlexAuthorId = it.path("author").path("id").asText(null)?.substringAfterLast('/'))
}

internal fun fixtureSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun sourceOwnershipCases(): List<JsonNode> = jacksonObjectMapper().readTree(
    requireNotNull(SourceAuthorEmailResolverTest::class.java.getResourceAsStream("/discovery/source-email-ownership-cases.json"))
).toList()

internal fun sourceOwnershipAuthors(case: JsonNode): List<PaperAuthor> = case.path("authors").map {
    PaperAuthor(it.path("givenNames").asText(), it.path("familyNames").asText(), null, null, false)
}

class SourceAuthorEmailResolverTest {
    private val jane = PaperAuthor("Jane", "Doe", "0000-0002-1825-0097", "Jane Lab", true, openAlexAuthorId = "A123")
    private val john = PaperAuthor("John", "Smith", null, "John Lab", false)

    @Test fun `original Springer named mailto anchors bind only unique metadata authors`() {
        val entries = htmlContactEntries()
        val manifest = jacksonObjectMapper().readTree(requireNotNull(entries["manifest.json"]))
        val relations = mutableListOf<Pair<String, String>>()
        for (fixture in manifest.path("fixtures")) {
            val id = fixture.path("id").asText()
            val bytes = requireNotNull(entries[fixture.path("html").path("path").asText()])
            val metadataBytes = requireNotNull(entries[fixture.path("metadata").path("path").asText()])
            assertEquals(fixture.path("html").path("sha256").asText(), fixtureSha256(bytes))
            assertEquals(fixture.path("metadata").path("sha256").asText(), fixtureSha256(metadataBytes))
            if (fixture.path("expectedChallenge").asBoolean()) continue
            val resolved = SourceAuthorEmailResolver.resolveHtml(String(bytes, Charsets.UTF_8),
                htmlContactAuthors(jacksonObjectMapper().readTree(metadataBytes)))
            for (expected in fixture.path("expectedExplicitContacts").fields()) {
                val email = resolved.single { it.email == expected.key }
                assertEquals(expected.value.asText(), "${email.givenNames} ${email.familyNames}", id)
                assertTrue(email.identityEvidence!!.startsWith("SOURCE_SHA256:"), id)
                relations += id to email.email
            }
        }
        assertEquals(3, relations.size)
        assertEquals(2, relations.map { it.second }.toSet().size)
    }

    @Test fun `named mailto requires corresponding region exact visible name and single safe recipient`() {
        fun result(heading: String, anchor: String, authors: List<PaperAuthor> = listOf(jane, john)) =
            SourceAuthorEmailResolver.resolveHtml(
                "<div><h3 id='corresponding-author'>$heading</h3>" +
                    "<p id='corresponding-author-list'>Correspondence to $anchor.</p></div>", authors)
        val link = "<a href='mailto:opaque@uni.edu'>Jane Doe</a>"
        assertEquals("Jane", result("Corresponding author", link).single().givenNames)
        assertTrue(result("Author biographies", link).none { it.givenNames != null })
        assertTrue(result("Corresponding author", "<a href='mailto:opaque@uni.edu' aria-label='Jane Doe'>Contact</a>")
            .none { it.givenNames != null })
        assertTrue(result("Corresponding author", link, listOf(jane, jane.copy(orcidId = "other")))
            .none { it.givenNames != null })
        assertTrue(result("Corresponding author", "<a href='mailto:opaque@uni.edu'>Jane Doe and John Smith</a>")
            .none { it.givenNames != null })
        assertTrue(result("Corresponding author", "<a href='mailto:opaque@uni.edu'>Jane Doe</a>" +
            "<a href='mailto:opaque@uni.edu'>John Smith</a>").none { it.givenNames != null })
        assertEquals("jane+lab@uni.edu", result("Corresponding author",
            "<a href='mailto:jane%2Blab@uni.edu?subject=ignore'>Jane Doe</a>").single().email)
        assertEquals("jane+lab@uni.edu", result("Corresponding author",
            "<a href='mailto:jane+lab@uni.edu'>Jane Doe</a>").single().email)
        val independent = result("Corresponding authors",
            "<a href='mailto:jane@uni.edu'>Jane Doe</a> and <a href='mailto:john@uni.edu'>John Smith</a>")
        assertEquals(mapOf("jane@uni.edu" to "Jane", "john@uni.edu" to "John"),
            independent.associate { it.email to it.givenNames })
        val conflicting = SourceAuthorEmailResolver.resolveHtml("<div><h3 id='corresponding-author'>" +
            "Corresponding author</h3><p id='corresponding-author-list'>" +
            "<a href='mailto:opaque@uni.edu'>Jane Doe</a></p></div><p>John Smith: opaque@uni.edu</p>",
            listOf(jane, john))
        assertNull(conflicting.single { it.email == "opaque@uni.edu" }.givenNames)
        for (href in listOf("mailto:first@uni.edu,second@uni.edu", "mailto:opaque@uni.edu%0d%0abcc:other@uni.edu",
            "mailto:opaque@uni.edu;other@uni.edu")) {
            assertTrue(result("Corresponding author", "<a href='$href'>Jane Doe</a>")
                .none { it.givenNames != null }, href)
        }
        assertTrue(result("Corresponding author", "<a href='mailto:support@uni.edu'>Jane Doe</a>")
            .none { it.givenNames != null })
    }

    @Test fun `PDF contact claims are rejected when they conflict with existing source claims`() {
        val contact = PdfAuthorContactLayout.Contact("opaque@uni.edu", 1, 1,
            "Jane Doe* John Smith", "*Correspondence: opaque@uni.edu")
        val result = SourceAuthorEmailResolver.resolvePdf(
            "Jane Doe: opaque@uni.edu", listOf(jane, john), listOf(contact))
        assertNull(result.single().givenNames)
        assertNull(result.single().identityEvidence)
    }

    @Test fun `layout rejects shared markers duplicate names ambiguous initials and a cross-column mailbox`() {
        val cases = pdfLayoutNegativeCases()
        for ((authors, lines, label) in cases) {
            val bytes = positionedPdf(lines)
            val contacts = org.apache.pdfbox.pdmodel.PDDocument.load(bytes).use {
                PdfAuthorContactLayout.collect(it, 1, authors)
            }
            assertTrue(contacts.isEmpty(), "$label: $contacts")
            val resolved = extractOwnershipContent(bytes, org.springframework.http.MediaType.APPLICATION_PDF, authors)
                .emails.single { it.email == "opaque@uni.edu" }
            assertNull(resolved.givenNames, label)
        }
    }

    @Test fun `one unique PDF contact marker retains two explicit mailboxes`() {
        val pdf = positionedPdf(listOf(
            Triple(50f, 720f, "Jane Doe*"),
            Triple(50f, 600f, "*Email: first@uni.edu; second@uni.edu")))
        val result = extractOwnershipContent(pdf, org.springframework.http.MediaType.APPLICATION_PDF, listOf(jane))
        assertEquals(setOf("first@uni.edu", "second@uni.edu"), result.emails.map { it.email }.toSet())
        assertTrue(result.emails.all { it.givenNames == "Jane" && it.familyNames == "Doe" &&
            it.identityEvidence?.startsWith("SOURCE_SHA256:") == true })
    }

    @Test fun `published source blocks preserve two owned mailboxes and reject shared correspondence`() {
        for (case in sourceOwnershipCases()) {
            val result = SourceAuthorEmailResolver.resolveHtml(case.path("html").asText(), sourceOwnershipAuthors(case))
            assertEquals(case.path("expected").fieldNames().asSequence().toSet(), result.map { it.email }.toSet())
            for (email in result) {
                val expected = case.path("expected").path(email.email)
                if (expected.isNull) {
                    assertNull(email.givenNames, "${case.path("id")}: ${email.email}")
                    assertNull(email.familyNames)
                    assertNull(email.affiliation)
                    assertNull(email.orcidId)
                    assertNull(email.openAlexAuthorId)
                    assertNull(email.identityEvidence)
                } else {
                    assertEquals(expected.asText(), "${email.givenNames} ${email.familyNames}")
                    assertTrue(email.identityEvidence!!.startsWith("SOURCE_SHA256:"))
                }
            }
        }
    }

    @Test fun `bounded contact fields carry one whole identity and multiple emails`() {
        val result = SourceAuthorEmailResolver.resolveText(
            "Contact: Jane Doe (e-mail: r142@uni.edu; r143@uni.edu)\nJohn Smith: r144@uni.edu", listOf(jane, john))
        assertEquals(listOf("Jane", "Jane", "John"), result.map { it.givenNames })
        assertEquals(listOf(jane.orcidId, jane.orcidId, null), result.map { it.orcidId })
        assertEquals(listOf("Jane Lab", "Jane Lab", "John Lab"), result.map { it.affiliation })
        assertEquals(listOf("A123", "A123", null), result.map { it.openAlexAuthorId })
        val wrapped = SourceAuthorEmailResolver.resolveText("Contact: Jane Doe\nEmail: opaque@uni.edu", listOf(jane))
        assertEquals("Jane", wrapped.single().givenNames)
    }

    @Test fun `spelling adjacency and single-author assumptions leave only an email clue`() {
        for (text in listOf(
            "Contact: jane.doe@uni.edu", "Jane Doe\n\nEmail: jane.doe@uni.edu",
            "John Smith\nJane Doe\nEmail: jane.doe@uni.edu",
            "Jane Doe jane.doe@uni.edu", "Jane Doe discusses data from jane.doe@uni.edu",
            "Jane Doe\nThird party contact: jane.doe@uni.edu",
            "Jane Doe and John Smith: shared@uni.edu", "Jane Doe: {jane,john}@uni.edu")) {
            val result = SourceAuthorEmailResolver.resolveText(text, listOf(jane))
            assertTrue(result.isNotEmpty(), text)
            for (email in result) {
                assertNull(email.givenNames, text)
                assertNull(email.orcidId, text)
                assertNull(email.affiliation, text)
                assertNull(email.openAlexAuthorId, text)
            }
        }
    }

    @Test fun `same names within a paper and competing owners of one email are ambiguous`() {
        val duplicateNames = listOf(jane, jane.copy(orcidId = "different", affiliation = "Other Lab"))
        assertNull(SourceAuthorEmailResolver.resolveText("Jane Doe: opaque@uni.edu", duplicateNames).single().givenNames)
        val shared = SourceAuthorEmailResolver.resolveText("Jane Doe: same@uni.edu\nJohn Smith: same@uni.edu", listOf(jane, john))
        assertNull(shared.single().givenNames)
        // Separate papers are not merged by name.
        for ((i, author) in duplicateNames.withIndex()) {
            assertEquals(author.orcidId, SourceAuthorEmailResolver.resolveText(
                "Jane Doe: opaque$i@uni.edu", listOf(author)).single().orcidId)
        }
    }

    @Test fun `html boundaries prevent a name in one paragraph claiming another paragraph mailbox`() {
        val html = "<p>Jane Doe</p><p>Email: opaque@uni.edu</p>"
        assertNull(SourceAuthorEmailResolver.resolveHtml(html, listOf(jane)).single().givenNames)
    }

    @Test fun `empty structured name never carries an author ID`() {
        val html = "<span class='ltx_role_author'><span class='ltx_personname'></span>" +
            "<span class='ltx_contact'>opaque@uni.edu</span></span>"
        val result = SourceAuthorEmailResolver.resolveHtml(html,
            listOf(PaperAuthor(null, null, "orcid-must-not-leak", "Lab", true, openAlexAuthorId = "A123"))).single()
        assertNull(result.givenNames)
        assertNull(result.orcidId)
        assertNull(result.openAlexAuthorId)
        assertNull(result.affiliation)
        assertNull(result.identityEvidence)
    }

    @Test fun `nested or duplicate author nodes never broadcast a contact`() {
        val node = "<span class='ltx_role_author'><span class='ltx_personname'>Jane Doe</span>" +
            "<span class='ltx_contact'>opaque@uni.edu</span></span>"
        assertNull(SourceAuthorEmailResolver.resolveHtml(node, listOf(jane, jane.copy(orcidId = "different"))).single().givenNames)
        val nested = "<span class='ltx_role_author'><span class='ltx_personname'>John Smith</span>$node</span>"
        assertEquals("Jane", SourceAuthorEmailResolver.resolveHtml(nested, listOf(jane, john)).single().givenNames)
    }
    @Test fun `normalizes obfuscated contact identically while hashing original entry`() {
        val original = "Jane Doe: opaque(at)uni.edu"
        val plain = SourceAuthorEmailResolver.resolveText("Jane Doe: opaque@uni.edu", listOf(jane)).single()
        val obfuscated = SourceAuthorEmailResolver.resolveText(original, listOf(jane)).single()
        assertEquals(plain.email, obfuscated.email)
        assertEquals(plain.givenNames, obfuscated.givenNames)
        assertEquals("SOURCE_SHA256:" + com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.hash(original),
            obfuscated.identityEvidence)
        assertNotEquals(plain.identityEvidence, obfuscated.identityEvidence)
    }
    @Test fun `one-line email wrapping claims only its own bounded contact record`() {
        val original = "Jane Doe: opaque@\nuni.edu"
        val wrapped = SourceAuthorEmailResolver.resolveText(original, listOf(jane)).single()
        assertEquals("opaque@uni.edu", wrapped.email)
        assertEquals("Jane", wrapped.givenNames)
        assertEquals("SOURCE_SHA256:" + com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.hash(original),
            wrapped.identityEvidence)
        val splitParagraph = SourceAuthorEmailResolver.resolveText("Jane Doe: opaque@\n\nuni.edu", listOf(jane))
        assertTrue(splitParagraph.isEmpty())
    }

    @Test fun `brace and wrapped source emails remain unbound without explicit same record owner`() {
        val fixture = jacksonObjectMapper().readTree(
            requireNotNull(javaClass.getResourceAsStream("/discovery/email-text-recall.json")).use { it.readBytes() })
        val brace = fixture.path("sourceBrace").path("cases").first().path("text").asText()
        val braceResults = SourceAuthorEmailResolver.resolveText(brace, listOf(jane))
        assertEquals(6, braceResults.size)
        assertTrue(braceResults.all { it.givenNames == null && it.familyNames == null && it.identityEvidence == null })
        val wrapped = SourceAuthorEmailResolver.resolveText(fixture.path("wrappedSource").path("text").asText(), listOf(jane))
        assertTrue(wrapped.any { it.email == "kairouz@google.com" && it.givenNames == null && it.identityEvidence == null })
    }
}

/** Real PDF/HTML parsing with an HTTP response fixture; only the transport is replaced. */
internal fun extractOwnershipContent(
    content: ByteArray,
    mediaType: org.springframework.http.MediaType,
    authors: List<PaperAuthor>
): com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome {
    val template = org.springframework.web.client.RestTemplate()
    val server = org.springframework.test.web.client.MockRestServiceServer.bindTo(template).build()
    server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://paper.test/content"))
        .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(content, mediaType))
    val http = object : com.weibo.talentintroduction.config.BoundedHttpExecutor {
        override fun <T : Any> getForObject(base: org.springframework.web.client.RestTemplate, url: String,
            responseType: Class<T>, connectCapMs: Long, readCapMs: Long, deadline: java.time.Instant?): T? =
            base.getForObject(url, responseType)
        override fun <T> execute(base: org.springframework.web.client.RestTemplate, uri: java.net.URI,
            connectCapMs: Long, readCapMs: Long, deadline: java.time.Instant?,
            responseExtractor: org.springframework.web.client.ResponseExtractor<T>): T? =
            base.execute(uri, org.springframework.http.HttpMethod.GET, null, responseExtractor)
    }
    val result = PdfEmailExtractor(template, PlainTextEmailExtractor(),
        com.weibo.talentintroduction.config.PdfExtractionProperties(), http)
        .extract("https://paper.test/content", authors, "TEST")
    server.verify()
    return result
}

/** A synthetic PDF with explicit contact text, not a claimed copy of a published paper. */
internal fun ownershipPdf(vararg lines: String): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
        val page = org.apache.pdfbox.pdmodel.PDPage()
        doc.addPage(page)
        org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { stream ->
            stream.beginText()
            stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11f)
            stream.newLineAtOffset(40f, 720f)
            for (line in lines) { stream.showText(line); stream.newLineAtOffset(0f, -16f) }
            stream.endText()
        }
        doc.save(output)
    }
    return output.toByteArray()
}

/** Synthetic coordinate controls; the archived published PDFs remain byte-for-byte unchanged. */
internal fun positionedPdf(lines: List<Triple<Float, Float, String>>): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
        val page = org.apache.pdfbox.pdmodel.PDPage()
        doc.addPage(page)
        org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { stream ->
            stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11f)
            for ((x, y, text) in lines) {
                stream.beginText()
                stream.newLineAtOffset(x, y)
                stream.showText(text)
                stream.endText()
            }
        }
        doc.save(output)
    }
    return output.toByteArray()
}

/** Each control has the same PDFBox path, but is expressly synthetic rather than a source-paper claim. */
internal fun pdfLayoutNegativeCases(): List<Triple<List<PaperAuthor>, List<Triple<Float, Float, String>>, String>> {
    val jane = PaperAuthor("Jane", "Doe", "0000-0002-1825-0097", "Jane Lab", true, openAlexAuthorId = "A123")
    val john = PaperAuthor("John", "Smith", null, "John Lab", false)
    return listOf(
        Triple(listOf(jane, john), listOf(
            Triple(50f, 720f, "Jane Doe* John Smith*"), Triple(50f, 600f, "*Email: opaque@uni.edu")), "shared marker"),
        Triple(listOf(jane, jane.copy(orcidId = "other")), listOf(
            Triple(50f, 720f, "Jane Doe*"), Triple(50f, 600f, "*Email: opaque@uni.edu")), "duplicate name"),
        Triple(listOf(PaperAuthor("Shirui", "Pan", null, null), PaperAuthor("Samantha", "Pan", null, null)),
            listOf(Triple(50f, 720f, "Shirui Pan Samantha Pan"),
                Triple(50f, 220f, "S. Pan is with University (Email: opaque@uni.edu).")), "ambiguous initials"),
        Triple(listOf(jane), listOf(
            Triple(50f, 720f, "Jane Doe*"), Triple(350f, 600f, "*Email: opaque@uni.edu")), "cross-column"),
        Triple(listOf(jane), listOf(
            Triple(50f, 720f, "Jane Doe"), Triple(50f, 220f, "References: Jane Doe Email: opaque@uni.edu")), "references")
    )
}
