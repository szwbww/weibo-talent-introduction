package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.mail.service.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class MailReplyDraftControllerTest {
    private val service = mock(MailReplyDraftService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(MailReplyDraftController(service)).build()
    private fun session(owner: String) = MockHttpSession().also { it.setAttribute(AuthSessionKeys.USERNAME,owner) }
    @Test fun `all endpoints require session and never invoke service anonymously`() {
        listOf(get("/api/mail/mailbox/drafts"),get("/api/mail/mailbox/drafts/summaries"),get("/api/mail/mailbox/drafts/1"),
            get("/api/mail/mailbox/drafts/target?contactId=11&kind=OUTBOUND"),delete("/api/mail/mailbox/drafts/1?expectedVersion=1"),
            put("/api/mail/mailbox/drafts/target?contactId=11&kind=OUTBOUND").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"content\":{}}"))
            .forEach { mvc.perform(it).andExpect(status().isUnauthorized) }
        verifyNoInteractions(service)
    }
    @Test fun `session username not forged body owner defines persisted identity`() {
        val target = MailReplyDraftTarget(11,MailReplyDraftKind.OUTBOUND)
        val body = MailReplyDraftSaveRequest(0,MailReplyDraftContent(text="draft"))
        `when`(service.save("A",target,body)).thenReturn(MailReplyDraftDetail(4,1,MailReplyDraftState.ACTIVE,target,body.content))
        mvc.perform(put("/api/mail/mailbox/drafts/target?contactId=11&kind=OUTBOUND").session(session("A"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"content\":{\"text\":\"draft\"}}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.id").value(4)).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.content.text").value("draft"))
        verify(service).save("A",target,body)
        mvc.perform(put("/api/mail/mailbox/drafts/target?contactId=11&kind=OUTBOUND").session(session("A"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"B\",\"expectedVersion\":0,\"content\":{\"text\":\"draft\"}}"))
            .andExpect(status().isUnprocessableEntity)
        verifyNoMoreInteractions(service)
    }
    @Test fun `HTTP codec rejects fractional overflow missing versions and unknown snapshot fields`() {
        val bodies = listOf(
            "{\"content\":{}}",
            "{\"expectedVersion\":1.5,\"content\":{}}",
            "{\"expectedVersion\":9223372036854775808,\"content\":{}}",
            "{\"expectedVersion\":0,\"content\":{\"context\":{\"schemaVersion\":2}}}",
            "{\"expectedVersion\":0,\"content\":{\"context\":{\"schemaVersion\":1.5}}}",
            "{\"expectedVersion\":0,\"content\":{\"context\":{\"downloadUrl\":\"blob:private\"}}}",
            "{\"expectedVersion\":0,\"sendAttemptId\":99,\"content\":{}}"
        )
        bodies.forEach { body ->
            mvc.perform(put("/api/mail/mailbox/drafts/target?contactId=11&kind=OUTBOUND").session(session("A"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableEntity)
        }
        verifyNoInteractions(service)
    }
    @Test fun `missing target raw response distinguishes no row from terminal row`() {
        val target = MailReplyDraftTarget(11,MailReplyDraftKind.OUTBOUND)
        `when`(service.getTarget("A",target)).thenReturn(MailReplyDraftDetail(target=target))
        mvc.perform(get("/api/mail/mailbox/drafts/target?contactId=11&kind=OUTBOUND").session(session("A")))
            .andExpect(status().isOk).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.state").isEmpty)
        `when`(service.get("A",4)).thenReturn(MailReplyDraftDetail(4,3,MailReplyDraftState.DISCARDED))
        mvc.perform(get("/api/mail/mailbox/drafts/4").session(session("A"))).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("DISCARDED")).andExpect(jsonPath("$.content").isEmpty)
    }
    @Test fun `version conflict exposes current marker and no overwrite retry`() {
        `when`(service.discard("A",4,1)).thenThrow(MailReplyDraftException(HttpStatus.CONFLICT,"DRAFT_VERSION_CONFLICT","conflict",2,MailReplyDraftState.ACTIVE))
        mvc.perform(delete("/api/mail/mailbox/drafts/4?expectedVersion=1").session(session("A")))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("DRAFT_VERSION_CONFLICT"))
            .andExpect(jsonPath("$.currentVersion").value(2)).andExpect(jsonPath("$.currentState").value("ACTIVE"))
        verify(service,times(1)).discard("A",4,1)
    }
    @Test fun `list summaries and not found maintain raw API contract`() {
        `when`(service.list("A","name","real",0,20)).thenReturn(MailReplyDraftList(emptyList(),8,0,20))
        mvc.perform(get("/api/mail/mailbox/drafts?search=name&accountScope=real").session(session("A")))
            .andExpect(status().isOk).andExpect(jsonPath("$.total").value(8)).andExpect(jsonPath("$.items").isArray)
        `when`(service.summaries("A",listOf(11L,12L),null)).thenReturn(MailReplyDraftSummaries(listOf(MailReplyDraftSummary(11,2)),8))
        mvc.perform(get("/api/mail/mailbox/drafts/summaries?contactIds=11,12").session(session("A")))
            .andExpect(status().isOk).andExpect(jsonPath("$.total").value(8))
        `when`(service.get("B",4)).thenThrow(MailReplyDraftException(HttpStatus.NOT_FOUND,"NOT_FOUND","missing"))
        mvc.perform(get("/api/mail/mailbox/drafts/4").session(session("B"))).andExpect(status().isNotFound)
    }
}
