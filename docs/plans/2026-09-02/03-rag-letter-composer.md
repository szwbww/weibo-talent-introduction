# 03 整封生成：两次 LLM 调用 + 令牌逐字替换 + 未识别提问

> 顺序权威：`00-execution-order.md`。**依赖 02**。
> 全局不变量 G-1 ~ G-4 适用，本文不重复定义。

## 需求描述

**Observable outcome**

1. 新增端点 `POST /api/rag-reply/compose`，给定一封来信（训练邮件或线上来信），返回一份整封英文草稿、
   本封用到的有序事实清单、未识别的提问清单。
2. 草稿中 7 条 `VERBATIM` 事实的正文与 `rag_fact.answer` **逐字相同**；模型在提示词里拿不到这些 answer，
   只拿到 `{{FACT:KB-XXX-NNN}}` 占位符。替换后原文若不在正文里，整次请求失败并返回可重试错误码，
   **不降级、不 fallback**。
3. 来信里有明确请求但没有事实支撑、因而没被回答的，出现在 `unaddressed` 里，
   每条带一段来信的逐字片段。

**What must NOT change**

1. 现有可信工作台（`/api/trust-reply/workbench/*`）、自动回复、手动发信的任何行为。
2. `qa_rule` 及旧链路的任何读写路径。
3. 回复框架（尊语/开场白/致谢语/结束语）与落款的现有解析方式——本计划**不生成**称呼与署名。

**Out of scope**

- 前端（→ 05）；提示词编辑界面（→ 06）。
- 旧链路下线与死代码删除（→ 07 / 另开计划）。
- 自动发送、置信度打分、动作白名单、高危 claim 校验：D-1 明确全部不做。
- 把整封草稿落库（本计划只返回，不持久化）。

## 关键不变量

### I-13: VERBATIM 事实的 answer 绝不进入生成提示词
- Rule: 构建生成调用的 `retrieved_chunks` 时，`render_mode == VERBATIM` 的条目必须
  **删除 `answer` 字段**，改为 `render_token = "{{FACT:<fact_code>}}"` 加一句 `render_instruction`。
  `COMPOSE` 条目保留 `answer`。
- Applies to: `RagPromptBuilder.buildGenerationPrompt()`。
- Violation consequence: 模型看得到原文就会改写它，逐字保证失效，而校验只查「原文是否出现」，
  改写后的段落会与原文并存或替代，最终发出去的话术不是审定过的那一句。
- 来源: original（`spike_deepseek_reply.py` `build_generation_prompt()` 的 `record.pop("answer", None)`）

### I-14: 令牌替换失败即整次失败
- Rule: 替换完成后逐条检查：每个 `VERBATIM` 事实的 `answer` 必须作为子串出现在最终正文中。
  任一条不满足 → 抛出业务异常，端点返回 `422 RAG_VERBATIM_MISSING` 并列出缺失的 `fact_code`。
  **不得**降级为「用 COMPOSE 方式重写这条」，**不得**返回半成品草稿。
- Applies to: `RagVerbatimRenderer.render()`、`RagLetterComposer.compose()`。
- Violation consequence: 静默降级会让高危事实以模型改写版发出，而 UI 上看不出区别。
- 来源: original（`spike_deepseek_reply.py` `verbatim_violations()` + `raise SystemExit`）

### I-15: 令牌的插入与去重规则照抄脚本
- Rule: 替换前先做两件事，顺序固定：
  ① 同一令牌出现多次时，**只保留第一次**，其余删除；
  ② 某令牌完全没出现时，按脚本 `render_verbatim_facts()` 的三级回退插入：
  先找它前面最近的、已在正文中的令牌并插到其后；否则找它后面最近的、已在正文中的令牌并插到其前；
  否则插到第一个段落之后（正文无空行时插到最前）。插入时两侧补 `\n\n`。
