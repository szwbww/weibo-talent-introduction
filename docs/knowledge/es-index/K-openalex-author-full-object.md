---
id: K-openalex-author-full-object
domain: discovery
created: 2026-07-11
last_used: 2026-07-11
hit_count: 0
source: create-p:discipline-category-data
---

OpenAlex author 的两条获取路径(`enrichAuthor` 单个 `GET /authors/{id}`、`batchEnrichByOrcids` 批量 `GET /authors?filter=orcid:a|b|c`)均**不带 `select=` 参数**,返回完整 author 对象——`topics[]` 每项含 `subfield/field/domain` 层级(domain 四值:Physical/Life/Health/Social Sciences)、`summary_stats`、`works_api_url` 等都已在响应中。扩展 enrichment 数据时**不需要新增 API 请求**,只需在共用解析点 `parseAuthorEnrichmentFromNode()`(单个与批量路径共用)提取新字段并扩 `AuthorEnrichment`(带默认值,`baseEnrichment` 等既有构造点即免改)。
