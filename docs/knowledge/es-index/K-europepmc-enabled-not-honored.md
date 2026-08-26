---
id: K-europepmc-enabled-not-honored
domain: es-index
created: 2026-08-25
last_used: 2026-08-25
hit_count: 0
source: create-p:04-discovery-subject-scope
severity: P1
---

`EUROPE_PMC_ENABLED=false` **关不掉 Europe PMC**：页面显示为灰，定时发现照跑满配额。

## 不对称的证据

七个数据源里，六个带 `@ConditionalOnProperty(..., name=["enabled"], havingValue="true")`，
false 时 Bean 不创建：`CoreDataSource.kt:21`、`ArxivDataSource.kt:18`、`PmcOaDataSource.kt:18`、
`OpenAlexDataSource.kt:19`、`CrossrefDataSource.kt:17`、`OrcidDataSource.kt:17`。

`EuropePmcDataSource.kt:19` 是**裸 `@Service`，无任何 Conditional**，且被
`ExpertDiscoveryService.kt:50` 作为普通依赖直接注入（其余源都走 `ObjectProvider`）。
`resolveEnabledSources:209` 写的是 `add({ europePmc }, europePmc.sourceName)` ——
**从不读 `europePmcProperties.enabled`**。

## 为什么手动路径侥幸躲过、定时路径必中

`resolveEnabledSources:204-207` 的条件是
`src != null && (criteria.sources.isEmpty() || criteria.sources.contains(name))`。

- 手动发现：`app.js` 的 `getSelectedSources` 用 `:checked:not([disabled])` 过滤，
  灰掉的 EUROPE_PMC 不会回传 → `criteria.sources` 非空且不含它 → 被排除。
- **定时发现**：`ExpertDiscoveryScheduler:40-43` 传 `PaperSearchCriteria(excludeCountries=["CN"], openAccessOnly=true)`，
  `sources` 为空列表 → `isEmpty()` 恒真 → Europe PMC 照加，跑满 `EUROPE_PMC_MAX_PAPERS`（默认 1500）。

而 `ExpertDiscoveryController.getAvailableSources:50-64` 对 Europe PMC **读的是**
`europePmcProperties.enabled`（其余源读 `getIfAvailable() != null`）——所以页面和实际行为直接矛盾。

## 修法

改动小的那个：在 `resolveEnabledSources` 里显式判 `europePmcProperties.enabled`（需注入该 properties）。
补 `@ConditionalOnProperty` 也可以，但要连带把直接注入改成 `ObjectProvider`，牵动构造函数与全部测试。

关联：[[K-openalex-fetch-works-gated]]
