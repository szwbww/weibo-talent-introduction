package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.mail.service.ExpertContactNoteService
import com.weibo.talentintroduction.mail.service.ExpertContactNoteView
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.LocalDateTime

@WebMvcTest(controllers = [ExpertContactNoteController::class])
@Import(AuthWebConfig::class, ExpertContactNoteControllerTest.KotlinObjectMapperConfig::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class ExpertContactNoteControllerTest {
    @TestConfiguration
    class KotlinObjectMapperConfig {
        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    @Autowired private lateinit var mvc: MockMvc
    @MockBean private lateinit var service: ExpertContactNoteService
    @MockBean private lateinit var authService: AuthService

    private fun session(username: String = "admin", mustChangePassword: Boolean = false): MockHttpSession {
        Mockito.`when`(authService.findUser(username)).thenReturn(
            AdminUser(id = 1, username = username, passwordHash = "hash",
                mustChangePassword = mustChangePassword, createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now())
        )
        return MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }
    }

    @Test
    fun `GET and clear PUT return exactly empty state fields`() {
        val empty = ExpertContactNoteView(42, "", null, null)
        Mockito.`when`(service.get(42)).thenReturn(empty)
        Mockito.`when`(service.save("admin", 42, "")).thenReturn(empty)
        val expected = """{"contactId":42,"note":"","updatedBy":null,"updatedAt":null}"""
        mvc.perform(get("/api/mail/contact-notes/42").session(session()))
            .andExpect(status().isOk).andExpect(content().json(expected, true))
        mvc.perform(put("/api/mail/contact-notes/42").session(session())
            .contentType(MediaType.APPLICATION_JSON).content("""{"note":""}"""))
            .andExpect(status().isOk).andExpect(content().json(expected, true))
        Mockito.verify(service).get(42)
        Mockito.verify(service).save("admin", 42, "")
    }

    @Test
    fun `PUT body identity and time cannot override session identity or persisted response`() {
        Mockito.`when`(service.save("userA", 42, "中文\n第二行")).thenReturn(
            ExpertContactNoteView(42, "中文\n第二行", "userA", "2026-10-08T10:18:00.123+08:00")
        )
        mvc.perform(put("/api/mail/contact-notes/42").session(session("userA"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"note":"中文\n第二行","username":"root","operatorName":"root","updatedAt":"fake"}"""))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"contactId":42,"note":"中文\n第二行","updatedBy":"userA","updatedAt":"2026-10-08T10:18:00.123+08:00"}""", true))
        Mockito.verify(service).save("userA", 42, "中文\n第二行")
    }

    @Test
    fun `missing body missing note and null note are bad requests without service calls`() {
        listOf("", "{}", """{"note":null}""").forEach { body ->
            mvc.perform(put("/api/mail/contact-notes/42").session(session())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest)
        }
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun `invalid ids oversized text and unknown contacts map to 400 and 404`() {
        listOf(0L, -1L).forEach { id ->
            Mockito.`when`(service.get(id)).thenThrow(IllegalArgumentException("联系人ID必须为正数"))
            Mockito.`when`(service.save("admin", id, "x")).thenThrow(IllegalArgumentException("联系人ID必须为正数"))
            mvc.perform(get("/api/mail/contact-notes/$id").session(session())).andExpect(status().isBadRequest)
            mvc.perform(put("/api/mail/contact-notes/$id").session(session())
                .contentType(MediaType.APPLICATION_JSON).content("""{"note":"x"}""")).andExpect(status().isBadRequest)
        }
        val oversized = "中".repeat(2001)
        Mockito.`when`(service.save("admin", 42, oversized)).thenThrow(IllegalArgumentException("备注不能超过2000个字符"))
        mvc.perform(put("/api/mail/contact-notes/42").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content(ObjectMapper().writeValueAsString(mapOf("note" to oversized)))).andExpect(status().isBadRequest)
        Mockito.`when`(service.get(404)).thenThrow(NoSuchElementException("联系人不存在"))
        Mockito.`when`(service.save("admin", 404, "x")).thenThrow(NoSuchElementException("联系人不存在"))
        mvc.perform(get("/api/mail/contact-notes/404").session(session())).andExpect(status().isNotFound)
        mvc.perform(put("/api/mail/contact-notes/404").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"note":"x"}""")).andExpect(status().isNotFound)
    }

    @Test
    fun `no session expired session and forced password change preserve auth rules for both routes`() {
        val expired = MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, "deleted-user") }
        for (route in listOf(get("/api/mail/contact-notes/42"), put("/api/mail/contact-notes/42")
            .contentType(MediaType.APPLICATION_JSON).content("""{"note":"x"}"""))) {
            mvc.perform(route).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        }
        for (login in listOf(expired, session("password-user", true))) {
            val expected = if (login === expired) 401 else 403
            mvc.perform(get("/api/mail/contact-notes/42").session(login)).andExpect(status().`is`(expected))
            mvc.perform(put("/api/mail/contact-notes/42").session(login).contentType(MediaType.APPLICATION_JSON)
                .content("""{"note":"x"}""")).andExpect(status().`is`(expected))
        }
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun `controller independently refuses absent and blank session usernames`() {
        val controller = ExpertContactNoteController(service)
        listOf<String?>(null, " ").forEach { username ->
            val request = MockHttpServletRequest()
            if (username != null) request.getSession().setAttribute(AuthSessionKeys.USERNAME, username)
            assertEquals(401, controller.get(request, 42).statusCodeValue)
            assertEquals(401, controller.save(request, 42, SaveExpertContactNoteRequest("x")).statusCodeValue)
        }
        Mockito.verifyNoInteractions(service)
    }
}
