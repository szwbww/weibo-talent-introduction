---
id: K-clearExecutionContext-status-leak
domain: task
created: 2026-07-07
last_used: 2026-07-08
hit_count: 3
source: create-p:enrichment-improvement-v2
---

`TaskProgressStore.clearExecutionContext()` 只清除 `executionId`（置 null），**不清除 status**。如果任务提前退出且未显式写入终态（COMPLETED/FAILED/CANCELLED），store 中 status 会遗留为 RUNNING，导致前端永远显示"初始化中"。任何新任务的 Controller finally 块必须保证：正常完成路径由 service 层写终态；异常路径由 Controller 显式写 FAILED；最后兜底检查 store 是否仍为 RUNNING，是则 `progressStore.clear(taskType)`。
