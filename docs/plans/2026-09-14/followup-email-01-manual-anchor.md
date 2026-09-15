# 跟进邮件：人工选择引用邮件与自然正文

> 状态：待执行。用户已确认交互方案；本计划只定义功能实现，不执行代码修改。
>
> 上位约束：[`00-followup-email-main.md`](00-followup-email-main.md)。如有冲突，以总计划的顺序、文件边界和发布门禁为准；实现语义与逐字样式仍以本计划为准。

## 需求描述

在收发件箱的“人工回复”工具栏中，把“跟进邮件”按钮放在“会议确认”旁。点击后弹出窗口，运营必须手动选择一封当前专家、当前账号范围内已经成功发送的邮件；系统不预选。选中后显示可编辑的短跟进正文和完整原邮件引用，点击“填入人工回复”后回填现有主题与富文本编辑器，最终仍由现有“发送人工回复”按钮发送。

默认正文使用短句，不在正文中机械重复发送日期；日期只出现在标准引用头中。当前专家标签只在其语义唯一时提供视频或简历文案：只有“待约视频”时使用视频文案，只有“待发简历”时使用简历文案；两者同时存在、都不存在或标签不可用时使用通用文案。正文始终可人工修改。

必须保持：

- 未点击“填入人工回复”时，主题、正文、QA 草稿、会议草稿均不变化。
- 普通来信回复仍走 `/api/mail/unmatched-inbound/{id}/manual-rich-reply`；只有已经填入所选发件锚点的草稿改走会话级接口。
- 没有显式锚点的既有无来信会话回信继续由服务端选择最近成功发件。
- 现有安全确认、退订门禁、幂等、发送状态收敛、操作审计、换行规范化和发送成功后刷新逻辑不变。
- 现有“会议确认”和“选择模板发送跟进邮件”入口保留。

范围外：

- 不自动发送、不定时提醒、不增加跟进次数或跟进状态。
- 不新增邮件模板管理、AI 生成、批量跟进或历史数据回写。
- 不新增数据库表、字段、迁移、GET 接口或独立前端组件文件。
- 弹窗只列出当前已加载的时间线窗口；如存在更早页，明确提示先关闭弹窗并点击“加载更早信件”。本计划不为此循环拉取全部历史。
- 静态资源缓存键激活由顺序计划 `followup-email-02-cache-activation.md` 单独处理。

## 关键不变量

### Invariant I-1: 引用邮件必须人工选择
- Rule: 弹窗初始状态没有选中项，“填入人工回复”禁用；只有运营点击某封邮件后才能填入。禁止默认选择最新邮件或任何自动回退。
- Applies to: 跟进按钮、弹窗状态、选择列表、填入动作。
- Violation consequence: 自动引用错误邮件，违背本需求的核心目的。
- 来源: original

### Invariant I-2: 候选只来自当前真实成功发件
- Rule: 前端候选必须同时满足 `source='MAIL_RECORD'`、`direction='OUTBOUND'`、`sendStatus='SENT'`，且 `accountScope` 非空时 `accountCode` 必须相等；服务端收到 `anchorMailRecordId` 后必须重新读取 `mail_record` 并验证同一 `contactId`、`OUTBOUND`、`SENT`、非空非模拟器账号及 `accountScope`。任一条件失败统一返回 `422 CONVERSATION_SENT_ANCHOR_NOT_FOUND`，且不得 claim 或 SMTP。
- Applies to: 时间线筛选、`ConversationManualRichReplyRequest`、`sendConversationManualRichReply`。
- Violation consequence: 可跨专家、跨账号或引用失败/模拟邮件发信。
- 来源: K-manual-rich-reply-anchor-must-be-real

### Invariant I-3: 所选锚点决定线程与账号
- Rule: 显式锚点合法时，发件账号固定取该记录的 `sender_account_code`；其合法 `message_id` 进入新记录 `in_reply_to` 与 SMTP `In-Reply-To`，`References` 继续按现有 `anchor.inReplyTo + anchor.messageId` 组成。不得再查询或替换为最近发件。
- Applies to: `sendConversationManualRichReply`、`ManualRichSendSource`、`ComposedMail`、`SendPayload`。
- Violation consequence: UI 显示选择 A，实际邮件却接在线程 B。
- 来源: K-outbound-thread-headers-single-seam；K-manual-send-explicit-account-must-match-binding

### Invariant I-4: 选择与幂等请求绑定
- Rule: 草稿保存 `followUpAnchorMailRecordId`；主题、正文或锚点任一变化都把旧 `requestId` 置空。发送前生成的 requestId 随失败重试保留；成功响应仍先按 requestId 收敛，再读取锚点。`sourceAnchor='MAIL_RECORD:<id>'` 必须使用所选真实 id。
- Applies to: `readManualValues`、`saveDraftFromInputs`、requestId helper、会话级发送入口、`SendPayload`。
- Violation consequence: 换锚点后复用旧 attempt，或响应丢失后重复投递。
- 来源: K-manual-send-fingerprint-complete-identity；K-manual-send-unknown-must-converge；K-mailbox-draft-cache-owner-capture

