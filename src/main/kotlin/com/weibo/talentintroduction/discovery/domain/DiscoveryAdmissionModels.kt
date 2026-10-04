package com.weibo.talentintroduction.discovery.domain

import java.time.Instant

/**
 * 01（I-1/I-2/I-3）：新发现/待确认档案的自动准入结论。
 *
 * - [AUTO_PASSED]：基础校验全部通过，自动准入，无需人工点击审核。
 * - [LEGACY_APPROVED]：同身份有效历史人工认可（`DiscoveryIdentity.legacyOutreachApproved`）。单独返回，
 *   不混入自动分支，也**不**升级为学术来源验证（`DiscoveryIdentity.allowed` 仍为 false）。
 * - [NEEDS_REVIEW]：存在阻断原因，需人工审核。人工 `MANUAL_APPROVED` 由 02 的审核表提供，
 *   本解释器不签发任何人工批准（I-2）。
 */
enum class DiscoveryAdmissionStatus { AUTO_PASSED, LEGACY_APPROVED, NEEDS_REVIEW }

/**
 * 01（I-3）：一条准入原因 —— 事实，不是推断。
 *
 * [observed] / [expected] 是**当前档案**里的实际值与规则期望值；未知一律留 `null`，
 * [field] 无法定位时固定为 [UNKNOWN_FIELD]，禁止虚构历史原因或旧字段值。
 */
data class AdmissionReason(
    val code: String,
    val label: String,
    val field: String,
    val observed: String? = null,
    val expected: String? = null,
    val sourceLocation: String
) {
    companion object {
        /** [field] 无法定位（未知信息）时的固定占位，不是字段名。 */
        const val UNKNOWN_FIELD = "UNKNOWN"
    }
}

/**
 * 01（I-1/I-3）：判定当时生效的候选资格配置快照（只读回显）。字段逐字对应
 * `CandidateFilterProperties` / `AcademicFilterProperties`，本子计划不新增、不改写任何阈值。
 */
data class AdmissionConfigSnapshot(
    val requireOrcid: Boolean,
    val requireValidEmail: Boolean,
    val requireDoctoralDegree: Boolean,
    val excludeChineseNationality: Boolean,
    val enableAgeFilter: Boolean,
    val maxAgeExclusive: Int,
    val enableHIndexFilter: Boolean,
    val minHIndex: Int,
    val enableCitationFilter: Boolean,
    val minCitationCount: Int,
    val enableActivityFilter: Boolean,
    val recentYearsThreshold: Int
)

/**
 * 01（I-1/I-3）：一次自动准入判定的完整结果。
 *
 * 不变量：未命中有效历史认可时 `status == AUTO_PASSED` 当且仅当 [blockingReasons] 为空；
 * 命中有效历史认可时 [status] 为 [DiscoveryAdmissionStatus.LEGACY_APPROVED]，[blockingReasons] 为空，
 * 机构/国家/凭证/资格问题只进 [hints]（已认可的事实提示，不再强制送回审核）。
 * 没有 `REVIEW_REQUIRED` 之类的冗余布尔：是否需要人工审核由 [status] 派生。
 */
data class AutomaticAdmissionResult(
    val status: DiscoveryAdmissionStatus,
    val blockingReasons: List<AdmissionReason>,
    val hints: List<AdmissionReason>,
    val policyVersion: String,
    val checkedAt: Instant,
    val configSnapshot: AdmissionConfigSnapshot
) {
    /** 从 [status] 派生：`AUTO_PASSED` 与 `LEGACY_APPROVED` 均准入，`NEEDS_REVIEW` 不准入。 */
    val eligible: Boolean get() = status != DiscoveryAdmissionStatus.NEEDS_REVIEW
}

/**
 * 01（I-3）：原因词表。前 12 个码由本子计划产出，`CANDIDATE_RULE_CODES` 是现有
 * `CandidateEligibilityService.evaluateEligibility()` 的原码（逐字保留，不新增阈值）。
 */
object AdmissionReasonCodes {
    const val IDENTITY_MISSING = "IDENTITY_MISSING"
    const val IDENTITY_STATUS_UNVERIFIED = "IDENTITY_STATUS_UNVERIFIED"
    const val IDENTITY_VERSION_UNSUPPORTED = "IDENTITY_VERSION_UNSUPPORTED"
    const val IDENTITY_FIELDS_MISMATCH = "IDENTITY_FIELDS_MISMATCH"
    const val IDENTITY_SOURCE_INVALID = "IDENTITY_SOURCE_INVALID"
    const val INSTITUTION_MISSING = "INSTITUTION_MISSING"
    const val COUNTRY_MISSING = "COUNTRY_MISSING"
    const val COUNTRY_UNMAPPED = "COUNTRY_UNMAPPED"
    const val INSTITUTION_EVIDENCE_MISSING = "INSTITUTION_EVIDENCE_MISSING"
    const val INSTITUTION_EVIDENCE_INVALID = "INSTITUTION_EVIDENCE_INVALID"
    const val SOURCE_ID_MISSING = "SOURCE_ID_MISSING"
    const val SOURCE_ID_CONFLICT = "SOURCE_ID_CONFLICT"

    /** 资料提示码：研究方向缺失只提示，永不产生资格原因（I-1）。 */
    const val RESEARCH_DIRECTION_MISSING = "RESEARCH_DIRECTION_MISSING"

    /**
     * 现有候选规则原码（`CandidateEligibilityService.evaluateEligibility()` 的字面量全集）。
     * 解释器逐字透传这些码，未知码同样原样透传，绝不改写或丢弃。
     */
    val CANDIDATE_RULE_CODES: Set<String> = setOf(
        "MISSING_ORCID",
        "INVALID_EMAIL_FORMAT",
        "DISPOSABLE_EMAIL",
        "NO_DOCTORAL_DEGREE",
        "AGE_EXCEEDED",
        "CHINESE_NATIONALITY",
        "H_INDEX_TOO_LOW",
        "CITATION_COUNT_TOO_LOW",
        "INACTIVE",
        "RND_SCOPE_UNCONFIRMED",
        "RND_EVIDENCE_INSUFFICIENT",
        "RND_OUT_OF_SCOPE",
        "RND_SERVICE_ONLY"
    )
}
