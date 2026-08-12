---
id: K-region-constant-not-display-label
domain: expert
created: 2026-08-12
last_used: 2026-08-12
hit_count: 0
source: create-p:batch-send-rhythm-and-filter-00-master
severity: P1
---

「地区」不是自由文本，是 `CountryContinentMapping`（`expert/domain/CountryContinentMapping.kt`）
定义的 9 个固定大区英文常量：`China` / `Asia (Japan & Korea)` / `Asia (Other)` / `Europe` /
`North America` / `South America` / `Africa` / `Oceania` / `Other`（`REGION_ORDER`，`:16-26`）。

这些英文串是**领域值**，参与 ES 查询构造：
`toRegion(country)`（`:254`）把专家的 `country` 归一到大区，
`countriesForRegion(region)`（`:263`）反查该大区的全部国家名/二字码，
再经 `esTermVariants()`（`:274`）展开大小写与首字母大写变体喂给 ES `terms`。
`aggregateRegions()`（`ExpertSearchService.kt:704`）也用 `allRegions()` 作为聚合桶的键。

因此「地区下拉显示中文」这类需求**只能改显示标签**：前端维护
`region 英文常量 → 中文文案` 的映射表用于 `option.textContent`，
`option.value`、API query param、DB 存值、ES term 值必须保持英文原串。
把中文串写进 `value` 会让 `countriesForRegion()` 返回空集，
筛选静默命中 0 条且不报错。

`REGION_OTHER` 还有一条特殊性：`countriesForRegion(REGION_OTHER)` 显式返回
`emptySet()`（`:264`），它是「未映射到任何已知大区」的兜底桶，
不能按「有对应国家列表」的方式处理。

关联：[[K-agg-filter-source-of-truth]]、[[K-filter-option-scope-parity]]
