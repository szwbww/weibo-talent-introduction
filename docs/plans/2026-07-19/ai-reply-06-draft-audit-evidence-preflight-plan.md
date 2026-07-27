# AI 回复第 6 步：草稿审计、证据展示与人工编辑复验

## 需求描述

- 可观察结果：每次首次生成 AI 草稿时，服务端形成一份有界审计快照，记录草稿哈希、Prompt 版本、模型、生成模式、证据规则版本、请求覆盖和 `READY / NEEDS_REVIEW / BLOCKED`；操作日志写入失败不能影响草稿返回。
- 可观察结果：可信回复工作台直接展示“这封草稿基于哪些标准事实、事实版本是否仍可用、哪些问题缺少依据”，运营无需阅读内部 intent、rule ID 或 warning code。
- 可观察结果：运营采用 AI 草稿后，编辑器对当前完整纯文本做 500ms debounce 的只读复验；新增无来源数字/链接/高风险承诺、信任替代话术、角色隐藏、企业确定性误述、敏感材料或未授权下一步时显示中文提示。
- 可观察结果：复验是写作辅助，不是发送权限。复验为 `WARNING`、接口暂不可用或历史草稿为 `NEEDS_REVIEW / BLOCKED` 时，人工发送按钮仍保持现有直接发送行为；第 7 步再实现 SMTP 前的当前态硬门禁。

必须保持不变：

1. 第 4 步的 Grounded JSON、claim/sourceIds、自然段和统一组装契约不变；第 5 步的信任、声明、动作、一次修复和三态计算不变。
2. QA `answerBody` 仍是唯一事实正文；`coverageKeys` 只做覆盖授权，`replyPolicy` 只做 AUTO/REVIEW 策略，不新增临时标签、变体或 TrustProfile 表。
3. 生成态仍只影响展示和自动回复 decision；不得恢复 draft identity、review confirmation、人工勾选解除缺口或历史状态发送门禁。（来源：K-ai-generation-observability-not-send-gate、K-ai-review-server-authoritative-snapshot）
4. 人工富文本发送继续走现有 `PendingMailOperationService.sendManualRichReply()`，保留 raw/rendered/template 边界、变量渲染、QA 关联和现有 SMTP 行为。
5. `operator_action_log` 不保存入站邮件、完整草稿、Prompt、`answerBody`、原始 LLM JSON 或可替代正文的大段文本；只保存有界 key、摘要、版本和 SHA-256。（来源：K-review-event-audit-payload-bounds）
6. 不修改数据库 schema、Flyway、OperatorActionType、QA 管理接口、自动回复发送链、项目介绍邮件和线上数据。

范围说明：

- 流程图中的独立 `TrustProfile` 当前并不存在；本步以现有 QA 原子事实形成 `evidenceSetVersion`，不在第 6 步临时引入第二套事实库。
- 流程图中的“保存原始结构化输出/自然草稿”在当前通用操作日志中改为保存结构摘要和正文哈希。它支持追踪与变更检测，不承诺逐字重放；若以后必须逐字复现，应单独设计加密、权限和保留期明确的 immutable snapshot store。
- 不在本计划内：最终 HTML/变量渲染后的 SMTP 前复验、最终幂等发送、反馈学习、在线核验、独立审计后台、QA 历史版本表；这些属于第 7 步或独立数据治理。

## 关键不变量

### Invariant I-1：审计版本来自本次服务端生成结果
- Rule：`AiReplyDraftResult` 必须携带本次使用的稳定 `promptVersion`；证据快照在 result 返回前从最终 `qaRuleIds` 对应的服务端规则重读，包含 observedAt、ruleId、展示名、`updatedAt`、`answerBodySha256`、available 和有序集合哈希 `evidenceSetVersion`。它表示生成返回时服务端观察到的事实版本，不伪称可逐字重放 Prompt 输入；客户端不得提交或覆盖审计来源。
- Applies to：`AiReplyDraftService` 所有 LLM、fallback、修复返回分支，`AiReplyReviewAuditService`，AI turn response。
- Violation consequence：日志中的模型/Prompt/事实版本与草稿不对应，无法判断后续 QA 更新是否影响旧草稿。
- 来源：K-answerbody-source-exclusive；original。

### Invariant I-2：Prompt 内容与版本必须同一快照
- Rule：QA_MATCHED、QA_GROUNDED 使用代码常量版本；FREE_FORM 必须一次读取 effective prompt，形成 `{systemPrompt, version}` 后同时用于消息构造和结果字段。默认版使用稳定常量，自定义版使用 `updatedAt + effectivePromptSha256`；不得先构造 Prompt、再二次读取配置计算版本。
- Applies to：`AiReplyDraftService.build*Messages()`、首次与续轮 generate、fallback。
- Violation consequence：Prompt 在并发更新时出现“实际用了 A、审计写 B”。
- 来源：K-prompt-config-effective-default；original。

