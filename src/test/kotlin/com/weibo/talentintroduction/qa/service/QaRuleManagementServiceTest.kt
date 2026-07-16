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

    // ── coverage keys ──────────────────────────────────────────────────────────

    @Test
    fun `create rule with valid coverage keys`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "funding",
                replySubject = null,
                replyBody = "Funding info",
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
                replySubject = null,
                replyBody = "Funding info",
                coverageKeys = null
            )
        )

        assertEquals("", created.rule.coverageKeys)
    }

    @Test
    fun `create rule with empty coverage keys saves empty`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as QaRule).copy(id = 10L) }

        val created = service.createRule(
            QaRuleCreateCommand(
                categoryId = 1L,
                keywords = "funding",
                replySubject = null,
                replyBody = "Funding info",
                coverageKeys = emptyList()
            )
        )

        assertEquals("", created.rule.coverageKeys)
    }

    @Test
    fun `rejects unknown coverage key`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    replySubject = null,
                    replyBody = "Funding info",
                    coverageKeys = listOf("finance.guaranteed_amount")
                )
            )
        }

        assertTrue(ex.message!!.contains("Unknown coverage keys"))
        assertTrue(ex.message!!.contains("finance.guaranteed_amount"))
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `rejects duplicate coverage keys`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    replySubject = null,
                    replyBody = "Funding info",
                    coverageKeys = listOf("company.legal_name", "company.legal_name")
                )
            )
        }

        assertTrue(ex.message!!.contains("Duplicate coverage keys"))
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `update with null coverage keys preserves existing`() {
        val existing = rule(id = 10L, enabled = true).copy(
            coverageKeys = "company.legal_name,company.registered_location"
        )
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(existing))
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as QaRule }
        stubVariantPersistence()

        val updated = service.updateRule(
            10L,
            QaRuleUpdateCommand(
                categoryId = 1L,
                keywords = "funding",
                matchMode = "ANY",
                priority = 100,
                replySubject = "Subject",
                replyBody = "Funding info",
                displayName = null,
                autoReplyEnabled = true,
                handoffRequired = false,
                enabled = true,
                coverageKeys = null
            )
        )

        assertEquals("company.legal_name,company.registered_location", updated.rule.coverageKeys)
    }

    @Test
    fun `update with empty coverage keys clears`() {
        val existing = rule(id = 10L, enabled = true).copy(
            coverageKeys = "company.legal_name,company.registered_location"
        )
        Mockito.`when`(ruleRepository.findById(10L)).thenReturn(Optional.of(existing))
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)
        Mockito.`when`(ruleRepository.save(Mockito.any(QaRule::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as QaRule }
        stubVariantPersistence()

        val updated = service.updateRule(
            10L,
            QaRuleUpdateCommand(
                categoryId = 1L,
                keywords = "funding",
                matchMode = "ANY",
                priority = 100,
                replySubject = "Subject",
                replyBody = "Funding info",
                displayName = null,
                autoReplyEnabled = true,
                handoffRequired = false,
                enabled = true,
                coverageKeys = emptyList()
            )
        )

        assertEquals("", updated.rule.coverageKeys)
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

    // ── P1-1: blank key rejection ──────────────────────────────────────────────

    @Test
    fun `create rejects blank coverage key item`() {
        Mockito.`when`(categoryRepository.existsById(1L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createRule(
                QaRuleCreateCommand(
                    categoryId = 1L,
                    keywords = "funding",
                    replySubject = null,
                    replyBody = "Funding info",
                    coverageKeys = listOf("", "company.legal_name")
                )
            )
        }

        assertTrue(ex.message!!.contains("Coverage keys must not be blank"))
        Mockito.verify(ruleRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `update rejects blank coverage key item`() {
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
                    replySubject = "Subject",
                    replyBody = "Funding info",
                    displayName = null,
                    autoReplyEnabled = true,
                    handoffRequired = false,
                    enabled = true,
                    coverageKeys = listOf("company.legal_name", "  ")
                )
            )
        }

        assertTrue(ex.message!!.contains("Coverage keys must not be blank"))
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
