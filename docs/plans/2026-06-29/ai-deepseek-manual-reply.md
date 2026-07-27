# 开发计划：人工回复接入 DeepSeek 多轮生成英文邮件（含翻译 + 自动回复预留）

> 用 create-p 技能编写。复验对象：本计划文件本身。
> 日期：2026-06-29

## 需求描述

**可观察结果**：运营在「未匹配来信 / 转人工详情」页面，针对专家来信，可点击「AI 生成回复」唤起一个**对话框**：
- 第一次生成时，后端先用 `QaMatchService` 匹配出与该来信相关的 QA 规则子集，连同来信正文 **以及现有「回复片段配置」（`ReplySnippet`：尊语 SALUTATION / 致谢 ACK / 问候 GREETING / 结束语 CLOSING，经 `ReplySnippetService.resolveManualFrame()`）** 一起喂给 DeepSeek，产出一封**英文回复邮件草稿**（DeepSeek 据片段配置使用配置的尊语/问候/结束语等行文风格）。
- 运营可在对话框继续输入修改要求（如"语气更正式""加一句感谢"），多轮迭代，每轮返回新草稿。
- 每条 AI 草稿下方有「🌐 翻译为中文」按钮（复用现有翻译能力），方便查看中文含义。
- 运营满意后点「采用此草稿」，把草稿填入现有发送路径并发送。

**不可改变的行为**：
- 现有「组装台回复」「QA 单条回复」「人工富文本回复」三条发送路径的行为与审计不变。
- 现有 `composed-reply/polish` 润色端点行为不变（继续可用）。
- 自动回复管线（`AutoMailReplyService` / `BatchAutoMailReplyService`）本轮**完全不改动**，线上自动回复行为零变化。
- 现有翻译功能（`/api/translate`、`translatableBody`）行为不变。

**显式不做（Out of scope）**：
- 不把对话历史持久化到数据库（无新表、无 Flyway 迁移）。会话由前端持有，刷新即重置。
- 不修改 `AutoMailReplyService` 接线 DeepSeek——本轮只做**接口与配置预留**（见 I-6），自动回复实际接入留待后续独立计划。
- 不改发送链路（SMTP / `ComposedMail` / `MailRecord` 写入）。
- 不引入 WebClient、不引入新的 HTTP client 框架；沿用 `RestTemplate`。

## 关键不变量

### Invariant I-1: AI 生成必须优雅降级，绝不阻断工作台
- Rule: DeepSeek 调用失败 / 超时 / `llm.enabled=false` / `LlmDraftClient` bean 缺失时，`AiReplyDraftService` 必须返回可用结果（首轮回退到匹配 QA 规则的确定性拼接草稿；续轮回退到上一轮草稿原样返回并标记 `usedLlm=false`），**绝不抛异常**，绝不让对话框卡死。
- Applies to: `AiReplyDraftService.generate(...)`、`HttpLlmDraftClient.chat(...)`、新端点 `ai-reply/turn`。
- Violation consequence: DeepSeek 抖动时人工工作台不可用，运营无法发信。
- 来源: K-llm-timeout-fallback

### Invariant I-2: AI 只产文本，发送必须复用既有路径
- Rule: AI 对话框只负责生成草稿文本，**不得新增任何发送 / `MailRecord` 写入路径**。「采用此草稿」必须把草稿作为 `overrideTextBody` 走现有 `POST /unmatched-inbound/{id}/composed-reply`（当匹配到 ≥1 条 QA 规则时，`qaRuleIds` = 匹配子集）；若匹配子集为空则走现有 `POST /unmatched-inbound/{id}/manual-rich-reply`。两条路径的 `triggeredBy=OPERATOR`、`mail_record_qa_rule` ordinal 顺序契约、frame 三消费者同源契约均不受影响。
- Applies to: 前端「采用此草稿」handler。后端发送服务（`PendingMailOperationService`）**不改**。
- Violation consequence: 绕过既有审计 / 顺序契约，邮件正文与日志漂移。
- 来源: K-composed-reply-order-contract、K-manual-frame-three-consumers

