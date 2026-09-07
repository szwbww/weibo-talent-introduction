package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.sun.mail.imap.IMAPFolder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.UIDFolder

/**
 * 远端 part 的唯一身份（account/folder/UIDVALIDITY/UID/partPath；Message-ID
 * 仅复核，文件名/主题不作身份）。客户端文件名永不参与路径拼接。
 */
data class RemotePartSource(
    val folder: String,
    val uidValidity: Long,
    val uid: Long,
    val messageId: String? = null,
    val partPath: String,
    val expectedContentType: String? = null
)

/**
 * 有界取件失败。错误已脱敏：message 不含密码、凭据或邮件正文；内部细节只进日志。
 * [terminalState] 决定行落 FAILED 还是 SOURCE_UNAVAILABLE；[code] 写入 error_code。
 */
sealed class AttachmentFetchException(
    message: String,
    val terminalState: String,
    val code: String
) : Exception(message) {

    class SourceUnavailable(code: String, message: String) :
        AttachmentFetchException(
            message,
            MailAttachmentTransfer.STATE_SOURCE_UNAVAILABLE,
            code
        )

    class LimitExceeded(message: String) :
        AttachmentFetchException(message, MailAttachmentTransfer.STATE_FAILED, "LIMIT_EXCEEDED")

    /** [reason] 是调用方（worker）写入的中断原因，原样回传用于结局判定。 */
    class Aborted(val reason: String, message: String) :
        AttachmentFetchException(message, MailAttachmentTransfer.STATE_FAILED, "TIMEOUT")

    class TransferFailed(code: String, message: String) :
        AttachmentFetchException(message, MailAttachmentTransfer.STATE_FAILED, code)
}

/** 已解析就绪的目标 part；只取该 part 的内容，[close] 优雅关闭，[forceClose] 硬关闭。 */
interface ResolvedAttachmentPart : AutoCloseable {
    fun streamContent(
        sink: OutputStream,
        maxBytes: Long,
        abortReason: () -> String?
    ): Long

    override fun close()

    /** 不等待服务端响应的硬关闭（总时限/租约丢失时由 watchdog 调用）。 */
    fun forceClose()
}

/**
 * 独立 READ_ONLY 连接按源身份取 part：
 *
 * 校验顺序：folder UIDVALIDITY -> UID 存在 -> Message-ID（有值才比较）-> part
 * 路径/类型；任一不符抛 [AttachmentFetchException.SourceUnavailable]。绝不以
 * subject/from 搜索换源，也不下载整个 message：内容流通过
 * mail.imap.partialfetch + mail.imap.fetchsize 的 partial fetch 只取目标 section
 * （BODY.PEEK）。
 *
 * 总时限不由本类执行，但被要求硬关闭时必须真关闭连接（forceClose ->
 * protocol.disconnect -> socket.close），不能只 Future.cancel。
 */
