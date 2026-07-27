# 子计划 05：退信监控

> 主计划：`2026-06-19-batch-send-robustness-00-master.md`。共享不变量见主计划。

## 需求描述

- 可观察结果：系统定时通过 IMAP 收集发送账号收件箱中的退信（Bounce / DSN），解析退信类型（硬退信 / 软退信），关联到原始发送的 `ExpertContact`，记录退信统计。当某账号的退信率（硬退信数 / 近 N 天发送数）超过阈值时自动暂停该账号，保护域名发送信誉。前端可在监控页查看退信统计。
- 不可改变：现有 `ImapMailReceiveService` 的业务回复处理逻辑不变。退信识别作为独立模块，不混入自动回复流程。
- 不做：退信后自动发送确认邮件给收件人。退信的 Webhook/实时通知。

## 关键不变量（引用 + 专属）

- 引用 R-5（退信判定准确性）。
- Invariant L5-1：退信 vs 业务回复不冲突。退信处理在业务回复处理**之后**执行（或独立定时任务），共用 IMAP 连接但处理不同邮件。已被业务回复流程处理过的邮件（已读/已移动）不会被退信处理重复处理。
- Invariant L5-2：退信去重。同一封退信只记录一次。通过 `Message-ID` 去重（存入 `bounce_record` 表）。
- Invariant L5-3：退信率计算窗口。退信率 = 近 7 天硬退信数 / 近 7 天该账号发送总数。窗口天数可配。阈值默认 5%。
- Invariant L5-4：退信暂停可恢复。因退信率暂停的账号，`autoSendPausedReason` 标记为 `BOUNCE_RATE_HIGH:<rate>`。操作员可手动恢复（与 SELF_CHECK_FAILED 相同的恢复路径）。每日重置**不**自动解除退信暂停（只解除 `DAILY_LIMIT%`）。

## 现状审计

- `ImapMailReceiveService`：连接 IMAP、拉取 UNSEEN 邮件、解析为 `ReceivedMail`。当前只处理业务回复。
- 退信的典型特征：
  - 发件人：`mailer-daemon@*`、`postmaster@*`、`MAILER-DAEMON`
  - Content-Type：`multipart/report; report-type=delivery-status`（RFC 3464）
  - 主题：包含 `Undelivered`、`Delivery Status Notification`、`Returned mail`、`Mail delivery failed` 等
  - DSN 正文中包含原始 Message-ID（可关联到 `mail_record.message_id`）
- 硬退信（Hard Bounce）：5.x.x 状态码 → 永久失败（地址不存在、域不存在）
- 软退信（Soft Bounce）：4.x.x 状态码 → 暂时失败（邮箱满、服务器暂不可用）
- `mail_record` 表已有 `message_id` 字段，可用于退信关联。

## 实现方案

### 任务 1：数据库迁移 V29 — `bounce_record` 表

文件：`src/main/resources/db/migration/V29__create_bounce_record.sql`

```sql
CREATE TABLE bounce_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_account_code VARCHAR(64) NOT NULL,
    bounce_message_id VARCHAR(255) NOT NULL,
    original_message_id VARCHAR(255),
    original_expert_contact_id BIGINT,
    bounce_type VARCHAR(20) NOT NULL COMMENT 'HARD or SOFT',
    dsn_status VARCHAR(20) COMMENT 'e.g. 5.1.1, 4.2.2',
    bounce_reason VARCHAR(1000),
    received_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_bounce_message_id (bounce_message_id),
    INDEX idx_sender_account (sender_account_code),
    INDEX idx_received_at (received_at),
    INDEX idx_original_contact (original_expert_contact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 任务 2：领域 + 仓储

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/domain/BounceRecord.kt`

```kotlin
@Table("bounce_record")
data class BounceRecord(
    @Id val id: Long? = null,
    val senderAccountCode: String,
    val bounceMessageId: String,
    val originalMessageId: String?,
    val originalExpertContactId: Long?,
    val bounceType: String,  // HARD, SOFT
    val dsnStatus: String?,
    val bounceReason: String?,
    val receivedAt: LocalDateTime,
    val createdAt: LocalDateTime? = null
)
```

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/repository/BounceRecordRepository.kt`

```kotlin
interface BounceRecordRepository : CrudRepository<BounceRecord, Long> {
    fun existsByBounceMessageId(bounceMessageId: String): Boolean

    @Query("""
        SELECT COUNT(*) FROM bounce_record
         WHERE sender_account_code = :accountCode
           AND bounce_type = 'HARD'
           AND received_at >= :since
    """)
    fun countHardBouncesSince(accountCode: String, since: LocalDateTime): Long

    fun findAllBySenderAccountCodeOrderByReceivedAtDesc(accountCode: String): List<BounceRecord>
}
```

### 任务 3：退信解析服务

新文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceDetector.kt`

