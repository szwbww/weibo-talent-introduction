package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory

interface MailDeliveryService {
    fun send(account: MailSenderAccount, mail: ComposedMail): DeliveredMail
}

data class DeliveredMail(
    val messageId: String?,
    val status: String,
    val errorCategory: SmtpErrorCategory = SmtpErrorCategory.SUCCESS,
    val smtpResponseCode: Int? = null,
    val errorDetail: String? = null
)
