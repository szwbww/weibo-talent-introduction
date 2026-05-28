package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InboundIntentClassifierTest {
    private val classifier = InboundIntentClassifier()

    @Test
    fun `classifies positive interest as meeting invitation action`() {
        val result = classifier.classify("I am interested and happy to discuss this program.")

        assertEquals(InboundIntentCode.INTERESTED, result.intentCode)
        assertEquals(AutoIntentAction.SEND_MEETING_INVITATION, result.autoAction)
        assertTrue(result.confidence >= 75)
    }

    @Test
    fun `routes attachments to manual review`() {
        val result = classifier.classify("Attached is my CV for your review.")

        assertEquals(InboundIntentCode.CV_ATTACHED, result.intentCode)
        assertEquals(AutoIntentAction.MANUAL_REVIEW, result.autoAction)
    }

    @Test
    fun `allows process questions to continue through QA matching`() {
        val result = classifier.classify("Could you explain the application process and timeline?")

        assertEquals(InboundIntentCode.ASK_PROCESS, result.intentCode)
        assertEquals(AutoIntentAction.QA, result.autoAction)
    }

    @Test
    fun `closes clear unsubscribe replies`() {
        val result = classifier.classify("Please remove me from your list. I am not interested.")

        assertEquals(InboundIntentCode.NOT_INTERESTED, result.intentCode)
        assertEquals(AutoIntentAction.CLOSE, result.autoAction)
    }
}
