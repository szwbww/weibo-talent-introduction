package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberRecord
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonRecord
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonScope
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonFeatureGate
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewDecision
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewAdmissionKey
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewResolvedAdmission
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewService
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * 08（I-1/I-2/I-3）：人物级选择器真值表。覆盖跨页成员、默认不合格改选次合格、
 * 人物级 HOLD/已联系保护、scope 差异、重试沿用原址、范围外逐邮箱一致。
 */
class DiscoveryPersonRecipientServiceTest {
    private val review = Mockito.mock(DiscoveryReviewService::class.java)
    private val selector = BatchRecipientSelectionService(review)
    private val personRepository = Mockito.mock(DiscoveryPersonRepository::class.java)
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val suppression = Mockito.mock(EmailSuppressionService::class.java)
    private val contacts = Mockito.mock(ExpertContactRepository::class.java)

    private val service = DiscoveryPersonRecipientService(
        batchRecipientSelectionService = selector,
        discoveryPersonRepository = personRepository,
        expertSearchService = expertSearchService,
        emailSuppressionService = suppression,
        expertContactRepository = contacts,
        // I-3（R-1）：总开关默认关闭；本用例显式开启以覆盖人物归并的**启用路径**。
        featureGate = DiscoveryPersonFeatureGate(enabled = true)
    )

    private val loadedProfiles = mutableMapOf<String, ExpertProfile>()

    private fun scope(
        mailType: String = "ADMISSION_ONLY",
        funnelLevels: Set<String> = setOf("CANDIDATE")
    ) = RecipientScope(
        mailType = mailType,
        funnelLevels = funnelLevels,
        tags = emptyList(),
        regions = emptyList(),
        emailDomains = emptyList(),
        discipline = null
    )

    private fun expert(
        docId: String,
        email: String = "$docId@example.org",
        level: Int? = null,
        discovery: Boolean = true
    ): ExpertProfile = ExpertProfile(
        orcidId = docId,
        esDocId = docId,
        email = email,
        givenNames = "Given",
        familyNames = "Family",
        country = "China",
        keyword = "kw",
        employment = "Employment",
        emailSource = if (discovery) "PAPER_FULLTEXT" else null,
        emailVerifiedLevel = level
    )

    private fun member(docId: String, personId: String, defaultEmail: String? = null) =
        DiscoveryPersonMemberRecord(
            expertDocId = docId,
            personId = personId,
            normalizedEmail = defaultEmail,
            identityFingerprint = "fp-$docId",
            evidenceJson = null,
            createdAt = LocalDateTime.of(2026, 8, 1, 12, 0),
            updatedAt = LocalDateTime.of(2026, 8, 1, 12, 0)
        )

    private fun stubPerson(personId: String, members: List<DiscoveryPersonMemberRecord>) {
        Mockito.`when`(personRepository.findPersonIdsByDocIds(Mockito.anyCollection())).thenAnswer { invocation ->
            val ids = invocation.getArgument<Collection<String>>(0)
            members.filter { it.expertDocId in ids }.associate { it.expertDocId to it.personId }
        }
        Mockito.`when`(personRepository.findMembersByPerson(personId)).thenReturn(members)
        members.forEach { m ->
            Mockito.`when`(personRepository.findMember(m.expertDocId)).thenReturn(m)
        }
    }

    private fun stubExpertSearch(profiles: List<ExpertProfile>) {
        loadedProfiles.clear()
        profiles.forEach { loadedProfiles[it.esDocId ?: it.orcidId] = it }
        val answer = org.mockito.stubbing.Answer<List<ExpertProfile>> { invocation ->
            val ids = invocation.getArgument<List<String>>(1)
            ids.mapNotNull { loadedProfiles[it] }
        }
        Mockito.`when`(expertSearchService.findByDocumentIds(anyLevel(), Mockito.anyList()))
            .thenAnswer(answer)
    }

    private fun anyLevel(): ExpertIndexLevel =
        Mockito.any(ExpertIndexLevel::class.java) ?: ExpertIndexLevel.CANDIDATE

    private fun stubAdmissions(vararg decisions: Pair<String, String>) {
        val byDoc = decisions.toMap()
        Mockito.`when`(review.resolveAdmissionBatch(Mockito.anyList())).thenAnswer { invocation ->
            val keys = invocation.getArgument<List<DiscoveryReviewAdmissionKey>>(0)
            keys.associate { key ->
                val decision = byDoc[key.docId]
                key.docId to DiscoveryReviewResolvedAdmission(
                    docId = key.docId,
                    decision = decision,
                    admitted = decision in setOf(
                        DiscoveryReviewDecision.AUTO_PASSED.name,
                        DiscoveryReviewDecision.MANUAL_APPROVED.name,
                        DiscoveryReviewDecision.LEGACY_APPROVED.name
                    ),
                    manual = decision == DiscoveryReviewDecision.MANUAL_APPROVED.name,
                    initialized = decision != null,
                    identityChanged = false,
                    revision = if (decision == null) 0L else 1L,
                    syncErrorCode = null
                )
            }
        }
    }

