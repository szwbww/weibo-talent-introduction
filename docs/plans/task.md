# Task List: 2026-06-15-es-unified-operator-status-query-plan

> Plan: `docs/plans/2026-06-15-es-unified-operator-status-query-plan.md`

| Task ID | Task Description | Status | Evidence |
|---|---|---|---|
| Task-01 | Phase 1: ES CANDIDATE indexing operatorStatus + sync write (WriterService, Entrypoints, SearchService, ExpertProfile) | done | Verified dynamic mapping update boot check via `checkCandidateOperatorStatusMapping()`. Tested ES indexing of `operatorStatus` (keyword) and term search in `ExpertIndexWriterServiceTest.kt` and `ExpertSearchServiceTest.kt`. |
| Task-02 | Phase 2: `/api/experts` API extension (listExperts, ExpertIndexResponse, Batch SQL) | done | Verified `/api/experts` parameter parsing and ES status query assembly. Covered by `ExpertIndexControllerTest.kt` and `ExpertIndexControllerMvcTest.kt`. |
| Task-03 | Phase 3: Frontend query unification (operatorStatus via ES, needsAttention MySQL degraded) | done | Verified frontend unified `loadContacts` in `app.js` using Node.js unit tests (105 pass). `needsAttention` retains MySQL degraded path as designed. |
| Task-04 | Phase 4: Batch outreach performance optimization (`countPending`, `countExperts`, `buildSnapshot`, `scrollExpertsFiltered`, loading text) | done | Verified `countPending` and `buildSnapshot` use shared `notContactedWithEmailFilters()`. `totalSendable` = pending + retryable; normal path has retryable subset of pending. Fifth round accepts occasional retryable overcount (very rare transient). Covered by `ManualInitialOutreachServiceTest.kt`. |
| Task-05 | Phase 5: Clean candidate index email-less history (revalidate candidates) | production_not_run | Verified candidate revalidation and downgrade behavior to RAW for email-less/failed contacts in `ExpertRevalidationServiceBehaviorTest.kt`. Production one-time cleanup not executed. |
| Task-06 | Phase 6: Backfill (backfill operatorStatus API and button) | done | Verified backfill latest-per-ORCID deduplication, mapping validation, and structured bulk response parsing (success/skipped/failure counts). NOT_CONTACTED uses painless script to remove field, not write value. Covered by `ExpertIndexControllerTest.kt` and `ExpertIndexWriterServiceTest.kt`. |
| Task-07 | Phase 7: consolidation (split-button for discovery, taskModal integration, async checkReplies, clean taskMenuDropdown) | done | Verified `CHECK_REPLIES` supports progress callbacks and cancellation check loop. Verified `MANUAL_INITIAL_OUTREACH` taskModal async entry. Covered by `BatchAutoMailReplyServiceTest.kt` and `taskModalStateMachine.test.js`. |

## Reverification Fix Tasks (2026-06-15)

| Fix Task | Description | Status | Evidence |
|---|---|---|---|
| Fix-01 | Declare keyword `operatorStatus` in mapping and check/validate on startup and before backfill | done | Boot check dynamic mapping presence in `ExpertIndexService.kt`. Validated in `ExpertIndexControllerTest.kt`. |
| Fix-02 | Deduplicate backfill contacts to keep latest-per-ORCID and parse bulk item status codes | done | Handled in `ExpertIndexController.kt` and `ExpertIndexWriterService.kt`. Verified in `ExpertIndexWriterServiceTest.kt`. |
| Fix-03 | Frontend task modal consolidation, fix launch callbacks and entrypoints | done | Corrected `handleBulkOutreach()` and modal triggers. Node unit tests passing (96/96). |
| Fix-04 | Add progress callbacks and cancellation checks to `CHECK_REPLIES` task | done | Implemented in `BatchAutoMailReplyService.kt` and `MailAutomationController.kt`. Verified in `BatchAutoMailReplyServiceTest.kt` and `MailAutomationControllerTest.kt`. |
| Fix-05 | Run complete verification and hygiene check | integration_blocked | Passed unit tests and JS syntax/lint tests, but `mvn -Pmigration-it test` integration tests blocked because the Docker socket is unavailable locally. |

## Second Reverification Fix Tasks (2026-06-15)

