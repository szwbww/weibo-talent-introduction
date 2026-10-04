package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.RecipientFilterKeys
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewDecision
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewIdentity
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewAdmissionKey
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewService
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.springframework.stereotype.Service

/**
 * I-1: 逐人准入状态。发现/待确认档案消费 04 的持久结论；非发现档案保留原准入入口
 * （不在本选择器重判），两者都不再调用基础资格/机构/国家/凭证验签做二次拒绝。
 *
 * `UNINITIALIZED`/`IDENTITY_CHANGED`/`MANUAL_REJECTED` 是明确展示的准入状态，
 * **不是**发送失败，也不混入发送失败计数。
 */
enum class RecipientAdmissionState {
    NOT_DISCOVERY,
    ADMITTED,
    NEEDS_REVIEW,
    MANUAL_REJECTED,
    UNINITIALIZED,
    IDENTITY_CHANGED
}

/**
 * I-3: 一名去重后的目标（真实 docId）。`filterKeys` 是显式条件不匹配项，
 * `admissionState` 是持久准入结论；`included` 表示是否进入本次目标名单。
 */
data class RecipientDecision(
    val docId: String,
    val orcidId: String,
    val admissionState: RecipientAdmissionState,
    val admissionDecision: String?,
    val syncErrorCode: String?,
    val filterKeys: List<String>,
    val included: Boolean
)

/**
 * I-3: 统一 selector 的产物。`reasonHits` 是「原因命中」计数（同一人多个原因各计一次）；
 * `excluded` 已按真实 docId 去重（总排除人数按人去重）。`includedDocIds` 供调用方过滤自身列表。
 */
data class RecipientSelection(
    val included: List<RecipientDecision>,
    val excluded: List<RecipientDecision>,
    val reasonHits: Map<String, Int>
) {
    val includedDocIds: Set<String> get() = included.map { it.docId }.toSet()

    companion object {
        val EMPTY = RecipientSelection(emptyList(), emptyList(), emptyMap())
    }
}

/**
 * I-2/I-3: 准入状态对应的原因 key（总排除按人去重后每个排除人计一个状态原因）。
 */
object RecipientAdmissionReasonKeys {
    const val UNINITIALIZED = "ADMISSION_UNINITIALIZED"
    const val NEEDS_REVIEW = "ADMISSION_NEEDS_REVIEW"
    const val IDENTITY_CHANGED = "ADMISSION_IDENTITY_CHANGED"
    const val MANUAL_REJECTED = "ADMISSION_REJECTED"
    const val SYNC_FAILED = "ADMISSION_SYNC_FAILED"
}

/**
 * I-1/I-3: 批量目标的**唯一**选择与解释入口。
 *
 * - 逐人去重（真实 docId = `esDocId ?: orcidId`），同一人跨候选/有效层只算一次；
 * - 显式条件全部来自 [RecipientScope]（空集合语义逐条见其 KDoc），无隐式默认；
 * - 发现/待确认档案按 500 一批经 [DiscoveryReviewService.resolveAdmissionBatch] 读取持久准入，
 *   不自行 SQL、不调用基础资格/机构/国家/凭证验签；
 * - 纯计算：不写库、不发 HTTP、不创建 campaign/contact。
 *
 * 预估与执行（含 NEW 重试）必须共用本方法，不得再做二次近似过滤。
 */
