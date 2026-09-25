---
id: K-progress-log-batchonly-two-readers
domain: task
created: 2026-09-24
last_used: 2026-09-24
hit_count: 3
source: create-p:batch-execution-log-process-visibility-p1
severity: P1
---

`task_progress_log` 的通用任务弹窗与批量发送日志有不同契约，不应顺手统一。

- `TaskProgressController.getProgressLogs` 受 batchOnly 参数控制；默认false可读全部，true保留通用弹窗的按批行为。
- 2026-09-24 当前 `BatchSendConfigController.buildProgressRows:164` 已保留 batchNumber=0 的首条INIT与后续FINAL；其余按batchNumber取末条ROUND，并按id排序。旧条目“初始化/最终行全被丢弃且无测试”的缺陷已修复，现有 `BatchSendExecutionDetailTest` 覆盖此语义。
- 每邮箱事件仍不能仅依赖按批取末条的列表；已有OutcomeAccumulator错误样本最多20，完整逐项审计应有独立读写合同，不能把样本当全集。

证据：docs/plans/2026-09-24/emailable-evidence.md F-8；本条不要求修改通用进度控制器。
