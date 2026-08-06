# 材料提醒邮件退订头抑制、发件人显示名与称呼个性化（第二批）

> 用 create-p skill 编写。
> **前置依赖**：`material-reminder-01-threading.md` 必须先落地并完成人工验收。本计划复用其在 `ComposedMail` 上确立的"新增字段必带默认值"约定与投递层改动惯例。第一批不依赖本批。
> **执行前决策点**：若第一批上线后 `MATERIAL_REMINDER` 已稳定进入 Gmail「主要」标签页，**任务 1/2（退订头）应直接放弃**，只执行任务 3-5（称呼个性化）与任务 6（From 显示名）。退订头改动涉及修正既有计划的不变量且有合规争议面，收益不明确时不值得做。
> **修正既有计划**：`docs/plans/2026-06-20/unsubscribe-suppression-02-list-unsubscribe-oneclick.md` 的 Invariant L2-2（见本文 J-1）。

## 需求描述

**可观察结果**

1. `MATERIAL_REMINDER` 外发邮件的 MIME 头**不再包含** `List-Unsubscribe` 与 `List-Unsubscribe-Post`；Gmail 中发件人名字旁不再出现「退订」按钮。
2. `INTRODUCTION` 及其余所有邮件类型的这两个头**行为逐字不变**。
3. 所有外发邮件在发件账号配置了 `senderDisplayName` 时，`From` 头呈现为 `显示名 <邮箱>`；未配置时维持当前裸邮箱形态。
4. `MATERIAL_REMINDER` 正文首行由固定的 `Dear Professor,` 变为按收件人渲染：`expert_contact.expert_name` 有合法值时为 `Dear <姓名>,`，为空或为技术标识时回退 `Dear Professor,`。

**必须不变（must NOT change）**

- `INTRODUCTION`、`MEETING_INVITATION`、`MEETING_CONFIRMATION`、QA 自动回复、人工富文本回复的 `List-Unsubscribe` / `List-Unsubscribe-Post` 头**照常携带**（`UnsubscribeTokenService.enabled()` 为真时），内容与顺序逐字不变。
- 第一批确立的线程头行为（`In-Reply-To` / `References` / `Re:` 主题归一化 / `<reminder-...>` Message-ID / `in_reply_to` 落库同源）**全部不变**。
- `MATERIAL_REMINDER` 正文除首行称呼外的**所有段落逐字不变**，与 `V71` 内容一致。
- `MailComposeTemplateService.renderText()` 及其余 4 个变量注入入口零改动（K-renderText-all-callers）。
- `ComposedMail(html=false)` 纯文本分支与 html multipart 分支零改动。
- `ManualExpertMailService` 的账号选择、QA 审计写入、状态流转、`nextStatus()`、`listSendOptions()` 不变。

**不在范围（out of scope）**

- 接入 ES / `ExpertSearchService` / `MailVariableService` 取全量专家变量（`institution` / `country` / `researchFields`）—— 会给每封信引入一次 ES 查询与新的失败分支，且这些字段在生产库大面积为空（`mail-personalization-anti-spam` 已记录 `researchFields` / `keyword` 从未被采集管道填充）。单独立项。
- 给 `INTRODUCTION` 或其他类型抑制退订头 —— 那正是 2026-07-03 投诉率 6.9% 事故的成因。
- 主题行个性化 —— 第一批的 `Re: <原主题>` 已覆盖。
- 发送节奏、SPF/DKIM/DMARC。
- 本计划不触及任何前端文件，故无 `## 样式契约` 一节。

---

## 关键不变量

### Invariant J-1: 退订头抑制的默认值必须是"携带"，且只有一个显式关闭点

