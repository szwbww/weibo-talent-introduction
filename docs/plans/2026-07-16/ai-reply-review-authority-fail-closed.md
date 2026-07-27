# P1：AI 草稿审核 authority fail-closed

## 需求描述

- 可观察结果：收发邮件首轮 AI 草稿只有在服务端初稿审计成功后才显示/可采用；发送只认确定性的 current authority；READY 不计人工确认，合法非 READY 确认只计一次。
- 必须不变：训练模拟零写入、模型下拉与 loading、正文变量最终渲染、投递成功路径、QA 选择/ordinal、现有质量指标读取语义。
- 明确不做：不新增表或 migration，不修改 `index.html/styles.css`，不重构其他 operator action、自动回复或人工外联写路径，不解决外部投递成功后审计失败的分布式事务问题。

## 关键不变量

### I-1：无持久化 authority，不暴露可采用 AI 草稿

- 收发邮件首轮生成时，READY、NEEDS_REVIEW、BLOCKED 三种结果都必须先成功写入 `operator_action_log`，再返回草稿正文。
- 任意初稿审计写异常返回 HTTP 200，但响应必须为：`draftText=""`、`renderedDraftText=""`、`draftIdentity=null`、`draftAuthorityAvailable=false`，并在 `contextWarnings` 增加 `AI_REPLY_AUDIT_UNAVAILABLE`。
- 失败响应可以保留 request coverage/readiness/model 等诊断元数据，但不得把 raw、rendered 或可采用的正文放入任何响应字段。
- 训练模拟没有发送语义，保持零审计写入且不增加该 authority 流程。
- 适用：mailbox `aiReplyTurn()` 首轮、`recordInitialDraft()`、`AiReplyTurnResponse`；训练模拟为显式排除路径。
- 违反后果：浏览器可采用一个服务端无法在发送时识别的 AI 草稿。
- 来源：K-ai-review-authority-loss-and-order、K-ai-review-server-authoritative-snapshot。

### I-2：latest authority 顺序唯一

- `operator_action_log.created_at` 是秒级 `DATETIME`；latest 查询必须使用 `ORDER BY created_at DESC, id DESC LIMIT 1`。
- 同一 inbound 同一秒生成两稿时，只接受更大 `id` 的 identity，旧 identity 必须拒绝。（K-ai-review-authority-loss-and-order）
- 适用：`OperatorActionLogRepository.findLatestAiDraftByInboundProcessingId()` 与所有发送校验调用。
- 违反后果：旧稿被接受或新稿被错误拒绝。
- 来源：K-ai-review-authority-loss-and-order。

### I-3：authority 记录必须自洽，损坏即 fail closed

发送校验必须从 action type 推导 expected readiness，并与 JSON `readiness` 精确交叉验证：

| action_type | 唯一合法 readiness |
|---|---|
| `AI_REPLY_DRAFT_READY` | `READY` |
| `AI_REPLY_DRAFT_NEEDS_REVIEW` | `NEEDS_REVIEW` |
| `AI_REPLY_DRAFT_BLOCKED` | `BLOCKED` |

以下任一情况都必须在 mail delivery 前拒绝：afterValue 缺失/非法 JSON、缺失或未知 readiness、action/readiness 矛盾、identity 空、snapshot 类型错误、count 不一致、非 READY snapshot 为空、reviewKey 重复或不等于 `{requestIndex}:{intentKey}`。（K-ai-review-canonical-key-uniqueness）

READY 也必须先完成 authority 自洽校验，不能在读取 JSON 前提前 return。

- 适用：latest action 解析、READY/NEEDS_REVIEW/BLOCKED 三个分支。
- 违反后果：损坏或矛盾记录被当作 READY 放行。
- 来源：K-ai-review-server-authoritative-snapshot、K-ai-review-canonical-key-uniqueness。

### I-4：客户端不能创造或降级 authority

