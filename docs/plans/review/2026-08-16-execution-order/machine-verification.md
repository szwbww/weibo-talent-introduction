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

## Epoch 2 — 2026-08-17

- Master plan: `docs/plans/2026-08-16/00-execution-order.md` (sha256 `2ca42704e7c4608eaf7f6199ca86fa2acbde51cb54a2b968b60135f187e89118`)
- Governing master identity: worktree sha256 `2ca42704e7c4608eaf7f6199ca86fa2acbde51cb54a2b968b60135f187e89118`; recorded commit `65b8de831a5f0edeafeae5683a2f15b79f7000a3`
- Master identity state: `CONSISTENT`; A1–A6 remain the human-approved amendments recorded in the fast-p ledger.
- Boundary: `edda3e4e67e8b4511f3c7ca76b09926c56e4f69a..3867f61b26b5584b54ac52e540360f7aa8122492`
- Post-repair boundary: `4d7f206a4f506104af73f3e63e4fceea3d857ef7..3867f61b26b5584b54ac52e540360f7aa8122492`
- Reviewer: `/root/post_repair_reviewer` (fresh after repair commit; no inherited fast-p or repair-execution context)
- Repair evidence: `DURABLE_HANDOFF`; `docs/plans/fix/00-execution-order/repair.md` sha256 `ebbb5dbfcf25ddb2784b49dd38b429ed2fe60aa3e75ce8f08b491f220832026d`; `repair-execution.md` sha256 `f97c747c558572c0a6e576856747a6c995343263aaf6eaa146135480f3532cc4`.
- Result: `PASS`
- Convergence: `CONVERGED`
- Repair artifact/result: N/A (V-1 is resolved; no new repair plan)

### Fresh command evidence

| Command | Exit | Result |
|---|---:|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | 0 | PASS; JVM 2512 tests, 0 failures, 0 errors, 4 skipped; embedded JS 630/630; `BUILD SUCCESS`. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | 0 | PASS; JVM 2512/0/0/4; embedded JS 630/630; WAR built; `BUILD SUCCESS`. |
| `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js` | 0 | 17/17 pass. |
| `node --check src/main/resources/static/app.js` | 0 | PASS. |
| `node --test src/test/js/*.test.js` | 0 | 630/630 pass; 99 suites. |
| `git diff --check` | 0 | clean. |

`mvn clean package` retries are recorded: its first two attempts could not delete one untracked transient `target/*.war.original`; it was current-user writable with no `lsof` holder. Deleting that transient build artifact, then a third fresh run, produced exit 0. No tracked content changed during the diagnosis.

### Master contract matrix

| Contract | Status | Evidence |
|---|---|---|
| Governance / child order / A1–A6 amendments | PASS | Master/hash identities, ordered child evidence, and human-approved amendments agree. |
| A1 console drawer | PASS | Scoped selection, layout, opacity, and timeline-error behavior remain covered by passing JS suites. |
| A2 global recent-log drawer | PASS | Global 50-row fetch, stable identity, tab persistence, and route behavior pass. |
| A3 batch outreach list | PASS | Contact/expert columns and expected bulk-outreach behavior/tests pass. |
| B1 execution list / pagination / cache triad | PASS | Projection/count contracts, defaults, pager, and cache key `20260817-v6-task-drilldown` are consistent. |
| B2 task catalog / summary extractor | PASS | Catalog, dynamic options, 32KB generic detail, and derived statuses pass. |
| B3 outreach execution persistence | PASS | V101 has no FK; transaction-helper success/failure paths are covered. |
| B4 drill-down / mailbox filter | PASS | Per-record drilldown and dangling-reference behavior remain correct. |
| B5 retention | PASS | Cutoff/order/table isolation/status contracts remain intact; V-1 cap repair passes. |
| V-1 repair authorization and scope | PASS | `4d7f..3867` product/test delta is exactly the two repair-authorized files. |

### Finding lineage

| Finding | Epoch 1 | Epoch 2 |
|---|---|---|
| V-1 | `maxRowsPerRun=3000` with batch 2000 deleted 4000 rows. | RESOLVED: `TaskAuditRetentionService.kt:64-70` passes `minOf(batchSize, remaining)`; `TaskAuditRetentionServiceTest.kt:65-83` asserts total 3000 and delete limits `[2000, 1000]`. |

### Findings

- P1: none.
- P2: none.
- Repair planning: N/A.

### Fast-P RECORD_ONLY Re-evaluation

| Source | Result |
|---|---|
| A1 substring path; A2/B1/B2/B3/B4/B5 docs/count/adapter/comment observations | Re-evaluated against the master matrix; non-blocking and unchanged. |
| B1/B3/B5 conditional MySQL EXPLAIN/Flyway checks | Still conditional environment boundaries; static, migration-text, unit, and aggregate gates pass. |

### Evidence boundaries

- Docker-backed Flyway integration and live-MySQL `EXPLAIN` remain conditionally unavailable.
- Manual acceptance is pending under `manual-acceptance.md`, Epoch 2.
- No product code was modified by this review.

No product code was modified.
