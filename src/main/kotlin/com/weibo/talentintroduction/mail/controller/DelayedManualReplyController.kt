package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException
import com.weibo.talentintroduction.mail.repository.DelayedManualReplyRepository
import com.weibo.talentintroduction.mail.service.*
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/mail/mailbox/conversations/{contactId}/delayed-replies")
class DelayedManualReplyController(
    private val repository: DelayedManualReplyRepository,
    private val lifecycle: DelayedManualReplyService,
    private val worker: DelayedManualReplyWorker,
    private val guard: ExpertMailPermissionGuard,
    private val jdbc: NamedParameterJdbcTemplate
) {
    @GetMapping
    fun list(request: HttpServletRequest, @PathVariable contactId: Long,
        @RequestParam originKind: DelayedManualReplyOriginKind, @RequestParam originId: Long): ResponseEntity<Any> = respond(request) { actor ->
        read(actor, contactId)
        if (originId <= 0) invalid()
        val table = when (originKind) {
            DelayedManualReplyOriginKind.LEGACY -> "inbound_mail_processing"
            DelayedManualReplyOriginKind.DRAFT -> "mailbox_reply_draft"
            DelayedManualReplyOriginKind.REVIEW -> "mailbox_review_draft"
        }
        val parameters = mapOf("contact" to contactId, "origin" to originId, "kind" to originKind.name, "actor" to actor)
        if (jdbc.queryForList("SELECT id FROM $table WHERE id=:origin AND expert_contact_id=:contact", parameters).isEmpty()) notFound()
        val source = if (originKind == DelayedManualReplyOriginKind.LEGACY)
            "JSON_EXTRACT(snapshot_json,'$.payload.inboundProcessingId')=:origin" else "origin_id=:origin"
        val ids = jdbc.queryForList("SELECT id FROM delayed_manual_reply WHERE expert_contact_id=:contact AND origin_kind=:kind " +
            "AND $source AND (:actor='admin' OR actor_username=:actor) ORDER BY id DESC LIMIT 20", parameters, Long::class.java)
        ids.mapNotNull(repository::findById).map { projection(it, actor) }
    }

    @GetMapping("/{jobId}")
    fun get(request: HttpServletRequest, @PathVariable contactId: Long, @PathVariable jobId: Long): ResponseEntity<Any> = respond(request) { actor ->
        projection(owned(actor, contactId, jobId), actor)
    }

    @GetMapping("/{jobId}/restore")
    fun restore(request: HttpServletRequest, @PathVariable contactId: Long, @PathVariable jobId: Long): ResponseEntity<Any> = respond(request) { actor ->
        val row = owned(actor, contactId, jobId)
        action(actor, row)
        if (row.originKind == DelayedManualReplyOriginKind.REVIEW || row.status !in setOf(DelayedManualReplyStatus.CANCELLED, DelayedManualReplyStatus.FAILED)) conflict()
        if (row.snapshotJson.toByteArray(Charsets.UTF_8).size > DELAYED_MANUAL_REPLY_MAX_SNAPSHOT_BYTES)
            throw DelayedManualReplyException(413, "SNAPSHOT_INVALID", DelayedReplyPublicError.SNAPSHOT_INVALID.message)
        val snapshot = DelayedManualReplySnapshotCodec.decodeOrNull(row.snapshotJson) ?: invalid()
        if (snapshot.payload.contactId != row.expertContactId || snapshot.payload.accountCode != row.accountCode) invalid()
        val input: Any = when (val restore = snapshot.restore) {
            is DelayedLegacyRestore -> {
                if (row.originKind != DelayedManualReplyOriginKind.LEGACY || row.originId != null) invalid()
                restore.originalInput ?: invalid()
            }
            is DelayedDraftRestore -> {
                if (row.originKind != DelayedManualReplyOriginKind.DRAFT || row.originId != restore.draftId ||
                    row.originVersion != restore.draftVersion || row.actorUsername != restore.owner) invalid()
                restore
            }
            is DelayedReviewRestore -> conflict()
        }
        DelayedReplyRestoreResponse(row.id, row.revision, row.originKind, row.originId, input)
    }

    @PostMapping("/{jobId}/cancel")
    fun cancel(request: HttpServletRequest, @PathVariable contactId: Long, @PathVariable jobId: Long,
        @RequestBody body: JsonNode): ResponseEntity<Any> = respond(request) { actor ->
        val row = owned(actor, contactId, jobId)
        action(actor, row)
        val revision = revision(body)
        // A late click must retain the deadline error even when the worker already won the claim.
        // The lifecycle transaction still performs the authoritative deadline/CAS check for WAITING.
        if (row.status != DelayedManualReplyStatus.CANCELLED && repository.currentTimeMs() >= row.dueAtMs)
            throw DelayedManualReplyException(409, "DELAYED_REPLY_TOO_LATE", "已超过撤回期限")
        lifecycle.cancel(jobId, actor, revision)
        projection(owned(actor, contactId, jobId), actor)
    }

    @PostMapping("/{jobId}/retry")
    fun retry(request: HttpServletRequest, @PathVariable contactId: Long, @PathVariable jobId: Long,
        @RequestBody body: JsonNode): ResponseEntity<Any> = respond(request) { actor ->
        val row = owned(actor, contactId, jobId)
        action(actor, row)
        val revision = revision(body)
        if (row.revision != revision || row.status != DelayedManualReplyStatus.FAILED ||
            DelayedReplyPublicError.ofOrInvalid(row.publicErrorCode)?.retryableAfterRevalidation != true) conflict()
        worker.preflight(row)
        lifecycle.retry(jobId, actor, revision)
        projection(owned(actor, contactId, jobId), actor)
    }

    private fun projection(row: DelayedManualReplyRow, actor: String): DelayedManualReplyView {
        val view = lifecycle.view(row)
        val canAct = runCatching { action(actor, row) }.isSuccess
        return view.copy(canCancel = view.canCancel && canAct,
            canRetry = view.canRetry && canAct && runCatching { worker.preflight(row) }.isSuccess)
    }

    private fun read(actor: String, contactId: Long) {
        if (actor != "admin" && actor != "user1") forbidden()
        if (contactId <= 0) invalid()
        guard.requireRead(actor, contactId)
    }

    private fun owned(actor: String, contactId: Long, jobId: Long): DelayedManualReplyRow {
        read(actor, contactId)
        val row = repository.findById(jobId)?.takeIf { it.expertContactId == contactId } ?: notFound()
        if (actor != "admin" && actor != row.actorUsername && row.originKind != DelayedManualReplyOriginKind.REVIEW) forbidden()
        return row
    }

    private fun action(actor: String, row: DelayedManualReplyRow) {
        if (actor != "admin" && (row.actorUsername != actor || row.originKind == DelayedManualReplyOriginKind.REVIEW)) forbidden()
        guard.requireFull(actor, row.expertContactId)
    }

    private fun revision(body: JsonNode): Long {
        val revision = body.get("expectedRevision")
        if (!body.isObject || body.size() != 1 || revision == null || !revision.isIntegralNumber ||
            !revision.canConvertToLong() || revision.longValue() <= 0) invalid()
        return revision.longValue()
    }

    private fun respond(request: HttpServletRequest, block: (String) -> Any): ResponseEntity<Any> {
        val actor = request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String
        if (actor.isNullOrBlank()) return ResponseEntity.status(401).body(mapOf("code" to "UNAUTHORIZED", "message" to "请先登录"))
        return try { ResponseEntity.ok(block(actor)) }
        catch (e: DelayedManualReplyException) { ResponseEntity.status(e.status).body(mapOf("code" to e.code, "message" to e.message)) }
        catch (e: MailReadAccessDeniedException) { ResponseEntity.status(403).body(mapOf("code" to e.code, "message" to e.message)) }
        catch (e: MailReviewDraftException) {
            val error = when {
                e.status == 403 -> DelayedReplyPublicError.ACCESS_CHANGED
                e.code == "REVIEW_CONTEXT_CHANGED" -> DelayedReplyPublicError.REVIEW_CONTEXT_CHANGED
                else -> DelayedReplyPublicError.SOURCE_CHANGED
            }
            ResponseEntity.status(e.status).body(mapOf("code" to error.code, "message" to error.message))
        }
        catch (e: MailReplyDraftException) {
            val error = if (e.status.value() == 403) DelayedReplyPublicError.ACCESS_CHANGED else DelayedReplyPublicError.SOURCE_CHANGED
            ResponseEntity.status(e.status).body(mapOf("code" to error.code, "message" to error.message))
        }
        catch (_: DelayedManualReplySnapshotException) { ResponseEntity.status(422).body(mapOf("code" to DelayedReplyPublicError.SNAPSHOT_INVALID.code, "message" to DelayedReplyPublicError.SNAPSHOT_INVALID.message)) }
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException::class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException::class,
        org.springframework.web.bind.MissingServletRequestParameterException::class)
    fun invalidRequest(): ResponseEntity<Any> = ResponseEntity.status(422).body(
        mapOf("code" to "SNAPSHOT_INVALID", "message" to DelayedReplyPublicError.SNAPSHOT_INVALID.message))

    private fun forbidden(): Nothing = throw DelayedManualReplyException(403, "MAIL_WRITE_FORBIDDEN", "无权操作该发送任务")
    private fun notFound(): Nothing = throw DelayedManualReplyException(404, "DELAYED_REPLY_NOT_FOUND", "发送任务不存在")
    private fun invalid(): Nothing = throw DelayedManualReplyException(422, "SNAPSHOT_INVALID", DelayedReplyPublicError.SNAPSHOT_INVALID.message)
    private fun conflict(): Nothing = throw DelayedManualReplyException(409, "DELAYED_REPLY_STATE_CONFLICT", "当前任务状态或代次已变化，请重新读取")
}

data class DelayedReplyRestoreResponse(val id: Long, val expectedCancelledRevision: Long,
    val originKind: DelayedManualReplyOriginKind, val originId: Long?, val originalInput: Any)
