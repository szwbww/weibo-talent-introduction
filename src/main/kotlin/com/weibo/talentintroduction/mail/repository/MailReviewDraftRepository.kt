package com.weibo.talentintroduction.mail.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.mail.service.*
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.ResultSet
import java.time.LocalDateTime

@Repository
class MailReviewDraftRepository(private val jdbc: NamedParameterJdbcTemplate, private val objectMapper: ObjectMapper) {
    private val mapper = RowMapper { r: ResultSet, _: Int ->
        MailReviewDraftRow(r.getLong("id"), r.getString("owner_username"), r.getLong("expert_contact_id"),
            r.getString("client_request_id"), r.getString("target_kind"), r.getLong("target_id"), r.getString("account_code"),
            r.getLong("version"), MailReviewDraftState.valueOf(r.getString("state")), r.getString("subject"),
            r.getString("html_body"), r.getString("text_body"), r.time("submitted_at"), r.getString("return_reason"),
            r.getString("reviewed_by"), r.time("reviewed_at"), r.getString("approved_context_token"), r.getString("send_request_id"),
            (r.getObject("send_attempt_id") as? Number)?.toLong(), r.time("deleted_at"),
            requireNotNull(r.time("created_at")), requireNotNull(r.time("updated_at")))
    }
    private fun ResultSet.time(column: String): LocalDateTime? = getTimestamp(column)?.toLocalDateTime()
    private fun transaction() = check(TransactionSynchronizationManager.isActualTransactionActive()) { "Review writes and locks require a transaction" }

    fun find(id: Long, lock: Boolean = false): MailReviewDraftRow? {
        if (lock) transaction()
        return jdbc.query("SELECT * FROM mailbox_review_draft WHERE id=:id" + if (lock) " FOR UPDATE" else "", mapOf("id" to id), mapper).singleOrNull()
    }
    fun findByRequest(owner: String, clientRequestId: String): MailReviewDraftRow? = jdbc.query(
        "SELECT * FROM mailbox_review_draft WHERE owner_username=:owner AND client_request_id=:request",
        mapOf("owner" to owner, "request" to clientRequestId), mapper).singleOrNull()

    private fun params(r: MailReviewDraftRow) = MapSqlParameterSource()
        .addValue("id", r.id).addValue("owner", r.ownerUsername).addValue("contact", r.contactId)
        .addValue("request", r.clientRequestId).addValue("kind", r.targetKind).addValue("target", r.targetId)
        .addValue("account", r.accountCode).addValue("version", r.version).addValue("state", r.state.name)
        .addValue("subject", r.subject).addValue("html", r.html).addValue("text", r.text)
        .addValue("submitted", r.submittedAt).addValue("reason", r.returnReason).addValue("reviewer", r.reviewedBy)
        .addValue("reviewed", r.reviewedAt).addValue("token", r.approvedContextToken).addValue("sendRequest", r.sendRequestId)
        .addValue("attempt", r.sendAttemptId).addValue("deleted", r.deletedAt).addValue("created", r.createdAt).addValue("updated", r.updatedAt)

    fun insert(row: MailReviewDraftRow): MailReviewDraftRow {
        transaction()
        require(row.version == 1L && row.state == MailReviewDraftState.DRAFT && row.ownerUsername == "user1")
        val keys = GeneratedKeyHolder()
        jdbc.update("""INSERT INTO mailbox_review_draft
            (owner_username,expert_contact_id,client_request_id,target_kind,target_id,account_code,version,state,subject,html_body,text_body,created_at,updated_at)
            VALUES (:owner,:contact,:request,:kind,:target,:account,1,'DRAFT',:subject,:html,:text,:created,:updated)""", params(row), keys)
        return row.copy(id = requireNotNull(keys.key).toLong())
    }

