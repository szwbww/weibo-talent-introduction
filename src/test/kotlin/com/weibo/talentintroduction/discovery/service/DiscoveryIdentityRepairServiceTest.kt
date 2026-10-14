package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonAuthorKeyRecord
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberRecord
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberWrite
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonRecord
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonWriteStatus
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.IdentityVerification
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertPromotionAuditService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
 * 01（I-1/I-2/I-3）：真实 [ExpertIndexWriterService] + HTTP 替身 —— 证据矩阵、三层版本保护、
 * 部分失败不报成功与恢复后只补缺层。这里不 mock writer，因为「仅写 proof 的条件补丁」本身是被验收的事实。
 */
class DiscoveryIdentityRepairServiceTest {

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
    private val service = DiscoveryIdentityRepairService(writer)

    private class LayerDoc(var source: MutableMap<String, Any?>, var seq: Long)

    private val docs = linkedMapOf<String, LayerDoc>()
    private val updateUrls = mutableListOf<String>()
    private val updateBodies = mutableListOf<Map<String, Any?>>()
    private var failSearch = false
    private var failUpdateLevel: String? = null
    /** 模拟「读取快照后、条件写入前」另一人的并发改动：目标层的 seq 在 CAS 前被推进。 */
    private var concurrentBumpLevel: ExpertIndexLevel? = null

    private fun key(level: ExpertIndexLevel, id: String) = "${level.name}/$id"

    private fun levelOf(indexName: String): ExpertIndexLevel? =
        ExpertIndexLevel.values().firstOrNull { indexService.indexName(it) == indexName }

    private fun put(level: ExpertIndexLevel, id: String, source: MutableMap<String, Any?>, seq: Long = 1L) {
        docs[key(level, id)] = LayerDoc(source, seq)
    }

    @BeforeEach
    fun setUp() {
        Mockito.doAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            val method = invocation.getArgument<HttpMethod>(1)
            val body = (invocation.getArgument<Any?>(2) as? HttpEntity<*>)?.body
            when {
                method == HttpMethod.POST && url.contains("/_search") -> searchResponse(body)
                method == HttpMethod.GET && url.contains("/_doc/") -> getResponse(url)
                method == HttpMethod.POST && url.contains("/_update/") -> updateResponse(url, body)
                else -> throw IllegalStateException("unexpected request $method $url")
            }
        }.`when`(restTemplate).exchange(
            Mockito.anyString(), Mockito.any(HttpMethod::class.java), Mockito.any(), Mockito.eq(JsonNode::class.java)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun searchResponse(body: Any?): ResponseEntity<JsonNode> {
        if (failSearch) throw HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR)
        val query = body as Map<String, Any?>
        val term = (query["query"] as Map<String, Any?>)["term"] as Map<String, Any?>
        val email = term["email"]
        val hits = docs.entries.mapNotNull { (storedKey, doc) ->
            val level = ExpertIndexLevel.valueOf(storedKey.substringBefore('/'))
            val id = storedKey.substringAfter('/')
            if (doc.source["email"] == email) {
                mapOf("_index" to indexService.indexName(level), "_id" to id, "_source" to doc.source)
            } else {
                null
            }
        }
        return ResponseEntity.ok(mapper.valueToTree(mapOf(
            "hits" to mapOf("total" to mapOf("value" to hits.size), "hits" to hits)
        )))
    }

    private fun getResponse(url: String): ResponseEntity<JsonNode> {
        val indexName = url.removePrefix("${properties.baseUrl}/").substringBefore("/_doc/")
        val id = url.substringAfter("/_doc/").substringBefore('?')
        val level = levelOf(indexName) ?: throw IllegalStateException("unknown index $indexName")
        val doc = docs[key(level, id)] ?: throw HttpClientErrorException(HttpStatus.NOT_FOUND)
        return ResponseEntity.ok(mapper.valueToTree(mapOf(
            "_source" to doc.source, "_seq_no" to doc.seq, "_primary_term" to 1L
        )))
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateResponse(url: String, body: Any?): ResponseEntity<JsonNode> {
        updateUrls += url
        val indexName = url.removePrefix("${properties.baseUrl}/").substringBefore("/_update/")
        val id = url.substringAfter("/_update/").substringBefore('?')
        val level = levelOf(indexName) ?: throw IllegalStateException("unknown index $indexName")
        val doc = (body as Map<String, Any?>)["doc"] as Map<String, Any?>
        updateBodies += doc
        if (failUpdateLevel == level.name) throw HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR)
        val stored = docs[key(level, id)] ?: throw HttpClientErrorException(HttpStatus.NOT_FOUND)
        if (concurrentBumpLevel == level) {
            stored.seq += 1
            concurrentBumpLevel = null
        }
        val ifSeqNo = url.substringAfter("if_seq_no=").substringBefore('&').toLong()
        if (ifSeqNo != stored.seq) throw HttpClientErrorException(HttpStatus.CONFLICT)
        doc.forEach { (field, value) -> stored.source[field] = value }
        stored.seq += 1
        return ResponseEntity.ok(mapper.valueToTree(mapOf(
            "result" to "updated", "_seq_no" to stored.seq, "_primary_term" to 1L
        )))
    }

