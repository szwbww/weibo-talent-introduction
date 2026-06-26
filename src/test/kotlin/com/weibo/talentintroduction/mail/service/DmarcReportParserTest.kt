package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class DmarcReportParserTest {
    private val parser = DmarcReportParser()

    @Test
    fun `parses google aggregate xml sample`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <feedback>
              <report_metadata>
                <org_name>google.com</org_name>
                <email>noreply-dmarc-support@google.com</email>
                <report_id>20260101000000</report_id>
                <date_range>
                  <begin>1609459200</begin>
                  <end>1609545600</end>
                </date_range>
              </report_metadata>
              <policy_published>
                <domain>qftechtalent.com</domain>
                <adkim>r</adkim>
                <aspf>r</aspf>
                <p>none</p>
                <sp>none</sp>
                <pct>100</pct>
              </policy_published>
              <record>
                <row>
                  <source_ip>203.0.113.10</source_ip>
                  <count>7</count>
                  <policy_evaluated>
                    <disposition>none</disposition>
                    <dkim>pass</dkim>
                    <spf>pass</spf>
                  </policy_evaluated>
                </row>
                <identifiers>
                  <header_from>qftechtalent.com</header_from>
                </identifiers>
                <auth_results>
                  <dkim>
                    <domain>qftechtalent.com</domain>
                    <result>pass</result>
                  </dkim>
                  <spf>
                    <domain>qftechtalent.com</domain>
                    <result>pass</result>
                  </spf>
                </auth_results>
              </record>
              <record>
                <row>
                  <source_ip>198.51.100.2</source_ip>
                  <count>3</count>
                  <policy_evaluated>
                    <disposition>none</disposition>
                    <dkim>fail</dkim>
                    <spf>pass</spf>
                  </policy_evaluated>
                </row>
                <identifiers>
                  <header_from>qftechtalent.com</header_from>
                </identifiers>
              </record>
            </feedback>
        """.trimIndent()

        val gzBytes = gzip(xml.toByteArray(Charsets.UTF_8))
        val summary = parser.parse(
            ReceivedMailAttachment(
                fileName = "google.com!qftechtalent.com!1609459200!1609545600.xml.gz",
                contentType = "application/gzip",
                content = gzBytes
            )
        )

        requireNotNull(summary)
        assertEquals("20260101000000", summary.reportId)
        assertEquals("google.com", summary.orgName)
        assertEquals("qftechtalent.com", summary.domain)
        assertEquals(1609459200L, summary.dateBegin)
        assertEquals(1609545600L, summary.dateEnd)
        assertEquals(10L, summary.totalCount)
        assertEquals(7L, summary.dkimPassCount)
        assertEquals(10L, summary.spfPassCount)
        assertEquals(10L, summary.dmarcPassCount)
        assertEquals("203.0.113.10", summary.topSourceIp)
    }

    @Test
    fun `dmarc pass counts when only dkim passes`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <feedback>
              <report_metadata>
                <org_name>google.com</org_name>
                <report_id>dkim-only-pass</report_id>
                <date_range>
                  <begin>1609459200</begin>
                  <end>1609545600</end>
                </date_range>
              </report_metadata>
              <policy_published>
                <domain>example.com</domain>
              </policy_published>
              <record>
                <row>
                  <source_ip>203.0.113.20</source_ip>
                  <count>4</count>
                  <policy_evaluated>
                    <dkim>pass</dkim>
                    <spf>fail</spf>
                  </policy_evaluated>
                </row>
              </record>
            </feedback>
        """.trimIndent()

        val summary = parser.parse(
            ReceivedMailAttachment(
                fileName = "report.xml",
                contentType = "application/xml",
                content = xml.toByteArray(Charsets.UTF_8)
            )
        )

        requireNotNull(summary)
        assertEquals(4L, summary.dkimPassCount)
        assertEquals(0L, summary.spfPassCount)
        assertEquals(4L, summary.dmarcPassCount)
    }

    @Test
    fun `returns null for corrupted gzip`() {
        val summary = parser.parse(
            ReceivedMailAttachment(
                fileName = "bad.xml.gz",
                contentType = "application/gzip",
                content = "not-gzip".toByteArray()
            )
        )
        assertNull(summary)
    }

    private fun gzip(content: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(content) }
        return output.toByteArray()
    }
}