- Applies to: `RagVerbatimRenderer`。
- Violation consequence: 与脚本行为分叉（D-2）。
- 来源: original（`spike_deepseek_reply.py` `render_verbatim_facts()`）
- **已知风险，本轮不修**：插入是按位置硬拼的，可能落在语法上不合适的位置，
  而插入后 I-14 的检查必然通过。这是脚本的既有行为，D-2 要求逐字一致，故照搬。
  该风险登记在此，若后续要改需另开计划。

### I-16: 服务端是 fact_code 的唯一权威
- Rule: 检索调用返回的 `fact_ids` 逐个校验，只接受**在本次候选列表内**的 `fact_code`；
  非法值丢弃并记一条 warn。随后按固定顺序回补两批：
  ① 强制 `fact_code` 前置合并；② 覆盖键与 `requested` 相交但未被模型选中的候选事实追加到尾部。
  最终截断到 `retrievalLimit`（14）。模型返回空或全部非法时，回落为候选前 12 条。
- Applies to: `RagLetterComposer.retrieve()`。
- Violation consequence: 模型漏选强制事实 → 该发的原文不发。
- 来源: original（`spike_deepseek_reply.py` `retrieve_with_deepseek()` 的 `hard_required` 与
  `required` 两段回补 + `if not selected_ids: selected_ids = candidates[:12]`）

### I-17: unaddressed.quote 必须是来信的逐字子串
- Rule: 模型返回的每条 `unaddressed`，其 `quote` 折叠空白后必须是来信折叠空白后的子串；
  折叠后长度 < 8 的丢弃；重复 quote 丢弃。校验不通过的条目**静默丢弃**，不报错、不影响草稿。
- Applies to: `RagLetterComposer.parseUnaddressed()`。
- Violation consequence: 模型可以编造「来信问了 X」，运营据此误判。
- 来源: 复用 `llm/service/InboundAskEnumerator.kt:107-146` 已有的
  `foldWhitespace` + `indexOf` + `MIN_QUOTE_LENGTH` 实现（I-1 同款）

### I-18: 称呼与落款由回复框架拼接，模型不写
- Rule: 生成提示词的第 12 条改为「不要写称呼、问候语、致谢语与署名，它们由系统拼接」。
  最终正文 = `尊语 + 开场白 + 模型正文 + 致谢语 + 结束语`，四段来自现有回复框架解析。
  端点返回时把框架四段与模型正文分开返回，前端才能把框架段落画成虚线（05 的 S-2）。
- Applies to: `RagPromptConstraints` 第 12 条、`RagLetterComposer.compose()` 的返回结构。
- Violation consequence: 模型自己写一遍署名 + 系统再拼一遍 = 一封信两个落款；
  且脚本里写死的 `Wu Wei, Customer Care Officer` 会覆盖真实发件账号。
- 来源: original（D-5；脚本 `SYSTEM_PROMPT` 第 12 条原文为
  `Sign as Wu Wei, Customer Care Officer, Qingfei Tech Talent Team, China.`）

### I-45: 扩展 LLM 客户端只走「新增带默认实现的重载」
- Rule: 为 RAG 链路补 `max_tokens` 只允许在 `LlmDraftClient` 上**新增一个带默认实现的四参重载**，
  由 `HttpLlmDraftClient` 单独覆写。**禁止**给 `chatWithModelObserved` /
  `chat` / `chatWithModel` 任何一个既有方法增删参数。
- Applies to: `llm/service/HttpLlmDraftClient.kt`。
- Violation consequence: `chatWithModelObserved` 有 22 处 override（几乎全是测试桩），
  改签名会让它们同时失去 override 资格，编译期批量失败——一个本该 1 文件的改动膨胀成 12 文件。
- 来源: original（实测覆写计数）

### I-46: 已登记的两处脚本偏离（不实现，只登记）
- Rule: 脚本 `call_deepseek_json()` 的请求体含 `"thinking": {"type": "disabled"}` 与
  `"stream": false`；生产 `HttpLlmDraftClient` **恒流式**（`:217` `"stream" to true`）
  且不发 `thinking` 字段。本轮**不补这两项**，仅登记为 D-2 平价的已知偏离。
