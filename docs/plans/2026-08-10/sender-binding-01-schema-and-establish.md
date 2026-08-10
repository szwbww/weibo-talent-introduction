# P1：专家—发送账号绑定基座（字段 / 回填 / 建立点 / 解析服务）

> 系列索引与全局不变量 G-1..G-3、验证命令来源见 [sender-binding-00-index.md](sender-binding-00-index.md)。

## 需求描述

**Observable outcome**

1. `expert_contact` 新增 `bound_sender_account_code` / `sender_account_bound_at` 两列；
   新建的专家联系人在**创建行的同一次 save** 中固化本次分配到的发件账号。
2. 历史数据由迁移回填：取该 contact **最早一条** `direction='OUTBOUND' AND mail_type='INTRODUCTION'`
   的 `mail_record.sender_account_code` 作为绑定值，`sender_account_bound_at` 取该邮件的 `created_at`。
3. 新增 `SenderAccountBindingService`，对外暴露 `resolveForSend(contact, manual)`，
   本计划**只实现不接入**（无调用方），供 P2 切换消费。

**What must NOT change**

- 任何一封邮件的实际发件账号（本计划不改变任何选号结果）。
- `InitialOutreachService` / `ManualInitialOutreachService` 的批次统计、跳过原因、
  `assignments` 打分行为。
- `ManualExpertMailService` / `MeetingScheduleService` / `PendingMailOperationService` /
  `AutoMailReplyService` 的发送行为（本计划完全不碰这四个文件）。
- `mail_sender_account.todaySentCount` 的任何读写路径（K-operator-send-quota-paths）。

**Out of scope（明确延后）**

- 绑定的消费与 enabled 门禁 → P2
- 分发打分计入存量绑定 → P3
- 换绑接口、`sender_account_changed` 标记列、审计 → P4
- 前端展示 → P5
- ES 索引同步绑定字段（本系列全程不做；绑定是 MySQL-only 事实）

## 关键不变量

### I-1: 绑定的建立点恰好三处
- Rule: 写入 `bound_sender_account_code` 的路径全集 = ①`InitialOutreachService.kt:51` 的
  `ExpertContact(...)` 构造 ②`ManualInitialOutreachService.kt:575` 的 `ExpertContact(...)` 构造
  ③ V85 迁移的一次性回填 SQL。除此之外本计划不新增任何写入点。
- Applies to: 上述三处。所有其他 `expertContactRepository.save(contact.copy(...))` 路径
  因 Kotlin data class `copy` 语义自动保留原值，**不需要也不允许**逐处传参。
- Violation consequence: 漏改构造点 → 新专家永久无绑定，P2 上线后全部走"无绑定兜底"，
  强一致失效；多改 `copy` 点 → 绑定被非绑定语义的更新路径覆盖。
- 来源: K-expert-contact-two-write-sites（本次已 re-grep 复核：
  `grep -n "ExpertContact(" campaign/service/*.kt` 仍只有这 2 处构造）

### I-2: 绑定在 contact 建行时固化，不在发送成功后写
- Rule: 绑定值必须写在**创建 `ExpertContact` 行的那一次 `save`** 里，
  不得改为"发送成功后再 update 绑定"。
- Applies to: `InitialOutreachService.kt:50-62`、`ManualInitialOutreachService.kt:573-582`。
- Violation consequence: `InitialOutreachService.kt:65-80` 的发送异常分支会 `return@forEachIndexed`
  且**不回滚已保存的 contact**；若绑定后写，首封失败的专家会留下无绑定的 contact，
  下次重试重新选号 → 绑定不稳定，正是本需求要消灭的行为。

### I-3: 绑定为 NULL 表示未绑定（全局 G-2）
- Rule: 列可空，默认 `NULL`；禁止空串或哨兵字符串。
- Applies to: V85 DDL、回填 SQL 的 `WHERE`、`ExpertContact.kt` 的 Kotlin 默认值（`null`）。
- Violation consequence: 回填 SQL 的 `WHERE bound_sender_account_code IS NULL` 幂等条件失效，
  重跑迁移会覆盖 P4 之后的人工换绑结果。

### I-4: 回填只写两列，且必须是列级 UPDATE
- Rule: 回填 SQL 只 `SET bound_sender_account_code, sender_account_bound_at`；
  若后续在 Kotlin 侧补写回填器，必须用 `@Modifying @Query("UPDATE expert_contact SET ...")`，
  不得用 `repository.save(contact.copy(...))`。
