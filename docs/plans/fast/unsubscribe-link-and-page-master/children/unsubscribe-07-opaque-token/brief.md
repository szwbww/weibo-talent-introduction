# Fast-P Child Brief — unsubscribe-07-opaque-token

- Master: docs/plans/2026-08-12/unsubscribe-link-and-page-master.md (sha256 29f401c80efaba9649fb720d8b2856d8dedc1b45956c36d5cd76eb7628108594)
- Child plan: docs/plans/2026-08-12/unsubscribe-07-opaque-token.md (sha256 33cf962a667a6993bc3b51ba5a64ff40e7ef360cfccda39134f40f50186cfd9e)
- Depends on: none
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/unsubscribe-link-and-page-master
- Branch: fast/unsubscribe-link-and-page-master
- Child base: 0482bcd497eefba9ce4f44f61a5624ae25d0efe1
- Global constraints: JDK 11 only (JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home); one writer at a time; commit message `feat(fast-p): implement unsubscribe-07-opaque-token`; skip linters/formatters; no project-wide test suite beyond the plan's required commands; do not touch files outside the plan's 变更文件清单.
- Downstream interfaces for later children: none (child 08 does not assume any token shape). Child 06 may have edited the same worktree first — do not amend its commits; base your work on the current HEAD.

The complete approved contract is the child plan below, verbatim.


# Plan 07 — 退订 token 改为不透明随机 id

> 主索引：[unsubscribe-link-and-page-master.md](unsubscribe-link-and-page-master.md)（共享证据 E-1、E-7、E-9 不在本文重复）
> 生成日期：2026-08-12 · create-p
> 子系统：① token 存储（domain / repository / service / 迁移） —— 共 1 个，符合上限

## 需求描述

**Observable outcome**

1. 新签发的退订 URL 形如 `https://<base>/u/unsubscribe?token=<43 字符 base64url 随机串>`，**不包含任何可解码出收件人邮箱的片段**。
2. URL 总长从当前 ~150+ 字符降到 ~<base-url 长度> + 55 字符左右。
3. 历史邮件里已发出的旧格式 token（`base64url(email).base64url(hmac)`）在过渡期内**仍可退订成功**。

**What must NOT change**

1. 一键退订 `POST /u/unsubscribe` 的语义、返回体（`"unsubscribed"` / `"invalid"`）与状态码（`UnsubscribeController.kt:21-26`）。
2. `EmailSuppressionService.suppress(email, ONE_CLICK, ...)` 的幂等语义与入参（`EmailSuppressionService.kt:25-42`）。
3. `List-Unsubscribe` / `List-Unsubscribe-Post` 两个头的产出条件与格式（`SmtpMailDeliveryService.kt:64-69`）。
4. 未配置退订地址时 `${unsubscribeUrl}` 仍退化为空串、邮件正常外发（`MailVariableService.kt:251-263`）。
5. `verify()` 对畸形输入不抛异常、只返回 null 的契约（`UnsubscribeTokenServiceTest.kt:50-60` 现有 4 条断言）。
6. `sign()` 对同一邮箱的大小写/空白差异归一化后产出**一致的 URL**（`UnsubscribeTokenServiceTest.kt:26-29`）。

**Out of scope**

- token 过期（`expires_at`）与密钥轮换。create-p 的"每个共享存储 ≤1 个新字段"规则要求单列 —— 新表本轮只带必需列，过期列后续计划再加。
- 旧 hmac 通道的下线时间点与清理迁移（运维决策，需先确认存量邮件的自然衰减周期）。
- 退订页样式 → Plan 08；正文链接形态 → Plan 06。
- 抑制名单的运营界面。

## 关键不变量

### Invariant I-1: token 值不携带可解码的收件人信息
- Rule：新签发 token 必须是与邮箱无统计关联的随机串；邮箱 ↔ token 的映射**只**存在于 `unsubscribe_token` 表。禁止把邮箱、其哈希、或任何由邮箱派生的值编码进 token。
- Applies to：`UnsubscribeTokenService.sign()`。
- Violation consequence：需求 observable 1 直接不成立；邮件被转发 / 经企业网关 / 进访问日志时泄露收件人地址。
- 来源：original

