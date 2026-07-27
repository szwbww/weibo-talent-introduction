# AI 生成回复：QA 命中拼接 / 未命中自由发挥 混合模式

> 计划类型：新功能 + 现有 LLM 草稿链路增强
> 创建日期：2026-06-30
> 关联代码：`llm/service/AiReplyDraftService`、`llm/service/HttpLlmDraftClient`、`mail/controller/UnmatchedInboundMailController`、`static/app.js`

## 需求描述

可观察结果：
- 运营在「AI 生成回复」面板生成草稿时：
  - **命中 QA 规则** → AI 把多段命中规则的**正文逐字保留**、配合已配置敬语框架（尊语/问候/致谢/结束语）**拼接得更流畅**（只加过渡、去重，不改写事实）。
  - **未命中 QA 规则** → AI 依据**专家画像 + 历史往来邮件**自由生成回复。
  - 面板/状态栏**明确提示当前这版是「QA 命中拼接」还是「自由发挥」**，命中时显示命中规则数。
- 首轮生成即可携带运营补充要求（不再被丢弃）。
- system prompt 扩充：角色 / 目标 / 语气 / 语言匹配 / 长度。

不可改变（must NOT change）：
- 发送审计语义：进入 `mail_record_qa_rule` 的 `qaRuleIds` 只能是 `QaMatchService.suggestComposition().suggestedRuleIds` 的**真实匹配子集**；自由发挥模式返回**空** `qaRuleIds`（走人工富文本发送路径）。见 I-3。
- 敬语框架单一来源 `ReplySnippetService.resolveManualFrame()`，不得新建第二来源。见 I-4。
- LLM 失败/超时静默回退到确定性拼接（`LlmStitchService.composeDeterministicDraft`）的现有行为。
- LLM HTTP 调用必须经 `llmRestTemplate`（已接 connect/read timeout）。见 I-6。
- 自动回复管线（`QaReplyComposer.compose` 自动序链路）完全不动。

超出范围（out of scope，本计划不做）：
- 从 ES 拉取专家科研画像（研究领域/论文等）。本期「专家画像」仅取 MySQL `ExpertContact` 已有字段（姓名/国家/邮箱/状态）。ES 富化作为后续计划。
- few-shot 范例注入。
- 占位符回填 / 输出关键句校验回退（仅作为后续可选增强）。
- `composed-reply/polish` 链路（`LlmStitchService.polishDraft`）的 prompt 改造——本期只动「AI 生成回复」入口（`generate`）。
- 多轮中途切换模式：模式首轮判定后锁定（见 I-7）。

## 关键不变量

### Invariant I-1: 模式判定唯一依据
- Rule：`mode` 仅由首轮 `QaMatchService.suggestComposition(inboundText).suggestedRuleIds` 是否非空决定。非空 = `QA_MATCHED`；空 = `FREE_FORM`。`qaRuleIds` 显式传入（非 null）时视为 `QA_MATCHED`。
- Applies to：`AiReplyDraftService.generate` / `resolveQaRules`。
- Violation consequence：模式与实际发送审计子集不一致，运营误判草稿可信来源。
- 来源：original（基于 K-ai-reply-prompt-vs-send-rule-ids 的子集语义）

### Invariant I-2: QA_MATCHED 模式正文逐字保留
- Rule：`QA_MATCHED` 模式下，喂给 LLM 的命中规则正文为 `QaRule.replyBody` 原文；prompt 必须指示「保留各 SEGMENT 措辞与事实逐字不变，仅插入过渡句、整合敬语、去重寒暄」。敬语框架四要素来自 `resolveManualFrame()`。
- Applies to：`AiReplyDraftService.buildMatchedMessages` / `buildSystemPrompt`。
- Violation consequence：AI 改写口径、编造承诺，违背「规则正文不可改」诉求。
- 来源：original

### Invariant I-3: 发送审计子集只认真实匹配
- Rule：`AiReplyDraftResult.qaRuleIds`（透出为 `AiReplyTurnResponse.qaRuleIds`，最终决定 `mail_record_qa_rule`）= `ResolvedQaRules.sendQaRuleIds`，永远是真实匹配子集；`FREE_FORM` 模式该值为**空列表**。严禁把 prompt 用的全集/参考集写进发送子集。
- Applies to：`AiReplyDraftService.generate` 的两个返回分支。
- Violation consequence：`mail_record_qa_rule` 关联无关规则，污染规则使用审计。
- 来源：K-ai-reply-prompt-vs-send-rule-ids、K-rich-reply-qa-audit-reuse

