package com.weibo.talentintroduction.discovery.domain

import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import java.time.LocalDateTime

/**
 * 02（I-1/I-2/I-4）：深度发现审核的动作。`REVOKE` 只撤销当前仍然有效的人工决策。
 */
enum class DiscoveryReviewAction { APPROVE, HOLD, REJECT, REVOKE }

/**
 * 02（I-4）：审核项状态机 `STAGED → READY → APPLYING → APPLIED`，可分支到
 * `STALE` / `FAILED` / `CANCELLED`。`APPLIED` 只表示「审核已保存」，
 * 不表示 ES 晋升完成（04 接投影，UI 分开显示）。
 */
enum class DiscoveryReviewItemState { STAGED, READY, APPLYING, APPLIED, STALE, FAILED, CANCELLED }

/**
 * 02（I-1）：`expert_discovery_admission.decision` 的取值域，逐字对应迁移。
 * 人工决策（`MANUAL_APPROVED`/`HOLD`/`REJECTED`）与自动结论
 * （`AUTO_PASSED`/`NEEDS_REVIEW`/`LEGACY_APPROVED`）共用一列，是否准入由该值派生。
 */
enum class DiscoveryReviewDecision {
    AUTO_PASSED,
    NEEDS_REVIEW,
    MANUAL_APPROVED,
    LEGACY_APPROVED,
    HOLD,
    REJECTED;

    /** 当前结论是否来自人工点击（`APPROVE`/`HOLD`/`REJECT`）。 */
    val manual: Boolean get() = this == MANUAL_APPROVED || this == HOLD || this == REJECTED

    companion object {
        /** 仅三种写动作可映射到结论；`REVOKE` 由撤销时重新运行的自动校验决定。 */
        fun forAction(action: DiscoveryReviewAction): DiscoveryReviewDecision = when (action) {
            DiscoveryReviewAction.APPROVE -> MANUAL_APPROVED
            DiscoveryReviewAction.HOLD -> HOLD
            DiscoveryReviewAction.REJECT -> REJECTED
            DiscoveryReviewAction.REVOKE -> throw IllegalArgumentException("REVOKE 没有直接结论，须重新运行自动校验")
        }
    }
}

/**
 * 02（I-2）：身份键的规范算法 = SHA-256(真实 docId ␀ 规范化邮箱 ␀ givenNames ␀ familyNames)。
 * 精确绑定到人：研究方向/机构/国家/指标/分类变化不使批准失效；邮箱或姓名变化即视为换人。
 */
object DiscoveryReviewIdentity {
    fun hash(docId: String, email: String?, givenNames: String?, familyNames: String?): String =
        DiscoveryIdentity.hash(
            listOf(docId, DiscoveryIdentity.normalizedEmail(email), givenNames.orEmpty(), familyNames.orEmpty())
                .joinToString("\u0000")
        )
}

/** 02（I-1）：`expert_discovery_admission` 行。 */
data class ExpertDiscoveryAdmission(
    val expertDocId: String,
    val identityHash: String,
    val decision: String,
    val revision: Long,
    val decisionItemId: Long?,
    val policyVersion: String?,
    val checkedAt: LocalDateTime?,
    val updatedAt: LocalDateTime
) {
    val decisionEnum: DiscoveryReviewDecision get() = DiscoveryReviewDecision.valueOf(decision)
}

/** 02（I-1/I-4）：`expert_discovery_review_item` 行（快照名单 + 决策 + 应用状态 + 历史）。 */
data class ExpertDiscoveryReviewItem(
    val id: Long,
    val batchKey: String,
    val expertDocId: String,
    val sourceLevel: String,
    val identityHash: String,
    val snapshotHash: String,
    val expectedRevision: Long,
    val action: String,
    val state: String,
    val snapshotJson: String,
    val reasonSnapshotJson: String?,
    val actor: String,
    val note: String?,
    val previousItemId: Long?,
    val executionId: Long?,
    val errorCode: String?,
    val createdAt: LocalDateTime,
    val confirmedAt: LocalDateTime?,
    val appliedAt: LocalDateTime?
)

