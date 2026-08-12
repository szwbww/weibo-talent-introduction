# Plan 06 — 冷外联正文的退订链接改为 HTML 锚文本

> 主索引：[unsubscribe-link-and-page-master.md](unsubscribe-link-and-page-master.md)（共享证据 E-1 ~ E-6、E-9 不在本文重复）
> 生成日期：2026-08-12 · create-p
> 子系统：① 邮件组装与投递（Kotlin） ② 模板正文内容迁移（SQL）—— 共 2 个，符合上限

## 需求描述

**Observable outcome**

1. INTRODUCTION 与 MATERIAL_REMINDER 外发邮件的 **HTML 版正文**里，退订链接渲染为可点击锚文本 `Unsubscribe`，收件人看不到裸 URL。
2. 同一封邮件的 **text/plain 版正文**仍逐字包含完整退订 URL（文本客户端可复制）。
3. INTRODUCTION 从纯文本单部件升级为 `multipart/alternative`（MATERIAL_REMINDER 已是，形态不变）。

**What must NOT change**

1. `mail_record.body` 落库内容仍是**纯文本**（前端 `.pre` + `escapeHtml` 渲染依赖它，见主索引 E-4）。
2. INTRODUCTION 的 subject、`Message-ID`（`<intro-{orcid}-{uuid}@{domain}>`）、无 `In-Reply-To`/`References` 的形态不变。
3. MATERIAL_REMINDER 的线程头（`inReplyTo` / `references`）、`Re:` 主题改写、`allowSuppressedRecipient` 语义不变。
4. `List-Unsubscribe` / `List-Unsubscribe-Post` 两个头的产出条件与值不变（`SmtpMailDeliveryService.kt:64-69`）。
5. 个性化门禁 `PersonalizationGateService.evaluate()` 与 `requireNoPlaceholderResidue()` 的输入仍是**纯文本**，不得改喂 HTML。
6. `unsubscribeUrl` 为空串时（未配置 / 无收件邮箱，主索引 E-1）外发正文不得出现空锚 `<a href="">`。

**Out of scope**

- token 的形态与长度 → Plan 07。
- 退订页样式 → Plan 08。
- 会议邮件族（MEETING_INVITATION / MEETING_CONFIRMATION）的 `unsubscribeUrl` 注入 → 前序 Plan 04，状态不变。
- `List-Unsubscribe` 头收窄到冷外联 → 前序 Plan 03，状态不变。
- QA 自动回复 / 人工富文本 / AI 草稿的正文形态 —— 它们已是 HTML（`AutoMailReplyService.kt:571`、`PendingMailOperationService.kt:271`、`ManualExpertMailService.kt:250`），本计划不碰。
- 退订行的中文/多语言版本。

## 关键不变量

### Invariant I-1: 纯文本是唯一内容源
- Rule：一封邮件的 text/plain 与 text/html 两个部件必须由**同一份渲染后纯文本**派生。`ComposedMail.text` 恒为该纯文本；`ComposedMail.body`（`html = true` 时）必须是对它调用 `MailContentService.plainTextToHtml(...)` 的产物。禁止为 HTML 版单独维护一份文案。
- Applies to：`IntroductionMailComposer.compose()`（`IntroductionMailComposer.kt:36-41`）、`ManualExpertMailService.composeComposeTemplate()`（`ManualExpertMailService.kt:243`（转换）与 `:246-256`（`ComposedMail` 块），现状已合规）。
- Violation consequence：两份文案漂移，运营在后台改模板只影响一侧；纯文本客户端与 HTML 客户端看到不同内容。
- 来源：K-compose-template-html-after-render

### Invariant I-2: `mail_record.body` 只持久化纯文本
- Rule：所有把外发正文写入 `mail_record` 的调用点，`body` 参数必须传 `mail.text ?: mail.body`，不得传 `mail.body`。
- Applies to：主索引 E-4 表格的 7 个落库点 —— `InitialOutreachService.kt:95`、`:105`；`ManualInitialOutreachService.kt:695`、`:709`、`:723`、`:747`、`:763`。
- Violation consequence：收发件箱与专家详情页显示 `<p>`/`<br>` 源码（正文展示点用 `<div class="pre">${escapeHtml(...)}</div>` 渲染，实测分散在 `app.js:3098`、`:5827`、`:9126` 等多处）；审计 `bodyPreviewText` 被 HTML 污染。
- 来源：K-plaintext-reply-client-reflow

