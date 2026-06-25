package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.BatchSendSetting
import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.repository.BatchSendSettingRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

class BatchSendSettingServiceTest {

    private val repository = org.mockito.Mockito.mock(BatchSendSettingRepository::class.java)
    private val eventPublisher = org.mockito.Mockito.mock(ApplicationEventPublisher::class.java)

    private fun service(): BatchSendSettingService = BatchSendSettingService(repository, eventPublisher)

    private fun row(key: String, value: String, id: Long? = null): BatchSendSetting =
        BatchSendSetting(id = id, settingKey = key, settingValue = value, updatedAt = LocalDateTime.now())

    @Test
    fun `getConfig returns defaults when DB empty`() {
        `when`(repository.findAll()).thenReturn(emptyList())
        val cfg = service().getConfig()
        assertEquals(false, cfg.autoEnabled)
        assertEquals("0 0 0 * * ?", cfg.cron)
        assertEquals(1000, cfg.dailyCap)
        assertEquals(50, cfg.roundSize)
        assertEquals(1000L, cfg.perMailIntervalMs)
        assertEquals(60000L, cfg.perRoundIntervalMs)
        assertEquals(30, cfg.selfCheckTtlMinutes)
        assertEquals("", cfg.emailDomain)
    }

    @Test
    fun `getConfig overrides from DB values`() {
        `when`(repository.findAll()).thenReturn(listOf(
            row("batchSend.autoEnabled", "true"),
            row("batchSend.cron", "0 30 6 * * ?"),
            row("batchSend.dailyCap", "500"),
            row("batchSend.roundSize", "25"),
            row("batchSend.perMailIntervalMs", "2000"),
            row("batchSend.perRoundIntervalMs", "120000"),
            row("batchSend.selfCheckTtlMinutes", "60"),
            row("batchSend.emailDomain", "gmail.com")
        ))
        val cfg = service().getConfig()
        assertEquals(true, cfg.autoEnabled)
        assertEquals("0 30 6 * * ?", cfg.cron)
        assertEquals(500, cfg.dailyCap)
        assertEquals(25, cfg.roundSize)
        assertEquals(2000L, cfg.perMailIntervalMs)
        assertEquals(120000L, cfg.perRoundIntervalMs)
        assertEquals(60, cfg.selfCheckTtlMinutes)
        assertEquals("gmail.com", cfg.emailDomain)
    }

    @Test
    fun `getConfig partial DB still falls back to defaults for missing keys`() {
        `when`(repository.findAll()).thenReturn(listOf(
            row("batchSend.roundSize", "10")
        ))
        val cfg = service().getConfig()
        assertEquals(10, cfg.roundSize)
        assertEquals(false, cfg.autoEnabled)
        assertEquals(1000, cfg.dailyCap)
        assertEquals("0 0 0 * * ?", cfg.cron)
    }

    @Test
    fun `getConfig falls back to defaults on illegal DB values`() {
        `when`(repository.findAll()).thenReturn(listOf(
            row("batchSend.autoEnabled", "maybe"),
            row("batchSend.cron", "not a cron"),
            row("batchSend.dailyCap", "not-a-number"),
            row("batchSend.perMailIntervalMs", "oops")
        ))
        val cfg = service().getConfig()
        assertEquals(false, cfg.autoEnabled)
        assertEquals("0 0 0 * * ?", cfg.cron)
        assertEquals(1000, cfg.dailyCap)
        assertEquals(1000L, cfg.perMailIntervalMs)
    }

    @Test
    fun `getConfig falls back to defaults when DB read throws`() {
        `when`(repository.findAll()).thenThrow(RuntimeException("db down"))
        val cfg = service().getConfig()
        assertEquals(false, cfg.autoEnabled)
        assertEquals(1000, cfg.dailyCap)
        assertEquals(50, cfg.roundSize)
    }

