package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.domain.ExpertDocumentType
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

@Service
class MailAttachmentService(
    private val properties: MailAttachmentStorageProperties,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val expertDocumentRepository: ExpertDocumentRepository
) {
    fun saveInboundAttachments(
        expertContactId: Long,
        mailRecordId: Long,
        attachments: List<ReceivedMailAttachment>
    ): List<ExpertDocument> {
        if (attachments.isEmpty()) {
            return emptyList()
        }

        val now = LocalDateTime.now()
        val directory = Path.of(properties.basePath, expertContactId.toString(), mailRecordId.toString())
        Files.createDirectories(directory)

        return attachments.map { received ->
            val safeFileName = received.fileName.toSafeFileName()
            val storagePath = directory.resolve("${UUID.randomUUID()}-$safeFileName")
            Files.write(storagePath, received.content)

            val mailAttachment = mailAttachmentRepository.save(
                MailAttachment(
                    mailRecordId = mailRecordId,
                    fileName = received.fileName,
                    contentType = received.contentType,
                    fileSize = received.content.size.toLong(),
                    storagePath = storagePath.toString(),
                    createdAt = now
                )
            )

            expertDocumentRepository.save(
                ExpertDocument(
                    expertContactId = expertContactId,
                    mailAttachmentId = mailAttachment.id ?: error("Mail attachment id is required"),
                    documentType = inferDocumentType(received.fileName).name,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun inferPrimaryIntentFromAttachments(attachments: List<ReceivedMailAttachment>): InboundIntentCode? {
        if (attachments.isEmpty()) {
            return null
        }
        val types = attachments.map { inferDocumentType(it.fileName) }.toSet()
        return if (types == setOf(ExpertDocumentType.CV) || ExpertDocumentType.CV in types) {
            InboundIntentCode.CV_ATTACHED
        } else {
            InboundIntentCode.DOCS_ATTACHED
        }
    }

    fun inferDocumentType(fileName: String): ExpertDocumentType {
        val normalized = fileName.lowercase(Locale.ROOT)
        return when {
            normalized.contains("cv") || normalized.contains("resume") -> ExpertDocumentType.CV
            normalized.contains("passport") -> ExpertDocumentType.PASSPORT
            normalized.contains("phd") || normalized.contains("doctor") -> ExpertDocumentType.PHD_DEGREE
            normalized.contains("master") -> ExpertDocumentType.MASTER_DEGREE
            normalized.contains("bachelor") -> ExpertDocumentType.BACHELOR_DEGREE
            normalized.contains("employment") || normalized.contains("work") -> ExpertDocumentType.EMPLOYMENT_PROOF
            normalized.contains("patent") -> ExpertDocumentType.PATENT_PROOF
            normalized.contains("award") || normalized.contains("honor") -> ExpertDocumentType.AWARD_PROOF
            normalized.contains("publication") || normalized.contains("paper") -> ExpertDocumentType.PUBLICATION_LIST
            normalized.endsWith(".ppt") || normalized.endsWith(".pptx") || normalized.contains("powerpoint") -> ExpertDocumentType.PPT
            normalized.endsWith(".mp4") || normalized.endsWith(".mov") || normalized.contains("video") || normalized.contains("vcr") -> ExpertDocumentType.VIDEO
            normalized.contains("commitment") || normalized.contains("statement") -> ExpertDocumentType.COMMITMENT
            else -> ExpertDocumentType.OTHER
        }
    }

    private fun String.toSafeFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "attachment" }
            .take(180)
}
