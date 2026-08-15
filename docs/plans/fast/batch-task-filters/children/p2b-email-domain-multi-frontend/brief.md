# Fast-P Child Brief: p2b-email-domain-multi-frontend

## Authority
- Master plan: `docs/plans/2026-08-15/batch-task-filters-main.md` (commit 72ea4f55)
- Child plan: `docs/plans/2026-08-15/p2b-email-domain-multi-frontend.md` (commit 72ea4f55) — **the complete contract. Read it in full before implementing** (also read master plan X-3 frontend style inventory — reusable class line numbers, picker DOM contract, `readBatchRegionPickerValue` verbatim).
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters`
- Branch: `fast/batch-task-filters`
- Child base SHA: `8d8dccb2` (p2a's terminal code head — p2a is committed on this branch)

## Prerequisite (P2a, verified)
P2a is committed on this branch: backend accepts `emailDomains: string[]` on all four endpoints, `toView()`/commands carry `emailDomains: List<String>`, entity column is `email_domains_json`. Do NOT re-verify backend; consume the interface.

## Global constraints (master plan, binding for this child)
- **X-3**: values stored comma-delimited in hidden input; option values must NOT contain commas; family element id contract `<valueId>Chips`/`Search`/`Dropdown` + `data-tag-picker="<valueId>"`.
- **N-3**: existing tag/region pickers (`batchConfigEditorTags`/`Regions`, `batchManualTags`/`Regions`) DOM/CSS/JS untouched — one line.
- **N-1/N2b-2**: expert list page `#expertEmailDomainFilter` single-select code untouched (listed line ranges in plan).
- **N-4**: the other 7 manual-execution "modified" diff fields' behavior unchanged.
- **N2b-5**: 500ms debounce + stale-response discard in `refreshRecipientPreview` unchanged.

## Authorized files (complete, exclusive list)
1. `src/main/resources/static/app.js`
2. `src/main/resources/static/index.html` (exactly the 2 DOM block replacements S2b-1 :1200-1206 and S2b-2 :1368-1375)
3. `src/test/js/batchSendTaskConsoleInteraction.test.js`

**`styles.css` is FORBIDDEN in this diff** (N2b-3). No `.kt`/`.sql` files. Fast-p artifacts (`docs/plans/fast/…`) must NOT be in the commit.

## Required work (per child plan T2b-1..T2b-6)
- T2b-1: new generic multi-picker foundation — registry `BATCH_MULTI_PICKER_REGISTRY` (initial entries for both editor+manual email domains) + 7 functions `readBatchMultiPickerValue`/`setBatchMultiPickerValue`/`renderBatchMultiPicker`/`toggleBatchMultiPickerValue`/`openBatchMultiPicker`/`closeBatchMultiPicker`/`bindBatchMultiPicker`, derived verbatim from the `renderBatchRegionPicker` family (app.js:13773-13870) with exactly the 3 stated differences; `batchProviderOptions()` absorbing the provider compatibility logic from `fillBatchConfigEditorProviderSelect`. Insert after the region-picker family, before `buildConfigEditorRecipientSnapshot`. DO NOT touch the tag/region picker functions (I2b-2).
- T2b-2: index.html replacements — verbatim blocks from S2b-1/S2b-2 (outer element MUST be `<div>`, not `<label>`; diff badge/original divs kept as direct children in manual panel).
- T2b-3: editor wiring per the plan's table (showBatchConfigEditor, buildConfigEditorRecipientSnapshot, delete fillBatchConfigEditorProviderSelect, saveBatchConfigEditor payload `emailDomains: readBatchMultiPickerValue(...)`, remove `"batchConfigEditorEmailDomain"` from the change-listener array at :15042, add `bindBatchMultiPicker(...)` calls next to the existing `bindBatchRegionPicker(...)` call).
- T2b-4: manual panel wiring + the 5 diff registration points (I2b-4): normalizeManualSnapshot (sorted, I2b-5), formatManualDiffValue, computeManualDiffs fieldDefs, computeAndRenderDiffs fieldMap, clearAllDiffMarkers verify. **First read app.js:14336-14378 to learn the ACTUAL equality comparison for tags/regions arrays; write your conclusion in the execution report — do not assume.** If equality uses `===`, arrays must be normalized to a comparable form (e.g. sorted joined string) in normalizeManualSnapshot.
- T2b-5: `renderBatchConfigRow` :13389 scope-line change verbatim.
- T2b-6: tests V1-V10 from the plan's table.

## Downstream interfaces (consumed by later children — must match exactly)
- Registry `BATCH_MULTI_PICKER_REGISTRY` + the 7-function family: P3b registers `batchConfigEditorOperatorStatuses`/`batchManualOperatorStatuses` entries ONLY, reusing the family (no second implementation).
- `readBatchMultiPickerValue(valueId)` returns `string[]`; `setBatchMultiPickerValue(valueId, arr)` writes comma-joined hidden input.
- Manual diff keys stay `emailDomains`; DOM id `manualFieldEmailDomain` unchanged.
- `batchProviderOptions()` used by both registry entries.

## Required commands (run freshly, record exit codes + counts)
1. `node --test src/test/js/batchSendTaskConsoleInteraction.test.js` → exit 0, `fail 0`
2. `node --test src/test/js/*.test.js` → exit 0, `fail 0` (baseline 549 after p1)
3. `git diff --check` → no output
Full `mvn test` deferred to merge gate — do NOT run Maven in this child.

## Commit rules
- Commit implementation locally as `feat(fast-p): implement p2b-email-domain-multi-frontend`, staging ONLY the 3 authorized files.
- Do not push, merge, rebase, amend, or rewrite history. Do not touch `docs/plans/fast/`.
- Write the full result (file:line evidence incl. grep receipts for I2b-4's 5 registration points, command outputs, commit SHA) to `docs/plans/fast/batch-task-filters/children/p2b-email-domain-multi-frontend/execution.md`, overwriting the empty placeholder. Do not stage it.
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, commit SHA, command summary, report path.