- `replySource`、`draftIdentity`、confirmed keys、operator note 均为客户端声明，不是 authority。
- 服务端存在非 READY latest 记录时，客户端省略 `replySource` 或 confirmation 仍必须拒绝，不能伪装纯人工绕过。
- 服务端没有 AI 初稿记录时，只有 `replySource` 缺失/空且 `aiReviewConfirmation=null` 的纯人工发送可继续；只要提交 confirmation 对象（即使字段全空）、声称 `AI_DRAFT` 或使用未知非空 source 都拒绝。
- 未知非空 `replySource` 拒绝；不默认为 manual。
- 服务端存在 READY authority 时无需人工确认；若客户端声称 AI_DRAFT 并携带 identity，则 identity 必须与 current authority 一致。
- 适用：manual-rich send request 的 `replySource` 与 `aiReviewConfirmation`。
- 违反后果：直接 API 可通过省略/伪造字段绕过非 READY gate。
- 来源：K-ai-review-server-authoritative-snapshot。

### I-5：确认事件只由服务端校验结果触发

`validateConfirmationForSend()` 不再返回 `Unit`，而返回不可由客户端构造的规范结果，例如：

- `MANUAL`：无 AI authority 且请求为纯人工；
- `AI_READY`：current authority 自洽且 readiness=READY；
- `AI_REVIEW_CONFIRMED`：current authority 为 NEEDS_REVIEW/BLOCKED，identity、完整 canonical keys 和必要备注全部通过。

只有 `AI_REVIEW_CONFIRMED` 在投递成功后写 `AI_REPLY_REVIEW_CONFIRMED`。READY、纯人工、仅客户端声称 AI_DRAFT 都不得写该事件，避免污染 Phase 10 指标。

- 适用：`validateConfirmationForSend()` 返回值与 `PendingMailOperationService.recordConfirmed()` 条件。
- 违反后果：客户端制造确认指标，READY 被错误计为人工审核。
- 来源：K-ai-review-authority-loss-and-order、K-audit-selected-source。

### I-6：所有拒绝发生在不可逆写入前

- authority 校验必须位于 `mailDeliveryService.send()`、`mailRecordRepository.save()`、`mailRecordQaRuleRepository.save()` 和外发 operator log 之前。
- 校验失败时上述调用次数均为 0。
- 通过后，现有投递→mail_record→QA 关联→外发审计顺序及正文变量最终渲染保持不变。
- `mail_record_qa_rule` 仍只记录真实 `qaRuleIds`，ordinal 与现有 QA 审计一致。（K-rich-reply-qa-audit-reuse、K-audit-selected-source）
- 适用：Pending manual-rich 校验、delivery、mail/association/action writes。
- 违反后果：拒绝后仍发信/留脏记录，或 QA 使用审计失真。
- 来源：K-rich-reply-qa-audit-reuse、K-audit-selected-source。

### I-7：前端 authority 失败可恢复且不污染会话状态

当 `draftAuthorityAvailable === false`：

- 不调用 `appendAiChatOperatorBubble()` 或 `appendAiChatDraftBubble()`；
- 不写 `aiReplyState.drafts`、lastDraft、lastQaRuleIds、lastDraftIdentity、mode；
- 不将 `firstTurnDone` 设为 true，不清空输入框；
- 在现有 feedback 区显示明确错误并结束 loading；用户可原地再次点击重试。

旧草稿/跨详情状态仍按 draft entry 和模块级 review helper 管理，不引入新的局部 modal 状态。（K-ai-draft-review-state-per-draft、K-ai-reply-modal-helper-scope）

- 适用：mailbox `ai-reply-turn` 成功/authority failure/stale/error 分支。
- 违反后果：失败响应锁死首轮、清空用户输入或产生空草稿采用按钮。
- 来源：K-ai-draft-review-state-per-draft、K-ai-reply-modal-helper-scope。

## 样式契约

### S-1：authority 错误反馈

- 复用：`.ai-reply-feedback`（`styles.css:5876`）与 `.ai-reply-error`（`styles.css:5902`）。前者为 column、`6px` gap、`10px` bottom margin；后者为 `8px 10px` padding、`1px solid var(--error-border)`、`var(--radius-sm)`、`var(--error-bg)`、`var(--error)`、`12px`、`line-height:1.5`。token 实值：error `#e11d48`、background `rgba(225,29,72,.07)`、border `rgba(225,29,72,.16)`、radius `7px`。
- 新增：无 CSS、无 class、无 DOM 元素。
- DOM 结构固定为：

```html
<div id="aiReplyFeedback" class="ai-reply-feedback" role="status" aria-live="polite" hidden></div>
```

