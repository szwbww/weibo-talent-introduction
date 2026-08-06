package com.weibo.talentintroduction.mail.service

import java.util.UUID

/**
 * 出站 Message-ID 的唯一生成入口（写侧专用）。
 *
 * 格式 `<{kind}-{discriminator}-{uuid}@{domain}>`：domain 必须取自本次投递实际使用的
 * 发件账号 `MailSenderAccount.senderEmail` 的 `@` 后缀（I-2）；唯一性只依赖
 * `UUID.randomUUID()`，`kind` / `discriminator` 仅供人工排查，禁止任何代码解析或匹配（I-3）。
 */
object OutboundMessageIdFactory {
    private val KIND_PATTERN = Regex("^[a-z-]+$")

    fun newId(kind: String, discriminator: String, senderEmail: String): String {
        require(kind.isNotBlank()) { "kind must not be blank" }
        require(KIND_PATTERN.matches(kind)) { "kind must contain only [a-z-]" }
        require(discriminator.isNotBlank()) { "discriminator must not be blank" }
        val domain = senderEmail.substringAfter("@", "")
        require(domain.isNotBlank()) { "senderEmail must contain a non-blank domain after '@'" }
        return "<$kind-$discriminator-${UUID.randomUUID()}@$domain>"
    }
}
