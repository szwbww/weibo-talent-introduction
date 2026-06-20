package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
}
