package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MailSendAttemptRepository : CrudRepository<MailSendAttempt, Long> {
    fun findByOrcidIdAndMailType(orcidId: String, mailType: String): MailSendAttempt?
}
