package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.config.AuthInterceptor
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.controller.OutboundAttachmentController
import com.weibo.talentintroduction.mail.domain.OutboundMailAttachment
import com.weibo.talentintroduction.mail.repository.OutboundMailAttachmentRepository
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import java.util.stream.Collectors

/**
 * 04 验收（I-1～I-5）：真实临时存储目录 + 真实 controller/advice/拦截器 + 内存元数据台账。
 *
 * 覆盖：任意格式/0 字节/中文名/自定义扩展名、10MiB 与 10MiB+1 边界、临时文件清理、
 * 身份与归属（跨用户/跨专家 404、身份超列宽拒绝）、原件缺失/损坏/越界路径、选择顺序与
 * 总量边界（重复 id/11 个/20MiB）、快照 codec（NULL/损坏/schema/sha/未知字段）、下载安全头。
 *
 * 容器 multipart 大小限制在 MockMvc 中不生效（没有真实容器解析），因此该路径由真实 HTTP
 * 启动验证记录在 execution 报告中；这里只验证 `MaxUploadSizeExceededException -> 413`
 * 的全局映射本身，以及 `GlobalExceptionHandler` 对 400/404/409/413 的固定映射。
 */
class OutboundAttachmentServiceTest {

    @TempDir
    lateinit var storageRoot: Path

    private val repository: OutboundMailAttachmentRepository =
        Mockito.mock(OutboundMailAttachmentRepository::class.java)
    private val expertContactRepository: ExpertContactRepository =
        Mockito.mock(ExpertContactRepository::class.java)
    private val authService: AuthService = Mockito.mock(AuthService::class.java)

    private lateinit var service: OutboundAttachmentService
    private lateinit var mockMvc: MockMvc

    /** 内存台账：让 mock 仓库具备真实读写语义（读回顺序由服务恢复）。 */
    private val rows = linkedMapOf<String, OutboundMailAttachment>()
    private var insertFailure: RuntimeException? = null

