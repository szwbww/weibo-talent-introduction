# 子计划 06：发送速率动态调节 + 事务粒度修正

> 主计划：`2026-06-19-batch-send-robustness-00-master.md`。共享不变量见主计划。

## 需求描述

- 可观察结果（速率调节）：每个发送账号维护独立的发送间隔。当某账号收到 SMTP 421/452 限流响应时，自动将该账号的间隔翻倍（指数退避），直到达到上限。成功发送时逐步恢复到基础间隔。前端进度中展示每个账号的当前间隔。
- 可观察结果（事务修正）：`InitialOutreachService.sendInitialBatch()` 的整方法 `@Transactional` 被移除，改为逐条事务（与 `ManualInitialOutreachService` 一致），消除"SMTP 超时导致已发邮件记录被回滚"的不一致风险。
- 不可改变：全局 `perMailIntervalMs` 配置保留作为基础间隔下限。总量约束（R-6）不受影响。
- 不做：按邮件服务商自动识别最优间隔（后续可通过机器学习优化）。

## 关键不变量（引用 + 专属）

- 引用 R-6（速率调节不突破定量约束）。
- Invariant L6-1：间隔只增不减（相对基础值）。动态间隔 >= `perMailIntervalMs`（全局配置）。退避后的恢复速度慢于升级速度（指数升、线性降）。
- Invariant L6-2：间隔状态非持久化。间隔调节状态存在内存中（`ConcurrentHashMap`），进程重启后从基础值重新开始。这是可接受的——重启后重新探测。
- Invariant L6-3：事务修正不改变 `sendInitialBatch` 的返回值语义。调用方仍收到 `InitialOutreachBatchResult`，包含 sent/skipped/failed 计数。

## 现状审计

### 速率控制
- `perMailIntervalMs` 存储在 `batch_send_setting` 表（默认 1000ms）。
- `ManualInitialOutreachService.runScheduledBatch()` 中（约 L301-303）：
  ```kotlin
  if (config.perMailIntervalMs > 0 && index < roundEnd) {
      try { Thread.sleep(config.perMailIntervalMs) } catch (_: InterruptedException) { ... }
  }
  ```
  所有账号使用相同间隔。
- 限流场景：SMTP 421/452 目前被统一归为异常，触发 failedCount++，但不调整发送节奏。同一账号连续被限流会连续失败，浪费配额且加剧限流。

### 事务
- `InitialOutreachService.sendInitialBatch()`（L30-109）：整方法标注 `@Transactional`。循环内逻辑：
  1. `expertContactRepository.save()` — DB 写入
  2. `mailDeliveryService.send()` — SMTP 网络 IO（超时 10s）
  3. `mailRecordRepository.save()` — DB 写入
  4. `mailSenderAccountRepository.save()` — DB 写入
  
  如果步骤 2 在第 N 封邮件抛异常，前 N-1 封的 DB 记录全部回滚，但邮件已发出。
- `ManualInitialOutreachService` 通过 `txHelper.recordSuccess()` 在独立事务中记录，已正确处理。`InitialOutreachService` 是 legacy 代码但仍在用（`MailAutomationScheduler` 调用）。

## 实现方案

### 部分 A：发送速率动态调节

#### 任务 A1：`AccountRateLimiter`（内存速率管理器）

新文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/AccountRateLimiter.kt`

```kotlin
@Service
class AccountRateLimiter {
    private data class RateState(
        val currentIntervalMs: Long,
        val backoffLevel: Int,          // 0 = 基础, 1 = 2x, 2 = 4x, ...
        val consecutiveSuccesses: Int   // 连续成功次数，用于恢复
    )

    private val states = ConcurrentHashMap<String, RateState>()

    companion object {
        const val MAX_BACKOFF_LEVEL = 5       // 最大 2^5 = 32 倍
        const val RECOVERY_THRESHOLD = 10     // 连续 10 次成功后降一级
        const val MAX_INTERVAL_MS = 60_000L   // 绝对上限 60 秒
    }

