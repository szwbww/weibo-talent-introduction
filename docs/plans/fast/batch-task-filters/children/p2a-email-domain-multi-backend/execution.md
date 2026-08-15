# P2a Execution Report — email-domain multi-backend

> This file REPLACES the pre-amendment conflict-report placeholder entirely (approval A1, commit d6980764: migration is V97).

## Result: BLOCKED

The 10-file implementation is **complete, committed, and its own tests pass** (45 + 70 Kotlin tests, 38 JS tests).
The blocker is the M-5 required command `OperatorStatusWriteSeamGuardTest`: it fails purely because the plan's
mandated additions shifted line numbers, making the guard's `EXCLUDED_NOISE_SITES` entries stale. Refreshing those
`path:line:context` entries requires editing `OperatorStatusWriteSeamGuardTest.kt` — an **11th file** outside the
authorized list. The brief explicitly instructs: *"if a fix needs an 11th file, STOP and return BLOCKED — do not touch it."*
M-5's disposition additionally gates `EXCLUDED_NOISE_SITES` registration behind **HUMAN authorization**. No authorization
was granted in this invocation, so the guard file was NOT touched.

## Identity

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters/docs/plans/2026-08-15/p2a-email-domain-multi-backend.md`
- Plan SHA-256: `dbd58a833ea8a5ff6f8ffdaa419cd8443ce6dd0ed9ad237ebd82eb1ba426a372`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters@fast/batch-task-filters@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/batch-task-filters`
- Base HEAD: `d698076432bbee4632122d7df1ab09ef365aacf1` (amended A1)
- Implementation commit: `1a9a470` — `feat(fast-p): implement p2a-email-domain-multi-backend` (10 files, 376 insertions, 57 deletions)

## Implementation (per child plan T2a-1..T2a-6, amended)

### T2a-1 Migration V97 — `src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql` (new)
- `ADD COLUMN email_domains_json TEXT NOT NULL AFTER regions_json` (V93 two-step pattern; no DEFAULT on TEXT).
- Backfill `CASE` (NULL/'' → `'[]'`, else `CONCAT('["', email_domain, '"]')`).
- `ALTER TABLE ... DROP COLUMN email_domain` (V92 precedent).
- Zero `${` literals: `grep -c '\${' V97*.sql` → `0`.
- Grep receipt: `grep -rn "email_domain\b" V97*.sql` hits lines 1, 4, 10, 11, 14 (line 14 = DROP).

### T2a-2 Entity/snapshot — `BatchSendTaskConfig.kt` / `BatchExecutionModels.kt`
- `BatchSendTaskConfig.emailDomainsJson: String = "[]"` (:23, after `regionsJson`); view `emailDomains: List<String> = emptyList()` (:47); both commands `emailDomains: List<String> = emptyList()` (:71, :89).
- `BatchExecutionSnapshot.emailDomains: List<String> = emptyList()` (:18); `RecipientScope.emailDomains: List<String>` (:53).
- `matchesExpert` (:76-81): any-OR `emailDomains.none { email.endsWith("@$it") }` with empty-skip (`emailDomains.isNotEmpty()` gate; blank email → false only when list non-empty) — I2a-4/I2a-2.
- `RecipientScope.fromSnapshot` (:107-108): `emailDomains = snapshot.emailDomains.map { it.trim() }.filter { it.isNotEmpty() }.distinct()` — I2a-2/I2a-5.
- `toExecutionSnapshot` (:222-230): parses `emailDomainsJson` with the file's existing try/catch TypeReference pattern (same as tags/regions); parse failure → `emptyList()` (I2a-2).

### T2a-3 ES helpers — `ExpertSearchService.kt` (additions only; N2a-1/N2a-2)
- `emailDomainsFilter` (:120-133): single `bool.should` + `minimum_should_match: 1`, returns `null` for empty (I2a-3/I2a-2).
- `notContactedWithEmailDomainsFilters` (:137-155): multi-value overload; old single-value `notContactedWithEmailFilters` (:157) untouched.
- Diff: `git diff --numstat` → `41 0` (additions only, 0 deletions).

