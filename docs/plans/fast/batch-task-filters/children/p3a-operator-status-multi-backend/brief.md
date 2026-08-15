# Fast-P Child Brief: p3a-operator-status-multi-backend

## Authority
- Master plan: `docs/plans/2026-08-15/batch-task-filters-main.md` (commit 72ea4f55)
- Child plan: `docs/plans/2026-08-15/p3a-operator-status-multi-backend.md` (commit 72ea4f55) — **the complete contract. Read it in full before implementing** (also read master plan M-1..M-5, X-1..X-3).
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters`
- Branch: `fast/batch-task-filters`
- Child base SHA: `<p2a code head — recorded in the dispatch prompt>`

## Prerequisite (P2a, verified/committed on branch)
P2a delivered: V96 migration taken (next free version is **V97**), `buildEsFiltersForLevel` now multi-domain shape — `notContactedWithEmailDomainsFilters(scope.emailDomains, scope.discipline)`, `emailDomainsFilter(scope.emailDomains)`, `RecipientScope.emailDomains`. Build on that exact shape; do not re-verify.

## Global constraints (master plan, binding)
- **M-1**: both target sources — `buildEsFiltersForLevel` + `RecipientScope.matchesExpert`. Exactly 2 live sources (X-1; `buildMaterialReminderEsFilters` dead — do NOT touch).
- **M-2**: `updateLegacyConfig` must preserve `operatorStatuses = parseOperatorStatuses(existing.operatorStatusesJson)`.
- **M-3**: full mapping set (4 data classes, service create/update/toView/ConfigFields/NormalizedConfig/3×`*Fields()`/normalizeAndValidate, snapshot/RecipientScope/fromSnapshot/toExecutionSnapshot). KV layer `BatchSendConfig` has NO operatorStatus field — do NOT add one, and do NOT add degradation reads (T3a-5).
- **M-4**: preview/execution same-source, one `buildEsFiltersForLevel`.
- **M-5**: `OperatorStatusWriteSeamGuardTest` green; NEVER edit guard logic. If it turns red from this plan's mapping lines, that is a HUMAN-authorized noise-site registration decision — return PLAN_CONFLICT instead of touching the guard.
- **X-2**: migration MUST be V97; no `${` literals; V93 two-step TEXT pattern; DROP-column precedent V92.

## Authorized files (complete, exclusive list — 10-file cap)
1. `src/main/resources/db/migration/V97__add_operator_statuses_to_batch_send_task_config.sql` (new)
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`
7. `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` (add 2 companion functions + fix ONLY the KDoc comment at :138-143; function bodies of operatorStatusFilter/notContactedWithEmailFilters/buildExpertFilters/searchExperts untouched — N3a-1)
8. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` — **first grep it for `operatorStatus`; only adapt if it actually references the field; if no reference, strike it from the change list and say so in the report**

11th file needed → STOP, return BLOCKED. Do not touch: app.js, index.html, styles.css, BatchSendSettingService.kt, BatchSendConfigController.kt, applied migrations, buildMaterialReminderEsFilters.

## Required work (per child plan T3a-1..T3a-6)
Follow the plan verbatim: V97 migration (ADD `operator_statuses_json TEXT NOT NULL AFTER discipline` + backfill CASE + DROP `operator_status`); `operatorStatusPredicate` (pure predicates, NOT_CONTACTED = `must_not exists operatorStatus` ONLY — I3a-1/I3a-2) + `operatorStatusesFilter` (null on empty — I3a-3); `buildEsFiltersForLevel` rewrite with the I3a-4 base-switch criterion (`statuses.isEmpty() || all NOT_CONTACTED` → notContacted base; else status-agnostic base + operatorStatusesFilter); entity/snapshot renames; `matchesExpert` I3a-5 any-OR; `normalizeAndValidate` I3a-6 whitelist from `ALLOWED_OPERATOR_STATUSES` (derived from `OperatorStatus.entries`) + comma check; `parseOperatorStatuses`; full test set T3a-6 incl. the hardcoded-baseline equivalence test (N3a-2), base-switch test, mixed test, pure-predicate test, ES/DB parity test, no-`term NOT_CONTACTED` assertion.

## Downstream interfaces (consumed by later children — must match exactly)
- `RecipientScope.operatorStatuses: List<String>` + `matchesExpert` any-OR (P3b/P4a build on this).
- `ExpertSearchService.operatorStatusesFilter(List<String>): Map<String, Any>?` (null on empty) + `operatorStatusPredicate(String)` (P4a reuses).
- `BatchSendTaskConfigView.operatorStatuses: List<String>` (P3b reads).
- Entity column `operator_statuses_json`; `operator_status` column GONE after V97.

## Required commands (run freshly, record exit codes + counts)
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
1. `mvn test -Dtest=ManualInitialOutreachServiceTest`
2. `mvn test -Dtest=BatchSendTaskConfigServiceTest`
3. `mvn test -Dtest=OperatorStatusWriteSeamGuardTest`
4. `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` (requires local Docker; if unavailable record SKIPPED: no Docker with evidence, do NOT fake a pass)
5. `node --test src/test/js/*.test.js` (must stay green)
6. `git diff --check`
All must exit 0 unless recorded as SKIPPED.

## Commit rules
- Commit implementation locally as `feat(fast-p): implement p3a-operator-status-multi-backend`, staging ONLY the authorized files.
- Do not push, merge, rebase, amend, or rewrite history. Do not touch `docs/plans/fast/`.
- Write the full result (file:line evidence, grep receipts e.g. `grep -rn "operatorStatuses" src/main/kotlin | wc -l` and the `"NOT_CONTACTED"` scan per I3a-1, command outputs, commit SHA) to `docs/plans/fast/batch-task-filters/children/p3a-operator-status-multi-backend/execution.md`, overwriting the empty placeholder. Do not stage it.
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, commit SHA, command summary, report path.
