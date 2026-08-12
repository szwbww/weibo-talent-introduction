## 需求描述

首次向任意有邮箱的专家发送手工介绍邮件时，系统可成功持久化新的退订 token 并继续发送邮件。

不得改变：既有 token 的按邮箱复用、并发重复键回读、旧 HMAC token 校验、`unsubscribe_token` 表结构与退订 URL 格式。

范围外：修改既有迁移、回填历史 token、调整手工邮件/SMTP 的调用顺序或模板内容。

## 关键不变量

### Invariant I-1: 首次 token 写入显式时间
- Rule: `UnsubscribeTokenService.sign()` 首次插入记录时必须传入非空 `createdAt`；不得依赖 MySQL 的 `DEFAULT CURRENT_TIMESTAMP` 来覆盖 Spring Data JDBC 显式绑定的 NULL。
- Applies to: `UnsubscribeTokenService.sign()` 唯一 INSERT 路径。
- Violation consequence: 新邮箱首次发信在 SMTP 前因 `unsubscribe_token.created_at` 的 NOT NULL 约束失败。
- 来源: K-spring-data-jdbc-null-default

### Invariant I-2: token 现有语义不变
- Rule: 同一归一化邮箱仍复用已有 token；发生重复键时仍回读 token；新 token 仍为 43 位随机 base64url；旧 HMAC token 仍可校验。
- Applies to: `UnsubscribeTokenService.sign()`、`verify()`。
- Violation consequence: 退订链接失效、并发首次发信失败或兼容性回归。
- 来源: K-unsubscribe-token-plaintext-email

### Invariant I-3: 持久化路径有真实 MySQL 覆盖
- Rule: 集成测试必须经 Spring Data JDBC、Flyway V89 与 MySQL 执行 `sign()` 的首次写入，并断言返回 token 和表中非空 `created_at`。
- Applies to: 新增 `UnsubscribeTokenServiceJdbcIT`。
- Violation consequence: Mockito 测试会遗漏 SQL NULL 与数据库默认值的交互问题。
- 来源: K-spring-data-jdbc-null-default

## 现状审计

### `unsubscribe_token`
- Schema/mapping: `V89__create_unsubscribe_token.sql` 定义 `id` 自增、`email`/`token` 唯一、`created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`；`UnsubscribeToken.createdAt` 是可写 nullable 属性。
- Write paths:
  1. `UnsubscribeTokenService.sign()` — 查无已有邮箱 token 时 `repository.save(UnsubscribeToken(...))`；当前未赋 `createdAt`。
  2. `V89__create_unsubscribe_token.sql` — 只建表，不回填、不写业务记录。
- Read paths:
  1. `UnsubscribeTokenService.sign()` — `findByEmail()` 复用 token。
  2. `UnsubscribeTokenService.verify()` — `findByToken()` 查询邮箱，未命中才校验旧 token。
  3. `UnsubscribeController` — 调用 `verify()` 处理退订请求。
- Interaction points: `MailVariableService.buildVariables()` 在手工邮件渲染中调用 `unsubscribeUrl()`，继而首次进入 `sign()` 写表（来源: K-unsubscribe-variable-injection-sites）。写入失败发生在 SMTP 发送前。

### 测试基础设施
- `UnsubscribeTokenServiceTest` 的 repository 是 Mockito mock，`save()` 仅返回参数，不生成 JDBC INSERT。
- 现有 MySQL/Flyway 集成测试使用 `@DataJdbcTest`、`MySQLContainer`、`-Pmigration-it`；新增测试复用该模式。

## 实现方案

### T-1: 为首次 INSERT 提供创建时间
- 不变量: I-1、I-2。
- 修改文件: `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenService.kt`。
- 在 `sign()` 的 `UnsubscribeToken` 构造处传入 `createdAt = LocalDateTime.now()`；仅影响查无现有 token 的新增记录，不改查找、冲突处理或校验分支。

### T-2: 覆盖真实 JDBC/MySQL 首次签发
- 不变量: I-1、I-2、I-3。
- 修改文件: `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenServiceJdbcIT.kt`。
- 使用 `@DataJdbcTest` + Testcontainers MySQL + Flyway；以真实 `UnsubscribeTokenRepository` 构造服务并调用 `sign()`，断言 token 长度为 43、可被 `verify()` 解析、持久化记录的 `createdAt` 非空。测试前清空 `unsubscribe_token`，不修改任何其他业务表。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenService.kt` | 首次 token 写入传入创建时间 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenServiceJdbcIT.kt` | 新增真实 MySQL/JDBC 回归测试 |

## 验收标准

- I-1: `sign()` 构造的新 `UnsubscribeToken.createdAt` 非空；首次邮件不再抛出 `InsertRoot`。
- I-2: `UnsubscribeTokenServiceTest` 全部通过；重复 `sign()` 仍只保存一次，旧格式 token 仍可校验。
- I-3: `mvn -Pmigration-it test -Dtest=UnsubscribeTokenServiceJdbcIT` 通过；MySQL 表记录的 `created_at` 非空。
- 质量: `git diff --check` 无输出；仅变更清单内的产品/测试文件，计划与知识文件除外。

## 人工验收清单

### A-1: 新专家手工发送介绍邮件
- 前置条件: 测试环境已部署本修复；存在一名有有效邮箱、尚无对应 `unsubscribe_token` 记录的专家；退订基础 URL 已配置。
- 操作步骤:
  1. 在专家详情选择介绍邮件模板。
  2. 点击发送。
  3. 查询该邮箱的 `unsubscribe_token` 记录。
- 预期结果: 页面返回发送成功；不显示 `Failed to execute InsertRoot`；表中恰有一条该邮箱记录，`token` 长度 43，`created_at` 非空。
- 覆盖: I-1、I-3、需求描述。

### A-2: 同一专家再次发送
- 前置条件: 已完成 A-1。
- 操作步骤:
  1. 再次向同一专家发送一封允许发送的邮件。
  2. 查询该邮箱的 `unsubscribe_token` 记录。
- 预期结果: 发送成功；该邮箱仍只有一条 token 记录，token 值不变。
- 覆盖: I-2、需求描述的“不得改变”。

### A-3: 历史退订链接
- 前置条件: 保存一条旧 HMAC 格式的历史退订链接，且测试环境配置旧 HMAC 密钥。
- 操作步骤:
  1. 访问该链接并确认退订。
- 预期结果: 页面显示退订成功，邮箱进入抑制名单。
- 覆盖: I-2、需求描述的“不得改变”。
