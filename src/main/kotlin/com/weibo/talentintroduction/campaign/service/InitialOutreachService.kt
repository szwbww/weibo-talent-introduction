package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderExpertAssignment
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class InitialOutreachService(
    private val expertSearchService: ExpertSearchService,
    private val senderAccountAssignmentService: SenderAccountAssignmentService,
    private val introductionMailComposer: IntroductionMailComposer,
    private val mailDeliveryService: MailDeliveryService,
    private val expertContactRepository: ExpertContactRepository,
    private val txHelper: ManualOutreachTxHelper
) {
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
            val now = LocalDateTime.now()
            val contact = expertContactRepository.save(
                ExpertContact(
                    campaignId = campaignId,
                    orcidId = expert.orcidId,
                    expertEmail = expert.email.orEmpty(),
                    expertName = expert.displayName,
                    currentStatus = "NEW",
                    createdAt = now,
                    updatedAt = now
                )
            )

            val mail = introductionMailComposer.compose(account.accountCode, expert)
            val delivered = try {
                mailDeliveryService.send(account, mail)
            } catch (e: Exception) {
                sentResults += InitialOutreachSendResult(
                    orcidId = expert.orcidId,
                    expertEmail = expert.email.orEmpty(),
                    senderAccountCode = account.accountCode,
                    status = "FAILED"
                )
                assignments += SenderExpertAssignment(
                    accountCode = account.accountCode,
                    expertId = expert.orcidId,
                    distributionKey = expert.country?.lowercase()?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"
                )
                return@forEach
            }

            if (delivered.status == "SENT") {
                txHelper.recordSuccess(
                    contact = contact,
                    accountCode = account.accountCode,
                    deliveredMessageId = delivered.messageId,
                    subject = mail.subject,
                    body = mail.body,
                    attemptId = 0L
                )
            } else {
                txHelper.recordFailure(
                    contactId = contact.id ?: error("Saved expert contact id is null"),
                    accountCode = account.accountCode,
                    messageId = delivered.messageId,
                    errorSummary = delivered.errorDetail ?: delivered.status,
                    subject = mail.subject,
                    body = mail.body,
                    attemptId = null
                )
            }

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
            sent = sentResults.count { it.status == "SENT" },
            failed = sentResults.count { it.status != "SENT" },
            skipped = skipped,
            results = sentResults
        )
    }
}

data class InitialOutreachBatchResult(
    val requested: Int,
    val candidates: Int,
    val sent: Int,
    val failed: Int,
    val skipped: Int,
    val results: List<InitialOutreachSendResult>
)

data class InitialOutreachSendResult(
    val orcidId: String,
    val expertEmail: String,
    val senderAccountCode: String,
    val status: String
)
