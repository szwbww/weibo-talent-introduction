package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.mail.service.*
import com.weibo.talentintroduction.template.service.*
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

/** Read-only template use, authorized against the current expert before resolving any content. */
@RestController
@RequestMapping("/api/mail/conversations/{contactId}/reply-templates")
class ConversationTemplateController(
    private val templates: MailComposeTemplateService,
    private val operations: PendingMailOperationService,
    private val guard: ExpertMailPermissionGuard
) {
    @GetMapping
    fun list(@PathVariable contactId: Long, request: HttpServletRequest): List<MailComposeTemplateDetail> {
        guard.requireFull(request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String, contactId)
        return templates.listAll().filter { it.enabled }
    }

    @PostMapping("/preview")
    fun preview(@PathVariable contactId: Long, @RequestBody body: ConversationTemplatePreviewRequest,
                request: HttpServletRequest): ComposeTemplatePreviewDraftResult {
        guard.requireFull(request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String, contactId)
        val source = operations.resolveIdentitySource(ReplyIdentityContextRequest(
            sourceKind = ReplyIdentitySourceKind.INBOUND, contactId = contactId,
            inboundProcessingId = body.inboundProcessingId))
        require(body.senderAccountCode.isNullOrBlank() || body.senderAccountCode == source.account.accountCode) { "来信账号不匹配" }
        val template = templates.getById(body.templateId)
        require(template.enabled) { "模板已停用" }
        return templates.previewDraft(ComposeTemplatePreviewDraftRequest(
            subject = template.subject, subjectSnippetId = template.subjectSnippetId,
            blocks = template.blocks.map { ComposeDraftBlock(it.blockOrder, it.blockType, it.refId, it.customText) },
            contactId = contactId, senderAccountCode = source.account.accountCode,
            variantIndex = 0, strictPlaceholders = false))
    }
}

data class ConversationTemplatePreviewRequest(
    val templateId: Long,
    val inboundProcessingId: Long,
    val senderAccountCode: String? = null
)
