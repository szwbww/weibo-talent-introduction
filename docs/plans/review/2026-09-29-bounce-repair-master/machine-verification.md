# Aggregate Machine Verification — bounce-repair-master

## Epoch 1 — 2026-09-29

- Master plan: `docs/plans/2026-09-29/bounce-repair-master.md` (sha256 `ece7a3567cf898708ba3b388df88539393fe6c59a3f6698f41826ac55d24a26b`)
- Governing master identity: worktree sha256 `ece7a3567cf898708ba3b388df88539393fe6c59a3f6698f41826ac55d24a26b`; recorded commit `4dccc7404dad92fc3a1dfe3224e2e6fe03feb331`
- Master identity state: CONSISTENT; governing amendment: N/A. Child-plan amendment A1 was approved as recorded in the review ledger.
- Boundary: `ca55f0e37ca2d61cdcf362d4d64c5658e4dc34b3..46d7e17ddb4b71093a1faca156300335a442ddc4`
- Evidence HEAD: `aa944b4b4e3e1c7da943902d9b123a074966590a` (evidence only; not reviewed as implementation)
- Reviewer: `/root/aggregate_bounce_review_e1`
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: `docs/plans/fix/bounce-repair-master/repair.md` / N/A

## review-p — aggregate/master

### Verification Result: PASS

Plan: `docs/plans/2026-09-29/bounce-repair-master.md`

Implementation boundary: `ca55f0e37ca2d61cdcf362d4d64c5658e4dc34b3..46d7e17ddb4b71093a1faca156300335a442ddc4`

Convergence: INITIAL

Manual acceptance: PENDING