### Invariant I-3: 来信正文取值必须带 cleanedBody 回退
- Rule: 喂给 DeepSeek 与匹配 QA 的来信正文统一用 `record.cleanedBody?.takeIf { it.isNotBlank() } ?: record.body.orEmpty()`。
- Applies to: 新端点 `ai-reply/turn` 取来信处。
- Violation consequence: OUTBOUND / 历史旧数据 `cleanedBody` 为空时 DeepSeek 收到空正文。
- 来源: K-cleanedbody-inbound-only

### Invariant I-4: QA 子集来源唯一
- Rule: 喂给 DeepSeek 的 QA 规则子集 = `QaMatchService.suggestComposition(messageBody).suggestedRuleIds` 对应的规则；当 `suggestedRuleIds` 为空时，回退到 `QaRuleRepository.findAllEnabledOrdered()` 全集。**不得新写一套匹配逻辑**。
- Applies to: `AiReplyDraftService`（首轮构造 system prompt 时）。
- Violation consequence: 与组装台建议规则不一致，且重复实现匹配易漂移。
- 来源: original

### Invariant I-5: 会话无状态，sessionId 仅预留
- Rule: 多轮会话历史由前端持有并整体回传；后端 `ai-reply/turn` 端点无状态、不落库。请求体可含 `sessionId: String?` 字段但本轮**不读取不持久化**（为将来持久化预留）。
- Applies to: `AiReplyTurnRequest`、`ai-reply/turn`。
- Violation consequence: 引入未计划的表 / 迁移，超出 scope。
- 来源: original

### Invariant I-6: 自动回复接入仅做预留，不接线
- Rule: 新增配置 `talent-introduction.llm.auto-reply-enabled`（默认 `false`）。`AiReplyDraftService.generate(...)` 设计为 **UI 无关**（输入 = 来信文本 + QA 规则子集 + 可选 operator 指令历史，输出 = 英文正文 + `usedLlm`），使其将来可被自动回复直接调用。本轮**不修改 `AutoMailReplyService`**，不读取该配置于自动链路。
- Applies to: `LlmProperties`、`application.yml`、`AiReplyDraftService` 方法签名设计。
- Violation consequence: 触碰线上自动回复管线 = 高风险，超出本轮 scope。
- 来源: original

### Invariant I-7: 翻译复用既有能力
- Rule: AI 草稿气泡的中文翻译必须复用现有 `translatableBody()` / `.btn-translate` 委托 / `POST /api/translate`，不得新增翻译端点或前端翻译逻辑。
- Applies to: 前端 AI 对话框渲染。
- Violation consequence: 翻译行为分叉、维护点增多。
- 来源: K-mail-body-display-sites

### Invariant I-8: 回复片段配置作为 prompt 风格指引，不污染确定性拼装链
- Rule: 喂给 DeepSeek 的回复片段必须来自 `ReplySnippetService.resolveManualFrame()`（启用且默认的 SALUTATION/GREETING/CLOSING + 启用的 ACK 选项），作为 system prompt 中的「行文风格 / 框架语指引」注入。这是 frame 的一个**新只读消费者**，**不得**改动 `QaReplyComposer.composeInOperatorOrder` / `compose` 或其常量，也不改既有 `LlmStitchService.buildRuleSegments`（其有意不含 frame）。frame 为空（未配置）时跳过该段，不报错。
- Applies to: `AiReplyDraftService.generate(...)` 构造 system prompt 处。
- Violation consequence: 触碰 frame 三消费者同源链导致组装台/润色/外发漂移；或片段未配置时崩溃。
- 来源: K-manual-frame-three-consumers

## 现状审计

### LLM 链路（`com.weibo.talentintroduction.llm` + `config`）
- 配置类 `LlmProperties`（`config/LlmProperties.kt`）：`enabled / apiUrl / apiKey / model(默认 gpt-4o-mini) / timeoutMs(默认 30000)`。在 `RestTemplateConfig` 的 `@EnableConfigurationProperties` 列表（:33）注册。
- HTTP client bean `llmRestTemplate`（`config/LlmClientConfig.kt:16-22`）：`@ConditionalOnProperty(llm.enabled=true)`，**已正确设置 connect/read timeout = `timeoutMs`**。
  - ⚠️ 知识订正：`K-llm-timeout-fallback` 反例称 timeout 未接到 client，但当前 `LlmClientConfig` 已修复（timeout 已生效）。该 K 条目已过时，Phase 6 需订正。
