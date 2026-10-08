package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ExpertInboundNotificationServiceTest {
    private val repository = Mockito.mock(ExpertInboundNotificationRepository::class.java)
    private val inbound = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val contacts = Mockito.mock(ExpertContactRepository::class.java)
    private val mapper = ObjectMapper()
    private val url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=isolated-test-not-a-credential"
    private fun service(webhook: String = url) = ExpertInboundNotificationService(repository, inbound, contacts, MailBodyCleaner(), mapper, webhook)
    private val claim = ExpertInboundNotificationClaim(17, 1, "专家来信\n通知编号：17", 1, "test-token")
    private fun source(body: String = "New reply\n> old quoted message") = InboundMailProcessing(
        id = 1, senderAccountCode = "sender", mailboxOwnerCode = "owner", uidValidity = 2, imapUid = 3,
        messageId = null, fromEmail = "expert@example.test", subject = null, body = body,
        receivedAt = LocalDateTime.of(2026, 10, 8, 12, 0), processStatus = "MANUAL_REVIEW",
        processReason = "GLOBAL_AUTO_REPLY_DISABLED", expertContactId = 11
    )

    @Test fun `settings do not write or probe HTTP and survive service reconstruction`() {
        Mockito.`when`(repository.settings()).thenReturn(ExpertInboundNotificationSetting(true, 4, null))
        val a = service().settings()
        val b = service().settings()
        assertEquals(a, b)
        assertTrue(a.enabled)
        assertTrue(a.configured)
        assertEquals(4L, a.generation)
        Mockito.verify(repository, Mockito.times(2)).settings()
        Mockito.verifyNoMoreInteractions(repository)
    }

    @Test fun `configuration validation rejects alternate targets and permits only safe HTTPS webhook`() {
        val invalid = listOf("", "http://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=x", "https://evil.test/cgi-bin/webhook/send?key=x",
            "https://qyapi.weixin.qq.com.evil.test/cgi-bin/webhook/send?key=x", "https://user@qyapi.weixin.qq.com/cgi-bin/webhook/send?key=x",
            "https://qyapi.weixin.qq.com:444/cgi-bin/webhook/send?key=x", "https://qyapi.weixin.qq.com/other?key=x",
            "https://qyapi.weixin.qq.com/cgi-bin/webhook/send", "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=",
            "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=%0A", "$url#fragment", "$url&key=another")
        invalid.forEach { assertNull(ExpertInboundNotificationService.validWebhook(it), it) }
        assertNotNull(ExpertInboundNotificationService.validWebhook(url))
        assertNotNull(ExpertInboundNotificationService.validWebhook("https://qyapi.weixin.qq.com:443/cgi-bin/webhook/send?key=x"))
    }

    @Test fun `missing configuration forbids enable but permits disable and never sends`() {
        val service = service("")
        assertThrows(ExpertInboundNotificationNotConfiguredException::class.java) { service.setEnabled(true, "admin") }
        Mockito.`when`(repository.setEnabled(false, "admin")).thenReturn(ExpertInboundNotificationSetting())
        assertFalse(service.setEnabled(false, "admin").configured)
        service.tick()
        Mockito.verify(repository, Mockito.never()).claim(Mockito.anyString())
    }

    @Test fun `database failures are not masked as disabled and worker fails closed`() {
        Mockito.`when`(repository.settings()).thenThrow(IllegalStateException("database unavailable"))
        assertThrows(IllegalStateException::class.java) { service().settings() }
        Mockito.`when`(repository.claim(Mockito.anyString())).thenThrow(IllegalStateException("database unavailable"))
        val service = service()
        val http = MockRestServiceServer.bindTo(service.httpClient).build()
        service.tick()
        service.tick()
        http.verify()
        Mockito.verify(repository, Mockito.never()).authorizeSend(Mockito.anyString(), Mockito.any<ExpertInboundNotificationClaim>() ?: claim)
    }

    @Test fun `plain text message cleans history has fallback and bounded Unicode fields`() {
        val service = service()
        val fallback = service.message(17, source(), "")
        assertTrue(fallback.startsWith("专家来信\n通知编号：17"))
        assertTrue(fallback.contains("专家：expert@example.test"))
        assertTrue(fallback.contains("主题：无主题"))
        assertFalse(fallback.contains("old quoted"))
        val emoji = "😀".repeat(250)
        val message = service.message(17, source("a\u0000b\n@all"), emoji)
        assertFalse(message.contains('\u0000'))
        assertFalse(message.contains("@all"))
        val name = message.substringAfter("专家：").substringBefore('\n')
        assertEquals(80, name.codePointCount(0, name.length))
        val long = service.message(17, source(emoji).copy(subject = emoji, senderAccountCode = "中".repeat(200), fromEmail = emoji), emoji)
        assertTrue(long.toByteArray(Charsets.UTF_8).size <= 1800)
        assertFalse(long.last().isHighSurrogate())
        val summary = service.message(17, source(emoji), "Name").substringAfter("摘要：")
        assertEquals(160, summary.codePointCount(0, summary.length))
        val cleaned = service.message(17, source("old").copy(cleanedBody = "latest"), "Name")
        assertTrue(cleaned.endsWith("摘要：latest"))
    }

    @Test fun `HTTP success uses exact text JSON without mentions attachments or credentials`() {
        val service = service()
        val http = MockRestServiceServer.bindTo(service.httpClient).build()
        http.expect(requestTo(url)).andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(content().json("""{"msgtype":"text","text":{"content":"专家来信\n通知编号：17"}}""", true))
            .andRespond(withSuccess("""{"errcode":0,"errmsg":"ok"}""", MediaType.APPLICATION_JSON))
        assertEquals(ExpertInboundNotificationSendResult(true, false, null), service.send(claim))
        http.verify()
    }

    @Test fun `business errors permanent errors malformed responses and redirects never report success`() {
        val cases = listOf(
            Triple(HttpStatus.OK, """{"errcode":45009}""", true),
            Triple(HttpStatus.OK, """{"errcode":40008,"errmsg":"$url"}""", false),
            Triple(HttpStatus.OK, """{"errcode":0.0}""", false),
            Triple(HttpStatus.OK, """{"errcode":4294967296}""", false),
            Triple(HttpStatus.OK, "{}", false), Triple(HttpStatus.OK, "invalid", false),
            Triple(HttpStatus.FOUND, "", false), Triple(HttpStatus.BAD_REQUEST, "", false),
            Triple(HttpStatus.INTERNAL_SERVER_ERROR, "", true), Triple(HttpStatus.TOO_MANY_REQUESTS, "", true)
        )
        for ((status, body, retry) in cases) {
            val service = service()
            val http = MockRestServiceServer.bindTo(service.httpClient).build()
            http.expect(requestTo(url)).andRespond(withStatus(status).body(body).contentType(MediaType.APPLICATION_JSON).location(java.net.URI("https://evil.test")))
            val result = service.send(claim)
            assertFalse(result.success)
            assertEquals(retry, result.retryable)
            assertFalse(result.toString().contains(url))
            http.verify()
        }
    }

    @Test fun `timeout is retryable and diagnostic never includes HTTP exception or URL`() {
        val service = service()
        val http = MockRestServiceServer.bindTo(service.httpClient).build()
        http.expect(requestTo(url)).andRespond { throw SocketTimeoutException(url) }
        assertEquals(ExpertInboundNotificationSendResult(false, true, "HTTP_TIMEOUT"), service.send(claim))
        http.verify()
    }

    @Test fun `worker rechecks authorization and only finishes its claimed token`() {
        val service = service()
        Mockito.`when`(repository.claim(Mockito.anyString())).thenReturn(claim)
        Mockito.`when`(repository.authorizeSend(Mockito.anyString(), Mockito.eq(claim) ?: claim)).thenReturn(false)
        val http = MockRestServiceServer.bindTo(service.httpClient).build()
        service.tick()
        http.verify()
        Mockito.verify(repository, Mockito.never()).finish(Mockito.eq(claim) ?: claim, Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.nullable(String::class.java))
        Mockito.`when`(repository.authorizeSend(Mockito.anyString(), Mockito.eq(claim) ?: claim)).thenReturn(true)
        http.expect(requestTo(url)).andRespond(withSuccess("""{"errcode":0}""", MediaType.APPLICATION_JSON))
        service.tick()
        Mockito.verify(repository).finish(claim, true, false, null)
        http.verify()
    }

    @Test fun `dedicated worker lifecycle neither blocks caller nor uses shared scheduler`() {
        val service = service()
        val called = CountDownLatch(1)
        var thread = ""
        Mockito.`when`(repository.claim(Mockito.anyString())).thenAnswer {
            thread = Thread.currentThread().name
            called.countDown()
            null
        }
        service.start()
        service.start()
        assertTrue(called.await(4, TimeUnit.SECONDS))
        service.stop()
        assertEquals("expert-inbound-notification", thread)
        Mockito.verify(repository).releaseWorker(Mockito.anyString())
        assertFalse(Thread.getAllStackTraces().keys.any { it.isAlive && it.name == "expert-inbound-notification" })
    }

    @Test fun `receipt savepoint exceptions are caught outside the boundary`() {
        Mockito.doThrow(IllegalStateException(url)).`when`(repository).inReceiptSavepoint(Mockito.any<() -> Unit>() ?: {})
        assertDoesNotThrow { service().enqueueBestEffort("owner", 1, 2) }
    }
}