    @BeforeEach
    fun setUp() {
        service = OutboundAttachmentService(
            MailAttachmentStorageProperties(basePath = storageRoot.toString()),
            repository,
            expertContactRepository
        )
        Mockito.`when`(expertContactRepository.existsById(CONTACT_ID)).thenReturn(true)
        Mockito.doAnswer { invocation ->
            insertFailure?.let { throw it }
            val attachment = invocation.getArgument<OutboundMailAttachment>(0)
            rows[attachment.id] = attachment
            null
        }.`when`(repository).insert(anyValue(sampleRow()))
        Mockito.`when`(
            repository.findAllByExpertContactIdAndIdIn(Mockito.anyLong(), Mockito.anyList())
        ).thenAnswer { invocation ->
            val contactId = invocation.getArgument<Long>(0)
            val ids = invocation.getArgument<List<String>>(1)
            ids.mapNotNull { rows[it] }.filter { it.expertContactId == contactId }
        }
        Mockito.`when`(authService.findUser(OWNER)).thenReturn(adminUser(OWNER))
        Mockito.`when`(authService.findUser(OTHER_USER)).thenReturn(adminUser(OTHER_USER))
        mockMvc = MockMvcBuilders
            .standaloneSetup(OutboundAttachmentController(service))
            .addInterceptors(AuthInterceptor(authService, ObjectMapper()))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    // ------------------------------------------------------------------
    // 上传：原字节、元数据、任意格式
    // ------------------------------------------------------------------

    @Test
    fun `upload stores original bytes with sha and metadata and writes only the outbound dir`() {
        val bytes = "会议资料".toByteArray(StandardCharsets.UTF_8) + ByteArray(64) { it.toByte() }
        val response = upload(bytes, filename = "会议资料.zip", contentType = "application/zip")

        assertTrue(isUuid(response.id))
        assertEquals("会议资料.zip", response.filename)
        assertEquals("application/zip", response.contentType)
        assertEquals(bytes.size.toLong(), response.byteLength)
        assertEquals(sha256Hex(bytes), response.sha256)
        assertEquals(
            "/api/mail/conversations/$CONTACT_ID/outbound-attachments/${response.id}/download",
            response.downloadUrl
        )
        // 磁盘上只有 outbound/<uuid> 一个文件（无临时残留），字节与原件一致。
        assertEquals(listOf(response.id), storedFileNames())
        assertArrayEquals(bytes, Files.readAllBytes(outboundDir().resolve(response.id)))
        // 元数据落库且上传者来自会话参数。
        val row = rows.getValue(response.id)
        assertEquals(OWNER, row.createdBy)
        assertEquals(CONTACT_ID, row.expertContactId)
        assertEquals(bytes.size.toLong(), row.byteLength)
        // 草稿下载逐字节一致。
        val downloaded = service.resolveDraftDownload(CONTACT_ID, response.id, OWNER)
        assertArrayEquals(bytes, downloaded.bytes)
        assertEquals(response.id, downloaded.snapshot.id)
    }

    @Test
    fun `upload accepts a zero byte file and keeps it downloadable`() {
        val response = upload(ByteArray(0), filename = "empty.txt", contentType = "text/plain")

        assertEquals(0L, response.byteLength)
        assertEquals(sha256Hex(ByteArray(0)), response.sha256)
        assertEquals(0L, Files.size(outboundDir().resolve(response.id)))
        assertArrayEquals(
            ByteArray(0),
            service.resolveDraftDownload(CONTACT_ID, response.id, OWNER).bytes
        )
    }

    @Test
    fun `upload applies no extension or mime allowlist`() {
        val cases = listOf(
            "a.txt" to "text/plain",
            "b.pdf" to "application/pdf",
            "c.zip" to "application/zip",
            "d.png" to "image/png",
            "no-extension" to "application/octet-stream",
            "e.sketchbook" to "application/x-custom+json"
        )
        cases.forEach { (name, type) ->
            val response = upload("payload-$name".toByteArray(), filename = name, contentType = type)
            assertEquals(name, response.filename)
            assertEquals(type, response.contentType)
            assertArrayEquals(
                "payload-$name".toByteArray(),
                service.resolveDraftDownload(CONTACT_ID, response.id, OWNER).bytes
            )
        }
        assertEquals(cases.size, storedFileNames().size)
    }

    @Test
    fun `upload sanitizes the file name into a display name without path or control characters`() {
        val cases = listOf(
            "C:\\Users\\x\\会议 资料.zip" to "会议 资料.zip",
            "/tmp/a\u0000b\u0007c.txt" to "abc.txt",
            "   " to "attachment",
            "///" to "attachment",
            "\u0000" to "attachment"
        )
        cases.forEach { (raw, expected) ->
            val response = upload(byteArrayOf(1, 2, 3), filename = raw, contentType = "text/plain")
            assertEquals(expected, response.filename)
            assertFalse(response.filename.contains('/'))
            assertFalse(response.filename.contains('\\'))
            assertTrue(response.filename.none { it.isISOControl() })
            // 无论如何清洗，文件永远只落在 outbound/<uuid>。
            assertEquals(1, storedFileNames().count { it == response.id })
        }
        val capped = upload(byteArrayOf(1), filename = "x".repeat(300) + ".pdf", contentType = "application/pdf")
        assertEquals(255, capped.filename.length)
        assertTrue(capped.filename.endsWith(".pdf"))
    }

    @Test
    fun `upload falls back to octet stream for missing or malformed content types`() {
        val cases = listOf(
            "text/plain\r\nX-Injected: 1" to DEFAULT_CONTENT_TYPE,
            "not-a-mime" to DEFAULT_CONTENT_TYPE,
            "text/plain; charset=utf-8" to DEFAULT_CONTENT_TYPE,
            null to DEFAULT_CONTENT_TYPE,
            "text/html" to "text/html",
            "application/octet-stream" to "application/octet-stream"
        )
        cases.forEach { (declared, expected) ->
            assertEquals(expected, upload(byteArrayOf(9), filename = "x.bin", contentType = declared).contentType)
        }
    }

    // ------------------------------------------------------------------
    // 容量边界与失败清理
    // ------------------------------------------------------------------

    @Test
    fun `upload accepts exactly ten mebibytes and rejects one byte more with 413`() {
        val exact = upload(EndlessStream(MAX_FILE_BYTES), filename = "exact.bin")
        assertEquals(MAX_FILE_BYTES, exact.byteLength)
        assertEquals(MAX_FILE_BYTES, Files.size(outboundDir().resolve(exact.id)))

        val failure = assertThrows(OutboundAttachmentException::class.java) {
            // 无界流：一旦没有在超限处立即中断，本用例会一直读下去（挂起/OOM 而非通过）。
            service.upload(CONTACT_ID, OWNER, "over.bin", "application/octet-stream", EndlessStream())
        }
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, failure.status)
        assertEquals("PAYLOAD_TOO_LARGE", failure.code)
        // 本次临时文件已清理，且没有落任何元数据。
        assertEquals(listOf(exact.id), storedFileNames())
        assertEquals(1, rows.size)
    }