- Applies to: V85 迁移；`ExpertContactRepository` 新增的 `updateBindingById`。
- Violation consequence: 聚合 save 会把整行回写，覆盖同一时刻其他路径写入的
  `current_status` / `operator_status` / `follow_up_marked` 等字段。
- 来源: K-backfill-column-specific-update（severity P1，反例即
  `ContactCountryBackfillService.kt:68`）

### I-5: `SIMULATOR_NOOP` 永不入绑定（全局 G-3）
- Rule: 建立点与回填都必须排除 `SIMULATOR_NOOP`。
- Applies to: V85 回填 SQL 的 `WHERE mr.sender_account_code <> 'SIMULATOR_NOOP'`；
  `SenderAccountBindingService.bindOnCreate()` 的入参断言。
- Violation consequence: 模拟器账号被绑定后，P2 会把真实专家的外发路由到 NOOP 通道，
  邮件静默丢失。
- 来源: K-sender-account-enabled-scope

### I-6: `resolveForSend` 是绑定的唯一读取入口
- Rule: 本计划起，任何"这个专家该用哪个账号发信"的判定只能经
  `SenderAccountBindingService.resolveForSend(contact, manual)`；
  禁止调用方直接读 `contact.boundSenderAccountCode` 再自行 `getAccount(...)`。
- Applies to: 本计划无调用方（服务已实现待接入）；P2 起为强制约束。
- Violation consequence: 门禁矩阵（见索引"冲突 2"表格）在不同调用方漂移，
  重演本次 LiLei 事故的同类缺陷。

### I-7: 门禁矩阵由 `manual` 形参区分，不由调用方类型推断
- Rule: `resolveForSend(contact, manual: Boolean)`：
  - `manual=false`（自动路径）：账号须满足 `enabled && !autoSendPaused &&
    todaySentCount < effectiveDailyLimit && code != SIMULATOR_NOOP`
  - `manual=true`（人工路径）：账号只须满足 `enabled && code != SIMULATOR_NOOP`；
    **不判 `autoSendPaused`、不判每日额度**
  - 不满足 → 抛 `BoundSenderAccountUnavailableException(contactId, accountCode, reason)`，
    **不降级重选**
  - `boundSenderAccountCode == null` → 抛 `SenderAccountNotBoundException(contactId)`，
    由 P2 的调用方各自决定兜底（本计划不定义兜底）
- Applies to: `SenderAccountBindingService.resolveForSend`。
- Violation consequence: 人工路径若加上额度判定，会回归
  K-operator-send-quota-paths 记录的"人工发送脱离配额"既有决策
  （锁定测试 `MailSenderAccountServiceTest.kt:35-46`、`:48-57`）。
- 来源: 决策 ② + K-sender-account-enabled-scope + K-operator-send-quota-paths 调和口径

## 现状审计

### MySQL 表 `expert_contact`

- Schema: `V1__create_business_tables.sql:79-95`。主键 `id`，
  `UNIQUE KEY uk_campaign_expert (campaign_id, orcid_id)`，FK → `campaign(id)`。
  后续列由 V11/V12/V14/V19/V48/V51 逐步追加，均为 `ALTER TABLE ... ADD COLUMN` 直白写法
  （参照 `V48__add_country_to_expert_contact.sql`：两行 ALTER + CREATE INDEX，无幂等包装）。
  Kotlin 映射 `ExpertContact.kt:8-31`（Spring Data JDBC，列必须显式建模，加列不改 data class 即读不到）。

- **构造（建行）路径 — 全集 2 处**（`grep -n "ExpertContact(" campaign/service/*.kt` 实测）
  1. `InitialOutreachService.kt:50-62` — 自动首封。作用域内持有
     `expert: ExpertProfile` 与 `account: MailSenderAccount`（`:48` 选出），
     二者都在同一个 `forEachIndexed` 迭代内可见。
  2. `ManualInitialOutreachService.kt:573-582` — 手动/调度批量首封，
     `existingContact ?: run { expertContactRepository.save(ExpertContact(...)) }`。
     `account` 在 `:552` 已选出，位于同一 `try` 块之前，可见。

