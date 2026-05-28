package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.MailAttachment
import org.springframework.data.repository.CrudRepository

interface MailAttachmentRepository : CrudRepository<MailAttachment, Long> {
    fun findAllByMailRecordIdOrderByCreatedAtAsc(mailRecordId: Long): List<MailAttachment>
}