@Service
class BatchRecipientSelectionService(
    /**
     * 04 的批量准入读取。Spring 注入真实 bean；单元测试可省略 —— 省略时所有发现/待确认档案
     * 视为 [RecipientAdmissionState.UNINITIALIZED]（fail-closed，绝不静默放行）。生产恒注入。
     */
    private val discoveryReviewService: DiscoveryReviewService? = null
) {
    /** 04 冻结的批量读取上限：每批最多 500 个 docId。 */
    private val admissionBatchSize = ADMISSION_BATCH_SIZE

    fun select(scope: RecipientScope, profiles: List<ExpertProfile>): RecipientSelection {
        if (profiles.isEmpty()) return RecipientSelection.EMPTY

        // 1. 按真实 docId 去重（保序）。
        val seen = HashSet<String>(profiles.size)
        val distinct = ArrayList<ExpertProfile>(profiles.size)
        for (profile in profiles) {
            if (seen.add(docIdOf(profile))) distinct.add(profile)
        }

        // 2. 显式条件：逐人收集不匹配 key。
        val conditionKeys = HashMap<String, List<String>>(distinct.size)
        val conditionPassed = ArrayList<ExpertProfile>(distinct.size)
        for (profile in distinct) {
            val keys = scope.mismatchKeys(profile)
            if (keys.isEmpty()) conditionPassed.add(profile) else conditionKeys[docIdOf(profile)] = keys
        }

        // 3. 持久准入：只对发现/待确认档案，按 500 一批读取。
        val discoveryProfiles = conditionPassed.filter { RecipientScope.isDiscoveryOutreach(it) }
        val admissions = resolveAdmissions(discoveryProfiles)

        // 4. 汇总逐人结论。
        val included = ArrayList<RecipientDecision>()
        val excluded = ArrayList<RecipientDecision>()
        val reasonHits = LinkedHashMap<String, Int>()

        for (profile in distinct) {
            val docId = docIdOf(profile)
            val keys = conditionKeys[docId]
            if (keys != null) {
                keys.forEach { reasonHits.merge(it, 1, Int::plus) }
                excluded += RecipientDecision(docId, profile.orcidId, RecipientAdmissionState.NOT_DISCOVERY, null, null, keys, false)
                continue
            }
            if (!RecipientScope.isDiscoveryOutreach(profile)) {
                included += RecipientDecision(docId, profile.orcidId, RecipientAdmissionState.NOT_DISCOVERY, null, null, emptyList(), true)
                continue
            }
            val resolved = admissions[docId]
            val state = admissionStateOf(resolved)
            val admitted = state == RecipientAdmissionState.ADMITTED
            val decision = RecipientDecision(
                docId = docId,
                orcidId = profile.orcidId,
                admissionState = state,
                admissionDecision = resolved?.decision,
                syncErrorCode = resolved?.syncErrorCode,
                filterKeys = emptyList(),
                included = admitted
            )
            if (admitted) {
                included += decision
            } else {
                reasonHits.merge(admissionReasonKey(state), 1, Int::plus)
                excluded += decision
            }
        }

        return RecipientSelection(included, excluded, reasonHits)
    }

    /**
     * I-3: 只判准入（无本次页面条件的 scope），供旧首发等无页面快照的入口复用同一持久结论。
     * 这里不发信类型，故不追加研发类型判定（类型由旧首发的查询与内存门禁另行负责）。
     */
    fun selectAdmission(profiles: List<ExpertProfile>): RecipientSelection =
        select(RecipientScope(mailType = ADMISSION_ONLY_MAIL_TYPE, funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(), emailDomains = emptyList(), discipline = null), profiles)

    private fun resolveAdmissions(
        profiles: List<ExpertProfile>
    ): Map<String, com.weibo.talentintroduction.discovery.service.DiscoveryReviewResolvedAdmission> {
        if (profiles.isEmpty()) return emptyMap()
        val review = discoveryReviewService ?: return emptyMap()
        val out = HashMap<String, com.weibo.talentintroduction.discovery.service.DiscoveryReviewResolvedAdmission>(profiles.size)
        profiles.chunked(admissionBatchSize).forEach { chunk ->
            val keys = chunk.map { profile ->
                val docId = docIdOf(profile)
                DiscoveryReviewAdmissionKey(
                    docId,
                    DiscoveryReviewIdentity.hash(docId, profile.email, profile.givenNames, profile.familyNames)
                )
            }
            out.putAll(review.resolveAdmissionBatch(keys))
        }
        return out
    }

    private fun admissionStateOf(
        resolved: com.weibo.talentintroduction.discovery.service.DiscoveryReviewResolvedAdmission?
    ): RecipientAdmissionState {
        if (resolved == null) return RecipientAdmissionState.UNINITIALIZED
        if (resolved.identityChanged) return RecipientAdmissionState.IDENTITY_CHANGED
        val decision = resolved.decision ?: return RecipientAdmissionState.UNINITIALIZED
        return when {
            resolved.admitted -> RecipientAdmissionState.ADMITTED
            decision == DiscoveryReviewDecision.NEEDS_REVIEW.name -> RecipientAdmissionState.NEEDS_REVIEW
            decision == DiscoveryReviewDecision.HOLD.name || decision == DiscoveryReviewDecision.REJECTED.name ->
                RecipientAdmissionState.MANUAL_REJECTED
            else -> RecipientAdmissionState.NEEDS_REVIEW
        }
    }

    private fun admissionReasonKey(state: RecipientAdmissionState): String = when (state) {
        RecipientAdmissionState.UNINITIALIZED -> RecipientAdmissionReasonKeys.UNINITIALIZED
        RecipientAdmissionState.IDENTITY_CHANGED -> RecipientAdmissionReasonKeys.IDENTITY_CHANGED
        RecipientAdmissionState.MANUAL_REJECTED -> RecipientAdmissionReasonKeys.MANUAL_REJECTED
        else -> RecipientAdmissionReasonKeys.NEEDS_REVIEW
    }

    companion object {
        /** 04（I-1）：批量读取准入的上限，与 [DiscoveryReviewService.resolveAdmissionBatch] 契约一致。 */
        const val ADMISSION_BATCH_SIZE = 500

        fun docIdOf(profile: ExpertProfile): String = profile.esDocId ?: profile.orcidId

        /** 只判准入、不判研发类型的哨兵 mailType（不是任何真实发信类型）。 */
        private const val ADMISSION_ONLY_MAIL_TYPE = "ADMISSION_ONLY"
    }
}
