# QA 规则回复正文段落格式

> 计划产出：create-p ｜ 日期：2026-06-27 ｜ 状态：待执行

## 需求描述

**可观察结果**：QA 规则自动/人工回复邮件，到达专家邮箱（Gmail / Outlook 网页版等会重排纯文本的客户端）时保留段落结构——段落之间有空行、各 section 标题独立成行，不再塌成一整段；同时现有 QA 规则中的长正文（尤其 `Program overview` 总览规则）本身带上段落换行。

**不能改变的行为**：
- QA 多规则组装的**顺序契约**（K-composed-reply-order-contract）：UI 预览、payload、后端外发正文、`mail_record_qa_rule.ordinal` 必须仍用同一顺序。HTML 化只是对**已组装好**的正文做包裹，禁止重排 section。
- `mail_record.body` 持久化与前端 `.pre`（`white-space: pre-wrap`）展示：列表/详情仍展示**纯文本**正文。
- 介绍信、会议邀请、人工自由 HTML 回复（`PendingMailOperationService` 既有 `html=true` 路径，:176-188）等非 QA 链路不变。
- QA 规则的 `keywords / match_mode / priority / reply_subject / category_id / section_title / supersedes_children` 等匹配字段全部不变。

**超出范围（显式延后）**：
- 介绍信 / 会议邀请模板的 HTML 化（同样是纯文本，但不是本次诉求，独立计划）。
- LLM 润色草稿（`LlmStitchService`）的输出格式——它只生成运营在 textarea 里编辑的纯文本草稿，不直接发送，保持纯文本。
- 富文本编辑器 / 所见即所得编辑能力。

## 关键不变量

### Invariant I-1: QA 回复线路格式 = multipart alternative（纯文本 + HTML）
- Rule：所有 QA 规则派生的外发回复，发送时 `ComposedMail.html=true`，`body` 为 HTML，`text` 为对应纯文本；HTML 由 `MailContentService.plainTextToHtml(纯文本)` 生成，绝不手写拼接。
- Applies to：(1) `AutoMailReplyService` QA 自动回复 :469；(2) `PendingMailOperationService.sendQaReply` 单规则 :102；(3) `PendingMailOperationService.sendManualComposedReply` 组装回复 :291。
- Violation consequence：漏改任一发送点 → 该路径仍发纯文本，客户端重排后段落丢失，问题复现。
- 来源：original

### Invariant I-2: `mail_record.body` 持久化纯文本，永不存 HTML
- Rule：三个发送点写 `MailRecord.body` 时存**纯文本正文**（HTML 化前的原文），不存 `plainTextToHtml` 的输出。
- Applies to：`AutoMailReplyService` :488（`body = reply.body`）、`PendingMailOperationService` :102 路径的 MailRecord 保存、:310（`body = finalBody`）。
- Violation consequence：前端 `.pre` 用 `escapeHtml` 渲染（app.js:5022），若存 HTML 会显示成转义后的标签串；运营审计 `bodyPreviewText`（:348）也会被污染。
- 来源：original（前端审计：app.js:5019-5022 / 4076 / 4083，CSS `.pre` white-space:pre-wrap styles.css:1506-1519）

### Invariant I-3: `plainTextToHtml` 必须转义 + 段落映射
- Rule：先对输入做 HTML 转义（`& < > " '`），再做映射：连续空行（`\n\s*\n`）切分为 `<p>…</p>`，段内单个 `\n` 转 `<br>`；输出为最小内联结构（如多个 `<p>`，可包一层容器），不引入外链样式/脚本。
- Applies to：新增的 `MailContentService.plainTextToHtml`。
- Violation consequence：不转义 → 规则正文若含 `<`/`&` 造成 HTML 注入或显示错乱；不做段落映射 → HTML 里仍是一行，等于没修。
- 来源：original

