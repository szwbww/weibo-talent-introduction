# Child 04 — verify-log



## Epoch2 Attempt1 — 2026-10-29T16:10:02.286312+00:00

## Light Verification: LIGHT_FAIL

- Child: `04` — `docs/plans/2026-10-29/draft-review-04-reader.md` at `c17c9013ece5bf7967d6233cd97f5bfed47d1cab` (Epoch2 authoritative brief; plan identity `2eadefbae3db8205e028ce2cebad46b66f7912ce5fe465d33a7947496ed3c97a`).
- Boundary: `f7b24c9c7364ea64adc4fa1013083343399756d7..ae148e46ce143a73046c648d0a19500b37230c60`.
- Verifier: `DraftReview04Verifier` (independent of `DraftReview04Implementer`).
- Scope: four light gates only. No later-child review, broad audit, full verification skill, build, Git mutation, or product/test correction. This report is the only repository file appended by the verifier.

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1. Authorized scope | PASS | Exact boundary diff contains the eight authorized product/test paths: mailbox-readonly.js, app.js, styles.css, index.html, mailboxReadOnly.test.js, authFlow.test.js, mailReviewReader.test.js, mailboxChatStyle.test.js (under their planned static/test directories). The additional seven paths are controller plan/ledger/predecessor-child03 evidence, not child04 product expansion. The eighth test file only retires the obsolete blanket HTML prohibition, fixed-copy case, and old title assertion; no replacement wording pin or unrelated permission/style alteration. Current eight source paths have no diff from the exact terminal SHA. |
| 2. Explicit I-16–I-18 / S-1 / S-2 and inherited API authority | FAIL | F-01 and F-02 below are independently reproduced, patch-introduced failure paths. Otherwise inspected the persistent single native editor, shared HTML sanitizer and text-only mail history, four server-state write matrices, canonical save/latest-version submit, revision-aware dirty flush/navigation/logout, request ownership/sequences, revoke/unmount clearing, name-only identity, no attachment/admin/Storage capability, and literal CSS/DOM. Exact frontend methods/fields were traced through backend controller/service and predecessor I-1–I-7, not assumed from mocks. Required existing behavior cases pass in the fresh planned command, but do not cover the two reported branches. |
| 3. Every planned syntax/node command fresh | PASS | Fresh verifier execution after inspection: C1 exit0; C2 exit0; C3 exit0, 127 tests / 22 suites / 127 pass / 0 fail / 0 cancelled / 0 skipped / 0 todo, duration_ms 816.090417. These are verifier-run receipts, not the executor's historical 839ms receipt. |
| 4. Downstream shared CSS / host handle / cache-key / UI interfaces | PASS | S-1 CSS appended literally without changing existing mailbox-chat/global rules; fresh literal-CSS assertion passes. Existing versioned assets uniformly use `20261029-review-reader`, no new script/link; meeting-confirmation.js still exports MailboxMeeting.sanitizeDraftHtml before reader/app load. MailboxReadOnly.mount/unmount and handle.refresh remain, with flushDrafts/hasPendingDrafts added and refresh/logout consumers traced in app.js. Existing mailboxList/panel/title/menu hooks and restricted shell remain. No child05 implementation or later plan audited. |

### Fresh command receipts

Executed in `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`, in one ordered shell chain; shell exit0 confirms all commands completed successfully:

```sh
node --check src/main/resources/static/mailbox-readonly.js
node --check src/main/resources/static/app.js
node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js
```

- C1: exit0, no syntax diagnostics.
- C2: exit0, no syntax diagnostics.
- C3: exit0; `tests 127`, `suites 22`, `pass 127`, `fail 0`, `cancelled 0`, `skipped 0`, `todo 0`, `duration_ms 816.090417`.
- Additional focused `node -e` diagnostic: exit0. Reused only the finite-node harness and setup prefix, without editing tests; asserted the two failure branches and unmounted both instances. For F-02 the diagnostic rejected the emitted non-UUID request with422, matching the inspected real backend UUID parser; this is not represented as a fresh HTTP/backend run. For F-01 it opened history then failed the initial draft GET, confirmed the rendered reload action, and clicked it with confirm=true. Exact stdout:

```text
F-02: crypto.randomUUID unavailable; emitted canonicalUUID=false; state=暂无草稿; error=保存失败，内容尚未同步
F-01: failed initial detail; reload visible=true; click raised TypeError: Cannot read properties of null (reading 'id')
```

### Existing live evidence (historical, not verifier-performed)

Read execution.md's authenticated production-static/Spring/MySQL browser account, native contenteditable bold/list/real clipboard paste/caret observations, real API state/version/flush/revocation observations, final DB receipt, cleanup and final-source reload disclosure. Opened and inspected the retained final desktop and mobile screenshots:

- `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15b966959a6e7031.webp`
- `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15b96695bc6e7032.webp`

