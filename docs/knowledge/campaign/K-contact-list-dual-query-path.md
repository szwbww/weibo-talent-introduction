---
id: K-contact-list-dual-query-path
domain: campaign
created: 2026-06-29
last_used: 2026-06-30
hit_count: 2
source: create-p:expert-contact-reply-mode-filter
severity: P1
---
经验：专家列表 `loadContacts()`（app.js:1965）有两条查询路径——`if (needsAttention)` 走 MySQL `/api/expert-contacts`（:2030），否则走 ES `/api/experts`（:2060）。MySQL-only 的事实字段（`auto_reply_enabled`、`current_status`、`needs_manual_attention`）ES 索引不持有。
正确做法：任何按 MySQL-only 字段做的列表筛选，必须强制走 DB 路径（把触发条件并入 `needsAttention || <新筛选>`），并同步禁用标签/地区（仅 ES 模式可用，:1977-2002）、排序兜底（:2104）。在 ES 路径上加这类筛选会静默失效。
关联：回复模式筛选口径见 K-reply-mode-filter-gate-parity。
