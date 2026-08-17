# Child 06 Execution Report — 批量发送任务的可达性过滤配置

- Child: 06
- Plan: docs/plans/2026-08-16/expert-reachability-06-batch-config.md
- Plan identity (amended): commit:fb184c4510964449e15928e432ccecc07c794c77 (amendment A4, human-approved), sha256 `e3c9ce3ea1e3a7e988fd1b484993bd728ce138be73a95a0a0b42e16120f486fc`
- Base (child 05 terminal code head): a591cb7972bd7838cead435a496f90e095817bc1
- Executor: Reachability06Implementer (execute-p)
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-reachability @ fast/expert-reachability
- Result: READY_FOR_VERIFICATION
- Implementation commit: `feat(fast-p): implement 06`

## What changed (per task)

| Task | File | Change |
|------|------|--------|
| T1 | `src/main/resources/db/migration/V100__add_reachability_filter_to_batch_send_task_config.sql` (NEW) | `ALTER TABLE batch_send_task_config ADD COLUMN reachability_filter VARCHAR(32) NULL COMMENT '可达性过滤模式：NULL=不过滤';` — verbatim per plan. NULL default = 不过滤 (I-6-5); no `${` (I-6-3, grep count 0). Latest migration was V99 (R-11 reconfirmed), so V100 is correct. |
| T2 | `BatchSendTaskConfig.kt` | `BatchSendTaskConfig.reachabilityFilter: String? = null`; `BatchSendTaskConfigView.reachabilityFilter: String? = null`; `BatchSendTaskConfigCreateCommand.reachabilityFilter: String? = null`; `BatchSendTaskConfigUpdateCommand.reachabilityFilter: String? = null` (all defaulted, I-6-5). View + CreateCommand are required by I-6-2 ("toView() 要加" + "三个 *Fields() 要加" — CreateCommand.toFields() cannot carry the column without the command field). |
| T3 | `BatchSendTaskConfigService.kt` | `toView()` adds `reachabilityFilter = row.reachabilityFilter` (:464); all three `*Fields()` (:598/:618/:638) add `reachabilityFilter = reachabilityFilter`; `create()` entity construction (:80) and `update()` existing.copy (:115) carry `reachabilityFilter = normalized.reachabilityFilter`; `ConfigFields` (:557) + `NormalizedConfig` (:578) gain the field. `toLegacyConfig()` and `updateLegacyConfig` return construction untouched (I-6-2 / N-3). |
| T4 | `BatchSendTaskConfigService.kt` | I-6-1 preservation line after the gateFilterEnabled line in `updateLegacyConfig` (:200): `// 同 M-2：旧 typed API 不传可达性过滤，必须显式保留存量值（漏写会命中默认值静默重置）。 reachabilityFilter = existing.reachabilityFilter`. |
| T5 | `BatchSendTaskConfigService.kt` | Validation in `normalizeAndValidate` (:297-303): trim, empty→null (I-6-5); non-empty must be in `ExpertSearchService.ALLOWED_REACHABILITY_MODES` (I-6-4 — single source, no second string set), else `IllegalArgumentException` (→ 400). |
| T6 | `BatchExecutionModels.kt` (A4-authorized, exactly 3 additive lines) + `ManualInitialOutreachService.kt` (zero-diff, p4a precedent) | `BatchExecutionSnapshot.reachabilityFilter: String? = null`; `toExecutionSnapshot()` passthrough `reachabilityFilter = reachabilityFilter`; `RecipientScope.fromSnapshot()` passthrough `reachabilityFilter = snapshot.reachabilityFilter`. `resolveScope(snapshot)` constructs RecipientScope solely via `fromSnapshot`, so the config value now flows config → snapshot → scope → `buildEsFiltersForLevel` / `buildMaterialReminderEsFilters` / `matchesExpert` (IP-1 wired; Observable outcome 2; A-3). The two direct RecipientScope constructions in ManualInitialOutreachService (legacy KV INTRODUCTION retry :1023, KV MATERIAL stats :1166) are unchanged (O-2 / N-3 — KV layer has no column). |
| T7 | `index.html` + `app.js` | Both editors gain the S-6-1 3-option select (`batchConfigEditorReachabilityFilter` after `editorFieldGateFilter`; `batchManualReachabilityFilter` after `manualFieldGateFilter`, with diff-badge/original divs matching the manual-editor field pattern). app.js replicates all 12 gateFilterEnabled roles (R-13) plus the 3 diff-panel integration points: pill render `可达性 · <label>` via `.batch-gate-pill` reuse, only when non-empty (S-6-2, :13455); editor fill (:13600); config-editor preview snapshot (:14332); manual-execution snapshot (:14353, `|| undefined` so the empty case serializes exactly like baseline — I-2 node-test contract); save payload (:14472); deepCloneConfig (:14557); manual defaults (:14579); manual fill (:14603); readManualFormValues (:14688); normalizeManualSnapshot (:14707); formatManualDiffValue branch (:14718); computeManualDiffs fieldDefs (:14758); fieldMap (:14806); clearAllDiffMarkers list. Shared `BATCH_REACHABILITY_LABELS` + `batchReachabilityFilterLabel()` helper (covers EXCLUDE_BLOCKED/HIGH_ONLY/UNKNOWN_ONLY/BLOCKED_ONLY + fallback). No new CSS class, no inline style, styles.css zero diff (S-6-1/S-6-2). |
| T8 | `BatchSendTaskConfigReachabilityTest.kt` (NEW) + `BatchSendTaskConfigServiceTest.kt` | New file, 6 tests: create default null (I-6-5); update persists + toView/get 透出 (I-6-2); create illegal value 400/IllegalArgumentException (I-6-4); update illegal value no-save (I-6-4); legacy typed update preserves existing column — I-6-1 core case; legacy typed response JSON does not contain `reachabilityFilter` (I-6-2/N-3). Existing file: `createCmd`/`updateCmd`/`row` helpers gain `reachabilityFilter` param; `updateLegacyConfig writes entity row preserves name funnel tags and publishes reload` gains entity `reachabilityFilter = "HIGH_ONLY"` + survival assertion (补断言). |

