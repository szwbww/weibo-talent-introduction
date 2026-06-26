package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

class MailboxAttachmentServiceTest {
    private val mailboxService = Mockito.mock(MailboxService::class.java)
    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)

    @Test
    fun `listAttachments maps mailbox attachments`() {
        val service = MailboxAttachmentService(
            MailAttachmentStorageProperties(basePath = "/tmp"),
            mailboxService,
            mailAttachmentRepository
        )
        Mockito.`when`(mailboxService.resolveAttachments("MAIL_RECORD", 5L)).thenReturn(
            listOf(
                MailAttachment(
                    id = 1L,
                    mailRecordId = 5L,
                    inboundProcessingId = null,
                    fileName = "cv.pdf",
                    contentType = "application/pdf",
                    fileSize = 100L,
                    storagePath = "/tmp/cv.pdf"
                )
            )
        )

        val result = service.listAttachments("MAIL_RECORD", 5L)

        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("cv.pdf", result[0].fileName)
    }

    @Test
    fun `download rejects path outside base path`(@TempDir tempDir: Path) {
        val baseDir = tempDir.resolve("base")
        Files.createDirectories(baseDir)
        val outsideFile = tempDir.resolve("outside.txt")
        Files.writeString(outsideFile, "secret")

        val service = MailboxAttachmentService(
            MailAttachmentStorageProperties(basePath = baseDir.toString()),
            mailboxService,
            mailAttachmentRepository
        )
        Mockito.`when`(mailAttachmentRepository.findById(7L)).thenReturn(
            Optional.of(
                MailAttachment(
                    id = 7L,
                    mailRecordId = null,
                    inboundProcessingId = 3L,
                    fileName = "outside.txt",
                    contentType = "text/plain",
                    fileSize = 6L,
                    storagePath = outsideFile.toString()
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.download(7L)
        }
    }

    @Test
    fun `download returns file inside base path`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("file.txt")
        Files.writeString(file, "hello")

        val service = MailboxAttachmentService(
            MailAttachmentStorageProperties(basePath = tempDir.toString()),
            mailboxService,
            mailAttachmentRepository
        )
        Mockito.`when`(mailAttachmentRepository.findById(8L)).thenReturn(
            Optional.of(
                MailAttachment(
                    id = 8L,
                    mailRecordId = 2L,
                    inboundProcessingId = null,
                    fileName = "file.txt",
                    contentType = "text/plain",
                    fileSize = 5L,
                    storagePath = file.toString()
                )
            )
        )

        val result = service.download(8L)

        assertEquals("file.txt", result.fileName)
        assertEquals("text/plain", result.contentType)
        assertEquals("hello", Files.readString(result.path))
    }
}
