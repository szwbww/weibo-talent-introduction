# AI 回复复杂问询覆盖与双入口语义对齐（后端）

> 日期：2026-07-12  
> 顺序：计划 1/3；本计划先实施。  
> 后续：`ai-reply-loading-and-warnings-frontend.md`、`ai-training-dialogue-style-curation.md`。  
> 背景：V68 已为公司注册、职责、企业匹配、流程、合同/IP 增加关键词，但 `Program overview` 的 `supersedesChildren=true` 在 `suggestComposition()` 中把这些详细命中全部压掉；历史邮件模拟与收发件箱 AI 回复虽然共用 `AiReplyDraftService.generate()`，仍分别组装上下文，且模拟专属 `simulateOnly` fallback 会产生不同正文。

## 需求描述

Observable outcomes:

1. 单一泛化咨询仍生成 `QA_MATCHED` 总览回复；含两个及以上请求单元的邮件进入 `QA_GROUNDED`，保留全部真实命中 QA 作为事实来源，并要求草稿按请求顺序完整作答。
2. 示例专家邮件应识别公司信息、项目结构、匹配方式、职责/交付、合同/财务/IP、后续流程，以及“研究匹配判断”这一资料依赖项；不得再只返回 `Two tracks`。
3. 历史邮件模拟与收发件箱 AI 回复共用同一上下文构建、模式判定、fallback、QA/训练知识和提示词；同一来信、同一补充指令下，两入口送入 LLM 的 messages、mode、qaRuleIds、contextWarnings、requestCoverage 一致。LLM 开启时不要求两次独立采样的 draftText 逐字相同；fallback 必须逐字相同。
4. 只读取已有 ES 专家画像，不触发 OpenAlex/Scholar/Scopus/任何 enrichment；需要研究匹配判断而已有研究字段不足时，响应返回 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`，prompt 禁止声称已审阅外部链接或确认匹配。
5. 模拟接口支持 `mailRecordId` 精确选择来信；传入时必须使用该行，不得再次按联系人读取“最新来信”。为兼容旧前端，暂时保留 `expertContactId` fallback。
6. 返回可验证的“请求事实覆盖”元数据：`requestCount`、`groundedRequestCount`、`unsupportedRequests`、`contextWarnings`。这些字段表示上下文是否有可靠依据，不宣称模型最终正文一定回答成功。

What must NOT change:

- `QaMatchService.match()` 的自动回复覆盖、变体、转人工和 `gapDetected` 现有语义不变；本计划只改变草稿/组装建议使用的 `suggestComposition()`。
- `sendQaRuleIds` 只能来自真实匹配或显式传入规则；prompt 全集不得进入 `mail_record_qa_rule`。
- 单一命中仍走 `QA_MATCHED` verbatim 契约；`buildMatchedSystemPrompt/buildMatchedUserContent` 不改写。
- 模拟不发送、不保存 `mail_record`、不写 `mail_record_qa_rule`、不改变联系人状态。
- 不新增 ES 字段、不修改 ES mapping、不启动现有 enrichment 任务。
- 本计划不改前端 loading、提示卡样式，也不启用 `QA_GROUNDED` few-shot（由后续计划处理）。

Out of scope:

- 访问用户邮件中的 Google Scholar/Scopus URL。
- 用第二次 LLM 调用判定“最终正文是否逐项回答”；本计划只报告事实覆盖，不制造虚假的 answeredCount。
- 自动发送 `QA_GROUNDED` 草稿；它仍是人工审阅/模拟能力。
- 清理兼容字段 `expertContactId`；待前端全面切到 `mailRecordId` 后另做。

## 关键不变量

### Invariant I-1: 自动回复与人工草稿覆盖语义隔离
- Rule: `match()` 继续无条件应用 `supersedesChildren`；`suggestComposition()` 仅在请求单元数 `<= 1` 时应用覆盖。多请求时返回覆盖前全部真实命中，不能让 overview 删除详细规则。
- Applies to: `QaMatchService.match`、`suggestComposition`、`applySupersede`。
- Violation consequence: 修改自动外发行为，或多问草稿继续只剩总览。
- 来源: K-overview-gap-supersede（本计划细化为 auto/draft 双语义）

### Invariant I-2: 模式判定唯一且续轮稳定
- Rule: `sendQaRuleIds` 为空为 `FREE_FORM`；非空且请求单元数 `<=1`、无 unsupported、也不需要研究画像参与回答时才为 `QA_MATCHED`；多请求、存在 unsupported、或任一请求需要研究画像时为 `QA_GROUNDED`。显式传入 qaRuleIds 的续轮仍重新分析原始 inbound 的请求结构，不能从 `QA_GROUNDED` 退回 `QA_MATCHED`。
- Applies to: `AiReplyDraftService.resolveQaRules/generate`。
- Violation consequence: 首轮和续轮策略漂移，复杂问询再次被 verbatim 总览覆盖。
- 来源: original

### Invariant I-3: prompt 规则与发送审计规则分离
- Rule: `promptRuleIds` 可含为生成提供事实的规则集合；`sendQaRuleIds` 只能是真实匹配子集或显式传入子集。新增模式和元数据不得改变该边界。
- Applies to: `ResolvedQaRules`、`AiReplyDraftResult`、两个 Controller 响应、人工富文本采用链路。
- Violation consequence: `mail_record_qa_rule` 关联无关全集规则。
- 来源: K-ai-reply-prompt-vs-send-rule-ids / K-rich-reply-qa-audit-reuse

### Invariant I-4: 研究画像只读且缺失可见
- Rule: AI 回复只读取 contact 对应当前 ES 层；APPLICATION 查不到时允许回退 CANDIDATE。严禁调用 `/api/expert-discovery/enrich`、`ExpertDiscoveryService.enrichExistingExperts`、`OpenAlexDataSource`。当来信要求研究匹配且 `researchFields/keyword/disciplineCategory/recentWorkTitles` 全空时，必须返回 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT` 并把限制写入 prompt。
- Applies to: 新 `AiReplyContextService`、`AiReplyContextBuilder`、两个 Controller。
- Violation consequence: 点击生成回复产生外部抓取副作用，或 AI 无依据确认研究匹配。
- 来源: original

