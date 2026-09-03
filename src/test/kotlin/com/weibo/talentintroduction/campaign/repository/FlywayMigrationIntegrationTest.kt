package com.weibo.talentintroduction.campaign.repository

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.io.File
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
class FlywayMigrationIntegrationTest {
    companion object {
        private class KotlinMySqlContainer(image: String) :
            MySQLContainer<KotlinMySqlContainer>(image)

        private val mysql = KotlinMySqlContainer("mysql:8.0.36")
            .withDatabaseName("talent_introduction")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @BeforeAll
        fun startMysql() {
            check(DockerClientFactory.instance().isDockerAvailable) {
                "Docker is required for Flyway migration tests"
            }
            mysql.start()
        }

        @JvmStatic
        @AfterAll
        fun stopMysql() {
            if (mysql.isRunning) mysql.stop()
        }
    }

    @Test
    fun `V23 file checksum matches expected`() {
        val file = File("src/main/resources/db/migration/V23__create_mail_send_attempt_and_add_mail_record_error.sql")
        assertTrue(file.exists())
        val bytes = file.readBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val checksum = digest.joinToString("") { "%02x".format(it) }
        assertEquals("e0ba4fafaa762d7bc2ac4f4c4e1ce15348b325cb19912e6b9fd4aa702e7a70e6", checksum)
    }

    @Test
    fun `fresh database migrates through V112`() {
        val flyway = flyway()
        flyway.clean()
        assertEquals("112", flyway.migrate().targetSchemaVersion)
    }

