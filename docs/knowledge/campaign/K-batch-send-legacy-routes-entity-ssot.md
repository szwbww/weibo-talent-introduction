---
id: K-batch-send-legacy-routes-entity-ssot
domain: campaign
created: 2026-07-14
last_used: 2026-07-14
hit_count: 2
source: fix-v:batch-send-task-execution-and-logs:fix-1
severity: P1
---

经验：批量发送配置实体化后，若旧 `/config` 与 `/types/{sendType}/config` 仍读写 KV，而 scheduler/执行只读 `batch_send_task_config`，会形成双事实源；旧客户端改 cron/限额后定时与手动仍用过期实体值。

正确做法：兼容路由只做 `legacy_code` 实体适配器，复用 `BatchSendTaskConfigService` 校验/保存/reload；缺失或软删除返回明确 404，禁止静默退回 KV。

反例：`BatchSendConfigController` 直连 `BatchSendSettingService.getConfig/updateConfig`，而 `BatchSendScheduler`/`BatchSendControlService.startScheduled` 只读实体。
