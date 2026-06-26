package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.DmarcReport
import org.springframework.data.repository.CrudRepository

interface DmarcReportRepository : CrudRepository<DmarcReport, Long> {
    fun existsByReportId(reportId: String): Boolean
}
