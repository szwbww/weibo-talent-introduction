package com.weibo.talentintroduction.document.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.audit.repository.OperatorActionLogRepository
import com.weibo.talentintroduction.auth.config.AuthInterceptor
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.OperatorStatusReconcileService
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.domain.ManualExpertMaterialUpload
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.document.repository.ManualExpertMaterialUploadRepository
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.document.service.ManualExpertMaterialUploadService
import com.weibo.talentintroduction.document.service.MaterialRow
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentTransferRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.AttachmentTransferService
import com.weibo.talentintroduction.mail.service.MailAttachmentService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID
import kotlin.streams.toList

/**
 * 手动材料上传贯通测试（I-1～I-8，fast-p manual-expert-material-upload backend 阶段 3）。
 *
 * 真实件：V130 后的三张表写入语义（[ManualExpertMaterialUploadService] 真实流式落盘、真实
 * 字节计数、真实原子移动、真实事务提交/回滚）、真实 [MailAttachmentService.inferDocumentType]
 * 材料类型规则、真实 [ExpertMaterialService] 统一读模型（SQL 投影 → 行装配 → 来源/状态/能力）、
 * 真实 [ExpertMaterialController] + [AuthInterceptor] + [GlobalExceptionHandler] HTTP 语义、
 * 真实 [OperatorStatusReconcileService] 期望值映射。只 mock 仓储边界（内存台账 + 只在 commit
 * 时发布，因此「回滚后无残留」可断言）与 ES/SMTP 等外部副作用。
 *
 * 覆盖：I-1 三选一 owner、I-2 文件与三表同成同败、I-3 100 MiB 双层边界、I-4 成功即
 * STORED/PENDING_REVIEW 且无 transfer 行、I-5 可解释可筛选的手动来源、I-6 会话身份与
 * 跨专家拒绝、I-7 手动材料不成为邮件事件也不推进 MATERIALS_RECEIVED、I-8 parser ceiling 配置。
 */
class ManualExpertMaterialUploadFlowTest {

    @TempDir
    lateinit var storageRoot: Path

