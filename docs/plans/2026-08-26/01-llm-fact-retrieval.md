# 01 · LLM 全库事实检索接入 auto 路

基线：`main` @ `f293507`。执行顺序见 `00-execution-order.md`（本计划排第 1）。

---

## 需求描述

### Observable outcome

1. 一封来信里**关键词零命中、但库里确有对应事实**的诉求，现在能绑上该事实：
   工作台该条摘要的「已选事实」不再为空，整合正文引用该事实的 `answerBody` 口径，
   不再出现 "we do not have a confirmed answer on file" 这类内部库存状态措辞。
2. 检索来源在接口上可区分：每条摘要能看出哪些事实是关键词命中的、哪些是 LLM 检索补的
   （新增只读诊断字段，本计划不做前端展示）。

### What must NOT change

1. **运营手动矩阵路径逐字不变** —— `resolveMatrixSelection`（`QaFactSelectionService.kt:195-266`）
   的 `factRuleIds = boundRuleIds = explicitIds` 行为、以及它产出的 `sendQaRuleIds`。
2. **`requestKey` 与所有既有 version 哈希不变** —— `intentCoverages` 的条目集合、顺序、
   `intentKey` 字面量一律不动。
3. **LLM 不可用时行为逐字退化为今天** —— `talent-introduction.llm.enabled=false`、
   `HttpLlmDraftClient` bean 缺席、超时、返回不可解析 JSON 这四种情况下，
   `select()` / `selectForWorkbench()` 的返回值必须与本计划落地前**逐字节相同**。
4. **`ANSWER_FACTS_VERBATIM` 通路不变**（`AiReplyDraftService.kt:498-514`、`:1060-1069`）。
5. **`mail_record_qa_rule` 的写入语义不变** —— 仍然只写"实际选用规则"，仍由
   `AutoMailReplyService.kt:637` / `ManualReplySendAttemptService.kt:254` / `ManualExpertMailService.kt:92` 三处写入。

### Out of scope（显式延后）

- 未识别诉求进入 status 判定、孤儿 coverage key 清理 → `02-unrecognized-asks-and-orphan-keys.md`
- 整封信编排（事实全信去重展示、段落角色、CTA 唯一落点）、`suggestedInstructionFor` 重写、
  前端渲染 `renderedDraftText` → `03-orchestration-and-preview.md`
- 修 `select()` 路径 `boundRuleIds` 恒空这个既有不一致（见 `## 现状审计` 的 A-4），本计划**不修**，只记录。
- 向量检索 / embedding：全库仅约 2 万字符英文正文（按 migration 重建统计），一次 prompt 装得下，不引入。

---

## 关键不变量

### Invariant I-1: 检索结果只经 `factRuleIds` 进入链路，绝不进 `intents`
- Rule: LLM 检索返回的 ruleId 只能写入 `RequestFactItem.factRuleIds`（以及新增的诊断字段
  `retrievedFactRuleIds`）。**禁止**据此新增/删除/重排 `RequestFactItem.intents` 中的
  `RequestIntentCoverage` 条目，禁止修改任何条目的 `intentKey`。
- Applies to: `QaFactSelectionService.buildRequestFact`（`:444-541`）的全部 5 个调用点
  （`:52` select、`:236` matrix、`:278` legacy 空选、`:304` legacy、`:337` auto）。
- Violation consequence: `TrustReplyWorkbenchService.requestKey(...)` 的 `intentKeys` 输入变化
  → canonical 投影（`:2077` 读 `item.intents`）与 item 投影不再逐字相等 →
  `validateMatrixKeys` 抛 `TRUST_REPLY_REQUEST_KEY_INVALID` / `TRUST_REPLY_FACT_SELECTION_INVALID`
  → **bootstrap 失败、工作台打不开，且 V83 表里全部历史锁定项作废**。
- 来源: K-request-key-includes-intent-keys

### Invariant I-2: 检索只在 auto 路生效，矩阵路径必须旁路
- Rule: `resolveMatrixSelection`（`:195`）内的 `buildRequestFact` 调用（`:236`）
  必须传 `retrievedRuleIds = emptyList()`；`resolveLegacySelection` 的两个调用点
  （`:278`、`:304`）同样传空。只有 `select()` 的 `:52` 与 `resolveAutoSelection` 的 `:337` 传真值。
- Applies to: 上述 5 个调用点，逐个显式赋值，不得依赖默认值传播。
- Violation consequence: 运营显式勾选的事实集会被机器结果污染，
  `:256` 的 `if (bound.boundRuleIds != explicitIds) throw 422` 会把工作台打死；
  且违反「人工矩阵是最终权威」的产品决策。
- 来源: K-workbench-matrix-path-is-operator-scoped

### Invariant I-3: 并集不是替代 —— 关键词命中的必进
- Rule: 一条摘要的最终候选集 = `strictCandidateRules`（今日逻辑逐字）**∪** 校验通过的检索结果。
  关键词命中的规则在任何情况下都不得因检索结果而被移除。
- Applies to: `buildRequestFact` 内候选集的构造。
- Violation consequence: 失去确定性下限；LLM 抖动会让今天能答的问题明天答不了。

