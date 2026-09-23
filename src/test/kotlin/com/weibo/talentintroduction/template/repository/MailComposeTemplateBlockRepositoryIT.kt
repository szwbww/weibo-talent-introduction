package com.weibo.talentintroduction.template.repository

import com.weibo.talentintroduction.template.domain.ComposeBlockType
import com.weibo.talentintroduction.template.domain.MailComposeTemplate
import com.weibo.talentintroduction.template.domain.MailComposeTemplateBlock
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer

@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MailComposeTemplateBlockRepositoryIT {
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
                "Docker is required for mail compose template block repository tests"
            }
            mysql.start()
        }

        @JvmStatic
        @AfterAll
        fun stopMysql() {
            if (mysql.isRunning) mysql.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.flyway.placeholder-replacement") { false }
        }
    }

    @Autowired
    private lateinit var templateRepository: MailComposeTemplateRepository

    @Autowired
    private lateinit var blockRepository: MailComposeTemplateBlockRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS task_execution")
        jdbcTemplate.execute("DROP TABLE IF EXISTS batch_send_task_config")
        jdbcTemplate.execute("DROP TABLE IF EXISTS mail_compose_template_block")
        jdbcTemplate.execute("DROP TABLE IF EXISTS mail_compose_template")
        jdbcTemplate.execute(
            """
            CREATE TABLE mail_compose_template (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                template_code VARCHAR(64) NULL,
                template_name VARCHAR(100) NOT NULL,
                subject VARCHAR(255) NOT NULL,
                subject_variants TEXT NULL,
                subject_snippet_id BIGINT NULL,
                description VARCHAR(500) NULL,
                mail_type VARCHAR(64) NULL,
                required_keys VARCHAR(500) NULL,
                enabled TINYINT(1) NOT NULL DEFAULT 1,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            CREATE TABLE mail_compose_template_block (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                template_id BIGINT NOT NULL,
                block_order INT NOT NULL,
                block_type VARCHAR(30) NOT NULL,
                ref_id BIGINT NULL,
                custom_text TEXT NULL,
                FOREIGN KEY (template_id) REFERENCES mail_compose_template(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    @Test
    fun `deleteAllByTemplateId deletes all blocks for a template`() {
        val templateId = templateRepository.save(
            MailComposeTemplate(
                templateName = "Follow up",
                subject = "Hello"
            )
        ).id ?: error("template id is required")
        blockRepository.save(
            MailComposeTemplateBlock(
                templateId = templateId,
                blockOrder = 0,
                blockType = ComposeBlockType.CUSTOM_TEXT,
                customText = "Line 1"
            )
        )
        blockRepository.save(
            MailComposeTemplateBlock(
                templateId = templateId,
                blockOrder = 1,
                blockType = ComposeBlockType.CUSTOM_TEXT,
                customText = "Line 2"
            )
        )

        blockRepository.deleteAllByTemplateId(templateId)

        assertEquals(0, blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(templateId).size)
    }
    @Test
    fun `subject snippet reference persists and can be cleared through JDBC`() {
        val saved = templateRepository.save(
            MailComposeTemplate(
                templateName = "Referenced subject",
                subject = "Source snapshot",
                subjectSnippetId = 42L
            )
        )
        val templateId = saved.id ?: error("template id is required")

        assertEquals(42L, templateRepository.findById(templateId).orElseThrow().subjectSnippetId)

        templateRepository.save(saved.copy(subjectSnippetId = null))

        assertEquals(null, templateRepository.findById(templateId).orElseThrow().subjectSnippetId)
        assertEquals("Source snapshot", templateRepository.findById(templateId).orElseThrow().subject)
    }
}
