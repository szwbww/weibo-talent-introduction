package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.common.controller.ApiErrorResponse
import com.weibo.talentintroduction.mail.service.ExpertFollowService
import com.weibo.talentintroduction.mail.service.MailboxConversationService
import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.mail.service.PendingMailSendResult
import com.weibo.talentintroduction.mail.service.TagView
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

// ---------------------------------------------------------------------------
// 会话 summary/timeline API DTO（fast-p 07；与 00 总计划 API 契约逐字对齐）
// ---------------------------------------------------------------------------

data class ConversationLatestMessageItem(
    val source: String,
    val id: Long,
    val direction: String,
    val subject: String?,
    val preview: String?,
    val time: String,
    val sendStatus: String?
)

data class ConversationLatestInboundItem(
    val processingId: Long,
    val accountCode: String,
    val messageId: String?,
    val receivedAt: String
)

/**
 * 会话 summary 项：只含聚合口径与最新消息投影，绝不携带全量正文；
 * latestInbound.processingId 是真实 inbound_mail_processing.id（供可信工作台），
 * 绝不从 mail_record 推算。
 * expertTags：本页该专家的 ES 画像标签（I-6）。null = 未取得有效画像结果
 * （无 ORCID/层级非法/画像缺失/该层 ES 查询异常，前端不得显示为空）；[] = 画像已读取
 * 且确实没有标签。与消息 timeline.tags（邮件标签）完全分离。
 */
data class ConversationItemResponse(
    val contactId: Long,
    val name: String?,
    val email: String,
    val orcid: String,
    /** expert_contact 无机构列（DB 无 institution 存储）；恒 null，UI 自专家资料流程补充。 */
    val institution: String?,
    val accountCodes: List<String>,
    val followed: Boolean,
    val receivedCount: Long,
    val sentCount: Long,
    val failedCount: Long,
    val pendingCount: Long,
    val waitingReply: Boolean,
    val latestMessage: ConversationLatestMessageItem?,
    val latestInbound: ConversationLatestInboundItem?,
    val materialCount: Long,
    val expertTags: List<String>? = null
)

/**
 * 会话级人工回信请求（无来信路径，T3/I-4/I-6）：携带前端生成并随草稿保存的 requestId
 * （attempt 短键来源）与可空 accountScope（只约束服务端锚点查询，不直接决定发件账号）；
 * 不接收 senderAccountCode、qaRuleIds、RAG 或 assembly 字段 —— 联系人与锚点校验全部在
 * service，避免前端成为权限边界。
 */
data class ConversationManualRichReplyRequest(
    val requestId: String,
    val accountScope: String? = null,
    val subject: String,
    val htmlBody: String,
    val textBody: String? = null,
    val operatorName: String? = null,
    val safetyWarningConfirmed: Boolean = false,
    val strongConfirmationText: String? = null
)

data class ConversationListResponse(
    val items: List<ConversationItemResponse>,
    val total: Long,
    val page: Int,
    val size: Int
)

data class ConversationMessageItemResponse(
    val source: String,
    val id: Long,
    val contactId: Long,
    val direction: String,
    val accountCode: String?,
    val subject: String?,
    val body: String?,
    val cleanedBody: String?,
    val eventAt: String,
    val sendStatus: String?,
    val processStatus: String?,
    val attachmentCount: Int,
    val firstAttachmentNames: List<String>,
    val messageId: String?,
    val inReplyTo: String?,
    /**
     * 当前窗口真实邮件标签（I-3）：只有 INBOUND_PROCESSING 消息填充（[TagView] 直出，
     * child 02 不再逐封请求 /thread）；OUTBOUND 恒为空数组——绝不按 source_inbound_id
     * 或数值巧合映射。
     */
    val tags: List<TagView> = emptyList(),
    /** 03 (I-4)：单独日历附件元数据；末尾可空默认 null 保持既有构造点不变。 */
    val calendarAttachment: ConversationCalendarAttachment? = null
)

data class ConversationMessageListResponse(
    val items: List<ConversationMessageItemResponse>,
    val nextBefore: String?,
    val hasMore: Boolean
)

/**
 * 03 (I-4)：单独日历附件元数据（独立于专家材料的 attachmentCount/firstAttachmentNames）。
 * 只可能出现在 MAIL_RECORD+OUTBOUND+SENT 行；downloadUrl 指向 GET calendar-attachment
 * （原件字节与预览/SMTP 相同，child 04 不重新渲染）。旧行/非 SENT/损坏快照恒为 null。
 */
