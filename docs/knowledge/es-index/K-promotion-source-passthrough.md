---
id: K-promotion-source-passthrough
domain: es-index
created: 2026-07-11
last_used: 2026-07-11
hit_count: 2
source: create-p:discipline-category-data
---

晋升写路径(`ExpertIndexWriterService.promoteToCandidate` L365、`promoteToApplication` L232)通过 `_source.fields()` 全量逐字段复制构建目标文档,再叠加晋升元数据字段。因此**新增 ES 字段会自动透传 L3→L2→L1,晋升代码零改动**;只有 partial update 写路径(`updateExpertAcademicFields`、`syncCandidateOperatorStatus` 等 doc/script 局部更新)需要显式处理新字段。计划新增字段时,晋升路径应列为"透传、禁改",并用验收项验证晋升后字段不丢失。
