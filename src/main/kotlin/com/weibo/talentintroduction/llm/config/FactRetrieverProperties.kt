package com.weibo.talentintroduction.llm.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 计划 01 (T1.1, I-9): LLM 全库事实检索的开关与上限。两个开关分离（与
 * [AskEnumeratorProperties] 同构）：工作台是人看着的，自动回复跑在 IMAP 拉取
 * 循环里（`BatchAutoMailReplyService`），先只对工作台开（[enabled]）；
 * [enabledForAutoReply] 控制 `select()`（自动/人工发送路径）是否调用检索。
 *
 * Key: `talent-introduction.llm.fact-retriever.*`（application.yml 绑定
 * enabled / enabled-for-auto-reply / max-facts-per-request / max-rules-in-prompt，
 * [cacheEntries] 保持默认）。
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.llm.fact-retriever")
data class FactRetrieverProperties(
    val enabled: Boolean = false,
    val enabledForAutoReply: Boolean = false,
    val maxFactsPerRequest: Int = 3,
    val maxRulesInPrompt: Int = 60,
    val cacheEntries: Int = 200
)

/**
 * Registration kept in this file to stay inside the authorized file scope
 * (same mechanism as `AskEnumeratorPropertiesConfig`).
 */
@Configuration
@EnableConfigurationProperties(FactRetrieverProperties::class)
class FactRetrieverPropertiesConfig
