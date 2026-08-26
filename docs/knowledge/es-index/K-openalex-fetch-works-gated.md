---
id: K-openalex-fetch-works-gated
domain: es-index
created: 2026-08-16
last_used: 2026-08-25
hit_count: 1
source: create-p:expert-reachability-01-last-publication-year
severity: P1
---

OpenAlex 的 works/patents 拉取有**两条路径、两种门控**，做任何「从 works 派生字段」的需求前必须先分清：

```bash
grep -rn "fetchRecentWorks" --include=*.kt src/main/kotlin
```
```
OpenAlexDataSource.kt:126:    private fun fetchRecentWorks(worksUrl: String, limit: Int): List<String>?
OpenAlexDataSource.kt:252:   val recentWorkTitles = if (properties.fetchWorksEnabled) fetchRecentWorks(worksApiUrl, 3) else null
OpenAlexDataSource.kt:285:   val recentWorkTitles = if (fetchWorksAndPatents && worksUrl != null) fetchRecentWorks(worksUrl, limit = 3) else null
```

- **`:285` 单专家路径**（`enrichAuthor()` → `parseAuthorEnrichmentFromNode(node, fetchWorksAndPatents = true)`）
  无条件取 works。
- **`:252` 批量路径**受 `properties.fetchWorksEnabled` 门控，而该开关**默认 false**：
  ```
  application.yml:159:      fetch-works-enabled: ${OPENALEX_FETCH_WORKS_ENABLED:false}
  OpenAlexProperties.kt:20:    val fetchWorksEnabled: Boolean = false,
  ```

**推论**：`recentWorkTitles` / `patentTitles` 以及任何从 works 响应派生的新字段，
在默认配置下**只有单专家 enrich 路径会产出**，批量 enrichment 零产出。
「反正 works 已经在取了，加个字段零成本」这一判断只对 `:285` 成立；
对 `:252` 成立的前提是运营已开 `OPENALEX_FETCH_WORKS_ENABLED=true`，
而开启后每位专家增加 1~2 次 OpenAlex 请求 + 对应 `requestDelayMs` 等待。

计划里凡出现「零额外 API 调用」的措辞，必须同时说明它只对哪条路径成立。

关联：[[K-openalex-enrichment-existing]]、[[K-openalex-author-full-object]]