### Invariant I-5: 选中邮件身份精确
- Rule: 请求带 `mailRecordId` 时，只能读取该 id 且验证 `direction=INBOUND`、`expertContactId` 非空；响应中的 inbound 正文、主题和上下文联系人均来自该行。只在 `mailRecordId=null` 时使用旧 `expertContactId + latest inbound` fallback。
- Applies to: `AiTrainingController.listSimulateMails/simulate`。
- Violation consequence: 用户点击 A 邮件却模拟了随后到达的 B 邮件。
- 来源: original

### Invariant I-6: 两入口生成文本同源
- Rule: `simulateOnly` 不得参与模式、prompt 或 fallback 文本选择；模拟只读由 Controller 无写路径保证。两个入口必须调用同一 `AiReplyContextService` 和 `AiReplyDraftService.generate()`；测试比较送入 client 的 messages，而非要求随机 LLM 输出逐字相同。
- Applies to: `AiReplyDraftService.fallback`、`AiTrainingController.simulate`、`UnmatchedInboundMailController.aiReplyTurn`。
- Violation consequence: 训练页验证结果不能代表收发件箱真实 AI 回复。
- 来源: K-ai-generate-single-freeform-seam / K-free-form-fallback-nonempty

### Invariant I-7: 覆盖元数据不冒充模型验收
- Rule: 普通请求仅在 `candidateRuleIds` 非空时计入 `groundedRequestCount`；研究匹配请求必须同时满足“candidateRuleIds 非空或有画像依据”且不存在 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`。画像不足时即使命中泛化的 Partner company QA，也必须进入 `unsupportedRequests`。字段名和 UI 文案不得称为 `answeredCount` 或“已回答”。
- Applies to: `QaMatchService` 请求提取、`AiReplyDraftResult`、Controller DTO。
- Violation consequence: UI 错误宣称 AI 已完整回答，掩盖模型遗漏。
- 来源: original

### Invariant I-8: 单一 frame 和事实来源
- Rule: `QA_MATCHED` 继续复用既有 reply frame；`QA_GROUNDED` 的问候/结束语仍来自 `ReplySnippetService.resolveManualFrame()`，事实只能来自匹配 QA、训练知识和已有画像。不得复制 few-shot 事实。
- Applies to: `AiReplyDraftService` 三种模式构建函数。
- Violation consequence: 双入口礼貌框架漂移，或生成未经审核承诺。
- 来源: K-manual-frame-three-consumers / K-training-knowledge-injection-points

## 现状审计

### `qa_rule` / `QaMatchService`
- Schema/mapping: MySQL `qa_rule` 含 `supersedes_children`; V41 将 Program overview 设为复合覆盖规则；V68 为示例邮件各问题增加详细关键词和 Contract/IP 规则。
- Write paths:
  1. Flyway V3/V41/V45/V46/V52/V57/V63/V65/V68 写入或更新规则。
  2. `QaRuleManagementService`/模板管理接口维护规则正文与关键词（本计划不改）。
- Read paths:
  1. `QaMatchService.match()` → `AutoMailReplyService`、`AutoReplyPreviewService`，属于自动回复路径。
  2. `QaMatchService.suggestComposition()` → `AiReplyDraftService` 与 `PendingMailOperationService`，属于人工草稿/组装建议路径。
  3. `matchAllRuleIds()` → `InboundMailTagService`，保留全部原始命中。
- Current defect: `suggestComposition:22` 和 `match:65` 共用同一 `applySupersede`; `applySupersede:96-103` 只要 overview 命中就只返回它。`QaMatchServiceTest:647-674` 还把该缺陷锁成“multi question 只剩 id24 且无 gap”的期望。
- Interaction points: 调整 `suggestComposition` 会同时改善 AI 草稿与人工组装建议；`match()` 必须零行为变化。

### `AiReplyDraftService`
- Schema/mapping: 无持久化；返回 `AiReplyDraftResult(draftText, usedLlm, qaRuleIds, mode, fewShotDialogRefs)`。
- Write paths: 无。
- Read paths:
  1. `QaMatchService.suggestComposition()` 获取真实匹配。
  2. `QaRuleRepository` 读取 authoritative QA 正文。
  3. `AiPromptConfigService` 读取有效提示词；默认值不能由前端复制（来源 K-prompt-config-effective-default）。
  4. `AiTrainingDialogueService` 只在 FREE_FORM 选择范例。
- Current behavior: 任意 `sendQaRuleIds` 非空即 `QA_MATCHED`; matched prompt 强制 SEGMENT verbatim；`simulateOnly` 只在 fallback 分出模拟专属正文，因此 LLM 关闭/失败时双入口不同。
- Interaction points: 新 `QA_GROUNDED` 必须保留 `qaRuleIds` 审计子集，同时用详细规则作 prompt facts；续轮显式 ids 不能改变复杂度。

### `mail_record`
- Schema/mapping: V1 `mail_record.id` 主键、`expert_contact_id` FK、`direction/body/subject/received_at`; 后续迁移增加 `cleaned_body/source_inbound_id` 等字段。`mail_record_qa_rule` 由 V42 单独关联外发审计。
- Write paths: SMTP/自动回复/人工发送服务保存 mail_record；本计划不新增、修改任何写路径。
- Read paths:
  1. `AiTrainingController.listSimulateMails` 通过 `findInboundMailsForSimulation` 每联系人取最新 inbound。
  2. `AiTrainingController.simulate` 当前只收 contactId，再调用 `findLatestInboundByExpertContactId`。
  3. `UnmatchedInboundMailController.aiReplyTurn` 用 URL 中 inbound processing id 精确读取详情。
  4. 两入口均读取 `findAllByExpertContactIdOrderByCreatedAtAsc` 作为历史。
- Interaction points: 模拟列表返回的实际 `mail_record.id` 必须进入 simulate 请求；旧 contactId fallback 暂留以保证前后端可分批部署。

### ES 专家画像（CANDIDATE/APPLICATION）
- Schema/mapping: 两层 mapping 均 `dynamic:false`，已有 `keyword/researchFields/disciplineCategory/institution/hIndex/recentWorkTitles/patentTitles/enrichedAt`；本计划零 mapping 变更。
- Write paths: `ExpertDiscoveryService.updateExpertAcademicFields()` 写 enrichment；晋升路径透传 `_source`。本计划禁止调用。
- Read paths:
  1. `ExpertSearchService.findByOrcidId(orcidId, level)` 已能读取上述字段。
  2. `MailVariableService.resolveExpertProfile` 已证明“当前层 → APPLICATION 缺失回退 CANDIDATE”的现有模式。
  3. AI 当前 `AiReplyContextBuilder.buildExpertProfile(contact)` 只输出姓名、国家、邮箱、状态，完全没读取 ES 研究字段。
- Interaction points: 新上下文服务复用现有 `findByOrcidId`，只读存量数据；失败/空字段转 warning，不触发补充任务。

### 两个 API 入口
- `AiTrainingController.simulate`: 读取最新 inbound、组装 history/profile/knowledge、`simulateOnly=true`，响应不含 qaRuleIds/覆盖/warning。
- `UnmatchedInboundMailController.aiReplyTurn`: 精确读取 inbound processing、独立组装 history/profile/knowledge，响应含 qaRuleIds/mode。
- Interaction points: 两个 Controller 的上下文拼装是漂移点；必须收敛到新服务，响应字段同构（模拟可返回 qaRuleIds 供展示，但永不持久化）。

## 实现方案

### T1：拆分 suggestion 与 auto supersede，并修复请求单元提取（I-1/I-7）
文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt`

