package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.audit.repository.OperatorActionLogRepository
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.OperatorStatus
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * P-C operator_status 对账作业（只读，I-1）：
 *
 * 从事件（mail_record / bounce_record / mail_attachment）反推每位联系人的**期望** operator_status
 * （I-4 映射表，全部对应当前仓库既有自动推进实现），与 DB 实际值、ES 三层实际值三方比对，
 * 产出 [ReconcileReport]（总数 / 一致 / DB 与期望不符 / ES 与 DB 不符 / 人工覆盖 / 各类差异前 20 条样本）。
 *
 * 首版只报告、不自动修：本服务不注入任何 writer（I-1），不调用任何 save/update/index 写方法；
 * 对 ES 仅做 `_search` 只读查询。
 *
 * - I-2：存在 `action_type='CHANGE_OPERATOR_STATUS'` 审计日志的联系人视为人工权威，单列"人工覆盖"，
 *   不计入异常（changeStatus 写审计、updateAutomatically 不写——01 落地后判别器仍成立）。
 * - I-3：`COMPLETED` 无事件来源，一律视为人工终态：不参与期望值差异判定；
 *   ES 与 DB 的**事实**比对与其无关，仍正常参与。
 */
@Service
class OperatorStatusReconcileService(
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val bounceRecordRepository: BounceRecordRepository,
    private val operatorActionLogRepository: OperatorActionLogRepository,
    private val restTemplate: RestTemplate,
    private val properties: ElasticsearchProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 全表扫描 + 内存比对（expert_contact 2062 行 / mail_record 2157 行，规模小，一次读入）。 */
    fun reconcile(): ReconcileReport {
        val contacts = expertContactRepository.findAll().toList()
        val mailRecords = mailRecordRepository.findAll()
        val attachments = mailAttachmentRepository.findAll()
        val bounces = bounceRecordRepository.findAll()

        val contactIds = contacts.mapNotNull { it.id }
        // I-2：人工覆盖判别器——有 CHANGE_OPERATOR_STATUS 审计日志的 contact id 集合
        val humanOverrideIds = if (contactIds.isEmpty()) {
            emptySet()
        } else {
            operatorActionLogRepository.findContactIdsWithChangeOperatorStatusLogs(contactIds).toSet()
        }

        val recordsByContact = mailRecords.groupBy { it.expertContactId }
        // 材料附件挂在 INBOUND mail_record 上（MailAttachmentService.saveInboundAttachments 以 mailRecordId 关联）
        val attachedMailRecordIds = attachments.mapNotNull { it.mailRecordId }.toSet()
        // I-4 EMAIL_INVALID 判据（两条旁路终态证据）：
        // ① HARD 退信记录（BounceCollectionService:105-107）；② 首封外发 PERMANENT 失败
        // （ManualInitialOutreachService:697,706 + ManualOutreachTxHelper.recordFailure:96 的 errorSummary "PERMANENT:…"）
        val hardBounceContactIds = bounces
            .filter { it.bounceType == "HARD" && it.originalExpertContactId != null }
            .map { it.originalExpertContactId!! }
            .toSet()
        val permanentFailureContactIds = mailRecords
            .filter {
                it.direction == "OUTBOUND" &&
                    it.mailType == "INTRODUCTION" &&
                    it.sendStatus == "FAILED" &&
                    (it.errorSummary?.startsWith("PERMANENT:") == true)
            }
            .map { it.expertContactId }
            .toSet()

        val esStatusByNormalizedOrcid = fetchEsStatuses(contacts)

        val dbVsExpectedSamples = mutableListOf<ReconcileSample>()
        val esVsDbSamples = mutableListOf<ReconcileSample>()
        val humanOverrideSamples = mutableListOf<ReconcileSample>()
        var dbVsExpected = 0
        var esVsDb = 0
        var humanOverride = 0

        for (contact in contacts) {
            val contactId = contact.id ?: continue
            val dbStatus = contact.operatorStatus
            val expectedStatus = deriveExpectedStatus(
                contactId,
                recordsByContact[contactId].orEmpty(),
                attachedMailRecordIds,
                hardBounceContactIds,
                permanentFailureContactIds
            )
            val esStatus = contact.orcidId.takeIf { it.isNotBlank() }
                ?.let { esStatusByNormalizedOrcid[ExpertIdNormalizer.normalize(it)] }
                ?: "NOT_CONTACTED"

            if (humanOverrideIds.contains(contactId)) {
                // I-2：人工覆盖单列，不计入异常（三方取值仍在样本中展示，不藏信息）
                humanOverride++
                if (humanOverrideSamples.size < SAMPLE_LIMIT) {
                    humanOverrideSamples += sample(contact, expectedStatus, esStatus, CATEGORY_HUMAN_OVERRIDE)
                }
                continue
            }
            // I-3：COMPLETED 人工终态，不参与期望值差异判定
            if (dbStatus != "COMPLETED" && expectedStatus != dbStatus) {
                dbVsExpected++
                if (dbVsExpectedSamples.size < SAMPLE_LIMIT) {
                    dbVsExpectedSamples += sample(contact, expectedStatus, esStatus, CATEGORY_DB_VS_EXPECTED)
                }
            }
            // ES 与 DB 事实比对（与期望值无关；COMPLETED 也参与）
            if (esStatus != dbStatus) {
                esVsDb++
                if (esVsDbSamples.size < SAMPLE_LIMIT) {
                    esVsDbSamples += sample(contact, expectedStatus, esStatus, CATEGORY_ES_VS_DB)
                }
            }
        }

        val total = contacts.size
        val report = ReconcileReport(
            total = total,
            consistent = total - dbVsExpected - esVsDb - humanOverride,
            dbVsExpected = dbVsExpected,
            esVsDb = esVsDb,
            humanOverride = humanOverride,
            samples = dbVsExpectedSamples + esVsDbSamples + humanOverrideSamples
        )
        log.info(
            "operatorStatus reconcile done: total={} consistent={} dbVsExpected={} esVsDb={} humanOverride={}",
            report.total, report.consistent, report.dbVsExpected, report.esVsDb, report.humanOverride
        )
        return report
    }

    private fun sample(
        contact: ExpertContact,
        expectedStatus: String,
        esStatus: String,
        category: String
    ): ReconcileSample = ReconcileSample(
        contactId = contact.id ?: -1L,
        orcid = contact.orcidId,
        expectedStatus = expectedStatus,
        dbStatus = contact.operatorStatus,
        esStatus = esStatus,
        category = category
    )

    /**
     * I-4 期望值映射（逐条对应当前仓库既有自动推进实现）：
     * - CONTACTED：存在 OUTBOUND + INTRODUCTION + SENT 的 mail_record（ManualInitialOutreachService.hasSentIntroduction():895 逐字）
     * - INVITED：存在 OUTBOUND + MEETING_INVITATION + SENT（AutoMailReplyService:484,816）
     * - REPLIED：存在 INBOUND mail_record（AutoMailReplyService:802）
     * - MATERIALS_RECEIVED：INBOUND 邮件有材料附件（AutomaticApplicationPromotionService:50,57；
     *   附件经 MailAttachmentService.saveInboundAttachments 以 mailRecordId 落 mail_attachment）
     * - EMAIL_INVALID：HARD 退信记录（BounceCollectionService:105）或首封外发 PERMANENT 失败
     *   （ManualInitialOutreachService:706）——旁路终态，优先于一切枚举推进
     * - COMPLETED：不可派生（I-3），由调用方单独豁免
     *
     * 多个判据同时成立时取最大 ordinal（与 updateAutomatically 单调不回退语义一致：
     * 系统沿 CONTACTED→REPLIED→MATERIALS_RECEIVED→INVITED 正向推进，期望状态即已达最高里程碑）。
     */
    private fun deriveExpectedStatus(
        contactId: Long,
        records: List<MailRecord>,
        attachedMailRecordIds: Set<Long>,
        hardBounceContactIds: Set<Long>,
        permanentFailureContactIds: Set<Long>
    ): String {
        if (hardBounceContactIds.contains(contactId) || permanentFailureContactIds.contains(contactId)) {
            return "EMAIL_INVALID"
        }
        var maxOrdinal = -1
        val hasIntroductionSent = records.any {
            it.direction == "OUTBOUND" && it.mailType == "INTRODUCTION" && it.sendStatus == "SENT"
        }
        if (hasIntroductionSent) {
            maxOrdinal = maxOf(maxOrdinal, OperatorStatus.CONTACTED.ordinal)
        }
        val hasInbound = records.any { it.direction == "INBOUND" }
        if (hasInbound) {
            maxOrdinal = maxOf(maxOrdinal, OperatorStatus.REPLIED.ordinal)
        }
        val hasMaterialAttachment = records.any { it.direction == "INBOUND" && attachedMailRecordIds.contains(it.id) }
        if (hasMaterialAttachment) {
            maxOrdinal = maxOf(maxOrdinal, OperatorStatus.MATERIALS_RECEIVED.ordinal)
        }
        val hasMeetingInvitationSent = records.any {
            it.direction == "OUTBOUND" && it.mailType == "MEETING_INVITATION" && it.sendStatus == "SENT"
        }
        if (hasMeetingInvitationSent) {
            maxOrdinal = maxOf(maxOrdinal, OperatorStatus.INVITED.ordinal)
        }
        return if (maxOrdinal >= 0) OperatorStatus.entries[maxOrdinal].name else "NOT_CONTACTED"
    }

    /**
     * ES 侧实际值：三层（RAW/CANDIDATE/APPLICATION）各按 500 条一批 terms 查询
     * （与 syncOperatorStatusBatch 同款分批方式），值优先级 CANDIDATE > APPLICATION > RAW
     * （CANDIDATE 为主工作层）；文档存在但 operatorStatus 字段缺失视为 NOT_CONTACTED
     * （syncOperatorStatus 对 NOT_CONTACTED 走字段移除脚本）。
     */
    private fun fetchEsStatuses(contacts: List<ExpertContact>): Map<String, String> {
        val normalizedOrcidIds = contacts
            .mapNotNull { it.orcidId.takeIf { orcid -> orcid.isNotBlank() } }
            .map { ExpertIdNormalizer.normalize(it) }
            .distinct()
        if (normalizedOrcidIds.isEmpty()) return emptyMap()

        val candidate = mutableMapOf<String, String>()
        val application = mutableMapOf<String, String>()
        val raw = mutableMapOf<String, String>()
        for (batch in normalizedOrcidIds.chunked(ES_BATCH_SIZE)) {
            queryEsOperatorStatus(properties.candidateIndexName, batch, candidate)
            queryEsOperatorStatus(properties.applicationIndexName, batch, application)
            queryEsOperatorStatus(properties.rawIndexName, batch, raw)
        }
        val resolved = HashMap<String, String>(candidate.size + application.size + raw.size)
        val allOrcids = LinkedHashSet<String>()
        allOrcids.addAll(candidate.keys)
        allOrcids.addAll(application.keys)
        allOrcids.addAll(raw.keys)
        for (orcid in allOrcids) {
            resolved[orcid] = candidate[orcid] ?: application[orcid] ?: raw[orcid] ?: "NOT_CONTACTED"
        }
        return resolved
    }

    private fun queryEsOperatorStatus(index: String, orcidIds: List<String>, into: MutableMap<String, String>) {
        val searchBody = mapOf(
            "size" to orcidIds.size,
            "_source" to listOf("orcidId", "operatorStatus"),
            "query" to mapOf("terms" to mapOf("orcidId" to orcidIds))
        )
        val response = restTemplate.exchange(
            "${properties.baseUrl}/$index/_search",
            HttpMethod.POST,
            HttpEntity(searchBody, headers()),
            JsonNode::class.java
        )?.body
        val hits = response?.path("hits")?.path("hits") ?: return
        for (hit in hits) {
            val orcid = hit.path("_source").path("orcidId").asText(null) ?: continue
            val status = hit.path("_source").path("operatorStatus").asText("NOT_CONTACTED")
            into[orcid] = status
        }
    }

    private fun headers(): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set(HttpHeaders.AUTHORIZATION, basicAuthHeader())
        }

    private fun basicAuthHeader(): String {
        val raw = "${properties.username}:${properties.password}"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        return "Basic $encoded"
    }

    companion object {
        const val ES_BATCH_SIZE = 500
        const val SAMPLE_LIMIT = 20
        const val CATEGORY_DB_VS_EXPECTED = "DB_VS_EXPECTED"
        const val CATEGORY_ES_VS_DB = "ES_VS_DB"
        const val CATEGORY_HUMAN_OVERRIDE = "HUMAN_OVERRIDE"
    }
}

/** 对账差异样本行：contactId + orcid + 三方取值（期望 / DB / ES）+ 差异分类。 */
data class ReconcileSample(
    val contactId: Long,
    val orcid: String,
    val expectedStatus: String,
    val dbStatus: String,
    val esStatus: String,
    val category: String
)

/**
 * 对账报告：总数 / 一致 / DB 与期望不符 / ES 与 DB 不符 / 人工覆盖 / 各类差异前 20 条样本。
 *
 * [TaskExecutionSummaryProvider] 语义（task 面板）：
 * successCount=一致数、failureCount=异常总数（DB与期望不符 + ES与DB不符）、
 * finalStatus 走既有推导（无异常=SUCCESS，有异常=PARTIAL_SUCCESS/FAILED，
 * 与 BulkSyncResult 先例一致——对账发现漂移即告警，明细见 result_summary）。
 */
data class ReconcileReport(
    val total: Int,
    val consistent: Int,
    val dbVsExpected: Int,
    val esVsDb: Int,
    val humanOverride: Int,
    val samples: List<ReconcileSample>
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = consistent
    override val taskFailureCount: Int get() = dbVsExpected + esVsDb
    override val taskFinalStatus: String? get() = null
}
