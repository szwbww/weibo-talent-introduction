---
id: K-agg-filter-source-of-truth
domain: expert
created: 2026-06-29
last_used: 2026-06-29
hit_count: 0
source: create-p:expert-filter-counts-cross-linkage
---

# 专家列表 filter 构建是单一权威，聚合必须复用

`ExpertSearchService.searchExperts` 内联构建 ES `bool.filter`（tag term / `operatorStatus` 含 `NOT_CONTACTED` 经 `notContactedWithEmailFilters` 展开 / `emailDomain` wildcard / `region` 经 `regionFilter`）。这是列表筛选口径的唯一权威来源。

两个聚合接口 `aggregateEmailDomains` / `aggregateRegions` 原本用 `match_all` / `exists email`，**完全忽略筛选**，导致下拉里的 `(N)` 与列表实际命中数脱节。任何需要「数量随筛选联动」的需求，都应抽取共享 `buildExpertFilters(tag, operatorStatus, emailDomain, region)` 让列表与聚合同源，而非在聚合里另写一套。

互斥联动口径：某下拉的计数要排除它自身的筛选维度（服务商聚合不应用 emailDomain，地区聚合不应用 region），否则一旦选中该维度，其余选项计数全归 0。

注意 `loadEmailProviders` 同时喂「列表筛选下拉 `#expertEmailDomainFilter`」和「批量发送配置下拉 `#batchSendEmailDomain`」—— 后者必须保持全量，不能被列表筛选联动改写。