- Rule: `ComposedMail` 新增字段 `listUnsubscribe: Boolean = true`。`SmtpMailDeliveryService.send()` 的 `if (unsubscribeTokenService.enabled())`（`:48`）收紧为 `if (mail.listUnsubscribe && unsubscribeTokenService.enabled())`。全代码库**有且仅有一处**显式传 `listUnsubscribe = false`，即 `ManualExpertMailService.composeComposeTemplate()` 在 `rendered.mailType == "MATERIAL_REMINDER"` 时；其余 7 个 `ComposedMail` 构造点一律使用默认值，源码零改动。**禁止**在投递层按 mailType 硬编码判断（策略必须留在组装层）。
- Applies to: `SmtpMailDeliveryService.send()`；`ComposedMail` 全部 8 个构造点。
- Violation consequence: 默认值若为 `false`，或判断逻辑下沉到投递层，会让 `INTRODUCTION` 冷邮件也失去退订头 —— `docs/plans/2026-07-03/google-spam-mitigation.md` 记录该场景导致投诉率 6.9%（Google 阈值 0.3%）。
- **本条修正 `unsubscribe-suppression-02` 的 Invariant L2-2**（原文要求"每封外发邮件"均带该两头，缩小为"除 `MATERIAL_REMINDER` 外的每封外发邮件"）。修正依据：Google 的 bulk sender 一键退订强制要求适用于**日发 5000 封以上**且为 **marketing/promotional** 性质的邮件；本项目 `DEFAULT_REMINDER_DAILY_CAP = 60`（`BatchSendSettingService.kt:225`），远低于阈值，且 `MATERIAL_REMINDER` 收件人是已回信并承诺提供材料的专家，属一对一事务性跟进，非营销邮件。执行本计划时**必须**同步在 `unsubscribe-suppression-02` 追加 `## 修正记录`（任务 7）。
- 来源: original（修正 unsubscribe-suppression-02 L2-2）

### Invariant J-2: `From` 显示名仅在配置非空时生效，且必须 RFC 2047 编码

- Rule: `SmtpMailDeliveryService.send()` `:30` 中，`account.senderDisplayName` 经 `trim()` 后非空时用 `message.setFrom(InternetAddress(account.senderEmail, displayName, "UTF-8"))`；为 null 或全空白时**必须**退回现状 `message.setFrom(account.senderEmail)`。非 ASCII 显示名由该构造器完成 RFC 2047 编码，**禁止**手工拼接原始字符串。
- Applies to: `SmtpMailDeliveryService.send()` —— **影响全部 7 个投递调用点**（清单见现状审计）。
- Violation consequence: 未配置账号若被拼出 `" " <addr>` 形态会破坏 From 头；非 ASCII 显示名未编码会导致乱码或 MTA 拒信；这是本批回归面最大的一处改动。
- 来源: original

### Invariant J-3: 收件人称呼只取 `expert_contact.expertName`，且必须过滤技术标识

- Rule: `ManualExpertMailService.mailTemplateVariables()` 新增**唯一一个** key `expertName`，取值为 `ExpertRecipientNamePolicy.resolveRecipientName(contact, null)`，为 null 时置空串。**禁止**引入 `ExpertSearchService` / `MailVariableService` 依赖。**禁止**注入 `expertFamilyName` —— `resolveFamilyName(expertProfile = null, ...)` 在无 `ExpertProfile` 时恒返回空串（`MailVariableService.kt:52-56`），注入它等同于永远走 fallback，属误导性接线。
- Applies to: `ManualExpertMailService.mailTemplateVariables()`；`V84` 迁移中的模板正文。
- Violation consequence: 直接用 `contact.expertName` 而不过滤会渲染出 `Dear EMAIL-abc123,` / `Dear 0000-0002-1825-0097,`。
- 来源: K-recipient-name-no-technical-identifier（severity P1）、K-manual-expert-mail-sender-only-variables

### Invariant J-4: 变量注入只改一个入口，`renderText` 本身不动

- Rule: 只在 `ManualExpertMailService.mailTemplateVariables()` 这**一个**入口新增 key。**禁止**修改 `MailComposeTemplateService.renderText()` 本身，**禁止**改动其余 4 个注入入口（`IntroductionMailComposer.compose()`、`AutoMailReplyService.mailTemplateVariables()`、`AutoReplyPreviewService.mailTemplateVariables()`、`MeetingInvitationMailComposer`）。
- Applies to: `ManualExpertMailService.mailTemplateVariables()`。
- Violation consequence: `renderText` 是全部模板变量替换的唯一实现点（4 处内部调用 + 5 个外部注入入口），改它会同时影响 5 条链路。
- 来源: K-renderText-all-callers（hit_count 16）

### Invariant J-5: 模板迁移不得覆盖运营的运行时改动

