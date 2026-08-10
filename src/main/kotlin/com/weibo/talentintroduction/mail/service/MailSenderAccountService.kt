package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.CampaignRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class MailSenderAccountService(
    private val repository: MailSenderAccountRepository,
    private val selfCheckService: SenderAccountSelfCheckService,
    private val smtpSenderFactory: SmtpSenderFactory,
    private val warmup: SenderWarmupService,
    private val connectivityService: MailAccountConnectivityService,
    private val campaignRepository: CampaignRepository,
    private val expertContactRepository: ExpertContactRepository
) {
    fun listAccounts(): List<MailSenderAccount> =
        repository.findAllByOrderByAccountCodeAsc()

    fun bindingCountsByAccount(): Map<String, Long> =
        expertContactRepository.countBindingsByAccount()
            .associate { it.accountCode to it.boundCount }

    fun effectiveDailyLimitFor(account: MailSenderAccount): Int =
        warmup.effectiveDailyLimit(account)

    fun getEnabledAccount(accountCode: String): MailSenderAccount =
        repository.findByAccountCodeAndEnabledTrue(accountCode)
            ?: error("Enabled mail sender account not found: $accountCode")

    fun getAccount(accountCode: String): MailSenderAccount =
        repository.findByAccountCode(accountCode)
            ?: error("Mail sender account not found: $accountCode")

    fun listEnabledAccounts(): List<MailSenderAccount> =
        repository.findAllByEnabledTrue()

    fun listAutoReceiveAccounts(): List<MailSenderAccount> =
        repository.findAllByAccountCodeNot(SIMULATOR_ACCOUNT_CODE)

    fun getReceiveAccount(accountCode: String): MailSenderAccount {
        val account = repository.findByAccountCode(accountCode)
            ?: error("Mail sender account not found: $accountCode")
        if (account.accountCode == SIMULATOR_ACCOUNT_CODE) {
            error("Mail sender account is not allowed for receive: $accountCode")
        }
        return account
    }

    fun getAutoReceiveAccount(accountCode: String): MailSenderAccount =
        getReceiveAccount(accountCode)

    fun getAutoReceiveAccountOrNull(accountCode: String): MailSenderAccount? =
        try {
            getReceiveAccount(accountCode)
        } catch (_: Exception) {
            null
        }

    fun getManualSendAccount(accountCode: String): MailSenderAccount {
        val account = repository.findByAccountCode(accountCode)
            ?: error("Mail sender account not found: $accountCode")
        if (account.accountCode == SIMULATOR_ACCOUNT_CODE) {
            error("Mail sender account is not allowed for manual send: $accountCode")
        }
        return account
    }

    fun createAccount(command: MailSenderAccountCreateCommand): MailSenderAccount {
        require(command.accountCode.isNotBlank()) { "accountCode is required" }
        require(command.senderEmail.isNotBlank()) { "senderEmail is required" }
        require(command.smtpHost.isNotBlank()) { "smtpHost is required" }
        require(command.imapHost.isNotBlank()) { "imapHost is required" }
        require(command.strategyWeight > 0) { "strategyWeight must be positive" }
        require(command.dailySendLimit > 0) { "dailySendLimit must be positive" }
        require(!repository.existsByAccountCode(command.accountCode)) {
            "Mail sender account already exists: ${command.accountCode}"
        }
        require(command.smtpPassword.isNotBlank()) { "smtpPassword is required" }
        require(command.imapPassword.isNotBlank()) { "imapPassword is required" }

        return repository.save(command.toDomain().copy(enabled = false))
    }

    fun updateAccount(accountCode: String, command: MailSenderAccountUpdateCommand): MailSenderAccount {
        val existing = getAccount(accountCode)
        require(command.strategyWeight > 0) { "strategyWeight must be positive" }
        require(command.dailySendLimit > 0) { "dailySendLimit must be positive" }
        require(command.todaySentCount >= 0) { "todaySentCount must not be negative" }
        require(command.todaySentCount <= command.dailySendLimit) {
            "todaySentCount must not exceed dailySendLimit"
        }

        val smtpPassword = if (command.smtpPassword.isNullOrBlank()) {
            existing.smtpPassword
        } else {
            command.smtpPassword
        }
        val imapPassword = if (command.imapPassword.isNullOrBlank()) {
            existing.imapPassword
        } else {
            command.imapPassword
        }

        if (!existing.enabled && command.enabled) {
            requireConnectivityPassed(accountCode)
        }

        warmup.validateWarmupStepsJson(command.warmupStepsJson)
        val warmupStartedAt = parseWarmupStartedAt(command.warmupStartedAt)

        val updated = repository.save(
            existing.copy(
                senderEmail = command.senderEmail,
                senderName = command.senderName,
                senderTitle = command.senderTitle,
                senderDisplayName = command.senderDisplayName,
                teamName = command.teamName,
                countryName = command.countryName,
                smtpHost = command.smtpHost,
                smtpPort = command.smtpPort,
                smtpUsername = command.smtpUsername,
                smtpPassword = smtpPassword,
                imapHost = command.imapHost,
                imapPort = command.imapPort,
                imapUsername = command.imapUsername,
                imapPassword = imapPassword,
                strategyWeight = command.strategyWeight,
                dailySendLimit = command.dailySendLimit,
                todaySentCount = command.todaySentCount,
                enabled = command.enabled,
                warmupEnabled = command.warmupEnabled,
                warmupStartedAt = warmupStartedAt,
                warmupStepsJson = command.warmupStepsJson?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
        smtpSenderFactory.evict(accountCode)
        return updated
    }

    fun setEnabled(accountCode: String, enabled: Boolean): MailSenderAccount {
        if (enabled) {
            requireConnectivityPassed(accountCode)
        }
        val existing = getAccount(accountCode)
        val updated = repository.save(existing.copy(enabled = enabled))
        if (!enabled) {
            smtpSenderFactory.evict(accountCode)
        }
        return updated
    }

    fun deleteAccount(accountCode: String) {
        val account = getAccount(accountCode)
        if (account.accountCode == SIMULATOR_ACCOUNT_CODE) {
            throw IllegalStateException("模拟器账号不可删除")
        }
        val accountId = account.id ?: error("Mail sender account id is null: $accountCode")
        if (campaignRepository.existsBySenderAccountId(accountId)) {
            throw IllegalStateException("该账号已被活动引用，无法删除")
        }
        smtpSenderFactory.evict(accountCode)
        repository.deleteById(accountId)
    }

    fun resetTodaySentCount(accountCode: String): MailSenderAccount {
        val existing = getAccount(accountCode)
        return repository.save(existing.copy(todaySentCount = 0))
    }

    /**
     * 每日重置：清零 todaySentCount + 解除限额暂停。
     * 由定时任务调用。
     */
    @Transactional
    fun resetDailyCounts(): DailyResetResult {
        val todayStart = LocalDate.now().atStartOfDay()
        val countReset = repository.resetDailyCountsBeforeDate(todayStart)
        val pauseResumed = repository.resumeDailyLimitPausedAccounts()
        return DailyResetResult(countReset = countReset, pauseResumed = pauseResumed)
    }

    fun pauseAutoSend(accountCode: String, reason: String) {
        repository.pauseAutoSend(accountCode, reason, java.time.LocalDateTime.now())
    }

    fun resumeAutoSend(accountCode: String) {
        repository.resumeAutoSend(accountCode)
        selfCheckService.invalidate(accountCode)
    }

    fun selectAccountForSending(): MailSenderAccount =
        repository.findAllByEnabledTrue()
            .filter { isSendable(it) }
            .maxWithOrNull(compareBy<MailSenderAccount> { selectionScore(it) }.thenBy { it.id ?: 0L })
            ?: error("No available mail sender account")

    fun selectAccountForManualSending(): MailSenderAccount =
        repository.findAllByAccountCodeNot(SIMULATOR_ACCOUNT_CODE)
            .filter { isManualSendable(it) }
            .maxWithOrNull(compareBy<MailSenderAccount> { selectionScore(it) }.thenBy { it.id ?: 0L })
            ?: error("No available mail sender account for manual send")

    fun listSendableAccounts(ignoreWarmup: Boolean = false): List<MailSenderAccount> =
        repository.findAllByEnabledTrue().filter { isSendable(it, ignoreWarmup) }

    fun remainingDailyCapacity(ignoreWarmup: Boolean = false): Int =
        listEnabledAccounts()
            .filter { it.accountCode != SIMULATOR_ACCOUNT_CODE && !it.autoSendPaused }
            .sumOf { warmup.remainingCapacity(it, ignoreWarmup = ignoreWarmup) }

    fun warmupActiveCount(): Int =
        listEnabledAccounts()
            .filter { it.accountCode != SIMULATOR_ACCOUNT_CODE && !it.autoSendPaused }
            .count { warmup.isWarmupActive(it) }

    fun todayTotalCapacity(): Int =
        listEnabledAccounts()
            .filter { it.accountCode != SIMULATOR_ACCOUNT_CODE && !it.autoSendPaused }
            .sumOf { warmup.effectiveDailyLimit(it) }

    private fun isSendable(account: MailSenderAccount, ignoreWarmup: Boolean = false): Boolean =
        account.enabled &&
            !account.autoSendPaused &&
            account.todaySentCount < warmup.effectiveDailyLimit(account, ignoreWarmup = ignoreWarmup) &&
            account.accountCode != SIMULATOR_ACCOUNT_CODE

    private fun isManualSendable(account: MailSenderAccount): Boolean =
        account.enabled &&
            account.accountCode != SIMULATOR_ACCOUNT_CODE

    private fun selectionScore(account: MailSenderAccount): Double {
        val effectiveLimit = warmup.effectiveDailyLimit(account)
        val remainingRatio = (effectiveLimit - account.todaySentCount).toDouble() / effectiveLimit
        return account.strategyWeight * remainingRatio
    }

    private fun requireConnectivityPassed(accountCode: String) {
        val result = connectivityService.testAccount(accountCode)
        if (!result.passed) {
            throw IllegalStateException(
                "连通性测试未通过: SMTP=${result.smtp.message}, IMAP=${result.imap.message}"
            )
        }
    }

    private fun parseWarmupStartedAt(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) {
            return null
        }
        return try {
            LocalDateTime.parse(value.trim())
        } catch (_: Exception) {
            throw IllegalArgumentException("warmupStartedAt must be an ISO-8601 datetime")
        }
    }

    companion object {
        const val SIMULATOR_ACCOUNT_CODE = "SIMULATOR_NOOP"
    }
}

data class DailyResetResult(val countReset: Int, val pauseResumed: Int)

data class MailSenderAccountCreateCommand(
    val accountCode: String,
    val senderEmail: String,
    val senderName: String,
    val senderTitle: String?,
    val senderDisplayName: String?,
    val teamName: String?,
    val countryName: String?,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpUsername: String,
    val smtpPassword: String,
    val imapHost: String,
    val imapPort: Int,
    val imapUsername: String,
    val imapPassword: String,
    val strategyWeight: Int = 100,
    val dailySendLimit: Int = 100,
    val enabled: Boolean = true
) {
    fun toDomain(): MailSenderAccount {
        val now = LocalDateTime.now()
        return MailSenderAccount(
            accountCode = accountCode,
            senderEmail = senderEmail,
            senderName = senderName,
            senderTitle = senderTitle,
            senderDisplayName = senderDisplayName,
            teamName = teamName,
            countryName = countryName,
            smtpHost = smtpHost,
            smtpPort = smtpPort,
            smtpUsername = smtpUsername,
            smtpPassword = smtpPassword,
            imapHost = imapHost,
            imapPort = imapPort,
            imapUsername = imapUsername,
            imapPassword = imapPassword,
            strategyWeight = strategyWeight,
            dailySendLimit = dailySendLimit,
            enabled = enabled,
            createdAt = now,
            updatedAt = now
        )
    }
}

data class MailSenderAccountUpdateCommand(
    val senderEmail: String,
    val senderName: String,
    val senderTitle: String?,
    val senderDisplayName: String?,
    val teamName: String?,
    val countryName: String?,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpUsername: String,
    val smtpPassword: String?,
    val imapHost: String,
    val imapPort: Int,
    val imapUsername: String,
    val imapPassword: String?,
    val strategyWeight: Int,
    val dailySendLimit: Int,
    val todaySentCount: Int,
    val enabled: Boolean,
    val warmupEnabled: Boolean? = null,
    val warmupStartedAt: String? = null,
    val warmupStepsJson: String? = null
)