- Applies to: 平价说明文档、`RagLetterComposerTest` 的注释。
- Violation consequence: 不登记则后续复盘时会把它当成实现缺陷反复提。
- 影响评估: `thinking` 未禁用会让 flash 模型可能进入推理模式（更慢、更贵，输出结构不变）；
  流式与否不影响最终 JSON 内容。两者都不改变逐字出信的正确性。
- 来源: original（2026-09-02 计划评审）

### I-19: ProcessContext 的映射固定
- Rule: `cvStatus` 取自 `expert_material_status` 中该联系人 `material_code='CV'` 的行：
  `PROVIDED → RECEIVED`、`DECLINED → UNKNOWN`、**缺行 → MISSING**。
  `expertReplyCount` = 该联系人 `mail_record` 中 `direction='INBOUND'` 的条数。
  `expertTags` 本轮恒为空列表。
- Applies to: `RagProcessContextResolver`。
- Violation consequence: 缺行误映射成 UNKNOWN → I-12 的 CV 请求永不触发；
  `DECLINED` 误映射成 MISSING → 对已明确拒绝的专家反复索要 CV。
- 来源: original（D-7；`V111__create_expert_material_status.sql` 的注释 I1-2 明确
  「只保存 PROVIDED/DECLINED；缺行唯一解释为 PENDING」）

## 现状审计

### LLM 客户端
- 接口：`llm/service/HttpLlmDraftClient.kt:87` `interface LlmDraftClient`。
- 可用方法：`:115-119` `chatWithModelObservedJson(messages, temperature, providerModel): LlmChatResult`
  （其实现委托 `:98` `chatWithModelObserved`）。
- **实测缺口（P0，本计划必须一并解决）**：该签名**没有 `maxTokens` 参数**，
  且 `HttpLlmDraftClient` 构造的请求体（`:213-222` 流式、`:411` 附近非流式）
  只有 `model / messages / temperature / stream / stream_options / response_format`，
  **完全没有 `max_tokens` 字段**。因此 01 定义的 `RagProperties.retrievalMaxTokens` /
  `generationMaxTokens` 若不改客户端就是死配置，且计划里写「传 maxTokens」会直接编译失败。
  改法见 T0。
- 覆写情况（决定改造的爆炸半径，执行前请重跑核对）：
  `grep -rn "override fun chatWithModelObservedJson" src/main src/test` → **1 处**；
  `grep -rn "override fun chatWithModelObserved\b" src/main src/test` → **22 处**（几乎全在测试桩）。
  因此**绝不能**给 `chatWithModelObserved` 加参数（22 个桩会同时失去 override 资格）。
- 注入方式：`ObjectProvider<LlmDraftClient>` + `getIfAvailable()`
  （见 `QaFactRetriever.kt:41-46,85`）。
- **同构模板**：`llm/service/QaFactRetriever.kt:81-175` 的 `retrieve()` 是本计划两次调用的写法样板——
  显式 `temperature = 0.0`（`:110` 注释明确「不得走 LlmProperties.temperature，默认 0.3」）、
  `cacheKey = sha256(inboundText) + ":" + poolFingerprint(pool)`（`:94`）、
  失败分类 `DISABLED / CLIENT_ABSENT / TRANSPORT_ERROR / EMPTY_RESPONSE / PARSE_ERROR`。
- JSON 提取：`QaFactRetriever.kt:280` / `InboundAskEnumerator.kt:150` / `AiQaExtractionService.kt:164`
  三处各有一份 `extractJsonPayload`，形状相同（剥 markdown fence + 取首个 `[`/`{` 到末个 `]`/`}`）。
  本计划**复用形状、不抽公共类**（避免改动三处既有调用方）。

