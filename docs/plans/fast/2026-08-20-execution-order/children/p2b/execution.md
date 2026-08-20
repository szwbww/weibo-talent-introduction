# P2b Execution Result — bound facts into prompt (not into outbound audit)

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order/docs/plans/2026-08-20/P2b-bound-facts-into-prompt.md`
- Plan SHA-256: `fba9070939891131fa6ab38364178ae17146ce34749d121ee66bfac9fa18be53`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order/docs/plans/2026-08-20/P2b-bound-facts-into-prompt.md@fba9070939891131fa6ab38364178ae17146ce34749d121ee66bfac9fa18be53`
- Execution epoch: NEW
- Executor: `P2bImplementer` (execute-p)
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order`
- Target branch: `fast/2026-08-20-execution-order`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-20-execution-order@fast/2026-08-20-execution-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-08-20-execution-order`
- Pre-execution code SHA (P2a code head): `14f88ad08b3b35caf8d27e6e2eb0704b030c0c6f`; pre-execution HEAD: `0aa4d4865faaa982ce40638ef57b584d4e38c542`
- Post-execution code SHA / Evidence HEAD / Implementation commit: **`a3ef1cd3fbeafdb5c05ed03cca97996b1b328fe6`** (`feat(fast-p): implement P2b`)
- Implementation boundary: `0aa4d4865faaa982ce40638ef57b584d4e38c542..a3ef1cd3fbeafdb5c05ed03cca97996b1b328fe6` (single product commit; no separate evidence commit required by plan)

## Changes per file (exactly the 5 authorized files)

1. `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt`
   - `workbenchResult` (A-1 / I-1 / I-4): `promptRuleIds` source changed from `sendIds` to ordered union
     `(sendIds + ordered.flatMap { it.boundRuleIds }).distinct()` — evidence first, bound appended;
     `sendQaRuleIds = sendIds` untouched (outbound audit stays evidence-only).
   - `select()` path `promptRuleIds = sendQaRuleIds` (line 69) intentionally NOT changed (A-3).

2. `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
   - `generateItem` (A-2 / I-1): `ResolvedQaRules.promptRuleIds` = `(requestFact.factRuleIds + requestFact.boundRuleIds).distinct()`;
     `sendQaRuleIds` untouched.
   - `generateItem` (B-1): `generateOperatorDirectedAnswer(...)` call passes new `boundRuleIds = requestFact.boundRuleIds`;
     function signature gains `boundRuleIds: List<Long>` (no field added to `RequestFactItem`).
   - `generateOperatorDirectedAnswer` (B-2 / I-3 / I-4): builds `boundFactsBlock` by fetching each bound id from
     `qaRuleRepository.findById(ruleId)` CURRENT value, skipping blank `answerBody` (`if (body.isBlank()) return@mapNotNull null`),
     title = `displayName` or `"Fact $ruleId"`, joined `\n\n`, `.take(12000)` — same knowledge-block style as
     `buildFreeFormUserContent` (:2281-2296). User message appends the facts paragraph ONLY when `boundFactsBlock.isNotBlank()`
     (no empty paragraph title).
   - `generateOperatorDirectedAnswer` (B-3 / I-2): system-message basis clause revised to the plan's exact wording
     (contains `neither the answer basis nor the attached reference facts`); Line A action constraint
     `Do not introduce any outbound action that the answer basis does not state.` preserved VERBATIM (fact channel is
     parallel reference material, never an action-authorization source).
   - (B-4 / I-5): output validation (`findViolations(candidate, allowedActions)`, `INTERNAL_RESPONSE_MARKER`,
     `rejectNonEnglishItemAnswer`) untouched — zero diff.

3. `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt`
   - Added C-1 case `prompt rule ids include bound facts while send rule ids do not`.
   - Added C-1 case `prompt rule ids are identical to send rule ids without extra bindings`.
   - Updated P2a case `send and prompt rule ids still come from factRuleIds` → renamed
     `send rule ids stay evidence only while bound facts join prompt ids` (forced deviation D-1, below).

4. `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
   - Added C-2 case `operator directed item injects attached facts as reference material`.
   - Added C-2 case `operator directed item without bound facts keeps the prompt unchanged`.
   - Added C-2 case `operator directed item still blocks an unauthorised action from an attached fact`.
   - Updated Line A case per C-3: `operator directed item uses only target question and operator answer basis`
     → renamed `... without attached facts`, comment now documents it verifies I-4 identity.

5. `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`
   - Added C-4 case `bound facts never enter the send audit rule ids`; `assembleFixture` gained
     `boundFactIds: List<Long> = emptyList()` (default keeps all existing fixtures byte-identical), fixture now
     exposes `selection`; `boundRuleIds` fed through the per-request evidence-version identity, send ids stay `[9L]`.

## Invariant evidence

- **I-1 (prompt 用绑定、审计用证据)**: 
  - `grep -n "sendQaRuleIds = "` on both prod files: every RHS is evidence-only (`orderEvidenceRuleIds(...)`,
    `sendIds`, `requestFact.factRuleIds.distinct()`) — none contains `boundRuleIds`.
  - `promptRuleIds` both sites: `QaFactSelectionService.kt:320` `promptRuleIds = promptIds` (union, sendIds first);
    `AiReplyDraftService.kt:490` `promptRuleIds = (requestFact.factRuleIds + requestFact.boundRuleIds).distinct()`.
  - C-1 two cases green; C-4 green.
- **I-2 (两通道并列)**: `grep -c` on `AiReplyDraftService.kt`: `neither the answer basis nor the attached reference facts` = 1;
  `Do not introduce any outbound action that the answer basis does not state.` = 1 (verbatim). C-2 case 1 green.