- 保留 `match()` 调用现有 `applySupersede(rawMatches)`，函数语义不变。
- `suggestComposition()` 先调用新的 `extractRequestItems(messageBody)`：
  - 收集所有 bullet 行（`-/*/•/数字.)`）；
  - 再收集不属于已收集 bullet 的问号句；
  - trim、保持邮件顺序、按规范化文本去重；
  - 无问号且无 bullet 时，整封非空正文作为 1 个请求单元。
- 多请求时 suggestion 返回 `rawMatches`；单请求时返回 `applySupersede(rawMatches)`。
- `GapItem` 保留原字段，但语义改为“每个请求单元及其候选规则”；`gapDetected = gapItems.any { candidateRuleIds.isEmpty() }`。不要再用 category 数量代替问题覆盖。
- 改写 V68 回归测试：示例邮件的 `suggestedRuleIds` 必须包含 24/18/5/23/9/Contract-IP id，且研究匹配请求进入 unsupported；`match()` 对相同文本仍只返回 overview（锁定自动路径）。

### T2：新增 `QA_GROUNDED` 与共享覆盖元数据（I-2/I-3/I-6/I-7/I-8）
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- `AiReplyMode` 增加 `QA_GROUNDED`。
- 扩展 `ResolvedQaRules`：保存 `requestItems`、`unsupportedRequests`、`requestCount`、`groundedRequestCount`，同时继续分离 send/prompt ids。
- 注入 `AiReplyContextService`，用其唯一 `requiresResearchContext(text)` 判定每个请求单元；只要该标志为 true 且有真实 QA 命中，模式必须是 QA_GROUNDED，使 profile/history 真正进入 prompt，不能落入忽略画像的 QA_MATCHED。禁止在 DraftService 再复制一套短语表。
- 即使显式传入 qaRuleIds，也调用 `suggestComposition(inboundText)` 取请求结构；显式 ids 只替换 send/prompt id 集，不替换复杂度。
- 新增 `buildGroundedMessages`：
  - system prompt 明确“matched QA/training/profile 是事实边界；逐项、按原顺序回答；资料不足必须明确 pending；禁止声称访问了外部 URL”；
  - user content依次放 QA facts、Existing expert profile、Context warnings、Mail history、Request checklist、Inbound email；
  - 复用 manual frame；不使用 verbatim SEGMENT 要求；本计划不注入 few-shot。
