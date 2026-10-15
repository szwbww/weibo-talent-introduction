package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.BatchRecipientSelectionService
import com.weibo.talentintroduction.campaign.service.RecipientAdmissionState
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonScope
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.EmailDomainCount
import com.weibo.talentintroduction.expert.service.ExpertHitWithSort
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.expert.service.RegionCount
import com.weibo.talentintroduction.expert.service.TagCount
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Locale

/**
 * 06（I-1/I-3）：人物口径的**只读**查询入口。
 *
 * 把一次混合检索拆成两个分区后再归并：
 * - discovered 分区（`tags` 含 `discovered`）：**完整**流式读取，按 500 条批量取人物关系，
 *   以有效 `personId` 精确归组；同一人物取原排序命中成员中最前者作代表成员。
 * - 其他分区（无 discovered 标签，每个文档天然是一人）：保持 ES 精确 count，仅取归并排序
 *   所需的前 `from + size` 条，不为归并全量扫描。
 *
 * `totalHits = 其他原始匹配数 + discovered 人物数`；各聚合桶同样分区相加。滚动在服务内关闭。
 * discovered 分区每次最多读取 [MAX_DISCOVERED_DOCS] 条 / [DISCOVERED_BUDGET_MS] 毫秒，
 * 超限抛 [DiscoveryPersonQueryUnavailableException]（可重试），**绝不返回部分数或近似数**。
 *
 * 本服务只读 ES/SQL：不登记、不修复、不创建联系人；不把 personId 填进旧 `orcidId`；
 * 关系失效（当前标签/证据指纹不再成立）的成员按独立档案显示并标记待核，不并入人物聚合。
 * 不声称 ES 与 MySQL 是同一事务快照。
 *
 * R-11/R-12：DB 联系人只按**核验通过**的关系聚合（其余回退原 contactId 单位并标待核）；
 * 详情里的逐邮箱准入/邮箱状态一律读取既有真值（统一 selector 的持久准入 +
 * `email_suppression` 抑制名单），无法取得时明确 `UNKNOWN`，绝不把旧 `filterResult` 当许可。
 */
