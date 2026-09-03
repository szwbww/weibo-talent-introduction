package com.weibo.talentintroduction.qa

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.qa.controller.QaRuleManagementController
import com.weibo.talentintroduction.qa.domain.QaCategory
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.service.QaRuleAuditService
import com.weibo.talentintroduction.qa.service.QaRuleManagementService
import com.weibo.talentintroduction.qa.service.QaRuleWithCategory
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 07 (c8) 回归：qa_rule / qa_category 停写（I-35 / I-36 / D-9）。
 *
 * 七个写端点必须返回 HTTP 403，body code 为 QA_RULE_READ_ONLY（不是 404、不是 500）；
 * `GET /api/qa/rules` 只读端点必须照常 200 且数据非空（I-36）。
 * docker-free：全部 MockBean，无 DB。
 */
@WebMvcTest(QaRuleManagementController::class)
@Import(GlobalExceptionHandler::class)
class QaRuleReadOnlyTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

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

    private val factBody = "The application does not charge experts a service fee."

    private fun rulePayload() =
        """
        {
          "categoryId": 1,
          "keywords": "fee,cost",
          "matchMode": "ANY",
          "priority": 100,
          "answerBody": "$factBody",
          "replyPolicy": "REVIEW",
          "displayName": "Fee fact",
          "enabled": true
        }
        """.trimIndent()

    @Test
    fun `POST categories is read-only 403 QA_RULE_READ_ONLY`() {
        mockMvc.perform(
            post("/api/qa/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"categoryCode":"FIN2","categoryName":"Finance 2"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("QA_RULE_READ_ONLY"))
    }

    @Test
    fun `POST categories enable is read-only 403 QA_RULE_READ_ONLY`() {
        mockMvc.perform(post("/api/qa/categories/FIN2/enable"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("QA_RULE_READ_ONLY"))
    }

    @Test
    fun `POST categories disable is read-only 403 QA_RULE_READ_ONLY`() {
        mockMvc.perform(post("/api/qa/categories/FIN2/disable"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("QA_RULE_READ_ONLY"))
    }

    @Test
    fun `POST rules is read-only 403 QA_RULE_READ_ONLY`() {
        mockMvc.perform(
            post("/api/qa/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rulePayload())
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("QA_RULE_READ_ONLY"))
    }

    @Test
    fun `PUT rules is read-only 403 QA_RULE_READ_ONLY`() {
        mockMvc.perform(
            put("/api/qa/rules/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rulePayload())
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("QA_RULE_READ_ONLY"))
    }

    @Test
    fun `POST rules enable is read-only 403 QA_RULE_READ_ONLY`() {
        mockMvc.perform(post("/api/qa/rules/10/enable"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("QA_RULE_READ_ONLY"))
    }

    @Test
    fun `POST rules disable is read-only 403 QA_RULE_READ_ONLY`() {
        mockMvc.perform(post("/api/qa/rules/10/disable"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("QA_RULE_READ_ONLY"))
    }

    @Test
    fun `GET rules stays readable and returns non-empty list`() {
        Mockito.doReturn(
            listOf(
                QaRuleWithCategory(
                    rule = QaRule(
                        id = 1L,
                        categoryId = 1L,
                        keywords = "fee,cost",
                        replySubject = null,
                        replyBody = factBody,
                        answerBody = factBody,
                        displayName = "Fee fact",
                        replyPolicy = QaReplyPolicy.REVIEW.name,
                        enabled = true
                    ),
                    category = QaCategory(
                        id = 1L,
                        categoryCode = "FIN",
                        categoryName = "Finance",
                        description = null
                    )
                )
            )
        ).`when`(service).listRules(Mockito.isNull())

        mockMvc.perform(get("/api/qa/rules"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].displayName").value("Fee fact"))
            .andExpect(jsonPath("$[0].categoryCode").value("FIN"))
    }
}
