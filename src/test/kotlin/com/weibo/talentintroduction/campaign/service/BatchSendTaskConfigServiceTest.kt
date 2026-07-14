package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigCreateCommand
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigUpdateCommand
import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateDetail
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

class BatchSendTaskConfigServiceTest {

    private val repository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
    private val mailComposeTemplateService = Mockito.mock(MailComposeTemplateService::class.java)
    private val eventPublisher = Mockito.mock(ApplicationEventPublisher::class.java)
    private val objectMapper = ObjectMapper()

    private fun service() = BatchSendTaskConfigService(
        repository = repository,
        mailComposeTemplateService = mailComposeTemplateService,
        objectMapper = objectMapper,
        eventPublisher = eventPublisher
    )

    private fun createCmd(
        name: String = "每日介绍",
        autoEnabled: Boolean = false,
        cron: String = "0 0 9 * * ?",
        dailyCap: Int = 100,
        roundSize: Int = 10,
        perMailIntervalMs: Long = 1000,
        perRoundIntervalMs: Long = 60000,
        selfCheckTtlMinutes: Int = 30,
        funnelLevel: String? = null,
        tags: List<String> = emptyList(),
        emailDomain: String? = null,
        discipline: String? = null,
        templateId: Long? = null
    ) = BatchSendTaskConfigCreateCommand(
        configName = name,
        autoEnabled = autoEnabled,
        cron = cron,
        dailyCap = dailyCap,
        roundSize = roundSize,
        perMailIntervalMs = perMailIntervalMs,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = selfCheckTtlMinutes,
        funnelLevel = funnelLevel,
        tags = tags,
        emailDomain = emailDomain,
        discipline = discipline,
        templateId = templateId
    )

    private fun updateCmd(
        name: String = "每日介绍",
        autoEnabled: Boolean = false,
        cron: String = "0 0 9 * * ?",
        dailyCap: Int = 100,
        roundSize: Int = 10,
        perMailIntervalMs: Long = 1000,
        perRoundIntervalMs: Long = 60000,
        selfCheckTtlMinutes: Int = 30,
        funnelLevel: String? = null,
        tags: List<String> = emptyList(),
        emailDomain: String? = null,
        discipline: String? = null,
        templateId: Long? = null
    ) = BatchSendTaskConfigUpdateCommand(
        configName = name,
        autoEnabled = autoEnabled,
        cron = cron,
        dailyCap = dailyCap,
        roundSize = roundSize,
        perMailIntervalMs = perMailIntervalMs,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = selfCheckTtlMinutes,
        funnelLevel = funnelLevel,
        tags = tags,
        emailDomain = emailDomain,
        discipline = discipline,
        templateId = templateId
    )

    private fun row(
        id: Long = 1L,
        name: String = "每日介绍",
        mailType: String = "INTRODUCTION",
        autoEnabled: Boolean = false,
        cron: String = "0 0 9 * * ?",
        tagsJson: String = "[]",
        funnelLevel: String? = null,
        emailDomain: String? = null,
        discipline: String? = null,
        templateId: Long? = null,
        deletedAt: LocalDateTime? = null,
        updatedAt: LocalDateTime = LocalDateTime.of(2026, 7, 14, 10, 0)
    ) = BatchSendTaskConfig(
        id = id,
        configName = name,
        mailType = mailType,
        autoEnabled = autoEnabled,
        cron = cron,
        dailyCap = 100,
        roundSize = 10,
        perMailIntervalMs = 1000,
        perRoundIntervalMs = 60000,
        selfCheckTtlMinutes = 30,
        funnelLevel = funnelLevel,
        tagsJson = tagsJson,
        emailDomain = emailDomain,
        discipline = discipline,
        templateId = templateId,
        deletedAt = deletedAt,
        createdAt = updatedAt,
        updatedAt = updatedAt
    )

