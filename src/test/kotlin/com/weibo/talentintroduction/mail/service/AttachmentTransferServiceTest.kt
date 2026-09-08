package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentTransferRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.springframework.dao.DataIntegrityViolationException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Optional

class AttachmentTransferServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private val transferRepository = Mockito.mock(MailAttachmentTransferRepository::class.java)
    private val attachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
    private val expertDocumentRepository = Mockito.mock(ExpertDocumentRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)

    private fun service(): AttachmentTransferService = AttachmentTransferService(
        transferRepository,
        attachmentRepository,
        expertDocumentRepository,
        mailRecordRepository
    )

    private fun attachment(
        id: Long,
        mailRecordId: Long? = 10L,
        fileName: String = "cv.pdf",
        storagePath: String? = null
    ) = MailAttachment(
        id = id,
        mailRecordId = mailRecordId,
        inboundProcessingId = null,
        fileName = fileName,
        contentType = "application/pdf",
        fileSize = null,
        storagePath = storagePath
    )

    private fun materialRequest(
        attachmentId: Long? = 1L,
        accountCode: String = "acc-a",
        folder: String = "INBOX",
        uidValidity: Long = 7,
        imapUid: Long = 42,
        partPath: String = "2",
        messageId: String? = "msg-42",
        fileName: String = "cv.pdf"
    ) = AttachmentTransferService.RegisterTransferRequest(
        purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
        attachmentId = attachmentId,
        accountCode = accountCode,
        folder = folder,
        uidValidity = uidValidity,
        imapUid = imapUid,
        partPath = partPath,
        messageId = messageId,
        fileName = fileName,
        contentType = "application/pdf"
    )

    private fun stubAttachmentExists(id: Long) {
        Mockito.`when`(attachmentRepository.findById(id))
            .thenReturn(Optional.of(attachment(id)))
    }

    // ------------------------------------------------------------------
    // 登记（I-1：来源唯一身份；MATERIAL/DMARC 组合规则）
    // ------------------------------------------------------------------

    @Test
    fun `register creates a METADATA_ONLY row with validated source identity`() {
        stubAttachmentExists(1L)
        Mockito.`when`(transferRepository.findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString()
        )).thenReturn(null)
        Mockito.`when`(transferRepository.save(any(MailAttachmentTransfer::class.java)))
            .thenAnswer { it.getArgument<MailAttachmentTransfer>(0) }

        val result = service().register(materialRequest())

        assertTrue(result.created)
        assertEquals(MailAttachmentTransfer.STATE_METADATA_ONLY, result.transfer.state)
        assertNull(result.transfer.requestedBy)
        assertEquals(7, result.transfer.uidValidity)
        assertEquals(42, result.transfer.imapUid)
        assertEquals("2", result.transfer.partPath)
        assertEquals("acc-a", result.transfer.accountCode)
        assertEquals(1L, result.transfer.attachmentId)
    }

    @Test
    fun `register same source twice returns the same row without a second insert`() {
        stubAttachmentExists(1L)
        val existing = MailAttachmentTransfer(
            id = 9L, attachmentId = 1L, purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
            accountCode = "acc-a", folder = "INBOX", uidValidity = 7, imapUid = 42,
            partPath = "2", messageId = "msg-42", fileName = "cv.pdf",
            state = MailAttachmentTransfer.STATE_METADATA_ONLY
        )
        Mockito.`when`(transferRepository.findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString()
        )).thenReturn(existing)

        val service = service()
        val first = service.register(materialRequest())
        val second = service.register(materialRequest())

        assertFalse(first.created)
        assertFalse(second.created)
        assertEquals(9L, first.transfer.id)
        assertEquals(9L, second.transfer.id)
        Mockito.verify(transferRepository, Mockito.never()).save(any(MailAttachmentTransfer::class.java))
    }

    @Test
    fun `same file name across different UIDs or part paths registers distinct rows`() {
        stubAttachmentExists(1L)
        stubAttachmentExists(2L)
        Mockito.`when`(transferRepository.findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString()
        )).thenReturn(null)
        Mockito.`when`(transferRepository.save(any(MailAttachmentTransfer::class.java)))
            .thenAnswer { it.getArgument<MailAttachmentTransfer>(0) }

        val service = service()
        service.register(materialRequest(attachmentId = 1L, imapUid = 42, partPath = "2"))
        service.register(materialRequest(attachmentId = 2L, imapUid = 43, partPath = "2"))
        service.register(materialRequest(attachmentId = 1L, imapUid = 42, partPath = "3"))

        Mockito.verify(transferRepository, Mockito.times(3)).save(any(MailAttachmentTransfer::class.java))
    }

    @Test
    fun `register rejects MATERIAL without attachmentId and DMARC with attachmentId`() {
        val service = service()
        assertThrows(IllegalArgumentException::class.java) {
            service.register(materialRequest(attachmentId = null))
        }
        val base = materialRequest()
        val dmarcWithAttachment = base.copy(
            purpose = MailAttachmentTransfer.PURPOSE_DMARC
        )
        assertThrows(IllegalArgumentException::class.java) {
            service.register(dmarcWithAttachment)
        }
        val dmarcWithProcessing = base.copy(
            purpose = MailAttachmentTransfer.PURPOSE_DMARC,
            attachmentId = null,
            inboundProcessingId = 3L
        )
        assertThrows(IllegalArgumentException::class.java) {
            service.register(dmarcWithProcessing)
        }
        Mockito.verify(transferRepository, Mockito.never()).save(any())
    }

    @Test
    fun `register rejects invalid source identity values`() {
        val service = service()
        listOf("0", "1.0", "1..2", "a.b", "-1", "1.2.0", "").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) {
                service.register(materialRequest(attachmentId = 1L, partPath = path))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.register(materialRequest(uidValidity = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.register(materialRequest(imapUid = -1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.register(materialRequest(fileName = " "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.register(materialRequest(accountCode = ""))
        }
        val invalidPurpose = materialRequest().copy(purpose = "OTHER")
        assertThrows(IllegalArgumentException::class.java) {
            service.register(invalidPurpose)
        }
    }

    @Test
    fun `register rejects unknown attachment and unknown purpose rows`() {
        Mockito.`when`(attachmentRepository.findById(1L)).thenReturn(Optional.empty())
        assertThrows(IllegalArgumentException::class.java) {
            service().register(materialRequest(attachmentId = 1L))
        }
    }

    @Test
    fun `concurrent duplicate register falls back to the winning row on unique key violation`() {
        stubAttachmentExists(1L)
        val existing = MailAttachmentTransfer(
            id = 5L, attachmentId = 1L, purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
            accountCode = "acc-a", folder = "INBOX", uidValidity = 7, imapUid = 42,
            partPath = "2", fileName = "cv.pdf", state = MailAttachmentTransfer.STATE_METADATA_ONLY
        )
        // 第一次查找未命中 -> save 触发唯一键冲突 -> 回查返回并发赢家行。
        Mockito.`when`(transferRepository.findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString()
        )).thenReturn(null, existing)
        Mockito.`when`(transferRepository.save(any(MailAttachmentTransfer::class.java)))
            .thenThrow(DataIntegrityViolationException("duplicate source"))

        val result = service().register(materialRequest())

        assertFalse(result.created)
        assertEquals(5L, result.transfer.id)
    }

    @Test
    fun `register rejects same source registered for a different purpose or attachment`() {
        val material = MailAttachmentTransfer(
            id = 5L, attachmentId = 1L, purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
            accountCode = "acc-a", folder = "INBOX", uidValidity = 7, imapUid = 42,
            partPath = "2", fileName = "cv.pdf", state = MailAttachmentTransfer.STATE_METADATA_ONLY
        )
        Mockito.`when`(transferRepository.findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString()
        )).thenReturn(material)

        val service = service()
        assertThrows(IllegalArgumentException::class.java) {
            service.register(materialRequest(attachmentId = 1L).copy(purpose = MailAttachmentTransfer.PURPOSE_DMARC, attachmentId = null))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.register(materialRequest(attachmentId = 2L))
        }
    }

    // ------------------------------------------------------------------
    // 入队（I-2：重复提交不新建；显式重试；上限）
    // ------------------------------------------------------------------

    private fun stubTransferRow(
        id: Long,
        attachmentId: Long,
        state: String = MailAttachmentTransfer.STATE_METADATA_ONLY
    ): MailAttachmentTransfer {
        val row = MailAttachmentTransfer(
            id = id,
            attachmentId = attachmentId,
            purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
            accountCode = "acc-a",
            folder = "INBOX",
            uidValidity = 7,
            imapUid = 42,
            partPath = "2",
            fileName = "cv.pdf",
            state = state
        )
        Mockito.`when`(transferRepository.findByAttachmentId(attachmentId)).thenReturn(row)
        Mockito.`when`(transferRepository.findById(id)).thenReturn(Optional.of(row))
        return row
    }

    @Test
    fun `enqueueMaterial flips METADATA_ONLY to QUEUED once and reports later duplicates as already queued`() {
        val row = MailAttachmentTransfer(
            id = 1L, attachmentId = 1L, purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
            accountCode = "acc-a", folder = "INBOX", uidValidity = 7, imapUid = 42,
            partPath = "2", fileName = "cv.pdf", state = MailAttachmentTransfer.STATE_METADATA_ONLY
        )
        stubAttachmentExists(1L)
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(
            ExpertDocument(expertContactId = 1L, mailAttachmentId = 1L, documentType = "CV")
        )
        Mockito.`when`(transferRepository.findByAttachmentId(1L)).thenReturn(row)
        Mockito.`when`(transferRepository.countQueued()).thenReturn(0L)
        Mockito.`when`(transferRepository.markRequested(Mockito.anyLong(), Mockito.anyString(), any(LocalDateTime::class.java) ?: LocalDateTime.now()))
            .thenReturn(1)
        val queuedRow = row.copy(state = MailAttachmentTransfer.STATE_QUEUED)
        Mockito.`when`(transferRepository.findById(1L)).thenReturn(Optional.of(queuedRow))

        val service = service()
        val first = service.enqueueMaterial(1L, listOf(1L), "admin")
        // 重复提交：行已是 QUEUED，markRequested 的 CAS 不会再命中
        Mockito.`when`(transferRepository.markRequested(Mockito.anyLong(), Mockito.anyString(), any(LocalDateTime::class.java) ?: LocalDateTime.now()))
            .thenReturn(0)
        val second = service.enqueueMaterial(1L, listOf(1L), "admin")

        assertEquals(1, first.acceptedCount)
        assertEquals(MailAttachmentTransfer.STATE_QUEUED, first.items.single().transferState)
        assertEquals(0, second.acceptedCount)
        assertEquals(MailAttachmentTransfer.STATE_QUEUED, second.items.single().transferState)
        Mockito.verify(transferRepository, Mockito.times(2)).markRequested(Mockito.anyLong(), Mockito.anyString(), any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `enqueueMaterial on STORED row does not re-queue`() {
        stubAttachmentExists(1L)
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(
            ExpertDocument(expertContactId = 1L, mailAttachmentId = 1L, documentType = "CV")
        )
        val row = MailAttachmentTransfer(
            id = 3L, attachmentId = 1L, purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
            accountCode = "acc-a", folder = "INBOX", uidValidity = 7, imapUid = 42,
            partPath = "2", fileName = "cv.pdf", state = MailAttachmentTransfer.STATE_STORED
        )
        Mockito.`when`(transferRepository.findByAttachmentId(1L)).thenReturn(row)

        val result = service().enqueueMaterial(1L, listOf(1L), "admin")

        assertEquals(MailAttachmentTransfer.STATE_STORED, result.items.single().transferState)
        assertEquals(0, result.acceptedCount)
        Mockito.verify(transferRepository, Mockito.never()).markRequested(Mockito.anyLong(), Mockito.anyString(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `explicit retry of a FAILED row re-queues and clears the failure`() {
        stubAttachmentExists(1L)
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(
            ExpertDocument(expertContactId = 1L, mailAttachmentId = 1L, documentType = "CV")
        )
        val row = MailAttachmentTransfer(
            id = 4L, attachmentId = 1L, purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
            accountCode = "acc-a", folder = "INBOX", uidValidity = 7, imapUid = 42,
            partPath = "2", fileName = "cv.pdf", state = MailAttachmentTransfer.STATE_FAILED,
            errorCode = "TIMEOUT", errorMessage = "deadline exceeded"
        )
        Mockito.`when`(transferRepository.findByAttachmentId(1L)).thenReturn(row)
        Mockito.`when`(transferRepository.countQueued()).thenReturn(0L)
        Mockito.`when`(transferRepository.markRequested(Mockito.anyLong(), Mockito.anyString(), any(LocalDateTime::class.java) ?: LocalDateTime.now()))
            .thenReturn(1)

        val result = service().enqueueMaterial(1L, listOf(1L), "admin")

        assertEquals(1, result.acceptedCount)
        assertEquals(MailAttachmentTransfer.STATE_QUEUED, result.items.single().transferState)
        Mockito.verify(transferRepository).markRequested(
            Mockito.anyLong(), Mockito.anyString(),
            Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )
    }

    @Test
    fun `ready legacy attachment is reported as already ready and never queued`() {
        val readyPath = tempDir.resolve("legacy.pdf")
        Files.write(readyPath, ByteArray(10))
        stubAttachmentExists(1L)
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(
            ExpertDocument(expertContactId = 1L, mailAttachmentId = 1L, documentType = "CV")
        )
        Mockito.`when`(attachmentRepository.findById(1L)).thenReturn(
            Optional.of(attachment(1L, storagePath = readyPath.toString()))
        )
        Mockito.`when`(transferRepository.findByAttachmentId(1L)).thenReturn(null)

        val result = service().enqueueMaterial(1L, listOf(1L), "admin")

        assertEquals(1, result.alreadyReadyCount)
        assertEquals(0, result.acceptedCount)
        assertTrue(result.items.single().alreadyReady)
        Mockito.verify(transferRepository, Mockito.never()).markRequested(Mockito.anyLong(), Mockito.anyString(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `attachment without local file and without a registered source cannot be queued`() {
        stubAttachmentExists(1L)
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(
            ExpertDocument(expertContactId = 1L, mailAttachmentId = 1L, documentType = "CV")
        )
        Mockito.`when`(transferRepository.findByAttachmentId(1L)).thenReturn(null)

        val result = service().enqueueMaterial(1L, listOf(1L), "admin")

        assertEquals("NO_RELIABLE_SOURCE", result.items.single().errorCode)
        assertEquals(0, result.acceptedCount)
    }

    @Test
    fun `enqueueMaterial rejects attachments not owned by the expert or entirely unbound`() {
        val attachmentId = 1L
        stubAttachmentExists(attachmentId)
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(attachmentId))
            .thenReturn(ExpertDocument(expertContactId = 99L, mailAttachmentId = attachmentId, documentType = "CV"))

        val otherOwner = service().enqueueMaterial(1L, listOf(attachmentId), "admin")
        assertEquals("NOT_OWNED_BY_EXPERT", otherOwner.items.single().errorCode)

        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(attachmentId)).thenReturn(null)
        Mockito.`when`(attachmentRepository.findById(attachmentId)).thenReturn(
            Optional.of(attachment(attachmentId, mailRecordId = null))
        )
        val unbound = service().enqueueMaterial(1L, listOf(attachmentId), "admin")
        assertEquals("ATTACHMENT_UNBOUND", unbound.items.single().errorCode)
        Mockito.verify(transferRepository, Mockito.never()).markRequested(Mockito.anyLong(), Mockito.anyString(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `ownership can be resolved through the owning mail record when no expert document exists`() {
        val attachmentId = 1L
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(attachmentId)).thenReturn(null)
        Mockito.`when`(attachmentRepository.findById(attachmentId)).thenReturn(
            Optional.of(attachment(attachmentId, mailRecordId = 10L))
        )
        Mockito.`when`(mailRecordRepository.findByIdOrNull(10L))
            .thenReturn(
                MailRecord(
                    id = 10L, expertContactId = 1L, direction = "INBOUND", mailType = "REPLY",
                    messageId = null, inReplyTo = null, subject = null, body = null,
                    matchedQaRuleId = null, sendStatus = null, receivedAt = null, sentAt = null
                )
            )
        val row = MailAttachmentTransfer(
            id = 7L, attachmentId = attachmentId, purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
            accountCode = "acc-a", folder = "INBOX", uidValidity = 7, imapUid = 42,
            partPath = "2", fileName = "cv.pdf", state = MailAttachmentTransfer.STATE_METADATA_ONLY
        )
        Mockito.`when`(transferRepository.findByAttachmentId(attachmentId)).thenReturn(row)
        Mockito.`when`(transferRepository.countQueued()).thenReturn(0L)
        Mockito.`when`(transferRepository.markRequested(Mockito.anyLong(), Mockito.anyString(), any(LocalDateTime::class.java) ?: LocalDateTime.now()))
            .thenReturn(1)

        val result = service().enqueueMaterial(1L, listOf(attachmentId), "admin")

        assertEquals(MailAttachmentTransfer.STATE_QUEUED, result.items.single().transferState)
        assertEquals(1, result.acceptedCount)
    }

    @Test
    fun `enqueueMaterial rejects oversized request batches and missing ids`() {
        val service = service()
        assertThrows(IllegalArgumentException::class.java) {
            service.enqueueMaterial(1L, emptyList(), "admin")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.enqueueMaterial(1L, (1L..501L).toList(), "admin")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.enqueueMaterial(1L, listOf(1L), " ")
        }
        Mockito.verify(transferRepository, Mockito.never()).markRequested(Mockito.anyLong(), Mockito.anyString(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `enqueue refuses when the global queue is full without touching rows`() {
        stubAttachmentExists(1L)
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(
            ExpertDocument(expertContactId = 1L, mailAttachmentId = 1L, documentType = "CV")
        )
        stubTransferRow(2L, 1L)
        Mockito.`when`(transferRepository.countQueued()).thenReturn(
            AttachmentTransferService.MAX_QUEUED_ROWS
        )

        val result = service().enqueueMaterial(1L, listOf(1L), "admin")

        assertTrue(result.queueFull)
        assertEquals("QUEUE_FULL", result.items.single().errorCode)
        assertEquals(0, result.acceptedCount)
        Mockito.verify(transferRepository, Mockito.never()).markRequested(Mockito.anyLong(), Mockito.anyString(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `missing attachment and duplicate ids are handled per item`() {
        Mockito.`when`(attachmentRepository.findById(1L)).thenReturn(Optional.empty())
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(1L)).thenReturn(null)
        Mockito.`when`(attachmentRepository.findById(2L)).thenReturn(
            Optional.of(attachment(2L, mailRecordId = 10L))
        )
        Mockito.`when`(mailRecordRepository.findByIdOrNull(10L))
            .thenReturn(
                MailRecord(
                    id = 10L, expertContactId = 1L, direction = "INBOUND", mailType = "REPLY",
                    messageId = null, inReplyTo = null, subject = null, body = null,
                    matchedQaRuleId = null, sendStatus = null, receivedAt = null, sentAt = null
                )
            )
        stubTransferRow(11L, 2L, state = MailAttachmentTransfer.STATE_METADATA_ONLY)
        Mockito.`when`(transferRepository.countQueued()).thenReturn(0L)
        Mockito.`when`(transferRepository.markRequested(Mockito.anyLong(), Mockito.anyString(), any(LocalDateTime::class.java) ?: LocalDateTime.now()))
            .thenReturn(1)

        val result = service().enqueueMaterial(1L, listOf(1L, 2L, 2L), "admin")

        assertEquals(2, result.items.size) // 去重后 2 件
        assertEquals("ATTACHMENT_NOT_FOUND", result.items[0].errorCode)
        assertEquals(1, result.acceptedCount)
    }

    // ------------------------------------------------------------------
    // 按 transfer id 入队（DMARC SYSTEM 规则）
    // ------------------------------------------------------------------

    @Test
    fun `DMARC rows can only be requested by SYSTEM and MATERIAL retry by id works`() {
        val dmarc = MailAttachmentTransfer(
            id = 21L, attachmentId = null, purpose = MailAttachmentTransfer.PURPOSE_DMARC,
            accountCode = "acc-d", folder = "INBOX", uidValidity = 7, imapUid = 50,
            partPath = "2", fileName = "report.xml", contentType = "application/xml",
            state = MailAttachmentTransfer.STATE_METADATA_ONLY
        )
        Mockito.`when`(transferRepository.findById(21L)).thenReturn(Optional.of(dmarc))

        val service = service()
        assertThrows(IllegalArgumentException::class.java) {
            service.enqueueTransferByIds(listOf(21L), "admin")
        }
        Mockito.verify(transferRepository, Mockito.never()).markRequested(Mockito.anyLong(), Mockito.anyString(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())

        Mockito.`when`(transferRepository.countQueued()).thenReturn(0L)
        Mockito.`when`(transferRepository.markRequested(
            Mockito.anyLong(), Mockito.anyString(),
            any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )).thenReturn(1)
        val systemResult = service.enqueueTransferByIds(
            listOf(21L), AttachmentTransferService.SYSTEM_REQUESTER
        )
        assertEquals(1, systemResult.acceptedCount)
        assertEquals(MailAttachmentTransfer.STATE_QUEUED, systemResult.items.single().transferState)
        assertFalse(systemResult.queueFull)
    }

    @Test
    fun `enqueue by id reports missing rows`() {
        Mockito.`when`(transferRepository.findById(99L)).thenReturn(Optional.empty())
        val result = service().enqueueTransferByIds(listOf(99L), AttachmentTransferService.SYSTEM_REQUESTER)
        assertEquals("TRANSFER_NOT_FOUND", result.items.single().errorCode)
    }
}