### Invariant I-2: 一个邮箱最多一条有效 token 记录，`sign()` 幂等
- Rule：`unsubscribe_token.email` 上有唯一约束；`sign(email)` 必须先查已有记录并复用其 token，只在不存在时插入。并发插入冲突（`DuplicateKeyException`）必须回读复用，不得向上抛。
- Applies to：`UnsubscribeTokenService.sign()`；`V89` 的 `UNIQUE KEY uk_email`。
- Violation consequence：每封邮件签发新 token → 表无限膨胀；且 must-NOT-change 第 6 条（同邮箱产出一致 URL）被破坏，同一专家不同批次邮件里的退订链接互不相同，运营对账困难。
- 来源：original（并发回读范式参考 `EmailSuppressionService.kt:38` 的 `catch (e: DuplicateKeyException)`（方法体 `:25-42`））

### Invariant I-3: `verify()` 双通道，新表优先、旧 hmac 兜底
- Rule：`verify(token)` 顺序为 ① 查 `unsubscribe_token` 表命中即返回其 email；② 未命中则走旧 hmac 校验；③ 都失败返回 null。旧通道必须保留 `MessageDigest.isEqual` 常量时间比较（`UnsubscribeTokenService.kt:30`）。
- Applies to：`UnsubscribeTokenService.verify()`；其唯一调用方 `UnsubscribeController.kt:23`、`:31`、`:37`。
- Violation consequence：顺序颠倒会让旧通道对新 token 做无谓的 base64 解码尝试（无功能影响但增加异常路径）；去掉旧通道则已发出的历史邮件退订链接全部失效。
- 来源：original

### Invariant I-4: 随机源与编码固定
- Rule：token 由 `java.security.SecureRandom` 产生 32 字节，经 `Base64.getUrlEncoder().withoutPadding()` 编码为 43 字符。禁止 `java.util.Random`、`UUID.randomUUID()`（仅 122 bit 且格式含 `-` 分段易被识别为 UUID）。
- Applies to：`UnsubscribeTokenService` 的 token 生成。
- Violation consequence：可预测 token → 攻击者可批量退订任意专家（拒绝服务式破坏外联链路）。
- 来源：original

### Invariant I-5: `enabled()` 的语义按通道分裂，且必须显式
- Rule：注入了 repository（生产装配）时 `enabled()` == `baseUrl.isNotBlank()`（不再要求 secret）；repository 为 null（测试兜底装配）时保持旧语义 `baseUrl.isNotBlank() && secret.isNotBlank()`。secret 为空且需要走旧 hmac 校验时，`verify()` 的旧通道必须**直接返回 null**，不得用空 key 做 HMAC。
- Applies to：`UnsubscribeTokenService.enabled()`、`verify()` 旧通道分支。
- Violation consequence：不分裂就会打破 `UnsubscribeTokenServiceTest.kt:63-67` 现有断言；空 key HMAC 会让"任意人构造的 token"在密钥未配置的环境里通过校验。
- 来源：original

### Invariant I-6: 生产分支必须被非空 repository 的测试覆盖
- Rule：repository 可空是为收敛测试改面（主索引 E-7），但**生产恒为非空**。因此 `sign()` / `verify()` / `enabled()` 的每条断言都必须有一份用**非空（mock 或 fake）repository** 装配的用例；不得只测 null 分支。
- Applies to：`UnsubscribeTokenServiceTest.kt`。
- Violation consequence：只测 fallback 分支会漏掉线上独有路径 —— 该陷阱在本仓库已发生过一次。
- 来源：K-compose-template-html-after-render（测试陷阱段）

### Invariant I-7: 迁移只建表不回填
- Rule：`V89` 只 `CREATE TABLE`，**不**为历史专家预生成 token。历史链接由 I-3 的旧通道兜底。
- Applies to：`V89__create_unsubscribe_token.sql`。
- Violation consequence：回填需要遍历 ES 全量专家，属跨存储的重操作，且回填出的 token 与历史邮件里的链接对不上，毫无收益。
- 来源：original

