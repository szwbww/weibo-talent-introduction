package com.weibo.talentintroduction.template.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("mail_compose_template")
data class MailComposeTemplate(
    @Id val id: Long? = null,
    val templateCode: String? = null,
    val templateName: String,
    val subject: String,
    val subjectVariants: String? = null,
    val description: String? = null,
    val mailType: String? = null,
    val requiredKeys: String? = null,
    val enabled: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)

@Table("mail_compose_template_block")
data class MailComposeTemplateBlock(
    @Id val id: Long? = null,
    val templateId: Long,
    val blockOrder: Int,
    val blockType: String,
    val refId: Long? = null,
    val customText: String? = null
)

object ComposeBlockType {
    const val QA_RULE = "QA_RULE"
    const val REPLY_SNIPPET = "REPLY_SNIPPET"
    const val CUSTOM_TEXT = "CUSTOM_TEXT"
}