- **更新（copy）路径 — 不需改动，列举以证明 I-1 成立**
  `ConversationStateService.transition`、`ExpertContactManagementService`
  （`markFollowUp:57`、`unmarkFollowUp:64`）、`ExpertOperatorStatusService`、
  `ExpertIndexLevelOperationService`、`ContactCountryBackfillService.kt:68`、
  `ExpertIndexController.kt:~400` 的 `applicationIndexed` 回写、
  `AutoMailReplyService` 的状态推进 —— 全部为 `contact.copy(...)`，
  新列有 Kotlin 默认值即自动透传。

- **读路径（本计划新增字段的未来消费方，本计划均不改）**
  `ExpertContactRepository.findFilteredContacts`（`:41-63`）、
  `ExpertIndexController.kt:68-73`（`findByOrcidIdIn` 后按 ORCID 归一 join）、
  `ExpertContactManagementService.getContactDetail:68-91`。

- **Interaction points**
  - IP-1：V85 回填（写）× `SenderAccountBindingService.resolveForSend`（读）——
    回填口径若与建立点口径不一致（例如回填取最新一封而非最早一封），
    历史专家的绑定会指向后期换过的账号，与"绑定=首封归属"语义冲突。
  - IP-2：`ExpertContact` 新列 × 所有 `copy(...)` 更新路径 —— 由 Kotlin 默认值保证，
    但**必须给默认值**（`= null`），否则全仓 `copy` 调用编译不过。

### MySQL 表 `mail_record`（回填数据源，只读）

- Schema: `V1__create_business_tables.sql:97-115`，含 `expert_contact_id`、`direction`、
  `mail_type`、`sender_account_code`、`created_at`。
  `V31__add_mail_record_created_at_index.sql` 已加 `created_at` 索引。
- 回填只做一次性 `SELECT`，不写。
- 已知数据特征：`ManualExpertMailService.kt:68-86` 与 `ManualOutreachTxHelper.recordSuccess`
  写 OUTBOUND 记录时 `senderAccountCode` 恒非空（`MailRecord` 该字段为非空 String，
  见 `MailRecordRepository.kt:9`）。

### 发件账号池 `mail_sender_account`（只读）

- `MailSenderAccountService.SIMULATOR_ACCOUNT_CODE = "SIMULATOR_NOOP"`（`:257`）。
- 额度读判定唯一来源 `SenderWarmupService.effectiveDailyLimit(account, ignoreWarmup)`
  （K-operator-send-quota-paths 补充段落记载的"读判定全集"）。
  `resolveForSend(manual=false)` 必须复用它，不得另写 `dailySendLimit` 比较。
- `MailSenderAccountService.getAccount(code)`（`:30-32`）按 code 取任意状态账号，
  `getEnabledAccount(code)`（`:26-28`）只取 enabled —— 本计划的门禁自行判定，
  统一走 `getAccount` 再判，避免两种异常文案。

## 实现方案

### 阶段 1 — 数据层

**T1.1 新建迁移 `V85__add_expert_contact_sender_binding.sql`**（遵 I-3/I-4/I-5，参照 V48 写法）

```sql
-- V85: 专家—发送账号绑定基座。
-- 绑定语义 = 主题发起权归属（回复仍由 mail_record.sender_account_code 决定）。
-- NULL 表示未绑定，禁止空串/哨兵值。
ALTER TABLE expert_contact
    ADD COLUMN bound_sender_account_code VARCHAR(64) NULL
    COMMENT '绑定的发件账号 code；NULL=未绑定。决定新发起主题邮件的发件账号',
    ADD COLUMN sender_account_bound_at DATETIME NULL
    COMMENT '绑定建立时间';

CREATE INDEX idx_expert_contact_bound_sender
    ON expert_contact (bound_sender_account_code);

-- 回填：取每个 contact 最早一封 OUTBOUND INTRODUCTION 的发件账号（IP-1 口径）。
-- 排除 SIMULATOR_NOOP（I-5）。WHERE ... IS NULL 保证重跑幂等（I-3）。
UPDATE expert_contact ec
JOIN (
    SELECT mr.expert_contact_id,
           SUBSTRING_INDEX(GROUP_CONCAT(mr.sender_account_code
                           ORDER BY mr.created_at ASC, mr.id ASC), ',', 1) AS first_code,
           MIN(mr.created_at) AS first_at
      FROM mail_record mr
     WHERE mr.direction = 'OUTBOUND'
       AND mr.mail_type = 'INTRODUCTION'
       AND mr.sender_account_code IS NOT NULL
       AND mr.sender_account_code <> ''
       AND mr.sender_account_code <> 'SIMULATOR_NOOP'
     GROUP BY mr.expert_contact_id
) f ON f.expert_contact_id = ec.id
SET ec.bound_sender_account_code = f.first_code,
    ec.sender_account_bound_at   = f.first_at
WHERE ec.bound_sender_account_code IS NULL;
```

