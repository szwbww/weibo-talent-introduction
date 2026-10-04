package com.weibo.talentintroduction.campaign.domain

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.service.BatchSendType
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import java.time.LocalDateTime

/**
 * I-1: Emailable 放行结果白名单的**唯一**允许值集合与严格校验（快照与任务配置共用）。
 *
 * `null` 只代表旧请求缺字段 —— 维持 deliverable/risky/unknown 三态全放行；非 null 数组可以为空
 * （明确不放行任何明确结果），但每个元素必须逐字等于三个允许值之一。`undeliverable`、未知值、
 * 大小写/空白变体与非字符串元素一律拒绝，绝不静默降级成「不限」或把非法值当成放行。
 */
object EmailVerificationAllowedStates {
    const val DELIVERABLE = "deliverable"
    const val RISKY = "risky"
    const val UNKNOWN = "unknown"

    /** 规范化顺序固定为 deliverable, risky, unknown。 */
    val ALLOWED: List<String> = listOf(DELIVERABLE, RISKY, UNKNOWN)

    private val ALLOWED_SET: Set<String> = ALLOWED.toSet()

    /** 严格校验（含 null 元素）；非法值抛 [IllegalArgumentException]。 */
    fun requireValid(states: List<String>?) {
        states?.forEach { state ->
            require(state in ALLOWED_SET) {
                "emailVerificationAllowedStates 含非法值：$state（仅允许 $ALLOWED）"
            }
        }
    }

    /** 校验后去重并按固定顺序规范化；`null` 原样返回。 */
    fun normalize(states: List<String>?): List<String>? {
        if (states == null) return null
        requireValid(states)
        return ALLOWED.filter { it in states }
    }

    /** 本次放行的状态集合；`null` = 三态全放行。 */
    fun allowedFor(states: List<String>?): Set<String> {
        requireValid(states)
        return states?.toSet() ?: ALLOWED_SET
    }
}

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
    val emailVerificationEnabled: Boolean = false,
    /**
     * I-1/I-2: 本次执行唯一的 Emailable 放行白名单快照。`null` = 旧请求缺字段（三态全放行）；
     * `[]` = 明确不放行任何明确结果；非空元素必须逐字属于 [EmailVerificationAllowedStates.ALLOWED]。
     * 启动前严格校验（含 JSON 绑定）并固定进 `task_execution.request_payload`，本次执行不再读取可变配置。
     */
    val emailVerificationAllowedStates: List<String>? = null,
    /** Historical verified-undeliverable filtering is independent of live verification. */
    val excludeVerifiedUnavailableEmails: Boolean = false
) {
    init {
        // I-1：直接构造与 JSON 绑定都在启动前拒绝非法白名单，绝不带着未校验策略开跑。
        EmailVerificationAllowedStates.requireValid(emailVerificationAllowedStates)
    }
}

