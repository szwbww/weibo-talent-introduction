# 出站 Message-ID 收口（第一批）：补齐 4 个缺失构造点

> 用 create-p skill 编写。独立计划，无前置依赖，可独立部署与验证。
> **第二批**见 `outbound-message-id-02-domain-alignment.md`（依赖本批的 `OutboundMessageIdFactory`，反之不成立）。
> **拆分理由**：全量收口（补缺 4 处 + 域名对齐 2 处）需改 6 个主文件 + 6 个测试文件 = 12 个，超出 create-p 的 10 文件上限。
> 本批取「消除 JavaMail 默认格式指纹」这一最小可交付切片 —— 它是 `K-message-id-fingerprint` 的核心诉求，
> 域名不对齐只是弱信号，价值排序明确。
>
> **建议执行时机**：`material-reminder-01-threading.md` 已于 `3bff469` 落地；本批与其文件零重叠，可立即执行。
> 若 `material-reminder-02-headers-personalization.md` 尚未落地，本批亦不冲突（不碰 `SmtpMailDeliveryService`）。

## 需求描述

**可观察结果**

1. 会议邀请（`MEETING_INVITATION`，两条产出路径）、会议确认（`MEETING_CONFIRMATION`）、QA/Grounded 自动回复三类外发邮件的 `Message-ID` 形如 `<{kind}-{discriminator}-{uuid}@{发件账号域名}>`，不再是 JavaMail 默认的 `<{随机数}.{计数}.{时间戳}@{服务器主机名}>`。
2. 上述邮件落库的 `mail_record.message_id` 与实际交给中继的值一致（形态同第 1 条）。
3. 收件人在邮件客户端查看原始邮件时，`Message-ID` 的域名与 `From` 的域名一致。

**必须不变（must NOT change）**

- `IntroductionMailComposer.kt:28` 产出的 `<intro-{orcid}-{uuid}@{domain}>` **逐字不变**。
- `ManualExpertMailService.kt:192` 产出的 `<reminder-{contactId}-{uuid}@{senderDomain}>` **逐字不变** —— 该值由 `material-reminder-01-threading.md` 的 Invariant I-3 确立并已落地（`3bff469`），本批不得改动其格式或生成位置。
- `ManualInitialOutreachService.kt:587` 的 `<manual-outreach-{orcid}-{uuid}@weibo.com>` 与 `ManualReplySendAttemptService.kt:35` 的 `<manual-rich-{uuid}@weibo.com>` **本批完全不动**（属第二批）。
- `SmtpMailDeliveryService.kt` **零改动** —— 其 `mail.messageId != null` 时覆写 `updateMessageID()` 的既有机制（`:20-28`）正是本批依赖的载体，不需要改。
- `ComposedMail` data class（`IntroductionMailComposer.kt:59-68`）**零改动** —— `messageId` 已是带默认值的既有字段。
- `material-reminder-01` 确立的线程头行为（`In-Reply-To` / `References` / `Re:` 主题归一化 / `in_reply_to` 落库同源）全部不变。
- `ManualReplySendAttemptService.computeFingerprint()` 的 `appendLengthPrefix` 字段序列**一个字节都不许动**（见 I-4）。
- 各邮件类型的 `subject` / `body` / `html` / `text` 渲染逻辑与模板选择零改动。
- 各路径的状态流转、`mail_record` 其余字段、审计写入零改动。

**不在范围（out of scope）**

- 域名硬编码 `@weibo.com` 的两处 —— 第二批。
- 把 `IntroductionMailComposer` / `ManualExpertMailService` 两个**已正确**的构造点重构为调用新 helper —— 见 I-5，仅在产出字符串逐字节相同时才允许，且本批不强制；收益为零、回归面非零。
- `Message-ID` 落库后与实际投递值的差异（腾讯企业邮加 `[0-9A-F]{16}+` 前缀）—— 由 `inbound-message-id-vendor-prefix.md` 在读匹配侧处理。
- `List-Unsubscribe` / DKIM 相关任何改动。
- 新增 Flyway migration。
- 本计划不触及任何前端文件，故无 `## 样式契约` 一节。

