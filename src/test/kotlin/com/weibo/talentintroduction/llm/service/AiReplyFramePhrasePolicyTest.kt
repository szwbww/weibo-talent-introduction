package com.weibo.talentintroduction.llm.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiReplyFramePhrasePolicyTest {

    // ── I-2：剥离永不把答案清空 ────────────────────────────────────────────

    @Test
    fun `strip never empties a degenerate sign-off-only answer`() {
        val input = "Best regards,"
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertEquals(input, result.text)
        assertFalse(result.stripped)
        assertTrue(result.skipped)
    }

    @Test
    fun `strip never empties a salutation-plus-sign-off answer`() {
        val input = "Dear Josep,\n\nBest regards,"
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertEquals(input, result.text)
        assertFalse(result.stripped)
        assertTrue(result.skipped)
    }

    @Test
    fun `strip leaves blank input untouched without skip flag`() {
        val input = "   \n  "
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertEquals(input, result.text)
        assertFalse(result.stripped)
        assertFalse(result.skipped)
    }

    // ── I-3：无删除逐字返回原文；有删除只动整块/首句，不压缩布局 ──────────────

    @Test
    fun `strip returns the same instance when nothing is deletable`() {
        val input = "1. First point\n   continued line\n2. Second point"
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertSame(input, result.text)
        assertFalse(result.stripped)
        assertFalse(result.skipped)
    }

    @Test
    fun `strip preserves internal newlines indentation and numbering verbatim`() {
        val input = "Dear Josep,\n\nThank you for your message.\n\n" +
            "1. First point\n   with indent\n2. Second point\n\nBest regards,"
        val expected = "1. First point\n   with indent\n2. Second point"
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertEquals(expected, result.text)
        assertTrue(result.stripped)
        assertFalse(result.skipped)
    }

    @Test
    fun `strip removes only the leading consecutive salutation blocks`() {
        val input = "Dear Josep,\n\nHi there,\n\nWe are reviewing your application.\n\nBest regards,"
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertEquals("We are reviewing your application.", result.text)
        assertTrue(result.stripped)
    }

    @Test
    fun `strip removes only the trailing consecutive sign-off and courtesy blocks`() {
        val input = "We are reviewing your application.\n\nPlease let us know if you have any further questions.\n\nBest regards,"
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertEquals("We are reviewing your application.", result.text)
        assertTrue(result.stripped)
    }

    @Test
    fun `strip removes one opening thank-you sentence from the first remaining block`() {
        val input = "Thank you for your message.\n\nWe are reviewing your application.\n\nBest regards,"
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertEquals("We are reviewing your application.", result.text)
        assertTrue(result.stripped)
    }

    @Test
    fun `strip removes salutation and opening thank-you before the substance`() {
        val input = "Dear Josep,\n\nThank you for your message.\n\n" +
            "At this stage we are reviewing your profile."
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertEquals("At this stage we are reviewing your profile.", result.text)
        assertTrue(result.stripped)
    }

    @Test
    fun `strip leaves a mid-text salutation untouched`() {
        val input = "You asked whether \"Dear Colleague\" is an acceptable salutation for our template."
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertSame(input, result.text)
        assertFalse(result.stripped)
    }

    // ── I-5 反例：误删会破坏语义，必须逐字保留 ──────────────────────────────

    @Test
    fun `strip keeps a thank-you sentence without an incoming-mail noun`() {
        val input = "We are pleased to hear from you. Thank you for your patience while we complete the review."
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertSame(input, result.text)
        assertFalse(result.stripped)
    }

    @Test
    fun `strip keeps a sign-off word used mid-sentence`() {
        val input = "Regards from the Shanghai office were passed along."
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertSame(input, result.text)
        assertFalse(result.stripped)
    }

    @Test
    fun `strip keeps an opening thank-you not at the sentence start`() {
        val input = "We received your inquiry and are processing it. Thank you for your message; we will reply shortly."
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertSame(input, result.text)
        assertFalse(result.stripped)
    }

    @Test
    fun `strip keeps a thank-you sentence lacking sentence-final punctuation`() {
        val input = "Thank you for your message\nAt this stage we are reviewing your profile."
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertSame(input, result.text)
        assertFalse(result.stripped)
    }

    @Test
    fun `strip keeps frame words embedded in a longer paragraph`() {
        val input = "Please note that \"Best regards\" is our standard sign-off for outbound mail."
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertSame(input, result.text)
        assertFalse(result.stripped)
    }

    // ── 边界：中文标点与尾逗号变体 ─────────────────────────────────────────

    @Test
    fun `strip handles full-width comma in salutation`() {
        val input = "Dear Professor，\n\nWe are reviewing your application."
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertEquals("We are reviewing your application.", result.text)
        assertTrue(result.stripped)
    }

    @Test
    fun `strip handles sign-off with trailing comma and courtesy with period`() {
        val input = "We are reviewing your application.\n\nPlease let us know if you have any further questions.\n\nBest regards,"
        val result = AiReplyFramePhrasePolicy.strip(input)
        assertEquals("We are reviewing your application.", result.text)
        assertTrue(result.stripped)
    }
}
