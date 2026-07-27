# 修复计划：批量发送 cron 变更后旧触发未取消（重复发送）

## 需求描述

**可观察结果**：在管理界面修改 `batchSend.cron`（定时发送时间点）后，系统只会在新 cron 对应的时间点触发批量发送；不会再出现按"旧时间点"残留触发的额外执行。

**线上现象（根因证据）**：6/24 21:29 将 cron 从默认 `0 0 0 * * ?`（0 点）改为 `0 0 8 * * ?`（8 点）后，6/25 仍在 `00:00:11` 触发了一次实际发送（sent=40/failed=4），随后 `08:00:00` 才是新 cron 的正常触发。`00:00` 这一发就是"改 cron 时旧的已排程触发未被取消"。

**根因**：`BatchSendScheduler` 通过 `SchedulingConfigurer.configureTasks(...)` 在启动时一次性注册 `addTriggerTask(...)`。Spring 的 `ReschedulingRunnable` 持有"下一次触发时间"，且**只在每次执行结束后**才调用 `DynamicCronTrigger.nextExecutionTime(...)` 重算。因此：
- 启动时（或上一次执行后）按当时 cron 排好的那一发，已经"在途"；
- 运行期通过 `BatchSendSettingService.updateConfig(...)` 改 cron，**不会取消/重排**这一发；
- 于是旧 cron 的那一发仍会按旧时间点触发，之后才会切换到新 cron。

**必须不变的行为（NOT change）**：
- `DynamicCronTrigger` 每次评估都从 DB 读取最新 cron 的行为保留。
- `autoEnabled=false` 时不实际发送的逻辑保留（仍在 trigger body 内判断）。
- 互斥（I-1 原始设计）：同一时刻只有一个发送执行，仍由 `TaskProgressStore.tryStartWithToken` + 单线程 `manualOutreachExecutor` 保证。
- `startAuto()` 要求 `runtimeStatus==IDLE` 的前置校验保留。
- 修改 cron 以外的字段（dailyCap / roundSize / 间隔 / autoEnabled 等）时的现有行为保留。
- `MailAutomationScheduler`、`DailyCountResetScheduler`、`ExpertDiscoveryScheduler` 等其它调度器不受影响。

**Out of scope（显式延后）**：
- 12:15:08 那次 `SCHEDULED` 启动的来源定位（需 `batch_send_setting` cron 历史、全量 access log、实例数据；属运维排查，不在本代码修复内）。本计划只保证"改 cron / 重启不再遗留旧触发"。
- ES `_count` 返回 403 的健壮性处理（启动预检失败 → 任务 FAILED → PAUSED）。属独立问题，另开计划。
- 批量发送的多实例分布式锁（ShedLock 等）。当前为单实例部署假设，不在本计划。
- 任何 UI / 前端改动。

## 关键不变量

### Invariant I-1: 单一在途触发（single pending fire）
- Rule: 任意时刻，BatchSend 调度最多只有一个"在途的下一次触发"，且其触发时间必须由**当前 DB 中的 cron** 计算得出。任何由已被取代（superseded）的旧 cron 值排出的触发，都不允许再被执行。
- Applies to: `BatchSendScheduler`（自持 `ScheduledFuture` 的注册/重排路径）、`BatchSendSettingService.updateConfig`（写 cron 的唯一路径，必须触发重排）。
- Violation consequence: 旧时间点多触发一发批量邮件（线上 6/25 00:00 即为此）。

### Invariant I-2: 重排不打断在途执行
- Rule: 因 cron 变更而取消并重排调度时，**不得**取消或中断一个正在执行中的发送。取消只针对"尚未触发的下一发"。
- Applies to: `BatchSendScheduler` 的重排逻辑（必须使用 `future.cancel(mayInterruptIfRunning=false)`）。
- Violation consequence: 正在发送的批次被打断，邮件发送状态不一致。