---

## 关键不变量

### Invariant I-1: 每封外发邮件必须携带项目自生成的 UUID Message-ID

- Rule: `ComposedMail` 的全部 8 个构造点中，本批负责的 4 处（`MeetingInvitationMailComposer.kt:22`、`AutoMailReplyService.kt:567`、`AutoMailReplyService.kt:958`、`MeetingScheduleService.kt:125`）必须显式设置 `messageId`，值由 `OutboundMessageIdFactory` 产出。**禁止**留空依赖 JavaMail 默认生成。
- Applies to: 上述 4 个构造点。
- Violation consequence: JavaMail 默认格式 `<{随机数}.{计数}.{时间戳}@{hostname}>` 会把服务器内网主机名（生产实证：`VM-4-16-centos`）暴露到公网邮件头，且是可被 ESP 指纹识别为批量发送工具的特征。
- 来源: K-message-id-fingerprint（hit_count 8）

### Invariant I-2: Message-ID 的域名必须取自本次发送所用的发件账号

- Rule: 域名一律为 `account.senderEmail.substringAfter("@")`，其中 `account` 是**本次投递实际使用的** `MailSenderAccount`。**禁止**硬编码任何域名字面量，**禁止**使用服务器 hostname、配置项或常量。若 `senderEmail` 不含 `@` 或域名部分为空白，则 `OutboundMessageIdFactory` 抛 `IllegalArgumentException`（fail-fast，不产出畸形 msg-id）。
- Applies to: `OutboundMessageIdFactory`；本批 4 个构造点。
- Violation consequence: 发件账号池是按 `country` 分布的多账号设计（`SenderAccountAssignmentService`），硬编码域名会让 Message-ID 无法指示实际发件域名；域名与 `From` 不一致是部分反垃圾规则的弱负面信号，而本项目对投递率高度敏感（`docs/plans/2026-07-03/google-spam-mitigation.md` 记录投诉率 6.9% 事故）。
- 来源: original

### Invariant I-3: Message-ID 全局唯一且不可预测，唯一性只依赖 UUID

- Rule: 格式为 `<{kind}-{discriminator}-{uuid}@{domain}>`，其中 `uuid` 为 `UUID.randomUUID().toString()`（保留连字符）。唯一性**只**由 `uuid` 保证；`kind` 与 `discriminator` 仅供人工排查，**不得**被任何代码解析、匹配或作为查询条件。
- Applies to: `OutboundMessageIdFactory`；全代码库（以「无人 grep `intro-` / `reminder-` 等前缀做逻辑判断」验证）。
- Violation consequence: 若唯一性依赖 `discriminator`（如 contactId），同一联系人的多封同类邮件会产生相同 Message-ID，破坏 `MailRecordRepository.findByMessageId()` 的单值语义与退信关联。同时 `inbound-message-id-vendor-prefix.md` 的 I-1 已明确禁止对 Message-ID 内容做格式假设，本条与之呼应。
- 来源: K-message-id-fingerprint；呼应 `inbound-message-id-vendor-prefix.md` I-1

### Invariant I-4: 不得触碰人工回复的发送指纹输入

- Rule: 本批**不修改** `ManualReplySendAttemptService.kt`。即便第二批修改该文件，`computeFingerprint()` 中 `appendLengthPrefix` 的字段序列（`SCHEMA_VERSION` / `inboundProcessingId` / `contactId` / `orcidId` / `accountCode` / `normalizedRecipient` / `subject` / `finalText` / `finalHtml` / `inReplyTo` / `canonicalQaRuleIds`）**逐字段不得增删改序**。`messageId` 不是指纹输入，改它不影响幂等。
- Applies to: `ManualReplySendAttemptService.computeFingerprint()`（本批为"禁止改动"约束，非改动项）。
- Violation consequence: 指纹字段变化会让历史 `mail_send_attempt.mail_type`（`MANUAL_RICH:` + sha256 前 32 位）全部失效，跨部署边界的重试会绕过 `insertIgnore` 去重导致**重复发信**。
- 来源: K-manual-send-fingerprint-complete-identity（severity P1, hit_count 4）

