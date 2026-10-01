package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource

/**
 * 01（c1）人工所在地配置真实 MySQL 集成测试（mysqlIt 门禁，`-Pmysql-it`）。
 *
 * 只连隔离库 `talent_contact_timing_it`（`@BeforeEach` 用 `SELECT DATABASE()` 守卫，
 * 连错库立即失败，绝不写业务库），Flyway 在上下文启动时从 V1 迁移到 V146。
 *
 * 证明（I-1～I-5 / IP-1～IP-3）：
 * - 迁移：新表列/主键/CHAR(2)/可空 zone_id、外键指向 expert_contact 且 DELETE CASCADE；
 * - I-1：未配置不建占位行；同 id 两次保存仍一行；不存在 contact 读/写 404（异常语义）；
 * - I-2：`br` 归一为 `BR`，`ZZ` 拒绝；目录快照在真实读回路径生效；
 * - I-3：null = 国家默认（DB 中 zone_id 为 NULL）、显式值属于该国、换配置不残留旧显式时区；
 * - I-4/IP-3：校验失败不产生任何写（前值保持）；
 * - IP-1/I-5：手工配置与 `expert_contact.country` 互不影响（回填原 country 不改新配置）；
 * - 持久性：换一条新连接/新服务实例（进程重启的 IT 等价物）仍读回同一配置；
 * - 外键：悬空 contact 被拒绝；contact 删除时级联清理本表。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
class ExpertContactLocationServiceIT {

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Value("\${spring.datasource.url}")
    private lateinit var dbUrl: String

    @Value("\${spring.datasource.username}")
    private lateinit var dbUser: String

    @Value("\${spring.datasource.password}")
    private lateinit var dbPassword: String

    private val catalog = ExpertContactLocationCatalog()

    private lateinit var service: ExpertContactLocationService

    @BeforeEach
    fun setUp() {
        assertEquals(
            ISOLATED_SCHEMA,
            jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java),
            "IT 只允许连接隔离测试库"
        )
        service = ExpertContactLocationService(NamedParameterJdbcTemplate(dataSource), catalog)
        cleanup()
        seed()
    }

    @AfterEach
    fun tearDown() {
        cleanup()
    }

    // ------------------------------------------------------------------
    // 迁移
    // ------------------------------------------------------------------

    @Test
    fun `migration creates the location table with contact primary key and cascade foreign key`() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expert_contact_location'
            ORDER BY ORDINAL_POSITION
            """.trimIndent()
        )
        assertEquals(
            listOf("expert_contact_id", "country_code", "zone_id"),
            columns.map { it["COLUMN_NAME"] }
        )
        assertEquals("PRI", columns[0]["COLUMN_KEY"])
        assertEquals("NO", columns[0]["IS_NULLABLE"])
        assertEquals("char(2)", columns[1]["COLUMN_TYPE"].toString().lowercase())
        assertEquals("NO", columns[1]["IS_NULLABLE"])
        assertEquals("varchar(64)", columns[2]["COLUMN_TYPE"].toString().lowercase())
        assertEquals("YES", columns[2]["IS_NULLABLE"], "zone_id 为空表示使用国家默认时区")

        val fk = jdbcTemplate.queryForMap(
            """
            SELECT REFERENCED_TABLE_NAME, DELETE_RULE
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_expert_contact_location_contact'
            """.trimIndent()
        )
        assertEquals("expert_contact", fk["REFERENCED_TABLE_NAME"])
        assertEquals("CASCADE", fk["DELETE_RULE"])

        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '146'",
                Int::class.java
            )
        )
    }

    // ------------------------------------------------------------------
    // I-1 / I-2 / I-3
    // ------------------------------------------------------------------

    @Test
    fun `unconfigured contact reads configured false and creates no row then default and explicit zones persist in one row`() {
        assertEquals(0, locationRowCount(CONTACT_A))

        val unconfigured = service.get(CONTACT_A)
        assertFalse(unconfigured.configured)
        assertNull(unconfigured.zoneId)
        assertNull(unconfigured.effectiveZoneId)
        assertNull(unconfigured.countryLabel)
        assertFalse(unconfigured.usingDefaultZone)
        assertEquals(0, locationRowCount(CONTACT_A), "GET 不得创建占位行")

        val withDefault = service.save("admin", CONTACT_A, SaveContactLocationRequest("br", null))
        assertEquals("BR", withDefault.countryCode, "小写国家码必须归一为大写")
        assertEquals("巴西", withDefault.countryLabel)
        assertNull(withDefault.zoneId)
        assertEquals("America/Sao_Paulo", withDefault.effectiveZoneId)
        assertEquals("巴西 · 圣保罗", withDefault.zoneLabel)
        assertTrue(withDefault.usingDefaultZone)
        assertEquals(1, locationRowCount(CONTACT_A))
        assertNull(
            jdbcTemplate.queryForObject("SELECT zone_id FROM expert_contact_location WHERE expert_contact_id = ?", String::class.java, CONTACT_A),
            "未选具体时区时 DB 必须存 NULL，不得回写展开后的默认值"
        )
        assertEquals(
            "BR",
            jdbcTemplate.queryForObject("SELECT country_code FROM expert_contact_location WHERE expert_contact_id = ?", String::class.java, CONTACT_A)
        )

        val explicit = service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "America/Manaus"))
        assertEquals("America/Manaus", explicit.zoneId)
        assertEquals("America/Manaus", explicit.effectiveZoneId)
        assertEquals("巴西 · 马瑙斯", explicit.zoneLabel)
        assertFalse(explicit.usingDefaultZone)
        assertEquals(1, locationRowCount(CONTACT_A), "同 id 两次保存仍只有一行")
        assertEquals(
            "America/Manaus",
            jdbcTemplate.queryForObject("SELECT zone_id FROM expert_contact_location WHERE expert_contact_id = ?", String::class.java, CONTACT_A)
        )

        val backToDefault = service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "  "))
        assertNull(backToDefault.zoneId, "空白时区归一为 null")
        assertTrue(backToDefault.usingDefaultZone)
        assertNull(
            jdbcTemplate.queryForObject("SELECT zone_id FROM expert_contact_location WHERE expert_contact_id = ?", String::class.java, CONTACT_A),
            "切回默认必须清空旧显式时区"
        )
        assertEquals(1, locationRowCount(CONTACT_A))
    }

    @Test
    fun `configuration survives a fresh connection and service instance`() {
        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "America/Manaus"))

        val reloaded = freshService().get(CONTACT_A)

        assertEquals("BR", reloaded.countryCode)
        assertEquals("America/Manaus", reloaded.effectiveZoneId)
        assertFalse(reloaded.usingDefaultZone)
        assertTrue(reloaded.configured)
    }

    // ------------------------------------------------------------------
    // I-2 / I-4 / IP-3
    // ------------------------------------------------------------------

    @Test
    fun `rejected writes leave the previous configuration untouched`() {
        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "America/Manaus"))

        val foreignZone = assertThrows(IllegalArgumentException::class.java) {
            service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "Asia/Tokyo"))
        }
        assertTrue(foreignZone.message!!.contains("不支持时区"))

        val unknownCountry = assertThrows(IllegalArgumentException::class.java) {
            service.save("admin", CONTACT_A, SaveContactLocationRequest("ZZ", null))
        }
        assertTrue(unknownCountry.message!!.contains("未知国家代码"))

        val blankUser = assertThrows(IllegalArgumentException::class.java) {
            service.save("  ", CONTACT_A, SaveContactLocationRequest("BR", null))
        }
        assertEquals("未登录", blankUser.message)

        val unchanged = service.get(CONTACT_A)
        assertEquals("BR", unchanged.countryCode)
        assertEquals("America/Manaus", unchanged.zoneId)
        assertEquals(1, locationRowCount(CONTACT_A))

        assertThrows(IllegalArgumentException::class.java) {
            service.save("admin", CONTACT_B, SaveContactLocationRequest("ZZ", null))
        }
        assertEquals(0, locationRowCount(CONTACT_B), "非法国家不得为任何 contact 建行")
    }

    @Test
    fun `missing contact is not found and dangling foreign key is rejected`() {
        assertThrows(NoSuchElementException::class.java) { service.get(ABSENT_CONTACT) }
        assertThrows(NoSuchElementException::class.java) {
            service.save("admin", ABSENT_CONTACT, SaveContactLocationRequest("BR", null))
        }
        assertEquals(0, locationRowCount(ABSENT_CONTACT))

        assertThrows(DataIntegrityViolationException::class.java) {
            jdbcTemplate.update(
                "INSERT INTO expert_contact_location (expert_contact_id, country_code, zone_id) VALUES (?, 'BR', NULL)",
                ABSENT_CONTACT
            )
        }
    }

    @Test
    fun `deleting the contact cascades the location row`() {
        service.save("admin", CONTACT_B, SaveContactLocationRequest("BR", null))
        assertEquals(1, locationRowCount(CONTACT_B))

        jdbcTemplate.update("DELETE FROM expert_contact WHERE id = ?", CONTACT_B)

        assertEquals(0, locationRowCount(CONTACT_B))
    }

    // ------------------------------------------------------------------
    // I-5 / IP-1
    // ------------------------------------------------------------------

    @Test
    fun `manual location and expert_contact country stay independent`() {
        val originalCountry = jdbcTemplate.queryForObject(
            "SELECT country FROM expert_contact WHERE id = ?", String::class.java, CONTACT_A
        )
        assertEquals("旧统计值", originalCountry)

        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "America/Manaus"))

        assertEquals(
            "旧统计值",
            jdbcTemplate.queryForObject("SELECT country FROM expert_contact WHERE id = ?", String::class.java, CONTACT_A),
            "人工配置不得改写 expert_contact.country"
        )
        val mailsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mail_record", Long::class.java)

        // 模拟既有画像/回填写入口改原 country：新配置必须完全不受影响。
        jdbcTemplate.update("UPDATE expert_contact SET country = ? WHERE id = ?", "画像回填值", CONTACT_A)

        assertEquals(
            "画像回填值",
            jdbcTemplate.queryForObject("SELECT country FROM expert_contact WHERE id = ?", String::class.java, CONTACT_A)
        )
        val after = service.get(CONTACT_A)
        assertEquals("BR", after.countryCode)
        assertEquals("America/Manaus", after.zoneId)
        assertEquals(
            mailsBefore,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mail_record", Long::class.java),
            "所在地配置不影响邮件行数"
        )
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun locationRowCount(contactId: Long): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact_location WHERE expert_contact_id = ?",
            Int::class.java,
            contactId
        ) ?: 0

    /** 进程重启的 IT 等价物：新连接 + 新服务实例，只从 MySQL 读回（无任何内存态）。 */
    private fun freshService(): ExpertContactLocationService =
        ExpertContactLocationService(
            NamedParameterJdbcTemplate(DriverManagerDataSource(dbUrl, dbUser, dbPassword)),
            ExpertContactLocationCatalog()
        )

    private fun cleanup() {
        jdbcTemplate.update("DELETE FROM expert_contact_location WHERE expert_contact_id BETWEEN ? AND ?", CONTACT_A, CONTACT_B)
        jdbcTemplate.update("DELETE FROM expert_contact WHERE id BETWEEN ? AND ?", CONTACT_A, CONTACT_B)
        jdbcTemplate.update("DELETE FROM campaign WHERE id = ?", CAMPAIGN_ID)
        jdbcTemplate.update("DELETE FROM mail_sender_account WHERE id = ?", SENDER_ID)
    }

    private fun seed() {
        jdbcTemplate.update(
            """
            INSERT INTO mail_sender_account
                (id, account_code, sender_email, sender_name, smtp_host, smtp_port,
                 smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
            VALUES (?, 'location-it', 'sender@example.test', 'Sender', 'smtp.test', 465,
                    'sender@example.test', 'pw', 'imap.test', 993, 'sender@example.test', 'pw')
            """.trimIndent(),
            SENDER_ID
        )
        jdbcTemplate.update(
            "INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) VALUES (?, 'LOCATION_IT', 'Location IT', ?)",
            CAMPAIGN_ID,
            SENDER_ID
        )
        jdbcTemplate.update(
            """
            INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name, country)
            VALUES (?, ?, 'location-it-a', 'a@example.test', 'A', '旧统计值'),
                   (?, ?, 'location-it-b', 'b@example.test', 'B', NULL)
            """.trimIndent(),
            CONTACT_A,
            CAMPAIGN_ID,
            CONTACT_B,
            CAMPAIGN_ID
        )
    }

    private companion object {
        const val ISOLATED_SCHEMA = "talent_contact_timing_it"
        const val SENDER_ID = 881000L
        const val CAMPAIGN_ID = 881000L
        const val CONTACT_A = 881001L
        const val CONTACT_B = 881002L
        const val ABSENT_CONTACT = 889999999L
    }
}
