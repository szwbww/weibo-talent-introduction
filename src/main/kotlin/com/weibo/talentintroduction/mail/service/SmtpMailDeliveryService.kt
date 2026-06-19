package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.mail.MailException
import org.springframework.stereotype.Service
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.mail.SendFailedException

@Service
class SmtpMailDeliveryService(
    private val smtpSenderFactory: SmtpSenderFactory
) : MailDeliveryService {
    override fun send(account: MailSenderAccount, mail: ComposedMail): DeliveredMail {
        val sender = smtpSenderFactory.getSender(account)

        val mailSession = sender.session
        val message = if (mail.messageId != null) {
            object : javax.mail.internet.MimeMessage(mailSession) {
                override fun updateMessageID() {
                    setHeader("Message-ID", mail.messageId)
                }
            }
        } else {
            sender.createMimeMessage()
        }

        message.setFrom(account.senderEmail)
        message.setRecipients(javax.mail.Message.RecipientType.TO, mail.to)
        message.subject = mail.subject
        if (mail.html) {
            message.setContent(mail.body, "text/html; charset=UTF-8")
        } else {
            message.setText(mail.body, Charsets.UTF_8.name())
        }

        return try {
            sender.send(message)
            DeliveredMail(
                messageId = message.messageID ?: mail.messageId,
                status = "SENT"
            )
        } catch (e: SendFailedException) {
            SmtpErrorClassifier.fromSendFailedException(e, mail.messageId)
        } catch (e: AuthenticationFailedException) {
            SmtpErrorClassifier.fromAuthenticationFailedException(e, mail.messageId)
        } catch (e: MessagingException) {
            SmtpErrorClassifier.fromMessagingException(e, mail.messageId)
        } catch (e: MailException) {
            SmtpErrorClassifier.fromMailException(e, mail.messageId)
        }
    }
}
