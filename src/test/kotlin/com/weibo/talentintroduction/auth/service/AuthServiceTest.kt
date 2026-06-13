package com.weibo.talentintroduction.auth.service

import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.repository.AdminUserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.LocalDateTime

class AuthServiceTest {

    private val adminUserRepository = mock(AdminUserRepository::class.java)
    private val passwordEncoder = BCryptPasswordEncoder()
    private val authService = AuthService(adminUserRepository, passwordEncoder)

    @Test
    fun `login success`() {
        val rawPassword = "admin"
        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = passwordEncoder.encode(rawPassword),
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(user)
        `when`(adminUserRepository.save(Mockito.any(AdminUser::class.java))).thenAnswer { it.arguments[0] }

        val loggedInUser = authService.login("admin", rawPassword)
        assertNotNull(loggedInUser)
        assertEquals("admin", loggedInUser.username)
        assertNotNull(loggedInUser.lastLoginAt)
    }

    @Test
    fun `login failure due to user not found`() {
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(null)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            authService.login("admin", "admin")
        }
        assertEquals("用户名或密码错误", exception.message)
    }

    @Test
    fun `login failure due to wrong password`() {
        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = passwordEncoder.encode("admin"),
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(user)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            authService.login("admin", "wrong_pwd")
        }
        assertEquals("用户名或密码错误", exception.message)
    }

    @Test
    fun `login with empty or invalid length inputs`() {
        var exception = assertThrows(IllegalArgumentException::class.java) { authService.login("", "admin") }
        assertEquals("用户名或密码错误", exception.message)

        exception = assertThrows(IllegalArgumentException::class.java) { authService.login("admin", "") }
        assertEquals("用户名或密码错误", exception.message)

        exception = assertThrows(IllegalArgumentException::class.java) { authService.login("a".repeat(65), "admin") }
        assertEquals("用户名或密码错误", exception.message)

        exception = assertThrows(IllegalArgumentException::class.java) { authService.login("admin", "p".repeat(129)) }
        assertEquals("用户名或密码错误", exception.message)
    }

    @Test
    fun `changePassword success`() {
        val oldPassword = "admin"
        val newPassword = "new_password_123"
        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = passwordEncoder.encode(oldPassword),
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(user)
        `when`(adminUserRepository.save(Mockito.any(AdminUser::class.java))).thenAnswer {
            val saved = it.arguments[0] as AdminUser
            assertFalse(saved.mustChangePassword)
            assertTrue(passwordEncoder.matches(newPassword, saved.passwordHash))
            assertEquals(user.id, saved.id)
            assertEquals(user.username, saved.username)
            assertEquals(user.createdAt, saved.createdAt)
            saved
        }

        authService.changePassword("admin", oldPassword, newPassword)
    }

    @Test
    fun `changePassword fails if old password incorrect`() {
        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = passwordEncoder.encode("admin"),
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(user)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            authService.changePassword("admin", "wrong_old", "new_password")
        }
        assertEquals("原密码错误", exception.message)
    }

    @Test
    fun `changePassword fails if new password less than 8 characters`() {
        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = passwordEncoder.encode("admin"),
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(user)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            authService.changePassword("admin", "admin", "short")
        }
        assertEquals("新密码长度不能少于8位", exception.message)
    }

    @Test
    fun `changePassword fails if new password same as username`() {
        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = passwordEncoder.encode("admin_old_pwd"),
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(user)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            authService.changePassword("admin", "admin_old_pwd", "admin")
        }
        assertEquals("新密码不能与用户名相同", exception.message)
    }

    @Test
    fun `changePassword fails if new password same as old password`() {
        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = passwordEncoder.encode("admin_old_password"),
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(user)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            authService.changePassword("admin", "admin_old_password", "admin_old_password")
        }
        assertEquals("新密码不能与原密码相同", exception.message)
    }

    @Test
    fun `changePassword fails if username not found`() {
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(null)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            authService.changePassword("admin", "admin", "new_password")
        }
        assertEquals("用户不存在", exception.message)
    }

    @Test
    fun `findUser checks`() {
        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = "hashed_old",
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(user)

        assertEquals(user, authService.findUser("admin"))
        assertEquals(user, authService.findUser(" admin "))
        assertNull(authService.findUser(""))
        assertNull(authService.findUser("a".repeat(65)))
    }
}