```kotlin
@Service
class BounceDetector {
    /**
     * 判断一封 IMAP 邮件是否为退信。基于发件人地址和主题模式匹配。
     */
    fun isBounce(from: String?, subject: String?, contentType: String?): Boolean {
        val fromLower = from?.lowercase() ?: return false
        if (fromLower.contains("mailer-daemon") || fromLower.contains("postmaster")) return true
        val subjectLower = subject?.lowercase() ?: ""
        if (subjectLower.contains("undelivered") ||
            subjectLower.contains("delivery status") ||
            subjectLower.contains("returned mail") ||
            subjectLower.contains("mail delivery failed") ||
            subjectLower.contains("undeliverable")) return true
        if (contentType?.contains("report-type=delivery-status") == true) return true
        return false
    }

    /**
     * 从退信正文/DSN 部分提取原始 Message-ID 和 DSN 状态码。
     */
    fun parseBounceDetails(message: javax.mail.Message): BounceDetails {
        val dsnStatus = extractDsnStatus(message)
        val originalMessageId = extractOriginalMessageId(message)
        val bounceType = if (dsnStatus?.startsWith("5") == true) "HARD"
                         else if (dsnStatus?.startsWith("4") == true) "SOFT"
                         else inferBounceTypeFromSubject(message.subject)
        return BounceDetails(
            originalMessageId = originalMessageId,
            dsnStatus = dsnStatus,
            bounceType = bounceType,
            reason = message.subject?.take(500)
        )
    }

    // DSN 状态码提取：遍历 multipart，找 message/delivery-status part，
    // 解析 Status: 字段（如 "Status: 5.1.1"）
    private fun extractDsnStatus(message: javax.mail.Message): String? { /* ... */ }

    // 原始 Message-ID 提取：遍历 multipart，找 message/rfc822 part 或
    // DSN 中的 Original-Message-ID / In-Reply-To 头
    private fun extractOriginalMessageId(message: javax.mail.Message): String? { /* ... */ }

    private fun inferBounceTypeFromSubject(subject: String?): String {
        // 无法确定时默认 SOFT（保守，R-5 准确性原则的延伸）
        return "SOFT"
    }
}

data class BounceDetails(
    val originalMessageId: String?,
    val dsnStatus: String?,
    val bounceType: String,
    val reason: String?
)
```

### 任务 4：退信收集服务

新文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt`

```kotlin
@Service
class BounceCollectionService(
    private val mailReceiveService: ImapMailReceiveService,
    private val bounceDetector: BounceDetector,
    private val bounceRecordRepository: BounceRecordRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val mailSenderAccountService: MailSenderAccountService,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val expertContactRepository: ExpertContactRepository
) {
    private val log = LoggerFactory.getLogger(BounceCollectionService::class.java)

    /**
     * 扫描指定账号的收件箱，识别并记录退信。
     * 在业务回复处理之后调用，只处理 UNSEEN 的退信邮件。
     */
    fun collectBounces(account: MailSenderAccount): BounceCollectionResult {
        var collected = 0
        var skippedDuplicate = 0

        // 使用 IMAP 获取 UNSEEN 邮件（与业务回复处理共用 IMAP 读取，但独立扫描）
        val messages = mailReceiveService.fetchUnseenMessages(account)
        for (message in messages) {
            val from = message.from?.firstOrNull()?.toString()
            val subject = message.subject
            val contentType = message.contentType

            if (!bounceDetector.isBounce(from, subject, contentType)) continue

            val messageId = message.getHeader("Message-ID")?.firstOrNull() ?: continue
            if (bounceRecordRepository.existsByBounceMessageId(messageId)) {
                skippedDuplicate++
                continue
            }

            val details = bounceDetector.parseBounceDetails(message)
            val originalContact = details.originalMessageId?.let { origMsgId ->
                mailRecordRepository.findByMessageId(origMsgId)?.let { mailRecord ->
                    expertContactRepository.findById(mailRecord.expertContactId).orElse(null)
                }
            }

            bounceRecordRepository.save(BounceRecord(
                senderAccountCode = account.accountCode,
                bounceMessageId = messageId,
                originalMessageId = details.originalMessageId,
                originalExpertContactId = originalContact?.id,
                bounceType = details.bounceType,
                dsnStatus = details.dsnStatus,
                bounceReason = details.reason,
                receivedAt = message.receivedDate?.toInstant()
                    ?.atZone(java.time.ZoneId.systemDefault())?.toLocalDateTime()
                    ?: LocalDateTime.now()
            ))

            // 硬退信：标记专家邮箱无效
            if (details.bounceType == "HARD" && originalContact != null) {
                expertIndexWriterService.syncCandidateOperatorStatus(
                    originalContact.orcidId, "EMAIL_INVALID"
                )
            }

            collected++
        }

        return BounceCollectionResult(collected = collected, skippedDuplicate = skippedDuplicate)
    }
}

