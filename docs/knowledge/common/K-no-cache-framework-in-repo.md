---
id: K-no-cache-framework-in-repo
domain: common
created: 2026-08-26
last_used: 2026-08-26
hit_count: 0
source: create-p:01-llm-fact-retrieval
---

# 本仓没有任何缓存框架——要缓存只能手写

`grep -rn "@Cacheable\|@CacheEvict\|@EnableCaching\|CacheManager\|Caffeine" src/main/kotlin pom.xml src/main/resources` 零命中；
`pom.xml` 无 `spring-boot-starter-cache` / caffeine / ehcache / redis。

`llm` / `qa` 模块内的两处 `ConcurrentHashMap` **都不是缓存**：
`AiReplyDraftService.kt:77`（取消监听器登记）、`AiReplyGenerationCoordinator.kt:29`（在途 SSE 生成登记）。
`QaFactSelectionService.kt:29` 每次调用都重新 `qaRuleRepository.findAllEnabledOrdered()`。

仓内仅有的"缓存"是一张 DB 表：`V21__add_email_validation_cache.sql` +
`expert/domain/EmailValidationCache.kt:7-22`（带 `expiresAt` 与 `isExpired()`），
TTL 走 `application.yml:143` 的 `cache-ttl-days`。

正确做法：需要缓存时只有两条路——(i) 服务字段上的 `ConcurrentHashMap`（进程内、无淘汰，
必须自带容量上限与清空策略）；(ii) 新建 `@Table` + `expiresAt` 表并配 Flyway 迁移。
**不要在计划里假设 `@Cacheable` 可用。**
