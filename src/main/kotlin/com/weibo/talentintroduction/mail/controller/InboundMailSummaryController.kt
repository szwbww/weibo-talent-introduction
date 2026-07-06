package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.InboundMailTagService
import com.weibo.talentintroduction.mail.service.TagStatItem
import com.weibo.talentintroduction.mail.service.TagStatsResult
import com.weibo.talentintroduction.mail.service.TagView
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/inbound-summary")
class InboundMailSummaryController(
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val inboundMailTagService: InboundMailTagService,
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository
) {
    @GetMapping("/mails")
    fun listMails(
        @RequestParam(required = false) tagKey: String?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(defaultValue = "0") pageOffset: Int
    ): InboundSummaryListResponse {
        val (qaRuleId, customLabel) = parseTagKey(tagKey)
        val records = inboundMailProcessingRepository.listInboundSummary(
            from = from,
            to = to,
            qaRuleId = qaRuleId,
            label = customLabel,
            limit = pageSize,
            offset = pageOffset
        )
        val totalCount = inboundMailProcessingRepository.countInboundSummary(
            from = from,
            to = to,
            qaRuleId = qaRuleId,
            label = customLabel
        )
        val inboundIds = records.mapNotNull { it.id }
        val tagsByInbound = inboundMailTagService.listTagsBatch(inboundIds)
        val contactIds = records.mapNotNull { it.expertContactId }.distinct()
        val contactsById = if (contactIds.isEmpty()) {
            emptyMap()
        } else {
            expertContactRepository.findAllById(contactIds).associateBy { it.id }
        }
        return InboundSummaryListResponse(
            records = records.map { record ->
                val inboundId = requireNotNull(record.id)
                InboundSummaryMailRow(
                    inboundId = inboundId,
                    fromEmail = record.fromEmail,
                    subject = record.subject,
                    receivedAt = record.receivedAt,
                    messageId = record.messageId,
                    expertContactId = record.expertContactId,
                    expertName = record.expertContactId?.let { contactsById[it]?.expertName },
                    processStatus = record.processStatus,
                    tags = tagsByInbound[inboundId].orEmpty()
                )
            },
            totalCount = totalCount
        )
    }

    @GetMapping("/mails/{inboundId}/thread")
    fun getThread(@PathVariable inboundId: Long): InboundSummaryThreadResponse {
        val inbound = inboundMailProcessingRepository.findById(inboundId)
            .orElseThrow { IllegalArgumentException("Inbound mail processing not found: $inboundId") }
        val tags = inboundMailTagService.listTags(inboundId)
        val processingByMessageId = if (inbound.expertContactId != null) {
            inboundMailProcessingRepository.findAllByExpertContactId(inbound.expertContactId)
                .mapNotNull { processing ->
                    processing.messageId?.let { messageId ->
                        messageId to requireNotNull(processing.id)
                    }
                }
                .toMap()
        } else {
            emptyMap()
        }
        val messages = if (inbound.expertContactId != null) {
            mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(inbound.expertContactId)
                .map { record ->
                    val inboundProcessingId = record.resolveInboundProcessingId(processingByMessageId)
                    record.toThreadMessage(inboundProcessingId)
                }
        } else {
            listOf(inbound.toThreadMessage())
        }
        val processingIds = messages.mapNotNull { it.inboundProcessingId }
        val tagsByProcessing = inboundMailTagService.listTagsBatch(processingIds)
        val messagesWithTags = messages.map { message ->
            message.inboundProcessingId?.let { processingId ->
                message.copy(tags = tagsByProcessing[processingId].orEmpty())
            } ?: message
        }
        return InboundSummaryThreadResponse(
            inboundId = inboundId,
            currentMessageId = inbound.messageId,
            tags = tags,
            messages = messagesWithTags
        )
    }

    @GetMapping("/tags/stats")
    fun tagStats(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?
    ): TagStatsResult = if (from != null && to != null) {
        inboundMailTagService.stats(from, to)
    } else {
        inboundMailTagService.stats()
    }

    @GetMapping("/tags/options")
    fun tagOptions(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?
    ): TagOptionsResponse {
        val stats = if (from != null && to != null) {
            inboundMailTagService.stats(from, to)
        } else {
            inboundMailTagService.stats()
        }
        return TagOptionsResponse(items = stats.items)
    }

    @PostMapping("/mails/{inboundId}/tags/auto")
    fun autoApplyTags(
        @PathVariable inboundId: Long,
        @RequestBody(required = false) request: TagOperatorRequest?
    ): TagListResponse {
        val inbound = inboundMailProcessingRepository.findById(inboundId)
            .orElseThrow { IllegalArgumentException("Inbound mail processing not found: $inboundId") }
        val body = inbound.cleanedBody ?: inbound.body
        val addedCount = inboundMailTagService.autoApplyQaTags(inboundId, body, request?.operatorName)
        return TagListResponse(tags = inboundMailTagService.listTags(inboundId), addedCount = addedCount)
    }

    @PostMapping("/mails/{inboundId}/tags")
    fun addTag(
        @PathVariable inboundId: Long,
        @RequestBody request: AddTagRequest
    ): TagListResponse {
        when {
            request.qaRuleId != null -> inboundMailTagService.addQaTag(
                inboundId,
                request.qaRuleId,
                request.operatorName
            )
            !request.label.isNullOrBlank() -> inboundMailTagService.addCustomTag(
                inboundId,
                request.label,
                request.operatorName
            )
            else -> throw IllegalArgumentException("Either qaRuleId or label is required")
        }
        return TagListResponse(tags = inboundMailTagService.listTags(inboundId))
    }

    @DeleteMapping("/tags/{tagId}")
    fun deleteTag(@PathVariable tagId: Long) {
        inboundMailTagService.deleteTag(tagId)
    }

    private fun parseTagKey(tagKey: String?): Pair<Long?, String?> {
        if (tagKey.isNullOrBlank()) return null to null
        return when {
            tagKey.startsWith("qa:") -> tagKey.removePrefix("qa:").toLongOrNull()?.let { it to null }
                ?: throw IllegalArgumentException("Invalid tagKey: $tagKey")
            tagKey.startsWith("custom:") -> null to tagKey.removePrefix("custom:")
            else -> throw IllegalArgumentException("Invalid tagKey: $tagKey")
        }
    }
}

