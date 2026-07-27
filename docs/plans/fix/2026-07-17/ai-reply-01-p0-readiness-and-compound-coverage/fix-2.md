# fix-2: P0-1 IP 证据词边界

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 1 / P0-1）
- 子计划：`docs/plans/2026-07-15/ai-reply-01-p0-readiness-and-compound-coverage.md`
- 前序修复单：`docs/plans/fix/ai-reply-01-p0-readiness-and-compound-coverage/fix-1.md`

## 约束摘录

- I-1：`draftReadiness` 仅由 service 的 `requestFacts` 聚合；controller 只映射。
- I-2：同一 request 的每个触发 facet 必须由其自身 `factRuleIds` 的 subject/body 提供真实证据；禁止跨 request 或 prompt fallback 借证据。
- I-3：临时启发式只能把状态降为 `PARTIAL`，不得修改 `factRuleIds`、`sendQaRuleIds` 或正文。
- I-4：`groundedRequestCount` 继续统计 `GROUNDED + PARTIAL`。
- I-5：临时 facet 表仅位于 `AiReplyDraftService`，等待计划 6 删除。
- T2：IP 的合格证据是 `IP`、`intellectual property`、`ownership` 或 `rights` 等真实 IP 事实；规范化只允许 lower-case、空白折叠和常见连字符统一，不得引入语义模型。
- 范围：仅 P0-1 的 6 个源码/测试文件；不改 QA 数据、迁移、前端或审计。

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 期望 |
|---|---|---|---|---|
| P1-3 | P1 | 任一合同/IP 复合 request 的命中正文包含英语单词 `partnership`（或任意包含连续字符 `ip` 的非 IP 单词）且没有 IP 事实时 | IP facet 使用裸子串 `ruleText.contains("ip")`；`partnership` 的结尾包含 `ip`。若同一正文还有 `agreement`，contract 与 IP 都会被判有据，request 被错误标为 `GROUNDED/READY`。 | `ip` 仅作为独立词或明确 IP 短语命中；`partnership` 等非 IP 单词不能满足 IP facet，缺少 IP 事实必须为 `PARTIAL/NEEDS_REVIEW`。 |

## 修复规格

### P1-3 — IP token 边界匹配

**文件**：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

1. 为 `ip` 证据采用最小的 token/词边界匹配，或将该 evidence 改为能保持相同语义的明确短语匹配；继续允许 `IP rights`、`IP ownership` 等真实 IP 证据。
2. 不改变其它 facet 的语义，不引入 NLP/同义词服务或新事实来源；证据仍仅来自当前 request 的 `factRuleIds` subject/body 合集。
3. 不改变 readiness 聚合、`factRuleIds`、`sendQaRuleIds`、`groundedRequestCount` 或 draftText。

**测试**：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

1. request 为 `contract terms and IP rights`，rule 正文仅为 `partnership agreement` 类合同文字时，断言 `PARTIAL`。
2. 同一 request 有独立 `IP`、`intellectual property` 或 `IP ownership` 事实时，断言可满足 IP facet；与合同事实共同存在时为 `GROUNDED`。
3. 保留 fix-1 的 `IP` 大小写和已规范化文本测试。

## 当前状态（修前）

- Build：PASS。
- Tests：PASS — 120 passed, 0 failed, 0 skipped。
  - `AiReplyDraftServiceTest`: 93
  - `AiTrainingSimulateTest`: 22
  - `UnmatchedInboundAiReplyTurnKnowledgeTest`: 5
- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyDraftServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest test`

## 合规审计

| Constraint | Verdict | Evidence |
|---|---|---|
| I-1 readiness 单源 | ✅ | `AiReplyDraftService.kt:415,581-594` 从最终 `resolved.requestFacts` 聚合；`AiTrainingController.kt:247-248`、`UnmatchedInboundMailController.kt:336-337` 只映射 service 值。 |
| I-2 request 内逐 facet 真实证据 | ❌ P1-3 | `AiReplyDraftService.kt:550-565` 只合并当前 `factRuleIds`，但 `565` 对每个 evidence 做无边界子串匹配；`TEMPORARY_COMPOUND_FACETS` 的 IP evidence 含裸 `ip`（`965-968`）。`partnership agreement` 因 `ip` + `agreement` 会错误满足 IP + contract。 |
| I-3 只降级、不改事实/正文 | ✅ | `AiReplyDraftService.kt:516-528` 仅选择 request status；`531-542` 保持规则 ID 和旧统计；`417-430` 仅附加 readiness。 |
| I-4 旧统计兼容 | ✅ | `AiReplyDraftService.kt:539-541` 统计 `GROUNDED || PARTIAL`。 |
| I-5 临时表可删除 | ✅ | `AiReplyDraftService.kt:920-989` companion 内单一 `TEMPORARY_COMPOUND_FACETS`。 |
| T1 readiness DTO/seam | ✅ | `AiReplyDraftService.kt:23-27,47,415,581-594`；`AiReplyDraftServiceTest.kt:2656-2701`。 |
| T3 两 API 契约 | ✅ | `AiTrainingController.kt:222-249,394-414`；`UnmatchedInboundMailController.kt:315-338,553-569`。 |
| T4 服务测试 | ❌ P1-3 | `AiReplyDraftServiceTest.kt:3003-3030` 仅验证独立 `IP` 能命中，未覆盖 `partnership` 子串误命中的负例。 |
| T5 controller 契约测试 | ✅ | `AiTrainingSimulateTest.kt:196,362`；`UnmatchedInboundAiReplyTurnKnowledgeTest.kt:236,323`。 |
| Deleted code | ✅ | `PARTIAL_DETAIL_PHRASES` 已删除，替换为 `TEMPORARY_COMPOUND_FACETS`。 |
| No extras | ✅ | 仅审计 P0-1 指定的 6 个文件；main 其它脏改动未读取、未修改。 |

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ N/A；readiness 为 `requestFacts` 派生值，没有持久化状态或恢复路径。
- Cross-plan check：✅ Phase 1→3 只新增 `draftReadiness` 响应字段；`requestCoverage`、model、generationState 与发送链路未改变。P1-3 修复后再关闭本轮。
