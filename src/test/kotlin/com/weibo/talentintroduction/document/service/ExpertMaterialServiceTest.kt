package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.controller.ExpertDocumentAnalysisController
import com.weibo.talentintroduction.document.controller.ExpertDocumentBrowseController
import com.weibo.talentintroduction.document.controller.ExpertMaterialController
import com.weibo.talentintroduction.document.domain.DocumentStatus
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.domain.ExpertDocumentType
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.mail.controller.MailboxAttachmentController
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentTransferRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.AttachmentTransferService
import com.weibo.talentintroduction.mail.service.MailAttachmentService
import com.weibo.talentintroduction.mail.service.MailboxAttachmentService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.NoSuchElementException
import java.util.Optional

class ExpertMaterialServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var properties: MailAttachmentStorageProperties
    private val expertDocumentRepository = Mockito.mock(ExpertDocumentRepository::class.java)
    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val mailAttachmentTransferRepository = Mockito.mock(MailAttachmentTransferRepository::class.java)
    private val attachmentTransferService = Mockito.mock(AttachmentTransferService::class.java)
    private val mailAttachmentService = Mockito.mock(MailAttachmentService::class.java)
    private val jdbc = Mockito.mock(NamedParameterJdbcTemplate::class.java)

    private lateinit var service: ExpertMaterialService

    @BeforeEach
    fun setUp() {
        properties = MailAttachmentStorageProperties(basePath = tempDir.toString())
        service = ExpertMaterialService(
            properties,
            expertDocumentRepository,
            mailAttachmentRepository,
            mailRecordRepository,
            inboundMailProcessingRepository,
            mailAttachmentTransferRepository,
            attachmentTransferService,
            mailAttachmentService,
            jdbc
        )
        Mockito.`when`(mailAttachmentService.inferDocumentType(Mockito.anyString()))
            .thenAnswer { invocation ->
                val name = invocation.getArgument<String>(0).lowercase()
                when {
                    name.contains("cv") -> ExpertDocumentType.CV
                    name.contains("degree") -> ExpertDocumentType.PHD_DEGREE
                    else -> ExpertDocumentType.OTHER
                }
            }
    }

    @AfterEach
    fun tearDown() {
        Mockito.reset(
            expertDocumentRepository,
            mailAttachmentRepository,
            mailRecordRepository,
            inboundMailProcessingRepository,
            mailAttachmentTransferRepository,
            attachmentTransferService,
            mailAttachmentService,
            jdbc
        )
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun writeFile(name: String, dir: Path = tempDir, content: String = "content"): Path {
        Files.createDirectories(dir)
        val file = dir.resolve(name)
        Files.writeString(file, content)
        return file
    }

    private fun attachment(
        id: Long,
        mailRecordId: Long?,
        processingId: Long? = null,
        fileName: String = "cv.pdf",
        contentType: String? = "application/pdf",
        path: Path? = null,
        fileSize: Long? = null,
        createdAt: LocalDateTime = LocalDateTime.of(2026, 9, 1, 10, 0)
    ): MailAttachment = MailAttachment(
        id = id,
        mailRecordId = mailRecordId,
        inboundProcessingId = processingId,
        fileName = fileName,
        contentType = contentType,
        fileSize = fileSize ?: path?.let { Files.size(it) },
        storagePath = path?.toString(),
        createdAt = createdAt
    )

    private fun document(id: Long, contactId: Long, attachmentId: Long, docType: String = "CV"): ExpertDocument =
        ExpertDocument(
            id = id,
            expertContactId = contactId,
            mailAttachmentId = attachmentId,
            documentType = docType,
            documentStatus = DocumentStatus.PENDING_REVIEW.name,
            createdAt = LocalDateTime.of(2026, 9, 1, 10, 0),
            updatedAt = LocalDateTime.of(2026, 9, 1, 10, 0)
        )

    private fun mailRecord(
        id: Long,
        contactId: Long,
        accountCode: String? = "account-a",
        messageId: String? = "msg-$id",
        direction: String = "INBOUND",
        receivedAt: LocalDateTime = LocalDateTime.of(2026, 9, 1, 9, 0)
    ): MailRecord = MailRecord(
        id = id,
        expertContactId = contactId,
        direction = direction,
        mailType = "REPLY",
        senderAccountCode = accountCode,
        messageId = messageId,
        inReplyTo = null,
        subject = "Subject $id",
        body = "Body",
        matchedQaRuleId = null,
        sendStatus = null,
        receivedAt = receivedAt,
        sentAt = null,
        createdAt = receivedAt
    )

    private fun processing(
        id: Long,
        contactId: Long?,
        accountCode: String = "account-a",
        messageId: String? = "msg-$id",
        receivedAt: LocalDateTime = LocalDateTime.of(2026, 9, 1, 9, 30)
    ): InboundMailProcessing = InboundMailProcessing(
        id = id,
        senderAccountCode = accountCode,
        uidValidity = 5,
        imapUid = 100 + id,
        messageId = messageId,
        fromEmail = "expert@example.com",
        subject = "Inbound $id",
        receivedAt = receivedAt,
        processStatus = "PROCESSED",
        processReason = "AUTO_REPLIED",
        expertContactId = contactId,
        createdAt = receivedAt,
        updatedAt = receivedAt
    )

    private fun transfer(
        id: Long,
        attachmentId: Long,
        state: String = MailAttachmentTransfer.STATE_METADATA_ONLY,
        processingId: Long? = null,
        encodedSize: Long? = null,
        errorCode: String? = null,
        errorMessage: String? = null,
        bytesDownloaded: Long = 0
    ): MailAttachmentTransfer = MailAttachmentTransfer(
        id = id,
        attachmentId = attachmentId,
        purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
        accountCode = "account-a",
        folder = "INBOX",
        uidValidity = 5,
        imapUid = 100 + attachmentId,
        partPath = "2",
        messageId = "src-$attachmentId",
        fileName = "cv.pdf",
        contentType = "application/pdf",
        encodedSize = encodedSize,
        inboundProcessingId = processingId,
        state = state,
        bytesDownloaded = bytesDownloaded,
        errorCode = errorCode,
        errorMessage = errorMessage,
        createdAt = LocalDateTime.of(2026, 9, 1, 10, 0),
        updatedAt = LocalDateTime.of(2026, 9, 1, 10, 0)
    )

    private fun stubDocAttachment(attachmentId: Long, att: MailAttachment, contactId: Long, docId: Long = attachmentId) {
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(attachmentId))
            .thenReturn(document(docId, contactId, attachmentId))
        Mockito.`when`(mailAttachmentRepository.findById(attachmentId))
            .thenReturn(Optional.of(att))
    }

    // ------------------------------------------------------------------
    // resolveReadyFile（I-3：双 owner、realpath、409）
    // ------------------------------------------------------------------

    @Test
    fun `resolveReadyFile resolves a mail_record-owned stored file`() {
        val contactId = 1L
        val file = writeFile("cv.pdf", tempDir.resolve("1"))
        val att = attachment(10, mailRecordId = 100, path = file)
        stubDocAttachment(10, att, contactId)
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, contactId))
        Mockito.`when`(mailAttachmentTransferRepository.findByAttachmentId(10L)).thenReturn(null)

        val ready = service.resolveReadyFile(contactId, 10)

        assertEquals(10L, ready.attachment.id)
        assertEquals(file.toRealPath(), ready.path)
        assertTrue(Files.isRegularFile(ready.path))
    }

    @Test
    fun `resolveReadyFile resolves a processing-owned file bound to the same contact`() {
        val contactId = 1L
        val file = writeFile("degree.pdf", tempDir.resolve("1"))
        val att = attachment(11, mailRecordId = null, processingId = 500, path = file)
        stubDocAttachment(11, att, contactId)
        Mockito.`when`(inboundMailProcessingRepository.findById(500L))
            .thenReturn(Optional.of(processing(500, contactId)))

        val ready = service.resolveReadyFile(contactId, 11)

        assertEquals(file.toRealPath(), ready.path)
    }

    @Test
    fun `resolveReadyFile rejects a document of another contact`() {
        val file = writeFile("cv.pdf", tempDir.resolve("1"))
        val att = attachment(10, mailRecordId = 100, path = file)
        stubDocAttachment(10, att, contactId = 2)
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, 2))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.resolveReadyFile(1, 10)
        }
        assertTrue(ex.message!!.contains("does not belong"))
    }

    @Test
    fun `resolveReadyFile rejects an attachment whose record owner is another contact`() {
        val file = writeFile("cv.pdf", tempDir.resolve("1"))
        val att = attachment(10, mailRecordId = 100, path = file)
        stubDocAttachment(10, att, contactId = 1)
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, 999))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.resolveReadyFile(1, 10)
        }
        assertTrue(ex.message!!.contains("does not belong"))
    }

    @Test
    fun `metadata-only attachment throws MaterialNotReadyException carrying attachmentId and state`() {
        val contactId = 1L
        val att = attachment(10, mailRecordId = 100, path = null)
        stubDocAttachment(10, att, contactId)
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, contactId))
        Mockito.`when`(mailAttachmentTransferRepository.findByAttachmentId(10L))
            .thenReturn(transfer(20, 10, state = MailAttachmentTransfer.STATE_METADATA_ONLY))

        val ex = assertThrows(MaterialNotReadyException::class.java) {
            service.resolveReadyFile(contactId, 10)
        }
        assertEquals(10L, ex.attachmentId)
        assertEquals(MailAttachmentTransfer.STATE_METADATA_ONLY, ex.state)
    }

    @Test
    fun `missing local file of a STORED transfer throws MaterialNotReady with STORED state`() {
        val contactId = 1L
        val att = attachment(10, mailRecordId = 100, path = null)
        stubDocAttachment(10, att, contactId)
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, contactId))
        Mockito.`when`(mailAttachmentTransferRepository.findByAttachmentId(10L))
            .thenReturn(transfer(20, 10, state = MailAttachmentTransfer.STATE_STORED))

        val ex = assertThrows(MaterialNotReadyException::class.java) {
            service.resolveReadyFile(contactId, 10)
        }
        assertEquals(MailAttachmentTransfer.STATE_STORED, ex.state)
    }

    @Test
    fun `legacy attachment without transfer row and missing file throws MaterialNotReady as SOURCE_UNAVAILABLE`() {
        val contactId = 1L
        val att = attachment(10, mailRecordId = 100, path = null)
        stubDocAttachment(10, att, contactId)
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, contactId))
        Mockito.`when`(mailAttachmentTransferRepository.findByAttachmentId(10L)).thenReturn(null)

        val ex = assertThrows(MaterialNotReadyException::class.java) {
            service.resolveReadyFile(contactId, 10)
        }
        assertEquals(MailAttachmentTransfer.STATE_SOURCE_UNAVAILABLE, ex.state)
    }

    @Test
    fun `missing document throws NoSuchElementException`() {
        val file = writeFile("cv.pdf", tempDir.resolve("1"))
        val att = attachment(10, mailRecordId = 100, path = file)
        Mockito.`when`(mailAttachmentRepository.findById(10L)).thenReturn(Optional.of(att))
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(10L)).thenReturn(null)

        assertThrows(NoSuchElementException::class.java) {
            service.resolveReadyFile(1, 10)
        }
    }

    @Test
    fun `path outside basePath is rejected`() {
        val outside = writeFile("secret.txt", Files.createTempDirectory("outside"))
        val att = attachment(10, mailRecordId = 100, path = outside)
        stubDocAttachment(10, att, contactId = 1)
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, 1))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.resolveReadyFile(1, 10)
        }
        assertTrue(ex.message!!.contains("outside configured base path"))
    }

    @Test
    fun `symlink escaping basePath is rejected`() {
        val outsideDir = Files.createTempDirectory("outside-link")
        val outsideFile = writeFile("target.txt", outsideDir)
        val link = tempDir.resolve("link.txt")
        Files.createSymbolicLink(link, outsideFile)

        val att = attachment(10, mailRecordId = 100, path = link)
        stubDocAttachment(10, att, contactId = 1)
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, 1))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.resolveReadyFile(1, 10)
        }
        assertTrue(ex.message!!.contains("outside configured base path"))
    }

    @Test
    fun `unscoped resolveReadyFile requires no document (mailbox download path)`() {
        val file = writeFile("cv.pdf", tempDir.resolve("mailbox"))
        val att = attachment(10, mailRecordId = 100, path = file)
        Mockito.`when`(mailAttachmentRepository.findById(10L)).thenReturn(Optional.of(att))

        val ready = service.resolveReadyFileUnscoped(10)

        assertEquals(file.toRealPath(), ready.path)
        Mockito.verify(expertDocumentRepository, Mockito.never())
            .findFirstByMailAttachmentId(Mockito.anyLong())
    }

    // ------------------------------------------------------------------
    // 材料列表：分页/搜索/状态/来源/统计（I-1/I-2/I-4）
    // ------------------------------------------------------------------

    /** 1 件历史旧文件（无 transfer 行、真实落盘）+ 39 件 METADATA_ONLY 新附件。 */
    private fun fixture40Rows(): List<MaterialRow> {
        val dir = tempDir.resolve("expert1")
        Files.createDirectories(dir)
        val legacyFile = dir.resolve("legacy-cv.pdf")
        Files.writeString(legacyFile, "legacy")
        val rows = mutableListOf<MaterialRow>()
        // legacy row: attachment 1, mail_record 100 (received 2026-08-01), no transfer row
        rows += legacyRow(
            documentId = 1, attachmentId = 1, storagePath = legacyFile.toString(),
            mrReceivedAt = LocalDateTime.of(2026, 8, 1, 9, 0), mrMessageId = "msg-100",
            mailRecordId = 100
        )
        // 39 metadata-only rows: attachment 2..40, bridged processing 200..238
        for (i in 2..40) {
            rows += metadataRow(
                documentId = i.toLong(),
                attachmentId = i.toLong(),
                transferId = 1000L + i,
                processingId = 199L + i,
                receivedAt = LocalDateTime.of(2026, 9, 1, 9, 0).plusMinutes(i.toLong())
            )
        }
        return rows
    }

    private fun legacyRow(
        documentId: Long,
        attachmentId: Long,
        storagePath: String,
        mrReceivedAt: LocalDateTime,
        mrMessageId: String?,
        mailRecordId: Long,
        mrAccountCode: String? = "account-a",
        fileName: String = "legacy-cv.pdf"
    ): MaterialRow = MaterialRow(
        documentId = documentId,
        documentType = "CV",
        documentStatus = DocumentStatus.PENDING_REVIEW.name,
        attachmentId = attachmentId,
        fileName = fileName,
        contentType = "application/pdf",
        fileSize = 7L,
        storagePath = storagePath,
        attachmentCreatedAt = mrReceivedAt,
        mailRecordId = mailRecordId,
        attachmentInboundProcessingId = null,
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
        mrSubject = "Old inbound",
        mrReceivedAt = mrReceivedAt,
        mrAccountCode = mrAccountCode,
        mrDirection = "INBOUND",
        mrMessageId = mrMessageId,
        mrExpertContactId = 1
    )

    private fun metadataRow(
        documentId: Long,
        attachmentId: Long,
        transferId: Long,
        processingId: Long,
        receivedAt: LocalDateTime,
        fileName: String = "cv-$attachmentId.pdf"
    ): MaterialRow = MaterialRow(
        documentId = documentId,
        documentType = "CV",
        documentStatus = DocumentStatus.PENDING_REVIEW.name,
        attachmentId = attachmentId,
        fileName = fileName,
        contentType = "application/pdf",
        fileSize = null,
        storagePath = null,
        attachmentCreatedAt = receivedAt,
        mailRecordId = 100L,
        attachmentInboundProcessingId = null,
        transferId = transferId,
        transferState = MailAttachmentTransfer.STATE_METADATA_ONLY,
        transferInboundProcessingId = processingId,
        bytesDownloaded = 0,
        encodedSize = 1024L,
        errorCode = null,
        errorMessage = null,
        pSubject = "Inbound $processingId",
        pReceivedAt = receivedAt,
        pAccountCode = "account-a",
        pExpertContactId = 1,
        mrSubject = "Record of $processingId",
        mrReceivedAt = receivedAt,
        mrAccountCode = "account-a",
        mrDirection = "INBOUND",
        mrMessageId = "msg-$processingId",
        mrExpertContactId = 1
    )

    @Test
    fun `40 materials paginate 4 pages of 10 and summary covers all materials`() {
        val rows = fixture40Rows()

        val page1 = service.assemblePage(1, rows, emptyList(), 0, 10, null, null, null, null)
        assertEquals(10, page1.items.size)
        assertEquals(40L, page1.total)
        assertEquals(0, page1.page)
        // 排序：receivedAt DESC（39 件新附件在 9/1 之后 > 旧文件 8/1）
        assertTrue(page1.items[0].attachmentId == 40L || page1.items[0].attachmentId == 2L)

        val page4 = service.assemblePage(1, rows, emptyList(), 3, 10, null, null, null, null)
        assertEquals(10, page4.items.size)
        val lastPage = service.assemblePage(1, rows, emptyList(), 4, 10, null, null, null, null)
        assertEquals(0, lastPage.items.size)

        // summary 恒为全体材料且不受分页影响
        val summary = page1.summary
        assertEquals(40L, summary.total)
        assertEquals(1L, summary.stored)
        assertEquals(39L, summary.metadataOnly)
        assertEquals(0L, summary.active)
        assertEquals(0L, summary.failed)
        assertEquals(0L, summary.sourceUnavailable)
        // 默认分析候选 = 支持格式（PDF）且仅元数据的 CV/学位材料，完整返回不截断
        assertEquals(39, summary.defaultAnalysisAttachmentIds.size)
        assertTrue(summary.defaultAnalysisAttachmentIds.contains(40L))
    }

    @Test
    fun `same snapshot from repeated calls and identical item ids across hosts`() {
        val rows = fixture40Rows()
        // 第 4 页（offset 30）包含最旧的历史文件（attachmentId=1）
        val first = service.assemblePage(1, rows, emptyList(), 3, 10, null, null, null, null)
        val second = service.assemblePage(1, rows, emptyList(), 3, 10, null, null, null, null)
        assertEquals(first.items.map { it.attachmentId to it.storageState }, second.items.map { it.attachmentId to it.storageState })
        assertEquals(first.summary, second.summary)

        // 旧文件在同一快照里两处入口展示一致（attachmentId/documentId/可下载性）
        val legacy = first.items.single { it.attachmentId == 1L }
        assertEquals(1L, legacy.documentId)
        assertTrue(legacy.canDownload)
        assertTrue(legacy.canAnalyze)
        assertEquals("/api/expert-contacts/1/attachments/1/download", legacy.downloadUrl)
        val metadata = first.items.single { it.attachmentId == 2L }
        assertFalse(metadata.canDownload)
        assertTrue(metadata.canFetch)
        assertTrue(metadata.analysisSupported)
        assertFalse(metadata.canAnalyze)
        assertNull(metadata.actualSize)
        assertEquals(1024L, metadata.encodedSize)
    }

    @Test
    fun `search filters by file name across the whole expert set`() {
        val rows = fixture40Rows()
        val page = service.assemblePage(1, rows, emptyList(), 0, 10, q = "legacy", null, null, null)
        assertEquals(1L, page.total)
        assertEquals("legacy-cv.pdf", page.items.single().fileName)
        // summary 不受搜索影响
        assertEquals(40L, page.summary.total)
    }

    @Test
    fun `state filter validates against storage state enum`() {
        val rows = fixture40Rows()
        val stored = service.assemblePage(1, rows, emptyList(), 0, 100, null, null, null, "STORED")
        assertEquals(1L, stored.total)
        val metadataOnly = service.assemblePage(1, rows, emptyList(), 0, 100, null, null, null, "METADATA_ONLY")
        assertEquals(39L, metadataOnly.total)
        // 枚举校验在入口（listMaterials），不在装配层
        assertThrows(IllegalArgumentException::class.java) {
            service.listMaterials(1, 0, 10, null, null, null, "BOGUS_STATE")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.listMaterials(1, 0, 10, null, "MAIL_RECORD", null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.listMaterials(1, 0, 10, null, "NOT_A_SOURCE", 1, null)
        }
    }

    @Test
    fun `legacy attachment ambiguity refuses source and never attributes another account's file`() {
        // 旧 record 无账号信息（无法按账号甄别）+ 两个同 expert/同 messageId 的
        // processing 候选（跨账号/重复投递）→ 拒绝猜测，来源置空并标 SOURCE_AMBIGUOUS。
        val rows = listOf(
            legacyRow(
                documentId = 1, attachmentId = 1,
                storagePath = writeFile("cv.pdf", tempDir.resolve("amb")).toString(),
                mrReceivedAt = LocalDateTime.of(2026, 8, 1, 9, 0),
                mrMessageId = "same-message", mailRecordId = 100,
                mrAccountCode = null
            )
        )
        val candidates = listOf(
            ProcessingCandidateRow(200, "same-message", "account-a"),
            ProcessingCandidateRow(201, "same-message", "account-b")
        )
        val page = service.assemblePage(1, rows, candidates, 0, 10, null, null, null, null)
        val item = page.items.single()
        assertNull(item.source)
        assertEquals("SOURCE_AMBIGUOUS", item.error?.code)
        // 文件本体仍按已确认 expert_document 归属展示（可下载）
        assertTrue(item.canDownload)
        // 来源筛选（任意一个账号的 processing）都不得把该件归属到某账号
        val byA = service.assemblePage(1, rows, candidates, 0, 10, null, "INBOUND_PROCESSING", 200, null)
        val byB = service.assemblePage(1, rows, candidates, 0, 10, null, "INBOUND_PROCESSING", 201, null)
        assertEquals(0L, byA.total)
        assertEquals(0L, byB.total)
    }

    @Test
    fun `two accounts with the same message id never cross-wire an expert attachment`() {
        // 旧 record 属于 account-a；account-b 也有同 messageId 的 processing。
        // 严格同账号匹配只归属 account-a 的唯一候选，绝不把 account-b 的行当来源。
        val rows = listOf(
            legacyRow(
                documentId = 1, attachmentId = 1,
                storagePath = writeFile("cv.pdf", tempDir.resolve("cross")).toString(),
                mrReceivedAt = LocalDateTime.of(2026, 8, 1, 9, 0),
                mrMessageId = "dup-msg", mailRecordId = 100,
                mrAccountCode = "account-a"
            )
        )
        val candidates = listOf(
            ProcessingCandidateRow(200, "dup-msg", "account-a"),
            ProcessingCandidateRow(201, "dup-msg", "account-b")
        )
        Mockito.`when`(inboundMailProcessingRepository.findById(200L))
            .thenReturn(Optional.of(processing(200, contactId = 1, accountCode = "account-a", messageId = "dup-msg")))
        val page = service.assemblePage(1, rows, candidates, 0, 10, null, null, null, null)
        val item = page.items.single()
        assertEquals("INBOUND_PROCESSING", item.source?.type)
        assertEquals(200L, item.source?.id)
        assertEquals("account-a", item.source?.accountCode)
        assertEquals("Inbound 200", item.source?.subject)
        val byB = service.assemblePage(1, rows, candidates, 0, 10, null, "INBOUND_PROCESSING", 201, null)
        assertEquals(0L, byB.total)
    }

    @Test
    fun `legacy attachment with exactly one processing candidate is attributed to that processing`() {
        val dir = tempDir.resolve("uniq")
        val rows = listOf(
            legacyRow(
                documentId = 1, attachmentId = 1,
                storagePath = writeFile("cv.pdf", dir).toString(),
                mrReceivedAt = LocalDateTime.of(2026, 8, 1, 9, 0),
                mrMessageId = "msg-100", mailRecordId = 100
            )
        )
        val candidates = listOf(ProcessingCandidateRow(200, "msg-100", "account-a"))
        val page = service.assemblePage(1, rows, candidates, 0, 10, null, null, null, null)
        val item = page.items.single()
        assertEquals("INBOUND_PROCESSING", item.source?.type)
        assertEquals(200L, item.source?.id)
        val filtered = service.assemblePage(1, rows, candidates, 0, 10, null, "INBOUND_PROCESSING", 200, null)
        assertEquals(1L, filtered.total)
    }

    @Test
    fun `mail_record source pair filter works for legacy rows without processing candidate`() {
        val dir = tempDir.resolve("legacy-only")
        val rows = listOf(
            legacyRow(
                documentId = 1, attachmentId = 1,
                storagePath = writeFile("cv.pdf", dir).toString(),
                mrReceivedAt = LocalDateTime.of(2026, 8, 1, 9, 0),
                mrMessageId = null, mailRecordId = 100
            )
        )
        val page = service.assemblePage(1, rows, emptyList(), 0, 10, null, "MAIL_RECORD", 100, null)
        assertEquals(1L, page.total)
        assertEquals("MAIL_RECORD", page.items.single().source?.type)
        assertEquals(100L, page.items.single().source?.id)
    }

    @Test
    fun `source and sourceId must be provided together`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.listMaterials(1, 0, 10, null, "MAIL_RECORD", null, null)
        }
    }

    @Test
    fun `page and size bounds are validated`() {
        assertThrows(IllegalArgumentException::class.java) { service.listMaterials(1, -1, 10, null, null, null, null) }
        assertThrows(IllegalArgumentException::class.java) { service.listMaterials(1, 0, 0, null, null, null, null) }
        assertThrows(IllegalArgumentException::class.java) { service.listMaterials(1, 0, 101, null, null, null, null) }
    }

    @Test
    fun `unknown actual size is null and zero-byte real file stays zero`() {
        val zeroFile = writeFile("empty.pdf", tempDir.resolve("zero"), content = "")
        val rows = listOf(
            MaterialRow(
                documentId = 1, documentType = "CV", documentStatus = "PENDING_REVIEW",
                attachmentId = 1, fileName = "empty.pdf", contentType = "application/pdf",
                fileSize = 0L, storagePath = zeroFile.toString(),
                attachmentCreatedAt = LocalDateTime.of(2026, 9, 1, 9, 0),
                mailRecordId = 100, attachmentInboundProcessingId = null,
                transferId = null, transferState = null, transferInboundProcessingId = null,
                bytesDownloaded = 0, encodedSize = null, errorCode = null, errorMessage = null,
                pSubject = null, pReceivedAt = null, pAccountCode = null, pExpertContactId = null,
                mrSubject = null, mrReceivedAt = null, mrAccountCode = null,
                mrDirection = "INBOUND", mrMessageId = null, mrExpertContactId = 1
            )
        )
        val page = service.assemblePage(1, rows, emptyList(), 0, 10, null, null, null, null)
        assertEquals(0L, page.items.single().actualSize)
        assertEquals("STORED", page.items.single().storageState)
        assertTrue(page.items.single().canDownload)
    }

    // ------------------------------------------------------------------
    // GET 零副作用（I-1）
    // ------------------------------------------------------------------

    @Test
    fun `GET list never touches transfer writes or the queue`() {
        Mockito.`when`(jdbc.query(
            Mockito.anyString(),
            Mockito.any(MapSqlParameterSource::class.java),
            Mockito.any(org.springframework.jdbc.core.RowMapper::class.java)
        )).thenReturn(emptyList())
        Mockito.`when`(inboundMailProcessingRepository.findAllByExpertContactId(1L)).thenReturn(emptyList())

        val page = service.listMaterials(1, 0, 10, null, null, null, null)

        assertEquals(0L, page.total)
        assertEquals(0L, page.summary.total)
        Mockito.verify(attachmentTransferService, Mockito.never())
            .enqueueMaterial(Mockito.anyLong(), Mockito.anyList<Long>() ?: emptyList(), Mockito.anyString())
        Mockito.verify(mailAttachmentTransferRepository, Mockito.never())
            .markRequested(
                Mockito.anyLong(),
                Mockito.anyString(),
                Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
            )
        Mockito.verifyNoInteractions(expertDocumentRepository)
        Mockito.verifyNoInteractions(mailAttachmentRepository)
    }

    // ------------------------------------------------------------------
    // POST transfers（I-3）
    // ------------------------------------------------------------------

    @Test
    fun `requestTransfers enqueues only owned attachments and maps the batch`() {
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(document(1, 1, 1))
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(2L)).thenReturn(document(2, 1, 2))
        Mockito.`when`(mailAttachmentTransferRepository.countQueued()).thenReturn(0L)
        Mockito.`when`(attachmentTransferService.enqueueMaterial(1L, listOf(1L, 2L), "admin"))
            .thenReturn(
                AttachmentTransferService.EnqueueBatchResult(
                    items = listOf(
                        AttachmentTransferService.EnqueueItemResult(1L, 10L, MailAttachmentTransfer.STATE_QUEUED),
                        AttachmentTransferService.EnqueueItemResult(2L, null, null, alreadyReady = true)
                    ),
                    acceptedCount = 1,
                    alreadyReadyCount = 1,
                    queueFull = false
                )
            )

        val result = service.requestTransfers(1, listOf(1L, 2L, 2L), "admin")

        assertEquals(1, result.acceptedCount)
        assertEquals(1, result.alreadyReadyCount)
        assertEquals(listOf(1L, 2L), result.items.map { it.attachmentId })
        assertEquals(MailAttachmentTransfer.STATE_QUEUED, result.items[0].state)
        Mockito.verify(attachmentTransferService).enqueueMaterial(1L, listOf(1L, 2L), "admin")
    }

    @Test
    fun `one foreign attachment id rejects the whole batch before enqueue`() {
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(document(1, 1, 1))
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(99L)).thenReturn(document(99, 2, 99))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.requestTransfers(1, listOf(1L, 99L), "admin")
        }
        assertTrue(ex.message!!.contains("are not materials of expert contact 1"))
        Mockito.verify(attachmentTransferService, Mockito.never())
            .enqueueMaterial(Mockito.anyLong(), Mockito.anyList(), Mockito.anyString())
    }

    @Test
    fun `requestTransfers enforces 1 to 500 distinct ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.requestTransfers(1, emptyList(), "admin")
        }
        val tooMany = (1L..501L).toList()
        assertThrows(IllegalArgumentException::class.java) {
            service.requestTransfers(1, tooMany, "admin")
        }
    }

    @Test
    fun `requestTransfers throws queue-full exception when queue is at capacity`() {
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(document(1, 1, 1))
        Mockito.`when`(mailAttachmentTransferRepository.countQueued())
            .thenReturn(AttachmentTransferService.MAX_QUEUED_ROWS)

        assertThrows(AttachmentTransferQueueFullException::class.java) {
            service.requestTransfers(1, listOf(1L), "admin")
        }
        Mockito.verify(attachmentTransferService, Mockito.never())
            .enqueueMaterial(Mockito.anyLong(), Mockito.anyList(), Mockito.anyString())
    }

    // ------------------------------------------------------------------
    // reconcile（I-1：显式历史修复 dryRun/apply 幂等）
    // ------------------------------------------------------------------

    @Test
    fun `reconcile dryRun returns candidates without writing anything`() {
        val candidates = listOf(10L, 11L)
        Mockito.`when`(jdbc.query(
            Mockito.contains("NOT EXISTS"),
            Mockito.any(MapSqlParameterSource::class.java),
            Mockito.any(org.springframework.jdbc.core.RowMapper::class.java)
        )).thenAnswer { invocation ->
            val mapper = invocation.getArgument<org.springframework.jdbc.core.RowMapper<Any>>(2)
            listOf(10L, 11L).map { id ->
                val rs = mockResultSet(id, "cv-$id.pdf")
                mapper.mapRow(rs, 0)
            }
        }

        val result = service.reconcileDryRun(1)

        assertEquals(listOf(10L, 11L), result.candidateAttachmentIds)
        assertEquals(0, result.createdCount)
        Mockito.verify(expertDocumentRepository, Mockito.never())
            .save(Mockito.any(ExpertDocument::class.java))
        Mockito.verify(expertDocumentRepository, Mockito.never())
            .findFirstByMailAttachmentId(Mockito.anyLong())
    }

    private fun mockResultSet(id: Long, fileName: String): java.sql.ResultSet {
        val rs = Mockito.mock(java.sql.ResultSet::class.java)
        Mockito.`when`(rs.getLong("id")).thenReturn(id)
        Mockito.`when`(rs.getString("file_name")).thenReturn(fileName)
        return rs
    }

    @Test
    fun `reconcile apply creates missing documents once and is idempotent`() {
        Mockito.`when`(jdbc.query(
            Mockito.contains("FOR UPDATE"),
            Mockito.any(MapSqlParameterSource::class.java),
            Mockito.any(org.springframework.jdbc.core.RowMapper::class.java)
        )).thenAnswer { invocation ->
            val mapper = invocation.getArgument<org.springframework.jdbc.core.RowMapper<Any>>(2)
            listOf(10L, 11L).map { id ->
                mapper.mapRow(mockResultSet(id, "cv-$id.pdf"), 0)
            }
        }
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(Mockito.anyLong())).thenReturn(null)

        val first = service.reconcileApply(1)

        assertEquals(2, first.createdCount)
        assertEquals(listOf(10L, 11L), first.candidateAttachmentIds)
        val captor = ArgumentCaptor.forClass(ExpertDocument::class.java)
        Mockito.verify(expertDocumentRepository, Mockito.times(2)).save(captor.capture())
        captor.allValues.forEach { saved ->
            assertEquals(1L, saved.expertContactId)
            assertEquals(DocumentStatus.PENDING_REVIEW.name, saved.documentStatus)
            assertEquals(ExpertDocumentType.CV.name, saved.documentType)
            assertTrue(saved.mailAttachmentId == 10L || saved.mailAttachmentId == 11L)
        }

        // 第二次 apply：FOR UPDATE 查询已看不到候选 → 幂等，不重复建 doc
        Mockito.reset(jdbc)
        Mockito.`when`(jdbc.query(
            Mockito.contains("FOR UPDATE"),
            Mockito.any(MapSqlParameterSource::class.java),
            Mockito.any(org.springframework.jdbc.core.RowMapper::class.java)
        )).thenReturn(emptyList())
        val second = service.reconcileApply(1)
        assertEquals(0, second.createdCount)
        assertTrue(second.candidateAttachmentIds.isEmpty())
    }

    @Test
    fun `reconcile apply skips attachments that already have a document`() {
        Mockito.`when`(jdbc.query(
            Mockito.contains("FOR UPDATE"),
            Mockito.any(MapSqlParameterSource::class.java),
            Mockito.any(org.springframework.jdbc.core.RowMapper::class.java)
        )).thenAnswer { invocation ->
            val mapper = invocation.getArgument<org.springframework.jdbc.core.RowMapper<Any>>(2)
            listOf(10L).map { id -> mapper.mapRow(mockResultSet(id, "cv-10.pdf"), 0) }
        }
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(10L)).thenReturn(document(1, 1, 10))

        val result = service.reconcileApply(1)

        assertEquals(0, result.createdCount)
        Mockito.verify(expertDocumentRepository, Mockito.never()).save(Mockito.any(ExpertDocument::class.java))
    }

    // ------------------------------------------------------------------
    // resolveMessageAttachments（I-2：07 MailboxService 委托同一语义）
    // ------------------------------------------------------------------

    @Test
    fun `resolveMessageAttachments returns bridged attachments of a processing message`() {
        val att = attachment(5, mailRecordId = 100)
        Mockito.`when`(mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(200L))
            .thenReturn(emptyList())
        Mockito.`when`(jdbc.queryForList(
            Mockito.contains("mail_attachment_transfer"),
            Mockito.any(MapSqlParameterSource::class.java),
            Mockito.eq(Long::class.java)
        )).thenReturn(listOf(5L))
        Mockito.`when`(mailAttachmentRepository.findById(5L)).thenReturn(Optional.of(att))

        val result = service.resolveMessageAttachments("INBOUND_PROCESSING", 200)

        assertEquals(listOf(5L), result.map { it.id })
    }

    @Test
    fun `resolveMessageAttachments refuses ambiguous legacy attribution across accounts`() {
        val processing = processing(200, contactId = 1, accountCode = "account-a", messageId = "dup-msg")
        Mockito.`when`(mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(200L))
            .thenReturn(emptyList())
        Mockito.`when`(jdbc.queryForList(
            Mockito.contains("mail_attachment_transfer"),
            Mockito.any(MapSqlParameterSource::class.java),
            Mockito.eq(Long::class.java)
        )).thenReturn(emptyList())
        Mockito.`when`(inboundMailProcessingRepository.findById(200L)).thenReturn(Optional.of(processing))
        Mockito.`when`(jdbc.queryForList(
            Mockito.contains("mail_record"),
            Mockito.any(MapSqlParameterSource::class.java),
            Mockito.eq(Long::class.java)
        )).thenReturn(listOf(300L, 301L))

        val result = service.resolveMessageAttachments("INBOUND_PROCESSING", 200)

        assertTrue(result.isEmpty())
        Mockito.verify(mailAttachmentRepository, Mockito.never())
            .findAllByMailRecordIdOrderByCreatedAtAsc(Mockito.anyLong())
    }

    @Test
    fun `resolveMessageAttachments never treats sourceInboundId as a processing id`() {
        // MAIL_RECORD source 只按 record 直接附件返回，不通过 source_inbound_id 查 processing。
        val att = attachment(5, mailRecordId = 300)
        Mockito.`when`(mailRecordRepository.findByIdOrNull(300L)).thenReturn(mailRecord(300, 1))
        Mockito.`when`(mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(300L))
            .thenReturn(listOf(att))

        val result = service.resolveMessageAttachments("MAIL_RECORD", 300)

        assertEquals(listOf(5L), result.map { it.id })
    }
}

/**
 * 409/429 必须穿透「真实 advice 链」：GlobalExceptionHandler 的 catch(Exception)
 * 会把裸异常变 500，本测试同时挂 scoped advice + 全局 advice（@WebMvcTest 自动
 * 装载全部 @ControllerAdvice）证明 409 MATERIAL_NOT_READY / 429 确实生效。
 */
@org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest(
    controllers = [
        ExpertMaterialController::class,
        ExpertDocumentBrowseController::class,
        ExpertDocumentAnalysisController::class,
        MailboxAttachmentController::class
    ]
)
class ExpertMaterialControllerHttpTest {

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var mockMvc: org.springframework.test.web.servlet.MockMvc

    @org.springframework.boot.test.mock.mockito.MockBean
    private lateinit var materialService: ExpertMaterialService

    @org.springframework.boot.test.mock.mockito.MockBean
    private lateinit var browseService: ExpertDocumentBrowseService

    @org.springframework.boot.test.mock.mockito.MockBean
    private lateinit var analysisService: ExpertDocumentAnalysisService

    @org.springframework.boot.test.mock.mockito.MockBean
    private lateinit var mailboxAttachmentService: MailboxAttachmentService

    private val pageFixture = ExpertMaterialPage(
        items = listOf(
            ExpertMaterialItem(
                attachmentId = 7L,
                documentId = 3L,
                source = MaterialSource(
                    type = "INBOUND_PROCESSING",
                    id = 200L,
                    subject = "Re: materials",
                    receivedAt = LocalDateTime.of(2026, 9, 1, 9, 0),
                    accountCode = "account-a"
                ),
                fileName = "cv.pdf",
                contentType = "application/pdf",
                documentType = "CV",
                documentStatus = DocumentStatus.PENDING_REVIEW.name,
                actualSize = null,
                encodedSize = 1024L,
                storageState = MailAttachmentTransfer.STATE_METADATA_ONLY,
                bytesDownloaded = 0,
                error = null,
                canFetch = true,
                canDownload = false,
                canPreview = false,
                analysisSupported = true,
                canAnalyze = false,
                downloadUrl = null,
                previewUrl = null
            )
        ),
        total = 1,
        page = 0,
        size = 10,
        summary = ExpertMaterialSummary(
            total = 1,
            stored = 0,
            metadataOnly = 1,
            active = 0,
            failed = 0,
            sourceUnavailable = 0,
            defaultAnalysisAttachmentIds = listOf(7L)
        )
    )

    @org.junit.jupiter.api.Test
    fun `GET materials returns the fixed item fields and never leaks storagePath or workerToken`() {
        Mockito.`when`(materialService.listMaterials(
            Mockito.eq(1L),
            Mockito.anyInt(),
            Mockito.anyInt(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull()
        )).thenReturn(pageFixture)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/expert-contacts/1/materials")
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].attachmentId").value(7))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].documentId").value(3))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].source.type").value("INBOUND_PROCESSING"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].source.id").value(200))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].fileName").value("cv.pdf"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].contentType").value("application/pdf"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].documentType").value("CV"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].documentStatus").value("PENDING_REVIEW"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].storageState").value("METADATA_ONLY"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].bytesDownloaded").value(0))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].canFetch").value(true))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].analysisSupported").value(true))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].canAnalyze").value(false))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.summary.metadataOnly").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.summary.defaultAnalysisAttachmentIds[0]").value(7))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].storagePath").doesNotExist())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].workerToken").doesNotExist())
    }

    @org.junit.jupiter.api.Test
    fun `old download returns 409 MATERIAL_NOT_READY through the real advice chain`() {
        // 若 GlobalExceptionHandler 抢先，这里会变成 500 —— 409 断言证明 scoped
        // advice 以最高优先级生效。
        Mockito.`when`(browseService.resolveForDownload(1L, 7L))
            .thenThrow(MaterialNotReadyException(7L, "METADATA_ONLY", "Attachment 7 has no local file"))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/expert-contacts/1/attachments/7/download")
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("MATERIAL_NOT_READY"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.attachmentId").value(7))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.state").value("METADATA_ONLY"))
    }

    @org.junit.jupiter.api.Test
    fun `POST transfers accepts only owned attachments and returns 202 batch`() {
        Mockito.`when`(materialService.requestTransfers(1L, listOf(1L, 2L), "admin"))
            .thenReturn(
                MaterialTransferBatchResponse(
                    items = listOf(
                        MaterialTransferItem(1L, 10L, MailAttachmentTransfer.STATE_QUEUED),
                        MaterialTransferItem(2L, null, null)
                    ),
                    acceptedCount = 1,
                    alreadyReadyCount = 1
                )
            )

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/expert-contacts/1/materials/transfers")
                .sessionAttr("AUTH_USERNAME", "admin")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"attachmentIds":[1,2]}""")
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.acceptedCount").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.alreadyReadyCount").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].attachmentId").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].state").value("QUEUED"))
    }

    @org.junit.jupiter.api.Test
    fun `POST transfers rejects a foreign expert attachment batch with 400`() {
        Mockito.`when`(materialService.requestTransfers(1L, listOf(1L, 99L), "admin"))
            .thenThrow(IllegalArgumentException("Attachments [99] are not materials of expert contact 1"))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/expert-contacts/1/materials/transfers")
                .sessionAttr("AUTH_USERNAME", "admin")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"attachmentIds":[1,99]}""")
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("BAD_REQUEST"))
    }

    @org.junit.jupiter.api.Test
    fun `POST transfers without session login is rejected`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/expert-contacts/1/materials/transfers")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"attachmentIds":[1]}""")
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest)
    }

    @org.junit.jupiter.api.Test
    fun `POST transfers maps queue capacity exhaustion to 429`() {
        Mockito.`when`(materialService.requestTransfers(1L, listOf(1L), "admin"))
            .thenThrow(AttachmentTransferQueueFullException("queue is full"))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/expert-contacts/1/materials/transfers")
                .sessionAttr("AUTH_USERNAME", "admin")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"attachmentIds":[1]}""")
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isTooManyRequests)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("TRANSFER_QUEUE_FULL"))
    }

    @org.junit.jupiter.api.Test
    fun `POST reconcile dryRun returns candidates and apply delegates to the service`() {
        Mockito.`when`(materialService.reconcileDryRun(1L))
            .thenReturn(ReconcileResult(listOf(10L, 11L), 0))
        Mockito.`when`(materialService.reconcileApply(1L))
            .thenReturn(ReconcileResult(listOf(10L, 11L), 2))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/expert-contacts/1/materials/reconcile")
                .sessionAttr("AUTH_USERNAME", "admin")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"dryRun":true}""")
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.candidateAttachmentIds[0]").value(10))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.createdCount").value(0))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/expert-contacts/1/materials/reconcile")
                .sessionAttr("AUTH_USERNAME", "admin")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"dryRun":false}""")
        )
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.createdCount").value(2))
        Mockito.verify(materialService).reconcileApply(1L)
    }
}
