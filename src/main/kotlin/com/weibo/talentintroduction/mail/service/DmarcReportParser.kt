package com.weibo.talentintroduction.mail.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class DmarcReportSummary(
    val reportId: String,
    val orgName: String?,
    val domain: String,
    val dateBegin: Long,
    val dateEnd: Long,
    val totalCount: Long,
    val dkimPassCount: Long,
    val spfPassCount: Long,
    val dmarcPassCount: Long,
    val topSourceIp: String?
)

@Service
class DmarcReportParser {
    private val log = LoggerFactory.getLogger(DmarcReportParser::class.java)

    fun parse(attachment: ReceivedMailAttachment): DmarcReportSummary? {
        return try {
            val xmlBytes = decompressToXmlBytes(attachment) ?: return null
            parseXml(xmlBytes)
        } catch (e: Exception) {
            log.warn("Failed to parse DMARC attachment {}", attachment.fileName, e)
            null
        }
    }

    private fun decompressToXmlBytes(attachment: ReceivedMailAttachment): ByteArray? {
        val content = attachment.content
        if (content.isEmpty()) {
            return null
        }
        val fileName = attachment.fileName.lowercase()
        return when {
            fileName.endsWith(".gz") || isGzip(content) ->
                GZIPInputStream(ByteArrayInputStream(content)).use { it.readBytes() }
            fileName.endsWith(".zip") || isZip(content) ->
                ZipInputStream(ByteArrayInputStream(content)).use { zip ->
                    generateSequence { zip.nextEntry }.firstOrNull()?.let { zip.readBytes() }
                }
            else -> content
        }
    }

    private fun parseXml(xmlBytes: ByteArray): DmarcReportSummary? {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val document = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xmlBytes))
        val root = document.documentElement ?: return null

        val reportId = root.firstChildText("report_id") ?: return null
        val orgName = root.firstChildText("org_name")
        val domain = root.firstChildTextIn("policy_published", "domain") ?: return null
        val dateBegin = root.firstChildTextIn("date_range", "begin")?.toLongOrNull() ?: return null
        val dateEnd = root.firstChildTextIn("date_range", "end")?.toLongOrNull() ?: return null

        var totalCount = 0L
        var dkimPassCount = 0L
        var spfPassCount = 0L
        var dmarcPassCount = 0L
        val sourceIpCounts = mutableMapOf<String, Long>()

        val records = root.getElementsByTagName("record")
        for (i in 0 until records.length) {
            val record = records.item(i) as? Element ?: continue
            val row = record.getElementsByTagName("row").item(0) as? Element ?: continue
            val count = row.firstChildText("count")?.toLongOrNull() ?: 1L
            val sourceIp = row.firstChildText("source_ip")
            if (sourceIp != null) {
                sourceIpCounts[sourceIp] = sourceIpCounts.getOrDefault(sourceIp, 0L) + count
            }

            totalCount += count
            val policyEvaluated = record.getElementsByTagName("policy_evaluated").item(0) as? Element
            val dkim = policyEvaluated?.firstChildText("dkim")?.lowercase()
            val spf = policyEvaluated?.firstChildText("spf")?.lowercase()
            if (dkim == "pass") {
                dkimPassCount += count
            }
            if (spf == "pass") {
                spfPassCount += count
            }
            if (dkim == "pass" || spf == "pass") {
                dmarcPassCount += count
            }
        }

        return DmarcReportSummary(
            reportId = reportId,
            orgName = orgName,
            domain = domain,
            dateBegin = dateBegin,
            dateEnd = dateEnd,
            totalCount = totalCount,
            dkimPassCount = dkimPassCount,
            spfPassCount = spfPassCount,
            dmarcPassCount = dmarcPassCount,
            topSourceIp = sourceIpCounts.maxByOrNull { it.value }?.key
        )
    }

    private fun Element.firstChildText(tag: String): String? {
        val nodes = getElementsByTagName(tag)
        if (nodes.length == 0) {
            return null
        }
        return nodes.item(0).textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun Element.firstChildTextIn(parentTag: String, tag: String): String? {
        val parent = getElementsByTagName(parentTag).item(0) as? Element ?: return null
        return parent.firstChildText(tag)
    }

    private fun isGzip(content: ByteArray): Boolean =
        content.size >= 2 && content[0] == 0x1f.toByte() && content[1] == 0x8b.toByte()

    private fun isZip(content: ByteArray): Boolean =
        content.size >= 2 && content[0] == 'P'.code.toByte() && content[1] == 'K'.code.toByte()
}