### Invariant I-3：审计事件有界且不复制正文
- Rule：`AI_REPLY_DRAFT_READY / NEEDS_REVIEW / BLOCKED` 的 `afterValue` 固定 `schemaVersion=ai-reply-draft-audit-v1`；最多 50 个 evidence、50 个 request coverage、30 个 warning、10 个 few-shot ref，每个 label/ref 有长度上限，并保存真实总数和 `truncated=true/false`。禁止保存 `draftText/renderedDraftText/requestText/inboundText/answerBody/prompt/rawJson`。
- Applies to：`AiReplyReviewAuditService.buildSnapshot()/recordInitialDraft()`、前端日志详情。
- Violation consequence：通用日志被正文膨胀、泄露专家来信或变成影子事实库。
- 来源：K-review-event-audit-payload-bounds。

### Invariant I-4：审计写入是 best-effort，快照计算可返回
- Rule：`recordInitialDraft()` 先返回同一份 immutable snapshot，再 best-effort 写日志；QA 版本读取或日志写入异常时，草稿仍返回，快照把对应 source 标为 unavailable 并加入稳定观测 warning，不得改变 generationState/readiness。
- Applies to：首次 AI turn；续轮只 `buildSnapshot()` 返回当前草稿元数据，不新增审计事件。
- Violation consequence：观测基础设施故障阻断人工处理，或续轮刷出大量无意义事件。
- 来源：K-ai-generation-observability-not-send-gate；K-ai-draft-review-state-per-draft。

### Invariant I-5：证据展示按问题映射，不暴露内部标签
- Rule：前端以现有 `requestCoverage.factRuleIds` 关联 response 的 evidence snapshot，展示事实 displayName、更新时间和短 hash；没有展示名统一显示“未命名事实”，不得回退为 rule ID、intent key、coverage key 或 warning code。内部状态不得进入邮件正文。
- Applies to：可信回复反馈、问题覆盖列表、每条聊天草稿状态。
- Violation consequence：运营看到内部实现细节，或错误认为某条无关事实支持当前问题。
- 来源：K-grounding-status-ui-only、K-ai-draft-review-state-per-draft。

### Invariant I-6：采用旧草稿必须使用该草稿自己的证据
- Rule：每个 `aiReplyState.drafts[]` entry 同时保存 raw、rendered、qaRuleIds、requestCoverage、evidence snapshot、promptVersion、draftHash 和 readiness；采用旧草稿不得读取 `lastQaRuleIds` 或最后一次 response 的证据。
- Applies to：`appendAiChatDraftBubble()`、`trust-adopt-draft`、`ai-adopt-draft`、manualReplyQaContext。
- Violation consequence：旧正文与新事实集错配，复验和最终 QA 审计引用错误来源。
- 来源：K-ai-draft-review-state-per-draft、K-ai-preview-raw-adoption-boundary。

### Invariant I-7：编辑复验针对当前完整纯文本
- Rule：复验请求接收当前 `editor.innerText`、被采用草稿自己的 qaRuleIds 和该草稿的 expectedEvidenceSetVersion；服务端重新读取 inbound、contact research sufficiency、当前 enabled/non-NEVER/nonblank `answerBody`，重算 currentEvidenceSetVersion 并重新执行 request→intent→evidence canonicalization、readiness、plain-text claim、trust rhetoric/confidentiality/role/enterprise 和 action 校验。expected version 只用于提示“生成后事实已变化”，不能成为事实来源或发送 authority；不得根据“与 baseline 的 diff”推断问题已解决。
- Applies to：`PendingMailOperationService.preflightEditedAiReply()`、`POST /composed-reply/preflight`、编辑器 input handler。
- Violation consequence：加空格或改粗体即可伪装复验通过，或者已禁用事实仍被当作依据。
- 来源：K-ai-draft-edit-not-review-confirmation、K-readiness-evidence-revalidation、K-validation-exhaustion-must-block-readiness。

### Invariant I-8：复验动作权限采用保守服务端上下文
- Rule：动作校验只信任当前 inbound 可重建的授权：`AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList())`，再按 blocking trust gap 收紧；敏感材料永远 warning，CV 仍需用途+自愿性。生成时仅存在于临时 operator instruction 的动作在本步提示“需人工确认”，不得把客户端 allowedActions 当服务端权限。
- Applies to：人工编辑 preflight。
- Violation consequence：客户端伪造动作授权，或把无法重建的历史指令误当当前发送权限。
- 来源：K-ai-reply-action-cta-variant-coverage、K-action-sanitizer-preserve-layout；original。

