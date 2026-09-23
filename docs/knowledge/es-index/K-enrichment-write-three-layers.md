---
id: K-enrichment-write-three-layers
domain: es-index
created: 2026-09-21
last_used: 2026-09-22
hit_count: 14
source: create-p:expert-enrichment-backend
---

`ExpertDiscoveryService.updateExpertAcademicFields()` 是 RAW、CANDIDATE、APPLICATION 三层学术字段的集中 partial update 写入点。2026-09-21 源码已先 HEAD 判断文档存在，再 `_update`；某层不存在不创建新文档。任何新增学术字段须在此 doc map 显式写入。

2026-09-22重新核对：当前返回 `LayerUpdateResult`，已按层表达结果。历史实现曾返回 `candidateUpdated`，导致 RAW-only 或 APPLICATION-only 写成功仍判失败；后续改动不得退回该布尔语义，须区分文档不存在、查询失败和写入失败。
