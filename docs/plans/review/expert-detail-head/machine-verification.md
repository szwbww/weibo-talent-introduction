# Aggregate Machine Verification — expert-detail-head

## Epoch 1 — 2026-08-15T00:21:46+0800

- Master plan: `docs/plans/2026-08-14/expert-detail-head-main.md` (sha256 `2632e7f628b6ad8e21ee6af36b9411821590bfeb8a0017230b50abdc1895975e`)
- Governing master identity: sha256 `2632e7f628b6ad8e21ee6af36b9411821590bfeb8a0017230b50abdc1895975e`; recorded commit `90498efb768f74a2371e895d984bde1ac4743c49`
- Master identity state: `CONSISTENT`; governing amendment: N/A
- Boundary: `90498efb768f74a2371e895d984bde1ac4743c49..7b914c44e6410aa8c49c51d3bd25e8eb1f893322`
- Reviewer: `/root/aggregate_reviewer_retry`
- Result: `FAIL`
- Convergence: `INITIAL`
- Repair artifact/result: `docs/plans/fix/expert-detail-head-main/repair.md` — `DRAFT_READY`

## Verification Result: FAIL

Plan: `docs/plans/2026-08-14/expert-detail-head-main.md`
Implementation boundary: `90498efb768f74a2371e895d984bde1ac4743c49..7b914c44e6410aa8c49c51d3bd25e8eb1f893322`
Convergence: `INITIAL`
Manual acceptance: `PENDING`

Identity: master SHA-256 `2632e7f628b6ad8e21ee6af36b9411821590bfeb8a0017230b50abdc1895975e`; recorded/invoked master commit `90498efb768f74a2371e895d984bde1ac4743c49`; `CONSISTENT`; governing amendment N/A. P1/P2 SHA-256 identities match the fast-p ledger and A1/A2 are approved. The target worktree was uniquely selected through `DISCOVERED_FROM_GIT_WORKTREES`. Evidence HEAD: `9576699278308c061525cbdf262554637ac4b71d`.

### Commands

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/expertProfileAbsence.test.js src/test/js/senderBindingDisplay.test.js src/test/js/expertMailPreviewTab.test.js src/test/js/composeTemplatePreview.test.js src/test/js/contactHeadLayout.test.js` | PASS | exit 0; 50 pass, 0 fail |
| `node --check src/main/resources/static/app.js` | PASS | exit 0; no output |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 537 pass, 0 fail |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailComposeTemplateServiceTest` | PASS | exit 0; 40 tests, 0 failures/errors |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; 2,421 tests, 0 failures/errors, 4 skipped; bound Node suite 537/0 |
| `git diff --check 90498efb768f74a2371e895d984bde1ac4743c49..7b914c44e6410aa8c49c51d3bd25e8eb1f893322` | PASS | exit 0; no output |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| Outcome: bound-account preview | PASS | `MailComposeTemplateService.kt:219-237,306-313`; `app.js:8112-8138`; P1 tests |
| Outcome: account pill replaces metadata card | PASS | `app.js:7001-7048`; removed-card diff; layout test |
| Outcome: main row + More fold | PASS | `app.js:7003-7047,8564-8570` |
| Outcome: inline tags / no-profile pill | PASS | `app.js:3965-4015,4100-4105`; 15 profile tests |
| M-A / M-1: send uses saved DB binding | PASS | `app.js:8590-8603`: `senderAccountCode: null` |
| M-B / M-3: mailbox tag editor unchanged | PASS | default renderer diff unchanged; profile strict-equality tests pass |
| M-C: editor preview / GET endpoint unchanged | PASS | no controller change; `renderServerComposeTemplatePreview` outside diff; compose tests pass |
| M-D: four sub-tabs unchanged | PASS | mail/template counts 3/3; panel div count 2; tests pass |
| M-2: preview/send account source alignment | PASS | explicit → bound → null resolution at Kotlin `310-313` |
| M-4: required JS gate used | PASS | fresh Node gates above |
| Explicit non-goals / scope | PASS | combined diff limited to approved P1/P2 files and evidence docs; no controller/index/batch/reply-snippet changes; `updateSaveButtonState` unchanged |

