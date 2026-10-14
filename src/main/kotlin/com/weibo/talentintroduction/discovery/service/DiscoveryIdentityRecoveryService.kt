package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.config.OpenAlexBudgetDeferredException
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonScope
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/** 05（I-1）：恢复路径记入人物成员 `evidence_json` 的发现来源标签（不参与身份判定）。 */
private const val PERSON_SOURCE_RECOVERY = "IDENTITY_RECOVERY"

/**
 * 02（I-1/I-2/I-3/I-4）：旧名单**回源预览 + 受控恢复**的唯一协调器。
 *
 * 只做四件事：①按服务端存量 `externalIds`（PMCID/DOI/PMID）精确定位原文并重新取证；
 * ②在只读预览里给出可否恢复的判定、逐层摘要与两项 hash；③apply 时**服务端重新取证重算**
 * （客户端只带 `docId` 与两项 hash），不同即 `STALE`（控制器 409）；④以各层版本 CAS 落地
 * 「只填空姓名段 + proof」，部分写入回报 `PARTIAL` 且**不刷新自动准入**。
 *
 * 调用方绝不提交 proof/作者名/下载 URL/模拟原文；本服务只接受真实 [docId]，
 * 旧 `extraction_json` 摘要不当证明（每次都重新提取）。唯一事实写入口仍是
 * [DiscoveryIdentityRepairService] + [ExpertIndexWriterService]。
 */
