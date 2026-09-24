package com.weibo.talentintroduction.campaign.domain

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.service.BatchSendType
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import java.time.LocalDateTime

/** Immutable launch snapshot consumed once per execution (I-1). */
data class BatchExecutionSnapshot(
    val mailType: String,
    val roundSize: Int,
    val roundsPerRun: Int = 1,
    val perMailIntervalMs: Long,
    val perRoundIntervalMs: Long,
    val selfCheckTtlMinutes: Int,
    val funnelLevel: String? = null,
    val tags: List<String> = emptyList(),
    val regions: List<String> = emptyList(),
    val emailDomains: List<String> = emptyList(),
    val discipline: String? = null,
    val operatorStatuses: List<String> = emptyList(),
    val expertTypes: List<String> = emptyList(),
    /**
     * I-1/I-2: 本次执行唯一的发件账号范围快照（逻辑 `mail_sender_account.account_code`）。
     * `[]` = 不限（旧任务/未传字段）；非空 = 严格白名单，两发送循环与选号服务都只在此集合内运作。
     */
    val senderAccountCodes: List<String> = emptyList(),
    val templateId: Long? = null,
    val gateFilterEnabled: Boolean = false,
    /**
     * I-1/I-2: 研究方向三态（[ResearchDirectionFilters]）；默认 [ResearchDirectionFilters.ANY]。
     * 前端手动快照、`toExecutionSnapshot` 与 `RecipientScope.fromSnapshot` 逐字传递。
     */
    val researchDirectionFilter: String = ResearchDirectionFilters.ANY,
    val oneRoundOnly: Boolean = false,
    /**
     * I-1（快照）：发送前邮箱验证开关。旧 `request_payload` JSON 缺字段 = false（默认关闭）；
     * 只有 INTRODUCTION 允许 true；MATERIAL_REMINDER + true 在任何业务写入前拒绝。
     * 关闭时不调用验证 HTTP / 验证明细仓储，也不要求密钥。
     */
    val emailVerificationEnabled: Boolean = false
)

data class ManualBatchExecutionRequest(
    val sourceConfigId: Long? = null,
    val sourceUpdatedAt: LocalDateTime? = null,
    val snapshot: BatchExecutionSnapshot
)

data class ReasonCount(
    val label: String,
    val count: Int
)

data class OutcomeBreakdown(
    val target: Int,
    val success: Int,
    val failure: Int,
    val skipped: Int,
    val remaining: Int,
    val failureReasons: Map<String, ReasonCount> = emptyMap(),
    val skippedReasons: Map<String, ReasonCount> = emptyMap(),
    val errorSamples: List<String> = emptyList()
)