### 逐字子串校验（I-17 的复用来源）
- `llm/service/InboundAskEnumerator.kt:107-146` `parse()`：
  `foldWhitespace(inboundText)` 返回 `(foldedText, indexMap)`；
  `foldedQuote.length < MIN_QUOTE_LENGTH` 丢弃；`foldedText.indexOf(foldedQuote) < 0` 丢弃；
  `seenQuotes.add()` 去重。本计划直接复用这三步判定（不复用 `originalRange` 映射，
  05 的 UI 只展示 quote，不做高亮定位）。

### ProcessContext 数据源
- `campaign/domain/ExpertMaterialStatusRecord.kt` + `campaign/repository/ExpertMaterialStatusRepository.kt:12-16`
  提供 `findByExpertContactIdAndMaterialCode(contactId, "CV")`，返回可空。
- `campaign/service/ExpertMaterialService.kt:14` `ExpertMaterialCode` 枚举含 `CV`；
  `:48` `ExpertMaterialProvisionStatus { PENDING, PROVIDED, DECLINED }`。
- `V111__create_expert_material_status.sql` 注释 I1-2：「只保存 PROVIDED/DECLINED；缺行唯一解释为 PENDING」。
- 回信条数：`mail/repository/MailRecordRepository.kt:39`
  `findAllByExpertContactIdOrderByCreatedAtAsc(contactId)` 可用（本计划在应用层过滤
  `direction == "INBOUND"` 并计数，避免新增仓储方法）。
- Write paths（本计划不写，仅确认）：`expert_material_status` 的唯一应用层写入点是
  `campaign/service/ExpertMaterialService.kt:89` `updateStatus()`；
  `mail_record` 的写入点在 mail 模块，与本计划无交集。
- **Interaction point 1**：`ExpertMaterialService.updateStatus()`（写）× `RagProcessContextResolver`（读）。
  运营在专家详情把 CV 标为「已提供」后，下一次生成必须立刻按 `RECEIVED` 处理。
  由于每次 compose 都实时查库、不缓存 context，该点自然成立；A-3 验收它。

### 来信解析与回复框架
- 来信文本与联系人解析：现有 `TrustReplyWorkbenchService.kt:618` `resolveSource()`
  已封装 `TRAINING_MAIL` / `LIVE_INBOUND` 两种来源到
  `ResolvedTrustReplySource(contact, inboundText, subject, senderAccountCode, ...)`
  （定义在 `:54-73`）。本计划**不复用该类**（它携带大量将被删除的字段），
  改为在 `RagReplyController` 侧用同样的两条仓储路径自行解析，只取
  `contact / inboundText / subject / senderAccountCode` 四项。
  这样 03 不依赖将被 07 摘除的服务。
- 回复框架：`reply/service/ReplySnippetService.kt`（402 行）提供 snippet 解析；
  现有消费者集中在 `AiReplyPointByPointComposer` 的 Grounded 组装与
  `AiReplyDraftService` 的 matched/FREE_FORM 提示词与 fallback。
  **本计划新增第 4 个消费者**，只调用 snippet 解析、不改 `ReplySnippetService` 本身。
  （来源: K-manual-frame-three-consumers — 该条记录的「三个消费者」在本计划后变为四个，
  Phase 6 需更新该知识条目。）
- **Interaction point 2**：`ReplySnippetService`（读框架）× `RagLetterComposer`（拼接）。
  运营改了结束语 snippet 后，新链路的落款必须跟着变。A-4 验收它。

### 端点命名空间
- 现有 `/api/trust-reply/workbench/*`（`llm/controller/TrustReplyWorkbenchController.kt:44`）。
- 本计划用**新命名空间** `/api/rag-reply/*`，与旧端点零重叠，可并行存在与灰度。

## 实现方案

