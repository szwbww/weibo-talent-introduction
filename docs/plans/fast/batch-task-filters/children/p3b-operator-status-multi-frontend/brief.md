# Fast-P Child Brief: p3b-operator-status-multi-frontend

## Authority
- Master plan: `docs/plans/2026-08-15/batch-task-filters-main.md` (commit d6980764, amended A1)
- Child plan: `docs/plans/2026-08-15/p3b-operator-status-multi-frontend.md` (commit 72ea4f55 — unaffected by A1) — **the complete contract. Read it in full before implementing** (also read master plan X-3).
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters`
- Branch: `fast/batch-task-filters`
- Child base SHA: `1ba04713` (p3a terminal code head; p2b foundation committed on branch)

## Prerequisites (verified on branch before dispatch)
- P2b committed: generic multi-picker foundation `BATCH_MULTI_PICKER_REGISTRY` + 7 functions (`readBatchMultiPickerValue`/`setBatchMultiPickerValue`/`renderBatchMultiPicker`/`toggleBatchMultiPickerValue`/`openBatchMultiPicker`/`closeBatchMultiPicker`/`bindBatchMultiPicker`) in app.js, `batchProviderOptions()`, manual diff keys `emailDomains`.
- P3a committed: backend accepts `operatorStatuses: string[]`; `toView()`/commands carry `operatorStatuses`; `RecipientScope.operatorStatuses`.

## Global constraints (master plan, binding)
- **N-3**: existing tag/region pickers untouched; **N3b-2**: email-domain pickers (P2b) untouched.
- **N-1/N3b-3**: expert list page `#contactStatusFilter` single-select code untouched.
- **N3b-4**: `styles.css` ZERO changes in this diff.
- **X-3**: comma-delimited hidden input contract; option values must be English enum names (no commas).

## Authorized files (complete, exclusive list)
1. `src/main/resources/static/app.js`
2. `src/main/resources/static/index.html` (exactly the 2 DOM block replacements S3b-1 :1214-1219 and S3b-2 :1391-1399)
3. `src/test/js/batchSendTaskConsoleInteraction.test.js`

**`styles.css` FORBIDDEN.** No `.kt`/`.sql`. Fast-p artifacts (`docs/plans/fast/…`) must NOT be in the commit.

## Required work (per child plan T3b-1..T3b-6)
- T3b-1: register TWO entries in `BATCH_MULTI_PICKER_REGISTRY` (`batchConfigEditorOperatorStatuses`/`batchManualOperatorStatuses`); add `batchOperatorStatusOptions()` + `operatorStatusLabel(value)` derived from the EXISTING `operatorStatusOptions` constant (find its definition first with grep; paste it in the execution report — I3b-3). DO NOT modify the 7 foundation functions (N3b-1).
- T3b-2: index.html replacements — verbatim S3b-1/S3b-2 blocks, outer `<div>`, keep `id="manualFieldOperatorStatus"`.
- T3b-3: editor wiring per plan table (:13517, :13939, :14057, delete `fillBatchOperatorStatusSelectOptions`, remove its call at :15028, remove `"batchConfigEditorOperatorStatus"` from change-listener array :15043, add two `bindBatchMultiPicker(...)` calls next to existing ones).
- T3b-4: manual wiring + diff 5 points (I3b-4). **KEY GAP**: grep shows `deepCloneConfig`/`fillManualFormDefaults`/`normalizeManualSnapshot`/`formatManualDiffValue`/`computeManualDiffs` fieldDefs currently have NO `operatorStatus` entry — add them (3 NEW entries), don't rename. `computeAndRenderDiffs` fieldMap key → `operatorStatuses` (DOM id unchanged); verify `clearAllDiffMarkers` still contains `"manualFieldOperatorStatus"`. normalizeManualSnapshot sorts (I3b-5). First read `computeManualDiffs` (:14336-14378) to confirm array comparison; write the conclusion in the report.
- T3b-5: `renderBatchConfigRow` new scope line (S3b-3, verbatim).
- T3b-6: tests W1-W10 from the plan table.

## Downstream interfaces (consumed by later children — must match exactly)
- `BATCH_MULTI_PICKER_REGISTRY` gains the two operator-status entries; foundation functions unchanged (P4b relies on this).
- `operatorStatuses` keys in: editor snapshot, save payload, manual snapshot, manual diff 5 points.
- `operatorStatusLabel(value)` + `batchOperatorStatusOptions()` available (P4b may reference label mapping).

## Required commands (run freshly, record exit codes + counts)
1. `node --test src/test/js/batchSendTaskConsoleInteraction.test.js` → exit 0, `fail 0`
2. `node --test src/test/js/*.test.js` → exit 0, `fail 0`
3. `git diff --check` → no output
Full `mvn test` deferred to merge gate — do NOT run Maven.

## Commit rules
- Commit implementation locally as `feat(fast-p): implement p3b-operator-status-multi-frontend`, staging ONLY the 3 authorized files.
- Do not push, merge, rebase, amend, or rewrite history. Do not touch `docs/plans/fast/`.
- Write the full result (file:line evidence incl. grep receipts for I3b-4's 5 registration points with NEW/renamed marking, operatorStatusOptions definition line, command outputs, commit SHA) to `docs/plans/fast/batch-task-filters/children/p3b-operator-status-multi-frontend/execution.md`, overwriting the placeholder. Do not stage it.
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, commit SHA, command summary, report path.
