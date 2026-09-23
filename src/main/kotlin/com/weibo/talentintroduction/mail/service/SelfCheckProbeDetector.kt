package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.stereotype.Service

/**
 * I-3：内部 `[self-check]` 探针识别。探针由 [DefaultSelfCheckProbeSender] 以账号自己的
 * From/To 发出，主题固定为 `[self-check] {accountCode} {System.currentTimeMillis()}`。
 *
 * 共享物理收件箱下 owner 与别名互收对方探针，故识别口径是**当前物理组的全部成员**：一封
 * 邮件成立当且仅当它同时满足某个成员的 `sender_email`（地址大小写无关）与该成员账号代码
 * 对应的完整生成主题。只看主题前缀、只看 From、跨组、错账号代码、`Re:`、非十进制尾巴
 * 一律不成立——外部真实邮件绝不因可伪造的主题被吞。
 */
@Service
class SelfCheckProbeDetector {

    fun isSelfCheckProbe(
        from: String?,
        subject: String?,
        groupMembers: List<MailSenderAccount>
    ): Boolean {
        if (from.isNullOrBlank()) return false
        val codeInSubject = probeAccountCodeOf(subject ?: return false) ?: return false
        val normalizedFrom = from.trim().lowercase()
        return groupMembers.any { member ->
            member.accountCode == codeInSubject && normalizedFrom == member.senderEmail.trim().lowercase()
        }
    }

    /**
     * 完整生成主题里的账号代码；不是生成格式时返回 null。标签内空格容错沿用旧检查器
     * （`[ self - check ]`），但账号代码必须是一个完整令牌（前后不粘连其它字符），
     * 尾巴必须是非空十进制时间戳。
     */
    private fun probeAccountCodeOf(subject: String): String? =
        PROBE_SUBJECT.find(subject)?.groupValues?.get(1)

    private companion object {
        /** 与 [DefaultSelfCheckProbeSender] 的主题生成格式同源：`[self-check] {accountCode} {十进制时间戳}`。 */
        val PROBE_SUBJECT = Regex(
            """^\[\s*self\s*-\s*check\s*\]\s*(\S+)\s+(\d+)\s*$""",
            RegexOption.IGNORE_CASE
        )
    }
}