- **I-3 (注入当前值)**: new block at `AiReplyDraftService.kt:678-685` contains `qaRuleRepository.findById(ruleId)`,
  `if (body.isBlank()) return@mapNotNull null`, `.take(12000)` — isomorphic to `:2315-2324` knowledge-block style.
- **I-4 (无绑定恒等)**: `boundFactsBlock.isNotBlank()` gate at `AiReplyDraftService.kt:718`; C-1 case 2 and C-2 case 2 green;
  updated Line A case (C-3) green.
- **I-5 (不放松 G2 校验)**: `git diff` on `generateOperatorDirectedAnswer` output-validation region = zero changes;
  C-2 case 3 green (`lockable == false` for passport-request wording injected via attached fact).
- **must-NOT-change 1-3, 8**: `git diff-tree --root --no-commit-id --name-only -r HEAD` = exactly the 5 authorized paths
  (see scope check below); `TrustReplyWorkbenchService.kt`, `AiReplyGroundedContentPlanner.kt`,
  `AutoReplyConfidenceScorer.kt`, `AiReplyReviewAuditService.kt`, `PendingMailOperationService.kt`,
  `src/main/resources/static/` absent.

## Commands (all run freshly in this invocation, foreground)

| Command | Exit | Result |
|---|---|---|
| `JAVA_HOME=... mvn test -Dtest='QaFactSelectionServiceTest#prompt rule ids include bound facts while send rule ids do not'` | 0 | Tests run: 1, Failures: 0, Errors: 0; BUILD SUCCESS |
| `JAVA_HOME=... mvn test -Dtest='QaFactSelectionServiceTest#prompt rule ids are identical to send rule ids without extra bindings'` | 0 | Tests run: 1, Failures: 0, Errors: 0; BUILD SUCCESS |
| `JAVA_HOME=... mvn test -Dtest='AiReplyDraftServiceTest#operator directed item injects attached facts as reference material'` | 0 | Tests run: 1, Failures: 0, Errors: 0; BUILD SUCCESS |
| `JAVA_HOME=... mvn test -Dtest='AiReplyDraftServiceTest#operator directed item without bound facts keeps the prompt unchanged'` | 0 | Tests run: 1, Failures: 0, Errors: 0; BUILD SUCCESS |
| `JAVA_HOME=... mvn test -Dtest='AiReplyDraftServiceTest#operator directed item still blocks an unauthorised action from an attached fact'` | 0 | Tests run: 1, Failures: 0, Errors: 0; BUILD SUCCESS |
| `JAVA_HOME=... mvn test -Dtest='TrustReplyWorkbenchItemFlowTest#bound facts never enter the send audit rule ids'` | 0 | Tests run: 1, Failures: 0, Errors: 0; BUILD SUCCESS |
| `JAVA_HOME=... mvn test -Dtest=QaFactSelectionServiceTest,AiReplyDraftServiceTest,TrustReplyWorkbenchItemFlowTest` | 0 | Tests run: 265 (54+171+40), Failures: 0, Errors: 0; BUILD SUCCESS |
| `JAVA_HOME=... mvn test` (shared 验证命令, full suite) | 0 | Tests run: 2656, Failures: 0, Errors: 0, Skipped: 4; BUILD SUCCESS |
| `node --test src/test/js/*.test.js` | 0 | tests 678 / pass 678 / **fail 0** |
| `node --check src/main/resources/static/app.js` | 0 | silent |
| `node --check src/main/resources/static/trust-reply-workbench.js` | 0 | silent |
| `git diff --check` | 0 | no output |

(`JAVA_HOME=...` = `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`; `mvn` output was filtered to
`Tests run:|BUILD|FAIL|ERROR` lines; the exec-plugin JS suite runs inside `mvn test` and reports pass.)

## Scope check (A-9)

`git diff-tree --root --no-commit-id --name-only -r HEAD` output:
```
src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt
src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt
src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt
```
Exactly the 5 authorized files. `git show --stat HEAD` confirms `5 files changed, 292 insertions(+), 18 deletions(-)`.
`docs/plans/fast/` files were NOT staged (ledger.md remains a pre-existing unstaged controller-owned modification; this
report file is intentionally not staged).

## Deviations

- **D-1 (forced, I-1-determined)**: the P2a test `send and prompt rule ids still come from factRuleIds`
  (`QaFactSelectionServiceTest.kt`) asserted `assertFalse(2L in resolved.promptRuleIds)` under the pre-P2b semantics.
  P2b's 需求方决策 2 (and 00-execution-order.md authority) intentionally revises that: bound-but-not-evidence facts DO
  enter `promptRuleIds` while `sendQaRuleIds` stays evidence-only. The plan's own required full-suite pass
  (`Failures: 0`) uniquely determines the repair. The case was renamed
  `send rule ids stay evidence only while bound facts join prompt ids`, its send-side assertions preserved verbatim,
  its prompt assertions updated to I-1 (`[1L, 2L]`, evidence first), with a comment documenting the P2b supersession.
  No other existing test asserted the pre-P2b prompt semantics (verified by grep across `src/test`).
- No other deviations. No unrelated behavior repaired; plans untouched; no push/merge/amend/squash.

## Freshness

- Plan identity rechecked: YES (SHA-256 unchanged `fba90709…` before and after execution)
- Worktree identity rechecked: YES (root/branch/git-dir/HEAD verified via `worktree_identity.py --expect-*` before commit)
- Reported commit reachable from target branch: YES (`git branch --contains a3ef1cd…` → `fast/2026-08-20-execution-order`; commit is HEAD)
- Required commands run this invocation: YES (all 12 commands above)
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None. All authorized work complete; all required commands green.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p` against this plan identity.
