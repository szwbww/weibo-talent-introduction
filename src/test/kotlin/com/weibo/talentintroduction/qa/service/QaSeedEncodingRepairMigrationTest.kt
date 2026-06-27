package com.weibo.talentintroduction.qa.service

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class QaSeedEncodingRepairMigrationTest {
    @Test
    fun `new qa rule display names are repaired with utf8 hex literals`() {
        val sqlPath = Path.of("src/main/resources/db/migration/V44__repair_qa_new_rule_display_names_encoding.sql")
        val sql = Files.readString(sqlPath)

        expectedRepairs.forEach { (replySubject, displayName) ->
            val hex = displayName.toByteArray(Charsets.UTF_8).joinToString("") { "%02X".format(it) }
            assertTrue(
                sql.contains("CONVERT(UNHEX('$hex') USING utf8mb4)") &&
                    sql.contains("reply_subject = '$replySubject'"),
                "Missing UTF-8 hex repair for $replySubject"
            )
        }
    }

    @Test
    fun `new qa rule reply bodies use ascii range separators after repair`() {
        val sqlPath = Path.of("src/main/resources/db/migration/V45__repair_qa_reply_body_encoding.sql")
        val sql = Files.readString(sqlPath)

        expectedReplyBodyRepairs.forEach { (replySubject, replyBody) ->
            assertTrue(
                sql.contains("reply_body = '$replyBody'") &&
                    sql.contains("reply_subject = '$replySubject'"),
                "Missing ASCII reply body repair for $replySubject"
            )
        }
        assertTrue(!sql.contains("–"), "Repair SQL must not contain UTF-8 en dash")
        assertTrue(!sql.contains("â"), "Repair SQL must not contain mojibake en dash")
    }

    private val expectedRepairs = linkedMapOf(
        "Confirmation video requirement" to "承诺视频 VCR",
        "Single application commitment" to "单一申报承诺",
        "After selection process" to "入选后流程",
        "Success rate and reapplication" to "成功率/未入选",
        "Document confidentiality and no fees" to "资料保密·绝不收费",
        "Agency credentials and government cooperation" to "代理资质·政府合作证明",
        "Multi-agency rights protection" to "多代理·权益保障",
        "Project sensitivity concerns" to "项目敏感性",
        "Meeting arrangement" to "会议安排",
        "Email-only communication preference" to "只邮件·不用LinkedIn",
        "Partner company information" to "合作企业信息",
        "Program overview" to "项目总览"
    )

    private val expectedReplyBodyRepairs = linkedMapOf(
        "Confirmation video requirement" to "To prevent AI-forged materials and duplicate applications, we need a short confirmation video (about 3-7 minutes) showing you holding your passport and reading the commitment statement. Please submit it together with your application materials.",
        "Meeting arrangement" to "Zoom, Teams, or Webex are all fine. We typically schedule 15-20 minutes and will arrange a time based on your time zone, sending the meeting link before the call.",
        "Program overview" to "Thank you for your interest in our talent program. This is a national-level initiative that connects outstanding overseas experts with Chinese enterprises through two main tracks: Innovative Talent Schemes for high-caliber researchers joining enterprises, and Entrepreneurial Talent Schemes for experts who can convert research into products. Selected candidates may receive government research funding in the range of 3-12 million RMB, with enterprises providing personal salary support. Typical application materials include a passport, doctoral degree certificate, CV with publications and achievements, proof of employment, and supporting certificates. After you submit materials, our team matches partner enterprises, prepares application documents, and submits them for review; the overall cycle often spans six months or longer, with results commonly announced in late autumn. We keep all materials strictly confidential, never charge fees, and you may take time to decide after selection."
    )
}
