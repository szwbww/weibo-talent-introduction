package com.weibo.talentintroduction.template.controller

import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.NoSuchElementException

@WebMvcTest(MailComposeTemplateController::class)
@Import(GlobalExceptionHandler::class)
class ComposeTemplateGateControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: MailComposeTemplateService

    @Test
    fun `gate-fields returns effectiveRequiredKeys and requiredEsFields verbatim`() {
        Mockito.`when`(service.effectiveRequiredKeys(7L))
            .thenReturn(listOf("recentWorkTitle", "primaryResearchField"))
        Mockito.`when`(service.requiredEsFields(7L))
            .thenReturn(listOf("recentWorkTitles", "researchFields"))

        mockMvc.perform(get("/api/compose-templates/7/gate-fields"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.templateId").value(7))
            .andExpect(jsonPath("$.requiredKeys[0]").value("recentWorkTitle"))
            .andExpect(jsonPath("$.requiredKeys[1]").value("primaryResearchField"))
            .andExpect(jsonPath("$.esFields[0]").value("recentWorkTitles"))
            .andExpect(jsonPath("$.esFields[1]").value("researchFields"))

        Mockito.verify(service).effectiveRequiredKeys(7L)
        Mockito.verify(service).requiredEsFields(7L)
    }

    @Test
    fun `gate-fields passes through empty esFields without inventing fields`() {
        Mockito.`when`(service.effectiveRequiredKeys(3L)).thenReturn(listOf("senderName"))
        Mockito.`when`(service.requiredEsFields(3L)).thenReturn(emptyList())

        mockMvc.perform(get("/api/compose-templates/3/gate-fields"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.templateId").value(3))
            .andExpect(jsonPath("$.requiredKeys[0]").value("senderName"))
            .andExpect(jsonPath("$.esFields").isEmpty())
    }

    @Test
    fun `gate-fields maps unknown template to 404 via service exception`() {
        Mockito.`when`(service.effectiveRequiredKeys(99L))
            .thenThrow(NoSuchElementException("Template not found: 99"))

        mockMvc.perform(get("/api/compose-templates/99/gate-fields"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }
}
