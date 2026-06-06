package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class BatchAutoMailReplyService(
    private val mailSenderAccountService: MailSenderAccountService,
    private val autoMailReplyService: AutoMailReplyService,
    private val mailRecordRepository: MailRecordRepository
) {
    private val log = LoggerFactory.getLogger(BatchAutoMailReplyService::class.java)

    fun receiveAndAutoReplyAll(maxMessagesPerAccount: Int): BatchAutoMailReplyResult {
        val accounts = mailSenderAccountService.listAutoReceiveAccounts()
        val startedAt = System.currentTimeMillis()
        val perAccountResults = pollAccounts(accounts, maxMessagesPerAccount)
        val finishedAt = System.currentTimeMillis()

        return buildResult(perAccountResults, startedAt, finishedAt)
    }

    fun receiveAndAutoReplyForContacts(
        contactIds: List<Long>,
        maxMessagesPerAccount: Int
    ): BatchAutoMailReplyResult {
        if (contactIds.isEmpty()) {
            return receiveAndAutoReplyAll(maxMessagesPerAccount)
        }

        val accountCodes = mailRecordRepository
            .findDistinctSenderAccountCodesByExpertContactIds(contactIds)

        if (accountCodes.isEmpty()) {
            error("No auto-receive account found for selected contacts: ${contactIds.joinToString()}")
        }

        val resolvedAccounts = accountCodes.map { code ->
            code to mailSenderAccountService.getAutoReceiveAccountOrNull(code)
        }
        val unavailableAccountCodes = resolvedAccounts
            .filter { it.second == null }
            .map { it.first }

        if (unavailableAccountCodes.isNotEmpty()) {
            error(
                "Selected contacts map to unavailable auto-receive accounts: " +
                    unavailableAccountCodes.joinToString()
            )
        }

        val accounts = resolvedAccounts.map { it.second!! }

        val startedAt = System.currentTimeMillis()
        val perAccountResults = pollAccounts(accounts, maxMessagesPerAccount)
        val finishedAt = System.currentTimeMillis()

        return buildResult(perAccountResults, startedAt, finishedAt)
    }

    private fun pollAccounts(
        accounts: List<MailSenderAccount>,
        maxMessagesPerAccount: Int
    ): List<AccountAutoMailReplyResult> =
        accounts.map { account ->
            try {
                val result = autoMailReplyService.receiveAndAutoReply(
                    accountCode = account.accountCode,
                    maxMessages = maxMessagesPerAccount
                )
                AccountAutoMailReplyResult(
                    accountCode = account.accountCode,
                    status = "SUCCESS",
                    fetched = result.fetched,
                    recorded = result.recorded,
                    replied = result.replied,
                    manualReview = result.manualReview,
                    errorMessage = null,
                    repliedExperts = result.repliedExperts
                )
            } catch (ex: Exception) {
                val errorMessage = sanitizeErrorMessage(
                    message = ex.message,
                    secrets = listOf(account.imapPassword, account.smtpPassword)
                )
                log.warn("Auto reply failed for account {}: {}", account.accountCode, errorMessage)
                AccountAutoMailReplyResult(
                    accountCode = account.accountCode,
                    status = "FAILED",
                    fetched = 0,
                    recorded = 0,
                    replied = 0,
                    manualReview = 0,
                    errorMessage = errorMessage
                )
            }
        }

    private fun buildResult(
        perAccountResults: List<AccountAutoMailReplyResult>,
        startedAt: Long,
        finishedAt: Long
    ): BatchAutoMailReplyResult {
        val successResults = perAccountResults.filter { it.status == "SUCCESS" }
        val failedResults = perAccountResults.filter { it.status == "FAILED" }
        val taskStatus = when {
            failedResults.isEmpty() -> null
            successResults.isEmpty() -> "FAILED"
            else -> "PARTIAL_SUCCESS"
        }

        return BatchAutoMailReplyResult(
            accountCount = perAccountResults.size,
            successAccountCount = successResults.size,
            failedAccountCount = failedResults.size,
            fetched = successResults.sumOf { it.fetched },
            recorded = successResults.sumOf { it.recorded },
            replied = successResults.sumOf { it.replied },
            manualReview = successResults.sumOf { it.manualReview },
            accounts = perAccountResults,
            taskFinalStatus = taskStatus,
            totalAccountsToPoll = perAccountResults.size,
            accountsPolled = perAccountResults.size,
            expertsWithReply = successResults
                .flatMap { it.repliedExperts }
                .mapNotNull { it.expertEmail?.trim()?.lowercase() }
                .distinct(),
            startedAt = startedAt,
            finishedAt = finishedAt
        )
    }

    private fun sanitizeErrorMessage(message: String?, secrets: List<String>): String? =
        secrets
            .filter { it.isNotBlank() }
            .distinct()
            .fold(message.orEmpty()) { sanitized, secret -> sanitized.replace(secret, "[REDACTED]") }
            .take(1000)
            .ifBlank { null }
}

data class BatchAutoMailReplyResult(
    val accountCount: Int,
    val successAccountCount: Int = 0,
    val failedAccountCount: Int = 0,
    val fetched: Int,
    val recorded: Int,
    val replied: Int,
    val manualReview: Int,
    val accounts: List<AccountAutoMailReplyResult>,
    val totalAccountsToPoll: Int = 0,
    val accountsPolled: Int = 0,
    val expertsWithReply: List<String> = emptyList(),
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    override val taskFinalStatus: String? = null
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = successAccountCount
    override val taskFailureCount: Int get() = failedAccountCount
}

data class AccountAutoMailReplyResult(
    val accountCode: String,
    val status: String,
    val fetched: Int,
    val recorded: Int,
    val replied: Int,
    val manualReview: Int,
    val errorMessage: String?,
    val repliedExperts: List<RepliedExpertInfo> = emptyList()
)
