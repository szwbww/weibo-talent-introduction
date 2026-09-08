package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.mail.domain.MailAttachment
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
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

class DocumentTextExtractorTest {
    @TempDir
    lateinit var tempDir: Path

    private val materialService = Mockito.mock(ExpertMaterialService::class.java)
    private lateinit var extractor: DocumentTextExtractor

    @BeforeEach
    fun setUp() {
        extractor = DocumentTextExtractor(materialService)
    }

    @AfterEach
    fun tearDown() {
        Mockito.reset(materialService)
    }

    @Test
    fun `extract reads plain text files`() {
        val contactId = 10L
        val attachmentId = 101L
        val storageDir = tempDir.resolve("mail").resolve("101")
        Files.createDirectories(storageDir)
        val filePath = storageDir.resolve("notes.txt")
        Files.writeString(filePath, "Expert name: Alice Chen")

        stubReadyAttachment(contactId, attachmentId, filePath, "notes.txt", "text/plain")

        val result = extractor.extract(contactId, listOf(attachmentId))

        assertEquals("Expert name: Alice Chen", result[attachmentId]?.text)
        assertTrue(result[attachmentId]?.supported == true)
    }

    @Test
    fun `extract reads pdf files`() {
        val contactId = 10L
        val attachmentId = 102L
        val storageDir = tempDir.resolve("mail").resolve("102")
        Files.createDirectories(storageDir)
        val filePath = storageDir.resolve("cv.pdf")
        createPdf(filePath, "PhD from Tsinghua University")

        stubReadyAttachment(contactId, attachmentId, filePath, "cv.pdf", "application/pdf")

        val result = extractor.extract(contactId, listOf(attachmentId))

        assertTrue(result[attachmentId]?.text?.contains("PhD from Tsinghua University") == true)
        assertTrue(result[attachmentId]?.supported == true)
    }

    @Test
    fun `extract marks unsupported file types`() {
        val contactId = 10L
        val attachmentId = 103L
        val storageDir = tempDir.resolve("mail").resolve("103")
        Files.createDirectories(storageDir)
        val filePath = storageDir.resolve("photo.png")
        Files.write(filePath, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

        stubReadyAttachment(contactId, attachmentId, filePath, "photo.png", "image/png")

        val result = extractor.extract(contactId, listOf(attachmentId))

        assertEquals("", result[attachmentId]?.text)
        assertFalse(result[attachmentId]?.supported == true)
        assertTrue(result[attachmentId]?.unsupportedReason?.contains("不支持") == true)
    }

    @Test
    fun `validate rejects attachment from another contact via the shared resolver`() {
        val attachmentId = 104L
        Mockito.`when`(materialService.resolveReadyFile(10L, attachmentId))
            .thenThrow(IllegalArgumentException("Document for attachment $attachmentId does not belong to expert contact 10"))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            extractor.validateAttachmentBelongsToContact(10L, attachmentId)
        }
        assertTrue(ex.message!!.contains("does not belong"))
    }

    @Test
    fun `extract rejects attachment with no storage path with MATERIAL_NOT_READY before reading any file`() {
        val contactId = 10L
        val attachmentId = 105L
        stubNotReady(contactId, attachmentId)

        val ex = assertThrows(MaterialNotReadyException::class.java) {
            extractor.extract(contactId, listOf(attachmentId))
        }
        assertEquals(attachmentId, ex.attachmentId)
        assertEquals("METADATA_ONLY", ex.state)
        assertTrue(ex.message!!.contains("no local file"))
    }

    @Test
    fun `extract validates all selected attachments are ready before reading any content`() {
        val contactId = 10L
        val readyId = 106L
        val notReadyId = 107L
        val storageDir = tempDir.resolve("mail").resolve("106")
        Files.createDirectories(storageDir)
        val readyFile = storageDir.resolve("first.txt")
        Files.writeString(readyFile, "should never be read when a later file is not ready")

        stubReadyAttachment(contactId, readyId, readyFile, "first.txt", "text/plain")
        stubNotReady(contactId, notReadyId)

        assertThrows(MaterialNotReadyException::class.java) {
            extractor.extract(contactId, listOf(readyId, notReadyId))
        }
        // 阶段 1 就绪校验覆盖全部所选：两次 resolveReadyFile 都发生，读阶段未执行。
        Mockito.verify(materialService).resolveReadyFile(contactId, readyId)
        Mockito.verify(materialService).resolveReadyFile(contactId, notReadyId)
    }

    private fun stubReadyAttachment(
        contactId: Long,
        attachmentId: Long,
        filePath: Path,
        fileName: String,
        contentType: String
    ) {
        val attachment = MailAttachment(
            id = attachmentId,
            mailRecordId = 500L,
            fileName = fileName,
            contentType = contentType,
            fileSize = Files.size(filePath),
            storagePath = filePath.toString(),
            createdAt = LocalDateTime.now()
        )
        Mockito.`when`(materialService.resolveReadyFile(contactId, attachmentId))
            .thenReturn(ReadyFile(attachment, filePath.toRealPath()))
    }

    private fun stubNotReady(contactId: Long, attachmentId: Long) {
        Mockito.`when`(materialService.resolveReadyFile(contactId, attachmentId))
            .thenThrow(
                MaterialNotReadyException(
                    attachmentId,
                    "METADATA_ONLY",
                    "Attachment $attachmentId has no local file (storage_path is null)"
                )
            )
    }

    private fun createPdf(path: Path, text: String) {
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font.HELVETICA, 12f)
                content.newLineAtOffset(50f, 700f)
                content.showText(text)
                content.endText()
            }
            document.save(path.toFile())
        }
    }
}
