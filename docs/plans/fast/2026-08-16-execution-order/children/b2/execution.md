# B2 Execution Report — PLAN_CONFLICT (stopped at preflight, no edits, no commit)

- Executor: ImplementB2
- Outcome: **PLAN_CONFLICT** — implementing the plan per its own contract requires editing 4 test files that are NOT in the authorized 10-file list; the plan explicitly instructs: 执行时若发现还须改第 11 个文件，停止并回报，不要顺手扩围.
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast/docs/plans/2026-08-16/b2-task-type-catalog-semantics.md`
- Plan SHA-256: `20ba6ac5e5b23c3545f2a0bfe214d17401cabee9106829a6134cdbe5850cf0f2`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast/docs/plans/2026-08-16/b2-task-type-catalog-semantics.md@20ba6ac5e5b23c3545f2a0bfe214d17401cabee9106829a6134cdbe5850cf0f2`
- Execution epoch: NEW
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast@fast/2026-08-16-execution-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast`
- Pre-execution HEAD: `34bddec79bf0112f1280700a7ce0228308fec04c` (controller evidence commits on top of b1 code head `ad005d98b706ceed67b34c96a89e642334ca819a`)
- Post-execution HEAD: unchanged (no edits made)
- Worktree index: clean (no changes staged)
- Changed files: **none** (implementation stopped at preflight per plan contingency rule)

---

## 1. Plan / worktree identity gates (execute-p)

- `plan_identity.py` → canonical path + sha256 as above, exit 0.
- `worktree_identity.py --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast` → branch `fast/2026-08-16-execution-order`, git_dir `…/.git/worktrees/weibo-talent-introduction-fast`, head `34bddec…`, exit 0.
- Re-verified at handoff: plan SHA and worktree identity unchanged.

## 2. Chain check (brief: verify before editing index.html)

`grep -n '?v=' src/main/resources/static/index.html` at HEAD:

```
11:    <link rel="stylesheet" href="styles.css?v=20260817-v4-task-records-paging">
2039:<script src="trust-reply-workbench.js?v=20260817-v4-task-records-paging"></script>
2040:<script src="app.js?v=20260817-v4-task-records-paging"></script>
```

- All three `?v=` values equal `20260817-v4-task-records-paging` (b1's value) → chain check **PASS** (would have bumped to v5 only if implementation proceeded).
- `batchSendTaskConsoleVisualFix.test.js` currently asserts the same v4 literals (baseline run: 17 pass / 0 fail — see §6).

## 3. ⏳ metricLabel decisions (resolved from actual write-side sources, per brief)

Decision rule applied (plan T1-1 + brief): label only if the persisted `success_count`/`failure_count` reflection/provider path actually hits fields whose semantics match the label; otherwise `null` (safe: — 无统计). Never guess from field names.

### 3.1 INITIAL_OUTREACH → `metricLabel = null`（— 无统计）

Write-side sources read:

- `campaign/service/InitialOutreachService.kt:32` — `fun sendInitialBatch(campaignId: Long, size: Int): InitialOutreachBatchResult`
- `campaign/service/InitialOutreachService.kt:147-153` — `data class InitialOutreachBatchResult(requested, candidates, sent, failed, skipped, results)` — **NOT** a `TaskExecutionSummaryProvider`.
- Reflection list (`task/service/TaskExecutionService.kt:281-300`, `TaskResultSummary.from`): success side `firstInt(fields, "sent","replied","accepted","fetched","dispatched")` → hits `sent`; failure side `firstInt(fields, "manualReview","skipped","failureCount")` → hits **`skipped`** (send failures live in `failed`, which is NOT in the failure list).
- QUEUE-mode write path: `mail/queue/MailQueueConsumer.kt:23-33` and `task/service/MailAutomationScheduler.kt:52-62` — when the publisher is present the block returns `QueuePublishResult(accepted: Boolean, queue, messageType)` (`mail/queue/MailQueueMessages.kt:17-20`) → reflection maps the Boolean `accepted` → `success_count` = 1/0 (accepted flag), `failure_count` = 0. Meaningless.

Decision: **null**. Reason: (a) in SYNC mode the persisted failure side is `skipped` (already-contacted / suppressed / no-email), not send failures — a label "已发送/失败" would be a lie; (b) in QUEUE mode the persisted success side is an accepted-flag Boolean. Neither reflects a clean "已发送/失败" semantic; landing null (— 无统计) is the safe, honest choice. Real per-execution numbers remain available in expanded detail via extractor level ①/②.

### 3.2 AUTO_REPLY_ALL → `metricLabel = "轮询账号/失败账号"`

Write-side sources read:

- `mail/service/BatchAutoMailReplyService.kt:17-20` — `fun receiveAndAutoReplyAll(...): BatchAutoMailReplyResult`
- `mail/service/BatchAutoMailReplyService.kt:167-178` — `data class BatchAutoMailReplyResult(accountCount, successAccountCount, failedAccountCount, fetched, recorded, replied, manualReview, accounts, …, wasCancelled) : TaskExecutionSummaryProvider { taskSuccessCount = successAccountCount; taskFailureCount = failedAccountCount }`
- Provider branch (`TaskExecutionService.kt:131-135` / `:209-213`): persisted `success_count = successAccountCount`, `failure_count = failedAccountCount` — truthful, and matches the plan's own observable-outcome example "4/0 轮询账号/失败账号".

Decision: **"轮询账号/失败账号"**. Caveat (documented, not blocking): in QUEUE-mode deployments (`MAIL_QUEUE_ENABLED=true`) the SCHEDULED `AUTO_REPLY_ALL` row's block returns `QueuePublishResult(accepted)` (1/0); the default deployment is SYNC (`application.yml:48` default `false`), and the plan's own acceptance scenarios (A1-7, b3 preconditions) run with `mail-queue.enabled=false`. In the default deployment the persisted counts are exactly successAccountCount/failedAccountCount.

### 3.3 OPERATOR_STATUS_RECONCILE → `metricLabel = "一致/异常"`

Write-side sources read:

- `campaign/service/OperatorStatusReconcileService.kt:303-315` — `data class ReconcileReport(total, consistent, dbVsExpected, esVsDb, humanOverride, samples) : TaskExecutionSummaryProvider { taskSuccessCount = consistent; taskFailureCount = dbVsExpected + esVsDb; taskFinalStatus = null }`
- Write paths: `task/service/MailAutomationScheduler.kt:82-87` (SCHEDULED) and `expert/controller/ExpertIndexController.kt:226-232` (MANUAL) — both `runAndRecordWithResult("OPERATOR_STATUS_RECONCILE", …) { … reconcile() }` returning `ReconcileReport` → provider branch persists `success_count = consistent`, `failure_count = dbVsExpected + esVsDb`. No queue variant for this task type.

Decision: **"一致/异常"** (success side = 一致数 consistent; failure side = 异常数 dbVsExpected+esVsDb). Persisted values truthfully map to the label in both write paths.

### 3.4 Summary table

| code | metricLabel | decision basis |
|---|---|---|
| INITIAL_OUTREACH | `null` | InitialOutreachService.kt:32,147-153; failure side hits `skipped` not `failed`; QUEUE mode persists `accepted` Boolean (MailQueueMessages.kt:17-20) |
| AUTO_REPLY_ALL | `轮询账号/失败账号` | BatchAutoMailReplyService.kt:167-178 provider → successAccountCount/failedAccountCount persisted (default SYNC deployment) |
| OPERATOR_STATUS_RECONCILE | `一致/异常` | OperatorStatusReconcileService.kt:303-315 provider → consistent / (dbVsExpected+esVsDb) persisted via both write paths |

## 4. Re-verify greps (brief verify-first items)

- `grep -rn "allowedTaskTypes" src/main` → only `task/controller/TaskProgressController.kt:33-36` (hardcoded 6-item set: EXPERT_REVALIDATION / RAW_PROMOTION_SCAN / EXPERT_DISCOVERY / EXPERT_ENRICHMENT / MANUAL_INITIAL_OUTREACH / CHECK_REPLIES). Matches plan audit. (Would be replaced by catalog derivation per T1-3.)
- Three hardcoded lists present as documented: `index.html:940-947` `#taskTypeFilter` (5 options), `app.js:679-685` `taskButtonMapping` (6 items), `TaskProgressController.kt:33-36` `allowedTaskTypes` (6 items).
- `parseResultSummary` / `fallbackFromLog` / `detectWasCancelled` / `ExecutionTotals` grep across `src`: all hits inside `TaskProgressController.kt` only (private, no external callers) — clean to migrate into the extractor.
- `findRecentByTaskType` callers: TaskProgressController.kt:85, TaskExecutionService.kt:55/86, plus test mocks — unchanged by plan.