## 现状审计

> Step 1b-fe **未触发**：变更文件清单中无 `src/main/resources/static` 下文件，也无 `.html` / `.css` / 前端 `.js`。故本计划无 `## 样式契约` 节。

### `unsubscribe_token`（本计划新建表）

- Schema：见 T-1 的建表 SQL。参照同域最近表 `email_suppression`（`V30__create_email_suppression.sql`）的列型约定：`email VARCHAR(320)`、`created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`、`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`。
- Write paths（改动后全集）：
  1. `UnsubscribeTokenService.sign()` — 首次为某邮箱签发时 INSERT。**唯一写路径。**
- Read paths（改动后全集）：
  1. `UnsubscribeTokenService.sign()` — 按 email 查已有 token（I-2 幂等）。
  2. `UnsubscribeTokenService.verify()` — 按 token 查 email（I-3 通道 ①）。
- Interaction points：写路径 1 × 读路径 2 —— 签发与校验必须对同一张表、同一列做归一化一致的匹配（email 均经 `normalize` 后存/查）。

### `UnsubscribeTokenService`（现状逐行）

`src/main/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenService.kt`，全文 50 行：

| 行 | 内容 | 本计划处置 |
|---|---|---|
| `:12-14` | 构造：`(private val properties: UnsubscribeProperties)` | 尾部追加 `repository: UnsubscribeTokenRepository? = null` |
| `:15` | `enabled() = baseUrl.isNotBlank() && secret.isNotBlank()` | 按 I-5 分裂 |
| `:17-21` | `sign(email)` = `enc(n) + "." + enc(mac)` | 重写为查表/插表 |
| `:24-35` | `verify(token)` 拆两段 + `MessageDigest.isEqual` | 前置新表查询，旧逻辑降级为 `verifyLegacy()` 私有方法 |
| `:37-38` | `unsubscribeUrl(email)` = `baseUrl.trimEnd('/') + "/u/unsubscribe?token=" + sign(email)` | **不改** |
| `:40-43` | `hmac(data)` | 保留，供 `verifyLegacy()` 使用 |
| `:45-49` | `enc` / `dec` 三个私有方法 | 保留 |

- 生产注入方（`grep -rn "unsubscribeTokenService" src/main/kotlin`）：`MailVariableService.kt:112`（可空构造参数）、`SmtpMailDeliveryService.kt:13`（必填）。两者都只调用 `enabled()` / `unsubscribeUrl()`，**不直接调用 `sign()` / `verify()`**，故本计划对它们零改动。
- 测试构造点 9 处，见主索引 E-7。采用可空默认参数后，其中 **8 处零改动**，只有 `UnsubscribeTokenServiceTest.kt` 需要扩写。

### `UnsubscribeController`（唯一 `verify()` 调用方）

- `:23` POST 一键退订、`:31` GET 确认页、`:37` POST 确认。三处都是 `tokenService.verify(token) ?: return <400>`。
- 本计划对该文件**零改动** —— `verify()` 的签名与 null 语义不变。（Plan 08 会改这个文件的 HTML 渲染部分，两计划在此文件上无 diff 重叠：Plan 07 不碰，Plan 08 不碰 verify 调用。）

### `EmailSuppression`（同域 Spring Data JDBC 范式参照，本计划不改）

- domain：`EmailSuppression.kt` — `@Table("email_suppression")` + `@Id val id: Long? = null` + `createdAt: LocalDateTime? = null`。
- repository：`EmailSuppressionRepository.kt` — `CrudRepository<EmailSuppression, Long>` + 派生查询 `existsByEmail` / `findByEmail` + `@Query` 手写 SQL。
- 新建的 domain/repository 逐字沿用这套形状。

## 实现方案

### 阶段 1 — 存储（I-1、I-2、I-7）

**T-1** 新建 `src/main/resources/db/migration/V89__create_unsubscribe_token.sql`：

