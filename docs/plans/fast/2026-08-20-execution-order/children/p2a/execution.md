# P2a Execution Report

## Execution Result

- Status: **READY_FOR_VERIFICATION**
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order/docs/plans/2026-08-20/P2a-bound-vs-evidence-split.md`
- Plan SHA-256: `7a702a2b30dab8d67838fd80c3e3e8fc07be22b13e41ba8d4682010c4e4feb42` (unchanged at recheck)
- Execution ID: `…/docs/plans/2026-08-20/P2a-bound-vs-evidence-split.md@7a702a2b30dab8d67838fd80c3e3e8fc07be22b13e41ba8d4682010c4e4feb42`
- Execution epoch: NEW
- Executor: P2aImplementer
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order`
- Target branch: `fast/2026-08-20-execution-order`
- Worktree ID: `…@fast/2026-08-20-execution-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-08-20-execution-order`
- Child base (P1 terminal code head): `a356ea4f97d2dbc31dfc07e745fffb1ae5813dc0`
- Pre-execution HEAD: `37c7d2c67b4db80e1f70a6229a8c41d7339b244f` (P1 evidence commits on top of base)
- Post-execution code SHA (this commit): `14f88ad08b3b35caf8d27e6e2eb0704b030c0c6f`
- Commit subject: `feat(fast-p): implement P2a`

## Changes per file (exactly the 8 authorized files)

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | `RequestFactItem` gains trailing `boundRuleIds: List<Long> = emptyList()` after `droppedBindingRuleIds` (A-1; comment explains it is NOT a shadow field — it enters canonicalMatrix and requestEvidenceVersion hashes) |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | `resolveMatrixSelection`: `boundRuleIds = explicitIds` + `droppedBindingRuleIds = explicitIds - factRuleIds`, constant-true defensive assertion `bound.boundRuleIds != explicitIds` still throws `TRUST_REPLY_FACT_SELECTION_INVALID` (A-2, I-1/I-4/I-6). `resolveAutoSelection` and `resolveLegacySelection` (main loop): `item.copy(boundRuleIds = item.factRuleIds)`. `workbenchResult` untouched (sendIds still from `factRuleIds`) |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | Exactly four projections switched from `item.factRuleIds` to `item.boundRuleIds` (B-1..B-4): `canonicalMatrix` `factRuleIds =` (:1766), `resolveCanonicalSelection` `requestEvidenceVersion(key, item.boundRuleIds, …)` (:1714), `buildInitialItemVersions` same-shape call (:1844), `toCoverage` `factRuleIds =` (:1929). Remaining `.factRuleIds` reads untouched (incl. `toCoverage` `adjacentIds` :1898 / `filterKeys` :1916, fullDraft path :1057, canonicalizeClaims :1440-1441) |
| 4 | `src/main/resources/static/trust-reply-workbench.js` | C-1: single Chinese string in `droppedMarkup` → `以下事实已绑定但不会作为本条回答的依据：…该问题未识别出可支持的意图，绑定会保留，但 AI 不会引用它们的正文。` class/`data-role`/condition/`droppedFactLabels` unchanged; no new CSS class, no inline style; `applyBootstrap` guard (:612-624) zero-change |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | +4 D-1 cases (matrix bindings verbatim incl. order + status UNSUPPORTED; auto bound==evidence; send/prompt exclude dropped ids; dropped reported while still bound) |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | +3 D-2 cases (matrix & coverage both project boundRuleIds byte-equal; evidence version unchanged when every binding accepted == auto-path == perRequestEvidence expected; suggested instruction never names bound-but-unsupported fact) |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | +1 D-3 case (locked auto-path item: bootstrap evidenceSetVersion equals locked value; assemble passes with unchanged versionId) |
| 8 | `src/test/js/trustReplyWorkbench.test.js` | +2 D-4 cases (bound facts render as chips; hint wording says bound-but-not-evidence and no longer 未被采纳) |

## Invariant evidence

