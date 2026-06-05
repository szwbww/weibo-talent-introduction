package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.DocumentStatus
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Optional

class ExpertDocumentBrowseServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var properties: MailAttachmentStorageProperties
    private val expertDocumentRepository = Mockito.mock(ExpertDocumentRepository::class.java)
    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private lateinit var service: ExpertDocumentBrowseService

    private lateinit var storageDir: Path

    @BeforeEach
    fun setUp() {
        properties = MailAttachmentStorageProperties(basePath = tempDir.toString())
        storageDir = tempDir.resolve("1").resolve("100")
        Files.createDirectories(storageDir)
        service = ExpertDocumentBrowseService(
            properties,
            expertDocumentRepository,
            mailAttachmentRepository,
            mailRecordRepository
        )
    }

    @AfterEach
    fun tearDown() {
        Mockito.reset(expertDocumentRepository, mailAttachmentRepository, mailRecordRepository)
    }

    private fun createTestFile(name: String, content: String = "test", dir: Path? = null): Path {
        val target = (dir ?: storageDir).resolve(name)
        Files.write(target, content.toByteArray())
        return target
    }

    private fun attachment(id: Long, mailRecordId: Long, fileName: String, contentType: String? = null, path: Path? = null): MailAttachment =
        MailAttachment(
            id = id,
            mailRecordId = mailRecordId,
            fileName = fileName,
            contentType = contentType,
            fileSize = path?.toFile()?.length() ?: 100,
            storagePath = (path ?: createTestFile(fileName)).toString(),
            createdAt = LocalDateTime.now()
        )

    private fun document(id: Long, contactId: Long, attachmentId: Long, docType: String = "CV"): ExpertDocument =
        ExpertDocument(
            id = id,
            expertContactId = contactId,
            mailAttachmentId = attachmentId,
            documentType = docType,
            documentStatus = DocumentStatus.PENDING_REVIEW.name,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

    private fun mailRecord(id: Long, contactId: Long): MailRecord =
        MailRecord(
            id = id,
            expertContactId = contactId,
            direction = "INBOUND",
            mailType = "REPLY",
            messageId = "msg-$id",
            inReplyTo = null,
            subject = "Subject",
            body = "Body",
            matchedQaRuleId = null,
            sendStatus = null,
            receivedAt = LocalDateTime.now(),
            sentAt = null
        )

    @Test
    fun `lists documents for expert contact`() {
        val contactId = 1L
        val att = attachment(1, 100, "cv.pdf", "application/pdf")
        val doc = document(1, contactId, 1, "CV")

        Mockito.`when`(expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
            .thenReturn(listOf(doc))
        Mockito.`when`(mailAttachmentRepository.findById(1L))
            .thenReturn(Optional.of(att))
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L))
            .thenReturn(mailRecord(100, contactId))

        val result = service.listDocuments(contactId)

        assertEquals(1, result.size)
        assertEquals(1L, result[0].documentId)
        assertEquals(1L, result[0].attachmentId)
        assertEquals(100L, result[0].mailRecordId)
        assertEquals("cv.pdf", result[0].fileName)
        assertEquals("application/pdf", result[0].contentType)
        assertTrue(result[0].previewable)
        assertEquals("CV", result[0].documentType)
        assertTrue(result[0].downloadUrl.contains("/api/expert-contacts/$contactId/attachments/1/download"))
        assertTrue(result[0].previewUrl.contains("/api/expert-contacts/$contactId/attachments/1/preview"))
        assertTrue(result[0].downloadUrl.contains("/api/expert-contacts/$contactId/attachments/1/download"))
        assertTrue(result[0].previewUrl.contains("/api/expert-contacts/$contactId/attachments/1/preview"))
    }

    @Test
    fun `pdf is previewable`() {
        val contactId = 1L
        val f = createTestFile("doc.pdf")
        val att = attachment(1, 100, "doc.pdf", null, f)
        val doc = document(1, contactId, 1)

        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, contactId))

        val result = service.resolveForPreview(contactId, 1)

        assertEquals("application/pdf", result.contentType)
    }

    @Test
    fun `image is previewable`() {
        val contactId = 1L
        val f = createTestFile("photo.png")
        val att = attachment(1, 100, "photo.png", null, f)
        val doc = document(1, contactId, 1)

        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, contactId))

        val result = service.resolveForPreview(contactId, 1)

        assertEquals("image/png", result.contentType)
    }

    @Test
    fun `text file is previewable`() {
        val contactId = 1L
        val f = createTestFile("notes.txt")
        val att = attachment(1, 100, "notes.txt", null, f)
        val doc = document(1, contactId, 1)

        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, contactId))

        val result = service.resolveForPreview(contactId, 1)

        assertEquals("text/plain", result.contentType)
    }

    @Test
    fun `office file is not previewable`() {
        val contactId = 1L
        val f = createTestFile("data.xlsx")
        val att = attachment(1, 100, "data.xlsx", null, f)
        val doc = document(1, contactId, 1)

        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, contactId))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.resolveForPreview(contactId, 1)
        }
        assertTrue(ex.message!!.contains("not previewable"))
    }

    @Test
    fun `document not belonging to contact is rejected`() {
        val f = createTestFile("cv.pdf")
        val att = attachment(1, 100, "cv.pdf", "application/pdf", f)
        val doc = document(1, 999, 1)

        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, 999))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.resolveForDownload(1, 1)
        }
        assertTrue(ex.message!!.contains("does not belong"))
    }

    @Test
    fun `mail record not belonging to contact is rejected`() {
        val f = createTestFile("cv.pdf")
        val att = attachment(1, 100, "cv.pdf", "application/pdf", f)
        val doc = document(1, 1, 1)

        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, 999))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.resolveForDownload(1, 1)
        }
        assertTrue(ex.message!!.contains("does not belong"))
    }

    @Test
    fun `path outside basePath is rejected`() {
        val outsideDir = Files.createTempDirectory("outside")
        val outsideFile = outsideDir.resolve("bad.txt")
        Files.write(outsideFile, "test".toByteArray())

        val att = attachment(1, 100, "bad.txt", "text/plain", outsideFile)
        val doc = document(1, 1, 1)

        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, 1))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.resolveForDownload(1, 1)
        }
        assertTrue(ex.message!!.contains("outside configured base path"))
    }

    @Test
    fun `content type inferred from extension when null`() {
        val contactId = 1L
        val f = createTestFile("doc.pdf")
        val att = attachment(1, 100, "doc.pdf", null, f)
        val doc = document(1, contactId, 1)

        Mockito.`when`(expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
            .thenReturn(listOf(doc))
        Mockito.`when`(mailAttachmentRepository.findById(1L))
            .thenReturn(Optional.of(att))
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L))
            .thenReturn(mailRecord(100, contactId))

        val result = service.listDocuments(contactId)

        assertEquals("application/pdf", result[0].contentType)
        assertTrue(result[0].previewable)
    }

    @Test
    fun `symlink pointing outside basePath is rejected`() {
        val outsideDir = Files.createTempDirectory("outside-link")
        val outsideFile = outsideDir.resolve("target.txt")
        Files.write(outsideFile, "secret".toByteArray())

        val symlinkPath = storageDir.resolve("link_to_outside.txt")
        Files.createSymbolicLink(symlinkPath, outsideFile)

        val att = attachment(1, 100, "link_to_outside.txt", "text/plain", symlinkPath)
        val doc = document(1, 1, 1)

        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(doc)
        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(Optional.of(att))
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L)).thenReturn(mailRecord(100, 1))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.resolveForDownload(1, 1)
        }
        assertTrue(ex.message!!.contains("outside configured base path"))
    }

    @Test
    fun `listDocuments rejects document whose mail record belongs to different contact`() {
        val contactId = 1L
        val att = attachment(1, 100, "cv.pdf", "application/pdf")
        val doc = document(1, contactId, 1, "CV")

        Mockito.`when`(expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
            .thenReturn(listOf(doc))
        Mockito.`when`(mailAttachmentRepository.findById(1L))
            .thenReturn(Optional.of(att))
        Mockito.`when`(mailRecordRepository.findByIdOrNull(100L))
            .thenReturn(mailRecord(100, 999))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.listDocuments(contactId)
        }
        assertTrue(ex.message!!.contains("does not belong"))
    }
}
