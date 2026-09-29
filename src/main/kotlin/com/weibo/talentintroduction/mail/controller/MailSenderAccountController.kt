package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.service.BounceRateMonitorService
import com.weibo.talentintroduction.mail.service.MailAccountConnectivityResult
import com.weibo.talentintroduction.mail.service.MailAccountConnectivityService
import com.weibo.talentintroduction.mail.service.MailSenderAccountCreateCommand
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailSenderAccountUpdateCommand
import com.weibo.talentintroduction.mail.service.SenderAccountSelfCheckService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.HttpStatus

@RestController
@RequestMapping("/api/mail/sender-accounts")
class MailSenderAccountController(
    private val service: MailSenderAccountService,
    private val connectivityService: MailAccountConnectivityService,
    private val selfCheckService: SenderAccountSelfCheckService,
    private val bounceRateMonitorService: BounceRateMonitorService
) {
    @GetMapping
    fun listAccounts(): List<MailSenderAccountResponse> {
        val counts = service.bindingCountsByAccount()
        return service.listAccounts().map { toResponse(it, counts[it.accountCode] ?: 0L) }
    }

    @GetMapping("/{accountCode}")
    fun getAccount(@PathVariable accountCode: String): MailSenderAccountResponse {
        val account = service.getAccount(accountCode)
        return toResponse(account, service.bindingCountsByAccount()[accountCode] ?: 0L)
    }

    @PostMapping
    fun createAccount(@RequestBody request: MailSenderAccountCreateRequest): MailSenderAccountResponse {
        val account = service.createAccount(request.toCommand())
        return toResponse(account, service.bindingCountsByAccount()[account.accountCode] ?: 0L)
    }

    @PutMapping("/{accountCode}")
    fun updateAccount(
        @PathVariable accountCode: String,
        @RequestBody request: MailSenderAccountUpdateRequest
    ): MailSenderAccountResponse {
        val account = service.updateAccount(accountCode, request.toCommand())
        return toResponse(account, service.bindingCountsByAccount()[accountCode] ?: 0L)
    }

    @PostMapping("/{accountCode}/enable")
    fun enableAccount(@PathVariable accountCode: String): MailSenderAccountResponse {
        val account = service.setEnabled(accountCode, true)
        return toResponse(account, service.bindingCountsByAccount()[accountCode] ?: 0L)
    }

    @PostMapping("/{accountCode}/disable")
    fun disableAccount(@PathVariable accountCode: String): MailSenderAccountResponse {
        val account = service.setEnabled(accountCode, false)
        return toResponse(account, service.bindingCountsByAccount()[accountCode] ?: 0L)
    }

    @PostMapping("/{accountCode}/reset-today-sent-count")
    fun resetTodaySentCount(@PathVariable accountCode: String): MailSenderAccountResponse {
        val account = service.resetTodaySentCount(accountCode)
        return toResponse(account, service.bindingCountsByAccount()[accountCode] ?: 0L)
    }

    @PostMapping("/{accountCode}/test-connectivity")
    fun testConnectivity(@PathVariable accountCode: String): MailAccountConnectivityResult =
        connectivityService.testAccount(accountCode)

    @PostMapping("/{accountCode}/resume-auto-send")
    fun resumeAutoSend(@PathVariable accountCode: String): MailSenderAccountResponse {
        service.resumeAutoSend(accountCode)
        val account = service.getAccount(accountCode)
        return toResponse(account, service.bindingCountsByAccount()[accountCode] ?: 0L)
    }

    @PostMapping("/{accountCode}/self-check")
    fun selfCheck(@PathVariable accountCode: String) =
        selfCheckService.checkSendable(service.getAccount(accountCode))

    @DeleteMapping("/{accountCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(@PathVariable accountCode: String) {
        service.deleteAccount(accountCode)
    }

    // I-1：每个账号的响应只取一次统计快照，数字与比率来自同一次计算结果，不重复查询。
    private fun toResponse(account: MailSenderAccount, boundExpertCount: Long = 0): MailSenderAccountResponse {
        val bounceStats = bounceRateMonitorService.getStats(account.accountCode)
        return MailSenderAccountResponse(
            id = account.id,
            accountCode = account.accountCode,
            senderEmail = account.senderEmail,
            senderName = account.senderName,
            senderTitle = account.senderTitle,
            senderDisplayName = account.senderDisplayName,
            teamName = account.teamName,
            countryName = account.countryName,
            inboundMailboxCode = account.inboundMailboxCode,
            smtpHost = account.smtpHost,
            smtpPort = account.smtpPort,
            smtpUsername = account.smtpUsername,
            imapHost = account.imapHost,
            imapPort = account.imapPort,
            imapUsername = account.imapUsername,
            strategyWeight = account.strategyWeight,
            dailySendLimit = account.dailySendLimit,
            effectiveDailyLimit = service.effectiveDailyLimitFor(account),
            todaySentCount = account.todaySentCount,
            lastSentAt = account.lastSentAt?.toString(),
            enabled = account.enabled,
            boundExpertCount = boundExpertCount,
            autoSendPaused = account.autoSendPaused,
            autoSendPausedReason = account.autoSendPausedReason,
            autoSendPausedAt = account.autoSendPausedAt?.toString(),
            hardBounceRateHigh = bounceStats.high,
            hardBounceCount = bounceStats.hardBounceCount,
            sentCount = bounceStats.sentCount,
            hardBounceRate = bounceStats.rate,
            hardBounceSampleSufficient = bounceStats.sampleSufficient,
            hardBounceWindowDays = bounceStats.windowDays,
            warmupEnabled = account.warmupEnabled,
            warmupStartedAt = account.warmupStartedAt?.toString(),
            warmupStepsJson = account.warmupStepsJson
        )
    }
}

