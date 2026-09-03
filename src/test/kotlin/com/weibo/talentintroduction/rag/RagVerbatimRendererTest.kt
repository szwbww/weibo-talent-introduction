package com.weibo.talentintroduction.rag

import com.weibo.talentintroduction.rag.domain.RagFact
import com.weibo.talentintroduction.rag.service.RagVerbatimRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 计划 03 (T7): 令牌逐字渲染的 I-15 平价 —— `render()` 与脚本
 * `render_verbatim_facts()` 逐字等价：重复令牌只保留首次；缺失令牌三级回退
 * 插入（前一个令牌后 / 后一个令牌前 / 首段后，正文无空行时插最前），
 * 插入令牌两侧为 `\n\n`；末尾逐字替换 + I-14 violations 判定。
 */
class RagVerbatimRendererTest {

    @Test
    fun `missing token is inserted right after the nearest previous token already in body`() {
        val facts = listOf(
            verbatimFact("KB-PROG-002", "The programme overview answer."),
            verbatimFact("KB-FUND-033", "The salary and funding answer.")
        )
        // 原稿只写了 KB-PROG-002 的令牌：KB-FUND-033 缺失 →
        // 插到「正文中它前面最近的令牌」KB-PROG-002 之后。
        val draft = "Intro paragraph.\n\n{{FACT:KB-PROG-002}}\n\nClosing paragraph."
        val rendered = RagVerbatimRenderer.render(draft, facts)

        assertEquals(
            "Intro paragraph.\n\nThe programme overview answer.\n\n" +
                "The salary and funding answer.\n\nClosing paragraph.",
            rendered
        )
        // 插入点两侧为 \n\n（令牌自成一段）。
        assertTrue(rendered.contains("overview answer.\n\nThe salary and funding answer.\n\n"))
        assertTrue(RagVerbatimRenderer.violations(rendered, facts).isEmpty())
    }

    @Test
    fun `missing token is inserted right before the nearest following token already in body`() {
        val facts = listOf(
            verbatimFact("KB-PROG-002", "The programme overview answer."),
            verbatimFact("KB-FUND-033", "The salary and funding answer.")
        )
        // 原稿只写了 KB-FUND-033 的令牌：KB-PROG-002 缺失、前面无令牌 →
        // 插到「正文中它后面最近的令牌」KB-FUND-033 之前。
        val draft = "Intro paragraph.\n\n{{FACT:KB-FUND-033}}\n\nClosing paragraph."
        val rendered = RagVerbatimRenderer.render(draft, facts)

        assertEquals(
            "Intro paragraph.\n\nThe programme overview answer.\n\n" +
                "The salary and funding answer.\n\nClosing paragraph.",
            rendered
        )
        assertTrue(rendered.contains("\n\nThe programme overview answer.\n\nThe salary and funding answer.\n"))
    }

    @Test
    fun `missing token with no neighbouring tokens is inserted after the first paragraph`() {
        val facts = listOf(
            verbatimFact("KB-FUND-033", "The salary and funding answer.")
        )
        // 原稿完全没写令牌，且有段落分隔（首个 \n\n）：插到第一个段落之后。
        val draft = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."
        val rendered = RagVerbatimRenderer.render(draft, facts)

        assertEquals(
            "First paragraph.\n\nThe salary and funding answer.\n\n" +
                "Second paragraph.\n\nThird paragraph.",
            rendered
        )
    }

    @Test
    fun `missing token with a single unbroken paragraph is inserted at the very front`() {
        val facts = listOf(
            verbatimFact("KB-FUND-033", "The salary and funding answer.")
        )
        // 正文没有空行 → 插到最前（令牌 + \n\n + 原正文）。
        val draft = "A single unbroken paragraph without blank lines."
        val rendered = RagVerbatimRenderer.render(draft, facts)

        assertEquals(
            "The salary and funding answer.\n\nA single unbroken paragraph without blank lines.",
            rendered
        )
    }

    @Test
    fun `duplicate tokens keep only the first occurrence`() {
        val facts = listOf(
            verbatimFact("KB-FUND-033", "The salary and funding answer.")
        )
        val draft = "{{FACT:KB-FUND-033}}\n\n{{FACT:KB-FUND-033}}"
        val rendered = RagVerbatimRenderer.render(draft, facts)

        // 只保留第一次出现的令牌；最终 answer 恰好出现一次。
        assertEquals(1, rendered.split("The salary and funding answer.").size - 1)
        assertFalse(rendered.contains("{{FACT:"))
    }

    @Test
    fun `render without verbatim facts returns the draft unchanged`() {
        val composeFact = RagFact(
            factCode = "KB-APP-001", area = "APP", seq = 1, title = "t", category = "c",
            questionVariants = "", keywords = "", answer = "a", coverageKeys = "",
            replyPolicy = "", status = "APPROVED", riskLevel = "LOW", renderMode = "COMPOSE",
            sourceRefs = "", enabled = true, sortOrder = 1
        )
        val draft = "Model prose only."
        assertEquals(draft, RagVerbatimRenderer.render(draft, listOf(composeFact)))
    }

    private fun verbatimFact(code: String, answer: String): RagFact = RagFact(
        factCode = code,
        area = code.substring(3, code.indexOf('-', 3)),
        seq = code.substringAfterLast('-').toInt(),
        title = "t",
        category = "c",
        questionVariants = "",
        keywords = "",
        answer = answer,
        coverageKeys = "",
        replyPolicy = "",
        status = "APPROVED",
        riskLevel = "MEDIUM",
        renderMode = "VERBATIM",
        sourceRefs = "",
        enabled = true,
        sortOrder = 0
    )
}
