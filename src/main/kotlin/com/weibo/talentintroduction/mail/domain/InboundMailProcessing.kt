package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("inbound_mail_processing")
data class InboundMailProcessing(
    @Id
    val id: Long? = null,
    val senderAccountCode: String,
    /**
     * 实际登录的物理邮箱账号：NULL = 历史未知行，不回填。
     * 原邮箱补收时可以是转发组的别名账号；唯一键为账号 + folder + 代际 + UID。
     */
    val mailboxOwnerCode: String? = null,
    /** 远端邮箱代际（I-1）：0 仅历史未知（V120 默认），新接收必须为实际正值。 */
    val uidValidity: Long = 0,
    val imapUid: Long,
    val messageId: String?,
    val inReplyTo: String? = null,
    val fromEmail: String,
    val subject: String?,
    val body: String? = null,
    val cleanedBody: String? = null,
    val receivedAt: LocalDateTime,
    val processStatus: String,
    val processReason: String,
    val reasonType: String? = null,
    val resolvedAt: LocalDateTime? = null,
    val resolvedBy: String? = null,
    val expertContactId: Long?,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    /** 完整 IMAP 路径；同一 UID 在不同目录中表示不同邮件。 */
    val folder: String = "INBOX"
)
