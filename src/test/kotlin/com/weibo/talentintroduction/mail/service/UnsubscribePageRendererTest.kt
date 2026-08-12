package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.UnsubscribeProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnsubscribePageRendererTest {

    private fun renderer(
        brandLogoUrl: String = "",
        siteUrl: String = "https://www.qingfeitalent.com",
        footerLine1: String = "Jiangsu Qingfei Talent Technology Co., Ltd · Nanjing",
        footerLine2: String = "QFtechtalent@qftechtalent.com"
    ): UnsubscribePageRenderer =
        UnsubscribePageRenderer(
            UnsubscribeProperties(
                brandLogoUrl = brandLogoUrl,
                siteUrl = siteUrl,
                footerLine1 = footerLine1,
                footerLine2 = footerLine2
            )
        )

    @Test
    fun `confirm page keeps me subscribed as link with single form and button`() {
        val html = renderer().confirmPage("good-token", "liu@tsinghua.edu.cn")

        val keepIdx = html.indexOf("Keep me subscribed")
        assertTrue(keepIdx >= 0)
        val tagStart = html.lastIndexOf("<", keepIdx)
        assertTrue(html.substring(tagStart, keepIdx).startsWith("<a"))

        assertEquals(1, html.split("<form").size - 1)
        assertEquals(1, html.split("<button").size - 1)
    }

    @Test
    fun `token is html attribute escaped before rendering`() {
        val html = renderer().confirmPage("a\"><script>x</script>", "liu@tsinghua.edu.cn")

        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&quot;"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `confirm form action stays relative`() {
        val html = renderer().confirmPage("good-token", "liu@tsinghua.edu.cn")

        assertTrue(html.contains("action=\"unsubscribe/confirm\""))
        assertFalse(html.contains("action=\"/u/"))
    }

    @Test
    fun `page is self contained and wordmark degrades when no logo configured`() {
        val html = renderer().confirmPage("good-token", "liu@tsinghua.edu.cn")

        assertFalse(html.contains("styles.css"))
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("https://cdn"))
        assertFalse(html.contains("fonts.googleapis"))

        assertFalse(html.contains("<img"))
        assertTrue(html.contains("class=\"qf-wordmark\""))
    }

    @Test
    fun `brand logo renders img with alt when configured`() {
        val html = renderer(brandLogoUrl = "https://static.qingfeitalent.com/logo.png")
            .confirmPage("good-token", "liu@tsinghua.edu.cn")

        assertTrue(html.contains("<img class=\"qf-logo\" src=\"https://static.qingfeitalent.com/logo.png\""))
        assertTrue(html.contains("alt="))
    }

    @Test
    fun `email is masked in confirm page`() {
        val html = renderer().confirmPage("good-token", "liu@tsinghua.edu.cn")

        assertTrue(html.contains("l•••@tsinghua.edu.cn"))
        assertFalse(html.contains("liu@"))
    }

    @Test
    fun `mask email handles boundary shapes`() {
        val render = renderer()

        assertTrue(render.confirmPage("t", "a@b.com").contains("•••@b.com"))
        assertTrue(render.confirmPage("t", "noatsign").contains("<p class=\"qf-pill\">•••</p>"))
        assertTrue(render.confirmPage("t", "a@b@c.com").contains("•••@c.com"))
    }

    @Test
    fun `both states share identical single style block and footer`() {
        val confirm = renderer().confirmPage("good-token", "liu@tsinghua.edu.cn")
        val success = renderer().successPage()

        assertEquals(1, confirm.split("<style").size - 1)
        assertEquals(1, success.split("<style").size - 1)

        val confirmStyle = confirm.substringAfter("<style>").substringBefore("</style>")
        val successStyle = success.substringAfter("<style>").substringBefore("</style>")
        assertEquals(confirmStyle, successStyle)

        assertTrue(confirm.contains("class=\"qf-foot\""))
        assertTrue(success.contains("class=\"qf-foot\""))
    }

    @Test
    fun `style contract fragments are present and stale palette absent`() {
        val html = renderer().confirmPage("good-token", "liu@tsinghua.edu.cn")

        assertTrue(html.contains("--color-bg:#05070f"))
        assertTrue(html.contains("background:var(--gradient-brand)"))
        assertTrue(html.contains("linear-gradient(100deg,#60a5fa,#3b82f6 55%,#6366f1)"))
        assertTrue(html.contains("font:700 11px/1 var(--font-mono)"))
        assertTrue(html.contains("@media (max-width:480px)"))

        assertFalse(html.contains("#0B1B2E"))
        assertFalse(html.contains("#1E6FB8"))
        assertFalse(html.contains("#5DCAA5"))
    }

    @Test
    fun `success page renders contract fragments`() {
        val html = renderer().successPage()

        assertTrue(html.contains("<p class=\"qf-check\">&#10003;</p>"))
        assertTrue(html.contains("<h1 class=\"qf-title\">You&#39;ve been unsubscribed</h1>"))
        assertTrue(html.contains("Visit www.qingfeitalent.com &#8594;"))
    }

    @Test
    fun `empty footer line omits its break`() {
        val html = renderer(footerLine2 = "").successPage()

        assertTrue(html.contains("Jiangsu Qingfei Talent Technology Co., Ltd · Nanjing"))
        assertFalse(html.contains("<br>"))
    }
}