/** Unified recipient filter applied to ES and MySQL retry paths (I-3). */
data class RecipientScope(
    val mailType: String,
    val funnelLevels: Set<String>,
    val tags: List<String>,
    val regions: List<String>,
    val emailDomains: List<String>,
    val discipline: String?,
    val operatorStatuses: List<String> = emptyList(),
    /** I4-2: 研发类型收窄（INTRODUCTION 专属；空集合 = 发给零个人，fail-closed，见 [matchesExpertType]）。 */
    val expertTypes: List<String> = emptyList(),
    /** I4a-4: 已解析的门禁 ES 字段（ALLOWED_HAS_FIELDS 交集）；解析只发生在 resolveScope。 */
    val gateEsFields: List<String> = emptyList(),
    /**
     * I-2/I-3: 研究方向三态（[ResearchDirectionFilters]）。与 ES 侧同口径：
     * `PRESENT` 命中 `fieldPresenceFilter("researchFields")`（`exists AND NOT term ""`），
     * `ABSENT` 为其补集，`ANY` 不判定。与研发类型（`UNKNOWN`/`UNCLASSIFIED`）和模板门禁
     * 是彼此独立的 AND 维度（I-3）。
     */
    val researchDirectionFilter: String = ResearchDirectionFilters.ANY
) {
    fun matchesExpert(profile: com.weibo.talentintroduction.expert.domain.ExpertProfile): Boolean {
        // I3a-5：与 ES 的 operatorStatusesFilter 同口径 —— 多状态取 OR；
        // NOT_CONTACTED = ES 文档无该字段（I3a-1）；空集合不判定（I3a-3）。
        if (operatorStatuses.isNotEmpty()) {
            val matched = operatorStatuses.any {
                if (it == "NOT_CONTACTED") profile.operatorStatus.isNullOrBlank()
                else profile.operatorStatus == it
            }
            if (!matched) return false
        }
        // I4-1: INTRODUCTION 的唯一收口点；MATERIAL_REMINDER 不判定（零影响）。
        if (mailType == BatchSendType.INTRODUCTION.name && !matchesExpertType(profile)) return false
        if (!discipline.isNullOrBlank()) {
            val matched = if (discipline == "UNCLASSIFIED") {
                profile.disciplineCategory.isNullOrBlank()
            } else {
                profile.disciplineCategory == discipline
            }
            if (!matched) return false
        }
        // I2a-4: 与 ES 的 emailDomainsFilter 同口径 —— 多域取 OR；空集合不判定（I2a-2）。
        if (emailDomains.isNotEmpty()) {
            val email = profile.email
            if (email.isNullOrBlank()) return false
            if (emailDomains.none { email.endsWith("@$it") }) return false
        }
        // I-2: 方向三态与 ES 的 researchFields 存在性判据同口径。keyword 字段下
        // `fieldPresenceFilter("researchFields")` = `exists AND NOT term ""`，
        // 故只有 null/空串算「无」，纯空格串在 ES 里 exists 且非 term ""，算「有」。
        // I-3: 本判定与 expertTypes / gateEsFields 是独立维度，同时指定即 AND。
        when (researchDirectionFilter) {
            ResearchDirectionFilters.PRESENT -> if (profile.researchFields.isNullOrEmpty()) return false
            ResearchDirectionFilters.ABSENT -> if (!profile.researchFields.isNullOrEmpty()) return false
        }
        if (tags.isNotEmpty()) {
            val expertTags = profile.tags.orEmpty()
            if (tags.none { it in expertTags }) return false
        }
        if (regions.isNotEmpty()) {
            val expertRegion = com.weibo.talentintroduction.expert.domain
                .CountryContinentMapping.toRegion(profile.country)
            if (expertRegion !in regions) return false
        }
        // I4a-5: 与 ES 的 fieldPresenceFilter 同口径。BLANK_EXCLUDABLE_FIELDS
        // （researchFields / recentWorkTitles / patentTitles / degree）在 ES 侧是
        // `exists AND NOT term ""`，故空串不算有值；employment / institution 只有
        // `exists`，空串在 ES 里算有值，内存侧对应 `!= null`。
        if (gateEsFields.isNotEmpty()) {
            val allPresent = gateEsFields.all { field ->
                when (field) {
                    "employment" -> profile.employment != null
                    "institution" -> profile.institution != null
                    "degree" -> !profile.degree.isNullOrBlank()
                    "researchFields" -> !profile.researchFields.isNullOrBlank()
                    "recentWorkTitles" -> profile.recentWorkTitles?.any { it.isNotBlank() } == true
                    "patentTitles" -> profile.patentTitles?.any { it.isNotBlank() } == true
                    else -> true   // I4a-3 已裁剪，理论不可达；保守放行，不静默排除
                }
            }
            if (!allPresent) return false
        }
        return true
    }

    /**
     * I4-4: 研发类型判定的**唯一** Kotlin 实现，由 [matchesExpert] 与
     * ManualInitialOutreachService 的发送前门禁共同调用，禁止再复刻第二份。
     * I4-5: 与 ES 的 expertTypePredicate 同口径 —— `UNCLASSIFIED` = 类型为 null。
     * I4-2: 空集合返回 false（fail-closed）。
     */
    fun matchesExpertType(profile: ExpertProfile): Boolean {
        val typeName = profile.expertClassification?.type?.name
        return expertTypes.any { if (it == "UNCLASSIFIED") typeName == null else typeName == it }
    }

    companion object {
        fun fromSnapshot(snapshot: BatchExecutionSnapshot): RecipientScope {
            val levels = when (snapshot.funnelLevel?.trim()?.takeIf { it.isNotEmpty() }) {
                null -> setOf("CANDIDATE", "APPLICATION")
                "CANDIDATE" -> setOf("CANDIDATE")
                "APPLICATION" -> setOf("APPLICATION")
                else -> setOf(snapshot.funnelLevel)
            }
            return RecipientScope(
                mailType = snapshot.mailType,
                funnelLevels = levels,
                tags = snapshot.tags,
                regions = snapshot.regions,
                // I2a-2 / I2a-5：trim、丢空、去重保序；空集合 = 不限。
                emailDomains = snapshot.emailDomains.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                discipline = snapshot.discipline?.trim()?.takeIf { it.isNotEmpty() },
                // I3a-3：trim、丢空、去重保序；空集合 = 不限。
                operatorStatuses = snapshot.operatorStatuses.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                // I4-2：trim、丢空、去重保序；空集合在发信判定中 fail-closed（发给零个人）。
                expertTypes = snapshot.expertTypes.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                // I-1/I-2：三态原样传递（空白/未传值归一为 ANY）；非法值已在校验层被拒。
                researchDirectionFilter = ResearchDirectionFilters.normalize(snapshot.researchDirectionFilter)
            )
        }
    }
}

