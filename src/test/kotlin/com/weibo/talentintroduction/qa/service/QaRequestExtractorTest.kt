package com.weibo.talentintroduction.qa.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QaRequestExtractorTest {

    private val prachetaMail = """
        Thank you for your message. Here are my research profiles:
        https://scholar.google.com/citations?user=OT-O6joAAAAJ&hl=en
        https://www.scopus.com/authid/detail.uri?authorId=57201234567

        Based on my research profile and
        areas of expertise, could you confirm whether my background fits the
        enterprise projects your team manages?

        Specifically:
        - What is the full name and registered location of your company?
        - What is the programme purpose and structure?
        - How are researchers selected and matched with enterprise projects?
        - What are the expected responsibilities and deliverables?
        - What are the contractual, financial and IP arrangements?
        - What are the next stages?

        Best regards
    """.trimIndent()

    @Test
    fun `pracheta mail yields 7 source-ordered requests with cross-line research question first`() {
        val items = QaRequestExtractor.extract(prachetaMail)

        assertEquals(7, items.size, items.map { it.text }.toString())
        val research = items[0].text
        assertTrue(research.contains("Based on my research profile"), research)
        assertTrue(research.contains("areas of expertise"), research)
        assertTrue(research.contains("enterprise projects your team manages?"), research)
        assertFalse(research.contains("\n"), "soft newlines must fold: $research")

        assertTrue(items[1].text.contains("full name and registered location", ignoreCase = true))
        assertTrue(items[2].text.contains("programme purpose and structure", ignoreCase = true))
        assertTrue(items[3].text.contains("selected and matched", ignoreCase = true))
        assertTrue(items[4].text.contains("responsibilities and deliverables", ignoreCase = true))
        assertTrue(items[5].text.contains("contractual, financial and IP", ignoreCase = true))
        assertTrue(items[6].text.contains("next stages", ignoreCase = true))

        assertTrue(items.none { it.text.contains("com/citations?", ignoreCase = true) })
        assertTrue(items.none { it.text.contains("detail.uri?", ignoreCase = true) })
        assertTrue(items.none { it.text.contains("authorId", ignoreCase = true) })
    }

    @Test
    fun `question before bullet still sorts by source offset`() {
        val body = """
            What funding is available?
            - What is the deadline?
        """.trimIndent()

        val items = QaRequestExtractor.extract(body)
        assertEquals(2, items.size)
        assertTrue(items[0].text.contains("funding", ignoreCase = true))
        assertTrue(items[1].text.contains("deadline", ignoreCase = true))
        assertTrue(items[0].startOffset < items[1].startOffset)
    }

    @Test
    fun `bullet before question still sorts by source offset`() {
        val body = """
            - What is the deadline?
            What funding is available?
        """.trimIndent()

        val items = QaRequestExtractor.extract(body)
        assertEquals(2, items.size)
        assertTrue(items[0].text.contains("deadline", ignoreCase = true))
        assertTrue(items[1].text.contains("funding", ignoreCase = true))
    }

    @Test
    fun `overlapping bullet question keeps bullet once`() {
        val body = "- What is funding?"
        val items = QaRequestExtractor.extract(body)
        assertEquals(1, items.size)
        assertEquals(QaRequestExtractor.Kind.BULLET, items[0].kind)
        assertTrue(items[0].text.contains("funding", ignoreCase = true))
    }

    @Test
    fun `duplicate normalized text keeps first occurrence only`() {
        val body = """
            What is the funding amount?

            What is the funding amount?
        """.trimIndent()

        val items = QaRequestExtractor.extract(body)
        assertEquals(1, items.size)
        assertTrue(items[0].text.contains("funding amount", ignoreCase = true))
    }

    @Test
    fun `scholar and scopus URL-only body yields zero`() {
        val body = """
            https://scholar.google.com/citations?user=OT-O6joAAAAJ&hl=en
            https://www.scopus.com/authid/detail.uri?authorId=57201234567
        """.trimIndent()
        assertTrue(QaRequestExtractor.extract(body).isEmpty())
    }

    @Test
    fun `text plus URL bullet kept once with URL preserved`() {
        val body = "- Please review https://scholar.google.com/citations?user=OT-O6joAAAAJ&hl=en for my publications"
        val items = QaRequestExtractor.extract(body)
        assertEquals(1, items.size)
        assertTrue(items[0].text.contains("Please review", ignoreCase = true))
        assertTrue(items[0].text.contains("scholar.google.com", ignoreCase = true))
    }

    @Test
    fun `two questions on same line are two units`() {
        val items = QaRequestExtractor.extract("What funding is available? Can we arrange a meeting?")
        assertEquals(2, items.size)
        assertTrue(items[0].text.contains("funding", ignoreCase = true))
        assertTrue(items[1].text.contains("meeting", ignoreCase = true))
    }

    @Test
    fun `questions do not cross blank paragraph boundary`() {
        val body = """
            What is funding

            available?
        """.trimIndent()
        val items = QaRequestExtractor.extract(body)
        // incomplete first paragraph has no ?; second paragraph "?"" alone or "available?" is one unit
        assertEquals(1, items.size)
        assertTrue(items[0].text.contains("available?", ignoreCase = true))
        assertFalse(items[0].text.contains("What is funding", ignoreCase = true))
    }

    @Test
    fun `plain body without question or bullet falls back to one unit`() {
        val body = "Thank you for reaching out. I am interested in this opportunity."
        val items = QaRequestExtractor.extract(body)
        assertEquals(1, items.size)
        assertEquals(QaRequestExtractor.Kind.FALLBACK, items[0].kind)
        assertEquals(body, items[0].text)
    }

    @Test
    fun `blank body yields zero`() {
        assertTrue(QaRequestExtractor.extract("   ").isEmpty())
        assertTrue(QaRequestExtractor.extract("").isEmpty())
    }

    @Test
    fun `url-only bullet yields zero`() {
        val items = QaRequestExtractor.extract(
            "- https://www.scopus.com/authid/detail.uri?authorId=57201234567"
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun `CRLF body offsets map to original messageBody slices`() {
        val body = listOf(
            "Thank you for your message.",
            "",
            "Based on my research profile and",
            "areas of expertise, could you confirm whether my background fits the",
            "enterprise projects your team manages?",
            "",
            "Specifically:",
            "- What is the full name and registered location of your company?",
            "- What are the next stages?",
            "",
            "https://scholar.google.com/citations?user=OT-O6joAAAAJ&hl=en"
        ).joinToString("\r\n")

        val items = QaRequestExtractor.extract(body)
        assertEquals(3, items.size, items.map { it.text }.toString())

        for (item in items) {
            assertTrue(item.startOffset in 0 until item.endOffset, "bad range: $item")
            assertTrue(item.endOffset <= body.length, "end past body: $item")
            val slice = body.substring(item.startOffset, item.endOffset)
            assertEquals(foldLike(slice), item.text, "offset must slice original body")
        }

        assertTrue(items[0].text.contains("Based on my research profile"))
        assertTrue(items[0].text.contains("enterprise projects your team manages?"))
        assertFalse(items[0].text.contains("\n"))
        assertFalse(items[0].text.contains("\r"))
        assertTrue(items[1].text.contains("registered location", ignoreCase = true))
        assertTrue(items[2].text.contains("next stages", ignoreCase = true))
        assertTrue(items.none { it.text.contains("com/citations?", ignoreCase = true) })
        assertTrue(items[0].startOffset < items[1].startOffset)
        assertTrue(items[1].startOffset < items[2].startOffset)
    }

    @Test
    fun `CR-only body offsets map to original messageBody slices`() {
        val body = "What funding is available?\r- What is the deadline?"
        val items = QaRequestExtractor.extract(body)
        assertEquals(2, items.size)
        assertEquals(foldLike(body.substring(items[0].startOffset, items[0].endOffset)), items[0].text)
        assertEquals(foldLike(body.substring(items[1].startOffset, items[1].endOffset)), items[1].text)
        assertTrue(items[0].text.contains("funding", ignoreCase = true))
        assertTrue(items[1].text.contains("deadline", ignoreCase = true))
        assertTrue(items[0].startOffset < items[1].startOffset)
    }

    private fun foldLike(text: String): String =
        text.replace(Regex("[ \\t]*\\r\\n[ \\t]*"), " ")
            .replace(Regex("[ \\t]*\\n[ \\t]*"), " ")
            .replace(Regex("[ \\t]*\\r[ \\t]*"), " ")
            .replace(Regex(" {2,}"), " ")
            .trim()
}
