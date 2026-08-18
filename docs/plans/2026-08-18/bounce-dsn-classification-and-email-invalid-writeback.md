# 退信 DSN 分类修复 + EMAIL_INVALID 双写落库

> 计划日期：2026-08-18
> 触发：线上实测发现 `bounce_record` 中 SOFT 39 条 `dsn_status` 100% 为 NULL，
> 且 `expert_contact` 中 `EMAIL_INVALID` 计数为 0（2112 封 INTRODUCTION 已发送）。

---

## 需求描述

### Observable outcome

1. 标准 DSN 退信（`multipart/report; report-type=delivery-status`）被正确分类为 `HARD`，
   `bounce_record.dsn_status` 落库为真实状态码（如 `5.1.1`），不再为 NULL。
2. 硬退信发生后，该专家的 `expert_contact.operator_status` 在 **MySQL 中**变为 `EMAIL_INVALID`
   （当前只写了 ES），从而被 `ManualInitialOutreachService.buildRetryableTargets()` 的
   重试队列过滤排除。

### What must NOT change

1. **正常专家回信的正文提取结果**（`ReceivedMail.body`）不得改变——尤其
   `multipart/alternative` 邮件不得因改动出现 text/plain 与 text/html 版本重复。
   下游消费者：`inbound_mail_processing.body`、QA 关键词匹配、`AiReplyDraftService` prompt。
2. **`expert_contact.operator_status` 的写入口白名单**不得扩大——
   `OperatorStatusWriteSeamGuardTest` 的 `ALLOWED_WRITE_SITES` 保持
   `{ExpertOperatorStatusService.kt, ManualInitialOutreachService.kt}` 两项不变。
3. **已推进状态不得被退信回退**——已 `REPLIED` / `MATERIALS_RECEIVED` / `INVITED` /
   `COMPLETED` 的专家，后到的硬退信不得把状态抹回 `EMAIL_INVALID`。
4. **`collectBounces()` 的 SEEN 过滤通道语义**不变（`K-inbound-seen-not-processed-marker`
   已明确该通道为独立通道且仍按 SEEN 过滤）。
5. `ManualInitialOutreachService:762` 发送期 `PERMANENT` 失败写 EMAIL_INVALID 的既有路径
   行为不变。

### Out of scope（显式推迟）

| 项 | 推迟理由 |
|---|---|
| 历史 39 条 SOFT 记录重分类 | 原始 DSN 分段从未落库，`bounce_record.bounce_reason` 只存 subject（`BounceCollectionService:100`），DB 内无可重解析原料。需单独的 IMAP 重扫计划。 |
| `BounceBackfillService` 改造 | 它扫 `inbound_mail_processing`，而退信在 `AutoMailReplyService:658` `return@forEach`、从不进 `processSingle`（`InboundMailProcessing` 仅在 `:1021`/`:1070` 构造）——退信结构性不在该表，改它无收益。另受 `K-backfill-readonly-inbound` 只读边界约束。 |
| `collectBounces()` 被 SEEN 饿死 | 本计划改动 2 后内联路径已能正确分类，该 sweep 成为冗余补漏网。删除属行为变更，另开计划。 |
| 把 `ManualInitialOutreachService:762` 的 EMAIL_INVALID 迁移到新出口 | 会触碰守卫白名单，扩大回归面。本计划只新增退信侧出口，不动发送侧。 |
| 开启 `operator-status-reconcile-cron` | `K-operator-status-reconcile` I-1 明确该作业**只读不修**（不注入任何 writer），开启它不会修复任何数据，只产出报告。 |
| 一专家多邮箱 / 备用地址补投 | 与本计划无关，且实测天花板 < 2%（41/2112），已否决。 |

---

## 关键不变量

### Invariant I-1：DSN 机器段必须以字节流兜底读取
- **Rule**：读取 `message/delivery-status` 分段内容时，禁止只依赖 `part.content as? String`；
  该表达式返回 null 时**必须**回退到 `part.inputStream` 解码为文本。
- **Applies to**：`BounceDetector.findDeliveryStatusBody():197`、
  `ImapMailReceiveService.extractBody():151`
- **Violation consequence**：本仓依赖只有 `spring-boot-starter-mail`（`pom.xml:56`），
  **无 `com.sun.mail:dsn`**（`find ~/.m2 -iname "*dsn*.jar"` 零结果）。
  无 DataContentHandler 时 `getContent()` 返回 InputStream，`as? String` 恒为 null →
  `extractDsnStatus()` 恒返回 null → `classifyBounceType()` 落
  `heuristicBounce -> "SOFT"` 兜底（`BounceDetector:88`）→ 所有标准硬退信记成 SOFT。
- **来源**：original（`K-mime-dsn-before-heuristic` 的补强：该条只规定了"MIME DSN 优先于
  启发式"的**顺序**，未覆盖"MIME DSN 根本读不出来"这一失败模式。Phase 6 需回写补强。）

### Invariant I-2：正文提取按 multipart 子类型分流
- **Rule**：`multipart/alternative` 的各分段是**同一内容的多种表现** → 取首个非空；
  其余 multipart（`report` / `mixed` / …）各分段是**不同内容** → 拼接。
