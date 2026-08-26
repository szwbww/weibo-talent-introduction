package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertClassificationService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.AutoReplySettingService
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderAccountBindingService
import com.weibo.talentintroduction.mail.service.SenderExpertAssignment
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.random.Random

@Service
class InitialOutreachService(
    private val expertSearchService: ExpertSearchService,
    private val senderAccountAssignmentService: SenderAccountAssignmentService,
    private val introductionMailComposer: IntroductionMailComposer,
    private val mailDeliveryService: MailDeliveryService,
    private val expertContactRepository: ExpertContactRepository,
    private val txHelper: ManualOutreachTxHelper,
    private val emailSuppressionService: EmailSuppressionService,
    private val autoReplySettingService: AutoReplySettingService,
    private val schedulingProperties: MailSchedulingProperties,
    private val senderAccountBindingService: SenderAccountBindingService
) {
    fun sendInitialBatch(campaignId: Long, size: Int, taskExecutionId: Long? = null): InitialOutreachBatchResult {
        val experts = expertSearchService.searchSendableExpertsWithEmail(size, ExpertIndexLevel.CANDIDATE).experts
        val assignments = mutableListOf<SenderExpertAssignment>()
        val stock = senderAccountAssignmentService.loadBindingStock()
        val sentResults = mutableListOf<InitialOutreachSendResult>()
        var skipped = 0

        experts.forEachIndexed { index, expert ->
            // I3-1/I3-4：发送前最后门禁 —— 查询/缓存/未来重构错误可能绕过硬门禁，创建 contact 前再次检查。
            // null/false/旧策略版本均拒绝，计 skipped，不创建 contact、不渲染、不投递。
            val classification = expert.expertClassification
            if (classification?.sendable != true ||
                classification.version !in ExpertClassificationService.ACCEPTED_CLASSIFICATION_VERSIONS
            ) {
                skipped += 1
                return@forEachIndexed
            }

            if (expertContactRepository.existsByCampaignIdAndOrcidId(campaignId, expert.orcidId)) {
                skipped += 1
                return@forEachIndexed
            }

            val email = expert.email
            if (email.isNullOrBlank() || emailSuppressionService.isSuppressed(email)) {
                skipped += 1
                return@forEachIndexed
            }

            val account = senderAccountAssignmentService.selectAccount(expert, assignments, stock = stock)
            val now = LocalDateTime.now()
            val (boundCode, boundAt) = senderAccountBindingService
                .bindingFieldsFor(account.accountCode, now)
            val contact = expertContactRepository.save(
                ExpertContact(
                    campaignId = campaignId,
                    orcidId = expert.orcidId,
                    expertEmail = expert.email.orEmpty(),
                    expertName = expert.displayName,
                    currentStatus = "NEW",
                    country = expert.country,
                    autoReplyEnabled = autoReplySettingService.isGlobalEnabled(),
                    boundSenderAccountCode = boundCode,
                    senderAccountBoundAt = boundAt,
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
                return@forEachIndexed
            }

            if (delivered.status == "SENT") {
                txHelper.recordSuccess(
                    contact = contact,
                    accountCode = account.accountCode,
                    deliveredMessageId = delivered.messageId,
                    subject = mail.subject,
                    body = mail.text ?: mail.body,
                    attemptId = 0L,
                    taskExecutionId = taskExecutionId
                )
            } else {
                txHelper.recordFailure(
                    contactId = contact.id ?: error("Saved expert contact id is null"),
                    accountCode = account.accountCode,
                    messageId = delivered.messageId,
                    errorSummary = delivered.errorDetail ?: delivered.status,
                    subject = mail.subject,
                    body = mail.text ?: mail.body,
                    attemptId = null,
                    taskExecutionId = taskExecutionId
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

            if (delivered.status == "SENT" && index < experts.lastIndex) {
                sleepBeforeNextSend()
            }
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

    private fun sleepBeforeNextSend() {
        val baseMs = schedulingProperties.initialOutreachSendIntervalMs
        val jitterMs = schedulingProperties.initialOutreachSendJitterMs
        if (baseMs <= 0 && jitterMs <= 0) return
        val jitter = if (jitterMs > 0) Random.nextLong(jitterMs) else 0L
        Thread.sleep(baseMs + jitter)
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