- `AiReplyDraftResult` 新增 `requestCount/groundedRequestCount/unsupportedRequests/contextWarnings`。
- `generate()` 新增 `contextWarnings: List<String> = emptyList()`；覆盖计算先识别研究匹配请求，再按 I-7 用 warning 修正 grounded/unsupported，避免泛化 QA 假装覆盖研究判断。
- 删除 `simulateOnly` 对 fallback 文本的影响：将 `composeSimulateDeterministicDraft` 重命名为共享 `composeFreeFormDeterministicDraft`，FREE_FORM 两入口都使用；模拟只读不靠该参数保障。可保留废弃参数一轮兼容，但不得读取它。
- `QA_MATCHED` 三个既有 build 函数保持原文；temperature: `QA_MATCHED` 用 `properties.temperature`，`QA_GROUNDED/FREE_FORM` 用 `freeFormTemperature`。

### T3：建立只读共享上下文服务（I-4/I-6/I-8）
文件：

- 新增 `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt`
- 修改 `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt`

实现：

- `AiReplyContextService.build(contact, records, inboundText, trainingKnowledge)` 返回 `profileText/mailHistory/contextWarnings`；两个 Controller 将 `context.contextWarnings` 原样传给 `generate` 的同名参数。
- 当前层按 `contact.currentIndexLevel` 解析；APPLICATION 无结果时只回退 CANDIDATE；异常记录 warn 并返回 `EXPERT_PROFILE_NOT_FOUND`，禁止启动 enrichment。
- `AiReplyContextBuilder` 增加存量 `ExpertProfile?` 参数，输出非空的 `researchFields/keyword/disciplineCategory/institution/hIndex/recentWorkTitles/patentTitles/enrichedAt`；继续附加训练知识一次。
- `requiresResearchContext(text)` 是全链路唯一检测函数，仅识别以下规范化短语：`research profile`、`research background`、`areas of expertise`、`expertise fall within`、`within the scope`、`google scholar`、`scopus`；上下文 warning 和 Draft mode/coverage 均调用它，禁止重复正则。
- 研究充分条件：`researchFields/keyword/disciplineCategory/recentWorkTitles` 至少一项非空；否则 warning `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`。
- warning 同时进入 prompt 和结果元数据，但不自动写进最终正文模板。

