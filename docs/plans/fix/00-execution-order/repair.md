# Repair Plan: 00-execution-order

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-08-16/00-execution-order.md
Verification report: aggregate/master review, 2026-08-16
Implementation boundary: edda3e4e67e8b4511f3c7ca76b09926c56e4f69a..4d7f206a4f506104af73f3e63e4fceea3d857ef7

## Objective

Task-audit retention never deletes more than the configured `maxRowsPerRun` for either table in one run.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | B5 I3-2: batch deletion stops at the per-run total cap. | `purgeLoop` always supplies the full configured batch size, then checks the cap after deletion. |

## Findings Excluded

| Finding | Reason |
|---|---|
| N/A | No other confirmed mandatory violation. |

## Unchanged Contract

- Delete `task_progress_log` before `task_execution`.
- Keep the `created_at` / `started_at` predicates, no join/existence relation, and no self-task exemption.
- Keep independent per-table error handling and final-status semantics.
- Preserve default batch size (2000), default cap (200000), and existing configuration keys.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt | Bound each repository delete call by remaining per-run capacity. |
| src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt | Prove an uneven cap does not overshoot and correct the contradictory assertion. |

## Repair Tasks

### R-1: Enforce the per-run retention cap

- Resolves: V-1
- Root cause: `purgeLoop` makes a full-size delete before comparing the accumulated count to `maxRowsPerRun`; with batch size 2000 and cap 3000, it returns 4000.
- Files: `src/main/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionService.kt`; `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskAuditRetentionServiceTest.kt`
- Change: Pass each delete call no more than the remaining capacity; stop before a call when no capacity remains.
- Regression test: With `batchSize=2000`, `maxRowsPerRun=3000`, and deletable rows remaining, assert result is 3000 and delete limits are 2000 then 1000 only.
- Existing verification: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=TaskAuditRetentionServiceTest test`.
- Must not change: Predicate, deletion order, per-table catches, scheduler enablement, retention cutoff/time zone, or result-status mapping.
- Prohibited: Changing migration files, configuration defaults, repository SQL, catalog semantics, unrelated cleanup, or broadening retention scope.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=TaskAuditRetentionServiceTest test`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
4. `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js`
5. `node --check src/main/resources/static/app.js`
6. `node --test src/test/js/*.test.js`
7. `git diff --check`

## Completion Criteria

- `maxRowsPerRun=3000` cannot delete or report more than 3000 rows for either table in one purge run.
- The 2000/1000 remaining-capacity sequence is machine-tested.
- All changed files remain inside the authorized list.
- Required verification commands pass.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/00-execution-order/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with subject `fix(fast-p): enforce task audit retention run cap`.
3. Appending `docs/plans/review/2026-08-16-execution-order/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with subject `docs(review-fast-p): record task audit retention repair execution`.
5. Returning to the already authorized `$review-fast-p docs/plans/fast/2026-08-16-execution-order/human-review-handoff.md` aggregate re-review in the same task when the human invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