### Invariant I-4: 现有正文段落补齐只改 `reply_body`，且走新迁移
- Rule：给现有 QA 规则补段落换行 = 新增 `V46__*.sql`，仅 `UPDATE qa_rule SET reply_body=...`，在主题/语义边界插入 `\n\n`；字面量保持 ASCII-only（沿用 V45 约定，避免客户端编码相关标点）；不得编辑任何已应用迁移；不得改动 `reply_body` 以外的列。
- Applies to：`src/main/resources/db/migration/V46__qa_reply_body_paragraphs.sql`。
- Violation consequence：改了匹配字段 → 破坏命中/顺序；编辑旧迁移 → Flyway 校验失败。
- 来源：original（DB 迁移规则见 CLAUDE.md）

### Invariant I-5: HTML 化不改组装顺序
- Rule：`plainTextToHtml` 只是对 composer 已输出的 `replyBody`（含 greeting / section / closing 顺序）做整体包裹，禁止在转换里重新排序、增删 section 或合并规则。
- Applies to：三个发送点的调用方式——先取既有纯文本，再整体转换。
- Violation consequence：违反 K-composed-reply-order-contract，运营调整的顺序在正文里失效。
- 来源：K-composed-reply-order-contract

## 现状审计

### MySQL `qa_rule` 表（reply_body 数据源）
- Schema：`reply_body TEXT NOT NULL`（V1:56），另有 `section_title`（V40）、`supersedes_children`（V41）。
- 当前规则正文来源迁移：V3（原 12 条，单段连续文字）、V38（新增 11 条 FAQ，多为 1-3 句）、V41（`Program overview` 总览规则，`supersedes_children=1`，**最长、多主题、典型一堵墙**，V45 又修过编码）、V45（修 `Confirmation video` / `Meeting arrangement` / `Program overview` 编码）。
- 现状：所有 `reply_body` 均为**无换行的单段**。
- 写路径（迁移外无运行时直接改 reply_body 内容，仅运营经 `QaRuleManagementService` 增改，保留原始换行不归一）：
  1. `QaRuleManagementService.create/update`（:59-77）——透传 `command.replyBody`，不 trim 正文、不去换行。
- 读路径：
  1. `QaReplyComposer.compose / composeInOperatorOrder / formatSection`（用 `\n\n` 拼 section，`section_title\n正文`）。
  2. `LlmStitchService.composeDeterministic / buildRuleSegments`（拼草稿，纯文本，仅预览不发送）。
- 交互点：迁移补 `\n\n` 后，`compose` 的 `\n\n` join 与正文内 `\n\n` 叠加 → 由 `plainTextToHtml` 统一映射为段落；需保证 composer 仍负责 section 间分隔，迁移只管**单条正文内部**分段。

### 发送链路 `ComposedMail` → SMTP
- `ComposedMail(to, subject, body, html=false, text=null, messageId=null)`（IntroductionMailComposer.kt:33-40）。
- `SmtpMailDeliveryService.send`（:33-46）：`html=true` 走 multipart/alternative，plain part 取 `mail.text` 非空否则 `htmlToPlainText(body)`，html part 为 `body`；`html=false` 走 `setText(body)` 纯文本。**HTML 分支已存在且有测试**（SmtpMailDeliveryServiceTest:195-216），本次不改该文件。
- QA 三个发送点（均 `html=false` 纯文本）：
  1. `AutoMailReplyService` :469-473 建 `ComposedMail(body=match.replyBody)`，:474 发送，:488 存 `MailRecord.body=reply.body`。
  2. `PendingMailOperationService.sendQaReply` :102-106 建 `ComposedMail(body=rule.replyBody)`，单规则。
  3. `PendingMailOperationService.sendManualComposedReply` :291-295 建 `ComposedMail(body=finalBody)`（finalBody = override 或 composer+freeText），:310 存 `MailRecord.body=finalBody`。
