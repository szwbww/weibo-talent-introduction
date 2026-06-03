package com.weibo.talentintroduction.monitoring.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.ExpertApplicationPromotion
import com.weibo.talentintroduction.expert.repository.ExpertApplicationPromotionRepository
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.monitoring.controller.InboundListResponse
import com.weibo.talentintroduction.monitoring.controller.InboundRow
import com.weibo.talentintroduction.monitoring.controller.IntroductionListResponse
import com.weibo.talentintroduction.monitoring.controller.IntroductionRow
import com.weibo.talentintroduction.monitoring.controller.OutboundReplyListResponse
import com.weibo.talentintroduction.monitoring.controller.OutboundReplyRow
import com.weibo.talentintroduction.monitoring.controller.PromotionListResponse
import com.weibo.talentintroduction.monitoring.controller.PromotionRow
import com.weibo.talentintroduction.monitoring.controller.SenderAccountHealthRow
import com.weibo.talentintroduction.monitoring.controller.SourceInboundSummary
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class MailMonitoringService(
    private val mailRecordRepository: MailRecordRepository,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val promotionRepository: ExpertApplicationPromotionRepository,
    private val mailSenderAccountRepository: MailSenderAccountRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val qaRuleRepository: QaRuleRepository,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val dateRangeResolver: MonitoringDateRangeResolver
) {
    data class DailySummary(
        val date: String,
        val introductions: Long,
        val inboundReplies: Long,
        val repliedExperts: Long,
        val autoReplies: Long,
        val operatorOutbound: Long,
        val meetingInvitations: Long,
        val manualReviewInbound: Long,
        val unmatchedInbound: Long,
        val failedOutbound: Long,
        val applicationPromotions: Long
    )

    fun summary(date: LocalDate?): DailySummary {
        val (from, to) = dateRangeResolver.resolveDay(date)
        return DailySummary(
            date = date?.toString() ?: dateRangeResolver.todayString(),
            introductions = mailRecordRepository.countOutboundByMailTypeBetween("INTRODUCTION", from, to),
            inboundReplies = mailRecordRepository.countInboundBetween(from, to),
            repliedExperts = mailRecordRepository.countDistinctRepliedExpertsBetween(from, to),
            autoReplies = mailRecordRepository.countAutoRepliesBetween(from, to),
            operatorOutbound = mailRecordRepository.countOperatorOutboundBetween(from, to),
            meetingInvitations = mailRecordRepository.countOutboundByMailTypeBetween("MEETING_INVITATION", from, to),
            manualReviewInbound = inboundMailProcessingRepository.countManualReviewBetween(from, to),
            unmatchedInbound = inboundMailProcessingRepository.countUnmatchedBetween(from, to),
            failedOutbound = mailRecordRepository.countFailedOutboundBetween(from, to),
            applicationPromotions = promotionRepository.countByStatusAndCreatedAtBetween("SUCCESS", from, to)
        )
    }

    fun listIntroductions(
        from: LocalDate?,
        to: LocalDate?,
        senderAccountCode: String?,
        pageSize: Int,
        pageOffset: Int
    ): IntroductionListResponse {
        val (start, end) = dateRangeResolver.resolveRange(from, to)
        val records = mailRecordRepository.listIntroductions(start, end, senderAccountCode, pageSize.safeLimit(), pageOffset.safeOffset())
        val contacts = records.contactsById()
        return IntroductionListResponse(
            records = records.map { record ->
                val contact = contacts[record.expertContactId]
                IntroductionRow(
                    mailRecordId = record.id ?: 0,
                    sentAt = record.sentAt?.toString(),
                    expertContactId = record.expertContactId,
                    orcidId = contact?.orcidId,
                    expertName = contact?.expertName,
                    expertEmail = contact?.expertEmail,
                    campaignId = contact?.campaignId,
                    senderAccountCode = record.senderAccountCode,
                    subject = record.subject,
                    sendStatus = record.sendStatus,
                    contactCurrentStatus = contact?.currentStatus,
                    currentIndexLevel = contact?.currentIndexLevel,
                    replied = contact?.lastReplyAt != null
                )
            },
            totalCount = mailRecordRepository.countIntroductions(start, end, senderAccountCode)
        )
    }

    fun listOutboundReplies(
        from: LocalDate?,
        to: LocalDate?,
        triggeredBy: String?,
        mailType: String?,
        senderAccountCode: String?,
        sendStatus: String?,
        pageSize: Int,
        pageOffset: Int
    ): OutboundReplyListResponse {
        val (start, end) = dateRangeResolver.resolveRange(from, to)
        val records = mailRecordRepository.listOutboundReplies(
            start, end, triggeredBy, mailType, senderAccountCode, sendStatus, pageSize.safeLimit(), pageOffset.safeOffset()
        )
        val contacts = records.contactsById()
        val sourceInbound = records.mapNotNull { it.sourceInboundId }.distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { mailRecordRepository.findAllById(it).associateBy { record -> record.id } }
            ?: emptyMap()
        val qaRules = records.mapNotNull { it.matchedQaRuleId }.distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { qaRuleRepository.findAllById(it).associateBy { rule -> rule.id } }
            ?: emptyMap()

        return OutboundReplyListResponse(
            records = records.map { record ->
                val contact = contacts[record.expertContactId]
                val source = record.sourceInboundId?.let(sourceInbound::get)
                OutboundReplyRow(
                    mailRecordId = record.id ?: 0,
                    sentAt = record.sentAt?.toString(),
                    expertContactId = record.expertContactId,
                    orcidId = contact?.orcidId,
                    expertName = contact?.expertName,
                    expertEmail = contact?.expertEmail,
                    triggeredBy = record.triggeredBy ?: TriggeredBy.OPERATOR,
                    mailType = record.mailType,
                    senderAccountCode = record.senderAccountCode,
                    subject = record.subject,
                    body = record.body,
                    matchedQaRuleId = record.matchedQaRuleId,
                    matchedQaRuleDisplayName = record.matchedQaRuleId?.let { qaRules[it]?.displayName },
                    sendStatus = record.sendStatus,
                    sourceInbound = source?.let {
                        SourceInboundSummary(
                            mailRecordId = it.id ?: 0,
                            receivedAt = it.receivedAt?.toString(),
                            subject = it.subject
                        )
                    }
                )
            },
            totalCount = mailRecordRepository.countOutboundReplies(start, end, triggeredBy, mailType, senderAccountCode, sendStatus)
        )
    }

    fun listInbound(
        from: LocalDate?,
        to: LocalDate?,
        processStatus: String?,
        reasonType: String?,
        pageSize: Int,
        pageOffset: Int
    ): InboundListResponse {
        val (start, end) = dateRangeResolver.resolveRange(from, to)
        val records = inboundMailProcessingRepository.listInboundActivity(
            start, end, processStatus, reasonType, pageSize.safeLimit(), pageOffset.safeOffset()
        )
        val contacts = records.mapNotNull { it.expertContactId }.distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { expertContactRepository.findAllById(it).associateBy { contact -> contact.id } }
            ?: emptyMap()
        return InboundListResponse(
            records = records.map { record ->
                val contact = record.expertContactId?.let(contacts::get)
                InboundRow(
                    inboundProcessingId = record.id ?: 0,
                    receivedAt = record.receivedAt.toString(),
                    expertContactId = record.expertContactId,
                    orcidId = contact?.orcidId,
                    expertName = contact?.expertName,
                    fromEmail = record.fromEmail,
                    subject = record.subject,
                    cleanedBody = record.cleanedBody,
                    senderAccountCode = record.senderAccountCode,
                    processStatus = record.processStatus,
                    reasonType = record.reasonType,
                    intentCode = null,
                    intentConfidence = null,
                    contactCurrentStatus = contact?.currentStatus,
                    autoReplyEnabled = contact?.autoReplyEnabled,
                    needsManualAttention = contact?.needsManualAttention
                )
            },
            totalCount = inboundMailProcessingRepository.countInboundActivity(start, end, processStatus, reasonType)
        )
    }

    fun listPromotions(
        from: LocalDate?,
        to: LocalDate?,
        promotionStatus: String?,
        pageSize: Int,
        pageOffset: Int
    ): PromotionListResponse {
        val (start, end) = dateRangeResolver.resolveRange(from, to)
        val records = promotionRepository.list(promotionStatus, start, end, pageSize.safeLimit(), pageOffset.safeOffset())
        val contacts = records.map { it.expertContactId }.distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { expertContactRepository.findAllById(it).associateBy { contact -> contact.id } }
            ?: emptyMap()
        return PromotionListResponse(
            records = records.map { it.toRow(contacts[it.expertContactId]) },
            totalCount = promotionRepository.count(promotionStatus, start, end)
        )
    }

    fun retryPromotion(id: Long): PromotionRow {
        val promotion = expertIndexWriterService.retryFailedPromotion(id)
        val contact = expertContactRepository.findById(promotion.expertContactId).orElse(null)
        return promotion.toRow(contact)
    }

    fun senderAccountHealth(date: LocalDate?): List<SenderAccountHealthRow> {
        val (from, to) = dateRangeResolver.resolveDay(date)
        val stats = mailRecordRepository.aggregateSenderAccountStats(from, to).associateBy { it.senderAccountCode }
        val lastReceived = inboundMailProcessingRepository.findLastReceivedAtPerAccount()
            .associate { it.senderAccountCode to it.lastReceivedAt }
        return mailSenderAccountRepository.findAllByOrderByAccountCodeAsc().map { account ->
            val stat = stats[account.accountCode]
            SenderAccountHealthRow(
                accountCode = account.accountCode,
                senderEmail = account.senderEmail,
                enabled = account.enabled,
                todaySentCount = account.todaySentCount,
                dailySendLimit = account.dailySendLimit,
                introductionCount = stat?.introductionCount ?: 0,
                autoReplyCount = stat?.autoReplyCount ?: 0,
                failedCount = stat?.failedCount ?: 0,
                lastSentAt = stat?.lastSentAt?.toString() ?: account.lastSentAt?.toString(),
                lastReceivedAt = lastReceived[account.accountCode]?.toString()
            )
        }
    }

    private fun List<MailRecord>.contactsById(): Map<Long?, ExpertContact> =
        map { it.expertContactId }.distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { expertContactRepository.findAllById(it).associateBy { contact -> contact.id } }
            ?: emptyMap()

    private fun ExpertApplicationPromotion.toRow(contact: ExpertContact?): PromotionRow =
        PromotionRow(
            promotionId = id ?: 0,
            createdAt = createdAt?.toString(),
            updatedAt = updatedAt?.toString(),
            expertContactId = expertContactId,
            orcidId = orcidId,
            expertName = contact?.expertName,
            triggeredBy = triggeredBy,
            promotionStatus = promotionStatus,
            fromLevel = fromLevel,
            toLevel = toLevel,
            sourceInboundId = sourceInboundId,
            errorMessage = errorMessage,
            operatorName = operatorName
        )

    private fun Int.safeLimit(): Int = coerceIn(1, 100)

    private fun Int.safeOffset(): Int = coerceAtLeast(0)
}
