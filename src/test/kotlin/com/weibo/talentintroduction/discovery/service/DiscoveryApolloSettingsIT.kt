package com.weibo.talentintroduction.discovery.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.controller.DiscoveryApolloSettingsController
import com.weibo.talentintroduction.discovery.repository.DiscoveryApolloSettingConflict
import com.weibo.talentintroduction.discovery.repository.DiscoveryApolloSettingRepository
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/** Real V160, real InnoDB races, real loopback HTTP and the existing login interceptor. */
@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
class DiscoveryApolloSettingsIT {
    companion object {
        @JvmStatic @BeforeAll fun start() = ApolloSettingsMysql.start()
        @JvmStatic @AfterAll fun stop() = ApolloSettingsMysql.stop()
    }

    @BeforeEach fun clean() = ApolloSettingsMysql.clean()

    @Test fun `four independent combinations survive recreation and same values preserve revision and UTC milliseconds`() {
        ApolloSettingsFixture().use { f ->
            val baseline = f.nonSettingState()
            assertEquals(0, f.service.resolve().revision)
            assertNull(f.repository.find())
            var revision = 0L
            for ((search, detail) in listOf(false to false, false to true, true to false, true to true)) {
                val saved = f.service.save(revision, search, detail)
                revision++
                assertEquals(revision, saved.revision)
                assertEquals(search, saved.searchEnabled)
                assertEquals(detail, saved.detailEnabled)
                assertEquals(search && detail, saved.effectiveDetails)
                assertEquals(ApolloSettingSource.SAVED, saved.source)
                assertEquals(Instant.parse("2026-10-24T01:02:03.123Z"), saved.updatedAt)
                assertEquals(saved, f.newService().resolve())
                assertEquals(saved, f.newService(Clock.fixed(Instant.parse("2026-10-25T00:00:00Z"), ZoneOffset.UTC))
                    .save(revision, search, detail))
                assertThrows(DiscoveryApolloSettingConflict::class.java) { f.service.save(revision - 1, search, detail) }
            }
            assertEquals("2026-10-24 01:02:03.123000", f.jdbc.queryForObject(
                "SELECT DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') FROM discovery_apollo_setting", String::class.java))
            assertEquals(baseline, f.nonSettingState())
            f.assertNoProviderRequests()
        }
    }

    @Test fun `missing settings use deployment defaults without persisting and availability does not rewrite saved flags`() {
        ApolloSettingsFixture().use { f ->
            for (enabled in listOf(false, true)) for (key in listOf("", "   ", SYNTHETIC_KEY)) {
                for (default in listOf(false, true)) for (worker in listOf(false, true)) {
                    val view = f.newService(properties = properties(enabled, worker, default, key)).resolve()
                    assertEquals(enabled && key.isNotBlank() && default, view.searchEnabled)
                    assertFalse(view.detailEnabled)
                    assertFalse(view.effectiveDetails)
                    assertEquals(worker, view.workerEnabled)
                    assertEquals(enabled, view.serviceEnabled)
                    assertEquals(key.isNotBlank(), view.keyConfigured)
                    assertEquals(0, view.revision)
                    assertNull(view.updatedAt)
                    assertEquals(ApolloSettingSource.DEPLOYMENT_DEFAULT, view.source)
                }
            }
            assertNull(f.repository.find())
            val saved = f.newService(properties = properties(false, false, false, "")).save(0, true, true)
            assertTrue(saved.searchEnabled && saved.detailEnabled)
            assertFalse(saved.effectiveSearch || saved.effectiveDetails)
            assertEquals(1, f.service.resolve().revision)
            assertTrue(f.service.resolve().effectiveDetails)
        }
    }

    @Test fun `two empty row transactions reach insert together and exactly one wins while stale HTTP gets 409`() {
        ApolloSettingsFixture().use { f ->
            f.firstWriteRace()
            val current = f.service.resolve()
            assertEquals(1, current.revision)
            assertEquals(409, f.request("PUT", payload(0, true, false)).status)
            assertEquals(1, f.jdbc.queryForObject("SELECT COUNT(*) FROM discovery_apollo_setting", Int::class.java))
        }
    }

