package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.MeetingVideoException
import com.weibo.talentintroduction.document.domain.MeetingVideoLimits
import com.weibo.talentintroduction.document.domain.MeetingVideoRenameRequest
import com.weibo.talentintroduction.document.domain.MeetingVideoUploadRequest
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileInvalidRequestException
import com.weibo.talentintroduction.expert.domain.ExpertProfileLookupUnavailableException
import com.weibo.talentintroduction.expert.domain.ExpertProfileNotFoundException
import com.weibo.talentintroduction.expert.service.ExpertProfileIdentityResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.streams.toList

/**
 * 04（I-2–I-6/I-9/I-10）：命名、流边界、并发槽与失败清理。
 *
 * 真实件：真实文件流（生成式 InputStream，绝不构造大数组）、真实 64 KiB 缓冲、真实
 * ATOMIC_MOVE、真实 NFC 规范化、真实 TransactionTemplate（事务管理器替身）。
 * 替身：身份解析器与 JDBC 边界（内存行台账，被调用时回放），因此「成功/失败后是否留文件、
 * 是否落库、是否放了并发槽」都可断言。
 */
class ExpertMeetingVideoServiceTest {

    @TempDir
    lateinit var baseDir: Path

    private val jdbc = Mockito.mock(NamedParameterJdbcTemplate::class.java)
    private val resolver = Mockito.mock(ExpertProfileIdentityResolver::class.java)
    private lateinit var service: ExpertMeetingVideoService
    private lateinit var storageRoot: Path
    private var usableSpace: Long = Long.MAX_VALUE / 4
    private val rows = mutableListOf<MutableMap<String, Any?>>()
    private val inserted: MutableList<MapSqlParameterSource> = Collections.synchronizedList(mutableListOf())
    private var updateResult = 0

    private val knownExpert = "0000-0001"
    private val otherExpert = "0000-0002"

    @BeforeEach
    fun setUp() {
        storageRoot = baseDir.resolve("meeting-videos")
        usableSpace = Long.MAX_VALUE / 4
        rows.clear()
        inserted.clear()
        updateResult = 0
        service = ExpertMeetingVideoService(
            MailAttachmentStorageProperties(basePath = baseDir.toString()),
            resolver,
            jdbc,
            TransactionTemplate(Mockito.mock(PlatformTransactionManager::class.java)),
            { usableSpace }
        )
        ExpertIndexLevel.values().forEach { level ->
            listOf(knownExpert, otherExpert).forEach { id ->
                Mockito.`when`(resolver.requireKnown(id, level)).thenReturn(id.uppercase())
            }
            Mockito.`when`(resolver.requireKnown("missing-expert", level))
                .thenThrow(ExpertProfileNotFoundException("专家不存在"))
            Mockito.`when`(resolver.requireKnown(null, level))
                .thenThrow(ExpertManualProfileInvalidRequestException("缺少专家标识，无法保存"))
            Mockito.`when`(resolver.requireKnown("es-down", level))
                .thenThrow(ExpertProfileLookupUnavailableException("专家身份查询服务不可用，请稍后重试"))
        }
        stubJdbc()
    }

    // ------------------------------------------------------------------
    // 命名（I-3/I-4/I-5）
    // ------------------------------------------------------------------

