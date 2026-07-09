package com.weibo.talentintroduction.qa.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.MailPlaceholderService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.domain.ContentVariant
import com.weibo.talentintroduction.variant.domain.ContentVariantOwnerType
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class QaRuleManagementServiceTest {
    private val categoryRepository = Mockito.mock(QaCategoryRepository::class.java)
    private val ruleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val contentVariantRepository = Mockito.mock(ContentVariantRepository::class.java)
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
            Mockito.mock(MailSenderAccountService::class.java),
            ContentVariantService(contentVariantRepository, MailPlaceholderService())
        )
    )
    private val contentVariantService = ContentVariantService(contentVariantRepository, MailPlaceholderService())
    private val service = QaRuleManagementService(
        categoryRepository,
        ruleRepository,
        mailVariableService,
        contentVariantService
    )
    private val variantIdSeq = AtomicLong(1)

    @Test
    fun `creates qa rule when category exists`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }

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

        assertEquals(1L, created.rule.categoryId)
        assertEquals("ANY", created.rule.matchMode)
        assertEquals(10, created.rule.priority)
        assertTrue(created.variants.isEmpty())
    }

    @Test
    fun `create persists variants in order and update replaces them`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(rule(id = 10L, enabled = true)))
        stubVariantPersistence()

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "funding",
                replySubject = null,
                replyBody = "Main body",
                variants = listOf("Variant A", "Variant B")
            )
        )

        assertEquals(listOf("Variant A", "Variant B"), created.variants)

        val updated = service.updateRule(
            10L,
            QaRuleUpdateCommand(
                categoryId = 1L,
                keywords = "funding",
                matchMode = "ANY",
                priority = 100,
                replySubject = "Subject",
                replyBody = "Main body",
                displayName = null,
                autoReplyEnabled = true,
                handoffRequired = false,
                enabled = true,
                variants = emptyList()
            )
        )

        assertTrue(updated.variants.isEmpty())
        Mockito.verify(contentVariantRepository, Mockito.times(2))
            .deleteByOwnerTypeAndOwnerId(ContentVariantOwnerType.QA_RULE, 10L)
    }

    @Test
    fun `deleteRule cascades variants`() {
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(rule(id = 10L, enabled = true)))

        service.deleteRule(10L)

        Mockito.verify(contentVariantRepository).deleteByOwnerTypeAndOwnerId(ContentVariantOwnerType.QA_RULE, 10L)
        Mockito.verify(ruleRepository).deleteById(10L)
    }

    @Test
    fun `rejects variant duplicate of main body`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    replySubject = null,
                    replyBody = "Main body",
                    variants = listOf("Main body")
                )
            )
        }

        assertTrue(ex.message!!.contains("变体不能与主体重复"))
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
        Mockito.verify(contentVariantRepository, Mockito.never()).deleteByOwnerTypeAndOwnerId(
            Mockito.anyString(),
            Mockito.anyLong()
        )
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
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }
        stubVariantPersistence()

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "funding",
                replySubject = null,
                replyBody = "Dear \${expertFamilyName|Professor}, welcome."
            )
        )

        assertEquals("Dear \${expertFamilyName|Professor}, welcome.", created.rule.replyBody)
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
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                2L
            )
        ).thenReturn(emptyList())

        val disabled = service.setRuleEnabled(2L, false)

        assertFalse(disabled.rule.enabled)
    }

    private fun stubVariantPersistence() {
        val stored = mutableListOf<ContentVariant>()
        Mockito.lenient().doAnswer {
            stored.clear()
            null
        }.`when`(contentVariantRepository).deleteByOwnerTypeAndOwnerId(
            Mockito.anyString(),
            Mockito.anyLong()
        )
        Mockito.`when`(contentVariantRepository.save(Mockito.any(ContentVariant::class.java)))
            .thenAnswer { invocation ->
                val saved = invocation.arguments[0] as ContentVariant
                val withId = saved.copy(id = variantIdSeq.getAndIncrement())
                stored += withId
                withId
            }
        Mockito.lenient().`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(
                Mockito.anyString(),
                Mockito.anyLong()
            )
        ).thenAnswer {
            stored.sortedWith(compareBy({ it.variantOrder }, { it.id }))
        }
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
