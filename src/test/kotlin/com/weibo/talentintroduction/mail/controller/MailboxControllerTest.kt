package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.service.MailboxService
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
import java.time.format.DateTimeFormatter

@WebMvcTest(MailboxController::class)
class MailboxControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var mailboxService: MailboxService

    @Test
    fun `list mailbox records matches query mapping and returns response DTO`() {
        val testResponse = MailboxListResponse(
            items = listOf(
                MailboxItemResponse(
                    id = 1L,
                    source = "MAIL_RECORD",
                    expertContactId = 100L,
                    direction = "OUTBOUND",
                    mailType = "INTRODUCTION",
                    senderAccountCode = "account1",
                    triggeredBy = "SYSTEM",
                    isSystemSent = true,
                    expertEmail = "expert@example.com",
                    expertName = "Expert Name",
                    subject = "Hello",
                    bodyPreview = "Preview of body...",
                    hasAttachment = false,
                    sendStatus = "SENT",
                    timestamp = "2026-06-22T10:00:00",
                    tags = listOf("专家", "发件", "自动回复", "首发"),
                    processStatus = null,
                    reasonType = null,
                    inboundProcessingId = null
                ),
                MailboxItemResponse(
                    id = 2L,
                    source = "INBOUND_PROCESSING",
                    expertContactId = 200L,
                    direction = "INBOUND",
                    mailType = "REPLY",
                    senderAccountCode = "account1",
                    triggeredBy = null,
                    isSystemSent = false,
                    expertEmail = "expert2@example.com",
                    expertName = "Expert Two",
                    subject = "Re: Hello",
                    bodyPreview = "Reply body...",
                    hasAttachment = true,
                    sendStatus = null,
                    timestamp = "2026-06-22T11:00:00",
                    tags = listOf("专家", "收件"),
                    processStatus = "PROCESSED",
                    reasonType = "MANUAL_BOUND",
                    inboundProcessingId = 2L
                )
            ),
            totalCount = 2L
        )

        val startLocalTime = LocalDateTime.of(2026, 6, 22, 0, 0, 0)
        val endLocalTime = LocalDateTime.of(2026, 6, 23, 0, 0, 0) // because endDate is plusDays(1) start of day

        Mockito.`when`(
            mailboxService.listMailbox(
                direction = "OUTBOUND",
                accountCode = "account1",
                keyword = "Hello",
                recipientEmail = "expert@example.com",
                startTime = startLocalTime,
                endTime = endLocalTime,
                pending = false,
                page = 0,
                size = 20
            )
        ).thenReturn(testResponse)

        mockMvc.perform(
            get("/api/mail/mailbox")
                .param("direction", "OUTBOUND")
                .param("accountCode", "account1")
                .param("keyword", "Hello")
                .param("recipientEmail", "expert@example.com")
                .param("startDate", "2026-06-22")
                .param("endDate", "2026-06-22")
                .param("page", "0")
                .param("size", "20")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(2))
            .andExpect(jsonPath("$.items[0].id").value(1))
            .andExpect(jsonPath("$.items[0].direction").value("OUTBOUND"))
            .andExpect(jsonPath("$.items[0].triggeredBy").value("SYSTEM"))
            .andExpect(jsonPath("$.items[0].isSystemSent").value(true))
            .andExpect(jsonPath("$.items[0].hasAttachment").value(false))
            .andExpect(jsonPath("$.items[1].id").value(2))
            .andExpect(jsonPath("$.items[1].direction").value("INBOUND"))
            .andExpect(jsonPath("$.items[1].triggeredBy").isEmpty)
            .andExpect(jsonPath("$.items[1].isSystemSent").value(false))
            .andExpect(jsonPath("$.items[1].hasAttachment").value(true))
    }

    @Test
    fun `detail mailbox record returns detail DTO`() {
        val detail = MailboxDetailResponse(
            id = 1L,
            source = "MAIL_RECORD",
            expertContactId = 100L,
            direction = "OUTBOUND",
            mailType = "INTRODUCTION",
            senderAccountCode = "account1",
            triggeredBy = "SYSTEM",
            isSystemSent = true,
            expertEmail = "expert@example.com",
            expertName = "Expert Name",
            subject = "Hello",
            bodyPreview = "Preview",
            body = "Full body text",
            hasAttachment = false,
            sendStatus = "SENT",
            timestamp = "2026-06-22T10:00:00",
            processStatus = null,
            reasonType = null,
            inboundProcessingId = null
        )

        Mockito.`when`(mailboxService.getMailboxDetail("MAIL_RECORD", 1L)).thenReturn(detail)

        mockMvc.perform(get("/api/mail/mailbox/MAIL_RECORD/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.body").value("Full body text"))
            .andExpect(jsonPath("$.direction").value("OUTBOUND"))
    }
}
