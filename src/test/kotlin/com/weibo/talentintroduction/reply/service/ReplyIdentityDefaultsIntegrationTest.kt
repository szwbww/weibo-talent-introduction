package com.weibo.talentintroduction.reply.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.mail.service.MailPlaceholderService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.reply.controller.ReplySnippetController
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.template.service.ComposeTemplatePreviewDraftRequest
import com.weibo.talentintroduction.template.service.ComposeDraftBlock
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.http.MediaType
import org.testcontainers.containers.MySQLContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.enabled=false"])
@Import(ReplySnippetService::class, ContentVariantService::class, MailPlaceholderService::class)
class ReplyIdentityDefaultsIntegrationTest {
    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("identity_fixture").withUsername("test").withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1")
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
        @JvmStatic @AfterAll fun stop() { mysql.stop() }
    }
    @Autowired private lateinit var service: ReplySnippetService
    @Autowired private lateinit var repository: ReplySnippetRepository
    @Autowired private lateinit var contentVariants: ContentVariantService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var transactions: PlatformTransactionManager
    @MockBean private lateinit var variables: MailVariableService

    @BeforeEach fun seed() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS reply_snippet (id BIGINT PRIMARY KEY AUTO_INCREMENT, snippet_type VARCHAR(32) NOT NULL, content TEXT NOT NULL, display_order INT NOT NULL DEFAULT 100, is_default TINYINT(1) NOT NULL DEFAULT 0, enabled TINYINT(1) NOT NULL DEFAULT 1, variant_group VARCHAR(100), name VARCHAR(255), created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)")
        jdbc.execute("CREATE TABLE IF NOT EXISTS content_variant (id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_type VARCHAR(32) NOT NULL, owner_id BIGINT NOT NULL, variant_order INT NOT NULL, content TEXT NOT NULL, enabled TINYINT(1) NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)")
        jdbc.execute("DROP TRIGGER IF EXISTS reject_signature")
        jdbc.update("DELETE FROM content_variant")
        jdbc.update("DELETE FROM reply_snippet")
        jdbc.update("INSERT INTO reply_snippet (id,snippet_type,content,display_order,is_default,name,variant_group) VALUES (101,'SALUTATION','Dear',10,1,'Salutation','group-a'),(102,'SALUTATION','Hello',20,0,'Other','group-b'),(201,'CLOSING','Regards',10,1,'Signature','group-c'),(301,'GREETING','Thanks',10,1,'Greeting',NULL)")
        jdbc.update("INSERT INTO content_variant (id,owner_type,owner_id,variant_order,content,enabled) VALUES (501,'REPLY_SNIPPET',201,10,'Cheers',1),(502,'REPLY_SNIPPET',201,20,'Sincerely',0)")
        doAnswer { MailPlaceholderService().requireValidTemplatePlaceholders(it.getArgument(0)); null }
            .`when`(variables).requireValidTemplatePlaceholders(anyString())
    }
    @AfterEach fun cleanupTrigger() { jdbc.execute("DROP TRIGGER IF EXISTS reject_signature") }
    private fun request(salutation: String = "Hi", signature: String = "Best", fingerprint: String = service.getIdentityDefaults().baseFingerprint) =
        IdentityDefaultsSaveRequest(fingerprint, IdentityDefaultSelection(102, salutation), IdentityDefaultSelection(201, signature))
    private fun rows() = jdbc.queryForList("SELECT * FROM reply_snippet ORDER BY id")
    private fun variants() = jdbc.queryForList("SELECT * FROM content_variant ORDER BY id")

    @Test fun `atomic save preserves non identity fields variants explicit selections and next default consumers`() {
        val before = rows(); val variantBefore = variants()
        val updated = service.saveIdentityDefaults(request())
        assertEquals(updated, service.getIdentityDefaults())
        assertEquals(102L, updated.salutation?.id)
        assertEquals("Hi", service.resolveManualFrame().salutation)
        assertEquals("Best", service.resolveDefaultSelectableFrame().closing)
        assertEquals("Dear", service.resolveSelectableFrame(ReplyFrameSelection(salutationSnippetId = 101)).salutation)
        assertEquals("Best", repository.findById(201).get().content)
        assertEquals(variantBefore, variants())
        val after = rows()
        before.zip(after).forEach { (old, new) ->
            assertEquals(old.filterKeys { it !in setOf("content", "is_default", "updated_at") },
                new.filterKeys { it !in setOf("content", "is_default", "updated_at") })
        }
        assertEquals(before.last(), after.last())
        // This isolated schema contains only the two permitted stores: any draft/mail write fails the test.
        assertEquals(listOf("content_variant", "reply_snippet"), jdbc.queryForList("SHOW TABLES").map { it.values.first().toString() }.sorted())
    }

    @Test fun `by ID template preview rereads edited main content while an explicit different ID stays unchanged`() {
        val templates = MailComposeTemplateService(
            mock(MailComposeTemplateRepository::class.java), mock(MailComposeTemplateBlockRepository::class.java),
            mock(QaRuleRepository::class.java), repository, ObjectMapper(), variables,
            mock(ExpertContactRepository::class.java), mock(MailSenderAccountService::class.java), contentVariants)
        fun preview(id: Long) = templates.previewDraft(ComposeTemplatePreviewDraftRequest(
            subject = "Fixture", blocks = listOf(ComposeDraftBlock(10, "REPLY_SNIPPET", refId = id)), variantIndex = -id.toInt())).body
        assertEquals("Regards", preview(201))
        assertEquals("Dear", preview(101))
        service.saveIdentityDefaults(request())
        assertEquals("Best", preview(201))
        assertEquals("Dear", preview(101))
    }

    @Test fun `invalid second slot variants and SQL second save failure roll back the entire operation`() {
        val before = rows(); val variantBefore = variants()
        for (invalid in listOf("", "Cheers", "\${unknown}", "x".repeat(10001))) {
            assertThrows(IllegalArgumentException::class.java) { service.saveIdentityDefaults(request(signature = invalid)) }
            assertEquals(before, rows())
        }
        for (id in listOf(-1L, 999L, 301L)) {
            assertThrows(IllegalArgumentException::class.java) {
                service.saveIdentityDefaults(request().copy(signature = IdentityDefaultSelection(id, "Best")))
            }
        }
        jdbc.execute("CREATE TRIGGER reject_signature BEFORE UPDATE ON reply_snippet FOR EACH ROW BEGIN IF NEW.id=201 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected signature failure'; END IF; END")
        assertThrows(Exception::class.java) {
            service.saveIdentityDefaults(request().copy(salutation = IdentityDefaultSelection(101, "Hi")))
        }
        assertEquals(before, rows()); assertEquals(variantBefore, variants())
    }

    @Test fun `same fingerprint parallel writers have one winner and one conflict`() {
        val base = request(); val ready = CountDownLatch(2); val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val jobs = (1..2).map { n -> pool.submit<Int> {
                ready.countDown(); check(start.await(20, TimeUnit.SECONDS))
                try { service.saveIdentityDefaults(base.copy(salutation = IdentityDefaultSelection(102, "Hi $n"))); 200 }
                catch (expected: ReplyIdentityDefaultsChangedException) { 409 }
            } }
            check(ready.await(20, TimeUnit.SECONDS)); start.countDown()
            assertEquals(listOf(200, 409), jobs.map { it.get(30, TimeUnit.SECONDS) }.sorted())
        } finally { start.countDown(); pool.shutdownNow() }
    }

    @Test fun `all legacy writers serialize behind identity lock and read latest rows`() {
        val actions = listOf<(Long) -> Unit>(
            { service.setEnabled(it, false) }, { service.setDefault(it) },
            { service.update(it, ReplySnippetUpdateCommand("Edited", 20, isDefault = false, enabled = true, name = "Edited")) },
            { service.delete(it) },
            { service.create(ReplySnippetCreateCommand("CLOSING", "New", name = "New")) }
        )
        for (action in actions) {
            seed()
            val before = service.getIdentityDefaults()
            val held = CountDownLatch(1); val release = CountDownLatch(1); val entered = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val writer = pool.submit {
                    TransactionTemplate(transactions).executeWithoutResult {
                        repository.lockAllForUpdate(); service.saveIdentityDefaults(request())
                        held.countDown(); check(release.await(20, TimeUnit.SECONDS))
                    }
                }
                check(held.await(20, TimeUnit.SECONDS))
                val legacy = pool.submit { entered.countDown(); action(102) }
                check(entered.await(20, TimeUnit.SECONDS))
                assertThrows(java.util.concurrent.TimeoutException::class.java) { legacy.get(200, TimeUnit.MILLISECONDS) }
                assertEquals(before, service.getIdentityDefaults())
                release.countDown(); writer.get(30, TimeUnit.SECONDS); legacy.get(30, TimeUnit.SECONDS)
                assertEquals("Best", repository.findById(201).get().content)
                if (repository.findById(102).isPresent && action === actions.first()) {
                    assertEquals("Hi", repository.findById(102).get().content)
                }
            } finally { release.countDown(); pool.shutdownNow() }
        }
        seed()
        val stale = request()
        service.setEnabled(102, false)
        assertThrows(ReplyIdentityDefaultsChangedException::class.java) { service.saveIdentityDefaults(stale) }
    }

    @Test fun `empty table ordered gap locks permit complete create or complete deadlock victim only`() {
        jdbc.update("DELETE FROM content_variant"); jdbc.update("DELETE FROM reply_snippet")
        assertNull(service.getIdentityDefaults().salutation)
        val locked = CountDownLatch(2); val insert = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val jobs = (1..2).map { n -> pool.submit<Boolean> {
                try {
                    TransactionTemplate(transactions).executeWithoutResult {
                        repository.lockAllForUpdate(); locked.countDown(); check(insert.await(20, TimeUnit.SECONDS))
                        service.create(ReplySnippetCreateCommand("SALUTATION", "First $n", variants = listOf("Variant $n")))
                    }; true
                } catch (exception: RuntimeException) {
                    if (generateSequence<Throwable>(exception) { it.cause }
                            .none { it is org.springframework.dao.DeadlockLoserDataAccessException }) throw exception
                    false
                }
            } }
            check(locked.await(20, TimeUnit.SECONDS)); insert.countDown()
            val success = jobs.count { it.get(30, TimeUnit.SECONDS) }
            assertTrue(success in 1..2)
            assertEquals(success, rows().size); assertEquals(success, variants().size)
        } finally { insert.countDown(); pool.shutdownNow() }
    }

    @Test fun `HTTP authentication validation conflict and success retain global advice`() {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val auth = mock(AuthService::class.java)
        val now = java.time.LocalDateTime.now()
        `when`(auth.findUser("alpha")).thenReturn(AdminUser(id = 901, username = "alpha", passwordHash = "fixture", mustChangePassword = false, createdAt = now, updatedAt = now))
        `when`(auth.findUser("beta")).thenReturn(AdminUser(id = 902, username = "beta", passwordHash = "fixture", mustChangePassword = false, createdAt = now, updatedAt = now))
        val context = AnnotationConfigWebApplicationContext()
        context.servletContext = MockServletContext()
        context.addBeanFactoryPostProcessor { factory ->
            factory.registerSingleton("identityService", service)
            factory.registerSingleton("authService", auth)
            factory.registerSingleton("objectMapper", mapper)
            factory.registerSingleton("expertMailPermissionGuard", org.mockito.Mockito.mock(com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard::class.java))
        }
        context.register(IdentityDefaultsMvc::class.java, ReplySnippetController::class.java, AuthWebConfig::class.java, GlobalExceptionHandler::class.java)
        context.refresh()
        try {
            val mvc = MockMvcBuilders.webAppContextSetup(context).build()
            val endpoint = "/api/reply-snippets/identity-defaults"
            fun session(user: String) = MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, user) }
            val alpha = session("alpha"); val beta = session("beta")
            mvc.perform(get(endpoint)).andExpect(status().isUnauthorized)
            val first = mvc.perform(get(endpoint).session(alpha)).andExpect(status().isOk).andReturn().response.contentAsString
            assertEquals(first, mvc.perform(get(endpoint).session(beta)).andReturn().response.contentAsString)
            val request = request()
            mvc.perform(put(endpoint).session(alpha).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk).andExpect(jsonPath("$.salutation.id").value(102))
            mvc.perform(put(endpoint).session(beta).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("REPLY_IDENTITY_DEFAULTS_CHANGED"))
            mvc.perform(put(endpoint).session(beta).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(signature = ""))))
                .andExpect(status().isBadRequest)
        } finally { context.close() }
    }
}

@Configuration
@EnableWebMvc
class IdentityDefaultsMvc
