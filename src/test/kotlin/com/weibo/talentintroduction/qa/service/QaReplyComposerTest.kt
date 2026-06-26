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
    fun `multiple matches include section titles when present`() {
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

        assertEquals(
            listOf(
                QaReplyComposer.GREETING,
                "Funding & timeline\nFunding answer",
                "Meeting arrangement\nMeeting answer",
                QaReplyComposer.CLOSING
            ).joinToString("\n\n"),
            result.replyBody
        )
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
        assertTrue(result.replyBody.contains("Meeting arrangement\nMeeting answer"))
        assertFalse(result.replyBody.contains("Funding & timeline"))
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
}
