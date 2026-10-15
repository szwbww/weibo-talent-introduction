package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.BatchRecipientSelectionService
import com.weibo.talentintroduction.campaign.service.RecipientAdmissionState
import com.weibo.talentintroduction.campaign.service.RecipientDecision
import com.weibo.talentintroduction.campaign.service.RecipientSelection
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberRecord
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonRecord
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.IdentityVerification
import com.weibo.talentintroduction.expert.service.ExpertHitWithSort
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.expert.service.ExpertSearchUnavailableException
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 06（I-1/I-2/I-3）定向测试：混合范围、跨页、跨层、条件不拼接、精确桶数、超预算失败与只读。
 * 全部为 Mockito 单元测试：ES（ExpertSearchService）与关系仓储（DiscoveryPersonRepository）均被模拟，
 * 不触达真实数据。
 */
class DiscoveryPersonQueryServiceTest {
    private val search = Mockito.mock(ExpertSearchService::class.java)
    private val repository = Mockito.mock(DiscoveryPersonRepository::class.java)
    private val contacts = Mockito.mock(ExpertContactRepository::class.java)

    private val baseFilter: Map<String, Any> = mapOf("term" to mapOf("researchFields" to "AI"))

    private var capturedCountFilters: List<Map<String, Any>> = emptyList()
    private var capturedScrollFilters: List<Map<String, Any>> = emptyList()
    private var capturedPrefixRequests: MutableList<Pair<Int, Int>> = mutableListOf()

    /** 人物口径（featureGate 打开）服务；R-12 的两个真值服务按需注入（省略即「不可得」）。 */
    private fun service(
        clock: Clock = Clock.systemUTC(),
        selection: BatchRecipientSelectionService? = null,
        suppressionService: EmailSuppressionService? = null
    ) = DiscoveryPersonQueryService(
        expertSearchService = search,
        personRepository = repository,
        contactRepository = contacts,
        featureGate = DiscoveryPersonFeatureGate(enabled = true),
        clock = clock,
        batchRecipientSelectionService = selection,
        emailSuppressionService = suppressionService
    )

    // ── fixtures ────────────────────────────────────────────────────────────────

    private data class Doc(
        val docId: String,
        val email: String = "ada@example.com",
        val given: String = "Ada",
        val family: String = "Lovelace",
        val country: String? = "United States",
        val tags: List<String> = listOf("discovered"),
        val sort: Long = 0
    )

    /**
     * R-12：档案刻意带旧 ES `filterResult=PASSED` —— 详情绝不能再把它当成可发送许可，
     * 逐邮箱状态只能来自真实准入/抑制真值。
     */
    private fun profile(doc: Doc): ExpertProfile = ExpertProfile(
        esDocId = doc.docId,
        orcidId = doc.docId,
        email = doc.email,
        givenNames = doc.given,
        familyNames = doc.family,
        country = doc.country,
        keyword = null,
        employment = null,
        emailSource = "PAPER_FULLTEXT",
        filterResult = "PASSED",
        tags = doc.tags,
        identityVerification = IdentityVerification(
            status = "VERIFIED",
            version = DiscoveryIdentity.VERSION,
            email = doc.email.lowercase(),
            givenNames = doc.given,
            familyNames = doc.family,
            source = "JATS_SHA256",
            evidenceHash = "a".repeat(64)
        )
    )

    private fun hit(doc: Doc) = ExpertHitWithSort(profile(doc), listOf(doc.sort))

    private fun memberRow(doc: Doc, personId: String, fingerprint: String? = null): DiscoveryPersonMemberRecord {
        val fp = fingerprint ?: DiscoveryPersonIdentity.fingerprint(
            doc.docId, doc.email.lowercase(), doc.given, doc.family, emptyList()
        )
        return DiscoveryPersonMemberRecord(
            expertDocId = doc.docId,
            personId = personId,
            normalizedEmail = doc.email.lowercase(),
            identityFingerprint = fp,
            evidenceJson = "{\"source\":\"PAPER_FULLTEXT\"}",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    private fun stubFilters() {
        Mockito.`when`(
            search.expertFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyList())
        ).thenReturn(listOf(baseFilter))
    }

    private fun anyLevel(): ExpertIndexLevel =
        Mockito.any(ExpertIndexLevel::class.java) ?: ExpertIndexLevel.CANDIDATE

    private fun anyHandler(): (List<ExpertHitWithSort>) -> Boolean =
        Mockito.any<(List<ExpertHitWithSort>) -> Boolean>() ?: { true }

    private fun stubScroll(batches: List<List<ExpertHitWithSort>>) {
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedScrollFilters = invocation.arguments[1] as List<Map<String, Any>>
            @Suppress("UNCHECKED_CAST")
            val handler = invocation.arguments[4] as (List<ExpertHitWithSort>) -> Boolean
            batches.forEach { handler(it) }
            null
        }.`when`(search).scrollExpertsFilteredWithSort(anyLevel(), anyList(), anyInt(), any(), anyHandler())
    }

