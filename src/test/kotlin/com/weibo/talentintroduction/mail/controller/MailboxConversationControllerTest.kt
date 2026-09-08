package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import com.weibo.talentintroduction.mail.service.ExpertFollowService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailboxConversationService
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * 专家会话关注/查询 controller 集成测试（fast-p 07；mysqlIt 门禁）。
 *
 * 采用 @WebMvcTest（web slice，不加载其它 controller；全量 @SpringBootTest 会触发
 * 06 与 campaign 的既有 /api/expert-contacts/{contactId}/materials 映射冲突——那是
 * 07 之前就存在的跨 child 问题，不在本 child 授权文件内修复）。
 *
 * 关注幂等与 Session 身份断言调用**真实** [ExpertFollowService]：其全部读写为参数化
 * JDBC，测试配置提供真实 MySQL DataSource（test application.yml 数据源，
 * 127.0.0.1:3306/talent_introduction）+ Flyway（迁移到最新含 V121），绝不 mock 成功
 * 返回。GET summary/timeline 也走真实 SQL 仓库 + 真实服务（仅外围 Spring Data 仓库与
 * 附件解析 mock，分组/聚合/游标均在真实 MySQL 上执行）：
 * - I-3：并发 PUT 仅 1 行 / DELETE 重复成功；身份只取 Session AUTH_USERNAME，body 携带
 *   username 被忽略且不能改变 owner；匿名 401（真实 AuthInterceptor）；会话用户不存在 401。
 * - I-1/I-2/I-4：summary 不携带正文、latestInbound 为真实 processing.id；timeline 只返回
 *   当前专家、正序最新窗口 + (time,source,id) 游标翻页；游标与 contact/account 绑定
 *   （跨专家/改账号/乱码 400）。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@WebMvcTest(controllers = [MailboxConversationController::class])
