package com.weibo.talentintroduction.expert.domain

import java.time.LocalDateTime

/**
 * 专家研发类型（I1-1）。恰好六个值，`sendable=true` 当且仅当类型属于 [ExpertClassification.SENDABLE_TYPES]（前三类）。
 */
enum class ExpertType {
    PRODUCTION_RND,
    ACADEMIC_RND,
    HYBRID_RND,
    SERVICE_ONLY,
    OUT_OF_SCOPE,
    UNKNOWN
}

/**
 * rnd-v1-2026 分类结果对象（I1-5 顶层 ES 对象）。
 *
 * 构造函数不接受调用方传入的 [sendable]（I1-1）：`sendable` 是只读派生 getter，
 * 仅当 [type] 属于 [SENDABLE_TYPES] 时为 true。Jackson 序列化仍会输出 `sendable`
 * 字段，但反序列化时 ES 中不可信的 sendable 值无法覆盖该 getter（fail closed）。
 *
 * 全部规则、词表、计分与证据 code 只由 ExpertClassificationService 计算（M-2）。
 */
data class ExpertClassification(
    val type: ExpertType,
    val productionScore: Int,
    val researchScore: Int,
    val positiveEvidence: List<String>,
    val negativeEvidence: List<String>,
    val version: String,
    val sourceFingerprint: String,
    val classifiedAt: LocalDateTime
) {
    val sendable: Boolean
        get() = type in SENDABLE_TYPES

    companion object {
        /** 可发信类型：仅前三类（I1-1）。 */
        val SENDABLE_TYPES: Set<ExpertType> = setOf(
            ExpertType.PRODUCTION_RND,
            ExpertType.ACADEMIC_RND,
            ExpertType.HYBRID_RND
        )
    }
}