### Invariant I-3: 非 cron 变更不重排
- Rule: 一次 `updateConfig` 若未改变 cron 字符串值（与持久化前的旧值逐字符相等），则**不得**重排调度，避免无意义地把"下一发"推后/前移，造成跳发或重发。
- Applies to: `BatchSendSettingService.updateConfig`（仅在 cron 实际变化时发布事件）。
- Violation consequence: 改其它字段时意外平移下一次触发时间，间接造成漏发或多发。

### Invariant I-4: autoEnabled 不影响注册存活
- Rule: 调度任务的注册/重排逻辑**不得**依赖 `autoEnabled`。`autoEnabled` 仅在 trigger body（`triggerBatchSend`）内决定是否实际发送。即 `autoEnabled=false` 时调度仍然存活、仍在按 cron 评估，只是不发。
- Applies to: `BatchSendScheduler.triggerBatchSend`（保留现有 autoEnabled 判断）、重排逻辑（不读 autoEnabled）。
- Violation consequence: 关闭再开启自动发送后，调度丢失，再也不触发。

## 现状审计

### `batch_send_setting` 表（cron 等配置 KV）
- Schema/存储：键值表，Spring Data JDBC，`BatchSendSetting(@Id id, settingKey, settingValue, updatedAt)`。cron 存于 `settingKey="batchSend.cron"`。
- **Write paths（cron）**：
  1. `BatchSendSettingService.updateConfig(cmd)` → `upsert(KEY_CRON, cmd.cron)` —— **唯一**写 cron 的入口。由 `PUT /api/mail/batch-send/config`（`BatchSendConfigController.updateConfig`）调用。写前 `validate()` 已 `CronExpression.parse(cmd.cron)` 校验。
  2. （非 cron）`setAutoEnabled`、`setRuntimeStatus` 写其它键，不碰 cron。
  3. 种子/迁移：Flyway 迁移可能 seed 默认 cron（不在运行期，无需重排）。
- **Read paths（cron）**：
  1. `BatchSendSettingService.getConfig().cron` —— 被 `DynamicCronTrigger.nextExecutionTime`（每次评估）、`BatchSendScheduler.triggerBatchSend`（日志）、`ManualInitialOutreachService.runScheduledBatch`、`BatchSendControlService` 读取。
  2. `cronValue(...)`：读取时若解析失败回退默认 `0 0 0 * * ?`。

### BatchSend 调度注册
- 现状：`BatchSendScheduler : SchedulingConfigurer`，`configureTasks` 内 `taskRegistrar.addTriggerTask(Runnable{triggerBatchSend()}, DynamicCronTrigger(settingService))`。注册发生**一次**（应用启动）。
- `@EnableScheduling` 在 `TalentIntroductionApplication`。无自定义 `TaskScheduler` Bean → Spring 使用默认单线程 `ThreadPoolTaskScheduler`，同时服务于 `DailyCountResetScheduler`、`MailAutomationScheduler` 等。
- 关键缺陷：`addTriggerTask` 注册后，"下一次触发时间"由框架的 `ReschedulingRunnable` 持有，仅在**执行完成后**重算。运行期改 cron 无任何 cancel/reschedule 钩子。→ 违反 I-1。

### 交互点（Interaction points）
- **IP-1**：写路径 `updateConfig`（改 cron）× 调度注册（持有旧"在途触发"）。当前两者无联动 → 本计划要建立联动（事件驱动重排）。这是唯一新增交互点。
- 受影响读路径：`DynamicCronTrigger.nextExecutionTime` 仍照常每次读最新 cron，无需改动；重排只是强制它"立刻"基于新 cron + 当前时刻重算一次，而非等到下次执行后。

### 测试现状
- `BatchSendSettingServiceTest` 以 `BatchSendSettingService(repository)` 构造（单参）。新增 `ApplicationEventPublisher` 构造参数会破坏它 → 必须同步更新该测试。
- `BatchSendControlServiceTest` 不构造 `BatchSendSettingService` 真身（使用 mock），预计不受影响（执行时确认）。
- 无 `BatchSendSchedulerTest` 现存 → 新增。

