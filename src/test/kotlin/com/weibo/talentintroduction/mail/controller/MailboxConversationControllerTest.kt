package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import com.weibo.talentintroduction.mail.service.ExpertFollowService
import com.weibo.talentintroduction.mail.service.InboundMailTagService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailboxConversationService
import com.weibo.talentintroduction.mail.service.TagView
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.mockito.Mockito.anyCollection
import org.mockito.Mockito.anyList
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
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
import java.nio.charset.StandardCharsets
import java.sql.Statement
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.test.web.servlet.MvcResult

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

    @MockBean
    private lateinit var inboundMailTagService: InboundMailTagService

    @MockBean
    private lateinit var expertSearchService: ExpertSearchService

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
        // I-6 默认投影基座：findAllById 按既有 contact(id) 构造器回页内实体（与 findById
        // 同源、同 orcid 模板，level 默认 CANDIDATE——与 expert_contact 列默认一致）。
        // searchByOrcidIds 未 stub 时默认空结果 → expertTags=null（画像缺失语义）。
        Mockito.`when`(expertContactRepository.findAllById(anyCollection()))
            .thenAnswer { invocation ->
                val ids = (invocation.getArgument(0) as Collection<*>).filterIsInstance<Long>()
                ids.map { contact(it) }
            }
        Mockito.`when`(inboundMailTagService.listTagsBatch(anyCollection()))
            .thenReturn(emptyMap())
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
            get("/api/mail/mailbox/conversations?recipientEmail=${"r".repeat(300)}").session(sessionOf("op1"))
        ).andExpect(status().isBadRequest)
        mockMvc.perform(
            get("/api/mail/mailbox/conversations?keyword=${"k".repeat(300)}").session(sessionOf("op1"))
        ).andExpect(status().isBadRequest)
        mockMvc.perform(
            get("/api/mail/mailbox/conversations?startDate=2026-09-09&endDate=2026-09-01").session(sessionOf("op1"))
        ).andExpect(status().isBadRequest)
        // trim 后为空的 recipientEmail/keyword 按未提供处理（不报错、不缩小结果集）
        val trimmedBody = mockMvc.perform(
            get("/api/mail/mailbox/conversations")
                .param("recipientEmail", "   ")
                .param("keyword", "   ")
                .session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(1, objectMapper.readTree(trimmedBody)["total"].asInt(),
            "空白筛选等价于未提供")
        mockMvc.perform(
            get("/api/mail/mailbox/conversations/999999/messages").session(sessionOf("op1"))
        ).andExpect(status().isNotFound)
        mockMvc.perform(
            put("/api/mail/mailbox/conversations/999999/follow").session(sessionOf("op1"))
        ).andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // T2：timeline 当前窗口邮件标签（I-3）
    // ------------------------------------------------------------------

    @Test
    fun `one timeline window of 20 inbound letters triggers exactly one tag batch with real ids`() {
        val requestedIds = mutableListOf<Collection<Long>>()
        stubTagBatchRecordingInto(requestedIds)
        for (i in 1..20) {
            insertProcessingRow(1, 2000L + i, "PROCESSED", "2026-09-%02d 09:%02d:00".format(i, i),
                "win-$i", "window subject $i")
        }
        val result = mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages?limit=50").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val tree = objectMapper.readTree(utf8Body(result))
        assertEquals(20, tree["items"].size())

        assertEquals(1, requestedIds.size, "一个窗口只调一次 listTagsBatch")
        assertEquals(20, requestedIds[0].size)
        val expected = (1..20).map { processingIdOf("win-$it") }.toSet()
        assertEquals(expected, requestedIds[0].toSet(),
            "批量标签只读取当前窗口的真实 INBOUND_PROCESSING id")
        assertTrue(tree["items"].all { it["tags"].size() == 0 }, "无标签来信返回空数组")
    }

    @Test
    fun `same numeric id on outbound and inbound never cross tags`() {
        insertProcessingRow(1, 3101, "PROCESSED", "2026-09-02 09:00:00", "shared-num-in", "in subject")
        val sharedId = processingIdOf("shared-num-in")
        // 强制 mail_record 与 inbound_mail_processing 数值 id 相同：只靠 source 区分。
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (id, expert_contact_id, direction, mail_type, sender_account_code, triggered_by,
                 message_id, subject, body, send_status, sent_at, created_at)
            VALUES (?, 1, 'OUTBOUND', 'INTRODUCTION', 'acc-a', 'SYSTEM', 'out-shared-num', 'out subject',
                    'body', 'SENT', ?, ?)
            """.trimIndent(),
            sharedId, Timestamp.valueOf(ts("2026-09-01 09:00:00")), Timestamp.valueOf(ts("2026-09-01 09:00:00"))
        )
        Mockito.`when`(inboundMailTagService.listTagsBatch(anyCollection())).thenReturn(
            mapOf(
                sharedId to listOf(
                    TagView(tagId = 77L, tagType = "CUSTOM", qaRuleId = null, label = "会议安排",
                        source = "MANUAL", active = true)
                )
            )
        )

        val result = mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val tree = objectMapper.readTree(utf8Body(result))
        assertEquals(2, tree["items"].size())
        val outbound = tree["items"][0]
        assertEquals("MAIL_RECORD", outbound["source"].asText())
        assertEquals(sharedId, outbound["id"].asLong())
        assertEquals(0, outbound["tags"].size(), "OUTBOUND 与 processing 同数值 id 也不得串标签")
        val inbound = tree["items"][1]
        assertEquals("INBOUND_PROCESSING", inbound["source"].asText())
        assertEquals(sharedId, inbound["id"].asLong())
        assertEquals("会议安排", inbound["tags"][0]["label"].asText())
        assertEquals(77L, inbound["tags"][0]["tagId"].asLong())
    }

    @Test
    fun `outbound-only timeline window never calls tag batch`() {
        insertOutbound(1, "SENT", "2026-09-01 09:00:00", "older")
        insertOutbound(1, "SENT", "2026-09-02 09:00:00", "newer")

        mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages").session(sessionOf("op1"))
        ).andExpect(status().isOk)
        verify(inboundMailTagService, never()).listTagsBatch(anyCollection())
    }

    @Test
    fun `tag added to an inbound letter shows up in the next timeline window and disappears after delete`() {
        insertProcessingRow(1, 3201, "PROCESSED", "2026-09-02 09:00:00", "tag-read-in", "tagged subject")
        val processingId = processingIdOf("tag-read-in")
        val tagId = insertTagRow(processingId, "会议安排")
        stubTagBatchFromDb()

        fun fetchTags(): String {
            val result = mockMvc.perform(
                get("/api/mail/mailbox/conversations/1/messages").session(sessionOf("op1"))
            ).andExpect(status().isOk).andReturn()
            val items = objectMapper.readTree(utf8Body(result))["items"]
            val inbound = items.first { it["source"].asText() == "INBOUND_PROCESSING" }
            return inbound["tags"].toString()
        }

        val firstRead = fetchTags()
        assertTrue(firstRead.contains("\"label\":\"会议安排\""), "落库标签必须在下一次 timeline 返回：$firstRead")
        assertTrue(firstRead.contains("\"tagId\":$tagId"), "返回真实 DB tagId：$firstRead")

        jdbcTemplate.update("DELETE FROM inbound_mail_tag WHERE id = ?", tagId)
        val after = mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val afterInbound = objectMapper.readTree(utf8Body(after))["items"]
            .first { it["source"].asText() == "INBOUND_PROCESSING" }
        assertEquals(0, afterInbound["tags"].size(), "删除标签后 timeline 不再返回")
    }

    // ------------------------------------------------------------------
    // T3：历史 encoded subject 读兼容（I-4）
    // ------------------------------------------------------------------

    @Test
    fun `encoded historical subjects decode in api output and are never rewritten in db`() {
        val rawInboundSubject = "=?UTF-8?Q?Re:_Remote_advisory_collaboration?= =?UTF-8?Q?_request?="
        val rawOutboundSubject = "=?windows-1252?Q?caf=E9?="
        insertProcessingRow(1, 3401, "PROCESSED", "2026-09-02 09:00:00", "enc-subject-in", rawInboundSubject)
        insertOutbound(2, "SENT", "2026-09-01 09:00:00", rawOutboundSubject)

        val result = mockMvc.perform(
            get("/api/mail/mailbox/conversations").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val tree = objectMapper.readTree(utf8Body(result))
        val contact1 = tree["items"].first { it["contactId"].asLong() == 1L }
        assertEquals("Re: Remote advisory collaboration request",
            contact1["latestMessage"]["subject"].asText(), "summary 最近消息 subject 解码可读")
        val contact2 = tree["items"].first { it["contactId"].asLong() == 2L }
        assertEquals("café", contact2["latestMessage"]["subject"].asText())

        // 历史行只读不写：DB 中 encoded subject 原样保留。
        assertEquals(rawInboundSubject,
            jdbcTemplate.queryForObject(
                "SELECT subject FROM inbound_mail_processing WHERE message_id = 'enc-subject-in'",
                String::class.java
            )!!)
        assertEquals(rawOutboundSubject,
            jdbcTemplate.queryForObject(
                "SELECT subject FROM mail_record WHERE expert_contact_id = 2 AND direction = 'OUTBOUND'",
                String::class.java
            )!!)

        val timeline = mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val item = objectMapper.readTree(utf8Body(timeline))["items"]
            .first { it["source"].asText() == "INBOUND_PROCESSING" }
        assertEquals("Re: Remote advisory collaboration request", item["subject"].asText())
    }

    // ------------------------------------------------------------------
    // T4：本页专家标签投影（I-6）
    // ------------------------------------------------------------------

    @Test
    fun `one page of twenty same-level experts triggers exactly one batch search in sql order`() {
        val shared = "2026-09-05 08:00:00"
        for (id in 5L..24L) {
            seedContactRow(id, "Expert $id", "expert$id@example.org")
            insertOutbound(id, "SENT", shared, "s-$id")
        }
        val calls = mutableListOf<Pair<List<String>, ExpertIndexLevel>>()
        Mockito.`when`(expertSearchService.searchByOrcidIds(anyList(), anyLevel())).thenAnswer { invocation ->
            val orcids = invocation.getArgument(0) as List<String>
            val level = invocation.getArgument(1) as ExpertIndexLevel
            calls += orcids to level
            orcids.reversed().map { orcid -> profile(orcid, listOf("tag-" + orcid.takeLast(4))) }
        }

        val result = mockMvc.perform(
            get("/api/mail/mailbox/conversations?size=20").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val tree = objectMapper.readTree(utf8Body(result))
        assertEquals(20, tree["total"].asInt())
        assertEquals(20, tree["items"].size())

        assertEquals(1, calls.size, "一页 20 位同层专家只调 1 次批量查询")
        assertEquals(ExpertIndexLevel.CANDIDATE, calls[0].second)
        assertEquals(20, calls[0].first.size)
        assertEquals((5L..24L).map(::orcidOf).toSet(), calls[0].first.toSet())

        // ES mock 乱序返回仍按 SQL 页序回填；(level, orcid) 键匹配而非位置匹配。
        for ((index, id) in (24L downTo 5L).withIndex()) {
            val item = tree["items"][index]
            assertEquals(id, item["contactId"].asLong(), "SQL 页序必须保持（contactId DESC）")
            assertEquals(orcidOf(id), item["orcid"].asText())
            assertEquals("tag-${orcidOf(id).takeLast(4)}", item["expertTags"][0].asText())
            assertTrue(item["expertTags"].size() == 1)
        }
    }

    @Test
    fun `experts across three levels trigger at most three batch searches with per level orcids`() {
        val shared = "2026-09-05 08:00:00"
        for (id in 5L..7L) {
            seedContactRow(id, "Expert $id", "expert$id@example.org")
            insertOutbound(id, "SENT", shared, "s-$id")
        }
        val levelsById = mapOf(5L to "RAW", 6L to "CANDIDATE", 7L to "APPLICATION")
        Mockito.`when`(expertContactRepository.findAllById(anyCollection())).thenAnswer { invocation ->
            val ids = (invocation.getArgument(0) as Collection<*>).filterIsInstance<Long>()
            ids.map { id -> contact(id).copy(currentIndexLevel = levelsById[id] ?: "CANDIDATE") }
        }
        Mockito.`when`(expertSearchService.searchByOrcidIds(anyList(), anyLevel())).thenAnswer { invocation ->
            val orcids = invocation.getArgument(0) as List<String>
            val level = invocation.getArgument(1) as ExpertIndexLevel
            orcids.map { orcid -> profile(orcid, listOf(level.name + "-tag")) }
        }

        val result = mockMvc.perform(
            get("/api/mail/mailbox/conversations").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val tree = objectMapper.readTree(utf8Body(result))
        assertEquals(3, tree["total"].asInt())

        verify(expertSearchService, times(1))
            .searchByOrcidIds(listOf(orcidOf(5L)), ExpertIndexLevel.RAW)
        verify(expertSearchService, times(1))
            .searchByOrcidIds(listOf(orcidOf(6L)), ExpertIndexLevel.CANDIDATE)
        verify(expertSearchService, times(1))
            .searchByOrcidIds(listOf(orcidOf(7L)), ExpertIndexLevel.APPLICATION)
        verifyNoMoreInteractions(expertSearchService)

        val byId = tree["items"].associate { it["contactId"].asLong() to it }
        assertEquals(listOf("RAW-tag"), byId[5L]!!["expertTags"].map { it.asText() })
        assertEquals(listOf("CANDIDATE-tag"), byId[6L]!!["expertTags"].map { it.asText() })
        assertEquals(listOf("APPLICATION-tag"), byId[7L]!!["expertTags"].map { it.asText() })
    }

    @Test
    fun `missing profile yields null expert tags while present profiles keep their values`() {
        val shared = "2026-09-05 08:00:00"
        for (id in 5L..7L) {
            seedContactRow(id, "Expert $id", "expert$id@example.org")
            insertOutbound(id, "SENT", shared, "s-$id")
        }
        val levelsById = mapOf(5L to "RAW", 6L to "CANDIDATE", 7L to "APPLICATION")
        Mockito.`when`(expertContactRepository.findAllById(anyCollection())).thenAnswer { invocation ->
            val ids = (invocation.getArgument(0) as Collection<*>).filterIsInstance<Long>()
            ids.map { id -> contact(id).copy(currentIndexLevel = levelsById[id] ?: "CANDIDATE") }
        }
        // 只返回 5、7 的画像；6 的画像缺失 → 6 必须 null（绝不伪称 []）。
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf(orcidOf(5L)), ExpertIndexLevel.RAW))
            .thenReturn(listOf(profile(orcidOf(5L), listOf(orcidOf(5L).takeLast(4)))))
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf(orcidOf(6L)), ExpertIndexLevel.CANDIDATE))
            .thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf(orcidOf(7L)), ExpertIndexLevel.APPLICATION))
            .thenReturn(listOf(profile(orcidOf(7L), listOf(orcidOf(7L).takeLast(4)))))

        val result = mockMvc.perform(
            get("/api/mail/mailbox/conversations").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val byId = objectMapper.readTree(utf8Body(result))["items"].associate { it["contactId"].asLong() to it }
        assertEquals("0005", byId[5L]!!["expertTags"][0].asText())
        assertTrue(byId[6L]!!["expertTags"].isNull, "画像缺失必须是 null 而非 []")
        assertEquals("0007", byId[7L]!!["expertTags"][0].asText())
    }

    @Test
    fun `per level expert search failure nulls only that group and keeps the page at 200`() {
        val shared = "2026-09-05 08:00:00"
        for (id in 5L..7L) {
            seedContactRow(id, "Expert $id", "expert$id@example.org")
            insertOutbound(id, "SENT", shared, "s-$id")
        }
        val levelsById = mapOf(5L to "RAW", 6L to "CANDIDATE", 7L to "APPLICATION")
        Mockito.`when`(expertContactRepository.findAllById(anyCollection())).thenAnswer { invocation ->
            val ids = (invocation.getArgument(0) as Collection<*>).filterIsInstance<Long>()
            ids.map { id -> contact(id).copy(currentIndexLevel = levelsById[id] ?: "CANDIDATE") }
        }
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf(orcidOf(5L)), ExpertIndexLevel.RAW))
            .thenReturn(listOf(profile(orcidOf(5L), listOf("RAW-ok"))))
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf(orcidOf(6L)), ExpertIndexLevel.CANDIDATE))
            .thenThrow(IllegalStateException("simulated es outage"))
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf(orcidOf(7L)), ExpertIndexLevel.APPLICATION))
            .thenReturn(listOf(profile(orcidOf(7L), listOf("APPLICATION-ok"))))

        val result = mockMvc.perform(
            get("/api/mail/mailbox/conversations").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val tree = objectMapper.readTree(utf8Body(result))
        assertEquals(3, tree["total"].asInt(), "某层 ES 失败不得让整页 500 或改变计数")
        val byId = tree["items"].associate { it["contactId"].asLong() to it }
        assertEquals(listOf("RAW-ok"), byId[5L]!!["expertTags"].map { it.asText() })
        assertTrue(byId[6L]!!["expertTags"].isNull, "失败层该组降级为 null")
        assertEquals(listOf("APPLICATION-ok"), byId[7L]!!["expertTags"].map { it.asText() })
        verify(expertSearchService, times(3)).searchByOrcidIds(anyList(), anyLevel())
    }

    @Test
    fun `no batch expert searches when page empty or rows lack a valid level orcid`() {
        // 全空页：无任何邮件 → 不读 contact、不查 ES。
        mockMvc.perform(
            get("/api/mail/mailbox/conversations").session(sessionOf("op1"))
        ).andExpect(status().isOk)
        verify(expertContactRepository, never()).findAllById(anyCollection())
        verifyNoInteractions(expertSearchService)

        // 有行但 contact 无 ORCID + 非法层级：不查 ES，expertTags=null，页面 200。
        insertOutbound(1, "SENT", "2026-09-01 09:00:00")
        Mockito.`when`(expertContactRepository.findAllById(anyCollection())).thenAnswer { invocation ->
            val ids = (invocation.getArgument(0) as Collection<*>).filterIsInstance<Long>()
            ids.map { id -> contact(id).copy(orcidId = "", currentIndexLevel = "LEGACY_LEVEL") }
        }
        val result = mockMvc.perform(
            get("/api/mail/mailbox/conversations").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val item = objectMapper.readTree(utf8Body(result))["items"][0]
        assertTrue(item["expertTags"].isNull)
        verifyNoInteractions(expertSearchService)
    }

    @Test
    fun `expert tags and mail tags stay fully separated and tags trim dedupe preserving es order`() {
        insertProcessingRow(1, 3501, "MANUAL_REVIEW", "2026-09-02 09:00:00", "iso-mail-in", "isolation")
        insertTagRow(processingIdOf("iso-mail-in"), "会议安排")
        Mockito.`when`(expertSearchService.searchByOrcidIds(anyList(), anyLevel())).thenAnswer { invocation ->
            val orcids = invocation.getArgument(0) as List<String>
            orcids.map { orcid ->
                profile(orcid, listOf("  学术科研 ", "学术科研", "重点关注"))
            }
        }
        stubTagBatchFromDb()

        val summary = mockMvc.perform(
            get("/api/mail/mailbox/conversations").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val item = objectMapper.readTree(utf8Body(summary))["items"][0]
        assertEquals(listOf("学术科研", "重点关注"),
            item["expertTags"].map { it.asText() }, "trim/去空/去重且保持 ES 顺序")
        val itemKeys = item.fieldNames().asSequence().toList()
        assertFalse(itemKeys.contains("tags"), "邮件标签不得泄漏到 summary")
        assertTrue(itemKeys.contains("expertTags"))
        verify(expertSearchService, times(1)).searchByOrcidIds(anyList(), anyLevel())

        val timeline = mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val timelineItems = objectMapper.readTree(utf8Body(timeline))["items"]
        val inbound = timelineItems.first { it["source"].asText() == "INBOUND_PROCESSING" }
        assertEquals("会议安排", inbound["tags"][0]["label"].asText(), "邮件标签只属于 timeline 消息")
        assertTrue(timelineItems.none { it["source"].asText() == "MAIL_RECORD" && it["tags"].size() > 0 },
            "OUTBOUND 消息绝不携带邮件标签")
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

    private fun insertProcessingRow(
        contactId: Long,
        imapUid: Long,
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
            imapUid, messageId, subject,
            Timestamp.valueOf(ts(eventAt)), processStatus, contactId
        )
    }

    /** 真实落库 CUSTOM 标签行（写路径由既有 InboundMailSummaryController/服务覆盖）。 */
    private fun insertTagRow(processingId: Long, label: String): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update(
            { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO inbound_mail_tag (inbound_processing_id, tag_type, label, source, created_at)
                    VALUES (?, 'CUSTOM', ?, 'MANUAL', ?)
                    """.trimIndent(),
                    Statement.RETURN_GENERATED_KEYS
                ).apply {
                    setLong(1, processingId)
                    setString(2, label)
                    setTimestamp(3, Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 10, 0)))
                }
            },
            keyHolder
        )
        return keyHolder.key!!.toLong()
    }

    /** listTagsBatch 从真实 DB 读 CUSTOM 标签行（含真实 DB tagId），模拟既有服务的批量读。 */
    private fun stubTagBatchFromDb() {
        Mockito.`when`(inboundMailTagService.listTagsBatch(anyCollection())).thenAnswer { invocation ->
            val ids = (invocation.getArgument(0) as Collection<*>).filterIsInstance<Long>().toList()
            if (ids.isEmpty()) {
                emptyMap<Long, List<TagView>>()
            } else {
                val placeholders = ids.joinToString(",") { "?" }
                val pairs = jdbcTemplate.query(
                    """
                    SELECT id, inbound_processing_id, tag_type, qa_rule_id, label, source
                      FROM inbound_mail_tag
                     WHERE inbound_processing_id IN ($placeholders)
                    """.trimIndent(),
                    { rs, _ ->
                        rs.getLong("inbound_processing_id") to TagView(
                            tagId = rs.getLong("id"),
                            tagType = rs.getString("tag_type"),
                            qaRuleId = rs.getLong("qa_rule_id").takeIf { !rs.wasNull() },
                            label = rs.getString("label"),
                            source = rs.getString("source"),
                            active = true
                        )
                    },
                    *ids.toTypedArray()
                )
                pairs.groupBy({ it.first }, { it.second })
            }
        }
    }

    private fun profile(orcid: String, tags: List<String>?): ExpertProfile =
        ExpertProfile(
            orcidId = orcid,
            email = null,
            givenNames = null,
            familyNames = null,
            country = null,
            keyword = null,
            employment = null,
            tags = tags
        )

    private fun orcidOf(id: Long): String = "0000-0000-0000-%04d".format(id)

    /** mockMvc 响应按 UTF-8 读取（MockHttpServletResponse 默认 ISO-8859-1 会把中文/é 变乱码）。 */
    private fun utf8Body(result: MvcResult): String =
        String(result.response.contentAsByteArray, StandardCharsets.UTF_8)

    /**
     * Mockito 对 Kotlin 非空参数不能直接返回 null 的匹配器（any()/capture() 会触发
     * 非空校验 NPE）；anyLevel() 注册 any(Class) 匹配器后返回非空哨兵 RAW。
     */
    private fun anyLevel(): ExpertIndexLevel {
        Mockito.any(ExpertIndexLevel::class.java)
        return ExpertIndexLevel.RAW
    }

    /** listTagsBatch 桩：记录每次调用收到的 id 集合，默认返回空标签。 */
    private fun stubTagBatchRecordingInto(seen: MutableList<Collection<Long>>) {
        Mockito.`when`(inboundMailTagService.listTagsBatch(anyCollection())).thenAnswer { invocation ->
            seen += invocation.getArgument(0) as Collection<Long>
            emptyMap<Long, List<TagView>>()
        }
    }

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

