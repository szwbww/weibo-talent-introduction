package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.controller.MailboxController
import com.weibo.talentintroduction.mail.controller.MailboxListResponse
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailboxRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import java.time.LocalDateTime
import java.util.Optional

/**
 * B4（T2b-6）：按 task_execution_id 过滤收发件箱的单元测试。
 * 覆盖 N2b-1（null 走原查询）、I2b-3（两条路径 DTO 一致）、I2b-4（悬垂 id 不报错）。
 */
class MailboxTaskExecutionFilterTest {

    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val senderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val inboundMailTagService = Mockito.mock(InboundMailTagService::class.java)
    private val mailboxService = MailboxService(
        mailRecordRepository,
        senderAccountRepository,
        inboundMailProcessingRepository,
        mailAttachmentRepository,
        expertContactRepository,
        inboundMailTagService
    )

    private val activeAccount = MailSenderAccount(
        id = 1L,
        accountCode = "acc1",
        senderEmail = "active@example.com",
        senderName = "Active",
        senderTitle = null,
        senderDisplayName = null,
        teamName = null,
        countryName = null,
        smtpHost = "smtp.example.com", smtpPort = 465, smtpUsername = "active", smtpPassword = "pwd",
        imapHost = "imap.example.com", imapPort = 993, imapUsername = "active", imapPassword = "pwd",
        enabled = true
    )

    private val contact = ExpertContact(
        id = 4471L,
        campaignId = 1L,
        orcidId = "0000-0001-2345-6789",
        expertEmail = "expert@example.com",
        expertName = "Dr. Expert"
    )

    private val now = LocalDateTime.of(2026, 8, 16, 10, 0, 0)

    private fun outboundRecord(
        id: Long = 42L,
        taskExecutionId: Long = 13023L
    ) = MailRecord(
        id = id,
        expertContactId = 4471L,
        direction = "OUTBOUND",
        mailType = "INTRODUCTION",
        senderAccountCode = "acc1",
        triggeredBy = "MANUAL",
        messageId = "msg-$id",
        inReplyTo = null,
        subject = "Invitation",
        body = "Hello body",
        cleanedBody = null,
        matchedQaRuleId = null,
        sendStatus = "SENT",
        receivedAt = null,
        sentAt = now,
        taskExecutionId = taskExecutionId
    )

    /** 与 listMailbox 的 UNION 投影（MAIL_RECORD 分支）对同一底层数据的 SQL 形态。 */
    private fun sqlRowFor(record: MailRecord, sentAt: LocalDateTime) = MailboxRow(
        source = "MAIL_RECORD",
        id = record.id ?: 0L,
        expertContactId = record.expertContactId,
        direction = record.direction,
        mailType = record.mailType,
        senderAccountCode = record.senderAccountCode,
        triggeredBy = record.triggeredBy,
        matchedQaRuleId = record.matchedQaRuleId,
        subject = record.subject,
        bodyPreview = (record.cleanedBody ?: record.body)?.take(200),
        sendStatus = record.sendStatus,
        sentAt = sentAt,
        receivedAt = null,
        processStatus = null,
        reasonType = null,
        expertEmail = contact.expertEmail,
        expertName = contact.expertName,
        hasAttachment = 0L,
        inboundProcessingId = null
    )

    // ── Controller 路由（N2b-1）───────────────────────────────────────────────

    @Test
    fun `N2b-1 taskExecutionId null keeps original listMailbox path`() {
        val mailboxServiceMock = Mockito.mock(MailboxService::class.java)
        val controller = MailboxController(mailboxServiceMock)

        controller.list(
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startDate = null,
            endDate = null,
            pending = false,
            page = 0,
            size = 20,
            taskExecutionId = null
        )

        Mockito.verify(mailboxServiceMock).listMailbox(
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            pending = false,
            page = 0,
            size = 20
        )
        Mockito.verify(mailboxServiceMock, never()).listByTaskExecution(Mockito.anyLong())
    }

