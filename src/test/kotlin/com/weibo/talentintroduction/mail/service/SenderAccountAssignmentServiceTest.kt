package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.config.WarmupStep
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class SenderAccountAssignmentServiceTest {
    private val repository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val warmupService = SenderWarmupService(WarmupProperties(enabled = false), ObjectMapper().registerKotlinModule())
    private val service = SenderAccountAssignmentService(
        repository,
        warmupService,
        Mockito.mock(ExpertContactRepository::class.java)
    )

    @Test
    fun `avoids repeatedly assigning same country segment to same account`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("chen", strategyWeight = 100),
                account("zoe", strategyWeight = 100)
            )
        )

        val selected = service.selectAccount(
            expert = expert(country = "United States"),
            currentBatchAssignments = listOf(
                SenderExpertAssignment(
                    accountCode = "chen",
                    expertId = "0000-0001",
                    distributionKey = "united states"
                )
            )
        )

        assertEquals("zoe", selected.accountCode)
    }

    @Test
    fun `excludes simulator account`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("SIMULATOR_NOOP", strategyWeight = 100),
                account("zoe", strategyWeight = 100)
            )
        )

        val selected = service.selectAccount(
            expert = expert(country = "United States")
        )

        assertEquals("zoe", selected.accountCode)
    }

    @Test
    fun `excludes auto-paused account`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("paused", strategyWeight = 200, autoSendPaused = true),
                account("zoe", strategyWeight = 100, autoSendPaused = false)
            )
        )

        val selected = service.selectAccount(
            expert = expert(country = "United States")
        )

        assertEquals("zoe", selected.accountCode)
    }

    @Test
    fun `low strategyWeight account stays selectable with same segment penalty`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(account("low", strategyWeight = 10))
        )

        val selected = service.selectAccount(
            expert = expert(country = "United States"),
            currentBatchAssignments = listOf(
                SenderExpertAssignment(
                    accountCode = "low",
                    expertId = "0000-0001",
                    distributionKey = "united states"
                )
            )
        )

        assertEquals("low", selected.accountCode)
    }

    @Test
    fun `prefers alternate account when low weight account already has same segment assignment`() {
        val repository = Mockito.mock(MailSenderAccountRepository::class.java)
        val service = SenderAccountAssignmentService(
            repository,
            SenderWarmupService(WarmupProperties(enabled = false), ObjectMapper().registerKotlinModule()),
            Mockito.mock(ExpertContactRepository::class.java)
        )
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("weighted", strategyWeight = 100),
                account("other", strategyWeight = 100)
            )
        )

        val selected = service.selectAccount(
            expert = expert(country = "United States"),
            currentBatchAssignments = listOf(
                SenderExpertAssignment(
                    accountCode = "weighted",
                    expertId = "0000-0001",
                    distributionKey = "united states"
                )
            )
        )

        assertEquals("other", selected.accountCode)
    }

    @Test
    fun `selectAccount includes warmup-limited account when ignoreWarmup is true`() {
        val enabledWarmup = SenderWarmupService(
            WarmupProperties(
                enabled = true,
                steps = listOf(WarmupStep(1, 20))
            ),
            ObjectMapper().registerKotlinModule()
        )
        val serviceWithWarmup = SenderAccountAssignmentService(
            repository,
            enabledWarmup,
            Mockito.mock(ExpertContactRepository::class.java)
        )
        val now = LocalDateTime.of(2026, 6, 24, 12, 0)
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account(
                    "warmup_only",
                    strategyWeight = 100,
                    dailySendLimit = 100,
                    todaySentCount = 20,
                    warmupEnabled = true,
                    warmupStartedAt = now,
                    warmupStepsJson = """[{"dayFrom":1,"limit":20}]"""
                )
            )
        )

        val selected = serviceWithWarmup.selectAccount(
            expert = expert(country = "United States"),
            ignoreWarmup = true
        )

        assertEquals("warmup_only", selected.accountCode)
    }

    @Test
    fun `selectAccount excludes account at dailySendLimit even when ignoreWarmup is true`() {
        val enabledWarmup = SenderWarmupService(
            WarmupProperties(
                enabled = true,
                steps = listOf(WarmupStep(1, 20))
            ),
            ObjectMapper().registerKotlinModule()
        )
        val serviceWithWarmup = SenderAccountAssignmentService(
            repository,
            enabledWarmup,
            Mockito.mock(ExpertContactRepository::class.java)
        )
        val now = LocalDateTime.of(2026, 6, 24, 12, 0)
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account(
                    "at_daily_limit",
                    strategyWeight = 100,
                    dailySendLimit = 100,
                    todaySentCount = 100,
                    warmupEnabled = true,
                    warmupStartedAt = now,
                    warmupStepsJson = """[{"dayFrom":1,"limit":20}]"""
                )
            )
        )

        assertThrows(NoAvailableSenderAccountException::class.java) {
            serviceWithWarmup.selectAccount(
                expert = expert(country = "United States"),
                ignoreWarmup = true
            )
        }
    }

    @Test
    fun `empty stock keeps score identical to legacy behavior`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("chen", strategyWeight = 50),
                account("zoe", strategyWeight = 100)
            )
        )

        val legacy = service.selectAccount(
            expert = expert(country = "United States")
        )
        val withEmptyStock = service.selectAccount(
            expert = expert(country = "United States"),
            stock = SenderBindingStock.EMPTY
        )

        assertEquals("zoe", legacy.accountCode)
        assertEquals(legacy.accountCode, withEmptyStock.accountCode)
    }

    @Test
    fun `account with larger bound stock is deprioritized`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("chen", strategyWeight = 100),
                account("zoe", strategyWeight = 100)
            )
        )
        val stock = SenderBindingStock(
            totalByAccount = mapOf("chen" to 900L, "zoe" to 100L),
            segmentByAccount = emptyMap(),
            segmentTotals = emptyMap()
        )

        val selected = service.selectAccount(
            expert = expert(country = "United States"),
            stock = stock
        )

        assertEquals("zoe", selected.accountCode)
    }

    @Test
    fun `stock penalty does not override strategy weight entirely`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("chen", strategyWeight = 1000),
                account("zoe", strategyWeight = 10)
            )
        )
        val stock = SenderBindingStock(
            totalByAccount = mapOf("chen" to 900L, "zoe" to 100L),
            segmentByAccount = emptyMap(),
            segmentTotals = emptyMap()
        )

        val selected = service.selectAccount(
            expert = expert(country = "United States"),
            stock = stock
        )

        assertEquals("chen", selected.accountCode)
    }

    @Test
    fun `country segment stock is considered`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("chen", strategyWeight = 100),
                account("zoe", strategyWeight = 100)
            )
        )
        val stock = SenderBindingStock(
            totalByAccount = mapOf("chen" to 500L, "zoe" to 500L),
            segmentByAccount = mapOf(
                "chen" to "germany" to 450L,
                "zoe" to "germany" to 50L
            ),
            segmentTotals = mapOf("germany" to 500L)
        )

        val selected = service.selectAccount(
            expert = expert(country = "Germany"),
            stock = stock
        )

        assertEquals("zoe", selected.accountCode)
    }

    @Test
    fun `unknown country falls into unknown segment`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("chen", strategyWeight = 100),
                account("zoe", strategyWeight = 100)
            )
        )
        val stock = SenderBindingStock(
            totalByAccount = mapOf("chen" to 100L, "zoe" to 100L),
            segmentByAccount = mapOf(
                "chen" to "unknown" to 90L,
                "zoe" to "unknown" to 10L
            ),
            segmentTotals = mapOf("unknown" to 100L)
        )

        val nullCountry = service.selectAccount(
            expert = expert(country = "United States").copy(country = null),
            stock = stock
        )
        val blankCountry = service.selectAccount(
            expert = expert(country = "  "),
            stock = stock
        )

        assertEquals("zoe", nullCountry.accountCode)
        assertEquals("zoe", blankCountry.accountCode)
    }

    @Test
    fun `zero segment total yields zero segment penalty`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("chen", strategyWeight = 100),
                account("zoe", strategyWeight = 100)
            )
        )
        val stock = SenderBindingStock(
            totalByAccount = mapOf("chen" to 100L, "zoe" to 100L),
            segmentByAccount = mapOf(
                "chen" to "germany" to 90L,
                "zoe" to "germany" to 10L
            ),
            segmentTotals = mapOf("germany" to 100L)
        )

        // 全新国别没有段存量 → 段惩罚恒为 0（I-4 除零保护）
        assertEquals(0.0, stock.segmentShare("chen", "france"))
        assertEquals(0.0, stock.segmentShare("zoe", "france"))

        // 总量相同且段惩罚为 0 → 两账号打平，取首个账号
        val selected = service.selectAccount(
            expert = expert(country = "France"),
            stock = stock
        )

        assertEquals("chen", selected.accountCode)
    }

    private fun account(
        accountCode: String,
        strategyWeight: Int = 100,
        autoSendPaused: Boolean = false,
        dailySendLimit: Int = 100,
        todaySentCount: Int = 0,
        warmupEnabled: Boolean? = null,
        warmupStartedAt: LocalDateTime? = null,
        warmupStepsJson: String? = null
    ): MailSenderAccount =
        MailSenderAccount(
            accountCode = accountCode,
            senderEmail = "$accountCode@qftechtalent.com",
            senderName = accountCode,
            senderTitle = "Customer Care Officer",
            senderDisplayName = accountCode,
            teamName = "Qingfei Tech Talent Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "$accountCode@qftechtalent.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "$accountCode@qftechtalent.com",
            imapPassword = "secret",
            strategyWeight = strategyWeight,
            dailySendLimit = dailySendLimit,
            todaySentCount = todaySentCount,
            autoSendPaused = autoSendPaused,
            warmupEnabled = warmupEnabled,
            warmupStartedAt = warmupStartedAt,
            warmupStepsJson = warmupStepsJson
        )

    private fun expert(country: String): ExpertProfile =
        ExpertProfile(
            orcidId = "0000-0002",
            email = "expert@example.com",
            givenNames = "Ada",
            familyNames = "Lovelace",
            country = country,
            keyword = "computer science",
            employment = "University"
        )
}
