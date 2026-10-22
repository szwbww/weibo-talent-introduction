package com.weibo.talentintroduction.campaign.repository

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.time.LocalDateTime

@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
class ExpertContactCursorRepositoryIT {
    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("cursor_test").withUsername("test").withPassword("test")
        private val sameTime = LocalDateTime.of(2026, 10, 22, 12, 0)
        @JvmStatic @BeforeAll fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) { "Docker required for NEW keyset smoke" }
            mysql.start()
        }
        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    @Autowired private lateinit var repository: ExpertContactRepository
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach fun seed() {
        jdbc.update("DELETE FROM mail_record")
        jdbc.update("DELETE FROM expert_contact")
        jdbc.update("DELETE FROM campaign")
        jdbc.update("DELETE FROM mail_sender_account")
        jdbc.update("""
            INSERT INTO mail_sender_account
                (id, account_code, sender_email, sender_name, smtp_host, smtp_port, smtp_username,
                 smtp_password, imap_host, imap_port, imap_username, imap_password)
            VALUES (9201, 'cursor-test', 'sender@example.test', 'Test', 'smtp.test', 465,
                    'test', 'test', 'imap.test', 993, 'test', 'test')
        """.trimIndent())
        jdbc.update("INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) VALUES (9201, 'CURSOR', 'Cursor', 9201)")
        jdbc.update("INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) VALUES (9202, 'OTHER', 'Other', 9201)")
    }

    private fun contact(id: Long, campaign: Long = 9201, status: String = "NEW", at: LocalDateTime = sameTime) {
        jdbc.update("""
            INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name, current_status, updated_at)
            VALUES (?, ?, ?, ?, 'Cursor Test', ?, ?)
        """.trimIndent(), id, campaign, "orcid-$id", "expert$id@example.test", status, at)
    }

    private fun snapshot() = jdbc.queryForList("SELECT * FROM expert_contact ORDER BY id")

    @Test fun `501 same-second NEW rows keep stable tail after state updates and exclude new high ids`() {
        (1L..501L).forEach { contact(it) }
        contact(900, campaign = 9202)
        val upper = repository.findNewRetryUpperId(9201)!!
        assertEquals(501L, upper)
        val beforeFirst = snapshot()
        val first = repository.findNewRetryPage(9201, upper, null, null, 500)
        assertEquals(beforeFirst, snapshot(), "SELECT must not modify rows")
        assertEquals((501L downTo 2L).toList(), first.map { it.id })
        first.forEach { jdbc.update("UPDATE expert_contact SET current_status = 'WAITING_REPLY' WHERE id = ?", it.id) }
        contact(502)
        val beforeRest = snapshot()
        val tail = first.last()
        val second = repository.findNewRetryPage(9201, upper, tail.updatedAt, tail.id, 500)
        assertEquals(listOf(1L), second.map { it.id })
        assertTrue(repository.findNewRetryPage(9201, upper, second.last().updatedAt, second.last().id, 500).isEmpty())
        assertEquals(beforeRest, snapshot())
        assertEquals(502L, repository.findNewRetryUpperId(9201))
        // Preserve the old full-list API and its status scope.
        assertEquals(setOf(1L, 502L), repository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(9201, "NEW").map { it.id }.toSet())
        println("CURSOR_SMOKE MySQL=8.0.36 initialNEW=501 firstPage=500 tailPage=1 highIdExcluded=502 readWrites=0 SMTP=0")
    }

    @Test fun `timestamp and id keyset order is strict with campaign status and upper id isolation`() {
        contact(1, at = sameTime.minusSeconds(1))
        contact(2)
        contact(3)
        contact(4, status = "WAITING_REPLY")
        contact(5, campaign = 9202)
        contact(6, at = sameTime.plusSeconds(1))
        val before = snapshot()
        val first = repository.findNewRetryPage(9201, 4, null, null, 1).single()
        assertEquals(3L, first.id)
        val next = repository.findNewRetryPage(9201, 4, first.updatedAt, first.id, 500)
        assertEquals(listOf(2L, 1L), next.map { it.id })
        assertEquals(before, snapshot())
        assertNull(repository.findNewRetryUpperId(9999))
        assertTrue(repository.findNewRetryPage(9999, 0, null, null, 500).isEmpty())
    }

    @Test fun `invalid limits and half cursors fail before any query or mutation`() {
        contact(1)
        val before = snapshot()
        listOf(0, -1, 501).forEach { limit ->
            assertThrows(IllegalArgumentException::class.java) { repository.findNewRetryPage(9201, 1, null, null, limit) }
        }
        assertThrows(IllegalArgumentException::class.java) { repository.findNewRetryPage(9201, 1, sameTime, null, 1) }
        assertThrows(IllegalArgumentException::class.java) { repository.findNewRetryPage(9201, 1, null, 1, 1) }
        assertEquals(before, snapshot())
    }
}