### Invariant I-3: 锚文本替换发生在转义之后、且只匹配精确 URL
- Rule：URL → `<a>` 的替换必须在 `escapeHtml()` 之后、`<p>` 包装之内进行，且只替换与传入 URL **逐字相等**的子串。禁止用正则泛匹配任意 `https?://`。
- Applies to：`MailContentService.plainTextToHtml(plain, linkedUrls)` 新重载。
- 依据：退订 URL 的字符集（`https://` + 主机 + 路径 + `?token=` + `A-Za-z0-9-_.`）与 `escapeHtml()` 处理的 5 个字符（`& < > " '`）不相交（主索引 E-3），故转义后 URL 逐字不变，精确串替换成立。
- Violation consequence：先替换后转义会把 `<a href=...>` 自身转义成字面量；泛正则会把正文里的其他 URL（如模板中的官网链接）一并锚化，属未声明的行为变更。
- 来源：original

### Invariant I-4: 空 URL 不产生锚
- Rule：`linkedUrls` 中任何 `isBlank()` 的项必须被跳过，不得生成 `<a href="">`；`linkedUrls` 为空集合时 `plainTextToHtml` 的输出必须与旧单参重载**逐字相同**。
- Applies to：`MailContentService.plainTextToHtml(plain, linkedUrls)`；`IntroductionMailComposer.compose()` 传入 `variables["unsubscribeUrl"]`（可能为 `""`，见主索引 E-1）。
- Violation consequence：未配置 `UNSUBSCRIBE_BASE_URL` 的环境（`application.yml:87` 默认空）外发带空锚的邮件，是垃圾邮件特征。
- 来源：original

### Invariant I-5: 门禁与占位符残留检查仍吃纯文本
- Rule：`personalizationGateService.evaluate(rendered.rawTexts, ...)` 与 `requireNoPlaceholderResidue(subject, body)` 的 `body` 实参必须是纯文本，不得改成 HTML 串。
- Applies to：`IntroductionMailComposer.kt:28`、`:42`。
- Violation consequence：`requireNoPlaceholderResidue` 检测 `${...}` 残留，HTML 化不改变 `$` `{` `}`，功能上仍可工作，但一旦后续引入实体转义就会静默失效；且 `evaluate` 的 `rawTexts` 本就是模板原文，混入 HTML 属语义错误。
- 来源：original

### Invariant I-6: 迁移只改文案不改结构，且不覆盖运营编辑
- Rule：`V88` 只能用 `REPLACE(b.custom_text, '<V87 原句>', '<新句>')` 形式，且 `WHERE` 必须带 `custom_text LIKE '%<V87 原句>%'` 守卫；禁止整块 `SET custom_text = '...'`；禁止写 `mail_template` 表。
- Applies to：`V88__rewrite_unsubscribe_line_wording.sql`。
- Violation consequence：整块覆盖会抹掉运营在后台编辑器里的历史修改；写 `mail_template` 会产生两份漂移正文。
- 来源：K-qa-rule-runtime-vs-migration-writes、K-mail-template-table-dead

## 现状审计

> Step 1b-fe **未触发**：本计划变更文件清单中无 `src/main/resources/static` 下文件，也无 `.html` / `.css` / 前端 `.js`。故本计划无 `## 样式契约` 节。

### `mail_compose_template_block`（正文 SSOT）

- Schema：`V61__create_mail_compose_template.sql` 建表；正文列 `custom_text`，块类型列 `block_type`，`block_order` 排序。INTRODUCTION / MATERIAL_REMINDER 当前均为单 `CUSTOM_TEXT` 块（K-mail-template-table-dead）。
- Write paths：
  1. `V62__unify_mail_templates.sql:38-72` — 从 `mail_template.body` 搬迁（历史）。
  2. `V87__append_unsubscribe_line_to_cold_outreach_templates.sql:7-16` — CONCAT 追加退订行，带 `NOT LIKE '%unsubscribeUrl%'` 幂等守卫。
  3. 后台模板编辑器（`/api/mail/compose-templates`，运行时）。
  4. **新增** `V88`（本计划）。
