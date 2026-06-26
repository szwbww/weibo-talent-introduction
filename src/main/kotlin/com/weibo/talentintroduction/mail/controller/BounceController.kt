package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.BounceRecord
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.service.BounceBackfillResult
import com.weibo.talentintroduction.mail.service.BounceBackfillService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mail")
class BounceController(
    private val bounceRecordRepository: BounceRecordRepository,
    private val bounceBackfillService: BounceBackfillService,
    private val expertContactRepository: ExpertContactRepository,
    private val operatorActionLogService: OperatorActionLogService
) {
    @GetMapping("/bounces")
    fun listBounces(
        @RequestParam(required = false) accountCode: String?,
        @RequestParam(required = false) bounceType: String?,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
        @RequestParam(required = false, defaultValue = "0") pageOffset: Int
    ): BounceListResponse {
        val limit = pageSize.coerceIn(1, 100)
        val offset = pageOffset.coerceAtLeast(0)
        val records = bounceRecordRepository.findPaged(accountCode, bounceType, limit, offset)
        val totalCount = bounceRecordRepository.countPaged(accountCode, bounceType)
        val contactIds = records.mapNotNull { it.originalExpertContactId }.distinct()
        val contacts = if (contactIds.isNotEmpty()) {
            expertContactRepository.findAllById(contactIds).associateBy { it.id }
        } else {
            emptyMap()
        }
        return BounceListResponse(
            records = records.map { record ->
                val contact = record.originalExpertContactId?.let { contacts[it] }
                record.toResponse(
                    expertName = contact?.expertName,
                    expertEmail = contact?.expertEmail
                )
            },
            totalCount = totalCount
        )
    }

    @PostMapping("/bounces/backfill")
    fun backfill(
        @RequestParam(required = false, defaultValue = "200") batchSize: Int
    ): BounceBackfillResponse {
        val result = bounceBackfillService.run(batchSize.coerceIn(1, 500))
        operatorActionLogService.record(
            targetType = "mail_bounce",
            targetId = 0L,
            actionType = OperatorActionType.MARK_INBOUND_RESOLVED,
            summaryOverride = "退信历史回填",
            note = "scanned=${result.scanned}, ingested=${result.ingested}, duplicates=${result.duplicates}"
        )
        return result.toResponse()
    }
}

data class BounceListResponse(
    val records: List<BounceRecordResponse>,
    val totalCount: Long
)

data class BounceRecordResponse(
    val id: Long?,
    val senderAccountCode: String,
    val bounceMessageId: String,
    val originalMessageId: String?,
    val originalExpertContactId: Long?,
    val expertName: String?,
    val expertEmail: String?,
    val failedRecipient: String?,
    val bounceType: String,
    val dsnStatus: String?,
    val bounceReason: String?,
    val receivedAt: java.time.LocalDateTime
)

data class BounceBackfillResponse(
    val scanned: Int,
    val ingested: Int,
    val duplicates: Int
)

private fun BounceRecord.toResponse(
    expertName: String?,
    expertEmail: String?
) = BounceRecordResponse(
    id = id,
    senderAccountCode = senderAccountCode,
    bounceMessageId = bounceMessageId,
    originalMessageId = originalMessageId,
    originalExpertContactId = originalExpertContactId,
    expertName = expertName,
    expertEmail = expertEmail,
    failedRecipient = failedRecipient,
    bounceType = bounceType,
    dsnStatus = dsnStatus,
    bounceReason = bounceReason,
    receivedAt = receivedAt
)

private fun BounceBackfillResult.toResponse() = BounceBackfillResponse(
    scanned = scanned,
    ingested = ingested,
    duplicates = duplicates
)
