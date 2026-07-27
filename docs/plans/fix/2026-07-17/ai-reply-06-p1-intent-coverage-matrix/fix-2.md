# fix-2：P1-6 RequestIntent 覆盖矩阵复验

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 6 / P1）
- 子计划：`docs/plans/2026-07-15/ai-reply-06-p1-intent-coverage-matrix.md`
- 上轮修复单：`docs/plans/fix/ai-reply-06-p1-intent-coverage-matrix/fix-1.md`

## 约束摘录

- I-1：按 `gapItems` offset 顺序建立 group，index 自 1 稳定递增。
- I-2：仅后端目录决定 intent；已知复合问法拆分，未知问题保留 `general.answer`，aliases 边界安全。
- I-3：intent 证据只能来自当前 group 的 candidate 与 promptSet 交集，禁止跨 group / prompt fallback 借用。
- I-4：group status 由 intent 聚合；`factRuleIds` 是 supported intent 的证据并集。
- I-5：`expertise.programme_fit` 同时要求充分画像与 `programme.scope`；`enterprise.project_types` 独立。
- I-6：删除 P0 临时 phrase/facet 判定，coverage key + catalog 成为唯一 partial 判定。
- `application.next_stages` 始终需要 `application.steps`；请求时间语义时还必须额外需要 `application.timeline`。

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 证据 |
|---|---|---|---|---|
| P1-1 | P1 | 所有 next-stages 请求只有 `application.timeline`，尤其是同时询问 timeline 的复合请求 | `application.steps` 本应始终必需，但 catalog 把 `application.timeline` 保留为 alternative；时间分支动态追加 timeline 后，缺失 steps 仍会被 alternative 命中掩盖，intent/group 被误判为 SUPPORTED/GROUNDED。 | `AiReplyIntentCatalog.kt:107-113,135-137,211-218` |

## 修复规格

### P1-1：时间询问必须同时验证 steps 与 timeline

文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt`

1. `application.next_stages` 的基础定义只将 `application.steps` 设为必需；`application.timeline` 不得作为 alternative key。
2. 命中时间语义时，required keys 固定为 `application.steps` 与 `application.timeline`，且仍无 alternative key。
3. 时间复合请求仅有 timeline 时，`application.steps` 必须出现在 `missingEvidenceKeys`，intent/group 必须为 PARTIAL 或 MISSING，绝不能为 SUPPORTED/GROUNDED；非时间请求仅有 timeline 也不能满足 steps。

测试：在 `AiReplyDraftServiceTest.kt` 增加“next stages + timeline，只有 timeline”反例；断言 intent 非 SUPPORTED，并断言缺失 `application.steps`。

## 当前状态

- 编译：PASS（定向 Maven 测试已完成 Kotlin 编译）。
- 测试：PASS — 151 passed, 0 failed, 0 skipped。
  - `AiReplyDraftServiceTest`: 90
  - `AiTrainingSimulateTest`: 22
  - `UnmatchedInboundAiReplyTurnKnowledgeTest`: 5
  - `QaMatchServiceTest`: 34
- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyDraftServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest,QaMatchServiceTest test`

## 合规审计

- I-1：✅ `AiReplyDraftService.kt:492-493,508,559-565` 从 `gapItems` 的既有顺序 `mapIndexed` 生成稳定 index。
- I-2：✅ `AiReplyIntentCatalog.kt:24-143` 由固定目录匹配，未命中时只生成 `general.answer`；`:146-148` 使用边界匹配。上轮 P1-2/P1-4 已修复。
- I-3：✅ `AiReplyDraftService.kt:510-535` 先限制 current candidate 到 promptSet，再以该集合的 coverage keys 解析 intent；`AiReplyIntentCatalog.kt:158-201` 不读取其他 group。
- I-4：❌ P1-1。`AiReplyIntentCatalog.kt:107-113` 先把 timeline 定义为 steps 的 alternative，`:135-137` 设置两个 required keys 后未清空该 alternative，`:211-213` 因而仍会把 timeline 视作 steps 的替代，导致 status 聚合错误完整。
- I-5：✅ `AiReplyIntentCatalog.kt:26-36,171-180` programme fit 独立要求 profile 与 scope，project types 独立；`AiReplyDraftService.kt:507,527-535` 传入画像充分性与当前 group 候选。
- I-6：✅ `AiReplyDraftService.kt:527-551` 只经 catalog + coverage 聚合；扫描未发现 `isPartialCoverage`、`PARTIAL_DETAIL_*`、临时 facet 判定。
- T3：✅ `AiTrainingController.kt:239-258`、`UnmatchedInboundMailController.kt:329-348` 映射相同 nested schema，未把 missing keys 写入 draftText。
- T4/T5：❌ P1-1 未覆盖“只有 timeline”反例，现有测试只覆盖“两者都有”和“只有 steps”。
- Deleted code：✅ P0 临时短语逻辑已删除。
- No extras：✅ 本轮只复验 Phase 6 的变更与上轮修复范围；未处理 main 其他改动。

## 语义完整性检查

- Accumulation：✅ 无时间窗口计数器。
- State machine：✅ N/A；readiness 为 request facts 派生值。
- Cross-plan：❌ P1-1。Phase 4 将 `application.steps` 与 `application.timeline` 定义为独立 coverage keys；Phase 6 在时间询问分支将前者错误地视为可被 timeline 替代，违背其读侧契约。
