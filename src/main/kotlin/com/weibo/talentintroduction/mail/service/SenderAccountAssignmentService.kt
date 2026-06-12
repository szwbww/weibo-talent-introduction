package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class SenderAccountAssignmentService(
    private val repository: MailSenderAccountRepository
) {
    fun selectAccount(
        expert: ExpertProfile,
        currentBatchAssignments: List<SenderExpertAssignment> = emptyList()
    ): MailSenderAccount {
        val distributionKey = distributionKey(expert)
        return repository.findAllByEnabledTrue()
            .filter { it.todaySentCount < it.dailySendLimit && it.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE }
            .maxWithOrNull(
                compareBy<MailSenderAccount> { account ->
                    assignmentScore(account, distributionKey, currentBatchAssignments)
                }.thenBy { it.id ?: 0L }
            )
            ?: throw NoAvailableSenderAccountException("No available mail sender account")
    }

    private fun assignmentScore(
        account: MailSenderAccount,
        distributionKey: String,
        assignments: List<SenderExpertAssignment>
    ): Double {
        val remainingRatio = (account.dailySendLimit - account.todaySentCount).toDouble() / account.dailySendLimit
        val baseScore = account.strategyWeight * remainingRatio
        val sameSegmentCount = assignments.count {
            it.accountCode == account.accountCode && it.distributionKey == distributionKey
        }
        val totalAccountCount = assignments.count { it.accountCode == account.accountCode }
        return baseScore - sameSegmentCount * 20.0 - totalAccountCount * 2.0
    }

    private fun distributionKey(expert: ExpertProfile): String =
        expert.country
            ?.lowercase(Locale.ROOT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "unknown"
}

data class SenderExpertAssignment(
    val accountCode: String,
    val expertId: String,
    val distributionKey: String
)

class NoAvailableSenderAccountException(message: String) : RuntimeException(message)
