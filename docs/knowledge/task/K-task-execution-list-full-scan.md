---
id: K-task-execution-list-full-scan
domain: task
created: 2026-09-22
last_used: 2026-09-22
hit_count: 1
source: create-p:task-records-refactor-main
severity: P1
---

2026-09-22 现状复核：下述三重放大是历史问题。当前 TaskExecutionService.listExecutions:19 已走四组分页投影查询，TaskExecutionController:37 返回 items/total，V100已加索引。保留M-1作为新增高频列表约束；不可据此声称现有任务记录仍全表返回。TaskProgressController的旧最近执行接口仍读完整实体，不能直接复用为全站高频列表。


经验：`GET /api/task-executions`（任务记录页）慢到不可用，是**三重放大叠加**，只治其一收益有限。

1. **全表返回**：`TaskExecutionService.listExecutions` 走 `findAllByOrderByStartedAtDesc()`，无分页无 LIMIT。
2. **全量 TEXT**：`TaskExecutionResponse` 携带 `requestPayload` + `resultSummary` 两个 TEXT 列，而前端 `loadTasks()`（`app.js:8906`）**只渲染 7 个标量字段**。`AUTO_REPLY_ALL` 的 `result_summary` 内嵌 `accounts[].repliedExperts[]`，单行可达几十 KB。
3. **无索引**：`V4__create_task_execution.sql` 只建主键；`V73` 只加 `(batch_config_id, started_at)`。`ORDER BY started_at DESC`、`WHERE task_type=?`、`WHERE status=?` 全是全表扫 + filesort。

**不变量（M-1）**：任何**列表**性质的 task_execution 接口（返回多行），SQL 的 SELECT 列表中不得出现 `request_payload` / `result_summary`；这两列只允许由**单行**详情接口读取。列表 DTO 也不得声明这两个属性。

推论：因此列表页无法提供从 `resultSummary` 提取的 `summaryText` —— 摘要只能出现在展开明细时。想在列表行显示摘要就必然违反 M-1。

关联：[[K-execution-detail-running-needs-progress-log]]、[[K-progress-log-per-mail-write-amplification]]（同表族的另一侧增长问题）
