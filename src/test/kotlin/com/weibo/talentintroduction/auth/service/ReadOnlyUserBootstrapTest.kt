package com.weibo.talentintroduction.auth.service

import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.repository.AdminUserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.util.function.Supplier

/**
 * I-1：内置只读账号只在首次启动创建，重复启动/并发启动不重置既有记录；
 * `talent-introduction.auth.enabled=false` 时不创建（与 [AdminUserBootstrap] 同属性机制）。
 *
 * 用真实 [BCryptPasswordEncoder] 校验「明文被现场编码」与「hash 不等于明文」，
 * 不 mock 编码结果（mock 会把「密码是否正确编码」变成恒真）。
 */
class ReadOnlyUserBootstrapTest {

    private val adminUserRepository = mock(AdminUserRepository::class.java)
    private val passwordEncoder = BCryptPasswordEncoder()
    private val bootstrap = ReadOnlyUserBootstrap(adminUserRepository, passwordEncoder)
    private val args = mock(ApplicationArguments::class.java)

    private val readOnlyUsername = MailReadOnlyPolicy.READER_USERNAME

    @Test
    fun `creates read-only user on first run with hashed initial password and no forced change`() {
        `when`(adminUserRepository.findByUsername(readOnlyUsername)).thenReturn(null)
        `when`(adminUserRepository.save(Mockito.any(AdminUser::class.java))).thenAnswer { it.arguments[0] }

        bootstrap.run(args)

        val captor = ArgumentCaptor.forClass(AdminUser::class.java)
        verify(adminUserRepository, times(1)).findByUsername(readOnlyUsername)
        verify(adminUserRepository, times(1)).save(captor.capture())
        val saved = captor.value

        assertEquals(readOnlyUsername, saved.username)
        assertNotEqualsPlaintext(saved.passwordHash)
        assertTrue(passwordEncoder.matches(INITIAL_PASSWORD, saved.passwordHash), "初始口令须能被现有编码器校验")
        assertFalse(saved.mustChangePassword, "user1 不进入不支持的改密流程")
        assertNull(saved.lastLoginAt)
        assertNotNull(saved.createdAt)
        assertNotNull(saved.updatedAt)
        assertEquals(saved.createdAt, saved.updatedAt)
    }

    @Test
    fun `does not reset an existing modified hash flags or timestamps`() {
        val created = LocalDateTime.now().minusDays(3)
        val updated = created.plusHours(1)
        val existing = AdminUser(
            id = 7L,
            username = readOnlyUsername,
            passwordHash = passwordEncoder.encode("operator_changed_password"),
            mustChangePassword = false,
            lastLoginAt = created.plusHours(2),
            createdAt = created,
            updatedAt = updated
        )
        `when`(adminUserRepository.findByUsername(readOnlyUsername)).thenReturn(existing)

        bootstrap.run(args)

        verify(adminUserRepository, times(1)).findByUsername(readOnlyUsername)
        verify(adminUserRepository, never()).save(Mockito.any(AdminUser::class.java))
    }

    @Test
    fun `handles concurrent unique key conflict by re-querying`() {
        `when`(adminUserRepository.findByUsername(readOnlyUsername))
            .thenReturn(null)
            .thenReturn(
                AdminUser(
                    id = 1L,
                    username = readOnlyUsername,
                    passwordHash = "hashed_by_other_instance",
                    mustChangePassword = false,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            )
        `when`(adminUserRepository.save(Mockito.any(AdminUser::class.java)))
            .thenThrow(RuntimeException("Duplicate entry for key 'uk_admin_user_username'"))

        assertDoesNotThrow { bootstrap.run(args) }

        verify(adminUserRepository, times(2)).findByUsername(readOnlyUsername)
    }

    @Test
    fun `rethrows database exception when the concurrent re-query finds nothing`() {
        `when`(adminUserRepository.findByUsername(readOnlyUsername)).thenReturn(null)
        `when`(adminUserRepository.save(Mockito.any(AdminUser::class.java)))
            .thenThrow(RuntimeException("Some other DB error"))

        val error = assertThrows(RuntimeException::class.java) { bootstrap.run(args) }

        assertEquals("Some other DB error", error.message)
        verify(adminUserRepository, times(2)).findByUsername(readOnlyUsername)
    }

    @Test
    fun `does not create the account when auth is disabled`() {
        val repository = mock(AdminUserRepository::class.java)
        val encoder = Mockito.mock(PasswordEncoder::class.java)

        contextRunner(repository, encoder)
            .withPropertyValues("talent-introduction.auth.enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(ReadOnlyUserBootstrap::class.java)
            }
        Mockito.verifyNoInteractions(repository, encoder)
    }

    @Test
    fun `creates the account when auth is enabled`() {
        val repository = mock(AdminUserRepository::class.java)
        val encoder = BCryptPasswordEncoder()
        `when`(repository.findByUsername(readOnlyUsername)).thenReturn(null)
        `when`(repository.save(Mockito.any(AdminUser::class.java))).thenAnswer { it.arguments[0] }

        contextRunner(repository, encoder)
            .withPropertyValues("talent-introduction.auth.enabled=true")
            .run { context ->
                assertThat(context).hasSingleBean(ReadOnlyUserBootstrap::class.java)
                context.getBean(ReadOnlyUserBootstrap::class.java).run(null)
            }

        verify(repository, times(1)).save(Mockito.any(AdminUser::class.java))
    }

    private fun contextRunner(
        repository: AdminUserRepository,
        encoder: PasswordEncoder
    ): ApplicationContextRunner = ApplicationContextRunner()
        .withBean(AdminUserRepository::class.java, Supplier { repository })
        .withBean(PasswordEncoder::class.java, Supplier { encoder })
        .withUserConfiguration(ReadOnlyUserBootstrap::class.java)

    private fun assertNotEqualsPlaintext(hash: String) {
        assertTrue(hash.isNotBlank())
        assertFalse(hash == INITIAL_PASSWORD, "hash 不得等于明文")
        assertTrue(hash.startsWith("\$2"), "须为 BCrypt 编码结果：$hash")
    }

    private companion object {
        /** 与实现常量一致的最初口令；测试需要用它断言编码可校验，故仅存在于测试源码。 */
        const val INITIAL_PASSWORD = "qfuser1"
    }
}
