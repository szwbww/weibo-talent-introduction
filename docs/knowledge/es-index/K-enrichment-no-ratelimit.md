---
id: K-enrichment-no-ratelimit
domain: discovery
created: 2026-07-07
last_used: 2026-07-08
hit_count: 4
source: create-p:enrichment-cross-day-resilient-run
---

（2026-07-07 复核修订）enrichment 路径的限流信号已能传播：`OpenAlexDataSource.batchEnrichByOrcids` 对 429/503 返回 `EnrichmentOutcome.RateLimited(retryAfterMs)`，`enrichExistingExperts` 有退避+熔断。但历史实现存在缺陷模式，扩展时须避免复现：① 整批限流被计入 failed 并跳批（不重试）；② 连续 5 次熔断退出导致长任务无法完成；③ 退避封顶 60s 扛不住长限流窗口。OpenAlex 限额有两层：10 万请求/天 + polite pool 约 1000 req/5min 窗口；批量 enrichment（50 ORCID/请求）请求总量小，触发限流时应优先怀疑窗口级限制并遵守 Retry-After。