### T0 — LLM 客户端补 `max_tokens`（P0，必须先做）
修改 `llm/service/HttpLlmDraftClient.kt`（接口与实现同文件）：
- 在 `interface LlmDraftClient` 中**新增一个重载**，带默认实现：
  ```
  fun chatWithModelObservedJson(
      messages: List<LlmChatMessage>,
      temperature: Double?,
      providerModel: String,
      maxTokens: Int?
  ): LlmChatResult = chatWithModelObservedJson(messages, temperature, providerModel)
  ```
  默认实现忽略 `maxTokens` 并委托既有三参方法 —— **22 个测试桩零改动**（I-45）。
- `HttpLlmDraftClient` 覆写该四参方法，把 `maxTokens` 透传到
  `executeChatObserved`（`:391`）并在其请求体中，当 `maxTokens != null` 时追加
  `body["max_tokens"] = maxTokens`。既有三参路径不追加该字段，行为逐字不变。
- **不改** `chatWithModelObserved`、不改 `chat`、不改 `chatWithModel`（I-45）。

遵循 I-45。

### T1 — 提示词常量
新建 `rag/service/RagPromptConstraints.kt`：
- `RETRIEVAL_SYSTEM_HEAD` + `RETRIEVAL_RULES: List<String>`（5 条，逐字取自脚本 `RETRIEVAL_SYSTEM_PROMPT`）。
- `GENERATION_SYSTEM_HEAD`（含 JSON 输出结构）+ `GENERATION_RULES: List<String>`（22 条）。
  - 第 1~21 条逐字取自脚本 `SYSTEM_PROMPT`，**唯二改动**：
    - 第 12 条按 I-18 改写为「不要写称呼、问候语、致谢语与署名；它们由系统拼接」。
    - 第 18/19/21 条标记为 `derived = true`——它们的正文由 `rag_mandatory_rule` 现算生成（见 T2），
      不写死在常量里。
  - 第 22 条为新增（D-6），正文：
    `Inspect every explicit request in the inbound email. For any request that is NOT answered in the
     draft because no retrieved chunk supports it, add an entry to "unaddressed" with a quote copied
     VERBATIM from the inbound email and a short reason. Never list a request that the draft already
     answers, and never invent a quote.`
- JSON 输出结构在 `GENERATION_SYSTEM_HEAD` 中声明为
  `{ "draft": string, "coverage": [...], "warnings": [...], "unaddressed": [{quote, reason}] }`。

遵循 I-18、D-6。

### T2 — 提示词构建
新建 `rag/service/RagPromptBuilder.kt`：
- `buildRetrievalPrompt(inbound, candidates, context)` — 三段 XML 包裹
  （`<process_context>` / `<inbound_email>` / `<candidate_chunks>`），候选记录字段与脚本
  `retrieval_record()` 一致：`fact_id / title / category / coverage_keys / reply_policy /
  status / risk_level / render_mode / retrieval_text`。
  **注意**：`fact_id` 的值是 `fact_code`（G-1），`title` 只在此处作为检索线索出现，
  不会进入生成调用（G-3 在生成侧生效）。
- `buildGenerationPrompt(retrieved, inbound, mandatory, context)` — 四段 XML
  （`<process_context>` / `<retrieved_chunks>` / `<mandatory_fact_ids>` / `<inbound_email>`），
  `VERBATIM` 条目按 I-13 处理。
- `renderDerivedRules(mandatoryRules)` — 把第 18/19/21 条按当前强制规则表现算成自然语言，
  插入 `GENERATION_RULES` 对应位置。

遵循 I-13、G-1、G-3。

### T3 — 流程上下文解析
新建 `rag/service/RagProcessContextResolver.kt`：
- `resolve(contactId): RagProcessContext(expertReplyCount, expertTags, cvStatus)`，严格按 I-19。

遵循 I-19。

### T4 — 令牌渲染
新建 `rag/service/RagVerbatimRenderer.kt`：
- `render(draft, retrieved): String` — 严格实现 I-15 的去重与三级回退插入，末尾做逐字替换。
- `violations(rendered, retrieved): List<String>` — 返回 answer 未出现的 `fact_code`（I-14）。