    private fun stubCount(total: Long) {
        Mockito.`when`(search.countExpertsComplete(anyLevel(), anyList())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedCountFilters = invocation.arguments[1] as List<Map<String, Any>>
            total
        }
    }

    private fun stubOthersPrefix(hits: List<ExpertHitWithSort>) {
        Mockito.`when`(search.searchExpertsWithSort(anyLevel(), anyList(), anyInt(), anyInt(), any()))
            .thenReturn(hits)
    }

    /** R-9：按 (from, size) 真实分页应答，并记录每页请求参数。 */
    private fun stubOthersPrefixPaged(all: List<ExpertHitWithSort>) {
        Mockito.`when`(search.searchExpertsWithSort(anyLevel(), anyList(), anyInt(), anyInt(), any()))
            .thenAnswer { invocation ->
                val offset = invocation.arguments[2] as Int
                val requestSize = invocation.arguments[3] as Int
                capturedPrefixRequests += offset to requestSize
                all.drop(offset).take(requestSize)
            }
    }

    /** R-12：人物详情的跨层/联系人骨架；逐邮箱状态真值由各用例单独注入。 */
    private fun stubDetail(vararg docs: Doc) {
        val first = docs.first()
        Mockito.`when`(repository.findMember(first.docId)).thenReturn(memberRow(first, "P1"))
        Mockito.`when`(repository.findPerson("P1")).thenReturn(
            DiscoveryPersonRecord("P1", first.docId, 1, LocalDateTime.now(), LocalDateTime.now())
        )
        Mockito.`when`(repository.findMembersByPerson("P1")).thenReturn(docs.map { memberRow(it, "P1") })
        Mockito.`when`(search.findByDocumentIdsStrict(anyLevel(), anyList())).thenReturn(docs.map { profile(it) })
        Mockito.`when`(search.findByDocumentIds(anyLevel(), anyList())).thenReturn(docs.map { profile(it) })
        Mockito.`when`(contacts.findByOrcidIdIn(anyList())).thenReturn(
            docs.mapIndexed { index, doc -> contact((index + 1).toLong(), doc.docId, "REPLIED", doc.email) }
        )
    }

    private fun admission(docId: String, state: RecipientAdmissionState, decision: String? = null) =
        RecipientDecision(
            docId = docId,
            orcidId = docId,
            admissionState = state,
            admissionDecision = decision,
            syncErrorCode = null,
            filterKeys = emptyList(),
            included = state == RecipientAdmissionState.ADMITTED
        )

    private fun stubRelations(rows: List<DiscoveryPersonMemberRecord>) {
        Mockito.`when`(repository.findMembers(anyList())).thenAnswer { invocation ->
            val ids = invocation.arguments[0] as List<String>
            rows.filter { it.expertDocId in ids }
        }
    }

    // ── I-1：混合范围先归人、去重、精确 total ─────────────────────────────────────

    @Test
    fun `groups same-person members to one and adds other partition to total`() {
        stubFilters()
        val d1 = Doc("d1", sort = 10)
        val d2 = Doc("d2", email = "ada2@example.com", sort = 20)
        val d3 = Doc("d3", email = "ada3@example.com", sort = 30)
        val d4 = Doc("d4", email = "bob@example.com", given = "Bob", family = "Byte", sort = 15)
        val other = Doc("d5", email = "other@example.com", given = "Cy", family = "Dee", tags = emptyList(), sort = 25)

        stubScroll(listOf(listOf(hit(d1), hit(d2), hit(d3), hit(d4))))
        stubRelations(listOf(memberRow(d1, "P1"), memberRow(d2, "P1"), memberRow(d3, "P1"), memberRow(d4, "P2")))
        stubCount(1L)
        stubOthersPrefix(listOf(hit(other)))

        val result = service().searchPersons(level = ExpertIndexLevel.CANDIDATE, size = 2, from = 0)

        // 其他 1 + 人物 2（P1 三个人合成一人 + P2）
        assertEquals(3L, result.totalHits)
        assertEquals(2, result.experts.size)
        // CANDIDATE 排序降序：P1 代表取命中成员中最前者（sort=30 的 d3），其次 other（25）
        assertEquals("d3", result.experts[0].profile.esDocId)
        assertEquals("d5", result.experts[1].profile.esDocId)
    }

