---
id: K-progress-log-pending-token-orphan
domain: task
created: 2026-08-06
last_used: 2026-08-16
hit_count: 1
source: create-p:batch-execution-log-process-visibility-p1
severity: P1
---

经验：`TaskProgressStore.tryStartWithToken()` 落的第一条 `task_progress_log`（初始化行）
持久化时 `task_execution_id = pendingToken`（`-System.nanoTime()`，负值），
而 `bindExecutionId()` 只改内存槽，**不回写已落库的日志行**
（`TaskProgressStore.kt:145-156` / `:158-179`）。

后果：
1. 任何按真实 executionId 查询进度日志的读取口（`findAllByTaskExecutionIdOrderByIdAsc`）
   都拿不到初始化行，"任务启动了但看不到起点"；
2. 负 id 行在 `task_progress_log` 中永久成为孤儿，无任何查询路径可达，也不会被清理。

正确做法：`bindExecutionId` 绑定成功后，按 `task_execution_id = :pendingToken` 条件
把这些行改写为真实 executionId；改写必须 try/catch 吞异常并只记 WARN，
不得改变 `bindExecutionId` 返回值或阻断任务启动——该方法被 6 个任务类型共用
（`BatchSendControlService`、`MailAutomationController`、`ExpertDiscoveryController`×3、
`ExpertDiscoveryScheduler`、`ExpertIndexController`×2），抛出即 P0 级启动回归。

关联：[[K-manual-outreach-executor-shared]]、[[K-clearExecutionContext-status-leak]]
