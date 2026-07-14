package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigCreateCommand
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigUpdateCommand
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigView
import com.weibo.talentintroduction.campaign.service.BatchSendConfig
import com.weibo.talentintroduction.campaign.service.BatchSendConfigUpdateRequest
import com.weibo.talentintroduction.campaign.service.BatchSendControlService
import com.weibo.talentintroduction.campaign.service.BatchSendSettingService
import com.weibo.talentintroduction.campaign.service.BatchSendStatusView
import com.weibo.talentintroduction.campaign.service.BatchSendTaskConfigService
import com.weibo.talentintroduction.campaign.service.BatchSendType
import com.weibo.talentintroduction.campaign.service.ManualInitialOutreachService
import com.weibo.talentintroduction.campaign.service.PendingOutreachSummary
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mail/batch-send")
class BatchSendConfigController(
    private val batchSendSettingService: BatchSendSettingService,
    private val batchSendTaskConfigService: BatchSendTaskConfigService,
    private val templateRepository: MailComposeTemplateRepository,
    private val batchSendControlService: BatchSendControlService,
    private val manualInitialOutreachService: ManualInitialOutreachService
) {
    // ── New multi-config CRUD ──────────────────────────────────────────────────

    @GetMapping("/configs")
    fun listConfigs(@RequestParam(required = false) q: String?): ResponseEntity<List<BatchSendTaskConfigView>> =
        ResponseEntity.ok(batchSendTaskConfigService.list(q))

    @PostMapping("/configs")
    fun createConfig(@RequestBody request: BatchSendTaskConfigCreateCommand): ResponseEntity<BatchSendTaskConfigView> =
        ResponseEntity.status(HttpStatus.CREATED).body(batchSendTaskConfigService.create(request))

    @GetMapping("/configs/{id}")
    fun getConfigById(@PathVariable id: Long): ResponseEntity<BatchSendTaskConfigView> =
        ResponseEntity.ok(batchSendTaskConfigService.get(id))

    @PutMapping("/configs/{id}")
    fun updateConfigById(
        @PathVariable id: Long,
        @RequestBody request: BatchSendTaskConfigUpdateCommand
    ): ResponseEntity<BatchSendTaskConfigView> =
        ResponseEntity.ok(batchSendTaskConfigService.update(id, request))

    @PatchMapping("/configs/{id}/enabled")
    fun setConfigEnabled(
        @PathVariable id: Long,
        @RequestBody request: BatchSendTaskConfigEnabledRequest
    ): ResponseEntity<BatchSendTaskConfigView> =
        ResponseEntity.ok(batchSendTaskConfigService.setEnabled(id, request.enabled))

    @DeleteMapping("/configs/{id}")
    fun deleteConfig(@PathVariable id: Long): ResponseEntity<Void> {
        batchSendTaskConfigService.softDelete(id)
        return ResponseEntity.noContent().build()
    }

    // ── INTRODUCTION compat config endpoints (legacy) ──────────────────────────

    @GetMapping("/config")
    fun getConfig(): ResponseEntity<BatchSendConfig> =
        ResponseEntity.ok(batchSendSettingService.getConfig())

    @PutMapping("/config")
    fun updateConfig(@RequestBody request: BatchSendConfigUpdateRequest): ResponseEntity<BatchSendConfig> {
        validateTemplate(request.templateId, BatchSendType.INTRODUCTION)
        return ResponseEntity.ok(batchSendSettingService.updateConfig(request))
    }

    // ── Typed config endpoints (legacy compat) ─────────────────────────────────

    @GetMapping("/types/{sendType}/config")
    fun getConfigByType(@PathVariable sendType: BatchSendType): ResponseEntity<BatchSendConfig> =
        ResponseEntity.ok(batchSendSettingService.getConfig(sendType))

    @PutMapping("/types/{sendType}/config")
    fun updateConfigByType(
        @PathVariable sendType: BatchSendType,
        @RequestBody request: BatchSendConfigUpdateRequest
    ): ResponseEntity<BatchSendConfig> {
        validateTemplate(request.templateId, sendType)
        return ResponseEntity.ok(batchSendSettingService.updateConfig(request, sendType))
    }

    // ── Typed control endpoints ────────────────────────────────────────────────

    @GetMapping("/types/{sendType}/pending-count")
    fun getPendingCount(@PathVariable sendType: BatchSendType): ResponseEntity<PendingOutreachSummary> =
        ResponseEntity.ok(manualInitialOutreachService.countPending(sendType))

    @PostMapping("/types/{sendType}/start")
    fun startManual(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
        batchSendControlService.startManual(sendType)

    @PostMapping("/types/{sendType}/pause")
    fun pause(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
        batchSendControlService.pause("OPERATOR", sendType)

    @PostMapping("/types/{sendType}/manual")
    fun runManualOnce(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
        batchSendControlService.runManualOnce(sendType)

    @PostMapping("/types/{sendType}/start-auto")
    fun startAuto(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
        batchSendControlService.startAuto(sendType)

    @PostMapping("/types/{sendType}/resume-schedule")
    fun resumeSchedule(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
        batchSendControlService.resumeSchedule(sendType)

    @PostMapping("/types/{sendType}/pause-schedule")
    fun pauseSchedule(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
        batchSendControlService.pauseSchedule(sendType)

    @GetMapping("/types/{sendType}/status")
    fun getStatus(@PathVariable sendType: BatchSendType): ResponseEntity<BatchSendStatusView> =
        ResponseEntity.ok(batchSendControlService.getStatus(sendType))

    // ── I-7: template type gate (legacy typed API) ─────────────────────────────

    /**
     * I-7: enforces that the template pointed to by templateId is enabled and
     * has the mailType matching sendType.
     * INTRODUCTION: null templateId is allowed (falls back to default INTRODUCTION template).
     * MATERIAL_REMINDER: templateId must not be null (enforced in service validate; checked here too).
     */
    private fun validateTemplate(templateId: Long?, sendType: BatchSendType) {
        if (sendType == BatchSendType.MATERIAL_REMINDER) {
            requireNotNull(templateId) { "MATERIAL_REMINDER config requires a templateId" }
        }
        if (templateId == null) return
        val template = templateRepository.findById(templateId).orElse(null)
            ?: throw IllegalArgumentException("Template $templateId not found")
        require(template.enabled) { "Template $templateId is not enabled" }
        val expectedMailType = sendType.name
        require(template.mailType == expectedMailType) {
            "Template $templateId has mailType=${template.mailType}, expected $expectedMailType for $sendType"
        }
    }
}

data class BatchSendTaskConfigEnabledRequest(
    val enabled: Boolean
)
