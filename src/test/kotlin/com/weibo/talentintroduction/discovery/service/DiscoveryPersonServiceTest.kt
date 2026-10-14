package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonAuthorKeyRecord
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberRecord
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberSnapshot
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberWrite
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonOutcome
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonRecord
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonSnapshot
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonWriteStatus
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.IdentityVerification
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertPromotionAuditService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

/**
 * 04（I-1/I-2/I-3）：[DiscoveryPersonService] 的服务端核实与结果枚举验收。
 *
 * 使用**真实** [ExpertIndexWriterService] + HTTP 替身（与 02 测试同款手法）：被验收的正是
 * 「登记前从 ES 当前档案重新核实来源/标签/docId/指纹」这条事实链。关系存储用内存假仓储，
 * 真实事务/并发/唯一约束由 `DiscoveryPersonRepositoryTest`（真实 MySQL）验收。
 */
class DiscoveryPersonServiceTest {

    // ── 装配 ────────────────────────────────────────────────────────────────

    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val mapper = ObjectMapper()
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
    private val repository = FakeRepository()
    private val service = DiscoveryPersonService(repository, writer, contactRepository, aliasService)

    private val docs = linkedMapOf<String, MutableMap<String, Any?>>()

    init {
        Mockito.doAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            val method = invocation.getArgument<HttpMethod>(1)
            when {
                method == HttpMethod.GET && url.contains("/_doc/") -> getResponse(url)
                else -> throw IllegalStateException("unexpected request $method $url")
            }
        }.`when`(restTemplate).exchange(
            Mockito.anyString(), Mockito.any(HttpMethod::class.java), Mockito.any(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    private fun getResponse(url: String): ResponseEntity<com.fasterxml.jackson.databind.JsonNode> {
        val indexName = url.removePrefix("${properties.baseUrl}/").substringBefore("/_doc/")
        val docId = url.substringAfter("/_doc/").substringBefore('?')
        val level = ExpertIndexLevel.values().firstOrNull { indexService.indexName(it) == indexName }
            ?: throw IllegalStateException("unknown index $indexName")
        val source = docs["${level.name}/$docId"] ?: throw HttpClientErrorException(HttpStatus.NOT_FOUND)
        return ResponseEntity.ok(mapper.valueToTree(mapOf("_source" to source, "_seq_no" to 1L, "_primary_term" to 1L)))
    }

    // ── 夹具 ────────────────────────────────────────────────────────────────

    private val evidenceHash = "a".repeat(64)

    private fun discoveredSource(
        email: String = "jane@uni.edu",
        given: String? = "Jane",
        family: String? = "Doe",
        orcid: String? = null,
        openAlex: String? = null,
        externalIds: Map<String, String> = emptyMap(),
        withProof: Boolean = true
    ): MutableMap<String, Any?> = mutableMapOf<String, Any?>(
        "email" to email, "givenNames" to given, "familyNames" to family,
        "emailSource" to "PAPER_FULLTEXT", "tags" to listOf("discovered")
    ).apply {
        if (withProof) {
            put(
                "identityVerification",
                IdentityVerification(
                    "VERIFIED", DiscoveryIdentity.VERSION,
                    DiscoveryIdentity.normalizedEmail(email), given, family,
                    "JATS_SHA256", evidenceHash, orcid, openAlex
                )
            )
        }
        if (externalIds.isNotEmpty()) put("externalIds", externalIds)
    }

    private fun put(docId: String, level: ExpertIndexLevel = ExpertIndexLevel.RAW, source: MutableMap<String, Any?>) {
        docs["${level.name}/$docId"] = source
    }

    private fun orcidKey(value: String) = "${DiscoveryPersonIdentity.ORCID_PREFIX}$value"
    private fun openAlexKey(value: String) = "${DiscoveryPersonIdentity.OPENALEX_PREFIX}$value"

    private fun fingerprint(docId: String, email: String?, given: String?, family: String?, keys: List<String>) =
        DiscoveryPersonIdentity.fingerprint(docId, email?.let { DiscoveryIdentity.normalizedEmail(it) }, given, family, keys)

    private fun member(docId: String, email: String, given: String = "Jane", family: String = "Doe", keys: List<String>) =
        DiscoveryPersonMemberSnapshot(docId, fingerprint(docId, email, given, family, keys))

    private fun register(vararg members: DiscoveryPersonMemberSnapshot) =
        service.register(DiscoveryPersonSnapshot("ORCID", members.toList()))

    // ── I-1：一人三邮箱 ─────────────────────────────────────────────────────

    @Test
    fun `one person with three emails is created once and re-run adds no member`() {
        listOf("doc-a" to "a@uni.edu", "doc-b" to "b@uni.edu", "doc-c" to "c@uni.edu").forEach { (docId, email) ->
            put(docId, source = discoveredSource(email = email, orcid = "0000-0002-1825-0097"))
        }
        val key = orcidKey("0000-0002-1825-0097")
        val snapshot = DiscoveryPersonSnapshot(
            "ORCID",
            listOf(
                member("doc-b", "b@uni.edu", keys = listOf(key)),
                member("doc-a", "a@uni.edu", keys = listOf(key)),
                member("doc-c", "c@uni.edu", keys = listOf(key))
            )
        )

        val created = service.register(snapshot)
        assertEquals(DiscoveryPersonOutcome.CREATED, created.outcome, created.reason)
        assertNotNull(created.personId)
        assertEquals(3, created.memberCount)
        assertEquals("doc-a", repository.findPerson(created.personId!!)!!.primaryDocId, "代表按最小 docId 固定")
        assertEquals(setOf(key), repository.keys.keys)

        val rerun = service.register(snapshot)
        assertEquals(DiscoveryPersonOutcome.UNCHANGED, rerun.outcome, rerun.reason)
        assertEquals(created.personId, rerun.personId)
        assertEquals(3, repository.members.size)
    }

    @Test
    fun `a later email joins the existing traceable root without changing the representative`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        val first = register(member("doc-a", "a@uni.edu", keys = listOf(orcidKey("0000-0002-1825-0097"))))
        assertEquals(DiscoveryPersonOutcome.CREATED, first.outcome, first.reason)

        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val second = register(member("doc-b", "b@uni.edu", keys = listOf(orcidKey("0000-0002-1825-0097"))))
        assertEquals(DiscoveryPersonOutcome.ATTACHED, second.outcome, second.reason)
        assertEquals(first.personId, second.personId)
        assertEquals(2, second.memberCount)
        assertEquals("doc-a", repository.findPerson(first.personId!!)!!.primaryDocId)
    }

    @Test
    fun `a discovery record without stable binding evidence registers as a singleton without author keys`() {
        put("doc-weak", source = discoveredSource(email = "solo@uni.edu", orcid = null, withProof = false))
        val result = register(member("doc-weak", "solo@uni.edu", keys = emptyList()))
        assertEquals(DiscoveryPersonOutcome.CREATED, result.outcome, result.reason)
        assertEquals(1, result.memberCount)
        assertTrue(repository.keys.isEmpty(), "缺绑定证据不得创建作者键")

        put("doc-other", source = discoveredSource(email = "other@uni.edu", orcid = null, withProof = false))
        val other = register(member("doc-other", "other@uni.edu", keys = emptyList()))
        assertEquals(DiscoveryPersonOutcome.CREATED, other.outcome, other.reason)
        assertEquals(2, repository.persons.size, "同名/同邮箱弱证据不得据以归组")
    }

    @Test
    fun `same name with different author ids is not merged`() {
        put("doc-x", source = discoveredSource(email = "x@uni.edu", orcid = "0000-0001-1111-1111"))
        put("doc-y", source = discoveredSource(email = "y@uni.edu", orcid = "0000-0002-2222-2222"))
        val one = register(member("doc-x", "x@uni.edu", keys = listOf(orcidKey("0000-0001-1111-1111"))))
        val two = register(member("doc-y", "y@uni.edu", keys = listOf(orcidKey("0000-0002-2222-2222"))))
        assertEquals(DiscoveryPersonOutcome.CREATED, one.outcome, one.reason)
        assertEquals(DiscoveryPersonOutcome.CREATED, two.outcome, two.reason)
        assertEquals(2, repository.persons.size)
    }

    // ── I-2：并发/桥接/不可弱关联 ────────────────────────────────────────────

    @Test
    fun `bridging two existing person roots needs review and keeps both roots`() {
        put("doc-x", source = discoveredSource(email = "x@uni.edu", orcid = "0000-0001-1111-1111"))
        put("doc-y", source = discoveredSource(email = "y@uni.edu", orcid = null, openAlex = "A5023888391"))
        val x = register(member("doc-x", "x@uni.edu", keys = listOf(orcidKey("0000-0001-1111-1111"))))
        val y = register(member("doc-y", "y@uni.edu", keys = listOf(openAlexKey("A5023888391"))))
        assertEquals(DiscoveryPersonOutcome.CREATED, x.outcome, x.reason)
        assertEquals(DiscoveryPersonOutcome.CREATED, y.outcome, y.reason)

        put(
            "doc-bridge",
            source = discoveredSource(email = "bridge@uni.edu", orcid = "0000-0001-1111-1111", openAlex = "A5023888391")
        )
        val bridge = register(
            member("doc-bridge", "bridge@uni.edu", keys = listOf(orcidKey("0000-0001-1111-1111"), openAlexKey("A5023888391")))
        )
        assertEquals(DiscoveryPersonOutcome.NEEDS_REVIEW, bridge.outcome)
        assertEquals("BRIDGE_EXISTING_ROOTS", bridge.reason)
        assertNull(repository.members["doc-bridge"], "不自动吞并根")
        assertEquals(2, repository.persons.size)
    }

    @Test
    fun `a multi-member group with an unbound member is not merged`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-weak", source = discoveredSource(email = "weak@uni.edu", orcid = null, withProof = false))
        val result = register(
            member("doc-a", "a@uni.edu", keys = listOf(orcidKey("0000-0002-1825-0097"))),
            member("doc-weak", "weak@uni.edu", keys = emptyList())
        )
        assertEquals(DiscoveryPersonOutcome.NEEDS_REVIEW, result.outcome)
        assertEquals("UNBOUND_MEMBER", result.reason)
        assertTrue(repository.persons.isEmpty())
    }