### Invariant I-9：复验只读且不成为发送 gate
- Rule：preflight 不写 `operator_action_log/mail_record/mail_record_qa_rule`，不改 contact/process 状态，不调用 delivery；response 只有 `PASS / WARNING`、稳定 warningCodes、canonicalFactIds、evidenceReadiness、currentEvidenceSetVersion、checkedTextHash，不提供 `canSend`。前端不得 disable/hide 发送按钮、不得在发送 handler 读取 preflight 状态、不得弹确认 modal。
- Applies to：新 endpoint、前端复验状态、`send-manual-rich-reply`。
- Violation consequence：写作提示重新变成人工审批权，或每次敲字制造审计噪音。
- 来源：K-ai-generation-observability-not-send-gate、K-ai-review-server-authoritative-snapshot。

### Invariant I-10：异步复验不能串邮件或覆盖新文本
- Rule：500ms debounce；请求捕获 recordId、seq、采用草稿 ID/qaRuleIds 和 exact text snapshot。响应仅在 recordId、seq、当前采用草稿、当前 editor.innerText 全部仍相等时渲染；切换邮件、重新采用、清空或发送后取消 timer 并递增 seq。
- Applies to：`app.js` preflight state/reset/input/adopt/send。
- Violation consequence：慢响应把上一封邮件或旧文本的 PASS 显示到当前编辑器。
- 来源：K-ai-reply-loading-panel；original。

### Invariant I-11：复验输入与输出有硬边界
- Rule：`textBody` 非空且最多 20,000 字符；qaRuleIds 最多 50 个、必须为正整数并去重；expectedEvidenceSetVersion 最多 128 字符且只允许版本字符集；response 不回显正文，只回 `SHA-256(exact textBody)`。异常映射为稳定 4xx/观测错误，前端显示“复验暂不可用”，不把异常吞成 PASS。
- Applies to：request DTO、service、controller、JS。
- Violation consequence：编辑器可制造重查询/大 payload，或网络失败被误认安全。
- 来源：K-review-event-audit-payload-bounds；original。

### Invariant I-12：raw/rendered 与现有人工发送语义不变
- Rule：preflight 读取 rendered 编辑器纯文本但不得修改 editor、rawTemplate 或 HTML baseline；未编辑草稿仍可提交 raw template，任何 text/HTML 编辑仍按既有逻辑省略 raw template。`manualReplyQaContext` 仍只决定 QA 关联和 edited 字段。
- Applies to：采用、编辑、发送路径。
- Violation consequence：复验覆盖人工编辑、变量二次渲染或富文本格式丢失。
- 来源：K-ai-preview-raw-adoption-boundary、K-manual-rich-render-before-send。

## 样式契约

本计划不改 `styles.css`，只复用现有 class；不得为新区域添加 inline style。

### S-1：草稿证据与状态
- DOM：沿用现有 `#trustReplyFeedback.ai-reply-feedback`；状态/模型/Prompt/证据集版本用 `<div class="ai-reply-coverage">`，风险项用 `<div class="ai-reply-warning">`，异常用 `<div class="ai-reply-error">`。
- 问题列表沿用 `.compose-gap-list > li`；事实来源放在问题文字下的 `<span class="gap-no-rules-hint">依据：项目总览（2026-07-19 · a1b2c3d4）</span>`，状态 badge 继续使用既有 `badge()` helper。
- 基线：`styles.css:5809-5827`（问题列表）、`5862-5867`（来源提示）、`5965-5999`（feedback/coverage/warning/error）。视觉值保持现状：feedback gap 6px、底部 10px；coverage 7px 9px、12px 字体、`var(--surface/text-muted)`；warning/error 8px 10px、12px、`var(--radius-sm)`。
- 禁止：新证据卡片色板、rule ID 兜底、横向表格、raw JSON、固定高度或 hover 才能看见关键信息。

### S-2：人工编辑复验区
- DOM 固定插在 `#manualRichReplyEditor` 后、发送按钮前：
  ```html
  <div id="manualReplyPreflight" class="ai-reply-feedback" role="status" aria-live="polite" hidden>
    <div class="ai-reply-coverage">正在复验当前全文…</div>
  </div>
  ```
