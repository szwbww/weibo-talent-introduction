# Fast-P Child Brief: p4a-template-gate-filter-backend

## Authority
- Master plan: `docs/plans/2026-08-15/batch-task-filters-main.md` (commit d6980764, amended A1)
- Child plan: `docs/plans/2026-08-15/p4a-template-gate-filter-backend.md` (commit d6980764, amended A1 — migration is **V99**) — **the complete contract. Read it in full before implementing** (also read master plan M-1..M-5, X-1..X-3).
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters`
- Branch: `fast/batch-task-filters`
- Child base SHA: `802ab2b` (p3b terminal code head; P2a V97 + P3a V98 committed on branch)

## Prerequisites (verified/committed on branch before dispatch)
- P2a (V97) + P3a (V98) committed: `buildEsFiltersForLevel` is multi-value shape (`notContactedWithEmailDomainsFilters(scope.emailDomains, scope.discipline)`, `emailDomainsFilter`, `operatorStatusesFilter(scope.operatorStatuses)` appended in the status-agnostic branch). Next free migration: **V99**.
- V97/V98 are taken; do NOT create V96-V98.

## Global constraints (master plan, binding)
- **M-1**: both target sources — `buildEsFiltersForLevel` + `matchesExpert`.
- **M-2**: `updateLegacyConfig` must preserve `gateFilterEnabled = existing.gateFilterEnabled`.
- **M-3**: full mapping set (4 data classes, service create/update/toView/ConfigFields/NormalizedConfig/3×`*Fields()`/normalizeAndValidate, snapshot/RecipientScope/fromSnapshot/toExecutionSnapshot). KV layer (`toLegacyConfig`/`updateLegacyConfig` return value) NOT extended.
- **M-4**: preview/execution same-source — `resolveScope` is the single parsing seam (I4a-4): ALL 4 main `RecipientScope.fromSnapshot` call sites (:174/:426/:431/:482) become `resolveScope(snapshot)`; `countBySnapshot` keeps taking the snapshot.
- **M-5**: `OperatorStatusWriteSeamGuardTest` green; never edit guard logic.
- **X-2**: migration MUST be V99; no `${` literals; BOOLEAN can carry DEFAULT (no two-step needed).

## Authorized files (complete, exclusive list — 10-file cap)
1. `src/main/resources/db/migration/V99__add_gate_filter_enabled_to_batch_send_task_config.sql` (new)
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`
7. `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt`

11th file → STOP, return BLOCKED. Do NOT modify: `PersonalizationGateService.kt`, `IntroductionMailComposer.kt`, `ManualExpertMailService.kt`, `MailComposeTemplateService.kt`, `MailComposeTemplateController.kt`, app.js, index.html, styles.css, applied migrations.

## Required work (per child plan T4a-1..T4a-6)
Follow the plan verbatim: V99 migration (ADD `gate_filter_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER template_id`); entity + service mapping (gateFilterEnabled Boolean default false, `updateLegacyConfig` preservation); ExpertSearchService — make `fieldPresenceFilter` non-private (body unchanged, N4a-4) + new `fieldPresenceFilters(fields)` with `require(it in ALLOWED_HAS_FIELDS)` fail-fast; BatchExecutionModels — snapshot `gateFilterEnabled: Boolean = false`, RecipientScope `gateEsFields: List<String> = emptyList()` (fromSnapshot does NOT resolve — default empty; resolution ONLY in `resolveScope`), `toExecutionSnapshot` passthrough; ManualInitialOutreachService — 25th constructor dep `mailComposeTemplateService`, new `resolveScope` (verbatim from plan T4a-4: gate off / no templateId / empty requiredEsFields → base unchanged; intersect with ALLOWED_HAS_FIELDS, log dropped), all 4 call sites → `resolveScope`; buildEsFiltersForLevel appends `filters.addAll(ExpertSearchService.fieldPresenceFilters(scope.gateEsFields))` after regionsFilter; matchesExpert gate block (I4a-5: employment/institution `!= null`, degree/researchFields `!isNullOrBlank()`, lists any-not-blank, else true); BatchSendControlService snapshot passthrough; full test set T4a-6 (zero-drift hardcoded baselines, AND test, trim test, single-seam test, parity matrix incl. institution="" and degree="", M-2 preservation).

## Downstream interfaces (consumed by later children — must match exactly)
- `BatchSendTaskConfigView.gateFilterEnabled: Boolean` (P4b reads).
- `RecipientScope.gateEsFields: List<String>` (resolved, ALLOWED_HAS_FIELDS-only) + `resolveScope` semantics (P4b renders the same fields).
- `ExpertSearchService.fieldPresenceFilters(List<String>)` (P4b's UI must show only intersectable fields: employment/degree/institution/researchFields/patentTitles/recentWorkTitles).
- `PendingOutreachSummary` DTO shape UNCHANGED (N4a-5); `POST /recipients/preview` signature unchanged.

## Required commands (run freshly, record exit codes + counts)
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
1. `mvn test -Dtest=ManualInitialOutreachServiceTest`
2. `mvn test -Dtest=BatchSendTaskConfigServiceTest`
3. `mvn test -Dtest=BatchSendTaskRuntimeIntegrationTest`
4. `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` (requires local Docker; if unavailable record SKIPPED: no Docker with evidence, do NOT fake a pass)
5. `node --test src/test/js/*.test.js` (must stay green)
6. `git diff --check`
All must exit 0 unless recorded as SKIPPED.

## Commit rules
- Commit implementation locally as `feat(fast-p): implement p4a-template-gate-filter-backend`, staging ONLY the authorized files.
- Do not push, merge, rebase, amend, or rewrite history. Do not touch `docs/plans/fast/`.
- Write the full result (file:line evidence, grep receipts: `RecipientScope.fromSnapshot` in src/main = exactly 1, `fieldPresenceFilters` = exactly 1 with addAll context, `ALLOWED_HAS_FIELDS` hit in ManualInitialOutreachService, `gateFilterEnabled = existing.gateFilterEnabled` hit, `gateFilterEnabled|gateEsFields | wc -l`; command outputs; commit SHA) to `docs/plans/fast/batch-task-filters/children/p4a-template-gate-filter-backend/execution.md`, overwriting the placeholder. Do not stage it.
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, commit SHA, command summary, report path.