    @Test
    fun `paginates the merged person prefix across pages without duplicating a person`() {
        stubFilters()
        val d1 = Doc("d1", sort = 10)
        val d2 = Doc("d2", email = "ada2@example.com", sort = 20)
        val d3 = Doc("d3", email = "ada3@example.com", sort = 30)
        val d4 = Doc("d4", email = "bob@example.com", given = "Bob", family = "Byte", sort = 15)
        val other = Doc("d5", email = "other@example.com", given = "Cy", family = "Dee", tags = emptyList(), sort = 25)

        stubScroll(listOf(listOf(hit(d1), hit(d2), hit(d3), hit(d4))))
        stubRelations(listOf(memberRow(d1, "P1"), memberRow(d2, "P1"), memberRow(d3, "P1"), memberRow(d4, "P2")))
        stubCount(1L)
        stubOthersPrefix(listOf(hit(other)))

        val page2 = service().searchPersons(level = ExpertIndexLevel.CANDIDATE, size = 1, from = 2)
        // 归并前缀 [P1(d3,30), other(d5,25), P2(d4,15)] → drop(2) → [P2(d4)]
        assertEquals(3L, page2.totalHits)
        assertEquals(listOf("d4"), page2.experts.map { it.profile.esDocId })
    }

    // ── R-9（V-10）：合法深分页 ───────────────────────────────────────────────────

    @Test
    fun `deep page is read in pages of at most one thousand and returns page 21 exactly`() {
        stubFilters()
        val discoveredFirst = Doc("d1", sort = 1_000_000)
        val discoveredSecond = Doc("d2", email = "ada2@example.com", sort = 999_000)
        stubScroll(listOf(listOf(hit(discoveredFirst), hit(discoveredSecond))))
        stubRelations(listOf(memberRow(discoveredFirst, "P1"), memberRow(discoveredSecond, "P1")))
        // 其他分区 1050 条，按显示排序降序（o0 最前）。
        val others = (0 until 1050).map { index ->
            hit(
                Doc(
                    docId = "o$index",
                    email = "o$index@example.com",
                    given = "G$index",
                    family = "F$index",
                    tags = emptyList(),
                    sort = (1049 - index).toLong()
                )
            )
        }
        stubCount(others.size.toLong())
        stubOthersPrefixPaged(others)

        val page21 = service().searchPersons(level = ExpertIndexLevel.CANDIDATE, size = 50, from = 1000)

        // total = 其他 1050 + 人物 1；归并序列 [P1, o0..o1049]，第 21 页（from=1000）为 o999..o1048。
        assertEquals(1051L, page21.totalHits)
        assertEquals(50, page21.experts.size)
        assertEquals("o999", page21.experts.first().profile.esDocId)
        assertEquals("o1048", page21.experts.last().profile.esDocId)
        assertEquals(50, page21.experts.map { it.profile.esDocId }.distinct().size, "边界重叠不重复")
        // 前缀按单页上限分段读取，绝不把 from+size(=1050) 当单页 size。
        assertEquals(listOf(0 to 1000, 1000 to 50), capturedPrefixRequests)
    }

    @Test
    fun `shallower page keeps the same merged ordering as the deep page`() {
        stubFilters()
        val discoveredFirst = Doc("d1", sort = 1_000_000)
        val discoveredSecond = Doc("d2", email = "ada2@example.com", sort = 999_000)
        stubScroll(listOf(listOf(hit(discoveredFirst), hit(discoveredSecond))))
        stubRelations(listOf(memberRow(discoveredFirst, "P1"), memberRow(discoveredSecond, "P1")))
        val others = (0 until 1050).map { index ->
            hit(
                Doc(
                    docId = "o$index",
                    email = "o$index@example.com",
                    given = "G$index",
                    family = "F$index",
                    tags = emptyList(),
                    sort = (1049 - index).toLong()
                )
            )
        }
        stubCount(others.size.toLong())
        stubOthersPrefixPaged(others)

        val page1 = service().searchPersons(level = ExpertIndexLevel.CANDIDATE, size = 50, from = 0)

        assertEquals(1051L, page1.totalHits)
        assertEquals("d1", page1.experts.first().profile.esDocId)
        assertEquals(
            listOf("d1") + (0 until 49).map { "o$it" },
            page1.experts.map { it.profile.esDocId },
            "浅页与深页共用同一归并排序"
        )
    }