### T2a-4 ES target wiring — `ManualInitialOutreachService.kt`
- `:1249` → `notContactedWithEmailDomainsFilters(scope.emailDomains, scope.discipline).toMutableList()` (1 occurrence).
- `:1254`, `:1261` → `emailDomainsFilter(scope.emailDomains)?.let { base.add(it) }` (exactly 2 occurrences per acceptance I2a-3).
- Scope constructions: legacy wrapper (:1001, null-safe `emailDomain?.ifBlank { null }?.let { listOf(it) } ?: emptyList()`), material-reminder snapshot (:1142) — same bridging.
- Log strings (:1130, :1146): `(scope.emailDomains.takeIf { it.isNotEmpty() }?.let { " + domains=" + it.joinToString(",") } ?: "")`.
- KV bridges: `toSnapshot` (:1284) `emailDomains = emailDomain.ifBlank { null }?.let { listOf(it) } ?: emptyList()`; `toBatchSendConfig` (:1300) `emailDomain = emailDomains.firstOrNull().orEmpty()` (I2a-6 degradation).
- `countPending` (:102/:111) still uses the old single-value KV path + `notContactedWithEmailFilters` — untouched (N2a-2).
- Dead `buildMaterialReminderEsFilters` (:1102-1128) untouched — references KV `BatchSendConfig.emailDomain`, still compiles.

### T2a-5 Config service — `BatchSendTaskConfigService.kt` (full mapping table)
- create (:75) / update (:108): `emailDomainsJson = normalized.emailDomainsJson`.
- `updateLegacyConfig` (:188): `emailDomains = parseEmailDomains(existing.emailDomainsJson)` — M-2 receipt: `grep -n "emailDomains = parseEmailDomains(existing"` → `188`. Never rebuilt from `request.emailDomain`.
- `updateLegacyConfig` return (:203): `emailDomain = view.emailDomains.firstOrNull().orEmpty()`.
- `toLegacyConfig` (:230): `emailDomain = parseEmailDomains(row.emailDomainsJson).firstOrNull().orEmpty()`.
- `normalizeAndValidate` (:259-267): trim → drop-empty → distinct → `require(!it.contains(","))` (:265) → `emailDomainsJson = objectMapper.writeValueAsString(...)` (I2a-2/I2a-5).
- `NormalizedConfig` (:531) `emailDomainsJson: String`; `toView` (:421) `emailDomains = parseEmailDomains(row.emailDomainsJson)`; `ConfigFields` (:512) `emailDomains: List<String>`; `toFields()` for create/update (:549/:567) `emailDomains = emailDomains`; entity `toFields()` (:585) `emailDomains = parseEmailDomains(emailDomainsJson)`.
- Helper `parseEmailDomains` (:393-405): verbatim per plan (trim/filter/distinct; catch → warn → emptyList). Added `log` (slf4j LoggerFactory) to the service for the helper's warn.
- `BatchSendControlService.kt` :575 snapshot construction renamed to `emailDomains = emailDomain.ifBlank { null }?.let { listOf(it) } ?: emptyList()`.

### T2a-6 Tests
- `BatchSendTaskConfigServiceTest.kt` (+6 new tests): multi-value persist/order (I2a-1), whitespace/dedup (I2a-5), comma rejection (I2a-5), null/[] → `"[]"` (I2a-2), **updateLegacyConfig preservation** (I2a-6, critical — `emailDomainsJson` stays `["a.com","b.com"]` after legacy PUT with `emailDomain=""`), toLegacyConfig first-degradation (I2a-6). Existing tests/helpers adapted to `emailDomains`/`emailDomainsJson`.
- `ManualInitialOutreachServiceTest.kt` (+4 new tests): exactly one `bool.should` with should-size 2 and `minimum_should_match 1` + wildcard values `*@a.com`,`*@b.com` (I2a-3); no wildcard / no bool.should for empty (I2a-2); `matchesExpert` any-OR + empty-skip with null email (I2a-2/I2a-4); ES/DB parity per-profile vs manual `endsWith("@$domain")` semantics (I2a-4). Two `runScheduledBatch` tests updated to expect the multi-value filter shape (`notContactedWithEmailDomainsFilters`).
- `BatchSendTaskRuntimeIntegrationTest.kt`: `enabledConfig` helper → `emailDomainsJson`; `baseSnapshot` helper → `emailDomains: List<String>`; 3 `baseSnapshot(emailDomain=...)` call sites → `emailDomains = listOf(...)`.

## Grep receipts (acceptance)

| Check | Receipt |
|---|---|
| I2a-1 V97 DROP | `grep -rn "email_domain\b" V97*.sql` → line 14 `ALTER TABLE batch_send_task_config DROP COLUMN email_domain;` |
| X-2 no `${` | `grep -c '\${' V97*.sql` → `0` |
| I2a-3 wiring count | `grep -n "emailDomainsFilter" ManualInitialOutreachService.kt` → exactly `1254`, `1261` (2) + `notContactedWithEmailDomainsFilters` → `1249` (1) |
| M-2 | `grep -n "emailDomains = parseEmailDomains(existing"` → `BatchSendTaskConfigService.kt:188` |
| M-3 | `grep -rn "emailDomains" src/main/kotlin \| wc -l` → `40` (≥20, per-point verified) |
| N2a-2 | old `fun notContactedWithEmailFilters` still at `ExpertSearchService.kt:157`, unchanged; `git diff --numstat` → `41 0` |
| I2a-5 | comma require at `BatchSendTaskConfigService.kt:265` |
| I2a-4 | `emailDomains.none` at `BatchExecutionModels.kt:80` |
| Entity old field gone | `grep -rn "emailDomain\b" src/main/kotlin/com/weibo/talentintroduction/campaign/` → remaining hits are only KV-degradation reads (`BatchSendConfig` bridge `firstOrNull()`) and KV-layer `BatchSendSettingService` (untouched per M-3) |

