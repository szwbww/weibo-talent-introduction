package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException
import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import com.weibo.talentintroduction.campaign.controller.ExpertContactManagementController
import com.weibo.talentintroduction.campaign.controller.MeetingCalendarController
import com.weibo.talentintroduction.document.controller.ExpertDocumentBrowseController
import com.weibo.talentintroduction.document.controller.ExpertMaterialController
import com.weibo.talentintroduction.mail.controller.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerMapping
import java.util.Locale
import java.util.UUID
import javax.servlet.http.HttpServletRequest

/** Fixed business-resource admission. Reads metadata only; never opens files, ES or SMTP. */
@Service
class ExpertMailPermissionGuard(
    private val accessService: ExpertMailReadAccessService,
    private val jdbc: NamedParameterJdbcTemplate
) {
    companion object {
        const val MAIL_WRITE_FORBIDDEN = "MAIL_WRITE_FORBIDDEN"

        private enum class Target { CONTACT, PROCESSING, MESSAGE, ATTACHMENT, DOCUMENT, MAIL, OUTBOUND, SENT_OUTBOUND, EVENTS, SUMMARIES, EVENT, MEETING_OPTIONS, MEETING_PREVIEW }
        private data class Key(val controller: Class<*>, val method: String, val verb: String)
        private val targets = buildMap<Key, Target> {
            fun route(controller: Class<*>, method: String, verb: String, target: Target) {
                put(Key(controller, method, verb), target)
            }
            val conversation = MailboxConversationController::class.java
            route(conversation, "conversationManualRichReply", "POST", Target.CONTACT)
            route(conversation, "follow", "PUT", Target.CONTACT)
            route(conversation, "unfollow", "DELETE", Target.CONTACT)
            route(conversation, "setProgressStatus", "PUT", Target.CONTACT)
            route(conversation, "dismissReplied", "PUT", Target.CONTACT)
            route(conversation, "suspension", "GET", Target.CONTACT)
            route(conversation, "suspend", "PUT", Target.CONTACT)
            route(conversation, "updateSuspensionReason", "PATCH", Target.CONTACT)
            route(conversation, "resumeSuspension", "DELETE", Target.CONTACT)
            val unmatched = UnmatchedInboundMailController::class.java
            route(unmatched, "getUnmatchedDetail", "GET", Target.PROCESSING)
            route(unmatched, "sendManualRichReply", "POST", Target.PROCESSING)
            route(unmatched, "markResolved", "POST", Target.PROCESSING)
            route(unmatched, "cancelResolved", "POST", Target.PROCESSING)
            route(MailboxAttachmentController::class.java, "listAttachments", "GET", Target.MESSAGE)
            route(MailboxAttachmentController::class.java, "download", "GET", Target.ATTACHMENT)
            route(OutboundAttachmentController::class.java, "upload", "POST", Target.CONTACT)
            route(OutboundAttachmentController::class.java, "downloadDraft", "GET", Target.OUTBOUND)
            route(OutboundAttachmentController::class.java, "downloadSentAttachment", "GET", Target.SENT_OUTBOUND)
            route(CalendarAttachmentController::class.java, "download", "GET", Target.MAIL)
            val materials = ExpertMaterialController::class.java
            route(materials, "listMaterials", "GET", Target.CONTACT)
            route(materials, "requestTransfers", "POST", Target.CONTACT)
            route(materials, "uploadMaterial", "POST", Target.CONTACT)
            route(materials, "downloadZip", "POST", Target.CONTACT)
            route(materials, "reconcile", "POST", Target.CONTACT)
            val documents = ExpertDocumentBrowseController::class.java
            route(documents, "listDocuments", "GET", Target.CONTACT)
            route(documents, "download", "GET", Target.DOCUMENT)
            route(documents, "preview", "GET", Target.DOCUMENT)
            val contacts = ExpertContactManagementController::class.java
            route(contacts, "getContactDetail", "GET", Target.CONTACT)
            route(contacts, "changeOperatorStatus", "POST", Target.CONTACT)
            route(contacts, "changeIndexLevel", "POST", Target.CONTACT)
            route(ExpertContactNoteController::class.java, "get", "GET", Target.CONTACT)
            route(ExpertContactNoteController::class.java, "save", "PUT", Target.CONTACT)
            route(ExpertContactLocationController::class.java, "get", "GET", Target.CONTACT)
            route(ExpertContactLocationController::class.java, "save", "PUT", Target.CONTACT)
            route(ExpertContactLocationController::class.java, "timing", "GET", Target.CONTACT)
            val calendar = MeetingCalendarController::class.java
            route(calendar, "events", "GET", Target.EVENTS)
            route(calendar, "summaries", "GET", Target.SUMMARIES)
            route(calendar, "event", "GET", Target.EVENT)
            route(calendar, "update", "PUT", Target.EVENT)
            route(calendar, "cancel", "POST", Target.EVENT)
            route(MeetingConfirmationController::class.java, "options", "GET", Target.MEETING_OPTIONS)
            route(MeetingConfirmationController::class.java, "preview", "POST", Target.MEETING_PREVIEW)
        }

        private fun target(handler: Any?, verb: String?): Target? {
            val method = (handler as? HandlerMethod)?.method ?: return null
            return targets[Key(method.declaringClass, method.name, verb?.uppercase(Locale.ROOT).orEmpty())]
        }

        fun isResourceHandler(handler: Any?, verb: String?): Boolean = target(handler, verb) != null

        private fun forbidden(): Nothing = throw MailReadAccessDeniedException(
            MAIL_WRITE_FORBIDDEN, "无权操作该专家的邮件资源"
        )
    }

    /** Service-entry recheck also precedes completed-attempt/draft returns. Null stays internal, not user1. */
    fun requireFull(authenticatedUsername: String?, contactId: Long) {
        if (!MailReadOnlyPolicy.isReader(authenticatedUsername)) return
        require(contactId > 0) { "contactId must be positive" }
        if (accessService.accessLevel(contactId) != ExpertMailAccessLevel.FULL) forbidden()
    }

    fun authorize(username: String, handler: Any, request: HttpServletRequest) {
        if (!MailReadOnlyPolicy.isReader(username)) return
        val rule = target(handler, request.method) ?: forbidden()
        @Suppress("UNCHECKED_CAST")
        val path = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<String, String>
            ?: emptyMap()
        fun id(key: String): Long = positive(path[key], key)
        fun contact(): Long = id("contactId").also { requireContact(it); requireFull(username, it) }
        fun match(actual: Long?, expected: Long) { if (actual == null || actual != expected) forbidden() }
        when (rule) {
            Target.CONTACT -> contact()
            Target.PROCESSING -> requireFull(username, owner("inbound_mail_processing", id("id")))
            Target.MESSAGE -> {
                val table = when (path["source"]) {
                    "MAIL_RECORD" -> "mail_record"
                    "INBOUND_PROCESSING" -> "inbound_mail_processing"
                    else -> throw IllegalArgumentException("Invalid mail source")
                }
                requireFull(username, owner(table, id("id")))
            }
            Target.ATTACHMENT -> requireFull(username, attachmentOwner(id("attachmentId")))
            Target.DOCUMENT -> {
                val contactId = contact()
                match(attachmentOwner(id("attachmentId")), contactId)
            }
            Target.MAIL -> {
                val contactId = contact()
                match(owner("mail_record", id("mailRecordId")), contactId)
            }
            Target.OUTBOUND, Target.SENT_OUTBOUND -> {
                val contactId = contact()
                if (rule == Target.SENT_OUTBOUND) match(owner("mail_record", id("mailRecordId")), contactId)
                val rawId = requireNotNull(path[if (rule == Target.OUTBOUND) "id" else "attachmentId"]) { "Attachment id is required" }
                val uuid = try { UUID.fromString(rawId) } catch (ex: IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid attachment id", ex)
                }
                require(uuid.toString() == rawId) { "Invalid attachment id" }
                val rows = jdbc.queryForList("SELECT expert_contact_id FROM outbound_mail_attachment WHERE id = :id", mapOf("id" to rawId))
                val row = rows.singleOrNull() ?: throw NoSuchElementException("Attachment not found")
                match((row["expert_contact_id"] as? Number)?.toLong(), contactId)
            }
            Target.EVENTS -> {
                val contactId = request.getParameter("contactId") ?: forbidden()
                val parsed = positive(contactId, "contactId")
                requireContact(parsed)
                requireFull(username, parsed)
            }
            Target.SUMMARIES -> {
                val ids = request.getParameterValues("contactIds")?.takeIf { it.isNotEmpty() && it.all(String::isNotBlank) } ?: forbidden()
                ids.flatMap { it.split(',') }.map { positive(it.trim(), "contactIds") }.distinct().forEach {
                    requireContact(it)
                    requireFull(username, it)
                }
            }
            Target.EVENT -> requireFull(username, owner("meeting_calendar_event", id("id")))
            Target.MEETING_OPTIONS -> {
                val actual = owner("inbound_mail_processing", id("processingId"))
                requireFull(username, actual)
                match(positive(request.getParameter("contactId"), "contactId"), actual)
            }
            Target.MEETING_PREVIEW -> requireFull(username, owner("inbound_mail_processing", id("processingId")))
        }
    }

    private fun positive(raw: String?, label: String): Long = raw?.toLongOrNull()?.takeIf { it > 0 }
        ?: throw IllegalArgumentException("$label must be a positive integer")

    private fun requireContact(id: Long) {
        if (jdbc.queryForList("SELECT id FROM expert_contact WHERE id = :id", mapOf("id" to id)).isEmpty())
            throw NoSuchElementException("Expert contact not found: $id")
    }

    /** Table names are private fixed literals from the resolver above, never request input. */
    private fun owner(table: String, id: Long): Long {
        val row = jdbc.queryForList("SELECT expert_contact_id FROM $table WHERE id = :id", mapOf("id" to id))
            .singleOrNull() ?: throw NoSuchElementException("Resource not found: $id")
        return (row["expert_contact_id"] as? Number)?.toLong() ?: forbidden()
    }

    private fun attachmentOwner(id: Long): Long {
        val row = jdbc.queryForList(
            "SELECT mail_record_id, inbound_processing_id, manual_upload_id FROM mail_attachment WHERE id = :id", mapOf("id" to id)
        ).singleOrNull() ?: throw NoSuchElementException("Attachment not found: $id")
        val record = (row["mail_record_id"] as? Number)?.toLong()
        val processing = (row["inbound_processing_id"] as? Number)?.toLong()
        val manual = (row["manual_upload_id"] as? Number)?.toLong()
        if (listOfNotNull(record, processing, manual).size != 1) forbidden()
        return when {
            record != null -> owner("mail_record", record)
            processing != null -> owner("inbound_mail_processing", processing)
            else -> owner("manual_expert_material_upload", requireNotNull(manual))
        }
    }
}