    // ── R-10（V-13）：ES 不完整结果不发布部分精确数 ────────────────────────────────

    @Test
    fun `an incomplete discovered traversal propagates instead of publishing a partial total`() {
        stubFilters()
        stubCount(0L)
        Mockito.doThrow(
            ExpertSearchUnavailableException("ES 查询超时（timed_out=true），查询结果不可用")
        ).`when`(search).scrollExpertsFilteredWithSort(anyLevel(), anyList(), anyInt(), any(), anyHandler())

        assertThrows(ExpertSearchUnavailableException::class.java) {
            service().searchPersons(level = ExpertIndexLevel.RAW, size = 10, from = 0)
        }
    }

    @Test
    fun `an incomplete other-partition page propagates instead of returning a short page`() {
        stubFilters()
        val d1 = Doc("d1")
        stubScroll(listOf(listOf(hit(d1))))
        stubRelations(listOf(memberRow(d1, "P1")))
        stubCount(5L)
        Mockito.doThrow(
            ExpertSearchUnavailableException("ES 分片失败（_shards.failed=1），查询结果不可用")
        ).`when`(search).searchExpertsWithSort(anyLevel(), anyList(), anyInt(), anyInt(), any())

        assertThrows(ExpertSearchUnavailableException::class.java) {
            service().searchPersons(level = ExpertIndexLevel.CANDIDATE, size = 10, from = 0)
        }
    }

    // ── A13：ES 列表行携带成员数（跨页一致；非 discovered 行 = 1）─────────────────

    @Test
    fun `discovered rows carry memberCount consistent across pages while others stay one`() {
        stubFilters()
        val d1 = Doc("d1", sort = 10)
        val d2 = Doc("d2", email = "ada2@example.com", sort = 20)
        val d3 = Doc("d3", email = "ada3@example.com", sort = 30)
        val other = Doc("d5", email = "other@example.com", given = "Cy", family = "Dee", tags = emptyList(), sort = 25)

        stubScroll(listOf(listOf(hit(d1), hit(d2), hit(d3))))
        stubRelations(listOf(memberRow(d1, "P1"), memberRow(d2, "P1"), memberRow(d3, "P1")))
        stubCount(1L)
        stubOthersPrefix(listOf(hit(other)))

        val service = service()
        // 第一页：P1 代表（sort=30）在前，携带 3 个成员。
        val page1 = service.searchPersons(level = ExpertIndexLevel.CANDIDATE, size = 1, from = 0)
        assertEquals("d3", page1.experts[0].profile.esDocId)
        assertEquals(3, page1.experts[0].memberCount)

        // 第二页：其他分区每行天然一人 → 成员数 1；人口径 total 跨页一致。
        val page2 = service.searchPersons(level = ExpertIndexLevel.CANDIDATE, size = 1, from = 1)
        assertEquals("d5", page2.experts[0].profile.esDocId)
        assertEquals(1, page2.experts[0].memberCount)
        assertEquals(2L, page1.totalHits)
        assertEquals(page1.totalHits, page2.totalHits)
    }

    @Test
    fun `raw-only email does not add a candidate person`() {
        stubFilters()
        // CANDIDATE 层只见到同一人的两个成员；RAW 独有档案不在本层扫描结果内，不增加候选人数。
        val d1 = Doc("d1", sort = 10)
        val d2 = Doc("d2", email = "ada2@example.com", sort = 20)
        stubScroll(listOf(listOf(hit(d1), hit(d2))))
        stubRelations(listOf(memberRow(d1, "P1"), memberRow(d2, "P1")))
        stubCount(0L)

        val result = service().searchPersons(level = ExpertIndexLevel.CANDIDATE, size = 10, from = 0)
        assertEquals(1L, result.totalHits)
        assertEquals(1, result.experts.size)
    }

    // ── I-1：条件不拼接（两个分区共享同一套原筛选）───────────────────────────────

