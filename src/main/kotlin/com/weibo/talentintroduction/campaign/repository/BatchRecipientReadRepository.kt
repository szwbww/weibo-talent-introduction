package com.weibo.talentintroduction.campaign.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * I-2/I-4/I-5（02）：批量目标只读事实查询。**只 SELECT、不做任何写入**，供统一预检查
 * （[com.weibo.talentintroduction.campaign.service.BatchRecipientPreflightService]）解释
 * 已发送 / 永久失败 / 账号绑定 / 退订抑制等发送保护事实，以及判断联系人是否存在。
 *
 * 设计边界：
 * - 与执行路径消费同一批列与同一判据（`bound_sender_account_code` 非空、`email_suppression.email`
 *   的归一化语义、`mail_record` 的 OUTBOUND INTRODUCTION 发送/永久失败事实），不新造语义。
 * - 一律按 500 一批参数化 IN 查询（[BATCH_SIZE]），空入参不发 SQL，绝不逐人 N+1、绝不拼接值。
 * - 只读事务；仓储异常直接向上抛，调用方据此把预检查标为不可用/partial，绝不静默当成「无事实」。
 *
 * 归一化责任：ORCID 与邮箱由调用方按现有规则（`ExpertIdNormalizer.normalize` /
 * `normalizeVerificationEmail`）先行归一化后传入，本类按列原值精确匹配。
 */
@Repository
class BatchRecipientReadRepository(private val jdbcTemplate: JdbcTemplate) {

    /** 任一 `expert_contact` 行已绑定非空发件账号的 ORCID（与绑定值是否在选中集合无关）。 */
    @Transactional(readOnly = true)
    fun findBoundOrcids(orcidIds: Collection<String>): Set<String> =
        queryOrcids(BOUND_SQL, normalize(orcidIds))

    /** `operator_status='EMAIL_INVALID'` 的 ORCID（既有地址保护事实）。 */
    @Transactional(readOnly = true)
    fun findEmailInvalidOrcids(orcidIds: Collection<String>): Set<String> =
        queryOrcids(EMAIL_INVALID_SQL, normalize(orcidIds))

    /** `email_suppression` 命中的邮箱（调用方已归一化）。 */
    @Transactional(readOnly = true)
    fun findSuppressedEmails(emails: Collection<String?>): Set<String> {
        val distinct = emails.asSequence()
            .mapNotNull { it?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        if (distinct.isEmpty()) return emptySet()
        return chunkedQuery(distinct) { args ->
            jdbcTemplate.query(SUPPRESSED_SQL.replace(PLACEHOLDERS, placeholders(args.size)), { rs, _ ->
                rs.getString(1)
            }, *args.toTypedArray())
        }.toSet()
    }

    /**
     * OUTBOUND INTRODUCTION 的发送事实：`sent` = 存在 `send_status='SENT'` 的记录；
     * `permanentFailure` = 存在 `send_status='FAILED'` 且 `error_summary` 以 [PERMANENT_FAILURE_SUMMARY_PREFIX]
     * 开头（与 `ManualInitialOutreachService.isPermanentIntroductionFailure` 同一持久化摘要判据）。
     * 无 `mail_record` 的 ORCID 不出现在返回值里（等同于两项 false）。
     */
    @Transactional(readOnly = true)
    fun findIntroductionSendFacts(orcidIds: Collection<String>): Map<String, IntroductionSendFact> {
        val distinct = normalize(orcidIds)
        if (distinct.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, IntroductionSendFact>()
        chunkedQuery(distinct) { args ->
            jdbcTemplate.query(SEND_FACTS_SQL.replace(PLACEHOLDERS, placeholders(args.size)), { rs, _ ->
                rs.getString("orcid_id") to IntroductionSendFact(
                    sent = rs.getInt("sent_flag") == 1,
                    permanentFailure = rs.getInt("permanent_flag") == 1
                )
            }, *args.toTypedArray())
        }.forEach { (orcid, fact) -> out[orcid] = fact }
        return out
    }

    private fun queryOrcids(sql: String, orcids: List<String>): Set<String> {
        if (orcids.isEmpty()) return emptySet()
        return chunkedQuery(orcids) { args ->
            jdbcTemplate.query(sql.replace(PLACEHOLDERS, placeholders(args.size)), { rs, _ ->
                rs.getString(1)
            }, *args.toTypedArray())
        }.toSet()
    }

    /**
     * 按 [BATCH_SIZE] 分批执行，单批失败原样抛出（不吞异常、不返回部分结果冒充完整结果）。
     * `rowSource` 只负责把一批入参跑成一批结果行。
     */
    private fun <T> chunkedQuery(values: List<String>, rowSource: (List<String>) -> List<T>): List<T> {
        val out = ArrayList<T>(values.size)
        for (batch in values.chunked(BATCH_SIZE)) {
            out.addAll(rowSource(batch))
        }
        return out
    }

    private fun normalize(values: Collection<String>): List<String> =
        values.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().toList()

    private fun placeholders(size: Int): String = Array(size) { "?" }.joinToString(",")

    companion object {
        /** 每批参数化 IN 的上限（与批量准入读取 500 一致）。 */
        const val BATCH_SIZE = 500

        /** 占位符替换锚点：values 只经 `?` 绑定，绝不字符串拼接。 */
        private const val PLACEHOLDERS = "/* VALUES */"

        /**
         * `mail_record.error_summary` 中「首封永久失败」的持久化前缀 —— 与
         * `ManualInitialOutreachService` 在线判定共用同一字符串，禁止各自硬编码第二份。
         */
        const val PERMANENT_FAILURE_SUMMARY_PREFIX = "PERMANENT:"

        private const val BOUND_SQL = """
            SELECT DISTINCT orcid_id
              FROM expert_contact
             WHERE orcid_id IN (/* VALUES */)
               AND bound_sender_account_code IS NOT NULL
               AND bound_sender_account_code <> ''
        """

        private const val SUPPRESSED_SQL = """
            SELECT DISTINCT email
              FROM email_suppression
             WHERE email IN (/* VALUES */)
        """

        private const val EMAIL_INVALID_SQL = """
            SELECT DISTINCT orcid_id
              FROM expert_contact
             WHERE orcid_id IN (/* VALUES */)
               AND operator_status = 'EMAIL_INVALID'
        """

        private const val SEND_FACTS_SQL = """
            SELECT ec.orcid_id AS orcid_id,
                   MAX(CASE WHEN mr.direction = 'OUTBOUND' AND mr.mail_type = 'INTRODUCTION'
                             AND mr.send_status = 'SENT' THEN 1 ELSE 0 END) AS sent_flag,
                   MAX(CASE WHEN mr.direction = 'OUTBOUND' AND mr.mail_type = 'INTRODUCTION'
                             AND mr.send_status = 'FAILED'
                             AND mr.error_summary LIKE 'PERMANENT:%' THEN 1 ELSE 0 END) AS permanent_flag
              FROM expert_contact ec
              JOIN mail_record mr ON mr.expert_contact_id = ec.id
             WHERE ec.orcid_id IN (/* VALUES */)
             GROUP BY ec.orcid_id
        """
    }
}

/** I-2（02）：一次批量查询返回的发送事实。 */
data class IntroductionSendFact(
    val sent: Boolean,
    val permanentFailure: Boolean
)