```sql
-- V89: 退订 token 的不透明随机 id 存储。
-- 旧格式 token（base64url(email).base64url(hmac)）把收件人邮箱明文编码进 URL，
-- 本表把映射搬到服务端，URL 只留随机串（Plan 07 I-1）。
-- uk_email 保证一个邮箱最多一条记录，sign() 复用（I-2）。
-- uk_token 保证校验查询走唯一索引。
-- 不回填历史数据：历史邮件里的旧 token 由 verifyLegacy() 兜底（I-7）。

CREATE TABLE unsubscribe_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(320) NOT NULL COMMENT '归一化邮箱(小写trim)',
    token VARCHAR(64) NOT NULL COMMENT '不透明随机 id：SecureRandom 32 字节的 base64url 无填充编码，43 字符',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**T-2** 新建 `src/main/kotlin/com/weibo/talentintroduction/mail/domain/UnsubscribeToken.kt`：

```kotlin
package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("unsubscribe_token")
data class UnsubscribeToken(
    @Id val id: Long? = null,
    val email: String,
    val token: String,
    val createdAt: LocalDateTime? = null
)
```

**T-3** 新建 `src/main/kotlin/com/weibo/talentintroduction/mail/repository/UnsubscribeTokenRepository.kt`：

```kotlin
package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.UnsubscribeToken
import org.springframework.data.repository.CrudRepository

interface UnsubscribeTokenRepository : CrudRepository<UnsubscribeToken, Long> {
    fun findByEmail(email: String): UnsubscribeToken?

    fun findByToken(token: String): UnsubscribeToken?
}
```

### 阶段 2 — 服务（I-1 ~ I-6）

**T-4** 改 `UnsubscribeTokenService.kt`：

1. 构造函数尾部追加 `private val repository: UnsubscribeTokenRepository? = null`（收敛测试改面，见现状审计）。
2. `enabled()` 按 I-5 分裂：

```kotlin
fun enabled(): Boolean =
    if (repository != null) properties.baseUrl.isNotBlank()
    else properties.baseUrl.isNotBlank() && properties.secret.isNotBlank()
```

3. `sign(email)` 重写（I-1、I-2、I-4）：

```kotlin
fun sign(email: String): String {
    val n = email.trim().lowercase(Locale.ROOT)
    val repo = repository ?: return legacySign(n)
    repo.findByEmail(n)?.let { return it.token }
    val token = newToken()
    return try {
        repo.save(UnsubscribeToken(email = n, token = token)).token
    } catch (e: DuplicateKeyException) {
        repo.findByEmail(n)?.token ?: throw e
    }
}

