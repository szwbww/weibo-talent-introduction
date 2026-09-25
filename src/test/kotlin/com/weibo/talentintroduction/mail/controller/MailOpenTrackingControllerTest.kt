package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.mail.service.MailOpenTrackingService
import com.weibo.talentintroduction.mail.service.MailOpenTrackingSettings
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import javax.imageio.ImageIO

@WebMvcTest(controllers = [MailOpenTrackingController::class])
@Import(AuthWebConfig::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class MailOpenTrackingControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockBean lateinit var service: MailOpenTrackingService
    @MockBean lateinit var authService: AuthService

    private fun session(): MockHttpSession {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")
        `when`(authService.findUser("admin")).thenReturn(AdminUser(
            id=1, username="admin", passwordHash="hash", mustChangePassword=false,
            createdAt=LocalDateTime.now(), updatedAt=LocalDateTime.now()
        ))
        return session
    }

    @Test fun `management is authenticated and PUT requires actual boolean`() {
        mvc.perform(get("/api/mail-open-tracking/settings")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/mail-open-tracking/records").param("from","2026-09-25").param("to","2026-09-25"))
            .andExpect(status().isUnauthorized)
        mvc.perform(get("/api/mail-open-tracking/records/1")).andExpect(status().isUnauthorized)
        mvc.perform(put("/api/mail-open-tracking/settings").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
            .andExpect(status().isUnauthorized)
        `when`(service.settings()).thenReturn(MailOpenTrackingSettings(false,false,""))
        mvc.perform(get("/api/mail-open-tracking/settings").session(session()))
            .andExpect(status().isOk).andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.configured").value(false))
        for (json in listOf("{}", "{\"enabled\":null}", "{\"enabled\":\"true\"}")) {
            mvc.perform(put("/api/mail-open-tracking/settings").session(session())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest)
        }
        `when`(service.setEnabled(true)).thenReturn(MailOpenTrackingSettings(true,true,"https://example.test/talent"))
        mvc.perform(put("/api/mail-open-tracking/settings").session(session())
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.baseUrl").value("https://example.test/talent"))
        verify(service).setEnabled(true)
    }

    @Test fun `configured URL is required for enabled PUT but disabling remains allowed`() {
        `when`(service.setEnabled(true)).thenThrow(IllegalArgumentException("A valid HTTPS base URL is required"))
        mvc.perform(put("/api/mail-open-tracking/settings").session(session())
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
            .andExpect(status().isBadRequest)
        `when`(service.setEnabled(false)).thenReturn(MailOpenTrackingSettings(false,false,""))
        mvc.perform(put("/api/mail-open-tracking/settings").session(session())
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.enabled").value(false))
    }

    @Test fun `public GET sends decodable fixed GIF while HEAD has no signal or body`() {
        val token = "A".repeat(43)
        val get = mvc.perform(get("/t/mail-open/$token.gif")).andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store,no-cache,must-revalidate,max-age=0"))
            .andExpect(content().contentType(MediaType.IMAGE_GIF)).andReturn().response
        val image = ImageIO.read(ByteArrayInputStream(get.contentAsByteArray))
        assertNotNull(image)
        assertEquals(1, image.width)
        assertEquals(1, image.height)
        assertEquals(0, image.getRGB(0, 0) ushr 24)
        assertNull(get.getHeader("Set-Cookie"))
        assertNull(get.getHeader("ETag"))
        val head = mvc.perform(head("/t/mail-open/$token.gif")).andExpect(status().isOk)
            .andExpect(content().bytes(byteArrayOf())).andReturn().response
        assertEquals(get.getHeader("Content-Length"), head.getHeader("Content-Length"))
        verify(service, times(1)).recordSignal(token)
    }
}