- Read paths：
  1. `MailComposeTemplateService.resolveBlocks()`（函数定义 `:425`，三个分支 `:440` QA_RULE / `:480` REPLY_SNIPPET / `:526` CUSTOM_TEXT）→ `renderText()`（`:588-597`，其中 `:589` 先由 `FALLBACK_PLACEHOLDER_REGEX`（`:600`）处理 `${key|fallback}` 形态）。对外入口 `renderByCode()` / `render()`。

> 更正记录：本节初稿的 `:248/:279/:294` 继承自知识条目 K-mail-template-table-dead，本轮 grep 实测已漂移，上方为实测值。
- Interaction points：写路径 4（V88）× 读路径 1 —— V88 改后的文案经 `renderText()` 出来的纯文本，正是 I-1 的唯一内容源。若 V88 的 `REPLACE` 源串与 V87 追加的串不逐字一致，迁移**静默不生效**（0 行受影响，Flyway 不报错），退订行文案保持旧样，锚文本仍能工作但句子读作 "you can unsubscribe here: Unsubscribe"。故必须有文本断言比对两个迁移文件的字符串（见验收 I-6）。

**V87 追加的原句（逐字，`V87__...sql:11`）：**

```
\n\n---\nIf you would prefer not to receive further emails from us, you can unsubscribe here: ${unsubscribeUrl}
```

### `mail_record`（外发正文归档）

- Schema：`body` 为正文列（长文本）。
- Write paths（限 INTRODUCTION 相关，主索引 E-4 已列全）：`InitialOutreachService.kt:95`、`:105`；`ManualInitialOutreachService.kt:695`、`:709`、`:723`、`:747`、`:763`。均经 `txHelper.recordSuccess/recordFailure(body = ...)`。
- Read paths：
  1. 前端收发件箱 / 专家详情 —— `.pre` 类（实测 `styles.css:1694`，`white-space: pre-wrap`）+ `escapeHtml`（实测 `app.js:3098` / `:5827` / `:9126` 等多处）。
  2. 审计 `bodyPreviewText`。
- Interaction points：`IntroductionMailComposer.compose()` 改 `body` 为 HTML × 上述 7 个写点 × 前端 `.pre` 读点。**这是本计划唯一的跨模块交互点**，由 I-2 覆盖。

> 前端读点的行号说明：`grep -n 'class="pre"' src/main/resources/static/app.js` 本轮实测命中 `:3098`、`:5827`、`:9126` 等**多处**（K-mail-body-display-sites 记载该展示点分散在 app.js 多处，本轮复核成立）。旧知识条目里的 `app.js:5022` 单点引用**已过期**，不要据此定位。CSS 规则在 `styles.css:1694`（`.pre { white-space: pre-wrap }`，旧记的 `:1506` 已因文件增长漂移）。

### `ComposedMail`（进程内传输对象，非持久化）

- 定义：`IntroductionMailComposer.kt:69-80`。字段 `html: Boolean = false`、`text: String? = null`。
- 消费方（唯一）：`SmtpMailDeliveryService.send()`，`:49-62` —— `html = true` 时构造 `MimeMultipart("alternative")`，plain 部件取 `mail.text` 非空值，否则回退 `mailContentService.htmlToPlainText(mail.body)`。
- Interaction point：本计划给 INTRODUCTION 显式传 `text`，因此**不会**走 `htmlToPlainText` 回退。必须断言这一点 —— 回退路径会把 `<a>` 拆成纯文本并丢掉 URL（`htmlToPlainText` 的 `.replace(Regex("<[^>]+>"), "")`，`MailContentService.kt:21`，只删标签不保留 href）。

### `MailContentService`

- `plainTextToHtml(plain: String)`：`:7-15`，见主索引 E-3。
- 现有生产调用点（`grep -rn "plainTextToHtml" src/main/kotlin`，本轮实测**3 处**）：

| # | 位置 | 场景 | 本计划处置 |
|---|---|---|---|
| 1 | `ManualExpertMailService.kt:243` | COMPOSE_TEMPLATE（含 MATERIAL_REMINDER）外发 | **改传两参重载**（T-6） |
| 2 | `AutoMailReplyService.kt:570` | QA / AI 自动回复外发 | **不改**（保持一参重载，行为逐字不变） |
| 3 | `PendingMailOperationService.kt:204` | 待办邮件人工操作路径 | **不改**（同上） |

