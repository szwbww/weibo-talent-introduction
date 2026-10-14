package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.EmailVerificationAllowedStates
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.campaign.repository.BatchRecipientReadRepository
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * I-1/I-5（02）：发送保护原因 key。与显式条件（[com.weibo.talentintroduction.campaign.domain.RecipientFilterKeys]）
 * 和准入状态（[RecipientAdmissionReasonKeys]）分开表达 —— 保护原因描述「已发送/已绑定/已抑制/永久失败」
 * 以及地址可用性事实，不冒充准入结论，也不放宽任何保护。
 */
object RecipientProtectionReasonKeys {
    /**
     * repair V-1：母集成员没有可用收件地址（`email` 缺失/空白）—— 确定性地址排除。
     * 母集只按层级/标签读取，这类成员必须留在母集里并给出显式原因，绝不静默丢出诊断。
     */
    const val NO_RECIPIENT_ADDRESS = "PROTECTION_NO_RECIPIENT_ADDRESS"

    const val ALREADY_SENT = "PROTECTION_ALREADY_SENT"
    const val BOUND_SENDER = "PROTECTION_BOUND_SENDER"
    const val SUPPRESSED = "PROTECTION_SUPPRESSED"
    const val PERMANENT_FAILURE = "PROTECTION_PERMANENT_FAILURE"

    /** 联系人既有 `operator_status='EMAIL_INVALID'` 地址保护。 */
    const val EMAIL_INVALID = "PROTECTION_EMAIL_INVALID"
}

/** I-6/I-3（02）：模板门禁与邮箱历史解释专用的原因 key。 */
object RecipientPreflightReasonKeys {
    /** 模板门禁开启时命中「专家必填变量缺失」。 */
    const val TEMPLATE_MISSING = "TEMPLATE_MISSING"

    /** 当前开关/放行集合下，可复用邮箱历史结论明确拒绝该目标。 */
    const val EMAIL_VERIFICATION_REJECTED = "EMAIL_VERIFICATION_REJECTED"
}

/**
 * I-3（MAIN M-3）：单个目标在当前开关下的邮箱验证相位。只描述本次解释，不新增持久状态。
 */
enum class RecipientVerificationPhase {
    /** 实时验证与历史过滤均关闭 —— 不读邮箱历史、不调用供应商。 */
    DISABLED,

    /** 实时验证开启且有可复用结论，且该结论在当前放行集合内。 */
    CACHED_ALLOWED,

    /** 有明确结论（含历史 undeliverable 或不在当前放行集合内）明确拒绝。 */
    CACHED_REJECTED,

    /** 实时验证开启但无有效可复用结论 —— 需在执行时真实验证。 */
    AWAITING_VERIFICATION
}

/** I-5：原因样本（只含稳定标识，不含正文/PII 之外的字段）。 */
data class RecipientReasonSample(
    val docId: String,
    val orcidId: String
)

/**
 * I-1/I-5：单个原因的命中数与**上限 20**的样本；`truncated` 明确标记样本被截断，
 * `count` 始终是完整命中数（绝不等于样本数）。
 */
data class RecipientReasonHit(
    val reasonKey: String,
    val count: Int,
    val samples: List<RecipientReasonSample>,
    val truncated: Boolean
)

/** 一个去重目标（真实 docId）的完整只读判定。 */
data class RecipientPreflightDecision(
    val docId: String,
    val orcidId: String,
    val admissionState: String,
    val included: Boolean,
    /** I-1/MAIN M-3：准入未通过时唯一贡献的准入原因 key（条件未通过时为空，口径同 selector）。 */
    val admissionReasonKeys: List<String>,
    val conditionReasonKeys: List<String>,
    val protectionReasonKeys: List<String>,
    val templateMissingKeys: List<String>,
    val verificationPhase: RecipientVerificationPhase
) {
    val reasonKeys: List<String>
        get() = admissionReasonKeys + conditionReasonKeys + protectionReasonKeys +
            templateMissingKeys.map { RecipientPreflightReasonKeys.TEMPLATE_MISSING } +
            if (verificationPhase == RecipientVerificationPhase.CACHED_REJECTED) {
                listOf(RecipientPreflightReasonKeys.EMAIL_VERIFICATION_REJECTED)
            } else {
                emptyList()
            }

    /**
     * I-1/MAIN M-3：准入事实必须折进资格判定 —— 未获准入的母集成员即使显式条件全通过
     * 也**不是**通过目标（否则会被计入 `eligibleBeforeLiveVerification` 而漏出 `excludedUnique`）。
     */
    val eligible: Boolean get() = included && reasonKeys.isEmpty()
}

