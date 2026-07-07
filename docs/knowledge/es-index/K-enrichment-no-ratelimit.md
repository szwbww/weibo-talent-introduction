---
id: K-enrichment-no-ratelimit
domain: discovery
created: 2026-07-07
last_used: 2026-07-07
hit_count: 2
source: create-p:enrichment-ratelimit-fix
---

`ExpertDiscoveryService.enrichExistingExperts()` 对 OpenAlex 的调用路径与 `discoverFromSource()` 不同：discovery 路径有 circuit breaker（429/503 退避+熔断），enrichment 路径没有。`OpenAlexDataSource.enrichAuthor/fetchRecentWorks/fetchPatents` 内部 catch 所有异常返回 null，会吞掉 429 限流信号。每次 enrichment 最多产生 4 次 API 调用（search + author + works + patents），大批量执行时极易触发 OpenAlex polite pool 限额（1000 req/5min）。任何扩展 enrichment 逻辑时须确保限流信号能传播到业务循环层。