- authority failure 时只在该容器生成一次 `<div class="ai-reply-error">AI 草稿审核记录保存失败，本次草稿未提供。请重试生成。</div>`；文案经 `escapeHtml()`。
- 禁止项：修改 `.ai-reply-feedback/.ai-reply-error`；新增近似错误 class；inline style；显示 exception、warning code、SQL 或 audit JSON。

### S-2：loading 与模型控件

- 复用：`.ai-chat-panel`（`styles.css:5796`）、`.ai-reply-model-row`（`:5804`）、`.ai-reply-model-select` 及 focus/disabled（`:5822/:5832/:5838`）、`.ai-reply-loading-overlay`（`:5844`）、`.ai-reply-loading-spinner`（`:5861`）。
- 实值：panel border 为 `rgba(15,23,42,.08)`、radius `10px`、白底、`14px` padding、relative；overlay 为 absolute inset 0、z-index 6、`rgba(255,255,255,.84)`、blur `2px`；disabled select 为 not-allowed、opacity `0.65`、`#f8fafc`；focus 主色 `#2563eb`。
- 新增：无 CSS、无 class、无 DOM 元素。
- DOM 层级保持 `.ai-chat-panel > .ai-reply-model-row + #aiReplyFeedback + #aiChatMessages + .ai-chat-input-row`；loading overlay 仍由 `setAiReplyLoading()` 临时追加为 panel 直接子元素。
- 禁止项：修改模型 select 尺寸/焦点/禁用态，修改 overlay 动画/层级，新增另一套遮罩，修改 `index.html` 或 `styles.css`。

## 现状审计

### 初稿生成与响应

- `UnmatchedInboundMailController.aiReplyTurn()` 仅在 `request.turns.isEmpty()` 调用 `recordInitialDraft()`。
- `recordInitialDraft()` 当前捕获异常并返回 null；controller 随后仍 preview 并返回完整 `draftText/renderedDraftText`，仅 `draftIdentity` 为空。
- 前端收到响应后无 authority 分支，立即追加 operator/draft bubble、保存 last state、设置 `firstTurnDone=true` 并清空输入。

### 发送 gate 与确认事件

- `PendingMailOperationService.sendManualRichReply()` 已在投递前调用 `validateConfirmationForSend()`，位置正确。
- 当前无 latest 记录时直接 return；缺 `readiness` 时也直接 return；READY 在 identity/snapshot 校验前 return。
- 发送成功后只要客户端带 identity 或 `replySource == "AI_DRAFT"` 就调用 `recordConfirmed()`，与服务端是否真正确认非 READY snapshot 无关。

### `operator_action_log` store

- Schema/mapping：V19；`id BIGINT AUTO_INCREMENT`，`action_type VARCHAR(64)`，`after_value TEXT`，`created_at DATETIME DEFAULT CURRENT_TIMESTAMP`；inbound/created_at 有普通索引但时间精度只有秒。
- Write paths：唯一 repository save 在 `OperatorActionLogService.record()`；调用者为 `BounceController`、`ExpertOperatorStatusService`、`ExpertIndexLevelOperationService`、`ExpertContactManagementService`、`UnmatchedInboundMailService`、`PendingMailOperationService`、`AiReplyReviewAuditService`。本轮只改最后一项 initial/confirmed 语义，其他调用保持。
- Read paths：`OperatorActionLogService.search()` 供操作日志页；`QaRuleAuditService` 用 search/countSearch 聚合 QA 与 AI readiness/blocked/confirmed 指标；`AiReplyReviewAuditService` 用 latest 查询建立发送 authority。
- Interaction points：initial write→controller 暴露草稿；latest read→Pending 发送 gate；validated result→confirmed write→`QaRuleAuditService` 指标。

### `mail_record` store

- Schema/mapping：V1；outbound 关键字段为 contact/direction/mail_type/message/subject/body/matched rule/send status/sent_at。
- Write paths：`ManualOutreachTxHelper`（初次外联及记录）、`MeetingScheduleService`（会议邀请）、`AutoMailReplyService`（inbound/auto outbound/其他自动记录）、`ManualExpertMailService`（人工回复）、`PendingMailOperationService`（QA、manual-rich、其他 pending send）。
- Read paths：mailbox/detail/history (`MailboxService`、`UnmatchedInboundMailController/Service`、`InboundMailSummaryController`、`ExpertContactManagementService`)；训练/抽取 (`AiTrainingController`、`AiQaExtractionService`)；自动回复/晋升/退信；document browse/extract；monitoring/reporting。
- Interaction points：本计划只把 Pending authority gate 固定在其 manual-rich `mailRecordRepository.save()` 之前；合法路径字段和其他 writers/readers不变。

