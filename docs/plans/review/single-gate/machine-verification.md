# Aggregate Machine Verification — single-gate

## Epoch 1 — 2026-08-28

- Master plan: `docs/plans/2026-08-28/00-single-gate-master.md` (sha256 `fb2e511d39b90da7a5d81057263b85899b97bdb2a586652e91eea371ed901184`)
- Governing master identity: recorded commit `1f5a916489933fc9b2e8e469037fc912d55edd5d`; worktree sha256 `fb2e511d39b90da7a5d81057263b85899b97bdb2a586652e91eea371ed901184`
- Master identity state: `CONSISTENT`; amendments A1–A5 are recorded, HUMAN-approved, and verified against their named master rules.
- Boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..4636727749202052c6affd2550e5353139fcb4a1`
- Reviewer: `/root/aggregate_reviewer`
- Result: `FAIL`
- Convergence: `INITIAL`
- Repair artifact/result: `docs/plans/fix/00-single-gate-master/repair.md` — `DRAFT_READY`

## Verification Result: FAIL

Plan: `docs/plans/2026-08-28/00-single-gate-master.md` (aggregate/master)

Implementation boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..4636727749202052c6affd2550e5353139fcb4a1`

Manual acceptance: `PENDING`

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; 2,969 Kotlin tests; 0 failures; 0 errors; 5 skipped; embedded JS 755/755 |
| Selected master/child Maven target regression set | PASS | exit 0 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0; 2,969/0/0/5; WAR packaged |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 755 pass; 0 fail; 120 suites |
| `git diff --check` | PASS | exit 0 |
| `git diff --check de228e17cc0134a7c11dea7cbf82054e8d249f99..4636727749202052c6affd2550e5353139fcb4a1` | PASS | exit 0 |
| M-1 grep guards | PASS | no production `expertClassification.sendable`, `classification.sendable`, `expertSendableFilter`, or `ACCEPTED_CLASSIFICATION_VERSIONS` hits |
| M-4 audit | PASS | both `minusDays(30)` calls retained; no boundary diff change |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 | FAIL | V-1: MATERIAL_REMINDER empty expert types incorrectly rejected by the editor |
| M-2 | PASS | INTRODUCTION backend configuration/manual-start validation rejects empty types |
| M-3 | PASS | classification mapping/type rules retained; only authorized gate cleanup |
| M-4 | PASS | two 30-day cutoff paths unchanged |
| 01: I1-1..I1-8, S1-1 | PASS | OpenAlex recovery, stats, controller, and write-path tests pass |
| 02: I2-1..I2-5 | PASS | explicit type filters; no implicit sendable/version gate |
| 03: I3-1 | FAIL | MATERIAL_REMINDER editor cannot save empty types |
| 03: I3-2..I3-6 | PASS | server config/start behavior, selector/filter behavior, fail-closed INTRODUCTION coverage pass |
| 03: S3-1 | FAIL | exact error exists but is incorrectly applied to MATERIAL_REMINDER |
| 04: I4-1..I4-6 | PASS | empty whitelist guard passes; single type-gate behavior verified |
| 05: I5-1..I5-5 | PASS | sendable vocabulary cleanup/mapping preservation guard passes |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | `src/main/resources/static/app.js:15033` unconditionally rejects empty `expertTypes` despite the existing selected-template mail-type resolver |

### Findings

#### P1

- V-1 — An enabled `MATERIAL_REMINDER` template cannot save a configuration with `expertTypes: []`. The backend preserves that behavior, but `saveBatchConfigEditor()` at `src/main/resources/static/app.js:15033` runs the INTRODUCTION-only validation unconditionally. This violates master M-1 and child 03 I3-1.

#### P2

- N/A.

#### Observations

- N/A.

### Amendments

| Amendment | Result |
|---|---|
| A1 / child 03 fixtures | PASS under I3-1/I3-2 regression rule; alignment inside approved scope |
| A2 / child 04 test deletion + line pin | PASS under I4-6/full-regression rule |
| A3 / child 05 ExpertSearchServiceTest deletion | PASS under I5-5 scope gate |
| A4 / child 05 ManualInitialOutreach fixture | PASS under I5-5 scope gate; local triad preserves behavior |
| A5 / child 05 operator-status line pin | PASS under Task 4/full-regression rule |

### Evidence Boundaries

- None.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 helper local rename | No mandatory master contract affected | RECORD_ONLY | behavior-equivalent test-only rename |
| 02 evidence/boundary/deviation bookkeeping | No mandatory master contract affected | RECORD_ONLY | docs-only |
| 03 boundary spans 02 evidence docs | No mandatory master contract affected | RECORD_ONLY | docs-only |
| 04 grep “exactly one” wording | M-1 | RECORD_ONLY | two hits: one child-04 matcher and one pre-existing child-02 inline predicate; M-1 behavior passes |
| 04 docs/bookkeeping | No mandatory master contract affected | RECORD_ONLY | docs-only |
| 04 test-entry migration quantity | M-2 | RECORD_ONLY | M-2 consequence; assertions retained |
| 05 | N/A | N/A | no entry |

### Repair Planning Result: DRAFT_READY

Baseline plan: `docs/plans/2026-08-28/00-single-gate-master.md`

