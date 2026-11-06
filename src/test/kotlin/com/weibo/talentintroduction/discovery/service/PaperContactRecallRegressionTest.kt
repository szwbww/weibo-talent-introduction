package com.weibo.talentintroduction.discovery.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class PaperContactRecallRegressionTest {
    private fun resolve(rows: List<LayoutPdfFixtures.Row>) = extractOwnershipContent(
        LayoutPdfFixtures.bytes(rows), MediaType.APPLICATION_PDF, LayoutPdfFixtures.authors).emails

    @Test fun `centered affiliation and right aligned sidebar preserve contact blocks`() {
        val centered = listOf(LayoutPdfFixtures.Row("Alice Smith", 100f, 270f),
            LayoutPdfFixtures.Row("Example University Department of Sciences", 122f, 204f),
            LayoutPdfFixtures.Row("alice@sample.edu", 135f, 255f))
        assertEquals("Alice", resolve(centered).single().givenNames)
        // Like #31413, body and sidebar are emitted as separate column runs.
        val sidebar = listOf(LayoutPdfFixtures.Row("The main article's separate column", 302f, 168f),
            LayoutPdfFixtures.Row("continues here beside the contact", 314.1f, 168f),
            LayoutPdfFixtures.Row("Alice Smith", 300f, 100f),
            LayoutPdfFixtures.Row("alice@sample.edu", 314f, 69f))
        assertEquals("Alice", resolve(sidebar).single().givenNames)
    }

    @Test fun `explicit email field may be followed by social identifiers inside author block`() {
        val rows = listOf(LayoutPdfFixtures.Row("Alice Smith", 100f),
            LayoutPdfFixtures.Row("Example University", 112f),
            LayoutPdfFixtures.Row("Email: alice@sample.edu | Twitter: @opaque | ORCID: 0000-0002-1825-0097", 124f))
        assertEquals("Alice", resolve(rows).single().givenNames)
        assertTrue(resolve(rows.dropLast(1) + LayoutPdfFixtures.Row(
            "Email: alice@sample.edu | Bob Jones: bob@sample.edu", 124f)).all { it.givenNames == null })
    }

    @Test fun `owner label may wrap within one physical contact entry`() {
        val start = LayoutPdfFixtures.Row("opaque@sample.edu (Alice", 600f)
        assertEquals("Alice", resolve(listOf(start, LayoutPdfFixtures.Row("Smith)", 612f))).single().givenNames)
        for (end in listOf(LayoutPdfFixtures.Row("Smith)", 612f, 340f),
            LayoutPdfFixtures.Row("Smith)", 20f, page = 1), LayoutPdfFixtures.Row("Smith)", 640f))) {
            assertNull(resolve(listOf(start, end)).single().givenNames)
        }
        assertNull(resolve(listOf(start, LayoutPdfFixtures.Row("Bob Jones", 612f),
            LayoutPdfFixtures.Row("Smith)", 624f))).single().givenNames)
    }

    @Test fun `semicolon separated full name comma mailbox pairs are explicit ownership`() {
        val row = LayoutPdfFixtures.Row("* Alice Smith, opaque@sample.edu; Bob Jones, other@sample.edu", 600f)
        assertEquals(mapOf("opaque@sample.edu" to "Alice", "other@sample.edu" to "Bob"),
            resolve(listOf(row)).associate { it.email to it.givenNames })
        for (text in listOf("Alice Smith, Bob Jones, opaque@sample.edu",
            "Alice Smith and Bob Jones, opaque@sample.edu", "References: Alice Smith, opaque@sample.edu",
            "Alice Smith; Bob Jones, opaque@sample.edu", "Alice Smith, Bob Jones; opaque@sample.edu")) {
            assertTrue(resolve(listOf(LayoutPdfFixtures.Row(text, 600f))).all { it.givenNames == null }, text)
        }
    }

    @Test fun `splitting named entries cannot bypass the existing size and shared owner fences`() {
        val oversized = List(40) { "Alice Smith, opaque@sample.edu" }.joinToString("; ")
        assertTrue(oversized.length > 1000)
        for (text in listOf(oversized,
            "Alice Smith, opaque@sample.edu (all authors); Bob Jones, other@sample.edu")) {
            assertTrue(SourceAuthorEmailResolver.resolveText(text, LayoutPdfFixtures.authors).all { it.givenNames == null })
        }
    }

    @Test fun `font width overlap must not interleave a separately positioned mailbox`() {
        // #31863: fallback glyph widths overlap the following text object. Preserve each
        // encoded word's order, while keeping same-line/column boundaries for ownership.
        for (email in listOf("unique@sample.edu", "opaque@sample.edu")) {
            val rows = listOf(LayoutPdfFixtures.Row("Alice Smith*", 100f),
                LayoutPdfFixtures.Row("*", 600f), LayoutPdfFixtures.Row("Corresponding author: ", 602f, 45f),
                LayoutPdfFixtures.Row(email, 602f, 133f))
            org.apache.pdfbox.pdmodel.PDDocument.load(LayoutPdfFixtures.bytes(rows)).use { document ->
                val contact = PdfAuthorContactLayout.collect(document, 2, LayoutPdfFixtures.authors, 1).single()
                assertEquals(email, contact.email)
                assertEquals(0, contact.authorIndex)
            }
            // In the opaque control PDFBox suppresses the overlapping duplicate 'o'.
            // A truncated clue must NOT inherit the full mailbox's ownership.
            assertEquals(if (email.startsWith("unique")) "Alice" else null, resolve(rows).single().givenNames)
        }
    }

    @Test fun `original wrapped labels and comma contacts recover proven bindings`() {
        val expected = mapOf(32259L to "Frank-Michael Schleif", 32741L to "Alba Fernández-Izquierdo",
            37301L to "Seung Hwan Ko", 31413L to "Luca Salvatore", 31584L to "Joachim Goedhart", 31646L to "Asem Alzoubi")
        val replay = DiscoveryPaperIdentityReplayTool.replay(ReprocessOriginalInputs.directory,
            ReprocessOriginalInputs.manifest(), expected.keys) { fail<Unit>("Network forbidden") }
        for ((id, name) in expected) assertTrue(replay.any {
            (it["recordId"] as Number).toLong() == id && (it["newNames"] as List<*>).contains(name)
        }, "missing proven $id")
    }
}
