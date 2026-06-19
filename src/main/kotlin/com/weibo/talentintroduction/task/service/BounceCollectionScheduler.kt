package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.mail.service.AutoMailReplyService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.stereotype.Component

@Component
@Configuration
class BounceCollectionScheduler(
    private val mailSenderAccountService: MailSenderAccountService,
    private val autoMailReplyService: AutoMailReplyService,
    private val mailSchedulingProperties: MailSchedulingProperties,
    private val taskExecutionService: TaskExecutionService
) : SchedulingConfigurer {

    private val log = LoggerFactory.getLogger(BounceCollectionScheduler::class.java)

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addCronTask(
            Runnable { runCollection() },
            "0 0 */2 * * ?"
        )
    }

    fun runCollection() {
        try {
            taskExecutionService.runAndRecord("BOUNCE_COLLECTION", "SCHEDULED", "bounce-collection") {
                val accounts = mailSenderAccountService.listAutoReceiveAccounts()
                val maxMessages = mailSchedulingProperties.autoReplyMaxMessagesPerAccount
                for (account in accounts) {
                    try {
                        autoMailReplyService.receiveAndAutoReply(account.accountCode, maxMessages)
                    } catch (e: Exception) {
                        log.error("Bounce collection failed for account {}", account.accountCode, e)
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Bounce collection task failed", e)
        }
    }
}