### Invariant I-5: 跟进正文短且可编辑
- Rule: 主正文不写“我在某日发送”或 `For reference...`。默认文案逐字如下；称呼优先复用所选邮件正文首个非空行，但只接受 `Dear ...` 或 `Hi ...` 且最长 100 字符，否则使用 `Dear Professor,`。签名名取所选 `accountCode`。标签判定只使用 `selectedSummary.expertTags` 的原值；仅命中一个业务标签时使用对应文案，否则使用通用文案。
- Applies to: 正文生成、弹窗正文编辑、填入人工回复。
- Violation consequence: 文案机械，或基于不可靠姓名拆分生成错误称呼。
- 来源: original

视频文案：

```text
{greeting}

Just following up on my email below about a brief Zoom call. Would you be available sometime this week or next? We’re happy to work around your time zone.

Best regards,
{accountCode}
```

简历文案：

```text
{greeting}

Just following up on my note below. When convenient, could you please send your CV? It will help us identify suitable industry partners.

Best regards,
{accountCode}
```

通用文案：

```text
{greeting}

Just following up on my email below. Please let me know when you have a chance.

Best regards,
{accountCode}
```

### Invariant I-6: 引用正文与线程锚点同源
- Rule: 主题、引用头和完整引用正文都必须来自同一个已选择的时间线对象；主题使用现有 `chatSubjectPrefill(selected.subject)`；引用头固定为 `On YYYY-MM-DD HH:mm, <accountCode> wrote:`。引用源优先取非空 `cleanedBody`，否则取 `body`；若为 HTML，先把 `<br>` 变为 `\n`、`</li>` 变为 `\n`，并把 `</p>`、`</div>`、`</blockquote>`、`</h1>` 至 `</h6>` 各自变为 `\n\n`，再用 `DOMParser.parseFromString(..., 'text/html').body.textContent` 在惰性文档中取纯文本，最后统一 CRLF、去行尾空白并把 3 个以上连续换行压为 2 个。称呼也从这份纯文本首行提取。所有输出必须再经 DOM `textContent` 或现有 `escapeText` 处理。
- Applies to: 弹窗预览、人工回复 HTML/text、会话请求 `anchorMailRecordId`。
- Violation consequence: 预览内容与实际线程锚点不一致，或引入 HTML 注入。
- 来源: original

### Invariant I-7: 跟进草稿与会议/QA互斥
- Rule: “填入人工回复”是全文替换操作，必须清除当前 `instance.manual.qa`、会议快照和会议正文块；反向采用可信回复草稿或应用会议确认时必须清除 `followUpAnchorMailRecordId`。只打开、切换选择或取消弹窗不得清除任何状态。
- Applies to: 跟进填入、`adoptAssembly`、`applyMeetingFromDialog`、草稿保存。
- Violation consequence: 会话级跟进发送携带旧会议正文却不带 ICS，或错误丢弃 QA 审计上下文。
- 来源: K-manual-rich-final-body-single-seam

### Invariant I-8: 现有发送链不旁路
- Rule: 跟进发送复用 `mcHostSendConversationRichReply` → `submitManualRichReply` → `sendConversationManualRichReply` → `executeManualRichSend`；安全检查、suppression、claim、SMTP、finalize、after-commit audit 与最终正文规范化顺序不得复制或重排。
- Applies to: 前端发送分支、Controller DTO、`PendingMailOperationService`。
- Violation consequence: 跟进邮件绕过退订、安全确认、幂等或审计。
- 来源: K-manual-rich-final-body-single-seam；K-manual-send-unknown-must-converge

## 样式契约

### S-1: 工具栏按钮
- 复用：`.mail-chat .button`（`src/main/resources/static/mailbox-chat.css:66-68,147-148`）。跟进按钮只允许 `class="button"`，不新增按钮 class，不修改既有规则。
- 新增：无。
- DOM 结构：

```html
<div class="mc-editor-tools">
  <button class="button" type="button" data-action="mc-rich-command" data-command="bold">B</button>
  <button class="button" type="button" data-action="mc-rich-command" data-command="italic">I</button>
  <button class="button" type="button" data-action="mc-rich-command" data-command="insertUnorderedList">列表</button>
  <button class="button" type="button" data-action="mc-rich-command" data-command="createLink">链接</button>
  <!-- inbound 且会议组件可用时保留既有会议确认按钮 -->
  <button class="button" type="button" data-action="mc-open-followup">↗ 跟进邮件</button>
</div>
```

