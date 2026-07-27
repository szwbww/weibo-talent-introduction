# 批量发送改造：长期定时异步 + 账号自检/限额门禁 + 定量轮次 + 控制台（主计划）

> 本主计划是「定时批量发送」改造的总纲。因改造范围超过单计划上限（≤10 文件 / ≤2 子系统 / 每个共享存储 ≤1 新字段），按 create-p 规范拆分为 4 个**可独立部署、独立验收**的顺序子计划。本文件承载**共享的现状审计与关键不变量**，各子计划按编号引用，不重复抄写。

## 需求描述

可观察结果（用户视角）：
- 「批量发送介绍邮件」从「点一次发一批」改造为**长期定时异步流程**：每天按配置的时间（如每天 00:00）自动触发一次，按「每日上限 / 每轮数量 / 每封间隔 / 每轮间隔」分轮发送。
- 弹出框可配置定时与定量参数；除「开始执行」外新增「暂停」「手动」按钮；手动 = 立即执行一次按配置定量的发送，且**仅在暂停状态下可点**。
- 每轮发送前对每个邮箱做**轻量真实投递自检 + 每日上限检查**，不通过则**暂停该邮箱**（不影响其他邮箱）。
- 没有任何可用邮箱时**暂停整个批量发送流程**，并在列表页给出提示，**刷新页面后提示仍在**。
- 执行记录细到每个邮箱：今日已发 / 上限 / 总数 / 成功 / 失败。
- 明确区分当前是「自动定时执行」还是「手动执行」。

不可改变（必须保留）：
- 现有防重语义：ES `operatorStatus=CONTACTED`、`mail_send_attempt` 的 `UNIQUE(orcid_id, mail_type)`、发送前 `hasSentIntroduction` 双检（见 I-7）。
- 模拟账号 `SIMULATOR_NOOP` 永不参与真实发送。
- 状态机仍通过 `ConversationStateService.transition(...)` 流转，不直接改 `currentStatus`。
- 单实例部署假设：进程内 `@Scheduled` + 内存并发锁（`TaskProgressStore`）即可，不引入分布式锁。
- 已废弃的即时外联代码（`InitialOutreachService.sendInitialBatch`、`/api/mail/initial-outreach*`、`MailAutomationScheduler.scheduleInitialOutreach`）**保留不动**，本次不触碰。

不做（明确推迟）：
- 多套定时计划 / 多活动并存（本次只做单一全局配置）。
- IMAP 收件校验型自检（本次自检只到 SMTP 接受，见 I-4）。
- SPF/DKIM/DMARC、投递评分、退信解析等 DNS/邮件信誉体系改造。
- 分布式部署、跨实例调度协调。
- 删除/重构 legacy 即时外联与其前端入口。

## 设计决策（已与需求方确认）

| 决策点 | 选定方案 |
|---|---|
| 自检方式 | **SMTP 自发探针**：向账号自身 `senderEmail` 发一封探针邮件，SMTP 返回 250（接受）即通过；不校验 IMAP 收件。 |
| 自检频率 | **TTL 缓存**，默认 30 分钟；同一账号 TTL 内不重复探针；TTL 可配置。 |
| 暂停账号表示 | **新增账号级字段** `auto_send_paused` + 原因 + 时间，与人工 `enabled` 解耦，可自动/手动恢复。 |
| 与旧代码关系 | **演进** `MANUAL_INITIAL_OUTREACH` 为定时异步流程；legacy 即时外联保留不动。 |
| 定时配置粒度 | **单一全局配置**：一个每日 cron + 一组定量参数。 |
| 部署形态 | **单实例 Tomcat**，进程内 `@Scheduled`/`SchedulingConfigurer` + 内存并发锁。 |

## 关键不变量（全特性共享，子计划按编号引用）

### Invariant I-1：单一活动流，自动与手动互斥
- 规则：定时批量发送沿用唯一 `taskType = "MANUAL_INITIAL_OUTREACH"`。任意时刻最多一个执行实例（沿用 `TaskProgressStore.tryStartWithToken` 抢占）。自动（定时）与手动（操作员）共用同一编排器与 taskType，**绝不并发**。
- 适用于：调度器触发路径、`/manual-outreach/start`、新增的 `/pause` `/manual` 控制端点。
- 违反后果：两次发送并发 → 重复发信、账号计数与防重竞态。