- Rule: `V84` 的两条 `UPDATE` 必须使用 `REPLACE(...)` + `WHERE ... LIKE '%Dear Professor,%'` 守卫，只在正文仍为迁移基线时才改写。**禁止**整体 `SET body = '...'` 覆写。上线前须先在生产库核对当前正文基线。同时更新 `mail_template.body` 与 `mail_compose_template_block.custom_text` 两处（`V71` 建立的同步关系）。
- Applies to: `V84__personalize_material_reminder_template.sql`。
- Violation consequence: Flyway 对模板正文的整体 UPDATE 会覆盖运营在后台已做的运行时改动。
- 来源: K-qa-rule-runtime-vs-migration-writes

### Invariant J-6: `ComposedMail` 新增字段必须带默认值

- Rule: `listUnsubscribe: Boolean = true` 须带默认值。git diff 中除 `ManualExpertMailService.kt:175` 外，其余 7 个 `ComposedMail(...)` 构造点必须一行未改。
- Applies to: `IntroductionMailComposer.kt` 的 `ComposedMail` data class。
- Violation consequence: 无默认值会强制 8 处全改，把回归面扩大到全部投递路径。
- 来源: original（沿用第一批 I-5）

---

## 现状审计

### `SmtpMailDeliveryService.send()`（`:16-70`，含第一批改动后的状态）

- `:30` `message.setFrom(account.senderEmail)` —— **裸地址，无显示名**，尽管 `MailSenderAccount.senderDisplayName` 字段存在（`MailSenderAccount.kt:15`）且已在 6 处模板变量注入中被使用（`MeetingInvitationMailComposer:17`、`AutoReplyPreviewService:212`、`AutoMailReplyService:995`、`ManualExpertMailService:203`、`MeetingScheduleService:120`，以及 `MailSenderAccountController` 的 CRUD 透传）。← 本批改动点
- `:48-53` **无条件**追加 `List-Unsubscribe` + `List-Unsubscribe-Post: List=One-Click`，仅受 `unsubscribeTokenService.enabled()` 门控（该方法要求 `baseUrl` 与 `secret` 均非空，`UnsubscribeTokenService.kt`）。← 本批改动点
- **全部 7 个投递调用点**（J-2 的 From 改动同时影响这 7 条）：
  1. `PendingMailOperationService.kt:270`（人工富文本回复）
  2. `AutoMailReplyService.kt:574`（QA 自动回复）
  3. `AutoMailReplyService.kt:963`（自动回复另一分支）
  4. `ManualExpertMailService.kt:57`（提醒 / COMPOSE_TEMPLATE）
  5. `ManualInitialOutreachService.kt:626`（INTRODUCTION 批量）
  6. `MeetingScheduleService.kt:130`（会议确认）
  7. `InitialOutreachService.kt:66`（INTRODUCTION 自动）

### `ComposedMail` 8 个构造点（J-1, J-6 的改动面）

| # | 位置 | mailType | `listUnsubscribe` 取值 |
|---|---|---|---|
| 1 | `IntroductionMailComposer.kt:28` | INTRODUCTION | 默认 `true`（不改） |
| 2 | `MeetingInvitationMailComposer.kt:22` | MEETING_INVITATION | 默认 `true`（不改） |
| 3 | `MeetingScheduleService.kt:125` | MEETING_CONFIRMATION | 默认 `true`（不改） |
| 4 | `AutoMailReplyService.kt:567` | QA 自动回复 | 默认 `true`（不改） |
| 5 | `AutoMailReplyService.kt:958` | 自动回复另一分支 | 默认 `true`（不改） |
| 6 | `PendingMailOperationService.kt:258` | 人工富文本回复 | 默认 `true`（不改） |
| 7 | `ManualExpertMailService.kt:175` | COMPOSE_TEMPLATE / MATERIAL_REMINDER | **显式 `!isMaterialReminder`** ← 唯一改动点 |
| 8 | `ManualInitialOutreachService.kt:587`（经 1） | INTRODUCTION | 默认 `true`（不改） |

### `ManualExpertMailService.mailTemplateVariables()`（`:196-204`）

**只注入 6 个 sender 变量**（`senderEmail` / `senderName` / `senderTitle` / `teamName` / `countryName` / `senderDisplayName`），**零个专家变量**。这是 K-renderText-all-callers 列出的 5 个注入入口中的第 3 个。