## 实现方案

采用**事件驱动重排**，避免 `BatchSendSettingService` ↔ `BatchSendScheduler` 循环依赖：写 cron 时发布领域事件，调度器监听后取消旧 future、按新 cron 重排。

### Task 1 — 定义 cron 变更事件（I-1）
- 新增 `BatchSendCronChangedEvent`（`campaign/event` 或 `task/event` 包），携带 `newCron: String`、`oldCron: String`。纯数据类，无逻辑。
- 文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/event/BatchSendCronChangedEvent.kt`（新增）。

### Task 2 — 写 cron 时发布事件，且仅在 cron 实际变化时发布（I-1, I-3）
- 修改 `BatchSendSettingService`：
  - 构造注入 `org.springframework.context.ApplicationEventPublisher`。
  - `updateConfig(cmd)`：在 `upsert(KEY_CRON, cmd.cron)` 之前读取旧 cron（`getConfig().cron` 或 `loadAll()[KEY_CRON]`）；写入后，**仅当** `newCron != oldCron` 时 `publisher.publishEvent(BatchSendCronChangedEvent(oldCron, cmd.cron))`。
  - `setAutoEnabled`、`setRuntimeStatus` 不发事件（I-3/I-4）。
- 文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt`（修改）。
- 对应读路径：事件被 Task 3 的监听器消费；DB cron 已经是新值，监听器重排时 `DynamicCronTrigger` 读到的即新值。

### Task 3 — 调度器自持 future 并支持重排（I-1, I-2, I-4）
- 重构 `BatchSendScheduler`：
  - 保留 `SchedulingConfigurer`，但**不再**用 `addTriggerTask`。改为注入 `org.springframework.scheduling.TaskScheduler`（默认单线程 scheduler，Spring 提供），在 `configureTasks`（或 `@PostConstruct` / `ApplicationReadyEvent`）中调用 `taskScheduler.schedule(Runnable{triggerBatchSend()}, DynamicCronTrigger(settingService))`，将返回的 `ScheduledFuture<*>` 存入字段 `@Volatile private var scheduledFuture`。
    - 说明：仍实现 `SchedulingConfigurer` 仅为确保默认 `TaskScheduler` 被创建并可注入；初始 schedule 在拿到 scheduler 后进行。若注入 `TaskScheduler` 已足够，可移除 `SchedulingConfigurer`——以能拿到非空 scheduler 为准（执行时确认默认 scheduler 注入可用）。
  - 新增 `@EventListener fun onCronChanged(e: BatchSendCronChangedEvent)`：`synchronized` 下 `scheduledFuture?.cancel(false)`（**false**，不打断在途执行 → I-2），再 `taskScheduler.schedule(...)` 重新排程并替换 `scheduledFuture`。`DynamicCronTrigger` 首次评估时 `TriggerContext` 为空 → 基于"当前时刻"按新 cron 计算下一发，旧的在途触发被 cancel 丢弃（满足 I-1）。
  - `triggerBatchSend()` 内**保留**现有 `if (!config.autoEnabled) return` 判断与日志（I-4），逻辑不变。
  - 重排逻辑**不读** `autoEnabled`（I-4）。
- 文件：`src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt`（修改）。

