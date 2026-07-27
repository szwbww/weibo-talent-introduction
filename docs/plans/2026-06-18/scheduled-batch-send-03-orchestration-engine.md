# 子计划 03：轮次编排引擎 + 调度器 + 控制（开始/暂停/手动/状态/模式）

> 主计划：`2026-06-18-scheduled-batch-send-00-master.md`。依赖子计划 01（配置/运行时状态）、02（自检与可发送账号）。

## 需求描述
- 可观察结果：
  - `MANUAL_INITIAL_OUTREACH` 演进为**轮次化定时异步流程**：按 `roundSize` 分轮、轮内 `perMailIntervalMs`、轮间 `perRoundIntervalMs`，单日不超 `dailyCap`。
  - **每轮开始前**对候选账号做自检（子计划 02）+ 每日上限检查；不通过的账号本轮跳过（已被自检置 `auto_send_paused`，由 I-3 谓词自然排除）。
  - **无任何可用账号** → 流程转 PAUSED + `NO_AVAILABLE_ACCOUNT`（I-5），持久化、可查询。
  - 新增**调度器**：按配置 cron 每日触发一次「自动」运行（受 `autoEnabled` 控制）。
  - 新增**控制端点**：开始 / 暂停 / 手动（仅 PAUSED 可手动）/ 状态查询；运行带明确模式 AUTO/MANUAL（I-2）。
  - 进度明细细到每账号（I-8）。
- 不可改变：防重三道（I-7）、`ManualOutreachTxHelper`、`TaskProgressStore`/`TaskExecutionService` 既有契约、legacy 即时外联。
- 不做：前端（子计划 04）。

## 关键不变量（引用 + 专属）
- 引用 I-1（单流互斥）、I-2（模式）、I-3（账号可发送）、I-4（自检）、I-5（无账号暂停）、I-6（定量/限额）、I-7（防重）、I-8（明细）、I-9（状态机）。
- Invariant L3-1：轮前门禁顺序固定。
  - 规则：每轮开始时，先取「可发送账号」候选→对其中**未在 TTL 缓存**的账号触发自检（I-4）→重算可发送集合（I-3）→若为空则 PAUSED（I-5）→否则发本轮（≤roundSize 且 ≤dailyCap 剩余）。
  - 适用于：编排器轮循环。
  - 违反后果：向失效账号发信 / 无账号时空跑。
- Invariant L3-2：dailyCap 按「自然日 + 自动运行」计量。
  - 规则：`dailyCap` 限制单个自然日内本流程**累计**发送量（跨当天多轮、跨自动+手动累加）。计量以当日 `mail_record`（OUTBOUND/INTRODUCTION/SENT、triggeredBy in MANUAL）或运行时累计器为准（实现取其一并在验收固定）。达到 dailyCap → 本日不再发，流程置 `IDLE`/`PAUSED`（在验收固定为：自动运行达上限置 IDLE 等待次日；手动达上限则该次返回并保持 PAUSED）。
  - 适用于：编排器、控制服务手动入口。
  - 违反后果：超过每日额度。
- Invariant L3-3：状态持久化与重启恢复一致。
  - 规则：流程状态（I-9）写入 `batch_send_setting` 运行时键（子计划 01 `setRuntimeStatus`）。`/batch-send/status` 端点优先读运行时键；进程重启后状态从表恢复（RUNNING 视为 INTERRUPTED 并归一为 PAUSED，原因 `INTERRUPTED`，避免“假运行”）。
  - 适用于：控制服务、status 端点。
  - 违反后果：刷新/重启后状态错乱（违反 I-5 持久要求）。

