# 开发计划：发件邮箱预热爬坡（warmup effective daily limit）

> 用 create-p skill 编写。独立计划。

## 需求描述

- 可观察结果：新发件账号每日**有效发送上限**按账号建立天数自动爬坡（如第 1–2 天 20，逐步翻倍），达到配置目标后等于其 `dailySendLimit`；老账号（建立已久）有效上限即 `dailySendLimit`，不受影响。批量发送与选号在到达「有效上限」时即停止该账号当日发送。
- 必须不变：`mail_sender_account` 既有字段不删改语义；`dailySendLimit` 仍是「目标上限」；每日计数重置逻辑（`resetDailyCounts`）不变；`autoSendPaused` 熔断不变。
- 不做：自动调整 `dailySendLimit` 本身；按账号信誉动态调速（那属节流计划）；前端展示爬坡曲线（可后续）。

## 关键不变量

### Invariant I-1：有效上限 = min(目标, 爬坡值)，单一来源
- 规则：唯一计算入口 `SenderWarmupService.effectiveDailyLimit(account): Int = min(account.dailySendLimit, rampLimit(ageDays))`；`ageDays = DAYS between account.createdAt and now`（createdAt 为空时视为已过预热期，返回 `dailySendLimit`）。所有「当日是否还能发」的判断都改用该入口，禁止再直接比较 `dailySendLimit`。
- 适用于：发送门禁与选号过滤。
- 违反后果：不同代码路径对同一账号给出不同上限，超发或欠发。

### Invariant I-2：爬坡只受配置与账号年龄影响，关闭即恒等
- 规则：`talent-introduction.warmup.enabled=false` 时 `effectiveDailyLimit ≡ dailySendLimit`（恒等，零行为变化）。`rampLimit` 由有序配置档位决定，单调不减，最终档 ≥ 任意 `dailySendLimit` 视为「已满速」。
- 适用于：`SenderWarmupService`。
- 违反后果：关开关后仍改变发送量，难以回滚。

### Invariant I-3：预热不绕过既有熔断
- 规则：有效上限只「收紧」不「放宽」——它与 `todaySentCount < effectiveDailyLimit`、`enabled`、`!autoSendPaused`、非模拟账号等既有条件是「与」关系，不替换它们。
- 适用于：`MailSenderAccountService.isSendable`、`SenderAccountAssignmentService.selectAccount`。
- 违反后果：预热逻辑意外放行被暂停账号。

## 现状审计

### `MailSenderAccount`
- 含 `dailySendLimit`、`todaySentCount`、`createdAt`、`enabled`、`autoSendPaused`。爬坡用 `createdAt` 计算年龄，无需新增字段。

### 当日上限判断（读路径，全部改为用 effectiveDailyLimit — I-1）
1. `mail/service/MailSenderAccountService.kt:isSendable` → `account.todaySentCount < account.dailySendLimit`。
2. `mail/service/MailSenderAccountService.kt:selectionScore` → `remainingRatio` 用 `dailySendLimit`（影响选号权重，建议同步改为有效上限，保持一致）。
3. `mail/service/SenderAccountAssignmentService.kt:selectAccount` 过滤 `it.todaySentCount < it.dailySendLimit`；`assignmentScore` 的 `remainingRatio` 同理。
- 批量循环 `ManualInitialOutreachService` 的每账号 `dailySentTotal`/配额计算依赖上述选号/门禁，改单一入口后自动生效。

### 配置
- 现有 `config/` 多个 `@ConfigurationProperties`。新增 `WarmupProperties`。

## 实现方案

### 任务 1：配置（I-2）
文件：`src/main/kotlin/com/weibo/talentintroduction/config/WarmupProperties.kt`（新增）
```kotlin
@ConfigurationProperties(prefix = "talent-introduction.warmup")
data class WarmupProperties(
    val enabled: Boolean = false,
    // 第 N 天(含)起的每日上限档位，按 dayFrom 升序；最后一档为满速前的最高值
    val steps: List<WarmupStep> = listOf(
        WarmupStep(1, 20), WarmupStep(3, 40), WarmupStep(5, 80),
        WarmupStep(8, 160), WarmupStep(12, 320)
    )
)
data class WarmupStep(val dayFrom: Int, val limit: Int)
```
文件：`src/main/resources/application.yml`
```yaml
  warmup:
    enabled: ${WARMUP_ENABLED:false}
```
（steps 用默认值，可按需用 env/外部配置覆盖；登记方式对齐既有 properties 类。）

### 任务 2：预热服务（I-1, I-2）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderWarmupService.kt`（新增）
```kotlin
@Service
class SenderWarmupService(private val props: WarmupProperties) {
    fun effectiveDailyLimit(account: MailSenderAccount, now: LocalDateTime = LocalDateTime.now()): Int {
        if (!props.enabled) return account.dailySendLimit                      // I-2 恒等
        val created = account.createdAt ?: return account.dailySendLimit        // I-1 空视为已预热
        val ageDays = Duration.between(created, now).toDays().toInt() + 1       // 第1天 = ageDays 1
        val ramp = props.steps.filter { it.dayFrom <= ageDays }.maxOfOrNull { it.limit }
            ?: props.steps.minOf { it.limit }                                   // 早于首档→最低档
        return minOf(account.dailySendLimit, ramp)                             // I-1
    }
}
```

### 任务 3：门禁与选号改用有效上限（I-1, I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt`
- 注入 `SenderWarmupService`。`isSendable`：`account.todaySentCount < warmup.effectiveDailyLimit(account)`，其余条件与逻辑（`enabled && !autoSendPaused && != SIMULATOR`）不变（I-3）。`selectionScore` 的 `remainingRatio` 分母改为有效上限。

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentService.kt`
- 注入 `SenderWarmupService`。`selectAccount` 过滤与 `assignmentScore` 的 `remainingRatio` 改用 `effectiveDailyLimit(account)`（I-1）。

### 任务 4：测试
文件：`src/test/kotlin/.../SenderWarmupServiceTest.kt`
- enabled=false → 恒等 `dailySendLimit`（I-2）。
- 第 1 天账号 → 返回最低档（如 20）；第 10 天 → 较高档；档位 ≥ dailySendLimit 时取 dailySendLimit（I-1）。
- createdAt=null → dailySendLimit。
文件：`src/test/kotlin/.../MailSenderAccountServiceTest.kt`（补充）
- todaySentCount 达到有效上限（< dailySendLimit）时 `isSendable=false`（I-1）；被 `autoSendPaused` 的账号仍不可发（I-3）。

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `config/WarmupProperties.kt` | 新增 |
| 2 | `src/main/resources/application.yml` | 修改 |
| 3 | `mail/service/SenderWarmupService.kt` | 新增 |
| 4 | `mail/service/MailSenderAccountService.kt` | 修改 |
| 5 | `mail/service/SenderAccountAssignmentService.kt` | 修改 |
| 6 | `test/.../SenderWarmupServiceTest.kt` | 新增 |
| 7 | `test/.../MailSenderAccountServiceTest.kt` | 新增/修改 |

文件数 = 7 ≤ 10。子系统：预热计算（含配置）+ 门禁/选号接线 = 2。

## 验收标准
- I-1：单一入口；门禁与选号在有效上限处停发。
- I-2：开关关闭时全链路行为与现状逐字节一致（恒等）。
- I-3：预热不放宽 enabled/paused/simulator 等既有限制。
- 集成：新建账号（createdAt=今天）在发满有效上限后 `selectAccount` 不再选它，但次日（或档位提升）上限增大。
