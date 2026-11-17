package com.weibo.talentintroduction.document.repository

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class MaterialFileMetadata(
    val attachmentId: Long,
    val parentAttachmentId: Long? = null,
    val entryPath: String? = null,
    val displayName: String? = null,
    val contentNote: String = "",
    val tags: List<String> = emptyList(),
    val deleted: Boolean = false,
    val extractionState: String = "PENDING",
    val extractionError: String? = null
)

@Repository
class MaterialFileRepository(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper) {
    fun forContact(contactId: Long): Map<Long, MaterialFileMetadata> = jdbc.query(
        """SELECT f.* FROM expert_material_file f JOIN expert_document d ON d.mail_attachment_id=f.attachment_id
           WHERE d.expert_contact_id=?""", { rs, _ ->
            MaterialFileMetadata(rs.getLong("attachment_id"), rs.getObject("parent_attachment_id")?.let { (it as Number).toLong() },
                rs.getString("entry_path"), rs.getString("display_name"), rs.getString("content_note").orEmpty(),
                mapper.readTree(rs.getString("tags_json")).map { it.asText() }, rs.getTimestamp("deleted_at") != null,
                rs.getString("extraction_state"), rs.getString("extraction_error"))
        }, contactId).associateBy { it.attachmentId }

    fun isDeleted(id: Long): Boolean = jdbc.queryForObject(
        "SELECT COUNT(*) FROM expert_material_file WHERE attachment_id=? AND deleted_at IS NOT NULL", Long::class.java, id)!! > 0

    fun isDerived(id: Long): Boolean = jdbc.queryForObject(
        "SELECT COUNT(*) FROM expert_material_file WHERE attachment_id=? AND parent_attachment_id IS NOT NULL", Long::class.java, id)!! > 0

    fun ensure(id: Long) { jdbc.update("INSERT IGNORE INTO expert_material_file (attachment_id,tags_json) VALUES (?, '[]')", id) }
    fun lock(id: Long) { jdbc.queryForObject("SELECT attachment_id FROM expert_material_file WHERE attachment_id=? FOR UPDATE", Long::class.java, id) }
    fun edit(id: Long, name: String, note: String, tags: List<String>) {
        jdbc.update("UPDATE expert_material_file SET display_name=?,content_note=?,tags_json=?,updated_at=CURRENT_TIMESTAMP(3) WHERE attachment_id=?",
            name, note, mapper.writeValueAsString(tags), id)
    }
    fun deleted(id: Long, deleted: Boolean) {
        jdbc.update("UPDATE expert_material_file SET deleted_at=${if (deleted) "CURRENT_TIMESTAMP(3)" else "NULL"},updated_at=CURRENT_TIMESTAMP(3) WHERE attachment_id=?", id)
    }
    fun state(id: Long, state: String, error: String? = null) {
        jdbc.update("UPDATE expert_material_file SET extraction_state=?,extraction_error=?,updated_at=CURRENT_TIMESTAMP(3) WHERE attachment_id=?", state, error, id)
    }
    fun addChild(id: Long, parent: Long, path: String) {
        jdbc.update("INSERT INTO expert_material_file (attachment_id,parent_attachment_id,entry_path,tags_json,extraction_state) VALUES (?,?,?,'[]','DONE')", id, parent, path)
    }
}
