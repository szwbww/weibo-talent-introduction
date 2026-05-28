package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service

@Service
class BatchAutoMailReplyService(
    private val mailSenderAccountService: MailSenderAccountService,
    private val autoMailReplyService: AutoMailReplyService
) {
    fun receiveAndAutoReplyAll(maxMessagesPerAccount: Int): BatchAutoMailReplyResult {
        val results = mailSenderAccountService.listEnabledAccounts()
            .map { account ->
                val result = autoMailReplyService.receiveAndAutoReply(
                    accountCode = account.accountCode,
                    maxMessages = maxMessagesPerAccount
                )
                AccountAutoMailReplyResult(
                    accountCode = account.accountCode,
                    fetched = result.fetched,
                    recorded = result.recorded,
                    replied = result.replied,
                    manualReview = result.manualReview
                )
            }

        return BatchAutoMailReplyResult(
            accountCount = results.size,
            fetched = results.sumOf { it.fetched },
            recorded = results.sumOf { it.recorded },
            replied = results.sumOf { it.replied },
            manualReview = results.sumOf { it.manualReview },
            accounts = results
        )
    }
}

data class BatchAutoMailReplyResult(
    val accountCount: Int,
    val fetched: Int,
    val recorded: Int,
    val replied: Int,
    val manualReview: Int,
    val accounts: List<AccountAutoMailReplyResult>
)

data class AccountAutoMailReplyResult(
    val accountCode: String,
    val fetched: Int,
    val recorded: Int,
    val replied: Int,
    val manualReview: Int
)
