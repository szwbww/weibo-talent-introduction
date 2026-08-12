package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MailContentServiceTest {
    private val service = MailContentService()

    @Test
    fun `converts paragraph and line breaks`() {
        val plain = service.htmlToPlainText("<p>Hello</p><br/>World")

        assertEquals("Hello\n\nWorld", plain)
    }

    @Test
    fun `strips tags and decodes entities`() {
        val plain = service.htmlToPlainText("<p>Tom &amp; Jerry &lt;3&gt;</p>")

        assertEquals("Tom & Jerry <3>", plain)
    }

    @Test
    fun `normalizes whitespace`() {
        val plain = service.htmlToPlainText("<p>Hello   world</p>\n\n\n\n<p>Again</p>")

        assertEquals("Hello world\n\nAgain", plain)
    }

    @Test
    fun `removes script and style blocks`() {
        val plain = service.htmlToPlainText(
            "<style>.x{color:red}</style><script>alert(1)</script><p>Visible</p>"
        )

        assertEquals("Visible", plain)
        assertFalse(plain.contains("alert"))
        assertFalse(plain.contains("color"))
    }

    @Test
    fun `plainTextToHtml maps blank lines to paragraphs`() {
        val html = service.plainTextToHtml("First paragraph.\n\nSecond paragraph.")

        assertEquals("<p>First paragraph.</p><p>Second paragraph.</p>", html)
    }

    @Test
    fun `plainTextToHtml maps single line breaks to br`() {
        val html = service.plainTextToHtml("Line one\nLine two")

        assertEquals("<p>Line one<br>Line two</p>", html)
    }

    @Test
    fun `plainTextToHtml escapes html characters`() {
        val html = service.plainTextToHtml("Tom & Jerry <3>")

        assertEquals("<p>Tom &amp; Jerry &lt;3&gt;</p>", html)
    }

    @Test
    fun `plainTextToHtml returns empty string for blank input`() {
        assertEquals("", service.plainTextToHtml(""))
        assertEquals("", service.plainTextToHtml("   "))
    }

    @Test
    fun `plainTextToHtml with urls replaces exact url with anchor`() {
        val url = "https://example.com/u/unsubscribe?token=abc123"
        val html = service.plainTextToHtml(
            "Click here: $url",
            listOf(url)
        )

        assertEquals("<p>Click here: <a href=\"$url\">Unsubscribe</a></p>", html)
    }

    @Test
    fun `plainTextToHtml with empty url collection matches single arg overload`() {
        val plain = "First paragraph.\n\nSecond paragraph with <brackets> & ampersands."
        assertEquals(
            service.plainTextToHtml(plain),
            service.plainTextToHtml(plain, emptyList())
        )
    }

    @Test
    fun `plainTextToHtml skips blank urls and produces no empty anchor`() {
        val html = service.plainTextToHtml("Please unsubscribe: https://example.com/u", listOf("", "   "))

        assertFalse(html.contains("href=\"\""))
        assertFalse(html.contains("<a href=\""))
        assertEquals("<p>Please unsubscribe: https://example.com/u</p>", html)
    }

    @Test
    fun `plainTextToHtml escapes html characters before anchoring urls`() {
        val url = "https://example.com/u/unsubscribe?token=abc123"
        val html = service.plainTextToHtml(
            "Tom & Jerry <3> use $url",
            listOf(url)
        )

        assertTrue(html.contains("Tom &amp; Jerry &lt;3&gt; use <a href=\"$url\">Unsubscribe</a>"))
        assertFalse(html.contains("<a href=\"&lt;"), "anchor tag itself must not be escaped")
    }

    @Test
    fun `plainTextToHtml anchors only exact target url not other urls`() {
        val target = "https://example.com/u/unsubscribe?token=abc"
        val html = service.plainTextToHtml(
            "Visit https://www.qingfeitalent.com or use $target",
            listOf(target)
        )

        assertTrue(html.contains("<a href=\"$target\">Unsubscribe</a>"))
        assertTrue(html.contains("https://www.qingfeitalent.com"), "non-target url must stay plain text")
        assertFalse(html.contains("href=\"https://www.qingfeitalent.com\""))
    }
}
