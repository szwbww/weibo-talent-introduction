package com.weibo.talentintroduction.document.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * 手动材料上传来源（V130）：一行 = 一次成功的手动上传。
 *
 * 只承载归属与审计身份：
 * - [expertContactId] 是唯一归属来源，材料下载/预览/AI 都要求它与
 *   `expert_document.expert_contact_id` 同时等于请求的 contactId；
 * - [uploadedBy] 只由服务端会话用户名写入（客户端不可传），列表来源展示用它；
 * - 文件本身（文件名/大小/磁盘路径）只存 `mail_attachment`，材料类型/审核状态只存
 *   `expert_document`，本表不重复。
 *
 * 不存路径、不存文件名、不存重复/删除状态：手动材料不是邮件事件（`mail_record_id`
 * 与 `inbound_processing_id` 恒为 null），也不创建 `mail_attachment_transfer` 行。
 */
@Table("manual_expert_material_upload")
data class ManualExpertMaterialUpload(
    @Id
    val id: Long? = null,
    val expertContactId: Long,
    val uploadedBy: String,
    val createdAt: LocalDateTime? = null
)