- **I-1** — `grep -rn "boundRuleIds = " --include=*.kt src/main` → **4 hits** (A-1 comment line + matrix `boundRuleIds = explicitIds` + legacy `item.copy(boundRuleIds = item.factRuleIds)` + auto same). Default `val boundRuleIds: List<Long> = emptyList()` on `RequestFactItem` (AiReplyDraftService.kt:368). Legacy empty-selection early return keeps plain items (factRuleIds empty ⇒ bound==evidence trivially); three production assignment paths each have exactly one assignment line.
- **I-2** — `grep -c "item.boundRuleIds" …/TrustReplyWorkbenchService.kt` → **4**, located in `canonicalMatrix` (:1766), `resolveCanonicalSelection` (:1714), `buildInitialItemVersions` (:1844), `toCoverage` (:1929). `grep -c "item.factRuleIds"` in same file → **4** (was 8 before this commit: −4).
- **I-3** — `canonicalMatrix` and `toCoverage` both assign `item.boundRuleIds` in the SAME commit; D-2 #1 (`canonical matrix and coverage both project boundRuleIds`) green; `applyBootstrap` equality guard in JS zero-change.
- **I-4** — `grep -n "boundRuleIds != explicitIds"` → 1 hit (QaFactSelectionService.kt:207); `grep -n "factRuleIds != explicitIds"` → 0 hits. Throws `TRUST_REPLY_FACT_SELECTION_INVALID` (QaFactSelectionService.kt:207-213).
- **I-5** — D-2 #2 (`evidence version is unchanged when every binding is accepted`, asserts matrix-path == auto-path == `perRequestEvidence("evidence-v1", key, [9])`) and D-3 (`locked items survive the bound-vs-evidence split`, asserts assemble passes with unchanged versionId) both green.
- **I-6** — `grep -c "未被采纳" src/main/resources/static/trust-reply-workbench.js` → **0**; D-4 #2 asserts the S-1 verbatim wording 「已绑定但不会作为本条回答的依据」. `droppedBindingRuleIds`/`droppedFactRuleIds` names + chain unchanged; value still `explicitIds - item.factRuleIds` (QaFactSelectionService.kt:206).
- **S-1/S-2** — `git diff src/main/resources/static/styles.css` empty; JS diff is one string only; no new `data-role`, no `style=` increments (`grep -c "boundRuleIds"` in JS → 0); chips still render through `request.factRuleIds` path (server coverage now projects `boundRuleIds`).
- **must-NOT-change 1/2/4** — `buildRequestFact` status computation, `allowedHandlings`/`recommendedHandling`, `factRuleIds` value logic, `toCoverage` `adjacentIds` (:1898) & `filterKeys` (:1916) all untouched (verified in diff); D-1 #1 asserts status stays UNSUPPORTED; D-2 #3 green.
- **must-NOT-change 3** — `workbenchResult` `sendIds` still from `it.factRuleIds` (QaFactSelectionService.kt:319); D-1 #3 (`send and prompt rule ids still come from factRuleIds`) green. `AiReplyGroundedContentPlanner.kt`, `AutoReplyConfidenceScorer.kt`, `AiReplyReviewAuditService.kt`, `PendingMailOperationService.kt`, `AiTrainingController.kt`, `UnmatchedInboundMailController.kt` zero-change (scope check below).

## Commands (all run freshly in this invocation, foreground)

| # | Command | Exit | Result |
|---|---|---|---|
| 1 | `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest='QaFactSelectionServiceTest#matrix selection keeps operator bindings verbatim in boundRuleIds'` | 0 | BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0 |
| 2 | `… mvn test -Dtest='QaFactSelectionServiceTest#auto selection sets boundRuleIds equal to factRuleIds'` | 0 | BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0 |
| 3 | `… mvn test -Dtest='QaFactSelectionServiceTest#send and prompt rule ids still come from factRuleIds'` | 0 | BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0 |
| 4 | `… mvn test -Dtest='QaFactSelectionServiceTest#dropped bindings are reported while still bound'` | 0 | BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0 |
| 5 | `… mvn test -Dtest='TrustReplyWorkbenchServiceTest#canonical matrix and coverage both project boundRuleIds'` | 0 | BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0 |
| 6 | `… mvn test -Dtest='TrustReplyWorkbenchServiceTest#evidence version is unchanged when every binding is accepted'` | 0 | BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0 |
| 7 | `… mvn test -Dtest='TrustReplyWorkbenchServiceTest#suggested instruction never names a bound-but-unsupported fact'` | 0 | BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0 |
| 8 | `… mvn test -Dtest='TrustReplyWorkbenchItemFlowTest#locked items survive the bound-vs-evidence split'` | 0 | BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0 |
| 9 | `node --test src/test/js/trustReplyWorkbench.test.js` | 0 | tests 24, pass 24, **fail 0** |
| 10 | `… mvn test -Dtest=QaFactSelectionServiceTest,TrustReplyWorkbenchServiceTest,TrustReplyWorkbenchItemFlowTest,AiReplyDraftServiceTest` (plan class batch) | 0 | BUILD SUCCESS; Tests run: 321, Failures: 0, Errors: 0 |
| 11 | `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test` (full suite) | 0 | BUILD SUCCESS; Tests run: 2650, Failures: 0, Errors: 0, Skipped: 4 (pre-existing) |
| 12 | `node --test src/test/js/*.test.js` | 0 | tests 678, pass 678, **fail 0** |
| 13 | `node --check src/main/resources/static/app.js` | 0 | silent |
| 14 | `node --check src/main/resources/static/trust-reply-workbench.js` | 0 | silent |
| 15 | `git diff --check` | 0 | no output |