    @Test
    fun `updateConfig persists all keys and returns updated config`() {
        val cmd = BatchSendConfigUpdateRequest(
            autoEnabled = true, cron = "0 15 3 * * ?",
            dailyCap = 200, roundSize = 20,
            perMailIntervalMs = 500, perRoundIntervalMs = 30000,
            selfCheckTtlMinutes = 15, emailDomain = "gmail.com"
        )
        `when`(repository.save(any())).thenAnswer { it.arguments[0] }
        `when`(repository.findAll()).thenReturn(listOf(
            row("batchSend.autoEnabled", "true"),
            row("batchSend.cron", "0 15 3 * * ?"),
            row("batchSend.dailyCap", "200"),
            row("batchSend.roundSize", "20"),
            row("batchSend.perMailIntervalMs", "500"),
            row("batchSend.perRoundIntervalMs", "30000"),
            row("batchSend.selfCheckTtlMinutes", "15"),
            row("batchSend.emailDomain", "gmail.com")
        ))

        val result = service().updateConfig(cmd)

        assertEquals(true, result.autoEnabled)
        assertEquals("0 15 3 * * ?", result.cron)
        assertEquals(200, result.dailyCap)
        assertEquals(20, result.roundSize)
        assertEquals(500L, result.perMailIntervalMs)
        assertEquals(30000L, result.perRoundIntervalMs)
        assertEquals(15, result.selfCheckTtlMinutes)
        assertEquals("gmail.com", result.emailDomain)

        val captor = ArgumentCaptor.forClass(BatchSendSetting::class.java)
        verify(repository, org.mockito.Mockito.times(8)).save(captor.capture())
        captor.allValues.forEach { saved ->
            assertTrue(saved.settingKey.startsWith("batchSend."))
        }
    }

    @Test
    fun `updateConfig upserts existing rows preserving id`() {
        val cmd = BatchSendConfigUpdateRequest(
            autoEnabled = true, cron = "0 0 0 * * ?",
            dailyCap = 1000, roundSize = 50,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000,
            selfCheckTtlMinutes = 30, emailDomain = ""
        )
        `when`(repository.findBySettingKey("batchSend.autoEnabled")).thenReturn(row("batchSend.autoEnabled", "false", id = 7L))
        `when`(repository.save(any())).thenAnswer { it.arguments[0] }
        `when`(repository.findAll()).thenReturn(emptyList())

        service().updateConfig(cmd)

        val captor = ArgumentCaptor.forClass(BatchSendSetting::class.java)
        verify(repository, org.mockito.Mockito.times(8)).save(captor.capture())
        val autoEnabledSave = captor.allValues.first { it.settingKey == "batchSend.autoEnabled" }
        assertEquals(7L, autoEnabledSave.id)
        assertEquals("true", autoEnabledSave.settingValue)
    }

    @Test
    fun `updateConfig rejects roundSize less than 1`() {
        val cmd = BatchSendConfigUpdateRequest(
            autoEnabled = false, cron = "0 0 0 * * ?",
            dailyCap = 10, roundSize = 0,
            perMailIntervalMs = 0, perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30
        )
        assertThrows(IllegalArgumentException::class.java) {
            service().updateConfig(cmd)
        }
    }

    @Test
    fun `updateConfig rejects dailyCap less than roundSize`() {
        val cmd = BatchSendConfigUpdateRequest(
            autoEnabled = false, cron = "0 0 0 * * ?",
            dailyCap = 10, roundSize = 20,
            perMailIntervalMs = 0, perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30
        )
        assertThrows(IllegalArgumentException::class.java) {
            service().updateConfig(cmd)
        }
    }

    @Test
    fun `updateConfig rejects negative perMailIntervalMs`() {
        val cmd = BatchSendConfigUpdateRequest(
            autoEnabled = false, cron = "0 0 0 * * ?",
            dailyCap = 100, roundSize = 10,
            perMailIntervalMs = -1, perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30
        )
        assertThrows(IllegalArgumentException::class.java) {
            service().updateConfig(cmd)
        }
    }

    @Test
    fun `updateConfig rejects negative perRoundIntervalMs`() {
        val cmd = BatchSendConfigUpdateRequest(
            autoEnabled = false, cron = "0 0 0 * * ?",
            dailyCap = 100, roundSize = 10,
            perMailIntervalMs = 0, perRoundIntervalMs = -5,
            selfCheckTtlMinutes = 30
        )
        assertThrows(IllegalArgumentException::class.java) {
            service().updateConfig(cmd)
        }
    }

    @Test
    fun `updateConfig rejects selfCheckTtlMinutes less than 1`() {
        val cmd = BatchSendConfigUpdateRequest(
            autoEnabled = false, cron = "0 0 0 * * ?",
            dailyCap = 100, roundSize = 10,
            perMailIntervalMs = 0, perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 0
        )
        assertThrows(IllegalArgumentException::class.java) {
            service().updateConfig(cmd)
        }
    }

