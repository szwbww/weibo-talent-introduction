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
import com.weibo.talentintroduction.llm.service.TrustReplyRearrangeRequest
import com.weibo.talentintroduction.llm.service.TrustReplyRearrangeResponse
import com.weibo.talentintroduction.llm.service.TrustReplyPinnedParagraphRequest
import com.weibo.talentintroduction.llm.service.ParagraphPlanEntry
import com.weibo.talentintroduction.llm.service.PlanFact
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplyLockedItemRequest
import com.weibo.talentintroduction.llm.service.TrustReplySaveStateItemRequest
import com.weibo.talentintroduction.llm.service.TrustReplySaveStateRequest
import com.weibo.talentintroduction.llm.service.TrustReplySavedState
import com.weibo.talentintroduction.llm.service.TrustReplyRequestFactSelection
import com.weibo.talentintroduction.llm.service.TrustReplyFrameSelection
import com.weibo.talentintroduction.llm.service.TrustReplyFrameSnapshot
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PatchMapping
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
                requestFactSelections = request.requestFactSelections?.map { it.toDomain() },
                frameSnapshot = request.frameSnapshot?.toDomain()
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

    // c5 / 15-workbench-three-step（T-5）: 重排端点——运营编辑后的 paragraphPlanDraft +
    // pinned 段落 + op* 运营事实 → 一次 13 编排调用，返回新 paragraphs 与六道校验结果。
    // 不落库（I-4）——落库仍走整合（assemble）。
    @PostMapping("/rearrange")
    fun rearrange(@RequestBody request: TrustReplyRearrangeHttpRequest): TrustReplyRearrangeResponse =
        workbenchService.rearrange(request.toDomain())

    @PutMapping("/state")
    fun saveState(@RequestBody request: TrustReplySaveStateHttpRequest): TrustReplySavedState =
        workbenchService.saveState(request.toDomain())

    // 计划 14 (T-1, I-2/I-4): 条目级持久化——请求体只含该条的 requestKey +
    // 该条 locked item + 乐观锁 expectedStateVersion；服务端在既有状态行内
    // 合并该条，返回新的 stateVersion。
    @PatchMapping("/state/item")
    fun saveStateItem(@RequestBody request: TrustReplySaveStateItemHttpRequest): TrustReplySavedState =
        workbenchService.saveStateItem(request.toDomain())

    @DeleteMapping("/state")
    fun deleteState(@RequestBody request: TrustReplyDeleteStateHttpRequest): TrustReplySavedState =
        workbenchService.deleteState(request.source.toDomain(), request.expectedStateVersion)

    @PostMapping("/state/reset")
    fun resetState(@RequestBody request: TrustReplyResetStateHttpRequest): TrustReplySavedState =
        workbenchService.resetState(request.source.toDomain())

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

    private fun TrustReplyFrameSnapshotHttpRequest.toDomain() = TrustReplyFrameSnapshot(
        selection = selection?.let {
            TrustReplyFrameSelection(
                salutationSnippetId = it.salutationSnippetId,
                greetingSnippetId = it.greetingSnippetId,
                ackSnippetId = it.ackSnippetId,
                closingSnippetId = it.closingSnippetId
            )
        },
        version = version.orEmpty()
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
        frameSnapshot = frameSnapshot?.toDomain(),
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

    // c5 / 15-workbench-three-step（T-5）: 重排请求转换。op* 事实按 I-2 强制逐字插槽
    // （frozen=true、required=true），id 为 op<n>，绝不进入任何哈希（I-1 / G-7）。
    private fun TrustReplyRearrangeHttpRequest.toDomain() = TrustReplyRearrangeRequest(
        source = source.toDomain(),
        expectedSourceVersion = expectedSourceVersion,
        paragraphPlanDraft = paragraphPlanDraft.map { entry ->
            ParagraphPlanEntry(
                topic = entry.topic,
                factIds = entry.factIds,
                gapCondition = entry.gapCondition
            )
        },
        pinnedParagraphs = pinnedParagraphs.map { pinned ->
            TrustReplyPinnedParagraphRequest(
                topic = pinned.topic,
                factIds = pinned.factIds,
                text = pinned.text,
                evidenceSetVersion = pinned.evidenceSetVersion
            )
        },
        operatorFacts = operatorFacts.map { fact ->
            PlanFact(
                id = fact.id,
                topic = fact.topic,
                body = fact.body,
                controlled = fact.controlled,
                frozen = fact.frozen,
                required = fact.required
            )
        },
        requestedFactIds = requestedFactIds,
        requestFactSelections = requestFactSelections?.map { it.toDomain() }
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
        frameSnapshot = frameSnapshot?.toDomain(),
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

    private fun TrustReplySaveStateItemHttpRequest.toDomain() = TrustReplySaveStateItemRequest(
        source = source.toDomain(),
        expectedStateVersion = expectedStateVersion,
        schemaVersion = schemaVersion,
        sourceVersion = sourceVersion,
        evidenceSetVersion = evidenceSetVersion,
        requestKey = requestKey,
        lockedItem = TrustReplyLockedItemRequest(
            requestKey = lockedItem.requestKey,
            versionId = lockedItem.versionId,
            handling = lockedItem.handling.toHandling(),
            answerText = lockedItem.answerText,
            claims = lockedItem.claims,
            model = lockedItem.model,
            generationKind = runCatching {
                com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind.valueOf(lockedItem.generationKind)
            }.getOrElse {
                throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_GENERATION_KIND_INVALID")
            },
            evidenceSetVersion = lockedItem.evidenceSetVersion,
            sourceVersion = lockedItem.sourceVersion,
            operatorInstructionHash = lockedItem.operatorInstructionHash,
            operatorInstruction = lockedItem.operatorInstruction
        )
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

data class TrustReplyFrameSelectionHttpRequest(
    val salutationSnippetId: Long? = null,
    val greetingSnippetId: Long? = null,
    val ackSnippetId: Long? = null,
    val closingSnippetId: Long? = null
)

data class TrustReplyFrameSnapshotHttpRequest(
    val selection: TrustReplyFrameSelectionHttpRequest? = null,
    val version: String? = null
)

data class TrustReplyBootstrapHttpRequest(
    val source: TrustReplySourceHttpRequest,
    val requestedFactIds: List<Long>? = null,
    val requestFactSelections: List<TrustReplyRequestFactSelectionHttpRequest>? = null,
    val frameSnapshot: TrustReplyFrameSnapshotHttpRequest? = null
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
    val requestFactSelections: List<TrustReplyRequestFactSelectionHttpRequest>? = null,
    val frameSnapshot: TrustReplyFrameSnapshotHttpRequest? = null
)

// c5 / 15-workbench-three-step（T-5）: 重排请求 DTO。paragraphPlanDraft 复用 13 的
// ParagraphPlanEntry 形状（topic + factIds + 可选 gapCondition）；pinned 段落携带条目级
// evidenceSetVersion（I-3）；operatorFacts 为 op<n> 逐字插槽（I-1/I-2）。
data class TrustReplyParagraphPlanEntryHttpRequest(
    val topic: String,
    val factIds: List<String>,
    val gapCondition: String? = null
)

data class TrustReplyPinnedParagraphHttpRequest(
    val topic: String,
    val factIds: List<String>,
    val text: String,
    val evidenceSetVersion: String
)

data class TrustReplyOperatorFactHttpRequest(
    val id: String,
    val topic: String,
    val body: String,
    val controlled: String? = null,
    val frozen: Boolean = true,
    val required: Boolean = true
)

data class TrustReplyRearrangeHttpRequest(
    val source: TrustReplySourceHttpRequest,
    val expectedSourceVersion: String,
    val paragraphPlanDraft: List<TrustReplyParagraphPlanEntryHttpRequest>,
    val pinnedParagraphs: List<TrustReplyPinnedParagraphHttpRequest> = emptyList(),
    val operatorFacts: List<TrustReplyOperatorFactHttpRequest> = emptyList(),
    val requestedFactIds: List<Long>? = null,
    val requestFactSelections: List<TrustReplyRequestFactSelectionHttpRequest>? = null
)

data class TrustReplyDeleteStateHttpRequest(
    val source: TrustReplySourceHttpRequest,
    val expectedStateVersion: Long
)

data class TrustReplyResetStateHttpRequest(
    val source: TrustReplySourceHttpRequest
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
    val requestFactSelections: List<TrustReplyRequestFactSelectionHttpRequest>? = null,
    val frameSnapshot: TrustReplyFrameSnapshotHttpRequest? = null
)

data class TrustReplySaveStateItemHttpRequest(
    val source: TrustReplySourceHttpRequest,
    val expectedStateVersion: Long,
    val schemaVersion: String? = null,
    val sourceVersion: String,
    val evidenceSetVersion: String,
    val requestKey: String,
    val lockedItem: TrustReplyLockedItemHttpRequest
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
