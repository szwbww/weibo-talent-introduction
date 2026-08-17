package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigCreateCommand
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigUpdateCommand
import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

/**
 * 计划 06 —— 批量任务可达性过滤配置（I-6-1..I-6-5）。
 *
 * 覆盖：
 * - I-6-5：新建配置默认「不过滤」（reachabilityFilter == null）；
 * - I-6-2：update 持久化新列且 `toView()` 透出（读路径 get 同源）；
 * - I-6-4：非法档位抛 IllegalArgumentException（映射 400），复用
 *   `ExpertSearchService.ALLOWED_REACHABILITY_MODES` 单一真源；
 * - I-6-1 核心：旧 typed API 更新任意字段后新列不变（漏保留行会命中 Kotlin 默认值静默重置）；
 * - I-6-2 / N-3：`toLegacyConfig()` 返回的旧 typed 响应不含新列。
 */
class BatchSendTaskConfigReachabilityTest {

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

    private fun createCmd(
        name: String = "每日介绍",
        cron: String = "0 0 9 * * ?",
        roundSize: Int = 10,
        perMailIntervalMs: Long = 1000,
        perRoundIntervalMs: Long = 60000,
        selfCheckTtlMinutes: Int = 30,
        templateId: Long? = null,
        reachabilityFilter: String? = null
    ) = BatchSendTaskConfigCreateCommand(
        configName = name,
        autoEnabled = false,
        cron = cron,
        roundSize = roundSize,
        roundsPerRun = 1,
        perMailIntervalMs = perMailIntervalMs,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = selfCheckTtlMinutes,
        templateId = templateId,
        reachabilityFilter = reachabilityFilter
    )

    private fun updateCmd(
        name: String = "每日介绍",
        cron: String = "0 0 9 * * ?",
        roundSize: Int = 10,
        perMailIntervalMs: Long = 1000,
        perRoundIntervalMs: Long = 60000,
        selfCheckTtlMinutes: Int = 30,
        templateId: Long? = null,
        reachabilityFilter: String? = null
    ) = BatchSendTaskConfigUpdateCommand(
        configName = name,
        autoEnabled = false,
        cron = cron,
        roundSize = roundSize,
        roundsPerRun = 1,
        perMailIntervalMs = perMailIntervalMs,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = selfCheckTtlMinutes,
        templateId = templateId,
        reachabilityFilter = reachabilityFilter
    )

    private fun row(
        id: Long = 1L,
        name: String = "每日介绍",
        mailType: String = "INTRODUCTION",
        cron: String = "0 0 9 * * ?",
        tagsJson: String = "[]",
        emailDomainsJson: String = "[]",
        operatorStatusesJson: String = "[]",
        templateId: Long? = null,
        reachabilityFilter: String? = null,
        deletedAt: LocalDateTime? = null,
        updatedAt: LocalDateTime = LocalDateTime.of(2026, 7, 14, 10, 0)
    ) = BatchSendTaskConfig(
        id = id,
        configName = name,
        mailType = mailType,
        autoEnabled = false,
        cron = cron,
        roundSize = 10,
        roundsPerRun = 1,
        perMailIntervalMs = 1000,
        perRoundIntervalMs = 60000,
        selfCheckTtlMinutes = 30,
        funnelLevel = null,
        tagsJson = tagsJson,
        emailDomainsJson = emailDomainsJson,
        discipline = null,
        operatorStatusesJson = operatorStatusesJson,
        templateId = templateId,
        reachabilityFilter = reachabilityFilter,
        deletedAt = deletedAt,
        createdAt = updatedAt,
        updatedAt = updatedAt
    )

