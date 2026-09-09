package com.weibo.talentintroduction.mail.service

import javax.mail.internet.MimeUtility

/**
 * MIME 主题解码的唯一纯函数（I-4/R3）。
 *
 * - null / 空串 / 不含 encoded-word 的普通文本原样返回（普通下划线绝不被误改）；
 * - 对合法 folding（CRLF + LWSP 续行）先 unfold 一次，再 [MimeUtility.decodeText] 单次解码；
 *   该顺序与 JavaMail `MimeMessage.getSubject()` 的参考实现一致。
 * - 不做 HTML unescape，不把解码结果当 HTML，不循环解码。
 * - 未知 charset / 损坏 encoded-word / 其它任何异常 → 回退原字符串；绝不向收信/会话读取抛出。
 *
 * 接收路径（ImapMailReceiveService 头读取）与读取路径（会话 latestMessage/timeline subject，
 * 含历史 OUTBOUND 行）复用同一函数；旧库 subject 只做读兼容，不 UPDATE。
 */
object MailSubjectDecoder {

    fun decode(subject: String?): String? {
        if (subject.isNullOrEmpty()) return subject
        return try {
            val unfolded = MimeUtility.unfold(subject)
            MimeUtility.decodeText(unfolded)
        } catch (_: Exception) {
            subject
        }
    }
}