- 禁止项：inline style；改变会议按钮顺序；修改 `.button`。

### S-2: 跟进弹窗
- 复用：`.button/.button.primary`；`.mail-chat :is(...):focus-visible`（`mailbox-chat.css:68`）。
- 新增：以下规则必须逐字追加到 `src/main/resources/static/styles.css`；不得改值：

```css
/* Follow-up email composer: mailbox portal only. */
.followup-dialog{margin:auto;inset:0;width:min(980px,calc(100vw - 40px));max-height:calc(100dvh - 40px);padding:0;border:1px solid #d5dfed;border-radius:14px;background:#fff;color:#334155;font-family:var(--font-body);font-size:12px;line-height:1.6;box-shadow:0 24px 100px #172c473d;overflow:auto;overscroll-behavior:contain}
.followup-dialog::backdrop{background:#182a464f;backdrop-filter:blur(2px)}
.followup-dialog *{box-sizing:border-box}
.followup-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding:18px 22px;border-bottom:1px solid #e2e8f0;background:#fff;position:sticky;top:0;z-index:2}
.followup-head h2{margin:0;font-size:18px;font-weight:600;color:#334155;line-height:1.4}
.followup-head p{margin:5px 0 0;color:#64748b;font-size:12px}
.followup-close{display:inline-flex;align-items:center;justify-content:center;flex:none;width:28px;height:28px;padding:0;border:0;border-radius:5px;background:transparent;color:#91a1b7;font:inherit;font-size:21px;cursor:pointer}
.followup-close:hover{background:#edf3ff;color:#2451b9}
.followup-close:active{background:#dbeafe}
.followup-grid{display:grid;grid-template-columns:44% 56%;min-height:430px}
.followup-list-pane,.followup-preview-pane{min-width:0;padding:18px 20px}
.followup-list-pane{border-right:1px solid #e2e8f0;background:#f8faff}
.followup-pane-title{margin:0 0 5px;font-size:13px;font-weight:600;color:#445b79}
.followup-help{margin:0 0 12px;color:#75859c;font-size:11px;line-height:1.7}
.followup-mail-list{display:flex;flex-direction:column;gap:8px}
.followup-mail-option{width:100%;padding:11px 12px;border:1px solid #d8e1ee;border-radius:9px;background:#fff;color:#334155;text-align:left;font:inherit;cursor:pointer}
.followup-mail-option:hover{border-color:#93b4ec;background:#f5f8ff}
.followup-mail-option:active{background:#eaf1ff}
.followup-mail-option[aria-checked=true]{border-color:#3b82f6;background:#eff5ff;box-shadow:0 0 0 1px #3b82f6}
.followup-mail-option small,.followup-mail-option span{display:block;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.followup-mail-option small{color:#7b8ba2;font-size:11px}
.followup-mail-option strong{display:block;margin:4px 0;color:#334155;font-size:12px;font-weight:600;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.followup-mail-option span{color:#64748b;font-size:11px}
.followup-empty{display:grid;place-content:center;min-height:340px;color:#7b8ba2;text-align:center}
.followup-field{display:flex;flex-direction:column;gap:6px;margin-bottom:12px;color:#52647e;font-weight:500}
.followup-field input,.followup-field textarea{width:100%;margin:0;padding:8px 10px;border:1px solid #d7e0ed;border-radius:7px;background:#fff;color:#334155;font:inherit;font-weight:400;box-shadow:none}
.followup-field input{height:36px;min-height:36px}
.followup-field textarea{min-height:128px;resize:vertical;line-height:1.7}
.followup-field :is(input,textarea):hover:not(:disabled){border-color:#93b4ec}
.followup-quote{max-height:190px;overflow:auto;padding:12px 14px;border-left:3px solid #bfd0e8;background:#f8faff;color:#5d6f88;font-size:11px;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}
.followup-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:14px 22px;border-top:1px solid #e2e8f0;background:#fff;position:sticky;bottom:0}
.followup-actions p{margin:0;color:#75859c;font-size:11px}
.followup-actions>div{display:flex;gap:8px;flex:none}
.followup-dialog :is(button,input,textarea):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.followup-dialog :is(button,.button):disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
@media(max-width:760px){.followup-dialog{width:calc(100vw - 20px);max-height:calc(100dvh - 20px)}.followup-grid{grid-template-columns:1fr}.followup-list-pane{border-right:0;border-bottom:1px solid #e2e8f0}.followup-head,.followup-list-pane,.followup-preview-pane{padding:14px}.followup-actions{align-items:flex-end;flex-wrap:wrap;padding:12px 14px}.followup-actions p{width:100%}.followup-actions>div{margin-left:auto}}
@media(prefers-reduced-motion:reduce){.followup-dialog *{transition:none!important;scroll-behavior:auto!important}}
```

- DOM 结构：