    @Test
    fun `upload stores basename extension and default stem with real streaming to a uuid path`() {
        val view = upload(
            originalName = "顶会/材料/企业对接会.MP4",
            expectedSize = "5",
            body = ByteArrayInputStream("hello".toByteArray())
        )

        // 磁盘只用服务端 UUID；原始名只留去路径后的 basename；扩展名小写派生。
        assertTrue(view.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        assertEquals("企业对接会.MP4", view.originalFilename)
        assertEquals("企业对接会.mp4", view.displayFilename)
        assertEquals("mp4", view.extension)
        assertEquals(5L, view.byteLength)
        assertEquals(1L, view.version)
        assertEquals("admin", view.uploadedBy)
        assertEquals("0000-0001", view.expertId)
        assertTrue(view.uploadedAt.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+08:00")))
        assertEquals(view.uploadedAt, view.updatedAt)
        assertEquals("/api/experts/meeting-videos/${view.id}/preview?expertId=0000-0001", view.previewUrl)
        assertEquals("/api/experts/meeting-videos/${view.id}/download?expertId=0000-0001", view.downloadUrl)
        assertEquals("hello", String(Files.readAllBytes(storageRoot.resolve(view.id))))
        assertEquals(
            listOf(view.id),
            Files.list(storageRoot).use { stream -> stream.map { it.fileName.toString() }.toList() }
        )

        val insert = inserted.single()
        assertEquals(view.id, insert.getValue("id"))
        assertEquals("0000-0001", insert.getValue("expertKey"))
        assertEquals("企业对接会.MP4", insert.getValue("originalFilename"))
        assertEquals("企业对接会.mp4", insert.getValue("displayFilename"))
        assertEquals(5L, insert.getValue("byteLength"))
        assertEquals("admin", insert.getValue("uploadedBy"))
        assertEquals("admin", insert.getValue("updatedBy"))
        assertEquals(insert.getValue("uploadedAt"), insert.getValue("updatedAt"))
    }

    @Test
    fun `upload normalizes the stem to NFC keeps spaces and counts the full name against 100 code points`() {
        val decomposed = "e\u0301 会议" // e + combining acute → é
        val view = upload(nameStem = decomposed, expectedSize = "1", body = oneByte())
        assertEquals("é 会议.mp4", view.displayFilename)

        // 完整名（含扩展名）恰好 100 code point 接受、101 拒绝；超长要求修改而不是截断。
        val accepted = upload(nameStem = "名".repeat(96), expectedSize = "1", body = oneByte())
        assertEquals(100, accepted.displayFilename.codePointCount(0, accepted.displayFilename.length))
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
            upload(nameStem = "名".repeat(97), expectedSize = "1", body = oneByte())
        }
    }

    @Test
    fun `upload rejects illegal stems empty stems and unsupported extensions without touching the disk`() {
        listOf(
            "会议/名字", "会议\\名字", "会议:名字", "会议*名字", "会议?名字", "会议\"名字",
            "会议<名字", "会议>名字", "会议|名字", "会议.", "会议\u0007"
        ).forEach { stem ->
            assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
                upload(nameStem = stem, expectedSize = "1", body = oneByte())
            }
        }
        // 原始名只剩分隔符/去扩展名后主体为空：默认主体为空 → 400（不猜、不截断）。
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
            upload(originalName = ".mp4", expectedSize = "1", body = oneByte())
        }
        // 无后缀/白名单外后缀一律 415。
        listOf("会议", "会议.avi", "会议.exe", "会议.mp4.exe", "会议.").forEach { name ->
            assertVideoError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_VIDEO_TYPE") {
                upload(originalName = name, expectedSize = "1", body = oneByte())
            }
        }
        // 原始名超长、只剩路径分隔符：400，绝不截断。
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
            upload(originalName = "会" + "议".repeat(255) + ".mp4", expectedSize = "1", body = oneByte())
        }
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
            upload(originalName = "顶会/材料/", expectedSize = "1", body = oneByte())
        }
        assertFalse(Files.exists(storageRoot), "校验失败不得创建存储目录或文件")
        assertTrue(inserted.isEmpty())
    }

    // ------------------------------------------------------------------
    // 字节边界与流（I-2）
    // ------------------------------------------------------------------

    @Test
    fun `expectedSize declaredLength and stream counters decide 400 413 and empty rejections`() {
        listOf(null, "", "abc", "-1", "99999999999999999999").forEach { raw ->
            assertVideoError(HttpStatus.BAD_REQUEST, "VIDEO_SIZE_MISMATCH") {
                upload(expectedSize = raw, body = oneByte())
            }
        }
        assertVideoError(HttpStatus.PAYLOAD_TOO_LARGE, "VIDEO_TOO_LARGE") {
            upload(expectedSize = (MeetingVideoLimits.MAX_BYTES + 1).toString(), body = oneByte())
        }
        assertVideoError(HttpStatus.BAD_REQUEST, "EMPTY_VIDEO") {
            upload(expectedSize = "0", body = oneByte())
        }
        // expectedSize 恰好等于上限时预检通过：后续失败必须是字节数不符而不是 413。
        assertVideoError(HttpStatus.BAD_REQUEST, "VIDEO_SIZE_MISMATCH") {
            upload(expectedSize = MeetingVideoLimits.MAX_BYTES.toString(), body = oneByte())
        }
        // Content-Length 已知时必须与 expectedSize 相等；自身超限也 413。
        assertVideoError(HttpStatus.BAD_REQUEST, "VIDEO_SIZE_MISMATCH") {
            upload(expectedSize = "5", declaredLength = 4L, body = fiveBytes())
        }
        assertVideoError(HttpStatus.PAYLOAD_TOO_LARGE, "VIDEO_TOO_LARGE") {
            upload(expectedSize = "1", declaredLength = MeetingVideoLimits.MAX_BYTES + 1, body = oneByte())
        }
        assertEquals(5, upload(expectedSize = "5", declaredLength = 5L, body = fiveBytes()).byteLength)
        // 未知长度（chunked/无 Content-Length）按实际流计数：相等即通过。
        assertEquals(5, upload(expectedSize = "5", declaredLength = null, body = fiveBytes()).byteLength)

        // 实际字节数与 expectedSize 不符（更少/更多/零字节）都在流阶段拒绝。
        assertVideoError(HttpStatus.BAD_REQUEST, "VIDEO_SIZE_MISMATCH") {
            upload(expectedSize = "5", body = oneByte())
        }
        assertVideoError(HttpStatus.BAD_REQUEST, "VIDEO_SIZE_MISMATCH") {
            upload(expectedSize = "5", body = ByteArrayInputStream("123456".toByteArray()))
        }
        assertVideoError(HttpStatus.BAD_REQUEST, "EMPTY_VIDEO") {
            upload(expectedSize = "5", body = ByteArrayInputStream(ByteArray(0)))
        }
        assertNoTempResidue()
    }

    @Test
    fun `upload streams one gibibyte plus one byte with a fixed 64 KiB buffer and aborts with 413`() {
        val body = GeneratedStream(MeetingVideoLimits.MAX_BYTES + 1)
        assertVideoError(HttpStatus.PAYLOAD_TOO_LARGE, "VIDEO_TOO_LARGE") {
            upload(expectedSize = MeetingVideoLimits.MAX_BYTES.toString(), body = body)
        }
        // 固定 64 KiB 缓冲；到上限立即中止，不把整个流读完、也不把文件读进内存。
        assertEquals(MeetingVideoLimits.STREAM_BUFFER_BYTES, body.maxRequestedBuffer)
        assertTrue(body.produced <= MeetingVideoLimits.MAX_BYTES + MeetingVideoLimits.STREAM_BUFFER_BYTES)
        assertTrue(inserted.isEmpty())
        assertNoVisibleFiles()
    }

    @Test
    fun `upload writes a generated ten mebibyte stream exactly and never buffers it`() {
        val body = GeneratedStream(10L * 1024 * 1024)
        val view = upload(expectedSize = (10L * 1024 * 1024).toString(), body = body)
        assertEquals(10L * 1024 * 1024, Files.size(storageRoot.resolve(view.id)))
        assertEquals(10L * 1024 * 1024, view.byteLength)
        assertEquals(10L * 1024 * 1024, body.produced)
        assertEquals(MeetingVideoLimits.STREAM_BUFFER_BYTES, body.maxRequestedBuffer)
    }

    // ------------------------------------------------------------------
    // 失败清理与事务边界（I-6/I-9）
    // ------------------------------------------------------------------

    @Test
    fun `duplicate display name deletes the final file and maps to 409`() {
        Mockito.doAnswer { throw DuplicateKeyException("uk_expert_meeting_video_name") }
            .`when`(jdbc).update(
                Mockito.contains("INSERT INTO expert_meeting_video"),
                Mockito.any(MapSqlParameterSource::class.java)
            )
        assertVideoError(HttpStatus.CONFLICT, "VIDEO_NAME_CONFLICT") {
            upload(expectedSize = "3", body = threeBytes())
        }
        assertNoVisibleFiles()
    }

    @Test
    fun `database failure deletes the final file and maps to 500 without leaking internals`() {
        Mockito.doAnswer { throw DataAccessResourceFailureException("jdbc:mysql://db/expert_meeting_video down") }
            .`when`(jdbc).update(
                Mockito.contains("INSERT INTO expert_meeting_video"),
                Mockito.any(MapSqlParameterSource::class.java)
            )
        val failure = assertThrows(MeetingVideoException::class.java) {
            upload(expectedSize = "3", body = threeBytes())
        }
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.status)
        assertEquals("VIDEO_STORE_FAILED", failure.code)
        assertEquals("视频保存失败，请稍后重试", failure.message)
        assertNoVisibleFiles()
    }

    @Test
    fun `low usable space rejects with 507 before any write or database call`() {
        usableSpace = 1024L
        assertVideoError(HttpStatus.INSUFFICIENT_STORAGE, "VIDEO_STORAGE_FULL") {
            upload(expectedSize = "3", body = threeBytes())
        }
        assertNoVisibleFiles()
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `unwritable storage fails with 500 and leaves neither temp nor final files`() {
        Files.createDirectories(storageRoot)
        Files.setPosixFilePermissions(storageRoot, PosixFilePermissions.fromString("r-xr-xr-x"))
        try {
            val failure = assertThrows(MeetingVideoException::class.java) {
                upload(expectedSize = "3", body = threeBytes())
            }
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.status)
            assertEquals("VIDEO_STORE_FAILED", failure.code)
            assertTrue(inserted.isEmpty())
        } finally {
            Files.setPosixFilePermissions(storageRoot, PosixFilePermissions.fromString("rwxr-xr-x"))
        }
        assertNoVisibleFiles()
    }

    @Test
    fun `io failure with no space left maps to 507 and cleans the temp file`() {
        val failure = assertThrows(MeetingVideoException::class.java) {
            upload(expectedSize = "3", body = FailingStream("No space left on device"))
        }
        assertEquals(HttpStatus.INSUFFICIENT_STORAGE, failure.status)
        assertEquals("VIDEO_STORAGE_FULL", failure.code)
        assertTrue(inserted.isEmpty())
        assertNoVisibleFiles()
    }

    @Test
    fun `a known same-name upload is rejected before the request body is read or a slot is taken`() {
        rows += row(id = "existing", display = "已存在.mp4")
        val body = ReadForbiddenStream()

        // 04 I-5：NFC 后的完整名已存在时在读请求体之前 409；唯一索引仍是并发竞争的最终裁决。
        assertVideoError(HttpStatus.CONFLICT, "VIDEO_NAME_CONFLICT") {
            upload(originalName = "已存在.mp4", expectedSize = "3", body = body)
        }
        assertEquals(0, body.reads, "已知同名必须在读请求体之前拒绝")
        assertTrue(inserted.isEmpty())
        assertNoVisibleFiles()

        // 预检在读体与占槽之前：随后两次并发上传仍能各持一个槽。
        val started = CountDownLatch(2)
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit(Callable { upload(expectedSize = "3", body = GatedStream(started, gate)) })
            val second = pool.submit(Callable { upload(expectedSize = "3", body = GatedStream(started, gate)) })
            assertTrue(started.await(30, TimeUnit.SECONDS), "同名预检不得占用并发槽")
            gate.countDown()
            assertNotNull(first.get(30, TimeUnit.SECONDS))
            assertNotNull(second.get(30, TimeUnit.SECONDS))
        } finally {
            gate.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `cleanup failure keeps the typed outcome and cannot consume upload slots`() {
        val deleteAttempts = AtomicInteger()
        val failingCleanup = ExpertMeetingVideoService(
            MailAttachmentStorageProperties(basePath = baseDir.toString()),
            resolver,
            jdbc,
            TransactionTemplate(Mockito.mock(PlatformTransactionManager::class.java)),
            { usableSpace },
            { deleteAttempts.incrementAndGet(); throw IOException("cleanup denied") }
        )

        // 流写失败后的 temp 清理也失败：主结果仍是固定 400，不得被清理 IOException 顶成 500（更不得变成成功）。
        assertVideoError(HttpStatus.BAD_REQUEST, "VIDEO_SIZE_MISMATCH") {
            upload(target = failingCleanup, expectedSize = "5", body = threeBytes())
        }
        assertTrue(deleteAttempts.get() >= 1, "失败分支必须尝试过清理（IOException 被吞掉）")
        val residue = storageFiles()
        assertEquals(1, residue.size, "清理失败只留 temp 残留（已知副作用，人工核对）：$residue")
        assertTrue(residue.single().startsWith(".tmp-"), "写失败路径的残留必须是 .tmp-：$residue")
        assertTrue(inserted.isEmpty())

        // 清理失败消耗不掉并发槽：随后两次并发上传仍能各持一槽，第三个上传才是 429。
        val started = CountDownLatch(2)
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit(Callable { upload(target = failingCleanup, expectedSize = "3", body = GatedStream(started, gate)) })
            val second = pool.submit(Callable { upload(target = failingCleanup, expectedSize = "3", body = GatedStream(started, gate)) })
            assertTrue(started.await(30, TimeUnit.SECONDS), "清理失败不得漏放并发槽")
            assertVideoError(HttpStatus.TOO_MANY_REQUESTS, "VIDEO_UPLOAD_BUSY") {
                upload(target = failingCleanup, expectedSize = "3", body = threeBytes())
            }
            gate.countDown()
            assertNotNull(first.get(30, TimeUnit.SECONDS))
            assertNotNull(second.get(30, TimeUnit.SECONDS))
        } finally {
            gate.countDown()
            pool.shutdownNow()
        }
        assertEquals(2, inserted.size)
        assertTrue(deleteAttempts.get() >= 3, "成功分支的 temp 清理同样走注入的删除操作")
    }

    @Test
    fun `final cleanup failure after a unique conflict still answers 409`() {
        val failingCleanup = ExpertMeetingVideoService(
            MailAttachmentStorageProperties(basePath = baseDir.toString()),
            resolver,
            jdbc,
            TransactionTemplate(Mockito.mock(PlatformTransactionManager::class.java)),
            { usableSpace },
            { throw IOException("cleanup denied") }
        )
        Mockito.doAnswer { throw DuplicateKeyException("uk_expert_meeting_video_name") }
            .`when`(jdbc).update(
                Mockito.contains("INSERT INTO expert_meeting_video"),
                Mockito.any(MapSqlParameterSource::class.java)
            )

        // final 删除失败不得把唯一冲突的 409 改成 500，也不得吞掉失败变成成功。
        assertVideoError(HttpStatus.CONFLICT, "VIDEO_NAME_CONFLICT") {
            upload(target = failingCleanup, expectedSize = "3", body = threeBytes())
        }
        val residue = storageFiles()
        assertTrue(residue.none { it.startsWith(".tmp-") }, "tmp 已 ATOMIC_MOVE 成 final：$residue")
        assertEquals(1, residue.size, "清理失败的 final 残留是已知副作用：$residue")
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `two uploads hold the slots and a third concurrent upload is rejected with 429`() {
        val started = CountDownLatch(2)
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit(Callable { upload(expectedSize = "3", body = GatedStream(started, gate)) })
            val second = pool.submit(Callable { upload(expectedSize = "3", body = GatedStream(started, gate)) })
            assertTrue(started.await(30, TimeUnit.SECONDS), "两个上传必须先各占一个槽")
            assertVideoError(HttpStatus.TOO_MANY_REQUESTS, "VIDEO_UPLOAD_BUSY") {
                upload(expectedSize = "3", body = threeBytes())
            }
            gate.countDown()
            assertNotNull(first.get(30, TimeUnit.SECONDS))
            assertNotNull(second.get(30, TimeUnit.SECONDS))
            assertEquals(2, inserted.size)
            // 两个槽都已释放：第三个上传可以正常完成。
            val third = upload(expectedSize = "3", body = threeBytes())
            assertEquals(3, inserted.size)
            assertTrue(Files.exists(storageRoot.resolve(third.id)))
        } finally {
            gate.countDown()
            pool.shutdownNow()
        }
    }

    // ------------------------------------------------------------------
    // 列表与播放（I-1/I-7/I-10）
    // ------------------------------------------------------------------

    @Test
    fun `list pages by uploaded_at and id descending with 1 to 50 size bounds`() {
        rows += row(id = "id-a", display = "a.mp4", uploadedAt = LocalDateTime.of(2026, 10, 11, 9, 0))
        rows += row(id = "id-b", display = "b.mp4", uploadedAt = LocalDateTime.of(2026, 10, 11, 10, 0))
        rows += row(
            id = "other",
            expertKey = otherExpert,
            display = "c.mp4",
            uploadedAt = LocalDateTime.of(2026, 10, 11, 11, 0)
        )

        val firstPage = service.list(knownExpert, null, null, "")
        assertEquals(2L, firstPage.total)
        assertEquals(0, firstPage.page)
        assertEquals(MeetingVideoLimits.DEFAULT_PAGE_SIZE, firstPage.size)
        assertEquals(listOf("id-b", "id-a"), firstPage.items.map { it.id })
        assertEquals("b.mp4", firstPage.items.first().displayFilename)
        assertEquals(knownExpert, firstPage.items.first().expertId)

        val secondPage = service.list(knownExpert, 1, 1, "")
        assertEquals(listOf("id-a"), secondPage.items.map { it.id })
        assertEquals(1, secondPage.page)
        assertEquals(1, secondPage.size)

        // 无记录：只跑 COUNT，不跑分页查询，返回空数组。
        val queriesBefore = queryCalls().size
        val noRows = service.list("0000-0009", 0, 20, "")
        assertEquals(0L, noRows.total)
        assertTrue(noRows.items.isEmpty())
        assertEquals(queriesBefore, queryCalls().size)
    }

    @Test
    fun `list rejects invalid pagination and expert ids`() {
        listOf(0, 51, -1).forEach { size ->
            assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
                service.list(knownExpert, 0, size, "")
            }
        }
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") { service.list(knownExpert, -1, 20, "") }
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") { service.list("  ", 0, 20, "") }
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") { service.list("x".repeat(65), 0, 20, "") }
        assertEquals(50, service.list(knownExpert, 0, 50, "").size)
        // 规范化后才是同一个专家：大小写不同不应串人。
        rows += row(id = "id-b", expertKey = "0000-0002", display = "b.mp4")
        assertEquals(0L, service.list(knownExpert, 0, 20, "").total)
    }

    @Test
    fun `resolveFile validates ownership real path and byte length`() {
        Files.createDirectories(storageRoot)
        val id = "11111111-1111-1111-1111-111111111111"
        Files.write(storageRoot.resolve(id), ByteArray(4))
        rows += row(id = id, byteLength = 4L, display = "企业对接会.mp4")

        val file = service.resolveFile(knownExpert, id)
        assertEquals(storageRoot.resolve(id).toRealPath(), file.path)
        assertEquals("企业对接会.mp4", file.displayFilename)
        assertEquals("mp4", file.extension)
        assertEquals("video/mp4", file.contentType)
        assertEquals(4L, file.byteLength)

        assertVideoError(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND") { service.resolveFile(otherExpert, id) }
        assertVideoError(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND") { service.resolveFile(knownExpert, "unknown-id") }
        assertVideoError(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND") { service.resolveFile(knownExpert, " ") }
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") { service.resolveFile(null, id) }

        // 元数据存在但文件缺失 / 实际大小不符：都不返回磁盘路径。
        Files.delete(storageRoot.resolve(id))
        assertVideoError(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND") { service.resolveFile(knownExpert, id) }
        Files.write(storageRoot.resolve(id), ByteArray(5))
        val mismatch = assertThrows(MeetingVideoException::class.java) { service.resolveFile(knownExpert, id) }
        assertEquals("VIDEO_STORE_FAILED", mismatch.code)
        assertFalse(mismatch.message!!.contains(storageRoot.toString()))
        assertFalse(mismatch.message!!.contains("/"))
    }

    @Test
    fun `list and playback never consult the identity resolver so stored videos survive es outages`() {
        // I-1：列表/播放只用 expert_key + video_id 查元数据；身份解析器（会走 ES）零调用。
        val bareResolver = Mockito.mock(ExpertProfileIdentityResolver::class.java)
        val bareService = ExpertMeetingVideoService(
            MailAttachmentStorageProperties(basePath = baseDir.toString()),
            bareResolver,
            jdbc,
            TransactionTemplate(Mockito.mock(PlatformTransactionManager::class.java)),
            { usableSpace }
        )
        Files.createDirectories(storageRoot)
        val id = "55555555-5555-5555-5555-555555555555"
        Files.write(storageRoot.resolve(id), ByteArray(3))
        rows += row(id = id, display = "ES故障也能播.mp4")

        val page = bareService.list(knownExpert, 0, 20, "")
        assertEquals(1L, page.total)
        assertEquals("ES故障也能播.mp4", page.items.single().displayFilename)
        assertEquals(3L, bareService.resolveFile(knownExpert, id).byteLength)
        assertEquals("video/mp4", bareService.resolveFile(knownExpert, id).contentType)
        Mockito.verifyNoInteractions(bareResolver)
    }

    @Test
    fun `resolveFile rejects symlinks escaping the storage root`() {
        Files.createDirectories(storageRoot)
        val id = "22222222-2222-2222-2222-222222222222"
        val outside = baseDir.resolve("outside.bin")
        Files.write(outside, ByteArray(4))
        Files.createSymbolicLink(storageRoot.resolve(id), outside)
        rows += row(id = id, byteLength = 4L)

        val failure = assertThrows(MeetingVideoException::class.java) { service.resolveFile(knownExpert, id) }
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.status)
        assertEquals("VIDEO_STORE_FAILED", failure.code)
    }

    // ------------------------------------------------------------------
    // 重命名（I-4/I-5）
    // ------------------------------------------------------------------

    @Test
    fun `rename only rewrites display name with version CAS and keeps the stored file`() {
        Files.createDirectories(storageRoot)
        val id = "33333333-3333-3333-3333-333333333333"
        val stored = storageRoot.resolve(id)
        Files.write(stored, ByteArray(3))
        rows += row(id = id, display = "企业对接会.mp4", version = 1L, byteLength = 3L)
        updateResult = 1

        val renamed = service.rename(
            MeetingVideoRenameRequest("企业对接会-第二次", 1L), knownExpert, id, "admin", ""
        )

        assertEquals("企业对接会-第二次.mp4", renamed.displayFilename)
        assertEquals("会议.mp4", renamed.originalFilename)
        assertEquals(2L, renamed.version)
        assertEquals("admin", renamed.updatedBy)
        assertEquals("/api/experts/meeting-videos/$id/preview?expertId=0000-0001", renamed.previewUrl)
        assertTrue(Files.exists(stored), "重命名不得搬动磁盘文件")
        val update = lastUpdateParams()
        assertEquals("企业对接会-第二次.mp4", update.getValue("displayFilename"))
        assertEquals(1L, update.getValue("expectedVersion"))
        assertEquals(id, update.getValue("id"))
        assertEquals(knownExpert, update.getValue("expertKey"))
        assertTrue((update.getValue("updatedAt") as LocalDateTime).year >= 2026)
    }

    @Test
    fun `rename rejects stale versions conflicts and invalid names`() {
        val id = "44444444-4444-4444-4444-444444444444"
        rows += row(id = id, display = "企业对接会.mp4", version = 3L)
        assertVideoError(HttpStatus.CONFLICT, "VIDEO_VERSION_CONFLICT") {
            service.rename(MeetingVideoRenameRequest("新名字", 2L), knownExpert, id, "admin", "")
        }

        Mockito.doAnswer { throw DuplicateKeyException("uk_expert_meeting_video_name") }
            .`when`(jdbc).update(
                Mockito.contains("UPDATE expert_meeting_video"),
                Mockito.any(MapSqlParameterSource::class.java)
            )
        assertVideoError(HttpStatus.CONFLICT, "VIDEO_NAME_CONFLICT") {
            service.rename(MeetingVideoRenameRequest("别人的名字", 3L), knownExpert, id, "admin", "")
        }

        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
            service.rename(MeetingVideoRenameRequest("新名字", null), knownExpert, id, "admin", "")
        }
        listOf(null, "", " ", "新/名字", "新名.", "\u0000新名").forEach { stem ->
            assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
                service.rename(MeetingVideoRenameRequest(stem, 3L), knownExpert, id, "admin", "")
            }
        }
        assertVideoError(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND") {
            service.rename(MeetingVideoRenameRequest("新名字", 3L), otherExpert, id, "admin", "")
        }
        assertVideoError(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND") {
            service.rename(MeetingVideoRenameRequest("新名字", 3L), knownExpert, "missing", "admin", "")
        }
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
            service.rename(MeetingVideoRenameRequest("新名字", 3L), knownExpert, id, " ", "")
        }
    }

    // ------------------------------------------------------------------
    // 身份与审计（I-1/I-3）
    // ------------------------------------------------------------------

    @Test
    fun `identity failures map to 400 404 and 503 and never fall through to 500`() {
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
            upload(expertId = null, expectedSize = "1", body = oneByte())
        }
        assertVideoError(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND") {
            upload(expertId = "missing-expert", expectedSize = "1", body = oneByte())
        }
        assertVideoError(HttpStatus.SERVICE_UNAVAILABLE, "EXPERT_LOOKUP_UNAVAILABLE") {
            upload(expertId = "es-down", expectedSize = "1", body = oneByte())
        }
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `actor must come from the session and stay inside the audit column width`() {
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
            upload(username = null, expectedSize = "1", body = oneByte())
        }
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
            upload(username = "  ", expectedSize = "1", body = oneByte())
        }
        assertVideoError(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_NAME") {
            upload(username = "u".repeat(65), expectedSize = "1", body = oneByte())
        }
        assertEquals("admin", upload(username = " admin ", expectedSize = "1", body = oneByte()).uploadedBy)
        assertTrue(inserted.all { (it.getValue("uploadedBy") as String).length <= 64 })
    }

    // ------------------------------------------------------------------
    // 替身与断言工具
    // ------------------------------------------------------------------

    private fun upload(
        expertId: String? = knownExpert,
        originalName: String? = "会议.mp4",
        nameStem: String? = null,
        expectedSize: String? = "3",
        declaredLength: Long? = null,
        level: ExpertIndexLevel = ExpertIndexLevel.RAW,
        username: String? = "admin",
        contextPath: String = "",
        target: ExpertMeetingVideoService = service,
        body: InputStream = threeBytes()
    ) = target.upload(
        MeetingVideoUploadRequest(expertId, level, originalName, nameStem, expectedSize, declaredLength),
        username,
        contextPath,
        body
    )

    private fun oneByte() = ByteArrayInputStream(byteArrayOf(1))
    private fun threeBytes() = ByteArrayInputStream(byteArrayOf(1, 2, 3))
    private fun fiveBytes() = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))

    private fun assertVideoError(status: HttpStatus, code: String, block: () -> Unit) {
        val failure = assertThrows(MeetingVideoException::class.java) { block() }
        assertEquals(status, failure.status, failure.message)
        assertEquals(code, failure.code, failure.message)
    }

    /** 任何分支都不许留下 `.tmp-*`；成功项才允许有 final 文件。 */
    private fun assertNoTempResidue() {
        if (!Files.exists(storageRoot)) return
        val leftovers = storageFiles()
        assertTrue(leftovers.none { it.startsWith(".tmp-") }, "临时文件必须被清理：$leftovers")
    }

    /** 失败分支：存储目录里不许有任何文件（两者都没有才是「零可见记录」）。 */
    private fun assertNoVisibleFiles() {
        if (!Files.exists(storageRoot)) return
        assertEquals(emptyList<String>(), storageFiles())
    }

    private fun storageFiles(): List<String> =
        Files.list(storageRoot).use { stream -> stream.map { it.fileName.toString() }.toList() }

    private fun queryCalls() = Mockito.mockingDetails(jdbc).invocations.filter { it.method.name == "query" }

    private fun lastUpdateParams(): MapSqlParameterSource =
        Mockito.mockingDetails(jdbc).invocations
            .last { it.method.name == "update" }
            .arguments[1] as MapSqlParameterSource

    /** 内存行台账 + SQL 回放；真实 RowMapper 在受控 ResultSet 上运行。 */
    private fun stubJdbc() {
        Mockito.doAnswer { invocation ->
            val sql = invocation.arguments[0] as String
            val params = invocation.arguments[1] as MapSqlParameterSource
            when {
                sql.contains("INSERT INTO expert_meeting_video") -> {
                    inserted += params
                    1
                }
                sql.contains("UPDATE expert_meeting_video") -> {
                    if (updateResult > 0) {
                        val target = rows.firstOrNull { it["id"] == params.getValue("id") }
                        target?.set("display_filename", params.getValue("displayFilename"))
                        target?.set("updated_by", params.getValue("updatedBy"))
                        target?.set("updated_at", params.getValue("updatedAt"))
                        target?.set("version", (target["version"] as Long) + 1)
                    }
                    updateResult
                }
                else -> 0
            }
        }.`when`(jdbc).update(Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java))

        Mockito.doAnswer { invocation ->
            val params = invocation.arguments[1] as MapSqlParameterSource
            rows.count { it["expert_key"] == params.getValue("expertKey") }.toLong()
        }.`when`(jdbc).queryForObject(
            Mockito.anyString(),
            Mockito.any(MapSqlParameterSource::class.java),
            Mockito.eq(Long::class.java)
        )

        Mockito.doAnswer { invocation ->
            val sql = invocation.arguments[0] as String
            val params = invocation.arguments[1] as MapSqlParameterSource
            val mapper = invocation.arguments[2] as RowMapper<Any>
            val matching = rows.filter { it["expert_key"] == params.getValue("expertKey") }
            val selected = when {
                sql.contains("display_filename = :displayFilename") ->
                    matching.filter { it["display_filename"] == params.getValue("displayFilename") }
                sql.contains("ORDER BY") -> {
                    val limit = params.getValue("limit") as Int
                    val offset = (params.getValue("offset") as Long).toInt()
                    matching.sortedWith(
                        compareByDescending<MutableMap<String, Any?>> { it["uploaded_at"] as LocalDateTime }
                            .thenByDescending { it["id"] as String }
                    ).drop(offset).take(limit)
                }
                sql.contains("WHERE id = :id") -> matching.filter { it["id"] == params.getValue("id") }
                else -> emptyList()
            }
            selected.map { mapper.mapRow(resultSet(it), 0) }
        }.`when`(jdbc).query(
            Mockito.anyString(),
            Mockito.any(MapSqlParameterSource::class.java),
            Mockito.any<RowMapper<Any>>()
        )
    }

    private fun resultSet(row: Map<String, Any?>): ResultSet {
        val rs = Mockito.mock(ResultSet::class.java)
        Mockito.`when`(rs.getString("id")).thenReturn(row["id"] as String?)
        Mockito.`when`(rs.getString("expert_key")).thenReturn(row["expert_key"] as String?)
        Mockito.`when`(rs.getString("original_filename")).thenReturn(row["original_filename"] as String?)
        Mockito.`when`(rs.getString("display_filename")).thenReturn(row["display_filename"] as String?)
        Mockito.`when`(rs.getLong("byte_length")).thenReturn(row["byte_length"] as Long)
        Mockito.`when`(rs.getString("uploaded_by")).thenReturn(row["uploaded_by"] as String?)
        Mockito.`when`(rs.getTimestamp("uploaded_at")).thenReturn(Timestamp.valueOf(row["uploaded_at"] as LocalDateTime))
        Mockito.`when`(rs.getString("updated_by")).thenReturn(row["updated_by"] as String?)
        Mockito.`when`(rs.getTimestamp("updated_at")).thenReturn(Timestamp.valueOf(row["updated_at"] as LocalDateTime))
        Mockito.`when`(rs.getLong("version")).thenReturn(row["version"] as Long)
        return rs
    }

    private fun row(
        id: String,
        expertKey: String = knownExpert,
        original: String = "会议.mp4",
        display: String = "会议.mp4",
        byteLength: Long = 3L,
        uploadedAt: LocalDateTime = LocalDateTime.of(2026, 10, 11, 10, 0),
        version: Long = 1L
    ): MutableMap<String, Any?> = mutableMapOf(
        "id" to id,
        "expert_key" to expertKey,
        "original_filename" to original,
        "display_filename" to display,
        "byte_length" to byteLength,
        "uploaded_by" to "admin",
        "uploaded_at" to uploadedAt,
        "updated_by" to "admin",
        "updated_at" to uploadedAt,
        "version" to version
    )

    /** 生成式字节流：不构造大数组；记录单次读取请求的缓冲大小与实际产出字节。 */
    private class GeneratedStream(private val total: Long) : InputStream() {
        var produced = 0L
        var maxRequestedBuffer = 0
        private var position = 0L

        override fun read(): Int {
            if (position >= total) return -1
            position++
            produced++
            return (position % 251).toInt()
        }

        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            maxRequestedBuffer = maxOf(maxRequestedBuffer, length)
            if (position >= total) return -1
            val count = minOf(length.toLong(), total - position).toInt()
            for (index in 0 until count) {
                target[offset + index] = ((position + index) % 251).toByte()
            }
            position += count
            produced += count
            return count
        }
    }

    /** 阻塞式字节流：两个上传各自占住一个并发槽（started 倒计数）后再放行（gate）。 */
    private class GatedStream(private val started: CountDownLatch, private val gate: CountDownLatch) : InputStream() {
        private var sent = false

        override fun read(): Int = read(ByteArray(1), 0, 1).let { if (it < 0) -1 else 1 }

        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            if (sent) return -1
            started.countDown()
            gate.await(30, TimeUnit.SECONDS)
            sent = true
            val count = minOf(length, 3)
            for (index in 0 until count) target[offset + index] = 7
            return count
        }
    }

    /** 读即失败：证明「已知同名」在读请求体之前就拒绝（并统计是否被读过）。 */
    private class ReadForbiddenStream : InputStream() {
        var reads = 0
            private set

        override fun read(): Int {
            reads += 1
            throw AssertionError("已知同名不得读取请求体")
        }

        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            reads += 1
            throw AssertionError("已知同名不得读取请求体")
        }
    }

    /** 与真实磁盘写满时 JDK 抛出的 `FileSystemException(reason="No space left on device")` 同形。 */
    private class FailingStream(private val reason: String) : InputStream() {
        override fun read(): Int = throw java.nio.file.FileSystemException("/tmp/expert-meeting-video", null, reason)

        override fun read(target: ByteArray, offset: Int, length: Int): Int = read()
    }
}
