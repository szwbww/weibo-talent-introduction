package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.config.*
import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.controller.DiscoveryRecheckController
import com.weibo.talentintroduction.discovery.controller.DiscoveryReviewController
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationTraceRepository
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.sun.net.httpserver.HttpServer
import org.apache.catalina.startup.Tomcat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestTemplate
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.net.CookieManager
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.sql.DataSource

/** No application component scan, production datasource, scheduler, SMTP or provider is bootstrapped. */
@org.springframework.boot.test.context.TestConfiguration
@EnableAutoConfiguration
@EnableJdbcRepositories(basePackages = ["com.weibo.talentintroduction"])
class RecheckRuntimeJdbcConfig

@org.springframework.boot.test.context.TestConfiguration
@EnableWebMvc
@Import(AuthWebConfig::class, GlobalExceptionHandler::class)
class RecheckRuntimeMvc(private val mapper: ObjectMapper) : WebMvcConfigurer {
    override fun configureMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        converters.add(MappingJackson2HttpMessageConverter(mapper))
    }
}

/** Selected ITs always require real MySQL. main() exercises the same actual servlet/SQL pipeline outside JUnit. */
class DiscoveryRecheckFlowIT {
    @Test fun `all new routes frozen 1001 audit history application cancellation recovery and projection use real SQL`() = runRuntime()
    @Test fun `archived bodies use production consumer registration and private replay CLI`() = runRuntime(true)

    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        @JvmStatic fun main(args: Array<String>) {
            if (args.firstOrNull() == "--historical") {
                try { runHistorical(args.drop(1)) }
                catch (e: Exception) {
                    val frame = e.stackTrace.firstOrNull { it.className.startsWith("com.weibo.") }
                    System.err.println("HISTORICAL_REPLAY_FAILED:${e.javaClass.simpleName} at=${frame?.fileName}:${frame?.lineNumber}")
                    kotlin.system.exitProcess(2)
                }
            }
            else if (args.firstOrNull() == "--archived") runRuntime(true)
            else { runRuntime(); runRuntime(true) }
        }

