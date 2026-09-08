package com.weibo.talentintroduction.mail.service

import com.sun.mail.imap.IMAPFolder
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility

@Service
class ImapMailReceiveService(
    private val properties: MailAttachmentStorageProperties = MailAttachmentStorageProperties()
) : MailReceiveService {
    private val log = LoggerFactory.getLogger(ImapMailReceiveService::class.java)

    override fun fetchInboundSince(
        account: MailSenderAccount,
        afterUid: Long,
        maxMessages: Int
    ): InboundFetchResult {
        require(maxMessages in 1..100) { "maxMessages must be between 1 and 100" }
        require(afterUid >= 0) { "afterUid must be non-negative" }

        // I-2：每次账号接收建立独立 deadline（120s 默认）；到期 watchdog forceClose 该连接，
        // 阻塞中的读立即以异常结束，finally 清理，失败由调用方记 FAILED 后继续下一账号。
        return withAccountReceiveWindow(account) { folder, budget ->
            val uidFolder = folder as? UIDFolder
                ?: error("IMAP INBOX does not support UID lookup")
            val uidValidity = uidFolder.uidValidity
            val startUid = if (afterUid == 0L) 1L else afterUid + 1
            val candidates = uidFolder.getMessagesByUID(startUid, UIDFolder.LASTUID)
                .asSequence()
                .mapNotNull { message ->
                    val uid = uidFolder.getUID(message)
                    if (uid <= afterUid) null else message to uid
                }
                .sortedBy { it.second }
                .take(maxMessages)
                .toList()
            // I-2：只预取 ENVELOPE/CONTENT_INFO(BODYSTRUCTURE)/UID 及必要头，不预取 MESSAGE 全内容。
            if (candidates.isNotEmpty()) {
                budget.check()
                val profile = FetchProfile()
                profile.add(FetchProfile.Item.ENVELOPE)
                profile.add(FetchProfile.Item.CONTENT_INFO)
                profile.add(UIDFolder.FetchProfileItem.UID)
                folder.fetch(candidates.map { it.first }.toTypedArray(), profile)
            }
            budget.check()
            val messages = candidates.map { (message, uid) ->
                budget.check()
                convertToReceivedMail(message, account, folder.name, uidValidity, uid)
            }
            // 连接已被 watchdog 硬关但单信转换吞掉了 IO 异常时，这里按窗口超时显式失败，
            // 绝不让「读到空正文」冒充一次成功接收。
            budget.check()
            InboundFetchResult(
                mails = messages,
                uidValidity = uidValidity,
                maxUidInWindow = messages.maxOfOrNull { it.imapUid } ?: afterUid
            )
        }
    }

    override fun fetchByUids(account: MailSenderAccount, uids: List<Long>): List<ReceivedMail> {
        require(uids.isNotEmpty()) { "uids must not be empty" }
        require(uids.all { it > 0 }) { "each uid must be positive" }

        // I-2：回补读取同样受单账号接收窗口约束（连接/头/正文目录），到期强制断开。
        return withAccountReceiveWindow(account) { folder, budget ->
            val uidFolder = folder as? UIDFolder
                ?: error("IMAP INBOX does not support UID lookup")
            val messages = uidFolder.getMessagesByUID(uids.toLongArray())
                .filterNotNull()
            // I-2：只预取 ENVELOPE/CONTENT_INFO(BODYSTRUCTURE)/UID 及必要头，不预取 MESSAGE 全内容。
            if (messages.isNotEmpty()) {
                budget.check()
                val profile = FetchProfile()
                profile.add(FetchProfile.Item.ENVELOPE)
                profile.add(FetchProfile.Item.CONTENT_INFO)
                profile.add(UIDFolder.FetchProfileItem.UID)
                folder.fetch(messages.toTypedArray(), profile)
            }
            budget.check()
            val byUid = messages.associateBy { uidFolder.getUID(it) }
            val result = uids.mapNotNull { byUid[it] }.map { msg ->
                budget.check()
                convertToReceivedMail(msg, account, folder.name, uidFolder.uidValidity, uidFolder.getUID(msg))
            }
            // 与 fetchInboundSince 一致：窗口超时后的转换不得冒充成功。
            budget.check()
            result
        }
    }

    /**
     * Fetches UNSEEN inbox messages as detached [MimeMessage] copies so callers can parse
     * multipart DSN content after the IMAP connection closes.
     */
    fun fetchUnseenMessages(account: MailSenderAccount, maxMessages: Int = 100): List<MimeMessage> {
        require(maxMessages in 1..100) { "maxMessages must be between 1 and 100" }

        val session = Session.getInstance(imapProperties(account.imapPort))
        val store = session.getStore("imap")
        store.connect(account.imapHost, account.imapPort, account.imapUsername, account.imapPassword)

        return store.use { connectedStore ->
            val inbox = connectedStore.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            inbox.use { folder ->
                folder.messages
                    .asSequence()
                    .filterNot { it.flags.contains(Flags.Flag.SEEN) }
                    .take(maxMessages)
                    .map { message -> MimeMessage(message as MimeMessage) }
                    .toList()
            }
        }
    }

    // ------------------------------------------------------------------
    // 单账号接收窗口（I-2）：连接/头/正文目录共用一次 deadline；到期 watchdog
    // 真关闭连接（IMAPFolder.forceClose），阻塞读立即失败；finally 收尾清理。
    // 只约束 IMAP 接收阶段，绝不中断已经开始处理的业务/SMTP 事务。
    // ------------------------------------------------------------------

    /**
     * 打开 INBOX 并在 [properties.accountReceiveTimeoutSeconds] 账号预算内执行 [block]。
     * 预算耗尽：watchdog forceClose 连接；[block] 内 [AccountReceiveBudget.check] 抛
     * [MetadataTimeoutException]（可重试）；finally 取消 watchdog 并清理连接。
     * 异常/超时路径不再等待服务端响应（先硬关再收尾），避免慢服务端拖住清理。
     */
    private fun <T> withAccountReceiveWindow(
        account: MailSenderAccount,
        block: (folder: Folder, budget: AccountReceiveBudget) -> T
    ): T {
        require(properties.accountReceiveTimeoutSeconds > 0) {
            "accountReceiveTimeoutSeconds must be positive"
        }
        val session = Session.getInstance(imapProperties(account.imapPort))
        val store = session.getStore("imap")
        store.connect(account.imapHost, account.imapPort, account.imapUsername, account.imapPassword)
        val folder = store.getFolder("INBOX")
        folder.open(Folder.READ_WRITE)
        val budget = AccountReceiveBudget(account.accountCode, properties.accountReceiveTimeoutSeconds)
        budget.arm {
            // 预算到点：真关闭连接，打断阻塞中的读。实测 JavaMail 1.6.x 的
            // IMAPFolder.forceClose()/protocol.disconnect() 都会等待在途 literal 读完成
            // （fixture 滴流下约等于整个响应的发送时长），因此先直接关底层 socket。
            hardCloseFolder(folder)
        }
        try {
            return block(folder, budget)
        } finally {
            budget.cancel()
            try {
                if (budget.isExpired()) {
                    // 超时路径：优雅 CLOSE 会等慢服务端应答，直接硬关。
                    hardCloseFolder(folder)
                } else if (folder.isOpen) {
                    folder.close(false)
                }
            } catch (e: Exception) {
                // 连接可能已被 watchdog 硬关闭
            }
            try {
                if (store.isConnected) {
                    store.close()
                }
            } catch (e: Exception) {
                runCatching { hardCloseFolder(folder) }
            }
        }
    }

    /**
     * 硬关闭账号接收连接：先反射关闭 IMAP 协议底层 socket（打断在途阻塞读），
     * 再调用公开 [IMAPFolder.forceClose] 收尾。全部 runCatching——关闭是尽力而为，
     * 后续 folder/store 清理各自独立容错。
     */
    private fun hardCloseFolder(folder: Folder) {
        val imapFolder = folder as? IMAPFolder ?: return
        runCatching {
            val protocol = findDeclaredField(imapFolder.javaClass, "protocol")
                ?.let { field ->
                    field.isAccessible = true
                    field.get(imapFolder)
                }
                ?: return@runCatching
            val socket = findDeclaredField(protocol.javaClass, "socket")
                ?.let { field ->
                    field.isAccessible = true
                    field.get(protocol) as? java.net.Socket
                }
            socket?.close()
        }
        runCatching { imapFolder.forceClose() }
    }

    private fun findDeclaredField(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    /** 单账号接收窗口的 deadline/check/watchdog 句柄（每次读取独立建立）。 */
    private class AccountReceiveBudget(
        private val accountCode: String,
        timeoutSeconds: Long
    ) {
        private val deadlineNanos: Long = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        private val timeoutSeconds = timeoutSeconds

        @Volatile
        private var watchdogFuture: ScheduledFuture<*>? = null

        /** 在 deadline 到点时调用 [closer]（watchdog forceClose 连接）。 */
        fun arm(closer: () -> Unit) {
            val remainingNanos = deadlineNanos - System.nanoTime()
            watchdogFuture = WATCHDOG_EXECUTOR.schedule(
                { closer() },
                remainingNanos.coerceAtLeast(0L),
                TimeUnit.NANOSECONDS
            )
        }

        fun isExpired(): Boolean = System.nanoTime() > deadlineNanos

        fun check() {
            if (isExpired()) {
                throw MetadataTimeoutException(
                    "IMAP receive window exceeded the $timeoutSeconds" +
                        "s account budget (account=$accountCode)"
                )
            }
        }

        fun cancel() {
            watchdogFuture?.cancel(false)
            watchdogFuture = null
        }
    }

    override fun markSeen(account: MailSenderAccount, imapUid: Long) {
        val session = Session.getInstance(imapProperties(account.imapPort))
        val store = session.getStore("imap")
        store.connect(account.imapHost, account.imapPort, account.imapUsername, account.imapPassword)

        store.use { connectedStore ->
            val inbox = connectedStore.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            inbox.use { folder ->
                val uidFolder = folder as? UIDFolder
                    ?: error("IMAP INBOX does not support UID lookup")
                uidFolder.getMessageByUID(imapUid)?.setFlag(Flags.Flag.SEEN, true)
            }
        }
    }

    // ------------------------------------------------------------------
    // Message -> ReceivedMail 转换（fetchInboundSince/fetchByUids 共用解析）
    // ------------------------------------------------------------------

    internal fun convertToReceivedMail(
        message: Message,
        account: MailSenderAccount,
        folder: String,
        uidValidity: Long,
        imapUid: Long
    ): ReceivedMail {
        val deadlineNanos = System.nanoTime() +
            TimeUnit.SECONDS.toNanos(properties.metadataTotalTimeoutSeconds)
        val headers = fetchEnvelopeHeaders(message)
        val context = MailSourceContext(
            accountCode = account.accountCode,
            folder = folder,
            uidValidity = uidValidity,
            uid = imapUid,
            messageId = headers.messageId
        )
        val extracted = walkContent(message, context, deadlineNanos)
        return ReceivedMail(
            imapUid = imapUid,
            from = extractFromHeader(headers.from) ?: extractFromEnvelope(message),
            subject = headers.subject,
            body = extracted.bodyText,
            messageId = headers.messageId,
            inReplyTo = headers.inReplyTo,
            receivedAt = message.receivedDate
                ?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDateTime()
                ?: LocalDateTime.now(),
            attachments = extracted.attachments,
            uidValidity = uidValidity,
            bodyTruncated = extracted.bodyTruncated
        )
    }

    /** 仅读头字段（trigger 时 JavaMail 只拉 HEADER.FIELDS，不是全量 MESSAGE 预取）。 */
    private data class EnvelopeHeaders(
        val subject: String?,
        val from: String?,
        val messageId: String?,
        val inReplyTo: String?
    )

    private fun fetchEnvelopeHeaders(message: Message): EnvelopeHeaders =
        EnvelopeHeaders(
            subject = message.getHeader("Subject")?.firstOrNull(),
            from = message.getHeader("From")?.firstOrNull(),
            messageId = message.getHeader("Message-ID")?.firstOrNull(),
            inReplyTo = message.getHeader("In-Reply-To")?.firstOrNull()
        )

    private fun extractFromHeader(header: String?): String? {
        if (header.isNullOrBlank()) return null
        return runCatching { InternetAddress.parse(header, false).firstOrNull()?.address }
            .getOrNull()?.takeIf { !it.isNullOrBlank() } ?: header
    }

    private fun extractFromEnvelope(message: Message): String =
        message.from
            ?.filterIsInstance<InternetAddress>()
            ?.firstOrNull()
            ?.address
            ?: error("Received mail has no sender address")

    // ------------------------------------------------------------------
    // MIME 白名单遍历（I-2 / I-3）
    //
    // 先按 filename/disposition 排除附件，再按 MIME 类型走 multipart 容器与
    // 合法正文；附件（含带文件名的 text/*、嵌套 message/rfc822）绝不
    // getInputStream/getContent——metadataOnly 只登记名称/定位，legacy 才读字节。
    // ------------------------------------------------------------------

    internal class MailSourceContext(
        val accountCode: String,
        val folder: String,
        val uidValidity: Long,
        val uid: Long,
        val messageId: String?
    )

    internal class WalkResult(
        val bodyText: String,
        val bodyTruncated: Boolean,
        val attachments: List<ReceivedMailAttachment>
    )

    internal fun walkContent(part: Part, context: MailSourceContext, deadlineNanos: Long): WalkResult {
        val counter = NodeCounter()
        val walk = walk(part, context, "", properties.metadataOnly, deadlineNanos, counter)
        return WalkResult(
            bodyText = walk.bodyText,
            bodyTruncated = walk.bodyTruncated,
            attachments = walk.attachments
        )
    }

    private class NodeCounter {
        var count = 0
    }

    /** 一个节点的遍历结果：正文文本 / 是否截断 / 已登记附件。 */
    private class NodeResult(
        val bodyText: String,
        val bodyTruncated: Boolean,
        val attachments: List<ReceivedMailAttachment>
    )

    private fun checkDeadline(deadlineNanos: Long) {
        if (System.nanoTime() > deadlineNanos) {
            throw MetadataTimeoutException("IMAP metadata fetch exceeded the configured total time limit")
        }
    }

    /**
     * 递归白名单遍历：multipart 容器 -> 合法正文叶/DSN 机器段/嵌套消息；
     * 附件在叶层按 filename/disposition 排除并（视模式）登记。
     */
    private fun walk(
        part: Part,
        context: MailSourceContext,
        pathPrefix: String,
        metadataOnly: Boolean,
        deadlineNanos: Long,
        counter: NodeCounter
    ): NodeResult {
        counter.count++
        if (counter.count > properties.metadataMaxMimeNodes) {
            throw MetadataStructureLimitException(
                "MIME structure exceeds the configured node limit of ${properties.metadataMaxMimeNodes}"
            )
        }
        checkDeadline(deadlineNanos)

        val contentType = runCatching { part.contentType }.getOrNull()
            ?.substringBefore(";")?.trim()?.lowercase() ?: return NodeResult("", false, emptyList())
        val isMultipart = contentType.startsWith("multipart/")

        if (isMultipart) {
            val multipart = part.content as? Multipart ?: return NodeResult("", false, emptyList())
            val joinAll = !part.isMimeType("multipart/alternative")
            val segmentTexts = ArrayList<String>()
            var anyTruncated = false
            val allAttachments = ArrayList<ReceivedMailAttachment>()
            for (i in 0 until multipart.count) {
                val child = multipart.getBodyPart(i)
                val childPath = if (pathPrefix.isEmpty()) (i + 1).toString() else "$pathPrefix.${i + 1}"
                val childResult = walk(child, context, childPath, metadataOnly, deadlineNanos, counter)
                if (childResult.bodyText.isNotBlank()) {
                    segmentTexts.add(childResult.bodyText)
                }
                anyTruncated = anyTruncated || childResult.bodyTruncated
                allAttachments.addAll(childResult.attachments)
            }
            // alternative 各分段是同一内容的多种表现 → 取首个非空；
            // mixed/report/related 等各分段是不同内容 → 按顺序拼接
            val chosen = if (joinAll) {
                segmentTexts.joinToString("\n")
            } else {
                segmentTexts.firstOrNull().orEmpty()
            }
            return NodeResult(chosen, anyTruncated, allAttachments)
        }

        // 附件叶：metadataOnly 只登记；legacy 读字节（含带文件名的 text/* 附件）。
        if (isAttachmentLeaf(part, contentType)) {
            return NodeResult("", false, listOf(registerAttachment(part, context, pathPrefix, metadataOnly)))
        }

        val isText = contentType == "text/plain" || contentType == "text/html"
        val isDeliveryStatus = contentType == "message/delivery-status"
        if (isText || isDeliveryStatus) {
            val read = readBoundedText(part, metadataOnly, deadlineNanos)
            val text = if (contentType == "text/html") stripHtml(read.text) else read.text
            return NodeResult(text, read.truncated, emptyList())
        }

        // 其余 message/*（rfc822/global/…）：嵌套消息的内容整体只作为「正文源」
        // 走有界读（等效旧的 readPartAsText 兜底），从不把内部附件拆出来。
        return NodeResult(readBoundedText(part, metadataOnly, deadlineNanos).let { it.text }, false, emptyList())
    }

    /**
     * 附件判定（I-2）：带文件名即附件（text/plain|text/html 带文件名同样不是正文）；
     * 无文件名时 disposition=ATTACHMENT 登记为“未命名附件-{partPath}”，
     * disposition=INLINE 无文件名（内联签名图等）不构成专家材料。
     */
    private fun isAttachmentLeaf(part: Part, contentType: String): Boolean {
        val rawName = part.fileName
        if (!rawName.isNullOrBlank()) {
            return true
        }
        val isText = contentType == "text/plain" || contentType == "text/html"
        if (isText) {
            // 无文件名 text 叶永远不是附件：正文候选或忽略
            return false
        }
        val disposition = part.disposition
        return Part.ATTACHMENT.equals(disposition, ignoreCase = true)
    }

    private fun registerAttachment(
        part: Part,
        context: MailSourceContext,
        partPath: String,
        metadataOnly: Boolean
    ): ReceivedMailAttachment {
        val rawName = part.fileName
        val fileName = if (rawName.isNullOrBlank()) {
            "未命名附件-$partPath"
        } else {
            runCatching { MimeUtility.decodeText(rawName) }.getOrElse { rawName }
        }
        val contentType = runCatching { part.contentType }.getOrNull()?.substringBefore(";")?.trim()
        val disposition = runCatching { part.disposition }.getOrNull()
        val encodedSize = runCatching { part.size }.getOrNull()?.takeIf { it >= 0 }?.toLong()
        return ReceivedMailAttachment(
            fileName = fileName,
            contentType = contentType,
            content = if (metadataOnly) {
                null // I-1：metadata 模式 content 严格为 null
            } else {
                runCatching { part.inputStream.use { it.readBytes() } }.getOrElse {
                    log.warn("Failed to read attachment content for {}", fileName)
                    ByteArray(0)
                }
            },
            source = ImapAttachmentSource(
                accountCode = context.accountCode,
                folder = context.folder,
                uidValidity = context.uidValidity,
                uid = context.uid,
                partPath = partPath,
                messageId = context.messageId,
                encodedSize = encodedSize,
                disposition = disposition
            )
        )
    }

    /** 有界正文读取结果。 */
    private class BoundedText(val text: String, val truncated: Boolean)

    /**
     * 正文读取（I-3）：字节读取期间即按上限截断（metadataOnly 上限
     * metadataMaxBodyBytes；legacy 无上限）；绝不在 getContent 拿完整
     * String 后再截断。text/plain、text/html、DSN machine 段共用。
     */
    private fun readBoundedText(part: Part, metadataOnly: Boolean, deadlineNanos: Long): BoundedText {
        checkDeadline(deadlineNanos)
        val maxBytes = if (metadataOnly) properties.metadataMaxBodyBytes else Int.MAX_VALUE
        return try {
            val stream = part.inputStream
            if (stream == null) {
                BoundedText("", false)
            } else {
                stream.use { readBoundedFromStream(it, maxBytes, deadlineNanos) }
            }
        } catch (e: MetadataTimeoutException) {
            // 元数据预算到点：明确可重试失败，不能吞成空正文
            throw e
        } catch (e: MetadataStructureLimitException) {
            throw e
        } catch (_: Exception) {
            BoundedText("", false)
        }
    }

    private fun readBoundedFromStream(
        stream: InputStream,
        maxBytes: Int,
        deadlineNanos: Long
    ): BoundedText {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        var total = 0
        var truncated = false
        while (true) {
            checkDeadline(deadlineNanos)
            val n = stream.read(buffer)
            checkDeadline(deadlineNanos)
            if (n < 0) break
            if (maxBytes - total < n) {
                val allowed = (maxBytes - total).coerceAtLeast(0)
                if (allowed > 0) {
                    output.write(buffer, 0, allowed)
                }
                truncated = true
                break
            }
            output.write(buffer, 0, n)
            total += n
        }
        return BoundedText(output.toString(Charsets.UTF_8), truncated)
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun imapProperties(port: Int): Properties =
        Properties().apply {
            put("mail.imap.connectiontimeout", "10000")
            put("mail.imap.timeout", "10000")
            put("mail.imap.peek", "true")
            if (port == 993) {
                put("mail.imap.ssl.enable", "true")
            }
        }

    companion object {
        /** 单账号接收窗口 watchdog：到点 forceClose 对应连接（daemon，常驻轻量）。 */
        private val WATCHDOG_EXECUTOR: ScheduledExecutorService =
            Executors.newScheduledThreadPool(2) { runnable ->
                Thread(runnable, "imap-receive-watchdog").apply { isDaemon = true }
            }
    }
}

private inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        close()
    }
}
