package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.OperatorStatus
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ExpertOperatorStatusService(
    private val expertContactRepository: ExpertContactRepository,
    private val operatorActionLogService: OperatorActionLogService,
    private val expertIndexWriterService: ExpertIndexWriterService
) {
    @Transactional
    fun changeStatus(
        contactId: Long,
        targetStatus: String,
        operatorName: String?,
        note: String?,
        inboundProcessingId: Long? = null
    ): ExpertContact {
        val target = OperatorStatus.fromName(targetStatus)
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }
        val oldStatus = contact.operatorStatus
        val updated = expertContactRepository.save(contact.copy(operatorStatus = target.name))
        operatorActionLogService.record(
            targetType = "EXPERT_CONTACT",
            targetId = contactId,
            actionType = OperatorActionType.CHANGE_OPERATOR_STATUS,
            expertContactId = contactId,
            inboundProcessingId = inboundProcessingId,
            before = mapOf("operatorStatus" to oldStatus),
            after = mapOf("operatorStatus" to target.name),
            operatorName = operatorName,
            note = note
        )
        expertIndexWriterService.syncOperatorStatus(updated.orcidId, target.name)
        return updated
    }

    @Transactional
    fun updateAutomatically(
        contact: ExpertContact,
        targetStatus: OperatorStatus,
        reason: String
    ): ExpertContact {
        // I-2: EMAIL_INVALID 是枚举外的旁路终态，无条件短路（不进 ordinal 比较）
        if (contact.operatorStatus == "EMAIL_INVALID") {
            return contact
        }
        val current = OperatorStatus.entries.firstOrNull { it.name == contact.operatorStatus }
        if (current == OperatorStatus.COMPLETED) {
            return contact
        }
        // I-1: 单调不回退 —— 自动写入只沿 ordinal 正向推进
        if (current != null && current.ordinal >= targetStatus.ordinal) {
            return contact
        }
        val updated = expertContactRepository.save(contact.copy(operatorStatus = targetStatus.name))
        expertIndexWriterService.syncOperatorStatus(updated.orcidId, targetStatus.name)
        return updated
    }

    /**
     * 退信侧旁路终态写入口（I-3）。EMAIL_INVALID 不在 OperatorStatus 枚举内，
     * 既有两个出口都无法表达它：
     *   - updateAutomatically 形参类型为 OperatorStatus，字面无法传入；
     *   - changeStatus 走 OperatorStatus.fromName() 会 error()，且会写
     *     CHANGE_OPERATOR_STATUS 审计（对账的人工覆盖判别器，I-5 禁止）。
     * 故新增本方法，写入语句留在本文件内以保持守卫白名单闭包不变。
     */
    @Transactional
    fun markEmailInvalid(contact: ExpertContact, reason: String): ExpertContact {
        // I-4：已推进状态不回退 —— 已回信即证明地址可达
        val current = OperatorStatus.entries.firstOrNull { it.name == contact.operatorStatus }
        if (current != null && current.ordinal >= OperatorStatus.REPLIED.ordinal) {
            return contact
        }
        // 幂等：已是 EMAIL_INVALID 则零交互
        if (contact.operatorStatus == EMAIL_INVALID) {
            return contact
        }
        val updated = expertContactRepository.save(contact.copy(operatorStatus = EMAIL_INVALID))
        expertIndexWriterService.syncOperatorStatus(updated.orcidId, EMAIL_INVALID)
        // I-5：自动路径不写 CHANGE_OPERATOR_STATUS 审计
        return updated
    }

    companion object {
        const val EMAIL_INVALID = "EMAIL_INVALID"
    }
}