    @Test fun `existing revision updates race through CAS with one winner and preserve no-op semantics`() {
        ApolloSettingsFixture().use { f ->
            f.service.save(0, false, false)
            val pool = Executors.newFixedThreadPool(2)
            val barrier = CyclicBarrier(2)
            try {
                val results = listOf(true to false, false to true).map { (search, detail) -> pool.submit<Int> {
                    barrier.await(10, TimeUnit.SECONDS)
                    try { f.service.save(1, search, detail); 200 } catch (_: DiscoveryApolloSettingConflict) { 409 }
                } }.map { it.get(20, TimeUnit.SECONDS) }.sorted()
                assertEquals(listOf(200, 409), results)
                assertEquals(2, f.service.resolve().revision)
            } finally { pool.shutdownNow() }
        }
    }

    @Test fun `strict authenticated HTTP rejects malformed and unknown values without writing or exposing key`() {
        ApolloSettingsFixture().use { f ->
            val baseline = f.nonSettingState()
            assertEquals(401, f.request("GET", authenticated = false).status)
            assertEquals(401, f.request("PUT", payload(0, true, true), false).status)
            val invalid = listOf("{}", "null", "[]", "{", "{\"revision\":0,\"searchEnabled\":true}",
                "{\"revision\":0,\"searchEnabled\":true,\"detailEnabled\":false,\"source\":\"SAVED\"}",
                "{\"revision\":0,\"searchEnabled\":true,\"detailEnabled\":false,\"apolloApiKey\":\"not-accepted\"}") +
                listOf("-1", "0.0", "0.5", "1e0", "9223372036854775808", "\"0\"", "null", "true").map {
                    "{\"revision\":$it,\"searchEnabled\":true,\"detailEnabled\":false}"
                } + listOf("0", "1", "\"true\"", "null", "[]", "{}").flatMap { value -> listOf(
                    "{\"revision\":0,\"searchEnabled\":$value,\"detailEnabled\":false}",
                    "{\"revision\":0,\"searchEnabled\":true,\"detailEnabled\":$value}") }
            invalid.forEach { assertEquals(400, f.request("PUT", it).status, it.replace(SYNTHETIC_KEY, "[redacted]")) }
            assertNull(f.repository.find())
            assertEquals(200, f.request("PUT", payload(0, false, true)).status)
            val view = f.request("GET").json
            assertFalse(view.path("searchEnabled").booleanValue())
            assertTrue(view.path("detailEnabled").booleanValue())
            assertFalse(view.path("effectiveDetails").booleanValue())
            assertEquals(baseline, f.nonSettingState())
            f.assertNoProviderRequests()
        }
    }

    @Test fun `invalid stored id booleans revision and unavailable SQL return 503 instead of defaults`() {
        ApolloSettingsFixture().use { f ->
            f.service.save(0, true, true)
            f.jdbc.execute("ALTER TABLE discovery_apollo_setting ALTER CHECK chk_discovery_apollo_setting_id NOT ENFORCED")
            f.jdbc.execute("ALTER TABLE discovery_apollo_setting ALTER CHECK chk_discovery_apollo_setting_values NOT ENFORCED")
            try {
                for (assignment in listOf("search_enabled=2", "detail_enabled=-1", "revision=0", "id=2")) {
                    f.jdbc.update("UPDATE discovery_apollo_setting SET $assignment")
                    assertThrows(DiscoveryApolloSettingsUnavailable::class.java) { f.service.resolve() }
                    assertEquals(503, f.request("GET").status)
                    assertEquals(503, f.request("PUT", payload(1, false, false)).status)
                    f.jdbc.update("UPDATE discovery_apollo_setting SET id=1, search_enabled=1, detail_enabled=1, revision=1")
                }
            } finally {
                f.jdbc.update("UPDATE discovery_apollo_setting SET id=1, search_enabled=1, detail_enabled=1, revision=1")
                f.jdbc.execute("ALTER TABLE discovery_apollo_setting ALTER CHECK chk_discovery_apollo_setting_id ENFORCED")
                f.jdbc.execute("ALTER TABLE discovery_apollo_setting ALTER CHECK chk_discovery_apollo_setting_values ENFORCED")
            }
            f.jdbc.execute("RENAME TABLE discovery_apollo_setting TO discovery_apollo_setting_unavailable")
            try {
                assertEquals(503, f.request("GET").status)
                assertEquals(503, f.request("PUT", payload(1, false, false)).status)
            } finally { f.jdbc.execute("RENAME TABLE discovery_apollo_setting_unavailable TO discovery_apollo_setting") }
            assertEquals(1, f.service.resolve().revision)
        }
    }

