package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.llm.service.*
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.DelayedManualReplyRepository
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object DelayedWorkerFixture {
    val input = PendingManualRichReplyRequest("sender", "Original", "<p>Original</p>", "Original", "admin",
        qaRuleIds = listOf(9, 4), suggestedRuleIds = listOf(4, 9), edited = true, freeTextPreview = "adopted raw",
        trustReplyAssembly = TrustReplyAssembleRequest(TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100),
            "source-v", "evidence-v", emptyList(), listOf(9, 4),
            listOf(TrustReplyRequestFactSelection("r1", listOf(9)), TrustReplyRequestFactSelection("r2", listOf(4))),
            TrustReplyFrameSnapshot(TrustReplyFrameSelection(1, 2, 3, 4), "frame-v"),
            listOf(TrustReplyFinalParagraphRequest("topic", listOf("f9", "f4", "op1"), "original paragraph")),
            listOf(PlanFact("op1", "operator topic", "operator input", null, false, true))),
        meeting = MeetingInput(zoneId = "Asia/Shanghai", startLocal = "2027-01-01T10:00", endLocal = "2027-01-01T11:00",
            zoomUrl = "https://zoom.us/j/123?pwd=original", generatedAt = "2026-11-05T01:00:00Z"),
        previewAttachmentSha256 = "a".repeat(64), attachmentIds = listOf("00000000-0000-0000-0000-000000000002", "00000000-0000-0000-0000-000000000001"))
    val contact = ExpertContact(id = 1, campaignId = 1, orcidId = "orcid", expertEmail = "expert@example.test", expertName = "Expert", currentStatus = "WAITING_REPLY")
    val account = MailSenderAccount(id = 1, accountCode = "sender", senderEmail = "sender@example.test", senderName = "Sender",
        senderTitle = "", senderDisplayName = "", teamName = "", countryName = "",
        smtpHost = "127.0.0.1", smtpPort = 2525, smtpUsername = "unused", smtpPassword = "secret",
        imapHost = "127.0.0.1", imapPort = 143, imapUsername = "unused", imapPassword = "secret")
    val inbound = InboundMailProcessing(id = 100, senderAccountCode = "sender", imapUid = 1, messageId = "<in@test>",
        fromEmail = contact.expertEmail, subject = "Inbound", body = "Question", receivedAt = LocalDateTime.of(2026, 11, 5, 0, 0),
        processStatus = "MANUAL_REVIEW", processReason = "MANUAL", expertContactId = 1)
    fun snapshot(original: PendingManualRichReplyRequest? = input) = DelayedManualReplySnapshot(1,
        DelayedSendPayloadSnapshot("orcid", 1, 100, "sender", "expert@example.test", "Frozen", "Frozen text", "<p>Frozen text</p>",
            "<in@test>", emptyList(), null, null, null, null, null, emptyList()),
        DelayedReplySourceSnapshot(null, null, null, "<in@test>", 100, "sender", "expert@example.test"),
        DelayedLegacyRestore(100, "Original", "<p>Original</p>", "Original", emptyList(), emptyList(), null, null, null, null, emptyList(), original),
        DelayedReplyEvidenceSnapshot(false, emptyList(), emptyList(), false, emptyList(), "", null, null, null, null, "admin"),
        DelayedReplyConfirmationSnapshot(false, false, null, "a".repeat(64)))
    fun row(snapshot: DelayedManualReplySnapshot = snapshot(), status: DelayedManualReplyStatus = DelayedManualReplyStatus.WAITING) =
        DelayedManualReplyRow(1, 10, 1, "sender", "admin", DelayedManualReplyOriginKind.LEGACY, null, null, 1, status,
            60000, null, null, "Frozen", "Frozen text", DelayedManualReplySnapshotCodec.encode(snapshot), null, 0, 0)

    class Runtime(overrides: Map<Class<*>, Any> = emptyMap()) {
        val dependencies = mutableMapOf<Class<*>, Any>()
        val pending: PendingMailOperationService
        init {
            val constructor = PendingMailOperationService::class.java.declaredConstructors.single {
                it.parameterTypes.last().name != "kotlin.jvm.internal.DefaultConstructorMarker"
            }
            val args = constructor.parameterTypes.map { type -> (overrides[type] ?: if (type == com.weibo.talentintroduction.config.ManualReplyDelayProperties::class.java) com.weibo.talentintroduction.config.ManualReplyDelayProperties() else Mockito.mock(type)).also { dependencies[type] = it } }
            pending = constructor.newInstance(*args.toTypedArray()) as PendingMailOperationService
            Mockito.`when`(dependency(ExpertContactRepository::class.java).findById(1L)).thenReturn(Optional.of(contact))
            Mockito.`when`(dependency(InboundMailProcessingRepository::class.java).findById(100L)).thenReturn(Optional.of(inbound))
            Mockito.`when`(dependency(MailSenderAccountService::class.java).getManualSendAccount("sender")).thenReturn(account)
        }
        fun <T> dependency(type: Class<T>): T = type.cast(dependencies.getValue(type))
    }
}