- **Applies to**：`ImapMailReceiveService.extractBody():151`（唯一实现点）
- **Violation consequence**：
  - 无差别取首个（**现状**）→ `multipart/report` 的第 2 分段（DSN）永远读不到，
    内联路径 `AutoMailReplyService:666` 的 `detect(from, subject, mail.body)` 拿不到
    `Status:` 行，`extractDsnFromLines()` 空手而归。
  - 无差别拼接 → 正常回信的 text/plain 与 text/html 版本**重复**，污染
    `inbound_mail_processing.body` 与所有下游读者。
- **来源**：original

### Invariant I-3：EMAIL_INVALID 必须双写且经由唯一写入口
- **Rule**：退信侧标记 EMAIL_INVALID 必须**同时**落
  `expert_contact.operator_status`（MySQL）与三层 ES；写入语句**必须**位于
  `ExpertOperatorStatusService.kt` 内，禁止在 `BounceCollectionService.kt` 内直接
  `contact.copy(operatorStatus = ...)`。
- **Applies to**：`BounceCollectionService.ingest():108-113`、
  新增的 `ExpertOperatorStatusService.markEmailInvalid()`
- **Violation consequence**：
  - 只写 ES（**现状**）→ `ExpertIndexWriterService.syncOperatorStatus():67-107` 只更新
    RAW/CANDIDATE/APPLICATION 三个 ES 索引、不碰 MySQL；而
    `ManualInitialOutreachService.buildRetryableTargets():995` 的
    `it.operatorStatus != "EMAIL_INVALID"` 读的是 `expertContactRepository`（MySQL）→
    过滤恒不生效，死地址反复进重试队列。
  - 在 `BounceCollectionService.kt` 内直接写 → `OperatorStatusWriteSeamGuardTest`
    的"命中文件集合恰好等于白名单"断言失败，`mvn test` 红。
- **来源**：K-operator-status-single-writer、K-operator-status-write-seam-guard

### Invariant I-4：markEmailInvalid 不得回退已推进状态
- **Rule**：当 contact 当前 `operatorStatus` 落在
  `{REPLIED, MATERIALS_RECEIVED, INVITED, COMPLETED}` 中任一值时，
  `markEmailInvalid()` 必须**直接返回入参，零 DB / 零 ES 交互**。
  仅 `NOT_CONTACTED` 与 `CONTACTED` 允许被标记为 EMAIL_INVALID。
- **Applies to**：`ExpertOperatorStatusService.markEmailInvalid()`
- **Violation consequence**：专家已回信即证明该地址可达；后到的退信（自动回复循环、
  被误判的软退、同域其他地址的 DSN）会把 REPLIED 抹成 EMAIL_INVALID。
  且因 `updateAutomatically():53` 对 EMAIL_INVALID **无条件短路**，该专家将
  **永久**无法通过自动路径恢复，只能人工 `changeStatus` 救回。
- **来源**：original（由 K-operator-status-single-writer 的 I-1「单调不回退」推导；
  该条原文只覆盖 `updateAutomatically`，未覆盖旁路终态的写入方向）

### Invariant I-5：markEmailInvalid 不写 CHANGE_OPERATOR_STATUS 审计
- **Rule**：`markEmailInvalid()` 属自动路径，**禁止**调用
  `operatorActionLogService.record(actionType = OperatorActionType.CHANGE_OPERATOR_STATUS, …)`。
- **Applies to**：`ExpertOperatorStatusService.markEmailInvalid()`
- **Violation consequence**：`operator_action_log` 中的 `CHANGE_OPERATOR_STATUS`
  是对账作业的**人工覆盖判别器**——`OperatorStatusReconcileService:60-65` 用
  `findContactIdsWithChangeOperatorStatusLogs()` 把这些 contact 单列为 `HUMAN_OVERRIDE`
  且不计入异常。自动路径写它 → 退信标记被误分类为人工覆盖 → 对账报告四类统计失真。
  参照系：既有自动出口 `updateAutomatically()` 全程不写审计，人工出口
  `changeStatus():31-42` 才写。
- **来源**：K-operator-status-single-writer、K-operator-status-reconcile

---

## 现状审计

### Store 1：`bounce_record`（MySQL）

**Schema**（`V29__create_bounce_record.sql` 全文核对）：
```
bounce_type    VARCHAR(20) NOT NULL COMMENT 'HARD or SOFT'
dsn_status     VARCHAR(20) COMMENT 'e.g. 5.1.1, 4.2.2'
bounce_reason  VARCHAR(1000)
failed_recipient / original_message_id / original_expert_contact_id / sender_account_code
UNIQUE KEY uk_bounce_message_id (bounce_message_id)
```
> 注：`failed_recipient` 列由后续迁移追加（`K-bounce-visible-fields-persisted` 记录的
> P1 修复），V29 原文不含该列。

**Write paths（唯一写入点，grep 佐证见下）**：
1. `BounceCollectionService.ingest():86-131` —— 唯一 `bounceRecordRepository.save(...)`。

**Read paths**：
1. `BounceRateMonitorService.checkAndPause():24` — `countHardBouncesSince(accountCode, since)`，
   **只数 `bounce_type='HARD'`**。
2. `ExpertReachabilitySyncService.buildHardBouncedOrcids():84-91` —
   `.filter { it.bounceType == "HARD" && it.originalExpertContactId != null }`。
3. `OperatorStatusReconcileService:70-74` — 同款 `bounceType == "HARD"` 过滤。
4. `BounceController` 列表接口。

