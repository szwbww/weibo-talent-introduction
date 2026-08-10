package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class SenderAccountBindingService(
    private val mailSenderAccountService: MailSenderAccountService,
    private val warmup: SenderWarmupService,
    private val expertContactRepository: ExpertContactRepository
) {
    /** 建行时固化绑定：返回可直接放进 ExpertContact(...) 构造的二元组（I-2/I-5）。 */
    fun bindingFieldsFor(accountCode: String, now: LocalDateTime): Pair<String, LocalDateTime> {
        require(accountCode.isNotBlank()) { "accountCode is required for binding" }
        require(accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE) {
            "SIMULATOR_NOOP must never be bound to an expert contact"
        }
        return accountCode to now
    }

    /** 绑定的唯一读取入口（I-6/I-7）。 */
    fun resolveForSend(contact: ExpertContact, manual: Boolean): MailSenderAccount {
        val contactId = contact.id ?: error("Expert contact id is required")
        val code = contact.boundSenderAccountCode?.takeIf { it.isNotBlank() }
            ?: throw SenderAccountNotBoundException(contactId)
        val account = mailSenderAccountService.getAccount(code)
        requireAvailable(contactId, account, manual)
        return account
    }

    /** 无绑定兜底的补写：调用方选号成功后回填绑定，只写两列（I-4）。 */
    fun bindIfAbsent(contactId: Long, accountCode: String, now: LocalDateTime) {
        val (code, at) = bindingFieldsFor(accountCode, now)
        expertContactRepository.updateBindingById(contactId, code, at)
    }

    private fun requireAvailable(contactId: Long, account: MailSenderAccount, manual: Boolean) {
        if (account.accountCode == MailSenderAccountService.SIMULATOR_ACCOUNT_CODE) {
            throw BoundSenderAccountUnavailableException(contactId, account.accountCode, "SIMULATOR")
        }
        if (!account.enabled) {
            throw BoundSenderAccountUnavailableException(contactId, account.accountCode, "DISABLED")
        }
        if (manual) return   // 人工路径到此为止：不判暂停、不判额度（I-7）
        if (account.autoSendPaused) {
            throw BoundSenderAccountUnavailableException(contactId, account.accountCode, "AUTO_SEND_PAUSED")
        }
        if (account.todaySentCount >= warmup.effectiveDailyLimit(account)) {
            throw BoundSenderAccountUnavailableException(contactId, account.accountCode, "DAILY_LIMIT_REACHED")
        }
    }
}

class SenderAccountNotBoundException(val contactId: Long) :
    IllegalStateException("专家 $contactId 尚未绑定发件账号")

class BoundSenderAccountUnavailableException(
    val contactId: Long,
    val accountCode: String,
    val reason: String
) : IllegalStateException("绑定发件账号 $accountCode 不可用（$reason），专家 contactId=$contactId")