```html
<dialog class="followup-dialog" aria-labelledby="followupTitle">
  <header class="followup-head">
    <div><h2 id="followupTitle">生成跟进邮件</h2><p>专家名 · 请手动选择本次要引用的邮件</p></div>
    <button class="followup-close" type="button" data-action="mc-close-followup" aria-label="关闭跟进邮件">×</button>
  </header>
  <div class="followup-grid">
    <section class="followup-list-pane">
      <h3 class="followup-pane-title">1. 选择引用邮件</h3>
      <p class="followup-help">仅显示当前回复账号成功发出的已加载邮件。系统不会自动选择。</p>
      <div class="followup-mail-list" role="radiogroup" aria-label="可引用的已发送邮件"></div>
    </section>
    <section class="followup-preview-pane"></section>
  </div>
  <footer class="followup-actions">
    <p>选择只填入草稿，不会立即发送邮件</p>
    <div><button class="button" type="button" data-action="mc-close-followup">取消</button><button class="button primary" type="button" data-action="mc-apply-followup" disabled>填入人工回复</button></div>
  </footer>
</dialog>
```

- 禁止项：inline style；未在本契约中声明的新 class；修改既有 class；动画。

### S-3: 填入后的锚点提示
- 复用：`.mc-note`（`mailbox-chat.css:57`）；不新增 CSS。
- DOM 结构：`<div class="mc-note" data-role="followup-anchor-note">已引用邮件 #4007 · 2026-09-12 11:49 · Re: ...</div>`，仅 draft 存在 `followUpAnchorMailRecordId` 时渲染在 `.mc-editor` 后、会议附件前。
- 禁止项：把 Message-ID 或内部 fingerprint 显示给运营；inline style。

## 现状审计

### `mail_record`
- Schema/mapping: `V1__create_business_tables.sql:97-115` 定义 `id/expert_contact_id/direction/message_id/in_reply_to/subject/body/send_status/sent_at`；`V15__add_mail_monitoring_columns_and_promotion_audit.sql:2-15` 增加 `sender_account_code/triggered_by/source_inbound_id`。`MailRecord.kt:7-35` 与表字段对应。本计划不改 schema。
- Write paths:
  1. `ManualOutreachTxHelper.kt:59,110` — 人工触达成功/失败记录。
  2. `ManualExpertMailService.kt:70` — 模板人工发件记录。
  3. `MeetingScheduleService.kt:144` — 会议邮件记录。
  4. `AutoMailReplyService.kt:356,696,971,1169` — 入站归档、自动回复及失败记录。
  5. `ManualReplySendAttemptService.kt:302-374,377-445` — 人工富文本成功/失败记录；本功能继续只走此写路径。
  6. `V24__extend_mail_send_attempt_state_and_link_mail_record.sql:26-32` — 历史 attempt 关联回填。
- Read paths:
  1. `MailboxConversationRepository.timelineMessages/rangeUnionSql`（`:387-425,439-495`）— 当前专家时间线读取完整 `body/cleaned_body/message_id/in_reply_to`。
  2. `MailboxConversationService.listMessages`（`:201-279`）— 投影 `source/id/accountCode/subject/body/cleanedBody/eventAt/sendStatus/messageId/inReplyTo`，当前数据已足够构建选择弹窗，无需新增 GET。
  3. `MailRecordRepository.findById`（继承 `CrudRepository`）— 服务端按显式 id 重新读取权威锚点。
  4. `MailRecordRepository.findLatestSentOutboundAnchor`（`:850-870`）— 既有无显式锚点路径选择最近成功发件，必须保留。
  5. 其他现有读取者按职责仍包括联系人/首发、材料、AI/RAG、收发判断、监控、旧 mailbox 与人工发送服务；本计划不新增字段，也不改变这些读者口径。
- Interaction points: 时间线把真实 `mail_record.id` 给前端选择 → Controller 只把 id 当提示 → Service 重新读取同一行并验证 → `ManualReplySendAttemptService` 写新 `mail_record` → 时间线刷新读到新邮件。（来源: K-manual-rich-reply-anchor-must-be-real）

### `mail_send_attempt`
- Schema/mapping: `V23__create_mail_send_attempt_and_add_mail_record_error.sql:1-17` 定义唯一 `(orcid_id, mail_type)` 与唯一 `message_id`；`V24:18-40` 增加 fingerprint payload 字段及 `mail_record.mail_send_attempt_id` 一对一约束。
- Write paths: 全部生产写入集中在 `ManualReplySendAttemptService.prepareAndClaim` 的 `insertIgnore/claimStatus`，以及 `finalizeSuccess/finalizeFailure` 的 `updateStatusAndError`。
- Read paths: 同服务 `findCompletedByRequestId`、`prepareAndClaim` 和两个 finalize 读取 attempt；`MailRecordRepository.findByMailSendAttemptId` 读取唯一结果行。
- Interaction points: draft 的 `requestId + followUpAnchorMailRecordId` → `SendPayload.idempotencyRequestId + sourceAnchor` → full fingerprint；更换锚点必须生成新 requestId，响应丢失重试必须先返回旧成功结果。（来源: K-manual-send-fingerprint-complete-identity；K-manual-send-unknown-must-converge）