- `LlmDraftClient`（接口）+ `HttpLlmDraftClient`（`llm/service/HttpLlmDraftClient.kt`）：
  - 现有唯一方法 `stitchDraft(inboundQuestion, ruleSegments, freeText): String?`，单轮，标准 OpenAI `chat/completions` 格式（`messages=[{role:user, content:prompt}]`, `temperature=0.3`），Bearer auth，异常 catch→null。
  - `@ConditionalOnProperty(llm.enabled=true)`：关闭时 bean 不存在。
- `LlmStitchService`（`llm/service/LlmStitchService.kt`）：
  - `polishDraft(qaRuleIds, inboundQuestion, freeText, ackSnippetId): PolishDraftResult`，用 `ObjectProvider<LlmDraftClient>.getIfAvailable()` + try/catch，LLM 失败回退 `composeDeterministic`（调 `QaReplyComposer.composeInOperatorOrder`）。
  - `buildRuleSegments(qaRuleIds)`：把规则 `sectionTitle + replyBody` 拼成 prompt 段落（有意不含 frame）。
  - `isEnabled()` = `properties.enabled`。
- **DeepSeek 接入**：纯配置即可（`LLM_ENABLED=true / LLM_API_URL=https://api.deepseek.com/chat/completions / LLM_MODEL=deepseek-chat / LLM_API_KEY=sk-...`），无需改 client 代码。

### 人工回复 / 未匹配来信（`mail/controller/UnmatchedInboundMailController` + `mail/service/PendingMailOperationService`）
- 控制器 `@RequestMapping("/api/mail")`，已注入 `llmStitchService`、`unmatchedInboundMailService`、`pendingMailOperationService`、`replySnippetService`。
- 相关端点：
  - `GET /unmatched-inbound/{id}` `getUnmatchedDetail`：详情。
  - `GET /unmatched-inbound/{id}/composed-reply/suggest` `suggestComposedReply`：来信文本用 `detail.cleanedBody?.takeIf{isNotBlank} ?: detail.body`（:215），返回 `llmEnabled`、建议规则等。
  - `POST /unmatched-inbound/{id}/composed-reply/polish` `polishComposedReply`（:223-241）：调 `llmStitchService.polishDraft`，来信文本同样带 cleanedBody 回退（:229）。
  - `POST /unmatched-inbound/{id}/composed-reply` `sendComposedReply`（:243-256）→ `PendingMailOperationService.sendManualComposedReply`：**要求 `qaRuleIds` 非空**，`overrideTextBody` 覆盖正文，写 `MailRecord(mailType=MANUAL_COMPOSED_REPLY, triggeredBy=OPERATOR)` + `mail_record_qa_rule`（按 `qaRuleIds` 顺序写 ordinal）。
  - `POST /unmatched-inbound/{id}/manual-rich-reply` `sendManualRichReply`：`subject + htmlBody + textBody`，无需 qaRuleIds，写 `MailRecord(mailType=MANUAL_RICH_REPLY, triggeredBy=OPERATOR)`。
- 取来信正文范式（read path）：`record.cleanedBody?.takeIf { it.isNotBlank() } ?: record.body.orEmpty()`（`PendingMailOperationService:248/271`）。

### QA 匹配（`qa/service/QaMatchService`）
- `suggestComposition(messageBody): CompositionSuggestResult`，含 `suggestedRuleIds: List<Long>`、`rulesByCategory`、`gapItems`、`gapDetected`。组装台建议规则即来自此。
- `QaRuleRepository.findAllEnabledOrdered()` 取全部启用规则（按 priority,id）。
- `QaRule.replyBody`（英文回复正文）、`sectionTitle`、`displayName`、`keywords`。

### 回复片段配置（`reply/` 模块）
- `ReplySnippet`（`@Table("reply_snippet")`）：`snippetType ∈ {SALUTATION, ACK, GREETING, CLOSING}`、`content`、`displayOrder`、`isDefault`、`enabled`。
- `ReplySnippetService.resolveManualFrame(): ManualReplyFrame(salutation?, greeting?, closing?, ackOptions: List<AckOption(id, content)>)`：SALUTATION/GREETING/CLOSING 取「启用且默认」的最小 displayOrder 文本（可为 null）；ACK 取全部启用项。
- 现有消费者（K-manual-frame-three-consumers）：① `PendingMailOperationService.sendManualComposedReply`→`composeInOperatorOrder`；② `LlmStitchService.composeDeterministic`；③ 前端预览。本计划新增**第四个只读消费者**：`AiReplyDraftService` 把 frame 作为 prompt 风格指引（I-8），不进入上述确定性拼装链。