    /** Identity, first submission, and send binding are not writable through a content/state CAS. */
    fun update(row: MailReviewDraftRow, expectedVersion: Long, expectedState: MailReviewDraftState): Boolean {
        transaction()
        require(expectedVersion >= 1 && row.version == expectedVersion + 1)
        return jdbc.update("""UPDATE mailbox_review_draft SET version=version+1,state=:state,subject=:subject,
            html_body=:html,text_body=:text,submitted_at=COALESCE(submitted_at,:submitted),return_reason=:reason,
            reviewed_by=:reviewer,reviewed_at=:reviewed,approved_context_token=:token,send_request_id=:sendRequest,
            deleted_at=:deleted,updated_at=:updated
            WHERE id=:id AND owner_username=:owner AND version=:expectedVersion AND state=:expectedState
              AND deleted_at IS NULL AND state<>'APPROVED'""",
            params(row).addValue("expectedVersion", expectedVersion).addValue("expectedState", expectedState.name)) == 1
    }

    fun lockAccess(owner: String, contactId: Long): Boolean {
        transaction()
        return jdbc.queryForList("SELECT username FROM expert_mail_read_access WHERE username=:owner AND expert_contact_id=:contact FOR UPDATE",
            mapOf("owner" to owner, "contact" to contactId)).isNotEmpty()
    }
    fun contactName(contactId: Long): String? = jdbc.query("SELECT expert_name FROM expert_contact WHERE id=:id", mapOf("id" to contactId),
        RowMapper { r, _ -> r.getString("expert_name") }).singleOrNull()

