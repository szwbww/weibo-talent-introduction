# 入站 Message-ID 供应商前缀归一化：恢复 `IN_REPLY_TO` 与退信原信关联

> 用 create-p skill 编写。独立计划，无前置依赖，可独立部署与验证。
> 姊妹计划：`expert-profile-absence-not-error.md`。两者无依赖关系，文件零重叠。
> 与 `material-reminder-01-threading.md` 的关系：该计划的 must-NOT-change 要求
> 「`UnmatchedInboundMailService.suggestCandidates()` 的 `IN_REPLY_TO`（confidence 90）匹配继续可用」，
> 本计划是对该能力的**修复**，方向一致，且文件零重叠（本计划不碰 `SmtpMailDeliveryService` /
> `ManualExpertMailService` / `IntroductionMailComposer`）。两计划可任意顺序落地。

## 需求描述

**可观察结果**

1. 专家回复我方外发邮件后，在「收发件箱 → 待处理」里打开该来信，候选专家列表中出现 `IN_REPLY_TO`（置信度 90）的建议项，而非只有 `NAME_OR_EMAIL_MATCH`（置信度 60）。
2. 退信（bounce）能关联回原始外发邮件所属的联系人，而不再退化为按 `failedRecipient` 邮箱兜底匹配。
3. 上述两项在收件人 MUA **不加**供应商前缀时（如非腾讯企业邮通道投递）行为与改动前**完全一致**。

**必须不变（must NOT change）**

- `mail_record.message_id` 的**写入侧零改动** —— 仍存 `SmtpMailDeliveryService.kt:60` 的 `delivered.messageId`（我方交给中继前的值）。本计划不引入"回写投递后 Message-ID"的机制（技术上也拿不到，改写发生在中继）。
- `ComposedMail.messageId` 的四种既有生成格式全部不变：`<intro-{orcid}-{uuid}@{domain}>`、`<reminder-{contactId}-{uuid}@{domain}>`、`<manual-outreach-{orcid}-{uuid}@weibo.com>`、`<manual-rich-{...}@weibo.com>`，以及 5 个不设 messageId 的构造点仍走 JavaMail 默认（来源: K-message-id-fingerprint 的 2026-08-06 修正表）。
- `suggestCandidates()` 的候选顺序、去重逻辑（`candidates.none { it.contact.id == contact.id }`）、`IN_REPLY_TO` 的 `confidence = 90`、`NAME_OR_EMAIL_MATCH` 的 `confidence = 60` 全部不变。
- `BounceCollectionService.resolveOriginalContact()` 的 `?: signal.failedRecipient?.let { ... }` 兜底分支不变 —— 归一化只是让前一分支更容易命中。
- `ImapMailReceiveService.kt:135` 读取 `In-Reply-To` 头的方式不变；`mail_record.in_reply_to` 的落库值不变（仍存原始头值，含供应商前缀）。
- `MailRecordRepository.findByMessageId()` 的方法签名与 Spring Data JDBC 派生查询语义不变。

**不在范围（out of scope）**

- 给 `mail_record` 增加归一化列或唯一索引 —— 本计划**不新增 Flyway migration**。
- 修复其余 4 个缺 `Message-ID` 的 `ComposedMail` 构造点（会议邀请 / 会议确认 / QA 自动回复 / 人工富文本回复）—— 既有缺陷，`material-reminder-01-threading.md` 已明确移出范围并单独立项。
- Message-ID 域名硬编码 `@weibo.com`（`ManualInitialOutreachService.kt:587`、`ManualReplySendAttemptService.kt:35`）—— 观察项，单独立项。
- `List-Unsubscribe` / DKIM 相关任何改动。
- 更换发送服务商或自签 DKIM。
- 本计划不触及任何前端文件，故无 `## 样式契约` 一节。

---

## 关键不变量

### Invariant I-1: 归一化只剥离精确的供应商前缀，不得对其后内容做任何格式假设

