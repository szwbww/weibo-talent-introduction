package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigCreateCommand
import com.weibo.talentintroduction.campaign.domain.OperatorStatus
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigUpdateCommand
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigView
import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.slf4j.LoggerFactory
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
    private val eventPublisher: ApplicationEventPublisher,
    private val taskExecutionService: TaskExecutionService
) {

    private val log = LoggerFactory.getLogger(BatchSendTaskConfigService::class.java)

    fun list(query: String?): List<BatchSendTaskConfigView> {
        val trimmed = query?.trim().orEmpty()
        val rows = if (trimmed.isEmpty()) {
            repository.findAllActiveOrderByUpdatedAtDescIdDesc()
        } else {
            repository.findAllActiveByConfigNameContainingOrderByUpdatedAtDescIdDesc(trimmed)
        }
        // I-4: one aggregated query for all last-executed timestamps, never one per row.
        val ids = rows.mapNotNull { it.id }
        val lastExecutedMap = taskExecutionService.lastExecutedAtByBatchConfigIds(ids)
        return rows.map { toView(it, lastExecutedMap[it.id]) }
    }

    fun get(id: Long): BatchSendTaskConfigView {
        val row = repository.findByIdAndDeletedAtIsNull(id)
            ?: throw NoSuchElementException("Batch send task config not found: $id")
        return toView(row, taskExecutionService.lastExecutedAtByBatchConfigIds(listOf(id))[id])
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
                roundSize = normalized.roundSize,
                roundsPerRun = normalized.roundsPerRun,
                perMailIntervalMs = normalized.perMailIntervalMs,
                perRoundIntervalMs = normalized.perRoundIntervalMs,
                selfCheckTtlMinutes = normalized.selfCheckTtlMinutes,
                funnelLevel = normalized.funnelLevel,
                tagsJson = normalized.tagsJson,
                regionsJson = normalized.regionsJson,
                emailDomainsJson = normalized.emailDomainsJson,
                discipline = normalized.discipline,
                operatorStatus = normalized.operatorStatus,
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
                roundSize = normalized.roundSize,
                roundsPerRun = normalized.roundsPerRun,
                perMailIntervalMs = normalized.perMailIntervalMs,
                perRoundIntervalMs = normalized.perRoundIntervalMs,
                selfCheckTtlMinutes = normalized.selfCheckTtlMinutes,
                funnelLevel = normalized.funnelLevel,
                tagsJson = normalized.tagsJson,
                regionsJson = normalized.regionsJson,
                emailDomainsJson = normalized.emailDomainsJson,
                discipline = normalized.discipline,
                operatorStatus = normalized.operatorStatus,
                templateId = normalized.templateId,
                updatedAt = now
            ),
            configName = normalized.configName
        )
        publishReload(normalized.cron)
        return toView(saved, taskExecutionService.lastExecutedAtByBatchConfigIds(listOf(id))[id])
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
        return toView(saved, taskExecutionService.lastExecutedAtByBatchConfigIds(listOf(id))[id])
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
                roundSize = request.roundSize,
                roundsPerRun = existing.roundsPerRun,
                perMailIntervalMs = request.perMailIntervalMs,
                perRoundIntervalMs = request.perRoundIntervalMs,
                selfCheckTtlMinutes = request.selfCheckTtlMinutes,
                funnelLevel = existing.funnelLevel,
                tags = parseTags(existing.tagsJson),
                regions = parseRegions(existing.regionsJson),
                emailDomains = parseEmailDomains(existing.emailDomainsJson),
                discipline = request.discipline.ifBlank { null },
                operatorStatus = existing.operatorStatus,
                templateId = request.templateId
            )
        )
        return BatchSendConfig(
            sendType = sendType,
            autoEnabled = view.autoEnabled,
            cron = view.cron,
            dailyCap = LEGACY_DAILY_CAP_UNUSED,
            roundSize = view.roundSize,
            perMailIntervalMs = view.perMailIntervalMs,
            perRoundIntervalMs = view.perRoundIntervalMs,
            selfCheckTtlMinutes = view.selfCheckTtlMinutes,
            emailDomain = view.emailDomains.firstOrNull().orEmpty(),
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
            dailyCap = LEGACY_DAILY_CAP_UNUSED,
            roundSize = row.roundSize,
            perMailIntervalMs = row.perMailIntervalMs,
            perRoundIntervalMs = row.perRoundIntervalMs,
            selfCheckTtlMinutes = row.selfCheckTtlMinutes,
            emailDomain = parseEmailDomains(row.emailDomainsJson).firstOrNull().orEmpty(),
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
        // I2a-2 / I2a-5：trim、丢空、去重保序；空集合 = 不限。逗号是前端 picker 的
        // 分隔符（K-batch-picker-comma-delimited-contract），含逗号的域名会在回显时被拆坏。
        val emailDomains = fields.emailDomains
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        emailDomains.forEach {
            require(!it.contains(",")) { "emailDomain must not contain a comma: $it" }
        }
        val emailDomainsJson = objectMapper.writeValueAsString(emailDomains)
        val discipline = normalizeOptionalFilter(fields.discipline)
        if (discipline != null) {
            require(discipline in ALLOWED_DISCIPLINES) {
                "discipline must be one of $ALLOWED_DISCIPLINES or ALL/empty"
            }
        }
        // I-3: 状态白名单引用 OperatorStatus.entries（单一权威，照 ALLOWED_DISCIPLINES 范式），
        // 不另抄字符串集合，避免与枚举漂移。
        val operatorStatus = normalizeOptionalFilter(fields.operatorStatus)
        if (operatorStatus != null) {
            require(operatorStatus in ALLOWED_OPERATOR_STATUSES) {
                "operatorStatus must be one of $ALLOWED_OPERATOR_STATUSES or ALL/empty"
            }
        }

        val tags = normalizeTags(fields.tags)
        val tagsJson = objectMapper.writeValueAsString(tags)
        val regions = normalizeRegions(fields.regions)
        val regionsJson = objectMapper.writeValueAsString(regions)
        val mailType = resolveMailType(fields.templateId)

        return NormalizedConfig(
            configName = configName,
            mailType = mailType,
            autoEnabled = fields.autoEnabled,
            cron = fields.cron.trim(),
            roundSize = fields.roundSize,
            roundsPerRun = fields.roundsPerRun,
            perMailIntervalMs = fields.perMailIntervalMs,
            perRoundIntervalMs = fields.perRoundIntervalMs,
            selfCheckTtlMinutes = fields.selfCheckTtlMinutes,
            funnelLevel = funnelLevel,
            tagsJson = tagsJson,
            regionsJson = regionsJson,
            emailDomainsJson = emailDomainsJson,
            discipline = discipline,
            operatorStatus = operatorStatus,
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

    /**
     * I-1: regions must be English domain constants; invalid values are rejected (422),
     * never silently yielding zero ES hits. Empty list = no restriction.
     */
    private fun normalizeRegions(regions: List<String>): List<String> {
        val cleaned = regions.map { it.trim() }.filter { it.isNotEmpty() }
        cleaned.forEach { region ->
            require(region in CountryContinentMapping.allRegions()) {
                "region must be one of ${CountryContinentMapping.allRegions()}"
            }
        }
        return cleaned.distinct().sortedBy { CountryContinentMapping.allRegions().indexOf(it) }
    }

    private fun parseRegions(regionsJson: String): List<String> =
        try {
            objectMapper.readValue(regionsJson, object : TypeReference<List<String>>() {})
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sortedBy { CountryContinentMapping.allRegions().indexOf(it) }
        } catch (e: Exception) {
            emptyList()
        }

    private fun parseEmailDomains(json: String?): List<String> {
        val text = json?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        return try {
            objectMapper.readValue(text, object : TypeReference<List<String>>() {})
                .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        } catch (e: Exception) {
            log.warn("Failed to parse email_domains_json, treating as unrestricted: {}", e.message)
            emptyList()
        }
    }

    private fun toView(row: BatchSendTaskConfig, lastExecutedAt: LocalDateTime? = null): BatchSendTaskConfigView {
        val id = row.id ?: error("Batch send task config id is required")
        return BatchSendTaskConfigView(
            id = id,
            configName = row.configName,
            mailType = row.mailType,
            autoEnabled = row.autoEnabled,
            cron = row.cron,
            roundSize = row.roundSize,
            roundsPerRun = row.roundsPerRun,
            perMailIntervalMs = row.perMailIntervalMs,
            perRoundIntervalMs = row.perRoundIntervalMs,
            selfCheckTtlMinutes = row.selfCheckTtlMinutes,
            funnelLevel = row.funnelLevel,
            tags = parseTags(row.tagsJson),
            regions = parseRegions(row.regionsJson),
            emailDomains = parseEmailDomains(row.emailDomainsJson),
            discipline = row.discipline,
            operatorStatus = row.operatorStatus,
            templateId = row.templateId,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            nextFireTime = computeNextFireTime(row.autoEnabled, row.cron),
            lastExecutedAt = lastExecutedAt
        )
    }

    /**
     * I-1/I-2/I-3: same Spring 6-field cron implementation as the scheduler's CronTrigger.
     * Disabled configs and invalid cron degrade to null — a single dirty row must never
     * 500 the config list (X-4).
     */
    private fun computeNextFireTime(autoEnabled: Boolean, cron: String): LocalDateTime? {
        if (!autoEnabled) return null
        return runCatching { CronExpression.parse(cron).next(LocalDateTime.now()) }.getOrNull()
    }

    /** cron 预览：只读校验 + 最近 N 次触发时间。非法表达式返回 valid=false，不抛异常（I-3）。 */
    fun previewCron(cron: String, count: Int = 5): CronPreviewResult {
        val trimmed = cron.trim()
        if (trimmed.isEmpty()) return CronPreviewResult(false, "cron 表达式不能为空", emptyList())
        val expr = runCatching { CronExpression.parse(trimmed) }.getOrElse { e ->
            return CronPreviewResult(
                false,
                "不是合法的 Spring cron 表达式（6 段，秒 分 时 日 月 周）：${e.message}",
                emptyList()
            )
        }
        val times = mutableListOf<LocalDateTime>()
        var cursor = LocalDateTime.now()
        repeat(count.coerceIn(1, 20)) {
            val next = expr.next(cursor) ?: return@repeat
            times.add(next)
            cursor = next
        }
        return if (times.isEmpty()) {
            CronPreviewResult(false, "该表达式在可预见的未来没有触发时间", emptyList())
        } else {
            CronPreviewResult(true, null, times)
        }
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
        val roundSize: Int,
        val roundsPerRun: Int,
        val perMailIntervalMs: Long,
        val perRoundIntervalMs: Long,
        val selfCheckTtlMinutes: Int,
        val funnelLevel: String?,
        val tags: List<String>,
        val regions: List<String>,
        val emailDomains: List<String>,
        val discipline: String?,
        val operatorStatus: String?,
        val templateId: Long?
    )

    private data class NormalizedConfig(
        val configName: String,
        val mailType: String,
        val autoEnabled: Boolean,
        val cron: String,
        val roundSize: Int,
        val roundsPerRun: Int,
        val perMailIntervalMs: Long,
        val perRoundIntervalMs: Long,
        val selfCheckTtlMinutes: Int,
        val funnelLevel: String?,
        val tagsJson: String,
        val regionsJson: String,
        val emailDomainsJson: String,
        val discipline: String?,
        val operatorStatus: String?,
        val templateId: Long?
    )

    private fun BatchSendTaskConfigCreateCommand.toFields() = ConfigFields(
        configName = configName,
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
        operatorStatus = operatorStatus,
        templateId = templateId
    )

    private fun BatchSendTaskConfigUpdateCommand.toFields() = ConfigFields(
        configName = configName,
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
        operatorStatus = operatorStatus,
        templateId = templateId
    )

    private fun BatchSendTaskConfig.toFields() = ConfigFields(
        configName = configName,
        autoEnabled = autoEnabled,
        cron = cron,
        roundSize = roundSize,
        roundsPerRun = roundsPerRun,
        perMailIntervalMs = perMailIntervalMs,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = selfCheckTtlMinutes,
        funnelLevel = funnelLevel,
        tags = parseTags(tagsJson),
        regions = parseRegions(regionsJson),
        emailDomains = parseEmailDomains(emailDomainsJson),
        discipline = discipline,
        operatorStatus = operatorStatus,
        templateId = templateId
    )

    private companion object {
        /**
         * 日限额已下线，此值仅为旧 typed API 保持字段形态，不参与任何判定。
         */
        const val LEGACY_DAILY_CAP_UNUSED = 0

        val ALLOWED_MAIL_TYPES = setOf(
            BatchSendType.INTRODUCTION.name,
            BatchSendType.MATERIAL_REMINDER.name
        )
        val ALLOWED_FUNNEL_LEVELS = setOf("CANDIDATE", "APPLICATION")
        // I-5: single authority is ExpertSearchService.ALLOWED_DISCIPLINES (already includes UNCLASSIFIED);
        // keeping a second literal here would risk divergence (see plan 05 A-4).
        val ALLOWED_DISCIPLINES = ExpertSearchService.ALLOWED_DISCIPLINES
        // I-3: 状态白名单单一权威是 OperatorStatus 枚举本身（照 :ALLOWED_DISCIPLINES 范式），
        // 不另抄字符串集合，避免与枚举漂移（NOT_CONTACTED 的 must_not-exists 语义见 ExpertSearchService）。
        val ALLOWED_OPERATOR_STATUSES = OperatorStatus.entries.map { it.name }.toSet()
    }
}

/** 只读 cron 预览结果：valid=false 表示表达式非法/永不触发，message 为原因（I-3，永不抛异常）。 */
data class CronPreviewResult(
    val valid: Boolean,
    val message: String?,
    val nextFireTimes: List<LocalDateTime>
)
