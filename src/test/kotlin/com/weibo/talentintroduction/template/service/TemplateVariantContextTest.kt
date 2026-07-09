package com.weibo.talentintroduction.template.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.MailPlaceholderService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.mail.service.UnsubscribeTokenService
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier

class TemplateVariantContextTest {
    private val contextRunner = ApplicationContextRunner()
        .withBean(MailComposeTemplateRepository::class.java, Supplier {
            Mockito.mock(MailComposeTemplateRepository::class.java)
        })
        .withBean(MailComposeTemplateBlockRepository::class.java, Supplier {
            Mockito.mock(MailComposeTemplateBlockRepository::class.java)
        })
        .withBean(QaRuleRepository::class.java, Supplier {
            Mockito.mock(QaRuleRepository::class.java)
        })
        .withBean(ReplySnippetRepository::class.java, Supplier {
            Mockito.mock(ReplySnippetRepository::class.java)
        })
        .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
        .withBean(ExpertContactRepository::class.java, Supplier {
            Mockito.mock(ExpertContactRepository::class.java)
        })
        .withBean(MailSenderAccountService::class.java, Supplier {
            Mockito.mock(MailSenderAccountService::class.java)
        })
        .withBean(ContentVariantRepository::class.java, Supplier {
            Mockito.mock(ContentVariantRepository::class.java)
        })
        .withBean(ExpertSearchService::class.java, Supplier {
            Mockito.mock(ExpertSearchService::class.java)
        })
        .withBean(UnsubscribeTokenService::class.java, Supplier {
            Mockito.mock(UnsubscribeTokenService::class.java)
        })
        .withUserConfiguration(
            MailComposeTemplateService::class.java,
            ContentVariantService::class.java,
            MailVariableService::class.java,
            MailPlaceholderService::class.java
        )

    @Test
    fun `template variant beans start without circular dependency`() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
        }
    }
}
