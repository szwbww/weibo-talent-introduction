package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.discovery.domain.AdmissionConfigSnapshot
import com.weibo.talentintroduction.discovery.domain.AdmissionReason
import com.weibo.talentintroduction.discovery.domain.AdmissionReasonCodes
import com.weibo.talentintroduction.discovery.domain.AutomaticAdmissionResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryAdmissionStatus
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.EligibilityResult
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.CandidateEligibilityService
import com.weibo.talentintroduction.expert.service.EligibilityFilterService
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * 01（I-1/I-2/I-3）：新发现/待确认档案的自动准入解释器。
 *
 * 只读：不写 ES、不写库、不签发人工批准、不改任何来源凭证。规则**逐条搬用**现有判定：
 * [CandidateEligibilityService] 当前生效的候选规则（由调用方传入的 [EligibilityResult] 表达，
 * 或在 [evaluate] 单参版本里实时重算）+ [DiscoveryIdentity.allowed] 身份 + 机构非空 +
 * 国家可由 [CountryContinentMapping] 映射 + [DiscoveryIdentity.validInstitutionEvidence] 机构来源凭证。
 * 本子计划不新增任何阈值，[AdmissionConfigSnapshot] 只回显判定时生效的既有配置。
 *
 * I-1（`2026-10-13.1`）：**缺资料不再阻断首发** —— [SOFT_REASON_CODES] 的六类原因逐字保留原码与事实，
 * 只从 `blockingReasons` 移入 `hints`；明确不合格（研发范围/服务性工作/学历/年龄/国籍/指标/邮箱）、
 * 身份不可信、来源 ID 冲突与机构来源凭证 INVALID 继续留在 `blockingReasons`。
 * 提示只陈述缺失事实，绝不标成「数据已补齐」，也不得把明确不合格混为未知。
 *
 * 判定范围与发送门禁同定义（[RecipientScope.isDiscoveryOutreach]：身份凭证或 `待确认` 标签）。
 * 仅带 `待确认` 标签、没有身份对象的档案也必须显式阻断（I-2）——[DiscoveryIdentity.allowed]
 * 对非发现档案恒为 true，不能据此放行。非新发现档案保留原入口：
 * 直接 [DiscoveryAdmissionStatus.AUTO_PASSED]，不产生任何原因。
 *
 * 有效历史人工认可（[DiscoveryIdentity.legacyOutreachApproved]）单独返回
 * [DiscoveryAdmissionStatus.LEGACY_APPROVED]：机构/国家/凭证/资格问题只进 `hints`，
 * 不再强制送回审核；[DiscoveryIdentity.allowed] 的 source-verified 语义不变（I-2）。
 */
