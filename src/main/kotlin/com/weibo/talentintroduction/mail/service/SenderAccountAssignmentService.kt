package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class SenderAccountAssignmentService(
    private val repository: MailSenderAccountRepository,
    private val warmup: SenderWarmupService
) {
    fun selectAccount(
        expert: ExpertProfile,
        currentBatchAssignments: List<SenderExpertAssignment> = emptyList()
    ): MailSenderAccount {
        val distributionKey = distributionKey(expert)
        return repository.findAllByEnabledTrue()
            .filter {
                it.todaySentCount < warmup.effectiveDailyLimit(it) &&
                    it.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE &&
                    !it.autoSendPaused
            }
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
        val effectiveLimit = warmup.effectiveDailyLimit(account)
        val remainingRatio = (effectiveLimit - account.todaySentCount).toDouble() / effectiveLimit
        val baseScore = account.strategyWeight * remainingRatio
        val sameSegmentCount = assignments.count {
            it.accountCode == account.accountCode && it.distributionKey == distributionKey
        }
        val totalAccountCount = assignments.count { it.accountCode == account.accountCode }
        return baseScore -
            account.strategyWeight * 0.2 * sameSegmentCount -
            account.strategyWeight * 0.02 * totalAccountCount
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