### Invariant I-4: 敬语框架单一来源
- Rule：传给 LLM 的 salutation/greeting/ack/closing 必须经 `ReplySnippetService.resolveManualFrame()`（致谢可经 `resolveAck`），不得在 LLM 链路内另写一套 frame 常量或拼接顺序。
- Applies to：`AiReplyDraftService.buildFrameGuidanceText` / 新增 `buildMatchedMessages`。
- Violation consequence：预览/外发/AI 三处 frame 漂移。
- 来源：K-manual-frame-three-consumers

### Invariant I-5: 历史/画览正文取数带回退
- Rule：构造历史邮件上下文时，每条 `MailRecord` 正文取 `cleanedBody?.takeIf { it.isNotBlank() } ?: body`（OUTBOUND/旧数据 `cleanedBody` 为空）。
- Applies to：`UnmatchedInboundMailController` 历史上下文构造。
- Violation consequence：历史上下文对外发/旧邮件为空，自由发挥失去上下文。
- 来源：K-cleanedbody-inbound-only

### Invariant I-6: LLM 调用保持超时
- Rule：新增的 temperature 参数 / 任何新 LLM 调用仍走 `@Qualifier("llmRestTemplate")` 的 `RestTemplate`；不得新建无 timeout 的通用 `RestTemplate`。
- Applies to：`HttpLlmDraftClient.chat`。
- Violation consequence：慢请求阻塞人工工作台。
- 来源：K-llm-timeout-fallback

### Invariant I-7: 模式首轮锁定
- Rule：续轮（`operatorTurns` 非空）沿用首轮判定的模式与 `sendQaRuleIds`，不因续轮重新匹配而跳变。实现上续轮仍以首轮传入的 `qaRuleIds`（命中子集）为准；`FREE_FORM` 续轮维持空子集。
- Applies to：`AiReplyDraftService.generate`、前端 `aiReplyState`。
- Violation consequence：运营改着改着模式/审计子集突变，体验与审计错乱。
- 来源：original

## 现状审计

### `AiReplyDraftService`（`llm/service/AiReplyDraftService.kt`）
- 入口：`generate(inboundText, operatorTurns, qaRuleIds?)`。
- `resolveQaRules`：`qaRuleIds!=null` → send=prompt=该集；否则 `matched=suggestComposition().suggestedRuleIds`，`promptRuleIds = matched 非空 ? matched : 全部启用规则`，`sendQaRuleIds=matched`。
- `buildMessages`：system(`buildSystemPrompt`) + user(inbound 截 4000) + 逐轮 assistant(draft)+user(instruction)。
- `buildSystemPrompt`：3 句硬约束（英文、只正文、不超 QA）+ QA 知识(`buildRuleSegments` 截 8000) + frame 风格指导(`buildFrameGuidanceText` 经 `resolveManualFrame`)。
- `fallback`：无 turns 时 `composeDeterministicDraft(promptRuleIds)`；有 turns 时返回上版草稿。
- 返回 `AiReplyDraftResult(draftText, usedLlm, qaRuleIds=sendQaRuleIds)`。
- 交互点：`qaRuleIds` 透出后由前端决定走「组装台发送（非空）」或「人工富文本（空）」。

### `HttpLlmDraftClient`（`llm/service/HttpLlmDraftClient.kt`）
- `chat(messages)`：固定 body `model + messages + temperature=0.3`，经 `llmRestTemplate` POST `properties.apiUrl`，取 `choices[0].message.content`。异常返回 null + warn。
- `stitchDraft(...)`：另一条 prompt（润色用），本期不动。
- `@ConditionalOnProperty talent-introduction.llm.enabled=true`。

### `LlmProperties`（`config/LlmProperties.kt`）
- `enabled / apiUrl / apiKey / model(gpt-4o-mini) / timeoutMs(30000)`。无 temperature 配置。

### `UnmatchedInboundMailController`（`mail/controller/UnmatchedInboundMailController.kt`）
- `aiReplyTurn(id, AiReplyTurnRequest)`：`getDetail(id)` → inbound 正文取 `cleanedBody ?: body`；map turns；`aiReplyDraftService.generate(inboundText, turns, request.qaRuleIds)`；返回 `AiReplyTurnResponse(draftText, usedLlm, llmEnabled, qaRuleIds)`。
- 已注入 `expertContactRepository`、`llmStitchService`、`aiReplyDraftService`。**未注入** `MailRecordRepository`。
- `getDetail` 返回 `InboundMailProcessing`，含 `expertContactId`。
- DTO：`AiReplyTurnRequest(turns, qaRuleIds?, sessionId?)`、`AiReplyTurnResponse(draftText, usedLlm, llmEnabled, qaRuleIds)`、`AiReplyTurnDto(assistantDraft, operatorInstruction)`。

