---
id: K-es-mapping-single-declaration-source
domain: es-index
created: 2026-08-13
last_used: 2026-08-25
hit_count: 1
source: create-p:batch-send-status-consistency (03 P-B)
---

ES 索引 mapping 只能有一个声明源：`src/main/resources/es/*.json`。Kotlin 侧不得存在任何字段名白名单/黑名单。

**白名单陷阱（实测）**：`ExpertIndexService.kt` 曾有 `phase5NewFields` 白名单（`setOf("hIndex", …, "operatorStatus")`），
`loadMappingProperties` 只推送白名单内的字段 → Phase 5 之后新增的任何 ES 字段（`enrichedAt`/
`enrichmentSource`/`patentTitles`/`recentWorkTitles`/`operatorStatus`…）都到不了任何索引，且**无任何报错**
（APPLICATION 43 个声明字段只推送 16 个）。同构问题：同一份契约存在两处声明，无强制收敛。

**整批原子性（I-2）**：ES 对 `PUT _mapping` 是**整批原子**的——任一字段与既有 mapping 类型冲突，
整个请求 400，其余字段一并不生效。因此 `updateMappingIfNeeded` 先整批 PUT；4xx 时降级为逐字段 PUT
（`pushFieldsIndividually`），每个字段成功/失败分别记日志，汇总行形如
`index=X 推送 N 字段：成功 M，冲突 K（字段列表）`。移除白名单后此降级是必需项：
`enrichedAt`（JSON=date、线上 CANDIDATE=keyword）的冲突曾会让整批 34 个字段全军覆没。

**新增 mapping 不追溯存量（I-4）**：给既有索引 `PUT _mapping` 新字段后，存量文档 `_source` 中的值
不会自动进入倒排索引，须 `POST /{index}/_update_by_query` 触发重建（无 script 的 no-op 即可）——
操作步骤见 `docs/runbooks/es-mapping-reindex.md`。

**enrichedAt 类型债**：线上三层 `enrichedAt` 均为 `keyword`（动态映射产物），本地 JSON 迁就线上声明为
`keyword`（技术债，待独立 reindex 计划回正为 `date`）；`ExpertDiscoveryService:795/806` 的 `range` 查询
靠定宽零填充 `yyyy-MM-dd HH:mm:ss` 格式巧合保持正确。