遵循 I-14、I-15。

### T5 — 编排
新建 `rag/service/RagLetterComposer.kt`：
1. `prefilter`（02）→ 候选 ≤18。
2. 检索调用：走 T0 新增的**四参重载**
   `chatWithModelObservedJson(messages, temperature = retrievalTemperature /* 0.0 */,
   providerModel, maxTokens = props.retrievalMaxTokens)`；
   缓存键 `sha256(inbound) + ":" + corpusFingerprint`
   （复用 G-2 的指纹，比 `QaFactRetriever` 的 `poolFingerprint` 更稳）。
   解析后按 I-16 校验与回补。
3. 生成调用：`temperature = generationTemperature /* 0.2 */`，`maxTokens = generationMaxTokens`。
4. `RagVerbatimRenderer.render()` → `violations()` 非空则抛 `RagComposeException(422, "RAG_VERBATIM_MISSING")`。
5. `parseUnaddressed()` 按 I-17 过滤。
6. 拼接回复框架（I-18），返回
   `RagComposeResult(frame, bodyParagraphs, usedFacts, unaddressed, modelCoverage, warnings,
   corpusFingerprint, retrievalUsage, generationUsage)`。
   `usedFacts` 每条带 `factCode / title / renderMode / riskLevel / status / origin(MANDATORY|MODEL)`。

失败分类沿用 `QaFactRetriever` 的口径（`DISABLED / CLIENT_ABSENT / TRANSPORT_ERROR /
EMPTY_RESPONSE / PARSE_ERROR`），但**不 fail-open**：本链路是人工发信的唯一草稿来源，
失败必须让运营看见并重试，不能悄悄给一份降级稿。

遵循 I-13 ~ I-19、I-10、G-1、G-2、G-3。

### T6 — 端点
新建 `rag/controller/RagReplyController.kt`：
- `POST /api/rag-reply/compose`，请求体 `{ sourceType: TRAINING_MAIL|LIVE_INBOUND, sourceId,
  model?, forcedFactCodes?: string[], excludedFactCodes?: string[], frameSelection? }`。
- `forcedFactCodes` / `excludedFactCodes` 支撑 05 的「加事实 / 去事实」：
  前者并入强制列表（前置，去重保留首次，I-9），后者从候选中剔除；
  两者都只接受存在且 enabled 的 `fact_code`，否则 `400 RAG_FACT_CODE_INVALID`。
- 异常映射：`422 RAG_VERBATIM_MISSING` / `502 RAG_LLM_UNAVAILABLE` / `400 RAG_FACT_CODE_INVALID`。

遵循 I-14、I-16、G-1。

### T7 — 测试
新建 `RagLetterComposerTest.kt`（用 stub `LlmDraftClient`，不发真实请求）：
- 检索返回含非法 fact_code → 断言被丢弃且强制项仍在（I-16）
- 检索返回空数组 → 断言回落为候选前 12 条（I-16）
- 生成返回的 draft 少写一个令牌 → 断言令牌被插入且最终 answer 出现（I-15）
- 生成返回的 draft 里 VERBATIM answer 被改写且令牌缺失 → 断言抛 422（I-14）
- 生成返回的 `unaddressed` 含编造 quote → 断言被丢弃；含真实 quote → 断言保留（I-17）
- 断言生成提示词中**不含**任何 VERBATIM 事实的 answer 文本（I-13）
- 断言生成提示词中不含中文 `title`（G-3）
- 断言草稿不含 `Wu Wei`（I-18，落款由框架提供）

