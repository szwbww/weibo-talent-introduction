# Fast-P Child Brief: p2a-email-domain-multi-backend

## Authority
- Master plan: `docs/plans/2026-08-15/batch-task-filters-main.md` (commit 72ea4f55)
- Child plan: `docs/plans/2026-08-15/p2a-email-domain-multi-backend.md` (commit 72ea4f55) — **the complete contract. Read it in full before implementing** (also read the master plan's shared-invariant sections M-1..M-5 and audits X-1..X-3).
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters`
- Branch: `fast/batch-task-filters`
- Child base SHA: `72ea4f55`

## Global constraints (master plan, all binding for this child)
- **M-1**: every new filter dimension must cover BOTH live target sources — `buildEsFiltersForLevel` (ES, 3 call sites) AND `RecipientScope.matchesExpert` (MySQL retry). There are exactly 2 sources (X-1; `buildMaterialReminderEsFilters` is dead code — do NOT touch it).
- **M-2**: new column must be explicitly preserved in `updateLegacyConfig` (`emailDomains = parseEmailDomains(existing.emailDomainsJson)` — never rebuilt from `request.emailDomain`).
- **M-3**: full mapping-point set — the 4 data classes in `BatchSendTaskConfig.kt`, `BatchSendTaskConfigService.kt` (create/update/toView/ConfigFields/NormalizedConfig/3×`*Fields()`/normalizeAndValidate), `BatchExecutionModels.kt` (snapshot/RecipientScope/fromSnapshot/toExecutionSnapshot). Do NOT touch `toLegacyConfig` KV-layer construction beyond the required degradation reads.
- **M-4**: preview and execution stay same-source (`countBySnapshot` keeps taking `BatchExecutionSnapshot`; one `buildEsFiltersForLevel`).
- **M-5**: `OperatorStatusWriteSeamGuardTest` must stay green; never edit guard logic.
- **X-2**: migration MUST be V96 (next free version); migration must NOT contain `${` literals (placeholder-replacement is ON in prod); TEXT columns cannot carry DEFAULT — follow the V93 two-step ADD + UPDATE pattern. DROP-column precedent: V92.
- **X-3 comma contract**: option values must never contain commas (I2a-5 requires `require(!it.contains(","))`).

## Authorized files (complete, exclusive list — plan is at the 10-file cap)
1. `src/main/resources/db/migration/V96__add_email_domains_to_batch_send_task_config.sql` (new)
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` (only the `:575` snapshot construction rename)
7. `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` (ONLY add the 2 new companion functions)
8. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` (adapt `RecipientScope.fromSnapshot` / `baseSnapshot(emailDomain = "edu.cn")` call sites)

**If a fix needs an 11th file, STOP and report — do not touch it.** Do NOT modify: `app.js`, `index.html`, `styles.css`, `BatchSendSettingService.kt`, `BatchSendConfigController.kt`, any applied migration, `buildMaterialReminderEsFilters`, or the old single-value `notContactedWithEmailFilters` (N2a-2).

## Required work (per child plan T2a-1..T2a-6)
Follow the plan verbatim: V96 migration (add `email_domains_json TEXT NOT NULL AFTER regions_json` + backfill CASE + DROP `email_domain`); entity/snapshot renames (`emailDomainsJson: String = "[]"` on entity, `emailDomains: List<String>` on view/commands/snapshot/RecipientScope); `matchesExpert` any-OR with empty-skip; new `emailDomainsFilter` + `notContactedWithEmailDomainsFilters` in ExpertSearchService companion (verbatim code in plan T2a-3); wire `buildEsFiltersForLevel` at :1249/:1254/:1261; full service mapping table T2a-5; helper `parseEmailDomains`; normalizeAndValidate block; all tests in T2a-6 including the critical `updateLegacyConfig` preservation test.

## Downstream interfaces (consumed by later children — must match exactly)
- `BatchSendTaskConfigView.emailDomains: List<String>` (P2b reads this).
- Commands `emailDomains: List<String>` (P2b sends this).
- `RecipientScope.emailDomains: List<String>` + `matchesExpert` any-OR (P3a/P4a build on this).
- `ExpertSearchService.emailDomainsFilter(List<String>): Map<String, Any>?` + `notContactedWithEmailDomainsFilters(List<String>, String?)` (P3a/P4a reuse).
- Entity column is `email_domains_json`; old `email_domain` column is GONE after V96.
- `emailDomainsFilter` returns null for empty — callers must NOT append (I2a-2).

## Required commands (run freshly, record exit codes + counts)
JDK is REQUIRED: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`
1. `$JAVA_HOME/bin/java -version` sanity (optional; then mvn via env var):
   `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OperatorStatusWriteSeamGuardTest`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` — requires local Docker; if Docker is unavailable record `SKIPPED: no Docker` with evidence and continue (do NOT fake a pass).
5. `node --test src/test/js/batchSendTaskConsoleInteraction.test.js` (must stay green — regression: JS suite unaffected)
6. `git diff --check`

All must exit 0 unless explicitly recorded as SKIPPED.

## Commit rules
- Commit implementation locally as `feat(fast-p): implement p2a-email-domain-multi-backend`, staging ONLY the 10 authorized files.
- Do not push, merge, rebase, amend, or rewrite history. Do not touch `docs/plans/fast/`.
- Write the full result (file:line evidence, command outputs with exit codes/counts, commit SHA) to `docs/plans/fast/batch-task-filters/children/p2a-email-domain-multi-backend/execution.md`, overwriting the empty placeholder. Do not stage it.
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, commit SHA, command summary, report path.
