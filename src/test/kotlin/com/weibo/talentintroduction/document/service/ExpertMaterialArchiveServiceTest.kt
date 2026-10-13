package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.domain.ManualExpertMaterialUpload
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.document.repository.ManualExpertMaterialUploadRepository
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.*
import com.weibo.talentintroduction.mail.service.AttachmentTransferService
import com.weibo.talentintroduction.mail.service.MailAttachmentService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Locale
import java.util.Optional
import java.util.zip.ZipFile

/** Real ownership/path resolver and real ZIP files; only persistence/remote boundaries are mocked. */
internal class MaterialArchiveFixture(val root: Path) {
    val attachments = Mockito.mock(MailAttachmentRepository::class.java)
    val documents = Mockito.mock(ExpertDocumentRepository::class.java)
    val records = Mockito.mock(MailRecordRepository::class.java)
    val processing = Mockito.mock(InboundMailProcessingRepository::class.java)
    val uploads = Mockito.mock(ManualExpertMaterialUploadRepository::class.java)
    val transfers = Mockito.mock(MailAttachmentTransferRepository::class.java)
    val transferService = Mockito.mock(AttachmentTransferService::class.java)
    val mailService = Mockito.mock(MailAttachmentService::class.java)
    val material = ExpertMaterialService(
        MailAttachmentStorageProperties(basePath = root.toString()), documents, attachments, records,
        processing, transfers, transferService, mailService, Mockito.mock(NamedParameterJdbcTemplate::class.java), uploads
    )
    val archive = ExpertMaterialArchiveService(material, attachments)

    fun file(id: Long, name: String = "材料-$id.pdf", bytes: ByteArray = "body-$id".toByteArray(), owner: String = "manual", contact: Long = 7): Path {
        val path = root.resolve("file-$id")
        Files.write(path, bytes)
        register(id, name, path, owner, contact)
        return path
    }

    fun register(id: Long, name: String, path: Path?, owner: String = "manual", contact: Long = 7) {
        val row = MailAttachment(id = id, mailRecordId = id.takeIf { owner == "record" },
            inboundProcessingId = id.takeIf { owner == "processing" }, manualUploadId = id.takeIf { owner == "manual" },
            fileName = name, contentType = "application/pdf", storagePath = path?.toString(), fileSize = null)
        Mockito.`when`(attachments.findById(id)).thenReturn(Optional.of(row))
        Mockito.`when`(documents.findFirstByMailAttachmentId(id)).thenReturn(ExpertDocument(id = id, expertContactId = contact, mailAttachmentId = id, documentType = "CV"))
        Mockito.`when`(uploads.findById(id)).thenReturn(Optional.of(ManualExpertMaterialUpload(id, contact, "tester")))
        Mockito.`when`(records.findByIdOrNull(id)).thenReturn(MailRecord(id = id, expertContactId = contact, direction = "INBOUND", mailType = "REPLY", messageId = "msg-$id", inReplyTo = null, subject = "test", body = "test", matchedQaRuleId = null, sendStatus = null, receivedAt = null, sentAt = null))
        Mockito.`when`(processing.findById(id)).thenReturn(Optional.of(InboundMailProcessing(id = id, senderAccountCode = "account", imapUid = id, messageId = "msg-$id", fromEmail = "test@example.com", subject = "test", receivedAt = LocalDateTime.now(), processStatus = "MANUAL_RESOLVED", processReason = "test", expertContactId = contact)))
    }

    fun assertNoWritesOrTransfers() {
        Mockito.verifyNoInteractions(transferService, mailService)
        Mockito.verify(attachments, Mockito.never()).save(Mockito.any(MailAttachment::class.java))
        Mockito.verify(documents, Mockito.never()).save(Mockito.any(ExpertDocument::class.java))
    }
}

class ExpertMaterialArchiveServiceTest {
    @TempDir lateinit var root: Path

