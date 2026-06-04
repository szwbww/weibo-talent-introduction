package com.weibo.talentintroduction.audit.repository

import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

interface OperatorActionLogRepository : CrudRepository<OperatorActionLog, Long> {
    @Query(
        """
        SELECT * FROM operator_action_log
        WHERE (:expertContactId IS NULL OR expert_contact_id = :expertContactId)
          AND (:inboundProcessingId IS NULL OR inbound_processing_id = :inboundProcessingId)
          AND (:actionType IS NULL OR action_type = :actionType)
          AND (:operatorName IS NULL OR operator_name LIKE CONCAT('%', :operatorName, '%'))
          AND (:start IS NULL OR created_at >= :start)
          AND (:end IS NULL OR created_at < :end)
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun search(
        expertContactId: Long?,
        inboundProcessingId: Long?,
        actionType: String?,
        operatorName: String?,
        start: LocalDateTime?,
        end: LocalDateTime?,
        limit: Int,
        offset: Int
    ): List<OperatorActionLog>

    @Query(
        """
        SELECT COUNT(*) FROM operator_action_log
        WHERE (:expertContactId IS NULL OR expert_contact_id = :expertContactId)
          AND (:inboundProcessingId IS NULL OR inbound_processing_id = :inboundProcessingId)
          AND (:actionType IS NULL OR action_type = :actionType)
          AND (:operatorName IS NULL OR operator_name LIKE CONCAT('%', :operatorName, '%'))
          AND (:start IS NULL OR created_at >= :start)
          AND (:end IS NULL OR created_at < :end)
        """
    )
    fun countSearch(
        expertContactId: Long?,
        inboundProcessingId: Long?,
        actionType: String?,
        operatorName: String?,
        start: LocalDateTime?,
        end: LocalDateTime?
    ): Long
}