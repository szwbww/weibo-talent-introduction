package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.config.UnsubscribeProperties
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.service.SuppressionSource
import com.weibo.talentintroduction.mail.service.UnsubscribePageRenderer
import com.weibo.talentintroduction.mail.service.UnsubscribeTokenService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(UnsubscribeController::class)
class UnsubscribeControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var tokenService: UnsubscribeTokenService

    @MockBean
    private lateinit var suppressionService: EmailSuppressionService

    @TestConfiguration
    class RendererConfig {
        @Bean
        fun unsubscribePageRenderer(): UnsubscribePageRenderer =
            UnsubscribePageRenderer(UnsubscribeProperties())
    }

    private fun eqValue(value: String): String = eq(value) ?: value

    private fun <T> eqValue(value: T): T = eq(value) ?: value

    @Test
    fun `POST valid token unsubscribes without auth`() {
        doReturn("user@example.com").`when`(tokenService).verify("good-token")

        mockMvc.perform(post("/u/unsubscribe").param("token", "good-token"))
            .andExpect(status().isOk)
            .andExpect(content().string("unsubscribed"))

        verify(suppressionService).suppress(
            eqValue("user@example.com"),
            eqValue(SuppressionSource.ONE_CLICK),
            eqValue("one-click unsubscribe")
        )
    }

    @Test
    fun `POST invalid token returns 400 and does not suppress`() {
        doReturn(null).`when`(tokenService).verify("bad-token")

        mockMvc.perform(post("/u/unsubscribe").param("token", "bad-token"))
            .andExpect(status().isBadRequest)
            .andExpect(content().string("invalid"))

        verifyNoInteractions(suppressionService)
    }

    @Test
    fun `GET valid token returns confirm html with context-path-safe action`() {
        doReturn("user@example.com").`when`(tokenService).verify("good-token")

        mockMvc.perform(get("/u/unsubscribe").param("token", "good-token"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"unsubscribe/confirm\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("good-token")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"qf-btn qf-btn-primary\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirm unsubscribe")))

        verifyNoInteractions(suppressionService)
    }

    @Test
    fun `GET invalid token returns 400`() {
        doReturn(null).`when`(tokenService).verify("bad-token")

        mockMvc.perform(get("/u/unsubscribe").param("token", "bad-token"))
            .andExpect(status().isBadRequest)
            .andExpect(content().string("invalid link"))
    }

    @Test
    fun `POST confirm valid token unsubscribes`() {
        doReturn("user@example.com").`when`(tokenService).verify("good-token")

        mockMvc.perform(post("/u/unsubscribe/confirm").param("token", "good-token"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("You&#39;ve been unsubscribed")))

        verify(suppressionService).suppress(
            eqValue("user@example.com"),
            eqValue(SuppressionSource.ONE_CLICK),
            eqValue("web confirm unsubscribe")
        )
    }
}
