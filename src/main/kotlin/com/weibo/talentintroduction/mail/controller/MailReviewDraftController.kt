package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.mail.service.MailReviewDraftException
import com.weibo.talentintroduction.mail.service.MailReviewDraftService
import com.weibo.talentintroduction.mail.service.MailReviewSendRequest
import com.weibo.talentintroduction.mail.service.MailReviewSendService
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

/** Session identity and strict integral parsing are local to this workflow. */
@RestController
@RequestMapping("/api/mail/mailbox/review-drafts")
class MailReviewDraftController(private val service: MailReviewDraftService, private val sends: MailReviewSendService) {
    @PostMapping
    fun create(request: HttpServletRequest, @RequestBody body: JsonNode): Any {
        val actor = actor(request)
        fields(body, "contactId", "clientRequestId", "subject", "html")
        return service.create(actor, integer(body, "contactId"), text(body, "clientRequestId"), text(body, "subject"), text(body, "html"))
    }

    @GetMapping
    fun list(request: HttpServletRequest, @RequestParam(required = false) contactId: String?,
             @RequestParam(defaultValue = "0") page: String, @RequestParam(defaultValue = "20") size: String,
             @RequestParam(defaultValue = "false") includeDeleted: String): Any {
        val actor = actor(request)
        if (includeDeleted != "true" && includeDeleted != "false") invalid()
        return service.list(actor, contactId?.let { queryInteger(it) }, intQuery(page), intQuery(size), includeDeleted == "true")
    }

    @GetMapping("/{id}")
    fun detail(request: HttpServletRequest, @PathVariable id: Long): Any = service.detail(actor(request), id)

    @PutMapping("/{id}")
    fun save(request: HttpServletRequest, @PathVariable id: Long, @RequestBody body: JsonNode): Any {
        val actor = actor(request)
        fields(body, "expectedVersion", "subject", "html")
        return service.save(actor, id, integer(body, "expectedVersion"), text(body, "subject"), text(body, "html"))
    }

    @PostMapping("/{id}/submit")
    fun submit(request: HttpServletRequest, @PathVariable id: Long, @RequestBody body: JsonNode): Any {
        val actor = actor(request); fields(body, "expectedVersion")
        return service.submit(actor, id, integer(body, "expectedVersion"))
    }

    @PostMapping("/{id}/withdraw")
    fun withdraw(request: HttpServletRequest, @PathVariable id: Long, @RequestBody body: JsonNode): Any {
        val actor = actor(request); fields(body, "expectedVersion")
        return service.withdraw(actor, id, integer(body, "expectedVersion"))
    }

    @DeleteMapping("/{id}")
    fun delete(request: HttpServletRequest, @PathVariable id: Long, @RequestParam expectedVersion: String): Any =
        service.softDelete(actor(request), id, queryInteger(expectedVersion))

    @PostMapping("/{id}/approve")
    fun approve(request: HttpServletRequest, @PathVariable id: Long, @RequestBody body: JsonNode): Any {
        val actor = actor(request); fields(body, "expectedVersion", "observedContextToken")
        return service.approve(actor, id, integer(body, "expectedVersion"), text(body, "observedContextToken"))
    }

    @PostMapping("/{id}/return")
    fun returnDraft(request: HttpServletRequest, @PathVariable id: Long, @RequestBody body: JsonNode): Any {
        val actor = actor(request); fields(body, "expectedVersion", "reason")
        return service.returnDraft(actor, id, integer(body, "expectedVersion"), text(body, "reason"))
    }

    @PostMapping("/{id}/comments")
    fun comment(request: HttpServletRequest, @PathVariable id: Long, @RequestBody body: JsonNode): Any {
        val actor = actor(request); fields(body, "expectedVersion", "text")
        return service.comment(actor, id, integer(body, "expectedVersion"), text(body, "text"))
    }

    @GetMapping("/{id}/events")
    fun events(request: HttpServletRequest, @PathVariable id: Long, @RequestParam(required = false) beforeId: String?,
               @RequestParam(defaultValue = "20") limit: String): Any =
        service.events(actor(request), id, beforeId?.let { queryInteger(it) }, intQuery(limit))