## 现状审计（专属）
- `ManualInitialOutreachService.runBulkOutreach(executionId)`：单遍 `buildSnapshot`（重试 NEW + ES 滚动新候选）→ 顺序发送，`properties.sendIntervalMs` 节流，账号耗尽抛 `NoAvailableSenderAccountException`→COMPLETED+NO_CAPACITY。`updateProgress(...)` 写 `details=mapOf(pending,sent,failed)`（**需扩展为每账号 I-8**）。防重三道在此（I-7，保留）。
- `MailAutomationController.startManualOutreach`：`tryStartWithToken` 抢占→`manualOutreachExecutor`（单线程）→`runAndRecordWithResult("MANUAL_INITIAL_OUTREACH","MANUAL",...)`→`runBulkOutreach`。**演进**：triggerType 区分 SCHEDULED/MANUAL（I-2）；新增 pause/manual/status。
- `manualOutreachExecutor`：core=max=1,queue=0 → 自动与手动天然不能并发入池（被拒），配合 `tryStartWithToken` 双保险（I-1）。
- `MailAutomationScheduler`：gated `talent-introduction.scheduling.enabled`，含 legacy `scheduleInitialOutreach`（**不动**）。新调度器独立类，cron 来自 DB 配置（动态）→ 用 `SchedulingConfigurer + CronTrigger` 每次读 `BatchSendSettingService.getConfig().cron`。
- `TaskProgress.details: Map<String,Any>?`、`batchPassed/batchRejected/batchNumber` 字段已可承载轮次与每账号明细。
- `ManualOutreachResult: TaskExecutionSummaryProvider`（`taskSuccessCount/taskFailureCount/taskFinalStatus`）——演进后仍返回该类型以兼容 `runAndRecordWithResult`。

## 实现方案

### 任务 1：编排器轮次化（遵循 I-3/I-4/I-6/I-7/I-8、L3-1/L3-2）
文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
- 新增 `runScheduledBatch(executionId, mode: ExecutionMode, oneRoundOnly: Boolean): ManualOutreachResult`：
  - 读 `BatchSendSettingService.getConfig()` 取 roundSize/intervals/dailyCap/selfCheckTtl。
  - 复用现有 `buildSnapshot` 取目标（防重逻辑不变 I-7）。
  - 轮循环：
    1. 取消检查（沿用 `progressStore.isCancelled`）。
    2. **轮前门禁**（L3-1）：`mailSenderAccountService.listSendableAccounts()`（I-3，子计划 02）→ 对未缓存账号 `selfCheckService.checkSendable`（I-4）→ 重新取 sendable。
    3. sendable 为空 → 调 `batchSendControlService.pause("NO_AVAILABLE_ACCOUNT")`（I-5）并 break。
    4. 计算本轮配额 = `min(roundSize, dailyCap剩余, snapshot剩余)`；为 0（达 dailyCap）→ 按 L3-2 结束。
    5. 发本轮：每封走原有 `selectAccount`→compose→`mailDeliveryService.send`→`txHelper.recordSuccess/recordFailure`（I-7 不变）；每封后 `Thread.sleep(perMailIntervalMs)`。
    6. 每轮结束写进度（I-8 每账号明细）。`oneRoundOnly=true`（手动）→发完一轮即返回。
    7. 轮间 `Thread.sleep(perRoundIntervalMs)`。
- 保留旧 `runBulkOutreach` 作为薄封装委托 `runScheduledBatch(mode=MANUAL, oneRoundOnly=false)`（保持 `/manual-outreach/start` 行为或被控制服务取代——见任务 3）。
- 进度明细：新增私有 `buildAccountStats()` 聚合当日每账号 `todaySent/dailyLimit/success/failed/paused/pauseReason`，放入 `details`（I-8）。
- 新增 `enum class ExecutionMode { AUTO, MANUAL }`（同文件或 control 文件）。

### 任务 2：控制服务（状态机 I-9 / 模式 I-2 / 互斥 I-1 / 持久 L3-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`
- 依赖：`TaskProgressStore`、`TaskExecutionService`、`ManualInitialOutreachService`、`BatchSendSettingService`、`manualOutreachExecutor`。
- `startAuto()`：仅当 runtimeStatus∈{IDLE} 且 `autoEnabled` 真；抢占→异步跑 `runScheduledBatch(AUTO, oneRoundOnly=false)`；setRuntimeStatus(RUNNING, AUTO)。
- `startManual()`（操作员「开始执行」）：等同 startAuto 但 mode=MANUAL、triggerType=MANUAL。
- `pause(reason)`：RUNNING→PAUSED；请求取消当前执行（`progressStore.requestCancel`）+ `setRuntimeStatus(PAUSED, mode, reason)`。供编排器（I-5）与操作员暂停按钮共用。
- `runManualOnce()`（「手动」按钮）：**仅当 runtimeStatus==PAUSED**（I-9/I-2），否则抛 409；抢占→异步 `runScheduledBatch(MANUAL, oneRoundOnly=true)`；跑完回到 PAUSED。
- `getStatus(): BatchSendStatusView`：读 `BatchSendSettingService.getRuntimeStatus()` + 最新 `TaskProgress`（含 details I-8）合并。
- 重启恢复（L3-3）：读运行时键，若为 RUNNING（实际无活动执行）→归一 PAUSED+`INTERRUPTED`。
- 所有状态变更经 `setRuntimeStatus` 持久化（L3-3）。

