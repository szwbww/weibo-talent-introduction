package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.auth.config.AuthInterceptor
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.config.*
import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.controller.DiscoveryPaperReprocessController
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryPaperReprocessRepository
import com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.util.concurrent.CancellationException

class DiscoveryPaperReprocessServiceTest {
    @Test fun `failure receipt preserves cause code but excludes arbitrary exception contents`() {
        val result = jacksonObjectMapper().createObjectNode()
        PaperReprocessFailure.record(result, IllegalStateException("secret-token alice@example.com",
            IllegalArgumentException("SOURCE_IDENTITY_PARTIAL")), 12, 34)
        assertEquals("12/34", result.path("failure").path("reference").asText())
        assertEquals("SOURCE_IDENTITY_PARTIAL", result.path("failure").path("causes")[1].path("code").asText())
        assertFalse(result.toString().contains("secret-token"))
        assertFalse(result.toString().contains("alice@example.com"))
    }

    @Test fun `readonly preview and detail expose persisted counts units overlap and proposal semantics without parsing`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val repository = Mockito.mock(DiscoveryPaperReprocessRepository::class.java)
        val archive = Mockito.mock(DiscoveryPaperArchiveService::class.java)
        val analyzer = Mockito.mock(PaperBodyAnalyzer::class.java)
        val settings = Mockito.mock(DiscoveryApolloSettingsService::class.java)
        val progress = Mockito.mock(com.weibo.talentintroduction.task.service.TaskProgressStore::class.java)
        val service = DiscoveryPaperReprocessService(repository, archive, analyzer, mapper, settings, progress)
        try {
            val hash = "a".repeat(64)
            Mockito.`when`(repository.watermark(9001)).thenReturn(ScopeWatermark(9001, "SUCCESS", 121, 130,
                121, 121, 130, "130", hash))
            Mockito.`when`(repository.missingSourcePapers(9001)).thenReturn(3)
            val preview = service.preview(9001)
            assertEquals(121L, preview.recordCount); assertEquals(hash, preview.expectedScopeHash)
            assertEquals(3L, preview.missingSourcePapers); assertNull(preview.activeRun)
            val time = java.time.Instant.parse("2026-11-01T01:02:03Z")
            val run = ReprocessRun(9002, 9001, "request", hash, ReprocessMode.AUDIT,
                ReprocessState.RUNNING, PaperReprocessPhase.BIND, mapper.createObjectNode().put("frozen", true),
                0, null, null, 0, false, time, time, null)
            val summary = ReprocessSummary(121, 121, 0, 0, 121, 121, 0,
                wouldCreate = 2, wouldRepair = 1,
                conflict = 2, missingSourcePapers = 3, totalKnown = true, targetTotalKnown = false,
                qualification = ReprocessQualificationSummary(unavailable = 121))
            Mockito.`when`(repository.find(9002)).thenReturn(run)
            Mockito.`when`(repository.summary(9002)).thenReturn(summary)
            val detail = service.detail(9002)
            assertEquals(summary, detail.summary); assertTrue(detail.totalKnown)
            val json = mapper.valueToTree<JsonNode>(detail)
            assertEquals(2, json.path("summary").path("wouldCreate").asInt())
            assertEquals(1, json.path("summary").path("wouldRepair").asInt())
            assertEquals(0, json.path("summary").path("actualNew").asInt())
            assertEquals(0, json.path("summary").path("actualUpdated").asInt())
            assertEquals(0, json.path("summary").path("actualAdmitted").asInt())
            assertEquals("PAPER", json.path("summary").path("paperUnit").asText())
            assertEquals("TARGET", json.path("summary").path("targetUnit").asText())
            assertTrue(json.path("summary").path("categoriesOverlap").asBoolean())
            assertFalse(json.path("summary").path("targetTotalKnown").asBoolean())
            assertEquals(0, json.path("summary").path("qualification").path("unknown").asInt())
            assertEquals(121, json.path("summary").path("qualification").path("unavailable").asInt())
            Mockito.verifyNoInteractions(archive, analyzer, settings, progress)
        } finally { service.shutdown() }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "paperReprocessIt", matches = "true")
    fun `preview HTTP uses full source descriptor absence including deleted and save error without changing archive`() {
        Paper03RuntimeFixture().use { f ->
            f.seed(121)
            f.db.jdbc.update("UPDATE discovery_paper_archive_attempt SET status='DELETED' WHERE record_id=?", f.records[120].id)
            f.db.jdbc.update("UPDATE discovery_paper_archive_attempt SET status='SAVE_ERROR' WHERE record_id=?", f.records[119].id)
            f.archive.beginRecord(f.sourceId, "OPENALEX", "never-saved", f.mapper.readTree("""{"schemaVersion":1}"""))
            val before = f.db.jdbc.queryForList("SELECT * FROM discovery_paper_archive_record ORDER BY id")
            val attempts = f.db.jdbc.queryForList("SELECT * FROM discovery_paper_archive_attempt ORDER BY id")
            val mvc = MockMvcBuilders.standaloneSetup(f.controller).build()
            mvc.perform(get("/api/expert-discovery/paper-reprocess/preview").param("sourceExecutionId", f.sourceId.toString()))
                .andExpect(status().isOk).andExpect(jsonPath("$.recordCount").value(122))
                .andExpect(jsonPath("$.missingSourcePapers").value(3))
            assertEquals(before, f.db.jdbc.queryForList("SELECT * FROM discovery_paper_archive_record ORDER BY id"))
            assertEquals(attempts, f.db.jdbc.queryForList("SELECT * FROM discovery_paper_archive_attempt ORDER BY id"))
            Mockito.verify(f.analyzer, Mockito.never()).versions()
            Mockito.verifyNoInteractions(f.network, f.bounded, f.settings, f.progress)
        }
    }

    @Test fun `optional page checkpoints cancel qualification and contact scans without altering defaults or versions`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val network = Mockito.mock(RestTemplate::class.java)
        val bounded = Mockito.mock(BoundedHttpExecutor::class.java)
        val pdf = PdfEmailExtractor(network, PlainTextEmailExtractor(), PdfExtractionProperties(), bounded)
        val facts = PaperQualificationEvidenceExtractor()
        val analyzer = PaperBodyAnalyzer(pdf, facts, mapper)
        val bytes = QualificationFixtures.pdf(3, listOf("Alice Smith: alice@fixture.edu", "Alice Smith is a Professor."))
        val input = PaperBodyInput(DiscoveryPaperArchiveService.sha(bytes), ArchiveAttemptKind.PDF_URL, bytes)
        val metadata = mapper.valueToTree<JsonNode>(mapOf("authors" to QualificationFixtures.authors, "pubYear" to 2023))
        val default = analyzer.parse(input, metadata)
        var qualificationPages = 0
        val qualification = facts.extract(bytes, "PDF", QualificationFixtures.authors, 2023, input.hash,
            checkpoint = { qualificationPages++ })
        assertEquals(3, qualificationPages)
        assertEquals(qualification, default.qualification)
        var allPages = 0
        assertEquals(default, analyzer.parse(input, metadata) { allPages++ })
        assertTrue(allPages > qualificationPages, "contact extraction must also invoke the checkpoint")
        for (at in listOf(2, qualificationPages + 1)) for (code in listOf("CANCELLED", "PARSE_BUDGET_EXCEEDED")) {
            var seen = 0
            val failure = assertThrows(PaperParseCheckpointException::class.java) {
                analyzer.parse(input, metadata) { if (++seen == at) throw PaperParseCheckpointException(code) }
            }
            assertEquals(code, failure.code); assertEquals(at, seen)
        }
        for (at in listOf(2, qualificationPages + 1)) {
            var seen = 0
            assertThrows(CancellationException::class.java) { analyzer.parse(input, metadata) {
                if (++seen == at) throw CancellationException("fixture")
            } }
        }
        assertEquals(20261101, analyzer.versions()["pdfLayout"])
        assertEquals(20261004, analyzer.versions()["identityExtraction"])
        assertEquals(default, analyzer.parse(input, metadata))
        val broken = "%PDF-1.4\ninvalid".toByteArray()
        val brokenInput = PaperBodyInput(DiscoveryPaperArchiveService.sha(broken), ArchiveAttemptKind.PDF_URL, broken)
        val ordinaryFailure = analyzer.parse(brokenInput, metadata)
        assertEquals(PaperEvidenceStatus.ERROR, ordinaryFailure.qualification.coverage.status)
        assertTrue("EMAIL_PARSE_ERROR" in ordinaryFailure.reasons)
        assertEquals(ordinaryFailure, analyzer.parse(brokenInput, metadata) {})
        Mockito.verifyNoInteractions(network, bounded)
    }

    @Test fun `nonfirst exact evidence is available while default first remains and leases protect deletion`() {
        ArchiveMysqlFixture.clean()
        ArchiveFixture().use { f ->
            f.enable()
            val bridge = PaperBridgeFixture(f)
            val first = bridge.paper(value = "No correspondence here")
            val record = f.repository.record(first.archiveRecordId)!!
            val later = f.body(record, bridge.text.toByteArray(), ArchiveAttemptKind.EMBEDDED_TEXT, "text/plain")
            val repeated = f.body(record, bridge.text.toByteArray(), ArchiveAttemptKind.EMBEDDED_TEXT, "text/plain")
            val ref = first.copy(attemptId = later.id, bodySha256 = later.blobSha256!!)
            val before = f.repository.record(record.id)
            f.service.analysisLease(record.id).use { baselineLease ->
                assertEquals(listOf(first.bodySha256), baselineLease.bodies.map { b -> b.hash })
            f.service.analysisLease(record.id, listOf(later.id to later.blobSha256!!, repeated.id to repeated.blobSha256!!)).use { lease ->
                assertEquals(2, lease.fileSet.size); assertEquals(1, lease.bodies.size)
                val preview = f.service.deletePreview(listOf(record.id))
                assertTrue("BUSY" in preview.items.single().reasonCodes)
                assertEquals(0, f.service.delete(ArchiveDeleteRequest(preview.ids, preview.snapshotHash)).deletedFiles)
            }
            }
            val current = bridge.collect(evidenceSnapshot(), listOf(ref))
            assertNotNull(current.sources.single().binding)
            assertTrue(current.sources.single().evidence.isNotEmpty())
            val wrongHash = bridge.collect(evidenceSnapshot(), listOf(ref.copy(bodySha256 = first.bodySha256)))
            assertNotNull(wrongHash.sources.single().failure)
            val other = bridge.paper("other")
            val wrongRecord = bridge.collect(evidenceSnapshot(), listOf(ref.copy(archiveRecordId = other.archiveRecordId, sourceItemKey = "other")))
            assertNotNull(wrongRecord.sources.single().failure)
            assertEquals(before, f.repository.record(record.id))
            Mockito.verifyNoInteractions(bridge.network, bridge.bounded)
        }
    }

    @Test fun `all routes require authentication and strict request integers are rejected before service`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val service = Mockito.mock(DiscoveryPaperReprocessService::class.java)
        val auth = Mockito.mock(AuthService::class.java)
        Mockito.`when`(auth.findUser("fixture")).thenReturn(AdminUser(1, "fixture", "unused", false,
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()))
        val mvc = MockMvcBuilders.standaloneSetup(DiscoveryPaperReprocessController(service))
            .addInterceptors(AuthInterceptor(auth, mapper, Mockito.mock(ExpertMailPermissionGuard::class.java))).build()
        val root = "/api/expert-discovery/paper-reprocess"
        for (path in listOf("/preview?sourceExecutionId=1", "/runs", "/runs/1", "/runs/1/items", "/runs/1/items/1"))
            mvc.perform(get(root + path)).andExpect(status().isUnauthorized)
        for (path in listOf("/runs", "/runs/1/cancel", "/runs/1/retry"))
            mvc.perform(post(root + path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized)
        val session = MockHttpSession().also { it.setAttribute(AuthSessionKeys.USERNAME, "fixture") }
        for (revision in listOf("1.5", "9223372036854775808", "\"1\"", "null")) {
            mvc.perform(post("$root/runs/1/cancel").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":$revision}")).andExpect(status().isBadRequest)
        }
        mvc.perform(post("$root/runs").session(session).contentType(MediaType.APPLICATION_JSON)
            .content("""{"requestKey":"x","sourceExecutionId":1.1,"mode":"AUDIT","expectedScopeHash":"x"}"""))
            .andExpect(status().isBadRequest)
        Mockito.verifyNoInteractions(service)
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "paperReprocessIt", matches = "true")
    fun `repository API errors retain stable status and code with bounded Chinese operator explanations`() {
        Paper03RuntimeFixture().use { f ->
            val mvc = MockMvcBuilders.standaloneSetup(f.controller).build()
            val root = "/api/expert-discovery/paper-reprocess"
            val missing = mvc.perform(get("$root/preview").param("sourceExecutionId", "404"))
                .andExpect(status().isNotFound).andReturn().response
            f.db.jdbc.update("UPDATE task_execution SET status='RUNNING' WHERE id=?", f.sourceId)
            val running = mvc.perform(get("$root/preview").param("sourceExecutionId", f.sourceId.toString()))
                .andExpect(status().isConflict).andReturn().response
            f.db.jdbc.update("UPDATE task_execution SET status='SUCCESS' WHERE id=?", f.sourceId)
            val run = f.create()
            val conflict = mvc.perform(post("$root/runs/${run.executionId}/cancel")
                .contentType(MediaType.APPLICATION_JSON).content("""{"revision":${run.revision + 1}}"""))
                .andExpect(status().isConflict).andReturn().response
            for ((response, code) in listOf(missing to "SOURCE_NOT_FOUND", running to "SOURCE_RUNNING", conflict to "REVISION_CONFLICT")) {
                val error = f.mapper.readTree(response.contentAsByteArray)
                assertEquals(code, error.path("code").asText())
                val message = error.path("message").asText()
                assertTrue(message.length in 1..256)
                assertNotEquals(code, message)
                assertTrue(message.any { it in '\u4e00'..'\u9fff' })
            }
        }
    }
}