### Combined Child Contract Matrix

| Contract | Verdict | Evidence |
|---|---|---|
| P1 I-1..I-7 | PASS | Kotlin `219-237,306-313`; preview payload `8116-8131`; targeted Maven 40/0 |
| P2 I-1 | PASS | `app.js:8590-8603`; layout test |
| P2 I-2..I-4 | PASS | `app.js:7002-7061,8564-8570,8843-8853,11227-11235` |
| P2 I-5..I-8 | PASS | `app.js:3965-4015,4100-4105`; CSS `9418-9489`; tests |
| P2 I-9 | FAIL | `app.js:7007,7009,7014` use raw truthiness/raw text; whitespace-only code is treated as bound |
| P2 I-10 | PASS | counts 3/3 and 2 panel divs; tests |
| P2 S-1..S-9 | PASS | CSS append `9276-9489`, sanctioned disabled rule `9406-9410`, DOM tests |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | No prior aggregate review; mandatory P2 I-9 violation. |

### Findings

#### P1

- **V-1 — whitespace-only bound account renders as bound.** P2 I-9 requires empty or whitespace-only `contact.boundSenderAccountCode` values to render the gray `.sender-binding-dot.is-unbound` and `未绑定`. `loadContactDetail` uses `contact.boundSenderAccountCode ?` and `||`; `"   "` is truthy. Smallest implicated scope: header normalization plus one layout regression test.

#### P2

- N/A

#### Observations

- N/A

### Evidence Boundaries

- None.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| P1 O-1: stale `13→16` narrative | M-4 / A1 test-count criterion | Not a defect | A1 approved the actual `10→13` criterion; current plan and fresh test result conform. |
| P1 O-2: `getEnabledAccount` text | M-2 / P1 I-5 | Not a defect | Kotlin `:303` is a comment; runtime calls `getAccount` at `:313`. |
| P2 O-1: existing metadata `style=` | P2 action-bar no-inline-style interpretation | Not a defect | base/head metadata style-line hash identical; the new action-bar adds none. |
| P2 O-2: conditional disabled-button rule | P2 S-4 | Not a defect | base has no `.button[disabled]`; `styles.css:9406-9410` is the explicitly sanctioned addition. |

## Repair Planning Result: DRAFT_READY

Baseline plan: `docs/plans/2026-08-14/expert-detail-head-main.md`
Verification result: `FAIL / INITIAL`
Repair artifact: `docs/plans/fix/expert-detail-head-main/repair.md`

### Included Findings

- V-1 only.

### Excluded Findings

- All four `RECORD_ONLY` items; no repair authority.

### Required Human Decision

- Approve the repair plan with `$execute-p docs/plans/fix/expert-detail-head-main/repair.md`.

No product code was modified.

## Epoch 2 — 2026-08-15 (post V-1 repair)

- Master plan: `docs/plans/2026-08-14/expert-detail-head-main.md` (sha256 `2632e7f628b6ad8e21ee6af36b9411821590bfeb8a0017230b50abdc1895975e`)
- Governing master identity: sha256 `2632e7f628b6ad8e21ee6af36b9411821590bfeb8a0017230b50abdc1895975e`; recorded commit `90498efb768f74a2371e895d984bde1ac4743c49`; identity state `CONSISTENT`; governing amendment N/A (A1/A2 approved in fast-p ledger, both at commit `95a21a1`)
- Boundary: `90498efb768f74a2371e895d984bde1ac4743c49..82af050103285614a177d2ab4822be6f43861585` (cumulative, includes repair delta `7b914c44e6410aa8c49c51d3bd25e8eb1f893322..82af050103285614a177d2ab4822be6f43861585`)
- Final code head: `82af050103285614a177d2ab4822be6f43861585`; evidence head: `b8bf5c1e8d09ccf68765dd8439ca515db6623956`
- Worktree resolution: `DISCOVERED_FROM_GIT_WORKTREES` — SELECTED; fast-p ledger/handoff `READY_FOR_HUMAN_REVIEW`, 2 terminal children, valid ancestry
- Reviewer: AggregateReviewerE2 (fresh; differs from P1Implementer/P1Verifier/P2Implementer/P2Verifier/Main)
- Result: `PASS`; Convergence: `PROGRESSING`; Manual acceptance: `PENDING`