/** I-1/I-5：一次只读预检查的输入 —— 母集（仅层级/标签）与本次显式条件分开传入。 */
data class BatchRecipientPreflightInput(
    /** 本次显式条件（地区/状态/方向/类型/模板门禁等），只用于逐人原因，不用于缩小母集。 */
    val scope: RecipientScope,
    /** 母集候选（仅所选漏斗层级与标签），按真实 docId 去重。 */
    val profiles: List<ExpertProfile>,
    val gateFilterEnabled: Boolean,
    val templateId: Long?,
    val emailVerificationEnabled: Boolean,
    val emailVerificationAllowedStates: List<String>?,
    val excludeVerifiedUnavailableEmails: Boolean,
    val asOf: LocalDateTime,
    /** 母集读取是否不完整（超限/取消/部分页失败）——true 时不得套用完整总数承诺。 */
    val partial: Boolean = false
)

/**
 * I-1/I-2/I-3（MAIN M-2/M-3）：只读预检查结果。
 *
 * `baseline = excludedUnique + eligibleBeforeLiveVerification`；
 * `awaitingVerification` 是 `eligibleBeforeLiveVerification` 的子集（不另加总）；
 * `reasonHits` 可重叠，绝不相加冒充总排除人数。
 */
data class BatchRecipientPreflightResult(
    val baseline: Int,
    val excludedUnique: Int,
    val eligibleBeforeLiveVerification: Int,
    val awaitingVerification: Int,
    val reasonHits: List<RecipientReasonHit>,
    val asOf: LocalDateTime,
    val partial: Boolean,
    val verificationPhase: RecipientVerificationPhase,
    val decisions: List<RecipientPreflightDecision>
)

/**
 * I-1/I-2/I-3/I-4/I-5/I-6（02）：批量目标的**只读**统一预检查。
 *
 * - 母集只应用所选漏斗层级与标签（由调用方在 ES 侧提供），本服务对**完整母集**逐人给出独立原因；
 *   不做地区/状态/研究方向/类型/模板的提前缩小，也不按 docId 以外的方式归并。
 * - 准入事实经 [BatchRecipientSelectionService]（唯一 selector）消费；显式条件不匹配**不再**把
 *   已准入目标标成 `NOT_DISCOVERY`（[RecipientPreflightDecision.admissionState] 保留真实准入结论）。
 * - 只读：只 SELECT / 读历史结论；不创建 campaign/contact/task/Pending 验证/发送尝试/准入，
 *   不调用 `verify()`、SMTP 或供应商。
 */