### Invariant I-5: 已正确的两个构造点若接入 helper，产出必须逐字节相同

- Rule: `IntroductionMailComposer.kt:28`（`<intro-{orcid}-{uuid}@{domain}>`）与 `ManualExpertMailService.kt:192`（`<reminder-{contactId}-{uuid}@{senderDomain}>`）**本批默认不改**。若执行者认为接入 helper 更整洁，仅当 `OutboundMessageIdFactory` 对相同入参产出与现状**逐字节相同**的字符串时才允许，且必须补充断言该等价性的测试；否则维持原样。
- Applies to: 上述两个构造点。
- Violation consequence: `ManualExpertMailService` 的格式受 `material-reminder-01-threading.md` I-3 约束且刚落地（`3bff469`），擅自变更会使该计划的验收失效；`IntroductionMailComposer` 的变更会同时影响两条介绍邮件路径（K-dual-outreach-paths）。
- 来源: K-dual-outreach-paths（hit_count 7）；material-reminder-01 I-3

---

## 现状审计

### `ComposedMail` 全部 8 个构造点（grep `ComposedMail(` 排除 data class，2026-08-06 实测）

| # | 构造点 | 邮件类型 | `messageId` 现状 | 本批 |
|---|---|---|---|---|
| 1 | `IntroductionMailComposer.kt:28` | INTRODUCTION | ✅ `<intro-{orcid}-{uuid}@{account域名}>` | 不动（I-5） |
| 2 | `ManualInitialOutreachService.kt:587`（`.copy()` 覆盖 #1） | INTRODUCTION | ⚠️ `<manual-outreach-{orcid}-{uuid}@weibo.com>` 域名硬编码 | **第二批** |
| 3 | `ManualExpertMailService.kt:192` | COMPOSE_TEMPLATE / MATERIAL_REMINDER | ✅ `<reminder-{contactId}-{uuid}@{senderDomain}>` | 不动（I-5） |
| 4 | `PendingMailOperationService.kt:258` | 人工富文本回复 | ⚠️ `messageId = claim.messageId`，值来自 `ManualReplySendAttemptService.kt:35` 的 `<manual-rich-{uuid}@weibo.com>` | **第二批** |
| 5 | `MeetingInvitationMailComposer.kt:22` | MEETING_INVITATION | ❌ 未设置 | **本批** |
| 6 | `AutoMailReplyService.kt:567` | QA / Grounded 自动回复 | ❌ 未设置 | **本批** |
| 7 | `AutoMailReplyService.kt:958` | MEETING_INVITATION（自动分支） | ❌ 未设置 | **本批** |
| 8 | `MeetingScheduleService.kt:125` | MEETING_CONFIRMATION | ❌ 未设置 | **本批** |

> **本表更正了 `K-message-id-fingerprint` 的 2026-08-06 修正表**：该表把 `PendingMailOperationService.kt:258` 列为「❌ 无 messageId」，实测该处已通过 `claim.messageId` 设置（`:264`），问题是**域名硬编码**而非缺失。同时该表未反映 `ManualExpertMailService` 已由 `3bff469` 修复。缺失数由「5 处」更正为「4 处」。须在 Phase 6 回写。

### `mail_record.message_id` 写入链路

- 唯一 MIME 写入点：`SmtpMailDeliveryService.send()`（`:20-28`）—— `mail.messageId != null` 时用匿名 `MimeMessage` 子类覆写 `updateMessageID()`；为 null 时走 `sender.createMimeMessage()` 由 JavaMail 生成。
- 落库值：`DeliveredMail.messageId = message.messageID ?: mail.messageId`（`:60`），两种情况都等于**交给中继前**的值。
- ⚠️ 该值与**实际投递值**不一致：腾讯企业邮中继会加 `[0-9A-F]{16}+` 前缀（实证见 `inbound-message-id-vendor-prefix.md` 现状审计）。本批不改变这一事实，读匹配侧的兼容由该姊妹计划负责。
- 全部 7 个投递调用点共用 `SmtpMailDeliveryService.send()`（来源: K-outbound-thread-headers-single-seam）：`PendingMailOperationService:270`、`AutoMailReplyService:574/:963`、`ManualExpertMailService:57`、`ManualInitialOutreachService:626`、`MeetingScheduleService:130`、`InitialOutreachService:66`。