- Rule: 归一化仅移除 local-part 开头**恰好匹配** `^[0-9A-F]{16}\+` 的部分（16 位**大写**十六进制 + 一个 `+`）。剥离后的剩余部分**原样保留**，禁止校验它是否符合我方的 `intro-` / `reminder-` / `manual-outreach-` / `manual-rich-` 任一格式。不匹配该正则时返回原值。
- Applies to: 新增的 `MessageIdNormalizer.stripVendorPrefix()`。
- Violation consequence: 生产实证 —— 2026-07-05 投递的一封 INTRODUCTION 邮件，其 Message-ID 为 `<ED4DEF51D75D746B+1387390957.0.1783265426131@VM-4-16-centos>`，`+` 之后是 **JavaMail 默认格式**（该邮件早于 `87eb186` 引入 UUID messageId 逻辑）。任何"`+` 之后必须是我方格式"的假设都会在历史数据上失效。
- 证据：两个独立样本，跨一个月、跨两个 QQ 中继集群（`smtpbgjp3.qq.com` / `smtpbgeu2.qq.com`），前缀分别为 `ED4DEF51D75D746B+`、`6136051B41AACA62+`，格式一致。
- 来源: original

### Invariant I-2: 只允许精确相等查询，禁止任何模糊匹配

- Rule: 所有查询一律走 `MailRecordRepository.findByMessageId(exactValue)` 的**精确相等**语义。**禁止**引入 `LIKE '%...'`、后缀匹配、前缀匹配、正则查询或任何新的 Repository 派生方法。前缀兼容通过"构造有限个候选值 + 逐个精确查"实现。
- Applies to: `UnmatchedInboundMailService.suggestCandidates()`、`BounceCollectionService.resolveOriginalContact()`。
- Violation consequence: `message_id` 是 `VARCHAR(255)` 且无索引约束保证唯一（`V1__create_business_tables.sql:102`）；后缀 `LIKE` 既走不了索引，又可能命中不同域名下 local-part 相同的记录，把回信关联到错误的联系人 —— 比当前的"静默失效"更糟。
- 说明：需求方原话是"按原 ID 后缀匹配"。本不变量以**有界前缀剥离 + 精确相等**实现同一意图，语义等价但无误匹配风险，属实现方式收紧，不改变需求。
- 来源: original

### Invariant I-3: 归一化只作用于读匹配侧，写入侧零改动

- Rule: `MessageIdNormalizer` 只被查询路径调用。**禁止**在 `SmtpMailDeliveryService`、任何 `ComposedMail` 构造点、`mail_record` 写入路径、`ImapMailReceiveService` 中调用它。`mail_record.message_id` 与 `mail_record.in_reply_to` 落库值**逐字保持原状**。
- Applies to: 全代码库（以 grep `MessageIdNormalizer` 的调用点全集验证）。
- Violation consequence: 若在写入侧归一化，库内值将与实际发出/收到的头不一致，破坏 `material-reminder-01-threading.md` 的 Invariant I-4（`in_reply_to` 必须与实际外发头同源同值），并让 K-outbound-thread-headers-single-seam 记录的审计失真问题从"匹配失败"升级为"数据失真"。
- 来源: K-outbound-thread-headers-single-seam（hit_count 0，2026-08-06 新建）

### Invariant I-4: 候选按固定顺序尝试，原值优先，首个命中即返回

- Rule: 候选值按此顺序生成并去重，逐个精确查询，**首个非 null 结果即返回**：① 原始值（trim 后）；② 规范化尖括号形态 `<core>`；③ ②再剥离供应商前缀。三者相同时只查一次。任一候选为空白则跳过。
- Applies to: `MessageIdNormalizer.candidatesFor()`；两个调用点。
- Violation consequence: 若归一化值优先于原值，当某天中继不再改写、而库内恰好存了带前缀的历史值时会错过精确命中。原值优先保证"行为只增不减"。
- 来源: original

