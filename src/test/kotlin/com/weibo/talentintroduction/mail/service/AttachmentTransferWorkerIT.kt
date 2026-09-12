package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import com.weibo.talentintroduction.mail.repository.MailAttachmentTransferRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Statement
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 有界、持久化附件传输 worker 集成测试（mysqlIt 门禁：真实 MySQL
 * 127.0.0.1:3306/talent_introduction；Flyway 迁移 scratch 测试库，不触碰生产库）。
 *
 * 本地 IMAP fixture 用 JDK socket 实现（无新中间件依赖），按 JavaMail 1.6.7
 * 实际发出的命令应答：LOGIN/EXAMINE、UID FETCH (UID)、FETCH (ENVELOPE
 * INTERNALDATE RFC822.SIZE)、FETCH (BODYSTRUCTURE) 与分块 BODY.PEEK[sec]<start.cnt>。
 *
 * 覆盖 I-1..I-4 验收场景：
 * - I-1：同源并发登记一行；同名跨 UID 不同任务；UIDVALIDITY/Message-ID/part
 *   不符与来源缺失 -> SOURCE_UNAVAILABLE；DMARC 无附件、MATERIAL 必须有附件。
 * - I-2：10 次重复提交仅 1 次下载；重启只恢复已请求项（METADATA_ONLY 不动）；
 *   SOURCE_UNAVAILABLE/FAILED 不自动重试。
 * - I-3：两账号全局 2 / 同账号 1；1B/s 慢源在总时限内失败且连接被硬关闭；
 *   超过 1MiB（测试配置上限）立即 FAILED/LIMIT_EXCEEDED；旧租约/旧 token 不能提交。
 * - I-4：.part 从不残留/可见；转正与提交之间崩溃后重启收敛，不重复下载、
 *   不复制专家材料；完成后才写 storage_path/file_size 并标 STORED。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// worker 在独立线程/独立连接里读写同一批行；测试级 @Transactional 回滚会让
// worker 看不到未提交行。显式 NOT_SUPPORTED 覆盖 DataJdbcTest 的默认事务。
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(
    properties = [
        "spring.flyway.placeholder-replacement=false"
    ]
)
@Import(
    AttachmentTransferWorkerItConfig::class,
    AttachmentTransferTransactionConfig::class,
    AttachmentTransferService::class,
    AttachmentTransferWorker::class,
    ImapAttachmentContentFetcher::class,
    MailAttachmentService::class
)
class AttachmentTransferWorkerIT {

    companion object {
        const val PER_FILE_MAX_BYTES = 1_048_576L // 测试配置：单文件 1MiB
        const val TOTAL_TIMEOUT_SECONDS = 5L
        /** 2026-09-12 生产真实样本 fileId（形状与真实分享链接一致）。 */
        const val DRIVE_FILE_ID = "1eUOvutQu2yinCWYHiWIbVAVt2icbSwW9"

        @JvmStatic
        @DynamicPropertySource
        fun transferProperties(registry: DynamicPropertyRegistry) {
            val base = Files.createTempDirectory("attachment-transfer-it")
            registry.add("talent-introduction.mail-attachment-storage.base-path") { base.toString() }
            registry.add("talent-introduction.mail-attachment-storage.transfer-total-timeout-seconds") { TOTAL_TIMEOUT_SECONDS.toString() }
            registry.add("talent-introduction.mail-attachment-storage.transfer-max-bytes") { PER_FILE_MAX_BYTES.toString() }
        }
    }

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var properties: MailAttachmentStorageProperties

    @Autowired
    private lateinit var service: AttachmentTransferService

    @Autowired
    private lateinit var worker: AttachmentTransferWorker

    @Autowired
    private lateinit var transferRepository: MailAttachmentTransferRepository

    @Autowired
    private lateinit var mailAttachmentService: MailAttachmentService

    private lateinit var server: ImapFixtureServer

    private var driveServer: DriveHttpFixtureServer? = null

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @BeforeEach
    fun setUp() {
        worker.stop()
        cleanupTables()
    }

    @AfterEach
    fun tearDown() {
        worker.stop()
        runCatching { server.close() }
        driveServer?.let { runCatching { it.close() } }
        driveServer = null
        cleanupTables()
    }

    private fun cleanupTables() {
        jdbcTemplate.update("DELETE FROM mail_attachment_transfer")
        jdbcTemplate.update("DELETE FROM expert_document")
        jdbcTemplate.update("DELETE FROM mail_attachment")
        jdbcTemplate.update("DELETE FROM mail_record")
        jdbcTemplate.update("DELETE FROM inbound_mail_processing")
        jdbcTemplate.update("DELETE FROM expert_contact")
        jdbcTemplate.update("DELETE FROM campaign")
        jdbcTemplate.update("DELETE FROM mail_sender_account")
    }

    // ------------------------------------------------------------------
    // 种子助手
    // ------------------------------------------------------------------

    private fun startServer(vararg boxes: FixtureMailbox): ImapFixtureServer {
        server = ImapFixtureServer(boxes.associateBy { it.username })
        return server
    }

