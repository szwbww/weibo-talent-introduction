# 子计划 01：退订抑制名单核心 + 外发过滤 + 入站捕获

> 主计划：`2026-06-20-unsubscribe-suppression-00-master.md`。共享不变量 G-1..G-4 见主计划。

## 需求描述

- 可观察结果：
  1. 存在抑制名单存储；服务可幂等加入邮箱、可查询邮箱是否被抑制（归一化）。
  2. 自动批量外发（`InitialOutreachService`）与人工批量外发（`ManualInitialOutreachService`）在发送前跳过被抑制邮箱，计入"已跳过"，不创建联系记录、不发送。
  3. 收件人回复中含明确退订措辞时，其邮箱被自动加入抑制名单（来源标记 `INBOUND_REPLY`）。
- 必须不变：现有 `AutoMailReplyService` 对各意图的处理与状态流转结果不变；退订捕获是附加动作。批量外发既有的"已存在联系记录则跳过"逻辑不变。
- 不做：邮件头改造、一键端点（子计划 02）；前端管理页；会话内回复的发送拦截。

## 关键不变量（引用 + 专属）

- 引用 G-1（归一化唯一）、G-2（幂等）、G-3（外发先查）。
- Invariant L1-1：抑制来源可追溯。每行记录 `source`（`INBOUND_REPLY` / `ONE_CLICK` / `MAILTO` / `MANUAL`）与 `reason`（可空，截断 ≤ 500）。子计划 01 只产生 `INBOUND_REPLY`；其余来源值为枚举占位，供 02 与未来使用。
- Invariant L1-2：退订捕获不改变意图路由。入站捕获在 `AutoMailReplyService` 既有意图处理**之外**追加；无论是否捕获到退订，原 `NOT_INTERESTED → MANUAL_HANDOFF` 等流转都照常执行。
- Invariant L1-3：退订关键词独立判定。退订捕获用 `EmailSuppressionService.looksLikeUnsubscribe(body)` 的独立关键词集，不依赖也不修改 `InboundIntentClassifier` 的枚举或关键词表。

## 现状审计

### `email_suppression`（新表）
- 不存在，本计划创建（V30）。

### 外发写路径（G-3 过滤点）
- `campaign/service/InitialOutreachService.kt`：`sendInitialBatch` 遍历 `experts`，对每个先查 `existsByCampaignIdAndOrcidId`（已存在→`skipped++; return@forEach`），再 `selectAccount` → 保存 `ExpertContact(currentStatus="NEW")` → `compose` → `send`。抑制过滤须插在保存联系记录**之前**。
- `campaign/service/ManualInitialOutreachService.kt`：发送循环在 `:286`，结构类似（人工批量）。须读其循环以确定 `skipped` 计数字段名与目标对象的邮箱取值。

### 入站读路径（捕获点）
- `mail/service/AutoMailReplyService.kt`：入站单封处理入口（约 `:90` 起的 pipeline）。已有 `from`/邮箱与 `body`（经 `MailBodyCleaner`）。捕获点选在意图分类**之后、动作执行附近**，取发件人邮箱与正文调用抑制服务。
- `mail/service/InboundIntentClassifier.kt`：`notInterestedKeywords` 含 `unsubscribe`、`please remove me` 等（只读参考，不改）。

### 来源邮箱字段
- `ExpertContact.expertEmail` 即收件邮箱；外发目标用 `expert.email`（`ExpertProfile`）。入站发件人邮箱来自 IMAP message from。

## 实现方案

### 任务 1：迁移 V30 — `email_suppression` 表（G-1, G-2, L1-1）

文件：`src/main/resources/db/migration/V30__create_email_suppression.sql`

```sql
CREATE TABLE email_suppression (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(320) NOT NULL COMMENT '归一化邮箱(小写trim)',
    source VARCHAR(20) NOT NULL COMMENT 'INBOUND_REPLY/ONE_CLICK/MAILTO/MANUAL',
    reason VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_email (email),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 任务 2：领域 + 仓储（G-2）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/domain/EmailSuppression.kt`

```kotlin
@Table("email_suppression")
data class EmailSuppression(
    @Id val id: Long? = null,
    val email: String,        // 归一化
    val source: String,       // L1-1
    val reason: String?,
    val createdAt: LocalDateTime? = null
)
```

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/repository/EmailSuppressionRepository.kt`

```kotlin
interface EmailSuppressionRepository : CrudRepository<EmailSuppression, Long> {
    fun existsByEmail(email: String): Boolean
    fun findByEmail(email: String): EmailSuppression?
}
```

### 任务 3：抑制服务（G-1, G-2, L1-1, L1-3）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionService.kt`

