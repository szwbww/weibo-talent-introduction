package com.weibo.talentintroduction.auth.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.auth.controller.AuthController
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.controller.BounceController
import com.weibo.talentintroduction.mail.controller.CalendarAttachmentController
import com.weibo.talentintroduction.mail.controller.ExpertContactNoteController
import com.weibo.talentintroduction.mail.controller.InboundMailSummaryController
import com.weibo.talentintroduction.mail.controller.MailReplyDraftController
import com.weibo.talentintroduction.mail.controller.MailSenderAccountController
import com.weibo.talentintroduction.mail.controller.MailboxAttachmentController
import com.weibo.talentintroduction.mail.controller.MailboxController
import com.weibo.talentintroduction.mail.controller.MailboxConversationController
import com.weibo.talentintroduction.mail.controller.OutboundAttachmentController
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.BounceBackfillService
import com.weibo.talentintroduction.mail.service.BounceRateMonitorService
import com.weibo.talentintroduction.mail.service.ExpertContactNoteService
import com.weibo.talentintroduction.mail.service.ExpertFollowService
import com.weibo.talentintroduction.mail.service.ExpertRepliedDismissalService
import com.weibo.talentintroduction.mail.service.InboundMailTagService
import com.weibo.talentintroduction.mail.service.MailAccountConnectivityService
import com.weibo.talentintroduction.mail.service.MailReplyDraftService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailboxAttachmentService
import com.weibo.talentintroduction.mail.service.MailboxConversationService
import com.weibo.talentintroduction.mail.service.MailboxService
import com.weibo.talentintroduction.mail.service.MailboxSuspensionService
import com.weibo.talentintroduction.mail.service.OutboundAttachmentService
import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.mail.service.SenderAccountSelfCheckService
import com.weibo.talentintroduction.mail.controller.ConversationListResponse
import com.weibo.talentintroduction.mail.controller.ConversationMessageListResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.Import
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.HandlerMethod
import java.lang.reflect.Method
import java.time.LocalDateTime

@RestController
class AuthProbeController {
    @GetMapping("/api/auth-probe")
    fun probe() = mapOf("ok" to true)
}