### 发件账号域名来源

- `MailSenderAccount.senderEmail`（`mail_sender_account.sender_email VARCHAR(255) NOT NULL`，`V1__create_business_tables.sql:4`）。
- 现有正确用法先例：`IntroductionMailComposer.kt:25` `val domain = account.senderEmail.substringAfter("@")`；`ManualExpertMailService.kt:198` 同法。
- 本批 4 个构造点的 `account` 可得性（实测确认，无需新增查询或依赖注入）：

| 构造点 | `account` 来源 |
|---|---|
| `MeetingInvitationMailComposer.kt:22` | 方法入参已有 `account`（`:16` 已读 `account.senderDisplayName`） |
| `AutoMailReplyService.kt:567` | 同方法内 `account`（`:562` 已传入 `mailVariableService.renderForContact`） |
| `AutoMailReplyService.kt:958` | 同方法内 `account`（`:955` 已用于 `mailTemplateVariables(account)`） |
| `MeetingScheduleService.kt:125` | 同方法内 `account`（`:120` 已读 `account.teamName`） |

### Interaction points

1. **4 个构造点写 `messageId` × `SmtpMailDeliveryService.send()` 的 `updateMessageID()` 覆写分支** —— 这 4 类邮件从「走 JavaMail 默认分支」切换到「走覆写分支」，是本批唯一的行为切换面。跨模块（组装层 ↔ 投递层）。
2. **4 个构造点写 `messageId` × `mail_record.message_id` 落库** —— 落库值形态改变，需确认 `VARCHAR(255)` 不溢出（见验收标准 I-3）。
3. **新 Message-ID 格式 × `MailRecordRepository.findByMessageId()` 的两个读路径** —— `UnmatchedInboundMailService:78`、`BounceCollectionService:137`。两者均为精确相等查询、格式无关，**不需要改**；但 `inbound-message-id-vendor-prefix.md` 若同期落地，其 `MessageIdNormalizer` 也必须对新格式格式无关（该计划 I-1 已保证）。
4. **`MEETING_INVITATION` 有两个产出路径**（`MeetingInvitationMailComposer.kt:22` 与 `AutoMailReplyService.kt:958`）—— 两处必须产出**同一 `kind`**，否则同类邮件出现两种 Message-ID 前缀，重蹈 `intro-` / `manual-outreach-` 分裂的覆辙。

---

## 实现方案

### 阶段 1：统一工厂（子系统 ① mail/service 组装层）

**任务 1.1 — 新增 `OutboundMessageIdFactory`**（I-2, I-3）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundMessageIdFactory.kt`（新增）

以 Kotlin `object` 实现（无状态、无依赖，便于单测直接调用）：

- `fun newId(kind: String, discriminator: String, senderEmail: String): String`
  - 校验 `kind` 非空白且仅含 `[a-z-]`；`discriminator` 非空白（调用方保证已归一化）；否则抛 `IllegalArgumentException`。
  - `val domain = senderEmail.substringAfter("@", "")`；`require(domain.isNotBlank())` —— 不含 `@` 或域名为空时 fail-fast（I-2）。
  - 返回 `"<$kind-$discriminator-${UUID.randomUUID()}@$domain>"`。
- **禁止**在本类中出现任何域名字面量、hostname 读取或配置注入（I-2）。
- **禁止**提供任何"解析 Message-ID"的反向方法（I-3）。

**任务 1.2 — 工厂单测**（I-2, I-3）

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundMessageIdFactoryTest.kt`（新增）

必测用例：

