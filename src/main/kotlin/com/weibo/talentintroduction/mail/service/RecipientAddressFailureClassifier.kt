package com.weibo.talentintroduction.mail.service

/**
 * I-2：收件地址无效的精确、保守判据（DSN 永久退信的**地址证据**）。
 *
 * 唯一白名单 `{5.1.1, 5.1.2, 5.1.3, 5.1.10}`，对应 IANA 增强状态码的
 * 「目的邮箱不存在 / 目的系统无效 / 目的地址语法错误 / Null MX」。
 * - `5.1.7`/`5.1.8` 是**发件人**地址问题，必须拒绝；`5.1.6` 本轮不扩白名单。
 * - 未知码、无增强码（如裸 `550`）、多个码、尾随解释、残缺字段一律不构成证据。
 *
 * 输入契约：只消费 `bounce_record.dsn_status` 字段整串，绝不扫描退信正文 / reason / URL。
 * 无依赖 Kotlin object：不新增 Spring 构造参数，不引入服务商规则。
 */
object RecipientAddressFailureClassifier {

    /** 完整增强码：class 一位，subject/detail 各 1～3 位（不许截断 `5.1.10`）。 */
    private val ENHANCED_STATUS = Regex("""5\.\d{1,3}\.\d{1,3}""")

    /** 既有 SMTP 文本格式：三位 5xx 码 + 空白/连字符 + 完整增强码（如 `550-5.1.1`、`550 5.1.1`）。 */
    private val LEGACY_SMTP_PREFIXED = Regex("""5\d\d[\s-]+(5\.\d{1,3}\.\d{1,3})""")

    private val ALLOWED_STATUSES = setOf("5.1.1", "5.1.2", "5.1.3", "5.1.10")

    /**
     * 整字段匹配（`Regex.matches`/`matchEntire` 双重锚定）后才判定白名单：
     * `5.1.1 5.1.2`、`reason: 5.1.1`、`https://…/5.1.1`、`5.1.1 user unknown`、`5.1.100`、
     * `5.1.10.1`、`15.1.1`、裸 `550` 均为 false。
     */
    fun isInvalidDsnStatus(value: String?): Boolean {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return false
        val enhanced = if (ENHANCED_STATUS.matches(raw)) {
            raw
        } else {
            LEGACY_SMTP_PREFIXED.matchEntire(raw)?.groupValues?.get(1) ?: return false
        }
        return enhanced in ALLOWED_STATUSES
    }
}
