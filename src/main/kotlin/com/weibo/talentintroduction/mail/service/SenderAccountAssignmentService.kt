package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class SenderAccountAssignmentService(
    private val repository: MailSenderAccountRepository,
    private val warmup: SenderWarmupService,
    private val expertContactRepository: ExpertContactRepository
) {
    fun selectAccount(
        expert: ExpertProfile,
        currentBatchAssignments: List<SenderExpertAssignment> = emptyList(),
        ignoreWarmup: Boolean = false,
        stock: SenderBindingStock = SenderBindingStock.EMPTY
    ): MailSenderAccount {
        val distributionKey = distributionKey(expert)
        return repository.findAllByEnabledTrue()
            .filter {
                it.todaySentCount < warmup.effectiveDailyLimit(it, ignoreWarmup = ignoreWarmup) &&
                    it.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE &&
                    !it.autoSendPaused
            }
            .maxWithOrNull(
                compareBy<MailSenderAccount> { account ->
                    assignmentScore(account, distributionKey, currentBatchAssignments, ignoreWarmup, stock)
                }.thenBy { it.id ?: 0L }
            )
            ?: throw NoAvailableSenderAccountException("No available mail sender account")
    }

    private fun assignmentScore(
        account: MailSenderAccount,
        distributionKey: String,
        assignments: List<SenderExpertAssignment>,
        ignoreWarmup: Boolean = false,
        stock: SenderBindingStock = SenderBindingStock.EMPTY
    ): Double {
        val effectiveLimit = warmup.effectiveDailyLimit(account, ignoreWarmup = ignoreWarmup)
        val remainingRatio = (effectiveLimit - account.todaySentCount).toDouble() / effectiveLimit
        val baseScore = account.strategyWeight * remainingRatio
        val sameSegmentCount = assignments.count {
            it.accountCode == account.accountCode && it.distributionKey == distributionKey
        }
        val totalAccountCount = assignments.count { it.accountCode == account.accountCode }
        return baseScore -
            account.strategyWeight * 0.2 * sameSegmentCount -
            account.strategyWeight * 0.02 * totalAccountCount -
            account.strategyWeight * STOCK_TOTAL_WEIGHT * stock.totalShare(account.accountCode) -
            account.strategyWeight * STOCK_SEGMENT_WEIGHT *
                stock.segmentShare(account.accountCode, distributionKey)
    }

    /** 批次开始时调用一次（I-1）。 */
    fun loadBindingStock(): SenderBindingStock {
        val totals = expertContactRepository.countBindingsByAccount()
            .associate { it.accountCode to it.boundCount }
        val segments = expertContactRepository.countBindingsByAccountAndCountry()
            .associate { (it.accountCode to normalizeKey(it.distributionKey)) to it.boundCount }
        val segmentTotals = segments.entries
            .groupBy { it.key.second }
            .mapValues { (_, entries) -> entries.sumOf { it.value } }
        return SenderBindingStock(totals, segments, segmentTotals)
    }

    private fun normalizeKey(raw: String?): String =
        raw?.lowercase(Locale.ROOT)?.trim()?.takeIf { it.isNotBlank() } ?: "unknown"

    private fun distributionKey(expert: ExpertProfile): String = normalizeKey(expert.country)

    companion object {
        /**
         * 存量惩罚系数。存量项是"占比"（[0,1]），与批内计数（小整数）量纲不同，
         * 因此使用独立系数（I-2）。取值理由：
         *  - baseScore ∈ [0, strategyWeight]，存量项最大也是 strategyWeight 的同量级，
         *    保证"剩余额度"仍能与"存量公平"竞争，而非被单方碾压。
         *  - 总量权重 > 国别权重：总量是主目标，国别分散是次目标。
         */
        const val STOCK_TOTAL_WEIGHT = 0.5
        const val STOCK_SEGMENT_WEIGHT = 0.3
    }
}

data class SenderExpertAssignment(
    val accountCode: String,
    val expertId: String,
    val distributionKey: String
)

/**
 * 批次开始时刻的存量绑定分布快照（I-1：每批次取一次，只读，I-6：不参与写决策）。
 * 空快照（EMPTY）下所有存量项恒为 0，打分与引入存量前逐字相同（I-4）。
 */
data class SenderBindingStock(
    private val totalByAccount: Map<String, Long>,
    private val segmentByAccount: Map<Pair<String, String>, Long>,
    private val segmentTotals: Map<String, Long>
) {
    val grandTotal: Long = totalByAccount.values.sum()

    fun totalShare(accountCode: String): Double =
        if (grandTotal <= 0L) 0.0
        else (totalByAccount[accountCode] ?: 0L).toDouble() / grandTotal

    fun segmentShare(accountCode: String, distributionKey: String): Double {
        val segTotal = segmentTotals[distributionKey] ?: 0L
        if (segTotal <= 0L) return 0.0
        return (segmentByAccount[accountCode to distributionKey] ?: 0L).toDouble() / segTotal
    }

    companion object {
        val EMPTY = SenderBindingStock(emptyMap(), emptyMap(), emptyMap())
    }
}

class NoAvailableSenderAccountException(message: String) : RuntimeException(message)
