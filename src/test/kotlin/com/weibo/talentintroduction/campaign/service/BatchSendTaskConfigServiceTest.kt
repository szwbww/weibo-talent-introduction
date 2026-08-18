package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigCreateCommand
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigUpdateCommand
import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.template.service.MailComposeTemplateDetail
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

class BatchSendTaskConfigServiceTest {

    private val repository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
    private val mailComposeTemplateService = Mockito.mock(MailComposeTemplateService::class.java)
    private val eventPublisher = Mockito.mock(ApplicationEventPublisher::class.java)
    private val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
    private val objectMapper = ObjectMapper()

    private fun service() = BatchSendTaskConfigService(
        repository = repository,
        mailComposeTemplateService = mailComposeTemplateService,
        objectMapper = objectMapper,
        eventPublisher = eventPublisher,
        taskExecutionService = taskExecutionService
    )

    // Repo-standard Mockito helpers for Kotlin-declared (non-null parameter) mock methods:
    // the matcher placeholders return null and must be coalesced with a default.
    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun <T> captureValue(captor: ArgumentCaptor<T>, defaultValue: T): T =
        captor.capture() ?: defaultValue

    private fun createCmd(
        name: String = "每日介绍",
        autoEnabled: Boolean = false,
        cron: String = "0 0 9 * * ?",
        roundSize: Int = 10,
        roundsPerRun: Int = 1,
        perMailIntervalMs: Long = 1000,
        perRoundIntervalMs: Long = 60000,
        selfCheckTtlMinutes: Int = 30,
        funnelLevel: String? = null,
        tags: List<String> = emptyList(),
        regions: List<String> = emptyList(),
        emailDomains: List<String> = emptyList(),
        discipline: String? = null,
        operatorStatuses: List<String> = emptyList(),
        templateId: Long? = null,
        gateFilterEnabled: Boolean = false,
        reachabilityFilter: String? = null
    ) = BatchSendTaskConfigCreateCommand(
        configName = name,
        autoEnabled = autoEnabled,
        cron = cron,
        roundSize = roundSize,
        roundsPerRun = roundsPerRun,
        perMailIntervalMs = perMailIntervalMs,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = selfCheckTtlMinutes,
        funnelLevel = funnelLevel,
        tags = tags,
        regions = regions,
        emailDomains = emailDomains,
        discipline = discipline,
        operatorStatuses = operatorStatuses,
        templateId = templateId,
        gateFilterEnabled = gateFilterEnabled,
        reachabilityFilter = reachabilityFilter
    )

    private fun updateCmd(
        name: String = "每日介绍",
        autoEnabled: Boolean = false,
        cron: String = "0 0 9 * * ?",
        roundSize: Int = 10,
        roundsPerRun: Int = 1,
        perMailIntervalMs: Long = 1000,
        perRoundIntervalMs: Long = 60000,
        selfCheckTtlMinutes: Int = 30,
        funnelLevel: String? = null,
        tags: List<String> = emptyList(),
        regions: List<String> = emptyList(),
        emailDomains: List<String> = emptyList(),
        discipline: String? = null,
        operatorStatuses: List<String> = emptyList(),
        templateId: Long? = null,
        gateFilterEnabled: Boolean = false,
        reachabilityFilter: String? = null
    ) = BatchSendTaskConfigUpdateCommand(
        configName = name,
        autoEnabled = autoEnabled,
        cron = cron,
        roundSize = roundSize,
        roundsPerRun = roundsPerRun,
        perMailIntervalMs = perMailIntervalMs,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = selfCheckTtlMinutes,
        funnelLevel = funnelLevel,
        tags = tags,
        regions = regions,
        emailDomains = emailDomains,
        discipline = discipline,
        operatorStatuses = operatorStatuses,
        templateId = templateId,
        gateFilterEnabled = gateFilterEnabled,
        reachabilityFilter = reachabilityFilter
    )

