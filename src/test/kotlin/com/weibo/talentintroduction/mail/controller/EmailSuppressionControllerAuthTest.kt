package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(EmailSuppressionController::class)
@Import(AuthWebConfig::class, ObjectMapper::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class EmailSuppressionControllerAuthTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: EmailSuppressionService

    @MockBean
    private lateinit var authService: AuthService

    @Test
    fun `unauthenticated GET is rejected by AuthInterceptor`() {
        mockMvc.perform(get("/api/suppressions"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `unauthenticated DELETE is rejected by AuthInterceptor`() {
        mockMvc.perform(delete("/api/suppressions").param("email", "a@x.com"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }
}
