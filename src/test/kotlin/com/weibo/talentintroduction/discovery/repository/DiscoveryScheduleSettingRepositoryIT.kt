package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.service.DiscoveryScheduleSettingService
import com.weibo.talentintroduction.discovery.service.DiscoveryScheduleStatus
import com.weibo.talentintroduction.discovery.service.ExpertDiscoveryScheduler
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

/**
 * I-1（03）整数小时设置存储的真实 MySQL 集成测试（`-DmigrationIt=true`，testcontainers MySQL 8.0.36）。
 *
 * 表由真实 **V144** 迁移建立（不是手写 DDL），因此同时验证迁移契约：单例行、无初始化行、无外键、
 * `updated_at` 为毫秒精度 DATETIME，且应用以 **UTC 墙钟**读写（与 JVM/连接默认时区无关）。
 *
 * 覆盖计划验收：首写、同值不改锚点、变化更新锚点、事务失败回滚，以及
 * 「库读失败/存量值越界**不得**被解释为无行」。H2 不能替代这些持久化与回滚语义。
 */
@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// 需要真实提交/回滚与真实行数断言：测试级事务会让「回滚」变得不可观察。
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(
    properties = [
        "spring.flyway.placeholder-replacement=false"
    ]
)
@Import(DiscoveryScheduleSettingRepository::class)
class DiscoveryScheduleSettingRepositoryIT {

