package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.controller.ReplyIdentityController
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.reply.service.*
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ReplyIdentityServiceTest {
    private val snippets = mock(ReplySnippetService::class.java)
    private val variables = mock(MailVariableService::class.java)
    private val templates = mock(MailComposeTemplateService::class.java,CALLS_REAL_METHODS)
    private val service = ReplyIdentityService(snippets,variables,templates)
    private val contact = ExpertContact(id=1,campaignId=1,orcidId="fixture",expertEmail="expert@example.invalid",expertName="Daniel Morgan",currentStatus="WAITING_REPLY")
    private val account = MailSenderAccount(accountCode="A",senderEmail="sender@example.invalid",senderName="Alex Lee",senderTitle=null,
        senderDisplayName=null,teamName="Team",countryName=null,smtpHost="localhost",smtpPort=2525,smtpUsername="",smtpPassword="",
        imapHost="localhost",imapPort=1143,imapUsername="",imapPassword="")
    private val context = ReplyIdentityContext(1,2,null,"A",contact.expertEmail,account.senderName)
    private val source = ReplyIdentitySource(contact,account,context,null,null)
    private val values = mapOf("expertName" to "Daniel Morgan","expertFamilyName" to "Morgan","senderName" to "Alex Lee","teamName" to "Team")
    private val hello = "Dear Dr Morgan,"
    private val signature = "Best regards,\nAlex Lee"
    private fun snapshot(first: String? = "Dear Dr \${expertFamilyName},",last: String? = "Best regards,\n\${senderName}") =
        IdentityDefaultsResponse(first?.let { IdentityDefaultValue(1,null,it) },last?.let { IdentityDefaultValue(2,null,it) },
            listOf(IdentityDefaultOption(1,null,"",0,true)),listOf(IdentityDefaultOption(2,null,"",0,true)),"fingerprint",emptyList())
    @BeforeEach fun setup() {
        `when`(snippets.getIdentityDefaults()).thenReturn(snapshot())
        `when`(variables.buildVariables(account,null,null,false,contact)).thenReturn(values)
    }
    private fun inspect(text: String,html: String = text.replace("\n","<br>"),s: ReplyIdentitySource = source) = service.inspect(s,text,html)
    @Test fun `empty editor has two trusted missing slots and one variable snapshot`() {
        val result = inspect("")
        assertEquals(ReplyIdentityStatus.MISSING,result.salutation.status)
        assertEquals(ReplyIdentityStatus.MISSING,result.signature.status)
        assertTrue(result.salutation.canInsert && result.signature.canInsert)
        assertEquals(hello,result.salutation.expectedText)
        assertEquals(signature,result.signature.expectedText)
        verify(variables,times(1)).resolveExpertProfileFor(contact)
        verify(variables,times(1)).buildVariables(account,null,null,false,contact)
    }
    @Test fun `matching full boundaries are comparison only`() {
        val text = "$hello\n\nThank you for your reply.\n\n$signature"
        val result = inspect(text)
        assertEquals(ReplyIdentityStatus.MATCH,result.salutation.status)
        assertEquals(ReplyIdentityStatus.MATCH,result.signature.status)
        assertFalse(result.salutation.canInsert || result.signature.canInsert)
        assertTrue(service.analyzeFinalBody(source,text,text.replace("\n","<br>")).isEmpty())
    }
    @Test fun `existing wrong names mismatch and never permit insertion`() {
        val result = inspect("Dear Alice,\n\nThank you for your reply.\n\nBest regards,\nBob")
        assertEquals(ReplyIdentityStatus.MISMATCH,result.salutation.status)
        assertEquals(ReplyIdentityStatus.MISMATCH,result.signature.status)
        assertFalse(result.salutation.canInsert || result.signature.canInsert)
        assertTrue(service.analyzeFinalBody(source,"Dear Alice,","Dear Alice,").all { it.severity == SafetySeverity.NORMAL })
    }
    @Test fun `normal prose is missing but bare name is uncertain`() {
        assertTrue(inspect("Thank you for your reply.").signature.canInsert)
        assertEquals(ReplyIdentityStatus.UNCERTAIN,inspect("Thank you for your reply.\n\nBob").signature.status)
    }
    @Test fun `repeated and misplaced salutations are not completion candidates`() {
        assertEquals(ReplyIdentityStatus.MULTIPLE_OR_MISPLACED,inspect("$hello\n\n$hello").salutation.status)
        assertEquals(ReplyIdentityStatus.MULTIPLE_OR_MISPLACED,inspect("Thank you for your reply.\n\n$hello").salutation.status)
    }
    @Test fun `HTML wrong identity cannot be masked by correct text`() {
        val result = inspect("$hello\n\n$signature","Dear Alice,<br><br>Best regards,<br>Bob")
        assertEquals(ReplyIdentityStatus.UNCERTAIN,result.salutation.status)
        assertFalse(result.salutation.canInsert || result.signature.canInsert)
    }
    @Test fun `hidden correct identity does not mask wrong visible text`() {
        val result = inspect("Dear Alice,","<div hidden>$hello</div>Dear Alice,")
        assertEquals(ReplyIdentityStatus.MISMATCH,result.salutation.status)
    }
    @Test fun `untrusted CSS visibility is uncertain`() {
        assertEquals(ReplyIdentityStatus.UNCERTAIN,inspect(hello,"<div class='unknown'>$hello</div>").salutation.status)
    }
    @Test fun `forged quote marker is not a trusted boundary`() {
        assertEquals("UNCERTAIN",inspect("$hello\n\nOn 2026-10-21 10:00, A wrote:\n\n$signature").quoteBoundary.kind)
    }
    @Test fun `real complete outbound quote is excluded in both MIME representations`() {
        val history = "Dear Alice,\n\nOld reply.\n\nBest regards,\nBob"
        val header = "On 2026-10-21 10:00, A wrote:"
        val result = inspect("$hello\n\nThank you for your reply.\n\n$signature\n\n$header\n\n$history",s=source.copy(quotedBody=history,canonicalQuoteHeader=header))
        assertEquals("VERIFIED_TAIL",result.quoteBoundary.kind)
        assertEquals(ReplyIdentityStatus.MATCH,result.signature.status)
    }
    @Test fun `missing defaults and missing used variables cannot insert`() {
        `when`(snippets.getIdentityDefaults()).thenReturn(snapshot(last=null))
        assertEquals(ReplyIdentityStatus.UNCERTAIN,inspect("").signature.status)
        `when`(variables.buildVariables(account,null,null,false,contact)).thenReturn(values + ("expertFamilyName" to ""))
        assertFalse(inspect("").salutation.canInsert)
    }
    @Test fun `literal unrelated name cannot self attest`() {
        `when`(snippets.getIdentityDefaults()).thenReturn(snapshot(first="Dear Alice,"))
        val result = inspect("Dear Alice,")
        assertEquals("UNVERIFIED_LITERAL",result.salutation.reasonCode)
        assertFalse(result.salutation.canInsert)
    }
    @Test fun `unsubscribe body variable is read only uncertainty`() {
        val result = inspect("\${unsubscribeUrl}")
        assertEquals("BODY_VARIABLE_REQUIRES_WRITE",result.salutation.reasonCode)
        verify(variables).buildVariables(account,null,null,false,contact)
    }
    @Test fun `unsaved defaults preview never saves or uses example fallback`() {
        val result = service.previewDefaults(source,IdentityDefaultSelection(1,"Hi \${expertName},"),IdentityDefaultSelection(2,"Best regards,\n\${senderName}"))
        assertEquals("Hi Daniel Morgan,",result.renderedTexts["salutation"])
        verify(snippets).getIdentityDefaults()
        verifyNoMoreInteractions(snippets)
    }
    @Test fun `identity read failure becomes confirmable normal finding`() {
        `when`(snippets.getIdentityDefaults()).thenThrow(IllegalStateException("offline"))
        assertEquals(SafetySeverity.NORMAL,service.analyzeFinalBody(source,"body","body").single().severity)
    }
    @Test fun `source backed tail container excludes historical wrong names only at the tail`() {
        val history = "Dear Alice,\n\nOld reply.\n\nBest regards,\nBob"
        val current = "$hello\n\nThank you for your reply.\n\n$signature"
        val html = current.replace("\n","<br>")+"<blockquote>"+history.replace("\n","<br>")+"</blockquote>"
        val result = inspect("$current\n\n$history",html,source.copy(quotedBody=history))
        assertEquals("VERIFIED_TAIL",result.quoteBoundary.kind)
        assertEquals(ReplyIdentityStatus.MATCH,result.salutation.status)
        assertEquals(ReplyIdentityStatus.MATCH,result.signature.status)
        assertEquals("UNCERTAIN",inspect("$current\n\n$history","<blockquote>$hello</blockquote>",
            source.copy(quotedBody=history)).quoteBoundary.kind)
    }

    @Test fun `fallback cannot supply missing identity evidence and unrelated missing fields do not matter`() {
        `when`(snippets.getIdentityDefaults()).thenReturn(snapshot(first="Dear Dr \${expertFamilyName|Professor},"))
        `when`(variables.buildVariables(account,null,null,false,contact)).thenReturn(values + ("expertFamilyName" to ""))
        assertEquals("MISSING_VARIABLE",inspect("").salutation.reasonCode)
        assertFalse(inspect("").salutation.canInsert)
        `when`(variables.buildVariables(account,null,null,false,contact)).thenReturn(values + ("countryName" to ""))
        assertTrue(inspect("").salutation.canInsert)
    }

    @Test fun `normalization retains case punctuation and full signature while decoding entities`() {
        val body = "$hello\n\nThank you for your reply.\n\n$signature"
        assertEquals(ReplyIdentityStatus.MATCH,inspect(body.replace("Dr ","Dr\u00a0 "),
            body.replace("\n","<br>").replace("Dr ","Dr&nbsp; ")).salutation.status)
        assertEquals(ReplyIdentityStatus.MISMATCH,inspect(body.replace("Morgan","morgan")).salutation.status)
        assertEquals(ReplyIdentityStatus.MISMATCH,inspect(body.replace("Morgan,","Morgan!")).salutation.status)
        assertEquals(ReplyIdentityStatus.MULTIPLE_OR_MISPLACED,
            inspect("$body\n\nThank you for your reply.").signature.status)
    }

    @Test fun `hidden styles and aria cannot attest identity and complex CSS is uncertain`() {
        for (attribute in listOf("aria-hidden='true'","style='display:none'","style='visibility:hidden'")) {
            assertEquals(ReplyIdentityStatus.MISMATCH,inspect("Dear Alice,",
                "<div $attribute>$hello</div>Dear Alice,").salutation.status)
        }
        assertEquals(ReplyIdentityStatus.UNCERTAIN,
            inspect(hello,"<span style='font-size:0'>$hello</span>").salutation.status)
    }

    @Test fun `literal generic and exact name rules support trusted missing suggestions`() {
        for (first in listOf("Dear Professor,","Dear Morgan,","Dear Daniel Morgan,","您好")) {
            `when`(snippets.getIdentityDefaults()).thenReturn(snapshot(first=first,last="Regards,"))
            assertTrue(inspect("").salutation.canInsert,first)
            assertTrue(inspect("").signature.canInsert)
        }
        `when`(snippets.getIdentityDefaults()).thenReturn(snapshot(first="Dear MorganX,"))
        assertFalse(inspect("").salutation.canInsert)
    }
}

