package com.weibo.talentintroduction.mail.service

/**
 * 入站 Message-ID 读侧归一化工具（read-side only，见 K-vendor-message-id-prefix）。
 *
 * 腾讯企业邮中继会在投递时改写外发 Message-ID，给 local-part 加 16 位大写十六进制 + '+' 前缀
 * （观测事实，无官方文档，见 K-vendor-message-id-prefix.md 两个样本）。`mail_record.message_id`
 * 落库的是交给中继**之前**的值，因此入站 `in_reply_to` / 退信 `originalMessageId`（引用的是投递后
 * 的值）在精确匹配前需要构造有限候选。
 *
 * I-1：仅剥离 `^[0-9A-F]{16}\+`，剥离后**不做任何我方格式假设**（不识别任何我方已知格式）。
 */
object MessageIdNormalizer {

    /** 16 位大写十六进制 + '+'，严格锚定观测格式（I-1）。 */
    private val VENDOR_PREFIX = Regex("^[0-9A-F]{16}\\+")

    /**
     * 规范化：trim；空白返回 null；含 `<` 时取**第一个** `<...>` 片段（含尖括号，In-Reply-To
     * 理论可含多个 msg-id）；否则用 `<` + 原值 + `>` 包裹。结果内部再 trim。
     */
    fun canonicalize(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        return if (trimmed.contains('<')) {
            val start = trimmed.indexOf('<')
            val end = trimmed.indexOf('>', start)
            if (end > start) trimmed.substring(start, end + 1).trim() else trimmed
        } else {
            "<$trimmed>"
        }
    }

    /**
     * 有界前缀剥离：取第一个尖括号片段的内容，按**第一个** `@` 切 local-part 与 domain；
     * 对 local-part 应用 [VENDOR_PREFIX] 剥离；无 `@` 时对整体应用；重新包裹尖括号。
     * 剥离后 local-part 为空则返回入参原值（避免产出 `<@domain>`）。不匹配则原样返回。
     */
    fun stripVendorPrefix(bracketed: String): String {
        val start = bracketed.indexOf('<')
        val end = bracketed.indexOf('>', start)
        val inner = if (end > start) bracketed.substring(start + 1, end) else bracketed
        val atIndex = inner.indexOf('@')
        val localPart = if (atIndex >= 0) inner.substring(0, atIndex) else inner
        val domain = if (atIndex >= 0) inner.substring(atIndex) else ""
        val stripped = VENDOR_PREFIX.replaceFirst(localPart, "")
        if (stripped.isEmpty()) return bracketed
        return "<$stripped$domain>"
    }

    /**
     * I-4：按固定顺序产出候选 —— ① 原值(trim) ② [canonicalize] ③ [stripVendorPrefix](canonicalize)，
     * 过滤空白，`distinct()` 保序去重。调用方逐个精确查询，首个命中即返回。
     */
    fun candidatesFor(raw: String?): List<String> {
        val canonical = canonicalize(raw)
        return listOf(
            raw?.trim(),
            canonical,
            canonical?.let { stripVendorPrefix(it) }
        )
            .filterNotNull()
            .filter { it.isNotBlank() }
            .distinct()
    }
}