| 场景 | 期望 |
|---|---|
| `newId("meeting-invitation", "42", "lilei@talents.szwebotech.cn")` | 匹配 `^<meeting-invitation-42-[0-9a-f-]{36}@talents\.szwebotech\.cn>$` |
| 连续调用 1000 次 | 全部互不相同（I-3 唯一性） |
| `senderEmail` 不含 `@` | 抛 `IllegalArgumentException` |
| `senderEmail` 为 `"user@"` | 抛 `IllegalArgumentException` |
| `kind` 为空白 | 抛 `IllegalArgumentException` |
| `discriminator` 为空白 | 抛 `IllegalArgumentException` |
| 长度 | 典型入参产出 ≤ 255 字符（配合 I-3 的落库约束） |

### 阶段 2：接入 3 个 mail/service 构造点（子系统 ①）

**任务 2.1 — `MeetingInvitationMailComposer`**（I-1, I-2, IP-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingInvitationMailComposer.kt`

`:22` 的 `ComposedMail(...)` 增加 `messageId = OutboundMessageIdFactory.newId("meeting-invitation", expert.orcidId, account.senderEmail)`。`to` / `subject` / `body` 逐字不变。

**任务 2.2 — `AutoMailReplyService` 自动回复分支**（I-1, I-2）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`

`:567` 的 `ComposedMail(...)` 增加 `messageId = OutboundMessageIdFactory.newId("auto-reply", contactId.toString(), account.senderEmail)`。`to` / `subject` / `body` / `html` / `text` 逐字不变。

**任务 2.3 — `AutoMailReplyService` 会议邀请分支**（I-1, I-2, IP-4）

同文件 `:958` 的 `ComposedMail(...)` 增加 `messageId = OutboundMessageIdFactory.newId("meeting-invitation", contact.orcidId, account.senderEmail)`。

⚠️ `kind` 必须与任务 2.1 **完全相同**（`"meeting-invitation"`），`discriminator` 均用 ORCID —— 两条路径产出同一种 `MEETING_INVITATION` 邮件（IP-4）。

### 阶段 3：接入 campaign/service 构造点（子系统 ② campaign/service）

**任务 3.1 — `MeetingScheduleService`**（I-1, I-2）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt`

`:125` 的 `ComposedMail(...)` 增加 `messageId = OutboundMessageIdFactory.newId("meeting-confirmation", contact.orcidId, account.senderEmail)`。`to` / `subject` / `body` 逐字不变。

### 阶段 4：调用点测试（子系统 ①②）

**任务 4.1** — `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingInvitationMailComposerTest.kt`（修改）：断言产出的 `ComposedMail.messageId` 匹配 `^<meeting-invitation-.+-[0-9a-f-]{36}@{账号域名}>$`，且**不为 null**。

**任务 4.2** — `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`（修改）：对两个分支各断言一次 `messageId` 形态；断言两次调用产出的 `messageId` 不同（I-3）；断言会议邀请分支的 `kind` 与 `MeetingInvitationMailComposer` 一致（IP-4）。

**任务 4.3** — `src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleServiceTest.kt`（修改）：断言 `messageId` 形态与域名来自 stub 账号的 `senderEmail`（而非任何硬编码域名）。

---

## 变更文件清单

| # | 文件 | 类型 | 子系统 | 不变量 |
|---|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundMessageIdFactory.kt` | 新增 | ① mail 组装层 | I-2, I-3 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingInvitationMailComposer.kt` | 修改 | ① | I-1, I-2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 修改 | ① | I-1, I-2 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt` | 修改 | ② campaign | I-1, I-2 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundMessageIdFactoryTest.kt` | 新增 | ① | I-2, I-3 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingInvitationMailComposerTest.kt` | 修改 | ① | I-1, I-2 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 修改 | ① | I-1, I-2, I-3 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleServiceTest.kt` | 修改 | ② | I-1, I-2 |

**文件数：8 ≤ 10 ✓** ｜ **子系统数：2 ✓** ｜ **共享存储新增字段：0 ✓** ｜ **数据库迁移：无 ✓** ｜ **前端改动：无 ✓**

---

## 验证命令（可直接复制执行）

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。以下为**唯一权威的可执行形式**，fix-v / verify-p 直接照抄，不得自行推断或简化。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的测试类（单独运行）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OutboundMessageIdFactoryTest

# 本计划修改的测试类（单独运行）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MeetingInvitationMailComposerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AutoMailReplyServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MeetingScheduleServiceTest

# 四个类一次跑完
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='OutboundMessageIdFactoryTest,MeetingInvitationMailComposerTest,AutoMailReplyServiceTest,MeetingScheduleServiceTest'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`；`git diff --check` 无输出。

