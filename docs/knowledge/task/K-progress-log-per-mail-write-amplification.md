---
id: K-progress-log-per-mail-write-amplification
domain: task
created: 2026-09-22
last_used: 2026-09-24
hit_count: 2
source: create-p:batch-execution-log-process-visibility-p1
---

`TaskProgressStore.update()` 被接受时会 persistProgressLog，每次调用可能产生一条日志。
`ManualInitialOutreachService.updateProgressWithAccumulator`（当前 :1410 起附近）仍将 accounts 完整统计加入 details（:1446），然后调用 Store.update（:1449）。不能把完整日志或 TaskProgress.details 塞进全站高频任务列表。

2026-09-22 复核修正：已存在 `V102__add_task_progress_log_created_at_index.sql` 与 `TaskAuditRetentionService.purge:42` 的按 created_at 分批保留清理；历史“没有created_at索引、没有清理策略”不是当前事实，不应重复设计保留调度。

读取原则：活动列表用小字段投影，运行进度读匹配executionId的内存小DTO；单执行日志才允许按需读取。前端slice最后50条只限制渲染，不等于服务端分页或限制网络大小。若确有日志读取性能问题，单独审计端点后再加分页，勿在展示改造中默认追加写入降频/清理变更。

来源复核：create-p:task-activity-center。
