package com.weibo.talentintroduction.rag.service

import com.weibo.talentintroduction.rag.domain.RagFact

/**
 * 计划 03 (T4): VERBATIM 令牌的确定性渲染 —— 与
 * `scripts/spike_deepseek_reply.py` 的 `render_verbatim_facts()` /
 * `verbatim_violations()` / `fact_render_token()` 逐字等价（D-2）。
 *
 * - I-15: 同一令牌重复出现只保留第一次；缺失令牌按三级回退插入
 *   （前面最近的已在正文令牌之后 → 后面最近的已在正文令牌之前 →
 *   第一个段落之后 / 正文无空行时插到最前），插入令牌两侧由落点边界提供
 *   `\n\n` 分段（令牌自成一段，与脚本逐字一致）。
 * - I-14: 替换完成后逐条断言 VERBATIM answer 是最终正文的子串，
 *   缺失的 fact_code 由 [violations] 返回，编排层抛
 *   `RagComposeException(422, "RAG_VERBATIM_MISSING")` —— 不降级、不 fallback。
 */
object RagVerbatimRenderer {

    /** 脚本 `fact_render_token()`：`{{FACT:<fact_code>}}`。 */
    fun factRenderToken(factCode: String): String = "{{FACT:$factCode}}"

    /**
     * I-15 渲染：对 [draft]（模型正文）做去重 → 缺失插入 → 逐字替换，
     * 返回最终模型正文（首尾 trim）。无 VERBATIM 事实时原样返回 [draft]。
     */
    fun render(draft: String, retrieved: List<RagFact>): String {
        val verbatimFacts = retrieved.filter { it.renderMode == "VERBATIM" }
        if (verbatimFacts.isEmpty()) {
            return draft
        }
        var rendered = draft.trim()
        val tokens = verbatimFacts.map { factRenderToken(it.factCode) }

        // ① 同一令牌多次出现：只保留第一次，其余删除（含后续循环对余串的去重）。
        for (token in tokens) {
            val first = rendered.indexOf(token)
            if (first >= 0) {
                val tailStart = first + token.length
                rendered = rendered.substring(0, tailStart) +
                    rendered.substring(tailStart).replace(token, "")
            }
        }

        // ② 缺失令牌三级回退插入（顺序固定，见 I-15）。
        for (index in tokens.indices) {
            val token = tokens[index]
            if (token in rendered) {
                continue
            }
            val previous = tokens.take(index).asReversed().firstOrNull { it in rendered }
            if (previous != null) {
                val insertAt = rendered.indexOf(previous) + previous.length
                rendered = rendered.substring(0, insertAt) + "\n\n" + token + rendered.substring(insertAt)
                continue
            }
            val following = tokens.drop(index + 1).firstOrNull { it in rendered }
            if (following != null) {
                val insertAt = rendered.indexOf(following)
                rendered = rendered.substring(0, insertAt) + token + "\n\n" + rendered.substring(insertAt)
                continue
            }
            val firstParagraphEnd = rendered.indexOf("\n\n")
            if (firstParagraphEnd >= 0) {
                val insertAt = firstParagraphEnd + 2
                rendered = rendered.substring(0, insertAt) + token + "\n\n" + rendered.substring(insertAt)
            } else {
                rendered = token + "\n\n" + rendered
            }
        }

        // ③ 逐字替换：令牌 → 审定原文。
        verbatimFacts.zip(tokens).forEach { (fact, token) ->
            rendered = rendered.replace(token, fact.answer)
        }
        return rendered.trim()
    }

    /**
     * I-14 审计：返回 [draft]（模型**原稿**，渲染前）中完全没出现的 VERBATIM
     * 令牌对应的 fact_code，顺序 = retrieved 顺序。编排层用它做「原稿零令牌
     * （把审定原文整体改写掉了）→ 整次失败」的判定 —— 计划验收
     * `verbatimMissingFailsWholeCompose` 要求的语义；原稿仍有 ≥1 个令牌时，
     * 缺失项交给 [render] 的 I-15 插入救回，不在此判定。
     */
    fun missingTokens(draft: String, retrieved: List<RagFact>): List<String> =
        retrieved
            .filter { it.renderMode == "VERBATIM" }
            .filter { factRenderToken(it.factCode) !in draft }
            .map { it.factCode }

    /**
     * I-14 校验：返回 answer 未作为子串出现在 [rendered] 中的 VERBATIM
     * fact_code 列表（顺序 = retrieved 顺序）。空列表 = 全部到位。
     */
    fun violations(rendered: String, retrieved: List<RagFact>): List<String> =
        retrieved
            .filter { it.renderMode == "VERBATIM" }
            .filter { it.answer !in rendered }
            .map { it.factCode }
}
