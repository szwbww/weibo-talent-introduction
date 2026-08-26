---
id: K-openalex-has-no-patent-data
domain: es-index
created: 2026-08-25
last_used: 2026-08-25
hit_count: 1
source: create-p:00-rnd-gate-master
severity: P1
---

**OpenAlex 没有专利数据。** 2026-08-25 实测：

```
GET https://api.openalex.org/works?filter=type:patent   →  meta.count = 0，results = []
GET https://api.openalex.org/types                      →  类型列表中不存在 "patent"
按 author.id 合并 type:patent                            →  count = 0
```

（该数据继承自已停服的 Microsoft Academic，OpenAlex 未延续。）

## 直接后果

1. `OpenAlexDataSource.fetchPatents:146-147` 的查询
   `$worksUrl?filter=type:patent&select=title,publication_year` **永远返回空**。
   `patentTitles` 这个 ES 字段在本系统中**恒不可得**。
2. **打开 `OPENALEX_FETCH_PATENTS_ENABLED` 是纯成本**：每位专家多一次 OpenAlex 请求 +
   一次 `enrichmentDelayMs` 等待（`OpenAlexDataSource.kt:240-253`），零收益。**不要打开。**
3. `ExpertClassificationService.productionScore` 中权重最高的
   `PROD_PATENTS` +45（`:108-111`）恒不触发 → 生产分实际可达上限 55
   （`PROD_ROLE` +35 + `PROD_THEME` +20 是过 50 阈值的必要组合）。
   **`PRODUCTION_RND` 是罕见类型**，任何计划或验收清单都不要把「存在 PRODUCTION_RND 专家」
   当作可满足的前置条件。

## 一颗地雷：`${patentTitle}` 会让批量任务收件人恒为 0

`patentTitles` 在 `ExpertSearchService.ALLOWED_HAS_FIELDS`（`:35`）之内。链路是：

模板正文含 `${patentTitle}` → `mailComposeTemplateService.requiredEsFields(templateId)` 返回 `patentTitles`
→ `ManualInitialOutreachService.resolveScope:432-442` 放进 `scope.gateEsFields`
→ `ExpertSearchService.fieldPresenceFilters` 生成 `exists patentTitles AND NOT term ""`
→ **该任务收件人恒为 0，且无任何报错**。

同理 `index.html:570` 的「有专利」筛选 chip（`data-value="patentTitles"`）是死控件。

2026-08-25 时点：现存迁移与模板中 `${patentTitle}` 零使用，尚未爆雷。
如果将来运营在模板里加了这个变量而任务突然发不出去，先查这里。

关联：[[K-openalex-fetch-works-gated]]、[[K-raw-index-scoring-fields-sparse]]
