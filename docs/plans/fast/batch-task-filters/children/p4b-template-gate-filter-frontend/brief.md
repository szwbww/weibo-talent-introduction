# Fast-P Child Brief: p4b-template-gate-filter-frontend

## Authority
- Master plan: `docs/plans/2026-08-15/batch-task-filters-main.md` (commit d6980764, amended A1)
- Child plan: `docs/plans/2026-08-15/p4b-template-gate-filter-frontend.md` (commit 72ea4f55 — unaffected by A1) — **the complete contract. Read it in full before implementing** (also read master plan X-3 and P4a's I4a-3 for the drop-set rationale).
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters`
- Branch: `fast/batch-task-filters`
- Child base SHA: `9cde7473` (p4a terminal code head; p3b foundation committed on branch)

## Prerequisites (verified on branch before dispatch)
- P4a committed: backend accepts `gateFilterEnabled: boolean`; `BatchSendTaskConfigView.gateFilterEnabled`; `RecipientScope.gateEsFields`; `ExpertSearchService.fieldPresenceFilters`; DTO shape unchanged.
- P3b committed: editor/manual panels stable in multi-picker form; `BATCH_MULTI_PICKER_REGISTRY` + 7 foundation functions present.

## Global constraints (master plan, binding)
- **N4b-1**: expert list page `#expertGateTemplateFilter` + `initExpertGateFilter` (app.js:11585-11760) ONE LINE unchanged (reference only for failure-handling strategy).
- **N4b-2**: the four multi-pickers (tags/regions/email domains/operator statuses) behavior unchanged.
- **N4b-3**: `scheduleRecipientPreview` (500ms debounce) and `recipientPreviewRequestSeq` mechanism structure unchanged.
- **N4b-4**: other manual diff fields unchanged.
- **N4b-5**: styles.css — APPEND ONLY (zero deletions/modifications of existing rules).

## Authorized files (complete, exclusive list — the ONLY frontend plan with a CSS change)
1. `src/main/resources/static/styles.css` (APPEND the S4b-1 CSS block verbatim after `.batch-tag-picker-empty`)
2. `src/main/resources/static/index.html` (exactly the 2 new field blocks at the end of each `.batch-config-editor-grid`)
3. `src/main/resources/static/app.js`
4. `src/test/js/batchSendTaskConsoleInteraction.test.js`

No `.kt`/`.sql` files. Fast-p artifacts (`docs/plans/fast/…`) must NOT be in the commit.

## Required work (per child plan T4b-1..T4b-7)
- T4b-1: CSS append verbatim (copy the block in S4b-1 EXACTLY — no added/removed/changed properties).
- T4b-2: index.html — insert the two verbatim blocks (editor `#editorFieldGateFilter`, manual `#manualFieldGateFilter`) as the LAST child of each grid; manual block keeps `.batch-config-diff-badge`/`.batch-config-diff-original` as direct children.
- T4b-3: `BATCH_GATE_FILTERABLE_FIELDS` (6 keys MUST byte-match `ExpertSearchService.ALLOWED_HAS_FIELDS` — paste both grep outputs in the report) + `batchGateState` + `refreshBatchGateState(kind)` implementing the 8-step spec (unavailable when templateId empty / request fails / esFields empty / fields all dropped; warn text for all-dropped; chip rendering `.tag-chip.active`; dropped note div; label sync; always ends with `scheduleRecipientPreview(kind)`).
- T4b-4: rewrite `refreshRecipientPreview` per the verbatim spec (two requests same seq, unavailable = single request I4b-6, excluded = off−on I4b-1, stale-discard on both seq checks I4b-2); add helpers `baseHintHtml` (verbatim identical to the pre-change hint line) + `gateToggleId`.
- T4b-5: wiring table (editor checkbox init from `config.gateFilterEnabled`, snapshot/payload `gateFilterEnabled: gateToggleChecked(kind)`, manual snapshot/readManualFormValues/deepCloneConfig/fillManualFormDefaults/fillManualFormFromDraft, template-dropdown change → refreshBatchGateState, toggle change → updateGateToggleLabel + scheduleRecipientPreview, `renderBatchConfigRow` pill). **Intentional deviation to record**: list pill blue variant is 「门禁过滤 · 开」 NOT 「门禁过滤 · N 字段」 (no per-row gate-fields request) — write this in the report.
- T4b-6: diff 5 points (normalizeManualSnapshot Boolean(), formatManualDiffValue 开启/关闭, fieldDefs, fieldMap, clearAllDiffMarkers append `manualFieldGateFilter`).
- T4b-7: tests G1-G14.

## Downstream interfaces
- None consumed by later children (last child). Must keep `refreshRecipientPreview`'s exported behavior identical for `scheduleRecipientPreview` callers; `buildConfigEditorRecipientSnapshot`/`buildManualExecutionSnapshot` shapes gain `gateFilterEnabled`.

## Required commands (run freshly, record exit codes + counts)
1. `node --test src/test/js/batchSendTaskConsoleInteraction.test.js` → exit 0, `fail 0`
2. `node --test src/test/js/*.test.js` → exit 0, `fail 0`
3. `git diff --check` → no output
Full `mvn test` deferred to merge gate — do NOT run Maven.

## Commit rules
- Commit implementation locally as `feat(fast-p): implement p4b-template-gate-filter-frontend`, staging ONLY the 4 authorized files.
- Do not push, merge, rebase, amend, or rewrite history. Do not touch `docs/plans/fast/`.
- Write the full result (file:line evidence, grep receipts: `grep -c "recipients/preview" app.js` == 1, seq single increment, I4b-5's 5 registration points with line+context, ALLOWED_HAS_FIELDS vs BATCH_GATE_FILTERABLE_FIELDS comparison, styles.css diff = additions only, intentional pill deviation note; command outputs; commit SHA) to `docs/plans/fast/batch-task-filters/children/p4b-template-gate-filter-frontend/execution.md`, overwriting the placeholder. Do not stage it.
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, commit SHA, command summary, report path.
