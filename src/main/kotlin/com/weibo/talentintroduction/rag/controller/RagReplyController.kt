package com.weibo.talentintroduction.rag.controller

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.service.AiReplyModel
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.rag.service.RagComposeException
import com.weibo.talentintroduction.rag.service.RagComposeResult
import com.weibo.talentintroduction.rag.service.RagKnowledgeBase
import com.weibo.talentintroduction.rag.service.RagLetterComposer
import com.weibo.talentintroduction.reply.service.ReplyFrameSelection
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 计划 03 (T6): RAG 整封草稿端点，新命名空间 `/api/rag-reply`（与旧
 * `/api/trust-reply/workbench` 零重叠，可并行灰度）。
 *
 * - `POST /api/rag-reply/compose`：给定一封来信（训练邮件或线上来信）返回整封
 *   草稿的框架分段 + 模型正文分段 + 用到的有序事实 + 未识别提问。
 * - 来源解析按 plan 03 的**两条仓储路径**自行完成（不复用
 *   `TrustReplyWorkbenchService.resolveSource`，07 会摘除那个服务），
 *   只取 contact / inboundText / subject / senderAccountCode 四项。
 * - G-1: 请求与响应只出现 `fact_code`，绝不出现 rag 自增 id。
 * - 本端点不持久化草稿、不触发任何发送路径（D-1 / What must NOT change）。
 * - 异常映射：400 RAG_FACT_CODE_INVALID / 422 RAG_VERBATIM_MISSING /
 *   502 RAG_LLM_UNAVAILABLE（由 [RagComposeException] 统一携带）。
 */