### `mail_record_qa_rule` store

- Schema/mapping：V42；`mail_record_id`、`qa_rule_id`、`ordinal`，唯一 `(mail_record_id, qa_rule_id)`，两个外键均 RESTRICT。
- Write paths：`ManualExpertMailService`、`AutoMailReplyService`、`PendingMailOperationService` 两个 QA-carrying 分支。
- Read paths：`QaRuleAuditService.resolveSelectedRuleIds()` 按 ordinal 读取真实选用规则。
- Interaction points：Pending 校验拒绝时不得写；合法发送保持原 `qaRuleIds.forEachIndexed`；指标仍以关联表为准。（来源: K-rich-reply-qa-audit-reuse、K-audit-selected-source）

本轮不增加持久化字段：`draftAuthorityAvailable` 仅为 response DTO 字段；initial/confirmed action 继续写既有 `after_value` JSON，mail 与 QA 关联字段在校验失败时全部跳过、成功时保持原值与原顺序。

### 前端样式盘点

- 可复用 class：`.ai-chat-panel`（`styles.css:5796-5802`）、`.ai-reply-model-row`（`:5804-5810`）、`.ai-reply-model-select` 及 focus/disabled（`:5822-5842`）、`.ai-reply-loading-overlay/.ai-reply-loading-spinner`（`:5844-5868`）、`.ai-reply-feedback`（`:5876-5881`）、`.ai-reply-error`（`:5902-5910`）。这些 class 在本计划全部复用、不就地修改、不派生新 class。
- 设计基准 token/实值：错误色 `#e11d48`、背景 `rgba(225,29,72,.07)`、边框 `rgba(225,29,72,.16)`；主色 `#2563eb`；radius `7px/10px`；字号 `12px`；panel padding `14px`；overlay z-index `6`；disabled opacity `0.65`。
- DOM 结构约定：mailbox panel 由 `renderAiReplyPanelHtml()` 动态生成；反馈容器 id 唯一；loading overlay 由 helper 临时作为 panel 直接子节点；不修改 `index.html` 注册。
- 改动前 HTML 基线（`app.js:8933-8947`）：

```html
<div class="ai-chat-panel">
    <div class="ai-reply-model-row">...</div>
    <div id="aiReplyFeedback" class="ai-reply-feedback" role="status" aria-live="polite" hidden></div>
    <div id="aiChatMessages" class="ai-chat-messages"></div>
    <div class="ai-chat-input-row">...</div>
</div>
```

- 改动前 CSS 基线：

```css
.ai-reply-feedback {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-bottom: 10px;
}

.ai-reply-error {
    padding: 8px 10px;
    border: 1px solid var(--error-border);
    border-radius: var(--radius-sm);
    background: var(--error-bg);
    color: var(--error);
    font-size: 12px;
    line-height: 1.5;
}
```

### 历史修复约束

`docs/plans/fix/ai-reply-08-p2-review-audit-backend/fix-3.md` 已列出 3 个 P1。该文件明确禁止未收敛后创建 fix-4；本计划以 authority lifecycle 为新边界重做，并保留 Phase 8 已通过的 payload bounds、canonical key 和零写入约束。（K-review-event-audit-payload-bounds）

## 实现方案

### T1：确定性 latest 查询

约束：I-2、I-3。文件：`OperatorActionLogRepository.kt`、`OperatorActionLogRepositoryTest.kt`。现有 `AiReplyReviewAuditService` 是该 read path 唯一 authority 消费者；所有 operator action writers 无需调整。

- 修改 `findLatestAiDraftByInboundProcessingId()` 为 `ORDER BY created_at DESC, id DESC LIMIT 1`。
- 新增 repository 回归测试：插入同 inbound、同 created_at、不同 id 的两条合法初稿，断言返回较大 id；不同 inbound 不串记录。
- 不改 V19、不增加 timestamp 精度 migration。

### T2：显式初稿 authority 结果

约束：I-1、I-3。文件：`AiReplyReviewAuditService.kt`、`AiReplyReviewAuditServiceTest.kt`。该写入继续由 `OperatorActionLogService.record()` 落到既有 after JSON，controller 消费显式结果。

