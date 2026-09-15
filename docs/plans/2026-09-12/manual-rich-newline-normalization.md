# 人工富文本发送换行规范化

## 需求描述

人工富文本回复在提交 SMTP 前统一规范正文：纯文本任意连续换行（含 CRLF、LF、空白行）压成一个 `\n`；富文本中连续 `<br>` 与纯空行块压成一个可见换行。线上 `mail_record.id=3984` 已证明当前正文在相邻段落间写入 5 个 LF，必须阻止同类正文再次外发。

不得改变：富文本的加粗、斜体、链接、列表；变量最终渲染；安全确认；QA/RAG 审计；会议附件；会话回信幂等、线程头及账号选择；非人工富文本邮件既有的 `\n\n` 段落约定。

范围外：不回写历史 `mail_record`；不修改 AI/QA 段落生成协议；不改变所有邮件客户端对 `<p>` 外边距的自有渲染；不增加新 UI、CSS、数据库字段或迁移。

## 关键不变量

### Invariant I-1: 人工富文本纯文本单换行
- Rule: 两个人工富文本入口进入共享发送实现后，最终 `text/plain` 中不得出现 `\r`，也不得出现两个相邻换行；段落边界固定为一个 `\n`。空白行中的空格、Tab 视作空行并一并压缩。
- Applies to: `sendManualRichReply`、`sendConversationManualRichReply` 经 `executeManualRichSend` 产生的 `finalTextBody`、`SendPayload.finalText`、`ComposedMail.text`、审计预览与 `mail_record.body`。
- Violation consequence: 收件人纯文本视图、复制结果和站内历史出现多余空行。
- 来源: original；K-plaintext-reply-client-reflow

### Invariant I-2: 富文本可见空行同步收敛
- Rule: 人工富文本最终 HTML 中连续 `<br>`（允许标签间空白）最多保留一个；仅由空白、`&nbsp;`、`<br>` 组成的空 `<p>`/`<div>` 不得形成重复空行。其他标签、属性与文本顺序逐字保留。
- Applies to: 前端提交的 `htmlBody` 与服务端最终渲染后的 `finalHtmlBody`。
- Violation consequence: HTML 邮件仍可能显示多个空白行，即使纯文本已修复。
- 来源: original

### Invariant I-3: 服务端是最终发送门
- Rule: 前端先规范化以保证编辑/提交体验；服务端必须在变量渲染之后、最终校验/安全检查/幂等指纹/SMTP/落库之前再次规范化。API 调用者不得绕过该规则。
- Applies to: `mailbox-chat.js:sendManualReply`；`PendingMailOperationService.executeManualRichSend`。
- Violation consequence: 其他客户端或旧页面仍能发送异常正文，且幂等指纹与实际正文不一致。
- 来源: K-manual-rich-render-before-send

### Invariant I-4: 规范正文单一贯穿
- Rule: 同一次发送的安全检查、会议正文核对、`SendPayload` 指纹、SMTP 两个 MIME alternative、成功/失败持久化、审计预览与归档必须引用同一份规范化后的 final text/html；禁止检查原文却发送另一份正文。
- Applies to: `executeManualRichSend` 的所有 final body 读路径。
- Violation consequence: 校验、幂等、实际邮件和数据库记录互相矛盾。
- 来源: K-manual-rich-render-before-send；K-mailbox-draft-cache-owner-capture

### Invariant I-5: 仅限定人工富文本
- Rule: 不修改 `plainTextToHtml`、`htmlToPlainText`、AI composer 的 `joinToString("\n\n")` 或自动/模板邮件；历史与其他邮件继续使用原段落规则。
- Applies to: `MailContentService` 新增的 manual-rich 专用函数及唯一调用点。
- Violation consequence: Gmail/Outlook 其他邮件段落退化，或 AI 锁定文本/哈希协议变化。
- 来源: K-plaintext-reply-client-reflow