新建 `RagVerbatimRendererTest.kt`：I-15 的三级回退各一条用例 + 重复令牌去重一条。
新建 `RagProcessContextResolverTest.kt`：I-19 的三种映射各一条 + 回信计数一条。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 0 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt` | 修改（T0：新增四参重载 + `max_tokens`） |
| 1 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPromptConstraints.kt` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPromptBuilder.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagProcessContextResolver.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagVerbatimRenderer.kt` | 新增 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagLetterComposer.kt` | 新增 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt` | 新增 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagLetterComposerTest.kt` | 新增 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagVerbatimRendererTest.kt` | 新增 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagProcessContextResolverTest.kt` | 新增 |

文件数 10（含 T0 的客户端改造），子系统 1（后端生成链路；读 campaign / reply 两处仓储属只读依赖，
不构成第二个可独立测试的子系统）。无前端改动，故无 `## 样式契约`。

## 验证命令

> 本项目必须用 JDK 11（zulu-11）。裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的三个测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagLetterComposerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagVerbatimRendererTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagProcessContextResolverTest

# 单独跑令牌失败必须整次失败那条
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagLetterComposerTest#verbatimMissingFailsWholeCompose

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 提示词泄漏检查（人工执行；断言生成提示词不含 VERBATIM 原文与中文 title）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagLetterComposerTest#generationPromptHidesVerbatimAnswers

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0 且 `Tests run: N, Failures: 0, Errors: 0`；`git diff --check` 无输出。
来源：`CLAUDE.md:10-27` Commands 章节。

## 验收标准

- **I-45**：`grep -c "override fun chatWithModelObserved\b" src/main src/test -r` 的计数
  与改动前相同（22）；`git diff HttpLlmDraftClient.kt` 中 `chatWithModelObserved` /
  `chat` / `chatWithModel` 三个方法的签名行**无 hunk**；新增用例断言
  四参重载传 `maxTokens=900` 时请求体含 `"max_tokens":900`、传 `null` 时**不含** `max_tokens` 键。
- **I-46**：`RagLetterComposerTest` 中有一条注释形式的登记（或一条 `@Disabled` 的说明用例），
  写明 `thinking` 与 `stream` 两处偏离；不作为失败项。
- **I-13**：`generationPromptHidesVerbatimAnswers` 断言生成提示词字符串**不包含**
  `KB-FUND-033` 的 answer 前 30 字符 `After a successful application,`，
  且**包含** `{{FACT:KB-FUND-033}}`。
- **I-14**：`verbatimMissingFailsWholeCompose` 用 stub 让模型返回一份既无令牌、
  又把原文改写了的 draft，断言抛 `RagComposeException` 且 code 为 `RAG_VERBATIM_MISSING`，
  并断言**没有**返回任何草稿对象。
- **I-15**：`RagVerbatimRendererTest` 三条用例分别断言「插到前一个令牌之后」「插到后一个令牌之前」
  「插到首段之后」，各自校验插入点两侧为 `\n\n`；第四条断言重复令牌只保留首次。
- **I-16**：断言非法 fact_code 被丢弃后强制项仍在结果首位；断言模型返回空数组时结果为候选前 12 条。
- **I-17**：断言编造 quote（不在来信中）被丢弃、折叠后 <8 字符被丢弃、重复 quote 只留一条。
- **I-18**：断言 `RagPromptConstraints.GENERATION_RULES[11]`（第 12 条）不含 `Sign as`；
  断言 compose 返回的 `bodyParagraphs` 拼接结果不含 `Wu Wei`，而 `frame.closing` 含之。
- **I-19**：三条映射断言 + 回信计数断言（构造 3 条 INBOUND + 2 条 OUTBOUND，断言计数为 3）。
- **I-10**：断言 compose 返回的 `unaddressed` 与 `modelCoverage` 两个字段在服务端**不参与**
  任何判定分支——以 grep 断言 `RagLetterComposer` 中 `modelCoverage` 只被赋值、不被 `if` 读取。
- 回归：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 整封草稿可生成且逐字段落正确
- 前置条件: 01、02 已落地；`llm.enabled=true` 且 DeepSeek 可用；
  库中存在一封日本教授样例那类的来信（问项目名 / 政府机构 / 合同主体 / 职责期限报酬 /
  任职单位 / IP / 保密）。
