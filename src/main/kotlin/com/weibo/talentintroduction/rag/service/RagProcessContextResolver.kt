package com.weibo.talentintroduction.rag.service

import com.weibo.talentintroduction.campaign.repository.ExpertMaterialStatusRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 计划 03 (T3): ProcessContext 的 DB → [RagProcessContext] 映射，严格按 I-19 / D-7：
 *
 * - `cvStatus` 取自 `expert_material_status` 中该联系人 `material_code='CV'` 的行：
 *   `PROVIDED → RECEIVED`、`DECLINED → UNKNOWN`、缺行 → `MISSING`。
 *   V111 语义：表只保存 PROVIDED/DECLINED，缺行唯一解释为 PENDING —— 防御性把
 *   意外出现的 `PENDING` 行也按缺行处理（→ MISSING）；其余未知取值记为 UNKNOWN
 *   并 warn（不触发 CV 索要、不冒充已收到）。
 * - `expertReplyCount` = 该联系人 `mail_record` 中 `direction='INBOUND'` 的条数
 *   （应用层过滤计数；`findAllByExpertContactIdOrderByCreatedAtAsc` 已存在，不加仓储方法）。
 * - `expertTags` 本轮恒为空列表。
 *
 * 每次 compose 实时查库、不缓存 —— Interaction point 1（运营改 CV 状态后下一次
 * 生成立即按新状态处理）由本设计自然成立。
 */
@Service
class RagProcessContextResolver(
    private val materialStatusRepository: ExpertMaterialStatusRepository,
    private val mailRecordRepository: MailRecordRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun resolve(contactId: Long): RagProcessContext {
        val cvRow = materialStatusRepository.findByExpertContactIdAndMaterialCode(contactId, CV_MATERIAL_CODE)
        val cvStatus = when (cvRow?.materialStatus?.trim()?.uppercase()) {
            null -> CV_STATUS_MISSING
            "PROVIDED" -> CV_STATUS_RECEIVED
            "DECLINED" -> CV_STATUS_UNKNOWN
            "PENDING" -> CV_STATUS_MISSING
            else -> {
                log.warn(
                    "Unexpected expert_material_status materialStatus={} for contactId={} materialCode={}; mapped to UNKNOWN",
                    cvRow.materialStatus, contactId, CV_MATERIAL_CODE
                )
                CV_STATUS_UNKNOWN
            }
        }
        val replyCount = mailRecordRepository
            .findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
            .count { it.direction.equals("INBOUND", ignoreCase = true) }
        return RagProcessContext(
            expertReplyCount = replyCount,
            expertTags = emptyList(),
            cvStatus = cvStatus
        )
    }

    companion object {
        const val CV_MATERIAL_CODE = "CV"
        const val CV_STATUS_RECEIVED = "RECEIVED"
        const val CV_STATUS_UNKNOWN = "UNKNOWN"
        const val CV_STATUS_MISSING = "MISSING"
    }
}
