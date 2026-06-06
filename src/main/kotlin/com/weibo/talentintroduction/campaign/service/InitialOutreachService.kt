package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderExpertAssignment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class InitialOutreachService(
    private val expertSearchService: ExpertSearchService,
    private val senderAccountAssignmentService: SenderAccountAssignmentService,
    private val introductionMailComposer: IntroductionMailComposer,
    private val mailDeliveryService: MailDeliveryService,
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val mailSenderAccountRepository: MailSenderAccountRepository
) {
    @Transactional
    fun sendInitialBatch(campaignId: Long, size: Int): InitialOutreachBatchResult {
        val experts = expertSearchService.searchExpertsWithEmail(size, ExpertIndexLevel.CANDIDATE).experts
        val assignments = mutableListOf<SenderExpertAssignment>()
        val sentResults = mutableListOf<InitialOutreachSendResult>()
        var skipped = 0

        experts.forEach { expert ->
            if (expertContactRepository.existsByCampaignIdAndOrcidId(campaignId, expert.orcidId)) {
                skipped += 1
                return@forEach
            }

            val account = senderAccountAssignmentService.selectAccount(expert, assignments)
            val mail = introductionMailComposer.compose(account.accountCode, expert)
            val delivered = mailDeliveryService.send(account, mail)
            val now = LocalDateTime.now()

            val contact = expertContactRepository.save(
                ExpertContact(
                    campaignId = campaignId,
                    orcidId = expert.orcidId,
                    expertEmail = expert.email.orEmpty(),
                    expertName = expert.displayName,
                    currentStatus = ConversationStatus.INTRO_SENT.name,
                    lastMailAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )

            mailRecordRepository.save(
                MailRecord(
                    expertContactId = contact.id ?: error("Saved expert contact id is null"),
                    direction = "OUTBOUND",
                    mailType = "INTRODUCTION",
                    senderAccountCode = account.accountCode,
                    triggeredBy = TriggeredBy.SYSTEM,
                    sourceInboundId = null,
                    messageId = delivered.messageId,
                    inReplyTo = null,
                    subject = mail.subject,
                    body = mail.body,
                    matchedQaRuleId = null,
                    sendStatus = delivered.status,
                    receivedAt = null,
                    sentAt = now,
                    createdAt = now
                )
            )

            mailSenderAccountRepository.save(
                account.copy(
                    todaySentCount = account.todaySentCount + 1,
                    lastSentAt = now
                )
            )

            assignments += SenderExpertAssignment(
                accountCode = account.accountCode,
                expertId = expert.orcidId,
                distributionKey = expert.country?.lowercase()?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"
            )

            sentResults += InitialOutreachSendResult(
                orcidId = expert.orcidId,
                expertEmail = expert.email.orEmpty(),
                senderAccountCode = account.accountCode,
                status = delivered.status
            )
        }

        return InitialOutreachBatchResult(
            requested = size,
            candidates = experts.size,
            sent = sentResults.size,
            skipped = skipped,
            results = sentResults
        )
    }
}

data class InitialOutreachBatchResult(
    val requested: Int,
    val candidates: Int,
    val sent: Int,
    val skipped: Int,
    val results: List<InitialOutreachSendResult>
)

data class InitialOutreachSendResult(
    val orcidId: String,
    val expertEmail: String,
    val senderAccountCode: String,
    val status: String
)
