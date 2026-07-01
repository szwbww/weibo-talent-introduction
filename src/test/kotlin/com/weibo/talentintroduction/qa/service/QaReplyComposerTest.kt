package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.qa.domain.QaRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QaReplyComposerTest {
    private val composeOrder = mapOf(
        1L to 30,
        2L to 10,
        3L to 60
    )

    @Test
    fun `single match returns rule subject and body unchanged`() {
        val rule = QaRule(
            id = 1,
            categoryId = 1,
            keywords = "salary",
            replySubject = "Funding support",
            replyBody = "Funding answer"
        )

        val result = QaReplyComposer.compose(listOf(QaRuleMatch(rule, 1)), composeOrder)

        assertEquals("Funding support", result.replySubject)
        assertEquals("Funding answer", result.replyBody)
    }

    @Test
    fun `multiple matches join bodies in compose order`() {
        val fundingRule = QaRule(
            id = 10,
            categoryId = 1,
            keywords = "salary",
            priority = 80,
            replySubject = "Funding support",
            replyBody = "Funding answer"
        )
        val meetingRule = QaRule(
            id = 20,
            categoryId = 3,
            keywords = "meeting",
            priority = 50,
            replySubject = "Meeting arrangement",
            replyBody = "Meeting answer"
        )

        val result = QaReplyComposer.compose(
            listOf(
                QaRuleMatch(meetingRule, 1),
                QaRuleMatch(fundingRule, 2)
            ),
            composeOrder
        )

        assertEquals("Funding support", result.replySubject)
        assertEquals(
            listOf(
                QaReplyComposer.GREETING,
                "Funding answer",
                "Meeting answer",
                QaReplyComposer.CLOSING
            ).joinToString("\n\n"),
            result.replyBody
        )
    }

    @Test
    fun `multiple matches omit section titles from composed body`() {
        val fundingRule = QaRule(
            id = 10,
            categoryId = 1,
            keywords = "salary",
            priority = 80,
            replySubject = "Funding support",
            replyBody = "Funding answer",
            sectionTitle = "Funding & timeline"
        )
        val meetingRule = QaRule(
            id = 20,
            categoryId = 3,
            keywords = "meeting",
            priority = 50,
            replySubject = "Meeting arrangement",
            replyBody = "Meeting answer",
            sectionTitle = "Meeting arrangement"
        )

        val result = QaReplyComposer.compose(
            listOf(
                QaRuleMatch(meetingRule, 1),
                QaRuleMatch(fundingRule, 2)
            ),
            composeOrder
        )

        assertFalse(result.replyBody.contains("Funding & timeline"))
        assertFalse(result.replyBody.contains("Meeting arrangement"))
        assertTrue(result.replyBody.contains("Funding answer"))
        assertTrue(result.replyBody.contains("Meeting answer"))
        assertEquals(
            listOf(
                QaReplyComposer.GREETING,
                "Funding answer",
                "Meeting answer",
                QaReplyComposer.CLOSING
            ).joinToString("\n\n"),
            result.replyBody
        )
    }

    @Test
    fun `duplicate section titles appear zero times in composed body`() {
        val sharedTitle = "Role & work style"
        val ruleOne = QaRule(
            id = 10,
            categoryId = 1,
            keywords = "role",
            priority = 80,
            replySubject = "Role A",
            replyBody = "Role answer one",
            sectionTitle = sharedTitle
        )
        val ruleTwo = QaRule(
            id = 20,
            categoryId = 1,
            keywords = "style",
            priority = 50,
            replySubject = "Role B",
            replyBody = "Role answer two",
            sectionTitle = sharedTitle
        )

        val result = QaReplyComposer.compose(
            listOf(QaRuleMatch(ruleOne, 1), QaRuleMatch(ruleTwo, 1)),
            composeOrder
        )

        assertFalse(result.replyBody.contains(sharedTitle))
        assertTrue(result.replyBody.contains("Role answer one"))
        assertTrue(result.replyBody.contains("Role answer two"))
    }

    @Test
    fun `null section title omits heading line without error`() {
        val fundingRule = QaRule(
            id = 10,
            categoryId = 1,
            keywords = "salary",
            priority = 80,
            replySubject = "Funding support",
            replyBody = "Funding answer",
            sectionTitle = null
        )
        val meetingRule = QaRule(
            id = 20,
            categoryId = 3,
            keywords = "meeting",
            priority = 50,
            replySubject = "Meeting arrangement",
            replyBody = "Meeting answer",
            sectionTitle = "Meeting arrangement"
        )

        val result = QaReplyComposer.compose(
            listOf(QaRuleMatch(fundingRule, 1), QaRuleMatch(meetingRule, 1)),
            composeOrder
        )

        assertTrue(result.replyBody.contains("Funding answer"))
        assertTrue(result.replyBody.contains("Meeting answer"))
        assertFalse(result.replyBody.contains("Meeting arrangement"))
    }

    @Test
    fun `same category ties broken by priority then id`() {
        val lowerPriority = QaRule(
            id = 2,
            categoryId = 1,
            keywords = "deadline",
            priority = 10,
            replySubject = "Deadline",
            replyBody = "Deadline answer"
        )
        val higherPriority = QaRule(
            id = 1,
            categoryId = 1,
            keywords = "salary",
            priority = 80,
            replySubject = "Funding support",
            replyBody = "Funding answer"
        )

        val result = QaReplyComposer.compose(
            listOf(
                QaRuleMatch(higherPriority, 1),
                QaRuleMatch(lowerPriority, 1)
            ),
            composeOrder
        )

        assertEquals(
            listOf(
                QaReplyComposer.GREETING,
                "Deadline answer",
                "Funding answer",
                QaReplyComposer.CLOSING
            ).joinToString("\n\n"),
            result.replyBody
        )
    }

    @Test
    fun `selectPrimary prefers more matched keywords`() {
        val fewer = QaRuleMatch(
            QaRule(id = 1, categoryId = 2, keywords = "program", priority = 10, replySubject = "A", replyBody = "A"),
            matchedKeywordCount = 1
        )
        val more = QaRuleMatch(
            QaRule(id = 2, categoryId = 1, keywords = "salary,funding", priority = 80, replySubject = "B", replyBody = "B"),
            matchedKeywordCount = 2
        )

        val primary = QaReplyComposer.selectPrimary(listOf(fewer, more))

        assertEquals(2, primary.rule.id)
    }

    @Test
    fun `selectPrimary uses lower priority value as tiebreaker`() {
        val highPriorityNumber = QaRuleMatch(
            QaRule(id = 2, categoryId = 1, keywords = "a", priority = 80, replySubject = "B", replyBody = "B"),
            matchedKeywordCount = 1
        )
        val lowPriorityNumber = QaRuleMatch(
            QaRule(id = 1, categoryId = 1, keywords = "b", priority = 10, replySubject = "A", replyBody = "A"),
            matchedKeywordCount = 1
        )

        val primary = QaReplyComposer.selectPrimary(listOf(highPriorityNumber, lowPriorityNumber))

        assertEquals(1, primary.rule.id)
    }

    @Test
    fun `composeInOperatorOrder preserves operator sequence`() {
        val ruleB = QaRuleMatch(
            QaRule(id = 20, categoryId = 1, keywords = "b", priority = 10, replySubject = "B", replyBody = "Body B"),
            matchedKeywordCount = 1
        )
        val ruleA = QaRuleMatch(
            QaRule(id = 10, categoryId = 2, keywords = "a", priority = 80, replySubject = "A", replyBody = "Body A"),
            matchedKeywordCount = 1
        )

        val result = QaReplyComposer.composeInOperatorOrder(
            matches = listOf(ruleB, ruleA),
            greeting = QaReplyComposer.GREETING,
            closing = QaReplyComposer.CLOSING
        )

        val bodyBIndex = result.replyBody.indexOf("Body B")
        val bodyAIndex = result.replyBody.indexOf("Body A")
        assertTrue(bodyBIndex >= 0)
        assertTrue(bodyAIndex > bodyBIndex)
    }

    @Test
    fun `composeInOperatorOrder assembles frame in fixed order for single rule`() {
        val rule = QaRuleMatch(
            QaRule(id = 1, categoryId = 1, keywords = "x", replySubject = "Subj", replyBody = "Body only"),
            matchedKeywordCount = 1
        )

        val result = QaReplyComposer.composeInOperatorOrder(
            matches = listOf(rule),
            salutation = "Dear Professor,",
            ack = "Thanks for CV.",
            greeting = "Hello.",
            closing = "Bye."
        )

        val salIndex = result.replyBody.indexOf("Dear Professor,")
        val ackIndex = result.replyBody.indexOf("Thanks for CV.")
        val greetIndex = result.replyBody.indexOf("Hello.")
        val bodyIndex = result.replyBody.indexOf("Body only")
        val closeIndex = result.replyBody.indexOf("Bye.")
        assertTrue(salIndex >= 0)
        assertTrue(salIndex < ackIndex)
        assertTrue(ackIndex < greetIndex)
        assertTrue(greetIndex < bodyIndex)
        assertTrue(bodyIndex < closeIndex)
    }
}
