# 材料提醒邮件挂回原会话线程（第一批）

> 用 create-p skill 编写。独立计划，无前置依赖，可独立部署与验证。
> 第二批见 `material-reminder-02-headers-personalization.md`（依赖本计划，反之不成立）。
> 拆分理由：本批为纯增量改动，无锚点时退回现状行为，`INTRODUCTION` 等其余邮件类型零影响，回归面接近零；且它是"脱离 Gmail 推广标签页"这一目标里预期贡献最大的一项，应独立验证效果后再决定是否上第二批。

## 需求描述

**可观察结果**

1. 发送 `MATERIAL_REMINDER` 邮件后，收件人在 Gmail 中看到的是**挂在该专家原有会话线程里的一封回信**（与此前往来邮件折叠在同一 thread），而非独立新邮件。
2. 该专家在收发件箱里没有任何 INBOUND 记录时，提醒邮件**照常发送成功**，形态与改动前完全一致（独立新邮件、模板原主题、无线程头）。
3. `MATERIAL_REMINDER` 及其余 `COMPOSE_TEMPLATE` 外发邮件的 `Message-ID` 为 `<reminder-{contactId}-{uuid}@{senderDomain}>`，不再是 JavaMail 默认的 `<hash.JavaMail.user@host>`。
4. 后台该联系人收发件箱中，这封提醒的"回复自"（`mail_record.in_reply_to`）与实际发出的 `In-Reply-To` 头一致。

**必须不变（must NOT change）**

- `INTRODUCTION`、`MEETING_INVITATION`、`MEETING_CONFIRMATION`、QA 自动回复、人工富文本回复五类邮件的 MIME 形态**逐项不变**：不新增线程头、`Message-ID` 格式不变、`From` 不变、`List-Unsubscribe` / `List-Unsubscribe-Post` 照常携带。
- `SmtpMailDeliveryService` 的 `List-Unsubscribe` 逻辑（`:48-53`）本批**完全不动**（属第二批范围）。
- `ComposedMail(html=false)` 纯文本分支（`:44-46`）与 html multipart 分支（`:33-43`）零改动；`MATERIAL_REMINDER` 维持 `html=true` + `text=纯文本` 形态（来源: K-plaintext-reply-client-reflow）。
- `ManualExpertMailService` 的账号选择（`:52-55`）、`mail_record_qa_rule` 审计写入（`:80-91`）、状态流转（`:95-105`）、`nextStatus()`（`:187-194`）、`listSendOptions()`、`mailTemplateVariables()` 全部不变。
- `hasSentMaterialReminder()` 去重语义不变；`BatchSendSettingService` 节奏常量不变。
- `UnmatchedInboundMailService.suggestCandidates()` 的 `IN_REPLY_TO`（confidence 90）匹配继续可用。

**不在范围（out of scope）**

- `List-Unsubscribe` 头的条件化、`From` 显示名、正文称呼个性化 —— 全部属第二批。
- `INTRODUCTION` 冷启动邮件的线程化 —— 冷邮件无前序会话，无锚点可挂。
- 其余 4 个缺 `Message-ID` 的构造点（会议邀请 / 会议确认 / QA 自动回复 / 人工富文本回复）—— 既有缺陷，单独立项（见 K-message-id-fingerprint 的 2026-08-06 修正表）。
- 发送节奏、SPF/DKIM/DMARC、ES 深度个性化。
- 本计划不触及任何前端文件，故无 `## 样式契约` 一节。

---

## 关键不变量

### Invariant I-1: 线程锚点只能来自真实 INBOUND 记录，缺失时不得伪造

- Rule: `In-Reply-To` / `References` 的值只能取自 `MailRecordRepository.findLatestInboundByExpertContactId(contactId)` 返回记录的 `messageId`。该方法返回 `null`、或返回记录的 `messageId` 为空白、或其长度 > 255，则**不写任何线程头**，主题回退为模板 subject，邮件照常发送（fail-open，不阻断投递）。**禁止**用外发记录的 messageId、构造的假 ID 或空串充当锚点。
- Applies to: `ManualExpertMailService.composeComposeTemplate()`（唯一组装点）、`SmtpMailDeliveryService.send()`（唯一 MIME 写头点）。
- Violation consequence: 伪造的 In-Reply-To 指向不存在的 Message-ID，Gmail 无法归并线程反而暴露伪造头；空串头会被部分 MTA 拒信。
- 来源: original

