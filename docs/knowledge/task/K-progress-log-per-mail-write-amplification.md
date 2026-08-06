---
id: K-progress-log-per-mail-write-amplification
domain: task
created: 2026-08-06
last_used: 2026-08-06
hit_count: 0
source: create-p:batch-execution-log-process-visibility-p1
---

经验：`TaskProgressStore.update()` 每次调用都 `persistProgressLog()` 落一行，
而 `ManualInitialOutreachService.updateProgressWithAccumulator()` **每发一封邮件调用一次**。
即批量发送每封邮件写一行 `task_progress_log`，且每行的 `details_json` 都内嵌
`buildAccountStats()` 产出的**完整启用账号数组**（含每个账号的额度、预热、限流快照）。

后果：日限额 1000 封 × 十余个账号的 JSON ≈ 每天数 MB；`V22__create_task_progress_log.sql`
只建了 `task_type` 与 `task_execution_id` 两个索引，无 `created_at` 索引、无任何清理策略。

正确做法：要保留逐封明细时，必须同期做两件事之一或全部——
① 降低写放大：只在轮次边界与失败/跳过时把 `accounts` 写进 `details_json`，
   逐封成功行不带账号快照（内存 `TaskProgress` 仍完整，实时面板不受影响）；
② 保留窗口：加 `created_at` 索引 + 定期清理调度（沿用
   `TaskExecutionService.runAndRecord` 记审计），保留天数走配置。

关联：[[K-progress-log-pending-token-orphan]]
