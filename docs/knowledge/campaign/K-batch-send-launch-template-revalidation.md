---
id: K-batch-send-launch-template-revalidation
domain: campaign
created: 2026-07-13
last_used: 2026-07-14
hit_count: 3
source: fix-v:material-reminder-batch-send:fix-1
severity: P1
---
经验：配置保存时的模板类型校验无法防住旧 KV、直接数据写入或模板随后被禁用/改类型。
正确做法：启动任务前对所有显式 templateId 再校验 enabled 与 `mailType`；仅允许 INTRODUCTION 的 null 模板回退默认值。
反例：`BatchSendControlService.kt:225` 对 INTRODUCTION 提前返回。
