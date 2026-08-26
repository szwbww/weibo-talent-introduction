---
id: K-batch-config-legacy-adapter-field-preservation
domain: campaign
created: 2026-08-12
last_used: 2026-08-25
hit_count: 1
source: create-p:batch-send-rhythm-01-rounds-per-run
severity: P1
---

`BatchSendTaskConfigService.updateLegacyConfig()`（`:156-190`）是旧 typed API
（`/api/mail/batch-send/types/{sendType}/config`）到实体的适配器：它接收**只含旧字段**的
`BatchSendConfigUpdateRequest`，却调用**全量** `update(id, BatchSendTaskConfigUpdateCommand(...))`。

因此每当 `batch_send_task_config` 新增一个列，都必须在该适配器里显式写
`newField = existing.newField`，加入既有的保留集合（`configName` / `funnelLevel` / `tags`）。
漏写会命中 `BatchSendTaskConfigUpdateCommand` 的 Kotlin 默认值，把存量配置**静默重置**为默认值——
运营从旧界面改一次任意字段，新配置项就被抹掉，且无任何报错。

同一文件里区分三类映射，不要混淆：

- `:338` `toView()` —— row → `BatchSendTaskConfigView`，**新列要加**（前端要读）
- `:181` `updateLegacyConfig` 返回值、`:208` `toLegacyConfig()` —— row → `BatchSendConfig`
  （`BatchSendSettingService.kt:240` 的 KV 兼容 data class），**新列通常不加**，
  否则会把 KV 兼容层拖进变更范围
- `:423/:439/:455` 三个 `*Fields()` —— command/entity → `ConfigFields`，**新列要加**（走校验）

关联：[[K-batch-send-legacy-routes-entity-ssot]]、[[K-batch-task-config-snapshot-log-identity]]