The evidence HEAD `aa944b4b4e3e1c7da943902d9b123a074966590a` is documentation-only and was not reviewed as product implementation.

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true -Dtest=BounceRateMonitorServiceTest,MailSenderAccountControllerMvcTest test` | PASS | exit 0; 23 tests; 0 failures, 0 errors, 0 skipped |
| `node --test src/test/js/senderBindingDisplay.test.js` | PASS | exit 0; 14 pass, 0 fail |
| `node --check src/main/resources/static/app.js` | PASS | exit 0; syntax OK |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 1233 pass, 0 fail, 243 suites |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true -Dtest=RecipientAddressFailureClassifierTest,BounceDetectorTest,BounceCollectionServiceTest,BounceBackfillServiceTest,OperatorStatusReconcileServiceTest,ExpertOperatorStatusServiceTest,OperatorStatusWriteSeamGuardTest,ManualExpertMaterialUploadFlowTest test` | PASS | exit 0; 178 tests; 0 failures, 0 errors, 0 skipped |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true -Dtest=RecipientAddressFailureClassifierTest,ManualInitialOutreachServiceTest,OperatorStatusReconcileServiceTest,BatchSendTaskRuntimeIntegrationTest,MailOpenTrackingPersistenceTest,ExpertOperatorStatusServiceTest,OperatorStatusWriteSeamGuardTest test` | PASS | exit 0; 308 tests; 0 failures, 0 errors, 0 skipped |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; 272 reports; 4444 tests; 0 failures, 0 errors, 13 skipped |

Initial Maven and full-JS invocations could not write `target/` under the sandbox. The same original commands were approved and rerun; the fresh final results above govern this review.

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-O1 | PASS | `BounceRateMonitorService` produces one read-only snapshot; controller and UI expose the account hard-bounce figures. |
| M-O2 | PASS | The shared classifier applies exact DSN/SMTP address-evidence allowlists; permanent delivery failure is not itself an invalid-address conclusion. |
| M-I1 | PASS | `BounceCollectionService` persists `BounceRecord` first; only HARD plus whitelisted evidence reaches the public status writer. |
| M-I2 | PASS | Children 01 → 02 → 03 are ordered; overlapping files were serial; A1 was human-approved. |
| M-I3 | PASS | Reconcile remains read-only; no migration, historical update, or batch/global ES synchronization is in scope. |
| 01-I1 | PASS | `BounceRateMonitorService.kt:36-49` shares one cutoff and performs one hard/sent query pair. |
| 01-I2 | PASS | Low samples yield null rate; high is strictly greater than 5%; 23 fresh tests pass. |
| 01-I3/IP3-IP4 | PASS | Compatibility methods retain behavior; statistics/GET paths do not pause or persist. |
| 01-I4 | PASS | DTO/UI use finite-number and sample guards, with the original badge fallback when stats are unusable. |
| 01-I5/IP1-IP2 | PASS | `index.html` has 11 versioned resources; orphan HARD events and SENT filtering remain represented in tests/queries. |
| 01-S1 | PASS | `styles.css` is outside the cumulative implementation diff. |
| 02-I1 | PASS | `BounceDetector` retains protocol HARD classification; `BounceCollectionService` separates address status evidence. |
| 02-I2 | PASS | `RecipientAddressFailureClassifier` permits only 5.1.1, 5.1.2, 5.1.3, and 5.1.10. |
| 02-I3 | PASS | MIME DSN remains authoritative; enhanced status parsing preserves bounded multi-digit components. |
| 02-I4 | PASS | `OperatorStatusReconcileService` derives status read-only and preserves communication milestones. |
| 02-I5/IP1-IP3 | PASS | Idempotency/attribution checks pass; no migration or database contract change exists. |
| 03-I1 | PASS | Online handling and reconcile use the same persisted `PERMANENT:<5xx>:` summary rule. |
| 03-I2 | PASS | SMTP summary parsing accepts only trusted line-start protocol evidence and rejects truncation/ambiguity. |
| 03-I3 | PASS | `recordFailure` precedes the constrained public `markEmailInvalid` call. |
| 03-I4 | PASS | Retry filtering and the pre-SMTP gate reject prior permanent first failures without resending. |
| 03-I5 | PASS | No historical data write, global synchronization, or reconcile writer was introduced. |

### Amendment Evidence

A1 applies and is consistent: the approved amendment added `src/test/js/providerUndeliveredColumn.test.js` to child 01 after its global `hardBounceCount` guard conflicted with the required account DTO/UI consumer. Approval is recorded as `HUMAN:批准 A1（推荐） (recorded 2026-09-29T20:18:32+08:00)`.

### Cumulative Diff and Runtime Evidence

- `git diff --check ca55f0e37ca2d61cdcf362d4d64c5658e4dc34b3..46d7e17ddb4b71093a1faca156300335a442ddc4`: no output.
- Total diff: 39 files, `+3137/-92`; implementation `src/`: 20 files, `+1559/-92`.
- Not changed: migrations, mail repositories, `MailMonitoringService`, `ManualOutreachTxHelper`, `ExpertOperatorStatusService`, and `styles.css`.
- Runtime-path inspection confirms: statistics are read-only; collection persists failure facts before constrained state change; reconcile does not write; SMTP failure preserves facts and permanent first failure does not resend.

### Finding Lineage

No prior aggregate finding or repair lineage exists.

| Finding | State | Evidence |
|---|---|---|
| N/A | N/A | No confirmed P1 or mandatory P2 finding in epoch 1. |

### Findings

#### P1

- N/A

#### P2

- N/A

#### Observations

- N/A

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Child 02 O-1 — `BounceDetector.kt:269` | I-1/I-2: address-invalid decisions require stored, exact evidence. | Non-blocking | The remaining single-digit heuristic only detects a bounce body; it does not create a stored DSN address decision or call the classifier. |
| Child 03 O-1 — unused `expertIndexWriterService` dependency in `ManualInitialOutreachService` | I-3: use the public status writer and preserve approved scope. | Non-blocking | The plan expressly retained the dependency; removal needs an approved plan amendment and constructor-test changes. |

### Evidence Boundaries

- The 13 full-suite skipped tests are pre-existing opt-in/Docker-gated tests. No production environment was used.
- Manual acceptance remains pending and must use the master plan's isolated-environment procedures.

### Next Action

- Machine PASS: complete the pending human acceptance for this exact boundary.

## Repair Planning Result: NO_ACTION

Baseline plan: `docs/plans/2026-09-29/bounce-repair-master.md`

Verification result: PASS / INITIAL

Repair artifact: N/A

### Included Findings

- N/A

### Excluded Findings

- N/A

### Required Human Decision

- Complete the pending manual acceptance; no repair is authorized or needed.

No implementation was performed. No product code was modified.
