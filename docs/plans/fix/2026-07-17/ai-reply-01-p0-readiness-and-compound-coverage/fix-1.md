# fix-1: P0-1 草稿就绪与复合覆盖判定

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 1 / P0-1）
- 子计划：`docs/plans/2026-07-15/ai-reply-01-p0-readiness-and-compound-coverage.md`

## 约束摘录

- I-1：`draftReadiness` 只能由 service 的 `requestFacts` 统一计算；两个 controller 只映射结果。
- I-2：同一 request 命中的每个 facet 都必须由其自身 `factRuleIds` 的 subject/body 合集提供证据；不得跨 request 或从 prompt fallback 借证据。
- I-3：临时启发式只能把状态降为 `PARTIAL`，不得改变 `factRuleIds`、`sendQaRuleIds` 或正文。
- I-4：`groundedRequestCount` 继续统计 `GROUNDED + PARTIAL`。
- I-5：临时 facet 表仅存在于 `AiReplyDraftService`，供计划 6 删除。
- T2：保留公司 `full name + registered location` 的既有判定；规范化仅做 lower-case、空白折叠、常见连字符统一。
- 范围：仅 6 个 P0-1 源码/测试文件；不改 QA 数据、迁移、前端或审计。

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 期望 |
|---|---|---|---|---|
| P1-1 | P1 | 任一有效证据以 `R&D`、`RMB` 或 `IP` 等计划列出的大小写形式出现；连字符变体出现时同样可触发 | `normalizeCoverageText()` 已 lower-case，但 facet 表仍保留 `R&D`/`RMB`/`IP` 大写字面量，且未统一常见连字符；这些有效证据被误判缺失，草稿会错误降为 `PARTIAL/NEEDS_REVIEW`。 | facet 定义与已规范化文本在同一规范域比较；常见连字符等价；计划列出的有效证据必须可使相应 facet 满足。 |
| P1-2 | P1 | 专家询问注册地址且命中的 QA 正文仅含泛泛的 `registered` 描述、没有注册地址 | `registered_location` 的证据由原有精确短语退化为 `registered`。例如 “We are a registered company.” 会把 “What is your registered location?” 标为 `GROUNDED/READY`，违反保留既有 `registered location` 判定及“不伪造完整覆盖”。 | 恢复与原判定等价的注册地址证据要求；泛泛注册表述不能满足 location facet，仍应为 `PARTIAL/NEEDS_REVIEW`。 |

## 修复规格

### P1-1 — 统一 coverage 规范域

**文件**：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

1. 让 trigger/evidence/辅助证据与 `normalizeCoverageText()` 的输出使用同一规范形式；最小改动可在定义表中全部使用小写，并在 normalizer 中将常见连字符统一为空格或普通 `-` 后再折叠空白。
2. 不引入语义模型、同义词服务或新事实来源；仍仅检查该 request 的 `factRuleIds` subject/body 合集。
3. 不改变状态聚合、`sendQaRuleIds`、`factRuleIds` 或正文。

**测试**：`AiReplyDraftServiceTest.kt`

1. 含 `R&D` 的 responsibilities/project-type 证据可满足对应 facet。
2. 含 `RMB` 的财务证据可满足 finance facet。
3. 含 `IP` 的 IP 证据可满足 IP facet。
4. request 或 rule 使用常见连字符变体时，等价 facet 仍可命中。

### P1-2 — 保持注册地址覆盖的精度

**文件**：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

1. 将 `registered_location` 的证据恢复为能证明地址/注册地址的精确条件，至少不得仅因单词 `registered` 满足。
2. 维持本计划范围：只降低不充分证据的状态，不改 QA 正文或 rule ID 集合。

**测试**：`AiReplyDraftServiceTest.kt`

1. “registered company” 不能满足 “registered location”，结果为 `PARTIAL`。
2. 含实际注册地址/等价明确 location 表述的 QA 正文可满足该 facet。

## 当前状态（修前）

- Build：PASS（定向 Maven test 编译阶段完成）。
- Tests：PASS — 112 passed, 0 failed, 0 skipped。
  - `AiReplyDraftServiceTest`: 85
  - `AiTrainingSimulateTest`: 22
  - `UnmatchedInboundAiReplyTurnKnowledgeTest`: 5
- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyDraftServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest test`

## 合规审计

| Constraint | Verdict | Evidence |
|---|---|---|
| I-1 readiness 单源 | ✅ | `AiReplyDraftService.kt:579-592` 聚合 `requestFacts`；`AiTrainingController.kt:248`、`UnmatchedInboundMailController.kt:337` 仅映射 `.name`。 |
| I-2 request 内 facet 证据 | ❌ P1-1/P1-2 | `AiReplyDraftService.kt:550-573` 仅合并传入 request 的 rule；但 `AiReplyDraftService.kt:945,960,965,984` 将已 lower-case 文本与大写证据比较，且 `979-980` 用泛化 `registered` 作为 location 证据。 |
| I-3 只降级、不改事实/正文 | ✅ | `AiReplyDraftService.kt:516-528` 仅选择 status；`531-542` 保持 rule ID 与旧统计；`415-430` 只附加 readiness。 |
| I-4 旧统计兼容 | ✅ | `AiReplyDraftService.kt:539-541` 统计 `GROUNDED || PARTIAL`。 |
| I-5 临时表可删除 | ✅ | `AiReplyDraftService.kt:923-984` companion 内单一 `TEMPORARY_COMPOUND_FACETS`。 |
| T1 readiness DTO/seam | ✅ | `AiReplyDraftService.kt:23-27,47,415,579-592`; 测试 `AiReplyDraftServiceTest.kt:2653-2701`。 |
| T3 两 API 契约 | ✅ | `AiTrainingController.kt:222-249,394-414`; `UnmatchedInboundMailController.kt:315-338,553-569`。 |
| T4 服务测试 | ❌ P1-1/P1-2 | `AiReplyDraftServiceTest.kt:2704-2934` 覆盖主要组合，但未覆盖规范化大小写/连字符或泛化 `registered` 的错误升级。 |
| T5 controller 契约测试 | ✅ | `AiTrainingSimulateTest.kt:196,362`; `UnmatchedInboundAiReplyTurnKnowledgeTest.kt:236,323`。 |
| Deleted code | ✅ | `PARTIAL_DETAIL_PHRASES` 已由 `TEMPORARY_COMPOUND_FACETS` 替换。 |
| No extras | ✅（受限） | Phase 1 审计只纳入计划列出的 6 个文件；main 其他脏改动按委托要求未读取、未处理。 |

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ N/A；readiness 是由 request facts 派生的三值结果，不含持久化状态或恢复路径。
- Cross-plan check：✅ Phase 1→3 接口一致：只新增 `draftReadiness` 响应字段，未改变 `requestCoverage`、model、generationState 或发送链路；后续 UI 消费不在本 Phase 范围。P1-1/P1-2 修复后再关闭本轮。