@Service
class DiscoveryPersonQueryService(
    private val expertSearchService: ExpertSearchService,
    private val personRepository: DiscoveryPersonRepository,
    private val contactRepository: ExpertContactRepository,
    private val featureGate: DiscoveryPersonFeatureGate = DiscoveryPersonFeatureGate(),
    private val clock: Clock = Clock.systemUTC(),
    /**
     * R-12：逐邮箱准入真值的**唯一**读取入口（统一 selector，生产恒注入）。
     * 省略时（单测）逐邮箱准入一律 `UNKNOWN`，绝不回退成「可发送」。
     */
    private val batchRecipientSelectionService: BatchRecipientSelectionService? = null,
    /** R-12：逐邮箱抑制（退订）真值；省略时逐邮箱状态一律 `UNKNOWN`。 */
    private val emailSuppressionService: EmailSuppressionService? = null
) {
    private val log = LoggerFactory.getLogger(DiscoveryPersonQueryService::class.java)

    companion object {
        const val DISCOVERED_TAG = "discovered"
        const val MAX_DISCOVERED_DOCS = 100_000
        const val DISCOVERED_BUDGET_MS = 10_000L
        const val RELATION_BATCH_SIZE = 500

        /**
         * R-12：逐邮箱展示状态 token。除这两种外一律取统一 selector 的
         * [RecipientAdmissionState].name（可发送 = `ADMITTED`）。
         */
        const val STATUS_SUPPRESSED = "SUPPRESSED"
        const val STATUS_UNKNOWN = "UNKNOWN"

        /** R-8：当前运营状态已知不合格（不可发送）——沿用既有非许可状态投影，不改持久准入结论。 */
        const val OPERATOR_STATUS_EMAIL_INVALID = "EMAIL_INVALID"

        private val LAYERS =
            listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)
    }

    // ── 列表与计数（I-1）────────────────────────────────────────────────────────

    fun searchPersons(
        level: ExpertIndexLevel,
        size: Int,
        tag: String? = null,
        sortBy: String? = null,
        from: Int = 0,
        operatorStatus: String? = null,
        emailDomain: String? = null,
        region: String? = null,
        hIndexMin: Int? = null,
        citationCountMin: Int? = null,
        recentYears: Int? = null,
        hasField: List<String>? = null,
        discipline: String? = null,
        expertTypes: List<String> = emptyList()
    ): PersonSearchResult {
        require(size in 1..1000) { "size must be between 1 and 1000" }
        require(from >= 0) { "from must be >= 0" }

        val filters = expertSearchService.expertFilters(
            tag, operatorStatus, emailDomain, region,
            hIndexMin, citationCountMin, recentYears, hasField, discipline, expertTypes
        )
        // 默认关闭：逐档案口径（与 04 引入前一致），不做任何人物归并。
        if (!featureGate.enabled) {
            val total = expertSearchService.countExpertsComplete(level, filters)
            val page = if (total == 0L) emptyList()
            else expertSearchService.searchExpertsWithSort(level, filters, from, size, sortBy)
            return PersonSearchResult(page.map { PersonListEntry(it.profile, 1) }, total)
        }
        val discoveredFilter = filters + discoveredFilter()
        val othersFilter = filters + notDiscoveredFilter()

        val othersTotal = expertSearchService.countExpertsComplete(level, othersFilter)
        val scan = scanDiscovered(level, discoveredFilter, sortBy)
        // 「其他」分区只需归并排序所需的前 from+size 条；无其他匹配时跳过 ES 前缀读取。
        // R-9：单次 `_search` 读页上限 1000，故按上限分页读取——绝不把 from+size 当单页 size。
        val othersPrefix = if (othersTotal == 0L) {
            emptyList()
        } else {
            readSortedPrefix(level, othersFilter, from + size, sortBy)
        }
        val merged = mergeBySort(scan.representatives, othersPrefix, from + size, level, sortBy)
        // A13：每行人物投影携带成员数（与人物关系同源、同一次批量读取得出）。非 discovered 行（其他分区
        // 每个文档天然一人）不在 count 表中，取 1；discovered 行取该人全部命中成员数，跨页一致。
        val page = merged.drop(from).take(size).map { hit ->
            PersonListEntry(
                profile = hit.profile,
                memberCount = scan.memberCountByDocId[hit.profile.esDocId] ?: 1
            )
        }
        return PersonSearchResult(experts = page, totalHits = othersTotal + scan.personCount)
    }

    fun countPersons(
        level: ExpertIndexLevel,
        tag: String? = null,
        operatorStatus: String? = null,
        emailDomain: String? = null,
        region: String? = null,
        hIndexMin: Int? = null,
        citationCountMin: Int? = null,
        recentYears: Int? = null,
        hasField: List<String>? = null,
        discipline: String? = null,
        expertTypes: List<String> = emptyList()
    ): Long {
        val filters = expertSearchService.expertFilters(
            tag, operatorStatus, emailDomain, region,
            hIndexMin, citationCountMin, recentYears, hasField, discipline, expertTypes
        )
        if (!featureGate.enabled) return expertSearchService.countExpertsComplete(level, filters)
        val othersTotal = expertSearchService.countExpertsComplete(level, filters + notDiscoveredFilter())
        val scan = scanDiscovered(level, filters + discoveredFilter(), null)
        return othersTotal + scan.personCount
    }

    // ── 聚合桶（I-1：分区相加、桶内按人去重）──────────────────────────────────────

    fun aggregateTagsPersons(
        level: ExpertIndexLevel,
        operatorStatus: String? = null,
        emailDomain: String? = null,
        region: String? = null
    ): List<TagCount> {
        val filters = expertSearchService.expertFilters(null, operatorStatus, emailDomain, region)
        if (!featureGate.enabled) {
            return expertSearchService.aggregateTerms(level, filters, "tags", 100)
                .map { TagCount(it.first, it.second) }
                .filter { it.tag.isNotBlank() }
                .sortedByDescending { it.count }
        }
        val others = expertSearchService
            .aggregateTerms(level, filters + notDiscoveredFilter(), "tags", 100)
            .toMap()
        val persons = scanDiscovered(level, filters + discoveredFilter(), null).tagPersonCounts
        return (others.keys + persons.keys).map { key ->
            TagCount(key, (others[key] ?: 0L) + (persons[key] ?: 0L))
        }.sortedByDescending { it.count }
    }

    fun aggregateRegionsPersons(
        level: ExpertIndexLevel,
        tag: String? = null,
        operatorStatus: String? = null,
        emailDomain: String? = null
    ): List<RegionCount> {
        val filters = expertSearchService.expertFilters(tag, operatorStatus, emailDomain, null)
        if (!featureGate.enabled) {
            val legacy = mutableMapOf<String, Long>()
            expertSearchService.aggregateTerms(level, filters, "country", 500).forEach { (country, count) ->
                val regionName = CountryContinentMapping.toRegion(country)
                legacy[regionName] = (legacy[regionName] ?: 0L) + count
            }
            return CountryContinentMapping.allRegions().map { regionName ->
                RegionCount(regionName, legacy[regionName] ?: 0L)
            }
        }
        val others = mutableMapOf<String, Long>()
        expertSearchService.aggregateTerms(level, filters + notDiscoveredFilter(), "country", 500)
            .forEach { (country, count) ->
                val regionName = CountryContinentMapping.toRegion(country)
                others[regionName] = (others[regionName] ?: 0L) + count
            }
        val persons = scanDiscovered(level, filters + discoveredFilter(), null).regionPersonCounts
        return CountryContinentMapping.allRegions().map { regionName ->
            RegionCount(regionName, (others[regionName] ?: 0L) + (persons[regionName] ?: 0L))
        }
    }

    fun aggregateEmailDomainsPersons(
        level: ExpertIndexLevel,
        tag: String? = null,
        operatorStatus: String? = null,
        region: String? = null
    ): List<EmailDomainCount> {
        val filters = expertSearchService.expertFilters(tag, operatorStatus, null, region) +
            mapOf("exists" to mapOf("field" to "email"))
        val script = mapOf(
            "source" to "doc['email'].value.substring(doc['email'].value.indexOf('@') + 1)"
        )
        if (!featureGate.enabled) {
            return expertSearchService.aggregateTerms(level, filters, "email", 10000, script)
                .map { (domain, count) -> EmailDomainCount(domain, count) }
                .filter { it.domain.isNotBlank() }
                .sortedByDescending { it.count }
        }
        val others = expertSearchService
            .aggregateTerms(level, filters + notDiscoveredFilter(), "email", 10000, script)
            .toMap()
        val persons = scanDiscovered(level, filters + discoveredFilter(), null).domainPersonCounts
        return (others.keys + persons.keys).map { domain ->
            EmailDomainCount(domain, (others[domain] ?: 0L) + (persons[domain] ?: 0L))
        }.sortedByDescending { it.count }
    }

    // ── 人物详情（I-2：只读）────────────────────────────────────────────────────

    /**
     * 读取 `docId` 所属人物（无关系时按独立档案）的只读视图：全部有效邮箱成员、各自原层级、
     * contactId、状态、代表标记、失效标记与证据来源。绝不登记、修复或创建联系人。
     * 未在任何层出现且无关系时返回 null。
     */
    fun personDetail(docId: String, level: ExpertIndexLevel): DiscoveryPersonDetail? {
        require(docId.isNotBlank()) { "docId is required" }
        val memberRow = personRepository.findMember(docId)
        // 默认关闭：退回逐档案视图（无人物归并、无关系语义）；关系读取不参与展示。
        val personId = if (featureGate.enabled) memberRow?.personId else null
        val person = personId?.let { personRepository.findPerson(it) }
        val memberRows = personId?.let { personRepository.findMembersByPerson(it) } ?: emptyList()

        val docIds = (memberRows.map { it.expertDocId } + docId).distinct()
        val profilesByDoc = loadProfilesByDoc(docIds)
        if (memberRow == null && profilesByDoc[docId].isNullOrEmpty()) return null

        // R-8：联系人真值不可得必须与「无联系人」区分 —— null 表示读取失败，展示投影一律 UNKNOWN。
        val contacts = try {
            contactRepository.findByOrcidIdIn(docIds).groupBy { it.orcidId }
        } catch (e: Exception) {
            log.warn("人物详情读取联系人失败 docId={}: {}", docId, e.message)
            null
        }
        val rowByDoc = memberRows.associateBy { it.expertDocId }
        val representativeDocId = person?.primaryDocId ?: docId

        // R-12：逐邮箱准入/邮箱状态真值。准入经统一 selector 读取持久结论，邮箱状态取抑制名单；
        // 任一真值不可得即明确 UNKNOWN，绝不把旧 `filterResult` 当成可发送许可。
        val admissionStates = readAdmissionStates(docIds.mapNotNull { profilesByDoc[it]?.firstOrNull()?.second })

        val members = docIds.map { id ->
            val profiles = profilesByDoc[id].orEmpty()
            val levels = profiles.map { it.first.name }
            val raw = profiles.firstOrNull()?.second
            val stored = rowByDoc[id]
            val contact = contacts?.get(id)?.maxByOrNull { it.updatedAt ?: java.time.LocalDateTime.MIN }
            // V-12：关系失效（当前标签/证据指纹不成立）的成员，其准入 token 一律不可信。
            val stale = stored != null && isStale(stored.identityFingerprint, id, profiles)
            DiscoveryPersonMember(
                docId = id,
                levels = levels,
                email = raw?.email,
                contactId = contact?.id,
                contactStatus = contact?.currentStatus,
                operatorStatus = contact?.operatorStatus ?: raw?.operatorStatus,
                admissionStatus = perEmailAdmissionStatus(
                    raw?.email, admissionStates[id], stale,
                    currentOperatorStatus = contact?.operatorStatus,
                    contactReadable = contacts != null
                ),
                representative = id == representativeDocId,
                stale = stale,
                evidenceSource = evidenceSource(stored?.evidenceJson, raw)
            )
        }

        return DiscoveryPersonDetail(
            personId = personId,
            revision = person?.revision,
            representativeDocId = representativeDocId,
            requestedDocId = docId,
            members = members,
            conflicts = memberConflicts(members, contacts.orEmpty(), docIds)
        )
    }

    /**
     * R-11：DB 联系人分支的关系解析（只读，不登记；默认关闭时不归并）。
     *
     * 只有**当前核验通过**的关系才给出 `personId`（实时 discovered 范围 + 成员身份指纹与登记
     * 快照一致）。已登记但当前核验失败（移标、指纹变化、档案不可读）的关系回退原联系人单位并
     * 标记 [VerifiedPersonRelation.needsReview]；无关系即范围外，不参与人物聚合。
     * 关系读取本身失败时抛 [DiscoveryPersonQueryUnavailableException]（明确「关系未知」），
     * 绝不返回空映射冒充「无人映射」——调用方宁可报告不可用，也不得据此错误归并/展示。
     */
    fun resolveVerifiedPersonRelations(docIds: Collection<String>): Map<String, VerifiedPersonRelation> {
        if (!featureGate.enabled) return emptyMap()
        val ids = docIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val memberRows = try {
            personRepository.findMembers(ids)
        } catch (e: Exception) {
            log.warn("关系核验读取失败，本次不归并人物: {}", e.message)
            throw DiscoveryPersonQueryUnavailableException("关系核验读取失败，关系未知，查询暂不可用")
        }
        if (memberRows.isEmpty()) return emptyMap()
        val profilesByDoc = loadProfilesByDoc(memberRows.map { it.expertDocId })
        return memberRows.associate { row ->
            val stale = isStale(row.identityFingerprint, row.expertDocId, profilesByDoc[row.expertDocId].orEmpty())
            row.expertDocId to VerifiedPersonRelation(
                personId = row.personId.takeUnless { stale },
                needsReview = stale
            )
        }
    }

    // ── 关系核验与逐邮箱状态辅助 ─────────────────────────────────────────────────

    /** 按层读取真实 `_id` 档案（与详情/扫描同源），按 docId 归组。 */
    private fun loadProfilesByDoc(
        docIds: List<String>
    ): Map<String, MutableList<Pair<ExpertIndexLevel, ExpertProfile>>> {
        val profilesByDoc = LinkedHashMap<String, MutableList<Pair<ExpertIndexLevel, ExpertProfile>>>()
        for (layer in LAYERS) {
            expertSearchService.findByDocumentIds(layer, docIds).forEach { profile ->
                val id = profile.esDocId ?: return@forEach
                profilesByDoc.getOrPut(id) { mutableListOf() }.add(layer to profile)
            }
        }
        return profilesByDoc
    }

    /**
     * R-12：逐 docId 的持久准入结论（统一 selector 的 [RecipientAdmissionState] 词汇）。
     * 读取异常/服务缺失时返回空表 —— 调用方据此显示 `UNKNOWN`，绝不回落成「可发送」。
     */
    private fun readAdmissionStates(profiles: List<ExpertProfile>): Map<String, RecipientAdmissionState> {
        val service = batchRecipientSelectionService ?: return emptyMap()
        if (profiles.isEmpty()) return emptyMap()
        return try {
            val selection = service.selectAdmission(profiles)
            (selection.included + selection.excluded).associate { it.docId to it.admissionState }
        } catch (e: Exception) {
            log.warn("逐邮箱准入读取失败，本轮显示为未知: {}", e.message)
            emptyMap()
        }
    }

    /**
     * R-12/V-12：单个成员邮箱的展示状态。关系失效（[stale]）时准入 token 一律不可信 → `UNKNOWN`；
     * 抑制（退订）= [STATUS_SUPPRESSED]，其余取持久准入结论；邮箱为空、准入真值或抑制真值不可得、
     * 非人物档案一律 `UNKNOWN`（绝不显示可发送）。
     */
    private fun perEmailAdmissionStatus(
        email: String?,
        admissionState: RecipientAdmissionState?,
        stale: Boolean,
        currentOperatorStatus: String?,
        contactReadable: Boolean
    ): String {
        if (email.isNullOrBlank()) return STATUS_UNKNOWN
        if (stale) return STATUS_UNKNOWN
        // R-8（V-12）：当前已知运营状态不合格（EMAIL_INVALID）或联系人真值不可得 ⇒ 不得投影为可发送。
        if (!contactReadable) return STATUS_UNKNOWN
        if (currentOperatorStatus == OPERATOR_STATUS_EMAIL_INVALID) return STATUS_UNKNOWN
        val suppressed = isSuppressed(email) ?: return STATUS_UNKNOWN
        if (suppressed) return STATUS_SUPPRESSED
        if (admissionState == null || admissionState == RecipientAdmissionState.NOT_DISCOVERY) return STATUS_UNKNOWN
        return admissionState.name
    }

    /** 抑制真值：null = 不可得（区别于「未抑制」）。 */
    private fun isSuppressed(email: String): Boolean? {
        val service = emailSuppressionService ?: return null
        return try {
            service.isSuppressed(email)
        } catch (e: Exception) {
            log.warn("邮箱抑制真值读取失败: {}", e.message)
            null
        }
    }

    // ── discovered 分区扫描 ─────────────────────────────────────────────────────

    private class DiscoveredScan(
        val representatives: List<ExpertHitWithSort>,
        val personCount: Int,
        val memberCountByDocId: Map<String, Int>,
        val tagPersonCounts: Map<String, Long>,
        val regionPersonCounts: Map<String, Long>,
        val domainPersonCounts: Map<String, Long>
    )

    private fun scanDiscovered(
        level: ExpertIndexLevel,
        filters: List<Map<String, Any>>,
        sortBy: String?
    ): DiscoveredScan {
        val start = java.time.Instant.now(clock)
        val representativeByPerson = LinkedHashMap<String, ExpertHitWithSort>()
        val memberCountByKey = HashMap<String, Int>()
        val tagPersons = HashMap<String, MutableSet<String>>()
        val regionPersons = HashMap<String, MutableSet<String>>()
        val domainPersons = HashMap<String, MutableSet<String>>()
        var docCount = 0

        expertSearchService.scrollExpertsFilteredWithSort(level, filters, RELATION_BATCH_SIZE, sortBy) { batch ->
            if (batch.isNotEmpty()) {
                docCount += batch.size
                if (docCount > MAX_DISCOVERED_DOCS) {
                    throw DiscoveryPersonQueryUnavailableException(
                        "discovered 分区超过每批上限 $MAX_DISCOVERED_DOCS 条，查询暂不可用，请缩小范围后重试"
                    )
                }
                val elapsedMs = java.time.Duration.between(start, java.time.Instant.now(clock)).toMillis()
                if (elapsedMs > DISCOVERED_BUDGET_MS) {
                    throw DiscoveryPersonQueryUnavailableException(
                        "discovered 分区读取超过 ${DISCOVERED_BUDGET_MS}ms 预算，查询暂不可用，请稍后重试"
                    )
                }

                val docIds = batch.mapNotNull { it.profile.esDocId }
                val memberByDoc = personRepository.findMembers(docIds).associateBy { it.expertDocId }

                for (hit in batch) {
                    val profile = hit.profile
                    val docId = profile.esDocId ?: continue
                    val stored = memberByDoc[docId]
                    val stale = stored != null &&
                        isStale(stored.identityFingerprint, docId, listOf(level to profile))
                    val personId = stored?.personId
                    // 失效关系/无关系：按独立档案显示（personKey = docId），不并入人物聚合。
                    val key = if (personId != null && !stale) personId else docId

                    val existing = representativeByPerson[key]
                    if (existing == null || compareHits(hit, existing, level, sortBy) < 0) {
                        representativeByPerson[key] = hit
                    }
                    // A13：同一人物每个命中成员各计数一次（同一次批量关系读取得出成员数）。
                    memberCountByKey.merge(key, 1, Int::plus)
                    profile.tags.orEmpty().forEach { t ->
                        if (t.isNotBlank()) tagPersons.getOrPut(t) { mutableSetOf() }.add(key)
                    }
                    profile.country?.takeIf { it.isNotBlank() }?.let { country ->
                        regionPersons.getOrPut(CountryContinentMapping.toRegion(country)) { mutableSetOf() }.add(key)
                    }
                    domainOf(profile.email)?.let { domain ->
                        domainPersons.getOrPut(domain) { mutableSetOf() }.add(key)
                    }
                }
            }
            true
        }

        val representatives = representativeByPerson.values.sortedWith { a, b -> compareHits(a, b, level, sortBy) }
        // 以代表成员的 docId 为键给出该人物成员数（供列表投影携带）。
        val memberCountByDocId = HashMap<String, Int>(representativeByPerson.size)
        representativeByPerson.forEach { (key, hit) ->
            hit.profile.esDocId?.let { docId -> memberCountByDocId[docId] = memberCountByKey[key] ?: 1 }
        }
        return DiscoveredScan(
            representatives = representatives,
            personCount = representativeByPerson.size,
            memberCountByDocId = memberCountByDocId,
            tagPersonCounts = tagPersons.mapValues { (_, keys) -> keys.size.toLong() },
            regionPersonCounts = regionPersons.mapValues { (_, keys) -> keys.size.toLong() },
            domainPersonCounts = domainPersons.mapValues { (_, keys) -> keys.size.toLong() }
        )
    }

    /**
     * R-9：读取「其他」分区归并排序所需的前 `limit` 条。单次 `_search` 读页上限为
     * [ExpertSearchService.MAX_SEARCH_PAGE_SIZE]，故按该上限逐页读取（from 递增），
     * **不**把 `from + size` 当成单页 size，也不读取请求范围之外的专家：
     * ES 返回不足一页即说明索引已到底，立即停止。任一页不完整由 ES 服务层明确失败并向上传播。
     */
    private fun readSortedPrefix(
        level: ExpertIndexLevel,
        filters: List<Map<String, Any>>,
        limit: Int,
        sortBy: String?
    ): List<ExpertHitWithSort> {
        if (limit <= 0) return emptyList()
        val pageSize = ExpertSearchService.MAX_SEARCH_PAGE_SIZE
        val out = ArrayList<ExpertHitWithSort>(minOf(limit, pageSize))
        var offset = 0
        while (offset < limit) {
            val requestSize = minOf(pageSize, limit - offset)
            val page = expertSearchService.searchExpertsWithSort(level, filters, offset, requestSize, sortBy)
            out += page
            if (page.size < requestSize) break
            offset += requestSize
        }
        return out
    }

    /** 归并两个各自已按显示排序的前缀，去重（分区不相交，仅防同 key 重复），取前 `limit` 条。 */
    private fun mergeBySort(
        left: List<ExpertHitWithSort>,
        right: List<ExpertHitWithSort>,
        limit: Int,
        level: ExpertIndexLevel,
        sortBy: String?
    ): List<ExpertHitWithSort> {
        if (left.isEmpty()) return right.take(limit)
        if (right.isEmpty()) return left.take(limit)
        val merged = ArrayList<ExpertHitWithSort>(minOf(limit, left.size + right.size))
        val seen = HashSet<String>()
        var i = 0
        var j = 0
        while (merged.size < limit && (i < left.size || j < right.size)) {
            val takeLeft = when {
                i >= left.size -> false
                j >= right.size -> true
                else -> compareHits(left[i], right[j], level, sortBy) <= 0
            }
            val next = if (takeLeft) left[i++] else right[j++]
            val key = personKey(next)
            if (seen.add(key)) merged.add(next)
        }
        return merged
    }

    private fun personKey(hit: ExpertHitWithSort): String = hit.profile.esDocId ?: hit.profile.orcidId

    private fun compareHits(
        a: ExpertHitWithSort,
        b: ExpertHitWithSort,
        level: ExpertIndexLevel,
        sortBy: String?
    ): Int {
        // 显示排序方向：RAW 且未显式按 updatedAt 时升序（_doc asc），其余（含 updatedAt）降序。
        val descending = !(level == ExpertIndexLevel.RAW && sortBy != "updatedAt")
        val size = maxOf(a.sort.size, b.sort.size)
        for (index in 0 until size) {
            val av = a.sort.getOrNull(index)
            val bv = b.sort.getOrNull(index)
            var cmp = compareSortValue(av, bv)
            if (descending) cmp = -cmp
            if (cmp != 0) return cmp
        }
        // 稳定兜底：以文档 key 升序保证确定性。
        return personKey(a).compareTo(personKey(b))
    }

    private fun compareSortValue(a: Any?, b: Any?): Int {
        if (a == null && b == null) return 0
        if (a == null) return 1
        if (b == null) return -1
        val an = (a as? Number)?.toDouble()
        val bn = (b as? Number)?.toDouble()
        return if (an != null && bn != null) an.compareTo(bn) else a.toString().compareTo(b.toString())
    }

    // ── 关系核验与辅助 ──────────────────────────────────────────────────────────

    /**
     * 当前标签/证据指纹核验（I-3）：以当前层档案重算指纹并与登记快照比对。
     * 层间身份一致时（登记门槛保证）单层重算与多层重算同值，故此处可在扫描路径上低成本执行。
     */
    private fun isStale(storedFingerprint: String, docId: String, profiles: List<Pair<ExpertIndexLevel, ExpertProfile>>): Boolean {
        val profile = profiles.firstOrNull()?.second ?: return true
        // I-3：关系有效的前提是**实时**人物范围（来源证据 + 字面 discovered 标签）且身份仍可核实。
        if (!DiscoveryPersonScope.isLiveDiscovered(profile)) return true
        if (!DiscoveryIdentity.allowed(profile)) return true
        val authorKeys = DiscoveryPersonIdentity.authorKeys(profiles.map { it.second })
        val normalizedEmail = DiscoveryIdentity.normalizedEmail(profile.email).takeIf { it.isNotEmpty() }
        val fingerprint = DiscoveryPersonIdentity.fingerprint(
            docId, normalizedEmail, profile.givenNames, profile.familyNames, authorKeys
        )
        return fingerprint != storedFingerprint
    }

    private fun evidenceSource(evidenceJson: String?, profile: ExpertProfile?): String? {
        if (!evidenceJson.isNullOrBlank()) {
            try {
                val node = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(evidenceJson)
                node.path("source").takeIf { it.isTextual }?.asText()?.let { return it }
            } catch (_: Exception) {
                // 忽略解析失败，回退到档案来源
            }
        }
        return profile?.emailSource
    }

    private fun memberConflicts(
        members: List<DiscoveryPersonMember>,
        contacts: Map<String, List<ExpertContact>>,
        docIds: List<String>
    ): List<String> {
        val conflicts = LinkedHashSet<String>()
        val operatorStatuses = members.mapNotNull { it.operatorStatus }.distinct()
        if (operatorStatuses.size > 1) conflicts.add("OPERATOR_STATUS_DIFFERS")
        val contactStatuses = members.mapNotNull { it.contactStatus }.distinct()
        if (contactStatuses.size > 1) conflicts.add("CONTACT_STATUS_DIFFERS")
        val names = docIds.mapNotNull { id ->
            contacts[id]?.maxByOrNull { it.updatedAt ?: java.time.LocalDateTime.MIN }?.expertName
        }.distinct()
        if (names.size > 1) conflicts.add("EXPERT_NAME_DIFFERS")
        return conflicts.toList()
    }

    private fun domainOf(email: String?): String? {
        val at = email?.indexOf('@') ?: return null
        return email.substring(at + 1).takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
    }

    private fun discoveredFilter(): Map<String, Any> =
        mapOf("term" to mapOf("tags" to DISCOVERED_TAG))

    private fun notDiscoveredFilter(): Map<String, Any> =
        mapOf("bool" to mapOf("must_not" to listOf(mapOf("term" to mapOf("tags" to DISCOVERED_TAG)))))
}

