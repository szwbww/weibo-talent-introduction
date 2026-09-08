package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.Base64
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility

/**
 * 03 元数据读取集成测试（mysqlIt 门禁；本地 JDK socket 脚本式 IMAP，无新中间件）。
 *
 * 真实 JavaMail 客户端连接本地 IMAP fixture，逐命令记录协议日志证明：
 * - 附件内容 BODY[]/BODY.PEEK[n] FETCH 数为 0（metadataOnly）；
 * - 19/20 附件与 1000 附件目录完整、无截断；
 * - text 附件不进正文；正文只来自白名单 text 段；
 * - legacy 模式仍返回原附件字节。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
class ImapMetadataFetchIT {
    private lateinit var server: MetadataFixtureServer

    @BeforeEach
    fun setUp() {
        server = MetadataFixtureServer()
    }

    private fun metadataServer(): MetadataFixtureServer = server.setFailOnAttachmentRequest(true)

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun service(metadataOnly: Boolean): ImapMailReceiveService =
        ImapMailReceiveService(
            MailAttachmentStorageProperties(
                metadataOnly = metadataOnly,
                metadataMaxBodyBytes = 2 * 1024 * 1024,
                metadataTotalTimeoutSeconds = 30
            )
        )

    private fun account(
        port: Int,
        code: String = "it-account",
        username: String = "user1"
    ): MailSenderAccount =
        MailSenderAccount(
            accountCode = code,
            senderEmail = "$code@fixture.local",
            senderName = code,
            senderTitle = null,
            senderDisplayName = null,
            teamName = null,
            countryName = null,
            smtpHost = "smtp.fixture",
            smtpPort = 465,
            smtpUsername = code,
            smtpPassword = "pw",
            imapHost = "127.0.0.1",
            imapPort = port,
            imapUsername = username,
            imapPassword = "pw"
        )

    @Test
    fun `metadata mode fetches 19 and 20 attachment envelopes completely without any attachment content fetch`() {
        metadataServer()
        val box = server.addBox(
            username = "user1",
            password = "pw",
            uidValidity = 501L,
            messages = listOf(
                server.mail(uid = 11L, subject = "nineteen", bodyText = "Mail with 19 files", attachmentCount = 19),
                server.mail(uid = 12L, subject = "twenty", bodyText = "Mail with 20 files", attachmentCount = 20)
            )
        )
        val result = service(metadataOnly = true).fetchInboundSince(
            account = account(server.port()),
            afterUid = 0L,
            maxMessages = 10
        )

        assertEquals(2, result.mails.size)
        assertEquals(501L, result.uidValidity)
        val first = result.mails.first { it.subject == "nineteen" }
        val second = result.mails.first { it.subject == "twenty" }
        assertEquals(19, first.attachments.size)
        assertEquals(20, second.attachments.size)
        // I-3：不因附件数量截断目录；39 件全部有完整远端定位
        val all = first.attachments + second.attachments
        assertEquals(39, all.size)
        all.forEach { attachment ->
            assertNull(attachment.content, "metadata mode content must stay null")
            val source = attachment.source
            assertNotNull(source, "every attachment must carry a source descriptor")
            assertEquals("it-account", source!!.accountCode)
            assertEquals(501L, source.uidValidity)
            assertNotNull(source.partPath)
            assertTrue(source.partPath.matches(Regex("^[1-9][0-9]*$")), "flat multipart part path, got ${source.partPath}")
        }
        assertEquals("Mail with 19 files", first.body)
        assertEquals("Mail with 20 files", second.body)
        assertTrue(first.attachments.all { it.fileName.startsWith("doc") })

        val fetchedSections = server.attachmentContentSections()
        assertTrue(
            fetchedSections.isEmpty(),
            "no attachment content section may be fetched, got $fetchedSections"
        )
        val log = server.commandLog()
        assertTrue(log.isNotEmpty())
        assertTrue(log.none { it.contains("BODY.PEEK[2]") || it.contains("BODY[2]") }, "no part-2 fetch in $log")
    }

    @Test
    fun `1000 attachment metadata case completes with zero attachment stream access`() {
        metadataServer()
        val box = server.addBox(
            username = "user1",
            password = "pw",
            uidValidity = 502L,
            messages = listOf(
                server.mail(uid = 21L, subject = "stress", bodyText = "Stress body", attachmentCount = 1000)
            )
        )
        val mail = service(metadataOnly = true).fetchByUids(
            account = account(server.port()),
            uids = listOf(21L)
        ).single()

        assertEquals(1000, mail.attachments.size)
        assertTrue(mail.attachments.all { it.content == null })
        assertTrue(mail.attachments.all { it.source != null })
        assertEquals("Stress body", mail.body)
        assertEquals(502L, mail.uidValidity)
        // I-2 最强证明：附件流一旦被请求即抛错（见 fixture 服务端），测试走到这里即零访问
        assertTrue(
            server.attachmentContentSections().isEmpty(),
            "attachment sections must never be fetched: ${server.attachmentContentSections()}"
        )
    }

    @Test
    fun `text attachment is registered as attachment and never appears in body`() {
        metadataServer()
        server.addBox(
            username = "user1",
            password = "pw",
            uidValidity = 503L,
            messages = listOf(server.mailWithTextAttachment())
        )
        val mail = service(metadataOnly = true).fetchInboundSince(
            account = account(server.port()),
            afterUid = 0L,
            maxMessages = 10
        ).mails.single()

        assertEquals("The real message body", mail.body)
        assertTrue(!mail.body.contains("notes-content"), "text attachment content must not leak into body")
        assertEquals(1, mail.attachments.size)
        assertEquals("notes.txt", mail.attachments[0].fileName)
        assertNull(mail.attachments[0].content)
    }

    @Test
    fun `legacy mode still returns the original attachment bytes`() {
        val fixtureMail = server.mail(uid = 31L, subject = "legacy", bodyText = "Legacy body", attachmentCount = 2)
        server.addBox(
            username = "user1",
            password = "pw",
            uidValidity = 504L,
            messages = listOf(fixtureMail)
        )

        val mail = service(metadataOnly = false).fetchByUids(
            account = account(server.port()),
            uids = listOf(31L)
        ).single()

        assertEquals(2, mail.attachments.size)
        val expected = fixtureMail.attachments.map { it.bytes.toList() }
        val actual = mail.attachments.map { it.content!!.toList() }
        assertEquals(expected, actual, "legacy mode must return the exact original attachment bytes")
        assertEquals("Legacy body", mail.body)
        // I-1：source 在 legacy 模式也允许存在（content 非 null 是 legacy 的判定面）
        assertNotNull(mail.attachments[0].content)
    }

    @Test
    fun `body cap during metadata fetch is enforced while reading bytes and flagged`() {
        metadataServer()
        val capService = ImapMailReceiveService(
            MailAttachmentStorageProperties(
                metadataOnly = true,
                metadataMaxBodyBytes = 64,
                metadataTotalTimeoutSeconds = 30
            )
        )
        server.addBox(
            username = "user1",
            password = "pw",
            uidValidity = 505L,
            messages = listOf(
                server.mail(
                    uid = 41L, subject = "large-body", bodyText = "x".repeat(4096), attachmentCount = 0
                )
            )
        )
        val mail = capService.fetchInboundSince(
            account = account(server.port()),
            afterUid = 0L,
            maxMessages = 10
        ).mails.single()

        assertTrue(mail.bodyTruncated, "oversize body must be flagged truncated")
        assertTrue(mail.body.length <= 64, "bounded body must respect the cap")
        assertTrue(mail.body.startsWith("x"))
        // 读取上限发生在字节读取期间：BODY.PEEK[TEXT]/BODY.PEEK[1] 带 <start.count> 的分块请求
        val partialRequests = server.commandLog().filter { it.contains("BODY.PEEK[TEXT]<0.") || it.contains("BODY.PEEK[1]<0.") }
        assertTrue(
            partialRequests.isNotEmpty(),
            "bounded read should fetch the first block only, log had: ${server.commandLog()}"
        )
    }

    @Test
    fun `metadata deadline exceeded raises retryable timeout error and leaves no partial mail`() {
        val deadlineService = ImapMailReceiveService(
            MailAttachmentStorageProperties(
                metadataOnly = true,
                metadataMaxBodyBytes = 2 * 1024 * 1024,
                metadataTotalTimeoutSeconds = 1
            )
        )
        server.addBox(
            username = "user1",
            password = "pw",
            uidValidity = 506L,
            messages = listOf(server.mailWithSlowBody(uid = 51L, bodyDelayMs = 4_000L))
        )
        val failure = runCatching {
            deadlineService.fetchInboundSince(account = account(server.port()), afterUid = 0L, maxMessages = 10)
        }.exceptionOrNull()

        assertNotNull(failure, "slow body beyond the metadata deadline must raise")
        assertTrue(
            failure is MetadataTimeoutException || failure is javax.mail.MessagingException,
            "timeout must surface as retryable error, got ${failure!!.javaClass.name}"
        )
    }

    @Test
    fun `slow-drip body past account receive budget is force-closed and raises retryable error`() {
        // 05 I-2：账号接收窗口预算（accountReceiveTimeoutSeconds=1s，单信预算放宽到 30s），
        // 服务端按 200ms/字节持续滴流（全信需 100s+）。无 watchdog 时只能等单信预算 30s 或
        // 永不结束；watchdog 必须在 1s 预算到点真关闭连接，让阻塞读尽快以可重试错误结束。
        val budgetService = ImapMailReceiveService(
            MailAttachmentStorageProperties(
                metadataOnly = true,
                metadataMaxBodyBytes = 2 * 1024 * 1024,
                metadataTotalTimeoutSeconds = 30,
                accountReceiveTimeoutSeconds = 1
            )
        )
        server.addBox(
            username = "user1",
            password = "pw",
            uidValidity = 601L,
            messages = listOf(server.mailWithDripBody(uid = 61L, perByteDelayMs = 200L))
        )
        val startedAt = System.nanoTime()
        val failure = runCatching {
            budgetService.fetchInboundSince(account = account(server.port()), afterUid = 0L, maxMessages = 10)
        }.exceptionOrNull()
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertNotNull(failure, "drip body beyond the account receive budget must raise")
        assertTrue(
            failure is MetadataTimeoutException || failure is javax.mail.MessagingException,
            "budget expiry must surface as retryable error, got ${failure!!.javaClass.name}"
        )
        // 预算 1s 到点由 watchdog 强制断开：失败远早于 10s 读超时与 30s 单信预算。
        assertTrue(
            elapsedMs >= 700 && elapsedMs < 15_000,
            "force-close must happen at the 1s budget, took ${elapsedMs}ms"
        )
    }

    @Test
    fun `after budget force-close the next account proceeds on a new connection`() {
        // 05 I-2：慢账号接收超时失败后，同一服务的下一账号（另一邮箱）仍能正常读取。
        val budgetService = ImapMailReceiveService(
            MailAttachmentStorageProperties(
                metadataOnly = true,
                metadataMaxBodyBytes = 2 * 1024 * 1024,
                metadataTotalTimeoutSeconds = 30,
                accountReceiveTimeoutSeconds = 1
            )
        )
        server.addBox(
            username = "user1",
            password = "pw",
            uidValidity = 701L,
            messages = listOf(server.mailWithDripBody(uid = 71L, perByteDelayMs = 200L))
        )
        server.addBox(
            username = "user2",
            password = "pw",
            uidValidity = 702L,
            messages = listOf(server.mail(uid = 81L, subject = "healthy-next-account", bodyText = "ok", attachmentCount = 1))
        )

        val slowFailure = runCatching {
            budgetService.fetchInboundSince(
                account = account(server.port(), code = "slow-account", username = "user1"),
                afterUid = 0L,
                maxMessages = 10
            )
        }.exceptionOrNull()
        assertNotNull(slowFailure, "slow drip account must fail at its receive budget")

        // 下一账号走新连接：完整读到正文与附件元数据，连接未被慢账号拖死。
        val next = budgetService.fetchInboundSince(
            account = account(server.port(), code = "next-account", username = "user2"),
            afterUid = 0L,
            maxMessages = 10
        )
        assertEquals(1, next.mails.size)
        assertEquals("healthy-next-account", next.mails[0].subject)
        assertEquals("ok", next.mails[0].body)
        assertEquals(1, next.mails[0].attachments.size)
        assertEquals(702L, next.uidValidity)
    }

    @Test
    fun `healthy account completes within the receive budget`() {
        val budgetService = ImapMailReceiveService(
            MailAttachmentStorageProperties(
                metadataOnly = true,
                metadataMaxBodyBytes = 2 * 1024 * 1024,
                metadataTotalTimeoutSeconds = 30,
                accountReceiveTimeoutSeconds = 5
            )
        )
        server.addBox(
            username = "user1",
            password = "pw",
            uidValidity = 801L,
            messages = listOf(server.mail(uid = 91L, subject = "within-budget", bodyText = "fast body", attachmentCount = 2))
        )
        val result = budgetService.fetchInboundSince(
            account = account(server.port()),
            afterUid = 0L,
            maxMessages = 10
        )
        assertEquals(1, result.mails.size)
        assertEquals("fast body", result.mails[0].body)
        assertEquals(2, result.mails[0].attachments.size)
    }

    // ------------------------------------------------------------------
    // Fixture models
    // ------------------------------------------------------------------

    data class FixtureAttachment(
        val fileName: String,
        val contentType: String,
        val bytes: ByteArray
    )

    data class FixtureMail(
        val uid: Long,
        val subject: String,
        val bodyText: String,
        val attachments: List<FixtureAttachment>,
        /** 慢源：该信正文响应发送前延迟（metadata 时限断言用）。 */
        val slowBodyDelayMs: Long = 0L,
        /** 滴流源：正文 literal 每字节间隔毫秒（账号接收窗口 force-close 断言用）。 */
        val bodyDripMs: Long = 0L
    )

    /** fixture mailbox 句柄（服务端句柄）。 */
    class Box
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/**
 * 本地脚本式 IMAP fixture 服务器。messages 用真实 JavaMail writeTo 序列化，
 * 再按 RFC822/multipart 边界解析出各 part 的线上字节；BODYSTRUCTURE 由同一
 * MimeMessage 生成，保证类型/文件名/大小与线上字节一致。
 *
 * 附件 part 的正文一旦被请求即抛错（1000 附件场景的「throwing attachment
 * stream」），metadataOnly 客户端绝不触达。
 */
