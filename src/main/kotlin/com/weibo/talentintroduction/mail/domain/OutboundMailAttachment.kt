package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * outbound_mail_attachment：一行 = 一次成功上传的通用附件原件元数据（04 I-1）。
 *
 * [id] 是服务端随机 UUID，同时也是磁盘文件名 `outbound/<id>`；元数据与原字节创建后
 * 不可变——本类没有任何「读取后 copy 再写回」路径，仓库只提供 INSERT 与按 id 批量
 * 读取（不存在 UPDATE/DELETE 语句）。[createdBy] 是会话登录名
 * （`AuthSessionKeys.USERNAME`）；请求体里的 operatorName 一律不参与身份（I-2）。
 *
 * 用户原始文件名只作为 [fileName] 展示，绝不用于拼路径（I-4）。
 */
@Table("outbound_mail_attachment")
data class OutboundMailAttachment(
    @Id
    val id: String,
    val expertContactId: Long,
    val createdBy: String,
    val fileName: String,
    val contentType: String,
    /** 原件真实字节数（0 字节合法）。 */
    val byteLength: Long,
    /** 原件真实字节的 SHA-256（64 位小写 hex）。 */
    val sha256: String,
    val createdAt: LocalDateTime
)