    @Test
    fun `V27 seeds batch_send_setting with default rows`() {
        val flyway = flyway()
        flyway.clean()
        flyway.migrate()
        connection().use { connection ->
            assertTrue(connection.tableExists("batch_send_setting"))
            assertTrue(connection.columnExists("batch_send_setting", "setting_key"))
            assertTrue(connection.columnExists("batch_send_setting", "setting_value"))
            assertEquals(10L, connection.queryLong("SELECT COUNT(*) FROM batch_send_setting"))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM batch_send_setting WHERE setting_key = 'batchSend.cron' AND setting_value = '0 0 0 * * ?'"
            ))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM batch_send_setting WHERE setting_key = 'batchSend.runtimeStatus' AND setting_value = 'IDLE'"
            ))
        }
    }

    @Test
    fun `V28 adds auto_send_paused columns to mail_sender_account`() {
        val flyway = flyway()
        flyway.clean()
        flyway.migrate()
        connection().use { connection ->
            assertTrue(connection.columnExists("mail_sender_account", "auto_send_paused"))
            assertTrue(connection.columnExists("mail_sender_account", "auto_send_paused_reason"))
            assertTrue(connection.columnExists("mail_sender_account", "auto_send_paused_at"))
            assertEquals(0L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_sender_account WHERE auto_send_paused = 1"
            ))
        }
    }

    @Test
    fun `V112 creates rag knowledge base with seeded corpus and fingerprint`() {
        val flyway = flyway()
        flyway.clean()
        flyway.migrate()
        connection().use { connection ->
            listOf(
                "rag_fact", "rag_phrase_group", "rag_intent_coverage",
                "rag_mandatory_rule", "rag_prefilter_exclusion", "rag_kb_meta"
            ).forEach { table ->
                assertTrue(connection.tableExists(table), "missing table $table")
            }
            listOf(
                "fact_code", "area", "seq", "title", "category", "question_variants",
                "keywords", "answer", "coverage_keys", "reply_policy", "status",
                "risk_level", "render_mode", "source_refs", "legacy_rule_id",
                "enabled", "sort_order"
            ).forEach { column ->
                assertTrue(connection.columnExists("rag_fact", column), "missing rag_fact column $column")
            }
            assertTrue(connection.indexExists("rag_fact", "uk_rag_fact_code"))
            assertTrue(connection.checkConstraintExists("rag_kb_meta", "chk_rag_kb_meta_singleton"))

            // 45 条语料种子，fact_code 全唯一。
            assertEquals(45L, connection.queryLong("SELECT COUNT(*) FROM rag_fact"))
            assertEquals(45L, connection.queryLong("SELECT COUNT(DISTINCT fact_code) FROM rag_fact"))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM rag_fact WHERE enabled = 0"
            ))
            assertEquals(7L, connection.queryLong(
                "SELECT COUNT(*) FROM rag_fact WHERE render_mode = 'VERBATIM'"
            ))

            // rag_kb_meta 单行：G-2 指纹 + fact_count（与 export 脚本输出一致）。
            assertEquals(1L, connection.queryLong("SELECT COUNT(*) FROM rag_kb_meta"))
            assertEquals("e62421a42c432cf3", connection.queryString(
                "SELECT fingerprint FROM rag_kb_meta"
            ))
            assertEquals(45L, connection.queryLong("SELECT fact_count FROM rag_kb_meta"))

            // 规则表行数 + D-3 强制行。
            assertEquals(87L, connection.queryLong("SELECT COUNT(*) FROM rag_phrase_group"))
            assertEquals(21L, connection.queryLong("SELECT COUNT(*) FROM rag_intent_coverage"))
            assertEquals(6L, connection.queryLong("SELECT COUNT(*) FROM rag_mandatory_rule"))
            assertEquals(4L, connection.queryLong("SELECT COUNT(*) FROM rag_prefilter_exclusion"))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM rag_mandatory_rule " +
                    "WHERE sort_order = 15 AND rule_code = 'COMPENSATION' AND fact_codes = 'KB-FUND-033'"
            ))
        }
    }

    @Test
    fun `V111 creates expert_material_status with constraints and zero rows`() {
        val flyway = flyway()
        flyway.clean()
        flyway.migrate()
        connection().use { connection ->
            assertTrue(connection.tableExists("expert_material_status"))
            listOf("id", "expert_contact_id", "material_code", "material_status", "created_at", "updated_at")
                .forEach { column ->
                    assertTrue(
                        connection.columnExists("expert_material_status", column),
                        "missing column $column"
                    )
                }
            assertEquals(0L, connection.queryLong("SELECT COUNT(*) FROM expert_material_status"))
            assertTrue(connection.indexExists("expert_material_status", "uk_expert_material_contact_code"))
            assertTrue(connection.checkConstraintExists("expert_material_status", "chk_expert_material_code"))
            assertTrue(connection.checkConstraintExists("expert_material_status", "chk_expert_material_status"))
            assertTrue(connection.foreignKeyExists("expert_material_status", "fk_expert_material_contact"))
        }
    }

    @Test
    fun `database at original V23 upgrades to V112 without repair`() {
        val v23Flyway = flyway(MigrationVersion.fromVersion("23"))
        v23Flyway.clean()
        assertEquals("23", v23Flyway.migrate().targetSchemaVersion)
        connection().use { connection ->
            assertTrue(connection.columnExists("mail_record", "mail_send_attempt_id"))
        }
        assertEquals("112", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            assertTrue(connection.columnExists("mail_record", "mail_send_attempt_id"))
            assertTrue(connection.tableExists("batch_send_setting"))
            assertTrue(connection.columnExists("mail_sender_account", "auto_send_paused"))
        }
    }

    @Test
    fun `database at original V24 upgrades to V112 without repair`() {
        val v24Flyway = flyway(MigrationVersion.fromVersion("24"))
        v24Flyway.clean()
        assertEquals("24", v24Flyway.migrate().targetSchemaVersion)
        connection().use { connection ->
            assertFalse(connection.tableExists("admin_user"))
        }
        assertEquals("112", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            assertTrue(connection.tableExists("admin_user"))
            assertTrue(connection.columnExists("admin_user", "username"))
            assertTrue(connection.columnExists("admin_user", "password_hash"))
            assertTrue(connection.columnExists("admin_user", "must_change_password"))
            assertTrue(connection.tableExists("batch_send_setting"))
            assertTrue(connection.columnExists("mail_sender_account", "auto_send_paused"))
        }
    }

    @Test
    fun `V23 historical records are linked and SENT quota is backfilled`() {
        migrateToV23AndSeedBase()
        connection().use { connection ->
            connection.execute(
                """
                INSERT INTO mail_send_attempt
                    (id, orcid_id, mail_type, account_code, message_id, status)
                VALUES
                    (101, '0000-0001', 'INTRODUCTION', 'sender', 'msg-sent', 'SENT'),
                    (102, '0000-0002', 'INTRODUCTION', 'sender', 'msg-failed', 'FAILED')
                """
            )
            connection.execute(
                """
                INSERT INTO mail_record
                    (id, expert_contact_id, direction, mail_type, message_id, send_status, sent_at)
                VALUES
                    (201, 1, 'OUTBOUND', 'INTRODUCTION', 'msg-sent', 'SENT', '2026-06-12 10:00:00'),
                    (202, 2, 'OUTBOUND', 'INTRODUCTION', 'msg-failed', 'FAILED', NULL)
                """
            )
        }

        assertEquals("112", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            assertEquals(101L, connection.queryLong(
                "SELECT mail_send_attempt_id FROM mail_record WHERE id = 201"
            ))
            assertEquals(102L, connection.queryLong(
                "SELECT mail_send_attempt_id FROM mail_record WHERE id = 202"
            ))
            assertEquals(1L, connection.queryLong(
                "SELECT quota_counted FROM mail_send_attempt WHERE id = 101"
            ))
            assertEquals(0L, connection.queryLong(
                "SELECT quota_counted FROM mail_send_attempt WHERE id = 102"
            ))
        }
    }

    @Test
    fun `ambiguous V23 data fails before persistent V24 DDL and can rerun after repair`() {
        migrateToV23AndSeedBase()
        connection().use { connection ->
            connection.execute(
                """
                INSERT INTO mail_send_attempt
                    (id, orcid_id, mail_type, account_code, message_id, status)
                VALUES (101, '0000-0001', 'INTRODUCTION', 'sender', 'msg-ambiguous', 'SENT')
                """
            )
            connection.execute(
                """
                INSERT INTO mail_record
                    (id, expert_contact_id, direction, mail_type, message_id, send_status)
                VALUES
                    (201, 1, 'OUTBOUND', 'INTRODUCTION', 'msg-ambiguous', 'SENT'),
                    (202, 1, 'OUTBOUND', 'INTRODUCTION', 'msg-ambiguous', 'SENT')
                """
            )
        }

        assertThrows(Exception::class.java) { flyway().migrate() }
        connection().use { connection ->
            assertFalse(connection.columnExists("mail_send_attempt", "quota_counted"))
            connection.execute("DELETE FROM mail_record WHERE id = 202")
        }

        flyway().repair()
        assertEquals("112", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            assertTrue(connection.columnExists("mail_send_attempt", "quota_counted"))
        }
    }

    @Test
    fun `V24 unique and foreign key constraints are enforced`() {
        migrateToV23AndSeedBase()
        connection().use { connection ->
            connection.execute(
                """
                INSERT INTO mail_send_attempt
                    (id, orcid_id, mail_type, account_code, message_id, status)
                VALUES (101, '0000-0001', 'INTRODUCTION', 'sender', 'msg-one', 'SENT')
                """
            )
            connection.execute(
                """
                INSERT INTO mail_record
                    (id, expert_contact_id, direction, mail_type, message_id, send_status)
                VALUES (201, 1, 'OUTBOUND', 'INTRODUCTION', 'msg-one', 'SENT')
                """
            )
        }
        flyway().migrate()

        connection().use { connection ->
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_record
                        (expert_contact_id, direction, mail_type, message_id, send_status, mail_send_attempt_id)
                    VALUES (1, 'OUTBOUND', 'INTRODUCTION', 'duplicate-link', 'SENT', 101)
                    """
                )
            }
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_record
                        (expert_contact_id, direction, mail_type, message_id, send_status, mail_send_attempt_id)
                    VALUES (1, 'OUTBOUND', 'INTRODUCTION', 'missing-attempt', 'SENT', 999999)
                    """
                )
            }
        }
    }

    private fun migrateToV23AndSeedBase() {
        val v23Flyway = flyway(MigrationVersion.fromVersion("23"))
        v23Flyway.clean()
        v23Flyway.migrate()
        connection().use { connection ->
            connection.execute("DELETE FROM campaign")
            connection.execute("DELETE FROM mail_sender_account")
            connection.execute(
                """
                INSERT INTO mail_sender_account
                    (id, account_code, sender_email, sender_name, smtp_host, smtp_port,
                     smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
                VALUES
                    (1, 'sender', 'sender@example.com', 'Sender', 'smtp.example.com', 465,
                     'sender@example.com', 'pwd', 'imap.example.com', 993, 'sender@example.com', 'pwd')
                """
            )
            connection.execute(
                """
                INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id)
                VALUES (1, 'MANUAL_OUTREACH', 'Manual Outreach', 1)
                """
            )
            connection.execute(
                """
                INSERT INTO expert_contact
                    (id, campaign_id, orcid_id, expert_email, current_status)
                VALUES
                    (1, 1, '0000-0001', 'one@example.com', 'NEW'),
                    (2, 1, '0000-0002', 'two@example.com', 'NEW')
                """
            )
        }
    }

    private fun connection(): Connection =
        DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password)

    private fun Connection.execute(sql: String) {
        createStatement().use { it.execute(sql.trimIndent()) }
    }

    private fun Connection.queryLong(sql: String): Long =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                result.getLong(1)
            }
        }

    private fun Connection.queryString(sql: String): String =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                result.getString(1)
            }
        }

    private fun Connection.columnExists(table: String, column: String): Boolean =
        prepareStatement(
            """
            SELECT COUNT(*)
              FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = ?
               AND column_name = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, table)
            statement.setString(2, column)
            statement.executeQuery().use { result ->
                result.next()
                result.getInt(1) == 1
            }
        }

    private fun Connection.tableExists(table: String): Boolean =
        prepareStatement(
            """
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = DATABASE()
               AND table_name = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, table)
            statement.executeQuery().use { result ->
                result.next()
                result.getInt(1) == 1
            }
        }

    private fun Connection.indexExists(table: String, index: String): Boolean =
        prepareStatement(
            """
            SELECT COUNT(*)
              FROM information_schema.statistics
             WHERE table_schema = DATABASE()
               AND table_name = ?
               AND index_name = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, table)
            statement.setString(2, index)
            statement.executeQuery().use { result ->
                result.next()
                result.getInt(1) >= 1
            }
        }

    private fun Connection.checkConstraintExists(table: String, constraint: String): Boolean =
        prepareStatement(
            """
            SELECT COUNT(*)
              FROM information_schema.check_constraints
             WHERE constraint_schema = DATABASE()
               AND constraint_name = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, constraint)
            statement.executeQuery().use { result ->
                result.next()
                result.getInt(1) == 1
            }
        }

    private fun Connection.foreignKeyExists(table: String, constraint: String): Boolean =
        prepareStatement(
            """
            SELECT COUNT(*)
              FROM information_schema.table_constraints
             WHERE constraint_schema = DATABASE()
               AND table_name = ?
               AND constraint_name = ?
               AND constraint_type = 'FOREIGN KEY'
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, table)
            statement.setString(2, constraint)
            statement.executeQuery().use { result ->
                result.next()
                result.getInt(1) == 1
            }
        }

    private fun flyway(target: MigrationVersion? = null): Flyway {
        val configuration = Flyway.configure()
            .dataSource(mysql.jdbcUrl, mysql.username, mysql.password)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .placeholderReplacement(false)
        if (target != null) configuration.target(target)
        return configuration.load()
    }
}
