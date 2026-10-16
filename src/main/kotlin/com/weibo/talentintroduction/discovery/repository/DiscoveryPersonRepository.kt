package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonAuthorKeyRecord
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberRecord
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberWrite
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonRecord
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

/** 04：关系写路径结果。只有 [APPLIED] 真正落库；其余都是并发/状态冲突信号，由服务层重新读并复验。 */
enum class DiscoveryPersonWriteStatus {
    APPLIED,
    AUTHOR_KEY_TAKEN,
    MEMBER_TAKEN,
    PERSON_MISSING,
    /** R-2：锁后人物 revision 与核验时不一致（并发写入）——不得依据旧结果提交。 */
    REVISION_CHANGED,
    /** R-2：锁后重读的当前作者键与本次入参同类不同值 —— 不是同一人，禁止挂接。 */
    AUTHOR_KEY_CONFLICT
}

/**
 * 04（I-1/I-2/I-3）：`discovery_person` / `discovery_person_member` / `discovery_person_author_key`
 * 的唯一访问点。全部参数化绑定；写路径自成事务（[@Transactional]），并发控制按固定锁序
 * **排序后的作者键 → 人物行**，最终防线是主键唯一约束。
 *
 * 读查询是 06/08 的稳定接口：批量 docId→personId、人物成员/作者键、revision 与成员指纹。
 * 本类不做任何准入/邮箱验证判断，也不写既有共享表。
 */
@Repository
class DiscoveryPersonRepository(private val jdbcTemplate: JdbcTemplate) {

    // ── 读查询（06/08 使用前须用成员指纹与当前档案事实重核） ─────────────────────

    fun findPerson(personId: String): DiscoveryPersonRecord? =
        jdbcTemplate.query(
            "SELECT * FROM discovery_person WHERE person_id = ?", PERSON_MAPPER, personId
        ).firstOrNull()

    fun findMember(expertDocId: String): DiscoveryPersonMemberRecord? =
        jdbcTemplate.query(
            "SELECT * FROM discovery_person_member WHERE expert_doc_id = ?", MEMBER_MAPPER, expertDocId
        ).firstOrNull()

    /** 批量成员读取：一次最多 500 个 docId，避免逐条查询。 */
    fun findMembers(expertDocIds: Collection<String>): List<DiscoveryPersonMemberRecord> =
        queryIn(expertDocIds) { placeholders, args ->
            jdbcTemplate.query(
                "SELECT * FROM discovery_person_member WHERE expert_doc_id IN ($placeholders) ORDER BY expert_doc_id",
                MEMBER_MAPPER, *args
            )
        }

    /** 批量 docId → personId 映射（06/08 聚合读契约）。 */
    fun findPersonIdsByDocIds(expertDocIds: Collection<String>): Map<String, String> =
        findMembers(expertDocIds).associate { it.expertDocId to it.personId }

    /** 批量作者键 → personId 映射。 */
    fun findPersonIdsByAuthorKeys(authorKeys: Collection<String>): Map<String, String> =
        queryIn(authorKeys) { placeholders, args ->
            jdbcTemplate.query(
                "SELECT author_key, person_id FROM discovery_person_author_key WHERE author_key IN ($placeholders)",
                { rs: ResultSet, _: Int -> rs.getString("author_key") to rs.getString("person_id") }, *args
            )
        }.toMap()

    fun findMembersByPerson(personId: String): List<DiscoveryPersonMemberRecord> =
        jdbcTemplate.query(
            "SELECT * FROM discovery_person_member WHERE person_id = ? ORDER BY expert_doc_id",
            MEMBER_MAPPER, personId
        )

    fun findAuthorKeysByPerson(personId: String): List<DiscoveryPersonAuthorKeyRecord> =
        jdbcTemplate.query(
            "SELECT * FROM discovery_person_author_key WHERE person_id = ? ORDER BY author_key",
            AUTHOR_KEY_MAPPER, personId
        )

    // ── 写路径（显式事务 + 唯一约束 + 排序锁） ─────────────────────────────────