### 前端会话缓存与发送适配
- Schema/mapping: `mailbox-chat.js:302-347` 的模块内 `sessionStore` 保存每会话 `drafts: Map`；draft 当前含 `subject/html/text/qa/requestId/meeting/meetingAccountCode`，不写 localStorage 或数据库。
- Write paths:
  1. `readManualValues/saveDraftFromInputs`（`:3106-3157`）— 输入写 draft，正文变化清 requestId。
  2. `adoptAssembly`（`:3629-3664`）— 可信回复全文替换并写 draft。
  3. `applyMeetingFromDialog`（`:3500` 起）— 会议正文与快照写 draft。
  4. 跟进弹窗 `apply` — 本计划新增 `followUpAnchorMailRecordId` 并全文替换。
  5. 发送成功 `sendManualReply`（`:3785-3810`）— 删除捕获的 draft。
- Read paths:
  1. `manualComposeHtml`（`:2557-2596`）— 恢复主题、正文、会议与新增锚点提示。
  2. `sendManualReply`（`:3666-3821`）— 读取 live 正文和 draft，选择 inbound 或 conversation adapter。
  3. `instance.conversation.items`（`:382-390`，由 `listMessages` 填充）— 跟进候选唯一前端来源。
- Interaction points: 选择弹窗写 draft → 人工编辑继续保存同一 anchor → 发送按 anchor 改走 conversation adapter；切专家/账号/targetKey 时 draft 隔离且弹窗关闭。（来源: K-mailbox-draft-cache-owner-capture）
- 标签证据: `MailboxConversationSummaryResponse.expertTags`（`MailboxConversationController.kt:69`）与 `mailbox-chat.js:1091` 是页面现有唯一专家标签来源；仓库没有“待约视频/待发简历”的枚举或独立状态字段。因此实现只对用户给定的两个原始标签文本做精确匹配，缺失或歧义时使用通用文案，不推导新状态。

### Controller 与发送服务
- `ConversationManualRichReplyRequest`（`MailboxConversationController.kt:78-87`）当前只有 requestId/accountScope/正文/确认字段；新增唯一可空字段 `anchorMailRecordId: Long? = null`。
- `MailboxConversationController.conversationManualRichReply`（`:217-231`）按命名参数透传；`app.js:14564-14580` 已原样 JSON 序列化 requestBody，因此无需改 `app.js`。
- `PendingMailOperationService.sendConversationManualRichReply`（`:348-435`）当前在 completed attempt 收敛后固定调用 `findLatestSentOutboundAnchor`；本计划只把该处改成“显式 id → findById 并全校验；null → 原查询”。后续 `executeManualRichSend`（`:445` 起）保持原样。
- Interaction points: 前端显式 id → DTO → 服务端权威校验 → 现有 `ManualRichSendSource`；不得把前端对象或 Message-ID直接当权限边界。

### 前端样式盘点
- 可复用 class: `.mc-editor-tools`（`mailbox-chat.css:63`）；`.mc-note`（`:57`）；`.button` 与 disabled/focus（`:66-68,147-148`）；portal root `.mail-chat.mc-overlay-root`（`:154`）。
- 设计基准 token: 主色 `#1e40af`、focus `#3b82f6`、正文 `#334155`、次文字 `#64748b`、边框 `#dce4ef/#e2e8f0`、背景 `#fff/#f8faff`、圆角 `7/9/14px`、弹窗阴影 `0 24px 100px #172c473d`、按钮高度 `32px`。
- DOM 结构约定: `createPortalRoot`（`mailbox-chat.js:2695-2715`）把 `.mail-chat.mc-overlay-root` 放到 `document.body`，避免父 `.panel` 的 backdrop-filter 改变 fixed/dialog 定位；本弹窗复用该 root。（来源: K-mailbox-popover-scope-and-fixed-containing-block）
- 改动前基线: 工具栏为 `mc-compose > label + mc-editor-tools + mc-editor + meeting-attachment + mc-compose-footer`（`mailbox-chat.js:2579-2595`）；S-1 只追加一个按钮，S-3 只按 draft 条件追加一个 `.mc-note`。新弹窗全部使用 S-2 的 `followup-*`，不修改字节锁定的 `mailbox-chat.css`。

## 实现方案