## 5. ❗ PLAN_CONFLICT — root cause with evidence

The plan's mandated changes necessarily break **four existing test files that are NOT in the authorized 10-file list**, so the required full regression (`mvn test`, which includes `node --test src/test/js/*.test.js` per `pom.xml:199`) cannot pass without editing files the plan forbids touching. Per the plan's explicit contingency ("执行时若发现还须改第 11 个文件，停止并回报，不要顺手扩围") I stopped without editing anything.

### 5.1 `src/test/js/taskRecordsPaging.test.js` (b1) — N0-1 verbatim assertion breaks

Plan T1-7 #2 mandates: `loadTasks()` 的第 2 列改渲染 `task.taskTypeLabel`；第 5 列按 S1-3 渲染 (metricLabel-null → `<span class="text-muted">— 无统计</span>`).

b1's N0-1 test (`taskRecordsPaging.test.js`) renders a fixture item **without** `taskTypeLabel`/`metricLabel` and asserts verbatim:

```js
<td>AUTO_REPLY_ALL</td>   // col2 (would still pass only with `|| task.taskType` fallback)
<td>4/0</td>              // col5 ← plan-mandated S1-3 rendering emits — 无统计
```

Empirical proof (node, exact N0-1 harness + plan-mandated rendering):

```
N0-1 passes with plan-mandated new rendering: false
actual col5: "<td><span class=\"text-muted\">— 无统计</span></td>"
expected col5: <td>4/0</td>
```

