package com.weibo.talentintroduction.auth.service

import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.repository.AdminUserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 首启补建内置只读账号 `user1`（I-1/I-3）。
 *
 * 与 [AdminUserBootstrap] 同款装配与幂等语义：只在账号不存在时 insert，已有记录
 * （密码/首改密码标志/时间/授权）一律不重置；唯一键并发冲突后重查，存在才视为
 * 已由其它实例创建，否则原样抛出原异常。
 *
 * 只写 `admin_user` 一行：不写 `expert_mail_read_access`（无授权即看不到任何正文，
 * 由 admin 单独授权），也不新增字段/迁移。密码由现有 [PasswordEncoder] 现场编码，
 * 日志只记录账号名，不记录初始密码或 hash。
 */
@Component
@ConditionalOnProperty("talent-introduction.auth.enabled", havingValue = "true", matchIfMissing = true)
class ReadOnlyUserBootstrap(
    private val adminUserRepository: AdminUserRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(ReadOnlyUserBootstrap::class.java)

    override fun run(args: ApplicationArguments?) {
        val existing = adminUserRepository.findByUsername(MailReadOnlyPolicy.READER_USERNAME)
        if (existing != null) {
            logger.info("Read-only user already exists.")
            return
        }
        try {
            val now = LocalDateTime.now()
            adminUserRepository.save(
                AdminUser(
                    username = MailReadOnlyPolicy.READER_USERNAME,
                    passwordHash = passwordEncoder.encode(INITIAL_PASSWORD),
                    mustChangePassword = false,
                    lastLoginAt = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
            logger.info("Default read-only user created successfully.")
        } catch (e: Exception) {
            val doubleCheck = adminUserRepository.findByUsername(MailReadOnlyPolicy.READER_USERNAME)
            if (doubleCheck != null) {
                logger.info("Read-only user already exists (inserted concurrently).")
            } else {
                throw e
            }
        }
    }

    companion object {
        /**
         * 仅用于首次建号的初始口令。运行期改密不受支持（user1 无改密权限），因此本常量
         * 只在账号不存在时被编码一次；绝不打印。
         */
        private const val INITIAL_PASSWORD = "qfuser1"
    }
}