### 阶段 1：服务端接受并验证显式锚点
- 修改 `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`：给 `ConversationManualRichReplyRequest` 增加 `anchorMailRecordId: Long? = null`，按命名参数传给 service。遵守 I-2、I-8。
- 修改 `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`：给 `sendConversationManualRichReply` 增加可空参数。`findCompletedByRequestId` 仍先执行；未命中时，非空 id 使用 `mailRecordRepository.findById(id).orElse(null)`，统一验证 I-2 条件；null 保留 `findLatestSentOutboundAnchor`。校验后的同一 `MailRecord` 继续进入现有 Message-ID、References、账号与 `ManualRichSendSource` 代码。遵守 I-2、I-3、I-4、I-8。
- 修改 `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt`：新增 JSON `anchorMailRecordId:77` 的精确透传断言；保留省略字段为 null、extra `senderAccountCode/qa` 被忽略、401 回归。遵守 I-2、I-8。
- 修改 `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`：先写失败测试，再实现；覆盖选中较旧 id 时不调用 latest 查询、账号/线程头/sourceAnchor 均取所选行；跨 contact、非 OUTBOUND、非 SENT、空/模拟器账号、accountScope 不同均 422 且零 claim/SMTP；字段省略继续取 latest；completed requestId 命中时零锚点读取。遵守 I-2、I-3、I-4、I-8。

### 阶段 2：弹窗、自然正文与草稿状态
- 修改 `src/main/resources/static/mailbox-chat.js`：
  1. 在 `manualComposeHtml` 工具栏末尾加入 S-1 按钮；只在 `selectedSummary.sentCount > 0` 时渲染。
  2. 从 `instance.conversation.items` 派生 I-2 候选，按 `eventAt DESC,id DESC` 展示，不选择首项；`hasMore` 时显示范围说明。
  3. 在既有 portal root 创建一个 S-2 native dialog；选项仅以 `textContent`/`escapeText` 渲染，切换后按 I-6 的确定性纯文本规则转换正文，再生成 I-5 文案与引用预览。
  4. “填入”时设置主题与编辑器 HTML/text、`followUpAnchorMailRecordId`，清 QA/会议；调用 `saveDraftFromInputs` 后关闭并恢复焦点。取消只关闭。
  5. 扩展 draft 读写和 contentChanged 判定；锚点改变清 requestId。`adoptAssembly/applyMeetingFromDialog` 清锚点。
  6. `sendManualReply` 若 draft 有锚点，无论当前 manual.mode 是否 inbound，都构造 conversation request：`requestId/accountScope/anchorMailRecordId/subject/htmlBody/textBody/operatorName`，调用 `mcHostSendConversationRichReply`；无锚点沿原分支。异步前继续捕获 draftsMap/owner/key。
  7. `teardownConversationSubViews`、账号范围变化和 `unmount` 关闭弹窗；成功删除 draft，失败保留。
  遵守 I-1 至 I-8、S-1、S-2、S-3。
- 修改 `src/test/js/meetingConfirmationIntegration.test.js`（仅 S-1 造成的两处既有断言）：
  1. `:1361` 的数组改为只遍历 `.mc-editor-tools .button[data-command]`，仍断言 `["bold","italic","insertUnorderedList","createLink"]` 顺序不变；不再把无 `data-command` 的会议/跟进按钮纳入该数组。
  2. `:1411` 的 `tools.length` 断言改为 6，并断言顺序为四个 `data-command` 按钮、`mc-open-meeting`、`mc-open-followup`（`tools[4]`/`tools[5]` 的 `data-action`）。
  禁止改动该文件其他用例、断言或夹具。遵守 S-1。
