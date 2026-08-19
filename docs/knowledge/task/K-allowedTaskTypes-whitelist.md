---
id: K-allowedTaskTypes-whitelist
domain: task
created: 2026-07-07
last_used: 2026-07-07
hit_count: 3
source: create-p:enrichment-improvement-v2
---

`TaskProgressController.allowedTaskTypes`（L33）是执行历史接口的白名单。新增任务类型时必须同时把类型字符串加入此 set，否则 `GET /api/task-progress/{taskType}/executions` 返回 400，前端历史记录不可见。同时需在 `parseResultSummary` 和 `fallbackFromLog` 中添加对应的解析分支。
