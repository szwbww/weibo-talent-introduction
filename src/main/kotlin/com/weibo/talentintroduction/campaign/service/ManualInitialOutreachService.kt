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
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.AccountRateLimiter
import com.weibo.talentintroduction.mail.service.ProviderResolver
import com.weibo.talentintroduction.mail.service.DeliveredMail
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.NoAvailableSenderAccountException
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderAccountSelfCheckService
import com.weibo.talentintroduction.mail.service.SenderExpertAssignment
import com.weibo.talentintroduction.mail.service.SenderWarmupService
import com.weibo.talentintroduction.mail.service.AccountDailyState
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
    private val selfCheckService: SenderAccountSelfCheckService,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val accountRateLimiter: AccountRateLimiter,
    private val emailSuppressionService: EmailSuppressionService,
    private val providerResolver: ProviderResolver,
    private val senderWarmupService: SenderWarmupService
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
                    if (!hasSentIntro && contact.operatorStatus != "EMAIL_INVALID") {
                        retryable++
                    }
                }
            }
        }

        val emailDomain = batchSendSettingService.getConfig().emailDomain
        // 2. Pending: ES count query, operatorStatus does not exist + has email
        val pending = expertSearchService.countExperts(
            level = ExpertIndexLevel.CANDIDATE,
            filters = ExpertSearchService.notContactedWithEmailFilters(emailDomain.ifBlank { null })
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
     * - Streams targets via [OutreachTargetIterator] (I-7 preserved: ES operatorStatus filter,
     *   mail_send_attempt UNIQUE upsert, hasSentIntroduction double-check — all unchanged).
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

        val esFilters = ExpertSearchService.notContactedWithEmailFilters(config.emailDomain.ifBlank { null })
        val (retryableTargets, seenOrcids) = buildRetryableTargets(campaignId)
        val esEstimate = expertSearchService.countExperts(
            level = ExpertIndexLevel.CANDIDATE,
            filters = esFilters
        ).toInt()
        val totalEstimate = retryableTargets.size + esEstimate
        log.info("Outreach targets: {} retryable, {} ES estimate, {} total estimate; config: roundSize={}, dailyCap={}, perMailMs={}, perRoundMs={}",
            retryableTargets.size, esEstimate, totalEstimate,
            config.roundSize, config.dailyCap, config.perMailIntervalMs, config.perRoundIntervalMs)

        if (totalEstimate == 0) {
            val emptyFinal = if (oneRoundOnly) "PAUSED" else "COMPLETED"
            val emptyReason = if (oneRoundOnly) "EMPTY_SNAPSHOT" else null
            updateProgress(executionId, 0, 0, 0, totalEstimate, 0, totalEstimate,
                emptyFinal, "没有需要发送的专家", emptyList(), mode, 0, config, emptyMap())
            return ManualOutreachResult(
                total = 0, sent = 0, failed = 0, skippedNoAccount = 0,
                wasCancelled = false, finalStatus = emptyFinal, stopReason = emptyReason
            )
        }

        val targetIterator = OutreachTargetIterator(
            retryableTargets = retryableTargets,
            pageSize = config.roundSize * 2,
            seenOrcids = seenOrcids,
            fetchNextPage = { offset, size ->
                expertSearchService.searchExpertsFiltered(
                    level = ExpertIndexLevel.CANDIDATE,
                    filters = esFilters,
                    from = offset,
                    size = size
                )
            }
        )

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

        while (targetIterator.hasNext()) {
            // 1. Cancellation check (I-1: single flow, operator pause → requestCancel)
            if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                log.info("Cancelled after {} processed", processedTotal)
                wasCancelled = true
                stopReason = "CANCELLED"
                break
            }

            // 2. Round gate (L3-1): list sendable → self-check uncached → re-list sendable
            roundNumber++
            val sendable = runRoundGate()
            if (sendable.isEmpty()) {
                val outcome = classifyNoSendableOutcome()
                log.warn("No sendable accounts at round {}: stopReason={}, finalStatus={}", roundNumber, outcome.stopReason, outcome.finalStatus)
                stopReason = outcome.stopReason
                finalStatus = outcome.finalStatus
                break
            }

            // 3. Compute round quota (I-6/L3-2): min(roundSize, dailyCap remaining, estimated remaining, account capacity)
            val dailyCapRemaining = config.dailyCap - dailySentTotal
            val estimatedRemaining = maxOf(0, totalEstimate - processedTotal)
            val remainingAccountCapacity = sendable.sumOf { senderWarmupService.remainingCapacity(it) }
            val roundQuota = minOf(config.roundSize, dailyCapRemaining, estimatedRemaining, remainingAccountCapacity)
            if (roundQuota <= 0) {
                log.info(
                    "Round quota exhausted at round {} (dailyCapRemaining={}, estimatedRemaining={}, remainingAccountCapacity={})",
                    roundNumber, dailyCapRemaining, estimatedRemaining, remainingAccountCapacity
                )
                when {
                    dailyCapRemaining <= 0 -> {
                        stopReason = "DAILY_CAP_REACHED"
                        if (oneRoundOnly) {
                            finalStatus = "PAUSED"
                        }
                    }
                    remainingAccountCapacity <= 0 -> {
                        val limitOutcome = classifyLimitReachedOutcome(sendable)
                        stopReason = limitOutcome.stopReason
                        finalStatus = if (oneRoundOnly) "PAUSED" else "COMPLETED"
                    }
                }
                break
            }

            // 4. Send this round
            var roundSent = 0
            var roundProcessed = 0
            var roundPassed = 0
            var roundRejected = 0
            var midRoundStop = false
            while (roundSent < roundQuota && targetIterator.hasNext()) {
                val (existingContact, expert) = targetIterator.next()
                val normOrcid = normalizeOrcid(expert.orcidId)

                val email = expert.email
                if (email.isNullOrBlank() || emailSuppressionService.isSuppressed(email)) {
                    processedTotal++
                    roundSent++
                    roundProcessed++
                    roundRejected++
                    updateProgress(executionId, sentCount, failedCount, processedTotal,
                        totalEstimate - processedTotal, totalEstimate, totalEstimate,
                        "RUNNING", "已跳过抑制邮箱：${email ?: ""}", errors, mode, roundNumber, config, runAccountStats,
                        roundNumber, roundProcessed, roundPassed, roundRejected)
                    continue
                }

                // Select account (I-3 predicate enforced inside selectAccount)
                val account = try {
                    senderAccountAssignmentService.selectAccount(expert, assignments)
                } catch (e: NoAvailableSenderAccountException) {
                    log.warn("No available sender account mid-round after {} processed, pausing flow", processedTotal)
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
                val provider = providerResolver.resolve(expert.email)

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
                        roundSent++
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
                        accountRateLimiter.recordSuccess(account.accountCode, provider, config.perMailIntervalMs)
                        // 6. Record success atomically (state transition + mail_record + counter + attempt + ES) — I-7
                        txHelper.recordSuccess(
                            contact = contact, accountCode = account.accountCode,
                            deliveredMessageId = messageId, subject = mail.subject,
                            body = mail.body, attemptId = attempt.id!!
                        )
                        sentCount++
                        dailySentTotal++
                        stat.success++
                        roundPassed++
                    } else {
                        val errorSummary = buildSmtpErrorSummary(delivered)
                        when (delivered.errorCategory) {
                            SmtpErrorCategory.PERMANENT -> {
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.body, attemptId = attempt.id
                                )
                                expertContactRepository.save(
                                    contact.copy(operatorStatus = "EMAIL_INVALID", updatedAt = LocalDateTime.now())
                                )
                                expertIndexWriterService.syncCandidateOperatorStatus(normOrcid, "EMAIL_INVALID")
                                failedCount++
                                stat.failed++
                                roundRejected++
                                errors.add("永久发送失败 (${expert.email}): ${delivered.errorDetail ?: delivered.status}")
                                if (errors.size > 20) errors.removeAt(0)
                            }
                            SmtpErrorCategory.TRANSIENT -> {
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.body, attemptId = attempt.id
                                )
                                val code = delivered.smtpResponseCode
                                if (code == 421 || code == 452) {
                                    accountRateLimiter.recordThrottled(account.accountCode, provider, config.perMailIntervalMs)
                                    failedCount++
                                    stat.failed++
                                    roundRejected++
                                    errors.add("限流 (${expert.email}): ${delivered.errorDetail ?: delivered.status}")
                                    if (errors.size > 20) errors.removeAt(0)
                                } else {
                                    mailSenderAccountService.pauseAutoSend(
                                        account.accountCode,
                                        "SMTP_TRANSIENT:${delivered.smtpResponseCode}:${delivered.errorDetail?.take(200)}"
                                    )
                                    failedCount++
                                    stat.failed++
                                    roundRejected++
                                    errors.add("暂时发送失败 (${expert.email}): ${delivered.errorDetail ?: delivered.status}")
                                    if (errors.size > 20) errors.removeAt(0)
                                    midRoundStop = true
                                    break
                                }
                            }
                            SmtpErrorCategory.INFRASTRUCTURE -> {
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.body, attemptId = attempt.id
                                )
                                mailSenderAccountService.pauseAutoSend(
                                    account.accountCode,
                                    "SMTP_INFRA:${delivered.errorDetail?.take(200)}"
                                )
                                failedCount++
                                stat.failed++
                                roundRejected++
                                errors.add("基础设施发送失败 (${expert.email}): ${delivered.errorDetail ?: delivered.status}")
                                if (errors.size > 20) errors.removeAt(0)
                                midRoundStop = true
                                break
                            }
                            else -> {
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.body, attemptId = attempt.id
                                )
                                failedCount++
                                stat.failed++
                                roundRejected++
                                errors.add("发送失败 (${expert.email}): ${delivered.status}")
                                if (errors.size > 20) errors.removeAt(0)
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.error("Failed to process ORCID: {}", normOrcid, e)
                    failedCount++
                    stat.failed++
                    roundRejected++
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
                roundSent++
                roundProcessed++

                // Update progress (I-8: per-account stats)
                updateProgress(executionId, sentCount, failedCount, processedTotal,
                    totalEstimate - processedTotal, totalEstimate, totalEstimate,
                    "RUNNING", "正在发送：${expert.email}", errors, mode, roundNumber, config, runAccountStats,
                    roundNumber, roundProcessed, roundPassed, roundRejected)

                // Throttle per mail (I-6 + dynamic rate limiter)
                val intervalMs = accountRateLimiter.getIntervalMs(account.accountCode, provider, config.perMailIntervalMs)
                if (intervalMs > 0 && roundSent < roundQuota && targetIterator.hasNext()) {
                    try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                }
            }

            if (midRoundStop) break

            // 5. Round end progress (I-8)
            updateProgress(executionId, sentCount, failedCount, processedTotal,
                totalEstimate - processedTotal, totalEstimate, totalEstimate,
                "RUNNING", "第${roundNumber}轮完成，已发送 $sentCount 封", errors, mode, roundNumber, config, runAccountStats,
                roundNumber, roundProcessed, roundPassed, roundRejected)

            // 6. oneRoundOnly (manual button) — return after one round (L3-2: back to PAUSED)
            if (oneRoundOnly) {
                log.info("oneRoundOnly=true, returning after round {}", roundNumber)
                stopReason = "ONE_ROUND_DONE"
                finalStatus = "PAUSED"
                break
            }

            // 7. Round interval (I-6)
            if (config.perRoundIntervalMs > 0 && targetIterator.hasNext()) {
                try { Thread.sleep(config.perRoundIntervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
        }

        // Resolve final status
        val resolvedFinalStatus = when {
            wasCancelled -> "CANCELLED"
            finalStatus != null -> finalStatus
            else -> "COMPLETED"
        }
        val finalMessage = stopReasonMessage(resolvedFinalStatus, stopReason)
        updateProgress(executionId, sentCount, failedCount, processedTotal,
            totalEstimate - processedTotal, totalEstimate, totalEstimate,
            resolvedFinalStatus, finalMessage, errors, mode, roundNumber, config, runAccountStats,
            stopReason = stopReason)

        val skipped = if (stopReason == "NO_AVAILABLE_ACCOUNT") totalEstimate - processedTotal else 0
        return ManualOutreachResult(
            total = totalEstimate, sent = sentCount, failed = failedCount,
            skippedNoAccount = skipped, wasCancelled = wasCancelled,
            finalStatus = resolvedFinalStatus, stopReason = stopReason,
            remaining = totalEstimate - processedTotal
        )
    }

    // ──── Private helpers ────

    private fun classifyNoSendableOutcome(): StopOutcome {
        val activeAccounts = mailSenderAccountService.listEnabledAccounts()
            .filter { it.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE }
        if (activeAccounts.isEmpty()) {
            return StopOutcome("NO_AVAILABLE_ACCOUNT", "PAUSED")
        }
        val states = activeAccounts.map { senderWarmupService.dailyState(it) }
        if (states.any { it == AccountDailyState.PAUSED_FAULT }) {
            return StopOutcome("NO_AVAILABLE_ACCOUNT", "PAUSED")
        }
        return classifyLimitReachedOutcome(activeAccounts)
    }

    private fun classifyLimitReachedOutcome(accounts: List<MailSenderAccount>): StopOutcome {
        val states = accounts.map { senderWarmupService.dailyState(it) }
        val hasWarmupLimit = states.any { it == AccountDailyState.WARMUP_LIMIT_REACHED }
        val hasDailyLimit = states.any { it == AccountDailyState.DAILY_LIMIT_REACHED }
        val stopReason = when {
            hasDailyLimit -> "DAILY_LIMIT_REACHED"
            hasWarmupLimit -> "WARMUP_LIMIT_REACHED"
            else -> "NO_AVAILABLE_ACCOUNT"
        }
        val finalStatus = if (stopReason == "NO_AVAILABLE_ACCOUNT") "PAUSED" else "COMPLETED"
        return StopOutcome(stopReason, finalStatus)
    }

    private fun stopReasonMessage(finalStatus: String, stopReason: String?): String = when (stopReason) {
        "WARMUP_LIMIT_REACHED" -> "已达到预热上限，今日暂停发送"
        "DAILY_LIMIT_REACHED" -> if (hasWarmupLimitedAccounts()) {
            "已达到今日发送上限（含预热账号）"
        } else {
            "已达到今日发送上限"
        }
        "NO_AVAILABLE_ACCOUNT" -> "批量发送已暂停：无可用邮箱账号，请检查并恢复账号。"
        "DAILY_CAP_REACHED" -> "已达到本批次每日上限"
        "ONE_ROUND_DONE" -> "手动单轮发送已完成"
        "CANCELLED" -> "发送任务已被取消"
        else -> when (finalStatus) {
            "PAUSED" -> "流程已暂停: ${stopReason ?: ""}"
            "FAILED" -> "发送任务失败: ${stopReason ?: ""}"
            "COMPLETED" -> "发送任务已完成"
            else -> "发送任务结束"
        }
    }

    private fun hasWarmupLimitedAccounts(): Boolean =
        mailSenderAccountService.listEnabledAccounts()
            .filter { it.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE }
            .any { senderWarmupService.dailyState(it) == AccountDailyState.WARMUP_LIMIT_REACHED }

    private data class StopOutcome(val stopReason: String, val finalStatus: String)

    private fun normalizeOrcid(orcid: String) = ExpertIdNormalizer.normalize(orcid)

    private fun buildSmtpErrorSummary(delivered: DeliveredMail): String =
        buildString {
            append(delivered.errorCategory.name)
            delivered.smtpResponseCode?.let { append(":$it") }
            delivered.errorDetail?.let { append(":${it.take(200)}") }
        }

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

    private fun buildRetryableTargets(campaignId: Long): Pair<List<Pair<ExpertContact?, ExpertProfile>>, MutableSet<String>> {
        val seenOrcids = mutableSetOf<String>()
        val targets = mutableListOf<Pair<ExpertContact?, ExpertProfile>>()

        // 1. Retryable contacts: NEW status without SENT introduction (I-7 preserved)
        val newContacts = expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW")
        if (newContacts.isNotEmpty()) {
            val retryableContacts = newContacts.filter {
                !hasSentIntroduction(it.id!!) && it.operatorStatus != "EMAIL_INVALID"
            }
            val orcidIds = retryableContacts.map { it.orcidId }
            val profiles = if (orcidIds.isNotEmpty()) expertSearchService.searchByOrcidIds(orcidIds) else emptyList()
            val profileMap = profiles.associateBy { normalizeOrcid(it.orcidId) }
            for (contact in retryableContacts) {
                val normOrcid = normalizeOrcid(contact.orcidId)
                val profile = profileMap[normOrcid]
                if (profile != null && seenOrcids.add(normOrcid)) {
                    targets.add(Pair(contact, profile))
                }
            }
        }

        log.info("Retryable targets: {}", targets.size)
        return Pair(targets, seenOrcids)
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

    private fun buildAccountStats(runAccountStats: Map<String, AccountRunStat>, config: BatchSendConfig): List<AccountStatRow> {
        // 运行中账号统计仅展示已启用账号；已禁用(enabled=false)账号不参与发送，不应出现在面板中。
        val allAccounts = mailSenderAccountService.listEnabledAccounts()
        val rateLimiterSnapshot = accountRateLimiter.getSnapshot()
        return allAccounts.map { account ->
            val runStat = runAccountStats[account.accountCode]
            AccountStatRow(
                accountCode = account.accountCode,
                todaySent = account.todaySentCount,
                dailyLimit = account.dailySendLimit,
                effectiveDailyLimit = senderWarmupService.effectiveDailyLimit(account),
                warmupActive = senderWarmupService.isWarmupActive(account),
                limitReason = senderWarmupService.dailyState(account)
                    .takeIf { it != AccountDailyState.SENDABLE }
                    ?.name,
                success = runStat?.success ?: 0,
                failed = runStat?.failed ?: 0,
                paused = account.autoSendPaused,
                pauseReason = account.autoSendPausedReason,
                currentIntervalMs = rateLimiterSnapshot[account.accountCode] ?: config.perMailIntervalMs
            )
        }
    }

    private fun updateProgress(
        executionId: Long, sent: Int, failed: Int, processed: Int,
        remaining: Int, total: Int, totalCount: Int,
        status: String, message: String, errors: List<String>,
        mode: ExecutionMode, roundNumber: Int,
        config: BatchSendConfig, runAccountStats: Map<String, AccountRunStat>,
        batchNumber: Int = 0,
        batchProcessed: Int = 0,
        batchPassed: Int = 0,
        batchRejected: Int = 0,
        stopReason: String? = null
    ) {
        val details = mutableMapOf<String, Any>(
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
            "accounts" to buildAccountStats(runAccountStats, config)
        )
        if (stopReason != null) {
            details["stopReason"] = stopReason
        }
        progressStore.update("MANUAL_INITIAL_OUTREACH", TaskProgress(
            taskType = "MANUAL_INITIAL_OUTREACH", status = status,
            batchNumber = batchNumber, processedCount = processed.toLong(), totalCount = totalCount.toLong(),
            message = message,
            details = details,
            errors = errors.toList(),
            batchProcessed = batchProcessed,
            batchPassed = batchPassed,
            batchRejected = batchRejected,
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
    val effectiveDailyLimit: Int = dailyLimit,
    val warmupActive: Boolean = false,
    val limitReason: String? = null,
    val success: Int,
    val failed: Int,
    val paused: Boolean,
    val pauseReason: String?,
    val currentIntervalMs: Long? = null
)
