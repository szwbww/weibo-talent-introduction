package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageIdNormalizerTest {

    @Test
    fun `strips vendor prefix from reminder message id`() {
        val raw = "<6136051B41AACA62+reminder-2088-710aba50-77fa-4936-a8d3-72ecffaba836@talents.szwebotech.cn>"
        val candidates = MessageIdNormalizer.candidatesFor(raw)

        // I-4: 顺序 原值 -> 规范化 -> 剥离前缀，保序去重
        assertEquals(
            listOf(
                raw,
                "<reminder-2088-710aba50-77fa-4936-a8d3-72ecffaba836@talents.szwebotech.cn>"
            ),
            candidates
        )
    }

    @Test
    fun `strips vendor prefix when suffix is javamail default format`() {
        val raw = "<ED4DEF51D75D746B+1387390957.0.1783265426131@VM-4-16-centos>"
        val candidates = MessageIdNormalizer.candidatesFor(raw)

        // I-1 关键回归：'+' 之后是 JavaMail 默认格式，剥离后原样保留、不做格式假设
        assertEquals(
            listOf(
                raw,
                "<1387390957.0.1783265426131@VM-4-16-centos>"
            ),
            candidates
        )
    }

    @Test
    fun `unprefixed message id yields single candidate`() {
        val raw = "<manual-outreach-TEST-LUKAI-18014905480-66392015-4c74-424c-9609-8896a382e20b@weibo.com>"
        val candidates = MessageIdNormalizer.candidatesFor(raw)

        assertEquals(listOf(raw), candidates)
    }

    @Test
    fun `lowercase hex prefix is not stripped`() {
        val raw = "<6136051b41aaca62+reminder-1-x@d.cn>"
        val candidates = MessageIdNormalizer.candidatesFor(raw)

        assertEquals(listOf(raw), candidates)
    }

    @Test
    fun `short hex prefix is not stripped`() {
        val raw = "<ABC123+reminder-1-x@d.cn>"
        val candidates = MessageIdNormalizer.candidatesFor(raw)

        assertEquals(listOf(raw), candidates)
    }

    @Test
    fun `17-char hex prefix is not stripped`() {
        val raw = "<0123456789ABCDEF0+reminder-1-x@d.cn>"
        val candidates = MessageIdNormalizer.candidatesFor(raw)

        assertEquals(listOf(raw), candidates)
    }

    @Test
    fun `local-part with plus but no vendor prefix is not stripped`() {
        val raw = "<local+part+more@d.cn>"
        val candidates = MessageIdNormalizer.candidatesFor(raw)

        assertEquals(listOf(raw), candidates)
    }

    @Test
    fun `unbracketed message id yields raw and bracketed candidates`() {
        val raw = "reminder-1-x@d.cn"
        val candidates = MessageIdNormalizer.candidatesFor(raw)

        assertEquals(listOf(raw, "<reminder-1-x@d.cn>"), candidates)
    }

    @Test
    fun `strip leaving empty local-part returns input unchanged`() {
        val raw = "<0123456789ABCDEF+@d.cn>"
        val candidates = MessageIdNormalizer.candidatesFor(raw)

        assertEquals(listOf(raw), candidates)
    }

    @Test
    fun `blank or null input yields empty candidate list`() {
        assertEquals(emptyList<String>(), MessageIdNormalizer.candidatesFor(null))
        assertEquals(emptyList<String>(), MessageIdNormalizer.candidatesFor(""))
        assertEquals(emptyList<String>(), MessageIdNormalizer.candidatesFor("   "))
    }

    @Test
    fun `multiple msg-ids takes first bracketed segment`() {
        val raw = "<A@d.cn> <B@d.cn>"
        val candidates = MessageIdNormalizer.candidatesFor(raw)

        assertEquals(2, candidates.size)
        assertEquals(raw, candidates[0])
        assertEquals("<A@d.cn>", candidates[1])
        assertTrue(candidates.none { it == "<B@d.cn>" })
    }

    @Test
    fun `canonicalize trims whitespace`() {
        assertEquals("<A@d.cn>", MessageIdNormalizer.canonicalize("  <A@d.cn>  "))
        assertEquals(null, MessageIdNormalizer.canonicalize("   "))
        assertEquals(null, MessageIdNormalizer.canonicalize(null))
    }

    @Test
    fun `stripVendorPrefix on unbracketed input applies regex and rewraps`() {
        assertEquals(
            "<plain@d.cn>",
            MessageIdNormalizer.stripVendorPrefix("<0123456789ABCDEF+plain@d.cn>")
        )
        assertEquals(
            "<plain@d.cn>",
            MessageIdNormalizer.stripVendorPrefix("0123456789ABCDEF+plain@d.cn")
        )
    }
}