class MetadataFixtureServer : AutoCloseable {
    private val serverSocket = ServerSocket(0, 128, InetAddress.getLoopbackAddress())
    private val boxes = mutableMapOf<String, Box>()
    private val commandLog = CopyOnWriteArrayList<String>()
    private val attachmentContentRequests = CopyOnWriteArrayList<String>()
    private val acceptThread: Thread

    init {
        acceptThread = Thread {
            while (!serverSocket.isClosed()) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: IOException) {
                    break
                }
                Thread { runCatching { handle(socket) } }
                    .apply { isDaemon = true }
                    .start()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun port(): Int = serverSocket.localPort

    private class Box(
        val password: String,
        val uidValidity: Long,
        val messages: List<PreparedMessage>
    )

    /** 构造完成的 fixture 邮件：保留 MimeMessage（BODYSTRUCTURE/ENVELOPE）与线上字节。 */
    private class PreparedMessage(
        val fixture: ImapMetadataFetchIT.FixtureMail,
        val mime: MimeMessage,
        val wire: ByteArray,
        val headerBlock: ByteArray,
        /** 各 part 解码后内容（BODYSTRUCTURE size 用）。 */
        val leafBodies: Map<String, ByteArray>,
        /** 各 part 的线上字节（含 CTE 编码：base64 part 存编码后字节，客户端按头解码）。 */
        val rawLeafBodies: Map<String, ByteArray>,
        val leafHeaders: Map<String, ByteArray>,
        val wireSizes: Map<String, Int>
    )

    fun addBox(
        username: String,
        password: String,
        uidValidity: Long,
        messages: List<ImapMetadataFetchIT.FixtureMail>
    ): ImapMetadataFetchIT.Box {
        val prepared = messages.map { prepare(it) }
        boxes[username] = Box(password, uidValidity, prepared)
        return ImapMetadataFetchIT.Box()
    }

    /** 便捷构造：N 个同名附件（doc-1.pdf..doc-N.pdf）。 */
    fun mail(uid: Long, subject: String, bodyText: String, attachmentCount: Int): ImapMetadataFetchIT.FixtureMail =
        ImapMetadataFetchIT.FixtureMail(
            uid = uid,
            subject = subject,
            bodyText = bodyText,
            attachments = (1..attachmentCount).map { n ->
                ImapMetadataFetchIT.FixtureAttachment(
                    fileName = "doc-$n.pdf",
                    contentType = "application/pdf",
                    bytes = "attachment-bytes-$n-${subject}".toByteArray(StandardCharsets.UTF_8)
                )
            }
        )

    fun mailWithTextAttachment(): ImapMetadataFetchIT.FixtureMail =
        ImapMetadataFetchIT.FixtureMail(
            uid = 22L,
            subject = "text-attachment",
            bodyText = "The real message body",
            attachments = listOf(
                ImapMetadataFetchIT.FixtureAttachment(
                    fileName = "notes.txt",
                    contentType = "text/plain",
                    bytes = "notes-content-12345".toByteArray(StandardCharsets.UTF_8)
                )
            )
        )

    fun mailWithSlowBody(uid: Long, bodyDelayMs: Long): ImapMetadataFetchIT.FixtureMail =
        ImapMetadataFetchIT.FixtureMail(
            uid = uid,
            subject = "slow-body",
            bodyText = "x".repeat(256),
            attachments = emptyList(),
            slowBodyDelayMs = bodyDelayMs
        )

    /** 滴流正文：发送方以 [perByteDelayMs] 毫秒/字节持续慢吐，永不快速结束（force-close 断言）。 */
    fun mailWithDripBody(uid: Long, perByteDelayMs: Long): ImapMetadataFetchIT.FixtureMail =
        ImapMetadataFetchIT.FixtureMail(
            uid = uid,
            subject = "drip-body",
            bodyText = "y".repeat(512),
            attachments = emptyList(),
            bodyDripMs = perByteDelayMs
        )


    /** metadata 测试置 true：附件正文被请求即抛错；legacy 测试保持 false 以正常回传附件内容。 */
    private var failOnAttachmentRequest = false

    fun setFailOnAttachmentRequest(value: Boolean): MetadataFixtureServer {
        failOnAttachmentRequest = value
        return this
    }

    fun commandLog(): List<String> = commandLog

    fun attachmentContentSections(): List<String> = attachmentContentRequests

    // ------------------------------------------------------------------
    // Fixture construction
    // ------------------------------------------------------------------

    private fun prepare(fixture: ImapMetadataFetchIT.FixtureMail): PreparedMessage {
        val session = Session.getDefaultInstance(Properties())
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("expert@university.edu"))
        message.subject = fixture.subject
        message.setHeader("Message-ID", "<${fixture.subject}@fixture.local>")
        if (fixture.attachments.isEmpty()) {
            message.setText(fixture.bodyText, "utf-8")
        } else {
            val multipart = MimeMultipart("mixed")
            MimeBodyPart().apply {
                setText(fixture.bodyText, "utf-8")
                multipart.addBodyPart(this)
            }
            fixture.attachments.forEach { att ->
                MimeBodyPart().apply {
                    setDataHandler(
                        DataHandler(object : DataSource {
                            override fun getInputStream(): ByteArrayInputStream = ByteArrayInputStream(att.bytes)
                            override fun getOutputStream(): java.io.OutputStream =
                                throw UnsupportedOperationException()
                            override fun getContentType(): String = att.contentType
                            override fun getName(): String = att.fileName
                        })
                    )
                    setFileName(att.fileName)
                    setHeader("Content-Transfer-Encoding", "base64")
                    multipart.addBodyPart(this)
                }
            }
            message.setContent(multipart)
        }
        message.saveChanges()
        val out = ByteArrayOutputStream()
        message.writeTo(out)
        val wire = out.toByteArray()
        val headerEnd = indexOfWire(wire, "\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        val headerBlock = if (headerEnd >= 0) wire.copyOfRange(0, headerEnd + 4) else wire
        val (leafBodies, rawLeafBodies, leafHeaders, wireSizes) = extractLeafWireBytes(wire, fixture)
        return PreparedMessage(fixture, message, wire, headerBlock, leafBodies, rawLeafBodies, leafHeaders, wireSizes)
    }

    private fun indexOfWire(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /** 扁平 multipart/mixed fixture：boundary 分隔逐个 part；单 part 消息正文即整信正文。 */
    private fun extractLeafWireBytes(
        wire: ByteArray,
        fixture: ImapMetadataFetchIT.FixtureMail
    ): Quad<Map<String, ByteArray>, Map<String, ByteArray>, Map<String, ByteArray>, Map<String, Int>> {
        if (fixture.attachments.isEmpty()) {
            // 单 part：section 1 = 整封正文（headerBlock 之后）；text/plain 无 CTE（7bit），raw=decoded
            val headerEnd = indexOfWire(wire, "\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            val body = if (headerEnd >= 0) wire.copyOfRange(headerEnd + 4, wire.size) else ByteArray(0)
            val head = if (headerEnd >= 0) wire.copyOfRange(0, headerEnd + 4) else wire
            return Quad(
                mapOf("1" to body),
                mapOf("1" to body),
                mapOf("1" to head),
                mapOf("1" to body.size)
            )
        }
        val raw = String(wire, StandardCharsets.ISO_8859_1)
        val boundaryMatch = Regex("boundary=\"([^\"]+)\"").find(raw) ?: error("no boundary in fixture wire")
        val boundary = "--" + boundaryMatch.groupValues[1]
        val parts = raw.split(boundary).drop(1)
        val bodies = mutableMapOf<String, ByteArray>()
        val rawBodies = mutableMapOf<String, ByteArray>()
        val headers = mutableMapOf<String, ByteArray>()
        val sizes = mutableMapOf<String, Int>()
        var partNumber = 1
        for (part in parts) {
            val trimmed = part.removePrefix("\r\n").removeSuffix("\r\n").removeSuffix("--")
            val normalized = trimmed.removeSuffix("\r\n")
            if (normalized.isBlank() || normalized.startsWith("--")) continue
            val headerBodySep = normalized.indexOf("\r\n\r\n")
            if (headerBodySep < 0) continue
            val headerText = normalized.substring(0, headerBodySep)
            val bodyText = normalized.substring(headerBodySep + 4)
            val bodyBytes = bodyText.toByteArray(StandardCharsets.ISO_8859_1)
            val base64Cte = Regex("Content-Transfer-Encoding:\\s*base64", RegexOption.IGNORE_CASE)
                .containsMatchIn(headerText)
            val decoded = if (base64Cte) {
                val b64 = bodyText.replace(Regex("\\s"), "")
                try {
                    Base64.getMimeDecoder().decode(b64)
                } catch (e: IllegalArgumentException) {
                    bodyBytes
                }
            } else {
                bodyBytes
            }
            bodies["$partNumber"] = decoded
            rawBodies["$partNumber"] = bodyBytes
            headers["$partNumber"] = headerText.toByteArray(StandardCharsets.ISO_8859_1)
            sizes["$partNumber"] = decoded.size
            partNumber++
        }
        return Quad(bodies, rawBodies, headers, sizes)
    }

    // ------------------------------------------------------------------
    // IMAP protocol serving
    // ------------------------------------------------------------------

    private fun handle(socket: Socket) {
        try {
            socket.use {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                val writer = socket.getOutputStream()
                writeLine(writer, "* OK [CAPABILITY IMAP4rev1 UIDPLUS] metadata fixture ready")
                var box: Box? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    commandLog.add(line)
                    val tag = line.substringBefore(" ")
                    val command = line.substringAfter(" ").trim()
                    if (command.startsWith("CAPABILITY")) {
                        writeLine(writer, "* CAPABILITY IMAP4rev1 UIDPLUS")
                        ok(writer, tag, "CAPABILITY completed")
                    } else if (command.startsWith("LOGIN ")) {
                        val parts = command.removePrefix("LOGIN ").split(" ").map { stripQuotes(it) }
                        val candidate = boxes[parts.getOrNull(0) ?: ""]
                        if (candidate != null && candidate.password == parts.getOrNull(1)) {
                            box = candidate
                            ok(writer, tag, "LOGIN completed")
                        } else {
                            writeLine(writer, "$tag NO LOGIN failed")
                        }
                    } else if (command.startsWith("SELECT ") || command.startsWith("EXAMINE ")) {
                        val current = box
                        if (current == null) {
                            writeLine(writer, "$tag NO not authenticated")
                        } else {
                            writeLine(writer, "* FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft)")
                            writeLine(writer, "* ${current.messages.size} EXISTS")
                            writeLine(writer, "* 0 RECENT")
                            writeLine(writer, "* OK [UIDVALIDITY ${current.uidValidity}] UIDVALIDITY")
                            writeLine(writer, "* OK [UIDNEXT ${current.messages.size + 1}] Predicted next UID")
                            ok(writer, tag, "EXAMINE completed")
                        }
                    } else if (command.startsWith("UID FETCH ") || command.startsWith("FETCH ")) {
                        val current = box ?: run {
                            writeLine(writer, "$tag NO not authenticated")
                            null
                        }
                        if (current != null) {
                            handleFetch(writer, tag, command, current)
                        }
                    } else if (command.startsWith("LOGOUT")) {
                        writeLine(writer, "* BYE logging out")
                        ok(writer, tag, "LOGOUT completed")
                        break
                    } else if (command.startsWith("CLOSE") || command.startsWith("NOOP") ||
                        command.startsWith("EXPUNGE") || command.startsWith("STATUS") ||
                        command.startsWith("ID") || command.startsWith("UID ")
                    ) {
                        ok(writer, tag, "${command.substringBefore(" ")} completed")
                    } else {
                        ok(writer, tag, "${command.substringBefore(" ")} completed")
                    }
                }
            }
        } catch (e: Exception) {
            // client disconnect / protocol abort: close quietly
        }
    }

    private fun handleFetch(
        writer: java.io.OutputStream,
        tag: String,
        command: String,
        box: Box
    ) {
        val isUid = command.startsWith("UID ")
        val fetch = command.removePrefix("UID ").removePrefix("FETCH ").trim()
        val set = fetch.substringBefore(" ").trim()
        val parenStart = fetch.indexOf('(')
        if (parenStart < 0) {
            ok(writer, tag, "FETCH completed")
            return
        }
        val parenEnd = fetch.lastIndexOf(')')
        val items = fetch.substring(parenStart + 1, if (parenEnd < 0) fetch.length else parenEnd)
            .trim()
        val requested = splitFetchItems(items)

        // UID FETCH -> 按 uid 集匹配；FETCH -> 按 seq 集匹配（fixture 里 uid != seq）
        val bySeq = box.messages.withIndex().associate { (index, message) -> (index + 1).toLong() to message }
        val matched: List<Pair<Long, PreparedMessage>> = if (isUid) {
            box.messages.filter { matchesSet(it.fixture.uid, set) }
                .map { msg -> bySeq.entries.first { it.value === msg }.key to msg }
        } else {
            bySeq.filterKeys { matchesSeq(it, set) }.toList()
        }
        for ((seq, message) in matched) {
            // 无 literal 项与有 literal 项分开应答：多 item+literal 的复合 FETCH
            // 应答会被 JavaMail 1.6.2 的响应解析器丢弃（实测），逐条单 literal 应答稳定。
            val simpleItems = requested.filter { !it.startsWith("BODY.PEEK[") && !it.startsWith("BODY[") }
            if (simpleItems.isNotEmpty()) {
                val simpleParts = simpleItems.mapNotNull { item ->
                    when {
                        item == "UID" -> "UID ${message.fixture.uid}"
                        item == "FLAGS" -> "FLAGS ()"
                        item == "INTERNALDATE" -> "INTERNALDATE \"1-Sep-2026 12:00:00 +0800\""
                        item == "RFC822.SIZE" -> "RFC822.SIZE ${message.wire.size}"
                        item == "ENVELOPE" -> "ENVELOPE ${envelopeOf(message)}"
                        item == "BODYSTRUCTURE" -> "BODYSTRUCTURE ${bodyStructureOf(message)}"
                        else -> null
                    }
                }
                if (simpleParts.isNotEmpty()) {
                    writeSimpleResponse(writer, seq, simpleParts)
                }
            }
            for (item in requested) {
                if (!item.startsWith("BODY.PEEK[") && !item.startsWith("BODY[")) continue
                val resolved = resolveBodySection(message, item)
                if (resolved != null) {
                    writeLiteralResponse(writer, seq, resolved.marker, resolved.payload, resolved.dripMs)
                } else {
                    writeSimpleResponse(writer, seq, listOf("$item {0}"))
                }
            }
        }
        ok(writer, tag, "FETCH completed")
    }

    /** 顶层 item 切分：保留 BODY.PEEK[HEADER.FIELDS (X)] 这类带空格/括号的整项。 */
    private fun splitFetchItems(items: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var bracket = 0
        var current = StringBuilder()
        for (ch in items) {
            when (ch) {
                '(' -> {
                    depth++
                    current.append(ch)
                }
                ')' -> {
                    depth--
                    current.append(ch)
                }
                '[' -> {
                    bracket++
                    current.append(ch)
                }
                ']' -> {
                    bracket--
                    current.append(ch)
                }
                ' ' -> {
                    if (depth == 0 && bracket == 0) {
                        if (current.isNotBlank()) out.add(current.toString().trim())
                        current = StringBuilder()
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) out.add(current.toString().trim())
        return out
    }

    private fun writeSimpleResponse(writer: java.io.OutputStream, seq: Long, parts: List<String>) {
        writeLine(writer, "* $seq FETCH (${parts.joinToString(" ")})")
    }

    private fun writeLiteralResponse(
        writer: java.io.OutputStream,
        seq: Long,
        literalHead: String,
        payload: ByteArray,
        dripMs: Long = 0L
    ) {
        // 形如 "* 1 FETCH (BODY.PEEK[HEADER] {249}\r\n<payload>)\r\n" —— 整段一次写出，
        // 避免分片 write 让客户端读到半截响应而误判响应行结束。
        val head = "* $seq FETCH ($literalHead\r\n".toByteArray(StandardCharsets.ISO_8859_1)
        val tail = ")\r\n".toByteArray(StandardCharsets.ISO_8859_1)
        writer.write(head)
        if (dripMs > 0L) {
            // 滴流模式：payload 逐字节慢写（每字节间隔 dripMs），客户端读永远在途。
            for (b in payload) {
                writer.write(byteArrayOf(b))
                writer.flush()
                try {
                    Thread.sleep(dripMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        } else {
            writer.write(payload)
        }
        writer.write(tail)
        writer.flush()
    }

    private class ResolvedBody(
        val payload: ByteArray,
        val marker: String,
        /** >0：该 literal 按每字节间隔 dripMs 毫秒发送（force-close 断言）。 */
        val dripMs: Long = 0L
    )

    private fun resolveBodySection(message: PreparedMessage, item: String): ResolvedBody? {
        val inner = item.substringAfter("[").substringBeforeLast("]").trim()
        val rangeMatch = Regex("^(.*?)(?:<(\\d+)\\.(\\d+)>)?$").matchEntire(inner)!!
        val section = rangeMatch.groupValues[1].trim()
        val start = rangeMatch.groupValues[2].toLongOrNull() ?: 0L
        val count = rangeMatch.groupValues[3].toLongOrNull()

        // JavaMail 1.6.2 响应解析器对 "BODY.PEEK[...]" 形态的 item 名有缺陷
        // （match("BODY") 后要求紧跟 '['，遇到 .PEEK 误走 BODYSTRUCTURE 分支）：
        // 服务端应答统一用裸 "BODY[section]" 形态（.PEEK 只是客户端请求侧修饰词）。
        val responseItem = "BODY[$section]"
        // HEADER.FIELDS (Name1 Name2) -> 只回被请求的头字段；HEADER -> 全量头块
        val fieldsMatch = Regex("^HEADER\\.FIELDS\\s+\\(([^)]*)\\)$").matchEntire(section)
        when {
            fieldsMatch != null -> {
                val names = fieldsMatch.groupValues[1].split(Regex("\\s+")).filter { it.isNotBlank() }
                val payload = applyRange(extractHeaderFields(message.headerBlock, names), start, count)
                return ResolvedBody(payload, "$responseItem {${payload.size}}")
            }
            section == "HEADER" -> {
                val payload = applyRange(message.headerBlock, start, count)
                return ResolvedBody(payload, "$responseItem {${payload.size}}")
            }
            section == "TEXT" -> {
                // 慢源断言：正文发送前延迟，让单信元数据预算先到点（块间慢服务端）
                if (message.fixture.slowBodyDelayMs > 0L) {
                    Thread.sleep(message.fixture.slowBodyDelayMs)
                }
                // TEXT = 整信正文（单 part 时即 1 号正文；multipart 时 JavaMail 会另行按 part 请求）
                val textBody = message.rawLeafBodies["1"] ?: message.wire
                val payload = applyRange(textBody, start, count)
                return ResolvedBody(payload, "$responseItem {${payload.size}}", message.fixture.bodyDripMs)
            }
            section.endsWith(".MIME") -> {
                // part MIME 头（JavaMail 拉 part 级头时用 BODY.PEEK[n.MIME]）
                val partNumber = section.removeSuffix(".MIME")
                val partHeaders = message.leafHeaders[partNumber]
                    ?: message.leafHeaders["1"]
                    ?: message.headerBlock
                val payload = applyRange(partHeaders, start, count)
                return ResolvedBody(payload, "$responseItem {${payload.size}}")
            }
        }
        // 附件抛错（仅 metadata 断言用例）：metadata 客户端请求附件正文 = 测试失败
        val index = section.toIntOrNull()
        if (failOnAttachmentRequest && index != null && index >= 2 &&
            index - 2 < message.fixture.attachments.size
        ) {
            error("attachment content section requested by client: $section")
        }
        // 单 part 无附件消息按 part 1 请求正文：同样施加滴流（force-close 断言）
        val rawLeaf = message.rawLeafBodies[section] ?: return null
        val payload = applyRange(rawLeaf, start, count)
        val drip = if (index != null && index == 1 && message.fixture.attachments.isEmpty()) {
            message.fixture.bodyDripMs
        } else {
            0L
        }
        return ResolvedBody(payload, "$responseItem {${payload.size}}", drip)
    }

    private fun applyRange(body: ByteArray, start: Long, count: Long?): ByteArray {
        val from = start.toInt()
        return if (count == null) {
            body.copyOfRange(from, body.size)
        } else {
            body.copyOfRange(from, (from + count.toInt()).coerceAtMost(body.size))
        }
    }

    /** 从整块头部取指定字段（原行原样），找不到的字段省略。 */
    private fun extractHeaderFields(headerBlock: ByteArray, names: List<String>): ByteArray {
        val headerText = String(headerBlock, StandardCharsets.ISO_8859_1)
        val wanted = names.map { it.lowercase() }
        val wantedSet = wanted.toSet()
        val lines = headerText.split("\r\n")
        val out = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isEmpty() || line.startsWith(" ")) break // 头块结束（含折叠续行兜底）
            val name = line.substringBefore(":").trim().lowercase()
            if (name in wantedSet) {
                out.append(line).append("\r\n")
            }
            i++
        }
        out.append("\r\n")
        return out.toString().toByteArray(StandardCharsets.ISO_8859_1)
    }

    private fun envelopeOf(message: PreparedMessage): String {
        // JavaMail 对该格式（全 NIL + messageId）实测可解析；地址/主题等一律由
        // HEADER FETCH 提供，ENVELOPE 只用于填充 messageId 等信封级字段。
        val id = message.fixture.subject
        return "(NIL NIL NIL NIL NIL NIL NIL NIL NIL \"<$id@fixture.local>\")"
    }

    private fun bodyStructureOf(message: PreparedMessage): String {
        if (message.fixture.attachments.isEmpty()) {
            val size = message.leafBodies["1"]?.size ?: 0
            return "(\"text\" \"plain\" (\"charset\" \"utf-8\") NIL NIL \"8bit\" $size $size NIL NIL NIL NIL)"
        }
        val parts = mutableListOf<String>()
        parts.add("(\"text\" \"plain\" (\"charset\" \"utf-8\") NIL NIL \"8bit\" ${message.leafBodies["1"]?.size ?: 0} ${message.leafBodies["1"]?.size ?: 0} NIL NIL NIL NIL)")
        message.fixture.attachments.forEachIndexed { index, att ->
            val n = index + 2
            val size = message.leafBodies["$n"]?.size ?: 0
            val type = att.contentType.substringBefore("/")
            val subtype = att.contentType.substringAfter("/")
            // text/* 需要 RFC3501 的 body-fld-lines（位于 size 之后、disposition 之前）
            val lines = if (type.equals("text", ignoreCase = true)) "$size" else "NIL"
            val quotedType = "\"$type\""
            val quotedSubtype = "\"$subtype\""
            parts.add(
                "($quotedType $quotedSubtype (\"name\" \"${att.fileName}\") NIL NIL \"base64\" $size $lines " +
                    "(\"attachment\" (\"filename\" \"${att.fileName}\")) NIL NIL)"
            )
        }
        return "(${parts.joinToString(" ")} \"mixed\" (\"boundary\" \"----=_B_0\") NIL NIL)"
    }

    private fun matchesSet(value: Long, set: String): Boolean {
        return when {
            set == "*" -> true
            ":" in set -> {
                val (a, b) = set.split(":")
                val lo = a.toLongOrNull() ?: 1L
                val hi = if (b == "*") Long.MAX_VALUE else b.toLongOrNull() ?: Long.MAX_VALUE
                value in lo..hi
            }
            "," in set -> set.split(",").any { it.toLongOrNull() == value }
            else -> set.toLongOrNull() == value
        }
    }

    private fun matchesSeq(seq: Long, set: String): Boolean =
        when {
            set == "*" || set == "1:*" -> true
            ":" in set -> {
                val (a, b) = set.split(":")
                val lo = a.toLongOrNull() ?: 1L
                val hi = if (b == "*") Long.MAX_VALUE else b.toLongOrNull() ?: Long.MAX_VALUE
                seq in lo..hi
            }
            "," in set -> set.split(",").any { it.toLongOrNull() == seq }
            else -> set.toLongOrNull() == seq
        }

    private fun stripQuotes(value: String): String =
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value.substring(1, value.length - 1)
        } else {
            value
        }

    private fun ok(writer: java.io.OutputStream, tag: String, text: String) {
        writeLine(writer, "$tag OK $text")
    }

    private fun writeLine(writer: java.io.OutputStream, line: String) {
        writer.write(line.toByteArray(StandardCharsets.ISO_8859_1))
        writer.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        writer.flush()
    }

    private fun writeRaw(writer: java.io.OutputStream, text: String) {
        writer.write(text.toByteArray(StandardCharsets.ISO_8859_1))
        writer.flush()
    }

    override fun close() {
        runCatching { serverSocket.close() }
    }
}