> 更正记录：本节初稿写作"调用点唯一"，属未 grep 的臆断，现已按实测更正为 3 处。
>
> 2 和 3 不改是**刻意的范围决定**，不是遗漏：这两条路径的变量注入经 `MailVariableService.renderContact()`，按 K-unsubscribe-variable-injection-sites 它们**确实拿得到** `unsubscribeUrl`，因此若运营在某条 QA 规则正文里写了 `${unsubscribeUrl}`，其回复邮件的 HTML 版里仍会是裸 URL。当前 QA 规则种子数据无此写法，本计划不扩范围处理；若将来需要，改法就是这两处也改传两参重载（新增重载已为此预留，无需再动 `MailContentService`）。

因此新增重载对 2、3 零影响（它们继续走一参委托版，由 I-4 保证行为逐字等价）。
- 构造：无参 `@Service`（测试里 `MailContentService()`，`MailContentServiceTest.kt:8`）。

## 实现方案

### 阶段 1 — 转换器（I-3、I-4）

**T-1** 在 `MailContentService.kt` 新增重载，**保留**原单参方法（`ManualExpertMailService` 之外无调用方，但保留可让旧测试不变）：

```kotlin
fun plainTextToHtml(plain: String, linkedUrls: Collection<String>): String {
    if (plain.isBlank()) return ""
    val targets = linkedUrls.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
    return plain.split(Regex("\\n\\s*\\n"))
        .map { paragraph ->
            var inner = escapeHtml(paragraph.trim()).replace("\n", "<br>")
            targets.forEach { url ->
                inner = inner.replace(url, "<a href=\"$url\">$UNSUBSCRIBE_ANCHOR_TEXT</a>")
            }
            "<p>$inner</p>"
        }
        .joinToString("")
}

fun plainTextToHtml(plain: String): String = plainTextToHtml(plain, emptyList())
```

并在 `companion object` 声明 `const val UNSUBSCRIBE_ANCHOR_TEXT = "Unsubscribe"`。

要点：
- `sortedByDescending { it.length }` 防止短 URL 是长 URL 前缀时先被替换掉（当前只有一个 URL，属防御性写法）。
- `escapeHtml` 在替换**之前**执行 —— 满足 I-3。
- `filter { it.isNotBlank() }` 满足 I-4 前半；`emptyList()` 委托满足 I-4 后半（行为逐字等价）。

### 阶段 2 — INTRODUCTION 升级为 multipart（I-1、I-4、I-5）

**T-2** `IntroductionMailComposer.kt`：构造函数尾部追加带默认值的依赖，避免打爆唯一的测试构造点（`IntroductionMailComposerTest.kt:22` 是三参调用）：

```kotlin
private val mailContentService: MailContentService = MailContentService()
```

**T-3** `IntroductionMailComposer.compose()` 改 `ComposedMail` 构造（`:36-41`）：

```kotlin
val plain = rendered.body
val mail = ComposedMail(
    to = expert.email ?: error("Expert email is required for introduction mail"),
    subject = rendered.subject,
    body = mailContentService.plainTextToHtml(plain, listOfNotNull(variables["unsubscribeUrl"])),
    html = true,
    text = plain,
    messageId = messageId
)
personalizationGateService.requireNoPlaceholderResidue(mail.subject, plain)
```

要点：
- `:42` 的 `requireNoPlaceholderResidue` 第二个实参由 `mail.body` 改为 `plain` —— 满足 I-5。
- `:28` 的 `personalizationGateService.evaluate(rendered.rawTexts, ...)` **不动**。
- `variables["unsubscribeUrl"]` 可能是 `""`（主索引 E-1），由 T-1 的 `filter` 兜住 —— 满足 I-4。

### 阶段 3 — 落库口径收敛（I-2）

**T-4** `InitialOutreachService.kt:95`、`:105`：`body = mail.body` → `body = mail.text ?: mail.body`。

**T-5** `ManualInitialOutreachService.kt:695`、`:709`、`:723`、`:747`、`:763`：同上，五处逐一改。

> 不做"抽一个 helper"的重构：五处分属不同 `errorCategory` 分支，抽取会扩大 diff 且无收益。逐处改并由测试逐处断言。

### 阶段 4 — MATERIAL_REMINDER 锚化（I-1、I-3）

**T-6** `ManualExpertMailService.kt:243`（该行现为 `val html = mailContentService.plainTextToHtml(rendered.body)`，其上 `:240-242` 是三行说明注释，不要误改注释）：

```kotlin
val html = mailContentService.plainTextToHtml(rendered.body, listOfNotNull(variables["unsubscribeUrl"]))
```