There is no honest rendering that both (a) shows "4/0" for a no-metricLabel row and (b) shows "— 无统计" for `metricLabel=null` per I1-2/S1-3 — `undefined` and `null` are semantically the same "no semantics" state; distinguishing them would be test-gaming explicitly contrary to I1-2 (不得渲染 0/0). Therefore `taskRecordsPaging.test.js` N0-1 **must be updated** to the new rendering — an 11th file, not authorized.

### 5.2 `TaskExecutionController` constructor change breaks 3 more unlisted test files

Plan T1-5 (`/task-types` needs `TaskExecutionRepository.findTaskTypeCounts()` — repository is authorized, but the controller must be wired to it) and T1-6 (`/{id}/detail` needs the extractor, which needs `TaskProgressLogRepository` + `ObjectMapper` per T1-2) require new constructor dependencies on `TaskExecutionController` (currently `(service, objectMapper)`). `TaskExecutionService.kt` is NOT in the authorized list, so the controller cannot route through the service.

Files that construct/load the controller with the current 2-arg signature and are NOT authorized:

1. `src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionControllerTest.kt:20` — `TaskExecutionController(service, objMapper)` → compile break.
2. `src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionListPagingTest.kt:21` — `TaskExecutionController(service, objectMapper)` → compile break.
3. `src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionControllerMvcTest.kt:12-18` — `@WebMvcTest(TaskExecutionController::class)` with only `@MockBean TaskExecutionService`; the new `TaskExecutionRepository` / `TaskProgressLogRepository` / extractor deps are not present in the web slice → context load failure unless new `@MockBean`s are added.

Note: `TaskProgressController` (T1-3) can keep its 4-arg constructor and construct `TaskExecutionSummaryExtractor(progressLogRepository, objectMapper)` internally, so `TaskProgressControllerExecutionsTest` would remain green without edits (its existing `taskType not in whitelist returns 400` case already covers the whitelist behavior T1-8 asks to add). That side is NOT part of the conflict.

### 5.3 Required-command impact

- `JAVA_HOME=…zulu-11… mvn test` (full regression; must be `Tests run: N, Failures: 0, Errors: 0`, exit 0) runs `node --test src/test/js/*.test.js` → N0-1 fails (proven in §5.1) and Kotlin test-compile fails on the 3 controller test files (§5.2). The plan's acceptance "回归：执行主计划「验证命令」节的全量测试命令通过" is therefore unachievable within the authorized 10 files.

### 5.4 Required fix (for the human amendment decision)

To make the plan completable, authorize as additional files (family precedent: amendments A1/A2/A3 in the ledger resolved the same class of conflict by authorizing the affected test files):