    private fun seedSenderAccount(accountCode: String, username: String, password: String, port: Int): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO mail_sender_account
                        (account_code, sender_email, sender_name, smtp_host, smtp_port,
                         smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
                    VALUES (?, ?, ?, 'smtp.fixture', 465, ?, ?, '127.0.0.1', ?, ?, ?)
                    """.trimIndent(),
                    Statement.RETURN_GENERATED_KEYS
                ).apply {
                    setString(1, accountCode)
                    setString(2, "$accountCode@fixture.local")
                    setString(3, accountCode)
                    setString(4, username)
                    setString(5, password)
                    setInt(6, port)
                    setString(7, username)
                    setString(8, password)
                }
            },
            keyHolder
        )
        return keyHolder.key!!.toLong()
    }

    private fun seedExpert(contactId: Long) {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO mail_sender_account
                        (account_code, sender_email, sender_name, smtp_host, smtp_port,
                         smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
                    VALUES (?, ?, ?, 'smtp.seed', 465, ?, ?, '127.0.0.1', 143, ?, ?)
                    """.trimIndent(),
                    Statement.RETURN_GENERATED_KEYS
                ).apply {
                    setString(1, "seed-$contactId")
                    setString(2, "seed$contactId@fixture.local")
                    setString(3, "Seed $contactId")
                    setString(4, "seed$contactId")
                    setString(5, "seed")
                    setString(6, "seed$contactId")
                    setString(7, "seed")
                }
            },
            keyHolder
        )
        val senderAccountId = keyHolder.key!!.toLong()
        jdbcTemplate.update(
            "INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) VALUES (?, ?, 'Fixture', ?)",
            contactId, "FIXTURE-$contactId", senderAccountId
        )
        jdbcTemplate.update(
            """
            INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, current_status)
            VALUES (?, ?, ?, ?, 'NEW')
            """.trimIndent(),
            contactId, contactId, "0000-000$contactId", "expert$contactId@fixture.local"
        )
    }

    private fun insertMailRecord(contactId: Long, messageId: String?): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO mail_record
                        (expert_contact_id, direction, mail_type, message_id, send_status, received_at)
                    VALUES (?, 'INBOUND', 'REPLY', ?, 'SENT', ?)
                    """.trimIndent(),
                    Statement.RETURN_GENERATED_KEYS
                ).apply {
                    setLong(1, contactId)
                    setString(2, messageId)
                    setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()))
                }
            },
            keyHolder
        )
        return keyHolder.key!!.toLong()
    }

    private fun insertAttachment(mailRecordId: Long, fileName: String, contentType: String?): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO mail_attachment
                        (mail_record_id, file_name, content_type, file_size, storage_path)
                    VALUES (?, ?, ?, NULL, NULL)
                    """.trimIndent(),
                    Statement.RETURN_GENERATED_KEYS
                ).apply {
                    setLong(1, mailRecordId)
                    setString(2, fileName)
                    setString(3, contentType)
                }
            },
            keyHolder
        )
        return keyHolder.key!!.toLong()
    }

    private fun insertDocument(contactId: Long, attachmentId: Long) {
        jdbcTemplate.update(
            "INSERT INTO expert_document (expert_contact_id, mail_attachment_id, document_type, document_status) VALUES (?, ?, 'CV', 'PENDING_REVIEW')",
            contactId, attachmentId
        )
    }

    /** 建附件（mail_record 归属 + expert_document）并登记 MATERIAL transfer。 */
    private fun registerMaterial(
        contactId: Long,
        accountCode: String,
        box: FixtureMailbox,
        message: FixtureMail,
        fileName: String,
        partPath: String = "2",
        contentType: String? = "application/pdf",
        messageIdOverride: String? = null,
        uidValidityOverride: Long? = null,
        encodedSize: Long? = null
    ): Long {
        val mailRecordId = insertMailRecord(contactId, message.messageId)
        val attachmentId = insertAttachment(mailRecordId, fileName, contentType)
        insertDocument(contactId, attachmentId)
        val result = service.register(
            AttachmentTransferService.RegisterTransferRequest(
                purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
                attachmentId = attachmentId,
                accountCode = accountCode,
                folder = "INBOX",
                uidValidity = uidValidityOverride ?: box.uidValidity,
                imapUid = message.uid,
                partPath = partPath,
                messageId = messageIdOverride ?: message.messageId,
                fileName = fileName,
                contentType = contentType,
                encodedSize = encodedSize
            )
        )
        return result.transfer.id!!
    }

    private fun enqueueMaterial(contactId: Long, vararg transferIds: Long): AttachmentTransferService.EnqueueBatchResult =
        service.enqueueMaterial(
            contactId,
            transferIds.map { attachmentIdOfTransfer(it) },
            "admin"
        )

    private fun attachmentIdOfTransfer(transferId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT attachment_id FROM mail_attachment_transfer WHERE id = ?",
            Long::class.java, transferId
        )!!

    private fun stateOf(transferId: Long): String =
        jdbcTemplate.queryForObject(
            "SELECT state FROM mail_attachment_transfer WHERE id = ?",
            String::class.java, transferId
        )!!

    private fun errorCodeOf(transferId: Long): String? =
        jdbcTemplate.queryForObject(
            "SELECT error_code FROM mail_attachment_transfer WHERE id = ?",
            String::class.java, transferId
        )

    private fun errorMessageOf(transferId: Long): String? =
        jdbcTemplate.queryForObject(
            "SELECT error_message FROM mail_attachment_transfer WHERE id = ?",
            String::class.java, transferId
        )

    private fun attemptOf(transferId: Long): Int =
        jdbcTemplate.queryForObject(
            "SELECT attempt FROM mail_attachment_transfer WHERE id = ?",
            Int::class.java, transferId
        )!!

    private fun awaitState(transferId: Long, expected: Set<String>, timeoutMs: Long = 60_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (stateOf(transferId) in expected) return
            Thread.sleep(100)
        }
        throw AssertionError(
            "transfer $transferId did not reach $expected within ${timeoutMs}ms; " +
                "state=${stateOf(transferId)} error=${errorCodeOf(transferId)}"
        )
    }

    private fun transferDir(transferId: Long): Path =
        Path.of(properties.basePath, "transfer", transferId.toString())

    private fun finalPath(transferId: Long): Path = transferDir(transferId).resolve("content.bin")

    private fun listPartFiles(): List<String> {
        val root = Path.of(properties.basePath, "transfer")
        if (!Files.isDirectory(root)) return emptyList()
        val out = mutableListOf<String>()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".part") }
                .forEach { out.add(it.toString()) }
        }
        return out
    }

    /** 直接 SQL 模拟一次领取（测试不依赖 worker 领取计时）。 */
    private fun claimBySql(transferId: Long, token: String, leaseSecondsAhead: Long) {
        jdbcTemplate.update(
            """
            UPDATE mail_attachment_transfer
               SET state = 'DOWNLOADING', worker_token = ?, lease_until = ?,
                   started_at = ?, attempt = attempt + 1, bytes_downloaded = 0,
                   error_code = NULL, error_message = NULL, updated_at = ?
             WHERE id = ?
            """.trimIndent(),
            token,
            Timestamp.valueOf(LocalDateTime.now().plusSeconds(leaseSecondsAhead)),
            Timestamp.valueOf(LocalDateTime.now()),
            Timestamp.valueOf(LocalDateTime.now()),
            transferId
        )
    }

    private fun expireLease(transferId: Long, secondsAgo: Long = 30) {
        jdbcTemplate.update(
            "UPDATE mail_attachment_transfer SET lease_until = ? WHERE id = ?",
            Timestamp.valueOf(LocalDateTime.now().minusSeconds(secondsAgo)),
            transferId
        )
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }

    // ------------------------------------------------------------------
    // I-1/I-2：重复提交、并发登记、同源唯一
    // ------------------------------------------------------------------

    @Test
    fun `submitting the same attachment ten times downloads it exactly once`() {
        seedExpert(1)
        val bytes = contentBytes(2048, 1)
        val box = FixtureMailbox("acc-a", "pwd", 7, listOf(fixtureMail(1, "m1", "cv-a.pdf", bytes)))
        val started = startServer(box)
        seedSenderAccount("acc-a", "acc-a", "pwd", started.port)
        val transferId = registerMaterial(1, "acc-a", box, box.messages[0], "cv-a.pdf")

        val first = enqueueMaterial(1, transferId)
        assertEquals(1, first.acceptedCount)
        repeat(9) { enqueueMaterial(1, transferId) }

        worker.start()
        awaitState(transferId, setOf("STORED"))
        worker.stop()

        assertEquals("STORED", stateOf(transferId))
        assertEquals(1, attemptOf(transferId))
        assertEquals(1, started.tracker().downloadsByKey()["acc-a:1"])
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mail_attachment_transfer WHERE state = 'STORED' AND id = ?",
            Long::class.java, transferId
        ))
        // I-4：完成后才写 storage_path/file_size，.part 从不残留
        val attachmentId = attachmentIdOfTransfer(transferId)
        val storagePath = jdbcTemplate.queryForObject(
            "SELECT storage_path FROM mail_attachment WHERE id = ?", String::class.java, attachmentId
        )
        assertNotNull(storagePath)
        assertTrue(storagePath!!.endsWith("content.bin"), "unexpected storage path: $storagePath")
        assertEquals(bytes.size.toLong(), jdbcTemplate.queryForObject(
            "SELECT file_size FROM mail_attachment WHERE id = ?", Long::class.java, attachmentId
        ))
        assertTrue(Files.readAllBytes(Path.of(storagePath)).contentEquals(bytes))
        assertTrue(listPartFiles().isEmpty(), ".part must never be left visible: ${listPartFiles()}")
    }

    @Test
    fun `concurrent registration of the same source yields a single row`() {
        seedExpert(1)
        val box = FixtureMailbox("acc-a", "pwd", 7, listOf(fixtureMail(1, "m1", "cv-a.pdf", contentBytes(100, 1))))
        startServer(box)
        seedSenderAccount("acc-a", "acc-a", "pwd", server.port)
        val mailRecordId = insertMailRecord(1, "m1")
        val attachmentId = insertAttachment(mailRecordId, "cv-a.pdf", "application/pdf")
        insertDocument(1, attachmentId)

        val threads = 8
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(threads)
        val pool = Executors.newFixedThreadPool(threads)
        val registeredIds = ConcurrentHashMap.newKeySet<Long>()
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        repeat(threads) {
            pool.submit {
                startGate.await()
                try {
                    val result = service.register(
                        AttachmentTransferService.RegisterTransferRequest(
                            purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
                            attachmentId = attachmentId,
                            accountCode = "acc-a",
                            folder = "INBOX",
                            uidValidity = 7,
                            imapUid = 1,
                            partPath = "2",
                            messageId = "m1",
                            fileName = "cv-a.pdf",
                            contentType = "application/pdf"
                        )
                    )
                    registeredIds.add(result.transfer.id!!)
                } catch (t: Throwable) {
                    failures.add(
                        RuntimeException(
                            t.javaClass.simpleName + ": " + t.message +
                                " cause=" + t.cause?.javaClass?.simpleName + ": " + t.cause?.message
                        )
                    )
                } finally {
                    doneGate.countDown()
                }
            }
        }
        startGate.countDown()
        assertTrue(doneGate.await(20, TimeUnit.SECONDS), "concurrent registers did not finish")
        pool.shutdownNow()

        assertTrue(failures.isEmpty(), "register threads failed: $failures")
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mail_attachment_transfer", Long::class.java
        ))
        assertEquals(1, registeredIds.size)
    }

    @Test
    fun `same file name on different uids registers distinct rows and duplicate registration is idempotent`() {
        seedExpert(1)
        val box = FixtureMailbox(
            "acc-a", "pwd", 7,
            listOf(
                fixtureMail(1, "m1", "cv.pdf", contentBytes(64, 1)),
                fixtureMail(2, "m2", "cv.pdf", contentBytes(64, 2))
            )
        )
        startServer(box)
        seedSenderAccount("acc-a", "acc-a", "pwd", server.port)

        val mailRecordId1 = insertMailRecord(1, "m1")
        val attachment1 = insertAttachment(mailRecordId1, "cv.pdf", "application/pdf")
        insertDocument(1, attachment1)
        val r1 = registerRaw(attachment1, 7, 1, "m1")
        val mailRecordId2 = insertMailRecord(1, "m2")
        val attachment2 = insertAttachment(mailRecordId2, "cv.pdf", "application/pdf")
        insertDocument(1, attachment2)
        val r2 = registerRaw(attachment2, 7, 2, "m2")

        assertTrue(r1.created)
        assertTrue(r2.created)
        // 同源重复登记返回同一行
        val dup = registerRaw(attachment2, 7, 2, "m2")
        assertFalse(dup.created)
        assertEquals(r2.transfer.id, dup.transfer.id)
        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mail_attachment_transfer", Long::class.java
        ))
    }

    private fun registerRaw(
        attachmentId: Long,
        uidValidity: Long,
        uid: Long,
        messageId: String?
    ): AttachmentTransferService.RegistrationResult =
        service.register(
            AttachmentTransferService.RegisterTransferRequest(
                purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
                attachmentId = attachmentId,
                accountCode = "acc-a",
                folder = "INBOX",
                uidValidity = uidValidity,
                imapUid = uid,
                partPath = "2",
                messageId = messageId,
                fileName = "cv.pdf",
                contentType = "application/pdf"
            )
        )

    // ------------------------------------------------------------------
    // I-3：并发上限
    // ------------------------------------------------------------------

    @Test
    fun `two accounts download concurrently while one account never exceeds a single download`() {
        seedExpert(1)
        // 每文件约 2.3s（900KiB / 64KiB 块 × 150ms 块延迟）：足够让两个驱动线程
        // 的同账号/跨账号窗口确定重叠，观测真实并发而非调度噪声。
        val payload = ByteArray(900 * 1024) { 1 }
        val boxA = FixtureMailbox(
            "acc-a", "pwd", 7,
            listOf(
                fixtureMail(1, "a1", "a-1.pdf", payload, slowChunkDelayMs = 150),
                fixtureMail(2, "a2", "a-2.pdf", payload, slowChunkDelayMs = 150)
            )
        )
        val boxB = FixtureMailbox(
            "acc-b", "pwd", 7,
            listOf(
                fixtureMail(1, "b1", "b-1.pdf", payload, slowChunkDelayMs = 150),
                fixtureMail(2, "b2", "b-2.pdf", payload, slowChunkDelayMs = 150)
            )
        )
        val started = startServer(boxA, boxB)
        seedSenderAccount("acc-a", "acc-a", "pwd", started.port)
        seedSenderAccount("acc-b", "acc-b", "pwd", started.port)

        val a1 = registerMaterial(1, "acc-a", boxA, boxA.messages[0], "a-1.pdf")
        val a2 = registerMaterial(1, "acc-a", boxA, boxA.messages[1], "a-2.pdf")
        val b1 = registerMaterial(1, "acc-b", boxB, boxB.messages[0], "b-1.pdf")
        val b2 = registerMaterial(1, "acc-b", boxB, boxB.messages[1], "b-2.pdf")
        enqueueMaterial(1, a1, a2, b1, b2)

        worker.start()
        listOf(a1, a2, b1, b2).forEach { awaitState(it, setOf("STORED")) }
        worker.stop()
        // 等 fixture 会话线程观察到客户端关连接并落账，再快照并发区间
        Thread.sleep(700)

        assertEquals(4, started.tracker().totalDownloads())
        assertEquals(2, started.tracker().maxGlobalConcurrent())
        assertEquals(1, started.tracker().maxPerAccountConcurrent("acc-a"))
        assertEquals(1, started.tracker().maxPerAccountConcurrent("acc-b"))
        listOf(a1, a2, b1, b2).forEach { assertEquals("STORED", stateOf(it)) }
    }

    // ------------------------------------------------------------------
    // I-3：总时限、连接硬关闭、其余文件继续
    // ------------------------------------------------------------------

    @Test
    fun `slow source that cannot finish within the total deadline fails with its connection closed`() {
        seedExpert(1)
        // 慢源：800KiB，每个分块响应前延迟 1.5s -> 64KiB/块约 19s 才能完成，远超
        // 5s 总时限（若 JavaMail 用默认 16KiB 块则约 75s，同样远超）。
        val slowBox = FixtureMailbox(
            "acc-slow", "pwd", 7,
            listOf(
                fixtureMail(
                    1, "s1", "slow.pdf", ByteArray(800 * 1024) { 7 },
                    slowChunkDelayMs = 1_500
                )
            )
        )
        val fastBox = FixtureMailbox(
            "acc-fast", "pwd", 7,
            listOf(
                fixtureMail(1, "f1", "fast-1.pdf", contentBytes(20_000, 3)),
                fixtureMail(2, "f2", "fast-2.pdf", contentBytes(20_000, 4))
            )
        )
        val started = startServer(slowBox, fastBox)
        seedSenderAccount("acc-slow", "acc-slow", "pwd", started.port)
        seedSenderAccount("acc-fast", "acc-fast", "pwd", started.port)

        val slow = registerMaterial(1, "acc-slow", slowBox, slowBox.messages[0], "slow.pdf")
        val f1 = registerMaterial(1, "acc-fast", fastBox, fastBox.messages[0], "fast-1.pdf")
        val f2 = registerMaterial(1, "acc-fast", fastBox, fastBox.messages[1], "fast-2.pdf")
        val enqueuedAt = System.currentTimeMillis()
        enqueueMaterial(1, slow, f1, f2)

        worker.start()
        awaitState(slow, setOf("FAILED"), timeoutMs = 30_000)
        val elapsedMs = System.currentTimeMillis() - enqueuedAt
        listOf(f1, f2).forEach { awaitState(it, setOf("STORED")) }
        worker.stop()

        assertEquals("FAILED", stateOf(slow))
        assertEquals("TIMEOUT", errorCodeOf(slow))
        assertTrue(elapsedMs in 4_000..20_000, "slow transfer failed after ${elapsedMs}ms (total deadline is 5s)")
        assertTrue(
            started.tracker().wasInterruptedMidDownload("acc-slow"),
            "slow source connection must be closed by the watchdog"
        )
        // 慢源期间已有若干字节落盘，最终 .part 被清理
        assertTrue(jdbcTemplate.queryForObject(
            "SELECT bytes_downloaded FROM mail_attachment_transfer WHERE id = ?", Long::class.java, slow
        ) > 0)
        // 其余文件继续完成；失败不自动无限重试
        assertEquals("STORED", stateOf(f1))
        assertEquals("STORED", stateOf(f2))
        assertEquals("FAILED", stateOf(slow))
    }

    // ------------------------------------------------------------------
    // I-3：超限立即失败
    // ------------------------------------------------------------------

    @Test
    fun `oversized part fails with LIMIT_EXCEEDED quickly and leaves nothing readable behind`() {
        seedExpert(1)
        val box = FixtureMailbox(
            "acc-a", "pwd", 7,
            listOf(
                fixtureMail(1, "m1", "ok.pdf", contentBytes(4096, 1)),
                fixtureMail(2, "m2", "huge.pdf", ByteArray(1536 * 1024) { 9 })
            )
        )
        val started = startServer(box)
        seedSenderAccount("acc-a", "acc-a", "pwd", started.port)
        val ok = registerMaterial(1, "acc-a", box, box.messages[0], "ok.pdf")
        val huge = registerMaterial(1, "acc-a", box, box.messages[1], "huge.pdf")
        enqueueMaterial(1, ok, huge)
        val startedAt = System.currentTimeMillis()

        worker.start()
        awaitState(huge, setOf("FAILED"))
        awaitState(ok, setOf("STORED"))
        worker.stop()

        val elapsedMs = System.currentTimeMillis() - startedAt
        assertEquals("FAILED", stateOf(huge))
        assertEquals("LIMIT_EXCEEDED", errorCodeOf(huge))
        assertTrue(elapsedMs < 20_000, "oversized file must fail quickly, took ${elapsedMs}ms")
        // 写满上限后立即中止；不产生最终文件或 .part
        assertEquals(PER_FILE_MAX_BYTES, jdbcTemplate.queryForObject(
            "SELECT bytes_downloaded FROM mail_attachment_transfer WHERE id = ?", Long::class.java, huge
        ))
        assertFalse(Files.exists(finalPath(huge)))
        assertTrue(listPartFiles().isEmpty(), ".part must be removed on failure: ${listPartFiles()}")
        assertEquals("STORED", stateOf(ok))
    }

    // ------------------------------------------------------------------
    // I-2/I-4：重启恢复与崩溃收敛
    // ------------------------------------------------------------------

    @Test
    fun `restart recovers only requested transfers and never touches metadata-only rows`() {
        seedExpert(1)
        val box = FixtureMailbox(
            "acc-a", "pwd", 7,
            listOf(
                fixtureMail(1, "m1", "never.pdf", contentBytes(1024, 1)),
                fixtureMail(2, "m2", "done.pdf", contentBytes(1024, 2)),
                fixtureMail(3, "m3", "queued.pdf", contentBytes(1024, 3)),
                fixtureMail(4, "m4", "crashed.pdf", contentBytes(1024, 4))
            )
        )
        val started = startServer(box)
        seedSenderAccount("acc-a", "acc-a", "pwd", started.port)
        val never = registerMaterial(1, "acc-a", box, box.messages[0], "never.pdf")
        val done = registerMaterial(1, "acc-a", box, box.messages[1], "done.pdf")
        val queued = registerMaterial(1, "acc-a", box, box.messages[2], "queued.pdf")
        val crashed = registerMaterial(1, "acc-a", box, box.messages[3], "crashed.pdf")

        // 第一段运行：done 完成，never 保持 METADATA_ONLY
        enqueueMaterial(1, done)
        worker.start()
        awaitState(done, setOf("STORED"))
        worker.stop()
        assertEquals("METADATA_ONLY", stateOf(never))

        // “重启”：queued 入队未处理 + crashed 领取后崩溃（租约过期、无最终文件）
        enqueueMaterial(1, queued)
        claimBySql(crashed, "crash-token", leaseSecondsAhead = -30)

        worker.start()
        listOf(queued, crashed).forEach { awaitState(it, setOf("STORED")) }
        worker.stop()

        assertEquals("METADATA_ONLY", stateOf(never), "never-requested metadata rows must not be recovered")
        assertEquals("STORED", stateOf(done))
        assertEquals("STORED", stateOf(queued))
        assertEquals("STORED", stateOf(crashed))
        assertEquals(2, attemptOf(crashed))
        // 各自恰好下载一次；从未请求的附件 0 次
        assertEquals(1, started.tracker().downloadsByKey()["acc-a:2"])
        assertEquals(1, started.tracker().downloadsByKey()["acc-a:3"])
        assertEquals(1, started.tracker().downloadsByKey()["acc-a:4"])
        assertNull(started.tracker().downloadsByKey()["acc-a:1"])
    }

    @Test
    fun `crash between atomic rename and DB commit converges on restart without re-downloading`() {
        seedExpert(1)
        val payload = contentBytes(5000, 11)
        val box = FixtureMailbox(
            "acc-a", "pwd", 7,
            listOf(
                fixtureMail(1, "m1", "renamed.pdf", payload),
                fixtureMail(2, "m2", "mid-download.pdf", contentBytes(3000, 12))
            )
        )
        val started = startServer(box)
        seedSenderAccount("acc-a", "acc-a", "pwd", started.port)
        val renamed = registerMaterial(1, "acc-a", box, box.messages[0], "renamed.pdf")
        val mid = registerMaterial(1, "acc-a", box, box.messages[1], "mid-download.pdf")
        enqueueMaterial(1, renamed, mid)

        // 崩溃现场 1：claim 完成、.part 已原子转正为 content.bin、DB 未提交（租约随后过期）
        claimBySql(renamed, "crash-renamed", leaseSecondsAhead = 60)
        Files.createDirectories(transferDir(renamed))
        Files.write(finalPath(renamed), payload)
        Files.write(transferDir(renamed).resolve("crash-renamed.part"), payload.copyOfRange(0, 100))
        expireLease(renamed)
        // 崩溃现场 2：claim 完成、只有 .part（未转正），租约过期
        claimBySql(mid, "crash-mid", leaseSecondsAhead = 60)
        Files.createDirectories(transferDir(mid))
        Files.write(transferDir(mid).resolve("crash-mid.part"), payload.copyOfRange(0, 100))
        expireLease(mid)

        worker.start()
        listOf(renamed, mid).forEach { awaitState(it, setOf("STORED")) }
        worker.stop()

        // 已转正：收敛路径核验确定性最终文件后直接提交，不再向源请求内容
        assertEquals("STORED", stateOf(renamed))
        assertEquals(2, attemptOf(renamed))
        assertNull(started.tracker().downloadsByKey()["acc-a:1"], "renamed file must not be re-downloaded")
        assertTrue(Files.readAllBytes(finalPath(renamed)).contentEquals(payload))
        // 只有 .part：恢复后重下 1 次完成；.part 被清理
        assertEquals("STORED", stateOf(mid))
        assertEquals(1, started.tracker().downloadsByKey()["acc-a:2"])
        assertTrue(listPartFiles().isEmpty(), ".part leftovers must be cleaned: ${listPartFiles()}")
        // 不重复创建专家资料；完成后 storage_path/file_size 才可见且不指向 .part
        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_document", Long::class.java
        ))
        listOf(renamed, mid).forEach { tid ->
            val path = jdbcTemplate.queryForObject(
                """
                SELECT ma.storage_path
                  FROM mail_attachment ma
                  JOIN mail_attachment_transfer t ON t.attachment_id = ma.id
                 WHERE t.id = ?
                """.trimIndent(),
                String::class.java, tid
            )
            assertNotNull(path)
            assertFalse(path!!.endsWith(".part"))
            assertTrue(Files.isRegularFile(Path.of(path)))
        }
    }

    // ------------------------------------------------------------------
    // I-3：旧租约/旧 token 不能提交
    // ------------------------------------------------------------------

    @Test
    fun `stale lease and stale token cannot commit but recovery completes the transfer`() {
        seedExpert(1)
        val box = FixtureMailbox("acc-a", "pwd", 7, listOf(fixtureMail(1, "m1", "cv.pdf", contentBytes(1024, 5))))
        val started = startServer(box)
        seedSenderAccount("acc-a", "acc-a", "pwd", started.port)
        val transferId = registerMaterial(1, "acc-a", box, box.messages[0], "cv.pdf")
        enqueueMaterial(1, transferId)

        claimBySql(transferId, "current-token", leaseSecondsAhead = 60)
        // token 不符：不能提交
        assertEquals(0, transferRepository.commitStored(transferId, "wrong-token", 100, LocalDateTime.now()))
        assertEquals("DOWNLOADING", stateOf(transferId))
        // 租约过期：原 token 也不能提交/失败
        expireLease(transferId)
        assertEquals(0, transferRepository.commitStored(transferId, "current-token", 100, LocalDateTime.now()))
        assertEquals("DOWNLOADING", stateOf(transferId))
        assertEquals(0, transferRepository.failAttempt(
            transferId, "current-token", MailAttachmentTransfer.STATE_FAILED,
            "TIMEOUT", "stale", 0, LocalDateTime.now()
        ))

        // 恢复：过期 DOWNLOADING -> QUEUED -> 重新领取下载 -> STORED（下载仍只发生 1 次）
        worker.start()
        awaitState(transferId, setOf("STORED"))
        worker.stop()

        assertEquals("STORED", stateOf(transferId))
        assertEquals(2, attemptOf(transferId))
        assertNull(errorCodeOf(transferId))
        assertEquals(1, started.tracker().downloadsByKey()["acc-a:1"])
        val storagePath = jdbcTemplate.queryForObject(
            """
            SELECT ma.storage_path
              FROM mail_attachment ma
              JOIN mail_attachment_transfer t ON t.attachment_id = ma.id
             WHERE t.id = ?
            """.trimIndent(),
            String::class.java, transferId
        )
        assertNotNull(storagePath)
        assertFalse(storagePath!!.endsWith(".part"))
        assertTrue(Files.isRegularFile(Path.of(storagePath)))
    }

    // ------------------------------------------------------------------
    // I-1：来源缺失 / UIDVALIDITY / Message-ID / part 校验
    // ------------------------------------------------------------------

    @Test
    fun `missing source, UIDVALIDITY change, message-id and part mismatches are source-unavailable`() {
        seedExpert(1)
        val box = FixtureMailbox(
            "acc-a", "pwd", 7,
            listOf(
                fixtureMail(1, "m1", "p1.pdf", contentBytes(64, 1)),
                fixtureMail(2, "m2", "p2.pdf", contentBytes(64, 2)),
                fixtureMail(3, "m3", "p3.pdf", contentBytes(64, 3)),
                fixtureMail(4, "m4", "p4.pdf", contentBytes(64, 4))
            )
        )
        val started = startServer(box)
        seedSenderAccount("acc-a", "acc-a", "pwd", started.port)

        fun registerFor(
            uid: Long,
            messageId: String?,
            uidValidityOverride: Long? = null,
            partPath: String = "2",
            contentType: String? = "application/pdf",
            fileName: String = "x.pdf"
        ): Long {
            val recordId = insertMailRecord(1, messageId)
            val attachmentId = insertAttachment(recordId, fileName, contentType)
            insertDocument(1, attachmentId)
            val result = service.register(
                AttachmentTransferService.RegisterTransferRequest(
                    purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
                    attachmentId = attachmentId,
                    accountCode = "acc-a",
                    folder = "INBOX",
                    uidValidity = uidValidityOverride ?: box.uidValidity,
                    imapUid = uid,
                    partPath = partPath,
                    messageId = messageId,
                    fileName = fileName,
                    contentType = contentType
                )
            )
            return result.transfer.id!!
        }

        val missingUid = registerFor(99, "m99")
        val wrongValidity = registerFor(1, "m1", uidValidityOverride = 999)
        val wrongMessageId = registerFor(2, "not-m2")
        val wrongPart = registerFor(3, "m3", partPath = "7")
        val wrongType = registerFor(4, "m4", contentType = "image/png")
        listOf(missingUid, wrongValidity, wrongMessageId, wrongPart, wrongType).forEach {
            service.enqueueTransferByIds(listOf(it), "admin")
        }

        worker.start()
        listOf(missingUid, wrongValidity, wrongMessageId, wrongPart, wrongType).forEach {
            awaitState(it, setOf("SOURCE_UNAVAILABLE"))
        }
        Thread.sleep(2_000) // 失败不自动无限重试
        worker.stop()

        assertEquals("SOURCE_UNAVAILABLE", stateOf(missingUid))
        assertEquals("SOURCE_MISSING", errorCodeOf(missingUid))
        assertEquals("SOURCE_UNAVAILABLE", stateOf(wrongValidity))
        assertEquals("UIDVALIDITY_MISMATCH", errorCodeOf(wrongValidity))
        assertEquals("SOURCE_UNAVAILABLE", stateOf(wrongMessageId))
        assertEquals("MESSAGE_ID_MISMATCH", errorCodeOf(wrongMessageId))
        assertEquals("SOURCE_UNAVAILABLE", stateOf(wrongPart))
        assertEquals("PART_NOT_FOUND", errorCodeOf(wrongPart))
        assertEquals("SOURCE_UNAVAILABLE", stateOf(wrongType))
        assertEquals("PART_TYPE_MISMATCH", errorCodeOf(wrongType))
        // 全部在流开始前的校验阶段拒绝：无任何 part 内容读取
        assertEquals(0, started.tracker().totalDownloads())
        // 失败后无自动重试
        listOf(missingUid, wrongValidity, wrongMessageId, wrongPart, wrongType).forEach {
            assertEquals("SOURCE_UNAVAILABLE", stateOf(it))
        }
    }

    // ------------------------------------------------------------------
    // I-1/I-2：DMARC purpose 规则与未知 purpose 保持未执行
    // ------------------------------------------------------------------

    @Test
    fun `DMARC registration rules hold and unknown-purpose rows stay queued unexecuted`() {
        seedExpert(1)
        val box = FixtureMailbox(
            "acc-d", "pwd", 7,
            listOf(
                fixtureMail(1, "d1", "report.xml", contentBytes(64, 8), attachmentContentType = "application/xml"),
                fixtureMail(2, "m2", "cv.pdf", contentBytes(64, 9))
            )
        )
        val started = startServer(box)
        seedSenderAccount("acc-d", "acc-d", "pwd", started.port)

        val dmarc = service.register(
            AttachmentTransferService.RegisterTransferRequest(
                purpose = MailAttachmentTransfer.PURPOSE_DMARC,
                attachmentId = null,
                accountCode = "acc-d",
                folder = "INBOX",
                uidValidity = 7,
                imapUid = 1,
                partPath = "2",
                messageId = "d1",
                fileName = "report.xml",
                contentType = "application/xml"
            )
        )
        assertTrue(dmarc.created)
        assertNull(dmarc.transfer.attachmentId)
        // MATERIAL 必须附件；DMARC 禁止附件
        assertIllegalArgument {
            service.register(
                AttachmentTransferService.RegisterTransferRequest(
                    purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
                    attachmentId = null,
                    accountCode = "acc-d",
                    folder = "INBOX",
                    uidValidity = 7,
                    imapUid = 2,
                    partPath = "2",
                    fileName = "cv.pdf"
                )
            )
        }
        assertIllegalArgument {
            service.register(
                AttachmentTransferService.RegisterTransferRequest(
                    purpose = MailAttachmentTransfer.PURPOSE_DMARC,
                    attachmentId = 999L,
                    accountCode = "acc-d",
                    folder = "INBOX",
                    uidValidity = 7,
                    imapUid = 1,
                    partPath = "2",
                    fileName = "report.xml"
                )
            )
        }
        // DMARC 只能由 SYSTEM 请求；同源重复登记返回同一行
        assertIllegalArgument {
            service.enqueueTransferByIds(listOf(dmarc.transfer.id!!), "admin")
        }
        service.enqueueTransferByIds(listOf(dmarc.transfer.id!!), AttachmentTransferService.SYSTEM_REQUESTER)
        val dupDmarc = service.register(
            AttachmentTransferService.RegisterTransferRequest(
                purpose = MailAttachmentTransfer.PURPOSE_DMARC,
                attachmentId = null,
                accountCode = "acc-d",
                folder = "INBOX",
                uidValidity = 7,
                imapUid = 1,
                partPath = "2",
                messageId = "d1",
                fileName = "report.xml",
                contentType = "application/xml"
            )
        )
        assertFalse(dupDmarc.created)
        assertEquals(dmarc.transfer.id, dupDmarc.transfer.id)

        // MATERIAL 行照常走完
        val material = registerMaterial(1, "acc-d", box, box.messages[1], "cv.pdf")
        enqueueMaterial(1, material)

        worker.start()
        awaitState(material, setOf("STORED"))
        Thread.sleep(1_500) // 给若干轮领取机会
        worker.stop()

        // 无 DMARC 消费者：DMARC 行保持 QUEUED 未执行且报配置错误；MATERIAL 完成
        assertEquals("QUEUED", stateOf(dmarc.transfer.id!!))
        assertTrue("DMARC" in worker.lastMissingConsumerPurposes())
        assertNull(started.tracker().downloadsByKey()["acc-d:1"])
        assertEquals("STORED", stateOf(material))
    }

    // ------------------------------------------------------------------
    // I-4/就绪：已就绪旧附件不再入队
    // ------------------------------------------------------------------

    @Test
    fun `ready legacy attachment is reported already-ready and is never queued`() {
        seedExpert(1)
        val readyDir = Path.of(properties.basePath, "legacy")
        Files.createDirectories(readyDir)
        val readyPath = readyDir.resolve("old-cv.pdf")
        Files.write(readyPath, contentBytes(128, 1))
        val mailRecordId = insertMailRecord(1, "old")
        val attachmentId = insertAttachment(mailRecordId, "old-cv.pdf", "application/pdf")
        insertDocument(1, attachmentId)
        jdbcTemplate.update("UPDATE mail_attachment SET storage_path = ? WHERE id = ?", readyPath.toString(), attachmentId)

        val result = service.enqueueMaterial(1, listOf(attachmentId), "admin")

        assertEquals(1, result.alreadyReadyCount)
        assertEquals(0, result.acceptedCount)
        assertTrue(result.items.single().alreadyReady)
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mail_attachment_transfer WHERE attachment_id = ?",
            Long::class.java, attachmentId
        ))
    }

    // ------------------------------------------------------------------
    // 受控 Drive 外链来源（partPath=gdrive:{fileId}）：固定域 HTTP 取件
    // ------------------------------------------------------------------

    @Test
    fun `drive transfer downloads through the fixed endpoint without a sender account`() {
        seedExpert(1)
        val payload = contentBytes(2048, 21)
        val drive = startDriveServer(mapOf(DRIVE_FILE_ID to DriveHttpFile(bytes = payload)))
        // 不建 mail_sender_account：Drive 来源不依赖邮箱凭据
        val transferId = registerDriveMaterial(1, "acc-drive-missing", DRIVE_FILE_ID)
        val attachmentId = attachmentIdOfTransfer(transferId)
        val enqueued = enqueueMaterial(1, transferId)
        assertEquals(1, enqueued.acceptedCount)
        assertEquals(0, drive.totalRequests(), "登记与入队阶段绝不触网（I-2）")

        worker.start()
        awaitState(transferId, setOf("STORED"))
        worker.stop()

        assertEquals(1, drive.requestCount(DRIVE_FILE_ID), "恰好下载一次")
        val storagePath = jdbcTemplate.queryForObject(
            "SELECT storage_path FROM mail_attachment WHERE id = ?", String::class.java, attachmentId
        )
        assertNotNull(storagePath)
        assertTrue(storagePath!!.endsWith("content.bin"), "unexpected storage path: $storagePath")
        assertTrue(Files.readAllBytes(Path.of(storagePath)).contentEquals(payload))
        assertEquals(payload.size.toLong(), jdbcTemplate.queryForObject(
            "SELECT file_size FROM mail_attachment WHERE id = ?", Long::class.java, attachmentId
        ))
        assertTrue(listPartFiles().isEmpty(), ".part must never be left visible: ${listPartFiles()}")
    }

    @Test
    fun `mixed drive and mime sources both complete in one worker run`() {
        seedExpert(1)
        val drivePayload = contentBytes(1024, 31)
        val drive = startDriveServer(mapOf(DRIVE_FILE_ID to DriveHttpFile(bytes = drivePayload)))
        val box = FixtureMailbox(
            "acc-a", "pwd", 7,
            listOf(fixtureMail(1, "m1", "cv.pdf", contentBytes(4096, 32)))
        )
        val imap = startServer(box)
        seedSenderAccount("acc-a", "acc-a", "pwd", imap.port)
        val mime = registerMaterial(1, "acc-a", box, box.messages[0], "cv.pdf")
        val driveTransfer = registerDriveMaterial(1, "acc-drive-missing", DRIVE_FILE_ID)
        enqueueMaterial(1, mime, driveTransfer)

        worker.start()
        listOf(mime, driveTransfer).forEach { awaitState(it, setOf("STORED")) }
        worker.stop()

        assertEquals(1, imap.tracker().downloadsByKey()["acc-a:1"], "数字 partPath 仍走 IMAP 取件")
        assertEquals(1, drive.requestCount(DRIVE_FILE_ID), "gdrive partPath 走固定 HTTP 端点")
        assertEquals("STORED", stateOf(mime))
        assertEquals("STORED", stateOf(driveTransfer))
    }

    @Test
    fun `drive responses that cannot be served are source unavailable`() {
        seedExpert(1)
        val redirectId = "redirect-id"
        val htmlId = "html-id"
        val noDispositionId = "no-disposition-id"
        val forbiddenId = "forbidden-id"
        val missingId = "missing-id"
        startDriveServer(
            mapOf(
                redirectId to DriveHttpFile(
                    status = 302, contentType = "text/html", contentDisposition = null
                ),
                htmlId to DriveHttpFile(
                    bytes = "not a file".toByteArray(StandardCharsets.UTF_8),
                    contentType = "text/html"
                ),
                noDispositionId to DriveHttpFile(
                    bytes = "bytes".toByteArray(StandardCharsets.UTF_8),
                    contentDisposition = null
                ),
                forbiddenId to DriveHttpFile(
                    status = 403, contentType = "text/html", contentDisposition = null
                ),
                missingId to DriveHttpFile(
                    status = 404, contentType = "text/html", contentDisposition = null
                )
            )
        )
        val transfers = listOf(redirectId, htmlId, noDispositionId, forbiddenId, missingId)
            .map { registerDriveMaterial(1, "acc-drive", it, "x.zip") }
        enqueueMaterial(1, *transfers.toLongArray())

        worker.start()
        transfers.forEach { awaitState(it, setOf("SOURCE_UNAVAILABLE")) }
        Thread.sleep(1_000) // 失败不自动重试
        worker.stop()

        transfers.forEach { assertFalse(Files.exists(finalPath(it)), "no final file for transfer $it") }
        assertTrue(listPartFiles().isEmpty(), ".part must be removed on failure: ${listPartFiles()}")
        assertEquals("REDIRECT", errorCodeOf(transfers[0]))
        assertEquals("NOT_AN_ATTACHMENT", errorCodeOf(transfers[1]))
        assertEquals("NOT_AN_ATTACHMENT", errorCodeOf(transfers[2]))
        assertEquals("HTTP_403", errorCodeOf(transfers[3]))
        assertEquals("HTTP_404", errorCodeOf(transfers[4]))
    }

    @Test
    fun `drive transport failures and oversized streams fail as retryable failures`() {
        seedExpert(1)
        val serverErrorId = "server-error-id"
        val brokenId = "broken-id"
        val declaredTooLargeId = "declared-too-large-id"
        val streamTooLargeId = "stream-too-large-id"
        startDriveServer(
            mapOf(
                serverErrorId to DriveHttpFile(
                    status = 500, contentType = "text/html", contentDisposition = null
                ),
                brokenId to DriveHttpFile(
                    bytes = contentBytes(64 * 1024, 41),
                    declaredContentLength = 64L * 1024,
                    truncateAfterBytes = 8 * 1024
                ),
                declaredTooLargeId to DriveHttpFile(
                    bytes = contentBytes(1024, 42),
                    declaredContentLength = PER_FILE_MAX_BYTES + 1
                ),
                streamTooLargeId to DriveHttpFile(
                    bytes = ByteArray((PER_FILE_MAX_BYTES + 512 * 1024L).toInt()) { 5 },
                    chunked = true,
                    // 逐块慢发：客户端必须在服务端发完/关闭之前先撞到流式上限，
                    // 否则可能先收到连接重置（那属于「连接中断」而不是「超限」）。
                    chunkDelayMs = 30
                )
            )
        )
        val transfers = listOf(serverErrorId, brokenId, declaredTooLargeId, streamTooLargeId)
            .map { registerDriveMaterial(1, "acc-drive", it, "x.zip") }
        enqueueMaterial(1, *transfers.toLongArray())

        worker.start()
        transfers.forEach { awaitState(it, setOf("FAILED")) }
        worker.stop()

        assertEquals("HTTP_5XX", errorCodeOf(transfers[0]), "5xx 必须落可重试失败: ${errorMessageOf(transfers[0])}")
        assertEquals(
            "INCOMPLETE_DOWNLOAD",
            errorCodeOf(transfers[1]),
            "半截下载绝不能当成功: ${errorMessageOf(transfers[1])}"
        )
        assertEquals(
            "LIMIT_EXCEEDED",
            errorCodeOf(transfers[2]),
            "声明长度超限立即失败: ${errorMessageOf(transfers[2])}"
        )
        assertEquals(
            "LIMIT_EXCEEDED",
            errorCodeOf(transfers[3]),
            "未知长度仍受流式上限约束: ${errorMessageOf(transfers[3])}"
        )
        assertEquals(
            PER_FILE_MAX_BYTES,
            jdbcTemplate.queryForObject(
                "SELECT bytes_downloaded FROM mail_attachment_transfer WHERE id = ?",
                Long::class.java, transfers[3]
            )
        )
        assertTrue(listPartFiles().isEmpty(), ".part must be removed on failure: ${listPartFiles()}")
    }

    @Test
    fun `slow drive source is aborted by the watchdog and leaves no artifacts`() {
        seedExpert(1)
        val slowId = "slow-id"
        val drive = startDriveServer(
            mapOf(
                slowId to DriveHttpFile(
                    bytes = ByteArray(800 * 1024) { 7 },
                    chunkBytes = 64 * 1024,
                    chunkDelayMs = 1_500
                )
            )
        )
        val box = FixtureMailbox(
            "acc-a", "pwd", 7,
            listOf(fixtureMail(1, "m1", "cv.pdf", contentBytes(20_000, 51)))
        )
        val imap = startServer(box)
        seedSenderAccount("acc-a", "acc-a", "pwd", imap.port)
        val slow = registerDriveMaterial(1, "acc-drive", slowId)
        val fast = registerMaterial(1, "acc-a", box, box.messages[0], "cv.pdf")
        val enqueuedAt = System.currentTimeMillis()
        enqueueMaterial(1, slow, fast)

        worker.start()
        awaitState(slow, setOf("FAILED"), timeoutMs = 30_000)
        val elapsedMs = System.currentTimeMillis() - enqueuedAt
        awaitState(fast, setOf("STORED"))
        worker.stop()

        assertEquals("FAILED", stateOf(slow))
        assertEquals("TIMEOUT", errorCodeOf(slow))
        assertTrue(
            elapsedMs in 4_000..20_000,
            "slow drive transfer failed after ${elapsedMs}ms (total deadline is 5s)"
        )
        assertTrue(jdbcTemplate.queryForObject(
            "SELECT bytes_downloaded FROM mail_attachment_transfer WHERE id = ?",
            Long::class.java, slow
        ) > 0)
        assertFalse(Files.exists(finalPath(slow)), "aborted drive transfer must not keep a final file")
        assertTrue(listPartFiles().isEmpty(), ".part must be removed on abort: ${listPartFiles()}")
        assertEquals("STORED", stateOf(fast), "其余来源继续完成")
        assertEquals(1, drive.requestCount(slowId))
    }

    /**
     * 走真实登记写链（[MailAttachmentService.saveInboundAttachments] 的 metadata 分流）建
     * Drive 附件与 METADATA_ONLY transfer 行：证明 `gdrive:{fileId}` 自然满足现有
     * attachment/transfer/document 契约，无需新字段与新分支。
     */
    private fun registerDriveMaterial(
        contactId: Long,
        accountCode: String,
        fileId: String,
        fileName: String = "China_Collaborator.zip"
    ): Long {
        val mailRecordId = insertMailRecord(contactId, "drive-$fileId")
        mailAttachmentService.saveInboundAttachments(
            expertContactId = contactId,
            mailRecordId = mailRecordId,
            attachments = listOf(
                ReceivedMailAttachment(
                    fileName = fileName,
                    contentType = null,
                    content = null,
                    source = ImapAttachmentSource(
                        accountCode = accountCode,
                        folder = "INBOX",
                        uidValidity = 7L,
                        uid = 1L,
                        partPath = GoogleDriveMaterialSource.partPathFor(fileId),
                        messageId = "drive-$fileId",
                        encodedSize = null,
                        disposition = GoogleDriveMaterialSource.EXTERNAL_LINK_DISPOSITION
                    )
                )
            )
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM mail_attachment_transfer WHERE part_path = ?",
            Long::class.java,
            GoogleDriveMaterialSource.partPathFor(fileId)
        )!!
    }

    private fun startDriveServer(files: Map<String, DriveHttpFile>): DriveHttpFixtureServer {
        val started = DriveHttpFixtureServer(files)
        driveServer = started
        DriveDownloadEndpointHolder.baseUrl = started.baseUrl
        return started
    }

    // ------------------------------------------------------------------
    // 数据构造助手
    // ------------------------------------------------------------------

    private fun fixtureMail(
        uid: Long,
        messageId: String?,
        attachmentName: String,
        bytes: ByteArray,
        slowChunkDelayMs: Long = 0,
        attachmentContentType: String = "application/pdf"
    ): FixtureMail {
        val text = "fixture body $uid".toByteArray(StandardCharsets.UTF_8)
        val attachment = FixturePart(
            partNumber = 2,
            contentType = attachmentContentType,
            fileName = attachmentName,
            bytes = bytes,
            slowChunkDelayMs = slowChunkDelayMs
        )
        return FixtureMail(uid = uid, messageId = messageId, textBytes = text, attachments = listOf(attachment))
    }

    private fun contentBytes(size: Int, seed: Int): ByteArray =
        ByteArray(size) { ((it + seed) % 251).toByte() }
}

