# Repair Plan: trust-reply-durable-locks-and-assembly-generation

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-08-04/trust-reply-durable-locks-and-assembly-generation.md
Verification report: review-p / 2026-08-04 / working-tree verification
Implementation boundary: `f50f00e` → current working-tree diff (11 files: 9 modified, 2 added)

## Objective

Every successful workbench state PUT, including deletion of the final locked item, opportunistically removes expired state rows.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-6 | P1 | I-5: every successful save opportunistically deletes expired rows | The empty-snapshot branch returns immediately after `stateStore.delete`, bypassing the prune performed by non-empty saves. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | Resolved: subset save/restore reuses the grounded trust boundary before persistence or restoration. |
| V-2 | Resolved: non-expired non-restored states retain optimistic state version for a later validated PUT. |
| V-3 | Resolved: decision invalidations durably save the unlocked snapshot with rollback. |
| V-4 | Resolved: cancellation marks the sequence terminal before durable-save completion and prevents a next item/assembly. |
| V-5 | Resolved: an unusable implicit saved fact selection falls closed to `STALE`; caller-explicit invalid selection remains rejected. |
| P2 scope mismatch: `TrustReplyWorkbenchItemFlowTest.kt` | Constructor wiring only; no confirmed behavioral defect. |

## Unchanged Contract

- No bootstrap or refresh generation; stale/invalid saved items restore zero answers.
- `PARTIAL` and `UNSUPPORTED` remain manual-only; the auto loop uses `ADJUST_ITEM`, never `FULL_DRAFT`.
- State deletes still require optimistic-version validation and return state version zero.
- No email sending, `operator_action_log` replay, CSS/layout changes, local storage, endpoint expansion, or relaxed source/evidence validation.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt | Prune expired rows after a successful empty-snapshot delete before returning its deletion response. |
| src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt | Prove the deletion save path invokes the expiry prune only after the delete succeeds. |

## Repair Tasks

### R-1: Prune expired state after successful snapshot deletion

- Resolves: V-6.
- Root cause: `saveState` branches for an empty `lockedItems` list at `TrustReplyWorkbenchService.kt:356-359`; unlike the non-empty save path at `:376-383`, that branch performs no expiry prune.
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`, `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt`.
- Change: After a successful optimistic delete, opportunistically prune expired rows before returning `DELETED`; do not prune if deletion reports a conflict/failure.
- Regression test: Save an empty snapshot with a matching expected version; assert delete succeeds, expiry prune is called, and the response remains `DELETED` with state version `0`. Assert a delete conflict does not report deletion success.
- Existing verification: focused service suite, then all required Node and Maven suites.
- Must not change: delete conflict semantics, source/evidence validation, durable payload contents, selection validation, or client-visible response shape.
- Prohibited: no table/schema changes, no pruning of non-expired state, no catch-and-ignore of a delete conflict, and no product/UI changes.

## Verification Commands

1. `node --check src/main/resources/static/trust-reply-workbench.js`
2. `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js`
3. `node --test src/test/js/*.test.js`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`
5. `git diff --check`

## Completion Criteria

- Every successful empty-snapshot state PUT triggers opportunistic expiry pruning before returning `DELETED`.
- An optimistic delete conflict leaves the existing conflict response and does not claim deletion success.
- Changed files remain inside the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