### 数据源
- `ExpertContact`（`campaign/domain/ExpertContact.kt`）：`expertName / country / expertEmail / currentStatus`（本期画像字段）。
- `MailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId): List<MailRecord>`（历史往来，已存在）。
- `MailRecord`：`direction / mailType / subject / body / cleanedBody / sentAt / receivedAt / createdAt`。

### 前端（`static/app.js`）
- `aiReplyState{recordId, turns, lastDraft, lastQaRuleIds, drafts, nextDraftId}`，`resetAiReplyState`。
- `ai-reply-turn` action（~5807）：首轮 `lastDraft` 空 → 不 push instruction（**首轮补充要求被丢弃**）。POST `{turns, qaRuleIds:null}`。`showStatus(usedLlm ? "AI 生成完成" : "DeepSeek 不可用…")`。
- 面板文案（~5396 `renderAiReplyPanelHtml`、输入框 placeholder）当前写「基于匹配 QA 规则…英文草稿」。
- 交互点：`aiReplyState.lastQaRuleIds` 决定采用后发送走组装台还是人工富文本（~5850）。

## 实现方案

### 阶段 A：服务层混合模式 + 首轮指令 + prompt 扩充（核心，不依赖新数据）

**A1. 返回值与入参扩展**（I-1, I-3, I-7）`AiReplyDraftService.kt`
- 新增枚举 `enum class AiReplyMode { QA_MATCHED, FREE_FORM }`。
- `AiReplyDraftResult` 增加 `mode: AiReplyMode`。
- `generate` 签名增加：`operatorInstruction: String? = null`（首轮补充要求）、`expertProfile: String? = null`、`mailHistory: String? = null`（阶段 B 填充，阶段 A 传 null）。
- 模式判定：`val mode = if (resolved.sendQaRuleIds.isNotEmpty()) QA_MATCHED else FREE_FORM`（`qaRuleIds` 显式传入时 sendQaRuleIds 非空 → QA_MATCHED，满足 I-1/I-7）。
- 两返回分支都带 `mode`；`FREE_FORM` 的 `qaRuleIds` 必为空（I-3：sendQaRuleIds 在未命中时即空集，禁止回退全集进 send）。

**A2. 分流构造消息**（I-2, I-4）`AiReplyDraftService.kt`
- `buildMessages` 拆为按 mode 两条：
  - `QA_MATCHED` → `buildMatchedMessages`：system = 拼接器 prompt（保留 SEGMENT 正文逐字、整合 frame、补过渡、去重）；user 段结构化：
    ```
    SALUTATION/GREETING/ACK/[SEGMENT n]=replyBody/CLOSING（来自 resolveManualFrame + 命中规则原文）
    + 原始来信
    ```
  - `FREE_FORM` → `buildFreeFormMessages`：system = 扩充版（角色/目标/语气/语言匹配/长度，见 A3）；user = 专家画像 + 历史邮件 + 来信。
- 两分支都：首轮 `operatorInstruction` 非空时，作为独立 user 指令追加（紧随 inbound 之后、运营多轮 turn 之前）；续轮 turns 逻辑保持原样（assistant draft + user instruction 逐轮）。

**A3. 扩充 system prompt**（需求②）`AiReplyDraftService.kt`
- 角色：recruiting assistant for academic expert outreach。
- 目标：促成回复 / 推进会议。
- 语气：warm, professional, concise。
- 语言匹配：`reply in the same language as the inbound email`（**删除写死的 in English**）。
- 长度：限制段落（如 ≤4 段）。
- `QA_MATCHED` 版额外强约束 I-2 的逐字保留条款。

**A4. temperature 可配 + 分模式**（I-6）`LlmProperties.kt` + `HttpLlmDraftClient.kt`
- `LlmProperties` 增 `temperature: Double = 0.3`、`freeFormTemperature: Double = 0.6`。
- `LlmDraftClient.chat` 增重载/参数 `chat(messages, temperature)`，默认沿用 `properties.temperature`；`HttpLlmDraftClient` body 用传入 temperature，**仍走 `llmRestTemplate`**。
- 服务层：`QA_MATCHED` 用 `properties.temperature`，`FREE_FORM` 用 `freeFormTemperature`。

**A5. 单测**（`AiReplyDraftServiceTest.kt`）
- 命中 → mode=QA_MATCHED、qaRuleIds=匹配子集、消息含逐字 replyBody 与 frame。
- 未命中 → mode=FREE_FORM、qaRuleIds 空、消息含画像/历史占位与扩充 system。
- 首轮 operatorInstruction 注入断言（首轮即出现在 messages）。
- 续轮沿用首轮模式/子集（I-7）。

### 阶段 B：专家画像 + 历史邮件接入 + 模式透出前端