// ---------------------------------------------------------------------------
// Spring 测试配置：TransactionTemplate bean 已由
// AttachmentTransferTransactionConfig（main 侧，worker 文件内）提供。
// ---------------------------------------------------------------------------

@TestConfiguration
@EnableConfigurationProperties(MailAttachmentStorageProperties::class)
class AttachmentTransferWorkerItConfig {
    /**
     * 生产构造恒指向 `drive.usercontent.google.com`；IT 只替换 endpoint factory 为 loopback
     * fixture（package-internal 构造，业务代码无法传入任意下载 URL）。
     */
    @Bean
    fun googleDriveAttachmentContentFetcher(
        properties: MailAttachmentStorageProperties
    ): GoogleDriveAttachmentContentFetcher =
        GoogleDriveAttachmentContentFetcher(properties) { fileId ->
            URL("${DriveDownloadEndpointHolder.baseUrl}/download?id=$fileId")
        }
}

/** IT 内把固定下载域替换为 loopback fixture 的持有者（每个用例在启动 fixture 时写入）。 */
object DriveDownloadEndpointHolder {
    @Volatile
    var baseUrl: String = "http://127.0.0.1:1"
}

// ---------------------------------------------------------------------------
// 本地 IMAP fixture（JDK socket；按 JavaMail 1.6.7 实际命令应答）
// ---------------------------------------------------------------------------