/**
 * 02（I-3/I-4）：prepare 的固定快照（真实事实 + 自动原因 + ES seq/term + 准备时间）。
 * 只读回显，不推断历史；`snapshotHash` 覆盖整个对象。
 */
data class DiscoveryReviewSnapshot(
    val docId: String,
    val level: String,
    val orcidId: String,
    val email: String?,
    val givenNames: String?,
    val familyNames: String?,
    val institution: String?,
    val country: String?,
    val researchFields: String?,
    val disciplineCategory: String?,
    val institutionEvidence: String?,
    val filterResult: String?,
    val esSeqNo: Long,
    val esPrimaryTerm: Long,
    val preparedAt: String
)

/** 02（I-1）：自动结论 + 原因在 prepare 时的快照（`reason_snapshot_json`）。 */
data class DiscoveryReviewReasonSnapshot(
    val automaticStatus: String,
    val blockingReasons: List<AdmissionReason>,
    val hints: List<AdmissionReason>,
    val policyVersion: String
)

// ── 请求/响应 DTO ────────────────────────────────────────────────────────────

data class DiscoveryReviewPrepareRequest(
    val scope: String? = "IDS",
    val action: String? = null,
    val docIds: List<String> = emptyList(),
    val expectedRevisions: Map<String, Long> = emptyMap(),
    val note: String? = null,
    /** 默认 RAW；与 GET /experts 的 level 同义。 */
    val level: String? = null,
    /** 可选任务头（复用 task_execution）；不新建任务表。 */
    val executionId: Long? = null
)

data class DiscoveryReviewPrepareItemView(
    val docId: String,
    val identityHash: String,
    val expectedRevision: Long,
    val snapshotHash: String,
    val state: String
)

data class DiscoveryReviewPrepareResult(
    val batchKey: String,
    val batchHash: String,
    val itemCount: Int,
    val items: List<DiscoveryReviewPrepareItemView>
)

data class DiscoveryReviewConfirmRequest(val batchHash: String? = null)

data class DiscoveryReviewConfirmItemView(
    val itemId: Long,
    val docId: String,
    val state: String,
    val errorCode: String? = null
)

data class DiscoveryReviewConfirmResult(
    val batchKey: String,
    val total: Int,
    val applied: Int,
    val stale: Int,
    val failed: Int,
    val skipped: Int,
    val items: List<DiscoveryReviewConfirmItemView>
)

data class DiscoveryReviewBatchDetail(
    val batchKey: String,
    val counts: Map<String, Int>,
    val items: List<ExpertDiscoveryReviewItem>
)

data class DiscoveryReviewRevokeRequest(val note: String? = null)

data class DiscoveryReviewRevokeResult(
    val itemId: Long,
    val docId: String,
    val decision: String,
    val revision: Long
)

/**
 * 02（I-2/I-3）：列表行。`email`/`givenNames`/`familyNames` 只在登录态 API 返回。
 * `automatic*` 是实时自动结论（事实不做推断）；`decision` 是有效结论
 * （同身份的人工决策优先于自动问题，HOLD/REJECTED 不被自动覆盖）。
 */
data class DiscoveryReviewExpertRow(
    val docId: String,
    val level: String,
    val orcidId: String,
    val email: String?,
    val givenNames: String?,
    val familyNames: String?,
    val institution: String?,
    val country: String?,
    val researchFields: String?,
    val disciplineCategory: String?,
    val institutionEvidence: String?,
    val filterResult: String?,
    val tags: List<String>,
    val automaticStatus: String,
    val automaticReasons: List<AdmissionReason>,
    val automaticHints: List<AdmissionReason>,
    val revision: Long,
    val initialized: Boolean,
    val decision: String,
    val decisionManual: Boolean,
    val identityChanged: Boolean,
    val reviewedActor: String?,
    val reviewedAt: LocalDateTime?,
    val addressWarning: String?
)

data class DiscoveryReviewExpertPage(
    val total: Long,
    val from: Int,
    val size: Int,
    val experts: List<DiscoveryReviewExpertRow>
)
