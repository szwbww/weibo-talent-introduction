package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.controller.MailboxDetailResponse
import com.weibo.talentintroduction.mail.controller.MailboxExpertGroupListResponse
import com.weibo.talentintroduction.mail.controller.MailboxExpertGroupResponse
import com.weibo.talentintroduction.mail.controller.MailboxItemResponse
import com.weibo.talentintroduction.mail.controller.MailboxListResponse
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailboxRow
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class MailboxService(
    private val mailRecordRepository: MailRecordRepository,
    private val senderAccountRepository: MailSenderAccountRepository,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val inboundMailTagService: InboundMailTagService
) {
    fun listMailbox(
        direction: String?,
        accountCode: String?,
        keyword: String?,
        recipientEmail: String?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        pending: Boolean,
        page: Int,
        size: Int
    ): MailboxListResponse {
        val activeAccounts = senderAccountRepository.findAllByAccountCodeNot(
            MailSenderAccountService.SIMULATOR_ACCOUNT_CODE
        )
        val activeCodes = activeAccounts.map { it.accountCode }
        if (activeCodes.isEmpty()) return MailboxListResponse(emptyList(), 0)

        if (accountCode != null && accountCode !in activeCodes) {
            return MailboxListResponse(emptyList(), 0)
        }

        val onlyPending = if (pending) 1 else 0
        val offset = page.toLong() * size
        val rows = mailRecordRepository.listMailbox(
            accountCodes = activeCodes,
            direction = direction,
            accountCode = accountCode,
            keyword = keyword,
            recipientEmail = recipientEmail,
            startTime = startTime,
            endTime = endTime,
            onlyPending = onlyPending,
            limit = size,
            offset = offset
        )
        val total = mailRecordRepository.countMailbox(
            accountCodes = activeCodes,
            direction = direction,
            accountCode = accountCode,
            keyword = keyword,
            recipientEmail = recipientEmail,
            startTime = startTime,
            endTime = endTime,
            onlyPending = onlyPending
        )

        val inboundIds = rows.mapNotNull { it.inboundProcessingId }
        val inboundTagsById = inboundMailTagService.listTagsBatch(inboundIds)

        val items = rows.map { row -> toMailboxItemResponse(row, inboundTagsById) }

        return MailboxListResponse(items, total)
    }

    fun listByExpert(
        direction: String?,
        accountCode: String?,
        keyword: String?,
        recipientEmail: String?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        pending: Boolean,
        tag: String?,
        page: Int,
        size: Int,
        expertContactId: Long? = null
    ): MailboxExpertGroupListResponse {
        val activeAccounts = senderAccountRepository.findAllByAccountCodeNot(
            MailSenderAccountService.SIMULATOR_ACCOUNT_CODE
        )
        val activeCodes = activeAccounts.map { it.accountCode }
        if (activeCodes.isEmpty()) return MailboxExpertGroupListResponse(emptyList(), 0)

        if (accountCode != null && accountCode !in activeCodes) {
            return MailboxExpertGroupListResponse(emptyList(), 0)
        }

        val offset = page.toLong() * size
        val onlyPending = if (pending) 1 else 0
        val total = mailRecordRepository.countMailboxExperts(
            accountCodes = activeCodes,
            direction = direction,
            accountCode = accountCode,
            keyword = keyword,
            recipientEmail = recipientEmail,
            startTime = startTime,
            endTime = endTime,
            onlyPending = onlyPending,
            tag = tag,
            expertContactId = expertContactId
        )
        if (total == 0L) {
            return MailboxExpertGroupListResponse(emptyList(), 0)
        }

        val summaries = mailRecordRepository.listMailboxExpertSummaries(
            accountCodes = activeCodes,
            direction = direction,
            accountCode = accountCode,
            keyword = keyword,
            recipientEmail = recipientEmail,
            startTime = startTime,
            endTime = endTime,
            onlyPending = onlyPending,
            tag = tag,
            limit = size,
            offset = offset,
            expertContactId = expertContactId
        )
        if (summaries.isEmpty()) {
            return MailboxExpertGroupListResponse(emptyList(), total)
        }

        val expertContactIds = summaries.map { it.expertContactId }
        val mailRows = mailRecordRepository.listMailboxByExpertContactIds(
            expertContactIds = expertContactIds,
            accountCodes = activeCodes,
            direction = direction,
            accountCode = accountCode,
            keyword = keyword,
            recipientEmail = recipientEmail,
            startTime = startTime,
            endTime = endTime,
            onlyPending = onlyPending,
            tag = tag
        )
        val inboundIds = mailRows.mapNotNull { it.inboundProcessingId }
        val inboundTagsById = inboundMailTagService.listTagsBatch(inboundIds)
        val mailsByExpert = mailRows
            .map { row -> toMailboxItemResponse(row, inboundTagsById) }
            .groupBy { it.expertContactId }

        val groups = summaries.map { summary ->
            MailboxExpertGroupResponse(
                expertContactId = summary.expertContactId,
                expertName = summary.expertName,
                expertEmail = summary.expertEmail,
                expertOrcidId = summary.orcidId,
                operatorStatus = summary.operatorStatus,
                expertIndexLevel = summary.currentIndexLevel,
                mailCount = summary.mailCount,
                pendingCount = summary.pendingCount,
                receivedCount = summary.receivedCount,
                sentCount = summary.sentCount,
                failedCount = summary.failedCount,
                mails = mailsByExpert[summary.expertContactId] ?: emptyList()
            )
        }

        return MailboxExpertGroupListResponse(groups, total)
    }

    /**
     * I2b-3（B4）：按 task_execution_id 过滤的收发件箱查询。复用既有行装配
     * （MailboxRow 投影字段 → [toMailboxItemResponse] → [computeTags]，同一 DTO、同一标签计算），
     * 只在数据来源上多一个 WHERE（仓库派生查询）。不 join task_execution（I2b-4：
     * P3 保留清理删除执行行后，悬垂 id 仍正常返回邮件）。
     *
     * 排序：`id ASC`（仓库派生查询命名，同一批次的发送顺序）。
     * 刻意置于 listByExpert 之后：避免位移行号钉死的守卫测试（OperatorStatusWriteSeamGuardTest）。
     */
    fun listByTaskExecution(taskExecutionId: Long): MailboxListResponse {
        val items = mailRecordRepository.findAllByTaskExecutionIdOrderByIdAsc(taskExecutionId)
            .map { record -> toMailboxItemResponse(toMailboxRow(record), emptyMap()) }
        return MailboxListResponse(items, items.size.toLong())
    }

    /**
     * I2b-3：把 MailRecord 实体转换为与 listMailbox SQL 投影（MAIL_RECORD 分支）同形的
     * [MailboxRow]，使两条路径共用同一 DTO 装配。字段取值逐一对齐 UNION 投影的
     * SELECT 列表（source / id / expert_contact_id / direction / mail_type /
     * sender_account_code / triggered_by / matched_qa_rule_id / subject /
     * SUBSTRING(COALESCE(cleaned_body, body), 1, 200) / sent_at / NULL 列 /
     * LEFT JOIN expert_contact / EXISTS(mail_attachment)）。
     */
    private fun toMailboxRow(record: MailRecord): MailboxRow {
        val recordId = record.id
        val contact = expertContactRepository.findById(record.expertContactId).orElse(null)
        val hasAttachment = recordId != null &&
            mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(recordId).isNotEmpty()
        return MailboxRow(
            source = "MAIL_RECORD",
            id = recordId ?: 0L,
            expertContactId = record.expertContactId,
            direction = record.direction,
            mailType = record.mailType,
            senderAccountCode = record.senderAccountCode,
            triggeredBy = record.triggeredBy,
            matchedQaRuleId = record.matchedQaRuleId,
            subject = record.subject,
            bodyPreview = (record.cleanedBody ?: record.body)?.take(200),
            sendStatus = record.sendStatus,
            sentAt = record.sentAt,
            receivedAt = null,
            processStatus = null,
            reasonType = null,
            expertEmail = contact?.expertEmail,
            expertName = contact?.expertName,
            hasAttachment = if (hasAttachment) 1L else 0L,
            inboundProcessingId = null
        )
    }

    private fun toMailboxItemResponse(
        row: MailboxRow,
        inboundTagsById: Map<Long, List<TagView>>
    ): MailboxItemResponse {
        val timestamp = (row.sentAt ?: row.receivedAt)?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        return MailboxItemResponse(
            id = row.id,
            source = row.source,
            expertContactId = row.expertContactId,
            direction = row.direction,
            mailType = row.mailType,
            senderAccountCode = row.senderAccountCode,
            triggeredBy = row.triggeredBy,
            isSystemSent = row.triggeredBy == "SYSTEM",
            expertEmail = row.expertEmail,
            expertName = row.expertName,
            subject = row.subject,
            bodyPreview = row.bodyPreview,
            hasAttachment = row.hasAttachment != 0L,
            sendStatus = row.sendStatus,
            timestamp = timestamp,
            tags = computeTags(row),
            processStatus = row.processStatus,
            reasonType = row.reasonType,
            inboundProcessingId = row.inboundProcessingId,
            inboundTags = row.inboundProcessingId?.let { inboundTagsById[it] } ?: emptyList()
        )
    }

    fun getMailboxDetail(source: String, id: Long): MailboxDetailResponse {
        return when (source) {
            "MAIL_RECORD" -> {
                val record = mailRecordRepository.findByIdOrNull(id)
                    ?: throw NoSuchElementException("Mail record not found: $id")
                toDetailFromMailRecord(record)
            }
            "INBOUND_PROCESSING" -> {
                val record = inboundMailProcessingRepository.findById(id)
                    .orElseThrow { NoSuchElementException("Inbound mail processing not found: $id") }
                toDetailFromInbound(record)
            }
            else -> throw IllegalArgumentException("Unknown mailbox source: $source")
        }
    }

    fun resolveAttachments(source: String, id: Long): List<MailAttachment> {
        return when (source) {
            "MAIL_RECORD" -> mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(id)
            "INBOUND_PROCESSING" -> {
                val byInbound = mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(id)
                if (byInbound.isNotEmpty()) {
                    return byInbound
                }
                val inbound = inboundMailProcessingRepository.findById(id).orElse(null)
                    ?: return emptyList()
                val mailRecord = inbound.messageId?.let {
                    mailRecordRepository.findFirstByMessageIdOrderByCreatedAtDesc(it)
                } ?: return emptyList()
                val mailRecordId = mailRecord.id ?: return emptyList()
                mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(mailRecordId)
            }
            else -> throw IllegalArgumentException("Unknown mailbox source: $source")
        }
    }

    private fun toDetailFromMailRecord(record: MailRecord): MailboxDetailResponse {
        val recordId = record.id ?: throw IllegalStateException("Mail record id is null")
        val body = record.cleanedBody ?: record.body
        val contact = expertContactRepository.findById(record.expertContactId).orElse(null)
        val timestamp = (record.sentAt ?: record.receivedAt)?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val bodyPreview = body?.take(200)
        return MailboxDetailResponse(
            id = recordId,
            source = "MAIL_RECORD",
            expertContactId = record.expertContactId,
            direction = record.direction,
            mailType = record.mailType,
            senderAccountCode = record.senderAccountCode,
            triggeredBy = record.triggeredBy,
            isSystemSent = record.triggeredBy == "SYSTEM",
            expertEmail = contact?.expertEmail,
            expertName = contact?.expertName,
            subject = record.subject,
            bodyPreview = bodyPreview,
            body = body,
            hasAttachment = resolveAttachments("MAIL_RECORD", recordId).isNotEmpty(),
            sendStatus = record.sendStatus,
            timestamp = timestamp,
            processStatus = null,
            reasonType = null,
            inboundProcessingId = null,
            inboundTags = emptyList(),
            expertOrcidId = contact?.orcidId,
            expertIndexLevel = contact?.currentIndexLevel
        )
    }

    private fun toDetailFromInbound(record: InboundMailProcessing): MailboxDetailResponse {
        val recordId = record.id ?: throw IllegalStateException("Inbound mail processing id is null")
        val body = record.cleanedBody ?: record.body
        val contact = record.expertContactId?.let { expertContactRepository.findById(it).orElse(null) }
        val timestamp = record.receivedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val bodyPreview = body?.take(200)
        val hasAttachment = resolveAttachments("INBOUND_PROCESSING", recordId).isNotEmpty()
        return MailboxDetailResponse(
            id = recordId,
            source = "INBOUND_PROCESSING",
            expertContactId = record.expertContactId,
            direction = "INBOUND",
            mailType = "REPLY",
            senderAccountCode = record.senderAccountCode,
            triggeredBy = null,
            isSystemSent = false,
            expertEmail = contact?.expertEmail ?: record.fromEmail,
            expertName = contact?.expertName,
            subject = record.subject,
            bodyPreview = bodyPreview,
            body = body,
            hasAttachment = hasAttachment,
            sendStatus = null,
            timestamp = timestamp,
            processStatus = record.processStatus,
            reasonType = record.reasonType,
            inboundProcessingId = recordId,
            inboundTags = inboundMailTagService.listTags(recordId),
            expertOrcidId = contact?.orcidId,
            expertIndexLevel = contact?.currentIndexLevel
        )
    }

    internal fun computeTags(row: MailboxRow): List<String> {
        val tags = mutableListOf<String>()
        if (row.expertContactId != null) {
            tags.add("专家")
        } else {
            tags.add("待匹配")
        }
        if (row.direction == "INBOUND") {
            tags.add("收件")
        } else {
            tags.add("发件")
        }
        if (row.direction == "OUTBOUND") {
            if (row.triggeredBy == "SYSTEM" ||
                row.matchedQaRuleId != null ||
                row.mailType in AUTO_REPLY_MAIL_TYPES
            ) {
                tags.add("自动回复")
            }
            if (row.triggeredBy in MANUAL_TRIGGER_TYPES || row.mailType == "MANUAL_QA_REPLY") {
                tags.add("手动回复")
            }
            if (row.mailType == "INTRODUCTION") {
                tags.add("首发")
            }
        }
        if (row.source == "INBOUND_PROCESSING" && row.processStatus == "MANUAL_REVIEW") {
            tags.add("待处理")
        }
        return tags
    }

    companion object {
        private val AUTO_REPLY_MAIL_TYPES = setOf("QA_REPLY", "MEETING_INVITATION", "MEETING_CONFIRMATION")
        private val MANUAL_TRIGGER_TYPES = setOf("OPERATOR", "MANUAL")
    }
}
