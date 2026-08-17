# B5 Execution Report — PLAN_CONFLICT (implementation complete per 11 authorized files; regression blocked on one unlisted lock test)

- Executor: ImplementB5
- Outcome: **PLAN_CONFLICT** — the human-approved 11-file contract (amendment A5, T3-6 inline) is fully implemented and all focused tests pass, but the plan-mandated catalog entry (T3-6) breaks three exact-equality lock assertions in `src/test/kotlin/.../task/service/TaskExecutionSummaryExtractorTest.kt`, which is **NOT** in the authorized 11-file list. The required full regression (`mvn test`, criterion `Tests run: N, Failures: 0, Errors: 0`, exit 0) therefore cannot pass within the authorized scope. Per family precedent (b2 epoch 1 → amendment A4) and execute-p ("do not edit an unlisted test file"), this needs a human amendment, not self-authorized scope expansion.
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast/docs/plans/fast/2026-08-16-execution-order/children/b5/brief.md` (complete approved contract, full amended plan text embedded)
- Plan SHA-256: `10c900a61c34deb7118e447f41f31f6859116134a30f9761bd54cd2b4d017cec`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast/docs/plans/fast/2026-08-16-execution-order/children/b5/brief.md@10c900a61c34deb7118e447f41f31f6859116134a30f9761bd54cd2b4d017cec`
- Execution epoch: NEW
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast@fast/2026-08-16-execution-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast`
- Pre-execution code SHA: `d32ccb282d88a6e6182bb579acbc0b65d74995eb` (b4 terminal Code head; HEAD `84a84c4…` = b5 brief/ledger docs commit on top, code tree identical)
- Post-execution code SHA: [implementation commit SHA — see below]
- Implementation boundary: `d32ccb2..<implementation commit>` (11 files, `src/` + `src/test/` only)
- Worktree index: clean after commit; `docs/plans/fast/` excluded from the implementation commit (controller commits evidence separately)

---

## 1. Verify-first items — resolutions (all resolved BEFORE editing)

### 1.1 @Modifying + DELETE spike (plan T3-3) — SUPPORTED, affected-rows return confirmed

**Static evidence** (the exact library in this repo's dependency tree, `spring-data-jdbc-2.4.18`):
- `AbstractJdbcQuery.getQueryExecution` first branch: `if (queryMethod.isModifying()) return createModifyingQueryExecutor()` — no SQL-type inspection.
- `AbstractJdbcQuery.lambda$createModifyingQueryExecutor$0` bytecode: calls `NamedParameterJdbcOperations.update(sql, params)` → `int` affected rows; returns `Integer.valueOf(affectedRows)` when the method declares `int`/`Integer`, or `Boolean(affectedRows > 0)` when it declares `boolean`/`Boolean`. The executor is **DML-agnostic** — DELETE is executed exactly like the existing UPDATE precedents.

**Runtime evidence** (minimal spike, real DB): temporary `ModifyingDeleteSpikeTest.kt` (deleted after the spike, NOT part of the implementation) booted a minimal `@EnableJdbcRepositories` + `AbstractJdbcConfiguration` context against a scratch local MySQL 5.7 (homebrew `mysql@5.7`, port 3307, scratch datadir `/tmp/b5-spike`), with:
```kotlin
@Modifying
@Query("DELETE FROM spike_t WHERE created_at < :cutoff ORDER BY created_at LIMIT :batchSize")
fun deleteOlderThan(cutoff: LocalDateTime, batchSize: Int): Int
```
Seeded 5 rows (3 old / 2 recent); sequential calls returned **2 → 1 → 0** (affected-rows semantics + LIMIT batching + `ORDER BY` accepted by MySQL 5.7 single-table DELETE); `SELECT COUNT(*)` confirmed only the 2 recent rows survived. Test report: `Tests run: 1, Failures: 0, Errors: 0` (BUILD SUCCESS).

**Conclusion**: primary approach viable — no fallback to `NamedParameterJdbcTemplate` needed. This repo now has a DELETE precedent via `@Modifying`+`@Query`; recorded for the plan's intended knowledge entry `K-modifying-delete-precedent` (knowledge write-back to the MAIN worktree is the controller's Phase-6 job, per the brief).

### 1.2 batch_reject_reasons_json column check — NO DEFECT (plan audit note ③ outdated)

`grep` of `src/main/resources/db/migration/`:
- `V35__add_task_progress_batch_reject_reasons.sql:2` — `ALTER TABLE task_progress_log ADD COLUMN batch_reject_reasons_json TEXT NULL COMMENT '本批次拒绝原因明细(JSON: reason->count)'`.
- Entity `task/domain/TaskProgressLog.kt:20` — `val batchRejectReasonsJson: String? = null`.

The column **exists** (added by V35, well before V22-check-only reasoning in the plan's audit note ③). The plan's hypothesis ("若确无该列而实体有该属性，是既有缺陷") does **not** hold → **record-only observation, nothing to fix** (out of scope, no task created).

### 1.3 application.yml `spring.flyway.placeholder-replacement: false` — PRESENT, untouched

Verified at lines 8-13 of `application.yml` (comment block + `placeholder-replacement: false`). The new `talent-introduction.task-retention` section was inserted under `talent-introduction:` (after `scheduling:`, before `postmaster:`); the flyway block was not modified. Regression-asserted by `TaskRetentionMigrationTest`.

### 1.4 TaskRetentionProperties registration — `@EnableConfigurationProperties` on the scheduler (authorized file), NOT RestTemplateConfig

`MailSchedulingProperties` is registered via `@EnableConfigurationProperties` in `config/RestTemplateConfig.kt` (23-class list). RestTemplateConfig is **not** among the 11 authorized files, and there is no `@ConfigurationPropertiesScan`. To satisfy the plan's binding requirement ("配置类被 @ConfigurationPropertiesScan 或 @EnableConfigurationProperties 纳入") **within** the authorized scope, `TaskAuditRetentionScheduler` carries `@Component @Configuration @EnableConfigurationProperties(TaskRetentionProperties::class)` — the same mechanism (`@EnableConfigurationProperties`) as MailSchedulingProperties, co-located with the consumer; the dual `@Component @Configuration` annotation follows the repo's own BounceCollectionScheduler precedent. Binding verified at runtime by the app-context-free unit tests (direct construction) and by property binding conventions; no startup context test exists for it (out of scope).

### 1.5 I3-4 deletion order + I3-1 created_at-only condition — implemented as specified

- `TaskAuditRetentionService.purge()`: `task_progress_log` purge loop runs first, `task_execution` second (I3-4), each wrapped in its own try/catch (I3-6).
- `TaskProgressLogRepository.deleteOlderThan`: `DELETE FROM task_progress_log WHERE created_at < :cutoff ORDER BY created_at LIMIT :batchSize` — created_at-only, no JOIN / EXISTS / task_execution_id (I3-1 / M-6), no task_type filter (I3-5).
- `TaskExecutionRepository.deleteOlderThan`: `DELETE FROM task_execution WHERE started_at < :cutoff ORDER BY started_at LIMIT :batchSize` (I3-3, idx_te_started).
- Cutoff computed once per run: `LocalDateTime.now(TaskExecutionService.SHANGHAI).minusDays(props.retentionDays)` — Asia/Shanghai, shared by both tables (I3-3).

### 1.6 I3-5 no self-exemption + T3-5 runAndRecordWithResult + provider — implemented as specified

- Delete SQL carries no `task_type` exclusion; the scheduler's own RUNNING row is protected by `started_at < cutoff` (90-day-old boundary vs. current started_at).
- `TaskAuditRetentionScheduler.scheduleRetention()` uses `runAndRecordWithResult("TASK_AUDIT_RETENTION", "SCHEDULED", "task-audit-retention") { retentionService.purge() }`.
- `RetentionResult(progressLogDeleted, executionDeleted, failedTables)` implements `TaskExecutionSummaryProvider` (`taskSuccessCount = progressLogDeleted + executionDeleted`, `taskFailureCount = failedTables`, `taskFinalStatus` = FAILED/PARTIAL_SUCCESS/SUCCESS for failedTables 2/1/0) — NOT `TaskResultSummary.from()` reflection (which would yield 0/0).

---

## 2. Changes per authorized file (11/11)

| # | File | Change | Verification |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V102__add_task_progress_log_created_at_index.sql` (NEW) | `CREATE INDEX idx_tpl_created_at ON task_progress_log (created_at);` + plan-mandated comment; no `${` | `TaskRetentionMigrationTest` (2 tests); manual MySQL 5.7 apply: `SHOW INDEX` shows `idx_tpl_created_at` on `created_at` |
| 2 | `src/main/kotlin/.../config/TaskRetentionProperties.kt` (NEW) | `@ConstructorBinding @ConfigurationProperties("talent-introduction.task-retention")` data class: enabled=false / cron="0 30 4 * * *" / retentionDays=90 / batchSize=2000 / maxRowsPerRun=200000 | compiled; consumed by service+scheduler tests |
| 3 | `src/main/resources/application.yml` | `talent-introduction.task-retention:` 5 keys with env-var defaults matching the plan; flyway `placeholder-replacement: false` untouched | `TaskRetentionMigrationTest` placeholder regression |
| 4 | `src/main/kotlin/.../task/repository/TaskExecutionRepository.kt` | +`deleteOlderThan(cutoff, batchSize): Int` `@Modifying @Query` on `started_at < :cutoff ORDER BY started_at LIMIT :batchSize` | spike (same pattern, runtime) + `TaskRetentionMigrationTest` text assertion (`started_at <`, NOT `created_at <`) |
| 5 | `src/main/kotlin/.../task/repository/TaskProgressLogRepository.kt` | +`import java.time.LocalDateTime` + `deleteOlderThan(cutoff, batchSize): Int` `@Modifying @Query` on `created_at < :cutoff ORDER BY created_at LIMIT :batchSize` | spike (same pattern, runtime) + `TaskRetentionMigrationTest` text assertion (no JOIN/EXISTS/task_execution_id/task_type) |
| 6 | `src/main/kotlin/.../task/service/TaskAuditRetentionService.kt` (NEW) | `purge()` (I3-1..I3-6) + private `purgeLoop` (batch loop until 0 or maxRowsPerRun) + `RetentionResult` implementing `TaskExecutionSummaryProvider` | `TaskAuditRetentionServiceTest` (7 service tests) |
| 7 | `src/main/kotlin/.../task/service/TaskAuditRetentionScheduler.kt` (NEW) | `@Component @Configuration @EnableConfigurationProperties(TaskRetentionProperties::class)`; `@Scheduled(cron = "${talent-introduction.task-retention.cron:-}")`; `if (!props.enabled) return`; `runAndRecordWithResult("TASK_AUDIT_RETENTION", "SCHEDULED", "task-audit-retention")`; no executor (N3-6: `grep -n "manualOutreachExecutor\|Executor"` → no matches) | `TaskAuditRetentionServiceTest` (2 scheduler tests: disabled verify-never / enabled verify-called) |
| 8 | `src/main/kotlin/.../task/domain/TaskTypeCatalog.kt` | +1 entry `TASK_AUDIT_RETENTION` → label 任务审计清理 / group SCHEDULED / metricLabel 删除行数/失败表数 / summaryRule TASK_AUDIT_RETENTION / hasProgressUi false / drilldown null; doc comment 16→17 | compiled; extractor branch test path in full run |
| 9 | `src/main/kotlin/.../task/service/TaskExecutionSummaryExtractor.kt` | +`"TASK_AUDIT_RETENTION"` branch in `parseResultSummary`: `totalProcessed = progressLogDeleted + executionDeleted + failedTables`; `totalPassed = progressLogDeleted + executionDeleted`; `totalRejected = failedTables` | compiled; full-run extractor suite green apart from the 3 catalog-lock assertions (see §5) |
| 10 | `src/test/kotlin/.../task/service/TaskAuditRetentionServiceTest.kt` (NEW) | 9 tests: I3-3 cutoff (Asia/Shanghai, 90d), I3-2 batching (2000,2000,137,0 → 4 calls / 4137), I3-2 maxRowsPerRun early stop, I3-4 InOrder, I3-6 PARTIAL_SUCCESS, I3-6 FAILED, I3-5 arg-capture no task_type, N3-5 disabled verify-never, N3-5 enabled verify-called | `mvn test -Dtest=TaskAuditRetentionServiceTest` → 9 run / 0 fail / 0 err |
| 11 | `src/test/kotlin/.../task/service/TaskRetentionMigrationTest.kt` (NEW) | 5 text-assertion tests: V102 index, V102 no `${`, progress-log delete SQL (created_at <, no JOIN/EXISTS/task_execution_id/task_type), execution delete SQL (started_at <, not created_at), yml placeholder-replacement: false | `mvn test -Dtest=TaskRetentionMigrationTest` → 5 run / 0 fail / 0 err |

