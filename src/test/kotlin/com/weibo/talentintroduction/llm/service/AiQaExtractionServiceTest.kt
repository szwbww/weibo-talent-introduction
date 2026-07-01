package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.llm.domain.AiTrainingQa
import com.weibo.talentintroduction.llm.repository.AiTrainingQaRepository
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.service.QaMatchService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.client.ResourceAccessException

class AiQaExtractionServiceTest {
    private val llmProperties = LlmProperties(enabled = true, apiUrl = "http://llm")
    private val schedulingProperties = MailSchedulingProperties(aiQaExtractionMaxContacts = 5)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val aiReplyContextBuilder = AiReplyContextBuilder()
    private val aiTrainingQaRepository = Mockito.mock(AiTrainingQaRepository::class.java)
    private val qaMatchService = Mockito.mock(QaMatchService::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()

    @Suppress("UNCHECKED_CAST")
    private fun llmProvider(client: LlmDraftClient?): ObjectProvider<LlmDraftClient> {
        val mock = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>
        Mockito.`when`(mock.getIfAvailable()).thenReturn(client)
        return mock
    }

    private fun service(
        properties: LlmProperties = llmProperties,
        client: LlmDraftClient? = null
    ): AiQaExtractionService =
        AiQaExtractionService(
            llmProperties = properties,
            schedulingProperties = schedulingProperties,
            llmDraftClientProvider = llmProvider(client),
            mailRecordRepository = mailRecordRepository,
            aiReplyContextBuilder = aiReplyContextBuilder,
            aiTrainingQaRepository = aiTrainingQaRepository,
            objectMapper = objectMapper
        )

    private fun inboundRecord(contactId: Long = 10L) = MailRecord(
        id = 1L,
        expertContactId = contactId,
        direction = "INBOUND",
        mailType = "REPLY",
        subject = "Funding?",
        body = "What funding is available?",
        cleanedBody = "What funding is available?",
        messageId = null,
        inReplyTo = null,
        matchedQaRuleId = null,
        sendStatus = null,
        receivedAt = null,
        sentAt = null
    )

    @Test
    fun `extractBatch returns empty summary when llm disabled`() {
        val summary = service(properties = LlmProperties(enabled = false)).extractBatch(maxContacts = 3)

        assertEquals(0, summary.processed)
        assertEquals(0, summary.upserted)
        assertEquals(0, summary.skipped)
        Mockito.verifyNoInteractions(mailRecordRepository)
        Mockito.verifyNoInteractions(aiTrainingQaRepository)
        Mockito.verifyNoInteractions(qaMatchService)
    }

    @Test
    fun `extractBatch skips contact when llm throws`() {
        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail(null, 1))
            .thenReturn(listOf(10L))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(listOf(inboundRecord()))
        val failingClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                throw ResourceAccessException("timeout")
            }
        }

        val summary = service(client = failingClient).extractBatch(maxContacts = 1)

        assertEquals(1, summary.processed)
        assertEquals(0, summary.upserted)
        assertEquals(1, summary.skipped)
        Mockito.verify(aiTrainingQaRepository, Mockito.never()).save(Mockito.any())
        Mockito.verifyNoInteractions(qaMatchService)
    }

    @Test
    fun `extractBatch upserts AUTO_EXTRACTED row from llm json`() {
        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail(null, 1))
            .thenReturn(listOf(10L))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(listOf(inboundRecord()))
        Mockito.`when`(
            aiTrainingQaRepository.findBySourceAndSourceRef(AUTO_EXTRACTED, "contact:10")
        ).thenReturn(null)
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? =
                """[{"topic":"Funding","question":"What funding?","answer":"Up to 12M RMB","keywords":"funding,subsidy"}]"""
        }

        val summary = service(client = client).extractBatch(maxContacts = 1)

        assertEquals(1, summary.processed)
        assertEquals(1, summary.upserted)
        assertEquals(0, summary.skipped)
        val captor = ArgumentCaptor.forClass(AiTrainingQa::class.java)
        Mockito.verify(aiTrainingQaRepository).save(captor.capture())
        assertEquals(AUTO_EXTRACTED, captor.value.source)
        assertEquals("contact:10", captor.value.sourceRef)
        Mockito.verifyNoInteractions(qaMatchService)
    }

    @Test
    fun `upsertAuto updates existing contact row instead of inserting duplicate`() {
        val existing = AiTrainingQa(
            id = 99L,
            topic = "Old",
            question = "Old Q",
            answer = "Old A",
            keywords = "old",
            source = AUTO_EXTRACTED,
            sourceRef = "contact:10",
            enabled = true
        )
        Mockito.`when`(
            aiTrainingQaRepository.findBySourceAndSourceRef(AUTO_EXTRACTED, "contact:10")
        ).thenReturn(existing)

        service().upsertAuto(
            contactId = 10L,
            items = listOf(
                ExtractedQaItem(
                    topic = "Funding",
                    question = "What funding?",
                    answer = "Government funding up to 12M RMB",
                    keywords = "funding"
                )
            )
        )

        val captor = ArgumentCaptor.forClass(AiTrainingQa::class.java)
        Mockito.verify(aiTrainingQaRepository).save(captor.capture())
        assertEquals(99L, captor.value.id)
        assertEquals("contact:10", captor.value.sourceRef)
        assertTrue(captor.value.answer.contains("12M RMB"))
    }

    @Test
    fun `extractBatch only reads mail records`() {
        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail(null, 1))
            .thenReturn(emptyList())

        service(client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "[]"
        }).extractBatch(maxContacts = 1)

        Mockito.verify(mailRecordRepository).findExpertContactIdsWithInboundMail(null, 1)
        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `extractBatch processes contacts in repository return order`() {
        Mockito.`when`(mailRecordRepository.findExpertContactIdsWithInboundMail(null, 3))
            .thenReturn(listOf(30L, 20L, 10L))
        listOf(30L, 20L, 10L).forEach { contactId ->
            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
                .thenReturn(emptyList())
        }

        service(client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "[]"
        }).extractBatch(maxContacts = 3)

        val inOrder = Mockito.inOrder(mailRecordRepository)
        inOrder.verify(mailRecordRepository).findAllByExpertContactIdOrderByCreatedAtAsc(30L)
        inOrder.verify(mailRecordRepository).findAllByExpertContactIdOrderByCreatedAtAsc(20L)
        inOrder.verify(mailRecordRepository).findAllByExpertContactIdOrderByCreatedAtAsc(10L)
    }
}

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "migrationIt", matches = "true")
@org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
    replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE
)
@org.springframework.test.context.TestPropertySource(
    properties = ["spring.flyway.placeholder-replacement=false"]
)
class MailRecordRepositoryInboundContactSelectionIT {
    companion object {
        private class KotlinMySqlContainer(image: String) :
            org.testcontainers.containers.MySQLContainer<KotlinMySqlContainer>(image)

        private val mysql = KotlinMySqlContainer("mysql:8.0.36")
            .withDatabaseName("talent_introduction")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @org.junit.jupiter.api.BeforeAll
        fun startMysql() {
            check(org.testcontainers.DockerClientFactory.instance().isDockerAvailable) {
                "Docker is required for MailRecordRepository inbound contact selection tests"
            }
            mysql.start()
        }

        @JvmStatic
        @org.junit.jupiter.api.AfterAll
        fun stopMysql() {
            if (mysql.isRunning) mysql.stop()
        }

        @JvmStatic
        @org.springframework.test.context.DynamicPropertySource
        fun registerDynamicProperties(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var mailRecordRepository: MailRecordRepository

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate

    @org.junit.jupiter.api.BeforeEach
    fun cleanTables() {
        jdbcTemplate.execute("DELETE FROM mail_record")
        jdbcTemplate.execute("DELETE FROM expert_contact")
        jdbcTemplate.execute("DELETE FROM campaign")
        jdbcTemplate.execute("DELETE FROM mail_sender_account")
        seedContacts()
    }

    @Test
    fun `findExpertContactIdsWithInboundMail deduplicates repeated inbound for same contact`() {
        insertInbound(contactId = 1L, messageId = "in-1a")
        insertInbound(contactId = 1L, messageId = "in-1b")
        insertInbound(contactId = 2L, messageId = "in-2")

        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(null, 10)

        assertEquals(listOf(2L, 1L), contactIds)
    }

    @Test
    fun `findExpertContactIdsWithInboundMail orders contacts by latest inbound mail id desc`() {
        insertInbound(contactId = 1L, messageId = "in-old")
        insertInbound(contactId = 2L, messageId = "in-newest")
        insertInbound(contactId = 3L, messageId = "in-middle")
        jdbcTemplate.update(
            """
            UPDATE mail_record
               SET id = CASE message_id
                   WHEN 'in-old' THEN 100
                   WHEN 'in-middle' THEN 200
                   WHEN 'in-newest' THEN 300
               END
             WHERE message_id IN ('in-old', 'in-middle', 'in-newest')
            """.trimIndent()
        )

        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(null, 10)

        assertEquals(listOf(2L, 3L, 1L), contactIds)
    }

    private fun seedContacts() {
        jdbcTemplate.update(
            """
            INSERT INTO mail_sender_account
                (id, account_code, sender_email, sender_name, smtp_host, smtp_port,
                 smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
            VALUES
                (1, 'sender', 'sender@example.com', 'Sender', 'smtp.example.com', 465,
                 'sender@example.com', 'pwd', 'imap.example.com', 993, 'sender@example.com', 'pwd')
            """
        )
        jdbcTemplate.update(
            """
            INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id)
            VALUES (1, 'MANUAL_OUTREACH', 'Manual Outreach', 1)
            """
        )
        jdbcTemplate.update(
            """
            INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name, current_status)
            VALUES
                (1, 1, '0000-0001', 'one@example.com', 'One', 'NEW'),
                (2, 1, '0000-0002', 'two@example.com', 'Two', 'NEW'),
                (3, 1, '0000-0003', 'three@example.com', 'Three', 'NEW')
            """
        )
    }

    private fun insertInbound(contactId: Long, messageId: String) {
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id, subject, body, send_status)
            VALUES (?, 'INBOUND', 'REPLY', 'sender', ?, 'subject', 'body', NULL)
            """.trimIndent(),
            contactId,
            messageId
        )
    }
}