data class ManualBatchExecutionRequest(
    val sourceConfigId: Long? = null,
    val sourceUpdatedAt: LocalDateTime? = null,
    val snapshot: BatchExecutionSnapshot,
    /**
     * A3 (I-4)：预估时冻结的模板版本令牌（[com.weibo.talentintroduction.campaign.service.PendingOutreachSummary.template]
     * 里的 `versionToken`）。执行开始时与同一模板的**当前**令牌比对，不一致即拒绝使用过期预估；
     * null = 旧调用 / 定时路径无预览，不校验。随请求写入 `task_execution.request_payload`。
     */
    val previewTemplateToken: String? = null
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

/**
 * I-2/I-3: 本次批量筛选的显式条件 key。每个 key 精确对应一个用户可见控件，
 * 排除原因必须落在这里列出的 key（或统一 selector 的准入状态）之一，不得有隐式默认。
 */
object RecipientFilterKeys {
    const val OPERATOR_STATUS = "OPERATOR_STATUS"
    const val EXPERT_TYPE = "EXPERT_TYPE"
    const val DISCIPLINE = "DISCIPLINE"
    const val EMAIL_DOMAIN = "EMAIL_DOMAIN"
    const val RESEARCH_DIRECTION = "RESEARCH_DIRECTION"
    const val TAGS = "TAGS"
    const val REGION = "REGION"
    const val GATE_FIELD = "GATE_FIELD"
}

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
    val researchDirectionFilter: String = ResearchDirectionFilters.ANY,
    val excludeVerifiedUnavailableEmails: Boolean = false
) {
    /**
     * I-2/I-3: 本次显式条件的不匹配 key 列表（空列表 = 全部命中）。**不含**发现准入判定 ——
     * 准入由 04 的持久结论经 [com.weibo.talentintroduction.campaign.service.BatchRecipientSelectionService]
     * 统一消费；同身份批准后不再调用机构/国家/凭证验签来二次拒绝。
     *
     * 同一记录多个条件不满足时每个 key 各计一次「原因命中」；调用方按人（真实 docId）去重总排除数。
     * 只读取本次列出的显式控件来源，无任何隐式默认（空集合语义见各分支）。
     */
    fun mismatchKeys(profile: ExpertProfile): List<String> {
        val keys = mutableListOf<String>()
        // I3a-5：与 ES 的 operatorStatusesFilter 同口径 —— 多状态取 OR；
        // NOT_CONTACTED = ES 文档无该字段（I3a-1）；空集合不判定（I3a-3）。
        if (operatorStatuses.isNotEmpty()) {
            val matched = operatorStatuses.any {
                if (it == "NOT_CONTACTED") profile.operatorStatus.isNullOrBlank()
                else profile.operatorStatus == it
            }
            if (!matched) keys += RecipientFilterKeys.OPERATOR_STATUS
        }
        // I4-1: INTRODUCTION 的唯一收口点；MATERIAL_REMINDER 不判定（零影响）。
        if (mailType == BatchSendType.INTRODUCTION.name && !matchesExpertType(profile)) {
            keys += RecipientFilterKeys.EXPERT_TYPE
        }
        if (!discipline.isNullOrBlank()) {
            val matched = if (discipline == "UNCLASSIFIED") {
                profile.disciplineCategory.isNullOrBlank()
            } else {
                profile.disciplineCategory == discipline
            }
            if (!matched) keys += RecipientFilterKeys.DISCIPLINE
        }
        // I2a-4: 与 ES 的 emailDomainsFilter 同口径 —— 多域取 OR；空集合不判定（I2a-2）。
        if (emailDomains.isNotEmpty()) {
            val email = profile.email
            if (email.isNullOrBlank() || emailDomains.none { email.endsWith("@$it") }) {
                keys += RecipientFilterKeys.EMAIL_DOMAIN
            }
        }
        // I-2: 方向三态与 ES 的 researchFields 存在性判据同口径。keyword 字段下
        // `fieldPresenceFilter("researchFields")` = `exists AND NOT term ""`，
        // 故只有 null/空串算「无」，纯空格串在 ES 里 exists 且非 term ""，算「有」。
        // I-3: 本判定与 expertTypes / gateEsFields 是独立维度，同时指定即 AND。
        when (researchDirectionFilter) {
            ResearchDirectionFilters.PRESENT ->
                if (profile.researchFields.isNullOrEmpty()) keys += RecipientFilterKeys.RESEARCH_DIRECTION
            ResearchDirectionFilters.ABSENT ->
                if (!profile.researchFields.isNullOrEmpty()) keys += RecipientFilterKeys.RESEARCH_DIRECTION
        }
        if (tags.isNotEmpty()) {
            val expertTags = profile.tags.orEmpty()
            if (tags.none { it in expertTags }) keys += RecipientFilterKeys.TAGS
        }
        if (regions.isNotEmpty() && !matchesRegion(profile)) keys += RecipientFilterKeys.REGION
        if (gateEsFields.isNotEmpty() && !matchesGateFields(profile)) keys += RecipientFilterKeys.GATE_FIELD
        return keys
    }

    /** I-2/I-3: 全部显式条件命中（准入由统一 selector 追加判定）。 */
    fun matchesExpert(profile: ExpertProfile): Boolean = mismatchKeys(profile).isEmpty()

    /**
     * I-1/I-2/I-3: ES 候选页与预估共用的显式条件谓词（执行取页、预估 scroll、发前兜底）。
     * 与 [matchesExpert] 同义 —— 发现准入不在此处，一律由统一 selector 消费持久结论。
     */
    fun matchesEsTarget(profile: ExpertProfile): Boolean = matchesExpert(profile)

    /**
     * I-2: 地区判定与 ES `regionsFilter` 同口径。发现/待确认档案按**已证实机构所在地**
     * `country` 判（空/未映射国家不进任何地区，含 `Other`）；非发现档案按
     * country OR nationality 判（与 ES 粗筛一致，修正重试只判 country 的不一致）。
     */
    private fun matchesRegion(profile: ExpertProfile): Boolean {
        if (isDiscoveryOutreach(profile)) {
            val country = profile.country
            if (country.isNullOrBlank()) return false
            val region = CountryContinentMapping.toRegion(country)
            if (region == CountryContinentMapping.REGION_OTHER) return false
            return region in regions
        }
        return regions.any { region ->
            regionOf(profile.country) == region || regionOf(profile.nationality) == region
        }
    }

    private fun regionOf(value: String?): String? =
        value?.takeIf { it.isNotBlank() }?.let { CountryContinentMapping.toRegion(it) }

    /** I4a-5: 与 ES 的 fieldPresenceFilter 同口径。 */
    private fun matchesGateFields(profile: ExpertProfile): Boolean = gateEsFields.all { field ->
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
        /**
         * I-1: 触发新发现首发门禁的档案标签。与 [DiscoveryIdentity.isDiscovery] 取并集 ——
         * 由 `discovered` 转为 `待确认` 的存量人群没有身份对象，也必须 fail-closed。
         */
        const val DISCOVERY_PENDING_TAG = "待确认"

        /** I-3: ES `filterResult` 的合格值（写入侧同值：`PASSED` / `REJECTED`）。 */
        private const val FILTER_RESULT_PASSED = "PASSED"

        /**
         * I-1: 该档案是否按新发现规则发送 —— 身份凭证（[DiscoveryIdentity.isDiscovery]）
         * 或 `待确认` 标签任一命中。**不改变** [DiscoveryIdentity.isDiscovery] 既有语义。
         */
        fun isDiscoveryOutreach(profile: ExpertProfile): Boolean =
            DiscoveryIdentity.isDiscovery(profile) || profile.tags.orEmpty().contains(DISCOVERY_PENDING_TAG)

        /**
         * I-1/I-2: 新发现首发的唯一最终谓词（ES 候选页、预估、NEW 重试与旧首发共用）。
         *
         * 必须同时成立：身份凭证经 [DiscoveryIdentity.allowed]、`institution` 非空、
         * `country` 能由 [CountryContinentMapping] 映射（空/未映射值不等于 Other）、
         * `institutionEvidence` 经 02 的唯一验签函数重算通过（含来源 ID 一致性）、
         * `filterResult == PASSED`。显式历史人工认可可替代身份及机构来源凭证，
         * 但不替代机构、国家、PASSED 条件。未获认可的缺字段不通过；非新发现档案一律放行。
         */
        fun matchesDiscoveryOutreach(profile: ExpertProfile): Boolean {
            if (!isDiscoveryOutreach(profile)) return true
            val legacyApproved = DiscoveryIdentity.legacyOutreachApproved(profile)
            if (!legacyApproved && !DiscoveryIdentity.allowed(profile)) return false
            if (profile.institution.isNullOrBlank()) return false
            if (profile.country.isNullOrBlank()) return false
            if (CountryContinentMapping.toRegion(profile.country) == CountryContinentMapping.REGION_OTHER) return false
            if (profile.filterResult != FILTER_RESULT_PASSED) return false
            return legacyApproved || DiscoveryIdentity.validInstitutionEvidence(profile)
        }

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
                researchDirectionFilter = ResearchDirectionFilters.normalize(snapshot.researchDirectionFilter),
                excludeVerifiedUnavailableEmails = snapshot.excludeVerifiedUnavailableEmails
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
     * I-1/I-3: 发送前兜底门禁命中 —— 新发现/待确认档案缺身份/机构/可映射国家/机构证据或
     * `filterResult != PASSED`。ES 取页与 NEW 重试已按同一谓词过滤，此码只覆盖绕过查询侧的残余路径。
     */
    const val DISCOVERY_EVIDENCE_MISSING = "DISCOVERY_EVIDENCE_MISSING"
    /**
     * I-3/I-4: 目标专家已有任一 `expert_contact.bound_sender_account_code`（与绑定值是否在
     * 本次选中集合无关）→ 本次批量任务跳过，不发信、不重选号、不改绑。
     */
    const val BOUND_SENDER_ALREADY_SET = "BOUND_SENDER_ALREADY_SET"
    /**
     * I-2/I-6: 发送前验证明确不通过（undeliverable）。
     * 该目标只计 processed/skipped，**不占本轮成功配额**（循环继续扫描补足 roundPassed），
     * 不计 success、不计发送失败、不占账号发送量；明细行的 send_status=SKIPPED 使用同一码。
     */
    const val EMAIL_VERIFICATION_REJECTED = "EMAIL_VERIFICATION_REJECTED"
    /**
     * I-2/I-3: 供应商给出明确结果，但不在本次快照放行集合内（未选中的 deliverable/risky/unknown）。
     * 与 [EMAIL_VERIFICATION_REJECTED] 一样只计 processed/skipped、不占本轮成功配额，但**不**追加
     * 「邮箱异常」标签、不改联系人/账号状态；明细行 `decision=SKIP` + `send_status=SKIPPED` 使用同一码。
     */
    const val EMAIL_VERIFICATION_POLICY_SKIP = "EMAIL_VERIFICATION_POLICY_SKIP"
    const val EMAIL_VERIFICATION_DEFERRED = "EMAIL_VERIFICATION_DEFERRED"

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
        DISCOVERY_EVIDENCE_MISSING to "新发现机构证据不足",
        BOUND_SENDER_ALREADY_SET to "专家已绑定发件账号",
        EMAIL_VERIFICATION_REJECTED to "邮箱验证未通过",
        EMAIL_VERIFICATION_POLICY_SKIP to "邮箱验证策略跳过",
        EMAIL_VERIFICATION_DEFERRED to "邮箱验证暂缓"
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
        oneRoundOnly = oneRoundOnly,
        // I-1/I-4: 配置实体是快照的唯一来源；启动时逐字复制，运行中改配置/软删不改本次快照。
        emailVerificationEnabled = emailVerificationEnabled,
        // I-1/I-3: 本列同样只经唯一解析点读取（旧 SQL NULL → 三态全放行），坏值在此拒绝启动。
        emailVerificationAllowedStates = parseEmailVerificationAllowedStates(
            objectMapper,
            emailVerificationAllowedStatesJson
        ),
        excludeVerifiedUnavailableEmails = excludeVerifiedUnavailableEmails
    )
}

