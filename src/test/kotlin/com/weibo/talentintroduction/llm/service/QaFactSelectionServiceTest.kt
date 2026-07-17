package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class QaFactSelectionServiceTest {
    private val repository = Mockito.mock(QaRuleRepository::class.java)
    private val service = QaFactSelectionService(repository)

    private fun rule(
        id: Long,
        keywords: String,
        answerBody: String,
        priority: Int = 100,
        replyPolicy: String = QaReplyPolicy.AUTO.name
    ) = QaRule(
        id = id,
        categoryId = 1,
        keywords = keywords,
        replySubject = null,
        replyBody = answerBody,
        answerBody = answerBody,
        priority = priority,
        replyPolicy = replyPolicy,
        enabled = true
    )

    @Test
    fun `assigns keyword matched rules without coverage keys`() {
        val selectionRule = rule(
            id = 10,
            keywords = "selection,selected,criteria",
            answerBody = "Researchers are selected through a structured review."
        )
        val matchingRule = rule(
            id = 11,
            keywords = "matching,matched,enterprise",
            answerBody = "Matching pairs researchers with partner enterprises."
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(selectionRule, matchingRule))

        val resolved = service.select(
            inboundText = """
                How are researchers selected?
                How are researchers matched with enterprises?
            """.trimIndent(),
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        assertTrue(resolved.sendQaRuleIds.containsAll(listOf(10L, 11L)))
        assertFalse(resolved.requestFacts.any { it.intents.any { intent -> intent.requiredCoverageKeys.isNotEmpty() } })
    }

    @Test
    fun `each rule appears in at most one intent evidence set per request`() {
        val sharedRule = rule(
            id = 20,
            keywords = "responsibilities,deliverables,role",
            answerBody = "Role expectations include research delivery."
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(sharedRule))

        val resolved = service.select(
            inboundText = "What are my responsibilities and deliverables?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val evidenceCounts = resolved.requestFacts
            .flatMap { it.intents }
            .flatMap { it.evidenceRuleIds }
            .groupingBy { it }
            .eachCount()
        assertTrue(evidenceCounts.values.all { it <= 1 })
    }

    @Test
    fun `explicit NEVER rule is rejected`() {
        Mockito.`when`(repository.findById(99L)).thenReturn(
            Optional.of(
                rule(
                    id = 99,
                    keywords = "fee",
                    answerBody = "Internal fee note.",
                    replyPolicy = QaReplyPolicy.NEVER.name
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.select(
                inboundText = "What is the fee?",
                selectedRuleIds = listOf(99L),
                researchProfileSufficient = true
            )
        }
    }

    @Test
    fun `unsupported request yields empty send ids and blocked readiness input`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())

        val resolved = service.select(
            inboundText = "Can you guarantee 10 million RMB?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        assertEquals(emptyList<Long>(), resolved.sendQaRuleIds)
        assertTrue(resolved.requestFacts.all { it.status == RequestGroundingStatus.UNSUPPORTED })
    }

    @Test
    fun `explicit rules must match at least one extracted request`() {
        val rule = rule(id = 3, keywords = "salary", answerBody = "Salary facts")
        Mockito.`when`(repository.findById(3L)).thenReturn(Optional.of(rule))

        assertThrows(IllegalArgumentException::class.java) {
            service.select(
                inboundText = "Unrelated text",
                selectedRuleIds = listOf(3L),
                researchProfileSufficient = true
            )
        }
    }

    @Test
    fun `explicit rules never bypass keyword matching per request`() {
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.select(
            inboundText = "- Salary?\n- Visa?",
            selectedRuleIds = listOf(1L),
            researchProfileSufficient = true
        )

        assertEquals(listOf(1L), resolved.requestFacts[0].factRuleIds)
        assertTrue(resolved.requestFacts[1].factRuleIds.isEmpty())
        assertEquals(listOf(1L), resolved.sendQaRuleIds)
    }

    @Test
    fun `explicit mixed matching and non-matching rules is rejected`() {
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val feeRule = rule(id = 2, keywords = "fee", answerBody = "Fee body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(feeRule))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.select(
                inboundText = "What is salary?",
                selectedRuleIds = listOf(1L, 2L),
                researchProfileSufficient = true
            )
        }
        assertTrue(ex.message!!.contains("2"))
    }

    @Test
    fun `send ids follow request then priority order`() {
        val lowPriority = rule(id = 1, keywords = "salary", answerBody = "Salary A", priority = 200)
        val highPriority = rule(id = 2, keywords = "visa", answerBody = "Visa B", priority = 50)
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(lowPriority, highPriority))

        val resolved = service.select(
            inboundText = """
                What is the salary support?
                What is the visa process?
            """.trimIndent(),
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        assertEquals(listOf(1L, 2L), resolved.sendQaRuleIds)
    }
}
