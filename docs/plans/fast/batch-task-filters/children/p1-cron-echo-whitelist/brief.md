# Fast-P Child Brief: p1-cron-echo-whitelist

## Authority
- Master plan: `docs/plans/2026-08-15/batch-task-filters-main.md` (commit 72ea4f55)
- Child plan: `docs/plans/2026-08-15/p1-cron-echo-whitelist.md` (commit 72ea4f55) — **the complete contract. Read it in full before implementing.**
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters`
- Branch: `fast/batch-task-filters`
- Child base SHA: `72ea4f55`

## Global constraints (from master plan)
- Frontend-only child. **Zero** changes to `.kt` / `.sql` / `.yml` (child N1-4), `styles.css`, `index.html` (child S1-1).
- `saveBatchConfigEditor` must remain byte-for-byte unchanged (child N1-2 / master N-6): custom cron submits the raw input string.
- `syncBatchConfigEditorScheduleFields()` call position unchanged (N1-3).
- Backend invariants M-1..M-5 do not apply to this child (no backend surface touched).

## Authorized files (complete, exclusive list)
1. `src/main/resources/static/app.js`
2. `src/test/js/batchSendTaskConsoleInteraction.test.js`

Everything else in the repo is OFF LIMITS. Fast-p artifacts (`docs/plans/fast/…`) must NOT appear in your commit.

## Required work (per child plan T1-1, T1-2)
- Replace the cron decode block at `app.js:13525-13537` with the whitelist decode given verbatim in the plan (T1-1). Keep the three lines `setVal("batchConfigEditorFrequency", freq)` / `setVal("batchConfigEditorTime", time)` / `syncBatchConfigEditorScheduleFields()` in place, unchanged.
- Add top-level helpers `isCronClock` / `padClock` immediately before `showBatchConfigEditor` (NOT inside its body — tests `extractFn` the function body).
- Append test cases U1–U11 at `batchSendTaskConsoleInteraction.test.js` after line 505, using the sandbox construction at :481-497 verbatim plus `isCronClock`/`padClock` injection.

## Downstream interfaces
- None consumed by later children. Do NOT change DOM ids (`#batchConfigEditorFrequency`, `#batchConfigEditorTime`, `#batchConfigEditorCron`, `#batchConfigEditorCronField`, `#batchConfigEditorCronPreview`), the save path, or `syncBatchConfigEditorScheduleFields()` behavior.

## Required commands (run freshly, record exit codes + counts)
1. `node --test src/test/js/batchSendTaskConsoleInteraction.test.js` → exit 0, `fail 0`
2. `node --test src/test/js/*.test.js` → exit 0, `fail 0`
3. `git diff --check` → no output

Baseline at base 72ea4f55: full JS suite 538 tests, 538 pass, 0 fail. Full `mvn test` is deferred to the merge gate (recorded in ledger Baseline), do NOT run it in this child.

## Commit rules
- Commit implementation locally as `feat(fast-p): implement p1-cron-echo-whitelist`, staging ONLY the two authorized files.
- Do not push, merge, rebase, amend, or rewrite history. Do not touch `docs/plans/fast/`.
- Write the full result to `docs/plans/fast/batch-task-filters/children/p1-cron-echo-whitelist/execution.md` (overwrite the empty file; do not include it in the implementation commit).
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, commit SHA, command summary, report path.