    @Test
    fun `create defaults reachabilityFilter to null (I-6-5)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("每日介绍")).thenReturn(null)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 11L)
        }

        val view = service().create(createCmd())

        assertNull(view.reachabilityFilter)
        verify(repository).save(captor.capture())
        assertNull(captor.value.reachabilityFilter)
        verify(eventPublisher).publishEvent(any(BatchSendCronChangedEvent::class.java))
    }

    @Test
    fun `update persists reachabilityFilter and view exposes it (I-6-2)`() {
        val existing = row(id = 5L)
        `when`(repository.findByIdAndDeletedAtIsNull(5L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("每日介绍")).thenReturn(existing)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { it.arguments[0] }

        val view = service().update(5L, updateCmd(reachabilityFilter = "EXCLUDE_BLOCKED"))

        // toView 透出（I-6-2）。
        assertEquals("EXCLUDE_BLOCKED", view.reachabilityFilter)
        verify(repository).save(captor.capture())
        assertEquals("EXCLUDE_BLOCKED", captor.value.reachabilityFilter)

        // 读路径 get → toView 同源透出。
        `when`(repository.findByIdAndDeletedAtIsNull(5L)).thenReturn(captor.value)
        assertEquals("EXCLUDE_BLOCKED", service().get(5L).reachabilityFilter)
    }

    @Test
    fun `create rejects illegal reachabilityFilter value (I-6-4)`() {
        `when`(repository.findByConfigNameAndDeletedAtIsNull("非法档位")).thenReturn(null)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service().create(createCmd(name = "非法档位", reachabilityFilter = "MEDIUM"))
        }
        assertTrue(ex.message!!.contains("reachabilityFilter must be one of"))
        verify(repository, never()).save(any())
    }

    @Test
    fun `update rejects illegal reachabilityFilter value without saving (I-6-4)`() {
        `when`(repository.findByIdAndDeletedAtIsNull(5L)).thenReturn(row(id = 5L))
        `when`(repository.findByConfigNameAndDeletedAtIsNull("每日介绍")).thenReturn(null)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service().update(5L, updateCmd(reachabilityFilter = "BOGUS"))
        }
        assertTrue(ex.message!!.contains("reachabilityFilter must be one of"))
        verify(repository, never()).save(any())
    }

    @Test
    fun `legacy typed update preserves existing reachabilityFilter (I-6-1 core)`() {
        val existing = BatchSendTaskConfig(
            id = 2L, configName = "默认介绍邮件任务", mailType = "INTRODUCTION",
            autoEnabled = false, cron = "0 0 0 * * ?", roundSize = 50,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            discipline = null, templateId = null, legacyCode = "INTRODUCTION",
            reachabilityFilter = "EXCLUDE_BLOCKED",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        `when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(existing)
        `when`(repository.findByIdAndDeletedAtIsNull(2L)).thenReturn(existing)
        `when`(repository.findByConfigNameAndDeletedAtIsNull("默认介绍邮件任务")).thenReturn(existing)
        val captor = ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        `when`(repository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 2L, legacyCode = "INTRODUCTION")
        }

        // 旧 typed API 只改 cron —— 请求体不含可达性过滤字段。
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
        val saved = captor.value
        // I-6-1: 漏写保留行会命中 BatchSendTaskConfigUpdateCommand 的 Kotlin 默认值，静默重置为不过滤。
        assertEquals("EXCLUDE_BLOCKED", saved.reachabilityFilter)
        assertEquals("0 30 8 * * ?", saved.cron)
    }

    @Test
    fun `legacy typed response does not expose reachabilityFilter (I-6-2 N-3)`() {
        val entity = BatchSendTaskConfig(
            id = 1L, configName = "默认介绍邮件任务", mailType = "INTRODUCTION",
            autoEnabled = true, cron = "0 0 7 * * ?", roundSize = 10,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            emailDomainsJson = """["edu.cn"]""", discipline = "STEM", templateId = null,
            legacyCode = "INTRODUCTION", reachabilityFilter = "HIGH_ONLY",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        `when`(repository.findByLegacyCode("INTRODUCTION")).thenReturn(entity)

        val config = service().getLegacyConfig(BatchSendType.INTRODUCTION)
        val json = objectMapper.writeValueAsString(config)

        assertEquals("HIGH_ONLY", entity.reachabilityFilter)
        // N-3 / I-6-2: 旧 typed 适配器（KV 兼容层）不带新列。
        assertFalse(json.contains("reachabilityFilter"), "legacy typed response must not carry the new column")
    }
}