### Invariant I-2: `Re:` 主题归一化，不得叠加

- Rule: 线程化成功时 subject = `"Re: " + strip(anchor.subject)`。`strip()` 反复剥离开头的 `Re:` / `RE:` / `re:` / `Re[n]:` / `答复:` / `回复:`（其后允许 0..n 个空白），直到不再匹配，再统一加**一个** `"Re: "` 前缀。结果超 255 字符时截断到 255（`mail_record.subject` 为 `VARCHAR(255)`，`V1__create_business_tables.sql:104`）。锚点 subject 为 null 或剥离后为空时，回退为模板渲染的 subject 且**不加** `Re:` 前缀。
- Applies to: `ManualExpertMailService.composeComposeTemplate()`。
- Violation consequence: `Re: Re: Re:` 叠加是典型机器生成特征；超长 subject 写库抛异常导致发送事务回滚。
- 来源: original

### Invariant I-3: 每封外发邮件必须有项目自生成的 UUID Message-ID

- Rule: `composeComposeTemplate()` 必须生成 `<reminder-{contactId}-{UUID}@{senderEmail 的域名部分}>` 写入 `ComposedMail.messageId`。该字段对**全部 `COMPOSE_TEMPLATE` 发送**生效，不限于 `MATERIAL_REMINDER`。落库的 `mail_record.message_id` 仍取 `delivered.messageId`（`MimeMessage.messageID`，与写入值一致）。
- Applies to: `ManualExpertMailService.composeComposeTemplate()`。
- Violation consequence: JavaMail 默认格式 `<hash.JavaMail.user@hostname>` 是可被 ESP 指纹识别的批量工具特征。本次审计确认该路径**当前正处于违反状态**（既有缺陷，非本计划引入）。
- 来源: K-message-id-fingerprint（hit_count 8）

### Invariant I-4: `mail_record.in_reply_to` 必须与实际外发头同源同值

- Rule: `sendManualMail()` 落库的 `inReplyTo` 必须与本次实际发出的 `In-Reply-To` 头**完全相同**；未写头时落 `null`。禁止出现"发了头但库里 null"或"库里有值但没发头"。实现上直接读 `composed.mail.inReplyTo`，**不得**另建一份独立计算。长度守卫在 I-1 已前置（>255 时两侧同时为 null），沿用 `PendingMailOperationService.kt:245-249` 的既有先例。
- Applies to: `ManualExpertMailService.sendManualMail()` 第 69 行（现为硬编码 `inReplyTo = null`）。
- Violation consequence: `UnmatchedInboundMailService.suggestCandidates()` 与人工排查依赖库内 `in_reply_to` 复原会话链，两侧不一致会让审计失真。
- 来源: original

### Invariant I-5: `ComposedMail` 新增字段必须带默认值，其余 7 个构造点零改动

- Rule: `inReplyTo: String? = null` 与 `references: String? = null` 均须带默认值。git diff 中除 `ManualExpertMailService.kt:175` 外，其余 7 个 `ComposedMail(...)` 构造点**必须一行未改**。
- Applies to: `IntroductionMailComposer.kt` 的 `ComposedMail` data class。
- Violation consequence: 无默认值会强制 8 处全改，把回归面从 1 条路径扩大到全部 7 条投递路径，违背本批"零回归"的拆分前提。
- 来源: original

---

## 现状审计

### `ComposedMail`（`mail/service/IntroductionMailComposer.kt:59-66`）

当前定义：`(to, subject, body, html=false, text=null, messageId=null)`。**全部 8 个构造点**（grep `ComposedMail(` 排除 data class 定义）：

| # | 位置 | mailType | messageId 现状 | 本批改动 |
|---|---|---|---|---|
| 1 | `IntroductionMailComposer.kt:28` | INTRODUCTION | ✅ `<intro-{orcid}-{uuid}@{domain}>` | 否 |
| 2 | `MeetingInvitationMailComposer.kt:22` | MEETING_INVITATION | ❌ 无 | 否 |
| 3 | `MeetingScheduleService.kt:125` | MEETING_CONFIRMATION | ❌ 无 | 否 |
| 4 | `AutoMailReplyService.kt:567` | QA 自动回复 | ❌ 无 | 否 |
| 5 | `AutoMailReplyService.kt:958` | 自动回复（另一分支） | ❌ 无 | 否 |
| 6 | `PendingMailOperationService.kt:258` | 人工富文本回复 | ❌ 无 | 否 |
| 7 | `ManualExpertMailService.kt:175` | COMPOSE_TEMPLATE / **MATERIAL_REMINDER** | ❌ 无 | **是** |
| 8 | `ManualInitialOutreachService.kt:587`（经 1） | INTRODUCTION | ✅ | 否 |