在 `AiReplyReviewAuditService` 将 `recordInitialDraft()` 返回值从 nullable identity 改为显式结果对象：

- 成功：`available=true` + nonblank identity；
- 失败：记录安全日志，`available=false` + null identity；不抛内部异常给浏览器。

所有 readiness 使用相同规则；不再把 null 同时表示“无需 identity”和“持久化失败”。保存的 after JSON 继续包含 model/mode/count/readiness/generationState/snapshot，并保持 SEND_BLOCKED payload 上限。

### T3：controller 原子暴露草稿

约束：I-1、I-7。文件：`UnmatchedInboundMailController.kt`、`UnmatchedInboundAiReplyTurnKnowledgeTest.kt`。

- 首轮调用 T2 结果；只有 available 才 preview 和返回 raw/rendered draft。
- unavailable 时返回空正文、null identity、`draftAuthorityAvailable=false`，warning 合并保持顺序且去重。
- `AiReplyTurnResponse` 新增 `draftAuthorityAvailable: Boolean = true`。该字段只属于 mailbox response；训练模拟 DTO 不变。
- continuation 保持既有 identity/session 语义；直接伪造 continuation 无 authority 最终仍由发送 gate 拒绝。

### T4：解析 canonical authority 并返回校验结果

约束：I-2、I-3、I-4、I-5。文件：`AiReplyReviewAuditService.kt`、`AiReplyReviewAuditServiceTest.kt`。输入由 existing initial action write feed；输出只由 Pending 消费。

重构 `validateConfirmationForSend()`：

1. 接收 `replySource` 与完整 confirmation，而非只收拆散字段。
2. latest 为空：严格验证纯人工条件并返回 `MANUAL`，否则拒绝。
3. latest 存在：从 action type 映射 expected readiness；严格解析 after JSON，并校验 readiness、identity、unresolvedCount/snapshot、canonical keys。
4. READY：返回 `AI_READY`；AI_DRAFT 请求携带 identity 时校验 current identity，confirmation keys/note 不得伪装人工审核。
5. NEEDS_REVIEW/BLOCKED：无论 replySource 是否省略，都要求 current identity、确认 keys 与 canonical set 完全相等且无重复；BLOCKED note 保持最少 5 字符；成功返回 `AI_REVIEW_CONFIRMED`。
6. 任何损坏或不一致抛 `IllegalArgumentException`，由现有 API 错误处理返回 4xx；不得 return 放行。

规范分支必须满足：

| latest authority | 客户端声明 | 校验结果 | 可投递 | REVIEW_CONFIRMED |
|---|---|---|---|---|
| 无 | source 空、confirmation null | `MANUAL` | 是 | 否 |
| 无 | AI/未知 source 或任意 confirmation 对象 | 拒绝 | 否 | 否 |
| READY 且自洽 | manual 或 current AI identity | `AI_READY` | 是 | 否 |
| NEEDS_REVIEW/BLOCKED 且自洽 | identity/keys/note 完整 | `AI_REVIEW_CONFIRMED` | 是 | 是 |
| 任意损坏/不一致 | 任意 | 拒绝 | 否 | 否 |

### T5：Pending 只消费服务端规范结果

约束：I-4、I-5、I-6。文件：`PendingMailOperationService.kt`、`PendingMailOperationServiceTest.kt`。合法路径继续供 mail/history/monitoring 和 `QaRuleAuditService` 读取，无字段调整。

- 在 `mailDeliveryService.send()` 前取得 T4 结果。
- 所有现有变量渲染、投递、mail record、QA 关联和外发审计逻辑保持。
- 投递成功后仅 `AI_REVIEW_CONFIRMED` 调用 `recordConfirmed()`；传入服务端已校验的 identity/keys/note，禁止重新使用未验证客户端条件决定 action type。
- READY 与 MANUAL 不写 `AI_REPLY_REVIEW_CONFIRMED`。

### T6：前端 authority 失败分支

约束：I-1、I-7、S-1、S-2。文件：`app.js`、`aiReplyReviewConfirmation.test.js`。

