package com.weibo.talentintroduction.auth.service

import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.repository.AdminUserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.springframework.boot.ApplicationArguments
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime

class AdminUserBootstrapTest {

    private val adminUserRepository = mock(AdminUserRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val bootstrap = AdminUserBootstrap(adminUserRepository, passwordEncoder)
    private val args = mock(ApplicationArguments::class.java)

    @Test
    fun `creates admin when not exists`() {
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(null)
        `when`(passwordEncoder.encode("admin")).thenReturn("hashed_admin")
        `when`(adminUserRepository.save(Mockito.any(AdminUser::class.java))).thenAnswer { it.arguments[0] }

        bootstrap.run(args)

        verify(adminUserRepository, times(1)).findByUsername("admin")
        verify(adminUserRepository, times(1)).save(Mockito.any(AdminUser::class.java))
    }

    @Test
    fun `does nothing when admin already exists`() {
        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = "hashed_admin",
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(user)

        bootstrap.run(args)

        verify(adminUserRepository, times(1)).findByUsername("admin")
        verify(adminUserRepository, never()).save(Mockito.any(AdminUser::class.java))
    }

    @Test
    fun `handles concurrent unique key conflict and succeeds`() {
        `when`(adminUserRepository.findByUsername("admin"))
            .thenReturn(null)
            .thenReturn(AdminUser(
                id = 1L,
                username = "admin",
                passwordHash = "hashed_admin",
                mustChangePassword = true,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            ))

        `when`(passwordEncoder.encode("admin")).thenReturn("hashed_admin")
        `when`(adminUserRepository.save(Mockito.any(AdminUser::class.java)))
            .thenThrow(RuntimeException("Duplicate key error"))

        assertDoesNotThrow {
            bootstrap.run(args)
        }

        verify(adminUserRepository, times(2)).findByUsername("admin")
    }

    @Test
    fun `rethrows database exception when concurrent check also fails`() {
        `when`(adminUserRepository.findByUsername("admin")).thenReturn(null)
        `when`(passwordEncoder.encode("admin")).thenReturn("hashed_admin")
        `when`(adminUserRepository.save(Mockito.any(AdminUser::class.java)))
            .thenThrow(RuntimeException("Some other DB error"))

        assertThrows(RuntimeException::class.java) {
            bootstrap.run(args)
        }

        verify(adminUserRepository, times(2)).findByUsername("admin")
    }
}
