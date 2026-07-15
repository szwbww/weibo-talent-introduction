# Batch Manual Tags and Diff Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove false manual-configuration diff styling when no task is selected and replace both batch tag text fields with searchable multi-select dropdowns.

**Architecture:** Keep the existing `tags: string[]` API contract. Add a reusable DOM tag-picker around hidden CSV-compatible values, preload merged CANDIDATE/APPLICATION tag aggregations, and explicitly detach stale manual source state when the task search text no longer represents the selected task.

**Tech Stack:** Vanilla JavaScript, HTML/CSS, Node.js built-in test runner, Maven.

## Global Constraints

- Do not add or change backend APIs.
- Preserve historical/configured tags missing from aggregation results.
- Only render manual differences when `batchTaskState.manualSource` is non-null.
- Detaching a source through search text preserves current manual form values.
- Clicking “清除选择” continues to restore independent execution defaults.

---

### Task 1: Manual source detachment regression

**Files:**
- Modify: `src/test/js/batchSendTaskConsoleInteraction.test.js`
- Modify: `src/main/resources/static/app.js`

**Interfaces:**
- Produces: `detachBatchManualSourcePreservingDraft(): void`
- Consumes: `readManualFormValues()`, `clearAllDiffMarkers()`, `updateManualSourceInfo()`

- [ ] Add a failing test proving that clearing/changing `batchManualSourceQuery` removes `manualSource`, clears hidden source metadata, preserves the current form snapshot, and clears diff markers.
- [ ] Run `node --test src/test/js/batchSendTaskConsoleInteraction.test.js` and confirm the new test fails because detachment is absent.
- [ ] Implement `detachBatchManualSourcePreservingDraft()` and call it from `handleManualSourceSearch()` whenever the query differs from the selected config name.
- [ ] Re-run the targeted test and confirm it passes.

### Task 2: Shared searchable multi-select tag picker

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/app.js`
- Modify: `src/main/resources/static/styles.css`
- Modify: `src/test/js/batchSendTaskConsoleInteraction.test.js`
- Modify: `src/test/js/batchSendTaskConsoleVisualFix.test.js`

**Interfaces:**
- Produces: `normalizeBatchTags(value): string[]`, `setBatchTagPickerValue(valueId, tags): void`, `readBatchTagPickerValue(valueId): string[]`, `renderBatchTagPicker(valueId): void`, `loadBatchTagOptions(): Promise<void>`
- Consumes: `/api/experts/tags/aggregation?level=CANDIDATE`, `/api/experts/tags/aggregation?level=APPLICATION`

- [ ] Add failing interaction tests for aggregation merge/deduplication, configured-tag retention, multi-select toggling, and `string[]` form reads.
- [ ] Add failing visual contract tests requiring searchable tag picker markup in both editor and manual sections.
- [ ] Run the two targeted test files and confirm failures describe missing picker behavior/markup.
- [ ] Replace the two tag text inputs with reusable picker markup while retaining hidden IDs `batchConfigEditorTags` and `batchManualTags`.
- [ ] Add picker rendering, search, selection/removal, dropdown closing, and aggregation preload helpers.
- [ ] Update editor/manual fill and read paths to use tag arrays through the picker helpers.
- [ ] Add scoped picker styles and responsive behavior.
- [ ] Re-run targeted tests and confirm they pass.

### Task 3: Regression verification

**Files:**
- Verify all modified production and test files.

- [ ] Run `node --check src/main/resources/static/app.js`.
- [ ] Run `node --test src/test/js/*.test.js` and confirm zero failures.
- [ ] Run `git diff --check` and confirm no whitespace errors.
- [ ] Run `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` and confirm `BUILD SUCCESS`.
- [ ] Inspect scoped diffs and confirm unrelated dirty-worktree files were not changed.
