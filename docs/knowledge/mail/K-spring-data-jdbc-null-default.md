---
id: K-spring-data-jdbc-null-default
domain: mail
created: 2026-08-12
last_used: 2026-08-12
hit_count: 0
source: create-p:unsubscribe-token-created-at-null
severity: P1
---

Spring Data JDBC 的新增实体会把所有可写的非 ID 属性加入 INSERT；Kotlin nullable 属性值为 `null` 时也会绑定 SQL NULL。MySQL 的 `NOT NULL DEFAULT CURRENT_TIMESTAMP` 只在省略列时生效，不会覆盖显式 NULL。

`unsubscribe_token` 的唯一写路径是 `UnsubscribeTokenService.sign()`。首次签发必须传入非空 `createdAt`；仅用 Mockito mock `CrudRepository.save()` 不能验证这个数据库约束，需由 `@DataJdbcTest` + MySQL/Flyway 集成测试覆盖。
