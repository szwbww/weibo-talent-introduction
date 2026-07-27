# 采用 AI 草稿后直接人工发送：开发计划

## 需求描述

在「待处理邮件详情」中，AI 生成草稿仅用于展示与质量统计；不会阻止人工发送。操作员点击每条草稿的「采用此草稿」后，将该草稿复制到人工富文本编辑器；点击「发送人工回复」即走既有人工邮件发送链路，不显示审核弹窗，不要求 `draftIdentity`、确认项或审核说明。

必须保持：

- 未生成或未采用 AI 草稿时，人工富文本回复仍可发送。
- 已采用草稿仍保留 raw 模板与 rendered text/HTML baseline；未编辑时最终发送按现有最终账号/联系人重新渲染，编辑后按编辑器内容发送。（来源: K-ai-preview-raw-adoption-boundary）
- 主题、HTML 正文、最终变量渲染、账号选择、QA 关联、`mail_record`、`SEND_MANUAL_*` 外发日志的现有语义不变。（来源: K-manual-rich-render-before-send、K-rich-reply-qa-audit-reuse）
- AI 初稿 READY/NEEDS_REVIEW/BLOCKED 的生成日志和质量统计保留；日志写入失败不能影响草稿展示、续轮生成或邮件发送。

不在本计划内：

- 修改 AI 生成、意图识别、`draftReadiness` 计算或 QA 覆盖率。
- 修改自动回复、训练模拟、邮件变量渲染、富文本工具栏、CSS 样式或数据库 migration。
- 删除历史 `AI_REPLY_SEND_BLOCKED` / `AI_REPLY_REVIEW_CONFIRMED` 枚举值、历史日志、质量接口兼容字段及 repository 旧查询；它们不再有运行时写入者，后续破坏性清理另立计划。

## 关键不变量

### Invariant I-1：生成日志只作观测，不作外发或展示闸门

- Rule：mailbox 首轮 AI 生成仍 best-effort 写 READY/NEEDS_REVIEW/BLOCKED 初稿日志；无论该写入成功、失败或历史日志损坏，controller 都返回本次生成的草稿并允许续轮与人工发送。
- Applies to：`AiReplyReviewAuditService.recordInitialDraft(...)`、`UnmatchedInboundMailController.aiReplyTurn(...)`、前端 AI 草稿成功分支。
- Violation consequence：监控/审计异常再次导致草稿空白、无法采用，或把历史 BLOCKED 变成发送阻塞。
- 来源：新需求；K-ai-review-authority-loss-and-order 仅适用于已废弃的审核闸门，不能再用于人工外发决策。

### Invariant I-2：采用是显式的、逐草稿的编辑器复制操作

- Rule：只有点击当前草稿条目的「采用此草稿」才会写入 `aiReplyState.adoptContext` 并填充人工编辑器；AI 生成、详情重开、草稿切换都不得自动填充。采用不发请求、不写审核事件。
- Applies to：`appendAiChatDraftBubble(...)`、`ai-adopt-draft` action、`resetAiReplyState(...)`、`showUnmatchedDetail(...)`。
- Violation consequence：运营误将非目标草稿发出，或历史草稿状态污染当前编辑器。
- 来源：K-ai-draft-review-state-per-draft、K-ai-preview-raw-adoption-boundary。

### Invariant I-3：人工富文本发送不读取 AI readiness 或审核信息

- Rule：`sendManualRichReply(...)` 只执行既有输入、规则、账号与最终渲染校验；不得查询最新 AI 草稿，不得要求 `draftIdentity`，不得因 READY/NEEDS_REVIEW/BLOCKED 拒绝。请求 DTO 中已废弃的审核字段不再由前端发送，也不参与服务端决策。
- Applies to：`PendingMailOperationService.sendManualRichReply(...)`、`PendingManualRichReplyRequest`、manual-rich controller 透传、浏览器 send action。
- Violation consequence：历史 BLOCKED 草稿再次让纯人工或已采用草稿出现 400；或绕过既有邮件渲染/QA 审计。
- 来源：新需求；K-manual-rich-render-before-send、K-rich-reply-qa-audit-reuse。