    /** 与 Spring Boot 默认 mapper 一致：Kotlin data class 反序列化需要 KotlinModule。 */
    private val kotlinMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    private val expertContactRepository: ExpertContactRepository =
        Mockito.mock(ExpertContactRepository::class.java)
    private val manualUploadRepository: ManualExpertMaterialUploadRepository =
        Mockito.mock(ManualExpertMaterialUploadRepository::class.java)
    private val mailAttachmentRepository: MailAttachmentRepository =
        Mockito.mock(MailAttachmentRepository::class.java)
    private val expertDocumentRepository: ExpertDocumentRepository =
        Mockito.mock(ExpertDocumentRepository::class.java)
    private val mailAttachmentTransferRepository: MailAttachmentTransferRepository =
        Mockito.mock(MailAttachmentTransferRepository::class.java)
    private val mailRecordRepository: MailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val inboundMailProcessingRepository: InboundMailProcessingRepository =
        Mockito.mock(InboundMailProcessingRepository::class.java)
    private val attachmentTransferService: AttachmentTransferService =
        Mockito.mock(AttachmentTransferService::class.java)
    private val jdbc: NamedParameterJdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate::class.java)
    private val authService: AuthService = Mockito.mock(AuthService::class.java)

    // ---- I-7：运营状态反推协作件 ----
    private val bounceRecordRepository: BounceRecordRepository =
        Mockito.mock(BounceRecordRepository::class.java)
    private val operatorActionLogRepository: OperatorActionLogRepository =
        Mockito.mock(OperatorActionLogRepository::class.java)
    private val restTemplate: org.springframework.web.client.RestTemplate =
        Mockito.mock(org.springframework.web.client.RestTemplate::class.java)

    private lateinit var properties: MailAttachmentStorageProperties
    private lateinit var mailAttachmentService: MailAttachmentService
    private lateinit var service: ManualExpertMaterialUploadService
    private lateinit var materialService: ExpertMaterialService
    private lateinit var reconcileService: OperatorStatusReconcileService
    private lateinit var mockMvc: MockMvc

    // ---- 仓储内存台账（只在事务提交时发布，见 TrackingTransactionManager）----
    private val uploadRows = linkedMapOf<Long, ManualExpertMaterialUpload>()
    private val attachmentRows = linkedMapOf<Long, MailAttachment>()
    private val documentRows = linkedMapOf<Long, ExpertDocument>()
    private val staged = mutableListOf<() -> Unit>()
    private var nextId = 1L

    private lateinit var transactions: TrackingTransactionManager

    private var failAttachmentWrites = false
    private var failDocumentWrites = false

    @BeforeEach
    fun setUp() {
        uploadRows.clear()
        attachmentRows.clear()
        documentRows.clear()
        staged.clear()
        nextId = 1L
        failAttachmentWrites = false
        failDocumentWrites = false

        properties = MailAttachmentStorageProperties(basePath = storageRoot.toString())
        mailAttachmentService = MailAttachmentService(
            properties,
            mailAttachmentRepository,
            expertDocumentRepository,
            mailAttachmentTransferRepository
        )
        transactions = TrackingTransactionManager(
            publish = { staged.forEach { it() }; staged.clear() },
            discard = { staged.clear() }
        )
        service = ManualExpertMaterialUploadService(
            properties,
            expertContactRepository,
            manualUploadRepository,
            mailAttachmentRepository,
            expertDocumentRepository,
            mailAttachmentService,
            TransactionTemplate(transactions)
        )
        materialService = ExpertMaterialService(
            properties,
            expertDocumentRepository,
            mailAttachmentRepository,
            mailRecordRepository,
            inboundMailProcessingRepository,
            mailAttachmentTransferRepository,
            attachmentTransferService,
            mailAttachmentService,
            jdbc,
            manualUploadRepository
        )
        reconcileService = OperatorStatusReconcileService(
            expertContactRepository,
            mailRecordRepository,
            mailAttachmentRepository,
            bounceRecordRepository,
            operatorActionLogRepository,
            restTemplate,
            ElasticsearchProperties(
                baseUrl = "http://es:9200",
                username = "es-user",
                password = "es-pass",
                rawIndexName = "orcid_info",
                candidateIndexName = "orcid_info_candidate",
                applicationIndexName = "orcid_info_application"
            )
        )
        mockMvc = MockMvcBuilders
            .standaloneSetup(ExpertMaterialController(materialService, service))
            .addInterceptors(AuthInterceptor(authService, ObjectMapper()))
            .setControllerAdvice(GlobalExceptionHandler())
            // 生产由 Spring Boot 配置 Jackson（Kotlin + JavaTimeModule，LocalDateTime 为
            // ISO-8601 字符串）；standalone MockMvc 的默认 mapper 会把它写成数字数组，
            // 故这里显式对齐，确保本测试产出的冻结样例与前端真实收到的 JSON 逐字一致。
            .setMessageConverters(
                MappingJackson2HttpMessageConverter(
                    ObjectMapper()
                        .registerModule(KotlinModule.Builder().build())
                        .registerModule(JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                )
            )
            .build()

        Mockito.`when`(authService.findUser(OWNER)).thenReturn(adminUser(OWNER))
        Mockito.`when`(expertContactRepository.existsById(CONTACT_ID)).thenReturn(true)

        Mockito.doAnswer { invocation ->
            val row = invocation.getArgument<ManualExpertMaterialUpload>(0)
            val saved = row.copy(id = nextId++)
            staged += { uploadRows[saved.id!!] = saved }
            saved
        }.`when`(manualUploadRepository).save(Mockito.any())
        Mockito.doAnswer { invocation ->
            if (failAttachmentWrites) throw IllegalStateException("attachment write failed")
            val row = invocation.getArgument<MailAttachment>(0)
            val saved = row.copy(id = nextId++)
            staged += { attachmentRows[saved.id!!] = saved }
            saved
        }.`when`(mailAttachmentRepository).save(Mockito.any())
        Mockito.doAnswer { invocation ->
            if (failDocumentWrites) throw IllegalStateException("document write failed")
            val row = invocation.getArgument<ExpertDocument>(0)
            val saved = row.copy(id = nextId++)
            staged += { documentRows[saved.id!!] = saved }
            saved
        }.`when`(expertDocumentRepository).save(Mockito.any())

        Mockito.`when`(manualUploadRepository.findById(Mockito.anyLong())).thenAnswer { invocation ->
            Optional.ofNullable(uploadRows[invocation.getArgument<Long>(0)])
        }
        Mockito.`when`(mailAttachmentRepository.findById(Mockito.anyLong())).thenAnswer { invocation ->
            Optional.ofNullable(attachmentRows[invocation.getArgument<Long>(0)])
        }
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(Mockito.anyLong()))
            .thenAnswer { invocation ->
                documentRows.values.firstOrNull { it.mailAttachmentId == invocation.getArgument<Long>(0) }
            }
        // I-4：手动材料不创建 transfer 行——解析器与状态机都看不到 transfer。
        Mockito.`when`(mailAttachmentTransferRepository.findByAttachmentId(Mockito.anyLong()))
            .thenReturn(null)
        Mockito.`when`(inboundMailProcessingRepository.findAllByExpertContactId(Mockito.anyLong()))
            .thenReturn(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Mockito.reset(
            expertContactRepository,
            manualUploadRepository,
            mailAttachmentRepository,
            expertDocumentRepository,
            mailAttachmentTransferRepository,
            mailRecordRepository,
            inboundMailProcessingRepository,
            attachmentTransferService,
            jdbc,
            authService
        )
    }

    // ------------------------------------------------------------------
    // I-1/I-2/I-5：小文件成功路径（HTTP 201 + 磁盘 + 三表 + 会话身份）
    // ------------------------------------------------------------------

    @Test
    fun `small upload returns 201 with session identity real bytes and three rows`() {
        val content = "手动上传的简历内容".toByteArray(StandardCharsets.UTF_8)

        val response = postUpload(OWNER, CONTACT_ID, "cv-张伟.pdf", "application/pdf", content)

        assertEquals(201, response.status)
        val body = json(response)
        assertEquals("cv-张伟.pdf", body.path("fileName").asText())
        assertEquals("application/pdf", body.path("contentType").asText())
        assertEquals(content.size.toLong(), body.path("fileSize").asLong())
        assertEquals("CV", body.path("documentType").asText())
        assertEquals("PENDING_REVIEW", body.path("documentStatus").asText())
        assertEquals("STORED", body.path("storageState").asText())
        val attachmentId = body.path("attachmentId").asLong()
        val documentId = body.path("documentId").asLong()
        assertTrue(attachmentId > 0 && documentId > 0)
        // I-6：响应绝不含磁盘路径/绝对根/临时文件名。
        val raw = text(response)
        assertFalse(raw.contains("storagePath"))
        assertFalse(raw.contains(storageRoot.toString()))
        assertFalse(raw.contains(".tmp-"))

        // 磁盘：只有 manual/<UUID> 一个最终文件，字节与上传一致。
        val stored = attachmentRows.getValue(attachmentId)
        val finalPath = Path.of(stored.storagePath!!)
        assertEquals(manualRoot().toRealPath(), finalPath.parent.toRealPath())
        assertEquals(UUID.fromString(finalPath.fileName.toString()).toString(), finalPath.fileName.toString())
        assertEquals(content.size.toLong(), Files.size(finalPath))
        assertArrayEquals(content, Files.readAllBytes(finalPath))
        // manual 目录只有本次 final 文件：没有任何 `.tmp-*` 或其它残留。
        assertEquals(listOf(finalPath.fileName.toString()), fileNamesOf(manualRoot()))

        // I-1：三选一 owner = 手动 owner（两个邮件 owner 均为 null，绝不伪造邮件事件）。
        assertNull(stored.mailRecordId)
        assertNull(stored.inboundProcessingId)
        assertEquals(content.size.toLong(), stored.fileSize)
        assertNotNull(stored.createdAt)
        val upload = uploadRows.values.single()
        assertEquals(1, uploadRows.size)
        assertEquals(stored.manualUploadId, upload.id)
        assertEquals(CONTACT_ID, upload.expertContactId)
        assertEquals(OWNER, upload.uploadedBy)
        assertNotNull(upload.createdAt)

        // I-4：一条 PENDING_REVIEW 材料，document 归属与 attachment 一对一。
        assertEquals(1, documentRows.size)
        val document = documentRows.getValue(documentId)
        assertEquals(CONTACT_ID, document.expertContactId)
        assertEquals(attachmentId, document.mailAttachmentId)
        assertEquals("CV", document.documentType)
        assertEquals("PENDING_REVIEW", document.documentStatus)

        // I-4：不建 transfer 行（仓库上没有任何写；读路径也看不到 transfer）。
        Mockito.verify(mailAttachmentTransferRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `file name path segments and control characters never become a disk path segment`() {
        val content = "x".toByteArray(StandardCharsets.UTF_8)

        val response = postUpload(OWNER, CONTACT_ID, "..\\..\\etc/pa\u0000ss wd cv.pdf", "application/pdf", content)

        assertEquals(201, response.status)
        val body = json(response)
        assertEquals("pass wd cv.pdf", body.path("fileName").asText())
        assertEquals("CV", body.path("documentType").asText())
        val stored = attachmentRows.getValue(body.path("attachmentId").asLong())
        val finalPath = Path.of(stored.storagePath!!)
        assertEquals(manualRoot().toRealPath(), finalPath.parent.toRealPath())
        assertEquals(36, finalPath.fileName.toString().length)
        // basePath 根下只有 manual 目录：文件名没有产生任何越界路径段。
        assertEquals(listOf("manual"), fileNamesOf(storageRoot))
    }

    // ------------------------------------------------------------------
    // I-3：精确 100 MiB 边界与 0 字节
    // ------------------------------------------------------------------

    @Test
    fun `zero byte and exactly one hundred MiB uploads are accepted`() {
        assertEquals(104857600L, ManualExpertMaterialUploadService.MAX_MANUAL_MATERIAL_BYTES)

        val empty = service.upload(
            CONTACT_ID,
            OWNER,
            "empty.txt",
            "text/plain",
            ZeroLengthStream()
        )
        assertEquals(0L, empty.fileSize)
        assertEquals("OTHER", empty.documentType)
        val emptyStored = attachmentRows.getValue(empty.attachmentId)
        assertEquals(0L, emptyStored.fileSize)
        assertEquals(0L, Files.size(Path.of(emptyStored.storagePath!!)))

        val exact = service.upload(
            CONTACT_ID,
            OWNER,
            "phd-degree.pdf",
            "application/pdf",
            FixedLengthStream(104857600L)
        )
        assertEquals(104857600L, exact.fileSize)
        assertEquals("PHD_DEGREE", exact.documentType)
        val exactStored = attachmentRows.getValue(exact.attachmentId)
        assertEquals(104857600L, exactStored.fileSize)
        assertEquals(104857600L, Files.size(Path.of(exactStored.storagePath!!)))
        assertEquals(2, uploadRows.size)
    }

    @Test
    fun `http error contract is 401 400 404 413 and never leaks the storage path`() {
        // 匿名：AuthInterceptor 固定 401（与生产一致）。
        val anonymous = mockMvc.perform(
            multipart("/api/expert-contacts/$CONTACT_ID/materials/uploads")
                .file(MockMultipartFile("file", "cv.pdf", "application/pdf", "x".toByteArray()))
        ).andReturn().response
        assertEquals(401, anonymous.status)
        assertEquals("UNAUTHORIZED", json(anonymous).path("code").asText())
        assertTrue(uploadRows.isEmpty())

        // 缺 file 字段：400 业务文案（不让 MissingServletRequestPartException 落成 500）。
        val missing = mockMvc.perform(
            multipart("/api/expert-contacts/$CONTACT_ID/materials/uploads").session(sessionOf(OWNER))
        ).andReturn().response
        assertEquals(400, missing.status)
        assertEquals("BAD_REQUEST", json(missing).path("code").asText())
        assertTrue(uploadRows.isEmpty())

        // 未知专家：404，且不落任何文件/行。
        val unknown = postUpload(OWNER, 999L, "cv.pdf", "application/pdf", "x".toByteArray())
        assertEquals(404, unknown.status)
        assertTrue(uploadRows.isEmpty())
        assertEquals(emptyList<String>(), fileNamesOf(manualRoot().takeIf { Files.exists(it) }))

        // 104857601 字节：413 + code=PAYLOAD_TOO_LARGE，零文件零元数据、无临时文件。
        val oversize = postUpload(
            OWNER,
            CONTACT_ID,
            "too-big.pdf",
            "application/pdf",
            ByteArray(104857601)
        )
        assertEquals(413, oversize.status)
        assertEquals("PAYLOAD_TOO_LARGE", json(oversize).path("code").asText())
        val oversizeRaw = text(oversize)
        assertFalse(oversizeRaw.contains("storagePath"))
        assertFalse(oversizeRaw.contains(storageRoot.toString()))
        assertTrue(uploadRows.isEmpty())
        assertTrue(attachmentRows.isEmpty())
        assertTrue(documentRows.isEmpty())
        assertEquals(emptyList<String>(), fileNamesOf(manualRoot()))
        // 超限在流式落盘阶段就被拒绝，早于事务，故没有任何 BEGIN/COMMIT/ROLLBACK。
        assertTrue(transactions.outcomes.isEmpty())
    }

    // ------------------------------------------------------------------
    // I-2：文件与三表同成同败（流失败 / 第二三写失败 / 提交失败）
    // ------------------------------------------------------------------

    @Test
    fun `stream failure writes nothing to disk or database`() {
        assertThrows(IOException::class.java) {
            service.upload(CONTACT_ID, OWNER, "cv.pdf", "application/pdf", FailingStream("cv-content"))
        }

        // 落盘失败发生在事务之前：零数据库写、无临时/最终文件。
        assertTrue(transactions.outcomes.isEmpty())
        assertTrue(uploadRows.isEmpty())
        assertTrue(attachmentRows.isEmpty())
        assertTrue(documentRows.isEmpty())
        assertEquals(emptyList<String>(), fileNamesOf(manualRoot()))
    }

    @Test
    fun `attachment and document write failures roll back and delete the final file`() {
        failAttachmentWrites = true
        assertThrows(IllegalStateException::class.java) {
            service.upload(CONTACT_ID, OWNER, "cv.pdf", "application/pdf", ZeroLengthStream())
        }
        assertEquals(listOf("BEGIN", "ROLLBACK"), transactions.outcomes)
        assertEquals(emptyList<String>(), fileNamesOf(manualRoot()))
        assertTrue(uploadRows.isEmpty())
        assertTrue(attachmentRows.isEmpty())
        assertTrue(documentRows.isEmpty())

        transactions.outcomes.clear()
        failAttachmentWrites = false
        failDocumentWrites = true
        assertThrows(IllegalStateException::class.java) {
            service.upload(CONTACT_ID, OWNER, "cv.pdf", "application/pdf", ZeroLengthStream())
        }
        // 第三行失败：前两行的暂存写入随回滚作废，final 文件被删除。
        assertEquals(listOf("BEGIN", "ROLLBACK"), transactions.outcomes)
        assertEquals(emptyList<String>(), fileNamesOf(manualRoot()))
        assertTrue(uploadRows.isEmpty())
        assertTrue(attachmentRows.isEmpty())
        assertTrue(documentRows.isEmpty())
    }

    @Test
    fun `commit failure deletes the final file and publishes no rows`() {
        transactions.failCommit = true

        assertThrows(IllegalStateException::class.java) {
            service.upload(CONTACT_ID, OWNER, "cv.pdf", "application/pdf", ZeroLengthStream())
        }

        assertEquals(listOf("BEGIN", "ROLLBACK"), transactions.outcomes)
        assertEquals(emptyList<String>(), fileNamesOf(manualRoot()))
        assertTrue(uploadRows.isEmpty())
        assertTrue(attachmentRows.isEmpty())
        assertTrue(documentRows.isEmpty())
    }

    // ------------------------------------------------------------------
    // I-4/I-5/I-6：统一读模型、来源筛选与跨专家拒绝
    // ------------------------------------------------------------------

    @Test
    fun `same expert list is stored pending review with the manual source`() {
        val content = "material".toByteArray(StandardCharsets.UTF_8)
        val uploaded = upload(CONTACT_ID, OWNER, "cv.pdf", "application/pdf", content)
        val upload = uploadRows.getValue(uploaded.uploadId)

        val page = materialService.assemblePage(
            CONTACT_ID,
            listOf(materialRowFor(uploaded.attachmentId)),
            emptyList(),
            0,
            10,
            null,
            null,
            null,
            null
        )

        val item = page.items.single()
        assertEquals(uploaded.attachmentId, item.attachmentId)
        assertEquals(uploaded.documentId, item.documentId)
        assertEquals("STORED", item.storageState)
        assertEquals("PENDING_REVIEW", item.documentStatus)
        assertEquals("CV", item.documentType)
        assertFalse(item.canFetch)
        assertTrue(item.canDownload)
        assertTrue(item.canPreview)
        assertTrue(item.canAnalyze)
        assertEquals(content.size.toLong(), item.actualSize)
        assertNull(item.error)
        assertEquals("MANUAL_UPLOAD", item.source?.type)
        assertEquals(CONTACT_ID, item.source?.id)
        assertEquals("手动上传", item.source?.subject)
        assertEquals(OWNER, item.source?.uploadedBy)
        assertNull(item.source?.accountCode)
        assertEquals(upload.createdAt, item.source?.receivedAt)
        assertEquals(
            "/api/expert-contacts/$CONTACT_ID/attachments/${uploaded.attachmentId}/download",
            item.downloadUrl
        )
        // summary 与列表同快照：手动材料计为已存，不是「待获取」。
        assertEquals(1L, page.summary.total)
        assertEquals(1L, page.summary.stored)
        assertEquals(0L, page.summary.metadataOnly)
        assertEquals(0L, page.summary.sourceUnavailable)
    }

    @Test
    fun `manual source filter returns only manual materials of the contact`() {
        val uploaded = upload(CONTACT_ID, OWNER, "cv.pdf", "application/pdf", "a".toByteArray())
        val manualRow = materialRowFor(uploaded.attachmentId)
        val mailRow = legacyMailRow()
        stubMaterialList(listOf(manualRow, mailRow))

        val filtered = materialService.listMaterials(CONTACT_ID, 0, 10, null, "MANUAL_UPLOAD", CONTACT_ID, null)
        assertEquals(1L, filtered.total)
        assertEquals(uploaded.attachmentId, filtered.items.single().attachmentId)
        assertEquals("MANUAL_UPLOAD", filtered.items.single().source?.type)
        assertEquals(OWNER, filtered.items.single().source?.uploadedBy)

        // 邮件来源投影不变，全量列表仍含两类来源。
        val all = materialService.listMaterials(CONTACT_ID, 0, 10, null, null, null, null)
        assertEquals(2L, all.total)
        val mailOnly = materialService.listMaterials(CONTACT_ID, 0, 10, null, "MAIL_RECORD", MAIL_RECORD_ID, null)
        assertEquals(1L, mailOnly.total)
        assertEquals("MAIL_RECORD", mailOnly.items.single().source?.type)
    }

    @Test
    fun `manual material is readable only for its own expert through the shared resolver`() {
        val content = "shared-resolver".toByteArray(StandardCharsets.UTF_8)
        val uploaded = upload(CONTACT_ID, OWNER, "cv.pdf", "application/pdf", content)

        // 下载/预览/AI 共用同一个 resolver：同专家可读，且拿回真实文件字节。
        val ready = materialService.resolveReadyFile(CONTACT_ID, uploaded.attachmentId)
        assertEquals(uploaded.attachmentId, ready.attachment.id)
        assertEquals(CONTACT_ID, materialService.resolveOwnerContact(ready.attachment))
        assertEquals(content.size.toLong(), Files.size(ready.path))
        assertArrayEquals(content, Files.readAllBytes(ready.path))

        // 跨专家：manual owner 指向另一位专家 → 归属校验拒绝（不返回文件）。
        val owner = uploadRows.getValue(uploaded.uploadId)
        uploadRows[owner.id!!] = owner.copy(expertContactId = OTHER_CONTACT_ID)
        assertThrows(IllegalArgumentException::class.java) {
            materialService.resolveReadyFile(CONTACT_ID, uploaded.attachmentId)
        }
        // 该材料在另一位专家名下可见（owner 精确相等），在原作者名下来源不可核、不可下载。
        val foreignPage = materialService.assemblePage(
            CONTACT_ID,
            listOf(materialRowFor(uploaded.attachmentId)),
            emptyList(),
            0,
            10,
            null,
            null,
            null,
            null
        )
        val foreignItem = foreignPage.items.single()
        assertFalse(foreignItem.canDownload)
        assertFalse(foreignItem.canPreview)
        assertFalse(foreignItem.canAnalyze)
        assertNull(foreignItem.source)
        assertEquals("SOURCE_AMBIGUOUS", foreignItem.error?.code)
        // 文件本身在服务器上（STORED），但归属不属于本专家，因此不可下载/预览/分析。
        assertEquals("STORED", foreignItem.storageState)
    }

    // ------------------------------------------------------------------
    // I-7：手动材料不成为邮件事件，也不推进 MATERIALS_RECEIVED
    // ------------------------------------------------------------------

    @Test
    fun `manual attachment does not advance operator status to MATERIALS_RECEIVED`() {
        val uploaded = upload(CONTACT_ID, OWNER, "cv.pdf", "application/pdf", "a".toByteArray())
        val stored = attachmentRows.getValue(uploaded.attachmentId)
        val inbound = MailRecord(
            id = 10L,
            expertContactId = CONTACT_ID,
            direction = "INBOUND",
            mailType = "REPLY",
            messageId = "msg-10",
            inReplyTo = null,
            subject = "Re: introduction",
            body = "body",
            matchedQaRuleId = null,
            sendStatus = null,
            receivedAt = LocalDateTime.of(2026, 9, 20, 9, 0),
            sentAt = null
        )
        Mockito.`when`(expertContactRepository.findAll()).thenReturn(
            listOf(
                ExpertContact(
                    id = CONTACT_ID,
                    campaignId = 1,
                    orcidId = "ORCID-1",
                    expertEmail = "expert1@example.com",
                    expertName = "Expert 1",
                    operatorStatus = "REPLIED"
                )
            )
        )
        Mockito.`when`(mailRecordRepository.findAll()).thenReturn(listOf(inbound))
        Mockito.`when`(mailAttachmentRepository.findAll()).thenReturn(listOf(stored))
        Mockito.`when`(bounceRecordRepository.findAll()).thenReturn(emptyList())
        Mockito.`when`(operatorActionLogRepository.findContactIdsWithChangeOperatorStatusLogs(Mockito.anyList()))
            .thenReturn(emptyList())

        val report = reconcileService.reconcile()

        // 手动材料 mail_record_id 为 null，不能充当「来信材料附件」。
        assertNull(stored.mailRecordId)
        assertNotNull(stored.manualUploadId)
        assertEquals(1, report.total)
        assertEquals(0, report.dbVsExpected)
        assertTrue(report.samples.none { it.expectedStatus == "MATERIALS_RECEIVED" })
    }

    // ------------------------------------------------------------------
    // I-8：容器 parser ceiling 配置（业务上限仍由服务按实际字节裁决）
    // ------------------------------------------------------------------

    @Test
    fun `multipart parser ceiling is one hundred MiB in application yml`() {
        val yml = File("src/main/resources/application.yml").readText()

        assertTrue(Regex("max-file-size:\\s*100MB").containsMatchIn(yml))
        assertTrue(Regex("max-request-size:\\s*101MB").containsMatchIn(yml))
        assertFalse(Regex("max-file-size:\\s*10MB").containsMatchIn(yml))
        assertFalse(Regex("max-request-size:\\s*11MB").containsMatchIn(yml))
    }

    // ------------------------------------------------------------------
    // 冻结样例（跨计划契约）：201 / 413 / GET manual item，均不含 storagePath
    // ------------------------------------------------------------------

    /**
     * 与 frontend 子计划共享的三段冻结响应样例。测试同时断言「不含物理路径」，
     * 并把真实响应体打到 stdout，便于在联调时逐字核对（前端只依赖这些字段）。
     */
    @Test
    fun `frozen response samples carry no storage path`() {
        val content = "sample".toByteArray(StandardCharsets.UTF_8)
        val created = postUpload(OWNER, CONTACT_ID, "cv.pdf", "application/pdf", content)
        assertEquals(201, created.status)
        val createdBody = text(created)
        assertFalse(createdBody.contains("storagePath"))
        println("FROZEN_201=$createdBody")

        val attachmentId = json(created).path("attachmentId").asLong()
        stubMaterialList(listOf(materialRowFor(attachmentId)))
        val listed = mockMvc.perform(
            get("/api/expert-contacts/$CONTACT_ID/materials").session(sessionOf(OWNER))
        ).andReturn().response
        assertEquals(200, listed.status)
        val listedRaw = text(listed)
        assertFalse(listedRaw.contains("storagePath"))
        assertFalse(listedRaw.contains(storageRoot.toString()))
        assertFalse(listedRaw.contains(".tmp-"))
        println("FROZEN_GET_ITEM=" + json(listed).path("items")[0].toString())

        val oversize = postUpload(OWNER, CONTACT_ID, "big.pdf", "application/pdf", ByteArray(104857601))
        assertEquals(413, oversize.status)
        val oversizeBody = text(oversize)
        assertFalse(oversizeBody.contains("storagePath"))
        assertFalse(oversizeBody.contains(storageRoot.toString()))
        println("FROZEN_413=$oversizeBody")
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private data class Uploaded(val attachmentId: Long, val documentId: Long, val uploadId: Long, val fileSize: Long)

    /** 服务级上传（不经 HTTP）：用于边界与失败注入用例。 */
    private fun upload(
        contactId: Long,
        username: String,
        filename: String,
        contentType: String,
        bytes: ByteArray
    ): Uploaded {
        val response = service.upload(
            contactId,
            username,
            filename,
            contentType,
            bytes.inputStream()
        )
        val stored = attachmentRows.getValue(response.attachmentId)
        return Uploaded(
            attachmentId = response.attachmentId,
            documentId = response.documentId,
            uploadId = stored.manualUploadId!!,
            fileSize = response.fileSize
        )
    }

    private fun postUpload(
        username: String,
        contactId: Long,
        filename: String,
        contentType: String,
        bytes: ByteArray
    ): MockHttpServletResponse =
        mockMvc.perform(
            multipart("/api/expert-contacts/$contactId/materials/uploads")
                .file(MockMultipartFile("file", filename, contentType, bytes))
                .session(sessionOf(username))
        ).andReturn().response

    private fun sessionOf(username: String): MockHttpSession =
        MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }

    private fun adminUser(username: String): AdminUser =
        AdminUser(
            username = username,
            passwordHash = "not-checked",
            mustChangePassword = false,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

    private fun json(response: MockHttpServletResponse): JsonNode =
        kotlinMapper.readTree(text(response))

    /** MockHttpServletResponse 默认按 ISO-8859-1 读字符串；中文材料名必须按 UTF-8 字节解析。 */
    private fun text(response: MockHttpServletResponse): String =
        String(response.contentAsByteArray, StandardCharsets.UTF_8)

    private fun manualRoot(): Path =
        Path.of(properties.basePath).toAbsolutePath().normalize().resolve("manual")

    /** 目录直接子项名字（用于断言没有遗留 `.tmp-*` / 越界路径段）。 */
    private fun fileNamesOf(dir: Path?): List<String> {
        if (dir == null || !Files.isDirectory(dir)) {
            return emptyList()
        }
        return Files.list(dir).use { stream -> stream.map { it.fileName.toString() }.sorted().toList() }
    }

    /**
     * 与 MATERIAL_LIST_SQL 同形的行投影：从内存台账构造 `MaterialRow`，
     * 让统一读模型（来源/状态/能力/summary）读到与真实 SQL 完全一致的字段。
     */
    private fun materialRowFor(attachmentId: Long): MaterialRow {
        val attachment = attachmentRows.getValue(attachmentId)
        val document = documentRows.values.first { it.mailAttachmentId == attachmentId }
        val upload = attachment.manualUploadId?.let { uploadRows.getValue(it) }
        return MaterialRow(
            documentId = document.id!!,
            documentType = document.documentType,
            documentStatus = document.documentStatus,
            attachmentId = attachment.id!!,
            fileName = attachment.fileName,
            contentType = attachment.contentType,
            fileSize = attachment.fileSize,
            storagePath = attachment.storagePath,
            attachmentCreatedAt = attachment.createdAt,
            mailRecordId = attachment.mailRecordId,
            attachmentInboundProcessingId = attachment.inboundProcessingId,
            transferId = null,
            transferState = null,
            transferInboundProcessingId = null,
            bytesDownloaded = 0,
            encodedSize = null,
            errorCode = null,
            errorMessage = null,
            pSubject = null,
            pReceivedAt = null,
            pAccountCode = null,
            pExpertContactId = null,
            mrSubject = null,
            mrReceivedAt = null,
            mrAccountCode = null,
            mrDirection = null,
            mrMessageId = null,
            mrExpertContactId = null,
            manualUploadId = attachment.manualUploadId,
            muExpertContactId = upload?.expertContactId,
            muUploadedBy = upload?.uploadedBy,
            muCreatedAt = upload?.createdAt
        )
    }

    /** 旧邮件来源行（回归：邮件来源投影与筛选不受手动材料影响）。 */
    private fun legacyMailRow(): MaterialRow = MaterialRow(
        documentId = 900L,
        documentType = "CV",
        documentStatus = "PENDING_REVIEW",
        attachmentId = 900L,
        fileName = "mail-cv.pdf",
        contentType = "application/pdf",
        fileSize = null,
        storagePath = null,
        attachmentCreatedAt = LocalDateTime.of(2026, 9, 1, 9, 0),
        mailRecordId = MAIL_RECORD_ID,
        attachmentInboundProcessingId = null,
        transferId = null,
        transferState = "METADATA_ONLY",
        transferInboundProcessingId = null,
        bytesDownloaded = 0,
        encodedSize = 1024L,
        errorCode = null,
        errorMessage = null,
        pSubject = null,
        pReceivedAt = null,
        pAccountCode = null,
        pExpertContactId = null,
        mrSubject = "Old inbound",
        mrReceivedAt = LocalDateTime.of(2026, 9, 1, 9, 0),
        mrAccountCode = "acc-a",
        mrDirection = "INBOUND",
        mrMessageId = null,
        mrExpertContactId = CONTACT_ID
    )

    /** GET 材料列表走 SQL 投影，这里用同一 RowMapper 契约返回内存行。 */
    private fun stubMaterialList(rows: List<MaterialRow>) {
        Mockito.`when`(
            jdbc.query(
                Mockito.anyString(),
                Mockito.any(MapSqlParameterSource::class.java),
                Mockito.any(RowMapper::class.java)
            )
        ).thenAnswer { rows }
    }

    /**
     * 最小真实事务语义：BEGIN/COMMIT/ROLLBACK 可观测；事务内写出的行先暂存，
     * 只在 commit 时发布到可见台账，因此「回滚后无残留」与「提交后可见」都可断言。
     * [failCommit] 注入提交失败（与真实 DataSourceTransactionManager 提交失败同形：
     * 异常从 `TransactionTemplate.execute` 逃出，调用方必须删掉刚落的 final 文件）。
     */
    private class TrackingTransactionManager(
        private val publish: () -> Unit,
        private val discard: () -> Unit
    ) : AbstractPlatformTransactionManager() {
        val outcomes = mutableListOf<String>()
        var failCommit = false

        override fun doGetTransaction(): Any = Any()

        override fun doBegin(transaction: Any, definition: TransactionDefinition) {
            outcomes += BEGIN
        }

        override fun doCommit(status: DefaultTransactionStatus) {
            if (failCommit) {
                throw IllegalStateException("commit failed")
            }
            publish()
            outcomes += COMMIT
        }

        override fun doRollback(status: DefaultTransactionStatus) {
            discard()
            outcomes += ROLLBACK
        }

        private companion object {
            const val BEGIN = "BEGIN"
            const val COMMIT = "COMMIT"
            const val ROLLBACK = "ROLLBACK"
        }
    }

    /** 0 字节合法材料。 */
    private class ZeroLengthStream : InputStream() {
        override fun read(): Int = -1
        override fun read(b: ByteArray, off: Int, len: Int): Int = -1
    }

    /** 生成任意长度的合成字节流：边界用例不需要为 100 MiB 分配数组。 */
    private class FixedLengthStream(private var remaining: Long) : InputStream() {
        private val chunk = ByteArray(CHUNK_BYTES) { 'a'.code.toByte() }

        override fun read(): Int {
            if (remaining <= 0) return -1
            remaining--
            return 'a'.code
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val count = minOf(len.toLong(), remaining, CHUNK_BYTES.toLong()).toInt()
            System.arraycopy(chunk, 0, b, off, count)
            remaining -= count
            return count
        }

        private companion object {
            const val CHUNK_BYTES = 64 * 1024
        }
    }

    /** 落盘中途失败的流：第一批字节成功，随后 IOException。 */
    private class FailingStream(private val first: String) : InputStream() {
        private var servedFirst = false

        override fun read(): Int = throw IOException("stream failed")

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (!servedFirst) {
                servedFirst = true
                val bytes = first.toByteArray(StandardCharsets.UTF_8)
                System.arraycopy(bytes, 0, b, off, bytes.size)
                return bytes.size
            }
            throw IOException("stream failed")
        }
    }

    private companion object {
        const val CONTACT_ID = 1L
        const val OTHER_CONTACT_ID = 2L
        const val MAIL_RECORD_ID = 100L
        const val OWNER = "op1"
    }
}
