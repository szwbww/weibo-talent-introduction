---
id: K-no-cache-framework-in-repo
domain: common
created: 2026-10-22
last_used: 2026-10-27
hit_count: 5
source: create-p:01-llm-fact-retrieval
last_source: fix-v:2026-10-26-recheck-repair:initial-pass
---

# 本仓未配置缓存框架；缓存设计须显式声明容量与生命周期

2026-10-22复核：`rg -n 'Cacheable|CacheEvict|EnableCaching|CacheManager|Caffeine|spring-boot-starter-cache|caffeine|ehcache|redis' pom.xml src/main/kotlin src/main/resources` 零命中。不能据此假定可直接使用 `@Cacheable`；引入框架需单列依赖与配置范围。

`llm` / `qa` 模块内的两处 `ConcurrentHashMap` **都不是缓存**：
`AiReplyDraftService.kt:77`（取消监听器登记）、`AiReplyGenerationCoordinator.kt:29`（在途 SSE 生成登记）。
`QaFactSelectionService.kt:29` 每次调用都重新 `qaRuleRepository.findAllEnabledOrdered()`。

已知持久缓存示例：`V21__add_email_validation_cache.sql` +
`expert/domain/EmailValidationCache.kt:7-22`（带 `expiresAt` 与 `isExpired()`），
TTL 走 `application.yml:143` 的 `cache-ttl-days`。

设计选择包括有界进程内缓存或带TTL的持久化表，但并非只有这两种实现。`ConcurrentHashMap` 本身不提供淘汰/刷新语义，需显式规定容量、并发single-flight、失败保留旧值、时间与重启行为；LRU也可用受锁保护的LinkedHashMap。上述两处Map用途为历史局部事实，不代表当前仓库所有Map或缓存的穷举。

纠正旧版“仓内仅有一种缓存/只有两条路”的过度结论。复核来源：create-p:batch-demand-master；统计缓存不得反向控制发送额度。
