---
id: K-batch-send-setting-kv
domain: mail
created: 2026-07-05
last_used: 2026-07-11
hit_count: 5
source: create-p:batch-send-template-selector
---

`batch_send_setting` 是 KV 表（`setting_key UNIQUE, setting_value`），不是列式 schema。新增配置项只需加常量 + upsert 调用，无需 DDL 迁移。

**写路径**: `BatchSendSettingService.upsert(key, value)` — 唯一写入点。
**读路径**: `BatchSendSettingService.loadAll()` → 被 `getConfig()` / `getRuntimeStatus()` 消费。

**Why:** 与列式配置表不同，新增字段不需要 Flyway 迁移，但也不会有 NOT NULL 约束保护——需在读取侧处理缺失值。

**How to apply:** 新增批量发送配置项时，在 `BatchSendSettingService` 加 KEY 常量 + 默认值 + validate，不写迁移。