### Task 4 — 同步修复受影响的现有单测
- `BatchSendSettingServiceTest`：构造改为 `BatchSendSettingService(repository, eventPublisher)`，`eventPublisher` 用 mock（`mockk` / Mockito，与项目现有风格一致）。补一条断言：改 cron 时发布事件、cron 未变时不发布（覆盖 I-3）。
- 文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingServiceTest.kt`（修改）。

### Task 5 — 新增调度器重排测试
- 新增 `BatchSendSchedulerTest`：
  - 用 mock `TaskScheduler`，验证收到 `BatchSendCronChangedEvent` 后：先 `cancel(false)` 旧 future，再 `schedule(...)` 一次（I-1、I-2：断言 `cancel` 入参为 `false`）。
  - 验证 `triggerBatchSend()` 在 `autoEnabled=false` 时不调用 `batchSendControlService.startAuto()`（I-4 回归）。
- 文件：`src/test/kotlin/com/weibo/talentintroduction/task/service/BatchSendSchedulerTest.kt`（新增）。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|------|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/event/BatchSendCronChangedEvent.kt` | 新增 | cron 变更事件 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt` | 修改 | 注入 publisher；cron 变化时发事件 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt` | 修改 | 自持 future + `@EventListener` 重排 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingServiceTest.kt` | 修改 | 适配新构造 + 事件断言 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/task/service/BatchSendSchedulerTest.kt` | 新增 | 重排 / 取消 / autoEnabled 回归 |

文件数：5（≤10 ✔）。子系统：调度（task）+ 配置服务（campaign），2 个（≤2 ✔）。共享存储新增字段：0（无新字段，仅新增联动 ✔）。

## 验收标准

- **I-1（单一在途触发）**：
  - 单测：发布 `BatchSendCronChangedEvent` 后，`TaskScheduler.schedule` 恰好被再次调用一次，且旧 `ScheduledFuture` 被 cancel；重排后不存在两个 active future。
  - 手工/集成：`PUT /batch-send/config` 改 cron 后，下一次实际触发时间符合新 cron；旧时间点不再触发（可用一个近未来 cron 验证仅触发一次）。
- **I-2（不打断在途）**：单测断言 `scheduledFuture.cancel(false)` 的入参为 `false`（`mayInterruptIfRunning=false`）。
- **I-3（非 cron 变更不重排）**：`BatchSendSettingServiceTest` 断言：`updateConfig` 中 cron 与旧值相同 → 不发布事件；只改 dailyCap/roundSize 等 → 不发布事件。仅 cron 变化 → 发布一次。
- **I-4（autoEnabled 不影响注册）**：
  - 单测：`autoEnabled=false` 时 `triggerBatchSend()` 不调用 `startAuto()`，但调度仍注册（schedule 已被调用、future 非空）。
  - 回归：`pauseSchedule`（autoEnabled=false）后再 `resumeSchedule`，cron 触发仍工作。
- **集成场景（覆盖 IP-1）**：模拟"运行期改 cron"——初始 cron A 排程 → `updateConfig` 改为 cron B → 断言旧 A 触发被取消、后续仅按 B 触发。复现并防止 6/25 00:00 的"旧残留触发"。
- **全量回归**：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 全绿；`MailAutomationScheduler` / `DailyCountResetScheduler` 相关测试不受影响。

## 自检清单（create-p Phase 4）

- [x] 关键不变量存在，且对新增联动/状态各有 ≥1 条不变量（I-1..I-4）
- [x] 现状审计基于 grep 实证列出 cron 的全部 write/read 路径（唯一写入口 = `updateConfig`）
- [x] 没有任务引入未被不变量覆盖的写路径（未新增任何写路径，仅新增事件联动）
- [x] 文件数 5 ≤ 10
- [x] 子系统 2 ≤ 2
- [x] 每个任务引用其约束不变量编号
- [x] 验收标准每条不变量至少一项检查
- [x] 文件清单逐一具名，无"相关文件"/"等"
- [x] Out-of-scope 显式延后（12:15 来源定位、ES 403、多实例锁、前端）

## 执行期需现场确认的两点（不阻塞计划）

1. 默认 `TaskScheduler` 是否可直接注入（无自定义 Bean 时 Spring 是否暴露）。若不可注入，则在本类内定义一个单线程 `ThreadPoolTaskScheduler` Bean 复用，并确保与现有 `SchedulingConfigurer` 调度器不冲突（仍属本计划 task/调度子系统，文件数不变）。
2. 项目 mock 框架（mockk vs Mockito）以现有测试为准，保持一致。