### Invariant I-5: 匹配增强不得改变候选建议的语义与优先级

- Rule: `suggestCandidates()` 中 `IN_REPLY_TO` 的 `confidence = 90`、其在候选列表中的**首位**位置、以及后续 `NAME_OR_EMAIL_MATCH` 的去重条件全部不变。`resolveOriginalContact()` 的 `failedRecipient` 兜底分支保持在 `?:` 右侧不变。
- Applies to: `UnmatchedInboundMailService.suggestCandidates()`、`BounceCollectionService.resolveOriginalContact()`。
- Violation consequence: 置信度或顺序变化会改变运营看到的候选排序，属于本计划范围外的行为变更。
- 来源: original

---

## 现状审计

### `mail_record` 表（MySQL，Spring Data JDBC）

- Schema：`message_id VARCHAR(255)`、`in_reply_to VARCHAR(255)`（`V1__create_business_tables.sql:102-103`）。两列均**可为 null**，**无唯一约束、无索引声明**。
- 写路径（`message_id`）：唯一来源 `SmtpMailDeliveryService.send()` 返回的 `DeliveredMail.messageId`（`:60`，取 `message.messageID ?: mail.messageId`），经各发送服务落库。本计划**零改动**。
- 写路径（`in_reply_to`）：自动回复与人工回复路径填充（`AutoMailReplyService:273/586/776/974/1024/1073`、`PendingMailOperationService:240-249`）；入站记录由 `ImapMailReceiveService.kt:135` 读原始头。本计划**零改动**。
- 读路径（`findByMessageId`，grep 全集，`MailRecordRepository.kt:128` 定义）：
  1. `UnmatchedInboundMailService.suggestCandidates()`（`:78`）—— 用**入站** `record.inReplyTo` 查**出站** `message_id`。**本计划修改点 1。**
  2. `BounceCollectionService.resolveOriginalContact()`（`:137`）—— 用退信解析出的 `originalMessageId` 查原始外发记录。**本计划修改点 2。**
- 另有 `MailSendAttemptRepository.findByMessageId()`（`:14`）—— 不同表（`mail_send_attempt`），grep 确认 `src/main/kotlin` 内**无调用方**，不在本计划范围。

### 供应商 Message-ID 改写（生产实证）

| 样本 | 投递日期 | 中继 | 实际投递 Message-ID | 我方生成 |
|---|---|---|---|---|
| 提醒邮件 | 2026-08-06 | `smtpbgeu2.qq.com` (18.194.254.142) | `<6136051B41AACA62+reminder-2088-710aba50-77fa-4936-a8d3-72ecffaba836@talents.szwebotech.cn>` | `<reminder-2088-710aba50-...@talents.szwebotech.cn>` |
| 介绍邮件 | 2026-07-05 | `smtpbgjp3.qq.com` (54.92.39.34) | `<ED4DEF51D75D746B+1387390957.0.1783265426131@VM-4-16-centos>` | JavaMail 默认（早于 `87eb186`） |

- 前缀格式：16 位大写十六进制 + `+`，两样本一致，跨集群跨月份稳定。
- 该改写行为**无腾讯官方文档**，属观测所得，故 I-1 要求剥离规则严格锚定观测到的格式，不做外推。
- 对应的入站证据：专家回信头 `In-Reply-To: <6136051B41AACA62+reminder-2088-...@talents.szwebotech.cn>`，而 `mail_record.message_id` 存的是无前缀形态 —— `findByMessageId` 精确匹配必然落空。

### `mail_record.message_id` 实际取值样例（需求方线上库核对，2026-08-06）

- 联系人 `TEST-LUKAI-18014905480` 的 INTRODUCTION：`mail_record.id = 2200`，`message_id = <manual-outreach-TEST-LUKAI-18014905480-66392015-4c74-424c-9609-8896a382e20b@weibo.com>`。
- 佐证 I-1：我方共有 4 种 Message-ID 格式在流通（`intro-` / `reminder-` / `manual-outreach-` / `manual-rich-`），另有 5 个构造点走 JavaMail 默认格式（K-message-id-fingerprint 修正表），归一化规则**必须格式无关**。

