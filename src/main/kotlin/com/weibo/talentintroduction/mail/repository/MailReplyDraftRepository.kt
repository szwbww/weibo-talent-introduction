package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.service.*
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.ResultSet
import java.time.LocalDateTime

@Repository
class MailReplyDraftRepository(private val jdbc: NamedParameterJdbcTemplate) {
    private val mapper = RowMapper { r: ResultSet, _: Int ->
        MailReplyDraftRow(r.getLong("id"), r.getString("username"),
            MailReplyDraftTarget(r.getLong("expert_contact_id"), MailReplyDraftKind.valueOf(r.getString("target_kind")), r.getLong("inbound_processing_id"), r.getString("account_scope")),
            r.getLong("version"), MailReplyDraftState.valueOf(r.getString("state")), r.getString("subject"), r.getString("html_body"), r.getString("text_body"), r.getString("context_json"),
            r.getObject("send_attempt_id")?.let { (it as Number).toLong() }, r.getObject("send_version")?.let { (it as Number).toLong() },
            r.getTimestamp("created_at").toLocalDateTime(), r.getTimestamp("updated_at").toLocalDateTime(),
            r.getBoolean("contact_exists"), r.getString("expert_name"), r.getString("expert_email"), r.getString("send_attempt_status"))
    }
    private val projection = """SELECT d.*, c.id IS NOT NULL AS contact_exists, c.expert_name, c.expert_email,
        a.status AS send_attempt_status FROM mailbox_reply_draft d
        LEFT JOIN expert_contact c ON c.id=d.expert_contact_id
        LEFT JOIN mail_send_attempt a ON a.id=d.send_attempt_id"""
    private fun targetParams(owner: String, target: MailReplyDraftTarget) = mapOf("owner" to owner, "contact" to target.contactId,
        "kind" to target.kind.name, "processing" to target.processingId, "scope" to target.accountScope)
    fun findTarget(owner: String, target: MailReplyDraftTarget): MailReplyDraftRow? = jdbc.query("$projection WHERE d.username=:owner AND d.expert_contact_id=:contact AND d.target_kind=:kind AND d.inbound_processing_id=:processing AND d.account_scope=:scope", targetParams(owner, target), mapper).singleOrNull()
    fun findOwned(owner: String, id: Long): MailReplyDraftRow? = jdbc.query("$projection WHERE d.username=:owner AND d.id=:id", mapOf("owner" to owner, "id" to id), mapper).singleOrNull()
    fun insert(owner: String, target: MailReplyDraftTarget, content: MailReplyDraftContent, contextJson: String, now: LocalDateTime): Long {
        val params = MapSqlParameterSource(targetParams(owner, target)).addValue("subject", content.subject).addValue("html", content.html).addValue("text", content.text).addValue("context", contextJson).addValue("now", now)
        val key = GeneratedKeyHolder()
        jdbc.update("""INSERT INTO mailbox_reply_draft (username,expert_contact_id,target_kind,inbound_processing_id,account_scope,version,state,subject,html_body,text_body,context_json,created_at,updated_at)
            VALUES (:owner,:contact,:kind,:processing,:scope,1,'ACTIVE',:subject,:html,:text,:context,:now,:now)""", params, key)
        return requireNotNull(key.key).toLong()
    }
    fun save(owner: String, id: Long, version: Long, state: MailReplyDraftState, content: MailReplyDraftContent, contextJson: String, now: LocalDateTime): Boolean {
        val resetBinding = if (state == MailReplyDraftState.ACTIVE) "" else ",send_attempt_id=NULL,send_version=NULL"
        return jdbc.update("""UPDATE mailbox_reply_draft SET version=version+1,state='ACTIVE',subject=:subject,html_body=:html,text_body=:text,context_json=:context,updated_at=:now$resetBinding
            WHERE id=:id AND username=:owner AND version=:version AND state=:state""",
            mapOf("owner" to owner,"id" to id,"version" to version,"state" to state.name,"subject" to content.subject,"html" to content.html,"text" to content.text,"context" to contextJson,"now" to now)) == 1
    }
    fun discard(owner: String, id: Long, version: Long, now: LocalDateTime): Boolean = jdbc.update("""UPDATE mailbox_reply_draft SET state='DISCARDED',version=version+1,subject=NULL,html_body=NULL,text_body=NULL,context_json=NULL,updated_at=:now
        WHERE id=:id AND username=:owner AND version=:version AND state='ACTIVE'""", mapOf("owner" to owner,"id" to id,"version" to version,"now" to now)) == 1