## Commands (all run fresh, final state, JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home)

| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/app.js` | PASS (exit 0) | `NODE_CHECK_OK` |
| `mvn test -Dtest=BatchSendTaskConfigReachabilityTest` | PASS | BUILD SUCCESS, `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0` |
| `mvn test -Dtest=BatchSendTaskConfigServiceTest` | PASS | BUILD SUCCESS, `Tests run: 54, Failures: 0, Errors: 0, Skipped: 0` |
| `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | **DOCKER-GATED FAILURE (recorded, not faked)** | `Tests run: 1, Failures: 0, Errors: 1` — `IllegalStateException: Docker is required for Flyway migration tests`. Environment evidence: `/var/run/docker.sock` is a dangling symlink → missing `/Users/lukai/.orbstack/run/docker.sock`; testcontainers reports "Could not find a valid Docker environment". Same environment state as child 05's recorded SKIPPED. Test is `@EnabledIfSystemProperty(migrationIt=true)`, so the plain full suite skips it (counted in Skipped: 4). |
| `mvn test` (full regression) | PASS (exit 0) | BUILD SUCCESS, `Tests run: 2515, Failures: 0, Errors: 0, Skipped: 4` (2509 baseline after children 02-05 + 6 new = 2515; skipped 4 unchanged, incl. the migration IT disabled by property). Node test phase (exec-maven-plugin node-test, `node --test src/test/js/*.test.js`) green: 584 pass / 0 fail. |
| `git diff --check` | PASS | `DIFF_CHECK_OK` (exit 0) |

## Acceptance criteria (plan 验收标准)

