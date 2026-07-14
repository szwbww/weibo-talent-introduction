---
id: K-batch-task-config-implementation-evidence
domain: campaign
created: 2026-07-14
last_used: 2026-07-14
hit_count: 3
source: fix-v:batch-send-task-config-crud:fix-1
severity: P1
---
经验：已有模块的绿测只能证明旧行为可编译，不能证明新计划已落地；若计划列出的迁移、实体、仓储、服务和路由均不存在，新 API 每次调用都会失败。
正确做法：复验配置 CRUD 时先逐项确认计划文件已创建，再审计持久化边界、服务语义、路由和目标测试；缺失实现必须作为 P1，不能被旧测试绿灯掩盖。
反例：`BatchSendConfigController.kt:31-54` 仅有旧 KV 路由，`BatchSendTaskConfigService.kt` 与 V72 均不存在。
