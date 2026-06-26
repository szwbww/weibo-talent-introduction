package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.DmarcReport
import com.weibo.talentintroduction.mail.repository.DmarcReportRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class DmarcReportIngestService(
    private val parser: DmarcReportParser,
    private val dmarcReportRepository: DmarcReportRepository
) {
    fun ingest(attachments: List<ReceivedMailAttachment>) {
        val now = LocalDateTime.now()
        attachments.forEach { attachment ->
            val summary = parser.parse(attachment) ?: return@forEach
            if (dmarcReportRepository.existsByReportId(summary.reportId)) {
                return@forEach
            }
            dmarcReportRepository.save(summary.toEntity(now))
        }
    }

    private fun DmarcReportSummary.toEntity(now: LocalDateTime): DmarcReport =
        DmarcReport(
            reportId = reportId,
            orgName = orgName,
            domain = domain,
            dateBegin = epochSecondsToLocalDateTime(dateBegin),
            dateEnd = epochSecondsToLocalDateTime(dateEnd),
            totalCount = totalCount,
            dkimPassCount = dkimPassCount,
            spfPassCount = spfPassCount,
            dmarcPassCount = dmarcPassCount,
            topSourceIp = topSourceIp,
            createdAt = now
        )

    private fun epochSecondsToLocalDateTime(epochSeconds: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)
}
