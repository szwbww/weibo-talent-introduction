---
id: K-filter-option-scope-parity
domain: frontend
created: 2026-07-02
last_used: 2026-07-14
hit_count: 5
source: fix-v:ai-training-redesign:fix-1
severity: P1
---
经验：前端筛选选项的取值范围必须与后端实际筛选范围一致；后端支持多索引/多来源筛选时，选项接口只拉单一来源会造成“后端可筛、UI 不可选”的隐性失效。
正确做法：选项加载与筛选执行共享同一 scope 定义；如后端筛选 CANDIDATE+APPLICATION，前端也必须合并两个 level 的 tag 聚合，或由后端提供统一聚合接口。
反例：`app.js:1927` 只拉 `tags/aggregation?level=APPLICATION`，但 `AiTrainingController.kt:220-231` 筛选时同时查 CANDIDATE 与 APPLICATION。