- 既有 HTML 发送先例：`PendingMailOperationService` :176-188（运营自填 HTML 回复），说明 multipart 路径线上可用。
- 依赖注入：`AutoMailReplyService`、`PendingMailOperationService` 构造器**均未注入** `MailContentService`，需新增。
- 读路径（展示/审计）：app.js `renderMailItem`(:3514) 与详情(:4520) 读 `mail.body`，`.pre` 容器 `escapeHtml` 渲染（:5022），审计 after.bodyPreviewText（:348）→ 故 I-2 必须存纯文本。

### 交互点汇总
- IP-1：QA 正文（写：3 发送点）× 客户端展示（读：邮件客户端）——本计划核心，HTML 化解决。
- IP-2：`mail_record.body`（写：3 发送点）× 前端列表/详情/审计（读：app.js）——必须保持纯文本（I-2）。
- IP-3：迁移补 `\n\n`（写：V46）× composer 拼接（读：QaReplyComposer）——迁移只动单条正文内部，section 间分隔仍由 composer 负责（I-5）。

## 实现方案

### 阶段 A：纯文本转 HTML 能力（I-3）
- A1. `MailContentService` 新增 `fun plainTextToHtml(plain: String): String`（约束见 I-3）：HTML 转义 → 按 `\n\s*\n` 切段 → 每段内 `\n` 转 `<br>` → 包成 `<p>…</p>` 串（可选最外层 `<div>`）。空白/空串返回空 `<p></p>` 或空串（与既有 `htmlToPlainText` 风格一致）。
  - 文件：`mail/service/MailContentService.kt`

### 阶段 B：三个 QA 发送点改为 HTML 多部分（I-1、I-2、I-5）
- B1. `AutoMailReplyService`：构造器注入 `mailContentService: MailContentService`；:469 改为
  `val plainBody = match.replyBody`，`ComposedMail(to, subject, body = mailContentService.plainTextToHtml(plainBody), html = true, text = plainBody)`；:488 `MailRecord.body` 改存 `plainBody`（纯文本，I-2）。
  - 文件：`mail/service/AutoMailReplyService.kt`
- B2. `PendingMailOperationService`：构造器注入 `mailContentService`；
  - `sendQaReply` :102 → `val plainBody = rule.replyBody`，`ComposedMail(..., body = plainTextToHtml(plainBody), html = true, text = plainBody)`，对应 MailRecord 存 `plainBody`。
  - `sendManualComposedReply` :291 → `ComposedMail(..., body = plainTextToHtml(finalBody), html = true, text = finalBody)`，:310 MailRecord 仍存 `finalBody`（纯文本）。
  - **不动** :176-188 既有 HTML 路径。
  - 文件：`mail/service/PendingMailOperationService.kt`
- 注：`QaReplyComposer` **不改**——顺序契约与 section 拼接保持原状（I-5）。

### 阶段 C：现有正文段落补齐（I-4）
- C1. 新增 `V46__qa_reply_body_paragraphs.sql`，对**多句/多主题**的长正文 `UPDATE ... SET reply_body` 在主题边界插入 `\n\n`。目标规则（按 `reply_subject` 定位）：
  - `Program overview`（V41 总览，最长，多主题）——必改，按「致谢/两条 track｜资助额度｜申请材料｜流程与周期｜保密与无费用」≥5 段拆分。
  - `About the talent program`、`Application criteria`、`Application process`、`Full-time and part-time options`、`Workplace arrangement`、`Funding support`、`Thank you for your reply`（退休致谢，多句）——按句群在自然边界插 `\n\n`（每条 2-3 段）。
  - 1-2 句的短规则（如 `Email-only…`、`Project sensitivity…`、`Meeting arrangement`）**不动**。
  - 约束：ASCII-only 字面量；仅改 `reply_body`；用 `reply_subject` 作 WHERE（沿用 V45）。
  - 文件：`src/main/resources/db/migration/V46__qa_reply_body_paragraphs.sql`