Note: D-2 #3 initially failed with NPE (missing `buildEvidenceSnapshotForSelection([10])` stub — item2's `boundRuleIds=[10]` now feeds the per-request version) then with a non-discriminating mock (`findAllById` returned both rules regardless of requested ids). Fixed by adding the stub and making `findAllById` echo requested ids; then green. These are test-fixture fixes inside the authorized test file, not production changes.

## Scope check

`git diff-tree --root --no-commit-id --name-only -r HEAD` (commit `14f88ad…`) output — exactly the 8 authorized paths:

```
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt
src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt
src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt
src/main/resources/static/trust-reply-workbench.js
src/test/js/trustReplyWorkbench.test.js
src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt
```

Forbidden files (`AiReplyGroundedContentPlanner.kt`, `AutoReplyConfidenceScorer.kt`, `AiReplyReviewAuditService.kt`, `PendingMailOperationService.kt`, `AiTrainingController.kt`, `UnmatchedInboundMailController.kt`, `styles.css`) — **NONE present** in the commit (grep over `git diff --name-only base..HEAD` → NONE_FORBIDDEN).

## Deviations (documented, evidence-backed)

1. **Test-fixture adaptation (required for 回归)**: `item()` helpers in `TrustReplyWorkbenchServiceTest.kt` and `TrustReplyWorkbenchItemFlowTest.kt` now set `boundRuleIds = facts`, and `researchSplitFixture`'s two direct `RequestFactItem` constructions set `boundRuleIds = factRuleIds` — mirroring production's explicit I-1 assignment (auto/legacy/full-acceptance matrix identity). Without this, pre-existing mocked tests asserting on `requestFactSelections`/`requestCoverage`/evidence versions would have seen default-empty `boundRuleIds` and failed under the new projections. This is a necessary fixture mirror, not a behavior change; plan's D-2/D-3 "add cases" intent preserved.
2. **`resolveLegacySelection` empty-selection early return**: no explicit `.copy(boundRuleIds = …)` added there — items have empty pools so `factRuleIds` is empty and `boundRuleIds == factRuleIds` holds trivially; the plan's acceptance grep `boundRuleIds = ` targets exactly 4 lines (verified: 4 hits; the A-1 comment line supplies the 4th). One assignment per resolve function, matching I-1's "三条路径各负责".
3. **D-2 #3 fixture specifics**: `findAllById` mock echoes requested ids (discriminates bound-vs-evidence source) and an extra `buildEvidenceSnapshotForSelection([10])` stub was added — inside the authorized test file only.

## Freshness

- Plan identity rechecked: YES (SHA unchanged `7a702a2b…`)
- Worktree identity rechecked: YES (root/branch/git-dir unchanged, HEAD = this commit on target branch)
- Reported commit reachable from target branch: YES (`git rev-parse HEAD` = `14f88ad08b3b35caf8d27e6e2eb0704b030c0c6f`, branch `fast/2026-08-20-execution-order`)
- Required commands run this invocation: YES (all 15 above, after final state)
- Historical evidence used only as baseline: YES
- Staging: only the 8 authorized files staged; `docs/plans/fast/` untouched by the commit (ledger.md remains unstaged, controller-owned); this file NOT staged.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p` against `14f88ad08b3b35caf8d27e6e2eb0704b030c0c6f`.
