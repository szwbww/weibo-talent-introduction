---
id: K-progress-log-pending-token-orphan
domain: task
created: 2026-09-22
last_used: 2026-09-22
hit_count: 2
source: create-p:batch-execution-log-process-visibility-p1
severity: P1
---

2026-09-22 复核：历史“bindExecutionId 不回写负 token 日志”问题已经修复。

- `TaskProgressStore.tryStartWithToken:145` 仍先生成负 pendingToken，并写初始化日志。
- `bindExecutionId:158-184` 成功替换内存 id 后调用 `TaskProgressLogRepository.rebindPendingExecutionId`；按 pendingToken 更新对应日志的 task_execution_id。
- rebind 失败只 WARN，不改变 bind 成功返回值，不能为了日志修复阻断业务启动。
- 新实时观察者须按“正 executionId 相等”绑定进度；负 token 阶段不能当真实任务记录展示。`clearExecutionContext` 清 id 不清 status，也不能仅按 taskType/status 认领执行。

历史问题仍应由 TaskProgressStoreRebindTest 防回归；不要在后续计划中重复要求实现已存在的 rebind。保留清理按 created_at，而不是依赖真实 executionId 的关联，兼容 rebind 失败留下的孤儿行。

来源复核：create-p:task-activity-center。关联：[[K-clearExecutionContext-status-leak]]。
