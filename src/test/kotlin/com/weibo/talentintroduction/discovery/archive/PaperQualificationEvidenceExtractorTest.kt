package com.weibo.talentintroduction.discovery.archive

import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

internal object QualificationFixtures {
    val authors = listOf("Alice Smith", "Bob Jones", "Carol White").map { PaperAuthor(it.substringBefore(' '), it.substringAfter(' '), null, null) }
    val biography = "Biographies\n\nAlice Smith is an Assistant Professor.\n\nBob Jones received his PhD in 2023.\n\nCarol White is a PhD student.\n\nAcknowledgements\nAlice Smith thanks Professor Bob Jones."
    fun pdf(pages: Int = 1, lines: List<String> = biography.lines()): ByteArray = PDDocument().use { doc ->
        repeat(pages) {
            val page = PDPage(); doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 10f); stream.newLineAtOffset(40f, 740f)
                lines.forEach { line -> stream.showText(line); stream.newLineAtOffset(0f, -14f) }; stream.endText()
            }
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }
}

class PaperQualificationEvidenceExtractorTest {
    private val extractor = PaperQualificationEvidenceExtractor()
    private fun text(value: String, authors: List<PaperAuthor> = QualificationFixtures.authors, format: String = "TEXT") =
        value.toByteArray().let { extractor.extract(it, format, authors, 2023, DiscoveryPaperArchiveService.sha(it)) }
    @Test fun `three named historical facts retain exact source locator hash and year`() {
        val result = text(QualificationFixtures.biography)
        assertEquals(listOf(PaperQualificationKind.ASSISTANT_PROFESSOR, PaperQualificationKind.COMPLETED_DOCTORATE, PaperQualificationKind.DOCTORAL_STUDENT),result.facts.map { it.kind })
        assertEquals(PaperEvidenceStatus.FOUND,result.coverage.status)
        result.facts.forEach { fact ->
            assertNull(fact.page); assertTrue(fact.paragraph > 0); assertEquals(2023,fact.paperYear)
            assertEquals("PUBLICATION_TIME",fact.temporalScope); assertTrue(QualificationFixtures.biography.contains(fact.quote.trim()))
            assertEquals(DiscoveryPaperArchiveService.sha(QualificationFixtures.biography.toByteArray()),fact.bodySha256)
        }
    }
    @Test fun `longest title wins while associate and professor remain separate`() {
        val result = text("Biographies\nAlice Smith is an Assistant Professor.\n\nBob Jones was an Associate Professor.\n\nCarol White is a Professor.")
        assertEquals(listOf(PaperQualificationKind.ASSISTANT_PROFESSOR, PaperQualificationKind.ASSOCIATE_PROFESSOR, PaperQualificationKind.PROFESSOR),result.facts.map { it.kind })
    }
    @Test fun `ambiguous other author mentor references pronouns and duplicate names are rejected`() {
        listOf("Alice Smith works with Professor Bob Jones.", "Alice Smith is supervised by Professor Unknown Person.",
            "Alice Smith and Bob Jones are professors.", "He is a Professor.", "Smith is a Professor.",
            "Alice Smith is an Assistant Professor and Bob Jones is a Professor.").forEach { body ->
            assertTrue(text("Biographies\n$body").facts.isEmpty(),body)
        }
        assertTrue(text("References\nAlice Smith is a Professor.").facts.isEmpty())
        assertTrue(text("Alice Smith is a Professor.").facts.isEmpty())
        assertTrue(text("Biographies\nAlice Smith is a Professor.",listOf(QualificationFixtures.authors[0],QualificationFixtures.authors[0])).facts.isEmpty())
        assertEquals(PaperEvidenceStatus.NOT_FOUND,text("a scanned image without text").coverage.status)
    }
    @Test fun `independent full name heading admits continuous named statement`() {
        assertEquals(PaperQualificationKind.PROFESSOR,text("Alice Smith\nAlice Smith is a Professor.").facts.single().kind)
    }
    @Test fun `negative future and conflicting doctorate never claim completion`() {
        listOf("Alice Smith has not received her PhD.", "Alice Smith received her PhD, expected in 2027.",
            "Alice Smith is pursuing a doctorate.", "Alice Smith is working toward a PhD.").forEach {
            assertTrue(text("Biographies\n$it").facts.isEmpty())
        }
        val conflict = text("Biographies\nAlice Smith received her PhD. Alice Smith is a doctoral student.")
        assertTrue(conflict.facts.isNotEmpty()); assertTrue(conflict.facts.all { it.kind == PaperQualificationKind.CONFLICT })
    }
    @Test fun `safe HTML XML and DTD rejection preserve paragraph semantics`() {
        assertEquals(PaperQualificationKind.PROFESSOR,text("<html><body><h2>Biographies</h2><script>Alice Smith is a PhD student.</script><p>Alice Smith is a Professor.</p></body></html>",format="HTML").facts.single().kind)
        assertEquals(PaperQualificationKind.COMPLETED_DOCTORATE,text("<article><title>Biographies</title><p>Alice Smith earned her doctorate.</p></article>",format="XML").facts.single().kind)
        assertEquals(PaperEvidenceStatus.ERROR,text("<!DOCTYPE article [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><article>&x;</article>",format="XML").coverage.status)
    }
    @Test fun `actual PDF source has genuine pages while bounded scans remain partial`() {
        val bytes = QualificationFixtures.pdf()
        val result = extractor.extract(bytes,"PDF",QualificationFixtures.authors,2023,DiscoveryPaperArchiveService.sha(bytes))
        assertEquals(3,result.facts.size); assertTrue(result.facts.all { it.page == 1 })
        val limit = extractor.extract(QualificationFixtures.pdf(101,emptyList()),"PDF",QualificationFixtures.authors,2023,"hash")
        assertEquals(100,limit.coverage.scannedPages); assertEquals(101,limit.coverage.totalPages)
        assertTrue("PAGE_LIMIT" in limit.coverage.reasons); assertEquals(PaperEvidenceStatus.PARTIAL,limit.coverage.status)
        assertEquals(PaperEvidenceStatus.ERROR,extractor.extract("broken".toByteArray(),"PDF",emptyList(),2023,"hash").coverage.status)
    }
    @Test fun `text fact and soft deadline limits are explicit`() {
        val oversized = text("Biographies\n" + "x".repeat(PaperQualificationEvidenceExtractor.MAX_TEXT_BYTES + 1))
        assertEquals(PaperEvidenceStatus.PARTIAL,oversized.coverage.status); assertTrue("TEXT_LIMIT" in oversized.coverage.reasons)
        val tooMany = text("Biographies\n" + (1..101).joinToString("\n\n") { "Alice Smith is a Professor at institution $it." })
        assertEquals(100,tooMany.facts.size); assertTrue("FACT_LIMIT" in tooMany.coverage.reasons)
        var now = 0L; extractor.nanoTime = { now.also { now += PaperQualificationEvidenceExtractor.SOFT_NANOS } }
        val timed = extractor.extract(QualificationFixtures.pdf(),"PDF",QualificationFixtures.authors,2023,"hash")
        assertEquals(0,timed.coverage.scannedPages); assertTrue("SOFT_TIME_LIMIT" in timed.coverage.reasons)
    }
}