/** Isolated disposable MySQL + real archive; no production Spring context or provider configuration. */
internal class Paper03RuntimeFixture : AutoCloseable {
    val db = com.weibo.talentintroduction.discovery.repository.ReprocessMysqlFixture
    val mapper = db.mapper
    val repository = db.repository()
    val directory = java.nio.file.Files.createTempDirectory("paper03-runtime-").toRealPath()
    val archiveRepository = DiscoveryPaperArchiveRepository(db.jdbc, db.manager)
    val archive = DiscoveryPaperArchiveService(archiveRepository,
        DiscoveryPaperArchiveProperties(directory.resolve("papers").toString()),
        DiscoverySampleProperties(directory = directory.resolve("samples").toString()), mapper)
    val network = Mockito.mock(RestTemplate::class.java)
    val bounded = Mockito.mock(BoundedHttpExecutor::class.java)
    val analyzer = Mockito.spy(PaperBodyAnalyzer(PdfEmailExtractor(network, PlainTextEmailExtractor(),
        PdfExtractionProperties(), bounded), PaperQualificationEvidenceExtractor(), mapper))
    val progress = Mockito.mock(com.weibo.talentintroduction.task.service.TaskProgressStore::class.java)
    val settings = Mockito.mock(DiscoveryApolloSettingsService::class.java)
    val service: DiscoveryPaperReprocessService
    val controller: DiscoveryPaperReprocessController
    val sourceId = 9001L
    val records = mutableListOf<ArchiveRecord>()
    val refs = mutableListOf<List<ArchiveAttempt>>()
    init {
        db.clean(); db.task(sourceId)
        archive.diskProbe = { ArchiveDisk(100 * ARCHIVE_GIB, 80 * ARCHIVE_GIB, 70 * ARCHIVE_GIB) }
        archive.updateSettings(ArchiveSettingsRequest(0, true, 20 * ARCHIVE_GIB, ARCHIVE_GIB))
        Mockito.`when`(settings.freeze()).thenReturn(FrozenApolloAccess.fromSettings(DiscoveryApolloSettings(
            0, ApolloSettingSource.DEPLOYMENT_DEFAULT, false, false, false, false, false, false, false, null)))
        service = DiscoveryPaperReprocessService(repository, archive, analyzer, mapper, settings, progress)
        controller = DiscoveryPaperReprocessController(service)
    }
    fun seed(count: Int = 121) {
        val authors = listOf(PaperAuthor("Alice", "Smith", null, null, openAlexAuthorId = "A123"),
            PaperAuthor("Bob", "Jones", null, null, openAlexAuthorId = "A456"))
        repeat(count) { index ->
            val metadata = mapper.valueToTree<JsonNode>(mapOf("schemaVersion" to 1, "title" to "Paper $index",
                "authors" to authors, "pubYear" to 2023, "doi" to "10.1234/paper$index"))
            val record = archive.beginRecord(sourceId, "OPENALEX", "W$index", metadata)
            fun body(text: String): ArchiveAttempt {
                val attempt = archive.beginAttempt(record.id, java.util.UUID.randomUUID(), 0, ArchiveAttemptKind.EMBEDDED_TEXT)
                return archive.saveBody(attempt.id, text.toByteArray(), "text/plain")
            }
            val primary = body(if (index == count - 1 && count > 5) "Bob Jones: alice@fixture.edu"
                else "Alice Smith: alice@fixture.edu\n\nAlice Smith received her PhD in 2020.")
            val bodies = if (index == 0) listOf(primary, body("Bob Jones: bob@fixture.edu"), body("Bob Jones: bob@fixture.edu")) else listOf(primary)
            archive.finishExtraction(record.id, mapper.readTree("""{"schemaVersion":1,"first":{"capturedAt":"2023-01-01T00:00:00Z","attemptIds":[${primary.id}],"outcome":{"emails":[]}}}"""))
            records += archiveRepository.record(record.id)!!
            refs += bodies
        }
        archive.protection(ArchiveProtectionRequest(listOf(ArchiveRevisionItem(records.first().id,
            archiveRepository.record(records.first().id)!!.revision)), true))
        records[0] = archiveRepository.record(records[0].id)!!
    }
    fun create(): ReprocessRun {
        val preview = service.preview(sourceId)
        return service.create(CreateReprocessRequest(java.util.UUID.randomUUID().toString(), sourceId, ReprocessMode.AUDIT, preview.expectedScopeHash))
    }
    fun claim() = repository.claimRun("runtime-owner")!!
    fun execute() = service.execute(claim())
    fun waitTerminal(id: Long): ReprocessDetail {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(90)
        while (true) {
            val detail = service.detail(id)
            if (detail.run.state !in setOf(ReprocessState.PREPARING, ReprocessState.RUNNING, ReprocessState.CANCELLING)) return detail
            check(System.nanoTime() < deadline) { "runtime task did not reach terminal state" }
            service.poll()
            Thread.sleep(20)
        }
    }
    override fun close() {
        service.shutdown()
        directory.toFile().deleteRecursively()
    }
}

