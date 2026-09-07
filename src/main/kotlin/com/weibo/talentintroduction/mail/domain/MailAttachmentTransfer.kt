package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * mail_attachment_transfer：一行 = 一个远端 part（唯一源身份）与其有界下载状态。
 *
 * 状态机（I-2）：METADATA_ONLY -> QUEUED -> DOWNLOADING -> STORED；
 * 失败落 FAILED / SOURCE_UNAVAILABLE；失败重试必须显式请求
 * （METADATA_ONLY|FAILED|SOURCE_UNAVAILABLE -> QUEUED）。
 * lease_until/worker_token 只在 DOWNLOADING 有值；所有提交按 id + workerToken
 * + 租约 CAS。
 *
 * 完整字段契约见总计划持久化契约（02 新表）；禁止新增状态/列。
 */
@Table("mail_attachment_transfer")
data class MailAttachmentTransfer(
    @Id
    val id: Long? = null,
    /** MATERIAL 必填（一个附件至多一行，uk_mail_attachment_transfer_attachment）；
     *  DMARC 恒为空。 */
    val attachmentId: Long? = null,
    /** 仅 MATERIAL / DMARC，登记后不可变。 */
    val purpose: String,
    val accountCode: String,
    /** 实际收信账号文件夹，区分大小写（列级 utf8mb4_bin）。 */
    val folder: String,
    val uidValidity: Long,
    val imapUid: Long,
    /** 点分 1-based part 路径（如 2、2.1），ASCII，登记后不可变。 */
    val partPath: String,
    /** 可空；仅用于下载复核，空不补猜值。 */
    val messageId: String? = null,
    val fileName: String,
    val contentType: String? = null,
    /** BODYSTRUCTURE 编码大小；未知 null，不是实际文件长度。 */
    val encodedSize: Long? = null,
    val disposition: String? = null,
    /** MATERIAL 由 04 终结登记桥接；DMARC 恒空，不建立假来信。 */
    val inboundProcessingId: Long? = null,
    val state: String = STATE_METADATA_ONLY,
    /** METADATA_ONLY 空；用户动作取 Session；DMARC 固定 SYSTEM。 */
    val requestedBy: String? = null,
    val queuedAt: LocalDateTime? = null,
    val startedAt: LocalDateTime? = null,
    val leaseUntil: LocalDateTime? = null,
    val workerToken: String? = null,
    /** 本次尝试已写实际字节；重试归 0，不是未下载时的远端长度。 */
    val bytesDownloaded: Long = 0,
    /** 每次实际领取 +1；重复点击不增。 */
    val attempt: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
) {
    companion object {
        const val PURPOSE_MATERIAL = "MATERIAL"
        const val PURPOSE_DMARC = "DMARC"

        const val STATE_METADATA_ONLY = "METADATA_ONLY"
        const val STATE_QUEUED = "QUEUED"
        const val STATE_DOWNLOADING = "DOWNLOADING"
        const val STATE_STORED = "STORED"
        const val STATE_FAILED = "FAILED"
        const val STATE_SOURCE_UNAVAILABLE = "SOURCE_UNAVAILABLE"

        /** 全部可请求下载（领取目标）的源状态。 */
        val REQUESTABLE_STATES = setOf(
            STATE_METADATA_ONLY, STATE_FAILED, STATE_SOURCE_UNAVAILABLE
        )
    }
}
