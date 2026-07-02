package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.InboundMailTagService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(InboundMailSummaryController::class)
class InboundMailSummaryControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var inboundMailProcessingRepository: InboundMailProcessingRepository

    @MockBean
    private lateinit var inboundMailTagService: InboundMailTagService

    @MockBean
    private lateinit var expertContactRepository: ExpertContactRepository

    @MockBean
    private lateinit var mailRecordRepository: MailRecordRepository

    @Test
    fun `list mails accepts iso local datetime query params`() {
        val from = LocalDateTime.of(2026, 4, 2, 0, 0)
        val to = LocalDateTime.of(2026, 4, 3, 0, 0)

        Mockito.`when`(
            inboundMailProcessingRepository.listInboundSummary(
                from = from,
                to = to,
                qaRuleId = null,
                label = null,
                limit = 20,
                offset = 0
            )
        ).thenReturn(
            listOf(
                InboundMailProcessing(
                    id = 100L,
                    senderAccountCode = "account1",
                    imapUid = 200L,
                    messageId = "msg-1",
                    fromEmail = "expert@example.com",
                    subject = "Reply",
                    body = "Hello",
                    cleanedBody = "Hello",
                    receivedAt = from.plusHours(1),
                    processStatus = "PROCESSED",
                    processReason = "QA_MATCHED",
                    expertContactId = 42L
                )
            )
        )
        Mockito.`when`(
            inboundMailProcessingRepository.countInboundSummary(
                from = from,
                to = to,
                qaRuleId = null,
                label = null
            )
        ).thenReturn(1L)
        Mockito.`when`(inboundMailTagService.listTagsBatch(listOf(100L))).thenReturn(emptyMap())
        Mockito.`when`(expertContactRepository.findAllById(listOf(42L))).thenReturn(emptyList())

        mockMvc.perform(
            get("/api/inbound-summary/mails")
                .param("from", "2026-04-02T00:00:00")
                .param("to", "2026-04-03T00:00:00")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.records[0].inboundId").value(100))
    }
}