    private fun admitAll(vararg docIds: String) =
        stubAdmissions(*docIds.map { it to DiscoveryReviewDecision.AUTO_PASSED.name }.toTypedArray())

    @Test
    fun `selects exactly one qualified address per person across pages (I-1 I-2)`() {
        // 同人三邮箱分散页：d1 默认被抑制、d2 合格、d3 待核（无验证级别）。
        val d1 = expert("d1", level = 3)
        val d2 = expert("d2", level = 1)
        val d3 = expert("d3", level = null)
        val members = listOf(
            member("d1", "P1", defaultEmail = "d1@example.org"),
            member("d2", "P1"),
            member("d3", "P1")
        )
        stubPerson("P1", members)
        stubExpertSearch(listOf(d1, d2, d3))
        admitAll("d1", "d2", "d3")
        Mockito.`when`(suppression.isSuppressed("d1@example.org")).thenReturn(true)
        Mockito.`when`(contacts.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())

        // 第 1 页只出现默认邮箱 d1（被抑制）——不得因首个地址不合格而漏人。
        val page1 = service.select(scope(), listOf(d1))
        assertEquals(setOf("d2"), page1.selectedDocIds)
        assertEquals("d2@example.org", page1.included.single().selectedEmail)
        assertEquals(setOf("d1", "d2", "d3"), page1.included.single().memberDocIds.toSet())

        // 第 2 页出现 d2 —— 与第 1 页选中同一个人、同一地址。
        val page2 = service.select(scope(), listOf(d2))
        assertEquals(setOf("d2"), page2.selectedDocIds)

        // 第 3 页出现 d3 —— 仍解析为选中 d2，不因换页而多发。
        val page3 = service.select(scope(), listOf(d3))
        assertEquals(setOf("d2"), page3.selectedDocIds)
    }

    @Test
    fun `picks qualified non-default address when the default member is filtered (I-1)`() {
        // 默认 d1 显式条件不合格（scope 邮箱域限制），d2 合格 → 选 d2。
        val d1 = expert("d1", email = "a@blocked.com", level = 5)
        val d2 = expert("d2", email = "b@allowed.com", level = 1)
        stubPerson("P1", listOf(member("d1", "P1", defaultEmail = "a@blocked.com"), member("d2", "P1")))
        stubExpertSearch(listOf(d1, d2))
        admitAll("d1", "d2")
        Mockito.`when`(contacts.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())

        val selection = service.select(
            scope().copy(emailDomains = listOf("allowed.com")),
            listOf(d1)
        )

        assertEquals(setOf("d2"), selection.selectedDocIds)
        assertEquals("b@allowed.com", selection.included.single().selectedEmail)
    }

    @Test
    fun `person is blocked when any member is manually rejected (I-1)`() {
        val d1 = expert("d1")
        val d2 = expert("d2")
        stubPerson("P1", listOf(member("d1", "P1"), member("d2", "P1")))
        stubExpertSearch(listOf(d1, d2))
        stubAdmissions("d1" to DiscoveryReviewDecision.AUTO_PASSED.name, "d2" to DiscoveryReviewDecision.HOLD.name)
        Mockito.`when`(contacts.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())

        val selection = service.select(scope(), listOf(d1, d2))

        assertTrue(selection.included.isEmpty())
        assertEquals(
            listOf(RecipientAdmissionReasonKeys.MANUAL_REJECTED),
            selection.excluded.single().exclusionReasons
        )
        assertEquals("P1", selection.excluded.single().personId)
    }

    @Test
    fun `person is blocked when any member is already contacted (I-1)`() {
        val d1 = expert("d1")
        val d2 = expert("d2")
        stubPerson("P1", listOf(member("d1", "P1"), member("d2", "P1")))
        stubExpertSearch(listOf(d1, d2))
        admitAll("d1", "d2")
        Mockito.`when`(contacts.findByOrcidIdIn(Mockito.anyList())).thenReturn(
            listOf(
                com.weibo.talentintroduction.campaign.domain.ExpertContact(
                    id = 9L, campaignId = 1L, orcidId = "d2",
                    expertEmail = "d2@example.org", expertName = null, currentStatus = "NEW",
                    boundSenderAccountCode = "chen"
                )
            )
        )

        val selection = service.select(scope(), listOf(d1))

        assertTrue(selection.included.isEmpty())
        assertEquals(
            listOf(PersonRecipientReasonKeys.ALREADY_CONTACTED),
            selection.excluded.single().exclusionReasons
        )
    }

