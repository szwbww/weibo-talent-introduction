package com.weibo.talentintroduction.expert.repository

import com.weibo.talentintroduction.expert.domain.EmailValidationCache
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

interface EmailValidationCacheRepository : CrudRepository<EmailValidationCache, Long> {

    @Query("SELECT * FROM email_validation_cache WHERE email = :email LIMIT 1")
    fun findByEmail(email: String): EmailValidationCache?

    @Query("SELECT * FROM email_validation_cache WHERE domain = :domain AND mx_valid IS NOT NULL AND expires_at > :now ORDER BY verified_at DESC LIMIT 1")
    fun findByDomainWithMxResult(domain: String, now: LocalDateTime): EmailValidationCache?
}
