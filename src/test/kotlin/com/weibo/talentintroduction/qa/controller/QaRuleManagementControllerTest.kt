package com.weibo.talentintroduction.qa.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.service.QaRuleAuditService
import com.weibo.talentintroduction.qa.service.QaRuleCreateCommand
import com.weibo.talentintroduction.qa.service.QaRuleDetail
import com.weibo.talentintroduction.qa.service.QaRuleManagementService
import com.weibo.talentintroduction.qa.service.QaRuleUpdateCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(QaRuleManagementController::class)
@Import(GlobalExceptionHandler::class)
class QaRuleManagementControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var service: QaRuleManagementService

    @MockBean
    private lateinit var qaRuleAuditService: QaRuleAuditService

    @MockBean
    private lateinit var mailVariableService: MailVariableService

    @MockBean
    private lateinit var expertContactRepository: ExpertContactRepository

    @MockBean
    private lateinit var expertSearchService: ExpertSearchService

    @Test
    fun `create rule returns answerBody and legacy replyBody`() {
        val factBody = "The application does not charge experts a service fee."
        Mockito.doReturn(
            QaRuleDetail(
                rule = QaRule(
                    id = 10L,
                    categoryId = 1L,
                    keywords = "fee",
                    replySubject = null,
                    replyBody = factBody,
                    answerBody = factBody,
                    replyPolicy = QaReplyPolicy.REVIEW.name,
                    autoReplyEnabled = false,
                    handoffRequired = true,
                    enabled = true
                )
            )
        ).`when`(service).createRule(any())

        mockMvc.perform(
            post("/api/qa/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": 1,
                      "keywords": "fee",
                      "matchMode": "ANY",
                      "priority": 100,
                      "answerBody": "$factBody",
                      "replyPolicy": "REVIEW",
                      "displayName": "Fee fact",
                      "enabled": true
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answerBody").value(factBody))
            .andExpect(jsonPath("$.replyBody").value(factBody))
            .andExpect(jsonPath("$.replyPolicy").value("REVIEW"))
            .andExpect(jsonPath("$.autoReplyEnabled").value(false))
            .andExpect(jsonPath("$.handoffRequired").value(true))
    }

    @Test
    fun `create rule rejects missing replyPolicy with 400`() {
        mockMvc.perform(
            post("/api/qa/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": 1,
                      "keywords": "fee",
                      "matchMode": "ANY",
                      "priority": 100,
                      "answerBody": "No service fee.",
                      "displayName": "Fee fact",
                      "enabled": true
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)

        Mockito.verify(service, Mockito.never()).createRule(any())
    }

    @Test
    fun `create rule rejects missing answerBody with 400`() {
        mockMvc.perform(
            post("/api/qa/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": 1,
                      "keywords": "fee",
                      "matchMode": "ANY",
                      "priority": 100,
                      "replyBody": "Legacy body only",
                      "displayName": "Fee fact",
                      "enabled": true
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)

        Mockito.verify(service, Mockito.never()).createRule(any())
    }

    @Test
    fun `create rule rejects blank answerBody with 400`() {
        Mockito.doThrow(IllegalArgumentException("answerBody is required"))
            .`when`(service).createRule(any())

        mockMvc.perform(
            post("/api/qa/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": 1,
                      "keywords": "fee",
                      "matchMode": "ANY",
                      "priority": 100,
                      "answerBody": "   ",
                      "replyPolicy": "REVIEW",
                      "displayName": "Fee fact",
                      "enabled": true
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("answerBody is required"))
    }

    @Test
    fun `create rule rejects salutation in answerBody with 400`() {
        Mockito.doThrow(IllegalArgumentException("answerBody must not start with a salutation (Dear/Hi/Hello)"))
            .`when`(service).createRule(any())

        mockMvc.perform(
            post("/api/qa/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": 1,
                      "keywords": "identity",
                      "matchMode": "ANY",
                      "priority": 100,
                      "answerBody": "Dear Professor, verified identity details.",
                      "replyPolicy": "REVIEW",
                      "displayName": "Identity",
                      "enabled": true
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("answerBody must not start with a salutation (Dear/Hi/Hello)"))
    }

    @Test
    fun `create accepts null legacy boolean fields and uses replyPolicy`() {
        val factBody = "The application does not charge experts a service fee."
        Mockito.doAnswer { invocation ->
            val command = invocation.arguments[0] as QaRuleCreateCommand
            assertEquals(QaReplyPolicy.AUTO.name, command.replyPolicy)
            QaRuleDetail(
                rule = QaRule(
                    id = 10L,
                    categoryId = 1L,
                    keywords = "fee",
                    replySubject = null,
                    replyBody = factBody,
                    answerBody = factBody,
                    replyPolicy = QaReplyPolicy.AUTO.name,
                    autoReplyEnabled = true,
                    handoffRequired = false,
                    enabled = true
                )
            )
        }.`when`(service).createRule(any())

        mockMvc.perform(
            post("/api/qa/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": 1,
                      "keywords": "fee",
                      "matchMode": "ANY",
                      "priority": 100,
                      "answerBody": "$factBody",
                      "replyPolicy": "AUTO",
                      "autoReplyEnabled": null,
                      "handoffRequired": null,
                      "displayName": "Fee fact",
                      "enabled": true
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replyPolicy").value("AUTO"))
    }

    @Test
    fun `update accepts null legacy boolean fields and uses replyPolicy`() {
        Mockito.doAnswer { invocation ->
            val command = invocation.arguments[1] as QaRuleUpdateCommand
            assertEquals(QaReplyPolicy.REVIEW.name, command.replyPolicy)
            QaRuleDetail(
                rule = QaRule(
                    id = 10L,
                    categoryId = 1L,
                    keywords = "fee",
                    replySubject = null,
                    replyBody = "Updated body",
                    answerBody = "Updated body",
                    replyPolicy = QaReplyPolicy.REVIEW.name,
                    autoReplyEnabled = false,
                    handoffRequired = true,
                    enabled = true
                )
            )
        }.`when`(service).updateRule(Mockito.eq(10L), any())

        mockMvc.perform(
            put("/api/qa/rules/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": 1,
                      "keywords": "fee",
                      "matchMode": "ANY",
                      "priority": 100,
                      "answerBody": "Updated body",
                      "replyPolicy": "REVIEW",
                      "autoReplyEnabled": null,
                      "handoffRequired": null,
                      "displayName": "Fee fact",
                      "enabled": true
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replyPolicy").value("REVIEW"))
    }

    @Test
    fun `update rule rejects qa variants with 400`() {
        Mockito.doThrow(IllegalArgumentException("QA rule content variants are no longer supported"))
            .`when`(service).updateRule(Mockito.eq(10L), any())

        mockMvc.perform(
            put("/api/qa/rules/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "categoryId": 1,
                      "keywords": "fee",
                      "matchMode": "ANY",
                      "priority": 100,
                      "answerBody": "The application does not charge experts a service fee.",
                      "replyPolicy": "REVIEW",
                      "displayName": "Fee fact",
                      "enabled": true,
                      "variants": ["Variant A"]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("QA rule content variants are no longer supported"))
    }

    private inline fun <reified T> any(): T = Mockito.any(T::class.java)
}