data class MailSenderAccountCreateRequest(
    val accountCode: String,
    val senderEmail: String,
    val senderName: String,
    val senderTitle: String?,
    val senderDisplayName: String?,
    val teamName: String?,
    val countryName: String?,
    /** 共享收件箱主账号代码（S-1 表单字段名，I-1）：null/空 = 独立收件箱（本账号）。 */
    val inboundMailboxCode: String? = null,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpUsername: String,
    val smtpPassword: String,
    val imapHost: String = "",
    val imapPort: Int = 993,
    val imapUsername: String = "",
    val imapPassword: String = "",
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
            inboundMailboxCode = inboundMailboxCode,
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
    /** 共享收件箱主账号代码（S-1 表单字段名，I-1）：null/空 = 独立收件箱（本账号）。 */
    val inboundMailboxCode: String? = null,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpUsername: String,
    val smtpPassword: String? = null,
    val imapHost: String? = null,
    val imapPort: Int? = null,
    val imapUsername: String? = null,
    val imapPassword: String? = null,
    val strategyWeight: Int,
    val dailySendLimit: Int,
    val todaySentCount: Int,
    val enabled: Boolean,
    val warmupEnabled: Boolean? = null,
    val warmupStartedAt: String? = null,
    val warmupStepsJson: String? = null
) {
    fun toCommand(): MailSenderAccountUpdateCommand =
        MailSenderAccountUpdateCommand(
            senderEmail = senderEmail,
            senderName = senderName,
            senderTitle = senderTitle,
            senderDisplayName = senderDisplayName,
            teamName = teamName,
            countryName = countryName,
            inboundMailboxCode = inboundMailboxCode,
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
            enabled = enabled,
            warmupEnabled = warmupEnabled,
            warmupStartedAt = warmupStartedAt,
            warmupStepsJson = warmupStepsJson
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
    /** 共享收件箱主账号代码（I-1）：null = 独立收件箱（本账号）。 */
    val inboundMailboxCode: String? = null,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpUsername: String,
    val imapHost: String,
    val imapPort: Int,
    val imapUsername: String,
    val strategyWeight: Int,
    val dailySendLimit: Int,
    val effectiveDailyLimit: Int,
    val todaySentCount: Int,
    val lastSentAt: String?,
    val enabled: Boolean,
    val boundExpertCount: Long = 0,
    val autoSendPaused: Boolean,
    val autoSendPausedReason: String?,
    val autoSendPausedAt: String?,
    val hardBounceRateHigh: Boolean,
    /** I-4：窗口内 HARD 退信事件数（含无法归因到专家的退信）。 */
    val hardBounceCount: Long,
    /** I-4：窗口内成功发信数（OUTBOUND + SENT + sent_at）。 */
    val sentCount: Long,
    /** I-4：0～非限定上界的小数比率；null 表示样本不足，禁止当作 0。 */
    val hardBounceRate: Double?,
    /** I-4：样本是否达到 MIN_SAMPLE_SIZE。 */
    val hardBounceSampleSufficient: Boolean,
    /** I-4：统计窗口天数。 */
    val hardBounceWindowDays: Int,
    val warmupEnabled: Boolean?,
    val warmupStartedAt: String?,
    val warmupStepsJson: String?
)