### Invariant I-4：审核 UI 与审核写入路径不可达

- Rule：页面不存在 AI 审核确认弹窗、checkbox、说明输入或 review-event 调用；后端不暴露 review-event endpoint，不在成功发送后写 `AI_REPLY_REVIEW_CONFIRMED`，也不写 `AI_REPLY_SEND_BLOCKED`。
- Applies to：`index.html`、`app.js`、`UnmatchedInboundMailController`、`AiReplyReviewAuditService`、`PendingMailOperationService`。
- Violation consequence：运营仍被迫审核，或质量报表继续产生“拦截/人工确认”新数据。
- 来源：新需求。

### Invariant I-5：质量面板只呈现初稿质量，不呈现已废弃审核指标

- Rule：质量面板保留初稿总数、READY 完整率、NEEDS_REVIEW 部分覆盖率、BLOCKED 遗漏率；移除“直发拦截”和“人工确认”卡片。后端兼容响应字段可保留，前端不得展示。
- Applies to：`renderQaAuditPanel(...)`；初稿 action write → `QaRuleAuditService.aggregateAiReplyQualityMetrics(...)` read。
- Violation consequence：停用审核后，界面仍展示无法再发生或仅代表历史数据的指标。
- 来源：新需求。

## 样式契约

### S-1：删除审核弹窗，不新增替代 UI

- 复用：AI 草稿采用按钮继续使用既有 `.ai-chat-draft-actions`（`src/main/resources/static/styles.css:5952-5954`）和 `.button.small.primary`；人工编辑器继续使用 `.rich-editor`（`styles.css:2727-2744`）。
- 删除：`index.html:1872-1890` 的 `#aiReplyReviewModal` 及其子节点；`app.js` 的审核状态机与绑定事件。
- 新增：无 CSS、无 class、无 DOM 元素、无 inline style。
- DOM 结构：AI 草稿条目保持 `.ai-chat-bubble.ai-chat-assistant > .ai-chat-draft-actions > button[data-action="ai-adopt-draft"]`；人工编辑器保持 `#manualRichReplyEditor.rich-editor` 与 `button[data-action="send-manual-rich-reply"]`。
- 禁止项：修改 `styles.css`；新增审核提示、二次确认弹窗、阻塞遮罩或近似按钮样式。

## 现状审计

### `operator_action_log`（AI 初稿质量日志）

- Schema/mapping：`operator_action_log` 保存 `action_type`、`action_summary`、`after_value`、`inbound_processing_id`、`created_at`；无本计划所需新增字段。
- Write paths：本计划相关写入仅为 `AiReplyReviewAuditService.recordInitialDraft()`；当前还会由 `recordSendBlocked()` 与 `recordConfirmed()` 写审核 action。其他 `OperatorActionLogService.record()` 调用来自状态、层级、绑定、QA/人工外发，均不改。
- Read paths：`QaRuleAuditService.aggregateAiReplyQualityMetrics()` 按三个初稿 action 聚合质量；操作日志页面直接读取保存的 `actionSummary`，因此历史审核 action 即使 enum 未来不再写入仍可显示。
- Interaction points：初稿写入失败 → controller 当前抹掉草稿（本计划解除）；审核 write → 质量面板两个卡片（本计划停止 write 并隐藏卡片）。

### `mail_record` 与 `mail_record_qa_rule`（人工外发）

- Write paths：`PendingMailOperationService.sendManualRichReply()` 在最终变量渲染后执行 delivery、保存 `MANUAL_RICH_REPLY`，有 QA 时依次写 `mail_record_qa_rule`，再写现有 `SEND_MANUAL_COMPOSED_REPLY` / `SEND_MANUAL_RICH_REPLY`。
- Read paths：收件详情、邮件历史、训练、监控和 `QaRuleAuditService.resolveSelectedRuleIds()`；均依赖现有外发字段与 QA ordinal。
- Interaction points：AI 草稿采用 → `templateTextBody`/baseline → manual-rich send 的最终变量渲染；本计划只删除审核 gate，绝不能绕过此渲染与关联写入。

