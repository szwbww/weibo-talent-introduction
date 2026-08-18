package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.AutoReplyConfidenceLog
import org.springframework.data.repository.CrudRepository

interface AutoReplyConfidenceLogRepository : CrudRepository<AutoReplyConfidenceLog, Long>