@Service
class DiscoveryIdentityRecoveryService(
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val repairService: DiscoveryIdentityRepairService,
    private val europePmcDataSource: EuropePmcDataSource,
    private val openAlexDataSource: ObjectProvider<OpenAlexDataSource>,
    private val expertEmailAliasService: ExpertEmailAliasService,
    private val expertContactRepository: ExpertContactRepository,
    private val admissionPolicy: DiscoveryAdmissionPolicy,
    private val reviewService: DiscoveryReviewService,
    private val clock: Clock = Clock.systemUTC(),
    /**
     * 05（I-1）：补证成功后的统一人物登记刷新接缝。为空（历史单测/未装配）时保持原行为；
     * 生产恒由 Spring 注入。它只读、只登记关系，不改变恢复结论。
     */
    private val personRegistrationService: DiscoveryPersonRegistrationService? = null
) {
    private val log = LoggerFactory.getLogger(DiscoveryIdentityRecoveryService::class.java)
    private val mapper = jacksonObjectMapper()

    private val layers = listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)

    /** I-2：触发风险排除的旧学术字段全集（不能只看是否有 researchFields）。 */
    private val academicFieldKeys = listOf(
        "hIndex", "citationCount", "worksCount", "researchFields", "researchFieldIds",
        "recentWorkTitles", "patentTitles", "disciplineCategory", "enrichedAt"
    )

    /**
     * I-3：只读预览 —— 对 ES/准入/联系人/邮件**零写**（可产生既有请求额度台账）。
     * 返回可否恢复的判定、逐层摘要、`snapshotHash`、`proposalHash`、规则版本与全量阻断原因。
     */
    fun preview(docId: String): RecoveryPreview = inspect(docId).preview

    /**
     * I-3：受控应用。只接受服务端重新取证重算出的两项 hash；不一致返回 [RecoveryApplyStatus.STALE]。
     * 落地走受限补证路径；[RecoveryApplyStatus.APPLIED] 时才以既有 `recordAutomatic` 接缝刷新自动准入
     * （名字补齐改变 `identity_hash`，旧人工决定自然作废）；[RecoveryApplyStatus.PARTIAL] 不刷新。
     */
    fun apply(docId: String, snapshotHash: String?, proposalHash: String?): RecoveryApplyResult {
        val inspection = inspect(docId)
        if (inspection.preview.status == RecoveryStatus.DEFERRED) {
            return finished(docId, RecoveryApplyStatus.DEFERRED, inspection.preview.reason, emptyList(), null, null)
        }
        val stale = inspection.preview.snapshotHash != snapshotHash ||
            inspection.preview.proposalHash != proposalHash ||
            snapshotHash.isNullOrBlank() || proposalHash.isNullOrBlank()
        if (inspection.preview.status != RecoveryStatus.RECOVERABLE) {
            // 源/档案已变（hash 不再匹配）报 409；其余不可恢复情形单列原因，不算并发冲突。
            return if (stale && (snapshotHash != null || proposalHash != null)) {
                finished(docId, RecoveryApplyStatus.STALE, inspection.preview.reason ?: "SOURCE_CHANGED", emptyList(), null, null)
            } else {
                finished(docId, RecoveryApplyStatus.NOT_RECOVERABLE, inspection.preview.reason, emptyList(), null, null)
            }
        }
        if (stale) {
            return finished(docId, RecoveryApplyStatus.STALE, "SNAPSHOT_OR_PROPOSAL_CHANGED", emptyList(), null, null)
        }

        val incoming = inspection.incoming
            ?: return finished(docId, RecoveryApplyStatus.NOT_RECOVERABLE, "SOURCE_NO_TARGET_EMAIL", emptyList(), null, null)
        val repair = try {
            repairService.repairEmptyNameProof(docId, incoming)
        } catch (e: Exception) {
            log.warn("空名补证失败 docId={}: {}", docId, e.message)
            return finished(docId, RecoveryApplyStatus.FAILED, "REPAIR_FAILED", emptyList(), null, null)
        }
        // ALREADY_VERIFIED：本次申请的每一层都已等于目标值（同一原申请续跑），继续完成
        // 人物登记与准入重验；这不是「不可恢复」，也不再被已有的目标 proof 挡住。
        val status = when (repair.outcome) {
            IdentityRepairOutcome.REPAIRED, IdentityRepairOutcome.ALREADY_VERIFIED -> RecoveryApplyStatus.APPLIED
            IdentityRepairOutcome.PARTIAL -> RecoveryApplyStatus.PARTIAL
            else -> RecoveryApplyStatus.FAILED
        }
        if (status != RecoveryApplyStatus.APPLIED) {
            return finished(docId, status, repair.outcome.name, repair.layers, null, null)
        }

        // 05（I-1/I-3）：各层事实补齐成功后刷新人物登记。登记失败/STALE 不得报**完整成功**
        // （否则同一原申请无法续跑）；返回可续跑的 PARTIAL，只差登记与准入重验。
        val registration = try {
            personRegistrationService?.refresh(PERSON_SOURCE_RECOVERY, docId)
        } catch (e: Exception) {
            log.warn("恢复后刷新人物登记失败 docId={}: {}", docId, e.message)
            DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.FAILED, "REGISTRATION_EXCEPTION")
        }
        if (registration != null && registration.retryable) {
            return finished(
                docId, RecoveryApplyStatus.PARTIAL, "REGISTRATION_${registration.status.name}",
                repair.layers, null, null
            )
        }

        // I-2/I-3：名字补齐改变 admission identity_hash —— 以既有规则重算，绝不复用旧人工决定。
        val rawAfter = try {
            expertIndexWriterService.readDiscoveryDocument(ExpertIndexLevel.RAW, docId)?.source
                ?: inspection.rawSource
        } catch (e: Exception) {
            log.warn("补证后读取 RAW 失败 docId={}: {}", docId, e.message)
            inspection.rawSource
        }
        var decision: String? = null
        var identityChanged: Boolean? = null
        try {
            val profile = expertIndexWriterService.discoveryProfile(docId, rawAfter)
            val automatic = admissionPolicy.evaluate(profile)
            val outcome = reviewService.recordAutomaticConclusion(docId, profile, automatic)
            decision = outcome.decision
            identityChanged = outcome.identityChanged
        } catch (e: Exception) {
            // I-3：补证后原有准入重验失败单列、可重跑；名字已落地，不算整体失败。
            log.warn("补证后准入重验失败 docId={}: {}", docId, e.message)
            return finished(docId, RecoveryApplyStatus.APPLIED, "ADMISSION_REVERIFY_FAILED", repair.layers, null, null)
        }
        return finished(docId, RecoveryApplyStatus.APPLIED, repair.outcome.name, repair.layers, decision, identityChanged)
    }

    private fun finished(
        docId: String,
        status: RecoveryApplyStatus,
        reason: String?,
        layers: List<IdentityRepairLayer>,
        admissionDecision: String?,
        admissionIdentityChanged: Boolean?
    ) = RecoveryApplyResult(
        docId = docId,
        status = status,
        reason = reason,
        layers = layers,
        admissionDecision = admissionDecision,
        admissionIdentityChanged = admissionIdentityChanged,
        observedAt = Instant.now(clock).toString()
    )

    // ── 服务端只读取证 ──────────────────────────────────────────────────────

    private class Inspection(
        val preview: RecoveryPreview,
        val rawSource: Map<String, Any?>,
        val incoming: AuthorEmail?
    )

    private fun inspect(docId: String): Inspection {
        if (docId.isBlank()) {
            return inspectionResult(docId, RecoveryStatus.NOT_FOUND, "DOC_ID_BLANK", emptyMap(), emptyList(), null)
        }
        val snapshots = LinkedHashMap<ExpertIndexLevel, ExpertIndexWriterService.DiscoverySnapshot?>()
        for (level in layers) {
            snapshots[level] = try {
                expertIndexWriterService.readDiscoveryDocument(level, docId)
            } catch (e: Exception) {
                log.warn("恢复预览读取 {} 层失败 docId={}: {}", level, docId, e.message)
                return inspectionResult(docId, RecoveryStatus.SOURCE_UNAVAILABLE, "ES_READ_FAILED", emptyMap(), emptyList(), null)
            }
        }
        val rawSnapshot = snapshots[ExpertIndexLevel.RAW] ?: snapshots[ExpertIndexLevel.CANDIDATE]
        if (rawSnapshot == null) {
            return inspectionResult(docId, RecoveryStatus.NOT_FOUND, "DOCUMENT_MISSING", emptyMap(), emptyList(), null)
        }
        val rawSource = rawSnapshot.source
        val layerStates = snapshots.map { (level, snap) -> layerState(level, snap) }
        val snapshotHash = snapshotHash(docId, snapshots)

        fun blocked(status: RecoveryStatus, reason: String, reasons: List<String> = listOf(reason)): Inspection =
            inspectionResult(docId, status, reason, rawSource, layerStates, null, snapshotHash, reasons)

        if (!DiscoveryPersonScope.isLiveDiscovered(rawSource)) return blocked(RecoveryStatus.NEEDS_MANUAL, "NOT_DISCOVERY")
        if (snapshots[ExpertIndexLevel.APPLICATION] != null) return blocked(RecoveryStatus.NEEDS_MANUAL, "APPLICATION_PRESENT")

        val presentSources = snapshots.mapNotNull { (_, snap) -> snap?.source }
        if (presentSources.any { hasAcademicFields(it) }) return blocked(RecoveryStatus.NEEDS_MANUAL, "LEGACY_ACADEMIC_FIELDS")
        if (authorIdsConflictAcrossLayers(presentSources)) return blocked(RecoveryStatus.NEEDS_MANUAL, "AUTHOR_ID_CONFLICT")

        val email = DiscoveryIdentity.normalizedEmail(rawSource["email"] as? String)
        if (email.isEmpty()) return blocked(RecoveryStatus.NEEDS_MANUAL, "EMAIL_MISSING")

        // 联系人保护：docId（业务主键）/真实 ORCID/邮箱/别名任一命中都只建议人工处理。
        // I-2/V-4：保护读取异常**立即停止**（零身份写），绝不把「读不到」当作「没有保护」。
        val storedOrcid = presentSources.mapNotNull { externalIds(it)["orcid"]?.takeIf { id -> id.isNotBlank() } }.distinct()
        val orcidKeys = buildSet {
            add(docId)
            addAll(storedOrcid)
        }
        val contactPresent = try {
            orcidKeys.any { expertContactRepository.existsByOrcidId(it) } ||
                expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email) != null ||
                expertEmailAliasService.findContactByEmailOrAlias(email) != null
        } catch (e: Exception) {
            log.warn("恢复预览联系人/别名保护读取失败 docId={}: {}", docId, e.message)
            return blocked(RecoveryStatus.SOURCE_UNAVAILABLE, "CONTACT_CHECK_FAILED")
        }
        if (contactPresent) {
            return blocked(RecoveryStatus.NEEDS_MANUAL, "CONTACT_PRESENT")
        }

        // 人工/历史准入决定：名字补齐会改身份哈希，旧决定不得沿用，直接转人工。
        val profileBefore = expertIndexWriterService.discoveryProfile(docId, rawSource)
        val admission = try {
            reviewService.resolveAdmission(docId, profileBefore)
        } catch (e: Exception) {
            log.warn("恢复预览读取准入结论失败 docId={}: {}", docId, e.message)
            return blocked(RecoveryStatus.SOURCE_UNAVAILABLE, "ADMISSION_READ_FAILED")
        }
        if (admission.manual) return blocked(RecoveryStatus.NEEDS_MANUAL, "MANUAL_DECISION")

        // 邮箱必须唯一命中本 docId（修复全集语义）。
        val hits = try {
            expertIndexWriterService.findDiscoveryHitsByEmail(email)
        } catch (e: Exception) {
            return inspectionResult(docId, RecoveryStatus.SOURCE_UNAVAILABLE, "EMAIL_LOOKUP_FAILED", rawSource, layerStates, null, snapshotHash)
        }
        val hitIds = hits.map { it.docId }.distinct()
        if (hitIds.isEmpty()) return blocked(RecoveryStatus.NEEDS_MANUAL, "EMAIL_HITS_MISSING")
        if (hitIds.size != 1 || hitIds.single() != docId) return blocked(RecoveryStatus.NEEDS_MANUAL, "EMAIL_NOT_UNIQUE")

        // 服务端存量来源 ID：三层必须一致，否则停止。
        val locator = try {
            resolveLocator(presentSources)
        } catch (e: SourceIdentityConflictException) {
            return blocked(RecoveryStatus.SOURCE_IDENTITY_CONFLICT, e.message ?: "SOURCE_IDENTITY_CONFLICT")
        } ?: return blocked(RecoveryStatus.SOURCE_LOCATOR_MISSING, "SOURCE_LOCATOR_MISSING")

        val extraction = try {
            extractSource(locator, email)
        } catch (e: OpenAlexBudgetDeferredException) {
            return blocked(RecoveryStatus.DEFERRED, "BUDGET_DEFERRED")
        } catch (e: SourceIdentityConflictException) {
            return blocked(RecoveryStatus.SOURCE_IDENTITY_CONFLICT, e.message ?: "SOURCE_IDENTITY_CONFLICT")
        } catch (e: SourceUnavailableException) {
            return blocked(RecoveryStatus.SOURCE_UNAVAILABLE, e.message ?: "SOURCE_UNAVAILABLE")
        } catch (e: Exception) {
            log.warn("恢复预览精确取证失败 docId={}: {}", docId, e.message)
            return blocked(RecoveryStatus.SOURCE_UNAVAILABLE, "SOURCE_READ_FAILED")
        }

        val incoming = extraction.incoming
            ?: return blocked(RecoveryStatus.NEEDS_MANUAL, extraction.reasonCode)
        val proposalHash = proposalHash(docId, incoming)
        val presentLayers = snapshots.mapNotNull { (l, s) -> s?.let { l to it.source } }
        // I-3：本次申请会写下的目标 proof（与 repair service 同源、确定性）。
        val target = DiscoveryIdentity.verified(
            incoming.email, incoming.givenNames, incoming.familyNames,
            incoming.identityEvidence!!, incoming.orcidId, incoming.openAlexAuthorId
        )

        // 已有 proof 只接受「本次目标值」（同一原申请的部分层已完成，可续跑）；任何第三种值立即停止。
        for ((_, source) in presentLayers) {
            val proof = proofOf(source)
            if (proof != null && proof != target) {
                return blocked(RecoveryStatus.NEEDS_MANUAL, "EXISTING_PROOF")
            }
        }

        // 姓名适用性：已有非空姓名段必须与来源一致；三层全有姓名只缺 proof → 走 01。
        var anyBlank = false
        var allAtTarget = true
        for ((_, source) in presentLayers) {
            val given = (source["givenNames"] as? String)?.takeIf { it.isNotBlank() }
            val family = (source["familyNames"] as? String)?.takeIf { it.isNotBlank() }
            if ((given != null && given != incoming.givenNames) || (family != null && family != incoming.familyNames)) {
                return blocked(RecoveryStatus.NEEDS_MANUAL, "NAME_CONFLICT")
            }
            if (given == null || family == null) anyBlank = true
            if (proofOf(source) != target || given == null || family == null) allAtTarget = false
        }
        // 全名已齐且未全部达到本次目标（缺 proof 等）→ 仍走 01；三层都已是本次目标值 → 可续跑
        // （只差人物登记/准入重验，见 apply），不再被「已有 proof」挡在人工处理。
        if (!anyBlank && !allAtTarget) {
            return blocked(RecoveryStatus.ROUTE_TO_REPAIR, "ROUTE_TO_REPAIR")
        }

        for ((_, source) in presentLayers) {
            if (incomingAuthorIdConflicts(source, incoming)) {
                return blocked(RecoveryStatus.NEEDS_MANUAL, "AUTHOR_ID_CONFLICT")
            }
        }

        val preview = RecoveryPreview(
            docId = docId,
            status = RecoveryStatus.RECOVERABLE,
            reason = null,
            identityRuleVersion = DiscoveryIdentity.EXTRACTION_VERSION,
            snapshotHash = snapshotHash,
            proposalHash = proposalHash,
            layers = layerStates,
            sourceLocator = locator.describe(),
            sourceUrl = locator.url(),
            evidenceSource = incoming.identityEvidence?.substringBefore(':'),
            evidenceHash = incoming.identityEvidence?.substringAfter(':'),
            proposedGivenNames = incoming.givenNames,
            proposedFamilyNames = incoming.familyNames,
            blockedReasons = emptyList(),
            observedAt = Instant.now(clock).toString()
        )
        return Inspection(preview, rawSource, incoming)
    }

    private fun inspectionResult(
        docId: String,
        status: RecoveryStatus,
        reason: String?,
        rawSource: Map<String, Any?>,
        layerStates: List<RecoveryLayerState>,
        incoming: AuthorEmail?,
        snapshotHash: String? = null,
        blockedReasons: List<String> = listOfNotNull(reason)
    ): Inspection {
        val preview = RecoveryPreview(
            docId = docId,
            status = status,
            reason = reason,
            identityRuleVersion = DiscoveryIdentity.EXTRACTION_VERSION,
            snapshotHash = snapshotHash,
            proposalHash = null,
            layers = layerStates,
            sourceLocator = null,
            sourceUrl = null,
            evidenceSource = null,
            evidenceHash = null,
            proposedGivenNames = null,
            proposedFamilyNames = null,
            blockedReasons = blockedReasons,
            observedAt = Instant.now(clock).toString()
        )
        return Inspection(preview, rawSource, incoming)
    }

    // ── 来源定位 / 精确取证 ────────────────────────────────────────────────

    private class SourceIdentityConflictException(message: String) : IllegalStateException(message)
    private class SourceUnavailableException(message: String) : IllegalStateException(message)

    private class SourceLocator(val pmcId: String?, val doi: String?, val pmid: String?) {
        fun url(): String = when {
            pmcId != null -> "https://www.ebi.ac.uk/europepmc/webservices/rest/$pmcId/fullTextXML"
            doi != null -> "https://api.openalex.org/works/doi:$doi"
            else -> "https://api.openalex.org/works/pmid:$pmid"
        }

        fun describe(): String = when {
            pmcId != null -> "pmcId:$pmcId"
            doi != null -> "doi:$doi"
            else -> "pmid:$pmid"
        }
    }

    /** 三层 `externalIds` 的同键值必须一致；不一致即停止（绝不任取一层）。 */
    private fun resolveLocator(sources: List<Map<String, Any?>>): SourceLocator? {
        val maps = sources.map { externalIds(it) }
        val keys = listOf("pmcId", "doi", "pmid")
        for (key in keys) {
            val values = maps.mapNotNull { it[key]?.takeIf { v -> v.isNotBlank() } }.distinct()
            if (values.size > 1) throw SourceIdentityConflictException("SOURCE_IDENTITY_CONFLICT:$key")
        }
        fun first(key: String): String? = maps.firstNotNullOfOrNull { it[key]?.takeIf { v -> v.isNotBlank() } }
        val pmcId = first("pmcId")?.takeIf { it.matches(Regex("PMC[0-9]+")) }
        val doi = first("doi")
        val pmid = first("pmid")
        if (pmcId == null && doi == null && pmid == null) return null
        return SourceLocator(pmcId, doi, pmid)
    }

    private class ExtractedSource(val incoming: AuthorEmail?, val reasonCode: String)

    private fun extractSource(locator: SourceLocator, email: String): ExtractedSource {
        val emails: List<AuthorEmail> = when {
            locator.pmcId != null -> {
                // I-1：PMCID 路径必须用**原始 JATS** 回源，并核对原文声明的 PMC 编号与服务端存量
                // locator 一致；不符（或原文没有 PMC 编号）即停止，绝不据来源不明的字节签 proof。
                // 沿用既有抓取（含既有预算/重试）与既有解析器，不新增任意 URL 或客户端证据入口。
                val xml = europePmcDataSource.fetchFullTextXml(locator.pmcId)
                    ?: throw SourceUnavailableException("SOURCE_FETCH_FAILED")
                if (!declaredPmcIds(xml).any { it.equals(locator.pmcId, ignoreCase = true) || it == locator.pmcId.removePrefix("PMC") }) {
                    throw SourceIdentityConflictException("SOURCE_ARTICLE_ID_MISMATCH")
                }
                JatsXmlEmailParser.parse(xml)
            }
            locator.doi != null -> {
                val openAlex = openAlexDataSource.getIfAvailable()
                    ?: throw SourceUnavailableException("OPENALEX_DISABLED")
                val work = openAlex.fetchWorkByDoi(locator.doi) ?: throw SourceUnavailableException("SOURCE_WORK_NOT_FOUND")
                openAlex.extractAuthorEmails(work).emails
            }
            else -> {
                val openAlex = openAlexDataSource.getIfAvailable()
                    ?: throw SourceUnavailableException("OPENALEX_DISABLED")
                val work = openAlex.fetchWorkByPmid(locator.pmid!!) ?: throw SourceUnavailableException("SOURCE_WORK_NOT_FOUND")
                openAlex.extractAuthorEmails(work).emails
            }
        }
        val matching = emails.filter { DiscoveryIdentity.normalizedEmail(it.email) == email }
        if (matching.isEmpty()) return ExtractedSource(null, "SOURCE_NO_TARGET_EMAIL")
        val proven = matching.filter { DiscoveryIdentity.validEvidence(it.identityEvidence) }
        if (proven.isEmpty()) return ExtractedSource(null, "SOURCE_EVIDENCE_INVALID")
        val distinct = proven.distinctBy { "${it.givenNames}|${it.familyNames}|${it.identityEvidence}" }
        if (distinct.size > 1) throw SourceIdentityConflictException("SOURCE_IDENTITY_CONFLICT:author")
        val candidate = distinct.single()
        if (candidate.givenNames.isNullOrBlank() || candidate.familyNames.isNullOrBlank()) {
            return ExtractedSource(null, "SOURCE_NO_TARGET_EMAIL")
        }
        return ExtractedSource(candidate, "RECOVERABLE")
    }

    /**
     * I-1：从原始 JATS 里读出**PMC 形态**的明确论文编号（`article-id[@pub-id-type=pmc*]`，含
     * `PMC7759461` 与裸数字两种写法）。只认 PMC 类型，绝不用 PMID/DOI 冒充；解析失败按「无编号」处理
     * （因此不会签出 proof）。与 [JatsXmlEmailParser] 使用同样的安全解析设置。
     */
    private fun declaredPmcIds(xml: ByteArray): Set<String> = try {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val doc = factory.newDocumentBuilder().parse(java.io.ByteArrayInputStream(xml))
        val nodes = doc.getElementsByTagName("article-id")
        (0 until nodes.length).mapNotNull { index ->
            val node = nodes.item(index) as? org.w3c.dom.Element ?: return@mapNotNull null
            val type = node.getAttribute("pub-id-type").trim().lowercase()
            if (!type.startsWith("pmc")) return@mapNotNull null
            node.textContent?.trim()?.takeIf { it.isNotEmpty() }
        }.toSet<String>()
    } catch (e: Exception) {
        log.debug("读取 JATS PMC 编号失败: {}", e.message)
        emptySet<String>()
    }

    // ── 判定辅助 ────────────────────────────────────────────────────────────

    private fun layerState(level: ExpertIndexLevel, snap: ExpertIndexWriterService.DiscoverySnapshot?): RecoveryLayerState = RecoveryLayerState(
        level = level,
        present = snap != null,
        givenNames = snap?.source?.get("givenNames") as? String,
        familyNames = snap?.source?.get("familyNames") as? String,
        hasProof = snap != null && hasProof(snap.source),
        seqNo = snap?.seqNo,
        primaryTerm = snap?.primaryTerm
    )

    private fun hasProof(source: Map<String, Any?>): Boolean = source["identityVerification"] != null

    private fun hasAcademicFields(source: Map<String, Any?>): Boolean = academicFieldKeys.any { key ->
        val value = source[key] ?: return@any false
        when (value) {
            is Collection<*> -> value.isNotEmpty()
            is String -> value.isNotBlank()
            else -> true
        }
    }

    private fun authorIdsConflictAcrossLayers(sources: List<Map<String, Any?>>): Boolean {
        val orcids = mutableSetOf<String>()
        val openAlex = mutableSetOf<String>()
        for (source in sources) {
            val ids = externalIds(source)
            ids["orcid"]?.takeIf { it.isNotBlank() }?.let { orcids += it.lowercase() }
            ids["openAlexAuthorId"]?.takeIf { it.isNotBlank() }?.let { openAlex += it.lowercase() }
            val proof = proofOf(source)
            proof?.orcid?.takeIf { it.isNotBlank() }?.let { orcids += it.lowercase() }
            proof?.openAlexAuthorId?.takeIf { it.isNotBlank() }?.let { openAlex += it.lowercase() }
        }
        return orcids.size > 1 || openAlex.size > 1
    }

    private fun incomingAuthorIdConflicts(source: Map<String, Any?>, incoming: AuthorEmail): Boolean {
        val ids = externalIds(source)
        return idConflict(ids["orcid"], incoming.orcidId) || idConflict(ids["openAlexAuthorId"], incoming.openAlexAuthorId)
    }

    private fun idConflict(stored: String?, incoming: String?): Boolean {
        val left = stored?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val right = incoming?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return !left.equals(right, ignoreCase = true)
    }

    private fun proofOf(source: Map<String, Any?>): com.weibo.talentintroduction.expert.domain.IdentityVerification? {
        val node: JsonNode = source["identityVerification"]?.let { mapper.valueToTree(it) } ?: return null
        return DiscoveryIdentity.read(node)
    }

    @Suppress("UNCHECKED_CAST")
    private fun externalIds(source: Map<String, Any?>): Map<String, String> {
        val raw = source["externalIds"] ?: return emptyMap()
        val map: Map<*, *> = when (raw) {
            is Map<*, *> -> raw
            is String -> try {
                mapper.readValue(raw, Map::class.java)
            } catch (_: Exception) {
                emptyMap<Any, Any>()
            }
            else -> emptyMap<Any, Any>()
        }
        return map.entries.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { key to it } } }.toMap()
    }

    /** I-3：`snapshotHash` —— 三层身份相关快照（含版本）的规范化指纹；apply 重算不一致即 409。 */
    private fun snapshotHash(
        docId: String,
        snapshots: Map<ExpertIndexLevel, ExpertIndexWriterService.DiscoverySnapshot?>
    ): String {
        val canonical = layers.joinToString("\n") { level ->
            val snap = snapshots[level]
            if (snap == null) "${level.name}|absent"
            else listOf(
                level.name, docId, snap.seqNo.toString(), snap.primaryTerm.toString(),
                snap.source["email"]?.toString().orEmpty(),
                snap.source["givenNames"]?.toString().orEmpty(),
                snap.source["familyNames"]?.toString().orEmpty(),
                if (hasProof(snap.source)) "proof" else "no-proof",
                externalIds(snap.source).toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }
            ).joinToString("|")
        }
        return DiscoveryIdentity.hash(canonical)
    }

    /** I-3：`proposalHash` —— 本次拟写身份（含证据与提取版本）的规范化指纹。 */
    private fun proposalHash(docId: String, incoming: AuthorEmail): String = DiscoveryIdentity.hash(
        listOf(
            docId, incoming.givenNames.orEmpty(), incoming.familyNames.orEmpty(),
            incoming.identityEvidence.orEmpty(), incoming.orcidId.orEmpty(),
            incoming.openAlexAuthorId.orEmpty(), DiscoveryIdentity.EXTRACTION_VERSION.toString()
        ).joinToString("|")
    )
}

