---
id: K-contact-list-dual-path-field-parity
domain: frontend
created: 2026-08-10
last_used: 2026-08-10
hit_count: 0
source: create-p:sender-binding-05-frontend-visibility
severity: P1
---
经验：专家列表 `loadContacts()` 有两条查询路径——`useDbContactPath`（needsAttention/replyMode）走 DB `/api/expert-contacts`，否则走 ES `/api/experts`。**任何要显示在列表上的字段，必须同时加进两条路径的 DTO**：`ExpertIndexResponse`（ES 路径，`expert/controller/ExpertIndexController.kt`）与 `ExpertContactResponse`（DB 路径，`campaign/controller/ExpertContactManagementController.kt`），且 `app.js` `loadContacts()` 的两个 map 分支（DB 分支与 ES 分支）都要取值。
正确做法：新增列表字段时先在两条 DTO + 两处 map 的清单上打勾；DB 路径只在运营启用「需人工关注 / 回复模式」筛选时触发，漏加一侧不会立刻报错，而是切换路径后字段静默消失。
反例：`tags` 字段——`app.js` DB 分支写了 `tags: c.tags || []`，但 `ExpertContactResponse` 根本没有 `tags`，导致 DB 路径下标签恒为空。绑定字段（`boundSenderAccountCode` / `senderAccountChanged`）的 P5 实现即按此双路径补齐。
关联：K-contact-list-dual-query-path、K-detail-es-backed-fields-need-authoritative-read。
