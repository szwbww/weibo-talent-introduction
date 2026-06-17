package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.repository.CampaignRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.config.ManualOutreachProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.NoAvailableSenderAccountException
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderExpertAssignment
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class ManualInitialOutreachService(
    private val expertSearchService: ExpertSearchService,
    private val senderAccountAssignmentService: SenderAccountAssignmentService,
    private val introductionMailComposer: IntroductionMailComposer,
    private val mailDeliveryService: MailDeliveryService,
    private val expertContactRepository: ExpertContactRepository,
    private val campaignRepository: CampaignRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val mailSenderAccountRepository: MailSenderAccountRepository,
    private val mailSendAttemptRepository: MailSendAttemptRepository,
    private val progressStore: TaskProgressStore,
    private val properties: ManualOutreachProperties,
    private val txHelper: ManualOutreachTxHelper
) {
    private val log = LoggerFactory.getLogger(ManualInitialOutreachService::class.java)

    /**
     * Count experts pending outreach: new candidates from ES + retryable contacts (NEW status without SENT mail record).
     */
    fun countPending(): PendingOutreachSummary {
        var retryable = 0

        // 1. Count retryable: NEW contacts in MANUAL_OUTREACH campaign without a SENT introduction
        val campaign = campaignRepository.findByCampaignCode("MANUAL_OUTREACH")
        if (campaign != null) {
            val campaignId = campaign.id ?: error("Campaign ID is null")
            val newContacts = expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW")
            val seenRetryableOrcids = mutableSetOf<String>()
            for (contact in newContacts) {
                val normOrcid = normalizeOrcid(contact.orcidId)
                if (seenRetryableOrcids.add(normOrcid)) {
                    // Only retryable if no SENT introduction exists
                    val hasSentIntro = hasSentIntroduction(contact.id!!)
                    if (!hasSentIntro) {
                        retryable++
                    }
                }
            }
        }

        // 2. Pending: ES count query, operatorStatus does not exist + has email
        val pending = expertSearchService.countExperts(
            level = ExpertIndexLevel.CANDIDATE,
            filters = ExpertSearchService.notContactedWithEmailFilters()
        )

        return PendingOutreachSummary(pending = pending.toInt(), retryable = retryable, totalSendable = pending.toInt() + retryable)
    }

    fun runBulkOutreach(executionId: Long): ManualOutreachResult {
        log.info("Starting manual bulk outreach, executionId={}", executionId)
        val campaign = getOrCreateManualCampaign()
        val campaignId = campaign.id ?: error("Campaign ID is null")

        val snapshot = buildSnapshot(campaignId)
        val totalCount = snapshot.size
        log.info("Outreach snapshot: {} targets", totalCount)

        if (totalCount == 0) {
            updateProgress(executionId, 0, 0, 0, 0, 0, totalCount, "COMPLETED", "没有需要发送的专家", emptyList())
            return ManualOutreachResult(total = 0, sent = 0, failed = 0, skippedNoAccount = 0, wasCancelled = false)
        }

        var sentCount = 0
        var failedCount = 0
        var wasCancelled = false
        val errors = mutableListOf<String>()
        val assignments = mutableListOf<SenderExpertAssignment>()

        for ((index, target) in snapshot.withIndex()) {
            val (existingContact, expert) = target

            // Check cancellation
            if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                log.info("Cancelled at {}/{}", index, totalCount)
                wasCancelled = true
                break
            }

            val normOrcid = normalizeOrcid(expert.orcidId)

            // Select account
            val account = try {
                senderAccountAssignmentService.selectAccount(expert, assignments)
            } catch (e: NoAvailableSenderAccountException) {
                val processed = sentCount + failedCount
                val remaining = totalCount - processed
                updateProgress(executionId, sentCount, failedCount, processed, remaining, totalCount, totalCount,
                    "COMPLETED", "发件账号今日额度耗尽，已停止；可在账号页重置后继续", errors)
                return ManualOutreachResult(total = totalCount, sent = sentCount, failed = failedCount,
                    skippedNoAccount = remaining, wasCancelled = false, stopReason = "NO_CAPACITY", remaining = remaining)
            } catch (e: Exception) {
                log.error("System error selecting account", e)
                val processed = sentCount + failedCount
                updateProgress(executionId, sentCount, failedCount, processed, totalCount - processed, totalCount, totalCount,
                    "FAILED", "系统错误: ${e.message}", errors + (e.message ?: "Unknown error"))
                return ManualOutreachResult(total = totalCount, sent = sentCount, failed = failedCount,
                    skippedNoAccount = 0, wasCancelled = false, finalStatus = "FAILED", remaining = totalCount - processed)
            }

            try {
                // 1. Create or reuse contact (occupy the slot)
                val contact = existingContact ?: run {
                    val now = LocalDateTime.now()
                    expertContactRepository.save(ExpertContact(
                        campaignId = campaignId, orcidId = normOrcid,
                        expertEmail = expert.email.orEmpty(), expertName = expert.displayName,
                        currentStatus = "NEW", operatorStatus = "NOT_CONTACTED",
                        createdAt = now, updatedAt = now
                    ))
                }

                // 2. Double-check: skip if SENT introduction already exists (anti-duplicate)
                if (hasSentIntroduction(contact.id!!)) {
                    log.info("SENT introduction already exists for contact {}, skipping", contact.id)
                    continue
                }

                // 3. Compose mail
                val messageId = "<manual-outreach-${normOrcid}-${UUID.randomUUID()}@weibo.com>"
                val mail = introductionMailComposer.compose(account.accountCode, expert).copy(messageId = messageId)

                // 4. Persist attempt as PREPARED (audit trail) — upsert to respect UNIQUE(orcid_id, mail_type)
                val now = LocalDateTime.now()
                val existingAttempt = mailSendAttemptRepository.findByOrcidIdAndMailType(normOrcid, "INTRODUCTION")
                val attempt = mailSendAttemptRepository.save(
                    if (existingAttempt != null) {
                        existingAttempt.copy(
                            accountCode = account.accountCode, messageId = messageId,
                            status = MailSendAttemptStatus.PREPARED, errorSummary = null,
                            updatedAt = now
                        )
                    } else {
                        MailSendAttempt(
                            orcidId = normOrcid, mailType = "INTRODUCTION",
                            accountCode = account.accountCode, messageId = messageId,
                            status = MailSendAttemptStatus.PREPARED,
                            createdAt = now, updatedAt = now
                        )
                    }
                )

                // 5. Send via SMTP
                val delivered = mailDeliveryService.send(account, mail)
                if (delivered.status == "SENT") {
                    // 6. Record success atomically
                    txHelper.recordSuccess(
                        contact = contact, accountCode = account.accountCode,
                        deliveredMessageId = messageId, subject = mail.subject,
                        body = mail.body, attemptId = attempt.id!!
                    )
                    sentCount++
                } else {
                    // Non-SENT delivery status
                    txHelper.recordFailure(
                        contactId = contact.id, accountCode = account.accountCode,
                        messageId = messageId, errorSummary = "Delivery status: ${delivered.status}",
                        subject = mail.subject, body = mail.body, attemptId = attempt.id
                    )
                    failedCount++
                    errors.add("发送失败 (${expert.email}): ${delivered.status}")
                    if (errors.size > 20) errors.removeAt(0)
                }
            } catch (e: Exception) {
                log.error("Failed to process ORCID: {}", normOrcid, e)
                failedCount++
                errors.add("发送异常 (${expert.email}): ${e.message ?: "Unknown error"}")
                if (errors.size > 20) errors.removeAt(0)
                // Continue to next expert — don't stop the whole task
            }

            // Track assignment for account balancing
            assignments.add(SenderExpertAssignment(
                accountCode = account.accountCode, expertId = normOrcid,
                distributionKey = expert.country?.lowercase()?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"
            ))

            // Update progress
            val processed = sentCount + failedCount
            updateProgress(executionId, sentCount, failedCount, processed, totalCount - processed, totalCount, totalCount,
                "RUNNING", "正在发送：${expert.email}", errors)

            // Throttle
            if (properties.sendIntervalMs > 0 && index < totalCount - 1) {
                try { Thread.sleep(properties.sendIntervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
        }

        // Final progress
        val finalStatus = if (wasCancelled) "CANCELLED" else "COMPLETED"
        val finalMessage = if (wasCancelled) "发送任务已被取消" else "发送任务已完成"
        val finalProcessed = sentCount + failedCount
        updateProgress(executionId, sentCount, failedCount, finalProcessed, totalCount - finalProcessed, totalCount, totalCount,
            finalStatus, finalMessage, errors)

        return ManualOutreachResult(total = totalCount, sent = sentCount, failed = failedCount,
            skippedNoAccount = 0, wasCancelled = wasCancelled, remaining = totalCount - finalProcessed)
    }

    // ──── Private helpers ────

    private fun normalizeOrcid(orcid: String) = orcid.trim().uppercase()

    /** Check if a SENT outbound INTRODUCTION mail record exists for this contact. */
    private fun hasSentIntroduction(contactId: Long): Boolean {
        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
        return records.any { it.direction == "OUTBOUND" && it.mailType == "INTRODUCTION" && it.sendStatus == "SENT" }
    }

    private fun buildSnapshot(campaignId: Long): List<Pair<ExpertContact?, ExpertProfile>> {
        val seenOrcids = mutableSetOf<String>()
        val snapshot = mutableListOf<Pair<ExpertContact?, ExpertProfile>>()

        // 1. Retryable contacts: NEW status without SENT introduction
        val newContacts = expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW")
        if (newContacts.isNotEmpty()) {
            val retryableContacts = newContacts.filter { !hasSentIntroduction(it.id!!) }
            val orcidIds = retryableContacts.map { it.orcidId }
            val profiles = if (orcidIds.isNotEmpty()) expertSearchService.searchByOrcidIds(orcidIds) else emptyList()
            val profileMap = profiles.associateBy { normalizeOrcid(it.orcidId) }
            for (contact in retryableContacts) {
                val normOrcid = normalizeOrcid(contact.orcidId)
                val profile = profileMap[normOrcid]
                if (profile != null && seenOrcids.add(normOrcid)) {
                    snapshot.add(Pair(contact, profile))
                }
            }
        }

        // 2. New candidates from ES: scroll ES, only take documents where operatorStatus doesn't exist
        expertSearchService.scrollExpertsFiltered(
            level = ExpertIndexLevel.CANDIDATE,
            filters = ExpertSearchService.notContactedWithEmailFilters()
        ) { batch ->
            for (expert in batch) {
                val normOrcid = normalizeOrcid(expert.orcidId)
                if (seenOrcids.add(normOrcid)) {
                    snapshot.add(Pair(null, expert))
                }
            }
            true
        }

        log.info("Snapshot: {} retryable, {} new, {} total",
            snapshot.count { it.first != null }, snapshot.count { it.first == null }, snapshot.size)
        return snapshot
    }

    private fun getOrCreateManualCampaign(): Campaign {
        val existing = campaignRepository.findByCampaignCode("MANUAL_OUTREACH")
        if (existing != null) return existing

        val enabledAccounts = mailSenderAccountRepository.findAllByEnabledTrue()
            .filter { it.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE }
        if (enabledAccounts.isEmpty()) error("No enabled real mail accounts available to create campaign")

        val now = LocalDateTime.now()
        return campaignRepository.save(Campaign(
            campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach Campaign",
            description = "Created automatically for manual bulk outreach",
            status = "ACTIVE", senderAccountId = enabledAccounts.first().id ?: error("Account ID is null"),
            createdAt = now, updatedAt = now
        ))
    }

    private fun updateProgress(
        executionId: Long, sent: Int, failed: Int, processed: Int, remaining: Int,
        total: Int, totalCount: Int, status: String, message: String, errors: List<String>
    ) {
        progressStore.update("MANUAL_INITIAL_OUTREACH", TaskProgress(
            taskType = "MANUAL_INITIAL_OUTREACH", status = status,
            batchNumber = processed, processedCount = processed.toLong(), totalCount = totalCount.toLong(),
            message = message,
            details = mapOf("pending" to remaining, "sent" to sent, "failed" to failed),
            errors = errors.toList(), batchPassed = sent, batchRejected = failed,
            executionId = executionId
        ), executionId)
    }
}

data class PendingOutreachSummary(
    val pending: Int,
    val retryable: Int,
    val totalSendable: Int
)

data class ManualOutreachResult(
    val total: Int,
    val sent: Int,
    val failed: Int,
    val skippedNoAccount: Int,
    val wasCancelled: Boolean,
    val finalStatus: String? = null,
    val stopReason: String? = null,
    val remaining: Int = 0
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = sent
    override val taskFailureCount: Int get() = failed
    override val taskFinalStatus: String? get() = finalStatus ?: when {
        wasCancelled -> "CANCELLED"
        failed > 0 && sent > 0 -> "PARTIAL_SUCCESS"
        failed > 0 -> "FAILED"
        else -> "SUCCESS"
    }
}