## Verification Result: PASS

Plan: `docs/plans/2026-08-14/expert-detail-head-main.md`
Implementation boundary: `90498efb768f74a2371e895d984bde1ac4743c49..82af050103285614a177d2ab4822be6f43861585`
Convergence: `PROGRESSING`
Manual acceptance: `PENDING`

### Identity and Boundary Freeze

- Master plan sha256 `2632e7f6...` matches ledger; fast-p ledger sha256 `12aebc2e...` matches; handoff sha256 `519aa754...` matches; repair plan sha256 `71598c84...` matches; child plans at amended commit `95a21a1` (A1/A2) — all re-verified from the worktree.
- **Repair delta confinement (independent confirmation):** `git diff --name-only 7b914c4..82af050` product/test files = exactly `src/main/resources/static/app.js`, `src/test/js/contactHeadLayout.test.js` (the 2 Authorized Files). Commit `82af050` (`fix(fast-p): render whitespace sender binding as unbound`) contains exactly those 2 files (9-line app.js change, 38-line test change); commit `b8bf5c1` contains only `docs/plans/review/expert-detail-head/repair-execution.md`. **No extra product/test file — no blocker.**
- Cumulative scope (base..HEAD): 16 docs + 7 product/test files, exactly the union of the P1 (MailComposeTemplateService.kt, app.js, expertMailPreviewTab.test.js, MailComposeTemplateServiceTest.kt) and P2 (app.js, styles.css, expertProfileAbsence.test.js, contactHeadLayout.test.js) authorized lists, app.js deduplicated. `src/test/js/senderBindingDisplay.test.js`, `index.html`, and all controllers are zero-diff.

