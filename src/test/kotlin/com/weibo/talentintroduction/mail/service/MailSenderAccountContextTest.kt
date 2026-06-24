package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.CampaignRepository
import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier

class MailSenderAccountContextTest {
    private val contextRunner = ApplicationContextRunner()
        .withBean(MailSenderAccountRepository::class.java, Supplier {
            Mockito.mock(MailSenderAccountRepository::class.java)
        })
        .withBean(SenderAccountSelfCheckService::class.java, Supplier {
            Mockito.mock(SenderAccountSelfCheckService::class.java)
        })
        .withBean(SmtpSenderFactory::class.java, Supplier {
            Mockito.mock(SmtpSenderFactory::class.java)
        })
        .withBean(CampaignRepository::class.java, Supplier {
            Mockito.mock(CampaignRepository::class.java)
        })
        .withBean(WarmupProperties::class.java, Supplier {
            WarmupProperties(enabled = false)
        })
        .withBean(ObjectMapper::class.java, Supplier {
            ObjectMapper()
        })
        .withUserConfiguration(
            SenderWarmupService::class.java,
            MailSenderAccountService::class.java,
            MailAccountConnectivityService::class.java
        )

    @Test
    fun `mail sender account beans start without circular dependency`() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
        }
    }
}