### Invariant I-4: 服务端是 ruleId 的唯一权威，模型只提供候选
- Rule: 模型返回的每个 ruleId 必须逐个通过四项校验才可采纳：
  ① 该 id 在本次 `promptPool` 内；② `QaRule.enabled == true`；
  ③ `replyPolicyEnum() != QaReplyPolicy.NEVER`；④ `answerBody.trim().isNotBlank()`。
  任一不满足即丢弃，并按条 `log.warn` 记录被丢弃的 id 与原因。**不得静默丢弃。**
- Applies to: 新增 `QaFactRetriever` 的结果消费点。
- Violation consequence: 模型幻觉出的 id、或已被运营停用/置 NEVER 的规则正文进入外发邮件，
  绕过 `GroundedAutoReplyDecisionService.verifyAutoEvidenceRuleIds`（`:105-115`）之前的所有防线。
- 来源: K-answerbody-source-exclusive（事实正文单一来源）

### Invariant I-5: 检索结果必须进入 `sendQaRuleIds`，两条路径口径一致
- Rule: 采纳的检索事实必须同时出现在该 request 的 `factRuleIds` 与该封信的 `sendQaRuleIds` 中。
- Applies to:
  - `workbenchResult()`（`:354`）：`sendIds = ordered.flatMap { it.factRuleIds }.distinct()`（`:367`）——
    天然满足，无需改动。
  - `select()`（`:22`）：`sendQaRuleIds = orderEvidenceRuleIds(requestFacts, promptPool)`（`:64`），
    而 `orderEvidenceRuleIds`（`:544-559`）**只读 `item.intents[*].evidenceRuleIds`，不读 `factRuleIds`** ——
    **必须改动**，否则检索到的事实进得了正文却进不了审计。
- Violation consequence: 正文引用了事实 X，`mail_record_qa_rule` 里没有 X →
  `QaRuleAuditService.resolveSelectedRuleIds`（`:74-88`）读出的选用规则与实际外发不符；
  并且 `GroundedAutoReplyDecisionService.passesSendGate` 的
  `draft.qaRuleIds != verifiedAutoRuleIds`（`:169-171`）判定基础被破坏。
- 来源: K-ai-reply-prompt-vs-send-rule-ids、K-audit-selected-source

### Invariant I-6: status 的新口径 —— 有事实就不是 UNSUPPORTED
- Rule: `buildRequestFact` 的 status 表达式在今日全部分支之后追加一层：
  若今日结论为 `UNSUPPORTED` **且** 本条最终 `factRuleIds` 非空，则降为 `PARTIAL`（不得直接判 `GROUNDED`）。
  其余分支逐字不变。
- Applies to: `QaFactSelectionService.buildRequestFact:506-514`。
- Violation consequence:
  - 若不改：检索到的事实进了 `factRuleIds`，但 `AutoReplyConfidenceScorer:41` 仍按 UNSUPPORTED 计权（0.35），
    工作台仍推荐 `ANSWER_FROM_OPERATOR_INPUT`（`TrustReplyWorkbenchService:2280`），
    产出仍是"库里没有确认口径"那句 —— **本计划的可观察结果不成立**。
  - 若直接判 GROUNDED：`GroundedAutoReplyDecisionService.passesSendGate:172-178` 对
    PARTIAL/UNSUPPORTED 一律拒发，跳过 PARTIAL 直接放开自动发是**扩大自动发面**，超出本计划授权。
- 来源: K-grounding-status-ui-only（状态是操作端控制数据，不得外发）

### Invariant I-7: 确定性 —— 同一输入同一输出
- Rule: 检索调用必须 `temperature = 0.0`；同一 `(inboundText, 规则集指纹)` 在同一进程生命周期内
  只调用一次 LLM，其后命中进程内缓存。规则集指纹 = 对 `promptPool` 按 id 升序拼接
  `id|updatedAt|sha256(answerBody)` 后取 SHA-256（与 `AiReplyDraftService` 既有证据快照口径同源）。
- Applies to: 新增 `QaFactRetriever`。
- Violation consequence: 同一封信两次 bootstrap 得到不同 `factRuleIds` →
  `workbenchResult` 的 `sendIds` 变 → `TrustReplyWorkbenchService` 的
  `requestEvidenceVersion`（`:1933`）与 `versionId` 变 → 工作台反复提示"来源或事实已变化"，
  运营锁定的条目被反复作废。
- 来源: K-ai-reply-evidence-version-deterministic、K-workbench-evidence-two-layer-global-coupling

### Invariant I-8: fail-open 且可分类
- Rule: `QaFactRetriever.retrieve(...)` 的每一条失败路径（LLM 关闭、bean 缺席、`chat` 抛异常、
  空响应、JSON 不可解析、全部 id 校验失败）都返回 `FactRetrieval(available = false, byRequestIndex = emptyMap())`，
  绝不抛进调用方；同时按失败类型写一行 `log.warn`，类型枚举至少区分
  `DISABLED / CLIENT_ABSENT / TRANSPORT_ERROR / EMPTY_RESPONSE / PARSE_ERROR / ALL_REJECTED`。
- Applies to: `QaFactRetriever`。
- Violation consequence: 收信链路（`BatchAutoMailReplyService` → `AutoMailReplyService` → `select()`）
  会因为一次 LLM 故障而整体失败；且运营无法区分"模型说没有"与"模型没跑"。