Verification result: `FAIL` / `INITIAL`

Repair artifact: `docs/plans/fix/00-single-gate-master/repair.md`

Included finding: `V-1`.

Required human decision: approve the repair plan before execution.

No product code was modified by the reviewer.

## Epoch 2 — 2026-08-28

- Master plan: `docs/plans/2026-08-28/00-single-gate-master.md` (sha256 `fb2e511d39b90da7a5d81057263b85899b97bdb2a586652e91eea371ed901184`)
- Governing master identity: recorded commit `1f5a916489933fc9b2e8e469037fc912d55edd5d`; worktree sha256 `fb2e511d39b90da7a5d81057263b85899b97bdb2a586652e91eea371ed901184`
- Master identity state: `CONSISTENT`; amendments A1–A5 recorded in the fast-p ledger.
- Boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..b3b7a9b78ed3f85abc0e9fc6a51a0a5a43f5695f`
- Reviewer: `01a048e2-6e40-7461-94e5-06da97888dff`
- Result: `BLOCKED`
- Convergence: `BLOCKED`
- Repair artifact/result: `docs/plans/fix/00-single-gate-master/repair.md` — N/A (verification blocked)

## Verification Result: BLOCKED

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/docs/plans/2026-08-28/00-single-gate-master.md`

Implementation boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..b3b7a9b78ed3f85abc0e9fc6a51a0a5a43f5695f`

Convergence: `BLOCKED`

Manual acceptance: `PENDING`

### Commands

| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/batchExpertTypeFilter.test.js` | PASS | exit 0; 8/8 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 757 pass, 0 fail, 120 suites |
| `git diff --check` | PASS | exit 0 |
| `git diff --check de228e1..b3b7a9b` | PASS | exit 0 |
| M-1 grep guards | PASS | zero forbidden gate/sendable-read hits |
| M-4 audit | PASS | two `minusDays(30)` calls retained; zero boundary changes |
| Master targeted Maven set | BLOCKED | Initial sandbox run exit 1 before tests: existing `target/classes/application.yml` returned `Operation not permitted`. Escalated rerun session `19119` started; polling was interrupted after 23.3 seconds. Final process status unknown; it may still be running. |
| Full `mvn test` | BLOCKED | Not run after interrupted targeted command |
| Full `mvn clean package` | BLOCKED | Not run |
| Child/master targeted Maven checks | BLOCKED | Required fresh results unavailable |

Interrupted command:

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test -Dtest='OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,ExpertDiscoveryControllerTest,ExpertClassificationServiceTest,ExpertSearchServiceTest,InitialOutreachServiceTest,BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,V109ExpertTypesMigrationTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest,ExpertClassificationVersionGateGuardTest,ExpertIndexWriterServiceTest,ExpertClassificationBackfillServiceTest,ExpertClassificationAdminControllerTest,ExpertClassificationSchedulerTest,OperatorStatusWriteSeamGuardTest'
```

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| Identity/boundary | PASS | Master/ledger/handoff/repair hashes match; base→repair→evidence ancestry valid |
| Repair scope | PASS | `b03e81f..b3b7a9b` changes only authorized `app.js` and `batchExpertTypeFilter.test.js`; repair commit parent exactly `b03e81f` |
| M-1 | PASS | Explicit expert-type gate only; forbidden grep guards empty |
| M-2 | BLOCKED | Static fail-closed paths verified; fresh Maven runtime evidence incomplete |
| M-3 | BLOCKED | Source boundary preserves classifier rules/VERSION; required fresh classification regression incomplete |
| M-4 | PASS | Both 30-day constants unchanged |
| 01: I1-1..I1-8, S1-1 | BLOCKED | Static paths conform; fresh Maven checks incomplete |
| 02: I2-1..I2-5 | BLOCKED | Static paths conform; fresh Maven checks incomplete |
| 03: I3-1..I3-6, S3-1 | BLOCKED | V-1 browser path resolved; backend Maven checks incomplete |
| 04: I4-1..I4-6 | BLOCKED | Static guards conform; fresh guard/runtime Maven checks incomplete |
| 05: I5-1..I5-5 | BLOCKED | Static cleanup conforms; fresh Maven checks incomplete |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | MATERIAL_REMINDER empty types now reach API; INTRODUCTION remains blocked. Repair-specific JS tests 8/8; full JS 757/757. |

### Findings

#### P1

- N/A.

#### P2

- N/A.

#### Observations

- All prior RECORD_ONLY items remain non-blocking after re-evaluation.
- Existing parent-owned modification: `docs/plans/review/single-gate/ledger.md`; untouched.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| All epoch-1 RECORD_ONLY entries | M-1/M-2 or no mandatory master contract | RECORD_ONLY | Fresh reviewer reported all remain non-blocking; unavailable Maven evidence prevents no additional finding classification. |

### Evidence Boundaries

- Mandatory Maven targeted, full-test, and package evidence is unavailable because the escalated targeted run was interrupted with unknown terminal status.

### Next Action

Obtain/terminate session `19119` as appropriate, then freshly run the targeted Maven set, full `mvn test`, and full `mvn clean package`; rerun `verify-p`.

Repair planning: N/A — verification is `BLOCKED`, not an eligible `FAIL`.

No product code was modified.