**B1. 控制器取数并传入**（I-5）`UnmatchedInboundMailController.kt`
- 注入 `MailRecordRepository`。
- `aiReplyTurn`：由 `detail.expertContactId` 取 `ExpertContact`（已有 repo）构造画像串（姓名/国家/邮箱/状态）；`findAllByExpertContactIdOrderByCreatedAtAsc` 取历史，每条正文 `cleanedBody?.takeIf{isNotBlank} ?: body`（I-5），限制条数与总长（如最近 N 条、整体截断）。
- 透传 `operatorInstruction = request.operatorInstruction`、`expertProfile`、`mailHistory` 到 `generate`。
- `AiReplyTurnRequest` 增 `operatorInstruction: String? = null`。
- `AiReplyTurnResponse` 增 `mode: String`（`result.mode.name`）。

**B2. 前端首轮指令 + 模式提示**（需求①、模式提示核心）`static/app.js`
- `ai-reply-turn`：首轮（`lastDraft` 空）把输入框内容作为 `operatorInstruction` 放进 body（不再丢弃）；续轮维持 turns 追加逻辑。body 增 `operatorInstruction`。
- 用响应 `mode` 渲染提示气泡/状态：`QA_MATCHED` → `已匹配 QA 规则（${qaRuleIds.length} 条），按规则拼接`；`FREE_FORM` → `未匹配 QA 规则，依据历史邮件/专家画像自由生成`。
- `aiReplyState` 记 `mode`（首轮锁定，I-7），续轮沿用并保持已传 `qaRuleIds`/空集逻辑不变。
- 更新面板文案与 placeholder 反映「首轮可填补充要求 / 命中即拼接、未命中自由发挥」。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 加 `AiReplyMode`、result 加 mode、generate 加参、分流消息、扩充 prompt、temperature 分模式 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt` | `chat` 支持 temperature 参数（仍用 llmRestTemplate） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/config/LlmProperties.kt` | 加 `temperature`、`freeFormTemperature` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 注入 `MailRecordRepository`；取画像/历史；DTO 加 `operatorInstruction`、`mode`；透传 |
| 5 | `src/main/resources/static/app.js` | 首轮指令传参、模式提示渲染、面板文案、state 记 mode |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 新增/调整：模式判定、首轮指令、逐字、续轮锁定 |

合计 6 个文件（≤10）。子系统：LLM 草稿服务（1）+ 邮件控制器/取数与前端透出（2）= 2（≤2）。

## 验收标准

- I-1：命中输入 → `mode=QA_MATCHED`；未命中输入 → `mode=FREE_FORM`；显式传 `qaRuleIds` → `QA_MATCHED`。单测断言。
- I-2：QA_MATCHED 构造的 messages 中含各命中规则 `replyBody` 原文子串与 `resolveManualFrame` 的 salutation/greeting/closing；prompt 含逐字保留指令。单测断言。
- I-3：FREE_FORM 时 `result.qaRuleIds` 为空；命中时等于 `suggestComposition().suggestedRuleIds`。单测断言；并人工核对发送后 `mail_record_qa_rule` 未含非匹配规则。
- I-4：grep 确认 LLM 链路未新增第二套 GREETING/CLOSING 常量；frame 全部来自 `replySnippetService`。
- I-5：历史含 OUTBOUND/旧记录时正文非空（取 body 回退）。单测/手测。
- I-6：`HttpLlmDraftClient` 仍仅用 `llmRestTemplate`；temperature 改动不引入新 RestTemplate。代码审查。
- I-7：续轮请求模式/`qaRuleIds` 与首轮一致。单测 + 前端手测。
- 集成场景：①命中专家 → 多段规则 + 敬语流畅拼接、状态显示「已匹配 n 条」；②未命中专家（有历史）→ 自由发挥、状态显示「自由生成」；③新专家无历史无命中 → FREE_FORM 仍给礼貌草稿（可后续转人工）。
- 构建/测试：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`。

## 自检清单

- [x] 关键不变量含每个新字段/状态 ≥1 条（mode→I-1/I-7、verbatim→I-2、send 子集→I-3）
- [x] 现状审计列出受触达 store 的读写路径（grep 实证：`MailRecordRepository`、`ExpertContact`、send 子集流向）
- [x] 无未被不变量覆盖的新写路径（本计划不新增 DB 写路径；发送子集语义由 I-3 约束）
- [x] 文件数 ≤10（6）
- [x] 子系统 ≤2（2）
- [x] 每个任务引用其治理不变量编号
- [x] 验收标准每条不变量至少一项检查
- [x] 文件清单无「等/related files」
- [x] 超出范围显式声明（ES 画像、few-shot、占位回填、polish 链路、中途换模式）
- [x] Phase 0 知识已使用或显式拒绝
- [x] 计划保存至 docs/plans/2026-06-30/
