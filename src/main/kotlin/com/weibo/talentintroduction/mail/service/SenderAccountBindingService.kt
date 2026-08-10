package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class SenderAccountBindingService(
    private val mailSenderAccountService: MailSenderAccountService,
    private val warmup: SenderWarmupService,
    private val expertContactRepository: ExpertContactRepository,
    private val operatorActionLogService: OperatorActionLogService
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
    fun resolveForSend(
        contact: ExpertContact,
        manual: Boolean,
        ignoreWarmup: Boolean = false
    ): MailSenderAccount {
        val contactId = contact.id ?: error("Expert contact id is required")
        val code = contact.boundSenderAccountCode?.takeIf { it.isNotBlank() }
            ?: throw SenderAccountNotBoundException(contactId)
        val account = mailSenderAccountService.getAccount(code)
        requireAvailable(contactId, account, manual, ignoreWarmup)
        return account
    }

    /** 无绑定兜底的补写：调用方选号成功后回填绑定，只写两列（I-4）。 */
    fun bindIfAbsent(contactId: Long, accountCode: String, now: LocalDateTime) {
        val (code, at) = bindingFieldsFor(accountCode, now)
        expertContactRepository.updateBindingById(contactId, code, at)
    }

    /** 单专家主动换绑：置变更标记 + 逐专家审计（I-1/I-2/I-4）。 */
    @Transactional
    fun rebind(contactId: Long, command: RebindCommand): ExpertContact {
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { NoSuchElementException("Expert contact not found: $contactId") }
        val target = requireEnabledTarget(command.senderAccountCode)          // I-3
        val old = contact.boundSenderAccountCode
        if (old == target.accountCode) return contact                         // I-5

        val now = LocalDateTime.now()
        expertContactRepository.rebindSenderAccountById(contactId, target.accountCode, now)  // I-2

        operatorActionLogService.record(                                      // I-4
            targetType = "EXPERT_CONTACT",
            targetId = contactId,
            actionType = OperatorActionType.CHANGE_SENDER_ACCOUNT,
            expertContactId = contactId,
            before = mapOf("boundSenderAccountCode" to old),
            after = mapOf("boundSenderAccountCode" to target.accountCode),
            operatorName = command.operatorName,
            note = boundedNote(command.note, activeThreadHint(contact, old))  // I-4/I-7
        )
        return expertContactRepository.findById(contactId).orElseThrow()
    }

    /** 按源账号批量迁移：不打标记、逐专家一条审计（I-1/I-4/I-6）。 */
    @Transactional
    fun migrateAccount(command: MigrateCommand): MigrateResult {
        val target = requireEnabledTarget(command.toAccountCode)              // I-3
        require(command.fromAccountCode.isNotBlank()) { "fromAccountCode is required" }
        require(command.fromAccountCode != target.accountCode) {              // I-5
            "源账号与目标账号相同，无需迁移"
        }
        val affected = expertContactRepository
            .findAllByBoundSenderAccountCode(command.fromAccountCode)         // I-6
        if (affected.isEmpty()) return MigrateResult(0, command.fromAccountCode, target.accountCode)

        val now = LocalDateTime.now()
        val updated = expertContactRepository.migrateBindingByAccount(
            command.fromAccountCode, target.accountCode, now
        )                                                                     // I-2/I-6

        affected.forEach { c ->                                               // I-4：逐专家一条
            operatorActionLogService.record(
                targetType = "EXPERT_CONTACT",
                targetId = c.id!!,
                actionType = OperatorActionType.MIGRATE_SENDER_ACCOUNT,
                expertContactId = c.id,
                before = mapOf("boundSenderAccountCode" to command.fromAccountCode),
                after = mapOf("boundSenderAccountCode" to target.accountCode),
                operatorName = command.operatorName,
                note = boundedNote(command.reason, null)
            )
        }
        return MigrateResult(updated, command.fromAccountCode, target.accountCode)
    }

    /** 清除变更标记：只清标记两列，禁碰绑定列（I-1/I-2）。 */
    @Transactional
    fun clearChangeMark(contactId: Long, operatorName: String?, note: String?): ExpertContact {
        expertContactRepository.clearSenderChangeMarkById(contactId)
        operatorActionLogService.record(
            targetType = "EXPERT_CONTACT",
            targetId = contactId,
            actionType = OperatorActionType.CLEAR_SENDER_CHANGE_MARK,
            expertContactId = contactId,
            before = mapOf("senderAccountChanged" to true),
            after = mapOf("senderAccountChanged" to false),
            operatorName = operatorName,
            note = boundedNote(note, null)
        )
        return expertContactRepository.findById(contactId).orElseThrow()
    }

    private fun requireEnabledTarget(code: String): MailSenderAccount {       // I-3
        require(code.isNotBlank()) { "senderAccountCode is required" }
        require(code != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE) {
            "模拟器账号不可作为绑定目标"
        }
        val account = mailSenderAccountService.getAccount(code)
        require(account.enabled) { "目标发件账号已禁用，不可绑定：$code" }
        return account
    }

    /** I-7：只提示，不阻断、不改数据。 */
    private fun activeThreadHint(contact: ExpertContact, oldCode: String?): String? {
        if (oldCode == null) return null
        // 未发过信（NEW）或已交人工（MANUAL_HANDOFF）视为无进行中线程；其余状态视为有线程。
        val terminal = setOf("NEW", "MANUAL_HANDOFF")
        if (contact.currentStatus in terminal) return null
        return "存在进行中的会话（${contact.currentStatus}），该专家的回复仍由 $oldCode 处理"
    }

    /** I-4：note 有界。 */
    private fun boundedNote(note: String?, hint: String?): String? {
        val merged = listOfNotNull(note?.trim()?.takeIf { it.isNotEmpty() }, hint)
            .joinToString(" | ")
            .takeIf { it.isNotEmpty() } ?: return null
        return if (merged.length <= NOTE_MAX) merged
               else merged.take(NOTE_MAX) + "…(truncated)"
    }

    private fun requireAvailable(
        contactId: Long,
        account: MailSenderAccount,
        manual: Boolean,
        ignoreWarmup: Boolean
    ) {
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
        if (account.todaySentCount >= warmup.effectiveDailyLimit(account, ignoreWarmup = ignoreWarmup)) {
            throw BoundSenderAccountUnavailableException(contactId, account.accountCode, "DAILY_LIMIT_REACHED")
        }
    }

    companion object {
        const val NOTE_MAX = 500
    }
}

data class RebindCommand(val senderAccountCode: String, val operatorName: String?, val note: String?)

data class MigrateCommand(
    val fromAccountCode: String,
    val toAccountCode: String,
    val operatorName: String?,
    val reason: String?
)

data class MigrateResult(val migrated: Int, val fromAccountCode: String, val toAccountCode: String)

class SenderAccountNotBoundException(val contactId: Long) :
    IllegalStateException("专家 $contactId 尚未绑定发件账号")

class BoundSenderAccountUnavailableException(
    val contactId: Long,
    val accountCode: String,
    val reason: String
) : IllegalStateException("绑定发件账号 $accountCode 不可用（$reason），专家 contactId=$contactId")