    @Test
    fun `both partitions reuse the same original filters with only the discovered marker differing`() {
        stubFilters()
        val d1 = Doc("d1", sort = 10)
        stubScroll(listOf(listOf(hit(d1))))
        stubRelations(listOf(memberRow(d1, "P1")))
        stubCount(0L)
        stubOthersPrefix(emptyList())

        service().searchPersons(level = ExpertIndexLevel.CANDIDATE, size = 10, from = 0)

        // 两个分区都完整保留原筛选（同一条 baseFilter），只差 discovered 标记 → 条件由同一成员满足。
        assertTrue(capturedCountFilters.contains(baseFilter))
        assertTrue(capturedScrollFilters.contains(baseFilter))
        assertTrue(
            capturedCountFilters.contains(
                mapOf("bool" to mapOf("must_not" to listOf(mapOf("term" to mapOf("tags" to "discovered")))))
            )
        )
        assertTrue(capturedScrollFilters.contains(mapOf("term" to mapOf("tags" to "discovered"))))
    }

    // ── I-3：失效关系不并入人物聚合 ──────────────────────────────────────────────

    @Test
    fun `stale relation is shown as an independent archive not merged into the person`() {
        stubFilters()
        val staleDoc = Doc("d6", email = "old@example.com", sort = 40)
        val liveDoc = Doc("d7", email = "new@example.com", sort = 30)
        stubScroll(listOf(listOf(hit(staleDoc), hit(liveDoc))))
        stubRelations(
            listOf(
                memberRow(staleDoc, "P3", fingerprint = "deadbeef"), // 指纹失配 → 待核
                memberRow(liveDoc, "P3")
            )
        )
        stubCount(0L)

        val result = service().searchPersons(level = ExpertIndexLevel.CANDIDATE, size = 10, from = 0)
        // P3（d7）与失效独立档案（d6）各自计数 → 2 人，而不是错误合并成 1。
        assertEquals(2L, result.totalHits)
        assertEquals(setOf("d6", "d7"), result.experts.map { it.profile.esDocId }.toSet())
    }

    // ── R-11（V-11）：DB 联系人只用当前有效关系聚合 ──────────────────────────────

    @Test
    fun `relation read failure surfaces as unavailable instead of an empty mapping`() {
        Mockito.`when`(repository.findMembers(anyList())).thenThrow(RuntimeException("relations down"))

        // V-13：关系读取失败不得返回空映射冒充「无人映射」—— 必须明确「关系未知/查询不可用」。
        assertThrows(DiscoveryPersonQueryUnavailableException::class.java) {
            service().resolveVerifiedPersonRelations(listOf("d1", "d6"))
        }
    }

    @Test
    fun `verified relations are returned while a changed fingerprint falls back and needs review`() {
        val live = Doc("d1")
        val changed = Doc("d6", email = "old@example.com")
        stubRelations(
            listOf(
                memberRow(live, "P1"),
                memberRow(changed, "P3", fingerprint = "deadbeef")
            )
        )
        Mockito.`when`(search.findByDocumentIdsStrict(anyLevel(), anyList()))
            .thenReturn(listOf(profile(live), profile(changed)))
        Mockito.`when`(search.findByDocumentIds(anyLevel(), anyList()))
            .thenReturn(listOf(profile(live), profile(changed)))

        val relations = service().resolveVerifiedPersonRelations(listOf("d1", "d6", "d9"))

        assertEquals("P1", relations.getValue("d1").personId)
        assertFalse(relations.getValue("d1").needsReview)
        assertNull(relations.getValue("d6").personId, "指纹变化的关系不得再聚合")
        assertTrue(relations.getValue("d6").needsReview, "无效关系须标待核")
        // 范围外（无登记关系）：不产出映射，调用方保持原联系单位。
        assertFalse(relations.containsKey("d9"))
    }

    @Test
    fun `a member whose live discovered tag is gone is no longer aggregated`() {
        val untagged = Doc("d6", tags = emptyList())
        stubRelations(listOf(memberRow(untagged, "P3")))
        Mockito.`when`(search.findByDocumentIdsStrict(anyLevel(), anyList())).thenReturn(listOf(profile(untagged)))
        Mockito.`when`(search.findByDocumentIds(anyLevel(), anyList())).thenReturn(listOf(profile(untagged)))

        val relations = service().resolveVerifiedPersonRelations(listOf("d6"))

        assertNull(relations.getValue("d6").personId, "移标后实时 discovered 范围不成立，不得聚合")
        assertTrue(relations.getValue("d6").needsReview)
    }

    // ── I-3：超预算明确失败，不返回部分数 ────────────────────────────────────────

    @Test
    fun `exceeding the discovered doc cap fails with a retryable query-unavailable error`() {
        stubFilters()
        val shared = hit(Doc("d0", sort = 0))
        val huge = List(DiscoveryPersonQueryService.MAX_DISCOVERED_DOCS + 1) { shared }
        stubScroll(listOf(huge))
        stubCount(0L)

        assertThrows(DiscoveryPersonQueryUnavailableException::class.java) {
            service().searchPersons(level = ExpertIndexLevel.RAW, size = 10, from = 0)
        }
    }

