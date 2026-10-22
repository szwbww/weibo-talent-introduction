# Child 04 verification log

## Epoch 3 / attempt 1 — timestamp boundary 2026-10-22T23:06:00+08:00..2026-10-22T23:25:49+08:00

## Light Verification: LIGHT_FAIL
Child: 04 — docs/plans/2026-10-22/batch-demand-04-execution.md (commit:1ba7003f2719165e4d420132255d071102cd6c3e; SHA-256 d7ae76071b513da814c9b1f49feea7cc01942e87b500d0e0027588cc6fecd565)
Boundary: c83a74ec5afc6b380d0f3ec33aa997db512103f7..566ed817ce6f314383e0764331b75760f29e328c
Verifier: Demand04LightVerifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status c83a74e..566ed81` = exactly the ten authorized product/test paths (ManualInitialOutreachService.kt, BatchExecutionModels.kt, OutreachTargetIterator.kt, ExpertSearchCursor.kt, ManualInitialOutreachServiceTest.kt, OutreachTargetIteratorTest.kt, BatchSendTaskRuntimeIntegrationTest.kt, BatchDemandExecutionTest.kt, ExpertSearchCursorTest.kt, MailOpenTrackingPersistenceTest.kt) plus approved fast-p plan/evidence docs; `git status --porcelain` shows no product edits. |
| Plan and invariants | FAIL | I-1/I-2/I-3/I-5 have direct code and focused test evidence (demand page formula `ManualInitialOutreachService.kt:1719`, bounded NEW/ES sources `:1748-1990`, strict current reads `:1962`, material streaming count `:2481-2512`, iterator `OutreachTargetIterator.kt:65-160`; `BatchDemandExecutionTest` 26/26, `OutreachTargetIteratorTest` 28/28, `ExpertSearchCursorTest` 10/10 real ES). I-4 fails at F-1: running progress writes generic `totalCount` as the actual processed count, contrary to the 03 v2 protocol (`docs/plans/2026-10-22/batch-demand-03-progress.md:19`: running generic totalCount=0 means unknown full population; final totalCount=actual processed). |
| Required commands | PASS | Fresh, final product state, exact `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`: (1) focused `mvn test '-Dtest=ManualInitialOutreachServiceTest*,OutreachTargetIteratorTest,BatchSendTaskRuntimeIntegrationTest,BatchDemandExecutionTest,BatchSendExecutionDetailTest,ExpertSearchCursorTest,MailOpenTrackingPersistenceTest'` → BUILD SUCCESS, 365 tests, 0 failures, 0 errors, 1 gated real-ES skip; Node 1799 pass/0 fail/0 skip. (2) `mvn test -Dtest=ExpertSearchCursorTest -DesIt=true` → exit 0, 10 tests, 0 failures/errors/skips, actual ES 7.14.2 (`CURSOR_SMOKE documents=11001 pages=24 countRequests=0`; early stop releasedPits=2, openContexts=0, SMTP=0). (3) full `mvn test` → exit 0, 5440 tests, 0 failures, 0 errors, 23 skips; Node 1799 pass/0 fail/0 skip; `ExpertContactLocationServiceTest` 19/0/0 on JDK 11.0.32.1. Baseline old JDK 11.0.15 full 5441/9/19/23 was recorded, not rerun; the one-test delta is the authorized obsolete 10000-abort test deletion. |
| Downstream interfaces | PASS | 05 seams present: `countBySnapshot(BatchExecutionSnapshot)` retains signature and preview fields (`:616-688`), `diagnoseRecipients`/`scanBaseCandidates` 20000 diagnostic unchanged (`:810-864`, `:728-763`), material read-only streaming source without 10000 cap (`:2481-2512`, verified 11001 count/zero business writes), `ExpertSearchCursor.nextPage(pageSize)` 1..500 with no-arg default (`ExpertSearchCursor.kt:39-41`), and no cache reference in execution. |

### AUTO_FIX
- F-1: Running demand progress presents processed count as the total and 100% complete.
  - Governing requirement: 03 protocol I-2, incorporated by 04 I-4 (`docs/plans/2026-10-22/batch-demand-04-execution.md:32-33`): running generic `totalCount=0` means unknown full population; final `totalCount=actual processed`.
  - Evidence: `ManualInitialOutreachService.kt:2803-2804` writes `processedCount = breakdown.target` and `totalCount = breakdown.target` whenever demand metadata is present, for every RUNNING update. `BatchSendControlService.kt:184-193` copies `progress.totalCount` into `ExecutionLiveView`; `app.js:25090-25097` renders `已处理 X / 约 totalCount（percentage%）`, and `TaskProgressStore.kt:275-276` computes percentage from both, so a running detail shows `约 N（100%）` from the first update while the run continues (rounds/quota may still send more). Final rows are correct.
  - Smallest authorized correction: in the `totalCount` expression at `ManualInitialOutreachService.kt:2804`, write `0L` when `executionMetadata != null && status == "RUNNING"`, otherwise `breakdown.target.toLong()`; leave `processedCount` as the actual processed count. File #1 is authorized; no plan amendment needed.

### RECORD_ONLY
- N/A

### Required Action
- AUTO_FIX

## Epoch 3 / attempt 2 — timestamp boundary 2026-10-22T23:29:00+08:00..2026-10-22T23:50:00+08:00

## Light Verification: LIGHT_PASS
Child: 04 — docs/plans/2026-10-22/batch-demand-04-execution.md (commit:1ba7003f2719165e4d420132255d071102cd6c3e; SHA-256 d7ae76071b513da814c9b1f49feea7cc01942e87b500d0e0027588cc6fecd565)
Boundary: c83a74ec5afc6b380d0f3ec33aa997db512103f7..5b4bc0a134a7af7c08ce0b7ac5376ee567649c47
Verifier: Demand04Reverifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status c83a74e..5b4bc0a` = exactly the ten authorized product/test paths (ManualInitialOutreachService.kt, BatchExecutionModels.kt, OutreachTargetIterator.kt, ExpertSearchCursor.kt, ManualInitialOutreachServiceTest.kt, OutreachTargetIteratorTest.kt, BatchSendTaskRuntimeIntegrationTest.kt, BatchDemandExecutionTest.kt, ExpertSearchCursorTest.kt, MailOpenTrackingPersistenceTest.kt) plus approved fast-p plan/evidence docs; repair commit `5b4bc0a` touches only authorized `ManualInitialOutreachService.kt` + `BatchDemandExecutionTest.kt`; `git status --porcelain -- src` empty. |
| Plan and invariants | PASS | F-1 closed: `ManualInitialOutreachService.kt:2803-2806` writes `processedCount = breakdown.target` on every demand event and `totalCount = 0L` for `RUNNING`, `breakdown.target.toLong()` for terminal statuses, matching 03 I-2 (`docs/plans/2026-10-22/batch-demand-03-progress.md:19`: running generic totalCount=0 unknown population; final totalCount=actual processed). `BatchDemandExecutionTest.kt:442-455` asserts per captured event `processedCount = sentTotal+failedTotal+skippedTotal` and `totalCount = 0(RUNNING)/actual(final)`; 26/26 capture tests pass, covering both send types and early/failure paths. I-1/I-2/I-3/I-5 spot-checked: demand page formula `ManualInitialOutreachService.kt:1719`; bounded NEW keyset + terms-cursor source `:1748-1990`; strict `revalidate`/mget `:1907,1962`; material streaming read-only count `:2481-2512`; cursor closes `:545,1686`. Legacy non-demand `totalCount`/`processedCount` branches unchanged. |
| Required commands | PASS | Fresh, final product state, exact `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home` (Zulu 11.0.32.1): (1) focused `mvn test '-Dtest=ManualInitialOutreachServiceTest*,OutreachTargetIteratorTest,BatchSendTaskRuntimeIntegrationTest,BatchDemandExecutionTest,BatchSendExecutionDetailTest,ExpertSearchCursorTest,MailOpenTrackingPersistenceTest'` → exit 0/BUILD SUCCESS, 365 tests, 0 failures, 0 errors, 1 gated real-ES skip (reports: ManualInitialOutreachServiceTest 234 incl. nested, BatchDemandExecutionTest 26, BatchSendTaskRuntimeIntegrationTest 23, OutreachTargetIteratorTest 28, BatchSendExecutionDetailTest 38, ExpertSearchCursorTest 10/1 skip, MailOpenTrackingPersistenceTest 6); Node 1799 pass/0 fail/0 skip. (2) `mvn test -Dtest=ExpertSearchCursorTest -DesIt=true` → exit 0/BUILD SUCCESS, 10 tests, 0 failures/errors/skips, real-ES test `real ES 7_14 cursor traverses 11001 documents...` executed. (3) full `mvn test` → exit 0/BUILD SUCCESS, 5440 tests, 0 failures, 0 errors, 23 skips; Node 1799 pass/0 fail/0 skip. Matches recorded baseline full 5440/0/0/23 and realES 10/0/0/0; old JDK 11.0.15 timezone errors recorded, not rerun. |
| Downstream interfaces | PASS | 05 seams intact: `countBySnapshot(BatchExecutionSnapshot)` signature/preview fields (`ManualInitialOutreachService.kt:616-688`), `diagnoseRecipients`/`scanBaseCandidates` 20000 diagnostic unchanged (`:810-864`, `:728-763`), material read-only streaming source without 10000 cap (`:2481-2512`), `ExpertSearchCursor.nextPage(pageSize)` 1..500 with no-arg default (`ExpertSearchCursor.kt:39-41`), and no cache reference in the execution path. |

### AUTO_FIX
- N/A — F-1 (running demand progress presented processed count as total) is fixed by `5b4bc0a` and covered by the capture regression; no open four-gate violation.

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD
