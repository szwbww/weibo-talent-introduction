package com.weibo.talentintroduction.monitoring.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import com.weibo.talentintroduction.expert.domain.ExpertApplicationPromotion
import com.weibo.talentintroduction.expert.repository.ExpertApplicationPromotionRepository
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.CountryCohortStat
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.BounceRateMonitorService
import com.weibo.talentintroduction.mail.service.ProviderResolver
import com.weibo.talentintroduction.monitoring.controller.BounceStatsResponse
import com.weibo.talentintroduction.monitoring.controller.InboundListResponse
import com.weibo.talentintroduction.monitoring.controller.InboundRow
import com.weibo.talentintroduction.monitoring.controller.IntroductionListResponse
import com.weibo.talentintroduction.monitoring.controller.IntroductionRow
import com.weibo.talentintroduction.monitoring.controller.OutboundReplyListResponse
import com.weibo.talentintroduction.monitoring.controller.OutboundReplyRow
import com.weibo.talentintroduction.monitoring.controller.PromotionListResponse
import com.weibo.talentintroduction.monitoring.controller.PromotionRow
import com.weibo.talentintroduction.monitoring.controller.ProviderDistributionResponse
import com.weibo.talentintroduction.monitoring.controller.ProviderStatRow
import com.weibo.talentintroduction.monitoring.controller.RegionCountryRow
import com.weibo.talentintroduction.monitoring.controller.RegionStatRow
import com.weibo.talentintroduction.monitoring.controller.SenderAccountHealthRow
import com.weibo.talentintroduction.monitoring.controller.SourceInboundSummary
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
@Service
class MailMonitoringService(
    private val mailRecordRepository: MailRecordRepository,
    private val bounceRecordRepository: BounceRecordRepository,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val promotionRepository: ExpertApplicationPromotionRepository,
    private val mailSenderAccountRepository: MailSenderAccountRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val qaRuleRepository: QaRuleRepository,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val dateRangeResolver: MonitoringDateRangeResolver,
    private val providerResolver: ProviderResolver
) {
    data class DailySummary(
        val date: String,
        val from: String,
        val to: String,
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

    fun summary(fromDate: LocalDate?, toDate: LocalDate?): DailySummary {
        val (start, end) = dateRangeResolver.resolveRange(fromDate, toDate)
        val anchorDate = toDate ?: fromDate ?: dateRangeResolver.todayString()
        return DailySummary(
            date = anchorDate.toString(),
            from = start.toLocalDate().toString(),
            to = anchorDate.toString(),
            introductions = mailRecordRepository.countOutboundByMailTypeBetween("INTRODUCTION", start, end),
            inboundReplies = mailRecordRepository.countInboundBetween(start, end),
            repliedExperts = mailRecordRepository.countDistinctRepliedExpertsBetween(start, end),
            autoReplies = mailRecordRepository.countAutoRepliesBetween(start, end),
            operatorOutbound = mailRecordRepository.countOperatorOutboundBetween(start, end),
            meetingInvitations = mailRecordRepository.countOutboundByMailTypeBetween("MEETING_INVITATION", start, end),
            manualReviewInbound = inboundMailProcessingRepository.countManualReviewBetween(start, end),
            unmatchedInbound = inboundMailProcessingRepository.countUnmatchedBetween(start, end),
            failedOutbound = mailRecordRepository.countFailedOutboundBetween(start, end),
            applicationPromotions = promotionRepository.countByStatusAndCreatedAtBetween("SUCCESS", start, end)
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
                autoSendPaused = account.autoSendPaused,
                autoSendPausedReason = account.autoSendPausedReason,
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

    fun getBounceStats(accountCode: String, windowDays: Int = BounceRateMonitorService.DEFAULT_WINDOW_DAYS): BounceStatsResponse {
        val days = windowDays.coerceAtLeast(1)
        val since = java.time.LocalDateTime.now().minusDays(days.toLong())
        val hardBounces = bounceRecordRepository.countHardBouncesSince(accountCode, since)
        val softBounces = bounceRecordRepository.countSoftBouncesSince(accountCode, since)
        val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)
        val bounceRate = if (sentCount > 0) hardBounces.toDouble() / sentCount else 0.0
        return BounceStatsResponse(
            accountCode = accountCode,
            windowDays = days,
            hardBounceCount = hardBounces,
            softBounceCount = softBounces,
            sentCount = sentCount,
            bounceRate = bounceRate
        )
    }

    fun providerDistribution(fromDate: LocalDate?, toDate: LocalDate?): ProviderDistributionResponse {
        val (start, end) = dateRangeResolver.resolveRange(fromDate, toDate)
        val matureBefore = LocalDateTime.now().minusDays(MATURITY_DAYS)
        val stats = PROVIDER_ORDER.associateWith { MutableProviderStats() }.toMutableMap()

        mailRecordRepository.aggregateIntroCohortByDomain(start, end, matureBefore).forEach { row ->
            val provider = resolveProviderFromDomain(row.domain)
            val bucket = stats.getOrPut(provider) { MutableProviderStats() }
            bucket.sent += row.cohortCount
            bucket.replied += row.repliedCount
            bucket.matureCohort += row.matureCohortCount
            bucket.matureReplied += row.matureRepliedCount
        }
        // 未送达 = 发送失败 ∪ 被退回，按专家去重（UNION），分桶键是专家邮箱域名（I-1、I-3）
        mailRecordRepository.aggregateUndeliveredByDomain(start, end).forEach { row ->
            val provider = resolveProviderFromDomain(row.domain)
            val bucket = stats.getOrPut(provider) { MutableProviderStats() }
            bucket.undelivered += row.undeliveredCount
        }
        val unattributedBounceCount = bounceRecordRepository.countUnattributedBouncesBetween(start, end)

        return ProviderDistributionResponse(
            rows = PROVIDER_ORDER.map { provider ->
                val bucket = stats.getValue(provider)
                ProviderStatRow(
                    provider = provider,
                    sentCount = bucket.sent,
                    repliedCount = bucket.replied,
                    replyRate = ratio(bucket.replied, bucket.sent),
                    matureCohortCount = bucket.matureCohort,
                    matureRepliedCount = bucket.matureReplied,
                    matureReplyRate = ratio(bucket.matureReplied, bucket.matureCohort),
                    undeliveredCount = bucket.undelivered
                )
            },
            unattributedBounceCount = unattributedBounceCount
        )
    }

    fun regionDistribution(fromDate: LocalDate?, toDate: LocalDate?): List<RegionStatRow> {
        val (start, end) = dateRangeResolver.resolveRange(fromDate, toDate)
        val matureBefore = LocalDateTime.now().minusDays(MATURITY_DAYS)
        val countryRows = mailRecordRepository.aggregateIntroCohortByCountry(start, end, matureBefore)
            .map { row -> row.toRegionCountryRow() }
        val promotionByRegion = promotionRepository.aggregateSuccessByCountry(start, end)
            .groupBy { CountryContinentMapping.toRegion(it.country) }
            .mapValues { (_, rows) -> rows.sumOf { it.count } }
        val countriesByRegion = countryRows.groupBy { CountryContinentMapping.toRegion(it.country) }

        return CountryContinentMapping.allRegions().map { region ->
            val countries = countriesByRegion[region].orEmpty().sortedByDescending { it.sentCount }
            val sent = countries.sumOf { it.sentCount }
            val replied = countries.sumOf { it.repliedCount }
            val matureCohort = countries.sumOf { it.matureCohortCount }
            val matureReplied = countries.sumOf { it.matureRepliedCount }
            RegionStatRow(
                region = region,
                sentCount = sent,
                repliedCount = replied,
                replyRate = ratio(replied, sent),
                matureCohortCount = matureCohort,
                matureRepliedCount = matureReplied,
                matureReplyRate = ratio(matureReplied, matureCohort),
                promotionCount = promotionByRegion[region] ?: 0,
                countries = countries
            )
        }
    }

    // 队列成员的国家展示行：country 为空/空白时显示「未知」；计数即该国家的队列口径四元组。
    private fun CountryCohortStat.toRegionCountryRow(): RegionCountryRow {
        val displayCountry = country?.trim()?.takeIf { it.isNotBlank() } ?: "未知"
        return RegionCountryRow(
            country = displayCountry,
            sentCount = cohortCount,
            repliedCount = repliedCount,
            replyRate = ratio(repliedCount, cohortCount),
            matureCohortCount = matureCohortCount,
            matureRepliedCount = matureRepliedCount,
            matureReplyRate = ratio(matureRepliedCount, matureCohortCount)
        )
    }

    private fun resolveProviderFromDomain(domain: String?): String =
        providerResolver.resolve(
            domain?.trim()?.takeIf { it.isNotBlank() }?.let { "x@$it" }
        )

    private fun ratio(numerator: Long, denominator: Long): Double =
        if (denominator > 0) numerator.toDouble() / denominator else 0.0

    private data class MutableProviderStats(
        var sent: Long = 0,
        var replied: Long = 0,
        var matureCohort: Long = 0,
        var matureReplied: Long = 0,
        var undelivered: Long = 0
    )

    companion object {
        private val PROVIDER_ORDER = listOf("gmail", "outlook", "yahoo", "edu", "tencent", "netease", "other")

        // 7 日成熟口径天数：与 MailRecordRepository 两条队列 SQL 的 INTERVAL 7 DAY 是同一常量（I-4）。
        const val MATURITY_DAYS = 7L
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
