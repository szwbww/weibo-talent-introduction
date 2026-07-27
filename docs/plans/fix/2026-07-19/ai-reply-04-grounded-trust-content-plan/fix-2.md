# AI 回复第 4 阶段 Grounded Trust 内容计划：修复计划 2

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-19/ai-reply-04-grounded-trust-content-plan.md`
- 前序修复：`docs/plans/fix/2026-07-19/ai-reply-04-grounded-trust-content-plan/fix-1.md`
- 复验轮次：fix-2（2/3）
- 范围：仅修复轮 1 已变更的 `AiReplyGroundedDraftMaterializer.kt` 与其测试；不得改公开 DTO、controller、自动发送、前端、数据库或计划外文件。

## 约束摘录

1. I-1/I-5：plan 唯一决定 claim、paragraph 及其顺序；统一 JSON 的 paragraph、claim、coverage、missingFacts 和 requiresReview 必须与 plan 完全相等，任何不一致均 fail closed。
2. I-6：`proposedAction` 必须与正文检测到的唯一动作一致；`NONE` 不得有动作，最多一个且必须已授权。
3. I-7：结构或动作校验失败必须返回 deterministic fallback、`usedLlm=false`、`FALLBACK_NO_RESPONSE`，不得保留 `LLM_USED`。

## 修正记录表

| P1 | 触发频率 | 根因 |
|---|---|---|
| paragraph object array 可重排仍被接受 | LLM 非确定输出、首轮或动作重试均可能发生 | 校验按 `paragraphIndex` 建 map/set 后逐项比对，未比较 JSON array 与 plan 的顺序。 |
| 正文动作与 `proposedAction` 不一致仍可作为 LLM 成功返回 | 用户已允许 meeting/material CTA 且模型在 claim 中写 CTA、却声明 `NONE` 时发生 | materializer 只检测 `proposedAction.text`，未检测 materialized 正文；最终 sanitizer 只删除未授权动作。 |

## 修复规格

### P1-1：paragraph array 顺序严格等于 plan

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt`
- 改动：逐项比较 parsed paragraph array 与 `plan.paragraphs`，同时校验 `paragraphIndex`、`claimKeys` 的顺序和次数；不得以 set/map 对齐。
- 预期：交换两个合法 paragraph object、交换 claimKeys、重复 paragraph 或 claim key 都返回 structured-invalid，首轮和 retry 均走 I-7 fallback。
- 测试：补交换 paragraph object 的负向 fixture，断言 `valid=false` 和 structured warning。

### P1-2：materialized 正文与动作声明双向一致

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- 改动：在组装 frame 后用 `AiReplyActionPolicy.detectActions` 检查整个正文。`NONE` 必须检测不到动作；非 `NONE` 必须只检测到声明类型，且其声明 text 已位于正文。任何不一致直接 materialize invalid，不依赖最终 sanitizer。
- 预期：允许 meeting/material 的 claim 内 CTA + `NONE`、声明 meeting + 正文 materials、双 CTA 均 fallback，`usedLlm=false`、`FALLBACK_NO_RESPONSE`；授权且一致的单 CTA 仍保留在最后正文段。
- 测试：覆盖上述三类负向 JSON 及一类授权一致 CTA；验证服务层不会把不一致响应标为 `LLM_USED`。

## 当前状态（修复前）

- 编译/测试：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`，1749 tests，0 failures，0 errors，4 skipped。
- JS 回归：PASS — 336 passed，0 failed，0 skipped。
- `git diff --check`：PASS。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 server plan | ❌ | `AiReplyGroundedDraftMaterializer.kt:368-380` 仅按 paragraphIndex 映射校验，接受 paragraph JSON array 重排。 |
| I-2 intent-local evidence | ✅ | `AiReplyGroundedContentPlanner.kt:59-84` 生成 intent-local claim；`AiReplyGroundedDraftMaterializer.kt:241-301` 校验 request、intent、sourceIds 子集。 |
| I-3 missing facts / trust route | ✅ | `AiReplyGroundedContentPlanner.kt:177-199` 覆盖完整 trust family；`AiReplyDraftService.kt:131-134` 强制 `QA_GROUNDED`。 |
| I-4 natural paragraphs / no dropped claims | ✅ | `AiReplyGroundedContentPlanner.kt:114-173` 最多四段并合并相邻 request；`AiReplyPointByPointComposer.kt:12-33,101-115` 按 plan 组装且无 GREETING/ACK。 |
| I-5 exact JSON protocol | ❌ | `AiReplyGroundedDraftMaterializer.kt:368-380` 未要求 paragraph objects 保持 plan array 顺序。 |
| I-6 action consistency | ❌ | `AiReplyGroundedDraftMaterializer.kt:127-136` 只检测 action text；`AiReplyDraftService.kt:249-270,503-531` 对已授权 claim CTA 不会删除或降级。 |
| I-7 fail closed | ❌ | 上述 I-6 不一致不会成为 validation failure，仍可保留 `LLM_USED`。 |
| I-8 public result/audit compatibility | ✅ | `AiReplyDraftService.kt:535-548` 继续使用 `resolved.sendQaRuleIds` 构造公开结果。 |
| I-9 grounded prompt code-owned | ✅ | `AiReplyDraftService.kt:765-805` 为代码内 Grounded schema；`807-808` FREE_FORM 配置路径独立。 |
| I-10 action layout preservation | ✅ | `AiReplyActionPolicy.kt:91-148,174-215` detect/find/sanitize 共用 tokenizer/direct matcher，span 清理保留接缝。 |
| Deleted code | ✅ | Grounded materialized/fallback 使用 `assembleGroundedEmail`，未走 `assembleNaturalEmail(...).take(4)`：`AiReplyPointByPointComposer.kt:32,79,101-115`。 |
| No extras | ✅（P2 观察） | `AiReplyGroundedContentPlannerTest.kt` 尚缺；这是原计划测试覆盖缺口，未证明运行时 defect。前序 `AiTrainingSimulateTest.kt` 计划外变更不属于本轮修复范围。 |

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ 本计划未新增状态机；自动发送仍要求 `usedLlm + LLM_USED + READY`。
- Cross-plan check：✅ N/A（单一计划）。

## 收敛

- fix-1：3 P1。
- fix-2：2 P1。
- P1 数量严格下降，可进入独立修复后第 3 轮复验。
