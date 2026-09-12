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

    // I-1：人工富文本纯文本任意连续换行（含 CRLF/CR、仅空格或 Tab 的空白行）压成单个 \n。
    @Test
    fun `normalizeManualTextLineBreaks collapses every consecutive newline run to one lf`() {
        assertEquals("A\nB", service.normalizeManualTextLineBreaks("A\n\n\n\n\nB"))
        assertEquals("A\nB", service.normalizeManualTextLineBreaks("A\r\n\r\n\r\nB"))
        assertEquals("A\nB", service.normalizeManualTextLineBreaks("A\r\rB"))
        assertEquals("A\nB", service.normalizeManualTextLineBreaks("A\n \t\nB"))
        // 单换行与纯文本正文幂等（不得引入多余换行或删除内容）
        assertEquals("A\nB", service.normalizeManualTextLineBreaks("A\nB"))
        assertEquals("A\nB\nC", service.normalizeManualTextLineBreaks("A\nB\nC"))
        // 结果不含 CR，也不含相邻换行
        val collapsed = service.normalizeManualTextLineBreaks("A\r\n\r\n \r\nB")
        assertFalse(collapsed.contains('\r'))
        assertFalse(collapsed.contains("\n\n"))
    }

    // I-1：非空行内容逐字保留（不 trim、不合并行内空白、不动空串）。
    @Test
    fun `normalizeManualTextLineBreaks keeps non blank lines verbatim`() {
        assertEquals("A\n B \nC", service.normalizeManualTextLineBreaks("A\n\n B \n\nC"))
        assertEquals("A  B", service.normalizeManualTextLineBreaks("A  B"))
        assertEquals("", service.normalizeManualTextLineBreaks(""))
        assertEquals("A", service.normalizeManualTextLineBreaks("A"))
    }

    // I-2：连续 <br>（标签间允许空白）折叠为一个；其他标签/属性/文本顺序逐字保留。
    @Test
    fun `normalizeManualRichHtmlLineBreaks folds consecutive br into one`() {
        assertEquals(
            "<b>A</b><br><a href=\"https://x.test\">B</a>",
            service.normalizeManualRichHtmlLineBreaks("<b>A</b><br><br><br><a href=\"https://x.test\">B</a>")
        )
        assertEquals("A<br>B", service.normalizeManualRichHtmlLineBreaks("A<br> \n<br />B"))
        // 单个 br 不动
        assertEquals("<p>A<br>B</p>", service.normalizeManualRichHtmlLineBreaks("<p>A<br>B</p>"))
    }

    // I-2：仅由空白/&nbsp;/<br> 组成的空 <p>/<div> 不产生重复空行；嵌套空块也收敛。
    @Test
    fun `normalizeManualRichHtmlLineBreaks removes empty blocks without touching content`() {
        assertEquals(
            "<p>A</p><p>B</p>",
            service.normalizeManualRichHtmlLineBreaks("<p>A</p><p><br></p><p>&nbsp;</p><p>B</p>")
        )
        assertEquals(
            "<div>A</div><div>B</div>",
            service.normalizeManualRichHtmlLineBreaks("<div>A</div><div><br></div><div>B</div>")
        )
        assertEquals(
            "<p>A</p>",
            service.normalizeManualRichHtmlLineBreaks("<p>A</p><div><div><br></div></div>")
        )
    }

    // I-2/I-6：bold、链接、列表等非空内容与属性逐字保留；空输入直接返回。
    @Test
    fun `normalizeManualRichHtmlLineBreaks preserves non empty markup verbatim`() {
        val list = "<ul><li><b>A</b></li><li><a href=\"https://x.test\">B</a></li></ul>"
        assertEquals(list, service.normalizeManualRichHtmlLineBreaks(list))
        val styled = "<div class=\"mc-block\" style=\"color:#333\">A<br>B</div>"
        assertEquals(styled, service.normalizeManualRichHtmlLineBreaks(styled))
        assertEquals("", service.normalizeManualRichHtmlLineBreaks(""))
    }

    // I-5：非人工富文本路径的段落约定不受影响（\n\n 仍映射为两个 <p>）。
    @Test
    fun `plainTextToHtml keeps the two paragraph convention after normalization exists`() {
        assertEquals(
            "<p>First paragraph.</p><p>Second paragraph.</p>",
            service.plainTextToHtml("First paragraph.\n\nSecond paragraph.")
        )
        assertEquals(
            "First paragraph.\n\nSecond paragraph.",
            service.htmlToPlainText("<p>First paragraph.</p><p>Second paragraph.</p>")
        )
    }
}