- 在模型一致性检查之后、追加任何 bubble/写 state 之前检查 `result.draftAuthorityAvailable === false`。
- 调用现有 feedback 渲染：`AI_REPLY_AUDIT_UNAVAILABLE` 固定使用 `.ai-reply-error` 且不再由通用 warning loop 重复渲染；保留 textarea，显示失败 status 后 return；finally 继续解除 loading/inFlight。
- 在 `AI_REPLY_WARNING_LABELS` 注册中文文案；不新增 HTML/CSS。
- 成功路径逐行保持原顺序，模型不一致、并发 stale response、API error 分支不变。

### T7：回归测试矩阵

约束：I-1 至 I-7、S-1、S-2。文件仅限变更清单中的 5 个测试文件；`QaRuleAuditServiceTest` 只运行、不修改。

- Service：audit 写成功/失败显式结果；action/readiness mismatch、missing readiness/identity/snapshot/count、重复/非法 key、no-row manual/伪 AI、READY、NEEDS_REVIEW、BLOCKED。
- Controller：三种 readiness audit 失败均 HTTP 200 + 空正文 + authority false；成功返回 true + identity；训练模拟不受影响。
- Pending：所有拒绝均验证 delivery/mail record/QA association/action log 零调用；READY 不 recordConfirmed；非 READY 完整确认只记录一次。
- Frontend：authority false 不追加 bubble、不设置 firstTurnDone、不清 input、不写 adopt state；loading 恢复且可再次触发；成功路径不回归。
- 运行现有 `QaRuleAuditServiceTest`，确认质量指标读取逻辑无需修改且不会收到伪确认事件。

## 变更文件清单（10）

| 文件 | 操作 | 目的 |
|---|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/audit/repository/OperatorActionLogRepository.kt` | 修改 | latest 加 `id DESC` tie-break |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt` | 修改 | 初稿 authority 结果、canonical parser、发送校验结果 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 修改 | 原子暴露、authority 字段与 warning |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 修改 | 发送前消费 authority、发送后规范确认事件 |
| `src/main/resources/static/app.js` | 修改 | authority 失败恢复分支和 warning 文案 |
| `src/test/kotlin/com/weibo/talentintroduction/audit/repository/OperatorActionLogRepositoryTest.kt` | 新增 | 同秒双稿 latest 查询回归 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt` | 修改 | fail-closed authority 矩阵 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt` | 修改 | controller 草稿暴露与模拟边界 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 修改 | 投递前拒绝与确认事件语义 |
| `src/test/js/aiReplyReviewConfirmation.test.js` | 修改 | 浏览器状态恢复及成功回归 |

文件数 10，两个子系统：审核/邮件后端与 mailbox 前端。`styles.css`、`index.html`、migration、`QaRuleAuditService` 不修改。若 repository 集成测试需要改变生产 schema 或第三套测试基础设施，停止并改为独立测试计划。

## 验收标准

- I-1：任意 readiness 的初稿审计异常，响应正文两字段为空、identity null、authority false、warning 存在；前端无草稿气泡和 adopt state。
- I-2：同 created_at 的两条记录返回更大 id；旧 identity 发送失败，新 identity 按 readiness 校验。
- I-3：缺失/非法/矛盾 authority 的每个用例都在 delivery 前返回 4xx；READY 损坏也不能绕过。
- I-4：无记录 + source 空 + confirmation null 允许；无记录 + 任意 confirmation 对象/AI_DRAFT/未知 source 拒绝；已有非 READY + 省略 source/confirmation 拒绝。
- I-5：READY 和纯人工发送后 `AI_REPLY_REVIEW_CONFIRMED` 增量为 0；合法非 READY 确认增量为 1。
- I-6：所有拒绝用例中 mail delivery、`mail_record`、`mail_record_qa_rule` 与外发 action log 均无写入；合法路径原有正文和 QA ordinal 不变。
- I-7：authority false 时 textarea 保留、`firstTurnDone=false`、last/drafts 不变、loading 消失；再次点击能成功生成并进入正常 adopt 流程。
- S-1：源码/DOM 测试断言 authority 文案恰好一次且使用 `.ai-reply-error`；无 raw code/exception；`git diff -- index.html styles.css` 为空，无新增 class/inline style。
- S-2：DOM 测试覆盖 loading 开始/失败 finally/重试成功，select 的 disabled 状态恢复；panel 层级、overlay class 与模型控件源码保持基线。
- `AiReplyReviewAuditServiceTest`、`OperatorActionLogRepositoryTest`、`UnmatchedInboundAiReplyTurnKnowledgeTest`、`PendingMailOperationServiceTest`、`QaRuleAuditServiceTest` 通过。
- `node --check src/main/resources/static/app.js` 与 `node --test src/test/js/*.test.js` 通过。
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 通过。