### Interaction points

1. **`ImapMailReceiveService` 写入 `in_reply_to`（原始头，带前缀）× `suggestCandidates()` 读取匹配** —— 跨模块（收信 ↔ 人工队列）。本计划在读侧收口，写侧不动。
2. **`SmtpMailDeliveryService` 写入 `message_id`（无前缀）× 上述两个读路径** —— 跨模块（投递 ↔ 人工队列 / 退信）。不对称的根源，读侧兼容。
3. **`BounceDetector.kt:193` 解析退信中的 `In-Reply-To`/原信 Message-ID × `resolveOriginalContact()`** —— 退信里嵌的原信 Message-ID 来自**收件方 MTA 引用的投递后值**，同样带前缀，与 IP-1 同型。

### 需要更正的既有知识条目

`docs/knowledge/mail/K-message-id-fingerprint.md` 末段现写：

> 落库的 `mail_record.message_id` 取 `message.messageID`，两种情况下都与实际发出值一致。

本次审计**证伪**该结论：`message.messageID` 是交给中继**之前**的值，腾讯企业邮在投递时改写了它，因此库内值与**实际投递值**不一致。该条目须在 Phase 6 更正（见文末）。

---

## 实现方案

### 阶段 1：归一化工具（子系统 ① 邮件匹配）

**任务 1.1 — 新增 `MessageIdNormalizer`**（I-1, I-2, I-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MessageIdNormalizer.kt`（新增）

以 Kotlin `object` 实现（无状态、无依赖，便于单测直接调用）：

- `private val VENDOR_PREFIX = Regex("^[0-9A-F]{16}\\+")` —— 严格大写十六进制，长度恰好 16（I-1）。
- `fun canonicalize(raw: String?): String?` —— trim；为空白返回 null；若含 `<`，取**第一个** `<...>` 片段（`In-Reply-To` 理论上可含多个 msg-id）；否则用 `<` + 原值 + `>` 包裹；结果内部再 trim。
- `fun stripVendorPrefix(bracketed: String): String` —— 取尖括号内内容，按**第一个** `@` 切成 local-part 与 domain；对 local-part 应用 `VENDOR_PREFIX.replaceFirst("")`；无 `@` 时对整体应用；重新包裹尖括号。剥离后 local-part 为空则返回入参原值（避免产出 `<@domain>`）。
- `fun candidatesFor(raw: String?): List<String>` —— 按 I-4 顺序产出 ①原值(trim) ②`canonicalize` ③`stripVendorPrefix(canonicalize)`，过滤空白，`distinct()` 保序去重。

**禁止**在本类中引入任何对我方 Message-ID 格式（`intro-` / `reminder-` / …）的判断（I-1）。

**任务 1.2 — 单元测试**（I-1, I-4）

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/MessageIdNormalizerTest.kt`（新增）

必测用例：