/** The opt-in scenarios share the exact production repository/controller/service used by the runtime main. */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "paperReprocessIt", matches = "true")
class DiscoveryPaperReprocessRuntimeScenarios {
    @Test fun `121 records whole source API freeze parse then conflict merge has seven pages and zero business writes`() {
        Paper03RuntimeFixture().use { f -> paper03WholeSourceScenario(f) }
    }
    @Test fun `cancel retains completed papers retry preserves results and version change refuses resume`() {
        Paper03RuntimeFixture().use { f ->
            f.seed(5); val created = f.create()
            var parsed = 0
            f.service.beforeParse = {
                if (++parsed == 3) f.service.cancel(created.executionId, f.service.detail(created.executionId).run.revision)
            }
            f.execute()
            val cancelled = f.service.detail(created.executionId)
            assertEquals(ReprocessState.CANCELLED, cancelled.run.state)
            assertEquals(2L, cancelled.summary.paperComplete)
            val completeBefore = f.repository.items(created.executionId, ReprocessKind.PAPER, 0, 100).items
                .filter { it.state == ReprocessItemState.COMPLETE }.associate { it.id to it.result }
            f.service.beforeParse = {}
            f.service.retry(created.executionId, cancelled.run.revision)
            f.execute()
            assertEquals(ReprocessState.COMPLETED, f.service.detail(created.executionId).run.state)
            for ((id, result) in completeBefore) assertEquals(result, f.repository.item(created.executionId, id)!!.result)
            // Explicit timeout interruption, no normal parse-error substitution.
            val next = f.create(); f.service.parseBudgetMillis = 0
            f.execute()
            val interrupted = f.service.detail(next.executionId)
            assertEquals(ReprocessState.INTERRUPTED, interrupted.run.state)
            val scope = interrupted.run.scope.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("codeVersion", "old")
            f.db.jdbc.update("UPDATE discovery_paper_reprocess_run SET scope_json=? WHERE execution_id=?", scope.toString(), next.executionId)
            assertEquals("VERSION_CHANGED", assertThrows(ReprocessException::class.java) {
                f.service.retry(next.executionId, f.service.detail(next.executionId).run.revision)
            }.code)
            Mockito.verifyNoInteractions(f.network, f.bounded)
        }
    }
    @Test fun `every retained HTML XML PDF and TEXT body is parsed without downloading or changing first`() {
        Paper03RuntimeFixture().use { f ->
            f.seed(1)
            val record = f.records.single()
            fun body(bytes: ByteArray, kind: ArchiveAttemptKind, media: String) {
                val attempt = f.archive.beginAttempt(record.id, java.util.UUID.randomUUID(), 0, kind)
                f.archive.saveBody(attempt.id, bytes, media)
            }
            body("<html><body>Alice Smith: alice@fixture.edu</body></html>".toByteArray(), ArchiveAttemptKind.PDF_URL, "text/html")
            body("<article><front><article-meta><title-group><article-title>Fixture</article-title></title-group></article-meta></front></article>".toByteArray(),
                ArchiveAttemptKind.PMC_XML, "application/xml")
            body(QualificationFixtures.pdf(lines = listOf("Alice Smith: alice@fixture.edu")), ArchiveAttemptKind.PDF_URL, "application/pdf")
            val before = f.archiveRepository.record(record.id)
            val run = f.create(); f.execute()
            assertEquals(ReprocessState.COMPLETED, f.service.detail(run.executionId).run.state)
            val paper = f.repository.items(run.executionId, ReprocessKind.PAPER, 0, 20).items.single()
            assertEquals(6, paper.result!!.path("attempts").size())
            assertEquals(setOf("HTML", "XML", "PDF", "TEXT"), paper.result!!.path("attempts").map { it.path("parsed").path("format").asText() }.toSet())
            assertEquals(before, f.archiveRepository.record(record.id))
            Mockito.verifyNoInteractions(f.network, f.bounded)
        }
    }
    @Test fun `same email and name but different trusted author ids is a cross paper conflict`() {
        Paper03RuntimeFixture().use { f ->
            f.seed(3)
            val record = f.records.last()
            val metadata = f.mapper.readTree(record.metadataJson)
            (metadata.path("authors")[0] as com.fasterxml.jackson.databind.node.ObjectNode).put("openAlexAuthorId", "A999")
            f.db.jdbc.update("UPDATE discovery_paper_archive_record SET metadata_json=?,revision=revision+1 WHERE id=?", metadata.toString(), record.id)
            val run = f.create(); f.execute()
            val target = f.repository.items(run.executionId, ReprocessKind.TARGET, 0, 20).items.single {
                it.input.path("email").asText() == "alice@fixture.edu"
            }
            assertEquals("CONFLICT", target.result!!.path("reasonCode").asText())
        }
    }
    @Test fun `dedicated bounded executor rejection releases lease and shutdown interrupts owned work`() {
        Paper03RuntimeFixture().use { f ->
            f.seed(1); val run = f.create()
            val field = DiscoveryPaperReprocessService::class.java.getDeclaredField("pool").also { it.isAccessible = true }
            val pool = field.get(f.service) as java.util.concurrent.ThreadPoolExecutor
            assertEquals(1, pool.corePoolSize); assertEquals(1, pool.maximumPoolSize)
            assertEquals(1, pool.queue.remainingCapacity())
            pool.shutdown()
            f.service.poll()
            val rejected = f.repository.find(run.executionId)!!
            assertNull(rejected.leaseToken)
            assertTrue(rejected.generation > 0)
            val reclaimed = f.repository.claimRun("new-owner")!!
            assertEquals(run.executionId, reclaimed.executionId)
            assertTrue(reclaimed.generation > rejected.generation)
        }
        Paper03RuntimeFixture().use { f ->
            f.seed(1); val run = f.create()
            f.service.beforeParse = { f.service.shutdown() }
            f.service.poll()
            assertEquals(ReprocessState.INTERRUPTED, f.waitTerminal(run.executionId).run.state)
            assertNull(f.repository.active())
        }
    }
    @Test fun `papers frozen without bodies are skipped and do not block candidate processing`() {
        Paper03RuntimeFixture().use { f ->
            f.seed(3)
            val run = f.create(); val claim = f.claim()
            while (!f.repository.freezePage(run.executionId, claim.leaseToken!!, claim.generation)) { }
            val paper = f.repository.items(run.executionId, ReprocessKind.PAPER, 0, 20).items.first()
            f.db.jdbc.update("UPDATE discovery_paper_reprocess_item SET input_json=JSON_SET(input_json,'$.bodies',JSON_ARRAY()) WHERE id=?", paper.id)
            f.service.execute(claim)
            val result = f.service.detail(run.executionId)
            assertEquals(ReprocessState.COMPLETED, result.run.state)
            assertEquals(1L, result.summary.paperSkipped)
            assertEquals(0L, result.summary.paperErrors)
            assertEquals(2L, result.summary.paperComplete)
            assertTrue(result.summary.targets > 0)
            assertTrue(result.summary.targetTotalKnown)
            assertEquals(ReprocessItemState.SKIPPED, f.repository.item(run.executionId, paper.id)!!.state)
        }
    }