    @Test fun `V160 enforces singleton values and millisecond timestamp with no seed or other columns`() {
        val jdbc = ApolloSettingsMysql.jdbc
        assertNull(ApolloSettingsFixture().use { it.repository.find() })
        assertEquals(listOf("id:int:null", "search_enabled:tinyint:null", "detail_enabled:tinyint:null",
            "revision:bigint:null", "updated_at:datetime:3"), jdbc.query(
            "SELECT COLUMN_NAME, DATA_TYPE, DATETIME_PRECISION FROM information_schema.columns " +
                "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='discovery_apollo_setting' ORDER BY ORDINAL_POSITION",
            RowMapper { rs, _ -> "${rs.getString(1)}:${rs.getString(2)}:${rs.getString(3)}" }))
        for ((id, search, detail, revision) in listOf(listOf(2, 0, 0, 1), listOf(1, 2, 0, 1),
            listOf(1, 0, -1, 1), listOf(1, 0, 0, 0))) {
            assertThrows(org.springframework.dao.DataAccessException::class.java) {
                jdbc.update("INSERT INTO discovery_apollo_setting VALUES (?, ?, ?, ?, UTC_TIMESTAMP(3))", id, search, detail, revision)
            }
        }
    }

    @Test fun `sole frozen protocol round trips saved default and legacy but refuses malformed schema fields or coercion`() {
        ApolloSettingsFixture().use { f ->
            val defaults = f.service.freeze()
            assertEquals(defaults, FrozenApolloAccess.parse(defaults.toJson(APOLLO_MAPPER)))
            assertEquals(0, defaults.settingRevision)
            f.service.save(0, false, true)
            val frozen = f.service.freeze()
            assertEquals(frozen, FrozenApolloAccess.parse(frozen.toJson(APOLLO_MAPPER)))
            assertTrue(frozen.detailEnabled)
            val legacy = frozen.toJson(APOLLO_MAPPER).put("source", "LEGACY").put("settingRevision", 0)
            assertEquals(ApolloSettingSource.LEGACY, FrozenApolloAccess.parse(legacy).source)
            val invalid = mutableListOf<JsonNode>(APOLLO_MAPPER.nullNode(), APOLLO_MAPPER.createArrayNode())
            val valid = frozen.toJson(APOLLO_MAPPER)
            valid.fieldNames().asSequence().toList().forEach { field -> invalid.add(valid.deepCopy().apply { remove(field) }) }
            invalid.add(valid.deepCopy().put("requestsPerDay", 500))
            for ((field, values) in mapOf("schemaVersion" to listOf("0", "2", "1.0", "4294967297", "null", "\"1\""),
                "settingRevision" to listOf("-1", "1.0", "9223372036854775808", "null", "\"1\""),
                "searchEnabled" to listOf("0", "\"false\"", "null"),
                "detailEnabled" to listOf("1", "\"true\"", "null"),
                "source" to listOf("\"OTHER\"", "0", "null"))) {
                values.forEach { value -> invalid.add(valid.deepCopy().apply { set<JsonNode>(field, APOLLO_MAPPER.readTree(value)) }) }
            }
            invalid.add(valid.deepCopy().put("settingRevision", 0))
            invalid.add(valid.deepCopy().put("source", "DEPLOYMENT_DEFAULT"))
            invalid.forEach { node -> assertThrows(IllegalArgumentException::class.java) { FrozenApolloAccess.parse(node) } }
        }
    }
}