- 修改 `src/main/resources/static/styles.css`：只追加 S-2 逐字 CSS；不修改现有规则。遵守 S-2。
- 修改 `src/test/js/mailboxChatBehavior.test.js`：覆盖无默认选择、候选过滤/排序/账号范围、切换锚点、三种正文、问候白名单、完整引用/XSS 转义、填入/取消、QA/会议互斥、draft 恢复、换锚点清 requestId、inbound 页面跟进改走 conversation adapter、失败保留与成功清理、hasMore 提示、切专家关闭。遵守 I-1 至 I-8、S-1、S-3。
- 修改 `src/test/js/mailboxChatStyle.test.js`：逐条断言 S-2 选择器与完整规则块存在于 `styles.css`；继续断言 `mailbox-chat.css` 字节未变、模板无 inline style、所有 `followup-*` class 已声明。遵守 S-1、S-2、S-3。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` | 会话人工回信 DTO 增加可空锚点 id并透传 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 显式锚点权威读取、校验及旧 latest fallback |
| `src/main/resources/static/mailbox-chat.js` | 跟进按钮、选择弹窗、自然正文、引用和 draft/send 分支 |
| `src/main/resources/static/styles.css` | S-2 跟进弹窗样式 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | DTO 透传与兼容测试 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 锚点校验、线程、幂等和旧行为测试 |
| `src/test/js/mailboxChatBehavior.test.js` | 完整前端交互与发送测试 |
| `src/test/js/mailboxChatStyle.test.js` | S-2 逐字样式与 class/inline 守卫 |
| `src/test/js/meetingConfirmationIntegration.test.js` | 重钉 S-1 工具栏结构断言（仅 `:1361`、`:1411` 两处） |

文件数：9。子系统：3（邮件发送链、收发件箱前端、会议确认集成测试）。共享 store 新字段：0。
第 9 个文件由总计划修正 A1（2026-09-14 人工批准）追加：S-1 在 `.mc-editor-tools` 内新增跟进按钮，必然使该文件两处既有断言的“工具条第 N 个按钮”计数失效，而它不在原子计划白名单内。只允许重钉这两处，禁止改动该文件的其他用例。

## 验收标准

- I-1: JS 测试断言打开弹窗后 4 个候选 `aria-checked=false`，apply `disabled=true`；选择 #2893 后只该项为 true。
- I-2: 前端 fixture 混入 INBOUND、FAILED、其他账号和模拟器行，只显示合法 SENT；服务端每种非法 id 均返回 422，`prepareAndClaim/send` 调用数均 0。
- I-3: 选择非最新 #2893、同时 stub 最新 #4007，断言不调用 `findLatestSentOutboundAnchor`，payload `sourceAnchor=MAIL_RECORD:2893`，SMTP `In-Reply-To` 为 #2893 的 Message-ID。
- I-4: 锚点从 2893 改 4007 后 draft requestId 严格为 null；同锚点失败重试 requestId 不变；completed 命中不读 `mail_record`。
- I-5: `待约视频`、`待发简历`、双标签/空标签分别得到计划中的视频、简历、通用正文，正文不含 `September`、`email I sent on` 或 `For reference`；textarea 修改值逐字进入编辑器。
- I-6: #2893 的 subject、quote header、body 与请求 anchor id 同源；`<p>A</p><p>B<br>C</p>` 转成 `A\n\nB\nC`，不显示标签；正文 `<script>` fixture 不生成 DOM script。
- I-7: 取消弹窗前后 QA/meeting/draft 深相等；填入后 QA/meeting 为 null；采用可信草稿或应用会议后 anchor 为 null。
- I-8: 无锚点 inbound 仍只调用 `mcHostSendRichReply`；有锚点 inbound 只调用 `mcHostSendConversationRichReply`；服务测试捕获后续 `executeManualRichSend` 既有 payload 与 SMTP 路径。
- S-1: DOM 中跟进按钮紧随既有 meeting trigger（存在时）且 class 严格为 `button`；`mailbox-chat.css` diff 为 0。
- S-1（A1 追加）: `src/test/js/meetingConfirmationIntegration.test.js` 全部用例通过；组件在场用例断言工具栏 6 个按钮且顺序为四个 `data-command` 按钮、`mc-open-meeting`、`mc-open-followup`。
- S-2: `mailboxChatStyle.test.js` 对计划 CSS 每条规则逐字匹配；桌面网格 44%/56%，760px 以下一列；无 inline style、无未声明 class。
- S-3: 填入后显示 `已引用邮件 #<id> · YYYY-MM-DD HH:mm · <subject>`；不显示 Message-ID/fingerprint。
- 自动命令：`node --check src/main/resources/static/mailbox-chat.js`；`node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js src/test/js/meetingConfirmationIntegration.test.js`；`node --test src/test/js/*.test.js`；`mvn -Dtest=MailboxConversationControllerTest,PendingMailOperationServiceTest test`。功能计划通过后执行缓存激活计划，再跑全量 `mvn test`。

## 人工验收清单

### A-1: 默认不选择
- 前置条件: 当前专家时间线已有至少两封 `SENT` 发件。
- 操作步骤: 1. 打开收发件箱专家会话；2. 点击人工回复工具栏“跟进邮件”。
- 预期结果: 弹窗标题为“生成跟进邮件”；所有邮件均未选中；“填入人工回复”灰显不可点；人工回复原主题和正文不变化。
- 覆盖: I-1、S-1、S-2

### A-2: 切换引用邮件
- 前置条件: 当前专家已加载 #2893 和 #4007 两封 `SENT` 发件。
- 操作步骤: 1. 选择 #2893；2. 查看右侧；3. 改选 #4007。
- 预期结果: 每次只有一项蓝色选中；主题、引用头、完整原文同步切换；主正文不出现发送日期和 `For reference`。
- 覆盖: I-1、I-5、I-6、S-2