    private fun row(
        id: Long = 1L,
        name: String = "每日介绍",
        mailType: String = "INTRODUCTION",
        autoEnabled: Boolean = false,
        cron: String = "0 0 9 * * ?",
        roundsPerRun: Int = 1,
        tagsJson: String = "[]",
        funnelLevel: String? = null,
        emailDomainsJson: String = "[]",
        discipline: String? = null,
        operatorStatusesJson: String = "[]",
        templateId: Long? = null,
        gateFilterEnabled: Boolean = false,
        reachabilityFilter: String? = null,
        deletedAt: LocalDateTime? = null,
        updatedAt: LocalDateTime = LocalDateTime.of(2026, 7, 14, 10, 0)
    ) = BatchSendTaskConfig(
        id = id,
        configName = name,
        mailType = mailType,
        autoEnabled = autoEnabled,
        cron = cron,
        roundSize = 10,
        roundsPerRun = roundsPerRun,
        perMailIntervalMs = 1000,
        perRoundIntervalMs = 60000,
        selfCheckTtlMinutes = 30,
        funnelLevel = funnelLevel,
        tagsJson = tagsJson,
        emailDomainsJson = emailDomainsJson,
        discipline = discipline,
        operatorStatusesJson = operatorStatusesJson,
        templateId = templateId,
        gateFilterEnabled = gateFilterEnabled,
        reachabilityFilter = reachabilityFilter,
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
    fun `create maps active name unique key race to 409`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("每日介绍")).thenReturn(null)
        `when`(repository.save(any())).thenThrow(
            DuplicateKeyException("Duplicate entry '每日介绍' for key 'uk_batch_send_task_config_active_name'")
        )