    @Test
    fun `exceeding the discovered time budget fails with a retryable query-unavailable error`() {
        stubFilters()
        val start = Instant.parse("2026-10-13T00:00:00Z")
        val clock = MutableClock(mutableListOf(start, start.plusSeconds(11)))
        val d1 = Doc("d1", sort = 10)
        stubScroll(listOf(listOf(hit(d1))))
        stubRelations(listOf(memberRow(d1, "P1")))
        stubCount(0L)

        assertThrows(DiscoveryPersonQueryUnavailableException::class.java) {
            service(clock).searchPersons(level = ExpertIndexLevel.CANDIDATE, size = 10, from = 0)
        }
    }

    // ── I-1：精确桶数（其他 ES 桶 + discovered 人物去重桶）────────────────────────

    @Test
    fun `tag buckets add other-partition doc counts to discovered person counts`() {
        stubFilters()
        val d1 = Doc("d1", tags = listOf("T1"), sort = 10)
        val d2 = Doc("d2", email = "bob@example.com", given = "Bob", family = "Byte", tags = listOf("T2"), sort = 20)
        stubScroll(listOf(listOf(hit(d1), hit(d2))))
        stubRelations(listOf(memberRow(d1, "P1"), memberRow(d2, "P2")))
        Mockito.`when`(
            search.aggregateTerms(anyLevel(), anyList(), anyString(), anyInt(), any())
        ).thenReturn(listOf("T1" to 1L))

        val tags = service().aggregateTagsPersons(ExpertIndexLevel.CANDIDATE).associate { it.tag to it.count }
        assertEquals(2L, tags["T1"]) // 其他 1 + discovered 人物 1
        assertEquals(1L, tags["T2"]) // 仅 discovered 人物 1
    }

    // ── I-2：跨层只读人物详情 ────────────────────────────────────────────────────

