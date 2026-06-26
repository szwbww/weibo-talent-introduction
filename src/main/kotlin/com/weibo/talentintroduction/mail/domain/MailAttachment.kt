package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("mail_attachment")
data class MailAttachment(
    @Id
    val id: Long? = null,
    val mailRecordId: Long? = null,
    val inboundProcessingId: Long? = null,
    val fileName: String,
    val contentType: String?,
    val fileSize: Long,
    val storagePath: String,
    val createdAt: LocalDateTime? = null
)