    @GetMapping("/{id}/context")
    fun context(request: HttpServletRequest, @PathVariable id: Long): Any = service.context(actor(request), id)

    @PostMapping("/{id}/confirm-context")
    fun confirmContext(request: HttpServletRequest, @PathVariable id: Long, @RequestBody body: JsonNode): Any {
        val actor = actor(request); fields(body, "expectedVersion", "observedContextToken")
        return service.confirmContext(actor, id, integer(body, "expectedVersion"), text(body, "observedContextToken"))
    }

    @PostMapping("/{id}/translate")
    fun translate(request: HttpServletRequest, @PathVariable id: Long, @RequestBody body: JsonNode): Any {
        val actor = actor(request); fields(body, "expectedVersion")
        return service.translate(actor, id, integer(body, "expectedVersion"))
    }

    @PostMapping("/{id}/send")
    fun send(request: HttpServletRequest, @PathVariable id: Long, @RequestBody body: JsonNode): Any {
        val actor = actor(request)
        val allowed = setOf("expectedVersion", "confirmationEventId", "safetyWarningConfirmed", "strongConfirmationText")
        if (!body.isObject || !body.has("expectedVersion") || body.fieldNames().asSequence().any { it !in allowed }) invalid()
        val confirmed = body.get("safetyWarningConfirmed")?.let {
            if (!it.isBoolean) invalid()
            it.booleanValue()
        } ?: false
        return sends.send(actor, id, MailReviewSendRequest(integer(body, "expectedVersion"),
            body.get("confirmationEventId")?.takeUnless { it.isNull }?.let { integer(body, "confirmationEventId") },
            confirmed, body.get("strongConfirmationText")?.takeUnless { it.isNull }?.let { text(body, "strongConfirmationText") }))
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun sendError(error: ResponseStatusException): ResponseEntity<Any> = ResponseEntity.status(error.status)
        .body(mapOf("code" to when (error.status.value()) {
            409 -> "REVIEW_SEND_PENDING"
            422 -> "REVIEW_SEND_REJECTED"
            503 -> "REVIEW_SEND_SAFE_TO_RETRY"
            else -> "REVIEW_SEND_BLOCKED"
        }, "message" to error.reason))

    @ExceptionHandler(MailReviewDraftException::class)
    fun reviewError(error: MailReviewDraftException): ResponseEntity<Any> = ResponseEntity.status(error.status)
        .body(mapOf("code" to error.code, "message" to error.message))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun malformedJson(error: HttpMessageNotReadableException): ResponseEntity<Any> = ResponseEntity.unprocessableEntity()
        .body(mapOf("code" to "REVIEW_INVALID_CONTENT", "message" to "Invalid review request"))

    private fun actor(request: HttpServletRequest): String =
        (request.getSession(false)?.getAttribute(AuthSessionKeys.USERNAME) as? String)
            ?: throw MailReviewDraftException(401, "UNAUTHORIZED", "Authentication required")

    private fun fields(body: JsonNode, vararg allowed: String) {
        if (!body.isObject || body.fieldNames().asSequence().any { it !in allowed } || allowed.any { !body.has(it) }) invalid()
    }
    private fun text(body: JsonNode, key: String): String {
        val value = body.get(key) ?: invalid()
        if (!value.isTextual) invalid()
        return value.textValue()
    }
    private fun integer(body: JsonNode, key: String): Long {
        val value = body.get(key) ?: invalid()
        if (!value.isIntegralNumber || !value.canConvertToLong() || value.longValue() < 0) invalid()
        return value.longValue()
    }
    private fun queryInteger(value: String): Long {
        if (!value.matches(Regex("0|[1-9][0-9]*"))) invalid()
        return value.toLongOrNull() ?: invalid()
    }
    private fun intQuery(value: String): Int {
        val number = queryInteger(value)
        if (number > Int.MAX_VALUE) invalid()
        return number.toInt()
    }
    private fun invalid(): Nothing = throw MailReviewDraftException(422, "REVIEW_INVALID_CONTENT", "Invalid review request")
}
