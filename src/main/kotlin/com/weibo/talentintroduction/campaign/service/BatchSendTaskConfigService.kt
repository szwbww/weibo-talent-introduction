package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigCreateCommand
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigUpdateCommand
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigView
import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@Service
class BatchSendTaskConfigService(
    private val repository: BatchSendTaskConfigRepository,
    private val mailComposeTemplateService: MailComposeTemplateService,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun list(query: String?): List<BatchSendTaskConfigView> {
        val trimmed = query?.trim().orEmpty()
        val rows = if (trimmed.isEmpty()) {
            repository.findAllActiveOrderByUpdatedAtDescIdDesc()
        } else {
            repository.findAllActiveByConfigNameContainingOrderByUpdatedAtDescIdDesc(trimmed)
        }
        return rows.map { toView(it) }
    }

    fun get(id: Long): BatchSendTaskConfigView {
        val row = repository.findByIdAndDeletedAtIsNull(id)
            ?: throw NoSuchElementException("Batch send task config not found: $id")
        return toView(row)
    }

    @Transactional
    fun create(cmd: BatchSendTaskConfigCreateCommand): BatchSendTaskConfigView {
        val normalized = normalizeAndValidate(cmd.toFields(), excludeId = null)
        val now = LocalDateTime.now()
        val saved = saveConfig(
            BatchSendTaskConfig(
                configName = normalized.configName,
                mailType = normalized.mailType,
                autoEnabled = normalized.autoEnabled,
                cron = normalized.cron,
                dailyCap = normalized.dailyCap,
                roundSize = normalized.roundSize,
                roundsPerRun = normalized.roundsPerRun,
                perMailIntervalMs = normalized.perMailIntervalMs,
                perRoundIntervalMs = normalized.perRoundIntervalMs,
                selfCheckTtlMinutes = normalized.selfCheckTtlMinutes,
                funnelLevel = normalized.funnelLevel,
                tagsJson = normalized.tagsJson,
                emailDomain = normalized.emailDomain,
                discipline = normalized.discipline,
                templateId = normalized.templateId,
                createdAt = now,
                updatedAt = now
            ),
            configName = normalized.configName
        )
        publishReload(normalized.cron)
        return toView(saved)
    }

    @Transactional
    fun update(id: Long, cmd: BatchSendTaskConfigUpdateCommand): BatchSendTaskConfigView {
        val existing = repository.findByIdAndDeletedAtIsNull(id)
            ?: throw NoSuchElementException("Batch send task config not found: $id")
        val normalized = normalizeAndValidate(cmd.toFields(), excludeId = id)
        val now = LocalDateTime.now()
        val saved = saveConfig(
            existing.copy(
                configName = normalized.configName,
                mailType = normalized.mailType,
                autoEnabled = normalized.autoEnabled,
                cron = normalized.cron,
                dailyCap = normalized.dailyCap,
                roundSize = normalized.roundSize,
                roundsPerRun = normalized.roundsPerRun,
                perMailIntervalMs = normalized.perMailIntervalMs,
                perRoundIntervalMs = normalized.perRoundIntervalMs,
                selfCheckTtlMinutes = normalized.selfCheckTtlMinutes,
                funnelLevel = normalized.funnelLevel,
                tagsJson = normalized.tagsJson,
                emailDomain = normalized.emailDomain,
                discipline = normalized.discipline,
                templateId = normalized.templateId,
                updatedAt = now
            ),
            configName = normalized.configName
        )
        publishReload(normalized.cron)
        return toView(saved)
    }

    @Transactional
    fun setEnabled(id: Long, enabled: Boolean): BatchSendTaskConfigView {
        val existing = repository.findByIdAndDeletedAtIsNull(id)
            ?: throw NoSuchElementException("Batch send task config not found: $id")
        if (enabled) {
            if (existing.mailType == BatchSendType.MATERIAL_REMINDER.name && existing.templateId == null) {
                throw ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "MATERIAL_REMINDER config requires a templateId"
                )
            }
            normalizeAndValidate(existing.toFields().copy(autoEnabled = true), excludeId = id)
        }
        val now = LocalDateTime.now()
        val saved = repository.save(
            existing.copy(
                autoEnabled = enabled,
                updatedAt = now
            )
        )
        publishReload(saved.cron)
        return toView(saved)
    }

    @Transactional
    fun softDelete(id: Long) {
        val existing = repository.findByIdAndDeletedAtIsNull(id)
            ?: throw NoSuchElementException("Batch send task config not found: $id")
        val now = LocalDateTime.now()
        repository.save(
            existing.copy(
                autoEnabled = false,
                deletedAt = now,
                updatedAt = now
            )
        )
        publishReload(existing.cron)
    }

    /**
     * Legacy typed API adapter: read the seeded `legacy_code` entity as [BatchSendConfig].
     * Soft-deleted / missing rows → 404; never falls back to KV.
     */
    fun getLegacyConfig(sendType: BatchSendType): BatchSendConfig =
        toLegacyConfig(requireActiveLegacy(sendType), sendType)

    /**
     * Legacy typed API adapter: update the seeded `legacy_code` entity via the same validate/save/reload path.
     * Preserves entity `configName` / funnel / tags; maps old [BatchSendConfigUpdateRequest] fields only.
     */
    @Transactional
    fun updateLegacyConfig(sendType: BatchSendType, request: BatchSendConfigUpdateRequest): BatchSendConfig {
        val existing = requireActiveLegacy(sendType)
        val id = existing.id ?: error("Batch send task config id is required")
        val view = update(
            id,
            BatchSendTaskConfigUpdateCommand(
                configName = existing.configName,
                autoEnabled = request.autoEnabled,
                cron = request.cron,
                dailyCap = request.dailyCap,
                roundSize = request.roundSize,
                roundsPerRun = existing.roundsPerRun,
                perMailIntervalMs = request.perMailIntervalMs,
                perRoundIntervalMs = request.perRoundIntervalMs,
                selfCheckTtlMinutes = request.selfCheckTtlMinutes,
                funnelLevel = existing.funnelLevel,
                tags = parseTags(existing.tagsJson),
                emailDomain = request.emailDomain.ifBlank { null },
                discipline = request.discipline.ifBlank { null },
                templateId = request.templateId
            )
        )
        return BatchSendConfig(
            sendType = sendType,
            autoEnabled = view.autoEnabled,
            cron = view.cron,
            dailyCap = view.dailyCap,
            roundSize = view.roundSize,
            perMailIntervalMs = view.perMailIntervalMs,
            perRoundIntervalMs = view.perRoundIntervalMs,
            selfCheckTtlMinutes = view.selfCheckTtlMinutes,
            emailDomain = view.emailDomain.orEmpty(),
            discipline = view.discipline.orEmpty(),
            templateId = view.templateId
        )
    }

    private fun requireActiveLegacy(sendType: BatchSendType): BatchSendTaskConfig {
        val row = repository.findByLegacyCode(sendType.name)
        if (row == null || row.deletedAt != null) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Legacy batch send config not found: ${sendType.name}"
            )
        }
        return row
    }

    private fun toLegacyConfig(row: BatchSendTaskConfig, sendType: BatchSendType): BatchSendConfig =
        BatchSendConfig(
            sendType = sendType,
            autoEnabled = row.autoEnabled,
            cron = row.cron,
            dailyCap = row.dailyCap,
            roundSize = row.roundSize,
            perMailIntervalMs = row.perMailIntervalMs,
            perRoundIntervalMs = row.perRoundIntervalMs,
            selfCheckTtlMinutes = row.selfCheckTtlMinutes,
            emailDomain = row.emailDomain.orEmpty(),
            discipline = row.discipline.orEmpty(),
            templateId = row.templateId
        )

    private fun normalizeAndValidate(fields: ConfigFields, excludeId: Long?): NormalizedConfig {
        val configName = fields.configName.trim()
        require(configName.isNotEmpty()) { "configName must not be blank" }
        require(configName.length <= 120) { "configName must be <= 120 characters" }

        val duplicate = repository.findByConfigNameAndDeletedAtIsNull(configName)
        if (duplicate != null && duplicate.id != excludeId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Config name already exists: $configName")
        }

        require(fields.dailyCap > 0) { "dailyCap must be > 0" }
        require(fields.roundSize > 0) { "roundSize must be > 0" }
        require(fields.roundsPerRun >= 1) { "roundsPerRun must be >= 1" }
        require(fields.perMailIntervalMs >= 0) { "perMailIntervalMs must be >= 0" }
        require(fields.perRoundIntervalMs >= 0) { "perRoundIntervalMs must be >= 0" }
        require(fields.selfCheckTtlMinutes >= 1) { "selfCheckTtlMinutes must be >= 1" }
        require(fields.cron.isNotBlank()) { "cron must not be blank" }
        try {
            CronExpression.parse(fields.cron)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("cron is not a valid Spring cron expression: ${fields.cron}", e)
        }

        val funnelLevel = normalizeFunnelLevel(fields.funnelLevel)
        val emailDomain = normalizeOptionalFilter(fields.emailDomain)
        val discipline = normalizeOptionalFilter(fields.discipline)
        if (discipline != null) {
            require(discipline in ALLOWED_DISCIPLINES) {
                "discipline must be one of $ALLOWED_DISCIPLINES or ALL/empty"
            }
        }

        val tags = normalizeTags(fields.tags)
        val tagsJson = objectMapper.writeValueAsString(tags)
        val mailType = resolveMailType(fields.templateId)

        return NormalizedConfig(
            configName = configName,
            mailType = mailType,
            autoEnabled = fields.autoEnabled,
            cron = fields.cron.trim(),
            dailyCap = fields.dailyCap,
            roundSize = fields.roundSize,
            roundsPerRun = fields.roundsPerRun,
            perMailIntervalMs = fields.perMailIntervalMs,
            perRoundIntervalMs = fields.perRoundIntervalMs,
            selfCheckTtlMinutes = fields.selfCheckTtlMinutes,
            funnelLevel = funnelLevel,
            tagsJson = tagsJson,
            emailDomain = emailDomain,
            discipline = discipline,
            templateId = fields.templateId
        )
    }

    /**
     * I-2: derive mailType from enabled template; null templateId => INTRODUCTION only.
     * Invalid/disabled/wrong template => 422, never silent fallback.
     */
    private fun resolveMailType(templateId: Long?): String {
        if (templateId == null) {
            return BatchSendType.INTRODUCTION.name
        }
        require(templateId > 0) { "templateId must be > 0" }
        val template = try {
            mailComposeTemplateService.getById(templateId)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Template $templateId not found", e)
        }
        if (!template.enabled) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Template $templateId is not enabled")
        }
        val mailType = template.mailType?.trim().orEmpty()
        if (mailType !in ALLOWED_MAIL_TYPES) {
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Template $templateId has unsupported mailType=$mailType"
            )
        }
        return mailType
    }

    private fun normalizeFunnelLevel(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.equals("ALL", ignoreCase = true)) return null
        require(value in ALLOWED_FUNNEL_LEVELS) {
            "funnelLevel must be one of $ALLOWED_FUNNEL_LEVELS or empty/ALL"
        }
        return value
    }

    private fun normalizeOptionalFilter(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.equals("ALL", ignoreCase = true)) return null
        return value
    }

    private fun normalizeTags(tags: List<String>): List<String> =
        tags.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    private fun parseTags(tagsJson: String): List<String> =
        try {
            objectMapper.readValue(tagsJson, object : TypeReference<List<String>>() {})
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        } catch (e: Exception) {
            emptyList()
        }

    private fun toView(row: BatchSendTaskConfig): BatchSendTaskConfigView {
        val id = row.id ?: error("Batch send task config id is required")
        return BatchSendTaskConfigView(
            id = id,
            configName = row.configName,
            mailType = row.mailType,
            autoEnabled = row.autoEnabled,
            cron = row.cron,
            dailyCap = row.dailyCap,
            roundSize = row.roundSize,
            roundsPerRun = row.roundsPerRun,
            perMailIntervalMs = row.perMailIntervalMs,
            perRoundIntervalMs = row.perRoundIntervalMs,
            selfCheckTtlMinutes = row.selfCheckTtlMinutes,
            funnelLevel = row.funnelLevel,
            tags = parseTags(row.tagsJson),
            emailDomain = row.emailDomain,
            discipline = row.discipline,
            templateId = row.templateId,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt
        )
    }

    private fun publishReload(cron: String) {
        eventPublisher.publishEvent(BatchSendCronChangedEvent(cron, cron))
    }

    /**
     * Persist after service-level checks. Concurrent create/update can still race the
     * active-name unique key; map that conflict to 409 instead of 500.
     */
    private fun saveConfig(entity: BatchSendTaskConfig, configName: String): BatchSendTaskConfig =
        try {
            repository.save(entity)
        } catch (e: DuplicateKeyException) {
            throwActiveNameConflictOrRethrow(configName, e)
        } catch (e: DataIntegrityViolationException) {
            throwActiveNameConflictOrRethrow(configName, e)
        }

    private fun throwActiveNameConflictOrRethrow(configName: String, e: DataIntegrityViolationException): Nothing {
        if (isActiveNameUniqueViolation(e)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Config name already exists: $configName", e)
        }
        throw e
    }

    private fun isActiveNameUniqueViolation(e: DataIntegrityViolationException): Boolean {
        val messages = sequenceOf(e.message, e.mostSpecificCause.message)
            .filterNotNull()
            .joinToString(" ")
            .lowercase()
        return "uk_batch_send_task_config_active_name" in messages ||
            "active_config_name" in messages
    }

    private data class ConfigFields(
        val configName: String,
        val autoEnabled: Boolean,
        val cron: String,
        val dailyCap: Int,
        val roundSize: Int,
        val roundsPerRun: Int,
        val perMailIntervalMs: Long,
        val perRoundIntervalMs: Long,
        val selfCheckTtlMinutes: Int,
        val funnelLevel: String?,
        val tags: List<String>,
        val emailDomain: String?,
        val discipline: String?,
        val templateId: Long?
    )

    private data class NormalizedConfig(
        val configName: String,
        val mailType: String,
        val autoEnabled: Boolean,
        val cron: String,
        val dailyCap: Int,
        val roundSize: Int,
        val roundsPerRun: Int,
        val perMailIntervalMs: Long,
        val perRoundIntervalMs: Long,
        val selfCheckTtlMinutes: Int,
        val funnelLevel: String?,
        val tagsJson: String,
        val emailDomain: String?,
        val discipline: String?,
        val templateId: Long?
    )

    private fun BatchSendTaskConfigCreateCommand.toFields() = ConfigFields(
        configName = configName,
        autoEnabled = autoEnabled,
        cron = cron,
        dailyCap = dailyCap,
        roundSize = roundSize,
        roundsPerRun = roundsPerRun,
        perMailIntervalMs = perMailIntervalMs,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = selfCheckTtlMinutes,
        funnelLevel = funnelLevel,
        tags = tags,
        emailDomain = emailDomain,
        discipline = discipline,
        templateId = templateId
    )

    private fun BatchSendTaskConfigUpdateCommand.toFields() = ConfigFields(
        configName = configName,
        autoEnabled = autoEnabled,
        cron = cron,
        dailyCap = dailyCap,
        roundSize = roundSize,
        roundsPerRun = roundsPerRun,
        perMailIntervalMs = perMailIntervalMs,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = selfCheckTtlMinutes,
        funnelLevel = funnelLevel,
        tags = tags,
        emailDomain = emailDomain,
        discipline = discipline,
        templateId = templateId
    )

    private fun BatchSendTaskConfig.toFields() = ConfigFields(
        configName = configName,
        autoEnabled = autoEnabled,
        cron = cron,
        dailyCap = dailyCap,
        roundSize = roundSize,
        roundsPerRun = roundsPerRun,
        perMailIntervalMs = perMailIntervalMs,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = selfCheckTtlMinutes,
        funnelLevel = funnelLevel,
        tags = parseTags(tagsJson),
        emailDomain = emailDomain,
        discipline = discipline,
        templateId = templateId
    )

    private companion object {
        val ALLOWED_MAIL_TYPES = setOf(
            BatchSendType.INTRODUCTION.name,
            BatchSendType.MATERIAL_REMINDER.name
        )
        val ALLOWED_FUNNEL_LEVELS = setOf("CANDIDATE", "APPLICATION")
        val ALLOWED_DISCIPLINES = setOf("STEM", "HUMANITIES")
    }
}
