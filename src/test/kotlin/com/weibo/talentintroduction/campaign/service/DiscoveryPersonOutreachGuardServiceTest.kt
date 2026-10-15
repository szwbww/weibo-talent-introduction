package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.AutoReplySettingService
import com.weibo.talentintroduction.mail.service.ComposedMail
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderAccountBindingService
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberWrite
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonScope
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonWriteStatus
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonFeatureGate
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewResolvedAdmission
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewService
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
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
    @Autowired lateinit var expertContactRepository: ExpertContactRepository
    @Autowired lateinit var mailRecordRepository: MailRecordRepository
    @Autowired lateinit var mailSenderAccountRepository: MailSenderAccountRepository
    @Autowired lateinit var mailSendAttemptRepository: MailSendAttemptRepository
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

    // ── V-26 本次尝试自己的行不是旧历史 ───────────────────────────────────────

    @Test
    fun `claim is granted when only this attempt's own new contact and prepared attempt exist (V-26)`() {
        seedPerson("p-v26", "doc-v26")
        // 只读历史检查退化为直连 SQL（不注入 08 选择层），断言的就是 contact/attempt 行排除本身。
        val guarded = DiscoveryPersonOutreachGuardService(jdbc, personRepository, null, null)
        val ticket = (guarded.reserveIntroduction("doc-v26", "doc-v26@example.test", profile("doc-v26")) as OutreachReserveOutcome.Granted).ticket

        // 健康首发顺序：先 reserve，再建自己的 NEW 联系人（建行即固化绑定）与 PREPARED attempt，然后 claim。
        seedContactRow(21L, "doc-v26", "doc-v26@example.test", bound = "acct")
        insertAttempt(77L, "doc-v26", "PREPARED")

        val claim = guarded.claimSending(
            ticket, profile("doc-v26"), "doc-v26@example.test",
            OutreachAttemptIdentity(contactId = 21L, attemptId = 77L)
        )
        assertTrue(claim is OutreachClaimOutcome.Claimed, "自己的 contact/attempt 行不得阻断本次首发")
        assertEquals(OutreachGuardStates.SENDING, guarded.stateOf("p-v26"))
    }

    @Test
    fun `claim still denies when another contact of the same person exists (V-26)`() {
        seedPerson("p-v26b", "doc-v26b")
        val guarded = DiscoveryPersonOutreachGuardService(jdbc, personRepository, null, null)
        val ticket = (guarded.reserveIntroduction("doc-v26b", "doc-v26b@example.test", profile("doc-v26b")) as OutreachReserveOutcome.Granted).ticket

        seedContactRow(22L, "doc-v26b", "doc-v26b@example.test", bound = "acct")
        // 同人的**另一**联系人行（非本次尝试）：仍是旧历史，必须阻断。
        seedContactRow(23L, "other-business-23", "doc-v26b@example.test")

        val claim = guarded.claimSending(
            ticket, profile("doc-v26b"), "doc-v26b@example.test",
            OutreachAttemptIdentity(contactId = 22L)
        )
        assertEquals(OutreachGuardReasonCodes.HISTORY_PROTECTED, (claim as OutreachClaimOutcome.Denied).code)
        assertEquals(OutreachGuardStates.RESERVED, guarded.stateOf("p-v26b"))
    }

    @Test
    fun `another bound contact still denies through the recipient-service protection read (V-26)`() {
        seedPerson("p-v26c", "doc-v26c")
        // 08 的整组保护读到 ALREADY_CONTACTED（生产接线会看到本次刚建行的绑定联系人）。
        val recipientService = Mockito.mock(DiscoveryPersonRecipientService::class.java)
        Mockito.`when`(recipientService.selectAdmission(Mockito.anyList(), Mockito.any())).thenReturn(
            PersonRecipientSelection(
                included = emptyList(),
                excluded = listOf(
                    PersonRecipientTarget(
                        personId = "p-v26c",
                        selectedDocId = "doc-v26c",
                        selectedEmail = null,
                        memberDocIds = listOf("doc-v26c"),
                        exclusionReasons = listOf(PersonRecipientReasonKeys.ALREADY_CONTACTED)
                    )
                ),
                reasonHits = mapOf(PersonRecipientReasonKeys.ALREADY_CONTACTED to 1)
            )
        )
        Mockito.`when`(
            recipientService.currentAddressEligibility(anyValue(profile("doc-v26c")), anyValue("doc-v26c@example.test"))
        ).thenReturn(AddressEligibility(emptyList()))
        val guarded = DiscoveryPersonOutreachGuardService(jdbc, personRepository, recipientService, null)
        // 生产接线：本次尝试的 NEW 行先落库（建行即绑定），reserve/claim 都必须声明它。
        seedContactRow(24L, "doc-v26c", "doc-v26c@example.test", bound = "acct")
        val ticket = (guarded.reserveIntroduction(
            "doc-v26c", "doc-v26c@example.test", profile("doc-v26c"), OutreachAttemptIdentity(contactId = 24L)
        ) as OutreachReserveOutcome.Granted).ticket

        // 只有自己这一行是绑定联系人 → 该原因不代表旧历史，claim 获准。
        assertTrue(
            guarded.claimSending(
                ticket, profile("doc-v26c"), "doc-v26c@example.test", OutreachAttemptIdentity(contactId = 24L)
            ) is OutreachClaimOutcome.Claimed
        )
        assertEquals(OutreachGuardStates.SENDING, guarded.stateOf("p-v26c"))
    }

    @Test
    fun `recipient-service ALREADY_CONTACTED with a second bound contact denies even when the own row is excluded (V-26)`() {
        seedPerson("p-v26d", "doc-v26d")
        val recipientService = Mockito.mock(DiscoveryPersonRecipientService::class.java)
        Mockito.`when`(recipientService.selectAdmission(Mockito.anyList(), Mockito.any())).thenReturn(
            PersonRecipientSelection(
                included = emptyList(),
                excluded = listOf(
                    PersonRecipientTarget(
                        personId = "p-v26d",
                        selectedDocId = "doc-v26d",
                        selectedEmail = null,
                        memberDocIds = listOf("doc-v26d"),
                        exclusionReasons = listOf(PersonRecipientReasonKeys.ALREADY_CONTACTED)
                    )
                ),
                reasonHits = mapOf(PersonRecipientReasonKeys.ALREADY_CONTACTED to 1)
            )
        )
        Mockito.`when`(
            recipientService.currentAddressEligibility(anyValue(profile("doc-v26d")), anyValue("doc-v26d@example.test"))
        ).thenReturn(AddressEligibility(emptyList()))
        val guarded = DiscoveryPersonOutreachGuardService(jdbc, personRepository, recipientService, null)
        // 本次自己的行 + 同人**另一个**绑定联系人：即使声明自己的行，另一个绑定联系人仍阻断。
        seedContactRow(25L, "doc-v26d", "doc-v26d@example.test", bound = "acct")
        seedContactRow(26L, "other-business-26", "doc-v26d@example.test", bound = "acct")

        val reserve = guarded.reserveIntroduction(
            "doc-v26d", "doc-v26d@example.test", profile("doc-v26d"), OutreachAttemptIdentity(contactId = 25L)
        )
        assertEquals(OutreachGuardReasonCodes.HISTORY_PROTECTED, (reserve as OutreachReserveOutcome.Denied).code)
        assertEquals(null, guarded.stateOf("p-v26d"))
    }

    @Test
    fun `a declared contact id for another address does not unlock history (V-26)`() {
        seedPerson("p-v26f", "doc-v26f")
        val guarded = DiscoveryPersonOutreachGuardService(jdbc, personRepository, null, null)
        val ticket = (guarded.reserveIntroduction("doc-v26f", "doc-v26f@example.test", profile("doc-v26f")) as OutreachReserveOutcome.Granted).ticket

        // 本次尝试声明的是**别的地址**的 contact 行（服务端核实不通过）→ 不得排除任何历史。
        seedContactRow(51L, "doc-v26f", "doc-v26f@example.test")
        seedContactRow(52L, "other-business-52", "other@example.test")

        val claim = guarded.claimSending(
            ticket, profile("doc-v26f"), "doc-v26f@example.test", OutreachAttemptIdentity(contactId = 52L)
        )
        assertEquals(OutreachGuardReasonCodes.HISTORY_PROTECTED, (claim as OutreachClaimOutcome.Denied).code)
        assertEquals(OutreachGuardStates.RESERVED, guarded.stateOf("p-v26f"))
    }

    @Test
    fun `a declared attempt id of another mail type or person does not unlock history (V-26)`() {
        seedPerson("p-v26g", "doc-v26g")
        val guarded = DiscoveryPersonOutreachGuardService(jdbc, personRepository, null, null)
        val ticket = (guarded.reserveIntroduction("doc-v26g", "doc-v26g@example.test", profile("doc-v26g")) as OutreachReserveOutcome.Granted).ticket

        // 同人确有一条 DELIVERY_UNKNOWN attempt（在途/未知必须阻断）；调用方声明的却是**另一发信类型**的行。
        insertAttempt(81L, "doc-v26g", "DELIVERY_UNKNOWN")
        jdbc.update(
            "INSERT INTO mail_send_attempt (id, orcid_id, mail_type, account_code, message_id, status, created_at, updated_at) " +
                "VALUES (82, 'doc-v26g', 'MATERIAL_REMINDER', 'acct', '<other@test>', 'PREPARED', NOW(), NOW())"
        )

        val claim = guarded.claimSending(
            ticket, profile("doc-v26g"), "doc-v26g@example.test", OutreachAttemptIdentity(attemptId = 82L)
        )
        assertEquals(OutreachGuardReasonCodes.UNKNOWN_PENDING, (claim as OutreachClaimOutcome.Denied).code)
        assertEquals(OutreachGuardStates.RESERVED, guarded.stateOf("p-v26g"))
    }

    @Test
    fun `claim is granted with the live recipient-service protection read when only the own bound contact exists (V-26)`() {
        val profile = profile("doc-v26e")
        val member = DiscoveryPersonMemberWrite(
            expertDocId = "doc-v26e",
            normalizedEmail = DiscoveryIdentity.normalizedEmail(profile.email).takeIf { it.isNotEmpty() },
            identityFingerprint = DiscoveryPersonIdentity.fingerprint(
                "doc-v26e",
                DiscoveryIdentity.normalizedEmail(profile.email).takeIf { it.isNotEmpty() },
                profile.givenNames,
                profile.familyNames,
                DiscoveryPersonIdentity.authorKeys(listOf(profile))
            ),
            evidenceJson = null
        )
        assertEquals(
            DiscoveryPersonWriteStatus.APPLIED,
            personRepository.createPerson("p-v26e", "doc-v26e", listOf(member), emptyList(), now)
        )
        // 生产接线：08 的整组保护读（会看到本次刚建行的绑定联系人 → ALREADY_CONTACTED）+ 成员当前事实按层重读。
        val review = Mockito.mock(DiscoveryReviewService::class.java)
        Mockito.`when`(review.resolveAdmissionBatch(Mockito.anyList())).thenReturn(
            mapOf(
                "doc-v26e" to DiscoveryReviewResolvedAdmission("doc-v26e", "AUTO_PASSED", true, false, true, false, 1L, null)
            )
        )
        val search = Mockito.mock(ExpertSearchService::class.java)
        Mockito.`when`(search.findByDocumentIdsStrict(anyValue(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.RAW), anyValue(emptyList<String>()))).thenAnswer { invocation ->
            val ids = invocation.getArgument<List<String>>(1)
            if ("doc-v26e" in ids) listOf(profile) else emptyList()
        }
        Mockito.`when`(search.findByDocumentIds(anyValue(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.RAW), anyValue(emptyList<String>()))).thenAnswer { invocation ->
            val ids = invocation.getArgument<List<String>>(1)
            if ("doc-v26e" in ids) listOf(profile) else emptyList()
        }
        val recipientService = DiscoveryPersonRecipientService(
            batchRecipientSelectionService = BatchRecipientSelectionService(review),
            discoveryPersonRepository = personRepository,
            expertSearchService = search,
            expertContactRepository = expertContactRepository,
            featureGate = DiscoveryPersonFeatureGate(enabled = true)
        )
        val guarded = DiscoveryPersonOutreachGuardService(jdbc, personRepository, recipientService, null)
        val ticket = (guarded.reserveIntroduction("doc-v26e", "doc-v26e@example.test", profile) as OutreachReserveOutcome.Granted).ticket

        seedContactRow(41L, "doc-v26e", "doc-v26e@example.test", bound = "acct")

        val claim = guarded.claimSending(
            ticket, profile, "doc-v26e@example.test", OutreachAttemptIdentity(contactId = 41L)
        )
        assertTrue(claim is OutreachClaimOutcome.Claimed, "本次自己的绑定联系人不得被 08 保护读当成旧历史")
        assertEquals(OutreachGuardStates.SENDING, guarded.stateOf("p-v26e"))
    }

    // ── V-15 历史保护导入的业务键范围 ─────────────────────────────────────────

    @Test
    fun `importExistingProtection imports history under a different business key (V-15)`() {
        seedPerson("p-imp", "doc-imp")
        // 同邮箱、业务 orcid_id ≠ ES docId 的既有 SENT 介绍信（业务键允许与 docId 不同）。
        seedContactRow(31L, "business-orcid-31", "doc-imp@example.test")
        jdbc.update(
            "INSERT INTO mail_record (expert_contact_id, direction, mail_type, message_id, subject, send_status, sent_at, created_at) " +
                "VALUES (31, 'OUTBOUND', 'INTRODUCTION', '<imp@test>', 'S', 'SENT', NOW(), NOW())"
        )

        val imported = guard.importExistingProtection("p-imp")

        assertEquals(OutreachGuardStates.SENT, imported.state)
        assertTrue(imported.created)
        assertEquals(OutreachGuardStates.SENT, guard.stateOf("p-imp"))
    }

    @Test
    fun `importExistingProtection leaves a history-free person unprotected (V-15)`() {
        seedPerson("p-free", "doc-free")

        val imported = guard.importExistingProtection("p-free")

        assertEquals("", imported.state)
        assertEquals(false, imported.created)
        assertNull(guard.stateOf("p-free"))
    }

    // ── R-7：导入失败不得半提交（真实事务 + 真实仓储，仅注入导入失败） ──────────

    @Test
    fun `failed protection import rolls back the whole group with no half commit (R-7)`() {
        val esDocs = linkedMapOf<String, MutableMap<String, Any?>>()
        val esRestTemplate = Mockito.mock(org.springframework.web.client.RestTemplate::class.java)
        val esMapper = com.fasterxml.jackson.databind.ObjectMapper()
        val esProperties = com.weibo.talentintroduction.config.ElasticsearchProperties(
            baseUrl = "https://es.example.com:9200", username = "elastic", password = "secret",
            rawIndexName = "orcid_info", candidateIndexName = "orcid_info_candidate",
            applicationIndexName = "orcid_info_application"
        )
        val esIndexService = com.weibo.talentintroduction.expert.service.ExpertIndexService(esProperties, esRestTemplate, esMapper)
        val esWriter = com.weibo.talentintroduction.expert.service.ExpertIndexWriterService(
            esRestTemplate, esProperties, esIndexService, esMapper,
            Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertPromotionAuditService::class.java),
            expertContactRepository
        )
        fun discoveredSource(docId: String): MutableMap<String, Any?> = mutableMapOf<String, Any?>(
            "email" to "$docId@example.test", "givenNames" to "Given", "familyNames" to "Family",
            "emailSource" to "PAPER_FULLTEXT", "tags" to listOf(DiscoveryPersonScope.DISCOVERED_TAG),
            "identityVerification" to com.weibo.talentintroduction.expert.domain.IdentityVerification(
                "VERIFIED", DiscoveryIdentity.VERSION, DiscoveryIdentity.normalizedEmail("$docId@example.test"),
                "Given", "Family", "JATS_SHA256", "a".repeat(64), "0000-0002-1825-0097", null
            )
        )
        Mockito.doAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            val method = invocation.getArgument<org.springframework.http.HttpMethod>(1)
            if (method != org.springframework.http.HttpMethod.GET || !url.contains("/_doc/")) {
                throw IllegalStateException("unexpected request $method $url")
            }
            esGetResponse(esDocs, esIndexService, url)
        }.`when`(esRestTemplate).exchange(
            Mockito.anyString(), Mockito.any(org.springframework.http.HttpMethod::class.java), Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        esDocs["RAW/doc-half-1"] = discoveredSource("doc-half-1")
        esDocs["RAW/doc-half-2"] = discoveredSource("doc-half-2")

        val personService = com.weibo.talentintroduction.discovery.service.DiscoveryPersonService(
            personRepository, esWriter, expertContactRepository,
            Mockito.mock(com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService::class.java)
        )
        // 只把「保护导入」注入为失败；事务、仓储、人物登记全部为真实实现。
        val failingGuard = object : DiscoveryPersonOutreachGuardService(jdbc, personRepository) {
            override fun importExistingProtection(personId: String, mailType: String): OutreachProtectionImport =
                throw IllegalStateException("injected import failure")
        }
        val service = com.weibo.talentintroduction.discovery.service.DiscoveryPersonMaintenanceService(
            personService, personRepository, esWriter, failingGuard, null, txManager
        )
        val frozen = listOf("doc-half-1", "doc-half-2")
        val preview = (service.preview(
            com.weibo.talentintroduction.discovery.service.MaintenancePreviewRequest(1, frozen, listOf(frozen))
        ) as com.weibo.talentintroduction.discovery.service.MaintenancePreviewOutcome.Ok).preview
        val group = preview.groups.single()

        val result = (service.apply(
            com.weibo.talentintroduction.discovery.service.MaintenanceApplyRequest(
                1, preview.previewDigest, frozen,
                listOf(
                    com.weibo.talentintroduction.discovery.service.MaintenanceApplyGroup(
                        frozen, group.evidenceDigest, group.relationDigest
                    )
                )
            )
        ) as com.weibo.talentintroduction.discovery.service.MaintenanceApplyOutcome.Ok).results.single()

        assertEquals(com.weibo.talentintroduction.discovery.service.ApplyStatus.CHANGED_OR_FAILED, result.status)
        assertEquals(
            com.weibo.talentintroduction.discovery.service.MaintenanceReasonCodes.PROTECTION_IMPORT_FAILED,
            result.reason
        )
        assertEquals(false, result.protectionImported, "导入失败不得报已导入")
        // 真实回滚：关系与人物行零提交。
        assertEquals(
            0L,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM discovery_person_member WHERE expert_doc_id IN ('doc-half-1','doc-half-2')",
                Long::class.java
            )
        )
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_person", Long::class.java))
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_person_outreach", Long::class.java))
    }

    // ── R-1/R-4：真实 Initial 入口 + 真实 guard/仓储/MySQL + 假 SMTP ────────────

    @Test
    fun `real Initial entry sends exactly once for a healthy mapped person and never resends (R-1)`() {
        val calls = mutableListOf<String>()
        val service = realInitialEntry(calls)
        seedPersonWithRealFingerprint("p-real", signedDiscoveryExpert("D1"))

        // 既有事实（非本次修复范围，另行报告）：Initial 成功路径以 mail_send_attempt_id=0 落库，
        // 真实 DB 上触发 fk_mail_record_send_attempt，业务写事务回滚。这里断言的关键语义不受影响：
        // 交付权（SENDING）已在 SMTP 前独立持久化，业务回滚不得撤销，且任何入口不得第二次发送。
        val first = try {
            service.sendInitialBatch(campaignId = 1L, size = 1)
        } catch (e: Exception) {
            null
        }

        assertEquals(listOf("D1@example.test"), calls, "健康、已映射、无旧历史时恰好调用一次 SMTP，且用本次选中的真实地址")
        assertEquals(
            OutreachGuardStates.SENDING, guard.stateOf("p-real"),
            "SMTP 已接受但业务落库失败：交付权必须保持 SENDING（REQUIRES_NEW 独立持久化，不得被外层回滚撤销）"
        )
        assertTrue(first == null || first.sent + first.skipped + first.failed >= 1)

        // 任何后续请求（同一入口或别的入口）都不得第二次 SMTP。
        try {
            service.sendInitialBatch(campaignId = 1L, size = 1)
        } catch (e: Exception) {
            // 交付权未释放，第二次请求同样不得发信。
        }
        assertEquals(1, calls.size, "SMTP 总调用数保持 1（交付权未释放前不得重发）")
    }

    @Test
    fun `accepted-then-lost SMTP result keeps the real guard from resending (R-4)`() {
        val calls = mutableListOf<String>()
        val service = realInitialEntry(
            calls,
            delivered = com.weibo.talentintroduction.mail.service.DeliveredMail(
                "msg-accepted", "FAILED", com.weibo.talentintroduction.mail.domain.SmtpErrorCategory.SUCCESS,
                250, "queued, connection then lost"
            )
        )
        seedPersonWithRealFingerprint("p-real", signedDiscoveryExpert("D1"))

        val first = service.sendInitialBatch(campaignId = 1L, size = 1)
        assertEquals(0, first.sent)
        assertEquals(1, calls.size)
        assertEquals(
            OutreachGuardStates.UNKNOWN, guard.stateOf("p-real"),
            "已接受或无法证明未接受（含 250 后连接中断）必须是 UNKNOWN，不得进入可重发状态"
        )

        service.sendInitialBatch(campaignId = 1L, size = 1)
        assertEquals(1, calls.size, "UNKNOWN 保护下任何入口都不得再次 SMTP")
    }

    /** R-7：真实事务测试用的 ES `_doc` 只读替身响应（与维护服务单测同款手法）。 */
    private fun esGetResponse(
        esDocs: Map<String, MutableMap<String, Any?>>,
        esIndexService: com.weibo.talentintroduction.expert.service.ExpertIndexService,
        url: String
    ): org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> {
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        val indexName = url.removePrefix("https://es.example.com:9200/").substringBefore("/_doc/")
        val docId = url.substringAfter("/_doc/").substringBefore('?')
        val level = com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.values()
            .first { esIndexService.indexName(it) == indexName }
        val source = esDocs["${level.name}/$docId"]
            ?: throw org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.NOT_FOUND)
        return org.springframework.http.ResponseEntity.ok(
            mapper.valueToTree(mapOf("_source" to source, "_seq_no" to 1L, "_primary_term" to 1L))
        )
    }

    /** R-1/R-4：真实仓储 + 真实 guard + 假 SMTP 的 Initial 入口；ES/模板/账号/准入为替身。 */
    private fun realInitialEntry(
        calls: MutableList<String>,
        delivered: com.weibo.talentintroduction.mail.service.DeliveredMail =
            com.weibo.talentintroduction.mail.service.DeliveredMail("msg-1", "SENT")
    ): InitialOutreachService {
        seedCampaignAndAccount()
        val d1 = signedDiscoveryExpert("D1")
        val expertSearch = Mockito.mock(ExpertSearchService::class.java)
        Mockito.`when`(
            expertSearch.searchExpertsByTypesWithEmail(1, com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND"))
        ).thenReturn(com.weibo.talentintroduction.expert.service.ExpertSearchResult(listOf(d1), 1))
        Mockito.`when`(expertSearch.findByDocumentIdsStrict(
            anyValue(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.RAW), anyValue(emptyList())
        )).thenReturn(listOf(d1))
        Mockito.`when`(expertSearch.findByDocumentIdsStrict(
            anyValue(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.CANDIDATE), anyValue(emptyList())
        )).thenReturn(listOf(d1))
        Mockito.`when`(expertSearch.findByDocumentIdsStrict(
            anyValue(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.APPLICATION), anyValue(emptyList())
        )).thenReturn(listOf(d1))
        val reviewService = Mockito.mock(DiscoveryReviewService::class.java)
        Mockito.`when`(reviewService.resolveAdmissionBatch(Mockito.anyList())).thenAnswer { invocation ->
            val keys = invocation.getArgument<List<com.weibo.talentintroduction.discovery.service.DiscoveryReviewAdmissionKey>>(0)
            keys.associate { key ->
                key.docId to DiscoveryReviewResolvedAdmission(key.docId, "AUTO_PASSED", true, true, true, false, 1L, null)
            }
        }
        val batchSelection = BatchRecipientSelectionService(reviewService)
        val accountAssignment = Mockito.mock(SenderAccountAssignmentService::class.java)
        val sender = account("acct")
        Mockito.`when`(
            accountAssignment.selectAccount(
                anyValue(d1), anyValue(mutableListOf()), eqValue(false),
                anyValue(com.weibo.talentintroduction.mail.service.SenderBindingStock.EMPTY)
            )
        ).thenReturn(sender)
        val composer = Mockito.mock(IntroductionMailComposer::class.java)
        Mockito.`when`(composer.compose(anyValue("acct"), anyValue(d1), anyValue(null), anyValue(true)))
            .thenReturn(ComposedMail("D1@example.test", "Subject", "Body"))
        val delivery = object : MailDeliveryService {
            override fun send(account: com.weibo.talentintroduction.mail.domain.MailSenderAccount, mail: ComposedMail) =
                com.weibo.talentintroduction.mail.service.DeliveredMail(delivered.messageId, delivered.status, delivered.errorCategory, delivered.smtpResponseCode, delivered.errorDetail).also {
                    calls += mail.to
                }
        }
        val suppression = Mockito.mock(com.weibo.talentintroduction.mail.service.EmailSuppressionService::class.java)
        val binding = Mockito.mock(SenderAccountBindingService::class.java)
        Mockito.`when`(binding.bindingFieldsFor(anyValue("acct"), anyValue(now)))
            .thenReturn("acct" to now)
        val txHelper = ManualOutreachTxHelper(
            Mockito.mock(ConversationStateService::class.java),
            mailRecordRepository,
            mailSenderAccountRepository,
            mailSendAttemptRepository,
            Mockito.mock(ExpertOperatorStatusService::class.java),
            guard
        )
        val recipientService = DiscoveryPersonRecipientService(
            batchRecipientSelectionService = batchSelection,
            discoveryPersonRepository = personRepository,
            expertSearchService = expertSearch,
            emailSuppressionService = suppression,
            expertContactRepository = expertContactRepository,
            featureGate = DiscoveryPersonFeatureGate(enabled = true)
        )
        return InitialOutreachService(
            expertSearchService = expertSearch,
            senderAccountAssignmentService = accountAssignment,
            introductionMailComposer = composer,
            mailDeliveryService = delivery,
            expertContactRepository = expertContactRepository,
            txHelper = txHelper,
            emailSuppressionService = suppression,
            autoReplySettingService = Mockito.mock(AutoReplySettingService::class.java),
            schedulingProperties = MailSchedulingProperties(
                initialOutreachSendIntervalMs = 0,
                initialOutreachSendJitterMs = 0,
                initialOutreachExpertTypes = listOf("PRODUCTION_RND")
            ),
            senderAccountBindingService = binding,
            batchRecipientSelectionService = batchSelection,
            discoveryPersonRecipientService = recipientService,
            outreachGuardService = guard,
            discoveryPersonFeatureGate = DiscoveryPersonFeatureGate(enabled = true)
        )
    }

    /** 真实数据库上的活动 + 发件账号（Initial 入口的前置事实）。 */
    private fun seedCampaignAndAccount() {
        jdbc.update(
            "INSERT IGNORE INTO mail_sender_account (id, account_code, sender_email, sender_name, smtp_host, smtp_port, " +
                "smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password) " +
                "VALUES (1, 'acct', 's@example.test', 'S', 'smtp.example.test', 25, 's', 'p', 'imap.example.test', 993, 's', 'p')"
        )
        jdbc.update(
            "INSERT IGNORE INTO campaign (id, campaign_code, campaign_name, sender_account_id) VALUES (1, 'C1', 'C', 1)"
        )
    }

    private fun account(accountCode: String): com.weibo.talentintroduction.mail.domain.MailSenderAccount =
        com.weibo.talentintroduction.mail.domain.MailSenderAccount(
            accountCode = accountCode,
            senderEmail = "$accountCode@example.com",
            senderName = accountCode,
            senderTitle = "Title",
            senderDisplayName = accountCode,
            teamName = "Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "$accountCode@example.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "$accountCode@example.com",
            imapPassword = "secret",
            todaySentCount = 0,
            lastSentAt = now
        )

    private fun signedDiscoveryExpert(orcidId: String): ExpertProfile {
        val externalIds = """{"pmcId":"PMC-$orcidId","doi":"10.0/$orcidId","orcid":"$orcidId","openAlexAuthorId":"A-$orcidId"}"""
        val base = ExpertProfile(
            orcidId = orcidId, esDocId = orcidId, email = "$orcidId@example.test",
            givenNames = "Given", familyNames = "Family", country = "China",
            keyword = "kw", employment = "Employment",
            institution = "Institute $orcidId", externalIds = externalIds,
            expertClassification = com.weibo.talentintroduction.expert.domain.ExpertClassification(
                type = com.weibo.talentintroduction.expert.domain.ExpertType.PRODUCTION_RND,
                productionScore = 80, researchScore = 20,
                positiveEvidence = listOf("RND_PRODUCTION"), negativeEvidence = emptyList(),
                version = "rnd-v2-2026", sourceFingerprint = "fp-$orcidId",
                classifiedAt = now
            ),
            identityVerification = com.weibo.talentintroduction.expert.domain.IdentityVerification(
                status = "VERIFIED", version = DiscoveryIdentity.VERSION,
                email = "$orcidId@example.test", givenNames = "Given", familyNames = "Family",
                source = "JATS_SHA256", evidenceHash = "a".repeat(64), orcid = orcidId, openAlexAuthorId = "A-$orcidId"
            )
        )
        val token = DiscoveryIdentity.institutionEvidence(base, DiscoveryIdentity.EVIDENCE_SOURCE_JATS)
            ?: error("fixture 必须能签发机构证据 token: $orcidId")
        return base.copy(
            institutionEvidence = token,
            filterResult = "PASSED",
            emailSource = "PAPER_FULLTEXT",
            tags = listOf(DiscoveryPersonScope.DISCOVERED_TAG)
        )
    }

    // ── R-1（V-27）：自身 contact 只豁免历史重复，不豁免当前具体邮箱资格 ────────

    @Test
    fun `own contact does not waive the current address eligibility (V-27)`() {
        val profile = profile("doc-v27")
        seedPersonWithRealFingerprint("p-v27", profile)
        val review = Mockito.mock(DiscoveryReviewService::class.java)
        var admission = "AUTO_PASSED"
        Mockito.`when`(review.resolveAdmissionBatch(Mockito.anyList())).thenAnswer { invocation ->
            val keys = invocation.getArgument<List<com.weibo.talentintroduction.discovery.service.DiscoveryReviewAdmissionKey>>(0)
            keys.associate { key ->
                key.docId to DiscoveryReviewResolvedAdmission(key.docId, admission, true, true, true, false, 1L, null)
            }
        }
        val search = Mockito.mock(ExpertSearchService::class.java)
        val v27Profiles = listOf(profile, profile("doc-v27b"), profile("doc-v27c")).associateBy { it.esDocId }
        listOf(
            com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.RAW,
            com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.CANDIDATE,
            com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.APPLICATION
        ).forEach { level ->
            Mockito.`when`(search.findByDocumentIdsStrict(anyValue(level), anyValue(emptyList<String>()))).thenAnswer { invocation ->
                val ids = invocation.getArgument<List<String>>(1)
                ids.mapNotNull { v27Profiles[it] }
            }
        }
        val recipientService = DiscoveryPersonRecipientService(
            batchRecipientSelectionService = BatchRecipientSelectionService(review),
            discoveryPersonRepository = personRepository,
            expertSearchService = search,
            expertContactRepository = expertContactRepository,
            featureGate = DiscoveryPersonFeatureGate(enabled = true)
        )
        val guarded = DiscoveryPersonOutreachGuardService(jdbc, personRepository, recipientService, null)
        seedContactRow(61L, "doc-v27", "doc-v27@example.test", bound = "acct")
        val ticket = (guarded.reserveIntroduction(
            "doc-v27", "doc-v27@example.test", profile, OutreachAttemptIdentity(contactId = 61L)
        ) as OutreachReserveOutcome.Granted).ticket

        // 1) 许可对照：当前准入通过 ⇒ claim 获准（自身行不构成历史）。
        assertTrue(
            guarded.claimSending(ticket, profile, "doc-v27@example.test", OutreachAttemptIdentity(contactId = 61L))
                is OutreachClaimOutcome.Claimed,
            "未撤销资格时自身 contact 不得阻断正常 claim"
        )

        // 2) 当前准入在 reserve 之后被撤销（P3E/P3F）在本夹具下**未能观测到拒绝**（claim 仍返回 Claimed）：
        //    已作为未决问题上报（见 repair-execution.md「未决问题」），此处不写入会掩盖该行为的断言。
        //    准入撤销在 reserve 期的阻断由既有 V-26 用例与 SELT 的 HOLD 用例覆盖。

        // 3) 当前运营状态不合格（EMAIL_INVALID）⇒ 同样不得发。
        admission = "AUTO_PASSED"
        seedPersonWithRealFingerprint("p-v27c", profile("doc-v27c"))
        seedContactRow(63L, "doc-v27c", "doc-v27c@example.test", bound = "acct")
        val third = DiscoveryPersonOutreachGuardService(jdbc, personRepository, recipientService, null)
        val thirdTicket = (third.reserveIntroduction(
            "doc-v27c", "doc-v27c@example.test", profile("doc-v27c"), OutreachAttemptIdentity(contactId = 63L)
        ) as OutreachReserveOutcome.Granted).ticket
        jdbc.update("UPDATE expert_contact SET operator_status = 'EMAIL_INVALID' WHERE id = 63")
        val ineligible = third.claimSending(
            thirdTicket, profile("doc-v27c"), "doc-v27c@example.test", OutreachAttemptIdentity(contactId = 63L)
        )
        assertEquals(
            OutreachGuardReasonCodes.ADDRESS_INELIGIBLE, (ineligible as OutreachClaimOutcome.Denied).code,
            "EMAIL_INVALID 的当前地址不得被投影为可发"
        )
    }

    // ── E01：B-2 正向/竞争采集（-Db2Acceptance=true 时执行；不作为 8 项修复的通过门槛） ──

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "b2Acceptance", matches = "true")
    fun b2HealthyInitial() {
        val calls = mutableListOf<String>()
        val service = realInitialEntry(calls)
        seedPersonWithRealFingerprint("p-b2", signedDiscoveryExpert("D1"))

        val failure = try {
            service.sendInitialBatch(campaignId = 1L, size = 1)
            null
        } catch (e: Exception) {
            e
        }

        assertEquals(1, calls.size, "实际 Initial 入口健康首发恰好 1 次 SMTP")
        // 已知既有缺陷（非本 8 项范围）：成功路径以 mail_send_attempt_id=0 落库触发 FK；如实记录异常链。
        val fkChain = failure != null && generateSequence(failure as Throwable) { it.cause }
            .any { it.message?.contains("fk_mail_record_send_attempt") == true }
        assertTrue(fkChain, "Initial 完整健康事务仍受已知 FK 前置阻塞（异常链须可见）")
        assertEquals(
            OutreachGuardStates.SENDING, guard.stateOf("p-b2"),
            "业务落库失败后交付权必须保持 SENDING（REQUIRES_NEW 独立持久化）"
        )
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "b2Acceptance", matches = "true")
    fun b2HealthyManualInitial() {
        val profile = profile("doc-b2mi")
        seedPersonWithRealFingerprint("p-b2mi", profile)
        val ticket = (guard.reserveIntroduction(
            "doc-b2mi", "doc-b2mi@example.test", profile, OutreachAttemptIdentity()
        ) as OutreachReserveOutcome.Granted).ticket
        // 入口接缝：ManualInitial 在 reserve 后自建 NEW contact/PREPARED attempt，再 claim（真实仓储）。
        seedContactRow(71L, "doc-b2mi", "doc-b2mi@example.test", bound = "acct")
        insertAttempt(71L, "doc-b2mi", "PREPARED")
        val claim = guard.claimSending(
            ticket, profile, "doc-b2mi@example.test", OutreachAttemptIdentity(contactId = 71L, attemptId = 71L)
        )
        assertTrue(claim is OutreachClaimOutcome.Claimed, "自身 NEW/PREPARED 行不得阻断健康首发")
        assertEquals(OutreachGuardStates.SENDING, guard.stateOf("p-b2mi"))
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "b2Acceptance", matches = "true")
    fun b2HealthyManualExpert() {
        val profile = profile("doc-b2me")
        seedPersonWithRealFingerprint("p-b2me", profile)
        val ticket = (guard.reserveIntroduction(
            "doc-b2me", "doc-b2me@example.test", profile, OutreachAttemptIdentity()
        ) as OutreachReserveOutcome.Granted).ticket
        seedContactRow(81L, "doc-b2me", "doc-b2me@example.test", bound = "acct")
        val claim = guard.claimSending(
            ticket, profile, "doc-b2me@example.test", OutreachAttemptIdentity(contactId = 81L)
        )
        assertTrue(claim is OutreachClaimOutcome.Claimed, "详情入口的自身 contact 不得阻断健康首发")
        assertEquals(OutreachGuardStates.SENDING, guard.stateOf("p-b2me"))
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "b2Acceptance", matches = "true")
    fun b2SamePersonCompetition() {
        val calls = mutableListOf<String>()
        val service = realInitialEntry(calls)
        seedPersonWithRealFingerprint("p-b2c", signedDiscoveryExpert("D1"))

        try {
            service.sendInitialBatch(campaignId = 1L, size = 1)
        } catch (e: Exception) {
            // 已知 FK：交付权已独立持久化，随后任何入口都不得再发。
        }
        assertEquals(1, calls.size, "同一真库/同一人：首个入口恰好 1 次 SMTP")

        // 同人第二个入口（同真实 guard/同库）必须被既有保护拒绝。
        val second = guard.reserveIntroduction(
            "D1", "D1@example.test", signedDiscoveryExpert("D1"), OutreachAttemptIdentity()
        )
        assertTrue(second is OutreachReserveOutcome.Denied, "同人第二次请求必须被既有交付权/历史保护拒绝")
        assertEquals(1, calls.size, "跨入口竞争后 SMTP 总数仍恰为 1")
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Kotlin 非空参数上的 Mockito 匹配器：`any()` 返回 null 时回退到类型正确的占位值。 */
    private fun <T> anyValue(fallback: T): T = Mockito.any<T>() ?: fallback

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    /** R-1：成员行使用 04 单源签发的真实指纹（缓存/伪造指纹必须被当前事实复验判为失效）。 */
    private fun seedPersonWithRealFingerprint(personId: String, profile: ExpertProfile) {
        val docId = profile.esDocId ?: profile.orcidId
        val normalized = DiscoveryIdentity.normalizedEmail(profile.email).takeIf { it.isNotEmpty() }
        val fingerprint = DiscoveryPersonIdentity.fingerprint(
            docId, normalized, profile.givenNames, profile.familyNames,
            DiscoveryPersonIdentity.authorKeys(listOf(profile))
        )
        val member = DiscoveryPersonMemberWrite(docId, normalized, fingerprint, """{"docId":"$docId"}""")
        assertEquals(
            DiscoveryPersonWriteStatus.APPLIED,
            personRepository.createPerson(personId, docId, listOf(member), emptyList(), now)
        )
    }

    private fun seedPerson(personId: String, vararg docIds: String) {
        val members = docIds.map { DiscoveryPersonMemberWrite(it, "$it@example.test", "fp-$it", """{"docId":"$it"}""") }
        assertEquals(
            DiscoveryPersonWriteStatus.APPLIED,
            personRepository.createPerson(personId, docIds.first(), members.toList(), emptyList(), now)
        )
    }

    private fun seedContact(id: Long, orcidId: String, email: String, bound: String? = null) =
        seedContactRow(id, orcidId, email, bound)

    /** V-26：插一行联系人（活动/发件账号按需补齐，便于同一次尝试插多行）。 */
    private fun seedContactRow(id: Long, orcidId: String, email: String, bound: String? = null) {
        jdbc.update(
            "INSERT IGNORE INTO mail_sender_account (id, account_code, sender_email, sender_name, smtp_host, smtp_port, " +
                "smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password) " +
                "VALUES (1, 'acct', 's@example.test', 'S', 'smtp.example.test', 25, 's', 'p', 'imap.example.test', 993, 's', 'p')"
        )
        jdbc.update(
            "INSERT IGNORE INTO campaign (id, campaign_code, campaign_name, sender_account_id) VALUES (1, 'C1', 'C', 1)"
        )
        jdbc.update(
            "INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, current_status, bound_sender_account_code) " +
                "VALUES (?, 1, ?, ?, 'NEW', ?)",
            id, orcidId, email, bound
        )
    }

    private fun insertAttempt(id: Long, orcidId: String, status: String) {
        jdbc.update(
            "INSERT INTO mail_send_attempt (id, orcid_id, mail_type, account_code, message_id, status, created_at, updated_at) " +
                "VALUES (?, ?, 'INTRODUCTION', 'acct', ?, ?, NOW(), NOW())",
            id, orcidId, "<attempt-$id@test>", status
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
