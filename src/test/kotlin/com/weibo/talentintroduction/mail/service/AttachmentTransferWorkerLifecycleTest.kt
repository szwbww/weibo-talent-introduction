package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.repository.MailAttachmentTransferRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

class AttachmentTransferWorkerLifecycleTest {
    @Test
    fun `application ready starts worker and context close stops it`() {
        val context = AnnotationConfigApplicationContext(WorkerLifecycleTestConfig::class.java)
        val worker = context.getBean(AttachmentTransferWorker::class.java)
        try {
            assertFalse(worker.isRunning())

            context.publishEvent(ApplicationReadyEvent(SpringApplication(), emptyArray(), context))

            assertTrue(worker.isRunning())
        } finally {
            context.close()
        }
        assertFalse(worker.isRunning())
    }
}

@Configuration
class WorkerLifecycleTestConfig {
    @Bean
    fun attachmentTransferWorker(): AttachmentTransferWorker = AttachmentTransferWorker(
        transferRepository = mock(MailAttachmentTransferRepository::class.java),
        senderAccountRepository = mock(MailSenderAccountRepository::class.java),
        fetcher = ImapAttachmentContentFetcher(MailAttachmentStorageProperties(basePath = "/tmp")),
        properties = MailAttachmentStorageProperties(basePath = "/tmp"),
        transactionTemplate = mock(TransactionTemplate::class.java),
        jdbcTemplate = mock(JdbcTemplate::class.java),
        purposeConsumers = emptyList()
    )
}
