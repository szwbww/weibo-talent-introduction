package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarEvent
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarInput
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MeetingCalendarEventRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.campaign.service.MeetingCalendarService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.llm.service.AiReplyContext
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyHighRiskClaimValidator
import com.weibo.talentintroduction.llm.service.QaFactSelectionService
import com.weibo.talentintroduction.llm.service.ResolvedQaRules
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchService
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexService
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * fast-p 02（I-1～I-4）真实 MySQL 事务语义（mysqlIt 门禁，`-Pmysql-it` / `-DmysqlIt=true`）。
 *
 * 只 mock SMTP（`MailDeliveryService`）与合同外的协作件；事务全部真实：
 * `ManualReplySendAttemptService.finalizeSuccess` 的 REQUIRES_NEW、01
 * `MeetingCalendarService.createFromSentMail` 的 MANDATORY、真实 `mail_record` /
 * `mail_send_attempt` / `meeting_calendar_event` 行与真实 `mail_record.id` 外键。
 *
 * 证明：
 * - 只预览：0 attempt / 0 邮件 / 0 排期（01 只读）；
 * - I-2：发送成功恰好 1 场排期，`source_mail_record_id` 指向真实 SENT 记录，UTC 起止与
 *   预览一致；失败/未知不新增；
 * - I-2 回滚：注入排期写入异常 → 成功事务整体回滚（无 SENT 记录、0 排期），
 *   上层保持既有 DELIVERY_UNKNOWN /「发送状态未知，请勿重复发送」；
 * - I-3：重提同一请求（真实 claim → DEDUP_SENT）不产生第二次 SMTP，改期/取消后的
 *   人工状态不被覆盖；UNKNOWN 重试不再次 SMTP、不伪造排期；
 * - I-4：普通人工回复 0 排期，来源邮件与 ICS 字节不被后续重提/改期改写。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
