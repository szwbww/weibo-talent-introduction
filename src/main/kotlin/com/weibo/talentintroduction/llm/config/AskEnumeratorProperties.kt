package com.weibo.talentintroduction.llm.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * P2a (plan 02-unrecognized-request-detection, I-6): enumerator gating for the
 * auto-reply path. The workbench path is never gated by this flag (it always
 * enumerates); the auto-reply path skips enumeration while
 * [enabledForAutoReply] is false (the default), so the IMAP polling loop does
 * not gain a synchronous LLM call per mail.
 *
 * Key: `talent-introduction.llm.ask-enumerator.enabled-for-auto-reply`
 * (the plan writes `llm.ask-enumerator.enabled-for-auto-reply`, matching the
 * repository convention that abbreviates the `talent-introduction.` prefix).
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.llm.ask-enumerator")
data class AskEnumeratorProperties(
    val enabledForAutoReply: Boolean = false
)

/**
 * Registration kept in this file to stay inside the authorized file scope
 * (same mechanism as `TaskAuditRetentionScheduler` keeping its own
 * [EnableConfigurationProperties] for the same reason).
 */
@Configuration
@EnableConfigurationProperties(AskEnumeratorProperties::class)
class AskEnumeratorPropertiesConfig
