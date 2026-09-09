package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import javax.mail.internet.MimeUtility

/**
 * I-4 纯函数矩阵：null/空/普通文本不变；合法 folding 先 unfold 一次再 decodeText 单次
 * 解码；不做 HTML unescape、不把结果当 HTML、不循环解码；未知 charset/损坏输入回退原串，
 * 永不抛异常。截图 fixture：UTF-8 Q 两段（折叠）、Windows-1252 Q、UTF-8 B。
 */
class MailSubjectDecoderTest {

    @Test
    fun `null and empty strings pass through unchanged`() {
        assertNull(MailSubjectDecoder.decode(null))
        assertEquals("", MailSubjectDecoder.decode(""))
    }

    @Test
    fun `plain english and chinese text pass through unchanged`() {
        assertEquals("Re: Introduction", MailSubjectDecoder.decode("Re: Introduction"))
        assertEquals("你好，专家合作邀请", MailSubjectDecoder.decode("你好，专家合作邀请"))
        assertEquals("Meeting z9 follow-up", MailSubjectDecoder.decode("Meeting z9 follow-up"))
    }

    @Test
    fun `plain underscores are never rewritten`() {
        // 非 MIME 正则误把普通 _ 当 Q 空格 = 禁止回归。
        assertEquals("meeting_z9_followup", MailSubjectDecoder.decode("meeting_z9_followup"))
        assertEquals("_leading and trailing_", MailSubjectDecoder.decode("_leading and trailing_"))
    }

    @Test
    fun `html fragments pass through without unescape or decode-as-html`() {
        val html = "Team <a href=\"x\">café</a> &amp; more"
        assertEquals(html, MailSubjectDecoder.decode(html), "解码不得做 HTML unescape")
    }

    @Test
    fun `utf8 base64 encoded word decodes once`() {
        assertEquals("你好", MailSubjectDecoder.decode("=?UTF-8?B?5L2g5aW9?="))
    }

    @Test
    fun `windows-1252 q encoded word decodes`() {
        assertEquals("café", MailSubjectDecoder.decode("=?windows-1252?Q?caf=E9?="))
    }

    @Test
    fun `utf8 q two folded segments decode to readable text`() {
        // 截图 fixture：合法 folding 的 UTF-8 Q 两段；unfold 一次后相邻 encoded-word
        // 拼接解码（encoded-word 间的 LWSP 显示时忽略，RFC 2047 6.2）。
        assertEquals(
            "Re: Remote advisory collaboration request",
            MailSubjectDecoder.decode(
                "=?UTF-8?Q?Re:_Remote_advisory_collaboration?=\r\n =?UTF-8?Q?_request?="
            )
        )
        assertEquals(
            "Re: advisory meeting",
            MailSubjectDecoder.decode("=?UTF-8?Q?Re:_advisory?= =?UTF-8?Q?_meeting?=")
        )
    }

    @Test
    fun `folding whitespace inside plain text keeps the separating space`() {
        assertEquals("plain subject", MailSubjectDecoder.decode("plain\r\n subject"))
        assertEquals("tab\tcontinued", MailSubjectDecoder.decode("tab\r\n\tcontinued"))
    }

    @Test
    fun `unknown charset falls back to the original string`() {
        val raw = "=?unknown-charset-x?Q?abc?="
        assertEquals(raw, MailSubjectDecoder.decode(raw), "未知 charset 必须回退原串，不抛异常")
    }

    @Test
    fun `broken encoded words fall back to the original string`() {
        val noClosing = "=?UTF-8?Q?broken"
        assertEquals(noClosing, MailSubjectDecoder.decode(noClosing), "缺 ?= 的残缺词整体回退原串")
        // 合法词后跟普通文本：只解出合法词，绝不把相邻文本当 encoded-word 或抛异常。
        assertEquals("x garbage", MailSubjectDecoder.decode("=?UTF-8?Q?x?= garbage"))
    }

    @Test
    fun `encodeText output with real folding round trips back to the original text`() {
        val original = "Re: Remote advisory collaboration request — 请尽快回复 meeting-z9"
        val encoded = MimeUtility.encodeText(original, "UTF-8", "Q")
        assertEquals(original, MailSubjectDecoder.decode(encoded), "编码器折叠输出必须可逆")
    }

    @Test
    fun `decode never throws on hostile input`() {
        val hostile = listOf(
            "=?x?Q?${"a".repeat(10_000)}?=",
            "=?UTF-8?B?!!!!?=",
            "=?UTF-8?Q?\u0000?=",
            "=?  ?Q?=?=",
            "=?UTF-8?Q?" + "a".repeat(10_000)
        )
        for (input in hostile) {
            // 只要求不抛异常（可能回退原串）；decode 的职责是读路径永不炸。
            val result = MailSubjectDecoder.decode(input)
            if (result == null) {
                assertNull(input, "decode 不可把非 null 输入变 null")
            }
        }
    }
}