private const val SYNTHETIC_KEY = "synthetic-apollo-prefix-unique-secret-suffix"
private val APOLLO_MAPPER = jacksonObjectMapper().findAndRegisterModules()
private fun properties(enabled: Boolean = true, worker: Boolean = false, default: Boolean = true, key: String = SYNTHETIC_KEY) =
    DiscoveryVerificationProperties(enabled = enabled, workerEnabled = worker, apolloDefault = default, apolloApiKey = key)
private fun payload(revision: Long, search: Boolean, detail: Boolean) =
    "{\"revision\":$revision,\"searchEnabled\":$search,\"detailEnabled\":$detail}"

private object ApolloSettingsMysql {
    class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
    val mysql = Mysql().withDatabaseName("apollo_settings").withUsername("test").withPassword("test")
    lateinit var dataSource: DriverManagerDataSource
    lateinit var jdbc: JdbcTemplate
    fun start() {
        check(DockerClientFactory.instance().isDockerAvailable) { "Disposable Docker MySQL is required" }
        mysql.start()
        val jdbcUrl = mysql.jdbcUrl + (if ('?' in mysql.jdbcUrl) "&" else "?") + "connectTimeout=2000&socketTimeout=5000"
        dataSource = DriverManagerDataSource(jdbcUrl, mysql.username, mysql.password)
        Flyway.configure().dataSource(dataSource).placeholderReplacement(false).target("160").load().migrate()
        jdbc = JdbcTemplate(dataSource)
        jdbc.update("INSERT INTO discovery_verification_run (execution_id, mode, policy_version, apollo_requested, settings_json, " +
            "created_at, updated_at) VALUES (60601, 'AUDIT', 'fixture', 1, '{\"immutable\":true}', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))")
    }
    fun clean() { jdbc.update("DELETE FROM discovery_apollo_setting") }
    fun stop() { if (mysql.isRunning) mysql.stop() }
}

/** Synchronizes only after actual empty SELECT FOR UPDATE; both transactions then really contend in InnoDB. */
private class RacingApolloJdbc : JdbcTemplate(ApolloSettingsMysql.dataSource) {
    @Volatile var emptyReadBarrier: CyclicBarrier? = null
    override fun <T : Any?> query(sql: String, rowMapper: RowMapper<T>): List<T> {
        val result = super.query(sql, rowMapper)
        if (sql.endsWith("FOR UPDATE") && result.isEmpty()) emptyReadBarrier?.await(10, TimeUnit.SECONDS)
        return result
    }
}

@TestConfiguration
@EnableWebMvc
class ApolloSettingsHttpConfiguration : WebMvcConfigurer {
    override fun configureMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        converters.add(MappingJackson2HttpMessageConverter(APOLLO_MAPPER))
    }
}

private data class ApolloHttpResult(val status: Int, val body: String) {
    val json: JsonNode get() = APOLLO_MAPPER.readTree(body)
}

