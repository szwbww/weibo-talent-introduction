package com.weibo.talentintroduction.rag

import com.weibo.talentintroduction.rag.service.RagPhraseMatcher
import com.weibo.talentintroduction.rag.service.RagTextNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 计划 02 (T5 + 验收标准 I-7): 归一化与短语命中 —— 与脚本逐字等价的字面断言。
 *
 * I-7 violation consequence 的钉死点：
 * - 首尾各补一个空格 → `paid` 不能命中 `unpaid`（否则静默分叉，预筛结果不同但无报错）；
 * - token 正则硬编码 ASCII `[a-z0-9]+` → 非 ASCII 字符（é、× 等）不参与分词
 *   （若用 Unicode 词类会与脚本分叉）。
 */
class RagTextNormalizerTest {

    private val normalizer = RagTextNormalizer()
    private val matcher = RagPhraseMatcher(normalizer)

    @Test
    fun `normalize lowercases joins tokens with single space and pads both sides - I-7`() {
        assertEquals(" compensation please ", normalizer.normalize("Compensation, please?"))
        // 空串：join 空词元 + 首尾空格 = 两个空格（与脚本 " ".join([]) 一致）
        assertEquals("  ", normalizer.normalize(""))
        // 换行与多空格折叠为单空格
        assertEquals(" the offer ", normalizer.normalize("  THE\n\noffer  "))
    }

    @Test
    fun `normalize token regex is hardcoded ascii a-z0-9 never unicode classes - I-7`() {
        // é/× 不是 [a-z0-9]：词元被截断在 ASCII 边界（脚本 _TOKEN_RE 同构）。
        assertEquals(" caf 2 r sum ", normalizer.normalize("Café, 2× résumé"))
        assertEquals(setOf("caf", "2", "r", "sum"), normalizer.tokens("Café, 2× résumé"))
        // tokens 去重 + 小写折叠
        assertEquals(
            setOf("the", "offer", "is", "great"),
            normalizer.tokens("The Offer IS great, the offer!")
        )
    }

    @Test
    fun `containsAny pads words so paid cannot match unpaid - I-7`() {
        assertFalse(matcher.containsAny("I am unpaid", listOf("paid")))
        assertTrue(matcher.containsAny("I am paid", listOf("paid")))
        // 短语集合为空时恒 false（与脚本 any(()) 一致）
        assertFalse(matcher.containsAny("anything at all", emptyList()))
        // 子串命中语义：多词短语按归一化后的连续词元命中
        assertTrue(matcher.containsAny("Could you tell me more details from you?", listOf("more details")))
        assertFalse(matcher.containsAny("no detail here", listOf("more details")))
    }
}