**Interaction point BP-1**：写入侧的 `bounceType` 误判 → 上述 **3 个** HARD 读者全部失真。
其中读者 2、3 是设计中的"全量自愈/对账"机制，与写入侧共享同一被污染列，
**兜底与主路径同源，自愈失效**。

### Store 2：`expert_contact.operator_status`（MySQL）

**Write paths（守卫测试机器强制，白名单闭包）**：
1. `ExpertOperatorStatusService.changeStatus():30` — 人工，写 CHANGE_OPERATOR_STATUS 审计。
2. `ExpertOperatorStatusService.updateAutomatically():61` — 自动，不写审计，
   单调不回退 + EMAIL_INVALID 短路（`:53`）。
3. `ManualInitialOutreachService:273` 区域 — 建行初始化 `NOT_CONTACTED`。
4. `ManualInitialOutreachService:762` — 发送期 `SmtpErrorCategory.PERMANENT` 标记 EMAIL_INVALID
   （**两写**：`expertContactRepository.save(contact.copy(...))` + `syncOperatorStatus(...)`）。

守卫：`OperatorStatusWriteSeamGuardTest`（`src/test/kotlin/com/weibo/talentintroduction/campaign/`）
递归扫描 `src/main/kotlin` 全部 `.kt`，断言命中文件集合**恰好等于**
`ALLOWED_WRITE_SITES = {ExpertOperatorStatusService.kt, ManualInitialOutreachService.kt}`。

**Read paths（与本计划相关）**：
1. `ManualInitialOutreachService.buildRetryableTargets():995` —
   `!hasSentIntroduction(it.id!!) && it.operatorStatus != "EMAIL_INVALID"`，**读 MySQL**。
2. `ExpertOperatorStatusService.updateAutomatically():53` — EMAIL_INVALID 无条件短路。
3. `OperatorStatusReconcileService` — DB 实际值三方比对。

**Interaction point EP-1**：`BounceCollectionService.ingest():109` 只调
`expertIndexWriterService.syncOperatorStatus(...)`（**ES-only**，见 Store 3），
而读者 1 读 MySQL → 写读不同源，过滤恒失效。这是 `EMAIL_INVALID = 0` 的直接成因。

### Store 3：ES 三层 `operatorStatus`（RAW / CANDIDATE / APPLICATION）

**Write path**：`ExpertIndexWriterService.syncOperatorStatus():67-107` /
`syncOperatorStatusBatch():114`。逐层 HEAD 探测存在性后 `_update`。
`NOT_CONTACTED` 走 `ctx._source.remove('operatorStatus')` 字段移除脚本
（ES 侧「未联系」= 字段缺失，见 K-operator-status-single-writer I-5）。

**关键事实**：该方法**不写 MySQL**。逐行确认 `:67-107` 无任何 `expertContactRepository` 调用。

### Store 4：入站邮件正文提取链路（非存储，但为共享读路径）

`ImapMailReceiveService.extractBody():151`（`private`，唯一实现）
← `toReceivedMail():149` ← `fetchInboundSince()` / `fetchByUids()` → `ReceivedMail.body`

**下游消费者**：
1. `AutoMailReplyService:666` — `bounceDetector.detect(mail.from, mail.subject, mail.body)`（内联退信判定）
2. `AutoMailReplyService:1021 / :1070` — 写 `inbound_mail_processing.body`
3. QA 关键词匹配链路（经 `processSingle`）
4. `AiReplyDraftService` prompt 输入

**Interaction point IP-1**：改 `extractBody` 会同时波及消费者 1（本计划**要**的效果）
与消费者 2/3/4（本计划**不要**的副作用）。I-2 即为此边界。

### 证据（grep receipts，遵循 K-plan-quantified-claims-need-grep-receipts）

```bash
# ① 无 DSN provider —— I-1 的前提
$ grep -n "mail" pom.xml | head
56:            <artifactId>spring-boot-starter-mail</artifactId>
$ find ~/.m2 -iname "*dsn*.jar"
(零输出)

# ② syncOperatorStatus 不写 MySQL —— EP-1 的直接证据
$ grep -n "expertContactRepository" \
    src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt
(零输出)

# ③ bounce_record 唯一写入点
$ grep -rn "bounceRecordRepository.save" --include=*.kt src/main/kotlin
src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt:93

# ④ bounce_type == HARD 的全部读者（3 处）
$ grep -rn 'bounceType == "HARD"\|countHardBounces' --include=*.kt src/main/kotlin
.../mail/service/BounceCollectionService.kt:108
.../mail/service/BounceRateMonitorService.kt:24
.../expert/service/ExpertReachabilitySyncService.kt:87
.../campaign/service/OperatorStatusReconcileService.kt:71

# ⑤ EMAIL_INVALID 在 ManualInitialOutreachService 的全部出现（3 处）
$ grep -n "EMAIL_INVALID" .../ManualInitialOutreachService.kt
762:  contact.copy(operatorStatus = "EMAIL_INVALID", updatedAt = LocalDateTime.now())
764:  expertIndexWriterService.syncOperatorStatus(normOrcid, "EMAIL_INVALID")
995:  !hasSentIntroduction(it.id!!) && it.operatorStatus != "EMAIL_INVALID"

# ⑥ 直接构造 BounceCollectionService 的测试（2 处，决定构造签名策略）
$ grep -rn "BounceCollectionService(" --include=*.kt src/ | grep -v "^src/main"
src/test/.../BounceBackfillServiceTest.kt:16
src/test/.../BounceCollectionServiceTest.kt:36

# ⑦ 两处均使用具名/位置参数省略尾部可空默认参数 reachabilitySyncService
#    → 新增参数必须放在尾部并给默认值，否则两个测试文件编译失败
```

