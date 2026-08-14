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
