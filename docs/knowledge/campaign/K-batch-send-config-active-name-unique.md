---
id: K-batch-send-config-active-name-unique
domain: campaign
created: 2026-07-14
last_used: 2026-07-14
hit_count: 2
source: fix-v:batch-send-task-config-crud:fix-1
severity: P1
---
经验：仅在 service 里先查再写，不能保证可软删除配置的活动名称唯一；两个并发请求可同时查到空结果并插入同名行。
正确做法：对 `deleted_at IS NULL` 的活动名称使用数据库唯一约束（MySQL 可用生成列承载活动名称），并把唯一键冲突转换为 409；软删除后名称可复用。
反例：`V72__create_batch_send_task_config.sql:1-27` 无活动名称唯一键，`BatchSendTaskConfigService.kt:145-148` 是 TOCTOU 预检查。
