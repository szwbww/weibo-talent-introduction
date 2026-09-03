package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.MailRecordRagFact
import org.springframework.data.repository.CrudRepository

/**
 * 计划 03b (T2): `mail_record_rag_fact` 存证仓储，与
 * [MailRecordQaRuleRepository] 同形（I-42：读取恒按 ordinal 升序还原请求顺序）。
 */
interface MailRecordRagFactRepository : CrudRepository<MailRecordRagFact, Long> {
    fun findByMailRecordIdOrderByOrdinalAsc(mailRecordId: Long): List<MailRecordRagFact>
}
