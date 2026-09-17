package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.OutboundMailAttachment
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * outbound_mail_attachment 的显式 JDBC 读写（04 T1）。
 *
 * 全部语句只有 INSERT 与按 (expert_contact_id, id 集合) 的 SELECT：不存在
 * UPDATE/DELETE，元数据创建后不可变（I-1）；跨专家读取只能取到该专家自己的行，
 * 调用方再判上传者归属（I-2）。
 *
 * 用 [NamedParameterJdbcTemplate] 显式 INSERT，而不是 `CrudRepository.save`：这里的
 * `id` 是服务端预生成的 UUID 主键（非自增），走 Spring Data JDBC 的 isNew 推断会被
 * 当成已存在实体而发 UPDATE。
 */
@Repository
class OutboundMailAttachmentRepository(
    private val jdbc: NamedParameterJdbcTemplate
) {

    /** 只插入，不 upsert、不覆盖：同一 UUID 不可能被第二次写入（主键冲突即数据异常）。 */
    fun insert(attachment: OutboundMailAttachment) {
        jdbc.update(
            """
            INSERT INTO outbound_mail_attachment
                (id, expert_contact_id, created_by, file_name, content_type,
                 byte_length, sha256, created_at)
            VALUES
                (:id, :expertContactId, :createdBy, :fileName, :contentType,
                 :byteLength, :sha256, :createdAt)
            """,
            MapSqlParameterSource()
                .addValue("id", attachment.id)
                .addValue("expertContactId", attachment.expertContactId)
                .addValue("createdBy", attachment.createdBy)
                .addValue("fileName", attachment.fileName)
                .addValue("contentType", attachment.contentType)
                .addValue("byteLength", attachment.byteLength)
                .addValue("sha256", attachment.sha256)
                .addValue("createdAt", attachment.createdAt)
        )
    }

    /**
     * 按专家 + id 集合批量读取（一次 `IN` 查询，不与 id 顺序关联；调用方按请求顺序
     * 重排，见 `OutboundAttachmentService.loadOrdered`）。不在集合里的 id 直接缺席，
     * 由调用方判为「不存在或不属于当前会话」。
     */
    fun findAllByExpertContactIdAndIdIn(
        expertContactId: Long,
        ids: List<String>
    ): List<OutboundMailAttachment> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        return jdbc.query(
            """
            SELECT id, expert_contact_id, created_by, file_name, content_type,
                   byte_length, sha256, created_at
              FROM outbound_mail_attachment
             WHERE expert_contact_id = :expertContactId
               AND id IN (:ids)
            """,
            MapSqlParameterSource()
                .addValue("expertContactId", expertContactId)
                .addValue("ids", ids),
            ROW_MAPPER
        )
    }

    private companion object {
        val ROW_MAPPER = RowMapper { rs, _ ->
            OutboundMailAttachment(
                id = rs.getString("id"),
                expertContactId = rs.getLong("expert_contact_id"),
                createdBy = rs.getString("created_by"),
                fileName = rs.getString("file_name"),
                contentType = rs.getString("content_type"),
                byteLength = rs.getLong("byte_length"),
                sha256 = rs.getString("sha256"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime()
            )
        }
    }
}