→ **模板正文里写 `${expertName}` 在提醒邮件路径当前完全不渲染**，只输出字面量或走 `${key|fallback}` 回退。这与 `IntroductionMailComposer.compose()` 形成不对称（后者经 `MailVariableService.buildVariables()` 注入全量 sender + expert + unsubscribe 变量）：同一个模板在两条路径下渲染结果不同（来源: K-manual-expert-mail-sender-only-variables）。

调用点：`composeComposeTemplate()` `:166` 一处。改签名加 `contact` 参数只影响此处。

### `ExpertRecipientNamePolicy`（`MailVariableService.kt:13`，`internal object`）

同 module 可直接引用，无需改可见性。两个函数语义不同：

- `resolveRecipientName(contact: ExpertContact, expertProfile: ExpertProfile?): String?` —— `expertProfile` 传 `null` 时仍能从 `contact.expertName` 取值（`:45-47`），并过滤 `EMAIL-*` 前缀、ORCID 格式、等于 contact 邮箱/ORCID 的值（`:27-33`、`:100-105`）。**可用于无 ES 的 contact-only 路径。** ← 本批使用
- `resolveFamilyName(expertProfile: ExpertProfile?, contact: ExpertContact?): String` —— 只读 `expertProfile?.familyNames`，`expertProfile == null` 时首行 `if (family.isBlank()) return ""` 即返回空串（`:52-56`）。**contact-only 路径不可用。** ← 本批禁用

### `renderText` 的 fallback 语法（`MailComposeTemplateService`）

`renderText()` 先用 `FALLBACK_PLACEHOLDER_REGEX` 处理 `${key|默认值}`（变量值为空字符串时走默认值），再做普通 `${key}` 替换。故 `Dear ${expertName|Professor},` 在 `expertName` 为空串时正确渲染为 `Dear Professor,`。**本批不修改该函数。**

### 模板正文与迁移（J-5）

- `V71__update_material_reminder_template.sql` 同时写 `mail_template.body`（`:3-22`）与 `mail_compose_template_block.custom_text`（`:37-52`，由 `mail_template.body` 灌入）。正文首行为 `Dear Professor,`。
- `mail_compose_template_block.custom_text` 是管理后台**可运行时编辑**的，`V71` 之后的编辑不走迁移。
- 当前最新迁移为 `V83__create_trust_reply_workbench_state.sql` → 本批新增 `V84`。

### 既有测试基线

- `SmtpMailDeliveryServiceTest.kt`：`:126` `send adds List-Unsubscribe headers when token service enabled` 与 `:152` `send omits List-Unsubscribe headers when token service disabled` 断言的是**无条件**行为 —— J-1 落地后须保留（验证默认 `listUnsubscribe=true` 仍带头），并新增覆盖 `listUnsubscribe=false` 的用例。加上第一批新增的 4 条线程头用例，本批开始时基线为 16 条。
- `ManualExpertMailServiceTest.kt`：第一批结束时基线为 20 条（原 11 + 新增 9）。

### 交互点

- **IP-A**：`ManualExpertMailService` 决定 `listUnsubscribe` × `SmtpMailDeliveryService` 消费。策略在组装层、执行在投递层，跨模块。**需 A-n 双向验证**（提醒无头 + 介绍有头）。
- **IP-B**：`SmtpMailDeliveryService` 改 From 头 × 其余 6 条投递路径消费。**需回归 A-n。**
- **IP-C**：`V84` 写 `custom_text` × `MailComposeTemplateService.resolveBlocks()` 读并经 `renderText` 渲染 × `mailTemplateVariables()` 提供 `expertName`。三方必须对齐：迁移写的占位符名、注入的 key 名、fallback 语法三者一致才渲染得出。**需 A-n 验证。**

---

## 实现方案

### 阶段 A：退订头抑制（可放弃 —— 见文首决策点）

#### 任务 1：`ComposedMail` 加 `listUnsubscribe`（J-1, J-6）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt`

在第一批已扩展的基础上追加**一个**字段：