    /**
     * 获取当前账号的发送间隔（ms）。
     * 不低于 baseIntervalMs（全局配置，L6-1）。
     */
    fun getIntervalMs(accountCode: String, baseIntervalMs: Long): Long {
        val state = states[accountCode] ?: return baseIntervalMs
        return maxOf(state.currentIntervalMs, baseIntervalMs)
    }

    /**
     * 发送成功后调用：计数连续成功，达到 RECOVERY_THRESHOLD 时降一级。
     */
    fun recordSuccess(accountCode: String, baseIntervalMs: Long) {
        states.compute(accountCode) { _, existing ->
            if (existing == null || existing.backoffLevel == 0) return@compute null  // 已是基础级，移除条目
            val newSuccesses = existing.consecutiveSuccesses + 1
            if (newSuccesses >= RECOVERY_THRESHOLD && existing.backoffLevel > 0) {
                val newLevel = existing.backoffLevel - 1
                val newInterval = if (newLevel == 0) baseIntervalMs
                                  else baseIntervalMs * (1L shl newLevel)
                RateState(minOf(newInterval, MAX_INTERVAL_MS), newLevel, 0)
            } else {
                existing.copy(consecutiveSuccesses = newSuccesses)
            }
        }
    }

    /**
     * 收到限流错误（421/452）后调用：指数退避。
     */
    fun recordThrottled(accountCode: String, baseIntervalMs: Long) {
        states.compute(accountCode) { _, existing ->
            val currentLevel = existing?.backoffLevel ?: 0
            val newLevel = minOf(currentLevel + 1, MAX_BACKOFF_LEVEL)
            val newInterval = baseIntervalMs * (1L shl newLevel)
            RateState(minOf(newInterval, MAX_INTERVAL_MS), newLevel, 0)
        }
    }

    /**
     * 获取当前状态快照（供进度展示）。
     */
    fun getSnapshot(): Map<String, Long> =
        states.mapValues { it.value.currentIntervalMs }

    fun clear() = states.clear()
}
```

#### 任务 A2：编排器集成

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

注入 `AccountRateLimiter`。

**改造发送间隔**（约 L301-303）：

```kotlin
// 原：Thread.sleep(config.perMailIntervalMs)
// 新：
val intervalMs = accountRateLimiter.getIntervalMs(account.accountCode, config.perMailIntervalMs)
if (intervalMs > 0 && index < roundEnd) {
    try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
}
```

**在发送结果处理中触发退避/恢复**（与子计划 02 配合）：

```kotlin
if (delivered.status == "SENT") {
    accountRateLimiter.recordSuccess(account.accountCode, config.perMailIntervalMs)
    // ... 原逻辑
} else {
    when (delivered.errorCategory) {
        SmtpErrorCategory.TRANSIENT -> {
            // 判断是否为限流（421/452）
            val code = delivered.smtpResponseCode
            if (code == 421 || code == 452) {
                accountRateLimiter.recordThrottled(account.accountCode, config.perMailIntervalMs)
                // 不暂停账号（与子计划 02 区分：限流先退避，连续限流再暂停）
                failedCount++
                stat.failed++
                // 不设 midRoundStop，继续发下一个但间隔已拉长
            } else {
                // 其他 4xx：暂停账号（子计划 02 逻辑）
                // ...
            }
        }
        // ... 其他分支不变
    }
}
```

> **决策说明**：421/452 限流不立即暂停账号（与子计划 02 的 TRANSIENT 处理有所区分）。退避后如果连续 N 次仍被限流，`backoffLevel` 达到上限（32 倍间隔 ≈ 32 秒/封），此时效率已极低，编排器可选择跳过该账号转用其他账号（通过 `SenderAccountAssignmentService` 的评分自然降权实现）。

#### 任务 A3：进度中展示间隔

`updateProgress()` 的 `details` 中，`AccountStatRow` 新增 `currentIntervalMs` 字段：

```kotlin
data class AccountStatRow(
    val accountCode: String,
    val todaySent: Int,
    val dailyLimit: Int,
    val success: Int,
    val failed: Int,
    val paused: Boolean,
    val pauseReason: String?,
    val currentIntervalMs: Long? = null  // 新增
)
```

`buildAccountStats()` 中填充：

```kotlin
val rateLimiterSnapshot = accountRateLimiter.getSnapshot()
// ...
AccountStatRow(
    // ... 原有字段
    currentIntervalMs = rateLimiterSnapshot[account.accountCode] ?: config.perMailIntervalMs
)
```

### 部分 B：事务粒度修正

#### 任务 B1：移除 `InitialOutreachService.sendInitialBatch()` 的 `@Transactional`

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt`

