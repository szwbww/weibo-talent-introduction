# P1-6 修复：RequestFactItem 事实顺序保持 candidate 顺序

## 需求描述

修复复合 request 的 `RequestFactItem.factRuleIds` 顺序：只保留已被 supported intent 使用的规则，同时严格保持该 request 的 `candidateRuleIds` 原始有效顺序。这样 grounded LLM prompt 与 deterministic fallback 的事实段落顺序一致、可预测。

不得改变 intent 识别、coverage key 判定、group status、`sendQaRuleIds` / `promptRuleIds` 语义、跨 request 证据隔离或任何 API schema。

Out of scope：迁移、QA 规则数据、intent catalog、controller、前端、AI 回复正文标题/编号策略，以及 main 当前其他未提交改动。

## 关键不变量

### Invariant I-1: supported evidence 的 candidate 顺序

- Rule：对每个 `gapItem`，`factRuleIds` 必须等于 `validFactRuleIds` 中属于 supported intent `evidenceRuleIds` 并集的 ID，按 `validFactRuleIds` 原顺序去重；不得按 intent catalog 顺序重排。
- Applies to：`AiReplyDraftService.resolveQaRules()` 写入 `RequestFactItem.factRuleIds` 的路径。
- Violation consequence：同一 request 的 fallback 正文及 LLM approved-facts prompt 会以 catalog 顺序而非 QA matcher candidate 顺序展示事实，导致可观察文案顺序漂移。
- 来源：原 P1-6 T2「supported intent evidence union，保持原顺序去重」；K-request-facts-not-flat-pool。

### Invariant I-2: 过滤边界不变

- Rule：修复只能改变 evidence union 的排序步骤；候选仍必须先受当前 group、`promptSet`、非空 `replyBody` 限制，且只允许 supported intent 的 evidence 进入 `factRuleIds`。
- Applies to：`AiReplyDraftService.resolveQaRules()` 的 candidate 过滤、intent coverage 聚合与 `RequestFactItem` 创建。
- Violation consequence：无关规则可能进入某 request 的 approved facts，或 PARTIAL/UNSUPPORTED 的缺口语义被掩盖。
- 来源：K-request-facts-not-flat-pool；K-compound-request-coverage-intent-atomic。

### Invariant I-3: 审计与下游消费者不变

- Rule：`sendQaRuleIds` 保持 `QaMatchService.suggestComposition(...).suggestedRuleIds` 的外发审计子集；`promptRuleIds` 回退逻辑不变。只允许 `factRuleIds` 的顺序变化，并让 grounded prompt 与 deterministic fallback 读取同一顺序。
- Applies to：`ResolvedQaRules` 返回值、`buildGroundedUserContent()` 与 `AiReplyPointByPointComposer.composeFallback()`。
- Violation consequence：错误把 prompt 全集写入外发审计，或两个下游消费者使用不同事实顺序。
- 来源：K-ai-reply-prompt-vs-send-rule-ids；K-request-facts-not-flat-pool。

## 现状审计

### RequestFactItem 临时事实矩阵

- Schema/mapping：无 DB、ES、缓存或迁移改动；`RequestFactItem` 是内存数据类，字段 `factRuleIds: List<Long>` 定义于 `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:61-67`。
- Write paths：
  1. `AiReplyDraftService.resolveQaRules()`（`AiReplyDraftService.kt:508-566`）从 `gapItems.candidateRuleIds` 过滤当前 promptSet 与有效正文，解析 intent evidence 后写入 `RequestFactItem.factRuleIds`。
- Read paths：
  1. `AiReplyDraftService.buildGroundedUserContent()`（`AiReplyDraftService.kt:773-794`）顺序遍历 `factRuleIds`，生成每个 request 的 LLM approved-facts 块。
  2. `AiReplyPointByPointComposer.composeFallback()` / `joinFacts()`（`AiReplyPointByPointComposer.kt:47-79,111-121`）顺序遍历 `factRuleIds`，生成 deterministic fallback 正文。
  3. 两个 controller 将该列表原样输出为 request coverage；本计划不修改其 schema，因为排序由写入路径统一保证。
- Interaction points：`resolveQaRules()` 写入的顺序同时被 LLM prompt 与 fallback 消费；测试必须证明反序 candidate 输入在两个消费语义下均得到 candidate 顺序，而不改变 `sendQaRuleIds`。

### 已有知识复核

- `K-request-facts-not-flat-pool`：已确认 request→factRuleIds 矩阵同时供生成与 fallback 使用；本计划将「按原邮件顺序」细化为 request 内 candidate 顺序。
- `K-ai-reply-prompt-vs-send-rule-ids`：已确认本修复不得碰 `sendQaRuleIds` 与 prompt fallback 的边界。
- `K-compound-request-coverage-intent-atomic`：已确认 evidence membership 仍由 intent coverage 决定，不能为修复顺序而放宽 coverage 条件。

