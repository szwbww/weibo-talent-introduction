package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class QaRuleManagementServiceTest {
    private val categoryRepository = Mockito.mock(QaCategoryRepository::class.java)
    private val ruleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val service = QaRuleManagementService(categoryRepository, ruleRepository)

    @Test
    fun `creates qa rule when category exists`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as QaRule }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "funding, salary",
                matchMode = "any",
                priority = 10,
                replySubject = "Funding support",
                replyBody = "The program may provide funding support."
            )
        )

        assertEquals(1L, created.categoryId)
        assertEquals("ANY", created.matchMode)
        assertEquals(10, created.priority)
    }

    @Test
    fun `disables qa rule`() {
        Mockito.`when`(ruleRepository.findById(2L)).thenReturn(
            Optional.of(rule(id = 2L, enabled = true))
        )
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as QaRule }

        val disabled = service.setRuleEnabled(2L, false)

        assertFalse(disabled.enabled)
    }

    private fun rule(id: Long, enabled: Boolean): QaRule =
        QaRule(
            id = id,
            categoryId = 1L,
            keywords = "funding",
            matchMode = "ANY",
            priority = 100,
            replySubject = "Funding support",
            replyBody = "The program may provide funding support.",
            enabled = enabled
        )
}
