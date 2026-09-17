package com.weibo.talentintroduction.campaign.controller

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarEvent
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MeetingCalendarEventRepository
import com.weibo.talentintroduction.campaign.service.MeetingCalendarService
import com.weibo.talentintroduction.mail.controller.MailboxConversationRealJdbcConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDateTime

/**
 * 01（I-1～I-5）排期接口契约测试（@WebMvcTest，service 为 mock）。
 *
 * 只断言接口层事实：状态码与错误体、响应字段集合（无额外 source/isCancelled/changed
 * 布尔值）、北京时间入参到 UTC 输出的接口形态。409 必须由本 controller 的专用 advice
 * 返回 —— 走真实异常解析链，证明它优先于 GlobalExceptionHandler 的兜底 500。
 *
 * 真实认证 + 真实 MySQL 的端到端（匿名 401、落库 UTC、交集与游标分页）在同文件
 * [MeetingCalendarControllerMysqlTest]（mysqlIt 门禁）。这两个文件共同承担验收标准
 * 中的「真实认证、接口与 MySQL 查询」。
 */
@WebMvcTest(controllers = [MeetingCalendarController::class])
@Import(MeetingCalendarControllerTest.KotlinObjectMapperConfig::class)
class MeetingCalendarControllerTest {

    /**
     * 本 slice 需要解析 Kotlin data class @RequestBody；测试侧显式提供带 KotlinModule
     * 的 primary ObjectMapper（与 MailboxConversationControllerTest 同款手法）。
     */
    @TestConfiguration
    class KotlinObjectMapperConfig {
        @Bean
        fun objectMapper(): ObjectMapper =
            ObjectMapper()
                .registerModule(KotlinModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var service: MeetingCalendarService

    @Test
    fun `POST events returns 201 with the stored UTC instants`() {
        stubCreate("2026-09-18T10:00", "2026-09-18T10:30", "https://meet.example/a", "note")

        mockMvc.perform(
            post("/api/meeting-calendar/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"contactId":1,"startBeijing":"2026-09-18T10:00","endBeijing":"2026-09-18T10:30",
                     "meetingLink":"https://meet.example/a","note":"note"}
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(3))
            .andExpect(jsonPath("$.contactId").value(1))
            .andExpect(jsonPath("$.startUtc").value("2026-09-18T02:00:00Z"))
            .andExpect(jsonPath("$.endUtc").value("2026-09-18T02:30:00Z"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.updatedAt").value("2026-09-18T02:00:00Z"))
    }

    @Test
    fun `event response carries exactly the documented fields`() {
        Mockito.`when`(service.get(3L)).thenReturn(row())

        val body = mockMvc.perform(get("/api/meeting-calendar/events/3"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expertName").value("Expert"))
            .andExpect(jsonPath("$.expertEmail").value("expert@example.com"))
            .andReturn().response.contentAsByteArray

        val fields = objectMapper.readTree(body).fieldNames().asSequence().toSet()
        assertEquals(
            setOf(
                "id", "contactId", "expertName", "expertEmail", "sourceMailRecordId",
                "startUtc", "endUtc", "meetingLink", "note", "status", "cancelReason",
                "createdAt", "updatedAt"
            ),
            fields,
            "不新增持久化的 source/hasSchedule/isCancelled/changed 字段"
        )
    }

    @Test
    fun `a stale version is answered by this controller advice with 409 and not the generic 500`() {
        Mockito.`when`(
            service.update(9L, "2026-09-19T10:00", "2026-09-19T10:30", null, null, "2026-09-18T02:00:00Z")
        ).thenThrow(MeetingCalendarService.MeetingCalendarConflictException("版本已过期"))

        mockMvc.perform(
            put("/api/meeting-calendar/events/9")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"startBeijing":"2026-09-19T10:00","endBeijing":"2026-09-19T10:30",
                     "expectedUpdatedAt":"2026-09-18T02:00:00Z"}
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("版本已过期"))
    }

    @Test
    fun `invalid arguments map to 400 and missing events to 404`() {
        Mockito.`when`(service.createManual(1L, "2026-09-18T10:30", "2026-09-18T10:30", null, null))
            .thenThrow(IllegalArgumentException("end must be after start"))
        mockMvc.perform(
            post("/api/meeting-calendar/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"contactId":1,"startBeijing":"2026-09-18T10:30","endBeijing":"2026-09-18T10:30"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))

        Mockito.`when`(service.list(null, null, null, false, 100, null))
            .thenThrow(IllegalArgumentException("from and to are required without contactId"))
        mockMvc.perform(get("/api/meeting-calendar/events"))
            .andExpect(status().isBadRequest)

        Mockito.`when`(service.get(404L)).thenThrow(NoSuchElementException("Meeting calendar event not found: 404"))
        mockMvc.perform(get("/api/meeting-calendar/events/404"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `GET events applies the documented defaults and returns the cursor`() {
        Mockito.`when`(
            service.list("2026-09-17T16:00:00Z", "2026-09-18T16:00:00Z", null, false, 100, null)
        ).thenReturn(MeetingCalendarService.Page(listOf(row()), "cursor-1"))
        Mockito.`when`(
            service.list("2026-09-17T16:00:00Z", "2026-09-18T16:00:00Z", 1L, true, 200, null)
        ).thenReturn(MeetingCalendarService.Page(listOf(row()), null))

        mockMvc.perform(
            get("/api/meeting-calendar/events")
                .param("from", "2026-09-17T16:00:00Z")
                .param("to", "2026-09-18T16:00:00Z")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].id").value(3))
            .andExpect(jsonPath("$.nextCursor").value("cursor-1"))

        mockMvc.perform(
            get("/api/meeting-calendar/events")
                .param("from", "2026-09-17T16:00:00Z")
                .param("to", "2026-09-18T16:00:00Z")
                .param("contactId", "1")
                .param("showCancelled", "true")
                .param("limit", "200")
        ).andExpect(status().isOk)
        Mockito.verify(service).list("2026-09-17T16:00:00Z", "2026-09-18T16:00:00Z", 1L, true, 200, null)
    }

    @Test
    fun `GET summaries parses the id list and keeps zero rows explicit`() {
        Mockito.`when`(service.summaries(listOf(1L, 2L))).thenReturn(
            listOf(
                MeetingCalendarService.MeetingCalendarSummary(1L, 0, null),
                MeetingCalendarService.MeetingCalendarSummary(2L, 1, row())
            )
        )

        mockMvc.perform(get("/api/meeting-calendar/summaries").param("contactIds", "1,2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].contactId").value(1))
            .andExpect(jsonPath("$[0].activeCount").value(0))
            .andExpect(jsonPath("$[0].next").doesNotExist())
            .andExpect(jsonPath("$[1].activeCount").value(1))
            .andExpect(jsonPath("$[1].next.id").value(3))

        Mockito.`when`(service.summaries(emptyList())).thenReturn(emptyList())
        mockMvc.perform(get("/api/meeting-calendar/summaries"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
        Mockito.verify(service).summaries(emptyList())

        mockMvc.perform(get("/api/meeting-calendar/summaries").param("contactIds", "1,abc"))
            .andExpect(status().isBadRequest)
    }

    private fun stubCreate(start: String, end: String, link: String?, note: String?) {
        Mockito.`when`(service.createManual(1L, start, end, link, note)).thenReturn(row())
    }

    private fun row(): MeetingCalendarEventRepository.EventRow = MeetingCalendarEventRepository.EventRow(
        MeetingCalendarEvent(
            id = 3L,
            expertContactId = 1L,
            startsAtUtc = Instant.parse("2026-09-18T02:00:00Z"),
            endsAtUtc = Instant.parse("2026-09-18T02:30:00Z"),
            meetingLink = "https://meet.example/a",
            note = "note",
            createdAt = Instant.parse("2026-09-18T02:00:00Z"),
            updatedAt = Instant.parse("2026-09-18T02:00:00Z")
        ),
        "Expert",
        "expert@example.com"
    )
}

/**
 * 01（I-1～I-5）排期接口真实认证 + 真实 MySQL 端到端（mysqlIt 门禁）。
 *
 * 真实 AuthInterceptor（匿名 401）+ 真实 [MeetingCalendarService] + 真实参数化 JDBC
 * 仓储 + 真实库；只 mock 专家联系方式存在性（本 child 的 MySQL 关注点是排期表本身，
 * 排期行仍受 fk_meeting_calendar_contact 真实约束）。
 * 覆盖：落库即 UTC 墙上时间、北京日边界交集（跨午夜）、游标分页、版本冲突与取消
 * 终态（HTTP 409/200）、以及 I-4 —— 排期写入不改专家行、不新增邮件、不删行。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@WebMvcTest(controllers = [MeetingCalendarController::class])
@Import(
    AuthWebConfig::class,
    MeetingCalendarControllerTest.KotlinObjectMapperConfig::class,
    MailboxConversationRealJdbcConfig::class,
    MeetingCalendarEventRepository::class,
    MeetingCalendarService::class
)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class MeetingCalendarControllerMysqlTest {

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

    @BeforeEach
    fun setUp() {
        cleanup()
        seedAccount()
        jdbcTemplate.update(
            "INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) " +
                "VALUES (1, 'FIXTURE-1', 'Fixture', (SELECT id FROM mail_sender_account WHERE account_code = 'acc-a'))"
        )
        jdbcTemplate.update(
            "INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name, current_status) " +
                "VALUES (1, 1, '0000-0000-0000-0001', 'alice@example.org', 'Alice Expert', 'NEW')"
        )
        Mockito.`when`(authService.findUser("op1")).thenReturn(
            AdminUser(
                username = "op1",
                passwordHash = "not-checked",
                mustChangePassword = false,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )
        Mockito.`when`(expertContactRepository.existsById(1L)).thenReturn(true)
    }

    @AfterEach
    fun tearDown() {
        cleanup()
    }

    @Test
    fun `anonymous calendar requests are rejected and authenticated ones persist UTC wall time`() {
        mockMvc.perform(
            get("/api/meeting-calendar/events")
                .param("from", "2026-09-17T16:00:00Z")
                .param("to", "2026-09-18T16:00:00Z")
        ).andExpect(status().isUnauthorized)

        val created = mockMvc.perform(
            post("/api/meeting-calendar/events")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"contactId":1,"startBeijing":"2026-09-18T10:00","endBeijing":"2026-09-18T10:30"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.startUtc").value("2026-09-18T02:00:00Z"))
            .andExpect(jsonPath("$.endUtc").value("2026-09-18T02:30:00Z"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.sourceMailRecordId").isEmpty())
            .andExpect(jsonPath("$.expertName").value("Alice Expert"))
            .andReturn()

        val id = objectMapper.readTree(created.response.contentAsByteArray).get("id").asLong()
        val raw = jdbcTemplate.queryForObject(
            "SELECT starts_at_utc FROM meeting_calendar_event WHERE id = ?", String::class.java, id
        )!!
        assertTrue(raw.startsWith("2026-09-18 02:00:00"), "库里是 UTC 墙上时间：$raw")

        mockMvc.perform(get("/api/meeting-calendar/events/$id").session(sessionOf("op1")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.contactId").value(1))
            .andExpect(jsonPath("$.expertEmail").value("alice@example.org"))

        mockMvc.perform(
            get("/api/meeting-calendar/summaries").session(sessionOf("op1")).param("contactIds", "1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].activeCount").value(1))
            .andExpect(jsonPath("$[0].next.id").value(id))

        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mail_record", Long::class.java))
        assertEquals(
            "NOT_CONTACTED",
            jdbcTemplate.queryForObject("SELECT operator_status FROM expert_contact WHERE id = 1", String::class.java)
        )
    }

    @Test
    fun `two clients share one version and cancellation is terminal over HTTP`() {
        val created = objectMapper.readTree(
            mockMvc.perform(
                post("/api/meeting-calendar/events")
                    .session(sessionOf("op1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"contactId":1,"startBeijing":"2026-09-18T10:00","endBeijing":"2026-09-18T10:30"}""")
            ).andExpect(status().isCreated).andReturn().response.contentAsByteArray
        )
        val id = created.get("id").asLong()
        val firstVersion = created.get("updatedAt").asText()

        val updated = objectMapper.readTree(
            mockMvc.perform(
                put("/api/meeting-calendar/events/$id")
                    .session(sessionOf("op1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"startBeijing":"2026-09-19T14:00","endBeijing":"2026-09-19T14:30",
                         "meetingLink":"https://meet.example/room","expectedUpdatedAt":"$firstVersion"}
                        """.trimIndent()
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.startUtc").value("2026-09-19T06:00:00Z"))
                .andReturn().response.contentAsByteArray
        )
        val secondVersion = updated.get("updatedAt").asText()
        assertTrue(secondVersion != firstVersion, "成功改期写入新版本")

        mockMvc.perform(
            put("/api/meeting-calendar/events/$id")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"startBeijing":"2026-09-20T14:00","endBeijing":"2026-09-20T14:30",
                     "expectedUpdatedAt":"$firstVersion"}
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))

        mockMvc.perform(
            post("/api/meeting-calendar/events/$id/cancel")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expectedUpdatedAt":"$secondVersion","reason":"专家时间调整"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelReason").value("专家时间调整"))

        mockMvc.perform(
            put("/api/meeting-calendar/events/$id")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"startBeijing":"2026-09-21T14:00","endBeijing":"2026-09-21T14:30",
                     "expectedUpdatedAt":"$secondVersion"}
                    """.trimIndent()
                )
        ).andExpect(status().isConflict)

        mockMvc.perform(
            post("/api/meeting-calendar/events/$id/cancel")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expectedUpdatedAt":"$firstVersion","reason":"第二个原因"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelReason").value("专家时间调整"))

        mockMvc.perform(
            get("/api/meeting-calendar/events")
                .session(sessionOf("op1"))
                .param("from", "2026-09-18T00:00:00Z")
                .param("to", "2026-09-21T00:00:00Z")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(0))

        mockMvc.perform(
            get("/api/meeting-calendar/events")
                .session(sessionOf("op1"))
                .param("from", "2026-09-18T00:00:00Z")
                .param("to", "2026-09-21T00:00:00Z")
                .param("showCancelled", "true")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].cancelReason").value("专家时间调整"))

        // I-3/I-4：取消保留行，专家流程状态与邮件都不动
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM meeting_calendar_event", Long::class.java))
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mail_record", Long::class.java))
        assertEquals(
            "NEW",
            jdbcTemplate.queryForObject("SELECT current_status FROM expert_contact WHERE id = 1", String::class.java)
        )
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact_status_history WHERE expert_contact_id = 1", Long::class.java
        ))
    }

    @Test
    fun `calendar range intersects Beijing days and paginates through the cursor`() {
        listOf(
            "2026-09-18T09:00" to "2026-09-18T09:30",
            "2026-09-18T11:00" to "2026-09-18T11:30",
            "2026-09-18T22:00" to "2026-09-19T02:00",
            "2026-09-10T10:00" to "2026-09-10T10:30"
        ).forEach { (start, end) ->
            mockMvc.perform(
                post("/api/meeting-calendar/events")
                    .session(sessionOf("op1"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"contactId":1,"startBeijing":"$start","endBeijing":"$end"}""")
            ).andExpect(status().isCreated)
        }

        val day18First = objectMapper.readTree(
            mockMvc.perform(
                get("/api/meeting-calendar/events")
                    .session(sessionOf("op1"))
                    .param("from", "2026-09-17T16:00:00Z")
                    .param("to", "2026-09-18T16:00:00Z")
                    .param("limit", "2")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().response.contentAsByteArray
        )
        val cursor = day18First.get("nextCursor").asText()
        val day18Second = objectMapper.readTree(
            mockMvc.perform(
                get("/api/meeting-calendar/events")
                    .session(sessionOf("op1"))
                    .param("from", "2026-09-17T16:00:00Z")
                    .param("to", "2026-09-18T16:00:00Z")
                    .param("limit", "2")
                    .param("cursor", cursor)
            ).andExpect(status().isOk).andReturn().response.contentAsByteArray
        )
        assertEquals(1, day18Second.get("items").size())
        assertTrue(day18Second.get("nextCursor").isNull, "最后一页没有游标")
        val day18Starts = (day18First.get("items") + day18Second.get("items"))
            .map { it.get("startUtc").asText() }
        assertEquals(day18Starts.sorted(), day18Starts)
        assertEquals(3, day18Starts.distinct().size, "北京 09-18 一天读到 3 场（含跨午夜场次）")

        mockMvc.perform(
            get("/api/meeting-calendar/events")
                .session(sessionOf("op1"))
                .param("from", "2026-09-18T16:00:00Z")
                .param("to", "2026-09-19T16:00:00Z")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].startUtc").value("2026-09-18T14:00:00Z"))
            .andExpect(jsonPath("$.items[0].endUtc").value("2026-09-18T18:00:00Z"))

        mockMvc.perform(
            get("/api/meeting-calendar/events")
                .session(sessionOf("op1"))
                .param("from", "2026-09-19T16:00:00Z")
                .param("to", "2026-09-20T16:00:00Z")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(0))

        mockMvc.perform(
            get("/api/meeting-calendar/events")
                .session(sessionOf("op1"))
                .param("from", "2026-09-17T16:00:00Z")
                .param("to", "2026-12-01T16:00:00Z")
        )
            .andExpect(status().isBadRequest)
    }

    private fun sessionOf(username: String): MockHttpSession =
        MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }

    private fun cleanup() {
        jdbcTemplate.update("DELETE FROM meeting_calendar_event")
        jdbcTemplate.update("DELETE FROM mail_record")
        jdbcTemplate.update("DELETE FROM expert_contact")
        jdbcTemplate.update("DELETE FROM campaign")
        jdbcTemplate.update("DELETE FROM mail_sender_account")
    }

    private fun seedAccount() {
        jdbcTemplate.update(
            """
            INSERT INTO mail_sender_account
                (account_code, sender_email, sender_name, smtp_host, smtp_port,
                 smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
            VALUES ('acc-a', 'acc-a@fixture.local', 'acc-a', 'smtp.fixture', 465,
                    'acc-a', 'pw', 'imap.fixture', 993, 'acc-a', 'pw')
            """.trimIndent()
        )
    }
}
