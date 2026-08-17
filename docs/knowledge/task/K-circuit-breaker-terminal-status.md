---
id: K-circuit-breaker-terminal-status
domain: task
created: 2026-07-08
last_used: 2026-08-16
hit_count: 2
source: fix-v:enrichment-cross-day-resilient-run:fix-2
severity: P1
---

经验：后台任务出现 circuit breaker / abort / safety stop 时，不能只写 failureReasons 后落入正常完成分支；否则进度弹窗和 task_execution 会显示成功，运营会误判任务已完整完成。
正确做法：熔断分支必须有显式 FAILED 终态，progressStore、返回结果、TaskExecutionSummaryProvider 三者语义一致；测试必须同时断言 failureReasons 和最终 status。
反例：`ExpertDiscoveryService.enrichExistingExperts()` 在 ABORT 连续限流 5 次后仅设置 `failureReasons["CIRCUIT_BREAKER"]`，随后无条件写 `COMPLETED`（`ExpertDiscoveryService.kt:921-925`, `998-1011`）。