### 翻译（`mail/service/MailTranslationService` + `mail/controller/TranslationController` + 前端）
- `POST /api/translate` 入参 `{text}` → `{ok, translatedText, reason}`，失败降级 `ok=false` 不抛。
- 前端 `app.js`：`translatableBody(text, opts)`（:1041）渲染「正文块 + 🌐 翻译为中文 按钮 + 译文区」；`ensureTranslateClickHandler()`(:1057) 全局委托 `.btn-translate`→`onTranslateClick`(:1068) 调 `/api/translate`。**可直接复用**。

### 前端组装台（`static/app.js`）
- `renderComposedReplyWorkbenchHtml(suggest, recordId)`（:5280）：渲染片段面板 / 草稿预览 / 缺口清单；润色按钮 `#composedPolishBtn`（:5337，`hidden=!suggest.llmEnabled` 由 :5207 控制）。
- `composedReplyState`：`selectedRuleIds / freeText / ackSnippetId / previewEdited`。
- 动作委托：`polish-composed-reply`(:5713)、`send-composed-reply`(:5736)。`send-composed-reply` 用 `overrideTextBody = previewEdited ? previewText : null`。
- `showUnmatchedDetail(id)`(:5349) 渲染详情面板，是挂入 AI 对话框的位置。

### 交互点（Interaction points）
1. 新端点取来信文本 ↔ 既有 cleanedBody 回退范式（I-3）：必须一致。
2. AI 草稿「采用」→ 既有 `composed-reply` / `manual-rich-reply` 发送路径（I-2）：复用，不新增。
3. `AiReplyDraftService` 首轮 QA 子集 ↔ `QaMatchService.suggestComposition`（I-4）：复用组装台同源建议。
4. AI 草稿翻译 ↔ 既有 `/api/translate` + `translatableBody`（I-7）：复用。
5. `LlmDraftClient` 新增 `chat()` ↔ 既有 `stitchDraft()`（`LlmStitchService.polishDraft` 调用方）：新增方法不得破坏旧方法签名与行为。

## 实现方案

### 阶段 A — 后端 LLM 层（subsystem 1）

