package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * 邮件附件行。三类所有者严格三选一（V130 CHECK 在 MySQL 8 生效，5.7 忽略 CHECK，
 * 因此所有写路径仍必须显式构造恰好一个非空 owner）：
 * - [mailRecordId]：邮件消息/旧收信附件；
 * - [inboundProcessingId]：来信处理链附件；
 * - [manualUploadId]：运营手动上传的专家材料（归属取
 *   `manual_expert_material_upload.expert_contact_id`，不是邮件事件）。
 *
 * [manualUploadId] 追加在末尾且默认 null：既有三条写路径（registerMetadataAttachment、
 * saveLegacyRecordAttachment、saveLegacyProcessingAttachment）与 transfer 补偿更新
 * （只改 storage_path/file_size）都保持原样，只有手动上传服务写它。
 */
@Table("mail_attachment")
data class MailAttachment(
    @Id
    val id: Long? = null,
    val mailRecordId: Long? = null,
    val inboundProcessingId: Long? = null,
    val fileName: String,
    val contentType: String?,
    val fileSize: Long? = null,
    val storagePath: String? = null,
    val createdAt: LocalDateTime? = null,
    val manualUploadId: Long? = null
)
