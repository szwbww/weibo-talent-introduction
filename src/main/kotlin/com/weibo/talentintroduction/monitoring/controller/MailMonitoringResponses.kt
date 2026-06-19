package com.weibo.talentintroduction.monitoring.controller

data class IntroductionListResponse(
    val records: List<IntroductionRow>,
    val totalCount: Long
)

data class IntroductionRow(
    val mailRecordId: Long,
    val sentAt: String?,
    val expertContactId: Long,
    val orcidId: String?,
    val expertName: String?,
    val expertEmail: String?,
    val campaignId: Long?,
    val senderAccountCode: String?,
    val subject: String?,
    val sendStatus: String?,
    val contactCurrentStatus: String?,
    val currentIndexLevel: String?,
    val replied: Boolean
)

data class OutboundReplyListResponse(
    val records: List<OutboundReplyRow>,
    val totalCount: Long
)

data class OutboundReplyRow(
    val mailRecordId: Long,
    val sentAt: String?,
    val expertContactId: Long,
    val orcidId: String?,
    val expertName: String?,
    val expertEmail: String?,
    val triggeredBy: String,
    val mailType: String,
    val senderAccountCode: String?,
    val subject: String?,
    val body: String?,
    val matchedQaRuleId: Long?,
    val matchedQaRuleDisplayName: String?,
    val sendStatus: String?,
    val sourceInbound: SourceInboundSummary?
)

data class SourceInboundSummary(
    val mailRecordId: Long,
    val receivedAt: String?,
    val subject: String?
)

data class InboundListResponse(
    val records: List<InboundRow>,
    val totalCount: Long
)

data class InboundRow(
    val inboundProcessingId: Long,
    val receivedAt: String?,
    val expertContactId: Long?,
    val orcidId: String?,
    val expertName: String?,
    val fromEmail: String,
    val subject: String?,
    val cleanedBody: String?,
    val senderAccountCode: String,
    val processStatus: String,
    val reasonType: String?,
    val intentCode: String?,
    val intentConfidence: Int?,
    val contactCurrentStatus: String?,
    val autoReplyEnabled: Boolean?,
    val needsManualAttention: Boolean?
)

data class PromotionListResponse(
    val records: List<PromotionRow>,
    val totalCount: Long
)

data class PromotionRow(
    val promotionId: Long,
    val createdAt: String?,
    val updatedAt: String?,
    val expertContactId: Long,
    val orcidId: String,
    val expertName: String?,
    val triggeredBy: String,
    val promotionStatus: String,
    val fromLevel: String,
    val toLevel: String,
    val sourceInboundId: Long?,
    val errorMessage: String?,
    val operatorName: String?
)

data class SenderAccountHealthRow(
    val accountCode: String,
    val senderEmail: String,
    val enabled: Boolean,
    val todaySentCount: Int,
    val dailySendLimit: Int,
    val introductionCount: Long,
    val autoReplyCount: Long,
    val failedCount: Long,
    val lastSentAt: String?,
    val lastReceivedAt: String?
)

data class BounceStatsResponse(
    val accountCode: String,
    val windowDays: Int,
    val hardBounceCount: Long,
    val softBounceCount: Long,
    val sentCount: Long,
    val bounceRate: Double
)
