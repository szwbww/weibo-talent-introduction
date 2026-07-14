---
id: K-batch-send-setting-kv
domain: mail
created: 2026-07-05
last_used: 2026-07-14
hit_count: 8
source: create-p:batch-send-template-selector
---

`batch_send_setting` 是旧 typed API 的 KV 表（`setting_key UNIQUE, setting_value`），不是列式 schema。它只用于兼容旧接口；可命名、可软删除的任务配置必须落到独立的规范化表，不能继续扩展 KV。

**写路径**: `BatchSendSettingService.upsert(key, value)` — 唯一写入点。
**读路径**: `BatchSendSettingService.loadAll()` → 被 `getConfig()` / `getRuntimeStatus()` 消费。

**Why:** 与列式配置表不同，新增字段不需要 Flyway 迁移，但也不会有 NOT NULL 约束保护——需在读取侧处理缺失值。

**How to apply:** 旧 typed API 的兼容字段才在 `BatchSendSettingService` 加 key；新任务配置需用迁移、实体和仓储实现，且不得形成与 KV 的双写事实源。