@Service
class BatchRecipientPreflightService(
    private val selectionService: BatchRecipientSelectionService,
    private val emailVerificationService: BatchEmailVerificationService,
    private val readRepository: BatchRecipientReadRepository,
    private val introductionMailComposer: IntroductionMailComposer
) {

    fun preflight(input: BatchRecipientPreflightInput): BatchRecipientPreflightResult {
        val distinct = dedupeByDocId(input.profiles)
        val selection = if (distinct.isEmpty()) {
            RecipientSelection.EMPTY
        } else {
            selectionService.select(input.scope, distinct)
        }
        val decisionByDoc = (selection.included + selection.excluded).associateBy { it.docId }

        val orcids = distinct.map { normalizeOrcid(it) }.filter { it.isNotEmpty() }.distinct()
        val boundOrcids = readRepository.findBoundOrcids(orcids)
        val emailInvalidOrcids = readRepository.findEmailInvalidOrcids(orcids)
        val sendFacts = readRepository.findIntroductionSendFacts(orcids)
        val suppressedEmails = readRepository.findSuppressedEmails(distinct.map { it.email })

        val verification = resolveVerification(input, distinct)

        val decisions = ArrayList<RecipientPreflightDecision>(distinct.size)
        for (profile in distinct) {
            val docId = BatchRecipientSelectionService.docIdOf(profile)
            val selected = decisionByDoc[docId]
            val conditionKeys = selected?.filterKeys.orEmpty()
            val admissionKeys = admissionReasonKeys(selected)
            val orcid = normalizeOrcid(profile)
            // repair V-1：地址可用性与抑制判据共用同一份规范化结果（没有地址就不可能被抑制，也不会进入发送验证）。
            val normalizedEmail = normalizeVerificationEmail(profile.email)
            val protectionKeys = buildList {
                if (normalizedEmail.isEmpty()) add(RecipientProtectionReasonKeys.NO_RECIPIENT_ADDRESS)
                if (orcid in boundOrcids) add(RecipientProtectionReasonKeys.BOUND_SENDER)
                if (orcid in emailInvalidOrcids) add(RecipientProtectionReasonKeys.EMAIL_INVALID)
                val fact = sendFacts[orcid]
                if (fact?.sent == true) add(RecipientProtectionReasonKeys.ALREADY_SENT)
                if (fact?.permanentFailure == true) add(RecipientProtectionReasonKeys.PERMANENT_FAILURE)
                if (normalizedEmail.isNotEmpty() && normalizedEmail in suppressedEmails) {
                    add(RecipientProtectionReasonKeys.SUPPRESSED)
                }
            }
            val templateKeys = if (input.gateFilterEnabled) {
                expertMissingTemplateKeys(profile, input.templateId)
            } else {
                emptyList()
            }
            decisions += RecipientPreflightDecision(
                docId = docId,
                orcidId = profile.orcidId,
                admissionState = (selected?.admissionState ?: RecipientAdmissionState.UNINITIALIZED).name,
                included = selected?.included == true,
                admissionReasonKeys = admissionKeys,
                conditionReasonKeys = conditionKeys,
                protectionReasonKeys = protectionKeys,
                templateMissingKeys = templateKeys,
                verificationPhase = verification.phaseFor(normalizedEmail)
            )
        }

        val baseline = distinct.size
        val excludedUnique = decisions.count { !it.eligible }
        val eligible = decisions.count { it.eligible }
        val awaiting = decisions.count {
            it.eligible && it.verificationPhase == RecipientVerificationPhase.AWAITING_VERIFICATION
        }
        val reasonHits = aggregateReasonHits(decisions)
        return BatchRecipientPreflightResult(
            baseline = baseline,
            excludedUnique = excludedUnique,
            eligibleBeforeLiveVerification = eligible,
            awaitingVerification = awaiting,
            reasonHits = reasonHits,
            asOf = input.asOf,
            partial = input.partial,
            verificationPhase = aggregateVerificationPhase(input, decisions),
            decisions = decisions
        )
    }

    /**
     * I-6：模板门禁的**唯一**逐人判定 —— 复用 [IntroductionMailComposer.evaluateForBatch] 与
     * `effectiveRequiredKeys`，只把**专家变量**缺项算作原因；账号变量缺项是启动前配置错误，
     * 不逐人筛掉专家。模板不存在/不可读时由 composer 原样抛出（按任务配置错误处理）。
     */
    fun expertMissingTemplateKeys(profile: ExpertProfile, templateId: Long?): List<String> {
        val evaluation = introductionMailComposer.evaluateForBatch(null, profile, templateId)
        return evaluation.missingKeys.filter { it !in MailComposeTemplateService.ACCOUNT_VARIABLE_KEYS }
    }

    /** I-6：门禁关闭时为 no-op；开启时按真实模板内容逐人判定（与发送期 compose 同源）。 */
    fun templateGateExcludes(profile: ExpertProfile, templateId: Long?): Boolean =
        expertMissingTemplateKeys(profile, templateId).isNotEmpty()

    private fun admissionReasonKeys(selected: RecipientDecision?): List<String> {
        if (selected == null) return listOf(RecipientAdmissionReasonKeys.UNINITIALIZED)
        if (selected.filterKeys.isNotEmpty()) return emptyList()
        if (selected.included) return emptyList()
        return BatchRecipientSelectionService.reasonKeysOf(selected)
    }

    private fun aggregateReasonHits(decisions: List<RecipientPreflightDecision>): List<RecipientReasonHit> {
        val counts = LinkedHashMap<String, Int>()
        val samples = LinkedHashMap<String, MutableList<RecipientReasonSample>>()
        decisions.forEach { decision ->
            decision.reasonKeys.distinct().forEach { key ->
                counts.merge(key, 1, Int::plus)
                val bucket = samples.getOrPut(key) { mutableListOf() }
                if (bucket.size < MAX_REASON_SAMPLES) {
                    bucket += RecipientReasonSample(decision.docId, decision.orcidId)
                }
            }
        }
        return counts.map { (key, count) ->
            val bucket = samples[key].orEmpty()
            RecipientReasonHit(
                reasonKey = key,
                count = count,
                samples = bucket,
                truncated = count > bucket.size
            )
        }
    }

    /**
     * 顶层相位表达本次整体态势：关闭 = DISABLED；开启且仍有待验证目标 = AWAITING_VERIFICATION；
     * 否则 = CACHED_ALLOWED。逐人精确相位（含 CACHED_REJECTED）见各 decision。
     */
    private fun aggregateVerificationPhase(
        input: BatchRecipientPreflightInput,
        decisions: List<RecipientPreflightDecision>
    ): RecipientVerificationPhase = when {
        !input.emailVerificationEnabled -> RecipientVerificationPhase.DISABLED
        decisions.any { it.verificationPhase == RecipientVerificationPhase.AWAITING_VERIFICATION } ->
            RecipientVerificationPhase.AWAITING_VERIFICATION
        else -> RecipientVerificationPhase.CACHED_ALLOWED
    }

    /**
     * I-3/I-4：两开关组合。
     * - 实时验证 OFF 且历史过滤 OFF：**完全不读**邮箱历史，返回全 DISABLED 视图（不触碰仓储）。
     * - 实时验证 OFF、历史过滤 ON：只读有效最新 undeliverable（沿用既有仓储有效期/排序），命中即拒绝。
     * - 实时验证 ON：按当前 `allowedStates` 解释可复用结论；无结论 = AWAITING_VERIFICATION。
     */
    private fun resolveVerification(
        input: BatchRecipientPreflightInput,
        profiles: List<ExpertProfile>
    ): VerificationView {
        if (!input.emailVerificationEnabled && !input.excludeVerifiedUnavailableEmails) {
            return VerificationView.disabled()
        }
        val emails = profiles.map { it.email }
        if (!input.emailVerificationEnabled) {
            val undeliverable = emailVerificationService.findKnownUndeliverableEmails(emails, input.asOf)
            return VerificationView.historicalOnly(undeliverable)
        }
        val allowed = EmailVerificationAllowedStates.allowedFor(input.emailVerificationAllowedStates)
        val reusable = emailVerificationService.readReusableProviderStates(emails, input.asOf)
        return VerificationView.live(reusable, allowed)
    }

    private fun dedupeByDocId(profiles: List<ExpertProfile>): List<ExpertProfile> {
        val seen = HashSet<String>(profiles.size)
        val out = ArrayList<ExpertProfile>(profiles.size)
        for (profile in profiles) {
            if (seen.add(BatchRecipientSelectionService.docIdOf(profile))) out += profile
        }
        return out
    }

    private fun normalizeOrcid(profile: ExpertProfile): String =
        ExpertIdNormalizer.normalize(profile.orcidId).trim()

    /** 邮箱相位解释：与供应商状态名称共用现有常量，不发明第二套取值。 */
    private class VerificationView(
        private val disabled: Boolean,
        private val undeliverable: Set<String>,
        private val reusable: Map<String, String>,
        private val allowed: Set<String>
    ) {
        fun phaseFor(normalizedEmail: String): RecipientVerificationPhase {
            if (disabled) {
                return if (normalizedEmail.isNotEmpty() && normalizedEmail in undeliverable) {
                    RecipientVerificationPhase.CACHED_REJECTED
                } else {
                    RecipientVerificationPhase.DISABLED
                }
            }
            if (normalizedEmail.isEmpty()) return RecipientVerificationPhase.DISABLED
            val state = reusable[normalizedEmail] ?: return RecipientVerificationPhase.AWAITING_VERIFICATION
            return when {
                state == BatchEmailVerificationService.STATE_UNDELIVERABLE -> RecipientVerificationPhase.CACHED_REJECTED
                state in allowed -> RecipientVerificationPhase.CACHED_ALLOWED
                else -> RecipientVerificationPhase.CACHED_REJECTED
            }
        }

        companion object {
            fun disabled(): VerificationView =
                VerificationView(disabled = true, undeliverable = emptySet(), reusable = emptyMap(), allowed = emptySet())

            fun historicalOnly(undeliverable: Set<String>): VerificationView =
                VerificationView(disabled = true, undeliverable = undeliverable, reusable = emptyMap(), allowed = emptySet())

            fun live(reusable: Map<String, String>, allowed: Set<String>): VerificationView =
                VerificationView(disabled = false, undeliverable = emptySet(), reusable = reusable, allowed = allowed)
        }
    }

    companion object {
        /** I-5：每个原因保留的样本上限。 */
        const val MAX_REASON_SAMPLES = 20

        /** I-5：单次按 docId 诊断的 docId 上限。 */
        const val MAX_DIAGNOSTIC_DOC_IDS = 200
    }
}