> 注：`GROUP_CONCAT` 默认 `group_concat_max_len=1024`，此处每组只取首元素，
> 单个 account_code ≤ 64 字符，第一个元素永不被截断。

**T1.2 `ExpertContact.kt` 加两个字段**（遵 I-3、IP-2）

在 `country`（`:24`）之后、`operatorStatus`（`:25`）之前插入：

```kotlin
    val boundSenderAccountCode: String? = null,
    val senderAccountBoundAt: LocalDateTime? = null,
```

必须带 `= null` 默认值，否则全仓 `ExpertContact(...)` 构造与 `copy(...)` 编译失败。

**T1.3 `ExpertContactRepository.kt` 加列级更新方法**（遵 I-4）

紧跟现有 `updateCountryById`（`:65-67`）之后追加：

```kotlin
    @Modifying
    @Query("""
        UPDATE expert_contact
           SET bound_sender_account_code = :accountCode,
               sender_account_bound_at = :boundAt
         WHERE id = :id
    """)
    fun updateBindingById(id: Long, accountCode: String?, boundAt: LocalDateTime?): Int
```

需要 `import java.time.LocalDateTime`。本计划内该方法**仅被
`SenderAccountBindingService.bindOnCreate` 的"无绑定补写"分支使用**，
不新增回填器 Bean（回填由 SQL 一次性完成）。

### 阶段 2 — 解析服务

**T2.1 新建 `mail/service/SenderAccountBindingService.kt`**（遵 I-5/I-6/I-7）

放在 `mail/service` 而非 `campaign/service`：它依赖 `MailSenderAccountService` 与
`SenderWarmupService`，而 `campaign` 已单向依赖 `mail`
（`InitialOutreachService.kt:8-13` 即 import `mail.service.*`），反向会成环。

```kotlin
package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class SenderAccountBindingService(
    private val mailSenderAccountService: MailSenderAccountService,
    private val warmup: SenderWarmupService,
    private val expertContactRepository: ExpertContactRepository
) {
    /** 建行时固化绑定：返回可直接放进 ExpertContact(...) 构造的二元组（I-2/I-5）。 */
    fun bindingFieldsFor(accountCode: String, now: LocalDateTime): Pair<String, LocalDateTime> {
        require(accountCode.isNotBlank()) { "accountCode is required for binding" }
        require(accountCode != MailSenderAccountService.SIMULATOR_ACCOUNT_CODE) {
            "SIMULATOR_NOOP must never be bound to an expert contact"
        }
        return accountCode to now
    }

    /** 绑定的唯一读取入口（I-6/I-7）。 */
    fun resolveForSend(contact: ExpertContact, manual: Boolean): MailSenderAccount {
        val contactId = contact.id ?: error("Expert contact id is required")
        val code = contact.boundSenderAccountCode?.takeIf { it.isNotBlank() }
            ?: throw SenderAccountNotBoundException(contactId)
        val account = mailSenderAccountService.getAccount(code)
        requireAvailable(contactId, account, manual)
        return account
    }

    /** 无绑定兜底的补写：调用方选号成功后回填绑定，只写两列（I-4）。 */
    fun bindIfAbsent(contactId: Long, accountCode: String, now: LocalDateTime) {
        val (code, at) = bindingFieldsFor(accountCode, now)
        expertContactRepository.updateBindingById(contactId, code, at)
    }

    private fun requireAvailable(contactId: Long, account: MailSenderAccount, manual: Boolean) {
        if (account.accountCode == MailSenderAccountService.SIMULATOR_ACCOUNT_CODE) {
            throw BoundSenderAccountUnavailableException(contactId, account.accountCode, "SIMULATOR")
        }
        if (!account.enabled) {
            throw BoundSenderAccountUnavailableException(contactId, account.accountCode, "DISABLED")
        }
        if (manual) return   // 人工路径到此为止：不判暂停、不判额度（I-7）
        if (account.autoSendPaused) {
            throw BoundSenderAccountUnavailableException(contactId, account.accountCode, "AUTO_SEND_PAUSED")
        }
        if (account.todaySentCount >= warmup.effectiveDailyLimit(account)) {
            throw BoundSenderAccountUnavailableException(contactId, account.accountCode, "DAILY_LIMIT_REACHED")
        }
    }
}

// 必须继承 IllegalStateException：GlobalExceptionHandler（common/controller）只对
// IllegalArgumentException / IllegalStateException / NoSuchElementException 映射 400，
// 其余 Exception 一律落到 handleException → 500 INTERNAL_ERROR，运营侧看不到可读原因。
class SenderAccountNotBoundException(val contactId: Long) :
    IllegalStateException("专家 $contactId 尚未绑定发件账号")

class BoundSenderAccountUnavailableException(
    val contactId: Long,
    val accountCode: String,
    val reason: String
) : IllegalStateException("绑定发件账号 $accountCode 不可用（$reason），专家 contactId=$contactId")
```

