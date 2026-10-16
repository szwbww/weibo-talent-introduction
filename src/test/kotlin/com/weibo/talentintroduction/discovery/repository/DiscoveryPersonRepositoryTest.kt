package com.weibo.talentintroduction.discovery.repository

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberSnapshot
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberWrite
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonOutcome
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonSnapshot
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonService
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.IdentityVerification
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertPromotionAuditService
import org.mockito.Mockito
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 04（I-1/I-2/I-3）：`DiscoveryPersonRepository` 的**真实数据库**事务/并发/唯一约束验收。
 * Testcontainers 引导与 `MailOpenTrackingRepositoryIT` 同款（`@DataJdbcTest` + `Replace.NONE` +
 * `MySQLContainer` + `@DynamicPropertySource` + `-DmysqlIt=true`）。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
@Import(DiscoveryPersonRepository::class)
class DiscoveryPersonRepositoryTest {
    companion object {
        private class Mysql(image: String) : MySQLContainer<Mysql>(image)
        private val mysql = Mysql("mysql:8.0.36").withDatabaseName("talent_introduction")
            .withUsername("test").withPassword("test")

        @JvmStatic @BeforeAll fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) { "Docker is required for discovery person MySQL IT" }
            mysql.start()
        }

        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    @Autowired lateinit var jdbc: JdbcTemplate
    @SpyBean lateinit var repository: DiscoveryPersonRepository
    @Autowired lateinit var transactions: PlatformTransactionManager

    private val now = LocalDateTime.of(2026, 10, 14, 9, 30)
    private val mapper = ObjectMapper()
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val properties = ElasticsearchProperties(
        baseUrl = "https://es.example.com:9200",
        username = "elastic", password = "secret",
        rawIndexName = "orcid_info",
        candidateIndexName = "orcid_info_candidate",
        applicationIndexName = "orcid_info_application"
    )
    private val indexService = ExpertIndexService(properties, restTemplate, mapper)
    private val writer = ExpertIndexWriterService(
        restTemplate, properties, indexService, mapper,
        Mockito.mock(ExpertPromotionAuditService::class.java),
        Mockito.mock(ExpertContactRepository::class.java)
    )
    private val contactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val aliasService = Mockito.mock(ExpertEmailAliasService::class.java)
    private val docs = linkedMapOf<String, MutableMap<String, Any?>>()
    private val evidenceHash = "a".repeat(64)
    private var afterRootRead: ((String) -> Unit)? = null

    @BeforeEach fun setup() {
        docs.clear()
        afterRootRead = null
        jdbc.update("DELETE FROM discovery_person_author_key")
        jdbc.update("DELETE FROM discovery_person_member")
        jdbc.update("DELETE FROM discovery_person")
    }
    init {
        Mockito.doAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            val method = invocation.getArgument<HttpMethod>(1)
            if (method == HttpMethod.GET && url.contains("/_doc/")) getResponse(url)
            else throw IllegalStateException("unexpected request $method $url")
        }.`when`(restTemplate).exchange(
            Mockito.anyString(), Mockito.any(HttpMethod::class.java), Mockito.any(),
            Mockito.eq(JsonNode::class.java)
        )
    }

    private fun getResponse(url: String): ResponseEntity<JsonNode> {
        val indexName = url.removePrefix("${properties.baseUrl}/").substringBefore("/_doc/")
        val docId = url.substringAfter("/_doc/").substringBefore('?')
        val level = ExpertIndexLevel.values().firstOrNull { indexService.indexName(it) == indexName }
            ?: throw IllegalStateException("unknown index $indexName")
        val source = docs["${level.name}/$docId"] ?: throw HttpClientErrorException(HttpStatus.NOT_FOUND)
        return ResponseEntity.ok(mapper.valueToTree(mapOf("_source" to source, "_seq_no" to 1L, "_primary_term" to 1L)))
    }

    private fun service() = DiscoveryPersonService(repository, writer, contactRepository, aliasService)

    private fun discoveredSource(email: String, given: String = "Jane", family: String = "Doe") =
        mutableMapOf<String, Any?>(
            "email" to email,
            "givenNames" to given,
            "familyNames" to family,
            "emailSource" to "PAPER_FULLTEXT",
            "tags" to listOf("discovered"),
            "identityVerification" to IdentityVerification(
                "VERIFIED",
                DiscoveryIdentity.VERSION,
                DiscoveryIdentity.normalizedEmail(email),
                given,
                family,
                "JATS_SHA256",
                evidenceHash,
                "0000-0002-1825-0097",
                null
            )
        )

    private fun put(docId: String, email: String, given: String = "Jane", family: String = "Doe") {
        docs["${ExpertIndexLevel.RAW.name}/$docId"] = discoveredSource(email, given, family)
    }

    private val authorKey = "ORCID:0000-0002-1825-0097"

    private fun snapshot(docId: String, email: String) =
        DiscoveryPersonMemberSnapshot(
            docId,
            DiscoveryPersonIdentity.fingerprint(
                docId, DiscoveryIdentity.normalizedEmail(email), "Jane", "Doe", listOf(authorKey)
            )
        )

    private fun register(service: DiscoveryPersonService, docId: String, email: String) =
        service.register(DiscoveryPersonSnapshot("ORCID", listOf(snapshot(docId, email))))

    private fun afterNextRootRead(action: (String) -> Unit) {
        afterRootRead = action
        Mockito.doAnswer { invocation ->
            val person = invocation.callRealMethod()
            val personId = invocation.getArgument<String>(0)
            afterRootRead?.let { callback ->
                afterRootRead = null
                callback(personId)
            }
            person
        }.`when`(repository).findPerson(Mockito.anyString())
    }


    private fun member(docId: String, email: String? = "$docId@example.test", fingerprint: String = "f".repeat(64)) =
        DiscoveryPersonMemberWrite(docId, email, fingerprint, """{"docId":"$docId"}""")

    private fun count(table: String) = jdbc.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)!!

    private fun create(personId: String, primary: String, members: List<DiscoveryPersonMemberWrite>, keys: List<String>) =
        repository.createPerson(personId, primary, members, keys, now)

    @Test
    fun `createPerson persists person members and author keys with batch reads`() {
        val status = create(
            "p-1", "doc-a",
            listOf(member("doc-b", "b@example.test"), member("doc-a", "a@example.test"), member("doc-c", "c@example.test")),
            listOf("ORCID:0000-0002-1825-0097")
        )
        assertEquals(DiscoveryPersonWriteStatus.APPLIED, status)
        val person = repository.findPerson("p-1")!!
        assertEquals("doc-a", person.primaryDocId)
        assertEquals(0L, person.revision)
        assertEquals(3, repository.findMembersByPerson("p-1").size)
        assertEquals(mapOf("doc-a" to "p-1", "doc-b" to "p-1", "doc-c" to "p-1"), repository.findPersonIdsByDocIds(listOf("doc-a", "doc-b", "doc-c")))
        assertEquals(mapOf("ORCID:0000-0002-1825-0097" to "p-1"), repository.findPersonIdsByAuthorKeys(listOf("ORCID:0000-0002-1825-0097")))
        assertEquals(listOf("ORCID:0000-0002-1825-0097"), repository.findAuthorKeysByPerson("p-1").map { it.authorKey })
        val stored = repository.findMember("doc-a")!!
        assertEquals("f".repeat(64), stored.identityFingerprint)
        assertEquals("a@example.test", stored.normalizedEmail)
    }

    @Test
    fun `author key and docId uniqueness reject duplicates and keep no partial root`() {
        assertEquals(DiscoveryPersonWriteStatus.APPLIED, create("p-1", "doc-a", listOf(member("doc-a")), listOf("ORCID:k")))
        // 作者键已被占用：不写任何行，也不留下空主档。
        assertEquals(DiscoveryPersonWriteStatus.AUTHOR_KEY_TAKEN, create("p-2", "doc-b", listOf(member("doc-b")), listOf("ORCID:k")))
        // docId 已有成员行。
        assertEquals(DiscoveryPersonWriteStatus.MEMBER_TAKEN, create("p-3", "doc-a", listOf(member("doc-a")), emptyList()))
        assertEquals(1, count("discovery_person"))
        assertEquals(1, count("discovery_person_member"))
        assertEquals(1, count("discovery_person_author_key"))
        // 数据库层唯一约束仍是最终防线。
        assertThrows(DuplicateKeyException::class.java) {
            jdbc.update(
                "INSERT INTO discovery_person_member (expert_doc_id, person_id, identity_fingerprint, created_at, updated_at) VALUES ('doc-a', 'p-1', ?, ?, ?)",
                "f".repeat(64), now, now
            )
        }
        assertThrows(DuplicateKeyException::class.java) {
            jdbc.update("INSERT INTO discovery_person_author_key (author_key, person_id) VALUES ('ORCID:k', 'p-1')")
        }
    }

    @Test
    fun `attachToPerson adds members bumps revision and keeps the representative`() {
        assertEquals(DiscoveryPersonWriteStatus.APPLIED, create("p-1", "doc-b", listOf(member("doc-b")), listOf("ORCID:k")))
        val status = repository.attachToPerson("p-1", listOf(member("doc-a", "a@example.test")), listOf("OPENALEX:w"), now)
        assertEquals(DiscoveryPersonWriteStatus.APPLIED, status)
        val person = repository.findPerson("p-1")!!
        assertEquals("doc-b", person.primaryDocId, "新增邮箱不悄悄换代表")
        assertEquals(1L, person.revision)
        assertEquals(2, repository.findMembersByPerson("p-1").size)
        assertEquals(2, repository.findAuthorKeysByPerson("p-1").size)
        // 重复挂接（成员与键都已存在）不改 revision。
        assertEquals(DiscoveryPersonWriteStatus.APPLIED, repository.attachToPerson("p-1", listOf(member("doc-a", "a@example.test")), listOf("OPENALEX:w"), now))
        assertEquals(1L, repository.findPerson("p-1")!!.revision)
        // 挂到不存在的成员所属他人时返回冲突信号。
        assertEquals(DiscoveryPersonWriteStatus.APPLIED, create("p-2", "doc-z", listOf(member("doc-z")), emptyList()))
        assertEquals(DiscoveryPersonWriteStatus.MEMBER_TAKEN, repository.attachToPerson("p-2", listOf(member("doc-a")), emptyList(), now))
        assertEquals(DiscoveryPersonWriteStatus.PERSON_MISSING, repository.attachToPerson("p-missing", listOf(member("doc-new")), emptyList(), now))
    }

    @Test
    fun `concurrent first saves with the same author key create exactly one person`() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val work = listOf("p-a" to "doc-a", "p-b" to "doc-b").map { (personId, docId) ->
                executor.submit<DiscoveryPersonWriteStatus?> {
                    try {
                        create(personId, docId, listOf(member(docId)), listOf("ORCID:race"))
                    } catch (_: DataAccessException) {
                        // 并发同键首次登记：败者可能是重复键（DuplicateKeyException）或
                        // 死锁（DeadlockLoserDataAccessException），两者都意味着本事务未写任何行。
                        null
                    }
                }
            }
            val results = work.map { it.get(60, TimeUnit.SECONDS) }
            assertTrue(results.any { it == DiscoveryPersonWriteStatus.APPLIED }, "results=$results")
            assertTrue(results.none { it == DiscoveryPersonWriteStatus.MEMBER_TAKEN }, "results=$results")
        } finally {
            executor.shutdownNow()
        }
        assertEquals(1, count("discovery_person"))
        assertEquals(1, count("discovery_person_member"))
        assertEquals(1, count("discovery_person_author_key"))
    }

    @Test
    fun `rolled back create leaves no empty person root`() {
        assertThrows(IllegalStateException::class.java) {
            TransactionTemplate(transactions).execute {
                create("p-rollback", "doc-r", listOf(member("doc-r")), listOf("ORCID:rollback"))
                throw IllegalStateException("boom")
            }
        }
        assertNull(repository.findPerson("p-rollback"))
        assertNull(repository.findMember("doc-r"))
        assertEquals(0, count("discovery_person"))
        assertEquals(0, count("discovery_person_author_key"))
    }

    @Test
    fun `real service rejects a concurrent invalid root member on MySQL without writing A`() {
        docs.clear()
        val service = service()
        put("doc-a", "a@uni.edu")
        val root = register(service, "doc-a", "a@uni.edu")
        assertEquals(DiscoveryPersonOutcome.CREATED, root.outcome, root.reason)

        put("doc-b", "b@uni.edu")
        put("doc-c", "c@uni.edu")
        afterNextRootRead { personId ->
            assertEquals(DiscoveryPersonOutcome.ATTACHED, register(service(), "doc-b", "b@uni.edu").outcome)
            put("doc-b", "b@uni.edu", given = "John", family = "Smith")
            assertEquals(1L, repository.findPerson(personId)!!.revision)
        }

        val result = register(service, "doc-c", "c@uni.edu")

        assertEquals(DiscoveryPersonOutcome.STALE, result.outcome, result.reason)
        assertEquals("FINGERPRINT_MISMATCH", result.reason)
        assertNull(repository.findMember("doc-c"), "A must not write after the concurrent member fails full-group verification")
        assertEquals(setOf("doc-a", "doc-b"), repository.findMembersByPerson(root.personId!!).map { it.expertDocId }.toSet())
        assertEquals(1L, repository.findPerson(root.personId!!)!!.revision)
        assertEquals(1, count("discovery_person"))
        assertEquals(2, count("discovery_person_member"))
        assertEquals(1, count("discovery_person_author_key"))
    }


    @Test
    fun `real service attaches a compatible concurrent root member after fresh verification on MySQL`() {
        docs.clear()
        val service = service()
        put("doc-a", "a@uni.edu")
        val root = register(service, "doc-a", "a@uni.edu")
        assertEquals(DiscoveryPersonOutcome.CREATED, root.outcome, root.reason)
        put("doc-b", "b@uni.edu")
        put("doc-c", "c@uni.edu")
        afterNextRootRead { personId ->
            assertEquals(DiscoveryPersonOutcome.ATTACHED, register(service(), "doc-b", "b@uni.edu").outcome)
            assertEquals(1L, repository.findPerson(personId)!!.revision)
        }

        val result = register(service, "doc-c", "c@uni.edu")

        assertEquals(DiscoveryPersonOutcome.ATTACHED, result.outcome, result.reason)
        assertEquals(root.personId, result.personId)
        assertEquals(setOf("doc-a", "doc-b", "doc-c"), repository.findMembersByPerson(root.personId!!).map { it.expertDocId }.toSet())
        assertEquals(2L, repository.findPerson(root.personId!!)!!.revision)
        assertEquals(1, count("discovery_person"))
        assertEquals(3, count("discovery_person_member"))
        assertEquals(1, count("discovery_person_author_key"))
    }

    @Test
    fun `member evidence is queryable for freshness re-check`() {
        assertEquals(DiscoveryPersonWriteStatus.APPLIED, create("p-1", "doc-a", listOf(member("doc-a", "a@example.test", "abc123")), listOf("ORCID:k")))
        val stored = repository.findMember("doc-a")
        assertNotNull(stored)
        assertEquals("abc123", stored!!.identityFingerprint)
        assertEquals("""{"docId":"doc-a"}""", stored.evidenceJson)
    }
}