### A-3: 待约视频自然正文
- 前置条件: 专家标签只含业务标签“待约视频”，所选邮件账号为 `LiLei`。
- 操作步骤: 1. 选择任一合法发件；2. 查看正文；3. 点击“填入人工回复”。
- 预期结果: 正文包含 `Would you be available sometime this week or next?` 与 `We’re happy to work around your time zone.`；签名为 `LiLei`；完整原邮件位于签名下方引用块。
- 覆盖: I-5、I-6

### A-4: 待发简历自然正文
- 前置条件: 专家标签只含业务标签“待发简历”，所选邮件账号为 `LiLei`。
- 操作步骤: 1. 选择任一合法发件；2. 查看正文；3. 点击“填入人工回复”。
- 预期结果: 正文包含 `When convenient, could you please send your CV?` 与 `It will help us identify suitable industry partners.`；不包含 Zoom 文案或发送日期。
- 覆盖: I-5、I-6

### A-5: 标签歧义回退
- 前置条件: 专家同时具有“待约视频”和“待发简历”，或 `expertTags=null`。
- 操作步骤: 1. 选择合法发件；2. 查看正文。
- 预期结果: 正文严格使用 `Just following up on my email below. Please let me know when you have a chance.`；不猜视频或简历。
- 覆盖: I-5

### A-6: 编辑后发送所选线程
- 前置条件: #2893 和 #4007 均成功发出，#4007 更新；测试 SMTP 可查看原始邮件头。
- 操作步骤: 1. 选择较旧 #2893；2. 修改正文一个单词；3. 填入并发送；4. 查看原始 MIME 和新 `mail_record`。
- 预期结果: 只收到一封；`In-Reply-To` 等于 #2893 的 Message-ID，不等于 #4007；新记录 `in_reply_to` 同值，`source_inbound_id=NULL`；正文包含人工修改。
- 覆盖: I-2、I-3、I-4、I-8

### A-7: 非法/过期选择被拒绝
- 前置条件: 在隔离验收库准备专家 A 的 `SENT` 发件 #2893；打开弹窗并选择 #2893 后，由验收人员执行 `UPDATE mail_record SET send_status='FAILED' WHERE id=2893 AND expert_contact_id=<专家A编号>;`。
- 操作步骤: 1. 保持已填入的主题、正文和锚点；2. 点击“发送人工回复”；3. 查看网络响应与验收邮箱。
- 预期结果: 返回 `422 CONVERSATION_SENT_ANCHOR_NOT_FOUND`；无 SMTP 邮件、无新 attempt、无新 mail_record；草稿和所选 id 保留。
- 覆盖: I-2、I-4、I-8

### A-8: 会议与跟进互斥
- 前置条件: 当前 inbound 草稿已有会议正文和 ICS 快照。
- 操作步骤: 1. 打开跟进弹窗但取消；2. 确认会议仍在；3. 再打开、选择并填入；4. 查看人工回复区。
- 预期结果: 取消后会议正文/附件不变；填入后只保留跟进正文与引用，会议正文和 ICS 卡均消失；提示显示所选邮件 id。
- 覆盖: I-7、S-3

### A-9: 普通回复回归
- 前置条件: 当前专家有来信，未使用跟进按钮。
- 操作步骤: 1. 手工输入普通回复；2. 发送；3. 检查网络请求。
- 预期结果: 只 POST `/api/mail/unmatched-inbound/{processingId}/manual-rich-reply`；没有 `anchorMailRecordId`；原 QA、安全确认和会议能力均可用。
- 覆盖: I-8、需求描述“必须保持”第 2、4、5 项

### A-10: 无来信会话回归
- 前置条件: 专家无来信但有成功发件；不使用跟进弹窗，直接输入正文。
- 操作步骤: 1. 输入主题/正文；2. 发送；3. 查看线程头。
- 预期结果: 会话接口请求不含 `anchorMailRecordId`；服务端仍选择最近 SENT 发件；邮件成功后刷新时间线。
- 覆盖: I-3、I-8、需求描述“必须保持”第 3 项

### A-11: 更早邮件范围提示
- 前置条件: 当前时间线 `hasMore=true`，当前窗口只加载最近 50 条。
- 操作步骤: 1. 打开跟进弹窗；2. 查看列表底部；3. 取消；4. 点击“加载更早信件”；5. 再打开弹窗。
- 预期结果: 第一次明确提示存在更早邮件且不自动加载；加载后旧发件出现在可选列表中。
- 覆盖: I-1、I-2、范围外第 4 项

### A-12: 桌面与窄屏目测
- 前置条件: 分别使用 1280px 与 390px 视口打开弹窗。
- 操作步骤: 1. 对照 S-2 查看布局；2. 用 Tab 遍历选项、正文和按钮。
- 预期结果: 1280px 为 44%/56% 双栏；390px 为单栏；弹窗距视口至少 10px；focus outline 为 `2px solid #3b82f6`；禁用按钮 opacity `.45`；无横向滚动。
- 覆盖: S-2
