package com.weibo.talentintroduction.qa.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class QaRuleManagementServiceTest {
    private val categoryRepository = Mockito.mock(QaCategoryRepository::class.java)
    private val ruleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val mailVariableService = MailVariableService(
        Mockito.mock(ExpertSearchService::class.java),
        MailComposeTemplateService(
            Mockito.mock(MailComposeTemplateRepository::class.java),
            Mockito.mock(MailComposeTemplateBlockRepository::class.java),
            Mockito.mock(QaRuleRepository::class.java),
            Mockito.mock(ReplySnippetRepository::class.java),
            ObjectMapper(),
            Mockito.mock(MailVariableService::class.java),
            Mockito.mock(ExpertContactRepository::class.java),
            Mockito.mock(MailSenderAccountService::class.java)
        )
    )
    private val service = QaRuleManagementService(categoryRepository, ruleRepository, mailVariableService)

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
    fun `rejects qa rule with unknown placeholder`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    replySubject = null,
                    replyBody = "Dear \${unknownKey}, welcome."
                )
            )
        }

        assertTrue(ex.message!!.contains("\${unknownKey}"))
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `rejects nullable placeholder without fallback`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    replySubject = null,
                    replyBody = "Dear \${expertFamilyName}, welcome."
                )
            )
        }

        assertTrue(ex.message!!.contains("\${expertFamilyName}"))
    }

    @Test
    fun `rejects nullable placeholder with whitespace-only fallback`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    replySubject = null,
                    replyBody = "Dear \${expertFamilyName|   }, welcome."
                )
            )
        }

        assertTrue(ex.message!!.contains("\${expertFamilyName|   }"))
    }

    @Test
    fun `accepts nullable placeholder with fallback`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as QaRule }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "funding",
                replySubject = null,
                replyBody = "Dear \${expertFamilyName|Professor}, welcome."
            )
        )

        assertEquals("Dear \${expertFamilyName|Professor}, welcome.", created.replyBody)
    }

    @Test
    fun `update validates new reply body only`() {
        val existing = rule(
            id = 2L,
            enabled = true,
            replyBody = "Legacy body with \${badToken}"
        )
        Mockito.`when`(ruleRepository.findById(2L)).thenReturn(Optional.of(existing))
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.updateRule(
                2L,
                QaRuleUpdateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    matchMode = "ANY",
                    priority = 100,
                    replySubject = "Subject",
                    replyBody = "Dear \${expertFamilyName}, updated.",
                    displayName = null,
                    autoReplyEnabled = true,
                    handoffRequired = false,
                    enabled = true
                )
            )
        }

        assertTrue(ex.message!!.contains("\${expertFamilyName}"))
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
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

    private fun rule(id: Long, enabled: Boolean, replyBody: String = "The program may provide funding support."): QaRule =
        QaRule(
            id = id,
            categoryId = 1L,
            keywords = "funding",
            matchMode = "ANY",
            priority = 100,
            replySubject = "Funding support",
            replyBody = replyBody,
            enabled = enabled
        )
}