| 输入 | 期望 `candidatesFor` |
|---|---|
| `<6136051B41AACA62+reminder-2088-710aba50-77fa-4936-a8d3-72ecffaba836@talents.szwebotech.cn>` | 含原值 + `<reminder-2088-710aba50-77fa-4936-a8d3-72ecffaba836@talents.szwebotech.cn>` |
| `<ED4DEF51D75D746B+1387390957.0.1783265426131@VM-4-16-centos>` | 含原值 + `<1387390957.0.1783265426131@VM-4-16-centos>`（I-1 关键回归：`+` 后为 JavaMail 默认格式） |
| `<manual-outreach-TEST-LUKAI-18014905480-66392015-4c74-424c-9609-8896a382e20b@weibo.com>` | 只有 1 个候选（无前缀，三者相同后去重） |
| `<6136051b41aaca62+reminder-1-x@d.cn>`（小写 hex） | **不剥离**，只有 1 个候选 |
| `<ABC123+reminder-1-x@d.cn>`（长度 6，非 16） | **不剥离** |
| `<0123456789ABCDEF0+reminder-1-x@d.cn>`（长度 17） | **不剥离**（正则锚定 16 位后必须紧跟 `+`，此处第 17 位是 `0` 不是 `+`） |
| `<local+part+more@d.cn>`（local-part 含 `+` 但前段非 16 位 hex） | **不剥离** |
| `reminder-1-x@d.cn`（无尖括号） | 候选含原值与 `<reminder-1-x@d.cn>` |
| `<0123456789ABCDEF+@d.cn>`（剥离后 local-part 为空） | **不剥离** |
| `null` / `""` / `"   "` | 空列表 |
| `<A@d.cn> <B@d.cn>`（多 msg-id） | 取第一个 `<A@d.cn>` |

### 阶段 2：接入两个读路径（子系统 ① 邮件匹配）

**任务 2.1 — `suggestCandidates()` 接入**（I-2, I-4, I-5）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt`

- `:76-91`：把 `mailRecordRepository.findByMessageId(inReplyTo)` 替换为遍历 `MessageIdNormalizer.candidatesFor(inReplyTo)`，对每个候选调用 `findByMessageId`，`firstNotNullOfOrNull { ... }` 取首个命中（I-4）。
- 其后的 `expertContactRepository.findById(...)`、`CandidateSuggestion(reason = "IN_REPLY_TO", confidence = 90)` 构造**逐字不变**（I-5）。
- `:93` 起的 `NAME_OR_EMAIL_MATCH` 与邮箱匹配段落**零改动**。

**任务 2.2 — `resolveOriginalContact()` 接入**（I-2, I-4, I-5）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt`

- `:135-140`：`signal.originalMessageId?.let { origMsgId -> ... }` 内部同样改为遍历候选取首个命中。
- `?: signal.failedRecipient?.let { ... }` 兜底分支**逐字不变**（I-5）。
- 注意 `originalMessageId` 可能是 `NOID:<sha1>` 形态（`:132` 的合成 ID）—— 该形态不含尖括号也不匹配前缀正则，`candidatesFor` 会产出 `["NOID:xxx", "<NOID:xxx>"]`，第二个候选查不到即落空，行为与改动前等价。**测试须覆盖此形态确认无回归。**

**任务 2.3 — 调用点测试**（I-2, I-4, I-5）

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt`（修改）、`src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt`（修改）

- `suggestCandidates`：入站 `inReplyTo` 带前缀、库内 `message_id` 无前缀 → 断言产出 `IN_REPLY_TO` 且 `confidence == 90` 且位于候选首位。
- `suggestCandidates`：两侧都无前缀 → 断言行为与改动前一致（回归）。
- `suggestCandidates`：`inReplyTo` 为 null → 断言不查库、无 `IN_REPLY_TO` 候选（回归）。
- `suggestCandidates`：带前缀但库内也无对应记录 → 断言无 `IN_REPLY_TO` 候选，仍产出 `NAME_OR_EMAIL_MATCH`（回归）。
- `resolveOriginalContact`：`originalMessageId` 带前缀 → 断言命中原始记录的联系人。
- `resolveOriginalContact`：`originalMessageId` 为 `NOID:` 形态 → 断言落到 `failedRecipient` 兜底分支（回归）。

---

## 变更文件清单

| # | 文件 | 类型 | 子系统 | 不变量 |
|---|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MessageIdNormalizer.kt` | 新增 | ① 邮件匹配 | I-1, I-2, I-4 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt` | 修改 | ① | I-2, I-3, I-4, I-5 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt` | 修改 | ① | I-2, I-3, I-4, I-5 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MessageIdNormalizerTest.kt` | 新增 | ① | I-1, I-4 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt` | 修改 | ① | I-4, I-5 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt` | 修改 | ① | I-4, I-5 |

**文件数：6 ≤ 10 ✓** ｜ **子系统数：1 ✓** ｜ **共享存储新增字段：0 ✓** ｜ **数据库迁移：无 ✓** ｜ **前端改动：无 ✓**

---

## 验证命令（可直接复制执行）

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。以下为**唯一权威的可执行形式**，fix-v / verify-p 直接照抄，不得自行推断或简化。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的测试类（单独运行）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MessageIdNormalizerTest

# 本计划修改的测试类（单独运行）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnmatchedInboundMailServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BounceCollectionServiceTest

# 三个类一次跑完
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='MessageIdNormalizerTest,UnmatchedInboundMailServiceTest,BounceCollectionServiceTest'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`；`git diff --check` 无输出。

