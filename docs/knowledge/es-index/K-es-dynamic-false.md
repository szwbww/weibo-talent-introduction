---
id: K-es-dynamic-false
domain: es-index
created: 2026-07-07
last_used: 2026-07-11
hit_count: 6
source: create-p:expert-enrichment-backend
---

三层 ES 索引（RAW/CANDIDATE/APPLICATION）均设置 `dynamic: false`。新字段必须在 mapping JSON 中显式声明后才可被搜索和聚合；未声明字段可写入但不可查询。任何涉及 ES 字段新增的计划必须同步修改三个 mapping 文件。