- 来源: K-llm-timeout-fallback

### Invariant I-9: 每条摘要的检索上限，且截断必须可见
- Rule: 单条 request 采纳的检索事实数上限 `maxFactsPerRequest`（默认 3）。
  超限时按模型返回顺序截断，并 `log.warn` 记录被丢弃的 id 数量与全部 id。
- Applies to: `QaFactRetriever` 结果消费点。
- Violation consequence: 模型把全库塞给每个问题 → 自动回复把全库当依据（
  这正是 K-workbench-matrix-path-is-operator-scoped 明确警告的反面后果）；
  静默截断则让"覆盖完整"成为假象。

---

## 现状审计

### A. `QaFactSelectionService`（`src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt`）

**A-1 · 公开入口与生产调用点**（`grep -rn "qaFactSelectionService\." src/main`，14 个调用行）

| 入口 | 签名位置 | 生产调用点 |
|---|---|---|
| `select(inboundText, selectedRuleIds, researchProfileSufficient)` | `:22` | `AiReplyDraftService.kt:2156`、`PendingMailOperationService.kt:214/516/532/533/614/633/851/853/1038/1041/1044` —— **共 12 处** |
| `selectForWorkbench(inboundText, selectionsByRequest, requestedFactIds, researchProfileSufficient)` | `:150` | `TrustReplyWorkbenchService.kt:1906`（`resolveCanonicalSelection`）—— **唯一 1 处** |
| `partitionExplicitSelection(inboundText, ruleIds)` | `:112` | `PendingMailOperationService.kt:618` —— 1 处 |

**A-2 · `buildRequestFact` 的 5 个调用点**（`grep -rn "buildRequestFact" src/main`）

| file:line | 所在函数 | promptPool |
|---|---|---|
| `:52` | `select()` | `explicitRules ?: matchableRules`（`:36`） |
| `:236` | `resolveMatrixSelection()` | `explicitRules`（该 request 的运营矩阵） |
| `:278` | `resolveLegacySelection()` 空选早返回 | `emptyList()` |
| `:304` | `resolveLegacySelection()` 主分支 | `remaining.toList()` |
| `:337` | `resolveAutoSelection()` | `remaining.toList()` |

**A-3 · 候选集与 status 的产生链**（`:453-514`，逐字）

```
normalizedRequest = QaFactKeywordMatcher.normalize(requestText)          :453
matchedSpans      = AiReplyIntentCatalog.matchIntentsWithSpans(...)      :457
strictCandidateRules = promptPool.filter { matchesRule(rule, normalized) } :466-471
assignments       = AiReplyIntentCatalog.assignRulesToIntents(...)       :473
intentCoverages   = matchedIntents.map { resolveIntentEvidence(...) }    :474-481
status            = when { ... }                                        :506-514
evidenceSet       = intentCoverages.filter{SUPPORTED}.flatMap{evidenceRuleIds}.toSet()  :516-519
factRuleIds       = strictCandidateRules.mapNotNull{id}.filter{ it in evidenceSet }     :521-523
```

`QaFactKeywordMatcher.normalize`（`:589-594`）只做 `lowercase` + 空白折叠 + `details→information`；
`matchesRule`（`:602-611`）是 `keywords.filter { normalizedText.contains(it) }`，`ANY` 模式取非空。
**无词形、无同义、无语义。** 这就是 4 条 bullet 里 3 条零命中的直接原因。

**A-4 · 已核实的既有不一致（本计划不修，仅记录）**

`buildRequestFact` 从不设置 `boundRuleIds`（返回值走 `AiReplyDraftService.kt:374` 的默认 `emptyList()`）。
三条 workbench 路径在 `:252`/`:316`/`:349` 各自 `copy` 补齐，但 **`select()`（`:22-61`）没有任何
`copy(boundRuleIds = ...)`**，因此 `select()` 返回的 `requestFacts[*].boundRuleIds` 恒空。
影响：`AiReplyDraftService.kt:463`（operator-directed 的 `boundFactsBlock`）与 `:541`（`promptRuleIds`）
在经 `select()` 得到的条目上拿到空列表。**本计划不改动此行为，新代码也不得依赖 `boundRuleIds`。**

### B. `ResolvedQaRules` 与 `RequestFactItem`

**B-1 · `ResolvedQaRules`** 声明 `AiReplyDraftService.kt:377-391`，10 个字段。
构造点 3 处：`QaFactSelectionService.kt:67`（select）、`:370`（workbenchResult）、
`AiReplyDraftService.kt:538`（generateItem 内合成单条）。

`sendQaRuleIds` 的两条产生口径**不同**：

| 路径 | 表达式 | 位置 |
|---|---|---|
| `select()` | `orderEvidenceRuleIds(requestFacts, promptPool)` —— 只读 `item.intents[*].evidenceRuleIds` | `:64` / `:544-559` |
| `workbenchResult()` | `ordered.flatMap { it.factRuleIds }.distinct()` | `:367` |

→ **这是 I-5 必须改 `select()` 的直接依据。**

