package com.weibo.talentintroduction.rag.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 计划 01 (T5): RAG 确定性层与生成调用的运行期常量，默认值取自已实测的
 * `scripts/spike_deepseek_reply.py`（与 `FactRetrieverProperties` /
 * `AskEnumeratorProperties` 同构）：
 * - `prefilter_facts(limit=18)`、`selected_ids[:14]`、`_lexical_score` 的
 *   `100.0 / 12.0 / 1.0`、`call_deepseek_json` 的 `temperature=0.0 / 0.2`
 *   与 `max_tokens=900 / 2600`。
 *
 * Key: `talent-introduction.rag.*`（application.yml 的 `rag:` 块，kebab-case
 * 绑定：`prefilter-limit` → [prefilterLimit] 等）。
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.rag")
data class RagProperties(
    val prefilterLimit: Int = 18,
    val retrievalLimit: Int = 14,
    val minLexicalScore: Int = 2,
    val coverageWeight: Double = 100.0,
    val phraseWeight: Double = 12.0,
    val overlapWeight: Double = 1.0,
    val retrievalTemperature: Double = 0.0,
    val generationTemperature: Double = 0.2,
    val retrievalMaxTokens: Int = 900,
    val generationMaxTokens: Int = 2600
)

/**
 * 注册保持在本文件内以留在授权文件范围里（与 `FactRetrieverPropertiesConfig`
 * 同机制）。
 */
@Configuration
@EnableConfigurationProperties(RagProperties::class)
class RagPropertiesConfig
