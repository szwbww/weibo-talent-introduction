package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.InboundIntent
import org.springframework.data.repository.CrudRepository

interface InboundIntentRepository : CrudRepository<InboundIntent, Long> {
    fun findAllByExpertContactIdOrderByCreatedAtDesc(expertContactId: Long): List<InboundIntent>
    fun findAllByExpertContactIdOrderByCreatedAtAsc(expertContactId: Long): List<InboundIntent>
}