/**
 * I-1: `batch_send_task_config.email_verification_allowed_states_json` 的唯一解析点
 * （配置 View 与启动快照共用）。
 *
 * SQL NULL = 升级前的旧配置，逐字返回三态全放行（[EmailVerificationAllowedStates.ALLOWED]）；
 * 非 NULL 文本必须是合法 JSON 数组且元素逐字属于允许值，`[]` 明确表示不实施发送前验证。
 * JSON null、非数组、非字符串元素、未知值与大小写/空白变体一律拒绝（[IllegalStateException]），
 * 绝不降级成旧 NULL —— 降级会把「明确不放行」静默放宽成三态放行。
 * 返回值为按固定顺序去重后的列表。
 */
fun parseEmailVerificationAllowedStates(objectMapper: ObjectMapper, json: String?): List<String> {
    // 只有 SQL NULL 才是「旧配置」；非 NULL 的空白文本不是合法 JSON 数组，必须拒绝，
    // 绝不能像 sender_account_codes_json 那样把空文本当作未设置（那会把非法数据放行为三态）。
    if (json == null) return EmailVerificationAllowedStates.ALLOWED
    val text = json.trim()
    if (text.isEmpty()) {
        throw IllegalStateException("email_verification_allowed_states_json must not be blank: '$json'")
    }
    val node = try {
        objectMapper.readTree(text)
    } catch (e: Exception) {
        throw IllegalStateException("email_verification_allowed_states_json is not valid JSON: $text", e)
    }
    if (node == null || node.isNull || !node.isArray) {
        throw IllegalStateException("email_verification_allowed_states_json must be a JSON array: $text")
    }
    val states = node.map { element ->
        if (!element.isTextual) {
            throw IllegalStateException("email_verification_allowed_states_json must contain only strings: $text")
        }
        element.asText()
    }
    return try {
        EmailVerificationAllowedStates.normalize(states) ?: EmailVerificationAllowedStates.ALLOWED
    } catch (e: IllegalArgumentException) {
        throw IllegalStateException("email_verification_allowed_states_json contains invalid states: $text", e)
    }
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