    private fun scopeSql(scope: String?) = if (scope == null) "" else " AND d.account_scope=:scope"
    private fun listParams(owner: String, scope: String?, search: String?): Map<String, Any?> = mapOf("owner" to owner,"scope" to scope,
        "search" to ("%" + search.orEmpty().replace("!","!!").replace("%","!%").replace("_","!_") + "%"))
    fun list(owner: String, scope: String?, search: String?, page: Int, size: Int): MailReplyDraftList {
        val filter = " WHERE d.username=:owner AND d.state='ACTIVE'" + scopeSql(scope) +
            if (search.isNullOrEmpty()) "" else " AND (c.expert_name LIKE :search ESCAPE '!' OR c.expert_email LIKE :search ESCAPE '!' OR d.subject LIKE :search ESCAPE '!')"
        val params = listParams(owner,scope,search) + mapOf("limit" to size,"offset" to page.toLong()*size)
        val total = jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_reply_draft d LEFT JOIN expert_contact c ON c.id=d.expert_contact_id$filter",params,Long::class.java) ?: 0
        // Do not fetch large HTML/context merely to render a list preview.
        val items = jdbc.query("""SELECT d.id,d.version,d.state,d.expert_contact_id,d.target_kind,d.inbound_processing_id,d.account_scope,d.subject,
            LEFT(d.text_body,120) AS preview,d.updated_at,c.id IS NOT NULL AS contact_exists,c.expert_name,c.expert_email
            FROM mailbox_reply_draft d LEFT JOIN expert_contact c ON c.id=d.expert_contact_id$filter ORDER BY d.updated_at DESC,d.id DESC LIMIT :limit OFFSET :offset""",params,
            RowMapper { r, _ -> MailReplyDraftListItem(r.getLong("id"),r.getLong("version"),MailReplyDraftState.valueOf(r.getString("state")),
                MailReplyDraftTarget(r.getLong("expert_contact_id"),MailReplyDraftKind.valueOf(r.getString("target_kind")),r.getLong("inbound_processing_id"),r.getString("account_scope")),
                r.getString("subject"),r.getString("preview").orEmpty(),r.getTimestamp("updated_at").toLocalDateTime(),r.getBoolean("contact_exists"),r.getString("expert_name"),r.getString("expert_email")) })
        return MailReplyDraftList(items,total,page,size)
    }
    fun summaries(owner: String, scope: String?, contacts: List<Long>): MailReplyDraftSummaries {
        val filter = " WHERE d.username=:owner AND d.state='ACTIVE'" + scopeSql(scope)
        val params = mapOf("owner" to owner,"scope" to scope,"contacts" to contacts)
        val total = jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_reply_draft d$filter",params,Long::class.java) ?: 0
        val counts = if (contacts.isEmpty()) emptyMap() else jdbc.query("SELECT d.expert_contact_id,COUNT(*) AS n FROM mailbox_reply_draft d$filter AND d.expert_contact_id IN (:contacts) GROUP BY d.expert_contact_id",params,
            RowMapper { r, _ -> r.getLong("expert_contact_id") to r.getLong("n") }).toMap()
        return MailReplyDraftSummaries(contacts.map { MailReplyDraftSummary(it,counts[it] ?: 0) },total)
    }

    /** Child 02 calls these only inside its existing sending transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun lockOwned(owner: String, id: Long): MailReplyDraftRow? {
        mandatory()
        // Lock only the draft row, not the optional LEFT JOIN business rows.
        jdbc.queryForList("SELECT id FROM mailbox_reply_draft WHERE username=:owner AND id=:id FOR UPDATE",mapOf("owner" to owner,"id" to id))
        return findOwned(owner,id)
    }
    @Transactional(propagation = Propagation.MANDATORY)
    fun bindAttempt(owner: String, id: Long, version: Long, attemptId: Long): Boolean {
        mandatory(); require(attemptId > 0 && version > 0)
        return jdbc.update("""UPDATE mailbox_reply_draft SET send_attempt_id=:attempt,send_version=:version
            WHERE username=:owner AND id=:id AND version=:version AND state='ACTIVE'
            AND ((send_attempt_id IS NULL AND send_version IS NULL) OR (send_attempt_id=:attempt AND send_version=:version))""",
            mapOf("owner" to owner,"id" to id,"version" to version,"attempt" to attemptId)) == 1
    }
    @Transactional(propagation = Propagation.MANDATORY)
    fun closeSent(owner: String, id: Long, sendVersion: Long, attemptId: Long, now: LocalDateTime): Boolean {
        mandatory()
        return jdbc.update("""UPDATE mailbox_reply_draft SET state='SENT',version=version+1,subject=NULL,html_body=NULL,text_body=NULL,context_json=NULL,updated_at=:now
            WHERE username=:owner AND id=:id AND version=:version AND state='ACTIVE' AND send_attempt_id=:attempt AND send_version=:version""",
            mapOf("owner" to owner,"id" to id,"version" to sendVersion,"attempt" to attemptId,"now" to now)) == 1
    }
    @Transactional(propagation = Propagation.MANDATORY)
    fun releaseCompletedBinding(owner: String, id: Long, sendVersion: Long, attemptId: Long): Boolean {
        mandatory()
        return jdbc.update("UPDATE mailbox_reply_draft SET send_attempt_id=NULL,send_version=NULL WHERE username=:owner AND id=:id AND send_attempt_id=:attempt AND send_version=:version",
            mapOf("owner" to owner,"id" to id,"attempt" to attemptId,"version" to sendVersion)) == 1
    }
    private fun mandatory() { check(TransactionSynchronizationManager.isActualTransactionActive()) { "Draft send operations require an existing transaction" } }
}