@Service
class DiscoveryAdmissionPolicy(
    private val candidateEligibilityService: CandidateEligibilityService,
    private val eligibilityFilterService: EligibilityFilterService,
    private val clock: Clock = Clock.systemUTC(),
    private val verificationGate: DiscoveryVerificationAdmissionGate? = null
) {

    /** 实时重算候选资格后判定（I-1：不把缺少 `PASSED` 当不可自愈的循环条件）。 */
    fun evaluate(profile: ExpertProfile): AutomaticAdmissionResult =
        evaluate(profile, candidateEligibilityService.evaluateEligibility(profile))

    /** 用调用方已有的资格结果判定（同一 profile 的实时结论或调用链上游刚算出的结论）。 */
    fun evaluate(profile: ExpertProfile, eligibility: EligibilityResult): AutomaticAdmissionResult {
        val docId = profile.esDocId?.takeIf { it.isNotBlank() }
        return if (docId != null) evaluate(docId, profile, eligibility)
        else evaluateBase(profile, eligibility)
    }

    fun evaluate(docId: String, profile: ExpertProfile): AutomaticAdmissionResult =
        evaluate(docId, profile, candidateEligibilityService.evaluateEligibility(profile))

    fun evaluate(docId: String, profile: ExpertProfile, context: VerificationAdmissionContext): AutomaticAdmissionResult =
        evaluate(docId, profile, candidateEligibilityService.evaluateEligibility(profile), context)

    fun evaluate(docId: String, profile: ExpertProfile, eligibility: EligibilityResult): AutomaticAdmissionResult =
        evaluate(docId, profile, eligibility, null)

    fun evaluate(
        docId: String, profile: ExpertProfile, eligibility: EligibilityResult,
        context: VerificationAdmissionContext?
    ): AutomaticAdmissionResult {
        require(docId.isNotBlank())
        val base = evaluateBase(profile, eligibility)
        if (verificationGate == null) return base
        val loaded = context ?: verificationGate.findContext(docId, profile)
        require(loaded.docId == docId && loaded.profile == profile) { "Verification context does not match RAW input" }
        return verificationGate.evaluateManaged(base, loaded)
    }

    private fun evaluateBase(profile: ExpertProfile, eligibility: EligibilityResult): AutomaticAdmissionResult {
        val snapshot = configSnapshot()
        val checkedAt = Instant.now(clock)
        if (!RecipientScope.isDiscoveryOutreach(profile)) {
            return AutomaticAdmissionResult(DiscoveryAdmissionStatus.AUTO_PASSED, emptyList(), emptyList(),
                POLICY_VERSION, checkedAt, snapshot)
        }
        val researchHint = researchDirectionHint(profile)
        val identityReasons = identityReasons(profile)
        val institutionReasons = institutionReasons(profile)
        val countryReasons = countryReasons(profile)
        val evidenceReasons = DiscoveryIdentity.explainInstitutionEvidence(profile)
        val eligibilityReasons = eligibility.rejectReasons.map { candidateReason(profile, it) }
        if (DiscoveryIdentity.legacyOutreachApproved(profile)) {
            // I-2：人工认可即准入，事实提示保留但不阻断；不签发也不升级任何凭证。
            val hints = buildList {
                researchHint?.let { add(it) }
                addAll(identityReasons)
                addAll(institutionReasons)
                addAll(countryReasons)
                addAll(evidenceReasons)
                addAll(eligibilityReasons)
            }
            return AutomaticAdmissionResult(DiscoveryAdmissionStatus.LEGACY_APPROVED, emptyList(), hints,
                POLICY_VERSION, checkedAt, snapshot)
        }
        // I-1：六类缺资料原因只进 hints；身份、SOURCE_ID_*、INSTITUTION_EVIDENCE_INVALID 与
        //       明确不合格（含邮箱/研发范围）继续阻断。
        val blockingReasons = buildList {
            addAll(eligibilityReasons.blockingOnly())
            addAll(identityReasons)
            addAll(institutionReasons.blockingOnly())
            addAll(countryReasons.blockingOnly())
            addAll(evidenceReasons.blockingOnly())
        }
        val hints = buildList {
            researchHint?.let { add(it) }
            addAll(eligibilityReasons.softOnly())
            addAll(institutionReasons.softOnly())
            addAll(countryReasons.softOnly())
            addAll(evidenceReasons.softOnly())
        }
        val status = if (blockingReasons.isEmpty()) DiscoveryAdmissionStatus.AUTO_PASSED
        else DiscoveryAdmissionStatus.NEEDS_REVIEW
        return AutomaticAdmissionResult(status, blockingReasons, hints, POLICY_VERSION, checkedAt, snapshot)
    }

    /** I-1：留在 `blockingReasons` 的原因（六类软原因之外的一切）。 */
    private fun List<AdmissionReason>.blockingOnly(): List<AdmissionReason> =
        filter { it.code !in SOFT_REASON_CODES }

    /** I-1：只进 `hints` 的六类缺资料原因；原码与事实逐字保留，只改展示位置。 */
    private fun List<AdmissionReason>.softOnly(): List<AdmissionReason> =
        filter { it.code in SOFT_REASON_CODES }

    /**
     * I-2：身份原因。发现档案（[DiscoveryIdentity.isDiscovery]）逐项解释；
     * 仅带 `待确认` 标签、没有身份对象的存量档案也必须显式阻断 ——
     * [DiscoveryIdentity.allowed] 对非发现档案恒为 true，不能据此放行。
     */
    private fun identityReasons(profile: ExpertProfile): List<AdmissionReason> {
        val explained = DiscoveryIdentity.explainIdentity(profile)
        if (explained.isNotEmpty() || DiscoveryIdentity.isDiscovery(profile)) return explained
        return listOf(AdmissionReason(
            AdmissionReasonCodes.IDENTITY_MISSING,
            "身份凭证缺失（仅有 `待确认` 标签，没有 VERIFIED 身份对象）",
            "identityVerification", null, "VERIFIED 身份凭证", "RecipientScope.DISCOVERY_PENDING_TAG"))
    }

    private fun configSnapshot(): AdmissionConfigSnapshot {
        val candidate = eligibilityFilterService.getCandidateFilter()
        val academic = eligibilityFilterService.getAcademicFilter()
        return AdmissionConfigSnapshot(
            requireOrcid = candidate.requireOrcid,
            requireValidEmail = candidate.requireValidEmail,
            requireDoctoralDegree = candidate.requireDoctoralDegree,
            excludeChineseNationality = candidate.excludeChineseNationality,
            enableAgeFilter = candidate.enableAgeFilter,
            maxAgeExclusive = candidate.maxAgeExclusive,
            enableHIndexFilter = academic.enableHIndexFilter,
            minHIndex = academic.minHIndex,
            enableCitationFilter = academic.enableCitationFilter,
            minCitationCount = academic.minCitationCount,
            enableActivityFilter = academic.enableActivityFilter,
            recentYearsThreshold = academic.recentYearsThreshold
        )
    }

    /** 机构非空；空记为缺项，不推断机构来源或数量（零机构/多机构在存储时已合并为 null）。 */
    private fun institutionReasons(profile: ExpertProfile): List<AdmissionReason> =
        if (!profile.institution.isNullOrBlank()) emptyList()
        else listOf(AdmissionReason(AdmissionReasonCodes.INSTITUTION_MISSING, "机构缺失",
            "institution", profile.institution, "非空机构名", "profile.institution"))

    /** 国家非空且可映射到 [CountryContinentMapping] 的大区；`Other` 表示未映射，不等于通过。 */
    private fun countryReasons(profile: ExpertProfile): List<AdmissionReason> = when {
        profile.country.isNullOrBlank() -> listOf(AdmissionReason(AdmissionReasonCodes.COUNTRY_MISSING,
            "国家缺失", "country", profile.country, "非空国家", "profile.country"))
        CountryContinentMapping.toRegion(profile.country) == CountryContinentMapping.REGION_OTHER ->
            listOf(AdmissionReason(AdmissionReasonCodes.COUNTRY_UNMAPPED, "国家无法映射到大区",
                "country", profile.country, "9 个大区之一（Other 表示未映射）", "CountryContinentMapping.toRegion()"))
        else -> emptyList()
    }

    /**
     * 研究方向缺失只作资料提示（I-1）：不产生资格原因，也不影响状态。
     * 独立于 [SOFT_REASON_CODES]（那六类来自候选资格/机构/国家/凭证原因），但同为「缺资料」提示。
     */
    private fun researchDirectionHint(profile: ExpertProfile): AdmissionReason? =
        if (!profile.researchFields.isNullOrBlank()) null
        else AdmissionReason(AdmissionReasonCodes.RESEARCH_DIRECTION_MISSING,
            "研究方向缺失（资料提示，不参与自动准入）", "researchFields", profile.researchFields,
            "非空研究方向", "profile.researchFields")

    /** I-3：逐字保留 [EligibilityResult.rejectReasons] 的原码，只补事实字段与位置。 */
    private fun candidateReason(profile: ExpertProfile, code: String): AdmissionReason {
        val fact = CANDIDATE_FACTS[code]
            ?: return AdmissionReason(code, code, AdmissionReason.UNKNOWN_FIELD, null, null,
                "CandidateEligibilityService.evaluateEligibility()")
        return AdmissionReason(code, fact.label, fact.field, fact.observed(profile), fact.expected,
            "CandidateEligibilityService.evaluateEligibility()")
    }

    private data class CandidateFact(
        val label: String,
        val field: String,
        val expected: String,
        val observed: (ExpertProfile) -> String?
    )

    companion object {
        /** 解释器版本：判定规则或原因词表变化时递增；持久化结论必须带此版本。 */
        const val POLICY_VERSION = "2026-10-13.1"

        private const val RND_TYPES = "PRODUCTION_RND / ACADEMIC_RND / HYBRID_RND"

        /** 研发范围/证据只听「未确认/不足」两种，明确不符（OUT_OF_SCOPE/SERVICE_ONLY）继续阻断。 */
        private const val RND_SCOPE_UNCONFIRMED = "RND_SCOPE_UNCONFIRMED"
        private const val RND_EVIDENCE_INSUFFICIENT = "RND_EVIDENCE_INSUFFICIENT"

        /**
         * I-1：缺资料类原因码 —— 这些原因逐字保留原码与事实，但只出现在 `hints`，不再阻断自动准入。
         * 未列入本集合的原因（身份、`SOURCE_ID_*`、`INSTITUTION_EVIDENCE_INVALID`、研发范围明确不符、
         * 学历/年龄/国籍/指标/邮箱…）继续留在 `blockingReasons`。
         */
        val SOFT_REASON_CODES: Set<String> = setOf(
            AdmissionReasonCodes.COUNTRY_MISSING,
            AdmissionReasonCodes.COUNTRY_UNMAPPED,
            AdmissionReasonCodes.INSTITUTION_MISSING,
            AdmissionReasonCodes.INSTITUTION_EVIDENCE_MISSING,
            RND_SCOPE_UNCONFIRMED,
            RND_EVIDENCE_INSUFFICIENT
        )

        /**
         * 现有候选规则码 → 可展示事实。键与 [AdmissionReasonCodes.CANDIDATE_RULE_CODES] 一致；
         * 未收录的码原样透传（`observed`/`expected` 留空，不虚构）。
         */
        private val CANDIDATE_FACTS: Map<String, CandidateFact> = mapOf(
            "MISSING_ORCID" to CandidateFact("缺少 ORCID", "orcidId", "非空 ORCID") { it.orcidId.ifBlank { null } },
            "INVALID_EMAIL_FORMAT" to CandidateFact("邮箱格式无效", "email", "合法邮箱格式") { it.email },
            "DISPOSABLE_EMAIL" to CandidateFact("邮箱为一次性邮箱", "email", "非一次性邮箱") { it.email },
            "NO_DOCTORAL_DEGREE" to CandidateFact("学历未达博士", "degree", "博士学历") { it.degree },
            "AGE_EXCEEDED" to CandidateFact("年龄超出配置范围", "age", "在配置的年龄范围内") { it.age?.toString() },
            "CHINESE_NATIONALITY" to CandidateFact("国籍为受限的中国籍", "nationality", "非中国籍") { it.nationality },
            "H_INDEX_TOO_LOW" to CandidateFact("h-index 低于配置阈值", "hIndex", "不低于配置的最小 h-index") {
                it.hIndex?.toString()
            },
            "CITATION_COUNT_TOO_LOW" to CandidateFact("引用数低于配置阈值", "citationCount", "不低于配置的最小引用数") {
                it.citationCount?.toString()
            },
            "INACTIVE" to CandidateFact("最近年限内无发表", "lastPublicationYear", "在配置的最近年限内有发表") {
                it.lastPublicationYear?.toString()
            },
            RND_SCOPE_UNCONFIRMED to CandidateFact("研发范围未确认", "expertClassification.type", RND_TYPES) {
                it.expertClassification?.type?.name
            },
            RND_EVIDENCE_INSUFFICIENT to CandidateFact("研发证据不足", "expertClassification.type", RND_TYPES) {
                it.expertClassification?.type?.name
            },
            "RND_OUT_OF_SCOPE" to CandidateFact("不在研发范围内", "expertClassification.type", RND_TYPES) {
                it.expertClassification?.type?.name
            },
            "RND_SERVICE_ONLY" to CandidateFact("仅有服务性工作", "expertClassification.type", RND_TYPES) {
                it.expertClassification?.type?.name
            }
        )
    }
}
