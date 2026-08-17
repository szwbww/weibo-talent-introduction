---
id: K-spring-data-jdbc-null-default
domain: mail
created: 2026-08-12
last_used: 2026-08-16
hit_count: 2
source: create-p:unsubscribe-token-created-at-null
severity: P1
---

Spring Data JDBC 的新增实体会把所有可写的非 ID 属性加入 INSERT；Kotlin nullable 属性值为 `null` 时也会绑定 SQL NULL。MySQL 的 `NOT NULL DEFAULT CURRENT_TIMESTAMP` 只在省略列时生效，不会覆盖显式 NULL。

`unsubscribe_token` 的唯一写路径是 `UnsubscribeTokenService.sign()`。首次签发必须传入非空 `createdAt`；仅用 Mockito mock `CrudRepository.save()` 不能验证这个数据库约束，需由 `@DataJdbcTest` + MySQL/Flyway 集成测试覆盖。

## 推论（2026-08-14，P1 片段命名计划 I-2 依据）

同一机制在 UPDATE 侧的含义：`update()` 走 `existing.copy(...)` 时，`updated_at` 的旧值被**显式写回**，MySQL 的 `ON UPDATE CURRENT_TIMESTAMP` 在列被显式赋值时不触发。因此仅改 `name`（或其他经 `copy()` 的字段）不会改变 `updated_at`——这是"改名不动 frameVersion"（`frameSlotIdentity` 读 `updatedAt`）的成立前提。

反向警示：`reply_snippet.updated_at` 实际停在创建时刻（或显式传入新值的时刻）。若将来有人依赖它做"最近修改"排序或缓存失效，会静默失效。本轮不修，作为已知事实记录。
