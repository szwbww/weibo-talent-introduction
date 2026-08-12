package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot
import com.weibo.talentintroduction.campaign.domain.BatchOutcomeReasonCodes
import com.weibo.talentintroduction.campaign.domain.OutcomeAccumulator
import com.weibo.talentintroduction.campaign.domain.OutcomeBreakdown
import com.weibo.talentintroduction.campaign.domain.RecipientScope
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
import com.weibo.talentintroduction.mail.service.AccountDailyState
import com.weibo.talentintroduction.mail.service.AccountRateLimiter
import com.weibo.talentintroduction.mail.service.AutoReplySettingService
import com.weibo.talentintroduction.mail.service.BoundSenderAccountUnavailableException
import com.weibo.talentintroduction.mail.service.DeliveredMail
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.ManualExpertMailService
import com.weibo.talentintroduction.mail.service.ManualMailOptionType
import com.weibo.talentintroduction.mail.service.ManualMailSendCommand
import com.weibo.talentintroduction.mail.service.NoAvailableSenderAccountException
import com.weibo.talentintroduction.mail.service.PersonalizationGateException
import com.weibo.talentintroduction.mail.service.ProviderResolver
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderAccountBindingService
import com.weibo.talentintroduction.mail.service.SenderAccountNotBoundException
import com.weibo.talentintroduction.mail.service.SenderAccountSelfCheckService
import com.weibo.talentintroduction.mail.service.SenderExpertAssignment
import com.weibo.talentintroduction.mail.service.SenderWarmupService
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.ceil

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
    private val senderWarmupService: SenderWarmupService,
    private val autoReplySettingService: AutoReplySettingService,
    private val manualExpertMailService: ManualExpertMailService,
    private val taskExecutionService: TaskExecutionService,
    private val senderAccountBindingService: SenderAccountBindingService
) {
    private val log = LoggerFactory.getLogger(ManualInitialOutreachService::class.java)

    /**
     * Count experts pending outreach: new candidates from ES + retryable contacts (NEW status without SENT mail record).
     */
    fun countPending(): PendingOutreachSummary {
        val config = batchSendSettingService.getConfig()
        var retryable = 0

        // 1. Retryable: NEW contacts without SENT introduction (same path as runScheduledBatch)
        val campaign = campaignRepository.findByCampaignCode("MANUAL_OUTREACH")
        if (campaign != null) {
            val campaignId = campaign.id ?: error("Campaign ID is null")
            val (retryableTargets, _) = buildRetryableTargets(
                campaignId,
                discipline = config.discipline.ifBlank { null },
                emailDomain = config.emailDomain.ifBlank { null }
            )
            retryable = retryableTargets.size
        }

        // 2. Pending: ES count query, operatorStatus does not exist + has email
        val pending = expertSearchService.countExperts(
            level = ExpertIndexLevel.CANDIDATE,
            filters = ExpertSearchService.notContactedWithEmailFilters(
                config.emailDomain.ifBlank { null },
                config.discipline.ifBlank { null }
            )
        )

        return PendingOutreachSummary(pending = pending.toInt(), retryable = retryable, totalSendable = pending.toInt() + retryable)
    }

    /**
     * Legacy single-pass entry point. Kept as a thin wrapper for backward compatibility with
     * the existing /manual-outreach/start endpoint and tests. Delegates to the round-based
     * scheduled batch engine in MANUAL mode, full run (not one-round-only).
     */
    fun runBulkOutreach(executionId: Long): ManualOutreachResult {
        val config = batchSendSettingService.getConfig()
        val snapshot = config.toSnapshot()
        return run(snapshot, executionId, ExecutionMode.MANUAL, oneRoundOnly = false)
    }

    /**
     * Unified batch send entry (I-1/I-3/I-5/I-6). Consumes launch snapshot only; never re-reads KV/entity config.
     */
    fun run(
        snapshot: BatchExecutionSnapshot,
        executionId: Long,
        mode: ExecutionMode,
        oneRoundOnly: Boolean = snapshot.oneRoundOnly
    ): ManualOutreachResult = when (snapshot.mailType) {
        BatchSendType.MATERIAL_REMINDER.name ->
            runMaterialFromSnapshot(snapshot, executionId, mode, oneRoundOnly)
        else ->
            runIntroductionFromSnapshot(snapshot, executionId, mode, oneRoundOnly)
    }

    /**
     * Material-reminder batch send loop.
     *
     * Targets: APPLICATION index + tag=`承诺回复材料` + has email + config emailDomain/discipline.
     * Snapshot is built eagerly before the first send; totalHits > 10000 aborts with error.
     * Sends via [ManualExpertMailService.sendManualMail] COMPOSE_TEMPLATE path.
     * Reuses round gate / dailyCap / roundSize / intervals / rate-limiter from MATERIAL_REMINDER config.
     * Does NOT create new contacts, call ManualOutreachTxHelper, or modify tags/index/handoff.
     */
    fun runMaterialReminderBatch(
        executionId: Long,
        mode: ExecutionMode,
        oneRoundOnly: Boolean
    ): ManualOutreachResult {
        val config = batchSendSettingService.getConfig(BatchSendType.MATERIAL_REMINDER)
        val snapshot = config.toSnapshot(oneRoundOnly = oneRoundOnly)
        return run(snapshot, executionId, mode, oneRoundOnly)
    }

    private fun runMaterialFromSnapshot(
        snapshot: BatchExecutionSnapshot,
        executionId: Long,
        mode: ExecutionMode,
        oneRoundOnly: Boolean
    ): ManualOutreachResult {
        log.info("Starting material reminder batch: executionId={}, mode={}, oneRoundOnly={}", executionId, mode, oneRoundOnly)
        val ignoreWarmup = mode == ExecutionMode.MANUAL
        val config = snapshot.toBatchSendConfig(BatchSendType.MATERIAL_REMINDER)
        val templateId = snapshot.templateId ?: error("MATERIAL_REMINDER config requires a templateId")
        val scope = RecipientScope.fromSnapshot(snapshot)

        val materialSnapshot = buildMaterialReminderSnapshot(scope, config)
        val targets = materialSnapshot.targets
        val totalEstimate = targets.size
        log.info("Material reminder snapshot: esHits={}, sendable={}, scope={}",
            materialSnapshot.totalEsHits, totalEstimate, materialSnapshot.scopeDescription)

        if (totalEstimate == 0) {
            val emptyFinal = if (oneRoundOnly) "PAUSED" else "COMPLETED"
            val emptyReason = if (oneRoundOnly) "EMPTY_SNAPSHOT" else null
            updateProgress(executionId, 0, 0, 0, 0, 0, 0,
                emptyFinal, "没有需要发送材料提醒的专家", emptyList(), mode, 0, config, emptyMap(),
                stopReason = emptyReason, sendType = BatchSendType.MATERIAL_REMINDER, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
            return emptyResult(emptyFinal, emptyReason)
        }

        var targetIndex = 0
        val accumulator = OutcomeAccumulator(totalEstimate)
        var wasCancelled = false
        val errors = mutableListOf<String>()
        val assignments = mutableListOf<SenderExpertAssignment>()
        val stock = senderAccountAssignmentService.loadBindingStock()
        val runAccountStats = mutableMapOf<String, AccountRunStat>()
        var roundNumber = 0
        var stopReason: String? = null
        var finalStatus: String? = null
        var processedTotal = 0

        while (targetIndex < targets.size) {
            // Cancellation check
            if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                log.info("Material reminder batch cancelled after {} processed", processedTotal)
                wasCancelled = true
                stopReason = "CANCELLED"
                break
            }

            // Round budget (I-1/I-2): roundsPerRun bounds rounds started in THIS run only.
            if (roundNumber >= snapshot.roundsPerRun) {
                log.info("Reminder rounds per run exhausted after {} rounds (roundsPerRun={})", roundNumber, snapshot.roundsPerRun)
                stopReason = "ROUNDS_PER_RUN_REACHED"
                finalStatus = "COMPLETED"
                break
            }

            // Round gate — explicit TTL from MATERIAL_REMINDER config (K-self-check-ttl-type-scope)
            roundNumber++
            val sendable = runRoundGate(ignoreWarmup, config.selfCheckTtlMinutes)
            if (sendable.isEmpty()) {
                val outcome = classifyNoSendableOutcome(ignoreWarmup)
                log.warn("No sendable accounts at reminder round {}: stopReason={}", roundNumber, outcome.stopReason)
                stopReason = outcome.stopReason
                finalStatus = outcome.finalStatus
                break
            }

            // Round quota
            val estimatedRemaining = maxOf(0, totalEstimate - targetIndex)
            val remainingAccountCapacity = sendable.sumOf { senderWarmupService.remainingCapacity(it, ignoreWarmup = ignoreWarmup) }
            val roundQuota = minOf(config.roundSize, estimatedRemaining, remainingAccountCapacity)
            if (roundQuota <= 0) {
                log.info("Reminder round quota exhausted (estimatedRemaining={}, accountCapacity={})",
                    estimatedRemaining, remainingAccountCapacity)
                when {
                    remainingAccountCapacity <= 0 -> {
                        val limitOutcome = classifyLimitReachedOutcome(sendable, ignoreWarmup)
                        stopReason = limitOutcome.stopReason
                        finalStatus = if (oneRoundOnly) "PAUSED" else "COMPLETED"
                    }
                }
                break
            }

            // Send round
            var roundSent = 0
            var roundProcessed = 0
            var roundPassed = 0
            var roundRejected = 0
            var midRoundStop = false

            while (roundSent < roundQuota && targetIndex < targets.size) {
                val (contact, expert) = targets[targetIndex]
                targetIndex++

                val contactId = contact.id!!
                val email = contact.expertEmail
                val normOrcid = normalizeOrcid(expert.orcidId)

                if (email.isBlank() || emailSuppressionService.isSuppressed(email)) {
                    accumulator.recordSkipped(BatchOutcomeReasonCodes.SUPPRESSED, "已跳过抑制邮箱：$email")
                    processedTotal++; roundSent++; roundProcessed++; roundRejected++
                    updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                        "RUNNING", "已跳过抑制邮箱：$email", errors, mode, roundNumber, config, runAccountStats,
                        roundNumber, roundProcessed, roundPassed, roundRejected,
                        sendType = BatchSendType.MATERIAL_REMINDER, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                    continue
                }

                val account = try {
                    senderAccountBindingService.resolveForSend(contact, manual = true)
                } catch (e: SenderAccountNotBoundException) {
                    try {
                        val picked = senderAccountAssignmentService
                            .selectAccount(expert, assignments, ignoreWarmup, stock)
                        senderAccountBindingService
                            .bindIfAbsent(contactId, picked.accountCode, LocalDateTime.now())
                        picked
                    } catch (ex: NoAvailableSenderAccountException) {
                        stopReason = "NO_AVAILABLE_ACCOUNT"
                        finalStatus = "PAUSED"
                        midRoundStop = true
                        break
                    }
                } catch (e: BoundSenderAccountUnavailableException) {
                    // I-4: 单专家跳过，不中断整批
                    accumulator.recordSkipped(
                        BatchOutcomeReasonCodes.SEND_EXCEPTION,
                        "绑定账号不可用（${e.accountCode}/${e.reason}）：$email"
                    )
                    processedTotal++; roundSent++; roundProcessed++; roundRejected++
                    updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                        "RUNNING", "正在发送材料提醒：$email", errors, mode, roundNumber, config, runAccountStats,
                        roundNumber, roundProcessed, roundPassed, roundRejected,
                        sendType = BatchSendType.MATERIAL_REMINDER, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                    continue
                }

                val stat = runAccountStats.getOrPut(account.accountCode) { AccountRunStat() }
                val provider = providerResolver.resolve(email)

                try {
                    // I-6 double-check: skip if SENT MATERIAL_REMINDER already recorded
                    if (hasSentMaterialReminder(contactId)) {
                        log.info("SENT MATERIAL_REMINDER already exists for contact {}, skipping", contactId)
                        accumulator.recordSkipped(BatchOutcomeReasonCodes.DEDUP)
                        roundSent++
                        continue
                    }

                    val command = ManualMailSendCommand(
                        optionType = ManualMailOptionType.COMPOSE_TEMPLATE.name,
                        optionValue = templateId.toString(),
                        senderAccountCode = account.accountCode
                    )
                    val result = manualExpertMailService.sendManualMail(contactId, command)

                    if (result.sendStatus == "SENT") {
                        accountRateLimiter.recordSuccess(account.accountCode, provider, config.perMailIntervalMs)
                        mailSenderAccountRepository.incrementTodaySentCount(account.accountCode, LocalDateTime.now())
                        accumulator.recordSuccess()
                        stat.success++
                        roundPassed++
                        taskExecutionService.updateProgressCounts(executionId, accumulator.success, accumulator.failure)
                    } else {
                        accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "发送失败 ($email): ${result.sendStatus}")
                        stat.failed++
                        roundRejected++
                        errors.add("发送失败 ($email): ${result.sendStatus}")
                        if (errors.size > 20) errors.removeAt(0)
                    }
                } catch (e: PersonalizationGateException) {
                    accumulator.recordSkipped(
                        BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE,
                        "个性化字段缺失（${e.missingKeys.joinToString(",")}）：$email"
                    )
                    // 计数由循环公共收尾路径（processedTotal/roundSent/roundProcessed）统一推进一次，
                    // 此处不再自增，避免与收尾路径重复计数（V-1）。
                } catch (e: Exception) {
                    log.error("Failed to send material reminder to contact {}", contactId, e)
                    accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "发送异常 ($email): ${e.message}")
                    stat.failed++
                    roundRejected++
                    errors.add("发送异常 ($email): ${e.message ?: "Unknown error"}")
                    if (errors.size > 20) errors.removeAt(0)
                }

                assignments.add(SenderExpertAssignment(
                    accountCode = account.accountCode, expertId = normOrcid,
                    distributionKey = expert.country?.lowercase()?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"
                ))

                processedTotal++
                roundSent++
                roundProcessed++

                updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                    "RUNNING", "正在发送材料提醒：$email", errors, mode, roundNumber, config, runAccountStats,
                    roundNumber, roundProcessed, roundPassed, roundRejected,
                    sendType = BatchSendType.MATERIAL_REMINDER, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)

                val intervalMs = accountRateLimiter.getIntervalMs(account.accountCode, provider, config.perMailIntervalMs)
                if (intervalMs > 0 && roundSent < roundQuota && targetIndex < targets.size) {
                    try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                }
            }

            if (midRoundStop) break

            updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                "RUNNING", "第${roundNumber}轮完成，已发送 ${accumulator.success} 封材料提醒", errors, mode, roundNumber, config, runAccountStats,
                roundNumber, roundProcessed, roundPassed, roundRejected,
                sendType = BatchSendType.MATERIAL_REMINDER, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)

            if (oneRoundOnly) {
                log.info("oneRoundOnly=true, returning after reminder round {}", roundNumber)
                stopReason = "ONE_ROUND_DONE"
                finalStatus = "PAUSED"
                break
            }

            // Round interval — skip when the roundsPerRun budget is already spent
            if (config.perRoundIntervalMs > 0 && targetIndex < targets.size && roundNumber < snapshot.roundsPerRun) {
                try { Thread.sleep(config.perRoundIntervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
        }

        val resolvedFinalStatus = when {
            wasCancelled -> "CANCELLED"
            finalStatus != null -> finalStatus
            else -> "COMPLETED"
        }
        val finalMessage = stopReasonMessage(resolvedFinalStatus, stopReason, ignoreWarmup)
        updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
            resolvedFinalStatus, finalMessage, errors, mode, roundNumber, config, runAccountStats,
            stopReason = stopReason, sendType = BatchSendType.MATERIAL_REMINDER, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)

        return buildResult(totalEstimate, accumulator, wasCancelled, resolvedFinalStatus, stopReason)
    }

    /**
     * Count pending outreach for the given sendType.
     * MATERIAL_REMINDER uses the shared snapshot builder; INTRODUCTION uses the existing ES+retry path.
     */
    fun countPending(sendType: BatchSendType): PendingOutreachSummary = when (sendType) {
        BatchSendType.INTRODUCTION -> countPending()
        BatchSendType.MATERIAL_REMINDER -> {
            val config = batchSendSettingService.getConfig(BatchSendType.MATERIAL_REMINDER)
            val snapshot = buildMaterialReminderSnapshot(config)
            PendingOutreachSummary(pending = snapshot.targets.size, retryable = 0, totalSendable = snapshot.targets.size)
        }
    }

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
     *   (NO_AVAILABLE_ACCOUNT → PAUSED, etc.). The control service persists
     *   runtime status transitions based on these signals.
     */
    fun runScheduledBatch(
        executionId: Long,
        mode: ExecutionMode,
        oneRoundOnly: Boolean
    ): ManualOutreachResult {
        val config = batchSendSettingService.getConfig()
        return runIntroductionFromSnapshot(
            config.toSnapshot(oneRoundOnly = oneRoundOnly),
            executionId,
            mode,
            oneRoundOnly = oneRoundOnly
        )
    }

    private fun runIntroductionFromSnapshot(
        snapshot: BatchExecutionSnapshot,
        executionId: Long,
        mode: ExecutionMode,
        oneRoundOnly: Boolean
    ): ManualOutreachResult {
        log.info("Starting scheduled batch outreach, executionId={}, mode={}, oneRoundOnly={}", executionId, mode, oneRoundOnly)
        val ignoreWarmup = mode == ExecutionMode.MANUAL
        val campaign = getOrCreateManualCampaign()
        val campaignId = campaign.id ?: error("Campaign ID is null")
        val config = snapshot.toBatchSendConfig(BatchSendType.INTRODUCTION)
        val scope = RecipientScope.fromSnapshot(snapshot)
        val (retryableTargets, seenOrcids) = buildRetryableTargets(campaignId, scope)
        val esEstimate = countEsTargets(scope)
        val totalEstimate = retryableTargets.size + esEstimate
        log.info("Outreach targets: {} retryable, {} ES estimate, {} total estimate; config: roundSize={}, perMailMs={}, perRoundMs={}",
            retryableTargets.size, esEstimate, totalEstimate,
            config.roundSize, config.perMailIntervalMs, config.perRoundIntervalMs)

        if (totalEstimate == 0) {
            val emptyFinal = if (oneRoundOnly) "PAUSED" else "COMPLETED"
            val emptyReason = if (oneRoundOnly) "EMPTY_SNAPSHOT" else null
            val accumulator = OutcomeAccumulator(0)
            updateProgressWithAccumulator(executionId, accumulator, 0, 0,
                emptyFinal, "没有需要发送的专家", emptyList(), mode, 0, config, emptyMap(),
                stopReason = emptyReason, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
            return emptyResult(emptyFinal, emptyReason)
        }

        val targetIterator = OutreachTargetIterator(
            retryableTargets = retryableTargets,
            pageSize = config.roundSize * 2,
            seenOrcids = seenOrcids,
            fetchNextPage = { offset, size ->
                fetchEsPage(scope, seenOrcids, offset, size)
            }
        )

        val accumulator = OutcomeAccumulator(totalEstimate)
        var wasCancelled = false
        val errors = mutableListOf<String>()
        val assignments = mutableListOf<SenderExpertAssignment>()
        val stock = senderAccountAssignmentService.loadBindingStock()
        val runAccountStats = mutableMapOf<String, AccountRunStat>()
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

            // Round budget (I-1/I-2): roundsPerRun bounds rounds started in THIS run only.
            if (roundNumber >= snapshot.roundsPerRun) {
                log.info("Rounds per run exhausted after {} rounds (roundsPerRun={})", roundNumber, snapshot.roundsPerRun)
                stopReason = "ROUNDS_PER_RUN_REACHED"
                finalStatus = "COMPLETED"
                break
            }

            // 2. Round gate (L3-1): list sendable → self-check uncached → re-list sendable
            roundNumber++
            val sendable = runRoundGate(ignoreWarmup, config.selfCheckTtlMinutes)
            if (sendable.isEmpty()) {
                val outcome = classifyNoSendableOutcome(ignoreWarmup)
                log.warn("No sendable accounts at round {}: stopReason={}, finalStatus={}", roundNumber, outcome.stopReason, outcome.finalStatus)
                stopReason = outcome.stopReason
                finalStatus = outcome.finalStatus
                break
            }

            // 3. Compute round quota (I-1): min(roundSize, estimated remaining, account capacity)
            val estimatedRemaining = maxOf(0, totalEstimate - processedTotal)
            val remainingAccountCapacity = sendable.sumOf { senderWarmupService.remainingCapacity(it, ignoreWarmup = ignoreWarmup) }
            val roundQuota = minOf(config.roundSize, estimatedRemaining, remainingAccountCapacity)
            if (roundQuota <= 0) {
                log.info(
                    "Round quota exhausted at round {} (estimatedRemaining={}, remainingAccountCapacity={})",
                    roundNumber, estimatedRemaining, remainingAccountCapacity
                )
                when {
                    remainingAccountCapacity <= 0 -> {
                        val limitOutcome = classifyLimitReachedOutcome(sendable, ignoreWarmup)
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
                    accumulator.recordSkipped(BatchOutcomeReasonCodes.SUPPRESSED, "已跳过抑制邮箱：${email ?: ""}")
                    processedTotal++
                    roundSent++
                    roundProcessed++
                    roundRejected++
                    updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                        "RUNNING", "已跳过抑制邮箱：${email ?: ""}", errors, mode, roundNumber, config, runAccountStats,
                        roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                    continue
                }

                // I-1: 绑定优先于选号 —— 已有 contact 先解析绑定；无绑定才走 selectAccount 兜底
                val account = if (existingContact != null && existingContact.boundSenderAccountCode != null) {
                    try {
                        senderAccountBindingService
                            .resolveForSend(existingContact, manual = false, ignoreWarmup = ignoreWarmup)
                    } catch (e: BoundSenderAccountUnavailableException) {
                        // I-4: 单专家跳过，不中断整批
                        accumulator.recordSkipped(
                            BatchOutcomeReasonCodes.SEND_EXCEPTION,
                            "绑定账号不可用（${e.accountCode}/${e.reason}）：${expert.email}"
                        )
                        processedTotal++; roundSent++; roundProcessed++; roundRejected++
                        updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                            "RUNNING", "绑定账号不可用：${expert.email}", errors, mode, roundNumber, config, runAccountStats,
                            roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                        continue
                    }
                } else {
                    try {
                        senderAccountAssignmentService.selectAccount(expert, assignments, ignoreWarmup, stock)
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
                }

                val stat = runAccountStats.getOrPut(account.accountCode) { AccountRunStat() }
                val provider = providerResolver.resolve(expert.email)

                try {
                    // 1. Create or reuse contact (occupy the slot) — I-7
                    val contact = existingContact ?: run {
                        val now = LocalDateTime.now()
                        val (boundCode, boundAt) = senderAccountBindingService
                            .bindingFieldsFor(account.accountCode, now)
                        expertContactRepository.save(ExpertContact(
                            campaignId = campaignId, orcidId = normOrcid,
                            expertEmail = expert.email.orEmpty(), expertName = expert.displayName,
                            currentStatus = "NEW", operatorStatus = "NOT_CONTACTED",
                            country = expert.country,
                            autoReplyEnabled = autoReplySettingService.isGlobalEnabled(),
                            boundSenderAccountCode = boundCode,
                            senderAccountBoundAt = boundAt,
                            createdAt = now, updatedAt = now
                        ))
                    }

                    // 2. Double-check: skip if SENT introduction already exists (anti-duplicate, I-7)
                    if (hasSentIntroduction(contact.id!!)) {
                        log.info("SENT introduction already exists for contact {}, skipping", contact.id)
                        accumulator.recordSkipped(BatchOutcomeReasonCodes.DEDUP)
                        processedTotal++
                        roundSent++
                        roundProcessed++
                        continue
                    }

                    val messageId = "<manual-outreach-${normOrcid}-${UUID.randomUUID()}@weibo.com>"
                    val mail = try {
                        introductionMailComposer.compose(account.accountCode, expert, config.templateId)
                            .copy(messageId = messageId)
                    } catch (e: PersonalizationGateException) {
                        log.info("Personalization gate blocked ORCID {}: missing keys {}", normOrcid, e.missingKeys)
                        accumulator.recordSkipped(
                            BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE,
                            "个性化字段缺失（${e.missingKeys.joinToString(",")}）：${expert.email}"
                        )
                        roundRejected++
                        processedTotal++
                        roundSent++
                        roundProcessed++
                        updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                            "RUNNING", "个性化字段缺失：${expert.email}", errors, mode, roundNumber, config, runAccountStats,
                            roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                        continue
                    } catch (e: Exception) {
                        log.error("Template compose failed for ORCID: {}", normOrcid, e)
                        accumulator.recordFailure(BatchOutcomeReasonCodes.TEMPLATE_RENDER_FAILED, "模板渲染失败 (${expert.email}): ${e.message}")
                        stat.failed++
                        roundRejected++
                        processedTotal++
                        roundSent++
                        roundProcessed++
                        updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                            "RUNNING", "模板渲染失败：${expert.email}", errors, mode, roundNumber, config, runAccountStats,
                            roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                        continue
                    }

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
                            body = mail.text ?: mail.body, attemptId = attempt.id!!
                        )
                        accumulator.recordSuccess()
                        stat.success++
                        roundPassed++
                        taskExecutionService.updateProgressCounts(executionId, accumulator.success, accumulator.failure)
                    } else {
                        val errorSummary = buildSmtpErrorSummary(delivered)
                        when (delivered.errorCategory) {
                            SmtpErrorCategory.PERMANENT -> {
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.text ?: mail.body, attemptId = attempt.id
                                )
                                expertContactRepository.save(
                                    contact.copy(operatorStatus = "EMAIL_INVALID", updatedAt = LocalDateTime.now())
                                )
                                expertIndexWriterService.syncCandidateOperatorStatus(normOrcid, "EMAIL_INVALID")
                                accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "永久发送失败 (${expert.email}): ${delivered.errorDetail ?: delivered.status}")
                                stat.failed++
                                roundRejected++
                            }
                            SmtpErrorCategory.TRANSIENT -> {
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.text ?: mail.body, attemptId = attempt.id
                                )
                                val code = delivered.smtpResponseCode
                                if (code == 421 || code == 452) {
                                    accountRateLimiter.recordThrottled(account.accountCode, provider, config.perMailIntervalMs)
                                    accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "限流 (${expert.email}): ${delivered.errorDetail ?: delivered.status}")
                                    stat.failed++
                                    roundRejected++
                                } else {
                                    mailSenderAccountService.pauseAutoSend(
                                        account.accountCode,
                                        "SMTP_TRANSIENT:${delivered.smtpResponseCode}:${delivered.errorDetail?.take(200)}"
                                    )
                                    accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "暂时发送失败 (${expert.email}): ${delivered.errorDetail ?: delivered.status}")
                                    stat.failed++
                                    roundRejected++
                                    midRoundStop = true
                                    break
                                }
                            }
                            SmtpErrorCategory.INFRASTRUCTURE -> {
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.text ?: mail.body, attemptId = attempt.id
                                )
                                mailSenderAccountService.pauseAutoSend(
                                    account.accountCode,
                                    "SMTP_INFRA:${delivered.errorDetail?.take(200)}"
                                )
                                accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "基础设施发送失败 (${expert.email}): ${delivered.errorDetail ?: delivered.status}")
                                stat.failed++
                                roundRejected++
                                midRoundStop = true
                                break
                            }
                            else -> {
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.text ?: mail.body, attemptId = attempt.id
                                )
                                accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "发送失败 (${expert.email}): ${delivered.status}")
                                stat.failed++
                                roundRejected++
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.error("Failed to process ORCID: {}", normOrcid, e)
                    accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "发送异常 (${expert.email}): ${e.message}")
                    stat.failed++
                    roundRejected++
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
                updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                    "RUNNING", "正在发送：${expert.email}", errors, mode, roundNumber, config, runAccountStats,
                    roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)

                // Throttle per mail (I-6 + dynamic rate limiter)
                val intervalMs = accountRateLimiter.getIntervalMs(account.accountCode, provider, config.perMailIntervalMs)
                if (intervalMs > 0 && roundSent < roundQuota && targetIterator.hasNext()) {
                    try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                }
            }

            if (midRoundStop) break

            // 5. Round end progress (I-8)
            updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                "RUNNING", "第${roundNumber}轮完成，已发送 ${accumulator.success} 封", errors, mode, roundNumber, config, runAccountStats,
                roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)

            // 6. oneRoundOnly (manual button) — return after one round (L3-2: back to PAUSED)
            if (oneRoundOnly) {
                log.info("oneRoundOnly=true, returning after round {}", roundNumber)
                stopReason = "ONE_ROUND_DONE"
                finalStatus = "PAUSED"
                break
            }

            // 7. Round interval (I-6) — skip when the roundsPerRun budget is already spent
            if (config.perRoundIntervalMs > 0 && targetIterator.hasNext() && roundNumber < snapshot.roundsPerRun) {
                try { Thread.sleep(config.perRoundIntervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
        }

        // Resolve final status
        val resolvedFinalStatus = when {
            wasCancelled -> "CANCELLED"
            finalStatus != null -> finalStatus
            else -> "COMPLETED"
        }
        val finalMessage = stopReasonMessage(resolvedFinalStatus, stopReason, ignoreWarmup)
        updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
            resolvedFinalStatus, finalMessage, errors, mode, roundNumber, config, runAccountStats,
            stopReason = stopReason, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)

        return buildResult(totalEstimate, accumulator, wasCancelled, resolvedFinalStatus, stopReason)
    }

    // ──── Private helpers ────

    private fun classifyNoSendableOutcome(ignoreWarmup: Boolean): StopOutcome {
        val activeAccounts = mailSenderAccountService.listEnabledAccounts()
            .filter { it.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE }
        if (activeAccounts.isEmpty()) {
            return StopOutcome("NO_AVAILABLE_ACCOUNT", "PAUSED")
        }
        val states = activeAccounts.map { senderWarmupService.dailyState(it, ignoreWarmup = ignoreWarmup) }
        if (states.any { it == AccountDailyState.PAUSED_FAULT }) {
            return StopOutcome("NO_AVAILABLE_ACCOUNT", "PAUSED")
        }
        return classifyLimitReachedOutcome(activeAccounts, ignoreWarmup)
    }

    private fun classifyLimitReachedOutcome(accounts: List<MailSenderAccount>, ignoreWarmup: Boolean): StopOutcome {
        val states = accounts.map { senderWarmupService.dailyState(it, ignoreWarmup = ignoreWarmup) }
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

    private fun stopReasonMessage(finalStatus: String, stopReason: String?, ignoreWarmup: Boolean): String = when (stopReason) {
        "WARMUP_LIMIT_REACHED" -> "已达到预热上限，今日暂停发送"
        "DAILY_LIMIT_REACHED" -> if (hasWarmupLimitedAccounts(ignoreWarmup)) {
            "已达到今日发送上限（含预热账号）"
        } else {
            "已达到今日发送上限"
        }
        "NO_AVAILABLE_ACCOUNT" -> "批量发送已暂停：无可用邮箱账号，请检查并恢复账号。"
        "ROUNDS_PER_RUN_REACHED" -> "本次调度轮次已用完"
        "ONE_ROUND_DONE" -> "手动单轮发送已完成"
        "CANCELLED" -> "发送任务已被取消"
        else -> when (finalStatus) {
            "PAUSED" -> "流程已暂停: ${stopReason ?: ""}"
            "FAILED" -> "发送任务失败: ${stopReason ?: ""}"
            "COMPLETED" -> "发送任务已完成"
            else -> "发送任务结束"
        }
    }

    private fun hasWarmupLimitedAccounts(ignoreWarmup: Boolean): Boolean =
        mailSenderAccountService.listEnabledAccounts()
            .filter { it.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE }
            .any { senderWarmupService.dailyState(it, ignoreWarmup = ignoreWarmup) == AccountDailyState.WARMUP_LIMIT_REACHED }

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
     * Round gate (L3-1) for INTRODUCTION: reads TTL from INTRODUCTION config (compat — existing tests mock checkSendable(account)).
     */
    private fun runRoundGate(ignoreWarmup: Boolean): List<MailSenderAccount> {
        val candidates = mailSenderAccountService.listSendableAccounts(ignoreWarmup)
        if (candidates.isEmpty()) return emptyList()
        for (account in candidates) {
            selfCheckService.checkSendable(account)
        }
        return mailSenderAccountService.listSendableAccounts(ignoreWarmup)
    }

    /**
     * Round gate with explicit TTL — used by MATERIAL_REMINDER to pass its own selfCheckTtlMinutes.
     */
    private fun runRoundGate(ignoreWarmup: Boolean, selfCheckTtlMinutes: Int): List<MailSenderAccount> {
        val candidates = mailSenderAccountService.listSendableAccounts(ignoreWarmup)
        if (candidates.isEmpty()) return emptyList()
        for (account in candidates) {
            selfCheckService.checkSendable(account, selfCheckTtlMinutes)
        }
        return mailSenderAccountService.listSendableAccounts(ignoreWarmup)
    }

    /**
     * Retry path applies the same [RecipientScope] as ES (I-3 / K-batch-send-filter-retry-parity).
     * Profiles are loaded from every funnel level in scope (not hard-coded CANDIDATE).
     */
    private fun buildRetryableTargets(
        campaignId: Long,
        scope: RecipientScope
    ): Pair<List<Pair<ExpertContact?, ExpertProfile>>, MutableSet<String>> {
        val seenOrcids = mutableSetOf<String>()
        val targets = mutableListOf<Pair<ExpertContact?, ExpertProfile>>()

        val newContacts = expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW")
        if (newContacts.isNotEmpty()) {
            val retryableContacts = newContacts.filter {
                !hasSentIntroduction(it.id!!) && it.operatorStatus != "EMAIL_INVALID"
            }
            val orcidIds = retryableContacts.map { it.orcidId }
            val profilesByLevel = if (orcidIds.isEmpty()) {
                emptyMap()
            } else {
                scope.funnelLevels.associateWith { level ->
                    expertSearchService.searchByOrcidIds(orcidIds, ExpertIndexLevel.valueOf(level))
                        .associateBy { normalizeOrcid(it.orcidId) }
                }
            }
            for (contact in retryableContacts) {
                val normOrcid = normalizeOrcid(contact.orcidId)
                val profile = scope.funnelLevels.asSequence()
                    .mapNotNull { level -> profilesByLevel[level]?.get(normOrcid) }
                    .firstOrNull() ?: continue
                if (!scope.matchesExpert(profile)) continue
                if (seenOrcids.add(normOrcid)) {
                    targets.add(Pair(contact, profile))
                }
            }
        }

        log.info("Retryable targets: {} (funnelLevels={})", targets.size, scope.funnelLevels)
        return Pair(targets, seenOrcids)
    }

    /** Legacy typed path — INTRODUCTION defaults to CANDIDATE-only funnel. */
    private fun buildRetryableTargets(
        campaignId: Long,
        discipline: String? = null,
        emailDomain: String? = null
    ): Pair<List<Pair<ExpertContact?, ExpertProfile>>, MutableSet<String>> {
        val scope = RecipientScope(
            mailType = BatchSendType.INTRODUCTION.name,
            funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(),
            regions = emptyList(),
            emailDomain = emailDomain,
            discipline = discipline
        )
        return buildRetryableTargets(campaignId, scope)
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

    private fun buildAccountStats(
        runAccountStats: Map<String, AccountRunStat>,
        config: BatchSendConfig,
        ignoreWarmup: Boolean
    ): List<AccountStatRow> {
        // 运行中账号统计仅展示已启用账号；已禁用(enabled=false)账号不参与发送，不应出现在面板中。
        val allAccounts = mailSenderAccountService.listEnabledAccounts()
        val rateLimiterSnapshot = accountRateLimiter.getSnapshot()
        return allAccounts.map { account ->
            val runStat = runAccountStats[account.accountCode]
            AccountStatRow(
                accountCode = account.accountCode,
                todaySent = account.todaySentCount,
                dailyLimit = account.dailySendLimit,
                effectiveDailyLimit = senderWarmupService.effectiveDailyLimit(account, ignoreWarmup = ignoreWarmup),
                warmupActive = senderWarmupService.isWarmupActive(account, ignoreWarmup = ignoreWarmup),
                limitReason = senderWarmupService.dailyState(account, ignoreWarmup = ignoreWarmup)
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
        stopReason: String? = null,
        sendType: BatchSendType = BatchSendType.INTRODUCTION,
        ignoreWarmup: Boolean = false,
        roundsPerRun: Int = 0
    ) {
        val details = mutableMapOf<String, Any>(
            "executionMode" to mode.name,
            "sendType" to sendType.name,
            "status" to status,
            "roundNumber" to roundNumber,
            "roundsPerRun" to roundsPerRun,
            "dailySentTotal" to sent,
            "sentTotal" to sent,
            "failedTotal" to failed,
            "pending" to remaining,
            "sent" to sent,
            "failed" to failed,
            "accounts" to buildAccountStats(runAccountStats, config, ignoreWarmup)
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

    private fun hasSentMaterialReminder(contactId: Long): Boolean {
        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
        return records.any { it.direction == "OUTBOUND" && it.mailType == "MATERIAL_REMINDER" && it.sendStatus == "SENT" }
    }

    private fun buildMaterialReminderEsFilters(config: BatchSendConfig): List<Map<String, Any>> {
        val filters = mutableListOf<Map<String, Any>>(
            mapOf("term" to mapOf("tags" to "承诺回复材料")),
            mapOf("exists" to mapOf("field" to "email"))
        )
        if (config.emailDomain.isNotBlank()) {
            filters.add(mapOf("wildcard" to mapOf("email" to mapOf("value" to "*@${config.emailDomain}"))))
        }
        if (config.discipline.isNotBlank()) {
            filters.add(ExpertSearchService.disciplineFilter(config.discipline))
        }
        return filters
    }

    /**
     * Builds the full send-target list for one material reminder batch run (I-3/I-6).
     * Rejects outright if ES total exceeds 10000 (I-6 — no partial sends on oversized scope).
     * Paginates ES in 1000-item pages, then joins to MySQL contacts and applies exclusion rules.
     */
    private fun buildMaterialReminderSnapshot(scope: RecipientScope, config: BatchSendConfig): MaterialReminderSnapshot {
        val scopeDescription = scope.funnelLevels.joinToString("+") + " + tags=${scope.tags}" +
            (scope.emailDomain?.let { " + domain=$it" } ?: "") +
            (scope.discipline?.let { " + discipline=$it" } ?: "")
        return buildMaterialReminderSnapshotFromScope(scope, scopeDescription)
    }

    private fun buildMaterialReminderSnapshot(config: BatchSendConfig): MaterialReminderSnapshot {
        val scope = RecipientScope(
            mailType = BatchSendType.MATERIAL_REMINDER.name,
            funnelLevels = setOf("APPLICATION"),
            tags = listOf("承诺回复材料"),
            // 统计路径输入为 BatchSendConfig（KV 层，无地区维度），故不携带地区；发送路径经 fromSnapshot 携带
            regions = emptyList(),
            emailDomain = config.emailDomain.ifBlank { null },
            discipline = config.discipline.ifBlank { null }
        )
        val scopeDescription = "APPLICATION + tag=承诺回复材料 + email" +
            (if (config.emailDomain.isNotBlank()) " + domain=${config.emailDomain}" else "") +
            (if (config.discipline.isNotBlank()) " + discipline=${config.discipline}" else "")
        return buildMaterialReminderSnapshotFromScope(scope, scopeDescription)
    }

    /** Material targets honor [RecipientScope.funnelLevels] (I-3), not a hard-coded APPLICATION-only search. */
    private fun buildMaterialReminderSnapshotFromScope(
        scope: RecipientScope,
        scopeDescription: String
    ): MaterialReminderSnapshot {
        var totalHits = 0L
        val allExperts = mutableListOf<ExpertProfile>()
        val pageSize = 1000
        for (level in scope.funnelLevels) {
            val filters = buildEsFiltersForLevel(scope, level)
            val levelHits = expertSearchService.countExperts(ExpertIndexLevel.valueOf(level), filters)
            totalHits += levelHits
            if (totalHits > 10000) {
                throw IllegalStateException(
                    "材料提醒目标数 ($totalHits) 超过 10000 上限，请缩小过滤范围后再发送"
                )
            }
            var offset = 0
            while (offset < levelHits) {
                val page = expertSearchService.searchExpertsFiltered(
                    level = ExpertIndexLevel.valueOf(level),
                    filters = filters,
                    from = offset,
                    size = pageSize
                )
                if (page.isEmpty()) break
                allExperts.addAll(page)
                offset += page.size
            }
        }
        if (totalHits > 10000) {
            throw IllegalStateException(
                "材料提醒目标数 ($totalHits) 超过 10000 上限，请缩小过滤范围后再发送"
            )
        }

        // Step 3: normalize ORCIDs and bulk-load contacts (K-es-tag-to-mail-cross-store-join)
        val normalizedExperts = allExperts.map { normalizeOrcid(it.orcidId) to it }
        val normOrcidList = normalizedExperts.map { it.first }.distinct()
        val contacts = if (normOrcidList.isNotEmpty()) expertContactRepository.findByOrcidIdIn(normOrcidList) else emptyList()
        val contactByNormOrcid = contacts.associateBy { normalizeOrcid(it.orcidId) }

        // Step 4: apply exclusion rules, dedup by contactId
        val seenContactIds = mutableSetOf<Long>()
        val sendableTargets = mutableListOf<Pair<ExpertContact, ExpertProfile>>()

        for ((normOrcid, expert) in normalizedExperts) {
            val contact = contactByNormOrcid[normOrcid] ?: continue  // exclude: no existing contact
            val contactId = contact.id ?: continue
            if (!seenContactIds.add(contactId)) continue              // dedup by contactId
            val email = contact.expertEmail
            if (email.isBlank()) continue                             // exclude: empty email
            if (emailSuppressionService.isSuppressed(email)) continue  // exclude: suppressed
            if (hasSentMaterialReminder(contactId)) continue          // exclude: already SENT (I-6)
            sendableTargets.add(Pair(contact, expert))
        }

        return MaterialReminderSnapshot(targets = sendableTargets, totalEsHits = totalHits, scopeDescription = scopeDescription)
    }

    private fun countEsTargets(scope: RecipientScope): Int {
        var total = 0
        for (level in scope.funnelLevels) {
            val filters = buildEsFiltersForLevel(scope, level)
            total += expertSearchService.countExperts(ExpertIndexLevel.valueOf(level), filters).toInt()
        }
        return total
    }

    private fun fetchEsPage(scope: RecipientScope, seenOrcids: MutableSet<String>, offset: Int, size: Int): List<ExpertProfile> {
        val results = mutableListOf<ExpertProfile>()
        var remaining = size
        var pageOffset = offset
        for (level in scope.funnelLevels) {
            if (remaining <= 0) break
            val filters = buildEsFiltersForLevel(scope, level)
            val levelCount = expertSearchService.countExperts(ExpertIndexLevel.valueOf(level), filters).toInt()
            if (pageOffset >= levelCount) {
                pageOffset -= levelCount
                continue
            }
            val page = expertSearchService.searchExpertsFiltered(
                level = ExpertIndexLevel.valueOf(level),
                filters = filters,
                from = pageOffset,
                size = remaining
            ).filter { normalizeOrcid(it.orcidId) !in seenOrcids }
            results.addAll(page)
            remaining -= page.size
            pageOffset = 0
        }
        return results
    }

    private fun buildEsFiltersForLevel(scope: RecipientScope, level: String): List<Map<String, Any>> {
        val filters = if (scope.mailType == BatchSendType.INTRODUCTION.name && level == "CANDIDATE") {
            ExpertSearchService.notContactedWithEmailFilters(scope.emailDomain, scope.discipline).toMutableList()
        } else {
            val base = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
            scope.emailDomain?.let { base.add(mapOf("wildcard" to mapOf("email" to mapOf("value" to "*@$it")))) }
            scope.discipline?.let { base.add(ExpertSearchService.disciplineFilter(it)) }
            base
        }
        if (scope.tags.isNotEmpty()) {
            filters.add(mapOf("terms" to mapOf("tags" to scope.tags)))
        }
        ExpertSearchService.regionsFilter(scope.regions)?.let { filters.add(it) }
        return filters
    }

    private fun BatchSendConfig.toSnapshot(oneRoundOnly: Boolean = false): BatchExecutionSnapshot =
        BatchExecutionSnapshot(
            mailType = sendType.name,
            roundSize = roundSize,
            roundsPerRun = maxOf(1, ceil(dailyCap.toDouble() / roundSize).toInt()),
            perMailIntervalMs = perMailIntervalMs,
            perRoundIntervalMs = perRoundIntervalMs,
            selfCheckTtlMinutes = selfCheckTtlMinutes,
            funnelLevel = if (sendType == BatchSendType.INTRODUCTION) "CANDIDATE" else "APPLICATION",
            tags = if (sendType == BatchSendType.MATERIAL_REMINDER) listOf("承诺回复材料") else emptyList(),
            emailDomain = emailDomain.ifBlank { null },
            discipline = discipline.ifBlank { null },
            templateId = templateId,
            oneRoundOnly = oneRoundOnly
        )

    private fun BatchExecutionSnapshot.toBatchSendConfig(sendType: BatchSendType): BatchSendConfig =
        BatchSendConfig(
            sendType = sendType,
            autoEnabled = false,
            cron = "0 0 0 * * ?",
            dailyCap = 0,
            roundSize = roundSize,
            perMailIntervalMs = perMailIntervalMs,
            perRoundIntervalMs = perRoundIntervalMs,
            selfCheckTtlMinutes = selfCheckTtlMinutes,
            emailDomain = emailDomain.orEmpty(),
            discipline = discipline.orEmpty(),
            templateId = templateId
        )

    private fun emptyResult(finalStatus: String?, stopReason: String?): ManualOutreachResult {
        val outcome = OutcomeBreakdown(target = 0, success = 0, failure = 0, skipped = 0, remaining = 0)
        return ManualOutreachResult(
            total = 0, sent = 0, failed = 0, skippedNoAccount = 0,
            wasCancelled = false, finalStatus = finalStatus, stopReason = stopReason,
            remaining = 0, outcome = outcome
        )
    }

    private fun buildResult(
        total: Int,
        accumulator: OutcomeAccumulator,
        wasCancelled: Boolean,
        finalStatus: String?,
        stopReason: String?
    ): ManualOutreachResult {
        accumulator.annotateTerminalRemaining(if (wasCancelled) "CANCELLED" else stopReason)
        val outcome = accumulator.toBreakdown()
        val skippedNoAccount = outcome.skippedReasons[BatchOutcomeReasonCodes.ACCOUNT_UNAVAILABLE]?.count ?: 0
        return ManualOutreachResult(
            total = total,
            sent = outcome.success,
            failed = outcome.failure,
            skipped = outcome.skipped,
            skippedNoAccount = skippedNoAccount,
            wasCancelled = wasCancelled,
            finalStatus = finalStatus,
            stopReason = stopReason,
            remaining = outcome.remaining,
            outcome = outcome
        )
    }

    private fun updateProgressWithAccumulator(
        executionId: Long,
        accumulator: OutcomeAccumulator,
        processed: Int,
        total: Int,
        status: String,
        message: String,
        errors: List<String>,
        mode: ExecutionMode,
        roundNumber: Int,
        config: BatchSendConfig,
        runAccountStats: Map<String, AccountRunStat>,
        batchNumber: Int = 0,
        batchProcessed: Int = 0,
        batchPassed: Int = 0,
        batchRejected: Int = 0,
        stopReason: String? = null,
        sendType: BatchSendType = BatchSendType.INTRODUCTION,
        ignoreWarmup: Boolean = false,
        roundsPerRun: Int = 0
    ) {
        val breakdown = accumulator.toBreakdown()
        val details = mutableMapOf<String, Any>(
            "executionMode" to mode.name,
            "sendType" to sendType.name,
            "status" to status,
            "roundNumber" to roundNumber,
            "roundsPerRun" to roundsPerRun,
            "dailySentTotal" to breakdown.success,
            "sentTotal" to breakdown.success,
            "failedTotal" to breakdown.failure,
            "skippedTotal" to breakdown.skipped,
            "pending" to breakdown.remaining,
            "sent" to breakdown.success,
            "failed" to breakdown.failure,
            "failureReasons" to breakdown.failureReasons,
            "skippedReasons" to breakdown.skippedReasons,
            "accounts" to buildAccountStats(runAccountStats, config, ignoreWarmup)
        )
        if (stopReason != null) details["stopReason"] = stopReason
        progressStore.update("MANUAL_INITIAL_OUTREACH", TaskProgress(
            taskType = "MANUAL_INITIAL_OUTREACH", status = status,
            batchNumber = batchNumber, processedCount = processed.toLong(), totalCount = total.toLong(),
            message = message,
            details = details,
            errors = errors.toList(),
            batchProcessed = batchProcessed,
            batchPassed = batchPassed,
            batchRejected = batchRejected,
            executionId = executionId
        ), executionId)
    }

    private data class MaterialReminderSnapshot(
        val targets: List<Pair<ExpertContact, ExpertProfile>>,
        val totalEsHits: Long,
        val scopeDescription: String
    )
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
    val remaining: Int = 0,
    val skipped: Int = 0,
    val outcome: OutcomeBreakdown? = null
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = sent
    override val taskFailureCount: Int get() = failed
    override val taskFinalStatus: String? get() = when {
        wasCancelled -> "CANCELLED"
        failed > 0 && sent > 0 -> "PARTIAL_SUCCESS"
        failed > 0 -> "FAILED"
        else -> finalStatus?.takeIf { it in setOf("FAILED", "CANCELLED", "PARTIAL_SUCCESS") } ?: "SUCCESS"
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
