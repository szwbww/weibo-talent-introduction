---
id: K-expert-classification-one-object-three-layers
domain: expert
created: 2026-08-24
last_used: 2026-08-25
hit_count: 1
source: create-p:expert-rnd-classification
---

RAW、CANDIDATE、APPLICATION 三层 ES mapping 都是 `dynamic:false`，层级晋升又通过
复制完整 `_source` 实现。新增跨层专家事实时，应在三份 mapping 中声明同一个顶层对象，
并同步加入 `ExpertSearchService.sourceFields/toExpertProfile`；禁止把 type、sendable、version
拆成多个根字段形成并列事实源。

回填只能按真实 ES `_id` 做局部 `_bulk update`，`doc_as_upsert=false`，不得修改根级
`updatedAt`；这样 RAW→CANDIDATE→APPLICATION 的既有整文档复制会自然携带该对象，
同时不会覆盖原始姓名、邮箱、标签和运营状态。