        private fun runRuntime(archivedOnly: Boolean = false) {
            check(DockerClientFactory.instance().isDockerAvailable) { "Disposable MySQL required; skipping is not PASS" }
            Mysql().withDatabaseName("recheck_runtime").withUsername("test").withPassword("test")
                .withCommand("--log-bin-trust-function-creators=1").use { mysql ->
                mysql.start()
                val app = SpringApplicationBuilder(RecheckRuntimeJdbcConfig::class.java).web(WebApplicationType.NONE)
                    .run("--spring.datasource.url=${mysql.jdbcUrl}", "--spring.datasource.username=${mysql.username}",
                        "--spring.datasource.password=${mysql.password}", "--spring.flyway.placeholder-replacement=false",
                        "--spring.flyway.target=165", "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                        "--spring.rabbitmq.listener.simple.auto-startup=false")
                try {
                    val jdbc = app.getBean(JdbcTemplate::class.java)
                    val manager = DataSourceTransactionManager(app.getBean(DataSource::class.java))
                    val tasks = app.getBean(TaskExecutionRepository::class.java)
                    val props = DiscoveryVerificationProperties(enabled = true, workerEnabled = true, apolloApiKey = "fixture-key")
                    VerificationFlowFixture(jdbc, manager, props).use { f ->
                        if (archivedOnly) ArchivedRuntime(f, manager, tasks, app).use { it.exercise() }
                        else Runtime(f, manager, tasks).use { runtime -> runtime.exercise() }
                    }
                } finally { app.close() }
            }
        }

        /** Opt-in private historical replay; only disposable MySQL and the existing strict fixture ES are writable. */
        private fun runHistorical(args: List<String>) {
            require(args.size == 3)
            (org.slf4j.LoggerFactory.getILoggerFactory() as ch.qos.logback.classic.LoggerContext)
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).level = ch.qos.logback.classic.Level.OFF
            val inputPath = java.nio.file.Paths.get(args[0]).toRealPath()
            DiscoveryHistoricalReplayTool.privateFile(inputPath)
            val input = DiscoveryHistoricalReplayTool.mapper.readTree(Files.readAllBytes(inputPath))
            val archiveRoot = java.nio.file.Paths.get(args[1]).toRealPath()
            val output = java.nio.file.Paths.get(args[2]).toAbsolutePath()
            DiscoveryHistoricalReplayTool.secureDirectory(output)
            check(DockerClientFactory.instance().isDockerAvailable)
            Mysql().withDatabaseName("historical_reuse").withUsername("test").withPassword("test").use { mysql ->
                mysql.start()
                val app = SpringApplicationBuilder(RecheckRuntimeJdbcConfig::class.java).web(WebApplicationType.NONE).run(
                    "--spring.datasource.url=${mysql.jdbcUrl}", "--spring.datasource.username=${mysql.username}",
                    "--spring.datasource.password=${mysql.password}", "--spring.flyway.placeholder-replacement=false",
                    "--spring.flyway.target=165", "--spring.main.banner-mode=off", "--logging.level.root=OFF")
                try {
                    val jdbc = app.getBean(JdbcTemplate::class.java)
                    val manager = DataSourceTransactionManager(app.getBean(DataSource::class.java))
                    val tasks = app.getBean(TaskExecutionRepository::class.java)
                    val props = DiscoveryVerificationProperties(enabled = true, workerEnabled = true, apolloApiKey = "isolated-never-dispatched")
                    VerificationFlowFixture(jdbc, manager, props).use { f ->
                        f.clock.now = java.time.Instant.parse(input.path("evaluationAt").asText())
                        Mockito.`when`(f.filters.getCandidateFilter()).thenReturn(f.mapper.treeToValue(input.path("candidateConfig"), CandidateFilterProperties::class.java))
                        Mockito.`when`(f.filters.getAcademicFilter()).thenReturn(f.mapper.treeToValue(input.path("academicConfig"), AcademicFilterProperties::class.java))
                        val originals = input.path("original").toList()
                        val docs = originals.map { it.path("docId").asText() }.sorted()
                        require(docs.isNotEmpty() && docs.distinct().size == docs.size)
                        val sql = input.path("sqlRows").toList()
                        val allowed = setOf("discovery_paper_archive_record", "task_execution", "discovery_verification_run",
                            "discovery_verification_item", "discovery_verification_attempt", "expert_discovery_admission")
                        fun restore(table: String) {
                            require(table in allowed)
                            sql.filter { it.path("table").asText() == table }.forEach { saved ->
                                val values = saved.path("row").fields().asSequence().toList()
                                require(values.all { it.key.matches(Regex("[a-z_][a-z0-9_]*")) })
                                val columns = values.joinToString(",") { "`${it.key}`" }
                                val parameters = values.map { (_, value) -> when {
                                    value.isNull -> null
                                    value.isIntegralNumber -> value.longValue()
                                    value.isBoolean -> value.booleanValue()
                                    value.isTextual -> value.textValue()
                                    else -> f.mapper.writeValueAsString(value)
                                } }.toTypedArray()
                                jdbc.update("INSERT INTO $table ($columns) VALUES (${values.joinToString(",") { "?" }})", *parameters)
                            }
                        }
                        listOf("task_execution", "discovery_verification_run", "discovery_verification_item",
                            "discovery_verification_attempt", "expert_discovery_admission", "discovery_paper_archive_record").forEach(::restore)
                        originals.forEach { original ->
                            val id = original.path("id").asLong()
                            // The actual original frozen cache, not a refreshed or precomputed proposed outcome.
                            jdbc.update("UPDATE discovery_verification_item SET evidence_json=? WHERE id=?",
                                f.mapper.writeValueAsString(original.path("evidence")), id)
                            @Suppress("UNCHECKED_CAST") val profile = f.mapper.convertValue(original.path("snapshot").path("profile"), Map::class.java) as Map<String, Any?>
                            val raw = profile.toMutableMap()
                            (raw["externalIds"] as? String)?.let { raw["externalIds"] = f.mapper.readValue(it, Map::class.java) }
                            f.raws[original.path("docId").asText()] = raw
                        }
                        val sources = input.path("sourcePapers").map { f.mapper.treeToValue(it, DiscoveryHistoricalReplayTool.Paper::class.java) }
                        sources.forEach { paper ->
                            val ref = paper.source
                            val path = archiveRoot.resolve("blobs").resolve(ref.bodySha256.take(2)).resolve("${ref.bodySha256}.bin")
                            require(DiscoveryPaperArchiveService.sha(Files.readAllBytes(path)) == ref.bodySha256)
                            jdbc.update("INSERT IGNORE INTO discovery_paper_archive_blob (sha256,size_bytes,media_type,state,created_at) VALUES (?,?,?,'AVAILABLE',?)",
                                ref.bodySha256, Files.size(path), "text/plain", f.clock.instant().verificationUtc())
                            jdbc.update("""INSERT INTO discovery_paper_archive_attempt
                                (id,record_id,attempt_key,kind,status,blob_sha256,completed_bytes,created_at,updated_at)
                                VALUES (?,?,?,?,'SAVED',?,?,?,?)""", ref.attemptId, ref.archiveRecordId,
                                "historical-${ref.attemptId}", paper.kind.name, ref.bodySha256, Files.size(path),
                                f.clock.instant().verificationUtc(), f.clock.instant().verificationUtc())
                        }
                        val archiveRepo = DiscoveryPaperArchiveRepository(jdbc, manager)
                        val archive = DiscoveryPaperArchiveService(archiveRepo, DiscoveryPaperArchiveProperties(archiveRoot.toString()),
                            DiscoverySampleProperties(directory = output.resolve("unused-samples").toString()), f.mapper)
                        val papers = PaperVerificationEvidenceService(archiveRepo, archive, DiscoveryHistoricalReplayTool.analyzer(), f.mapper)
                        var providerCalls = 0
                        val deny = RestTemplate().apply { interceptors.add(org.springframework.http.client.ClientHttpRequestInterceptor { _, _, _ ->
                            providerCalls++; error("Historical REUSE forbids dispatch") }) }
                        val trace = DiscoveryVerificationTraceRepository(jdbc)
                        val worker = DiscoveryVerificationWorker(props, f.repository, OrcidVerificationEvidenceClient(props, deny, f.clock),
                            ApolloPeopleSearchClient(props, deny, f.clock), QualificationEvidenceParser(f.clock), f.rules, f.commits,
                            DefaultListableBeanFactory().getBeanProvider(RegistrationRecovery::class.java), f.clock, trace, papers)
                        val beans = DefaultListableBeanFactory()
                        beans.registerSingleton("historicalWorker", worker)
                        val queries = DiscoveryVerificationQueryService(f.repository, f.admissions, props)
                        val scan = DiscoveryReviewScanService(f.admissions, f.policy, f.writer, f.esHttp, f.esProperties, f.indices, f.mapper)
                        val rechecks = DiscoveryRecheckService(f.repository, f.admissions, f.writer, scan, f.policy, f.apolloSettings, props,
                            tasks, TransactionTemplate(manager), queries, f.commits, Executor { it.run() },
                            beans.getBeanProvider(DiscoveryVerificationWorker::class.java), papers, f.clock)
                        fun business() = f.mapper.writeValueAsString(listOf(f.raws, f.candidates, f.applications,
                            jdbc.queryForList("SELECT * FROM expert_discovery_admission ORDER BY expert_doc_id"),
                            jdbc.queryForList("SELECT * FROM mail_record ORDER BY id"), jdbc.queryForList("SELECT * FROM expert_contact ORDER BY id")))
                        val businessBefore = business()
                        val oldItems = jdbc.queryForList("SELECT * FROM discovery_verification_item ORDER BY id")
                        val oldAttempts = jdbc.queryForList("SELECT * FROM discovery_verification_attempt ORDER BY id")
                        val runs = mutableListOf<RecheckRunView>()
                        val proposed = mutableListOf<Map<String, Any?>>()
                        try {
                            docs.chunked(100).forEach { batch ->
                                val run = rechecks.prepare(RecheckPrepareRequest(UUID.randomUUID().toString(), docIds = batch), "historical-isolated-audit")
                                check(run.phase == RecheckPhase.READY)
                                rechecks.start(run.runId, RecheckStartRequest(run.rosterHash!!, run.revision))
                                while (true) {
                                    val lease = f.repository.claimDue(f.clock.instant(), 120) ?: break
                                    check(lease.reviewEpoch > 0 && lease.ownerExecutionId == run.runId)
                                    worker.process(lease)
                                }
                                rechecks.reconcile()
                                runs += queries.recheckRun(run.runId)
                                f.repository.findHistoryByDocIds(batch).filter { it.ownerExecutionId == run.runId }.forEach { item ->
                                    val original = originals.single { it.path("docId").asText() == item.expertDocId }
                                    val frozen = f.mapper.readTree(item.snapshotJson).path("reviewInput")
                                    proposed += mapOf("docHash" to com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.hash(item.expertDocId),
                                        "before" to original.path("outcome").asText(), "proposed" to item.outcome?.name,
                                        "qualification" to item.qualification.name, "state" to item.state.name,
                                        "cacheAvailable" to frozen.path("baselineEvidenceAvailable").asBoolean(),
                                        "sourceRefs" to frozen.path("paperSourceRefs"), "reasons" to f.mapper.readTree(item.reasonsJson ?: "[]"),
                                        "steps" to trace.read(item.id), "admissionRevision" to item.admissionRevision)
                                }
                            }
                        } finally { worker.close() }
                        check(proposed.size == docs.size && providerCalls == 0 && businessBefore == business())
                        check(oldItems == jdbc.queryForList("SELECT * FROM discovery_verification_item WHERE review_epoch=0 ORDER BY id"))
                        check(oldAttempts == jdbc.queryForList("SELECT * FROM discovery_verification_attempt ORDER BY id"))
                        val summary = mapOf("inputSha256" to DiscoveryPaperArchiveService.sha(Files.readAllBytes(inputPath)),
                            "evaluationAt" to f.clock.instant(), "requested" to docs.size, "processed" to proposed.size,
                            "terminalStates" to proposed.groupingBy { it["state"] }.eachCount(),
                            "outcomes" to proposed.groupingBy { it["proposed"] }.eachCount(),
                            "compatibleFrozenCaches" to proposed.count { it["cacheAvailable"] == true },
                            "wouldAdmit" to runs.sumOf { it.counts.wouldAdmit }, "actualAdmitted" to runs.sumOf { it.counts.actualAdmitted },
                            "providerHttpRequests" to providerCalls, "businessUnchanged" to true,
                            "environment" to "DISPOSABLE_MYSQL_STRICT_FIXTURE_ES_ORIGINAL_WORKER_GATE", "runs" to runs, "rows" to proposed)
                        val bytes = f.mapper.writeValueAsBytes(summary)
                        DiscoveryVerificationReplayTool.atomicWrite(output.resolve("audit-reuse.json"), bytes)
                        println("RECHECK10_HISTORICAL requested=${docs.size} processed=${proposed.size} wouldAdmit=${runs.sumOf { it.counts.wouldAdmit }} actualAdmitted=0 providerHttpRequests=0 businessUnchanged=true reportHash=${DiscoveryPaperArchiveService.sha(bytes)}")
                    }
                } finally { app.close() }
            }
        }
    }

    private class ArchivedRuntime(val f: VerificationFlowFixture, val manager: DataSourceTransactionManager,
        val tasks: TaskExecutionRepository, val app: org.springframework.context.ConfigurableApplicationContext) : AutoCloseable {
        private val root = Files.createTempDirectory("discovery-archived-runtime-").toRealPath()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private var writes = 0
        private var unexpected = 0
        private var providers = 0
        private val fixtureMappings = mutableMapOf<String, com.fasterxml.jackson.databind.JsonNode>()
        init {
            Files.setPosixFilePermissions(root, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))
            server.createContext("/") { exchange ->
                try {
                    val parts = exchange.requestURI.path.trim('/').split('/')
                    val indices = mapOf("raw" to f.raws, "candidate" to f.candidates, "application" to f.applications)
                    val method = exchange.requestMethod
                    var status = 200
                    val response: Any = if (parts.size == 1 && parts[0] in indices && method in setOf("GET", "HEAD")) {
                        mapOf(parts[0] to mapOf("mappings" to mapOf("properties" to fixtureMappings[parts[0]])))
                    } else if (parts.size == 2 && parts[0] in indices && parts[1] == "_mapping" && method == "PUT") {
                        val properties = f.mapper.readTree(exchange.requestBody.readBytes()).path("properties")
                        check(properties.isObject && !properties.isEmpty)
                        fixtureMappings[parts[0]] = properties
                        mapOf("acknowledged" to true)
                    } else if (parts.last() == "_search" && method == "POST") {
                        val body = f.mapper.readTree(exchange.requestBody.readBytes())
                        val email = body.path("query").path("term").path("email").asText()
                        check(email.isNotBlank())
                        val hits = parts.first().split(',').flatMap { index ->
                            indices.getValue(index).filter { it.value["email"] == email }.map { (id, doc) ->
                                mapOf("_id" to id, "_index" to index, "_source" to doc) }
                        }
                        mapOf("timed_out" to false, "_shards" to mapOf("failed" to 0),
                            "hits" to mapOf("total" to mapOf("value" to hits.size, "relation" to "eq"), "hits" to hits))
                    } else {
                        check(parts.size == 3 && parts[1] == "_doc")
                        val docs = indices.getValue(parts[0]); val id = parts[2]
                        when (method) {
                            "PUT" -> {
                                check(!docs.containsKey(id)) { "Fixture forbids overwriting a profile" }
                                @Suppress("UNCHECKED_CAST") val source = f.mapper.readValue(exchange.requestBody.readBytes(), Map::class.java) as Map<String, Any?>
                                docs[id] = source; writes++; mapOf("result" to "created")
                            }
                            "GET", "HEAD" -> if (docs.containsKey(id)) mapOf("_source" to docs[id], "_seq_no" to 1, "_primary_term" to 1)
                                else { status = 404; emptyMap<String, Any>() }
                            else -> error("Unexpected method")
                        }
                    }
                    val bytes = f.mapper.writeValueAsBytes(response)
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(status, if (method == "HEAD") -1 else bytes.size.toLong())
                    if (method != "HEAD") exchange.responseBody.use { it.write(bytes) } else exchange.close()
                } catch (_: Throwable) { unexpected++; exchange.sendResponseHeaders(500, -1); exchange.close() }
            }
            server.start()
        }
        private fun command(args: List<String>, expected: Int = 0, environment: Map<String, String> = emptyMap()) {
            val script = java.nio.file.Paths.get("scripts/discovery_recheck_replay/run.sh").toAbsolutePath().toString()
            val process = ProcessBuilder(listOf(script) + args).redirectErrorStream(true).apply {
                environment()["REPLAY_CLASSPATH"] = System.getProperty("java.class.path")
                environment().putAll(environment)
            }.start()
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
            check(process.waitFor(180, TimeUnit.SECONDS) && process.exitValue() == expected) {
                "CLI expected=$expected actual=${process.exitValue()} output=$output" }
            check(!output.contains("ada@"))
            println("RECHECK10_CLI command=${args.firstOrNull()} exit=$expected")
        }
        fun exercise() {
            val basePaper = HistoricalReplayFixtures.paper(root, 900001).let { paper ->
                val path = java.nio.file.Paths.get(paper.path)
                val bytes = String(Files.readAllBytes(path), Charsets.UTF_8)
                    .replace("Ada Lee holds a PhD in physics.",
                        "Ada Lee received her PhD in 2015. She was born on 15 May 1990.")
                    .toByteArray(Charsets.UTF_8)
                Files.write(path, bytes)
                paper.copy(source = paper.source.copy(bodySha256 = DiscoveryPaperArchiveService.sha(bytes)))
            }
            val invalid = HistoricalReplayFixtures.paper(root, 900002, email = "ada@mailinator.com")
            val ambiguous = HistoricalReplayFixtures.paper(root, 900003).let { p ->
                val bytes = """<article><front><article-meta><contrib-group><contrib><name><given-names>Ada</given-names><surname>Lee</surname></name><email>shared@fixture.edu</email></contrib><contrib><name><given-names>Eve</given-names><surname>Lee</surname></name><email>shared@fixture.edu</email></contrib></contrib-group></article-meta></front></article>""".toByteArray()
                Files.write(java.nio.file.Paths.get(p.path), bytes)
                p.copy(source = p.source.copy(bodySha256 = DiscoveryPaperArchiveService.sha(bytes)),
                    metadata = p.metadata.copy(authors = listOf(PaperAuthor("Ada", "Lee", null, null), PaperAuthor("Eve", "Lee", null, null))))
            }
            val missing = HistoricalReplayFixtures.paper(root, 900004, missing = true)
            val repo = DiscoveryPaperArchiveRepository(f.jdbc, manager)
            val archive = DiscoveryPaperArchiveService(repo, DiscoveryPaperArchiveProperties(root.resolve("archive").toString()),
                DiscoverySampleProperties(directory = root.resolve("samples").toString()), f.mapper)
            val analyzer = DiscoveryHistoricalReplayTool.analyzer()
            val papers = PaperVerificationEvidenceService(repo, archive, analyzer, f.mapper)
            val settings = archive.settings()
            archive.updateSettings(ArchiveSettingsRequest(settings.revision, true, settings.maxBytes, settings.minFreeBytes))
            val retained = listOf(basePaper, invalid, ambiguous).map { p ->
                val metadata = f.mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(p.metadata).put("schemaVersion", 1)
                val record = archive.beginRecord(28671, "CROSSREF", p.source.sourceItemKey, metadata)
                val attempt = archive.beginAttempt(record.id, UUID.randomUUID(), 1, p.kind,
                    "https://papers.fixture.invalid/${p.source.sourceItemKey}")
                archive.saveBody(attempt.id, Files.readAllBytes(java.nio.file.Paths.get(p.path)), "text/plain")
                archive.finishExtraction(record.id, f.mapper.valueToTree(mapOf("schemaVersion" to 1, "first" to mapOf("attemptIds" to listOf(attempt.id),
                    "outcome" to mapOf("emails" to emptyList<Any>())))))
                p.copy(source = p.source.copy(archiveRecordId = record.id, attemptId = attempt.id), baselineAttemptIds = listOf(attempt.id))
            } + missing
            archive.analysisLease(retained.first().source.archiveRecordId).use { lease ->
                val ref = retained.first().source
                val stored = repo.attempt(ref.attemptId)!!
                check(lease.record.executionId == ref.sourceExecutionId && lease.record.sourceName == ref.sourceName &&
                    lease.record.paperKey == ref.sourceItemKey && stored.blobSha256 == ref.bodySha256 &&
                    (ref.attemptId to ref.bodySha256) in lease.fileSet) { "Fixture archive references must resolve exact retained bytes" }
            }
            val historyBefore = f.jdbc.queryForList("SELECT * FROM discovery_verification_item ORDER BY id")
            val tasksBefore = f.jdbc.queryForList("SELECT * FROM task_execution ORDER BY id")
            val checkpoints = f.jdbc.queryForList("SELECT * FROM discovery_source_cursor ORDER BY id")
            val archiveBefore = f.jdbc.queryForList("SELECT * FROM discovery_paper_archive_attempt ORDER BY id")
            val mailBefore = listOf("mail_record", "expert_contact", "mail_sender_account").associateWith {
                f.jdbc.queryForList("SELECT * FROM $it ORDER BY id") }
            val props = f.esProperties.copy(baseUrl = "http://127.0.0.1:${server.address.port}")
            val http = RestTemplate()
            val indices = com.weibo.talentintroduction.expert.service.ExpertIndexService(props, http, f.mapper)
            val writer = com.weibo.talentintroduction.expert.service.ExpertIndexWriterService(http, props, indices, f.mapper,
                com.weibo.talentintroduction.expert.service.ExpertPromotionAuditService(app.getBean(com.weibo.talentintroduction.expert.repository.ExpertApplicationPromotionRepository::class.java)),
                app.getBean(com.weibo.talentintroduction.campaign.repository.ExpertContactRepository::class.java))
            Mockito.`when`(f.filters.getEmailValidationConfig()).thenReturn(EmailValidationProperties(enableMxCheck = false))
            val validator = com.weibo.talentintroduction.expert.service.EmailValidationService(EmailValidationProperties(enableMxCheck = false),
                app.getBean(com.weibo.talentintroduction.expert.repository.EmailValidationCacheRepository::class.java),
                org.springframework.core.io.DefaultResourceLoader(), com.weibo.talentintroduction.expert.service.MxLookupClient {
                    providers++; error("No DNS/provider allowed") }, f.filters)
            val registration = DiscoveryVerificationRegistrationService(f.props, f.repository, writer, f.policy, f.admissions,
                f.jdbc, TransactionTemplate(manager), f.apolloSettings, f.clock, papers = papers)
            val beans = DefaultListableBeanFactory()
            fun <T : Any> provider(type: Class<T>) = beans.getBeanProvider(type)
            val discovery = ExpertDiscoveryService(EuropePmcDataSource(http, EuropePmcProperties(enabled = false)),
                provider(OpenAlexDataSource::class.java), provider(CrossrefDataSource::class.java), provider(ArxivDataSource::class.java),
                provider(PmcOaDataSource::class.java), provider(OrcidDataSource::class.java), provider(CoreDataSource::class.java),
                validator, f.eligibility, writer, indices, Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertRevalidationService::class.java),
                com.weibo.talentintroduction.expert.service.ExpertSearchService(http, props, indices),
                com.weibo.talentintroduction.expert.service.ExpertClassificationService(), http, props,
                ExpertDiscoveryProperties(maxAuthorsPerRun = 500), OpenAlexProperties(enabled = false), f.mapper,
                com.weibo.talentintroduction.task.service.TaskProgressStore(app.getBean(com.weibo.talentintroduction.task.repository.TaskProgressLogRepository::class.java), f.mapper),
                app.getBean(com.weibo.talentintroduction.discovery.repository.DiscoverySourceCursorRepository::class.java),
                ExpertAcademicEnrichmentJobService(app.getBean(com.weibo.talentintroduction.discovery.repository.ExpertAcademicEnrichmentJobRepository::class.java)),
                app.getBean(com.weibo.talentintroduction.discovery.repository.ExpertAcademicEnrichmentJobRepository::class.java),
                Executor { it.run() }, EuropePmcProperties(enabled = false), verificationRegistration = registration)
            val workerBeans = DefaultListableBeanFactory()
            val queries = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props)
            val scan = DiscoveryReviewScanService(f.admissions, f.policy, writer, http, props, indices, f.mapper)
            val rechecks = DiscoveryRecheckService(f.repository, f.admissions, writer, scan, f.policy, f.apolloSettings, f.props,
                tasks, TransactionTemplate(manager), queries, f.commits, Executor { it.run() },
                workerBeans.getBeanProvider(DiscoveryVerificationWorker::class.java), papers, f.clock)
            val target = HistoricalSqlBindingsTarget(tasks, registration, f.repository, f.admissions, writer, f.policy, rechecks)
            val config = mapOf("admission" to f.mapper.writeValueAsString(f.policy.evaluate(ruleSnapshot().profile).configSnapshot))
            val scope = HistoricalReplayFixtures.scope(retained, config = config)
            val scopePath = root.resolve("scope.json")
            DiscoveryVerificationReplayTool.atomicWrite(scopePath, f.mapper.writeValueAsBytes(scope))
            command(listOf("freeze", "--scope", scopePath.toString(), "--output", root.resolve("frozen").toString()))
            val frozen = DiscoveryHistoricalReplayTool.load(root.resolve("frozen/manifest.json"))
            val options = listOf("--manifest", frozen.directory.resolve("manifest.json").toString(), "--manifest-hash", frozen.hash)
            val writesBefore = writes
            command(listOf("compare") + options); val report = DiscoveryHistoricalReplayTool.writeComparison(frozen)
            val bytes = Files.readAllBytes(report)
            command(listOf("compare") + options); command(listOf("report") + options)
            check(bytes.contentEquals(Files.readAllBytes(report)) && writes == writesBefore)
            command(listOf("apply-bindings", "--manifest", frozen.directory.resolve("manifest.json").toString(), "--manifest-hash", "0".repeat(64)), expected = 2)
            val backfill = DiscoveryPaperBackfillService(discovery, analyzer, target)
            var checks = 0
            val first = backfill.apply(frozen, frozen.hash) { ++checks > 2 }
            check(first.cancelled && first.newRaw == 1)
            val second = backfill.apply(frozen, frozen.hash)
            check(second.newRaw == 0 && second.failed == 1 && second.skippedCompleted == 1)
            val repeated = backfill.apply(frozen, frozen.hash)
            check(repeated.newRaw == 0 && repeated.skippedCompleted == 3 && repeated.failed == 1)
            val item = f.repository.findItemsByDocIds(listOf(com.weibo.talentintroduction.expert.service.ExpertIdGenerator.generate(null, "ada@fixture.edu"))).single()
            val execution = f.mapper.readTree(item.snapshotJson).path("executionContext")
            check(f.mapper.treeToValue(execution, VerificationExecutionContext::class.java) ==
                VerificationExecutionContext(scope.evaluationAt, listOf(retained.first().source)))
            check(item.ownerExecutionId != 28671L && item.requestCount == 0)
            val deny = RestTemplate().apply { interceptors.add(org.springframework.http.client.ClientHttpRequestInterceptor { _, _, _ -> providers++; error("Provider dispatch forbidden") }) }
            val worker = DiscoveryVerificationWorker(f.props, f.repository, OrcidVerificationEvidenceClient(f.props, deny, f.clock),
                ApolloPeopleSearchClient(f.props, deny, f.clock), QualificationEvidenceParser(f.clock), f.rules, f.commits,
                beans.getBeanProvider(RegistrationRecovery::class.java), f.clock, DiscoveryVerificationTraceRepository(f.jdbc), papers)
            try {
                val lease = f.repository.claimDue(f.clock.instant(), 120)!!
                check(lease.id == item.id); worker.process(lease)
            } finally { worker.close() }
            val done = f.repository.findItem(item.id)!!
            check(done.state == VerificationState.COMPLETED && done.admissionRevision == null)
            check(done.evidenceJson!!.contains(retained.first().source.bodySha256))
            check(f.jdbc.queryForList("SELECT * FROM discovery_verification_item WHERE id<>? ORDER BY id", item.id) == historyBefore)
            check(tasksBefore.all { row -> f.jdbc.queryForList("SELECT * FROM task_execution WHERE id=?", row["id"]).single() == row })
            check(checkpoints == f.jdbc.queryForList("SELECT * FROM discovery_source_cursor ORDER BY id"))
            check(archiveBefore == f.jdbc.queryForList("SELECT * FROM discovery_paper_archive_attempt ORDER BY id"))
            check(mailBefore.all { (table, rows) -> rows == f.jdbc.queryForList("SELECT * FROM $table ORDER BY id") })
            // Existing identities go to an independent READY AUDIT run, never profile replacement or old-item reset.
            val profile = writer.discoveryProfile(item.expertDocId, f.raws.getValue(item.expertDocId))
            val snapshot = DiscoveryVerificationAdmissionGate.snapshot(item.expertDocId, profile, f.policy.evaluate(profile).configSnapshot)
            val actualPaper = papers.collect(snapshot, listOf(retained.first().source), RecheckEvidenceMode.REUSE, scope.evaluationAt)
            check(actualPaper.sources.single().evidence.map { it.field }.toSet().containsAll(setOf("education", "birthDate"))) {
                "Actual fixture paper fields=${actualPaper.sources.single().evidence.map { it.field }} reasons=${actualPaper.reasonCodes}"
            }
            val existing = DiscoveryHistoricalReplayTool.Expert(item.expertDocId, snapshot.identityHash, snapshot.factFingerprint,
                f.admissions.findAdmission(item.expertDocId)?.revision, profile)
            val existingFrozen = DiscoveryHistoricalReplayTool.freeze(HistoricalReplayFixtures.scope(listOf(retained.first()),
                listOf(existing), config), root.resolve("existing-frozen"))
            val existingBefore = f.raws.toMap()
            val existingResult = backfill.apply(existingFrozen, existingFrozen.hash)
            check(existingResult.newRaw == 0 && existingResult.receipts.single().recheckRunIds.size == 1)
            check(f.repository.findItem(item.id) == done && existingBefore == f.raws)
            val altered = existing.copy(identityHash = "0".repeat(64))
            val wrongIdentity = DiscoveryHistoricalReplayTool.freeze(HistoricalReplayFixtures.scope(listOf(retained.first()),
                listOf(altered), config), root.resolve("wrong-identity"))
            try { backfill.apply(wrongIdentity, wrongIdentity.hash); error("Changed identity must reject") }
            catch (e: IllegalArgumentException) { check(e.message == "CURRENT_IDENTITY_OR_REVISION_CHANGED") }
            val enforce = rechecks.prepare(RecheckPrepareRequest(UUID.randomUUID().toString(), docIds = listOf(item.expertDocId),
                mode = VerificationMode.ENFORCE, evidenceMode = RecheckEvidenceMode.REUSE), "isolated-archived-fixture")
            check(enforce.phase == RecheckPhase.READY)
            val enforceWorker = DiscoveryVerificationWorker(f.props, f.repository, OrcidVerificationEvidenceClient(f.props, deny, f.clock),
                ApolloPeopleSearchClient(f.props, deny, f.clock), QualificationEvidenceParser(f.clock), f.rules, f.commits,
                beans.getBeanProvider(RegistrationRecovery::class.java), f.clock, DiscoveryVerificationTraceRepository(f.jdbc), papers)
            workerBeans.registerSingleton("archivedWorker", enforceWorker)
            rechecks.start(enforce.runId, RecheckStartRequest(enforce.rosterHash!!, enforce.revision))
            try {
                val lease = f.repository.claimDue(f.clock.instant(), 120)!!
                check(lease.ownerExecutionId == enforce.runId); enforceWorker.process(lease)
            } finally { enforceWorker.close() }
            rechecks.reconcile()
            val enforced = f.repository.findHistoryByDocIds(listOf(item.expertDocId)).single { it.ownerExecutionId == enforce.runId }
            check(enforced.admissionRevision != null && enforced.qualification == VerificationQualification.MET &&
                enforced.projectionState == VerificationProjection.PROJECTED && f.candidates.containsKey(item.expertDocId)) {
                "Archived fixture outcome=${enforced.outcome} qualification=${enforced.qualification} state=${enforced.state} reasons=${enforced.reasonsJson}"
            }
            check(DiscoveryVerificationTraceRepository(f.jdbc).read(enforced.id).steps.map { it.stepNo }.toSet() == (1..8).toSet())
            val payload = f.mapper.readValue(enforced.evidenceJson, VerificationWorkerEvidence::class.java)
            val expectedFacts = payload.evidence.filter { it.identityHash == enforced.identityHash && it.factFingerprint == enforced.factFingerprint }
            val currentPaperFacts = payload.executionContext!!.paper!!.sources.single().evidence
            check(currentPaperFacts.size == 2 && expectedFacts.containsAll(currentPaperFacts))
            val detail = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props).recheckItem(enforce.runId, enforced.id)
            val savedEvidence = detail.evidence!!
            check(savedEvidence.path("state").asText() == "RECORDED")
            val shown = savedEvidence.path("items").toList()
            check(shown.size == expectedFacts.size && shown.all { !it.path("excerpt").asText().isBlank() })
            expectedFacts.zip(shown).forEach { (fact, view) ->
                check(view.path("excerpt").asText() == fact.excerpt && view.path("sourceUrl").asText() == fact.sourceUrl)
                check(view.path("responseSha256").asText() == fact.responseSha256 && view.path("jsonPointer").asText() == fact.jsonPointer)
                check(view.path("fetchedAt").asText() == fact.fetchedAt.toString())
                check(view.path("temporalScope").asText() == fact.temporalScope.name)
                check(view.path("page").isNull && view.path("paragraph").asInt() > 0)
                check(view.path("sourceRef") == f.mapper.valueToTree<JsonNode>(retained.first().source))
            }
            val beforeRead = f.jdbc.queryForList("SELECT * FROM discovery_verification_item WHERE id=?", enforced.id)
            Runtime(f, manager, tasks).use { http ->
                check(http.evidenceDetail(enforce.runId, enforced.id).path("evidence") ==
                    f.mapper.readTree(f.mapper.writeValueAsString(detail.evidence)))
            }
            check(beforeRead == f.jdbc.queryForList("SELECT * FROM discovery_verification_item WHERE id=?", enforced.id))
            println("RECHECK_REPAIR paperFacts=${currentPaperFacts.size} savedFacts=${expectedFacts.size} parserPersistedQueryHttpExact=true xmlPageUnknown=true frozenReaderRefs=true queryWrites=0")
            val cliPaper = HistoricalReplayFixtures.paper(root, 900005, email = "cli@fixture.edu")
            val cliFrozen = DiscoveryHistoricalReplayTool.freeze(HistoricalReplayFixtures.scope(listOf(cliPaper), config = config), root.resolve("cli-apply"))
            val cliOptions = listOf("--manifest", cliFrozen.directory.resolve("manifest.json").toString(), "--manifest-hash", cliFrozen.hash)
            val env = mapOf("REPLAY_DB_URL" to app.environment.getRequiredProperty("spring.datasource.url"),
                "REPLAY_DB_USERNAME" to app.environment.getRequiredProperty("spring.datasource.username"),
                "REPLAY_DB_PASSWORD" to app.environment.getRequiredProperty("spring.datasource.password"),
                "REPLAY_ES_URL" to props.baseUrl, "REPLAY_ES_USERNAME" to "u", "REPLAY_ES_PASSWORD" to "p",
                "ES_RAW_INDEX_NAME" to "raw", "ES_CANDIDATE_INDEX_NAME" to "candidate", "ES_APPLICATION_INDEX_NAME" to "application",
                "CANDIDATE_REQUIRE_DOCTORAL_DEGREE" to "true", "CANDIDATE_REQUIRE_VALID_EMAIL" to "false",
                "CANDIDATE_ENABLE_AGE_FILTER" to "true", "EMAIL_ENABLE_MX_CHECK" to "false")
            f.jdbc.update("DELETE FROM eligibility_filter_setting")
            val cliWritesBefore = writes
            command(listOf("apply-bindings") + cliOptions, environment = env)
            check(writes == cliWritesBefore + 1)
            command(listOf("apply-bindings") + cliOptions, environment = env)
            check(writes == cliWritesBefore + 1)
            check(mailBefore.all { (table, rows) -> rows == f.jdbc.queryForList("SELECT * FROM $table ORDER BY id") })
            check(providers == 0 && unexpected == 0) { "Forbidden provider requests=$providers unexpected fixture requests=$unexpected" }
            println("RECHECK10_RUNTIME isolatedMySQL=true strictFixtureES=true productionParserConsumerWorkerGate=true firstNewRaw=${first.newRaw} repeatNewRaw=${repeated.newRaw} actualFixtureAdmitted=1 candidateSync=1 partialMissing=1 frozenContext=true providerHttp=0 oldHistoryCheckpointMailUnchanged=true")
        }
        override fun close() { server.stop(0); check(root.toFile().deleteRecursively()) }
    }

    private class Runtime(val f: VerificationFlowFixture, val manager: DataSourceTransactionManager,
        val tasks: TaskExecutionRepository) : AutoCloseable {
        private val root = Files.createTempDirectory("discovery-recheck-http-").toFile()
        private val tx = TransactionTemplate(manager)
        private val prepares = Executors.newSingleThreadExecutor()
        private val workerBeans = DefaultListableBeanFactory()
        private val trace = DiscoveryVerificationTraceRepository(f.jdbc)
        private val archiveRepo = DiscoveryPaperArchiveRepository(f.jdbc, manager)
        private val archive = DiscoveryPaperArchiveService(archiveRepo, DiscoveryPaperArchiveProperties(root.resolve("archive").absolutePath),
            DiscoverySampleProperties(directory = root.resolve("samples").absolutePath), f.mapper)
        private val papers = PaperVerificationEvidenceService(archiveRepo, archive, Mockito.mock(PaperBodyAnalyzer::class.java), f.mapper)
        private val denyProviders = RestTemplate().apply {
            interceptors.add(org.springframework.http.client.ClientHttpRequestInterceptor { _, _, _ ->
                providerCalls++; throw IllegalStateException("Unexpected provider dispatch in REUSE")
            })
        }
        private var providerCalls = 0
        private val worker = DiscoveryVerificationWorker(f.props, f.repository, OrcidVerificationEvidenceClient(f.props, denyProviders, f.clock),
            ApolloPeopleSearchClient(f.props, denyProviders, f.clock), QualificationEvidenceParser(f.clock), f.rules, f.commits,
            DefaultListableBeanFactory().getBeanProvider(RegistrationRecovery::class.java), f.clock, trace, papers)
        private val scanHttp = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private var scrollDocs = emptyList<Pair<String, Map<String, Any?>>>()
        private var offset = 0
        private var failScroll = false
        private var blockScroll: CountDownLatch? = null
        private var scrollEntered: CountDownLatch? = null
        private val scan = run {
            scanHttp.createContext("/") { exchange ->
                scrollEntered?.countDown(); blockScroll?.await(30, TimeUnit.SECONDS)
                val search = exchange.requestURI.path.endsWith("/_search")
                if (search) { scrollDocs = f.raws.entries.map { it.key to it.value }.sortedBy { it.first }; offset = 0 }
                val status: Int
                val body: String
                if (exchange.requestMethod == "DELETE") { status = 200; body = "{}" }
                else if (failScroll && offset >= 500) { status = 503; body = "{}" }
                else {
                    status = 200
                    val docs = scrollDocs.drop(offset).take(500); offset += docs.size
                    body = f.mapper.writeValueAsString(mapOf("_scroll_id" to "fixed-fixture-scroll", "_shards" to mapOf("failed" to 0),
                        "timed_out" to false, "hits" to mapOf("total" to mapOf("value" to scrollDocs.size, "relation" to "eq"),
                            "hits" to docs.map { (id, source) -> mapOf("_id" to id, "_source" to source, "_seq_no" to 1, "_primary_term" to 1) })))
                }
                val bytes = body.toByteArray(); exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            }
            scanHttp.start()
            DiscoveryReviewScanService(f.admissions, f.policy, f.writer, RestTemplate(),
                f.esProperties.copy(baseUrl = "http://127.0.0.1:${scanHttp.address.port}"), f.indices, f.mapper)
        }
        private val queries = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props)
        private val service: DiscoveryRecheckService
        private val tomcat = Tomcat()
        private val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        private val anonymous = HttpClient.newHttpClient()
        private val base: String
        private var httpRequests = 0
        init {
            workerBeans.registerSingleton("worker", worker)
            service = newService(prepares)
            val auth = Mockito.mock(AuthService::class.java)
            val now = LocalDateTime.now()
            Mockito.`when`(auth.findUser("operator")).thenReturn(AdminUser(1, "operator", "fixture", false, createdAt = now, updatedAt = now))
            tomcat.setBaseDir(root.resolve("tomcat").absolutePath); tomcat.setPort(0); tomcat.connector
            val context = tomcat.addContext("", root.absolutePath)
            val web = AnnotationConfigWebApplicationContext()
            web.register(RecheckRuntimeMvc::class.java)
            web.addBeanFactoryPostProcessor { factory ->
                factory.registerSingleton("objectMapper", f.mapper)
                factory.registerSingleton("authService", auth)
                factory.registerSingleton("recheckController", DiscoveryRecheckController(service, queries))
                factory.registerSingleton("manualController", DiscoveryReviewController(f.review))
            }
            Tomcat.addServlet(context, "dispatcher", DispatcherServlet(web)).loadOnStartup = 1
            context.addServletMappingDecoded("/", "dispatcher")
            Tomcat.addServlet(context, "fixture-session", object : HttpServlet() {
                override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
                    request.getSession(true).setAttribute(AuthSessionKeys.USERNAME, "operator")
                    response.status = 200; response.writer.write("{}")
                }
            })
            context.addServletMappingDecoded("/fixture-session", "fixture-session")
            tomcat.start()
            base = "http://127.0.0.1:${tomcat.connector.localPort}"
            request("GET", "/fixture-session")
        }

        private fun newService(executor: Executor, props: DiscoveryVerificationProperties = f.props) = DiscoveryRecheckService(
            f.repository, f.admissions, f.writer, scan, f.policy, f.apolloSettings, props, tasks, tx, queries,
            f.commits, executor, workerBeans.getBeanProvider(DiscoveryVerificationWorker::class.java), papers, f.clock)

        private fun request(method: String, path: String, body: Any? = null, expected: Int = 200, loggedIn: Boolean = true): JsonNode {
            httpRequests++
            val builder = HttpRequest.newBuilder(URI(base + path)).header("Content-Type", "application/json")
            builder.method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else
                HttpRequest.BodyPublishers.ofString(if (body is String) body else f.mapper.writeValueAsString(body)))
            val response = (if (loggedIn) client else anonymous).send(builder.build(), HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() == expected) { "$method $path expected=$expected actual=${response.statusCode()} body=${response.body()}" }
            return if (response.body().isBlank()) f.mapper.createObjectNode() else f.mapper.readTree(response.body())
        }
        private fun api(method: String, path: String, body: Any? = null, expected: Int = 200, loggedIn: Boolean = true) =
            request(method, "/api/discovery/rechecks$path", body, expected, loggedIn)
        private fun prepare(ids: List<String>? = null, mode: VerificationMode = VerificationMode.AUDIT,
            key: String = UUID.randomUUID().toString()): JsonNode = api("POST", "/prepare",
            RecheckPrepareRequest(key, if (ids == null) RecheckScope.ALL_MATCHING else RecheckScope.IDS,
                ids.orEmpty(), mode = mode), 202)
        private fun ready(id: Long, phase: String = "READY"): JsonNode {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (true) {
                val view = api("GET", "/$id")
                if (view.path("phase").asText() == phase &&
                    view.path("prepareTask").path("status").asText() == if (phase == "READY") "SUCCESS" else phase) return view
                check(view.path("phase").asText() in setOf("PREPARING", phase)) { "Unexpected prepare terminal: $view" }
                check(System.nanoTime() < deadline) { "Prepare did not settle" }
                Thread.sleep(20)
            }
        }
        private fun start(view: JsonNode): JsonNode = api("POST", "/${view.path("runId").asLong()}/start",
            RecheckStartRequest(view.path("rosterHash").asText(), view.path("revision").asLong()))
        private fun processAll(): Int {
            var count = 0
            while (true) {
                val lease = f.repository.claimDue(f.clock.instant(), 120) ?: break
                worker.process(lease); count++
            }
            service.reconcile()
            return count
        }
        private fun profile(doc: String, cached: Boolean = false) {
            val p = ruleSnapshot().profile.copy(esDocId = doc, tags = listOf("discovered"), dataSource = "ORCID",
                researchFieldIds = listOf("22"), lastPublicationYear = 2026)
            @Suppress("UNCHECKED_CAST") val source = f.mapper.convertValue(p, Map::class.java) as Map<String, Any?>
            f.raws[doc] = source
            if (cached) {
                val snapshot = DiscoveryVerificationAdmissionGate.snapshot(doc, p, f.policy.evaluate(doc, p).configSnapshot)
                f.repository.createRun(800001, VerificationMode.AUDIT, false, "{}", f.clock.instant())
                val row = f.repository.reserveItem(VerificationReservation(800001, doc, snapshot.identityHash, VerificationMode.AUDIT.policyVersion,
                    "ORCID", doc, f.mapper.writeValueAsString(snapshot), snapshot.factFingerprint), f.clock.instant())
                f.repository.activateItem(row.id, 1, f.clock.instant())
                val lease = f.repository.claimDue(f.clock.instant(), 120)!!
                val fact = ruleJob("Professor").copy(identityHash = snapshot.identityHash, factFingerprint = snapshot.factFingerprint)
                val payload = VerificationWorkerEvidence(evidence = listOf(fact), ruleVersion = 3)
                check(f.repository.saveEvidenceAndOutcome(row.id, lease.leaseToken!!, lease.generation,
                    VerificationResult(VerificationState.COMPLETED, VerificationOutcome.PENDING, VerificationQualification.UNKNOWN,
                        VerificationRoute.NONE, f.mapper.writeValueAsString(payload), "[]"), f.clock.instant()))
            }
        }

        fun evidenceDetail(runId: Long, itemId: Long): JsonNode = api("GET", "/$runId/items/$itemId/steps")

        /** Capture the actual production component's POST, then validate it through the real servlet and SQL. */
        private fun componentWire(scope: String, filter: Map<String, String>): String {
            val script = """
                const fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
                const file = path.resolve('src/test/js/discoveryRecheck.test.js');
                const definitions = fs.readFileSync(file, 'utf8').split("test('approved exact root")[0];
                const sandbox = { require: require('node:module').createRequire(file), __dirname: path.dirname(file),
                    console, process, setImmediate, URL, URLSearchParams, AbortController, DOMException };
                vm.runInNewContext(definitions + `;(async () => {
                    const h = harness(); h.setFilter(JSON.parse(process.argv[2]));
                    h.setSelected(['fixture-0001', 'fixture-0002']);
                    for (const input of h.root.querySelectorAll('input[name="rr-scope"]')) input.checked = input.value === process.argv[1];
                    h.find('open').click(); await flush(); h.find('prepare').click(); await flush();
                    const captured = h.calls.find(c => c.url.endsWith('/prepare'));
                    if (!captured) throw Error('No component POST');
                    process.stdout.write(JSON.stringify(captured.body)); h.view.unmount();
                })().catch(e => { console.error(e); process.exitCode = 1; });`, sandbox);
            """.trimIndent()
            val process = ProcessBuilder("/opt/homebrew/bin/node", "-e", script, scope, f.mapper.writeValueAsString(filter)).start()
            val body = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "Component wire capture failed: $error" }
            return body
        }
        private fun business() = f.mapper.writeValueAsString(listOf(
            f.jdbc.queryForList("SELECT * FROM expert_discovery_admission ORDER BY expert_doc_id"),
            f.jdbc.queryForList("SELECT * FROM expert_discovery_review_item ORDER BY id"), f.raws, f.candidates, f.applications))

        fun exercise() {
            for (path in listOf("/runs", "/latest?docIds=fixture-1", "/1", "/1/items", "/1/items/1/steps", "/1/manifest"))
                api("GET", path, expected = 401, loggedIn = false)
            for (path in listOf("/prepare", "/1/start", "/1/cancel", "/1/sync-retry")) api("POST", path, "{}", 401, false)
            api("POST", "/prepare", """{"requestKey":"${UUID.randomUUID()}","scope":"UNKNOWN"}""", 400)
            api("GET", "/runs?size=21", expected = 400)
            api("GET", "/latest?docIds=" + (1..101).joinToString(",") { "id-$it" }, expected = 400)
            (1..1001).forEach { profile("fixture-%04d".format(it)) }
            val defaultFilter = mapOf("q" to "", "issue" to "", "decision" to "")
            val nonemptyFilter = mapOf("q" to "Ada", "issue" to "IDENTITY_UNRESOLVED", "decision" to "NEEDS_REVIEW")
            for ((scope, filter) in listOf("IDS" to nonemptyFilter, "ALL_MATCHING" to defaultFilter, "ALL_MATCHING" to nonemptyFilter, "ALL_PENDING" to nonemptyFilter)) {
                val wire = componentWire(scope, filter)
                val acceptedWire = api("POST", "/prepare", wire, 202)
                val fixed = ready(acceptedWire.path("runId").asLong())
                check(fixed.path("scope").asText() == scope && fixed.path("mode").asText() == "AUDIT" &&
                    fixed.path("evidenceMode").asText() == "REUSE")
                if (filter == defaultFilter || scope == "IDS") check(fixed.path("counts").path("total").asInt() == if (scope == "IDS") 2 else 1001)
                if (scope == "ALL_MATCHING" && filter == nonemptyFilter) check(fixed.path("filter").path("q").asText() == "Ada")
                api("POST", "/${fixed.path("runId").asLong()}/cancel")
                println("RECHECK_REPAIR productionComponentWire=true scope=$scope emptyFilter=${filter == defaultFilter} http=202 phase=READY")
            }
            for (invalid in listOf(
                """{"scope":"IDS","docIds":["fixture-0001"],"filter":{"q":"Ada"}}""",
                """{"scope":"IDS","docIds":["invalid/id"]}""",
                """{"scope":"ALL_MATCHING","filter":{"decision":""}}""",
                """{"scope":"ALL_MATCHING","filter":{"decision":"INVALID"}}""")) {
                val json = f.mapper.readTree(invalid) as com.fasterxml.jackson.databind.node.ObjectNode
                json.put("requestKey", UUID.randomUUID().toString()); api("POST", "/prepare", json, 400)
            }
            val previousHistoryCount = api("GET", "/runs?docId=fixture-0001").path("total").asInt()
            val before = business()
            val key = UUID.randomUUID().toString()
            val accepted = prepare(key = key); val id = accepted.path("runId").asLong()
            val view = ready(id)
            check(id > 0 && view.path("taskId").asLong() == id && view.path("actor").asText() == "operator")
            check(view.path("mode").asText() == "AUDIT" && view.path("evidenceMode").asText() == "REUSE")
            check(view.path("counts").path("total").asInt() == 1001 && view.path("counts").path("queued").asInt() == 1001)
            check(tasks.findById(id).orElseThrow().status == "SUCCESS")
            check(f.jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_step WHERE item_id IN (SELECT id FROM discovery_verification_item WHERE owner_execution_id=?)", Long::class.java, id) == 0L)
            check(before == business() && providerCalls == 0)
            check(prepare(key = key).path("runId").asLong() == id)
            check(prepare(key = UUID.randomUUID().toString()).path("runId").asLong() == id)
            check(api("GET", "/active").single().path("runId").asLong() == id)
            api("POST", "/prepare", RecheckPrepareRequest(UUID.randomUUID().toString(), RecheckScope.ALL_MATCHING,
                evidenceMode = RecheckEvidenceMode.REFRESH), 409)
            api("POST", "/prepare", RecheckPrepareRequest(key, RecheckScope.ALL_MATCHING, mode = VerificationMode.ENFORCE), 409)
            val disabled = newService(prepares, DiscoveryVerificationProperties(enabled = false))
            assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
                disabled.start(id, RecheckStartRequest(view.path("rosterHash").asText(), view.path("revision").asLong()))
            }
            check(api("GET", "/$id").path("phase").asText() == "READY")
            profile("later-raw")
            val fixedManifest = (0..10).flatMap { page -> api("GET", "/$id/manifest?page=$page&size=100").path("items").map { it.path("docId").asText() } }
            check(fixedManifest.size == 1001 && fixedManifest.toSet().size == 1001 && "later-raw" !in fixedManifest)
            start(view); start(view)
            val batches = f.jdbc.queryForList("SELECT batch_number,COUNT(*) AS n FROM discovery_verification_item WHERE owner_execution_id=? GROUP BY batch_number ORDER BY batch_number", id)
            check(batches.map { (it["n"] as Number).toInt() } == listOf(500,500,1))
            check(processAll() == 1001)
            val done = api("GET", "/$id")
            check(done.path("phase").asText() == "COMPLETED" && done.path("prepareTask").path("status").asText() == "SUCCESS")
            check(done.path("counts").path("processed").asInt() == 1001 && done.path("counts").path("actualAdmitted").asInt() == 0)
            check(providerCalls == 0)
            // The only extra RAW is the explicit fixture inserted after prepare, not a pipeline write.
            f.raws.remove("later-raw"); check(before == business())
            val item = api("GET", "/$id/items?size=20").path("items").first()
            check(item.path("trace").path("steps").size() == 8)
            check(!item.path("before").path("revision").isNumber)
            api("GET", "/$id/items/${item.path("itemId").asLong()}/steps")
            check(api("GET", "/latest?docIds=fixture-0001").path("fixture-0001").path("runId").asLong() == id)
            check(api("GET", "/runs?docId=fixture-0001").path("total").asInt() == previousHistoryCount + 1)
            println("RECHECK08_RUNTIME roster=1001 batches=500,500,1 frozenAfterInsert=true processed=1001 prepareProviderHttp=0 auditProviderHttp=0 auditBusinessWrites=0 realTaskBinding=true")

            // Partial scroll failure is retained as FAILED, never startable or inputClosed.
            failScroll = true
            val failed = prepare(); val failedId = failed.path("runId").asLong(); val failedView = ready(failedId, "FAILED")
            failScroll = false
            check(!failedView.path("inputClosed").asBoolean() && failedView.path("counts").path("total").asInt() == 0) { "Incomplete scan must not create executable items: $failedView" }
            check(failedView.path("scanned").asLong() == 500L && failedView.path("preparationStage").asText() == "SCANNING")
            check(tasks.findById(failedId).orElseThrow().status == "FAILED")
            api("POST", "/$failedId/start", RecheckStartRequest("a".repeat(64), failedView.path("revision").asLong()), 409)
            api("GET", "/$failedId/items/${item.path("itemId").asLong()}/steps", expected = 404)
            api("POST", "/$failedId/sync-retry", RecheckSyncRequest(listOf(item.path("itemId").asLong())), 404)

            // A persisted interrupted prepare is not mistaken for task SUCCESS or a resumable roster.
            val delayed = ArrayList<Runnable>()
            val preparing = newService(Executor { delayed.add(it) }).prepare(RecheckPrepareRequest(UUID.randomUUID().toString(), docIds = listOf("fixture-0001")), "operator")
            f.jdbc.update("UPDATE task_execution SET status='INTERRUPTED' WHERE id=?", preparing.runId)
            service.reconcile()
            check(api("GET", "/${preparing.runId}").path("phase").asText() == "INTERRUPTED")
            delayed.single().run()
            check(api("GET", "/${preparing.runId}").path("phase").asText() == "INTERRUPTED")
            val preCancel = newService(Executor { }).prepare(RecheckPrepareRequest(UUID.randomUUID().toString(), docIds = listOf("fixture-0001")), "operator")
            api("POST", "/${preCancel.runId}/cancel")
            check(api("GET", "/${preCancel.runId}").path("phase").asText() == "CANCELLED")
            val readyCancel = ready(prepare(listOf("fixture-0001")).path("runId").asLong())
            api("POST", "/${readyCancel.path("runId").asLong()}/cancel")
            println("RECHECK08_RUNTIME partialFreeze=FAILED/scanned500/items0/notClosed interruptedPrepare=INTERRUPTED preparingCancel=CANCELLED readyCancel=CANCELLED crossRun404=true")

            // Use the unchanged manual HTTP endpoints, then verify the new ENFORCE path protects their receipt.
            profile("manual", true)
            val staged = request("POST", "/api/discovery/review/batches/prepare", mapOf("docIds" to listOf("manual"), "action" to "APPROVE"))
            request("POST", "/api/discovery/review/batches/${staged.path("batchKey").asText()}/confirm", mapOf("batchHash" to staged.path("batchHash").asText()))
            val manualBefore = f.admissions.findAdmission("manual")!!
            check(manualBefore.decision == "MANUAL_APPROVED") { "Manual HTTP receipt: $manualBefore" }
            val manualRun = ready(prepare(listOf("manual"), VerificationMode.ENFORCE).path("runId").asLong())
            start(manualRun)
            val manualProcessed = processAll()
            check(manualProcessed == 1) { "Manual recheck processed=$manualProcessed" }
            check(f.admissions.findAdmission("manual") == manualBefore) { "Manual receipt changed: ${f.admissions.findAdmission("manual")}" }
            val manualItems = api("GET", "/${manualRun.path("runId").asLong()}/items")
            check(manualItems.path("items").first().path("allReasons").any { it.asText() == "REVIEW_PROTECTED" }) {
                "Manual protection trace: $manualItems"
            }

            profile("would-admit", true)
            val audit = ready(prepare(listOf("would-admit")).path("runId").asLong())
            val auditBusiness = business(); start(audit)
            val auditProcessed = processAll()
            check(auditProcessed == 1) { "AUDIT processed=$auditProcessed" }
            check(auditBusiness == business()) { "AUDIT changed business state" }
            val auditDone = api("GET", "/${audit.path("runId").asLong()}")
            check(auditDone.path("counts").path("wouldAdmit").asInt() == 1 && auditDone.path("counts").path("actualAdmitted").asInt() == 0) {
                "AUDIT qualification counts: $auditDone; items=${api("GET", "/${audit.path("runId").asLong()}/items")}"
            }
            println("RECHECK08_RUNTIME oldManualHttp=MANUAL_APPROVED protected=REVIEW_PROTECTED auditWouldAdmit=1 auditActualAdmitted=0")

            commitCancelRace(false)
            commitCancelRace(true)
            profile("lease-restart", true)
            val recovery = ready(prepare(listOf("lease-restart")).path("runId").asLong()); start(recovery)
            val old = f.repository.claimDue(f.clock.instant(), 120)!!
            f.clock.now = f.clock.now.plusSeconds(121)
            val reclaimed = f.repository.claimDue(f.clock.instant(), 120)!!
            check(reclaimed.id == old.id && reclaimed.requestCount == old.requestCount && reclaimed.generation > old.generation)
            worker.process(reclaimed); service.reconcile()
            check(api("GET", "/${recovery.path("runId").asLong()}").path("rosterHash") == recovery.path("rosterHash"))
            check(providerCalls == 0)
            println("RECHECK08_RUNTIME PASS actualServletHttp=true httpRequests=$httpRequests isolatedMySQL=true workerGateCommitTrace=true providerCalls=0 runningRecoveryHashAndRequestCount=true productionWrites=0")
        }

        private fun commitCancelRace(cancelFirst: Boolean) {
            val doc = if (cancelFirst) "race-cancel-first" else "race-commit-first"
            profile(doc, true)
            val view = ready(prepare(listOf(doc), VerificationMode.ENFORCE).path("runId").asLong()); start(view)
            val id = view.path("runId").asLong()
            val lease = f.repository.claimDue(f.clock.instant(), 120)!!
            val input = f.commits.prepare(lease)
            val root = f.mapper.readTree(lease.snapshotJson)
            val cache = f.mapper.treeToValue(root.path("reviewInput").path("evidence"), VerificationWorkerEvidence::class.java)
            val snapshot = f.mapper.readValue(lease.snapshotJson, VerificationSnapshot::class.java)
            val payload = cache.copy(ruleVersion = 3, executionContext = VerificationExecutionContext(f.clock.instant()))
            val decision = f.rules.evaluate(snapshot, payload.evidence, null, ruleVersion = 3, evaluatedAt = f.clock.instant())
            check(decision.outcome == VerificationOutcome.DIRECT_CLEAR)
            val proposed = VerificationResult(VerificationState.COMPLETED, decision.outcome, decision.qualification,
                decision.route, f.mapper.writeValueAsString(payload), f.mapper.writeValueAsString(decision.reasonCodes))
            val locked = CountDownLatch(1); val release = CountDownLatch(1); val competing = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val first = pool.submit(Callable {
                    tx.execute {
                        f.repository.lockRunAccepting(id)
                        if (cancelFirst) f.repository.cancelRecheck(id, f.clock.instant())
                        locked.countDown(); check(release.await(30, TimeUnit.SECONDS))
                        if (!cancelFirst) f.commits.commit(lease, proposed, input) else null
                    }
                })
                check(locked.await(30, TimeUnit.SECONDS))
                val second = pool.submit(Callable {
                    competing.countDown()
                    if (cancelFirst) f.commits.commit(lease, proposed, input) else { service.cancel(id); null }
                })
                check(competing.await(30, TimeUnit.SECONDS)); release.countDown()
                val committed = first.get(30, TimeUnit.SECONDS); val late = second.get(30, TimeUnit.SECONDS)
                val row = f.repository.findItem(lease.id)!!
                if (cancelFirst) {
                    check(committed == null && late == null && row.state == VerificationState.CANCELLED)
                    check(f.admissions.findAdmission(doc) == null)
                } else {
                    check(committed?.admissionRevision != null && row.admissionRevision == committed.admissionRevision)
                    check(row.projectionState == VerificationProjection.PENDING)
                    val savedAdmission = f.admissions.findAdmission(doc)
                    f.failProjection = true
                    api("POST", "/$id/sync-retry", RecheckSyncRequest(listOf(row.id)))
                    check(f.repository.findItem(row.id)!!.projectionState == VerificationProjection.ERROR)
                    f.failProjection = false
                    api("POST", "/$id/sync-retry", RecheckSyncRequest(listOf(row.id)))
                    api("POST", "/$id/sync-retry", RecheckSyncRequest(listOf(row.id)))
                    check(f.repository.findItem(row.id)!!.projectionState == VerificationProjection.PROJECTED)
                    check(f.admissions.findAdmission(doc) == savedAdmission)
                    check(f.repository.findAttempts(row.id).isEmpty())
                }
                println("RECHECK08_RUNTIME cancelCommitOrder=${if (cancelFirst) "cancel-first" else "commit-first"} state=${row.state} admitted=${row.admissionRevision != null} projectionOnlyRetry=${!cancelFirst} providerCalls=$providerCalls")
            } finally { release.countDown(); pool.shutdownNow() }
        }

        override fun close() {
            prepares.shutdown(); prepares.awaitTermination(60, TimeUnit.SECONDS)
            worker.close(); tomcat.stop(); tomcat.destroy(); scanHttp.stop(0)
            check(root.deleteRecursively()) { "Temporary runtime directory cleanup failed" }
        }
    }
}
