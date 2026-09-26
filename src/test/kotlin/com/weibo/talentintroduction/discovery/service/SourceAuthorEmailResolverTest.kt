package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal fun sourceOwnershipCases(): List<JsonNode> = jacksonObjectMapper().readTree(
    requireNotNull(SourceAuthorEmailResolverTest::class.java.getResourceAsStream("/discovery/source-email-ownership-cases.json"))
).toList()

internal fun sourceOwnershipAuthors(case: JsonNode): List<PaperAuthor> = case.path("authors").map {
    PaperAuthor(it.path("givenNames").asText(), it.path("familyNames").asText(), null, null, false)
}

class SourceAuthorEmailResolverTest {
    private val jane = PaperAuthor("Jane", "Doe", "0000-0002-1825-0097", "Jane Lab", true, openAlexAuthorId = "A123")
    private val john = PaperAuthor("John", "Smith", null, "John Lab", false)

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