private class ApolloSettingsFixture : AutoCloseable {
    val jdbc = RacingApolloJdbc()
    val repository = DiscoveryApolloSettingRepository(jdbc, DataSourceTransactionManager(ApolloSettingsMysql.dataSource))
    val service get() = activeService
    private var activeService = newService()
    private val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    private val logs = ListAppender<ILoggingEvent>().apply { start(); rootLogger.addAppender(this) }
    private val providerRequests = AtomicInteger()
    private val observer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { exchange -> providerRequests.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close() }
        start()
    }
    private val context = AnnotationConfigWebApplicationContext()
    private val auth = Mockito.mock(AuthService::class.java).apply {
        val now = LocalDateTime.now()
        Mockito.`when`(findUser("apollo-fixture")).thenReturn(AdminUser(1, "apollo-fixture", "unused", false, null, now, now))
    }
    private val factory = TomcatServletWebServerFactory(0).apply { address = InetAddress.getByName("127.0.0.1") }
    private val server = factory.getWebServer({ servletContext ->
        context.register(ApolloSettingsHttpConfiguration::class.java)
        context.addBeanFactoryPostProcessor { beans ->
            beans.registerSingleton("apolloController", DiscoveryApolloSettingsController(activeService))
            beans.registerSingleton("authWebConfig", AuthWebConfig(auth, APOLLO_MAPPER))
        }
        val dispatcher = servletContext.addServlet("dispatcher", DispatcherServlet(context))
        dispatcher.setLoadOnStartup(1)
        dispatcher.addMapping("/")
        servletContext.addServlet("fixture-session", object : HttpServlet() {
            override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
                req.getSession(true).setAttribute(AuthSessionKeys.USERNAME, "apollo-fixture")
                resp.status = 204
            }
        }).addMapping("/fixture-session")
    }).apply { start() }
    private var cookie: String? = null


    fun newService(clock: Clock = Clock.fixed(Instant.parse("2026-10-24T01:02:03.123456Z"), ZoneOffset.UTC),
        properties: DiscoveryVerificationProperties = properties()) = DiscoveryApolloSettingsService(repository, properties, clock)
    fun recreateService() {
        activeService = newService()
        val beans = context.beanFactory as org.springframework.beans.factory.support.DefaultListableBeanFactory
        beans.destroySingleton("apolloController")
        beans.registerSingleton("apolloController", DiscoveryApolloSettingsController(activeService))
    }

    fun request(method: String, body: String? = null, authenticated: Boolean = true): ApolloHttpResult {
        if (authenticated && cookie == null) {
            val login = URL("http://127.0.0.1:${server.port}/fixture-session").openConnection() as HttpURLConnection
            try { check(login.responseCode == 204); cookie = login.getHeaderField("Set-Cookie").substringBefore(';') }
            finally { login.disconnect() }
        }
        val connection = URL("http://127.0.0.1:${server.port}/api/expert-discovery/verification/apollo-settings").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 3000
        connection.readTimeout = 15000
        if (authenticated) connection.setRequestProperty("Cookie", cookie)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        try {
            val status = connection.responseCode
            val text = (if (status >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() } ?: ""
            check(!text.contains(SYNTHETIC_KEY) && !text.contains("synthetic-apollo-prefix") && !text.contains("unique-secret-suffix"))
            return ApolloHttpResult(status, text)
        } finally { connection.disconnect() }
    }

    fun firstWriteRace() {
        check(request("GET").status == 200)
        jdbc.emptyReadBarrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = listOf(true to false, false to true).map { (search, detail) -> executor.submit<Int> {
                request("PUT", payload(0, search, detail)).status
            } }.map { it.get(25, TimeUnit.SECONDS) }.sorted()
            check(results == listOf(200, 409)) { "Unexpected first-write outcomes: $results" }
            println("APOLLO_FIRST_WRITE_RACE emptyLockedReads=2 statuses=$results revision=${service.resolve().revision}")
        } finally { jdbc.emptyReadBarrier = null; executor.shutdownNow() }
    }

    /** All migrated non-setting tables: row counts plus MySQL content checksums, including the seeded run. */
    fun nonSettingState(): Map<String, Pair<Long, Any?>> = jdbc.queryForList("SHOW TABLES", String::class.java)
        .filter { it != "discovery_apollo_setting" }.associateWith { table ->
            val count = jdbc.queryForObject("SELECT COUNT(*) FROM `$table`", Long::class.java)!!
            count to jdbc.queryForMap("CHECKSUM TABLE `$table`")["Checksum"]
        }

    fun assertNoProviderRequests() { check(providerRequests.get() == 0) }
    fun assertNoSecretsInLogs() {
        val text = logs.list.joinToString("\n") { it.formattedMessage }
        check(!text.contains(SYNTHETIC_KEY) && !text.contains("synthetic-apollo-prefix") && !text.contains("unique-secret-suffix"))
    }
    override fun close() {
        try { assertNoProviderRequests(); assertNoSecretsInLogs() }
        finally { server.stop(); context.close(); observer.stop(0); rootLogger.detachAppender(logs); logs.stop() }
    }
}

