# 手动批量发送脱离预热限制（仅保留每日硬上限）

> 计划由 create-p 生成。复验请用 fix-v。
> 关联知识：K-operator-send-quota-paths（mail 域）

## 需求描述

**可观察结果**：操作员触发的批量外联（"开始执行"全量、"单轮发送"按钮）不再受发件账号的预热阶梯（warmup ramp）限制，可直接按账号的 `dailySendLimit`（每日硬上限）发送；预热期内的账号在手动执行时不再被压低到阶梯额度。

**不能改变的行为**：
- AUTO（定时调度）批量发送行为完全不变——预热启用时仍按阶梯额度限流。
- `dailySendLimit` 每日硬上限在两种模式下都必须严格生效，手动发送也不得超过。
- 其它发送门控不变：`enabled`、`!autoSendPaused`、排除 `SIMULATOR_NOOP`、SMTP 限流/暂停、批次 `dailyCap`、逐封/逐轮节流。
- 账号选号的负载均衡（国家分布、strategyWeight）算法不变。

**超出范围（显式延后/不做）**：
- 不改批次级 `dailyCap`（`BatchSendConfig`，与预热无关，保留）。
- 不改 `PendingMailOperationService` / `ManualExpertMailService` / `MeetingScheduleService` 等单封人工发送路径（它们已走 `selectAccountForManualSending`，本就不受预热/上限阻塞，见 K-operator-send-quota-paths）。
- 不改 `WarmupProperties` 默认阶梯、不改 warmup 数据库字段。
- 不在本计划引入任何新字段 / 新状态 / 新枚举值。

## 关键不变量

### Invariant I-1: MANUAL 模式忽略预热阶梯
- Rule：当 `ExecutionMode == MANUAL` 时，账号的"有效每日额度"等于 `account.dailySendLimit`，**不**套用 warmup 阶梯（`effectiveDailyLimit` 的 ramp 结果被忽略）。`ExecutionMode == AUTO` 时维持现状（预热启用且在预热期则用阶梯额度）。
- Applies to：`SenderWarmupService.effectiveDailyLimit/dailyState/remainingCapacity/isWarmupActive`；`MailSenderAccountService.listSendableAccounts/isSendable`；`SenderAccountAssignmentService.selectAccount`；`ManualInitialOutreachService.runScheduledBatch`（轮次门控、轮次配额、逐专家选号、stopReason 分类、账号面板）。
- Violation consequence：手动发送被预热阶梯错误阻塞（达不到 dailySendLimit），与需求相反；或 AUTO 被误绕过预热，破坏发件信誉。
- 来源：original

### Invariant I-2: `dailySendLimit` 硬上限始终生效
- Rule：两种模式下，单账号当日发送都必须满足 `todaySentCount < dailySendLimit`。MANUAL 绕过的是 warmup 阶梯，**不是** dailySendLimit。绕过后"有效额度"取 `min(dailySendLimit, …)` 中只剩 dailySendLimit 一项，仍是硬上限。
- Applies to：同 I-1 的所有选号 / 容量 / 门控路径。
- Violation consequence：手动发送突破每日硬上限，可能触发服务商封号。
- 来源：original（用户明确要求"只保留每日硬上限"）

### Invariant I-3: 其它发送门控两模式一致
- Rule：`enabled == true`、`!autoSendPaused`、`accountCode != SIMULATOR_NOOP`、批次 `dailyCap`、SMTP 限流/暂停、逐封/逐轮节流——这些门控不随 `ignoreWarmup` 改变，MANUAL/AUTO 完全一致。
- Applies to：`MailSenderAccountService.isSendable/isManualSendable`、`SenderAccountAssignmentService.selectAccount`、`runScheduledBatch` 配额与节流逻辑。
- Violation consequence：绕过预热时误删其它安全门控，造成向已暂停/已禁用账号发送。
- 来源：original

### Invariant I-4: 忽略预热时不得报 WARMUP_LIMIT_REACHED
- Rule：当 `ignoreWarmup == true` 时，`dailyState` 的"有效额度 < dailySendLimit"分支不可能成立（有效额度恒等于 dailySendLimit），因此 `stopReason`/`limitReason` 不得出现 `WARMUP_LIMIT_REACHED`；容量耗尽时报 `DAILY_LIMIT_REACHED`。
- Applies to：`ManualInitialOutreachService.classifyNoSendableOutcome/classifyLimitReachedOutcome/hasWarmupLimitedAccounts/stopReasonMessage`、`buildAccountStats`。
- Violation consequence：手动发送 UI 出现自相矛盾的"预热上限"提示，误导操作员。
- 来源：original

