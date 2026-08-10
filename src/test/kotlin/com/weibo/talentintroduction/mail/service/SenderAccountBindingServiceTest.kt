package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class SenderAccountBindingServiceTest {
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    // warmup 不可 mock（final class）：真实实例 + disabled 配置 ⇒ effectiveDailyLimit = dailySendLimit，确定可推演
    private val warmup = SenderWarmupService(
        WarmupProperties(enabled = false),
        ObjectMapper().registerKotlinModule()
    )
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val service = SenderAccountBindingService(
        mailSenderAccountService,
        warmup,
        expertContactRepository
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
}
