---
id: K-scroll-5m-keepalive
domain: es-index
created: 2026-07-07
last_used: 2026-07-08
hit_count: 1
source: create-p:enrichment-cross-day-resilient-run
---

`ExpertSearchService.scrollExperts / scrollExpertsFiltered` 的 ES scroll keepalive 固定 5m。handler 回调内任何可能超过 5 分钟的阻塞（限流退避、长睡眠、外部 API 等待）都会使 scroll 上下文过期，下一次 `_search/scroll` 直接失败。需要长等待的遍历任务必须改用 `search_after`（按 `orcidId` keyword asc 排序的无状态分页），不得使用 scroll。