## Commands (run freshly in this invocation, JDK zulu-11)

| # | Command | Exit | Result |
|---|---|---|---|
| sanity | `$JAVA_HOME/bin/java -version` | 0 | `openjdk version "11.0.15" ... Zulu11.56+19-CA` |
| 1 | `mvn test -Dtest=BatchSendTaskConfigServiceTest` | 0 | `Tests run: 45, Failures: 0, Errors: 0, Skipped: 0` + BUILD SUCCESS (+ JS 549/549 in same build) |
| 2 | `mvn test -Dtest=ManualInitialOutreachServiceTest` | 0 | `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0` + BUILD SUCCESS |
| 3 | `mvn test -Dtest=OperatorStatusWriteSeamGuardTest` | 1 | **FAIL** — see blocker below |
| 4 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | — | **SKIPPED: no Docker.** `docker version` → daemon socket missing: `failed to connect to the docker API at unix:///Users/lukai/.orbstack/run/docker.sock ... no such file or directory` (OrbStack not running). Not faked. |
| 5 | `node --test src/test/js/batchSendTaskConsoleInteraction.test.js` | 0 | `tests 38, pass 38, fail 0` |
| 6 | `git diff --check` | 0 | no output |

## Blocker detail (M-5 guard)

`OperatorStatusWriteSeamGuardTest` scans `src/main/kotlin` for `operatorStatus = ` named-argument lines and subtracts
`EXCLUDED_NOISE_SITES` (strict `path:line:context`). All 11 flagged lines are **pre-existing config-column DTO-noise
mappings that already have exclusion entries** — the entries' line numbers went stale because the plan-mandated
additions shifted lines:

| File | old excluded line | new actual line | shift caused by |
|---|---|---|---|
| BatchExecutionModels.kt | 107 | 110 | matchesExpert block +3 (plan T2a-2 verbatim) |
| BatchExecutionModels.kt | 243 | 255 | + fromSnapshot comment +1, toExecutionSnapshot parse block +9 (plan T2a-2) |
| BatchSendTaskConfigService.kt | 74/107/187 | 77/110/190 | logger +3 (required by verbatim `parseEmailDomains` `log.warn`) |
| BatchSendTaskConfigService.kt | 292 | 304 | + normalizeAndValidate block +9 (plan verbatim) |
| BatchSendTaskConfigService.kt | 399/527/545/563 | 423/551/569/587 | + parseEmailDomains helper +12 (plan verbatim) |
| ExpertSearchService.kt | 345 | 386 | +41 lines of the two new companion functions (plan T2a-3 verbatim) |

No semantic change to any `operatorStatus` line; no new `expert_contact` write site. The remedy (refresh
`path:line:context` in `EXCLUDED_NOISE_SITES`) is gated by M-5 on HUMAN authorization and lives in an
**unauthorized 11th file**. Per the brief's explicit rule, the guard file was not touched.

## Scope integrity

- Exactly 10 authorized files changed; commit `1a9a470` contains exactly those 10 (`git show --name-only` → 10 files).
- `docs/plans/fast/` artifacts (brief.md, p3b brief, p4a brief, ledger.md) modified by Main/user — never staged.
- Not touched: `BatchSendSettingService.kt`, `BatchSendConfigController.kt`, `BatchSendConfigControllerTest.kt` (verified: its `introEntity`/`reminderEntity` use named args only, default `emailDomainsJson = "[]"`), `buildMaterialReminderEsFilters`, old `notContactedWithEmailFilters`, any applied migration (V92/V93/V96 untouched).
- `git diff --check` clean; no history rewrite (single local commit on `fast/batch-task-filters`).

## Next action for Main

Authorize (or arrange human authorization for) refreshing the 11 stale `EXCLUDED_NOISE_SITES` entries
(path:line:context per the table above) in `OperatorStatusWriteSeamGuardTest.kt`, then re-run command 3.
Alternatively, run the guard in a fresh worktree at base `d6980764` to confirm it was green before this child.