data class BounceCollectionResult(val collected: Int, val skippedDuplicate: Int)
```

### 任务 5：退信率检查 + 自动暂停

新文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt`

```kotlin
@Service
class BounceRateMonitorService(
    private val bounceRecordRepository: BounceRecordRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val mailSenderAccountService: MailSenderAccountService
) {
    private val log = LoggerFactory.getLogger(BounceRateMonitorService::class.java)

    companion object {
        const val DEFAULT_WINDOW_DAYS = 7
        const val DEFAULT_THRESHOLD = 0.05  // 5%
        const val MIN_SAMPLE_SIZE = 20  // 发送量不足时不触发暂停
    }

    /**
     * 检查指定账号的退信率，超阈值则暂停。
     * 返回当前退信率（-1 表示样本不足）。
     */
    fun checkAndPause(accountCode: String, windowDays: Int = DEFAULT_WINDOW_DAYS,
                      threshold: Double = DEFAULT_THRESHOLD): Double {
        val since = LocalDateTime.now().minusDays(windowDays.toLong())
        val hardBounces = bounceRecordRepository.countHardBouncesSince(accountCode, since)
        val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)

        if (sentCount < MIN_SAMPLE_SIZE) return -1.0

        val rate = hardBounces.toDouble() / sentCount
        if (rate > threshold) {
            log.warn("Bounce rate for {} is {:.2f}% (threshold {:.2f}%), pausing account",
                accountCode, rate * 100, threshold * 100)
            mailSenderAccountService.pauseAutoSend(accountCode,
                "BOUNCE_RATE_HIGH:${String.format("%.2f", rate * 100)}%")
        }
        return rate
    }
}
```

### 任务 6：`MailRecordRepository` 新增统计方法

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt`

```kotlin
@Query("""
    SELECT COUNT(*) FROM mail_record
     WHERE sender_account_code = :accountCode
       AND direction = 'OUTBOUND'
       AND send_status = 'SENT'
       AND sent_at >= :since
""")
fun countSentByAccountSince(accountCode: String, since: LocalDateTime): Long

fun findByMessageId(messageId: String): MailRecord?
```

### 任务 7：定时任务

文件：`src/main/kotlin/com/weibo/talentintroduction/task/service/BounceCollectionScheduler.kt`（新文件）

```kotlin
@Component
@Configuration
class BounceCollectionScheduler(
    private val mailSenderAccountService: MailSenderAccountService,
    private val bounceCollectionService: BounceCollectionService,
    private val bounceRateMonitorService: BounceRateMonitorService,
    private val taskExecutionService: TaskExecutionService
) : SchedulingConfigurer {

    private val log = LoggerFactory.getLogger(BounceCollectionScheduler::class.java)

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        // 每 2 小时收集一次退信
        taskRegistrar.addCronTask(
            Runnable { runCollection() },
            "0 0 */2 * * ?"
        )
    }

    private fun runCollection() {
        try {
            taskExecutionService.runAndRecord("BOUNCE_COLLECTION", "SCHEDULED", "bounce-collection") {
                val accounts = mailSenderAccountService.listAutoReceiveAccounts()
                for (account in accounts) {
                    try {
                        val result = bounceCollectionService.collectBounces(account)
                        if (result.collected > 0) {
                            log.info("Collected {} bounces for account {}", result.collected, account.accountCode)
                        }
                        bounceRateMonitorService.checkAndPause(account.accountCode)
                    } catch (e: Exception) {
                        log.error("Bounce collection failed for account {}", account.accountCode, e)
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Bounce collection task failed", e)
        }
    }
}
```

### 任务 8：监控 API 补充（可选）

文件：`src/main/kotlin/com/weibo/talentintroduction/monitoring/service/MailMonitoringService.kt`

- 新增方法 `getBounceStats(accountCode, windowDays)` → 返回硬退信数、软退信数、退信率。
- 对应 controller endpoint `GET /api/monitoring/bounce-stats?accountCode=&days=7`。

### 任务 9：测试

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceDetectorTest.kt`

- `mailer-daemon@xxx` → isBounce = true。
- 正常专家回复 → isBounce = false（R-5）。
- DSN Content-Type → isBounce = true。
- `parseBounceDetails`：5.1.1 → HARD，4.2.2 → SOFT。

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorServiceTest.kt`

- 20 封发送、2 封硬退信 → 10% > 5% → 暂停（L5-4 reason 格式）。
- 10 封发送（< MIN_SAMPLE_SIZE）→ 不触发暂停。
- 0 封硬退信 → 不暂停。