### Invariant I-2：执行模式可辨识（AUTO vs MANUAL）
- 规则：每次运行带明确模式。`AUTO` = 定时触发（`TaskExecution.triggerType="SCHEDULED"`）；`MANUAL` = 操作员触发（`triggerType="MANUAL"`）。模式必须写入 `TaskProgress.details["executionMode"]`，供前端展示。手动运行**仅当流程处于 PAUSED** 时允许（见 I-9）。
- 适用于：调度器、控制服务、控制端点、前端状态展示。
- 违反后果：无法区分自动/手动；手动在自动运行中被触发导致 I-1 违反。

### Invariant I-3：账号「可发送」判定唯一口径
- 规则：账号可发送当且仅当 `enabled=true` AND `auto_send_paused=false` AND `today_sent_count < daily_send_limit` AND `account_code != SIMULATOR_NOOP`。`auto_send_paused` 与人工 `enabled` **语义解耦**：`enabled=false` 是人工停用；`auto_send_paused=true` 是系统自动暂停（自检失败或限额耗尽时设置）。所有账号选择读路径必须使用该统一谓词。
- 适用于：`SenderAccountAssignmentService.selectAccount`、`MailSenderAccountService.selectAccountForSending`、编排器轮前门禁。
- 违反后果：向不可用账号发信 / 绕过暂停 / 超出每日限额。

### Invariant I-4：自检语义（SMTP 自发探针 + TTL）
- 规则：自检 = 用账号 SMTP 向其自身 `senderEmail` 发一封探针邮件；SMTP 接受（250，无异常）= 通过；抛异常 = 失败。**不**做 IMAP 收件校验。自检结果按账号缓存，TTL 内（默认 30 分钟，可配）直接复用，不重复探针。自检失败 → 将该账号 `auto_send_paused=true`（原因 `SELF_CHECK_FAILED:<msg>`）。探针邮件不计入业务发送统计、不写 `mail_record`。
- 适用于：`SenderAccountSelfCheckService`、编排器轮前门禁。
- 违反后果：探针风暴触发风控 / 误判账号可用。

### Invariant I-5：无可用账号 → 暂停整个流程（可持久查询）
- 规则：轮开始时若不存在任何「可发送」账号（I-3），流程**转入 PAUSED**（而非 COMPLETED/FAILED），持久化暂停状态与原因 `NO_AVAILABLE_ACCOUNT`。该状态必须可被独立查询端点读取，使前端列表页提示在**刷新后依旧存在**。
- 适用于：编排器、控制服务（持久状态）、`/batch-send/status` 端点、前端 banner。
- 违反后果：无账号时静默结束 / 刷新后提示丢失。

### Invariant I-6：定量与限额双重约束
- 规则：单日自动运行总发送量不超过配置 `dailyCap`；每轮发送量不超过 `roundSize`；轮内每封间隔 `perMailIntervalMs`、轮间间隔 `perRoundIntervalMs`。同时**绝不**突破任一账号的 `daily_send_limit`（由 I-3 谓词 + 原子自增 `incrementTodaySentCount` 共同保证）。手动一次执行的额度 = 一次「配置定量操作」（默认一轮 `roundSize`；具体在子计划 03 定义）。
- 适用于：编排器轮循环、控制服务手动入口。
- 违反后果：超量发送 / 账号被风控。

### Invariant I-7：防重语义保持不变
- 规则：发送成功后写 ES `operatorStatus=CONTACTED`、`mail_send_attempt` 走 `UNIQUE(orcid_id,mail_type)` upsert、发送前 `hasSentIntroduction(contactId)` 双检——三道防重**全部保留**，签名与行为不变。重试快照（NEW 且无 SENT 记录的联系人）逻辑不变。
- 适用于：编排器（演进自 `ManualInitialOutreachService`）、`ManualOutreachTxHelper`。
- 违反后果：同一专家重复发信。

### Invariant I-8：进度明细包含每账号统计
- 规则：每次 `TaskProgress` 更新的 `details` 必须含：流程级 `executionMode`、`status`、`roundNumber`、`dailyCap`、`dailySentTotal`、`sentTotal`、`failedTotal`；以及账号级数组，每项含 `accountCode`、`todaySent`、`dailyLimit`、`success`、`failed`、`paused`、`pauseReason`。
- 适用于：编排器进度更新、前端进度区渲染。
- 违反后果：需求 5/7 不可见。

### Invariant I-9：流程状态机（IDLE / RUNNING / PAUSED）
- 规则：批量发送流程持久状态机：`IDLE`（未运行）→ `RUNNING`（自动或手动运行中）→ `PAUSED`（操作员暂停 或 无可用账号自动暂停）。转换：开始/定时触发 IDLE→RUNNING；暂停按钮 RUNNING→PAUSED；无账号 RUNNING→PAUSED（I-5）；手动按钮仅 PAUSED→（临时 RUNNING 跑一轮）→PAUSED；恢复 PAUSED→（等待下次定时 / 立即继续，子计划 03 定义）。状态持久化于 `batch_send_setting`，供刷新后恢复。
- 适用于：控制服务、调度器、控制端点、前端。
- 违反后果：手动在运行中被触发（违反 I-1/I-2）、刷新丢状态。

