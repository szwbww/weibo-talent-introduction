# AI 草稿变量渲染预览

## 需求描述

Observable outcome：训练页和邮箱显示/复制/采用已按当前专家和回信账号渲染的草稿，不再显示 `${expertName|Professor}` 等字面模板；内部仍保留 raw draft 供续轮模型和最终发送重渲染。  
What must NOT change：raw draftText 契约、现有 `/api/qa/render-preview` 响应、mailRecordId/模型竞态、草稿正文与反馈隔离。  
Out of scope：最终发送渲染（计划 6）、新增 sender 选择器、修改变量语法、持久化 preview。

## 关键不变量

### Invariant I-1: raw 与 rendered 双表示
- Rule: response 现有 `draftText` 继续是 raw template；新增 `renderedDraftText` 仅用于显示/复制/采用。续轮 `assistantDraft` 必须传 raw，不能把专家具体值反馈进模板链。
- Applies to: 两 response、前端 state/turn payload。
- Violation consequence: 模板语义丢失或续轮混用具体专家数据。
- 来源: K-preview-draft-raw-before-render

### Invariant I-2: 预览使用真实 contact/account
- Rule: 预览只能调用 `MailVariableService.renderPreview(raw, resolvedAccount, contact)`；account 从当前入站记录 senderAccountCode 精确读取。account 不存在时返回 raw 作为 rendered 并追加 warning，禁止用空变量抹掉签名。
- Applies to: simulate、mailbox。
- Violation consequence: senderName/teamName 变空或与实际回信账号不一致。
- 来源: original

### Invariant I-3: 两入口共享 preview service
- Rule: controller 不各写变量解析；统一 `AiReplyDraftPreviewService`。未知 placeholder 保留 raw token并返回 warning，不静默删除。
- Applies to: 两 controller。
- Violation consequence: 训练预览和邮箱草稿不同。
- 来源: K-render-preview-response-consumers

### Invariant I-4: 显示/采用 rendered，续轮 raw
- Rule: bubble、训练 copy、邮箱 adopt 使用 renderedDraftText；`operatorTurns.assistantDraft` 使用 raw draftText；两个值分别存储，不得复用一个 `lastDraft`。
- Applies to: app.js state/action。
- Violation consequence: 用户看到占位符或模型续轮丢模板。
- 来源: original

### Invariant I-5: API 只读
- Rule: preview 不 save/contact update/mail send/enrichment；模拟仍不产生业务写入。
- Applies to: preview service/controllers。
- Violation consequence: 点击生成修改业务状态。
- 来源: K-ai-simulate-exact-mail-id

## 样式契约

### S-1: 渲染草稿展示（纯复用）
- 复用：`.pre`（`styles.css:1571-1582`，保留换行）、`.ai-chat-bubble .pre`（`styles.css:5946-5950`）、`.ai-reply-warning`（`styles.css:5892-5900`）。
- 新增：无 CSS、无新 class、无新 DOM。
- DOM：现有 draft bubble 不变，只替换传给 `translatableBody` 的字符串；warning 仍进入既有 feedback。
- 禁止项：inline style；修改 `.pre`；把 raw/rendered 同时显示在正文中。

## 现状审计

### 模板变量 preview（只读）
- Schema: 无持久化；MailVariableService 从 contact + ES profile + sender account 构建变量。
- Write paths: 无；`renderPreview` 纯计算。
- Read paths: `/api/qa/render-preview` 两个既有前端消费者；本计划新增 AI preview service 直接调用 service，不改变旧 endpoint。（来源: K-render-preview-response-consumers）
- Interaction points: fallback placeholder 必须从 raw 文本解析；先 render 再检测会丢 fallbackKeys。（来源: K-preview-draft-raw-before-render）

### 前端样式盘点
- 基线：训练/邮箱均用 `translatableBody(draftText)`；`.pre` 已 `white-space:pre-wrap`。
- 状态：aiReplyState 只有 lastDraft；训练 state 只有 simulateResult。
- 可复用 warning/preview class 如 S-1；零 CSS 变更。

## 实现方案

### T1：共享 preview service（I-1/I-2/I-3/I-5）
文件：新增 `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftPreviewService.kt`