/** Separate executable runtime proof: real SQL and real TCP HTTP, not a JUnit invocation. */
object DiscoveryApolloSettingsSmokeLauncher {
    @JvmStatic fun main(args: Array<String>) {
        ApolloSettingsMysql.start()
        try {
            ApolloSettingsMysql.clean()
            ApolloSettingsFixture().use { f ->
                val baseline = f.nonSettingState()
                val defaults = f.request("GET")
                check(defaults.status == 200 && defaults.json.path("source").textValue() == "DEPLOYMENT_DEFAULT" &&
                    defaults.json.path("revision").longValue() == 0L && !defaults.json.path("detailEnabled").booleanValue())
                check(f.repository.find() == null)
                check(f.request("PUT", payload(0, true, false)).json.path("revision").longValue() == 1L)
                f.recreateService()
                val restarted = f.request("GET")
                check(restarted.status == 200 && restarted.json.path("source").textValue() == "SAVED" &&
                    restarted.json.path("searchEnabled").booleanValue() && !restarted.json.path("detailEnabled").booleanValue())
                val changed = f.request("PUT", payload(1, false, true))
                check(changed.status == 200 && changed.json.path("detailEnabled").booleanValue() &&
                    !changed.json.path("searchEnabled").booleanValue() && !changed.json.path("effectiveDetails").booleanValue())
                check(f.request("PUT", payload(1, true, true)).status == 409)
                check(f.request("PUT", payload(2, false, true).dropLast(1) + ",\"source\":\"SAVED\"}").status == 400)
                check(f.request("GET", authenticated = false).status == 401)
                check(f.request("PUT", payload(2, true, true), false).status == 401)
                val frozen = f.service.freeze()
                check(FrozenApolloAccess.parse(frozen.toJson(APOLLO_MAPPER)) == frozen)
                check(baseline == f.nonSettingState())
                println("APOLLO_RUNTIME_FLOW defaultGET=200/revision0 firstPUT=200/revision1 recreatedGET=200/SAVED independentPUT=200/false/true/effectiveDetails=false stale=409 unknown=400 anonymousGET=401 anonymousPUT=401 nonSettingTables=${baseline.size} nonSettingChanges=0 providerObserverRequests=0 snapshotRoundtrip=true")
                ApolloSettingsMysql.clean()
                f.firstWriteRace()
                val beforeOutage = f.service.resolve()
                val container = ApolloSettingsMysql.mysql
                container.dockerClient.stopContainerCmd(container.containerId).withTimeout(1).exec()
                try {
                    check(f.request("GET").status == 503)
                    check(f.request("PUT", payload(beforeOutage.revision, true, true)).status == 503)
                } finally {
                    container.dockerClient.startContainerCmd(container.containerId).exec()
                    // Docker may reassign an ephemeral host port when restarting the same container.
                    val binding = container.dockerClient.inspectContainerCmd(container.containerId).exec()
                        .networkSettings.ports.bindings.getValue(com.github.dockerjava.api.model.ExposedPort.tcp(3306))
                        .first { it.hostIp == "0.0.0.0" || it.hostIp == "127.0.0.1" }
                    val oldPort = java.net.URI(ApolloSettingsMysql.dataSource.url!!.removePrefix("jdbc:")).port
                    ApolloSettingsMysql.dataSource.url = "jdbc:mysql://${container.host}:${binding.hostPortSpec}/apollo_settings" +
                        "?connectTimeout=2000&socketTimeout=5000"
                    println("APOLLO_RUNTIME_MYSQL_RESTART previousPort=$oldPort currentPort=${binding.hostPortSpec}")
                }
                var restored = false
                for (attempt in 1..30) {
                    try { check(f.service.resolve() == beforeOutage); restored = true; break }
                    catch (_: DiscoveryApolloSettingsUnavailable) { Thread.sleep(500) }
                }
                check(restored)
                check(baseline == f.nonSettingState())
                f.assertNoProviderRequests()
                f.assertNoSecretsInLogs()
                println("APOLLO_RUNTIME_OUTAGE stoppedMysqlGET=503 stoppedMysqlPUT=503 restoredSavedRow=true nonSettingChanges=0 providerObserverRequests=0 keyLeak=false")
            }
        } finally { ApolloSettingsMysql.stop() }
    }
}