## 实现方案

### T1：以 candidate 顺序投影 supported evidence

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
- 遵守：I-1、I-2、I-3。
- 在已计算的 `intentCoverages` 上先得到 supported intent evidence ID 集合，再以 `validFactRuleIds` 为唯一排序来源过滤该集合；保持现有有效候选去重语义。
- 保留现有 group status 计算、`missingEvidenceKeys`、`unsupportedRequests`、`sendQaRuleIds`、`promptRuleIds` 与 catalog 解析，不调整任何其他路径。
- 下游：`buildGroundedUserContent()` 与 `AiReplyPointByPointComposer` 无需改动，继续消费写入后的同一有序列表。

### T2：增加反序 candidate 的服务回归

- 文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- 遵守：I-1、I-2、I-3。
- 构造一个命中至少两个 supported intent 的复合 request：`gapItems.candidateRuleIds` 故意设为与 catalog intent 顺序相反，两个 QA 规则均有非空正文及各自 coverage key。
- 断言 `RequestFactItem.factRuleIds` 精确等于反序 candidate 的有效顺序；同时断言 group 仍为 `GROUNDED`、两个 intent 都是 `SUPPORTED`，且 `sendQaRuleIds` 不被本修复扩大或重排。
- 通过 `buildGroundedUserContent()` 或现有 deterministic fallback 可观察输出断言，确认规则正文按相同 candidate 顺序出现，覆盖写入→读取 interaction point。

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | 以有效 candidate 列表投影 supported-evidence 集合，修正 `factRuleIds` 顺序。 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 增加反序 candidate 的 coverage、审计边界与下游事实顺序回归。 |

## 验收标准

- I-1：反序 `candidateRuleIds` 的复合 request 返回 `factRuleIds` 与其有效 candidate 顺序完全一致，不按 catalog intent 顺序排列。
- I-2：同一用例的两个 intent 均为 `SUPPORTED`、group 为 `GROUNDED`；空正文、非 promptSet、missing intent 的现有测试继续通过，且不新增任何无关 evidence。
- I-3：断言 `sendQaRuleIds` 保持 QA matcher 的原值；grounded prompt 或 fallback 中两条规则正文的出现顺序与 `factRuleIds` 一致。
- 定向回归：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyDraftServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest,QaMatchServiceTest test
```

- 变更范围检查：`git diff --name-only` 仅包含本计划列出的两个实现/测试文件（以及本计划文档）；不得修改 migration、controller、intent catalog、前端或既有 main 改动。

## 人工验收清单

### A-1: 复合 request 的反序候选事实顺序

- 前置条件：测试或本地 QA 数据中存在两条有效规则：规则 2 覆盖 `role.deliverables`、正文为 `Deliverables body`；规则 1 覆盖 `role.responsibilities`、正文为 `Responsibilities body`。同一 request 的候选顺序为 `[2, 1]`。
- 操作步骤：
  1. 提交 `What are the responsibilities and deliverables?` 至 AI 回复模拟入口。
  2. 查看该 request 的 generated draft 或 deterministic fallback 正文。
- 预期结果：该 request 的事实正文先出现 `Deliverables body`，后出现 `Responsibilities body`；request 状态为 `GROUNDED`，不出现缺口提示。
- 覆盖：I-1、I-2。

### A-2: 外发审计子集回归

- 前置条件：沿用 A-1 的 request；QA matcher 的 `suggestedRuleIds` 为 `[2, 1]`。
- 操作步骤：
  1. 生成 AI 草稿并查看模拟响应的 `qaRuleIds` 与 `requestCoverage[0].factRuleIds`。
  2. 不选择额外 QA 规则。
- 预期结果：`qaRuleIds` 仍为 matcher 提供的 `[2, 1]`，`requestCoverage[0].factRuleIds` 为 `[2, 1]`；不会出现 prompt fallback 中其他规则 ID。
- 覆盖：I-3。

### A-3: 部分覆盖回归

- 前置条件：仅保留覆盖 `role.responsibilities` 的规则 1，request 仍为 `What are the responsibilities and deliverables?`。
- 操作步骤：
  1. 生成 AI 草稿。
  2. 查看 request coverage。
- 预期结果：group 状态为 `PARTIAL`；`role.responsibilities` 为 `SUPPORTED`，`role.deliverables` 为 `MISSING`；`factRuleIds` 只含 `[1]`。
- 覆盖：I-2、I-3。
