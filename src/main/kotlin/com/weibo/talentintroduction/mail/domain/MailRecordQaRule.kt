package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("mail_record_qa_rule")
data class MailRecordQaRule(
    @Id
    val id: Long? = null,
    val mailRecordId: Long,
    val qaRuleId: Long,
    val ordinal: Int
)
