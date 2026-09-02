package com.weibo.talentintroduction.rag.service

/**
 * 计划 02 (T1): 与 `scripts/spike_deepseek_reply.py` **逐字等价**的分词与归一化（I-7）。
 *
 * `normalize(value)` = 转小写 → 按正则 `[a-z0-9]+` 取出全部词元 → 用单个空格连接 →
 * 首尾各补一个空格。正则是硬编码的 **ASCII 类**（`Regex("[a-z0-9]+")`，与脚本
 * `_TOKEN_RE = re.compile(r"[a-z0-9]+")` 逐字一致）；**绝不使用 Unicode 词类**，
 * 否则非 ASCII 字符会参与分词、与脚本静默分叉。
 *
 * 短语命中判定恒为 `normalize(text).contains(normalize(phrase))` —— 首尾空格使
 * `paid` 无法命中 `unpaid`（I-7 violation consequence 的钉死点）。
 * 词重叠打分（`RagPrefilterService.lexicalScore` 第三项）用同一正则产出的词元集合。
 *
 * Applies to: 本类、`RagPhraseMatcher`、`RagPrefilterService` 的全部打分与匹配。
 */
class RagTextNormalizer {

    /** I-7: `" " + " ".join(_TOKEN_RE.findall(value.lower())) + " "`。 */
    fun normalize(value: String): String =
        " " + TOKEN_REGEX.findAll(value.lowercase()).joinToString(" ") { it.value } + " "

    /** I-7: 同一正则产出的词元集合（脚本 `set(_TOKEN_RE.findall(...))`）。 */
    fun tokens(value: String): Set<String> =
        TOKEN_REGEX.findAll(value.lowercase()).map { it.value }.toSet()

    companion object {
        /** I-7: 与脚本 `_TOKEN_RE` 逐字一致；ASCII 类，禁止替换为 Unicode 词类。 */
        val TOKEN_REGEX: Regex = Regex("[a-z0-9]+")
    }
}