`variables` 已在同方法作用域内（声明于 `:197`）。`ComposedMail` 块（`:246-256`）的其余字段一律不动 —— 满足 must-NOT-change 第 3 条。

### 阶段 5 — 文案迁移（I-6）

**T-7** 新建 `src/main/resources/db/migration/V88__rewrite_unsubscribe_line_wording.sql`：

```sql
-- V88: 冷外联退订行文案改写，使 HTML 锚文本版读起来自然。
-- V87 追加的原句以 URL 收尾，HTML 版把 URL 换成锚文本 "Unsubscribe" 后
-- 会读作 "you can unsubscribe here: Unsubscribe"（语义重复）。
-- 只做定点 REPLACE，不整块覆盖，保护运营在后台编辑器里的历史修改（I-6）。
-- LIKE 守卫保证幂等：已改写或运营已自行改写的块跳过。
-- 不写 mail_template：该表已无代码读取方（K-mail-template-table-dead）。

UPDATE mail_compose_template_block b
JOIN mail_compose_template t ON t.id = b.template_id
SET b.custom_text = REPLACE(
        b.custom_text,
        'you can unsubscribe here: ${unsubscribeUrl}',
        'please use this link: ${unsubscribeUrl}'
    )
WHERE t.template_code IN ('INTRODUCTION', 'MATERIAL_REMINDER')
  AND b.block_type = 'CUSTOM_TEXT'
  AND b.custom_text IS NOT NULL
  AND b.custom_text LIKE '%you can unsubscribe here: ${unsubscribeUrl}%';
```

改写后两个版本分别读作：

- text/plain：`If you would prefer not to receive further emails from us, please use this link: https://…`
- text/html：`If you would prefer not to receive further emails from us, please use this link: `**Unsubscribe**（超链接）

> 该文案是运营内容决策。若需求方要换措辞，只需同步改 T-7 的 SQL 与 T-10 的断言字符串，代码零改动。

### 阶段 6 — 测试

**T-8** `MailContentServiceTest.kt` 新增用例：
- 单个 URL 被替换为 `<a href="URL">Unsubscribe</a>`，且 `<a` 未被转义。
- 传空集合时输出与单参重载**逐字相等**（I-4 后半）。
- URL 为 `""` 时不产生 `href=""`（I-4 前半）。
- 正文含 `&`/`<` 时仍被转义，同时 URL 仍被锚化（I-3 顺序）。
- 正文里的**非目标 URL**（如 `https://www.qingfeitalent.com`）保持纯文本，未被锚化（I-3 精确匹配）。

**T-9** `IntroductionMailComposerTest.kt` 新增用例：
- `compose()` 返回 `html == true`、`text == rendered.body`（纯文本）、`body` 含 `<p>`。
- `unsubscribeUrl` 为空（该测试类的 `MailVariableService` 未注入 `UnsubscribeTokenService`，`:21`，故天然为空）时 `body` 不含 `href=""`。
- 注入了 token service 的装配下，`body` 含 `<a href="https://example.com/u/unsubscribe?token=` 且 `text` 含完整 URL。
- `messageId` 仍匹配 `^<intro-.*@.*>$`，`inReplyTo`/`references` 仍为 null（must-NOT-change 第 2 条）。

**T-10** 新建 `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeWordingMigrationTest.kt`（沿用主索引 E-9 的文本断言范式）：
- 读 `V87` 与 `V88` 两个文件，断言 `V88` 的 REPLACE 源串**是** `V87` 中出现过的子串（防止 REPLACE 静默失效）。
- 断言 `V88` 含 `REPLACE(` 且不含 `SET b.custom_text = '`。
- 断言 `V88` 含 `LIKE '%you can unsubscribe here:` 幂等守卫。
- 断言 `V88` 去注释后不含 `mail_template`（沿用 `UnsubscribeBodyLinkMigrationTest.kt:39-44` 的过滤写法）。

**T-11**（fast-p 修正 A1，2026-08-12 人工批准）改 `ManualExpertMailServiceGateTest.kt` 的 `:219` 断言：
- 现有断言 `captor.value.body!!.startsWith("<p>Unsubscribe: https://example.com/u/unsubscribe?token=")` 断言 HTML 版正文以裸 URL 开头，与 T-6 的锚化行为直接冲突（T-6 落地后 HTML 版以 `<p>Unsubscribe: <a href="…">Unsubscribe</a>` 开头），而本计划「验证命令」要求该测试类保持通过。
- 修正为：`assertTrue(captor.value.body!!.startsWith("<p>Unsubscribe: <a href=\"https://example.com/u/unsubscribe?token="))`。
- 该测试类内其余断言（text 纯文本、实体转义、I-1 门禁）逐字保留。