    companion object {
        /** 北京时间 2026-09-29 10:00（= 02:00Z）。 */
        private val AT_10: Instant = Instant.parse("2026-09-29T02:00:00Z")
        private val AT_13: Instant = Instant.parse("2026-09-29T05:00:00Z")
        private val PROPERTIES = ExpertDiscoveryProperties(cron = "0 0 */2 * * ?")

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
                "Docker is required for discovery schedule setting repository tests"
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
    private lateinit var repository: DiscoveryScheduleSettingRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var dataSource: DataSource

    @BeforeEach
    fun cleanSetting() {
        jdbcTemplate.update("DELETE FROM discovery_schedule_setting")
    }

    private fun countRows(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM discovery_schedule_setting", Int::class.java) ?: -1

    private fun rawAnchor(): String? =
        jdbcTemplate.queryForObject(
            "SELECT DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') FROM discovery_schedule_setting WHERE id = 1",
            String::class.java
        )

    private fun iso(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)

    /** I-3：本切片没有调度器 bean（`enabled=false`），因此保存后必然 `applied=false`。 */
    private fun serviceAt(clock: Clock): DiscoveryScheduleSettingService {
        @Suppress("UNCHECKED_CAST")
        val noScheduler = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<ExpertDiscoveryScheduler>
        return DiscoveryScheduleSettingService(
            repository,
            TransactionTemplate(DataSourceTransactionManager(dataSource)),
            PROPERTIES,
            noScheduler,
            clock
        )
    }

    // ------------------------------------------------------------------
    // V144 迁移契约
    // ------------------------------------------------------------------

    @Test
    fun `V144 creates a single row millisecond anchored table with no seed row and no foreign key (I-1)`() {
        val columns = jdbcTemplate.query(
            """
            SELECT COLUMN_NAME, DATA_TYPE, COLUMN_KEY, DATETIME_PRECISION
              FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discovery_schedule_setting'
             ORDER BY ORDINAL_POSITION
            """,
            { rs, _ ->
                listOf(
                    rs.getString("COLUMN_NAME"),
                    rs.getString("DATA_TYPE"),
                    rs.getString("COLUMN_KEY"),
                    rs.getString("DATETIME_PRECISION")
                )
            }
        )

        assertEquals(
            listOf(
                listOf("id", "tinyint", "PRI", null),
                listOf("interval_hours", "smallint", "", null),
                listOf("updated_at", "datetime", "", "3")
            ),
            columns,
            "只允许 3 个业务列，且锚点必须是 DATETIME(3)"
        )
        assertEquals(0, countRows(), "迁移不得写入初始化行")

        val onUpdateColumns = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'discovery_schedule_setting'
               AND EXTRA LIKE '%on update%'
            """,
            Int::class.java
        ) ?: -1
        assertEquals(0, onUpdateColumns, "锚点列不得带 ON UPDATE（同值保存不能被动重置周期）")

        val foreignKeys = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'discovery_schedule_setting'
               AND CONSTRAINT_TYPE = 'FOREIGN KEY'
            """,
            Int::class.java
        ) ?: -1
        assertEquals(0, foreignKeys, "设置表不得有外键")
    }

    // ------------------------------------------------------------------
    // 首写 / 同值 / 变化 / 越界 / 坏存量
    // ------------------------------------------------------------------

    @Test
    fun `the first save writes exactly one row whose anchor round-trips as UTC (I-1)`() {
        assertNull(repository.find(), "空表 = 沿用部署 cron")

        val saved = repository.save(3, AT_10)

        assertEquals(1, saved.id)
        assertEquals(3, saved.intervalHours)
        assertEquals(AT_10, saved.updatedAt, "锚点必须原样读回（毫秒精度）")
        assertEquals(1, countRows())
        assertEquals(AT_10, repository.find()?.updatedAt)
        assertEquals(
            "2026-09-29 02:00:00.000000",
            rawAnchor(),
            "锚点必须以 UTC 墙钟写入 DATETIME(3)，与 JVM/连接时区无关"
        )
    }

    @Test
    fun `a changed value overwrites the single row and moves the anchor (I-1)`() {
        repository.save(3, AT_10)

        val updated = repository.save(5, AT_13)

        assertEquals(5, updated.intervalHours)
        assertEquals(AT_13, updated.updatedAt)
        assertEquals(1, countRows(), "永远只有单例行")
        assertEquals(5, repository.find()?.intervalHours)
        assertEquals(AT_13, repository.find()?.updatedAt)
    }

    @Test
    fun `the repository refuses out of range intervals without writing (I-1)`() {
        assertThrows(IllegalArgumentException::class.java) { repository.save(0, AT_10) }
        assertThrows(IllegalArgumentException::class.java) { repository.save(169, AT_10) }
        assertThrows(IllegalArgumentException::class.java) { repository.save(-3, AT_10) }

        assertEquals(0, countRows())
        assertNull(repository.find())
    }

    @Test
    fun `a stored value outside the allowed range is an error and never an empty table (I-1)`() {
        jdbcTemplate.update(
            "INSERT INTO discovery_schedule_setting (id, interval_hours, updated_at) VALUES (1, 0, ?)",
            java.time.LocalDateTime.ofInstant(AT_10, ZoneOffset.UTC)
        )
        assertThrows(IllegalStateException::class.java) { repository.find() }

        jdbcTemplate.update("UPDATE discovery_schedule_setting SET interval_hours = 200 WHERE id = 1")
        assertThrows(IllegalStateException::class.java) { repository.find() }
    }

    // ------------------------------------------------------------------
    // service 的「同值不改锚点 / 变化更新 / 事务失败回滚」（真实提交与回滚）
    // ------------------------------------------------------------------

    @Test
    fun `the service keeps the anchor on an unchanged value and moves it on a change (I-1)`() {
        val first = serviceAt(Clock.fixed(AT_10, ZoneOffset.UTC)).save(3)

        assertTrue(first.view.saved, "设置必须真正落库")
        assertFalse(first.view.applied, "本切片没有调度器 bean，不得谎报已应用")
        assertEquals(DiscoveryScheduleStatus.UNAVAILABLE, first.status)
        assertEquals(iso(AT_10), first.view.anchorAt)

        // 11:00 重存 3：锚点不动，也不产生第二行。
        val at11 = Instant.parse("2026-09-29T03:00:00Z")
        val later = serviceAt(Clock.fixed(at11, ZoneOffset.UTC))
        val same = later.save(3)

        assertEquals(iso(AT_10), same.view.anchorAt, "相同值重存不得重置锚点")
        assertEquals(AT_10, repository.find()?.updatedAt)
        assertEquals(1, countRows())

        val changed = later.save(5)

        assertEquals(iso(at11), changed.view.anchorAt, "值变化才移动锚点")
        assertEquals(5, repository.find()?.intervalHours)
        assertEquals(at11, repository.find()?.updatedAt)
        assertEquals(1, countRows())
    }

    @Test
    fun `a failing transaction rolls the setting write back (I-1, I-3)`() {
        repository.save(3, AT_10)
        val template = TransactionTemplate(DataSourceTransactionManager(dataSource))

        assertThrows(IllegalStateException::class.java) {
            template.executeWithoutResult {
                repository.save(7, AT_13)
                throw IllegalStateException("boom")
            }
        }

        assertEquals(3, repository.find()?.intervalHours, "事务失败必须回滚，原调度设置不变")
        assertEquals(AT_10, repository.find()?.updatedAt)
        assertEquals(1, countRows())
    }
}