- PASS 使用 `.ai-reply-coverage`，WARNING 使用一个或多个 `.ai-reply-warning`，请求失败使用 `.ai-reply-error`；不得用 PASS 绿色制造“获准发送”的含义，文案固定“当前未发现新增风险，发送前仍请人工核对”。
- 基线：编辑器继续使用 `styles.css:2805-2822` 的 `.rich-editor`；反馈继续使用 `5965-5999`。不得改变 editor border、focus、最小高度、发送按钮间距和响应式宽度。

### S-3：操作日志详情
- AI 草稿事件沿用 `<details class="log-detail"><summary>AI 草稿审计</summary>…</details>`；字段逐行 `<div>` 展示，不新增表格或 modal。
- 基线：`styles.css:3039-3060`；summary 10px、primary 色、详情 text-muted、word-break 规则保持不变。
- 日志详情只展示时间、模式、模型、Prompt 版本、草稿短 hash、证据集短 hash、来源数量、覆盖计数和状态；不渲染正文或 prompt。

## 现状审计

### MySQL `operator_action_log`
- Schema/mapping：V19 建表；`OperatorActionLog` 映射 id、targetType/targetId、expertContactId、inboundProcessingId、actionType/actionSummary、beforeValue/afterValue(TEXT)、operatorName、note、createdAt。无 JSON schema、版本列或 payload 长度门禁。
- Write paths（全部）：
  1. `OperatorActionLogService.record()` 是统一序列化/save 入口。
  2. `ExpertIndexLevelOperationService`、`ExpertOperatorStatusService`、`ExpertContactManagementService` 写专家层级/运营状态/联系人操作。
  3. `BounceController` 写退信操作。
  4. `PendingMailOperationService` 写人工富文本/QA 发送、状态与组装相关事件。
  5. `UnmatchedInboundMailService` 写绑定、解决等收件箱事件。
  6. `AiReplyReviewAuditService.recordInitialDraft()` 写三种 AI 初稿事件；当前 after 只有 model/mode/count/readiness/generationState，失败已 best-effort。
- Read paths（全部）：
  1. `OperatorActionLogController` 与 `OperatorActionLogService.search()` 提供后台搜索。
  2. `UnmatchedInboundMailController.getUnmatchedDetail()` 读取最近 50 条，`app.js renderOperatorLogs/renderLogDetail` 展示。
  3. `QaRuleAuditService` 读取建议/采用指标和 selected fallback。
  4. `OperatorActionLogRepository.findLatestAiDraftByInboundProcessingId()` 是遗留 AI 草稿读取 seam；当前人工发送不得把它恢复为 authority。
- Interaction points：新增审计字段只能进入 AI 三类事件的 after JSON；action type、其他 caller、搜索接口和 QA 指标含义不变。前端可读新 schema，但未知/旧 schema 必须继续降级展示。

### MySQL `qa_rule`
- Schema/mapping：`QaRule` 以 `id` 为主键；本步读取 `displayName/enabled/replyPolicy/answerBody/coverageKeys/updatedAt`。`answerBody` 是事实正文，`updatedAt + SHA-256(answerBody)` 组成可观察版本；当前没有 immutable revision 表。
- Write paths（全部）：
  1. `QaRuleManagementService.createRule/updateRule/setRuleEnabled/deleteRule()`。
  2. Flyway QA seed/repair/backfill migrations；本步不修改历史 migration、不新增 migration。
- Read paths：
  1. `QaFactSelectionService` 读取 enabled/matchable/coverage/answerBody，形成 request→intent→evidence。
  2. `AiReplyDraftService`、composer、materializer/claim validator 读取 answerBody/source IDs 生成与校验。
  3. `GroundedAutoReplyDecisionService` 重读 enabled/answerBody/replyPolicy 形成自动门禁。
  4. `PendingMailOperationService` 的 suggest/evaluate/send 读取 canonical QA 事实；本步新增只读 preflight 复用此边界。
  5. 模板、标签、监控和 QA 后台读取既有字段；本步不改变。
- Interaction points：规则更新/禁用后，旧审计 hash 保留，新 preflight 必须读取当前状态并提示来源变化；不得把日志快照或前端携带 ID 当当前可用事实。没有版本表意味着 hash 只能证明“当时观察到的内容版本”，不能恢复历史正文。