```kotlin
data class ComposedMail(
    val to: String,
    val subject: String,
    val body: String,
    val html: Boolean = false,
    val text: String? = null,
    val messageId: String? = null,
    val inReplyTo: String? = null,      // 第一批已加
    val references: String? = null,     // 第一批已加
    val listUnsubscribe: Boolean = true // 本批新增，默认必须 true
)
```

#### 任务 2：投递层条件化退订头（J-1）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt`

`:48` 的 `if (unsubscribeTokenService.enabled())` 改为：

```kotlin
if (mail.listUnsubscribe && unsubscribeTokenService.enabled()) {
```

**块内 `:49-52` 四行逐字不变**（URL 构造、mailto 构造、两个 `addHeader` 调用及其头名与值格式）。**禁止**在本文件出现任何 mailType 判断。

### 阶段 B：称呼个性化

#### 任务 3：注入 `expertName` 变量（J-3, J-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt`

```kotlin
private fun mailTemplateVariables(
    account: MailSenderAccount,
    contact: ExpertContact
): Map<String, String> =
    mapOf(
        "senderEmail" to account.senderEmail,
        "senderName" to account.senderName,
        "senderTitle" to account.senderTitle.orEmpty(),
        "teamName" to account.teamName.orEmpty(),
        "countryName" to account.countryName.orEmpty(),
        "senderDisplayName" to account.senderDisplayName.orEmpty(),
        "expertName" to (ExpertRecipientNamePolicy.resolveRecipientName(contact, null) ?: "")
    )
```

调用点 `:166` 同步改为 `mailTemplateVariables(account, contact)`。

**只加 `expertName` 一个 key**（J-3）。**不得**新增 `ExpertSearchService` / `MailVariableService` 构造器依赖。

#### 任务 4：`MATERIAL_REMINDER` 显式关闭退订头（J-1）

文件：同上。`composeComposeTemplate()` 的 `ComposedMail(...)` 构造追加一行（`isMaterialReminder` 变量在第一批任务 3 中已定义，直接复用）：

```kotlin
    listUnsubscribe = !isMaterialReminder    // J-1
```

#### 任务 5：模板正文迁移（J-3, J-5）

文件：`src/main/resources/db/migration/V84__personalize_material_reminder_template.sql`

```sql
-- 只改称呼行；其余段落保持 V71 内容逐字不变。
-- LIKE 守卫：只在正文仍为迁移基线时改写，避免覆盖运营的运行时编辑
-- （K-qa-rule-runtime-vs-migration-writes）。

UPDATE mail_template
SET body = REPLACE(body, 'Dear Professor,', 'Dear ${expertName|Professor},')
WHERE template_code = 'MATERIAL_REMINDER'
  AND body LIKE '%Dear Professor,%';

UPDATE mail_compose_template_block b
JOIN mail_compose_template t ON t.id = b.template_id
SET b.custom_text = REPLACE(b.custom_text, 'Dear Professor,', 'Dear ${expertName|Professor},')
WHERE t.template_code = 'MATERIAL_REMINDER'
  AND b.block_type = 'CUSTOM_TEXT'
  AND b.custom_text LIKE '%Dear Professor,%';
```

上线前须在生产库执行以下核对，确认基线未被改动：

```sql
SELECT b.block_order, b.block_type, LEFT(b.custom_text, 40)
FROM mail_compose_template_block b
JOIN mail_compose_template t ON t.id = b.template_id
WHERE t.template_code = 'MATERIAL_REMINDER'
ORDER BY b.block_order;
```

### 阶段 C：发件人显示名

#### 任务 6：`From` 显示名（J-2）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt`

`:30` 的 `message.setFrom(account.senderEmail)` 替换为：

```kotlin
val displayName = account.senderDisplayName?.trim()?.takeIf { it.isNotEmpty() }
if (displayName != null) {
    message.setFrom(javax.mail.internet.InternetAddress(account.senderEmail, displayName, "UTF-8"))
} else {
    message.setFrom(account.senderEmail)
}
```

### 阶段 D：测试与文档

#### 任务 7：既有计划修正记录（J-1）

文件：`docs/plans/2026-06-20/unsubscribe-suppression-02-list-unsubscribe-oneclick.md`

追加 `## 修正记录` 一节：

