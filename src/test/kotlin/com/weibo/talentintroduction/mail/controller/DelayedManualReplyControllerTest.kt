package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException
import com.weibo.talentintroduction.mail.repository.DelayedManualReplyRepository
import com.weibo.talentintroduction.mail.service.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DelayedManualReplyControllerTest {
    private val repository = Mockito.mock(DelayedManualReplyRepository::class.java)
    private val lifecycle = Mockito.mock(DelayedManualReplyService::class.java)
    private val worker = Mockito.mock(DelayedManualReplyWorker::class.java)
    private val guard = Mockito.mock(ExpertMailPermissionGuard::class.java)
    private val jdbc = Mockito.mock(NamedParameterJdbcTemplate::class.java)
    private lateinit var mvc: MockMvc
    private val base = "/api/mail/mailbox/conversations/1/delayed-replies/1"
    private val restoreSourceSql = "SELECT id FROM inbound_mail_processing WHERE id=:source AND expert_contact_id=:contact AND sender_account_code=:account"
    private val restoreSourceParams = mapOf("source" to 100L, "contact" to 1L, "account" to "sender")
    private fun session(actor: String = "admin") = MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, actor) }
    private fun row(value: DelayedManualReplyRow) {
        Mockito.`when`(repository.findById(1)).thenReturn(value)
        val error = DelayedReplyPublicError.ofOrInvalid(value.publicErrorCode)
        Mockito.`when`(lifecycle.view(value, null)).thenReturn(DelayedManualReplyView(value.id, value.revision, value.status,
            value.createdAtMs, value.dueAtMs, 100, value.originKind, value.originId, value.originVersion,
            value.status == DelayedManualReplyStatus.WAITING, false, error?.code, error?.message, null))
    }
    @BeforeEach fun setup() {
        mvc = MockMvcBuilders.standaloneSetup(DelayedManualReplyController(repository, lifecycle, worker, guard, jdbc)).build()
        row(DelayedWorkerFixture.row())
    }

    private fun outboundRow(): DelayedManualReplyRow {
        val original = DelayedWorkerFixture.snapshot()
        val request = ConversationManualRichReplyRequest("1b4e28ba-2fa1-11d2-883f-0016d3cca427",
            accountScope = " ", subject = "Original", htmlBody = "<p>Original</p>", textBody = "Original")
        val snapshot = original.copy(
            payload = original.payload.copy(inboundProcessingId = null, sourceAnchor = "MAIL_RECORD:900",
                idempotencyRequestId = request.requestId),
            source = original.source.copy(inboundProcessingId = null, anchorMailRecordId = 900,
                sourceMessageId = "<anchor@test>", smtpInReplyTo = "<anchor@test>"),
            restore = DelayedLegacyRestore(null, request.subject, request.htmlBody, request.textBody!!,
                emptyList(), emptyList(), null, null, null, null, emptyList(), null, request))
        return DelayedWorkerFixture.row(snapshot, DelayedManualReplyStatus.CANCELLED)
    }

    private val outboundSql = """SELECT message_id FROM mail_record WHERE id=:source AND expert_contact_id=:contact
                           AND sender_account_code=:account AND direction='OUTBOUND' AND send_status='SENT'
                           AND sender_account_code <> 'SIMULATOR_NOOP'"""
    private val outboundParams = mapOf("source" to 900L, "contact" to 1L, "account" to "sender")

    @Test fun `outbound restore returns optional original input and separately validated actual anchor`() {
        row(outboundRow())
        Mockito.`when`(jdbc.queryForList(outboundSql, outboundParams)).thenReturn(listOf(mapOf("message_id" to "<anchor@test>")))
        val json = mvc.perform(get("$base/restore").session(session())).andExpect(status().isOk)
            .andExpect(jsonPath("$.sourceKind").value("OUTBOUND"))
            .andExpect(jsonPath("$.anchorMailRecordId").value(900))
            .andExpect(jsonPath("$.inboundProcessingId").doesNotExist())
            .andExpect(jsonPath("$.originalConversationInput.anchorMailRecordId").doesNotExist())
            .andExpect(jsonPath("$.originalConversationInput.accountScope").value(" "))
            .andExpect(jsonPath("$.originalConversationInput.draftRef").doesNotExist())
            .andReturn().response.contentAsString
        for (privateField in listOf("snapshotJson", "dispatchToken", "smtpPassword", "trusted", "/private/"))
            assertFalse(json.contains(privateField))
        Mockito.verify(jdbc).queryForList(outboundSql, outboundParams)
    }

    @Test fun `outbound restore rejects absent reassigned non sent simulator and changed message source`() {
        row(outboundRow())
        // All membership/direction/status/account predicates are part of the actual current-source SQL.
        Mockito.`when`(jdbc.queryForList(outboundSql, outboundParams)).thenReturn(emptyList())
        mvc.perform(get("$base/restore").session(session())).andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.originalConversationInput").doesNotExist())
        Mockito.`when`(jdbc.queryForList(outboundSql, outboundParams)).thenReturn(listOf(mapOf("message_id" to "<new@test>")))
        mvc.perform(get("$base/restore").session(session())).andExpect(status().isUnprocessableEntity)
        Mockito.verify(jdbc, Mockito.times(2)).queryForList(outboundSql, outboundParams)
    }

    @Test fun `outbound restore requires original owner FULL or administrator`() {
        row(outboundRow())
        mvc.perform(get("$base/restore").session(session("user1"))).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.outboundAttachments").doesNotExist())
        row(outboundRow().copy(actorUsername = "user1"))
        Mockito.`when`(jdbc.queryForList(outboundSql, outboundParams)).thenReturn(listOf(mapOf("message_id" to "<anchor@test>")))
        mvc.perform(get("$base/restore").session(session("user1"))).andExpect(status().isOk)
        Mockito.doThrow(MailReadAccessDeniedException("MAIL_WRITE_FORBIDDEN", "无权操作"))
            .`when`(guard).requireFull("user1", 1)
        mvc.perform(get("$base/restore").session(session("user1"))).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.originalConversationInput").doesNotExist())
        mvc.perform(get("$base/restore").session(session())).andExpect(status().isOk)
    }

    @Test fun `public projection has exact fields and no original input credentials or snapshot`() {
        val json = mvc.perform(get(base).session(session())).andExpect(status().isOk)
            .andExpect(jsonPath("$.canCancel").value(true)).andReturn().response.contentAsString
        val node = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(json)
        assertEquals(setOf("id", "revision", "status", "createdAtMs", "dueAtMs", "serverNowMs", "originKind", "originId", "originVersion",
            "canCancel", "canRetry", "publicErrorCode", "publicErrorMessage", "sentMailRecordId"), node.fieldNames().asSequence().toSet())
        assertFalse(json.contains("Original")); assertFalse(json.contains("snapshot")); assertFalse(json.contains("secret"))
    }

    @Test fun `restore returns exact original non UTC input and historical absence returns opaque 422`() {
        row(DelayedWorkerFixture.row(status = DelayedManualReplyStatus.CANCELLED))
        Mockito.`when`(jdbc.queryForList(restoreSourceSql, restoreSourceParams)).thenReturn(listOf(mapOf("id" to 100L)))
        mvc.perform(get("$base/restore").session(session())).andExpect(status().isOk)
            .andExpect(jsonPath("$.expectedCancelledRevision").value(1))
            .andExpect(jsonPath("$.inboundProcessingId").value(100))
            .andExpect(jsonPath("$.sourceKind").value("INBOUND"))
            .andExpect(jsonPath("$.originalInput.meeting.zoneId").value("Asia/Shanghai"))
            .andExpect(jsonPath("$.originalInput.meeting.startLocal").value("2027-01-01T10:00"))
            .andExpect(jsonPath("$.originalInput.trustReplyAssembly.frameSnapshot.version").value("frame-v"))
            .andExpect(jsonPath("$.originalInput.trustReplyAssembly.requestFactSelections[0].factRuleIds[0]").value(9))
        row(DelayedWorkerFixture.row(DelayedWorkerFixture.snapshot(null), DelayedManualReplyStatus.CANCELLED))
        mvc.perform(get("$base/restore").session(session())).andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("SNAPSHOT_INVALID"))
            .andExpect(jsonPath("$.message").value("发送内容无法恢复，请联系管理员"))
            .andExpect(jsonPath("$.originalInput").doesNotExist())
    }

    @Test fun `restore rejects missing or reassigned processing source and mismatched snapshot identity`() {
        row(DelayedWorkerFixture.row(status = DelayedManualReplyStatus.CANCELLED))
        mvc.perform(get("$base/restore").session(session())).andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("SNAPSHOT_INVALID"))
        Mockito.verify(jdbc).queryForList(restoreSourceSql, restoreSourceParams)
        val original = DelayedWorkerFixture.row(status = DelayedManualReplyStatus.CANCELLED)
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        for (sourceId in listOf(0L, 101L)) {
            val json = mapper.readTree(original.snapshotJson)
            (json["restore"] as com.fasterxml.jackson.databind.node.ObjectNode).put("inboundProcessingId", sourceId)
            row(original.copy(snapshotJson = mapper.writeValueAsString(json)))
            mvc.perform(get("$base/restore").session(session())).andExpect(status().isUnprocessableEntity)
                .andExpect(jsonPath("$.inboundProcessingId").doesNotExist())
        }
    }

    @Test fun `READ sees status but cannot restore cancel or retry`() {
        row(DelayedWorkerFixture.row().copy(actorUsername = "user1"))
        Mockito.doThrow(MailReadAccessDeniedException("MAIL_WRITE_FORBIDDEN", "无权操作该专家的邮件资源"))
            .`when`(guard).requireFull("user1", 1)
        mvc.perform(get(base).session(session("user1"))).andExpect(status().isOk)
            .andExpect(jsonPath("$.canCancel").value(false)).andExpect(jsonPath("$.canRetry").value(false))
        mvc.perform(get("$base/restore").session(session("user1"))).andExpect(status().isForbidden)
        for (action in listOf("cancel", "retry")) mvc.perform(post("$base/$action").session(session("user1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1}")).andExpect(status().isForbidden)
        Mockito.verifyNoInteractions(worker)
    }

    @Test fun `foreign contact absent job cross actor and revoked read are rejected`() {
        mvc.perform(get(base.replace("conversations/1", "conversations/2")).session(session())).andExpect(status().isNotFound)
        mvc.perform(get(base).session(session("user1"))).andExpect(status().isForbidden)
        row(DelayedWorkerFixture.row().copy(actorUsername = "user1"))
        Mockito.doThrow(MailReadAccessDeniedException("MAIL_READ_FORBIDDEN", "无权查看"))
            .`when`(guard).requireRead("user1", 1)
        mvc.perform(get(base).session(session("user1"))).andExpect(status().isForbidden)
        Mockito.`when`(repository.findById(1)).thenReturn(null)
        mvc.perform(get(base).session(session())).andExpect(status().isNotFound)
    }

    @Test fun `review state is readable but reader actions and editable restore are forbidden`() {
        row(DelayedWorkerFixture.row().copy(originKind = DelayedManualReplyOriginKind.REVIEW, originId = 8, originVersion = 2))
        mvc.perform(get(base).session(session("user1"))).andExpect(status().isOk).andExpect(jsonPath("$.canCancel").value(false))
        mvc.perform(post("$base/cancel").session(session("user1")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedRevision\":1}")).andExpect(status().isForbidden)
        mvc.perform(get("$base/restore").session(session())).andExpect(status().isConflict)
    }

    @Test fun `expired cancel retains exact public conflict and invalid revisions are 422`() {
        Mockito.`when`(lifecycle.cancel(1, "admin", 1)).thenThrow(DelayedManualReplyException(409, "DELAYED_REPLY_TOO_LATE", "已超过撤回期限"))
        mvc.perform(post("$base/cancel").session(session()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1}"))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("DELAYED_REPLY_TOO_LATE"))
            .andExpect(jsonPath("$.message").value("已超过撤回期限"))
        for (json in listOf("{}", "{\"expectedRevision\":0}", "{\"expectedRevision\":1.5}", "{\"expectedRevision\":9223372036854775808}", "{\"expectedRevision\":\"1\"}", "{\"expectedRevision\":1,\"unexpected\":true}")) {
            mvc.perform(post("$base/cancel").session(session()).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isUnprocessableEntity)
        }
    }

    @Test fun `unknown and permanent failures cannot invoke preflight or retry`() {
        for ((state, code) in listOf(DelayedManualReplyStatus.UNKNOWN to "DELIVERY_UNKNOWN", DelayedManualReplyStatus.FAILED to "PERMANENT_DELIVERY_FAILURE")) {
            row(DelayedWorkerFixture.row(status = state).copy(publicErrorCode = code))
            mvc.perform(post("$base/retry").session(session()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1}"))
                .andExpect(status().isConflict)
        }
        Mockito.verifyNoInteractions(worker)
        Mockito.verify(lifecycle, Mockito.never()).retry(1, "admin", 1)
    }

    @Test fun `ten public errors contain fixed wording not raw diagnostics`() {
        val messages = listOf("发送暂时失败，可安全重试", "邮件被拒收，请修改内容或联系管理员", "发送结果待确认，请勿重复发送",
            "来信内容已更新，请先重新确认审核上下文", "权限已变更，邮件未发送", "回复目标已变更，邮件未发送", "附件不可用，邮件未发送",
            "发件账号不可用，邮件未发送", "当前收件人不可发送", "发送内容无法恢复，请联系管理员")
        assertEquals(messages.toSet(), DelayedReplyPublicError.values().map { it.message }.toSet())
        for (error in DelayedReplyPublicError.values()) {
            row(DelayedWorkerFixture.row(status = DelayedManualReplyStatus.FAILED).copy(publicErrorCode = error.code))
            mvc.perform(get(base).session(session())).andExpect(status().isOk)
                .andExpect(jsonPath("$.publicErrorMessage").value(error.message))
        }
        row(DelayedWorkerFixture.row(status = DelayedManualReplyStatus.FAILED).copy(publicErrorCode = "password=/private/secret"))
        mvc.perform(get(base).session(session())).andExpect(jsonPath("$.publicErrorCode").value("SNAPSHOT_INVALID"))
    }

    @Test fun `deadline error remains exact after worker wins the claim`() {
        row(DelayedWorkerFixture.row(status = DelayedManualReplyStatus.SENDING).copy(revision = 2))
        Mockito.`when`(repository.currentTimeMs()).thenReturn(60000L)
        mvc.perform(post("$base/cancel").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedRevision\":1}")).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("DELAYED_REPLY_TOO_LATE"))
            .andExpect(jsonPath("$.message").value("已超过撤回期限"))
        Mockito.verify(lifecycle, Mockito.never()).cancel(1, "admin", 1)
    }

    @Test fun `list verifies exact source ownership and fixes result limit at twenty`() {
        val path = base.removeSuffix("/1")
        val params = mapOf("contact" to 1L, "origin" to 100L, "kind" to "LEGACY", "actor" to "admin")
        val sourceSql = "SELECT id FROM inbound_mail_processing WHERE id=:origin AND expert_contact_id=:contact"
        mvc.perform(get(path).session(session()).param("originKind", "LEGACY").param("originId", "100"))
            .andExpect(status().isNotFound)
        Mockito.`when`(jdbc.queryForList(sourceSql, params)).thenReturn(listOf(mapOf("id" to 100L)))
        val sql = "SELECT id FROM delayed_manual_reply WHERE expert_contact_id=:contact AND origin_kind=:kind " +
            "AND JSON_EXTRACT(snapshot_json,'$.payload.inboundProcessingId')=:origin AND (:actor='admin' OR actor_username=:actor) ORDER BY id DESC LIMIT 20"
        Mockito.`when`(jdbc.queryForList(sql, params, Long::class.java)).thenReturn(listOf(1L))
        mvc.perform(get(path).session(session()).param("originKind", "LEGACY").param("originId", "100"))
            .andExpect(status().isOk).andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].snapshotJson").doesNotExist())
        Mockito.verify(jdbc).queryForList(sql, params, Long::class.java)
    }

    @Test fun `corrupt restore resource binding fails closed`() {
        row(DelayedWorkerFixture.row(status = DelayedManualReplyStatus.CANCELLED).copy(accountCode = "foreign"))
        mvc.perform(get("$base/restore").session(session())).andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("SNAPSHOT_INVALID"))
    }

    @Test fun `oversized restore is 413 and source list requires exact parameters`() {
        row(DelayedWorkerFixture.row(status = DelayedManualReplyStatus.CANCELLED).copy(snapshotJson = "x".repeat(4 * 1024 * 1024 + 1)))
        mvc.perform(get("$base/restore").session(session())).andExpect(status().isPayloadTooLarge)
        mvc.perform(get(base.removeSuffix("/1")).session(session())).andExpect(status().isUnprocessableEntity)
    }
}