data class ConversationCalendarAttachment(
    val filename: String,
    val byteLength: Int,
    val downloadUrl: String
)

/**
 * 专家会话查询与关注（fast-p 07）。身份沿现有 Session（AUTH_USERNAME）：
 * 关注只属于当前登录用户（I-3），body 永不携带 username；PUT 置 true / DELETE 置
 * false 均幂等。GET 仅读 MySQL（summary 先 DB 聚合专家再分页；timeline 只加载当前
 * 专家），绝不调用 IMAP。未登录由 AuthInterceptor 拦截（所有 /api/ 路径），此处对缺登录的
 * 关注写入再作 401 防御（auth 关闭的测试/非生产上下文亦不静默放行）。
 */
@RestController
@RequestMapping("/api/mail/mailbox/conversations")
class MailboxConversationController(
    private val conversationService: MailboxConversationService,
    private val expertFollowService: ExpertFollowService,
    private val pendingMailOperationService: PendingMailOperationService
) {
    @GetMapping
    fun list(
        request: HttpServletRequest,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "false") followed: Boolean,
        @RequestParam(defaultValue = "false") pendingOnly: Boolean,
        @RequestParam(defaultValue = "false") waitingReply: Boolean,
        @RequestParam(required = false) accountCode: String?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false) subject: String?,
        @RequestParam(required = false) label: String?,
        @RequestParam(required = false) recipientEmail: String?,
        @RequestParam(required = false) keyword: String?
    ): ConversationListResponse = conversationService.listConversations(
        username = sessionUsername(request),
        q = q,
        followed = followed,
        pendingOnly = pendingOnly,
        waitingReply = waitingReply,
        accountCode = accountCode,
        direction = direction,
        startDate = conversationService.parseDate(startDate, "startDate"),
        endDate = conversationService.parseDate(endDate, "endDate"),
        subject = subject,
        label = label,
        recipientEmail = recipientEmail,
        keyword = keyword,
        page = page,
        size = size
    )

    @GetMapping("/{contactId}/messages")
    fun messages(
        @PathVariable contactId: Long,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) before: String?,
        @RequestParam(required = false) accountCode: String?
    ): ConversationMessageListResponse = conversationService.listMessages(
        contactId = contactId,
        accountCode = accountCode,
        beforeCursor = before,
        limit = limit
    )

    @PutMapping("/{contactId}/follow")
    fun follow(
        request: HttpServletRequest,
        @PathVariable contactId: Long
    ): ResponseEntity<Any> {
        val username = sessionUsername(request)
            ?: return unauthorized()
        return ResponseEntity.ok(expertFollowService.setFollowed(username, contactId, true))
    }

    // ------------------------------------------------------------------
    // 会话级人工回信（无来信路径，T3）：自由 subject/body 外发到最近真实 SENT 出站锚点。
    // 只用窄 DTO 转发；联系人/锚点/账号/安全校验全部在 service（I-6），controller 不
    // 引入前端可选的账号/QA/RAG 字段。Auth 由 AuthInterceptor 统一拦截（/api/**）。
    // ------------------------------------------------------------------

    @PostMapping("/{contactId}/manual-rich-reply")
    fun conversationManualRichReply(
        @PathVariable contactId: Long,
        @RequestBody body: ConversationManualRichReplyRequest
    ): PendingMailSendResult = pendingMailOperationService.sendConversationManualRichReply(
        contactId = contactId,
        requestId = body.requestId,
        accountScope = body.accountScope,
        subject = body.subject,
        htmlBody = body.htmlBody,
        textBody = body.textBody,
        operatorName = body.operatorName,
        safetyWarningConfirmed = body.safetyWarningConfirmed,
        strongConfirmationText = body.strongConfirmationText
    )

    @DeleteMapping("/{contactId}/follow")
    fun unfollow(
        request: HttpServletRequest,
        @PathVariable contactId: Long
    ): ResponseEntity<Any> {
        val username = sessionUsername(request)
            ?: return unauthorized()
        return ResponseEntity.ok(expertFollowService.setFollowed(username, contactId, false))
    }

    private fun sessionUsername(request: HttpServletRequest): String? =
        request.getSession(false)
            ?.getAttribute(AuthSessionKeys.USERNAME) as? String

    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body<Any>(ApiErrorResponse("UNAUTHORIZED", "未登录", null))
}
