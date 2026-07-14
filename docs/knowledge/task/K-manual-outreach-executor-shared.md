---
id: K-manual-outreach-executor-shared
domain: task
created: 2026-07-07
last_used: 2026-07-14
hit_count: 2
source: create-p:enrichment-cross-day-resilient-run
---

`manualOutreachExecutor`（core=max=1, queue=0）被三处共用：`BatchSendControlService`（批量外发）、`MailAutomationController.checkReplies`（CHECK_REPLIES）、`BatchSendScheduler`。任何新的长时后台任务（分钟级以上，尤其可能跨天的任务）禁止提交到该执行器，否则会把发信/收信任务饿死；应新建专用单线程 executor bean（参照 `DiscoveryExecutorConfig`）。异步化 Controller 须遵守 `tryStartWithToken` → executor 内 `runAndRecordWithResult` + `bindExecutionId` → finally 清理的 CHECK_REPLIES 模式，且 executor 拒绝提交时必须 `progressStore.clear` 回滚占位。