### 前端 AI 草稿与人工编辑器

- Write paths：`appendAiChatDraftBubble()` 建立 per-draft raw/rendered entry；`ai-adopt-draft` 将 rendered 写入编辑器并保存 raw/text/HTML baseline；`submitManualRichReply()` 发出 JSON。
- Read paths：`send-manual-rich-reply` 使用 adopt baseline 决定是否传 `templateTextBody`；AI 标签继续读取 `draftReadiness` 作展示。
- 现有审核路径：`aiReplyReviewState`/modal helper，`send-manual-rich-reply` 对非 READY 调 review-event 再弹窗；`index.html` 有审核 modal；页面初始化绑定其事件。
- Interaction points：删除审核状态机时，必须同时删除详情切换/reset 调用、事件绑定、review payload、`draftIdentity`/`draftAuthorityAvailable` response 消费；否则会出现未定义函数、死 DOM 或继续发送旧字段。（来源: K-ai-reply-modal-helper-scope）

### 前端样式盘点

- 可复用 class：`.ai-chat-draft-actions`（`styles.css:5952-5954`）、`.rich-editor`（`styles.css:2727-2744`）、`.reply-workflow-detail`（`styles.css:1832-1901`）。
- 设计基准：本计划不新增视觉元素，不改颜色、字号、间距、圆角、hover、focus 或 disabled 规则。
- 改动前基线：审核 modal 位于 `src/main/resources/static/index.html:1872-1890`；采用按钮位于 `app.js:9008-9013`；人工富文本编辑器位于 `app.js:9221-9238`。

## 实现方案

### Task 1：解除后端审核 authority，保留初稿质量记录

**Files:**

- Modify: `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`
- Modify: `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- Modify: `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`

**Governing invariants:** I-1, I-3, I-4.

1. 将 `AiReplyReviewAuditService` 收敛为初稿质量日志：保留根据 `draftReadiness` 选择三个 `AI_REPLY_DRAFT_*` action 的 best-effort `recordInitialDraft(...)`；删除 UUID/`draftIdentity`、authority 解析、latest 查询、`resolveCurrentDraftAuthority(...)`、`validateConfirmationForSend(...)`、`recordSendBlocked(...)`、`recordConfirmed(...)` 与审核 DTO。
2. `recordInitialDraft(...)` 捕获日志异常并仅写 server warning；返回 `Unit`。after JSON 保留 model、mode、requestCount、groundedRequestCount、readiness、generationState；移除仅供审核使用的 identity、unresolved snapshot/count。
3. controller 的首轮生成无条件调用该 best-effort 方法后继续 preview/返回草稿；续轮不再读取旧 authority。删除 review-event endpoint 和 `ReviewEventRequest`。`AiReplyTurnResponse` 删除 `draftIdentity` 与 `draftAuthorityAvailable`，保留 `draftReadiness` 作为展示质量状态。
4. `PendingMailOperationService` 构造器移除 audit service；`sendManualRichReply(...)` 删除审核参数与发送前 gate，成功发送后不写 confirmed action。`PendingManualRichReplyRequest` 删除审核字段，controller 不再透传。
5. 保持 final render → delivery → mail record → QA association → existing external action log 的原有顺序；不修改 action enum、历史记录或迁移。

### Task 2：移除审核弹窗与阻塞发送分支，保留显式采用

**Files:**

- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/app.js`

**Governing invariants:** I-1, I-2, I-3, I-4, I-5; S-1.