### `AiReplyDraftResult`、AI turn DTO 与前端 draft state
- Write paths：`AiReplyDraftService` 三个生产 result 构造分支；`UnmatchedInboundMailController.aiReplyTurn()` 转为 response；`appendAiChatDraftBubble()` 写前端草稿数组；两条 adopt action 写 editor/adoptContext/manualReplyQaContext。
- Read paths：训练模拟、可信回复工作台、普通 AI chat、`GroundedAutoReplyDecisionService`、初稿审计、自动预览和人工采用。
- 当前缺口：result 没有 prompt/evidence/draft 版本；`AiReplyReviewAuditService` 需二次推断；`ai-adopt-draft` 使用全局 `lastQaRuleIds`，采用旧草稿可能错配最后响应的 facts。
- Interaction points：新增版本字段必须有默认值保证测试/调用兼容；每个 result 构造分支都必须显式填充；前端草稿 entry 是采用边界，不能只把元数据存到全局 state。

### 人工编辑、现有事实 evaluate 与发送
- 现有 `POST /composed-reply/evaluate` 只校验 factRuleIds、重新 canonicalize requestFacts/readiness；`app.js` 已有 300ms facts debounce + seq + fact set stale guard。
- `#manualRichReplyEditor` 当前没有 input 复验；采用只写 baseline，发送 handler 只校验 subject/body 后直接调用 `submitManualRichReply()`。
- `PendingMailOperationService.sendManualRichReply()` 已在当前正文/QA 上执行现有变量、claim、关联和发送逻辑；第 6 步不得改变该方法或让 preflight response 参与其权限判断。
- 新 preflight 复用 inbound/contact/fact selection/claim/action 读取链，但保持独立 read-only 方法和 endpoint，避免改变 facts evaluate 的 300ms 行为与返回语义。

### 前端样式与 DOM 基线
- 当前反馈由 `renderAiReplyFeedback()` 写入 `.ai-reply-feedback`，已有 coverage/warning/error 三种块；问题覆盖由 `renderComposedGapList()` 写 `.compose-gap-list`；操作日志由 `renderLogDetail()` 写 `.log-detail`。
- 人工回复 DOM 当前顺序为 subject → toolbar → `#manualRichReplyEditor.rich-editor` → send button。新复验容器只插入 editor 与 button 之间。
- 当前新区域不需要 CSS 能力缺口；修改 `styles.css` 或增加 inline style 均视为偏离 S-1 至 S-3。

## 实现方案

### T1：为生成结果建立单次 Prompt/证据版本快照
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- 新增 immutable `AiReplyPromptSnapshot(systemPrompt, version)` 和 `AiReplyEvidenceSnapshot(ruleId, displayName, updatedAt, answerBodySha256, available)`；`AiReplyDraftResult` 新增有默认值的 `promptVersion`、`evidenceSetVersion`、`evidenceSources`。
- 版本规则固定：`qa-matched-v1`、`qa-grounded-trust-json-v2`；FREE_FORM 默认 `free-form-default-v1`，自定义 `free-form-custom:<updatedAt-or-none>:<effectivePromptSha256前12位>`。SHA-256 对 exact UTF-8 字符串计算，不做 trim/换行归一化。
- FREE_FORM 在一次 generate 内只解析一次 effective prompt snapshot；build messages 和所有 fallback/result 分支复用它。QA 两种模式在 result 构造前按最终 sendQaRuleIds 顺序重读 evidence snapshot 和 aggregate SHA-256，并记录 observedAt；缺失/禁用/空正文 source 标 unavailable，不复制正文。提供 `buildEvidenceSnapshotForSelection()` 给当前态 preflight 重算版本，禁止 Pending service 复制 hash 算法。
- 所有三个生产 `AiReplyDraftResult(...)` 分支以及 LLM disabled/client unavailable/no response/trust repair fallback 测试断言版本非空且模式正确；自定义 Prompt 更新必须产生新 version，默认不随时间漂移。
- 增加 `hasBlockingTrustGapForSelection(requestFacts)` 薄封装，内部只委托现有 content planner，供 preflight 复用，不复制 trust prefix。
- 遵守：I-1、I-2、I-6、I-7。