### Commands (all run freshly this invocation; node v25.7.0; JDK 11.0.15 zulu-11)

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/expertProfileAbsence.test.js src/test/js/senderBindingDisplay.test.js src/test/js/expertMailPreviewTab.test.js src/test/js/composeTemplatePreview.test.js src/test/js/contactHeadLayout.test.js` | PASS | exit 0; `# tests 51 / # pass 51 / # fail 0` (15+6+13+7+10; the 10 layout tests include the new whitespace regression case) |
| `node --check src/main/resources/static/app.js` | PASS | exit 0; no output |
| `node --test src/test/js/*.test.js` | PASS | exit 0; `# tests 538 / # pass 538 / # fail 0` |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailComposeTemplateServiceTest` | PASS | exit 0; surefire `Tests run: 40, Failures: 0, Errors: 0, Skipped: 0` |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; `Tests run: 2421, Failures: 0, Errors: 0, Skipped: 4`; BUILD SUCCESS; bound Node suite 538/0 present in build output |
| `git diff --check 90498efb768f74a2371e895d984bde1ac4743c49..HEAD` | PASS | exit 0; no output |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| Outcome 1: bound-account preview (real sender vars + no fallback badges + real recipient) | PASS | `MailComposeTemplateService.kt:219-226,306-315`; `app.js:8117-8130`; P1 tests at `MailComposeTemplateServiceTest.kt:944,1004,1084`; JS I-2/I-3 cases at `expertMailPreviewTab.test.js:268,289,310`; fresh Maven 40/0 and gate 51/0 |
| Outcome 2: account pill in action bar, metadata card gone | PASS | `app.js:7005-7047` (S-8 skeleton); Sender Binding Card removed (`sender-binding-editor` 0 hits in app.js and styles.css); pill ids/labels asserted in `contactHeadLayout.test.js` |
| Outcome 3: main row + More fold | PASS | `app.js:7005-7047,8565-8571`; `state.contactHeadExpanded` at `app.js:26`; toggle branch before `select-expert`; I-4 test green |
| Outcome 4: inline tags / no-profile pill | PASS | `app.js:3965-4015` inline branch; name-row calls at `app.js:7096` (loadContactDetail) and `app.js:6685` (showExpertDetail); S-7 tests green |
| M-A / M-1: send uses saved DB binding; frontend never passes unsaved code | PASS | `app.js:8591-8604` `send-manual-mail` body keeps `senderAccountCode: null`; preview keeps `senderAccountCode: null` (`app.js:8129`); backend reads `contact.boundSenderAccountCode` (`MailComposeTemplateService.kt:309-311`) |
| M-B / M-3: `renderExpertTagEditor` default output byte-identical; mailbox views unchanged | PASS | signature `app.js:3965` with `layout = "section"` default; section branch unchanged; `renderMailboxExpertTagEditor` (`app.js:4497`) and call sites `app.js:9088,9657` zero-diff; `expertProfileAbsence.test.js` 15/15 (S1/S2 strictEqual + negative layout assertion at `:439`) |
| M-C: `GET /api/compose-templates/{id}/preview` + `renderServerComposeTemplatePreview` unchanged | PASS | `MailComposeTemplateController.kt` zero-diff; app.js diff has zero hits for `renderServerComposeTemplatePreview`/`collectComposeTemplatePreviewContext`; compose tests green |
| M-D: four sub-tabs keys/order/`data-panel` counts | PASS | `data-panel="mail-preview"` = 3, `data-panel="template"` = 3, `class="detail-tab-panel" data-panel="mail-preview"` = 2; `renderDetailSubTabs`/`activateDetailSubTab` zero-diff; `expertMailPreviewTab.test.js:322-329` green |
| M-2: preview/send account source alignment | PASS | `MailComposeTemplateService.kt:306-315` explicit → `contact.boundSenderAccountCode` (trim, non-blank, `contact?.` guards null) → null — same binding source as send `resolveForSend`; P1 test 2 asserts `verify(never).getAccount("LiLei")` for explicit-priority |
| M-4: authoritative JS gate is `node --test <file>` | PASS | fresh gates above (51/0 and 538/0) run via `node --test`; `verify.sh` untouched |
| Explicit non-goals / scope (direction D, auto-rebind, `reply_snippet.updated_at`, batch/mailbox/drawer layout, `updateSaveButtonState` refactor) | PASS | `index.html` zero-diff; `updateSaveButtonState` body (`app.js:8856-8878`) zero-diff (diff shows only new-function insertion before it); no controller/DTO/batch changes; combined diff limited to the 7 authorized product/test files |
| Order constraint (P1 before P2) | PASS | ancestry `1111807` (P1) precedes `7b914c4` (P2) on branch; P2 A-9 depends on P1 preview behavior, verified intact |

### Child Contract Matrix (aggregate)

| ID | Verdict | Evidence |
|---|---|---|
| P1 I-1: preview resolution priority explicit → bound → null; never ② when contact null | PASS | `MailComposeTemplateService.kt:306-315`; call site `:226-227` passes contact; test `MailComposeTemplateServiceTest.kt:944` |
| P1 I-2: single `api()` call per render, URL/method fixed, in-memory lookup only | PASS | `app.js:8117-8137` one call at `:8134-8137`; `previewContact`/`previewContactId` are pure in-memory (`:8117-8118`); test `expertMailPreviewTab.test.js:310` asserts `calls.length === 1` |
| P1 I-3: `contactId` only for strictly-equal orcid + non-null contactId, else null | PASS | `app.js:8117-8118` (`find` + `?.contactId ?? null`); tests `:268` (42) and `:289` (null) |
| P1 I-4: contactId set → backend ignores orcidId/expertEmail for recipient; expertEmail stays null | PASS | `MailComposeTemplateService.kt:279-284` early return on `findById`; `app.js:8128` `expertEmail: null`; `orcidId` still passed |
| P1 I-5: preview uses `getAccount` (not `getEnabledAccount`) + `runCatching` | PASS | `MailComposeTemplateService.kt:313` `mailSenderAccountService.getAccount(code)` inside `runCatching{}.getOrNull()` |
| P1 I-6: no new constructor dependency | PASS | service diff has only hunks `@@ -219,7` and `@@ -298,11` (call site + method body); `MailComposeTemplateServiceTest.kt` diff insertion-only (constructor 9-arg call untouched) |
| P1 I-7: variable replacement only via `renderPreview`; `resolveBlocks(renderVariables=false)` unchanged | PASS | `MailComposeTemplateService.kt:219,236,249`; no `renderText(` added; diff hunk list confirms no other change |
| P2 I-1: send body `senderAccountCode: null`, branch never reads `#senderBindingSelect` | PASS | `app.js:8602`; zero diff on that line; layout test 4 green |
| P2 I-2: dirty gate — one function sets note.hidden / sendBtn.disabled / pill data-dirty | PASS | `app.js:8844-8855`; init call `:7064`; change-delegate branch `app.js:11234-11236` (existing 3-id branch untouched `:11231-11233`); layout test 2 both states |
| P2 I-3: `data-original` sole dirty source; no selectedIndex | PASS | `app.js:7013` (`data-original="${boundSenderAccountCode}"`); `selectedIndex` 0 hits in app.js; layout test 3 (empty original + selected account → dirty) |
| P2 I-4: fold state in `state`, survives expert switch | PASS | `app.js:26`; render reads `state.contactHeadExpanded === true` (`:7031,7033`); toggle `:8565-8571`; `contactHeadExpanded` 6 hits; layout test 5 |
| P2 I-5: default renderer output byte-identical | PASS | `expertProfileAbsence.test.js` 15/15 including negative assertion `:439` |
| P2 I-6: inline root keeps class/id/data-orcid/data-level/data-layout | PASS | `app.js:3968-3972,3989-3992`; actions locate via `element.closest(".expert-tag-editor")` (`:8635,8670`); test 3 of inline suite |
| P2 I-7: re-render preserves layout | PASS | `app.js:4100-4106` reads `editor.dataset.layout`; layout test 6 |
| P2 I-8: loading mask must not inflate inline row | PASS | `styles.css:9487-9488` `.expert-tag-editor.is-inline.tag-editor-loading { min-height: 0; }` — exactly 1 hit |
| P2 I-9: unbound/whitespace-only renders gray dot + 未绑定 | PASS (V-1 repaired) | `app.js:7001` `(contact.boundSenderAccountCode || "").trim()`; dot `:7009`, label `:7010`, `data-original` `:7013`, selected comparison `:7058`; regression test `contactHeadLayout.test.js:127-162` (evaluates actual template + derivation with `"   "`, asserts `is-unbound`, `>未绑定<`, `data-original=""`, no whitespace leak, no bound dot); layout gate 10/10 |
| P2 I-10: sub-tab panel counts unchanged | PASS | counts 3/3, panel divs 2; `expertMailPreviewTab.test.js:322-329` green |
| P2 S-1..S-9: style contract | PASS | CSS appended under `/* === 专家详情头部 C 布局 === */` (`styles.css:9276-9489`), 2 hunks total (dead-rule deletion + append); S-1..S-6 blocks verbatim per plan; S-4 conditional `.contact-head-actions .button[disabled]` at `styles.css:9406-9410` with in-file comment (styles.css has no `.button[disabled]` rule — plan-sanctioned); existing blocks untouched; `contact-head-mail-row` 0 hits in app.js; `class="expert-profile-header"` = 2; `style="` hit set 182 = 182 = 182 (base 90498ef / pre-repair 7b914c4 / head); DOM tests 1/7/8/9 green |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 (whitespace-only binding rendered as bound) | RESOLVED | `app.js:7001,7009-7013,7058` now use one trimmed-or-empty local; regression case `contactHeadLayout.test.js:127-162` passes; full gate 51/0; no backend value rewritten, no send-branch/CSS/selectedIndex changes (repair delta = 2 Authorized Files only) |

No new findings. Preserved ID V-1; no V-2+ required.

### Observations

- `renderExpertTagEditor` inline branch emits `escapeHtml(editorId)/escapeHtml(orcidId)/escapeHtml(level)` (`app.js:3969-3971,3989-3991`), while plan S-7 skeleton shows raw interpolation — this matches the pre-existing section-branch convention in the same function, is output-identical for realistic id/ORCID/level values, and satisfies the plan's T11 assertions; no behavioral divergence. Noted for transparency; accepted in epoch 1 as well.

### Evidence Boundaries

- None. All six required commands re-run freshly with exit codes/counts; all mandatory invariants have file:line or command evidence. Manual acceptance (P1 A-1..A-9, P2 A-1..A-15) remains `PENDING` and does not block machine verification.

### Next Action

- PASS → perform pending human acceptance, then finish the branch (merge/release decision remains human).

## Fast-P RECORD_ONLY Re-evaluation (leads)

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| P1 O-1: execution.md stale `13→16` narrative | M-4 / A1 | Not a defect | A1 (commit 95a21a1) approved `10→13`; fresh run: `expertMailPreviewTab.test.js` 13 tests, gate 51/0 — conforms. Narrative-only. |
| P1 O-2: `getEnabledAccount` text at `MailComposeTemplateService.kt:303` | M-2 / P1 I-5 | Not a defect | `:303` is the plan-authored doc comment; functional rule holds at `:313` (`getAccount` + `runCatching`). |
| P2 O-1: plan T11/S-8 `loadContactDetail 源码不含 style=` vs 5 pre-existing metadata-grid inline styles | P2 S-8 no-new-inline-style | Not a defect | Implementer scoped the assertion to the new action-bar region; global `style="` hit set identical base/pre-repair/head (182 = 182 = 182). Plan-consistent. |
| P2 O-2: S-4 conditional `.contact-head-actions .button[disabled]` | P2 S-4 | Not a defect | `styles.css:9406-9410` with S-4 comment; styles.css has no `.button[disabled]` rule, so the plan's conditional sanctioned this exact addition. |

## Repair Planning Result: NO_ACTION (this epoch)

Baseline plan: `docs/plans/2026-08-14/expert-detail-head-main.md`
Verification result: `PASS / PROGRESSING` — review-p routing: PASS → Stop; repair-p would return NO_ACTION (no repairable finding remains). No new repair artifact written; the prior artifact is unchanged.

Prior repair (epoch 1, DRAFT_READY → HUMAN `$execute-p docs/plans/fix/expert-detail-head-main/repair.md` 2026-08-15 → executed by Main) is verified here:

- Repair artifact: `docs/plans/fix/expert-detail-head-main/repair.md` (sha256 `71598c84...`, unchanged)
- Pre/post code SHAs: `7b914c44e6410aa8c49c51d3bd25e8eb1f893322..82af050103285614a177d2ab4822be6f43861585` — independently confirmed to touch ONLY the 2 Authorized Files (product commit `82af050`); docs commit `b8bf5c1` records `repair-execution.md` only
- R-1 completion criteria met: whitespace-only binding renders exactly as unbound and passes the new regression case; existing unbound dirty-gate and normal bound-account behavior green; changed files inside authorized list
- Post-repair evidence mode: `DURABLE_HANDOFF` (repair-execution.md + evidence commit `b8bf5c1`; ledger epoch 2 records approval source, executor, SHAs, and next action READY_FOR_VERIFICATION → now verified PASS)

No product code was modified.
