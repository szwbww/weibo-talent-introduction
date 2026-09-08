package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.DocumentStatus
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.domain.ExpertDocumentType
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentTransferRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path

class MailAttachmentServiceTest {
    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
    private val expertDocumentRepository = Mockito.mock(ExpertDocumentRepository::class.java)
    private val attachmentTransferRepository = Mockito.mock(MailAttachmentTransferRepository::class.java)

    private fun service(basePath: String = "/tmp/mail-attachments", metadataOnly: Boolean = false) =
        MailAttachmentService(
            MailAttachmentStorageProperties(basePath = basePath, metadataOnly = metadataOnly),
            mailAttachmentRepository,
            expertDocumentRepository,
            attachmentTransferRepository
        )

    /** mock 行为：attachment save 依次给 id；transfer 查无→save 原样返回；doc 查无→save 原样返回。 */
    private fun stubMetadataSaveBehaviours() {
        Mockito.`when`(mailAttachmentRepository.save(Mockito.any(MailAttachment::class.java)))
            .thenAnswer { invocation ->
                val attachment = invocation.getArgument<MailAttachment>(0)
                attachment.copy(id = attachment.id ?: (ids.incrementAndGet().toLong()))
            }
        Mockito.`when`(attachmentTransferRepository
            .findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString()
            ))
            .thenReturn(null)
        Mockito.`when`(attachmentTransferRepository.save(Mockito.any(MailAttachmentTransfer::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<MailAttachmentTransfer>(0) }
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(Mockito.anyLong())).thenReturn(null)
        Mockito.`when`(expertDocumentRepository.save(Mockito.any(ExpertDocument::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertDocument>(0) }
    }

    private val ids = java.util.concurrent.atomic.AtomicLong(0)

    private fun source(
        uidValidity: Long = 5L,
        imapUid: Long = 101L,
        partPath: String = "2"
    ) = ImapAttachmentSource(
        accountCode = "sender",
        folder = "INBOX",
        uidValidity = uidValidity,
        uid = imapUid,
        partPath = partPath,
        messageId = "reply-1",
        encodedSize = 1024L,
        disposition = "attachment"
    )

    private fun metadataAttachment(
        fileName: String,
        partPath: String = "2",
        uidValidity: Long = 5L
    ) = ReceivedMailAttachment(
        fileName = fileName,
        contentType = "application/pdf",
        content = null,
        source = source(uidValidity = uidValidity, partPath = partPath)
    )

    private fun contentAttachment(fileName: String, partPath: String = "2") = ReceivedMailAttachment(
        fileName = fileName,
        contentType = "application/pdf",
        content = "content".toByteArray(),
        source = source(partPath = partPath)
    )

    @Test
    fun `saves attachment file and creates pending document`(@TempDir tempDir: Path) {
        val svc = service(basePath = tempDir.toString())
        Mockito.`when`(mailAttachmentRepository.save(Mockito.any(MailAttachment::class.java)))
            .thenAnswer { invocation ->
                val attachment = invocation.getArgument<MailAttachment>(0)
                attachment.copy(id = 31)
            }
        Mockito.`when`(expertDocumentRepository.save(Mockito.any(ExpertDocument::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertDocument>(0) }

        val documents = svc.saveInboundAttachments(
            expertContactId = 11,
            mailRecordId = 22,
            attachments = listOf(
                ReceivedMailAttachment(
                    fileName = "Professor CV.pdf",
                    contentType = "application/pdf",
                    content = "cv-content".toByteArray()
                )
            )
        )

        assertEquals(1, documents.size)
        val attachmentCaptor = ArgumentCaptor.forClass(MailAttachment::class.java)
        Mockito.verify(mailAttachmentRepository).save(attachmentCaptor.capture())
        assertEquals("Professor CV.pdf", attachmentCaptor.value.fileName)
        assertTrue(Files.exists(Path.of(attachmentCaptor.value.storagePath)))
        assertEquals("cv-content", Files.readString(Path.of(attachmentCaptor.value.storagePath)))

        val documentCaptor = ArgumentCaptor.forClass(ExpertDocument::class.java)
        Mockito.verify(expertDocumentRepository).save(documentCaptor.capture())
        assertEquals(ExpertDocumentType.CV.name, documentCaptor.value.documentType)
        assertEquals(DocumentStatus.PENDING_REVIEW.name, documentCaptor.value.documentStatus)
        Mockito.verifyNoInteractions(attachmentTransferRepository)
    }

    @Test
    fun `saveUnmatchedAttachments writes file without expert document`(@TempDir tempDir: Path) {
        val svc = service(basePath = tempDir.toString())
        Mockito.`when`(mailAttachmentRepository.save(Mockito.any(MailAttachment::class.java)))
            .thenAnswer { invocation ->
                val attachment = invocation.getArgument<MailAttachment>(0)
                attachment.copy(id = 41)
            }

        val saved = svc.saveUnmatchedAttachments(
            inboundProcessingId = 99,
            attachments = listOf(
                ReceivedMailAttachment(
                    fileName = "unknown.pdf",
                    contentType = "application/pdf",
                    content = "pdf-content".toByteArray()
                )
            )
        )

        assertEquals(1, saved.size)
        val attachmentCaptor = ArgumentCaptor.forClass(MailAttachment::class.java)
        Mockito.verify(mailAttachmentRepository).save(attachmentCaptor.capture())
        assertEquals(null, attachmentCaptor.value.mailRecordId)
        assertEquals(99L, attachmentCaptor.value.inboundProcessingId)
        assertTrue(Files.exists(Path.of(attachmentCaptor.value.storagePath)))
        assertEquals("pdf-content", Files.readString(Path.of(attachmentCaptor.value.storagePath)))
        Mockito.verify(expertDocumentRepository, Mockito.never()).save(Mockito.any(ExpertDocument::class.java))
    }

    @Test
    fun `metadata save of 39 attachments indexes exactly 39 attachments transfers and documents`() {
        stubMetadataSaveBehaviours()
        val svc = service()

        val attachments = (1..39).map { idx ->
            metadataAttachment(fileName = "doc-$idx.pdf", partPath = "2.$idx")
        }
        val documents = svc.saveInboundAttachments(
            expertContactId = 11,
            mailRecordId = 22,
            attachments = attachments
        )

        assertEquals(39, documents.size)
        Mockito.verify(mailAttachmentRepository, Mockito.times(39)).save(Mockito.any(MailAttachment::class.java))
        val transferCaptor = ArgumentCaptor.forClass(MailAttachmentTransfer::class.java)
        Mockito.verify(attachmentTransferRepository, Mockito.times(39)).save(transferCaptor.capture())
        assertEquals(39, transferCaptor.allValues.map { it.partPath }.distinct().size)
        transferCaptor.allValues.forEach { transfer ->
            assertEquals(MailAttachmentTransfer.PURPOSE_MATERIAL, transfer.purpose)
            assertEquals(MailAttachmentTransfer.STATE_METADATA_ONLY, transfer.state)
            assertNotNull(transfer.attachmentId)
            assertEquals("sender", transfer.accountCode)
            assertEquals("INBOX", transfer.folder)
            assertEquals(5L, transfer.uidValidity)
            assertEquals(101L, transfer.imapUid)
            assertEquals(null, transfer.inboundProcessingId)
        }
        val docCaptor = ArgumentCaptor.forClass(ExpertDocument::class.java)
        Mockito.verify(expertDocumentRepository, Mockito.times(39)).save(docCaptor.capture())
        assertEquals(39, docCaptor.allValues.map { it.mailAttachmentId }.distinct().size)
        // 元数据模式零文件 I/O：无 attachment 保存带 storagePath/fileSize
        val attachmentCaptor = ArgumentCaptor.forClass(MailAttachment::class.java)
        Mockito.verify(mailAttachmentRepository, Mockito.times(39)).save(attachmentCaptor.capture())
        attachmentCaptor.allValues.forEach { attachment ->
            assertEquals(null, attachment.storagePath)
            assertEquals(null, attachment.fileSize)
            assertEquals(22L, attachment.mailRecordId)
        }
    }

    @Test
    fun `metadata save of 1000 attachments registers all without artificial cap`() {
        stubMetadataSaveBehaviours()
        val svc = service()

        val attachments = (1..1000).map { idx ->
            metadataAttachment(fileName = "many-$idx.pdf", partPath = "2.$idx")
        }
        val documents = svc.saveInboundAttachments(
            expertContactId = 11,
            mailRecordId = 22,
            attachments = attachments
        )

        assertEquals(1000, documents.size)
        Mockito.verify(attachmentTransferRepository, Mockito.times(1000)).save(Mockito.any(MailAttachmentTransfer::class.java))
        Mockito.verify(expertDocumentRepository, Mockito.times(1000)).save(Mockito.any(ExpertDocument::class.java))
    }

    @Test
    fun `same file name different part paths are separate rows while same source never merges duplicates`() {
        stubMetadataSaveBehaviours()
        val svc = service()

        // 同名但不同 part → 各自一行
        val sameName = listOf(
            metadataAttachment(fileName = "cv.pdf", partPath = "2.1"),
            metadataAttachment(fileName = "cv.pdf", partPath = "2.2")
        )
        svc.saveInboundAttachments(expertContactId = 11, mailRecordId = 22, attachments = sameName)
        Mockito.verify(mailAttachmentRepository, Mockito.times(2)).save(Mockito.any(MailAttachment::class.java))
        Mockito.verify(attachmentTransferRepository, Mockito.times(2)).save(Mockito.any(MailAttachmentTransfer::class.java))

        // 同源（同 account/folder/validity/uid/partPath）再次到达 → 复用已登记附件，零新增
        Mockito.reset(mailAttachmentRepository, attachmentTransferRepository, expertDocumentRepository)
        stubMetadataSaveBehaviours()
        Mockito.`when`(attachmentTransferRepository
            .findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
                "sender", "INBOX", 5L, 101L, "2.1"
            ))
            .thenReturn(
                MailAttachmentTransfer(
                    id = 10L,
                    attachmentId = 1L,
                    purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
                    accountCode = "sender",
                    folder = "INBOX",
                    uidValidity = 5L,
                    imapUid = 101L,
                    partPath = "2.1",
                    fileName = "cv.pdf",
                    contentType = "application/pdf",
                    state = MailAttachmentTransfer.STATE_METADATA_ONLY
                )
            )
        Mockito.`when`(mailAttachmentRepository.findById(1L)).thenReturn(
            java.util.Optional.of(MailAttachment(id = 1L, mailRecordId = 22L, fileName = "cv.pdf", contentType = "application/pdf"))
        )

        val documents = svc.saveInboundAttachments(
            expertContactId = 11,
            mailRecordId = 22,
            attachments = listOf(metadataAttachment(fileName = "cv.pdf", partPath = "2.1"))
        )

        assertEquals(1, documents.size)
        Mockito.verify(mailAttachmentRepository, Mockito.never()).save(Mockito.any(MailAttachment::class.java))
        Mockito.verify(attachmentTransferRepository, Mockito.never()).save(Mockito.any(MailAttachmentTransfer::class.java))
        assertEquals(1L, documents[0].mailAttachmentId)
    }

    @Test
    fun `failure on the 20th attachment aborts registration with no partial index`() {
        stubMetadataSaveBehaviours()
        val svc = service()
        val savedPartPaths = mutableListOf<String>()
        Mockito.`when`(attachmentTransferRepository.save(Mockito.any(MailAttachmentTransfer::class.java)))
            .thenAnswer { invocation ->
                val transfer = invocation.getArgument<MailAttachmentTransfer>(0)
                if (transfer.partPath == "2.20") {
                    throw IllegalStateException("db down on 20th")
                }
                savedPartPaths += transfer.partPath
                transfer
            }

        val attachments = (1..39).map { idx ->
            metadataAttachment(fileName = "doc-$idx.pdf", partPath = "2.$idx")
        }
        assertThrows(IllegalStateException::class.java) {
            svc.saveInboundAttachments(expertContactId = 11, mailRecordId = 22, attachments = attachments)
        }

        // 第 20 件失败即中止：只有 19 件成功登记，无部分 39 件索引，后续件不再尝试
        // （I-2；整信回滚由外层事务保证）
        assertEquals(19, savedPartPaths.size)
        assertTrue(savedPartPaths.none { it == "2.20" })
        assertTrue(savedPartPaths.none { it == "2.21" })
        val docCaptor = ArgumentCaptor.forClass(ExpertDocument::class.java)
        Mockito.verify(expertDocumentRepository, Mockito.times(19)).save(docCaptor.capture())
        Mockito.verify(mailAttachmentRepository, Mockito.times(20)).save(Mockito.any(MailAttachment::class.java))
    }

    @Test
    fun `metadata content null without source fails explicitly instead of writing empty file`(@TempDir tempDir: Path) {
        stubMetadataSaveBehaviours()
        val svc = service(basePath = tempDir.toString())

        val orphan = ReceivedMailAttachment(
            fileName = "broken.pdf",
            contentType = "application/pdf",
            content = null,
            source = null
        )
        assertThrows(MetadataContentUnavailableException::class.java) {
            svc.saveInboundAttachments(expertContactId = 11, mailRecordId = 22, attachments = listOf(orphan))
        }
        assertEquals(0, Files.list(tempDir).count())
    }

    @Test
    fun `bridgeInboundProcessing links all registered transfers to the processing row idempotently`() {
        stubMetadataSaveBehaviours()
        val svc = service()

        val attachments = listOf(
            metadataAttachment(fileName = "a.pdf", partPath = "2.1"),
            metadataAttachment(fileName = "b.pdf", partPath = "2.2")
        )
        svc.saveInboundAttachments(expertContactId = 11, mailRecordId = 22, attachments = attachments)

        Mockito.reset(attachmentTransferRepository)
        Mockito.`when`(attachmentTransferRepository
            .findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString()
            ))
            .thenAnswer { invocation ->
                val partPath = invocation.getArgument<String>(4)
                val id = if (partPath == "2.1") 1L else 2L
                MailAttachmentTransfer(
                    id = id,
                    attachmentId = id,
                    purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
                    accountCode = "sender",
                    folder = "INBOX",
                    uidValidity = 5L,
                    imapUid = 101L,
                    partPath = partPath,
                    fileName = "$partPath.pdf",
                    contentType = "application/pdf",
                    state = MailAttachmentTransfer.STATE_METADATA_ONLY
                )
            }
        Mockito.`when`(attachmentTransferRepository.save(Mockito.any(MailAttachmentTransfer::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<MailAttachmentTransfer>(0) }

        svc.bridgeInboundProcessing(processingId = 777L, attachments = attachments)
        val transferCaptor = ArgumentCaptor.forClass(MailAttachmentTransfer::class.java)
        Mockito.verify(attachmentTransferRepository, Mockito.times(2)).save(transferCaptor.capture())
        transferCaptor.allValues.forEach { assertEquals(777L, it.inboundProcessingId) }

        // 幂等：已桥接行不再改写
        Mockito.reset(attachmentTransferRepository)
        Mockito.`when`(attachmentTransferRepository
            .findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString()
            ))
            .thenAnswer { invocation ->
                val partPath = invocation.getArgument<String>(4)
                val id = if (partPath == "2.1") 1L else 2L
                MailAttachmentTransfer(
                    id = id,
                    attachmentId = id,
                    purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
                    accountCode = "sender",
                    folder = "INBOX",
                    uidValidity = 5L,
                    imapUid = 101L,
                    partPath = partPath,
                    fileName = "$partPath.pdf",
                    contentType = "application/pdf",
                    state = MailAttachmentTransfer.STATE_METADATA_ONLY,
                    inboundProcessingId = 777L
                )
            }
        svc.bridgeInboundProcessing(processingId = 777L, attachments = attachments)
        Mockito.verify(attachmentTransferRepository, Mockito.never()).save(Mockito.any(MailAttachmentTransfer::class.java))
    }

    @Test
    fun `bridgeInboundProcessing fails the confirmation when a metadata registration is missing`() {
        stubMetadataSaveBehaviours()
        val svc = service()

        Mockito.`when`(attachmentTransferRepository
            .findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString()
            ))
            .thenReturn(null)

        assertThrows(IllegalStateException::class.java) {
            svc.bridgeInboundProcessing(
                processingId = 777L,
                attachments = listOf(metadataAttachment(fileName = "a.pdf", partPath = "2.1"))
            )
        }
    }

    @Test
    fun `bridge skips legacy content attachments with remote source and stays strict for metadata`(@TempDir tempDir: Path) {
        // A-1 回归：child-03 在两种模式下都无条件填 source；旧 content 模式附件经旧路径
        // 完整落库（无 transfer 行）。确认点 bridge 必须跳过 content 附件（I-2 完整性
        // 只约束 metadata 附件），否则默认 metadataOnly=false 下匹配来信带附件即确认回滚。
        val svc = service(basePath = tempDir.toString())
        Mockito.`when`(mailAttachmentRepository.save(Mockito.any(MailAttachment::class.java)))
            .thenAnswer { invocation ->
                val attachment = invocation.getArgument<MailAttachment>(0)
                attachment.copy(id = 71)
            }
        Mockito.`when`(expertDocumentRepository.save(Mockito.any(ExpertDocument::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertDocument>(0) }

        val contentAttachment = ReceivedMailAttachment(
            fileName = "cv.pdf",
            contentType = "application/pdf",
            content = "cv-content".toByteArray(),
            source = source(partPath = "2")
        )
        val documents = svc.saveInboundAttachments(
            expertContactId = 11,
            mailRecordId = 22,
            attachments = listOf(contentAttachment)
        )
        assertEquals(1, documents.size)
        // 旧 content 路径：真实文件落盘、无 transfer 行
        val savedAttachment = ArgumentCaptor.forClass(MailAttachment::class.java)
        Mockito.verify(mailAttachmentRepository).save(savedAttachment.capture())
        assertTrue(Files.exists(Path.of(savedAttachment.value.storagePath)))
        assertEquals("cv-content", Files.readString(Path.of(savedAttachment.value.storagePath)))
        Mockito.verify(attachmentTransferRepository, Mockito.never()).save(Mockito.any(MailAttachmentTransfer::class.java))

        // 确认点 bridge：content 附件 no-op（不查 transfer、不保存、不抛错）
        Mockito.verify(attachmentTransferRepository, Mockito.never())
            .findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString()
            )
        svc.bridgeInboundProcessing(processingId = 777L, attachments = listOf(contentAttachment))
        Mockito.verify(attachmentTransferRepository, Mockito.never())
            .findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString()
            )
        Mockito.verify(attachmentTransferRepository, Mockito.never()).save(Mockito.any(MailAttachmentTransfer::class.java))

        // 同一信内 metadata 附件仍严格（混合防御不变）
        Mockito.`when`(attachmentTransferRepository
            .findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString()
            ))
            .thenReturn(null)
        assertThrows(IllegalStateException::class.java) {
            svc.bridgeInboundProcessing(
                processingId = 777L,
                attachments = listOf(contentAttachment, metadataAttachment(fileName = "m.pdf", partPath = "2.1"))
            )
        }
    }

    @Test
    fun `processing owner registration links transfer to processing and creates document for known contact`() {
        stubMetadataSaveBehaviours()
        val svc = service()

        val saved = svc.saveUnmatchedAttachments(
            inboundProcessingId = 99L,
            attachments = listOf(metadataAttachment(fileName = "resume.pdf", partPath = "2")),
            expertContactId = 11L
        )

        assertEquals(1, saved.size)
        val transferCaptor = ArgumentCaptor.forClass(MailAttachmentTransfer::class.java)
        Mockito.verify(attachmentTransferRepository).save(transferCaptor.capture())
        assertEquals(99L, transferCaptor.value.inboundProcessingId)
        val attachmentCaptor = ArgumentCaptor.forClass(MailAttachment::class.java)
        Mockito.verify(mailAttachmentRepository).save(attachmentCaptor.capture())
        assertEquals(99L, attachmentCaptor.value.inboundProcessingId)
        assertEquals(null, attachmentCaptor.value.mailRecordId)
        val docCaptor = ArgumentCaptor.forClass(ExpertDocument::class.java)
        Mockito.verify(expertDocumentRepository).save(docCaptor.capture())
        assertEquals(11L, docCaptor.value.expertContactId)
        assertEquals(saved.single().id, docCaptor.value.mailAttachmentId)
    }

    @Test
    fun `ensureDocumentsForProcessingAttachments is gated by the metadata switch and never touches files`() {
        // 开关关闭：no-op（保持旧路径；绑定不新增关联）
        val off = service(basePath = "/tmp/nowhere", metadataOnly = false)
        off.ensureDocumentsForProcessingAttachments(inboundProcessingId = 99L, expertContactId = 11L)
        Mockito.verify(mailAttachmentRepository, Mockito.never())
            .findAllByInboundProcessingIdOrderByCreatedAtAsc(Mockito.anyLong())
        Mockito.verifyNoInteractions(expertDocumentRepository)

        // 开关启用：按 processing-owner 附件幂等补 ExpertDocument（同 attachmentId；零文件 I/O）
        val on = service(basePath = "/tmp/nowhere", metadataOnly = true)
        Mockito.reset(mailAttachmentRepository, expertDocumentRepository)
        Mockito.`when`(mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(99L))
            .thenReturn(
                listOf(
                    MailAttachment(id = 5L, inboundProcessingId = 99L, fileName = "cv.pdf", contentType = "application/pdf"),
                    MailAttachment(id = 6L, inboundProcessingId = 99L, fileName = "degree.pdf", contentType = "application/pdf")
                )
            )
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(Mockito.anyLong())).thenReturn(null)
        Mockito.`when`(expertDocumentRepository.save(Mockito.any(ExpertDocument::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertDocument>(0) }

        on.ensureDocumentsForProcessingAttachments(inboundProcessingId = 99L, expertContactId = 11L)
        val docCaptor = ArgumentCaptor.forClass(ExpertDocument::class.java)
        Mockito.verify(expertDocumentRepository, Mockito.times(2)).save(docCaptor.capture())
        assertEquals(listOf(5L, 6L), docCaptor.allValues.map { it.mailAttachmentId })
        docCaptor.allValues.forEach {
            assertEquals(11L, it.expertContactId)
            assertEquals(DocumentStatus.PENDING_REVIEW.name, it.documentStatus)
        }

        // 幂等：已存在文档不再重复创建
        Mockito.reset(expertDocumentRepository)
        Mockito.`when`(expertDocumentRepository.findFirstByMailAttachmentId(Mockito.anyLong())).thenAnswer { invocation ->
            val id = invocation.getArgument<Long>(0)
            ExpertDocument(id = id, expertContactId = 11L, mailAttachmentId = id, documentType = "OTHER")
        }
        on.ensureDocumentsForProcessingAttachments(inboundProcessingId = 99L, expertContactId = 11L)
        Mockito.verify(expertDocumentRepository, Mockito.never()).save(Mockito.any(ExpertDocument::class.java))
    }
}
