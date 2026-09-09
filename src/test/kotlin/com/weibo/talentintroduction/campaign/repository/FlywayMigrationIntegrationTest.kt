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
    fun `fresh database migrates through V122`() {
        val flyway = flyway()
        flyway.clean()
        assertEquals("122", flyway.migrate().targetSchemaVersion)
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
            assertEquals(11L, connection.queryLong("SELECT COUNT(*) FROM batch_send_setting"))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM batch_send_setting WHERE setting_key = 'batchSend.cron' AND setting_value = '0 0 0 * * ?'"
            ))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM batch_send_setting WHERE setting_key = 'batchSend.runtimeStatus' AND setting_value = 'IDLE'"
            ))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM batch_send_setting WHERE setting_key = 'autoReply.globalEnabled' AND setting_value = 'false'"
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
    fun `V117 clears only BOUNCE_RATE_HIGH pauses and keeps other pause reasons`() {
        val v116Flyway = flyway(MigrationVersion.fromVersion("116"))
        v116Flyway.clean()
        assertEquals("116", v116Flyway.migrate().targetSchemaVersion)
        connection().use { connection ->
            connection.execute(
                """
                INSERT INTO mail_sender_account
                    (account_code, sender_email, sender_name, smtp_host, smtp_port,
                     smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password,
                     auto_send_paused, auto_send_paused_reason, auto_send_paused_at)
                VALUES
                    ('bounce-paused', 'bounce-paused@example.com', 'Bounce', 'smtp.example.com', 465,
                     'bounce-paused@example.com', 'pwd', 'imap.example.com', 993,
                     'bounce-paused@example.com', 'pwd', 1, 'BOUNCE_RATE_HIGH:6.25%', NOW())
                """
            )
            connection.execute(
                """
                INSERT INTO mail_sender_account
                    (account_code, sender_email, sender_name, smtp_host, smtp_port,
                     smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password,
                     auto_send_paused, auto_send_paused_reason, auto_send_paused_at)
                VALUES
                    ('selfcheck-paused', 'selfcheck-paused@example.com', 'SelfCheck', 'smtp.example.com', 465,
                     'selfcheck-paused@example.com', 'pwd', 'imap.example.com', 993,
                     'selfcheck-paused@example.com', 'pwd', 1, 'SELF_CHECK_FAILED:timeout', NOW())
                """
            )
            connection.execute(
                """
                INSERT INTO mail_sender_account
                    (account_code, sender_email, sender_name, smtp_host, smtp_port,
                     smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password,
                     auto_send_paused, auto_send_paused_reason, auto_send_paused_at)
                VALUES
                    ('reputation-paused', 'reputation-paused@example.com', 'Reputation', 'smtp.example.com', 465,
                     'reputation-paused@example.com', 'pwd', 'imap.example.com', 993,
                     'reputation-paused@example.com', 'pwd', 1, 'REPUTATION:spam_rate=0.5%', NOW())
                """
            )
            connection.execute(
                """
                INSERT INTO mail_sender_account
                    (account_code, sender_email, sender_name, smtp_host, smtp_port,
                     smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password,
                     auto_send_paused, auto_send_paused_reason, auto_send_paused_at)
                VALUES
                    ('normal', 'normal@example.com', 'Normal', 'smtp.example.com', 465,
                     'normal@example.com', 'pwd', 'imap.example.com', 993,
                     'normal@example.com', 'pwd', 0, NULL, NULL)
                """
            )
        }

        assertEquals("122", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_sender_account " +
                    "WHERE account_code = 'bounce-paused' AND auto_send_paused = 0 " +
                    "AND auto_send_paused_reason IS NULL AND auto_send_paused_at IS NULL"
            ))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_sender_account " +
                    "WHERE account_code = 'selfcheck-paused' AND auto_send_paused = 1 " +
                    "AND auto_send_paused_reason = 'SELF_CHECK_FAILED:timeout'"
            ))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_sender_account " +
                    "WHERE account_code = 'reputation-paused' AND auto_send_paused = 1 " +
                    "AND auto_send_paused_reason = 'REPUTATION:spam_rate=0.5%'"
            ))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_sender_account " +
                    "WHERE account_code = 'normal' AND auto_send_paused = 0 " +
                    "AND auto_send_paused_reason IS NULL"
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
    fun `database at original V23 upgrades to V122 without repair`() {
        val v23Flyway = flyway(MigrationVersion.fromVersion("23"))
        v23Flyway.clean()
        assertEquals("23", v23Flyway.migrate().targetSchemaVersion)
        connection().use { connection ->
            assertTrue(connection.columnExists("mail_record", "mail_send_attempt_id"))
        }
        assertEquals("122", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            assertTrue(connection.columnExists("mail_record", "mail_send_attempt_id"))
            assertTrue(connection.tableExists("batch_send_setting"))
            assertTrue(connection.columnExists("mail_sender_account", "auto_send_paused"))
        }
    }

    @Test
    fun `database at original V24 upgrades to V122 without repair`() {
        val v24Flyway = flyway(MigrationVersion.fromVersion("24"))
        v24Flyway.clean()
        assertEquals("24", v24Flyway.migrate().targetSchemaVersion)
        connection().use { connection ->
            assertFalse(connection.tableExists("admin_user"))
        }
        assertEquals("122", flyway().migrate().targetSchemaVersion)
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

        assertEquals("122", flyway().migrate().targetSchemaVersion)
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
        assertEquals("122", flyway().migrate().targetSchemaVersion)
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

    @Test
    fun `V118 allows metadata-only attachments preserving historical values and owner XOR`() {
        migrateToV23AndSeedBase()
        connection().use { connection ->
            connection.execute(
                """
                INSERT INTO mail_record
                    (id, expert_contact_id, direction, mail_type, message_id, send_status)
                VALUES (201, 1, 'INBOUND', 'REPLY', 'msg-with-attachment', 'SENT')
                """
            )
            connection.execute(
                """
                INSERT INTO mail_attachment
                    (id, mail_record_id, file_name, content_type, file_size, storage_path)
                VALUES (301, 201, '历史简历.pdf', 'application/pdf', 12345, '/attachments/1/301.pdf')
                """
            )
            connection.execute(
                """
                INSERT INTO expert_document
                    (id, expert_contact_id, mail_attachment_id, document_type, document_status)
                VALUES (401, 1, 301, 'CV', 'PENDING_REVIEW')
                """
            )
        }

        assertEquals("122", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            // 历史值原样保留（I-2/I-3），document_status 迁移前后不变。
            assertEquals(12345L, connection.queryLong(
                "SELECT file_size FROM mail_attachment WHERE id = 301"
            ))
            assertEquals("/attachments/1/301.pdf", connection.queryString(
                "SELECT storage_path FROM mail_attachment WHERE id = 301"
            ))
            assertEquals("历史简历.pdf", connection.queryString(
                "SELECT file_name FROM mail_attachment WHERE id = 301"
            ))
            assertEquals("PENDING_REVIEW", connection.queryString(
                "SELECT document_status FROM expert_document WHERE id = 401"
            ))

            // V118 列契约：file_size/storage_path 可空，file_name 为 TEXT 且不可空。
            assertEquals("YES", connection.queryString(
                "SELECT IS_NULLABLE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'mail_attachment' " +
                    "AND column_name = 'file_size'"
            ))
            assertEquals("YES", connection.queryString(
                "SELECT IS_NULLABLE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'mail_attachment' " +
                    "AND column_name = 'storage_path'"
            ))
            assertEquals("NO", connection.queryString(
                "SELECT IS_NULLABLE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'mail_attachment' " +
                    "AND column_name = 'file_name'"
            ))
            assertEquals("text", connection.queryString(
                "SELECT DATA_TYPE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'mail_attachment' " +
                    "AND column_name = 'file_name'"
            ))

            // V36 owner XOR 与外键在 MODIFY 后仍生效。
            assertTrue(connection.checkConstraintExists("mail_attachment", "chk_mail_attachment_owner"))
            assertTrue(connection.foreignKeyExists("mail_attachment", "fk_mail_attachment_record"))
            assertTrue(connection.foreignKeyExists("mail_attachment", "fk_mail_attachment_inbound"))

            // 元数据登记：仅有名称，size/path 均为 NULL；owner 仍须恰好一个。
            connection.execute(
                """
                INSERT INTO mail_attachment
                    (mail_record_id, file_name, content_type, file_size, storage_path)
                VALUES (201, '元数据附件.pdf', 'application/pdf', NULL, NULL)
                """
            )
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_attachment WHERE file_name = '元数据附件.pdf' " +
                    "AND file_size IS NULL AND storage_path IS NULL"
            ))
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_attachment
                        (mail_record_id, inbound_processing_id, file_name)
                    VALUES (NULL, NULL, 'no-owner.pdf')
                    """
                )
            }

            // 长中文文件名（300 字）在 TEXT 下完整往返（I-3）。
            val longName = "研".repeat(300)
            connection.execute(
                """
                INSERT INTO mail_attachment
                    (mail_record_id, file_name, content_type, file_size, storage_path)
                VALUES (201, '$longName', 'application/pdf', NULL, NULL)
                """
            )
            assertEquals(300L, connection.queryLong(
                "SELECT CHAR_LENGTH(file_name) FROM mail_attachment WHERE file_name = '$longName'"
            ))
            assertEquals(longName, connection.queryString(
                "SELECT file_name FROM mail_attachment WHERE file_name = '$longName'"
            ))

            // 真实零字节文件：size = 0 且路径存在，是合法数据（I-1）。
            connection.execute(
                """
                INSERT INTO mail_attachment
                    (mail_record_id, file_name, content_type, file_size, storage_path)
                VALUES (201, 'empty.txt', 'text/plain', 0, '/attachments/1/empty.txt')
                """
            )
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_attachment WHERE file_name = 'empty.txt' " +
                    "AND file_size = 0 AND storage_path IS NOT NULL"
            ))
        }
    }

    @Test
    fun `V119 creates mail_attachment_transfer with the persistence contract`() {
        migrateToV23AndSeedBase()
        connection().use { connection ->
            connection.execute(
                """
                INSERT INTO mail_record
                    (id, expert_contact_id, direction, mail_type, message_id)
                VALUES (201, 1, 'INBOUND', 'REPLY', 'msg-with-part')
                """
            )
            connection.execute(
                """
                INSERT INTO mail_attachment
                    (id, mail_record_id, file_name, content_type, file_size, storage_path)
                VALUES (301, 201, 'cv.pdf', 'application/pdf', 12345, '/legacy/301.pdf')
                """
            )
        }

        assertEquals("122", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            assertTrue(connection.tableExists("mail_attachment_transfer"))
            listOf(
                "id", "attachment_id", "purpose", "account_code", "folder",
                "uid_validity", "imap_uid", "part_path", "message_id", "file_name",
                "content_type", "encoded_size", "disposition", "inbound_processing_id",
                "state", "requested_by", "queued_at", "started_at", "lease_until",
                "worker_token", "bytes_downloaded", "attempt", "error_code",
                "error_message", "created_at", "updated_at"
            ).forEach { column ->
                assertTrue(
                    connection.columnExists("mail_attachment_transfer", column),
                    "missing mail_attachment_transfer column $column"
                )
            }
            // 唯一键/队列索引/账号活动索引/processing 索引
            assertTrue(connection.indexExists("mail_attachment_transfer", "uk_mail_attachment_transfer_source"))
            assertTrue(connection.indexExists("mail_attachment_transfer", "uk_mail_attachment_transfer_attachment"))
            assertTrue(connection.indexExists("mail_attachment_transfer", "idx_mail_attachment_transfer_queue"))
            assertTrue(connection.indexExists("mail_attachment_transfer", "idx_mail_attachment_transfer_account"))
            assertTrue(connection.indexExists("mail_attachment_transfer", "idx_mail_attachment_transfer_inbound"))
            // CHECK：purpose/state 枚举与 purpose-attachment 组合
            assertTrue(connection.checkConstraintExists("mail_attachment_transfer", "chk_mail_attachment_transfer_purpose"))
            assertTrue(connection.checkConstraintExists("mail_attachment_transfer", "chk_mail_attachment_transfer_state"))
            assertTrue(connection.checkConstraintExists("mail_attachment_transfer", "chk_mail_attachment_transfer_purpose_attachment"))
            // 外键：attachment 与 inbound processing 均为真实 FK
            assertTrue(connection.foreignKeyExists("mail_attachment_transfer", "fk_mail_attachment_transfer_attachment"))
            assertTrue(connection.foreignKeyExists("mail_attachment_transfer", "fk_mail_attachment_transfer_inbound"))
            // folder 列级 utf8mb4_bin：同一唯一键下大小写区分（I-1）
            assertEquals("utf8mb4_bin", connection.queryString(
                "SELECT COLLATION_NAME FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'mail_attachment_transfer' " +
                    "AND column_name = 'folder'"
            ))

            // 合法 MATERIAL 行：默认 METADATA_ONLY、attempt/bytes 归零、无租约。
            connection.execute(
                """
                INSERT INTO mail_attachment_transfer
                    (attachment_id, purpose, account_code, folder, uid_validity, imap_uid,
                     part_path, message_id, file_name, content_type)
                VALUES (301, 'MATERIAL', 'sender', 'INBOX', 5, 42, '2', 'msg-with-part', 'cv.pdf', 'application/pdf')
                """
            )
            assertEquals("METADATA_ONLY", connection.queryString(
                "SELECT state FROM mail_attachment_transfer WHERE id = 1"
            ))
            assertEquals(0L, connection.queryLong(
                "SELECT attempt FROM mail_attachment_transfer WHERE id = 1"
            ))
            assertEquals(0L, connection.queryLong(
                "SELECT bytes_downloaded FROM mail_attachment_transfer WHERE id = 1"
            ))

            // 同源重复（account/folder/uid_validity/imap_uid/part_path）被唯一键拒绝；
            // 仅 folder 大小写不同则允许成两行（区分大小写）。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_attachment_transfer
                        (attachment_id, purpose, account_code, folder, uid_validity, imap_uid,
                         part_path, message_id, file_name, content_type)
                    VALUES (301, 'MATERIAL', 'sender', 'INBOX', 5, 42, '2', 'msg-with-part', 'cv.pdf', 'application/pdf')
                    """
                )
            }
            connection.execute(
                """
                INSERT INTO mail_attachment_transfer
                    (purpose, account_code, folder, uid_validity, imap_uid, part_path, file_name)
                VALUES ('DMARC', 'sender', 'inbox', 5, 43, '2', 'report.xml')
                """
            )
            assertEquals(2L, connection.queryLong("SELECT COUNT(*) FROM mail_attachment_transfer"))

            // 一个附件至多一行（uk_mail_attachment_transfer_attachment）。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_attachment_transfer
                        (attachment_id, purpose, account_code, folder, uid_validity, imap_uid,
                         part_path, file_name)
                    VALUES (301, 'MATERIAL', 'sender', 'INBOX', 6, 44, '2', 'cv.pdf')
                    """
                )
            }

            // CHECK：MATERIAL 必须 attachment_id，DMARC 必须为空；未知 purpose/state 拒绝。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_attachment_transfer
                        (purpose, account_code, folder, uid_validity, imap_uid, part_path, file_name)
                    VALUES ('MATERIAL', 'sender', 'INBOX', 5, 45, '2', 'cv.pdf')
                    """
                )
            }
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_attachment_transfer
                        (attachment_id, purpose, account_code, folder, uid_validity, imap_uid,
                         part_path, file_name)
                    VALUES (301, 'DMARC', 'sender', 'INBOX', 5, 46, '2', 'report.xml')
                    """
                )
            }
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_attachment_transfer
                        (purpose, account_code, folder, uid_validity, imap_uid, part_path, file_name)
                    VALUES ('OTHER', 'sender', 'INBOX', 5, 47, '2', 'x.pdf')
                    """
                )
            }
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_attachment_transfer
                        (purpose, account_code, folder, uid_validity, imap_uid, part_path,
                         file_name, state)
                    VALUES ('DMARC', 'sender', 'INBOX', 5, 48, '2', 'report.xml', 'BOGUS')
                    """
                )
            }
        }
    }

    @Test
    fun `V120 scopes inbound uid uniqueness by uid validity without backfilling history`() {
        migrateToV23AndSeedBase()
        connection().use { connection ->
            connection.execute(
                """
                INSERT INTO inbound_mail_processing
                    (sender_account_code, imap_uid, message_id, from_email, subject,
                     received_at, process_status, process_reason, expert_contact_id)
                VALUES ('sender', 777, 'historical-msg', 'one@example.com', 'Old',
                        '2026-06-01 10:00:00', 'PROCESSED', 'QA_AUTO_REPLIED', 1)
                """
            )
        }

        assertEquals("122", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            // 列契约：uid_validity BIGINT NOT NULL DEFAULT 0
            assertTrue(connection.columnExists("inbound_mail_processing", "uid_validity"))
            assertEquals("bigint", connection.queryString(
                "SELECT DATA_TYPE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'inbound_mail_processing' " +
                    "AND column_name = 'uid_validity'"
            ))
            assertEquals("NO", connection.queryString(
                "SELECT IS_NULLABLE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'inbound_mail_processing' " +
                    "AND column_name = 'uid_validity'"
            ))
            assertEquals("0", connection.queryString(
                "SELECT COLUMN_DEFAULT FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'inbound_mail_processing' " +
                    "AND column_name = 'uid_validity'"
            ))

            // 历史行不被回填：0 = 历史代际未知（I-1）
            assertEquals(0L, connection.queryLong(
                "SELECT uid_validity FROM inbound_mail_processing WHERE imap_uid = 777"
            ))

            // 唯一键替换：旧 (account, uid) 键移除，新 (account, uid_validity, uid) 键生效
            assertEquals(0, connection.queryLong(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'inbound_mail_processing' " +
                    "AND index_name = 'uk_inbound_mail_processing_uid'"
            ))
            assertTrue(connection.indexExists("inbound_mail_processing", "uk_inbound_mail_processing_uid_validity"))

            // 同 UID 不同代际可以并存；同 (account, uid_validity, uid) 唯一
            connection.execute(
                """
                INSERT INTO inbound_mail_processing
                    (sender_account_code, uid_validity, imap_uid, message_id, from_email,
                     received_at, process_status, process_reason)
                VALUES ('sender', 5, 777, 'new-generation', 'one@example.com',
                        '2026-09-01 10:00:00', 'MANUAL_REVIEW', 'BODY_TRUNCATED')
                """
            )
            connection.execute(
                """
                INSERT INTO inbound_mail_processing
                    (sender_account_code, uid_validity, imap_uid, message_id, from_email,
                     received_at, process_status, process_reason)
                VALUES ('sender', 6, 778, 'second-generation', 'one@example.com',
                        '2026-09-02 10:00:00', 'MANUAL_REVIEW', 'BODY_TRUNCATED')
                """
            )
            assertEquals(2L, connection.queryLong(
                "SELECT COUNT(*) FROM inbound_mail_processing WHERE imap_uid = 777"
            ))
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO inbound_mail_processing
                        (sender_account_code, uid_validity, imap_uid, message_id, from_email,
                         received_at, process_status, process_reason)
                    VALUES ('sender', 5, 777, 'duplicate', 'one@example.com',
                            '2026-09-03 10:00:00', 'MANUAL_REVIEW', 'BODY_TRUNCATED')
                    """
                )
            }
            // 历史 0 行仍保留原唯一性：0 代际同 UID 再插被拒绝
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO inbound_mail_processing
                        (sender_account_code, uid_validity, imap_uid, message_id, from_email,
                         received_at, process_status, process_reason)
                    VALUES ('sender', 0, 777, 'historical-dup', 'one@example.com',
                            '2026-06-02 10:00:00', 'MANUAL_REVIEW', 'BODY_TRUNCATED')
                    """
                )
            }
            // 外键仍生效
            assertTrue(connection.foreignKeyExists("inbound_mail_processing", "fk_inbound_mail_processing_contact"))
        }
    }

    @Test
    fun `V121 creates expert_follow with composite ownership key and contact FK`() {
        migrateToV23AndSeedBase()
        connection().use { connection ->
            assertFalse(connection.tableExists("expert_follow"))
        }

        assertEquals("122", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            assertTrue(connection.tableExists("expert_follow"))
            listOf("username", "expert_contact_id", "created_at").forEach { column ->
                assertTrue(connection.columnExists("expert_follow", column), "missing column $column")
            }
            // 列契约：username VARCHAR(64) NOT NULL / expert_contact_id BIGINT NOT NULL /
            // created_at DATETIME NOT NULL（无默认：首次关注时间由唯一写者显式落库）。
            assertEquals("varchar", connection.queryString(
                "SELECT DATA_TYPE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_follow' " +
                    "AND column_name = 'username'"
            ))
            assertEquals("64", connection.queryString(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_follow' " +
                    "AND column_name = 'username'"
            ))
            assertEquals("bigint", connection.queryString(
                "SELECT DATA_TYPE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_follow' " +
                    "AND column_name = 'expert_contact_id'"
            ))
            listOf("username", "expert_contact_id", "created_at").forEach { column ->
                assertEquals("NO", connection.queryString(
                    "SELECT IS_NULLABLE FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'expert_follow' " +
                        "AND column_name = '$column'"
                ))
            }

            // 复合主键列序：(username, expert_contact_id)
            assertEquals(2L, connection.queryLong(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_follow' " +
                    "AND index_name = 'PRIMARY'"
            ))
            assertEquals("username", connection.queryString(
                "SELECT COLUMN_NAME FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_follow' " +
                    "AND index_name = 'PRIMARY' AND SEQ_IN_INDEX = 1"
            ))
            assertEquals("expert_contact_id", connection.queryString(
                "SELECT COLUMN_NAME FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_follow' " +
                    "AND index_name = 'PRIMARY' AND SEQ_IN_INDEX = 2"
            ))

            // 复合主键 (username, expert_contact_id)：同一用户对同一专家至多一行；
            // 不同用户可关注同一专家；同一用户可关注不同专家。
            connection.execute(
                "INSERT INTO expert_follow (username, expert_contact_id, created_at) " +
                    "VALUES ('admin', 1, '2026-09-08 10:00:00')"
            )
            assertThrows(SQLException::class.java) {
                connection.execute(
                    "INSERT INTO expert_follow (username, expert_contact_id, created_at) " +
                        "VALUES ('admin', 1, '2026-09-08 10:00:01')"
                )
            }
            connection.execute(
                "INSERT INTO expert_follow (username, expert_contact_id, created_at) " +
                    "VALUES ('admin', 2, '2026-09-08 10:00:00')"
            )
            connection.execute(
                "INSERT INTO expert_follow (username, expert_contact_id, created_at) " +
                    "VALUES ('operator-b', 1, '2026-09-08 10:00:00')"
            )
            assertEquals(3L, connection.queryLong("SELECT COUNT(*) FROM expert_follow"))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM expert_follow WHERE username = 'admin' AND expert_contact_id = 1"
            ))

            // created_at 无默认：省略即拒绝（首次关注时间必须显式写）。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    "INSERT INTO expert_follow (username, expert_contact_id) VALUES ('admin', 1)"
                )
            }

            // 联系人外键仍生效：未知专家拒绝。
            assertTrue(connection.foreignKeyExists("expert_follow", "fk_expert_follow_contact"))
            assertThrows(SQLException::class.java) {
                connection.execute(
                    "INSERT INTO expert_follow (username, expert_contact_id, created_at) " +
                        "VALUES ('admin', 999999, '2026-09-08 10:00:00')"
                )
            }
        }
    }

    @Test
    fun `V122 seeds meeting confirmation template without touching old templates`() {
        val flyway = flyway()
        flyway.clean()
        flyway.migrate()
        connection().use { connection ->
            // 新专用模板：code/type/名称/subject/描述固定，恰好 1 头 + 1 CUSTOM_TEXT 块。
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_compose_template " +
                    "WHERE template_code = 'MANUAL_MEETING_CONFIRMATION'"
            ))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_compose_template " +
                    "WHERE template_code = 'MANUAL_MEETING_CONFIRMATION' " +
                    "AND mail_type = 'MANUAL_MEETING_CONFIRMATION' AND enabled = 1"
            ))
            assertEquals("专家会议确认 · 英文", connection.queryString(
                "SELECT template_name FROM mail_compose_template " +
                    "WHERE template_code = 'MANUAL_MEETING_CONFIRMATION'"
            ))
            assertEquals("Meeting confirmation", connection.queryString(
                "SELECT subject FROM mail_compose_template " +
                    "WHERE template_code = 'MANUAL_MEETING_CONFIRMATION'"
            ))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_compose_template_block b " +
                    "JOIN mail_compose_template t ON t.id = b.template_id " +
                    "WHERE t.template_code = 'MANUAL_MEETING_CONFIRMATION' AND b.block_type = 'CUSTOM_TEXT'"
            ))
            // 四个 {{...}} 变量逐字存在；无通用 ${...} 残留（专用语法不喂给通用渲染）。
            val customText = connection.queryString(
                "SELECT b.custom_text FROM mail_compose_template_block b " +
                    "JOIN mail_compose_template t ON t.id = b.template_id " +
                    "WHERE t.template_code = 'MANUAL_MEETING_CONFIRMATION'"
            )
            listOf(
                "{{expert_salutation}}", "{{meeting_time}}", "{{zoom_url}}", "{{sender_signature}}"
            ).forEach { token ->
                assertTrue(customText.contains(token), "missing token $token")
            }
            assertTrue(!customText.contains("\${"))

            // 旧 MEETING_CONFIRMATION（V62 由 mail_template 迁入）原样保留：1 头 + 1 块，正文未变。
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_compose_template WHERE template_code = 'MEETING_CONFIRMATION'"
            ))
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_compose_template_block b " +
                    "JOIN mail_compose_template t ON t.id = b.template_id " +
                    "WHERE t.template_code = 'MEETING_CONFIRMATION'"
            ))
            assertEquals(connection.queryString(
                "SELECT body FROM mail_template WHERE template_code = 'MEETING_CONFIRMATION'"
            ), connection.queryString(
                "SELECT b.custom_text FROM mail_compose_template_block b " +
                    "JOIN mail_compose_template t ON t.id = b.template_id " +
                    "WHERE t.template_code = 'MEETING_CONFIRMATION'"
            ))
        }
    }

    @Test
    fun `V122 does not overwrite a pre-existing manual meeting template configuration`() {
        val v121Flyway = flyway(MigrationVersion.fromVersion("121"))
        v121Flyway.clean()
        v121Flyway.migrate()
        connection().use { connection ->
            // 运营在 V121 阶段已手工创建同 code 模板（含自定义正文与两个块）。
            connection.execute(
                "INSERT INTO mail_compose_template " +
                    "(template_code, template_name, subject, description, mail_type, enabled) " +
                    "VALUES ('MANUAL_MEETING_CONFIRMATION', '人工自定义', 'Custom subject', " +
                    "'custom description', 'MANUAL_MEETING_CONFIRMATION', 0)"
            )
            connection.execute(
                "INSERT INTO mail_compose_template_block (template_id, block_order, block_type, custom_text) " +
                    "SELECT id, 0, 'CUSTOM_TEXT', 'custom body a' FROM mail_compose_template " +
                    "WHERE template_code = 'MANUAL_MEETING_CONFIRMATION'"
            )
            connection.execute(
                "INSERT INTO mail_compose_template_block (template_id, block_order, block_type, custom_text) " +
                    "SELECT id, 1, 'CUSTOM_TEXT', 'custom body b' FROM mail_compose_template " +
                    "WHERE template_code = 'MANUAL_MEETING_CONFIRMATION'"
            )
        }

        assertEquals("122", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            // 头不被覆盖：名称/主题/禁用状态原样；不新增任何块（禁止为原有模板删块/加块）。
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_compose_template WHERE template_code = 'MANUAL_MEETING_CONFIRMATION'"
            ))
            assertEquals("人工自定义", connection.queryString(
                "SELECT template_name FROM mail_compose_template " +
                    "WHERE template_code = 'MANUAL_MEETING_CONFIRMATION'"
            ))
            assertEquals("Custom subject", connection.queryString(
                "SELECT subject FROM mail_compose_template " +
                    "WHERE template_code = 'MANUAL_MEETING_CONFIRMATION'"
            ))
            assertEquals(0L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_compose_template WHERE template_code = 'MANUAL_MEETING_CONFIRMATION' " +
                    "AND enabled = 1"
            ))
            assertEquals(2L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_compose_template_block b " +
                    "JOIN mail_compose_template t ON t.id = b.template_id " +
                    "WHERE t.template_code = 'MANUAL_MEETING_CONFIRMATION'"
            ))
            assertTrue(!connection.queryString(
                "SELECT GROUP_CONCAT(custom_text ORDER BY block_order SEPARATOR '|') " +
                    "FROM mail_compose_template_block b " +
                    "JOIN mail_compose_template t ON t.id = b.template_id " +
                    "WHERE t.template_code = 'MANUAL_MEETING_CONFIRMATION'"
            ).contains("Dear {{expert_salutation}}"))
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