    @Test
    fun `upload leaves nothing behind when the client stream aborts`() {
        val stream = object : InputStream() {
            private var produced = 0
            override fun read(): Int = throw IOException("client aborted")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (produced >= 100) {
                    throw IOException("client aborted")
                }
                produced += 1
                b[off] = 7
                return 1
            }
        }
        assertThrows(IOException::class.java) {
            service.upload(CONTACT_ID, OWNER, "abort.bin", "application/octet-stream", stream)
        }
        assertTrue(storedFileNames().isEmpty())
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `upload deletes the stored file when the metadata insert fails`() {
        insertFailure = RuntimeException("db down")
        assertThrows(RuntimeException::class.java) { upload(byteArrayOf(1, 2, 3), filename = "db.bin") }
        assertTrue(storedFileNames().isEmpty())
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `upload rejects unknown expert and over wide operator identity without writing`() {
        val unknownContact = assertThrows(OutboundAttachmentException::class.java) {
            service.upload(999L, OWNER, "a.bin", "application/octet-stream", ByteArrayInputStream(byteArrayOf(1)))
        }
        assertEquals(HttpStatus.NOT_FOUND, unknownContact.status)

        val tooWide = assertThrows(IllegalStateException::class.java) {
            service.upload(
                CONTACT_ID,
                "u".repeat(101),
                "a.bin",
                "application/octet-stream",
                ByteArrayInputStream(byteArrayOf(1))
            )
        }
        assertTrue(tooWide.message!!.contains("created_by"))
        assertTrue(storedFileNames().isEmpty())
        assertTrue(rows.isEmpty())
    }

    // ------------------------------------------------------------------
    // 归属与原件读取
    // ------------------------------------------------------------------

    @Test
    fun `draft download rejects another user another contact and an unknown id with 404`() {
        val response = upload(byteArrayOf(1, 2, 3), filename = "draft.bin")

        val otherUser = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveDraftDownload(CONTACT_ID, response.id, OTHER_USER)
        }
        assertEquals(HttpStatus.NOT_FOUND, otherUser.status)

        val otherContact = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveDraftDownload(CONTACT_ID + 1, response.id, OWNER)
        }
        assertEquals(HttpStatus.NOT_FOUND, otherContact.status)