## 现状审计

### `mail_sender_account`（MySQL，每账号发送配额状态）
- 关键字段：`dailySendLimit`（每日硬上限）、`todaySentCount`、`warmupEnabled`(Boolean?)、`warmupStartedAt`、`warmupStepsJson`、`enabled`、`autoSendPaused`、`createdAt`。
- 额度计算源头 `SenderWarmupService.effectiveDailyLimit`（`SenderWarmupService.kt:25-39`）：
  - `warmupEnabled == false` → 直接返回 `dailySendLimit`（无预热）。
  - `warmupEnabled == true` → `min(dailySendLimit, rampLimit(warmupStartedAt|steps))`。
  - `warmupEnabled == null` 且 `props.enabled` → `min(dailySendLimit, rampLimit(createdAt, props.steps))`，否则 `dailySendLimit`。
- 写路径（todaySentCount，来自 K-operator-send-quota-paths，已 re-grep 确认）：
  1. `ManualExpertMailService.sendManualMail`（OPERATOR）
  2. `MeetingScheduleService.confirmSchedule`（SYSTEM，+1）
  3. `ManualOutreachTxHelper.recordSuccess`→`incrementTodaySentCount`（**本计划相关的批量外联写路径**）
  4. 管理/重置：`updateAccount`/`resetTodaySentCount`/`resetDailyCounts`
  - 本计划**不改任何写路径**，只改"读额度/选号"的判定，故 todaySentCount 语义不变。
- 读路径（额度判定，本计划要改的全集，grep 确认）：
  1. `SenderWarmupService.dailyState` (`:55`) → `effectiveDailyLimit`
  2. `SenderWarmupService.remainingCapacity` (`:70`) → `effectiveDailyLimit`
  3. `SenderWarmupService.isWarmupActive` (`:77`) → `effectiveDailyLimit`
  4. `MailSenderAccountService.isSendable` (`:215`) → `effectiveDailyLimit`（被 `listSendableAccounts`/`selectAccountForSending` 调用）
  5. `MailSenderAccountService.selectionScore`/`remainingDailyCapacity`/`todayTotalCapacity`/`warmupActiveCount`/`effectiveDailyLimitFor` → `effectiveDailyLimit`（仅 AUTO/展示路径，见下）
  6. `SenderAccountAssignmentService.selectAccount` (`:21`) + `assignmentScore` (`:38`) → `effectiveDailyLimit`
  7. `MailSenderAccountController:97/219` 展示 `effectiveDailyLimitFor`（账号管理页，与执行模式无关，**不改**）

### `ManualInitialOutreachService.runScheduledBatch`（批量发送引擎，`mode` 已贯穿）
- `mode` 来源（`BatchSendControlService`）：
  - `startAuto` → `ExecutionMode.AUTO`, oneRoundOnly=false（调度全量）
  - `startManual` → `ExecutionMode.MANUAL`, oneRoundOnly=false（"开始执行"全量）
  - `executeOneRound` → `ExecutionMode.MANUAL`, oneRoundOnly=true（"单轮发送"按钮）
  - ⇒ "手动执行" ≡ `mode == MANUAL`，是正确的判别条件。
- 受预热影响的执行点（需按 I-1 改造）：
  1. 轮次门控 `runRoundGate()` (`:557-564`) → `mailSenderAccountService.listSendableAccounts()` → `isSendable`
  2. 轮次配额 (`:204`) `remainingAccountCapacity = sendable.sumOf { senderWarmupService.remainingCapacity(it) }`
  3. 逐专家选号 (`:252`) `senderAccountAssignmentService.selectAccount(expert, assignments)`
- 受预热影响的报告/展示点（需按 I-4 保持一致）：
  4. `classifyNoSendableOutcome`(`:492`)/`classifyLimitReachedOutcome`(`:500`)/`hasWarmupLimitedAccounts`(`:534`) → `dailyState`
  5. `buildAccountStats`(`:619-621`) → `effectiveDailyLimit`/`isWarmupActive`/`dailyState`（运行面板每账号额度/预热标记/限制原因）

