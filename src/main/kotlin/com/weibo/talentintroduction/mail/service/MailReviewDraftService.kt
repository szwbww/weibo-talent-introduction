package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import com.weibo.talentintroduction.mail.repository.MailReviewDraftRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Approval freezes a canonical snapshot; this service has no delivery dependency. */
@Service
class MailReviewDraftService(
    private val repository: MailReviewDraftRepository,
    private val conversations: MailboxConversationRepository,
    private val senderAccounts: MailSenderAccountRepository,
    private val content: MailContentService,
    private val placeholders: MailPlaceholderService,
    private val translation: MailTranslationService
) {
    @Transactional
    fun create(actor: String?, contactId: Long, clientRequestId: String, subject: String, html: String): Any {
        val username = requireReader(actor)
        grant(username, contactId, true)
        val requestId = try { UUID.fromString(clientRequestId).toString() } catch (e: IllegalArgumentException) { invalid() }
        if (requestId != clientRequestId) invalid()
        val canonical = canonical(subject, html)
        repository.findByRequest(username, requestId)?.let { previous ->
            if (previous.contactId != contactId) conflictVersion()
            val initial = repository.creationEvent(previous.id) ?: error("Missing review creation snapshot")
            if (initial.payload["subject"] != canonical.subject || initial.payload["html"] != canonical.html || initial.payload["text"] != canonical.text) conflictVersion()
            return response(previous, username)
        }
        val target = repository.resolveTarget(contactId)
            ?: throw MailReviewDraftException(422, "REVIEW_TARGET_NOT_FOUND", "No real reply target exists")
        val now = now()
        val row = try {
            repository.insert(MailReviewDraftRow(ownerUsername = username, contactId = contactId,
                clientRequestId = requestId, targetKind = target.targetKind, targetId = target.targetId,
                accountCode = target.accountCode, state = MailReviewDraftState.DRAFT,
                subject = canonical.subject, html = canonical.html, text = canonical.text, createdAt = now, updatedAt = now))
        } catch (e: DuplicateKeyException) { conflictVersion() }
        snapshot(row, "CREATE", username)
        return response(row, username)
    }

    @Transactional(readOnly = true)
    fun detail(actor: String?, id: Long): Any {
        val username = requireActor(actor)
        return response(access(username, id, false), username)
    }

    @Transactional(readOnly = true)
    fun list(actor: String?, contactId: Long?, page: Int = 0, size: Int = 20, includeDeleted: Boolean = false): MailReviewDraftPage {
        val username = requireActor(actor)
        if (page < 0 || size !in 1..50) invalid()
        if (contactId != null && username == MailReadOnlyPolicy.READER_USERNAME) grant(username, contactId, false)
        return MailReviewDraftPage(repository.list(username, contactId, page, size, includeDeleted).map { response(it, username) },
            repository.count(username, contactId, includeDeleted), page, size)
    }

    @Transactional
    fun save(actor: String?, id: Long, expectedVersion: Long, subject: String, html: String): Any {
        val username = requireReader(actor)
        val row = writable(username, id, expectedVersion)
        state(row, MailReviewDraftState.DRAFT, MailReviewDraftState.RETURNED)
        val value = canonical(subject, html)
        if (value.subject == row.subject && value.html == row.html && value.text == row.text) return response(row, username)
        return response(change(row, row.copy(subject = value.subject, html = value.html, text = value.text), "SAVE", username), username)
    }

    @Transactional
    fun submit(actor: String?, id: Long, expectedVersion: Long): Any {
        val username = requireReader(actor)
        val row = writable(username, id, expectedVersion)
        state(row, MailReviewDraftState.DRAFT, MailReviewDraftState.RETURNED)
        validateSubmission(row)
        return response(change(row, row.copy(state = MailReviewDraftState.SUBMITTED, submittedAt = row.submittedAt ?: now(), returnReason = null), "SUBMIT", username), username)
    }

    @Transactional
    fun withdraw(actor: String?, id: Long, expectedVersion: Long): Any {
        val username = requireReader(actor)
        val row = writable(username, id, expectedVersion)
        state(row, MailReviewDraftState.SUBMITTED)
        return response(change(row, row.copy(state = MailReviewDraftState.DRAFT), "WITHDRAW", username), username)
    }

    @Transactional
    fun softDelete(actor: String?, id: Long, expectedVersion: Long): Any {
        val username = requireReader(actor)
        val row = access(username, id, true)
        if (row.deletedAt != null) {
            // Only the original delete request is idempotent, not arbitrary stale writes.
            if (expectedVersion < 0 || expectedVersion != row.version - 1) conflictVersion()
            return MailReviewDraftDeleted(row.id)
        }
        version(row, expectedVersion)
        if (row.state == MailReviewDraftState.APPROVED) conflictState()
        change(row, row.copy(deletedAt = now()), "DELETE", username)
        return MailReviewDraftDeleted(row.id)
    }

    @Transactional
    fun approve(actor: String?, id: Long, expectedVersion: Long, observedContextToken: String): Any {
        val username = requireAdmin(actor)
        val row = writable(username, id, expectedVersion)
        state(row, MailReviewDraftState.SUBMITTED)
        val token = matchingContext(row, observedContextToken)
        return response(change(row, row.copy(state = MailReviewDraftState.APPROVED,
            reviewedBy = username, reviewedAt = now(), approvedContextToken = token,
            sendRequestId = UUID.randomUUID().toString()), "APPROVE", username), username)
    }

    @Transactional
    fun returnDraft(actor: String?, id: Long, expectedVersion: Long, reason: String): Any {
        val username = requireAdmin(actor)
        val row = writable(username, id, expectedVersion)
        state(row, MailReviewDraftState.SUBMITTED)
        val value = shortText(reason)
        return response(change(row, row.copy(state = MailReviewDraftState.RETURNED, returnReason = value), "RETURN", username, value), username)
    }

    @Transactional
    fun comment(actor: String?, id: Long, expectedVersion: Long, text: String): MailReviewDraftEvent {
        val username = requireActor(actor)
        val row = writable(username, id, expectedVersion)
        if (username == MailReadOnlyPolicy.READER_USERNAME && row.state == MailReviewDraftState.APPROVED) conflictState()
        return repository.appendEvent(row.id, row.version, "COMMENT", username, mapOf("text" to shortText(text)), now())
    }

    @Transactional(readOnly = true)
    fun events(actor: String?, id: Long, beforeId: Long? = null, limit: Int = 20): List<MailReviewDraftEvent> {
        val username = requireActor(actor)
        access(username, id, false)
        if (limit !in 1..100 || (beforeId != null && beforeId <= 0)) invalid()
        return repository.events(id, beforeId, limit)
    }

    @Transactional(readOnly = true)
    fun context(actor: String?, id: Long): MailReviewDraftContext {
        val username = requireAdmin(actor)
        val row = access(username, id, false)
        val token = contextToken(row.contactId)
        return MailReviewDraftContext(token, row.approvedContextToken, token != row.approvedContextToken, now())
    }

    @Transactional
    fun confirmContext(actor: String?, id: Long, expectedVersion: Long, observedContextToken: String): MailReviewDraftConfirmation {
        val username = requireAdmin(actor)
        val row = writable(username, id, expectedVersion)
        state(row, MailReviewDraftState.APPROVED)
        val token = matchingContext(row, observedContextToken)
        val event = repository.appendEvent(row.id, row.version, "CONTEXT_CONFIRMED", username, mapOf("contextToken" to token), now())
        return MailReviewDraftConfirmation(event.id, token)
    }

    @Transactional(readOnly = true)
    fun translate(actor: String?, id: Long, expectedVersion: Long): Map<String, Any?> {
        val username = requireAdmin(actor)
        val row = access(username, id, false)
        if (row.deletedAt != null) conflictState()
        version(row, expectedVersion)
        val result = translation.translate(row.text)
        return mapOf("ok" to result.ok, "translatedText" to result.text, "reason" to result.reason)
    }

    @Transactional(readOnly = true)
    fun prepareSend(actor: String?, id: Long, request: MailReviewSendRequest): MailReviewSendRef {
        val username = requireAdmin(actor)
        val row = repository.find(id)?.takeIf { it.ownerUsername == MailReadOnlyPolicy.READER_USERNAME } ?: notFound()
        validateSendRow(row, request.expectedVersion)
        return MailReviewSendRef(row, username, request.confirmationEventId)
    }

    /** Called within the attempt transaction; review row is always locked before attempt. */
    fun lockForSend(ref: MailReviewSendRef): MailReviewDraftRow {
        requireAdmin(ref.actor)
        val row = access(ref.actor, ref.draft.id, true)
        validateSendRow(row, ref.draft.version)
        if (row.copy(sendAttemptId = null) != ref.draft.copy(sendAttemptId = null))
            throw MailReviewDraftException(409, "REVIEW_CONTENT_CHANGED", "Approved content changed")
        return row
    }

    /** Delivery has already happened: concurrent grant/target changes cannot discard its result. */
    fun lockForSendResult(ref: MailReviewSendRef): MailReviewDraftRow {
        val row = repository.find(ref.draft.id, true) ?: notFound()
        version(row, ref.draft.version)
        state(row, MailReviewDraftState.APPROVED)
        check(row.subject == ref.draft.subject && row.html == ref.draft.html && row.text == ref.draft.text &&
            row.contactId == ref.draft.contactId && row.targetId == ref.draft.targetId &&
            row.targetKind == ref.draft.targetKind && row.accountCode == ref.draft.accountCode &&
            row.sendRequestId == ref.draft.sendRequestId) { "Approved result identity changed" }
        return row
    }

    private fun validateSendRow(row: MailReviewDraftRow, expectedVersion: Long) {
        if (row.deletedAt != null) conflictState()
        version(row, expectedVersion)
        state(row, MailReviewDraftState.APPROVED)
        grant(row.ownerUsername, row.contactId, false)
        if (row.sendRequestId == null || !repository.validTarget(row))
            throw MailReviewDraftException(422, "REVIEW_TARGET_NOT_FOUND", "The original reply target is no longer valid")
    }

    fun validateSendContext(row: MailReviewDraftRow, ref: MailReviewSendRef) {
        val token = contextToken(row.contactId)
        if (token == row.approvedContextToken) return
        val event = ref.confirmationEventId?.let { repository.event(it) }
        if (event == null || event.draftId != row.id || event.draftVersion != row.version ||
            event.eventType != "CONTEXT_CONFIRMED" || event.actorUsername != ref.actor ||
            event.payload["contextToken"] != token)
            throw MailReviewDraftException(409, "REVIEW_CONTEXT_CHANGED", "Review and confirm the latest conversation before sending")
    }

    fun bindSend(row: MailReviewDraftRow, attemptId: Long, result: String) {
        repository.bindAttempt(row, attemptId)
        repository.appendEvent(row.id, row.version, "SEND_CLAIMED", "admin",
            mapOf("attemptId" to attemptId, "result" to result), now())
    }

    fun recordSendResult(row: MailReviewDraftRow, attemptId: Long, result: String, mailRecordId: Long?) {
        check(row.sendAttemptId == attemptId) { "Review attempt binding mismatch" }
        repository.appendSendResult(row, attemptId, result, mailRecordId, now())
    }

    private fun access(username: String, id: Long, lock: Boolean): MailReviewDraftRow {
        val observed = repository.find(id) ?: notFound()
        if (username == MailReadOnlyPolicy.READER_USERNAME) {
            if (observed.ownerUsername != username) notFound()
            // Grant always precedes draft lock: revoke and successful writes have an ordered outcome.
            grant(username, observed.contactId, lock)
        } else if (observed.ownerUsername != MailReadOnlyPolicy.READER_USERNAME || observed.submittedAt == null) notFound()
        val row = if (lock) repository.find(id, true) ?: notFound() else observed
        return row
    }

    private fun writable(username: String, id: Long, expectedVersion: Long): MailReviewDraftRow {
        val row = access(username, id, true)
        if (row.deletedAt != null) conflictState()
        version(row, expectedVersion)
        return row
    }

    private fun grant(username: String, contactId: Long, lock: Boolean) {
        if (!(if (lock) repository.lockAccess(username, contactId) else conversations.hasReadAccess(username, contactId)))
            throw MailReviewDraftException(403, "MAIL_READ_FORBIDDEN", "Mail read access is required")
    }

    private fun change(old: MailReviewDraftRow, value: MailReviewDraftRow, event: String, username: String, reason: String? = null): MailReviewDraftRow {
        if (old.version == Long.MAX_VALUE) conflictVersion()
        val next = value.copy(version = old.version + 1, updatedAt = now())
        if (!repository.update(next, old.version, old.state)) conflictVersion()
        snapshot(next, event, username, reason)
        return next
    }

    private fun snapshot(row: MailReviewDraftRow, event: String, username: String, reason: String? = null) {
        val payload = linkedMapOf<String, Any?>("subject" to row.subject, "html" to row.html, "text" to row.text, "state" to row.state.name)
        if (reason != null) payload["reason"] = reason
        repository.appendEvent(row.id, row.version, event, username, payload, now())
    }

    private fun response(row: MailReviewDraftRow, username: String): Any {
        if (row.deletedAt != null) return MailReviewDraftDeleted(row.id)
        val admin = username == MailReadOnlyPolicy.ADMIN_USERNAME
        val send = repository.sendProjection(row)
        return MailReviewDraftDetail(id = row.id, contactId = row.contactId, expertName = repository.contactName(row.contactId),
            ownerUsername = row.ownerUsername, version = row.version, state = row.state, subject = row.subject,
            html = row.html, text = row.text, accountCode = row.accountCode, targetKind = row.targetKind, targetId = row.targetId,
            submittedAt = row.submittedAt, returnReason = row.returnReason, reviewedAt = row.reviewedAt, deleted = false,
            createdAt = row.createdAt, updatedAt = row.updatedAt, commentCount = repository.commentCount(row.id),
            currentContextToken = if (admin) contextToken(row.contactId) else null,
            approvedContextToken = if (admin) row.approvedContextToken else null,
            sendStatus = send.sendStatus, sentMailRecordId = send.sentMailRecordId)
    }

    private data class Canonical(val subject: String, val html: String, val text: String)

    private fun canonical(subject: String, html: String): Canonical {
        if (subject.length > 255) invalid()
        size(html)
        val safelist = Safelist.none().addTags("p", "div", "br", "b", "strong", "i", "em", "u", "ul", "ol", "li", "blockquote", "a")
            .addAttributes("a", "href").addProtocols("a", "href", "http", "https", "mailto")
        val cleaned = Jsoup.clean(html, "", safelist, Document.OutputSettings().prettyPrint(false))
        val normalized = content.normalizeManualRichHtmlLineBreaks(cleaned)
        size(normalized)
        return Canonical(subject.replace("\r\n", "\n").replace('\r', '\n').trim(), normalized,
            content.normalizeManualTextLineBreaks(content.htmlToPlainText(normalized)))
    }

    private fun validateSubmission(row: MailReviewDraftRow) {
        if (row.subject.isBlank() || row.text.isBlank()) invalid()
        val texts = arrayOf(row.subject, row.html, row.text)
        try {
            texts.forEach { placeholders.requireValidPlaceholders(it) }
            if (placeholders.placeholderKeysIn(*texts).isNotEmpty())
                throw MailReviewDraftException(422, "REVIEW_INVALID_CONTENT", "请填写实际内容后提交")
        } catch (e: IllegalArgumentException) {
            throw MailReviewDraftException(422, "REVIEW_INVALID_CONTENT", "请填写实际内容后提交")
        }
        val hrefs = HREF_EXTRACTOR.findAll(row.html).map { match ->
            match.groupValues[1].ifEmpty { match.groupValues[2].ifEmpty { match.groupValues[3] } }
        }.toList()
        val finalText = listOfNotNull(row.subject.takeIf { it.isNotBlank() }, row.text.takeIf { it.isNotBlank() },
            content.htmlToPlainText(row.html).takeIf { it.isNotBlank() }, hrefs.takeIf { it.isNotEmpty() }?.joinToString(" ")).joinToString(" ")
        if (finalText.length > 20000) invalid()
    }

    private fun size(html: String) {
        if (html.toByteArray(Charsets.UTF_8).size > 1024 * 1024)
            throw MailReviewDraftException(413, "REVIEW_BODY_TOO_LARGE", "HTML exceeds 1 MiB")
    }
    private fun shortText(text: String): String {
        if (text.isBlank() || text.length > 2000) invalid()
        return text
    }
    private fun contextToken(contactId: Long): String = conversations.reviewContext(contactId,
        senderAccounts.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE).map { it.accountCode })
    private fun matchingContext(row: MailReviewDraftRow, observed: String): String {
        val current = contextToken(row.contactId)
        if (observed != current) throw MailReviewDraftException(409, "REVIEW_CONTEXT_CHANGED", "Review the latest conversation first")
        return current
    }
    private fun version(row: MailReviewDraftRow, expected: Long) { if (expected < 0 || expected != row.version) conflictVersion() }
    private fun state(row: MailReviewDraftRow, vararg allowed: MailReviewDraftState) { if (row.state !in allowed) conflictState() }
    private fun requireActor(actor: String?): String {
        if (actor == null) throw MailReviewDraftException(401, "UNAUTHORIZED", "Authentication required")
        if (actor != MailReadOnlyPolicy.ADMIN_USERNAME && actor != MailReadOnlyPolicy.READER_USERNAME)
            throw MailReviewDraftException(403, "READ_ONLY_FORBIDDEN", "Unknown review actor")
        return actor
    }
    private fun requireReader(actor: String?): String = requireActor(actor).also {
        if (it != MailReadOnlyPolicy.READER_USERNAME) throw MailReviewDraftException(403, "READ_ONLY_FORBIDDEN", "Only the owner may edit review drafts")
    }
    private fun requireAdmin(actor: String?): String = requireActor(actor).also {
        if (it != MailReadOnlyPolicy.ADMIN_USERNAME) throw MailReviewDraftException(403, "ADMIN_REQUIRED", "Administrator required")
    }
    private fun invalid(): Nothing = throw MailReviewDraftException(422, "REVIEW_INVALID_CONTENT", "Invalid review content")
    private fun notFound(): Nothing = throw MailReviewDraftException(404, "REVIEW_DRAFT_NOT_FOUND", "Review draft not found")
    private fun conflictVersion(): Nothing = throw MailReviewDraftException(409, "REVIEW_VERSION_CONFLICT", "Review version changed")
    private fun conflictState(): Nothing = throw MailReviewDraftException(409, "REVIEW_STATE_CONFLICT", "Review state does not allow this operation")
    private fun now(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)

    companion object {
        private val HREF_EXTRACTOR = Regex("""href\s*=\s*(?:"([^"]+)"|'([^']+)'|([^\s>"']+))""", RegexOption.IGNORE_CASE)
    }
}
