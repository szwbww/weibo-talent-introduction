package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface BatchSendTaskConfigRepository : CrudRepository<BatchSendTaskConfig, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): BatchSendTaskConfig?

    @Query(
        """
        SELECT * FROM batch_send_task_config
        WHERE deleted_at IS NULL
        ORDER BY updated_at DESC, id DESC
        """
    )
    fun findAllActiveOrderByUpdatedAtDescIdDesc(): List<BatchSendTaskConfig>

    @Query(
        """
        SELECT * FROM batch_send_task_config
        WHERE deleted_at IS NULL
          AND config_name LIKE CONCAT('%', :query, '%')
        ORDER BY updated_at DESC, id DESC
        """
    )
    fun findAllActiveByConfigNameContainingOrderByUpdatedAtDescIdDesc(query: String): List<BatchSendTaskConfig>

    @Query(
        """
        SELECT * FROM batch_send_task_config
        WHERE deleted_at IS NULL
          AND config_name = :configName
        LIMIT 1
        """
    )
    fun findByConfigNameAndDeletedAtIsNull(configName: String): BatchSendTaskConfig?

    fun findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc(): List<BatchSendTaskConfig>

    @Query(
        """
        SELECT * FROM batch_send_task_config
        WHERE legacy_code = :legacyCode
        LIMIT 1
        """
    )
    fun findByLegacyCode(legacyCode: String): BatchSendTaskConfig?
}
