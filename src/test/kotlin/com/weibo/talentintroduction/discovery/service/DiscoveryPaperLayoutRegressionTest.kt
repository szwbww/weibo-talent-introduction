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

    @Test fun `private original corpus replay explicitly skips when unavailable`() {
        val root = System.getenv("DISCOVERY_LAYOUT_FROZEN_SAMPLES")
        assumeTrue(!root.isNullOrBlank(), "SKIPPED: exact original294 frozen raw corpus unavailable; no real-effect claim")
        val output = System.getenv("DISCOVERY_LAYOUT_REPLAY_OUTPUT")
        require(!output.isNullOrBlank()) { "Private output path required" }
        require(Files.isDirectory(Paths.get(root!!)))
        assertEquals(0, DiscoverySampleReplay.run(arrayOf("replay", "--samples", root, "--output", output!!)))
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
    @JvmStatic fun main(args: Array<String>) { smoke(); println("PURE_PARSER_SMOKE resolved=2 crossColumnRejected=1 conflictingOwnerRejected=1 network=0 businessWrites=0") }
}
