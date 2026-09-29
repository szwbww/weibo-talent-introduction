package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * I-2 地址证据纯判据矩阵：唯一白名单 `{5.1.1, 5.1.2, 5.1.3, 5.1.10}`；
 * 整字段匹配；不扫描正文/reason/URL；`5.1.7`/`5.1.8`（发件方问题）与未知码必须拒绝。
 */
class RecipientAddressFailureClassifierTest {

    @ParameterizedTest
    @ValueSource(strings = ["5.1.1", "5.1.2", "5.1.3", "5.1.10"])
    fun `whitelisted enhanced statuses are address evidence`(value: String) {
        assertTrue(RecipientAddressFailureClassifier.isInvalidDsnStatus(value))
    }

    @ParameterizedTest
    @ValueSource(strings = [" 5.1.10 ", "\t5.1.1\n", "550 5.1.1", "550-5.1.1", "550 5.1.10", "554-5.1.2"])
    fun `padded and legacy smtp prefixed forms are address evidence`(value: String) {
        assertTrue(RecipientAddressFailureClassifier.isInvalidDsnStatus(value))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            // 无增强码的裸 SMTP 码
            "550",
            // 白名单外的增强码（含发件方地址问题的 5.1.7/5.1.8 与未扩的 5.1.6）
            "5.1.5",
            "5.1.6",
            "5.1.7",
            "5.1.8",
            "5.2.2",
            "5.4.1",
            "5.7.1",
            "5.0.0",
            "4.2.2",
            // 截断/非法长度
            "5.1.100",
            "5.1.10.1",
            "15.1.1",
            "5.1.1.2",
            // 多码、尾随解释、嵌入正文与 URL
            "5.1.1 5.1.2",
            "reason: 5.1.1",
            "5.1.1 user unknown",
            "https://example.com/5.1.1",
            "550 5.1.1 user unknown"
        ]
    )
    fun `everything else is not address evidence`(value: String) {
        assertFalse(RecipientAddressFailureClassifier.isInvalidDsnStatus(value))
    }

    @Test
    fun `null and blank are not address evidence`() {
        assertFalse(RecipientAddressFailureClassifier.isInvalidDsnStatus(null))
        assertFalse(RecipientAddressFailureClassifier.isInvalidDsnStatus(""))
        assertFalse(RecipientAddressFailureClassifier.isInvalidDsnStatus("   "))
    }

    // ── 03 I-2：结构化 SMTP 摘要 `PERMANENT:<5xx>:<详情>` 的地址证据矩阵 ──

    @ParameterizedTest
    @ValueSource(
        strings = [
            "PERMANENT:550:550 5.1.1 User unknown",
            "PERMANENT:554:554-5.1.2 mailbox unavailable",
            "PERMANENT:553:5.1.3 Bad destination address syntax",
            "PERMANENT:556:556 5.1.10 Null MX",
            "PERMANENT:550:   550 5.1.1 User unknown",
            "PERMANENT:550:5.1.1 User unknown",
            // 重复同一码（同一响应多行回显）允许
            "PERMANENT:550:550 5.1.1 User unknown\n550 5.1.1 User unknown",
            // 不含协议码的说明行不作为证据，也不否定已成立的行首协议码
            "PERMANENT:550:550 5.1.1 User unknown\npostmaster note"
        ]
    )
    fun `permanent summary with a trusted whitelisted protocol line is address evidence`(summary: String) {
        assertTrue(RecipientAddressFailureClassifier.isInvalidPermanentSummary(summary))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            // 政策/路由/容量/发件方类永久码：协议成立但地址证据不成立
            "PERMANENT:550:550 5.7.1 Blocked",
            "PERMANENT:550:5.7.1 policy rejection",
            "PERMANENT:550:550 5.2.2 mailbox full",
            "PERMANENT:550:550 5.1.7 Bad sender address",
            "PERMANENT:550:5.1.6",
            // 4xx 增强码不构成永久地址证据
            "PERMANENT:550:550 4.2.2 try again later",
            "PERMANENT:450:450 4.2.2 try again later",
            // 无可信协议码（裸码/英文说明/嵌入正文/URL）
            "PERMANENT:550:550",
            "PERMANENT:550:",
            "PERMANENT:550:user unknown",
            "PERMANENT:550:reason: 5.1.1 user unknown",
            "PERMANENT:550:https://example.com/5.1.1",
            // 前缀不成立
            "PERMANENT",
            "PERMANENT:550",
            "PERMANENT:250:250 5.1.1 ok",
            "TRANSIENT:421:421 5.1.1 slow down",
            // 摘要三位码与协议行三位码冲突
            "PERMANENT:554:550 5.1.1 User unknown",
            // 不同增强码冲突
            "PERMANENT:550:550 5.1.1 User unknown\n550 5.1.2 mailbox unavailable",
            "PERMANENT:550:5.1.1 first line\n5.1.2 second line",
            // 残缺/非法长度（不许截断出 5.1.1）
            "PERMANENT:550:550 5.1.100 mailbox unavailable",
            "PERMANENT:550:550 5.1.10.1 nested"
        ]
    )
    fun `permanent summary without trusted whitelisted address evidence is not address evidence`(summary: String) {
        assertFalse(RecipientAddressFailureClassifier.isInvalidPermanentSummary(summary))
    }

    @Test
    fun `null and blank summaries are not address evidence`() {
        assertFalse(RecipientAddressFailureClassifier.isInvalidPermanentSummary(null))
        assertFalse(RecipientAddressFailureClassifier.isInvalidPermanentSummary(""))
        assertFalse(RecipientAddressFailureClassifier.isInvalidPermanentSummary("   "))
    }

    @Test
    fun `a detail at the 200 character truncation cap is never address evidence`() {
        val protocolLine = "550 5.1.1 User unknown"
        // 199 字符的完整协议行：仍按白名单判定（未被截断）。
        val detail199 = protocolLine + " " + "x".repeat(199 - protocolLine.length - 1)
        assertEquals(199, detail199.length)
        assertTrue(RecipientAddressFailureClassifier.isInvalidPermanentSummary("PERMANENT:550:$detail199"))
        // 200 字符：已达截断上限，即使协议码完整可见也保守 false。
        assertFalse(RecipientAddressFailureClassifier.isInvalidPermanentSummary("PERMANENT:550:${detail199}x"))
        // 协议码恰好被截断在末尾（尾部 5.1.10 可能被截成 5.1.1）→ false。
        assertFalse(
            RecipientAddressFailureClassifier.isInvalidPermanentSummary(
                "PERMANENT:550:" + "x".repeat(194) + " 5.1.1"
            )
        )
    }
}
