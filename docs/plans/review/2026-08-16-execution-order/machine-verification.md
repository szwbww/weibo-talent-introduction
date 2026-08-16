# Aggregate Machine Verification — master: docs/plans/2026-08-16/00-execution-order.md

## Epoch 1 — 2026-08-16

- Master plan: `docs/plans/2026-08-16/00-execution-order.md` (sha256 `2ca42704e7c4608eaf7f6199ca86fa2acbde51cb54a2b968b60135f187e89118`)
- Governing master identity: worktree sha256 `2ca42704e7c4608eaf7f6199ca86fa2acbde51cb54a2b968b60135f187e89118`; recorded commit `65b8de831a5f0edeafeae5683a2f15b79f7000a3`
- Master identity state: `CONSISTENT`; governing amendment: N/A; approved amendments A1–A6 are recorded in the fast-p ledger.
- Boundary: `edda3e4e67e8b4511f3c7ca76b09926c56e4f69a..4d7f206a4f506104af73f3e63e4fceea3d857ef7`
- Reviewer: `/root/aggregate_reviewer` (fresh independent reviewer)
- Result: `FAIL`
- Convergence: `INITIAL`
- Repair artifact/result: `docs/plans/fix/00-execution-order/repair.md` (`DRAFT_READY`, sha256 `ebbb5dbfcf25ddb2784b49dd38b429ed2fe60aa3e75ce8f08b491f220832026d`)

### Verification Result: FAIL

Manual acceptance: `PENDING`.

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=...zulu-11... mvn test` | PASS | exit 0; 2512 tests, 0 failures, 0 errors, 4 skipped; `BUILD SUCCESS`. First run’s Surefire bootstrap temp-file failure was retried after no concurrent process was found. |
| `JAVA_HOME=...zulu-11... mvn clean package` | PASS | exit 0; 2512/0/0/4; JS 630/630; WAR built; `BUILD SUCCESS`. |
| `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js` | PASS | exit 0; 17 pass, 0 fail. |
| `node --check src/main/resources/static/app.js` | PASS | exit 0. |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 630 pass, 0 fail. |
| `git diff --check` | PASS | exit 0; no output. |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| C-1 Boundary/governance | PASS | SHA256s match supplied master, ledger, handoff; HEAD is supplied evidence head; all 32 child evidence files present; A1–A6 are HUMAN-approved. |
| C-2 Master order/cache/migrations | PASS | Commit history preserves A1→A2→A3→B1→B2→B3→B4→B5; final cache triad is `20260817-v6-task-drilldown`; V100/V101/V102 exist in order. |
| A-M1 / A1 I-1,I-2 | PASS | `app.js:13570-13615` truncates `scopeParts` at array level and appends gate pill after folded items; layout tests pass. |
| A-M2 / A1 I-3,I-4 | PASS | `index.html:1117,1516`; opaque drawer/body containment and exception-only hiding are covered by passing drawer tests. |
| A2 I2-1..I2-4 / A-M3 | PASS | `logMode` routing, non-destructive tab switch, global execution listing, and dual limit clamps pass child and aggregate tests. |
| A3 I3-1..I3-3 / A-M4 | PASS | `index.html:106,737`; one unchanged `bulkOutreachBtn` moved to mailbox panel; relocation tests pass. |
| B-M1 / B1 I0-1..I0-5 | PASS | `TaskExecutionRepository.kt:128-184` has explicit non-TEXT projections and four filtered pages; controller clamps pagination at `TaskExecutionController.kt:37-50`. |
| B-M7 / B1 I0-6; B2 I1-8; B4 I2b-6 | PASS | Final cache triad and three visual-test literals use the same v6 key; targeted and aggregate JS tests pass. |
| B-M2,B-M3 / B2 I1-1..I1-7 | PASS | `TaskTypeCatalog.kt`; shared extractor, runtime type options, generic bounded detail fallback, and semantic rendering pass. |
| B-M5 / B3 I2a-1..I2a-5 | PASS | V101 has no FK; designated outreach paths carry execution ID; link tests pass. |
| B-M4 / B4 I2b-1..I2b-5 | PASS | Drilldown state/texts, mailbox filtering, dangling-ID fallback, reused navigation, and no new view pass. |
| B-M6 / B5 I3-1,I3-3..I3-6 | PASS | Correct cutoff predicates, index, order, no self-exemption, independent failure handling, scheduler/config/catalog behavior pass. |
| B5 I3-2 | FAIL | `TaskAuditRetentionService.kt:42,49,58-65` always issues full `batchSize` then checks cap. With cap 3000 and batch 2000 it deletes/reports 4000; test explicitly asserts this at `TaskAuditRetentionServiceTest.kt:65-73`. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | No prior aggregate finding. B5 I3-2 total-cap violation proven by runtime path and regression test. |

### Findings

#### P1

- V-1: B5 I3-2 requires a per-run deletion upper bound. `maxRowsPerRun=3000` allows 4000 deletions because the second delete still receives 2000. Implicated scope: `TaskAuditRetentionService.kt` and its test.

#### P2

- N/A

#### Observations

- Final fresh aggregate count is 2512/0/0/4, consistent with baseline plus child additions.
- No MySQL/Docker authority was available for conditional EXPLAIN/Flyway integration checks; mandatory static, unit, migration-text, and aggregate gates passed.

### Evidence Boundaries

- Manual acceptance remains pending.
- Docker-backed Flyway integration and live MySQL EXPLAIN were unavailable; plans record them as conditional.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| A1 O-1: `renderErrorSamples` substring | A-M1 applies only to `renderBatchConfigRow`; plan forbids changing this pre-existing path | Non-blocking | `app.js:15237` |
| A2 O1/O2 | Aggregate boundary and A2 I2-1/I2-2/M-3 | Non-blocking; docs-only head delta and in-scope test semantics | `children/a2/verify-log.md` |
| A3 O-1; B1 O-1 | Required command evidence | Non-blocking reporting/count artifacts; fresh aggregate commands passed | respective verify logs and fresh Maven result |
| B1 O-2/O-3 | B-M1 migration/index requirement | Non-blocking docs whitespace; conditional EXPLAIN not locally available, V100 text/static gates pass | `children/b1/verify-log.md` |
| B2 O-1/O-2/O-3 | B-M2/B-M3 and aggregate scope | Non-blocking docs artifacts and output-equivalent escaping | `children/b2/verify-log.md` |
| B3 O-1/O-2/O-3 | B-M5 migration/link contract | Non-blocking conditional Flyway, docs-only boundary, and plan arithmetic; required static/link tests pass | `children/b3/verify-log.md` |
| B4 O-1/O-2/O-3 | B-M4 drilldown contract | Non-blocking count accounting, authorized adapter detail, and docs-only boundary | `children/b4/verify-log.md` |
| B5 O-1/O-2/O-3 | B-M6/B5 I3 requirements | Non-blocking stale comment, conditional Flyway, configuration-registration placement; the independently proven I3-2 violation is V-1 | `children/b5/verify-log.md` |

### Repair Planning Result: DRAFT_READY

Baseline plan: `docs/plans/2026-08-16/00-execution-order.md`.

Included finding: V-1. Excluded findings: N/A.

The repair plan limits changes to `TaskAuditRetentionService.kt` and `TaskAuditRetentionServiceTest.kt`, preserves deletion order/predicates/error isolation/defaults, and requires a 2000/1000 remaining-capacity regression test. Its required execution-handoff section authorizes no work unless the human explicitly invokes its exact path.

No product code was modified.
