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

    @Query(
        """
        SELECT * FROM operator_action_log
        WHERE inbound_processing_id = :inboundProcessingId
          AND action_type IN ('AI_REPLY_DRAFT_READY', 'AI_REPLY_DRAFT_NEEDS_REVIEW', 'AI_REPLY_DRAFT_BLOCKED')
        ORDER BY created_at DESC, id DESC
        LIMIT 1
        """
    )
    fun findLatestAiDraftByInboundProcessingId(inboundProcessingId: Long): OperatorActionLog?

    /**
     * 04 P-C 对账 I-2 判别器：存在 `action_type='CHANGE_OPERATOR_STATUS'` 审计日志的 contact id 集合。
     * 有该日志 = 被人工覆盖过（changeStatus 写审计、updateAutomatically 不写），其状态视为人工权威。
     * 调用方需保证 [contactIds] 非空（IN () 非法）。
     */
    @Query(
        """
        SELECT DISTINCT expert_contact_id FROM operator_action_log
        WHERE action_type = 'CHANGE_OPERATOR_STATUS'
          AND expert_contact_id IN (:contactIds)
        """
    )
    fun findContactIdsWithChangeOperatorStatusLogs(contactIds: Collection<Long>): List<Long>
}
