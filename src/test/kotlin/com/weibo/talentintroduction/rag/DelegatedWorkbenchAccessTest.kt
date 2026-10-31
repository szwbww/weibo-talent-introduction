package com.weibo.talentintroduction.rag

import com.weibo.talentintroduction.auth.config.*
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.repository.*
import com.weibo.talentintroduction.mail.service.*
import com.weibo.talentintroduction.rag.controller.*
import com.weibo.talentintroduction.rag.service.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.mock.web.MockHttpServletRequest
import java.time.LocalDateTime
import java.util.Optional

class DelegatedWorkbenchAccessTest {
    @Test fun `only LIVE inbound owned by a FULL expert reaches composer and revocation takes effect`() {
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        val processing = Mockito.mock(InboundMailProcessingRepository::class.java)
        val records = Mockito.mock(MailRecordRepository::class.java)
        val access = Mockito.mock(ExpertMailReadAccessService::class.java)
        val guard = ExpertMailPermissionGuard(access, Mockito.mock(NamedParameterJdbcTemplate::class.java))
        val composer = Mockito.mock(RagLetterComposer::class.java) { throw IllegalStateException("composer reached") }
        val controller = RagReplyController(composer, Mockito.mock(RagKnowledgeBase::class.java), records, processing, contacts, LlmProperties(), guard)
        val http = MockHttpServletRequest().apply { getSession(true).setAttribute(AuthSessionKeys.USERNAME, "user1") }
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(ExpertContact(7, 1, "fixture", "expert@example.test", "Fixture")))
        Mockito.`when`(processing.findById(99L)).thenReturn(Optional.of(InboundMailProcessing(
            id=99, senderAccountCode="acc", imapUid=1, messageId="fixture", fromEmail="expert@example.test",
            subject="Fixture", receivedAt=LocalDateTime.now(), processStatus="MANUAL_REVIEW", processReason="test", expertContactId=7)))
        for (level in listOf(ExpertMailAccessLevel.READ, ExpertMailAccessLevel.NONE)) {
            Mockito.`when`(access.accessLevel(7)).thenReturn(level)
            assertThrows(MailReadAccessDeniedException::class.java) { controller.compose(RagReplyComposeRequest("LIVE_INBOUND", 99), http) }
        }
        Mockito.`when`(access.accessLevel(7)).thenReturn(ExpertMailAccessLevel.FULL)
        assertEquals("composer reached", assertThrows(IllegalStateException::class.java) { controller.compose(RagReplyComposeRequest("LIVE_INBOUND", 99), http) }.message)
        assertThrows(MailReadAccessDeniedException::class.java) { controller.compose(RagReplyComposeRequest("TRAINING_MAIL", 99), http) }
        Mockito.verifyNoInteractions(records)
        Mockito.`when`(access.accessLevel(7)).thenReturn(ExpertMailAccessLevel.READ)
        assertThrows(MailReadAccessDeniedException::class.java) { controller.compose(RagReplyComposeRequest("LIVE_INBOUND", 99), http) }
    }
}
