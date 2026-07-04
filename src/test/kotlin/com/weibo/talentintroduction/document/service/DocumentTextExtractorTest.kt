package com.weibo.talentintroduction.document.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.DocumentStatus
import com.weibo.talentintroduction.document.domain.ExpertAnalysisResult
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.repository.ExpertAnalysisResultRepository
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.llm.service.LlmChatMessage
import com.weibo.talentintroduction.llm.service.LlmDraftClient
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
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
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.client.ResourceAccessException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Optional

class DocumentTextExtractorTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var properties: MailAttachmentStorageProperties
    private val expertDocumentRepository = Mockito.mock(ExpertDocumentRepository::class.java)
    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private lateinit var extractor: DocumentTextExtractor

    @BeforeEach
    fun setUp() {
        properties = MailAttachmentStorageProperties(basePath = tempDir.toString())
        extractor = DocumentTextExtractor(
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

    @Test
    fun `extract reads plain text files`() {
        val contactId = 10L
        val attachmentId = 101L
        val storageDir = tempDir.resolve("mail").resolve("101")
        Files.createDirectories(storageDir)
        val filePath = storageDir.resolve("notes.txt")
        Files.writeString(filePath, "Expert name: Alice Chen")

        stubAttachmentOwnership(contactId, attachmentId, filePath, "notes.txt", "text/plain")

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

        stubAttachmentOwnership(contactId, attachmentId, filePath, "cv.pdf", "application/pdf")

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

        stubAttachmentOwnership(contactId, attachmentId, filePath, "photo.png", "image/png")

        val result = extractor.extract(contactId, listOf(attachmentId))

        assertEquals("", result[attachmentId]?.text)
        assertFalse(result[attachmentId]?.supported == true)
        assertTrue(result[attachmentId]?.unsupportedReason?.contains("不支持") == true)
    }

    @Test
    fun `validate rejects attachment from another contact`() {
        val attachmentId = 104L
        val storageDir = tempDir.resolve("mail").resolve("104")
        Files.createDirectories(storageDir)
        val filePath = storageDir.resolve("cv.pdf")
        Files.writeString(filePath, "ignored")

        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(attachmentId))
            .thenReturn(
                ExpertDocument(
                    id = 1L,
                    expertContactId = 99L,
                    mailAttachmentId = attachmentId,
                    documentType = "CV",
                    documentStatus = DocumentStatus.PENDING_REVIEW.name
                )
            )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            extractor.validateAttachmentBelongsToContact(10L, attachmentId)
        }
        assertTrue(ex.message!!.contains("does not belong"))
    }

    private fun stubAttachmentOwnership(
        contactId: Long,
        attachmentId: Long,
        filePath: Path,
        fileName: String,
        contentType: String
    ) {
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(attachmentId))
            .thenReturn(
                ExpertDocument(
                    id = 1L,
                    expertContactId = contactId,
                    mailAttachmentId = attachmentId,
                    documentType = "CV",
                    documentStatus = DocumentStatus.PENDING_REVIEW.name
                )
            )
        Mockito.`when`(mailAttachmentRepository.findById(attachmentId))
            .thenReturn(
                Optional.of(
                    MailAttachment(
                        id = attachmentId,
                        mailRecordId = 500L,
                        fileName = fileName,
                        contentType = contentType,
                        fileSize = Files.size(filePath),
                        storagePath = filePath.toString(),
                        createdAt = LocalDateTime.now()
                    )
                )
            )
        Mockito.`when`(mailRecordRepository.findByIdOrNull(500L))
            .thenReturn(
                MailRecord(
                    id = 500L,
                    expertContactId = contactId,
                    direction = "INBOUND",
                    mailType = "REPLY",
                    messageId = "msg-500",
                    inReplyTo = null,
                    subject = "docs",
                    body = "body",
                    matchedQaRuleId = null,
                    sendStatus = null,
                    receivedAt = LocalDateTime.now(),
                    sentAt = null,
                    createdAt = LocalDateTime.now()
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
