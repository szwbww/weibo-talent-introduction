package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.service.SuppressionPage
import com.weibo.talentintroduction.mail.service.SuppressionSource
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(EmailSuppressionController::class)
class EmailSuppressionControllerMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: EmailSuppressionService

    @Test
    fun `POST add delegates to service with MANUAL source and normalized email`() {
        Mockito.`when`(
            service.suppress("  A@X.com ", SuppressionSource.MANUAL, "blocked by admin")
        ).thenReturn(true)

        mockMvc.perform(
            post("/api/suppressions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"  A@X.com ","reason":"blocked by admin"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.added").value(true))

        Mockito.verify(service).suppress("  A@X.com ", SuppressionSource.MANUAL, "blocked by admin")
    }

    @Test
    fun `POST add uses default reason when omitted`() {
        Mockito.`when`(
            service.suppress("a@x.com", SuppressionSource.MANUAL, "manual add")
        ).thenReturn(false)

        mockMvc.perform(
            post("/api/suppressions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"a@x.com"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.added").value(false))
    }

    @Test
    fun `DELETE remove is idempotent when email missing`() {
        Mockito.`when`(service.remove("missing@x.com")).thenReturn(false)

        mockMvc.perform(delete("/api/suppressions").param("email", "missing@x.com"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.removed").value(false))
    }

    @Test
    fun `DELETE remove returns removed true when deleted`() {
        Mockito.`when`(service.remove("a@x.com")).thenReturn(true)

        mockMvc.perform(delete("/api/suppressions").param("email", "a@x.com"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.removed").value(true))
    }

    @Test
    fun `GET list delegates pagination and keyword to service`() {
        Mockito.`when`(service.list("test", 1, 20)).thenReturn(
            SuppressionPage(items = emptyList(), page = 1, size = 20, total = 0)
        )

        mockMvc.perform(
            get("/api/suppressions")
                .param("keyword", "test")
                .param("page", "1")
                .param("size", "20")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.total").value(0))

        Mockito.verify(service).list("test", 1, 20)
    }

    @Test
    fun `GET list uses defaults when params omitted`() {
        Mockito.`when`(service.list(isNull(), eq(0), eq(50))).thenReturn(
            SuppressionPage(items = emptyList(), page = 0, size = 50, total = 0)
        )

        mockMvc.perform(get("/api/suppressions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(50))
    }
}