## 现状审计（共享）

### 存储：MySQL `mail_sender_account`（共享，多读路径）
- Schema（`mail/domain/MailSenderAccount.kt` + V1）：`enabled`、`daily_send_limit`、`today_sent_count`、`last_sent_at`、`strategy_weight`、SMTP/IMAP 凭证等。**无**自动暂停字段，**无**每日计数自动清零任务（仅 `MailSenderAccountService.resetTodaySentCount` 手动重置）。
- 写路径：
  1. `MailSenderAccountService.{createAccount,updateAccount,setEnabled,resetTodaySentCount}` — 管理操作。
  2. `MailSenderAccountRepository.incrementTodaySentCount(accountCode, sentAt)` — 发送成功后 +1（`ManualOutreachTxHelper.recordSuccess`）。
  3. `InitialOutreachService` / `ManualExpertMailService` — `account.copy(todaySentCount+1, lastSentAt=now)` 保存（legacy/即时单发路径）。
- 读路径：
  1. `SenderAccountAssignmentService.selectAccount` — 过滤 `enabled && todaySentCount<limit && !=SIMULATOR`，按分布/权重选号。
  2. `MailSenderAccountService.selectAccountForSending` — 同口径选号（单发）。
  3. `MailSenderAccountService.listAccounts / listEnabledAccounts / listAutoReceiveAccounts`。
  4. `MailMonitoringService.senderAccountHealth` — 读 `enabled/todaySentCount/dailySendLimit`。
  5. `MailSenderAccountController` — 列表/详情响应。
- 交互点：子计划 02 新增 `auto_send_paused` → 写路径新增「自检失败/限额暂停/恢复」；读路径 1、2 必须并入 I-3 谓词；读路径 3、4、5 需透出暂停状态供 UI。

### 存储：MySQL（settings 模式）
- 现状：`eligibility_filter_setting`（V26）为 key-value 配置表，配套读取服务（`/api/experts/eligibility-filters`）。本次新建 `batch_send_setting` 沿用同一模式（子计划 01）。

### 编排/进度/审计基础设施
- `ManualInitialOutreachService`（`campaign/service`）：`countPending()` 预览；`runBulkOutreach(executionId)` 单遍快照顺序发送，`send-interval-ms` 节流，无「轮」概念，无 dailyCap，账号耗尽抛 `NoAvailableSenderAccountException` → 整批以 COMPLETED + `NO_CAPACITY` 结束。**这是演进目标**（子计划 03）。
- `ManualOutreachTxHelper.recordSuccess/recordFailure`：成功原子事务（状态流转 + mail_record + 账号自增 + attempt + ES 同步）。**保留**。
- `TaskProgressStore`（`task/service`）：内存 `ConcurrentHashMap` + `task_progress_log`（V22）持久化；`tryStartWithToken/bindExecutionId/update/requestCancel/isCancelled/clearExecutionContext`；`restoreFromLog` 在重启后把 RUNNING→INTERRUPTED。`TaskProgress.details: Map<String,Any>?` 已可承载每账号明细（I-8）。
- `TaskExecutionService.runAndRecordWithResult(taskType, triggerType, request, onStarted){...}`：写 `task_execution` 审计行，`triggerType` 即 I-2 的模式来源（"SCHEDULED"/"MANUAL"）。
- `MailAutomationController`：`/manual-outreach/pending-count`、`/manual-outreach/start`（提交到 `manualOutreachExecutor` 单线程池，`tryStartWithToken` 抢占，`taskExecutionService.runAndRecordWithResult` 包裹 `runBulkOutreach`）。**演进目标**（子计划 03）。
- `ManualOutreachConfig.manualOutreachExecutor`：core=max=1、queue=0 的单线程池。沿用。
- `MailAutomationScheduler`（gated by `talent-introduction.scheduling.enabled`）：含 legacy `scheduleInitialOutreach`（保留不动）。新调度器独立新建（子计划 03）。
- 自检可复用件：`MailAccountConnectivityService`（仅 SMTP `testConnection` + IMAP，不真发）；`SmtpMailDeliveryService.send`（真发，构造 `JavaMailSenderImpl`）。自检需「真发到自身」，故新建 `SenderAccountSelfCheckService`，复用 `JavaMailSenderImpl` 构造方式（子计划 02）。
- 前端任务弹框（`static/app.js`）：`taskLaunchConfigs`（CONFIG 模式，`preload`/`showKeyword`/`showFilters`/`run`）+ `openTaskModal`（PROGRESS 模式轮询 `details`）。`MANUAL_INITIAL_OUTREACH` 已注册，`executeManualOutreach` 调 `/manual-outreach/start`。**演进目标**（子计划 04）。

