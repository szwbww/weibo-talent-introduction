package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ExpertIndexLevelOperationService(
    private val expertContactRepository: ExpertContactRepository,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val operatorActionLogService: OperatorActionLogService
) {
    @Transactional
    fun changeLevel(
        contactId: Long,
        targetLevel: String,
        operatorName: String?,
        note: String?,
        inboundProcessingId: Long? = null
    ): ExpertContact {
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }

        if (contact.currentIndexLevel == targetLevel) return contact

        val oldLevel = contact.currentIndexLevel
        val oldApplicationIndexed = contact.applicationIndexed

        val updated = when {
            oldLevel == "RAW" && targetLevel == "CANDIDATE" -> promoteRawToCandidate(contact)
            oldLevel == "RAW" && targetLevel == "APPLICATION" -> promoteRawToApplication(contact)
            contact.currentIndexLevel == "CANDIDATE" && targetLevel == "APPLICATION" -> promoteCandidateToApplication(contact)
            targetLevel == "RAW" -> demoteToRaw(contact)
            contact.currentIndexLevel == "APPLICATION" && targetLevel == "CANDIDATE" ->
                error("APPLICATION can only be demoted to RAW; demotion to CANDIDATE is not supported")
            else -> error("Unsupported level transition: $oldLevel -> $targetLevel")
        }

        operatorActionLogService.record(
            targetType = "EXPERT_CONTACT",
            targetId = contactId,
            actionType = OperatorActionType.CHANGE_INDEX_LEVEL,
            expertContactId = contactId,
            inboundProcessingId = inboundProcessingId,
            before = mapOf(
                "currentIndexLevel" to oldLevel,
                "applicationIndexed" to oldApplicationIndexed
            ),
            after = mapOf(
                "currentIndexLevel" to updated.currentIndexLevel,
                "applicationIndexed" to updated.applicationIndexed
            ),
            operatorName = operatorName,
            note = note
        )

        return updated
    }

    private fun promoteRawToCandidate(contact: ExpertContact): ExpertContact {
        require(contact.currentIndexLevel == "RAW") { "Only RAW contact can be promoted to CANDIDATE" }
        val ok = expertIndexWriterService.promoteToCandidate(contact.orcidId, contact)
        require(ok) { "Failed to promote contact ${contact.id} to CANDIDATE in ES" }
        return expertContactRepository.save(contact.copy(currentIndexLevel = "CANDIDATE"))
    }

    private fun promoteRawToApplication(contact: ExpertContact): ExpertContact {
        require(contact.currentIndexLevel == "RAW") { "Only RAW contact can be promoted to APPLICATION" }
        val firstReplyInstant = if (contact.firstReplyAt != null) {
            contact.firstReplyAt.toInstant(java.time.ZoneId.systemDefault().rules.getOffset(contact.firstReplyAt))
        } else {
            java.time.Instant.now()
        }
        val ok = expertIndexWriterService.promoteToApplication(
            orcid = contact.orcidId,
            contact = contact,
            firstReplyAt = firstReplyInstant,
            triggeredBy = TriggeredBy.OPERATOR
        )
        require(ok) { "Failed to promote to application index" }
        return expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
    }

    private fun promoteCandidateToApplication(contact: ExpertContact): ExpertContact {
        if (contact.applicationIndexed) return contact
        val firstReplyInstant = if (contact.firstReplyAt != null) {
            contact.firstReplyAt.toInstant(java.time.ZoneId.systemDefault().rules.getOffset(contact.firstReplyAt))
        } else {
            java.time.Instant.now()
        }
        val ok = expertIndexWriterService.promoteToApplication(
            orcid = contact.orcidId,
            contact = contact,
            firstReplyAt = firstReplyInstant,
            triggeredBy = TriggeredBy.OPERATOR
        )
        require(ok) { "Failed to promote to application index" }
        return expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
    }

    private fun demoteToRaw(contact: ExpertContact): ExpertContact {
        require(contact.currentIndexLevel != "RAW") { "Contact already in RAW" }
        val ok = expertIndexWriterService.demoteToRaw(contact.orcidId, contact)
        require(ok) { "Failed to demote contact ${contact.id} to RAW in ES" }
        return expertContactRepository.save(contact.copy(currentIndexLevel = "RAW", applicationIndexed = false))
    }
}