## 人工验收清单

### A-1：初稿审计失败与恢复

- 前置：测试环境提供一次性 initial audit fault 开关；准备任一真实 inbound；开关第一次失败、第二次恢复。
- 操作：1）打开收件详情；2）输入 `Please answer point by point`；3）点击“生成 / 继续修改”；4）失败后不刷新页面再次点击。
- 预期：第一次无 operator/draft bubble、无采用按钮，输入仍为原值，loading 消失，只显示 `AI 草稿审核记录保存失败，本次草稿未提供。请重试生成。`；第二次出现正常草稿与采用按钮。
- 覆盖：I-1、I-7、S-1、S-2；initial write→controller→frontend interaction。

### A-2：同秒双稿

- 前置：同一 inbound 写入 created_at 相同、id/identity 不同的两条合法 NEEDS_REVIEW authority。
- 操作：1）记录两条 id；2）用较小 id 的 identity 调用发送 API；3）用较大 id 的 identity 和完整 canonical keys 重试。
- 预期：旧 identity 返回 4xx 且无投递/mail record；新 identity 成功并只发送一封。
- 覆盖：I-2、I-4、I-6；latest read→send gate interaction。

### A-3：损坏 authority

- 前置：分别准备缺 readiness、action/readiness 矛盾、非法 JSON、重复 reviewKey 四条记录。
- 操作：1）对四个 inbound 分别调用 manual-rich send API；2）查询投递 stub、`mail_record`、`mail_record_qa_rule`、相关 action logs。
- 预期：四次均 4xx；投递数 0；新增 mail record、QA 关联、外发/确认日志均为 0。
- 覆盖：I-3、I-6；corrupt authority read→write gate interaction。

### A-4：无记录纯人工与伪 AI

- 前置：inbound 没有 AI draft action。
- 操作：1）对第一条发送 `replySource=null, aiReviewConfirmation=null` 的人工正文；2）对第二条发送 `replySource=AI_DRAFT`；3）对第三条发送 source 空但 confirmation `{}`。
- 预期：第一条成功且 REVIEW_CONFIRMED 增量 0；第二、三条均 4xx 且投递数 0。
- 覆盖：I-4、I-5、I-6；pure manual 与 direct API bypass。

### A-5：READY 与非 READY 指标

- 前置：准备一条 READY、一条 NEEDS_REVIEW；两条均携带按 `[ruleA, ruleB]` 顺序选用的 QA；记录质量指标和关联表基线。
- 操作：1）发送 READY 草稿；2）完整确认 NEEDS_REVIEW keys 后发送；3）刷新 QA 质量指标并查询两个 mail record 的关联表。
- 预期：两封均成功；REVIEW_CONFIRMED 只增加 1；两个 mail record 的 QA ordinal 均为 `ruleA=0, ruleB=1`；READY 不计人工确认。
- 覆盖：I-5、I-6；validated result→confirmed log→metric 及 mail→QA audit interaction。

### A-6：样式与交互回归

- 前置：浏览器分别设为 1440px 与 390px 宽；一次性 audit fault 可开关。
- 操作：1）每个宽度各执行一次失败和一次成功生成；2）观察 panel、error、select disabled、overlay；3）检查 DOM class。
- 预期：错误块为 `12px`、`8px 10px` padding、error token 色；panel padding `14px`；overlay z-index `6` 且结束后移除；select 失败后恢复；DOM 无新 class/inline style，页面不显示内部 code。
- 覆盖：I-7、S-1、S-2；必须不变项。

### A-7：训练模拟保持零写入

- 前置：记录 `operator_action_log`、`mail_record`、`mail_record_qa_rule` 行数；准备同一专家邮件用于训练模拟。
- 操作：1）在 AI 回复训练选择 Pro 模型；2）生成一次模拟回复；3）重新查询三表行数。
- 预期：模拟回复正常返回；三表行数全部与操作前相同；response 不要求 mailbox `draftIdentity`。
- 覆盖：I-1、I-6；训练模拟必须不变项。