@WebMvcTest(
    controllers = [
        AuthProbeController::class,
        AuthController::class,
        MailboxConversationController::class,
        MailboxController::class,
        MailReplyDraftController::class,
        MailboxAttachmentController::class,
        OutboundAttachmentController::class,
        CalendarAttachmentController::class,
        MailSenderAccountController::class,
        ExpertContactNoteController::class,
        InboundMailSummaryController::class,
        BounceController::class
    ]
)
@Import(AuthWebConfig::class, AuthInterceptorTest.KotlinObjectMapperConfig::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class AuthInterceptorTest {
    @MockBean
    private lateinit var resourceGuard: com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard

    @org.junit.jupiter.api.BeforeEach
    fun denyUnassignedResourceOperations() {
        Mockito.doAnswer {
            throw MailReadAccessDeniedException(
                com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard.MAIL_WRITE_FORBIDDEN,
                "无权操作该专家的邮件资源"
            )
        }.`when`(resourceGuard).authorize(
            Mockito.anyString(), Mockito.any() ?: Any(),
            Mockito.any(javax.servlet.http.HttpServletRequest::class.java) ?: org.springframework.mock.web.MockHttpServletRequest()
        )
    }

    /** 解析 Kotlin data class @RequestBody 需要 KotlinModule（生产由 Boot 注册）。 */
    @TestConfiguration
    class KotlinObjectMapperConfig {
        @Bean
        fun objectMapper(): ObjectMapper =
            ObjectMapper()
                .registerModule(KotlinModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var authService: AuthService

    @MockBean
    private lateinit var conversationService: MailboxConversationService

    @MockBean
    private lateinit var expertFollowService: ExpertFollowService

    @MockBean
    private lateinit var expertRepliedDismissalService: ExpertRepliedDismissalService

    @MockBean
    private lateinit var pendingMailOperationService: PendingMailOperationService

    @MockBean
    private lateinit var mailboxSuspensionService: MailboxSuspensionService

    @MockBean
    private lateinit var mailboxService: MailboxService

    @MockBean
    private lateinit var mailReplyDraftService: MailReplyDraftService

    @MockBean
    private lateinit var mailboxAttachmentService: MailboxAttachmentService

    @MockBean
    private lateinit var outboundAttachmentService: OutboundAttachmentService

    @MockBean
    private lateinit var mailSenderAccountService: MailSenderAccountService

    @MockBean
    private lateinit var mailAccountConnectivityService: MailAccountConnectivityService

    @MockBean
    private lateinit var senderAccountSelfCheckService: SenderAccountSelfCheckService

    @MockBean
    private lateinit var bounceRateMonitorService: BounceRateMonitorService

    @MockBean
    private lateinit var expertContactNoteService: ExpertContactNoteService

    @MockBean
    private lateinit var inboundMailProcessingRepository: InboundMailProcessingRepository

    @MockBean
    private lateinit var inboundMailTagService: InboundMailTagService
    @MockBean
    private lateinit var inboundMailTagRepository: com.weibo.talentintroduction.mail.repository.InboundMailTagRepository

    @MockBean
    private lateinit var bounceRecordRepository: BounceRecordRepository

    @MockBean
    private lateinit var bounceBackfillService: BounceBackfillService

    @MockBean
    private lateinit var operatorActionLogService: OperatorActionLogService

    @MockBean
    private lateinit var expertContactRepository: ExpertContactRepository

    @MockBean
    private lateinit var mailRecordRepository: MailRecordRepository

    @MockBean
    private lateinit var mailSenderAccountRepository: MailSenderAccountRepository

    @Test
    fun `OPTIONS request is allowed without authentication`() {
        mockMvc.perform(options("/api/auth-probe"))
            .andExpect(status().isOk)
    }

    @Test
    fun `request without session returns 401 with standard JSON format`() {
        mockMvc.perform(get("/api/auth-probe"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("未登录"))
            .andExpect(jsonPath("$.detail").isEmpty)
    }

    @Test
    fun `anonymous business mail request returns 401`() {
        mockMvc.perform(get("/api/mail/mailbox/conversations"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `request to change-password without session returns 401`() {
        mockMvc.perform(post("/api/auth/change-password"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("未登录"))
    }

    @Test
    fun `request with session but user not in DB invalidates session and returns 401`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        Mockito.`when`(authService.findUser("admin")).thenReturn(null)

        val result = mockMvc.perform(get("/api/auth-probe").session(session))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andReturn()

        assertTrue(result.request.getSession(false) == null || (result.request.getSession(false) as MockHttpSession).isInvalid)
    }

    @Test
    fun `request with session when password change is required returns 403`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = "hash",
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(authService.findUser("admin")).thenReturn(user)

        mockMvc.perform(get("/api/auth-probe").session(session))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"))
            .andExpect(jsonPath("$.message").value("首次登录请先修改密码"))
            .andExpect(jsonPath("$.detail").isEmpty)
    }

    @Test
    fun `request with session when password change is required allows exempted endpoints`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = "hash",
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(authService.findUser("admin")).thenReturn(user)

        // /api/auth/me is excluded in AuthWebConfig interceptor mapping → real AuthController responds.
        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.mustChangePassword").value(true))

        // /api/auth/change-password matches /api/** but is explicitly exempted in the interceptor logic itself.
        mockMvc.perform(
            post("/api/auth/change-password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"old","newPassword":"new_password"}""")
        )
            .andExpect(status().isNoContent)

        // /api/auth/logout goes through preHandle but the interceptor allows it when mustChangePassword=true.
        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `request with session after password change has succeeded returns 200`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = "hash",
            mustChangePassword = false,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(authService.findUser("admin")).thenReturn(user)

        mockMvc.perform(get("/api/auth-probe").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
    }

    @Test
    fun `POST logout without session returns 401 UNAUTHORIZED`() {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("未登录"))
            .andExpect(jsonPath("$.detail").isEmpty)
    }

    // ------------------------------------------------------------------
    // I-1：精确 user1 默认拒绝，只有逐项登记的 handler 可访问。
    // ------------------------------------------------------------------

    @Test
    fun `non reader identities keep existing behaviour`() {
        // 只有精确 user1 被限制；其它非 admin 测试身份沿用原行为（业务授权不在本步范围）。
        val session = sessionOf("op1")
        mockMvc.perform(get("/api/auth-probe").session(session))
            .andExpect(status().isOk)
    }

    @Test
    fun `reader cannot access non allowlisted handler`() {
        mockMvc.perform(get("/api/auth-probe").session(sessionOf("user1")))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.READ_ONLY_FORBIDDEN))
            .andExpect(jsonPath("$.detail").isEmpty)
    }

    @Test
    fun `reader cannot open change-password even though password rule exempts it`() {
        mockMvc.perform(
            post("/api/auth/change-password")
                .session(sessionOf("user1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"old","newPassword":"new_password"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.READ_ONLY_FORBIDDEN))

        // 拦截器只做了 findUser 身份校验；改密服务绝不能被调用。
        Mockito.verify(authService, Mockito.never())
            .changePassword(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())
    }

    @Test
    fun `reader is allowed on conversation list and messages`() {
        Mockito.`when`(
            conversationService.listConversations(
                username = "user1",
                q = null,
                followed = false,
                repliedOnly = false,
                pendingOnly = false,
                waitingReply = false,
                accountCode = null,
                direction = null,
                startDate = null,
                endDate = null,
                subject = null,
                label = null,
                recipientEmail = null,
                keyword = null,
                page = 0,
                size = 20,
                suspendedOnly = false,
                providedOnly = false
            )
        ).thenReturn(ConversationListResponse(emptyList(), 0, 0, 20))
        Mockito.`when`(
            // 02 (I-2)：Controller 从 session 显式传身份，stub 参数同步为 5 个（断言语义不变）。
            conversationService.listMessages(
                contactId = 1L,
                accountCode = null,
                beforeCursor = null,
                limit = 50,
                username = MailReadOnlyPolicy.READER_USERNAME
            )
        ).thenReturn(ConversationMessageListResponse(emptyList(), null, false))

        val session = sessionOf("user1")
        mockMvc.perform(get("/api/mail/mailbox/conversations").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))

        mockMvc.perform(get("/api/mail/mailbox/conversations/1/messages").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hasMore").value(false))
    }

    @Test
    fun `reader is allowed to logout`() {
        mockMvc.perform(post("/api/auth/logout").session(sessionOf("user1")))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `reader is denied on representative protected endpoints without touching services`() {
        val session = sessionOf("user1")

        // 旧 mailbox / 草稿 / 附件 / 发件账号 / 备注 / 标签 / 监控
        mockMvc.perform(get("/api/mail/mailbox").session(session)).andExpect(status().isForbidden)
        mockMvc.perform(get("/api/mail/mailbox/by-expert").session(session)).andExpect(status().isForbidden)
        mockMvc.perform(get("/api/mail/mailbox/MAIL_RECORD/1/attachments").session(session))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/mail/mailbox/attachments/1/download").session(session))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/mail/conversations/1/outbound-attachments/1/download").session(session))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/mail/conversations/1/messages/1/calendar-attachment").session(session))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/mail/sender-accounts").session(session)).andExpect(status().isForbidden)
        mockMvc.perform(
            put("/api/mail/contact-notes/1")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"note":"x"}""")
        ).andExpect(status().isForbidden)
        mockMvc.perform(get("/api/inbound-summary/mails").session(session)).andExpect(status().isForbidden)
        mockMvc.perform(get("/api/mail/bounces").session(session)).andExpect(status().isForbidden)

        // 发信 / 关注 / 挂起 / 备注 / 标签 / 监控：业务服务零交互。
        Mockito.verifyNoInteractions(mailboxService)
        Mockito.verifyNoInteractions(mailReplyDraftService)
        Mockito.verifyNoInteractions(mailboxAttachmentService)
        Mockito.verifyNoInteractions(outboundAttachmentService)
        Mockito.verifyNoInteractions(mailSenderAccountService)
        Mockito.verifyNoInteractions(expertContactNoteService)
        Mockito.verifyNoInteractions(inboundMailProcessingRepository)
        Mockito.verifyNoInteractions(inboundMailTagService)
        Mockito.verifyNoInteractions(bounceRecordRepository)
        Mockito.verifyNoInteractions(bounceBackfillService)
        Mockito.verifyNoInteractions(operatorActionLogService)
    }

    @Test
    fun `reader is denied on conversation write endpoints without service interaction`() {
        val session = sessionOf("user1")

        mockMvc.perform(put("/api/mail/mailbox/conversations/1/follow").session(session))
            .andExpect(status().isForbidden)
        mockMvc.perform(delete("/api/mail/mailbox/conversations/1/follow").session(session))
            .andExpect(status().isForbidden)
        mockMvc.perform(
            put("/api/mail/mailbox/conversations/1/progress-status")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"FOLLOWING"}""")
        ).andExpect(status().isForbidden)
        mockMvc.perform(put("/api/mail/mailbox/conversations/1/replied-dismissal").session(session))
            .andExpect(status().isForbidden)
        mockMvc.perform(put("/api/mail/mailbox/conversations/1/suspension").session(session))
            .andExpect(status().isForbidden)
        mockMvc.perform(patch("/api/mail/mailbox/conversations/1/suspension/reason").session(session))
            .andExpect(status().isForbidden)
        mockMvc.perform(delete("/api/mail/mailbox/conversations/1/suspension").session(session))
            .andExpect(status().isForbidden)
        mockMvc.perform(
            post("/api/mail/mailbox/conversations/1/manual-rich-reply")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"requestId":"r1","accountScope":"","anchorMailRecordId":1,""" +
                        """"subject":"s","htmlBody":"<p>b</p>"}"""
                )
        ).andExpect(status().isForbidden)

        Mockito.verifyNoInteractions(expertFollowService)
        Mockito.verifyNoInteractions(expertRepliedDismissalService)
        Mockito.verifyNoInteractions(mailboxSuspensionService)
        Mockito.verifyNoInteractions(pendingMailOperationService)
    }

    // ------------------------------------------------------------------
    // 白名单必须与真实映射绑定：扫描全部 @RestController 的真实方法，
    // 仅精确 HandlerMethod/HTTP verb 配对可通过（不是 URL 前缀）。
    // ------------------------------------------------------------------

    @Test
    fun `reader allowlist matches exactly the approved real mapped handler methods`() {
        val allowed = sortedSetOf<String>()
        var mappedMethodCount = 0

        scanRestControllers().forEach { controllerType ->
            controllerType.declaredMethods.forEach { method ->
                val verbs = httpMethodsOf(method)
                if (verbs.isEmpty()) return@forEach
                mappedMethodCount++
                verbs.forEach { verb ->
                    if (MailReadOnlyPolicy.isReaderAllowed(HandlerMethod(Any(), method), verb)) {
                        allowed += "${controllerType.simpleName}#${method.name}:$verb"
                    }
                }
            }
        }

        assertTrue(mappedMethodCount >= 100, "扫描应覆盖真实映射方法（实际 $mappedMethodCount）")
        assertEquals(
            listOf(
                "AuthController#logout:POST",
                "CalendarAttachmentController#download:GET",
                "ExpertContactLocationController#countries:GET",
                "ExpertContactLocationController#get:GET",
                "ExpertContactLocationController#save:PUT",
                "ExpertContactLocationController#timing:GET",
                "ExpertContactManagementController#changeIndexLevel:POST",
                "ExpertContactManagementController#changeOperatorStatus:POST",
                "ExpertContactManagementController#getContactDetail:GET",
                "ExpertContactNoteController#get:GET",
                "ExpertContactNoteController#save:PUT",
                "ExpertDocumentBrowseController#download:GET",
                "ExpertDocumentBrowseController#listDocuments:GET",
                "ExpertDocumentBrowseController#preview:GET",
                "ExpertIndexController#addTag:POST",
                "ExpertIndexController#getExpertProfile:GET",
                "ExpertIndexController#removeTag:POST",
                "ExpertManualProfileController#get:GET",
                "ExpertManualProfileController#patch:PATCH",
                "ExpertMaterialController#downloadZip:POST",
                "ExpertMaterialController#listMaterials:GET",
                "ExpertMaterialController#reconcile:POST",
                "ExpertMaterialController#requestTransfers:POST",
                "ExpertMaterialController#uploadMaterial:POST",
                "InboundMailSummaryController#addTag:POST",
                "InboundMailSummaryController#autoApplyTags:POST",
                "InboundMailSummaryController#deleteTag:DELETE",
                "InboundMailSummaryController#tagOptions:GET",
                "MailReplyDraftController#detail:GET",
                "MailReplyDraftController#discard:DELETE",
                "MailReplyDraftController#list:GET",
                "MailReplyDraftController#save:PUT",
                "MailReplyDraftController#summaries:GET",
                "MailReplyDraftController#target:GET",
                "MailReviewDraftController#comment:POST",
                "MailReviewDraftController#create:POST",
                "MailReviewDraftController#delete:DELETE",
                "MailReviewDraftController#detail:GET",
                "MailReviewDraftController#events:GET",
                "MailReviewDraftController#list:GET",
                "MailReviewDraftController#save:PUT",
                "MailReviewDraftController#submit:POST",
                "MailReviewDraftController#withdraw:POST",
                "MailboxAttachmentController#download:GET",
                "MailboxAttachmentController#listAttachments:GET",
                "MailboxConversationController#conversationManualRichReply:POST",
                "MailboxConversationController#dismissReplied:PUT",
                "MailboxConversationController#follow:PUT",
                "MailboxConversationController#list:GET",
                "MailboxConversationController#messages:GET",
                "MailboxConversationController#setProgressStatus:PUT",
                "MailboxConversationController#unfollow:DELETE",
                "MeetingCalendarController#cancel:POST",
                "MeetingCalendarController#create:POST",
                "MeetingCalendarController#event:GET",
                "MeetingCalendarController#events:GET",
                "MeetingCalendarController#summaries:GET",
                "MeetingCalendarController#update:PUT",
                "MeetingConfirmationController#options:GET",
                "MeetingConfirmationController#preview:POST",
                "MeetingConfirmationController#timeZones:GET",
                "OutboundAttachmentController#downloadDraft:GET",
                "OutboundAttachmentController#downloadSentAttachment:GET",
                "OutboundAttachmentController#upload:POST",
                "TranslationController#translate:POST",
                "UnmatchedInboundMailController#cancelResolved:POST",
                "UnmatchedInboundMailController#getUnmatchedDetail:GET",
                "UnmatchedInboundMailController#markResolved:POST",
                "UnmatchedInboundMailController#sendManualRichReply:POST"
            ),
            allowed.toList()
        )
    }

    @Test
    fun `allowed handler method is rejected under a different HTTP method`() {
        val listMethod = MailboxConversationController::class.java.declaredMethods
            .single { it.name == "list" }
        val messagesMethod = MailboxConversationController::class.java.declaredMethods
            .single { it.name == "messages" }

        assertTrue(MailReadOnlyPolicy.isReaderAllowed(HandlerMethod(Any(), listMethod), "GET"))
        assertFalse(MailReadOnlyPolicy.isReaderAllowed(HandlerMethod(Any(), listMethod), "POST"))
        assertFalse(MailReadOnlyPolicy.isReaderAllowed(HandlerMethod(Any(), listMethod), "PUT"))
        assertTrue(MailReadOnlyPolicy.isReaderAllowed(HandlerMethod(Any(), messagesMethod), "GET"))
        assertFalse(MailReadOnlyPolicy.isReaderAllowed(HandlerMethod(Any(), messagesMethod), "DELETE"))
    }

    @Test
    fun `non handler and OPTIONS handling follow the fixed policy`() {
        assertFalse(MailReadOnlyPolicy.isReaderAllowed(null, "GET"))
        assertFalse(MailReadOnlyPolicy.isReaderAllowed("not-a-handler", "GET"))
        assertTrue(MailReadOnlyPolicy.isReaderAllowed(null, "OPTIONS"))
        assertTrue(MailReadOnlyPolicy.isReader("user1"))
        assertFalse(MailReadOnlyPolicy.isReader("User1"))
        assertFalse(MailReadOnlyPolicy.isReader(" user1"))
        assertFalse(MailReadOnlyPolicy.isReader("admin"))
        assertFalse(MailReadOnlyPolicy.isReader(null))
    }

    private fun sessionOf(username: String): MockHttpSession {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, username)
        Mockito.`when`(authService.findUser(username)).thenReturn(
            AdminUser(
                id = 1L,
                username = username,
                passwordHash = "hash",
                mustChangePassword = false,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )
        return session
    }

    private fun scanRestControllers(): List<Class<*>> {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
        return scanner.findCandidateComponents("com.weibo.talentintroduction")
            .mapNotNull { definition ->
                val className = definition.beanClassName ?: return@mapNotNull null
                runCatching {
                    Class.forName(className, false, AuthInterceptorTest::class.java.classLoader)
                }.getOrNull()
            }
    }

    private fun httpMethodsOf(method: Method): List<String> {
        val verbs = mutableListOf<String>()
        if (method.isAnnotationPresent(GetMapping::class.java)) verbs += "GET"
        if (method.isAnnotationPresent(PostMapping::class.java)) verbs += "POST"
        if (method.isAnnotationPresent(PutMapping::class.java)) verbs += "PUT"
        if (method.isAnnotationPresent(DeleteMapping::class.java)) verbs += "DELETE"
        if (method.isAnnotationPresent(PatchMapping::class.java)) verbs += "PATCH"
        return verbs
    }
}
