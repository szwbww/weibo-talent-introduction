# Aggregate Machine Verification — expert-rnd-classification

## Epoch 1 — 2026-08-24T20:49:56+0800

- Master plan: `docs/plans/2026-08-24/00-expert-rnd-classification-master.md` (sha256 `0a3b99c0fcf0abbfcd13b97c15b3b244352d1283eaa2cc1359601b3073e01e01`)
- Governing master identity: `commit 3a4162c9c458f899470f59ac6e1a07b9ba748b3a`; sha256 `0a3b99c0fcf0abbfcd13b97c15b3b244352d1283eaa2cc1359601b3073e01e01`
- Master identity state: CONSISTENT; amendments A1, A2, A3 recorded in the fast-p ledger.
- Boundary: `c004a18d675b86040597f17f5911aa52f718d156..0bc071bf24c84426315bc4b138d8aa4394182910`
- Reviewer: `/root/aggregate_reviewer`
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: N/A; repair-p result BLOCKED (`PLAN_AMENDMENT_REQUIRED`).

### Fresh Command Evidence

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; `BUILD SUCCESS`; fresh 2026-08-24 20:45:49 +08:00 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertClassificationServiceTest,ExpertSearchServiceTest,ExpertIndexWriterServiceTest,ExpertClassificationBackfillServiceTest,ExpertClassificationAdminControllerTest,InitialOutreachServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest,ExpertClassificationSchedulerTest,TaskTypeCatalogTest,TaskExecutionSummaryExtractorTest,OperatorStatusWriteSeamGuardTest` | PASS | exit 0; `BUILD SUCCESS`; fresh 2026-08-24 20:47:28 +08:00 |
| `git diff --check c004..0bc` | PASS | exit 0 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0; Node `v25.7.0` |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 safe-fail all INTRODUCTION paths | PASS | `InitialOutreachService.kt:32-45`; `RecipientScope.matchesExpert`:61-65; `ManualInitialOutreachService.kt:604-618`; ES filter:1318-1323. |
| M-2 single classification semantics | PASS | Rules/keywords occur only in `ExpertClassificationService.kt` plus its/backfill tests; backfill calls classifier at `ExpertClassificationBackfillService.kt:121`; send paths consume derived result only. |
| M-3 one ES object | PASS | Three mappings normalize to the same object with nine required children; `ExpertProfile.kt:33`; `ExpertSearchService.kt:498-588`. No root-level second fact source. |
| M-4 local bulk-only write | PASS | `ExpertIndexWriterService.kt:244-251` emits `_bulk update`, `doc.expertClassification`, `doc_as_upsert:false`; backfill write at `ExpertClassificationBackfillService.kt:134`. Runtime `_update_by_query` absent; occurrences are prohibition documentation/comments only. |
| M-5 preview/execution/retry parity; reminder unchanged | PASS | `countBySnapshot`:450; retry builders:1002/1040; ES count/page:1260/1269 all share `buildEsFiltersForLevel`:1294; in-memory gate in `BatchExecutionModels.kt:61-65`; MATERIAL_REMINDER bypasses sendable term. |
| M-6 deploy/write separation | PASS | Admin API is explicit trigger (`ExpertClassificationAdminController.kt:42-145`); scheduler condition is default-disabled (`ExpertClassificationScheduler.kt:31-48`); bounded CANDIDATE-only request:49-58. |
| Global: ordered authorized implementation scope; fast-p evidence excluded from implementation commits | FAIL | `0bc071b` subject `feat(fast-p): implement 04` changes seven files, including `docs/plans/fast/expert-rnd-classification/children/03/fix-log.md`; child 04 authorizes six files only. |

### Finding Lineage

| Finding | State | Severity | Evidence |
|---|---|---|---|
| V-1 | NEW | P2 | `git show --name-only 0bc071b` proves that an evidence file is in an implementation commit. |

### Finding V-1

The mandatory implementation/evidence boundary is breached: commit `0bc071b` includes `docs/plans/fast/expert-rnd-classification/children/03/fix-log.md`, which is historical fast-p evidence and outside child 04's authorized six files. Product runtime is unaffected, but the global scope requirement says fast-p evidence is excluded from implementation commits.

Repair-p classified V-1 as `PLAN_AMENDMENT_REQUIRED`. The approved plan forbids amend/rebase/history rewrite; a normal new commit cannot remove an already committed evidence file from the implementation commit. Required human decision: approve a plan amendment that either permits that single evidence-file rewrite in `0bc071b`, or explicitly authorizes a history rewrite to remove it.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| No entries | N/A | PASS | Handoff RECORD_ONLY index is empty. |

### Other Evidence

- Child evidence 01–03 is present. Child 04 has `brief.md`, `execution.md`, `verify-log.md`; its `fix-log.md` is empty, consistent with zero fix rounds.
- Manual acceptance A-1 through A-6 remains PENDING; no production ES/API environment was invoked.
- No product code was modified by the aggregate reviewer.

## Epoch 3 — 2026-08-24T21:18:49+0800

- Master plan: `docs/plans/2026-08-24/00-expert-rnd-classification-master.md` (sha256 `0a3b99c0fcf0abbfcd13b97c15b3b244352d1283eaa2cc1359601b3073e01e01`)
- Governing master identity: `commit 3a4162c9c458f899470f59ac6e1a07b9ba748b3a`; sha256 `0a3b99c0fcf0abbfcd13b97c15b3b244352d1283eaa2cc1359601b3073e01e01`
- Master identity state: CONSISTENT. R1, R2, and R3 authorize only the three exact fast-p evidence-file exceptions recorded in the review ledger.
- Boundary: `c004a18d675b86040597f17f5911aa52f718d156..0bc071bf24c84426315bc4b138d8aa4394182910`
- Reviewer: `/root/aggregate_reviewer_final`
- Result: PASS
- Convergence: PROGRESSING
- Repair artifact/result: N/A

### Fresh Command Evidence

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; `BUILD SUCCESS`; finished 2026-08-24T21:16:44+08:00; Maven 2828 tests, 0 failures/errors, 4 skipped; Node suite 731/0. |
| `git diff --check c004a18d675b86040597f17f5911aa52f718d156 0bc071bf24c84426315bc4b138d8aa4394182910` | PASS | exit 0. |
| `node --check src/main/resources/static/app.js` | PASS | exit 0; Node `v25.7.0`. |
| Scope/history audit (`git log`, `git diff-tree`, `git diff --name-status`) | PASS | Product/test/runbook changes map to child 01–04 authorized files/amendments; only evidence-file exceptions are R1–R3. |
| Master identity audit | PASS | `git show 3a4162c:...master.md | shasum -a 256` equals governing SHA. |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 safe-fail all INTRODUCTION paths | PASS | `InitialOutreachService.kt:33-45`; `BatchExecutionModels.kt:61-65`; `ManualInitialOutreachService.kt:604-618`; shared ES term `:1318-1323`. |
| M-2 single classification semantics | PASS | `ExpertClassificationService.kt:32-70,103-189`; backfill only calls it at `ExpertClassificationBackfillService.kt:121`. |
| M-3 one ES object | PASS | Identical nine-child object in three mappings; RAW `orcid_info_raw.json:40-54`; model/parser at `ExpertProfile.kt:33`, `ExpertSearchService.kt:498-535`. |
| M-4 local bulk-only update | PASS | `_bulk update`, doc-only classification, `doc_as_upsert:false`: `ExpertIndexWriterService.kt:244-262`; sole execution call `ExpertClassificationBackfillService.kt:128-145`. |
| M-5 preview/execution/retry parity; reminder unchanged | PASS | Preview/retry/count reuse: `ManualInitialOutreachService.kt:450-468,1260-1291`; shared seam `:1294-1324`; in-memory gate only INTRODUCTION `BatchExecutionModels.kt:61-65`. |
| M-6 release/data-write separation | PASS | Explicit API/backfill validation `ExpertClassificationBackfillService.kt:162-175`; scheduler default-off `ExpertClassificationScheduler.kt:32-58`; config default false `application.yml:33`. |
| I1-1 type/sendable derivation | PASS | Six-value enum and derived-only getter `ExpertClassification.kt:8-45`; regression test `ExpertClassificationServiceTest.kt:75`. |
| I1-2 clinical precedence | PASS | Fixed priority `ExpertClassificationService.kt:38-59`; tests `ExpertClassificationServiceTest.kt:102-120`. |
| I1-3 medical whitelist | PASS | Medical/whitelist fail-closed branch `ExpertClassificationService.kt:41-56`; tests `:139-169`. |
| I1-4 deterministic classification | PASS | Injected clock/fingerprint/normalization `ExpertClassificationService.kt:25-26,61-70,175-216`; full regression passed. |
| I1-5 mapping/read fail-close | PASS | Unknown type becomes null at `ExpertSearchService.kt:503-535`; round-trip tests `ExpertSearchServiceTest.kt:1604-1690`. |
| I2-1 DRY_RUN zero writes | PASS | Writes only inside `mode == EXECUTE`: `ExpertClassificationBackfillService.kt:118-145`; test `ExpertClassificationBackfillServiceTest.kt:153`. |
| I2-2 partial non-upsert bulk writes | PASS | `ExpertIndexWriterService.kt:223-251`; NDJSON test `ExpertIndexWriterServiceTest.kt:764-766`. |
| I2-3 explicit level/mode/version/confirmation | PASS | Validation `ExpertClassificationBackfillService.kt:162-175`; controller tests `ExpertClassificationAdminControllerTest.kt:280-356`. |
| I2-4 cancellation/retry/failure visibility | PASS | Cancellation, pending filter, terminal result `ExpertClassificationBackfillService.kt:108-159,178-205`; tests `ExpertClassificationBackfillServiceTest.kt:260-326`. |
| I2-5 task mutual exclusion/identity | PASS | Scheduler lifecycle `ExpertClassificationScheduler.kt:77-139`; controller and scheduler tests passed in full regression. |
| I2-6 accounting completeness | PASS | Backfill result/counter tests included in fresh `mvn test`; child service is sole result producer. |
| I3-1 INTRODUCTION fail closed | PASS | Query plus final gates: `ExpertSearchService.kt:356-374`, `InitialOutreachService.kt:33-45`, `ManualInitialOutreachService.kt:604-618`. |
| I3-2 ES/in-memory predicate equivalence | PASS | Exact ES predicate `ExpertSearchService.kt:50-56`; exact memory predicate `BatchExecutionModels.kt:61-65`. |
| I3-3 preview/execution same seams | PASS | `countBySnapshot` calls retry/count seams `ManualInitialOutreachService.kt:450-468`; count/page call `buildEsFiltersForLevel` `:1260-1291`. |
| I3-4 final send gate | PASS | Gates precede contact/account/render/delivery: `InitialOutreachService.kt:39-58`, `ManualInitialOutreachService.kt:600-623`; reason code `BatchExecutionModels.kt:152-164`. |
| I3-5 MATERIAL_REMINDER unchanged | PASS | Gate/ES term only under INTRODUCTION: `BatchExecutionModels.kt:61-65`, `ManualInitialOutreachService.kt:1318-1323`. |
| I4-1 default off | PASS | `application.yml:33`; conditional scheduler `ExpertClassificationScheduler.kt:32-36`; disabled-context test `ExpertClassificationSchedulerTest.kt:293-298`. |
| I4-2 CANDIDATE pending only | PASS | Fixed request `ExpertClassificationScheduler.kt:49-58`; test `ExpertClassificationSchedulerTest.kt:129-154`. |
| I4-3 shared mutual exclusion/lifecycle | PASS | Shared type/executor/token-bind/cleanup `ExpertClassificationScheduler.kt:77-122`. |
| I4-4 bounded increment | PASS | Properties used in bounded request `ExpertClassificationScheduler.kt:53-55`; max-doc stop `ExpertClassificationBackfillService.kt:114-120`; scheduler tests passed. |
| I4-5 conservative same-version behavior | PASS | Pending filter only missing/non-current version `ExpertClassificationBackfillService.kt:178-198`; no runtime `_update_by_query`/script query found. |
| Scope: child 01–04 files/amendments | PASS | Commit/file audit maps changes to child authorizations and A1/A2/A3. |
| R1 authority exception | PASS | Exactly `children/03/fix-log.md` in `0bc071bf24c84426315bc4b138d8aa4394182910`; no broader exception applied. |
| R2 authority exception | PASS | Exactly `children/01/fix-log.md` in `4937fe6ff32f36b655a173f4b742581700f2e2b5`; no broader exception applied. |
| R3 authority exception | PASS | Exactly `children/02/fix-log.md` in `b2188438ee45321b718efa5f70f3bbcaca1180e0`; no broader exception applied. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | R1 precisely authorizes `children/03/fix-log.md` in `0bc071b`. |
| V-2 | RESOLVED | R2 precisely authorizes `children/01/fix-log.md` in `4937fe6`. |
| V-3 | RESOLVED | R3 precisely authorizes `children/02/fix-log.md` in `b218843`. |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| No entries | N/A | PASS | Handoff index is empty; child verify logs contain no unresolved RECORD_ONLY entries. |

### Other Evidence

- No P1 or P2 findings remain.
- Production/manual acceptance A-1 through A-6 remains pending; no production ES/API environment was invoked.
- No product code was modified by the aggregate reviewer.

## Epoch 2 — 2026-08-24T21:07:50+0800

- Master plan: `docs/plans/2026-08-24/00-expert-rnd-classification-master.md` (sha256 `0a3b99c0fcf0abbfcd13b97c15b3b244352d1283eaa2cc1359601b3073e01e01`)
- Governing master identity: `commit 3a4162c9c458f899470f59ac6e1a07b9ba748b3a`; sha256 `0a3b99c0fcf0abbfcd13b97c15b3b244352d1283eaa2cc1359601b3073e01e01`
- Master identity state: CONSISTENT. R1 is the human-approved review authority for exactly `children/03/fix-log.md` in `0bc071bf24c84426315bc4b138d8aa4394182910`; it authorizes no other file.
- Boundary: `c004a18d675b86040597f17f5911aa52f718d156..0bc071bf24c84426315bc4b138d8aa4394182910`
- Reviewer: `/root/aggregate_reviewer_rerun`
- Result: FAIL
- Convergence: DIVERGING
- Repair artifact/result: N/A; `FAIL + DIVERGING` requires human adjudication.

### Fresh Command Evidence

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; `BUILD SUCCESS`; finished 2026-08-24T21:04:24+08:00. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertClassificationServiceTest,ExpertSearchServiceTest,ExpertIndexWriterServiceTest,ExpertClassificationBackfillServiceTest,ExpertClassificationAdminControllerTest,InitialOutreachServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest,ExpertClassificationSchedulerTest,TaskTypeCatalogTest,TaskExecutionSummaryExtractorTest,OperatorStatusWriteSeamGuardTest` | PASS | exit 0; `BUILD SUCCESS`; finished 2026-08-24T21:06:29+08:00. |
| `git diff --check c004a18d675b86040597f17f5911aa52f718d156 0bc071bf24c84426315bc4b138d8aa4394182910` | PASS | exit 0. |
| `node --check src/main/resources/static/app.js` | PASS | exit 0; Node `v25.7.0`. |
| Scope/history audit via `git diff-tree` / `git show --name-only` | FAIL | Two unapproved fast-p evidence files are in implementation commits. |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 | PASS | `InitialOutreachService.kt:33-45` uses `searchSendableExpertsWithEmail` and final null/false check; `BatchExecutionModels.kt:61-65` fails closed in retries; `ManualInitialOutreachService.kt:1318-1323` adds the shared ES predicate only for INTRODUCTION. |
| M-2 | PASS | Classification rules/keywords are confined to `ExpertClassificationService.kt`; backfill calls `classificationService.classify` at `ExpertClassificationBackfillService.kt:121`; send paths consume only derived `sendable`. |
| M-3 | PASS | All three mappings have one `expertClassification` object with identical nine children; parser reads it at `ExpertSearchService.kt:498-535`; no sibling root fact source found. |
| M-4 | PASS | `ExpertIndexWriterService.kt:244-251` emits `_bulk update`, `doc.expertClassification` only, `doc_as_upsert:false`; `ExpertClassificationBackfillService.kt:128-145` is the only classification write caller. No runtime `_update_by_query`. |
| M-5 | PASS | Preview, ES count/page, and retry all share `RecipientScope` / `buildEsFiltersForLevel` (`ManualInitialOutreachService.kt:450-468`, `1002-1031`, `1260-1324`); MATERIAL_REMINDER omits the sendable predicate. |
| M-6 | PASS | Explicit admin trigger at `ExpertClassificationAdminController.kt:42-145`; scheduler bean is default-disabled at `ExpertClassificationScheduler.kt:31-57`; only CANDIDATE pending request is constructed. |
| I4-1–I4-5 | PASS | Properties bounds/defaults at `ExpertClassificationProperties.kt:15-26`; scheduler lifecycle/lock cleanup at `ExpertClassificationScheduler.kt:77-139`; pending query at `ExpertClassificationBackfillService.kt:178-198`. |
| Global scope / fast-p evidence exclusion | FAIL | `4937fe6` (child 02 implementation) includes `docs/plans/fast/expert-rnd-classification/children/01/fix-log.md`; `b218843` (child 03 implementation) includes `docs/plans/fast/expert-rnd-classification/children/02/fix-log.md`. Neither is authorized by child plans/amendments. |
| R1 retroactive authorization | PASS | R1 authorizes exactly `docs/plans/fast/expert-rnd-classification/children/03/fix-log.md` in `0bc071bf24c84426315bc4b138d8aa4394182910`; no wider authority inferred. |