### `SmtpMailDeliveryService.send()`（`:16-70`）

- `:20-28` 仅当 `mail.messageId != null` 时用匿名 `MimeMessage` 子类覆写 `updateMessageID()` 强制写入；为 null 时走 `sender.createMimeMessage()`（JavaMail 默认）。
- `:30` `setFrom(account.senderEmail)`（本批不动）。
- `:33-46` 内容分支（本批不动）。`:48-53` 退订头（本批不动）。`:55-69` 异常分类（本批不动）。
- **全代码库出站侧从未设置 `In-Reply-To` / `References`** —— grep 仅在读取侧命中 `ImapMailReceiveService.kt:135`、`BounceDetector.kt:193`。
- **全部 7 个投递调用点**：`PendingMailOperationService:270`、`AutoMailReplyService:574`、`AutoMailReplyService:963`、`ManualExpertMailService:57`、`ManualInitialOutreachService:626`、`MeetingScheduleService:130`、`InitialOutreachService:66`。本批只改 `ComposedMail` 载体与 `send()` 内的**条件写头**分支，未传值的 6 条路径行为不变。

### `ManualExpertMailService`

- `composeComposeTemplate()`（`:157-185`）：`:164-168` 调 `render(templateId, mailTemplateVariables(account), variantSeedFor(...))`；`:175-181` 构造 `ComposedMail(to, subject=rendered.subject, body=plainTextToHtml(rendered.body), html=true, text=rendered.body)` —— **无 messageId**（违反 I-3）。
- `sendManualMail()`（`:46-115`）：`:69` `inReplyTo = null` 硬编码 ← 本批改动点。
- 构造器（`:20-30`）**已注入 `mailRecordRepository`**（第 22 行），本批无需新增依赖。

### `mail_record`（MySQL，`V1__create_business_tables.sql:97-115`）

- Schema：`message_id VARCHAR(255)`、`in_reply_to VARCHAR(255)`、`subject VARCHAR(255)`、`body LONGTEXT`。**本批不新增任何列，只填充既有的 `in_reply_to`。**
- OUTBOUND 写路径：
  1. `ManualExpertMailService.sendManualMail()` `:60` — `inReplyTo = null` ← **本批改动**
  2. `ManualOutreachTxHelper.recordSuccess()` `:50` / `recordFailure()` `:102` — `inReplyTo = null`（INTRODUCTION，不改）
  3. `MeetingScheduleService.kt:142` — `inReplyTo = null`（不改）
  4. `AutoMailReplyService.kt:586` / `:974` — `inReplyTo = received.messageId`（已正确填充）
  5. `PendingMailOperationService.kt:240` — `inReplyTo = record.messageId`（已正确填充，`:245-249` 有 255 长度守卫先例）
  6. `ManualReplySendAttemptService.kt:220/237/298/315` — 透传 payload
- 读路径：`UnmatchedInboundMailService.suggestCandidates()` `:76-78`（`inReplyTo` → `findByMessageId()` → `IN_REPLY_TO` confidence 90）；`ExpertContactManagementController.kt:516`、`UnmatchedInboundMailController.kt:1054` API 透传。
- 交互点：
  - **IP-1**：本服务写 `message_id` × `suggestCandidates()` 读 `findByMessageId(inReplyTo)`。提醒邮件 Message-ID 从 JavaMail 默认改为自生成 UUID 后，专家回信的 `In-Reply-To` 携带新格式；`mail_record.message_id` 落的是 `delivered.messageId`（实际发出值），两侧仍一致，匹配链不断。**需 A-n 验证。**
  - **IP-2**：本服务写 `in_reply_to` × `suggestCandidates()` 读。该列在提醒路径此前恒为 null，改动后开始有值，属新增数据流入既有读路径。**需 A-n 验证。**

### 线程锚点数据源

