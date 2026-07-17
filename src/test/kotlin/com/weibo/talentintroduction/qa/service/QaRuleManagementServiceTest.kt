package com.weibo.talentintroduction.qa.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.MailPlaceholderService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
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

        val factBody = "The program may provide funding support."
        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "funding, salary",
                matchMode = "any",
                priority = 10,
                answerBody = factBody
            )
        )

        assertEquals(1L, created.rule.categoryId)
        assertEquals("ANY", created.rule.matchMode)
        assertEquals(10, created.rule.priority)
        assertEquals(factBody, created.rule.answerBody)
        assertEquals(factBody, created.rule.replyBody)
        assertEquals(QaReplyPolicy.REVIEW.name, created.rule.replyPolicy)
        assertFalse(created.rule.autoReplyEnabled)
        assertTrue(created.rule.handoffRequired)
        assertTrue(created.variants.isEmpty())
    }

    @Test
    fun `rejects qa variants on create`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    answerBody = "The program may provide funding support.",
                    variants = listOf("Variant A")
                )
            )
        }

        assertTrue(ex.message!!.contains("content variants are no longer supported"))
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `update answerBody preserves legacy replyBody and routing fields`() {
        val existing = rule(
            id = 10L,
            enabled = true,
            replyBody = "Legacy runtime body",
            answerBody = "Legacy runtime body",
            autoReplyEnabled = true,
            handoffRequired = false
        ).copy(
            replySubject = "Legacy subject",
            coverageKeys = "company.legal_name"
        )
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(existing))
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as QaRule }
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                10L
            )
        ).thenReturn(emptyList())

        val updated = service.updateRule(
            10L,
            QaRuleUpdateCommand(
                categoryId = 1L,
                keywords = "funding",
                matchMode = "ANY",
                priority = 100,
                answerBody = "Updated fact-only body.",
                replyPolicy = QaReplyPolicy.AUTO.name,
                displayName = "Fact title",
                enabled = true
            )
        )

        assertEquals("Updated fact-only body.", updated.rule.answerBody)
        assertEquals("Legacy runtime body", updated.rule.replyBody)
        assertEquals(QaReplyPolicy.AUTO.name, updated.rule.replyPolicy)
        assertEquals("Legacy subject", updated.rule.replySubject)
        assertTrue(updated.rule.autoReplyEnabled)
        assertFalse(updated.rule.handoffRequired)
        assertEquals("company.legal_name", updated.rule.coverageKeys)
    }

    @Test
    fun `rejects non-empty qa variants on update`() {
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(rule(id = 10L, enabled = true)))
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.updateRule(
                10L,
                QaRuleUpdateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    matchMode = "ANY",
                    priority = 100,
                    answerBody = "Updated fact-only body.",
                    enabled = true,
                    variants = listOf("Variant A")
                )
            )
        }

        assertTrue(ex.message!!.contains("content variants are no longer supported"))
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `deleteRule cascades variants`() {
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(rule(id = 10L, enabled = true)))

        service.deleteRule(10L)

        Mockito.verify(contentVariantRepository).deleteByOwnerTypeAndOwnerId(ContentVariantOwnerType.QA_RULE, 10L)
        Mockito.verify(ruleRepository).deleteById(10L)
    }

    @Test
    fun `rejects non-empty qa variants on create duplicate message`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    answerBody = "The program may provide funding support.",
                    variants = listOf("The program may provide funding support.")
                )
            )
        }

        assertTrue(ex.message!!.contains("content variants are no longer supported"))
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
                    answerBody = "Topic references \${unknownKey} information."
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
                    answerBody = "Contact \${expertFamilyName} for details."
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
                    answerBody = "Topic \${expertFamilyName|   } is supported."
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
                answerBody = "Topic: \${researchFields|Science}."
            )
        )

        assertEquals("Topic: \${researchFields|Science}.", created.rule.answerBody)
    }

    @Test
    fun `update validates answer body only`() {
        val existing = rule(
            id = 2L,
            enabled = true,
            replyBody = "Legacy body with \${badToken}",
            answerBody = "Legacy body with \${badToken}"
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
                    answerBody = "Contact \${expertFamilyName} for details.",
                    displayName = null,
                    enabled = true
                )
            )
        }

        assertTrue(ex.message!!.contains("\${expertFamilyName}"))
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `rejects salutation in answerBody`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "identity",
                    answerBody = "Dear Professor, the program is administered by the local government."
                )
            )
        }

        assertTrue(ex.message!!.contains("salutation"))
    }

    @Test
    fun `rejects email signature in answerBody`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "fee",
                    answerBody = "The application does not charge experts a service fee. Best regards"
                )
            )
        }

        assertTrue(ex.message!!.contains("signature"))
    }

    @Test
    fun `v79 backfills answer_body from reply_body`() {
        val v79 = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/db/migration/V79__add_qa_answer_body.sql")
        )
        assertTrue(v79.contains("ADD COLUMN answer_body"))
        assertTrue(v79.contains("SET answer_body = reply_body"))
        assertTrue(v79.contains("updated_at = updated_at"))
        assertTrue(v79.contains("MODIFY answer_body TEXT NOT NULL"))
    }

    @Test
    fun `rejects blank answerBody on create`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    answerBody = "   "
                )
            )
        }

        assertTrue(ex.message!!.contains("answerBody is required"))
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `update trims answerBody before save`() {
        val existing = rule(id = 10L, enabled = true)
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(existing))
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as QaRule }
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                10L
            )
        ).thenReturn(emptyList())

        val core = "A".repeat(4000)
        val updated = service.updateRule(
            10L,
            QaRuleUpdateCommand(
                categoryId = 1L,
                keywords = "funding",
                matchMode = "ANY",
                priority = 100,
                answerBody = "  $core  ",
                enabled = true
            )
        )

        assertEquals(4000, updated.rule.answerBody.length)
        assertEquals(core, updated.rule.answerBody)
    }

    @Test
    fun `update rejects answerBody longer than 4000 trimmed characters`() {
        val existing = rule(id = 10L, enabled = true)
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(existing))
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.updateRule(
                10L,
                QaRuleUpdateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    matchMode = "ANY",
                    priority = 100,
                    answerBody = "A".repeat(4001),
                    enabled = true
                )
            )
        }

        assertTrue(ex.message!!.contains("4000"))
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `default replyPolicy is REVIEW for fail-safe domain construction`() {
        val rule = QaRule(
            categoryId = 1L,
            keywords = "funding",
            replySubject = null,
            replyBody = "Funding info."
        )

        assertEquals(QaReplyPolicy.REVIEW.name, rule.replyPolicy)
        assertFalse(rule.withReplyPolicy(rule.replyPolicyEnum()).autoReplyEnabled)
        assertTrue(rule.withReplyPolicy(rule.replyPolicyEnum()).handoffRequired)
    }

    @Test
    fun `create applies reply policy shadow mapping`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 11L) }

        val auto = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "fee",
                answerBody = "No service fee.",
                replyPolicy = QaReplyPolicy.AUTO.name
            )
        )
        assertEquals(QaReplyPolicy.AUTO.name, auto.rule.replyPolicy)
        assertTrue(auto.rule.autoReplyEnabled)
        assertFalse(auto.rule.handoffRequired)

        val never = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "legacy",
                answerBody = "Internal only.",
                replyPolicy = QaReplyPolicy.NEVER.name
            )
        )
        assertEquals(QaReplyPolicy.NEVER.name, never.rule.replyPolicy)
        assertFalse(never.rule.autoReplyEnabled)
        assertTrue(never.rule.handoffRequired)
    }

    @Test
    fun `rejects invalid replyPolicy`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "fee",
                    answerBody = "No service fee.",
                    replyPolicy = "INVALID"
                )
            )
        }

        assertTrue(ex.message!!.contains("replyPolicy"))
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `v80 backfills reply_policy and syncs legacy booleans`() {
        val v80 = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/db/migration/V80__add_qa_reply_policy.sql")
        )
        assertTrue(v80.contains("ADD COLUMN reply_policy"))
        assertTrue(v80.contains("handoff_required = 1 OR auto_reply_enabled = 0 THEN 'REVIEW'"))
        assertTrue(v80.contains("updated_at = updated_at"))
        assertTrue(v80.contains("reply_policy = 'AUTO' THEN 1 ELSE 0"))
        assertTrue(v80.contains("reply_policy IN ('REVIEW', 'NEVER') THEN 1 ELSE 0"))
    }

    @Test
    fun `rejects email signature with comma in answerBody`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "fee",
                    answerBody = "The application does not charge experts a service fee. Best regards,"
                )
            )
        }

        assertTrue(ex.message!!.contains("signature"))
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

    // ── coverage keys (frozen — request ignored) ───────────────────────────────

    @Test
    fun `create ignores coverage keys in request`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "funding",
                answerBody = "Funding info.",
                coverageKeys = listOf("company.legal_name", "finance.government_funding")
            )
        )

        assertEquals("", created.rule.coverageKeys)
    }

    @Test
    fun `create rule with null coverage keys saves empty`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "funding",
                answerBody = "Funding info.",
                coverageKeys = null
            )
        )

        assertEquals("", created.rule.coverageKeys)
    }

    @Test
    fun `create ignores unknown coverage keys`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "funding",
                answerBody = "Funding info.",
                coverageKeys = listOf("finance.guaranteed_amount")
            )
        )

        assertEquals("", created.rule.coverageKeys)
    }

    @Test
    fun `update preserves coverage keys when request sends null`() {
        val existing = rule(id = 10L, enabled = true).copy(
            coverageKeys = "company.legal_name,company.registered_location"
        )
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(existing))
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as QaRule }
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                10L
            )
        ).thenReturn(emptyList())

        val updated = service.updateRule(
            10L,
            QaRuleUpdateCommand(
                categoryId = 1L,
                keywords = "funding",
                matchMode = "ANY",
                priority = 100,
                answerBody = "Funding info.",
                enabled = true,
                coverageKeys = null
            )
        )

        assertEquals("company.legal_name,company.registered_location", updated.rule.coverageKeys)
    }

    @Test
    fun `update preserves coverage keys when request sends empty list`() {
        val existing = rule(id = 10L, enabled = true).copy(
            coverageKeys = "company.legal_name,company.registered_location"
        )
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(existing))
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as QaRule }
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                10L
            )
        ).thenReturn(emptyList())

        val updated = service.updateRule(
            10L,
            QaRuleUpdateCommand(
                categoryId = 1L,
                keywords = "funding",
                matchMode = "ANY",
                priority = 100,
                answerBody = "Funding info.",
                enabled = true,
                coverageKeys = emptyList()
            )
        )

        assertEquals("company.legal_name,company.registered_location", updated.rule.coverageKeys)
    }

    @Test
    fun `enable and disable preserves coverage keys`() {
        val existing = rule(id = 2L, enabled = true).copy(
            coverageKeys = "company.legal_name,company.verification_evidence"
        )
        Mockito.`when`(ruleRepository.findById(2L)).thenReturn(Optional.of(existing))
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
        assertEquals("company.legal_name,company.verification_evidence", disabled.rule.coverageKeys)
    }

    // ── P1-1: blank key rejection (ignored in fact-card phase) ─────────────────

    @Test
    fun `create ignores blank coverage key items`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "funding",
                answerBody = "Funding info.",
                coverageKeys = listOf("", "company.legal_name")
            )
        )

        assertEquals("", created.rule.coverageKeys)
    }

    @Test
    fun `update ignores blank coverage key items`() {
        val existing = rule(id = 10L, enabled = true).copy(coverageKeys = "company.legal_name")
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(existing))
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as QaRule }
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                10L
            )
        ).thenReturn(emptyList())

        val updated = service.updateRule(
            10L,
            QaRuleUpdateCommand(
                categoryId = 1L,
                keywords = "funding",
                matchMode = "ANY",
                priority = 100,
                answerBody = "Funding info.",
                enabled = true,
                coverageKeys = listOf("company.legal_name", "  ")
            )
        )

        assertEquals("company.legal_name", updated.rule.coverageKeys)
    }

    // ── P1-2/P1-3/P1-4: V76 migration static checks ────────────────────────────

    @Test
    fun `v76 backfills programme overview with finance keys`() {
        val v76 = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql")
        )
        val overviewIdx = v76.indexOf("Program overview")
        assertTrue(overviewIdx > 0, "Program overview section exists")
        val overviewSection = v76.substring(overviewIdx, v76.indexOf("-- About", overviewIdx))
        assertTrue(overviewSection.contains("finance.government_funding"), "Program overview should have government_funding")
        assertTrue(overviewSection.contains("finance.enterprise_compensation"), "Program overview should have enterprise_compensation")
        assertTrue(overviewSection.contains("work.relocation"), "Program overview should have work.relocation")
    }

    @Test
    fun `v76 uses correct stable subjects for project sensitivity and full time`() {
        val v76 = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql")
        )
        assertTrue(v76.contains("'Project sensitivity concerns'"), "should use exact subject")
        assertTrue(v76.contains("'Full-time and part-time options'"), "should use exact subject")
        assertFalse(v76.contains("'Project sensitivity' AND"), "old wrong subject absent")
        assertFalse(v76.contains("'Full-time / part-time roles'"), "old wrong subject absent")
    }

    @Test
    fun `v76 assigns researcher selection to application criteria not possible role`() {
        val v76 = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql")
        )
        val criteriaIdx = v76.indexOf("'Application criteria'")
        assertTrue(criteriaIdx > 0, "Application criteria section exists")
        val criteriaSection = v76.substring(criteriaIdx - 80, criteriaIdx + 20)
        assertTrue(criteriaSection.contains("researcher.selection"), "criteria should have selection")
        assertFalse(v76.contains("'Possible role' AND coverage_keys = ''"), "Possible role must not have selection")
    }

    // ── P1-5/P1-6: V76 backfill completeness ────────────────────────────────────

    @Test
    fun `v76 about section includes finance keys`() {
        val v76 = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql")
        )
        val aboutIdx = v76.indexOf("About the talent program")
        assertTrue(aboutIdx > 0, "About section exists")
        val nextSection = v76.indexOf("-- Partner", aboutIdx)
        val aboutSection = if (nextSection > 0) v76.substring(aboutIdx, nextSection) else v76.substring(aboutIdx)
        assertTrue(aboutSection.contains("finance.government_funding"), "About should include government_funding")
        assertTrue(aboutSection.contains("finance.enterprise_compensation"), "About should include enterprise_compensation")
    }

    @Test
    fun `v76 full time section includes travel arrangement`() {
        val v76 = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql")
        )
        val ftIdx = v76.indexOf("Full-time and part-time options")
        assertTrue(ftIdx > 0, "Full-time section exists")
        val startIdx = maxOf(0, ftIdx - 200)
        val endIdx = minOf(ftIdx + 100, v76.length)
        val section = v76.substring(startIdx, endIdx)
        assertTrue(section.contains("work.travel_arrangement"), "Full-time should include travel_arrangement")
        assertTrue(section.contains("work.remote_arrangement"), "Full-time should include remote_arrangement")
        assertFalse(section.contains("'work.remote_arrangement,work.travel_arrangement,work.relocation'"), "Full-time must not include relocation")
    }

    // ── P1-8: work arrangement labels match final body ──────────────────────────

    @Test
    fun `v76 workplace arrangement excludes remote`() {
        val v76 = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql")
        )
        val workplaceIdx = v76.indexOf("Workplace arrangement")
        assertTrue(workplaceIdx > 0, "Workplace section exists")
        val startIdx = maxOf(0, workplaceIdx - 10)
        val endIdx = minOf(workplaceIdx + 200, v76.length)
        val section = v76.substring(startIdx, endIdx)
        assertTrue(section.contains("work.travel_arrangement"), "Workplace should include travel_arrangement")
        assertTrue(section.contains("work.relocation"), "Workplace should include relocation")
        assertFalse(section.contains("work.remote_arrangement"), "Workplace must NOT include remote_arrangement")
    }

    // ── P1-7: parseStored canonical ordering and dedup ──────────────────────────

    @Test
    fun `parseStored sorts by catalog order`() {
        val result = QaCoverageKeyCatalog.parseStored("ip.arrangements,company.legal_name,role.responsibilities")
        assertEquals(
            listOf("company.legal_name", "role.responsibilities", "ip.arrangements"),
            result
        )
    }

    @Test
    fun `parseStored deduplicates repeated keys`() {
        val result = QaCoverageKeyCatalog.parseStored("company.legal_name,company.legal_name,contract.party")
        assertEquals(
            listOf("company.legal_name", "contract.party"),
            result
        )
    }

    @Test
    fun `parseStored silently ignores unknown keys`() {
        val result = QaCoverageKeyCatalog.parseStored("company.legal_name,unknown.fake_key,ip.arrangements")
        assertEquals(
            listOf("company.legal_name", "ip.arrangements"),
            result
        )
    }

    @Test
    fun `parseStored returns empty for empty or blank string`() {
        assertEquals(emptyList<String>(), QaCoverageKeyCatalog.parseStored(""))
        assertEquals(emptyList<String>(), QaCoverageKeyCatalog.parseStored("  ,  ,"))
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

    private fun rule(
        id: Long,
        enabled: Boolean,
        replyBody: String = "The program may provide funding support.",
        answerBody: String = replyBody,
        autoReplyEnabled: Boolean = true,
        handoffRequired: Boolean = false,
        replyPolicy: String = if (autoReplyEnabled && !handoffRequired) {
            QaReplyPolicy.AUTO.name
        } else {
            QaReplyPolicy.REVIEW.name
        }
    ): QaRule =
        QaRule(
            id = id,
            categoryId = 1L,
            keywords = "funding",
            matchMode = "ANY",
            priority = 100,
            replySubject = "Funding support",
            replyBody = replyBody,
            answerBody = answerBody,
            replyPolicy = replyPolicy,
            autoReplyEnabled = autoReplyEnabled,
            handoffRequired = handoffRequired,
            enabled = enabled
        )
}