@Import(
    AuthWebConfig::class,
    ObjectMapper::class,
    MailboxConversationRealJdbcConfig::class,
    MailboxConversationRepository::class,
    MailboxConversationService::class,
    ExpertFollowService::class
)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class MailboxConversationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var expertFollowService: ExpertFollowService

    @MockBean
    private lateinit var authService: AuthService

    @MockBean
    private lateinit var expertContactRepository: ExpertContactRepository

    @MockBean
    private lateinit var senderAccountRepository: MailSenderAccountRepository

    @MockBean
    private lateinit var expertMaterialService: ExpertMaterialService

    @BeforeEach
    fun setUp() {
        cleanup()
        // 关注/会话数据真实落库（expert_follow 复合主键、expert_contact FK、mail_record/
        // processing 聚合全部走真实 MySQL）；外围仓库 mock 与真实种子保持一致。
        seedAccount("acc-a")
        seedContactRow(1, "Alice Expert", "alice@example.org")
        seedContactRow(2, "Bob Expert", "bob@example.org")
        seedContactRow(3, "Carol Expert", "carol@example.org")
        Mockito.`when`(authService.findUser("op1")).thenReturn(adminUser("op1"))
        Mockito.`when`(authService.findUser("op2")).thenReturn(adminUser("op2"))
        Mockito.`when`(
            senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        ).thenReturn(listOf(activeAccount("acc-a"), activeAccount("acc-b")))
        listOf(1L, 2L, 3L).forEach { id ->
            Mockito.`when`(expertContactRepository.findById(id))
                .thenReturn(Optional.of(contact(id)))
        }
        Mockito.`when`(expertMaterialService.resolveMessageAttachments(Mockito.anyString(), Mockito.anyLong()))
            .thenReturn(emptyList())
    }

    @AfterEach
    fun tearDown() {
        cleanup()
    }

    // ------------------------------------------------------------------
    // I-3：关注幂等与 Session 身份（真实 ExpertFollowService + 真实 MySQL JDBC）
    // ------------------------------------------------------------------

    @Test
    fun `PUT follow ten times keeps one row and DELETE twice is idempotent`() {
        repeat(10) {
            mockMvc.perform(
                put("/api/mail/mailbox/conversations/1/follow")
                    .session(sessionOf("op1"))
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.followed").value(true))
        }
        assertEquals(1L, followRows("op1", 1L), "重复 PUT 只产生一行（INSERT IGNORE）")
        val createdAt = jdbcTemplate.queryForObject(
            "SELECT created_at FROM expert_follow WHERE username = 'op1' AND expert_contact_id = 1",
            Timestamp::class.java
        )!!

        // 真实服务并发路径：8 线程同时 PUT，仍只有一行，created_at 不刷新。
        val pool = Executors.newFixedThreadPool(8)
        val latch = CountDownLatch(1)
        val errors = ConcurrentLinkedQueue<Throwable>()
        try {
            repeat(8) {
                pool.submit {
                    latch.await()
                    runCatching { expertFollowService.setFollowed("op1", 1L, true) }
                        .onFailure { errors.add(it) }
                }
            }
            latch.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发 PUT 必须全部完成")
        } finally {
            pool.shutdownNow()
        }
        assertTrue(errors.isEmpty(), "并发 PUT 不得失败: $errors")
        assertEquals(1L, followRows("op1", 1L))
        assertEquals(createdAt, jdbcTemplate.queryForObject(
            "SELECT created_at FROM expert_follow WHERE username = 'op1' AND expert_contact_id = 1",
            Timestamp::class.java
        ), "幂等重复 PUT 不刷新首次 created_at")

        repeat(2) {
            mockMvc.perform(
                delete("/api/mail/mailbox/conversations/1/follow")
                    .session(sessionOf("op1"))
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.followed").value(false))
        }
        assertEquals(0L, followRows("op1", 1L), "重复 DELETE 幂等成功")
    }

    @Test
    fun `username in request body is ignored and owner stays the session user`() {
        mockMvc.perform(
            put("/api/mail/mailbox/conversations/1/follow")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"attacker","followed":false}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.followed").value(true))

        assertEquals(1L, followRows("op1", 1L), "关注写进 session 用户的 owner")
        assertEquals(0L, followRows("attacker", 1L), "body 里的 username 绝不能改变 owner")
        assertEquals(0L, followRows("op2", 1L))

        mockMvc.perform(
            delete("/api/mail/mailbox/conversations/1/follow")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"attacker"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.followed").value(false))
        assertEquals(0L, followRows("op1", 1L))
    }

    @Test
    fun `anonymous and invalid-session follow are rejected with 401`() {
        mockMvc.perform(put("/api/mail/mailbox/conversations/1/follow"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        mockMvc.perform(delete("/api/mail/mailbox/conversations/1/follow"))
            .andExpect(status().isUnauthorized)

        // 会话用户名已不存在（AuthInterceptor findUser 为 null）→ 401，不写任何 owner
        mockMvc.perform(
            put("/api/mail/mailbox/conversations/1/follow").session(sessionOf("ghost-user"))
        ).andExpect(status().isUnauthorized)
        assertEquals(0L, followRows("ghost-user", 1L))
    }

    @Test
    fun `follow ownership is per session user and list followed filter respects it`() {
        insertOutbound(1, "SENT", "2026-09-01 09:00:00")
        insertOutbound(2, "SENT", "2026-09-02 09:00:00")

        expertFollowService.setFollowed("op1", 1L, true)
        expertFollowService.setFollowed("op2", 2L, true)

        val body = objectMapper.readTree(
            mockMvc.perform(
                get("/api/mail/mailbox/conversations").session(sessionOf("op1"))
            ).andExpect(status().isOk).andReturn().response.contentAsString
        )
        assertEquals(2, body["total"].asInt())
        val items = body["items"]
        val first = items.first { it["contactId"].asLong() == 1L }
        val second = items.first { it["contactId"].asLong() == 2L }
        assertTrue(first["followed"].asBoolean())
        assertFalse(second["followed"].asBoolean(), "op2 的关注不属于 op1")

        val followedBody = objectMapper.readTree(
            mockMvc.perform(
                get("/api/mail/mailbox/conversations?followed=true").session(sessionOf("op1"))
            ).andExpect(status().isOk).andReturn().response.contentAsString
        )
        assertEquals(1, followedBody["total"].asInt())
        assertEquals(1L, followedBody["items"][0]["contactId"].asLong())
    }

    // ------------------------------------------------------------------
    // summary / timeline 端到端（真实 SQL 仓库 + 真实 MySQL；I-1/I-2/I-4）
    // ------------------------------------------------------------------

    @Test
    fun `conversations summary never carries body and latestInbound is the real processing id`() {
        insertOutbound(1, "SENT", "2026-09-01 09:00:00")
        insertProcessing(1, "PROCESSED", "2026-09-03 09:00:00", "in-msg-1")
        insertOutbound(2, "FAILED", "2026-09-01 09:00:00")

        val responseBody = mockMvc.perform(
            get("/api/mail/mailbox/conversations").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn().response.contentAsString

        val tree = objectMapper.readTree(responseBody)
        assertEquals(2, tree["total"].asInt())
        val itemKeys = tree["items"][0].fieldNames().asSequence().toList()
        assertFalse(itemKeys.contains("body"), "summary item 不得有 body 键")
        assertFalse(itemKeys.contains("cleanedBody"), "summary item 不得有 cleanedBody 键")
        val latestMessageKeys = tree["items"][0]["latestMessage"].fieldNames().asSequence().toList()
        assertEquals(
            listOf("source", "id", "direction", "subject", "preview", "time", "sendStatus"),
            latestMessageKeys,
            "latestMessage 只含投影字段（preview 而非正文）"
        )
        val contact1 = tree["items"].first { it["contactId"].asLong() == 1L }
        assertEquals("alice@example.org", contact1["email"].asText())
        assertEquals(1, contact1["receivedCount"].asInt())
        assertEquals(1, contact1["sentCount"].asInt())
        assertFalse(contact1["waitingReply"].asBoolean())
        assertEquals(processingIdOf("in-msg-1"),
            contact1["latestInbound"]["processingId"].asLong(),
            "latestInbound 必须是真实 processing.id")
        assertEquals("in-msg-1", contact1["latestInbound"]["messageId"].asText())
        assertEquals("INBOUND_PROCESSING", contact1["latestMessage"]["source"].asText())

        val contact2 = tree["items"].first { it["contactId"].asLong() == 2L }
        assertEquals(1, contact2["failedCount"].asInt())
        assertFalse(contact2["waitingReply"].asBoolean(), "仅 FAILED → 不等待专家回复")
        assertTrue(contact2["latestInbound"].isNull)
    }

    @Test
    fun `waiting and pending toggles return the exact experts`() {
        insertOutbound(1, "SENT", "2026-09-01 09:00:00")
        insertOutbound(2, "FAILED", "2026-09-01 09:00:00")
        seedContactRow(4, "Dan Expert", "dan@example.org")
        Mockito.`when`(expertContactRepository.findById(4L))
            .thenReturn(Optional.of(contact(4)))
        insertProcessing(3, "MANUAL_REVIEW", "2026-09-02 09:00:00", "in-d")
        insertProcessing(4, "PROCESSED", "2026-08-01 09:00:00", "in-old")

        val waiting = objectMapper.readTree(
            mockMvc.perform(
                get("/api/mail/mailbox/conversations?waitingReply=true").session(sessionOf("op1"))
            ).andExpect(status().isOk).andReturn().response.contentAsString
        )
        assertEquals(1, waiting["total"].asInt())
        assertEquals(1L, waiting["items"][0]["contactId"].asLong())

        val pending = objectMapper.readTree(
            mockMvc.perform(
                get("/api/mail/mailbox/conversations?pendingOnly=true").session(sessionOf("op1"))
            ).andExpect(status().isOk).andReturn().response.contentAsString
        )
        assertEquals(1, pending["total"].asInt())
        assertEquals(3L, pending["items"][0]["contactId"].asLong())
    }

    @Test
    fun `timeline returns only the current expert ascending newest window with bound cursor`() {
        insertOutbound(1, "SENT", "2026-09-01 09:00:00", "t1")
        insertProcessing(1, "PROCESSED", "2026-09-02 09:00:00", "m2", "t2")
        insertOutbound(1, "SENT", "2026-09-03 09:00:00", "t3")
        insertOutbound(2, "SENT", "2026-09-01 08:00:00", "other")

        val page1Body = mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages?limit=2").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val page1 = objectMapper.readTree(page1Body)
        assertTrue(page1["hasMore"].asBoolean())
        val page1Items = page1["items"]
        assertEquals(2, page1Items.size())
        assertEquals("t2", page1Items[0]["subject"].asText(), "正序最新窗口：先 t2")
        assertEquals("t3", page1Items[1]["subject"].asText())
        assertTrue(page1Items.all { it["contactId"].asLong() == 1L }, "timeline 只含当前专家")
        assertEquals("INBOUND_PROCESSING", page1Items[0]["source"].asText())
        assertEquals("PROCESSED", page1Items[0]["processStatus"].asText())
        assertFalse(page1Items[0]["body"].isNull, "timeline 消息 DTO 才携带 body")
        assertNotNull(page1["nextBefore"].asText())

        val page2Body = mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages?limit=2&before=${page1["nextBefore"].asText()}")
                .session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val page2 = objectMapper.readTree(page2Body)
        assertFalse(page2["hasMore"].asBoolean())
        assertEquals(1, page2["items"].size())
        assertEquals("t1", page2["items"][0]["subject"].asText())
        assertTrue(page2["nextBefore"].isNull)
    }

    @Test
    fun `timeline cursor is bound to contact and account scope and rejects tampering`() {
        insertOutbound(1, "SENT", "2026-09-01 09:00:00", "a1")
        // contact2：acc-a 两条（可翻页）+ acc-b 一条（换账号 scope 后仍有数据可查）
        insertOutbound(2, "SENT", "2026-09-01 09:00:00", "b1")
        insertOutbound(2, "SENT", "2026-09-02 09:00:00", "b2")
        insertOutbound(2, "SENT", "2026-09-03 08:00:00", "b3", "acc-b")

        val pageBody = mockMvc.perform(
            get("/api/mail/mailbox/conversations/2/messages?limit=1").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val cursor = objectMapper.readTree(pageBody)["nextBefore"].asText()
        assertNotNull(cursor)

        // 跨专家复用游标 → 400（scope 绑定 contact）
        mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages?limit=1&before=$cursor")
                .session(sessionOf("op1"))
        ).andExpect(status().isBadRequest)

        // 账号范围不一致 → 400：acc-a scope 取游标，换 acc-b scope 复用被拒绝
        val accCursorBody = mockMvc.perform(
            get("/api/mail/mailbox/conversations/2/messages?limit=1&accountCode=acc-a")
                .session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val accCursor = objectMapper.readTree(accCursorBody)["nextBefore"].asText()
        assertNotNull(accCursor)
        mockMvc.perform(
            get("/api/mail/mailbox/conversations/2/messages?limit=1&before=$accCursor&accountCode=acc-b")
                .session(sessionOf("op1"))
        ).andExpect(status().isBadRequest)

        // 乱码游标 → 400
        mockMvc.perform(
            get("/api/mail/mailbox/conversations/2/messages?limit=1&before=not-a-cursor!")
                .session(sessionOf("op1"))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `message attachments surface count and first names through the exact resolver`() {
        insertProcessing(1, "MANUAL_REVIEW", "2026-09-02 09:00:00", "in-msg-att")
        // 06 精确来源解析（mock 边界）：该 processing 的附件元数据
        Mockito.`when`(expertMaterialService.resolveMessageAttachments("INBOUND_PROCESSING", processingIdOf("in-msg-att")))
            .thenReturn(listOf(
                com.weibo.talentintroduction.mail.domain.MailAttachment(
                    id = 11L, mailRecordId = null, inboundProcessingId = processingIdOf("in-msg-att"),
                    fileName = "cv.pdf", contentType = "application/pdf", fileSize = 10L, storagePath = null
                ),
                com.weibo.talentintroduction.mail.domain.MailAttachment(
                    id = 12L, mailRecordId = null, inboundProcessingId = processingIdOf("in-msg-att"),
                    fileName = "diploma.pdf", contentType = "application/pdf", fileSize = 20L, storagePath = null
                )
            ))
        val body = mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val tree = objectMapper.readTree(body)
        val inboundItem = tree["items"].first { it["source"].asText() == "INBOUND_PROCESSING" }
        assertEquals(2, inboundItem["attachmentCount"].asInt())
        assertEquals("cv.pdf", inboundItem["firstAttachmentNames"][0].asText())
        assertEquals("diploma.pdf", inboundItem["firstAttachmentNames"][1].asText())
    }

    @Test
    fun `query parameters are whitelisted and validated`() {
        insertOutbound(1, "SENT", "2026-09-01 09:00:00")

        mockMvc.perform(
            get("/api/mail/mailbox/conversations?direction=BOGUS").session(sessionOf("op1"))
        ).andExpect(status().isBadRequest)
        mockMvc.perform(
            get("/api/mail/mailbox/conversations?startDate=not-a-date").session(sessionOf("op1"))
        ).andExpect(status().isBadRequest)
        mockMvc.perform(
            get("/api/mail/mailbox/conversations?q=${"x".repeat(300)}").session(sessionOf("op1"))
        ).andExpect(status().isBadRequest)
        mockMvc.perform(
            get("/api/mail/mailbox/conversations/999999/messages").session(sessionOf("op1"))
        ).andExpect(status().isNotFound)
        mockMvc.perform(
            put("/api/mail/mailbox/conversations/999999/follow").session(sessionOf("op1"))
        ).andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private fun sessionOf(username: String): MockHttpSession =
        MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }

    private fun adminUser(username: String): AdminUser =
        AdminUser(
            username = username,
            passwordHash = "not-checked",
            mustChangePassword = false,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

    private fun activeAccount(code: String = "acc-a"): MailSenderAccount =
        MailSenderAccount(
            accountCode = code,
            senderEmail = "$code@fixture.local",
            senderName = code,
            senderTitle = null,
            senderDisplayName = null,
            teamName = null,
            countryName = null,
            smtpHost = "smtp.fixture", smtpPort = 465, smtpUsername = code, smtpPassword = "pw",
            imapHost = "imap.fixture", imapPort = 993, imapUsername = code, imapPassword = "pw"
        )

    private fun contact(id: Long): ExpertContact =
        ExpertContact(
            id = id,
            campaignId = id,
            orcidId = "0000-0000-0000-%04d".format(id),
            expertEmail = "expert$id@example.org",
            expertName = "Expert $id"
        )

    private fun followRows(username: String, contactId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_follow WHERE username = ? AND expert_contact_id = ?",
            Long::class.java, username, contactId
        )!!

    private fun cleanup() {
        jdbcTemplate.update("DELETE FROM expert_follow")
        jdbcTemplate.update("DELETE FROM mail_attachment")
        jdbcTemplate.update("DELETE FROM inbound_mail_tag")
        jdbcTemplate.update("DELETE FROM inbound_mail_processing")
        jdbcTemplate.update("DELETE FROM mail_record")
        jdbcTemplate.update("DELETE FROM expert_contact")
        jdbcTemplate.update("DELETE FROM campaign")
        jdbcTemplate.update("DELETE FROM mail_sender_account")
    }

    private fun seedAccount(code: String) {
        if (jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mail_sender_account WHERE account_code = ?", Long::class.java, code
            )!! == 0L
        ) {
            jdbcTemplate.update(
                """
                INSERT INTO mail_sender_account
                    (account_code, sender_email, sender_name, smtp_host, smtp_port,
                     smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
                VALUES (?, ?, ?, 'smtp.fixture', 465, ?, ?, 'imap.fixture', 993, ?, ?)
                """.trimIndent(),
                code, "$code@fixture.local", code, code, "pw", code, "pw"
            )
        }
    }

    private fun seedContactRow(id: Long, name: String, email: String) {
        if (jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM campaign WHERE id = ?", Long::class.java, id
            )!! == 0L
        ) {
            jdbcTemplate.update(
                "INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) " +
                    "VALUES (?, ?, 'Fixture', (SELECT id FROM mail_sender_account WHERE account_code = 'acc-a'))",
                id, "FIXTURE-$id"
            )
        }
        if (jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expert_contact WHERE id = ?", Long::class.java, id
            )!! == 0L
        ) {
            jdbcTemplate.update(
                """
                INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name, current_status)
                VALUES (?, ?, ?, ?, ?, 'NEW')
                """.trimIndent(),
                id, id, "0000-0000-0000-%04d".format(id), email, name
            )
        }
    }

    private fun insertOutbound(
        contactId: Long,
        sendStatus: String,
        eventAt: String,
        subject: String = "subject-$contactId",
        accountCode: String = "acc-a"
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, triggered_by,
                 message_id, subject, body, send_status, sent_at, created_at)
            VALUES (?, 'OUTBOUND', 'INTRODUCTION', ?, 'SYSTEM', ?, ?, 'body', ?, ?, ?)
            """.trimIndent(),
            contactId, accountCode, "out-$contactId-${subject.hashCode()}", subject, sendStatus,
            if (sendStatus == "SENT") Timestamp.valueOf(ts(eventAt)) else null,
            Timestamp.valueOf(ts(eventAt))
        )
    }

    private fun insertProcessing(
        contactId: Long,
        processStatus: String,
        eventAt: String,
        messageId: String,
        subject: String = "in-$contactId"
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO inbound_mail_processing
                (sender_account_code, uid_validity, imap_uid, message_id, from_email,
                 subject, body, cleaned_body, received_at, process_status, process_reason,
                 expert_contact_id)
            VALUES ('acc-a', 1, ?, ?, 'expert@example.org', ?, 'body', 'cleaned', ?, ?, 'QA_AUTO_REPLIED', ?)
            """.trimIndent(),
            900L + contactId, messageId, subject,
            Timestamp.valueOf(ts(eventAt)), processStatus, contactId
        )
    }

    private fun processingIdOf(messageId: String): Long =
        jdbcTemplate.queryForObject(
            "SELECT id FROM inbound_mail_processing WHERE message_id = ?", Long::class.java, messageId
        )!!

    private fun ts(value: String): LocalDateTime =
        LocalDateTime.parse(value.replace(' ', 'T'))
}

/** 真实 MySQL + Flyway（test application.yml 数据源；迁移到最新含 V121）。 */
@Configuration
class MailboxConversationRealJdbcConfig {

    @Bean
    fun mailboxConversationDataSource(
        @Value("\${spring.datasource.url}") url: String,
        @Value("\${spring.datasource.username:root}") username: String,
        @Value("\${spring.datasource.password:root}") password: String
    ): DataSource = DriverManagerDataSource(url, username, password)

    @Bean(initMethod = "migrate")
    fun mailboxConversationFlyway(dataSource: DataSource): Flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .placeholderReplacement(false)
            .load()

    @Bean
    fun mailboxConversationJdbcTemplate(dataSource: DataSource): JdbcTemplate = JdbcTemplate(dataSource)

    @Bean
    fun mailboxConversationNamedJdbc(dataSource: DataSource): NamedParameterJdbcTemplate =
        NamedParameterJdbcTemplate(dataSource)
}