来源：`CLAUDE.md` 项目元信息 `test_command` / `build_command`。

---

## 验收标准

- **I-1**：grep `ComposedMail(` 全部 8 个构造点，确认 #5 / #6 / #7 / #8 四处均显式出现 `messageId =`；`OutboundMessageIdFactoryTest` 与三个调用点测试全绿。
- **I-2**：grep `OutboundMessageIdFactory.kt` 全文，确认**不含** `weibo.com`、`talents.szwebotech.cn`、`hostname`、`InetAddress` 任一字面量或调用；grep 本批 4 个改动点，确认域名参数一律为 `account.senderEmail`；`MeetingScheduleServiceTest` 断言域名随 stub 账号变化。
- **I-3**：`OutboundMessageIdFactoryTest` 的 1000 次唯一性用例通过；`AutoMailReplyServiceTest` 断言同一联系人连续两次自动回复的 `messageId` 不同；grep 全代码库确认无对 `"meeting-invitation"` / `"auto-reply"` / `"meeting-confirmation"` / `"intro-"` / `"reminder-"` 前缀做**解析或匹配**的逻辑（仅允许作为生成入参出现）。落库长度：断言典型入参产出 ≤ 255（`mail_record.message_id VARCHAR(255)`）。
- **I-4**：diff 确认 `ManualReplySendAttemptService.kt` **不在**本次变更中（文件清单已排除）。
- **I-5**：diff 确认 `IntroductionMailComposer.kt` 与 `ManualExpertMailService.kt` **零改动**；若执行者选择接入 helper，则必须存在断言"产出与旧实现逐字节相同"的测试，否则视为违反。
- **回归**：执行「验证命令」节的全量测试命令通过。

跨 interaction point 集成断言：

- IP-1：断言这 4 类邮件经 `SmtpMailDeliveryService.send()` 后，`DeliveredMail.messageId` 等于组装层设置的值（走覆写分支），而非 JavaMail 默认格式。
- IP-4：断言 `MeetingInvitationMailComposer` 与 `AutoMailReplyService:958` 两处产出的 `kind` 段完全相同（同一字符串常量或同一测试正则）。

---

## 人工验收清单

> **执行约定**：本节为**建议性清单，非强制门禁**。验收人按实际环境条件挑选执行即可，不要求逐条留痕、不要求导出勾选文件。
> 机器可验证的部分以 `## 验收标准` 为准，那一节是强制的。

### A-1: 会议邀请邮件的 Message-ID 形态

- 前置条件：一个处于 `WAITING_REPLY` 且已表达兴趣的测试联系人，发件账号 `senderEmail` 为 `lilei@talents.szwebotech.cn`。
- 操作步骤：① 触发一次会议邀请发送；② 在收件箱打开该邮件 →「显示原始邮件」；③ 同时执行 `SELECT message_id FROM mail_record WHERE mail_type='MEETING_INVITATION' ORDER BY id DESC LIMIT 1;`
- 预期结果：原始邮件的 `Message-ID` 形如 `<meeting-invitation-{ORCID}-{36位UUID}@talents.szwebotech.cn>`（收件方可能带 `[0-9A-F]{16}+` 前缀，属中继改写，正常）；库内值为**无前缀**的同一形态；`Message-ID` 的域名与 `From` 的域名一致；**不含** `JavaMail` 字样或服务器主机名 `VM-4-16-centos`。
- 覆盖：需求 1、2、3，I-1，I-2，interaction point 1、2

### A-2: 会议确认与自动回复同样收口

