package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
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
    private val senderAccountBindingService: SenderAccountBindingService,
    /** I-1/I-3: 发现准入的唯一来源（旧首发不再做机构/国家/凭证二次校验）。 */
    private val batchRecipientSelectionService: BatchRecipientSelectionService = BatchRecipientSelectionService(),
    /** I-1/I-2/I-3（08）：人物级选择层 —— 同一人物在首发目标里只计一次、只选一个地址。 */
    private val discoveryPersonRecipientService: DiscoveryPersonRecipientService =
        DiscoveryPersonRecipientService(batchRecipientSelectionService),
    /** 09 (I-1/I-2/I-3): 人物级首发原子门禁 —— 仅 discovered INTRODUCTION 使用。 */
    private val outreachGuardService: DiscoveryPersonOutreachGuardService? = null
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
        val experts = fetchSendableCandidates(size, ExpertIndexLevel.CANDIDATE, types)
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

            // I-1/I-3：发现准入与类型已由 fetchSendableCandidates 按统一 selector 判定，
            // 不再调用 matchesDiscoveryOutreach 做机构/国家/凭证二次拒绝。

            if (expertContactRepository.existsByCampaignIdAndOrcidId(campaignId, expert.orcidId)) {
                skipped += 1
                return@forEachIndexed
            }

            val email = expert.email
            if (email.isNullOrBlank() || emailSuppressionService.isSuppressed(email)) {
                skipped += 1
                return@forEachIndexed
            }

            // 09 (I-1/I-2/I-3): discovered INTRODUCTION 先占人物级首发许可 —— 未获许可绝不建 contact/发信。
            val guard = outreachGuardService
            var guardTicket: OutreachGuardTicket? = null
            if (guard != null && RecipientScope.isDiscoveryOutreach(expert)) {
                when (val outcome = guard.reserveIntroduction(
                    selectedDocId = BatchRecipientSelectionService.docIdOf(expert),
                    recipient = email,
                    profile = expert
                )) {
                    is OutreachReserveOutcome.Granted -> guardTicket = outcome.ticket
                    is OutreachReserveOutcome.Denied -> {
                        skipped += 1
                        return@forEachIndexed
                    }
                }
            }

            val account = try {
                senderAccountAssignmentService.selectAccount(expert, assignments, stock = stock)
            } catch (e: Exception) {
                // 未调用 SMTP，证明未交付 → 已持有的占位清理为可解释安全状态（原址可重试）。
                guardTicket?.let { guard?.markSafeRetry(it) }
                throw e
            }
            val now = LocalDateTime.now()
            val (boundCode, boundAt) = senderAccountBindingService
                .bindingFieldsFor(account.accountCode, now)
            val contact = try {
                expertContactRepository.save(
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
            } catch (e: Exception) {
                // 同上：建行失败未调用 SMTP，清理占位后保留原有中断语义。
                guardTicket?.let { guard?.markSafeRetry(it) }
                throw e
            }

            val mail = try {
                introductionMailComposer.compose(account.accountCode, expert)
            } catch (e: Exception) {
                // 未调用 SMTP，证明未交付 → 允许原址安全重试；保留原有中断语义。
                guardTicket?.let { guard?.markSafeRetry(it) }
                throw e
            }
            if (guardTicket != null) {
                when (val claim = guard!!.claimSending(guardTicket, expert, mail.to)) {
                    is OutreachClaimOutcome.Denied -> {
                        // 未取得交付权（revision 变化/地址不合格等）→ 未调用 SMTP，安全重试后跳过。
                        guard.markSafeRetry(guardTicket)
                        skipped += 1
                        return@forEachIndexed
                    }
                    else -> {}
                }
            }
            val delivered = try {
                mailDeliveryService.send(account, mail)
            } catch (e: Exception) {
                // SMTP 结果不明：不可重发。
                guardTicket?.let { guard?.markUnknown(it) }
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
                    contact = contact, accountCode = account.accountCode,
                    deliveredMessageId = delivered.messageId, subject = mail.subject,
                    body = mail.text ?: mail.body, attemptId = 0L,
                    taskExecutionId = taskExecutionId, openTrackingId = delivered.openTrackingId,
                    outreachGuardTicket = guardTicket
                )
            } else {
                // 只有 SMTP 明确未接受（有响应码）才证明未交付 → SAFE_RETRY；
                // 无响应码（网络/认证等）结果不明 → UNKNOWN，绝不据此重试。
                if (delivered.smtpResponseCode != null) {
                    guardTicket?.let { guard?.markSafeRetry(it) }
                } else {
                    guardTicket?.let { guard?.markUnknown(it) }
                }
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

    /**
     * I-1/I-3: 旧首发取目标时对 ES 粗筛页应用统一 selector（显式条件 + 04 持久准入）。
     * 被整页过滤时必须继续按 offset 取下一页，直到凑足 [size] 名合格候选或用尽 —— 不能先建联系人再判不合格。
     * 分类/邮箱/国家等粗筛仍由 ES 查询与内存既有门禁负责；发现准入只消费持久结论。
     */
    private fun fetchSendableCandidates(
        size: Int,
        level: ExpertIndexLevel,
        types: List<String>
    ): List<ExpertProfile> {
        val eligible = mutableListOf<ExpertProfile>()
        var offset = 0
        while (eligible.size < size) {
            val result = expertSearchService.searchExpertsByTypesWithEmail(size, level, types, offset)
            val raw = result.experts
            if (raw.isEmpty()) break
            offset += raw.size
            eligible += discoveryPersonRecipientService.selectAdmission(raw)
                .selectedDocIds
                .let { ids -> raw.filter { BatchRecipientSelectionService.docIdOf(it) in ids } }
            // I-3：页 offset 针对粗筛持续推进；只有短页或已越过 totalHits 才算数据耗尽。
            if (raw.size < size || offset >= result.totalHits) break
        }
        return eligible.take(size)
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
