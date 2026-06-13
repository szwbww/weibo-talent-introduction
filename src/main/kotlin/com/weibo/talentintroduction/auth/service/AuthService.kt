package com.weibo.talentintroduction.auth.service

import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.repository.AdminUserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AuthService(
    private val adminUserRepository: AdminUserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional
    fun login(username: String?, rawPassword: String?): AdminUser {
        val trimmedUsername = username?.trim()
        if (trimmedUsername.isNullOrBlank() || rawPassword.isNullOrBlank()) {
            throw IllegalArgumentException("用户名或密码错误")
        }
        if (trimmedUsername.length > 64 || rawPassword.length > 128) {
            throw IllegalArgumentException("用户名或密码错误")
        }
        val user = adminUserRepository.findByUsername(trimmedUsername)
            ?: throw IllegalArgumentException("用户名或密码错误")

        if (!passwordEncoder.matches(rawPassword, user.passwordHash)) {
            throw IllegalArgumentException("用户名或密码错误")
        }

        val updatedUser = user.copy(
            lastLoginAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        return adminUserRepository.save(updatedUser)
    }

    @Transactional
    fun changePassword(username: String?, oldPassword: String?, newPassword: String?) {
        val trimmedUsername = username?.trim()
        if (trimmedUsername.isNullOrBlank() || oldPassword.isNullOrBlank() || newPassword.isNullOrBlank()) {
            throw IllegalArgumentException("输入参数不能为空")
        }
        if (trimmedUsername != "admin") {
            throw IllegalArgumentException("用户名错误")
        }
        if (oldPassword.length > 128 || newPassword.length > 128) {
            throw IllegalArgumentException("密码长度过长")
        }

        val user = adminUserRepository.findByUsername(trimmedUsername)
            ?: throw IllegalArgumentException("用户不存在")

        if (!passwordEncoder.matches(oldPassword, user.passwordHash)) {
            throw IllegalArgumentException("原密码错误")
        }

        if (newPassword == trimmedUsername) {
            throw IllegalArgumentException("新密码不能与用户名相同")
        }
        if (newPassword.length < 8) {
            throw IllegalArgumentException("新密码长度不能少于8位")
        }
        if (newPassword == oldPassword) {
            throw IllegalArgumentException("新密码不能与原密码相同")
        }

        val updatedUser = user.copy(
            passwordHash = passwordEncoder.encode(newPassword),
            mustChangePassword = false,
            updatedAt = LocalDateTime.now()
        )
        adminUserRepository.save(updatedUser)
    }

    fun findUser(username: String): AdminUser? {
        val trimmed = username.trim()
        if (trimmed.isEmpty() || trimmed.length > 64) {
            return null
        }
        return adminUserRepository.findByUsername(trimmed)
    }
}