### 交互点
- 写路径 ③（incrementTodaySentCount）× 读判定（选号/容量）：本计划只放宽读侧额度上界（去掉 warmup ramp），写侧不变；`todaySentCount < dailySendLimit` 仍是硬约束 ⇒ 不产生超发。
- `mode` 跨模块传递：`BatchSendControlService` → `runScheduledBatch(mode)` → 需新增参数下传到 `MailSenderAccountService`/`SenderAccountAssignmentService`/`SenderWarmupService`。所有新增参数 `ignoreWarmup` 默认 `false`，保证 AUTO 及账号管理页/单封人工发送等既有调用点零行为变化。

## 实现方案

统一引入布尔开关 `ignoreWarmup`（含义：忽略预热阶梯，仅保留 dailySendLimit）。在 `runScheduledBatch` 内计算 `val ignoreWarmup = mode == ExecutionMode.MANUAL`，逐层下传。所有新参数默认 `false`。

### 阶段 A：额度计算源头加开关（I-1, I-2, I-4）
- **任务 A1**（`SenderWarmupService.kt`）：给 `effectiveDailyLimit` 增加 `ignoreWarmup: Boolean = false` 形参。当 `ignoreWarmup == true` 时**第一行直接 `return account.dailySendLimit`**（跳过所有 warmupEnabled/props 分支），保证 I-2 的硬上限仍是唯一上界。
  - 同步给 `dailyState`、`remainingCapacity`、`isWarmupActive` 增加 `ignoreWarmup: Boolean = false`，内部调用 `effectiveDailyLimit(account, now, ignoreWarmup)`。
  - 校验 I-4：`dailyState` 中 `eff < account.dailySendLimit` 在 ignoreWarmup 时恒为 false ⇒ 自然返回 `DAILY_LIMIT_REACHED`，无需额外分支。
  - 遵守：I-1, I-2, I-4。

### 阶段 B：选号 / 可发送列表加开关（I-1, I-2, I-3）
- **任务 B1**（`MailSenderAccountService.kt`）：
  - `isSendable(account, ignoreWarmup: Boolean = false)`：把 `todaySentCount < warmup.effectiveDailyLimit(account)` 改为 `... effectiveDailyLimit(account, ignoreWarmup = ignoreWarmup)`；其余条件（enabled、!autoSendPaused、非 SIMULATOR）不变（I-3）。
  - `listSendableAccounts(ignoreWarmup: Boolean = false)`：传入 `isSendable`。
  - 不改 `selectAccountForSending`（AUTO）、`isManualSendable`、`remainingDailyCapacity`、`todayTotalCapacity`、`warmupActiveCount`、`effectiveDailyLimitFor`、`selectionScore`（这些是 AUTO / 账号管理展示路径，保持 warmup 语义）。
  - 遵守：I-1, I-2, I-3。
- **任务 B2**（`SenderAccountAssignmentService.kt`）：
  - `selectAccount(expert, currentBatchAssignments = emptyList(), ignoreWarmup: Boolean = false)`：过滤条件 (`:21`) 用 `effectiveDailyLimit(it, ignoreWarmup = ignoreWarmup)`；`assignmentScore` 增加 `ignoreWarmup` 并在 (`:38`) 同步。负载均衡公式其余不变（I-3）。
  - 遵守：I-1, I-2, I-3。

### 阶段 C：批量引擎按模式下传（I-1, I-4）
- **任务 C1**（`ManualInitialOutreachService.kt`）：
  - `runScheduledBatch` 开头：`val ignoreWarmup = mode == ExecutionMode.MANUAL`。
  - 执行点：
    - `runRoundGate()` 增加 `ignoreWarmup` 形参 → 两处 `listSendableAccounts(ignoreWarmup)`。
    - `:204` `remainingAccountCapacity = sendable.sumOf { senderWarmupService.remainingCapacity(it, ignoreWarmup = ignoreWarmup) }`。
    - `:252` `senderAccountAssignmentService.selectAccount(expert, assignments, ignoreWarmup)`。
  - 报告/展示点（I-4 一致性）：
    - `classifyNoSendableOutcome`/`classifyLimitReachedOutcome`/`hasWarmupLimitedAccounts` 增加并下传 `ignoreWarmup` 给 `dailyState`；这样 MANUAL 下不会得到 `WARMUP_LIMIT_REACHED`。
    - `buildAccountStats` 增加并下传 `ignoreWarmup` 给 `effectiveDailyLimit`/`isWarmupActive`/`dailyState`，使运行面板的有效额度/预热标记与实际发送口径一致（MANUAL 显示 dailySendLimit、warmupActive=false）。
  - 遵守：I-1, I-3, I-4。

