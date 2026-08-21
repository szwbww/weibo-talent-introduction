# Child p2 Brief — 计划 P2：可信回复工作台操作遮罩补全 + 确认弹窗对比度修复

- Exact approved plan (authoritative contract; read in full first):
  `docs/plans/2026-08-21/ui-tweaks-02-overlay-and-dialog-contrast.md`
  (identity commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd)
- Master plan (ordering and global constraints):
  `docs/plans/2026-08-21/ui-tweaks-00-execution-order.md`
- Child base (product boundary): `53e12b979025e1df5f36736b2baf30d9e0bc688e` (p1 terminal Code head)
- Cache key for this child: `20260821-v10-overlay-contrast` (I-8; P1 set v9, P3 bumps to v11)

## Global constraints (from master plan)

1. Symbol/DOM anchors authoritative; line numbers cross-check only (P1 shifted index.html/app.js line numbers).
2. Out of scope (do NOT touch): workbench AUTO_PREVIEW mode code (11 sites); backend
   AutoReplyPreviewService/endpoint; mailbox resumeProgressPollingIfNeeded; hardcoded `未填写`
   label. P1 already removed the auto-preview host from app.js — do not re-introduce it.
3. `app.js` and all Kotlin source/tests are NOT in the file list — do not modify them.
4. Commit locally as `feat(fast-p): implement p2`; exclude fast-p evidence files.

## Authorized files (6 — exactly; A2 amended 2026-08-21)

| # | File | Action |
|---|---|---|
| 1 | src/main/resources/static/styles.css | modify (T1-1..T1-3, T2-1..T2-3) |
| 2 | src/main/resources/static/trust-reply-workbench.js | modify (T1-4, T1-5) |
| 3 | src/main/resources/static/index.html | modify 3 lines (T3-1) |
| 4 | src/test/js/batchSendTaskConsoleVisualFix.test.js | modify 3 lines (T3-2) |
| 5 | src/test/js/overlayAndDialogContrast.test.js | NEW (T3-3) |
| 6 | src/test/js/checkRepliesRelocation.test.js | modify (T3-4, amendment A2) |

Amendment A2 (approved 2026-08-21): T3-4 updates ONLY the CACHE_KEY const and the I-3 triad
assertion in checkRepliesRelocation.test.js from `20260821-v9-check-replies-move` to
`20260821-v10-overlay-contrast`; all other assertions verbatim.
Amendment A3 (approved 2026-08-21, master plan): each later child (p3, p4) may likewise sync
cache-key-asserting tests created by earlier children of this run (checkRepliesRelocation.test.js,
overlayAndDialogContrast.test.js, manualReplySubjectPrefill.test.js) to its own triad value,
counting them against its file budget.

## Key invariants (plan sections are authoritative)

- I-1: overlay rendered inside `renderMarkup()` via `${renderBusyOverlay()}`, never appended after
  the fact (no new `createElement` usage).
- I-2: overlay anchor is `.reply-workflow-content` (not `<details>`); markup position per plan.
- I-3: new `position: relative` must not change any existing child positioning; `position: absolute`
  count delta exactly 1 (the overlay itself).
- I-4: overlay cancel button reuses `data-action="cancel-generation"`; `onClick` unchanged.
- I-5: `busyOverlayState()` branch order mirrors the first five branches of
  `factActionBlockReason()`; `factActionBlockReason` function body diff empty.
- I-6: no inline style in overlay markup (test asserts `!/style=/` on pending-state innerHTML).
- I-7: `.action-dialog` must not use `var(--panel-bg)`; opaque + dark text; dark-mode media block
  (styles.css:9478..end) gains one override each for `.action-dialog` / `.trust-reply-busy-overlay` /
  `.trust-reply-busy-card`.
- I-8: triad `?v=` same value `20260821-v10-overlay-contrast`; batchSendTaskConsoleVisualFix.test.js:49-51 verbatim.
- S-1: `.trust-reply-workbench .reply-workflow-content` block matches plan's post-change code verbatim.
- S-2: new rules (4 + 2 dark) verbatim per plan; no new spinner styles (`ai-reply-loading-spinner`
  count unchanged; `@keyframes ai-reply-spin` count == 1).
- S-3: `.action-dialog` block matches plan verbatim; `.action-dialog-body p` +
  `.action-dialog-body .ai-reply-coverage` new rules; global `p` block (styles.css:302-306) diff empty.

## Required commands (run all; record exact outputs)

```bash
node --test src/test/js/overlayAndDialogContrast.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
node --test src/test/js/trustReplyWorkbench.test.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

Pass criteria: node --test exit 0 + `# fail 0`; node --check exit 0 no output; mvn test exit 0 with
`Tests run: N, Failures: 0, Errors: 0` and a `node --test` execution record; git diff --check exit 0.

## Downstream interfaces (later children depend on these)

- Triad value in index.html exactly `20260821-v10-overlay-contrast` (P3 bumps to v11).
- batchSendTaskConsoleVisualFix.test.js:49-51 assert the v10 key.
- `trust-reply-workbench.js` new overlay hooks (`renderBusyOverlay`, busy overlay markup/classes)
  are P2-local; no downstream child reads them.