No frontend files, no other migration files, no cache-key changes (pure backend — not applicable).

---

## 3. Commands run (all required commands executed; exit codes / counts)

| Command | Result | Evidence |
|---|---|---|
| `python3 …/execute-p/scripts/plan_identity.py …/children/b5/brief.md` | PASS (exit 0) | sha256 `10c900a6…17cec`; rechecked at handoff, unchanged |
| `python3 …/execute-p/scripts/worktree_identity.py …/children/b5/brief.md --worktree …-fast` | PASS (exit 0) | branch `fast/2026-08-16-execution-order`, git_dir `…/.git/worktrees/weibo-talent-introduction-fast`; rechecked at handoff, unchanged |
| `git diff d32ccb2 HEAD -- src pom.xml` | empty | code tree identical to b4 code head (docs-only commits on top) |
| @Modifying+DELETE spike: `JAVA_HOME=…zulu-11… mvn test -Dtest=ModifyingDeleteSpikeTest` (temporary file, deleted after) | PASS (exit 0) | Tests run: 1, Failures: 0, Errors: 0 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskAuditRetentionServiceTest` | PASS (exit 0) | Tests run: 9, Failures: 0, Errors: 0, BUILD SUCCESS |
| `JAVA_HOME=…zulu-11… mvn test -Dtest=TaskRetentionMigrationTest` | PASS (exit 0) | Tests run: 5, Failures: 0, Errors: 0, BUILD SUCCESS |
| `JAVA_HOME=…zulu-11… mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | **UNEXECUTED** (allowed by brief) | local Docker unavailable: `docker version` → cannot connect to `unix:///Users/lukai/.orbstack/run/docker.sock` (daemon not running); test uses Testcontainers `MySQLContainer`, which requires the Docker daemon. Substitute evidence: manual V102 apply against scratch MySQL 5.7 succeeded (see §1.5/§2 #1). |
| `JAVA_HOME=…zulu-11… mvn test` (full regression) | **FAIL (exit 1)** — see §5 | Kotlin/Java: Tests run: 2513, Failures: 3, Errors: 0, Skipped: 4; the 3 failures are all in `TaskExecutionSummaryExtractorTest` catalog-lock assertions (unlisted file). Build aborts at surefire, so the exec-plugin JS step did not run in the aborted build; JS run standalone below. |
| `node --test src/test/js/*.test.js` (standalone, main-plan command) | PASS (exit 0) | tests 630, pass 630, fail 0 (suites 99) |
| `git diff --check` | PASS (exit 0) | no output |

---

## 4. Docker status

Docker/OrbStack daemon is **not running** (`docker version` fails to connect). `FlywayMigrationIntegrationTest -DmigrationIt=true` recorded as **unexecuted** per the brief's explicit allowance. Scratch local MySQL 5.7 (homebrew) was started only for the spike + manual V102 check and has been **stopped and cleaned up** (`/tmp/b5-spike` removed); no residue on the machine.

---

## 5. ❗ PLAN_CONFLICT — root cause with evidence

### 5.1 The conflict

Plan T3-6 (human-approved amendment A5, 11-file complete path, **inline** catalog registration) mandates:
- `TaskTypeCatalog.entries` gains `TASK_AUDIT_RETENTION` (summaryRule `TASK_AUDIT_RETENTION`, metricLabel 删除行数/失败表数) → catalog 16 → **17** entries.

The existing test `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt` (b2-owned, **not** in the 11-file authorized list) locks the catalog with three exact-equality assertions:

1. `catalog covers the sixteen audited task types` (:219) — `assertEquals(auditedCodes(16), TaskTypeCatalog.entries.keys)` → now 17 keys. FAILED.
2. `catalog metricLabel decisions are locked` (:235) — `assertEquals(16, TaskTypeCatalog.entries.size)` → now 17. FAILED.
3. `catalog summaryRule keys match the extractor rule set` (:200) — `assertEquals(setOf(6 rule keys), ruleKeys)` → now 7 (TASK_AUDIT_RETENTION added). FAILED.

Full regression evidence (fresh run, exit 1): `Tests run: 2513, Failures: 3, Errors: 0` — the ONLY failures are these three; all other 2510 tests (including the entire B1–B4 surface: `TaskExecutionListPagingTest`, `TaskProgressControllerExecutionsTest` incl. both batchOnly cases, `TaskExecutionServiceTest`, JS 630/630) pass. There is **no implementation that satisfies T3-6 while keeping these exact-equality locks green** (any catalog addition breaks #1 and #2; the mandated summaryRule breaks #3). This is not a fixable defect — it is the plan's own change colliding with a lock test it did not authorize.

### 5.2 Why not self-authorized

- Brief/controller: "Modify ONLY the 11 Authorized Files … modify nothing else."
- execute-p: "Do not: Edit an unlisted implementation or test file"; "If completion requires an unlisted file or a new behavioral decision, stop with PLAN_CONFLICT."
- Family precedent (same ledger): **b2 epoch 1** hit the identical class (plan-mandated change breaking unlisted existing tests) and stopped with PLAN_CONFLICT; the human then approved **amendment A4** authorizing the affected test files (→ 14 files), and b2 epoch 2 completed. The designed loop for this exact conflict is PLAN_CONFLICT → human amendment, not executor scope expansion.

### 5.3 Required fix (minimal amendment request)

Authorize **one additional test file**: `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt` (12th file), with three uniquely-determined mechanical updates that preserve the lock semantics:
1. `catalog covers the sixteen audited task types` → rename/`auditedCodes` set += `"TASK_AUDIT_RETENTION"` (17).
2. `catalog metricLabel decisions are locked` → `assertEquals(16, …size)` → `17`; optionally add `assertEquals("删除行数/失败表数", meta("TASK_AUDIT_RETENTION").metricLabel)`.
3. `catalog summaryRule keys match the extractor rule set` → expected set += `"TASK_AUDIT_RETENTION"` (7).

No other files are affected; with that amendment the full regression criterion (`Tests run: N, Failures: 0, Errors: 0`, exit 0) is achievable — the 3 assertions are the only delta (proven by the fresh run).

---

## 6. Deviations / notes

- **Registration point (verify-first 1.4)**: `TaskRetentionProperties` is registered via `@EnableConfigurationProperties` on `TaskAuditRetentionScheduler` (authorized file), not in `RestTemplateConfig.kt` (unauthorized). Same mechanism as `MailSchedulingProperties`; dual `@Component @Configuration` follows the BounceCollectionScheduler precedent.
- **N3-5 scheduler test placement**: the plan's acceptance criteria require a "verify never" scheduler test, but the 11-file list has no scheduler-test file; it was placed in `TaskAuditRetentionServiceTest.kt` (authorized file 10, same package) — the only in-scope host. If the amendment in §5.3 is approved, this could alternatively be split out; no change required.
- `RetentionResult` property names are `progressLogDeleted` / `executionDeleted` / `failedTables` so the `result_summary` JSON keys match the plan's T3-6 extractor branch (`root.progressLogDeleted` etc.).
- No implementation commit was possible while §5 remains open? — NO: the 11-file implementation IS committed (see §7) per the controller's explicit commit instruction; the amendment, if approved, would be a small follow-up commit on the same branch (local only).
- `docs/plans/fast/` (this report) is excluded from the implementation commit, per the brief.

---

## 7. Commit

- One implementation commit, local only, not pushed:
  - Subject: `feat(fast-p): implement b5`
  - SHA: [see commit output]
  - Files: exactly the 11 authorized files (`src/` + `src/test/`); `docs/plans/fast/` excluded.

## 8. Freshness

- Plan identity rechecked: YES (unchanged at handoff)
- Worktree identity rechecked: YES (unchanged at handoff)
- Reported commit reachable from target branch: YES (HEAD of `fast/2026-08-16-execution-order`)
- Required commands run this invocation: YES (all; FlywayMigrationIntegrationTest allowed-unexecuted, Docker absent)
- Historical evidence used only as baseline: YES

## 9. Remaining blocker

One human decision: authorize `TaskExecutionSummaryExtractorTest.kt` as a 12th file with the three lock-set updates in §5.3 (or an equivalent plan amendment). Until then the full-regression gate cannot pass and the outcome is PLAN_CONFLICT.

## Fast-P Evidence (controller note)

- Terminal verdict: LIGHT_PASS (epoch 2, ReVerifyB5)
- Fix rounds: 1 (4d7f206a4f506104af73f3e63e4fceea3d857ef7, A6-authorized T3-8 catalog lock updates)
- Evidence commit: f73e55cc2d263192f80d49e29e349055846ff154 (corrected in successor commit to include this file)
- RECORD_ONLY: O-1 stale "16 种" comment (out of T3-8 scope), O-2 FlywayMigrationIntegrationTest unexecuted (no Docker), O-3 TaskRetentionProperties registration mechanism family

Controller note: terminal evidence consolidated.