**A1. `LlmDraftClient` 增加多轮 chat 能力**（obeys I-1, 交互点 5）
- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt`
- 接口 `LlmDraftClient` 新增：
  ```kotlin
  fun chat(messages: List<LlmChatMessage>): String?
  ```
  并新增 `data class LlmChatMessage(val role: String, val content: String)`（role ∈ user/assistant/system）。
- `HttpLlmDraftClient.chat`：把 `messages` 直接映射为 OpenAI `messages` 数组，`model=properties.model`、`temperature=0.3`、Bearer auth，复用 `llmRestTemplate`；异常 catch→null（同 `stitchDraft`）。
- **保留 `stitchDraft` 原样**（`LlmStitchService.polishDraft` 仍依赖它），可选地内部改为构造 `messages` 调 `chat`，但不改变其外部行为与回退语义。
- 不改 `LlmClientConfig`（timeout 已正确）。

**A2. 新增 `AiReplyDraftService`（UI 无关，多轮，降级）**（obeys I-1, I-4, I-6, I-8, 交互点 3）
- 新文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
- 依赖注入：`LlmProperties`、`ObjectProvider<LlmDraftClient>`、`QaMatchService`、`QaRuleRepository`、`LlmStitchService`（复用其确定性拼接做兜底）、`ReplySnippetService`（取回复片段 frame，I-8）。
- 核心方法（UI 无关，自动回复将来可直接调）：
  ```kotlin
  fun generate(
      inboundText: String,
      operatorTurns: List<AiReplyTurn>,   // 历轮：assistant 草稿 + operator 修改指令，空=首轮
      qaRuleIds: List<Long>? = null        // null=自动用 suggestComposition 匹配子集
  ): AiReplyDraftResult                     // (draftText, usedLlm, qaRuleIds)
  ```
  - 解析 QA 子集（I-4）：入参 `qaRuleIds` 优先；否则 `QaMatchService.suggestComposition(inboundText).suggestedRuleIds`；若空回退 `findAllEnabledOrdered()` 取 id。
  - 构造 messages：
    - system：约束"你是招募助理，依据下方 QA 知识用**英文**撰写专家回信正文；只输出邮件正文，不要主题行；不杜撰 QA 之外的承诺"。把子集规则的 `sectionTitle + replyBody` 段落（复用 `LlmStitchService.buildRuleSegments` 思路；若其为 private 则在本服务内同样实现一份只读取规则的拼接，**不改 frame**）放入 system。
    - system 追加「回复片段风格指引」段（I-8）：调 `replySnippetService.resolveManualFrame()`，把非空的 `salutation / greeting / closing` 与 `ackOptions[].content` 作为"请按以下尊语/问候/致谢/结束语风格行文"的指引注入；frame 全空则跳过该段。
    - user：来信正文（`take(限长)`）。
    - 历轮：依次 append `assistant=上轮草稿`、`user=本轮修改指令`。
  - 调 `llmDraftClientProvider.getIfAvailable()?.chat(messages)`，try/catch + 空白判定。
  - 降级（I-1）：`!properties.enabled` 或 LLM 返回 null/空 → 首轮回退 `LlmStitchService.composeDeterministic`(经 polishDraft 路径或等价) 的确定性草稿、`usedLlm=false`；续轮则回退"上一轮 assistant 草稿"原样、`usedLlm=false`。
  - 返回所用 `qaRuleIds`（供前端「采用草稿」决定走 composed-reply 还是 rich-reply）。
- DTO（同文件）：`AiReplyTurn(assistantDraft: String, operatorInstruction: String)`、`AiReplyDraftResult(draftText, usedLlm, qaRuleIds)`。

**A3. 配置预留 auto-reply 开关**（obeys I-6）
- 文件：`src/main/kotlin/com/weibo/talentintroduction/config/LlmProperties.kt`：新增字段 `val autoReplyEnabled: Boolean = false`。
- 文件：`src/main/resources/application.yml`：`talent-introduction.llm` 下新增 `auto-reply-enabled: ${LLM_AUTO_REPLY_ENABLED:false}`。
- 本轮不在任何自动链路读取它（仅占位 + 文档注释说明用途）。

### 阶段 B — 后端 API（subsystem 1）

**B1. 新增对话端点**（obeys I-1, I-3, I-5, 交互点 1）
- 文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- 注入 `aiReplyDraftService: AiReplyDraftService`。
- 新增：
  ```kotlin
  @PostMapping("/unmatched-inbound/{id}/ai-reply/turn")
  fun aiReplyTurn(@PathVariable id, @RequestBody req: AiReplyTurnRequest): AiReplyTurnResponse
  ```
  - 取来信文本：`detail.cleanedBody?.takeIf{isNotBlank} ?: detail.body.orEmpty()`（I-3）。
  - 调 `aiReplyDraftService.generate(inboundText, req.turns, req.qaRuleIds)`。
  - 返回 `AiReplyTurnResponse(draftText, usedLlm, llmEnabled = llmStitchService.isEnabled(), qaRuleIds)`。
  - `AiReplyTurnRequest(turns: List<AiReplyTurnDto>, qaRuleIds: List<Long>? = null, sessionId: String? = null)`；`sessionId` 接收但忽略（I-5）。
- DTO 定义在控制器文件末尾（与现有 DTO 同处）。

### 阶段 C — 前端对话框 UI（subsystem 2）

**C1. AI 对话框渲染 + 状态**（obeys I-2, I-7）
- 文件：`src/main/resources/static/app.js`
- 在 `renderComposedReplyWorkbenchHtml`(:5280) 输出的工作台旁，新增一个 AI 对话面板（仅 `suggest.llmEnabled` 为 true 时显示，复用 `#composedPolishBtn` 的 hidden 逻辑）：
  - 聊天消息列表容器 `#aiChatMessages`（operator 指令气泡 / AI 草稿气泡交替）。
  - 每条 AI 草稿气泡正文用 `translatableBody(draftText)` 渲染（I-7，自动获得🌐翻译按钮）。
  - 底部输入框 `#aiChatInput` + 按钮：「生成 / 继续修改」(`data-action="ai-reply-turn"`)、每条草稿气泡上「采用此草稿」(`data-action="ai-adopt-draft"`)。
