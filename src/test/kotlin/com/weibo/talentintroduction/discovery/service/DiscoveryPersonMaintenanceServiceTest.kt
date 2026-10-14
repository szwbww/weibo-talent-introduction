package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.DiscoveryPersonOutreachGuardService
import com.weibo.talentintroduction.campaign.service.DiscoveryPersonRecipientService
import com.weibo.talentintroduction.campaign.service.OutreachGuardStates
import com.weibo.talentintroduction.campaign.service.OutreachProtectionImport
import com.weibo.talentintroduction.campaign.service.PersonRecipientSelection
import com.weibo.talentintroduction.campaign.service.PersonRecipientTarget
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonAuthorKeyRecord
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonIdentity
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
 * 10（I-1/I-2/I-3）：[DiscoveryPersonMaintenanceService] 的只读复核、受限应用与回滚提案验收。
 *
 * 使用**真实** [DiscoveryPersonService] + [ExpertIndexWriterService] + HTTP 替身（与 04 测试同款手法），
 * 被验收的正是「服务端自行从 ES 重读证据并按互斥主类处置，再以 04/09 既有入口受限应用」这条事实链。
 * 09 的保护导入面用只读替身验收（不产生状态行）。真实并发/事务由既有仓储测试与 09 测试覆盖。
 */
class DiscoveryPersonMaintenanceServiceTest {

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
    private val repository = FakeRepository()
    private val personService = DiscoveryPersonService(
        repository, writer,
        Mockito.mock(ExpertContactRepository::class.java),
        Mockito.mock(ExpertEmailAliasService::class.java)
    )
    private val guard = FakeGuard(repository)
    private val recipient = FakeRecipient()
    private val service = DiscoveryPersonMaintenanceService(
        personService, repository, writer, guard, recipient
    )

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
            Mockito.anyString(), Mockito.any(HttpMethod::class.java), Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
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

    private val evidenceHash = "a".repeat(64)

    private fun discoveredSource(
        email: String = "jane@uni.edu",
        given: String? = "Jane",
        family: String? = "Doe",
        orcid: String? = null,
        openAlex: String? = null,
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
    }

    private fun put(docId: String, level: ExpertIndexLevel = ExpertIndexLevel.RAW, source: MutableMap<String, Any?>) {
        docs["${level.name}/$docId"] = source
    }

    private fun remove(docId: String) {
        docs.keys.filter { it.endsWith("/$docId") }.forEach { docs.remove(it) }
    }

    private fun preview(request: MaintenancePreviewRequest): MaintenancePreview =
        (service.preview(request) as MaintenancePreviewOutcome.Ok).preview

    private fun dispositionOf(preview: MaintenancePreview, docId: String): MaintenanceDisposition =
        preview.groups.first { docId in it.memberDocIds }.disposition

    // ── I-1：逐条互斥主类 + 计数 ─────────────────────────────────────────────

    @Test
    fun `preview classifies every frozen document into exactly one disposition`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-c", source = discoveredSource(email = "c@uni.edu", orcid = "0000-0003-3333-3333"))
        put("doc-weak", source = discoveredSource(email = "weak@uni.edu", withProof = false))
        put("doc-weak2", source = discoveredSource(email = "weak2@uni.edu", withProof = false))
        put("doc-orphan", source = discoveredSource(email = "orphan@uni.edu", orcid = "0000-0004-4444-4444"))
        // doc-gone 有意不写入 ES → 读取失败 → CHANGED_OR_FAILED
        val result = preview(
            MaintenancePreviewRequest(
                ruleVersion = DiscoveryPersonMaintenanceService.RULE_VERSION,
                frozenDocIds = listOf(
                    "doc-a", "doc-b", "doc-c", "doc-weak", "doc-weak2", "doc-gone", "doc-orphan"
                ),
                candidateGroups = listOf(
                    listOf("doc-a", "doc-b"),
                    listOf("doc-c"),
                    listOf("doc-weak", "doc-weak2"),
                    listOf("doc-gone")
                )
            )
        )