| Fix Task | Description | Status | Evidence |
|---|---|---|---|
| Fix2-01 | P1-1: Parse/show backfill warning/error/ok level mapping in frontend | done | Implemented in `app.js:handleBackfillOperatorStatus`. Verified counts formatting and warning/error levels in `taskModalStateMachine.test.js` under "Second reverification fix plan tests". |
| Fix2-02 | P1-2: Map CHECK_REPLIES terminal states (FAILED, PARTIAL_SUCCESS) in progress/watcher | done | Controller maps status. Mapped in `MailAutomationController.kt` and `task-modal-runtime.js:isProgressTerminal`. Tested with exact progress status and block output assertions in `MailAutomationControllerTest.kt` (`checkReplies records COMPLETED/PARTIAL_SUCCESS/FAILED/CANCELLED...`). |
| Fix2-03 | P2-1: Add ES search expert operatorStatus & counts assertions | done | Added test cases in `ExpertSearchServiceTest.kt` asserting search and scroll query parameters (`searchExperts with NOT_CONTACTED...` / `searchExperts with CONTACTED...` / `countExperts...` / `scrollExpertsFiltered...`). |
| Fix2-04 | P2-2: Verify syncCandidateOperatorStatus calls in operator service & tx helper | done | Mockito assertions verify ES updates in `ExpertOperatorStatusServiceTest.kt` (`changeStatus updates...` / `updateAutomatically...`) and `ManualOutreachTxHelperTest.kt` (`recordSuccess...`). |
| Fix2-05 | P2-3: Add frontend taskModal tests for new entrypoints and async launches | done | Verified `executeManualOutreach` and `executeCheckReplies` watcher trigger behaviors on acceptance in `taskModalStateMachine.test.js`. |
| Fix2-06 | P2-4: Update task.md with accurate test references and evidence | done | Added the Second Reverification Fix Tasks section with explicit, traceable evidence linking to individual unit tests. |

## Third Reverification Fix Tasks (2026-06-15)

| Fix Task | Description | Status | Evidence |
|---|---|---|---|
| Fix3-01 | P1-1: Unify PARTIAL_SUCCESS frontend display styling and label | done | Extracted `getProgressStatusMeta` in `task-modal-runtime.js`. Used it in `app.js` for modal text, old progress bar, and watcher. Added warning styling in `styles.css`. Tested in `taskModalStateMachine.test.js` ("getProgressStatusMeta..." and "updateTaskModalFromProgress sets warning style..."). |
| Fix3-02 | P2-1: Assert CHECK_REPLIES Controller final progress store updates | done | Added unit tests in `MailAutomationControllerTest.kt` asserting on COMPLETED, PARTIAL_SUCCESS, FAILED, and CANCELLED terminal progress updates. |
| Fix3-03 | P2-2: Assert CANDIDATE mapping contains operatorStatus keyword | done | Added unit tests in `ExpertIndexServiceTest.kt` verifying PUT mapping payload details and `checkCandidateOperatorStatusMapping()` parser states. |
| Fix3-04 | P2-3: Refactor task.md log status to differentiate not_run and blocked items | done | Updated `task.md` with explicit `production_not_run` and `integration_blocked` states. |

## Fourth Reverification Fix Tasks (2026-06-15)

| Fix Task | Description | Status | Evidence |
|---|---|---|---|
| Fix4-01 | P1-1: Task 1: 统一 NOT_CONTACTED 的 ES 存储语义，使用 Painless 脚本执行字段删除 | done | `syncCandidateOperatorStatus` and batch version use script `ctx._source.remove('operatorStatus')` for NOT_CONTACTED; other statuses write keyword. Covered by `ExpertIndexWriterServiceTest.kt`. |
| Fix4-02 | P1-2: Task 2: 统一 pending summary 与 snapshot 集合，避免重复计数 | done | `totalSendable` added to `PendingOutreachSummary`. Frontend `summarizeManualOutreachPending` uses `totalSendable`. Fifth round revised to `totalSendable = pending + retryable` accepting rare overcount. Covered by `ManualInitialOutreachServiceTest.kt`. |
| Fix4-03 | P1-3: Task 3: 让 `/api/experts` 返回 ES 状态，不使用 MySQL 覆盖 | done | `operatorStatus` in `listExperts` now uses `expert.operatorStatus ?: "NOT_CONTACTED"`. Covered by `ExpertIndexControllerTest.kt`. |
| Fix4-04 | P2-1: Task 4: 建立共享未联系查询定义，抽取共享的未联系 ES filters 构造函数 | done | `ExpertSearchService.notContactedWithEmailFilters()` extracted; used by `searchExperts`, `countPending`, `buildSnapshot`. Covered by `ExpertSearchServiceTest.kt`. |
| Fix4-05 | P2-2: Task 5: 修正交付记录并完成验证 | done | Updated fix plan statuses and evidence. Unit tests: 552 JVM / 105 Node. migration-it: integration_blocked. Real ES/browser/production: not_run. |