### 任务 3：控制器演进 + 新端点（I-1/I-2/I-5/I-9）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt`
- `POST /manual-outreach/start` → 委托 `batchSendControlService.startManual()`（保留兼容前端旧入口，演进为受控）。
- 新增 `POST /batch-send/pause` → `control.pause("OPERATOR")`。
- 新增 `POST /batch-send/manual` → `control.runManualOnce()`（PAUSED 前置，非 PAUSED 返回 409 + 提示）。
- 新增 `GET /batch-send/status` → `control.getStatus()`（供前端 + 列表页 banner，I-5 刷新保留）。
- `POST /batch-send/start-auto` 可选（手动开启自动循环），或复用 start。
- 调度器与控制端点的并发：均经 `tryStartWithToken`（I-1）。

### 任务 4：调度器（cron 动态、gated、AUTO 模式 I-2）
文件：`src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt`
- `@Configuration` 实现 `SchedulingConfigurer`；注册 `CronTrigger`，`nextExecution` 时读 `BatchSendSettingService.getConfig().cron`。
- 触发回调：若 `config.autoEnabled` 真 → `batchSendControlService.startAuto()`（包裹 `taskExecutionService` 由 control 内部完成，triggerType=SCHEDULED）。
- gate：bean 始终存在，但触发体内判 `autoEnabled`；cron 非法时 `getConfig()` 已回退默认（L1-1），调度器不崩。
- 不与 legacy `MailAutomationScheduler` 混用（独立类）。

### 任务 5：DTO/状态视图
文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`（同文件内）
- `data class BatchSendStatusView(status, mode, pauseReason, roundNumber, dailyCap, dailySentTotal, sentTotal, failedTotal, accounts: List<AccountStatRow>, executionId, message)`
- `data class AccountStatRow(accountCode, todaySent, dailyLimit, success, failed, paused, pauseReason)`

## 变更文件清单（6）
| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 改（轮次化 runScheduledBatch + 每账号明细） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 新增（状态机/控制/DTO） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt` | 改（pause/manual/status 端点 + start 委托） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt` | 新增（动态 cron 调度） |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 改（轮次/门禁/明细/无账号暂停） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt` | 改（新端点 + 模式 + 409） |

说明：`BatchSendSettingService.setRuntimeStatus/getRuntimeStatus` 已在子计划 01 提供，本计划仅调用，不再改 01 文件。

## 验收标准
- I-1：自动运行中调 `/batch-send/manual` 或再次 start → 被拒（409/冲突），无并发执行。
- I-2：AUTO 运行 `task_execution.triggerType=SCHEDULED` 且 `details.executionMode=AUTO`；手动则 MANUAL。
- L3-1：轮前对未缓存账号触发自检；自检失败账号本轮不被选中。
- I-5/L3-3：构造「全部账号不可发送」→ 运行后 `getStatus().status=PAUSED`、`pauseReason=NO_AVAILABLE_ACCOUNT`；重启后 status 端点仍返回 PAUSED（从 `batch_send_setting` 恢复，RUNNING→PAUSED/INTERRUPTED）。
- I-6/L3-2：dailyCap=100、roundSize=30 → 至多发 100，分 4 轮（30/30/30/10）；任一账号不超 `daily_send_limit`；间隔生效（计时断言或注入可控 sleep）。
- I-7：对已 CONTACTED/已 SENT 专家不再发（沿用现有用例断言）。
- I-8：`getStatus().accounts` 每项含 todaySent/dailyLimit/success/failed/paused。
- I-9：start→RUNNING；pause→PAUSED；manual 仅 PAUSED 可触发且跑后回 PAUSED。

## 自检清单
- [x] 新状态（IDLE/RUNNING/PAUSED）、模式（AUTO/MANUAL）均有不变量（I-9/I-2/L3-3）。
- [x] 文件数 6 ≤10；子系统：编排（含调度器同属编排控制）——单一主子系统 + 控制器边界。
- [x] 每任务引用不变量编号。
- [x] 防重写路径保持不变并由 I-7 覆盖；无新增未受不变量覆盖的写路径。
- [x] 每不变量有验收。
- [x] 文件清单无「等」。
