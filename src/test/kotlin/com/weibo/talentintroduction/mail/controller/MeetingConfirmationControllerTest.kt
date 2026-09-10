package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.mail.service.MeetingConfirmationDomain
import com.weibo.talentintroduction.mail.service.MeetingConfirmationService
import com.weibo.talentintroduction.mail.service.MeetingInput
import com.weibo.talentintroduction.mail.service.MeetingOptionsResponse
import com.weibo.talentintroduction.mail.service.MeetingPreviewRequest
import com.weibo.talentintroduction.mail.service.MeetingPreviewResponse
import com.weibo.talentintroduction.mail.service.MeetingTimeZoneOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.NoSuchElementException

@WebMvcTest(controllers = [MeetingConfirmationController::class])
@Import(AuthWebConfig::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class MeetingConfirmationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var authService: AuthService

    @MockBean
    private lateinit var meetingConfirmationService: MeetingConfirmationService

    private fun sessionOf(username: String = "admin"): MockHttpSession =
        MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }

    private fun adminUser(username: String = "admin"): AdminUser =
        AdminUser(
            username = username,
            passwordHash = "not-checked",
            mustChangePassword = false,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

    private fun stubAuth() {
        Mockito.`when`(authService.findUser("admin")).thenReturn(adminUser())
    }

    /** Mockito.any() 对 Kotlin 非空参数会返回 null；传一个真实默认值实例占位（既有测试同款手法）。 */
    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun sampleOptions() = MeetingOptionsResponse(
        targetKey = "1:acc-1",
        resolvedAccountCode = "acc-1",
        generatedAt = "2026-09-09T03:00:40Z",
        defaultZoneId = "Asia/Shanghai"
    )

    private fun sampleZones() = listOf(
        MeetingTimeZoneOption(
            id = "Europe/Istanbul",
            labelZh = "土耳其 · 伊斯坦布尔",
            aliases = listOf("土耳其", "伊斯坦布尔", "Turkey", "Türkiye", "Istanbul"),
            offsetLabel = "UTC+3",
            offsetSeconds = 10800
        ),
        MeetingTimeZoneOption(
            id = "Asia/Kolkata",
            labelZh = "印度 · 加尔各答",
            aliases = listOf("印度", "加尔各答", "India", "Kolkata"),
            offsetLabel = "UTC+5:30",
            offsetSeconds = 19800
        )
    )

    private fun samplePreview() = MeetingPreviewResponse(
        targetKey = "1:acc-1",
        resolvedAccountCode = "acc-1",
        meeting = MeetingInput(
            zoneId = "Europe/Istanbul",
            startLocal = "2026-09-11T10:00",
            endLocal = "2026-09-11T10:30",
            zoomUrl = "https://zoom.us/j/1",
            generatedAt = "2026-09-09T03:00:40Z"
        ),
        textBody = "Dear Professor Basdogan,",
        htmlBody = "<p>Dear Professor Basdogan,</p>",
        meetingTime = "Friday, September 11, 2026, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)",
        startUtc = "2026-09-11T07:00:00Z",
        endUtc = "2026-09-11T07:30:00Z",
        chinaTime = "2026/09/11 周五 15:00 – 2026/09/11 周五 15:30",
        durationMinutes = 30,
        attachment = com.weibo.talentintroduction.mail.service.MeetingCalendarAttachment(
            filename = "meeting-2026-09-11-Professor-Basdogan.ics",
            contentType = MeetingConfirmationDomain.CALENDAR_CONTENT_TYPE,
            icsText = "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n",
            byteLength = 40,
            sha256 = "a".repeat(64),
            semanticSha256 = "b".repeat(64)
        )
    )

    // ── 认证：沿 /api/** AuthInterceptor，无登录豁免（I-1） ──

    @Test
    fun `options requires authentication`() {
        stubAuth()
        mockMvc.perform(
            get("/api/mail/unmatched-inbound/7/meeting-confirmation/options")
                .param("contactId", "1")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `time-zones requires authentication`() {
        stubAuth()
        mockMvc.perform(get("/api/mail/meeting-confirmation/time-zones").param("date", "2026-09-11"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `preview requires authentication`() {
        stubAuth()
        mockMvc.perform(
            post("/api/mail/unmatched-inbound/7/meeting-confirmation/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"contactId":1,"meeting":{}}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `authenticated options returns only target account generatedAt and zone`() {
        stubAuth()
        Mockito.`when`(meetingConfirmationService.options(7L, 1L, null))
            .thenReturn(sampleOptions())

        mockMvc.perform(
            get("/api/mail/unmatched-inbound/7/meeting-confirmation/options")
                .session(sessionOf())
                .param("contactId", "1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetKey").value("1:acc-1"))
            .andExpect(jsonPath("$.resolvedAccountCode").value("acc-1"))
            .andExpect(jsonPath("$.defaultZoneId").value("Asia/Shanghai"))
            .andExpect(jsonPath("$.generatedAt").value("2026-09-09T03:00:40Z"))
            // I-1：options 不再返回专用模板目录/称呼/签名
            .andExpect(jsonPath("$.templates").doesNotExist())
            .andExpect(jsonPath("$.expertSalutation").doesNotExist())
            .andExpect(jsonPath("$.senderSignature").doesNotExist())
    }

    @Test
    fun `options forwards requested account code and passes processing id`() {
        stubAuth()
        Mockito.`when`(meetingConfirmationService.options(7L, 1L, "acc-2"))
            .thenReturn(sampleOptions())

        mockMvc.perform(
            get("/api/mail/unmatched-inbound/7/meeting-confirmation/options")
                .session(sessionOf())
                .param("contactId", "1")
                .param("senderAccountCode", "acc-2")
        )
            .andExpect(status().isOk)
        Mockito.verify(meetingConfirmationService).options(7L, 1L, "acc-2")
    }

    @Test
    fun `missing contactId is a 400 bad request`() {
        stubAuth()
        mockMvc.perform(
            get("/api/mail/unmatched-inbound/7/meeting-confirmation/options")
                .session(sessionOf())
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `time-zones list requires iso date param`() {
        stubAuth()
        mockMvc.perform(
            get("/api/mail/meeting-confirmation/time-zones").session(sessionOf()).param("date", "2026-09-11")
        )
            .andExpect(status().isOk)
        Mockito.verify(meetingConfirmationService)
            .timeZones(java.time.LocalDate.of(2026, 9, 11))

        // date 必填
        mockMvc.perform(get("/api/mail/meeting-confirmation/time-zones").session(sessionOf()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        Mockito.verifyNoMoreInteractions(meetingConfirmationService)
    }

    @Test
    fun `time-zones returns catalog json shape`() {
        stubAuth()
        Mockito.`when`(meetingConfirmationService.timeZones(java.time.LocalDate.of(2026, 9, 11)))
            .thenReturn(sampleZones())

        mockMvc.perform(
            get("/api/mail/meeting-confirmation/time-zones").session(sessionOf()).param("date", "2026-09-11")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("Europe/Istanbul"))
            .andExpect(jsonPath("$[0].labelZh").value("土耳其 · 伊斯坦布尔"))
            .andExpect(jsonPath("$[0].aliases[3]").value("Türkiye"))
            .andExpect(jsonPath("$[0].offsetLabel").value("UTC+3"))
            .andExpect(jsonPath("$[0].offsetSeconds").value(10800))
            .andExpect(jsonPath("$[1].offsetLabel").value("UTC+5:30"))
    }

    @Test
    fun `preview accepts the minimal meeting json without legacy fields`() {
        stubAuth()
        var captured: MeetingPreviewRequest? = null
        Mockito.`when`(
            meetingConfirmationService.preview(
                Mockito.eq(7L),
                anyValue(MeetingPreviewRequest(contactId = 1, meeting = samplePreview().meeting))
            )
        ).thenAnswer { invocation ->
            captured = invocation.getArgument<MeetingPreviewRequest>(1)
            samplePreview()
        }

        mockMvc.perform(
            post("/api/mail/unmatched-inbound/7/meeting-confirmation/preview")
                .session(sessionOf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"contactId":1,"meeting":{"zoneId":"Europe/Istanbul",""" +
                        """"startLocal":"2026-09-11T10:00","endLocal":"2026-09-11T10:30",""" +
                        """"zoomUrl":"https://zoom.us/j/1","generatedAt":"2026-09-09T03:00:40Z"}}"""
                )
        )
            .andExpect(status().isOk)

        val request = requireNotNull(captured)
        val meeting = request.meeting
        assertEquals(1L, request.contactId)
        assertEquals("Europe/Istanbul", meeting.zoneId)
        assertEquals("2026-09-11T10:00", meeting.startLocal)
        assertEquals("2026-09-11T10:30", meeting.endLocal)
        assertEquals("https://zoom.us/j/1", meeting.zoomUrl)
        assertEquals("2026-09-09T03:00:40Z", meeting.generatedAt)
        // 兼容字段走默认值（新请求不必发送）
        assertEquals(0L, meeting.templateId)
        assertEquals("", meeting.templateBody)
        assertEquals("", meeting.expertSalutation)
        assertEquals("", meeting.senderSignature)
    }

    @Test
    fun `preview posts json and returns full preview contract`() {
        stubAuth()
        Mockito.`when`(
            meetingConfirmationService.preview(
                Mockito.eq(7L),
                anyValue(MeetingPreviewRequest(contactId = 1, meeting = samplePreview().meeting))
            )
        ).thenReturn(samplePreview())

        mockMvc.perform(
            post("/api/mail/unmatched-inbound/7/meeting-confirmation/preview")
                .session(sessionOf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"contactId":1,"senderAccountCode":null,"meeting":{""" +
                        """"templateId":100,"templateBody":"Dear {{expert_salutation}}",""" +
                        """"expertSalutation":"Professor Basdogan","zoneId":"Europe/Istanbul",""" +
                        """"startLocal":"2026-09-11T10:00","endLocal":"2026-09-11T10:30",""" +
                        """"zoomUrl":"https://zoom.us/j/1","senderSignature":"LuKai",""" +
                        """"generatedAt":"2026-09-09T03:00:40Z"}}"""
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetKey").value("1:acc-1"))
            .andExpect(jsonPath("$.meeting.zoneId").value("Europe/Istanbul"))
            .andExpect(jsonPath("$.meetingTime").value(
                "Friday, September 11, 2026, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)"
            ))
            .andExpect(jsonPath("$.startUtc").value("2026-09-11T07:00:00Z"))
            .andExpect(jsonPath("$.durationMinutes").value(30))
            .andExpect(jsonPath("$.attachment.filename").value("meeting-2026-09-11-Professor-Basdogan.ics"))
            .andExpect(jsonPath("$.attachment.byteLength").value(40))
            .andExpect(jsonPath("$.attachment.sha256").value("a".repeat(64)))
    }

    @Test
    fun `preview with malformed body is a 400`() {
        stubAuth()
        mockMvc.perform(
            post("/api/mail/unmatched-inbound/7/meeting-confirmation/preview")
                .session(sessionOf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    // ── 错误映射：NoSuchElementException → 404；IllegalArgumentException → 400 ──

    @Test
    fun `service not found maps to 404`() {
        stubAuth()
        Mockito.`when`(meetingConfirmationService.options(7L, 1L, null))
            .thenThrow(NoSuchElementException("Inbound mail processing not found: 7"))

        mockMvc.perform(
            get("/api/mail/unmatched-inbound/7/meeting-confirmation/options")
                .session(sessionOf())
                .param("contactId", "1")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Inbound mail processing not found: 7"))
    }

    @Test
    fun `service validation error maps to 400 with fixed message`() {
        stubAuth()
        Mockito.`when`(
            meetingConfirmationService.preview(
                Mockito.eq(7L),
                anyValue(MeetingPreviewRequest(contactId = 1, meeting = samplePreview().meeting))
            )
        ).thenThrow(IllegalArgumentException("该当地时间出现两次，请选择不处于夏令时回拨区间的时间"))

        mockMvc.perform(
            post("/api/mail/unmatched-inbound/7/meeting-confirmation/preview")
                .session(sessionOf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"contactId":1,"meeting":{"templateId":100,"templateBody":"body",""" +
                        """"expertSalutation":"n","zoneId":"America/New_York",""" +
                        """"startLocal":"2026-11-01T01:30","endLocal":"2026-11-01T02:30",""" +
                        """"zoomUrl":"https://zoom.us/j/1","senderSignature":"s","generatedAt":"2026-09-09T03:00:40Z"}}"""
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("该当地时间出现两次，请选择不处于夏令时回拨区间的时间"))
    }
}
