---
id: K-enrichment-write-three-layers
domain: es-index
created: 2026-07-07
last_used: 2026-07-07
hit_count: 3
source: create-p:expert-enrichment-backend
---

`ExpertDiscoveryService.updateExpertAcademicFields()` 对 RAW、CANDIDATE、APPLICATION 三层索引均执行 ES `_update` partial update。某层不存在该文档时 catch 异常并静默跳过。任何新增 enrichment 字段必须在此方法的 doc map 中添加写入逻辑。
