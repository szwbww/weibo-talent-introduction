package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import org.springframework.data.repository.CrudRepository

interface InboundMailProcessingRepository : CrudRepository<InboundMailProcessing, Long> {
    fun findBySenderAccountCodeAndImapUid(senderAccountCode: String, imapUid: Long): InboundMailProcessing?

    fun findAllByProcessStatusOrderByReceivedAtDesc(processStatus: String): List<InboundMailProcessing>

    fun findAllByProcessStatusAndExpertContactIdIsNullOrderByReceivedAtDesc(processStatus: String): List<InboundMailProcessing>

    fun findAllByExpertContactIdIsNullAndProcessStatusOrderByReceivedAtDesc(
        processStatus: String
    ): List<InboundMailProcessing>

    fun countByProcessStatus(processStatus: String): Long
}