@Component
class ImapAttachmentContentFetcher(
    private val properties: MailAttachmentStorageProperties
) {
    private val log = LoggerFactory.getLogger(ImapAttachmentContentFetcher::class.java)

    /** 打开连接并校验源；未通过校验抛 [AttachmentFetchException]。 */
    fun resolve(account: MailSenderAccount, source: RemotePartSource): ResolvedAttachmentPart {
        val store = try {
            Session.getInstance(imapProperties(account.imapPort))
                .getStore("imap")
                .apply {
                    connect(
                        account.imapHost, account.imapPort,
                        account.imapUsername, account.imapPassword
                    )
                }
        } catch (e: MessagingException) {
            throw AttachmentFetchException.TransferFailed(
                "TIMEOUT",
                "cannot connect to the source mailbox"
            )
        }

        var opened = false
        try {
            val folder = store.getFolder(source.folder)
            folder.open(Folder.READ_ONLY)
            opened = true
            val uidFolder = folder as? UIDFolder
                ?: throw AttachmentFetchException.SourceUnavailable(
                    "SOURCE_UNAVAILABLE",
                    "mailbox does not support UID lookup"
                )
            if (uidFolder.uidValidity != source.uidValidity) {
                throw AttachmentFetchException.SourceUnavailable(
                    "UIDVALIDITY_MISMATCH",
                    "mailbox UIDVALIDITY changed since registration"
                )
            }
            val message = uidFolder.getMessageByUID(source.uid)
                ?: throw AttachmentFetchException.SourceUnavailable(
                    "SOURCE_MISSING",
                    "message is no longer available in the source mailbox"
                )
            verifyMessageId(message, source.messageId)
            val target = resolvePartByPath(message, source.partPath)
            verifyContentType(target, source.expectedContentType)
            return ImapResolvedPart(folder, store, target, log)
        } catch (e: AttachmentFetchException) {
            if (opened) closeQuietly(store)
            throw e
        } catch (e: MessagingException) {
            // 源侧协议/网络失败一律按可见超时类失败处理，不泄露服务端原文。
            log.warn("IMAP resolve failed for account {} part {}", source.uid, source.partPath, e)
            if (opened) closeQuietly(store)
            throw AttachmentFetchException.TransferFailed(
                "TIMEOUT",
                "source mailbox operation failed before download"
            )
        }
    }

    private fun verifyMessageId(message: Message, expected: String?) {
        if (expected.isNullOrBlank()) {
            return // 空不补猜值
        }
        val mime = message as? javax.mail.internet.MimeMessage
        val actual = try {
            mime?.messageID
        } catch (e: MessagingException) {
            throw AttachmentFetchException.TransferFailed(
                "TIMEOUT",
                "cannot read message identity before download"
            )
        }
        if (actual == null || actual != expected) {
            throw AttachmentFetchException.SourceUnavailable(
                "MESSAGE_ID_MISMATCH",
                "message Message-ID does not match the registered source"
            )
        }
    }

    private fun resolvePartByPath(message: Message, partPath: String): Part {
        val segments = parsePartPath(partPath)
        var current: Part = message
        for ((index, segment) in segments.withIndex()) {
            val content = try {
                current.content
            } catch (e: MessagingException) {
                throw AttachmentFetchException.TransferFailed(
                    "TIMEOUT",
                    "cannot read message structure from the source mailbox"
                )
            }
            if (content is Multipart) {
                if (segment < 1 || segment > content.count) {
                    throw AttachmentFetchException.SourceUnavailable(
                        "PART_NOT_FOUND",
                        "registered part does not exist in the message"
                    )
                }
                current = try {
                    content.getBodyPart(segment - 1)
                } catch (e: MessagingException) {
                    throw AttachmentFetchException.TransferFailed(
                        "TIMEOUT",
                        "cannot read message part from the source mailbox"
                    )
                }
            } else {
                // 非 multipart 的 message 只有 1 号 part（整封正文）；更深的路径不存在。
                if (segments.size == 1 && segments[0] == 1) {
                    return current
                }
                throw AttachmentFetchException.SourceUnavailable(
                    "PART_NOT_FOUND",
                    "registered part path is not inside a multipart body"
                )
            }
        }
        return current
    }

    private fun verifyContentType(part: Part, expected: String?) {
        if (expected.isNullOrBlank()) return
        val actual = try {
            part.contentType?.substringBefore(";")?.trim()
        } catch (e: MessagingException) {
            null
        } ?: return
        if (!actual.equals(expected.substringBefore(";").trim(), ignoreCase = true)) {
            throw AttachmentFetchException.SourceUnavailable(
                "PART_TYPE_MISMATCH",
                "registered part content type does not match the message"
            )
        }
    }

    private fun imapProperties(port: Int): Properties =
        Properties().apply {
            put("mail.imap.connectiontimeout", properties.transferConnectTimeoutMs.toString())
            put("mail.imap.timeout", properties.transferReadTimeoutMs.toString())
            put("mail.imap.peek", "true")
            put("mail.imap.partialfetch", "true")
            // JavaMail 1.6.7 的块大小属性是 mail.imap.fetchsize（不是 fetchblocksize）；
            // 该值决定每个 BODY.PEEK[sec]<pos.count> 分块请求的字节数（64KiB 流式缓冲）。
            put("mail.imap.fetchsize", properties.transferBufferBytes.toString())
            if (port == 993) {
                put("mail.imap.ssl.enable", "true")
            }
        }

    private fun closeQuietly(store: javax.mail.Store) {
        try {
            store.close()
        } catch (e: MessagingException) {
            // 忽略：可能已被 watchdog 硬关闭
        }
    }

    companion object {
        /** 点分 1-based part 路径语法（如 2、2.1）；非法即拒。 */
        private val PART_PATH_PATTERN = Regex("^[1-9][0-9]*(\\.[1-9][0-9]*)*$")

        fun parsePartPath(partPath: String): List<Int> {
            require(PART_PATH_PATTERN.matches(partPath)) {
                "part_path must be a dot-separated 1-based ASCII part path, got: $partPath"
            }
            return partPath.split(".").map { it.toInt() }
        }
    }
}

