package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.domain.BatchExecutionMetadata
import com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot
import com.weibo.talentintroduction.campaign.domain.BatchOutcomeReasonCodes
import com.weibo.talentintroduction.campaign.domain.EmailVerificationAllowedStates
import com.weibo.talentintroduction.campaign.domain.OutcomeAccumulator
import com.weibo.talentintroduction.campaign.domain.OutcomeBreakdown
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.campaign.domain.ResearchDirectionFilters
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.repository.CampaignRepository
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationErrorCodes
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationSendStatus
import com.weibo.talentintroduction.campaign.repository.BatchRecipientReadRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.config.ManualOutreachProperties
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonFeatureGate
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.AccountDailyState
import com.weibo.talentintroduction.mail.service.AccountRateLimiter
import com.weibo.talentintroduction.mail.service.AutoReplySettingService
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
import com.weibo.talentintroduction.mail.service.RecipientAddressFailureClassifier
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderAccountBindingService
import com.weibo.talentintroduction.mail.service.SenderAccountSelfCheckService
import com.weibo.talentintroduction.mail.service.SenderBindingStock
import com.weibo.talentintroduction.mail.service.SenderExpertAssignment
import com.weibo.talentintroduction.mail.service.SenderWarmupService
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.weibo.talentintroduction.template.service.ComposeTemplateSnapshot
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.template.service.InvalidRenderedSubjectException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil

enum class ExecutionMode { AUTO, MANUAL }

/** I-6：验证明细审计不可用（写入失败）时的停止原因码 —— 停止本次执行，绝不静默降级继续发信。 */
private const val STOP_EMAIL_VERIFY_AUDIT_FAILED = "EMAIL_VERIFY_AUDIT_FAILED"

/** I-6：PASS 行无法条件预占 SENDING（重复目标/状态冲突）时的停止原因码。 */
private const val STOP_EMAIL_VERIFY_SEND_STATE_CONFLICT = "EMAIL_VERIFY_SEND_STATE_CONFLICT"

/** I-2：SMTP 前发现最终收件地址与已验证地址不一致时的停止原因码 / 明细 send_reason。 */
private const val SEND_REASON_EMAIL_CHANGED = "EMAIL_CHANGED"

/**
 * I-1/I-4（03）：`buildSmtpErrorSummary` 的永久失败前缀 —— 在线判定与对账读取的是同一份持久化摘要。
 * 只作「历史首封永久失败」事实门禁的判据，不用于地址证据判定（那是地址证据白名单的职责）。
 * 字符串与 `RecipientAddressFailureClassifier` 写入侧一致；只读预检查的 SQL LIKE 判据共用
 * [com.weibo.talentintroduction.campaign.repository.BatchRecipientReadRepository.PERMANENT_FAILURE_SUMMARY_PREFIX]。
 */
private val PERMANENT_SUMMARY_PREFIX = com.weibo.talentintroduction.campaign.repository.BatchRecipientReadRepository.PERMANENT_FAILURE_SUMMARY_PREFIX

/** I-3（03）：传给公共状态入口的说明文本上限（该说明不落库，仅作调用点可读性）。 */
private const val SUMMARY_REASON_LIMIT = 200

/** A3 (I-3)：预估响应里逐人排除明细的条数上限（计数不受限，仅明细截断，避免响应过大）。 */
private const val PREVIEW_EXCLUDED_DETAIL_LIMIT = 500

/** I-5（02）：诊断母集 ES 扫描每批条数（只读，与批量事实读取同量级）。 */
private const val BASE_SCAN_BATCH_SIZE = 500

/** I-5（02）：单次母集扫描的条数上限；超限标 partial，绝不把截断结果当完整总数。 */
private const val MAX_BASELINE_SCAN = 20_000

/**
 * I-2：新发现专家在 ES 文档里的标签值（`ExpertDiscoveryService` 写入侧同值）。
 * 地区粗筛的 `Other` 补充分支只看这个标签，绝不把非发现档案卷进 `Other`。
 */
