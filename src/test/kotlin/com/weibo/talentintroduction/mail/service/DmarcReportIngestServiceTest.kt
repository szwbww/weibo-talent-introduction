package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.DmarcReport
import com.weibo.talentintroduction.mail.repository.DmarcReportRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime

class DmarcReportIngestServiceTest {
    private val parser = Mockito.mock(DmarcReportParser::class.java)
    private val repository = Mockito.mock(DmarcReportRepository::class.java)
    private val service = DmarcReportIngestService(parser, repository)

    @Test
    fun `ingest saves new report id`() {
        val attachment = ReceivedMailAttachment(
            fileName = "google.com!example.com!1!2.xml.gz",
            contentType = "application/gzip",
            content = ByteArray(0)
        )
        val summary = DmarcReportSummary(
            reportId = "report-1",
            orgName = "google.com",
            domain = "example.com",
            dateBegin = 1609459200L,
            dateEnd = 1609545600L,
            totalCount = 5L,
            dkimPassCount = 4L,
            spfPassCount = 5L,
            dmarcPassCount = 4L,
            topSourceIp = "1.2.3.4"
        )
        Mockito.`when`(parser.parse(attachment)).thenReturn(summary)
        Mockito.`when`(repository.existsByReportId("report-1")).thenReturn(false)

        service.ingest(listOf(attachment))

        val captor = ArgumentCaptor.forClass(DmarcReport::class.java)
        Mockito.verify(repository).save(captor.capture())
        assertEquals("report-1", captor.value.reportId)
        assertEquals("google.com", captor.value.orgName)
    }

    @Test
    fun `ingest skips duplicate report id`() {
        val attachment = ReceivedMailAttachment(
            fileName = "google.com!example.com!1!2.xml.gz",
            contentType = "application/gzip",
            content = ByteArray(0)
        )
        Mockito.`when`(parser.parse(attachment)).thenReturn(
            DmarcReportSummary(
                reportId = "report-dup",
                orgName = "google.com",
                domain = "example.com",
                dateBegin = 1609459200L,
                dateEnd = 1609545600L,
                totalCount = 1L,
                dkimPassCount = 1L,
                spfPassCount = 1L,
                dmarcPassCount = 1L,
                topSourceIp = null
            )
        )
        Mockito.`when`(repository.existsByReportId("report-dup")).thenReturn(true)

        service.ingest(listOf(attachment))

        Mockito.verify(repository, Mockito.never()).save(Mockito.any(DmarcReport::class.java))
    }
}
