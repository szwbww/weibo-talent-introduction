package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.UnsubscribeProperties
import com.weibo.talentintroduction.mail.repository.UnsubscribeTokenRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer

@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(
    properties = [
        "spring.flyway.placeholder-replacement=false"
    ]
)
class UnsubscribeTokenServiceJdbcIT {
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
                "Docker is required for UnsubscribeTokenService JDBC tests"
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
        }
    }

    @Autowired
    private lateinit var repository: UnsubscribeTokenRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun clearTokens() {
        jdbcTemplate.update("DELETE FROM unsubscribe_token")
    }

    @Test
    fun `first sign persists a non-null creation time`() {
        val service = UnsubscribeTokenService(
            UnsubscribeProperties(baseUrl = "https://outreach.example.com", secret = "test-secret"),
            repository
        )

        val token = service.sign(" User@Example.com ")
        val saved = repository.findByEmail("user@example.com")

        assertEquals(43, token.length)
        assertNotNull(saved)
        assertEquals(token, saved!!.token)
        assertNotNull(saved.createdAt)
        assertEquals("user@example.com", service.verify(token))
        assertTrue(
            jdbcTemplate.queryForObject(
                "SELECT created_at IS NOT NULL FROM unsubscribe_token WHERE email = ?",
                Boolean::class.java,
                "user@example.com"
            ) == true
        )
    }
}