### Invariant I-6: 富文本与业务状态不受破坏
- Rule: 换行规范化不得去除 `<b>/<strong>/<i>/<em>/<a>/<ul>/<ol>/<li>` 等非空内容；不得仅因换行变化把 AI 草稿标记为正文语义编辑；安全确认重提必须复用同一规范化 request body 与既有 requestId。
- Applies to: 前端 adoption、draft、edited 判定、两级确认重提；服务端 HTML 规范化。
- Violation consequence: 格式丢失、QA 审计误报或重复发送风险。
- 来源: K-ai-adopt-direct-send-no-residual-gates；K-mailbox-draft-cache-owner-capture

## 样式契约

### S-1: 人工富文本编辑区零视觉结构改动
- 复用：`.mail-chat .mc-editor` 位于 `src/main/resources/static/mailbox-chat.css:64` 与窄布局覆盖 `:130`；本计划不修改任一规则。
- 新增：无 CSS、无 class。
- DOM 结构：继续使用 `<div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="人工回复正文" data-role="mc-editor">…</div>`（`mailbox-chat.js:2561`）。
- 禁止项：修改 DOM 层级；新增提示、按钮、inline style 或 class；修改 `line-height:1.8`、尺寸、颜色、焦点样式。

## 现状审计

### 人工富文本编辑器与会话草稿缓存
- Schema/mapping: 无持久化 schema；会话 cache 外层按 `user|accountScope|contactId`，内部 draft 持有 `subject/html/text/requestId/qa/meeting`。
- Write paths:
  1. `mailbox-chat.js:readManualValues/saveDraftFromInputs` — 每次输入把 `editor.innerHTML` 与 `editor.innerText` 原样写草稿。
  2. `mailbox-chat.js:adoptAssembly` — 把 AI `renderedDraftText` 原样赋给 `editor.innerText`。
  3. `mailbox-chat.js:sendManualReply` — 再次读取 live `innerHTML/innerText`，构造来信或会话回信请求。
- Read paths:
  1. `manualComposeHtml` — 从 draft 恢复 HTML/text。
  2. `sendManualReply` — `innerText` 同时用于 `textBody` 和 QA `edited` 判定。
  3. 会议 stale/一致性检测 — 读取编辑器可见文本。
- Interaction points: adoption/粘贴产生连续 `<br>` 或块边界 → draft 保留 → send 原样提交；requestId、QA baseline 与会议快照共享同一 draft。

### 人工富文本服务端发送链
- Schema/mapping: `mail_record.body LONGTEXT`；人工富文本成功/失败记录优先保存 `SendPayload.finalText`。`mail_send_attempt` 的幂等指纹包含 final text 与 final HTML。
- Write paths:
  1. `PendingMailOperationService.sendManualRichReply` — 来信入口。
  2. `PendingMailOperationService.sendConversationManualRichReply` — 已发联系人会话入口。
  3. `PendingMailOperationService.executeManualRichSend` — 两入口共享的变量渲染、校验、指纹、SMTP 与审计编排。
  4. `ManualReplySendAttemptService.finalizeSuccess/finalizeFailure` — 将同一 payload text 写入 `mail_record.body`。
- Read paths:
  1. `SmtpMailDeliveryService.send` — `ComposedMail.text` 写入 `multipart/alternative` 的 `text/plain`，HTML 写入另一 part。
  2. `ManualReplySendAttemptService.computeFingerprint` — final text/html 进入幂等 hash。
  3. `PendingMailOperationService` safety、会议一致性、审计预览、unsupported-answer archive — 均读取 final body。
  4. 邮箱/专家邮件历史 — 读取 `mail_record.body`，以 `white-space:pre-wrap` 展示（来源: K-mail-body-display-sites）。
- Interaction points: 服务端若在 fingerprint 后才改换行会破坏幂等；若只改 SMTP 不改 payload 会令数据库/审计与邮件不一致；若在变量渲染前处理会遗漏占位符展开内容。