- 新增前端状态 `aiReplyState = { recordId, turns: [], lastDraft: "", lastQaRuleIds: [] }`，`turns` 累积 `{assistantDraft, operatorInstruction}`。

**C2. 对话交互 handler**（obeys I-1, I-2）
- `ai-reply-turn`：收集 `aiReplyState.turns` + 输入框文本，POST `/api/mail/unmatched-inbound/${id}/ai-reply/turn`，把返回 `draftText` 追加为新 AI 气泡，更新 `lastDraft/lastQaRuleIds`，`showStatus(usedLlm ? "AI 生成完成" : "DeepSeek 不可用，已用确定性草稿")`。失败弹错误不卡死（I-1）。
- `ai-adopt-draft`：把选中草稿写入组装台预览框 `#composedReplyPreview` 并 `composedReplyState.previewEdited=true`；
  - 若 `lastQaRuleIds` 非空 → 同步 `composedReplyState.selectedRuleIds = lastQaRuleIds`，提示运营点「发送组装回复」（走既有 `composed-reply`，I-2）。
  - 若为空 → 走「人工富文本回复」既有路径（I-2）。**[已决策 2026-06-29：运营确认接受空子集回退到人工富文本回复]**
  - **不新增发送请求逻辑**，复用既有 `send-composed-reply` / `send-manual-rich-reply` handler。
- 确保 `ensureTranslateClickHandler()` 已绑定（现有全局委托对新增 `.btn-translate` 自动生效）。

**C3. 样式**
- 文件：`src/main/resources/static/styles.css`：新增聊天气泡样式（`.ai-chat-*`），复用既有 `.pre` 正文渲染契约，不污染其他 `.pre`。

### 阶段 D — 测试

**D1.** 新文件 `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`：
- `llm.enabled=false` → 首轮返回确定性草稿、`usedLlm=false`（I-1）。
- LLM client 抛异常 → 不抛、回退、`usedLlm=false`（I-1）。
- `qaRuleIds=null` 且来信能匹配 → 用 `suggestComposition` 子集（I-4，mock `QaMatchService`）。
- `suggestComposition` 返回空 → 回退全集（I-4）。
- 续轮（`operatorTurns` 非空）LLM 不可用 → 回退上一轮草稿原样（I-1）。
- `resolveManualFrame` 返回非空 frame → 验证 frame 文本进入了喂给 client 的 messages（I-8，mock `LlmDraftClient` 捕获 messages）；frame 全空 → 不报错且 prompt 无 frame 段（I-8）。

## 变更文件清单

| # | 文件 | 改动 | 不变量 |
|---|---|---|---|
| 1 | `src/main/kotlin/.../config/LlmProperties.kt` | 加 `autoReplyEnabled=false` | I-6 |
| 2 | `src/main/kotlin/.../llm/service/HttpLlmDraftClient.kt` | 加 `chat(messages)` + `LlmChatMessage`，保留 `stitchDraft` | I-1 |
| 3 | `src/main/kotlin/.../llm/service/AiReplyDraftService.kt` | 新增（多轮、QA 子集、回复片段 frame 指引、降级、UI 无关）+ DTO | I-1,I-4,I-6,I-8 |
| 4 | `src/main/kotlin/.../mail/controller/UnmatchedInboundMailController.kt` | 新增 `ai-reply/turn` 端点 + 请求/响应 DTO，注入 `AiReplyDraftService` | I-3,I-5 |
| 5 | `src/main/resources/application.yml` | `llm.auto-reply-enabled` 占位 | I-6 |
| 6 | `src/main/resources/static/app.js` | AI 对话面板 + 状态 + handler，复用翻译与既有发送 | I-2,I-7 |
| 7 | `src/main/resources/static/styles.css` | 聊天气泡样式 | I-7 |
| 8 | `src/test/kotlin/.../llm/service/AiReplyDraftServiceTest.kt` | 新增单测 | I-1,I-4 |