- `MailRecordRepository.findLatestInboundByExpertContactId(contactId)` **已存在**（`ORDER BY COALESCE(received_at, created_at) DESC, id DESC LIMIT 1`），无需新增查询方法。
- 目标人群：批量入口 `buildMaterialReminderSnapshot()`（`ManualInitialOutreachService.kt:1043-1055`）限定 `funnelLevels=["APPLICATION"]` + `tags=["承诺回复材料"]`，APPLICATION 层定义为"已回复"，故绝大多数目标有 INBOUND 记录。但**手动单发入口不受此约束**（来源: K-material-reminder-single-compose-seam），可对任意 contact 发提醒 → I-1 的 fail-open 分支是必需的，不是理论情况。

### 组装入口收敛性

`MATERIAL_REMINDER` 只有一个组装点 `composeComposeTemplate()`，批量（`ManualInitialOutreachService.kt:299-304`）与手动单发（`/api/expert-contacts/{id}/mail`）两条入口均汇入此处，改一处即可（来源: K-material-reminder-single-compose-seam）。**不适用 K-dual-outreach-paths** —— 那条描述的是 `INTRODUCTION` 的两条路径，提醒邮件不经过 `IntroductionMailComposer`。

### 既有测试基线

- `SmtpMailDeliveryServiceTest.kt`：共 12 条。`:32-105` 六条 SMTP 异常分类、`:126/:152` 两条退订头、`:174/:195/:221` 三条内容形态，全部为 must-NOT-change 回归基线。
- `ManualExpertMailServiceTest.kt`：共 11 条。`:189` `sends compose template as html with plain text fallback`、`:399` `MATERIAL_REMINDER keeps current status and records outbound mail` 是关键基线。

---

## 实现方案

### 任务 1：`ComposedMail` 扩展两个带默认值的字段（I-5）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt`

```kotlin
data class ComposedMail(
    val to: String,
    val subject: String,
    val body: String,
    val html: Boolean = false,
    val text: String? = null,
    val messageId: String? = null,
    val inReplyTo: String? = null,
    val references: String? = null
)
```

除 data class 外**不得修改本文件任何内容** —— `IntroductionMailComposer.compose()` 保持原样（K-introduction-compose-callers：它有且仅有 2 个调用方，改 variables map 会同时影响两者）。

### 任务 2：`SmtpMailDeliveryService` 条件写线程头（I-1）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt`

在 `:32`（设完 subject）之后、`:33` 内容分支之前插入：

```kotlin
mail.inReplyTo?.takeIf { it.isNotBlank() }?.let { message.setHeader("In-Reply-To", it) }
mail.references?.takeIf { it.isNotBlank() }?.let { message.setHeader("References", it) }
```

用 `setHeader` 而非 `addHeader`：按 RFC 5322 这两个头每封信只允许出现一次。空白值一律不写头（I-1）。

**不得改动**：`:20-28` Message-ID 覆写逻辑、`:30` `setFrom`、`:33-46` 内容分支、`:48-53` 退订头、`:55-69` 异常分类。

### 任务 3：提醒邮件线程锚点解析 + Message-ID（I-1, I-2, I-3）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt`

在 `composeComposeTemplate()` 的 `:173` 之前插入：

```kotlin
val isMaterialReminder = (rendered.mailType ?: "COMPOSE_TEMPLATE") == "MATERIAL_REMINDER"
val anchor = if (isMaterialReminder) {
    contact.id?.let { mailRecordRepository.findLatestInboundByExpertContactId(it) }
} else null
val anchorMessageId = anchor?.messageId
    ?.trim()
    ?.takeIf { it.isNotBlank() && it.length <= 255 }        // I-1 长度守卫
val threadSubject = if (anchorMessageId != null) {
    buildReplySubject(anchor?.subject, rendered.subject)     // I-2
} else rendered.subject
val references = if (anchorMessageId != null) {
    listOfNotNull(anchor?.inReplyTo?.trim()?.takeIf { it.isNotBlank() }, anchorMessageId)
        .joinToString(" ")
} else null
```

新增私有函数（I-2）：