来源：`CLAUDE.md` 项目元信息 `test_command` / `build_command`。

---

## 验收标准

- **I-1**：`MessageIdNormalizerTest` 的 11 个用例全绿，重点是小写 hex / 长度非 16 / local-part 含 `+` 三个**不剥离**用例，以及 `+` 后为 JavaMail 默认格式的**剥离**用例。grep `MessageIdNormalizer.kt` 确认全文**不含** `intro-` / `reminder-` / `manual-outreach-` / `manual-rich-` 任一字面量。
- **I-2**：grep 本次 diff，确认**未新增**任何 Repository 方法，`MailRecordRepository.kt` 不在变更文件清单内；确认两个调用点均通过 `findByMessageId(候选)` 精确查询，全文无 `LIKE` / `Containing` / `EndingWith` / `StartingWith` 派生查询关键字。
- **I-3**：grep `MessageIdNormalizer` 全仓调用点，确认**仅出现在** `UnmatchedInboundMailService.kt`、`BounceCollectionService.kt` 及两个测试文件中；确认 `SmtpMailDeliveryService.kt`、`ImapMailReceiveService.kt`、任一 `ComposedMail` 构造点均**未**出现该符号。
- **I-4**：`MessageIdNormalizerTest` 断言 `candidatesFor` 返回列表的**顺序**为 原值 → 尖括号规范化 → 剥离前缀，且已去重；调用点测试断言"原值能命中时不会因归一化而改变结果"。
- **I-5**：`UnmatchedInboundMailServiceTest` 断言 `IN_REPLY_TO` 候选的 `confidence == 90` 且为 `candidates[0]`；`BounceCollectionServiceTest` 断言 `NOID:` 形态仍落到 `failedRecipient` 兜底。
- **回归**：执行「验证命令」节的全量测试命令通过。

跨 interaction point 集成断言：

- IP-1 / IP-2：`UnmatchedInboundMailServiceTest` 中构造"入站 `in_reply_to` 带前缀 + 库内 `message_id` 无前缀"的组合，断言能关联到正确联系人。
- IP-3：`BounceCollectionServiceTest` 中构造"退信 `originalMessageId` 带前缀"的组合，断言能关联到原始外发记录。

---

## 人工验收清单

> **执行约定（2026-08-06 需求方确认）**：本节为**建议性清单，非强制门禁**。验收人按实际环境条件挑选执行即可，
> 不要求逐条留痕、不要求导出 `<plan-name>-acceptance.md` 勾选文件。
> A-1 / A-4 依赖真实收发信往返，若不便构造，可用 A-2 / A-3 的 SQL 直插方式替代覆盖同一条匹配路径。
> 机器可验证的部分仍以 `## 验收标准` 为准，那一节是强制的。

### A-1: 带前缀的回信能给出 IN_REPLY_TO 候选