/**
 * 已打开的目标 part。由解析线程持有并在 finally 优雅关闭；watchdog 线程在
 * 总时限/租约丢失时调用 [forceClose] 硬关闭（IMAPFolder.forceClose ->
 * protocol.disconnect -> socket.close），让阻塞的流式读立即以 IO 异常结束。
 */
private class ImapResolvedPart(
    private val folder: Folder,
    private val store: javax.mail.Store,
    private val part: Part,
    private val log: org.slf4j.Logger
) : ResolvedAttachmentPart {

    override fun streamContent(
        sink: OutputStream,
        maxBytes: Long,
        abortReason: () -> String?
    ): Long {
        val input: InputStream = try {
            part.inputStream
        } catch (e: MessagingException) {
            throw mapAcquisitionFailure(e, abortReason())
        }
        input.use {
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val reason = abortReason()
                if (reason != null) {
                    throw AttachmentFetchException.Aborted(reason, "transfer aborted by caller")
                }
                val n = try {
                    input.read(buffer)
                } catch (e: IOException) {
                    throw mapReadFailure(e, abortReason())
                }
                if (n < 0) break
                if (maxBytes - total < n) {
                    throw AttachmentFetchException.LimitExceeded(
                        "download exceeds the configured per-file byte limit"
                    )
                }
                try {
                    sink.write(buffer, 0, n)
                } catch (e: IOException) {
                    // 本地写入失败由调用方按存储错误处理
                    throw e
                }
                total += n
            }
            return total
        }
    }

    private fun mapAcquisitionFailure(e: MessagingException, abort: String?): AttachmentFetchException {
        if (abort != null) {
            return AttachmentFetchException.Aborted(
                abort,
                "transfer aborted while opening the source part"
            )
        }
        log.info("IMAP part stream could not be opened before download completed", e)
        return AttachmentFetchException.TransferFailed(
            "TIMEOUT",
            "source part could not be opened for download"
        )
    }

    private fun mapReadFailure(e: IOException, abort: String?): AttachmentFetchException {
        if (abort != null) {
            return AttachmentFetchException.Aborted(
                abort,
                "transfer aborted while reading the source part"
            )
        }
        log.info("IMAP content read interrupted before download completed", e)
        return AttachmentFetchException.TransferFailed(
            "TIMEOUT",
            "source connection interrupted while downloading"
        )
    }

    override fun close() {
        try {
            folder.close(false)
        } catch (e: MessagingException) {
            // 已被 watchdog 硬关闭时忽略，直接收尾 store
        }
        try {
            store.close()
        } catch (e: MessagingException) {
            try {
                forceClose()
            } catch (_: MessagingException) {
                // 已关闭
            }
        }
    }

    override fun forceClose() {
        (folder as? IMAPFolder)?.forceClose()
    }
}
