package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("mail_record")
data class MailRecord(
    @Id
    val id: Long? = null,
    val expertContactId: Long,
    val direction: String,
    val mailType: String,
    val senderAccountCode: String? = null,
    val triggeredBy: String? = null,
    val sourceInboundId: Long? = null,
    val messageId: String?,
    val inReplyTo: String?,
    val subject: String?,
    val body: String?,
    val cleanedBody: String? = null,
    val matchedQaRuleId: Long?,
    val sendStatus: String?,
    val receivedAt: LocalDateTime?,
    val sentAt: LocalDateTime?,
    val errorSummary: String? = null,
    val mailSendAttemptId: Long? = null,
    val createdAt: LocalDateTime? = null,
    /** 产生该邮件的任务执行 id；只由 ManualOutreachTxHelper 写入（见计划 I2a-1），其余构造点恒为 null。 */
    val taskExecutionId: Long? = null
)