@RestController
@RequestMapping("/api/rag-reply")
class RagReplyController(
    private val composer: RagLetterComposer,
    private val knowledgeBase: RagKnowledgeBase,
    private val mailRecordRepository: MailRecordRepository,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val llmProperties: LlmProperties
) {

    @PostMapping("/compose")
    fun compose(@RequestBody request: RagReplyComposeRequest): RagComposeResult {
        val sourceType = request.sourceType?.trim()?.uppercase()
        val resolved = when (sourceType) {
            RAG_SOURCE_TYPE_TRAINING_MAIL -> resolveTrainingMail(request.sourceId)
            RAG_SOURCE_TYPE_LIVE_INBOUND -> resolveLiveInbound(request.sourceId)
            else -> throw RagComposeException(
                HttpStatus.BAD_REQUEST.value(),
                "RAG_REPLY_SOURCE_INVALID",
                "sourceType must be TRAINING_MAIL or LIVE_INBOUND"
            )
        }
        validateFactCodes(request.forcedFactCodes, "forcedFactCodes")
        validateFactCodes(request.excludedFactCodes, "excludedFactCodes")

        val providerModel = AiReplyModel.fromNullable(request.model)
            .resolveProviderModel(llmProperties)
        return composer.compose(
            contactId = resolved.contact.id!!,
            inboundText = resolved.inboundText,
            providerModel = providerModel,
            forcedFactCodes = request.forcedFactCodes.orEmpty(),
            excludedFactCodes = request.excludedFactCodes.orEmpty(),
            frameSelection = request.frameSelection
        )
    }

    /** 训练邮件：mail_record 行；只接受 INBOUND。 */
    private fun resolveTrainingMail(sourceId: Long): RagResolvedSource {
        val mail = mailRecordRepository.findById(sourceId).orElseThrow {
            RagComposeException(
                HttpStatus.NOT_FOUND.value(),
                "RAG_REPLY_SOURCE_NOT_FOUND",
                "mail_record $sourceId does not exist"
            )
        }
        if (!mail.direction.equals("INBOUND", ignoreCase = true)) {
            throw RagComposeException(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "RAG_REPLY_SOURCE_NOT_INBOUND",
                "mail_record $sourceId is not an inbound mail"
            )
        }
        return toResolved(mail.expertContactId, mail)
    }

    /** 线上来信：inbound_mail_processing 行；必须已绑定联系人。 */
    private fun resolveLiveInbound(sourceId: Long): RagResolvedSource {
        val inbound = inboundMailProcessingRepository.findById(sourceId).orElseThrow {
            RagComposeException(
                HttpStatus.NOT_FOUND.value(),
                "RAG_REPLY_SOURCE_NOT_FOUND",
                "inbound_mail_processing $sourceId does not exist"
            )
        }
        val contactId = inbound.expertContactId ?: throw RagComposeException(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "RAG_REPLY_SOURCE_CONTACT_REQUIRED",
            "inbound_mail_processing $sourceId has no expert contact"
        )
        return toResolved(contactId, inbound)
    }

    /**
     * T6（plan T1 同款解析路径）：contact / inboundText / subject /
     * senderAccountCode 四项。正文取 `cleanedBody ?: body`，与
     * `TrustReplyWorkbenchService` 的私有扩展同逻辑。
     */
    private fun toResolved(contactId: Long, mail: MailRecord): RagResolvedSource =
        RagResolvedSource(
            contact = resolveContact(contactId),
            inboundText = mail.cleanedBody?.takeIf { it.isNotBlank() } ?: mail.body.orEmpty(),
            subject = mail.subject,
            senderAccountCode = mail.senderAccountCode
        )

    private fun toResolved(contactId: Long, inbound: InboundMailProcessing): RagResolvedSource =
        RagResolvedSource(
            contact = resolveContact(contactId),
            inboundText = inbound.cleanedBody?.takeIf { it.isNotBlank() } ?: inbound.body.orEmpty(),
            subject = inbound.subject,
            senderAccountCode = inbound.senderAccountCode
        )

    private fun resolveContact(contactId: Long): ExpertContact =
        expertContactRepository.findById(contactId).orElseThrow {
            RagComposeException(
                HttpStatus.NOT_FOUND.value(),
                "RAG_REPLY_SOURCE_CONTACT_NOT_FOUND",
                "expert_contact $contactId does not exist"
            )
        }

    /**
     * T6: forced/excluded 只接受存在且 enabled 的 fact_code（G-1），否则
     * 400 RAG_FACT_CODE_INVALID 并列出非法值。enabled = 快照内 enabled 且
     * 归一状态非 DISABLED（I-2 口径，与 [RagKnowledgeBase.enabledFacts] 一致）。
     */
    private fun validateFactCodes(codes: List<String>?, field: String) {
        if (codes.isNullOrEmpty()) {
            return
        }
        val valid = knowledgeBase.enabledFacts().map { it.factCode }.toSet()
        val invalid = codes.filter { it !in valid }.distinct()
        if (invalid.isNotEmpty()) {
            throw RagComposeException(
                HttpStatus.BAD_REQUEST.value(),
                "RAG_FACT_CODE_INVALID",
                "$field contains non-existent or disabled fact_code(s): ${invalid.joinToString(", ")}"
            )
        }
    }

    @ExceptionHandler(RagComposeException::class)
    fun handleRagComposeException(ex: RagComposeException): ResponseEntity<RagComposeErrorResponse> {
        val status = HttpStatus.resolve(ex.status) ?: HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity
            .status(status)
            .body(RagComposeErrorResponse(code = ex.code, message = ex.message ?: ex.code))
    }

    private data class RagResolvedSource(
        val contact: ExpertContact,
        val inboundText: String,
        val subject: String?,
        val senderAccountCode: String?
    )

    companion object {
        const val RAG_SOURCE_TYPE_TRAINING_MAIL = "TRAINING_MAIL"
        const val RAG_SOURCE_TYPE_LIVE_INBOUND = "LIVE_INBOUND"
    }
}

/** 请求体（T6）：model 为 AI 模型枚举名，缺省 = flash。 */
data class RagReplyComposeRequest(
    val sourceType: String,
    val sourceId: Long,
    val model: String? = null,
    val forcedFactCodes: List<String>? = null,
    val excludedFactCodes: List<String>? = null,
    val frameSelection: ReplyFrameSelection? = null
)

/** 错误响应：与仓内 `ApiErrorResponse` 同构（code + message）。 */
data class RagComposeErrorResponse(
    val code: String,
    val message: String
)