        assertEquals(MaintenanceDisposition.MERGE_READY, dispositionOf(result, "doc-a"))
        assertEquals(MaintenanceDisposition.MERGE_READY, dispositionOf(result, "doc-b"))
        assertEquals(MaintenanceDisposition.KEEP_SINGLE, dispositionOf(result, "doc-c"))
        assertEquals(MaintenanceDisposition.REVIEW_REQUIRED, dispositionOf(result, "doc-weak"))
        assertEquals(MaintenanceDisposition.REVIEW_REQUIRED, dispositionOf(result, "doc-weak2"))
        assertEquals(MaintenanceDisposition.CHANGED_OR_FAILED, dispositionOf(result, "doc-gone"))
        assertEquals(MaintenanceDisposition.KEEP_SINGLE, dispositionOf(result, "doc-orphan"))
        assertEquals(7, result.totalDocuments)
        assertEquals(7, result.classCounts.values.sum())
        assertEquals(2, result.classCounts[MaintenanceDisposition.MERGE_READY.name])
        assertEquals(2, result.classCounts[MaintenanceDisposition.KEEP_SINGLE.name])
        assertEquals(2, result.classCounts[MaintenanceDisposition.REVIEW_REQUIRED.name])
        assertEquals(1, result.classCounts[MaintenanceDisposition.CHANGED_OR_FAILED.name])
        assertTrue(result.previewDigest.isNotBlank())
    }

    @Test
    fun `preview digest binds the frozen set regardless of mutable facts`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val request = MaintenancePreviewRequest(1, listOf("doc-a", "doc-b"), listOf(listOf("doc-a", "doc-b")))
        val first = preview(request)
        val second = preview(request)
        assertEquals(first.previewDigest, second.previewDigest)
        assertEquals(first.groups.single().evidenceDigest, second.groups.single().evidenceDigest)

        // 事实变化不改变冻结集摘要（逐组指纹负责发现变化）。
        put("doc-b", source = discoveredSource(email = "b2@uni.edu", orcid = "0000-0002-1825-0097"))
        val changed = preview(request)
        assertEquals(first.previewDigest, changed.previewDigest)
        assertFalse(first.groups.single().evidenceDigest == changed.groups.single().evidenceDigest)

        // 冻结集变化 → 摘要变化。
        assertFalse(
            first.previewDigest ==
                preview(MaintenancePreviewRequest(1, listOf("doc-a"), listOf(listOf("doc-a")))).previewDigest
        )
    }

    // ── I-3：每邮箱准入/禁发原因分别判断 ──────────────────────────────────────

    @Test
    fun `per email admission and send block reasons are separate from merge readiness`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        // 先登记 doc-a（形成关系），使人物级禁发状态可被预览读取。
        service.apply(mergeReadyRequest(listOf("doc-a")).second)
        guard.state = OutreachGuardStates.SENT
        recipient.excludedEmail = "a@uni.edu"

        val result = preview(
            MaintenancePreviewRequest(1, listOf("doc-a", "doc-b"), listOf(listOf("doc-a", "doc-b")))
        )
        val group = result.groups.single()
        assertTrue(group.canMerge, "合并判断不受发送保护影响")
        assertFalse(group.canSend, "存在已发送保护即不可发送")
        val memberA = group.members.first { it.docId == "doc-a" }
        assertEquals("OUTREACH_ALREADY_SENT", memberA.sendBlockReason)
        assertTrue(memberA.historyReference)
        assertNotNull(memberA.admissionReason, "人工/准入排除作为只读准入原因暴露")
    }

    // ── I-2：受限应用 + 幂等 ─────────────────────────────────────────────────

    private fun mergeReadyRequest(docIds: List<String>): Pair<MaintenancePreview, MaintenanceApplyRequest> {
        val preview = preview(MaintenancePreviewRequest(1, docIds, listOf(docIds)))
        val group = preview.groups.single()
        return preview to MaintenanceApplyRequest(
            ruleVersion = 1,
            previewDigest = preview.previewDigest,
            frozenDocIds = docIds,
            groups = listOf(
                MaintenanceApplyGroup(docIds, group.evidenceDigest, group.relationDigest)
            )
        )
    }

    @Test
    fun `apply registers the group once and rerun is UNCHANGED`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val (_, request) = mergeReadyRequest(listOf("doc-a", "doc-b"))

        val first = (service.apply(request) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(ApplyStatus.APPLIED, first.status)
        assertNotNull(first.personId)
        assertEquals(2, first.memberCount)
        assertEquals(1, repository.persons.size)
        assertEquals(2, repository.members.size)

        val second = (service.apply(request) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(ApplyStatus.UNCHANGED, second.status)
        assertEquals(first.personId, second.personId)
        assertEquals(2, repository.members.size, "重复 apply 0 新增")
    }

    @Test
    fun `apply refuses a mismatched preview digest`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val (_, request) = mergeReadyRequest(listOf("doc-a", "doc-b"))
        val rejected = service.apply(request.copy(previewDigest = "deadbeef"))
        assertEquals(MaintenanceReasonCodes.PREVIEW_DIGEST_MISMATCH, (rejected as MaintenanceApplyOutcome.Rejected).reason)
        assertTrue(repository.persons.isEmpty())
    }

    @Test
    fun `apply refuses a mismatched expected fingerprint`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val (_, request) = mergeReadyRequest(listOf("doc-a", "doc-b"))
        val group = request.groups.single().copy(expectedFingerprint = "0".repeat(64))
        val result = (service.apply(request.copy(groups = listOf(group))) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(ApplyStatus.CHANGED_OR_FAILED, result.status)
        assertEquals(MaintenanceReasonCodes.FINGERPRINT_MISMATCH, result.reason)
        assertTrue(repository.persons.isEmpty())
    }

    @Test
    fun `apply of a single unbound document registers a singleton without author keys`() {
        put("doc-weak", source = discoveredSource(email = "weak@uni.edu", withProof = false))
        val (_, request) = mergeReadyRequest(listOf("doc-weak"))
        val result = (service.apply(request) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(ApplyStatus.APPLIED, result.status)
        assertEquals(1, result.memberCount)
        assertTrue(repository.keys.isEmpty(), "缺绑定证据不得创建作者键")
    }

    // ── I-2/I-3：变化/中断与并发 ─────────────────────────────────────────────

    @Test
    fun `a member that changed after preview fails the whole group with no partial merge`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val (_, request) = mergeReadyRequest(listOf("doc-a", "doc-b"))

        // 预览后移除一个成员的 discovered 标签档案 → apply 实时复核失败。
        remove("doc-b")
        val result = (service.apply(request) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(ApplyStatus.CHANGED_OR_FAILED, result.status)
        assertTrue(repository.persons.isEmpty(), "整组不部分合并")
        assertTrue(repository.members.isEmpty())

        // 恢复后（中断/竞态消失）同请求可正常应用。
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val retry = (service.apply(request) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(ApplyStatus.APPLIED, retry.status)
        assertEquals(2, repository.members.size)
    }

    @Test
    fun `a concurrent registration conflict is re-read and re-verified`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val (_, request) = mergeReadyRequest(listOf("doc-a", "doc-b"))
        repository.rejectNextCreate = true

        val result = (service.apply(request) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(ApplyStatus.APPLIED, result.status)
        assertEquals(1, repository.persons.size, "冲突后重新读并复验，不产生重复主档")
        assertEquals(2, repository.members.size)
    }

    // ── I-3：回滚只提案 ──────────────────────────────────────────────────────

    @Test
    fun `rollback preview proposes and refuses unsafe restoration`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val (_, request) = mergeReadyRequest(listOf("doc-a", "doc-b"))
        service.apply(request)
        val personId = repository.persons.keys.single()

        val safe = service.rollbackPreview(MaintenanceRollbackRequest(1, listOf(listOf("doc-a", "doc-b"))))
        val proposal = (safe as MaintenanceRollbackOutcome.Ok).proposals.single()
        assertEquals(personId, proposal.personId)
        assertTrue(proposal.safeToRollback)
        assertEquals(2, proposal.affectedCount)

        // 后续成员加入 → 不可自动拆组。
        repository.members["doc-c"] = memberRecord("doc-c", personId)
        val unsafe = service.rollbackPreview(MaintenanceRollbackRequest(1, listOf(listOf("doc-a", "doc-b"))))
        val unsafeProposal = (unsafe as MaintenanceRollbackOutcome.Ok).proposals.single()
        assertFalse(unsafeProposal.safeToRollback)
        assertEquals(MaintenanceReasonCodes.ROLLBACK_UNSAFE, unsafeProposal.reason)

        // 已有成功首发 → 不可恢复。
        guard.state = OutreachGuardStates.SENT
        val sent = service.rollbackPreview(MaintenanceRollbackRequest(1, listOf(listOf("doc-c"))))
        val sentProposal = (sent as MaintenanceRollbackOutcome.Ok).proposals.single()
        assertFalse(sentProposal.safeToRollback)
    }

    @Test
    fun `preview rejects a mismatched rule version and an empty frozen list`() {
        assertEquals(
            MaintenanceReasonCodes.RULE_VERSION_MISMATCH,
            (service.preview(MaintenancePreviewRequest(999, listOf("doc-a"), emptyList())) as MaintenancePreviewOutcome.Rejected).reason
        )
        assertEquals(
            MaintenanceReasonCodes.EMPTY_FROZEN_LIST,
            (service.preview(MaintenancePreviewRequest(1, emptyList(), emptyList())) as MaintenancePreviewOutcome.Rejected).reason
        )
    }

    // ── I-1：每冻结 ID 恰一个处置（重叠候选组不得重复累计） ────────────────────

    @Test
    fun `overlapping candidate groups are rejected instead of double counted`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val outcome = service.preview(
            MaintenancePreviewRequest(
                ruleVersion = 1,
                frozenDocIds = listOf("doc-a", "doc-b"),
                candidateGroups = listOf(listOf("doc-a", "doc-b"), listOf("doc-a"))
            )
        )
        assertEquals(
            MaintenanceReasonCodes.OVERLAPPING_CANDIDATE_GROUPS,
            (outcome as MaintenancePreviewOutcome.Rejected).reason
        )
    }

    @Test
    fun `overlapping apply groups are rejected`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val (_, request) = mergeReadyRequest(listOf("doc-a", "doc-b"))
        val overlapping = request.copy(
            groups = request.groups + MaintenanceApplyGroup(listOf("doc-a"), request.groups.single().expectedFingerprint)
        )
        assertEquals(
            MaintenanceReasonCodes.OVERLAPPING_CANDIDATE_GROUPS,
            (service.apply(overlapping) as MaintenanceApplyOutcome.Rejected).reason
        )
        assertTrue(repository.persons.isEmpty())
    }

    // ── I-1/I-3：全量冻结清单与本次选择子集分离（试点/断点续跑） ────────────────

    @Test
    fun `pilot subset then resume share one frozen list identity and refuse non frozen ids`() {
        listOf("doc-a" to "a@uni.edu", "doc-b" to "b@uni.edu", "doc-c" to "c@uni.edu", "doc-d" to "d@uni.edu")
            .forEach { (docId, email) ->
                put(docId, source = discoveredSource(email = email, orcid = "0000-0002-1825-0097"))
            }
        val frozen = listOf("doc-a", "doc-b", "doc-c", "doc-d")
        val groups = listOf(listOf("doc-a", "doc-b"), listOf("doc-c"), listOf("doc-d"))
        val preview = preview(MaintenancePreviewRequest(1, frozen, groups))
        val byKey = preview.groups.associateBy { it.groupKey }
        fun keyOf(docIds: List<String>): String = DiscoveryIdentity.hash(
            docIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted().joinToString("\u0000")
        )
        fun applyGroup(docIds: List<String>) = MaintenanceApplyGroup(
            docIds,
            byKey.getValue(keyOf(docIds)).evidenceDigest,
            byKey.getValue(keyOf(docIds)).relationDigest
        )

        // 试点：只提交前两组（同一全量冻结清单 + 同一预览摘要）。
        val pilot = service.apply(
            MaintenanceApplyRequest(1, preview.previewDigest, frozen, listOf(applyGroup(listOf("doc-a", "doc-b")), applyGroup(listOf("doc-c"))))
        ) as MaintenanceApplyOutcome.Ok
        assertEquals(2, pilot.results.size)
        assertTrue(pilot.results.all { it.status == ApplyStatus.APPLIED })
        assertEquals(3, repository.members.size, "试点 3 条成员落库")

        // 断点续跑：只剩最后一组，仍用同一摘要与同一全量清单。
        val resumed = service.apply(
            MaintenanceApplyRequest(1, preview.previewDigest, frozen, listOf(applyGroup(listOf("doc-d"))))
        ) as MaintenanceApplyOutcome.Ok
        assertEquals(ApplyStatus.APPLIED, resumed.results.single().status)
        assertEquals(4, repository.members.size)
        assertEquals(1, repository.persons.size)

        // 夹入非冻结 ID：明确拒绝且零新增。
        val outside = service.apply(
            MaintenanceApplyRequest(1, preview.previewDigest, frozen, listOf(MaintenanceApplyGroup(listOf("doc-x"), "0".repeat(64))))
        ) as MaintenanceApplyOutcome.Ok
        assertEquals(ApplyStatus.CHANGED_OR_FAILED, outside.results.single().status)
        assertNull(repository.members["doc-x"])
    }

    // ── I-1/I-3：expected 绑定完整保护事实与关系版本 ────────────────────────────

    @Test
    fun `expected state binds proof hash layer version and manual decision`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val (_, request) = mergeReadyRequest(listOf("doc-a", "doc-b"))

        // 只改 proof 摘要 → 旧预览失配、零关系写。
        put(
            "doc-b",
            source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097").apply {
                put(
                    "identityVerification",
                    IdentityVerification(
                        "VERIFIED", DiscoveryIdentity.VERSION, "b@uni.edu", "Jane", "Doe",
                        "JATS_SHA256", "b".repeat(64), "0000-0002-1825-0097", null
                    )
                )
            }
        )
        val proofChanged = (service.apply(request) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(ApplyStatus.CHANGED_OR_FAILED, proofChanged.status)
        assertEquals(MaintenanceReasonCodes.FINGERPRINT_MISMATCH, proofChanged.reason)

        // 恢复 proof，但只改一层版本（layerVersions）→ 仍失配。
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        put(
            "doc-b",
            level = ExpertIndexLevel.CANDIDATE,
            source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097")
                .apply { put("updatedAt", "2026-10-14T00:00:00") }
        )
        val versionChanged = (service.apply(request) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(MaintenanceReasonCodes.FINGERPRINT_MISMATCH, versionChanged.reason)

        // 恢复层，只改人工准入决定 → 仍失配。
        docs.keys.filter { it.endsWith("/doc-b") && it.startsWith("CANDIDATE") }
            .forEach { docs[it] = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097") }
        recipient.excludedEmail = "b@uni.edu"
        val decisionChanged = (service.apply(request) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(MaintenanceReasonCodes.FINGERPRINT_MISMATCH, decisionChanged.reason)
        assertTrue(repository.persons.isEmpty(), "失配组零关系写")
    }

    @Test
    fun `a third party member added after apply is refused instead of resumed`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val (_, request) = mergeReadyRequest(listOf("doc-a", "doc-b"))
        service.apply(request)
        val personId = repository.persons.keys.single()
        repository.members["doc-c"] = memberRecord("doc-c", personId)

        val retry = (service.apply(request) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(ApplyStatus.CHANGED_OR_FAILED, retry.status)
        assertEquals(MaintenanceReasonCodes.RELATION_CHANGED, retry.reason)
        assertEquals(3, repository.members.size, "拒绝时零新增成员")
    }

    // ── I-3：必需事实读取失败不得当作「无事实」 ─────────────────────────────────

    @Test
    fun `a protection read failure fails the group instead of reporting sendable`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        // 先登记 doc-a：人物级保护读取只在已有人物关系时发生。
        service.apply(mergeReadyRequest(listOf("doc-a")).second)
        guard.readFailure = true

        val result = preview(MaintenancePreviewRequest(1, listOf("doc-a", "doc-b"), listOf(listOf("doc-a", "doc-b"))))
        val group = result.groups.single()
        assertEquals(MaintenanceDisposition.CHANGED_OR_FAILED, group.disposition)
        assertEquals(MaintenanceReasonCodes.HISTORY_READ_FAILED, group.reason)
        assertFalse(group.canSend, "保护读取失败不得显示可发送")
    }

    // ── I-2/I-3：历史保护持久导入与失败整组回滚 ────────────────────────────────

    @Test
    fun `history protection is persisted through the guard and import failure rolls back the group`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-c", source = discoveredSource(email = "c@uni.edu", orcid = "0000-0003-3333-3333"))
        put("doc-d", source = discoveredSource(email = "d@uni.edu", orcid = "0000-0003-3333-3333"))
        guard.state = OutreachGuardStates.SENT

        val (_, ok) = mergeReadyRequest(listOf("doc-a", "doc-b"))
        val applied = (service.apply(ok) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(ApplyStatus.APPLIED, applied.status)
        assertTrue(applied.protectionImported, "已有成功历史必须经 09 持久化为保护")

        guard.importFailure = true
        val (_, failing) = mergeReadyRequest(listOf("doc-c", "doc-d"))
        val failed = (service.apply(failing) as MaintenanceApplyOutcome.Ok).results.single()
        assertEquals(ApplyStatus.CHANGED_OR_FAILED, failed.status)
        assertEquals(MaintenanceReasonCodes.PROTECTION_IMPORT_FAILED, failed.reason)
        assertFalse(failed.protectionImported, "导入失败不得报已导入")
        assertEquals(0, failed.memberCount, "失败组不得报告成员数（真实回滚由整组同库事务保证）")
    }

    @Test
    fun `rollback refuses an in flight protection`() {
        put("doc-a", source = discoveredSource(email = "a@uni.edu", orcid = "0000-0002-1825-0097"))
        put("doc-b", source = discoveredSource(email = "b@uni.edu", orcid = "0000-0002-1825-0097"))
        val (_, request) = mergeReadyRequest(listOf("doc-a", "doc-b"))
        service.apply(request)

        guard.state = OutreachGuardStates.SENDING
        val inflight = service.rollbackPreview(MaintenanceRollbackRequest(1, listOf(listOf("doc-a", "doc-b"))))
        val proposal = (inflight as MaintenanceRollbackOutcome.Ok).proposals.single()
        assertFalse(proposal.safeToRollback, "SENDING 进行中不得标为可回滚")
        assertEquals(MaintenanceReasonCodes.ROLLBACK_UNSAFE, proposal.reason)
    }

    // ── 替身 ─────────────────────────────────────────────────────────────────

    private fun memberRecord(docId: String, personId: String): DiscoveryPersonMemberRecord {
        val now = LocalDateTime.parse("2026-10-13T00:00:00")
        return DiscoveryPersonMemberRecord(docId, personId, null, "f".repeat(64), null, now, now)
    }

    private class FakeGuard(repository: DiscoveryPersonRepository) :
        DiscoveryPersonOutreachGuardService(JdbcTemplate(), repository) {
        var state: String? = null
        var readFailure = false
        var importFailure = false
        var importCalls = 0

        override fun stateOf(personId: String, mailType: String): String? {
            if (readFailure) throw IllegalStateException("guard read failed")
            return state
        }

        override fun importExistingProtection(personId: String, mailType: String): OutreachProtectionImport {
            importCalls++
            if (readFailure) throw IllegalStateException("guard read failed")
            if (importFailure) throw IllegalStateException("guard import failed")
            return OutreachProtectionImport(state ?: "", created = false, recipient = null)
        }
    }

    private class FakeRecipient : DiscoveryPersonRecipientService() {
        var excludedEmail: String? = null
        override fun selectAdmission(
            profiles: List<com.weibo.talentintroduction.expert.domain.ExpertProfile>,
            onPerEmailSelection: ((com.weibo.talentintroduction.campaign.service.RecipientSelection) -> Unit)?
        ): PersonRecipientSelection {
            val profile = profiles.firstOrNull()
            val target = PersonRecipientTarget(
                personId = null,
                selectedDocId = profile?.esDocId ?: "?",
                selectedEmail = profile?.email,
                memberDocIds = listOfNotNull(profile?.esDocId),
                exclusionReasons = if (excludedEmail != null && profile?.email == excludedEmail) listOf("ADMISSION_REJECTED") else emptyList()
            )
            return if (target.exclusionReasons.isEmpty()) {
                PersonRecipientSelection(listOf(target), emptyList(), emptyMap())
            } else {
                PersonRecipientSelection(emptyList(), listOf(target), mapOf("ADMISSION_REJECTED" to 1))
            }
        }
    }

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
