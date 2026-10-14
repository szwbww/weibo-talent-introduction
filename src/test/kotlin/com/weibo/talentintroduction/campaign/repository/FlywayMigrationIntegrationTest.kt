package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.expert.domain.CompanyMatchStatus
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileDto
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileFields
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileGroup
import com.weibo.talentintroduction.expert.domain.ExpertManualProfilePatch
import com.weibo.talentintroduction.expert.domain.ExpertProfileConflictException
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.MeetingVideoException
import com.weibo.talentintroduction.document.domain.MeetingVideoRenameRequest
import com.weibo.talentintroduction.document.domain.MeetingVideoUploadRequest
import com.weibo.talentintroduction.document.domain.MeetingVideoView
import com.weibo.talentintroduction.document.service.ExpertMeetingVideoService
import com.weibo.talentintroduction.expert.service.ExpertManualProfileService
import com.weibo.talentintroduction.expert.service.ExpertProfileIdentityResolver
import com.weibo.talentintroduction.expert.service.ExpertSearchService
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
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.Comparator
import java.util.concurrent.Executors
import kotlin.streams.toList
import com.weibo.talentintroduction.mail.service.ExpertContactNoteService
import com.weibo.talentintroduction.mail.service.ExpertContactNoteView
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

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
    fun `V140 adds nullable reuse origin without changing existing verification results`() {
        val before = flyway(MigrationVersion.fromVersion("139"))
        before.clean()
        before.migrate()
        connection().use { c ->
            c.execute("INSERT INTO task_execution (id, task_type, trigger_type, status, started_at) VALUES (99001, 'MANUAL_INITIAL_OUTREACH', 'MANUAL', 'SUCCESS', NOW())")
            c.execute("""INSERT INTO batch_email_verification
                (task_execution_id, orcid_id, email, decision, provider_state, request_count, checked_at, created_at, updated_at)
                VALUES (99001, 'old', 'old@example.test', 'PASS', 'deliverable', 1, '2026-01-01 00:00:00', NOW(), NOW())""")
        }
        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { c ->
            assertTrue(c.columnExists("batch_email_verification", "reused_from_id"))
            assertTrue(c.indexExists("batch_email_verification", "idx_batch_email_verification_email_time"))
            assertEquals(1L, c.queryLong("SELECT COUNT(*) FROM batch_email_verification WHERE email='old@example.test' AND decision='PASS' AND reused_from_id IS NULL AND request_count=1 AND checked_at='2026-01-01 00:00:00'"))
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
    fun `V142 preserves old config as false and stores enabled historical exclusion`() {
        val v141 = flyway(MigrationVersion.fromVersion("141"))
        v141.clean()
        assertEquals("141", v141.migrate().targetSchemaVersion)
        connection().use { c ->
            assertFalse(c.columnExists("batch_send_task_config", "exclude_verified_unavailable_emails"))
            assertTrue(c.queryLong("SELECT COUNT(*) FROM batch_send_task_config") > 0L)
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { c ->
            assertTrue(c.columnExists("batch_send_task_config", "exclude_verified_unavailable_emails"))
            val configId = c.queryLong("SELECT id FROM batch_send_task_config ORDER BY id LIMIT 1")
            assertEquals(
                0L,
                c.queryLong("SELECT exclude_verified_unavailable_emails FROM batch_send_task_config WHERE id=$configId")
            )
            assertEquals(
                "NO",
                c.queryString(
                    "SELECT IS_NULLABLE FROM information_schema.columns " +
                        "WHERE table_schema=DATABASE() AND table_name='batch_send_task_config' " +
                        "AND column_name='exclude_verified_unavailable_emails'"
                )
            )
            assertEquals(
                "0",
                c.queryString(
                    "SELECT COLUMN_DEFAULT FROM information_schema.columns " +
                        "WHERE table_schema=DATABASE() AND table_name='batch_send_task_config' " +
                        "AND column_name='exclude_verified_unavailable_emails'"
                )
            )
            c.execute("UPDATE batch_send_task_config SET exclude_verified_unavailable_emails=TRUE WHERE id=$configId")
            assertEquals(
                1L,
                c.queryLong("SELECT exclude_verified_unavailable_emails FROM batch_send_task_config WHERE id=$configId")
            )
        }
    }

    @Test
    fun `V145 adds a nullable allow list column without backfilling old configs`() {
        val v144 = flyway(MigrationVersion.fromVersion("144"))
        v144.clean()
        assertEquals("144", v144.migrate().targetSchemaVersion)
        connection().use { c ->
            assertFalse(c.columnExists("batch_send_task_config", "email_verification_allowed_states_json"))
            assertTrue(c.queryLong("SELECT COUNT(*) FROM batch_send_task_config") > 0L)
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { c ->
            assertTrue(c.columnExists("batch_send_task_config", "email_verification_allowed_states_json"))
            assertEquals(
                "YES",
                c.queryString(
                    "SELECT IS_NULLABLE FROM information_schema.columns " +
                        "WHERE table_schema=DATABASE() AND table_name='batch_send_task_config' " +
                        "AND column_name='email_verification_allowed_states_json'"
                )
            )
            val configId = c.queryLong("SELECT id FROM batch_send_task_config ORDER BY id LIMIT 1")
            // I-1: 不回填存量 —— 升级前的配置全部保持 SQL NULL（= 旧语义三态全放行）。
            assertEquals(
                0L,
                c.queryLong("SELECT COUNT(*) FROM batch_send_task_config WHERE email_verification_allowed_states_json IS NOT NULL")
            )
            // I-4: 迁移不顺手打开任何验证开关。
            assertEquals(
                0L,
                c.queryLong("SELECT email_verification_enabled FROM batch_send_task_config WHERE id=$configId")
            )
            // 显式 [] 与显式数组都是合法文本，可写入回读。
            c.execute("UPDATE batch_send_task_config SET email_verification_allowed_states_json='[]' WHERE id=$configId")
            assertEquals(
                "[]",
                c.queryString("SELECT email_verification_allowed_states_json FROM batch_send_task_config WHERE id=$configId")
            )
            c.execute("""UPDATE batch_send_task_config SET email_verification_allowed_states_json='["risky"]' WHERE id=$configId""")
            assertEquals(
                """["risky"]""",
                c.queryString("SELECT email_verification_allowed_states_json FROM batch_send_task_config WHERE id=$configId")
            )
        }
    }

    @Test
    fun `fresh database migrates through the latest version`() {
        val flyway = flyway()
        flyway.clean()
        assertEquals("157", flyway.migrate().targetSchemaVersion)
    }

    @Test
    fun `V152 creates durable owner target draft table without business changes or foreign keys`() {
        val previous = flyway(MigrationVersion.fromVersion("151"))
        previous.clean()
        previous.migrate()
        val before = connection().use { c ->
            listOf("expert_contact", "mail_record", "mail_send_attempt", "inbound_mail_processing", "outbound_mail_attachment")
                .associateWith { c.queryLong("SELECT COUNT(*) FROM $it") }
        }
        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { c ->
            assertTrue(c.tableExists("mailbox_reply_draft"))
            listOf("id", "username", "expert_contact_id", "target_kind", "inbound_processing_id", "account_scope",
                "version", "state", "subject", "html_body", "text_body", "context_json", "send_attempt_id",
                "send_version", "created_at", "updated_at").forEach { assertTrue(c.columnExists("mailbox_reply_draft", it)) }
            listOf("html_body", "text_body", "context_json").forEach {
                assertEquals("longtext", c.queryString(columnMeta("mailbox_reply_draft", it, "DATA_TYPE")))
            }
            listOf("subject", "html_body", "text_body", "context_json", "send_attempt_id", "send_version").forEach {
                assertEquals("YES", c.queryString(columnMeta("mailbox_reply_draft", it, "IS_NULLABLE")))
            }
            listOf("username", "target_kind", "account_scope").forEach {
                assertEquals("utf8mb4_bin", c.queryString(columnMeta("mailbox_reply_draft", it, "COLLATION_NAME")))
            }
            listOf("created_at", "updated_at").forEach {
                assertEquals("3", c.queryString(columnMeta("mailbox_reply_draft", it, "DATETIME_PRECISION")))
            }
            assertTrue(c.indexExists("mailbox_reply_draft", "uk_mailbox_reply_draft_target"))
            assertTrue(c.indexExists("mailbox_reply_draft", "idx_mailbox_reply_draft_list"))
            assertEquals(0L, c.queryLong("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='mailbox_reply_draft' AND constraint_type='FOREIGN KEY'"))
            before.forEach { (table, count) -> assertEquals(count, c.queryLong("SELECT COUNT(*) FROM $table")) }
        }
    }

    @Test
    fun `V153 creates the expert manual profile table and preserves note and draft rows`() {
        migrateToV23AndSeedBase()
        assertEquals("152", flyway(MigrationVersion.fromVersion("152")).migrate().targetSchemaVersion)
        connection().use { c ->
            assertFalse(c.tableExists("expert_manual_profile"))
            c.execute(
                "INSERT INTO expert_contact_note (expert_contact_id, note, updated_by, updated_at) " +
                    "VALUES (1, '迁移前备注', 'admin', '2026-10-11 10:18:00.123')"
            )
            c.execute(
                """
                INSERT INTO mailbox_reply_draft
                    (username, expert_contact_id, target_kind, inbound_processing_id, account_scope,
                     version, state, created_at, updated_at)
                VALUES ('admin', 1, 'INBOUND', 77, '', 1, 'DRAFT', '2026-10-11 10:18:00.123', '2026-10-11 10:18:00.123')
                """.trimIndent()
            )
        }
        val contactBefore = connection().use { c ->
            c.queryString("SELECT CONCAT(id, '|', campaign_id, '|', orcid_id, '|', current_status) FROM expert_contact WHERE id=1")
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { c ->
            assertTrue(c.tableExists("expert_manual_profile"))
            // 迁移本身不写业务数据；19 项全部是真实独立列，且与白名单逐列一致。
            assertEquals(0L, c.queryLong("SELECT COUNT(*) FROM expert_manual_profile"))
            assertEquals(19, ExpertManualProfileFields.ALL.size)
            ExpertManualProfileFields.ALL.forEach { field ->
                assertTrue(c.columnExists("expert_manual_profile", field.column), "缺少列 ${field.column}")
            }
            assertEquals(
                23L,
                c.queryLong(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema=DATABASE() AND table_name='expert_manual_profile'"
                )
            )
            assertEquals("varchar", c.queryString(columnMeta("expert_manual_profile", "expert_key", "DATA_TYPE")))
            assertEquals("64", c.queryString(columnMeta("expert_manual_profile", "expert_key", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("utf8mb4_bin", c.queryString(columnMeta("expert_manual_profile", "expert_key", "COLLATION_NAME")))
            assertEquals("NO", c.queryString(columnMeta("expert_manual_profile", "expert_key", "IS_NULLABLE")))
            assertEquals(
                1L,
                c.queryLong(
                    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() " +
                        "AND table_name='expert_manual_profile' AND index_name='PRIMARY' " +
                        "AND column_name='expert_key' AND non_unique=0"
                )
            )
            assertEquals(
                "InnoDB",
                c.queryString("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='expert_manual_profile'")
            )
            listOf("talent_code", "administrative_titles").forEach { column ->
                assertEquals("utf8mb4", c.queryString(columnMeta("expert_manual_profile", column, "CHARACTER_SET_NAME")))
            }
            mapOf(
                "talent_code" to 64L, "chinese_name" to 200L, "birth_date_text" to 10L, "document_number" to 100L,
                "source_country" to 100L, "school_ranking" to 500L, "organization_ranking" to 500L,
                "postdoc_period" to 200L, "professional_title" to 200L, "industry" to 500L,
                "program_categories" to 500L, "research_ability" to 16L
            ).forEach { (column, limit) ->
                assertEquals("varchar", c.queryString(columnMeta("expert_manual_profile", column, "DATA_TYPE")), column)
                assertEquals(limit.toString(), c.queryString(columnMeta("expert_manual_profile", column, "CHARACTER_MAXIMUM_LENGTH")), column)
                assertEquals("YES", c.queryString(columnMeta("expert_manual_profile", column, "IS_NULLABLE")), column)
            }
            listOf(
                "graduated_school", "pre_china_organization", "postdoc_organization",
                "administrative_titles", "technical_expertise", "matched_companies"
            ).forEach { column ->
                assertEquals("text", c.queryString(columnMeta("expert_manual_profile", column, "DATA_TYPE")), column)
                assertEquals("YES", c.queryString(columnMeta("expert_manual_profile", column, "IS_NULLABLE")), column)
            }
            assertEquals("NO", c.queryString(columnMeta("expert_manual_profile", "company_match_status", "IS_NULLABLE")))
            assertEquals("varchar", c.queryString(columnMeta("expert_manual_profile", "company_match_status", "DATA_TYPE")))
            assertEquals("16", c.queryString(columnMeta("expert_manual_profile", "company_match_status", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("NOT_MATCHED", c.queryString(columnMeta("expert_manual_profile", "company_match_status", "COLUMN_DEFAULT")))
            assertEquals("bigint", c.queryString(columnMeta("expert_manual_profile", "version", "DATA_TYPE")))
            assertEquals("NO", c.queryString(columnMeta("expert_manual_profile", "version", "IS_NULLABLE")))
            assertEquals("0", c.queryString(columnMeta("expert_manual_profile", "version", "COLUMN_DEFAULT")))
            assertEquals("datetime", c.queryString(columnMeta("expert_manual_profile", "updated_at", "DATA_TYPE")))
            assertEquals("3", c.queryString(columnMeta("expert_manual_profile", "updated_at", "DATETIME_PRECISION")))
            assertEquals("NO", c.queryString(columnMeta("expert_manual_profile", "updated_at", "IS_NULLABLE")))
            assertEquals("NO", c.queryString(columnMeta("expert_manual_profile", "updated_by", "IS_NULLABLE")))
            assertEquals("varchar", c.queryString(columnMeta("expert_manual_profile", "updated_by", "DATA_TYPE")))
            assertEquals("64", c.queryString(columnMeta("expert_manual_profile", "updated_by", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals(
                0L,
                c.queryLong(
                    "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() " +
                        "AND table_name='expert_manual_profile' AND constraint_type='FOREIGN KEY'"
                )
            )
            // 无请求的全局人才编号唯一约束不得出现。
            assertEquals(
                0L,
                c.queryLong(
                    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() " +
                        "AND table_name='expert_manual_profile' AND column_name='talent_code' AND non_unique=0"
                )
            )
            // 既有 notes/drafts 数据、外键与唯一键保持不变。
            assertEquals("迁移前备注", c.queryString("SELECT note FROM expert_contact_note WHERE expert_contact_id=1"))
            assertEquals(1L, c.queryLong("SELECT COUNT(*) FROM expert_contact_note"))
            assertTrue(c.foreignKeyExists("expert_contact_note", "fk_expert_contact_note_contact"))
            assertEquals("DRAFT", c.queryString("SELECT state FROM mailbox_reply_draft WHERE username='admin'"))
            assertEquals(1L, c.queryLong("SELECT COUNT(*) FROM mailbox_reply_draft"))
            assertTrue(c.indexExists("mailbox_reply_draft", "uk_mailbox_reply_draft_target"))
            assertEquals(
                contactBefore,
                c.queryString("SELECT CONCAT(id, '|', campaign_id, '|', orcid_id, '|', current_status) FROM expert_contact WHERE id=1")
            )
        }
    }

    @Test
    fun `real manual profile service persists the nineteen fields and preserves unrelated rows on V153`() {
        migrateToV23AndSeedBase()
        flyway().migrate()
        val dataSource = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        val jdbc = NamedParameterJdbcTemplate(dataSource)
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        Mockito.`when`(contacts.existsByOrcidId("0000-0001")).thenReturn(true)
        val resolver = ExpertProfileIdentityResolver(jdbc, contacts, Mockito.mock(ExpertSearchService::class.java))
        val service = ExpertManualProfileService(
            jdbc, resolver, TransactionTemplate(DataSourceTransactionManager(dataSource))
        )
        val contactsBefore = jdbc.queryForList("SELECT * FROM expert_contact ORDER BY id", emptyMap<String, Any?>())
        val mailsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM mail_record", emptyMap<String, Any?>(), Long::class.java)

        assertEquals(ExpertManualProfileDto.empty("0000-0001"), service.get("0000-0001", ExpertIndexLevel.RAW))
        connection().use { assertEquals(0L, it.queryLong("SELECT COUNT(*) FROM expert_manual_profile")) }

        var dto = service.patch(
            ExpertManualProfilePatch(
                "0000-0001", ExpertIndexLevel.RAW, ExpertManualProfileGroup.IDENTITY, 0,
                mapOf(
                    "talentCode" to "G0007", "chineseName" to "测试译名", "birthDateText" to "1980",
                    "documentNumber" to "00001234", "sourceCountry" to "法国"
                )
            ),
            "admin"
        )
        assertEquals(1L, dto.version)
        assertEquals("00001234", dto.identity.documentNumber)
        assertEquals(CompanyMatchStatus.NOT_MATCHED.name, dto.matching.companyMatchStatus)
        assertTrue(dto.matching.matchedCompanies.isEmpty())

        dto = service.patch(
            ExpertManualProfilePatch(
                "0000-0001", ExpertIndexLevel.RAW, ExpertManualProfileGroup.EDUCATION, dto.version,
                mapOf(
                    "graduatedSchool" to "测试大学甲\n测试大学乙", "schoolRanking" to "QS 100 / 学科前列",
                    "preChinaOrganization" to "测试历史单位", "organizationRanking" to "世界500强",
                    "postdocOrganization" to "测试博士后单位", "postdocPeriod" to "2018–2020",
                    "professionalTitle" to "副教授", "administrativeTitles" to "主任\n学会委员"
                )
            ),
            "admin"
        )
        assertEquals(2L, dto.version)
        assertEquals("G0007", dto.identity.talentCode)

        dto = service.patch(
            ExpertManualProfilePatch(
                "0000-0001", ExpertIndexLevel.RAW, ExpertManualProfileGroup.TECHNOLOGY, dto.version,
                mapOf(
                    "industry" to "新能源", "technicalExpertise" to "储能材料\n测试技术",
                    "programCategories" to "QM/BS/工程师/RJ"
                )
            ),
            "admin"
        )
        assertEquals(3L, dto.version)
        assertEquals("测试大学甲\n测试大学乙", dto.education.graduatedSchool)

        dto = service.patch(
            ExpertManualProfilePatch(
                "0000-0001", ExpertIndexLevel.RAW, ExpertManualProfileGroup.MATCHING, dto.version,
                mapOf(
                    "researchAbility" to "STRONG", "companyMatchStatus" to "MATCHED",
                    "matchedCompanies" to listOf("测试企业甲", "测试企业乙")
                )
            ),
            "admin"
        )
        assertEquals(4L, dto.version)
        assertEquals("admin", dto.updatedBy)
        assertTrue(dto.updatedAt!!.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+08:00")))
        assertEquals("新能源", dto.technology.industry)
        assertEquals(listOf("测试企业甲", "测试企业乙"), dto.matching.matchedCompanies)
        assertEquals(dto, service.get("0000-0001", ExpertIndexLevel.CANDIDATE))

        connection().use { c ->
            assertEquals(1L, c.queryLong("SELECT COUNT(*) FROM expert_manual_profile"))
            assertEquals("G0007", c.queryString("SELECT talent_code FROM expert_manual_profile WHERE expert_key='0000-0001'"))
            assertEquals("1980", c.queryString("SELECT birth_date_text FROM expert_manual_profile WHERE expert_key='0000-0001'"))
            assertEquals("00001234", c.queryString("SELECT document_number FROM expert_manual_profile WHERE expert_key='0000-0001'"))
            assertEquals(
                "测试企业甲\n测试企业乙",
                c.queryString("SELECT matched_companies FROM expert_manual_profile WHERE expert_key='0000-0001'")
            )
            assertEquals("MATCHED", c.queryString("SELECT company_match_status FROM expert_manual_profile WHERE expert_key='0000-0001'"))
            assertEquals("4", c.queryString("SELECT version FROM expert_manual_profile WHERE expert_key='0000-0001'"))
            assertEquals("admin", c.queryString("SELECT updated_by FROM expert_manual_profile WHERE expert_key='0000-0001'"))
            assertTrue(
                c.queryString("SELECT DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') FROM expert_manual_profile WHERE expert_key='0000-0001'")
                    .startsWith(dto.updatedAt!!.substringBefore("+08:00").replace('T', ' '))
            )
        }
        assertEquals(contactsBefore, jdbc.queryForList("SELECT * FROM expert_contact ORDER BY id", emptyMap<String, Any?>()))
        assertEquals(
            mailsBefore,
            jdbc.queryForObject("SELECT COUNT(*) FROM mail_record", emptyMap<String, Any?>(), Long::class.java)
        )

        // 同一专家可同时存在于多个 campaign：档案按 expertId 归属，不按 contactId（K-bound-expert-cross-campaign-read）。
        connection().use { c ->
            c.execute("INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) VALUES (5, 'PROFILE_MULTI', 'Profile Multi', 1)")
            c.execute("INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, current_status) VALUES (5, 5, '0000-0001', 'one@example.com', 'NEW')")
        }
        val beforeMultiCampaign = service.get("0000-0001", ExpertIndexLevel.RAW)
        val multiCampaign = service.patch(
            ExpertManualProfilePatch(
                "0000-0001", ExpertIndexLevel.RAW, ExpertManualProfileGroup.IDENTITY, beforeMultiCampaign.version,
                mapOf(
                    "talentCode" to "MULTI", "chineseName" to null, "birthDateText" to null,
                    "documentNumber" to null, "sourceCountry" to null
                )
            ),
            "admin"
        )
        assertEquals(beforeMultiCampaign.version + 1, multiCampaign.version)
        connection().use { c ->
            assertEquals(1L, c.queryLong("SELECT COUNT(*) FROM expert_manual_profile"))
            assertEquals("MULTI", c.queryString("SELECT talent_code FROM expert_manual_profile WHERE expert_key='0000-0001'"))
            assertEquals(2L, c.queryLong("SELECT COUNT(*) FROM expert_contact WHERE orcid_id='0000-0001'"))
        }

        // I-3：失败事务不改任何字段（事务内的写入随回滚消失）。
        val beforeRollback = service.get("0000-0001", ExpertIndexLevel.RAW)
        TransactionTemplate(DataSourceTransactionManager(dataSource)).execute { status ->
            service.patch(
                ExpertManualProfilePatch(
                    "0000-0001", ExpertIndexLevel.RAW, ExpertManualProfileGroup.IDENTITY, beforeRollback.version,
                    mapOf(
                        "talentCode" to "ROLLBACK", "chineseName" to null, "birthDateText" to null,
                        "documentNumber" to null, "sourceCountry" to null
                    )
                ),
                "admin"
            )
            status.setRollbackOnly()
        }
        assertEquals(beforeRollback, service.get("0000-0001", ExpertIndexLevel.RAW))
    }

    @Test
    fun `manual profile service settles concurrent first insert and same version update on V153`() {
        migrateToV23AndSeedBase()
        flyway().migrate()
        val dataSource = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        val jdbc = NamedParameterJdbcTemplate(dataSource)
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        Mockito.`when`(contacts.existsByOrcidId("0000-0001")).thenReturn(true)
        val resolver = ExpertProfileIdentityResolver(jdbc, contacts, Mockito.mock(ExpertSearchService::class.java))
        val service = ExpertManualProfileService(
            jdbc, resolver, TransactionTemplate(DataSourceTransactionManager(dataSource))
        )
        val tx = TransactionTemplate(DataSourceTransactionManager(dataSource))
        fun identityValues(talentCode: String) = mapOf(
            "talentCode" to talentCode, "chineseName" to null, "birthDateText" to null,
            "documentNumber" to null, "sourceCountry" to null
        )

        val pool = Executors.newFixedThreadPool(2)
        try {
            // 两连接同时以 expectedVersion=0 首次插入同一 key：恰好一个成功、一个 409、只落一行。
            val insertBarrier = CyclicBarrier(2)
            val inserts = listOf("userA", "userB").map { actor ->
                pool.submit(Callable<Any> {
                    insertBarrier.await()
                    try {
                        tx.execute {
                            service.patch(
                                ExpertManualProfilePatch(
                                    "0000-0001", ExpertIndexLevel.RAW, ExpertManualProfileGroup.IDENTITY, 0,
                                    identityValues(actor)
                                ),
                                actor
                            )
                        }!!
                    } catch (ex: ExpertProfileConflictException) {
                        ex
                    }
                })
            }.map { it.get() }
            assertEquals(1, inserts.count { it is ExpertManualProfileDto }, "并发首次插入必须恰好一个成功")
            assertEquals(1, inserts.count { it is ExpertProfileConflictException }, "并发首次插入必须恰好一个 409")
            connection().use { c ->
                assertEquals(1L, c.queryLong("SELECT COUNT(*) FROM expert_manual_profile WHERE expert_key='0000-0001'"))
                assertEquals("1", c.queryString("SELECT version FROM expert_manual_profile WHERE expert_key='0000-0001'"))
            }
            val inserted = inserts.filterIsInstance<ExpertManualProfileDto>().single()
            assertEquals(inserted.identity.talentCode, service.get("0000-0001", ExpertIndexLevel.RAW).identity.talentCode)

            // 两条连接持同一 version 更新：一个成功、一个 409，且赢家字段与 version 落库。
            val updateBarrier = CyclicBarrier(2)
            val updates = listOf("userA" to "甲产业", "userB" to "乙产业").map { (actor, industry) ->
                pool.submit(Callable<Any> {
                    updateBarrier.await()
                    try {
                        tx.execute {
                            service.patch(
                                ExpertManualProfilePatch(
                                    "0000-0001", ExpertIndexLevel.RAW, ExpertManualProfileGroup.TECHNOLOGY,
                                    inserted.version,
                                    mapOf(
                                        "industry" to industry, "technicalExpertise" to null,
                                        "programCategories" to null
                                    )
                                ),
                                actor
                            )
                        }!!
                    } catch (ex: ExpertProfileConflictException) {
                        ex
                    }
                })
            }.map { it.get() }
            assertEquals(1, updates.count { it is ExpertManualProfileDto }, "同 version 更新必须恰好一个成功")
            assertEquals(1, updates.count { it is ExpertProfileConflictException }, "同 version 更新必须恰好一个 409")
            val winner = updates.filterIsInstance<ExpertManualProfileDto>().single()
            assertEquals(inserted.version + 1, winner.version)
            connection().use { c ->
                assertEquals(winner.technology.industry, c.queryString("SELECT industry FROM expert_manual_profile WHERE expert_key='0000-0001'"))
                assertEquals(winner.identity.talentCode, c.queryString("SELECT talent_code FROM expert_manual_profile WHERE expert_key='0000-0001'"))
                assertEquals(
                    winner.version.toString(),
                    c.queryString("SELECT version FROM expert_manual_profile WHERE expert_key='0000-0001'")
                )
            }
            assertEquals(winner, service.get("0000-0001", ExpertIndexLevel.RAW))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `V154 creates the expert meeting video table preserving profile and note rows`() {
        migrateToV23AndSeedBase()
        assertEquals("153", flyway(MigrationVersion.fromVersion("153")).migrate().targetSchemaVersion)
        connection().use { c ->
            assertFalse(c.tableExists("expert_meeting_video"))
            c.execute(
                "INSERT INTO expert_manual_profile (expert_key, updated_by, updated_at) " +
                    "VALUES ('0000-0001', 'admin', '2026-10-11 10:18:00.123')"
            )
            c.execute(
                "INSERT INTO expert_contact_note (expert_contact_id, note, updated_by, updated_at) " +
                    "VALUES (1, '迁移前备注', 'admin', '2026-10-11 10:18:00.123')"
            )
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { c ->
            assertTrue(c.tableExists("expert_meeting_video"))
            // 迁移本身不写业务数据；V154 建 10 列，V155 再补 meeting_record_id，全量迁移后共 11 列。
            assertEquals(0L, c.queryLong("SELECT COUNT(*) FROM expert_meeting_video"))
            listOf(
                "id", "expert_key", "original_filename", "display_filename", "byte_length",
                "uploaded_by", "uploaded_at", "updated_by", "updated_at", "version",
                "meeting_record_id"
            ).forEach { column ->
                assertTrue(c.columnExists("expert_meeting_video", column), "缺少列 $column")
            }
            assertEquals(
                11L,
                c.queryLong(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema=DATABASE() AND table_name='expert_meeting_video'"
                )
            )
            // id 是服务端 UUID：ASCII 定长 36，磁盘路径也只用它。
            assertEquals("char", c.queryString(columnMeta("expert_meeting_video", "id", "DATA_TYPE")))
            assertEquals("36", c.queryString(columnMeta("expert_meeting_video", "id", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("ascii", c.queryString(columnMeta("expert_meeting_video", "id", "CHARACTER_SET_NAME")))
            assertEquals("NO", c.queryString(columnMeta("expert_meeting_video", "id", "IS_NULLABLE")))
            // 身份与展示名大小写敏感；原始名 255、展示名 100（完整名含扩展名）。
            assertEquals("varchar", c.queryString(columnMeta("expert_meeting_video", "expert_key", "DATA_TYPE")))
            assertEquals("64", c.queryString(columnMeta("expert_meeting_video", "expert_key", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("utf8mb4_bin", c.queryString(columnMeta("expert_meeting_video", "expert_key", "COLLATION_NAME")))
            assertEquals("varchar", c.queryString(columnMeta("expert_meeting_video", "original_filename", "DATA_TYPE")))
            assertEquals(
                "255",
                c.queryString(columnMeta("expert_meeting_video", "original_filename", "CHARACTER_MAXIMUM_LENGTH"))
            )
            assertEquals("NO", c.queryString(columnMeta("expert_meeting_video", "original_filename", "IS_NULLABLE")))
            assertEquals(
                "100",
                c.queryString(columnMeta("expert_meeting_video", "display_filename", "CHARACTER_MAXIMUM_LENGTH"))
            )
            assertEquals(
                "utf8mb4_bin",
                c.queryString(columnMeta("expert_meeting_video", "display_filename", "COLLATION_NAME"))
            )
            assertEquals("bigint", c.queryString(columnMeta("expert_meeting_video", "byte_length", "DATA_TYPE")))
            assertEquals("NO", c.queryString(columnMeta("expert_meeting_video", "byte_length", "IS_NULLABLE")))
            listOf("uploaded_by", "updated_by").forEach { column ->
                assertEquals("varchar", c.queryString(columnMeta("expert_meeting_video", column, "DATA_TYPE")), column)
                assertEquals(
                    "64",
                    c.queryString(columnMeta("expert_meeting_video", column, "CHARACTER_MAXIMUM_LENGTH")),
                    column
                )
                assertEquals("NO", c.queryString(columnMeta("expert_meeting_video", column, "IS_NULLABLE")), column)
            }
            listOf("uploaded_at", "updated_at").forEach { column ->
                assertEquals("datetime", c.queryString(columnMeta("expert_meeting_video", column, "DATA_TYPE")), column)
                assertEquals(
                    "3",
                    c.queryString(columnMeta("expert_meeting_video", column, "DATETIME_PRECISION")),
                    column
                )
                assertEquals("NO", c.queryString(columnMeta("expert_meeting_video", column, "IS_NULLABLE")), column)
            }
            assertEquals("bigint", c.queryString(columnMeta("expert_meeting_video", "version", "DATA_TYPE")))
            assertEquals("NO", c.queryString(columnMeta("expert_meeting_video", "version", "IS_NULLABLE")))
            assertEquals("1", c.queryString(columnMeta("expert_meeting_video", "version", "COLUMN_DEFAULT")))
            // 主键 id、同一专家内展示名唯一（最终裁决）、列表索引 (expert_key, uploaded_at, id)。
            assertEquals(
                1L,
                c.queryLong(
                    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() " +
                        "AND table_name='expert_meeting_video' AND index_name='PRIMARY' " +
                        "AND column_name='id' AND non_unique=0"
                )
            )
            assertEquals(
                2L,
                c.queryLong(
                    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() " +
                        "AND table_name='expert_meeting_video' AND index_name='uk_expert_meeting_video_name' " +
                        "AND non_unique=0 AND column_name IN ('expert_key','display_filename')"
                )
            )
            assertEquals(
                3L,
                c.queryLong(
                    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() " +
                        "AND table_name='expert_meeting_video' AND index_name='idx_expert_meeting_video_list' " +
                        "AND non_unique=1 AND column_name IN ('expert_key','uploaded_at','id')"
                )
            )
            assertEquals(
                "InnoDB",
                c.queryString(
                    "SELECT ENGINE FROM information_schema.tables " +
                        "WHERE table_schema=DATABASE() AND table_name='expert_meeting_video'"
                )
            )
            // V154 不建 contact 外键（未联系专家也可上传）；V155 追加的唯一外键是指向
            // expert_meeting_record 的 fk_video_meeting_owner，不得有指向 expert_contact 的外键。
            assertTrue(c.foreignKeyExists("expert_meeting_video", "fk_video_meeting_owner"))
            assertEquals(
                1L,
                c.queryLong(
                    "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() " +
                        "AND table_name='expert_meeting_video' AND constraint_type='FOREIGN KEY'"
                )
            )
            assertEquals(
                0L,
                c.queryLong(
                    "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() " +
                        "AND table_name='expert_meeting_video' AND constraint_type='FOREIGN KEY' " +
                        "AND constraint_name <> 'fk_video_meeting_owner'"
                )
            )
            // 既有数据保持不变。
            assertEquals(
                1L,
                c.queryLong("SELECT COUNT(*) FROM expert_manual_profile WHERE expert_key='0000-0001'")
            )
            assertEquals("迁移前备注", c.queryString("SELECT note FROM expert_contact_note WHERE expert_contact_id=1"))
        }
    }

    @Test
    fun `real meeting video service settles concurrent same name uploads and stale renames on V154`() {
        migrateToV23AndSeedBase()
        flyway().migrate()
        val dataSource = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        val jdbc = NamedParameterJdbcTemplate(dataSource)
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        Mockito.`when`(contacts.existsByOrcidId("0000-0001")).thenReturn(true)
        Mockito.`when`(contacts.existsByOrcidId("0000-0002")).thenReturn(true)
        val resolver = ExpertProfileIdentityResolver(jdbc, contacts, Mockito.mock(ExpertSearchService::class.java))
        val storageDir = Files.createTempDirectory("meeting-video-v154-it-")
        val videoRoot = storageDir.resolve("meeting-videos")
        val service = ExpertMeetingVideoService(
            MailAttachmentStorageProperties(basePath = storageDir.toString()),
            resolver,
            jdbc,
            TransactionTemplate(DataSourceTransactionManager(dataSource))
        )
        fun upload(expertId: String, stem: String, actor: String) = service.upload(
            MeetingVideoUploadRequest(expertId, ExpertIndexLevel.RAW, "企业对接会.mp4", stem, "3", 3L),
            actor,
            "",
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        )

        val pool = Executors.newFixedThreadPool(2)
        try {
            // 同一专家、同名并发上传：唯一索引最终裁决，恰好一条成功、一条 409，只落一行只留一个文件。
            val insertBarrier = CyclicBarrier(2)
            val inserts = listOf("userA", "userB").map { actor ->
                pool.submit(Callable<Any> {
                    insertBarrier.await()
                    try {
                        upload("0000-0001", "企业对接会", actor)
                    } catch (ex: MeetingVideoException) {
                        ex
                    }
                })
            }.map { it.get() }
            assertEquals(1, inserts.count { it is MeetingVideoView }, "并发同名上传必须恰好一个成功")
            assertEquals(
                1,
                inserts.count { it is MeetingVideoException && it.code == "VIDEO_NAME_CONFLICT" },
                "并发同名上传必须恰好一个 409 VIDEO_NAME_CONFLICT：$inserts"
            )
            val winner = inserts.filterIsInstance<MeetingVideoView>().single()
            connection().use { c ->
                assertEquals(
                    1L,
                    c.queryLong("SELECT COUNT(*) FROM expert_meeting_video WHERE expert_key='0000-0001'")
                )
                assertEquals("1", c.queryString("SELECT version FROM expert_meeting_video WHERE id='${winner.id}'"))
                assertEquals(
                    "企业对接会.mp4",
                    c.queryString("SELECT display_filename FROM expert_meeting_video WHERE id='${winner.id}'")
                )
            }
            // 输家的 final 文件必须被删除：目录里只剩赢家一个文件。
            assertEquals(
                listOf(winner.id),
                Files.list(videoRoot).use { stream -> stream.map { it.fileName.toString() }.toList() }
            )

            // 跨专家同名允许；同一专家内大小写不同是不同名称（utf8mb4_bin）。
            val otherExpert = upload("0000-0002", "企业对接会", "userC")
            assertEquals("企业对接会.mp4", otherExpert.displayFilename)
            assertEquals("abc.mp4", upload("0000-0001", "abc", "userA").displayFilename)
            assertEquals("ABC.mp4", upload("0000-0001", "ABC", "userA").displayFilename)
            assertEquals(
                3L,
                connection().use { c ->
                    c.queryLong("SELECT COUNT(*) FROM expert_meeting_video WHERE expert_key='0000-0001'")
                }
            )

            // 同版本并发重命名：CAS 恰好一条成功、一条 409 VIDEO_VERSION_CONFLICT。
            val renameBarrier = CyclicBarrier(2)
            val renames = listOf("新名字甲", "新名字乙").map { stem ->
                pool.submit(Callable<Any> {
                    renameBarrier.await()
                    try {
                        service.rename(
                            MeetingVideoRenameRequest(stem, winner.version), "0000-0001", winner.id, "userA", ""
                        )
                    } catch (ex: MeetingVideoException) {
                        ex
                    }
                })
            }.map { it.get() }
            assertEquals(1, renames.count { it is MeetingVideoView }, "同版本重命名必须恰好一个成功")
            assertEquals(
                1,
                renames.count { it is MeetingVideoException && it.code == "VIDEO_VERSION_CONFLICT" },
                "同版本重命名必须恰好一个 409 VIDEO_VERSION_CONFLICT：$renames"
            )
            connection().use { c ->
                assertEquals("2", c.queryString("SELECT version FROM expert_meeting_video WHERE id='${winner.id}'"))
            }
            // 旧版本再次重命名仍然 409，且不搬动磁盘文件。
            val stale = assertThrows(MeetingVideoException::class.java) {
                service.rename(
                    MeetingVideoRenameRequest("再一次", winner.version), "0000-0001", winner.id, "userA", ""
                )
            }
            assertEquals("VIDEO_VERSION_CONFLICT", stale.code)
            assertTrue(Files.exists(videoRoot.resolve(winner.id)))
            // 归属不符：另一个专家改不动这条视频。
            val crossOwner = assertThrows(MeetingVideoException::class.java) {
                service.rename(MeetingVideoRenameRequest("别人的名字", 2L), "0000-0002", winner.id, "userC", "")
            }
            assertEquals("VIDEO_NOT_FOUND", crossOwner.code)
        } finally {
            pool.shutdownNow()
            if (Files.exists(storageDir)) {
                Files.walk(storageDir).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }

    @Test
    fun `V141 upgrades V140 history without backfilling and enforces unique association`() {
        migrateToV23AndSeedBase()
        assertEquals("140", flyway(MigrationVersion.fromVersion("140")).migrate().targetSchemaVersion)
        connection().use { c ->
            c.execute("INSERT INTO mail_record (id, expert_contact_id, direction, mail_type, send_status, sent_at) VALUES (801, 1, 'OUTBOUND', 'INTRODUCTION', 'SENT', NOW()), (802, 2, 'OUTBOUND', 'INTRODUCTION', 'SENT', NOW())")
        }
        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { c ->
            assertTrue(c.tableExists("mail_open_tracking"))
            assertTrue(c.columnExists("mail_record", "open_tracking_id"))
            assertTrue(c.indexExists("mail_record", "uq_mail_record_open_tracking"))
            assertTrue(c.indexExists("mail_open_tracking", "uq_mail_open_tracking_token"))
            assertTrue(c.foreignKeyExists("mail_record", "fk_mail_record_open_tracking"))
            assertEquals(2L, c.queryLong("SELECT COUNT(*) FROM mail_record WHERE id IN (801,802) AND open_tracking_id IS NULL"))
            c.execute("INSERT INTO mail_open_tracking (id, token, recipient, created_at) VALUES (901, 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', 'one@example.com', NOW())")
            c.execute("UPDATE mail_record SET open_tracking_id=901 WHERE id=801")
            assertThrows(SQLException::class.java) { c.execute("UPDATE mail_record SET open_tracking_id=901 WHERE id=802") }
            assertThrows(SQLException::class.java) { c.execute("UPDATE mail_record SET open_tracking_id=999999 WHERE id=802") }
            c.execute("INSERT INTO mail_open_tracking (token, recipient, created_at) VALUES ('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'two@example.com', NOW())")
            assertEquals(2L, c.queryLong("SELECT COUNT(*) FROM mail_open_tracking"))
        }
    }
    @Test
    fun `V136 preserves existing template subject and initializes reference as null`() {
        val v135 = flyway(MigrationVersion.fromVersion("135"))
        v135.clean()
        assertEquals("135", v135.migrate().targetSchemaVersion)
        val templateId = connection().use { connection ->
            connection.execute(
                """
                INSERT INTO mail_compose_template (template_code, template_name, subject, enabled)
                VALUES ('V136_TEST', 'V136 legacy subject', 'Old subject', 1)
                """
            )
            connection.queryLong(
                "SELECT id FROM mail_compose_template WHERE template_code = 'V136_TEST'"
            )
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            assertTrue(connection.columnExists("mail_compose_template", "subject_snippet_id"))
            assertEquals(
                "Old subject",
                connection.queryString("SELECT subject FROM mail_compose_template WHERE id = $templateId")
            )
            assertEquals(
                0L,
                connection.queryLong(
                    "SELECT COUNT(*) FROM mail_compose_template " +
                        "WHERE id = $templateId AND subject_snippet_id IS NOT NULL"
                )
            )
        }
    }

    @Test
    fun `V125 creates the structured calendar table and constraints`() {
        val flyway = flyway()
        flyway.clean()
        flyway.migrate()
        connection().use { connection ->
            assertTrue(connection.tableExists("meeting_calendar_event"))
            assertEquals("datetime", connection.queryString(
                "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() " +
                    "AND table_name = 'meeting_calendar_event' AND column_name = 'starts_at_utc'"
            ))
            assertEquals("6", connection.queryString(
                "SELECT DATETIME_PRECISION FROM information_schema.columns WHERE table_schema = DATABASE() " +
                    "AND table_name = 'meeting_calendar_event' AND column_name = 'starts_at_utc'"
            ))
            assertEquals("200", connection.queryString(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() " +
                    "AND table_name = 'meeting_calendar_event' AND column_name = 'note'"
            ))
            assertTrue(connection.indexExists("meeting_calendar_event", "uk_meeting_calendar_source_mail"))
            assertTrue(connection.indexExists("meeting_calendar_event", "idx_meeting_calendar_status_start_id"))
            assertTrue(connection.indexExists("meeting_calendar_event", "idx_meeting_calendar_contact_status_start_id"))
            assertTrue(connection.foreignKeyExists("meeting_calendar_event", "fk_meeting_calendar_contact"))
            assertTrue(connection.foreignKeyExists("meeting_calendar_event", "fk_meeting_calendar_source_mail"))
        }
    }

    @Test
    fun `V126 creates outbound_mail_attachment with the persistence contract`() {
        val flyway = flyway()
        flyway.clean()
        flyway.migrate()
        connection().use { connection ->
            assertTrue(connection.tableExists("outbound_mail_attachment"))
            // 列契约：id CHAR(36) 主键；上传者/文件名/类型列宽；原件真实字节数与摘要列。
            assertEquals("char", connection.queryString(outboundAttachmentColumnMeta("id", "DATA_TYPE")))
            assertEquals("36", connection.queryString(outboundAttachmentColumnMeta("id", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("100", connection.queryString(outboundAttachmentColumnMeta("created_by", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("255", connection.queryString(outboundAttachmentColumnMeta("file_name", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("255", connection.queryString(outboundAttachmentColumnMeta("content_type", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("bigint", connection.queryString(outboundAttachmentColumnMeta("byte_length", "DATA_TYPE")))
            assertEquals("char", connection.queryString(outboundAttachmentColumnMeta("sha256", "DATA_TYPE")))
            assertEquals("64", connection.queryString(outboundAttachmentColumnMeta("sha256", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("datetime", connection.queryString(outboundAttachmentColumnMeta("created_at", "DATA_TYPE")))
            assertEquals("6", connection.queryString(outboundAttachmentColumnMeta("created_at", "DATETIME_PRECISION")))
            assertTrue(connection.indexExists("outbound_mail_attachment", "PRIMARY"))
            assertTrue(connection.indexExists("outbound_mail_attachment", "idx_outbound_mail_attachment_contact"))
            assertTrue(connection.foreignKeyExists("outbound_mail_attachment", "fk_outbound_mail_attachment_contact"))
            // I-1：新表是新增的，不触碰既有附件/材料表。
            assertTrue(connection.tableExists("mail_attachment"))
            assertTrue(connection.tableExists("expert_document"))
            assertEquals(0L, connection.queryLong("SELECT COUNT(*) FROM outbound_mail_attachment"))
        }
    }

    @Test
    fun `V127 adds nullable outbound attachments json column leaving history null`() {
        migrateToV23AndSeedBase()
        // V127 之前的旧行：无该列，随迁移升到最新版本。
        connection().use { connection ->
            connection.execute(
                """
                INSERT INTO mail_record
                    (id, expert_contact_id, direction, mail_type, message_id, send_status, sent_at)
                VALUES
                    (203, 1, 'OUTBOUND', 'MANUAL_RICH_REPLY', 'historical-3', 'SENT', '2026-09-02 10:00:00'),
                    (204, 1, 'OUTBOUND', 'MANUAL_RICH_REPLY', 'historical-4', 'FAILED', NULL)
                """
            )
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            // 列契约：outbound_attachments_json LONGTEXT NULL（I-1 唯一 absence 形态）。
            assertTrue(connection.columnExists("mail_record", "outbound_attachments_json"))
            assertEquals("longtext", connection.queryString(
                "SELECT DATA_TYPE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'mail_record' " +
                    "AND column_name = 'outbound_attachments_json'"
            ))
            assertEquals("YES", connection.queryString(
                "SELECT IS_NULLABLE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'mail_record' " +
                    "AND column_name = 'outbound_attachments_json'"
            ))
            // 历史行不回填：旧行保持 NULL（不默认填 [] / 空串 / 假快照），
            // 既有 calendar 列也不受影响。
            assertEquals(2L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_record WHERE id IN (203, 204) AND outbound_attachments_json IS NULL"
            ))
            assertEquals(0L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_record WHERE outbound_attachments_json IS NOT NULL"
            ))
            assertEquals(2L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_record WHERE id IN (203, 204) AND calendar_attachment_json IS NULL"
            ))
            // 新列可写规范快照 JSON 数组（应用层 codec 才是校验方；本测试只验证列可存）。
            connection.execute(
                "UPDATE mail_record SET outbound_attachments_json = " +
                    "'[{\"schemaVersion\":1,\"id\":\"c8f0c0de-0000-4000-8000-000000000001\"," +
                    "\"filename\":\"a.txt\",\"contentType\":\"text/plain\"," +
                    "\"byteLength\":1,\"sha256\":\"" + "a".repeat(64) + "\"}]' " +
                    "WHERE id = 203"
            )
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_record WHERE id = 203 AND outbound_attachments_json IS NOT NULL"
            ))
        }
    }

    @Test
    fun `V129 widens the material code check to twelve codes without touching stored rows`() {
        migrateToV23AndSeedBase()
        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            // 零数据改写：V129 不建初始行。
            assertEquals(0L, connection.queryLong("SELECT COUNT(*) FROM expert_material_status"))

            // 约束名与状态域/唯一键不变（I-2）。
            assertTrue(connection.checkConstraintExists("expert_material_status", "chk_expert_material_code"))
            assertTrue(connection.checkConstraintExists("expert_material_status", "chk_expert_material_status"))
            assertTrue(connection.indexExists("expert_material_status", "uk_expert_material_contact_code"))

            val legacyCodes =
                listOf("CV", "PASSPORT", "DEGREE", "EMPLOYMENT", "PUBLICATIONS", "PATENTS", "RESEARCH")
            val requestCodes =
                listOf("REQ_PUBLICATIONS", "REQ_PROJECTS", "REQ_PATENTS", "REQ_AWARDS", "REQ_DEGREES")

            // 旧 7 代码与新 5 代码都可写（V129 只扩大域，不删旧代码）。
            legacyCodes.forEach { code ->
                connection.execute(
                    "INSERT INTO expert_material_status (expert_contact_id, material_code, material_status) " +
                        "VALUES (1, '$code', 'PROVIDED')"
                )
            }
            requestCodes.forEach { code ->
                connection.execute(
                    "INSERT INTO expert_material_status (expert_contact_id, material_code, material_status) " +
                        "VALUES (1, '$code', 'DECLINED')"
                )
            }
            assertEquals(12L, connection.queryLong("SELECT COUNT(*) FROM expert_material_status"))

            // 域外代码仍被拒绝。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    "INSERT INTO expert_material_status (expert_contact_id, material_code, material_status) " +
                        "VALUES (2, 'REQ_UNKNOWN', 'PROVIDED')"
                )
            }
            // 存储态仍严格两值：PENDING 不落库（缺行才是 PENDING）。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    "INSERT INTO expert_material_status (expert_contact_id, material_code, material_status) " +
                        "VALUES (2, 'REQ_PROJECTS', 'PENDING')"
                )
            }
            // 唯一键仍是 (expert_contact_id, material_code)：同联系人同代码第二行被拒。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    "INSERT INTO expert_material_status (expert_contact_id, material_code, material_status) " +
                        "VALUES (1, 'REQ_DEGREES', 'PROVIDED')"
                )
            }
            assertEquals(12L, connection.queryLong("SELECT COUNT(*) FROM expert_material_status"))
        }
    }

    @Test
    fun `V130 adds the manual upload owner table switching the owner check to three-way`() {
        migrateToV23AndSeedBase()
        connection().use { connection ->
            // 迁移前（V23 schema）的历史来信附件：mail_record owner（此时尚无 processing owner 列）。
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
        }
        // 升到 V36（inbound_processing_id owner 落地）再补一条 processing owner 历史行，
        // 两类历史 owner 行必须在 V130 后都原样保留。
        assertEquals("36", flyway(MigrationVersion.fromVersion("36")).migrate().targetSchemaVersion)
        connection().use { connection ->
            connection.execute(
                """
                INSERT INTO inbound_mail_processing
                    (id, sender_account_code, imap_uid, message_id, from_email, subject,
                     received_at, process_status, process_reason, expert_contact_id)
                VALUES (501, 'sender', 501, 'msg-501', 'two@example.com', 'Re: materials',
                        '2026-09-01 09:00:00', 'MANUAL_REVIEW', 'QA_NO_MATCH', 1)
                """
            )
            connection.execute(
                """
                INSERT INTO mail_attachment
                    (id, inbound_processing_id, file_name, content_type, file_size, storage_path)
                VALUES (302, 501, 'unmatched.pdf', 'application/pdf', 99, '/attachments/unmatched/302.pdf')
                """
            )
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            // 新表列/索引/外键契约。
            assertTrue(connection.tableExists("manual_expert_material_upload"))
            assertEquals(
                "bigint",
                connection.queryString(columnMeta("manual_expert_material_upload", "id", "DATA_TYPE"))
            )
            assertEquals(
                "bigint",
                connection.queryString(columnMeta("manual_expert_material_upload", "expert_contact_id", "DATA_TYPE"))
            )
            assertEquals(
                "NO",
                connection.queryString(columnMeta("manual_expert_material_upload", "uploaded_by", "IS_NULLABLE"))
            )
            assertEquals(
                "100",
                connection.queryString(
                    columnMeta("manual_expert_material_upload", "uploaded_by", "CHARACTER_MAXIMUM_LENGTH")
                )
            )
            assertEquals(
                "datetime",
                connection.queryString(columnMeta("manual_expert_material_upload", "created_at", "DATA_TYPE"))
            )
            assertEquals(
                "6",
                connection.queryString(columnMeta("manual_expert_material_upload", "created_at", "DATETIME_PRECISION"))
            )
            assertTrue(connection.indexExists("manual_expert_material_upload", "PRIMARY"))
            assertTrue(connection.indexExists("manual_expert_material_upload", "idx_manual_material_upload_contact"))
            assertTrue(
                connection.foreignKeyExists("manual_expert_material_upload", "fk_manual_material_upload_contact")
            )

            // mail_attachment 新列：可空 + 唯一 + 外键（唯一索引允许多个 NULL）。
            assertTrue(connection.columnExists("mail_attachment", "manual_upload_id"))
            assertEquals(
                "YES",
                connection.queryString(columnMeta("mail_attachment", "manual_upload_id", "IS_NULLABLE"))
            )
            assertTrue(connection.indexExists("mail_attachment", "uk_mail_attachment_manual_upload"))
            assertTrue(connection.foreignKeyExists("mail_attachment", "fk_mail_attachment_manual_upload"))

            // 历史两类 owner 行零改写：owner 原样、manual_upload_id 保持 NULL、真实值不变。
            assertEquals(
                1L,
                connection.queryLong(
                    "SELECT COUNT(*) FROM mail_attachment " +
                        "WHERE id = 301 AND mail_record_id = 201 AND manual_upload_id IS NULL"
                )
            )
            assertEquals(
                1L,
                connection.queryLong(
                    "SELECT COUNT(*) FROM mail_attachment " +
                        "WHERE id = 302 AND inbound_processing_id = 501 AND manual_upload_id IS NULL"
                )
            )
            assertEquals(12345L, connection.queryLong("SELECT file_size FROM mail_attachment WHERE id = 301"))
            assertEquals(
                "/attachments/unmatched/302.pdf",
                connection.queryString("SELECT storage_path FROM mail_attachment WHERE id = 302")
            )

            // 三选一 CHECK 存在（MySQL 8 真实生效；5.7 忽略但应用层仍显式构造三选一）。
            assertTrue(connection.checkConstraintExists("mail_attachment", "chk_mail_attachment_owner"))

            // 合法：手动 owner 行 = 上传来源行 + 恰好一个 manual owner 附件。
            connection.execute(
                """
                INSERT INTO manual_expert_material_upload (id, expert_contact_id, uploaded_by, created_at)
                VALUES (601, 1, 'op1', '2026-09-20 10:00:00')
                """
            )
            connection.execute(
                """
                INSERT INTO mail_attachment
                    (manual_upload_id, file_name, content_type, file_size, storage_path)
                VALUES (601, 'manual-cv.pdf', 'application/pdf', 0, '/attachments/manual/601')
                """
            )
            assertEquals(
                1L,
                connection.queryLong("SELECT COUNT(*) FROM mail_attachment WHERE manual_upload_id = 601")
            )
            // 真实 0 字节手动材料（file_size = 0 且路径非空）是合法数据。
            assertEquals(
                1L,
                connection.queryLong(
                    "SELECT COUNT(*) FROM mail_attachment " +
                        "WHERE manual_upload_id = 601 AND file_size = 0 AND storage_path IS NOT NULL"
                )
            )

            // 零 owner / 双 owner / 三 owner 一律拒绝（I-1）。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_attachment (file_name, content_type)
                    VALUES ('no-owner.pdf', 'application/pdf')
                    """
                )
            }
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_attachment (mail_record_id, manual_upload_id, file_name, content_type)
                    VALUES (201, 601, 'two-owner.pdf', 'application/pdf')
                    """
                )
            }
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_attachment
                        (mail_record_id, inbound_processing_id, manual_upload_id, file_name, content_type)
                    VALUES (201, 501, 601, 'three-owner.pdf', 'application/pdf')
                    """
                )
            }

            // 外键：附件不能指向不存在的上传行；上传行不能指向不存在的专家。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_attachment (manual_upload_id, file_name, content_type)
                    VALUES (999999, 'ghost.pdf', 'application/pdf')
                    """
                )
            }
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO manual_expert_material_upload (expert_contact_id, uploaded_by, created_at)
                    VALUES (999999, 'op1', '2026-09-20 10:00:00')
                    """
                )
            }
            // 唯一：同一个上传行不能挂两个附件。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO mail_attachment (manual_upload_id, file_name, content_type)
                    VALUES (601, 'dup.pdf', 'application/pdf')
                    """
                )
            }

            // 全部被拒后行数不变：3 条附件（两条历史 + 一条手动）、1 条上传来源。
            assertEquals(3L, connection.queryLong("SELECT COUNT(*) FROM mail_attachment"))
            assertEquals(1L, connection.queryLong("SELECT COUNT(*) FROM manual_expert_material_upload"))
        }
    }

    @Test
    fun `V131 creates the enrichment job table on a V130 database leaving existing contacts untouched`() {
        // A-3/I-4：库停在 V130（含原有联系人）再升到 V131；新表存在、历史行不变、迁移记录只新增一个版本。
        migrateToV23AndSeedBase()
        assertEquals("130", flyway(MigrationVersion.fromVersion("130")).migrate().targetSchemaVersion)
        val contactsBefore = connection().use { connection ->
            assertFalse(connection.tableExists("expert_academic_enrichment_job"))
            connection.queryLong("SELECT COUNT(*) FROM expert_contact")
        }
        val historyBefore = connection().use { it.queryLong("SELECT COUNT(*) FROM flyway_schema_history") }

        // V130 → V131 only adds one migration record; later migrations must not affect this assertion.
        assertEquals("131", flyway(MigrationVersion.fromVersion("131")).migrate().targetSchemaVersion)
        connection().use { connection ->
            assertEquals(historyBefore + 1, connection.queryLong("SELECT COUNT(*) FROM flyway_schema_history"))
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)

        connection().use { connection ->
            assertTrue(connection.tableExists("expert_academic_enrichment_job"))
            assertEquals(contactsBefore, connection.queryLong("SELECT COUNT(*) FROM expert_contact"))

            // 列契约（I-1/I-2/I-3）：字段名就是持久化契约，08 消费同一组名字。
            listOf(
                "id" to "bigint",
                "expert_doc_id" to "varchar",
                "source" to "varchar",
                "discovery_execution_id" to "bigint",
                "status" to "varchar",
                "attempts" to "int",
                "next_attempt_at" to "datetime",
                "lease_token" to "varchar",
                "lease_until" to "datetime",
                "last_error" to "varchar",
                "result_json" to "text",
                "created_at" to "datetime",
                "updated_at" to "datetime"
            ).forEach { (column, dataType) ->
                assertEquals(
                    dataType,
                    connection.queryString(columnMeta("expert_academic_enrichment_job", column, "DATA_TYPE")),
                    column
                )
            }
            // 审计归属列必须可空且无外键（task_execution 有保留期清理）。
            assertEquals(
                "YES",
                connection.queryString(
                    columnMeta("expert_academic_enrichment_job", "discovery_execution_id", "IS_NULLABLE")
                )
            )
            assertEquals(
                "1000",
                connection.queryString(
                    columnMeta("expert_academic_enrichment_job", "last_error", "CHARACTER_MAXIMUM_LENGTH")
                )
            )
            assertEquals(
                0L,
                connection.queryLong(
                    "SELECT COUNT(*) FROM information_schema.table_constraints " +
                        "WHERE constraint_schema = DATABASE() " +
                        "AND table_name = 'expert_academic_enrichment_job' " +
                        "AND constraint_type = 'FOREIGN KEY'"
                )
            )

            // 唯一键（同专家永远一行）+ 队列索引 (status, next_attempt_at, id) + 状态域 CHECK。
            assertTrue(
                connection.indexExists("expert_academic_enrichment_job", "uk_expert_academic_enrichment_job_doc")
            )
            assertTrue(
                connection.indexExists("expert_academic_enrichment_job", "idx_expert_academic_enrichment_job_due")
            )
            assertTrue(
                connection.checkConstraintExists(
                    "expert_academic_enrichment_job",
                    "chk_expert_academic_enrichment_job_status"
                )
            )

            connection.execute(
                """
                INSERT INTO expert_academic_enrichment_job
                    (expert_doc_id, source, status, attempts, next_attempt_at, created_at, updated_at)
                VALUES ('0000-0002-1825-009X', 'openalex', 'PENDING', 0,
                        '2026-09-21 10:00:00', '2026-09-21 10:00:00', '2026-09-21 10:00:00')
                """
            )
            // 重复 doc_id 直接被拒（入队幂等的前提）。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO expert_academic_enrichment_job
                        (expert_doc_id, source, status, attempts, next_attempt_at, created_at, updated_at)
                    VALUES ('0000-0002-1825-009X', 'orcid', 'PENDING', 0,
                            '2026-09-21 10:00:00', '2026-09-21 10:00:00', '2026-09-21 10:00:00')
                    """
                )
            }
            // 大小写不同的 ES _id 是两个文档（列级 utf8mb4_bin），唯一键不得把它们合并。
            connection.execute(
                """
                INSERT INTO expert_academic_enrichment_job
                    (expert_doc_id, source, status, attempts, next_attempt_at, created_at, updated_at)
                VALUES ('0000-0002-1825-009x', 'openalex', 'PENDING', 0,
                        '2026-09-21 10:00:00', '2026-09-21 10:00:00', '2026-09-21 10:00:00')
                """
            )
            assertEquals(2L, connection.queryLong("SELECT COUNT(*) FROM expert_academic_enrichment_job"))
            // 状态域之外的值被拒（MySQL 8 真实生效）。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    """
                    INSERT INTO expert_academic_enrichment_job
                        (expert_doc_id, source, status, attempts, next_attempt_at, created_at, updated_at)
                    VALUES ('0000-0002-1825-009Y', 'openalex', 'DONE', 0,
                            '2026-09-21 10:00:00', '2026-09-21 10:00:00', '2026-09-21 10:00:00')
                    """
                )
            }
            assertEquals(2L, connection.queryLong("SELECT COUNT(*) FROM expert_academic_enrichment_job"))
        }
    }

    @Test
    fun `V135 adds the sender account codes column backfilling existing rows with an empty array`() {
        // I-1：库停在 V133（含 V72 种子配置行）再升到最新版本 V135；新列存在、存量行一律回填 '[]'（= 不限）、
        // 收紧为 NOT NULL —— 旧任务的收件与选号范围零漂移。
        val v133 = flyway(MigrationVersion.fromVersion("133"))
        v133.clean()
        assertEquals("133", v133.migrate().targetSchemaVersion)
        val rowsBefore = connection().use { connection ->
            assertFalse(connection.columnExists("batch_send_task_config", "sender_account_codes_json"))
            connection.queryLong("SELECT COUNT(*) FROM batch_send_task_config")
        }
        assertTrue(rowsBefore > 0L, "V72 种子配置行必须存在，回填断言才有意义")

        assertEquals("157", flyway().migrate().targetSchemaVersion)

        connection().use { connection ->
            assertTrue(connection.columnExists("batch_send_task_config", "sender_account_codes_json"))
            assertEquals(
                "text",
                connection.queryString(
                    columnMeta("batch_send_task_config", "sender_account_codes_json", "DATA_TYPE")
                )
            )
            assertEquals(
                "NO",
                connection.queryString(
                    columnMeta("batch_send_task_config", "sender_account_codes_json", "IS_NULLABLE")
                )
            )
            // 存量行全部为 '[]'，且没有一行被漏成 NULL/空串。
            assertEquals(rowsBefore, connection.queryLong("SELECT COUNT(*) FROM batch_send_task_config"))
            assertEquals(
                0L,
                connection.queryLong(
                    "SELECT COUNT(*) FROM batch_send_task_config " +
                        "WHERE sender_account_codes_json IS NULL OR sender_account_codes_json <> '[]'"
                )
            )
        }
    }

    @Test
    fun `V138 creates the batch email verification table with its keys and cascade`() {
        // I-6/I-9：库停在 V137（新表尚不存在）再升到最新 V138，一次核对列契约、唯一键、
        // 游标索引、级联外键与缺省值 —— 明细表是逐邮箱审计的唯一持久化事实源。
        val v137 = flyway(MigrationVersion.fromVersion("137"))
        v137.clean()
        assertEquals("137", v137.migrate().targetSchemaVersion)
        connection().use { connection ->
            assertFalse(connection.tableExists("batch_email_verification"))
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)

        connection().use { connection ->
            assertTrue(connection.tableExists("batch_email_verification"))
            // 身份键：email 320 / orcid_id 128 且 NOT NULL，expert_doc_id 可缺失（不虚构 _id）。
            assertEquals("varchar", connection.queryString(columnMeta("batch_email_verification", "email", "DATA_TYPE")))
            assertEquals(320L, connection.queryLong(columnMeta("batch_email_verification", "email", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("utf8mb4_bin", connection.queryString(columnMeta("batch_email_verification", "email", "COLLATION_NAME")))
            assertEquals(128L, connection.queryLong(columnMeta("batch_email_verification", "orcid_id", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("NO", connection.queryString(columnMeta("batch_email_verification", "orcid_id", "IS_NULLABLE")))
            assertEquals("YES", connection.queryString(columnMeta("batch_email_verification", "expert_doc_id", "IS_NULLABLE")))
            // 三组状态列的缺省值就是「刚插入、还没结论」的形态。
            assertEquals("PENDING", connection.queryString(columnMeta("batch_email_verification", "decision", "COLUMN_DEFAULT")))
            assertEquals("NOT_SENT", connection.queryString(columnMeta("batch_email_verification", "send_status", "COLUMN_DEFAULT")))
            assertEquals("NOT_REQUIRED", connection.queryString(columnMeta("batch_email_verification", "tag_status", "COLUMN_DEFAULT")))
            assertEquals("int", connection.queryString(columnMeta("batch_email_verification", "request_count", "DATA_TYPE")))
            assertEquals("NO", connection.queryString(columnMeta("batch_email_verification", "created_at", "IS_NULLABLE")))
            assertEquals("NO", connection.queryString(columnMeta("batch_email_verification", "updated_at", "IS_NULLABLE")))
            // 唯一目标键（执行 + 规范化 ORCID + 规范化邮箱）与游标分页索引。
            assertEquals("task_execution_id,orcid_id,email", connection.queryString(
                "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'batch_email_verification' " +
                    "AND index_name = 'uk_batch_email_verification_target'"
            ))
            assertEquals("task_execution_id,id", connection.queryString(
                "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'batch_email_verification' " +
                    "AND index_name = 'idx_batch_email_verification_execution'"
            ))
            assertTrue(connection.foreignKeyExists("batch_email_verification", "fk_batch_email_verification_execution"))
            assertEquals("CASCADE", connection.queryString(
                "SELECT DELETE_RULE FROM information_schema.referential_constraints " +
                    "WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_batch_email_verification_execution'"
            ))
        }
    }

    @Test
    fun `V139 adds the email verification switch backfilling existing configs with false`() {
        // I-1：库停在 V138（配置表尚无该列）→ 升级到最新 V139。
        // 升级前先把种子任务改成可辨识的旧值，升级后逐一比对「只有新列出现、其它列零漂移」。
        val v138 = flyway(MigrationVersion.fromVersion("138"))
        v138.clean()
        assertEquals("138", v138.migrate().targetSchemaVersion)
        connection().use { connection ->
            assertFalse(connection.columnExists("batch_send_task_config", "email_verification_enabled"))
            connection.execute(
                """
                UPDATE batch_send_task_config
                SET config_name = 'V139 旧任务',
                    round_size = 7,
                    per_mail_interval_ms = 1234,
                    self_check_ttl_minutes = 9,
                    gate_filter_enabled = 1,
                    research_direction_filter = 'PRESENT',
                    sender_account_codes_json = '["LuKai"]',
                    expert_types_json = '["PRODUCTION_RND"]'
                WHERE legacy_code = 'INTRODUCTION'
                """
            )
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)

        connection().use { connection ->
            assertTrue(connection.columnExists("batch_send_task_config", "email_verification_enabled"))
            // 列契约：MySQL 8 的 BOOLEAN = tinyint，NOT NULL DEFAULT FALSE
            //（旧行与不传该字段的旧客户端一律 = 关闭，绝不暗中开启已验证策略）。
            assertEquals(
                "tinyint",
                connection.queryString(
                    columnMeta("batch_send_task_config", "email_verification_enabled", "DATA_TYPE")
                )
            )
            assertEquals(
                "NO",
                connection.queryString(
                    columnMeta("batch_send_task_config", "email_verification_enabled", "IS_NULLABLE")
                )
            )
            assertEquals(
                "0",
                connection.queryString(
                    columnMeta("batch_send_task_config", "email_verification_enabled", "COLUMN_DEFAULT")
                )
            )
            // 存量行全部回填 0；无一行漏成 NULL。
            assertEquals(
                0L,
                connection.queryLong(
                    "SELECT COUNT(*) FROM batch_send_task_config " +
                        "WHERE email_verification_enabled IS NULL OR email_verification_enabled <> FALSE"
                )
            )
            // 迁移只 ADD COLUMN：升级前写入的其它字段逐一不变。
            assertEquals(
                "V139 旧任务",
                connection.queryString(
                    "SELECT config_name FROM batch_send_task_config WHERE legacy_code = 'INTRODUCTION'"
                )
            )
            assertEquals(
                7L,
                connection.queryLong(
                    "SELECT round_size FROM batch_send_task_config WHERE legacy_code = 'INTRODUCTION'"
                )
            )
            assertEquals(
                1234L,
                connection.queryLong(
                    "SELECT per_mail_interval_ms FROM batch_send_task_config WHERE legacy_code = 'INTRODUCTION'"
                )
            )
            assertEquals(
                9L,
                connection.queryLong(
                    "SELECT self_check_ttl_minutes FROM batch_send_task_config WHERE legacy_code = 'INTRODUCTION'"
                )
            )
            assertEquals(
                1L,
                connection.queryLong(
                    "SELECT gate_filter_enabled FROM batch_send_task_config WHERE legacy_code = 'INTRODUCTION'"
                )
            )
            assertEquals(
                "PRESENT",
                connection.queryString(
                    "SELECT research_direction_filter FROM batch_send_task_config WHERE legacy_code = 'INTRODUCTION'"
                )
            )
            assertEquals(
                """["LuKai"]""",
                connection.queryString(
                    "SELECT sender_account_codes_json FROM batch_send_task_config WHERE legacy_code = 'INTRODUCTION'"
                )
            )
            assertEquals(
                """["PRODUCTION_RND"]""",
                connection.queryString(
                    "SELECT expert_types_json FROM batch_send_task_config WHERE legacy_code = 'INTRODUCTION'"
                )
            )
        }
    }

    @Test
    fun `V123 adds nullable calendar attachment json column leaving history null`() {
        migrateToV23AndSeedBase()
        // V123 之前的旧行：无该列，随迁移升到最新版本。
        connection().use { connection ->
            connection.execute(
                """
                INSERT INTO mail_record
                    (id, expert_contact_id, direction, mail_type, message_id, send_status, sent_at)
                VALUES
                    (201, 1, 'OUTBOUND', 'MANUAL_RICH_REPLY', 'historical-1', 'SENT', '2026-09-01 10:00:00'),
                    (202, 1, 'OUTBOUND', 'MANUAL_RICH_REPLY', 'historical-2', 'FAILED', NULL)
                """
            )
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            // 列契约：calendar_attachment_json LONGTEXT NULL（I-1 唯一 absence 形态）。
            assertTrue(connection.columnExists("mail_record", "calendar_attachment_json"))
            assertEquals("longtext", connection.queryString(
                "SELECT DATA_TYPE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'mail_record' " +
                    "AND column_name = 'calendar_attachment_json'"
            ))
            assertEquals("YES", connection.queryString(
                "SELECT IS_NULLABLE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'mail_record' " +
                    "AND column_name = 'calendar_attachment_json'"
            ))
            // 历史行不回填：迁移前旧行保持 NULL（不默认填 {} / 空串 / 假快照）。
            assertEquals(2L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_record WHERE id IN (201, 202) AND calendar_attachment_json IS NULL"
            ))
            assertEquals(0L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_record WHERE calendar_attachment_json IS NOT NULL"
            ))
            // 新列可写规范快照 JSON（应用层 codec 才是校验方；本测试只验证列可存）。
            connection.execute(
                "UPDATE mail_record SET calendar_attachment_json = '{\"schemaVersion\":1}' WHERE id = 201"
            )
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM mail_record WHERE id = 201 AND calendar_attachment_json IS NOT NULL"
            ))
        }
    }

    @Test
    fun `V124 allows material attached promotion audit trigger`() {
        // The FK needs a seeded expert_contact before inserting the audit row.
        migrateToV23AndSeedBase()
        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            assertEquals("32", connection.queryString(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_application_promotion' " +
                    "AND column_name = 'triggered_by'"
            ))
            connection.execute(
                "INSERT INTO expert_application_promotion " +
                    "(expert_contact_id, orcid_id, triggered_by, promotion_status) " +
                    "VALUES (1, '0000-0000-0000-0001', 'MATERIAL_ATTACHED', 'PENDING')"
            )
            assertEquals(1L, connection.queryLong(
                "SELECT COUNT(*) FROM expert_application_promotion WHERE triggered_by = 'MATERIAL_ATTACHED'"
            ))
        }
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

        assertEquals("157", flyway().migrate().targetSchemaVersion)
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
        assertEquals("157", flyway().migrate().targetSchemaVersion)
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
        assertEquals("157", flyway().migrate().targetSchemaVersion)
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

        assertEquals("157", flyway().migrate().targetSchemaVersion)
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
        assertEquals("157", flyway().migrate().targetSchemaVersion)
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

        assertEquals("157", flyway().migrate().targetSchemaVersion)
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

        assertEquals("157", flyway().migrate().targetSchemaVersion)
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

        assertEquals("157", flyway().migrate().targetSchemaVersion)
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

        assertEquals("157", flyway().migrate().targetSchemaVersion)
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

        assertEquals("157", flyway().migrate().targetSchemaVersion)
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

    @Test
    fun `V147 creates expert_mailbox_suspension as an empty table with composite key and FK`() {
        migrateToV23AndSeedBase()
        var contactsBefore = 0L
        connection().use { connection ->
            assertFalse(connection.tableExists("expert_mailbox_suspension"))
            contactsBefore = connection.queryLong("SELECT COUNT(*) FROM expert_contact")
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            assertTrue(connection.tableExists("expert_mailbox_suspension"))
            listOf("username", "expert_contact_id", "reason").forEach { column ->
                assertTrue(connection.columnExists("expert_mailbox_suspension", column), "missing column $column")
            }
            // 升级不留回填：新表初始为空，既有联系人原样保留（I-1/I-6）。
            assertEquals(0L, connection.queryLong("SELECT COUNT(*) FROM expert_mailbox_suspension"))
            assertEquals(contactsBefore, connection.queryLong("SELECT COUNT(*) FROM expert_contact"))

            // 列契约：username VARCHAR(64) NOT NULL；expert_contact_id BIGINT NOT NULL；
            // reason VARCHAR(500) NULL（可空 = 未填写原因，绝不表示未挂起 I-1）。
            assertEquals("varchar", connection.queryString(
                "SELECT DATA_TYPE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_mailbox_suspension' " +
                    "AND column_name = 'username'"
            ))
            assertEquals("64", connection.queryString(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_mailbox_suspension' " +
                    "AND column_name = 'username'"
            ))
            assertEquals("500", connection.queryString(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_mailbox_suspension' " +
                    "AND column_name = 'reason'"
            ))
            listOf("username", "expert_contact_id").forEach { column ->
                assertEquals("NO", connection.queryString(
                    "SELECT IS_NULLABLE FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'expert_mailbox_suspension' " +
                        "AND column_name = '$column'"
                ))
            }
            assertEquals("YES", connection.queryString(
                "SELECT IS_NULLABLE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_mailbox_suspension' " +
                    "AND column_name = 'reason'"
            ))

            // 复合主键列序：(username, expert_contact_id)。
            assertEquals(2L, connection.queryLong(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_mailbox_suspension' " +
                    "AND index_name = 'PRIMARY'"
            ))
            assertEquals("username", connection.queryString(
                "SELECT COLUMN_NAME FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_mailbox_suspension' " +
                    "AND index_name = 'PRIMARY' AND SEQ_IN_INDEX = 1"
            ))
            assertEquals("expert_contact_id", connection.queryString(
                "SELECT COLUMN_NAME FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_mailbox_suspension' " +
                    "AND index_name = 'PRIMARY' AND SEQ_IN_INDEX = 2"
            ))

            // NULL reason 合法（行存在即挂起）；重复主键拒绝；不同用户/专家各一行合法。
            connection.execute(
                "INSERT INTO expert_mailbox_suspension (username, expert_contact_id, reason) " +
                    "VALUES ('admin', 1, NULL)"
            )
            assertThrows(SQLException::class.java) {
                connection.execute(
                    "INSERT INTO expert_mailbox_suspension (username, expert_contact_id, reason) " +
                        "VALUES ('admin', 1, 'dup')"
                )
            }
            connection.execute(
                "INSERT INTO expert_mailbox_suspension (username, expert_contact_id, reason) " +
                    "VALUES ('admin', 2, '等待材料')"
            )
            connection.execute(
                "INSERT INTO expert_mailbox_suspension (username, expert_contact_id, reason) " +
                    "VALUES ('operator-b', 1, NULL)"
            )
            assertEquals(3L, connection.queryLong("SELECT COUNT(*) FROM expert_mailbox_suspension"))

            // username NOT NULL。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    "INSERT INTO expert_mailbox_suspension (username, expert_contact_id, reason) " +
                        "VALUES (NULL, 1, NULL)"
                )
            }

            // 联系人外键仍生效：未知专家拒绝。
            assertTrue(connection.foreignKeyExists("expert_mailbox_suspension", "fk_mailbox_suspension_contact"))
            assertThrows(SQLException::class.java) {
                connection.execute(
                    "INSERT INTO expert_mailbox_suspension (username, expert_contact_id, reason) " +
                        "VALUES ('admin', 999999, NULL)"
                )
            }
        }
    }

    @Test
    fun `V149 upgrades old follows to FOLLOWING without losing ownership or created_at`() {
        migrateToV23AndSeedBase()
        // 先迁到本片之前的已协调版本 147（V148 属并行计划，不在本 worktree）：此时
        // expert_follow 尚无 progress_status 列，插入两位用户的旧关注行与固定时间。
        val v147 = flyway(MigrationVersion.fromVersion("147"))
        assertEquals("147", v147.migrate().targetSchemaVersion)
        connection().use { connection ->
            assertFalse(connection.columnExists("expert_follow", "progress_status"))
            connection.execute(
                "INSERT INTO expert_follow (username, expert_contact_id, created_at) " +
                    "VALUES ('admin', 1, '2026-09-08 10:00:00'), " +
                    "('operator-b', 2, '2026-09-09 11:30:00'), " +
                    "('admin', 2, '2026-09-10 09:15:00')"
            )
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            // 列契约：VARCHAR(16) NOT NULL DEFAULT 'FOLLOWING'；不新增 provided 布尔列。
            assertTrue(connection.columnExists("expert_follow", "progress_status"))
            assertEquals("varchar", connection.queryString(
                "SELECT DATA_TYPE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_follow' " +
                    "AND column_name = 'progress_status'"
            ))
            assertEquals("16", connection.queryString(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_follow' " +
                    "AND column_name = 'progress_status'"
            ))
            assertEquals("NO", connection.queryString(
                "SELECT IS_NULLABLE FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_follow' " +
                    "AND column_name = 'progress_status'"
            ))
            assertEquals("FOLLOWING", connection.queryString(
                "SELECT COLUMN_DEFAULT FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'expert_follow' " +
                    "AND column_name = 'progress_status'"
            ))
            assertFalse(connection.columnExists("expert_follow", "provided"))

            // 行数、复合主键归属、首次 created_at 全部保留；旧行状态统一为 FOLLOWING。
            assertEquals(3L, connection.queryLong("SELECT COUNT(*) FROM expert_follow"))
            assertEquals("2026-09-08 10:00:00", connection.queryString(
                "SELECT created_at FROM expert_follow WHERE username = 'admin' AND expert_contact_id = 1"
            ))
            assertEquals("2026-09-09 11:30:00", connection.queryString(
                "SELECT created_at FROM expert_follow WHERE username = 'operator-b' AND expert_contact_id = 2"
            ))
            assertEquals(3L, connection.queryLong(
                "SELECT COUNT(*) FROM expert_follow WHERE progress_status = 'FOLLOWING'"
            ))

            // 旧 INSERT 省略新列仍由 DEFAULT 落 FOLLOWING（既有省略列写法继续有效）。
            connection.execute(
                "INSERT INTO expert_follow (username, expert_contact_id, created_at) " +
                    "VALUES ('legacy', 1, '2026-09-11 10:00:00')"
            )
            assertEquals("FOLLOWING", connection.queryString(
                "SELECT progress_status FROM expert_follow WHERE username = 'legacy' AND expert_contact_id = 1"
            ))
            // 复合主键仍唯一。
            assertThrows(SQLException::class.java) {
                connection.execute(
                    "INSERT INTO expert_follow (username, expert_contact_id, created_at) " +
                        "VALUES ('admin', 1, '2026-09-12 10:00:00')"
                )
            }
        }
    }

    @Test
    fun `V150 schema preserves V149 data and enforces note ownership`() {
        migrateToV23AndSeedBase()
        assertEquals("149", flyway(MigrationVersion.fromVersion("149")).migrate().targetSchemaVersion)
        val before = connection().use { c ->
            assertFalse(c.tableExists("expert_contact_note"))
            c.queryString("SELECT CONCAT(id, '|', campaign_id, '|', orcid_id, '|', current_status, '|', updated_at) FROM expert_contact WHERE id=1")
        }
        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { c ->
            assertEquals(before, c.queryString("SELECT CONCAT(id, '|', campaign_id, '|', orcid_id, '|', current_status, '|', updated_at) FROM expert_contact WHERE id=1"))
            assertEquals(2L, c.queryLong("SELECT COUNT(*) FROM expert_contact"))
            assertEquals(0L, c.queryLong("SELECT COUNT(*) FROM expert_contact_note"))
            assertEquals(4L, c.queryLong("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='expert_contact_note'"))
            mapOf("expert_contact_id" to "bigint", "note" to "varchar", "updated_by" to "varchar", "updated_at" to "datetime").forEach { (column, type) ->
                assertEquals(type, c.queryString(columnMeta("expert_contact_note", column, "DATA_TYPE")))
                assertEquals("NO", c.queryString(columnMeta("expert_contact_note", column, "IS_NULLABLE")))
            }
            assertEquals("2000", c.queryString(columnMeta("expert_contact_note", "note", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("64", c.queryString(columnMeta("expert_contact_note", "updated_by", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("3", c.queryString(columnMeta("expert_contact_note", "updated_at", "DATETIME_PRECISION")))
            listOf("note", "updated_by").forEach {
                assertEquals("utf8mb4", c.queryString(columnMeta("expert_contact_note", it, "CHARACTER_SET_NAME")))
            }
            assertEquals("InnoDB", c.queryString("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='expert_contact_note'"))
            assertEquals(1L, c.queryLong("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='expert_contact_note' AND index_name='PRIMARY' AND column_name='expert_contact_id' AND non_unique=0"))
            assertTrue(c.foreignKeyExists("expert_contact_note", "fk_expert_contact_note_contact"))
            assertEquals("CASCADE", c.queryString("SELECT DELETE_RULE FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fk_expert_contact_note_contact'"))
            assertEquals("expert_contact", c.queryString("SELECT REFERENCED_TABLE_NAME FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND constraint_name='fk_expert_contact_note_contact'"))
            assertEquals("id", c.queryString("SELECT REFERENCED_COLUMN_NAME FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND constraint_name='fk_expert_contact_note_contact'"))
            assertThrows(SQLException::class.java) {
                c.execute("INSERT INTO expert_contact_note VALUES (999999, 'orphan', 'admin', NOW(3))")
            }
            c.execute("INSERT INTO expert_contact_note VALUES (2, 'temporary', 'admin', NOW(3))")
            assertThrows(SQLException::class.java) {
                c.execute("INSERT INTO expert_contact_note VALUES (2, 'duplicate', 'admin', NOW(3))")
            }
            c.execute("DELETE FROM expert_contact WHERE id=2")
            assertEquals(0L, c.queryLong("SELECT COUNT(*) FROM expert_contact_note"))
        }
    }

    @Test
    fun `real note service persists text metadata isolation boundaries and rollback`() {
        migrateToV23AndSeedBase()
        flyway().migrate()
        connection().use { c ->
            c.execute("INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) VALUES (3, 'NOTE_TEST', 'Note Test', 1)")
            c.execute("INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, current_status) VALUES (3, 3, '0000-0001', 'three@example.com', 'NEW')")
        }
        val dataSource = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        val jdbc = NamedParameterJdbcTemplate(dataSource)
        val service = ExpertContactNoteService(jdbc)
        // Direct instances have no Spring proxy; explicitly exercise real transactions.
        val tx = TransactionTemplate(DataSourceTransactionManager(dataSource))
        fun save(user: String, id: Long, text: String): ExpertContactNoteView =
            tx.execute { service.save(user, id, text) }!!
        fun freshRead(id: Long) = ExpertContactNoteService(
            NamedParameterJdbcTemplate(DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password))
        ).get(id)
        val parentBefore = jdbc.queryForList("SELECT * FROM expert_contact ORDER BY id", emptyMap<String, Any>())
        assertEquals(ExpertContactNoteView(1, "", null, null), service.get(1))
        connection().use { assertEquals(0L, it.queryLong("SELECT COUNT(*) FROM expert_contact_note")) }
        val first = save("userA", 1, " \r\n中文 'quoted'\r\n第二行\r末行 ")
        assertEquals("中文 'quoted'\n第二行\n末行", first.note)
        assertEquals("userA", first.updatedBy)
        assertTrue(first.updatedAt!!.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+08:00")))
        assertEquals(first, freshRead(1))
        assertEquals(ExpertContactNoteView(3, "", null, null), freshRead(3))
        val second = save("userB", 1, "后一人覆盖")
        assertEquals("userB", second.updatedBy)
        assertEquals(second, freshRead(1))
        connection().use { c ->
            assertEquals(1L, c.queryLong("SELECT COUNT(*) FROM expert_contact_note WHERE expert_contact_id=1"))
            assertEquals(second.note, c.queryString("SELECT note FROM expert_contact_note WHERE expert_contact_id=1"))
            assertEquals(second.updatedBy, c.queryString("SELECT updated_by FROM expert_contact_note WHERE expert_contact_id=1"))
            assertEquals(second.updatedAt!!.substringBefore("+08:00").replace('T', ' '),
                c.queryString("SELECT DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') FROM expert_contact_note WHERE expert_contact_id=1").dropLast(3))
        }
        val separate = save("userA", 3, "同ORCID另一活动")
        assertEquals(separate, freshRead(3))
        assertEquals(second, freshRead(1))
        listOf("不能提交", "").forEach { text ->
            tx.execute { status ->
                service.save("rollback-user", 1, text)
                status.setRollbackOnly()
            }
            assertEquals(second, freshRead(1))
        }
        listOf("中".repeat(2000), "😀".repeat(1000)).forEach { text ->
            assertEquals(text, save("userA", 1, text).note)
            assertEquals(text, freshRead(1).note)
        }
        val boundary = freshRead(1)
        listOf("中".repeat(2001), "😀".repeat(1001), " ".repeat(2001)).forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { save("userB", 1, text) }
            assertEquals(boundary, freshRead(1))
        }
        assertEquals(ExpertContactNoteView(1, "", null, null), save("userB", 1, " \r\n\t "))
        assertEquals(ExpertContactNoteView(1, "", null, null), freshRead(1))
        connection().use { assertEquals(0L, it.queryLong("SELECT COUNT(*) FROM expert_contact_note WHERE expert_contact_id=1")) }
        assertEquals(parentBefore, jdbc.queryForList("SELECT * FROM expert_contact ORDER BY id", emptyMap<String, Any>()))
        connection().use { c ->
            c.execute("DELETE FROM expert_contact WHERE id=3")
            assertEquals(0L, c.queryLong("SELECT COUNT(*) FROM expert_contact_note WHERE expert_contact_id=3"))
        }
        assertThrows(NoSuchElementException::class.java) { freshRead(3) }
        assertThrows(NoSuchElementException::class.java) { save("userA", 3, "deleted") }
        assertThrows(NoSuchElementException::class.java) { freshRead(999999) }
    }

    @Test
    fun `V156 creates the three discovery person tables without touching business rows`() {
        migrateToV23AndSeedBase()
        var contactsBefore = 0L
        connection().use { connection ->
            listOf("discovery_person", "discovery_person_member", "discovery_person_author_key").forEach {
                assertFalse(connection.tableExists(it), "table $it must not exist before V156")
            }
            contactsBefore = connection.queryLong("SELECT COUNT(*) FROM expert_contact")
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            // I-1/I-2/I-3：三张独立新表存在。
            listOf("discovery_person", "discovery_person_member", "discovery_person_author_key").forEach {
                assertTrue(connection.tableExists(it), "missing table $it")
            }

            // discovery_person：person_id CHAR(36) 主键；其余列非空。
            assertEquals("char", connection.queryString(columnMeta("discovery_person", "person_id", "DATA_TYPE")))
            assertEquals("36", connection.queryString(columnMeta("discovery_person", "person_id", "CHARACTER_MAXIMUM_LENGTH")))
            listOf("primary_doc_id", "revision", "created_at", "updated_at").forEach { column ->
                assertEquals("NO", connection.queryString(columnMeta("discovery_person", column, "IS_NULLABLE")))
            }
            assertEquals("person_id", connection.queryString(
                "SELECT COLUMN_NAME FROM information_schema.statistics WHERE table_schema = DATABASE() " +
                    "AND table_name = 'discovery_person' AND index_name = 'PRIMARY' AND SEQ_IN_INDEX = 1"
            ))

            // discovery_person_member：expert_doc_id 主键（一个 docId 只隶属一个人物）；
            // normalized_email 只建**非唯一**索引——主邮箱仅为展示默认值，不以邮箱唯一代替人证据。
            assertEquals("expert_doc_id", connection.queryString(
                "SELECT COLUMN_NAME FROM information_schema.statistics WHERE table_schema = DATABASE() " +
                    "AND table_name = 'discovery_person_member' AND index_name = 'PRIMARY' AND SEQ_IN_INDEX = 1"
            ))
            listOf("person_id", "identity_fingerprint", "created_at", "updated_at").forEach { column ->
                assertEquals("NO", connection.queryString(columnMeta("discovery_person_member", column, "IS_NULLABLE")))
            }
            assertEquals("YES", connection.queryString(columnMeta("discovery_person_member", "normalized_email", "IS_NULLABLE")))
            assertTrue(connection.indexExists("discovery_person_member", "idx_discovery_person_member_email"))
            assertEquals("1", connection.queryString(
                "SELECT NON_UNIQUE FROM information_schema.statistics WHERE table_schema = DATABASE() " +
                    "AND table_name = 'discovery_person_member' AND index_name = 'idx_discovery_person_member_email' LIMIT 1"
            ))

            // discovery_person_author_key：author_key 主键（每键全局唯一，并发唯一防线）。
            assertEquals("author_key", connection.queryString(
                "SELECT COLUMN_NAME FROM information_schema.statistics WHERE table_schema = DATABASE() " +
                    "AND table_name = 'discovery_person_author_key' AND index_name = 'PRIMARY' AND SEQ_IN_INDEX = 1"
            ))
            assertEquals("NO", connection.queryString(columnMeta("discovery_person_author_key", "person_id", "IS_NULLABLE")))

            // 外键只指向本计划的新表（成员/作者键 → 主档）。
            assertTrue(connection.foreignKeyExists("discovery_person_member", "fk_discovery_person_member_person"))
            assertTrue(connection.foreignKeyExists("discovery_person_author_key", "fk_discovery_person_author_key_person"))

            // 既有业务表零改动。
            assertEquals(contactsBefore, connection.queryLong("SELECT COUNT(*) FROM expert_contact"))
        }
    }

    @Test
    fun `V157 creates the discovery person outreach guard table with composite primary key`() {
        migrateToV23AndSeedBase()
        connection().use { connection ->
            assertFalse(
                connection.tableExists("discovery_person_outreach"),
                "table discovery_person_outreach must not exist before V157"
            )
        }

        assertEquals("157", flyway().migrate().targetSchemaVersion)
        connection().use { connection ->
            // I-1/I-2：人物级首发占位表存在。
            assertTrue(connection.tableExists("discovery_person_outreach"), "missing table discovery_person_outreach")

            // 复合主键 (person_id, mail_type) —— 同人同发信类型至多一行（并发唯一防线，不以邮箱切开）。
            assertEquals("person_id", connection.queryString(
                "SELECT COLUMN_NAME FROM information_schema.statistics WHERE table_schema = DATABASE() " +
                    "AND table_name = 'discovery_person_outreach' AND index_name = 'PRIMARY' AND SEQ_IN_INDEX = 1"
            ))
            assertEquals("mail_type", connection.queryString(
                "SELECT COLUMN_NAME FROM information_schema.statistics WHERE table_schema = DATABASE() " +
                    "AND table_name = 'discovery_person_outreach' AND index_name = 'PRIMARY' AND SEQ_IN_INDEX = 2"
            ))

            // 必填列非空。
            listOf("state", "revision", "owner_token", "selected_doc_id", "recipient", "created_at", "updated_at")
                .forEach { column ->
                    assertEquals(
                        "NO",
                        connection.queryString(columnMeta("discovery_person_outreach", column, "IS_NULLABLE")),
                        "column $column must be NOT NULL"
                    )
                }
            // 交付结果列可空（占位时尚未交付）。
            listOf("message_id", "attempt_id", "history_reference_json").forEach { column ->
                assertEquals(
                    "YES",
                    connection.queryString(columnMeta("discovery_person_outreach", column, "IS_NULLABLE")),
                    "column $column must be nullable"
                )
            }

            // person_id CHAR(36)（与 discovery_person 主键同型）；owner_token CHAR(36)。
            assertEquals("char", connection.queryString(columnMeta("discovery_person_outreach", "person_id", "DATA_TYPE")))
            assertEquals("36", connection.queryString(columnMeta("discovery_person_outreach", "person_id", "CHARACTER_MAXIMUM_LENGTH")))
            assertEquals("char", connection.queryString(columnMeta("discovery_person_outreach", "owner_token", "DATA_TYPE")))
            assertEquals("36", connection.queryString(columnMeta("discovery_person_outreach", "owner_token", "CHARACTER_MAXIMUM_LENGTH")))

            // 外键只指向本计划新表（占位 → 人物主档）。
            assertTrue(connection.foreignKeyExists("discovery_person_outreach", "fk_discovery_person_outreach_person"))
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

    /** V126 列元数据查询（04 新表列契约）。 */
    private fun outboundAttachmentColumnMeta(column: String, field: String): String =
        "SELECT $field FROM information_schema.columns WHERE table_schema = DATABASE() " +
            "AND table_name = 'outbound_mail_attachment' AND column_name = '$column'"

    /** 任意表的列元数据查询（V130 的 manual_expert_material_upload / mail_attachment 列契约）。 */
    private fun columnMeta(table: String, column: String, field: String): String =
        "SELECT $field FROM information_schema.columns WHERE table_schema = DATABASE() " +
            "AND table_name = '$table' AND column_name = '$column'"

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