data class FixturePart(
    /** multipart 内 part 号（1-based）。 */
    val partNumber: Int,
    val contentType: String,
    val fileName: String,
    val bytes: ByteArray,
    /** 慢源：每个内容响应发送前延迟（块间慢服务端；响应本身给全量请求块）。 */
    val slowChunkDelayMs: Long = 0
) {
    val type: String get() = contentType.substringBefore("/").trim()
    val subtype: String get() = contentType.substringAfter("/", "octet-stream").trim()

    fun delayBeforeResponseMs(): Long = slowChunkDelayMs
}

data class FixtureMail(
    val uid: Long,
    val messageId: String?,
    val textBytes: ByteArray,
    val attachments: List<FixturePart>
)

data class FixtureMailbox(
    val username: String,
    val password: String,
    val uidValidity: Long,
    val messages: List<FixtureMail>
)

/** 下载并发观测：每条连接一次领取只计一次下载会话；最大并发按区间重叠计算。 */
class DownloadTracker {
    private val lock = Any()
    private class Session(
        val account: String,
        val uid: Long,
        val startMs: Long,
        var endMs: Long? = null
    )

    private val sessions = ArrayList<Session>()
    private val downloads = HashMap<String, Int>()
    private val interruptedAccounts = HashSet<String>()

    fun contentStart(account: String, uid: Long) {
        synchronized(lock) {
            sessions.add(Session(account, uid, System.currentTimeMillis()))
            val key = "$account:$uid"
            downloads[key] = (downloads[key] ?: 0) + 1
        }
    }