        val unknownId = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveDraftDownload(CONTACT_ID, "not-a-uuid", OWNER)
        }
        assertEquals(HttpStatus.NOT_FOUND, unknownId.status)
    }

    @Test
    fun `draft download fails closed for missing and tampered originals`() {
        val missing = upload(byteArrayOf(1, 2, 3), filename = "gone.bin")
        Files.delete(outboundDir().resolve(missing.id))
        val missingFailure = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveDraftDownload(CONTACT_ID, missing.id, OWNER)
        }
        assertEquals(HttpStatus.NOT_FOUND, missingFailure.status)

        val sameLength = upload(byteArrayOf(1, 2, 3), filename = "same.bin")
        Files.write(outboundDir().resolve(sameLength.id), byteArrayOf(4, 5, 6))
        val hashFailure = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveDraftDownload(CONTACT_ID, sameLength.id, OWNER)
        }
        assertEquals(HttpStatus.CONFLICT, hashFailure.status)

        val otherLength = upload(byteArrayOf(1, 2, 3), filename = "len.bin")
        Files.write(outboundDir().resolve(otherLength.id), byteArrayOf(1, 2, 3, 4))
        val sizeFailure = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveDraftDownload(CONTACT_ID, otherLength.id, OWNER)
        }
        assertEquals(HttpStatus.CONFLICT, sizeFailure.status)
    }

    @Test
    fun `draft download rejects symlinks escaping the outbound root`() {
        val response = upload(byteArrayOf(1, 2, 3), filename = "link.bin")
        val outside = storageRoot.parent.resolve("outside-${response.id}.bin")
        Files.write(outside, byteArrayOf(1, 2, 3))
        val link = outboundDir().resolve(response.id)
        Files.delete(link)
        Files.createSymbolicLink(link, outside)

        val failure = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveDraftDownload(CONTACT_ID, response.id, OWNER)
        }
        assertEquals(HttpStatus.CONFLICT, failure.status)
        Files.deleteIfExists(outside)
    }

    // ------------------------------------------------------------------
    // 发送前解析与元数据读取
    // ------------------------------------------------------------------

    @Test
    fun `resolveForSend keeps the user selection order and returns matching snapshots`() {
        val first = upload("first".toByteArray(), filename = "a.txt", contentType = "text/plain")
        val second = upload("second".toByteArray(), filename = "b.txt", contentType = "text/plain")
        val third = upload("third".toByteArray(), filename = "c.txt", contentType = "text/plain")
        val payloads = mapOf(
            first.id to "first".toByteArray(),
            second.id to "second".toByteArray(),
            third.id to "third".toByteArray()
        )
        val requestOrder = listOf(third.id, first.id, second.id)

        val result = service.resolveForSend(CONTACT_ID, requestOrder, OWNER)

        assertEquals(requestOrder, result.snapshots.map { it.id })
        assertEquals(requestOrder, result.files.map { it.snapshot.id })
        assertEquals(listOf("c.txt", "a.txt", "b.txt"), result.snapshots.map { it.filename })
        result.files.forEachIndexed { index, file ->
            assertArrayEquals(payloads.getValue(requestOrder[index]), file.bytes)
        }
        result.snapshots.forEach { snapshot ->
            val payload = payloads.getValue(snapshot.id)
            assertEquals(OUTBOUND_ATTACHMENT_SCHEMA_VERSION, snapshot.schemaVersion)
            assertEquals(sha256Hex(payload), snapshot.sha256)
            assertEquals(payload.size.toLong(), snapshot.byteLength)
        }
        assertTrue(service.resolveForSend(CONTACT_ID, emptyList(), OWNER).snapshots.isEmpty())
    }

    @Test
    fun `resolveForSend rejects duplicate and over count ids with 400`() {
        val first = upload(byteArrayOf(1), filename = "a.bin")
        val second = upload(byteArrayOf(2), filename = "b.bin")

        val duplicate = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveForSend(CONTACT_ID, listOf(first.id, first.id), OWNER)
        }
        assertEquals(HttpStatus.BAD_REQUEST, duplicate.status)

        val tooMany = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveForSend(CONTACT_ID, List(MAX_FILES + 1) { "${second.id}-$it" }, OWNER)
        }
        assertEquals(HttpStatus.BAD_REQUEST, tooMany.status)
        assertTrue(tooMany.message!!.contains(MAX_FILES.toString()))
    }

    @Test
    fun `resolveForSend accepts exactly twenty mebibytes and rejects more with 413`() {
        val tenMib = upload(EndlessStream(MAX_FILE_BYTES), filename = "ten.bin")
        val otherTenMib = upload(EndlessStream(MAX_FILE_BYTES), filename = "ten2.bin")

        val exact = service.resolveForSend(CONTACT_ID, listOf(tenMib.id, otherTenMib.id), OWNER)
        assertEquals(MAX_TOTAL_BYTES, exact.snapshots.sumOf { it.byteLength })

        val oneByte = upload(byteArrayOf(7), filename = "tiny.bin")
        val failure = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveForSend(CONTACT_ID, listOf(tenMib.id, otherTenMib.id, oneByte.id), OWNER)
        }
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, failure.status)
    }

    @Test
    fun `resolveForSend rejects another uploader with 404`() {
        val response = upload(byteArrayOf(1), filename = "mine.bin")
        val failure = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveForSend(CONTACT_ID, listOf(response.id), OTHER_USER)
        }
        assertEquals(HttpStatus.NOT_FOUND, failure.status)
    }

    @Test
    fun `loadSnapshots reads metadata only while send resolution stays fail closed`() {
        val first = upload("one".toByteArray(), filename = "one.txt", contentType = "text/plain")
        val second = upload("two".toByteArray(), filename = "two.txt", contentType = "text/plain")
        Files.delete(outboundDir().resolve(first.id))
        Files.delete(outboundDir().resolve(second.id))

        val snapshots = service.loadSnapshots(CONTACT_ID, listOf(second.id, first.id), OWNER)
        assertEquals(listOf(second.id, first.id), snapshots.map { it.id })
        assertEquals(listOf("two.txt", "one.txt"), snapshots.map { it.filename })

        val failure = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveForSend(CONTACT_ID, listOf(first.id), OWNER)
        }
        assertEquals(HttpStatus.NOT_FOUND, failure.status)
    }

    @Test
    fun `message download is contact scoped and does not require the original uploader`() {
        val response = upload(byteArrayOf(4, 5, 6), filename = "sent.bin")

        assertArrayEquals(
            byteArrayOf(4, 5, 6),
            service.resolveForMessageDownload(CONTACT_ID, response.id).bytes
        )
        val otherContact = assertThrows(OutboundAttachmentException::class.java) {
            service.resolveForMessageDownload(CONTACT_ID + 1, response.id)
        }
        assertEquals(HttpStatus.NOT_FOUND, otherContact.status)
    }

    // ------------------------------------------------------------------
    // 快照 codec
    // ------------------------------------------------------------------

    @Test
    fun `snapshot codec round trips ordered fields and treats null as absent`() {
        val snapshots = listOf(
            snapshot("a.txt", "text/plain", "aaa"),
            snapshot("b.zip", "application/zip", "bb")
        )
        val json = OutboundAttachmentSnapshotCodec.serialize(snapshots)

        assertEquals(snapshots, OutboundAttachmentSnapshotCodec.parseOrThrow(json))
        assertEquals(snapshots, OutboundAttachmentSnapshotCodec.parseOrNull(json))
        assertNull(OutboundAttachmentSnapshotCodec.parseOrThrow(null))
        assertNull(OutboundAttachmentSnapshotCodec.parseOrNull(null))
        // 快照不含绝对路径、字节或上传用户名。
        assertFalse(json.contains(storageRoot.toString()))
        assertFalse(json.contains(OWNER))
        assertFalse(json.contains("\"bytes\""))
        assertTrue(json.startsWith("["))
    }

    @Test
    fun `snapshot codec rejects empty corrupt oversize and unknown shapes`() {
        val valid = OutboundAttachmentSnapshotCodec.serialize(listOf(snapshot("a.txt", "text/plain", "aaa")))
        val element = valid.removeSurrounding("[", "]")
        val corrupt = listOf(
            "not-json",
            "{}",
            "[]",
            // V-1：非空的空白存档不是「没有附件」（唯一形态是 SQL NULL），严格解析一律按损坏 409。
            "",
            "   ",
            valid + "null",
            valid.dropLast(1),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":9"),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"extra\":1"),
            valid.replace("\"filename\":\"a.txt\"", "\"filename\":\"a/b\""),
            valid.replace("\"filename\":\"a.txt\"", "\"filename\":\"\""),
            valid.replace("\"filename\":\"a.txt\"", "\"filename\":\"${"x".repeat(300)}\""),
            valid.replace("\"id\":\"", "\"id\":\"not-a-uuid-"),
            valid.replace("\"sha256\":\"", "\"sha256\":\"00"),
            valid.replace("\"contentType\":\"text/plain\"", "\"contentType\":\"text/plain; charset=utf-8\""),
            "[" + List(MAX_FILES + 1) { element }.joinToString(",") + "]",
            "[$element,$element]",
            "[" + element.replace("\"byteLength\":3", "\"byteLength\":${MAX_FILE_BYTES + 1}") + "]"
        )
        corrupt.forEach { json ->
            val failure = assertThrows(
                OutboundAttachmentException::class.java,
                { OutboundAttachmentSnapshotCodec.parseOrThrow(json) },
                json
            )
            assertEquals(HttpStatus.CONFLICT, failure.status, json)
            assertNull(OutboundAttachmentSnapshotCodec.parseOrNull(json), json)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OutboundAttachmentSnapshotCodec.serialize(emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            // 总量超过 20MiB 的存档同样不合法。
            OutboundAttachmentSnapshotCodec.serialize(
                listOf(
                    snapshot("a.bin", "application/octet-stream", "aaa", byteLength = MAX_FILE_BYTES),
                    snapshot("b.bin", "application/octet-stream", "bbb", byteLength = MAX_FILE_BYTES),
                    snapshot("c.bin", "application/octet-stream", "ccc", byteLength = 1)
                )
            )
        }
    }

    @Test
    fun `snapshot validation rejects metadata whose stored form is not canonical`() {
        val broken = OutboundAttachmentSnapshot(
            schemaVersion = OUTBOUND_ATTACHMENT_SCHEMA_VERSION,
            id = UUID.randomUUID().toString(),
            filename = "a.txt",
            contentType = "text/plain",
            byteLength = 3,
            sha256 = "NOT-A-SHA"
        )
        assertEquals(
            HttpStatus.CONFLICT,
            assertThrows(OutboundAttachmentException::class.java) {
                OutboundAttachmentSnapshotCodec.validateSnapshot(broken)
            }.status
        )
    }

    // ------------------------------------------------------------------
    // HTTP 边界
    // ------------------------------------------------------------------

    @Test
    fun `http upload returns 201 with download url and metadata`() {
        mockMvc.perform(
            multipart("/api/mail/conversations/$CONTACT_ID/outbound-attachments")
                .file(MockMultipartFile("file", "会议.zip", "application/zip", byteArrayOf(1, 2, 3, 4)))
                .session(sessionOf(OWNER))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.filename").value("会议.zip"))
            .andExpect(jsonPath("$.contentType").value("application/zip"))
            .andExpect(jsonPath("$.byteLength").value(4))
            .andExpect(jsonPath("$.sha256").value(sha256Hex(byteArrayOf(1, 2, 3, 4))))
            .andExpect(jsonPath("$.downloadUrl").value(
                Matchers.containsString("/api/mail/conversations/$CONTACT_ID/outbound-attachments/")
            ))
    }

    @Test
    fun `http upload maps a missing file part to 400 instead of 500`() {
        mockMvc.perform(
            multipart("/api/mail/conversations/$CONTACT_ID/outbound-attachments")
                .file(MockMultipartFile("other", "x.bin", "application/octet-stream", byteArrayOf(1)))
                .session(sessionOf(OWNER))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `http upload and download require a session`() {
        mockMvc.perform(
            multipart("/api/mail/conversations/$CONTACT_ID/outbound-attachments")
                .file(MockMultipartFile("file", "x.bin", "application/octet-stream", byteArrayOf(1)))
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(get("/api/mail/conversations/$CONTACT_ID/outbound-attachments/some-id/download"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `http download forces attachment disposition with utf8 name and verified bytes`() {
        val bytes = "会议正文".toByteArray(StandardCharsets.UTF_8)
        val response = upload(bytes, filename = "会议资料.zip", contentType = "application/zip")

        val result = mockMvc.perform(
            get("/api/mail/conversations/$CONTACT_ID/outbound-attachments/${response.id}/download")
                .session(sessionOf(OWNER))
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment")))
            .andExpect(header().string("Content-Disposition", Matchers.containsString("UTF-8''")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", "private,no-store"))
            .andExpect(header().string("Content-Type", "application/zip"))
            .andReturn()

        assertArrayEquals(bytes, result.response.contentAsByteArray)
    }

    @Test
    fun `http download returns 404 for another user`() {
        val response = upload(byteArrayOf(1), filename = "mine.bin")
        mockMvc.perform(
            get("/api/mail/conversations/$CONTACT_ID/outbound-attachments/${response.id}/download")
                .session(sessionOf(OTHER_USER))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `http oversized upload returns 413 without leaking server paths`() {
        val oversized = ByteArray((MAX_FILE_BYTES + 1).toInt())
        val result = mockMvc.perform(
            multipart("/api/mail/conversations/$CONTACT_ID/outbound-attachments")
                .file(MockMultipartFile("file", "big.bin", "application/octet-stream", oversized))
                .session(sessionOf(OWNER))
        )
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
            .andReturn()

        val body = result.response.contentAsString
        assertFalse(body.contains(storageRoot.toString()))
        assertFalse(body.contains("outbound"))
        assertTrue(storedFileNames().isEmpty())
    }

    @Test
    fun `multipart parsing failure is mapped to a fixed 413 without internal details`() {
        val probe = MockMvcBuilders
            .standaloneSetup(MultipartLimitProbeController())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
        probe.perform(post("/probe/multipart-limit"))
            .andExpect(status().isPayloadTooLarge)
            .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
            .andExpect(jsonPath("$.message").value("上传文件超出大小上限"))
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private fun upload(
        bytes: ByteArray,
        filename: String? = "a.bin",
        contentType: String? = "application/octet-stream",
        contactId: Long = CONTACT_ID,
        user: String = OWNER
    ): OutboundAttachmentUploadResponse =
        service.upload(contactId, user, filename, contentType, ByteArrayInputStream(bytes))

    private fun upload(endless: EndlessStream, filename: String): OutboundAttachmentUploadResponse =
        service.upload(CONTACT_ID, OWNER, filename, "application/octet-stream", endless)

    private fun snapshot(
        filename: String,
        contentType: String,
        payload: String,
        byteLength: Long = payload.toByteArray().size.toLong()
    ): OutboundAttachmentSnapshot = OutboundAttachmentSnapshot(
        schemaVersion = OUTBOUND_ATTACHMENT_SCHEMA_VERSION,
        id = UUID.randomUUID().toString(),
        filename = filename,
        contentType = contentType,
        byteLength = byteLength,
        sha256 = sha256Hex(payload.toByteArray())
    )

    private fun outboundDir(): Path = storageRoot.resolve("outbound")

    private fun storedFileNames(): List<String> =
        if (Files.exists(outboundDir())) {
            Files.list(outboundDir()).use { stream ->
                stream.map { it.fileName.toString() }.collect(Collectors.toList())
            }
        } else {
            emptyList()
        }

    private fun sessionOf(username: String): MockHttpSession =
        MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }

    private fun adminUser(username: String): AdminUser = AdminUser(
        username = username,
        passwordHash = "not-checked",
        mustChangePassword = false,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private fun sampleRow(): OutboundMailAttachment = OutboundMailAttachment(
        id = UUID.randomUUID().toString(),
        expertContactId = CONTACT_ID,
        createdBy = OWNER,
        fileName = "sample.bin",
        contentType = "application/octet-stream",
        byteLength = 1,
        sha256 = sha256Hex(byteArrayOf(1)),
        createdAt = LocalDateTime.now()
    )

    /** Mockito.any() 对 Kotlin 非空参数返回 null；用真实默认值占位（既有测试同款手法）。 */
    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

    private fun sha256Hex(bytes: ByteArray): String = outboundSha256Hex(bytes)

    /** 重复字节流：[limit] 用尽才 EOF（Long.MAX_VALUE = 永不 EOF）。 */
    private class EndlessStream(private val limit: Long = Long.MAX_VALUE) : InputStream() {
        private var produced = 0L

        override fun read(): Int {
            if (produced >= limit) return -1
            produced += 1
            return 'A'.code
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (produced >= limit) return -1
            val count = minOf(len.toLong(), limit - produced).toInt()
            b.fill('A'.code.toByte(), off, off + count)
            produced += count
            return count
        }
    }

    private companion object {
        const val CONTACT_ID = 1L
        const val OWNER = "alice"
        const val OTHER_USER = "bob"
    }
}

/** 只用于验证容器解析期异常 `MaxUploadSizeExceededException -> 413` 的全局映射。 */
@RestController
private class MultipartLimitProbeController {
    @PostMapping("/probe/multipart-limit")
    fun probe(): String = throw MaxUploadSizeExceededException(MAX_FILE_BYTES)
}