> 继承 `IllegalStateException` 后，`ManualInitialOutreachService` 里
> **必须把这两个具体类型的 catch 分支放在通用 `catch (e: Exception)` 之前**
> （Kotlin/JVM 按声明顺序匹配），否则会被通用分支吞掉并升级为整批 FAILED（P2 的 I-4）。

> `effectiveDailyLimit(account)` 使用默认 `ignoreWarmup=false`。
> 批量路径的 `ignoreWarmup=true` 语义在 P2 接入时以重载形参补齐，本计划不提前引入。

### 阶段 3 — 建立点接线

**T3.1 `InitialOutreachService.kt`**（遵 I-1/I-2/I-5）

构造函数注入 `senderAccountBindingService: SenderAccountBindingService`。
`:49-62` 改为（`val now` 已存在于 `:49`）：

```kotlin
            val now = LocalDateTime.now()
            val (boundCode, boundAt) = senderAccountBindingService
                .bindingFieldsFor(account.accountCode, now)
            val contact = expertContactRepository.save(
                ExpertContact(
                    campaignId = campaignId,
                    orcidId = expert.orcidId,
                    expertEmail = expert.email.orEmpty(),
                    expertName = expert.displayName,
                    currentStatus = "NEW",
                    country = expert.country,
                    autoReplyEnabled = autoReplySettingService.isGlobalEnabled(),
                    boundSenderAccountCode = boundCode,
                    senderAccountBoundAt = boundAt,
                    createdAt = now,
                    updatedAt = now
                )
            )
```

`account` 在 `:48` 已选出，先于 `now` 与 contact 构造，顺序无需调整。

**T3.2 `ManualInitialOutreachService.kt`**（遵 I-1/I-2）

构造函数注入同一 Bean。`:573-582` 的 `existingContact ?: run { ... }` 分支内，
`account` 已在 `:552` 选出，改为：

```kotlin
                    val contact = existingContact ?: run {
                        val now = LocalDateTime.now()
                        val (boundCode, boundAt) = senderAccountBindingService
                            .bindingFieldsFor(account.accountCode, now)
                        expertContactRepository.save(ExpertContact(
                            campaignId = campaignId, orcidId = normOrcid,
                            expertEmail = expert.email.orEmpty(), expertName = expert.displayName,
                            currentStatus = "NEW", operatorStatus = "NOT_CONTACTED",
                            country = expert.country,
                            autoReplyEnabled = autoReplySettingService.isGlobalEnabled(),
                            boundSenderAccountCode = boundCode,
                            senderAccountBoundAt = boundAt,
                            createdAt = now, updatedAt = now
                        ))
                    }
```

**`existingContact` 分支本计划不动**（复用已有 contact 时不改其绑定，
即便本轮 `selectAccount` 选出了别的账号）——绑定的优先级切换属于 P2。
本计划这样做的后果是"P1 期间已存在的 contact 仍可能被别的账号发信"，
这与 must-NOT-change「不改变任何选号结果」一致，是有意为之。

**T3.3 材料提醒批量路径（`:272`）本计划不动**
该路径的 `targets` 全是已有 contact，绑定固化不适用；其消费改造在 P2。

### 阶段 4 — 测试

**T4.1 新建 `src/test/kotlin/.../mail/service/SenderAccountBindingServiceTest.kt`**

用例（Mockito，风格对齐 `MailSenderAccountServiceTest.kt`）：

