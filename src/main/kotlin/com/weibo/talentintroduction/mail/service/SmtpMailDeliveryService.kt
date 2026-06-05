package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Service
import java.util.Properties

@Service
class SmtpMailDeliveryService : MailDeliveryService {
    override fun send(account: MailSenderAccount, mail: ComposedMail): DeliveredMail {
        val sender = JavaMailSenderImpl().apply {
            host = account.smtpHost
            port = account.smtpPort
            username = account.smtpUsername
            password = account.smtpPassword
            javaMailProperties = smtpProperties(account.smtpPort)
        }

        val message = sender.createMimeMessage()
        message.setFrom(account.senderEmail)
        message.setRecipients(javax.mail.Message.RecipientType.TO, mail.to)
        message.subject = mail.subject
        if (mail.html) {
            message.setContent(mail.body, "text/html; charset=UTF-8")
        } else {
            message.setText(mail.body, Charsets.UTF_8.name())
        }

        sender.send(message)

        return DeliveredMail(
            messageId = message.messageID,
            status = "SENT"
        )
    }

    private fun smtpProperties(port: Int): Properties =
        Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.auth.mechanisms", "LOGIN")
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
            if (port == 465) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
            }
        }
}