private const val DISCOVERED_TAG = "discovered"

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
    private val senderAccountBindingService: SenderAccountBindingService,
    private val mailComposeTemplateService: MailComposeTemplateService,
    /** I-1/I-2/I-6：发送前邮箱验证（HTTP + 明细审计 + 标签）的唯一接入点。 */
    private val batchEmailVerificationService: BatchEmailVerificationService,
    /**
     * I-1/I-3（03）：`operator_status` 的公共写入口 —— 永久失败只有具备地址证据时才允许写
     * EMAIL_INVALID；失败事实本身仍由 [txHelper] 记录，二者相互独立。
     */
    private val expertOperatorStatusService: ExpertOperatorStatusService,
    /** I-1/I-3: 批量目标的唯一准入+显式条件选择器（预估/执行/重试/材料共用）。 */
    private val batchRecipientSelectionService: BatchRecipientSelectionService = BatchRecipientSelectionService(),
    /** I-1/I-2/I-3/I-4/I-5/I-6（02）：只读统一预检查 —— 预估与诊断共用，零业务写入。 */
    private val batchRecipientPreflightService: BatchRecipientPreflightService? = null,
    /**
     * I-1/I-2/I-3（08）：人物级选择层。首发预估、ES 目标与 NEW 重试共用；
     * 材料提醒保持逐邮箱选择不变。默认复用同一逐邮箱 selector，使旧/新路径准入口径一致。
     */
    private val discoveryPersonRecipientService: DiscoveryPersonRecipientService =
        DiscoveryPersonRecipientService(batchRecipientSelectionService),
    /** 09 (I-1/I-2/I-3): 人物级首发原子门禁 —— 仅 discovered INTRODUCTION 使用。 */
    private val outreachGuardService: DiscoveryPersonOutreachGuardService? = null,
    /**
     * V-1（master I-8/发布段、04 开关）：人物能力总开关。默认关闭时必须保持本轮引入前的
     * 旧发送行为 —— 只有**已存在人物映射/保护**的目标才继续走 guard（保护不可被开关撤销）。
     */
    private val discoveryPersonFeatureGate: DiscoveryPersonFeatureGate = DiscoveryPersonFeatureGate()
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
    ): ManualOutreachResult {
        // I-1/I-8：开启发送前验证时先校验类型与密钥 —— 任何业务写入之前完成，
        // MATERIAL_REMINDER + true 与缺失/test_ 密钥都在此明确失败，绝不带着未验证策略开跑。
        if (snapshot.emailVerificationEnabled) {
            require(snapshot.mailType == BatchSendType.INTRODUCTION.name) {
                "发送前邮箱验证只支持介绍邮件（${BatchSendType.INTRODUCTION.name}），当前快照类型为 ${snapshot.mailType}"
            }
            batchEmailVerificationService.requireConfiguredApiKey()
        }
        // A3 (I-4)：执行开始读取一次模板内容并冻结为内存快照，本次执行的每条邮件都复用它渲染 ——
        // 运行中模板被改写不会与已冻结内容混用；读取失败（模板缺失/启用状态异常）时回退到逐条渲染，
        // 由发送期的模板错误明确暴露，绝不静默发错内容。
        val templateSnapshot = loadTemplateSnapshot(snapshot.mailType, snapshot.templateId)
        return when (snapshot.mailType) {
            BatchSendType.MATERIAL_REMINDER.name ->
                runMaterialFromSnapshot(snapshot, executionId, mode, oneRoundOnly, templateSnapshot)
            else ->
                runIntroductionFromSnapshot(snapshot, executionId, mode, oneRoundOnly, templateSnapshot)
        }
    }

    /** A3 (I-4)：执行/预估共用的模板内存快照读取；不可读时返回 null（调用方回退到既有渲染路径）。 */
    private fun loadTemplateSnapshot(mailType: String, templateId: Long?): ComposeTemplateSnapshot? =
        runCatching {
            when {
                templateId != null -> mailComposeTemplateService.loadSnapshot(templateId)
                mailType == BatchSendType.INTRODUCTION.name ->
                    mailComposeTemplateService.loadSnapshotByCode("INTRODUCTION")
                else -> null
            }
        }.getOrNull()

    /**
     * Enumerates material candidates on demand, preserving the existing delivery and binding path.
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
        oneRoundOnly: Boolean,
        templateSnapshot: ComposeTemplateSnapshot?
    ): ManualOutreachResult {
        log.info("Starting material reminder batch: executionId={}, mode={}, oneRoundOnly={}", executionId, mode, oneRoundOnly)
        val ignoreWarmup = mode == ExecutionMode.MANUAL
        val config = snapshot.toBatchSendConfig(BatchSendType.MATERIAL_REMINDER)
        val templateId = snapshot.templateId ?: error("MATERIAL_REMINDER config requires a templateId")
        val scope = resolveScope(snapshot)
        // I-1/I-2: 本次执行的唯一发件账号范围快照。
        val allowedAccountCodes = allowedAccountCodesOf(snapshot)

        val source = DemandCandidateSource(snapshot, scope, null, executionId, { it }, verificationFilterNow())
        var remainingRoundQuota = config.roundSize
        val targetIterator = source.iterator { demandPageSize(remainingRoundQuota) }
        val accumulator = OutcomeAccumulator.demand()
        val plannedSuccess = plannedSuccess(snapshot, oneRoundOnly)
        accumulator.executionMetadata = {
            com.weibo.talentintroduction.campaign.domain.BatchExecutionMetadata(
                plannedSuccess, maxOf(0L, plannedSuccess - accumulator.success.toLong()),
                source.scanned, targetIterator.sourceExhausted
            )
        }
        val totalEstimate = 0
        var wasCancelled = false
        val errors = mutableListOf<String>()
        val assignments = mutableListOf<SenderExpertAssignment>()
        val stock = senderAccountAssignmentService.loadBindingStock()
        val runAccountStats = mutableMapOf<String, AccountRunStat>()
        var roundNumber = 0
        var stopReason: String? = null
        var finalStatus: String? = null
        var processedTotal = 0

        try {
        while (true) {
            // Cancellation check
            if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                log.info("Material reminder batch cancelled after {} processed", processedTotal)
                wasCancelled = true
                stopReason = "CANCELLED"
                break
            }

            // Round budget (I-1/I-2): roundsPerRun bounds rounds started in THIS run only.
            if (roundNumber >= effectiveRounds(snapshot, oneRoundOnly)) {
                log.info("Reminder rounds per run exhausted after {} rounds (roundsPerRun={})", roundNumber, snapshot.roundsPerRun)
                stopReason = "ROUNDS_PER_RUN_REACHED"
                finalStatus = "COMPLETED"
                break
            }

            // Round gate — explicit TTL from MATERIAL_REMINDER config (K-self-check-ttl-type-scope)
            roundNumber++
            val sendable = runRoundGate(ignoreWarmup, config.selfCheckTtlMinutes, allowedAccountCodes)
            if (sendable.isEmpty()) {
                val outcome = classifyNoSendableOutcome(ignoreWarmup, allowedAccountCodes)
                log.warn("No sendable accounts at reminder round {}: stopReason={}", roundNumber, outcome.stopReason)
                stopReason = outcome.stopReason
                finalStatus = outcome.finalStatus
                break
            }

            val remainingAccountCapacity = sendable.sumOf {
                senderWarmupService.remainingCapacity(it, ignoreWarmup = ignoreWarmup).toLong()
            }
            val roundQuota = minOf(config.roundSize.toLong(), remainingAccountCapacity).toInt()
            if (roundQuota <= 0) {
                log.info("Reminder account capacity exhausted")
                when {
                    remainingAccountCapacity <= 0 -> {
                        val limitOutcome = classifyLimitReachedOutcome(sendable, ignoreWarmup)
                        stopReason = limitOutcome.stopReason
                        finalStatus = if (oneRoundOnly) "PAUSED" else "COMPLETED"
                    }
                }
                break
            }

            // 每轮额度只计成功发送；跳过和失败继续扫描后续目标补足。
            var roundProcessed = 0
            var roundPassed = 0
            var roundRejected = 0
            var midRoundStop = false

            while (roundPassed < roundQuota) {
                if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                    wasCancelled = true
                    stopReason = "CANCELLED"
                    midRoundStop = true
                    break
                }
                if (accumulator.success.toLong() + accumulator.failure + accumulator.skipped >= Int.MAX_VALUE) {
                    stopReason = "COUNT_LIMIT_REACHED"
                    finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
                    midRoundStop = true
                    break
                }
                remainingRoundQuota = roundQuota - roundPassed
                if (!targetIterator.hasNext()) break
                if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                    wasCancelled = true
                    stopReason = "CANCELLED"
                    midRoundStop = true
                    break
                }
                val (selectedContact, expert) = targetIterator.next()
                val contact = selectedContact!!
                if (!source.revalidate(expert, contact)) {
                    accumulator.recordSkipped(BatchOutcomeReasonCodes.CANDIDATE_CHANGED)
                    processedTotal++; roundProcessed++; roundRejected++
                    continue
                }

                val contactId = contact.id!!
                val email = contact.expertEmail
                val normOrcid = normalizeOrcid(expert.orcidId)

                if (email.isBlank() || emailSuppressionService.isSuppressed(email)) {
                    accumulator.recordSkipped(BatchOutcomeReasonCodes.SUPPRESSED, "已跳过抑制邮箱：$email")
                    processedTotal++; roundProcessed++; roundRejected++
                    updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                        "RUNNING", "已跳过抑制邮箱：$email", errors, mode, roundNumber, config, runAccountStats,
                        roundNumber, roundProcessed, roundPassed, roundRejected,
                        sendType = BatchSendType.MATERIAL_REMINDER, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                    continue
                }

                // I-3: 任一 expert_contact 行已有绑定（与绑定值是否在选中集合无关）→ 本次批量不发信、
                // 不重选号、不改绑。目标构造已过滤一次，这里在发送前对同一 ORCID 重查兜底竞态。
                if (contact.boundSenderAccountCode != null || hasBoundContact(normOrcid)) {
                    accumulator.recordSkipped(
                        BatchOutcomeReasonCodes.BOUND_SENDER_ALREADY_SET,
                        "专家已绑定发件账号：$normOrcid"
                    )
                    processedTotal++; roundProcessed++; roundRejected++
                    updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                        "RUNNING", "已跳过已绑定发件账号：$email", errors, mode, roundNumber, config, runAccountStats,
                        roundNumber, roundProcessed, roundPassed, roundRejected,
                        sendType = BatchSendType.MATERIAL_REMINDER, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                    continue
                }

                val account = try {
                    val picked = selectSendAccount(expert, assignments, ignoreWarmup, stock, allowedAccountCodes)
                    senderAccountBindingService
                        .bindIfAbsent(contactId, picked.accountCode, LocalDateTime.now())
                    picked
                } catch (ex: NoAvailableSenderAccountException) {
                    stopReason = "NO_AVAILABLE_ACCOUNT"
                    finalStatus = "PAUSED"
                    midRoundStop = true
                    break
                }

                val stat = runAccountStats.getOrPut(account.accountCode) { AccountRunStat() }
                val provider = providerResolver.resolve(email)

                try {
                    // I-6 double-check: skip if SENT MATERIAL_REMINDER already recorded
                    if (hasSentMaterialReminder(contactId)) {
                        log.info("SENT MATERIAL_REMINDER already exists for contact {}, skipping", contactId)
                        accumulator.recordSkipped(BatchOutcomeReasonCodes.DEDUP)
                        processedTotal++
                        roundProcessed++
                        roundRejected++
                        continue
                    }

                    if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                        wasCancelled = true
                        stopReason = "CANCELLED"
                        midRoundStop = true
                        break
                    }
                    if (!source.revalidate(expert, contact, checkBinding = false) ||
                        emailSuppressionService.isSuppressed(email)) {
                        accumulator.recordSkipped(BatchOutcomeReasonCodes.CANDIDATE_CHANGED)
                        processedTotal++; roundProcessed++; roundRejected++
                        continue
                    }
                    if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                        wasCancelled = true
                        stopReason = "CANCELLED"
                        midRoundStop = true
                        break
                    }
                    val command = ManualMailSendCommand(
                        optionType = ManualMailOptionType.COMPOSE_TEMPLATE.name,
                        optionValue = templateId.toString(),
                        senderAccountCode = account.accountCode
                    )
                    val result = if (templateSnapshot != null) {
                        manualExpertMailService.sendManualMailFromSnapshot(
                            contactId, command,
                            enforcePersonalizationGate = snapshot.gateFilterEnabled,
                            templateSnapshot = templateSnapshot
                        )
                    } else {
                        manualExpertMailService.sendManualMail(
                            contactId, command,
                            enforcePersonalizationGate = snapshot.gateFilterEnabled
                        )
                    }

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
                } catch (e: com.weibo.talentintroduction.expert.service.ExpertSearchUnavailableException) {
                    throw e
                } catch (e: PersonalizationGateException) {
                    accumulator.recordSkipped(
                        BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE,
                        "个性化字段缺失（${e.missingKeys.joinToString(",")}）：$email"
                    )
                    // 计数由循环公共收尾路径（processedTotal/roundProcessed）统一推进一次，
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
                roundProcessed++

                updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                    "RUNNING", "正在发送材料提醒：$email", errors, mode, roundNumber, config, runAccountStats,
                    roundNumber, roundProcessed, roundPassed, roundRejected,
                    sendType = BatchSendType.MATERIAL_REMINDER, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)

                val intervalMs = accountRateLimiter.getIntervalMs(account.accountCode, provider, config.perMailIntervalMs)
                if (intervalMs > 0 && roundPassed < roundQuota && !targetIterator.sourceExhausted &&
                    !progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                    try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                }
            }

            if (midRoundStop) break
            if (targetIterator.sourceExhausted) break

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
            if (roundNumber < effectiveRounds(snapshot, oneRoundOnly) && config.perRoundIntervalMs > 0 &&
                !targetIterator.sourceExhausted && !progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                try { Thread.sleep(config.perRoundIntervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
        }
        } catch (ex: com.weibo.talentintroduction.expert.service.ExpertSearchUnavailableException) {
            stopReason = "CANDIDATE_READ_FAILED"
            finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
            errors.add("候选读取失败：${ex.message.orEmpty().take(160)}")
        } finally {
            try { targetIterator.close() } catch (ex: Exception) {
                stopReason = "CANDIDATE_READ_FAILED"
                finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
                errors.add("候选游标关闭失败：${ex.message.orEmpty().take(160)}")
            }
        }

        if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
            wasCancelled = true
            stopReason = "CANCELLED"
        }
        val resolvedFinalStatus = when {
            wasCancelled -> "CANCELLED"
            finalStatus != null -> finalStatus
            else -> "COMPLETED"
        }
        val finalMessage = stopReasonMessage(resolvedFinalStatus, stopReason, ignoreWarmup, allowedAccountCodes)
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
            PendingOutreachSummary(pending = snapshot.targetCount, retryable = 0, totalSendable = snapshot.targetCount)
        }
    }

    /**
     * I4a-4: 门禁 ES **粗筛**字段解析的唯一 seam。预估与执行共用，保证 M-4 同源。
     * I4a-1: 开关关闭 / 无模板 / 模板无 required_keys → gateEsFields 为空，零行为变化。
     * I4a-3: requiredEsFields 只返回"每个可能渲染都必需且可映射 ES 字段"的键，因此这里的
     * `gateEsFields` 是**不会错误排除**的安全子集；非 ES 字段（familyNames/keyword/country/
     * hIndex/worksCount/lastPublicationYear）不在此预筛 —— A3 起它们由 [batchTemplateGate]
     * 用与发送期 compose 同源的**精确**模板门禁逐人判定，不再"丢掉后假称检查完成"。
     */
    private fun resolveScope(snapshot: BatchExecutionSnapshot): RecipientScope {
        val base = RecipientScope.fromSnapshot(snapshot)
        if (!snapshot.gateFilterEnabled) return base
        val templateId = snapshot.templateId ?: return base
        val required = mailComposeTemplateService.requiredEsFields(templateId)
        if (required.isEmpty()) return base
        val usable = required.filter { it in ExpertSearchService.ALLOWED_HAS_FIELDS }
        val dropped = required - usable.toSet()
        if (dropped.isNotEmpty()) {
            log.info(
                "Gate filter: {} of template {} cannot be pre-filtered (not in ALLOWED_HAS_FIELDS), dropped: {}",
                dropped.size, templateId, dropped
            )
        }
        return base.copy(gateEsFields = usable)
    }

    /**
     * Side-effect-free recipient preview (P-F / 06). Same target computation as the execution
     * path (I-1): resolveScope + buildRetryableTargets + countEsTargets for
     * INTRODUCTION, buildMaterialReminderSnapshot(scope, config).targets.size for MATERIAL_REMINDER.
     * Reads the campaign read-only via findByCampaignCode (I-3): never getOrCreateManualCampaign().
     *
     * A3: 响应同时冻结模板版本摘要、准入计数、原因命中与逐人原因 key（06 数据源）；INTRODUCTION 的
     * 门禁开启时，目标在 selector 之后再过一次**精确**模板门禁（[batchTemplateGate]），预估与执行同判定。
     */
    fun countBySnapshot(snapshot: BatchExecutionSnapshot): PendingOutreachSummary = when (snapshot.mailType) {
        BatchSendType.MATERIAL_REMINDER.name -> {
            val scope = resolveScope(snapshot)
            val accumulator = AdmissionPreviewAccumulator()
            val materialSnapshot = buildMaterialReminderSnapshot(
                scope, verificationFilterNow(), onSelection = accumulator::record
            )
            PendingOutreachSummary(
                pending = materialSnapshot.targetCount,
                retryable = 0,
                totalSendable = materialSnapshot.targetCount,
                excludedVerifiedUnavailable = materialSnapshot.excludedVerifiedUnavailable,
                template = previewTemplateVersion(snapshot),
                admission = accumulator.counts(materialSnapshot.targetCount),
                reasonHits = accumulator.reasonHits(),
                excludedRecipients = accumulator.excludedRecipients(PREVIEW_EXCLUDED_DETAIL_LIMIT)
            )
        }
        else -> {
            val scope = resolveScope(snapshot)
            val now = verificationFilterNow()
            val accumulator = AdmissionPreviewAccumulator()
            // A3 (I-4)：门禁开启时用精确模板门禁（与发送期 compose 同源），不再是 ES 字段白名单子集。
            val gate = batchTemplateGate(snapshot)
            var retryable = 0
            var excluded = 0
            val seenOrcids = mutableSetOf<String>()
            val seenPersonKeys = mutableSetOf<String>()
            val seenDocIds = mutableSetOf<String>()
            val campaign = campaignRepository.findByCampaignCode("MANUAL_OUTREACH")
            if (campaign != null) {
                val campaignId = campaign.id ?: error("Campaign ID is null")
                val retry = buildFilteredRetryableTargets(campaignId, scope, now, seenDocIds, gate, accumulator::record)
                retryable = retry.targets.size
                excluded += retry.excluded
                seenOrcids.addAll(retry.seenOrcids)
                seenPersonKeys.addAll(retry.seenPersonKeys)
            }
            val (esEstimate, esExcluded) = countEsTargets(
                scope, now, gate = gate, onSelection = accumulator::record,
                initialSeenOrcids = seenOrcids, initialSeenDocIds = seenDocIds,
                initialSeenPersonKeys = seenPersonKeys
            )
            excluded += esExcluded
            // I-1/I-2（02）：完整母集只读预检查 —— 母集仅按所选层级/标签读取（不再被地区/状态/模板提前缩小），
            // 保护/模板/邮箱原因独立表达；新增字段为兼容扩展，旧字段含义逐字保留。
            val preflight = preflightForSnapshot(snapshot, scope)
            PendingOutreachSummary(
                pending = esEstimate,
                retryable = retryable,
                totalSendable = esEstimate + retryable,
                excludedVerifiedUnavailable = excluded,
                template = previewTemplateVersion(snapshot),
                admission = accumulator.counts(esEstimate + retryable),
                reasonHits = accumulator.reasonHits(),
                excludedRecipients = accumulator.excludedRecipients(PREVIEW_EXCLUDED_DETAIL_LIMIT),
                baseline = preflight?.baseline,
                excludedUnique = preflight?.excludedUnique,
                eligibleBeforeLiveVerification = preflight?.eligibleBeforeLiveVerification,
                awaitingVerification = preflight?.awaitingVerification,
                verificationPhase = preflight?.verificationPhase?.name,
                partial = preflight?.partial ?: false,
                asOf = preflight?.asOf,
                reasonHitsDetail = preflight?.reasonHits.orEmpty()
            )
        }
    }

    /**
     * I-1/I-4（02）：只读统一预检查 —— 母集按「所选漏斗层级 + 标签」读取 ES 候选并并入同层级 NEW 重试候选，
     * 按真实 docId 去重后交给 [BatchRecipientPreflightService] 逐人解释。**零业务写入**：只 SELECT/ES 读取，
     * 不调用 verify()/SMTP/供应商，也不创建任何 campaign/contact/task/发送尝试。
     *
     * `shouldStop` 只为取消语义保留；母集超限时返回 `partial=true`，绝不把截断结果当完整总数。
     */
    private fun preflightForSnapshot(
        snapshot: BatchExecutionSnapshot,
        scope: RecipientScope,
        shouldStop: () -> Boolean = { false }
    ): BatchRecipientPreflightResult? {
        val preflightService = batchRecipientPreflightService ?: return null
        val scan = scanBaseCandidates(scope, shouldStop)
        return preflightService.preflight(preflightInput(snapshot, scope, scan.profiles, scan.partial))
    }

    /**
     * repair V-2（epoch 2）：同一快照的只读预检查输入 —— 有界母集扫描（partial）与执行期的
     * 逐候选复核共用同一开关/时点/门禁口径，保证两条路径判定同源。
     */
    private fun preflightInput(
        snapshot: BatchExecutionSnapshot,
        scope: RecipientScope,
        profiles: List<ExpertProfile>,
        partial: Boolean
    ): BatchRecipientPreflightInput = BatchRecipientPreflightInput(
        scope = scope,
        profiles = profiles,
        // I-6：模板门禁只对 INTRODUCTION 生效（与 batchTemplateGate 同边界）。
        gateFilterEnabled = snapshot.gateFilterEnabled && scope.mailType == BatchSendType.INTRODUCTION.name,
        templateId = snapshot.templateId,
        emailVerificationEnabled = snapshot.emailVerificationEnabled,
        emailVerificationAllowedStates = snapshot.emailVerificationAllowedStates,
        excludeVerifiedUnavailableEmails = snapshot.excludeVerifiedUnavailableEmails,
        asOf = verificationFilterNow(),
        partial = partial
    )

    /**
     * I-1/I-2（02）：诊断母集扫描 —— ES 侧只应用层级与标签（[baseRecipientScope]），
     * 并并入同层级 NEW 重试候选（按既有身份键合并、真实 docId 去重）。地区/状态/方向/类型/模板门禁
     * 一律留给预检查逐人解释，绝不在扫描阶段提前丢人。
     */
    private fun scanBaseCandidates(scope: RecipientScope, shouldStop: () -> Boolean = { false }): BaseScan {
        val baseScope = baseRecipientScope(scope)
        val collected = LinkedHashMap<String, ExpertProfile>()
        var partial = false
        for (level in baseScope.funnelLevels) {
            if (shouldStop()) {
                partial = true
                break
            }
            expertSearchService.scrollExpertsFiltered(
                level = ExpertIndexLevel.valueOf(level),
                filters = buildBaseEsFilters(baseScope),
                batchSize = BASE_SCAN_BATCH_SIZE
            ) { batch ->
                for (profile in batch) {
                    if (collected.size >= MAX_BASELINE_SCAN) {
                        partial = true
                        return@scrollExpertsFiltered false
                    }
                    collected.putIfAbsent(BatchRecipientSelectionService.docIdOf(profile), profile)
                }
                !shouldStop()
            }
        }
        if (baseScope.mailType == BatchSendType.INTRODUCTION.name && !shouldStop()) {
            campaignRepository.findByCampaignCode("MANUAL_OUTREACH")?.id?.let { campaignId ->
                loadRetryCandidateProfiles(campaignId, baseScope.funnelLevels).forEach { profile ->
                    collected.putIfAbsent(BatchRecipientSelectionService.docIdOf(profile), profile)
                }
            }
        }
        return BaseScan(collected.values.toList(), partial)
    }

    /**
     * I-1（02）：只承载层级与标签的母集扫描范围（其余维度都是逐人原因，不是母集条件）。
     */
    private fun baseRecipientScope(scope: RecipientScope): RecipientScope = RecipientScope(
        mailType = scope.mailType,
        funnelLevels = scope.funnelLevels,
        tags = scope.tags,
        regions = emptyList(),
        emailDomains = emptyList(),
        discipline = null
    )

    /**
     * I-1（02）/ repair V-1：母集扫描的 ES 过滤器 —— 只有（可选）标签，**不**追加地区/状态/方向/
     * 类型/模板门禁，也**不**追加 `exists email`（地址缺失的母集成员必须留在母集里，由预检查作为
     * 确定性原因解释，绝不静默丢出诊断），更不追加 INTRODUCTION 空类型集合的 `MATCH_NONE_FILTER`
     * （那会把母集误扫成 0）。这些维度一律留给预检查逐人解释。
     */
    private fun buildBaseEsFilters(baseScope: RecipientScope): List<Map<String, Any>> =
        if (baseScope.tags.isEmpty()) {
            emptyList()
        } else {
            listOf(mapOf("terms" to mapOf("tags" to baseScope.tags)))
        }

    /**
     * I-1/I-2（02）：NEW 重试候选只读加载 —— 与执行构造同源（同 campaign、同 current_status、同层级索引），
     * 不做发送/绑定/抑制/地址保护过滤（那些由预检查作为独立原因解释）。
     */
    private fun loadRetryCandidateProfiles(campaignId: Long, levels: Set<String>): List<ExpertProfile> {
        val contacts = expertContactRepository
            .findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW")
        if (contacts.isEmpty()) return emptyList()
        val orcidIds = contacts.map { it.orcidId }
        val profilesByLevel = levels.associateWith { level ->
            expertSearchService.searchByOrcidIds(orcidIds, ExpertIndexLevel.valueOf(level))
                .associateBy { normalizeOrcid(it.orcidId) }
        }
        return contacts.mapNotNull { contact ->
            val normOrcid = normalizeOrcid(contact.orcidId)
            levels.asSequence().mapNotNull { profilesByLevel[it]?.get(normOrcid) }.firstOrNull()
        }
    }

    /**
     * I-4/I-5（02）：只读「按 docId 诊断」入口 —— 为操作端逐个解释指定目标。
     * `docIds` 1..200；母集外显式 `OUTSIDE_BASELINE`，任何层级都找不到显式 `NOT_FOUND`，绝不静默吞掉。
     */
    fun diagnoseRecipients(
        snapshot: BatchExecutionSnapshot,
        docIds: List<String>
    ): BatchRecipientDiagnosticsResponse {
        val requested = docIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(requested.isNotEmpty()) { "docIds 不能为空" }
        require(requested.size <= BatchRecipientPreflightService.MAX_DIAGNOSTIC_DOC_IDS) {
            "单次诊断最多 ${BatchRecipientPreflightService.MAX_DIAGNOSTIC_DOC_IDS} 个 docId"
        }
        val scope = resolveScope(snapshot)
        val scan = scanBaseCandidates(scope)
        val preflightService = requireNotNull(batchRecipientPreflightService) {
            "只读预检查服务未配置，无法诊断（Spring 环境恒注入）"
        }
        val preflight = preflightService.preflight(preflightInput(snapshot, scope, scan.profiles, scan.partial))
        val byDoc = preflight.decisions.associateBy { it.docId }
        val existing = existingDocIds(requested.filter { it !in byDoc })
        val entries = requested.map { docId ->
            val decision = byDoc[docId]
            if (decision != null) {
                BatchRecipientDiagnosticEntry(
                    docId = docId,
                    status = BatchRecipientDiagnosticStatus.OK,
                    orcidId = decision.orcidId,
                    admissionState = decision.admissionState,
                    eligible = decision.eligible,
                    admissionReasonKeys = decision.admissionReasonKeys,
                    conditionReasonKeys = decision.conditionReasonKeys,
                    protectionReasonKeys = decision.protectionReasonKeys,
                    templateMissingKeys = decision.templateMissingKeys,
                    verificationPhase = decision.verificationPhase.name
                )
            } else {
                BatchRecipientDiagnosticEntry(
                    docId = docId,
                    status = if (docId in existing) {
                        BatchRecipientDiagnosticStatus.OUTSIDE_BASELINE
                    } else {
                        BatchRecipientDiagnosticStatus.NOT_FOUND
                    }
                )
            }
        }
        return BatchRecipientDiagnosticsResponse(
            baseline = preflight.baseline,
            excludedUnique = preflight.excludedUnique,
            eligibleBeforeLiveVerification = preflight.eligibleBeforeLiveVerification,
            awaitingVerification = preflight.awaitingVerification,
            reasonHits = preflight.reasonHits,
            asOf = preflight.asOf,
            partial = preflight.partial,
            verificationPhase = preflight.verificationPhase.name,
            recipients = entries
        )
    }

    /** 只在所选母集之外的请求 docId 上做一次跨层级存在性确认（区分 OUTSIDE_BASELINE / NOT_FOUND）。 */
    private fun existingDocIds(docIds: List<String>): Set<String> {
        if (docIds.isEmpty()) return emptySet()
        val out = HashSet<String>(docIds.size)
        for (level in ExpertIndexLevel.values()) {
            expertSearchService.findByDocumentIds(level, docIds).forEach { profile ->
                out += BatchRecipientSelectionService.docIdOf(profile)
            }
        }
        return out
    }

    /** A3 (I-4)：预估冻结的模板版本摘要；无模板或不可读时为 null。 */
    private fun previewTemplateVersion(snapshot: BatchExecutionSnapshot): PreviewTemplateVersion? {
        val templateId = snapshot.templateId ?: return null
        val loaded = runCatching { mailComposeTemplateService.loadSnapshot(templateId) }.getOrNull() ?: return null
        return PreviewTemplateVersion(
            templateId = loaded.templateId,
            versionToken = loaded.versionToken,
            enabled = loaded.enabled,
            mailType = loaded.mailType
        )
    }

    /**
     * A3 (I-4): INTRODUCTION 批量模板门禁的**精确**判定过滤器 —— 与发送期 compose 使用同一模板
     * ID/seed/实际选中变体与实际变量值，只对**专家变量**判定；账号变量缺项是启动前配置错误
     * （[com.weibo.talentintroduction.campaign.service.BatchSendControlService.validateTemplateAtLaunch]），
     * 绝不用 `account=null` 把全部专家筛掉。门禁关闭 / 非 INTRODUCTION 时为 no-op。
     */
    private fun batchTemplateGate(snapshot: BatchExecutionSnapshot): (List<ExpertProfile>) -> List<ExpertProfile> {
        if (!snapshot.gateFilterEnabled || snapshot.mailType != BatchSendType.INTRODUCTION.name) return { it }
        val templateId = snapshot.templateId
        return { profiles -> profiles.filter { !batchGateExcludes(templateId, it) } }
    }

    private fun batchGateExcludes(templateId: Long?, profile: ExpertProfile): Boolean {
        // I-6/I-2（02）：模板门禁的逐人判定收口在只读预检查服务，预估与执行共用同一实现。
        // Spring 恒注入；仅历史手工构造的测试夹具（不传该依赖）回退到逐字相同的判定。
        val preflight = batchRecipientPreflightService
            ?: return introductionMailComposer.evaluateForBatch(null, profile, templateId)
                .missingKeys.any { it !in MailComposeTemplateService.ACCOUNT_VARIABLE_KEYS }
        return preflight.templateGateExcludes(profile, templateId)
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
        val snapshot = config.toSnapshot(oneRoundOnly = oneRoundOnly)
        return runIntroductionFromSnapshot(
            snapshot,
            executionId,
            mode,
            oneRoundOnly = oneRoundOnly,
            templateSnapshot = loadTemplateSnapshot(snapshot.mailType, snapshot.templateId)
        )
    }

    private fun runIntroductionFromSnapshot(
        snapshot: BatchExecutionSnapshot,
        executionId: Long,
        mode: ExecutionMode,
        oneRoundOnly: Boolean,
        templateSnapshot: ComposeTemplateSnapshot?
    ): ManualOutreachResult {
        log.info("Starting scheduled batch outreach, executionId={}, mode={}, oneRoundOnly={}", executionId, mode, oneRoundOnly)
        val ignoreWarmup = mode == ExecutionMode.MANUAL
        val campaign = getOrCreateManualCampaign()
        val campaignId = campaign.id ?: error("Campaign ID is null")
        val config = snapshot.toBatchSendConfig(BatchSendType.INTRODUCTION)
        val scope = resolveScope(snapshot)
        // I-1/I-2: 本次执行的唯一发件账号范围快照。
        val allowedAccountCodes = allowedAccountCodesOf(snapshot)
        // I-1/I-2/I-7：验证开关与逐次执行上下文。关闭时不建立上下文 —— 零 HTTP、零验证明细仓储调用。
        val emailVerificationEnabled = snapshot.emailVerificationEnabled
        // I-1：本次执行的放行集合只来自启动快照 —— 入口防御性复核并复制有效集合，绝不读取可变配置。
        val emailVerificationAllowedStates = EmailVerificationAllowedStates.normalize(snapshot.emailVerificationAllowedStates)
        val verificationContext = if (emailVerificationEnabled) {
            batchEmailVerificationService.beginExecution(executionId) {
                progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)
            }
        } else {
            null
        }
        val filterNow = verificationFilterNow()
        // A3 (I-4)：预估（countBySnapshot）与执行共用的精确模板门禁；门禁关闭时为 no-op。
        val gate = batchTemplateGate(snapshot)
        val source = DemandCandidateSource(snapshot, scope, campaignId, executionId, gate, filterNow)
        var remainingRoundQuota = config.roundSize
        val targetIterator = source.iterator { demandPageSize(remainingRoundQuota) }
        val accumulator = OutcomeAccumulator.demand()
        val plannedSuccess = plannedSuccess(snapshot, oneRoundOnly)
        accumulator.executionMetadata = {
            com.weibo.talentintroduction.campaign.domain.BatchExecutionMetadata(
                plannedSuccess, maxOf(0L, plannedSuccess - accumulator.success.toLong()),
                source.scanned, targetIterator.sourceExhausted
            )
        }
        val totalEstimate = 0 // Demand totals are supplied by the accumulator, never by a population estimate.
        var wasCancelled = false
        val errors = mutableListOf<String>()
        val assignments = mutableListOf<SenderExpertAssignment>()
        val stock = senderAccountAssignmentService.loadBindingStock()
        val runAccountStats = mutableMapOf<String, AccountRunStat>()
        var roundNumber = 0
        var stopReason: String? = null
        var finalStatus: String? = null
        var processedTotal = 0

        try {
        while (true) {
            // 1. Cancellation check (I-1: single flow, operator pause → requestCancel)
            if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                log.info("Cancelled after {} processed", processedTotal)
                wasCancelled = true
                stopReason = "CANCELLED"
                break
            }

            // Round budget (I-1/I-2): roundsPerRun bounds rounds started in THIS run only.
            if (roundNumber >= effectiveRounds(snapshot, oneRoundOnly)) {
                log.info("Rounds per run exhausted after {} rounds (roundsPerRun={})", roundNumber, snapshot.roundsPerRun)
                stopReason = "ROUNDS_PER_RUN_REACHED"
                finalStatus = "COMPLETED"
                break
            }

            // 2. Round gate (L3-1): list sendable → self-check uncached → re-list sendable
            roundNumber++
            val sendable = runRoundGate(ignoreWarmup, config.selfCheckTtlMinutes, allowedAccountCodes)
            if (sendable.isEmpty()) {
                val outcome = classifyNoSendableOutcome(ignoreWarmup, allowedAccountCodes)
                log.warn("No sendable accounts at round {}: stopReason={}, finalStatus={}", roundNumber, outcome.stopReason, outcome.finalStatus)
                stopReason = outcome.stopReason
                finalStatus = outcome.finalStatus
                break
            }

            // Only current account capacity and the configured successful-send budget authorize this round.
            val remainingAccountCapacity = sendable.sumOf {
                senderWarmupService.remainingCapacity(it, ignoreWarmup = ignoreWarmup).toLong()
            }
            val roundQuota = minOf(config.roundSize.toLong(), remainingAccountCapacity).toInt()
            if (roundQuota <= 0) {
                log.info("Round capacity exhausted at round {}", roundNumber)
                when {
                    remainingAccountCapacity <= 0 -> {
                        val limitOutcome = classifyLimitReachedOutcome(sendable, ignoreWarmup)
                        stopReason = limitOutcome.stopReason
                        finalStatus = if (oneRoundOnly) "PAUSED" else "COMPLETED"
                    }
                }
                break
            }

            // 4. 每轮额度只计成功发送；跳过和失败继续扫描后续目标补足。
            var roundProcessed = 0
            var roundPassed = 0
            var roundRejected = 0
            var midRoundStop = false
            while (roundPassed < roundQuota) {
                if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                    wasCancelled = true
                    stopReason = "CANCELLED"
                    midRoundStop = true
                    break
                }
                if (accumulator.success.toLong() + accumulator.failure + accumulator.skipped >= Int.MAX_VALUE) {
                    stopReason = "COUNT_LIMIT_REACHED"
                    finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
                    midRoundStop = true
                    break
                }
                remainingRoundQuota = roundQuota - roundPassed
                if (!targetIterator.hasNext()) break
                if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                    wasCancelled = true
                    stopReason = "CANCELLED"
                    midRoundStop = true
                    break
                }
                val (existingContact, expert) = targetIterator.next()
                val normOrcid = normalizeOrcid(expert.orcidId)
                if (!source.revalidate(expert, existingContact)) {
                    accumulator.recordSkipped(BatchOutcomeReasonCodes.CANDIDATE_CHANGED)
                    processedTotal++; roundProcessed++; roundRejected++
                    continue
                }

                // I-1/I-3: 发现准入与显式条件已由统一 selector 在取页/重试构造时判定；
                // 这里保留廉价的内存类型复核，兜住任何绕过查询侧的残余路径。
                if (scope.mailType == BatchSendType.INTRODUCTION.name && !scope.matchesExpertType(expert)) {
                    accumulator.recordSkipped(
                        BatchOutcomeReasonCodes.EXPERT_NOT_SENDABLE,
                        "研发类型不在本次选择范围内：${expert.orcidId}"
                    )
                    processedTotal++
                    roundProcessed++
                    roundRejected++
                    updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                        "RUNNING", "研发类型不在本次选择范围内：${expert.orcidId}", errors, mode, roundNumber, config, runAccountStats,
                        roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                    continue
                }

                val email = expert.email
                if (email.isNullOrBlank() || emailSuppressionService.isSuppressed(email)) {
                    accumulator.recordSkipped(BatchOutcomeReasonCodes.SUPPRESSED, "已跳过抑制邮箱：${email ?: ""}")
                    processedTotal++
                    roundProcessed++
                    roundRejected++
                    updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                        "RUNNING", "已跳过抑制邮箱：${email ?: ""}", errors, mode, roundNumber, config, runAccountStats,
                        roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                    continue
                }

                // I-3: 任一 expert_contact 行已有绑定（与绑定值是否在选中集合无关）→ 本次批量不发信、
                // 不重选号、不改绑。NEW 重试在目标构造时已过滤；ES 页在此发送前对同一 ORCID 重查。
                if (existingContact?.boundSenderAccountCode != null || hasBoundContact(normOrcid)) {
                    accumulator.recordSkipped(
                        BatchOutcomeReasonCodes.BOUND_SENDER_ALREADY_SET,
                        "专家已绑定发件账号：${expert.orcidId}"
                    )
                    processedTotal++
                    roundProcessed++
                    roundRejected++
                    updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                        "RUNNING", "已跳过已绑定发件账号：${expert.email}", errors, mode, roundNumber, config, runAccountStats,
                        roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                    continue
                }

                // ── I-2/I-3/I-6：发送前邮箱验证 ──
                // 位于类型/抑制/已绑定门禁之后、选号之前：非通过目标不新建/绑定 contact、
                // 不写 PREPARED、不调用 SMTP、不计账号发送量；通过目标仍走原全部门禁。
                var verified: VerificationResult.Passed? = null
                if (emailVerificationEnabled) {
                    // I-3：验证前检查取消 —— 取消后不再插入明细、不发请求、不建行。
                    if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                        wasCancelled = true
                        stopReason = "CANCELLED"
                        midRoundStop = true
                        break
                    }
                    val outcome = try {
                        batchEmailVerificationService.verify(
                            verificationContext!!,
                            EmailVerificationTarget(
                                expertDocId = expert.esDocId,
                                orcidId = normOrcid,
                                expertName = expert.displayName,
                                email = email
                            ),
                            // I-1/I-2：本次执行的放行集合来自启动快照（旧请求缺字段 = 三态全放行）。
                            emailVerificationAllowedStates
                        )
                    } catch (e: EmailVerificationAuditException) {
                        log.error("Email verification audit unavailable for ORCID {}: {}", normOrcid, e.message)
                        errors.add("验证审计写入失败：${e.message.orEmpty().take(120)}")
                        stopReason = STOP_EMAIL_VERIFY_AUDIT_FAILED
                        finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
                        midRoundStop = true
                        break
                    }
                    when (outcome) {
                        is VerificationResult.Rejected -> {
                            accumulator.recordSkipped(
                                BatchOutcomeReasonCodes.EMAIL_VERIFICATION_REJECTED,
                                "邮箱验证未通过：$email"
                            )
                            processedTotal++
                            roundProcessed++
                            roundRejected++
                            updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                                "RUNNING", "已跳过邮箱验证未通过：$email", errors, mode, roundNumber, config, runAccountStats,
                                roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                            continue
                        }
                        is VerificationResult.PolicySkipped -> {
                            // I-2/I-3：供应商结果明确但不在本次放行集合内 —— 只记策略跳过，
                            // 不打「邮箱异常」标签、不建/绑 contact、不选号、不发 SMTP；继续补足本轮成功数。
                            accumulator.recordSkipped(
                                BatchOutcomeReasonCodes.EMAIL_VERIFICATION_POLICY_SKIP,
                                "不在本次放行范围：$email"
                            )
                            processedTotal++
                            roundProcessed++
                            roundRejected++
                            updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                                "RUNNING", "已跳过不在本次放行范围的邮箱：$email", errors, mode, roundNumber, config, runAccountStats,
                                roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                            continue
                        }
                        is VerificationResult.ServiceFailure -> {
                            if (BatchEmailVerificationErrorCodes.isRecipientFailure(outcome.errorCode)) {
                                log.warn("Email verification deferred for ORCID {}: {}", normOrcid, outcome.errorCode)
                                try {
                                    batchEmailVerificationService.recordSend(
                                        outcome.rowId, BatchEmailVerificationSendStatus.SKIPPED,
                                        BatchOutcomeReasonCodes.EMAIL_VERIFICATION_DEFERRED
                                    )
                                } catch (e: EmailVerificationAuditException) {
                                    errors.add("验证审计写入失败：${e.message.orEmpty().take(120)}")
                                    stopReason = STOP_EMAIL_VERIFY_AUDIT_FAILED
                                    finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
                                    midRoundStop = true
                                    break
                                }
                                accumulator.recordSkipped(
                                    BatchOutcomeReasonCodes.EMAIL_VERIFICATION_DEFERRED,
                                    "邮箱验证暂缓：${outcome.errorCode}（$email）"
                                )
                                processedTotal++
                                roundProcessed++
                                roundRejected++
                                updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                                    "RUNNING", "邮箱验证暂缓：${outcome.errorCode}（$email）", errors, mode, roundNumber, config, runAccountStats,
                                    roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                                continue
                            }
                            log.warn("Email verification service failure for ORCID {}: {}", normOrcid, outcome.errorCode)
                            errors.add("邮箱验证服务故障：${outcome.errorCode}（$email）")
                            stopReason = outcome.errorCode
                            finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
                            midRoundStop = true
                            break
                        }
                        is VerificationResult.Cancelled -> {
                            outcome.rowId?.let {
                                recordVerificationSendQuietly(it, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.CANCELLED)
                            }
                            wasCancelled = true
                            stopReason = "CANCELLED"
                            midRoundStop = true
                            break
                        }
                        is VerificationResult.Passed -> verified = outcome
                    }
                }

                val account = try {
                    selectSendAccount(expert, assignments, ignoreWarmup, stock, allowedAccountCodes)
                } catch (e: NoAvailableSenderAccountException) {
                    log.warn("No available sender account mid-round after {} processed, pausing flow", processedTotal)
                    verified?.let {
                        recordVerificationSendQuietly(it.rowId, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.ACCOUNT_UNAVAILABLE)
                    }
                    stopReason = "NO_AVAILABLE_ACCOUNT"
                    finalStatus = "PAUSED"
                    midRoundStop = true
                    break
                } catch (e: Exception) {
                    log.error("System error selecting account", e)
                    verified?.let {
                        recordVerificationSendQuietly(it.rowId, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.SEND_EXCEPTION)
                    }
                    stopReason = "SYSTEM_ERROR"
                    finalStatus = "FAILED"
                    errors.add("系统错误: ${e.message ?: "Unknown error"}")
                    midRoundStop = true
                    break
                }

                val stat = runAccountStats.getOrPut(account.accountCode) { AccountRunStat() }
                val provider = providerResolver.resolve(expert.email)
                // I-4（03）：历史首封永久失败事实门禁（与 NEW 重试构造共用同一谓词）。
                // 位置在「contact 确定/原 SENT 去重检查」同一段门禁内、建行与写 PREPARED 与 SMTP 之前：
                // ES 页重新出现的同一 ORCID 走同一事实（按 ORCID 读既有行），不会因重试集合排除而绕过。
                // 跳过不新建/绑定 contact、不更新发送尝试、不调 SMTP、不计本次 failed/sent、不占额度。
                if (hasPermanentFirstMailFailure(introductionHistoryContactIds(normOrcid, existingContact))) {
                    log.info("Permanent introduction failure already recorded for ORCID {}, blocking automatic resend", normOrcid)
                    accumulator.recordSkipped(
                        BatchOutcomeReasonCodes.SEND_EXCEPTION,
                        "历史首封永久失败，需人工处理：${expert.email}"
                    )
                    processedTotal++
                    roundProcessed++
                    // I-6：已 PASS 验证的目标在此跳过时保留 NOT_SENT + 具体原因。
                    recordVerificationSend(verified, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.SEND_EXCEPTION)
                    continue
                }
                // I-6：区分「SMTP 前失败（NOT_SENT）」与「SMTP 结果不明（保持 SENDING）」。
                var smtpAttempted = false
                var accountFaulted = false
                // 09 (I-1/I-2/I-3): 人物级首发占位凭据（非 discovered 或 guard 未注入时为 null）。
                val guard = outreachGuardService
                var guardTicket: OutreachGuardTicket? = null

                try {
                    // 09 (I-1/I-2/I-3) + V-1：discovered INTRODUCTION 先占人物级首发许可 ——
                    // 未获许可绝不新建/复用 contact、不改 attempt、不发 SMTP。
                    // 总开关关闭且**无人物映射/保护**时逐字保持旧路径（不调 guard、不写 guard 状态）。
                    if (guard != null && scope.mailType == BatchSendType.INTRODUCTION.name &&
                        personGuardApplies(guard, expert)
                    ) {
                        when (val outcome = guard.reserveIntroduction(
                            selectedDocId = BatchRecipientSelectionService.docIdOf(expert),
                            recipient = expert.email.orEmpty(),
                            profile = expert,
                            // V-26：NEW 重试复用既有 contact 行时声明本次身份，避免自己的行被判成旧历史。
                            attempt = OutreachAttemptIdentity(contactId = existingContact?.id)
                        )) {
                            is OutreachReserveOutcome.Granted -> guardTicket = outcome.ticket
                            is OutreachReserveOutcome.Denied -> {
                                log.info("Person-level outreach guard denied for {}: {}", normOrcid, outcome.code)
                                accumulator.recordSkipped(outcome.code, "人物级首发保护：${outcome.code}（${expert.email}）")
                                processedTotal++
                                roundProcessed++
                                roundRejected++
                                recordVerificationSend(verified, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.SEND_EXCEPTION)
                                updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                                    "RUNNING", "人物级首发保护：${outcome.code}", errors, mode, roundNumber, config, runAccountStats,
                                    roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                                continue
                            }
                        }
                    }

                    val messageId = "<manual-outreach-${normOrcid}-${UUID.randomUUID()}@weibo.com>"
                    val mail = try {
                        if (templateSnapshot != null) {
                            introductionMailComposer.composeFromSnapshot(
                                account.accountCode, expert, config.templateId,
                                enforcePersonalizationGate = snapshot.gateFilterEnabled,
                                snapshot = templateSnapshot
                            ).copy(messageId = messageId)
                        } else {
                            introductionMailComposer.compose(
                                account.accountCode, expert, config.templateId,
                                enforcePersonalizationGate = snapshot.gateFilterEnabled
                            ).copy(messageId = messageId)
                        }
                    } catch (e: InvalidRenderedSubjectException) {
                        val message = "主题渲染不合法（${e.reason}）：${expert.email}"
                        log.info("Skipping ORCID {}: {}", normOrcid, e.reason)
                        accumulator.recordSkipped(BatchOutcomeReasonCodes.INVALID_RENDERED_SUBJECT, message)
                        roundRejected++
                        processedTotal++
                        roundProcessed++
                        recordVerificationSend(verified, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.INVALID_RENDERED_SUBJECT)
                        updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                            "RUNNING", message, errors, mode, roundNumber, config, runAccountStats,
                            roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                        continue
                    } catch (e: PersonalizationGateException) {
                        log.info("Personalization gate blocked ORCID {}: missing keys {}", normOrcid, e.missingKeys)
                        accumulator.recordSkipped(
                            BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE,
                            "个性化字段缺失（${e.missingKeys.joinToString(",")}）：${expert.email}"
                        )
                        roundRejected++
                        processedTotal++
                        roundProcessed++
                        recordVerificationSend(verified, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE)
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
                        roundProcessed++
                        recordVerificationSend(verified, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.TEMPLATE_RENDER_FAILED)
                        updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                            "RUNNING", "模板渲染失败：${expert.email}", errors, mode, roundNumber, config, runAccountStats,
                            roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                        continue
                    }

                    if (!source.revalidate(expert, existingContact) ||
                        emailSuppressionService.isSuppressed(expert.email.orEmpty()) || hasBoundContact(normOrcid)) {
                        accumulator.recordSkipped(BatchOutcomeReasonCodes.CANDIDATE_CHANGED)
                        processedTotal++; roundProcessed++; roundRejected++
                        recordVerificationSend(verified, BatchEmailVerificationSendStatus.NOT_SENT,
                            BatchOutcomeReasonCodes.CANDIDATE_CHANGED)
                        continue
                    }
                    if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                        wasCancelled = true
                        stopReason = "CANCELLED"
                        midRoundStop = true
                        recordVerificationSend(verified, BatchEmailVerificationSendStatus.NOT_SENT,
                            BatchOutcomeReasonCodes.CANCELLED)
                        break
                    }
                    // 主题/个性化校验通过后才创建并绑定联系人，跳过者可修正资料后重试。
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

                    // 保留发送前 SENT 去重检查（I-7）。
                    if (hasSentIntroduction(contact.id!!)) {
                        log.info("SENT introduction already exists for contact {}, skipping", contact.id)
                        accumulator.recordSkipped(BatchOutcomeReasonCodes.DEDUP)
                        processedTotal++
                        roundProcessed++
                        recordVerificationSend(verified, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.DEDUP)
                        continue
                    }

                    // I-2：SMTP 前再断言最终收件地址 = 已验证地址；不一致则终止本次执行，绝不发未验证地址。
                    val verifiedTarget = verified
                    if (verifiedTarget != null && normalizeVerificationEmail(mail.to) != verifiedTarget.normalizedEmail) {
                        log.error(
                            "Composed recipient differs from verified address for ORCID {}; stopping execution",
                            normOrcid
                        )
                        errors.add("收件地址在验证后发生变化：${expert.email}")
                        recordVerificationSend(verifiedTarget, BatchEmailVerificationSendStatus.NOT_SENT, SEND_REASON_EMAIL_CHANGED)
                        stopReason = SEND_REASON_EMAIL_CHANGED
                        finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
                        midRoundStop = true
                        break
                    }

                    // I-3/I-6：PASS 之后、SMTP 之前再查一次取消 —— 不发未确认的信，明细留 NOT_SENT/CANCELLED。
                    if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                        verified?.let {
                            recordVerificationSend(it, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.CANCELLED)
                        }
                        wasCancelled = true
                        stopReason = "CANCELLED"
                        midRoundStop = true
                        break
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

                    // I-6：审计先落库 —— 条件预占 SENDING 必须影响 1 行才允许 SMTP；
                    // 0 行表示重复目标或状态冲突，停止并报告，绝不重发。
                    if (verifiedTarget != null && !batchEmailVerificationService.markSending(verifiedTarget.rowId)) {
                        log.error("Verification row {} cannot be reserved for sending; stopping execution", verifiedTarget.rowId)
                        errors.add("验证明细状态冲突：无法预占发送（${expert.email}）")
                        stopReason = STOP_EMAIL_VERIFY_SEND_STATE_CONFLICT
                        finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
                        midRoundStop = true
                        break
                    }

                    // 09 (I-2/I-3): 发送前取得人物级唯一交付权（revision/地址复验 + CAS RESERVED→SENDING）。
                    // 全部原门禁已通过后才 CAS；失败即不发送，保持占位。
                    if (guardTicket != null) {
                        // V-26：本次尝试自己的 contact/attempt 行不是旧历史，随 claim 声明。
                        when (val claim = guard!!.claimSending(
                            guardTicket, expert, mail.to,
                            OutreachAttemptIdentity(contactId = contact.id, attemptId = attempt.id)
                        )) {
                            is OutreachClaimOutcome.Denied -> {
                                // 未调用 SMTP，证明未交付 → 安全重试。
                                guard.markSafeRetry(guardTicket)
                                accumulator.recordSkipped(claim.code, "首发交付权未取得：${claim.code}（${expert.email}）")
                                processedTotal++
                                roundProcessed++
                                roundRejected++
                                recordVerificationSend(verified, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.SEND_EXCEPTION)
                                updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                                    "RUNNING", "首发交付权未取得：${claim.code}", errors, mode, roundNumber, config, runAccountStats,
                                    roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)
                                continue
                            }
                            else -> {}
                        }
                    }

                    // 5. Send via SMTP
                    smtpAttempted = true
                    val delivered = mailDeliveryService.send(account, mail)
                    if (delivered.status == "SENT") {
                        accountRateLimiter.recordSuccess(account.accountCode, provider, config.perMailIntervalMs)
                        // 6. Record success atomically (state transition + mail_record + counter + attempt + ES) — I-7
                        txHelper.recordSuccess(
                            contact = contact, accountCode = account.accountCode,
                            deliveredMessageId = messageId, subject = mail.subject,
                            body = mail.text ?: mail.body, attemptId = attempt.id!!,
                            taskExecutionId = executionId, openTrackingId = delivered.openTrackingId,
                            outreachGuardTicket = guardTicket
                        )
                        accumulator.recordSuccess()
                        stat.success++
                        roundPassed++
                        taskExecutionService.updateProgressCounts(executionId, accumulator.success, accumulator.failure)
                        // I-6：SMTP 已返回 SENT 且 txHelper 已提交 —— 此处审计失败不回退成功计数，
                        // 行留在 SENDING（结果未确认）并由专用审计边界停止本次执行。
                        recordVerificationSend(verified, BatchEmailVerificationSendStatus.SENT, null)
                    } else {
                        val errorSummary = buildSmtpErrorSummary(delivered)
                        when (delivered.errorCategory) {
                            SmtpErrorCategory.PERMANENT -> {
                                // I-1：失败事实先落库，且判定与 recordFailure 使用同一个 errorSummary。
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.text ?: mail.body, attemptId = attempt.id,
                                    taskExecutionId = executionId
                                )
                                // I-3：公共状态入口与失败事实独立 —— 只有明确收件地址证据（行首可信
                                // 增强码命中白名单）才经公共入口写 EMAIL_INVALID（含 REPLIED 及以上保护、
                                // 无人工审计、ES 同步语义）；政策/路由类永久失败只留 FAILED 记录。
                                if (RecipientAddressFailureClassifier.isInvalidPermanentSummary(errorSummary)) {
                                    expertOperatorStatusService.markEmailInvalid(
                                        contact,
                                        "SMTP 永久失败且地址证据成立：${errorSummary.take(SUMMARY_REASON_LIMIT)}"
                                    )
                                }
                                accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "永久发送失败 (${expert.email}): ${delivered.errorDetail ?: delivered.status}")
                                stat.failed++
                                roundRejected++
                                recordVerificationSend(verified, BatchEmailVerificationSendStatus.FAILED, BatchOutcomeReasonCodes.SEND_EXCEPTION)
                            }
                            SmtpErrorCategory.TRANSIENT -> {
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.text ?: mail.body, attemptId = attempt.id,
                                    taskExecutionId = executionId
                                )
                                val code = delivered.smtpResponseCode
                                if (code == 421 || code == 452) {
                                    accountRateLimiter.recordThrottled(account.accountCode, provider, config.perMailIntervalMs)
                                    accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "限流 (${expert.email}): ${delivered.errorDetail ?: delivered.status}")
                                    stat.failed++
                                    roundRejected++
                                    recordVerificationSend(verified, BatchEmailVerificationSendStatus.FAILED, BatchOutcomeReasonCodes.SEND_EXCEPTION)
                                } else {
                                    mailSenderAccountService.pauseAutoSend(
                                        account.accountCode,
                                        "SMTP_TRANSIENT:${delivered.smtpResponseCode}:${delivered.errorDetail?.take(200)}"
                                    )
                                    accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "暂时发送失败 (${expert.email}): ${delivered.errorDetail ?: delivered.status}")
                                    stat.failed++
                                    roundRejected++
                                    recordVerificationSend(verified, BatchEmailVerificationSendStatus.FAILED, BatchOutcomeReasonCodes.SEND_EXCEPTION)
                                    accountFaulted = true
                                }
                            }
                            SmtpErrorCategory.INFRASTRUCTURE -> {
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.text ?: mail.body, attemptId = attempt.id,
                                    taskExecutionId = executionId
                                )
                                mailSenderAccountService.pauseAutoSend(
                                    account.accountCode,
                                    "SMTP_INFRA:${delivered.errorDetail?.take(200)}"
                                )
                                accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "基础设施发送失败 (${expert.email}): ${delivered.errorDetail ?: delivered.status}")
                                stat.failed++
                                roundRejected++
                                recordVerificationSend(verified, BatchEmailVerificationSendStatus.FAILED, BatchOutcomeReasonCodes.SEND_EXCEPTION)
                                accountFaulted = true
                            }
                            else -> {
                                txHelper.recordFailure(
                                    contactId = contact.id, accountCode = account.accountCode,
                                    messageId = messageId, errorSummary = errorSummary,
                                    subject = mail.subject, body = mail.text ?: mail.body, attemptId = attempt.id,
                                    taskExecutionId = executionId
                                )
                                accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "发送失败 (${expert.email}): ${delivered.status}")
                                stat.failed++
                                roundRejected++
                                recordVerificationSend(verified, BatchEmailVerificationSendStatus.FAILED, BatchOutcomeReasonCodes.SEND_EXCEPTION)
                            }
                        }
                    }
                    // 09 (I-2) + V-8：SMTP 未交付时的占位推进 —— 只有「有响应码且分类非 SUCCESS」
                    // （4xx/5xx **明确拒绝、未接受**）才证明未交付 → SAFE_RETRY（绑定原地址）；
                    // 250/SUCCESS（服务端已接受，之后连接中断）与无响应码（网络/认证等）结果不明
                    // → UNKNOWN，绝不从 FAILED/TRANSIENT 推断可重试。SENT 已由 recordSuccess 联动。
                    if (delivered.status != "SENT" && guardTicket != null) {
                        if (delivered.smtpResponseCode != null &&
                            delivered.errorCategory != SmtpErrorCategory.SUCCESS
                        ) {
                            guard!!.markSafeRetry(guardTicket)
                        } else {
                            guard!!.markUnknown(guardTicket)
                        }
                    }
                } catch (e: com.weibo.talentintroduction.expert.service.ExpertSearchUnavailableException) {
                    throw e
                } catch (e: EmailVerificationAuditException) {
                    // I-6：审计边界先于广义 catch —— 绝不进入 pauseAccount / SMTP 故障统计，也不伪增失败。
                    log.error("Email verification audit failed for ORCID {}: {}", normOrcid, e.message)
                    errors.add("验证审计写入失败：${e.message.orEmpty().take(120)}")
                    stopReason = STOP_EMAIL_VERIFY_AUDIT_FAILED
                    finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
                    midRoundStop = true
                    break
                } catch (e: Exception) {
                    log.error("Failed to process ORCID: {}", normOrcid, e)
                    // 09 (I-2): 已进 SMTP 的结果不明 → UNKNOWN（不可重发）；未进 SMTP → 安全重试。
                    if (guardTicket != null) {
                        if (smtpAttempted) guard!!.markUnknown(guardTicket) else guard!!.markSafeRetry(guardTicket)
                    }
                    // I-6：未进入 SMTP 的记 NOT_SENT + 原因；已进 SMTP 的结果不明，保持 SENDING 不覆盖。
                    if (!smtpAttempted) {
                        verified?.let {
                            recordVerificationSendQuietly(it.rowId, BatchEmailVerificationSendStatus.NOT_SENT, BatchOutcomeReasonCodes.SEND_EXCEPTION)
                        }
                    }
                    accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION, "发送异常 (${expert.email}): ${e.message}")
                    stat.failed++
                    roundRejected++
                } finally {
                    // 09 (I-2)：reserve 后**未进入 SMTP** 的 continue/break 退出（取消/compose/建行/
                    // 地址变化/审计等）也必须把已持有的占位清理为 SAFE_RETRY（证明未交付，原址可重试）。
                    // 已进 SMTP 的退出由上方分支按结果推进；已推进过的占位因状态不匹配而 CAS 失败，不会重复推进。
                    if (!smtpAttempted && guardTicket != null) {
                        guard!!.markSafeRetry(guardTicket)
                    }
                }

                // Track assignment for account balancing
                assignments.add(SenderExpertAssignment(
                    accountCode = account.accountCode, expertId = normOrcid,
                    distributionKey = expert.country?.lowercase()?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"
                ))

                processedTotal++
                roundProcessed++

                // Update progress (I-8: per-account stats)
                updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
                    "RUNNING", "正在发送：${expert.email}", errors, mode, roundNumber, config, runAccountStats,
                    roundNumber, roundProcessed, roundPassed, roundRejected, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)

                // A fault pauses only this account. Continue with subsequent targets;
                // never resubmit the failed mail through another sender here.
                if (accountFaulted && listSendableWithin(ignoreWarmup, allowedAccountCodes).isEmpty()) {
                    stopReason = "NO_AVAILABLE_ACCOUNT"
                    finalStatus = "PAUSED"
                    midRoundStop = true
                    break
                }
                // Throttle per mail (I-6 + dynamic rate limiter)
                val intervalMs = accountRateLimiter.getIntervalMs(account.accountCode, provider, config.perMailIntervalMs)
                if (intervalMs > 0 && roundPassed < roundQuota && !targetIterator.sourceExhausted &&
                    !progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                    try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                }
            }

            if (midRoundStop) break
            if (targetIterator.sourceExhausted) break

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
            if (roundNumber < effectiveRounds(snapshot, oneRoundOnly) && config.perRoundIntervalMs > 0 &&
                !targetIterator.sourceExhausted && !progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                try { Thread.sleep(config.perRoundIntervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
        }
        } catch (ex: com.weibo.talentintroduction.expert.service.ExpertSearchUnavailableException) {
            stopReason = "CANDIDATE_READ_FAILED"
            finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
            errors.add("候选读取失败：${ex.message.orEmpty().take(160)}")
        } finally {
            try { targetIterator.close() } catch (ex: Exception) {
                stopReason = "CANDIDATE_READ_FAILED"
                finalStatus = if (accumulator.success > 0) "PARTIAL_SUCCESS" else "FAILED"
                errors.add("候选游标关闭失败：${ex.message.orEmpty().take(160)}")
            }
        }

        if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
            wasCancelled = true
            stopReason = "CANCELLED"
        }
        val resolvedFinalStatus = when {
            wasCancelled -> "CANCELLED"
            finalStatus != null -> finalStatus
            else -> "COMPLETED"
        }
        val finalMessage = stopReasonMessage(resolvedFinalStatus, stopReason, ignoreWarmup, allowedAccountCodes)
        updateProgressWithAccumulator(executionId, accumulator, processedTotal, totalEstimate,
            resolvedFinalStatus, finalMessage, errors, mode, roundNumber, config, runAccountStats,
            stopReason = stopReason, ignoreWarmup = ignoreWarmup, roundsPerRun = snapshot.roundsPerRun)

        return buildResult(totalEstimate, accumulator, wasCancelled, resolvedFinalStatus, stopReason)
    }

    // ──── Private helpers ────
    private fun effectiveRounds(snapshot: BatchExecutionSnapshot, oneRoundOnly: Boolean): Int {
        require(snapshot.roundSize > 0 && snapshot.roundsPerRun > 0) { "发送轮数和每轮成功额度必须为正数" }
        return if (oneRoundOnly) 1 else snapshot.roundsPerRun
    }

    private fun plannedSuccess(snapshot: BatchExecutionSnapshot, oneRoundOnly: Boolean): Long =
        snapshot.roundSize.toLong() * effectiveRounds(snapshot, oneRoundOnly).toLong()

    private fun demandPageSize(remaining: Int): Int = minOf(500L, maxOf(1L, 2L * remaining.toLong())).toInt()

    /**
     * Bounded raw enumeration, separate from delivery. Only identity sets grow with the scan;
     * profiles and document-to-layer attribution are retained for the current accepted page.
     */
    private inner class DemandCandidateSource(
        private val snapshot: BatchExecutionSnapshot?,
        private val scope: RecipientScope,
        private val campaignId: Long?,
        private val executionId: Long?,
        private val gate: (List<ExpertProfile>) -> List<ExpertProfile>,
        private val now: LocalDateTime,
        private val onSelection: ((RecipientSelection) -> Unit)? = null
    ) : AutoCloseable {
        var scanned = 0L
            private set
        var excludedVerifiedUnavailable = 0
            private set
        private val levels = scope.funnelLevels.map(ExpertIndexLevel::valueOf)
        private var levelIndex = 0
        private var cursor: com.weibo.talentintroduction.expert.service.ExpertSearchCursor? = null
        private var retryUpperId: Long? = null
        private var retryStarted = false
        private var retryUpdatedAt: LocalDateTime? = null
        private var retryId: Long? = null
        private var retryExhausted = campaignId == null
        private val candidateLevels = mutableMapOf<String, ExpertIndexLevel>()
        private val seenOrcids = mutableSetOf<String>()
        private val seenDocIds = mutableSetOf<String>()
        private val seenEmails = mutableSetOf<String>()
        private val seenContacts = mutableSetOf<Long>()

        private fun cancelled() = executionId != null &&
            progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)

        private fun <T> readCandidates(action: () -> T): T = try {
            action()
        } catch (ex: com.weibo.talentintroduction.expert.service.ExpertSearchUnavailableException) {
            throw ex
        } catch (ex: Exception) {
            throw com.weibo.talentintroduction.expert.service.ExpertSearchUnavailableException(
                "Candidate source read failed"
            ).apply { initCause(ex) }
        }

        fun iterator(pageSize: () -> Int): DemandOutreachTargetIterator = OutreachTargetIterator.demand(
            retryNext = { size -> readCandidates {
                if (scope.mailType == BatchSendType.MATERIAL_REMINDER.name) nextMaterial(size) else nextRetry(size)
            } },
            esNext = { size -> readCandidates {
                if (scope.mailType == BatchSendType.MATERIAL_REMINDER.name) emptyList() else nextIntroduction(size)
            } },
            filterPage = { it },
            pageSize = pageSize,
            shouldStop = ::cancelled,
            dedupeKey = { profile ->
                if (scope.mailType == BatchSendType.MATERIAL_REMINDER.name) normalizeOrcid(profile.orcidId)
                else discoveryPersonRecipientService.personKeyOf(profile)
            },
            onScanned = {},
            close = ::close
        )

        private fun currentPage(raw: List<ExpertProfile>, level: ExpertIndexLevel): List<ExpertProfile> {
            val ids = raw.map(BatchRecipientSelectionService::docIdOf)
            val latest = expertSearchService.findByDocumentIdsStrict(level, ids)
                .associateBy(BatchRecipientSelectionService::docIdOf)
            return raw.mapNotNull { original ->
                latest[BatchRecipientSelectionService.docIdOf(original)]?.takeIf {
                    normalizeOrcid(it.orcidId) == normalizeOrcid(original.orcidId)
                }
            }
        }

        private fun nextRaw(size: Int): List<ExpertProfile> {
            while (levelIndex < levels.size && !cancelled()) {
                val level = levels[levelIndex]
                val active = cursor ?: expertSearchService.openCandidateCursor(
                    level, buildEsFiltersForLevel(scope, level.name), 500
                ).also { cursor = it }
                val page = active.nextPage(size)
                scanned = Math.addExact(scanned, page.profiles.size.toLong())
                if (cancelled()) return emptyList()
                if (page.profiles.isEmpty()) {
                    active.close()
                    cursor = null
                    levelIndex++
                    continue
                }
                candidateLevels.clear()
                val current = currentPage(page.profiles, level)
                val latest = if (scope.mailType == BatchSendType.INTRODUCTION.name) {
                    // Reserve raw identities after strict refresh, before deterministic filtering.
                    // A changed/previously accepted document must not reappear through an ORCID alias.
                    val unseenIds = page.profiles.filter {
                        seenOrcids.add(normalizeOrcid(it.orcidId)) &&
                            BatchRecipientSelectionService.docIdOf(it) !in seenDocIds
                    }.map(BatchRecipientSelectionService::docIdOf).toSet()
                    current.filter { BatchRecipientSelectionService.docIdOf(it) in unseenIds }
                } else current
                latest.forEach { candidateLevels[BatchRecipientSelectionService.docIdOf(it)] = level }
                // Missing current documents are filtered, not mistaken for source exhaustion.
                if (latest.isNotEmpty()) return latest
            }
            return emptyList()
        }

        private fun eligible(profiles: List<ExpertProfile>, reserveRetryIdentities: Boolean = false): List<ExpertProfile> {
            if (profiles.isEmpty()) return profiles
            val selected = if (scope.mailType == BatchSendType.INTRODUCTION.name) {
                selectIncluded(scope, profiles)
            } else {
                val matching = profiles.filter(scope::matchesExpert)
                val selection = batchRecipientSelectionService.select(scope.copy(tags = emptyList()), matching)
                onSelection?.invoke(selection)
                matching.filter { BatchRecipientSelectionService.docIdOf(it) in selection.includedDocIds }
            }
            val gated = gate(selected)
            if (reserveRetryIdentities) gated.forEach { seenOrcids.add(normalizeOrcid(it.orcidId)) }
            val deterministic = if (snapshot != null && batchRecipientPreflightService != null &&
                scope.mailType == BatchSendType.INTRODUCTION.name) {
                val decisions = batchRecipientPreflightService.preflight(
                    preflightInput(snapshot, scope, gated, partial = false)
                ).decisions.associate { it.docId to it.eligible }
                gated.filter { decisions[BatchRecipientSelectionService.docIdOf(it)] == true }
            } else gated
            return deterministic
        }

        private fun nextIntroduction(size: Int): List<ExpertProfile> {
            while (!cancelled()) {
                val raw = nextRaw(size)
                if (raw.isEmpty()) return emptyList()
                val distinct = raw.filter { BatchRecipientSelectionService.docIdOf(it) !in seenDocIds }
                val filtered = filterKnownProfiles(eligible(distinct), scope.excludeVerifiedUnavailableEmails, now)
                val accepted = filtered.filter {
                    seenDocIds.add(BatchRecipientSelectionService.docIdOf(it)) &&
                        seenEmails.add(normalizeVerificationEmail(it.email))
                }
                if (accepted.isNotEmpty()) return accepted
            }
            return emptyList()
        }

        private fun nextRetry(size: Int): List<Pair<ExpertContact?, ExpertProfile>> {
            if (!retryStarted) {
                retryStarted = true
                retryUpperId = campaignId?.let(expertContactRepository::findNewRetryUpperId)
                if (retryUpperId == null) retryExhausted = true
            }
            while (!retryExhausted && !cancelled()) {
                val contacts = expertContactRepository.findNewRetryPage(
                    campaignId!!, retryUpperId!!, retryUpdatedAt, retryId, size
                )
                scanned = Math.addExact(scanned, contacts.size.toLong())
                if (contacts.isEmpty()) {
                    retryExhausted = true
                    return emptyList()
                }
                retryUpdatedAt = contacts.last().updatedAt
                retryId = contacts.last().id
                val unseenContacts = contacts.filter { normalizeOrcid(it.orcidId) !in seenOrcids }
                val retryable = unseenContacts.filter { contact ->
                    val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)
                    contact.operatorStatus != "EMAIL_INVALID" && contact.boundSenderAccountCode.isNullOrBlank() &&
                        records.none { it.isPermanentIntroductionFailure() ||
                            (it.direction == "OUTBOUND" && it.mailType == "INTRODUCTION" && it.sendStatus == "SENT") }
                }
                val byOrcid = retryable.associateBy { normalizeOrcid(it.orcidId) }
                val bound = boundOrcidsOf(byOrcid.keys.toList())
                val chosen = mutableMapOf<String, ExpertProfile>()
                candidateLevels.clear()
                if (byOrcid.isNotEmpty()) for (level in levels) {
                    if (cancelled()) break
                    expertSearchService.openCandidateCursor(
                        level, listOf(mapOf("terms" to mapOf("orcidId" to byOrcid.keys.toList()))), 500
                    ).use { lookup ->
                        while (!cancelled()) {
                            val page = lookup.nextPage(size)
                            scanned = Math.addExact(scanned, page.profiles.size.toLong())
                            if (page.profiles.isEmpty()) break
                            val latest = currentPage(page.profiles, level).filter {
                                val norm = normalizeOrcid(it.orcidId)
                                val contact = byOrcid[norm]
                                contact != null && norm !in bound && norm !in chosen && norm !in seenOrcids &&
                                    normalizeVerificationEmail(contact.expertEmail) == normalizeVerificationEmail(it.email)
                            }
                            // Fully selected NEW identities remain reserved even if historical address filtering rejects them.
                            val selected = eligible(latest, reserveRetryIdentities = true)
                            val filtered = filterKnownProfiles(selected, scope.excludeVerifiedUnavailableEmails, now)
                            filtered.forEach { profile ->
                                val norm = normalizeOrcid(profile.orcidId)
                                if (chosen.putIfAbsent(norm, profile) == null) {
                                    candidateLevels[BatchRecipientSelectionService.docIdOf(profile)] = level
                                }
                            }
                        }
                    }
                }
                val result = retryable.mapNotNull { contact ->
                    val profile = chosen[normalizeOrcid(contact.orcidId)] ?: return@mapNotNull null
                    if (!seenDocIds.add(BatchRecipientSelectionService.docIdOf(profile)) ||
                        !seenEmails.add(normalizeVerificationEmail(contact.expertEmail))) return@mapNotNull null
                    Pair(contact, profile)
                }
                if (result.isNotEmpty()) return result
            }
            return emptyList()
        }

        private fun nextMaterial(size: Int): List<Pair<ExpertContact?, ExpertProfile>> {
            while (!cancelled()) {
                val raw = nextRaw(size)
                if (raw.isEmpty()) return emptyList()
                val profiles = eligible(raw)
                val contacts = expertContactRepository.findByOrcidIdIn(
                    profiles.map { normalizeOrcid(it.orcidId) }.distinct()
                )
                val bound = contacts.filter { !it.boundSenderAccountCode.isNullOrBlank() }
                    .map { normalizeOrcid(it.orcidId) }.toSet()
                val byOrcid = contacts.associateBy { normalizeOrcid(it.orcidId) }
                val targets = profiles.mapNotNull { profile ->
                    val norm = normalizeOrcid(profile.orcidId)
                    val contact = byOrcid[norm] ?: return@mapNotNull null
                    val id = contact.id ?: return@mapNotNull null
                    if (norm in bound || id in seenContacts || contact.expertEmail.isBlank() ||
                        emailSuppressionService.isSuppressed(contact.expertEmail) || hasSentMaterialReminder(id)) {
                        return@mapNotNull null
                    }
                    Pair(contact, profile)
                }
                val (filtered, excluded) = filterTargets(
                    targets, scope.excludeVerifiedUnavailableEmails, now, email = { it.first.expertEmail }
                )
                excludedVerifiedUnavailable = Math.addExact(excludedVerifiedUnavailable, excluded)
                val accepted = filtered.filter { seenContacts.add(it.first.id!!) &&
                    seenEmails.add(normalizeVerificationEmail(it.first.expertEmail)) }
                if (accepted.isNotEmpty()) return accepted
            }
            return emptyList()
        }

        fun revalidate(profile: ExpertProfile, contact: ExpertContact?, checkBinding: Boolean = true): Boolean = readCandidates {
            val id = BatchRecipientSelectionService.docIdOf(profile)
            val level = candidateLevels[id] ?: return@readCandidates false
            val latest = expertSearchService.findByDocumentIdsStrict(level, listOf(id)).singleOrNull()
                ?: return@readCandidates false
            if (normalizeOrcid(latest.orcidId) != normalizeOrcid(profile.orcidId) ||
                normalizeVerificationEmail(latest.email) != normalizeVerificationEmail(profile.email) ||
                eligible(listOf(latest)).isEmpty()) return@readCandidates false
            val recipientEmail = contact?.expertEmail ?: latest.email
            if (scope.excludeVerifiedUnavailableEmails &&
                normalizeVerificationEmail(recipientEmail) in findKnownUndeliverable(listOf(recipientEmail), verificationFilterNow())) {
                return@readCandidates false
            }
            if (contact != null) {
                val current = expertContactRepository.findById(contact.id!!).orElse(null)
                    ?: return@readCandidates false
                if (current.expertEmail != contact.expertEmail ||
                    (checkBinding && current.boundSenderAccountCode != contact.boundSenderAccountCode) ||
                    (scope.mailType == BatchSendType.INTRODUCTION.name && current.currentStatus != "NEW")) {
                    return@readCandidates false
                }
            }
            true
        }

        override fun close() {
            val active = cursor
            cursor = null
            active?.close()
        }
    }


    private fun classifyNoSendableOutcome(
        ignoreWarmup: Boolean,
        allowedAccountCodes: Set<String> = emptySet()
    ): StopOutcome {
        val activeAccounts = mailSenderAccountService.listEnabledAccounts()
            .filter { it.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE }
            // I-2/I-5: 停发判据只看本次选中集合 —— 未选中账号的状态不得影响筛选结果。
            .filter { allowedAccountCodes.isEmpty() || it.accountCode in allowedAccountCodes }
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

    private fun stopReasonMessage(
        finalStatus: String,
        stopReason: String?,
        ignoreWarmup: Boolean,
        allowedAccountCodes: Set<String> = emptySet()
    ): String = when (stopReason) {
        "WARMUP_LIMIT_REACHED" -> "已达到预热上限，今日暂停发送"
        "DAILY_LIMIT_REACHED" -> if (hasWarmupLimitedAccounts(ignoreWarmup, allowedAccountCodes)) {
            "已达到今日发送上限（含预热账号）"
        } else {
            "已达到今日发送上限"
        }
        "NO_AVAILABLE_ACCOUNT" -> "批量发送已暂停：无可用邮箱账号，请检查并恢复账号。"
        "ROUNDS_PER_RUN_REACHED" -> "本次调度轮次已用完"
        "ONE_ROUND_DONE" -> "手动单轮发送已完成"
        "CANCELLED" -> "发送任务已被取消"
        "CANDIDATE_READ_FAILED" -> "候选读取失败，已停止本次发送"
        "COUNT_LIMIT_REACHED" -> "实际处理数量达到计数上限，已停止本次发送"
        // I-4/I-6：发送前验证的受控停止原因（服务故障/审计不可用/状态冲突），文案按受控错误码给出。
        BatchEmailVerificationErrorCodes.AUTH_ERROR -> "邮箱验证服务鉴权失败，已停止本次发送"
        BatchEmailVerificationErrorCodes.NO_CREDITS -> "邮箱验证服务额度不足，已停止本次发送"
        BatchEmailVerificationErrorCodes.RATE_LIMITED -> "邮箱验证服务限流，已停止本次发送"
        BatchEmailVerificationErrorCodes.TIMEOUT -> "邮箱验证服务超时，已停止本次发送"
        BatchEmailVerificationErrorCodes.INCOMPLETE -> "邮箱验证服务未返回结果，已停止本次发送"
        BatchEmailVerificationErrorCodes.BAD_RESPONSE -> "邮箱验证服务返回非法响应，已停止本次发送"
        BatchEmailVerificationErrorCodes.SERVICE_ERROR -> "邮箱验证服务故障，已停止本次发送"
        STOP_EMAIL_VERIFY_AUDIT_FAILED -> "邮箱验证明细写入失败，已停止本次发送"
        STOP_EMAIL_VERIFY_SEND_STATE_CONFLICT -> "邮箱验证明细状态冲突，已停止本次发送"
        SEND_REASON_EMAIL_CHANGED -> "收件地址在验证后发生变化，已停止本次发送"
        else -> when (finalStatus) {
            "PAUSED" -> "流程已暂停: ${stopReason ?: ""}"
            "FAILED" -> "发送任务失败: ${stopReason ?: ""}"
            "COMPLETED" -> "发送任务已完成"
            else -> "发送任务结束"
        }
    }

    private fun hasWarmupLimitedAccounts(
        ignoreWarmup: Boolean,
        allowedAccountCodes: Set<String> = emptySet()
    ): Boolean =
        mailSenderAccountService.listEnabledAccounts()
            .filter { it.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE }
            .filter { allowedAccountCodes.isEmpty() || it.accountCode in allowedAccountCodes }
            .any { senderWarmupService.dailyState(it, ignoreWarmup = ignoreWarmup) == AccountDailyState.WARMUP_LIMIT_REACHED }

    private data class StopOutcome(val stopReason: String, val finalStatus: String)

    private fun normalizeOrcid(orcid: String) = ExpertIdNormalizer.normalize(orcid)

    /**
     * I-6：验证明细的发送结果收尾。审计不可用时抛 [EmailVerificationAuditException]，
     * 由发送循环的专用边界处理（停止本次执行，不进入 SMTP 故障统计）。
     */
    private fun recordVerificationSend(verified: VerificationResult.Passed?, sendStatus: String, sendReason: String?) {
        val passed = verified ?: return
        batchEmailVerificationService.recordSend(passed.rowId, sendStatus, sendReason)
    }

    /** I-6：调用方已经在停止时的收尾（取消/无可用账号/系统错误）；写失败只记日志，行保持默认 NOT_SENT。 */
    private fun recordVerificationSendQuietly(rowId: Long, sendStatus: String, sendReason: String?) {
        try {
            batchEmailVerificationService.recordSend(rowId, sendStatus, sendReason)
        } catch (e: EmailVerificationAuditException) {
            log.warn("Email verification trailing audit write failed for row {}: {}", rowId, e.message)
        }
    }

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
     * I-4（03）：历史首封永久失败事实门禁的判据 —— OUTBOUND + INTRODUCTION + FAILED 且摘要以
     * `PERMANENT:` 开头。**与地址证据解耦**：无法证明地址无效的政策/路由类永久失败同样阻断自动重发，
     * 而 UNKNOWN / TRANSIENT（含 421 限流）不冒充永久失败。
     */
    private fun MailRecord.isPermanentIntroductionFailure(): Boolean =
        direction == "OUTBOUND" && mailType == "INTRODUCTION" && sendStatus == "FAILED" &&
            errorSummary?.startsWith(PERMANENT_SUMMARY_PREFIX) == true

    /**
     * I-4（03）：事实门禁要读的 contact 行 —— ES 页重新出现的同一 ORCID 也必须在发送前命中同一事实，
     * 故按 ORCID 取既有行（复用既有 `findByOrcidIdIn`，不新增仓储方法/表/状态），
     * 再逐个用现有 `findAllByExpertContactIdOrderByCreatedAtAsc` 读一次邮件列表。
     */
    private fun introductionHistoryContactIds(normOrcid: String, existingContact: ExpertContact?): List<Long> =
        buildList {
            existingContact?.id?.let { add(it) }
            addAll(expertContactRepository.findByOrcidIdIn(listOf(normOrcid)).mapNotNull { it.id })
        }.distinct()

    /**
     * V-1（master I-8/发布段、04 开关、09 I-1）：人物级首发门禁是否适用于该目标。
     *
     * - 非 discovered 目标：不适用（保持旧普通路径）。
     * - 总开关**开启**：适用（当前行为，映射缺失即 fail-closed）。
     * - 总开关**关闭**：只对**已登记到人物**（存在映射/保护行）的目标适用 —— 已建立的
     *   SENT/UNKNOWN/SENDING 保护不因关闭而撤销；未登记目标逐字保持本轮引入前的旧行为
     *   （不调 guard、不写任何 guard 状态，绝不因 PERSON_MISSING 被拒）。
     */
    private fun personGuardApplies(
        guard: DiscoveryPersonOutreachGuardService,
        expert: ExpertProfile
    ): Boolean {
        if (!RecipientScope.isDiscoveryOutreach(expert)) return false
        return discoveryPersonFeatureGate.enabled ||
            guard.isKnownPerson(BatchRecipientSelectionService.docIdOf(expert))
    }

    /** I-4（03）：上述 contact 行中是否存在历史首封永久失败（每行一次列表读取，不做 N×M 查询）。 */
    private fun hasPermanentFirstMailFailure(contactIds: List<Long>): Boolean =
        contactIds.any { contactId ->
            mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
                .any { it.isPermanentIntroductionFailure() }
        }

    /**
     * Round gate (L3-1) for INTRODUCTION: reads TTL from INTRODUCTION config (compat — existing tests mock checkSendable(account)).
     * I-2: 非空 [allowedAccountCodes] 时候选与自检都只覆盖选中账号。
     */
    private fun runRoundGate(
        ignoreWarmup: Boolean,
        allowedAccountCodes: Set<String> = emptySet()
    ): List<MailSenderAccount> {
        val candidates = listSendableWithin(ignoreWarmup, allowedAccountCodes)
        if (candidates.isEmpty()) return emptyList()
        for (account in candidates) {
            selfCheckService.checkSendable(account)
        }
        return listSendableWithin(ignoreWarmup, allowedAccountCodes)
    }

    /**
     * Round gate with explicit TTL — used by MATERIAL_REMINDER to pass its own selfCheckTtlMinutes.
     */
    private fun runRoundGate(
        ignoreWarmup: Boolean,
        selfCheckTtlMinutes: Int,
        allowedAccountCodes: Set<String> = emptySet()
    ): List<MailSenderAccount> {
        val candidates = listSendableWithin(ignoreWarmup, allowedAccountCodes)
        if (candidates.isEmpty()) return emptyList()
        for (account in candidates) {
            selfCheckService.checkSendable(account, selfCheckTtlMinutes)
        }
        return listSendableWithin(ignoreWarmup, allowedAccountCodes)
    }

    /** I-2: 每轮可用账号只取快照选中集合内的逻辑 code（空集合 = 旧行为，不限制）。 */
    private fun listSendableWithin(ignoreWarmup: Boolean, allowedAccountCodes: Set<String>): List<MailSenderAccount> =
        mailSenderAccountService.listSendableAccounts(ignoreWarmup)
            .filter { allowedAccountCodes.isEmpty() || it.accountCode in allowedAccountCodes }

    /**
     * I-2: 非空白名单走五参选号（候选受限）；空集合保留旧四参调用，行为逐字不变。
     */
    private fun selectSendAccount(
        expert: ExpertProfile,
        assignments: List<SenderExpertAssignment>,
        ignoreWarmup: Boolean,
        stock: SenderBindingStock,
        allowedAccountCodes: Set<String>
    ): MailSenderAccount =
        if (allowedAccountCodes.isEmpty()) {
            senderAccountAssignmentService.selectAccount(expert, assignments, ignoreWarmup, stock)
        } else {
            senderAccountAssignmentService.selectAccount(expert, assignments, ignoreWarmup, stock, allowedAccountCodes)
        }

    /** I-1/I-2: 本次执行的发件账号范围快照（trim/丢空/去重后）；空集合 = 不限。 */
    private fun allowedAccountCodesOf(snapshot: BatchExecutionSnapshot): Set<String> =
        snapshot.senderAccountCodes.map { it.trim() }.filter { it.isNotEmpty() }.distinct().toSet()

    /**
     * I-3: 同一 ORCID 的**任一** `expert_contact` 行（任意 campaign）已有非空白绑定即为已绑定。
     * 空白字符串不是合法绑定值（V85 注释明确禁止），故与 [SenderAccountBindingService.resolveForSend] 同口径。
     */
    private fun hasBoundContact(normOrcid: String): Boolean =
        expertContactRepository.findByOrcidIdIn(listOf(normOrcid))
            .any { !it.boundSenderAccountCode.isNullOrBlank() }
    private fun buildFilteredRetryableTargets(
        campaignId: Long,
        scope: RecipientScope,
        now: LocalDateTime,
        seenDocIds: MutableSet<String>,
        gate: (List<ExpertProfile>) -> List<ExpertProfile> = { it },
        onSelection: ((RecipientSelection) -> Unit)? = null
    ): RetrySelection {
        val (targets, seenOrcids, seenPersonKeys) = buildRetryableTargets(campaignId, scope, gate, onSelection)
        val (filtered, excluded) = filterTargets(
            targets,
            enabled = scope.excludeVerifiedUnavailableEmails,
            now = now,
            email = { it.second.email }
        )
        filtered.forEach { seenDocIds.add(BatchRecipientSelectionService.docIdOf(it.second)) }
        return RetrySelection(filtered, seenOrcids, seenPersonKeys, excluded)
    }

    /** R-7：重试构造的产物 —— 目标 + 原始身份键 + 人物键 + 被历史过滤数。 */
    private data class RetrySelection(
        val targets: List<Pair<ExpertContact?, ExpertProfile>>,
        val seenOrcids: MutableSet<String>,
        val seenPersonKeys: MutableSet<String>,
        val excluded: Int
    )


    /**
     * I-3/I-4: 批量判定一组 ORCID 是否已绑定（预估与目标构造共用，保证同源）。
     * 同一 ORCID 在不同 campaign 可能有多行 —— 任一行有非空白绑定即视为已绑定。
     */
    private fun boundOrcidsOf(orcidIds: List<String>): Set<String> {
        val distinct = orcidIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinct.isEmpty()) return emptySet()
        return expertContactRepository.findByOrcidIdIn(distinct)
            .filter { !it.boundSenderAccountCode.isNullOrBlank() }
            .map { normalizeOrcid(it.orcidId) }
            .toSet()
    }

    /**
     * Retry path applies the same [RecipientScope] as ES (I-3 / K-batch-send-filter-retry-parity).
     * Profiles are loaded from every funnel level in scope (not hard-coded CANDIDATE).
     */
    private fun buildRetryableTargets(
        campaignId: Long,
        scope: RecipientScope,
        gate: (List<ExpertProfile>) -> List<ExpertProfile> = { it },
        onSelection: ((RecipientSelection) -> Unit)? = null
    ): Triple<List<Pair<ExpertContact?, ExpertProfile>>, MutableSet<String>, MutableSet<String>> {
        val seenOrcids = mutableSetOf<String>()
        val seenDocIds = mutableSetOf<String>()
        val seenPersonKeys = mutableSetOf<String>()
        val targets = mutableListOf<Pair<ExpertContact?, ExpertProfile>>()
        val candidates = mutableListOf<Triple<ExpertContact, ExpertProfile, String>>()

        val newContacts = expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW")
        if (newContacts.isNotEmpty()) {
            val retryableContacts = newContacts.filter { contact ->
                // I-4（03）：同一份邮件列表同时服务「已发首封」去重与「历史首封永久失败」阻断
                // （复用现有查询，不新增每行第二次读取）——永久失败不自动重发，不依赖 EMAIL_INVALID 副作用。
                val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)
                val hasSent = records.any {
                    it.direction == "OUTBOUND" && it.mailType == "INTRODUCTION" && it.sendStatus == "SENT"
                }
                !hasSent && !records.any { it.isPermanentIntroductionFailure() } &&
                    contact.operatorStatus != "EMAIL_INVALID"
            }
            val orcidIds = retryableContacts.map { it.orcidId }
            // I-3/I-4: 预估与执行共用本构造函数 —— 同一 ORCID 的任一 campaign 行已绑定即排除。
            val boundOrcids = boundOrcidsOf(orcidIds)
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
                if (normOrcid in boundOrcids) continue
                val profile = scope.funnelLevels.asSequence()
                    .mapNotNull { level -> profilesByLevel[level]?.get(normOrcid) }
                    .firstOrNull() ?: continue
                candidates.add(Triple(contact, profile, normOrcid))
            }
            // I-1/I-2/I-3: 与 ES 页、预估共用同一人物级选择层；安全重试沿用原尝试地址（不自动换址）。
            val included = if (candidates.isEmpty()) {
                emptySet()
            } else {
                discoveryPersonRecipientService.select(
                    scope,
                    candidates.map { it.second },
                    onPerEmailSelection = onSelection,
                    preferredDocIds = candidates.map { BatchRecipientSelectionService.docIdOf(it.second) }.toSet()
                ).selectedDocIds
            }
            // A3 (I-4): selector 之后再过精确模板门禁（与发送期 compose 同源）。
            val gatedDocIds = candidates.asSequence()
                .filter { BatchRecipientSelectionService.docIdOf(it.second) in included }
                .distinctBy { BatchRecipientSelectionService.docIdOf(it.second) }
                .map { it.second }
                .toList()
                .let { gate(it) }
                .map { BatchRecipientSelectionService.docIdOf(it) }
                .toSet()
            // I-2（R-7）：完整判定（selector + 精确模板门禁）之后才登记**人物去重键** ——
            // 预估与 ES 执行（迭代器 seenKeys）据此跳过同人另一成员，避免重复计数/重复发信。
            val personKeys = discoveryPersonRecipientService.personKeysOf(candidates.map { it.second })
            for ((contact, profile, normOrcid) in candidates) {
                val docId = BatchRecipientSelectionService.docIdOf(profile)
                if (docId !in gatedDocIds) continue
                if (seenOrcids.add(normOrcid) && seenDocIds.add(docId)) {
                    targets.add(Pair(contact, profile))
                    seenPersonKeys += personKeys[docId] ?: docId
                }
            }
        }

        log.info("Retryable targets: {} (funnelLevels={})", targets.size, scope.funnelLevels)
        return Triple(targets, seenOrcids, seenPersonKeys)
    }

    /**
     * 兼容反射接缝：既有测试按 `buildRetryableTargets(Long, RecipientScope)` 取
     * `Pair<List<Pair<ExpertContact?, ExpertProfile>>, MutableSet<String>>`（targets + 已见 ORCID）。
     * 人物去重键（seenPersonKeys）只在执行路径消费，不进入该兼容签名。
     */
    private fun buildRetryableTargets(
        campaignId: Long,
        scope: RecipientScope
    ): Pair<List<Pair<ExpertContact?, ExpertProfile>>, MutableSet<String>> {
        val (targets, seenOrcids, _) = buildRetryableTargets(campaignId, scope, { it }, null)
        return targets to seenOrcids
    }

    /**
     * Legacy typed path — INTRODUCTION defaults to CANDIDATE-only funnel.
     *
     * 兼容既有反射接缝（`BatchSendTaskRuntimeIntegrationTest` 等按
     * `Pair<List<Pair<ExpertContact?, ExpertProfile>>, MutableSet<String>>` 取用）：这里只暴露
     * `(targets, seenOrcids)` 两项；人物去重键（seenPersonKeys）由执行路径直接取用带 gate 的版本。
     */
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
            emailDomains = emailDomain?.ifBlank { null }?.let { listOf(it) } ?: emptyList(),
            discipline = discipline
        )
        val (targets, seenOrcids, _) = buildRetryableTargets(campaignId, scope, { it }, null)
        return targets to seenOrcids
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


    private fun hasSentMaterialReminder(contactId: Long): Boolean {
        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
        return records.any { it.direction == "OUTBOUND" && it.mailType == "MATERIAL_REMINDER" && it.sendStatus == "SENT" }
    }

    private fun buildMaterialReminderEsFilters(
        config: BatchSendConfig,
        operatorStatus: String? = null
    ): List<Map<String, Any>> {
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
        // I-1 第 3 条旁路：材料提醒查询与 ES 目标查询同口径按状态过滤（I-3 同款表达）。
        if (!operatorStatus.isNullOrBlank()) {
            filters.addAll(ExpertSearchService.operatorStatusFilter(operatorStatus))
        }
        return filters
    }

    /**
     * Explicit read-only material estimate. Traverses bounded pages and retains counters only.
     */
    private fun buildMaterialReminderSnapshot(
        scope: RecipientScope,
        now: LocalDateTime,
        onSelection: ((RecipientSelection) -> Unit)? = null
    ): MaterialReminderSnapshot {
        val scopeDescription = scope.funnelLevels.joinToString("+") + " + tags=${scope.tags}" +
            (scope.emailDomains.takeIf { it.isNotEmpty() }?.let { " + domains=" + it.joinToString(",") } ?: "") +
            (scope.discipline?.let { " + discipline=$it" } ?: "")
        return buildMaterialReminderSnapshotFromScope(scope, scopeDescription, now, onSelection)
    }

    private fun buildMaterialReminderSnapshot(config: BatchSendConfig): MaterialReminderSnapshot {
        val scope = RecipientScope(
            mailType = BatchSendType.MATERIAL_REMINDER.name,
            funnelLevels = setOf("APPLICATION"),
            tags = listOf("承诺回复材料"),
            // 统计路径输入为 BatchSendConfig（KV 层，无地区维度），故不携带地区；发送路径经 fromSnapshot 携带
            regions = emptyList(),
            emailDomains = config.emailDomain.ifBlank { null }?.let { listOf(it) } ?: emptyList(),
            discipline = config.discipline.ifBlank { null }
        )
        val scopeDescription = "APPLICATION + tag=承诺回复材料 + email" +
            (scope.emailDomains.takeIf { it.isNotEmpty() }?.let { " + domains=" + it.joinToString(",") } ?: "") +
            (if (config.discipline.isNotBlank()) " + discipline=${config.discipline}" else "")
        return buildMaterialReminderSnapshotFromScope(scope, scopeDescription, verificationFilterNow())
    }

    /** Material targets honor [RecipientScope.funnelLevels] (I-3), not a hard-coded APPLICATION-only search. */
    private fun buildMaterialReminderSnapshotFromScope(
        scope: RecipientScope,
        scopeDescription: String,
        now: LocalDateTime,
        onSelection: ((RecipientSelection) -> Unit)? = null
    ): MaterialReminderSnapshot {
        val source = DemandCandidateSource(null, scope, null, null, { it }, now, onSelection)
        var count = 0
        source.iterator { 500 }.use { iterator ->
            while (iterator.hasNext()) {
                iterator.next()
                count = Math.addExact(count, 1)
            }
        }
        return MaterialReminderSnapshot(
            targetCount = count,
            totalEsHits = source.scanned,
            scopeDescription = scopeDescription,
            excludedVerifiedUnavailable = source.excludedVerifiedUnavailable
        )
    }

    /**
     * I-3: 预估与实际发送共用同一个最终谓词 —— 走 ES scroll 对每个批次做内存最终筛选，
     * 绝不把粗筛命中数当成可发送数。`excludeVerifiedUnavailableEmails` 打开时叠加历史不可达过滤；
     * `shouldStop` 保留取消语义（取消后不再翻页、不再计数）。
     * A3: `gate` 是精确模板门禁（[batchTemplateGate]），`onSelection` 供预估汇总准入/原因明细。
     * R-1: retry identities seed a private global set, just as [OutreachTargetIterator] consumes
     * each normalized identity before selection across all pages/levels. Never mutate execution's set.
     */
    @JvmOverloads
    private fun countEsTargets(
        scope: RecipientScope,
        now: LocalDateTime,
        shouldStop: () -> Boolean = { false },
        gate: (List<ExpertProfile>) -> List<ExpertProfile> = { it },
        onSelection: ((RecipientSelection) -> Unit)? = null,
        initialSeenOrcids: Set<String> = emptySet(),
        initialSeenDocIds: Set<String> = emptySet(),
        initialSeenPersonKeys: Set<String> = emptySet()
    ): Pair<Int, Int> {
        var sendable = 0
        var excluded = 0
        // 原始 ES 身份（规范化 ORCID）级去重：与既有「同一身份只判定一次、保留首次排除原因」契约一致。
        val seenOrcids = initialSeenOrcids.toMutableSet()
        val seenDocIds = initialSeenDocIds.toMutableSet()
        // 人物级去重键：只在完整判定后登记，避免「先标记后审核」丢掉同人后续合格成员。
        val seenPersonKeys = initialSeenPersonKeys.toMutableSet()
        for (level in scope.funnelLevels) {
            if (shouldStop()) break
            expertSearchService.scrollExpertsFiltered(
                level = ExpertIndexLevel.valueOf(level),
                filters = buildEsFiltersForLevel(scope, level),
                batchSize = 500
            ) { batch ->
                if (shouldStop()) {
                    false
                } else {
                    // I-2（R-7）：与重试/执行共用同一人物集合 —— 已处理人物（含同人另一成员）不再计数。
                    val personKeys = discoveryPersonRecipientService.personKeysOf(batch)
                    val unseen = batch.filter {
                        val docId = BatchRecipientSelectionService.docIdOf(it)
                        seenOrcids.add(normalizeOrcid(it.orcidId)) &&
                            docId !in seenDocIds &&
                            (personKeys[docId] ?: docId) !in seenPersonKeys
                    }
                        .distinctBy { BatchRecipientSelectionService.docIdOf(it) }
                    // I-1/I-2/I-3: 人物级选择 —— 每人最多一个入选 docId（跨页/批次/预估同源）。
                    val personSelection = if (unseen.isEmpty()) PersonRecipientSelection.EMPTY
                    else discoveryPersonRecipientService.select(
                        scope, unseen, onPerEmailSelection = onSelection
                    )
                    val selected = personSelection.selectedDocIds
                    val matched = gate(
                        unseen.filter { BatchRecipientSelectionService.docIdOf(it) in selected }
                    )
                    val retained = filterKnownProfiles(matched, scope.excludeVerifiedUnavailableEmails, now)
                    // 真实 docId 只计一次；人物键在完整判定后登记。
                    sendable += retained.count { seenDocIds.add(BatchRecipientSelectionService.docIdOf(it)) }
                    retained.forEach {
                        val docId = BatchRecipientSelectionService.docIdOf(it)
                        seenPersonKeys.add(personKeys[docId] ?: docId)
                    }
                    excluded += matched.size - retained.size
                    !shouldStop()
                }
            }
        }
        return sendable to excluded
    }

    private fun verificationFilterNow(): LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))

    private fun findKnownUndeliverable(emails: Collection<String?>, now: LocalDateTime): Set<String> {
        val distinctEmails = emails.asSequence()
            .map(::normalizeVerificationEmail)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        if (distinctEmails.isEmpty()) return emptySet()
        return distinctEmails.asSequence()
            .chunked(500)
            .flatMap { batchEmailVerificationService.findKnownUndeliverableEmails(it, now).asSequence() }
            .toSet()
    }

    private fun filterKnownProfiles(
        profiles: List<ExpertProfile>,
        enabled: Boolean,
        now: LocalDateTime
    ): List<ExpertProfile> {
        if (!enabled || profiles.isEmpty()) return profiles
        val known = findKnownUndeliverable(profiles.map { it.email }, now)
        return profiles.filter { normalizeVerificationEmail(it.email) !in known }
    }

    /**
     * I-1/I-3: 人物级选择过滤（同一 docId 去重后保留原序）。预估、ES 取页与 NEW 重试共用，
     * 每人最多一个入选 docId；无人物映射时退化为逐邮箱选择，范围外行为不变。
     */
    private fun selectIncluded(
        scope: RecipientScope,
        profiles: List<ExpertProfile>,
        seenDocIds: Set<String> = emptySet()
    ): List<ExpertProfile> {
        val distinct = profiles.asSequence()
            .filter { BatchRecipientSelectionService.docIdOf(it) !in seenDocIds }
            .distinctBy { BatchRecipientSelectionService.docIdOf(it) }
            .toList()
        if (distinct.isEmpty()) return distinct
        val selected = discoveryPersonRecipientService.select(scope, distinct).selectedDocIds
        return distinct.filter { BatchRecipientSelectionService.docIdOf(it) in selected }
    }
    private fun <T> filterTargets(
        targets: List<T>,
        enabled: Boolean,
        now: LocalDateTime,
        email: (T) -> String?
    ): Pair<List<T>, Int> {
        if (!enabled || targets.isEmpty()) return targets to 0
        val known = findKnownUndeliverable(targets.map(email), now)
        val filtered = targets.filter { normalizeVerificationEmail(email(it)) !in known }
        return filtered to (targets.size - filtered.size)
    }

    private fun buildEsFiltersForLevel(scope: RecipientScope, level: String): List<Map<String, Any>> {
        // I-2: 状态空集合 = 不限，绝不偷偷切到 NOT_CONTACTED 基座（页面承诺与执行一致）。
        // 状态筛选一律走 operatorStatusesFilter（空集合返回 null，不追加）：
        // NOT_CONTACTED = ES 文档无该字段，其余走 term；含非 NOT_CONTACTED 状态时也自然正确。
        val base = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        ExpertSearchService.emailDomainsFilter(scope.emailDomains)?.let { base.add(it) }
        scope.discipline?.let { base.add(ExpertSearchService.disciplineFilter(it)) }
        ExpertSearchService.operatorStatusesFilter(scope.operatorStatuses)?.let { base.add(it) }
        val filters = base
        if (scope.tags.isNotEmpty()) {
            filters.add(mapOf("terms" to mapOf("tags" to scope.tags)))
        }
        ExpertSearchService.regionsFilter(scope.regions)?.let { regionFilter ->
            filters.add(withDiscoveryUnmappedCountryBranch(scope.regions, regionFilter))
        }
        // I-2: 方向三态 —— ABSENT 是既有 PRESENT 存在性 filter 的 bool.must_not；
        // ANY 不追加任何项（旧任务人群不变）。与模板门禁字段/研发类型平铺为 AND（I-3）。
        when (scope.researchDirectionFilter) {
            ResearchDirectionFilters.PRESENT ->
                filters.add(ExpertSearchService.fieldPresenceFilter(ResearchDirectionFilters.ES_FIELD))
            ResearchDirectionFilters.ABSENT ->
                filters.add(
                    mapOf(
                        "bool" to mapOf(
                            "must_not" to listOf(
                                ExpertSearchService.fieldPresenceFilter(ResearchDirectionFilters.ES_FIELD)
                            )
                        )
                    )
                )
        }
        // I4a-2: 门禁字段之间 AND —— 平铺进 filter 数组，不用 should。
        // I4a-1: 空集合时 fieldPresenceFilters 返回空列表，不追加任何项。
        filters.addAll(ExpertSearchService.fieldPresenceFilters(scope.gateEsFields))
        // I4-1: INTRODUCTION 的唯一收口点 —— 只按研发类型集合判定，无第二个门禁。
        // I4-2: 空集合 = 发给零个人（fail-closed），不是"不限"。
        if (scope.mailType == BatchSendType.INTRODUCTION.name) {
            filters.add(
                ExpertSearchService.expertTypesFilter(scope.expertTypes)
                    ?: ExpertSearchService.MATCH_NONE_FILTER
            )
        }
        return filters
    }

    /**
     * I-2：`Other` 被选中时，既有地区粗筛（[ExpertSearchService.regionsFilter]）与
     * 「`tags=discovered` 且 `country` 字段不存在」取 OR（`minimum_should_match=1`）。
     *
     * `country` 为 null 时 ES 文档没有该字段，既有 `Other` 查询的 `exists country` 前置永远不命中，
     * 仅修内存 matcher 会让这批新发现专家在粗筛阶段就被丢弃。空白或未识别**非空**值不走此分支
     * （`exists` 为真，由既有 `Other` 查询覆盖）。分支只做并集扩展，不缩小任何既有条件，
     * 因此不会提前丢掉非地区原因的目标；最终仍由同一个 [RecipientScope.matchesRegion] 判定。
     */
    private fun withDiscoveryUnmappedCountryBranch(
        regions: List<String>,
        regionFilter: Map<String, Any>
    ): Map<String, Any> {
        if (CountryContinentMapping.REGION_OTHER !in regions) return regionFilter
        return mapOf(
            "bool" to mapOf(
                "should" to listOf(
                    regionFilter,
                    mapOf(
                        "bool" to mapOf(
                            "must" to listOf(mapOf("term" to mapOf("tags" to DISCOVERED_TAG))),
                            "must_not" to listOf(mapOf("exists" to mapOf("field" to "country")))
                        )
                    )
                ),
                "minimum_should_match" to 1
            )
        )
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
            emailDomains = emailDomain.ifBlank { null }?.let { listOf(it) } ?: emptyList(),
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
            emailDomain = emailDomains.firstOrNull().orEmpty(),
            discipline = discipline.orEmpty(),
            templateId = templateId
        )

    private fun buildResult(
        total: Int,
        accumulator: OutcomeAccumulator,
        wasCancelled: Boolean,
        finalStatus: String?,
        stopReason: String?
    ): ManualOutreachResult {
        if (accumulator.executionMetadata == null) {
            accumulator.annotateTerminalRemaining(if (wasCancelled) "CANCELLED" else stopReason)
        }
        val outcome = accumulator.toBreakdown()
        val skippedNoAccount = outcome.skippedReasons[BatchOutcomeReasonCodes.ACCOUNT_UNAVAILABLE]?.count ?: 0
        return ManualOutreachResult(
            total = if (accumulator.executionMetadata != null) outcome.target else total,
            sent = outcome.success,
            failed = outcome.failure,
            skipped = outcome.skipped,
            skippedNoAccount = skippedNoAccount,
            wasCancelled = wasCancelled,
            finalStatus = finalStatus,
            stopReason = stopReason,
            remaining = outcome.remaining,
            outcome = outcome,
            execution = accumulator.executionMetadata?.invoke()
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
        accumulator.executionMetadata?.invoke()?.let { details["execution"] = it }
        if (stopReason != null) details["stopReason"] = stopReason
        progressStore.update("MANUAL_INITIAL_OUTREACH", TaskProgress(
            taskType = "MANUAL_INITIAL_OUTREACH", status = status,
            batchNumber = batchNumber,
            processedCount = if (accumulator.executionMetadata != null) breakdown.target.toLong() else processed.toLong(),
            totalCount = if (accumulator.executionMetadata != null) {
                if (status == "RUNNING") 0L else breakdown.target.toLong()
            } else total.toLong(),
            message = message,
            details = details,
            errors = errors.toList(),
            batchProcessed = batchProcessed,
            batchPassed = batchPassed,
            batchRejected = batchRejected,
            executionId = executionId
        ), executionId)
    }

    /** I-1/I-5（02）：诊断母集扫描结果；`partial=true` 表示超限/取消，统计不完整。 */
    private data class BaseScan(
        val profiles: List<ExpertProfile>,
        val partial: Boolean
    )

    private data class MaterialReminderSnapshot(
        val targetCount: Int,
        val totalEsHits: Long,
        val scopeDescription: String,
        val excludedVerifiedUnavailable: Int = 0
    )
}

