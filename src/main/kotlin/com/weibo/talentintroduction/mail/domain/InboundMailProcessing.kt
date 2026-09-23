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
     * 物理收件箱主账号代码（I-2，V134）：NULL = 历史未知行，不回填；
     * 新收件由收信路由写入真实物理 owner（唯一键 mailbox_owner_code + 代际 + UID）。
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
    val updatedAt: LocalDateTime? = null
)