/** I-5（02）：按 docId 诊断的单条结果状态。 */
object BatchRecipientDiagnosticStatus {
    /** 在本次母集内，且已给出完整只读判定。 */
    const val OK = "OK"

    /** 该 docId 存在，但不在本次所选层级/标签母集内。 */
    const val OUTSIDE_BASELINE = "OUTSIDE_BASELINE"

    /** 该 docId 在任何层级都找不到 —— 不静默吞掉。 */
    const val NOT_FOUND = "NOT_FOUND"
}

/** I-5（02）：按 docId 的只读解释行；非母集成员只带状态，不伪造原因。 */
data class BatchRecipientDiagnosticEntry(
    val docId: String,
    val status: String,
    val orcidId: String? = null,
    val admissionState: String? = null,
    val eligible: Boolean? = null,
    val admissionReasonKeys: List<String> = emptyList(),
    val conditionReasonKeys: List<String> = emptyList(),
    val protectionReasonKeys: List<String> = emptyList(),
    val templateMissingKeys: List<String> = emptyList(),
    val verificationPhase: String? = null
)

/**
 * I-1/I-4/I-5（MAIN M-3）：`POST /api/mail/batch-send/recipient-diagnostics` 的只读响应 ——
 * 完整母集统计 + 限量样本 + 逐 docId 解释。`reasonHits` 可重叠，绝不相加当作总排除人数。
 */
data class BatchRecipientDiagnosticsResponse(
    val baseline: Int,
    val excludedUnique: Int,
    val eligibleBeforeLiveVerification: Int,
    val awaitingVerification: Int,
    val reasonHits: List<RecipientReasonHit>,
    val asOf: LocalDateTime,
    val partial: Boolean,
    val verificationPhase: String,
    val recipients: List<BatchRecipientDiagnosticEntry>
)
