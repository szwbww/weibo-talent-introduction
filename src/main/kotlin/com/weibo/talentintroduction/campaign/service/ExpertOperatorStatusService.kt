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
        expertIndexWriterService.syncCandidateOperatorStatus(updated.orcidId, target.name)
        return updated
    }

    @Transactional
    fun updateAutomatically(
        contact: ExpertContact,
        targetStatus: OperatorStatus,
        reason: String
    ): ExpertContact {
        val current = OperatorStatus.entries.firstOrNull { it.name == contact.operatorStatus }
        if (current == OperatorStatus.COMPLETED) {
            return contact
        }
        if (targetStatus == OperatorStatus.REPLIED &&
            current != null &&
            current.ordinal > OperatorStatus.REPLIED.ordinal) {
            return contact
        }
        val updated = expertContactRepository.save(contact.copy(operatorStatus = targetStatus.name))
        expertIndexWriterService.syncCandidateOperatorStatus(updated.orcidId, targetStatus.name)
        return updated
    }
}