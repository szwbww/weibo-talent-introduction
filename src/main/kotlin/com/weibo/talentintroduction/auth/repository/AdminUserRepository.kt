package com.weibo.talentintroduction.auth.repository

import com.weibo.talentintroduction.auth.domain.AdminUser
import org.springframework.data.repository.CrudRepository

interface AdminUserRepository : CrudRepository<AdminUser, Long> {
    fun findByUsername(username: String): AdminUser?
}