    @Test
    fun `a member group without shared author keys is not merged`() {
        put("doc-x", source = discoveredSource(email = "x@uni.edu", orcid = "0000-0001-1111-1111"))
        put("doc-y", source = discoveredSource(email = "y@uni.edu", orcid = null, openAlex = "A5023888391"))
        val result = register(
            member("doc-x", "x@uni.edu", keys = listOf(orcidKey("0000-0001-1111-1111"))),
            member("doc-y", "y@uni.edu", keys = listOf(openAlexKey("A5023888391")))
        )
        assertEquals(DiscoveryPersonOutcome.NEEDS_REVIEW, result.outcome)
        assertEquals("UNCONNECTED_MEMBER", result.reason)
        assertTrue(repository.persons.isEmpty())
    }

    @Test
    fun `two different keys of the same kind are not merged`() {
        put("doc-x", source = discoveredSource(email = "x@uni.edu", orcid = "0000-0001-1111-1111"))
        put("doc-y", source = discoveredSource(email = "y@uni.edu", orcid = "0000-0002-2222-2222"))
        val result = register(
            member("doc-x", "x@uni.edu", keys = listOf(orcidKey("0000-0001-1111-1111"))),
            member("doc-y", "y@uni.edu", keys = listOf(orcidKey("0000-0002-2222-2222")))
        )
        assertEquals(DiscoveryPersonOutcome.NEEDS_REVIEW, result.outcome)
        assertEquals("MULTIPLE_AUTHOR_KEYS", result.reason)
        assertTrue(repository.persons.isEmpty())
    }