    @Test
    fun `person with all members suppressed is excluded (I-1)`() {
        val d1 = expert("d1")
        val d2 = expert("d2")
        stubPerson("P1", listOf(member("d1", "P1"), member("d2", "P1")))
        stubExpertSearch(listOf(d1, d2))
        admitAll("d1", "d2")
        Mockito.`when`(suppression.isSuppressed(Mockito.anyString())).thenReturn(true)
        Mockito.`when`(contacts.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())

        val selection = service.select(scope(), listOf(d1))

        assertTrue(selection.included.isEmpty())
        assertTrue(selection.excluded.single().exclusionReasons.contains(PersonRecipientReasonKeys.SUPPRESSED))
    }

    @Test
    fun `safe retry keeps the original address and never switches members (I-2)`() {
        val d1 = expert("d1", level = 1)
        val d2 = expert("d2", level = 5)
        stubPerson("P1", listOf(member("d1", "P1"), member("d2", "P1")))
        stubExpertSearch(listOf(d1, d2))
        admitAll("d1", "d2")
        Mockito.`when`(contacts.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())

        // 无偏好时按验证级别降序选 d2；指定 preferred=d1 时沿用原址 d1。
        assertEquals(setOf("d2"), service.select(scope(), listOf(d1)).selectedDocIds)
        assertEquals(
            setOf("d1"),
            service.select(scope(), listOf(d1), preferredDocIds = setOf("d1")).selectedDocIds
        )
    }

    @Test
    fun `retry preferred address ineligible is excluded instead of switching (I-2)`() {
        val d1 = expert("d1")
        val d2 = expert("d2")
        stubPerson("P1", listOf(member("d1", "P1"), member("d2", "P1")))
        stubExpertSearch(listOf(d1, d2))
        admitAll("d1", "d2")
        Mockito.`when`(suppression.isSuppressed("d1@example.org")).thenReturn(true)
        Mockito.`when`(contacts.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())

        val selection = service.select(scope(), listOf(d1), preferredDocIds = setOf("d1"))

        assertTrue(selection.included.isEmpty())
        assertEquals(
            listOf(PersonRecipientReasonKeys.PREFERRED_ADDRESS_INELIGIBLE),
            selection.excluded.single().exclusionReasons
        )
    }

    @Test
    fun `scope drives member loading and admission judgement (I-2 I-3)`() {
        val d1 = expert("d1")
        val d2 = expert("d2")
        stubPerson("P1", listOf(member("d1", "P1"), member("d2", "P1")))
        stubExpertSearch(listOf(d1, d2))
        admitAll("d1", "d2")
        Mockito.`when`(contacts.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())

        service.select(scope(funnelLevels = setOf("CANDIDATE", "APPLICATION")), listOf(d1))

        // 未出现在本批的成员 d2 按 scope 的 funnelLevels 逐层读取。
        Mockito.verify(expertSearchService).findByDocumentIds(
            com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.CANDIDATE, listOf("d2")
        )
        Mockito.verify(expertSearchService).findByDocumentIds(
            com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.APPLICATION, listOf("d2")
        )
    }

    @Test
    fun `raw hold member not on the candidate page still blocks another qualifying email (I-1)`() {
        // d1 只在 RAW 层（不在候选页、不在 scope 层）且被人工 HOLD；d2 是本页合格成员。
        val d1 = expert("d1")
        val d2 = expert("d2")
        stubPerson("P1", listOf(member("d1", "P1"), member("d2", "P1")))
        Mockito.`when`(expertSearchService.findByDocumentIds(ExpertIndexLevel.RAW, listOf("d1")))
            .thenReturn(listOf(d1))
        Mockito.`when`(expertSearchService.findByDocumentIds(ExpertIndexLevel.CANDIDATE, listOf("d1")))
            .thenReturn(emptyList())
        Mockito.`when`(expertSearchService.findByDocumentIds(ExpertIndexLevel.APPLICATION, listOf("d1")))
            .thenReturn(emptyList())
        stubAdmissions("d1" to DiscoveryReviewDecision.HOLD.name, "d2" to DiscoveryReviewDecision.AUTO_PASSED.name)
        Mockito.`when`(contacts.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())

        val selection = service.select(scope(), listOf(d2))

        assertTrue(selection.included.isEmpty())
        assertEquals(
            listOf(RecipientAdmissionReasonKeys.MANUAL_REJECTED),
            selection.excluded.single().exclusionReasons
        )
    }