Both evidence files exist and show the restricted actual reader UI, rich draft body and literal-text mail history; execution.md identifies their original1440/390 viewports and final reloaded source. The authenticated real backend/native runtime proof is therefore present as historical executor evidence, not replaced by VM tests or claimed as a fresh verifier browser/backend exercise. Human acceptance has NOT been performed or declared.

### AUTO_FIX

No corrections applied: this verifier's repository write authority is restricted to this append-only log. The following are bounded in-scope corrections for the controller to return to the implementation owner, not permission to expand files or weaken tests:

- **F-01 (P2) — Make initial draft-load failure recoverable without a null-draft reload.** `src/main/resources/static/mailbox-readonly.js:519–521` (also loadDraft failure at346). Opening a history entry runs resetReview() before GET. If that GET fails with a network error, draft remains null but loadDraft sets conflict=true and renderReview exposes `重新加载`. Confirming that action unconditionally reads draft.id and throws TypeError, so the offered recovery cannot issue a retry. Preserve a valid requested identity for recovery or avoid offering this conflict-only action without a draft; keep local-input protection and grant checks intact. Reproducer above exercises actual module dispatch, not merely string matching.
- **F-02 (P2) — Emit a canonical UUID in the create-request fallback.** `src/main/resources/static/mailbox-readonly.js:447–448`. When crypto.randomUUID is unavailable (including ordinary non-localhost insecure HTTP contexts), the fallback sends a base36 timestamp/random token. The inherited service create() requires UUID.fromString(clientRequestId) plus exact canonical roundtrip and rejects this token with422. Retrying retains that same invalid token, leaving every new-draft attempt in this branch unusable. Keep retry-stable identity but generate a backend-compatible canonical UUID even without randomUUID; do not loosen backend admission or change API authority. Existing browser proof used localhost, where randomUUID is available, and the node responder accepts arbitrary request ids, so neither catches this real contract mismatch.

### RECORD_ONLY

N/A. No outside-scope issue was pursued or added to this report.

### Required Action

- AUTO_FIX — Return F-01/F-02 to the controller/authorized writer for bounded corrections within the existing eight-file scope, then freshly rerun the exact planned commands and request a new independent light attempt. Do not mark child04 complete on the strength of the127 passing existing tests.

## Epoch2 Attempt2 — 2026-10-29T16:18:28.445892+00:00

## Light Verification: LIGHT_PASS

- Child: `04` — `docs/plans/2026-10-29/draft-review-04-reader.md` at approved commit `c17c9013ece5bf7967d6233cd97f5bfed47d1cab`; authoritative Epoch2 brief and full approved eight-file plan read, including the obsolete-contract retirement amendment.
- Boundary: `f7b24c9c7364ea64adc4fa1013083343399756d7..a02477be34b3308899820fcc9bd3060ca5bccb70` (aggregate); initial implementation `ae148e46ce143a73046c648d0a19500b37230c60`; round1 fix `a02477be34b3308899820fcc9bd3060ca5bccb70`.
- Verifier: `DraftReview04Verifier2`, independent of the writers and prior verifier.
- Scope: the four light gates only. No later-child review, broad audit/full-review skill, build, formatter, Git mutation, or product/test correction. Only this child04 verify-log is appended.

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1. Authorized scope | PASS | Aggregate `git diff --name-only f7b24c9c7364ea64adc4fa1013083343399756d7 a02477be34b3308899820fcc9bd3060ca5bccb70` lists the eight authorized source/test paths below plus the controller-owned plan/master/ledger and child03 evidence paths. These documentary paths are not child04 product expansion. The round1 diff changes only mailbox-readonly.js and mailReviewReader.test.js. The current worktree source/test paths and child04 plan have no diff from the terminal boundary. mailboxChatStyle.test.js retires the approved obsolete blanket HTML prohibition, fixed-copy case and stale title assertion without re-pinning replacement wording or changing unrelated CSS/permission assertions. |
| 2. Explicit I-16–I-18 / S-1 / S-2 and inherited API authority | PASS | Read the complete reader module, changed host/guard/logout context, behavioral tests, inherited01 I-1–I-7 and API matrix, actual MailReadOnlyPolicy handler dispatch, MailReviewDraftController and relevant service authority. Exact method/path/body pairs reach existing handlers, retaining Session/grant/owner/state/version and canonical-UUID authority. Reader remains independent of admin composition/send/attachment/translation APIs; name-only identity and text-only mail history remain. Persistent single editor, canonical save baseline/latest-version submit, dirty navigation/refresh/logout flush, revision-aware late saves, server state matrices, request ownership/sequences, revoke clearing and unmount abort/listener cleanup were inspected and covered by the fresh suite. F-01/F-02 independently close as detailed below. |
| 3. Every planned syntax/node command fresh | PASS | All three exact planned commands ran freshly in the target worktree after implementation inspection, in one ordered `&&` chain: C1 exit0, C2 exit0, C3 exit0; 131 tests / 22 suites / 131 pass / 0 fail / 0 cancelled / 0 skipped / 0 todo, duration_ms 984.284208. These are this verifier's receipts, not the executor's historical runs. |
| 4. Downstream module / host / cache / style contracts | PASS | MailboxReadOnly.mount/unmount and handle.refresh remain; flushDrafts/hasPendingDrafts are reader-owned and refresh/logout consumers were traced in app.js. Existing mailboxList/panel/title/mobile-menu hooks remain. The shared MailboxMeeting.sanitizeDraftHtml export and production script order precede reader/app loading; absent sanitizer yields text-only/read-only content. Existing versioned assets share 20261029-review-reader with no added script/link. Only the literal S-1 CSS is appended; original global/mc styles are unchanged in the aggregate diff, and the fresh exact-CSS/class/layout/resource/host assertions pass. No later-child interfaces were implemented or reviewed. |

