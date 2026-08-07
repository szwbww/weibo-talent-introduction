# Aggregate Machine Verification — mail-reliability

## Epoch 1 — 2026-08-07 CST

- Master plan: `docs/plans/2026-08-06/00-main-plan-mail-reliability.md` (governing worktree sha256 `5b8ca123301a2b9819470392bef3044cd33fbe1dcebe2ebb002dcbd628344e7d`; recorded identity `commit 9bbb046`)
- Invoked master sha256: `e12dc8db681f95d08a253c6cadc2a0497de2b0061082e2f1e28b3807dfbb1201`
- Master identity state: `AMENDMENT_RECORDED`
- Amendment A1: `92a678b..9bbb046`; master rule `Interaction point 3`; reason: ownership list omitted `src/test/js/mailboxInboundTags.test.js`, whose `refreshExpertTagsFromEs` stub must follow P1 task 2.2 object contract; approval: `HUMAN:retroactively confirmed 2026-08-07 — 事后追认，非当时批准；当时未持久化批准记录`.
- Boundary: `d911bd6..ef7e471`
- Reviewer: `/root/aggregate_review_v2`
- Result: `FAIL`
- Convergence: `INITIAL`
- Repair artifact/result: `docs/plans/fix/00-main-plan-mail-reliability/repair.md` — `DRAFT_READY`
- Product modification by reviewer: none

### Fresh Command Evidence

All 17 required/deduplicated commands exited 0.

| Coverage | Result |
|---|---|
| Full `mvn test` | Kotlin 2187 tests, 0 failures, 0 errors, 4 skipped; JS 466 pass, 0 fail |
| Full `mvn clean package` | Kotlin 2187 tests, 0 failures, 0 errors, 4 skipped; JS 466 pass, 0 fail |
| P4 `SmtpMailDeliveryServiceTest` | 17 tests, 0 failures, 0 errors |
| P1 `ExpertIndexControllerTest` | 18 tests, 0 failures, 0 errors |
| P1 JS targeted suites | `expertProfileAbsence`: 7 pass; `expertTagBatchFix`: 33 pass; `node --check`: pass |
| P3 targeted suites | factory 7/0/0; invitation 1/0/0; auto-reply 40/0/0; schedule 5/0/0; combined suite pass |
| P2 targeted suites | normalizer 13/0/0; unmatched 10/0/0; bounce 4/0/0; combined 27/0/0 |
| `git diff --check` | pass |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 ownership | PASS | Cumulative product/test delta matches child ownership. A1 file is only the required object-stub change. |
| A1 retroactive authorization | PASS | `mailboxInboundTags.test.js:84` returns `{ found: true, tags: [...] }`; follows P1 task 2.2. Human gate must name governing identity. |
| M-2 read/write separation | PASS | `OutboundMessageIdFactory` is write-only; `MessageIdNormalizer` has no write-path use or shared format constants. |
| M-3 migrations | PASS | No added migration. |
| M-4 method-level overlap | PASS | `AutoMailReplyService` changes only two P3 `ComposedMail` call sites; `mailTemplateVariables()` unchanged. |
| M-5 knowledge writeback | PASS | Both corrections retained; knowledge entries cross-link. |
| M-6 non-goals | PASS | No Gmail-button or post-relay stored-ID claim used as a pass criterion. |
| P4 J-7 | PASS | `SmtpMailDeliveryService.kt:54` exactly `List-Unsubscribe=One-Click`; equality test passes. Deferred P4 phases remain N/A under master step ⑤. |
| P3 I-1…I-5 | PASS | Four factory call sites use account domain/UUID; protected paths unchanged; tests pass. |
| P2 I-1…I-5 | PASS | Bounded uppercase-hex prefix removal; exact candidates; read-only use; ordering/fallback preserved. |
| P1 I-1…I-4, S-1/S-2 | PASS | Backend `found` contract, no-profile DOM, compatibility, and mutation guard match. |
| P1 I-5 / observable outcomes | FAIL | V-1. |
| J-1, J-2 | PENDING | Real Gmail/ES workflows require human acceptance. |
| J-3 | PASS | Fresh full regression passed. |

### Findings

| ID | State | Severity | Evidence |
|---|---|---|---|
| V-1 | NEW | P1 | `app.js:6596`, `6963`, `8780`, `9345` await a rejecting tag fetch before completing detail rendering. `showUnmatchedDetail()` awaits at `:9345` before `panel.hidden` / `innerHTML` at `:9354`; mailbox read-only, expert, and contact paths have the same uncaught critical await. This violates P1 outcomes 1–4 and I-5. |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| P4 expected-value update | J-7 | PASS | Required by corrected exact literal; equality remains discriminating. |
| A1 object-stub synchronization | P1 task 2.2 / M-1 | PASS | Contract-consistent, individually authorized by A1. |
| P3 test adaptations | P3 asserted behavior | PASS | Adaptations retain required behavior. |
| P2 baseline-count and document-date notes | N/A | NON-BLOCKING | Fresh aggregate evidence governs. |

### Repair Planning Result

`repair-p` produced `docs/plans/fix/00-main-plan-mail-reliability/repair.md` for V-1 only. Authorized files: `src/main/resources/static/app.js` and `src/test/js/expertProfileAbsence.test.js`. The plan preserves rejection semantics in `fetchExpertTagsFromEs()`, reports locally, and renders the existing S-1 fallback in all four paths.
