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
@EnableJdbcRepositories(basePackageClasses = [TaskExecutionRepository::class])
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

    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        @JvmStatic fun main(args: Array<String>) { runRuntime() }

        private fun runRuntime() {
            check(DockerClientFactory.instance().isDockerAvailable) { "Disposable MySQL required; skipping is not PASS" }
            Mysql().withDatabaseName("recheck_runtime").withUsername("test").withPassword("test")
                .withCommand("--log-bin-trust-function-creators=1").use { mysql ->
                mysql.start()
                val app = SpringApplicationBuilder(RecheckRuntimeJdbcConfig::class.java).web(WebApplicationType.NONE)
                    .run("--spring.datasource.url=${mysql.jdbcUrl}", "--spring.datasource.username=${mysql.username}",
                        "--spring.datasource.password=${mysql.password}", "--spring.flyway.placeholder-replacement=false",
                        "--spring.flyway.target=161", "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                        "--spring.rabbitmq.listener.simple.auto-startup=false")
                try {
                    val jdbc = app.getBean(JdbcTemplate::class.java)
                    val manager = DataSourceTransactionManager(app.getBean(DataSource::class.java))
                    val tasks = app.getBean(TaskExecutionRepository::class.java)
                    val props = DiscoveryVerificationProperties(enabled = true, workerEnabled = true, apolloApiKey = "fixture-key")
                    VerificationFlowFixture(jdbc, manager, props).use { f ->
                        Runtime(f, manager, tasks).use { runtime -> runtime.exercise() }
                    }
                } finally { app.close() }
            }
        }
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
            check(api("GET", "/runs?docId=fixture-0001").path("total").asInt() == 1)
            println("RECHECK08_RUNTIME roster=1001 batches=500,500,1 frozenAfterInsert=true processed=1001 prepareProviderHttp=0 auditProviderHttp=0 auditBusinessWrites=0 realTaskBinding=true")

            // Partial scroll failure is retained as FAILED, never startable or inputClosed.
            failScroll = true
            val failed = prepare(); val failedId = failed.path("runId").asLong(); val failedView = ready(failedId, "FAILED")
            failScroll = false
            check(!failedView.path("inputClosed").asBoolean() && failedView.path("counts").path("total").asInt() == 500)
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
            println("RECHECK08_RUNTIME partialFreeze=FAILED/500/notClosed interruptedPrepare=INTERRUPTED preparingCancel=CANCELLED readyCancel=CANCELLED crossRun404=true")

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