/** I-3：预览判定。 */
enum class RecoveryStatus {
    RECOVERABLE,
    ROUTE_TO_REPAIR,
    NEEDS_MANUAL,
    SOURCE_LOCATOR_MISSING,
    SOURCE_IDENTITY_CONFLICT,
    SOURCE_UNAVAILABLE,
    DEFERRED,
    NOT_FOUND
}

/** I-3：apply 判定（控制器据此映射 409/503）。 */
enum class RecoveryApplyStatus {
    APPLIED,
    PARTIAL,
    STALE,
    NOT_RECOVERABLE,
    DEFERRED,
    FAILED
}

/** I-3：单层只读摘要（不含任何写）。 */
data class RecoveryLayerState(
    val level: ExpertIndexLevel,
    val present: Boolean,
    val givenNames: String?,
    val familyNames: String?,
    val hasProof: Boolean,
    val seqNo: Long?,
    val primaryTerm: Long?
)

/** I-3：只读预览结果 —— 供维护 CLI 逐人写入 JSONL。 */
data class RecoveryPreview(
    val docId: String,
    val status: RecoveryStatus,
    val reason: String?,
    val identityRuleVersion: Int,
    val snapshotHash: String?,
    val proposalHash: String?,
    val layers: List<RecoveryLayerState>,
    val sourceLocator: String?,
    val sourceUrl: String?,
    val evidenceSource: String?,
    val evidenceHash: String?,
    val proposedGivenNames: String?,
    val proposedFamilyNames: String?,
    val blockedReasons: List<String>,
    val observedAt: String
)

/** I-3：apply 结果。[layers] 保留逐层前后 proof；[admissionDecision] 仅 APPLIED 且重验成功时非空。 */
data class RecoveryApplyResult(
    val docId: String,
    val status: RecoveryApplyStatus,
    val reason: String?,
    val layers: List<IdentityRepairLayer>,
    val admissionDecision: String?,
    val admissionIdentityChanged: Boolean?,
    val observedAt: String
)
