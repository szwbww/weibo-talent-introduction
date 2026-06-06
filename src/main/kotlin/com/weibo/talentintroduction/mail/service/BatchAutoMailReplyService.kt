package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class BatchAutoMailReplyService(
    private val mailSenderAccountService: MailSenderAccountService,
    private val autoMailReplyService: AutoMailReplyService
) {
    private val log = LoggerFactory.getLogger(BatchAutoMailReplyService::class.java)

    fun receiveAndAutoReplyAll(maxMessagesPerAccount: Int): BatchAutoMailReplyResult {
        val accounts = mailSenderAccountService.listAutoReceiveAccounts()
        val results = accounts.map { account ->
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
                    errorMessage = null
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

        val successResults = results.filter { it.status == "SUCCESS" }
        val failedResults = results.filter { it.status == "FAILED" }
        val taskStatus = when {
            failedResults.isEmpty() -> null
            successResults.isEmpty() -> "FAILED"
            else -> "PARTIAL_SUCCESS"
        }

        return BatchAutoMailReplyResult(
            accountCount = results.size,
            successAccountCount = successResults.size,
            failedAccountCount = failedResults.size,
            fetched = successResults.sumOf { it.fetched },
            recorded = successResults.sumOf { it.recorded },
            replied = successResults.sumOf { it.replied },
            manualReview = successResults.sumOf { it.manualReview },
            accounts = results,
            taskFinalStatus = taskStatus
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
    val errorMessage: String?
)
