package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import java.time.LocalDateTime
import java.util.Optional

class MailReplyDraftServiceTest {
    private val repository = mock(MailReplyDraftRepository::class.java)
    private val contacts = mock(ExpertContactRepository::class.java)
    private val inbound = mock(InboundMailProcessingRepository::class.java)
    private val accounts = mock(MailSenderAccountRepository::class.java)
    private val attachments = mock(OutboundAttachmentService::class.java)
    private val service = MailReplyDraftService(repository,contacts,inbound,accounts,attachments)
    private val target = MailReplyDraftTarget(11,MailReplyDraftKind.OUTBOUND)
    private val now = LocalDateTime.of(2026,10,8,12,0)
    private fun row(version: Long = 1, state: MailReplyDraftState = MailReplyDraftState.ACTIVE, content: MailReplyDraftContent = MailReplyDraftContent()) =
        MailReplyDraftRow(10,"A",target,version,state,if (state == MailReplyDraftState.ACTIVE) content.subject else null,
            if (state == MailReplyDraftState.ACTIVE) content.html else null,if (state == MailReplyDraftState.ACTIVE) content.text else null,
            if (state == MailReplyDraftState.ACTIVE) MailReplyDraftContextCodec.serialize(content.context) else null,null,null,now,now)
    private fun failure(status: HttpStatus, action: () -> Unit): MailReplyDraftException = assertThrows(MailReplyDraftException::class.java,action).also { assertEquals(status,it.status) }
    private fun existing(value: MailReplyDraftRow) { `when`(repository.findTarget("A",target)).thenReturn(value); `when`(contacts.existsById(11)).thenReturn(true) }

