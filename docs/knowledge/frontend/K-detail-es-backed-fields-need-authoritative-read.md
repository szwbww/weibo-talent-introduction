---
id: K-detail-es-backed-fields-need-authoritative-read
domain: frontend
created: 2026-07-02
last_used: 2026-08-10
hit_count: 4
source: fix-v:expert-tag-crud-reminder-batch:fix-1
severity: P1
---
经验：详情页展示 ES-only 字段时，不能把列表缓存当权威数据源；同一列表可能有 ES path 和 DB path，DB contact 响应不带 ES-only 字段，会把真实值渲染成空。
正确做法：详情页和变更后的刷新必须从字段的权威存储读取，或保证当前缓存来自同一权威查询口径；ES tags 等 ES-only 字段应按 `orcidId + level` 重新读 ES。
反例：`src/main/resources/static/app.js:4569-4587` 用 `state.contacts.find(...).tags || []` 渲染专家标签，但 `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt:380-400` 的 DB contact 响应没有 tags。