    @Test fun `successful papers proceed while failed papers retry with a new evidence union`() {
        Paper03RuntimeFixture().use { f ->
            f.seed(3)
            val missing = f.refs.first()[1]
            val hash = missing.blobSha256!!
            val path = f.directory.resolve("papers/blobs/${hash.take(2)}/$hash.bin")
            val bytes = java.nio.file.Files.readAllBytes(path)
            val run = f.create(); val claim = f.claim()
            while (!f.repository.freezePage(run.executionId, claim.leaseToken!!, claim.generation)) { }
            java.nio.file.Files.delete(path)
            f.service.execute(claim)
            val failed = f.service.detail(run.executionId)
            assertEquals(ReprocessState.COMPLETED_WITH_ERRORS, failed.run.state)
            assertEquals(2L, failed.summary.paperComplete); assertEquals(1L, failed.summary.paperErrors)
            assertTrue(failed.summary.targets > 0)
            assertTrue(failed.summary.targetTotalKnown)
            val papers = f.repository.items(run.executionId, ReprocessKind.PAPER, 0, 20).items
            val error = papers.single { it.state == ReprocessItemState.ERROR }
            assertTrue(error.result!!.path("attempts").any { it.has("parsed") })
            assertEquals(2, error.result!!.path("attempts").count { it.path("reasonCode").asText() == "SOURCE_MISSING" })
            assertTrue(error.result!!.path("analysis").path("delta").isNull)
            val completeBefore = papers.filter { it.state == ReprocessItemState.COMPLETE }.associate { it.id to it.result }
            java.nio.file.Files.write(path, bytes)
            val retried = f.service.retry(run.executionId, failed.run.revision)
            assertNotEquals(run.executionId, retried.executionId)
            assertEquals(run.executionId, retried.scope.path("retryOf").asLong())
            assertEquals(retried.executionId, f.service.retry(run.executionId, failed.run.revision).executionId)
            f.execute()
            val complete = f.service.detail(retried.executionId)
            assertEquals(ReprocessState.COMPLETED, complete.run.state)
            assertEquals(3L, complete.summary.paperComplete)
            assertTrue(complete.summary.targets > 0)
            for ((id, result) in completeBefore) assertEquals(result, f.repository.item(run.executionId, id)!!.result)
        }
    }
}