@Import(
    MeetingCalendarEventRepository::class,
    ManualReplySendAttemptService::class,
    MeetingCalendarSendIntegrationTestSupport::class
)
class MeetingCalendarSendIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var attemptService: ManualReplySendAttemptService

    @Autowired
    private lateinit var calendarService: MeetingCalendarService

    @Autowired
    private lateinit var inboundMailProcessingRepository: InboundMailProcessingRepository

    @Autowired
    private lateinit var expertContactRepository: ExpertContactRepository

    @Autowired
    private lateinit var mailRecordRepository: MailRecordRepository

    /** 上下文里唯一的审计替身（ManualReplySendAttemptService 与 Pending 共用同一实例）。 */
    @Autowired
    private lateinit var operatorActionLogService: OperatorActionLogService

    // ── 仅 SMTP 与合同外协作件被 mock ──

    private val mailDeliveryService = Mockito.mock(MailDeliveryService::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)
    private val expertIndexLevelOperationService = Mockito.mock(ExpertIndexLevelOperationService::class.java)
    private val trustReplyWorkbenchService = Mockito.mock(TrustReplyWorkbenchService::class.java)
    private val unsupportedAnswerIndexService = Mockito.mock(UnsupportedAnswerIndexService::class.java)
    private val emailSuppressionService = Mockito.mock(EmailSuppressionService::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val qaCategoryRepository = Mockito.mock(QaCategoryRepository::class.java)
    private val qaFactSelectionService = Mockito.mock(QaFactSelectionService::class.java)
    private val aiReplyDraftService = Mockito.mock(AiReplyDraftService::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val meetingTemplateService = Mockito.mock(MailComposeTemplateService::class.java)

    private lateinit var pendingMailOperationService: PendingMailOperationService
    private lateinit var meetingConfirmationService: MeetingConfirmationService

    @BeforeEach
    fun setUp() {
        CalendarSendToggle.failOnCreate = false
        cleanup()
        seed()

        val mailVariableService = MailVariableService(expertSearchService, renderTemplateService())

        // Kotlin 非空形参不接受 null matcher：每个 matcher 都带一个真实默认值（与既有测试同式）。
        Mockito.`when`(mailSenderAccountService.getManualSendAccount(anyValue(ACCOUNT_CODE))).thenReturn(senderAccount())
        Mockito.`when`(emailSuppressionService.isSuppressed(anyValue(EXPERT_EMAIL))).thenReturn(false)
        Mockito.`when`(qaCategoryRepository.findAll()).thenReturn(emptyList())
        Mockito.`when`(qaFactSelectionService.select(anyValue(""), anyValue(emptyList<Long>()), anyValue(true)))
            .thenReturn(ResolvedQaRules(emptyList(), emptyList(), emptyList()))
        Mockito.`when`(
            // build 的第 5 个形参有默认值：带默认参数的 Kotlin 方法必须给足全部 matcher
            // （否则默认参数走 $default 桥接，matcher 数量与最终 5 参调用对不上）。
            aiReplyContextService.build(
                anyValue(contactPlaceholder()), Mockito.anyList(), anyValue(""), anyValue(""), Mockito.any()
            )
        ).thenReturn(AiReplyContext(profileText = "", mailHistory = "", contextWarnings = emptyList()))
        Mockito.`when`(mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMailPlaceholder())))
            .thenReturn(DeliveredMail(messageId = MESSAGE_ID, status = "SENT", errorCategory = SmtpErrorCategory.SUCCESS))
        // 通用 MEETING_INVITATION 模板：正文只拼装服务端会议值，preview 与发送两次渲染逐字相同。
        Mockito.`when`(meetingTemplateService.renderByCode(eqValue(TEMPLATE_CODE), Mockito.anyMap(), Mockito.anyInt()))
            .thenAnswer { invocation ->
                val variables = invocation.getArgument<Map<String, String>>(1)
                ComposeTemplateRenderResult(
                    subject = "Meeting confirmation",
                    body = "Dear " + variables["expertFamilyName"].orEmpty().ifBlank { "Colleague" } + ",\n\n" +
                        "Thank you for confirming.\n\n" +
                        "We have noted the meeting time as " + variables["meeting_time"].orEmpty() + ".\n\n" +
                        "Please join the meeting using the following link:\n\n" +
                        variables["zoom_url"].orEmpty() + "\n\n" +
                        "We look forward to speaking with you.\n\n" +
                        "Best regards,\n" +
                        variables["senderName"].orEmpty() + ", " + variables["senderTitle"].orEmpty()
                )
            }

        meetingConfirmationService = MeetingConfirmationService(
            inboundMailProcessingRepository,
            expertContactRepository,
            mailSenderAccountService,
            meetingTemplateService,
            MailContentService(),
            mailVariableService
        )
        pendingMailOperationService = PendingMailOperationService(
            inboundMailProcessingRepository,
            expertContactRepository,
            expertOperatorStatusService,
            expertIndexLevelOperationService,
            mailSenderAccountService,
            mailDeliveryService,
            mailRecordRepository,
            Mockito.mock(MailRecordQaRuleRepository::class.java),
            operatorActionLogService,
            qaRuleRepository,
            qaCategoryRepository,
            qaFactSelectionService,
            aiReplyDraftService,
            aiReplyContextService,
            AiReplyHighRiskClaimValidator(qaRuleRepository),
            MailBodyCleaner(),
            MailContentService(),
            mailVariableService,
            attemptService,
            trustReplyWorkbenchService,
            unsupportedAnswerIndexService,
            emailSuppressionService,
            meetingConfirmationService
        )
    }

    @AfterEach
    fun tearDown() {
        CalendarSendToggle.failOnCreate = false
        cleanup()
    }

    // ------------------------------------------------------------------
    // I-1/I-2：预览只读；成功恰好一场排期
    // ------------------------------------------------------------------

    @Test
    fun `previewing a meeting writes no attempt no mail record and no schedule`() {
        val input = meetingInput()
        val preview = meetingConfirmationService.preview(
            PROCESSING_ID, MeetingPreviewRequest(contactId = CONTACT_ID, meeting = input)
        )

        assertEquals("2026-09-18T02:00:00Z", preview.startUtc, "北京 10:00 = 02:00Z")
        assertEquals("2026-09-18T02:30:00Z", preview.endUtc)
        assertEquals(0L, mailRecordCount())
        assertEquals(0L, attemptCount())
        assertEquals(0L, calendarCount())
    }

    @Test
    fun `a successful meeting send creates exactly one schedule bound to the real sent record`() {
        val (input, preview) = prepareMeetingSend()
        val result = sendMeeting(input, preview)

        assertEquals("SENT", result.sendStatus)
        assertEquals(1, smtpCalls(), "成功发送恰好一次 SMTP")

        val recordId = jdbcTemplate.queryForObject(
            "SELECT id FROM mail_record WHERE send_status = 'SENT' ORDER BY id DESC LIMIT 1", Long::class.java
        )!!
        assertEquals(1L, calendarCount())
        assertEquals(recordId, calendarSourceRecordId(), "排期来源必须是真实落库取得 id 的 SENT 记录")
        assertTrue(
            requireNotNull(calendarStartRaw()).startsWith("2026-09-18 02:00:00"),
            "UTC 秒级起止与预览一致（I-1）：${calendarStartRaw()}"
        )
        assertTrue(requireNotNull(calendarEndRaw()).startsWith("2026-09-18 02:30:00"))
        assertEquals(input.zoomUrl, calendarLink())
        assertEquals(MeetingCalendarEvent.ACTIVE, calendarStatus())
        assertEquals(recordId, mailRecordIdOfSuccessfulAttempt())
    }

    @Test
    fun `an ordinary rich reply without a meeting creates no schedule and keeps no attachment`() {
        val result = pendingMailOperationService.sendManualRichReply(
            inboundProcessingId = PROCESSING_ID,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.",
            operatorName = "op"
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(1, smtpCalls())
        assertEquals(0L, calendarCount())
        val saved = mailRecordRepository.findByMailSendAttemptId(requireNotNull(attemptId()))!!
        assertNull(saved.calendarAttachmentJson, "普通发送身份不含日历快照")
    }

    // ------------------------------------------------------------------
    // I-2：排期写失败 → 整个成功事务回滚；上层保持 UNKNOWN 且不重发
    // ------------------------------------------------------------------

    @Test
    fun `a schedule write failure rolls back the whole success transaction and keeps delivery unknown`() {
        val (input, preview) = prepareMeetingSend()
        CalendarSendToggle.failOnCreate = true

        val ex = assertThrowsResponseStatus { sendMeeting(input, preview) }
        CalendarSendToggle.failOnCreate = false

        assertEquals(HttpStatus.CONFLICT, ex.status)
        assertTrue(
            requireNotNull(ex.reason).startsWith("发送状态未知，请勿重复发送"),
            "沿用既有 UNKNOWN 语义，实际=${ex.reason}"
        )
        assertEquals(1, smtpCalls(), "SMTP 已发出 —— UNKNOWN 的由来")
        assertEquals(0L, calendarCount(), "排期写失败 → 0 新增")
        assertEquals(0L, sentRecordCount(), "SENT 记录随成功事务回滚")
        assertEquals(MailSendAttemptStatus.DELIVERY_UNKNOWN, attemptStatus())
        // 上层只写 UNKNOWN 结果行：attempt 记 DELIVERY_UNKNOWN，mail_record 沿用既有
        // 失败形态（send_status='FAILED'、sent_at NULL）—— 绝不出现半成功的 SENT 记录。
        assertEquals(1L, failureRecordCount())
        assertEquals(0L, sentAtRecordCount())

        // 未知重试：既不再次 SMTP，也不伪造成功排期。
        val retry = assertThrowsResponseStatus { sendMeeting(input, preview) }
        assertEquals(HttpStatus.CONFLICT, retry.status)
        assertTrue(requireNotNull(retry.reason).startsWith("发送状态未知，请勿重复发送"))
        assertEquals(1, smtpCalls(), "UNKNOWN 重试不再次 SMTP")
        assertEquals(0L, calendarCount())
        assertEquals(MailSendAttemptStatus.DELIVERY_UNKNOWN, attemptStatus())
    }

    @Test
    fun `a failed smtp delivery creates no schedule and stays safely retryable`() {
        val (input, preview) = prepareMeetingSend()
        Mockito.`when`(mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMailPlaceholder())))
            .thenReturn(
                DeliveredMail(
                    messageId = MESSAGE_ID,
                    status = "FAILED",
                    errorCategory = SmtpErrorCategory.TRANSIENT,
                    smtpResponseCode = 421,
                    errorDetail = "SMTP timeout"
                )
            )

        val ex = assertThrowsResponseStatus { sendMeeting(input, preview) }

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.status)
        assertEquals(0L, calendarCount(), "投递失败不占用排期")
        assertEquals(0L, sentRecordCount())
        assertEquals(MailSendAttemptStatus.FAILED_SAFE_TO_RETRY, attemptStatus())
    }

    // ------------------------------------------------------------------
    // I-3：重复请求收敛；人工改期/取消不被覆盖
    // ------------------------------------------------------------------

    @Test
    fun `resending a completed request never sends again and keeps later edits and cancel`() {
        val (input, preview) = prepareMeetingSend()
        assertEquals("SENT", sendMeeting(input, preview).sendStatus)

        val eventId = calendarEventId()
        val sourceRecordId = calendarSourceRecordId()
        val createdStart = calendarStartRaw()
        val sourceMailSnapshot = mailRowSnapshot(requireNotNull(sourceRecordId))

        // 重提同一请求：真实 claim → DEDUP_SENT；不 SMTP、不新建/刷新排期。
        assertEquals("SENT", sendMeeting(input, preview).sendStatus)
        assertEquals(1, smtpCalls(), "同一请求 SMTP 调用总数 1")
        assertEquals(1L, calendarCount())
        assertEquals(createdStart, calendarStartRaw())
        assertEquals(sourceRecordId, calendarSourceRecordId())

        // 人工改期到北京 14:00，再重提原成功邀请：改期保持，来源邮件逐字不变。
        calendarService.update(eventId, "2026-09-18T14:00", "2026-09-18T14:30", null, null, calendarVersion())
        assertEquals("SENT", sendMeeting(input, preview).sendStatus)
        assertEquals(1, smtpCalls(), "改期后重提也不 SMTP")
        assertEquals(1L, calendarCount(), "不新建第二场排期")
        assertEquals(
            Instant.parse("2026-09-18T06:00:00Z"),
            calendarService.get(eventId).event.startsAtUtc,
            "人工改期不被重提覆盖"
        )
        assertEquals(sourceRecordId, calendarSourceRecordId())
        assertEquals(sourceMailSnapshot, mailRowSnapshot(requireNotNull(sourceRecordId)), "原 ICS/正文不被改写")

        // 取消后再重提：不复活、不覆盖取消终态。
        calendarService.cancel(eventId, calendarVersion(), "专家时间调整")
        assertEquals("SENT", sendMeeting(input, preview).sendStatus)
        assertEquals(1, smtpCalls())
        assertEquals(1L, calendarCount(), "取消保留原行，不新建")
        val afterCancel = calendarService.get(eventId).event
        assertEquals(MeetingCalendarEvent.CANCELLED, afterCancel.status)
        assertEquals("专家时间调整", afterCancel.cancelReason)
        assertEquals(Instant.parse("2026-09-18T06:00:00Z"), afterCancel.startsAtUtc)
        assertEquals(sourceRecordId, calendarSourceRecordId())
    }

    @Test
    fun `the same request claimed twice by the real repository stays on one attempt row`() {
        val payload = meetingPayloadOf(prepareMeetingSend())

        val first = attemptService.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.CLAIMED, first.result)
        assertEquals(1L, attemptCount())
        assertEquals(0L, calendarCount(), "仅认领不产生排期")

        val second = attemptService.prepareAndClaim(payload)
        assertEquals(first.attemptId, second.attemptId, "同一请求收敛到同一 attempt 行")
        assertEquals(ManualReplySendAttemptService.ClaimResult.IN_PROGRESS, second.result)
        assertEquals(1L, attemptCount())
        assertEquals(0L, calendarCount())
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private fun renderTemplateService() = MailComposeTemplateService(
        Mockito.mock(MailComposeTemplateRepository::class.java),
        Mockito.mock(MailComposeTemplateBlockRepository::class.java),
        qaRuleRepository,
        Mockito.mock(com.weibo.talentintroduction.reply.repository.ReplySnippetRepository::class.java),
        ObjectMapper(),
        Mockito.mock(MailVariableService::class.java),
        expertContactRepository,
        mailSenderAccountService,
        ContentVariantService(Mockito.mock(ContentVariantRepository::class.java), MailPlaceholderService())
    )

    private fun prepareMeetingSend(): Pair<MeetingInput, MeetingPreviewResponse> {
        val input = meetingInput()
        val preview = meetingConfirmationService.preview(
            PROCESSING_ID, MeetingPreviewRequest(contactId = CONTACT_ID, meeting = input)
        )
        return input to preview
    }

    private fun meetingPayloadOf(
        prepared: Pair<MeetingInput, MeetingPreviewResponse>
    ): ManualReplySendAttemptService.SendPayload {
        val (input, preview) = prepared
        return ManualReplySendAttemptService.SendPayload(
            orcidId = ORCID_ID,
            contactId = CONTACT_ID,
            inboundProcessingId = PROCESSING_ID,
            accountCode = ACCOUNT_CODE,
            normalizedRecipient = EXPERT_EMAIL,
            subject = "Re: Meeting confirmation",
            finalText = preview.textBody,
            finalHtml = preview.htmlBody,
            inReplyTo = INBOUND_MESSAGE_ID,
            canonicalQaRuleIds = emptyList(),
            primaryRuleId = null,
            calendarAttachment = CalendarAttachmentSnapshot(
                schemaVersion = MeetingConfirmationDomain.CALENDAR_SCHEMA_VERSION,
                filename = preview.attachment.filename,
                contentType = preview.attachment.contentType,
                icsText = preview.attachment.icsText,
                sha256 = preview.attachment.sha256,
                semanticSha256 = preview.attachment.semanticSha256
            ),
            meetingEvent = MeetingCalendarInput(
                startUtc = Instant.parse(preview.startUtc),
                endUtc = Instant.parse(preview.endUtc),
                meetingLink = input.zoomUrl
            )
        )
    }

    private fun meetingInput() = MeetingInput(
        zoneId = "Asia/Shanghai",
        startLocal = "2026-09-18T10:00",
        endLocal = "2026-09-18T10:30",
        zoomUrl = "https://zoom.us/j/92123456789?pwd=abcDEF123",
        generatedAt = "2026-09-09T02:00:00Z"
    )

    private fun sendMeeting(input: MeetingInput, preview: MeetingPreviewResponse): PendingMailSendResult =
        pendingMailOperationService.sendManualRichReply(
            inboundProcessingId = PROCESSING_ID,
            senderAccountCode = null,
            subject = "Re: Meeting confirmation",
            htmlBody = preview.htmlBody,
            textBody = preview.textBody,
            operatorName = "op",
            templateTextBody = preview.textBody,
            templateHtmlBody = preview.htmlBody,
            safetyWarningConfirmed = true,
            meeting = input,
            previewAttachmentSha256 = preview.attachment.sha256
        )

    private inline fun assertThrowsResponseStatus(block: () -> Unit): ResponseStatusException =
        try {
            block()
            throw AssertionError("expected ResponseStatusException")
        } catch (ex: ResponseStatusException) {
            ex
        }

    private fun senderAccount() = MailSenderAccount(
        accountCode = ACCOUNT_CODE,
        senderEmail = "acc-cal@fixture.local",
        senderName = "LuKai",
        senderTitle = "Customer Care Officer",
        senderDisplayName = "LuKai",
        teamName = "Qingfei Tech Talent Team",
        countryName = "China",
        smtpHost = "smtp.fixture",
        smtpPort = 465,
        smtpUsername = ACCOUNT_CODE,
        smtpPassword = "pw",
        imapHost = "imap.fixture",
        imapPort = 993,
        imapUsername = ACCOUNT_CODE,
        imapPassword = "pw"
    )

    /** Matcher 占位：Kotlin 非空形参不接受 null，`any()/eq()` 之后回落到真实实例。 */
    private fun contactPlaceholder() = ExpertContact(
        id = CONTACT_ID,
        campaignId = 1,
        orcidId = ORCID_ID,
        expertEmail = EXPERT_EMAIL,
        expertName = "Professor Basdogan",
        currentStatus = "WAITING_MEETING_CONFIRMATION"
    )

    private fun composedMailPlaceholder() = ComposedMail(
        to = EXPERT_EMAIL,
        subject = "Re: Meeting confirmation",
        body = "<p>body</p>",
        html = true,
        text = "body",
        messageId = MESSAGE_ID
    )

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun seed() {
        jdbcTemplate.update(
            """
            INSERT INTO mail_sender_account
                (account_code, sender_email, sender_name, sender_title, sender_display_name,
                 team_name, country_name, smtp_host, smtp_port, smtp_username, smtp_password,
                 imap_host, imap_port, imap_username, imap_password)
            VALUES ('$ACCOUNT_CODE', 'acc-cal@fixture.local', 'LuKai', 'Customer Care Officer', 'LuKai',
                    'Qingfei Tech Talent Team', 'China', 'smtp.fixture', 465, '$ACCOUNT_CODE', 'pw',
                    'imap.fixture', 993, '$ACCOUNT_CODE', 'pw')
            """.trimIndent()
        )
        val accountId = jdbcTemplate.queryForObject(
            "SELECT id FROM mail_sender_account WHERE account_code = '$ACCOUNT_CODE'", Long::class.java
        )!!
        jdbcTemplate.update(
            "INSERT INTO campaign (id, campaign_code, campaign_name, status, sender_account_id, created_at, updated_at) " +
                "VALUES (1, 'FIXTURE-CAL', 'Fixture', 'ACTIVE', ?, NOW(6), NOW(6))",
            accountId
        )
        jdbcTemplate.update(
            "INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name, current_status) " +
                "VALUES (?, 1, ?, ?, 'Professor Basdogan', 'WAITING_MEETING_CONFIRMATION')",
            CONTACT_ID, ORCID_ID, EXPERT_EMAIL
        )
        jdbcTemplate.update(
            """
            INSERT INTO inbound_mail_processing
                (id, sender_account_code, imap_uid, message_id, from_email, subject, body, cleaned_body,
                 received_at, process_status, process_reason, expert_contact_id, created_at, updated_at)
            VALUES (?, '$ACCOUNT_CODE', 1, '$INBOUND_MESSAGE_ID', ?, 'Re: invitation', 'Can I work remotely?',
                    'Can I work remotely?', NOW(6), 'MANUAL_REVIEW', 'QA_NO_MATCH', ?, NOW(6), NOW(6))
            """.trimIndent(),
            PROCESSING_ID, EXPERT_EMAIL, CONTACT_ID
        )
    }

    private fun cleanup() {
        jdbcTemplate.update("DELETE FROM meeting_calendar_event")
        jdbcTemplate.update("DELETE FROM mail_record_qa_rule")
        jdbcTemplate.update("DELETE FROM mail_record")
        jdbcTemplate.update("DELETE FROM mail_send_attempt")
        jdbcTemplate.update("DELETE FROM inbound_mail_processing")
        jdbcTemplate.update("DELETE FROM expert_contact")
        jdbcTemplate.update("DELETE FROM campaign")
        jdbcTemplate.update("DELETE FROM mail_sender_account")
    }

    private fun smtpCalls(): Int =
        Mockito.mockingDetails(mailDeliveryService).invocations.count { it.method.name == "send" }

    private fun mailRecordCount(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mail_record", Long::class.java)!!

    private fun sentRecordCount(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mail_record WHERE send_status = 'SENT'", Long::class.java)!!

    private fun failureRecordCount(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mail_record WHERE send_status = 'FAILED'", Long::class.java
        )!!

    private fun sentAtRecordCount(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mail_record WHERE sent_at IS NOT NULL", Long::class.java
        )!!

    private fun attemptCount(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mail_send_attempt", Long::class.java)!!

    private fun attemptId(): Long? =
        jdbcTemplate.query("SELECT id FROM mail_send_attempt ORDER BY id DESC LIMIT 1") { rs, _ -> rs.getLong(1) }
            .firstOrNull()

    private fun attemptStatus(): String? =
        jdbcTemplate.queryForObject("SELECT status FROM mail_send_attempt ORDER BY id DESC LIMIT 1", String::class.java)

    private fun mailRecordIdOfSuccessfulAttempt(): Long? =
        jdbcTemplate.queryForObject(
            "SELECT mr.id FROM mail_record mr JOIN mail_send_attempt a ON mr.mail_send_attempt_id = a.id " +
                "WHERE a.status = 'SENT' ORDER BY mr.id DESC LIMIT 1",
            Long::class.java
        )

    private fun mailRowSnapshot(recordId: Long): String =
        jdbcTemplate.queryForObject(
            "SELECT CONCAT(body, '|', COALESCE(calendar_attachment_json, 'null')) FROM mail_record WHERE id = ?",
            String::class.java,
            recordId
        )!!

    private fun calendarCount(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM meeting_calendar_event", Long::class.java)!!

    private fun calendarEventId(): Long =
        jdbcTemplate.queryForObject("SELECT id FROM meeting_calendar_event ORDER BY id DESC LIMIT 1", Long::class.java)!!

    private fun calendarSourceRecordId(): Long? =
        jdbcTemplate.queryForObject(
            "SELECT source_mail_record_id FROM meeting_calendar_event ORDER BY id DESC LIMIT 1", Long::class.java
        )

    private fun calendarStartRaw(): String? =
        jdbcTemplate.queryForObject(
            "SELECT starts_at_utc FROM meeting_calendar_event ORDER BY id DESC LIMIT 1", String::class.java
        )

    private fun calendarEndRaw(): String? =
        jdbcTemplate.queryForObject(
            "SELECT ends_at_utc FROM meeting_calendar_event ORDER BY id DESC LIMIT 1", String::class.java
        )

    private fun calendarLink(): String? =
        jdbcTemplate.queryForObject(
            "SELECT meeting_link FROM meeting_calendar_event ORDER BY id DESC LIMIT 1", String::class.java
        )

    private fun calendarStatus(): String? =
        jdbcTemplate.queryForObject(
            "SELECT status FROM meeting_calendar_event ORDER BY id DESC LIMIT 1", String::class.java
        )

    private fun calendarVersion(): String = calendarService.get(calendarEventId()).event.updatedAt.toString()

    companion object {
        private const val ACCOUNT_CODE = "acc-cal"
        private const val CONTACT_ID = 1L
        private const val PROCESSING_ID = 100L
        private const val ORCID_ID = "0000-0000-0000-9001"
        private const val EXPERT_EMAIL = "expert@test.com"
        private const val INBOUND_MESSAGE_ID = "in-cal-1"
        private const val TEMPLATE_CODE = "MEETING_INVITATION"
        private const val MESSAGE_ID = "<manual-rich-cal@weibo.com>"
    }
}

/** 事件写入失败注入开关（仅测试内使用，默认关闭 → 完全走 01 真实实现）。 */
internal object CalendarSendToggle {
    @Volatile
    var failOnCreate: Boolean = false
}

/**
 * 01 真实实现的开关包装：`failOnCreate=true` 时按计划要求注入「排期写异常」，
 * 其余时刻逐字委托真实 repository 与事务语义（不是 mock 替身）。
 */
class ToggleableMeetingCalendarService(
    repository: MeetingCalendarEventRepository,
    expertContactRepository: ExpertContactRepository
) : MeetingCalendarService(repository, expertContactRepository) {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun createFromSentMail(
        record: MailRecord,
        input: MeetingCalendarInput
    ): MeetingCalendarEventRepository.EventRow {
        if (CalendarSendToggle.failOnCreate) {
            throw IllegalStateException("injected calendar write failure")
        }
        return super.createFromSentMail(record, input)
    }
}

/** 真实 MySQL 集成测试的窄配置：唯一 calendar 服务（真实实现 + 失败开关）与审计替身。 */
@TestConfiguration
class MeetingCalendarSendIntegrationTestSupport {

    @Bean
    fun meetingCalendarService(
        repository: MeetingCalendarEventRepository,
        expertContactRepository: ExpertContactRepository
    ): MeetingCalendarService = ToggleableMeetingCalendarService(repository, expertContactRepository)

    @Bean
    fun operatorActionLogService(): OperatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
}