## 变更文件清单

| # | 文件 | 类型 | 改动 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailContentService.kt` | 主代码 | 新增两参 `plainTextToHtml` 重载 + `UNSUBSCRIBE_ANCHOR_TEXT` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt` | 主代码 | 注入 `MailContentService`（带默认值）；`ComposedMail` 改 html/text；`requireNoPlaceholderResidue` 改喂纯文本 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | 主代码 | `:95`、`:105` 两处 `body =` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 主代码 | `:695`、`:709`、`:723`、`:747`、`:763` 五处 `body =` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` | 主代码 | `:241` 改传两参重载 |
| 6 | `src/main/resources/db/migration/V88__rewrite_unsubscribe_line_wording.sql` | 新建迁移 | 定点 REPLACE 文案 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailContentServiceTest.kt` | 测试 | T-8 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposerTest.kt` | 测试 | T-9 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeWordingMigrationTest.kt` | 新建测试 | T-10 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt` | 测试 | T-11（fast-p 修正 A1）：`:219` 断言改为锚文本前缀 |

合计 10 个文件 ≤ 10 ✅；子系统 2 个 ✅；无新增共享存储字段 ✅。

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。以下命令可原样复制到终端执行。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailContentServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=IntroductionMailComposerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribeWordingMigrationTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribeBodyLinkMigrationTest
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
来源：`CLAUDE.md` 的「Commands」章节与项目元信息 `test_command` / `build_command`；`-DmigrationIt=true` 来自同章节的 Flyway 集成测试说明。

## 验收标准

- **I-1**：`grep -n "ComposedMail(" src/main/kotlin/.../IntroductionMailComposer.kt` 后人工核对 —— `text` 实参与 `plainTextToHtml` 的第一实参是**同一个局部变量** `plain`。T-9 断言 `mail.text == renderResult.body`。
- **I-2**：`grep -rn "body = mail.body" src/main/kotlin` 结果必须为 **0 行**；`grep -rn "body = mail.text ?: mail.body" src/main/kotlin` 必须为 **7 行**，且行号落在清单文件 3、4 内。
- **I-3**：T-8 的转义顺序用例与非目标 URL 用例通过；`grep -n "Regex(\"https\\?://\")" src/main/kotlin/.../MailContentService.kt` 必须为 0 行（禁止泛正则）。
- **I-4**：T-8 的空集合等价用例与空串用例通过；`grep -rn 'href=""' src/main/kotlin` 为 0 行。
- **I-5**：`IntroductionMailComposer.kt` 中 `requireNoPlaceholderResidue(` 的第二实参 grep 为 `plain`，不含 `mail.body`。
- **I-6**：T-10 全部用例通过（含"V88 的 REPLACE 源串是 V87 的子串"这条）。
- **跨路径集成**：`SmtpMailDeliveryServiceTest` 现有 3 个 header 用例保持通过，证明 must-NOT-change 第 4 条未破 —— 本轮实测行号为 `:133`（`send adds List-Unsubscribe headers when token service enabled`）、`:159`（`list unsubscribe post header value is exactly RFC 8058 postarg`）、`:183`（`send omits List-Unsubscribe headers when token service disabled`）。
- **multipart 行为已有既存覆盖，不新增用例**：`SmtpMailDeliveryServiceTest` 已有 `:205`（`send uses plain string content for non-html mail`）、`:226`（`send uses multipart alternative for html mail`）、`:252`（`send uses explicit text part when provided for html mail`）三条。其中 `:252` 已经断言"给了 `text` 就用 `text`、不走 `htmlToPlainText` 回退"，即本计划需要的那条断言**已经存在**，执行时**不要重复新增**；只需确认它仍通过。