### 生产证据
- 2026-09-12 只读查询 `cbasdogan@ku.edu.tr` 最新 OUTBOUND：`mail_record.id=3984`、`MANUAL_RICH_REPLY`、`SENT`、`sent_at=2026-09-12 10:34:49`。
- `body` 共 22 个 LF；主要段落边界各为 5 个连续 LF；当前前后端均无 manual-rich 换行规范化。

### 前端样式盘点
- 可复用 class：`.mc-editor` — `mailbox-chat.css:64/:130`；`.mc-editor-tools` — `:63`；focus-visible — `:68`。
- 设计基准 token：正文 `12px`、`line-height:1.8`、padding `12px`、border `#dce4ef`、radius `7px`、background `#fff`、color `#334155`；窄版 min/max-height `100px/240px`。
- DOM 结构约定：`mc-compose > label + mc-editor-tools + mc-editor + meeting-attachment + mc-compose-footer`。
- 改动前基线：编辑器 DOM 与 CSS 逐字保持 S-1；本计划只改字符串规范化与发送数据。

## 实现方案

### 阶段 1：定义可测试的规范化函数
- 修改 `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailContentService.kt`：新增 manual-rich 专用 `normalizeManualTextLineBreaks` 与 `normalizeManualRichHtmlLineBreaks`。文本处理 CRLF/CR、空白行及连续 LF；HTML 仅折叠连续 `<br>` 和重复纯空行块，不碰非空标签/属性/顺序。遵守 I-1、I-2、I-5、I-6。
- 修改 `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailContentServiceTest.kt`：覆盖 5 LF、CRLF、带空格/Tab 的空行、单 LF 幂等、重复 `<br>`、空 `<p>/<div>`、bold/link/list 原样保留。遵守 I-1、I-2、I-5、I-6。

### 阶段 2：前端入口预规范化
- 修改 `src/main/resources/static/mailbox-chat.js`：新增与服务端规则一致的纯函数；`adoptAssembly` 入编辑器前规范化；`readManualValues` 保存 canonical text；`sendManualReply` 在生成 request body 和 `edited` 判定前再次规范化 text/html。规范化后的 request body 在两级安全确认中保持不变；不重写 live editor DOM，不丢富文本。遵守 I-1、I-2、I-3、I-4、I-6、S-1。
- 修改 `src/test/js/mailboxChatBehavior.test.js`：分别覆盖 inbound/outbound 发送 5 LF 与 CRLF，断言 adapter 收到单 LF、HTML 无重复 `<br>`、bold/link/list 仍在、仅换行差异不令 `edited=true`、安全确认重提 payload/requestId 不漂移。遵守 I-1 至 I-6、S-1。