- 注入 MailVariableService/MailSenderAccountRepository。
- 输入 raw/contact/senderAccountCode；account 找到时调用 renderPreview。
- 返回 renderedText + warningCodes；invalidTokens 对应 `AI_REPLY_PREVIEW_INVALID_PLACEHOLDER`；account missing 对应 `AI_REPLY_PREVIEW_ACCOUNT_NOT_FOUND` 且 rendered=raw。

### T2：双 controller 加法响应（I-1/I-2/I-3/I-5）
文件：
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`

- 调共享 service；response 新增 renderedDraftText；contextWarnings 合并 preview warnings。
- draftText 原值、selectedModel/generationState/coverage 不改。

### T3：前端双值 state（I-1/I-4/S-1）
文件：`src/main/resources/static/app.js`

- 训练 bubble/copy 使用 renderedDraftText ?: draftText。
- mailbox state 拆成 `lastDraftTemplate` 与 `lastRenderedDraft`；bubble/adopt 用 rendered，turn payload 用 raw。
- reset/竞态/model snapshot 逻辑保持。
- warning code 增加中文映射。

### T4：测试（I-1 至 I-5/S-1）
文件：
- 新增 `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftPreviewServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`
- `src/test/js/aiReplyLoadingFeedback.test.js`

- 专家名 fallback、sender 签名、unknown token、account missing；两 response；raw/rendered 前端路由隔离。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftPreviewService.kt` | 共享预览（新增） |
| `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | rendered response |
| `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | rendered response |
| `src/main/resources/static/app.js` | raw/rendered 双 state |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftPreviewServiceTest.kt` | service 测试（新增） |
| `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | simulate 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt` | mailbox 测试 |
| `src/test/js/aiReplyLoadingFeedback.test.js` | 前端路由契约 |

## 验收标准

- I-1：draftText 保留 token，renderedDraftText 无已知 token。
- I-2：sender/account 精确；account missing 不抹空且有 warning。
- I-3：两个 controller 只调用共享 service。
- I-4：测试证明 display/copy/adopt=rendered，turn=raw。
- I-5：preview service 无 repository save/HTTP/enrichment。
- S-1：styles/index 无 diff，现有 DOM/class 不变。
- 命令：`mvn -Dtest=AiReplyDraftPreviewServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest test`；`node --test src/test/js/aiReplyLoadingFeedback.test.js`。

## 人工验收清单

### A-1: 训练页显示实际变量
- 前置条件: 专家名 Janmeda，入站账号配置 sender/team/country。
- 操作步骤: 生成模拟回复并复制。
- 预期结果: 显示/复制内容包含 `Dear Janmeda` 和实际签名；不含 `${`。
- 覆盖: I-1/I-2/I-4/S-1

### A-2: 邮箱续轮保持模板链
- 前置条件: 首轮生成后输入“语气更正式”。
- 操作步骤: 继续修改并采用草稿。
- 预期结果: UI 两轮均显示实际变量；续轮请求内部仍使用 raw template；采用内容无 `${`。
- 覆盖: I-1/I-4

### A-3: 缺失账号安全降级
- 前置条件: 测试入站 senderAccountCode 不存在。
- 操作步骤: 生成草稿。
- 预期结果: 不删除签名占位符；显示“无法确定回信账号，变量预览未完全渲染”warning；无写入。
- 覆盖: I-2/I-5

## 修正记录

| 日期 | 修正项 | 原约束 | 修正后约束 | 原因 | 来源 |
|---|---|---|---|---|---|
| 2026-07-13 | I-1/I-4 跨计划采用边界 | 采用时只把 `renderedDraftText` 放入富文本编辑器；raw 仅留在 AI 续轮 state | AI 采用必须在保留 rendered 编辑体验的同时，保留可供最终发送链使用的 raw 模板及 rendered baseline；未编辑时最终发送传递 raw 并按最终 sender/contact 重渲染，编辑后不得把陈旧 raw 覆盖用户编辑 | 现有 `app.js` 只发送 editor 的 rendered html/text，Phase 6 无法兑现“最终账号权威” | `docs/plans/fix/ai-reply-05-rendered-preview/fix-1.md` P1-1 |