> - 2026-08-06：Invariant L2-2 的"每封外发邮件"缩小为"除 `MATERIAL_REMINDER` 外的每封外发邮件"。依据：Google bulk sender 一键退订强制要求适用于日发 ≥5000 封的 marketing/promotional 邮件，本项目提醒邮件日上限 60 封（`BatchSendSettingService.kt:225`）且为一对一事务性跟进。见 `docs/plans/2026-08-06/material-reminder-02-headers-personalization.md` J-1。

（阶段 A 若被放弃，本任务同步取消。）

#### 任务 8：投递层测试（J-1, J-2）

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt`

保留既有 16 条不改语义，新增：

- `send omits List-Unsubscribe headers when listUnsubscribe is false`（token service **enabled** 前提下）
- `send keeps List-Unsubscribe headers by default when listUnsubscribe is not specified`
- `send uses display name in From when senderDisplayName is present`
- `send falls back to bare address when senderDisplayName is null`
- `send falls back to bare address when senderDisplayName is blank`（`"   "`）
- `send encodes non-ASCII display name`（断言 From 头为 RFC 2047 编码形态，非原始字节）

#### 任务 9：组装层测试（J-1, J-3）

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt`

保留既有 20 条不改语义，新增：

- `MATERIAL_REMINDER sets listUnsubscribe false`
- `non-reminder compose template keeps listUnsubscribe true`
- `expertName variable is injected from contact`
- `expertName falls back to empty when contact name is EMAIL prefixed`（`"EMAIL-abc123"` → 空串）
- `expertName falls back to empty when contact name is an orcid`（`"0000-0002-1825-0097"` → 空串）
- `expertName falls back to empty when contact name is null`
- `mailTemplateVariables does not contain expertFamilyName`（J-3 的禁止项）

---

## 变更文件清单

