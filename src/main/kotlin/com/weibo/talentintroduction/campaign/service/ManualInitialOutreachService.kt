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
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.NoAvailableSenderAccountException
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderAccountSelfCheckService
import com.weibo.talentintroduction.mail.service.SenderExpertAssignment
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

enum class ExecutionMode { AUTO, MANUAL }

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
    private val txHelper: ManualOutreachTxHelper,
    private val batchSendSettingService: BatchSendSettingService,
    private val mailSenderAccountService: MailSenderAccountService,
    private val selfCheckService: SenderAccountSelfCheckService
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

    /**
     * Legacy single-pass entry point. Kept as a thin wrapper for backward compatibility with
     * the existing /manual-outreach/start endpoint and tests. Delegates to the round-based
     * scheduled batch engine in MANUAL mode, full run (not one-round-only).
     */
    fun runBulkOutreach(executionId: Long): ManualOutreachResult =
        runScheduledBatch(executionId, ExecutionMode.MANUAL, oneRoundOnly = false)

    /**
     * Round-based scheduled batch outreach (I-1/I-2/I-3/I-4/I-5/I-6/I-7/I-8, L3-1/L3-2).
     *
     * - Builds a deduplicated snapshot (I-7 preserved: ES operatorStatus filter, mail_send_attempt
     *   UNIQUE upsert, hasSentIntroduction double-check — all unchanged).
     * - Loops rounds: per-round gate (L3-1) → quota calc (I-6/L3-2) → send round → per-account
     *   progress (I-8) → optional round interval.
     * - oneRoundOnly=true (manual button) returns after one round; the control service maps the
     *   result back to PAUSED.
     * - Returns ManualOutreachResult with stopReason/finalStatus signalling flow-level outcomes
     *   (NO_AVAILABLE_ACCOUNT → PAUSED, DAILY_CAP_REACHED, etc.). The control service persists
     *   runtime status transitions based on these signals.
     */
    fun runScheduledBatch(
        executionId: Long,
        mode: ExecutionMode,
        oneRoundOnly: Boolean
    ): ManualOutreachResult {
        log.info("Starting scheduled batch outreach, executionId={}, mode={}, oneRoundOnly={}", executionId, mode, oneRoundOnly)
        val campaign = getOrCreateManualCampaign()
        val campaignId = campaign.id ?: error("Campaign ID is null")
        val config = batchSendSettingService.getConfig()

        val snapshot = buildSnapshot(campaignId)
        val totalCount = snapshot.size
        log.info("Outreach snapshot: {} targets; config: roundSize={}, dailyCap={}, perMailMs={}, perRoundMs={}",
            totalCount, config.roundSize, config.dailyCap, config.perMailIntervalMs, config.perRoundIntervalMs)

        if (totalCount == 0) {
            val emptyFinal = if (oneRoundOnly) "PAUSED" else "COMPLETED"
            val emptyReason = if (oneRoundOnly) "EMPTY_SNAPSHOT" else null
            updateProgress(executionId, 0, 0, 0, totalCount, 0, totalCount,
                emptyFinal, "没有需要发送的专家", emptyList(), mode, 0, config, emptyMap())
            return ManualOutreachResult(
                total = 0, sent = 0, failed = 0, skippedNoAccount = 0,
                wasCancelled = false, finalStatus = emptyFinal, stopReason = emptyReason
            )
        }

        var sentCount = 0
        var failedCount = 0
        var wasCancelled = false
        val errors = mutableListOf<String>()
        val assignments = mutableListOf<SenderExpertAssignment>()
        val runAccountStats = mutableMapOf<String, AccountRunStat>()
        var dailySentTotal = 0
        var roundNumber = 0
        var stopReason: String? = null
        var finalStatus: String? = null
        var processedTotal = 0
        var index = 0

        while (index < snapshot.size) {
            // 1. Cancellation check (I-1: single flow, operator pause → requestCancel)
            if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                log.info("Cancelled at index {}", index)
                wasCancelled = true
                stopReason = "CANCELLED"
                break
            }

            // 2. Round gate (L3-1): list sendable → self-check uncached → re-list sendable
            roundNumber++
            val sendable = runRoundGate()
            if (sendable.isEmpty()) {
                log.warn("No sendable accounts at round {}, pausing flow", roundNumber)
                stopReason = "NO_AVAILABLE_ACCOUNT"
                finalStatus = "PAUSED"
                break
            }

            // 3. Compute round quota (I-6/L3-2): min(roundSize, dailyCap remaining, snapshot remaining)
            val dailyCapRemaining = config.dailyCap - dailySentTotal
            val snapshotRemaining = snapshot.size - index
            val roundQuota = minOf(config.roundSize, dailyCapRemaining, snapshotRemaining)
            if (roundQuota <= 0) {
                log.info("Round quota exhausted at round {} (dailyCapRemaining={}, snapshotRemaining={})",
                    roundNumber, dailyCapRemaining, snapshotRemaining)
                // L3-2: dailyCap reached. oneRoundOnly → PAUSED; full run → COMPLETED (IDLE next day)
                if (oneRoundOnly) {
                    stopReason = "DAILY_CAP_REACHED"
                    finalStatus = "PAUSED"
                }
                // full run: just end (COMPLETED/IDLE)
                break
            }

            // 4. Send this round
            val roundEnd = index + roundQuota
            var midRoundStop = false
            while (index < roundEnd) {
                val target = snapshot[index]
                val (existingContact, expert) = target
                val normOrcid = normalizeOrcid(expert.orcidId)

                // Select account (I-3 predicate enforced inside selectAccount)
                val account = try {
                    senderAccountAssignmentService.selectAccount(expert, assignments)
                } catch (e: NoAvailableSenderAccountException) {
                    log.warn("No available sender account mid-round at index {}, pausing flow", index)
                    stopReason = "NO_AVAILABLE_ACCOUNT"
                    finalStatus = "PAUSED"
                    midRoundStop = true
                    break
                } catch (e: Exception) {
                    log.error("System error selecting account", e)
                    stopReason = "SYSTEM_ERROR"
                    finalStatus = "FAILED"
                    errors.add("系统错误: ${e.message ?: "Unknown error"}")
                    midRoundStop = true
                    break
                }

                val stat = runAccountStats.getOrPut(account.accountCode) { AccountRunStat() }

                try {
                    // 1. Create or reuse contact (occupy the slot) — I-7
                    val contact = existingContact ?: run {
                        val now = LocalDateTime.now()
                        expertContactRepository.save(ExpertContact(
                            campaignId = campaignId, orcidId = normOrcid,
                            expertEmail = expert.email.orEmpty(), expertName = expert.displayName,
                            currentStatus = "NEW", operatorStatus = "NOT_CONTACTED",
                            createdAt = now, updatedAt = now
                        ))
                    }

                    // 2. Double-check: skip if SENT introduction already exists (anti-duplicate, I-7)
                    if (hasSentIntroduction(contact.id!!)) {
                        log.info("SENT introduction already exists for contact {}, skipping", contact.id)
                        index++
                        continue
                    }

                    // 3. Compose mail
                    val messageId = "<manual-outreach-${normOrcid}-${UUID.randomUUID()}@weibo.com>"
                    val mail = introductionMailComposer.compose(account.accountCode, expert).copy(messageId = messageId)

                    // 4. Persist attempt as PREPARED (audit trail) — upsert to respect UNIQUE(orcid_id, mail_type) (I-7)
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
                        // 6. Record success atomically (state transition + mail_record + counter + attempt + ES) — I-7
                        txHelper.recordSuccess(
                            contact = contact, accountCode = account.accountCode,
                            deliveredMessageId = messageId, subject = mail.subject,
                            body = mail.body, attemptId = attempt.id!!
                        )
                        sentCount++
                        dailySentTotal++
                        stat.success++
                    } else {
                        // Non-SENT delivery status
                        txHelper.recordFailure(
                            contactId = contact.id, accountCode = account.accountCode,
                            messageId = messageId, errorSummary = "Delivery status: ${delivered.status}",
                            subject = mail.subject, body = mail.body, attemptId = attempt.id
                        )
                        failedCount++
                        stat.failed++
                        errors.add("发送失败 (${expert.email}): ${delivered.status}")
                        if (errors.size > 20) errors.removeAt(0)
                    }
                } catch (e: Exception) {
                    log.error("Failed to process ORCID: {}", normOrcid, e)
                    failedCount++
                    stat.failed++
                    errors.add("发送异常 (${expert.email}): ${e.message ?: "Unknown error"}")
                    if (errors.size > 20) errors.removeAt(0)
                    // Continue to next expert — don't stop the whole task
                }

                // Track assignment for account balancing
                assignments.add(SenderExpertAssignment(
                    accountCode = account.accountCode, expertId = normOrcid,
                    distributionKey = expert.country?.lowercase()?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"
                ))

                processedTotal++
                index++

                // Update progress (I-8: per-account stats)
                updateProgress(executionId, sentCount, failedCount, processedTotal,
                    snapshot.size - processedTotal, snapshot.size, snapshot.size,
                    "RUNNING", "正在发送：${expert.email}", errors, mode, roundNumber, config, runAccountStats)

                // Throttle per mail (I-6)
                if (config.perMailIntervalMs > 0 && index < roundEnd) {
                    try { Thread.sleep(config.perMailIntervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                }
            }

            if (midRoundStop) break

            // 5. Round end progress (I-8)
            updateProgress(executionId, sentCount, failedCount, processedTotal,
                snapshot.size - processedTotal, snapshot.size, snapshot.size,
                "RUNNING", "第${roundNumber}轮完成，已发送 $sentCount 封", errors, mode, roundNumber, config, runAccountStats)

            // 6. oneRoundOnly (manual button) — return after one round (L3-2: back to PAUSED)
            if (oneRoundOnly) {
                log.info("oneRoundOnly=true, returning after round {}", roundNumber)
                stopReason = "ONE_ROUND_DONE"
                finalStatus = "PAUSED"
                break
            }

            // 7. Round interval (I-6)
            if (config.perRoundIntervalMs > 0 && index < snapshot.size) {
                try { Thread.sleep(config.perRoundIntervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
        }

        // Resolve final status
        val resolvedFinalStatus = when {
            wasCancelled -> "CANCELLED"
            finalStatus != null -> finalStatus
            else -> "COMPLETED"
        }
        val finalMessage = when (resolvedFinalStatus) {
            "CANCELLED" -> "发送任务已被取消"
            "PAUSED" -> "流程已暂停: ${stopReason ?: ""}"
            "FAILED" -> "发送任务失败: ${stopReason ?: ""}"
            "COMPLETED" -> "发送任务已完成"
            else -> "发送任务结束"
        }
        updateProgress(executionId, sentCount, failedCount, processedTotal,
            snapshot.size - processedTotal, snapshot.size, snapshot.size,
            resolvedFinalStatus, finalMessage, errors, mode, roundNumber, config, runAccountStats)

        val skipped = if (stopReason == "NO_AVAILABLE_ACCOUNT") snapshot.size - processedTotal else 0
        return ManualOutreachResult(
            total = totalCount, sent = sentCount, failed = failedCount,
            skippedNoAccount = skipped, wasCancelled = wasCancelled,
            finalStatus = resolvedFinalStatus, stopReason = stopReason,
            remaining = snapshot.size - processedTotal
        )
    }

    // ──── Private helpers ────

    private fun normalizeOrcid(orcid: String) = orcid.trim().uppercase()

    /** Check if a SENT outbound INTRODUCTION mail record exists for this contact. */
    private fun hasSentIntroduction(contactId: Long): Boolean {
        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
        return records.any { it.direction == "OUTBOUND" && it.mailType == "INTRODUCTION" && it.sendStatus == "SENT" }
    }

    /**
     * Round gate (L3-1): list sendable accounts (I-3) → trigger self-check for each (I-4, cache-aware)
     * → re-list sendable (self-check may have paused failed accounts).
     */
    private fun runRoundGate(): List<MailSenderAccount> {
        val candidates = mailSenderAccountService.listSendableAccounts()
        if (candidates.isEmpty()) return emptyList()
        for (account in candidates) {
            selfCheckService.checkSendable(account)
        }
        return mailSenderAccountService.listSendableAccounts()
    }

    private fun buildSnapshot(campaignId: Long): List<Pair<ExpertContact?, ExpertProfile>> {
        val seenOrcids = mutableSetOf<String>()
        val snapshot = mutableListOf<Pair<ExpertContact?, ExpertProfile>>()

        // 1. Retryable contacts: NEW status without SENT introduction (I-7 preserved)
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

        // 2. New candidates from ES: scroll ES, only take documents where operatorStatus doesn't exist (I-7)
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

    private fun buildAccountStats(runAccountStats: Map<String, AccountRunStat>): List<AccountStatRow> {
        val allAccounts = mailSenderAccountService.listAccounts()
        return allAccounts.map { account ->
            val runStat = runAccountStats[account.accountCode]
            AccountStatRow(
                accountCode = account.accountCode,
                todaySent = account.todaySentCount,
                dailyLimit = account.dailySendLimit,
                success = runStat?.success ?: 0,
                failed = runStat?.failed ?: 0,
                paused = account.autoSendPaused,
                pauseReason = account.autoSendPausedReason
            )
        }
    }

    private fun updateProgress(
        executionId: Long, sent: Int, failed: Int, processed: Int,
        remaining: Int, total: Int, totalCount: Int,
        status: String, message: String, errors: List<String>,
        mode: ExecutionMode, roundNumber: Int,
        config: BatchSendConfig, runAccountStats: Map<String, AccountRunStat>
    ) {
        val details = mapOf(
            "executionMode" to mode.name,
            "status" to status,
            "roundNumber" to roundNumber,
            "dailyCap" to config.dailyCap,
            "dailySentTotal" to sent,
            "sentTotal" to sent,
            "failedTotal" to failed,
            "pending" to remaining,
            "sent" to sent,
            "failed" to failed,
            "accounts" to buildAccountStats(runAccountStats)
        )
        progressStore.update("MANUAL_INITIAL_OUTREACH", TaskProgress(
            taskType = "MANUAL_INITIAL_OUTREACH", status = status,
            batchNumber = processed, processedCount = processed.toLong(), totalCount = totalCount.toLong(),
            message = message,
            details = details,
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

/** Per-account run stats tracked during a single run (not persisted). */
class AccountRunStat {
    var success: Int = 0
    var failed: Int = 0
}

/** Per-account progress row (I-8). */
data class AccountStatRow(
    val accountCode: String,
    val todaySent: Int,
    val dailyLimit: Int,
    val success: Int,
    val failed: Int,
    val paused: Boolean,
    val pauseReason: String?
)