    @Test fun `owner absence and foreign ids do not expose drafts`() {
        failure(HttpStatus.UNAUTHORIZED) { service.get("",10) }
        failure(HttpStatus.NOT_FOUND) { service.get("B",10) }
        verifyNoInteractions(contacts,inbound,accounts,attachments)
    }
    @Test fun `invalid inbound and outbound targets fail before writes`() {
        failure(HttpStatus.UNPROCESSABLE_ENTITY) { service.getTarget("A",target.copy(processingId=1)) }
        failure(HttpStatus.UNPROCESSABLE_ENTITY) { service.getTarget("A",target.copy(kind=MailReplyDraftKind.INBOUND)) }
        `when`(contacts.existsById(11)).thenReturn(true)
        val t = MailReplyDraftTarget(11,MailReplyDraftKind.INBOUND,7,"real")
        `when`(inbound.findById(7)).thenReturn(Optional.of(InboundMailProcessing(senderAccountCode="other",imapUid=1,messageId=null,fromEmail="expert@test",subject=null,receivedAt=now,processStatus="MANUAL_REVIEW",processReason="TEST",expertContactId=11)))
        failure(HttpStatus.UNPROCESSABLE_ENTITY) { service.save("A",t,MailReplyDraftSaveRequest(0,MailReplyDraftContent())) }
        verify(repository,never()).insert(anyString(),anyTarget(),anyContent(),anyString(),anyTime())
    }
    @Test fun `closed drafts remain readable and ordinary saves never resurrect them`() {
        val closed = row(3,MailReplyDraftState.DISCARDED)
        existing(closed)
        `when`(repository.findOwned("A",10)).thenReturn(closed)
        assertNull(service.get("A",10).content)
        val stale = failure(HttpStatus.CONFLICT) { service.save("A",target,MailReplyDraftSaveRequest(2,MailReplyDraftContent())) }
        assertEquals(3L,stale.currentVersion)
        assertEquals("DRAFT_CLOSED",failure(HttpStatus.CONFLICT) { service.save("A",target,MailReplyDraftSaveRequest(3,MailReplyDraftContent())) }.code)
        assertEquals(MailReplyDraftState.DISCARDED,service.discard("A",10,3).state)
        verifyNoInteractions(attachments)
    }
    @Test fun `limits validate Unicode aggregate bytes subject and schema without partial writes`() {
        existing(row())
        failure(HttpStatus.UNPROCESSABLE_ENTITY) { service.save("A",target,MailReplyDraftSaveRequest(1,MailReplyDraftContent(subject="s".repeat(256)))) }
        failure(HttpStatus.PAYLOAD_TOO_LARGE) { service.save("A",target,MailReplyDraftSaveRequest(1,MailReplyDraftContent(text="界".repeat(350000)))) }
        failure(HttpStatus.UNPROCESSABLE_ENTITY) { service.save("A",target,MailReplyDraftSaveRequest(1,MailReplyDraftContent(context=MailReplyDraftContext(schemaVersion=2)))) }
        verify(repository,never()).save(anyString(),anyLong(),anyLong(),anyState(),anyContent(),anyString(),anyTime())
    }
    @Test fun `typed meeting rag rich text and followup roundtrip excludes unknown fields`() {
        val context = MailReplyDraftContext(qa=MailReplyDraftQa(listOf("fact.1"),"fp","原文"),requestId="5a7c7ea7-c426-458a-914c-93e477d3baf6",
            meeting=MailReplyDraftMeeting(MailReplyDraftMeetingInput("Asia/Shanghai","2026-10-08T12:00","2026-10-08T13:00","https://zoom.test"),
                MailReplyDraftMeetingPreview("<b>会议</b>","会议",MailReplyDraftCalendarAttachment("invite.ics","text/calendar","BEGIN:VCALENDAR",12,"a","b"),"start","end","local","china",60),"<b>block</b>","block","ready",3),
            meetingAccountCode="real",followUpAnchorMailRecordId=8)
        assertEquals(context,MailReplyDraftContextCodec.parse(MailReplyDraftContextCodec.serialize(context)))
        failure(HttpStatus.UNPROCESSABLE_ENTITY) { MailReplyDraftContextCodec.parse("{\"schemaVersion\":1,\"downloadUrl\":\"secret\"}") }
        failure(HttpStatus.UNPROCESSABLE_ENTITY) { MailReplyDraftContextCodec.parse("{\"schemaVersion\":1.5}") }
    }
    @Test fun `foreign attachment rejected while missing restored attachment keeps body`() {
        val item = MailReplyDraftAttachmentItem("k","ready","id","f","text/plain",1,"a")
        val content = MailReplyDraftContent(html="<b>原文</b>",text="原文",context=MailReplyDraftContext(outboundAttachmentDraft=MailReplyDraftAttachments(items=listOf(item))))
        existing(row())
        `when`(attachments.loadSnapshots(11,listOf("id"),"A")).thenThrow(OutboundAttachmentException.notFound("missing"))
        failure(HttpStatus.UNPROCESSABLE_ENTITY) { service.save("A",target,MailReplyDraftSaveRequest(1,content)) }
        `when`(repository.findOwned("A",10)).thenReturn(row(content=content))
        val restored = service.get("A",10)
        assertEquals("原文",restored.content!!.text)
        assertEquals("failed",restored.content!!.context.outboundAttachmentDraft!!.items.single().state)
        assertFalse(restored.attachmentsSendable)
    }
    @Test fun `missing old contact is readable and discardable but not saveable`() {
        val old = row().copy(contactExists=false,updatedAt=now.minusYears(2))
        existing(old); `when`(contacts.existsById(11)).thenReturn(false); `when`(repository.findOwned("A",10)).thenReturn(old)
        assertFalse(service.getTarget("A",target).contactExists)
        failure(HttpStatus.UNPROCESSABLE_ENTITY) { service.save("A",target,MailReplyDraftSaveRequest(1,MailReplyDraftContent())) }
        `when`(repository.discard(eq("A") ?: "A",eq(10L),eq(1L),anyTime())).thenReturn(true)
        assertEquals(2L,service.discard("A",10,1).version)
        verify(contacts,never()).deleteById(anyLong())
    }
    @Test fun `empty save stays active preserves created time and attempt binding`() {
        val old = row(content=MailReplyDraftContent(text="previous")).copy(sendAttemptId=71,sendVersion=1,sendAttemptStatus="SENDING")
        existing(old)
        `when`(repository.save(eq("A") ?: "A",eq(10L),eq(1L),eq(MailReplyDraftState.ACTIVE) ?: MailReplyDraftState.ACTIVE,anyContent(),anyString(),anyTime())).thenReturn(true)
        val saved = service.save("A",target,MailReplyDraftSaveRequest(1,MailReplyDraftContent(subject="😀".repeat(255))))
        assertEquals(2L,saved.version); assertEquals("",saved.content!!.text); assertEquals(now,saved.createdAt)
        assertEquals(71L,saved.sendAttemptId); assertEquals(1L,saved.sendVersion)
        verifyNoInteractions(inbound,accounts,attachments)
    }
    @Test fun `exact aggregate byte boundary accepts and one byte over rejects`() {
        existing(row())
        `when`(repository.save(eq("A") ?: "A",eq(10L),eq(1L),eq(MailReplyDraftState.ACTIVE) ?: MailReplyDraftState.ACTIVE,anyContent(),anyString(),anyTime())).thenReturn(true)
        val overhead = MailReplyDraftContextCodec.serialize(MailReplyDraftContext()).toByteArray(Charsets.UTF_8).size
        val text = "x".repeat(MAIL_REPLY_DRAFT_MAX_BYTES - overhead - 3) + "界"
        assertEquals(text,service.save("A",target,MailReplyDraftSaveRequest(1,MailReplyDraftContent(text=text))).content!!.text)
        failure(HttpStatus.PAYLOAD_TOO_LARGE) { service.save("A",target,MailReplyDraftSaveRequest(1,MailReplyDraftContent(text=text+"x"))) }
        verify(repository,times(1)).save(eq("A") ?: "A",eq(10L),eq(1L),eq(MailReplyDraftState.ACTIVE) ?: MailReplyDraftState.ACTIVE,anyContent(),anyString(),anyTime())
    }
    @Test fun `uploading context recovers as failed without file access`() {
        existing(row())
        `when`(repository.save(eq("A") ?: "A",eq(10L),eq(1L),eq(MailReplyDraftState.ACTIVE) ?: MailReplyDraftState.ACTIVE,anyContent(),anyString(),anyTime())).thenReturn(true)
        val content = MailReplyDraftContent(context=MailReplyDraftContext(outboundAttachmentDraft=MailReplyDraftAttachments(3,listOf(MailReplyDraftAttachmentItem("upload-1","uploading",filename="unfinished.bin")))))
        val item = service.save("A",target,MailReplyDraftSaveRequest(1,content)).content!!.context.outboundAttachmentDraft!!.items.single()
        assertEquals("failed",item.state); assertEquals("上传未完成，请重新选择文件",item.error)
        verifyNoInteractions(attachments)
    }
    @Test fun `summaries deduplicate ids and reject excess and paging limits`() {
        `when`(repository.summaries("A",null,listOf(1L))).thenReturn(MailReplyDraftSummaries(emptyList(),0))
        service.summaries("A",listOf(1,1),null)
        verify(repository).summaries("A",null,listOf(1L))
        failure(HttpStatus.UNPROCESSABLE_ENTITY) { service.summaries("A",(1L..101L).toList(),null) }
        failure(HttpStatus.UNPROCESSABLE_ENTITY) { service.list("A",null,null,0,101) }
    }
    private fun anyTarget(): MailReplyDraftTarget = org.mockito.ArgumentMatchers.any(MailReplyDraftTarget::class.java) ?: target
    private fun anyContent(): MailReplyDraftContent = org.mockito.ArgumentMatchers.any(MailReplyDraftContent::class.java) ?: MailReplyDraftContent()
    private fun anyState(): MailReplyDraftState = org.mockito.ArgumentMatchers.any(MailReplyDraftState::class.java) ?: MailReplyDraftState.ACTIVE
    private fun anyTime(): LocalDateTime = org.mockito.ArgumentMatchers.any(LocalDateTime::class.java) ?: now
}