- 前置条件：向一个测试专家发送一封提醒邮件（经腾讯企业邮通道），确认 `mail_record` 中该封的 `message_id` 形如 `<reminder-{contactId}-{uuid}@talents.szwebotech.cn>`（**无**十六进制前缀）。随后用该专家邮箱在 Gmail 中**点「回复」**（不要新建邮件）回一封内容任意的信，等待收信轮询入库。
- 操作步骤：① 打开「收发件箱」→ 勾选「仅待处理」；② 找到该来信，点「查看/处理」；③ 查看候选专家列表。
- 预期结果：候选列表**第一项**为该专家，来源标记 `IN_REPLY_TO`，置信度 `90`。（改动前此处只会出现置信度 `60` 的 `NAME_OR_EMAIL_MATCH`。）
- 覆盖：需求 1、I-4、I-5、interaction point 1

### A-2: 无前缀通道的行为未变（回归）

- 前置条件：构造一条入站记录，其 `in_reply_to` 与某条外发 `mail_record.message_id` **完全相同且都无前缀**（可直接用 SQL 插入 `inbound_mail_processing` 与 `mail_record` 各一行，`in_reply_to` = `message_id` = `<reminder-9999-test@example.com>`）。
- 操作步骤：① 在「收发件箱 → 待处理」打开该来信；② 查看候选列表。
- 预期结果：候选首项为对应联系人，来源 `IN_REPLY_TO`，置信度 `90` —— 与改动前完全一致。
- 覆盖：需求 3、I-4、must-NOT-change 第 3 项

### A-3: 无法匹配时仍有兜底候选（回归）

- 前置条件：构造一条入站记录，`in_reply_to` = `<0123456789ABCDEF+nonexistent-id@example.com>`（前缀合法但库内无任何对应记录），发件邮箱为某个已存在联系人的邮箱。
- 操作步骤：① 打开该来信详情；② 查看候选列表。
- 预期结果：**没有** `IN_REPLY_TO` 候选；但出现该联系人的 `NAME_OR_EMAIL_MATCH` 候选，置信度 `60`；页面正常渲染无报错。
- 覆盖：I-5、must-NOT-change 第 3 项

### A-4: 退信能关联回原始外发邮件

- 前置条件：向一个**确定不存在**的邮箱（如 `no-such-user-xyz@talents.szwebotech.cn`）发送一封邮件，等待退信入库。确认该联系人在库中存在。
- 操作步骤：① 打开「监控 → 退信」子标签；② 找到该退信记录；③ 查看其关联的专家/联系人字段。
- 预期结果：退信记录关联到**正确的联系人**，且该关联来自原信 Message-ID 匹配（而非仅靠收件邮箱兜底）。
- 覆盖：需求 2、interaction point 3

### A-5: 库内数据未被归一化改写（回归）

- 前置条件：完成 A-1 后。
- 操作步骤：① 执行 `SELECT message_id, in_reply_to FROM mail_record WHERE expert_contact_id = <该联系人 id> ORDER BY id DESC LIMIT 5;`
- 预期结果：外发行的 `message_id` 仍为**无前缀**的我方生成值；入站行的 `in_reply_to` 仍为**带前缀**的原始头值。两者**都没有**被改写成归一化形态。
- 覆盖：I-3、must-NOT-change 第 1、5 项

---

## Phase 6 知识写回（执行本计划后处理）

1. **更正 `docs/knowledge/mail/K-message-id-fingerprint.md`** —— 末段"落库的 `mail_record.message_id` 取 `message.messageID`，两种情况下都与实际发出值一致"被本次审计证伪。应改为：库内值等于**交给中继前**的值；腾讯企业邮会在投递时给 Message-ID 加 `[0-9A-F]{16}+` 前缀，故库内值与**实际投递值**不一致。并 bump `created`（re-validated）。
2. **新建 `docs/knowledge/mail/K-vendor-message-id-prefix.md`** —— 记录：腾讯企业邮中继改写 Message-ID 的观测事实、两个样本、影响的两个读路径、以及"归一化只在读侧、只做有界前缀剥离 + 精确相等"的规则。任何未来涉及 Message-ID 匹配的计划都应继承。