`unrecognizedAskCount` / `enumeratorAvailable` / `enumeratorEnumerated` / `enumeratorClaimed`
在 `src/main` 内**只有日志读点**（`QaFactSelectionService.kt:92-95`、`TrustReplyWorkbenchService.kt:672-675`），
与 `AiReplyDraftService.kt:384-386` 注释「shadow-period measurement only (I-3)」一致。

**B-2 · `RequestFactItem`** 声明 `AiReplyDraftService.kt:349-375`，10 个字段。
`factRuleIds` 的以实例为接收者的读点共 24 处，其中会被本计划影响的关键读点：

| file:line | 用途 | 本计划影响 |
|---|---|---|
| `AiReplyDraftService.kt:539` | `sendQaRuleIds = requestFact.factRuleIds.distinct()`（generateItem 合成） | 变多 → 期望内 |
| `AiReplyDraftService.kt:541` | `promptRuleIds = (factRuleIds + boundRuleIds).distinct()` | 变多 → 期望内 |
| `AiReplyDraftService.kt:1061` | `composeVerbatimFactAnswer` 逐条取 `answerBody` | 变多 → 期望内 |
| `AiReplyDraftService.kt:1125` | `generate()` mode 判定 `any { it.factRuleIds.isNotEmpty() }` | 更容易走 QA_GROUNDED → 期望内 |
| `AutoReplyConfidenceScorer.kt:54` | `facts.count { it.factRuleIds.isNotEmpty() }` → evidenceScore | CRS 上升 → 期望内 |
| `TrustReplyWorkbenchService.kt:2117/2135` | `toCoverage` 的 adjacent 计算 | 邻近集变小 → 与 03 计划方向一致 |
| `TrustReplyWorkbenchService.kt:2297` | `requireHandlingPrerequisites` 空则 422 | 变多 → 只会更宽松 |
| `QaFactSelectionService.kt:245/253` | `resolveMatrixSelection` 的 `matchedIds` / `intentMatchedFactRuleIds` | **I-2 保证此路径不受影响** |

**B-3 · status 的下游消费者（决定 I-6 必须存在）**

- `AutoReplyConfidenceScorer.kt:41-48` — `weightFor`: GROUNDED 1.0 / PARTIAL 0.6 / UNSUPPORTED 0.35，
  `COVERAGE_MAX = 40.0`。
- `GroundedAutoReplyDecisionService.kt:172-178`（`passesSendGate`）与 `:233-236`（`hasGroundingGap`）——
  **任一条目 PARTIAL 或 UNSUPPORTED 即拒绝自动发**。
  → 本计划把 UNSUPPORTED 降为 PARTIAL **不会扩大自动发面**（两者同样被拒），只提高 CRS 与推荐 handling。
- `TrustReplyWorkbenchService.kt:2277-2280` — `recommendedHandling`：UNSUPPORTED → `ANSWER_FROM_OPERATOR_INPUT`；
  PARTIAL → `ANSWER_SUPPORTED_PART`。
- `QaFactSelectionService.kt:72/76-77` 与 `:375/379-380` — `unsupportedRequests` 与 `groundedRequestCount`。

### C. `mail_record_qa_rule`（外发审计）

- 迁移：`src/main/resources/db/migration/V42__mail_record_qa_rule.sql`（**唯一**触及该表的迁移），
  列 `mail_record_id / qa_rule_id / ordinal`，唯一键 `(mail_record_id, qa_rule_id)`。
- 领域类 `mail/domain/MailRecordQaRule.kt:7-13`；仓库 `mail/repository/MailRecordQaRuleRepository.kt:6-8`。
- **写路径 3 处**：`AutoMailReplyService.kt:636-644`（`decision.qaRuleIds`）、
  `ManualReplySendAttemptService.kt:252-262`（`payload.canonicalQaRuleIds`）、
  `ManualExpertMailService.kt:89-100`（`composed.qaRuleIds`）。三者都以下标为 `ordinal`。
  **无删除/更新路径。**
- **读路径 1 处**：`qa/service/QaRuleAuditService.kt:81-86`。
- 本计划**不新增/不修改**任何写路径；只让流入 `decision.qaRuleIds` 的 `sendQaRuleIds` 变多（I-5）。

### D. LLM 客户端与配置约定

- `LlmDraftClient` 是接口，与其 HTTP 实现同文件：`llm/service/HttpLlmDraftClient.kt:87-139`。
  抽象方法只有 2 个：`stitchDraft(...)`、`chat(messages, temperature = null)`；其余均有默认实现。
- `HttpLlmDraftClient` 是 `@Component` + `@ConditionalOnProperty(prefix = "talent-introduction.llm", name = ["enabled"], havingValue = "true")`（`:141-142`）
  → **`enabled != true` 时 bean 不存在**。
- 因此全部调用方注入 `ObjectProvider<LlmDraftClient>` 并 `getIfAvailable()`：
  `AiReplyDraftService.kt:396`、`AiQaExtractionService.kt:34`、`InboundAskEnumerator.kt:54`、
  `ExpertDocumentAnalysisService.kt:36`。`CLAUDE.md:52` 明确要求保留该 fallback 模式。
