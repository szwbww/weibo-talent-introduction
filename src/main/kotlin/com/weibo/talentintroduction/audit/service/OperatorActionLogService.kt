package com.weibo.talentintroduction.audit.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.repository.OperatorActionLogRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class OperatorActionLogService(
    private val operatorActionLogRepository: OperatorActionLogRepository,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val MAX_PAGE_SIZE = 100
    }

    fun record(
        targetType: String,
        targetId: Long,
        actionType: OperatorActionType,
        expertContactId: Long? = null,
        inboundProcessingId: Long? = null,
        before: Any? = null,
        after: Any? = null,
        operatorName: String? = null,
        note: String? = null,
        summaryOverride: String? = null
    ): OperatorActionLog {
        val log = OperatorActionLog(
            targetType = targetType,
            targetId = targetId,
            expertContactId = expertContactId,
            inboundProcessingId = inboundProcessingId,
            actionType = actionType.name,
            actionSummary = summaryOverride ?: actionType.summary,
            beforeValue = before?.let { objectMapper.writeValueAsString(it) },
            afterValue = after?.let { objectMapper.writeValueAsString(it) },
            operatorName = operatorName,
            note = note,
            createdAt = LocalDateTime.now()
        )
        return operatorActionLogRepository.save(log)
    }

    fun search(
        expertContactId: Long?,
        inboundProcessingId: Long?,
        actionType: String?,
        operatorName: String?,
        start: LocalDateTime?,
        end: LocalDateTime?,
        pageSize: Int,
        pageOffset: Int
    ): Pair<List<OperatorActionLog>, Long> {
        val safePageSize = if (pageSize <= 0) 20 else minOf(pageSize, MAX_PAGE_SIZE)
        val safeOffset = if (pageOffset < 0) 0 else pageOffset
        val records = operatorActionLogRepository.search(
            expertContactId, inboundProcessingId, actionType, operatorName,
            start, end, safePageSize, safeOffset
        )
        val total = operatorActionLogRepository.countSearch(
            expertContactId, inboundProcessingId, actionType, operatorName,
            start, end
        )
        return Pair(records, total)
    }
}