文件数 = 8（≤10 ✅）。子系统 = 后端(1-5,8) + 前端(6,7)，共 2（≤2 ✅）。无 DB 字段 / 迁移。

## 验收标准

- **I-1**：单测覆盖 `enabled=false` / client 抛异常 / 续轮回退三种降级，均 `usedLlm=false` 且不抛；手测把 `LLM_API_URL` 指向无效地址，对话框点「生成」仍返回确定性草稿、工作台不卡。
- **I-2**：手测「采用草稿」后点「发送组装回复」，DB `mail_record.mail_type=MANUAL_COMPOSED_REPLY`、`triggered_by=OPERATOR`、`mail_record_qa_rule` 按 `lastQaRuleIds` 顺序写入；代码审查确认前端未新增任何 POST 发送路径。
- **I-3**：单测 / 手测 OUTBOUND 或旧数据（`cleanedBody` 空）来信，端点用 `body` 兜底非空。
- **I-4**：单测验证 `qaRuleIds=null` 时调用 `suggestComposition` 且用其 `suggestedRuleIds`；为空时用 `findAllEnabledOrdered()`。
- **I-5**：代码审查确认 `sessionId` 未被读取 / 未落库；无新表 / 无 Flyway 迁移。
- **I-6**：代码审查确认 `AutoMailReplyService` 无 diff；`auto-reply-enabled` 仅在 `LlmProperties` / yml 出现，未被自动链路读取；`AiReplyDraftService.generate` 签名 UI 无关。
- **I-7**：手测 AI 草稿气泡「🌐 翻译为中文」走 `/api/translate` 正常出中文；代码审查确认未新增翻译端点 / 逻辑。
- **I-8**：单测验证 `resolveManualFrame()` 文本进入 prompt；代码审查确认未改 `QaReplyComposer` / `LlmStitchService.buildRuleSegments`；frame 全空时不抛。
- **集成**：DeepSeek 真 key 下，首轮基于来信 + 匹配 QA 出英文草稿；输入"more formal"续轮草稿更正式；采用→发送成功，专家收到邮件。
- **构建/测试**：`JAVA_HOME=zulu-11 mvn test` 全绿（重点 `AiReplyDraftServiceTest`）。

## 自检清单

- [x] 关键不变量含每个新字段/状态的不变量（auto-reply-enabled→I-6，新端点→I-3/I-5，新服务→I-1/I-4）
- [x] 现状审计列全触达 store 的读写路径（LLM/人工回复/QA/翻译/前端，grep+读源码验证）
- [x] 无任务引入未被不变量覆盖的写路径（发送复用既有，I-2）
- [x] 文件数 ≤ 10（8）
- [x] 子系统数 ≤ 2（后端+前端）
- [x] 每个任务引用其治理不变量编号
- [x] 验收标准每个不变量至少一条检查
- [x] 文件清单无"等/related files"，逐一具名
- [x] Out-of-scope 显式 defer 了持久化会话与自动回复接线
- [x] Phase 0 知识均被使用或显式订正（K-llm-timeout-fallback 标记过时待 Phase 6 订正）
- [x] 计划保存到 docs/plans/2026-06-29/

## 修正记录

| 日期 | 类型 | 决策 | 原因 |
|---|---|---|---|
| 2026-06-29 | P1 计划语义修正 | 区分 `promptQaRuleIds` 与 `sendQaRuleIds`：`suggestedRuleIds` 为空时，DeepSeek prompt 可回退使用 `findAllEnabledOrdered()` 全集作为知识，但返回给前端用于发送路径和审计的 `qaRuleIds` 必须保持为空，从而走 `manual-rich-reply`。 | I-2 要求“匹配子集为空则走人工富文本回复”，I-4 的全集回退只应服务 prompt 知识，不应污染 `mail_record_qa_rule` 审计。 |

## 知识订正待办（create-p Phase 6）

- `K-llm-timeout-fallback`（qa）：反例描述已过时——`LlmClientConfig.kt:16-22` 现已为 `llmRestTemplate` 正确设置 connect/read timeout = `llm.timeout-ms`。执行后应在 fix-v 阶段订正该 K 条目（保留"必须配真实 timeout"的经验，更新反例为"已修复"）。
