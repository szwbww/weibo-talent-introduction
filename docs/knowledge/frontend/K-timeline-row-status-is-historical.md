---
id: K-timeline-row-status-is-historical
domain: frontend
created: 2026-08-07
last_used: 2026-08-07
hit_count: 0
source: create-p:batch-timeline-running-status-render
severity: P1
---

经验：`task_progress_log` 的每一行是**历史快照**，其 `status` 字段记录的是"写这行时任务处于什么状态"，
不是"现在处于什么状态"。写入侧 `ManualInitialOutreachService` 的全部非终态进度行**硬编码**传
`"RUNNING"`（`:264` / `:340` / `:353` / `:537` / `:600` / `:728` / `:742`），
只有 `batchNumber == 0` 的终态行传真实 `finalStatus`（`:186` / `:448`）。
读取侧 `BatchSendConfigController.toExecutionProgressRow`（`:266-281`）逐字透传，不做归一化。

后果：前端若无条件对每行调用 `statusLabel(row.status)`，一次**早已成功结束**的执行，
其时间线的初始化行与全部批次行都会永久显示「运行中」，且刷新页面不变。
线上曾据此误判"手动批量发送卡在初始化"，实际后端 100ms 内已 SUCCESS 完成。

正确做法：
1. 时间线行的状态标签只在承载信息时输出——`status === "RUNNING"` 的行不输出状态元素
   （**不输出标签本身**，而非置空或 `display:none`；`.batch-timeline-main` 有 `gap: 4px`，
   隐藏而非移除会留下空隙）。
2. liveness 由 live 区块独占表达：`renderBatchLiveSection` 只认 `detail.live`，
   而 `live` 非空当且仅当内存槽仍绑定该 executionId（`TaskProgressStore.getCurrentExecutionId`）。
   时间线渲染函数**不得**读取 `d.live` / `d.status` 反推行的显示，否则轮询半途两处状态会互相矛盾。
3. 同理，任何"从历史日志行推断当前状态"的 UI 都要先问：这个字段是快照还是现态。

关联：[[K-execution-detail-running-needs-progress-log]]、[[K-progress-log-batchonly-two-readers]]
