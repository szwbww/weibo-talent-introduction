package com.weibo.talentintroduction.common.controller

import com.weibo.talentintroduction.mail.service.ManualSendSafetyBlockedException
import com.weibo.talentintroduction.mail.service.SafetyFinding
import com.weibo.talentintroduction.mail.service.SafetySeverity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ManualSendSafetyBlockedHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `maps blocked send to 422 with code and ordered findings`() {
        val ex = ManualSendSafetyBlockedException(
            listOf(
                SafetyFinding("AI_REPLY_CLAIM_TRUST_RHETORIC", SafetySeverity.NORMAL, "Please rest assured."),
                SafetyFinding("AI_REPLY_ACTION_SENSITIVE_MATERIAL", SafetySeverity.STRONG, "send your passport")
            )
        )

        val response = handler.handleManualSendSafetyBlocked(ex)
        val body = requireNotNull(response.body)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals("MANUAL_SEND_SAFETY_BLOCKED", body.code)
        assertTrue(body.requiresStrongConfirmation)
        assertFalse(body.truncated)
        assertEquals(2, body.findings.size)
        // I-7: 顺序与入参一致
        assertEquals("AI_REPLY_CLAIM_TRUST_RHETORIC", body.findings[0].code)
        assertEquals("AI_REPLY_ACTION_SENSITIVE_MATERIAL", body.findings[1].code)
        assertEquals("STRONG", body.findings[1].severity)
    }

    @Test
    fun `caps findings at 20 and marks truncated`() {
        val findings = (1..21).map { SafetyFinding("CODE_$it", SafetySeverity.NORMAL, "sentence $it") }

        val response = handler.handleManualSendSafetyBlocked(ManualSendSafetyBlockedException(findings))
        val body = requireNotNull(response.body)

        assertTrue(body.truncated)
        assertEquals(20, body.findings.size)
        assertEquals("CODE_1", body.findings.first().code)
        assertEquals("CODE_20", body.findings.last().code)
    }

    @Test
    fun `truncates overlong sentence with ellipsis`() {
        val longSentence = "x".repeat(300)
        val ex = ManualSendSafetyBlockedException(
            listOf(SafetyFinding("CODE_1", SafetySeverity.NORMAL, longSentence))
        )

        val response = handler.handleManualSendSafetyBlocked(ex)
        val sentence = requireNotNull(requireNotNull(response.body).findings.single().sentence)

        assertTrue(sentence.endsWith("…"))
        assertTrue(sentence.length <= 200)
    }
}
