package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.OperatorStatus
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class AutomaticApplicationPromotionService(
    private val expertContactRepository: ExpertContactRepository,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val expertOperatorStatusService: ExpertOperatorStatusService,
    private val mailRecordRepository: MailRecordRepository
) {
    private val log = LoggerFactory.getLogger(AutomaticApplicationPromotionService::class.java)

    fun promoteByReplyCountIfNeeded(
        contact: ExpertContact,
        receivedAt: LocalDateTime,
        sourceInboundId: Long
    ): ExpertContact {
        if (contact.applicationIndexed) return contact
        if (contact.orcidId.isBlank()) return contact
        val contactId = contact.id ?: return contact

        val replyCount = mailRecordRepository.countInboundReplies(contactId)
        if (replyCount <= 2) return contact

        return doPromote(contact, receivedAt, sourceInboundId, "REPLY_COUNT_GT_2", OperatorStatus.REPLIED)
    }

    fun promoteByMaterialIfNeeded(
        contact: ExpertContact,
        receivedAt: LocalDateTime,
        sourceInboundId: Long,
        savedDocumentCount: Int
    ): ExpertContact {
        if (savedDocumentCount <= 0) return contact

        val statusUpdated = expertOperatorStatusService.updateAutomatically(
            contact,
            OperatorStatus.MATERIALS_RECEIVED,
            "MATERIAL_ATTACHED"
        )

        if (contact.applicationIndexed) return statusUpdated
        if (contact.orcidId.isBlank()) return statusUpdated

        return doPromote(statusUpdated, receivedAt, sourceInboundId, "MATERIAL_ATTACHED", OperatorStatus.MATERIALS_RECEIVED)
    }

    private fun doPromote(
        contact: ExpertContact,
        receivedAt: LocalDateTime,
        sourceInboundId: Long,
        triggeredBy: String,
        fallbackStatus: OperatorStatus
    ): ExpertContact {
        if (contact.applicationIndexed) return contact

        val firstReplyAt = contact.firstReplyAt ?: receivedAt
        val firstReplyInstant = firstReplyAt
            .atZone(ZoneId.systemDefault())
            .toInstant()

        try {
            val ok = expertIndexWriterService.promoteToApplication(
                orcid = contact.orcidId,
                contact = contact,
                firstReplyAt = firstReplyInstant,
                sourceInboundId = sourceInboundId,
                triggeredBy = triggeredBy
            )
            if (ok) {
                val saved = expertContactRepository.save(
                    contact.copy(
                        applicationIndexed = true,
                        currentIndexLevel = "APPLICATION",
                        firstReplyAt = firstReplyAt
                    )
                )
                val withStatus = expertOperatorStatusService.updateAutomatically(
                    saved, fallbackStatus, triggeredBy
                )
                return withStatus
            }
        } catch (e: Exception) {
            log.warn("ES promotion failed for contact {} (orcid={}), triggered by {}", contact.id, contact.orcidId, triggeredBy, e)
        }
        return contact
    }
}