- **最近的可照抄模板**：`InboundAskEnumerator.kt`（233 行）——
  构造 `(ObjectProvider<LlmDraftClient>, LlmProperties, AskEnumeratorProperties, ObjectMapper)`（`:53-58`）；
  两行守卫 `:66-70`；`try/catch` 包 `client.chat` `:71-81`；
  JSON 抽取 `extractJsonPayload` `:150-169`；硬上限 `MAX_ENUMERATED_ASKS = 12` `:198`；
  可断言的日志行构造器 `buildAskEnumLogLine` `:224-233`。
- **配置类注册**：全仓**没有** `@ConfigurationPropertiesScan`（`TalentIntroductionApplication.kt:9-14` 只有
  `@EnableScheduling` + `@SpringBootApplication`）。每个 properties 类必须被某个
  `@EnableConfigurationProperties` 列出。三个注册点：`config/RestTemplateConfig.kt:11-37`（22 个类）、
  `llm/config/AskEnumeratorProperties.kt:30-32`（**自注册，本计划照抄这一种**）、
  `task/service/TaskAuditRetentionScheduler.kt:22`。
- `LlmProperties`（`config/LlmProperties.kt:6-20`）：`enabled` / `autoReplyEnabled` / `apiUrl` / `apiKey` /
  `model` / `replyFlashModel` / `replyProModel` / `timeoutMs` / `temperature=0.3` /
  `freeFormTemperature=0.6` / `shadowScoringEnabled`。`application.yml:115-124` 只绑了其中 9 个，
  `temperature` 与 `free-form-temperature` **无 yml 键**（`grep -rn "temperature" src/main/resources/application.yml` 零命中）。
- **缓存：全仓无框架可用。** `grep -rn "@Cacheable\|@EnableCaching\|CacheManager\|Caffeine" src/main/kotlin pom.xml src/main/resources` 零命中；
  `pom.xml` 无 `spring-boot-starter-cache`/caffeine/redis。
  `llm`/`qa` 模块内的两处 `ConcurrentHashMap`（`AiReplyDraftService.kt:77` 取消监听器、
  `AiReplyGenerationCoordinator.kt:29` 在途生成登记）**都不是缓存**。
  → I-7 的缓存只能手写为服务字段上的 `ConcurrentHashMap`。

### E. 测试约定

- 依赖只有 `spring-boot-starter-test` + `testcontainers-mysql`（`pom.xml:77-87`）；
  **无 mockk、无显式 assertj**。实际用 JUnit 5 `org.junit.jupiter.api.Assertions.*` + Mockito。
- Kotlin all-open 插件已开（`pom.xml:155-165`），所以 `Mockito.mock(具体@Service类)` 可用。
- `InboundAskEnumeratorTest.kt:19-51` 是**新 LLM 服务测试的照抄模板**：
  匿名 `object : LlmDraftClient` 只覆写两个抽象方法；
  `Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>` + `@Suppress("UNCHECKED_CAST")`；
  `ObjectMapper().registerKotlinModule()`；直接构造 `LlmProperties(enabled = true)`。
- `QaFactSelectionServiceTest.kt:20-22` 用 `Mockito.mock(QaRuleRepository::class.java)` +
  单参构造 `QaFactSelectionService(repository)`；枚举器测试用
  `Mockito.mock(InboundAskEnumerator::class.java)` 与 `Mockito.verify(..., never())`（`:1637-1653`）。
  → 新增的 retriever 构造参数**必须可空且带默认值**，否则这 1740 行测试全部编译失败。

### Interaction points

| # | 写路径 | 读路径 | 本计划的处理 |
|---|---|---|---|
| IP-1 | `buildRequestFact` 写 `factRuleIds` | `AutoReplyConfidenceScorer.kt:54` 读 → evidenceScore | 期望内上升；A-3 覆盖 |
| IP-2 | `select()` 写 `sendQaRuleIds` | `AiReplyDraftService.kt:1802/1872/2109` → `AiReplyDraftResult.qaRuleIds` → `AutoMailReplyService.kt:637` 写审计表 | I-5 必须改 `orderEvidenceRuleIds` 口径，否则正文与审计不一致 |
| IP-3 | `buildRequestFact` 写 `status` | `GroundedAutoReplyDecisionService.kt:172-178` 自动发门禁 | I-6 只做 UNSUPPORTED→PARTIAL，两者同样被门禁拒绝，自动发面不扩大 |
| IP-4 | `workbenchResult` 写 `factRuleIds` | `TrustReplyWorkbenchService.kt:1933` `requestEvidenceVersion` / `:1985` `canonicalMatrix` | I-7 的确定性缓存是前提；否则版本反复漂 |
| IP-5 | 新 retriever 读 `qa_rule` | 运营 UI 运行时改 keywords/enabled（`QaRuleManagementService`） | 规则集指纹含 `updatedAt` + 正文哈希，运营一改即缓存失效（来源: K-qa-rule-runtime-vs-migration-writes） |

---

## 实现方案

### 阶段 1 · 新增 `QaFactRetriever`（I-4 / I-7 / I-8 / I-9）

**T1.1** 新建 `src/main/kotlin/com/weibo/talentintroduction/llm/config/FactRetrieverProperties.kt`，
照抄 `AskEnumeratorProperties.kt:19-32` 的自注册形态：

