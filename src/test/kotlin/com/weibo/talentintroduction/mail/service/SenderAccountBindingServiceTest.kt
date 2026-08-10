package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class SenderAccountBindingServiceTest {
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    // warmup 不可 mock（final class）：真实实例 + disabled 配置 ⇒ effectiveDailyLimit = dailySendLimit，确定可推演
    private val warmup = SenderWarmupService(
        WarmupProperties(enabled = false),
        ObjectMapper().registerKotlinModule()
    )
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val service = SenderAccountBindingService(
        mailSenderAccountService,
        warmup,
        expertContactRepository,
        operatorActionLogService
    )

    private val now = LocalDateTime.of(2026, 8, 10, 12, 0, 0)

    @Test
    fun `bindingFieldsFor rejects blank account code`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.bindingFieldsFor("   ", now)
        }
    }

    @Test
    fun `bindingFieldsFor rejects simulator account`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.bindingFieldsFor(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE, now)
        }
        assertEquals("SIMULATOR_NOOP must never be bound to an expert contact", ex.message)
    }

    @Test
    fun `resolveForSend throws when contact has no binding`() {
        val contact = contact(boundCode = null)

        val ex = assertThrows(SenderAccountNotBoundException::class.java) {
            service.resolveForSend(contact, manual = false)
        }

        assertEquals(42L, ex.contactId)
    }

    @Test
    fun `resolveForSend throws when bound account disabled for manual send`() {
        val acc = account("chen", enabled = false)
        val contact = contact(boundCode = "chen")
        Mockito.`when`(mailSenderAccountService.getAccount("chen")).thenReturn(acc)

        val ex = assertThrows(BoundSenderAccountUnavailableException::class.java) {
            service.resolveForSend(contact, manual = true)
        }

        assertEquals("DISABLED", ex.reason)
    }

    @Test
    fun `resolveForSend allows auto-paused account for manual send`() {
        val acc = account("chen", autoSendPaused = true)
        val contact = contact(boundCode = "chen")
        Mockito.`when`(mailSenderAccountService.getAccount("chen")).thenReturn(acc)

        val resolved = service.resolveForSend(contact, manual = true)

        assertEquals("chen", resolved.accountCode)
    }

    @Test
    fun `resolveForSend allows account at daily limit for manual send`() {
        val acc = account("chen", dailySendLimit = 100, todaySentCount = 100)
        val contact = contact(boundCode = "chen")
        Mockito.`when`(mailSenderAccountService.getAccount("chen")).thenReturn(acc)

        val resolved = service.resolveForSend(contact, manual = true)

        assertEquals("chen", resolved.accountCode)
    }

    @Test
    fun `resolveForSend throws when auto path hits auto-send pause`() {
        val acc = account("chen", autoSendPaused = true)
        val contact = contact(boundCode = "chen")
        Mockito.`when`(mailSenderAccountService.getAccount("chen")).thenReturn(acc)

        val ex = assertThrows(BoundSenderAccountUnavailableException::class.java) {
            service.resolveForSend(contact, manual = false)
        }

        assertEquals("AUTO_SEND_PAUSED", ex.reason)
    }

    @Test
    fun `resolveForSend throws when auto path hits daily limit`() {
        val acc = account("chen", dailySendLimit = 100, todaySentCount = 100)
        val contact = contact(boundCode = "chen")
        Mockito.`when`(mailSenderAccountService.getAccount("chen")).thenReturn(acc)

        val ex = assertThrows(BoundSenderAccountUnavailableException::class.java) {
            service.resolveForSend(contact, manual = false)
        }

        assertEquals("DAILY_LIMIT_REACHED", ex.reason)
    }

    @Test
    fun `resolveForSend throws for simulator binding`() {
        val acc = account(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        val contact = contact(boundCode = MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        Mockito.`when`(mailSenderAccountService.getAccount(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE))
            .thenReturn(acc)

        val ex = assertThrows(BoundSenderAccountUnavailableException::class.java) {
            service.resolveForSend(contact, manual = false)
        }

        assertEquals("SIMULATOR", ex.reason)
    }

    @Test
    fun `bindIfAbsent writes via column-specific update`() {
        service.bindIfAbsent(42L, "chen", now)

        Mockito.verify(expertContactRepository).updateBindingById(
            Mockito.eq(42L),
            Mockito.eq("chen"),
            Mockito.eq(now)
        )
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
    }

    @Test
    fun `rebind sets change mark and writes audit`() {
        val contact = contact(boundCode = "old")
        Mockito.`when`(expertContactRepository.findById(42L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getAccount("new")).thenReturn(account("new"))

        val result = service.rebind(
            42L, RebindCommand(senderAccountCode = "new", operatorName = "admin", note = "账号A退信率高")
        )

        assertEquals(contact, result)
        Mockito.verify(expertContactRepository).rebindSenderAccountById(
            eqValue(42L),
            eqValue("new"),
            anyNonNull(now)
        )
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
        Mockito.verify(operatorActionLogService).record(
            eqValue("EXPERT_CONTACT"),
            eqValue(42L),
            eqValue(OperatorActionType.CHANGE_SENDER_ACCOUNT),
            eqValue(42L),
            Mockito.isNull(),
            eqValue(mapOf("boundSenderAccountCode" to "old")),
            eqValue(mapOf("boundSenderAccountCode" to "new")),
            eqValue("admin"),
            eqValue("账号A退信率高"),
            Mockito.isNull()
        )
    }

    @Test
    fun `rebind is a no-op when target equals current binding`() {
        val contact = contact(boundCode = "new")
        Mockito.`when`(expertContactRepository.findById(42L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getAccount("new")).thenReturn(account("new"))

        val result = service.rebind(42L, RebindCommand(senderAccountCode = "new", operatorName = "admin", note = null))

        assertEquals(contact, result)
        Mockito.verify(expertContactRepository, Mockito.never()).rebindSenderAccountById(
            anyNonNull(0L), anyNonNull(""), anyNonNull(now)
        )
        Mockito.verifyNoInteractions(operatorActionLogService)
    }

    @Test
    fun `rebind rejects disabled target`() {
        val contact = contact(boundCode = "old")
        Mockito.`when`(expertContactRepository.findById(42L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getAccount("disabled")).thenReturn(account("disabled", enabled = false))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.rebind(42L, RebindCommand(senderAccountCode = "disabled", operatorName = null, note = null))
        }

        assertTrue(ex.message!!.contains("已禁用"))
        Mockito.verify(expertContactRepository, Mockito.never()).rebindSenderAccountById(
            anyNonNull(0L), anyNonNull(""), anyNonNull(now)
        )
        Mockito.verifyNoInteractions(operatorActionLogService)
    }

    @Test
    fun `rebind rejects simulator target`() {
        val contact = contact(boundCode = "old")
        Mockito.`when`(expertContactRepository.findById(42L)).thenReturn(Optional.of(contact))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.rebind(
                42L, RebindCommand(
                    senderAccountCode = MailSenderAccountService.SIMULATOR_ACCOUNT_CODE,
                    operatorName = null,
                    note = null
                )
            )
        }

        assertEquals("模拟器账号不可作为绑定目标", ex.message)
        Mockito.verify(expertContactRepository, Mockito.never()).rebindSenderAccountById(
            anyNonNull(0L), anyNonNull(""), anyNonNull(now)
        )
        Mockito.verifyNoInteractions(operatorActionLogService)
    }

    @Test
    fun `rebind note is truncated at 500 chars`() {
        val contact = contact(boundCode = "old")
        Mockito.`when`(expertContactRepository.findById(42L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getAccount("new")).thenReturn(account("new"))

        service.rebind(42L, RebindCommand(senderAccountCode = "new", operatorName = null, note = "x".repeat(600)))

        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(operatorActionLogService).record(
            eqValue("EXPERT_CONTACT"),
            eqValue(42L),
            eqValue(OperatorActionType.CHANGE_SENDER_ACCOUNT),
            eqValue(42L),
            Mockito.isNull(),
            eqValue(mapOf("boundSenderAccountCode" to "old")),
            eqValue(mapOf("boundSenderAccountCode" to "new")),
            Mockito.isNull(),
            captor.capture(),
            Mockito.isNull()
        )
        assertEquals(500, captor.value.length - "…(truncated)".length)
        assertTrue(captor.value.endsWith("…(truncated)"))
    }

    @Test
    fun `rebind appends active thread hint`() {
        val contact = contact(boundCode = "old").copy(currentStatus = "WAITING_REPLY")
        Mockito.`when`(expertContactRepository.findById(42L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getAccount("new")).thenReturn(account("new"))

        service.rebind(42L, RebindCommand(senderAccountCode = "new", operatorName = null, note = null))

        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(operatorActionLogService).record(
            eqValue("EXPERT_CONTACT"),
            eqValue(42L),
            eqValue(OperatorActionType.CHANGE_SENDER_ACCOUNT),
            eqValue(42L),
            Mockito.isNull(),
            eqValue(mapOf("boundSenderAccountCode" to "old")),
            eqValue(mapOf("boundSenderAccountCode" to "new")),
            Mockito.isNull(),
            captor.capture(),
            Mockito.isNull()
        )
        assertTrue(captor.value.contains("存在进行中的会话"))
    }

    @Test
    fun `migrate does not touch change mark`() {
        Mockito.`when`(mailSenderAccountService.getAccount("new")).thenReturn(account("new"))
        Mockito.`when`(expertContactRepository.findAllByBoundSenderAccountCode("old"))
            .thenReturn(listOf(contact(boundCode = "old")))
        Mockito.`when`(expertContactRepository.migrateBindingByAccount(eqValue("old"), eqValue("new"), anyNonNull(now)))
            .thenReturn(1)

        val result = service.migrateAccount(
            MigrateCommand(fromAccountCode = "old", toAccountCode = "new", operatorName = "admin", reason = null)
        )

        assertEquals(1, result.migrated)
        Mockito.verify(expertContactRepository).migrateBindingByAccount(
            eqValue("old"), eqValue("new"), anyNonNull(now)
        )
        Mockito.verify(expertContactRepository, Mockito.never()).rebindSenderAccountById(
            anyNonNull(0L), anyNonNull(""), anyNonNull(now)
        )
    }

    @Test
    fun `migrate writes one audit row per contact`() {
        Mockito.`when`(mailSenderAccountService.getAccount("new")).thenReturn(account("new"))
        val c1 = contact(boundCode = "old").copy(id = 11L)
        val c2 = contact(boundCode = "old").copy(id = 12L)
        val c3 = contact(boundCode = "old").copy(id = 13L)
        Mockito.`when`(expertContactRepository.findAllByBoundSenderAccountCode("old"))
            .thenReturn(listOf(c1, c2, c3))
        Mockito.`when`(expertContactRepository.migrateBindingByAccount(eqValue("old"), eqValue("new"), anyNonNull(now)))
            .thenReturn(3)

        val result = service.migrateAccount(
            MigrateCommand(fromAccountCode = "old", toAccountCode = "new", operatorName = "admin", reason = "账号被封")
        )

        assertEquals(3, result.migrated)
        val captor = ArgumentCaptor.forClass(Long::class.java)
        Mockito.verify(operatorActionLogService, Mockito.times(3)).record(
            eqValue("EXPERT_CONTACT"),
            anyNonNull(0L),
            eqValue(OperatorActionType.MIGRATE_SENDER_ACCOUNT),
            captor.capture(),
            Mockito.isNull(),
            eqValue(mapOf("boundSenderAccountCode" to "old")),
            eqValue(mapOf("boundSenderAccountCode" to "new")),
            eqValue("admin"),
            eqValue("账号被封"),
            Mockito.isNull()
        )
        assertEquals(setOf(11L, 12L, 13L), captor.allValues.toSet())
    }

    @Test
    fun `migrate rejects same source and target`() {
        Mockito.`when`(mailSenderAccountService.getAccount("same")).thenReturn(account("same"))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.migrateAccount(MigrateCommand(fromAccountCode = "same", toAccountCode = "same", operatorName = null, reason = null))
        }

        assertEquals("源账号与目标账号相同，无需迁移", ex.message)
        Mockito.verifyNoInteractions(operatorActionLogService)
    }

    @Test
    fun `migrate with no affected contacts writes nothing`() {
        Mockito.`when`(mailSenderAccountService.getAccount("new")).thenReturn(account("new"))

        val result = service.migrateAccount(
            MigrateCommand(fromAccountCode = "old", toAccountCode = "new", operatorName = "admin", reason = null)
        )

        assertEquals(0, result.migrated)
        Mockito.verify(expertContactRepository, Mockito.never()).migrateBindingByAccount(
            anyNonNull(""), anyNonNull(""), anyNonNull(now)
        )
        Mockito.verifyNoInteractions(operatorActionLogService)
    }

    @Test
    fun `migrate scope is source account only`() {
        Mockito.`when`(mailSenderAccountService.getAccount("new")).thenReturn(account("new"))
        Mockito.`when`(expertContactRepository.findAllByBoundSenderAccountCode("X"))
            .thenReturn(listOf(contact(boundCode = "X")))

        service.migrateAccount(MigrateCommand(fromAccountCode = "X", toAccountCode = "new", operatorName = null, reason = null))

        Mockito.verify(expertContactRepository).findAllByBoundSenderAccountCode("X")
        Mockito.verify(expertContactRepository).migrateBindingByAccount(
            eqValue("X"), eqValue("new"), anyNonNull(now)
        )
    }

    @Test
    fun `clearChangeMark only clears mark columns`() {
        val contact = contact(boundCode = "new")
        Mockito.`when`(expertContactRepository.findById(42L)).thenReturn(Optional.of(contact))

        val result = service.clearChangeMark(42L, "admin", "已知悉")

        assertEquals(contact, result)
        Mockito.verify(expertContactRepository).clearSenderChangeMarkById(42L)
        Mockito.verify(expertContactRepository, Mockito.never()).rebindSenderAccountById(
            anyNonNull(0L), anyNonNull(""), anyNonNull(now)
        )
        Mockito.verify(expertContactRepository, Mockito.never()).migrateBindingByAccount(
            anyNonNull(""), anyNonNull(""), anyNonNull(now)
        )
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
        Mockito.verify(operatorActionLogService).record(
            eqValue("EXPERT_CONTACT"),
            eqValue(42L),
            eqValue(OperatorActionType.CLEAR_SENDER_CHANGE_MARK),
            eqValue(42L),
            Mockito.isNull(),
            eqValue(mapOf("senderAccountChanged" to true)),
            eqValue(mapOf("senderAccountChanged" to false)),
            eqValue("admin"),
            eqValue("已知悉"),
            Mockito.isNull()
        )
    }

    private fun contact(boundCode: String?): ExpertContact =
        ExpertContact(
            id = 42L,
            campaignId = 1L,
            orcidId = "0001-0002-0003-0004",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            boundSenderAccountCode = boundCode
        )

    private fun account(
        accountCode: String,
        enabled: Boolean = true,
        autoSendPaused: Boolean = false,
        dailySendLimit: Int = 100,
        todaySentCount: Int = 0
    ): MailSenderAccount =
        MailSenderAccount(
            accountCode = accountCode,
            senderEmail = "$accountCode@example.com",
            senderName = accountCode,
            senderTitle = "Title",
            senderDisplayName = accountCode,
            teamName = "Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "$accountCode@example.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "$accountCode@example.com",
            imapPassword = "secret",
            dailySendLimit = dailySendLimit,
            todaySentCount = todaySentCount,
            enabled = enabled,
            autoSendPaused = autoSendPaused
        )

    // Kotlin 非空参数无法直接传 null 的 Mockito matcher（会触发 Intrinsics 非空检查），
    // 照抄仓库既有范式（AiReplyReviewAuditServiceTest / InitialOutreachServiceTest）：
    // matcher 注册 + 返回非空默认值。
    private fun <T> anyNonNull(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value
}