1. **移除** 方法级 `@Transactional` 注解（L30）。

2. **每封邮件的 DB 操作包在独立事务中**。引入与 `ManualOutreachTxHelper` 相同的模式：

```kotlin
// 注入 InitialOutreachTxHelper（新建，或复用 ManualOutreachTxHelper 并扩展）
// 每封邮件的处理流程：
//   1. SMTP 发送（事务外）
//   2. 成功 → txHelper.recordInitialOutreachSuccess(...)（事务内）
//   3. 失败 → 记录错误（事务内或直接日志）
```

#### 任务 B2：新建 `InitialOutreachTxHelper`（或扩展 `ManualOutreachTxHelper`）

**方案选择**：复用 `ManualOutreachTxHelper`——其 `recordSuccess` 已有完整的事务内操作（状态转换、mail_record、计数自增、attempt 标记、ES 同步）。`InitialOutreachService` 的成功路径与之高度重合。

但 `InitialOutreachService` 使用的是 `ConversationStatus.INTRO_SENT`（不经过 `ConversationStateService.transition()`，直接在 `ExpertContact` 构造时设置 `currentStatus`）。需要对齐：

```kotlin
// 改造后的 sendInitialBatch 单封处理：
val now = LocalDateTime.now()
val contact = expertContactRepository.save(
    ExpertContact(
        campaignId = campaignId, orcidId = expert.orcidId,
        expertEmail = expert.email.orEmpty(), expertName = expert.displayName,
        currentStatus = "NEW",  // 先存 NEW
        createdAt = now, updatedAt = now
    )
)

val mail = introductionMailComposer.compose(account.accountCode, expert)
val delivered = mailDeliveryService.send(account, mail)  // SMTP 在事务外

if (delivered.status == "SENT") {
    txHelper.recordSuccess(  // 事务内：NEW → INTRO_SENT + mail_record + counter
        contact = contact, accountCode = account.accountCode,
        deliveredMessageId = delivered.messageId, subject = mail.subject,
        body = mail.body, attemptId = 0  // legacy 不使用 attempt
    )
    sentResults += ...
} else {
    // 失败记录（contact 保留 NEW 状态，可重试）
}
```

#### 任务 B3：测试

文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt`（新建或补充）

- 第 3 封邮件 SMTP 异常 → 前 2 封的 `ExpertContact` 和 `MailRecord` 仍然存在（事务隔离验证）。
- 全部成功 → 结果不变（L6-3）。

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/AccountRateLimiterTest.kt`

- 基础间隔 1000ms，`recordThrottled` 后 → 2000ms。
- 连续 `recordThrottled` 5 次 → 32000ms（不超过 MAX_INTERVAL_MS）。
- `recordSuccess` 10 次（RECOVERY_THRESHOLD）→ 降一级。
- `getIntervalMs` 不低于 baseIntervalMs（L6-1）。
- `clear()` 后回到基础值。

## 修正记录

| 日期 | 修正 | 理由 | 参考 |
|------|------|------|------|
| 2026-06-19 | `InitialOutreachBatchResult` 新增 `failed` 字段；`sent` 仅统计 `status == "SENT"` | 复验 P1-1：原 `sent = sentResults.size` 把失败计入 sent，不满足 L6-3 | `docs/plans/fix/2026-06-19-batch-send-robustness-06-dynamic-rate-and-tx-fix/fix-1.md`
