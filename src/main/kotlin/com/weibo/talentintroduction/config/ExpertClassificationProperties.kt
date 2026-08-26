package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * 专家分类增量调度配置（子计划 04，I4-1/I4-4）。
 *
 * 默认关闭：`incremental-enabled=false`（或未配置）时 [ExpertClassificationScheduler] bean 不创建，
 * 发布/启动零副作用（I4-1）。`batchSize`/`delayMs`/`maxDocsPerRun` 在构造时按 I4-4 校验范围，
 * 越界配置直接导致启动失败（有界增量，不静默收敛）。
 *
 * [promotionGateEnabled]（子计划 03，I3-5/M-6）只控制快速晋升时是否拒绝 `SERVICE_ONLY`/`OUT_OF_SCOPE`
 * 两类证据充分的专家；**不控制分类写入**——无论开关状态，被晋升的文档都会带上 `expertClassification`。
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-classification")
data class ExpertClassificationProperties(
    val incrementalEnabled: Boolean = false,
    val promotionGateEnabled: Boolean = false,
    val incrementalCron: String = "0 0 4 * * ?",
    val batchSize: Int = 500,
    val delayMs: Int = 250,
    val maxDocsPerRun: Long = 50000
) {
    init {
        require(batchSize in 100..1000) { "batchSize 必须在 100..1000" }
        require(delayMs in 0..5000) { "delayMs 必须在 0..5000" }
        require(maxDocsPerRun in 1L..200000L) { "maxDocsPerRun 必须在 1..200000" }
    }
}