```kotlin
@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.llm.fact-retriever")
data class FactRetrieverProperties(
    val enabled: Boolean = false,
    val enabledForAutoReply: Boolean = false,
    val maxFactsPerRequest: Int = 3,
    val maxRulesInPrompt: Int = 60,
    val cacheEntries: Int = 200
)

@Configuration
@EnableConfigurationProperties(FactRetrieverProperties::class)
class FactRetrieverPropertiesConfig
```

两个开关分离的理由（与 `AskEnumeratorProperties` 同构）：工作台是人看着的，
自动回复跑在 IMAP 拉取循环里（`BatchAutoMailReplyService`），先只对工作台开。

**T1.2** 新建 `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetriever.kt`：

- 构造：`(ObjectProvider<LlmDraftClient>, LlmProperties, FactRetrieverProperties, ObjectMapper)`。
- `fun retrieve(inboundText: String, requests: List<String>, pool: List<QaRule>): FactRetrieval`
- 守卫（I-8）：`!properties.enabled` → `DISABLED`；`!llmProperties.enabled` → `DISABLED`；
  `getIfAvailable() == null` → `CLIENT_ABSENT`；`pool.isEmpty() || requests.isEmpty()` → 直接空结果。
- prompt：system 常量 `FACT_RETRIEVAL_SYSTEM_PROMPT`（本计划逐字定稿，见下）；
  user 部分依次给「按序号编号的诉求列表」与「规则清单」，每条规则一行
  `id | displayName ?: replySubject ?: "Rule $id" | answerBody`，按 `pool` 原序，
  取前 `maxRulesInPrompt` 条，被截断时 `log.warn` 记录截断数量（I-9 的同类要求）。
- 调用：`client.chatWithModelObservedJson(messages, temperature = 0.0, providerModel = llmProperties.model)`；
  **必须显式传 0.0**（I-7），不得走 `LlmProperties.temperature`（默认 0.3）。
- 解析：照抄 `InboundAskEnumerator.extractJsonPayload`（`:150-169`）的 fence/数组抽取；
  期望 `[{"requestIndex": 1, "ruleIds": [12, 34]}, ...]`。
- 校验（I-4）：逐 id 过四道；`requestIndex` 越界丢弃；每条 request 截断到 `maxFactsPerRequest`（I-9）。
- 日志：新增顶层纯函数 `buildFactRetrievalLogLine(source, available, requested, returned, accepted, rejected, truncated, outcome)`，
  形态照抄 `buildAskEnumLogLine`（`InboundAskEnumerator.kt:224-233`），便于测试逐字断言。
- 缓存（I-7）：服务字段 `private val cache = ConcurrentHashMap<String, FactRetrieval>()`，
  键 = `sha256(inboundText) + ":" + poolFingerprint(pool)`，
  `poolFingerprint` = 按 `id` 升序拼 `id|updatedAt|sha256(answerBody)` 后取 SHA-256。
  容量超过 `cacheEntries` 时整表 `clear()`（简单可预期，避免引入淘汰算法）。

**定稿 system prompt（逐字，执行时不得改写）：**

```
You select which approved facts answer each numbered request from an inbound email.
You are given a numbered list of requests and a numbered catalogue of approved facts.
Return ONLY a JSON array. Each element must have:
- requestIndex (integer, one of the request numbers given)
- ruleIds (array of integers, each one of the fact ids given)
Select a fact only when it directly answers that request. Prefer fewer, more precise facts.
Never invent an id that is not in the catalogue. Never write prose, explanations, or answer text.
If no fact answers a request, omit that request from the array.
Do not include markdown fences or commentary outside the JSON array.
```

### 阶段 2 · 接入 `QaFactSelectionService`（I-1 / I-2 / I-3 / I-5 / I-6）

**T2.1** 构造参数追加（必须可空+默认值，见审计 E）：

```kotlin
class QaFactSelectionService(
    private val qaRuleRepository: QaRuleRepository,
    private val inboundAskEnumerator: InboundAskEnumerator? = null,
    private val askEnumeratorProperties: AskEnumeratorProperties = AskEnumeratorProperties(),
    private val qaFactRetriever: QaFactRetriever? = null,
    private val factRetrieverProperties: FactRetrieverProperties = FactRetrieverProperties()
)
```

**T2.2** `buildRequestFact` 新增形参 `retrievedRuleIds: List<Long> = emptyList()`，
并在 5 个调用点**逐个显式赋值**（I-2）：`:236`/`:278`/`:304` 传 `emptyList()`；
`:52`（select）与 `:337`（resolveAutoSelection）传本 request 的检索结果。

**T2.3** 候选集并集（I-3）。在 `:466-471` 的 `strictCandidateRules` 之后新增：

```kotlin
val retrievedRules = promptPool.filter { it.id != null && it.id in retrievedRuleIds.toSet() }
    .filter { it !in strictCandidateRules }
val candidateRules = strictCandidateRules + retrievedRules
```

`assignRulesToIntents` 的入参**保持 `strictCandidateRules` 不变**（I-1：不让检索结果影响 intent 分配，
从而不影响 `intentCoverages`、不影响 `requestKey`）。

**T2.4** `factRuleIds` 合并（I-1）：