/** 06（A13）：ES 列表人物口径行——代表成员档案 + 该人物成员数（与人物关系同源、同一次批量读取得出）。 */
data class PersonListEntry(
    val profile: ExpertProfile,
    val memberCount: Int
)

/** 06：人物口径检索结果（代表成员档案 + 人物口径 total）。 */
data class PersonSearchResult(
    val experts: List<PersonListEntry>,
    val totalHits: Long
)

/** 06（I-2）：人物只读视图中的单个邮箱成员。 */
data class DiscoveryPersonMember(
    val docId: String,
    val levels: List<String>,
    val email: String?,
    val contactId: Long?,
    val contactStatus: String?,
    val operatorStatus: String?,
    /**
     * R-12/V-12：逐邮箱准入/邮箱状态真值 token —— `ADMITTED`（可发送）/
     * `NEEDS_REVIEW`/`MANUAL_REJECTED`/`IDENTITY_CHANGED`/`UNINITIALIZED` 取统一 selector 的持久准入；
     * `SUPPRESSED` = 邮箱在抑制名单；`UNKNOWN` = 真值不可得，**或**关系失效（[stale]）致准入不可信。
     * **绝不**是旧 ES `filterResult`；失效关系的成员一律不显示 `ADMITTED`。
     */
    val admissionStatus: String?,
    val representative: Boolean,
    val stale: Boolean,
    val evidenceSource: String?
)

/**
 * R-11：DB 联系人分支的关系核验结果。[personId] 非空表示当前关系核验通过、可并入人物聚合；
 * [needsReview] 表示已登记关系的实时标签/身份指纹已不成立 —— 回退原联系人单位并标记待核。
 */
data class VerifiedPersonRelation(
    val personId: String?,
    val needsReview: Boolean
)

/** 06（I-2）：只读人物详情；无关系时 [personId]/[revision] 为空，成员仅含原档案。 */
data class DiscoveryPersonDetail(
    val personId: String?,
    val revision: Long?,
    val representativeDocId: String,
    val requestedDocId: String,
    val members: List<DiscoveryPersonMember>,
    val conflicts: List<String>
)

/** 06（I-3）：超预算或查询不全时的可重试失败，绝不返回部分/近似 total。 */
class DiscoveryPersonQueryUnavailableException(message: String) : IllegalStateException(message)