### 阶段 D：测试
- **任务 D1**：`SenderWarmupServiceTest` 增加用例：预热生效账号在 `ignoreWarmup=true` 下 `effectiveDailyLimit == dailySendLimit`、`dailyState` 在超阶梯未超硬上限时为 `SENDABLE`、超 dailySendLimit 时为 `DAILY_LIMIT_REACHED`（验 I-1/I-2/I-4）。
- **任务 D2**：`MailSenderAccountServiceTest` + `SenderAccountAssignmentServiceTest`：`ignoreWarmup=true` 时预热账号进入可发送列表 / 可被选中，但 `todaySentCount==dailySendLimit` 时仍被排除（验 I-2）；默认 `false` 时行为与现状一致（回归）。
- **任务 D3**：`ManualInitialOutreachServiceTest`：MANUAL 模式下预热期账号可发送至 dailySendLimit；AUTO 模式仍受阶梯限制；MANUAL 容量耗尽 stopReason 为 `DAILY_LIMIT_REACHED` 而非 `WARMUP_LIMIT_REACHED`（验 I-1/I-4）。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `src/main/kotlin/.../mail/service/SenderWarmupService.kt` | 4 方法加 `ignoreWarmup` 参数 |
| 2 | `src/main/kotlin/.../mail/service/MailSenderAccountService.kt` | `isSendable`/`listSendableAccounts` 加参数 |
| 3 | `src/main/kotlin/.../mail/service/SenderAccountAssignmentService.kt` | `selectAccount`/`assignmentScore` 加参数 |
| 4 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | 计算并下传 `ignoreWarmup` 到执行/报告点 |
| 5 | `src/test/kotlin/.../mail/service/SenderWarmupServiceTest.kt` | I-1/I-2/I-4 用例 |
| 6 | `src/test/kotlin/.../mail/service/MailSenderAccountServiceTest.kt` | 可发送列表回归+绕过用例 |
| 7 | `src/test/kotlin/.../mail/service/SenderAccountAssignmentServiceTest.kt` | 选号绕过+硬上限用例 |
| 8 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | MANUAL/AUTO 对比 + stopReason 用例 |

文件数 8 ≤ 10。子系统 2（mail 选号/额度、campaign 批量引擎）≤ 2。无新增字段。

## 验收标准

- **I-1**：单测——`warmupEnabled=true`、阶梯额度 20、`dailySendLimit=100`、`todaySentCount=20` 的账号：`effectiveDailyLimit(ignoreWarmup=true)==100`；`isSendable(ignoreWarmup=true)==true`，`isSendable(ignoreWarmup=false)==false`。AUTO 集成路径仍按阶梯限流。
- **I-2**：上述账号 `todaySentCount=100` 时，`ignoreWarmup=true` 下 `isSendable==false`、`selectAccount` 不选中、`remainingCapacity==0`；手动全量运行总发送数不超过 `Σ dailySendLimit`。
- **I-3**：`autoSendPaused=true` 或 `enabled=false` 或 `SIMULATOR_NOOP` 账号在 `ignoreWarmup=true` 下仍被排除；批次 `dailyCap`、逐封/逐轮节流在 MANUAL 下仍生效。
- **I-4**：MANUAL 运行直到容量耗尽，`ManualOutreachResult.stopReason ∈ {DAILY_LIMIT_REACHED, DAILY_CAP_REACHED}`，绝不为 `WARMUP_LIMIT_REACHED`；运行面板 `limitReason` 不出现 `WARMUP_LIMIT_REACHED`、`warmupActive==false`。
- **回归**：所有默认 `ignoreWarmup=false` 的既有调用点（AUTO 调度、账号管理页 `effectiveDailyLimitFor`、单封人工发送）行为与改动前完全一致；`mvn test` 全绿。

## 复验入口

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=SenderWarmupServiceTest,MailSenderAccountServiceTest,SenderAccountAssignmentServiceTest,ManualInitialOutreachServiceTest
```