        val ex = assertThrows(ResponseStatusException::class.java) {
            service().create(createCmd())
        }
        assertEquals(HttpStatus.CONFLICT, ex.status)
        verify(eventPublisher, never()).publishEvent(any())
    }

    @Test
    fun `create reuses name after soft delete`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("每日介绍")).thenReturn(null)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 20L)
        }

        val view = service().create(createCmd(name = "每日介绍"))

        assertEquals(20L, view.id)
        assertEquals("每日介绍", view.configName)
        verify(eventPublisher).publishEvent(any(BatchSendCronChangedEvent::class.java))
    }

    @Test
    fun `update maps active name unique key race to 409`() {
        val existing = row(id = 5L, name = "旧名")
        `when`(repository.findByIdAndDeletedAtIsNull(5L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("冲突名")).thenReturn(null)
        `when`(repository.save(any())).thenThrow(
            DuplicateKeyException("Duplicate entry '冲突名' for key 'uk_batch_send_task_config_active_name'")
        )

        val ex = assertThrows(ResponseStatusException::class.java) {
            service().update(5L, updateCmd(name = "冲突名"))
        }
        assertEquals(HttpStatus.CONFLICT, ex.status)
        verify(eventPublisher, never()).publishEvent(any())
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
                emailDomains = emptyList(),
                discipline = ""
            )
        )

        assertNull(view.funnelLevel)
        assertEquals(listOf("alpha", "beta"), view.tags)
        assertTrue(view.emailDomains.isEmpty())
        assertNull(view.discipline)
        verify(repository).save(captor.capture())
        assertEquals("""["alpha","beta"]""", captor.value.tagsJson)
        assertEquals("[]", captor.value.emailDomainsJson)
        assertNull(captor.value.funnelLevel)
    }

    @Test
    fun `create persists UNCLASSIFIED discipline and view returns it (I-5)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("未分类任务")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 14L)
        }

        val view = service().create(createCmd(name = "未分类任务", discipline = "UNCLASSIFIED"))

        assertEquals("UNCLASSIFIED", view.discipline)
        verify(repository).save(captor.capture())
        assertEquals("UNCLASSIFIED", captor.value.discipline)
    }

    @Test
    fun `create rejects unknown discipline value (I-5)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("非法学科")).thenReturn(null)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "非法学科", discipline = "OTHER_STUFF"))
        }
        assertTrue(ex.message!!.contains("discipline must be one of"))
        verify(repository, never()).save(any())
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
            updateCmd(name = "新名", funnelLevel = "CANDIDATE", tags = listOf("t1"))
        )

        assertEquals(5L, view.id)
        assertEquals("新名", view.configName)
        assertEquals("CANDIDATE", view.funnelLevel)
        assertEquals(listOf("t1"), view.tags)
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

    @Test
    fun `getLegacyConfig reads active legacy_code entity as BatchSendConfig`() {
        val entity = BatchSendTaskConfig(
            id = 1L, configName = "默认介绍邮件任务", mailType = "INTRODUCTION",
            autoEnabled = true, cron = "0 0 7 * * ?", roundSize = 10,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            emailDomainsJson = """["edu.cn"]""", discipline = "STEM", templateId = null, legacyCode = "INTRODUCTION",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        `when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(entity)

        val config = service().getLegacyConfig(BatchSendType.INTRODUCTION)

        assertEquals(BatchSendType.INTRODUCTION, config.sendType)
        assertTrue(config.autoEnabled)
        assertEquals("0 0 7 * * ?", config.cron)
        assertEquals(0, config.dailyCap)
        assertEquals("edu.cn", config.emailDomain)
        assertEquals("STEM", config.discipline)
    }

    @Test
    fun `getLegacyConfig returns 404 when legacy entity missing or soft-deleted`() {
        `when`(repository.findByLegacyCode("MATERIAL_REMINDER")).thenReturn(null)
        val missing = assertThrows(ResponseStatusException::class.java) {
            service().getLegacyConfig(BatchSendType.MATERIAL_REMINDER)
        }
        assertEquals(HttpStatus.NOT_FOUND, missing.status)

        `when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(
            row(id = 1L, deletedAt = LocalDateTime.now(), mailType = "INTRODUCTION")
                .copy(legacyCode = "INTRODUCTION")
        )
        val deleted = assertThrows(ResponseStatusException::class.java) {
            service().getLegacyConfig(BatchSendType.INTRODUCTION)
        }
        assertEquals(HttpStatus.NOT_FOUND, deleted.status)
    }

    @Test
    fun `updateLegacyConfig writes entity row preserves name funnel tags and publishes reload`() {
        val existing = BatchSendTaskConfig(
            id = 2L, configName = "默认介绍邮件任务", mailType = "INTRODUCTION",
            autoEnabled = false, cron = "0 0 0 * * ?", roundSize = 50,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE", tagsJson = """["保留标签"]""", emailDomainsJson = "[]",
            discipline = null, templateId = null, legacyCode = "INTRODUCTION",
            reachabilityFilter = "HIGH_ONLY",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        `when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(existing)
        `when`(repository.findByIdAndDeletedAtIsNull(2L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("默认介绍邮件任务")).thenReturn(existing)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            val saved = invocation.arguments[0] as BatchSendTaskConfig
            saved.copy(id = 2L, legacyCode = "INTRODUCTION")
        }

        val updated = service().updateLegacyConfig(
            BatchSendType.INTRODUCTION,
            BatchSendConfigUpdateRequest(
                autoEnabled = true,
                cron = "0 30 8 * * ?",
                dailyCap = 200,
                roundSize = 20,
                perMailIntervalMs = 2000,
                perRoundIntervalMs = 120000,
                selfCheckTtlMinutes = 15,
                emailDomain = "ox.ac.uk",
                discipline = "HUMANITIES",
                templateId = null
            )
        )

        verify(repository).save(captor.capture())
        assertEquals("默认介绍邮件任务", captor.value.configName)
        assertEquals("CANDIDATE", captor.value.funnelLevel)
        assertEquals("""["保留标签"]""", captor.value.tagsJson)
        assertEquals("0 30 8 * * ?", captor.value.cron)
        // M-2/I2a-6: legacy request's degraded single emailDomain must never rebuild the entity json.
        assertEquals("[]", captor.value.emailDomainsJson)
        assertEquals("HUMANITIES", captor.value.discipline)
        // I-6-1: 旧 typed API 不传可达性过滤，存量值必须保留（漏写会命中默认值静默重置）。
        assertEquals("HIGH_ONLY", captor.value.reachabilityFilter)
        assertTrue(captor.value.autoEnabled)
        assertEquals("0 30 8 * * ?", updated.cron)
        verify(eventPublisher).publishEvent(any(BatchSendCronChangedEvent::class.java))
    }

    @Test
    fun `create rejects roundsPerRun below 1`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull(anyString())).thenReturn(null)

        val zero = assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "r0", roundsPerRun = 0))
        }
        assertTrue(zero.message!!.contains("roundsPerRun must be >= 1"))

        val negative = assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "r-", roundsPerRun = -2))
        }
        assertTrue(negative.message!!.contains("roundsPerRun must be >= 1"))
        verify(repository, never()).save(any())
    }

    @Test
    fun `create persists roundsPerRun and getById returns it in the view`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("每日介绍")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 11L)
        }

        val view = service().create(createCmd(roundsPerRun = 3))

        assertEquals(3, view.roundsPerRun)
        verify(repository).save(captor.capture())
        assertEquals(3, captor.value.roundsPerRun)

        // get() row→View mapping carries the field too
        `when`(repository.findByIdAndDeletedAtIsNull(11L)).thenReturn(captor.value.copy(id = 11L))
        assertEquals(3, service().get(11L).roundsPerRun)
    }

    @Test
    fun `updateLegacyConfig preserves existing roundsPerRun when request omits it`() {
        val existing = BatchSendTaskConfig(
            id = 2L, configName = "默认介绍邮件任务", mailType = "INTRODUCTION",
            autoEnabled = false, cron = "0 0 0 * * ?", roundSize = 50,
            roundsPerRun = 7,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE", tagsJson = """["保留标签"]""", emailDomainsJson = "[]",
            discipline = null, templateId = null, legacyCode = "INTRODUCTION",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        `when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(existing)
        `when`(repository.findByIdAndDeletedAtIsNull(2L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("默认介绍邮件任务")).thenReturn(existing)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 2L, legacyCode = "INTRODUCTION")
        }

        service().updateLegacyConfig(
            BatchSendType.INTRODUCTION,
            BatchSendConfigUpdateRequest(
                autoEnabled = true,
                cron = "0 30 8 * * ?",
                dailyCap = 200,
                roundSize = 20,
                perMailIntervalMs = 2000,
                perRoundIntervalMs = 120000,
                selfCheckTtlMinutes = 15,
                emailDomain = "ox.ac.uk",
                discipline = "HUMANITIES",
                templateId = null
            )
        )

        verify(repository).save(captor.capture())
        // X-4: roundsPerRun is not part of the legacy typed request; the entity value must survive.
        assertEquals(7, captor.value.roundsPerRun)
    }

    @Test
    fun `update changing only roundsPerRun publishes reload event with unchanged cron`() {
        val existing = row(id = 5L, name = "每日介绍")
        `when`(repository.findByIdAndDeletedAtIsNull(5L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("每日介绍")).thenReturn(existing)
        val eventCaptor = ArgumentCaptor.forClass(BatchSendCronChangedEvent::class.java)
        `when`(repository.save(any())).thenAnswer { it.arguments[0] }

        val view = service().update(5L, updateCmd(roundsPerRun = 9))

        assertEquals(9, view.roundsPerRun)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        // X-2: cron unchanged ⇒ BatchSendScheduler.reload() sees scheduledCrons[id] == cron and skips re-registration.
        assertEquals("0 0 9 * * ?", eventCaptor.value.oldCron)
        assertEquals("0 0 9 * * ?", eventCaptor.value.newCron)
    }

    @Test
    fun `create persists legal multi-select regions and get returns them in allRegions order`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("地区任务")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 30L)
        }

        val view = service().create(createCmd(name = "地区任务", regions = listOf("China", "Europe")))

        assertEquals(listOf("China", "Europe"), view.regions)
        verify(repository).save(captor.capture())
        assertEquals("""["China","Europe"]""", captor.value.regionsJson)

        // row → View mapping carries the field too (I-3: snapshot path reads regionsJson)
        `when`(repository.findByIdAndDeletedAtIsNull(30L)).thenReturn(captor.value.copy(id = 30L))
        assertEquals(listOf("China", "Europe"), service().get(30L).regions)
    }

    @Test
    fun `create rejects non-constant region value with region must be one of message`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("非法地区")).thenReturn(null)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "非法地区", regions = listOf("中国")))
        }
        assertTrue(ex.message!!.contains("region must be one of"))
        verify(repository, never()).save(any())
    }

    @Test
    fun `create normalizes duplicate and whitespace-padded regions`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("地区去重")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 31L)
        }

        val view = service().create(
            createCmd(name = "地区去重", regions = listOf("China", "China", " Europe "))
        )

        assertEquals(listOf("China", "Europe"), view.regions)
        verify(repository).save(captor.capture())
        assertEquals("""["China","Europe"]""", captor.value.regionsJson)
    }

    @Test
    fun `create with empty regions persists empty json and view returns empty list`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("无地区")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 32L)
        }

        val view = service().create(createCmd(name = "无地区"))

        assertEquals(emptyList<String>(), view.regions)
        verify(repository).save(captor.capture())
        assertEquals("[]", captor.value.regionsJson)
    }

    @Test
    fun `updateLegacyConfig preserves existing regionsJson entity value`() {
        val existing = BatchSendTaskConfig(
            id = 2L, configName = "默认介绍邮件任务", mailType = "INTRODUCTION",
            autoEnabled = false, cron = "0 0 0 * * ?", roundSize = 50,
            roundsPerRun = 7,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE", tagsJson = """["保留标签"]""", regionsJson = """["Europe"]""",
            emailDomainsJson = "[]", discipline = null, templateId = null, legacyCode = "INTRODUCTION",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        `when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(existing)
        `when`(repository.findByIdAndDeletedAtIsNull(2L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("默认介绍邮件任务")).thenReturn(existing)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 2L, legacyCode = "INTRODUCTION")
        }

        service().updateLegacyConfig(
            BatchSendType.INTRODUCTION,
            BatchSendConfigUpdateRequest(
                autoEnabled = true,
                cron = "0 30 8 * * ?",
                dailyCap = 200,
                roundSize = 20,
                perMailIntervalMs = 2000,
                perRoundIntervalMs = 120000,
                selfCheckTtlMinutes = 15,
                emailDomain = "ox.ac.uk",
                discipline = "HUMANITIES",
                templateId = null
            )
        )

        verify(repository).save(captor.capture())
        // X-2/K-batch-config-legacy-adapter-field-preservation: legacy request has no regions dimension; entity value must survive.
        assertEquals("""["Europe"]""", captor.value.regionsJson)
    }

    // ── P2a: emailDomains multi-value ─────────────────────────────────────────

    @Test
    fun `create persists emailDomains multi-value and get returns them in order (I2a-1)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("多域任务")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 40L)
        }

        val view = service().create(createCmd(name = "多域任务", emailDomains = listOf("a.com", "b.com")))

        assertEquals(listOf("a.com", "b.com"), view.emailDomains)
        verify(repository).save(captor.capture())
        assertEquals("""["a.com","b.com"]""", captor.value.emailDomainsJson)

        // row → View mapping carries the field too (I2a-1: snapshot path reads emailDomainsJson)
        `when`(repository.findByIdAndDeletedAtIsNull(40L)).thenReturn(captor.value.copy(id = 40L))
        assertEquals(listOf("a.com", "b.com"), service().get(40L).emailDomains)
    }

    @Test
    fun `create normalizes whitespace and duplicate emailDomains (I2a-5)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("域名去重")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 41L)
        }

        val view = service().create(
            createCmd(name = "域名去重", emailDomains = listOf("  a.com  ", "", "a.com"))
        )

        assertEquals(listOf("a.com"), view.emailDomains)
        verify(repository).save(captor.capture())
        assertEquals("""["a.com"]""", captor.value.emailDomainsJson)
    }

    @Test
    fun `create rejects emailDomain containing comma (I2a-5)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("逗号域名")).thenReturn(null)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "逗号域名", emailDomains = listOf("a,b.com")))
        }
        assertTrue(ex.message!!.contains("emailDomain must not contain a comma"))
        verify(repository, never()).save(any())
    }

    @Test
    fun `create with empty emailDomains persists empty json and view returns empty (I2a-2)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("无域名")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 42L)
        }

        val view = service().create(createCmd(name = "无域名"))

        assertEquals(emptyList<String>(), view.emailDomains)
        verify(repository).save(captor.capture())
        assertEquals("[]", captor.value.emailDomainsJson)
    }

    @Test
    fun `updateLegacyConfig preserves existing emailDomainsJson entity value (I2a-6)`() {
        val existing = BatchSendTaskConfig(
            id = 2L, configName = "默认介绍邮件任务", mailType = "INTRODUCTION",
            autoEnabled = false, cron = "0 0 0 * * ?", roundSize = 50,
            roundsPerRun = 7,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE", tagsJson = """["保留标签"]""",
            emailDomainsJson = """["a.com","b.com"]""",
            discipline = null, templateId = null, legacyCode = "INTRODUCTION",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        `when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(existing)
        `when`(repository.findByIdAndDeletedAtIsNull(2L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("默认介绍邮件任务")).thenReturn(existing)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 2L, legacyCode = "INTRODUCTION")
        }

        val updated = service().updateLegacyConfig(
            BatchSendType.INTRODUCTION,
            BatchSendConfigUpdateRequest(
                autoEnabled = true,
                cron = "0 30 8 * * ?",
                dailyCap = 200,
                roundSize = 20,
                perMailIntervalMs = 2000,
                perRoundIntervalMs = 120000,
                selfCheckTtlMinutes = 15,
                emailDomain = "",
                discipline = "HUMANITIES",
                templateId = null
            )
        )

        verify(repository).save(captor.capture())
        // M-2/I2a-6: legacy request carries only the degraded single emailDomain; the entity's
        // multi-value json must survive — never rebuilt from request.emailDomain.
        assertEquals("""["a.com","b.com"]""", captor.value.emailDomainsJson)
        assertEquals("a.com", updated.emailDomain)
    }

    @Test
    fun `getLegacyConfig degrades multi emailDomains to first (I2a-6)`() {
        val entity = BatchSendTaskConfig(
            id = 1L, configName = "默认介绍邮件任务", mailType = "INTRODUCTION",
            autoEnabled = true, cron = "0 0 7 * * ?", roundSize = 10,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            emailDomainsJson = """["a.com","b.com"]""", discipline = null, templateId = null,
            legacyCode = "INTRODUCTION",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        `when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(entity)

        val config = service().getLegacyConfig(BatchSendType.INTRODUCTION)

        assertEquals("a.com", config.emailDomain)
    }

    // ── P3a: operatorStatuses multi-value（I3a-3 / I3a-6 / M-2）──────────────────

    @Test
    fun `create persists operatorStatuses multi-value and get returns them in order (I3a-6)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("状态任务")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 50L)
        }

        val view = service().create(
            createCmd(name = "状态任务", operatorStatuses = listOf("NOT_CONTACTED", "CONTACTED"))
        )

        assertEquals(listOf("NOT_CONTACTED", "CONTACTED"), view.operatorStatuses)
        verify(repository).save(captor.capture())
        assertEquals("""["NOT_CONTACTED","CONTACTED"]""", captor.value.operatorStatusesJson)

        // row → View 映射同样携带该字段（读路径）。
        `when`(repository.findByIdAndDeletedAtIsNull(50L)).thenReturn(captor.value.copy(id = 50L))
        assertEquals(listOf("NOT_CONTACTED", "CONTACTED"), service().get(50L).operatorStatuses)
    }

    @Test
    fun `create normalizes whitespace and duplicate operatorStatuses (I3a-6)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("状态去重")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 51L)
        }

        val view = service().create(
            createCmd(name = "状态去重", operatorStatuses = listOf("  CONTACTED  ", "", "CONTACTED"))
        )

        assertEquals(listOf("CONTACTED"), view.operatorStatuses)
        verify(repository).save(captor.capture())
        assertEquals("""["CONTACTED"]""", captor.value.operatorStatusesJson)
    }

    @Test
    fun `create rejects operatorStatus containing comma (I3a-6)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("逗号状态")).thenReturn(null)

        // 逗号分隔符值既不在枚举白名单（先触发 whitelist require），也满足逗号防御性 require；
        // 无论哪条命中，契约都是：含逗号的状态被拒、不落库。
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "逗号状态", operatorStatuses = listOf("CONTACTED,REPLIED")))
        }
        assertTrue(
            ex.message!!.contains("operatorStatus must be one of") || ex.message!!.contains("comma"),
            "rejection must name the operatorStatus constraint, got: ${ex.message}"
        )
        verify(repository, never()).save(any())
    }

    @Test
    fun `create rejects unknown operatorStatus value with allowed list (I3a-6)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("非法状态")).thenReturn(null)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "非法状态", operatorStatuses = listOf("BOGUS")))
        }
        assertTrue(ex.message!!.contains("operatorStatus must be one of"))
        assertTrue(ex.message!!.contains("NOT_CONTACTED"), "message must show the enum-derived whitelist")
        verify(repository, never()).save(any())
    }

    @Test
    fun `create with empty operatorStatuses persists empty json and view returns empty (I3a-3)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("无状态")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 52L)
        }

        val view = service().create(createCmd(name = "无状态", operatorStatuses = emptyList()))

        assertEquals(emptyList<String>(), view.operatorStatuses)
        verify(repository).save(captor.capture())
        assertEquals("[]", captor.value.operatorStatusesJson)

        // 存量行 operator_statuses_json = "[]"（迁移回填形态）→ 视图同样为空集合。
        `when`(repository.findByIdAndDeletedAtIsNull(53L)).thenReturn(
            row(id = 53L, name = "存量空状态", operatorStatusesJson = "[]")
        )
        assertEquals(emptyList<String>(), service().get(53L).operatorStatuses)
    }

    @Test
    fun `updateLegacyConfig preserves existing operatorStatusesJson entity value (M-2)`() {
        val existing = BatchSendTaskConfig(
            id = 2L, configName = "默认介绍邮件任务", mailType = "INTRODUCTION",
            autoEnabled = false, cron = "0 0 0 * * ?", roundSize = 50,
            roundsPerRun = 7,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE", tagsJson = """["保留标签"]""",
            emailDomainsJson = "[]", operatorStatusesJson = """["CONTACTED"]""",
            discipline = null, templateId = null, legacyCode = "INTRODUCTION",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        `when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(existing)
        `when`(repository.findByIdAndDeletedAtIsNull(2L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("默认介绍邮件任务")).thenReturn(existing)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 2L, legacyCode = "INTRODUCTION")
        }

        service().updateLegacyConfig(
            BatchSendType.INTRODUCTION,
            BatchSendConfigUpdateRequest(
                autoEnabled = true,
                cron = "0 30 8 * * ?",
                dailyCap = 200,
                roundSize = 20,
                perMailIntervalMs = 2000,
                perRoundIntervalMs = 120000,
                selfCheckTtlMinutes = 15,
                emailDomain = "",
                discipline = "HUMANITIES",
                templateId = null
            )
        )

        verify(repository).save(captor.capture())
        // M-2: legacy request never carries operatorStatuses; the entity's multi-value json
        // must survive — 漏写会命中 Kotlin 默认值静默重置。
        assertEquals("""["CONTACTED"]""", captor.value.operatorStatusesJson)
    }

    // ── P4a: gateFilterEnabled 门禁开关（I4a-1 / I4a-6 / M-2）────────────────────

    @Test
    fun `create persists gateFilterEnabled true and get returns it (I4a-1)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("门禁任务")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 60L)
        }

        val view = service().create(createCmd(name = "门禁任务", gateFilterEnabled = true))

        assertTrue(view.gateFilterEnabled)
        verify(repository).save(captor.capture())
        assertTrue(captor.value.gateFilterEnabled)

        // row → View 映射同样携带该字段（读路径）。
        `when`(repository.findByIdAndDeletedAtIsNull(60L)).thenReturn(captor.value.copy(id = 60L))
        assertTrue(service().get(60L).gateFilterEnabled)
    }

    @Test
    fun `create without gateFilterEnabled defaults to false (I4a-1)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("无门禁")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 61L)
        }

        val view = service().create(createCmd(name = "无门禁"))

        assertFalse(view.gateFilterEnabled)
        verify(repository).save(captor.capture())
        assertFalse(captor.value.gateFilterEnabled)
    }

    @Test
    fun `updateLegacyConfig preserves existing gateFilterEnabled entity value (M-2 I4a-6)`() {
        val existing = BatchSendTaskConfig(
            id = 2L, configName = "默认介绍邮件任务", mailType = "INTRODUCTION",
            autoEnabled = false, cron = "0 0 0 * * ?", roundSize = 50,
            roundsPerRun = 7,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE", tagsJson = """["保留标签"]""",
            emailDomainsJson = "[]", operatorStatusesJson = "[]",
            discipline = null, templateId = null, legacyCode = "INTRODUCTION",
            gateFilterEnabled = true,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        `when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(existing)
        `when`(repository.findByIdAndDeletedAtIsNull(2L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("默认介绍邮件任务")).thenReturn(existing)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 2L, legacyCode = "INTRODUCTION")
        }

        service().updateLegacyConfig(
            BatchSendType.INTRODUCTION,
            BatchSendConfigUpdateRequest(
                autoEnabled = true,
                cron = "0 30 8 * * ?",
                dailyCap = 200,
                roundSize = 20,
                perMailIntervalMs = 2000,
                perRoundIntervalMs = 120000,
                selfCheckTtlMinutes = 15,
                emailDomain = "",
                discipline = "HUMANITIES",
                templateId = null
            )
        )

        verify(repository).save(captor.capture())
        // M-2 (I4a-6): legacy request never carries gateFilterEnabled; the entity value
        // must survive — 漏写会命中 Kotlin 默认值静默重置为 false。
        assertTrue(captor.value.gateFilterEnabled)
    }

    // ── 04a: nextFireTime / lastExecutedAt / cron preview ─────────────────────────

    @Test
    fun `nextFireTime is populated for autoEnabled config with valid cron`() {
        `when`(repository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(row(id = 1L, autoEnabled = true, cron = "0 0 9 * * ?"))

        val view = service().get(1L)

        assertNotNull(view.nextFireTime)
        assertTrue(view.nextFireTime!!.isAfter(LocalDateTime.now().minusSeconds(1)))
    }

    @Test
    fun `nextFireTime is calculated when autoEnabled is false`() {
        `when`(repository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(row(id = 1L, autoEnabled = false, cron = "0 0 9 * * ?"))

        val view = service().get(1L)

        assertNotNull(view.nextFireTime)
        assertTrue(view.nextFireTime!!.isAfter(LocalDateTime.now().minusSeconds(1)))
    }

    @Test
    fun `invalid cron degrades to null nextFireTime without throwing`() {
        `when`(repository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(row(id = 1L, autoEnabled = true, cron = "这不是cron"))

        val view = service().get(1L)

        assertNull(view.nextFireTime)
    }

    @Test
    fun `list queries lastExecutedAt exactly once with all ids`() {
        val rows = listOf(
            row(id = 1L, updatedAt = LocalDateTime.of(2026, 7, 14, 12, 0)),
            row(id = 2L, name = "b", updatedAt = LocalDateTime.of(2026, 7, 14, 11, 0)),
            row(id = 3L, name = "c", updatedAt = LocalDateTime.of(2026, 7, 14, 10, 0))
        )
        `when`(repository.findAllActiveOrderByUpdatedAtDescIdDesc()).thenReturn(rows)
        `when`(taskExecutionService.lastExecutedAtByBatchConfigIds(anyValue(emptyList<Long>()))).thenReturn(emptyMap())
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(Collection::class.java) as ArgumentCaptor<Collection<Long>>

        val views = service().list(null)

        assertEquals(3, views.size)
        verify(taskExecutionService, times(1))
            .lastExecutedAtByBatchConfigIds(captureValue(captor, emptyList<Long>()))
        assertEquals(listOf(1L, 2L, 3L), captor.value.toList())
    }

    @Test
    fun `list with zero configs calls aggregation with empty ids and does not throw`() {
        `when`(repository.findAllActiveOrderByUpdatedAtDescIdDesc()).thenReturn(emptyList())
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(Collection::class.java) as ArgumentCaptor<Collection<Long>>

        val views = service().list(null)

        assertEquals(0, views.size)
        verify(taskExecutionService, times(1))
            .lastExecutedAtByBatchConfigIds(captureValue(captor, emptyList<Long>()))
        assertTrue(captor.value.isEmpty())
    }

    @Test
    fun `previewCron returns 5 strictly increasing times for valid cron`() {
        val result = service().previewCron("0 0 9 * * ?")

        assertTrue(result.valid)
        assertNull(result.message)
        assertEquals(5, result.nextFireTimes.size)
        for (i in 1 until result.nextFireTimes.size) {
            assertTrue(result.nextFireTimes[i].isAfter(result.nextFireTimes[i - 1]))
        }
    }

    @Test
    fun `previewCron returns valid false with message for invalid cron without throwing`() {
        val result = service().previewCron("bogus")

        assertFalse(result.valid)
        assertTrue(!result.message.isNullOrEmpty())
        assertEquals(emptyList<LocalDateTime>(), result.nextFireTimes)
    }
}