    private fun templateDetail(
        id: Long,
        mailType: String,
        enabled: Boolean = true
    ) = MailComposeTemplateDetail(
        id = id,
        templateCode = "T$id",
        templateName = "Template $id",
        subject = "Subject",
        description = null,
        mailType = mailType,
        subjectVariants = null,
        enabled = enabled,
        blocks = emptyList(),
        createdAt = null,
        updatedAt = null
    )

    @Test
    fun `create without template defaults to INTRODUCTION and publishes reload`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("每日介绍")).thenReturn(null)
        `when`(repository.save(any())).thenAnswer { invocation ->
            val arg = invocation.arguments[0] as BatchSendTaskConfig
            arg.copy(id = 11L)
        }

        val view = service().create(createCmd())

        assertEquals(11L, view.id)
        assertEquals("INTRODUCTION", view.mailType)
        assertNull(view.templateId)
        assertEquals(emptyList<String>(), view.tags)
        assertNull(view.funnelLevel)
        verify(eventPublisher).publishEvent(any(BatchSendCronChangedEvent::class.java))
    }

    @Test
    fun `create with material template derives MATERIAL_REMINDER`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("材料提醒")).thenReturn(null)
        `when`(mailComposeTemplateService.getById(42L)).thenReturn(templateDetail(42L, "MATERIAL_REMINDER"))
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 12L)
        }

        val view = service().create(createCmd(name = "材料提醒", templateId = 42L))

        assertEquals("MATERIAL_REMINDER", view.mailType)
        assertEquals(42L, view.templateId)
    }

    @Test
    fun `create rejects disabled template with 422`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("坏模板")).thenReturn(null)
        `when`(mailComposeTemplateService.getById(7L)).thenReturn(templateDetail(7L, "INTRODUCTION", enabled = false))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service().create(createCmd(name = "坏模板", templateId = 7L))
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status)
        verify(repository, never()).save(any())
    }

    @Test
    fun `create rejects wrong mailType template with 422`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("坏类型")).thenReturn(null)
        `when`(mailComposeTemplateService.getById(8L)).thenReturn(templateDetail(8L, "QA_REPLY"))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service().create(createCmd(name = "坏类型", templateId = 8L))
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status)
    }

    @Test
    fun `create rejects duplicate name with 409`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("每日介绍")).thenReturn(row(id = 3L, name = "每日介绍"))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service().create(createCmd())
        }
        assertEquals(HttpStatus.CONFLICT, ex.status)
        verify(repository, never()).save(any())
    }

    @Test
    fun `create normalizes tags funnel ALL and empty filters`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("范围任务")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 13L)
        }

        val view = service().create(
            createCmd(
                name = "范围任务",
                funnelLevel = "ALL",
                tags = listOf("  beta ", "alpha", "beta", " "),
                emailDomain = "ALL",
                discipline = ""
            )
        )

        assertNull(view.funnelLevel)
        assertEquals(listOf("alpha", "beta"), view.tags)
        assertNull(view.emailDomain)
        assertNull(view.discipline)
        verify(repository).save(captor.capture())
        assertEquals("""["alpha","beta"]""", captor.value.tagsJson)
        assertNull(captor.value.funnelLevel)
    }

    @Test
    fun `create rejects illegal funnel cron and numeric ranges`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull(anyString())).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "a", funnelLevel = "RAW"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "b", cron = "not-a-cron"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "c", dailyCap = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "d", roundSize = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "e", perMailIntervalMs = -1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "f", selfCheckTtlMinutes = 0))
        }
        verify(repository, never()).save(any())
    }

    @Test
    fun `list excludes deleted and supports fuzzy name query sorted by updated_at desc`() {
        val newer = row(id = 2L, name = "材料提醒任务", updatedAt = LocalDateTime.of(2026, 7, 14, 12, 0))
        val older = row(id = 1L, name = "默认介绍邮件任务", updatedAt = LocalDateTime.of(2026, 7, 14, 11, 0))
        `when`(repository.findAllActiveOrderByUpdatedAtDescIdDesc()).thenReturn(listOf(newer, older))
        `when`(repository.findAllActiveByConfigNameContainingOrderByUpdatedAtDescIdDesc("材料"))
            .thenReturn(listOf(newer))

        val all = service().list(null)
        assertEquals(listOf(2L, 1L), all.map { it.id })

        val filtered = service().list(" 材料 ")
        assertEquals(listOf(2L), filtered.map { it.id })
    }

    @Test
    fun `get returns view and 404 after soft delete`() {
        `when`(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(row(id = 1L, tagsJson = """["x"]"""))
        val view = service().get(1L)
        assertEquals(listOf("x"), view.tags)

        `when`(repository.findByIdAndDeletedAtIsNull(9L)).thenReturn(null)
        assertThrows(NoSuchElementException::class.java) { service().get(9L) }
    }

    @Test
    fun `update replaces same record after full validation`() {
        val existing = row(id = 5L, name = "旧名")
        `when`(repository.findByIdAndDeletedAtIsNull(5L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("新名")).thenReturn(null)
        `when`(repository.save(any())).thenAnswer { it.arguments[0] }

        val view = service().update(
            5L,
            updateCmd(name = "新名", funnelLevel = "CANDIDATE", tags = listOf("t1"), dailyCap = 200)
        )

        assertEquals(5L, view.id)
        assertEquals("新名", view.configName)
        assertEquals("CANDIDATE", view.funnelLevel)
        assertEquals(listOf("t1"), view.tags)
        assertEquals(200, view.dailyCap)
        verify(eventPublisher).publishEvent(any(BatchSendCronChangedEvent::class.java))
    }

    @Test
    fun `update rejects invalid input without saving`() {
        `when`(repository.findByIdAndDeletedAtIsNull(5L)).thenReturn(row(id = 5L))
        `when`(repository.findByConfigNameAndDeletedAtIsNull("每日介绍")).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) {
            service().update(5L, updateCmd(cron = "bad"))
        }
        verify(repository, never()).save(any())
    }

    @Test
    fun `setEnabled true revalidates template and setEnabled false keeps cron`() {
        val existing = row(id = 6L, templateId = 42L, mailType = "MATERIAL_REMINDER", autoEnabled = false)
        `when`(repository.findByIdAndDeletedAtIsNull(6L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("每日介绍")).thenReturn(existing)
        `when`(mailComposeTemplateService.getById(42L)).thenReturn(templateDetail(42L, "MATERIAL_REMINDER"))
        `when`(repository.save(any())).thenAnswer { it.arguments[0] }

        val enabled = service().setEnabled(6L, true)
        assertTrue(enabled.autoEnabled)

        val disabled = service().setEnabled(6L, false)
        assertFalse(disabled.autoEnabled)
        assertEquals("0 0 9 * * ?", disabled.cron)
    }

    @Test
    fun `setEnabled true rejects disabled template`() {
        val existing = row(id = 6L, templateId = 42L)
        `when`(repository.findByIdAndDeletedAtIsNull(6L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("每日介绍")).thenReturn(existing)
        `when`(mailComposeTemplateService.getById(42L)).thenReturn(templateDetail(42L, "INTRODUCTION", enabled = false))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service().setEnabled(6L, true)
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status)
        verify(repository, never()).save(any())
    }

    @Test
    fun `softDelete disables then stamps deletedAt and hides from get`() {
        val existing = row(id = 7L, autoEnabled = true)
        `when`(repository.findByIdAndDeletedAtIsNull(7L)).thenReturn(existing)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { it.arguments[0] }

        service().softDelete(7L)

        verify(repository).save(captor.capture())
        assertFalse(captor.value.autoEnabled)
        assertTrue(captor.value.deletedAt != null)
        verify(eventPublisher).publishEvent(any(BatchSendCronChangedEvent::class.java))
    }

    @Test
    fun `softDelete on missing id throws and does not save`() {
        `when`(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(null)
        assertThrows(NoSuchElementException::class.java) { service().softDelete(99L) }
        verify(repository, never()).save(any())
    }
}