// ---------------------------------------------------------------------------
// A1 双控制器映射回归（Amendment A1, 2026-09-08 HUMAN 批准）：同挂 campaign 旧 feed
// controller 与 06 新材料 controller，证明不再 Ambiguous mapping（修复前本类 context
// 启动失败：GET /api/expert-contacts/{contactId}/materials 双重映射）。
// 本类不需要数据库/登录（全部构造依赖 @MockBean），因此不挂 mysqlIt 门禁，普通
// mvn test 全量即回归；若未来有人恢复 campaign 侧同模板 GET 映射会立即红。
// ---------------------------------------------------------------------------
@WebMvcTest(
    controllers = [
        com.weibo.talentintroduction.document.controller.ExpertMaterialController::class,
        com.weibo.talentintroduction.campaign.controller.ExpertContactManagementController::class
    ]
)
class MailboxMaterialsDualControllerMappingTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var documentMaterialService: com.weibo.talentintroduction.document.service.ExpertMaterialService

    @MockBean
    private lateinit var campaignMaterialService: com.weibo.talentintroduction.campaign.service.ExpertMaterialService

    @MockBean
    private lateinit var contactManagementService: com.weibo.talentintroduction.campaign.service.ExpertContactManagementService

    @MockBean
    private lateinit var manualExpertMailService: com.weibo.talentintroduction.mail.service.ManualExpertMailService

    @MockBean
    private lateinit var meetingScheduleService: com.weibo.talentintroduction.campaign.service.MeetingScheduleService

    @MockBean
    private lateinit var operatorStatusService: com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService

    @MockBean
    private lateinit var indexLevelOperationService: com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService

    @MockBean
    private lateinit var senderAccountBindingService: com.weibo.talentintroduction.mail.service.SenderAccountBindingService

    @Test
    fun `both materials controllers coexist and the surviving GET feed is the shared material api`() {
        // 修复前：context 加载即抛 Ambiguous mapping（两个 controller 映射同模板）。
        // 修复后：GET materials 唯一由 06 新材料 controller 提供（campaign 旧 feed 已退役）。
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/expert-contacts/1/materials"
            )
        ).andExpect(status().isOk)

        // 保留端点的占位验证（PUT updateMaterialStatus 仍由 campaign controller 提供）。
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/expert-contacts/1/materials/CV"
            ).contentType(MediaType.APPLICATION_JSON).content("""{"status":"PROVIDED"}""")
        ).andExpect(status().isOk)
    }
}
