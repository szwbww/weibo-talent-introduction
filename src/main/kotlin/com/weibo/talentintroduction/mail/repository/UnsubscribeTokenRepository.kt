package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.UnsubscribeToken
import org.springframework.data.repository.CrudRepository

interface UnsubscribeTokenRepository : CrudRepository<UnsubscribeToken, Long> {
    fun findByEmail(email: String): UnsubscribeToken?

    fun findByToken(token: String): UnsubscribeToken?
}
