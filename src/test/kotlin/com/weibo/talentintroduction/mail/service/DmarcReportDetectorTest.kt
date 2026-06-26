package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DmarcReportDetectorTest {
    private val detector = DmarcReportDetector()

    @Test
    fun `google dmarc sender is detected`() {
        assertTrue(
            detector.isDmarcAggregateReport(
                from = "noreply-dmarc-support@google.com",
                subject = "Report domain: example.com Submitter: google.com Report-ID: abc",
                attachments = emptyList()
            )
        )
    }

    @Test
    fun `report domain subject is detected`() {
        assertTrue(
            detector.isDmarcAggregateReport(
                from = "reports@example.net",
                subject = "Report domain: qftechtalent.com Submitter: google.com Report-ID: 123",
                attachments = emptyList()
            )
        )
    }

    @Test
    fun `aggregate attachment name is detected`() {
        assertTrue(
            detector.isDmarcAggregateReport(
                from = "reports@example.net",
                subject = "DMARC report",
                attachments = listOf(
                    ReceivedMailAttachment(
                        fileName = "google.com!qftechtalent.com!1609459200!1609545600.xml.gz",
                        contentType = "application/gzip",
                        content = ByteArray(0)
                    )
                )
            )
        )
    }

    @Test
    fun `normal expert reply is not detected`() {
        assertFalse(
            detector.isDmarcAggregateReport(
                from = "expert@university.edu",
                subject = "Re: Introduction",
                attachments = emptyList()
            )
        )
    }
}
