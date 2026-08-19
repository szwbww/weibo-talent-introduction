---
id: K-execution-detail-running-needs-progress-log
domain: task
created: 2026-08-06
last_used: 2026-08-07
hit_count: 1
source: create-p:batch-execution-log-process-visibility-p1
severity: P1
---

经验：`TaskExecution.resultSummary` 只在 `TaskExecutionService.runAndRecordWithResult()`
的 block 返回后才写入（`TaskExecutionService.kt:127`）。任何以 `resultSummary` 为唯一数据源的
"执行详情"接口，在执行进行中一律解析出 null，把 target / skipped / remaining /
failureReasons / skippedReasons 全渲染成 0。运行中唯一实时的 `task_execution` 字段是
`success_count` / `failure_count`（由 `updateProgressCounts` 刷新）。

正确做法：执行详情的聚合指标按三级优先级取数——
① `resultSummary`（终态权威）→ ② 该执行最新一条 `task_progress_log` 的 `detailsJson`
（`sentTotal` / `failedTotal` / `skippedTotal` / `pending` / `failureReasons` / `skippedReasons`）
与该行 `totalCount` → ③ 才回落 `successCount` / `failureCount`。
配套：任何"成功+失败+跳过+剩余 == 目标"的完整性告警只在终态计算，
否则运行全程恒定误报。

关联：[[K-progress-log-pending-token-orphan]]
