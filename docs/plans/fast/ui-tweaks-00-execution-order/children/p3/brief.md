# Child p3 Brief — 计划 P3：人工富文本回复自动填入回复主题

- Exact approved plan (authoritative contract; read in full first):
  `docs/plans/2026-08-21/ui-tweaks-03-manual-reply-subject-prefill.md`
  (identity commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd)
- Master plan (ordering and global constraints):
  `docs/plans/2026-08-21/ui-tweaks-00-execution-order.md`
- Child base (product boundary): `0ad6b3b188e4cef69229fc3e5a06f1251d343db9` (p2 terminal Code head)
- Cache key for this child: `20260821-v11-reply-subject-prefill` (I-5; P2 set v10, P4 bumps to v12)

## Global constraints (from master plan)

1. Symbol/DOM anchors authoritative; line numbers cross-check only (P1/P2 shifted line numbers).
2. Out of scope: workbench AUTO_PREVIEW mode code; backend AutoReplyPreviewService/endpoint;
   mailbox resumeProgressPollingIfNeeded; hardcoded `未填写` label (app.js:9952, never updated).
3. `styles.css`, `trust-reply-workbench.js`, and all Kotlin source/tests are NOT in the file list —
   zero style changes this child (S-1: `git diff src/main/resources/static/styles.css` empty).
4. Commit locally as `feat(fast-p): implement p3`; exclude fast-p evidence files.

## Authorized files (7 — 4 from plan + 2 per A3 + 1 per A4)

| # | File | Action |
|---|---|---|
| 1 | src/main/resources/static/app.js | modify (T1-1, T2-1) |
| 2 | src/main/resources/static/index.html | modify 3 lines (T3-1) |
| 3 | src/test/js/batchSendTaskConsoleVisualFix.test.js | modify 3 lines (T3-2) |
| 4 | src/test/js/manualReplySubjectPrefill.test.js | NEW (T3-3) |
| 5 | src/test/js/checkRepliesRelocation.test.js | modify (A3: key v10→v11 in CACHE_KEY + I-3 assertion) |
| 6 | src/test/js/overlayAndDialogContrast.test.js | modify (A3: key v10→v11 in its triad assertion) |
| 7 | src/test/js/expertProfileAbsence.test.js | modify 1 line (T3-4, amendment A4) |

Amendment A4 (approved 2026-08-21): T3-4 — in `createRendererSandbox()` (expertProfileAbsence.test.js ~:326-349), add
`vm.runInContext(extractFunction("buildManualReplySubject"), sandbox);` immediately before the
`showUnmatchedDetail` registration (~:348); nothing else in that file changes. Reason: the S-1 template
line makes showUnmatchedDetail call the new top-level buildManualReplySubject, which the sandbox's fixed
function list does not load (ReferenceError at :384).

Amendment A3 (approved 2026-08-21, master plan §三键断言测试的跨计划同步规则):
each later child may update cache-key-asserting tests created by earlier children of this run
(checkRepliesRelocation.test.js from p1, overlayAndDialogContrast.test.js from p2) to its own
triad value `20260821-v11-reply-subject-prefill`; only the cache-key literal changes, all other
assertions verbatim; these count against the file budget (still ≤10).

## Key invariants (plan sections are authoritative)

- I-1: `buildManualReplySubject` mirrors server `buildReplySubject` verbatim (9 I/O cases in plan;
  Re: prefix rules; no Re: Re: stacking; empty subject → `Re:`).
- I-2: value truncated to 255 chars via `slice(0, 255)` inside `buildManualReplySubject`
  (300-char input → length 255).
- I-3: prefill happens once at detail-panel render, HTML-escaped via
  `value="${escapeHtml(buildManualReplySubject(record.subject))}"`; `manualReplySubject` id appears
  exactly 2× in app.js (render + read), no `.value =` assignment, no addEventListener on it.
- I-4: `${expertName}` placeholder survives verbatim in the input; no replace logic for `"${"`
  inside `buildManualReplySubject`.
- I-5: triad `?v=` same value `20260821-v11-reply-subject-prefill`; batchSendTaskConsoleVisualFix.test.js:49-51 verbatim.
- S-1: app.js:9956 line matches plan's post-change code verbatim (only `value` attribute added).

## Required commands (run all; record exact outputs)

```bash
node --test src/test/js/manualReplySubjectPrefill.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
node --test src/test/js/unmatchedQaReplySource.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=GroundedAutoReplyDecisionServiceTest
git diff --check
```

Pass criteria: node --test exit 0 + `# fail 0`; node --check exit 0 no output; mvn test exit 0 with
`Tests run: N, Failures: 0, Errors: 0` + `node --test` record; command 5: exit 0 — if Surefire reports
`No tests were executed` (class absent), judge by command 4's full result, NOT a failure;
git diff --check exit 0.

## Downstream interfaces (later children depend on these)

- Triad value in index.html exactly `20260821-v11-reply-subject-prefill` (P4 bumps to v12).
- batchSendTaskConsoleVisualFix.test.js:49-51 assert the v11 key.
- `buildManualReplySubject` is P3-local; no downstream child calls it.