data class PendingOutreachSummary(
    val pending: Int,
    val retryable: Int,
    val totalSendable: Int,
    val excludedVerifiedUnavailable: Int = 0,
    /**
     * A3 (I-4): 预估时冻结的模板版本摘要。执行开始时用同一模板的当前令牌比对，不一致即"预估已过期"。
     * null = 快照无模板 / 模板不可读。
     */
    val template: PreviewTemplateVersion? = null,
    /** A3 (I-3): 本次预估的准入与显式条件计数（06 数据源）。 */
    val admission: RecipientAdmissionCounts = RecipientAdmissionCounts(),
    /** A3 (I-3): 原因命中计数（同一人多原因各计一次；总排除按人去重）。 */
    val reasonHits: Map<String, Int> = emptyMap(),
    /** A3 (I-3): 被排除者逐人原因 key（受 [PREVIEW_EXCLUDED_DETAIL_LIMIT] 限制，计数不受限）。 */
    val excludedRecipients: List<PreviewExcludedRecipient> = emptyList(),
    /**
     * I-1/I-5（02 / MAIN M-3）：完整母集（仅所选层级/标签）按既有规则去重后的目标数。
     * `null` = 旧响应/未计算（旧 UI 显示「详细解释不可用」，**禁止补 0**）。
     */
    val baseline: Int? = null,
    /** MAIN M-3：确定性条件/准入/保护未通过的目标去重数（`baseline = excludedUnique + eligibleBeforeLiveVerification`）。 */
    val excludedUnique: Int? = null,
    /** MAIN M-3：确定性检查通过的目标数（仍可能等待真实验证或发送前复核）。 */
    val eligibleBeforeLiveVerification: Int? = null,
    /** MAIN M-3：实时验证开启但无有效可复用结果的通过目标数（是 [eligibleBeforeLiveVerification] 的子集）。 */
    val awaitingVerification: Int? = null,
    /** MAIN M-3：本次整体邮箱验证相位（`DISABLED`/`CACHED_ALLOWED`/`CACHED_REJECTED`/`AWAITING_VERIFICATION`）。 */
    val verificationPhase: String? = null,
    /** MAIN M-3：母集读取是否不完整；true 时不得套用完整总数承诺。 */
    val partial: Boolean = false,
    /** MAIN M-3：统计时点。 */
    val asOf: java.time.LocalDateTime? = null,
    /**
     * MAIN M-3：新共享口径的原因命中明细（每原因含命中数与 ≤20 样本、超限 `truncated`）。
     * 与旧 [reasonHits] 计数口径一致但可重叠，绝不相加冒充总排除人数。
     */
    val reasonHitsDetail: List<RecipientReasonHit> = emptyList()
)