> 更正记录：本节初稿把 header 用例行号写成 `:126/:152/:176`（继承自 2026-08-11 的前序计划文档，未复核），且提出"新增一条 plain 部件断言"—— 本轮 grep 实测证明行号错误、且该断言已存在于 `:252`。两处均已更正。
- **回归**：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: INTRODUCTION 在 Gmail 里显示为锚文本
- 前置条件：`UNSUBSCRIBE_BASE_URL` / `UNSUBSCRIBE_SECRET` 已注入；ES CANDIDATE 层存在 ≥1 条带有效 Gmail 邮箱的专家；至少一个 enabled 发件账号。
- 操作步骤：
  1. 后台触发一次介绍邮件外发（专家详情页手动单发，或跑一次初次外联批次）。
  2. 用收件 Gmail 账号打开该邮件。
- 预期结果：正文最后一行为 `If you would prefer not to receive further emails from us, please use this link: Unsubscribe`，其中 `Unsubscribe` 是蓝色下划线超链接；**正文中看不到任何以 `https://` 开头的完整 URL 文本**。
- 覆盖：需求描述 observable 1、3

### A-2: 纯文本版仍含完整 URL
- 前置条件：同 A-1，已收到邮件。
- 操作步骤：Gmail 中打开该邮件 → 右上角三点菜单 → 「显示原始邮件」→ 在原始内容里定位 `Content-Type: text/plain` 部件。
- 预期结果：`text/plain` 部件里退订行逐字为 `... please use this link: https://<base-url>/u/unsubscribe?token=<token>`（完整 URL）；同一封邮件另有 `Content-Type: text/html` 部件，其中该 URL 出现在 `href="` 内、可见文字为 `Unsubscribe`；顶层 `Content-Type` 为 `multipart/alternative`。
- 覆盖：需求描述 observable 2、3；I-1

### A-3: 后台正文归档仍是纯文本（回归）
- 前置条件：A-1 已完成，该封邮件已入 `mail_record`。
- 操作步骤：后台 →「收发件箱」找到该封 OUTBOUND 记录 → 展开正文；再打开对应专家详情页的邮件往来。
- 预期结果：两处显示的正文都是可读的英文段落，**不含** `<p>`、`<br>`、`<a href=` 等标签字面量；退订行显示为完整 URL 文本。
- 覆盖：must-NOT-change 1；I-2；现状审计中 `mail_record` 的写×读交互点

### A-4: MATERIAL_REMINDER 同样锚化且线程未断（回归）
- 前置条件：存在一条 APPLICATION 层、带「承诺回复材料」标签、且有历史 INBOUND 记录的联系人。
- 操作步骤：专家详情页 → 手动单发 → 选择 MATERIAL_REMINDER 模板 → 发送 → 在收件方邮箱查看。
- 预期结果：① 退订行显示为 `Unsubscribe` 锚文本；② 该邮件在收件方邮箱里**归入原有会话线程**（与之前那封回信同一 thread）；③ 主题以 `Re: ` 开头。
- 覆盖：需求描述 observable 1；must-NOT-change 3

### A-5: 未配置退订地址时不出现空链接（回归）
- 前置条件：一套 `UNSUBSCRIBE_BASE_URL` 为空的环境（或临时置空后重启）。
- 操作步骤：发一封 INTRODUCTION → 查看收件方「显示原始邮件」。
- 预期结果：`text/html` 部件里**没有** `<a href="">` 或 `<a href=">`；退订行呈现为 `... please use this link:` 后无内容（空串替换结果），且邮件正常送达无异常。
- 覆盖：must-NOT-change 6；I-4

### A-6: 退订链接仍然可用（端到端回归）
- 前置条件：A-1 收到的邮件。
- 操作步骤：在邮件里点击 `Unsubscribe` 锚文本 → 在打开的页面点确认按钮 → 回到后台「退订名单」页面搜索该邮箱。
- 预期结果：页面提示已退订；后台退订名单出现该邮箱，来源为 `ONE_CLICK`。
- 覆盖：需求描述 observable 1（链接 href 正确性）

### A-7: 模板文案迁移已生效且未覆盖运营改动
- 前置条件：迁移已在目标库执行。
- 操作步骤：后台 →「邮件模板」→ 打开 INTRODUCTION 与 MATERIAL_REMINDER 的正文编辑器。
- 预期结果：两个模板正文末尾均含 `please use this link: ${unsubscribeUrl}`，且**不含** `you can unsubscribe here:`；正文其余段落与迁移前逐字一致（迁移前后各截一次图对比）。
- 覆盖：I-6；现状审计中 `mail_compose_template_block` 的写×读交互点