### T4：对齐两个 Controller，并增加精确邮件 id（I-3/I-5/I-6/I-7）
文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`

实现：

- 两入口删除各自 profile/history 拼接，统一调用 `AiReplyContextService`。
- `AiTrainingSimulateMailItem` 新增 `mailRecordId: Long`，来自列表实际记录 `requireNotNull(mail.id)`。
- `AiTrainingSimulateRequest` 变为 `mailRecordId: Long? = null, expertContactId: Long? = null, promptOverride: String?`：优先 mailRecordId；旧 fallback 必须显式校验 contactId。
- `AiTrainingSimulateResponse` 与 `AiReplyTurnResponse` 都返回 `qaRuleIds/mode/requestCount/groundedRequestCount/unsupportedRequests/contextWarnings/injectedDialogRefs`；收发件路径 `injectedDialogRefs` 也返回，便于对齐诊断。
- 训练接口继续不调用任何 save/send；`qaRuleIds` 只展示匹配依据。

### T5：测试矩阵（I-1 至 I-8）
文件：

- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

新增/改写断言：

1. V68 示例邮件 suggestion 保留全部详细规则且存在研究资料 unsupported；auto `match()` 仍 overview-only。
2. 普通单问题 + 一条规则为 QA_MATCHED；研究匹配单问题 + 规则也为 QA_GROUNDED；多请求 + 多规则为 QA_GROUNDED；无规则为 FREE_FORM；续轮显式 ids 仍 QA_GROUNDED。
3. QA_GROUNDED prompt 含每条请求、全部匹配 facts、资料不足 warning，且不含“已访问 Scholar/Scopus”断言。
4. `simulateOnly=true/false` 在相同输入下 fallback 文本相同；FREE_FORM fallback 非空且 send qaRuleIds 为空。
5. mailRecordId 精确选择；id 不存在、OUTBOUND、无 contact 均 400；旧 contact fallback 可用。
6. 两 Controller 用相同 mock context/result 时输出的共享字段逐项相等；服务测试捕获两入口等价参数形成的 LLM messages 并断言相等。
7. 验证模拟路径 repository `save` 零调用。

## 变更文件清单

| # | 文件 | 操作 | 任务 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt` | 修改 | T1 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 修改 | T2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt` | 新增 | T3 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt` | 修改 | T3 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | 修改 | T4 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 修改 | T4 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt` | 修改 | T5 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 修改 | T5 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | 修改 | T5 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt` | 修改 | T5 |

文件数：10；子系统：QA/LLM 生成、Controller/上下文适配，共 2。

## 验收标准

