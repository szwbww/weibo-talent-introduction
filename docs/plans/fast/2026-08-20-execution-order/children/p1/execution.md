# P1 Execution Report — 手动绑定的事实未被采纳时，丢弃并提示

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order/docs/plans/2026-08-20/P1-fact-binding-drop-not-fatal.md`
- Plan SHA-256: `9b5e2bfa50e4ddf507078bd3966a45125d9dffad5653ba15ecf66b8958aecaaf` (recomputed at end, unchanged)
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order/docs/plans/2026-08-20/P1-fact-binding-drop-not-fatal.md@9b5e2bfa50e4ddf507078bd3966a45125d9dffad5653ba15ecf66b8958aecaaf`
- Execution epoch: NEW
- Executor: P1Implementer
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order`
- Target branch: `fast/2026-08-20-execution-order`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order@fast/2026-08-20-execution-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-08-20-execution-order`
- Pre-execution code SHA (P0 terminal code head / child base): `8ea1e241b5703e967da9861847663e67e5eb3bdc`
- Pre-execution HEAD: `a76e1c99bf5913c085bcfb79eda8213478a9c77b` (P0 evidence commits on top of base)
- Post-execution code SHA (implementation commit): `a356ea4e95cd4fad71a61d4e512c6d6e3cf1f390` (`feat(fast-p): implement P1`) — verified reachable from `refs/heads/fast/2026-08-20-execution-order`
- Evidence HEAD: same as post-execution code SHA (single implementation commit; no separate evidence commit required by plan)

## Changes per file (exactly the 7 authorized files)

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | `RequestFactItem` 新增带默认值影子字段 `droppedBindingRuleIds: List<Long> = emptyList()`（`unrecognizedAsks` 之后，`AiReplyDraftService.kt:362`） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | `resolveMatrixSelection` 原 `:199-204` 唯一降级点：`if (item.factRuleIds != explicitIds) throw ...` 改为 `val accepted = item.factRuleIds.toSet(); val dropped = explicitIds.filter { it !in accepted }; if (dropped.isEmpty()) item else item.copy(droppedBindingRuleIds = dropped)`（`QaFactSelectionService.kt:197-208`），继续使用 `item.factRuleIds` 作为条目事实集 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | `TrustReplyRequestCoverage` 末尾新增 `droppedFactRuleIds: List<Long> = emptyList()`（`:151`）；`toCoverage` 构造处追加 `droppedFactRuleIds = item.droppedBindingRuleIds`（`:1949`） |
| 4 | `src/main/resources/static/trust-reply-workbench.js` | `requestFromCoverage` 带上 `droppedFactRuleIds: [...(item.droppedFactRuleIds || [])]`；新增 `droppedFactLabels(request)` 查名助手（复用 `factRuleById`，回退 `事实 <id>`）；`renderFactSection` 按 S-1 逐字新增 `droppedMarkup`（`class="muted" data-role="item-facts-dropped"`），返回模板 `${staleMarkup}` → `${staleMarkup}${droppedMarkup}` |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | C-1 新增 4 用例；另更新 1 个陈旧用例（见 Deviations） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | C-2 新增 2 用例 |
| 7 | `src/test/js/trustReplyWorkbench.test.js` | C-3 新增 3 用例；`bootstrapPayload` 增加可选 `droppedFactIds` 参数（默认空，既有用例不变） |

## Per-invariant evidence

- **I-1（唯一降级点）**: `grep -rn "TRUST_REPLY_FACT_SELECTION_INVALID" --include=*.kt src/main | wc -l` = **6**（由 7 降为 6）；消失的正是 `QaFactSelectionService.kt` 原 `:199-204` 区段。该文件仍命中 4 处（`:171` 条数不等、`:185` validateExplicitSelection、`:240`/`:263` legacy 两处），`TrustReplyWorkbenchService.kt` 仍命中 2 处（`:1747`、`:1751` validateMatrixKeys）。C-1 第 3、4 用例绿（`matrix size mismatch still throws`、`disabled rule still throws`）。
- **I-2（逐条目承载）**: `grep -rn "droppedBindingRuleIds\|droppedFactRuleIds" --include=*.kt src/main` 恰好命中 **4 行**：`AiReplyDraftService.kt:362`（声明）、`QaFactSelectionService.kt:208`（赋值）、`TrustReplyWorkbenchService.kt:151`（声明）、`TrustReplyWorkbenchService.kt:1949`（toCoverage 透传）。C-2 第 1 用例绿。
- **I-3（影子字段不进哈希/文本）**: `git diff` 显示 `TrustReplyWorkbenchService.kt` 仅 2 处 hunk（coverage 字段 + toCoverage 一行）；`requestEvidenceVersion`/`canonicalMatrix`/`versionId`/`TrustReplySavedStatePayload` 四处函数体零改动。C-2 第 2 用例绿（`dropped bindings never change the per-request evidence version`）。
- **I-4（两投影逐字相等）**: `canonicalMatrix`（`TrustReplyWorkbenchService.kt:1766`）与 `toCoverage`（`:1929`）的 `factRuleIds = item.factRuleIds` 均未改动；`applyBootstrap` 前端守卫（`trust-reply-workbench.js` `:585-595` 区）零改动。C-2 第 1 用例断言 canonicalMatrix 投影 `[key, []]` 与 coverage `factRuleIds = []` 一致。
- **I-5（自愈闭环）**: `serializeRequestFactSelections` 零改动（只回传 `requestKey` + `factRuleIds`）；C-3 第 3 用例绿（payload 的 `requestFactSelections` 每项仅有 `requestKey` 与 `factRuleIds` 两个键）。
- **S-1（样式契约）**: `git diff src/main/resources/static/styles.css` 为空；`grep -c 'style="' src/main/resources/static/trust-reply-workbench.js` = **1**（未增加）；`data-role="item-facts-dropped"` 恰 1 处；无新 CSS class、无 inline style；`droppedMarkup` 与计划 S-1 代码块逐字一致，`class="muted"` 复用 stale 提示同款写法，条件为假输出空字符串。