    @Test
    fun `all owner types keep exact bytes names zero length and deduplicate IDs`() {
        val f = MaterialArchiveFixture(root)
        f.file(1, "简历.pdf", byteArrayOf(0, 1, 2), "record")
        f.file(2, "简历.pdf", byteArrayOf(3, 4), "processing")
        f.file(3, "简历 (2).pdf", byteArrayOf(5))
        f.file(4, "空文件.txt", byteArrayOf())
        var archivePath: Path? = null
        f.archive.withArchive(7, listOf(1, 2, 3, 4, 1)) { archive ->
            archivePath = archive.path
            assertEquals("materials-7.zip", archive.fileName)
            assertEquals(Files.size(archive.path), archive.fileSize)
            ZipFile(archive.path.toFile()).use { zip ->
                val entries = zip.entries().asSequence().toList()
                assertEquals(listOf("简历.pdf", "简历 (2).pdf", "简历 (2) (2).pdf", "空文件.txt"), entries.map { it.name })
                entries.zip(listOf(byteArrayOf(0, 1, 2), byteArrayOf(3, 4), byteArrayOf(5), byteArrayOf())).forEach { (entry, bytes) ->
                    assertArrayEquals(bytes, zip.getInputStream(entry).use { it.readBytes() })
                }
            }
        }
        assertFalse(Files.exists(archivePath!!))
        f.assertNoWritesOrTransfers()
    }

    @Test
    fun `unsafe and long UTF8 names remain portable unique basenames`() {
        val f = MaterialArchiveFixture(root)
        val names = listOf("../../secret.pdf", "C:\\temp\\CV.PDF", "cv.pdf", "CON.txt", "LPT¹.pdf", "..", "bad\u0000<name>:?*.txt", "😀中".repeat(100) + ".pdf", "😀中".repeat(100) + ".pdf", "final. ")
        names.forEachIndexed { index, name -> f.file(index + 1L, name) }
        f.archive.withArchive(7, names.indices.map { it + 1L }) { archive ->
            ZipFile(archive.path.toFile()).use { zip ->
                val actual = zip.entries().asSequence().map { it.name }.toList()
                assertEquals(names.size, actual.map { it.lowercase(Locale.ROOT) }.toSet().size)
                assertEquals("secret.pdf", actual[0])
                assertEquals("cv (2).pdf", actual[2])
                assertEquals("_CON.txt", actual[3])
                assertEquals("_LPT¹.pdf", actual[4])
                assertEquals("attachment-6", actual[5])
                assertEquals("final", actual.last())
                actual.forEach { name ->
                    assertTrue(name.toByteArray(Charsets.UTF_8).size <= 240)
                    assertFalse(Regex("[\\p{Cc}<>:\"|?*/\\\\]").containsMatchIn(name))
                    assertFalse(name.endsWith(".") || name.endsWith(" "))
                    assertFalse(name.contains('\uFFFD'))
                }
                assertTrue(actual[7].endsWith(".pdf"))
                assertTrue(actual[8].endsWith(" (2).pdf"))
            }
        }
    }

    @Test
    fun `not ready rejects entire batch without creating archive or writing business state`() {
        val f = MaterialArchiveFixture(root)
        f.file(1)
        f.register(2, "未获取.pdf", null)
        val service = Mockito.spy(f.archive)
        val error = assertThrows(MaterialNotReadyException::class.java) {
            service.withArchive(7, listOf(1, 2)) { fail<Unit>("must not deliver partial ZIP") }
        }
        assertEquals(2L, error.attachmentId)
        assertEquals("资料尚未就绪，请先获取到服务器：未获取.pdf", error.message)
        Mockito.verify(service, Mockito.never()).createArchiveFile()
        f.assertNoWritesOrTransfers()
    }

    @Test
    fun `wrong document owner wrong source owner and escaped realpath are refused`() {
        val f = MaterialArchiveFixture(root)
        f.file(1, contact = 8)
        assertThrows(IllegalArgumentException::class.java) { f.archive.withArchive(7, listOf(1)) { fail<Unit>() } }
        Mockito.`when`(f.documents.findFirstByMailAttachmentId(1)).thenReturn(ExpertDocument(expertContactId = 7, mailAttachmentId = 1, documentType = "CV"))
        assertThrows(IllegalArgumentException::class.java) { f.archive.withArchive(7, listOf(1)) { fail<Unit>() } }
        val outside = Files.createTempFile("outside-materials-", ".pdf")
        try {
            val link = root.resolve("escape")
            Files.createSymbolicLink(link, outside)
            f.register(2, "escape.pdf", link)
            assertThrows(IllegalArgumentException::class.java) { f.archive.withArchive(7, listOf(2)) { fail<Unit>() } }
        } finally { Files.deleteIfExists(outside) }
        f.assertNoWritesOrTransfers()
    }