    fun contentStop(account: String) {
        synchronized(lock) {
            // 一条连接 = 一个下载会话；关停按账号找到最近一个未结束会话。
            val open = sessions.lastOrNull { it.account == account && it.endMs == null }
            open?.endMs = System.currentTimeMillis()
        }
    }

    fun markInterrupted(account: String) {
        synchronized(lock) {
            interruptedAccounts.add(account)
        }
    }

    fun downloadsByKey(): Map<String, Int> = synchronized(lock) { HashMap(downloads) }

    fun totalDownloads(): Int = synchronized(lock) { downloads.values.sum() }

    /** 快照（应至少在 worker.stop() 后再等 ~700ms，等 fixture 会话收尾落账）。 */
    fun maxGlobalConcurrent(): Int = synchronized(lock) { maxOverlap(sessions) }

    fun maxPerAccountConcurrent(account: String): Int =
        synchronized(lock) { maxOverlap(sessions.filter { it.account == account }) }

    fun wasInterruptedMidDownload(account: String): Boolean =
        synchronized(lock) { account in interruptedAccounts }

    private fun maxOverlap(list: List<Session>): Int {
        if (list.isEmpty()) return 0
        data class Event(val at: Long, val delta: Int)
        val events = list.flatMap {
            listOf(Event(it.startMs, 1), Event(it.endMs ?: System.currentTimeMillis(), -1))
        }.sortedBy { it.at }
        var running = 0
        var max = 0
        for (e in events) {
            running += e.delta
            max = maxOf(max, running)
        }
        return max
    }
}