/**
 * A3 (I-4): 预估冻结的模板版本摘要。[versionToken] 由模板内容摘要生成，随请求回传后由
 * [com.weibo.talentintroduction.campaign.service.BatchSendControlService] 在执行开始比对。
 */
data class PreviewTemplateVersion(
    val templateId: Long,
    val versionToken: String,
    val enabled: Boolean,
    val mailType: String?
)

/**
 * A3 (I-3): 预估准入计数（06 数据源）。
 *
 * - [admitted]：发现准入判定为通过的**去重人数**（非发现档案不计入此列）；
 * - [needsReview]：持久准入为待审核（NEEDS_REVIEW）的人数；
 * - [explicitFilterExcluded]：因本次显式条件（状态/类型/学科/域名/方向/标签/地区/门禁字段）被排除的人数；
 * - [target]：本次目标人数（= 现有 totalSendable 口径）。
 */
data class RecipientAdmissionCounts(
    val admitted: Int = 0,
    val needsReview: Int = 0,
    val explicitFilterExcluded: Int = 0,
    val target: Int = 0
)

/** A3 (I-3): 被排除者的逐人原因 key（filterKeys = 显式条件；admissionState/reasonKeys = 准入）。 */
data class PreviewExcludedRecipient(
    val docId: String,
    val orcidId: String,
    val admissionState: String,
    val filterKeys: List<String>,
    val reasonKeys: List<String>
)