1. `src/test/js/taskRecordsPaging.test.js` — update N0-1 fixture/expected HTML to the new rendering (taskTypeLabel col2, S1-3 col5).
2. `src/test/kotlin/.../task/controller/TaskExecutionControllerTest.kt` — adapt constructor call.
3. `src/test/kotlin/.../task/controller/TaskExecutionListPagingTest.kt` — adapt constructor call.
4. `src/test/kotlin/.../task/controller/TaskExecutionControllerMvcTest.kt` — add `@MockBean` for the new deps.

Alternatively, an explicit plan amendment stating a different wiring (e.g., inject the extractor/repository through an authorized path) would also resolve it.

## 6. Commands run (baseline only — implementation stopped at preflight)

| Command | Result |
|---|---|
| `git rev-parse HEAD` / `git branch --show-current` / `git status --porcelain` | HEAD `34bddec…`, branch `fast/2026-08-16-execution-order`, clean index |
| `grep -n '?v=' src/main/resources/static/index.html` | 3× `20260817-v4-task-records-paging` (chain check PASS) |
| `node --test src/test/js/taskRecordsPaging.test.js` | exit 0, 6 pass / 0 fail (baseline at HEAD) |
| `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js` | exit 0, 17 pass / 0 fail (baseline at HEAD) |
| `node --check src/main/resources/static/app.js` | exit 0 (APPJS_OK) |
| N0-1 simulation with plan-mandated rendering | **fails** (proves §5.1) |
| `plan_identity.py` / `worktree_identity.py` | exit 0, identity unchanged at handoff |

Required implementation commands (extractor test, TaskProgressControllerExecutionsTest, taskRecordsSemantics.test.js, full `mvn test`, `git diff --check`) were NOT run because implementation was stopped at preflight per the plan's contingency rule; running them against the unchanged baseline would not exercise any b2 change.

## 7. Deviations / notes

- No implementation commit was created (per plan contingency, STOP and report).
- No files modified; worktree index remains clean.
- The three ⏳ metricLabel items were resolved from actual source (evidence above) as instructed, even though implementation did not proceed.
- `git diff --check` not applicable (no diff). 

---

# B2 Execution Report — Epoch 2 (implemented, READY_FOR_VERIFICATION)

- Executor: ImplementB2E2
- Outcome: **READY_FOR_VERIFICATION**
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast/docs/plans/fast/2026-08-16-execution-order/children/b2/brief.md` (amended per ledger amendment A4, human-approved; full plan text embedded)
- Plan SHA-256: `cdc59e4f6cd26577cc699f29aca841e81e2d34a01611674a278b352ac9a90bca`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast/docs/plans/fast/2026-08-16-execution-order/children/b2/brief.md@cdc59e4f6cd26577cc699f29aca841e81e2d34a01611674a278b352ac9a90bca`
- Execution epoch: RESUME (epoch 2; epoch 1 = PLAN_CONFLICT, no edits; A4 authorized 4 extra test files → 14 total)
- Pre-execution HEAD: `55fb49bb0003380e82fd45fe63bfa40671729185` (controller evidence commits on top of b1 code head `ad005d98b706ceed67b34c96a89e642334ca819a`)

## 1. Chain check (before editing index.html) — PASS

