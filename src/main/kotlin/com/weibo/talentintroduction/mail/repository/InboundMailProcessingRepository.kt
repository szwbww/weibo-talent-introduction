package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import org.springframework.data.repository.CrudRepository

interface InboundMailProcessingRepository : CrudRepository<InboundMailProcessing, Long> {
    fun findBySenderAccountCodeAndImapUid(senderAccountCode: String, imapUid: Long): InboundMailProcessing?
}
