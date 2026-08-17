package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.mail.domain.MailRecord
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MailRecordTaskExecutionLinkTest {
    @Test
    fun `MailRecord taskExecutionId defaults to null`() {
        // I2a-1 反向保证：除 ManualOutreachTxHelper 两处外，其余构造点不传该参数即恒为 null
        val record = MailRecord(
            expertContactId = 1L,
            direction = "OUTBOUND",
            mailType = "INTRODUCTION",
            messageId = null,
            inReplyTo = null,
            subject = null,
            body = null,
            matchedQaRuleId = null,
            sendStatus = "SENT",
            receivedAt = null,
            sentAt = null
        )
        assertNull(record.taskExecutionId)
    }

    @Test
    fun `V101 adds task execution column and index without foreign key`() {
        val sqlPath = Path.of("src/main/resources/db/migration/V101__add_task_execution_id_to_mail_record.sql")
        val sql = Files.readString(sqlPath)

        assertTrue(
            sql.contains("ADD COLUMN task_execution_id BIGINT NULL"),
            "V101 must add nullable task_execution_id column"
        )
        assertTrue(
            sql.contains("CREATE INDEX idx_mail_record_task_execution"),
            "V101 must create idx_mail_record_task_execution index"
        )
        assertFalse(sql.contains("FOREIGN KEY"), "V101 must not add a foreign key (I2a-4)")
        assertFalse(sql.contains("\${"), "V101 must not contain placeholder interpolation")
    }
}
