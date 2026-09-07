package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import java.time.LocalDateTime

interface MailReceiveService {
    fun fetchInboundSince(account: MailSenderAccount, afterUid: Long, maxMessages: Int): InboundFetchResult

    fun fetchByUids(account: MailSenderAccount, uids: List<Long>): List<ReceivedMail>

    fun markSeen(account: MailSenderAccount, imapUid: Long)
}

data class InboundFetchResult(
    val mails: List<ReceivedMail>,
    val uidValidity: Long,
    val maxUidInWindow: Long
)

data class ReceivedMail(
    val imapUid: Long,
    val from: String,
    val subject: String?,
    val body: String,
    val messageId: String?,
    val inReplyTo: String?,
    val receivedAt: LocalDateTime,
    val attachments: List<ReceivedMailAttachment> = emptyList(),
    /** 实际 IMAP UIDVALIDITY（04 持久化契约：新收信必须为正值，不能补猜）。 */
    val uidValidity: Long = 0L,
    /** 正文是否因超过单信读取上限而被有界截断（默认 false；true 时后续必须转人工，不得自动回复）。 */
    val bodyTruncated: Boolean = false
)

/**
 * 远端附件来源描述（I-1：metadataOnly 模式下 content=null，定位信息只存在于
 * source；partPath 是点分 1-based MIME 路径，绝不按文件名定位）。
 */
data class ImapAttachmentSource(
    val accountCode: String,
    val folder: String,
    val uidValidity: Long,
    val uid: Long,
    val partPath: String,
    val messageId: String? = null,
    /** BODYSTRUCTURE 编码大小，只是估算；永不写入实际 file_size。 */
    val encodedSize: Long? = null,
    val disposition: String? = null
)

data class ReceivedMailAttachment(
    val fileName: String,
    val contentType: String?,
    /** metadataOnly 模式下恒为 null（配合 [source] 使用）；旧模式非 null，兼容既有 ByteArray 消费者。 */
    val content: ByteArray?,
    /** 仅旧模式允许为 null；metadataOnly 模式必须完整非空。 */
    val source: ImapAttachmentSource? = null
)

/** content=null（metadata 模式）下被当作有内容消费时的显式失败。 */
class MetadataContentUnavailableException(message: String) : IllegalStateException(message)

/** MIME 结构超限（节点数等）的明确可重试失败，绝不静默假完整。 */
class MetadataStructureLimitException(message: String) : IllegalStateException(message)

/** 元数据读取总时限超时的明确可重试失败；调用方不得推进游标。 */
class MetadataTimeoutException(message: String) : IllegalStateException(message)
