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
