package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * fast-p 01（总 I-11）：人工回复延迟发送配置。
 *
 * `enabled=false` 只控制新请求是否入队；已存在任务的处理/读取/撤回不受影响。
 * 60 秒期限是业务常量（`DELAYED_MANUAL_REPLY_DELAY_MS`），刻意不可配置；
 * worker 线程数固定 1，不复用批量发送 executor。
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.manual-reply-delay")
data class ManualReplyDelayProperties(
    val enabled: Boolean = false,
    val pollMs: Long = 1000L,
    val unknownAfterMs: Long = 600_000L
) {
    init {
        require(pollMs > 0) { "talent-introduction.manual-reply-delay.poll-ms must be positive" }
        require(unknownAfterMs > 0) { "talent-introduction.manual-reply-delay.unknown-after-ms must be positive" }
    }
}

@Configuration
@EnableConfigurationProperties(ManualReplyDelayProperties::class)
class ManualReplyDelayConfiguration
