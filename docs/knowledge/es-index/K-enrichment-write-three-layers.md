---
id: K-enrichment-write-three-layers
domain: es-index
created: 2026-07-07
last_used: 2026-07-11
hit_count: 11
source: create-p:expert-enrichment-backend
---

`ExpertDiscoveryService.updateExpertAcademicFields()` 对 RAW、CANDIDATE、APPLICATION 三层索引均执行 ES `_update` partial update。某层不存在该文档时 catch 异常并静默跳过（当前实现产生 404 WARN 日志，计划改为 HEAD 检查后按需写入）。任何新增 enrichment 字段必须在此方法的 doc map 中添加写入逻辑。