```kotlin
val factRuleIds = (strictCandidateRules.mapNotNull { it.id }.filter { it in evidenceSet }
    + retrievedRules.mapNotNull { it.id }).distinct()
```

新增诊断字段 `retrievedFactRuleIds = retrievedRules.mapNotNull { it.id }`
（加在 `RequestFactItem` 末尾，带默认值 `emptyList()`），只做展示与审计，
**不进任何哈希**——与既有 `intentMatchedFactRuleIds`/`intentMismatchFactRuleIds`
（`AiReplyDraftService.kt:359-366` 注释「绝不进入授权/版本/发送」）同级。

**T2.5** status（I-6）。在 `:506-514` 的 `when` **之后**追加：

```kotlin
val status = if (naturalStatus == RequestGroundingStatus.UNSUPPORTED && factRuleIds.isNotEmpty()) {
    RequestGroundingStatus.PARTIAL
} else {
    naturalStatus
}
```

**T2.6** `sendQaRuleIds`（I-5）。`orderEvidenceRuleIds`（`:544-559`）在既有的
intent 证据序之后，追加各 request 的 `factRuleIds` 中尚未出现的 id（保 request 顺序、保 `linkedSetOf` 去重）。
`workbenchResult()`（`:367`）无需改动。

**T2.7** 调用检索。`select()` 在 `:36` 得到 `promptPool` 之后、`:48` 的 `requestFacts` 之前插入；
门控与枚举器同构：仅当 `factRetrieverProperties.enabledForAutoReply` 为真才调用（`select` 是自动/人工发送路径）。
`selectForWorkbench` 的 auto 分支（`:182-191`）在 `resolveAutoSelection` 前插入，
门控为 `factRetrieverProperties.enabled`（工作台不受 auto 开关限制，与 `:167-168` 枚举器同构）。

**T2.8** 日志。两处调用点各打一行 `buildFactRetrievalLogLine(source = "AUTO" / "WORKBENCH", ...)`。

### 阶段 3 · 配置与测试

**T3.1** `application.yml` 在 `llm:` 块（`:115-124`）内追加：

```yaml
    fact-retriever:
      enabled: ${LLM_FACT_RETRIEVER_ENABLED:false}
      enabled-for-auto-reply: ${LLM_FACT_RETRIEVER_AUTO_REPLY_ENABLED:false}
      max-facts-per-request: ${LLM_FACT_RETRIEVER_MAX_FACTS:3}
      max-rules-in-prompt: ${LLM_FACT_RETRIEVER_MAX_RULES:60}
```

**T3.2** 新建 `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetrieverTest.kt`，
照抄 `InboundAskEnumeratorTest.kt:19-51` 的 stub 形态。

**T3.3** 新建 `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionRetrievalTest.kt`。

---

## 变更文件清单（7 个，≤10 ✅；子系统 1 个：QA 事实选择 ✅）

| # | 文件 | 新增/修改 | 涉及不变量 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/config/FactRetrieverProperties.kt` | 新增 | I-9 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetriever.kt` | 新增 | I-4 I-7 I-8 I-9 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | 修改 | I-1 I-2 I-3 I-5 I-6 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 修改（**仅** `RequestFactItem` 末尾追加 `retrievedFactRuleIds` 字段，带默认值） | I-1 |
| 5 | `src/main/resources/application.yml` | 修改（`llm:` 块内追加 `fact-retriever:` 4 键） | — |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetrieverTest.kt` | 新增 | I-4 I-7 I-8 I-9 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionRetrievalTest.kt` | 新增 | I-1 I-2 I-3 I-5 I-6 |

（7 个文件。`AiReplyDraftService.kt` 只动 data class 的一行字段声明——它是 `RequestFactItem` 的宿主文件，
见 `AiReplyDraftService.kt:349-375`；不得在本计划里改该文件的任何其他内容。）

---

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。来源：`CLAUDE.md:7`。

```bash
# 全量测试（回归门禁；JS 测试通过 pom.xml:203-217 的 node-test execution 绑定在 test 阶段）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的两个测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactRetrieverTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionRetrievalTest

# 受影响的既有测试类（必须全绿，证明 What-must-NOT-change）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyDraftServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchItemFlowTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=GroundedAutoReplyDecisionServiceTest

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：每条 `mvn` 退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`。
来源：`CLAUDE.md:7-27`（Commands 章节，含单类过滤语法 `-Dtest=ClassName`）。

---

## 验收标准

- **I-1**：`grep -n "intents\s*=" src/main/kotlin/.../QaFactSelectionService.kt` 的结果中，
  `buildRequestFact` 返回的 `intents = intentCoverages` 表达式未被修改；
  且 `assignRulesToIntents` 的第一个实参仍是 `strictCandidateRules`（不是 `candidateRules`）。
  测试：同一 requestText，开/关检索两次调用，断言 `item.intents.map { it.intentKey }` 逐元素相等。
- **I-2**：测试断言 `resolveMatrixSelection` 路径下即使 retriever 返回 id，
  结果 `factRuleIds == explicitIds` 且 `retrievedFactRuleIds.isEmpty()`。
  另用 `Mockito.verify(retriever, never()).retrieve(...)` 断言矩阵路径不调用检索。