| # | 文件 | 类型 | 子系统 | 不变量 | 阶段 A 放弃时 |
|---|---|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt` | 修改（仅 `ComposedMail`） | 1 投递层 | J-1, J-6 | 移出清单 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt` | 修改 | 1 投递层 | J-1, J-2 | 保留（仅 J-2） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` | 修改 | 2 组装层 | J-1, J-3, J-4 | 保留（仅 J-3/J-4） |
| 4 | `src/main/resources/db/migration/V84__personalize_material_reminder_template.sql` | 新增 | 2 组装层 | J-3, J-5 | 保留 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt` | 修改 | 1 投递层 | J-1, J-2 | 保留 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt` | 修改 | 2 组装层 | J-1, J-3 | 保留 |
| 7 | `docs/plans/2026-06-20/unsubscribe-suppression-02-list-unsubscribe-oneclick.md` | 文档修正记录 | — | J-1 | 移出清单 |

**文件数：6 个代码/迁移 + 1 个文档 ≤ 10 ✓** ｜ **子系统数：2 ✓** ｜ **共享存储新增字段：0 ✓** ｜ **单存储新增数据字段：0（`V84` 只改既有行内容，不加列）✓**

---

## 验收标准（fix-v 机器验证）

- **J-1**：grep 确认 `ComposedMail.listUnsubscribe` 默认值为 `true`；grep 全部 8 个构造点，确认仅 `ManualExpertMailService.kt` 一处显式传值；grep `SmtpMailDeliveryService.kt` 确认**无任何** `mailType` / `"MATERIAL_REMINDER"` 字面量；单测 `omits ... when listUnsubscribe is false` 与 `keeps ... by default` 同时通过；`:49-52` 四行 git diff 为空。
- **J-2**：单测覆盖 null / 空白 / 正常 / 非 ASCII 四个 case；grep 确认非空判断用 `trim()` 后 `isNotEmpty()`，且编码经 `InternetAddress(addr, personal, "UTF-8")` 构造器而非手工拼接。
- **J-3**：grep `ManualExpertMailService.kt` 确认**无** `ExpertSearchService` / `MailVariableService` 构造器依赖、**无** `expertFamilyName` 字面量；单测 `EMAIL prefixed` / `orcid` / `null` 三条通过。
- **J-4**：git diff 确认 `MailComposeTemplateService.kt` 零改动；确认 `IntroductionMailComposer.compose()`、`AutoMailReplyService.mailTemplateVariables()`、`AutoReplyPreviewService.mailTemplateVariables()`、`MeetingInvitationMailComposer` 四处零改动。
- **J-5**：grep `V84` 确认两条 `UPDATE` 均含 `REPLACE(` 与 `LIKE '%Dear Professor,%'`，**无**整体 `SET body = '` / `SET custom_text = '` 覆写；确认同时覆盖 `mail_template` 与 `mail_compose_template_block` 两表。
- **J-6**：git diff 确认除 `ManualExpertMailService.kt` 外，其余 6 个含 `ComposedMail(` 的文件零改动。
- **回归**：`SmtpMailDeliveryServiceTest` 既有 16 条全绿；`ManualExpertMailServiceTest` 既有 20 条全绿；`mvn test` 全量通过。
- **第一批不回退**：git diff 确认第一批的线程头写入分支、`buildReplySubject` / `stripReplyPrefixes`、`messageId` 生成、`inReplyTo = composed.mail.inReplyTo` 四处**一行未改**。
- **IP-C 集成**：以 `expertName = "John Smith"` 的 contact 渲染 `MATERIAL_REMINDER` 模板，断言渲染结果首行为 `Dear John Smith,`；以 `expertName = null` 渲染，断言首行为 `Dear Professor,`。

---

## 人工验收清单

### A-1：提醒邮件不带一键退订按钮

- 前置条件：确认测试环境 `UNSUBSCRIBE_BASE_URL` 与 `UNSUBSCRIBE_SECRET` 两个环境变量**均已配置**（否则本项无意义 —— 可用后台发一封介绍邮件确认它带退订按钮来反向验证环境已配好）。
- 操作步骤：向测试 Gmail 发送一封 `Material Reminder Email`，在 Gmail 中打开，查看发件人名字右侧；再点右上角三点 →「显示原邮件」。
- 预期结果：发件人名字旁**没有**蓝色「退订」按钮；原邮件源码中**搜不到** `List-Unsubscribe` 与 `List-Unsubscribe-Post` 任何一行。
- 覆盖：需求描述第 1 条，J-1，IP-A

### A-2：介绍邮件仍带一键退订（回归）

- 前置条件：同 A-1 的环境变量前提。
- 操作步骤：向另一个测试 Gmail 地址发送一封 `INTRODUCTION`（批量跑一轮或手动单发），在 Gmail 中打开并「显示原邮件」。
- 预期结果：发件人名字旁**有**「退订」按钮；源码中**存在** `List-Unsubscribe: <https://.../u/unsubscribe?token=...>, <mailto:...?subject=unsubscribe>` 与 `List-Unsubscribe-Post: List=One-Click` 两行，格式与本批改动前**逐字一致**。点击退订按钮后，该邮箱进入后台抑制名单。
- 覆盖：must-NOT-change 第 1 条，J-1，IP-A

### A-3：会议邀请与 QA 自动回复仍带退订头（回归）

- 操作步骤：① 触发一封会议邀请；② 用测试 Gmail 给系统发一封命中 QA 规则的问询信，等自动回复。两封均「显示原邮件」。
- 预期结果：两封**均带** `List-Unsubscribe` 与 `List-Unsubscribe-Post`。QA 自动回复的正文段落格式完好，不塌成一堵墙。
- 覆盖：must-NOT-change 第 1 条

### A-4：发件人显示名

- 前置条件：在「发件账号」管理页确认账号 A 的「发件显示名」有值（如 `Li Lei`）；把账号 B 的显示名清空保存。
- 操作步骤：分别用两个账号各发一封邮件到测试 Gmail，各自「显示原邮件」查看 `From:` 行。
- 预期结果：账号 A —— Gmail 收件列表中发件人显示为 `Li Lei`（而非完整邮箱），`From:` 行为 `From: Li Lei <lilei@talents.szwebotech.cn>`。账号 B —— `From:` 行为裸邮箱形态 `From: <邮箱>`，与本批改动前完全一致。
- 覆盖：需求描述第 3 条，J-2，IP-B

### A-5：非 ASCII 显示名不乱码

- 前置条件：把某测试账号的「发件显示名」设为中文（如 `李雷`）。
- 操作步骤：用该账号发一封邮件到测试 Gmail，查看收件列表与「显示原邮件」的 `From:` 行。
- 预期结果：Gmail 收件列表中显示为 `李雷`（正常中文，非乱码非问号）；原邮件 `From:` 行为 RFC 2047 编码形态（形如 `=?UTF-8?B?...?= <邮箱>`），**不是**原始中文字节。
- 覆盖：J-2

### A-6：正文称呼个性化与回退

- 前置条件：准备两个联系人 —— ① `UPDATE expert_contact SET expert_name='John Smith' WHERE id=<x>;` ② `UPDATE expert_contact SET expert_name=NULL WHERE id=<y>;`
- 操作步骤：对两人分别发送 `Material Reminder Email`，查看收到的正文首行。
- 预期结果：① 首行为 `Dear John Smith,`；② 首行为 `Dear Professor,`。两封信的**其余所有段落逐字相同**，与 `V71` 正文一致（可逐段对照 `docs/plans/2026-08-06/` 或 `V71` 迁移文件）。
- 覆盖：需求描述第 4 条，J-3，IP-C

### A-7：技术标识不得渲染进称呼（回归）

- 前置条件：`UPDATE expert_contact SET expert_name='EMAIL-abc123' WHERE id=<z>;`；另构造一个 `expert_name` 等于其 ORCID 值（如 `0000-0002-1825-0097`）的联系人。
- 操作步骤：分别发送 `Material Reminder Email`，查看正文首行。
- 预期结果：两封均为 `Dear Professor,`。**不得**出现 `Dear EMAIL-abc123,` 或 `Dear 0000-0002-1825-0097,`。
- 覆盖：J-3

### A-8：迁移不覆盖运营的运行时改动

- 前置条件：在测试库先手动把 `MATERIAL_REMINDER` 的 `custom_text` 首行改成运营自定义文案（如 `Dear esteemed Professor,`）。
- 操作步骤：执行 `V84` 迁移（重启应用触发 Flyway）。
- 预期结果：该模板 `custom_text` 首行**仍为** `Dear esteemed Professor,`，未被覆盖。应用正常启动，Flyway 无报错。再用一个基线未被改动的环境重跑一次，确认那里正常改写成 `Dear ${expertName|Professor},`。
- 覆盖：J-5

### A-9：第一批线程化行为不回退（回归）

- 前置条件：复用第一批 A-1 的测试专家（有 INBOUND 记录）。
- 操作步骤：再发一封 `Material Reminder Email`（如被 `hasSentMaterialReminder` 去重则用手动单发入口），在 Gmail 中查看并「显示原邮件」。
- 预期结果：仍**折叠在原会话线程内**；`In-Reply-To` 头存在且只出现一次；`Message-ID` 仍为 `<reminder-...>` 格式、不含 `.JavaMail.`；主题为单个 `Re:` 前缀；后台该条 OUTBOUND 记录的「回复自」字段与 `In-Reply-To` 头一致。
- 覆盖：must-NOT-change 第 2 条

### A-10：其余邮件类型端到端回归

- 操作步骤：依次触发 ① `INTRODUCTION`；② QA 自动回复；③ 会议邀请；④ 人工富文本回复。
- 预期结果：四封均正常送达，主题正确，正文段落格式完好。`From` 显示名行为与 A-4 一致。源码中**均无** `In-Reply-To` / `References` 头。`Message-ID` 格式与本批改动前一致。
- 覆盖：must-NOT-change 第 5、6 条，IP-B

---

## 观察项（不产生任务）

1. **效果归因**：本批同时改了退订头、From 显示名、正文称呼三项，无法区分各自贡献。若需精确归因，可把阶段 A（退订头）与阶段 B/C 再分两次发布。
2. **其余 4 个缺 Message-ID 的构造点**：会议邀请、会议确认、QA 自动回复、人工富文本回复仍在用 JavaMail 默认 Message-ID（见 K-message-id-fingerprint 的 2026-08-06 修正表）。属既有缺陷，建议单独立项统一收口。
3. **发送节奏**：`DEFAULT_REMINDER_DAILY_CAP = 60` / `ROUND_SIZE = 30` / `perMailIntervalMs = 3000`（`BatchSendSettingService.kt:224-228`）为运行时配置，运营后台可直接调整，本计划不动。
4. **域名信誉**：若第一批的上线前置条件（`talents.szwebotech.cn` / `mail.szwebotech.cn` 的 SPF）仍未解决，本批同样会被抵消。
