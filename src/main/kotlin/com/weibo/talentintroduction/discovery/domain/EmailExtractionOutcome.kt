package com.weibo.talentintroduction.discovery.domain

data class EmailExtractionOutcome @JvmOverloads constructor(
    val emails: List<AuthorEmail>,
    val methodUsed: String?,
    val failureReason: String? = null,
    val httpRequests: Int = 0,
    /**
     * c10（I-3）：本次提取是否真的取到了全文内容（与「有没有抽到邮箱」分开）。null 表示该适配器没有声明
     * （旧构造），由 [resolvedFulltextObtained] 沿用改动前的推导。所有新增字段都有默认值，EuropePMC /
     * CORE / arXiv / Crossref 的现有构造与统计口径不变。
     */
    val fulltextObtained: Boolean? = null,
    /**
     * c10（I-3）：下载失败的低基数类别，供任务 `details_json` 的 failureReasons 分桶：
     * HTTP_403 / HTTP_404 / HTTP_429 / HTTP_5XX / HTTP_4XX / TLS_ERROR / TIMEOUT / INVALID_CONTENT /
     * NETWORK_ERROR。null = 不是下载失败或未分类。
     */
    val downloadFailureCategory: String? = null
)

/** c10（I-3）：内容已取到但其中没有邮箱的失败原因（PDF 文本与 PMC 全文都属于这一类）。 */
internal val CONTENT_OBTAINED_WITHOUT_EMAIL_REASONS = setOf("NO_EMAIL_IN_FULLTEXT", "NO_EMAIL_IN_TEXT")

/**
 * c10（I-3）：「是否取到全文内容」的唯一读法 —— 适配器显式声明的 [EmailExtractionOutcome.fulltextObtained]
 * 优先；为 null（旧适配器未声明）时沿用改动前的推导：抽到邮箱、无 failureReason、或失败原因属于
 * 「内容已取到但没有邮箱」都算已获取。
 *
 * 注意：显式声明为 true 而邮箱为空的原因还包括 `NO_EMAIL_IN_HTML` —— 取到的 HTML 只保证是**该地址返回的内容**，
 * 不保证就是论文全文。
 */
internal fun EmailExtractionOutcome.resolvedFulltextObtained(): Boolean {
    fulltextObtained?.let { return it }
    if (emails.isNotEmpty()) return true
    return failureReason == null || failureReason in CONTENT_OBTAINED_WITHOUT_EMAIL_REASONS
}
