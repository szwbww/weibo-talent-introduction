# Aggregate Machine Verification — meeting-mail-master

## Epoch 1 — 2026-09-17T14:49:22+08:00

- Master plan: `docs/plans/2026-09-16/meeting-mail-master.md` sha256 `feb4f2cb7e68ef1e5c728c49d4386f800be4a83021047f7607da8e47cf3a3a72`
- Governing master identity: worktree sha256 `feb4f2cb7e68ef1e5c728c49d4386f800be4a83021047f7607da8e47cf3a3a72`; recorded commit `59e909070529b4b1e8ae62e61d03e67855f479ba`
- Master identity state: CONSISTENT; recorded child-plan amendments A1/A2/A3, including their master rules and HUMAN approvals, reviewed from `docs/plans/fast/meeting-mail-master/ledger.md`.
- Boundary: `24f5c8205a304d3682e09e02458960bc2caa0463..ae5d947b7257bf714e1d70e93dafbaf1894cbff6`
- Reviewer: `/root/aggregate_reviewer`
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: `docs/plans/fix/meeting-mail-master/repair.md` — DRAFT_READY

## Verification Result: FAIL

Plan: `docs/plans/2026-09-16/meeting-mail-master.md`
Implementation boundary: `24f5c820..ae5d947`
Convergence: INITIAL
Manual acceptance: PENDING

### Commands

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/*.test.js` | PASS | 944/944 |
| JDK11 `mvn test` | PASS | 3452 tests, 0 failures/errors, 13 skipped |
| JDK11 `mvn test -Dtest=OutboundAttachmentServiceTest` | PASS | 28/0/0 |
| JDK11 MySQL aggregate group | PASS | 105/0/0 |
| JDK11 Docker Flyway group | FAIL | 23/0/1: pre-existing V124 `fk_eap_contact` FK failure |
| `git diff --check 24f5c820..ae5d947` | Observation | Only seeded plan/evidence trailing blank lines |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| C-1 identity/boundary | PASS | Required hashes match; final code head `ae5d947`; evidence-only HEAD excluded |
| I-1 sent/calendar atomicity | PASS | `ManualReplySendAttemptService.kt:339-428`; MySQL group PASS |
| I-2 calendar UTC/state/versioning | PASS | `MeetingCalendarService.kt:32-323`, repository locking SQL; calendar tests PASS |
| I-3 outbound identity/snapshot | FAIL | `OutboundAttachmentModels.kt:165-168` accepts blank non-null snapshots as absent |
| I-4 UI owner/time/cache contracts | PASS, manual pending | JS suite PASS; viewport/browser acceptance remains manual |
| I-5 bounded scope | PASS | 54 implementation paths match child tables plus A1/A2/A3 authority |
| migrations V125–V127 | PASS | Fresh Docker migration reaches v127; residual V124 failure predates base |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | `parseOrThrow(\"\")` and whitespace return null; `OutboundAttachmentServiceTest.kt:467-469` codifies it |

### Findings

#### P1

- V-1 — Child 05 I-1/I-5 require SQL `NULL` as the sole no-attachment representation and rejection of invalid non-null snapshots. `OutboundAttachmentSnapshotCodec.parseOrThrow()` uses `isNullOrBlank()`, silently treating persisted `\"\"`/whitespace as absent.

#### P2

- N/A.

#### Observations

- Fresh Flyway failure is the documented pre-base V124 FK defect; not caused by `24f5c820..ae5d947`.
- RECORD_ONLY trailing blanks, 760px visual item, inert test fragment, test-depth gaps, nullable collaborator defaults, and same-expert retarget behavior are not confirmed master-contract violations.
- The RECORD_ONLY blank-snapshot item is promoted to V-1.

### Evidence Boundaries

- Manual acceptance pending: real SMTP/browser/viewport/restart checks.

### Next Action

- FAIL + INITIAL: repair planning completed below.

## Repair Planning Result: DRAFT_READY

Baseline plan: `docs/plans/2026-09-16/meeting-mail-master.md`
Verification result: FAIL / INITIAL
Repair artifact: `docs/plans/fix/meeting-mail-master/repair.md`

### Included Findings

- V-1.

### Excluded Findings

- Pre-existing Flyway V124 failure; non-mandatory RECORD_ONLY items.

### Required Human Decision

Approve repair plan.

No implementation was performed. No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Blank snapshot parsed as absence | I-3, I-5 | FAIL — V-1 | Strict parser `isNullOrBlank()` and its test accept non-null blanks. |
| V124 Flyway FK error | I-5 / migration gate | Unrelated pre-existing observation | Fresh Docker group reaches V127; failure predates master base. |
| Trailing plan/evidence blank lines | I-5 | Unrelated observation | `git diff --check` reports seeded docs/evidence only. |
| 760px nav overflow; inert dialog assertion | I-4 manual/UI | Manual or non-blocking observation | No confirmed mandatory master-contract violation. |
| I-1 matrix depth; multi-attachment/cross-message case; nullable collaborator defaults | I-1/I-3 evidence depth | Non-blocking observation | Fresh aggregate command and runtime evidence did not prove a violation. |
| Same-expert in-flight upload retarget | I-4 | Non-blocking observation | Plan does not define the transition. |
