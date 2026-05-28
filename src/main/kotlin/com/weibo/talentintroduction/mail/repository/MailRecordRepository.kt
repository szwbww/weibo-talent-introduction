package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.MailRecord
import org.springframework.data.repository.CrudRepository

interface MailRecordRepository : CrudRepository<MailRecord, Long> {
    fun findAllByExpertContactIdOrderByCreatedAtAsc(expertContactId: Long): List<MailRecord>

    fun existsByExpertContactIdAndDirectionAndMailType(
        expertContactId: Long,
        direction: String,
        mailType: String
    ): Boolean
}
