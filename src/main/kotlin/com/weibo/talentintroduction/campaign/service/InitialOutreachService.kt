package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
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
        // I2-2: 未配置即快速失败，绝不退化成"不限"。
        val types = schedulingProperties.initialOutreachExpertTypes
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(types.isNotEmpty()) {
            "未配置 talent-introduction.scheduling.initial-outreach-expert-types，旧首发链路拒绝执行"
        }
        // I2-3: 白名单唯一权威在 ExpertSearchService。
        types.forEach {
            require(it in ExpertSearchService.ALLOWED_EXPERT_TYPES) { "Invalid expert type: $it" }
        }
        val experts = expertSearchService
            .searchExpertsByTypesWithEmail(size, ExpertIndexLevel.CANDIDATE, types).experts
        val assignments = mutableListOf<SenderExpertAssignment>()
        val stock = senderAccountAssignmentService.loadBindingStock()
        val sentResults = mutableListOf<InitialOutreachSendResult>()
        var skipped = 0

        experts.forEachIndexed { index, expert ->
            // I2-5：发送前内存门禁与查询同口径 —— UNCLASSIFIED = 分类对象/类型不存在；
            // 创建 contact 前再次检查，查询/缓存/未来重构错误可能绕过。
            val typeName = expert.expertClassification?.type?.name
            val matched = types.any { if (it == "UNCLASSIFIED") typeName == null else typeName == it }
            if (!matched) {
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