    /**
     * 新建主档 + 全部成员 + 全部作者键，单一事务。锁序：排序后的作者键 → 插入。
     * 任一作者键已被占用、或任一 docId 已有成员行时不写任何行并返回冲突信号；
     * 事务内失败（含最后一刻的唯一键冲突）整体回滚，**绝不暴露空主档**。
     */
    @Transactional
    fun createPerson(
        personId: String,
        primaryDocId: String,
        members: List<DiscoveryPersonMemberWrite>,
        authorKeys: List<String>,
        now: LocalDateTime
    ): DiscoveryPersonWriteStatus {
        val sortedKeys = authorKeys.distinct().sorted()
        if (lockAuthorKeys(sortedKeys).isNotEmpty()) return DiscoveryPersonWriteStatus.AUTHOR_KEY_TAKEN
        if (findMembers(members.map { it.expertDocId }).isNotEmpty()) return DiscoveryPersonWriteStatus.MEMBER_TAKEN
        jdbcTemplate.update(
            "INSERT INTO discovery_person (person_id, primary_doc_id, revision, created_at, updated_at) VALUES (?, ?, 0, ?, ?)",
            personId, primaryDocId, Timestamp.valueOf(now), Timestamp.valueOf(now)
        )
        insertMembers(personId, members, now)
        insertAuthorKeys(sortedKeys, personId)
        return DiscoveryPersonWriteStatus.APPLIED
    }

    /**
     * 把成员/作者键挂到已存在的主档上，单一事务。锁序：排序后的作者键 → 人物行。
     * 作者键已指向其他人物、或 docId 已属于其他人物时返回冲突信号（服务层重新读并复验）；
     * 真正新增关系时递增 `revision`、刷新 `updated_at`（代表引用 [DiscoveryPersonRecord.primaryDocId] 不变）。
     */
    @Transactional
    fun attachToPerson(
        personId: String,
        members: List<DiscoveryPersonMemberWrite>,
        authorKeys: List<String>,
        now: LocalDateTime
    ): DiscoveryPersonWriteStatus {
        val sortedKeys = authorKeys.distinct().sorted()
        val locked = lockAuthorKeys(sortedKeys).toMap()
        if (locked.values.any { it != personId }) return DiscoveryPersonWriteStatus.AUTHOR_KEY_TAKEN
        val existing = findMembers(members.map { it.expertDocId })
        if (existing.any { it.personId != personId }) return DiscoveryPersonWriteStatus.MEMBER_TAKEN
        if (lockPerson(personId) == null) return DiscoveryPersonWriteStatus.PERSON_MISSING

        val existingDocIds = existing.map { it.expertDocId }.toSet()
        val newMembers = members.filter { it.expertDocId !in existingDocIds }
        val newKeys = sortedKeys.filter { it !in locked }
        if (newMembers.isEmpty() && newKeys.isEmpty()) return DiscoveryPersonWriteStatus.APPLIED
        insertMembers(personId, newMembers, now)
        insertAuthorKeys(newKeys, personId)
        jdbcTemplate.update(
            "UPDATE discovery_person SET revision = revision + 1, updated_at = ? WHERE person_id = ?",
            Timestamp.valueOf(now), personId
        )
        return DiscoveryPersonWriteStatus.APPLIED
    }

    /**
     * R-2（V-3）：**锁后核验**的挂接 —— 在锁定人物行之后重读当前 revision 与完整作者键，再决定是否写入。
     * 与 [attachToPerson] 的差别：把服务层核验时的事实绑定到锁内状态（revision 变化或同类不同值即拒绝），
     * 使「锁前核验 → 锁后提交」之间的并发窗口不再能挂出同一根的两个同类不同作者键。
     */
    @Transactional
    fun attachToPersonVerified(
        personId: String,
        members: List<DiscoveryPersonMemberWrite>,
        authorKeys: List<String>,
        now: LocalDateTime,
        expectedRevision: Long
    ): DiscoveryPersonWriteStatus {
        val sortedKeys = authorKeys.distinct().sorted()
        val locked = lockAuthorKeys(sortedKeys).toMap()
        if (locked.values.any { it != personId }) return DiscoveryPersonWriteStatus.AUTHOR_KEY_TAKEN
        val existing = findMembers(members.map { it.expertDocId })
        if (existing.any { it.personId != personId }) return DiscoveryPersonWriteStatus.MEMBER_TAKEN
        val person = lockPerson(personId) ?: return DiscoveryPersonWriteStatus.PERSON_MISSING
        if (person.revision != expectedRevision) return DiscoveryPersonWriteStatus.REVISION_CHANGED
        val currentKeys = findAuthorKeysByPerson(personId).map { it.authorKey }
        val incomingByClass = sortedKeys.groupBy { it.substringBefore(':') }
        val currentByClass = currentKeys.groupBy { it.substringBefore(':') }
        for ((keyClass, incoming) in incomingByClass) {
            val current = currentByClass[keyClass].orEmpty()
            if (current.any { it !in incoming }) return DiscoveryPersonWriteStatus.AUTHOR_KEY_CONFLICT
        }

        val existingDocIds = existing.map { it.expertDocId }.toSet()
        val newMembers = members.filter { it.expertDocId !in existingDocIds }
        val linkedKeys = locked.keys
        val newKeys = sortedKeys.filter { it !in linkedKeys }
        if (newMembers.isEmpty() && newKeys.isEmpty()) return DiscoveryPersonWriteStatus.APPLIED
        insertMembers(personId, newMembers, now)
        insertAuthorKeys(newKeys, personId)
        jdbcTemplate.update(
            "UPDATE discovery_person SET revision = revision + 1, updated_at = ? WHERE person_id = ?",
            Timestamp.valueOf(now), personId
        )
        return DiscoveryPersonWriteStatus.APPLIED
    }