### 阶段 3：服务端最终发送门
- 修改 `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`：在变量渲染完成后立即规范 `renderedText` 与渲染后 HTML，并让后续 validation、meeting、safety、payload、fingerprint、SMTP、preview、archive 全部只读 canonical final body。不得改 DTO/endpoint。遵守 I-1 至 I-6。
- 修改 `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`：绕过前端直接向两个入口提交异常换行；捕获 `SendPayload` 和 `ComposedMail`，断言 final text/html 已规范且相等贯穿；覆盖会议、变量、幂等与正常单换行回归。遵守 I-1 至 I-6。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/resources/static/mailbox-chat.js` | 前端规范化及两个人工回复请求组装 |
| `src/test/js/mailboxChatBehavior.test.js` | 前端发送、格式、edited、确认重提测试 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailContentService.kt` | manual-rich 专用文本/HTML 规范化函数 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailContentServiceTest.kt` | 规范化纯函数测试 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 共享发送边界强制规范化 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 两入口到 payload/SMTP 的贯穿测试 |

## 验收标准

- I-1: 输入 `A\r\n\r\n\r\nB`、`A\n\n\n\n\nB`、`A\n \t\nB`，前端 request、`SendPayload.finalText`、`ComposedMail.text` 均严格等于 `A\nB`；无 `\r`、无 `\n\n`。
- I-2: `<b>A</b><br><br><br><a href="https://x.test">B</a>` 最终保留 bold/link 且只有一个 `<br>`；重复空 p/div 不产生连续空行；list DOM 不变。
- I-3: 直接调用两个服务端 API（不经过浏览器）仍得到相同 canonical final body。
- I-4: Mockito 捕获的 prepareAndClaim payload、SMTP mail、审计/归档参数逐字使用同一 canonical text/html；规范化发生在 claim 前。
- I-5: `MailContentServiceTest` 现有 `plainTextToHtml("First paragraph.\n\nSecond paragraph.") == "<p>First paragraph.</p><p>Second paragraph.</p>"` 保持通过；AI composer 测试无改动。
- I-6: 富文本标签、变量、会议附件、QA/RAG、安全确认、会话 requestId/线程头回归测试通过；仅换行改变不标记语义编辑。
- S-1: `git diff` 中 `mailbox-chat.css`、编辑器 DOM 字符串零变化；无新增 class、inline style、按钮或提示。
- 自动门禁：`node --test src/test/js/mailboxChatBehavior.test.js`；`mvn -Dtest=MailContentServiceTest,PendingMailOperationServiceTest test`；`node --test src/test/js/*.test.js`；`mvn test`。

## 人工验收清单

### A-1: AI 草稿发送单换行
- 前置条件: 测试联系人有一封待处理来信；AI 草稿至少 5 段。
- 操作步骤: 1. 采用 AI 草稿；2. 不改正文直接发送；3. 在测试收件箱查看原始 MIME 的 `text/plain`；4. 查询对应 `mail_record.body`。
- 预期结果: 每两个可见段落之间恰好一个 LF；MIME 与 DB 均无 `\r`、无 `\n\n`。
- 覆盖: I-1、I-3、I-4

### A-2: 手工粘贴多空行富文本
- 前置条件: 测试联系人允许人工回复；准备含加粗、链接、列表及段间 5 个空行的富文本。
- 操作步骤: 1. 粘贴到人工富文本框；2. 发送；3. 打开收件邮件 HTML 与纯文本源。
- 预期结果: 纯文本段间恰好一个 LF；HTML 无连续 `<br>`/重复纯空行块；加粗、链接地址与列表全部保留。
- 覆盖: I-1、I-2、I-3、I-6、S-1

### A-3: 会话回信与安全确认
- 前置条件: 联系人无待处理来信但存在 SENT 出站锚点；正文触发一级安全确认并含连续空行。
- 操作步骤: 1. 点击发送；2. 在安全提示中确认；3. 查看邮件源和发送记录。
- 预期结果: 仅投递一封；段间恰好一个 LF；Message-ID/requestId 不因确认重提变化；In-Reply-To 仍为最近 SENT 锚点。
- 覆盖: I-1、I-3、I-4、I-6

### A-4: 正常单换行回归
- 前置条件: 准备正文 `A\nB`，包含 `${senderName}` 变量，并可附加会议日历。
- 操作步骤: 1. 预览会议；2. 发送；3. 查看正文与 ICS。
- 预期结果: 正文仍为 `A\nB`；变量替换为真实发件人；ICS 可下载且 hash 校验通过；无重复发送。
- 覆盖: I-4、I-5、I-6

### A-5: 其他邮件段落回归
- 前置条件: 选择一条普通模板/自动邮件，其纯文本含 `First paragraph.\n\nSecond paragraph.`。
- 操作步骤: 1. 预览或发送到测试邮箱；2. 查看 HTML 源。
- 预期结果: 仍生成两个 `<p>`，不受人工富文本单换行规则影响。
- 覆盖: I-5

### A-6: 编辑器视觉回归
- 前置条件: 打开人工富文本回复区。
- 操作步骤: 1. 对照现网查看编辑器、工具栏、焦点和窄屏高度；2. 输入并格式化正文。
- 预期结果: 字号 12px、行高 1.8、padding 12px、圆角 7px、窄屏高度范围 100–240px；DOM/按钮/颜色无变化。
- 覆盖: S-1