    @Test
    fun `taskExecutionId present routes to filtered query only`() {
        val mailboxServiceMock = Mockito.mock(MailboxService::class.java)
        val controller = MailboxController(mailboxServiceMock)
        Mockito.`when`(mailboxServiceMock.listByTaskExecution(13023L))
            .thenReturn(MailboxListResponse(emptyList(), 0))

        val response = controller.list(
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startDate = null,
            endDate = null,
            pending = false,
            page = 0,
            size = 20,
            taskExecutionId = 13023L
        )

        assertEquals(0L, response.totalCount)
        Mockito.verify(mailboxServiceMock).listByTaskExecution(13023L)
        Mockito.verify(mailboxServiceMock, never()).listMailbox(
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            pending = false,
            page = 0,
            size = 20
        )
    }

    // ── Service：I2b-3 双路径 DTO 一致 ───────────────────────────────────────

    @Test
    fun `I2b-3 filtered path produces same DTO as existing list path for same record`() {
        val record = outboundRecord()
        val sqlRow = sqlRowFor(record, sentAt = now)

        // 路径 A：既有 listMailbox（SQL 投影 → toMailboxItemResponse）
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE))
            .thenReturn(listOf(activeAccount))
        Mockito.`when`(
            mailRecordRepository.listMailbox(
                accountCodes = listOf("acc1"),
                direction = null,
                accountCode = null,
                keyword = null,
                recipientEmail = null,
                startTime = null,
                endTime = null,
                onlyPending = 0,
                limit = 20,
                offset = 0L
            )
        ).thenReturn(listOf(sqlRow))
        Mockito.`when`(
            mailRecordRepository.countMailbox(
                accountCodes = listOf("acc1"),
                direction = null,
                accountCode = null,
                keyword = null,
                recipientEmail = null,
                startTime = null,
                endTime = null,
                onlyPending = 0
            )
        ).thenReturn(1L)
        Mockito.`when`(inboundMailTagService.listTagsBatch(emptyList())).thenReturn(emptyMap())

        val listDto = mailboxService.listMailbox(
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            pending = false,
            page = 0,
            size = 20
        ).items.single()

        // 路径 B：listByTaskExecution（实体 → 同形 MailboxRow → 同一装配）
        Mockito.`when`(mailRecordRepository.findAllByTaskExecutionIdOrderByIdAsc(13023L))
            .thenReturn(listOf(record))
        Mockito.`when`(expertContactRepository.findById(4471L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(42L))
            .thenReturn(emptyList())

        val filterDto = mailboxService.listByTaskExecution(13023L).items.single()

        assertEquals(listDto, filterDto, "同一 MailRecord 走两条路径产出的 DTO 必须 equals（I2b-3）")
    }

    // ── Service：I2b-4 悬垂 id ───────────────────────────────────────────────

    @Test
    fun `I2b-4 dangling task execution id returns mails without error`() {
        // task_execution 表无对应行（P3 保留清理已删除该执行）：不 join task_execution，
        // 悬垂 id 仍正常返回邮件且不抛异常。
        val record = outboundRecord(taskExecutionId = 999L)
        Mockito.`when`(mailRecordRepository.findAllByTaskExecutionIdOrderByIdAsc(999L))
            .thenReturn(listOf(record))
        Mockito.`when`(expertContactRepository.findById(4471L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(42L))
            .thenReturn(emptyList())

        val response = mailboxService.listByTaskExecution(999L)

        assertTrue(response.items.isNotEmpty(), "悬垂 id 必须正常返回邮件")
        assertEquals(1, response.items.size)
    }

    @Test
    fun `I2b-4 filtered rows keep batch mail tags from shared assembly`() {
        val record = outboundRecord()
        Mockito.`when`(mailRecordRepository.findAllByTaskExecutionIdOrderByIdAsc(13023L))
            .thenReturn(listOf(record))
        Mockito.`when`(expertContactRepository.findById(4471L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(42L))
            .thenReturn(emptyList())

        val response = mailboxService.listByTaskExecution(13023L)
        val item = response.items.single()

        // computeTags（共享装配）：专家 / 发件 / 手动回复 / 首发
        assertEquals(listOf("专家", "发件", "手动回复", "首发"), item.tags)
        assertEquals(1L, response.totalCount)
    }
}
