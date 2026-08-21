# Child p1 Brief — 计划 P1：「检查回复」移入收发件箱 + 删除「自动回复预览」

- Exact approved plan (authoritative contract; read in full first):
  `docs/plans/2026-08-21/ui-tweaks-01-check-replies-move-and-auto-preview-removal.md`
  (identity commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd)
- Master plan (ordering and global constraints):
  `docs/plans/2026-08-21/ui-tweaks-00-execution-order.md`
- Child base (product boundary): `bb34ca2001d0abeac3bd7a8fc13995769e14143e`
- Cache key for this child: `20260821-v9-check-replies-move` (I-3; P2 will bump to v10)

## Global constraints (from master plan)

1. Plans were authored against main @ bb34ca2; symbol/DOM anchors are authoritative for
   locating change points; line numbers are cross-check only (P1-P4 bump the same triad,
   line numbers shift between children).
2. Out of scope (do NOT touch): `trust-reply-workbench.js` AUTO_PREVIEW mode code
   (11 sites incl. MODES/MODE_SOURCE/renderReadOnlyZone/requestJson gate/autoRunBar);
   backend `AutoReplyPreviewService` + `GET /api/mail/unmatched-inbound/{id}/auto-reply-preview`
   (`UnmatchedInboundMailController.kt:259`); no `resumeProgressPollingIfNeeded()` addition to
   mailbox view; do not change the hardcoded `未填写` label at app.js:9952.
3. `trust-reply-workbench.js` must remain byte-identical after this child; no Kotlin changes.
4. Commit locally as `feat(fast-p): implement p1`; exclude fast-p evidence files from the commit.

## Authorized files (9 — exactly; A1 amended 2026-08-21)

| # | File | Action |
|---|---|---|
| 1 | src/main/resources/static/index.html | modify (T1-1, T1-2, T3-1) |
| 2 | src/main/resources/static/app.js | modify (T2-1..T2-7) |
| 3 | src/main/resources/static/styles.css | modify (T1-3 only) |
| 4 | src/test/js/batchEntryRelocation.test.js | modify (T3-4) |
| 5 | src/test/js/autoPreviewWorkbenchHost.test.js | full rewrite (T3-5, exact content in plan) |
| 6 | src/test/js/trustReplyWorkbenchSharedMount.test.js | modify 1 line (T3-3) |
| 7 | src/test/js/batchSendTaskConsoleVisualFix.test.js | modify 3 lines (T3-2) |
| 8 | src/test/js/checkRepliesRelocation.test.js | NEW (T3-6; content defined by I-1/I-2/S-1/S-2 acceptance criteria) |
| 9 | src/test/js/unmatchedQaReplySource.test.js | modify (T3-7, amendment A1) |

Amendment A1 (approved 2026-08-21): T3-7 rewrites ONLY the 4th case
"mounts the read-only AUTO_PREVIEW workbench host from source" into a retirement guard
asserting `mountAutoPreviewTrustReply`, `data-trust-reply-auto-preview-host`,
`data-auto-preview-status` are ALL ABSENT from app.js (same shape as T3-5's guard, I-4);
the other 7 cases stay verbatim.

## Key invariants (plan sections are authoritative)

- I-1: `checkRepliesBtn` exactly 1× in index.html; 5 app.js reference sites verbatim
  (taskButtonOriginalTexts:690, taskButtonMapping.CHECK_REPLIES.btnId:701,
  handleCheckReplies:4885, executeCheckReplies:4907, taskLaunchConfigs.CHECK_REPLIES.btnId:5208).
- I-2: `.view` sections stay in DOM; no view-conditional logic for check-replies;
  `setView`/`resumeProgressPollingIfNeeded`/`expert-select-cb` unchanged.
- I-3: triad `?v=` three sites same value `20260821-v9-check-replies-move`;
  batchSendTaskConsoleVisualFix.test.js:49-51 asserted verbatim to it.
- I-4: all 12 retired identifiers gone from app.js+index.html (list in plan);
  autoPreviewWorkbenchHost.test.js rewritten to the plan's exact retirement-guard content.
- I-5: `unmountMailboxTrustReplyHosts` name + 8 call sites kept; body shrunk to single
  `unmountLiveTrustReply();`.
- I-6: `requireTrustReplyWorkbenchRuntime(host)` count 4→3; trustReplyWorkbenchSharedMount.test.js:352 → 3.
- A1 (T3-7): unmatchedQaReplySource.test.js 4th case flipped to retirement guard; other 7 cases verbatim.
- S-1: `.panel-head-actions` new class inserted verbatim after `.panel-head h2::before` block
  (styles.css:828-830) before `/* Tables */`; panel-head skeleton per plan S-1.
- S-3: `autoPreviewHtml` constant deleted entirely (both ternary branches), no placeholder.

## Required commands (run all; record exact outputs)

```bash
node --test src/test/js/checkRepliesRelocation.test.js
node --test src/test/js/autoPreviewWorkbenchHost.test.js
node --test src/test/js/batchEntryRelocation.test.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

Pass criteria: node --test exit 0 + `# fail 0` (+ `# cancelled 0` for the full glob);
node --check exit 0 no output; mvn test exit 0 with `Tests run: N, Failures: 0, Errors: 0`
and a `node --test` execution record in output (skipNodeTests must not skip);
git diff --check exit 0.

## Downstream interfaces (later children depend on these)

- Triad value in index.html must be exactly `20260821-v9-check-replies-move` (P2 bumps to v10).
- batchSendTaskConsoleVisualFix.test.js:49-51 must assert the v9 key (P2/P3/P4 rewrite these lines).
- No other cross-child interface. trust-reply-workbench.js byte-identical (P2 modifies it next).