All three `?v=` in `src/main/resources/static/index.html` equaled `20260817-v4-task-records-paging` (b1's value). Bumped to `20260817-v5-task-type-catalog` in the same commit as the three literal assertions in `batchSendTaskConsoleVisualFix.test.js` (M-7 / I1-8 / S1-5).

## 2. metricLabel decisions (carried from epoch 1, landed as decided)

- `INITIAL_OUTREACH` → `null`（— 无统计）
- `AUTO_REPLY_ALL` → `轮询账号/失败账号`
- `OPERATOR_STATUS_RECONCILE` → `一致/异常`

Spot-verified evidence quickly (no re-litigation): `InitialOutreachService.kt:147` `InitialOutreachBatchResult` is NOT a `TaskExecutionSummaryProvider`; `BatchAutoMailReplyService.kt:183-185` provider `successAccountCount/failedAccountCount`; `OperatorStatusReconcileService.kt:303-315` provider `consistent / dbVsExpected+esVsDb`. Locked via `catalog metricLabel decisions are locked` test in TaskExecutionSummaryExtractorTest.

## 3. Changes per authorized file (14/14)

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/.../task/domain/TaskTypeCatalog.kt` (NEW) | `TaskTypeMeta` + `Drilldown` + `TaskTypeCatalog.entries: Map<String,TaskTypeMeta>` — 16 declarations per audit; `hasProgressUi=true` exactly the 6 legacy whitelist types; `summaryRule` non-null for the same 6; `drilldown` all null |
| 2 | `src/main/kotlin/.../task/service/TaskExecutionSummaryExtractor.kt` (NEW) | Migrated `parseResultSummary` / `fallbackFromLog` / `detectWasCancelled` from TaskProgressController; `when` keys switched to `TaskTypeCatalog.byCode(taskType)?.summaryRule`; `ExecutionTotals` promoted to public data class; `extract()` implements I1-3 three-level priority (① resultSummary → ② latest progress_log detailsJson+processedCount → ③ stored success/failure counts) |
| 3 | `src/main/kotlin/.../task/controller/TaskProgressController.kt` | `allowedTaskTypes` derived from catalog (`TaskTypeCatalog.entries.filter { it.value.hasProgressUi }.keys`); deleted the 3 migrated private methods + private `ExecutionTotals`; `getExecutions` delegates to `extractor.extract`/`detectWasCancelled`; constructor unchanged (4 args) so `TaskProgressControllerExecutionsTest` needs no edits; `getProgressLogs` + batchOnly untouched (N1-1) |
| 4 | `src/main/kotlin/.../task/controller/TaskExecutionController.kt` | Constructor gains `taskExecutionRepository` + `extractor`; list DTO adds ONLY `taskTypeLabel` + `metricLabel` (T1-4 self-correction: `summaryText` NOT added); new `GET /task-types` (I1-7, catalog left-join label fallback, count desc); new `GET /{id}/detail` (I1-5, no `require(taskType)`; `NoSuchElementException` → 404; I1-6 32 KB truncation with `rawTruncated`) |
| 5 | `src/main/kotlin/.../task/repository/TaskExecutionRepository.kt` | `TaskTypeCount(taskType, cnt)` projection + `findTaskTypeCounts()` `GROUP BY task_type` query (BatchConfigLastExecution pattern; no TEXT columns, M-1) |
| 6 | `src/main/resources/static/app.js` | `loadTaskTypeOptions()` (S1-1 injection, placeholder kept, selection preserved, cached in `state.taskTypeOptions`); `loadTasks` col2 → `taskTypeLabel || taskType`, col5 → S1-3 (metricLabel null → `— 无统计`, else `n/m <span class="text-muted">label</span>`), first-entry dropdown load (failure non-blocking); `toggleTaskDetail` rewritten to always call `/api/task-executions/{id}/detail` (recent-polls call deleted, N1-3), EXPERT_DISCOVERY keeps bySource table + summaryText renderers via `rawResultSummary`, others → S1-4 JSON fallback via new `renderTaskDetailRawBlocks`; `normalizeDiscoveryResultSummary` / `renderDiscoverySummaryText` / `renderBySourceTable` untouched |
| 7 | `src/main/resources/static/index.html` | S1-1 `#taskTypeFilter` reduced to static placeholder only; S1-2 `#taskStatusFilter` 5 options (PARTIAL_SUCCESS/CANCELLED added); S1-5 three cache keys → `20260817-v5-task-type-catalog` |
| 8 | `src/test/kotlin/.../task/service/TaskExecutionSummaryExtractorTest.kt` (NEW) | 18 tests: 6 summaryRule parses (verbatim migration outputs), RUNNING level-② (2), processedCount-only log fallback, level-③ stored-count fallback, all-empty zeros + null summaryText, malformed JSON no log fallback, wasCancelled (3), catalog: hasProgressUi==6-item set (N1-2), summaryRule keys == extractor rules (reverse drift guard), 16-code coverage, metricLabel decisions locked |
| 9 | `src/test/js/taskRecordsSemantics.test.js` (NEW) | 7 tests: I1-2 `— 无统计` + no `0/0`; S1-3 verbatim cell; col2 taskTypeLabel; S1-4 two `.pre` blocks verbatim; I1-6 truncation notice; S1-1 placeholder/selection/caching; I1-1 no hardcoded option in `#taskTypeFilter` |
| 10 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | ONLY the three cache-key literals v4→v5 (17/17 pass) |
| 11 | `src/test/js/taskRecordsPaging.test.js` | A4 adaptation: N0-1 col5 `4/0` → `<span class="text-muted">— 无统计</span>` for the no-metricLabel fixture; other six columns verbatim; paging assertions untouched (6/6 pass) |
| 12 | `src/test/kotlin/.../task/controller/TaskExecutionControllerTest.kt` | Constructor-call adaptation only: `TaskExecutionController(service, repository, extractor, objMapper)` + extractor mock |
| 13 | `src/test/kotlin/.../task/controller/TaskExecutionListPagingTest.kt` | Constructor-call adaptation only (same pattern) |
| 14 | `src/test/kotlin/.../task/controller/TaskExecutionControllerMvcTest.kt` | `@MockBean` additions only: `TaskExecutionRepository` + `TaskExecutionSummaryExtractor` (no new/changed cases) |

## 4. Commands run (all required, all pass)

| Command | Result |
|---|---|
| `node --check src/main/resources/static/app.js` | exit 0 (APPJS_OK) |
| `node --test src/test/js/taskRecordsSemantics.test.js` | exit 0, 7 pass / 0 fail |
| `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js` | exit 0, 17 pass / 0 fail |
| `node --test src/test/js/taskRecordsPaging.test.js` | exit 0, 6 pass / 0 fail (A4-adapted N0-1) |
| `JAVA_HOME=…zulu-11… mvn test -Dtest=TaskExecutionSummaryExtractorTest` | exit 0, Tests run: 18, Failures: 0, Errors: 0, BUILD SUCCESS |
| `JAVA_HOME=…zulu-11… mvn test -Dtest=TaskProgressControllerExecutionsTest` | exit 0, Tests run: 26, Failures: 0, Errors: 0, BUILD SUCCESS (incl. both batchOnly cases, N1-1) |
| `JAVA_HOME=…zulu-11… mvn test` (full regression incl. node --test + node-check) | exit 0, Kotlin Tests run: 2488, Failures: 0, Errors: 0, Skipped: 4; JS node-test/check executions pass; BUILD SUCCESS |
| `git diff --check` | clean (no output) |

## 5. Acceptance-spot greps

- I1-4: `grep -c "when (taskType)" TaskProgressController.kt` = **0**.
- I1-1: `allowedTaskTypes` assignment derives from `TaskTypeCatalog.entries.filter { it.value.hasProgressUi }.keys`.
- I1-5: `require(` hits in TaskExecutionController are only pre-existing `recentPolls` (limit) and `pollDetail` (AUTO_REPLY_ALL check, N1-3 untouched); none inside `/{id}/detail`.
- I1-8/S1-5: `20260817-v5-task-type-catalog` = 3 in index.html, 3 in batchSendTaskConsoleVisualFix.test.js.
- M-3: no `INITIAL_OUTREACH|AUTO_REPLY_ALL|EXPERT_ENRICHMENT` in the `view-tasks` section of index.html.
- S1-*: commit contains no styles.css changes (no diff on `src/main/resources/static/styles.css`); no migration files touched.

## 6. Deviations / notes

- None in behavior. Implementation notes:
  - `TaskProgressController` keeps its original 4-arg constructor and builds `TaskExecutionSummaryExtractor(progressLogRepository, objectMapper)` internally so `TaskProgressControllerExecutionsTest` compiles/passes unchanged (its existing `taskType not in whitelist returns 400` covers the T1-8 whitelist case; no new test added there — not an authorized file).
  - `loadTasks` guards the first-entry dropdown fetch with `if (!state.taskTypeOptions)` + non-blocking try/catch; this keeps `taskRecordsPaging.test.js`'s sandbox (which lacks `loadTaskTypeOptions`) functioning with zero sandbox changes — the only modification to that file is the N0-1 col5 assertion.
  - Detail endpoint reads the execution via `taskExecutionRepository.findById(...).orElseThrow { NoSuchElementException(...) }` (not `service.getExecution`, which throws `IllegalStateException` → 400) to satisfy the 404 mapping (I1-5).
  - Extractor level ② uses `processedCount` (not `totalCount`) for `totalProcessed`, preserving the migrated `fallbackFromLog` behavior verbatim (plan's own 复核修正 note).
  - `EXPERT_DISCOVERY` detail keeps the bySource table + summaryText via `detail.rawResultSummary` parsed by the existing `normalizeDiscoveryResultSummary` (three existing renderer functions untouched).
- Worktree: only the 14 authorized files changed; `docs/plans/fast/` changes (this file) are NOT part of the implementation commit — left uncommitted for the controller's separate evidence commit.
- Commit: exactly one implementation commit `feat(fast-p): implement b2` (SHA recorded by controller); not pushed.