    // ── 内部 ─────────────────────────────────────────────────────────────────

    private fun insertMembers(
        personId: String,
        members: List<DiscoveryPersonMemberWrite>,
        now: LocalDateTime
    ) {
        members.sortedBy { it.expertDocId }.forEach { member ->
            jdbcTemplate.update(
                """
                INSERT INTO discovery_person_member
                    (expert_doc_id, person_id, normalized_email, identity_fingerprint, evidence_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                member.expertDocId, personId, member.normalizedEmail, member.identityFingerprint,
                member.evidenceJson, Timestamp.valueOf(now), Timestamp.valueOf(now)
            )
        }
    }

    private fun insertAuthorKeys(authorKeys: List<String>, personId: String) {
        authorKeys.forEach { key ->
            jdbcTemplate.update(
                "INSERT INTO discovery_person_author_key (author_key, person_id) VALUES (?, ?)", key, personId
            )
        }
    }

    /** 排序后的作者键行锁；只返回已存在的行（不存在的键由唯一约束兜底）。 */
    private fun lockAuthorKeys(authorKeys: List<String>): List<Pair<String, String>> =
        queryIn(authorKeys) { placeholders, args ->
            jdbcTemplate.query(
                "SELECT author_key, person_id FROM discovery_person_author_key WHERE author_key IN ($placeholders) ORDER BY author_key FOR UPDATE",
                { rs: ResultSet, _: Int -> rs.getString("author_key") to rs.getString("person_id") }, *args
            )
        }

    private fun lockPerson(personId: String): DiscoveryPersonRecord? =
        jdbcTemplate.query(
            "SELECT * FROM discovery_person WHERE person_id = ? FOR UPDATE", PERSON_MAPPER, personId
        ).firstOrNull()

    private fun <T> queryIn(values: Collection<String>, query: (String, Array<Any>) -> List<T>): List<T> {
        val distinct = values.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return emptyList()
        val result = mutableListOf<T>()
        distinct.chunked(BATCH_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(", ") { "?" }
            result += query(placeholders, chunk.toTypedArray())
        }
        return result
    }

    companion object {
        const val BATCH_SIZE = 500

        private val PERSON_MAPPER = RowMapper<DiscoveryPersonRecord> { rs: ResultSet, _: Int ->
            DiscoveryPersonRecord(
                personId = rs.getString("person_id"),
                primaryDocId = rs.getString("primary_doc_id"),
                revision = rs.getLong("revision"),
                createdAt = rs.getTimestamp("created_at")!!.toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at")!!.toLocalDateTime()
            )
        }

        private val MEMBER_MAPPER = RowMapper<DiscoveryPersonMemberRecord> { rs: ResultSet, _: Int ->
            DiscoveryPersonMemberRecord(
                expertDocId = rs.getString("expert_doc_id"),
                personId = rs.getString("person_id"),
                normalizedEmail = rs.getString("normalized_email"),
                identityFingerprint = rs.getString("identity_fingerprint"),
                evidenceJson = rs.getString("evidence_json"),
                createdAt = rs.getTimestamp("created_at")!!.toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at")!!.toLocalDateTime()
            )
        }

        private val AUTHOR_KEY_MAPPER = RowMapper<DiscoveryPersonAuthorKeyRecord> { rs: ResultSet, _: Int ->
            DiscoveryPersonAuthorKeyRecord(
                authorKey = rs.getString("author_key"),
                personId = rs.getString("person_id")
            )
        }
    }
}
