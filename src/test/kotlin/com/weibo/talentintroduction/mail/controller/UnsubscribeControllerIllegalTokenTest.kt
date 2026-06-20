package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.config.UnsubscribeProperties
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.service.UnsubscribeTokenService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(UnsubscribeController::class)
@Import(UnsubscribeControllerIllegalTokenTest.TokenTestConfig::class)
class UnsubscribeControllerIllegalTokenTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var suppressionService: EmailSuppressionService

    @Test
    fun `POST illegal base64 token returns 400 without suppress`() {
        mockMvc.perform(post("/u/unsubscribe").param("token", "%%%.x"))
            .andExpect(status().isBadRequest)
            .andExpect(content().string("invalid"))

        verifyNoInteractions(suppressionService)
    }

    @Test
    fun `GET illegal base64 token returns 400 without suppress`() {
        mockMvc.perform(get("/u/unsubscribe").param("token", "%%%.x"))
            .andExpect(status().isBadRequest)
            .andExpect(content().string("invalid link"))

        verifyNoInteractions(suppressionService)
    }

    @Test
    fun `POST confirm illegal base64 token returns 400 without suppress`() {
        mockMvc.perform(post("/u/unsubscribe/confirm").param("token", "%%%.x"))
            .andExpect(status().isBadRequest)
            .andExpect(content().string("invalid"))

        verifyNoInteractions(suppressionService)
    }

    @TestConfiguration
    class TokenTestConfig {
        @Bean
        fun unsubscribeTokenService(): UnsubscribeTokenService =
            UnsubscribeTokenService(
                UnsubscribeProperties(
                    baseUrl = "https://outreach.example.com",
                    secret = "test-secret-key"
                )
            )
    }
}
