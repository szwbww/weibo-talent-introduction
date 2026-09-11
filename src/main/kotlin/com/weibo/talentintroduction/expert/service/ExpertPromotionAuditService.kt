package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.expert.domain.ExpertApplicationPromotion
import com.weibo.talentintroduction.expert.repository.ExpertApplicationPromotionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 晋级审计独立于来信主事务。
 *
 * 审计写入失败时，新事务自行回滚；调用方可记录晋级失败，但不得把已经接收的来信回滚掉。
 */
@Service
class ExpertPromotionAuditService(
    private val repository: ExpertApplicationPromotionRepository
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun requireById(promotionId: Long): ExpertApplicationPromotion =
        repository.findById(promotionId)
            .orElseThrow { error("Promotion audit not found: $promotionId") }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun findLatestByContactAndStatus(
        expertContactId: Long,
        status: String
    ): ExpertApplicationPromotion? =
        repository.findFirstByExpertContactIdAndPromotionStatusOrderByCreatedAtDesc(expertContactId, status)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun create(
        contact: ExpertContact,
        orcid: String,
        sourceInboundId: Long?,
        triggeredBy: String,
        operatorName: String?
    ): ExpertApplicationPromotion? {
        val contactId = contact.id ?: return null
        val now = LocalDateTime.now()
        return repository.save(
            ExpertApplicationPromotion(
                expertContactId = contactId,
                orcidId = orcid,
                sourceInboundId = sourceInboundId,
                triggeredBy = triggeredBy,
                promotionStatus = "PENDING",
                operatorName = operatorName,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markSuccess(audit: ExpertApplicationPromotion?) {
        if (audit == null) return
        repository.save(audit.copy(promotionStatus = "SUCCESS", updatedAt = LocalDateTime.now()))
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(audit: ExpertApplicationPromotion?, message: String) {
        if (audit == null) return
        repository.save(
            audit.copy(
                promotionStatus = "FAILED",
                errorMessage = message.take(2000),
                updatedAt = LocalDateTime.now()
            )
        )
    }
}