Authorized source/test paths inspected against the boundary:

1. `src/main/resources/static/mailbox-readonly.js`
2. `src/main/resources/static/app.js`
3. `src/main/resources/static/styles.css`
4. `src/main/resources/static/index.html`
5. `src/test/js/mailboxReadOnly.test.js`
6. `src/test/js/authFlow.test.js`
7. `src/test/js/mailReviewReader.test.js`
8. `src/test/js/mailboxChatStyle.test.js`

### Prior finding closure — stable IDs retained

- **F-01 — CLOSED.** Initial detail loading now captures requestedDraftId before issuing GET. Reload chooses the loaded draft id or that requested id, checks selected/denied/destroyed context, obtains explicit discard confirmation, resets state and retries the same detail path. resetReview clears the recovery identity, including on revoke. Fresh tests exercise initial network failure with no loaded draft, cancelled confirmation issuing no request, confirmed retry of draft17, restored editable detail, preservation of local conflict input until confirmation, and403 clearing/no recovery retry. No null draft dereference remains on this recovery branch. Inspected round1 historical real-browser receipt separately; not represented as a verifier HTTP exercise.
- **F-02 — CLOSED.** The no-randomUUID branch now emits lowercase canonical v4-form UUIDs, matching the unchanged backend UUID.fromString plus exact-roundtrip check. createRequest still survives ambiguous creation failure/retry and resets after successful creation/context reset. Fresh behavioral cases execute both absent-crypto and present-crypto-without-randomUUID branches, enforce UUID structure, retry stability, one persisted logical draft after a lost response, and a different identity for a subsequent new draft. The fix-log also records actual authenticated real-backend acceptance of fallback UUID d5bf8463-a4ad-4783-a8c2-1727fd66162a; this is historical writer evidence, not this verifier's backend run.

### Fresh command receipts

Working directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`.

```sh
node --check src/main/resources/static/mailbox-readonly.js
node --check src/main/resources/static/app.js
node --test src/test/js/mailReviewReader.test.js src/test/js/mailboxReadOnly.test.js src/test/js/authFlow.test.js src/test/js/mailboxChatStyle.test.js
```

- C1: exit0, no syntax diagnostics.
- C2: exit0, no syntax diagnostics.
- C3: exit0; tests131, suites22, pass131, fail0, cancelled0, skipped0, todo0; duration_ms984.284208. Includes all four added round1 recovery/UUID behavioral cases and the inherited reader/auth/style regressions.
- No build/JVM/full-suite/browser execution was performed by this verifier.

### Required actual-browser evidence — present, historical only

Read execution.md and the complete round1 fix-log runtime receipts. They identify actual production static assets served by authenticated Spring/JDBC/MySQL backend sessions, not mock API or localStorage prototypes. Original execution records native contenteditable bold/list, real clipboard HTML paste, sanitization and caret preservation; distinct drafts, saves/submit/withdraw/return/comments/approval locks/deleted history; offline flush failure, stale replies, conflict and real grant revocation. It records final current-source page reload and desktop1440/mobile390 measurements. Round1 additionally records authenticated real-backend fallback UUID creation and initial-detail network-failure/reload recovery, with fixture/DB receipt and teardown.

Opened and inspected both retained final-source screenshot files; they exist and show the actual restricted reader surface, rich draft and literal-text historical mail at the reported desktop/mobile surfaces:

- Desktop1440: `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15b966959a6e7031.webp`
- Mobile390: `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-15b96695bc6e7032.webp`

These are historical executor browser receipts, not fresh verifier native-editor/backend observations. Required evidence exists; no missing-evidence PAUSE is warranted. Machine evidence does not establish human acceptance; human acceptance has not been performed or declared by this verifier.

### AUTO_FIX

N/A. F-01/F-02 are closed; no new uniquely plan-determined in-scope correction is required. No corrections applied.

### RECORD_ONLY

N/A. No outside-scope issue was pursued.

### Required Action

- COMPLETE_CHILD

The four light gates pass for the stated aggregate boundary. The controller may complete child04's machine-verification checkpoint; this verdict does not approve later-child work or claim human acceptance.