    @Test
    fun `member profile that cannot be read fails the whole person closed (I-1)`() {
        val d1 = expert("d1")
        stubPerson("P1", listOf(member("d1", "P1"), member("d2", "P1")))
        // 只加载得到 d1：d2 读不到任何层档案 → 整个人物 fail-closed，绝不因「本页看不到」而放行。
        stubExpertSearch(listOf(d1))
        admitAll("d1", "d2")
        Mockito.`when`(contacts.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())

        val selection = service.select(scope(), listOf(d1))

        assertTrue(selection.included.isEmpty())
        assertEquals(
            listOf(PersonRecipientReasonKeys.PERSON_UNREADABLE),
            selection.excluded.single().exclusionReasons
        )
    }

    @Test
    fun `unmapped live-discovered profile is excluded instead of per-archive sending (I-3)`() {
        Mockito.`when`(personRepository.findPersonIdsByDocIds(Mockito.anyCollection<String>())).thenReturn(emptyMap())
        val x = expert("X", discovery = true).copy(tags = listOf(DiscoveryPersonScope.DISCOVERED_TAG))

        val selection = service.select(scope(), listOf(x))

        assertTrue(selection.included.isEmpty())
        assertEquals(
            listOf(PersonRecipientReasonKeys.PERSON_MAPPING_MISSING),
            selection.excluded.single().exclusionReasons
        )
        Mockito.verifyNoInteractions(expertSearchService, suppression, contacts)
    }

    @Test
    fun `primary docId member is preferred when eligible and deterministic otherwise (I-2)`() {
        val d1 = expert("d1", level = 1)
        val d2 = expert("d2", level = 5)
        stubPerson("P1", listOf(member("d1", "P1"), member("d2", "P1")))
        stubExpertSearch(listOf(d1, d2))
        admitAll("d1", "d2")
        Mockito.`when`(contacts.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())
        Mockito.`when`(personRepository.findPerson("P1")).thenReturn(
            DiscoveryPersonRecord(
                personId = "P1",
                primaryDocId = "d1",
                revision = 1L,
                createdAt = LocalDateTime.of(2026, 8, 1, 12, 0),
                updatedAt = LocalDateTime.of(2026, 8, 1, 12, 0)
            )
        )

        // 代表档案 d1 合格 → 优先 d1（否则会按验证级别选 d2）。
        assertEquals(setOf("d1"), service.select(scope(), listOf(d1, d2)).selectedDocIds)

        // 代表档案不合格（被抑制）→ 按验证级别确定性选 d2。
        Mockito.`when`(suppression.isSuppressed("d1@example.org")).thenReturn(true)
        assertEquals(setOf("d2"), service.select(scope(), listOf(d1, d2)).selectedDocIds)
    }

    @Test
    fun `non-person records keep per-email behaviour without member loading (I-3)`() {
        // 无 04 映射：逐邮箱选择，不触发人物/ES/抑制/联系人读取。
        Mockito.`when`(personRepository.findPersonIdsByDocIds(Mockito.anyCollection())).thenReturn(emptyMap())
        admitAll("X")
        val x = expert("X")

        val selection = service.select(scope(), listOf(x))

        assertEquals(setOf("X"), selection.selectedDocIds)
        assertEquals(null, selection.included.single().personId)
        assertEquals(listOf("X"), selection.included.single().memberDocIds)
        Mockito.verify(personRepository, Mockito.never()).findMembersByPerson(Mockito.anyString())
        Mockito.verifyNoInteractions(expertSearchService, suppression, contacts)
    }

    @Test
    fun `selection performs no writes and reports reason hits per person (I-3)`() {
        val d1 = expert("d1")
        val d2 = expert("d2")
        stubPerson("P1", listOf(member("d1", "P1"), member("d2", "P1")))
        stubExpertSearch(listOf(d1, d2))
        stubAdmissions("d1" to DiscoveryReviewDecision.AUTO_PASSED.name, "d2" to DiscoveryReviewDecision.REJECTED.name)
        Mockito.`when`(contacts.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())

        val selection = service.select(scope(), listOf(d1))

        assertEquals(1, selection.reasonHits[RecipientAdmissionReasonKeys.MANUAL_REJECTED])
        assertEquals(1, selection.excluded.size)
        // 只读：仓储只被读查询调用，无写入交互。
        Mockito.verify(personRepository, Mockito.never()).createPerson(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyList(), Mockito.anyList(), anyTimestamp()
        )
        Mockito.verify(contacts, Mockito.never()).save(anyContact())
    }

    private fun anyTimestamp(): LocalDateTime =
        Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()

    private fun anyContact(): com.weibo.talentintroduction.campaign.domain.ExpertContact =
        Mockito.any(com.weibo.talentintroduction.campaign.domain.ExpertContact::class.java)
            ?: com.weibo.talentintroduction.campaign.domain.ExpertContact(
                campaignId = 0L, orcidId = "", expertEmail = "", expertName = null
            )
}
