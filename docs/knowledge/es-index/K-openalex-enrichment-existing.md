---
id: K-openalex-enrichment-existing
domain: discovery
created: 2026-07-07
last_used: 2026-07-08
hit_count: 9
source: create-p:expert-enrichment-backend
---

项目已有 OpenAlex enrichment 基础设施：`OpenAlexDataSource.enrichAuthorByOrcid()` 通过 ORCID 查 OpenAlex author API，`ExpertDiscoveryService.enrichExistingExperts()` 批量遍历 CANDIDATE 层执行 enrichment，`ExpertDiscoveryController.enrichExperts()` 提供 `POST /api/expert-discovery/enrich` 触发入口。扩展 enrichment 数据时应在此基础上改造，不需新建服务。`AuthorEnrichment` 数据类定义在 `OpenAlexDataSource.kt` 末尾。
