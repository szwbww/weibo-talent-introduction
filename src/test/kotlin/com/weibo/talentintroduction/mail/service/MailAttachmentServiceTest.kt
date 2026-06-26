package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.DocumentStatus
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.domain.ExpertDocumentType
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `saves attachment file and creates pending document`(@TempDir tempDir: Path) {
        val service = MailAttachmentService(
            MailAttachmentStorageProperties(basePath = tempDir.toString()),
            mailAttachmentRepository,
            expertDocumentRepository
        )
        Mockito.`when`(mailAttachmentRepository.save(Mockito.any(MailAttachment::class.java)))
            .thenAnswer { invocation ->
                val attachment = invocation.getArgument<MailAttachment>(0)
                attachment.copy(id = 31)
            }
        Mockito.`when`(expertDocumentRepository.save(Mockito.any(ExpertDocument::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertDocument>(0) }

        val documents = service.saveInboundAttachments(
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
    }

    @Test
    fun `saveUnmatchedAttachments writes file without expert document`(@TempDir tempDir: Path) {
        val service = MailAttachmentService(
            MailAttachmentStorageProperties(basePath = tempDir.toString()),
            mailAttachmentRepository,
            expertDocumentRepository
        )
        Mockito.`when`(mailAttachmentRepository.save(Mockito.any(MailAttachment::class.java)))
            .thenAnswer { invocation ->
                val attachment = invocation.getArgument<MailAttachment>(0)
                attachment.copy(id = 41)
            }

        val saved = service.saveUnmatchedAttachments(
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
}
