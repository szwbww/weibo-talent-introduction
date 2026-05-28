package com.weibo.talentintroduction.document.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("expert_document")
data class ExpertDocument(
    @Id
    val id: Long? = null,
    val expertContactId: Long,
    val mailAttachmentId: Long,
    val documentType: String,
    val documentStatus: String = DocumentStatus.PENDING_REVIEW.name,
    val reviewNote: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)

enum class ExpertDocumentType {
    CV,
    PASSPORT,
    PHD_DEGREE,
    MASTER_DEGREE,
    BACHELOR_DEGREE,
    EMPLOYMENT_PROOF,
    PATENT_PROOF,
    AWARD_PROOF,
    PUBLICATION_LIST,
    PPT,
    VIDEO,
    COMMITMENT,
    OTHER
}

enum class DocumentStatus {
    PENDING_REVIEW,
    ACCEPTED,
    REJECTED
}