- 前置条件：同 A-1。
- 操作步骤：① 触发一次会议确认发送；② 触发一次 QA 自动回复；③ 分别查看两封邮件原文。
- 预期结果：分别形如 `<meeting-confirmation-{ORCID}-{UUID}@talents.szwebotech.cn>` 与 `<auto-reply-{contactId}-{UUID}@talents.szwebotech.cn>`；均不含 `JavaMail` 字样。
- 覆盖：需求 1，I-1，I-2

### A-3: 介绍邮件与材料提醒未受影响（回归）

- 前置条件：同 A-1。
- 操作步骤：① 通过批量引擎发一封介绍邮件；② 发一封材料提醒；③ 查看两封原文的 `Message-ID`。
- 预期结果：介绍邮件仍为 `<manual-outreach-{ORCID}-{UUID}@weibo.com>`（**本批不改，第二批才动**）；材料提醒仍为 `<reminder-{contactId}-{UUID}@talents.szwebotech.cn>`，且其 `In-Reply-To` / `References` / `Re:` 主题行为与 `material-reminder-01` 落地后完全一致。
- 覆盖：must-NOT-change 第 1、2、3 项，I-5

### A-4: 人工富文本回复的去重未被破坏（回归）

- 前置条件：一封待处理来信，准备一段人工富文本回复内容。
- 操作步骤：① 发送该回复；② 不修改任何内容，**再次点击发送**。
- 预期结果：第二次被去重拦截（提示已发送 / `DEDUP_SENT`），**不产生第二封实际外发邮件**；`mail_send_attempt` 中该 `mail_type`（`MANUAL_RICH:` 开头）只有一行。
- 覆盖：I-4，must-NOT-change 第 7 项

### A-5: 多域名发件账号（条件性，若环境具备）

- 前置条件：存在两个 `senderEmail` 域名不同的启用发件账号。
- 操作步骤：① 分别用两个账号各触发一次会议邀请。
- 预期结果：两封邮件的 `Message-ID` 域名**分别等于各自账号的域名**，而非同一个固定值。
- 覆盖：I-2

---

## 后续计划（第二批，本批落地后再执行）

`outbound-message-id-02-domain-alignment.md` —— 消除剩余 2 处域名硬编码：

1. `ManualInitialOutreachService.kt:587` —— 该行 `.copy(messageId = ...)` 覆盖了 `IntroductionMailComposer.compose()` 已生成的正确值。需与需求方确认：是保留 `manual-outreach-` 前缀（仅换域名，维持两条介绍邮件路径的可区分性），还是直接删除该行让 composer 的 `intro-` 值透传（两条路径统一）。**这是产品/运维取舍，不是技术取舍。**
2. `ManualReplySendAttemptService.kt:35` 的 `MESSAGE_ID_TEMPLATE` —— 该类只持有 `payload.accountCode` 字符串，取域名需额外查发件账号，故不是纯常量替换。改动时**必须**遵守本批 I-4：`messageId` 不进指纹输入，`computeFingerprint()` 的字段序列一个字节都不许动。

---

## Phase 6 知识写回（执行本计划后处理）

1. **更正 `docs/knowledge/mail/K-message-id-fingerprint.md` 的 2026-08-06 修正表** —— 该表两处失准：① `PendingMailOperationService.kt:258` 实际**已设置** `messageId`（`:264` 取 `claim.messageId`），问题是域名硬编码而非缺失；② `ManualExpertMailService.kt` 已由 `3bff469` 修复。缺失数应由「5 处」更正为「4 处」，并 bump `created`（re-validated）。该表另有一处结论已被 `inbound-message-id-vendor-prefix.md` 证伪（库内值 ≠ 实际投递值），由该计划的 Phase 6 负责更正。
2. **新建 `docs/knowledge/mail/K-outbound-message-id-single-factory.md`** —— 记录：出站 Message-ID 的唯一生成入口是 `OutboundMessageIdFactory`；域名必须取自本次投递的 `account.senderEmail`；唯一性只依赖 UUID，`kind` / `discriminator` 段禁止被解析；同一邮件类型的多条产出路径必须用同一 `kind`。任何新增外发邮件路径都应继承。
