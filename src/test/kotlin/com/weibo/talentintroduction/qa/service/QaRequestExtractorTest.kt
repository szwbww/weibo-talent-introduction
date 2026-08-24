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

    // ── 计划 01 (阶段 1): bullet marker 必须带空白，Markdown 强调签名不是 bullet ──

    // 脱敏线上结构 (LIVE_INBOUND:124)：真实复合 question + 5 条 *...* 签名。
    // 修复前 5 条签名被拆成 BULLET；修复后只保留 1 条 QUESTION，offset 可回切原文。
    @Test
    fun `live signature with markdown emphasis is not extracted as bullets`() {
        val body = """
            Could you tell me the official programme name and the usual form of collaboration?

            *Name*
            *Title*
            *Institution*
            *Phone*
            *Address*
        """.trimIndent()

        val items = QaRequestExtractor.extract(body)
        assertEquals(1, items.size, items.map { it.text }.toString())
        assertEquals(QaRequestExtractor.Kind.QUESTION, items[0].kind)
        assertTrue(items[0].text.contains("official programme name", ignoreCase = true))
        assertTrue(items[0].text.contains("usual form of collaboration", ignoreCase = true))
        assertFalse(items.any { it.text.contains("*") }, "signature lines must not be extracted: ${items.map { it.text }}")
        assertTrue(items[0].startOffset in 0 until items[0].endOffset, "bad range: $items")
        assertTrue(items[0].endOffset <= body.length, "end past body: $items")
        assertEquals(foldLike(body.substring(items[0].startOffset, items[0].endOffset)), items[0].text)
    }

    // marker 边界表：五种合法 marker 均为 BULLET，且保持源顺序。
    @Test
    fun `all five explicit bullet markers are extracted in source order`() {
        val body = """
            - dash bullet
            * star bullet
            • dot bullet
            1. numbered-dot bullet
            1) numbered-paren bullet
        """.trimIndent()

        val items = QaRequestExtractor.extract(body)
        assertEquals(5, items.size, items.map { it.text }.toString())
        assertEquals(listOf(QaRequestExtractor.Kind.BULLET), items.map { it.kind }.distinct())
        assertTrue(items[0].text.contains("dash bullet"))
        assertTrue(items[1].text.contains("star bullet"))
        assertTrue(items[2].text.contains("dot bullet"))
        assertTrue(items[3].text.contains("numbered-dot bullet"))
        assertTrue(items[4].text.contains("numbered-paren bullet"))
        assertTrue(items.zipWithNext().all { (a, b) -> a.startOffset < b.startOffset })
    }

    // marker 边界表：无空白 marker 拒绝 —— Markdown 强调与连字符文本不是列表。
    @Test
    fun `markdown emphasis and hyphenated text are not bullets`() {
        val body = """
            *Name*
            -not a list

            What funding is available?
        """.trimIndent()

        val items = QaRequestExtractor.extract(body)
        assertEquals(1, items.size, items.map { it.text }.toString())
        assertEquals(QaRequestExtractor.Kind.QUESTION, items[0].kind)
        assertTrue(items[0].text.contains("funding", ignoreCase = true))
        assertFalse(items.any { it.text.contains("Name", ignoreCase = true) })
        assertFalse(items.any { it.text.contains("not a list", ignoreCase = true) })
    }

    // 缩进续行规则不变：空白开头的续行仍折进 bullet。
    @Test
    fun `indented continuation lines still fold into the bullet`() {
        val body = """
            - first line
              indented continuation
            - second line
        """.trimIndent()

        val items = QaRequestExtractor.extract(body)
        assertEquals(2, items.size, items.map { it.text }.toString())
        assertTrue(items[0].text.contains("first line"))
        assertTrue(items[0].text.contains("indented continuation"))
        assertFalse(items[0].text.contains("\n"), "soft newlines must fold: ${items[0].text}")
        assertTrue(items[1].text.contains("second line"))
        assertTrue(items[0].startOffset < items[1].startOffset)
    }

    private fun foldLike(text: String): String =
        text.replace(Regex("[ \\t]*\\r\\n[ \\t]*"), " ")
            .replace(Regex("[ \\t]*\\n[ \\t]*"), " ")
            .replace(Regex("[ \\t]*\\r[ \\t]*"), " ")
            .replace(Regex(" {2,}"), " ")
            .trim()
}
