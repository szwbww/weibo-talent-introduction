# 子计划 04：每日计数自动重置

> 主计划：`2026-06-19-batch-send-robustness-00-master.md`。共享不变量见主计划。

## 需求描述

- 可观察结果：每天自动重置所有发送账号的 `todaySentCount` 为 0，同时解除因 `DAILY_LIMIT_EXHAUSTED` 暂停的账号。操作员无需手动逐个点击重置。重置时间与批量发送 cron 协调，保证重置在当天首次发送之前完成。
- 不可改变：手动 `resetTodaySentCount` API 保留。`todaySentCount` 的自增逻辑（`incrementTodaySentCount`）不变。
- 不做：按时区自适应重置（当前单实例部署，使用服务器本地时区）。

## 关键不变量（引用 + 专属）

- 引用 R-4（每日重置的原子性）。
- Invariant L4-1：重置幂等。同一天内多次触发重置（如调度延迟导致两次触发），不会产生副作用。通过 `last_sent_at` 日期判断：仅重置 `last_sent_at` 日期 < 当天的账号。
- Invariant L4-2：不中断运行中的批次。如果重置触发时正有批次在运行，重置仍执行（SQL 原子操作），运行中批次后续的 `incrementTodaySentCount` 会在新的零基础上自增——这是正确行为（新的一天，新的配额）。
- Invariant L4-3：重置范围。重置所有 `enabled=true` 的账号（包含 `autoSendPaused=true` 的），不重置 `enabled=false` 的。因 `DAILY_LIMIT_EXHAUSTED` 暂停的账号同时解除暂停。

## 现状审计

- `todaySentCount` 存储在 `mail_sender_account.today_sent_count`（INT NOT NULL DEFAULT 0）。
- `lastSentAt` 存储在 `mail_sender_account.last_sent_at`（DATETIME NULL）。
- 自增：`MailSenderAccountRepository.incrementTodaySentCount(accountCode, sentAt)` — 原子 SQL `UPDATE SET today_sent_count = today_sent_count + 1, last_sent_at = :sentAt`。
- 手动重置：`MailSenderAccountService.resetTodaySentCount(accountCode)` — 单账号 `copy(todaySentCount = 0)` 后 save。
- 无自动按天重置机制。
- `autoSendPausedReason` 中，因限额暂停的原因会包含 `DAILY_LIMIT_EXHAUSTED` 字样（由编排器在未来可能设置，当前只有 `SELF_CHECK_FAILED` 和 `NO_AVAILABLE_ACCOUNT`）。

## 实现方案

### 任务 1：Repository 新增批量重置方法

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailSenderAccountRepository.kt`

```kotlin
/**
 * 重置所有 enabled 且 last_sent_at 在今天之前的账号的 todaySentCount。
 * L4-1 幂等：只重置 last_sent_at < :todayStart 的行，当天已重置过的不受影响。
 */
@Modifying
@Query("""
    UPDATE mail_sender_account
       SET today_sent_count = 0
     WHERE enabled = 1
       AND last_sent_at IS NOT NULL
       AND last_sent_at < :todayStart
""")
fun resetDailyCountsBeforeDate(todayStart: LocalDateTime): Int

/**
 * 解除因每日限额耗尽而暂停的账号（L4-3）。
 * 只解除 reason 以 DAILY_LIMIT 开头的暂停，不影响 SELF_CHECK_FAILED 等其他原因的暂停。
 */
@Modifying
@Query("""
    UPDATE mail_sender_account
       SET auto_send_paused = 0,
           auto_send_paused_reason = NULL,
           auto_send_paused_at = NULL
     WHERE enabled = 1
       AND auto_send_paused = 1
       AND auto_send_paused_reason LIKE 'DAILY_LIMIT%'
""")
fun resumeDailyLimitPausedAccounts(): Int
```

### 任务 2：`MailSenderAccountService` 新增重置方法

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt`

```kotlin
/**
 * 每日重置：清零 todaySentCount + 解除限额暂停。
 * 由定时任务调用。
 */
fun resetDailyCounts(): DailyResetResult {
    val todayStart = LocalDate.now().atStartOfDay()
    val countReset = repository.resetDailyCountsBeforeDate(todayStart)
    val pauseResumed = repository.resumeDailyLimitPausedAccounts()
    // 清除 self-check 缓存（新的一天，让 self-check 重新验证）
    // 不在此处清除——self-check 有自己的 TTL 机制
    return DailyResetResult(countReset = countReset, pauseResumed = pauseResumed)
}

data class DailyResetResult(val countReset: Int, val pauseResumed: Int)
```

### 任务 3：定时任务注册

文件：`src/main/kotlin/com/weibo/talentintroduction/task/service/DailyCountResetScheduler.kt`（新文件）

```kotlin
@Component
@Configuration
class DailyCountResetScheduler(
    private val mailSenderAccountService: MailSenderAccountService,
    private val taskExecutionService: TaskExecutionService
) : SchedulingConfigurer {

    private val log = LoggerFactory.getLogger(DailyCountResetScheduler::class.java)

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        // 每天 00:00 执行（固定 cron，不依赖 DB 配置）
        // 使用比批量发送 cron（默认也是 00:00）稍早的时间点，或者用同一时间——
        // resetDailyCounts 是幂等的（L4-1），且 SQL 原子操作不会与批量发送冲突（L4-2）。
        taskRegistrar.addCronTask(
            Runnable { runReset() },
            "0 0 0 * * ?"   // 每天 00:00
        )
    }

    private fun runReset() {
        try {
            taskExecutionService.runAndRecord("DAILY_COUNT_RESET", "SCHEDULED", "daily-count-reset") {
                val result = mailSenderAccountService.resetDailyCounts()
                log.info("Daily count reset complete: {} counts reset, {} pauses resumed",
                    result.countReset, result.pauseResumed)
            }
        } catch (e: Exception) {
            log.error("Daily count reset failed", e)
        }
    }
}
```

### 任务 4：编排器中标记限额暂停原因

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

在 `runScheduledBatch()` 的 quota 计算处（约 L167-179），当 `dailyCapRemaining <= 0` 时，对所有已满的账号执行：

```kotlin
if (roundQuota <= 0) {
    // 标记所有已满的账号为 DAILY_LIMIT 暂停（供 L4-3 次日自动解除）
    val sendable = mailSenderAccountService.listSendableAccounts()
    for (account in sendable) {
        if (account.todaySentCount >= account.dailySendLimit) {
            mailSenderAccountService.pauseAutoSend(account.accountCode, "DAILY_LIMIT_EXHAUSTED")
        }
    }
    // ... 原有逻辑不变
}
```

### 任务 5：测试

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt`（补充）

- `resetDailyCounts()`：`last_sent_at` 为昨天的账号被重置，今天的不被重置（L4-1）。
- `last_sent_at=NULL` 的账号不被重置（从未发过）。
- `enabled=false` 的账号不被重置。
- `autoSendPausedReason=DAILY_LIMIT_EXHAUSTED` 的账号被解除暂停（L4-3）。
- `autoSendPausedReason=SELF_CHECK_FAILED` 的账号**不**被解除暂停。

文件：`src/test/kotlin/com/weibo/talentintroduction/task/service/DailyCountResetSchedulerTest.kt`

- scheduler 正常触发 → `runAndRecord` 被调用。