data class InboundSummaryListResponse(
    val records: List<InboundSummaryMailRow>,
    val totalCount: Long
)

data class InboundSummaryMailRow(
    val inboundId: Long,
    val fromEmail: String,
    val subject: String?,
    val receivedAt: LocalDateTime,
    val messageId: String?,
    val expertContactId: Long?,
    val expertName: String?,
    val processStatus: String,
    val tags: List<TagView>
)

data class InboundSummaryThreadResponse(
    val inboundId: Long,
    val currentMessageId: String?,
    val tags: List<TagView>,
    val messages: List<ThreadMessageView>
)

data class ThreadMessageView(
    val messageId: String?,
    val direction: String?,
    val subject: String?,
    val body: String?,
    val sentAt: LocalDateTime?,
    val receivedAt: LocalDateTime?,
    val inboundProcessingId: Long? = null,
    val tags: List<TagView> = emptyList()
)

data class TagOptionsResponse(
    val items: List<TagStatItem>
)

data class TagListResponse(
    val tags: List<TagView>,
    val addedCount: Int? = null
)

data class AddTagRequest(
    val qaRuleId: Long? = null,
    val label: String? = null,
    val operatorName: String? = null
)

data class TagOperatorRequest(
    val operatorName: String? = null
)

private fun MailRecord.resolveInboundProcessingId(processingByMessageId: Map<String, Long>): Long? {
    if (!direction.equals("INBOUND", ignoreCase = true)) {
        return null
    }
    return sourceInboundId ?: messageId?.let { processingByMessageId[it] }
}

private fun MailRecord.toThreadMessage(inboundProcessingId: Long? = null) = ThreadMessageView(
    messageId = messageId,
    direction = direction,
    subject = subject,
    body = cleanedBody ?: body,
    sentAt = sentAt,
    receivedAt = receivedAt,
    inboundProcessingId = inboundProcessingId
)

private fun InboundMailProcessing.toThreadMessage() = ThreadMessageView(
    messageId = messageId,
    direction = "INBOUND",
    subject = subject,
    body = cleanedBody ?: body,
    sentAt = null,
    receivedAt = receivedAt,
    inboundProcessingId = id
)