```kotlin
private fun buildReplySubject(anchorSubject: String?, fallback: String): String {
    val stripped = stripReplyPrefixes(anchorSubject?.trim().orEmpty())
    if (stripped.isBlank()) return fallback
    return ("Re: $stripped").take(255)
}

private fun stripReplyPrefixes(subject: String): String {
    var s = subject
    while (true) {
        val m = REPLY_PREFIX_REGEX.find(s) ?: break
        s = s.removeRange(m.range).trimStart()
    }
    return s.trim()
}
```

companion object 内：

```kotlin
private val REPLY_PREFIX_REGEX =
    Regex("""^\s*(re|答复|回复)\s*(\[\d+\])?\s*[:：]\s*""", RegexOption.IGNORE_CASE)
```

`:173-184` 的构造改为：

```kotlin
val senderDomain = account.senderEmail.substringAfter("@")
return ManualComposedMail(
    mailType = rendered.mailType ?: "COMPOSE_TEMPLATE",
    mail = ComposedMail(
        to = contact.expertEmail,
        subject = threadSubject,                                                    // I-2
        body = mailContentService.plainTextToHtml(rendered.body),
        html = true,
        text = rendered.body,
        messageId = "<reminder-${contact.id}-${UUID.randomUUID()}@$senderDomain>",   // I-3
        inReplyTo = anchorMessageId,                                                // I-1
        references = references                                                     // I-1
    ),
    matchedQaRuleId = rendered.qaRuleIds.firstOrNull(),
    qaRuleIds = rendered.qaRuleIds
)
```

**不得改动**：`mailTemplateVariables()`（属第二批）、`:52-55` 账号选择、`:80-91` QA 审计、`:95-105` 状态流转、`:187-194` `nextStatus()`、`listSendOptions()`。

### 任务 4：`in_reply_to` 落库同源（I-4）

文件：同上。`sendManualMail()` `:69` 的 `inReplyTo = null` 改为 `inReplyTo = composed.mail.inReplyTo`。

**不得**在 `ManualComposedMail` 上新增独立的 `inReplyTo` 字段 —— 必须与实际发出值同源（I-4）。

### 任务 5：投递层测试（I-1）

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt`

保留既有 12 条不改语义，新增：

- `send writes In-Reply-To and References headers when provided`
- `send omits thread headers when inReplyTo and references are null`
- `send omits thread headers when inReplyTo is blank`
- `send writes In-Reply-To only once`（断言 `getHeader("In-Reply-To")` 数组长度为 1，验证用的是 `setHeader` 不是 `addHeader`）

### 任务 6：组装层测试（I-1, I-2, I-3, I-4）

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt`

保留既有 11 条不改语义，新增：

- `MATERIAL_REMINDER threads onto latest inbound message id`（断言 `inReplyTo` == 锚点 messageId、`references` 含锚点、`subject` 以单个 `Re: ` 开头）
- `MATERIAL_REMINDER without inbound anchor sends without thread headers`（锚点查询返回 null → `inReplyTo`/`references` 均 null，subject 为模板 subject 且不含 `Re: `）
- `MATERIAL_REMINDER strips stacked reply prefixes`（锚点 subject `"Re: RE: 回复: Hello"` → 结果恰为 `"Re: Hello"`）
- `MATERIAL_REMINDER truncates oversized subject to 255`
- `MATERIAL_REMINDER skips threading when anchor message id exceeds 255 chars`（断言 `inReplyTo` 为 null 且 subject 无 `Re: `）
- `MATERIAL_REMINDER references includes anchor inReplyTo when present`
- `non-reminder compose template does not query inbound anchor`（断言 `findLatestInboundByExpertContactId` 零调用）
- `sendManualMail persists inReplyTo matching the sent header`（I-4，含双双为 null 分支）
- `sendManualMail sets uuid message id for compose template`（I-3，断言匹配 `^<reminder-\d+-[0-9a-f-]{36}@.+>$`）

---

## 变更文件清单