object BatchOutcomeReasonCodes {
    const val SEND_EXCEPTION = "SEND_EXCEPTION"
    const val TEMPLATE_RENDER_FAILED = "TEMPLATE_RENDER_FAILED"
    const val ACCOUNT_UNAVAILABLE = "ACCOUNT_UNAVAILABLE"
    const val SUPPRESSED = "SUPPRESSED"
    const val NO_CONTACT = "NO_CONTACT"
    const val DEDUP = "DEDUP"
    const val DAILY_CAP_EXCEEDED = "DAILY_CAP_EXCEEDED"
    const val CANCELLED = "CANCELLED"
    const val PERSONALIZATION_INCOMPLETE = "PERSONALIZATION_INCOMPLETE"
    const val EXPERT_NOT_SENDABLE = "EXPERT_NOT_SENDABLE"
    /**
     * I-3/I-4: 目标专家已有任一 `expert_contact.bound_sender_account_code`（与绑定值是否在
     * 本次选中集合无关）→ 本次批量任务跳过，不发信、不重选号、不改绑。
     */
    const val BOUND_SENDER_ALREADY_SET = "BOUND_SENDER_ALREADY_SET"
    /**
     * I-2/I-6: 发送前验证明确不通过（undeliverable/risky/unknown）。
     * 该目标跳过并占本轮处理槽，但不计 success、不计发送失败、不占账号发送量；
     * 明细行的 send_status=SKIPPED 使用同一码。
     */
    const val EMAIL_VERIFICATION_REJECTED = "EMAIL_VERIFICATION_REJECTED"

    val LABELS = mapOf(
        SEND_EXCEPTION to "发送异常",
        TEMPLATE_RENDER_FAILED to "模板渲染失败",
        ACCOUNT_UNAVAILABLE to "邮箱账号不可用",
        SUPPRESSED to "退订/抑制",
        NO_CONTACT to "无联系人账号",
        DEDUP to "去重跳过",
        DAILY_CAP_EXCEEDED to "超日限额",
        CANCELLED to "被取消",
        PERSONALIZATION_INCOMPLETE to "个性化字段缺失",
        EXPERT_NOT_SENDABLE to "研发类型不在本次选择范围内",
        BOUND_SENDER_ALREADY_SET to "专家已绑定发件账号",
        EMAIL_VERIFICATION_REJECTED to "邮箱验证未通过"
    )

    fun label(code: String): String = LABELS[code] ?: code
}

class OutcomeAccumulator(private val target: Int) {
    private val failureReasons = mutableMapOf<String, Int>()
    private val skippedReasons = mutableMapOf<String, Int>()
    private val errorSamples = mutableListOf<String>()

    var success: Int = 0
        private set
    var failure: Int = 0
        private set
    var skipped: Int = 0
        private set

    fun recordSuccess() {
        success++
    }

    fun recordFailure(code: String, sample: String? = null) {
        failure++
        failureReasons.merge(code, 1) { a, b -> a + b }
        addSample(sample)
    }

    fun recordSkipped(code: String, sample: String? = null) {
        skipped++
        skippedReasons.merge(code, 1) { a, b -> a + b }
        addSample(sample)
    }

    fun remaining(): Int = (target - success - failure - skipped).coerceAtLeast(0)

    /**
     * Move unprocessed slots into skipped reasons for terminal stop codes (I-6 coverage).
     * Leaves remaining untouched for soft stops like ONE_ROUND_DONE.
     */
    fun annotateTerminalRemaining(stopReason: String?) {
        val left = remaining()
        if (left <= 0) return
        val code = when (stopReason) {
            "CANCELLED" -> BatchOutcomeReasonCodes.CANCELLED
            "DAILY_CAP_REACHED" -> BatchOutcomeReasonCodes.DAILY_CAP_EXCEEDED
            "NO_AVAILABLE_ACCOUNT", "NO_SENDABLE_ACCOUNT",
            "WARMUP_LIMIT_REACHED", "DAILY_LIMIT_REACHED" -> BatchOutcomeReasonCodes.ACCOUNT_UNAVAILABLE
            else -> return
        }
        repeat(left) { recordSkipped(code) }
    }