internal fun paper03WholeSourceScenario(f: Paper03RuntimeFixture) {
    f.seed()
    f.db.jdbc.update("UPDATE task_execution SET status='RUNNING' WHERE id=?", f.sourceId)
    assertEquals("SOURCE_RUNNING", assertThrows(ReprocessException::class.java) { f.service.preview(f.sourceId) }.code)
    f.db.jdbc.update("UPDATE task_execution SET status='SUCCESS' WHERE id=?", f.sourceId)
    val before = f.records.associate { it.id to f.archiveRepository.record(it.id) }
    val root = "/api/expert-discovery/paper-reprocess"
    val mvc = MockMvcBuilders.standaloneSetup(f.controller).build()
    val preview = f.mapper.readTree(mvc.perform(get("$root/preview").param("sourceExecutionId", f.sourceId.toString())
        .param("q", "only-one-paper").param("page", "6")).andExpect(status().isOk).andReturn().response.contentAsString)
    assertEquals(121, preview.path("recordCount").asInt())
    val request = """{"requestKey":"${java.util.UUID.randomUUID()}","sourceExecutionId":${f.sourceId},"mode":"AUDIT","expectedScopeHash":"${preview.path("expectedScopeHash").asText()}"}"""
    mvc.perform(post("$root/runs").contentType(MediaType.APPLICATION_JSON).content(request.replace("\"AUDIT\"", "\"ENFORCE\"")))
        .andExpect(status().isServiceUnavailable).andExpect(jsonPath("$.code").value("APPLY_NOT_AVAILABLE"))
    val created = f.mapper.readTree(mvc.perform(post("$root/runs").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isAccepted).andReturn().response.contentAsString)
    val id = created.path("executionId").asLong()
    val duplicate = f.mapper.readTree(mvc.perform(post("$root/runs").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isAccepted).andReturn().response.contentAsString)
    assertEquals(id, duplicate.path("executionId").asLong())
    mvc.perform(post("$root/runs").contentType(MediaType.APPLICATION_JSON)
        .content(request.replace(preview.path("expectedScopeHash").asText(), "0".repeat(64))))
        .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("REQUEST_KEY_CONFLICT"))
    val second = request.replace(created.path("requestKey").asText(), java.util.UUID.randomUUID().toString())
    val active = f.mapper.readTree(mvc.perform(post("$root/runs").contentType(MediaType.APPLICATION_JSON).content(second))
        .andExpect(status().isAccepted).andReturn().response.contentAsString)
    assertEquals(id, active.path("executionId").asLong())
    val activity = com.weibo.talentintroduction.task.controller.TaskActivityController(f.db.tasks, f.progress).activeExecutions(0, 50)
    assertTrue(activity.items.any { it.id == id && it.taskTypeLabel == "论文重新解析与补录" })
    f.service.beforeParse = {
        val run = f.repository.find(id)!!
        assertTrue(run.scope.path("frozen").asBoolean())
        assertEquals(121L, f.repository.summary(id).papers)
        assertEquals(64, run.scope.path("manifestHash").asText().length)
    }
    f.execute()
    val detail = f.service.detail(id)
    assertEquals(ReprocessState.COMPLETED, detail.run.state)
    assertEquals(121L, detail.summary.papers); assertEquals(121L, detail.summary.paperComplete)
    assertEquals(0, detail.summary.actualNew); assertEquals(0, detail.summary.actualUpdated); assertEquals(0, detail.summary.actualAdmitted)
    val ids = mutableSetOf<Long>()
    repeat(7) { page ->
        val response = f.mapper.readTree(mvc.perform(get("$root/runs/$id/items").param("kind", "PAPER")
            .param("page", page.toString()).param("size", "20")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(if (page == 6) 1 else 20, response.path("items").size())
        response.path("items").forEach { ids += it.path("id").asLong() }
    }
    assertEquals(121, ids.size)
    val first = f.repository.items(id, ReprocessKind.PAPER, 0, 20).items.first()
    assertEquals(3, first.input.path("bodies").size())
    assertEquals(3, first.result!!.path("attempts").size())
    mvc.perform(get("$root/runs/$id/items").param("size", "101")).andExpect(status().isBadRequest)
    mvc.perform(get("$root/runs/$id/items").param("page", "-1")).andExpect(status().isBadRequest)
    mvc.perform(get("$root/runs/$id/items/999999999")).andExpect(status().isNotFound)
    val targets = f.repository.items(id, ReprocessKind.TARGET, 0, 100).items
    assertTrue(targets.any { it.input.path("email").asText() == "alice@fixture.edu" && it.result!!.path("reasonCode").asText() == "CONFLICT" })
    // The real AUDIT writer persists candidate flags with businessExistenceEvaluated=false; summary must
    // surface those overlapping proposals for actual production rows, not only manually seeded fixtures.
    val candidateCreates = targets.count { it.result?.path("wouldCreate")?.asBoolean() == true }
    val candidateRepairs = targets.count { it.result?.path("wouldRepair")?.asBoolean() == true }
    assertNotNull(detail.summary.wouldCreate); assertNotNull(detail.summary.wouldRepair)
    assertEquals(candidateCreates.toLong(), detail.summary.wouldCreate)
    assertEquals(candidateRepairs.toLong(), detail.summary.wouldRepair)
    assertEquals(3, Mockito.mockingDetails(f.analyzer).invocations.count { it.method.name == "parse" },
        "shared body/metadata is decoded once; duplicate attempt references are retained")
    for ((recordId, value) in before) assertEquals(value, f.archiveRepository.record(recordId))
    Mockito.verifyNoInteractions(f.network, f.bounded)
}

@org.springframework.boot.test.context.TestConfiguration
@org.springframework.web.servlet.config.annotation.EnableWebMvc
class Paper03RuntimeMvc : org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
    override fun addInterceptors(registry: org.springframework.web.servlet.config.annotation.InterceptorRegistry) {
        registry.addInterceptor(requireNotNull(auth))
    }
    companion object { var auth: AuthInterceptor? = null }
}