### 线上实测数据（决策依据）

| 指标 | 值 | 来源 |
|---|---|---|
| INTRODUCTION 已发送 | 2112 | `mail_record` `direction='OUTBOUND' AND mail_type='INTRODUCTION' AND send_status='SENT'` |
| bounce_record HARD | 2（dsn_status 全非空） | `GROUP BY bounce_type` |
| bounce_record SOFT | 39（**dsn_status 39/39 全 NULL**） | 同上 |
| expert_contact EMAIL_INVALID | **0** | `GROUP BY operator_status` |

> SOFT 的 `dsn_status` 100% 缺失是 I-1 的决定性指纹：真软退信带 `4.x.x`，不可能一条都没有。
> `EMAIL_INVALID = 0` 是 I-3 的决定性指纹：发送期 PERMANENT 路径（`:762`）是双写的，
> 若曾触发 MySQL 必有值；为 0 说明 2112 封中该路径**从未触发**，
> 即中继在 RCPT TO 阶段照单全收、失效地址一律走异步 DSN——
> **异步 DSN 是本系统得知地址失效的唯一渠道。**

---

## 实现方案

### 阶段 A：退信 MIME 解析（子系统 1）

#### A-1　`BounceDetector` 新增字节流兜底读取　【I-1】

在 `BounceDetector` 私有方法区新增：

```kotlin
/**
 * I-1：绕开 DataHandler 读分段文本。
 * 本仓无 com.sun.mail:dsn，message/delivery-status 无 DataContentHandler，
 * getContent() 返回 InputStream 而非 String；Part.getInputStream() 返回解码后的
 * 内容流，不经 mailcap 查表，对任意 MIME 类型可用。
 */
private fun readPartAsText(part: Part): String =
    try {
        part.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Exception) {
        ""
    }
```

#### A-2　`findDeliveryStatusBody` 加兜底　【I-1】

`BounceDetector.kt:197-199`，仅改 `message/delivery-status` 分支：

```kotlin
if (part.isMimeType("message/delivery-status")) {
    return (part.content as? String)?.takeIf { it.isNotBlank() }
        ?: readPartAsText(part).takeIf { it.isNotBlank() }
}
```

> `as? String` 保留在前：将来若引入 dsn 依赖，行为不回归。
> `multipart/*` 递归分支与 `findEmbeddedRfc822Message` 保持原样，不动。

#### A-3　`extractBody` 分流 + DSN 分段　【I-1, I-2】

`ImapMailReceiveService.kt:151`，整体替换该方法。同时把可见性由 `private` 改为
`internal`（Kotlin `internal` 在同一 Maven module 的 test source set 可见，
用于 A-6 的直接单测）：

```kotlin
internal fun extractBody(part: Part): String {
    if (part.isMimeType("text/plain")) {
        return part.content as? String ?: ""
    }
    if (part.isMimeType("text/html")) {
        return stripHtml(part.content as? String ?: "")
    }
    // I-1：DSN 机器段，getContent() 无 handler 时返回 InputStream，走字节流兜底
    if (part.isMimeType("message/delivery-status")) {
        return readPartAsText(part)
    }
    if (part.content is Multipart) {
        val multipart = part.content as Multipart
        val segments = (0 until multipart.count)
            .map { extractBody(multipart.getBodyPart(it)) }
            .filter { it.isNotBlank() }
        // I-2：alternative 各分段是同一内容的多种表现 → 取首个；
        //      report / mixed 等各分段是不同内容 → 拼接
        return if (part.isMimeType("multipart/alternative")) {
            segments.firstOrNull().orEmpty()
        } else {
            segments.joinToString("\n")
        }
    }
    return ""
}
```

同时在本类新增与 A-1 同款的 `readPartAsText`（两个类各自私有持有，不新建共享工具类
——跨模块共享工具会引入第 3 个子系统，超出 Phase 2 限额）。

#### A-4　放宽 HARD 判定正则　【I-1 加固】

`BounceDetector.kt:230-231`：

```kotlin
private val DSN_STATUS_PATTERN     = Regex("""\b5\d\d[\s-]+5\.\d\.\d\b""")
private val HARD_SMTP_CODE_PATTERN = Regex("""\b5\d\d[\s-]+5\.\d\.\d\b""")
```

> 覆盖多行 SMTP 回复的连字符续行形式（`550-5.1.1 …`）。
> A-1~A-3 落地后主判定走 `classifyBounceType` 的 `dsnStatus.startsWith("5")` 主路，
> 本项仅为兜底加固，**不得**作为唯一依赖。

> **注意：`AutoMailReplyService` 一行不改。** A-3 之后 `mail.body` 已含 `Status:` 行，
> `:666` 现有的 `detect(from, subject, mail.body)` → `extractDsnFromLines():92` 即可命中，
> 无需把 `MimeMessage` 塞进 `ReceivedMail`（那会让每封入站邮件多驻留一份完整 MIME 副本）。

### 阶段 B：EMAIL_INVALID 双写（子系统 2）

#### B-1　`ExpertOperatorStatusService` 新增 `markEmailInvalid`　【I-3, I-4, I-5】

