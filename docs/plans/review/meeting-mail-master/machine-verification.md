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

## Epoch 2 — 2026-09-17T17:19:38+08:00

- Master plan: `docs/plans/2026-09-16/meeting-mail-master.md` sha256 `feb4f2cb7e68ef1e5c728c49d4386f800be4a83021047f7607da8e47cf3a3a72`
- Governing master identity: worktree sha256 `feb4f2cb7e68ef1e5c728c49d4386f800be4a83021047f7607da8e47cf3a3a72`; recorded identity `commit 59e909070529b4b1e8ae62e61d03e67855f479ba`; state `CONSISTENT`.
- Boundary: `24f5c8205a304d3682e09e02458960bc2caa0463..06dfb878f68e909540e9ef7c4ea63e519beded6e`
- Reviewer: `/root/aggregate_reviewer_epoch2`
- Result: PASS
- Convergence: PROGRESSING
- Repair artifact/result: `docs/plans/fix/meeting-mail-master/repair.md` SHA256 `38bf452eefd30f7ac09925bd29e296167a6e2dfa8150a7e74c464e2fe963bfe2`; V-1 resolved in `06dfb878f68e909540e9ef7c4ea63e519beded6e` under durable execution evidence.

## Verification Result: PASS

Implementation boundary: `24f5c820..06dfb878`
Manual acceptance: PENDING

### Commands

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/*.test.js` | PASS | exit 0; 944 pass / 0 fail |
| `mvn test -Dtest=OutboundAttachmentServiceTest` | PASS | exit 0; 28/0/0/0 |
| MySQL repair group | PASS | exit 0; 92/0/0/0 |
| `DOCKER_API_VERSION=1.44 mvn test` | PASS | exit 0; 3452/0/0/13 |
| Docker Flyway group | Observation | exit 1; 23/0/1/0; pre-base V124 FK failure |
| Master MySQL group | PASS | exit 0; 105/0/0/0 |
| `git diff --check 24f5c820..06dfb878` | Observation | Seeded docs-only trailing blank lines; no product whitespace issue |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| C-1 identity/boundary | PASS | Required identities match; implementation code head `06dfb878`; docs evidence HEAD excluded. |
| I-1 sent/calendar atomicity | PASS | Fresh MySQL groups pass; `ManualReplySendAttemptService` transaction/send paths remain covered by 92- and 105-test groups. |
| I-2 calendar UTC/state/versioning | PASS | Fresh 105-test MySQL group; calendar service/controller test coverage passes. |
| I-3 outbound identity/snapshot | PASS | `OutboundAttachmentModels.kt:165-180` accepts only SQL NULL as absent and rejects any non-null blank with 409; focused regression 28/0/0. |
| I-4 UI owner/time/cache | PASS, manual pending | Fresh JS 944/944 and full Maven pass; real browser/viewport/restart acceptance remains manual. |
| I-5 bounded scope/migrations | PASS | Repair diff has only the two authorized files; Flyway reaches V127 before its unrelated V124 test assertion fails. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | Strict parser rejects `""` and whitespace at `OutboundAttachmentModels.kt:169-170`; regression cases at `OutboundAttachmentServiceTest.kt:484-508` assert 409 and presentation-safe null. |

### Findings

- P1: N/A.
- P2: N/A.
- Observation: Flyway V124 remains 23/0/1 because of the pre-base `fk_eap_contact` FK failure; the run migrates through V127.
- Observation: working tree had only the controller's review-ledger documentation modification; this review did not modify product or test content.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Blank non-null snapshot | I-3/I-5 | RESOLVED as V-1 | Strict parse rejects blank non-null values; presentation parse remains null. |
| V124 Flyway FK | I-5/migration gate | Unrelated pre-existing observation | 23/0/1; predates master base. |
| Seeded trailing blank lines | I-5 | Docs-only observation | No product whitespace issue. |
| 760px nav / inert UI fragment | I-4 | Manual or non-mandatory observation | No confirmed mandatory violation. |
| Matrix-depth / nullable-default notes | I-1/I-3 | Non-blocking evidence-depth observations | Fresh aggregate evidence did not prove a violation. |
| Same-expert in-flight retarget | I-4 | Non-blocking | Transition unspecified by plan. |
| Child-08 guard/matcher regressions | I-5 | RESOLVED | Fresh full Maven is green. |

No product code was modified by the reviewer.
