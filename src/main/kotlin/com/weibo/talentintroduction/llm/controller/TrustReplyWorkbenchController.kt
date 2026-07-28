package com.weibo.talentintroduction.llm.controller

import com.weibo.talentintroduction.llm.service.AiReplyTimeoutPolicy
import com.weibo.talentintroduction.llm.service.TrustReplyBootstrapRequest
import com.weibo.talentintroduction.llm.service.TrustReplyBootstrapResponse
import com.weibo.talentintroduction.llm.service.AiReplyGenerationCoordinator
import com.weibo.talentintroduction.llm.service.TrustReplyGenerationRequest
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.llm.service.TrustReplySourceType
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchException
import com.weibo.talentintroduction.llm.service.AiReplyTurn
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@RestController
@RequestMapping("/api/trust-reply/workbench")
class TrustReplyWorkbenchController(
    private val workbenchService: com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchService,
    private val generationCoordinator: AiReplyGenerationCoordinator
) {
    @PostMapping("/bootstrap")
    fun bootstrap(@RequestBody request: TrustReplyBootstrapHttpRequest): TrustReplyBootstrapResponse =
        workbenchService.bootstrap(
            TrustReplyBootstrapRequest(
                source = request.source.toDomain(),
                requestedFactIds = request.requestedFactIds
            )
        )

    @PostMapping("/generations/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun generateStream(@RequestBody request: TrustReplyGenerationHttpRequest): ResponseEntity<SseEmitter> {
        val generationId = request.generationId?.trim()
            ?: throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_GENERATION_ID_REQUIRED")
        if (!isCanonicalUuid(generationId)) {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_GENERATION_ID_INVALID")
        }
        val policy = try {
            AiReplyTimeoutPolicy.resolve(request.llmAttemptTimeoutSeconds, request.llmTotalTimeoutSeconds)
        } catch (ex: IllegalArgumentException) {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_TIMEOUT_INVALID")
        }
        val source = request.source.toDomain()
        val scopeKey = "${source.sourceType.name}:${source.sourceId}"
        val emitter = generationCoordinator.start(scopeKey, generationId, policy) { token, reporter, beforeCommit ->
            workbenchService.generate(
                request = request.toDomain(),
                cancellationToken = token,
                progressReporter = reporter,
                beforeCommit = beforeCommit
            )
        }
        val headers = HttpHeaders().apply {
            cacheControl = "no-cache, no-transform"
            set("X-Accel-Buffering", "no")
        }
        return ResponseEntity.ok().headers(headers).body(emitter)
    }

    @PostMapping("/generations/{generationId}/cancel")
    fun cancel(
        @PathVariable generationId: String,
        @RequestBody request: TrustReplyCancelHttpRequest
    ): TrustReplyCancelResponse {
        if (!isCanonicalUuid(generationId)) {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_GENERATION_ID_INVALID")
        }
        val source = request.source.toDomain()
        val scopeKey = "${source.sourceType.name}:${source.sourceId}"
        return TrustReplyCancelResponse(
            generationId = generationId,
            status = generationCoordinator.cancel(scopeKey, generationId)
        )
    }

    @ExceptionHandler(TrustReplyWorkbenchException::class)
    fun handleWorkbenchException(ex: TrustReplyWorkbenchException): ResponseEntity<TrustReplyErrorResponse> =
        ResponseEntity.status(ex.status).body(TrustReplyErrorResponse(code = ex.code))

    private fun TrustReplySourceHttpRequest.toDomain(): TrustReplySourceRef {
        val sourceType = runCatching { TrustReplySourceType.valueOf(sourceType) }.getOrElse {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_SOURCE_INVALID")
        }
        if (sourceId <= 0) {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_SOURCE_INVALID")
        }
        return TrustReplySourceRef(sourceType, sourceId)
    }

    private fun TrustReplyGenerationHttpRequest.toDomain(): TrustReplyGenerationRequest =
        TrustReplyGenerationRequest(
            source = source.toDomain(),
            expectedSourceVersion = expectedSourceVersion,
            turns = turns.map { AiReplyTurn(it.assistantDraft, it.operatorInstruction) },
            qaRuleIds = qaRuleIds,
            operatorInstruction = operatorInstruction,
            operatorName = operatorName,
            model = model,
            llmAttemptTimeoutSeconds = llmAttemptTimeoutSeconds,
            llmTotalTimeoutSeconds = llmTotalTimeoutSeconds
        )

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
}

data class TrustReplySourceHttpRequest(
    val sourceType: String,
    val sourceId: Long
)

data class TrustReplyBootstrapHttpRequest(
    val source: TrustReplySourceHttpRequest,
    val requestedFactIds: List<Long>? = null
)

data class TrustReplyTurnHttpRequest(
    val assistantDraft: String,
    val operatorInstruction: String
)

data class TrustReplyGenerationHttpRequest(
    val source: TrustReplySourceHttpRequest,
    val expectedSourceVersion: String?,
    val turns: List<TrustReplyTurnHttpRequest> = emptyList(),
    val qaRuleIds: List<Long>? = null,
    val operatorInstruction: String? = null,
    val operatorName: String? = null,
    val model: String? = null,
    val generationId: String? = null,
    val llmAttemptTimeoutSeconds: Int? = null,
    val llmTotalTimeoutSeconds: Int? = null
)

data class TrustReplyCancelHttpRequest(
    val source: TrustReplySourceHttpRequest
)

data class TrustReplyCancelResponse(
    val generationId: String,
    val status: String
)

data class TrustReplyErrorResponse(
    val code: String
)
