package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.mail.repository.ExpertInboundNotificationSettingsResponse
import com.weibo.talentintroduction.mail.service.ExpertInboundNotificationNotConfiguredException
import com.weibo.talentintroduction.mail.service.ExpertInboundNotificationService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(ExpertInboundNotificationController::class, properties = ["talent-introduction.auth.enabled=true"])
@Import(AuthWebConfig::class)
class ExpertInboundNotificationControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockBean lateinit var service: ExpertInboundNotificationService
    @MockBean lateinit var auth: AuthService
    private val endpoint = "/api/expert-inbound-notifications/settings"
    private fun session() = MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, "operator") }
    @BeforeEach fun setup() {
        Mockito.`when`(auth.findUser("operator")).thenReturn(AdminUser(id = 1, username = "operator", passwordHash = "hash", mustChangePassword = false, createdAt = java.time.LocalDateTime.now(), updatedAt = java.time.LocalDateTime.now()))
        Mockito.`when`(service.settings()).thenReturn(ExpertInboundNotificationSettingsResponse(false, true, 0, null))
        Mockito.`when`(service.setEnabled(true, "operator")).thenReturn(ExpertInboundNotificationSettingsResponse(true, true, 1, null))
    }

    @Test fun `GET returns exact downstream response default disabled and no-store`() {
        mvc.perform(get(endpoint).session(session())).andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().json("""{"enabled":false,"configured":true,"generation":0,"updatedAt":null}""", true))
        Mockito.verify(service).settings()
        Mockito.verify(service, Mockito.never()).setEnabled(Mockito.anyBoolean(), Mockito.anyString())
    }

    @Test fun `PUT uses strict boolean and session identity instead of caller supplied identity`() {
        mvc.perform(put(endpoint).session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"enabled":true,"updatedBy":"forged"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.generation").value(1))
        Mockito.verify(service).setEnabled(true, "operator")
    }

    @Test fun `invalid JSON booleans are rejected rather than coerced`() {
        listOf("""{"enabled":"true"}""", """{"enabled":1}""", """{"enabled":null}""", "{}", "true", "[]", "{" ).forEach {
            mvc.perform(put(endpoint).session(session()).contentType(MediaType.APPLICATION_JSON).content(it))
                .andExpect(status().isBadRequest)
        }
        Mockito.verify(service, Mockito.never()).setEnabled(Mockito.anyBoolean(), Mockito.anyString())
    }

    @Test fun `anonymous GET and PUT are denied by existing session boundary`() {
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized)
        mvc.perform(put(endpoint).contentType(MediaType.APPLICATION_JSON).content("""{"enabled":true}"""))
            .andExpect(status().isUnauthorized)
        Mockito.verifyNoInteractions(service)
    }

    @Test fun `forced password change blocks settings without touching the service`() {
        Mockito.`when`(auth.findUser("operator")).thenReturn(AdminUser(id = 1, username = "operator", passwordHash = "hash", mustChangePassword = true, createdAt = java.time.LocalDateTime.now(), updatedAt = java.time.LocalDateTime.now()))
        mvc.perform(put(endpoint).session(session()).contentType(MediaType.APPLICATION_JSON).content("""{"enabled":true}"""))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"))
        Mockito.verifyNoInteractions(service)
    }

    @Test fun `unconfigured enable is 409 and disable remains available`() {
        Mockito.`when`(service.setEnabled(true, "operator")).thenThrow(ExpertInboundNotificationNotConfiguredException())
        Mockito.`when`(service.setEnabled(false, "operator")).thenReturn(ExpertInboundNotificationSettingsResponse(false, false, 0, null))
        mvc.perform(put(endpoint).session(session()).contentType(MediaType.APPLICATION_JSON).content("""{"enabled":true}"""))
            .andExpect(status().isConflict).andExpect(jsonPath("$.message").value("未配置企业微信机器人"))
        mvc.perform(put(endpoint).session(session()).contentType(MediaType.APPLICATION_JSON).content("""{"enabled":false}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.configured").value(false))
    }

    @Test fun `database errors return an error not fabricated disabled settings and are sanitized`() {
        val secret = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=should-never-appear"
        Mockito.`when`(service.settings()).thenThrow(IllegalStateException(secret))
        val response = mvc.perform(get(endpoint).session(session())).andExpect(status().isInternalServerError).andReturn().response.getContentAsString(Charsets.UTF_8)
        assertFalse(response.contains(secret))
        assertFalse(response.contains("enabled"))
        assertTrue(response.contains("群消息设置暂不可用"))
    }
}