1. 删除 `#aiReplyReviewModal` 全部 DOM。
2. 删除 `aiReplyReviewState`、open/close/cancel/confirm helper、详情/reset 中的 review cleanup、DOMContentLoaded 中的 review listener；删除仅用于 review 的 `buildIntentReviewItems(...)` 与非 READY 编号发送 gate。
3. 删除 `lastDraftIdentity`、entry/adopt context 的 identity/review item 字段，以及 `draftAuthorityAvailable` 错误分支；AI 初稿日志失败时仍追加草稿、保存当前 turns 并允许重试/续轮。
4. 所有草稿采用按钮固定文案为“采用此草稿”；点击继续仅复制 rendered 正文、保留 raw/text/HTML baseline、设置当前 `recordId`，不发 API、不弹窗。
5. `send-manual-rich-reply` 组装既有 subject/html/text/operator/QA/variant/template payload 后直接调用 `submitManualRichReply(...)`；不写 `replySource`、`aiReviewConfirmation`，不调用 review-event。
6. 质量面板删除“直发拦截”和“人工确认”两张卡；保留四个初稿质量指标，不改 CSS/class。

### Task 3：后端回归测试与废弃契约替换

**Files:**

- Modify: `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt`
- Modify: `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`
- Modify: `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

**Governing invariants:** I-1, I-3, I-4.

1. 将 audit service 测试替换为：三种 readiness 写对应初稿 action；写入 after JSON 不含完整正文、identity、snapshot；日志 service 抛异常不向上抛出。
2. 从 Pending 测试 fixture 移除 audit mock；删除“缺 identity 拒绝”“unknown source 拒绝”“confirmed action 写入”“审核 DTO 反序列化”等断言。新增采用草稿等同普通人工 payload 的成功发送测试：断言 delivery、mail record、QA association 和既有外发 action 发生，且无审核 service/日志调用。
3. controller 测试替换 authority failure 用例：初稿日志失败时响应仍带 raw/rendered 草稿与 `draftReadiness`；续轮不查 historical authority；不存在 review-event controller 方法。
4. 每个删除的 audit gate 测试必须替换为可观察的非阻塞发送或非阻塞草稿生成测试，避免仅删断言导致覆盖下降。

### Task 4：浏览器行为回归测试

**Files:**

- Modify: `src/test/js/aiReplyReviewConfirmation.test.js`
- Modify: `src/test/js/aiReplyLoadingFeedback.test.js`

**Governing invariants:** I-1, I-2, I-3, I-4, I-5; S-1.

1. 将 review-confirmation 测试改为 adopt-direct-send：HTML 不含 `aiReplyReviewModal`；源码不含 review-event、`aiReviewConfirmation`、`requestBody.replySource = "AI_DRAFT"`、审核 helper/DOM 绑定；采用按钮为“采用此草稿”。
2. 保留并扩展 raw adoption boundary 断言：采用写入人工编辑器；`templateTextBody` 仅在 raw/text/HTML 均未编辑时发送；富文本任一变化后不传 raw template。
3. 将 loading feedback 的审核弹窗断言替换为：AI 初稿的 `BLOCKED` 展示仍可产生采用按钮；点击发送不走 modal，直接仅一次提交 manual-rich API；质量面板不含两张审核指标卡。
4. 用 `node --check` 覆盖删除跨作用域 helper 后的 JS 语法；用 DOM stub 覆盖详情切换、采用、发送三条入口，确保不遗留对已删除函数的引用。

## 变更文件清单

| 文件 | 操作 | 目的 |
|---|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt` | 修改 | 初稿质量日志 best-effort；移除审核 authority 与确认写入 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 修改 | manual-rich 发送移除 AI 审核 gate |
| `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 修改 | 草稿响应非阻塞；删除 review endpoint/DTO 透传 |
| `src/main/resources/static/index.html` | 修改 | 删除审核弹窗 DOM |
| `src/main/resources/static/app.js` | 修改 | 采用后直接发送、移除审核状态机与审核指标卡 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt` | 修改 | 初稿日志的非阻塞与数据最小化测试 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 修改 | manual-rich 不受 AI 草稿状态影响 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt` | 修改 | controller 非阻塞草稿/续轮测试 |
| `src/test/js/aiReplyReviewConfirmation.test.js` | 修改 | 审核移除与采用直发测试 |
| `src/test/js/aiReplyLoadingFeedback.test.js` | 修改 | BLOCKED 仍可采用并直接提交的 UI 回归 |

文件数：10。子系统：后端 AI/mail 发送与 mailbox 前端，均为同一用户流程，必须同次交付。

## 验收标准

- I-1：让 `OperatorActionLogService.record()` 抛异常，首轮 API 响应仍含非空 `draftText`/`renderedDraftText`，前端仍出现“采用此草稿”；续轮不访问 latest draft authority。
- I-2：生成两条草稿后采用第一条，只复制第一条 rendered 内容；切换详情或发送成功后 adopt context 清空；生成本身不改人工编辑器。
- I-3：入站存在历史 `AI_REPLY_DRAFT_BLOCKED` 时，普通手写邮件和采用后的邮件都可成功投递；请求/服务端没有 `draftIdentity`、`replySource`、`aiReviewConfirmation` 依赖；变量渲染与 QA ordinal 断言不变。
- I-4：`index.html`、`app.js`、controller 路由均不存在 review modal/event/confirmation；成功发送不新增 `AI_REPLY_SEND_BLOCKED` 或 `AI_REPLY_REVIEW_CONFIRMED`。
- I-5：质量面板只显示初稿总数、完整率、部分覆盖率、遗漏率；“直发拦截”“人工确认”文案不存在；初稿质量单测继续通过。
- S-1：`git diff -- src/main/resources/static/styles.css` 为空；无新增 class/inline style；采用按钮和人工编辑器沿用原 class。

执行命令：

```bash
node --check src/main/resources/static/app.js
node --test src/test/js/aiReplyReviewConfirmation.test.js src/test/js/aiReplyLoadingFeedback.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyReviewAuditServiceTest,PendingMailOperationServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QaRuleAuditServiceTest test
```

## 人工验收清单

### A-1：仅生成不影响人工发送

- 前置条件：存在一封待处理来信；AI 生成结果状态为 `BLOCKED`。
- 操作步骤：1）打开来信详情；2）点击“AI 生成回复”；3）不点击采用；4）在“人工富文本回复”填写主题 `人工测试` 和正文 `人工撰写正文`；5）点击“发送人工回复”。
- 预期结果：邮件发送成功；不出现“AI 草稿审核确认”；不出现 `must provide draftIdentity`；操作日志新增“人工回复邮件”。
- 覆盖：I-1、I-3、I-4。

### A-2：采用 BLOCKED 草稿后直接发送

- 前置条件：存在一封待处理来信；已生成标签为“AI 草稿 — 缺依据”的草稿。
- 操作步骤：1）点击该草稿的“采用此草稿”；2）确认正文复制到人工富文本编辑器；3）填写主题；4）点击“发送人工回复”。
- 预期结果：只发生一次 manual-rich 请求并发送成功；不显示勾选项或审核说明；无 `draftIdentity` 报错。
- 覆盖：I-2、I-3、I-4。

### A-3：采用后的富文本编辑边界

- 前置条件：已采用任一 AI 草稿。
- 操作步骤：1）对正文加粗一段文字；2）填写主题；3）发送；4）查看收到邮件。
- 预期结果：发送成功，收到邮件保留加粗格式；不会被未编辑 raw 模板覆盖。
- 覆盖：I-2、I-3。

### A-4：日志故障不影响草稿

- 前置条件：测试环境使 AI 初稿操作日志写入一次失败。
- 操作步骤：1）打开待处理来信；2）点击“AI 生成回复”；3）点击生成出的“采用此草稿”；4）填写主题并发送。
- 预期结果：草稿可见、可采用、可发送；页面不显示审核记录失败提示；服务端仅记录日志写入 warning。
- 覆盖：I-1、I-2、I-3。

### A-5：质量面板回归

- 前置条件：存在 READY、NEEDS_REVIEW、BLOCKED 三类 AI 初稿日志。
- 操作步骤：1）打开 QA 规则审计/质量面板；2）查看 AI 回复质量指标区域。
- 预期结果：显示“AI 初稿总数”“完整率 (READY)”“部分覆盖率 (NEEDS_REVIEW)”“遗漏率 (BLOCKED)”；不显示“直发拦截”或“人工确认”。
- 覆盖：I-5。
