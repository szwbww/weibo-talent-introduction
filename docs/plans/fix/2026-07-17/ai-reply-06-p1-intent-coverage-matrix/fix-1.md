# fix-1：P1-6 RequestIntent 覆盖矩阵复验

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 6 / P1）
- 子计划：`docs/plans/2026-07-15/ai-reply-06-p1-intent-coverage-matrix.md`

## 约束摘录

- I-1：以 `gapItems` offset 顺序建立 group，index 从 1 稳定递增。
- I-2：仅后端目录决定 intent；已知复合问题拆分，未知问题保留 `general.answer`，aliases 必须边界安全。
- I-3：intent 证据只能来自当前 group 的 candidate 与 promptSet 交集，禁止跨 group / prompt fallback 借用。
- I-4：group status 由 intent 聚合；`factRuleIds` 是 supported intent 的证据并集。
- I-5：`expertise.programme_fit` 同时需要充分画像和 `programme.scope`；`enterprise.project_types` 独立。
- I-6：删除 P0 临时 phrase/facet 判定，coverage key + catalog 成为唯一 partial 判定。
- catalog 表：`application.next_stages` 必须有 `application.steps`；请求时间时还必须有 `application.timeline`。

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 证据 |
|---|---|---|---|---|
| P1-1 | P1 | 所有“next steps + timeline/when”复合请求 | `application.steps` 与 `application.timeline` 被当作互斥替代项；仅有 steps 或仅有 timeline 都会被误判为 SUPPORTED，遗漏的时间或流程不会进入缺口。 | `AiReplyIntentCatalog.kt:110-115,195-219` |
| P1-2 | P1 | 所有未知请求命中正文有效、但尚未补 coverage key 的候选规则 | `general.answer` 又要求 rule 有 coverage key；计划要求它只使用当前 group 的 valid candidate，且无 candidate 才 missing。V76 明确允许未知规则保留空标签，故会把可用事实错误阻断。 | `AiReplyIntentCatalog.kt:168-192`; `AiReplyDraftService.kt:516-518` |
| P1-3 | P1 | 全部 intent missing、但 group 仍有有效候选规则的请求 | `factRuleIds` 在无 supported intent 时回退为全部 valid candidate，而不是空的 supported-evidence union；API 会把无关规则暴露为该 request 的事实依据。 | `AiReplyDraftService.kt:554-565` |
| P1-4 | P1 | 含 `preselected` / `unselected` 等嵌入式片段或 URL/query 文本的请求 | catalog 以裸 `contains` 命中 aliases，不满足“边界安全 phrase/regex”，可虚构 selection 等 intent，重新引入 URL/词内伪匹配。 | `AiReplyIntentCatalog.kt:118-133` |

## 修复规格

### P1-1：时间线是 next-stages 的附加必需证据

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt`

1. 保留 `application.next_stages` 作为一个稳定 intent；`application.steps` 始终必需。
2. 当 request 明确询问时间、日期、timeline、when、duration 等时间语义时，将 `application.timeline` 追加为必需证据，不得把它与 steps 互斥替代。
3. 只缺 timeline 时该 intent 必须列出 `application.timeline` 并为 PARTIAL，group 因此为 PARTIAL；两项都缺失时才为 MISSING/UNSUPPORTED。

测试：`AiReplyDraftServiceTest.kt` 覆盖“next steps and timeline”分别只有 steps、只有 timeline、两者都有三种场景。

### P1-2：general.answer 仅按当前 valid candidate 判定

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt`

1. `general.answer` 有一个当前 group、在 promptSet 内、正文非空的 candidate 即 SUPPORTED，不读取该 rule 的 coverage key。
2. 无 valid candidate 才为 MISSING；不得用 prompt fallback 中其他 group 的规则补充。

测试：`AiReplyDraftServiceTest.kt` 覆盖 coverageKeys 为空的有效 candidate 仍支持 unknown request，以及无 candidate 仍 missing。

### P1-3：factRuleIds 严格等于 supported-evidence union

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`

1. `RequestFactItem.factRuleIds` 只保留 SUPPORTED intent 的 `evidenceRuleIds`，按 candidate 原始顺序去重。
2. 全部 missing 时返回空列表；不得以 valid candidate 回退。
3. 不改 `sendQaRuleIds`、promptSet 或自动回复路径。

测试：`AiReplyDraftServiceTest.kt` 与两入口 controller 测试覆盖 all-missing group 的空 `factRuleIds` 和 nested missing intent。

### P1-4：catalog alias 边界匹配

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt`

1. 以边界安全 phrase/regex 匹配 aliases；不得让字母数字词内子串命中。
2. 保留已有 URL-safe extractor 的职责，不新增第二套 request extraction；catalog 仅拒绝其收到的 URL/query 或词内伪片段。

测试：新增 `preselected` / `unselected` 与 URL query 负例，并保留 selection/matching 正例。

## 当前状态

- 编译：PASS（`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`）。
- 测试：PASS — 145 passed, 0 failed, 0 skipped。
  - `AiReplyDraftServiceTest`: 84
  - `AiTrainingSimulateTest`: 22
  - `UnmatchedInboundAiReplyTurnKnowledgeTest`: 5
  - `QaMatchServiceTest`: 34
- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyDraftServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest,QaMatchServiceTest test`

## 合规审计

- I-1：✅ `AiReplyDraftService.kt:508,559-561` 直接按 `gapItems.mapIndexed` 建 group 与稳定 index。
- I-2：❌ P1-2、P1-4。`AiReplyIntentCatalog.kt:118-133` 负责目录与 fallback，但 `:121` 的无边界匹配会伪命中，`:168-192` 又错误要求 general.answer 带 coverage key。
- I-3：✅ `AiReplyDraftService.kt:506,510-525,527-535` 将 candidate 限为当前 group ∩ promptSet，并仅从该集合取 coverage。
- I-4：❌ P1-1、P1-3。`AiReplyIntentCatalog.kt:195-219` 把 steps/timeline 当替代，`AiReplyDraftService.kt:554-565` 在无 supported intent 时不保留空 evidence union。
- I-5：✅ `AiReplyIntentCatalog.kt:28-38,156-165` 对 programme fit 同时要求 profile 与 scope，project types 独立；`AiReplyDraftService.kt:507,527-535` 传入画像可用性与当前 group 规则。
- I-6：✅ `rg` 未发现 `isPartialCoverage`、`PARTIAL_DETAIL_*` 或临时 facet 表；`AiReplyDraftService.kt:527-551` 只用 catalog + coverage 聚合。
- T3：✅ `AiTrainingController.kt:239-255`、`UnmatchedInboundMailController.kt:329-345` 两入口映射同一 nested intent schema，未将缺失 key 写入 draftText。
- T4/T5：⚠️ P2 观察。现有测试覆盖主要复合样例与 schema，但未覆盖本修复单四个反例；不单独构成生产 P1。
- Deleted code：✅ P0 临时短语逻辑已删除。
- No extras：✅ 本复验只读取 Phase 6 的 7 个文件及其既有契约；未处理 main 其他改动。修复单为唯一新增文件。

## 语义完整性检查

- Accumulation：✅ 无时间窗口计数器。
- State machine：✅ N/A；readiness 是 request facts 的派生值。
- Cross-plan：❌ P1-1。Phase 4 已提供 `application.steps` 与 `application.timeline` 两个独立 coverage keys（`QaCoverageKeyCatalog.kt:37-39`），但 Phase 6 将其合并为替代条件，破坏“询问时间时另需 timeline”的读写契约。P1-2 至 P1-4 均局限于 Phase 6 读侧实现。
