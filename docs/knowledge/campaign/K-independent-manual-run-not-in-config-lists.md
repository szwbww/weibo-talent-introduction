---
id: K-independent-manual-run-not-in-config-lists
domain: campaign
created: 2026-08-16
last_used: 2026-08-16
hit_count: 0
source: create-p:batch-console-log-drawer
severity: P1
---

经验：`task_execution.batch_config_id` 可空。**独立手动执行**（前端未选来源配置，
`ManualBatchExecutionRequest.sourceConfigId = null`）落库时该列为 `NULL`，
于是所有 `WHERE batch_config_id = :id` 的查询天然把它排除掉 ——
仓库代码自己写着这一点：`TaskExecutionRepository.kt:11-14` 与 `TaskExecutionService.kt:42-46`。

后果：`GET /api/mail/batch-send/configs/{id}/executions` 永远看不到独立执行；
若 UI 只提供「按配置查日志」这一条入口，独立执行的日志在抽屉关闭后**彻底不可达**。

正确做法：面向运营的「最近执行」列表必须按 `task_type` 查，不是按 `batch_config_id` 查。
`TaskExecutionRepository.findRecentByTaskType(taskType, limit)` **已存在**
（`:32`，生产调用点 `TaskProgressController.kt:85`），不需要新增 `@Query` ——
这一点很关键，因为本仓库 Spring Data JDBC 的 `@Query` 返回 DTO 投影零先例
（见 K-plan-quantified-claims-need-grep-receipts）。

配套：列表 DTO 要回传 `batchConfigId`，前端对 `null` 显示「独立执行」，
否则运营分不清同一批记录里哪条不属于任何定时任务。
