package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class QaMatchServiceTest {
    private val repository = Mockito.mock(QaRuleRepository::class.java)
    private val service = QaMatchService(repository)

    @Test
    fun `matches first enabled rule by keyword`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary,subsidy",
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                )
            )
        )

        val result = service.match("Could you explain the salary support?")

        assertEquals(1, result?.ruleId)
        assertEquals("Funding support", result?.replySubject)
    }

    @Test
    fun `returns null when no keyword matches`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 2,
                    categoryId = 2,
                    keywords = "deadline",
                    replySubject = "Deadline",
                    replyBody = "Deadline answer"
                )
            )
        )

        assertNull(service.match("Thank you for your email."))
    }

    @Test
    fun `prefers rule with more matched keywords before priority`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "project,program",
                    priority = 10,
                    replySubject = "Project",
                    replyBody = "Project answer"
                ),
                QaRule(
                    id = 2,
                    categoryId = 2,
                    keywords = "salary,funding",
                    priority = 80,
                    replySubject = "Funding support",
                    replyBody = "Funding answer"
                )
            )
        )

        val result = service.match("Could you explain the salary and funding support for this program?")

        assertEquals(2, result?.ruleId)
        assertEquals("Funding support", result?.replySubject)
    }
}