### 阶段 D：测试
- D1. `MailContentServiceTest`：新增 `plainTextToHtml` 用例——多段（`\n\n`→多 `<p>`）、段内换行（`\n`→`<br>`）、HTML 转义（`<`/`&` 被转义）、空串。
- D2. `AutoMailReplyServiceTest`：断言 QA 自动回复发送的 `ComposedMail.html==true` 且 `text` 为原纯文本；`MailRecord.body` 存纯文本（不含 `<p>`）。
- D3. `PendingMailOperationServiceTest`：断言 `sendQaReply` 与 `sendManualComposedReply` 发送 `html==true`、`text` 为纯文本，MailRecord 存纯文本；并保留一条覆盖**逆序多规则**的断言，确认 HTML 化未改变 section 顺序（守 I-5 / K-composed-reply-order-contract）。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailContentService.kt` | 新增 `plainTextToHtml` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 注入 `MailContentService`；:469 HTML 化发送；:488 存纯文本 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 注入 `MailContentService`；:102、:291 HTML 化发送；MailRecord 存纯文本 |
| 4 | `src/main/resources/db/migration/V46__qa_reply_body_paragraphs.sql` | 新迁移：长正文补 `\n\n` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailContentServiceTest.kt` | `plainTextToHtml` 单测 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | QA 自动回复 HTML/纯文本断言 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 手动 QA 回复 HTML/纯文本 + 顺序断言 |

文件数：7（≤10）。子系统：mail service + DB 迁移（2）。共享存储新增字段：0。

## 验收标准

- I-1：三个发送点单测/集成断言 `ComposedMail.html==true` 且 `text` 非空为纯文本；`SmtpMailDeliveryService` 既有 multipart 测试保持绿。手动发一封 QA 回复到 Gmail，确认段落保留。
- I-2：断言三处 `MailRecord.body` 不含 `<p>`/`<br>`，等于原纯文本；前端邮件详情仍正常显示分行（`.pre`）。
- I-3：`MailContentServiceTest` 覆盖多段、段内 `<br>`、`<`/`&` 转义、空串。
- I-4：`V46` 仅含 `UPDATE qa_rule SET reply_body`；grep 确认无其它列、无 ASCII 外字符；`mvn` 启动 Flyway 迁移成功；查库确认目标规则 `reply_body` 含 `\n\n` 且短规则未变。
- I-5：逆序多规则用例断言 HTML 输出中 section 出现顺序 == 运营选择顺序；composer 单测不变仍绿。
- 集成（IP-1×IP-2）：跑一条多规则 QA 自动回复，验证外发为多段 HTML、库内为纯文本、审计 `bodyPreviewText` 为纯文本。
- 回归：`mvn test` 全绿（JDK 11 zulu）。

## 自检清单

- [x] 关键不变量含每个新增能力/数据的不变量（I-1..I-5）
- [x] 现状审计列全三处写路径（grep 验证，非记忆）
- [x] 无未被不变量覆盖的新写路径
- [x] 文件数 ≤ 10（7）
- [x] 子系统 ≤ 2
- [x] 每个任务引用其约束不变量编号
- [x] 验收标准每个不变量至少一条
- [x] 文件清单无「相关文件 / 等」
- [x] 超出范围已显式延后
- [x] Phase 0 知识：K-composed-reply-order-contract 已采纳为 I-5；其余 QA/mail 知识审阅后与本次格式改动无冲突
- [x] 计划存于 docs/plans/2026-06-27/

## Phase 0 知识引用

- K-composed-reply-order-contract（hit_count↑，last_used 2026-06-27）→ I-5：HTML 化禁止重排 section。
- K-gap-items-compose-only / K-overview-gap-supersede：本次不触碰 `detectGap` / supersede 逻辑，仅确认 `Program overview` 是 `supersedes_children=1` 的总览规则、是补段落的首要目标，无冲突。
