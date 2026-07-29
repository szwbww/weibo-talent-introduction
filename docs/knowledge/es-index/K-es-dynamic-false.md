---
id: K-es-dynamic-false
domain: es-index
created: 2026-07-07
last_used: 2026-07-29
hit_count: 7
source: create-p:expert-enrichment-backend
last_source: create-p:trust-reply-unsupported-answer-v1
---

三层专家 ES 索引（RAW/CANDIDATE/APPLICATION）均设置 `dynamic: false`。给“专家文档”增加共享字段时，必须在三个 mapping JSON 中显式声明后才可搜索/聚合，并同步审计三层 writer/readers；未声明字段虽可能进入 `_source`，但不可依赖其可查询性。

独立业务索引不适用“三个 mapping 同改”：它必须使用独立 index-name 配置和自己的显式 mapping，并证明不进入 `ExpertIndexLevel`、三层 writer/search/promotion 路径。新索引可按自身合同选择更严格的 `dynamic: strict`。
