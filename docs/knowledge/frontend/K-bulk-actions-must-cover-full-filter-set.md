---
id: K-bulk-actions-must-cover-full-filter-set
domain: frontend
created: 2026-07-02
last_used: 2026-07-11
hit_count: 3
source: fix-v:expert-tag-crud-reminder-batch:fix-1
severity: P1
---
经验：面向“当前筛选结果全部执行”的批量操作，不能只复用当前页、首个分页请求或 ES 单次 `size=1000` 的前段结果；否则 UI 显示命中 N 人，实际只处理其中一段，属于静默漏处理。
正确做法：批量动作的 contactId 收集必须明确覆盖完整筛选结果集；前端分页拉满、后端按筛选条件收集，或在无法完整覆盖时阻断操作并告知限制。
反例：`src/main/resources/static/app.js:2417-2426` 对 `state.contactsTotalHits` 取 `Math.min(totalHits, 1000)` 后只请求 `from=0` 一次，`totalHits > 1000` 时第 1001 条之后不会发送。
