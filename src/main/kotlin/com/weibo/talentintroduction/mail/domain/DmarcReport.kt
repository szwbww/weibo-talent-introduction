package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("dmarc_report")
data class DmarcReport(
    @Id
    val id: Long? = null,
    val reportId: String,
    val orgName: String?,
    val domain: String,
    val dateBegin: LocalDateTime,
    val dateEnd: LocalDateTime,
    val totalCount: Long,
    val dkimPassCount: Long,
    val spfPassCount: Long,
    val dmarcPassCount: Long,
    val topSourceIp: String?,
    val createdAt: LocalDateTime? = null
)
