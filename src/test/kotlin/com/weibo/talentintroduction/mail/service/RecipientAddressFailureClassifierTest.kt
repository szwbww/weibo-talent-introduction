package com.weibo.talentintroduction.mail.service

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
}
