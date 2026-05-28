package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount

interface MailDeliveryService {
    fun send(account: MailSenderAccount, mail: ComposedMail): DeliveredMail
}

data class DeliveredMail(
    val messageId: String?,
    val status: String
)
