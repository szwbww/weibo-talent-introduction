package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class MailSenderAccountService(
    private val repository: MailSenderAccountRepository,
    private val selfCheckService: SenderAccountSelfCheckService
) {
    fun listAccounts(): List<MailSenderAccount> =
        repository.findAllByOrderByAccountCodeAsc()

    fun getEnabledAccount(accountCode: String): MailSenderAccount =
        repository.findByAccountCodeAndEnabledTrue(accountCode)
            ?: error("Enabled mail sender account not found: $accountCode")

    fun getAccount(accountCode: String): MailSenderAccount =
        repository.findByAccountCode(accountCode)
            ?: error("Mail sender account not found: $accountCode")

    fun listEnabledAccounts(): List<MailSenderAccount> =
        repository.findAllByEnabledTrue()

    fun listAutoReceiveAccounts(): List<MailSenderAccount> =
        repository.findAllByEnabledTrueAndAccountCodeNot(SIMULATOR_ACCOUNT_CODE)

    fun getAutoReceiveAccount(accountCode: String): MailSenderAccount {
        val account = repository.findByAccountCode(accountCode)
            ?: error("Mail sender account not found: $accountCode")
        if (account.accountCode == SIMULATOR_ACCOUNT_CODE) {
            error("Mail sender account is not allowed for auto receive: $accountCode")
        }
        if (!account.enabled) {
            error("Mail sender account is disabled: $accountCode")
        }
        return account
    }

    fun getAutoReceiveAccountOrNull(accountCode: String): MailSenderAccount? =
        try {
            getAutoReceiveAccount(accountCode)
        } catch (_: Exception) {
            null
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

        return repository.save(command.toDomain())
    }

    fun updateAccount(accountCode: String, command: MailSenderAccountUpdateCommand): MailSenderAccount {
        val existing = getAccount(accountCode)
        require(command.strategyWeight > 0) { "strategyWeight must be positive" }
        require(command.dailySendLimit > 0) { "dailySendLimit must be positive" }
        require(command.todaySentCount >= 0) { "todaySentCount must not be negative" }
        require(command.todaySentCount <= command.dailySendLimit) {
            "todaySentCount must not exceed dailySendLimit"
        }

        return repository.save(
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
                smtpPassword = command.smtpPassword,
                imapHost = command.imapHost,
                imapPort = command.imapPort,
                imapUsername = command.imapUsername,
                imapPassword = command.imapPassword,
                strategyWeight = command.strategyWeight,
                dailySendLimit = command.dailySendLimit,
                todaySentCount = command.todaySentCount,
                enabled = command.enabled
            )
        )
    }

    fun setEnabled(accountCode: String, enabled: Boolean): MailSenderAccount {
        val existing = getAccount(accountCode)
        return repository.save(existing.copy(enabled = enabled))
    }

    fun resetTodaySentCount(accountCode: String): MailSenderAccount {
        val existing = getAccount(accountCode)
        return repository.save(existing.copy(todaySentCount = 0))
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

    fun listSendableAccounts(): List<MailSenderAccount> =
        repository.findAllByEnabledTrue().filter { isSendable(it) }

    private fun isSendable(account: MailSenderAccount): Boolean =
        account.enabled &&
            !account.autoSendPaused &&
            account.todaySentCount < account.dailySendLimit &&
            account.accountCode != SIMULATOR_ACCOUNT_CODE

    private fun selectionScore(account: MailSenderAccount): Double {
        val remainingRatio = (account.dailySendLimit - account.todaySentCount).toDouble() / account.dailySendLimit
        return account.strategyWeight * remainingRatio
    }

    companion object {
        const val SIMULATOR_ACCOUNT_CODE = "SIMULATOR_NOOP"
    }
}

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
    val smtpPassword: String,
    val imapHost: String,
    val imapPort: Int,
    val imapUsername: String,
    val imapPassword: String,
    val strategyWeight: Int,
    val dailySendLimit: Int,
    val todaySentCount: Int,
    val enabled: Boolean
)