- 操作步骤:
  1. 用 curl 调 `POST /api/rag-reply/compose`，body `{"sourceType":"LIVE_INBOUND","sourceId":<id>}`。
  2. 在返回 JSON 中找 `usedFacts`。
  3. 把 `KB-FUND-033` 的 `answer`（从库里查）在返回的正文里做全文搜索。
- 预期结果: 第 2 步 `usedFacts` 含 7 条 `origin=MANDATORY` 的事实，顺序为
  `KB-PROG-002, KB-FUND-033, KB-PROG-003, KB-GOV-004, KB-COMP-007, KB-IP-039, KB-CONF-036`；
  第 3 步能在正文中找到**一模一样**的一整段
  `After a successful application, selected candidates may receive government research funding in the
  range of 3-12 million RMB, with enterprises providing personal salary support separately; full-time
  roles may also include additional housing allowance.`
- 覆盖: 需求 observable outcome 1、2；I-13；I-14；I-16

### A-2: 未识别的提问能被报出来
- 前置条件: 同 A-1。
- 操作步骤:
  1. 构造一封来信，正文包含
     `Could you clarify the registration under the programme, and how is tax handled?`
  2. 调 compose。
  3. 查看返回的 `unaddressed`。
- 预期结果: `unaddressed` 至少 1 条，其 `quote` 是上面那句话里的一段**逐字**文本
  （可在来信原文里精确搜索到），不是改写或概括。
- 覆盖: 需求 observable outcome 3；I-17；D-6

### A-3: CV 状态改动立刻生效（跨路径）
- 前置条件: 选一位已回过 2 封及以上信、CV 状态为「待提供」的专家；其最新来信表达了继续意愿并问了下一步。
- 操作步骤:
  1. 调 compose，记录正文里有没有索要 CV 的句子。
  2. 到专家详情页把 CV 材料状态改为「已提供」。
  3. 再调一次 compose。
- 预期结果: 第 1 步正文**有**索要 CV 的内容；第 3 步正文**没有**索要 CV 的内容。
- 覆盖: 现状审计 Interaction point 1；I-19；I-12（02 定义）

### A-4: 落款跟着回复框架走（跨路径）
- 前置条件: 有权限编辑回复片段。
- 操作步骤:
  1. 调 compose，记录返回的 `frame.closing`。
  2. 到回复片段管理处，把当前结束语片段改一个可辨识的词。
  3. 再调一次 compose。
- 预期结果: 第 3 步 `frame.closing` 反映改动；`bodyParagraphs`（模型正文）里始终**没有**
  `Wu Wei` 或任何署名。
- 覆盖: 现状审计 Interaction point 2；I-18；What must NOT change 第 3 条

### A-5: 令牌校验失败会整次失败，不给半成品
- 前置条件: 可临时把某条 VERBATIM 事实的 `answer` 改成一段模型极可能改写的文本，
  或用测试桩模拟。
- 操作步骤: 触发一次令牌缺失的 compose。
- 预期结果: 接口返回 HTTP 422，body 中 code 为 `RAG_VERBATIM_MISSING` 并列出缺失的 `fact_code`；
  **没有**返回任何草稿正文。
- 覆盖: 需求 observable outcome 2；I-14

### A-6: 回归 —— 旧链路零变化
- 前置条件: 03 已部署到测试环境。
- 操作步骤:
  1. 打开「收发件箱」→ 待处理来信 → 可信工作台，走一遍生成 → 整合 → 采用到人工回复。
  2. 打开「AI 回复训练 → 历史邮件模拟回复」，生成并保存一次训练评估。
  3. 检查手动发信的二次确认流程。
- 预期结果: 三处行为与本计划实施前完全一致。
- 覆盖: What must NOT change 第 1、2 条