| 用例 | 断言 |
|---|---|
| `bindingFieldsFor rejects blank account code` | 抛 `IllegalArgumentException` |
| `bindingFieldsFor rejects simulator account` | 抛 `IllegalArgumentException`（I-5） |
| `resolveForSend throws when contact has no binding` | 抛 `SenderAccountNotBoundException`（I-7） |
| `resolveForSend throws when bound account disabled for manual send` | `manual=true` + `enabled=false` → `BoundSenderAccountUnavailableException(reason="DISABLED")`（I-7，决策 ②） |
| `resolveForSend allows auto-paused account for manual send` | `manual=true` + `autoSendPaused=true` → 正常返回（I-7，保 K-sender-account-enabled-scope） |
| `resolveForSend allows account at daily limit for manual send` | `manual=true` + `todaySentCount == effectiveDailyLimit` → 正常返回（I-7，保 K-operator-send-quota-paths） |
| `resolveForSend throws when auto path hits auto-send pause` | `manual=false` + `autoSendPaused=true` → `reason="AUTO_SEND_PAUSED"` |
| `resolveForSend throws when auto path hits daily limit` | `manual=false` + `todaySentCount >= effectiveDailyLimit` → `reason="DAILY_LIMIT_REACHED"` |
| `resolveForSend throws for simulator binding` | `reason="SIMULATOR"`（I-5） |
| `bindIfAbsent writes via column-specific update` | `Mockito.verify(repo).updateBindingById(...)`，且 `Mockito.verify(repo, never()).save(any())`（I-4） |

**T4.2 `InitialOutreachServiceTest.kt` 补 1 例**

`sendInitialBatch binds selected account on contact creation`：
捕获 `expertContactRepository.save` 的 `ExpertContact` 实参，
断言 `boundSenderAccountCode == 选中账号 code` 且 `senderAccountBoundAt != null`（I-1/I-2）。

**T4.3 `ManualInitialOutreachServiceTest.kt` 补 2 例**

- `new contact is created with binding`：同上断言。
- `existing contact binding is not overwritten`：预置带绑定的 `existingContact`，
  断言全程无 `updateBindingById` 调用、无对该 contact 的 `save`（must-NOT-change）。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V85__add_expert_contact_sender_binding.sql` | 新增 | 两列 + 索引 + 回填 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/ExpertContact.kt` | 修改 | 加 2 字段（带默认值） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt` | 修改 | 加 `updateBindingById` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingService.kt` | 新增 | 解析服务 + 两个异常类 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | 修改 | 建行时固化绑定 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 修改 | 同上（仅新建分支） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountBindingServiceTest.kt` | 新增 | 10 例 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | 修改 | +1 例 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 | +2 例 |

文件数 9 ≤ 10 ✓　子系统 1（后端）≤ 2 ✓　新增共享存储字段：1 个逻辑字段（绑定，含其时间戳）✓

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。
> 来源：项目根 `CLAUDE.md`「Commands」+「项目元信息」`test_command` / `build_command`。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增/修改的测试类（快速迭代）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=SenderAccountBindingServiceTest

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=InitialOutreachServiceTest+ManualInitialOutreachServiceTest

# 单方法（示例语法）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='SenderAccountBindingServiceTest#resolveForSend allows auto-paused account for manual send'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0` 与 `BUILD SUCCESS`。
来源：`CLAUDE.md` 项目元信息（`test_command` / `build_command`），单测过滤语法取自
`CLAUDE.md`「Commands」章节示例 `-Dtest=QaMatchServiceTest#methodName`。

## 验收标准

- **I-1**: `grep -rn "ExpertContact(" src/main/kotlin/com/weibo/talentintroduction/campaign/service/`
  结果仍恰为 2 处，且两处均传入 `boundSenderAccountCode`。
  `git diff` 中不含任何新增的 `copy(boundSenderAccountCode = ...)`。
- **I-2**: `InitialOutreachServiceTest` / `ManualInitialOutreachServiceTest` 的新用例断言
  绑定值出现在**首次 `save` 的实参**上；`git diff` 中 `txHelper.recordSuccess` 前后无绑定写入。
- **I-3**: V85 DDL 无 `NOT NULL`、无 `DEFAULT ''`；回填 SQL 含 `WHERE ec.bound_sender_account_code IS NULL`。
  `ExpertContact.kt` 两字段类型为 `String?` / `LocalDateTime?` 且默认 `null`。
- **I-4**: `ExpertContactRepository.updateBindingById` 带 `@Modifying @Query`，SET 子句只含两列；
  `SenderAccountBindingServiceTest` 的 `bindIfAbsent writes via column-specific update` 通过。
