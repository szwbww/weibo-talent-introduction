---
id: K-progress-log-batchonly-two-readers
domain: task
created: 2026-08-06
last_used: 2026-08-06
hit_count: 0
source: create-p:batch-execution-log-process-visibility-p1
severity: P1
---

经验：`task_progress_log` 有两个语义不同的读取口，看起来都在做
`filter { batchNumber > 0 }.groupBy { batchNumber }.map { last }`，但不能顺手统一：

1. `TaskProgressController.getProgressLogs` — 过滤受 `batchOnly` 参数控制，
   默认 `false` 已返回全部行。`batchOnly=true` 是通用任务进度弹窗
   （`static/task-modal-runtime.js:130`）的既定契约，并被
   `TaskProgressControllerExecutionsTest`（"batchOnly filters out batchNumber zero and negative"
   与 "batchOnly false returns all logs"）两条用例锁定。**不要动。**
2. `BatchSendConfigController.getConfigExecutionDetail` — **硬编码**同样的过滤，
   无参数、无测试覆盖。`batchNumber == 0` 的行（初始化行、空快照终态、轮次闸门失败、
   最终 stopReason 收尾）全被丢弃，导致"为什么一封没发/为什么中途停了"在 UI 上不可见。

正确做法：改批量执行日志的可见性时，只改 ②，并保留 ① 的 batchOnly 语义与其测试。
`batchNumber == 0` 的行应分类为 INIT / FINAL 保留，`batchNumber > 0` 才按批次去重，
整体按 `id` 升序而非 `batchNumber` 升序输出。

关联：[[K-execution-detail-running-needs-progress-log]]