### Finding Lineage

| Finding | State | Severity | Evidence |
|---|---|---|---|
| V-1 | RESOLVED | P2 | R1 retroactively authorizes only `children/03/fix-log.md` in `0bc071b`. |
| V-2 | NEW | P2 | `4937fe6` implementation commit contains unapproved `children/01/fix-log.md`. |
| V-3 | NEW | P2 | `b218843` implementation commit contains unapproved `children/02/fix-log.md`. |

### Findings

- V-2: Child 02 implementation commit `4937fe6ff32f36b655a173f4b742581700f2e2b5` contains `docs/plans/fast/expert-rnd-classification/children/01/fix-log.md`, outside its authorized files. R1 does not cover it.
- V-3: Child 03 implementation commit `b2188438ee45321b718efa5f70f3bbcaca1180e0` contains `docs/plans/fast/expert-rnd-classification/children/02/fix-log.md`, outside its authorized files. R1 does not cover it.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| No entries | N/A | PASS | RECORD_ONLY index is empty; child verify logs state `N/A`. |

### Other Evidence

- Manual acceptance A-1 through A-6 remains PENDING; no production ES/API environment was invoked.
- V-1 is resolved, but V-2 and V-3 expand the unresolved set from one to two; convergence is DIVERGING.
- No product code was modified by the aggregate reviewer.
