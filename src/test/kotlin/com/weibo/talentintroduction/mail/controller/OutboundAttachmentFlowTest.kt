package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.auth.config.AuthInterceptor
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.llm.service.AiReplyContext
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyHighRiskClaimValidator
import com.weibo.talentintroduction.llm.service.QaFactSelectionService
import com.weibo.talentintroduction.llm.service.ResolvedQaRules
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchService
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.OutboundMailAttachment
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import com.weibo.talentintroduction.mail.repository.OutboundMailAttachmentRepository
import com.weibo.talentintroduction.mail.service.ComposedMail
import com.weibo.talentintroduction.mail.service.DeliveredMail
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.service.InboundMailTagService
import com.weibo.talentintroduction.mail.service.MailBodyCleaner
import com.weibo.talentintroduction.mail.service.MailContentService
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.MailPlaceholderService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.mail.service.MailboxConversationService
import com.weibo.talentintroduction.mail.service.ManualReplySendAttemptService
import com.weibo.talentintroduction.mail.service.MeetingConfirmationService
import com.weibo.talentintroduction.mail.service.OutboundAttachmentException
import com.weibo.talentintroduction.mail.service.OutboundAttachmentService
import com.weibo.talentintroduction.mail.service.OutboundAttachmentUploadResponse
import com.weibo.talentintroduction.mail.service.OutboundAttachmentSnapshotCodec
import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.mail.service.PendingMailSendResult
import com.weibo.talentintroduction.mail.service.outboundSha256Hex
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

/**
 * 06 验收（I-1～I-5）：上传 → 人工发送（两条入口）→ 会话 timeline → 已发原件下载 的贯通。
 *
 * 真实件：04 [OutboundAttachmentService]（真实临时存储目录、真实字节读写、真实 SHA/归属/
 * 容量校验、真实快照 codec）；真实 [MailboxConversationService] 快照投影（只 mock 其查询
 * 仓库与附件解析边界）；真实 [OutboundAttachmentController] + [AuthInterceptor] +
 * [GlobalExceptionHandler] 的 HTTP 语义。只 mock SMTP（[MailDeliveryService]）与发送门外部
 * 协作件；finalizeSuccess 桩按 05 的四分支语义把 payload 的通用附件快照（真实 codec）写入
 * 真实形态的 `mail_record` 行，使后续 timeline/下载读的是同一份存档。
 *
 * 覆盖：
 * - I-1：两条入口都按会话身份解析同一文件集合；越权/缺失/损坏/非法/超量一律 claim 前失败
 *   （SMTP=0、attempt=0，且不写成投递失败/UNKNOWN）；
 * - I-2：同 requestId 重提不重选锚点、不重投；附件语义不同固定 409；无附件短路不变；
 * - I-3：timeline 只投影 SENT 人工富文本行、单次批量读、损坏行不阻断；
 * - I-4：已发下载跨操作员可读、禁用真实账号可读、模拟账号/错专家/错消息/未引用 id 一律 404、
 *   匿名 401，下载 SHA 与 SMTP 携带的字节一致；
 * - I-5：退订前置检查与线程锚点语义不被附件改变，缺失原件不误入 SMTP UNKNOWN。
 */
class OutboundAttachmentFlowTest {

    @TempDir
    lateinit var storageRoot: Path

    /** 与 Spring Boot 默认 mapper 一致：Kotlin data class 反序列化需要 KotlinModule。 */
    private val kotlinMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    // ---- 04 真实服务：内存台账 + 真实临时存储目录 ----
    private val attachmentRepository: OutboundMailAttachmentRepository =
        Mockito.mock(OutboundMailAttachmentRepository::class.java)
    private val contactRepository: ExpertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val attachmentRows = linkedMapOf<String, OutboundMailAttachment>()

