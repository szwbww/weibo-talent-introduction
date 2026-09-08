package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.DmarcReport
import com.weibo.talentintroduction.mail.repository.DmarcReportRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 05 I-3：DMARC purpose 消费者（[DmarcAttachmentTransferConsumer]）单元测试。
 * 使用真实 [DmarcReportParser]/[DmarcReportIngestService]（repo mock），验证：
 * 报表写入（gz/zip）、report_id 去重、解压成员数/总量上限、路径穿越拒绝、
 * 解析失败可见（DMARC_PARSE_FAILED）且修复后可重试成功。
 * 本测试只做有界内存解压，不写任何机器报告临时文件。
 */
class DmarcAttachmentTransferConsumerTest {
    private val repository = Mockito.mock(DmarcReportRepository::class.java)

    private fun consumer(
        properties: MailAttachmentStorageProperties = MailAttachmentStorageProperties()
    ): DmarcAttachmentTransferConsumer {
        val parser = DmarcReportParser()
        return DmarcAttachmentTransferConsumer(
            parser = parser,
            ingestService = DmarcReportIngestService(parser, repository),
            properties = properties
        )
    }

    @TempDir
    lateinit var tempDir: Path

    private fun writeFile(name: String, bytes: ByteArray): Path {
        val file = tempDir.resolve(name)
        Files.write(file, bytes)
        return file
    }

    private fun context(file: Path): AttachmentTransferConsumeContext =
        AttachmentTransferConsumeContext(
            transferId = 1L,
            file = file,
            byteCount = Files.size(file),
            contentType = "application/gzip"
        )