在 `ExpertOperatorStatusService.kt` 类内追加（文件当前 67 行，追加于 `updateAutomatically` 之后）：

```kotlin
/**
 * 退信侧旁路终态写入口（I-3）。EMAIL_INVALID 不在 OperatorStatus 枚举内，
 * 既有两个出口都无法表达它：
 *   - updateAutomatically 形参类型为 OperatorStatus，字面无法传入；
 *   - changeStatus 走 OperatorStatus.fromName() 会 error()，且会写
 *     CHANGE_OPERATOR_STATUS 审计（对账的人工覆盖判别器，I-5 禁止）。
 * 故新增本方法，写入语句留在本文件内以保持守卫白名单闭包不变。
 */
@Transactional
fun markEmailInvalid(contact: ExpertContact, reason: String): ExpertContact {
    // I-4：已推进状态不回退 —— 已回信即证明地址可达
    val current = OperatorStatus.entries.firstOrNull { it.name == contact.operatorStatus }
    if (current != null && current.ordinal >= OperatorStatus.REPLIED.ordinal) {
        return contact
    }
    // 幂等：已是 EMAIL_INVALID 则零交互
    if (contact.operatorStatus == EMAIL_INVALID) {
        return contact
    }
    val updated = expertContactRepository.save(contact.copy(operatorStatus = EMAIL_INVALID))
    expertIndexWriterService.syncOperatorStatus(updated.orcidId, EMAIL_INVALID)
    // I-5：自动路径不写 CHANGE_OPERATOR_STATUS 审计
    return updated
}

companion object {
    const val EMAIL_INVALID = "EMAIL_INVALID"
}
```

> `reason` 形参当前仅用于调用方可读性与未来日志；若实现时判定其为死参数，
> 可省略——但**不得**用它去写 `operator_action_log`（I-5）。

#### B-2　`BounceCollectionService` 改为调用新出口　【I-3】

构造函数尾部追加可空默认参数（证据 ⑥⑦：两个测试文件以具名/位置参数省略尾部参数，
放尾部加默认值可保持 `BounceBackfillServiceTest` 零改动）：

```kotlin
private val reachabilitySyncService: ExpertReachabilitySyncService? = null,
// I-3：EMAIL_INVALID 双写出口。可空默认参数沿用上方 reachabilitySyncService 先例，
// 使既有测试构造无需改签名；生产由 Spring 注入。
private val expertOperatorStatusService: ExpertOperatorStatusService? = null
```

`ingest():108-113` 的 HARD 分支改为：

```kotlin
if (signal.bounceType == "HARD" && originalContact != null) {
    // I-3：先落 MySQL + ES（唯一写入口），再增量写 reachability
    try {
        expertOperatorStatusService?.markEmailInvalid(originalContact, "HARD_BOUNCE")
    } catch (e: Exception) {
        log.warn("Failed to mark EMAIL_INVALID for orcid={}", originalContact.orcidId, e)
    }
    try {
        reachabilitySyncService?.markBlockedByContact(originalContact)
    } catch (e: Exception) {
        log.warn("Failed to mark reachability BLOCKED_BOUNCED for orcid={}", originalContact.orcidId, e)
    }
}
```

> 原 `expertIndexWriterService.syncOperatorStatus(...)` 调用**删除**——
> `markEmailInvalid` 内部已含 ES 同步，保留会造成重复 `_update`。
> 删除后 `expertIndexWriterService` 若在本类无其他用途，仍保留构造参数
> （两个测试文件按位置传参，删参数会破坏 `BounceBackfillServiceTest:20`）。
>
> 循环依赖核对：`ExpertOperatorStatusService` 依赖
> `ExpertContactRepository` / `OperatorActionLogService` / `ExpertIndexWriterService`，
> 三者均不反向依赖 `BounceCollectionService`，无环。

### 阶段 C：测试（与阶段 A/B 同批交付）

#### C-1　`BounceDetectorTest` 夹具往返序列化　【验证 I-1】

**这是整个计划的前提检查点。** 现有 `dsnBounce()` / `neutralMimeDsn():113-140` 用
`dsnPart.setContent(String, "message/delivery-status")` 构造——该重载把 **String 对象**
直接存入 DataHandler，`getContent()` 原样返回，**不查 DataContentHandler**，
故 `part.content as? String` 在测试中成功。真实 IMAP 邮件走 InputStream 路径。
**这就是本 bug 通过全绿测试上线的原因。**

新增辅助方法并在 `parseBounceDetails` 系列断言中套用：

```kotlin
private fun roundTrip(message: MimeMessage): MimeMessage {
    val buf = java.io.ByteArrayOutputStream()
    message.writeTo(buf)
    return MimeMessage(
        Session.getDefaultInstance(Properties()),
        java.io.ByteArrayInputStream(buf.toByteArray())
    )
}
```

新增用例：
- `parseBounceDetails classifies 5_1_1 as HARD after MIME round trip`
- `parseBounceDetails classifies 4_2_2 as SOFT after MIME round trip`

**门禁**：这两个用例在 A-1/A-2 落地**之前**必须为**红**。执行方须先只加测试、
跑一次确认失败并记录输出，再实施 A-1/A-2。若加完测试即为绿，说明未复现线上失败模式，
**停止并回报**——此时 A-1/A-2 的前提假设不成立。