    // ---- 发送门协作件（只 mock 外部副作用） ----
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)
    private val expertIndexLevelOperationService = Mockito.mock(ExpertIndexLevelOperationService::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailDeliveryService = Mockito.mock(MailDeliveryService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailRecordQaRuleRepository = Mockito.mock(MailRecordQaRuleRepository::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val qaCategoryRepository = Mockito.mock(QaCategoryRepository::class.java)
    private val qaFactSelectionService = Mockito.mock(QaFactSelectionService::class.java)
    private val aiReplyDraftService = Mockito.mock(AiReplyDraftService::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)
    private val trustReplyWorkbenchService = Mockito.mock(TrustReplyWorkbenchService::class.java)
    private val unsupportedAnswerIndexService = Mockito.mock(UnsupportedAnswerIndexService::class.java)
    private val emailSuppressionService = Mockito.mock(EmailSuppressionService::class.java)
    private val meetingConfirmationService = Mockito.mock(MeetingConfirmationService::class.java)
    private val manualReplySendAttemptService = Mockito.mock(ManualReplySendAttemptService::class.java)

    // ---- 会话 timeline / 下载协作件 ----
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val expertMaterialService = Mockito.mock(ExpertMaterialService::class.java)
    private val inboundMailTagService = Mockito.mock(InboundMailTagService::class.java)
    private val mailboxConversationRepository = Mockito.mock(MailboxConversationRepository::class.java)
    private val mailSenderAccountRepository: MailSenderAccountRepository =
        Mockito.mock(MailSenderAccountRepository::class.java)
    private val authService: AuthService = Mockito.mock(AuthService::class.java)

    private val renderTemplateService = MailComposeTemplateService(
        Mockito.mock(com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository::class.java),
        Mockito.mock(com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository::class.java),
        qaRuleRepository,
        Mockito.mock(com.weibo.talentintroduction.reply.repository.ReplySnippetRepository::class.java),
        ObjectMapper(),
        Mockito.mock(MailVariableService::class.java),
        contactRepository,
        mailSenderAccountService,
        ContentVariantService(
            Mockito.mock(com.weibo.talentintroduction.variant.repository.ContentVariantRepository::class.java),
            MailPlaceholderService()
        )
    )
    private val mailVariableService = MailVariableService(expertSearchService, renderTemplateService)

    private lateinit var attachmentService: OutboundAttachmentService
    private lateinit var pendingService: PendingMailOperationService
    private lateinit var conversationService: MailboxConversationService
    private lateinit var mockMvc: MockMvc

    private val sentMails = mutableListOf<ComposedMail>()
    private val claimedPayloads = mutableListOf<ManualReplySendAttemptService.SendPayload>()
    private val mailRecords = linkedMapOf<Long, MailRecord>()
    private var nextRecordId = 500L

    private val contact = ExpertContact(
        id = CONTACT_ID,
        campaignId = 1,
        orcidId = "orcid-1",
        expertEmail = "expert@test.com",
        expertName = "Expert",
        currentStatus = "INTRO_SENT",
        operatorStatus = "CONTACTED",
        currentIndexLevel = "CANDIDATE"
    )

    @BeforeEach
    fun setUp() {
        attachmentRows.clear()
        mailRecords.clear()
        sentMails.clear()
        claimedPayloads.clear()

        attachmentService = OutboundAttachmentService(
            MailAttachmentStorageProperties(basePath = storageRoot.toString()),
            attachmentRepository,
            contactRepository
        )
        Mockito.`when`(contactRepository.existsById(CONTACT_ID)).thenReturn(true)
        Mockito.`when`(contactRepository.existsById(OTHER_CONTACT_ID)).thenReturn(true)
        Mockito.doAnswer { invocation ->
            val row = invocation.getArgument<OutboundMailAttachment>(0)
            attachmentRows[row.id] = row
            null
        }.`when`(attachmentRepository).insert(anyValue(attachmentSample()))
        Mockito.`when`(
            attachmentRepository.findAllByExpertContactIdAndIdIn(Mockito.anyLong(), Mockito.anyList())
        ).thenAnswer { invocation ->
            val contactId = invocation.getArgument<Long>(0)
            val ids = invocation.getArgument<List<String>>(1)
            ids.mapNotNull { attachmentRows[it] }.filter { it.expertContactId == contactId }
        }

        pendingService = PendingMailOperationService(
            inboundMailProcessingRepository,
            contactRepository,
            expertOperatorStatusService,
            expertIndexLevelOperationService,
            mailSenderAccountService,
            mailDeliveryService,
            mailRecordRepository,
            mailRecordQaRuleRepository,
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
            manualReplySendAttemptService,
            trustReplyWorkbenchService,
            unsupportedAnswerIndexService,
            emailSuppressionService,
            meetingConfirmationService,
            outboundAttachmentService = attachmentService
        )
        conversationService = MailboxConversationService(
            mailboxConversationRepository,
            mailSenderAccountRepository,
            contactRepository,
            expertMaterialService,
            inboundMailTagService,
            expertSearchService,
            mailRecordRepository
        )
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                OutboundAttachmentController(
                    attachmentService,
                    mailRecordRepository,
                    mailSenderAccountRepository
                )
            )
            .addInterceptors(AuthInterceptor(authService, ObjectMapper()))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        Mockito.`when`(authService.findUser(OWNER)).thenReturn(adminUser(OWNER))
        Mockito.`when`(authService.findUser(OTHER_OPERATOR)).thenReturn(adminUser(OTHER_OPERATOR))

        // 发送门前置事实
        Mockito.`when`(inboundMailProcessingRepository.findById(INBOUND_ID))
            .thenReturn(Optional.of(inbound()))
        Mockito.`when`(contactRepository.findById(CONTACT_ID)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(CONTACT_ID))
            .thenReturn(emptyList())
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Can I work remotely?", ""))
            .thenReturn(
                AiReplyContext(
                    profileText = "",
                    mailHistory = "",
                    contextWarnings = emptyList(),
                    researchProfileSufficient = true
                )
            )
        Mockito.`when`(expertSearchService.findByOrcidId(contact.orcidId, ExpertIndexLevel.CANDIDATE))
            .thenReturn(
                ExpertProfile(
                    orcidId = contact.orcidId,
                    email = contact.expertEmail,
                    givenNames = null,
                    familyNames = "Expert",
                    country = null,
                    keyword = null,
                    employment = null
                )
            )
        Mockito.`when`(qaCategoryRepository.findAll()).thenReturn(emptyList())
        // 来信路径的安全检查会求值 QA selection（无证据 → 空事实集，不产生 findings）。
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", null, true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = emptyList(),
                    promptRuleIds = emptyList(),
                    requestFacts = emptyList()
                )
            )
        Mockito.`when`(mailSenderAccountService.getManualSendAccount(ACCOUNT_CODE)).thenReturn(senderAccount())
        Mockito.`when`(manualReplySendAttemptService.canonicalConversationRequestId(REQUEST_ID))
            .thenReturn(REQUEST_ID)
        // 真实 SENT 线程锚点（无来信的会话跟进入口）
        Mockito.`when`(
            mailRecordRepository.findLatestSentOutboundAnchor(
                eqValue(CONTACT_ID),
                Mockito.any(),
                eqValue(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
            )
        ).thenReturn(sentAnchor())
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, REQUEST_ID))
            .thenReturn(null)

        // claim → SMTP → finalize：与 05 的最终发送门同形
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(payloadStub())))
            .thenReturn(
                ManualReplySendAttemptService.ClaimedAttempt(
                    attemptId = 1L,
                    messageId = MESSAGE_ID,
                    result = ManualReplySendAttemptService.ClaimResult.CLAIMED
                )
            )
        Mockito.doAnswer { invocation ->
            sentMails += invocation.getArgument<ComposedMail>(1)
            DeliveredMail(messageId = MESSAGE_ID, status = "SENT")
        }.`when`(mailDeliveryService).send(anyValue(senderAccount()), anyValue(mailStub()))
        Mockito.doAnswer { invocation ->
            persistSentRecord(
                invocation.getArgument<ManualReplySendAttemptService.SendPayload>(0)
            )
        }.`when`(manualReplySendAttemptService)
            .finalizeSuccess(anyValue(payloadStub()), Mockito.eq(1L), eqValue(MESSAGE_ID))

        // 会话 timeline / 已发下载读取同一份 mail_record 存档
        Mockito.`when`(mailRecordRepository.findById(Mockito.anyLong()))
            .thenAnswer { invocation -> Optional.ofNullable(mailRecords[invocation.getArgument<Long>(0)]) }
        Mockito.`when`(mailRecordRepository.findAllById(Mockito.anyCollection()))
            .thenAnswer { invocation ->
                val ids = invocation.getArgument<Collection<*>>(0).filterIsInstance<Long>()
                ids.mapNotNull { mailRecords[it] }
            }
        Mockito.`when`(
            mailSenderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        ).thenReturn(listOf(senderAccount(enabled = false), senderAccount(accountCode = "acc-b")))
        Mockito.`when`(expertMaterialService.resolveMessageAttachments(Mockito.anyString(), Mockito.anyLong()))
            .thenReturn(emptyList())
        Mockito.`when`(inboundMailTagService.listTagsBatch(Mockito.anyCollection())).thenReturn(emptyMap())
    }

    // ------------------------------------------------------------------
    // I-1/I-3/I-4：上传 → 来信人工发送 → timeline → 已发原件下载贯通
    // ------------------------------------------------------------------

    @Test
    fun `upload send timeline and sent download share the same original bytes for the inbound entry`() {
        val bytes = "会议资料-来信路径".toByteArray(StandardCharsets.UTF_8) + ByteArray(32) { it.toByte() }
        val uploaded = uploadAttachment(OWNER, CONTACT_ID, "会议资料.txt", "text/plain", bytes)

        val result = pendingService.sendManualRichReply(
            inboundProcessingId = INBOUND_ID,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>Test</p>",
            textBody = "Test",
            operatorName = "display-op",
            attachmentIds = listOf(uploaded.id),
            authenticatedUsername = OWNER
        )
        assertEquals("SENT", result.sendStatus)

        // I-1：SMTP 与 payload 用同一份原件/快照
        val mail = sentMails.single()
        assertEquals(1, mail.outboundAttachments.size)
        assertArrayEquals(bytes, mail.outboundAttachments.single().bytes)
        val payload = claimedPayloads.single()
        assertEquals(mail.outboundAttachments.single().snapshot, payload.outboundAttachments.single())

        val recordId = requireNotNull(mailRecords.keys.singleOrNull())
        val item = timelineItems().single()
        assertEquals(1, item["outboundAttachments"].size())
        val attachment = item["outboundAttachments"][0]
        assertEquals(uploaded.id, attachment["id"].asText())
        assertEquals("会议资料.txt", attachment["filename"].asText())
        assertEquals("text/plain", attachment["contentType"].asText())
        assertEquals(bytes.size.toLong(), attachment["byteLength"].asLong())
        assertEquals(
            "/api/mail/conversations/$CONTACT_ID/messages/$recordId/outbound-attachments/" +
                "${uploaded.id}/download",
            attachment["downloadUrl"].asText()
        )
        // I-3：入站材料口径与日历字段不受通用附件影响；整窗只批量读一次 mail_record。
        assertEquals(0, item["attachmentCount"].asInt())
        assertTrue(item["firstAttachmentNames"].isEmpty)
        assertTrue(item["calendarAttachment"].isNull)
        Mockito.verify(mailRecordRepository, Mockito.times(1)).findAllById(Mockito.anyCollection())

        // I-4：下载字节与存档/SMTP 完全一致（SHA 相同）
        val download = mockMvc.perform(
            get(attachment["downloadUrl"].asText()).session(sessionOf(OWNER))
        ).andExpect(status().isOk).andReturn()
        val downloaded = download.response.contentAsByteArray
        assertArrayEquals(bytes, downloaded)
        assertEquals(mail.outboundAttachments.single().snapshot.sha256, outboundSha256Hex(downloaded))
        assertEquals(
            "attachment; filename*=UTF-8''%E4%BC%9A%E8%AE%AE%E8%B5%84%E6%96%99.txt",
            download.response.getHeader("Content-Disposition")
        )
        assertEquals("private,no-store", download.response.getHeader("Cache-Control"))
        assertEquals("nosniff", download.response.getHeader("X-Content-Type-Options"))
    }

    @Test
    fun `conversation followup entry carries attachments on the real sent anchor thread`() {
        val bytes = "会议资料-会话跟进".toByteArray(StandardCharsets.UTF_8)
        val uploaded = uploadAttachment(OWNER, CONTACT_ID, "跟进资料.txt", "text/plain", bytes)

        val result = pendingService.sendConversationManualRichReply(
            contactId = CONTACT_ID,
            requestId = REQUEST_ID,
            accountScope = null,
            subject = "Re: follow",
            htmlBody = "<p>follow</p>",
            textBody = "follow",
            operatorName = "display-op",
            attachmentIds = listOf(uploaded.id),
            authenticatedUsername = OWNER
        )

        assertEquals("SENT", result.sendStatus)
        // I-5：线程锚点语义不被附件改变（真实锚点的 Message-ID + References 链）
        val mail = sentMails.single()
        assertEquals("<anchor-1@test.com>", mail.inReplyTo)
        assertEquals("<anchor-old@test.com> <anchor-1@test.com>", mail.references)
        assertEquals(1, mail.outboundAttachments.size)
        assertArrayEquals(bytes, mail.outboundAttachments.single().bytes)
        val payload = claimedPayloads.single()
        assertEquals("MAIL_RECORD:$ANCHOR_RECORD_ID", payload.sourceAnchor)

        val recordId = requireNotNull(mailRecords.keys.singleOrNull())
        val item = timelineItems().single()
        assertEquals(1, item["outboundAttachments"].size())
        assertEquals(
            "/api/mail/conversations/$CONTACT_ID/messages/$recordId/outbound-attachments/" +
                "${uploaded.id}/download",
            item["outboundAttachments"][0]["downloadUrl"].asText()
        )
        val download = mockMvc.perform(
            get(item["outboundAttachments"][0]["downloadUrl"].asText()).session(sessionOf(OWNER))
        ).andExpect(status().isOk).andReturn()
        assertArrayEquals(bytes, download.response.contentAsByteArray)
    }

    // ------------------------------------------------------------------
    // I-1：越权/缺失/损坏/非法/超量一律 claim 前失败，且不写成 SMTP UNKNOWN
    // ------------------------------------------------------------------

    @Test
    fun `cross user and cross expert ids fail before claim with no smtp and no delivery classification`() {
        val bytes = "仅属于 op1".toByteArray(StandardCharsets.UTF_8)
        // 同一专家、另一上传者：归属校验必须按会话身份拒绝。
        val otherUploader = uploadAttachment(OTHER_OPERATOR, CONTACT_ID, "other.txt", "text/plain", bytes)
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, REQUEST_ID))
            .thenReturn(null)
        val crossUser = assertThrows(OutboundAttachmentException::class.java) {
            pendingService.sendConversationManualRichReply(
                contactId = CONTACT_ID, requestId = REQUEST_ID, accountScope = null,
                subject = "Re: follow", htmlBody = "<p>follow</p>", textBody = "follow",
                operatorName = null,
                attachmentIds = listOf(otherUploader.id), authenticatedUsername = OWNER
            )
        }
        assertEquals(HttpStatus.NOT_FOUND, crossUser.status)

        // 另一专家的附件（同上传者）在来信入口同样被拒绝。
        val foreignExpert = uploadAttachment(OWNER, OTHER_CONTACT_ID, "foreign.txt", "text/plain", bytes)
        val crossExpert = assertThrows(OutboundAttachmentException::class.java) {
            pendingService.sendManualRichReply(
                inboundProcessingId = INBOUND_ID, senderAccountCode = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test",
                operatorName = null,
                attachmentIds = listOf(foreignExpert.id), authenticatedUsername = OWNER
            )
        }
        assertEquals(HttpStatus.NOT_FOUND, crossExpert.status)

        // 未知 id、重复 id、总量超限
        val unknown = assertThrows(OutboundAttachmentException::class.java) {
            sendInboundWith(listOf(UUID.randomUUID().toString()))
        }
        assertEquals(HttpStatus.NOT_FOUND, unknown.status)
        val duplicate = assertThrows(OutboundAttachmentException::class.java) {
            sendInboundWith(listOf(otherUploader.id, otherUploader.id))
        }
        assertEquals(HttpStatus.BAD_REQUEST, duplicate.status)
        val tooLarge = assertThrows(OutboundAttachmentException::class.java) {
            sendInboundWith(List(11) { UUID.randomUUID().toString() })
        }
        assertEquals(HttpStatus.BAD_REQUEST, tooLarge.status)

        // 全部失败都发生在 claim 之前：0 attempt、0 SMTP，且绝不写成投递失败/UNKNOWN。
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).prepareAndClaim(anyValue(payloadStub()))
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).finalizeFailure(
            anyValue(payloadStub()), Mockito.anyLong(), Mockito.anyString(),
            Mockito.anyString(), anyValue(null)
        )
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `missing original file and metadata mismatch never turn into an smtp unknown attempt`() {
        val bytes = "原件会被删掉".toByteArray(StandardCharsets.UTF_8)
        val uploaded = uploadAttachment(OWNER, CONTACT_ID, "vanished.txt", "text/plain", bytes)

        // 发送前把原件移走（元数据仍在）：I-5 要求这是服务端读文件错误，不是 SMTP UNKNOWN。
        Files.delete(storageRoot.resolve("outbound").resolve(uploaded.id))
        val missing = assertThrows(OutboundAttachmentException::class.java) {
            sendInboundWith(listOf(uploaded.id))
        }
        assertEquals(HttpStatus.NOT_FOUND, missing.status)
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).prepareAndClaim(anyValue(payloadStub()))
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).finalizeFailure(
            anyValue(payloadStub()), Mockito.anyLong(), Mockito.anyString(),
            Mockito.anyString(), anyValue(null)
        )
        Mockito.verifyNoInteractions(mailDeliveryService)

        // 原件被替换（尺寸/hash 不符）：409，仍不 claim、不 SMTP。
        val replaced = uploadAttachment(OWNER, CONTACT_ID, "replaced.txt", "text/plain", bytes)
        Files.write(storageRoot.resolve("outbound").resolve(replaced.id), "tampered".toByteArray())
        val conflict = assertThrows(OutboundAttachmentException::class.java) {
            sendInboundWith(listOf(replaced.id))
        }
        assertEquals(HttpStatus.CONFLICT, conflict.status)
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).prepareAndClaim(anyValue(payloadStub()))
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `suppressed recipient still fails before any attempt even with attachments`() {
        val uploaded = uploadAttachment(OWNER, CONTACT_ID, "supp.txt", "text/plain", "x".toByteArray())
        Mockito.`when`(emailSuppressionService.isSuppressed(contact.expertEmail)).thenReturn(true)

        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            sendInboundWith(listOf(uploaded.id))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).prepareAndClaim(anyValue(payloadStub()))
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `unknown claim keeps the warning and never resends with attachments`() {
        val uploaded = uploadAttachment(OWNER, CONTACT_ID, "unknown.txt", "text/plain", "x".toByteArray())
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(payloadStub())))
            .thenReturn(
                ManualReplySendAttemptService.ClaimedAttempt(
                    attemptId = 1L,
                    messageId = MESSAGE_ID,
                    result = ManualReplySendAttemptService.ClaimResult.UNKNOWN
                )
            )

        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            sendInboundWith(listOf(uploaded.id))
        }
        assertEquals(HttpStatus.CONFLICT, ex.status)
        assertTrue(requireNotNull(ex.reason).startsWith("发送状态未知，请勿重复发送"))
        Mockito.verifyNoInteractions(mailDeliveryService)
        assertTrue(mailRecords.isEmpty(), "UNKNOWN 不落成功存档")
    }

    // ------------------------------------------------------------------
    // I-2：同 requestId 重提的附件语义收敛
    // ------------------------------------------------------------------

    @Test
    fun `resubmitting a sent requestId reuses the original record and rejects changed attachments`() {
        val bytes = "第一次发送".toByteArray(StandardCharsets.UTF_8)
        val uploaded = uploadAttachment(OWNER, CONTACT_ID, "first.txt", "text/plain", bytes)
        val first = pendingService.sendConversationManualRichReply(
            contactId = CONTACT_ID, requestId = REQUEST_ID, accountScope = null,
            subject = "Re: follow", htmlBody = "<p>follow</p>", textBody = "follow",
            operatorName = null,
            attachmentIds = listOf(uploaded.id), authenticatedUsername = OWNER
        )
        assertEquals("SENT", first.sendStatus)
        val recordId = requireNotNull(mailRecords.keys.singleOrNull())
        val sentRecord = requireNotNull(mailRecords[recordId])
        sentMails.clear()
        Mockito.clearInvocations(mailRecordRepository, manualReplySendAttemptService, mailDeliveryService)

        // 已完成 attempt 收敛：原记录 + 同一附件 → 返回原 SENT，不重选锚点、不 SMTP/claim
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, REQUEST_ID))
            .thenReturn(
                ManualReplySendAttemptService.CompletedOutboundReply(
                    attemptId = 1L,
                    attemptMessageId = MESSAGE_ID,
                    attemptAccountCode = ACCOUNT_CODE,
                    mailRecord = sentRecord
                )
            )
        val replay = pendingService.sendConversationManualRichReply(
            contactId = CONTACT_ID, requestId = REQUEST_ID, accountScope = null,
            subject = "Re: follow", htmlBody = "<p>follow</p>", textBody = "follow",
            operatorName = null,
            attachmentIds = listOf(uploaded.id), authenticatedUsername = OWNER
        )
        assertEquals("SENT", replay.sendStatus)
        assertEquals(MESSAGE_ID, replay.messageId)
        assertEquals(0, sentMails.size)
        Mockito.verify(mailRecordRepository, Mockito.never())
            .findLatestSentOutboundAnchor(Mockito.anyLong(), Mockito.any(), Mockito.anyString())
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).prepareAndClaim(anyValue(payloadStub()))

        // 换一个附件（内容不同）→ 04 专用 409，且不重投
        val other = uploadAttachment(OWNER, CONTACT_ID, "second.txt", "text/plain", "second".toByteArray())
        val changed = assertThrows(OutboundAttachmentException::class.java) {
            pendingService.sendConversationManualRichReply(
                contactId = CONTACT_ID, requestId = REQUEST_ID, accountScope = null,
                subject = "Re: follow", htmlBody = "<p>follow</p>", textBody = "follow",
                operatorName = null,
                attachmentIds = listOf(other.id), authenticatedUsername = OWNER
            )
        }
        assertEquals(HttpStatus.CONFLICT, changed.status)
        assertEquals("该请求已发送，附件与原请求不同，请发起新回复", changed.message)
        assertEquals(0, sentMails.size)
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(senderAccount()), anyValue(mailStub()))

        // 同 requestId 但本次不带附件 → 同样不同（原记录有附件）
        val removed = assertThrows(OutboundAttachmentException::class.java) {
            pendingService.sendConversationManualRichReply(
                contactId = CONTACT_ID, requestId = REQUEST_ID, accountScope = null,
                subject = "Re: follow", htmlBody = "<p>follow</p>", textBody = "follow",
                operatorName = null
            )
        }
        assertEquals(HttpStatus.CONFLICT, removed.status)
        assertEquals(0, sentMails.size)
    }

    // ------------------------------------------------------------------
    // I-4：已发下载的授权矩阵
    // ------------------------------------------------------------------

    @Test
    fun `sent download allows any logged in operator and disabled real account but rejects mismatches`() {
        val bytes = "下载授权矩阵".toByteArray(StandardCharsets.UTF_8)
        val uploaded = uploadAttachment(OWNER, CONTACT_ID, "acl.txt", "text/plain", bytes)
        pendingService.sendManualRichReply(
            inboundProcessingId = INBOUND_ID, senderAccountCode = null,
            subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test",
            operatorName = null, attachmentIds = listOf(uploaded.id), authenticatedUsername = OWNER
        )
        val recordId = requireNotNull(mailRecords.keys.singleOrNull())
        val url = "/api/mail/conversations/$CONTACT_ID/messages/$recordId/outbound-attachments/" +
            "${uploaded.id}/download"

        // 另一登录操作员：已发文件可见（禁用真实账号仍可读）
        val byOther = mockMvc.perform(get(url).session(sessionOf(OTHER_OPERATOR)))
            .andExpect(status().isOk).andReturn()
        assertArrayEquals(bytes, byOther.response.contentAsByteArray)
        // 匿名：401
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized)
        // 错专家 / 错消息 / 未引用的附件 id：统一 404
        mockMvc.perform(get(url.replace("/conversations/$CONTACT_ID/", "/conversations/$OTHER_CONTACT_ID/"))
            .session(sessionOf(OWNER))).andExpect(status().isNotFound)
        mockMvc.perform(
            get("/api/mail/conversations/$CONTACT_ID/messages/${recordId + 999}/outbound-attachments/" +
                "${uploaded.id}/download").session(sessionOf(OWNER))
        ).andExpect(status().isNotFound)
        val unreferenced = uploadAttachment(OWNER, CONTACT_ID, "unreferenced.txt", "text/plain", "u".toByteArray())
        mockMvc.perform(
            get("/api/mail/conversations/$CONTACT_ID/messages/$recordId/outbound-attachments/" +
                "${unreferenced.id}/download").session(sessionOf(OWNER))
        ).andExpect(status().isNotFound)

        // 模拟器账号存档：即使快照存在也不可下载（账号必须真实且非 SIMULATOR_NOOP）
        mailRecords[recordId] = requireNotNull(mailRecords[recordId])
            .copy(senderAccountCode = MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        mockMvc.perform(get(url).session(sessionOf(OWNER))).andExpect(status().isNotFound)

        // 草稿下载仍只限上传者：op2 不能下载 op1 的草稿（既有 04 边界不变）
        mockMvc.perform(
            get("/api/mail/conversations/$CONTACT_ID/outbound-attachments/${uploaded.id}/download")
                .session(sessionOf(OTHER_OPERATOR))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `sent download returns 404 for inbound only and failed rows and corrupt snapshots`() {
        val uploaded = uploadAttachment(OWNER, CONTACT_ID, "rows.txt", "text/plain", "r".toByteArray())
        pendingService.sendManualRichReply(
            inboundProcessingId = INBOUND_ID, senderAccountCode = null,
            subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test",
            operatorName = null, attachmentIds = listOf(uploaded.id), authenticatedUsername = OWNER
        )
        val recordId = requireNotNull(mailRecords.keys.singleOrNull())
        val original = requireNotNull(mailRecords[recordId])
        val url = "/api/mail/conversations/$CONTACT_ID/messages/$recordId/outbound-attachments/" +
            "${uploaded.id}/download"

        // 非 SENT：不可下载
        mailRecords[recordId] = original.copy(sendStatus = "FAILED")
        mockMvc.perform(get(url).session(sessionOf(OWNER))).andExpect(status().isNotFound)

        // 非人工富文本（自动/模板群发）：不下载
        mailRecords[recordId] = original.copy(mailType = "INTRODUCTION")
        mockMvc.perform(get(url).session(sessionOf(OWNER))).andExpect(status().isNotFound)

        // 快照损坏：404（且 timeline 该行空列表，不 500）
        mailRecords[recordId] = original.copy(outboundAttachmentsJson = "{broken")
        mockMvc.perform(get(url).session(sessionOf(OWNER))).andExpect(status().isNotFound)
        assertTrue(timelineItems().single()["outboundAttachments"].isEmpty())

        // 恢复后仍可下载（证明前面失败只因该行不满足条件）
        mailRecords[recordId] = original
        mockMvc.perform(get(url).session(sessionOf(OWNER))).andExpect(status().isOk)
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 04 上传端点（真实 multipart 语义 + 真实存储写入）。 */
    private fun uploadAttachment(
        username: String,
        contactId: Long,
        filename: String,
        contentType: String,
        bytes: ByteArray
    ): OutboundAttachmentUploadResponse {
        val result = mockMvc.perform(
            multipart("/api/mail/conversations/$contactId/outbound-attachments")
                .file(MockMultipartFile("file", filename, contentType, bytes))
                .session(sessionOf(username))
        ).andExpect(status().isCreated).andReturn()
        // MockHttpServletResponse 默认按 ISO-8859-1 读字符串，这里按 UTF-8 字节解析；
        // Kotlin data class 反序列化需要 KotlinModule（生产由 Spring Boot 自动注册）。
        val body = String(result.response.contentAsByteArray, StandardCharsets.UTF_8)
        return kotlinMapper.readValue(body, OutboundAttachmentUploadResponse::class.java)
    }

    private fun sendInboundWith(attachmentIds: List<String>): PendingMailSendResult =
        pendingService.sendManualRichReply(
            inboundProcessingId = INBOUND_ID,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>Test</p>",
            textBody = "Test",
            operatorName = null,
            attachmentIds = attachmentIds,
            authenticatedUsername = OWNER
        )

    /** 与 05 finalize 同款：成功存档写入 payload 的通用附件快照（真实 codec），空即 null。 */
    private fun persistSentRecord(payload: ManualReplySendAttemptService.SendPayload): Long {
        val id = nextRecordId++
        mailRecords[id] = MailRecord(
            id = id,
            expertContactId = payload.contactId,
            direction = "OUTBOUND",
            mailType = "MANUAL_RICH_REPLY",
            senderAccountCode = payload.accountCode,
            messageId = MESSAGE_ID,
            inReplyTo = payload.inReplyTo,
            subject = payload.subject,
            body = payload.finalHtml,
            matchedQaRuleId = payload.primaryRuleId,
            sendStatus = "SENT",
            receivedAt = null,
            sentAt = LocalDateTime.now(),
            outboundAttachmentsJson = payload.outboundAttachments
                .takeIf { it.isNotEmpty() }
                ?.let { OutboundAttachmentSnapshotCodec.serialize(it) }
        )
        claimedPayloads += payload
        return id
    }

    /** 真实 [MailboxConversationService] 的时间线投影（只 mock SQL 仓库行与附件解析边界）。 */
    private fun timelineItems(): List<com.fasterxml.jackson.databind.JsonNode> {
        val rows = mailRecords.values.map { record ->
            MailboxConversationRepository.ConversationMessageSqlRow(
                source = MailboxConversationRepository.SOURCE_MAIL_RECORD,
                id = requireNotNull(record.id),
                expertContactId = record.expertContactId,
                direction = record.direction,
                accountCode = record.senderAccountCode,
                subject = record.subject,
                body = record.body,
                cleanedBody = null,
                eventAt = LocalDateTime.now(),
                sendStatus = record.sendStatus,
                processStatus = null,
                messageId = record.messageId,
                inReplyTo = record.inReplyTo
            )
        }
        Mockito.`when`(
            mailboxConversationRepository.timelineMessages(
                eqValue(CONTACT_ID),
                anyValue(MailboxConversationRepository.ConversationFilter(accountCodes = emptyList())),
                Mockito.any(),
                Mockito.anyInt()
            )
        ).thenReturn(MailboxConversationRepository.TimelinePage(rows, false))
        val response = conversationService.listMessages(CONTACT_ID, null, null, 50)
        val mapper = ObjectMapper()
        return response.items.map { mapper.valueToTree(it) }
    }

    private fun sessionOf(username: String): MockHttpSession =
        MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }

    private fun adminUser(username: String): AdminUser =
        AdminUser(
            username = username,
            passwordHash = "not-checked",
            mustChangePassword = false,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

    private fun attachmentSample() = OutboundMailAttachment(
        id = "00000000-0000-0000-0000-000000000000",
        expertContactId = CONTACT_ID,
        createdBy = OWNER,
        fileName = "sample.txt",
        contentType = "text/plain",
        byteLength = 0L,
        sha256 = "0".repeat(64),
        createdAt = LocalDateTime.now()
    )

    private fun inbound() = InboundMailProcessing(
        id = INBOUND_ID,
        senderAccountCode = ACCOUNT_CODE,
        imapUid = 1L,
        messageId = "in-1",
        fromEmail = "expert@test.com",
        subject = "Question",
        body = "Can I work remotely?",
        cleanedBody = "Can I work remotely?",
        receivedAt = LocalDateTime.now(),
        processStatus = "MANUAL_REVIEW",
        processReason = "QA_NO_MATCH",
        expertContactId = CONTACT_ID
    )

    private fun sentAnchor() = MailRecord(
        id = ANCHOR_RECORD_ID,
        expertContactId = CONTACT_ID,
        direction = "OUTBOUND",
        mailType = "INTRODUCTION",
        senderAccountCode = ACCOUNT_CODE,
        messageId = "<anchor-1@test.com>",
        inReplyTo = "<anchor-old@test.com>",
        subject = "Introduction",
        body = "intro",
        matchedQaRuleId = null,
        sendStatus = "SENT",
        receivedAt = null,
        sentAt = LocalDateTime.now()
    )

    private fun senderAccount(
        accountCode: String = ACCOUNT_CODE,
        enabled: Boolean = true
    ) = MailSenderAccount(
        accountCode = accountCode,
        senderEmail = "$accountCode@test.com",
        senderName = "Sender",
        senderTitle = "Title",
        senderDisplayName = "Sender",
        teamName = "Team",
        countryName = "CN",
        smtpHost = "smtp.test.com",
        smtpPort = 465,
        smtpUsername = "u",
        smtpPassword = "p",
        imapHost = "imap.test.com",
        imapPort = 993,
        imapUsername = "u",
        imapPassword = "p",
        enabled = enabled
    )

    /** Kotlin 非空形参占位：Mockito.any() 返回 Java null，必须用真实实例兜底。 */
    private fun payloadStub() = ManualReplySendAttemptService.SendPayload(
        orcidId = contact.orcidId,
        contactId = CONTACT_ID,
        inboundProcessingId = INBOUND_ID,
        accountCode = ACCOUNT_CODE,
        normalizedRecipient = contact.expertEmail,
        subject = "Re: Test",
        finalText = "Test",
        finalHtml = "<p>Test</p>",
        inReplyTo = "in-1",
        canonicalQaRuleIds = emptyList(),
        primaryRuleId = null
    )

    private fun mailStub() = ComposedMail(
        to = contact.expertEmail,
        subject = "Re: Test",
        body = "<p>Test</p>",
        html = true,
        text = "Test",
        messageId = MESSAGE_ID
    )

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private companion object {
        const val CONTACT_ID = 1L
        const val OTHER_CONTACT_ID = 2L
        const val INBOUND_ID = 100L
        const val ANCHOR_RECORD_ID = 2893L
        const val ACCOUNT_CODE = "acc-a"
        const val OWNER = "op1"
        const val OTHER_OPERATOR = "op2"
        const val REQUEST_ID = "b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f"
        const val MESSAGE_ID = "<manual-rich-abc@weibo.com>"
    }
}