```kotlin
@Service
class EmailSuppressionService(
    private val repository: EmailSuppressionRepository
) {
    fun normalize(email: String): String = email.trim().lowercase(Locale.ROOT)   // G-1

    fun isSuppressed(email: String): Boolean {
        val n = normalize(email)
        if (n.isBlank()) return false
        return repository.existsByEmail(n)
    }

    /** 幂等：已存在则忽略（G-2）。返回是否新增。 */
    fun suppress(email: String, source: SuppressionSource, reason: String?): Boolean {
        val n = normalize(email)
        if (n.isBlank() || repository.existsByEmail(n)) return false
        return try {
            repository.save(EmailSuppression(
                email = n, source = source.name, reason = reason?.take(500),
                createdAt = LocalDateTime.now()
            ))
            true
        } catch (e: DuplicateKeyException) {   // 并发下幂等兜底（G-2）
            false
        }
    }

    /** L1-3：独立退订关键词判定，不复用 InboundIntentClassifier。 */
    fun looksLikeUnsubscribe(body: String?): Boolean {
        val b = body?.lowercase(Locale.ROOT) ?: return false
        return UNSUBSCRIBE_PHRASES.any { b.contains(it) }
    }

    companion object {
        private val UNSUBSCRIBE_PHRASES = listOf(
            "unsubscribe", "please remove me", "remove me from",
            "stop emailing", "opt out", "opt-out", "取消订阅", "退订", "不要再发"
        )
    }
}

enum class SuppressionSource { INBOUND_REPLY, ONE_CLICK, MAILTO, MANUAL }
```

### 任务 4：自动批量外发过滤（G-3）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt`

- 注入 `EmailSuppressionService`。
- 在 `existsByCampaignIdAndOrcidId` 跳过判断之后、`selectAccount`/保存 `ExpertContact` 之前，新增：

```kotlin
if (expert.email.isNullOrBlank() || emailSuppressionService.isSuppressed(expert.email!!)) {
    skipped += 1
    return@forEach
}
```

（不创建联系记录、不发送、不计入 results；G-3。）

### 任务 5：人工批量外发过滤（G-3）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

- 注入 `EmailSuppressionService`。
- 在发送循环（`:286` 附近）确定目标邮箱后、发送前插入同样的 `isSuppressed` 跳过逻辑，复用该服务现有的 skipped/跳过计数路径（执行时按实际循环结构对齐字段名）。

### 任务 6：入站退订捕获（L1-1, L1-2, L1-3）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`

- 注入 `EmailSuppressionService`。
- 在入站单封处理中、意图分类之后追加（不替换既有路由，L1-2）：

```kotlin
if (emailSuppressionService.looksLikeUnsubscribe(cleanedBody)) {
    emailSuppressionService.suppress(senderEmail, SuppressionSource.INBOUND_REPLY,
        "inbound reply unsubscribe")
}
```

- 既有 `NOT_INTERESTED → MANUAL_HANDOFF` 等流转保持不变。

### 任务 7：测试

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionServiceTest.kt`
- `normalize`：大小写/空格归一（G-1）。
- `suppress` 同邮箱两次仅一行、第二次返回 false（G-2）。
- `isSuppressed` 对大小写不同的同邮箱命中（G-1）。
- `looksLikeUnsubscribe`：命中中英退订措辞；普通正文不命中（L1-3）。

文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt`（新增用例或新建）
- 被抑制邮箱：不调用 `mailDeliveryService.send`、不保存 `ExpertContact`、`skipped` +1（G-3）。

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V30__create_email_suppression.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/EmailSuppression.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/EmailSuppressionRepository.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionService.kt` | 新增 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | 修改 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 修改 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 修改 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionServiceTest.kt` | 新增 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | 新增/修改 |

文件数 = 9 ≤ 10。子系统：外发抑制（存储+服务+两外发入口）+ 入站捕获 = 2。

## 验收标准

- G-1：对 `A@X.com` 与 `a@x.com ` 调 `isSuppressed` 结果一致（同一行）。
- G-2：重复 `suppress` 同邮箱不抛异常、表中仅一行。
- G-3：mock 一个被抑制专家进入 `sendInitialBatch`，断言 `mailDeliveryService.send` 零调用、`expertContactRepository.save` 未对该专家调用、`skipped` 计数 +1。
- L1-2：入站一封含 "unsubscribe" 的 `NOT_INTERESTED` 邮件，断言既有 MANUAL_HANDOFF 流转仍发生**且** `email_suppression` 新增该发件人。
- L1-3：`looksLikeUnsubscribe("I'm not available")` = false；`("please unsubscribe me")` = true。
- 集成（交互点）：外发入口在过滤前会查 `existsByCampaignIdAndOrcidId`，断言两条跳过逻辑共存、计数不重复叠加。