- I-1：`QaMatchServiceTest` 同一复杂邮件断言 `suggestComposition` 返回详细 ids，而 `match()` 仍只返回 overview；`AutoMailReplyServiceTest` 全量通过。
- I-2：三模式与续轮稳定测试通过；grep 所有 `when (mode)` 均覆盖 `QA_GROUNDED`。
- I-3：测试断言 prompt 全集与 send ids 分离；人工采用路径仍仅使用响应 `qaRuleIds`。
- I-4：测试 mock `ExpertSearchService`，断言只调用 `findByOrcidId`，`verifyNoInteractions` 覆盖所有 enrichment 服务（新服务构造器中也不得存在这些依赖）。
- I-5：精确 id 测试断言未调用 `findLatestInboundByExpertContactId`; fallback 测试反向断言调用一次。
- I-6：相同生成参数下两入口送入 LLM 的 messages 和共享字段完全相等；LLM disabled/failure 的 draftText 逐字相等；LLM enabled 不做随机输出逐字断言。
- I-7：示例邮件 `requestCount >= 7`、`groundedRequestCount < requestCount`、研究匹配原文进入 unsupported；响应不存在 `answeredCount`。
- I-8：diff/测试锁定 `buildMatchedSystemPrompt/buildMatchedUserContent` 既有 verbatim 文本；QA_GROUNDED 不调用 `selectRelevantDialogues`。
- 集成命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaMatchServiceTest,AiReplyDraftServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest,AutoMailReplyServiceTest`。

## 人工验收清单

### A-1：复杂尽调邮件完整覆盖
- 前置条件：导入用户提供的英文专家邮件；公司、职责、匹配、流程、合同/IP QA 均启用；专家现有研究画像完整。
- 操作步骤：在历史邮件模拟页选择该邮件并生成；再在收发件箱同一来信生成首轮 AI 回复。
- 预期结果：两处 mode 均为 `QA_GROUNDED`；回复按专家问题顺序覆盖公司信息、项目结构、匹配、职责、合同/IP、下一步；不再只有 `Two tracks`；两个入口结构、事实范围、warnings/coverage 一致，允许随机措辞差异。
- 覆盖: outcomes 1/2/3，I-1/I-2/I-6

### A-2：专家画像不足提示
- 前置条件：选一位 ES 中 `researchFields/keyword/disciplineCategory/recentWorkTitles` 均空的专家，来信包含 Google Scholar/Scopus 和匹配度询问。
- 操作步骤：分别从模拟页和收发件箱生成回复。
- 预期结果：两响应 warnings 均含 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`；草稿不得写“we reviewed your Google Scholar/Scopus”或确认适配，只能说明基于当前资料无法确认；系统不出现 enrichment 任务。
- 覆盖: outcome 4，I-4/I-7

### A-3：只读使用已有画像
- 前置条件：同一专家已有 `researchFields=Structural health monitoring` 与近期论文标题。
- 操作步骤：生成匹配度回复，并观察任务执行列表和 ES 更新时间。
- 预期结果：prompt/草稿可引用现有研究方向；warnings 不含 research insufficient；无新 enrichment task，`enrichedAt` 不变化。
- 覆盖: outcome 4，I-4

### A-4：精确邮件选择
- 前置条件：模拟列表已加载并显示联系人当前来信 A；在不刷新列表的情况下，通过收信任务写入同联系人更新的来信 B。
- 操作步骤：对列表仍显示的来信 A 点击生成。
- 预期结果：请求携带 A 的 mailRecordId；响应 `inboundText/inboundSubject` 仍属于 A，不得因 B 已成为数据库最新来信而改用 B。
- 覆盖: outcome 5，I-5

### A-5：单一总览回归
- 前置条件：来信只有 “Could you provide more information about the programme?”。
- 操作步骤：生成模拟和收发件 AI 回复；同时执行自动回复预览。
- 预期结果：AI mode 为 `QA_MATCHED`，使用 Program overview；自动回复预览内容与改动前一致。
- 覆盖: must-NOT-change 1/3，I-1/I-2

### A-6：LLM 不可用时双入口一致
- 前置条件：关闭 LLM；准备一封无规则命中的普通来信。
- 操作步骤：分别从两个入口生成。
- 预期结果：两处均返回相同非空保守 fallback；mode 为 FREE_FORM；qaRuleIds 为空；模拟不写 mail_record。
- 覆盖: outcome 3，I-3/I-6

### A-7：QA 审计回归
- 前置条件：复杂邮件生成 QA_GROUNDED 后，将草稿采用到人工富文本并发送。
- 操作步骤：发送后查询该 outbound 的 `mail_record_qa_rule`。
- 预期结果：只包含本次真实命中的详细规则 ids；不包含无匹配时的 prompt 全集；ordinal 与采用规则顺序一致。
- 覆盖: must-NOT-change 2，I-3/I-8

### A-8：人工组装建议同步改善
- 前置条件：收发件箱存在与 A-1 相同的多问题邮件。
- 操作步骤：打开该邮件的人工 QA 组装区，查看默认勾选规则和缺口清单，不调用 AI。
- 预期结果：默认建议同时包含公司资质、项目总览、职责、企业匹配、流程、合同/IP 等详细规则；研究匹配请求显示为缺口或资料依赖项；不得只勾选 Program overview。
- 覆盖: `suggestComposition` × `PendingMailOperationService` interaction point，I-1/I-7

## 修正记录

（执行或复验期间的决策在此追加。）
