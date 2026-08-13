package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.campaign.service.BatchSendConfigUpdateRequest
import com.weibo.talentintroduction.campaign.service.BatchSendControlService
import com.weibo.talentintroduction.campaign.service.BatchSendTaskConfigService
import com.weibo.talentintroduction.campaign.service.BatchSendType
import com.weibo.talentintroduction.campaign.service.ManualInitialOutreachService
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

/**
 * P1-1: legacy `/config` and `/types/{sendType}/config` must read/write entity rows,
 * never BatchSendSettingService KV (controller no longer depends on KV service).
 */
class BatchSendConfigControllerTest {

    private val repository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
    private val templateService = Mockito.mock(com.weibo.talentintroduction.template.service.MailComposeTemplateService::class.java)
    private val eventPublisher = Mockito.mock(ApplicationEventPublisher::class.java)
    private val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
    private val objectMapper = ObjectMapper()
        .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
    private val taskConfigService = BatchSendTaskConfigService(
        repository, templateService, objectMapper, eventPublisher, taskExecutionService
    )
    private val templateRepository = Mockito.mock(MailComposeTemplateRepository::class.java)

    private fun controller() = BatchSendConfigController(
        batchSendTaskConfigService = taskConfigService,
        templateRepository = templateRepository,
        batchSendControlService = Mockito.mock(BatchSendControlService::class.java),
        manualInitialOutreachService = Mockito.mock(ManualInitialOutreachService::class.java),
        taskExecutionService = taskExecutionService,
        progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java),
        objectMapper = objectMapper
    )

    private fun introEntity(
        cron: String = "0 0 0 * * ?",
        autoEnabled: Boolean = false
    ) = BatchSendTaskConfig(
        id = 10L,
        configName = "默认介绍邮件任务",
        mailType = "INTRODUCTION",
        autoEnabled = autoEnabled,
        cron = cron,
        roundSize = 50,
        perMailIntervalMs = 1000,
        perRoundIntervalMs = 60000,
        selfCheckTtlMinutes = 30,
        tagsJson = "[]",
        legacyCode = "INTRODUCTION",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private fun reminderEntity(
        cron: String = "0 0 8 * * ?",
        templateId: Long = 99L
    ) = BatchSendTaskConfig(
        id = 20L,
        configName = "材料提醒任务",
        mailType = "MATERIAL_REMINDER",
        autoEnabled = false,
        cron = cron,
        roundSize = 30,
        perMailIntervalMs = 3000,
        perRoundIntervalMs = 120000,
        selfCheckTtlMinutes = 30,
        tagsJson = """["承诺回复材料"]""",
        templateId = templateId,
        legacyCode = "MATERIAL_REMINDER",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    @Test
    fun `GET config reads INTRODUCTION legacy entity not KV`() {
        Mockito.`when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(introEntity(cron = "0 15 6 * * ?"))

        val response = controller().getConfig()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("0 15 6 * * ?", response.body!!.cron)
        assertEquals(0, response.body!!.dailyCap)
        Mockito.verify(repository).findByLegacyCode("INTRODUCTION")
    }

    @Test
    fun `PUT config updates INTRODUCTION entity row for subsequent config-driven start`() {
        val existing = introEntity()
        Mockito.`when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(existing)
        Mockito.`when`(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(existing)
        Mockito.`when`(repository.findByConfigNameAndDeletedAtIsNull("默认介绍邮件任务")).thenReturn(existing)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        Mockito.`when`(repository.save(Mockito.any())).thenAnswer { inv ->
            (inv.arguments[0] as BatchSendTaskConfig).copy(id = 10L, legacyCode = "INTRODUCTION")
        }

        val response = controller().updateConfig(
            BatchSendConfigUpdateRequest(
                autoEnabled = true,
                cron = "0 0 9 * * ?",
                dailyCap = 333,
                roundSize = 40,
                perMailIntervalMs = 1500,
                perRoundIntervalMs = 90000,
                selfCheckTtlMinutes = 20,
                emailDomain = "",
                discipline = "",
                templateId = null
            )
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("0 0 9 * * ?", response.body!!.cron)
        assertEquals(0, response.body!!.dailyCap)
        assertTrue(response.body!!.autoEnabled)
        Mockito.verify(repository).save(captor.capture())
        assertEquals(10L, captor.value.id)
        assertEquals("0 0 9 * * ?", captor.value.cron)
        Mockito.verify(eventPublisher).publishEvent(Mockito.any(com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent::class.java))
    }

    @Test
    fun `GET and PUT types INTRODUCTION config use entity adapter`() {
        val existing = introEntity(cron = "0 0 1 * * ?")
        Mockito.`when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(existing)
        Mockito.`when`(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(existing)
        Mockito.`when`(repository.findByConfigNameAndDeletedAtIsNull("默认介绍邮件任务")).thenReturn(existing)
        Mockito.`when`(repository.save(Mockito.any())).thenAnswer { inv ->
            (inv.arguments[0] as BatchSendTaskConfig).copy(id = 10L, legacyCode = "INTRODUCTION")
        }

        val got = controller().getConfigByType(BatchSendType.INTRODUCTION)
        assertEquals(0, got.body!!.dailyCap)

        val put = controller().updateConfigByType(
            BatchSendType.INTRODUCTION,
            BatchSendConfigUpdateRequest(
                autoEnabled = false,
                cron = "0 5 5 * * ?",
                dailyCap = 22,
                roundSize = 10,
                perMailIntervalMs = 1000,
                perRoundIntervalMs = 60000,
                selfCheckTtlMinutes = 30
            )
        )
        assertEquals(0, put.body!!.dailyCap)
        assertEquals("0 5 5 * * ?", put.body!!.cron)
        Mockito.verify(repository, Mockito.atLeastOnce()).findByLegacyCode("INTRODUCTION")
        Mockito.verify(repository).save(Mockito.any())
    }

    @Test
    fun `GET and PUT types MATERIAL_REMINDER config use entity adapter`() {
        val existing = reminderEntity()
        Mockito.`when`(repository.findByLegacyCode("MATERIAL_REMINDER")).thenReturn(existing)
        Mockito.`when`(repository.findByIdAndDeletedAtIsNull(20L)).thenReturn(existing)
        Mockito.`when`(repository.findByConfigNameAndDeletedAtIsNull("材料提醒任务")).thenReturn(existing)
        Mockito.`when`(templateService.getById(99L)).thenReturn(
            com.weibo.talentintroduction.template.service.MailComposeTemplateDetail(
                id = 99L, templateCode = "MR", templateName = "reminder", subject = "s",
                description = null, mailType = "MATERIAL_REMINDER", subjectVariants = null,
                enabled = true, blocks = emptyList(), createdAt = null, updatedAt = null
            )
        )
        Mockito.`when`(templateRepository.findById(99L)).thenReturn(java.util.Optional.of(
            com.weibo.talentintroduction.template.domain.MailComposeTemplate(
                id = 99L,
                templateCode = "MR",
                templateName = "reminder",
                subject = "s",
                description = null,
                mailType = "MATERIAL_REMINDER",
                enabled = true
            )
        ))
        Mockito.`when`(repository.save(Mockito.any())).thenAnswer { inv ->
            (inv.arguments[0] as BatchSendTaskConfig).copy(id = 20L, legacyCode = "MATERIAL_REMINDER")
        }

        val got = controller().getConfigByType(BatchSendType.MATERIAL_REMINDER)
        assertEquals(BatchSendType.MATERIAL_REMINDER, got.body!!.sendType)
        assertEquals(0, got.body!!.dailyCap)

        val put = controller().updateConfigByType(
            BatchSendType.MATERIAL_REMINDER,
            BatchSendConfigUpdateRequest(
                autoEnabled = true,
                cron = "0 0 10 * * ?",
                dailyCap = 80,
                roundSize = 25,
                perMailIntervalMs = 3000,
                perRoundIntervalMs = 120000,
                selfCheckTtlMinutes = 30,
                templateId = 99L
            )
        )
        assertEquals(0, put.body!!.dailyCap)
        assertEquals("0 0 10 * * ?", put.body!!.cron)
        assertTrue(put.body!!.autoEnabled)
        Mockito.verify(repository).save(Mockito.any())
    }

    @Test
    fun `missing legacy entity returns 404`() {
        Mockito.`when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(null)
        try {
            controller().getConfig()
            throw AssertionError("expected ResponseStatusException")
        } catch (ex: org.springframework.web.server.ResponseStatusException) {
            assertEquals(HttpStatus.NOT_FOUND, ex.status)
        }
        Mockito.verify(repository, Mockito.never()).save(Mockito.any())
    }

    // ── 04a: cron preview + nextFireTime/lastExecutedAt in the configs response ───

    @Test
    fun `POST cron preview returns 200 with valid true and 5 times for legal expression`() {
        val response = controller().previewCron(CronPreviewRequest(cron = "0 0 9 * * ?"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, response.body!!.valid)
        assertEquals(5, response.body!!.nextFireTimes.size)
    }

    @Test
    fun `POST cron preview returns 200 with valid false for illegal expression`() {
        val response = controller().previewCron(CronPreviewRequest(cron = "每天九点"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(false, response.body!!.valid)
        assertTrue(!response.body!!.message.isNullOrEmpty())
        assertEquals(0, response.body!!.nextFireTimes.size)
    }

    @Test
    fun `GET configs response json contains nextFireTime and lastExecutedAt keys`() {
        Mockito.`when`(repository.findAllActiveOrderByUpdatedAtDescIdDesc())
            .thenReturn(listOf(introEntity(autoEnabled = true)))

        val response = controller().listConfigs(null)

        assertEquals(HttpStatus.OK, response.statusCode)
        val json = objectMapper.writeValueAsString(response.body)
        assertTrue(json.contains("nextFireTime"))
        assertTrue(json.contains("lastExecutedAt"))
    }
}
