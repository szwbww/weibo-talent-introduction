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
    val taskExecutionId: Long? = null,
    /** 会议日历附件存档 JSON（fast-p 02，I-1）：null=无会议日历；非 null 只能由 01
     *  CalendarAttachmentCodec.serialize 生成的规范快照（schemaVersion=1）。
     *  只由 ManualReplySendAttemptService 两个 finalize 的 copy/new 四支显式写，
     *  其余构造点保持默认 null（不强迫无关调用改参数）。 */
    val calendarAttachmentJson: String? = null
)
