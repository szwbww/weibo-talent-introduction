package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service

@Service
class DmarcReportDetector {
    fun isDmarcAggregateReport(
        from: String?,
        subject: String?,
        attachments: List<ReceivedMailAttachment>
    ): Boolean {
        val fromLower = from?.lowercase() ?: ""
        if (fromLower.contains("dmarc")) {
            return true
        }

        val subjectLower = subject?.lowercase() ?: ""
        if (subjectLower.contains("report domain") &&
            (subjectLower.contains("submitter") || subjectLower.contains("report-id"))
        ) {
            return true
        }

        return attachments.any { isAggregateAttachment(it) }
    }

    private fun isAggregateAttachment(attachment: ReceivedMailAttachment): Boolean {
        val fileName = attachment.fileName.lowercase()
        if (AGGREGATE_FILE_NAME.matches(fileName)) {
            return true
        }
        val contentType = attachment.contentType?.lowercase() ?: ""
        if ((contentType.contains("gzip") || contentType.contains("x-gzip")) &&
            (fileName.endsWith(".xml.gz") || fileName.endsWith(".xml") || fileName.contains(".xml"))
        ) {
            return true
        }
        if (contentType.contains("xml") && fileName.contains("!")) {
            return true
        }
        return false
    }

    companion object {
        private val AGGREGATE_FILE_NAME = Regex(""".*!.*\.xml(\.gz|\.zip)?$""")
    }
}