    @Test
    fun `a unique-conflict is re-read and re-verified instead of treated as success`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        val first = register(member("doc-a", "a@uni.edu", keys = listOf(orcidKey("0000-0002-1825-0097"))))
        assertEquals(DiscoveryPersonOutcome.CREATED, first.outcome, first.reason)

        // 模拟并发竞争者在本事务看到空表后抢占了作者键：首次写入返回冲突信号。
        repository.rejectNextCreate = true
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val second = register(member("doc-b", "b@uni.edu", keys = listOf(orcidKey("0000-0002-1825-0097"))))
        assertEquals(DiscoveryPersonOutcome.ATTACHED, second.outcome, second.reason)
        assertEquals(first.personId, second.personId)
        assertEquals(1, repository.persons.size)
    }

    // ── I-3：范围/事实有效性 ────────────────────────────────────────────────

    @Test
    fun `an email owned by an out-of-scope contact returns scope conflict without writing`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        Mockito.`when`(contactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc("a@uni.edu"))
            .thenReturn(ExpertContact(campaignId = 1, orcidId = "OTHER-EXPERT", expertEmail = "a@uni.edu", expertName = "Other"))

        val result = register(member("doc-a", "a@uni.edu", keys = listOf(orcidKey("0000-0002-1825-0097"))))
        assertEquals(DiscoveryPersonOutcome.SCOPE_CONFLICT, result.outcome)
        assertEquals("EMAIL_OUT_OF_SCOPE", result.reason)
        assertTrue(repository.persons.isEmpty())
        assertTrue(repository.members.isEmpty())
    }

    @Test
    fun `an email alias owned by an out-of-scope contact returns scope conflict`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        Mockito.`when`(aliasService.findContactByEmailOrAlias("a@uni.edu"))
            .thenReturn(ExpertContact(campaignId = 1, orcidId = "OTHER-EXPERT", expertEmail = "a@uni.edu", expertName = "Other"))

        val result = register(member("doc-a", "a@uni.edu", keys = listOf(orcidKey("0000-0002-1825-0097"))))
        assertEquals(DiscoveryPersonOutcome.SCOPE_CONFLICT, result.outcome)
        assertEquals("EMAIL_ALIAS_OUT_OF_SCOPE", result.reason)
        assertTrue(repository.persons.isEmpty())
    }

    @Test
    fun `an author key owned by an out-of-scope contact returns scope conflict`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        Mockito.`when`(contactRepository.findFirstByOrcidIdOrderByUpdatedAtDesc("0000-0002-1825-0097"))
            .thenReturn(ExpertContact(campaignId = 1, orcidId = "OTHER-EXPERT", expertEmail = "other@uni.edu", expertName = "Other"))

        val result = register(member("doc-a", "a@uni.edu", keys = listOf(orcidKey("0000-0002-1825-0097"))))
        assertEquals(DiscoveryPersonOutcome.SCOPE_CONFLICT, result.outcome)
        assertEquals("AUTHOR_KEY_OUT_OF_SCOPE", result.reason)
        assertTrue(repository.persons.isEmpty())
    }

    @Test
    fun `a stale fingerprint is rejected without writing`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        val result = service.register(
            DiscoveryPersonSnapshot("ORCID", listOf(DiscoveryPersonMemberSnapshot("doc-a", "b".repeat(64))))
        )
        assertEquals(DiscoveryPersonOutcome.STALE, result.outcome)
        assertEquals("FINGERPRINT_MISMATCH", result.reason)
        assertTrue(repository.persons.isEmpty())
    }

    @Test
    fun `a changed member fingerprint invalidates the existing relation pending re-review`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        val key = orcidKey("0000-0002-1825-0097")
        assertEquals(DiscoveryPersonOutcome.CREATED, register(member("doc-a", "a@uni.edu", keys = listOf(key))).outcome)

        // 档案事实变化（邮箱改动）后旧关系失效待重核，不静默改写。
        put("doc-a", source = discoveredSource(email = "a2@uni.edu", orcid = "0000-0002-1825-0097"))
        val result = register(member("doc-a", "a2@uni.edu", keys = listOf(key)))
        assertEquals(DiscoveryPersonOutcome.STALE, result.outcome)
        assertEquals("MEMBER_FINGERPRINT_CHANGED", result.reason)
        assertEquals(1, repository.members.size)
        assertEquals("a@uni.edu", repository.findMember("doc-a")!!.normalizedEmail, "旧关系不静默改写")
    }

    @Test
    fun `a missing or non-discovery document is stale`() {
        val missing = register(DiscoveryPersonMemberSnapshot("doc-missing", "f".repeat(64)))
        assertEquals(DiscoveryPersonOutcome.STALE, missing.outcome)
        assertEquals("DOCUMENT_MISSING", missing.reason)

        put("doc-legacy", source = mutableMapOf("email" to "legacy@uni.edu", "givenNames" to "Jane", "familyNames" to "Doe"))
        val notDiscovery = register(member("doc-legacy", "legacy@uni.edu", keys = emptyList()))
        assertEquals(DiscoveryPersonOutcome.STALE, notDiscovery.outcome)
        assertEquals("NOT_DISCOVERY", notDiscovery.reason)
    }

    @Test
    fun `conflicting layer identity needs review without writing`() {
        put("doc-a", level = ExpertIndexLevel.RAW, source = discoveredSource(email = "a@uni.edu", given = "Jane", orcid = "0000-0002-1825-0097"))
        put("doc-a", level = ExpertIndexLevel.CANDIDATE, source = discoveredSource(email = "a@uni.edu", given = "Janet", orcid = "0000-0002-1825-0097"))
        val result = register(member("doc-a", "a@uni.edu", keys = listOf(orcidKey("0000-0002-1825-0097"))))
        assertEquals(DiscoveryPersonOutcome.NEEDS_REVIEW, result.outcome)
        assertEquals("LAYER_IDENTITY_CONFLICT", result.reason)
        assertTrue(repository.persons.isEmpty())
    }

    @Test
    fun `invalid snapshots are stale and write nothing`() {
        val empty = service.register(DiscoveryPersonSnapshot("ORCID", emptyList()))
        assertEquals(DiscoveryPersonOutcome.STALE, empty.outcome)
        assertEquals("EMPTY_SNAPSHOT", empty.reason)

        val duplicated = register(
            DiscoveryPersonMemberSnapshot("doc-a", "f".repeat(64)),
            DiscoveryPersonMemberSnapshot("doc-a", "f".repeat(64))
        )
        assertEquals(DiscoveryPersonOutcome.STALE, duplicated.outcome)
        assertEquals("INVALID_SNAPSHOT", duplicated.reason)

        val blankSource = service.register(DiscoveryPersonSnapshot("  ", listOf(DiscoveryPersonMemberSnapshot("doc-a", "f".repeat(64)))))
        assertEquals(DiscoveryPersonOutcome.STALE, blankSource.outcome)
        assertEquals("SOURCE_UNVERIFIED", blankSource.reason)
        assertTrue(repository.persons.isEmpty())
    }

    // ── 内存假仓储（真实事务/并发由 DiscoveryPersonRepositoryTest 验收） ──────

    private class FakeRepository : DiscoveryPersonRepository(JdbcTemplate()) {
        val persons = linkedMapOf<String, DiscoveryPersonRecord>()
        val members = linkedMapOf<String, DiscoveryPersonMemberRecord>()
        val keys = linkedMapOf<String, String>()
        var rejectNextCreate = false

        override fun findPerson(personId: String): DiscoveryPersonRecord? = persons[personId]

        override fun findMember(expertDocId: String): DiscoveryPersonMemberRecord? = members[expertDocId]

        override fun findMembers(expertDocIds: Collection<String>): List<DiscoveryPersonMemberRecord> =
            expertDocIds.mapNotNull { members[it] }

        override fun findMembersByPerson(personId: String): List<DiscoveryPersonMemberRecord> =
            members.values.filter { it.personId == personId }

        override fun findAuthorKeysByPerson(personId: String): List<DiscoveryPersonAuthorKeyRecord> =
            keys.filterValues { it == personId }.map { DiscoveryPersonAuthorKeyRecord(it.key, it.value) }

        override fun findPersonIdsByDocIds(expertDocIds: Collection<String>): Map<String, String> =
            expertDocIds.mapNotNull { docId -> members[docId]?.let { docId to it.personId } }.toMap()

        override fun findPersonIdsByAuthorKeys(authorKeys: Collection<String>): Map<String, String> =
            authorKeys.mapNotNull { key -> keys[key]?.let { key to it } }.toMap()

        override fun createPerson(
            personId: String,
            primaryDocId: String,
            members: List<DiscoveryPersonMemberWrite>,
            authorKeys: List<String>,
            now: LocalDateTime
        ): DiscoveryPersonWriteStatus {
            if (rejectNextCreate) {
                rejectNextCreate = false
                return DiscoveryPersonWriteStatus.AUTHOR_KEY_TAKEN
            }
            if (authorKeys.any { keys.containsKey(it) }) return DiscoveryPersonWriteStatus.AUTHOR_KEY_TAKEN
            if (members.any { this.members.containsKey(it.expertDocId) }) return DiscoveryPersonWriteStatus.MEMBER_TAKEN
            persons[personId] = DiscoveryPersonRecord(personId, primaryDocId, 0, now, now)
            members.forEach { this.members[it.expertDocId] = record(it, personId, now) }
            authorKeys.forEach { keys[it] = personId }
            return DiscoveryPersonWriteStatus.APPLIED
        }

        override fun attachToPerson(
            personId: String,
            members: List<DiscoveryPersonMemberWrite>,
            authorKeys: List<String>,
            now: LocalDateTime
        ): DiscoveryPersonWriteStatus {
            if (authorKeys.any { keys[it]?.let { owner -> owner != personId } == true }) {
                return DiscoveryPersonWriteStatus.AUTHOR_KEY_TAKEN
            }
            if (members.any { this.members[it.expertDocId]?.personId?.let { owner -> owner != personId } == true }) {
                return DiscoveryPersonWriteStatus.MEMBER_TAKEN
            }
            val person = persons[personId] ?: return DiscoveryPersonWriteStatus.PERSON_MISSING
            var changed = false
            members.filter { it.expertDocId !in this.members }.forEach {
                changed = true
                this.members[it.expertDocId] = record(it, personId, now)
            }
            authorKeys.filter { it !in keys }.forEach {
                changed = true
                keys[it] = personId
            }
            if (changed) persons[personId] = person.copy(revision = person.revision + 1, updatedAt = now)
            return DiscoveryPersonWriteStatus.APPLIED
        }

        private fun record(write: DiscoveryPersonMemberWrite, personId: String, now: LocalDateTime) =
            DiscoveryPersonMemberRecord(
                expertDocId = write.expertDocId,
                personId = personId,
                normalizedEmail = write.normalizedEmail,
                identityFingerprint = write.identityFingerprint,
                evidenceJson = write.evidenceJson,
                createdAt = now,
                updatedAt = now
            )
    }
}