## Commands

| Command | Exit | Result |
|---|---|---|
| `JAVA_HOME=... mvn test -Dtest='QaFactSelectionServiceTest#unsupported request keeps the filtered facts and reports the dropped bindings'` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `JAVA_HOME=... mvn test -Dtest='QaFactSelectionServiceTest#accepted bindings report no dropped ids'` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `JAVA_HOME=... mvn test -Dtest='QaFactSelectionServiceTest#matrix size mismatch still throws'` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `JAVA_HOME=... mvn test -Dtest='QaFactSelectionServiceTest#disabled rule still throws'` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `JAVA_HOME=... mvn test -Dtest='TrustReplyWorkbenchServiceTest#bootstrap surfaces dropped bindings per request without failing'` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `JAVA_HOME=... mvn test -Dtest='TrustReplyWorkbenchServiceTest#dropped bindings never change the per-request evidence version'` | 0 | Tests run: 1, Failures: 0, Errors: 0 — BUILD SUCCESS |
| `node --test src/test/js/trustReplyWorkbench.test.js` | 0 | tests 22, pass 22, fail 0 |
| `JAVA_HOME=... mvn test`（共享全量，首次） | 1 | Tests run: 2642, **Failures: 1**, Errors: 0 — 1 个陈旧用例（见 Deviations） |
| `JAVA_HOME=... mvn test`（共享全量，陈旧用例更新后） | 0 | Tests run: 2642, Failures: 0, Errors: 0, Skipped: 4 — BUILD SUCCESS |
| `node --test src/test/js/*.test.js` | 0 | tests 676, pass 676, fail 0 |
| `node --check src/main/resources/static/app.js` | 0 | 无输出 |
| `node --check src/main/resources/static/trust-reply-workbench.js` | 0 | 无输出 |
| `git diff --check` | 0 | 无输出（`git show --check HEAD` 亦干净） |

## Scope check (A-9)

`git diff-tree --root --no-commit-id --name-only -r HEAD`（仅本实现提交）：

```
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt
src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt
src/main/resources/static/trust-reply-workbench.js
src/test/js/trustReplyWorkbench.test.js
src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt
```

恰好 7 个授权路径。`styles.css`、`AiReplyGroundedContentPlanner.kt`、`PendingMailOperationService.kt`、`AutoReplyConfidenceScorer.kt` 均不在其中。`docs/plans/fast/` 下无任何文件被 stage。提交后工作树仅剩控制器持有的 `docs/plans/fast/2026-08-20-execution-order/ledger.md` 未提交改动（P1 → IMPLEMENTING，控制器写入，未触碰）。

## Deviations (forced, with evidence)

1. **更新 1 个陈旧既有用例**（`QaFactSelectionServiceTest.kt`）：既有用例 `matrix mode rejects a fact that matches another request only` 断言的是 P1 之前的行为——"绑定的事实不匹配这条摘要"时 `resolveMatrixSelection` 抛 `TRUST_REPLY_FACT_SELECTION_INVALID`。这正是计划 I-1 唯一取消抛出的判据（原 `:199-204`）与证据 E-1b 描述的缺陷场景。首次共享全量运行时该用例失败（`AssertionFailedError: Expected ... to be thrown, but nothing was thrown`，2642 中唯一失败）。修复由计划唯一确定（该输入不再抛错；文件在授权清单 #5 内；共享全量通过判据要求 `Failures: 0`）：改名并改写为 `matrix mode keeps the request working when a bound fact matches another request only`，断言不抛、`factRuleIds = []`、`droppedBindingRuleIds = [2L]`、`sendQaRuleIds = []`。C-1 计划内的 4 个新用例照常新增（本更新是替换陈旧断言，不是第 5 个新用例）。
2. 除此之外无其他偏差；`serializeRequestFactSelections`、`changeRequestFacts`、`applyBootstrap` 守卫、`styles.css`、所有硬拦抛点均按计划保持原样。

## Freshness

- Plan identity rechecked: YES（SHA-256 与执行开始时一致，未发生 PLAN_CHANGED_DURING_EXECUTION）
- Worktree identity rechecked: YES（`--expect-root/--expect-branch/--expect-git-dir` 通过）
- Reported commit reachable from target branch: YES（`git merge-base --is-ancestor HEAD refs/heads/fast/2026-08-20-execution-order`）
- Required commands run this invocation: YES（C-1×4、C-2×2、C-3×1、共享全量、前端全量、两个 `node --check`、`git diff --check` 全部本调用内新鲜执行）
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
