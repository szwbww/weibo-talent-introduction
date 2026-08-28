package com.weibo.talentintroduction.expert.domain

import java.time.LocalDateTime

/**
 * 专家研发类型（I1-1）。恰好六个值。
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
 * rnd-v2-2026 分类结果对象（I1-5 顶层 ES 对象）。
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
)
