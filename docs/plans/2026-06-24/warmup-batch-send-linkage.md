# 开发计划：预热上限与批量发送联动 + 发送额度提示与校验

> 本文件包含 **两个相互独立、可分别部署/验证** 的计划：
> - **计划 A**：预热/每日上限与批量发送联动（停止原因区分、手动校验、页面预热提示、日志可读化）。
> - **计划 B**：切换「过滤邮箱服务商」后专家数不刷新的修复（独立前端缺陷）。
>
> 两个计划无前后依赖，可各自单独执行与验收。拆分原因见文末「拆分说明」。

---

# 计划 A：预热/每日上限与批量发送联动

## 需求描述

**可观察结果**
- 批量发送（定时 AUTO + 手动 MANUAL）以每个账号「当日有效上限」(`effectiveDailyLimit`，含预热爬坡）作为当日发送上限；当所有账号都达到各自有效上限时，本次执行以「今日额度完成」收尾（次日计数重置后定时任务可自动继续），而不是当作「无可用账号」故障暂停。
- 停止原因被区分并可读化：达到预热上限 / 达到今日发送上限 / 真正无可用账号（故障）/ 其它，体现在任务进度文案与 `task_execution` 审计记录中。
- 手动触发（「开始执行」全量、「手动」单轮）在当前已无剩余发送额度时被拒绝，给出明确提示，不启动执行。
- 批量发送面板展示预热概览：哪些账号已开启预热、开启数量、今日总有效额度、今日已发合计、剩余可发额度；账号明细行展示有效上限与「预热第N天/已达上限」标记。

**不可改变的行为**
- 发送选号的核心判定仍走 `isSendable` / `SenderAccountAssignmentService.selectAccount`，且仍以 `effectiveDailyLimit` 为准（当前已是如此，不得回退为 `dailySendLimit`）。
- I-1~I-9 既有不变量（互斥执行、单流、自检、反重复 `mail_send_attempt` UNIQUE、状态机 IDLE/RUNNING/PAUSED 持久化）保持不变。
- SMTP/连通性故障导致的 `autoSendPaused` 暂停语义与「次日 `resumeDailyLimitPausedAccounts` 仅恢复限额类暂停」的既有逻辑保持不变。
- `ConversationStateService.transition(...)` 等会话状态写入路径不变（本计划不触碰会话状态）。

**超出范围（明确延后）**
- 把预热阶梯从「绝对值」改为「占 `dailySendLimit` 的百分比」——本计划不做，仅保留为观察项。
- 预热起始日自动推进 / 自动错峰排程——不做。
- `dailyCap`（全局批量上限）语义重构——本计划仅在「轮次配额」中额外纳入有效总额度取 `min`，不改其它含义。
- 数据库 schema 变更——本计划**不新增任何 DB/ES 字段**（预热字段已存在于 `mail_sender_account`），因此**无 Flyway 迁移**。

## 关键不变量

### Invariant I-A1: 有效上限是批量发送当日额度的唯一来源
- Rule: 在批量发送外联流程（AUTO + MANUAL）中，任何「账号当日是否还能发、还能发多少、是否达上限」的判定，必须使用 `SenderWarmupService.effectiveDailyLimit(account)`，**禁止**直接使用 `account.dailySendLimit` 做发送资格/配额/暂停判定。
- Applies to:
  - `MailSenderAccountService.isSendable`（已合规，保持）
  - `SenderAccountAssignmentService.selectAccount` / `assignmentScore`（已合规，保持）
  - `ManualInitialOutreachService.runScheduledBatch` 配额耗尽暂停块（**当前违规**，使用 `dailySendLimit`，需改为有效上限判定）
  - 新增的「账号当日状态分类」与「剩余额度」计算
- Violation consequence: 预热账号会在「未达预热上限」时被误判为可发（超发，破坏养号），或在「已达预热上限」时无法被识别为限额达成（错误地走故障暂停路径）。

### Invariant I-A2: 达到上限是「今日完成」而非「故障暂停」
- Rule: 当本次执行无法继续，唯一原因是「所有候选账号都达到各自有效上限（预热或满额）」时，`finalStatus = COMPLETED`（→ 运行态 IDLE），`stopReason ∈ {WARMUP_LIMIT_REACHED, DAILY_LIMIT_REACHED}`。仅当存在「因 `autoSendPaused`（SMTP/连通性故障）而无可用账号」时，才允许 `finalStatus = PAUSED` + `stopReason = NO_AVAILABLE_ACCOUNT`。
- Applies to: `ManualInitialOutreachService.runScheduledBatch`（`sendable.isEmpty()` 分支、配额耗尽分支）、`BatchSendControlService.applyResultToRuntimeStatus`。
- Violation consequence: 预热达上限被当作故障 PAUSE，定时每小时任务次日 `startAuto` 因状态非 IDLE 而 CONFLICT，不能自动续跑，需人工干预。

### Invariant I-A3: 达到上限的账号不得被标记 autoSendPaused
- Rule: 因「达到有效上限」而不可发的账号，**不得**调用 `pauseAutoSend`；其不可发状态仅由 `isSendable`（`todaySentCount >= effectiveDailyLimit`）体现，并在次日 `resetDailyCounts` 计数清零后自然恢复。`autoSendPaused` 语义保留给真正的发送故障（SMTP/连通性/限流升级）。
- Applies to: `ManualInitialOutreachService.runScheduledBatch` 配额耗尽暂停块（当前会对 `todaySentCount >= dailySendLimit` 的账号 `pauseAutoSend`，需移除该按上限暂停的逻辑）。
- Violation consequence: 预热达上限的账号被写入 `autoSendPaused`；若 `resumeDailyLimitPausedAccounts()` 不匹配新原因，次日无法自动恢复，账号被永久挂起。

### Invariant I-A4: 停止原因必须分类且可读、并持久化
- Rule: 停止原因为受控枚举字符串：`WARMUP_LIMIT_REACHED` / `DAILY_LIMIT_REACHED` / `NO_AVAILABLE_ACCOUNT` / `ONE_ROUND_DONE` / `DAILY_CAP_REACHED` / `CANCELLED` / `SYSTEM_ERROR`。每个对外可见的停止都必须：(a) 写入 `TaskProgress.message`（中文可读，如「已达到预热上限，今日暂停发送」）；(b) 通过 `ManualOutreachResult.stopReason` 进入 `task_execution.resultSummary`（既有 `toJson` 路径）。
- Applies to: `ManualInitialOutreachService`（message 文案 + result.stopReason）、`BatchSendControlService.applyResultToRuntimeStatus`（pauseReason 透传）。
- Violation consequence: 运维无法从面板/日志判断今天为何停发。

### Invariant I-A5: 手动触发须先校验剩余额度
- Rule: `startManual`（全量）与 `runManualOnce`（单轮）在启动执行前，必须校验「今日剩余可发额度」`remainingDailyCapacity = Σ over (enabled && 非模拟器 && !autoSendPaused) max(0, effectiveDailyLimit - todaySentCount)`；若 `<= 0`，返回 HTTP 409，**不**调用 `launchExecution`，提示如「今日发送额度已用尽（含预热限制），暂不可手动发送」。
- Applies to: `BatchSendControlService.startManual`、`BatchSendControlService.runManualOnce`。
- Violation consequence: 用户在零额度时仍能启动，执行立即空转结束，体验混乱。

### Invariant I-A6: 预热/额度展示字段统一源自后端计算
- Rule: 面板展示的「有效上限、预热是否生效、是否达上限、剩余额度」必须来自后端（`SenderWarmupService.effectiveDailyLimit` 及派生），通过 `BatchSendStatusView` / `AccountStatRow` 下发；前端**不得**自行用 `dailySendLimit` 或本地推算预热档位。
- Applies to: `AccountStatRow`（新增 `effectiveDailyLimit`、`warmupActive`、`limitReason`）、`BatchSendStatusView`（新增 `warmupAccountCount`、`todayTotalCapacity`、`todayRemainingCapacity`）、前端 `renderBatchSendAccountTable` 与概览渲染。
- Violation consequence: 前后端口径不一致，页面数字与真实发送行为对不上。

## 现状审计

### 存储/数据源 1：`mail_sender_account`（MySQL，Spring Data JDBC）
- Schema 关键字段（已存在，无需迁移）：`daily_send_limit`、`today_sent_count`、`auto_send_paused`、`auto_send_paused_reason`、`warmup_enabled`、`warmup_started_at`、`warmup_steps_json`。
- 写路径：
  1. `MailSenderAccountService.updateAccount/createAccount/setEnabled/resetTodaySentCount` — 账号 CRUD 与计数重置。
  2. `MailSenderAccountService.resetDailyCounts()` — 定时：`resetDailyCountsBeforeDate` 清零 `today_sent_count` + `resumeDailyLimitPausedAccounts()` 仅恢复「限额类」暂停。
  3. `MailSenderAccountService.pauseAutoSend/resumeAutoSend` — 故障暂停/恢复。
  4. `ManualOutreachTxHelper.recordSuccess`（经 txHelper）— 发送成功后 `today_sent_count++`（计数自增）。
  5. `ManualInitialOutreachService.runScheduledBatch` 配额耗尽块 — 对 `today_sent_count >= daily_send_limit` 的账号 `pauseAutoSend("DAILY_LIMIT_EXHAUSTED")`（**本计划要改/移除，见 I-A3**）。
- 读路径：
  1. `SenderWarmupService.effectiveDailyLimit` — 读 `warmup_*`、`daily_send_limit`、`today_sent_count`/`created_at`，算有效上限。
  2. `MailSenderAccountService.isSendable/selectionScore/listSendableAccounts` — 用有效上限判定可发。
  3. `SenderAccountAssignmentService.selectAccount/assignmentScore` — 用有效上限过滤+打分。
  4. `MailSenderAccountController.toResponse` — 已下发 `effectiveDailyLimit` 给账号管理页。
  5. `ManualInitialOutreachService.buildAccountStats` — 组装 `AccountStatRow`（当前只含 `todaySent`/`dailyLimit`，**无有效上限**）。
- 交互点：
  - 写路径 4（成功自增计数）× 读路径 1（有效上限） → 决定账号何时落选，是本计划核心。
  - 写路径 2（次日重置 + 仅恢复限额暂停）× 写路径 5（按上限暂停） → 若按上限 `pauseAutoSend` 用了新原因字符串，次日可能不被恢复（**正是 I-A3 要规避的**）。

### 存储/数据源 2：批量发送运行态与进度（`BatchSendSettingService` 持久化 + `TaskProgressStore` 内存 + `task_execution` 表）
- 运行态：`BatchSendSettingService.getRuntimeStatus/setRuntimeStatus`（IDLE/RUNNING/PAUSED + mode + pauseReason），持久化、跨重启。
- 进度：`TaskProgressStore`（内存 `TaskProgress`，含 `details` map：roundNumber/dailyCap/accounts 等）。
- 审计：`TaskExecutionService.runAndRecordWithResult` 将 `ManualOutreachResult` 经 `toJson` 存入 `task_execution.result_summary`（**已含 `stopReason`/`finalStatus`**）；`status` 由 `TaskExecutionSummaryProvider.taskFinalStatus` 决定。
- 写路径：
  1. `BatchSendControlService.launchExecution/applyResultToRuntimeStatus` — RUNNING→(PAUSED|IDLE)，写 pauseReason。
  2. `ManualInitialOutreachService.updateProgress` — 写 `TaskProgress`（message + details）。
- 读路径：
  1. `BatchSendControlService.getStatus` → `BatchSendStatusView`（前端 `/batch-send/status` 轮询）。
  2. 前端 `renderBatchSendAccountTable`/`applyBatchSendControls` — 渲染账号表与汇总行。
- 交互点：
  - `ManualOutreachResult.stopReason`（写）× `applyResultToRuntimeStatus`（读，映射运行态）× `getStatus`（读，下发 pauseReason）× 前端 banner（读）。新增 stopReason 值必须三处一致映射。

### AUTO 触发链路
- `BatchSendScheduler`（动态 cron，常驻）→ `BatchSendControlService.startAuto`（要求状态 IDLE + autoEnabled）→ `runScheduledBatch(mode=AUTO, oneRoundOnly=false)`。
- 现状缺陷：达预热上限 → `sendable.isEmpty()` → `stopReason=NO_AVAILABLE_ACCOUNT` + `finalStatus=PAUSED` → 运行态 PAUSED → 次日 `startAuto` 命中「状态非 IDLE」CONFLICT，**不自动续跑**。本计划 I-A2 修复此点。

## 实现方案

### 阶段 1：账号当日状态分类 + 剩余额度计算（遵循 I-A1、I-A3、I-A5）
- 在 `SenderWarmupService` 新增纯函数（无副作用，便于单测）：
  - `fun dailyState(account, now=LocalDateTime.now()): AccountDailyState`，返回枚举 `SENDABLE / WARMUP_LIMIT_REACHED / DAILY_LIMIT_REACHED / PAUSED_FAULT / DISABLED_OR_SIMULATOR`。判定顺序：
    1. `!enabled || accountCode == SIMULATOR` → `DISABLED_OR_SIMULATOR`
    2. `autoSendPaused` → `PAUSED_FAULT`
    3. `eff = effectiveDailyLimit(account)`；`todaySentCount >= eff`：若 `eff < dailySendLimit`（预热在约束）→ `WARMUP_LIMIT_REACHED`，否则 → `DAILY_LIMIT_REACHED`
    4. 否则 `SENDABLE`
  - `fun remainingCapacity(account, now): Int = if (dailyState==SENDABLE) max(0, effectiveDailyLimit-todaySentCount) else 0`
- 在 `MailSenderAccountService` 暴露聚合（供控制层/进度用，复用 `listEnabledAccounts`）：
  - `fun remainingDailyCapacity(): Int`（Σ over enabled&非模拟器&非故障 的 `remainingCapacity`）
  - `fun warmupActiveCount(): Int`（`warmupEnabled==true && warmupStartedAt!=null && effectiveDailyLimit<dailySendLimit` 的计数）
  - `fun todayTotalCapacity(): Int`（Σ `effectiveDailyLimit`，over enabled&非模拟器&非故障）
- **文件**：`SenderWarmupService.kt`、`MailSenderAccountService.kt`。

### 阶段 2：批量发送停止原因区分与「今日完成」语义（遵循 I-A1、I-A2、I-A3、I-A4）
- `ManualInitialOutreachService.runScheduledBatch`：
  - **`sendable.isEmpty()` 分支**：不再无条件 `NO_AVAILABLE_ACCOUNT`。对「enabled && 非模拟器」账号用阶段 1 的 `dailyState` 分类：
    - 若存在 `PAUSED_FAULT` 且无任何 SENDABLE → `stopReason=NO_AVAILABLE_ACCOUNT`，`finalStatus=PAUSED`（保持故障语义）。
    - 否则（全部因达上限）→ 若全部/主要为 `WARMUP_LIMIT_REACHED` → `stopReason=WARMUP_LIMIT_REACHED`；否则 `DAILY_LIMIT_REACHED`；二者 `finalStatus=COMPLETED`（→ IDLE）。message 写中文可读文案（I-A4）。
    - 混合（部分预热上限 + 部分满额，无故障）：优先级 `DAILY_LIMIT_REACHED`（更接近「正常满额」），message 注明含预热账号。
  - **配额耗尽块（原 ~200-218 行）**：把 `todaySentCount >= dailySendLimit` 改为依据 `effectiveDailyLimit` 判定是否达上限（I-A1）；**移除对达上限账号的 `pauseAutoSend` 调用**（I-A3），仅保留「计算 roundQuota<=0 时结束」的控制流。`oneRoundOnly` 时按是预热还是满额给 `WARMUP_LIMIT_REACHED`/`DAILY_LIMIT_REACHED`，`finalStatus=COMPLETED`（全量）或 PAUSED（仅单轮按钮，沿用 L3-2 的「单轮后回 PAUSED」）。
  - `updateProgress` 的 `details` 增补 `stopReason` 与中文 `message`。
- `BatchSendControlService.applyResultToRuntimeStatus`：把 `WARMUP_LIMIT_REACHED`/`DAILY_LIMIT_REACHED` 归入 **COMPLETED→IDLE** 分支（而非 PAUSED 分支），并把 stopReason 写入一次「最终进度 message」供前端展示；PAUSED 分支仅保留 `NO_AVAILABLE_ACCOUNT`/`CANCELLED`/`FAILED`。
- **文件**：`ManualInitialOutreachService.kt`、`BatchSendControlService.kt`。

### 阶段 3：手动触发额度校验（遵循 I-A5）
- `BatchSendControlService.startManual` 与 `runManualOnce`：在现有状态校验之后、`launchExecution` 之前，调用 `mailSenderAccountService.remainingDailyCapacity()`；若 `<=0` → `ResponseEntity.status(409).body(mapOf("message" to "今日发送额度已用尽（含预热限制），暂不可手动发送"))`，直接 return。
- （`startAuto` 不强制拒绝：定时链路允许空跑并以 COMPLETED→IDLE 收尾，靠 I-A2 保证次日续跑；但可在 message 标注「今日额度已满」。）
- **文件**：`BatchSendControlService.kt`（与阶段 2 同文件）。

### 阶段 4：状态下发字段扩展（遵循 I-A6）
- `AccountStatRow`（定义于 `ManualInitialOutreachService.kt`）新增：`effectiveDailyLimit: Int`、`warmupActive: Boolean`、`limitReason: String?`（取 `dailyState` 名称，`SENDABLE` 时为 null）。`buildAccountStats` 填充（数据来自 `SenderWarmupService`）。
- `BatchSendStatusView`（定义于 `BatchSendControlService.kt`）新增：`warmupAccountCount: Int`、`todayTotalCapacity: Int`、`todayRemainingCapacity: Int`。`getStatus` 调用阶段 1 的聚合方法填充。
- `BatchSendControlService.extractAccountStats` 的 Map 反序列化分支同步补三个新键（兼容日志恢复路径）。
- **文件**：`ManualInitialOutreachService.kt`、`BatchSendControlService.kt`（均与前阶段同文件，无新增文件）。

### 阶段 5：前端预热提示与账号明细（遵循 I-A6）
- `index.html`：在批量发送进度面板（`#batchSendProgressPanel`/汇总行附近）增加预热概览区块占位（如 `#batchSendWarmupSummary`），账号表 `#batchSendAccountTable` 表头增「有效上限 / 状态」列。
- `app.js`：
  - `renderBatchSendAccountTable`：汇总行追加「预热账号 N · 今日额度 已发/总额度 · 剩余 R」，来自 `statusView.warmupAccountCount/todayTotalCapacity/todayRemainingCapacity`；每行展示 `effectiveDailyLimit` 与 `limitReason`（映射中文：预热第N天达上限 / 已达今日上限 / 故障暂停）。`warmupActive` 时加预热徽标。
  - banner/`applyBatchSendControls`：当 `pauseReason`/最终 message 为预热或今日上限时，显示「已达到预热上限，今日暂停发送」「已达到今日发送上限」。
  - 手动按钮 `handleBatchSendManual`/`handleBatchSendToggle`：捕获 409，用 `showModalToast`/`showStatus` 展示后端 message。
- `styles.css`：预热徽标与状态列的轻量样式（复用既有 `.badge`）。
- **文件**：`index.html`、`app.js`、`styles.css`。

### 阶段 6：测试（遵循全部不变量）
- 新增/扩展 `SenderWarmupServiceTest`：`dailyState` 四态边界（预热第1天发满20→WARMUP_LIMIT_REACHED；满 dailySendLimit→DAILY_LIMIT_REACHED；autoSendPaused→PAUSED_FAULT；未达→SENDABLE）；`remainingCapacity`/聚合求和。
- 新增 `ManualInitialOutreachServiceTest`（或扩展现有）：两账号预热第1天各发20 → 第21次 `sendable` 空 → 断言 `stopReason=WARMUP_LIMIT_REACHED`、`finalStatus=COMPLETED`、message 含「预热上限」；「一个满额 + 一个故障暂停」→ 断言 `NO_AVAILABLE_ACCOUNT`/PAUSED 优先；验证配额耗尽块不再 `pauseAutoSend` 达上限账号。
- 新增 `BatchSendControlServiceTest`：`remainingDailyCapacity()==0` 时 `startManual`/`runManualOnce` 返回 409 且未触发 executor；`applyResultToRuntimeStatus` 对两种上限 stopReason → IDLE。
- **文件**：`SenderWarmupServiceTest.kt`、`ManualInitialOutreachServiceTest.kt`、`BatchSendControlServiceTest.kt`。

## 变更文件清单

| # | 文件 | 变更类型 | 说明 |
|---|------|---------|------|
| 1 | `src/main/kotlin/.../mail/service/SenderWarmupService.kt` | 改 | 新增 `dailyState`/`remainingCapacity` 与 `AccountDailyState` 枚举 |
| 2 | `src/main/kotlin/.../mail/service/MailSenderAccountService.kt` | 改 | 新增 `remainingDailyCapacity`/`warmupActiveCount`/`todayTotalCapacity` 聚合 |
| 3 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | 改 | 停止原因分类、配额块改用有效上限并移除按上限暂停、`AccountStatRow` 扩展、message 文案 |
| 4 | `src/main/kotlin/.../campaign/service/BatchSendControlService.kt` | 改 | 手动额度校验、`applyResultToRuntimeStatus` 映射、`BatchSendStatusView` 扩展、`extractAccountStats` 补键 |
| 5 | `src/main/resources/static/index.html` | 改 | 预热概览占位 + 账号表新列 |
| 6 | `src/main/resources/static/app.js` | 改 | 渲染预热概览/明细、banner 文案、手动 409 提示 |
| 7 | `src/main/resources/static/styles.css` | 改 | 预热徽标/状态列样式 |
| 8 | `src/test/kotlin/.../mail/service/SenderWarmupServiceTest.kt` | 新增/改 | `dailyState`/容量单测 |
| 9 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | 新增/改 | 停止原因/暂停语义单测 |
| 10 | `src/test/kotlin/.../campaign/service/BatchSendControlServiceTest.kt` | 新增/改 | 手动校验/状态映射单测 |

**文件数 = 10（含上限）。无 DB 迁移。子系统数 = 2（后端外联流程 + 前端展示，强耦合于同一状态 DTO，作为一个交付单元）。**

## 验收标准

- **I-A1**：grep `runScheduledBatch` 配额块与所有外联判定，确认无直接用 `dailySendLimit` 做发送资格/配额/暂停；单测断言预热账号在 `todaySentCount < effectiveDailyLimit` 时 SENDABLE、`>=` 时落选。
- **I-A2**：集成场景——两账号预热第1天各发满 → 断言 `result.finalStatus=COMPLETED`、运行态最终 IDLE；模拟「次日重置后」`startAuto` 能再次进入 RUNNING（不 CONFLICT）。
- **I-A3**：单测断言达预热/满额上限的账号在配额耗尽后 `autoSendPaused==false`（无 `pauseAutoSend` 调用）；仅 SMTP 故障路径才置 `autoSendPaused`。
- **I-A4**：断言 `WARMUP_LIMIT_REACHED`/`DAILY_LIMIT_REACHED`/`NO_AVAILABLE_ACCOUNT` 三种停止下 `TaskProgress.message` 为对应中文文案，且 `task_execution.resultSummary` JSON 含对应 `stopReason`。
- **I-A5**：`remainingDailyCapacity()==0` 时 `POST /api/mail/manual-outreach/start` 与 `/batch-send/manual` 返回 409 且 message 含「额度已用尽」，且 executor 未被调用（mock 验证）；`>0` 时正常 202。
- **I-A6**：`/batch-send/status` 响应含 `warmupAccountCount/todayTotalCapacity/todayRemainingCapacity` 且与后端聚合一致；账号行 `effectiveDailyLimit/warmupActive/limitReason` 与 `SenderWarmupService` 计算一致；前端不出现基于 `dailySendLimit` 的预热推算。
- **集成（跨交互点）**：开两个预热账号 → 打开批量面板，概览显示「预热账号 2 · 今日额度 0/40 · 剩余 40」；手动发满 40 后再次点「手动」→ 409 提示「额度已用尽」；面板 banner 显示「已达到预热上限」。

---

# 计划 B：切换「过滤邮箱服务商」后专家数不刷新（独立前端缺陷）

## 需求描述

**可观察结果**：在批量发送配置中切换 `#batchSendEmailDomain`（过滤邮箱服务商）或保存配置后，「将向 X 位专家发送介绍邮件（Y 位未联系）」的专家数随所选服务商即时刷新。

**不可改变的行为**：联系人列表筛选 `#expertEmailDomainFilter`（已正确绑定 `reloadContactsFromStart`）的现有行为不变；批量发送的发送逻辑不变。

**超出范围**：后端 `countPending` 的查询语义不变（默认仍读已保存配置的 `emailDomain`）；不改 ES 过滤逻辑。

## 关键不变量

### Invariant I-B1: 展示的待发送专家数必须反映当前所选服务商
- Rule: 批量发送面板展示的 pending/totalSendable 数，必须对应「当前 `#batchSendEmailDomain` 选中的服务商」。改变该下拉或保存配置后，必须重新获取并刷新 `#taskLaunchDesc`。
- Applies to: `app.js` 中 `MANUAL_INITIAL_OUTREACH.preload`、`saveBatchSendConfig`、`#batchSendEmailDomain` 的 change 绑定。
- Violation consequence: 用户改了服务商，数字仍是旧服务商口径，误导发送决策。

## 现状审计

### 数据源：`GET /api/mail/manual-outreach/pending-count` → `ManualInitialOutreachService.countPending()`
- 读路径：`countPending` 读 `batchSendSettingService.getConfig().emailDomain`（**已保存配置**），用 `notContactedWithEmailFilters(emailDomain)` 查 ES。
- 前端调用点：仅 `taskLaunchConfigs.MANUAL_INITIAL_OUTREACH.preload`（打开任务弹窗时一次性拉取，写入 `#taskLaunchDesc`）。
- 缺陷确认：`#batchSendEmailDomain` 在 `app.js` 中仅出现于「填充选项(1621)、setVal(2799)、读表单(2838)」，**无 change 监听**；`saveBatchSendConfig`（2858）保存后只 `fillBatchSendConfigForm`，**未重新拉取 pending-count**。故切换服务商后专家数不刷新。
- 交互点：下拉值（前端）× 已保存配置 `emailDomain`（后端 countPending 读取）——若仅靠「保存后再 fetch」，需保证 fetch 在 PUT 成功之后。

## 实现方案

### 阶段 1：保存配置后刷新专家数（遵循 I-B1）
- `app.js`：抽出 `refreshOutreachPendingCount()`：`GET /manual-outreach/pending-count` → `summarizeManualOutreachPending` → 更新 `#taskLaunchDesc` 文案与 `runBtn.disabled`（同 preload 口径，复用现有函数）。
- `saveBatchSendConfig` 成功分支（`fillBatchSendConfigForm(saved)` 之后）调用 `refreshOutreachPendingCount()`。

### 阶段 2：切换服务商即时刷新（遵循 I-B1）
- 在批量发送初始化处（`openTaskLaunchModal` 的 `isBatchSend` 分支，约 2268 行附近）为 `#batchSendEmailDomain` 绑定一次 `change`：先 `saveBatchSendConfig()`（持久化所选服务商，确保后端 countPending 读到新值），再 `refreshOutreachPendingCount()`；用标志位避免重复绑定。
  - 备选（若不希望每次切换即落库）：给 `pending-count` 增加可选 query 参数 `emailDomain` 做「预览」，前端 change 时带当前下拉值请求而不落库——此为增强项，列为可选，不在本计划必做范围（保持 B 为纯前端单文件改动）。

### 阶段 3：手工验证
- 打开批量发送弹窗 → 切换服务商 → 断言专家数随之变化；保存配置 → 断言数字刷新；切回「全部」→ 数字恢复总量。

## 变更文件清单

| # | 文件 | 变更类型 | 说明 |
|---|------|---------|------|
| 1 | `src/main/resources/static/app.js` | 改 | `refreshOutreachPendingCount` + 保存后刷新 + 下拉 change 刷新 |

**文件数 = 1。子系统数 = 1（纯前端）。与计划 A 无文件重叠、无依赖。**

## 验收标准

- **I-B1**：切换 `#batchSendEmailDomain` 后 `#taskLaunchDesc` 的专家数在一次请求内更新为该服务商口径；`saveBatchSendConfig` 成功后同样刷新；选「全部」恢复为总量。手工三步验证全部通过。

---

# 拆分说明（create-p 规模检查）

- **计划 A** 聚焦「预热上限 ↔ 批量发送」联动，涉及外联流程与其状态展示，二者通过同一状态 DTO 强耦合，作为一个可独立部署/验证的交付单元（10 文件、2 子系统、0 新 DB 字段、0 迁移）。
- **计划 B** 是与预热完全无关的前端展示缺陷（pending-count 不随服务商刷新），独立成计划，1 文件、可单独验收，避免把无关 UI 修复塞进 A 制造交互面。
- 两计划无前后依赖，可并行或任意顺序执行，各自单独跑 `fix-v` 验证。

## 自检清单
- [x] 关键不变量含每个新字段/状态 ≥1 条（停止原因枚举→I-A4；额度校验→I-A5；展示字段→I-A6；B 的口径→I-B1）
- [x] 现状审计基于 grep/实际代码列出全部读写路径（含 `resetDailyCounts` 与按上限暂停的交互点）
- [x] 无任务引入未被不变量覆盖的写路径（I-A3 显式禁止对达上限账号 `pauseAutoSend`）
- [x] 计划 A 文件数 = 10（含上限）；计划 B 文件数 = 1
- [x] 每个子系统 ≤ 2；A=2，B=1
- [x] 每个任务引用其约束不变量编号
- [x] 验收标准每条不变量 ≥1 项检查
- [x] 文件清单无「等/相关文件」，逐一具名
- [x] 超出范围显式延后（百分比阶梯、自动错峰、dailyCap 重构、DB 迁移、pending-count 预览参数列为可选）