    fun resolveTarget(contactId: Long): MailReviewDraftTarget? {
        val p = mapOf("contact" to contactId, "simulator" to MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        fun target(sql: String) = jdbc.query(sql, p, RowMapper { r, _ -> MailReviewDraftTarget(r.getString("kind"), r.getLong("id"), r.getString("sender_account_code")) }).firstOrNull()
        return target("""SELECT 'INBOUND' AS kind,p.id,p.sender_account_code FROM inbound_mail_processing p
            JOIN mail_sender_account a ON a.account_code=p.sender_account_code
            WHERE p.expert_contact_id=:contact AND a.account_code<>:simulator ORDER BY p.received_at DESC,p.id DESC LIMIT 1""")
            ?: target("""SELECT 'OUTBOUND' AS kind,m.id,m.sender_account_code FROM mail_record m
                JOIN mail_sender_account a ON a.account_code=m.sender_account_code
                WHERE m.expert_contact_id=:contact AND m.direction='OUTBOUND' AND m.send_status='SENT'
                  AND a.account_code<>:simulator ORDER BY COALESCE(m.sent_at,m.created_at) DESC,m.id DESC LIMIT 1""")
    }

    fun validTarget(row: MailReviewDraftRow): Boolean {
        val target = when (row.targetKind) {
            "INBOUND" -> """SELECT p.id FROM inbound_mail_processing p JOIN mail_sender_account a
                ON a.account_code=p.sender_account_code JOIN expert_contact c ON c.id=p.expert_contact_id
                WHERE p.id=:target AND p.expert_contact_id=:contact AND p.sender_account_code=:account"""
            "OUTBOUND" -> """SELECT m.id FROM mail_record m JOIN mail_sender_account a
                ON a.account_code=m.sender_account_code JOIN expert_contact c ON c.id=m.expert_contact_id
                WHERE m.id=:target AND m.expert_contact_id=:contact AND m.sender_account_code=:account
                AND m.direction='OUTBOUND' AND m.send_status='SENT'"""
            else -> return false
        }
        return row.accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE && jdbc.queryForList(target,
            mapOf("target" to row.targetId, "contact" to row.contactId, "account" to row.accountCode)).size == 1
    }

    fun bindAttempt(row: MailReviewDraftRow, attemptId: Long) {
        transaction()
        check(jdbc.update("""UPDATE mailbox_review_draft SET send_attempt_id=:attempt
            WHERE id=:id AND version=:version AND state='APPROVED' AND deleted_at IS NULL
            AND (send_attempt_id IS NULL OR send_attempt_id=:attempt)""",
            mapOf("id" to row.id, "version" to row.version, "attempt" to attemptId)) == 1) { "Review attempt binding conflict" }
    }

    fun sendProjection(row: MailReviewDraftRow): MailReviewSendProjection {
        val attempt = row.sendAttemptId ?: return MailReviewSendProjection("NOT_SENT", null)
        return jdbc.query("""SELECT a.status,m.id AS record_id FROM mail_send_attempt a
            LEFT JOIN mail_record m ON m.mail_send_attempt_id=a.id AND m.send_status='SENT'
                AND m.direction='OUTBOUND' AND m.expert_contact_id=:contact AND m.sender_account_code=:account
                AND m.subject=:subject AND m.body=:text AND m.message_id=a.message_id
            WHERE a.id=:attempt""", mapOf("attempt" to attempt, "contact" to row.contactId,
            "account" to row.accountCode, "subject" to row.subject, "text" to row.text),
            RowMapper { r, _ -> MailReviewSendProjection(r.getString("status"),
                if (r.getString("status") == "SENT") (r.getObject("record_id") as? Number)?.toLong() else null) })
            .singleOrNull() ?: MailReviewSendProjection("NOT_SENT", null)
    }

    fun event(id: Long): MailReviewDraftEvent? = jdbc.query(
        "SELECT * FROM mailbox_review_draft_event WHERE id=:id", mapOf("id" to id), eventMapper).singleOrNull()

    fun appendSendResult(row: MailReviewDraftRow, attemptId: Long, result: String, mailRecordId: Long?, now: LocalDateTime) {
        transaction()
        val type = if (result == "SENT") "SEND_SUCCEEDED" else "SEND_RESULT"
        val exists = jdbc.queryForObject("""SELECT COUNT(*) FROM mailbox_review_draft_event
            WHERE draft_id=:id AND draft_version=:version AND event_type=:type
            AND JSON_EXTRACT(payload_json,'$.attemptId')=:attempt
            AND JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.result'))=:result""",
            mapOf("id" to row.id, "version" to row.version, "type" to type, "attempt" to attemptId, "result" to result), Long::class.java) ?: 0
        if (exists == 0L) {
            val payload = linkedMapOf<String, Any?>("attemptId" to attemptId, "result" to result)
            if (mailRecordId != null) payload["mailRecordId"] = mailRecordId
            appendEvent(row.id, row.version, type, "admin", payload, now)
        }
    }

    private fun visibility(actor: String, contactId: Long?, includeDeleted: Boolean): String {
        val role = when (actor) {
            "admin" -> "d.owner_username='user1' AND d.submitted_at IS NOT NULL"
            "user1" -> "d.owner_username=:actor AND EXISTS (SELECT 1 FROM expert_mail_read_access g WHERE g.username=:actor AND g.expert_contact_id=d.expert_contact_id)"
            else -> "1=0"
        }
        return " WHERE $role" + (if (contactId == null) "" else " AND d.expert_contact_id=:contact") +
            (if (includeDeleted) "" else " AND d.deleted_at IS NULL")
    }
    fun list(actor: String, contactId: Long?, page: Int, size: Int, includeDeleted: Boolean): List<MailReviewDraftRow> = jdbc.query(
        "SELECT d.* FROM mailbox_review_draft d" + visibility(actor, contactId, includeDeleted) + " ORDER BY d.updated_at DESC,d.id DESC LIMIT :size OFFSET :offset",
        mapOf("actor" to actor, "contact" to contactId, "size" to size, "offset" to page.toLong() * size), mapper)
    fun count(actor: String, contactId: Long?, includeDeleted: Boolean): Long = jdbc.queryForObject(
        "SELECT COUNT(*) FROM mailbox_review_draft d" + visibility(actor, contactId, includeDeleted),
        mapOf("actor" to actor, "contact" to contactId), Long::class.java) ?: 0L
    fun commentCount(id: Long): Long = jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_review_draft_event WHERE draft_id=:id AND event_type='COMMENT'", mapOf("id" to id), Long::class.java) ?: 0L

    fun appendEvent(draftId: Long, draftVersion: Long, eventType: String, actor: String, payload: Map<String, Any?>, createdAt: LocalDateTime): MailReviewDraftEvent {
        transaction()
        validatePayload(eventType, payload)
        val key = GeneratedKeyHolder()
        jdbc.update("""INSERT INTO mailbox_review_draft_event (draft_id,draft_version,event_type,actor_username,payload_json,created_at)
            VALUES (:draft,:version,:type,:actor,:payload,:created)""", MapSqlParameterSource()
            .addValue("draft", draftId).addValue("version", draftVersion).addValue("type", eventType).addValue("actor", actor)
            .addValue("payload", objectMapper.writeValueAsString(payload)).addValue("created", createdAt), key)
        return MailReviewDraftEvent(requireNotNull(key.key).toLong(), draftId, draftVersion, eventType, actor, payload, createdAt)
    }
    private val eventMapper = RowMapper { r: ResultSet, _: Int ->
        val type = r.getString("event_type")
        val node = objectMapper.factory.createParser(r.getString("payload_json")).use { parser ->
            parser.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            val parsed = objectMapper.readTree<com.fasterxml.jackson.databind.JsonNode>(parser)
            require(parser.nextToken() == null) { "Trailing review event payload" }
            parsed
        }
        require(node.isObject) { "Invalid review event payload" }
        val payload = node.fields().asSequence().associate { (key, value) ->
            key to when { value.isTextual -> value.textValue(); value.isIntegralNumber && value.canConvertToLong() -> value.longValue(); value.isNull -> null; else -> error("Invalid review event value") }
        }
        validatePayload(type, payload)
        MailReviewDraftEvent(r.getLong("id"), r.getLong("draft_id"), r.getLong("draft_version"), type,
            r.getString("actor_username"), payload, requireNotNull(r.time("created_at")))
    }
    fun events(id: Long, beforeId: Long?, limit: Int): List<MailReviewDraftEvent> = jdbc.query(
        "SELECT * FROM mailbox_review_draft_event WHERE draft_id=:id" + (if (beforeId == null) "" else " AND id<:before") + " ORDER BY id DESC LIMIT :limit",
        mapOf("id" to id, "before" to beforeId, "limit" to limit), eventMapper)
    fun creationEvent(draftId: Long): MailReviewDraftEvent? = jdbc.query(
        "SELECT * FROM mailbox_review_draft_event WHERE draft_id=:id AND event_type='CREATE' ORDER BY id LIMIT 1", mapOf("id" to draftId), eventMapper).singleOrNull()

    private fun validatePayload(type: String, payload: Map<String, Any?>) {
        val snapshots = setOf("CREATE", "SAVE", "SUBMIT", "WITHDRAW", "RETURN", "APPROVE", "DELETE")
        val required: Set<String>
        val optional: Set<String>
        when {
            type in snapshots -> { required = setOf("subject", "html", "text", "state"); optional = setOf("reason") }
            type == "COMMENT" -> { required = setOf("text"); optional = emptySet() }
            type == "CONTEXT_CONFIRMED" -> { required = setOf("contextToken"); optional = emptySet() }
            type in setOf("SEND_CLAIMED", "SEND_SUCCEEDED", "SEND_RESULT", "SEND_FAILED", "SEND_UNKNOWN") -> { required = setOf("attemptId", "result"); optional = setOf("mailRecordId") }
            else -> throw IllegalStateException("Unknown review event type")
        }
        require(payload.keys.containsAll(required) && payload.keys.all { it in required || it in optional }) { "Invalid review event schema" }
        payload.forEach { (key, value) ->
            if (key == "attemptId" || key == "mailRecordId") require(value is Long && value > 0)
            else require(value is String) { "Invalid review event string" }
        }
        if (type in snapshots) MailReviewDraftState.valueOf(payload["state"] as String)
        if (type == "COMMENT" || type == "RETURN") require((payload[if (type == "COMMENT") "text" else "reason"] as? String)?.let { it.isNotBlank() && it.length <= 2000 } == true)
        if (type == "CONTEXT_CONFIRMED") require((payload["contextToken"] as String).matches(Regex("[0-9a-f]{64}")))
    }
}