| # | 文件 | 类型 | 子系统 | 不变量 |
|---|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt` | 修改（仅 `ComposedMail` data class） | 1 投递层 | I-5 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt` | 修改 | 1 投递层 | I-1 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` | 修改 | 2 组装层 | I-1, I-2, I-3, I-4 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt` | 修改 | 1 投递层 | I-1 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt` | 修改 | 2 组装层 | I-1, I-2, I-3, I-4 |

**文件数：5 ≤ 10 ✓** ｜ **子系统数：2 ✓** ｜ **共享存储新增字段：0（`in_reply_to` 为既有列）✓** ｜ **数据库迁移：无 ✓**

---

## 验收标准（fix-v 机器验证）

- **I-1**：grep `SmtpMailDeliveryService.kt` 确认两个头均在 `takeIf { it.isNotBlank() }` 保护下、且用 `setHeader`；grep `ManualExpertMailService.kt` 确认锚点唯一来源为 `findLatestInboundByExpertContactId`，无其他 messageId 来源；单测 `without inbound anchor` 与 `exceeds 255 chars` 两条通过。
- **I-2**：单测 `strips stacked reply prefixes` 断言输出恰为 `"Re: Hello"`；`truncates oversized subject to 255` 断言 `subject.length == 255`。
- **I-3**：单测断言 `ComposedMail.messageId` 匹配 `^<reminder-\d+-[0-9a-f-]{36}@.+>$`。
- **I-4**：单测断言落库 `MailRecord.inReplyTo` 与捕获的 `ComposedMail.inReplyTo` 全等（含双双 null 分支）；grep 确认 `sendManualMail` 读的是 `composed.mail.inReplyTo`，无第二份计算。
- **I-5**：git diff 确认 `ComposedMail` 两个新字段均有 `= null` 默认值；确认 `MeetingInvitationMailComposer.kt`、`MeetingScheduleService.kt`、`AutoMailReplyService.kt`、`PendingMailOperationService.kt`、`InitialOutreachService.kt`、`ManualInitialOutreachService.kt` 六个文件**零改动**。
- **回归**：`SmtpMailDeliveryServiceTest` 既有 12 条全绿；`ManualExpertMailServiceTest` 既有 11 条全绿；`mvn test` 全量通过。
- **IP-1 集成**：构造一封 `In-Reply-To = <reminder-...>` 的入站信，断言 `suggestCandidates()` 产出 `reason = "IN_REPLY_TO"` 且 `confidence = 90` 的候选。
- **未越界**：git diff 确认 `SmtpMailDeliveryService.kt` 的 `:30 setFrom` 与 `:48-53` 退订头区块**一行未改**（属第二批）；`mailTemplateVariables()` 一行未改；无新增 `db/migration` 文件。

---

## 人工验收清单

### A-1：提醒邮件挂进原会话线程

- 前置条件：准备一个真实 Gmail 收件箱作为测试专家邮箱。① 用系统给它发一封 `INTRODUCTION`；② 用该 Gmail 回信一句 "I will send the materials next week."；③ 等系统 IMAP 收信入库，在后台该联系人收发件箱确认能看到这条 INBOUND 记录；④ 在 ES 给该专家打上 `承诺回复材料` 标签，确认 funnel 层级为 APPLICATION。
- 操作步骤：
  1. 后台进入该专家详情，选 `Material Reminder Email` 模板手动发送一封。
  2. 打开测试 Gmail 收件箱。
- 预期结果：这封提醒**折叠在与第 ① 封介绍邮件相同的会话线程内**（Gmail 中显示为同一 thread，点开可见 3 封往来）。位于「主要」标签页，**不出现在「推广」标签页**。
- 覆盖：需求描述第 1 条，I-1，I-2

### A-2：无入站锚点时降级发送不报错

- 前置条件：新建一个联系人，或选一个 `currentStatus = NEW`、收发件箱里**没有任何 INBOUND 记录**的联系人。
- 操作步骤：在该联系人详情手动发送 `Material Reminder Email`。
- 预期结果：页面提示发送成功，不报错、不 500。收到的邮件主题为 `Gentle Follow-up on the Requested Materials`（模板原主题，**不带** `Re:`），是一封独立新邮件，不挂任何线程。后台该联系人收发件箱新增一条 OUTBOUND 记录，其「回复自」字段为空。
- 覆盖：需求描述第 2 条，I-1 的 fail-open 分支

### A-3：主题不叠加 `Re:`

- 前置条件：构造一个联系人，其最新一条 INBOUND 记录的主题为 `Re: RE: 回复: Materials for the Talent Program`（可用 SQL：`UPDATE mail_record SET subject='Re: RE: 回复: Materials for the Talent Program' WHERE id=<最新 INBOUND 记录 id>;`）。
- 操作步骤：对该联系人发送 `Material Reminder Email`，在后台收发件箱查看新 OUTBOUND 记录的主题。
- 预期结果：主题**恰为** `Re: Materials for the Talent Program` —— 一个 `Re:` 前缀，无 `RE:`、无 `回复:`、无重复。
- 覆盖：I-2

### A-4：Message-ID 不再是 JavaMail 默认格式

- 操作步骤：对 A-1 收到的提醒邮件点「显示原邮件」，查找 `Message-ID:` 与 `In-Reply-To:` 两行。
- 预期结果：`Message-ID:` 形如 `<reminder-12345-3f2a9c1e-....@talents.szwebotech.cn>`，**不含** `.JavaMail.` 字样。`In-Reply-To:` 的值与后台该专家收发件箱里那条 INBOUND 记录的 Message-ID **逐字相同**，且 `In-Reply-To` 行**只出现一次**。
- 覆盖：需求描述第 3、4 条，I-1，I-3，I-4

### A-5：专家回信仍能自动关联到联系人（跨路径）

- 前置条件：完成 A-1，收到提醒邮件。
- 操作步骤：在测试 Gmail 中**直接点「回复」**回一句 "Attached, sorry for the delay."，等待系统 IMAP 收信周期或手动触发一次收信。
- 预期结果：后台该专家收发件箱出现这条新 INBOUND 记录，且**自动关联到正确的联系人**（不落入「未匹配来信」列表）。若确实落入未匹配列表，其候选建议中必须有一条 `IN_REPLY_TO` / 置信度 90 的候选指向正确联系人。
- 覆盖：IP-1、IP-2

### A-6：其余邮件类型端到端回归

- 操作步骤：依次触发 ① 一封 `INTRODUCTION`（批量跑一轮或手动单发）；② 一封 QA 自动回复（用测试 Gmail 给系统发一封命中 QA 规则的问询信）；③ 一封会议邀请；④ 一封人工富文本回复。对每封点「显示原邮件」。
- 预期结果：四封均正常送达。源码中**均无** `In-Reply-To` / `References` 头（QA 自动回复与人工回复此前也没有，本批未给它们传值）。四封**均带** `List-Unsubscribe` 与 `List-Unsubscribe-Post: List=One-Click`（前提是 `UNSUBSCRIBE_BASE_URL` / `UNSUBSCRIBE_SECRET` 已配置）。`From:` 行为裸邮箱形态，与改动前一致。正文段落格式完好，不塌成一堵墙。
- 覆盖：must-NOT-change 第 1、2、3 条；确认第二批范围未被提前带入

### A-7：批量提醒任务整体回归

- 前置条件：后台配置一个 `MATERIAL_REMINDER` 批量任务，目标范围内至少 3 个联系人（其中至少 1 个有 INBOUND 记录、至少 1 个没有）。
- 操作步骤：手动触发跑一轮。
- 预期结果：任务正常完成，无异常中断。任务执行记录中成功数与目标数一致。已发过提醒的联系人在下一轮被去重跳过（`hasSentMaterialReminder` 语义不变）。有锚点的收到线程化邮件，无锚点的收到独立邮件，两者均发送成功。
- 覆盖：must-NOT-change 第 4 条（去重语义、节奏常量）

---

## 上线前置条件

`docs/plans/2026-07-03/google-spam-mitigation.md` 记录发信子域名 `talents.szwebotech.cn` 与 `mail.szwebotech.cn` **SPF 缺失**，`K-deliverability-dns-live-verification` 记录复验时两者 TXT 仍为空。

**上线前必须跑一次 `dig +short TXT talents.szwebotech.cn` 与 `dig +short TXT mail.szwebotech.cn`。** 若仍为空，先补 SPF 再上本批 —— 否则线程化的收益会被域信誉问题抵消，而你会误判成"线程化没用"，从而错误地放弃第二批。

## 效果观察与第二批决策

本批上线后观察至少两周：统计 `MATERIAL_REMINDER` 落入「主要」标签页的比例（可通过少量真实收件人抽样或 Postmaster Tools 的 spam rate 趋势侧面判断）。

- 若提醒已稳定进入「主要」→ 第二批的退订头改动（涉及修正 `unsubscribe-suppression-02` 的 L2-2，有合规争议面）可以直接**不做**，只保留称呼个性化与 From 显示名。
- 若仍在「推广」→ 按 `material-reminder-02-headers-personalization.md` 执行第二批。
