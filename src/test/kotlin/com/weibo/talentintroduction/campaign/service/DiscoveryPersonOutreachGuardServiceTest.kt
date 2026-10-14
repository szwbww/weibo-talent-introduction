package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberWrite
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonScope
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonWriteStatus
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 09（I-1/I-2/I-3）：`DiscoveryPersonOutreachGuardService` 的**真实数据库**并发/状态机验收。
 * Testcontainers 引导与 `DiscoveryPersonRepositoryTest` 同款（`@DataJdbcTest` + `Replace.NONE` +
 * `MySQLContainer` + `@DynamicPropertySource` + `-DmysqlIt=true`）。真实表/唯一键/行锁与 CAS SQL，
 * 无 SMTP（本服务不发信）。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
@Import(DiscoveryPersonRepository::class, DiscoveryPersonOutreachGuardService::class)
class DiscoveryPersonOutreachGuardServiceTest {
    companion object {
        private class Mysql(image: String) : MySQLContainer<Mysql>(image)
        private val mysql = Mysql("mysql:8.0.36").withDatabaseName("talent_introduction")
            .withUsername("test").withPassword("test")

        @JvmStatic @BeforeAll fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) {
                "Docker is required for discovery person outreach guard MySQL IT"
            }
            mysql.start()
        }

        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var personRepository: DiscoveryPersonRepository
    @Autowired lateinit var guard: DiscoveryPersonOutreachGuardService
    @Autowired lateinit var txManager: PlatformTransactionManager

    private val now = LocalDateTime.of(2026, 10, 13, 9, 30)

    @BeforeEach fun setup() {
        jdbc.update("DELETE FROM discovery_person_outreach")
        jdbc.update("DELETE FROM discovery_person_member")
        jdbc.update("DELETE FROM discovery_person")
        jdbc.update("DELETE FROM discovery_person_author_key")
        jdbc.update("DELETE FROM mail_record")
        jdbc.update("DELETE FROM mail_send_attempt")
        jdbc.update("DELETE FROM expert_email_alias")
        jdbc.update("DELETE FROM expert_contact")
        jdbc.update("DELETE FROM campaign")
        jdbc.update("DELETE FROM mail_sender_account")
    }

    // ── I-1 并发互斥 ─────────────────────────────────────────────────────────

    @Test
    fun `two workers racing for one person grant exactly one reservation`() {
        seedPerson("p-race", "doc-race")
        val pool = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val futures = (0 until 2).map {
                pool.submit<OutreachReserveOutcome> {
                    start.await()
                    guard.reserveIntroduction("doc-race", "doc-race@example.test", profile("doc-race"))
                }
            }
            start.countDown()
            val outcomes = futures.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, outcomes.count { it is OutreachReserveOutcome.Granted }, "exactly one worker must win")
            assertEquals(
                1,
                outcomes.count {
                    it is OutreachReserveOutcome.Denied && it.code == OutreachGuardReasonCodes.IN_FLIGHT
                },
                "the loser must be denied as IN_FLIGHT"
            )
            assertEquals(
                1L,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM discovery_person_outreach WHERE person_id = 'p-race'",
                    Long::class.java
                )
            )
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `different persons do not block each other`() {
        seedPerson("p-a", "doc-a")
        seedPerson("p-b", "doc-b")
        assertTrue(guard.reserveIntroduction("doc-a", "doc-a@example.test", profile("doc-a")) is OutreachReserveOutcome.Granted)
        assertTrue(guard.reserveIntroduction("doc-b", "doc-b@example.test", profile("doc-b")) is OutreachReserveOutcome.Granted)
    }

    // ── I-2 状态机保守推进 ───────────────────────────────────────────────────

    @Test
    fun `reserve then claim then sent transitions monotonically`() {
        seedPerson("p-1", "doc-1")
        val granted = guard.reserveIntroduction("doc-1", "doc-1@example.test", profile("doc-1"))
        val ticket = (granted as OutreachReserveOutcome.Granted).ticket
        assertEquals(OutreachGuardStates.RESERVED, guard.stateOf("p-1"))

        // 未 claim 前不得重占（RESERVED 已在途）。
        assertEquals(
            OutreachGuardReasonCodes.IN_FLIGHT,
            (guard.reserveIntroduction("doc-1", "doc-1@example.test", profile("doc-1")) as OutreachReserveOutcome.Denied).code
        )

        assertTrue(guard.claimSending(ticket, profile("doc-1"), "doc-1@example.test") is OutreachClaimOutcome.Claimed)
        assertEquals(OutreachGuardStates.SENDING, guard.stateOf("p-1"))

        assertTrue(guard.markSent(ticket, "<msg@test>", 42L))
        assertEquals(OutreachGuardStates.SENT, guard.stateOf("p-1"))
        val row = guard.findReservation("p-1")!!
        assertEquals("<msg@test>", row.messageId)
        assertEquals(42L, row.attemptId)

        // 已发送后不可再次首发。
        assertEquals(
            OutreachGuardReasonCodes.ALREADY_SENT,
            (guard.reserveIntroduction("doc-1", "doc-1@example.test", profile("doc-1")) as OutreachReserveOutcome.Denied).code
        )
    }

    @Test
    fun `SENDING interruption never unlocks even after time passes`() {
        seedPerson("p-stuck", "doc-stuck")
        val ticket = (guard.reserveIntroduction("doc-stuck", "doc-stuck@example.test", profile("doc-stuck")) as OutreachReserveOutcome.Granted).ticket
        assertTrue(guard.claimSending(ticket, profile("doc-stuck"), "doc-stuck@example.test") is OutreachClaimOutcome.Claimed)

        // 模拟时间流逝（无分布式锁/超时解锁路径）。
        jdbc.update(
            "UPDATE discovery_person_outreach SET updated_at = DATE_SUB(NOW(), INTERVAL 2 DAY) WHERE person_id = 'p-stuck'"
        )
        val retry = guard.reserveIntroduction("doc-stuck", "doc-stuck@example.test", profile("doc-stuck"))
        assertEquals(
            OutreachGuardReasonCodes.UNKNOWN_PENDING,
            (retry as OutreachReserveOutcome.Denied).code
        )
        assertEquals(OutreachGuardStates.SENDING, guard.stateOf("p-stuck"))
    }

    @Test
    fun `markUnknown blocks automatic resend`() {
        seedPerson("p-u", "doc-u")
        val ticket = (guard.reserveIntroduction("doc-u", "doc-u@example.test", profile("doc-u")) as OutreachReserveOutcome.Granted).ticket
        guard.claimSending(ticket, profile("doc-u"), "doc-u@example.test")
        assertTrue(guard.markUnknown(ticket))
        assertEquals(OutreachGuardStates.UNKNOWN, guard.stateOf("p-u"))
        assertEquals(
            OutreachGuardReasonCodes.UNKNOWN_PENDING,
            (guard.reserveIntroduction("doc-u", "doc-u@example.test", profile("doc-u")) as OutreachReserveOutcome.Denied).code
        )
    }

    @Test
    fun `safe retry is bound to the original recipient only`() {
        seedPerson("p-r", "doc-r")
        val ticket = (guard.reserveIntroduction("doc-r", "doc-r@example.test", profile("doc-r")) as OutreachReserveOutcome.Granted).ticket
        // 明确未交付（未调用 SMTP）→ SAFE_RETRY。
        assertTrue(guard.markSafeRetry(ticket))
        assertEquals(OutreachGuardStates.SAFE_RETRY, guard.stateOf("p-r"))

        // 换邮箱重试被拒（不自动换址）。
        assertEquals(
            OutreachGuardReasonCodes.ADDRESS_INELIGIBLE,
            (guard.reserveIntroduction("doc-r", "other@example.test", profile("doc-r")) as OutreachReserveOutcome.Denied).code
        )
        // 原地址重试获准，重新占位。
        val regranted = guard.reserveIntroduction("doc-r", "doc-r@example.test", profile("doc-r"))
        assertTrue(regranted is OutreachReserveOutcome.Granted)
        assertEquals(OutreachGuardStates.RESERVED, guard.stateOf("p-r"))
    }

    @Test
    fun `safe retry re-checks the whole group and blocks when history appeared meanwhile (I-3)`() {
        seedPerson("p-rh", "doc-rh")
        val ticket = (guard.reserveIntroduction("doc-rh", "doc-rh@example.test", profile("doc-rh")) as OutreachReserveOutcome.Granted).ticket
        assertTrue(guard.markSafeRetry(ticket))
        // SAFE_RETRY 期间同人新增历史联系人 → 重授前必须重跑整组历史检查并阻断。
        seedContact(7L, "doc-rh", "doc-rh@example.test", bound = "acct")
        val denied = guard.reserveIntroduction("doc-rh", "doc-rh@example.test", profile("doc-rh"))
        assertEquals(OutreachGuardReasonCodes.HISTORY_PROTECTED, (denied as OutreachReserveOutcome.Denied).code)
        assertEquals(OutreachGuardStates.SAFE_RETRY, guard.stateOf("p-rh"))
    }

    @Test
    fun `reserve refuses an unreadable profile for a known person (I-3)`() {
        seedPerson("p-np", "doc-np")
        val denied = guard.reserveIntroduction("doc-np", "doc-np@example.test", null)
        assertEquals(OutreachGuardReasonCodes.PERSON_MISSING, (denied as OutreachReserveOutcome.Denied).code)
        assertNull(guard.stateOf("p-np"))
    }

    @Test
    fun `claim refuses when the live discovered tag was removed (I-3)`() {
        seedPerson("p-tag", "doc-tag")
        val ticket = (guard.reserveIntroduction("doc-tag", "doc-tag@example.test", profile("doc-tag")) as OutreachReserveOutcome.Granted).ticket
        val untagged = profile("doc-tag").copy(tags = emptyList())
        val claim = guard.claimSending(ticket, untagged, "doc-tag@example.test")
        assertEquals(OutreachGuardReasonCodes.ADDRESS_INELIGIBLE, (claim as OutreachClaimOutcome.Denied).code)
        assertEquals(OutreachGuardStates.RESERVED, guard.stateOf("p-tag"))
    }

    @Test
    fun `claim refuses an unreadable profile instead of degrading to an ordinary mail (I-3)`() {
        seedPerson("p-null", "doc-null")
        val ticket = (guard.reserveIntroduction("doc-null", "doc-null@example.test", profile("doc-null")) as OutreachReserveOutcome.Granted).ticket
        val claim = guard.claimSending(ticket, null, "doc-null@example.test")
        assertEquals(OutreachGuardReasonCodes.PERSON_MISSING, (claim as OutreachClaimOutcome.Denied).code)
        assertEquals(OutreachGuardStates.RESERVED, guard.stateOf("p-null"))
    }

    @Test
    fun `delivery claim survives an outer business transaction rollback (V-7)`() {
        seedPerson("p-tx", "doc-tx")
        val ticket = (guard.reserveIntroduction("doc-tx", "doc-tx@example.test", profile("doc-tx")) as OutreachReserveOutcome.Granted).ticket
        val tx = TransactionTemplate(txManager)
        assertThrows<IllegalStateException> {
            tx.executeWithoutResult {
                assertTrue(guard.claimSending(ticket, profile("doc-tx"), "doc-tx@example.test") is OutreachClaimOutcome.Claimed)
                // 假 SMTP 已交付后业务写失败 → 外层事务回滚
                throw IllegalStateException("business write failed")
            }
        }
        // REQUIRES_NEW 的交付权（SENDING）不被外层回滚撤销 → 不可再次占位/重发。
        assertEquals(OutreachGuardStates.SENDING, guard.stateOf("p-tx"))
        assertEquals(
            OutreachGuardReasonCodes.UNKNOWN_PENDING,
            (guard.reserveIntroduction("doc-tx", "doc-tx@example.test", profile("doc-tx")) as OutreachReserveOutcome.Denied).code
        )
    }

    @Test
    fun `revision change between reserve and send denies the delivery claim`() {
        seedPerson("p-rev", "doc-rev")
        val ticket = (guard.reserveIntroduction("doc-rev", "doc-rev@example.test", profile("doc-rev")) as OutreachReserveOutcome.Granted).ticket
        // 挂接新成员 → 人物 revision 递增。
        assertEquals(
            DiscoveryPersonWriteStatus.APPLIED,
            personRepository.attachToPerson(
                "p-rev",
                listOf(DiscoveryPersonMemberWrite("doc-extra", "extra@example.test", "fp-extra", null)),
                emptyList(),
                now
            )
        )
        val claim = guard.claimSending(ticket, profile("doc-rev"), "doc-rev@example.test")
        assertEquals(
            OutreachGuardReasonCodes.REVISION_CHANGED,
            (claim as OutreachClaimOutcome.Denied).code
        )
        assertEquals(OutreachGuardStates.RESERVED, guard.stateOf("p-rev"))
    }

    // ── I-3 历史保护不可绕过 ─────────────────────────────────────────────────

    @Test
    fun `existing sent introduction blocks a fresh reservation`() {
        seedPerson("p-h", "doc-h")
        seedContact(1L, "doc-h", "doc-h@example.test")
        jdbc.update(
            "INSERT INTO mail_record (expert_contact_id, direction, mail_type, message_id, subject, send_status, sent_at, created_at) " +
                "VALUES (1, 'OUTBOUND', 'INTRODUCTION', '<m@test>', 'S', 'SENT', NOW(), NOW())"
        )
        val denied = guard.reserveIntroduction("doc-h", "doc-h@example.test", profile("doc-h"))
        assertEquals(OutreachGuardReasonCodes.ALREADY_SENT, (denied as OutreachReserveOutcome.Denied).code)
        assertNull(guard.stateOf("p-h"))
    }

    @Test
    fun `existing bound contact blocks a fresh reservation`() {
        seedPerson("p-b", "doc-b2")
        seedContact(2L, "doc-b2", "doc-b2@example.test", bound = "acct")
        val denied = guard.reserveIntroduction("doc-b2", "doc-b2@example.test", profile("doc-b2"))
        assertEquals(OutreachGuardReasonCodes.HISTORY_PROTECTED, (denied as OutreachReserveOutcome.Denied).code)
    }

    @Test
    fun `ambiguous mail send attempt blocks a fresh reservation`() {
        seedPerson("p-att", "doc-att")
        jdbc.update(
            "INSERT INTO mail_send_attempt (orcid_id, mail_type, account_code, message_id, status, created_at, updated_at) " +
                "VALUES ('doc-att', 'INTRODUCTION', 'acct', '<att@test>', 'DELIVERY_UNKNOWN', NOW(), NOW())"
        )
        val denied = guard.reserveIntroduction("doc-att", "doc-att@example.test", profile("doc-att"))
        assertEquals(OutreachGuardReasonCodes.UNKNOWN_PENDING, (denied as OutreachReserveOutcome.Denied).code)
    }

    @Test
    fun `unmapped docId is fail-closed as person missing`() {
        val denied = guard.reserveIntroduction("no-such-doc", "x@example.test", null)
        assertEquals(OutreachGuardReasonCodes.PERSON_MISSING, (denied as OutreachReserveOutcome.Denied).code)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun seedPerson(personId: String, vararg docIds: String) {
        val members = docIds.map { DiscoveryPersonMemberWrite(it, "$it@example.test", "fp-$it", """{"docId":"$it"}""") }
        assertEquals(
            DiscoveryPersonWriteStatus.APPLIED,
            personRepository.createPerson(personId, docIds.first(), members.toList(), emptyList(), now)
        )
    }

    private fun seedContact(id: Long, orcidId: String, email: String, bound: String? = null) {
        jdbc.update(
            "INSERT INTO mail_sender_account (id, account_code, sender_email, sender_name, smtp_host, smtp_port, " +
                "smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password) " +
                "VALUES (1, 'acct', 's@example.test', 'S', 'smtp.example.test', 25, 's', 'p', 'imap.example.test', 993, 's', 'p')"
        )
        jdbc.update(
            "INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) VALUES (1, 'C1', 'C', 1)"
        )
        jdbc.update(
            "INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, current_status, bound_sender_account_code) " +
                "VALUES (?, 1, ?, ?, 'NEW', ?)",
            id, orcidId, email, bound
        )
    }

    private fun profile(docId: String): ExpertProfile = ExpertProfile(
        orcidId = docId,
        esDocId = docId,
        email = "$docId@example.test",
        givenNames = "Given",
        familyNames = "Family",
        country = "China",
        keyword = "kw",
        employment = "Employment",
        emailSource = "PAPER_FULLTEXT",
        // 09：门禁要求画像仍属**实时 discovered**（来源证据 + 字面标签同时成立）。
        tags = listOf(DiscoveryPersonScope.DISCOVERED_TAG)
    )

    @Suppress("unused")
    private fun assertTicket(ticket: OutreachGuardTicket?) {
        assertNotNull(ticket)
    }
}
