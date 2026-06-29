package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.config.WarmupStep
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
    private val service = SenderAccountAssignmentService(repository, warmupService)

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
            SenderWarmupService(WarmupProperties(enabled = false), ObjectMapper().registerKotlinModule())
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
        val serviceWithWarmup = SenderAccountAssignmentService(repository, enabledWarmup)
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
        val serviceWithWarmup = SenderAccountAssignmentService(repository, enabledWarmup)
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