    @Test
    fun `updateConfig rejects invalid cron`() {
        val cmd = BatchSendConfigUpdateRequest(
            autoEnabled = false, cron = "not a cron",
            dailyCap = 100, roundSize = 10,
            perMailIntervalMs = 0, perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30
        )
        assertThrows(IllegalArgumentException::class.java) {
            service().updateConfig(cmd)
        }
    }

    @Test
    fun `updateConfig rejects blank cron`() {
        val cmd = BatchSendConfigUpdateRequest(
            autoEnabled = false, cron = "",
            dailyCap = 100, roundSize = 10,
            perMailIntervalMs = 0, perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30
        )
        assertThrows(IllegalArgumentException::class.java) {
            service().updateConfig(cmd)
        }
    }

    @Test
    fun `updateConfig does not persist when validation fails`() {
        val cmd = BatchSendConfigUpdateRequest(
            autoEnabled = false, cron = "bad",
            dailyCap = 100, roundSize = 10,
            perMailIntervalMs = 0, perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30
        )
        assertThrows(IllegalArgumentException::class.java) {
            service().updateConfig(cmd)
        }
        verify(repository, org.mockito.Mockito.never()).save(any())
    }

    @Test
    fun `getRuntimeStatus returns defaults when DB empty`() {
        `when`(repository.findAll()).thenReturn(emptyList())
        val state = service().getRuntimeStatus()
        assertEquals("IDLE", state.status)
        assertEquals("NONE", state.mode)
        assertEquals("", state.pauseReason)
    }

    @Test
    fun `getRuntimeStatus reflects DB values`() {
        `when`(repository.findAll()).thenReturn(listOf(
            row("batchSend.runtimeStatus", "PAUSED"),
            row("batchSend.runtimeMode", "AUTO"),
            row("batchSend.pauseReason", "NO_AVAILABLE_ACCOUNT")
        ))
        val state = service().getRuntimeStatus()
        assertEquals("PAUSED", state.status)
        assertEquals("AUTO", state.mode)
        assertEquals("NO_AVAILABLE_ACCOUNT", state.pauseReason)
    }

    @Test
    fun `setRuntimeStatus upserts three runtime keys`() {
        `when`(repository.save(any())).thenAnswer { it.arguments[0] }
        service().setRuntimeStatus("PAUSED", "AUTO", "NO_AVAILABLE_ACCOUNT")
        val captor = ArgumentCaptor.forClass(BatchSendSetting::class.java)
        verify(repository, org.mockito.Mockito.times(3)).save(captor.capture())
        val keys = captor.allValues.map { it.settingKey }.toSet()
        assertTrue("batchSend.runtimeStatus" in keys)
        assertTrue("batchSend.runtimeMode" in keys)
        assertTrue("batchSend.pauseReason" in keys)
        val pauseReasonSave = captor.allValues.first { it.settingKey == "batchSend.pauseReason" }
        assertEquals("NO_AVAILABLE_ACCOUNT", pauseReasonSave.settingValue)
    }

    @Test
    fun `updateConfig publishes cron changed event when cron changes`() {
        `when`(repository.findAll()).thenReturn(listOf(
            row("batchSend.cron", "0 0 0 * * ?")
        ))
        `when`(repository.save(any())).thenAnswer { it.arguments[0] }

        val cmd = BatchSendConfigUpdateRequest(
            autoEnabled = false, cron = "0 0 8 * * ?",
            dailyCap = 1000, roundSize = 50,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000,
            selfCheckTtlMinutes = 30
        )
        service().updateConfig(cmd)

        val captor = ArgumentCaptor.forClass(BatchSendCronChangedEvent::class.java)
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals("0 0 0 * * ?", captor.value.oldCron)
        assertEquals("0 0 8 * * ?", captor.value.newCron)
    }

    @Test
    fun `updateConfig does not publish event when cron unchanged`() {
        `when`(repository.findAll()).thenReturn(listOf(
            row("batchSend.cron", "0 0 0 * * ?")
        ))
        `when`(repository.save(any())).thenAnswer { it.arguments[0] }

        val cmd = BatchSendConfigUpdateRequest(
            autoEnabled = true, cron = "0 0 0 * * ?",
            dailyCap = 500, roundSize = 25,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000,
            selfCheckTtlMinutes = 30
        )
        service().updateConfig(cmd)

        verify(eventPublisher, never()).publishEvent(any())
    }
}