    // ── 夹具 ────────────────────────────────────────────────────────────────

    private val evidence = "JATS_SHA256:" + "a".repeat(64)

    private fun incoming(
        email: String = "jane@uni.edu",
        given: String? = "Jane",
        family: String? = "Doe",
        evidence: String? = this.evidence,
        orcid: String? = null,
        openAlex: String? = null
    ) = AuthorEmail(email, given, family, false, null, orcid, null, openAlex, evidence)

    private fun source(
        email: String = "jane@uni.edu",
        given: String? = "Jane",
        family: String? = "Doe",
        proof: IdentityVerification? = null,
        externalIds: Map<String, String>? = null
    ): MutableMap<String, Any?> {
        val map = mutableMapOf<String, Any?>("email" to email, "givenNames" to given, "familyNames" to family)
        if (proof != null) map["identityVerification"] = proof
        if (externalIds != null) map["externalIds"] = externalIds
        return map
    }

    private fun targetProof(orcid: String? = null, openAlex: String? = null): IdentityVerification =
        DiscoveryIdentity.verified("jane@uni.edu", "Jane", "Doe", evidence, orcid, openAlex)

    // ── I-1/I-2/I-3 证据矩阵 ────────────────────────────────────────────────

    @Test
    fun `three layers with one id and one name receive the same proof by version`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, source())
        put(ExpertIndexLevel.CANDIDATE, id, source())
        put(ExpertIndexLevel.APPLICATION, id, source(), seq = 7L)

        val result = service.repairDuplicateIdentity(id, incoming())

        assertEquals(IdentityRepairOutcome.REPAIRED, result.outcome)
        assertFalse(result.retryableFailure)
        assertEquals(3, result.layers.count { it.outcome == IdentityRepairLayerOutcome.REPAIRED })
        assertEquals(3, updateUrls.size)
        assertTrue(updateUrls.all { it.contains("if_seq_no=") && it.contains("if_primary_term=1") }, updateUrls.toString())
        assertTrue(updateBodies.all { it.keys == setOf("identityVerification") }, updateBodies.toString())
        assertEquals(targetProof(), result.proof)
        docs.values.forEach { assertEquals(targetProof(), it.source["identityVerification"]) }
    }

    @Test
    fun `two business ids sharing one canonical email are a conflict with zero writes`() {
        put(ExpertIndexLevel.RAW, "EMAIL-jane@uni.edu", source())
        put(ExpertIndexLevel.CANDIDATE, "orcid-0000-0002", source())

        val result = service.repairDuplicateIdentity("EMAIL-jane@uni.edu", incoming())

        assertEquals(IdentityRepairOutcome.CONFLICT, result.outcome)
        assertTrue(updateUrls.isEmpty())
        assertTrue(docs.values.all { it.source["identityVerification"] == null })
    }

    @Test
    fun `a version conflict never retries the stale snapshot and the other layer still lands`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, source())
        put(ExpertIndexLevel.CANDIDATE, id, source(), seq = 4L)
        // 读取后、写入前有人并发改动候选层 → if_seq_no 不再匹配。
        concurrentBumpLevel = ExpertIndexLevel.CANDIDATE

        val result = service.repairDuplicateIdentity(id, incoming())

        assertEquals(IdentityRepairOutcome.PARTIAL, result.outcome)
        assertTrue(result.retryableFailure)
        assertTrue(result.layers.single { it.level == ExpertIndexLevel.RAW }.outcome == IdentityRepairLayerOutcome.REPAIRED)
        assertTrue(result.layers.single { it.level == ExpertIndexLevel.CANDIDATE }.outcome ==
            IdentityRepairLayerOutcome.VERSION_CONFLICT)
        assertTrue(updateUrls.count { it.contains("/orcid_info_candidate/_update/") } == 1,
            "409 后不得用旧快照盲目重试")
        assertEquals(null, docs[key(ExpertIndexLevel.CANDIDATE, id)]!!.source["identityVerification"])
        assertEquals(targetProof(), docs[key(ExpertIndexLevel.RAW, id)]!!.source["identityVerification"])
    }

    @Test
    fun `an existing proof such as a legacy approval is never overwritten`() {
        val id = "EMAIL-jane@uni.edu"
        val legacy = IdentityVerification(
            status = "LEGACY_APPROVED", version = DiscoveryIdentity.VERSION, email = "jane@uni.edu",
            givenNames = "Jane", familyNames = "Doe",
            source = "LEGACY_USER_APPROVED_20261002", evidenceHash = "a".repeat(64)
        )
        put(ExpertIndexLevel.RAW, id, source(proof = legacy))
        put(ExpertIndexLevel.CANDIDATE, id, source())

        val result = service.repairDuplicateIdentity(id, incoming())

        assertEquals(IdentityRepairOutcome.REPAIRED, result.outcome)
        assertEquals(1, updateUrls.size)
        assertTrue(result.layers.single { it.level == ExpertIndexLevel.RAW }.outcome ==
            IdentityRepairLayerOutcome.ALREADY_VERIFIED)
        assertEquals(legacy, docs[key(ExpertIndexLevel.RAW, id)]!!.source["identityVerification"])
        assertEquals(targetProof(), docs[key(ExpertIndexLevel.CANDIDATE, id)]!!.source["identityVerification"])
    }

    @Test
    fun `an empty stored name is an independent recovery reason with zero writes`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, source(given = null, family = null))

        val result = service.repairDuplicateIdentity(id, incoming())

        assertEquals(IdentityRepairOutcome.MISSING_NAME_NEEDS_RECOVERY, result.outcome)
        assertTrue(updateUrls.isEmpty())
        assertEquals(null, docs[key(ExpertIndexLevel.RAW, id)]!!.source["givenNames"])
    }

    @Test
    fun `an incoming without a complete name never generates a proof`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, source())

        val result = service.repairDuplicateIdentity(id, incoming(given = null))

        assertEquals(IdentityRepairOutcome.MISSING_NAME_NEEDS_RECOVERY, result.outcome)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `an invalid incoming evidence is refused without touching any layer`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, source())

        val result = service.repairDuplicateIdentity(id, incoming(evidence = "not-a-source-hash"))

        assertEquals(IdentityRepairOutcome.CONFLICT, result.outcome)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `a stored name conflict writes nothing`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, source(given = "Janet"))

        val result = service.repairDuplicateIdentity(id, incoming())

        assertEquals(IdentityRepairOutcome.CONFLICT, result.outcome)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `a cross-layer email mismatch is a conflict`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, source())
        put(ExpertIndexLevel.CANDIDATE, id, source(email = "someone-else@uni.edu"))

        val result = service.repairDuplicateIdentity(id, incoming())

        assertEquals(IdentityRepairOutcome.CONFLICT, result.outcome)
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `conflicting author ids across layers or with the incoming block every write`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, source(externalIds = mapOf("orcid" to "0000-0002-1825-0097")))
        put(ExpertIndexLevel.CANDIDATE, id, source(externalIds = mapOf("orcid" to "0000-0002-1825-0098")))

        assertEquals(IdentityRepairOutcome.CONFLICT, service.repairDuplicateIdentity(id, incoming()).outcome)
        assertTrue(updateUrls.isEmpty())

        docs.clear()
        updateUrls.clear()
        put(ExpertIndexLevel.RAW, id, source(externalIds = mapOf("orcid" to "0000-0002-1825-0097")))
        assertEquals(
            IdentityRepairOutcome.CONFLICT,
            service.repairDuplicateIdentity(id, incoming(orcid = "0000-0002-1825-0099")).outcome
        )
        assertTrue(updateUrls.isEmpty())
    }

    @Test
    fun `a missing layer is never created`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, source())

        val result = service.repairDuplicateIdentity(id, incoming())

        assertEquals(IdentityRepairOutcome.REPAIRED, result.outcome)
        assertEquals(1, updateUrls.size)
        assertTrue(updateUrls.single().contains("/orcid_info/_update/"))
        assertTrue(result.layers.single { it.level == ExpertIndexLevel.CANDIDATE }.outcome ==
            IdentityRepairLayerOutcome.MISSING)
        assertTrue(result.layers.single { it.level == ExpertIndexLevel.APPLICATION }.outcome ==
            IdentityRepairLayerOutcome.MISSING)
    }

    @Test
    fun `a layer write failure is not success and a retry only fills the remaining layer`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, source())
        put(ExpertIndexLevel.CANDIDATE, id, source())
        failUpdateLevel = ExpertIndexLevel.RAW.name

        val first = service.repairDuplicateIdentity(id, incoming())

        assertEquals(IdentityRepairOutcome.PARTIAL, first.outcome)
        assertTrue(first.retryableFailure)
        assertTrue(first.layers.single { it.level == ExpertIndexLevel.RAW }.outcome ==
            IdentityRepairLayerOutcome.WRITE_FAILED)
        assertTrue(first.layers.single { it.level == ExpertIndexLevel.CANDIDATE }.outcome ==
            IdentityRepairLayerOutcome.REPAIRED)

        failUpdateLevel = null
        val retry = service.repairDuplicateIdentity(id, incoming())

        assertEquals(IdentityRepairOutcome.REPAIRED, retry.outcome)
        assertTrue(retry.layers.single { it.level == ExpertIndexLevel.CANDIDATE }.outcome ==
            IdentityRepairLayerOutcome.ALREADY_VERIFIED, "重试只补缺层，不重写已落 proof 的层")
        assertEquals(targetProof(), docs[key(ExpertIndexLevel.RAW, id)]!!.source["identityVerification"])
        assertEquals(1, updateUrls.count { it.contains("/orcid_info_candidate/_update/") },
            "候选层只写入一次")
    }

    @Test
    fun `an email lookup failure is never treated as no duplicate`() {
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, source())
        failSearch = true

        val result = service.repairDuplicateIdentity(id, incoming())

        assertEquals(IdentityRepairOutcome.FAILED, result.outcome)
        assertTrue(result.retryableFailure)
        assertTrue(updateUrls.isEmpty())
    }

    // ── 05（I-1）：统一人物登记刷新 ──────────────────────────────────────────

    private fun personRegistrar(repo: FakeRepository): DiscoveryPersonRegistrationService {
        val personService = DiscoveryPersonService(
            repo, writer,
            Mockito.mock(ExpertContactRepository::class.java),
            Mockito.mock(ExpertEmailAliasService::class.java)
        )
        return DiscoveryPersonRegistrationService(writer, personService, repo)
    }

    @Test
    fun `two emails of the same orcid become one person with two email evidences (I-1)`() {
        val repo = FakeRepository()
        val registrar = personRegistrar(repo)
        val orcid = "0000-0002-1825-0097"
        val emailA = "a@uni.edu"
        val emailB = "b@uni.edu"
        put(ExpertIndexLevel.RAW, "EMAIL-$emailA",
            source(emailA, proof = DiscoveryIdentity.verified(emailA, "Jane", "Doe", evidence, orcid, null),
                externalIds = mapOf("orcid" to orcid)))
        put(ExpertIndexLevel.RAW, "EMAIL-$emailB",
            source(emailB, proof = DiscoveryIdentity.verified(emailB, "Jane", "Doe", evidence, orcid, null),
                externalIds = mapOf("orcid" to orcid)))

        val first = registrar.refresh("TEST", "EMAIL-$emailA")
        val second = registrar.refresh("TEST", "EMAIL-$emailB")

        assertEquals(DiscoveryPersonRefreshStatus.CREATED, first.status)
        assertEquals(DiscoveryPersonRefreshStatus.ATTACHED, second.status)
        assertEquals(2, repo.members.size, "两封邮箱证据都作为人物成员保留，而不是删掉第二邮箱")
        assertEquals(1, repo.persons.size, "同一可信 ORCID 只产生一个人物主档")
        assertEquals(2, docs.count { it.key.startsWith("RAW/") }, "原始邮箱事实仍保留两条 ES 技术记录")
    }

    @Test
    fun `a non-discovery document is skipped and never creates a person (I-1)`() {
        val repo = FakeRepository()
        val registrar = personRegistrar(repo)
        put(ExpertIndexLevel.RAW, "EMAIL-legacy@uni.edu", mutableMapOf("email" to "legacy@uni.edu"))

        val result = registrar.refresh("TEST", "EMAIL-legacy@uni.edu")

        assertEquals(DiscoveryPersonRefreshStatus.SKIPPED, result.status)
        assertTrue(repo.persons.isEmpty())
    }

    @Test
    fun `a repaired duplicate also refreshes the person registration (I-1)`() {
        val repo = FakeRepository()
        val registrar = personRegistrar(repo)
        val localService = DiscoveryIdentityRepairService(writer, registrar)
        val id = "EMAIL-jane@uni.edu"
        put(ExpertIndexLevel.RAW, id, source())

        val result = localService.repairDuplicateIdentity(id, incoming())

        assertEquals(IdentityRepairOutcome.REPAIRED, result.outcome)
        assertNotNull(result.personRegistration, "补证成功后统一刷新人物登记")
        assertEquals(DiscoveryPersonRefreshStatus.CREATED, result.personRegistration!!.status)
        assertEquals(1, repo.persons.size)
    }

    private class FakeRepository : DiscoveryPersonRepository(JdbcTemplate()) {
        val persons = linkedMapOf<String, DiscoveryPersonRecord>()
        val members = linkedMapOf<String, DiscoveryPersonMemberRecord>()
        val keys = linkedMapOf<String, String>()

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