/**
 * A3 (I-3): 预估侧准入摘要累加器（只读、无写入）。按真实 docId 去重后合并 selector 结论，
 * 汇总 [PendingOutreachSummary] 需要的准入计数、原因命中与逐人原因 key。
 */
internal class AdmissionPreviewAccumulator {
    private val decisions = LinkedHashMap<String, RecipientDecision>()
    private val reasonHits = LinkedHashMap<String, Int>()

    fun record(selection: RecipientSelection) {
        (selection.included + selection.excluded).forEach { decision ->
            if (decisions.putIfAbsent(decision.docId, decision) == null) {
                BatchRecipientSelectionService.reasonKeysOf(decision).forEach { key ->
                    reasonHits.merge(key, 1, Int::plus)
                }
            }
        }
    }

    fun counts(target: Int): RecipientAdmissionCounts = RecipientAdmissionCounts(
        admitted = decisions.values.count { it.admissionState == RecipientAdmissionState.ADMITTED },
        needsReview = decisions.values.count { it.admissionState == RecipientAdmissionState.NEEDS_REVIEW },
        explicitFilterExcluded = decisions.values.count { it.filterKeys.isNotEmpty() },
        target = target
    )

    fun reasonHits(): Map<String, Int> = reasonHits.toMap()

    fun excludedRecipients(limit: Int): List<PreviewExcludedRecipient> =
        decisions.values.asSequence()
            .filter { !it.included }
            .take(limit)
            .map {
                PreviewExcludedRecipient(
                    docId = it.docId,
                    orcidId = it.orcidId,
                    admissionState = it.admissionState.name,
                    filterKeys = it.filterKeys,
                    reasonKeys = BatchRecipientSelectionService.reasonKeysOf(it)
                )
            }
            .toList()
}

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
    val outcome: OutcomeBreakdown? = null,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val execution: BatchExecutionMetadata? = null
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
