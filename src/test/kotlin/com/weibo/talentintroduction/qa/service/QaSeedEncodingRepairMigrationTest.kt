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
}