    @Test
    fun `person detail lists members across layers with contact ids and representative`() {
        val d1 = Doc("d1", email = "ada@example.com")
        val d2 = Doc("d2", email = "ada2@example.com")
        Mockito.`when`(repository.findMember("d1")).thenReturn(memberRow(d1, "P1"))
        Mockito.`when`(repository.findPerson("P1")).thenReturn(
            DiscoveryPersonRecord("P1", "d1", 2, LocalDateTime.now(), LocalDateTime.now())
        )
        Mockito.`when`(repository.findMembersByPerson("P1")).thenReturn(
            listOf(memberRow(d1, "P1"), memberRow(d2, "P1"))
        )
        Mockito.`when`(search.findByDocumentIdsStrict(ExpertIndexLevel.RAW, listOf("d1", "d2")))
            .thenReturn(listOf(profile(d1)))
        Mockito.`when`(search.findByDocumentIdsStrict(ExpertIndexLevel.CANDIDATE, listOf("d1", "d2")))
            .thenReturn(listOf(profile(d1), profile(d2)))
        Mockito.`when`(search.findByDocumentIdsStrict(ExpertIndexLevel.APPLICATION, listOf("d1", "d2")))
            .thenReturn(emptyList())
        Mockito.`when`(contacts.findByOrcidIdIn(listOf("d1", "d2"))).thenReturn(
            listOf(
                contact(1L, "d1", "REPLIED", "ada@example.com"),
                contact(2L, "d2", "NOT_CONTACTED", "ada2@example.com")
            )
        )

        val detail = service().personDetail("d1", ExpertIndexLevel.CANDIDATE)!!
        assertEquals("P1", detail.personId)
        assertEquals("d1", detail.representativeDocId)
        assertEquals(2, detail.members.size)
        val m1 = detail.members.first { it.docId == "d1" }
        assertEquals(1L, m1.contactId)
        assertTrue(m1.representative)
        assertEquals(listOf("RAW", "CANDIDATE"), m1.levels)
        val m2 = detail.members.first { it.docId == "d2" }
        assertEquals(2L, m2.contactId)
        assertEquals("PAPER_FULLTEXT", m2.evidenceSource)

        // 只读：详情不得写入关系或联系人。
        Mockito.verify(repository, Mockito.never()).createPerson(
            anyString(), anyString(), anyList(), anyList(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )
        Mockito.verify(repository, Mockito.never()).attachToPerson(
            anyString(), anyList(), anyList(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )
        Mockito.verify(contacts, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
    }

    @Test
    fun `person detail marks conflicting operator status as a difference without overriding`() {
        val d1 = Doc("d1")
        val d2 = Doc("d2", email = "ada2@example.com")
        Mockito.`when`(repository.findMember("d1")).thenReturn(memberRow(d1, "P1"))
        Mockito.`when`(repository.findPerson("P1")).thenReturn(
            DiscoveryPersonRecord("P1", "d1", 1, LocalDateTime.now(), LocalDateTime.now())
        )
        Mockito.`when`(repository.findMembersByPerson("P1")).thenReturn(
            listOf(memberRow(d1, "P1"), memberRow(d2, "P1"))
        )
        Mockito.`when`(search.findByDocumentIdsStrict(anyLevel(), anyList())).thenReturn(emptyList())
        Mockito.`when`(search.findByDocumentIds(anyLevel(), anyList())).thenReturn(emptyList())
        Mockito.`when`(contacts.findByOrcidIdIn(anyList())).thenReturn(
            listOf(
                contact(1L, "d1", "REPLIED", "ada@example.com"),
                contact(2L, "d2", "NOT_CONTACTED", "ada2@example.com")
            )
        )

        val detail = service().personDetail("d1", ExpertIndexLevel.CANDIDATE)!!
        assertTrue(detail.conflicts.contains("OPERATOR_STATUS_DIFFERS"))
        // 代表成员仍是 d1，其自身状态未被其他成员覆盖。
        assertEquals("REPLIED", detail.members.first { it.docId == "d1" }.operatorStatus)
        assertEquals("NOT_CONTACTED", detail.members.first { it.docId == "d2" }.operatorStatus)
    }

    @Test
    fun `person detail returns null when the docId is unknown`() {
        Mockito.`when`(repository.findMember("missing")).thenReturn(null)
        Mockito.`when`(search.findByDocumentIdsStrict(anyLevel(), anyList())).thenReturn(emptyList())
        Mockito.`when`(search.findByDocumentIds(anyLevel(), anyList())).thenReturn(emptyList())
        assertNull(service().personDetail("missing", ExpertIndexLevel.CANDIDATE))
    }

    // ── R-12（V-12）：详情展示真实逐邮箱准入与邮箱状态 ────────────────────────────

    @Test
    fun `detail shows real per-email admission and suppression instead of the legacy filterResult`() {
        val d1 = Doc("d1", email = "ada@example.com")
        val d2 = Doc("d2", email = "ada2@example.com")
        stubDetail(d1, d2)
        val selection = Mockito.mock(BatchRecipientSelectionService::class.java)
        Mockito.`when`(selection.selectAdmission(anyList())).thenReturn(
            RecipientSelection(
                included = listOf(admission("d1", RecipientAdmissionState.ADMITTED)),
                // V148 结论 HOLD → 统一 selector 归为 MANUAL_REJECTED：绝不可发送。
                excluded = listOf(admission("d2", RecipientAdmissionState.MANUAL_REJECTED, "HOLD")),
                reasonHits = emptyMap()
            )
        )
        val suppression = Mockito.mock(EmailSuppressionService::class.java)
        Mockito.`when`(suppression.isSuppressed("ada@example.com")).thenReturn(false)
        Mockito.`when`(suppression.isSuppressed("ada2@example.com")).thenReturn(true)

        val detail = service(selection = selection, suppressionService = suppression)
            .personDetail("d1", ExpertIndexLevel.CANDIDATE)!!

        // 档案带旧 filterResult=PASSED，但真实准入是 HOLD + 邮箱被抑制 → 两个成员都不是可发送。
        assertEquals("ADMITTED", detail.members.first { it.docId == "d1" }.admissionStatus)
        assertEquals("SUPPRESSED", detail.members.first { it.docId == "d2" }.admissionStatus)
        assertTrue(detail.members.none { it.admissionStatus == "PASSED" })
        assertTrue(detail.members.none { it.admissionStatus == RecipientAdmissionState.ADMITTED.name && it.docId == "d2" })
    }

    @Test
    fun `detail reports unknown admission when the per-email truth is unavailable`() {
        val d1 = Doc("d1")
        stubDetail(d1)

        // 真值服务缺失：明确未知，绝不回退成「可发送」。
        val detail = service().personDetail("d1", ExpertIndexLevel.CANDIDATE)!!
        assertEquals("UNKNOWN", detail.members.single().admissionStatus)
        assertTrue(detail.members.none { it.admissionStatus == "PASSED" })
    }

    @Test
    fun `detail never reports a stale member as sendable even when the persistent admission is ADMITTED`() {
        val d1 = Doc("d1", email = "ada@example.com")
        stubDetail(d1)
        // 关系已登记但当前指纹不符 → 失效成员（移标/证据变化）。
        Mockito.`when`(repository.findMembersByPerson("P1")).thenReturn(
            listOf(memberRow(d1, "P1", fingerprint = "deadbeef"))
        )
        val selection = Mockito.mock(BatchRecipientSelectionService::class.java)
        Mockito.`when`(selection.selectAdmission(anyList())).thenReturn(
            RecipientSelection(
                included = listOf(admission("d1", RecipientAdmissionState.ADMITTED)),
                excluded = emptyList(),
                reasonHits = emptyMap()
            )
        )
        val suppression = Mockito.mock(EmailSuppressionService::class.java)
        Mockito.`when`(suppression.isSuppressed(anyString())).thenReturn(false)

        val member = service(selection = selection, suppressionService = suppression)
            .personDetail("d1", ExpertIndexLevel.CANDIDATE)!!.members.single()

        // V-12：失效关系即使持久准入标 ADMITTED，也绝不能显示成可发送。
        assertTrue(member.stale, "移标/指纹变化的关系必须标失效")
        assertEquals("UNKNOWN", member.admissionStatus)
    }

    @Test
    fun `detail reports unknown admission when reading the per-email truth fails`() {
        val d1 = Doc("d1")
        stubDetail(d1)
        val selection = Mockito.mock(BatchRecipientSelectionService::class.java)
        Mockito.`when`(selection.selectAdmission(anyList())).thenThrow(RuntimeException("admission down"))
        val suppression = Mockito.mock(EmailSuppressionService::class.java)
        Mockito.`when`(suppression.isSuppressed(anyString())).thenReturn(false)

        val detail = service(selection = selection, suppressionService = suppression)
            .personDetail("d1", ExpertIndexLevel.CANDIDATE)!!

        assertEquals("UNKNOWN", detail.members.single().admissionStatus)
    }

    @Test
    fun `detail reports unknown admission when the suppression truth cannot be read`() {
        val d1 = Doc("d1")
        stubDetail(d1)
        val selection = Mockito.mock(BatchRecipientSelectionService::class.java)
        Mockito.`when`(selection.selectAdmission(anyList())).thenReturn(
            RecipientSelection(
                included = listOf(admission("d1", RecipientAdmissionState.ADMITTED)),
                excluded = emptyList(),
                reasonHits = emptyMap()
            )
        )
        val suppression = Mockito.mock(EmailSuppressionService::class.java)
        Mockito.`when`(suppression.isSuppressed(anyString())).thenThrow(RuntimeException("suppression down"))

        val detail = service(selection = selection, suppressionService = suppression)
            .personDetail("d1", ExpertIndexLevel.CANDIDATE)!!

        assertEquals("UNKNOWN", detail.members.single().admissionStatus)
    }

    @Test
    fun `detail keeps non-discovery archives unknown instead of sendable`() {
        val d1 = Doc("d1")
        stubDetail(d1)
        val selection = Mockito.mock(BatchRecipientSelectionService::class.java)
        Mockito.`when`(selection.selectAdmission(anyList())).thenReturn(
            RecipientSelection(
                included = listOf(admission("d1", RecipientAdmissionState.NOT_DISCOVERY)),
                excluded = emptyList(),
                reasonHits = emptyMap()
            )
        )
        val suppression = Mockito.mock(EmailSuppressionService::class.java)
        Mockito.`when`(suppression.isSuppressed(anyString())).thenReturn(false)

        val detail = service(selection = selection, suppressionService = suppression)
            .personDetail("d1", ExpertIndexLevel.CANDIDATE)!!

        assertEquals("UNKNOWN", detail.members.single().admissionStatus)
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private fun contact(id: Long, docId: String, operatorStatus: String, email: String): ExpertContact =
        ExpertContact(
            id = id,
            campaignId = 1L,
            orcidId = docId,
            expertEmail = email,
            expertName = "Ada Lovelace",
            currentStatus = "WAITING_REPLY",
            operatorStatus = operatorStatus,
            updatedAt = LocalDateTime.now().plusSeconds(id)
        )

    private class MutableClock(private val times: MutableList<Instant>) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = if (times.size > 1) times.removeAt(0) else times.first()
    }
}
