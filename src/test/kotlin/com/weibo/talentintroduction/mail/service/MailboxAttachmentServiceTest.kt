package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.document.service.MaterialNotReadyException
import com.weibo.talentintroduction.document.service.ReadyFile
import com.weibo.talentintroduction.mail.domain.MailAttachment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path

class MailboxAttachmentServiceTest {
    private val mailboxService = Mockito.mock(MailboxService::class.java)
    private val materialService = Mockito.mock(ExpertMaterialService::class.java)

    private fun service(): MailboxAttachmentService =
        MailboxAttachmentService(mailboxService, materialService)

    @Test
    fun `listAttachments maps mailbox attachments`() {
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

        val result = service().listAttachments("MAIL_RECORD", 5L)

        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("cv.pdf", result[0].fileName)
    }

    @Test
    fun `listAttachments keeps null fileSize for metadata-only attachment`() {
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

        val result = service().listAttachments("MAIL_RECORD", 5L)

        assertEquals(1, result.size)
        assertEquals(2L, result[0].id)
        assertNull(result[0].fileSize)
    }

    @Test
    fun `download rejects metadata-only attachment with MATERIAL_NOT_READY`() {
        Mockito.`when`(materialService.resolveReadyFileUnscoped(9L))
            .thenThrow(MaterialNotReadyException(9L, "METADATA_ONLY", "no local file"))

        val ex = assertThrows(MaterialNotReadyException::class.java) {
            service().download(9L)
        }
        assertEquals(9L, ex.attachmentId)
        assertEquals("METADATA_ONLY", ex.state)
        assertTrue(ex.message!!.contains("no local file"))
    }

    @Test
    fun `download rejects path outside base path via the shared resolver`() {
        Mockito.`when`(materialService.resolveReadyFileUnscoped(7L))
            .thenThrow(IllegalArgumentException("Attachment path is outside configured base path"))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service().download(7L)
        }
        assertTrue(ex.message!!.contains("outside configured base path"))
    }

    @Test
    fun `download returns file inside base path`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("file.txt")
        Files.writeString(file, "hello")
        val attachment = MailAttachment(
            id = 8L,
            mailRecordId = 2L,
            inboundProcessingId = null,
            fileName = "file.txt",
            contentType = "text/plain",
            fileSize = 5L,
            storagePath = file.toString()
        )
        Mockito.`when`(materialService.resolveReadyFileUnscoped(8L))
            .thenReturn(ReadyFile(attachment, file.toRealPath()))

        val result = service().download(8L)

        assertEquals("file.txt", result.fileName)
        assertEquals("text/plain", result.contentType)
        assertEquals("hello", Files.readString(result.path))
        assertEquals(Files.size(file), result.fileSize)
        Mockito.verify(materialService).resolveReadyFileUnscoped(8L)
    }

    @Test
    fun `download reports actual size of the verified file not the stored value`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("stale-size.txt")
        Files.writeString(file, "hello")
        val attachment = MailAttachment(
            id = 10L,
            mailRecordId = 2L,
            inboundProcessingId = null,
            fileName = "stale-size.txt",
            contentType = "text/plain",
            fileSize = 999L,
            storagePath = file.toString()
        )
        Mockito.`when`(materialService.resolveReadyFileUnscoped(10L))
            .thenReturn(ReadyFile(attachment, file.toRealPath()))

        val result = service().download(10L)

        assertEquals(5L, result.fileSize)
        assertEquals("hello", Files.readString(result.path))
    }

    @Test
    fun `download accepts a real zero-byte file as size zero`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("empty.txt")
        Files.write(file, byteArrayOf())
        val attachment = MailAttachment(
            id = 11L,
            mailRecordId = 2L,
            inboundProcessingId = null,
            fileName = "empty.txt",
            contentType = "text/plain",
            fileSize = 0L,
            storagePath = file.toString()
        )
        Mockito.`when`(materialService.resolveReadyFileUnscoped(11L))
            .thenReturn(ReadyFile(attachment, file.toRealPath()))

        val result = service().download(11L)

        assertEquals(0L, result.fileSize)
        assertTrue(Files.exists(result.path))
        assertEquals("", Files.readString(result.path))
    }
}
