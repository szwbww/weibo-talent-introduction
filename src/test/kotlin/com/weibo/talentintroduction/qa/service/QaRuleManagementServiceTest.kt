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
import java.nio.file.Files
import java.nio.file.Paths

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

    private val v82DocKeywords = "confidential,keep my documents,never charge,any fee,money transfer"
    private val v82ContractKeywords = "intellectual property,ip rights,ip arrangements,contractual,contract terms,patent ownership,who owns the,formal agreement,formal contract,before any collaboration begins"

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
    fun `update synchronizes reply body with answer body and preserves routing fields`() {
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
        assertEquals("Updated fact-only body.", updated.rule.replyBody)
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

    // ── coverage keys write path (I-5) ────────────────────────────────────────

    @Test
    fun `create writes normalized coverage keys from request`() {
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

        assertEquals("company.legal_name,finance.government_funding", created.rule.coverageKeys)
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
    fun `create rejects unknown coverage keys`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    answerBody = "Funding info.",
                    coverageKeys = listOf("finance.guaranteed_amount")
                )
            )
        }
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `create accepts all high risk coverage keys`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "funding",
                answerBody = "Funding info.",
                coverageKeys = listOf(
                    "publication.authorship",
                    "finance.compensation_structure",
                    "confidentiality.research"
                )
            )
        )

        assertEquals(
            "publication.authorship,finance.compensation_structure,confidentiality.research",
            created.rule.coverageKeys
        )
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
    fun `update clears coverage keys on explicit empty list`() {
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

        assertEquals("", updated.rule.coverageKeys)
    }

    @Test
    fun `update replaces coverage keys with validated non empty list`() {
        val existing = rule(id = 10L, enabled = true).copy(
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
                answerBody = "Funding info.",
                enabled = true,
                coverageKeys = listOf("role.responsibilities", "role.deliverables")
            )
        )

        assertEquals("role.responsibilities,role.deliverables", updated.rule.coverageKeys)
    }

    @Test
    fun `update rejects invalid coverage keys without touching stored value`() {
        val existing = rule(id = 10L, enabled = true).copy(
            coverageKeys = "company.legal_name"
        )
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(existing))
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            service.updateRule(
                10L,
                QaRuleUpdateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    matchMode = "ANY",
                    priority = 100,
                    answerBody = "Funding info.",
                    enabled = true,
                    coverageKeys = listOf("unknown.key")
                )
            )
        }
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
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

    // ── V82 controlled coverage -> canonical body gate (R-1) ───────────────────

    private val canonicalFeeBody = "We never charge any fees throughout the entire process."

    @Test
    fun `create accepts controlled coverage only with canonical body`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "any fees",
                answerBody = canonicalFeeBody,
                coverageKeys = listOf("fees.policy")
            )
        )

        assertEquals("fees.policy", created.rule.coverageKeys)
    }

    @Test
    fun `create rejects controlled coverage with mismatched body without saving`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "any fees",
                    answerBody = "We never charge participants at any stage.",
                    coverageKeys = listOf("fees.policy")
                )
            )
        }
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `create accepts mixed controlled coverage as a non-authority rule`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "any fees",
                answerBody = canonicalFeeBody,
                coverageKeys = listOf("fees.policy", "confidentiality.materials")
            )
        )

        // I-1: mixed controlled keys do not form exactly one V82 group -> no body gate.
        assertEquals("fees.policy,confidentiality.materials", created.rule.coverageKeys)
    }

    @Test
    fun `update preserves controlled gate on null coverage and validates new writes`() {
        fun existing(body: String, coverage: String) = rule(
            id = 10L, enabled = true,
            replyBody = body,
            answerBody = body
        ).copy(coverageKeys = coverage)
        fun updateCommand(answerBody: String, coverageKeys: List<String>?) = QaRuleUpdateCommand(
            categoryId = 1L,
            keywords = "funding",
            matchMode = "ANY",
            priority = 100,
            answerBody = answerBody,
            enabled = true,
            coverageKeys = coverageKeys
        )
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as QaRule }
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                10L
            )
        ).thenReturn(emptyList())

        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(
            Optional.of(existing("We never charge any fees throughout the entire process.", "fees.policy"))
        )
        val preserved = service.updateRule(10L, updateCommand(canonicalFeeBody, null))
        assertEquals("fees.policy", preserved.rule.coverageKeys)

        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(
            Optional.of(existing("Stale stored body", "fees.policy"))
        )
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.updateRule(10L, updateCommand("Some other body.", null))
        }
        assertTrue(ex.message!!.contains("canonical body"))
        Mockito.verify(ruleRepository, Mockito.times(1)).save(Mockito.any())
    }

    @Test
    fun `enable re-verifies controlled stored rule before saving`() {
        fun existing(body: String, coverage: String) = rule(
            id = 2L, enabled = false,
            replyBody = body,
            answerBody = body
        ).copy(coverageKeys = coverage)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as QaRule }
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                2L
            )
        ).thenReturn(emptyList())

        Mockito.`when`(ruleRepository.findById(2L)).thenReturn(
            Optional.of(existing(canonicalFeeBody, "fees.policy"))
        )
        assertEquals("fees.policy", service.setRuleEnabled(2L, true).rule.coverageKeys)
        Mockito.verify(ruleRepository, Mockito.times(1)).save(Mockito.any())

        Mockito.`when`(ruleRepository.findById(2L)).thenReturn(
            Optional.of(existing("Wrong body for fees.", "fees.policy"))
        )
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.setRuleEnabled(2L, true)
        }
        assertTrue(ex.message!!.contains("canonical body"))
        Mockito.verify(ruleRepository, Mockito.times(1)).save(Mockito.any())
    }

    @Test
    fun `invalid controlled rule can be disabled but stays blocked on enable`() {
        val invalid = rule(
            id = 2L, enabled = true,
            replyBody = "Wrong body for fees.",
            answerBody = "Wrong body for fees."
        ).copy(coverageKeys = "fees.policy")
        Mockito.`when`(ruleRepository.findById(2L)).thenReturn(Optional.of(invalid))
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
        assertEquals("Wrong body for fees.", disabled.rule.answerBody, "disable must not mutate the body")
        assertEquals("fees.policy", disabled.rule.coverageKeys)
        Mockito.verify(ruleRepository, Mockito.times(1)).save(Mockito.any())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.setRuleEnabled(2L, true)
        }
        assertTrue(ex.message!!.contains("canonical body"))
        Mockito.verify(ruleRepository, Mockito.times(1)).save(Mockito.any())
    }

    @Test
    fun `program overview rule 24 real fixture is unaffected by the body gate after I-1 relaxation`() {
        // Real V76 coverage string for rule 24 (11 keys, incl. the two controlled keys).
        val rule24Coverage = listOf(
            "programme.purpose", "programme.structure", "programme.tracks", "programme.scope",
            "finance.government_funding", "finance.enterprise_compensation",
            "work.remote_arrangement", "work.travel_arrangement", "work.relocation",
            "fees.policy", "confidentiality.materials"
        )
        val rule24Stored = rule24Coverage.joinToString(",")
        // Real Program overview body excerpt (starts "Two tracks:", mentions fees/confidentiality).
        val overviewBody = "Two tracks: Innovative Talent Scheme and Entrepreneurial Talent Scheme. " +
            "There are no fees at any stage, and all materials are kept strictly confidential."
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "programme purpose",
                answerBody = overviewBody,
                coverageKeys = rule24Coverage
            )
        )
        assertEquals(rule24Stored, created.rule.coverageKeys, "create must preserve coverage verbatim")

        val existing = rule(id = 10L, enabled = true, replyBody = overviewBody, answerBody = overviewBody)
            .copy(coverageKeys = rule24Stored)
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(existing))
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
                keywords = "programme purpose",
                matchMode = "ANY",
                priority = 100,
                answerBody = overviewBody,
                enabled = true,
                coverageKeys = rule24Coverage
            )
        )
        assertEquals(rule24Stored, updated.rule.coverageKeys, "update must preserve coverage verbatim")
    }

    @Test
    fun `create accepts unpaired contract key as a non-authority rule`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "contract",
                answerBody = "The matched enterprise is introduced after selection.",
                coverageKeys = listOf("contract.party")
            )
        )

        // I-1: a half-checked controlled group is not an authority -> body gate passes it through.
        assertEquals("contract.party", created.rule.coverageKeys)
    }

    // ── P1-1: blank key rejection (I-5) ────────────────────────────────────────

    @Test
    fun `create rejects blank coverage key items`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    answerBody = "Funding info.",
                    coverageKeys = listOf("", "company.legal_name")
                )
            )
        }
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `update rejects blank coverage key items`() {
        val existing = rule(id = 10L, enabled = true).copy(coverageKeys = "company.legal_name")
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(existing))
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            service.updateRule(
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
        }
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
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

    // ── P4 (I-6): V107 strips only the two controlled keys from rule 24 ────────

    @Test
    fun `v107 strips only the two controlled keys from program overview and preserves timestamps`() {
        val v107 = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/resources/db/migration/V107__strip_controlled_keys_from_program_overview.sql")
        )
        val nineKeys = "programme.purpose,programme.structure,programme.tracks,programme.scope,finance.government_funding,finance.enterprise_compensation,work.remote_arrangement,work.travel_arrangement,work.relocation"
        val baseline = "$nineKeys,fees.policy,confidentiality.materials"
        assertTrue(v107.contains("updated_at = updated_at"), "V107 must preserve the auto-updated timestamp")
        assertTrue(v107.contains("WHERE id = 24"), "V107 must target rule 24 only")
        assertTrue(v107.contains("coverage_keys = '$nineKeys'"), "V107 must write the 9-key stripped string")
        assertTrue(v107.contains("coverage_keys = '$baseline'"), "V107 baseline guard must match the V76 value verbatim")
        assertFalse(v107.contains("answer_body"), "V107 must not touch answer_body")
        assertFalse(v107.contains("reply_body"), "V107 must not touch reply_body")
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

    // ── V82: atomic trust-reply facts (I-1/I-3/I-4/I-6) ────────────────────────

    @Test
    fun `v82 disables composite rules only against baseline gate`() {
        val v82 = Files.readString(
            Paths.get("src/main/resources/db/migration/V82__split_trust_reply_atomic_facts.sql")
        )
        assertTrue(v82.contains("'Document confidentiality and no fees'"), "old confidentiality subject targeted")
        assertTrue(v82.contains("'Contract and IP arrangements'"), "old contract/IP subject targeted")
        assertTrue(v82.contains("SHA2(answer_body, 256)"), "answer hash baseline gate present")
        assertTrue(v82.contains("'2026-06-26 22:14:06'"), "confidentiality updatedAt baseline present")
        assertTrue(v82.contains("'2026-07-16 18:03:00'"), "contract updatedAt baseline present")
        assertTrue(v82.contains("'04027e0b2046f72f4bcc736a7436299f7880bdef74e321744c61bafebcbb0a37'"), "confidentiality answer SHA-256 baseline")
        assertTrue(v82.contains("'3f142b13e0274db4d5b218f522ffe7071de7a501f6b5ab6324ccade424448f16'"), "contract answer SHA-256 baseline")
        assertTrue(v82.contains("id = 17") && v82.contains("id = 34"), "both audited ids present")
        assertTrue(v82.contains(v82DocKeywords), "id 17 keyword baseline present")
        assertTrue(v82.contains(v82ContractKeywords), "id 34 keyword baseline present")
    }

    @Test
    fun `v82 aborts on baseline drift before any qa rule write`() {
        val v82 = Files.readString(
            Paths.get("src/main/resources/db/migration/V82__split_trust_reply_atomic_facts.sql")
        )
        val createGateIdx = v82.indexOf("CREATE PROCEDURE v82_trust_reply_baseline_gate")
        val callGateIdx = v82.indexOf("CALL v82_trust_reply_baseline_gate")
        val firstInsertIdx = v82.indexOf("INSERT INTO qa_rule")
        val firstUpdateIdx = v82.indexOf("UPDATE qa_rule")
        assertTrue(createGateIdx in 1..callGateIdx, "gate created before it runs")
        assertTrue(callGateIdx in 1..firstInsertIdx, "gate runs before any INSERT")
        assertTrue(callGateIdx in 1..firstUpdateIdx, "gate runs before any UPDATE")

        val gateBody = v82.substring(createGateIdx, v82.indexOf("DELIMITER ;", createGateIdx))
        assertTrue(gateBody.contains("SIGNAL SQLSTATE '45000'"), "migration aborts via SIGNAL on baseline drift")
        assertTrue(gateBody.contains("id = 17") && gateBody.contains("id = 34"), "gate checks both audited ids")
        assertTrue(
            gateBody.contains(v82DocKeywords) && gateBody.contains(v82ContractKeywords),
            "gate checks exact baseline keywords"
        )
        assertTrue(
            gateBody.contains("'2026-06-26 22:14:06'") && gateBody.contains("'2026-07-16 18:03:00'"),
            "gate validates both baseline records"
        )
        assertTrue(
            gateBody.contains("'04027e0b2046f72f4bcc736a7436299f7880bdef74e321744c61bafebcbb0a37'") &&
                gateBody.contains("'3f142b13e0274db4d5b218f522ffe7071de7a501f6b5ab6324ccade424448f16'"),
            "gate validates both audited answer hashes"
        )
        assertTrue(v82.contains("DROP PROCEDURE v82_trust_reply_baseline_gate"), "gate procedure is cleaned up")
    }

    @Test
    fun `v82 gates and disables only the fully validated legacy rows`() {
        val v82 = Files.readString(
            Paths.get("src/main/resources/db/migration/V82__split_trust_reply_atomic_facts.sql")
        )
        assertTrue(
            v82.indexOf("id = 17") != v82.lastIndexOf("id = 17"),
            "id 17 guarded in both gate and disable predicate"
        )
        assertTrue(
            v82.indexOf("id = 34") != v82.lastIndexOf("id = 34"),
            "id 34 guarded in both gate and disable predicate"
        )
        assertTrue(
            v82.indexOf(v82DocKeywords) != v82.lastIndexOf(v82DocKeywords),
            "id 17 exact keywords guarded in both gate and disable predicate"
        )
        assertTrue(
            v82.indexOf(v82ContractKeywords) != v82.lastIndexOf(v82ContractKeywords),
            "id 34 exact keywords guarded in both gate and disable predicate"
        )
        assertTrue(
            v82.contains("(SELECT category_id FROM qa_rule WHERE id = 17)"),
            "material/fee rules derive category from validated id 17"
        )
        assertTrue(
            v82.contains("(SELECT category_id FROM qa_rule WHERE id = 34)"),
            "contract/IP rules derive category from validated id 34"
        )
        assertFalse(v82.contains("ORDER BY id LIMIT 1"), "no subject-based fallback derivation remains")
    }

    @Test
    fun `v82 inserts four atomic rules idempotently with atomic bodies`() {
        val v82 = Files.readString(
            Paths.get("src/main/resources/db/migration/V82__split_trust_reply_atomic_facts.sql")
        )
        val subjects = listOf(
            "Application material confidentiality" to "confidentiality.materials",
            "Participant fee policy" to "fees.policy",
            "Contract arrangements" to "contract.party,contract.terms",
            "Pre-contract IP boundary" to "ip.arrangements"
        )
        val sectionStarts = subjects.map { (subject, _) ->
            val idx = v82.indexOf(subject)
            assertTrue(idx > 0, "$subject section exists")
            idx
        }
        subjects.forEachIndexed { index, (subject, coverage) ->
            val idx = sectionStarts[index]
            val sectionEnd = sectionStarts.getOrNull(index + 1) ?: v82.length
            val section = v82.substring(maxOf(0, idx - 350), sectionEnd)
            assertTrue(section.contains("INSERT INTO qa_rule"), "$subject inserted")
            assertTrue(section.contains("WHERE NOT EXISTS"), "$subject insert is idempotent")
            assertTrue(section.contains(coverage), "$subject has coverage $coverage")
        }

        val materialBody = "Your materials are kept strictly confidential and used only for application purposes. Technical details you prefer not to disclose can be handled with appropriate redaction."
        val feeBody = "We never charge any fees throughout the entire process."
        val contractBody = "After selection, you will sign a labor contract directly with the matched enterprise, and you may review the full terms before making any commitment."
        val ipBody = "Until a contract is signed, nothing you share with us transfers any rights; any final intellectual-property arrangements will be set out in the future written agreement."
        assertTrue(v82.contains(materialBody), "material body present")
        assertTrue(v82.contains(feeBody), "fee body present")
        assertTrue(v82.contains(contractBody), "contract body present")
        assertTrue(v82.contains(ipBody), "IP body present")

        assertFalse(materialBody.contains("fee", ignoreCase = true), "material body has no fee sentence")
        assertFalse(materialBody.contains("charge", ignoreCase = true), "material body has no charge sentence")
        assertFalse(materialBody.contains("contract", ignoreCase = true), "material body has no contract sentence")

        assertFalse(feeBody.contains("confidential", ignoreCase = true), "fee body has no confidentiality sentence")
        assertFalse(feeBody.contains("redaction", ignoreCase = true), "fee body has no redaction sentence")

        val feeIdx = v82.indexOf("Participant fee policy")
        val feeSection = v82.substring(maxOf(0, feeIdx - 350), v82.indexOf("Contract arrangements"))
        assertFalse(feeSection.contains("obligations", ignoreCase = true), "fee rule must not recall bare obligations")
        assertTrue(feeSection.contains("any costs", ignoreCase = true), "fee rule keeps cost-specific recall")

        assertFalse(contractBody.contains("intellectual", ignoreCase = true), "contract body has no IP sentence")
        assertFalse(contractBody.contains("transfers any rights", ignoreCase = true), "contract body has no IP-boundary sentence")

        assertFalse(ipBody.contains("labor contract", ignoreCase = true), "IP body has no contract-clause sentence")
        assertFalse(ipBody.contains("review the full terms", ignoreCase = true), "IP body has no contract-review sentence")
        assertTrue(ipBody.contains("transfers any rights", ignoreCase = true), "IP body keeps pre-signature no-transfer fact")
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

    // ── V81 migration static contract (Phase 09 I-4) ──

    @Test
    fun `V81 migration has three UPDATEs with idempotent keyword parity`() {
        val sql = Files.readString(
            Paths.get("src/main/resources/db/migration/V81__ai_reply_due_diligence_keyword_parity.sql")
        )
        val updateStmts = sql.split(";").map { it.trim() }
            .filter { it.isNotBlank() && it.contains("UPDATE qa_rule", ignoreCase = true) }
        assertEquals(3, updateStmts.size, "V81 must have exactly 3 UPDATE statements")

        val subjects = listOf("Funding support", "Program overview", "Contract and IP arrangements")
        for (subject in subjects) {
            assertTrue(
                updateStmts.any { it.contains("reply_subject = '$subject'") },
                "V81 must have UPDATE for reply_subject='$subject'"
            )
        }

        for (stmt in updateStmts) {
            val setPart = stmt.substringAfter("SET keywords = ").substringBefore("updated_at = updated_at")
            assertTrue(setPart.contains("CONCAT(keywords,"), "must use CONCAT")
            assertTrue(setPart.contains("CASE WHEN LOWER(keywords) NOT LIKE"), "must use idempotent NOT LIKE")
            assertTrue(stmt.contains("updated_at = updated_at"), "must preserve updated_at")
            val assignedColumns = Regex("(?m)^\\s*([a-z_]+)\\s*=", RegexOption.IGNORE_CASE)
                .findAll(stmt.substringAfter("SET ").substringBefore("WHERE"))
                .map { it.groupValues[1].lowercase() }
                .toSet()
            assertEquals(setOf("keywords", "updated_at"), assignedColumns, "V81 may only assign keywords")
        }

        val actionPart = sql.replace(Regex("--[^\n]*"), "").replace("\n", " ")
        assertFalse(actionPart.contains("answer_body "), "SQL must not update answer_body")
        assertFalse(actionPart.contains("display_name "), "SQL must not update display_name")
        assertFalse(actionPart.contains("reply_policy "), "SQL must not update reply_policy")
        assertFalse(actionPart.contains("enabled "), "SQL must not update enabled")
        assertFalse(actionPart.contains("priority "), "SQL must not update priority")
    }

    @Test
    fun `V81 does not contain unsupported keywords`() {
        val sql = Files.readString(
            Paths.get("src/main/resources/db/migration/V81__ai_reply_due_diligence_keyword_parity.sql")
        ).lowercase()
        assertFalse(sql.contains("remuneration structure"), "must not add remuneration structure keyword")
        assertFalse(sql.contains("time commitment"), "must not add time commitment keyword")
        assertFalse(
            sql.contains("examples of enterprise") ||
                sql.contains("enterprise examples"),
            "must not add enterprise examples keyword"
        )
    }

    @Test
    fun `V81 each phrase has independent CASE NOT LIKE guard`() {
        val sql = Files.readString(
            Paths.get("src/main/resources/db/migration/V81__ai_reply_due_diligence_keyword_parity.sql")
        )

        val expectedPhrases = listOf(
            "advisory role compensated",
            "is the advisory role compensated",
            "typical duration",
            "duration of advisory projects",
            "advisory project duration",
            "formal agreement",
            "formal contract",
            "before any collaboration begins"
        )

        for (phrase in expectedPhrases) {
            val escaped = Regex.escape(phrase)
            assertTrue(
                Regex("CASE WHEN LOWER\\(keywords\\) NOT LIKE '%$escaped%'", RegexOption.IGNORE_CASE)
                    .containsMatchIn(sql),
                "V81 must have independent CASE/NOT LIKE for: $phrase"
            )
            assertTrue(
                Regex("THEN ',$escaped'", RegexOption.IGNORE_CASE)
                    .containsMatchIn(sql),
                "V81 must append phrase: $phrase"
            )
        }
    }
}