### 交互点汇总（潜在 P1）
1. `auto_send_paused`（写：子计划 02 自检/限额/恢复）× 选号读路径（`SenderAccountAssignmentService`、`MailSenderAccountService.selectAccountForSending`）—— 必须并入 I-3。
2. `batch_send_setting`（写：配置 API + 运行时状态机持久化）× 调度器/编排器/`/status` 端点/前端 banner（I-5/I-9）。
3. 编排器轮前自检（子计划 03 调用子计划 02 的 `SenderAccountSelfCheckService`）× 账号 `auto_send_paused` 写入 —— 跨子计划接口，需在 02 定稳签名。
4. 进度 `details`（写：编排器 I-8）× 前端进度区渲染（子计划 04）—— 字段契约需在 03 定稳。

## 拆分与顺序（每个子计划独立验收）

| 序 | 子计划 | 文件 | 子系统 | 新字段/存储 | 依赖 |
|---|---|---|---|---|---|
| 01 | 批量发送配置持久化（`batch_send_setting` + 服务 + API） | ~5 | 配置 | 新表 `batch_send_setting` | 无 |
| 02 | 账号自动暂停字段 + 真实投递自检服务 + 选号谓词 | ~7 | 账号就绪 | `mail_sender_account` +auto_send_paused 字段组 | 01（读 TTL 配置） |
| 03 | 轮次编排引擎 + 调度器 + 控制（开始/暂停/手动/状态/模式） | ~7 | 编排 | 复用 01 表存运行时状态 | 01、02 |
| 04 | 前端：弹框配置/三按钮/模式与每账号统计 + 列表页暂停 banner | ~3 | 前端 | 无 | 01、02、03 |

- 后续子计划可依赖前序，反之不可。
- 文件清单见各子计划文件：
  - `2026-06-18-scheduled-batch-send-01-config-persistence.md`
  - `2026-06-18-scheduled-batch-send-02-account-readiness.md`
  - `2026-06-18-scheduled-batch-send-03-orchestration-engine.md`
  - `2026-06-18-scheduled-batch-send-04-frontend.md`

## 迁移版本规划
- V27：`batch_send_setting`（子计划 01）。
- V28：`mail_sender_account` 增列（子计划 02）。
- 编排/前端子计划不含迁移。
- 规则：每次 schema 变更新增 `V<n>__*.sql`，绝不改已应用迁移（CLAUDE.md）。

## 验收标准（端到端，跨交互点）
- I-1/I-2：定时触发与手动触发不并发；`task_execution.triggerType` 与 `details.executionMode` 一致；手动在 RUNNING 时被拒。
- I-3：人工 `enabled=false`、自动 `auto_send_paused=true`、`today>=limit` 三类账号均不被选号；解耦可分别置位与查询。
- I-4：自检为对自身真发，250 通过、异常暂停账号；TTL 内不重复探针；探针不写 `mail_record`。
- I-5/I-9：无可用账号 → 流程 PAUSED + `NO_AVAILABLE_ACCOUNT`；刷新页面后列表页 banner 仍在（来自 `/batch-send/status`）。
- I-6：单日总量 ≤ dailyCap；每轮 ≤ roundSize；间隔生效；任一账号不超 `daily_send_limit`。
- I-7：重复运行不向已 `CONTACTED`/已 SENT 的专家再次发信。
- I-8：进度 `details` 含每账号 today/limit/success/failed/paused。

## 自检清单（create-p Phase 4）
- [x] 关键不变量：每个新字段/状态均有 ≥1 不变量（auto_send_paused→I-3/I-4；流程状态→I-5/I-9；模式→I-2；配置→I-6）。
- [x] 现状审计：列出 `mail_sender_account` 全部写/读路径（经 grep 验证，非记忆）。
- [x] 无新增写路径未被不变量覆盖。
- [x] 每个子计划文件数 ≤10；子系统 ≤2。
- [x] 每个子计划任务按编号引用不变量。
- [x] 验收按不变量逐条。
- [x] 文件清单无「相关文件/等」。
- [x] 明确推迟项已在「不做」列出。
