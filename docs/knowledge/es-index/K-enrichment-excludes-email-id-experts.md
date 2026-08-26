---
id: K-enrichment-excludes-email-id-experts
domain: es-index
created: 2026-08-25
last_used: 2026-08-25
hit_count: 1
source: create-p:00-rnd-gate-master
severity: P1
---

**无 ORCID 的专家永远不会被 OpenAlex enrichment 补字段**，其 ES 画像在入库那一刻就定型了。

## 三重限制，缺一不可绕过

1. `ExpertDiscoveryService.enrichExistingExperts:845-877` 只扫 `ExpertIndexLevel.CANDIDATE`
   （`:850` 计数、`:877` 遍历）。**RAW 层从不 enrich。**
2. `buildEnrichmentFilters:800-826` 的 `bool` 带
   `must_not: [{ prefix: { orcidId: "EMAIL-" } }]`（`:820-822`）。
   而无 ORCID 的作者用 `ExpertIdGenerator:16` 的 `EMAIL-<hash>` 作 id。
3. 批量补全走 `OpenAlexDataSource.batchEnrichByOrcids:200-205`，URL 是
   `authors?filter=orcid:<a|b|c>` —— **没有 ORCID 就没法查**，即使放开 2 也无解。

## 推论（做任何"新数据源"或"晋升门禁"计划前必读）

- 任何以 `EMAIL-` 为 id 入库的专家，其 `researchFields` / `recentWorkTitles` / `patentTitles` /
  `hIndex` / `worksCount` / `citationCount` / `disciplineCategory` **永久为空**。
- 因此这批人的 `ExpertClassificationService` 打分输入只有入库时写入的字段，
  分类结果**永远不会改变**。设计新数据源时，必须在入库那一刻就把足以过阈值的信息写全。
- 「先入库，后面 enrich 会补上」这个假设对 `EMAIL-` 专家一律不成立。

关联：[[K-raw-index-scoring-fields-sparse]]、[[K-openalex-fetch-works-gated]]
