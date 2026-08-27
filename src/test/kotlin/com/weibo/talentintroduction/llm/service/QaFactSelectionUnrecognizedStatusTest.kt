package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.llm.config.AskEnumeratorProperties
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito

class QaFactSelectionUnrecognizedStatusTest {
    // 照抄 QaFactSelectionServiceTest:20-22 的 mock 形态（repository mock + service 构造）。
    private val repository = Mockito.mock(QaRuleRepository::class.java)

    // 照抄 QaFactSelectionServiceTest:1427-1445 的 ask(...) fixture 构造器。
    private fun ask(label: String, quote: String, mail: String): EnumeratedAsk {
        val start = mail.indexOf(quote)
        require(start >= 0) { "quote must be a substring of the mail: $quote" }
        return EnumeratedAsk(label = label, quote = quote, originalRange = start until start + quote.length)
    }

    private fun serviceWith(enumeration: AskEnumeration): QaFactSelectionService {
        val enumerator = Mockito.mock(InboundAskEnumerator::class.java)
        Mockito.`when`(enumerator.enumerate(anyString())).thenReturn(enumeration)
        return QaFactSelectionService(repository, enumerator, AskEnumeratorProperties())
    }

    private fun groundedRule(id: Long) = QaRule(
        id = id,
        categoryId = 1,
        keywords = "next,steps",
        replySubject = null,
        replyBody = "The next steps are provided in the application guide.",
        answerBody = "The next steps are provided in the application guide.",
        replyPolicy = QaReplyPolicy.AUTO.name
    )

    @Test
    fun `unrecognized ask caps grounded status to partial when enumerator available`() {
        // I-1: matchIntents 命中的意图全部 SUPPORTED（自然 status = GROUNDED），
        // 同时喂一个落在 request 范围内、与任何 alias span 不重叠的 EnumeratedAsk
        // → item.status 封顶为 PARTIAL。
        val mail = "What are the next steps after I apply?"
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(groundedRule(10)))
        val enumeration = AskEnumeration(true, listOf(ask("After application", "after I apply", mail)))

        val resolved = serviceWith(enumeration).selectForWorkbench(mail, null, null, true)

        val item = resolved.requestFacts.single()
        assertEquals(RequestGroundingStatus.PARTIAL, item.status)
        assertEquals(1, item.unrecognizedAsks.size)
    }

    @Test
    fun `unavailable enumerator never caps status`() {
        // I-3: 同 I-1 的构造，但 AskEnumeration(available = false, ...) →
        // item.status == GROUNDED（逐字回到本计划落地前）。
        val mail = "What are the next steps after I apply?"
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(groundedRule(10)))
        val enumeration = AskEnumeration(false, listOf(ask("After application", "after I apply", mail)))

        val resolved = serviceWith(enumeration).selectForWorkbench(mail, null, null, true)

        val item = resolved.requestFacts.single()
        assertEquals(RequestGroundingStatus.GROUNDED, item.status)
        assertEquals(1, item.unrecognizedAsks.size)
    }

    @Test
    fun `cap never downgrades partial status`() {
        // I-1: 只封顶 GROUNDED→PARTIAL；今日结论为 PARTIAL 的保持不变（只封顶，不进一步下调）。
        // "next steps" → application.next_stages（有规则 → SUPPORTED）；
        // "form of collaboration" → collaboration.form（无规则 → MISSING）；
        // SUPPORTED + MISSING → 自然 status = PARTIAL；枚举器可用且存在未识别诉求也不下调。
        val mail = "Could you tell me about the next steps and the form of collaboration?"
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(groundedRule(10)))
        val enumeration = AskEnumeration(true, listOf(ask("Preamble", "Could you tell me", mail)))

        val resolved = serviceWith(enumeration).selectForWorkbench(mail, null, null, true)

        val item = resolved.requestFacts.single()
        assertEquals(RequestGroundingStatus.PARTIAL, item.status)
        assertTrue(item.unrecognizedAsks.isNotEmpty())
    }
}
