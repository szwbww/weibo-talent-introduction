---
id: K-allowedTaskTypes-whitelist
domain: task
created: 2026-08-24
last_used: 2026-08-24
hit_count: 4
source: create-p:expert-rnd-classification
---

`TaskProgressController.allowedTaskTypes` 已不再手工维护；当前实现从
`TaskTypeCatalog.entries.filter { hasProgressUi }` 派生。新增需要查询执行历史的任务类型时，
必须登记 `TaskTypeCatalog` 且设置 `hasProgressUi=true`，否则
`GET /api/task-progress/{taskType}/executions` 返回 400。

任务汇总语义由 `TaskTypeCatalog.summaryRule` 与 `TaskExecutionSummaryExtractor` 统一处理；
禁止在 controller 重新增加 taskType `when` 分支。若不需要结构化明细，`summaryRule=null`，
但任务结果仍应实现 `TaskExecutionSummaryProvider` 以保证 success/failure 与终态可信。
