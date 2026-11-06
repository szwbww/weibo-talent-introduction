package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.mail.controller.*
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.template.service.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mock.web.MockHttpServletRequest

class ConversationTemplateControllerTest {
    private val templates = mock(MailComposeTemplateService::class.java)
    private val operations = mock(PendingMailOperationService::class.java)
    private val guard = mock(ExpertMailPermissionGuard::class.java)
    private val controller = ConversationTemplateController(templates, operations, guard)
    private val request = MockHttpServletRequest().apply { getSession().setAttribute(AuthSessionKeys.USERNAME, "user1") }
    private val template = MailComposeTemplateDetail(1, "HELLO", "Hello", "Stored subject", null, null, null, true,
        listOf(MailComposeTemplateBlockDetail(2, 1, "CUSTOM", null, null, "Stored body")), null, null)
    private val contact = ExpertContact(id=1,campaignId=1,orcidId="fixture",expertEmail="expert@example.invalid",expertName="Daniel",currentStatus="WAITING_REPLY")
    private val account = MailSenderAccount(accountCode="A",senderEmail="sender@example.invalid",senderName="Alex",senderTitle=null,
        senderDisplayName=null,teamName="Team",countryName=null,smtpHost="localhost",smtpPort=2525,smtpUsername="",smtpPassword="",
        imapHost="localhost",imapPort=1143,imapUsername="",imapPassword="")
    private val context = ReplyIdentityContextRequest(ReplyIdentitySourceKind.INBOUND, 1, 101)

    private fun source() {
        `when`(operations.resolveIdentitySource(context)).thenReturn(ReplyIdentitySource(contact, account,
            ReplyIdentityContext(1,101,null,"A",contact.expertEmail,account.senderName),null,null))
        `when`(templates.getById(1)).thenReturn(template)
    }

    @Test fun `list only enabled templates after authorization`() {
        `when`(templates.listAll()).thenReturn(listOf(template, template.copy(id=2, enabled=false)))
        assertEquals(listOf(template), controller.list(1,request))
        val order = inOrder(guard, templates)
        order.verify(guard).requireFull("user1",1)
        order.verify(templates).listAll()
    }

    @Test fun `preview uses stored template and verified inbound account`() {
        source()
        val expected = ComposeTemplatePreviewDraftRequest(subject="Stored subject",
            blocks=listOf(ComposeDraftBlock(1,"CUSTOM",null,"Stored body")), variantIndex=0, contactId=1,senderAccountCode="A")
        val result = ComposeTemplatePreviewDraftResult("Stored subject","Stored body",emptyList(),emptyList())
        `when`(templates.previewDraft(expected)).thenReturn(result)
        assertEquals(result,controller.preview(1,ConversationTemplatePreviewRequest(1,101,"A"),request))
        val order = inOrder(guard,operations,templates)
        order.verify(guard).requireFull("user1",1)
        order.verify(operations).resolveIdentitySource(context)
        order.verify(templates).getById(1)
        order.verify(templates).previewDraft(expected)
    }

    @Test fun `disabled template and foreign account cannot render`() {
        source()
        assertThrows(IllegalArgumentException::class.java) { controller.preview(1,ConversationTemplatePreviewRequest(1,101,"OTHER"),request) }
        verifyNoInteractions(templates)
        `when`(templates.getById(1)).thenReturn(template.copy(enabled=false))
        assertThrows(IllegalArgumentException::class.java) { controller.preview(1,ConversationTemplatePreviewRequest(1,101,"A"),request) }
        assertTrue(mockingDetails(templates).invocations.none { it.method.name == "previewDraft" })
    }
}