#### C-2　`ImapMailReceiveServiceTest`（新建）　【验证 I-1, I-2】

新建 `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt`，
直接调用 `internal` 的 `extractBody`：

- `extractBody includes delivery-status segment for multipart report`
  —— 构造 `multipart/report`（text/plain "Delivery failed" + delivery-status `Status: 5.1.1`），
  往返序列化后断言返回值 `contains("Status: 5.1.1")`。
- `extractBody does not duplicate content for multipart alternative`
  —— 构造 `multipart/alternative`（text/plain "Hello" + text/html `<p>Hello</p>`），
  往返后断言返回值等于 `"Hello"`，且 `"Hello"` 出现次数为 **1**。
  **这是 must-NOT-change 第 1 项的回归护栏。**

#### C-3　`ExpertOperatorStatusServiceTest` 补 markEmailInvalid　【验证 I-3, I-4, I-5】

沿用现有 `contact(operatorStatus)` 工厂（`:22-33`）与 mock 装配（`:18-21`），新增：

- `markEmailInvalid writes both MySQL and ES for CONTACTED`
  —— 断言 `expertContactRepository.save` 被调用一次且入参 `operatorStatus == "EMAIL_INVALID"`，
  `expertIndexWriterService.syncOperatorStatus(_, "EMAIL_INVALID")` 被调用一次。
- `markEmailInvalid does not downgrade REPLIED`
- `markEmailInvalid does not downgrade MATERIALS_RECEIVED`
- `markEmailInvalid does not downgrade INVITED`
  —— 三者均断言 `Mockito.verifyNoInteractions(expertContactRepository, expertIndexWriterService)`
  且返回值 `=== contact`（入参原对象）。
- `markEmailInvalid never writes CHANGE_OPERATOR_STATUS audit`
  —— 断言 `Mockito.verifyNoInteractions(operatorActionLogService)`。
- `markEmailInvalid is idempotent when already EMAIL_INVALID`

#### C-4　`BounceCollectionServiceTest` 装配新依赖　【验证 I-3 集成】

`:36-44` 的构造调用改为具名参数并追加
`expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)`
（同时需具名传 `reachabilitySyncService = null` 或保持省略——因新参数在其后，
必须改为具名传参）。新增用例：

- `ingest marks EMAIL_INVALID via ExpertOperatorStatusService for HARD bounce`
  —— 断言 `markEmailInvalid` 被调用一次，入参 contact id 正确。
- `ingest does not mark EMAIL_INVALID for SOFT bounce`
  —— 断言 `verifyNoInteractions(expertOperatorStatusService)`。

> `BounceBackfillServiceTest:16-24` 使用全具名参数且未传尾部可空参数，
> 新增尾部默认参数后**无需改动**——执行时须实际编译确认。

#### C-5　守卫测试零改动核验　【验证 must-NOT-change 第 2 项】

`OperatorStatusWriteSeamGuardTest` **不得修改**。B-1 的写入语句位于
`ExpertOperatorStatusService.kt`（已在白名单内），B-2 不含 `operatorStatus = ` 赋值
（只调方法），故白名单闭包断言应自然通过。若该测试变红，说明 B-2 引入了
未预期的赋值语句，**必须改代码而非改白名单**。

---

## 变更文件清单

| # | 文件 | 类型 | 改动 | 子系统 |
|---|---|---|---|---|
| 1 | `src/main/kotlin/.../mail/service/BounceDetector.kt` | 主 | 新增 `readPartAsText`；`findDeliveryStatusBody:197` 加兜底；`:230-231` 正则 | 1 |
| 2 | `src/main/kotlin/.../mail/service/ImapMailReceiveService.kt` | 主 | `extractBody:151` 重写 + 可见性改 `internal`；新增 `readPartAsText` | 1 |
| 3 | `src/main/kotlin/.../campaign/service/ExpertOperatorStatusService.kt` | 主 | 新增 `markEmailInvalid` + companion 常量 | 2 |
| 4 | `src/main/kotlin/.../mail/service/BounceCollectionService.kt` | 主 | 构造尾部加可空参数；`ingest:108-113` 改调新出口 | 2 |
| 5 | `src/test/kotlin/.../mail/service/BounceDetectorTest.kt` | 测试 | 新增 `roundTrip` + 2 用例 | 1 |
| 6 | `src/test/kotlin/.../mail/service/ImapMailReceiveServiceTest.kt` | 测试（新建） | 2 用例 | 1 |
| 7 | `src/test/kotlin/.../campaign/service/ExpertOperatorStatusServiceTest.kt` | 测试 | 新增 6 用例 | 2 |
| 8 | `src/test/kotlin/.../mail/service/BounceCollectionServiceTest.kt` | 测试 | 构造改具名 + 新增 2 用例 | 2 |

**文件数 8 ≤ 10 ✅　子系统数 2 ≤ 2 ✅　新增共享存储字段 0 ✅**

> 清单外的文件一律不改。特别地：`AutoMailReplyService.kt`、`BounceBackfillService.kt`、
> `OperatorStatusWriteSeamGuardTest.kt`、`ManualInitialOutreachService.kt`
> **均不在清单内**，执行时若发现必须改动，说明计划有缺陷，停止并回报。

---

## 验证命令

> 本项目**必须**用 JDK 11（zulu-11）。裸 `mvn` 会因 JDK 版本不符构建失败。
> 来源：项目根 `CLAUDE.md` 的 `## Commands` 章节与文末「项目元信息」
> （`test_command:` / `build_command:`）。