private fun newToken(): String {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun legacySign(normalizedEmail: String): String =
    enc(normalizedEmail) + "." + enc(hmac(normalizedEmail))
```

`secureRandom` 为实例字段 `private val secureRandom = SecureRandom()`（`SecureRandom` 线程安全，单例复用即可）。

4. `verify(token)` 按 I-3 改为：

```kotlin
fun verify(token: String): String? {
    repository?.findByToken(token)?.let { return it.email }
    return verifyLegacy(token)
}

private fun verifyLegacy(token: String): String? {
    if (properties.secret.isBlank()) return null          // I-5：禁止空 key HMAC
    val parts = token.split(".")
    if (parts.size != 2) return null
    return try {
        val email = String(dec(parts[0]), Charsets.UTF_8)
        val expected = enc(hmac(email))
        if (!MessageDigest.isEqual(expected.toByteArray(), parts[1].toByteArray())) return null
        email
    } catch (_: IllegalArgumentException) {
        null
    }
}
```

`unsubscribeUrl()`（`:37-38`）、`hmac()`、`enc()`、`dec()` 保持逐字不变。

### 阶段 3 — 测试（I-6）

**T-5** 扩写 `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenServiceTest.kt`。现有 8 个用例**全部保留**（它们构造时不传 repository，覆盖 legacy 分支），新增一组用非空 repository 的用例：

- fake repository（内存 map 实现 `UnsubscribeTokenRepository`，或 Mockito mock）装配下：
  - `sign()` 产出 43 字符、只含 `A-Za-z0-9-_` 的串；`Base64.getUrlDecoder().decode(token)` 长度为 32。
  - `sign()` **不含** `.`；对 token 做 `Base64.getUrlDecoder().decode` 后转 UTF-8 字符串**不含** `@`（I-1 的可断言形式）。
  - 同一邮箱两次 `sign()` 返回**同一 token**，且 repository 只发生一次 save（I-2）。
  - `sign("A@X.com")` 与 `sign("a@x.com ")` 返回同一 token（must-NOT-change 6）。
  - `verify(sign(email))` 回到归一化邮箱（通道 ①）。
  - `verify(<legacy token>)` 仍返回邮箱（通道 ②，I-3）。
  - `verify("unknown-random")` 返回 null，且不抛异常。
  - repository 抛 `DuplicateKeyException` 时 `sign()` 回读并返回已有 token（I-2 并发分支）。
  - `enabled()`：repository 非空 + secret 为空 → true；repository 为 null + secret 为空 → false（I-5）。
  - secret 为空 + repository 非空时，`verify(<legacy token>)` 返回 null（I-5 后半）。

**T-6** 新建 `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenMigrationTest.kt`（文本断言范式，主索引 E-9）：

- 读 `V89__create_unsubscribe_token.sql`。
- 断言含 `CREATE TABLE unsubscribe_token`、`UNIQUE KEY uk_email`、`UNIQUE KEY uk_token`、`VARCHAR(320)`、`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`。
- 断言去注释后**不含** `INSERT`（I-7：不回填）。
- 断言去注释后**不含** `${`（避免重蹈 Flyway 占位符覆辙，K-flyway-placeholder-replacement）。

## 变更文件清单

| # | 文件 | 类型 | 改动 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V89__create_unsubscribe_token.sql` | 新建迁移 | 建表 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/UnsubscribeToken.kt` | 新建主代码 | domain |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/UnsubscribeTokenRepository.kt` | 新建主代码 | repository |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenService.kt` | 主代码 | 构造/enabled/sign/verify 改写 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenServiceTest.kt` | 测试 | T-5 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenMigrationTest.kt` | 新建测试 | T-6 |

合计 6 个文件 ≤ 10 ✅；子系统 1 个 ✅；新建存储的新字段数不适用（新表，非既有共享存储加字段）✅。

**未列入清单即视为不得改动**，特别是：`UnsubscribeController.kt`、`MailVariableService.kt`、`SmtpMailDeliveryService.kt`、`application.yml`、以及主索引 E-7 里另外 8 个构造 `UnsubscribeTokenService(` 的测试文件。若执行时发现它们必须改，说明可空默认参数的方案未落实，回到本计划修订而不是扩大范围。

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。以下命令可原样复制到终端执行。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribeTokenServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribeTokenMigrationTest

# 未列入清单但必须保持通过的关联测试（证明零改动面未被波及）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribeControllerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribeControllerIllegalTokenTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailVariableServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=SmtpMailDeliveryServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualExpertMailServiceGateTest

# 空库全量迁移（需本机 Docker；默认跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，且输出含 `Tests run: N, Failures: 0, Errors: 0`（`mvn test`）／`BUILD SUCCESS`（`mvn clean package`）；`git diff --check` 无输出。
来源：`CLAUDE.md` 的「Commands」章节与项目元信息 `test_command` / `build_command`。

## 验收标准

- **I-1**：T-5 的"解码后不含 `@`"与"不含 `.`"用例通过；`grep -n "enc(n)" src/main/kotlin/.../UnsubscribeTokenService.kt` 只出现在 `legacySign` 内。
- **I-2**：T-5 的幂等用例与 `DuplicateKeyException` 回读用例通过；T-6 断言 `uk_email` 存在。
- **I-3**：T-5 的双通道用例通过；人工核对 `verify()` 方法体首行即 `repository?.findByToken(...)`。
- **I-4**：`grep -n "SecureRandom" src/main/kotlin/.../UnsubscribeTokenService.kt` 命中；`grep -n "UUID\|java.util.Random" src/main/kotlin/.../UnsubscribeTokenService.kt` 为 0 行；T-5 断言解码后字节长度 32。
- **I-5**：T-5 的四条 `enabled()` / 空 secret 用例通过；`UnsubscribeTokenServiceTest.kt:63-67` 三条**原有**断言仍在且通过。
- **I-6**：T-5 中用非空 repository 装配的用例数 ≥ 8；`grep -c "UnsubscribeTokenService(properties, " src/test/.../UnsubscribeTokenServiceTest.kt` ≥ 1。
- **I-7**：T-6 的"不含 INSERT"用例通过。
- **变更面**：`git diff --stat` 列出的文件必须与「变更文件清单」逐一对应，多一个即视为超范围。
- **回归**：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 新发邮件的退订 URL 不含邮箱、且明显变短
- 前置条件：`UNSUBSCRIBE_BASE_URL` 已配置；`V89` 已在目标库执行；存在可发信的专家与 enabled 发件账号。
- 操作步骤：
  1. 发一封 INTRODUCTION 到测试邮箱。
  2. 复制邮件里退订链接的完整 URL（HTML 版可右键复制链接地址，或看「显示原始邮件」的 text/plain 部件）。
  3. 把 `token=` 后面的串粘到任意 base64 解码工具里解一次。
- 预期结果：① `token=` 后是 43 个字符、只含大小写字母、数字、`-`、`_`，**不含 `.`**；② base64 解码结果是乱码二进制，**看不到收件人邮箱或任何可读文本**；③ 整条 URL 长度 ≈ base-url 长度 + 55，明显短于改造前（改造前 ~150+ 字符，可与历史邮件对比）。
- 覆盖：需求描述 observable 1、2；I-1、I-4

### A-2: 同一专家的退订链接在多封邮件间保持一致
- 前置条件：同 A-1。
- 操作步骤：给**同一个**测试邮箱先后发两封邮件（一封 INTRODUCTION、一封 MATERIAL_REMINDER），分别复制两条退订 URL。
- 预期结果：两条 URL **完全相同**；数据库 `SELECT COUNT(*) FROM unsubscribe_token WHERE email = '<该邮箱>'` 返回 `1`。
- 覆盖：must-NOT-change 6；I-2

### A-3: 新 token 可正常退订
- 前置条件：A-1 收到的邮件；该邮箱当前**不在**抑制名单。
- 操作步骤：点击退订链接 → 在页面上点确认 → 后台「退订名单」搜索该邮箱。
- 预期结果：页面显示已退订；后台名单中出现该邮箱，来源列为 `ONE_CLICK`。再次点同一链接并确认，不报错、名单里仍只有一条记录。
- 覆盖：must-NOT-change 1、2

### A-4: 历史旧格式链接仍可退订（回归，本计划最关键的一条）
- 前置条件：手上有一封**改造前**发出的历史邮件（如 Gmail 里那封截图邮件），其 token 形如 `bGli….3LYe…`（含一个 `.`）；该邮箱不在抑制名单（如已在，先在后台移除）。
- 操作步骤：直接在浏览器打开那条历史 URL → 点确认。
- 预期结果：正常显示确认页并成功退订，**不出现** `invalid link`；后台名单出现该邮箱。
- 覆盖：需求描述 observable 3；I-3

### A-5: 伪造 token 被拒（回归）
- 前置条件：无。
- 操作步骤：浏览器打开 `<base-url>/u/unsubscribe?token=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa`（43 个 a）；再打开 `<base-url>/u/unsubscribe?token=%%%.abc`。
- 预期结果：两次都返回 HTTP 400、页面内容为 `invalid link`；不产生任何抑制记录（`SELECT COUNT(*) FROM email_suppression` 前后不变）；后端日志无异常堆栈。
- 覆盖：must-NOT-change 5；I-3

### A-6: 未配置退订地址的环境不受影响（回归）
- 前置条件：一套 `UNSUBSCRIBE_BASE_URL` 为空的环境。
- 操作步骤：启动应用 → 发一封 INTRODUCTION。
- 预期结果：应用正常启动无报错；邮件正常送达；正文退订行的 URL 位置为空；邮件头**不含** `List-Unsubscribe`；`unsubscribe_token` 表**无新增行**。
- 覆盖：must-NOT-change 3、4；I-5