    fun toBreakdown(): OutcomeBreakdown = OutcomeBreakdown(
        target = target,
        success = success,
        failure = failure,
        skipped = skipped,
        remaining = remaining(),
        failureReasons = failureReasons.mapValues { (code, count) ->
            ReasonCount(BatchOutcomeReasonCodes.label(code), count)
        },
        skippedReasons = skippedReasons.mapValues { (code, count) ->
            ReasonCount(BatchOutcomeReasonCodes.label(code), count)
        },
        errorSamples = errorSamples.toList()
    )

    fun failureReasonsMap(): Map<String, Int> = failureReasons.toMap()
    fun skippedReasonsMap(): Map<String, Int> = skippedReasons.toMap()

    private fun addSample(sample: String?) {
        if (sample.isNullOrBlank()) return
        if (errorSamples.size >= 20) errorSamples.removeAt(0)
        errorSamples.add(sample)
    }
}

fun BatchSendTaskConfig.toExecutionSnapshot(
    objectMapper: ObjectMapper,
    oneRoundOnly: Boolean = false
): BatchExecutionSnapshot {
    val tags = try {
        objectMapper.readValue(tagsJson, object : TypeReference<List<String>>() {})
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }
    val regions = try {
        objectMapper.readValue(regionsJson, object : TypeReference<List<String>>() {})
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }
    // I2a-1/I2a-2: email_domains_json 是唯一事实源；解析失败按不限（空集合）处理。
    val emailDomains = try {
        objectMapper.readValue(emailDomainsJson, object : TypeReference<List<String>>() {})
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }
    // I3a-1/I3a-3: operator_statuses_json 是唯一事实源；解析失败按不限（空集合）处理。
    val operatorStatuses = try {
        objectMapper.readValue(operatorStatusesJson, object : TypeReference<List<String>>() {})
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }
    // I2-4: expert_types_json 是唯一事实源；解析失败按不限（空集合）处理。
    val expertTypes = try {
        objectMapper.readValue(expertTypesJson, object : TypeReference<List<String>>() {})
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }
    // I-1: sender_account_codes_json 是唯一事实源，且是唯一**严格**解析的范围字段 ——
    // 坏 JSON 拒绝启动/读取，绝不降级为 []（否则白名单会被静默放宽成全池）。
    val senderAccountCodes = parseSenderAccountCodes(objectMapper, senderAccountCodesJson)
    return BatchExecutionSnapshot(
        mailType = mailType,
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
        expertTypes = expertTypes,
        senderAccountCodes = senderAccountCodes,
        templateId = templateId,
        gateFilterEnabled = gateFilterEnabled,
        researchDirectionFilter = researchDirectionFilter,
        oneRoundOnly = oneRoundOnly
    )
}

/**
 * I-1: `batch_send_task_config.sender_account_codes_json` 的唯一解析点。
 *
 * 与其它旧范围字段（tags/regions/emailDomains/operatorStatuses/expertTypes）的
 * 「解析失败按不限」相反：这里的坏 JSON 必须拒绝读取/启动，绝不静默降级成 `[]` ——
 * 降级会把「只从选中账号发件」悄悄放宽成全池。空/缺失文本 = `[]`（不限，旧行同义）。
 * 返回值为 trim、丢空、去重保序后的逻辑 `account_code` 列表。
 */
fun parseSenderAccountCodes(objectMapper: ObjectMapper, json: String?): List<String> {
    val text = json?.trim().orEmpty()
    if (text.isEmpty()) return emptyList()
    val node = try {
        objectMapper.readTree(text)
    } catch (e: Exception) {
        throw IllegalStateException("sender_account_codes_json is not valid JSON: $text", e)
    }
    if (node == null || !node.isArray) {
        throw IllegalStateException("sender_account_codes_json must be a JSON array: $text")
    }
    return node.map { element ->
        if (!element.isTextual) {
            throw IllegalStateException("sender_account_codes_json must contain only strings: $text")
        }
        element.asText()
    }.map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}
