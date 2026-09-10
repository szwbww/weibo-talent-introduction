package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
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
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import com.weibo.talentintroduction.mail.service.CalendarAttachmentCodec
import com.weibo.talentintroduction.mail.service.CalendarAttachmentSnapshot
import com.weibo.talentintroduction.mail.service.ExpertFollowService
import com.weibo.talentintroduction.mail.service.InboundMailTagService
import com.weibo.talentintroduction.mail.service.MailContentService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailboxConversationService
import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.mail.service.PendingMailSendResult
import com.weibo.talentintroduction.mail.service.MeetingConfirmationService
import com.weibo.talentintroduction.mail.service.MeetingConfirmationDomain
import com.weibo.talentintroduction.mail.service.MeetingInput
import com.weibo.talentintroduction.mail.service.MeetingPreviewResponse
import com.weibo.talentintroduction.mail.service.TagView
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
@WebMvcTest(controllers = [MailboxConversationController::class, CalendarAttachmentController::class])
@Import(
    AuthWebConfig::class,
    MailboxConversationControllerTest.KotlinObjectMapperConfig::class,
    MailboxConversationRealJdbcConfig::class,
    MailboxConversationRepository::class,
    MailboxConversationService::class,
    ExpertFollowService::class
)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class MailboxConversationControllerTest {

    /**
     * 本 slice 需要解析 Kotlin data class @RequestBody（ConversationManualRichReplyRequest）。
     * 生产由 Spring Boot 自动注册 jackson-module-kotlin；@WebMvcTest 不会，故测试侧显式
     * 提供带 KotlinModule 的 primary ObjectMapper（替代默认构造的裸 ObjectMapper bean）。
     */
    @TestConfiguration
    class KotlinObjectMapperConfig {
        @Bean
        fun objectMapper(): ObjectMapper =
            ObjectMapper()
                .registerModule(KotlinModule())
                // 与 Spring Boot 自动配置一致：@RequestBody 忽略未知字段（extra 键不 400）
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

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

    // 03 (T3): MailboxConversationService 新增依赖 —— mysqlIt context 缺此 bean 会启动失败；
    // 默认 findAllById 返回空集合（旧行/无日历行 → calendarAttachment=null），
    // CalendarAttachmentIntegrationTest 的日历场景单独 stub。
    @MockBean
    private lateinit var mailRecordRepository: MailRecordRepository

    @MockBean
    private lateinit var expertMaterialService: ExpertMaterialService

    @MockBean
    private lateinit var inboundMailTagService: InboundMailTagService

    @MockBean
    private lateinit var expertSearchService: ExpertSearchService

    @MockBean
    private lateinit var pendingMailOperationService: PendingMailOperationService

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
        // 03 (I-4): 默认无日历存档 —— 现有断言不受影响（calendarAttachment=null）。
        Mockito.`when`(mailRecordRepository.findAllById(anyCollection())).thenReturn(emptyList())
        Mockito.`when`(mailRecordRepository.findById(Mockito.anyLong())).thenReturn(Optional.empty())
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
    // 会话级人工回信 POST（T3）：窄 DTO 转发、Auth 拦截、PendingMailSendResult JSON
    // ------------------------------------------------------------------

    @Test
    fun `conversation manual rich reply posts narrow fields and maps result json`() {
        // 全 raw 参数 stub（本仓 Kotlin/Mockito 约定：matcher 需 elvis 实值，见 eqValue helper）
        Mockito.`when`(
            pendingMailOperationService.sendConversationManualRichReply(
                1L, "b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f", "acc-a",
                "Re: follow", "<p>follow</p>", "follow", "op1", false, null
            )
        ).thenReturn(
            PendingMailSendResult(
                contactId = 1L,
                senderAccountCode = "acc-a",
                mailType = "MANUAL_RICH_REPLY",
                subject = "Re: follow",
                sendStatus = "SENT",
                messageId = "<manual-rich-1@weibo.com>"
            )
        )
        mockMvc.perform(
            post("/api/mail/mailbox/conversations/1/manual-rich-reply")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"requestId":"b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f",
                     "accountScope":"acc-a",
                     "subject":"Re: follow",
                     "htmlBody":"<p>follow</p>",
                     "textBody":"follow",
                     "operatorName":"op1"}
                    """.trimIndent()
                )
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.contactId").value(1))
            .andExpect(jsonPath("$.senderAccountCode").value("acc-a"))
            .andExpect(jsonPath("$.mailType").value("MANUAL_RICH_REPLY"))
            .andExpect(jsonPath("$.subject").value("Re: follow"))
            .andExpect(jsonPath("$.sendStatus").value("SENT"))
            .andExpect(jsonPath("$.messageId").value("<manual-rich-1@weibo.com>"))
            .andExpect(jsonPath("$.unsupportedAnswerArchiveStatus").value("NOT_APPLICABLE"))
    }

    @Test
    fun `conversation manual rich reply ignores account override and qa fields in body`() {
        // I-6/I-8：body 不允许 senderAccountCode/qaRuleIds/RAG —— DTO 不接收，extra 键忽略，
        // service 只收到 requestId/accountScope/正文/确认字段。
        Mockito.`when`(
            pendingMailOperationService.sendConversationManualRichReply(
                1L, "b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f", null,
                "Re: follow", "<p>follow</p>", "follow", null, false, null
            )
        ).thenReturn(PendingMailSendResult(
            contactId = 1L, senderAccountCode = "acc-a", mailType = "MANUAL_RICH_REPLY",
            subject = "Re: follow", sendStatus = "SENT", messageId = null
        ))
        mockMvc.perform(
            post("/api/mail/mailbox/conversations/1/manual-rich-reply")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"requestId":"b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f",
                     "subject":"Re: follow",
                     "htmlBody":"<p>follow</p>",
                     "textBody":"follow",
                     "senderAccountCode":"hacked-acc",
                     "qaRuleIds":[1,2],
                     "ragFactCodes":["KB-X"],
                     "trustReplyAssembly":{"x":1}}
                    """.trimIndent()
                )
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.sendStatus").value("SENT"))
    }

    @Test
    fun `anonymous conversation rich reply is rejected with 401 and never reaches service`() {
        mockMvc.perform(
            post("/api/mail/mailbox/conversations/1/manual-rich-reply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"requestId":"b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f","subject":"s","htmlBody":"<p>b</p>"}""")
        ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        assertTrue(
            Mockito.mockingDetails(pendingMailOperationService).invocations.none { it.method.name == "sendConversationManualRichReply" },
            "匿名请求绝不到达 service"
        )
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


// ---------------------------------------------------------------------------
// 03 (T3/I-3/I-4): CalendarAttachmentIntegrationTest —— 历史日历原件下载与时间线单独
// 附件元数据（timeline 批量读一次 / 归属 / 损坏 / 旧行 null / 真实 HTTP 字节）。真实
// MySQL 种子 + 真实 01 生成器（只 mock 模板目录与 processing 存在性）+ 真实 codec。
// 独立 @WebMvcTest context；与 MailboxConversationControllerTest 共用真实 JDBC 配置。
// ---------------------------------------------------------------------------
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@WebMvcTest(controllers = [MailboxConversationController::class, CalendarAttachmentController::class])
@Import(
    AuthWebConfig::class,
    ObjectMapper::class,
    MailboxConversationRealJdbcConfig::class,
    MailboxConversationRepository::class,
    MailboxConversationService::class
)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class CalendarAttachmentIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockBean
    private lateinit var authService: AuthService

    @MockBean
    private lateinit var expertContactRepository: ExpertContactRepository

    @MockBean
    private lateinit var senderAccountRepository: MailSenderAccountRepository

    @MockBean
    private lateinit var mailRecordRepository: MailRecordRepository

    @MockBean
    private lateinit var expertMaterialService: ExpertMaterialService

    @MockBean
    private lateinit var inboundMailTagService: InboundMailTagService

    @MockBean
    private lateinit var expertSearchService: ExpertSearchService

    @MockBean
    private lateinit var expertFollowService: ExpertFollowService

    private val meetingTemplateId = 9001L
    private val variableServiceForMeeting = Mockito.mock(MailVariableService::class.java)

    /** Mockito.any() 对 Kotlin 非空参数会返回 null；传真实默认值实例占位（既有测试同款手法）。 */
    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    /** Mockito.eq() 对 Kotlin 非空参数会返回 null；传真实默认值实例占位。 */
    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value


    /**
     * 01 生成器协作者 stub：正文由通用 `MEETING_INVITATION` 模板链路渲染。
     */
    private fun mockMeetingInvitationBody(templateService: MailComposeTemplateService) {
        Mockito.`when`(variableServiceForMeeting.resolveExpertProfileFor(anyValue(contact(1L)))).thenReturn(null)
        Mockito.`when`(
            variableServiceForMeeting.buildVariables(
                anyValue(activeAccount("acc-a")),
                Mockito.any(),
                Mockito.any(),
                Mockito.anyBoolean(),
                Mockito.any()
            )
        ).thenReturn(
            mapOf(
                "senderName" to "LuKai",
                "senderTitle" to "Customer Care Officer",
                "teamName" to "Qingfei Tech Talent Team",
                "countryName" to "China",
                "expertName" to "Professor Basdogan",
                "expertFamilyName" to "Basdogan"
            )
        )
        Mockito.`when`(
            templateService.renderByCode(
                eqValue("MEETING_INVITATION"),
                anyValue(emptyMap()),
                Mockito.anyInt()
            )
        ).thenAnswer { invocation ->
            val variables = invocation.getArgument<Map<String, String>>(1)
            ComposeTemplateRenderResult(
                subject = "Meeting invitation",
                body = "Dear " + variables["expertName"].orEmpty() + ",\n\n" +
                    "We have noted the meeting time as " + variables["meeting_time"].orEmpty() + ".\n\n" +
                    "Please join the meeting using the following link:\n\n" +
                    variables["zoom_url"].orEmpty() + "\n\n" +
                    "Best regards,\n" + variables["senderName"].orEmpty() + ", " +
                    variables["senderTitle"].orEmpty()
            )
        }
    }

    @BeforeEach
    fun setUp() {
        cleanup()
        seedAccount("acc-a")
        seedContactRow(1, "Alice Expert", "alice@example.org")
        seedContactRow(2, "Bob Expert", "bob@example.org")
        Mockito.`when`(authService.findUser("op1")).thenReturn(
            AdminUser(
                username = "op1",
                passwordHash = "not-checked",
                mustChangePassword = false,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )
        Mockito.`when`(
            senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        ).thenReturn(listOf(activeAccount("acc-a"), activeAccount("acc-b")))
        listOf(1L, 2L).forEach { id ->
            Mockito.`when`(expertContactRepository.findById(id))
                .thenReturn(Optional.of(contact(id)))
        }
        Mockito.`when`(inboundMailTagService.listTagsBatch(anyCollection()))
            .thenReturn(emptyMap())
        Mockito.`when`(expertMaterialService.resolveMessageAttachments(Mockito.anyString(), Mockito.anyLong()))
            .thenReturn(emptyList())
        // 默认无日历存档：现有窗口只有旧行时 calendarAttachment=null。
        Mockito.`when`(mailRecordRepository.findAllById(anyCollection())).thenReturn(emptyList())
        Mockito.`when`(mailRecordRepository.findById(Mockito.anyLong())).thenReturn(Optional.empty())
    }

    @AfterEach
    fun tearDown() {
        cleanup()
    }

    private fun meetingInput() = MeetingInput(
        zoneId = "Europe/Istanbul",
        startLocal = "2026-09-11T10:00",
        endLocal = "2026-09-11T10:30",
        zoomUrl = "https://zoom.us/j/92123456789?pwd=abcDEF123",
        generatedAt = "2026-09-09T02:00:00Z"
    )

    /** 真实 01 生成器（只 mock 通用模板渲染与 processing 存在性，ICS 全真实）。 */
    private fun previewFor(contactId: Long = 1L): MeetingPreviewResponse {
        val inboundRepo = Mockito.mock(InboundMailProcessingRepository::class.java)
        Mockito.`when`(inboundRepo.findById(100L)).thenReturn(
            Optional.of(
                InboundMailProcessing(
                    id = 100L, senderAccountCode = "acc-a", imapUid = 1L, messageId = "in-cal-1",
                    fromEmail = "expert@example.org", subject = "Question", body = "Body",
                    cleanedBody = "Body", receivedAt = LocalDateTime.now(),
                    processStatus = "MANUAL_REVIEW", processReason = "QA_NO_MATCH",
                    expertContactId = contactId
                )
            )
        )
        val templateService = Mockito.mock(MailComposeTemplateService::class.java)
        mockMeetingInvitationBody(templateService)
        val service = MeetingConfirmationService(
            inboundRepo,
            expertContactRepository,
            Mockito.mock(MailSenderAccountService::class.java),
            templateService,
            MailContentService(),
            variableServiceForMeeting
        )
        return service.validateAndBuild(
            processingId = 100L,
            contact = contact(contactId),
            account = activeAccount("acc-a"),
            input = meetingInput()
        )
    }

    /** 与 02 finalize 同款存档 JSON（真实 codec serialize，schemaVersion=1）。 */
    private fun archiveJson(preview: MeetingPreviewResponse): String = CalendarAttachmentCodec.serialize(
        CalendarAttachmentSnapshot(
            schemaVersion = MeetingConfirmationDomain.CALENDAR_SCHEMA_VERSION,
            filename = preview.attachment.filename,
            contentType = preview.attachment.contentType,
            icsText = preview.attachment.icsText,
            sha256 = preview.attachment.sha256,
            semanticSha256 = preview.attachment.semanticSha256
        )
    )

    private fun mailRecordOf(
        id: Long,
        contactId: Long,
        calendarJson: String?,
        accountCode: String = "acc-a",
        sendStatus: String = "SENT"
    ) = MailRecord(
        id = id,
        expertContactId = contactId,
        direction = "OUTBOUND",
        mailType = "MANUAL_RICH_REPLY",
        senderAccountCode = accountCode,
        messageId = "out-cal-$id",
        inReplyTo = "in-cal-1",
        subject = "cal-subject-$id",
        body = "body",
        matchedQaRuleId = null,
        sendStatus = sendStatus,
        receivedAt = null,
        sentAt = LocalDateTime.now(),
        calendarAttachmentJson = calendarJson
    )

    private fun stubMailRecords(records: List<MailRecord>) {
        Mockito.`when`(mailRecordRepository.findAllById(anyCollection()))
            .thenReturn(records)
        records.forEach { record ->
            Mockito.`when`(mailRecordRepository.findById(record.id!!)).thenReturn(Optional.of(record))
        }
    }

    @Test
    fun `timeline attaches calendar metadata only for outbound sent rows in one batch read`() {
        val preview = previewFor()
        val json = archiveJson(preview)
        insertOutboundCalendarRow(900001L, 1L, json, subject = "cal-with-attachment")
        insertOutboundCalendarRow(900002L, 1L, null, subject = "cal-old-null")
        stubMailRecords(
            listOf(
                mailRecordOf(900001L, 1L, json),
                mailRecordOf(900002L, 1L, null)
            )
        )

        val result = mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val items = objectMapper.readTree(utf8Body(result))["items"]
        val withAttachment = items.first { it["source"].asText() == "MAIL_RECORD" && it["id"].asLong() == 900001L }
        val calendar = withAttachment["calendarAttachment"]
        assertNotNull(calendar, "SENT 出站存档行必须暴露 calendarAttachment")
        assertEquals(preview.attachment.filename, calendar["filename"].asText())
        assertEquals(
            preview.attachment.icsText.toByteArray(StandardCharsets.UTF_8).size,
            calendar["byteLength"].asInt()
        )
        assertEquals(
            "/api/mail/conversations/1/messages/900001/calendar-attachment",
            calendar["downloadUrl"].asText()
        )
        val oldNull = items.first { it["source"].asText() == "MAIL_RECORD" && it["id"].asLong() == 900002L }
        assertTrue(oldNull["calendarAttachment"].isNull, "无存档旧行 calendarAttachment 必须为 null")
        // I-4: 整窗只批量读一次。
        verify(mailRecordRepository, times(1)).findAllById(anyCollection())
    }

    @Test
    fun `inbound rows with the same numeric id never read the outbound snapshot`() {
        val preview = previewFor()
        val json = archiveJson(preview)
        insertProcessingRow(1L, 3901, "PROCESSED", "2026-09-02 09:00:00", "shared-cal-in", "shared cal in")
        val sharedId = processingIdOf("shared-cal-in")
        insertOutboundCalendarRow(sharedId, 1L, json, subject = "out-shared-cal")
        stubMailRecords(listOf(mailRecordOf(sharedId, 1L, json)))

        val result = mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val items = objectMapper.readTree(utf8Body(result))["items"]
        val outbound = items.first { it["source"].asText() == "MAIL_RECORD" }
        assertEquals(sharedId, outbound["id"].asLong())
        assertNotNull(outbound["calendarAttachment"], "OUTBOUND 行携带自己的快照")
        val inbound = items.first { it["source"].asText() == "INBOUND_PROCESSING" }
        assertEquals(sharedId, inbound["id"].asLong())
        assertTrue(inbound["calendarAttachment"].isNull, "INBOUND 同数值 id 绝不串日历附件")
    }

    @Test
    fun `download returns exactly the archived original bytes with archive headers`() {
        val preview = previewFor()
        val json = archiveJson(preview)
        insertOutboundCalendarRow(900001L, 1L, json)
        stubMailRecords(listOf(mailRecordOf(900001L, 1L, json)))

        val result = mockMvc.perform(
            get("/api/mail/conversations/1/messages/900001/calendar-attachment").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val expectedBytes = preview.attachment.icsText.toByteArray(StandardCharsets.UTF_8)
        assertArrayEquals(expectedBytes, result.response.contentAsByteArray, "下载字节必须等于存档原件")
        val contentType = result.response.getHeader("Content-Type")
        assertTrue(contentType != null && contentType.startsWith("text/calendar") && contentType.contains("UTF-8"),
            "Content-Type 必须是 text/calendar; charset=UTF-8: $contentType")
        val disposition = result.response.getHeader("Content-Disposition")
        assertTrue(disposition != null && disposition.startsWith("attachment;") && disposition.contains(preview.attachment.filename),
            "必须是 attachment + 安全 filename: $disposition")
        assertEquals(expectedBytes.size.toString(), result.response.getHeader("Content-Length"))
        assertEquals("private,no-store", result.response.getHeader("Cache-Control"))
        assertEquals("nosniff", result.response.getHeader("X-Content-Type-Options"))
    }

    @Test
    fun `download rejects wrong-contact not-sent corrupt old-null and out-of-scope account uniformly 404`() {
        val preview = previewFor()
        val json = archiveJson(preview)
        // 归属不一致：记录属于 contact 2，path contactId=1。
        insertOutboundCalendarRow(900004L, 2L, json)
        stubMailRecords(listOf(mailRecordOf(900004L, 2L, json)))
        mockMvc.perform(
            get("/api/mail/conversations/1/messages/900004/calendar-attachment").session(sessionOf("op1"))
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("日历附件不可用"))

        // 未 SENT（FAILED）不可下载。
        insertOutboundCalendarRow(900005L, 1L, json, sendStatus = "FAILED")
        stubMailRecords(listOf(mailRecordOf(900005L, 1L, json, sendStatus = "FAILED")))
        mockMvc.perform(
            get("/api/mail/conversations/1/messages/900005/calendar-attachment").session(sessionOf("op1"))
        ).andExpect(status().isNotFound)

        // 损坏 JSON / 空快照：404。
        insertOutboundCalendarRow(900006L, 1L, "not-a-json-snapshot")
        stubMailRecords(listOf(mailRecordOf(900006L, 1L, "not-a-json-snapshot")))
        mockMvc.perform(
            get("/api/mail/conversations/1/messages/900006/calendar-attachment").session(sessionOf("op1"))
        ).andExpect(status().isNotFound)

        // 旧行无存档（null）→ 下载 404。
        insertOutboundCalendarRow(900007L, 1L, null)
        stubMailRecords(listOf(mailRecordOf(900007L, 1L, null)))
        mockMvc.perform(
            get("/api/mail/conversations/1/messages/900007/calendar-attachment").session(sessionOf("op1"))
        ).andExpect(status().isNotFound)

        // 账号不在会话 active 范围（acc-zz 未在 findAllByAccountCodeNot stub 内）→ 404。
        insertOutboundCalendarRow(900008L, 1L, json, accountCode = "acc-zz")
        stubMailRecords(listOf(mailRecordOf(900008L, 1L, json, accountCode = "acc-zz")))
        mockMvc.perform(
            get("/api/mail/conversations/1/messages/900008/calendar-attachment").session(sessionOf("op1"))
        ).andExpect(status().isNotFound)

        // 不存在的记录 → 404。
        mockMvc.perform(
            get("/api/mail/conversations/1/messages/999999/calendar-attachment").session(sessionOf("op1"))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `timeline leaves corrupt snapshot rows null and never reads the batch for inbound only windows`() {
        insertOutboundCalendarRow(900006L, 1L, "corrupt-json")
        stubMailRecords(listOf(mailRecordOf(900006L, 1L, "corrupt-json")))
        val result = mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages").session(sessionOf("op1"))
        ).andExpect(status().isOk).andReturn()
        val items = objectMapper.readTree(utf8Body(result))["items"]
        val corrupt = items.first { it["source"].asText() == "MAIL_RECORD" && it["id"].asLong() == 900006L }
        assertTrue(corrupt["calendarAttachment"].isNull, "损坏快照不得出现在 timeline")

        // 纯 INBOUND 窗口：不读 outbound 快照（先移除本测试先前插入的 outbound 行）。
        jdbcTemplate.update("DELETE FROM mail_record WHERE id = 900006")
        Mockito.clearInvocations(mailRecordRepository)
        insertProcessing(1L, "PROCESSED", "2026-09-02 09:00:00", "cal-in-only")
        mockMvc.perform(
            get("/api/mail/mailbox/conversations/1/messages").session(sessionOf("op1"))
        ).andExpect(status().isOk)
        verify(mailRecordRepository, never()).findAllById(anyCollection())
    }

    // ───────────────────────── 基建（真实 MySQL JDBC） ─────────────────────────

    private fun sessionOf(username: String): MockHttpSession =
        MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }

    private fun activeAccount(code: String = "acc-a"): MailSenderAccount =
        MailSenderAccount(
            accountCode = code,
            senderEmail = "$code@fixture.local",
            senderName = code,
            senderTitle = "Title",
            senderDisplayName = code,
            teamName = "Team",
            countryName = "CN",
            smtpHost = "smtp.fixture",
            smtpPort = 465,
            smtpUsername = code,
            smtpPassword = "pw",
            imapHost = "imap.fixture",
            imapPort = 993,
            imapUsername = code,
            imapPassword = "pw",
            enabled = true
        )

    private fun contact(id: Long): ExpertContact =
        ExpertContact(
            id = id,
            campaignId = id,
            orcidId = "0000-0000-0000-%04d".format(id),
            expertEmail = "expert$id@example.org",
            expertName = "Expert $id",
            currentStatus = "NEW",
            operatorStatus = "CONTACTED",
            currentIndexLevel = "CANDIDATE"
        )

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

    private fun processingIdOf(messageId: String): Long =
        jdbcTemplate.queryForObject(
            "SELECT id FROM inbound_mail_processing WHERE message_id = ?", Long::class.java, messageId
        )!!

    /** 真实落库一条 OUTBOUND mail_record（可带 calendar_attachment_json 存档）。 */
    private fun insertOutboundCalendarRow(
        id: Long,
        contactId: Long,
        calendarJson: String?,
        sendStatus: String = "SENT",
        accountCode: String = "acc-a",
        mailType: String = "MANUAL_RICH_REPLY",
        messageId: String = "out-cal-$id",
        subject: String = "cal-subject-$id"
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (id, expert_contact_id, direction, mail_type, sender_account_code, triggered_by,
                 message_id, subject, body, send_status, sent_at, created_at, calendar_attachment_json)
            VALUES (?, ?, 'OUTBOUND', ?, ?, 'SYSTEM', ?, ?, 'body', ?, ?, ?, ?)
            """.trimIndent(),
            id, contactId, mailType, accountCode, messageId, subject, sendStatus,
            if (sendStatus == "SENT") Timestamp.valueOf(ts("2026-09-03 09:00:00")) else null,
            Timestamp.valueOf(ts("2026-09-03 09:00:00")), calendarJson
        )
    }

    private fun ts(value: String): LocalDateTime =
        LocalDateTime.parse(value.replace(' ', 'T'))

    /** mockMvc 响应按 UTF-8 读取（MockHttpServletResponse 默认 ISO-8859-1）。 */
    private fun utf8Body(result: MvcResult): String =
        String(result.response.contentAsByteArray, StandardCharsets.UTF_8)
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
