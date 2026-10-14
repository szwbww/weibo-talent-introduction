package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberWrite
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
    @Autowired lateinit var repository: DiscoveryPersonRepository
    @Autowired lateinit var transactions: PlatformTransactionManager

    private val now = LocalDateTime.of(2026, 10, 14, 9, 30)

    @BeforeEach fun setup() {
        jdbc.update("DELETE FROM discovery_person_author_key")
        jdbc.update("DELETE FROM discovery_person_member")
        jdbc.update("DELETE FROM discovery_person")
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
    fun `member evidence is queryable for freshness re-check`() {
        assertEquals(DiscoveryPersonWriteStatus.APPLIED, create("p-1", "doc-a", listOf(member("doc-a", "a@example.test", "abc123")), listOf("ORCID:k")))
        val stored = repository.findMember("doc-a")
        assertNotNull(stored)
        assertEquals("abc123", stored!!.identityFingerprint)
        assertEquals("""{"docId":"doc-a"}""", stored.evidenceJson)
    }
}