/**
 * 极简 IMAP 服务器：每连接一个线程；应答 LOGIN/EXAMINE/NOOP/CLOSE/LOGOUT、
 * UID FETCH (UID)、FETCH (ENVELOPE INTERNALDATE RFC822.SIZE / BODYSTRUCTURE /
 * BODY.PEEK[sec]<start.cnt>)。消息 uid 恒等于 seq。
 */
class ImapFixtureServer(
    private val boxes: Map<String, FixtureMailbox>
) : AutoCloseable {
    val port: Int
    private val serverSocket: ServerSocket
    private val tracker = DownloadTracker()
    private val acceptThread: Thread

    init {
        serverSocket = ServerSocket(0, 128, InetAddress.getLoopbackAddress())
        port = serverSocket.localPort
        acceptThread = Thread {
            while (!serverSocket.isClosed) {
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

    fun tracker(): DownloadTracker = tracker

    private class Session(
        var user: String? = null,
        var box: FixtureMailbox? = null,
        /** 本连接是否已经开始过内容下载（无论完成与否），用于连接结束时关账。 */
        var startedContent: Boolean = false,
        /** 是否正处于内容下载中途（EOF 时用于判定连接被中断）。 */
        var downloading: Boolean = false
    )

    private fun handle(socket: Socket) {
        val session = Session()
        try {
            socket.use {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
                writeLine(writer, "* OK [CAPABILITY IMAP4rev1 UIDPLUS] fixture ready")
                var loggedOut = false
                while (!loggedOut) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val tag = line.substringBefore(" ")
                    val command = line.substringAfter(" ").trim()
                    loggedOut = try {
                        dispatch(writer, tag, command, session, reader, socket)
                    } catch (e: IOException) {
                        if (session.downloading && session.user != null) tracker.markInterrupted(session.user!!)
                        throw e
                    } catch (e: Exception) {
                        writeLine(writer, "$tag BAD fixture error: ${e.javaClass.simpleName}")
                        false
                    }
                }
                // 下载中途客户端断开（EOF）＝连接被关闭的现场
                if (session.downloading && session.user != null) {
                    tracker.markInterrupted(session.user!!)
                }
            }
        } catch (e: IOException) {
            if (session.downloading && session.user != null) tracker.markInterrupted(session.user!!)
        } finally {
            // 只要连接开始过内容下载，结束（含完整成功）就关账；未开始过则不关。
            if (session.startedContent && session.user != null) {
                tracker.contentStop(session.user!!)
            }
        }
    }

    private fun dispatch(
        writer: BufferedWriter,
        tag: String,
        command: String,
        session: Session,
        reader: BufferedReader,
        socket: Socket
    ): Boolean {
        return when {
            command.startsWith("CAPABILITY") -> {
                writeLine(writer, "* CAPABILITY IMAP4rev1 UIDPLUS")
                ok(writer, tag, "CAPABILITY completed")
                false
            }
            command.startsWith("LOGIN ") -> {
                val parts = command.removePrefix("LOGIN ").split(" ").map { stripQuotes(it) }
                val box = if (parts.size >= 1) boxes[parts[0]] else null
                if (box != null && parts.size >= 2 && box.password == parts[1]) {
                    session.user = parts[0]
                    session.box = box
                    ok(writer, tag, "LOGIN completed")
                } else {
                    writeLine(writer, "$tag NO LOGIN failed")
                }
                false
            }
            command.startsWith("SELECT ") || command.startsWith("EXAMINE ") -> {
                val folder = stripQuotes(command.substringAfter(" ").trim())
                val box = session.box
                if (box == null) {
                    writeLine(writer, "$tag NO not authenticated")
                } else if (folder.equals("INBOX", ignoreCase = true)) {
                    writeLine(writer, "* FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft)")
                    writeLine(writer, "* ${box.messages.size} EXISTS")
                    writeLine(writer, "* 0 RECENT")
                    writeLine(writer, "* OK [UIDVALIDITY ${box.uidValidity}] UIDVALIDITY")
                    writeLine(writer, "* OK [UIDNEXT ${box.messages.size + 1}] Predicted next UID")
                    ok(writer, tag, "EXAMINE completed")
                } else {
                    writeLine(writer, "$tag NO no such folder")
                }
                false
            }
            command.startsWith("UID ") && command.removePrefix("UID ").trim().startsWith("FETCH ") ->
                handleFetch(
                    writer, tag,
                    command.removePrefix("UID ").trim().removePrefix("FETCH ").trim(),
                    session, reader, socket
                )
            command.startsWith("FETCH ") ->
                handleFetch(writer, tag, command.removePrefix("FETCH ").trim(), session, reader, socket)
            command.startsWith("UID ") -> {
                ok(writer, tag, "UID completed")
                false
            }
            command.startsWith("LOGOUT") -> {
                writeLine(writer, "* BYE logging out")
                ok(writer, tag, "LOGOUT completed")
                true
            }
            command.startsWith("NOOP") -> {
                ok(writer, tag, "NOOP completed")
                false
            }
            command.startsWith("CLOSE") || command.startsWith("EXPUNGE") ||
                command.startsWith("STATUS") || command.startsWith("ID") -> {
                ok(writer, tag, "${command.substringBefore(" ")} completed")
                false
            }
            else -> {
                ok(writer, tag, "${command.substringBefore(" ")} completed")
                false
            }
        }
    }

    private val contentSection = Regex("""BODY(?:\.PEEK)?\[([0-9.]+)](?:<(\d+)\.(\d+)>)?""")
    private val contentAny = Regex("""BODY(?:\.PEEK)?\[[^\]]*]""")

    private fun handleFetch(
        writer: BufferedWriter,
        tag: String,
        rest: String,
        session: Session,
        reader: BufferedReader,
        socket: Socket
    ): Boolean {
        val box = session.box
        if (box == null) {
            writeLine(writer, "$tag NO not authenticated")
            return false
        }
        val set = rest.substringBefore(" ").trim()
        val parenStart = rest.indexOf('(')
        if (parenStart < 0) {
            ok(writer, tag, "FETCH completed")
            return false
        }
        val parenEnd = rest.indexOf(')', parenStart)
        val items = rest.substring(parenStart + 1, if (parenEnd < 0) rest.length else parenEnd)
            .trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val seq = set.toLongOrNull()
        if (seq == null) {
            ok(writer, tag, "FETCH completed")
            return false
        }
        val mail = box.messages.getOrNull((seq - 1).toInt())
        if (mail == null) {
            // uid/seq 不存在：无 untagged 应答（JavaMail 视为 message 不存在）
            ok(writer, tag, "FETCH completed")
            return false
        }

        var contentPart: FixturePart? = null
        var contentFinishedFile = false
        val parts = mutableListOf<String>()
        for (item in items) {
            when {
                item == "UID" -> parts.add("UID ${mail.uid}")
                item == "FLAGS" -> parts.add("FLAGS ()")
                item == "ENVELOPE" -> parts.add("ENVELOPE ${envelope(mail)}")
                item == "INTERNALDATE" -> parts.add("INTERNALDATE \"1-Sep-2026 12:00:00 +0800\"")
                item == "RFC822.SIZE" ->
                    parts.add("RFC822.SIZE ${mail.textBytes.size + mail.attachments.sumOf { it.bytes.size }}")
                item == "BODYSTRUCTURE" -> parts.add("BODYSTRUCTURE ${bodyStructure(mail)}")
                contentSection.matches(item) -> {
                    val m = contentSection.matchEntire(item)!!
                    val section = m.groupValues[1]
                    val start = m.groupValues[2].toLongOrNull() ?: 0L
                    val count = m.groupValues[3].toLongOrNull()
                    val part = mail.attachments.firstOrNull { it.partNumber.toString() == section }
                    if (part == null || start >= part.bytes.size) {
                        parts.add("BODY[$section] {0}")
                    } else {
                        contentPart = part
                        val from = start.toInt()
                        // 给全量请求块（JavaMail 把中途短给视为 EOF）；慢速通过响应前延迟实现。
                        val length = if (count == null) part.bytes.size - from
                        else count.toInt().coerceAtMost(part.bytes.size - from)
                        if (length > 0) {
                            parts.add("BODY[$section]<$from> {$length}<<LITERAL>>")
                            if (from + length >= part.bytes.size) {
                                contentFinishedFile = true
                            }
                        } else {
                            parts.add("BODY[$section]<$from> {0}")
                        }
                    }
                }
                contentAny.matches(item) -> parts.add("BODY[] {0}")
                else -> Unit
            }
        }

        val isNewDownload = !session.downloading && contentPart != null
        try {
            if (contentPart == null) {
                writeLine(writer, "* $seq FETCH (${parts.joinToString(" ")})")
                ok(writer, tag, "FETCH completed")
                return false
            }
            if (isNewDownload) {
                session.startedContent = true
                session.downloading = true
                tracker.contentStart(session.user ?: "unknown", mail.uid)
            }
            val literalHead = parts.first { it.endsWith("<<LITERAL>>") }
                .removeSuffix("<<LITERAL>>")
            val from = literalHead.substringAfter("<").substringBefore(">").toInt()
            val length = literalHead.substringAfter("{").substringBefore("}").toInt()
            val payload = contentPart!!.bytes.copyOfRange(from, from + length)
            val delayMs = contentPart!!.delayBeforeResponseMs()
            if (delayMs > 0) {
                // 慢源：块间延迟（客户端此时阻塞等响应首字节；watchdog 的 forceClose
                // 最多等一个延迟窗口即可拿到协议锁并关连接）。
                Thread.sleep(delayMs)
            }
            val nonLiteral = parts.filterNot { it.endsWith("<<LITERAL>>") }
            writeLine(writer, "* $seq FETCH (${(nonLiteral + literalHead).joinToString(" ")}")
            socket.getOutputStream().write(payload)
            socket.getOutputStream().flush()
            writeLine(writer, ")")
            ok(writer, tag, "FETCH completed")
            if (contentFinishedFile) {
                session.downloading = false
            }
            return false
        } catch (e: IOException) {
            if (session.downloading && session.user != null) tracker.markInterrupted(session.user!!)
            throw e
        }
    }

    private fun envelope(mail: FixtureMail): String {
        val id = mail.messageId?.let { "\"$it\"" } ?: "NIL"
        return "(NIL NIL NIL NIL NIL NIL NIL NIL NIL $id)"
    }

    private fun bodyStructure(mail: FixtureMail): String {
        val textLen = mail.textBytes.size
        val parts = mutableListOf<String>()
        parts.add("(\"text\" \"plain\" (\"charset\" \"us-ascii\") NIL NIL \"7bit\" $textLen 1 NIL NIL NIL NIL)")
        for (part in mail.attachments) {
            parts.add(
                "(\"${part.type}\" \"${part.subtype}\" (\"name\" \"${part.fileName}\") NIL NIL " +
                    "\"8bit\" ${part.bytes.size} NIL (\"attachment\" (\"filename\" \"${part.fileName}\")) NIL NIL)"
            )
        }
        return "(${parts.joinToString(" ")} \"mixed\" (\"boundary\" \"----=_B_0\") NIL NIL)"
    }

    private fun stripQuotes(value: String): String =
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value.substring(1, value.length - 1)
        } else {
            value
        }

    private fun ok(writer: BufferedWriter, tag: String, text: String) {
        writeLine(writer, "$tag OK $text")
    }

    private fun writeLine(writer: BufferedWriter, line: String) {
        writer.write(line)
        writer.write("\r\n")
        writer.flush()
    }

    override fun close() {
        runCatching { serverSocket.close() }
    }
}

// ---------------------------------------------------------------------------
// 本地 HTTP fixture（JDK socket）：受控 Drive 下载端点的形状，按 fileId 返回预置响应
// ---------------------------------------------------------------------------

/** 单个 fileId 的预置响应（loopback HTTP fixture）。 */
data class DriveHttpFile(
    val bytes: ByteArray = ByteArray(0),
    val status: Int = 200,
    val contentType: String? = "application/octet-stream",
    val contentDisposition: String? = "attachment; filename=\"content.bin\"",
    /** 覆盖 Content-Length（用于「声明长度超限」与「提前断开」两种现场）。 */
    val declaredContentLength: Long? = null,
    /** 只写这么多字节就断开（模拟连接中断）。 */
    val truncateAfterBytes: Int? = null,
    /** 以 chunked 传输且不带 Content-Length（模拟未知长度）。 */
    val chunked: Boolean = false,
    val chunkBytes: Int = 64 * 1024,
    /** 每个数据块前的延迟（慢源；watchdog 必须能在总时限内断开）。 */
    val chunkDelayMs: Long = 0
)

/**
 * 极简 HTTP/1.1 fixture：每连接一个线程；只实现 `GET /download?id={fileId}`，按 fileId
 * 返回预置状态/头/内容，并统计每个 fileId 的请求次数（证明「只有显式获取才触网」）。
 */
class DriveHttpFixtureServer(private val files: Map<String, DriveHttpFile>) : AutoCloseable {
    private val serverSocket = ServerSocket(0, 128, InetAddress.getLoopbackAddress())
    val port: Int = serverSocket.localPort
    val baseUrl: String get() = "http://127.0.0.1:$port"

    private val requestCounts = ConcurrentHashMap<String, Int>()
    private val acceptThread = Thread { acceptLoop() }.apply {
        isDaemon = true
        start()
    }

    fun requestCount(fileId: String): Int = requestCounts[fileId] ?: 0

    fun totalRequests(): Int = requestCounts.values.sum()

    private fun acceptLoop() {
        while (!serverSocket.isClosed) {
            val socket = try {
                serverSocket.accept()
            } catch (e: IOException) {
                break
            }
            Thread { runCatching { handle(socket) } }
                .apply { isDaemon = true }
                .start()
        }
    }

    private fun handle(socket: Socket) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
            }
            if (!requestLine.startsWith("GET ")) {
                writeResponse(socket, DriveHttpFile(status = 405, contentDisposition = null))
                return
            }
            val target = requestLine.removePrefix("GET ").substringBefore(" ").trim()
            val fileId = target.substringAfter("id=", "").substringBefore("&")
            if (fileId.isNotBlank()) {
                requestCounts.merge(fileId, 1, Int::plus)
            }
            val file = files[fileId]
            if (file == null) {
                writeResponse(socket, DriveHttpFile(status = 404, contentDisposition = null))
                return
            }
            writeResponse(socket, file)
        }
    }

    private fun writeResponse(socket: Socket, file: DriveHttpFile) {
        val out = socket.getOutputStream()
        val head = StringBuilder()
        head.append("HTTP/1.1 ${file.status} ${reasonPhrase(file.status)}\r\n")
        file.contentType?.let { head.append("Content-Type: $it\r\n") }
        file.contentDisposition?.let { head.append("Content-Disposition: $it\r\n") }
        if (file.status == 200 && file.chunked) {
            head.append("Transfer-Encoding: chunked\r\n")
        } else {
            head.append("Content-Length: ${file.declaredContentLength ?: file.bytes.size.toLong()}\r\n")
        }
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray(StandardCharsets.ISO_8859_1))
        out.flush()
        if (file.status != 200 || file.bytes.isEmpty()) return

        val bodyBytes = file.truncateAfterBytes ?: file.bytes.size
        var offset = 0
        while (offset < bodyBytes) {
            val size = minOf(file.chunkBytes, bodyBytes - offset)
            if (file.chunkDelayMs > 0) {
                // 慢源：块间延迟（客户端阻塞等首字节；watchdog 到期硬断开这里就会写失败）。
                Thread.sleep(file.chunkDelayMs)
            }
            if (file.chunked) {
                // chunk 大小必须是十六进制。
                out.write("${Integer.toHexString(size)}\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            }
            out.write(file.bytes, offset, size)
            if (file.chunked) {
                out.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            }
            out.flush()
            offset += size
        }
        if (file.chunked) {
            out.write("0\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            out.flush()
        }
    }

    private fun reasonPhrase(status: Int): String = when (status) {
        200 -> "OK"
        302 -> "Found"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        500 -> "Internal Server Error"
        else -> "Status"
    }

    override fun close() {
        runCatching { serverSocket.close() }
    }
}