    @Test
    fun `missing record empty nonpositive and oversized selection fail before output`() {
        val f = MaterialArchiveFixture(root)
        for (ids in listOf(emptyList(), listOf(0L), listOf(-1L), (1L..501L).toList())) {
            assertThrows(IllegalArgumentException::class.java) { f.archive.withArchive(7, ids) { fail<Unit>() } }
        }
        assertThrows(NoSuchElementException::class.java) { f.archive.withArchive(7, listOf(9)) { fail<Unit>() } }
        for (id in 1L..500L) f.file(id, bytes = byteArrayOf())
        f.archive.withArchive(7, (1L..500L).toList()) { archive -> ZipFile(archive.path.toFile()).use { assertEquals(500, it.size()) } }
    }

    @Test
    fun `actual file size exceeding 500 MiB is rejected before temporary file creation`() {
        val f = MaterialArchiveFixture(root)
        val path = f.file(1)
        RandomAccessFile(path.toFile(), "rw").use { it.setLength(ExpertMaterialArchiveService.MAX_BYTES + 1) }
        val service = Mockito.spy(f.archive)
        val error = assertThrows(MaterialArchiveException::class.java) { service.withArchive(7, listOf(1)) { fail<Unit>() } }
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.status)
        Mockito.verify(service, Mockito.never()).createArchiveFile()
    }

    @Test
    fun `size cap applies to combined files not each file alone`() {
        val f = MaterialArchiveFixture(root)
        for (id in 1L..2L) {
            RandomAccessFile(f.file(id).toFile(), "rw").use { it.setLength(300L * 1024 * 1024) }
        }
        val error = assertThrows(MaterialArchiveException::class.java) {
            f.archive.withArchive(7, listOf(1, 2)) { fail<Unit>() }
        }
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.status)
    }

    @Test
    fun `exact 500 MiB limit succeeds with streaming output`() {
        val f = MaterialArchiveFixture(root)
        val path = f.file(1)
        RandomAccessFile(path.toFile(), "rw").use { it.setLength(ExpertMaterialArchiveService.MAX_BYTES) }
        f.archive.withArchive(7, listOf(1)) { archive ->
            ZipFile(archive.path.toFile()).use { zip ->
                val entry = zip.entries().nextElement()
                assertEquals(ExpertMaterialArchiveService.MAX_BYTES, entry.size)
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(65536)
                    var count = 0L
                    while (true) { val read = input.read(buffer); if (read < 0) break; count += read }
                    assertEquals(ExpertMaterialArchiveService.MAX_BYTES, count)
                }
            }
        }
    }

    @Test
    fun `growing source is checked by actual bytes and failed archive is deleted`() {
        val f = MaterialArchiveFixture(root)
        val path = f.file(1)
        val service = Mockito.spy(f.archive)
        val target = root.resolve("archive.zip")
        Mockito.doAnswer {
            RandomAccessFile(path.toFile(), "rw").use { it.setLength(ExpertMaterialArchiveService.MAX_BYTES + 1) }
            Files.createFile(target)
        }.`when`(service).createArchiveFile()
        val error = assertThrows(MaterialArchiveException::class.java) { service.withArchive(7, listOf(1)) { fail<Unit>() } }
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.status)
        assertFalse(Files.exists(target))
    }

    @Test
    fun `preparation IO failure is sanitized and disconnect always removes archive`() {
        val f = MaterialArchiveFixture(root)
        val path = f.file(1)
        val service = Mockito.spy(f.archive)
        val target = root.resolve("archive.zip")
        Mockito.doAnswer { Files.delete(path); Files.createFile(target) }.`when`(service).createArchiveFile()
        val error = assertThrows(MaterialArchiveException::class.java) { service.withArchive(7, listOf(1)) { fail<Unit>() } }
        assertEquals("压缩包生成失败，请重试", error.message)
        assertFalse(Files.exists(target))
        f.file(1)
        var prepared: Path? = null
        assertThrows(IOException::class.java) {
            f.archive.withArchive(7, listOf(1)) { archive -> prepared = archive.path; throw IOException("client disconnected") }
        }
        assertFalse(Files.exists(prepared!!))
    }
}