- **I-3**：测试：keyword 命中规则 A，retriever 只返回 B；断言 `factRuleIds` 同时含 A 与 B，且 A 在前。
- **I-4**：测试四种非法 id（不在 pool / `enabled=false` / `replyPolicy=NEVER` / `answerBody` 空白）
  各自被丢弃，且日志行的 `rejected` 计数为 4。
- **I-5**：测试 `select()` 返回的 `sendQaRuleIds` 包含仅由检索得到的 id；
  且 `workbenchResult` 路径下 `sendQaRuleIds == requestFacts.flatMap{factRuleIds}.distinct()`。
- **I-6**：测试三态——零事实仍 UNSUPPORTED；仅检索得到事实 → PARTIAL；
  全部 intent SUPPORTED → 仍 GROUNDED。并断言 `GroundedAutoReplyDecisionService.passesSendGate`
  对 PARTIAL 仍返回 false（自动发面未扩大）。
- **I-7**：测试同一 `(inboundText, pool)` 连续调用两次，`Mockito.verify(client, times(1))`；
  改动 pool 中任一 `answerBody` 后再调用，`times(2)`。并断言传给 client 的 `temperature == 0.0`。
- **I-8**：六种失败路径各一个测试，断言返回 `available == false` 且不抛异常，
  且 `select()` 的返回值与 retriever 为 null 时逐字段相等。
- **I-9**：retriever 对某 request 返回 5 个合法 id，断言只采纳 3 个，且日志行 `truncated == 2`。
- 回归：执行「验证命令」节的全量测试命令通过。

---

## 人工验收清单

### A-1: 零命中诉求现在能绑上事实（覆盖 需求描述 outcome 1；I-3 I-5 I-6）
- 前置条件：
  1. 后台「QA 规则」页确认 `Pre-contract IP boundary` 处于启用、回复策略非"从不"、事实正文非空。
  2. 环境变量设 `LLM_ENABLED=true`、`LLM_FACT_RETRIEVER_ENABLED=true`，重启应用。
  3. 准备一封收件内容含且仅含一行：`Who owns IP arising from advisory input—me or the company?`
- 操作步骤：
  1. 进入该来信的「未匹配收件」详情页，打开可信回复工作台。
  2. 展开第 1 条摘要。
- 预期结果：
  - 该条状态徽标为 **`依据部分`**（PARTIAL），不再是 `依据不足`。
  - 「已选事实」区出现 **`Pre-contract IP boundary`**。
  - 处理方式下拉的推荐值是 **`按有据部分回答`**，不再是 `按回答说明生成`。

### A-2: 检索到的事实进入外发审计（覆盖 I-5；interaction point IP-2）
- 前置条件：同 A-1，且该联系人可发信。
- 操作步骤：
  1. 在工作台点「一键预判」，待整合完成后采用并发送一封回复。
  2. 到「QA 使用审计」查看该封外发记录的"实际选用规则"。
- 预期结果：列表中出现 `Pre-contract IP boundary`，且其序号与正文中该口径出现的段落顺序一致。

### A-3: LLM 关闭时逐字退化（覆盖 must-NOT-change 第 3 条；I-8）
- 前置条件：先在 A-1 的环境下记录该来信工作台的完整截图（含每条摘要的状态徽标与已选事实）。
- 操作步骤：
  1. 设 `LLM_FACT_RETRIEVER_ENABLED=false`，重启应用。
  2. 重新打开同一封来信的工作台（先点「重置」清空既有编排）。
- 预期结果：每条摘要的状态徽标与已选事实与本计划落地**之前**完全一致
  （IP 那条回到 `依据不足`、已选事实为空）。应用日志中出现一行
  `[FACT_RETRIEVAL] source=WORKBENCH available=false ... outcome=DISABLED`。

### A-4: 运营手动矩阵不受影响（覆盖 must-NOT-change 第 1 条；I-2）
- 前置条件：A-1 的环境（检索开启）。
- 操作步骤：
  1. 在工作台第 1 条摘要上手动添加一条与该问题无关的事实（例如 `Participant fee policy`）。
  2. 观察该条「已选事实」区。
  3. 刷新页面（不重置），再次观察。
- 预期结果：
  - 「已选事实」**恰好**是运营勾选的那一条，检索补的 `Pre-contract IP boundary` **不在**其中；
  - 不出现 422 错误，工作台正常加载；
  - 处理方式仍可选中「按事实原文回答」，选中后生成的正文是该事实的 `answerBody` 逐字内容。

### A-5: 自动发面未扩大（覆盖 I-6 的 violation consequence；interaction point IP-3）
- 前置条件：A-1 的环境。
- 操作步骤：在该来信的「自动回复预判」区查看判定与硬性闸门。
- 预期结果：判定仍为 **`转人工`**（因为存在 PARTIAL 条目），硬性闸门列表非空。
  **不得**出现 `可自动发`。

### A-6: 两次打开结果一致（覆盖 I-7）
- 前置条件：A-1 的环境。
- 操作步骤：打开工作台 → 记录第 1 条的「已选事实」→ 关闭页面 → 5 分钟内重新打开。
- 预期结果：两次的已选事实集合与顺序完全一致；页面顶部**不**出现"来源或事实已变化，请确认后刷新工作台"。