class DelayedManualReplyWorkerTest {
    @Test fun `runtime smoke dispatches restored bytes to loopback SMTP and forwards original token`() {
        smtpSmoke(disconnect = false, failCompletion = false)
    }

    @Test fun `runtime smoke DATA disconnect and failed completion converge unknown without redelivery`() {
        smtpSmoke(disconnect = true, failCompletion = false)
        smtpSmoke(disconnect = false, failCompletion = true)
    }

    private fun smtpSmoke(disconnect: Boolean, failCompletion: Boolean) {
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            val received = java.util.concurrent.atomic.AtomicReference("")
            val serverFailure = java.util.concurrent.atomic.AtomicReference<Throwable>()
            val smtp = kotlin.concurrent.thread(isDaemon = true, name = "isolated-delayed-smtp") {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        val writer = socket.getOutputStream().bufferedWriter()
                        fun reply(line: String) { writer.write("$line\r\n"); writer.flush() }
                        reply("220 localhost isolated")
                        while (true) {
                            val command = reader.readLine() ?: break
                            when {
                                command.startsWith("EHLO") || command.startsWith("HELO") -> reply("250 localhost")
                                command.startsWith("MAIL") || command.startsWith("RCPT") -> reply("250 OK")
                                command == "DATA" -> {
                                    reply("354 end with dot")
                                    val body = StringBuilder()
                                    while (true) {
                                        val line = reader.readLine() ?: break
                                        if (line == ".") break
                                        body.append(line).append("\r\n")
                                    }
                                    received.set(body.toString())
                                    if (disconnect) break else reply("250 accepted")
                                }
                                command == "QUIT" -> { reply("221 bye"); break }
                                else -> reply("250 OK")
                            }
                        }
                    }
                } catch (ex: Throwable) { serverFailure.set(ex) }
            }
            val sender = org.springframework.mail.javamail.JavaMailSenderImpl().apply {
                host = "127.0.0.1"; port = server.localPort
                javaMailProperties["mail.smtp.connectiontimeout"] = "5000"
                javaMailProperties["mail.smtp.timeout"] = "5000"
                javaMailProperties["mail.smtp.writetimeout"] = "5000"
            }
            val factory = Mockito.mock(SmtpSenderFactory::class.java)
            Mockito.`when`(factory.getSender(DelayedWorkerFixture.account)).thenReturn(sender)
            val content = Mockito.mock(MailContentService::class.java)
            Mockito.`when`(content.stripOpenTrackingImages("<p>Frozen text</p>")).thenReturn("<p>Frozen text</p>")
            val delivery = SmtpMailDeliveryService(factory, Mockito.mock(UnsubscribeTokenService::class.java), content,
                Mockito.mock(EmailSuppressionService::class.java), Mockito.mock(MailOpenTrackingService::class.java))
            val attempts = Mockito.mock(ManualReplySendAttemptService::class.java)
            val runtime = DelayedWorkerFixture.Runtime(mapOf(MailDeliveryService::class.java to delivery,
                ManualReplySendAttemptService::class.java to attempts))
            // Deserialize a historical v1 payload: original-input absence must not impede delivery.
            fun sha(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val ics = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:frozen@test\r\nDTSTART:20270101T020000Z\r\nDTEND:20270101T030000Z\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
            val calendar = CalendarAttachmentSnapshot(1, "frozen.ics", "text/calendar; charset=UTF-8", ics, sha(ics.toByteArray()), "c".repeat(64))
            val files = listOf("second", "first").mapIndexed { index, text ->
                val bytes = text.toByteArray()
                OutboundMailFile(OutboundAttachmentSnapshot(1, "00000000-0000-0000-0000-00000000000${index + 1}",
                    "$text.txt", "text/plain", bytes.size.toLong(), sha(bytes)), bytes)
            }
            val fileSet = OutboundAttachmentFileSet(files, files.map { it.snapshot })
            Mockito.`when`(runtime.dependency(OutboundAttachmentService::class.java).resolveForSend(1, files.map { it.snapshot.id }, "admin")).thenReturn(fileSet)
            val old = DelayedWorkerFixture.snapshot(null)
            val snapshot = DelayedManualReplySnapshotCodec.decode(DelayedManualReplySnapshotCodec.encode(old.copy(
                payload = old.payload.copy(calendarAttachment = calendar, meetingEvent = DelayedMeetingEventSnapshot(
                    java.time.Instant.parse("2027-01-01T02:00:00Z").toEpochMilli(),
                    java.time.Instant.parse("2027-01-01T03:00:00Z").toEpochMilli(), "https://zoom.us/j/123"),
                    outboundAttachments = fileSet.snapshots))))
            val row = DelayedWorkerFixture.row(snapshot)
            val prepared = runtime.pending.rehydrateDelayed(snapshot, "admin")
            val fingerprint = Mockito.mock(ManualReplySendAttemptService::class.java, Mockito.CALLS_REAL_METHODS).computeFingerprint(prepared.payload)
            Mockito.`when`(attempts.computeFingerprint(prepared.payload)).thenReturn(fingerprint)
            Mockito.doReturn(MailSendAttempt(10, "orcid", fingerprint.shortKey, "sender", "<smoke@test>", "PREPARED",
                "expert@example.test", "Frozen", fingerprint.fullHex)).`when`(attempts).lockDelayedAttempt(10)
            val ref = DelayedDispatchRef(1, 2, "smoke-token")
            val settled = CountDownLatch(1)
            val state = java.util.concurrent.atomic.AtomicReference("WAITING")
            Mockito.doAnswer {
                if (failCompletion) throw IllegalStateException("isolated completion failure")
                state.set("SENT"); settled.countDown(); 99L
            }.`when`(attempts).finalizeDelayedSuccess(prepared.payload, 10, "<smoke@test>", ref)
            Mockito.doAnswer { state.set("UNKNOWN"); settled.countDown(); 99L }.`when`(attempts).finalizeDelayedFailure(
                anyValue(), Mockito.eq(10L), eqValue("<smoke@test>"),
                eqValue("DELIVERY_UNKNOWN"), Mockito.any(), eqValue(ref))
            val repository = Mockito.mock(DelayedManualReplyRepository::class.java)
            val lifecycle = Mockito.mock(DelayedManualReplyService::class.java)
            val transactions = Mockito.mock(PlatformTransactionManager::class.java)
            Mockito.`when`(transactions.getTransaction(Mockito.any())).thenReturn(SimpleTransactionStatus())
            Mockito.`when`(repository.currentTimeMs()).thenReturn(60000)
            Mockito.`when`(repository.scanDueIds(60000)).thenAnswer { if (state.get() == "WAITING") listOf(1L) else emptyList<Long>() }
            Mockito.`when`(repository.findById(1)).thenReturn(row)
            Mockito.`when`(lifecycle.claim(1)).thenAnswer {
                if (state.compareAndSet("WAITING", "SENDING")) DelayedClaimRow(row.copy(status = DelayedManualReplyStatus.SENDING, revision = 2), ref) else null
            }
            val worker = DelayedManualReplyWorker(repository, lifecycle, runtime.pending, attempts, transactions)
            val competingWorker = DelayedManualReplyWorker(repository, lifecycle, runtime.pending, attempts, transactions)
            try {
                worker.tick()
                competingWorker.tick()
                assertTrue(settled.await(10, TimeUnit.SECONDS))
            } finally { worker.close(); competingWorker.close() }
            smtp.join(5000)
            assertFalse(smtp.isAlive)
            assertNull(serverFailure.get())
            assertTrue(received.get().contains("Message-ID: <smoke@test>"))
            assertTrue(received.get().contains("Frozen text"))
            assertTrue(received.get().contains("<p>Frozen text</p>"))
            val message = javax.mail.internet.MimeMessage(javax.mail.Session.getInstance(java.util.Properties()),
                received.get().byteInputStream())
            assertEquals("<in@test>", message.getHeader("In-Reply-To", null))
            val mixed = message.content as javax.mail.Multipart
            assertEquals(4, mixed.count)
            assertEquals(ics, mixed.getBodyPart(1).inputStream.bufferedReader().readText())
            assertEquals("second.txt", mixed.getBodyPart(2).fileName)
            assertEquals("first.txt", mixed.getBodyPart(3).fileName)
            assertArrayEquals("second".toByteArray(), mixed.getBodyPart(2).inputStream.readBytes())
            assertArrayEquals("first".toByteArray(), mixed.getBodyPart(3).inputStream.readBytes())
            assertEquals(if (disconnect || failCompletion) "UNKNOWN" else "SENT", state.get())
            worker.recover()
            worker.dispatchDue()
            Mockito.verify(factory, Mockito.times(1)).getSender(DelayedWorkerFixture.account)
            println("DELAYED_RUNTIME_SMOKE loopback=true originalInput=absent disconnect=$disconnect completionFailure=$failCompletion result=${state.get()} smtp=1 token=${ref.dispatchToken}")
        }
    }

    @Test fun `original request round trips independently of frozen delivery and historical JSON remains readable`() {
        val original = DelayedWorkerFixture.snapshot()
        val frozenAssembly = DelayedReplyAssemblySnapshot(TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100),
            emptyList(), mapOf("r1" to "Frozen archive paragraph"), null)
        val snapshot = original.copy(evidence = original.evidence.copy(assembly = frozenAssembly))
        val encoded = DelayedManualReplySnapshotCodec.encode(snapshot)
        assertEquals(snapshot, DelayedManualReplySnapshotCodec.decode(encoded))
        val restored = (DelayedManualReplySnapshotCodec.decode(encoded).restore as DelayedLegacyRestore).originalInput!!
        assertEquals("Asia/Shanghai", restored.meeting!!.zoneId)
        assertEquals("2027-01-01T10:00", restored.meeting!!.startLocal)
        assertEquals(listOf(9L, 4L), restored.trustReplyAssembly!!.requestedFactIds)
        assertEquals("frame-v", restored.trustReplyAssembly!!.frameSnapshot!!.version)
        assertEquals(DelayedWorkerFixture.input.attachmentIds, restored.attachmentIds)
        val tree = jacksonObjectMapper().readTree(encoded)
        (tree["restore"] as com.fasterxml.jackson.databind.node.ObjectNode).remove("originalInput")
        val old = DelayedManualReplySnapshotCodec.decode(tree.toString())
        assertNull((old.restore as DelayedLegacyRestore).originalInput)
        assertEquals(snapshot.payload, old.payload)
        val runtime = DelayedWorkerFixture.Runtime()
        val fresh = runtime.pending.rehydrateDelayed(snapshot, "admin")
        val historical = runtime.pending.rehydrateDelayed(old, "admin")
        assertNull(historical.evidence.verifiedAssembly)
        assertEquals(frozenAssembly, historical.evidence.frozenAssembly)
        val attempts = Mockito.mock(ManualReplySendAttemptService::class.java, Mockito.CALLS_REAL_METHODS)
        assertEquals(attempts.computeFingerprint(fresh.payload).fullHex, attempts.computeFingerprint(historical.payload).fullHex)
        assertEquals("Frozen text", historical.finalTextBody)
        assertEquals("<p>Frozen text</p>", historical.finalHtmlBody)
        Mockito.verifyNoInteractions(runtime.dependency(TrustReplyWorkbenchService::class.java), runtime.dependency(MeetingConfirmationService::class.java))
    }

    @Test fun `RAG original adoption metadata is restore only`() {
        val input = DelayedWorkerFixture.input.copy(trustReplyAssembly = null, qaRuleIds = null,
            ragFactCodes = listOf("fact-b", "fact-a"), ragCorpusFingerprint = "corpus", templateTextBody = "raw template", templateHtmlBody = "<p>raw template</p>")
        val decoded = DelayedManualReplySnapshotCodec.decode(DelayedManualReplySnapshotCodec.encode(DelayedWorkerFixture.snapshot(input)))
        assertEquals(input, (decoded.restore as DelayedLegacyRestore).originalInput)
        assertTrue(decoded.evidence.ragFactCodes.isEmpty())
    }

    @Test fun `unknown schema unknown original fields and oversized snapshots fail closed`() {
        val json = DelayedManualReplySnapshotCodec.encode(DelayedWorkerFixture.snapshot())
        assertThrows(DelayedManualReplySnapshotException::class.java) { DelayedManualReplySnapshotCodec.decode(json.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":99")) }
        val node = jacksonObjectMapper().readTree(json)
        (node["restore"]["originalInput"] as com.fasterxml.jackson.databind.node.ObjectNode).put("smtpPassword", "secret")
        assertThrows(DelayedManualReplySnapshotException::class.java) { DelayedManualReplySnapshotCodec.decode(node.toString()) }
        for ((key, value) in listOf("edited" to "true", "qaRuleIds" to "9")) {
            val typed = jacksonObjectMapper().readTree(json)
            val input = typed["restore"]["originalInput"] as com.fasterxml.jackson.databind.node.ObjectNode
            if (key == "qaRuleIds") input.putArray(key).add(value) else input.put(key, value)
            assertThrows(DelayedManualReplySnapshotException::class.java) { DelayedManualReplySnapshotCodec.decode(typed.toString()) }
        }
        assertThrows(DelayedManualReplySnapshotException::class.java) {
            DelayedManualReplySnapshotCodec.encode(DelayedWorkerFixture.snapshot(DelayedWorkerFixture.input.copy(freeTextPreview = "x".repeat(4 * 1024 * 1024))))
        }
    }

    @Test fun `attachment mismatch prevents SMTP without regenerating content`() {
        val runtime = DelayedWorkerFixture.Runtime()
        val expected = OutboundAttachmentSnapshot(1, "00000000-0000-0000-0000-000000000001", "file.txt", "text/plain", 5, "a".repeat(64))
        val original = DelayedWorkerFixture.snapshot()
        val snapshot = original.copy(payload = original.payload.copy(outboundAttachments = listOf(expected)))
        Mockito.`when`(runtime.dependency(OutboundAttachmentService::class.java).resolveForSend(1, listOf(expected.id), "admin"))
            .thenReturn(OutboundAttachmentFileSet(emptyList(), emptyList()))
        assertEquals("ATTACHMENT_UNAVAILABLE", assertThrows(DelayedManualReplyException::class.java) {
            runtime.pending.rehydrateDelayed(snapshot, "admin")
        }.code)
        Mockito.verifyNoInteractions(runtime.dependency(MailDeliveryService::class.java))
    }

    @Test fun `review context and author permission failures have distinct fixed codes`() {
        val runtime = DelayedWorkerFixture.Runtime()
        val restore = DelayedReviewRestore(8, 2, "admin", null, "Frozen", "<p>Frozen text</p>", "Frozen text",
            "sender", 1, "INBOUND", 100, "00000000-0000-0000-0000-000000000001", "a".repeat(64))
        val snapshot = DelayedWorkerFixture.snapshot().copy(restore = restore)
        val reviews = runtime.dependency(MailReviewDraftService::class.java)
        Mockito.doThrow(MailReviewDraftException(409, "REVIEW_CONTEXT_CHANGED", "private context"))
            .`when`(reviews).lockDelayedReview("admin", restore, null)
        assertEquals("REVIEW_CONTEXT_CHANGED", assertThrows(DelayedManualReplyException::class.java) {
            runtime.pending.rehydrateDelayed(snapshot, "admin")
        }.code)
        Mockito.doThrow(com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException("MAIL_READ_FORBIDDEN", "private author"))
            .`when`(reviews).lockDelayedReview("admin", restore, null)
        assertEquals("ACCESS_CHANGED", assertThrows(DelayedManualReplyException::class.java) {
            runtime.pending.rehydrateDelayed(snapshot, "admin")
        }.code)
        Mockito.verifyNoInteractions(runtime.dependency(MailDeliveryService::class.java))
    }

    @Test fun `runtime permission recipient account source and suppression changes stop before SMTP`() {
        val snapshot = DelayedWorkerFixture.snapshot()
        fun rejects(expected: DelayedReplyPublicError, configure: (DelayedWorkerFixture.Runtime) -> Unit) {
            val runtime = DelayedWorkerFixture.Runtime()
            configure(runtime)
            val ex = assertThrows(DelayedManualReplyException::class.java) { runtime.pending.rehydrateDelayed(snapshot, "admin") }
            assertEquals(expected.code, ex.code)
            Mockito.verifyNoInteractions(runtime.dependency(MailDeliveryService::class.java))
        }
        rejects(DelayedReplyPublicError.ACCESS_CHANGED) { runtime -> Mockito.doThrow(IllegalStateException("private"))
            .`when`(runtime.dependency(ExpertMailPermissionGuard::class.java)).requireFull("admin", 1) }
        rejects(DelayedReplyPublicError.SOURCE_CHANGED) { runtime -> Mockito.`when`(runtime.dependency(ExpertContactRepository::class.java).findById(1L))
            .thenReturn(Optional.of(DelayedWorkerFixture.contact.copy(expertEmail = "changed@example.test"))) }
        rejects(DelayedReplyPublicError.ACCOUNT_UNAVAILABLE) { runtime -> Mockito.`when`(runtime.dependency(MailSenderAccountService::class.java).getManualSendAccount("sender"))
            .thenReturn(DelayedWorkerFixture.account.copy(enabled = false)) }
        rejects(DelayedReplyPublicError.RECIPIENT_SUPPRESSED) { runtime -> Mockito.`when`(runtime.dependency(EmailSuppressionService::class.java).isSuppressed("expert@example.test")).thenReturn(true) }
        rejects(DelayedReplyPublicError.SOURCE_CHANGED) { runtime -> Mockito.`when`(runtime.dependency(InboundMailProcessingRepository::class.java).findById(100L))
            .thenReturn(Optional.of(DelayedWorkerFixture.inbound.copy(messageId = "<changed>"))) }
        val runtime = DelayedWorkerFixture.Runtime()
        assertEquals("ACCESS_CHANGED", assertThrows(DelayedManualReplyException::class.java) { runtime.pending.rehydrateDelayed(snapshot, "") }.code)
    }

    @Test fun `single worker has no backlog and recovery runs while SMTP is blocked`() {
        val repository = Mockito.mock(DelayedManualReplyRepository::class.java)
        val lifecycle = Mockito.mock(DelayedManualReplyService::class.java)
        val pending = Mockito.mock(PendingMailOperationService::class.java)
        val attempts = Mockito.mock(ManualReplySendAttemptService::class.java, Mockito.CALLS_REAL_METHODS)
        val transactions = Mockito.mock(PlatformTransactionManager::class.java)
        Mockito.`when`(transactions.getTransaction(Mockito.any())).thenReturn(SimpleTransactionStatus())
        val snapshot = DelayedWorkerFixture.snapshot()
        val row = DelayedWorkerFixture.row(snapshot)
        val prepared = DelayedWorkerFixture.Runtime().pending.rehydrateDelayed(snapshot, "admin")
        val fingerprint = attempts.computeFingerprint(prepared.payload)
        Mockito.doReturn(MailSendAttempt(10, "orcid", fingerprint.shortKey, "sender", "<send@test>", "PREPARED",
            "expert@example.test", "Frozen", fingerprint.fullHex)).`when`(attempts).lockDelayedAttempt(10)
        Mockito.`when`(pending.rehydrateDelayed(snapshot, "admin", false)).thenReturn(prepared)
        Mockito.`when`(repository.currentTimeMs()).thenReturn(59999L)
        Mockito.`when`(repository.scanDueIds(59999)).thenReturn(emptyList())
        Mockito.`when`(repository.scanDueIds(60000)).thenReturn(listOf(1))
        Mockito.`when`(repository.findById(1)).thenReturn(row)
        val ref = DelayedDispatchRef(1, 2, "token")
        Mockito.`when`(lifecycle.claim(1)).thenReturn(DelayedClaimRow(row.copy(status = DelayedManualReplyStatus.SENDING, revision = 2), ref))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val claim = ManualReplySendAttemptService.ClaimedAttempt(10, "<send@test>", ManualReplySendAttemptService.ClaimResult.CLAIMED)
        Mockito.`when`(pending.deliverPrepared(prepared, claim, ref)).thenAnswer {
            assertEquals("manual-reply-delay", Thread.currentThread().name)
            entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS))
            PendingMailSendResult(1, "sender", "MANUAL_RICH_REPLY", "Frozen", "SENT", "<send@test>")
        }
        val worker = DelayedManualReplyWorker(repository, lifecycle, pending, attempts, transactions)
        try {
            worker.dispatchDue()
            Mockito.verify(lifecycle, Mockito.never()).claim(1)
            Mockito.`when`(repository.currentTimeMs()).thenReturn(60000L)
            worker.tick()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            repeat(20) { worker.tick() }
            worker.recover()
            Mockito.verify(lifecycle).recoverStale(null, null)
            Mockito.verify(lifecycle, Mockito.times(1)).claim(1)
            release.countDown()
        } finally { release.countDown(); worker.close() }
        Mockito.verify(pending, Mockito.times(1)).deliverPrepared(prepared, claim, ref)
    }
}

private fun <T> anyValue(): T = Mockito.any<T>()
private fun <T> eqValue(value: T): T { Mockito.eq(value); return value }
