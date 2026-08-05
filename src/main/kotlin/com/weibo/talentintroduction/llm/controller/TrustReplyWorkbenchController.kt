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
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleRequest
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleResponse
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplyLockedItemRequest
import com.weibo.talentintroduction.llm.service.TrustReplySaveStateRequest
import com.weibo.talentintroduction.llm.service.TrustReplySavedState
import com.weibo.talentintroduction.llm.service.TrustReplyRequestFactSelection
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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
                requestedFactIds = request.requestedFactIds,
                requestFactSelections = request.requestFactSelections?.map { it.toDomain() }
            )
        )

    @PostMapping("/generations/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun generateStream(@RequestBody request: TrustReplyGenerationHttpRequest): ResponseEntity<SseEmitter> {
        val generationId = request.generationId?.trim()
            ?: throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_GENERATION_ID_REQUIRED")
        if (!isCanonicalUuid(generationId)) {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_GENERATION_ID_INVALID")
        }
        val operation = request.operation?.trim()?.uppercase() ?: "FULL_DRAFT"
        if (operation !in setOf("FULL_DRAFT", "ADJUST_ITEM")) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_OPERATION_INVALID")
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

    @PostMapping("/assemble")
    fun assemble(@RequestBody request: TrustReplyAssembleHttpRequest): TrustReplyAssembleResponse =
        workbenchService.assemble(request.toDomain())

    @PutMapping("/state")
    fun saveState(@RequestBody request: TrustReplySaveStateHttpRequest): TrustReplySavedState =
        workbenchService.saveState(request.toDomain())

    @ExceptionHandler(TrustReplyWorkbenchException::class)
    fun handleWorkbenchException(ex: TrustReplyWorkbenchException): ResponseEntity<TrustReplyErrorResponse> =
        ResponseEntity.status(ex.status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(TrustReplyErrorResponse(code = ex.code))

    private fun TrustReplySourceHttpRequest.toDomain(): TrustReplySourceRef {
        val sourceType = runCatching { TrustReplySourceType.valueOf(sourceType) }.getOrElse {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_SOURCE_INVALID")
        }
        if (sourceId <= 0) {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_SOURCE_INVALID")
        }
        return TrustReplySourceRef(sourceType, sourceId)
    }

    private fun TrustReplyRequestFactSelectionHttpRequest.toDomain() = TrustReplyRequestFactSelection(
        requestKey = requestKey.orEmpty(),
        factRuleIds = factRuleIds.orEmpty()
    )

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
            llmTotalTimeoutSeconds = llmTotalTimeoutSeconds,
            operation = operation?.trim()?.uppercase() ?: "FULL_DRAFT",
            expectedEvidenceSetVersion = expectedEvidenceSetVersion,
            requestKey = requestKey,
            handling = handling?.toHandling(),
            requestedFactIds = requestedFactIds,
            requestFactSelections = requestFactSelections?.map { it.toDomain() }
        )

    private fun TrustReplyAssembleHttpRequest.toDomain() = TrustReplyAssembleRequest(
        source = source.toDomain(),
        expectedSourceVersion = expectedSourceVersion,
        expectedEvidenceSetVersion = expectedEvidenceSetVersion,
        requestedFactIds = requestedFactIds,
        requestFactSelections = requestFactSelections?.map { it.toDomain() },
        lockedItems = lockedItems.map { locked ->
            TrustReplyLockedItemRequest(
                requestKey = locked.requestKey,
                versionId = locked.versionId,
                handling = locked.handling.toHandling(),
                answerText = locked.answerText,
                claims = locked.claims,
                model = locked.model,
                generationKind = runCatching {
                    com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind.valueOf(locked.generationKind)
                }.getOrElse {
                    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_GENERATION_KIND_INVALID")
                },
                evidenceSetVersion = locked.evidenceSetVersion,
                sourceVersion = locked.sourceVersion,
                operatorInstructionHash = locked.operatorInstructionHash,
                operatorInstruction = locked.operatorInstruction
            )
        }
    )

    private fun TrustReplySaveStateHttpRequest.toDomain() = TrustReplySaveStateRequest(
        source = source.toDomain(),
        expectedStateVersion = expectedStateVersion,
        schemaVersion = schemaVersion,
        sourceVersion = sourceVersion,
        evidenceSetVersion = evidenceSetVersion,
        requestedFactIds = requestedFactIds,
        requestFactSelections = requestFactSelections?.map { it.toDomain() },
        selectedModel = selectedModel,
        lockedItems = lockedItems.map { locked ->
            TrustReplyLockedItemRequest(
                requestKey = locked.requestKey,
                versionId = locked.versionId,
                handling = locked.handling.toHandling(),
                answerText = locked.answerText,
                claims = locked.claims,
                model = locked.model,
                generationKind = runCatching {
                    com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind.valueOf(locked.generationKind)
                }.getOrElse {
                    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_GENERATION_KIND_INVALID")
                },
                evidenceSetVersion = locked.evidenceSetVersion,
                sourceVersion = locked.sourceVersion,
                operatorInstructionHash = locked.operatorInstructionHash,
                operatorInstruction = locked.operatorInstruction
            )
        }
    )

    private fun String.toHandling(): TrustReplyItemHandling = runCatching {
        TrustReplyItemHandling.valueOf(trim().uppercase())
    }.getOrElse {
        throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_HANDLING_INVALID")
    }

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
}

data class TrustReplySourceHttpRequest(
    val sourceType: String,
    val sourceId: Long
)

data class TrustReplyRequestFactSelectionHttpRequest(
    val requestKey: String? = null,
    val factRuleIds: List<Long>? = null
)

data class TrustReplyBootstrapHttpRequest(
    val source: TrustReplySourceHttpRequest,
    val requestedFactIds: List<Long>? = null,
    val requestFactSelections: List<TrustReplyRequestFactSelectionHttpRequest>? = null
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
    val llmTotalTimeoutSeconds: Int? = null,
    val operation: String? = "FULL_DRAFT",
    val expectedEvidenceSetVersion: String? = null,
    val requestKey: String? = null,
    val handling: String? = null,
    val requestedFactIds: List<Long>? = null,
    val requestFactSelections: List<TrustReplyRequestFactSelectionHttpRequest>? = null
)

data class TrustReplyLockedItemHttpRequest(
    val requestKey: String,
    val versionId: String,
    val handling: String,
    val answerText: String,
    val claims: List<com.weibo.talentintroduction.llm.service.AiReplyItemClaim> = emptyList(),
    val model: String,
    val generationKind: String,
    val evidenceSetVersion: String,
    val sourceVersion: String,
    val operatorInstructionHash: String = "",
    val operatorInstruction: String = ""
)

data class TrustReplyAssembleHttpRequest(
    val source: TrustReplySourceHttpRequest,
    val expectedSourceVersion: String,
    val expectedEvidenceSetVersion: String,
    val lockedItems: List<TrustReplyLockedItemHttpRequest>,
    val requestedFactIds: List<Long>? = null,
    val requestFactSelections: List<TrustReplyRequestFactSelectionHttpRequest>? = null
)

data class TrustReplySaveStateHttpRequest(
    val source: TrustReplySourceHttpRequest,
    val expectedStateVersion: Long,
    val schemaVersion: String? = null,
    val sourceVersion: String,
    val evidenceSetVersion: String,
    val requestedFactIds: List<Long>? = null,
    val selectedModel: String? = null,
    val lockedItems: List<TrustReplyLockedItemHttpRequest>,
    val requestFactSelections: List<TrustReplyRequestFactSelectionHttpRequest>? = null
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