### T2：把初稿日志升级为有界不可变审计快照
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt`
- 新增 `AiReplyAuditSnapshot`、`buildSnapshot(result)`；以 result 自带 prompt/evidence snapshot 为唯一来源，计算 exact draftText SHA-256，只保存结构元数据。
- snapshot 字段固定：schemaVersion、draftHash、model、mode、promptVersion、evidenceSetVersion、evidenceSources、sourceTotal/sourceTruncated、requestCount/groundedRequestCount、bounded requestCoverage（index/status/intent status/evidence IDs，不含 requestText/intent key 可替代正文值）、readiness、generationState、usedLlm、bounded warningCodes、fewShotRefs 和各自 total/truncated。
- `recordInitialDraft()` 返回已构造 snapshot；try/catch 只包日志写入。首次 controller 直接复用返回值；续轮调用 `buildSnapshot()` 但不写事件。
- 测试捕获 `OperatorActionLogService.record(after=...)`：验证 stable schema、hash、source 顺序/版本、边界截断；反向断言序列化内容不含 draft、requestText、answerBody、prompt 和 inbound；日志异常仍返回相同 snapshot。
- 遵守：I-1、I-3、I-4。

### T3：新增人工编辑全文只读 preflight
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`
- 新增 `preflightEditedAiReply(inboundProcessingId, factRuleIds, expectedEvidenceSetVersion, textBody)` 与 immutable result。沿用现有 record/contact/researchProfileSufficient 读取方法；不调用 send/save/audit。
- 输入先执行 I-11。非空 facts 调用 `qaFactSelectionService.select(inboundText, factRuleIds, researchProfileSufficient)` 得到 canonical IDs/requestFacts/readiness，并通过 DraftService 同源 helper 重算 currentEvidenceSetVersion；它与 expectedEvidenceSetVersion 不同即加入 `AI_REPLY_PREFLIGHT_SOURCE_CHANGED`，但仍针对当前事实完成其余检查。空 facts 返回 `AI_REPLY_PREFLIGHT_NO_EVIDENCE`，但继续跑无来源 trust/action 检查。
- 合并 warning codes，顺序固定：source/canonicalization → coverage/readiness → plain claim → trust → action。事实不存在、禁用、NEVER、answerBody 空或不再匹配当前请求统一 `AI_REPLY_PREFLIGHT_SOURCE_CHANGED`；不得抛成 PASS。
- 对全文执行现有 `claimValidator.validatePlainText()`；trust rhetoric 始终检查；blocking trust gap 时检查 confidentiality substitute；按 requestFacts 的 `company/agency/enterprise` intent→evidenceRuleIds 读取当前 source，再调用 validator 现有 internal family helper 检查角色披露和企业不确定性，禁止复制 phrase family。
- allowed actions 仅从当前 inbound 派生并按 trust gap restrict，再调用 `findViolations()`；把 violation code 稳定映射进 response。`PASS` 仅表示本次检查未发现新增问题，不改变 draft readiness，也不保证最终变量/HTML。
- result：`status`、`warningCodes`、`canonicalFactIds`、`evidenceReadiness`、`currentEvidenceSetVersion`、`checkedTextHash`；不回显正文、不持久化。
- 测试覆盖：完整事实 PASS、空事实、来源删除/禁用/正文变更、不匹配请求、虚构数字/链接/高风险承诺、信任修辞、保密替代、角色隐藏、企业确定性、敏感证件、CV 条件、未授权会议、20k/50 限制；逐个 `verifyNoInteractions`/`never()` 证明无 save/log/delivery。
- 遵守：I-7、I-8、I-9、I-11。

### T4：公开审计证据与 preflight API
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt`
- `AiReplyTurnResponse` 增加 promptVersion、draftHash、evidenceSetVersion、evidenceSources；evidence response 只含 ruleId（供 requestCoverage 关联）、displayName、updatedAt、answerBodyHash、available。
- 首次 turn：`recordInitialDraft()` 返回 snapshot；续轮：`buildSnapshot()`；同一 snapshot 用于 response，避免 UI 与日志各算一份。每条草稿都返回自己的元数据。
- 新增 `POST /api/mail/unmatched-inbound/{id}/composed-reply/preflight`：request `{factRuleIds,expectedEvidenceSetVersion,textBody}`，response 映射 T3 六个字段。不得复用/改变现有 `/evaluate` 的 facts 选择语义。
- controller 测试覆盖首次只写一次审计、续轮不写审计但有 snapshot、response 不含正文来源、preflight DTO 映射、超限为 4xx、service warning 不变成异常。
- 遵守：I-1、I-3、I-4、I-9、I-11。

### T5：前端展示证据、审计和编辑复验
- 文件：
  - `src/main/resources/static/app.js`
  - `src/test/js/trustReplyWorkbench.test.js`
- `renderAiReplyFeedback()` 增加 Prompt、证据集版本和来源摘要；`renderComposedGapList()` 通过 factRuleIds→evidenceSources 映射，在每个问题下展示事实名/更新时间/短 hash。全部经 `escapeHtml`；label 为空用既有 `UNNAMED_FACT_LABEL`，不用 ID。
- `appendAiChatDraftBubble()` 将 result 的 qaRuleIds、coverage、evidence、evidenceSetVersion、promptVersion、draftHash 写入该 draft entry；两条 adopt action 只读取 entry 自身元数据，修复旧草稿复用 `lastQaRuleIds` 的问题。preflight request 的 expectedEvidenceSetVersion 只能取自该 entry。
- 按 S-2 插入 `#manualReplyPreflight`。采用 AI 草稿后立即调度一次；只在当前 record 有 adoptContext 时监听 editor input。新增 timer/seq/reset，执行 I-10；loading/PASS/WARNING/error 都只改此容器。
- 新增 warning code→中文文案映射；未知 code 使用“发现未分类风险，请人工核对”，不得原样展示 code。`renderLogDetail()` 为三种 AI 草稿事件增加 S-3 的 bounded 详情，旧日志缺字段仍可展示。
- 严禁修改 `send-manual-rich-reply` 中 subject/body 后的直接 submit 分支；preflight state 不参与 button.disabled、return 或确认弹窗。发送成功、切邮件、重新采用时清理旧 preflight。
- JS 测试覆盖：HTML escaping、证据按问题映射、无 ID 兜底、旧草稿证据隔离、500ms debounce、seq/record/text/draft stale response、空 facts warning、错误可见、未知 code 中文兜底、日志旧/新 schema、send handler 不读取 preflight 且仍只提交一次、raw/rendered baseline 不变。
- 遵守：I-5、I-6、I-9、I-10、I-12、S-1 至 S-3。