- I-6-1: `grep -n "reachabilityFilter = existing.reachabilityFilter"` → exactly 1 hit (:200) inside `updateLegacyConfig`; unit test `legacy typed update preserves existing reachabilityFilter (I-6-1 core)` green.
- I-6-2: hits in `toView` (:464) and three `*Fields()` (:598/:618/:638); none in `toLegacyConfig` / `updateLegacyConfig` return construction (region :202-240 clean).
- I-6-3: `grep -c '\${' V100__*.sql` → 0.
- I-6-4: `ALLOWED_REACHABILITY_MODES` defined 1× (ExpertSearchService:242), referenced ≥2× (ExpertSearchService:262, BatchSendTaskConfigService:302); no own string set in BatchSendTaskConfigService.
- I-6-5: V100 column `VARCHAR(32) NULL` with no non-empty DEFAULT; entity/commands default null; tests assert `reachabilityFilter == null` on create.
- N-3: `BatchSendConfig` (BatchSendSettingService.kt:240) zero diff.
- S-6-1/S-6-2: `git diff` of index.html adds no CSS class definition and no inline style; `styles.css` zero diff; `.batch-gate-pill` reused in place (`grep -rn "batch-gate-pill"` usage points: app.js :13432/:13433/:13434/:13456 — the new pill is a reuse, no style change).
- Guard test: `OperatorStatusWriteSeamGuardTest` green in full suite (no pinned file was edited; T6's fromSnapshot line adds no `operatorStatus = ` pattern). Known-risk check: no stale pins — the guard's EXCLUDED_NOISE_SITES files (UnmatchedInboundMailController, MailboxService, ExpertContactManagementController, ExpertIndexController, ExpertSearchService, ExpertContactRepository, MailRecordRepository) are all untouched by this child.

## Deviations / notes

- **Mid-run amendment A4** (human-approved via Main): authorized 9th file `BatchExecutionModels.kt` for exactly 3 additive lines (snapshot field + toExecutionSnapshot passthrough + fromSnapshot passthrough). Discovered during T6 analysis: `resolveScope(snapshot)` builds RecipientScope only via `RecipientScope.fromSnapshot(snapshot)`; without the snapshot carrier the config value died at the Jackson/snapshot boundary and T6 would have been a silent no-op. Same pattern as the p4a `gateFilterEnabled` snapshot carrier.
- `ManualInitialOutreachService.kt` (authorized #4) is zero-diff — T6 is fully realized by the fromSnapshot passthrough; documented precedent: p4a's BatchSendControlService "authorized but no change needed".
- O-2 note: the plan's S-6-1 mandates the select in both editors and the 12-role replication carries it in the manual-execution snapshot, so a manual run whose operator actively selects a mode honors it (superset of O-2's minimal claim; independent runs default to 不过滤 = zero behavior change, N-4/I-5-4 regression-safe).
- `buildManualExecutionSnapshot` uses `values.reachabilityFilter || undefined` (not `|| null`) so the empty case serializes byte-identically to baseline — required by the existing node test contract "uses one complete manual snapshot for preview and execution (I-2)" (`src/test/js/batchSendTaskConsoleInteraction.test.js:1043`, JSON.stringify drops undefined but keeps null). Node suite: 584 pass / 0 fail. The node test files are outside the authorized scope and were NOT modified.
- `BatchSendTaskConfigCreateCommand` and `BatchSendTaskConfigView` gained the field beyond T2's explicit two — required by I-6-2's "三个 *Fields() 要加" and "toView() 要加" (CreateCommand.toFields()/View mapping cannot carry the column otherwise; create-mode save would silently drop it).
- FlywayMigrationIntegrationTest result recorded as DOCKER-GATED FAILURE (environment lacks Docker); not faked. In the plain full suite it is disabled by `@EnabledIfSystemProperty(migrationIt=true)` and counts toward Skipped: 4.
- Commit excludes docs/plans/fast/ (amendment + ledger commits were made by the controller; this report is evidence handled by the controller).

## Implementation commit

- `git add` only the 9 authorized files; `git commit -m "feat(fast-p): implement 06"` — single implementation commit; no push/merge/rebase/amend.
