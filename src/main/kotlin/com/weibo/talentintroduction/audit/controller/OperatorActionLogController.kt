package com.weibo.talentintroduction.audit.controller

import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/operator-action-logs")
class OperatorActionLogController(
    private val operatorActionLogService: OperatorActionLogService
) {
    @GetMapping
    fun search(
        @RequestParam(required = false) expertContactId: Long?,
        @RequestParam(required = false) inboundProcessingId: Long?,
        @RequestParam(required = false) actionType: String?,
        @RequestParam(required = false) operatorName: String?,
        @RequestParam(required = false) start: String?,
        @RequestParam(required = false) end: String?,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
        @RequestParam(required = false, defaultValue = "0") pageOffset: Int
    ): OperatorActionLogSearchResponse {
        val startTime = start?.let { LocalDateTime.parse(it) }
        val endTime = end?.let { LocalDateTime.parse(it) }
        val (records, total) = operatorActionLogService.search(
            expertContactId, inboundProcessingId, actionType, operatorName,
            startTime, endTime, pageSize, pageOffset
        )
        return OperatorActionLogSearchResponse(
            records = records.map { it.toResponse() },
            totalCount = total
        )
    }
}

data class OperatorActionLogSearchResponse(
    val records: List<OperatorActionLogResponse>,
    val totalCount: Long
)

data class OperatorActionLogResponse(
    val id: Long?,
    val targetType: String,
    val targetId: Long,
    val expertContactId: Long?,
    val inboundProcessingId: Long?,
    val actionType: String,
    val actionSummary: String,
    val beforeValue: String?,
    val afterValue: String?,
    val operatorName: String?,
    val note: String?,
    val createdAt: String?
)

private fun OperatorActionLog.toResponse() = OperatorActionLogResponse(
    id = id,
    targetType = targetType,
    targetId = targetId,
    expertContactId = expertContactId,
    inboundProcessingId = inboundProcessingId,
    actionType = actionType,
    actionSummary = actionSummary,
    beforeValue = beforeValue,
    afterValue = afterValue,
    operatorName = operatorName,
    note = note,
    createdAt = createdAt?.toString()
)