### 开发前研究检查点

1. R-1：用单测先锁定 `AiPromptConfigService` 默认/自定义 effective prompt 的一次读取次数；若当前 mock 不能证明单次读取，先补测试再重构，不额外改 Prompt 配置 service。
2. R-2：从测试构造 50 sources/50 requests/30 warnings 的最大 snapshot，序列化后应明显低于 `operator_action_log.after_value` TEXT 上限；若超出，缩短 label/ref 上限，不改库。
3. R-3：确认当前 `AiReplyHighRiskClaimValidator` internal helper 足以覆盖 role/enterprise；若必须新增 matcher，停止并另开第 5 步校验器补丁计划，不在 Pending service 复制正则。
4. R-4：用浏览器手工核对 960px 和常用桌面宽度；若现有 class 出现溢出，只允许在本计划评审后追加一个 scoped CSS 文件变更，并从清单移除同等数量文件以保持 ≤10。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | Prompt 单次版本快照、证据版本、result 字段、trust-gap 复用 seam |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditService.kt` | 有界审计 snapshot、哈希、best-effort 返回 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 只读完整正文 preflight |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | AI response 审计元数据与 preflight endpoint |
| 5 | `src/main/resources/static/app.js` | 证据/日志展示、每草稿状态、编辑 debounce 复验 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | Prompt/证据版本与所有返回分支测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyReviewAuditServiceTest.kt` | 审计 schema、边界、隐私和 best-effort 测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | 全文 preflight、当前事实、无写入测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt` | response/endpoint/首次审计 contract 测试 |
| 10 | `src/test/js/trustReplyWorkbench.test.js` | 证据 UI、旧草稿隔离、异步复验、无发送 gate 回归 |

边界：10 个实现/测试文件，2 个子系统（AI 生成与审计后端、收件箱可信回复工作台），0 个数据库字段，0 个 CSS 文件。若实现发现必须增加生产文件，先更新计划并删减/合并同等文件，不得静默超出范围。

## 验收标准

### 自动化

1. `AiReplyDraftServiceTest`：QA_MATCHED/QA_GROUNDED/FREE_FORM、LLM/fallback/repair/continuation 全部返回稳定 promptVersion；自定义 Prompt 只读一次且内容/version 同源；证据 hash/aggregate 顺序稳定。
2. `AiReplyReviewAuditServiceTest`：三 readiness 对应 action type 不变；首次 snapshot 字段完整；50/50/30/10 边界生效；序列化不出现任一正文；日志异常仍返回 snapshot。
3. `PendingMailOperationServiceTrustWorkbenchTest`：当前 canonical facts PASS；旧/禁用/NEVER/空/不匹配事实 WARNING；第 5 步 claim/trust/action 风险全部被复用；空 facts 与超限行为稳定；无 repository save、log record、delivery。
4. `UnmatchedInboundTrustWorkbenchTest`：首轮日志一次、续轮零次；response 与日志复用同一 hash/version；preflight request/response 映射准确。
5. `trustReplyWorkbench.test.js`：每草稿证据隔离、escape、debounce/stale guard、错误与未知 warning 可见；发送按钮及 handler 不消费 preflight。
6. 运行：
   ```bash
   JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
     -Dtest=AiReplyDraftServiceTest,AiReplyReviewAuditServiceTest,PendingMailOperationServiceTrustWorkbenchTest,UnmatchedInboundTrustWorkbenchTest
   node --test src/test/js/trustReplyWorkbench.test.js
   JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test
   npm test
   git diff --check
   ```

### 行为

1. 同一事实/Prompt/正文重复生成时版本/hash 稳定；修改任一 QA answerBody 或自定义 Prompt 后，新草稿对应版本变化，旧日志不被更新。
2. UI 能回答“模型是什么、Prompt 哪版、用了哪些事实、事实哪版、哪个问题缺依据”；邮件正文不出现这些内部信息。
3. 编辑整封草稿新增无依据承诺或敏感 CTA 后显示 WARNING；删除风险后变 PASS；PASS 文案不表达“允许发送”。
4. 规则在草稿生成后被禁用/修改，preflight 显示来源已变化并建议重新生成；不从历史日志恢复旧正文继续校验。
5. 复验接口 500/超时、历史 BLOCKED、WARNING 均不禁用人工发送；发送仍只提交一次。
6. 首次 AI 审计写失败时草稿仍能采用；操作日志刷新后没有伪造成功事件。
7. 采用第二版后再采用第一版，事实 IDs、证据标签、raw/rendered baseline 和 preflight 都回到第一版，不复用第二版全局状态。
8. `operator_action_log` 新事件中不存在 `draftText/requestText/answerBody/prompt/rawJson`；其他 action type 的 after JSON 和展示不变。

## 人工验收清单

准备：选一封已绑定专家、包含“你们是谁/项目是否真实/是否收费/下一步是什么”的入站邮件；准备至少 3 条 enabled QA 事实（其中一条 REVIEW），浏览器 Network 和数据库只读窗口保持打开。

1. 打开收件箱详情 → 可信回复工作台 → 生成第一版：
   - 草稿旁显示模型、Prompt 版本、证据集短 hash。
   - 每个问题下显示对应事实名称、更新时间/短 hash；缺依据项只显示“暂无可核验事实”。
   - 页面和正文均不出现 intent key、coverage key、rule ID、warning code。
2. 生成第二版，再采用第一版：
   - 编辑器填入第一版 raw/rendered 对应内容。
   - Network 的 preflight qaRuleIds 与第一版 response 一致，不是第二版 IDs。
   - 证据与 readiness 也回到第一版。
3. 查看操作日志：
   - 仅第一轮新增一条 READY/NEEDS_REVIEW/BLOCKED 初稿事件；续轮不新增。
   - 展开可见 schema/model/mode/prompt/draftHash/evidenceSetVersion/count/readiness。
   - 数据库 `after_value` 不含入站正文、草稿句子、标准事实正文或 Prompt。
4. 修改一条已用 QA 的 `answerBody` 或禁用规则，再回编辑器输入一个字符：
   - 约 500ms 后提示“依据已变化/不可用，请重新生成或选择事实”。
   - 旧日志版本不变，新生成草稿版本变化。
5. 在编辑器依次加入并观察 WARNING：
   - `The funding is guaranteed at USD 1,000,000.`（无来源数字/保证）。
   - `Please trust us; details are confidential.`（信任/保密替代）。
   - 隐去现有 service provider 角色，改成官方项目方。
   - `Please send your passport and bank statement.`（敏感材料）。
   - `Please share your CV.`（缺用途/自愿）。
   - `Let's schedule a Zoom call.`（来信未授权时）。
6. 删除上述风险文本：状态变为“当前未发现新增风险，发送前仍请人工核对”，发送按钮外观/可用性不变。
7. 连续快速输入 3 次并立刻切换到另一封邮件：上一封慢 response 不得出现在新详情；重新打开时没有残留 loading/error。
8. 模拟 preflight 500：显示“复验暂不可用，请人工核对”；填写主题后仍能直接发送一次，不出现确认弹窗，不重复请求 send。
9. 对采用后的正文改粗体但不改文字：raw template 不得继续提交；保留现有 rendered HTML。撤销格式、完全未编辑的另一份草稿仍走既有 raw template 逻辑。
10. 在 960px 宽度检查：问题来源不断出容器，feedback 与 editor 同宽，日志详情可换行；颜色、padding、圆角与 S-1 至 S-3 基线一致。
11. 最终只读核对：无 Flyway/schema 变化；`mail_record`、`mail_record_qa_rule`、contact/process 状态在纯 preflight 输入过程中均无新增/修改；实际人工发送后才按既有路径落库。
