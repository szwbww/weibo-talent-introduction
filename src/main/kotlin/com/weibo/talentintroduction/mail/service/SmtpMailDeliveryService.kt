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
    private val mailContentService: MailContentService,
    private val emailSuppressionService: EmailSuppressionService
) : MailDeliveryService {
    override fun send(account: MailSenderAccount, mail: ComposedMail): DeliveredMail {
        // I-1: 兜底 fail-closed 拦截。必须位于接触任何 SMTP 资源（getSender）之前；
        // 命中且未显式 override 时抛异常，绝不返回 DeliveredMail（I-2）。
        if (!mail.allowSuppressedRecipient && emailSuppressionService.isSuppressed(mail.to)) {
            throw RecipientSuppressedException(mail.to)
        }

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

        // J-2: 仅在 senderDisplayName trim 后非空时使用显示名；非 ASCII 由三参构造器
        // 完成 RFC 2047 编码，禁止手工拼接。为空时逐字退回裸地址形态。
        val displayName = account.senderDisplayName?.trim()?.takeIf { it.isNotEmpty() }
        if (displayName != null) {
            message.setFrom(javax.mail.internet.InternetAddress(account.senderEmail, displayName, "UTF-8"))
        } else {
            message.setFrom(account.senderEmail)
        }
        message.setRecipients(javax.mail.Message.RecipientType.TO, mail.to)
        message.subject = mail.subject
        mail.inReplyTo?.takeIf { it.isNotBlank() }?.let { message.setHeader("In-Reply-To", it) }
        mail.references?.takeIf { it.isNotBlank() }?.let { message.setHeader("References", it) }
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
            message.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click")
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