/** A separate top-level Surefire selector: real MVC request conversion, controller and global advice. */
class ReplyIdentityControllerTest {
    private val operations = mock(PendingMailOperationService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(ReplyIdentityController(operations, mock(ExpertMailPermissionGuard::class.java)))
        .setControllerAdvice(GlobalExceptionHandler()).build()
    @Test fun `authentication interceptor protects both endpoints`() {
        val protectedMvc = MockMvcBuilders.standaloneSetup(ReplyIdentityController(operations, mock(ExpertMailPermissionGuard::class.java)))
            .setControllerAdvice(GlobalExceptionHandler())
            .addInterceptors(com.weibo.talentintroduction.auth.config.AuthInterceptor(
                mock(com.weibo.talentintroduction.auth.service.AuthService::class.java),com.fasterxml.jackson.databind.ObjectMapper(),
                mock(ExpertMailPermissionGuard::class.java))).build()
        listOf("inspect","defaults-preview").forEach { endpoint ->
            protectedMvc.perform(post("/api/mail/reply-identity/$endpoint").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized)
        }
        verifyNoInteractions(operations)
    }
    @Test fun `mixed source context is HTTP 400 before service access`() {
        mvc.perform(post("/api/mail/reply-identity/inspect").contentType(MediaType.APPLICATION_JSON)
            .content("""{"sourceKind":"CONVERSATION","contactId":1,"inboundProcessingId":2} """))
            .andExpect(status().isBadRequest)
        verifyNoInteractions(operations)
    }
    @Test fun `invalid source kind and oversized body are HTTP 400`() {
        mvc.perform(post("/api/mail/reply-identity/inspect").contentType(MediaType.APPLICATION_JSON)
            .content("""{"sourceKind":"OTHER","contactId":1}""")).andExpect(status().isBadRequest)
        mvc.perform(post("/api/mail/reply-identity/inspect").contentType(MediaType.APPLICATION_JSON)
            .content("""{"sourceKind":"INBOUND","contactId":1,"inboundProcessingId":2,"textBody":"${"a".repeat(100001)}"}"""))
            .andExpect(status().isBadRequest)
        verifyNoInteractions(operations)
    }
    @Test fun `defaults preview rejects oversized unsaved content`() {
        mvc.perform(post("/api/mail/reply-identity/defaults-preview").contentType(MediaType.APPLICATION_JSON)
            .content("""{"sourceKind":"CONVERSATION","contactId":1,"salutation":{"id":1,"content":"${"a".repeat(10001)}"},"signature":{"id":2,"content":"Regards,"}}"""))
            .andExpect(status().isBadRequest)
        verifyNoInteractions(operations)
    }
}

