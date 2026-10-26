package com.weibo.talentintroduction.discovery.archive

import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate

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
        assertEquals(PaperQualificationKind.PROFESSOR, text("Alice Smith is a Professor.").facts.single().kind)
        assertTrue(text("Biographies\nAlice Smith is a Professor.",listOf(QualificationFixtures.authors[0],QualificationFixtures.authors[0])).facts.isEmpty())
        assertEquals(PaperEvidenceStatus.NOT_FOUND,text("a scanned image without text").coverage.status)
    }
    @Test fun `independent full name heading admits continuous named statement`() {
        assertEquals(PaperQualificationKind.PROFESSOR,text("Alice Smith\nAlice Smith is a Professor.").facts.single().kind)
    }
    @Test fun `negative and future assertions never claim completion while pursuing remains student`() {
        listOf("Alice Smith has not received her PhD.", "Alice Smith received her PhD, expected in 2027.").forEach {
            assertTrue(text("Biographies\n$it").facts.isEmpty())
        }
        listOf("Alice Smith is pursuing a doctorate.", "Alice Smith is working toward a PhD.").forEach {
            assertEquals(PaperQualificationKind.DOCTORAL_STUDENT, text("Biographies\n$it").facts.single().kind)
        }
        val separate = text("Biographies\nAlice Smith received her PhD. Alice Smith is a doctoral student.")
        assertEquals(setOf(PaperQualificationKind.COMPLETED_DOCTORATE, PaperQualificationKind.DOCTORAL_STUDENT), separate.facts.map { it.kind }.toSet())
    }
    @Test fun `received doctoral candidate or student status is not a completed degree`() {
        listOf("PhD candidate status", "Ph.D. candidate status", "PhD candidacy", "PhD student status",
            "doctorate candidate status").forEach { status ->
            val result = text("Biographies\nAlice Smith received her $status in 2023.")
            assertTrue(result.facts.isEmpty(),status)
            assertEquals(PaperEvidenceStatus.NOT_FOUND,result.coverage.status,status)
        }
        listOf("PhD", "Ph.D.", "PhD degree", "doctorate").forEach { degree ->
            val result = text("Biographies\nAlice Smith received her $degree in 2023.")
            assertEquals(PaperQualificationKind.COMPLETED_DOCTORATE,result.facts.single().kind,degree)
        }
    }
    @Test fun `safe HTML XML and external entity suppression preserve paragraph semantics`() {
        assertEquals(PaperQualificationKind.PROFESSOR,text("<html><body><h2>Biographies</h2><script>Alice Smith is a PhD student.</script><p>Alice Smith is a Professor.</p></body></html>",format="HTML").facts.single().kind)
        assertEquals(PaperQualificationKind.COMPLETED_DOCTORATE,text("<article><title>Biographies</title><p>Alice Smith earned her doctorate.</p></article>",format="XML").facts.single().kind)
        val xml = "<!DOCTYPE article SYSTEM 'http://127.0.0.1:1/never.dtd'><article><title>Biographies</title><p>Alice Smith earned her doctorate.</p></article>"
        assertEquals(PaperQualificationKind.COMPLETED_DOCTORATE,text(xml,format="XML").facts.single().kind)
        val secret = java.nio.file.Files.createTempFile("paper-xxe", ".txt")
        try {
            java.nio.file.Files.writeString(secret, "SECRET_QUALIFICATION")
            val unsafe = "<!DOCTYPE article [<!ENTITY x SYSTEM '${secret.toUri()}'>]><article>&x;<p>safe</p></article>"
            assertFalse(extractor.safeText(unsafe.toByteArray(), "XML").contains("SECRET_QUALIFICATION"))
        } finally { java.nio.file.Files.deleteIfExists(secret) }
        assertEquals(PaperEvidenceStatus.ERROR,text("<!DOCTYPE article><article>",format="XML").coverage.status)
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

    @Test fun `thirteen reviewed person structures preserve grammar without private identities`() {
        // Q01..Q13 map in reviewed positive-person order; only anonymized structural excerpts are retained.
        val fixtures = listOf(
            Triple("Q01", "Assoc. Prof. Alice Smith was born in Example city in 23 July, 1976. Received the B.Sc., M.Sc., and Ph.D. scientific degrees in engineering. She is a lecturer.", setOf(PaperQualificationKind.BIRTH_DATE, PaperQualificationKind.COMPLETED_DOCTORATE)),
            Triple("Q02", "Alice Smith is a chief scientist. Smith received a dr.philos. in computer science.", setOf(PaperQualificationKind.COMPLETED_DOCTORATE)),
            Triple("Q03", "Alice Smith received the Ph.D. degree in computing. She is currently a Postdoctoral Researcher at Example University, and an Assistant Professor with another school.", setOf(PaperQualificationKind.COMPLETED_DOCTORATE, PaperQualificationKind.ASSISTANT_PROFESSOR)),
            Triple("Q04", "Dr. Alice Smith is a distinguished professional. She earned her Doctor of Philosophy in Wireless in 2019. Currently holding the esteemed position of Professor and Director at Example Institute, she teaches.", setOf(PaperQualificationKind.COMPLETED_DOCTORATE, PaperQualificationKind.PROFESSOR)),
            Triple("Q05", "Alice Smith received her Bachelor of Engineering and Master of Technology degrees. She has more than 15 years of experience and currently working as an Assistant Professor in engineering.", setOf(PaperQualificationKind.ASSISTANT_PROFESSOR)),
            Triple("Q06", "Dr. Alice Smith holds a Ph.D. in Computer Science and Engineering.", setOf(PaperQualificationKind.COMPLETED_DOCTORATE)),
            Triple("Q07", "Alice Smith is an Associate Professor at Example University. She received her PhD in 2014; MSc in security.", setOf(PaperQualificationKind.COMPLETED_DOCTORATE, PaperQualificationKind.ASSOCIATE_PROFESSOR)),
            Triple("Q08", "Alice Smith is the head of a research group and an Ass.Prof. at Example University. She was awarded her Ph.D. by Example University.", setOf(PaperQualificationKind.COMPLETED_DOCTORATE)),
            Triple("Q09", "Alice Smith (M’12–SM’22) received the engineering degree in telecommunications in 2007 and the Ph.D. degree in electrical engineering in 2015.", setOf(PaperQualificationKind.COMPLETED_DOCTORATE)),
            Triple("Q10", "Alice Smith is an Assistant Professor in marketing at Example University.", setOf(PaperQualificationKind.ASSISTANT_PROFESSOR)),
            Triple("Q11", "Alice Smith is an associate professor at the Institute of Education.", setOf(PaperQualificationKind.ASSOCIATE_PROFESSOR)),
            Triple("Q12", "Alice Smith is an associate professor at Example University, Australia.", setOf(PaperQualificationKind.ASSOCIATE_PROFESSOR)),
            Triple("Q13", "Alice Smith received the B.Sc. degree in Automation in 2013, and the Ph.D. degree in Electrical Engineering in 2019. From 2020 to 2024, she was a Postdoctoral Researcher. Since 2026 she has been Associate Professor at Example University.", setOf(PaperQualificationKind.COMPLETED_DOCTORATE, PaperQualificationKind.ASSOCIATE_PROFESSOR))
        )
        assertEquals(13, fixtures.size)
        fixtures.forEach { (id, body, kinds) ->
            val year = listOf(2020, 2021, 2022, 2025, 2025, 2025, 2025, 2022, 2022, 2022, 2023, 2023, 2026)[id.drop(1).toInt() - 1]
            val result = extractor.extract(body.toByteArray(), "TEXT", QualificationFixtures.authors, year,
                DiscoveryPaperArchiveService.sha(body.toByteArray()))
            assertEquals(kinds, result.facts.map { it.kind }.toSet(), id)
            result.facts.forEach {
                assertEquals(0, it.authorIndex, id); assertEquals("Alice Smith", it.authorName, id)
                assertEquals("PUBLICATION_TIME", it.temporalScope, id)
                assertEquals(year, it.paperYear, id); assertTrue(body.contains(it.quote.trim()), id)
                assertEquals(DiscoveryPaperArchiveService.sha(body.toByteArray()), it.bodySha256, id)
            }
        }
    }

    @Test fun `four audited adjacent and illustrative structures cannot supply author qualifications`() {
        val fixtures = listOf(
            "Alice Smith works in a laboratory under Dr. Unknown Mentor. Nora Example is a Ph.D. student. She received her PhD.",
            "Alice Smith is with Example University. Example Question: Nora Example was born on 1976-07-23. She received her PhD.",
            "Alice Smith is the corresponding author. Prompt How old was Nora Example? Nora Example was born in 1954.",
            "Alice Smith is with another university. LLM Response Nora Example was born on 1976-07-23. She is a Professor."
        )
        fixtures.forEachIndexed { i, body -> assertTrue(text(body).facts.isEmpty(), "N0${i + 1}") }
        assertTrue(text("Alice Smith is a researcher.\n\nShe received her PhD.").facts.isEmpty())
        assertTrue(text("Alice Smith works in a laboratory.\nReferences\nShe received her PhD.").facts.isEmpty())
        assertTrue(text("Alice Smith works in a laboratory. Nora Example is a researcher. She received her PhD.").facts.isEmpty())
        assertTrue(text("Alice Smith works in a laboratory. N. Example is a researcher. She received her PhD.").facts.isEmpty())
        assertTrue(text("Alice Smith works in a laboratory.\nFUNDING\nShe works in research. She received her PhD.").facts.isEmpty())
    }

    @Test fun `completed degrees student status negation and conflicting titles are assertion scoped`() {
        val separate = text("Alice Smith holds a PhD. She is a doctoral student. She has not received a second doctorate.")
        assertEquals(setOf(PaperQualificationKind.COMPLETED_DOCTORATE, PaperQualificationKind.DOCTORAL_STUDENT), separate.facts.map { it.kind }.toSet())
        val student = text("Alice Smith received her B.Sc. and M.Sc. degrees. She currently pursues her Ph.D. degree.")
        assertEquals(PaperQualificationKind.DOCTORAL_STUDENT, student.facts.single().kind)
        listOf("She has not received her PhD.", "She received her PhD, expected in 2027.",
            "She is an assistant to Professor.", "She is a retired Professor.", "She is an emeritus Professor.").forEach {
            assertTrue(text("Alice Smith is a researcher. $it").facts.isEmpty(), it)
        }
        val conflict = text("Alice Smith is an Assistant Professor. She is an Associate Professor. She holds a PhD.")
        assertEquals(2, conflict.facts.count { it.kind == PaperQualificationKind.CONFLICT })
        assertEquals(1, conflict.facts.count { it.kind == PaperQualificationKind.COMPLETED_DOCTORATE })
    }

    @Test fun `birth requires explicit valid complete date bounded by publication not graduation`() {
        fun born(date: String, year: Int? = 2023, publication: LocalDate? = null) =
            extractor.extract("Alice Smith was born on $date.".toByteArray(), "TEXT", QualificationFixtures.authors,
                year, "hash", publication).facts
        listOf("1976-07-23", "23 July 1976", "July 23, 1976").forEach {
            assertEquals("1976-07-23", born(it).single().originalTerm, it)
        }
        listOf("1976", "2023-02-29", "2024-01-01", "23 Foo 1976", "0000-01-01").forEach {
            assertTrue(born(it).isEmpty(), it)
        }
        assertTrue(born("1976-07-23", null).isEmpty())
        assertEquals(1, born("2023-12-31").size)
        assertEquals(1, born("2023-04-01", publication = LocalDate.parse("2023-04-01")).size)
        assertTrue(born("2023-04-02", publication = LocalDate.parse("2023-04-01")).isEmpty())
        assertFalse(text("Alice Smith received her PhD in 2019.").facts.any { it.kind == PaperQualificationKind.BIRTH_DATE })
        assertEquals(3, PaperQualificationEvidenceExtractor.RULE_VERSION)
        assertEquals(PaperQualificationEvidenceExtractor.RULE_VERSION, PaperQualificationEvidenceExtractor.VERSION)
    }

    @Test fun `prior qualification not found cache is reanalysed without changing original capture`() {
        ArchiveMysqlFixture.clean()
        ArchiveFixture().use { f ->
            f.enable()
            val a = AnalysisFixture(f)
            val paper = a.paper("qualification-rule-upgrade")
            val body = a.body(paper, "Alice Smith (M’12) holds a PhD.")
            a.baseline(paper, listOf(body.id))
            val first = f.repository.record(paper.id)!!.outcomeJson
            val result = a.post(paper)
            val stale = result.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
            (stale.path("parserVersion") as com.fasterxml.jackson.databind.node.ObjectNode).put("qualification", 2)
            stale.set<com.fasterxml.jackson.databind.JsonNode>("qualificationFacts", f.mapper.createArrayNode())
            (stale.path("coverage") as com.fasterxml.jackson.databind.node.ObjectNode).put("status", "NOT_FOUND")
            f.service.saveAnalysis(paper.id, f.repository.record(paper.id)!!.revision, stale)
            var parsed = false
            a.analysis.beforeParse = { parsed = true }
            val refreshed = a.post(paper)
            assertTrue(parsed)
            assertEquals(3, refreshed.path("parserVersion").path("qualification").asInt())
            assertEquals("COMPLETED_DOCTORATE", refreshed.path("qualificationFacts").single().path("kind").asText())
            assertEquals(first, f.repository.record(paper.id)!!.outcomeJson)
            org.mockito.Mockito.verifyNoInteractions(a.network, a.bounded)
        }
    }

    @Test fun `wrapped long biography keeps the actual assertion inside its bounded literal quote`() {
        val body = "Alice Smith\nShe works on " + "research ".repeat(160) + ". She received her\nPh.D. in 2019."
        val fact = text(body).facts.single()
        assertEquals(PaperQualificationKind.COMPLETED_DOCTORATE, fact.kind)
        assertTrue(fact.quote.contains("received her\nPh.D."))
        assertTrue(body.contains(fact.quote))
    }
}
