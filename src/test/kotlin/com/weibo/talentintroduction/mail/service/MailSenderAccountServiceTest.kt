package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.config.WarmupStep
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.transaction.annotation.Transactional

class MailSenderAccountServiceTest {
    private val repository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val selfCheckService = Mockito.mock(SenderAccountSelfCheckService::class.java)
    private val smtpSenderFactory = Mockito.mock(SmtpSenderFactory::class.java)
    private val connectivityService = Mockito.mock(MailAccountConnectivityService::class.java)
    private val campaignRepository = Mockito.mock(com.weibo.talentintroduction.campaign.repository.CampaignRepository::class.java)
    private val warmupService = SenderWarmupService(WarmupProperties(enabled = false), ObjectMapper().registerKotlinModule())
    private val service = MailSenderAccountService(
        repository,
        selfCheckService,
        smtpSenderFactory,
        warmupService,
        connectivityService,
        campaignRepository
    )

    @Test
    fun `selectAccountForManualSending selects account at daily limit`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("exhausted", strategyWeight = 100, dailySendLimit = 100, todaySentCount = 100)
            )
        )

        val selected = service.selectAccountForManualSending()

        assertEquals("exhausted", selected.accountCode)
    }

    @Test
    fun `selectAccountForManualSending excludes auto-paused accounts`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("paused", strategyWeight = 200, dailySendLimit = 100, todaySentCount = 0, autoSendPaused = true),
                account("ok", strategyWeight = 80, dailySendLimit = 100, todaySentCount = 100, autoSendPaused = false)
            )
        )

        val selected = service.selectAccountForManualSending()

        assertEquals("ok", selected.accountCode)
    }

    @Test
    fun `selectAccountForManualSending excludes simulator account`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("SIMULATOR_NOOP", strategyWeight = 200, dailySendLimit = 100, todaySentCount = 0),
                account("real", strategyWeight = 80, dailySendLimit = 100, todaySentCount = 100)
            )
        )

        val selected = service.selectAccountForManualSending()

        assertEquals("real", selected.accountCode)
    }

    @Test
    fun `selectAccountForManualSending throws when no eligible account`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("paused", strategyWeight = 200, dailySendLimit = 100, todaySentCount = 0, autoSendPaused = true)
            )
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            service.selectAccountForManualSending()
        }

        assertTrue(ex.message!!.contains("manual send"))
    }

    @Test
    fun `selects enabled account with highest weighted remaining capacity`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("hot", strategyWeight = 100, dailySendLimit = 100, todaySentCount = 95),
                account("balanced", strategyWeight = 80, dailySendLimit = 100, todaySentCount = 10),
                account("exhausted", strategyWeight = 200, dailySendLimit = 100, todaySentCount = 100)
            )
        )

        val selected = service.selectAccountForSending()

        assertEquals("balanced", selected.accountCode)
    }

    @Test
    fun `selectAccountForSending excludes auto-paused accounts`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("paused", strategyWeight = 200, dailySendLimit = 100, todaySentCount = 0, autoSendPaused = true),
                account("balanced", strategyWeight = 80, dailySendLimit = 100, todaySentCount = 10, autoSendPaused = false)
            )
        )

        val selected = service.selectAccountForSending()

        assertEquals("balanced", selected.accountCode)
    }

    @Test
    fun `selectAccountForSending throws when all enabled accounts are auto-paused`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("paused", strategyWeight = 200, dailySendLimit = 100, todaySentCount = 0, autoSendPaused = true)
            )
        )

        assertThrows(IllegalStateException::class.java) {
            service.selectAccountForSending()
        }
    }

    @Test
    fun `creates sender account when account code is unique`() {
        Mockito.`when`(repository.existsByAccountCode("new_account")).thenReturn(false)
        Mockito.`when`(repository.save(Mockito.any(MailSenderAccount::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as MailSenderAccount }

        val created = service.createAccount(
            MailSenderAccountCreateCommand(
                accountCode = "new_account",
                senderEmail = "new_account@qftechtalent.com",
                senderName = "New Account",
                senderTitle = "Customer Care Officer",
                senderDisplayName = "New Account",
                teamName = "Qingfei Tech Talent Team",
                countryName = "China",
                smtpHost = "smtp.example.com",
                smtpPort = 465,
                smtpUsername = "new_account@qftechtalent.com",
                smtpPassword = "secret",
                imapHost = "imap.example.com",
                imapPort = 993,
                imapUsername = "new_account@qftechtalent.com",
                imapPassword = "secret",
                strategyWeight = 120,
                dailySendLimit = 80
            )
        )

        assertEquals("new_account", created.accountCode)
        assertEquals(120, created.strategyWeight)
        assertEquals(80, created.dailySendLimit)
        assertFalse(created.enabled)
        assertNotNull(created.createdAt)
        assertNotNull(created.updatedAt)
    }

    @Test
    fun `createAccount rejects blank smtp password`() {
        Mockito.`when`(repository.existsByAccountCode("new_account")).thenReturn(false)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.createAccount(
                MailSenderAccountCreateCommand(
                    accountCode = "new_account",
                    senderEmail = "new_account@qftechtalent.com",
                    senderName = "New Account",
                    senderTitle = null,
                    senderDisplayName = null,
                    teamName = null,
                    countryName = null,
                    smtpHost = "smtp.example.com",
                    smtpPort = 465,
                    smtpUsername = "new_account@qftechtalent.com",
                    smtpPassword = "",
                    imapHost = "imap.example.com",
                    imapPort = 993,
                    imapUsername = "new_account@qftechtalent.com",
                    imapPassword = "secret"
                )
            )
        }

        assertTrue(ex.message!!.contains("smtpPassword"))
    }

    @Test
    fun `setEnabled requires connectivity test to pass when enabling`() {
        Mockito.`when`(connectivityService.testAccount("a1")).thenReturn(
            MailAccountConnectivityResult(
                accountCode = "a1",
                smtp = MailProtocolConnectivityResult("SMTP", "smtp.example.com", 465, false, "auth failed"),
                imap = MailProtocolConnectivityResult("IMAP", "imap.example.com", 993, true, "ok"),
                passed = false
            )
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            service.setEnabled("a1", true)
        }

        assertTrue(ex.message!!.contains("连通性测试未通过"))
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(MailSenderAccount::class.java) ?: account("__any__"))
    }

    @Test
    fun `setEnabled succeeds when connectivity test passes`() {
        Mockito.`when`(connectivityService.testAccount("a1")).thenReturn(
            MailAccountConnectivityResult(
                accountCode = "a1",
                smtp = MailProtocolConnectivityResult("SMTP", "smtp.example.com", 465, true, "ok"),
                imap = MailProtocolConnectivityResult("IMAP", "imap.example.com", 993, true, "ok"),
                passed = true
            )
        )
        Mockito.`when`(repository.findByAccountCode("a1"))
            .thenReturn(account("a1", enabled = false))
        Mockito.`when`(repository.save(Mockito.any(MailSenderAccount::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as MailSenderAccount }

        val enabled = service.setEnabled("a1", true)

        assertTrue(enabled.enabled)
    }

    @Test
    fun `updateAccount preserves passwords when update command omits them`() {
        Mockito.`when`(repository.findByAccountCode("a1"))
            .thenReturn(account("a1", strategyWeight = 100, dailySendLimit = 100, todaySentCount = 0))
        Mockito.`when`(repository.save(Mockito.any(MailSenderAccount::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as MailSenderAccount }

        val updated = service.updateAccount(
            "a1",
            MailSenderAccountUpdateCommand(
                senderEmail = "updated@qftechtalent.com",
                senderName = "Updated",
                senderTitle = "Customer Care Officer",
                senderDisplayName = "Updated",
                teamName = "Qingfei Tech Talent Team",
                countryName = "China",
                smtpHost = "smtp2.example.com",
                smtpPort = 587,
                smtpUsername = "updated@qftechtalent.com",
                smtpPassword = null,
                imapHost = "imap2.example.com",
                imapPort = 993,
                imapUsername = "updated@qftechtalent.com",
                imapPassword = null,
                strategyWeight = 120,
                dailySendLimit = 80,
                todaySentCount = 5,
                enabled = true
            )
        )

        assertEquals("secret", updated.smtpPassword)
        assertEquals("secret", updated.imapPassword)
        Mockito.verify(connectivityService, Mockito.never()).testAccount("a1")
    }

    @Test
    fun `updateAccount requires connectivity test when enabling from disabled`() {
        Mockito.`when`(repository.findByAccountCode("a1"))
            .thenReturn(account("a1", enabled = false))
        Mockito.`when`(connectivityService.testAccount("a1")).thenReturn(
            MailAccountConnectivityResult(
                accountCode = "a1",
                smtp = MailProtocolConnectivityResult("SMTP", "smtp.example.com", 465, false, "auth failed"),
                imap = MailProtocolConnectivityResult("IMAP", "imap.example.com", 993, true, "ok"),
                passed = false
            )
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            service.updateAccount("a1", updateCommand(enabled = true))
        }

        assertTrue(ex.message!!.contains("连通性测试未通过"))
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(MailSenderAccount::class.java) ?: account("__any__"))
    }

    @Test
    fun `updateAccount enables account when connectivity test passes`() {
        Mockito.`when`(repository.findByAccountCode("a1"))
            .thenReturn(account("a1", enabled = false))
        Mockito.`when`(connectivityService.testAccount("a1")).thenReturn(
            MailAccountConnectivityResult(
                accountCode = "a1",
                smtp = MailProtocolConnectivityResult("SMTP", "smtp.example.com", 465, true, "ok"),
                imap = MailProtocolConnectivityResult("IMAP", "imap.example.com", 993, true, "ok"),
                passed = true
            )
        )
        Mockito.`when`(repository.save(Mockito.any(MailSenderAccount::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as MailSenderAccount }

        val updated = service.updateAccount("a1", updateCommand(enabled = true))

        assertTrue(updated.enabled)
    }

    @Test
    fun `updateAccount does not require connectivity test when account stays enabled`() {
        Mockito.`when`(repository.findByAccountCode("a1"))
            .thenReturn(account("a1", enabled = true))
        Mockito.`when`(repository.save(Mockito.any(MailSenderAccount::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as MailSenderAccount }

        service.updateAccount("a1", updateCommand(enabled = true))

        Mockito.verify(connectivityService, Mockito.never()).testAccount("a1")
    }

    @Test
    fun `deleteAccount removes account when no campaign references exist`() {
        val existing = account("a1").copy(id = 10L)
        Mockito.`when`(repository.findByAccountCode("a1")).thenReturn(existing)
        Mockito.`when`(campaignRepository.existsBySenderAccountId(10L)).thenReturn(false)

        service.deleteAccount("a1")

        Mockito.verify(repository).deleteById(10L)
        Mockito.verify(smtpSenderFactory).evict("a1")
    }

    @Test
    fun `deleteAccount rejects simulator account`() {
        Mockito.`when`(repository.findByAccountCode("SIMULATOR_NOOP"))
            .thenReturn(account("SIMULATOR_NOOP"))

        val ex = assertThrows(IllegalStateException::class.java) {
            service.deleteAccount("SIMULATOR_NOOP")
        }

        assertTrue(ex.message!!.contains("模拟器账号不可删除"))
    }

    @Test
    fun `deleteAccount rejects account referenced by campaign`() {
        val existing = account("a1").copy(id = 10L)
        Mockito.`when`(repository.findByAccountCode("a1")).thenReturn(existing)
        Mockito.`when`(campaignRepository.existsBySenderAccountId(10L)).thenReturn(true)

        val ex = assertThrows(IllegalStateException::class.java) {
            service.deleteAccount("a1")
        }

        assertTrue(ex.message!!.contains("该账号已被活动引用"))
    }

    @Test
    fun `disables sender account`() {
        Mockito.`when`(repository.findByAccountCode("a1"))
            .thenReturn(account("a1", strategyWeight = 100, dailySendLimit = 100, todaySentCount = 0))
        Mockito.`when`(repository.save(Mockito.any(MailSenderAccount::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as MailSenderAccount }

        val disabled = service.setEnabled("a1", false)

        assertFalse(disabled.enabled)
        Mockito.verify(smtpSenderFactory).evict("a1")
    }

    @Test
    fun `updates sender account and evicts cached smtp sender`() {
        Mockito.`when`(repository.findByAccountCode("a1"))
            .thenReturn(account("a1", strategyWeight = 100, dailySendLimit = 100, todaySentCount = 0))
        Mockito.`when`(repository.save(Mockito.any(MailSenderAccount::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as MailSenderAccount }

        val updated = service.updateAccount(
            "a1",
            MailSenderAccountUpdateCommand(
                senderEmail = "updated@qftechtalent.com",
                senderName = "Updated",
                senderTitle = "Customer Care Officer",
                senderDisplayName = "Updated",
                teamName = "Qingfei Tech Talent Team",
                countryName = "China",
                smtpHost = "smtp2.example.com",
                smtpPort = 587,
                smtpUsername = "updated@qftechtalent.com",
                smtpPassword = "new-secret",
                imapHost = "imap2.example.com",
                imapPort = 993,
                imapUsername = "updated@qftechtalent.com",
                imapPassword = "new-secret",
                strategyWeight = 120,
                dailySendLimit = 80,
                todaySentCount = 5,
                enabled = true
            )
        )

        assertEquals("smtp2.example.com", updated.smtpHost)
        assertEquals(587, updated.smtpPort)
        Mockito.verify(smtpSenderFactory).evict("a1")
    }

    @Test
    fun `resets today sent count`() {
        Mockito.`when`(repository.findByAccountCode("a1"))
            .thenReturn(account("a1", strategyWeight = 100, dailySendLimit = 100, todaySentCount = 25))
        Mockito.`when`(repository.save(Mockito.any(MailSenderAccount::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as MailSenderAccount }

        val reset = service.resetTodaySentCount("a1")

        assertEquals(0, reset.todaySentCount)
    }

    @Test
    fun `listAutoReceiveAccounts returns enabled real accounts only`() {
        Mockito.`when`(repository.findAllByEnabledTrueAndAccountCodeNot("SIMULATOR_NOOP")).thenReturn(
            listOf(account("a1"), account("a2"))
        )

        val result = service.listAutoReceiveAccounts()

        assertEquals(2, result.size)
        assertTrue(result.all { it.enabled })
        assertTrue(result.none { it.accountCode == "SIMULATOR_NOOP" })
    }

    @Test
    fun `listAutoReceiveAccounts excludes SIMULATOR_NOOP`() {
        Mockito.`when`(repository.findAllByEnabledTrueAndAccountCodeNot("SIMULATOR_NOOP")).thenReturn(
            emptyList()
        )

        val result = service.listAutoReceiveAccounts()

        assertEquals(0, result.size)
    }

    @Test
    fun `getAutoReceiveAccount returns enabled real account`() {
        Mockito.`when`(repository.findByAccountCode("real_acct")).thenReturn(
            account("real_acct", enabled = true)
        )

        val result = service.getAutoReceiveAccount("real_acct")

        assertEquals("real_acct", result.accountCode)
    }

    @Test
    fun `getAutoReceiveAccount rejects SIMULATOR_NOOP`() {
        Mockito.`when`(repository.findByAccountCode("SIMULATOR_NOOP")).thenReturn(
            account("SIMULATOR_NOOP", enabled = true)
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            service.getAutoReceiveAccount("SIMULATOR_NOOP")
        }
        assertTrue(ex.message!!.contains("not allowed"))
    }

    @Test
    fun `getAutoReceiveAccount rejects disabled account`() {
        Mockito.`when`(repository.findByAccountCode("disabled_acct")).thenReturn(
            account("disabled_acct", enabled = false)
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            service.getAutoReceiveAccount("disabled_acct")
        }
        assertTrue(ex.message!!.contains("disabled"))
    }

    @Test
    fun `getAutoReceiveAccount rejects unknown account`() {
        Mockito.`when`(repository.findByAccountCode("unknown")).thenReturn(null)

        val ex = assertThrows(IllegalStateException::class.java) {
            service.getAutoReceiveAccount("unknown")
        }
        assertTrue(ex.message!!.contains("not found"))
    }

    @Test
    fun `listEnabledAccounts unchanged returns all enabled including simulator`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(account("a1"), account("SIMULATOR_NOOP"))
        )

        val result = service.listEnabledAccounts()

        assertEquals(2, result.size)
        assertTrue(result.any { it.accountCode == "SIMULATOR_NOOP" })
    }

    @Test
    fun `pauseAutoSend delegates to repository with reason and timestamp`() {
        service.pauseAutoSend("a1", "SELF_CHECK_FAILED:boom")

        Mockito.verify(repository).pauseAutoSend(
            Mockito.eq("a1") ?: "a1",
            Mockito.eq("SELF_CHECK_FAILED:boom") ?: "",
            Mockito.any(java.time.LocalDateTime::class.java) ?: java.time.LocalDateTime.now()
        )
    }

    @Test
    fun `pauseAutoSend does not modify enabled flag`() {
        service.pauseAutoSend("a1", "reason")

        Mockito.verify(repository, Mockito.never())
            .save(Mockito.any(MailSenderAccount::class.java) ?: account("__any__"))
    }

    @Test
    fun `resumeAutoSend delegates to repository and invalidates self-check cache`() {
        service.resumeAutoSend("a1")

        Mockito.verify(repository).resumeAutoSend(Mockito.eq("a1") ?: "a1")
        Mockito.verify(selfCheckService).invalidate("a1")
    }

    @Test
    fun `resumeAutoSend does not modify enabled flag`() {
        service.resumeAutoSend("a1")

        Mockito.verify(repository, Mockito.never())
            .save(Mockito.any(MailSenderAccount::class.java) ?: account("__any__"))
    }

    @Test
    fun `listSendableAccounts returns enabled non-paused non-simulator accounts under limit`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account("ok", dailySendLimit = 100, todaySentCount = 5, autoSendPaused = false),
                account("paused", dailySendLimit = 100, todaySentCount = 0, autoSendPaused = true),
                account("exhausted", dailySendLimit = 100, todaySentCount = 100, autoSendPaused = false),
                account("SIMULATOR_NOOP", dailySendLimit = 100, todaySentCount = 0, autoSendPaused = false)
            )
        )

        val result = service.listSendableAccounts()

        assertEquals(1, result.size)
        assertEquals("ok", result[0].accountCode)
    }

    @Test
    fun `resetDailyCounts delegates to repository with today start and returns aggregated result`() {
        val todayStart = java.time.LocalDate.now().atStartOfDay()
        Mockito.`when`(repository.resetDailyCountsBeforeDate(todayStart)).thenReturn(3)
        Mockito.`when`(repository.resumeDailyLimitPausedAccounts()).thenReturn(2)

        val result = service.resetDailyCounts()

        assertEquals(3, result.countReset)
        assertEquals(2, result.pauseResumed)
        Mockito.verify(repository).resetDailyCountsBeforeDate(todayStart)
        Mockito.verify(repository).resumeDailyLimitPausedAccounts()
    }

    @Test
    fun `resetDailyCounts is idempotent when repository returns zero rows affected`() {
        val todayStart = java.time.LocalDate.now().atStartOfDay()
        Mockito.`when`(repository.resetDailyCountsBeforeDate(todayStart)).thenReturn(0)
        Mockito.`when`(repository.resumeDailyLimitPausedAccounts()).thenReturn(0)

        val result = service.resetDailyCounts()

        assertEquals(0, result.countReset)
        assertEquals(0, result.pauseResumed)
    }

    @Test
    fun `resetDailyCounts uses today start boundary for L4-1 last_sent_at filter`() {
        val todayStart = java.time.LocalDate.now().atStartOfDay()

        service.resetDailyCounts()

        Mockito.verify(repository).resetDailyCountsBeforeDate(
            Mockito.eq(todayStart) ?: todayStart
        )
    }

    @Test
    fun `resetDailyCounts resumes only DAILY_LIMIT paused accounts via repository query`() {
        service.resetDailyCounts()

        Mockito.verify(repository).resumeDailyLimitPausedAccounts()
        Mockito.verify(repository, Mockito.never())
            .resumeAutoSend(Mockito.anyString() ?: "__any__")
    }

    @Test
    fun `listSendableAccounts excludes account at warmup effective limit below dailySendLimit`() {
        val enabledWarmup = SenderWarmupService(
            WarmupProperties(
                enabled = true,
                steps = listOf(WarmupStep(1, 20))
            ),
            ObjectMapper().registerKotlinModule()
        )
        val serviceWithWarmup = MailSenderAccountService(
            repository,
            selfCheckService,
            smtpSenderFactory,
            enabledWarmup,
            connectivityService,
            campaignRepository
        )
        val now = java.time.LocalDateTime.of(2026, 6, 20, 12, 0)
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account(
                    "warmup_exhausted",
                    dailySendLimit = 500,
                    todaySentCount = 20,
                    createdAt = now
                ),
                account("ok", dailySendLimit = 500, todaySentCount = 5, createdAt = now.minusDays(30))
            )
        )

        val result = serviceWithWarmup.listSendableAccounts()

        assertEquals(1, result.size)
        assertEquals("ok", result[0].accountCode)
    }

    @Test
    fun `listSendableAccounts excludes auto-paused account even when under warmup effective limit`() {
        val enabledWarmup = SenderWarmupService(
            WarmupProperties(
                enabled = true,
                steps = listOf(WarmupStep(1, 20))
            ),
            ObjectMapper().registerKotlinModule()
        )
        val serviceWithWarmup = MailSenderAccountService(
            repository,
            selfCheckService,
            smtpSenderFactory,
            enabledWarmup,
            connectivityService,
            campaignRepository
        )
        val now = java.time.LocalDateTime.of(2026, 6, 20, 12, 0)
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account(
                    "paused",
                    dailySendLimit = 500,
                    todaySentCount = 0,
                    autoSendPaused = true,
                    createdAt = now
                )
            )
        )

        val result = serviceWithWarmup.listSendableAccounts()

        assertEquals(0, result.size)
    }

    @Test
    fun `listSendableAccounts includes warmup-exhausted account when ignoreWarmup is true`() {
        val enabledWarmup = SenderWarmupService(
            WarmupProperties(
                enabled = true,
                steps = listOf(WarmupStep(1, 20))
            ),
            ObjectMapper().registerKotlinModule()
        )
        val serviceWithWarmup = MailSenderAccountService(
            repository,
            selfCheckService,
            smtpSenderFactory,
            enabledWarmup,
            connectivityService,
            campaignRepository
        )
        val now = java.time.LocalDateTime.of(2026, 6, 20, 12, 0)
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account(
                    "warmup_exhausted",
                    dailySendLimit = 100,
                    todaySentCount = 20,
                    createdAt = now,
                    warmupEnabled = true,
                    warmupStartedAt = now,
                    warmupStepsJson = """[{"dayFrom":1,"limit":20}]"""
                )
            )
        )

        val withWarmup = serviceWithWarmup.listSendableAccounts(ignoreWarmup = false)
        val bypassWarmup = serviceWithWarmup.listSendableAccounts(ignoreWarmup = true)

        assertEquals(0, withWarmup.size)
        assertEquals(1, bypassWarmup.size)
        assertEquals("warmup_exhausted", bypassWarmup[0].accountCode)
    }

    @Test
    fun `listSendableAccounts excludes account at dailySendLimit even when ignoreWarmup is true`() {
        val enabledWarmup = SenderWarmupService(
            WarmupProperties(
                enabled = true,
                steps = listOf(WarmupStep(1, 20))
            ),
            ObjectMapper().registerKotlinModule()
        )
        val serviceWithWarmup = MailSenderAccountService(
            repository,
            selfCheckService,
            smtpSenderFactory,
            enabledWarmup,
            connectivityService,
            campaignRepository
        )
        val now = java.time.LocalDateTime.of(2026, 6, 20, 12, 0)
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(
                account(
                    "at_daily_limit",
                    dailySendLimit = 100,
                    todaySentCount = 100,
                    createdAt = now,
                    warmupEnabled = true,
                    warmupStartedAt = now,
                    warmupStepsJson = """[{"dayFrom":1,"limit":20}]"""
                )
            )
        )

        val result = serviceWithWarmup.listSendableAccounts(ignoreWarmup = true)

        assertEquals(0, result.size)
    }

    @Test
    fun `resetDailyCounts is transactional`() {
        val method = MailSenderAccountService::class.java.getMethod("resetDailyCounts")

        assertNotNull(method.getAnnotation(Transactional::class.java))
    }

    private fun updateCommand(enabled: Boolean): MailSenderAccountUpdateCommand =
        MailSenderAccountUpdateCommand(
            senderEmail = "updated@qftechtalent.com",
            senderName = "Updated",
            senderTitle = "Customer Care Officer",
            senderDisplayName = "Updated",
            teamName = "Qingfei Tech Talent Team",
            countryName = "China",
            smtpHost = "smtp2.example.com",
            smtpPort = 587,
            smtpUsername = "updated@qftechtalent.com",
            smtpPassword = null,
            imapHost = "imap2.example.com",
            imapPort = 993,
            imapUsername = "updated@qftechtalent.com",
            imapPassword = null,
            strategyWeight = 120,
            dailySendLimit = 80,
            todaySentCount = 5,
            enabled = enabled
        )

    private fun account(
        accountCode: String,
        strategyWeight: Int = 100,
        dailySendLimit: Int = 100,
        todaySentCount: Int = 0,
        enabled: Boolean = true,
        autoSendPaused: Boolean = false,
        createdAt: java.time.LocalDateTime? = null,
        warmupEnabled: Boolean? = null,
        warmupStartedAt: java.time.LocalDateTime? = null,
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
            enabled = enabled,
            autoSendPaused = autoSendPaused,
            createdAt = createdAt,
            warmupEnabled = warmupEnabled,
            warmupStartedAt = warmupStartedAt,
            warmupStepsJson = warmupStepsJson
        )
}
