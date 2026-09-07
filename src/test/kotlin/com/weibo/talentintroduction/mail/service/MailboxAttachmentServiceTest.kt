package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `listAttachments keeps null fileSize for metadata-only attachment`() {
        val service = MailboxAttachmentService(
            MailAttachmentStorageProperties(basePath = "/tmp"),
            mailboxService,
            mailAttachmentRepository
        )
        Mockito.`when`(mailboxService.resolveAttachments("MAIL_RECORD", 5L)).thenReturn(
            listOf(
                MailAttachment(
                    id = 2L,
                    mailRecordId = 5L,
                    inboundProcessingId = null,
                    fileName = "cv.pdf",
                    contentType = "application/pdf",
                    fileSize = null,
                    storagePath = null
                )
            )
        )

        val result = service.listAttachments("MAIL_RECORD", 5L)

        assertEquals(1, result.size)
        assertEquals(2L, result[0].id)
        assertNull(result[0].fileSize)
    }

    @Test
    fun `download rejects metadata-only attachment without a local file`() {
        val service = MailboxAttachmentService(
            MailAttachmentStorageProperties(basePath = "/tmp"),
            mailboxService,
            mailAttachmentRepository
        )
        Mockito.`when`(mailAttachmentRepository.findById(9L)).thenReturn(
            Optional.of(
                MailAttachment(
                    id = 9L,
                    mailRecordId = 2L,
                    inboundProcessingId = null,
                    fileName = "not-landed.pdf",
                    contentType = "application/pdf",
                    fileSize = null,
                    storagePath = null
                )
            )
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.download(9L)
        }
        assertTrue(ex.message!!.contains("no local file"))
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
        assertEquals(Files.size(file), result.fileSize)
    }

    @Test
    fun `download reports actual size of the verified file not the stored value`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("stale-size.txt")
        Files.writeString(file, "hello")

        val service = MailboxAttachmentService(
            MailAttachmentStorageProperties(basePath = tempDir.toString()),
            mailboxService,
            mailAttachmentRepository
        )
        Mockito.`when`(mailAttachmentRepository.findById(10L)).thenReturn(
            Optional.of(
                MailAttachment(
                    id = 10L,
                    mailRecordId = 2L,
                    inboundProcessingId = null,
                    fileName = "stale-size.txt",
                    contentType = "text/plain",
                    fileSize = 999L,
                    storagePath = file.toString()
                )
            )
        )

        val result = service.download(10L)

        assertEquals(5L, result.fileSize)
        assertEquals("hello", Files.readString(result.path))
    }

    @Test
    fun `download accepts a real zero-byte file as size zero`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("empty.txt")
        Files.write(file, byteArrayOf())

        val service = MailboxAttachmentService(
            MailAttachmentStorageProperties(basePath = tempDir.toString()),
            mailboxService,
            mailAttachmentRepository
        )
        Mockito.`when`(mailAttachmentRepository.findById(11L)).thenReturn(
            Optional.of(
                MailAttachment(
                    id = 11L,
                    mailRecordId = 2L,
                    inboundProcessingId = null,
                    fileName = "empty.txt",
                    contentType = "text/plain",
                    fileSize = 0L,
                    storagePath = file.toString()
                )
            )
        )

        val result = service.download(11L)

        assertEquals(0L, result.fileSize)
        assertTrue(Files.exists(result.path))
        assertEquals("", Files.readString(result.path))
    }
}
