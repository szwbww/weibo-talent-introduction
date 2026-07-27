# P1-6：RequestGroup / RequestIntent 原子覆盖矩阵

## 需求描述

把每条专家请求拆成稳定的原子 intent，并用 coverage keys 为每个 intent 选择本 request 自己的 QA 证据。request 的 PARTIAL 必须表示“部分 intent 有据、部分 intent 缺失”，不再依赖正文短语碰撞。

Out of scope：模型 intent JSON、最终固定标题组装、高风险 claim validator、前端确认弹窗。

## 关键不变量

### I-1：原邮件 request 顺序不变
- 继续以 `QaRequestExtractor/gapItems` 的 offset 顺序建立 group，index 从 1 开始且稳定。（K-request-extractor-offset-order）

### I-2：intent 由后端目录决定
- 模型不得创建、删除、合并 intent。
- 已知复合问法拆分；未知问题生成 `general.answer` fallback intent，不静默丢弃。

### I-3：证据隔离
- intent.evidenceRuleIds = 当前 group.candidateRuleIds ∩ promptSet 中 coverageKeys 满足该 intent 的规则。
- 不从其他 group 或 prompt 全集跨项借用。（K-request-facts-not-flat-pool）

### I-4：group 聚合固定
- 全部 intent supported → GROUNDED。
- 部分 supported、部分 missing → PARTIAL。
- 全部 missing → UNSUPPORTED。
- readiness 继续由 group status 聚合。

### I-5：研究双证据
- `expertise.programme_fit` 同时要求画像充分 + `programme.scope` QA 证据；任一缺失则该 intent missing。
- `enterprise.project_types` 是独立 intent，matching rule 不能替代。

### I-6：删除 P0 临时启发式
- 计划 1 的 facet/phrase table 在本计划删除；coverage key + intent catalog 成为唯一 partial 判定。

## intent 目录与固定标题

| 请求语义 | intentKey | 所需 QA key | 后续 section title |
|---|---|---|---|
| expertise within scope | expertise.programme_fit | programme.scope + profile | Research fit and enterprise projects |
| enterprise project types | enterprise.project_types | enterprise.project_types | 同上 |
| company legal name | company.legal_name | company.legal_name | Company details |
| registered location | company.registered_location | company.registered_location | Company details |
| programme purpose | programme.purpose | programme.purpose | Programme purpose and structure |
| programme structure/tracks | programme.structure | programme.structure 或 tracks | 同上 |
| researcher selection | researcher.selection | researcher.selection | Selection and enterprise matching |
| enterprise matching | enterprise.matching | enterprise.matching | 同上 |
| responsibilities | role.responsibilities | role.responsibilities | Responsibilities and deliverables |
| deliverables | role.deliverables | role.deliverables | 同上 |
| contractual arrangements | contract.terms | contract.party/terms | Contractual, financial and IP arrangements |
| financial arrangements | finance.arrangements | government_funding/enterprise_compensation（按请求范围） | 同上 |
| IP arrangements | ip.arrangements | ip.arrangements | 同上 |
| next stages | application.next_stages | application.steps；询问时间时另需 timeline | Next stages |

## 实现任务

### T1：新增 intent catalog 与数据结构
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt`

- 定义 `RequestIntentDefinition`：key、title、request aliases、required/alternative coverage keys、requiresProfile。
- 定义 `RequestIntentCoverage`：intentKey、title、requiredCoverageKeys、evidenceRuleIds、status、missingEvidenceKeys、requiresResearchContext。
- aliases 使用边界安全 phrase/regex，禁止 URL query 伪问题回归。
- 同一 request 可命中多个 intent，按 catalog 固定顺序去重。
- 未命中已知 intent 时创建 general.answer。

### T2：DraftService 构建 intent matrix
文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

- `RequestFactItem` 添加 `intents`。
- 对每个 gapItem 加载 candidate rules 一次，解析 coverageKeys，按 intent 过滤 evidence。
- general.answer 只使用当前 candidate valid rules；无 candidate 即 missing。
- group.factRuleIds 为 supported intent evidence union，保持原顺序去重。
- 删除 `isPartialCoverage/PARTIAL_DETAIL_*` 临时逻辑。
- unsupportedRequests 仍只列 group 全 missing；partial 的缺失原因由 nested intents 提供。

### T3：API 暴露 nested intent coverage
文件：
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`

- `RequestCoverageItem` 添加 `intents`。
- intent response：intentKey/title/status/evidenceRuleIds/missingEvidenceKeys/requiresResearchContext。
- 保留原 factRuleIds/status 字段供旧前端兼容。

### T4：服务测试
文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

- 本次 7 groups 精确 intent 列表与顺序。
- selection missing + matching supported → group PARTIAL。
- responsibilities supported + deliverables missing → PARTIAL。
- contract/IP supported + finance missing → PARTIAL。
- research profile+scope supported、project types missing → PARTIAL；profile 缺失时 fit missing。
- company 两 intent 都只使用新 company rule，不使用 credentials。
- promptSet 显式限制、sendQaRuleIds 不扩大。
- unknown request general.answer 回归。

### T5：controller 测试
文件：
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

- 两入口 nested schema 一致；missingEvidenceKeys 不进入 draftText。

## 变更文件清单（7）

1. `AiReplyIntentCatalog.kt`
2. `AiReplyDraftService.kt`
3. `AiTrainingController.kt`
4. `UnmatchedInboundMailController.kt`
5. `AiReplyDraftServiceTest.kt`
6. `AiTrainingSimulateTest.kt`
7. `UnmatchedInboundAiReplyTurnKnowledgeTest.kt`

## 验收标准

- 最新邮件 group 数仍为 7，intent 总数大于 group 数。
- 4/5/6 的 missing intent 清晰且不被同组其他事实掩盖。
- P0 phrase heuristic 全部删除。
- 自动回复测试无 coverageKeys 依赖。
- 定向测试：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyDraftServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest,QaMatchServiceTest test
```

## 人工验收清单

### A-1：查看 requestCoverage JSON
- 预期：第 4 项有 selection/matching 两 intent；第 5 项有 responsibilities/deliverables；第 6 项有 contract/finance/IP。

### A-2：补一条 deliverables QA
- 操作：为规则配置 role.deliverables 后重生成。
- 预期：只改变 deliverables intent 与第 5 group 状态，不影响其他 group。

### A-3：研究画像缺失
- 预期：programme_fit missing，系统不抓外网、不声称“aligns well”。