    private fun xml(reportId: String): String =
        """
        <?xml version="1.0" encoding="UTF-8" ?>
        <feedback>
          <report_metadata>
            <org_name>google.com</org_name>
            <report_id>$reportId</report_id>
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

    private fun gzip(content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(content) }
        return out.toByteArray()
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun savedReports(): List<DmarcReport> {
        val captor = ArgumentCaptor.forClass(DmarcReport::class.java)
        Mockito.verify(repository, Mockito.atLeastOnce()).save(captor.capture())
        return captor.allValues
    }

    @Test
    fun `gzip aggregate report is ingested into the original dmarc report table`() {
        val file = writeFile("report.part", gzip(xml("report-gz-1").toByteArray(Charsets.UTF_8)))

        consumer().consume(context(file))

        val saved = savedReports()
        assertEquals(1, saved.size)
        assertEquals("report-gz-1", saved[0].reportId)
        assertEquals("google.com", saved[0].orgName)
        assertEquals("example.com", saved[0].domain)
        assertEquals(4L, saved[0].totalCount)
        assertEquals(4L, saved[0].dkimPassCount)
        assertEquals(4L, saved[0].dmarcPassCount)
        assertEquals("203.0.113.20", saved[0].topSourceIp)
    }

    @Test
    fun `raw xml report file is ingested without any decompression step`() {
        val file = writeFile("report.part", xml("report-raw-1").toByteArray(Charsets.UTF_8))

        consumer().consume(context(file))

        assertEquals(listOf("report-raw-1"), savedReports().map { it.reportId })
    }

    @Test
    fun `zip with several xml members ingests each report`() {
        val content = zip(
            "google.com!example.com!1.xml" to xml("report-zip-1").toByteArray(Charsets.UTF_8),
            "google.com!example.com!2.xml" to xml("report-zip-2").toByteArray(Charsets.UTF_8)
        )
        val file = writeFile("report.part", content)

        consumer().consume(context(file))

        assertEquals(listOf("report-zip-1", "report-zip-2"), savedReports().map { it.reportId })
    }

    @Test
    fun `duplicate report id is not saved twice`() {
        Mockito.`when`(repository.existsByReportId("report-dup")).thenReturn(false, true)
        val file = writeFile("report.part", gzip(xml("report-dup").toByteArray(Charsets.UTF_8)))

        // 同一报表首次入库
        consumer().consume(context(file))
        // 队列重试再次消费同一内容：原 IngestService 按 report_id 去重
        consumer().consume(context(file))

        assertEquals(1, savedReports().size)
        assertEquals("report-dup", savedReports()[0].reportId)
    }

    @Test
    fun `zip bomb with too many archive members is rejected with limit exceeded`() {
        val limited = MailAttachmentStorageProperties(dmarcMaxArchiveMembers = 2)
        val content = zip(
            "a.xml" to xml("m1").toByteArray(Charsets.UTF_8),
            "b.xml" to xml("m2").toByteArray(Charsets.UTF_8),
            "c.xml" to xml("m3").toByteArray(Charsets.UTF_8)
        )
        val file = writeFile("report.part", content)

        val ex = assertThrows(AttachmentTransferConsumeError::class.java) {
            consumer(limited).consume(context(file))
        }
        assertEquals(DmarcAttachmentTransferConsumer.ERROR_LIMIT_EXCEEDED, ex.code)
    }

    @Test
    fun `extracted xml over the total byte cap is rejected`() {
        val limited = MailAttachmentStorageProperties(dmarcMaxExtractedBytes = 512)
        val bigXml = "<feedback><report_metadata><report_id>big</report_id></report_metadata>" +
            "<policy_published><domain>example.com</domain></policy_published>" +
            "<record>" + "<row><source_ip>1.1.1.1</source_ip><count>1</count></row>" +
            "</record>" + "x".repeat(4096) + "</feedback>"
        val file = writeFile("report.part", gzip(bigXml.toByteArray(Charsets.UTF_8)))

        val ex = assertThrows(AttachmentTransferConsumeError::class.java) {
            consumer(limited).consume(context(file))
        }
        assertEquals(DmarcAttachmentTransferConsumer.ERROR_LIMIT_EXCEEDED, ex.code)
    }

    @Test
    fun `path traversal zip member is rejected`() {
        val content = zip("../evil.xml" to xml("evil").toByteArray(Charsets.UTF_8))
        val file = writeFile("report.part", content)

        val ex = assertThrows(AttachmentTransferConsumeError::class.java) {
            consumer().consume(context(file))
        }
        assertEquals(DmarcAttachmentTransferConsumer.ERROR_ARCHIVE_INVALID, ex.code)
    }

    @Test
    fun `parse failure is a visible explicit error and becomes retryable after the report is fixed`() {
        // 坏报表：XML 内容存在但缺少 report_id（原 parser 返回 null）
        val broken = (
            "<?xml version=\"1.0\"?><feedback><policy_published><domain>x</domain>" +
                "</policy_published></feedback>"
            ).toByteArray(Charsets.UTF_8)
        val file = writeFile("report.part", gzip(broken))

        val ex = assertThrows(AttachmentTransferConsumeError::class.java) {
            consumer().consume(context(file))
        }
        assertEquals(DmarcAttachmentTransferConsumer.ERROR_DMARC_PARSE_FAILED, ex.code)
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(DmarcReport::class.java))

        // 队列重试 = 从头重新获取；内容修复后同一消费者实例直接成功（无状态残留）
        val fixedFile = writeFile("report-fixed.part", gzip(xml("report-retry-ok").toByteArray(Charsets.UTF_8)))
        consumer().consume(context(fixedFile))
        assertEquals(listOf("report-retry-ok"), savedReports().map { it.reportId })
    }

    @Test
    fun `corrupted compressed stream is a visible parse failure not a silent skip`() {
        val file = writeFile("report.part", "not-a-real-gzip-stream".toByteArray(Charsets.UTF_8))

        val ex = assertThrows(AttachmentTransferConsumeError::class.java) {
            consumer().consume(context(file))
        }
        assertEquals(DmarcAttachmentTransferConsumer.ERROR_DMARC_PARSE_FAILED, ex.code)
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(DmarcReport::class.java))
        // 失败码可见且对应行处于 FAILED（可显式重试），不落入无声日志
        assertNotNull(ex.message)
        assertTrue(ex.code.isNotBlank())
    }
}
