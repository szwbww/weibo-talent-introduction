package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.CampaignRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.config.ManualOutreachProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderExpertAssignment
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.mail.service.NoAvailableSenderAccountException
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
    private val progressStore: TaskProgressStore,
    private val properties: ManualOutreachProperties,
    private val txHelper: ManualOutreachTxHelper,
    private val mailSendAttemptRepository: MailSendAttemptRepository
) {
    private val log = LoggerFactory.getLogger(ManualInitialOutreachService::class.java)

    fun countPending(): PendingOutreachSummary {
        var pending = 0
        expertSearchService.scrollExperts(ExpertIndexLevel.CANDIDATE) { batch ->
            for (expert in batch) {
                if (!expert.email.isNullOrBlank()) {
                    if (!expertContactRepository.existsByOrcidId(expert.orcidId)) {
                        val attempt = mailSendAttemptRepository.findByOrcidIdAndMailType(expert.orcidId, "INTRODUCTION")
                        if (attempt == null || attempt.status == "PREPARE_FAILED" || attempt.status == "FAILED_SAFE_TO_RETRY") {
                            pending++
                        }
                    }
                }
            }
            true
        }
        val retryableContacts = expertContactRepository.findAllByCurrentStatus("NEW")
        val retryable = retryableContacts.count { contact ->
            val attempt = mailSendAttemptRepository.findByOrcidIdAndMailType(contact.orcidId, "INTRODUCTION")
            attempt == null || attempt.status == "PREPARE_FAILED" || attempt.status == "FAILED_SAFE_TO_RETRY"
        }
        return PendingOutreachSummary(pending = pending, retryable = retryable)
    }

    fun runBulkOutreach(executionId: Long): ManualOutreachResult {
        log.info("Starting manual bulk outreach task execution: {}", executionId)
        val campaign = getOrCreateManualCampaign()
        val campaignId = campaign.id ?: error("Campaign ID is null")

        // 1. Scan retryable contacts (NEW status)
        val retryableContacts = expertContactRepository.findAllByCurrentStatus("NEW").filter { contact ->
            val attempt = mailSendAttemptRepository.findByOrcidIdAndMailType(contact.orcidId, "INTRODUCTION")
            attempt == null || attempt.status == "PREPARE_FAILED" || attempt.status == "FAILED_SAFE_TO_RETRY"
        }
        val retryableOrcidIds = retryableContacts.map { it.orcidId }
        val retryableProfiles = if (retryableOrcidIds.isNotEmpty()) {
            expertSearchService.searchByOrcidIds(retryableOrcidIds)
        } else {
            emptyList()
        }
        val profileMap = retryableProfiles.associateBy { it.orcidId }
        val retryableList = retryableContacts.mapNotNull { contact ->
            val profile = profileMap[contact.orcidId]
            if (profile != null) {
                Pair(contact, profile)
            } else {
                null
            }
        }

        // 2. Scan new candidates from ES
        val newCandidates = mutableListOf<ExpertProfile>()
        expertSearchService.scrollExperts(ExpertIndexLevel.CANDIDATE) { batch ->
            for (expert in batch) {
                if (!expert.email.isNullOrBlank()) {
                    if (!expertContactRepository.existsByOrcidId(expert.orcidId)) {
                        val attempt = mailSendAttemptRepository.findByOrcidIdAndMailType(expert.orcidId, "INTRODUCTION")
                        if (attempt == null || attempt.status == "PREPARE_FAILED" || attempt.status == "FAILED_SAFE_TO_RETRY") {
                            newCandidates.add(expert)
                        }
                    }
                }
            }
            true
        }

        // 3. Assemble snapshot
        val snapshot = mutableListOf<Pair<ExpertContact?, ExpertProfile>>()
        for (pair in retryableList) {
            snapshot.add(Pair(pair.first, pair.second))
        }
        for (profile in newCandidates) {
            snapshot.add(Pair(null, profile))
        }

        val totalCount = snapshot.size
        log.info("Outreach snapshot built. Total targets to process: {}", totalCount)

        if (totalCount == 0) {
            val progress = TaskProgress(
                taskType = "MANUAL_INITIAL_OUTREACH",
                status = "COMPLETED",
                batchNumber = 0,
                processedCount = 0,
                totalCount = 0,
                message = "没有需要发送的专家",
                details = mapOf("pending" to 0, "sent" to 0, "failed" to 0),
                executionId = executionId
            )
            progressStore.update("MANUAL_INITIAL_OUTREACH", progress, executionId)
            return ManualOutreachResult(0, 0, 0, 0, 0, false)
        }

        var sentCount = 0
        var failedCount = 0
        var unknownCount = 0
        var wasCancelled = false
        val errors = mutableListOf<String>()
        val assignments = mutableListOf<SenderExpertAssignment>()

        for (index in 0 until totalCount) {
            val (existingContact, expert) = snapshot[index]

            if (progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)) {
                log.info("Manual bulk outreach cancelled at index {} of {}", index, totalCount)
                wasCancelled = true
                break
            }

            // Check existing attempt status
            var attempt = mailSendAttemptRepository.findByOrcidIdAndMailType(expert.orcidId, "INTRODUCTION")
            if (attempt != null && (attempt.status == "SENT" || attempt.status == "DELIVERY_UNKNOWN")) {
                log.warn("Attempt already SENT or DELIVERY_UNKNOWN for ORCID: {}. Skipping.", expert.orcidId)
                continue
            }

            val messageId = attempt?.messageId ?: "<manual-outreach-${expert.orcidId}-${UUID.randomUUID()}@weibo.com>"

            // Select account
            val account = try {
                senderAccountAssignmentService.selectAccount(expert, assignments)
            } catch (e: NoAvailableSenderAccountException) {
                val remaining = totalCount - index
                log.warn("Account quota exhausted or no account available. Stopping manual outreach. Remaining: {}", remaining)
                val progress = TaskProgress(
                    taskType = "MANUAL_INITIAL_OUTREACH",
                    status = "COMPLETED",
                    batchNumber = index,
                    processedCount = (sentCount + failedCount + unknownCount).toLong(),
                    totalCount = totalCount.toLong(),
                    message = "发件账号今日额度耗尽，已停止；可在账号页重置后继续",
                    details = mapOf(
                        "pending" to remaining,
                        "sent" to sentCount,
                        "failed" to failedCount,
                        "unknown" to unknownCount
                    ),
                    errors = errors.toList(),
                    batchPassed = sentCount,
                    batchRejected = failedCount + unknownCount,
                    executionId = executionId
                )
                progressStore.update("MANUAL_INITIAL_OUTREACH", progress, executionId)
                return ManualOutreachResult(totalCount, sentCount, failedCount, unknownCount, remaining, false)
            } catch (e: Exception) {
                log.error("System error during account selection. Failing task.", e)
                val remaining = totalCount - index
                val progress = TaskProgress(
                    taskType = "MANUAL_INITIAL_OUTREACH",
                    status = "FAILED",
                    batchNumber = index,
                    processedCount = (sentCount + failedCount + unknownCount).toLong(),
                    totalCount = totalCount.toLong(),
                    message = "系统错误: ${e.message}",
                    details = mapOf(
                        "pending" to remaining,
                        "sent" to sentCount,
                        "failed" to failedCount,
                        "unknown" to unknownCount
                    ),
                    errors = errors.toList() + (e.message ?: "Unknown error during account selection"),
                    batchPassed = sentCount,
                    batchRejected = failedCount + unknownCount,
                    executionId = executionId
                )
                progressStore.update("MANUAL_INITIAL_OUTREACH", progress, executionId)
                return ManualOutreachResult(totalCount, sentCount, failedCount, unknownCount, remaining, false)
            }

            var contact: ExpertContact? = null
            var mailSentAttempted = false
            try {
                // Pre-create or reuse contact
                contact = if (existingContact != null) {
                    existingContact
                } else {
                    val now = LocalDateTime.now()
                    expertContactRepository.save(
                        ExpertContact(
                            campaignId = campaignId,
                            orcidId = expert.orcidId,
                            expertEmail = expert.email.orEmpty(),
                            expertName = expert.displayName,
                            currentStatus = "NEW",
                            operatorStatus = "NOT_CONTACTED",
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }

                val mail = introductionMailComposer.compose(account.accountCode, expert).copy(messageId = messageId)

                // Persist attempt as DELIVERY_UNKNOWN before calling SMTP
                attempt = mailSendAttemptRepository.save(
                    (attempt ?: MailSendAttempt(
                        orcidId = expert.orcidId,
                        mailType = "INTRODUCTION",
                        accountCode = account.accountCode,
                        messageId = messageId,
                        status = "DELIVERY_UNKNOWN",
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )).copy(
                        status = "DELIVERY_UNKNOWN",
                        accountCode = account.accountCode,
                        updatedAt = LocalDateTime.now()
                    )
                )

                mailSentAttempted = true
                val delivered = mailDeliveryService.send(account, mail)
                if (delivered.status == "SENT") {
                    try {
                        txHelper.recordSuccess(
                            contact = contact!!,
                            accountCode = account.accountCode,
                            deliveredMessageId = messageId,
                            subject = mail.subject,
                            body = mail.body
                        )
                        mailSendAttemptRepository.save(attempt.copy(
                            status = "SENT",
                            updatedAt = LocalDateTime.now()
                        ))
                        sentCount++
                    } catch (dbEx: Exception) {
                        log.error("SMTP sent successfully but post-processing database update failed for ORCID: {}", expert.orcidId, dbEx)
                        mailSendAttemptRepository.save(attempt.copy(
                            status = "DELIVERY_UNKNOWN",
                            errorSummary = "SMTP sent but DB update failed: ${dbEx.message}",
                            updatedAt = LocalDateTime.now()
                        ))
                        throw dbEx
                    }
                } else {
                    val errSummary = "Mail delivery status: ${delivered.status}"
                    attempt = mailSendAttemptRepository.save(attempt.copy(
                        status = "DELIVERY_UNKNOWN",
                        errorSummary = errSummary,
                        updatedAt = LocalDateTime.now()
                    ))
                    txHelper.recordFailure(
                        contactId = contact!!.id ?: error("Contact ID is null"),
                        accountCode = account.accountCode,
                        errorSummary = errSummary,
                        subject = mail.subject,
                        body = mail.body
                    )
                    unknownCount++
                    val errMessage = "发送失败 (${expert.email}): ${delivered.status}"
                    errors.add(errMessage)
                    if (errors.size > 20) errors.removeAt(0)

                    // Stop task for uncertain delivery status
                    log.warn("Uncertain delivery status: {}. Stopping manual outreach.", delivered.status)
                    val remaining = totalCount - (sentCount + failedCount + unknownCount)
                    val progress = TaskProgress(
                        taskType = "MANUAL_INITIAL_OUTREACH",
                        status = "FAILED",
                        batchNumber = index + 1,
                        processedCount = (sentCount + failedCount + unknownCount).toLong(),
                        totalCount = totalCount.toLong(),
                        message = "发送失败 (${expert.email}): ${delivered.status}。任务已停止以防重复发送，需人工核对。",
                        details = mapOf("pending" to remaining, "sent" to sentCount, "failed" to failedCount, "unknown" to unknownCount),
                        errors = errors.toList(),
                        batchPassed = sentCount,
                        batchRejected = failedCount + unknownCount,
                        executionId = executionId
                    )
                    progressStore.update("MANUAL_INITIAL_OUTREACH", progress, executionId)
                    return ManualOutreachResult(totalCount, sentCount, failedCount, unknownCount, remaining, false)
                }
            } catch (e: Exception) {
                val isSafe = !mailSentAttempted || isSafeToRetry(e)
                val attemptStatus = if (!mailSentAttempted) {
                    "PREPARE_FAILED"
                } else if (isSafe) {
                    "FAILED_SAFE_TO_RETRY"
                } else {
                    "DELIVERY_UNKNOWN"
                }

                log.error("Failed to process outreach for ORCID: {}, status: {}, safe to retry: {}", expert.orcidId, attemptStatus, isSafe, e)

                val currentAttempt = attempt ?: MailSendAttempt(
                    orcidId = expert.orcidId,
                    mailType = "INTRODUCTION",
                    accountCode = account.accountCode,
                    messageId = messageId,
                    status = attemptStatus,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )

                attempt = mailSendAttemptRepository.save(currentAttempt.copy(
                    status = attemptStatus,
                    errorSummary = e.message ?: "Unknown error",
                    updatedAt = LocalDateTime.now()
                ))

                if (contact != null) {
                    try {
                        val mailSubject = try {
                            introductionMailComposer.compose(account.accountCode, expert).subject
                        } catch (_: Exception) {
                            "Research Collaboration Opportunity"
                        }
                        txHelper.recordFailure(
                            contactId = contact.id ?: error("Contact ID is null"),
                            accountCode = account.accountCode,
                            errorSummary = e.message ?: "Unknown error",
                            subject = mailSubject,
                            body = ""
                        )
                    } catch (dbEx: Exception) {
                        log.error("Failed to record failure in DB for ORCID: {}", expert.orcidId, dbEx)
                    }
                }

                if (isSafe) {
                    failedCount++
                } else {
                    unknownCount++
                }
                val errMessage = "发送异常 (${expert.email}): ${e.message ?: "Unknown error"}"
                errors.add(errMessage)
                if (errors.size > 20) errors.removeAt(0)

                if (!isSafe) {
                    log.warn("Uncertain delivery exception. Stopping manual outreach.")
                    val remaining = totalCount - (sentCount + failedCount + unknownCount)
                    val progress = TaskProgress(
                        taskType = "MANUAL_INITIAL_OUTREACH",
                        status = "FAILED",
                        batchNumber = index + 1,
                        processedCount = (sentCount + failedCount + unknownCount).toLong(),
                        totalCount = totalCount.toLong(),
                        message = "发送异常 (${expert.email}): ${e.message ?: "Unknown error"}。任务已停止以防重复发送，需人工核对。",
                        details = mapOf("pending" to remaining, "sent" to sentCount, "failed" to failedCount, "unknown" to unknownCount),
                        errors = errors.toList(),
                        batchPassed = sentCount,
                        batchRejected = failedCount + unknownCount,
                        executionId = executionId
                    )
                    progressStore.update("MANUAL_INITIAL_OUTREACH", progress, executionId)
                    return ManualOutreachResult(totalCount, sentCount, failedCount, unknownCount, remaining, false)
                }
            }

            assignments.add(
                SenderExpertAssignment(
                    accountCode = account.accountCode,
                    expertId = expert.orcidId,
                    distributionKey = expert.country?.lowercase()?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"
                )
            )

            val processed = sentCount + failedCount + unknownCount
            val remaining = totalCount - processed
            val progress = TaskProgress(
                taskType = "MANUAL_INITIAL_OUTREACH",
                status = "RUNNING",
                batchNumber = index + 1,
                processedCount = processed.toLong(),
                totalCount = totalCount.toLong(),
                message = "正在发送：${expert.email}",
                details = mapOf(
                    "pending" to remaining,
                    "sent" to sentCount,
                    "failed" to failedCount,
                    "unknown" to unknownCount
                ),
                errors = errors.toList(),
                batchPassed = sentCount,
                batchRejected = failedCount + unknownCount,
                executionId = executionId
            )
            progressStore.update("MANUAL_INITIAL_OUTREACH", progress, executionId)

            val interval = properties.sendIntervalMs
            if (interval > 0 && index < totalCount - 1) {
                try {
                    Thread.sleep(interval)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        val finalStatus = if (wasCancelled) "CANCELLED" else "COMPLETED"
        val finalMessage = if (wasCancelled) "发送任务已被取消" else "发送任务已完成"
        val finalProcessed = sentCount + failedCount + unknownCount
        val finalRemaining = totalCount - finalProcessed

        val finalProgress = TaskProgress(
            taskType = "MANUAL_INITIAL_OUTREACH",
            status = finalStatus,
            batchNumber = finalProcessed,
            processedCount = finalProcessed.toLong(),
            totalCount = totalCount.toLong(),
            message = finalMessage,
            details = mapOf(
                "pending" to finalRemaining,
                "sent" to sentCount,
                "failed" to failedCount,
                "unknown" to unknownCount
            ),
            errors = errors.toList(),
            batchPassed = sentCount,
            batchRejected = failedCount + unknownCount,
            executionId = executionId
        )
        progressStore.update("MANUAL_INITIAL_OUTREACH", finalProgress, executionId)

        return ManualOutreachResult(
            total = totalCount,
            sent = sentCount,
            failed = failedCount,
            unknown = unknownCount,
            skippedNoAccount = 0,
            wasCancelled = wasCancelled
        )
    }

    private fun getOrCreateManualCampaign(): Campaign {
        val campaignCode = "MANUAL_OUTREACH"
        val existing = campaignRepository.findByCampaignCode(campaignCode)
        if (existing != null) {
            return existing
        }

        val enabledAccounts = mailSenderAccountRepository.findAllByEnabledTrue()
            .filter { it.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE }
        if (enabledAccounts.isEmpty()) {
            error("No enabled real mail accounts available to create campaign")
        }
        val account = enabledAccounts.first()

        val now = LocalDateTime.now()
        val newCampaign = Campaign(
            campaignCode = campaignCode,
            campaignName = "Manual Outreach Campaign",
            description = "Created automatically for manual bulk outreach",
            status = "ACTIVE",
            senderAccountId = account.id ?: error("Account ID is null"),
            createdAt = now,
            updatedAt = now
        )
        return campaignRepository.save(newCampaign)
    }

    private fun isSafeToRetry(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            val name = cause.javaClass.name
            if (name.contains("AuthenticationFailedException") ||
                name.contains("MailAuthenticationException") ||
                name.contains("MailConnectException") ||
                name.contains("ConnectException") ||
                name.contains("UnknownHostException") ||
                name.contains("NoRouteToHostException")
            ) {
                return true
            }
            if (cause is org.springframework.mail.MailSendException) {
                val subExceptions = cause.failedMessages
                if (subExceptions.isNotEmpty() && subExceptions.values.all { isSafeToRetry(it) }) {
                    return true
                }
            }
            cause = cause.cause
        }
        return false
    }
}

data class PendingOutreachSummary(
    val pending: Int,
    val retryable: Int
)

data class ManualOutreachResult(
    val total: Int,
    val sent: Int,
    val failed: Int,
    val unknown: Int,
    val skippedNoAccount: Int,
    val wasCancelled: Boolean
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = sent
    override val taskFailureCount: Int get() = failed + unknown
    override val taskFinalStatus: String? get() = if (wasCancelled) "CANCELLED" else null
}