/**
 * Real HTTP/Tomcat smoke entry. Run with exec:java test classpath and -DpaperReprocessIt=true.
 * The input database/container and files are disposable synthetic fixtures, never production originals.
 */
object Paper03RuntimeSmoke {
    @JvmStatic fun main(args: Array<String>) {
        check(System.getProperty("paperReprocessIt") == "true") { "Explicit paperReprocessIt=true required" }
        Paper03RuntimeFixture().use { f ->
            f.seed()
            val auth = Mockito.mock(AuthService::class.java)
            Mockito.`when`(auth.findUser("fixture")).thenReturn(AdminUser(1, "fixture", "unused", false,
                createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()))
            Paper03RuntimeMvc.auth = AuthInterceptor(auth, f.mapper, Mockito.mock(ExpertMailPermissionGuard::class.java))
            val tomcat = org.apache.catalina.startup.Tomcat()
            tomcat.setBaseDir(f.directory.resolve("tomcat").toString()); tomcat.setPort(0); tomcat.connector
            val context = tomcat.addContext("", f.directory.toString())
            val spring = org.springframework.web.context.support.GenericWebApplicationContext()
            org.springframework.context.annotation.AnnotatedBeanDefinitionReader(spring).register(Paper03RuntimeMvc::class.java)
            spring.beanFactory.registerSingleton("paperReprocessController", f.controller)
            spring.beanFactory.registerSingleton("taskActivityController",
                com.weibo.talentintroduction.task.controller.TaskActivityController(f.db.tasks, f.progress))
            org.apache.catalina.startup.Tomcat.addServlet(context, "api", org.springframework.web.servlet.DispatcherServlet(spring))
                .loadOnStartup = 1
            context.addServletMappingDecoded("/", "api")
            org.apache.catalina.startup.Tomcat.addServlet(context, "fixtureLogin", object : javax.servlet.http.HttpServlet() {
                override fun doGet(req: javax.servlet.http.HttpServletRequest, res: javax.servlet.http.HttpServletResponse) {
                    req.getSession(true).setAttribute(AuthSessionKeys.USERNAME, "fixture"); res.status = 204
                }
            })
            context.addServletMappingDecoded("/fixture-login", "fixtureLogin")
            try {
                tomcat.start()
                val base = "http://127.0.0.1:${tomcat.connector.localPort}"
                var cookie: String? = null
                fun http(method: String, path: String, body: String? = null, expected: Int = 200): JsonNode {
                    val connection = java.net.URL(base + path).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = method; connection.connectTimeout = 5000; connection.readTimeout = 10000
                    cookie?.let { connection.setRequestProperty("Cookie", it) }
                    if (body != null) {
                        connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                        connection.outputStream.use { it.write(body.toByteArray()) }
                    }
                    val status = connection.responseCode
                    connection.getHeaderField("Set-Cookie")?.let { cookie = it.substringBefore(';') }
                    val text = (if (status >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    check(status == expected) { "$method $path returned $status: $text" }
                    connection.disconnect()
                    return if (text.isBlank()) f.mapper.nullNode() else f.mapper.readTree(text)
                }
                val root = "/api/expert-discovery/paper-reprocess"
                http("GET", "$root/preview?sourceExecutionId=${f.sourceId}", expected = 401)
                http("GET", "/fixture-login", expected = 204)
                val missing = http("GET", "$root/preview?sourceExecutionId=404", expected = 404)
                f.db.jdbc.update("UPDATE task_execution SET status='RUNNING' WHERE id=?", f.sourceId)
                val running = http("GET", "$root/preview?sourceExecutionId=${f.sourceId}", expected = 409)
                f.db.jdbc.update("UPDATE task_execution SET status='SUCCESS' WHERE id=?", f.sourceId)
                for ((error, code) in listOf(missing to "SOURCE_NOT_FOUND", running to "SOURCE_RUNNING")) {
                    check(error.path("code").asText() == code)
                    check(error.path("message").asText().length in 1..256)
                    check(error.path("message").asText().any { it in '\u4e00'..'\u9fff' })
                }
                val preview = http("GET", "$root/preview?sourceExecutionId=${f.sourceId}&q=only-one-paper&page=6")
                check(preview.path("recordCount").asInt() == 121)
                val request = """{"requestKey":"${java.util.UUID.randomUUID()}","sourceExecutionId":${f.sourceId},"mode":"AUDIT","expectedScopeHash":"${preview.path("expectedScopeHash").asText()}"}"""
                val run = http("POST", "$root/runs", request, 202)
                val id = run.path("executionId").asLong()
                val conflict = http("POST", "$root/runs/$id/cancel", """{"revision":${run.path("revision").asLong() + 1}}""", 409)
                check(conflict.path("code").asText() == "REVISION_CONFLICT")
                check(conflict.path("message").asText().length in 1..256)
                check(conflict.path("message").asText().any { it in '\u4e00'..'\u9fff' })
                check(http("POST", "$root/runs", request, 202).path("executionId").asLong() == id)
                check(http("GET", "/api/task-executions/active?size=50").path("items").any { it.path("id").asLong() == id })
                val before = f.records.associate { it.id to f.archiveRepository.record(it.id) }
                f.service.poll()
                f.waitTerminal(id)
                val detail = http("GET", "$root/runs/$id")
                check(detail.path("run").path("state").asText() == "COMPLETED") { detail.toString() }
                val ids = mutableSetOf<Long>()
                repeat(7) { page ->
                    val items = http("GET", "$root/runs/$id/items?kind=PAPER&page=$page&size=20")
                    check(items.path("items").size() == if (page == 6) 1 else 20)
                    items.path("items").forEach { ids += it.path("id").asLong() }
                }
                check(ids.size == 121)
                check(detail.path("summary").path("paperComplete").asInt() == 121)
                for (key in listOf("actualNew", "actualUpdated", "actualAdmitted")) check(detail.path("summary").path(key).asInt() == 0)
                val targets = http("GET", "$root/runs/$id/items?kind=TARGET&size=100")
                check(targets.path("items").any { it.path("result").path("reasonCode").asText() == "CONFLICT" })
                for ((recordId, value) in before) check(value == f.archiveRepository.record(recordId))
                check(f.db.tasks.findById(id).get().status == "SUCCESS")
                Mockito.verifyNoInteractions(f.network, f.bounded)
                println("PAPER03_RUNTIME_HTTP_OK records=121 pages=7 sourceFrozen=true duplicateRunId=$id conflicts=true actualNew=0 actualUpdated=0 actualAdmitted=0 providerCalls=0 archiveMutations=0")
                val correction = f.create()
                val owner = f.claim()
                while (!f.repository.freezePage(owner.executionId, owner.leaseToken!!, owner.generation)) { }
                check(f.repository.setPhase(owner.executionId, owner.leaseToken!!, owner.generation, PaperReprocessPhase.PARSE))
                val oversized = f.repository.claimItem(owner.executionId, owner.leaseToken!!, owner.generation, ReprocessKind.PAPER)!!
                check(f.repository.completeItem(owner.executionId, owner.leaseToken!!, owner.generation,
                    oversized.id, oversized.leaseToken!!, oversized.generation, ReprocessItemState.COMPLETE,
                    f.db.result().put("evidence", "a".repeat(4 * 1024 * 1024))))
                val interrupted = f.repository.claimItem(owner.executionId, owner.leaseToken!!, owner.generation, ReprocessKind.PAPER)!!
                f.db.jdbc.update("UPDATE discovery_paper_reprocess_run SET lease_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE execution_id=?", correction.executionId)
                check(f.repository.claimRun("correction-recovery") == null)
                for ((item, code) in listOf(oversized to "RESULT_TOO_LARGE", interrupted to "EXECUTION_LOST")) {
                    val apiItem = http("GET", "$root/runs/${correction.executionId}/items/${item.id}")
                    val raw = f.db.jdbc.queryForObject("SELECT result_json FROM discovery_paper_reprocess_item WHERE id=?", String::class.java, item.id)!!
                    val persisted = f.mapper.readTree(raw)
                    check(apiItem.path("state").asText() == "ERROR")
                    check(apiItem.path("result") == persisted)
                    check(persisted.path("schemaVersion").asInt() == 1)
                    check(persisted.path("reasonCode").asText() == code)
                    check(persisted.path("reason").asText().length in 1..256)
                    check(persisted.path("reason").asText().any { it in '\u4e00'..'\u9fff' })
                    check(raw.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024)
                    check(persisted.path("trace").map { it.path("stepNo").asInt() } == (1..6).toList())
                    check(persisted.path("trace").map { it.path("state").asText() } == listOf("ERROR") + List(5) { "NOT_STARTED" })
                    check(persisted.path("attempts").map { it.path("attemptId") to it.path("hash") } ==
                        item.input.path("bodies").map { it.path("attemptId") to it.path("hash") })
                }
                println("PAPER03_CORRECTION_HTTP_SQL_OK codes=SOURCE_NOT_FOUND,SOURCE_RUNNING,REVISION_CONFLICT statuses=404,409,409 chinese=true durable=RESULT_TOO_LARGE,EXECUTION_LOST schema=1 trace=6 refsPreserved=true bounded=true")
            } finally {
                tomcat.stop(); tomcat.destroy(); spring.close(); Paper03RuntimeMvc.auth = null; f.db.stop()
            }
        }
    }
}
