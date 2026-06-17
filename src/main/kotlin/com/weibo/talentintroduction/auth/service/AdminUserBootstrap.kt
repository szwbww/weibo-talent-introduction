package com.weibo.talentintroduction.auth.service

import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.repository.AdminUserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@ConditionalOnProperty("talent-introduction.auth.enabled", havingValue = "true", matchIfMissing = true)
class AdminUserBootstrap(
    private val adminUserRepository: AdminUserRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(AdminUserBootstrap::class.java)

    override fun run(args: ApplicationArguments?) {
        val existing = adminUserRepository.findByUsername("admin")
        if (existing == null) {
            try {
                val admin = AdminUser(
                    username = "admin",
                    passwordHash = passwordEncoder.encode("admin"),
                    mustChangePassword = true,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
                adminUserRepository.save(admin)
                logger.info("Default admin user created successfully.")
            } catch (e: Exception) {
                val doubleCheck = adminUserRepository.findByUsername("admin")
                if (doubleCheck != null) {
                    logger.info("Admin user already exists (inserted concurrently).")
                } else {
                    throw e
                }
            }
        } else {
            logger.info("Admin user already exists.")
        }
    }
}
