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
     * 结构化 SMTP 失败摘要（`buildSmtpErrorSummary`）：`PERMANENT:<三位码>:<详情>`。
     * 只认 500..599 的永久类别；详情按原样保留（含换行，不折叠）。
     */
    private val PERMANENT_SUMMARY = Regex("""PERMANENT:(5\d\d):([\s\S]*)""")

    /**
     * 详情行首（可带空白）的**完整**增强码：`5.1.1 User unknown`、`  5.1.10`。
     * 前后边界都要求行首/空白或行尾，`5.1.100`、`5.1.10.1` 不成立。
     */
    private val PROTOCOL_ENHANCED_LINE = Regex("""^[ \t]*(5\.\d{1,3}\.\d{1,3})(?=[ \t]|$)""")

    /**
     * 详情行首三位 SMTP 码 + 空白/连字符 + 完整增强码：`550 5.1.1`、`554-5.1.2 mailbox unavailable`。
     */
    private val PROTOCOL_SMTP_LINE = Regex("""^[ \t]*(\d{3})[ \t-]+(5\.\d{1,3}\.\d{1,3})(?=[ \t]|$)""")

    /**
     * `buildSmtpErrorSummary` 对 `errorDetail` 的现存截断上限（200 字符，见该方法的 take 调用）。
     * 详情长度达到该上限即无法区分「尾部被截断的 5.1.10」与「完整的 5.1.1」→ 一律不构成证据。
     */
    private const val SUMMARY_DETAIL_LIMIT = 200

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

    /**
     * SMTP 首封永久失败摘要（`PERMANENT:<500..599>:<详情>`，与对账读取的持久化字段逐字同源）中的
     * **地址证据**判据；白名单与 [isInvalidDsnStatus] 完全相同。
     *
     * 保守、有界、按协议行走：
     * - 只接受**行首**（可带空白）的完整增强码，或行首三位 SMTP 码 + 空白/连字符 + 完整增强码；
     *   绝不从正文/URL/异常说明里搜嵌入码（`reason: 5.1.1`、`https://…/5.1.1` 不成立）。
     * - 命中的 SMTP 三位码必须与摘要三位码一致；不同增强码冲突、只有 4xx 增强码、无可信协议行、
     *   非 PERMANENT 前缀、非法长度（`5.1.100`、`5.1.10.1`）一律 false。重复同一码允许。
     * - 详情长度达到 [SUMMARY_DETAIL_LIMIT]（现存 200 字符截断上限）时保守返回 false：
     *   允许漏掉自动无效标记，不允许凭截断推断地址无效；不改变摘要存储格式。
     */
    fun isInvalidPermanentSummary(summary: String?): Boolean {
        val match = PERMANENT_SUMMARY.matchEntire(summary ?: return false) ?: return false
        val summaryCode = match.groupValues[1]
        val detail = match.groupValues[2]
        if (detail.length >= SUMMARY_DETAIL_LIMIT) return false
        val enhancedCodes = linkedSetOf<String>()
        for (line in detail.lines()) {
            val smtpLine = PROTOCOL_SMTP_LINE.find(line)
            if (smtpLine != null) {
                // 摘要三位码与协议行三位码必须一致，否则不是同一份证据。
                if (smtpLine.groupValues[1] != summaryCode) return false
                enhancedCodes += smtpLine.groupValues[2]
                continue
            }
            PROTOCOL_ENHANCED_LINE.find(line)?.let { enhancedCodes += it.groupValues[1] }
        }
        if (enhancedCodes.size != 1) return false
        return enhancedCodes.single() in ALLOWED_STATUSES
    }
}
