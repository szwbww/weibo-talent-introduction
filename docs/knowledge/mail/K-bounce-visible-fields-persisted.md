---
id: K-bounce-visible-fields-persisted
domain: mail
created: 2026-06-26
last_used: 2026-08-18
hit_count: 3
source: fix-v:inbound-selfcheck-bounce-visibility:fix-1
severity: P1
---
经验：列表接口承诺展示的退信事实必须在写入时持久化；只把字段放在内存信号或响应 DTO，回填后无法稳定重建，尤其是未关联专家的失败收件人。
正确做法：`BounceCollectionService.ingest()` 保存退信名单需要展示的字段，包括 `failedRecipient`；`GET /api/mail/bounces` 从 `bounce_record` 返回这些字段，前端只渲染已持久化事实。
反例：`BounceCollectionService.kt:89-99` 未保存 `signal.failedRecipient`，`BounceController.kt:47` 固定传 `failedRecipient = null`，`BounceController.kt:109` 响应字段永远为空。
