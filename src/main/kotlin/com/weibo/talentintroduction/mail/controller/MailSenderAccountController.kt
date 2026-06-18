package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.service.MailAccountConnectivityResult
import com.weibo.talentintroduction.mail.service.MailAccountConnectivityService
import com.weibo.talentintroduction.mail.service.MailSenderAccountCreateCommand
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailSenderAccountUpdateCommand
import com.weibo.talentintroduction.mail.service.SenderAccountSelfCheckService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mail/sender-accounts")
class MailSenderAccountController(
    private val service: MailSenderAccountService,
    private val connectivityService: MailAccountConnectivityService,
    private val selfCheckService: SenderAccountSelfCheckService
) {
    @GetMapping
    fun listAccounts(): List<MailSenderAccountResponse> =
        service.listAccounts().map { it.toResponse() }

    @GetMapping("/{accountCode}")
    fun getAccount(@PathVariable accountCode: String): MailSenderAccountResponse =
        service.getAccount(accountCode).toResponse()

    @PostMapping
    fun createAccount(@RequestBody request: MailSenderAccountCreateRequest): MailSenderAccountResponse =
        service.createAccount(request.toCommand()).toResponse()

    @PutMapping("/{accountCode}")
    fun updateAccount(
        @PathVariable accountCode: String,
        @RequestBody request: MailSenderAccountUpdateRequest
    ): MailSenderAccountResponse =
        service.updateAccount(accountCode, request.toCommand()).toResponse()

    @PostMapping("/{accountCode}/enable")
    fun enableAccount(@PathVariable accountCode: String): MailSenderAccountResponse =
        service.setEnabled(accountCode, true).toResponse()

    @PostMapping("/{accountCode}/disable")
    fun disableAccount(@PathVariable accountCode: String): MailSenderAccountResponse =
        service.setEnabled(accountCode, false).toResponse()

    @PostMapping("/{accountCode}/reset-today-sent-count")
    fun resetTodaySentCount(@PathVariable accountCode: String): MailSenderAccountResponse =
        service.resetTodaySentCount(accountCode).toResponse()

    @PostMapping("/{accountCode}/test-connectivity")
    fun testConnectivity(@PathVariable accountCode: String): MailAccountConnectivityResult =
        connectivityService.testAccount(accountCode)

    @PostMapping("/{accountCode}/resume-auto-send")
    fun resumeAutoSend(@PathVariable accountCode: String): MailSenderAccountResponse {
        service.resumeAutoSend(accountCode)
        return service.getAccount(accountCode).toResponse()
    }

    @PostMapping("/{accountCode}/self-check")
    fun selfCheck(@PathVariable accountCode: String) =
        selfCheckService.checkSendable(service.getAccount(accountCode))
}

data class MailSenderAccountCreateRequest(
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
    fun toCommand(): MailSenderAccountCreateCommand =
        MailSenderAccountCreateCommand(
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
            enabled = enabled
        )
}

data class MailSenderAccountUpdateRequest(
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
) {
    fun toCommand(): MailSenderAccountUpdateCommand =
        MailSenderAccountUpdateCommand(
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
            todaySentCount = todaySentCount,
            enabled = enabled
        )
}

data class MailSenderAccountResponse(
    val id: Long?,
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
    val imapHost: String,
    val imapPort: Int,
    val imapUsername: String,
    val strategyWeight: Int,
    val dailySendLimit: Int,
    val todaySentCount: Int,
    val lastSentAt: String?,
    val enabled: Boolean,
    val autoSendPaused: Boolean,
    val autoSendPausedReason: String?,
    val autoSendPausedAt: String?
)

private fun MailSenderAccount.toResponse(): MailSenderAccountResponse =
    MailSenderAccountResponse(
        id = id,
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
        imapHost = imapHost,
        imapPort = imapPort,
        imapUsername = imapUsername,
        strategyWeight = strategyWeight,
        dailySendLimit = dailySendLimit,
        todaySentCount = todaySentCount,
        lastSentAt = lastSentAt?.toString(),
        enabled = enabled,
        autoSendPaused = autoSendPaused,
        autoSendPausedReason = autoSendPausedReason,
        autoSendPausedAt = autoSendPausedAt?.toString()
    )
