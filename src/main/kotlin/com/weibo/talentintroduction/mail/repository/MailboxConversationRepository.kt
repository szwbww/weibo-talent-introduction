package com.weibo.talentintroduction.mail.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDateTime

/**
 * 专家会话查询（fast-p 07）专用 JDBC 仓库。
 *
 * 数据权威（I-1）：来信只取 linked inbound_mail_processing（排除未匹配与独立机器信；
 * 绝不再 UNION INBOUND mail_record 重复计数）；发件只取 OUTBOUND mail_record。
 * 事件时间 = processing.received_at / OUTBOUND 的 COALESCE(sent_at, created_at)；
 * 稳定排序键 (event_at, source_rank, id)，source_rank 1=MAIL_RECORD 2=INBOUND_PROCESSING。
 * 文本列沿既有 mailbox SQL 显式统一 utf8mb4_unicode_ci，避免 UNION 排序字符集冲突。
 *
 * summary / count / timeline 共用同一份归一化 UNION SQL 基础（[rangeUnionSql]）。
 * 聚合在账号范围（accountCodes ∩ accountCode）全历史计算；方向/日期/主题/标签等
 * 消息级筛选只经 EXISTS 限定专家 membership，绝不影响聚合口径（I-2/I-4）。
 */
@Repository
class MailboxConversationRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    // ------------------------------------------------------------------
    // 参数与投影
    // ------------------------------------------------------------------

    /** 会话 summary 的消息级筛选（均为可空白名单值；方向由 service/controller 白名单约束）。 */
    data class ConversationFilter(
        val accountCodes: List<String>,
        val accountCode: String? = null,
        val q: String? = null,
        val followed: Boolean = false,
        val waitingReply: Boolean = false,
        val pendingOnly: Boolean = false,
        val direction: String? = null,
        val startTime: LocalDateTime? = null,
        val endTime: LocalDateTime? = null,
        val subject: String? = null,
        val label: String? = null,
        val recipientEmail: String? = null,
        val keyword: String? = null
    )

    data class ConversationSummarySqlRow(
        val expertContactId: Long,
        val expertName: String?,
        val expertEmail: String,
        val orcidId: String,
        val receivedCount: Long,
        val sentCount: Long,
        val failedCount: Long,
        val pendingCount: Long,
        val latestEventAt: LocalDateTime,
        val followed: Boolean
    )

    data class ConversationLatestMessageRow(
        val source: String,
        val id: Long,
        val direction: String,
        val accountCode: String?,
        val subject: String?,
        val preview: String?,
        val eventAt: LocalDateTime,
        val sendStatus: String?,
        val processStatus: String?
    )

    data class ConversationLatestInboundRow(
        val processingId: Long,
        val accountCode: String,
        val messageId: String?,
        val receivedAt: LocalDateTime
    )

    data class ConversationAccountCodesRow(
        val expertContactId: Long,
        val accountCodes: List<String>
    )

    data class ConversationMaterialCountRow(
        val expertContactId: Long,
        val materialCount: Long
    )

    data class ConversationMessageSqlRow(
        val source: String,
        val id: Long,
        val expertContactId: Long,
        val direction: String,
        val accountCode: String?,
        val subject: String?,
        val body: String?,
        val cleanedBody: String?,
        val eventAt: LocalDateTime,
        val sendStatus: String?,
        val processStatus: String?,
        val messageId: String?,
        val inReplyTo: String?
    )

    /** timeline 游标键（keyset，(event_at, source_rank, id) 严格更旧）。 */
    data class ConversationKeyset(
        val beforeTime: LocalDateTime,
        val beforeSourceRank: Int,
        val beforeId: Long
    )

    class TimelinePage(
        val rows: List<ConversationMessageSqlRow>,
        val hasMore: Boolean
    )

    // ------------------------------------------------------------------
    // summary / count
    // ------------------------------------------------------------------

    /** 会话专家数（受全部筛选/关注/等待/待处理约束；专家级去重后再计数）。 */
    fun countConversations(username: String, filter: ConversationFilter): Long {
        val eligibility = membership(filter)
        if (eligibility.impossible) return 0L
        val sql = """
            SELECT COUNT(*) FROM (
                SELECT u.expert_contact_id AS expert_contact_id
                  FROM (${rangeUnionSql(includeBody = false)}) u
                  JOIN expert_contact ec ON ec.id = u.expert_contact_id
                 WHERE ${expertPredicates(username, filter, eligibility)}
                 GROUP BY u.expert_contact_id
                 ${havingClause(filter)}
            ) counted
        """.trimIndent()
        return jdbcTemplate.queryForObject(sql, params(username, filter), Long::class.java) ?: 0L
    }

    /**
     * 会话专家分页 summary（先 DB 聚合专家再分页）。每行只含聚合口径字段与最新事件
     * 时间，绝不含邮件正文；latestMessage/latestInbound/accountCodes/materialCount
     * 由调用方按本页 contact id 集合追加查询（[latestMessageByContacts] 等）。
     */
    fun pageConversations(
        username: String,
        filter: ConversationFilter,
        size: Int,
        offset: Long
    ): List<ConversationSummarySqlRow> {
        val eligibility = membership(filter)
        if (eligibility.impossible) return emptyList()
        val sql = """
            SELECT u.expert_contact_id AS expert_contact_id,
                   CONVERT(ec.expert_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_name,
                   CONVERT(ec.expert_email USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_email,
                   CONVERT(ec.orcid_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS orcid_id,
                   SUM(u.received_flag) AS received_count,
                   SUM(u.sent_flag) AS sent_count,
                   SUM(u.failed_flag) AS failed_count,
                   SUM(u.pending_flag) AS pending_count,
                   MAX(u.event_at) AS latest_event_at,
                   EXISTS (
                       SELECT 1 FROM expert_follow ef
                        WHERE ef.username = :username
                          AND ef.expert_contact_id = u.expert_contact_id
                   ) AS followed
              FROM (${rangeUnionSql(includeBody = false)}) u
              JOIN expert_contact ec ON ec.id = u.expert_contact_id
             WHERE ${expertPredicates(username, filter, eligibility)}
             GROUP BY u.expert_contact_id, ec.expert_name, ec.expert_email, ec.orcid_id
             ${havingClause(filter)}
             ORDER BY ${orderByClause(filter)}
             LIMIT :size OFFSET :offset
        """.trimIndent()
        val p = params(username, filter)
        p.addValue("size", size)
        p.addValue("offset", offset)
        return jdbcTemplate.query(sql, p) { rs, _ -> rs.toSummaryRow() }
    }

    /**
     * EXPLAIN 诊断（只读、不执行主查询）：供 mysqlIt 集成测试对同一份 SQL 做计划
     * 健全性检查（G-1）。MySQL 支持 EXPLAIN 预编译语句，命名参数原样绑定。
     * 与 [pageConversations] 共用 [orderByClause]，保证诊断 SQL 与执行 SQL 的排序不漂移。
     */
    fun explainConversationsPage(
        username: String,
        filter: ConversationFilter,
        size: Int,
        offset: Long
    ): List<Map<String, Any>> {
        val eligibility = membership(filter)
        val sql = """
            SELECT u.expert_contact_id AS expert_contact_id,
                   CONVERT(ec.expert_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_name,
                   CONVERT(ec.expert_email USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_email,
                   CONVERT(ec.orcid_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS orcid_id,
                   SUM(u.received_flag) AS received_count,
                   SUM(u.sent_flag) AS sent_count,
                   SUM(u.failed_flag) AS failed_count,
                   SUM(u.pending_flag) AS pending_count,
                   MAX(u.event_at) AS latest_event_at,
                   EXISTS (
                       SELECT 1 FROM expert_follow ef
                        WHERE ef.username = :username
                          AND ef.expert_contact_id = u.expert_contact_id
                   ) AS followed
              FROM (${rangeUnionSql(includeBody = false)}) u
              JOIN expert_contact ec ON ec.id = u.expert_contact_id
             WHERE ${expertPredicates(username, filter, eligibility)}
             GROUP BY u.expert_contact_id, ec.expert_name, ec.expert_email, ec.orcid_id
             ${havingClause(filter)}
             ORDER BY ${orderByClause(filter)}
             LIMIT :size OFFSET :offset
        """.trimIndent()
        val p = params(username, filter)
        p.addValue("size", size)
        p.addValue("offset", offset)
        return jdbcTemplate.queryForList("EXPLAIN $sql", p)
    }

    /**
     * 分页前排序的唯一私有 order 片段（I-1），page 与 explain 共用。
     *
     * 排序键：`latest_reply_at = MAX(CASE WHEN u.source = 'INBOUND_PROCESSING' THEN u.event_at END)`
     * （纯本地 SQL 投影，无表字段、无新响应字段）。"全部" 视图（非关注/待处理/旧 waitingReply）：
     * 待处理>0 在前 → 最近来信为 NULL（无来信，仅有发件）置底 → 最近来信倒序 → contactId DESC 稳定。
     * 关注/待处理/旧 waitingReply 省略 pending 首项（只按最近来信倒序 + 稳定 id）。
     * 聚合先 GROUP BY 再排序 LIMIT；count 不受排序影响。
     */
    private fun orderByClause(filter: ConversationFilter): String {
        val latestReply = "MAX(CASE WHEN u.source = 'INBOUND_PROCESSING' THEN u.event_at END)"
        val plainOrder = listOf(
            "CASE WHEN $latestReply IS NULL THEN 1 ELSE 0 END ASC",
            "$latestReply DESC",
            "u.expert_contact_id DESC"
        )
        val pendingFirst = if (filter.followed || filter.pendingOnly || filter.waitingReply) {
            emptyList()
        } else {
            listOf("CASE WHEN SUM(u.pending_flag) > 0 THEN 0 ELSE 1 END ASC")
        }
        return (pendingFirst + plainOrder).joinToString(", ")
    }

    // ------------------------------------------------------------------
    // summary 追加投影（本页 contact id 集合）
    // ------------------------------------------------------------------

    /** 每位专家的最近一条消息（双来源中 event 最大者；并列按 (source_rank, id) 稳定）。
     *  MySQL 5.7 兼容：同一账号范围/来源内 NOT EXISTS 反连接实现 groupwise max，
     *  禁 ROW_NUMBER/OVER（旧线上服务端解析窗口函数报 1064）。 */
    fun latestMessageByContacts(
        contactIds: List<Long>,
        accountCodes: List<String>,
        accountCode: String?
    ): Map<Long, ConversationLatestMessageRow> {
        if (contactIds.isEmpty()) return emptyMap()
        val sql = """
            SELECT u.expert_contact_id AS expert_contact_id,
                   u.source AS source,
                   u.id AS id,
                   u.direction AS direction,
                   u.account_code AS account_code,
                   u.subject AS subject,
                   u.preview AS preview,
                   u.event_at AS event_at,
                   u.send_status AS send_status,
                   u.process_status AS process_status
              FROM (${rangeUnionSql(includeBody = false)}) u
             WHERE u.expert_contact_id IN (:contactIds)
               AND NOT EXISTS (
                   SELECT 1
                     FROM (${rangeUnionSql(includeBody = false)}) newer
                    WHERE newer.expert_contact_id = u.expert_contact_id
                      AND (newer.event_at > u.event_at
                           OR (newer.event_at = u.event_at
                               AND newer.source_rank > u.source_rank)
                           OR (newer.event_at = u.event_at
                               AND newer.source_rank = u.source_rank
                               AND newer.id > u.id))
               )
        """.trimIndent()
        val p = MapSqlParameterSource()
            .addValue("contactIds", contactIds)
            .addValue("accountCodes", accountCodes)
            .addValue("accountCode", accountCode)
        return jdbcTemplate.query(sql, p) { rs, _ ->
            rs.getLong("expert_contact_id") to rs.toLatestMessageRow()
        }.toMap()
    }

    /** 每位专家最近一封来信（真实 processing.id，绝不从 mail_record 推算）。
     *  MySQL 5.7 兼容：候选与反连接对手都限 INBOUND_PROCESSING，见 [latestMessageByContacts]。 */
    fun latestInboundByContacts(
        contactIds: List<Long>,
        accountCodes: List<String>,
        accountCode: String?
    ): Map<Long, ConversationLatestInboundRow> {
        if (contactIds.isEmpty()) return emptyMap()
        val sql = """
            SELECT u.expert_contact_id AS expert_contact_id,
                   u.id AS processing_id,
                   u.account_code AS account_code,
                   u.message_id AS message_id,
                   u.event_at AS received_at
              FROM (${rangeUnionSql(includeBody = false)}) u
             WHERE u.expert_contact_id IN (:contactIds)
               AND u.source = 'INBOUND_PROCESSING'
               AND NOT EXISTS (
                   SELECT 1
                     FROM (${rangeUnionSql(includeBody = false)}) newer
                    WHERE newer.expert_contact_id = u.expert_contact_id
                      AND newer.source = 'INBOUND_PROCESSING'
                      AND (newer.event_at > u.event_at
                           OR (newer.event_at = u.event_at
                               AND newer.source_rank > u.source_rank)
                           OR (newer.event_at = u.event_at
                               AND newer.source_rank = u.source_rank
                               AND newer.id > u.id))
               )
        """.trimIndent()
        val p = MapSqlParameterSource()
            .addValue("contactIds", contactIds)
            .addValue("accountCodes", accountCodes)
            .addValue("accountCode", accountCode)
        return jdbcTemplate.query(sql, p) { rs, _ ->
            rs.getLong("expert_contact_id") to ConversationLatestInboundRow(
                processingId = rs.getLong("processing_id"),
                accountCode = rs.getString("account_code"),
                messageId = rs.getString("message_id"),
                receivedAt = rs.localDateTime("received_at")
            )
        }.toMap()
    }

    /** 每位专家在账号范围内出现过的发件账号（去重，排序稳定）。 */
    fun accountCodesByContacts(
        contactIds: List<Long>,
        accountCodes: List<String>,
        accountCode: String?
    ): Map<Long, List<String>> {
        if (contactIds.isEmpty()) return emptyMap()
        val sql = """
            SELECT u.expert_contact_id AS expert_contact_id,
                   GROUP_CONCAT(DISTINCT u.account_code ORDER BY u.account_code SEPARATOR ',') AS account_codes
              FROM (${rangeUnionSql(includeBody = false)}) u
             WHERE u.expert_contact_id IN (:contactIds)
             GROUP BY u.expert_contact_id
        """.trimIndent()
        val p = MapSqlParameterSource()
            .addValue("contactIds", contactIds)
            .addValue("accountCodes", accountCodes)
            .addValue("accountCode", accountCode)
        return jdbcTemplate.query(sql, p) { rs, _ ->
            val codes = rs.getString("account_codes")
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            rs.getLong("expert_contact_id") to codes
        }.toMap()
    }

    /** 每位专家全体资料数（expert_document；不随账号范围/筛选变化）。 */
    fun materialCountByContacts(contactIds: List<Long>): Map<Long, Long> {
        if (contactIds.isEmpty()) return emptyMap()
        val sql = """
            SELECT expert_contact_id AS expert_contact_id, COUNT(*) AS material_count
              FROM expert_document
             WHERE expert_contact_id IN (:contactIds)
             GROUP BY expert_contact_id
        """.trimIndent()
        return jdbcTemplate.query(sql, MapSqlParameterSource("contactIds", contactIds)) { rs, _ ->
            rs.getLong("expert_contact_id") to rs.getLong("material_count")
        }.toMap()
    }

    // ------------------------------------------------------------------
    // timeline（单专家消息）
    // ------------------------------------------------------------------

    /**
     * 单个专家的往来消息（默认取最新窗口；before 为空取最新 limit 条）。
     * keyset (event_at DESC, source_rank DESC, id DESC) 严格更旧；同时间戳不丢不重。
     * 返回行按 DESC 排列（新→旧），由 service 逆转为正序展示；hasMore = 还有更早消息。
     * 行包含完整 body/cleanedBody（仅当前专家 timeline 加载正文；summary 永不携带）。
     */
    fun timelineMessages(
        contactId: Long,
        filter: ConversationFilter,
        before: ConversationKeyset?,
        limit: Int
    ): TimelinePage {
        val sql = """
            SELECT u.expert_contact_id AS expert_contact_id,
                   u.source AS source,
                   u.id AS id,
                   u.direction AS direction,
                   u.account_code AS account_code,
                   u.subject AS subject,
                   u.body AS body,
                   u.cleaned_body AS cleaned_body,
                   u.event_at AS event_at,
                   u.send_status AS send_status,
                   u.process_status AS process_status,
                   u.message_id AS message_id,
                   u.in_reply_to AS in_reply_to
              FROM (${rangeUnionSql(includeBody = true)}) u
             WHERE u.expert_contact_id = :contactId
               AND (:beforeTime IS NULL
                    OR u.event_at < :beforeTime
                    OR (u.event_at = :beforeTime
                        AND (u.source_rank < :beforeSourceRank
                             OR (u.source_rank = :beforeSourceRank AND u.id < :beforeId))))
             ORDER BY u.event_at DESC, u.source_rank DESC, u.id DESC
             LIMIT :fetchLimit
        """.trimIndent()
        val p = params("", filter)
        p.addValue("contactId", contactId)
        p.addValue("beforeTime", before?.beforeTime)
        p.addValue("beforeSourceRank", before?.beforeSourceRank)
        p.addValue("beforeId", before?.beforeId)
        p.addValue("fetchLimit", limit + 1)
        val rows = jdbcTemplate.query(sql, p) { rs, _ -> rs.toMessageRow() }
        val hasMore = rows.size > limit
        return TimelinePage(rows = if (hasMore) rows.take(limit) else rows, hasMore = hasMore)
    }

    // ------------------------------------------------------------------
    // SQL 装配
    // ------------------------------------------------------------------

    /**
     * 归一化 UNION 基础：OUTBOUND mail_record UNION ALL linked inbound_mail_processing。
     * 两个分支的文本列显式 CONVERT + COLLATE utf8mb4_unicode_ci，避免 UNION 排序/物化
     * 时字符集冲突。includeBody 仅 timeline 需要（summary 永不读正文列）。
     * 注意：本 SQL 片段同时作为 count/summary/latest 系列/accountCodes/timeline 的数据基础，
     * 各列位置与别名必须保持一致。
     */
    private fun rangeUnionSql(includeBody: Boolean): String {
        val outboundBody = if (includeBody) {
            """,
                   CONVERT(mr.body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS body,
                   CONVERT(mr.cleaned_body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS cleaned_body"""
        } else ""
        val inboundBody = if (includeBody) {
            """,
                   CONVERT(imp.body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS body,
                   CONVERT(imp.cleaned_body USING utf8mb4) COLLATE utf8mb4_unicode_ci AS cleaned_body"""
        } else ""
        return """
            SELECT mr.expert_contact_id AS expert_contact_id,
                   1 AS source_rank,
                   CONVERT('MAIL_RECORD' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS source,
                   mr.id AS id,
                   CONVERT('OUTBOUND' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS direction,
                   CONVERT(mr.sender_account_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS account_code,
                   COALESCE(mr.sent_at, mr.created_at) AS event_at,
                   CONVERT(mr.send_status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS send_status,
                   CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS process_status,
                   CONVERT(mr.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
                   CONVERT(SUBSTRING(COALESCE(mr.cleaned_body, mr.body), 1, 200) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS preview,
                   CONVERT(mr.message_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS message_id,
                   CONVERT(mr.in_reply_to USING utf8mb4) COLLATE utf8mb4_unicode_ci AS in_reply_to,
                   CASE WHEN mr.send_status = 'SENT' THEN 1 ELSE 0 END AS sent_flag,
                   CASE WHEN mr.send_status = 'FAILED' THEN 1 ELSE 0 END AS failed_flag,
                   0 AS received_flag,
                   0 AS pending_flag$outboundBody
              FROM mail_record mr
             WHERE mr.direction = 'OUTBOUND'
               AND mr.expert_contact_id IS NOT NULL
               AND mr.sender_account_code IN (:accountCodes)
               AND (:accountCode IS NULL OR mr.sender_account_code = :accountCode)
            UNION ALL
            SELECT imp.expert_contact_id AS expert_contact_id,
                   2 AS source_rank,
                   CONVERT('INBOUND_PROCESSING' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS source,
                   imp.id AS id,
                   CONVERT('INBOUND' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS direction,
                   CONVERT(imp.sender_account_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS account_code,
                   imp.received_at AS event_at,
                   CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS send_status,
                   CONVERT(imp.process_status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS process_status,
                   CONVERT(imp.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
                   CONVERT(SUBSTRING(COALESCE(imp.cleaned_body, imp.body), 1, 200) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS preview,
                   CONVERT(imp.message_id USING utf8mb4) COLLATE utf8mb4_unicode_ci AS message_id,
                   CONVERT(imp.in_reply_to USING utf8mb4) COLLATE utf8mb4_unicode_ci AS in_reply_to,
                   0 AS sent_flag,
                   0 AS failed_flag,
                   1 AS received_flag,
                   CASE WHEN imp.process_status = 'MANUAL_REVIEW' THEN 1 ELSE 0 END AS pending_flag$inboundBody
              FROM inbound_mail_processing imp
             WHERE imp.expert_contact_id IS NOT NULL
               AND imp.sender_account_code IN (:accountCodes)
               AND (:accountCode IS NULL OR imp.sender_account_code = :accountCode)
        """.trimIndent()
    }

    /** 消息级筛选在聚合口径上完全不可满足的组合（label 只存在于 INBOUND 侧）。 */
    private class MembershipEligibility(
        val impossible: Boolean,
        val outboundClause: String?,
        val inboundClause: String?
    )

    private fun membership(filter: ConversationFilter): MembershipEligibility {
        val hasMessageFilter = filter.direction != null ||
            filter.startTime != null ||
            filter.endTime != null ||
            filter.subject != null ||
            filter.label != null ||
            filter.recipientEmail != null ||
            filter.keyword != null
        if (!hasMessageFilter) {
            return MembershipEligibility(impossible = false, outboundClause = null, inboundClause = null)
        }
        // label 只存在于 inbound 侧；direction=OUTBOUND + label 无任何消息可满足 → 空集。
        if (filter.direction == DIRECTION_OUTBOUND && filter.label != null) {
            return MembershipEligibility(impossible = true, outboundClause = null, inboundClause = null)
        }
        val outboundEligible = filter.label == null && filter.direction != DIRECTION_INBOUND
        val inboundEligible = filter.direction != DIRECTION_OUTBOUND
        // 关键词只出现在消息 EXISTS 内（subject OR cleaned_body OR body；NULL 列照 SQL 空值
        // 处理）；summary SELECT 永不取回正文。邮箱语义沿旧 mailbox 口径：出站匹配
        // expert_contact.expert_email（本专家所有出站共享的联系人邮箱，在出站 EXISTS 内恒等），
        // 入站匹配 inbound_mail_processing.from_email（别名）。recipientEmail/keyword 与
        // 方向/日期/主题/label 全部 AND 在**同一封消息**的 EXISTS 内（I-2/X3）。
        val outboundClause = if (outboundEligible) {
            """
            EXISTS (
                SELECT 1 FROM mail_record mro
                 WHERE mro.direction = 'OUTBOUND'
                   AND mro.expert_contact_id = u.expert_contact_id
                   AND mro.sender_account_code IN (:accountCodes)
                   AND (:accountCode IS NULL OR mro.sender_account_code = :accountCode)
                   AND (:startTime IS NULL OR COALESCE(mro.sent_at, mro.created_at) >= :startTime)
                   AND (:endTime IS NULL OR COALESCE(mro.sent_at, mro.created_at) < :endTime)
                   AND (:subject IS NULL OR mro.subject LIKE CONCAT('%', :subject, '%'))
                   AND (:recipientEmail IS NULL OR ec.expert_email LIKE CONCAT('%', :recipientEmail, '%'))
                   AND (:keyword IS NULL
                        OR mro.subject LIKE CONCAT('%', :keyword, '%')
                        OR mro.cleaned_body LIKE CONCAT('%', :keyword, '%')
                        OR mro.body LIKE CONCAT('%', :keyword, '%'))
            )
            """.trimIndent()
        } else null
        val inboundClause = if (inboundEligible) {
            """
            EXISTS (
                SELECT 1 FROM inbound_mail_processing impi
                 WHERE impi.expert_contact_id = u.expert_contact_id
                   AND impi.sender_account_code IN (:accountCodes)
                   AND (:accountCode IS NULL OR impi.sender_account_code = :accountCode)
                   AND (:startTime IS NULL OR impi.received_at >= :startTime)
                   AND (:endTime IS NULL OR impi.received_at < :endTime)
                   AND (:subject IS NULL OR impi.subject LIKE CONCAT('%', :subject, '%'))
                   AND (:recipientEmail IS NULL OR impi.from_email LIKE CONCAT('%', :recipientEmail, '%'))
                   AND (:keyword IS NULL
                        OR impi.subject LIKE CONCAT('%', :keyword, '%')
                        OR impi.cleaned_body LIKE CONCAT('%', :keyword, '%')
                        OR impi.body LIKE CONCAT('%', :keyword, '%'))
                   AND (:label IS NULL OR EXISTS (
                         SELECT 1 FROM inbound_mail_tag imt
                          WHERE imt.inbound_processing_id = impi.id
                            AND imt.label = :label))
            )
            """.trimIndent()
        } else null
        return MembershipEligibility(
            impossible = false,
            outboundClause = outboundClause,
            inboundClause = inboundClause
        )
    }

    /**
     * WHERE 片段：q（真实 expert_contact 姓名/邮箱）+ followed 过滤 + 消息级 membership。
     * membership 为单消息合取语义：方向/日期/主题/标签须被同一封消息满足；label 只
     * join 其对应来源（inbound_mail_tag → processing），绝不因多标签产生重复行。
     * outbound/inbound 两个方向 EXISTS 是 OR 互补组：组外加整层括号后再与 q/followed
     * AND（X1），否则 SQL 优先级会让单个方向 EXISTS 绕过 q/followed。
     */
    private fun expertPredicates(username: String, filter: ConversationFilter, eligibility: MembershipEligibility): String {
        val clauses = mutableListOf<String>()
        clauses += """
            (:q IS NULL OR :q = ''
             OR ec.expert_name LIKE CONCAT('%', :q, '%')
             OR ec.expert_email LIKE CONCAT('%', :q, '%'))
        """.trimIndent()
        clauses += """
            (:followed = 0 OR EXISTS (
                SELECT 1 FROM expert_follow eff
                 WHERE eff.username = :username
                   AND eff.expert_contact_id = u.expert_contact_id))
        """.trimIndent()
        val membershipClauses = listOfNotNull(eligibility.outboundClause, eligibility.inboundClause)
        if (membershipClauses.isNotEmpty()) {
            clauses += "(\n${membershipClauses.joinToString("\n      OR ")}\n      )"
        }
        return clauses.joinToString("\n   AND ")
    }

    private fun havingClause(filter: ConversationFilter): String {
        val conditions = mutableListOf<String>()
        if (filter.waitingReply) {
            // I-2：全历史账号范围 receivedCount=0 且 sentCount>0（FAILED 不计为 sent）。
            conditions += "SUM(u.received_flag) = 0 AND SUM(u.sent_flag) > 0"
        }
        if (filter.pendingOnly) {
            // 待处理谓词复用 processing 现有 MANUAL_REVIEW 谓词。
            conditions += "SUM(u.pending_flag) > 0"
        }
        return if (conditions.isEmpty()) "" else "HAVING ${conditions.joinToString(" AND ")}"
    }

    private fun params(username: String, filter: ConversationFilter): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("username", username)
            .addValue("accountCodes", filter.accountCodes)
            .addValue("accountCode", filter.accountCode)
            .addValue("q", filter.q?.takeIf { it.isNotBlank() })
            .addValue("followed", if (filter.followed) 1 else 0)
            .addValue("startTime", filter.startTime)
            .addValue("endTime", filter.endTime)
            .addValue("subject", filter.subject?.takeIf { it.isNotBlank() })
            .addValue("label", filter.label?.takeIf { it.isNotBlank() })
            .addValue("recipientEmail", filter.recipientEmail?.takeIf { it.isNotBlank() })
            .addValue("keyword", filter.keyword?.takeIf { it.isNotBlank() })

    private fun ResultSet.toSummaryRow(): ConversationSummarySqlRow =
        ConversationSummarySqlRow(
            expertContactId = getLong("expert_contact_id"),
            expertName = getString("expert_name"),
            expertEmail = getString("expert_email"),
            orcidId = getString("orcid_id"),
            receivedCount = getLong("received_count"),
            sentCount = getLong("sent_count"),
            failedCount = getLong("failed_count"),
            pendingCount = getLong("pending_count"),
            latestEventAt = localDateTime("latest_event_at"),
            followed = getBoolean("followed")
        )

    private fun ResultSet.toLatestMessageRow(): ConversationLatestMessageRow =
        ConversationLatestMessageRow(
            source = getString("source"),
            id = getLong("id"),
            direction = getString("direction"),
            accountCode = getString("account_code"),
            subject = getString("subject"),
            preview = getString("preview"),
            eventAt = localDateTime("event_at"),
            sendStatus = getString("send_status"),
            processStatus = getString("process_status")
        )

    private fun ResultSet.toMessageRow(): ConversationMessageSqlRow =
        ConversationMessageSqlRow(
            source = getString("source"),
            id = getLong("id"),
            expertContactId = getLong("expert_contact_id"),
            direction = getString("direction"),
            accountCode = getString("account_code"),
            subject = getString("subject"),
            body = getString("body"),
            cleanedBody = getString("cleaned_body"),
            eventAt = localDateTime("event_at"),
            sendStatus = getString("send_status"),
            processStatus = getString("process_status"),
            messageId = getString("message_id"),
            inReplyTo = getString("in_reply_to")
        )


    private fun ResultSet.localDateTime(column: String): LocalDateTime {
        val ts = getTimestamp(column) ?: error("column $column is null")
        return ts.toLocalDateTime()
    }

    companion object {
        const val DIRECTION_INBOUND = "INBOUND"
        const val DIRECTION_OUTBOUND = "OUTBOUND"
        const val SOURCE_MAIL_RECORD = "MAIL_RECORD"
        const val SOURCE_INBOUND_PROCESSING = "INBOUND_PROCESSING"
    }
}
