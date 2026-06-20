package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.mail.MailException
import org.springframework.stereotype.Service
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.mail.SendFailedException

@Service
class SmtpMailDeliveryService(
    private val smtpSenderFactory: SmtpSenderFactory,
    private val unsubscribeTokenService: UnsubscribeTokenService,
    private val mailContentService: MailContentService
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
            val plain = mail.text?.takeIf { it.isNotBlank() }
                ?: mailContentService.htmlToPlainText(mail.body)
            val multipart = javax.mail.internet.MimeMultipart("alternative")
            multipart.addBodyPart(javax.mail.internet.MimeBodyPart().apply {
                setText(plain, Charsets.UTF_8.name())
            })
            multipart.addBodyPart(javax.mail.internet.MimeBodyPart().apply {
                setContent(mail.body, "text/html; charset=UTF-8")
            })
            message.setContent(multipart)
        } else {
            message.setText(mail.body, Charsets.UTF_8.name())
        }

        if (unsubscribeTokenService.enabled()) {
            val httpsUrl = unsubscribeTokenService.unsubscribeUrl(mail.to)
            val mailto = "mailto:${account.senderEmail}?subject=unsubscribe"
            message.addHeader("List-Unsubscribe", "<$httpsUrl>, <$mailto>")
            message.addHeader("List-Unsubscribe-Post", "List=One-Click")
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