## Fifth Reverification Fix Tasks (2026-06-15)

| Fix Task | Description | Status | Evidence |
|---|---|---|---|
| Fix5-01 | P1-1: totalSendable revert to pending + retryable (accept rare overcount) | done | `countPending` returns `totalSendable = pending.toInt() + retryable`. Covered by `ManualInitialOutreachServiceTest.kt` (4 totalSendable assertions). |
| Fix5-02 | P1-2: Add HEAD check in promoteRawToCandidateWithEmail to prevent CANDIDATE overwrite | done | HEAD check before PUT; skip if exists, fail closed on non-404 errors. Coverage: `ExpertDiscoveryServiceTest.kt` (`promoteRawToCandidateWithEmail skips when CANDIDATE already exists`). |
| Fix5-03 | P1-3: Add ORCID identity verification + pick first valid email | done | `tryGetEmailFromOrcid` filters records by matching orcidId. `backfillRawEmailsAndPromote` uses `firstOrNull { valid }`. Coverage: `ExpertDiscoveryServiceTest.kt` (`tryGetEmailFromOrcid skips when orcidId does not match record`, `backfillRawEmailsAndPromote selects first valid email`). |

## Sixth Reverification Fix Tasks (2026-06-15)

| Fix Task | Description | Status | Evidence |
|---|---|---|---|
| Fix6-01 | P1-1: Restore attempt-based limit in backfillRawEmailsAndPromote (attemptedCount) | done | `attemptedCount` consumed before ORCID query; `promotedCount` for logging. Stops at 100 attempts regardless of success/failure. Coverage: `ExpertDiscoveryServiceTest.kt` (`backfillRawEmailsAndPromote stops at 100 attempts when CANDIDATE already exists`). |
| Fix6-02 | P2-1: Add regression tests (100-limit, HEAD-404 promote, HEAD-5xx fail closed, URL-orcid match, multi-email first-valid) | done | Five new test cases in `ExpertDiscoveryServiceTest.kt`. Java verification helpers in `DiscoveryMockHelper.java`. Total: 30 ExpertDiscoveryServiceTest tests, 559 JVM / 105 Node overall. |
| Fix6-03 | P2-2: Update task.md with accurate statuses, counts, and evidence | done | Updated task.md: fourth round actual statuses, fifth/sixth round entries, 559 JVM / 105 Node counts, `integration_blocked`, `not_run` for real ES/browser/production. |

## Seventh Reverification Fix Tasks (2026-06-15)

| Fix Task | Description | Status | Evidence |
|---|---|---|---|
| Fix7-01 | P1-1: Add cancellation gates after ORCID returns and between RAW update / CANDIDATE operations | done | Three `isCancelled` checks added in `backfillRawEmailsAndPromote`: after ORCID, before RAW update, before CANDIDATE promote. Cancelled status prevents subsequent ES writes. Coverage: `ExpertDiscoveryServiceTest.kt` (`cancel after ORCID returns does not write RAW or CANDIDATE`, `cancel after RAW update does not touch CANDIDATE`). |
| Fix7-02 | P2-1: Add bidirectional ORCID URL/bare matching tests + cancellation timing tests | done | Four new tests: cancel after ORCID (no RAW), cancel after RAW (no CANDIDATE), bare→URL ORCID match, URL→bare ORCID match. `stubCancelledAfterNCalls` helper in DiscoveryMockHelper.java. 563 JVM / 105 Node. |
| Fix7-03 | P2-2: Update task.md with seventh round | done | Seventh round entries. migration-it: integration_blocked. Real ES/browser/production: not_run. |
