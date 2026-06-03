package com.weibo.talentintroduction.expert.repository

import com.weibo.talentintroduction.expert.domain.ExpertApplicationPromotion
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

interface ExpertApplicationPromotionRepository : CrudRepository<ExpertApplicationPromotion, Long> {
    fun findFirstByExpertContactIdAndPromotionStatusOrderByCreatedAtDesc(
        expertContactId: Long,
        promotionStatus: String
    ): ExpertApplicationPromotion?

    @Query(
        """
        SELECT COUNT(*) FROM expert_application_promotion
        WHERE promotion_status = :status
          AND created_at >= :from AND created_at < :to
        """
    )
    fun countByStatusAndCreatedAtBetween(status: String, from: LocalDateTime, to: LocalDateTime): Long

    @Query(
        """
        SELECT * FROM expert_application_promotion
        WHERE (:status IS NULL OR promotion_status = :status)
          AND (:from IS NULL OR created_at >= :from)
          AND (:to IS NULL OR created_at < :to)
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun list(
        status: String?,
        from: LocalDateTime?,
        to: LocalDateTime?,
        limit: Int,
        offset: Int
    ): List<ExpertApplicationPromotion>

    @Query(
        """
        SELECT COUNT(*) FROM expert_application_promotion
        WHERE (:status IS NULL OR promotion_status = :status)
          AND (:from IS NULL OR created_at >= :from)
          AND (:to IS NULL OR created_at < :to)
        """
    )
    fun count(status: String?, from: LocalDateTime?, to: LocalDateTime?): Long
}