```bash
# 全量测试（回归门禁，含 OperatorStatusWriteSeamGuardTest）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=BounceDetectorTest,ImapMailReceiveServiceTest,ExpertOperatorStatusServiceTest,BounceCollectionServiceTest

# 守卫测试单跑（must-NOT-change 第 2 项）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=OperatorStatusWriteSeamGuardTest

# C-1 门禁：A-1/A-2 实施前，单跑往返用例确认为红
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='BounceDetectorTest#parseBounceDetails classifies 5_1_1 as HARD after MIME round trip'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

**通过判据**：退出码 0，且输出含 `Tests run: N, Failures: 0, Errors: 0`。
C-1 门禁项例外——它在 A-1/A-2 实施前**必须**失败（`Failures: 1`），实施后转为 0。

---

## 验收标准

| 不变量 | 验证方式 | 断言 |
|---|---|---|
| **I-1** | `BounceDetectorTest` 往返用例 + `ImapMailReceiveServiceTest` DSN 用例 | 往返后的标准 DSN 消息，`parseBounceDetails().bounceType == "HARD"` 且 `dsnStatus == "5.1.1"`；`extractBody` 输出含 `Status: 5.1.1`。另 grep 确认 `findDeliveryStatusBody` 内存在 `readPartAsText` 兜底调用 |
| **I-2** | `ImapMailReceiveServiceTest` alternative 用例 | `multipart/alternative`（plain "Hello" + html `<p>Hello</p>`）的 `extractBody` 返回 `"Hello"`，`"Hello"` 出现次数 == 1 |
| **I-3** | `BounceCollectionServiceTest` + `ExpertOperatorStatusServiceTest` | HARD 退信触发 `markEmailInvalid` 恰 1 次；`markEmailInvalid` 内 `expertContactRepository.save` 与 `syncOperatorStatus` 各恰 1 次。grep 确认 `BounceCollectionService.kt` 内**无** `operatorStatus = ` 赋值 |
| **I-4** | `ExpertOperatorStatusServiceTest` 三个 downgrade 用例 | REPLIED / MATERIALS_RECEIVED / INVITED 入参下 `verifyNoInteractions(expertContactRepository, expertIndexWriterService)`，返回值恒等入参 |
| **I-5** | `ExpertOperatorStatusServiceTest` 审计用例 | `verifyNoInteractions(operatorActionLogService)` |
| **must-NOT-change 2** | 执行「验证命令」节的守卫测试单跑命令 | 通过，且 `OperatorStatusWriteSeamGuardTest.kt` 的 git diff 为空 |
| **回归** | 执行「验证命令」节的全量测试命令 | 通过 |
| **交叉 BP-1** | 代码走查 | `BounceRateMonitorService` / `ExpertReachabilitySyncService` / `OperatorStatusReconcileService` 三处 HARD 读者**未改动**，其行为改善纯由写入侧修正带来 |
| **交叉 EP-1** | `BounceCollectionServiceTest` HARD 用例 | 写入侧调用链落到 MySQL save，与 `buildRetryableTargets:995` 的读源一致 |

---

## 人工验收清单

### A-1：标准 DSN 退信被正确分类为 HARD
- **前置条件**：一个已启用的发件账号；能向其 INBOX 投递一封标准
  `multipart/report; report-type=delivery-status` 退信（可用真实发信到一个不存在的
  `@gmail.com` 地址触发，或手工投递一封构造的 DSN）。该退信的
  `Original-Message-ID` 需匹配一条既有 `mail_record.message_id`。
- **操作步骤**：
  1. 触发一次自动回复轮询（后台「自动回复」任务，或调用对应接口）。
  2. 打开退信列表页 `GET /api/mail/bounces`。
- **预期结果**：新增一条记录，「退信类型」列显示 **HARD**（不是 SOFT），
  `dsn_status` 列显示形如 **`5.1.1`** 的具体状态码（**不为空**）。
- **覆盖**：I-1、需求描述 observable outcome 第 1 条

### A-2：硬退信后专家在 MySQL 中变为 EMAIL_INVALID 并被移出重试队列
- **前置条件**：A-1 中那条退信可溯源到一个 `operator_status = 'CONTACTED'` 的
  `expert_contact` 行（记下其 `id` 与 `orcid_id`）。
- **操作步骤**：
  1. 执行 `SELECT id, operator_status FROM expert_contact WHERE id = <该 id>;`
  2. 在专家列表页按「未联系」筛选，确认该专家不再出现在待发送候选中。
  3. 手动触发一次批量发送任务（roundSize 设为足够大以覆盖全量候选）。
  4. 查看该次任务执行日志。
- **预期结果**：
  1. 第 1 步返回 `operator_status = 'EMAIL_INVALID'`（当前线上为 `CONTACTED`）。
  3~4 步中该专家**不出现**在本轮收件人里，日志无该专家的发送记录。
- **覆盖**：I-3、EP-1、需求描述 observable outcome 第 2 条

### A-3【回归】正常专家回信正文不重复
- **前置条件**：用任意邮件客户端（会同时发 text/plain 与 text/html 的，如 Gmail 网页版）
  向发件账号回一封内容为 `Hello from acceptance test` 的邮件。
- **操作步骤**：
  1. 触发自动回复轮询。
  2. 在「收发信箱」中打开该封入站邮件，查看正文。
  3. 执行 `SELECT body FROM inbound_mail_processing ORDER BY id DESC LIMIT 1;`
- **预期结果**：正文显示 `Hello from acceptance test` **恰一次**；
  第 3 步返回的 body 中该句**不出现两遍**，无 HTML 标签残留段落。
- **覆盖**：I-2、must-NOT-change 第 1 条、IP-1

### A-4【回归】已回信专家不被退信打回
- **前置条件**：构造一个 `operator_status = 'REPLIED'` 的 contact
  （`UPDATE expert_contact SET operator_status='REPLIED' WHERE id=<测试 id>;`），
  并投递一封 `Original-Message-ID` 指向该 contact 某封外发邮件的硬退信。
- **操作步骤**：
  1. 触发自动回复轮询。
  2. `SELECT operator_status FROM expert_contact WHERE id = <测试 id>;`
  3. `SELECT bounce_type FROM bounce_record ORDER BY id DESC LIMIT 1;`
- **预期结果**：第 2 步仍为 **`REPLIED`**（未被改成 EMAIL_INVALID）；
  第 3 步为 **`HARD`**（退信本身仍被正确记录，只是不改状态）。
- **覆盖**：I-4、must-NOT-change 第 3 条

### A-5【回归】发送期 PERMANENT 失败路径不变
- **前置条件**：一个 `operator_status = 'NOT_CONTACTED'`、邮箱地址为语法合法但域名
  确定不可投递的 contact。
- **操作步骤**：
  1. 对该专家手动发一封介绍邮件。
  2. 若 SMTP 返回 5xx，查 `SELECT operator_status FROM expert_contact WHERE id=<id>;`
- **预期结果**：`operator_status = 'EMAIL_INVALID'`，与改动前行为一致。
  若中继不返回同步 5xx（本系统实测即如此），本条记为 **N/A** 并注明，不算失败。
- **覆盖**：must-NOT-change 第 5 条

### A-6【回归】软退信不改状态
- **前置条件**：投递一封 `Status: 4.2.2`（邮箱满）的 DSN，可溯源到一个
  `CONTACTED` 的 contact。
- **操作步骤**：
  1. 触发自动回复轮询。
  2. 查退信列表的「退信类型」列。
  3. `SELECT operator_status FROM expert_contact WHERE id=<id>;`
- **预期结果**：第 2 步为 **SOFT**、`dsn_status` 为 **`4.2.2`**；
  第 3 步仍为 **`CONTACTED`**（软退不标失效）。
- **覆盖**：I-1（SOFT 侧）、I-3（不误伤）

### A-7【回归】变更范围与写入口白名单核验
> 本条为命令式核验（must-NOT-change 第 2、4 项按性质无法黑盒目测，
> 但可由验收人直接执行命令判定）。
- **前置条件**：已切到实现分支，工作区干净。
- **操作步骤**：
  1. 执行 `git diff --stat <基线 commit>..HEAD` 查看改动文件清单。
  2. 执行「验证命令」节的**守卫测试单跑**命令。
  3. 执行 `git diff <基线 commit>..HEAD -- '*OperatorStatusWriteSeamGuardTest.kt' '*AutoMailReplyService.kt' '*BounceBackfillService.kt' '*ManualInitialOutreachService.kt'`
- **预期结果**：
  1. 改动文件**恰为**「变更文件清单」中的 8 个，无第 9 个文件。
  2. 输出含 `Tests run: 1, Failures: 0, Errors: 0`。
  3. 输出为**空**（这 4 个文件一行未改）。
- **覆盖**：must-NOT-change 第 2 条、第 4 条

### A-8【跨路径】硬退信同时打通 reachability
- **前置条件**：同 A-2（一条可溯源到 CONTACTED contact 的硬退信）。
- **操作步骤**：
  1. 触发自动回复轮询。
  2. 在专家列表页对该专家查看「可达性」字段；或直接查 CANDIDATE 索引该
     `orcidId` 文档的 `reachability` 字段。
  3. 新建一个批量发送任务配置，`reachabilityFilter` 选「排除不可达」，
     预览收件人数量。
- **预期结果**：
  2. `reachability = BLOCKED_BOUNCED`。
  3. 该专家**不在**预览收件人列表中。
- **覆盖**：BP-1（写入侧 bounceType 修正 → `ExpertReachabilitySyncService` 读者受益）

---

## 上线后观测（非验收项）

修复上线后 2~4 周内，每周记录一次：

```sql
SELECT bounce_type, COUNT(*), SUM(dsn_status IS NULL) AS dsn_null
FROM bounce_record WHERE received_at >= '<上线日期>' GROUP BY bounce_type;

SELECT operator_status, COUNT(*) FROM expert_contact GROUP BY operator_status;
```

判读：新增 SOFT 记录若仍大量 `dsn_status IS NULL`，说明 I-1 未彻底修复。
历史 39 条 SOFT 应标注为「不可用于退信率统计」（原始 DSN 分段未落库，不可回溯重分类）。

`BounceRateMonitorService` 的 5%/7 天自动暂停阈值在修复后才真正生效——
上线后首次出现真实硬退信率时需人工确认暂停行为符合预期（阈值：
`DEFAULT_THRESHOLD = 0.05`、`MIN_SAMPLE_SIZE = 20`、`DEFAULT_WINDOW_DAYS = 7`）。