- **I-5**: V85 回填 SQL 含 `<> 'SIMULATOR_NOOP'`；
  `bindingFieldsFor rejects simulator account` 与 `resolveForSend throws for simulator binding` 通过。
- **I-6**: `grep -rn "boundSenderAccountCode" src/main/kotlin` 的读取点只出现在
  `SenderAccountBindingService.kt` 与 `ExpertContact.kt` 内（P1 期无其他消费方）。
- **I-7**: `SenderAccountBindingServiceTest` 中 6 条门禁矩阵用例全部通过。
- **回归**: 执行「验证命令」节的全量测试命令通过；
  特别确认 `MailSenderAccountServiceTest`、`ManualExpertMailServiceTest`、
  `MeetingScheduleServiceTest` **零改动零失败**（本计划不碰这三条路径）。

## 人工验收清单

### A-1: 新专家首封后自动带绑定
- 前置条件: 至少 2 个 `enabled=1` 的发件账号；ES CANDIDATE 层有未联系过的专家；
  `talent-introduction.scheduling.enabled=true` 或手动触发一次首封批量。
- 操作步骤:
  1. 触发一次自动首封（任务页「初次外联」或等待 cron）。
  2. 记录本批某个专家的 ORCID 与实际收到邮件的发件地址。
  3. 执行 `SELECT orcid_id, bound_sender_account_code, sender_account_bound_at
     FROM expert_contact WHERE orcid_id='<该 ORCID>';`
- 预期结果: `bound_sender_account_code` 等于步骤 2 中发件地址对应账号的 `account_code`；
  `sender_account_bound_at` 与 `created_at` 同秒。
- 覆盖: I-1、I-2、需求描述第 1 条

### A-2: 历史专家被正确回填
- 前置条件: 库中存在至少 1 个已发过 INTRODUCTION 的历史 contact；V85 已执行。
- 操作步骤:
  1. 任取一个历史 contact id，执行
     `SELECT sender_account_code, created_at FROM mail_record
      WHERE expert_contact_id=<id> AND direction='OUTBOUND' AND mail_type='INTRODUCTION'
      ORDER BY created_at ASC, id ASC LIMIT 1;`
  2. 执行 `SELECT bound_sender_account_code, sender_account_bound_at
     FROM expert_contact WHERE id=<id>;`
- 预期结果: 两组值逐字相等（account code 与时间戳）。
- 覆盖: IP-1、需求描述第 2 条

### A-3: 回填幂等，重跑不覆盖
- 前置条件: A-2 已通过。
- 操作步骤:
  1. 手工把某个 contact 的 `bound_sender_account_code` 改成另一个账号 code。
  2. 手工重跑 V85 的回填 UPDATE 语句（复制自迁移文件）。
  3. 再查该行。
- 预期结果: 步骤 1 手改的值**保持不变**（因 `WHERE ... IS NULL` 不命中）。
- 覆盖: I-3

### A-4（回归）: 发件账号分配结果不变
- 前置条件: 记录 P1 上线**前**一次首封批量的账号分布（任务执行页的「账号统计」）。
- 操作步骤: P1 上线后，用相同的批量配置再跑一次首封，对比账号分布形态。
- 预期结果: 各账号承担量的相对比例与上线前同量级（不要求逐条相同，
  但不得出现"全部集中到单一账号"或"某个 enabled 账号完全不参与"）。
- 覆盖: must-NOT-change 第 1、2 条

### A-5（回归）: 人工发送、会议确认、收发件箱回复行为未变
- 前置条件: 一个处于 `WAITING_REPLY` 的专家，且其绑定账号当前为 `enabled=0`。
- 操作步骤:
  1. 在专家详情页用「发送邮件」发一封模板邮件（不指定账号）。
  2. 在收发件箱对该专家的一封来信做人工回复。
- 预期结果: 两步均**发送成功**，不出现任何"账号不可用"报错
  （P1 尚未接入门禁，门禁在 P2 才生效）。步骤 1 的发件账号与 P1 上线前一致。
- 覆盖: must-NOT-change 第 3 条、全局 G-1

### A-6（回归）: 模拟器账号未被绑定
- 前置条件: 系统内存在 `SIMULATOR_NOOP` 账号。
- 操作步骤: 执行
  `SELECT COUNT(*) FROM expert_contact WHERE bound_sender_account_code='SIMULATOR_NOOP';`
- 预期结果: 返回 `0`。
- 覆盖: I-5、全局 G-3
