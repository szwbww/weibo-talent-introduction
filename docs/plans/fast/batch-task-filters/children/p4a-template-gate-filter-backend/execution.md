# P4a Execution Report — template-gate-filter-backend

- **Result: BLOCKED** (implementation complete + all required commands green, but M-5 guard turned red by an authorized line shift — needs HUMAN authorization to update `EXCLUDED_NOISE_SITES`, per brief non-negotiable and master plan M-5 disposition)
- Plan: `docs/plans/2026-08-15/p4a-template-gate-filter-backend.md` (amended A1, migration V99)
- Plan SHA-256: `6a9d1e99d8ac68d39f5c05e99ef536add57aae59d1093e0afc0435da879e72d1`
- Execution ID: `.../docs/plans/2026-08-15/p4a-template-gate-filter-backend.md@6a9d1e99...`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters`
- Branch: `fast/batch-task-filters` (base HEAD `901c0e9`, p3b terminal)
- Executor: `ImplP4aGateFilter` (execute-p)

## Authorized file scope (10/10, no 11th file touched)

| # | File | Change |
|---|---|---|
| 1 | `src/main/resources/db/migration/V99__add_gate_filter_enabled_to_batch_send_task_config.sql` | NEW — `ADD COLUMN gate_filter_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER template_id;` no `${` literals |
| 2 | `.../campaign/domain/BatchSendTaskConfig.kt` | `gateFilterEnabled: Boolean = false` in all 4 data classes, after `templateId` (entity:25, view:50, create:74, update:93) |
| 3 | `.../campaign/domain/BatchExecutionModels.kt` | snapshot `gateFilterEnabled: Boolean = false` (:22); `RecipientScope.gateEsFields: List<String> = emptyList()` (:58); `matchesExpert` gate block (:90-109); `toExecutionSnapshot` passthrough (:287). `fromSnapshot` does NOT resolve gateEsFields |
| 4 | `.../campaign/service/BatchSendTaskConfigService.kt` | create (:79) / update (:113) / toView (:448) / NormalizedConfig (:316) / ConfigFields (:540) / NormalizedConfig (:560) / 3× `toFields()` (:579/:598/:619); **`updateLegacyConfig` preservation `gateFilterEnabled = existing.gateFilterEnabled` (:196, I4a-6/M-2)** |
| 5 | `.../campaign/service/ManualInitialOutreachService.kt` | +import MailComposeTemplateService (:50); 25th ctor dep (:85-86); `resolveScope` (:427-441, verbatim T4a-4); **4 call sites → `resolveScope(snapshot)`** (:176/:495/:500/:558); `buildEsFiltersForLevel` appends `filters.addAll(ExpertSearchService.fieldPresenceFilters(scope.gateEsFields))` after regionsFilter (:1295, I4a-2 flat AND) |
| 6 | `.../campaign/service/BatchSendControlService.kt` | **zero-diff** — authorized but no change needed (P3a precedent: snapshot passthrough is delivered by `BatchExecutionModels.toExecutionSnapshot`, which BatchSendControlService calls at 5 sites; legacy `toLegacySnapshot` KV path must NOT carry the field per M-3 "KV 兼容层不拖进来"; `BatchExecutionSnapshot.gateFilterEnabled` defaults false there) |
| 7 | `.../expert/service/ExpertSearchService.kt` | `fieldPresenceFilter` visibility `private`→`fun` (body unchanged, N4a-4); new `fieldPresenceFilters(fields)` with `require(it in ALLOWED_HAS_FIELDS)` fail-fast (I4a-2/I4a-3) |
| 8 | `src/test/.../BatchSendTaskConfigServiceTest.kt` | helpers `createCmd`/`updateCmd`/`row` + 3 new tests (create true→view true, default false, M-2 legacy preservation) |
| 9 | `src/test/.../ManualInitialOutreachServiceTest.kt` | +25th ctor mock; 8 new tests (3× I4a-1 zero-drift hardcoded baseline, I4a-2 AND, I4a-3 trim + fail-fast, I4a-4 single seam, I4a-5 parity matrix incl. `institution=""`→true and `degree=""`→false) |
| 10 | `src/test/.../BatchSendTaskRuntimeIntegrationTest.kt` | `buildManualOutreachService()` 25th positional ctor arg; 4 `RecipientScope.fromSnapshot` calls compile unchanged via defaults (plan: "只需确认编译与既有断言不变") |

Forbidden files untouched (N4a-2/N4a-3): `PersonalizationGateService.kt` / `IntroductionMailComposer.kt` / `ManualExpertMailService.kt` / `MailComposeTemplateService.kt` / `MailComposeTemplateController.kt` / app.js / index.html / styles.css / applied migrations / guard test — **none in `git diff --name-only`**.

## Grep receipts

```
$ grep -rn "RecipientScope.fromSnapshot" src/main/kotlin
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:428:        val base = RecipientScope.fromSnapshot(snapshot)
```
→ **exactly 1**, only inside `resolveScope` (I4a-4). ✅

```
$ grep -n "fieldPresenceFilters" src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt
1294:        // I4a-1: 空集合时 fieldPresenceFilters 返回空列表，不追加任何项。
1295:        filters.addAll(ExpertSearchService.fieldPresenceFilters(scope.gateEsFields))
```
→ **exactly 1** usage, context is `filters.addAll(...)` (flat AND, no `should`). ✅

```
$ grep -n "ALLOWED_HAS_FIELDS" src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt
422:     * I4a-3: requiredEsFields 可能返回 ALLOWED_HAS_FIELDS 之外的字段
433:        val usable = required.filter { it in ExpertSearchService.ALLOWED_HAS_FIELDS }
437:                "Gate filter: {} of template {} cannot be pre-filtered (not in ALLOWED_HAS_FIELDS), dropped: {}",
```
→ trimming happens in `resolveScope` (I4a-3). ✅

```
$ grep -n "gateFilterEnabled = existing.gateFilterEnabled" src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt
196:                gateFilterEnabled = existing.gateFilterEnabled
```
→ M-2 / I4a-6 preservation. ✅

```
$ grep -rn "gateFilterEnabled\|gateEsFields" src/main/kotlin | wc -l
23
```

## Required commands (JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home)

| # | Command | Exit | Kotlin tests | JS tests | Notes |
|---|---|---|---|---|---|
| 1 | `mvn test -Dtest=ManualInitialOutreachServiceTest` | 0 | 86 run / 0 fail | 569 pass (bound via exec-maven-plugin) | includes 8 new P4a tests |
| 2 | `mvn test -Dtest=BatchSendTaskConfigServiceTest` | 0 | 54 run / 0 fail | 569 pass | includes 3 new P4a tests |
| 3 | `mvn test -Dtest=BatchSendTaskRuntimeIntegrationTest` | 0 | 21 run / 0 fail | 569 pass | ctor adaptation |
| 4 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | **SKIPPED** | 1 run / 1 error | — | **no Docker**: testcontainers `Could not find a valid Docker environment` → `IllegalStateException: Docker is required for Flyway migration tests`; `NoSuchFileException (/var/run/docker.sock)`; OrbStack socket `/Users/lukai/.orbstack/run/docker.sock` missing; no dockerd/orbstack/colima process. Not faked. V99 SQL follows V93/V95 template, no `${` |
| 5 | `node --test src/test/js/*.test.js` | 0 | — | 569 pass / 0 fail | |
| 6 | `git diff --check` | 0 | — | — | clean |

Additional: `mvn test -Dtest=OperatorStatusWriteSeamGuardTest` → **FAIL (exit 1)** — see blocker below.

## ⛔ M-5 guard BLOCKER (HUMAN authorization required)

`OperatorStatusWriteSeamGuardTest` went from GREEN at base to RED:

```
实际命中文件：[ExpertOperatorStatusService.kt, ExpertSearchService.kt, ManualInitialOutreachService.kt]
未登记白名单的写入点（违规）：
  com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:431: operatorStatus = source.nullableText("operatorStatus"),
```

Root cause: the authorized T4a-3 addition (`fieldPresenceFilters` + KDoc, +12 lines in the companion object, verbatim plan placement) shifted the pre-existing ES-document→DTO read mapping from line 419 to 431. The guard's `EXCLUDED_NOISE_SITES` entry is pinned at `ExpertSearchService.kt:419` (context `operatorStatus = source.nullableText`); the line itself is unchanged read-path code, not an `expert_contact` write, and was already human-authorized noise (A5: 332→345→386→419 amendments; P2a/P3a repair rounds).

Per brief non-negotiable and master plan M-5 disposition ("守卫若因本轮映射行变化而失败，必须 HUMAN 授权把对应 path:line:context 登记进 EXCLUDED_NOISE_SITES；不得自行改守卫判定逻辑"), I did **not** touch the guard test.

Required human authorization (one-line guard update, separate repair commit, P3a `1ba0471` precedent):
```
NoiseSite("com/weibo/talentintroduction/expert/service/ExpertSearchService.kt", 419, "operatorStatus = source.nullableText")
→ NoiseSite("com/weibo/talentintroduction/expert/service/ExpertSearchService.kt", 431, "operatorStatus = source.nullableText")
```

No other guard hits: my `gateFilterEnabled = ...` mapping lines do not match the `operatorStatus = ` scan pattern; the guard output lists ExpertSearchService.kt:431 as the only violation.

## Commit

- `feat(fast-p): implement p4a-template-gate-filter-backend` — staged ONLY the 10 authorized files; `docs/plans/fast/` artifacts (brief.md/ledger.md/execution.md) NOT staged.
- Commit SHA: see `git log -1` on branch `fast/batch-task-filters`.
- No push/merge/rebase/amend.

## Deviations

- `BatchSendControlService.kt` (authorized #6) zero-diff — identical to P3a: the plan's "快照构造处透传" is realized by `toExecutionSnapshot` in `BatchExecutionModels.kt`; the only direct snapshot construction here is the legacy KV `toLegacySnapshot`, which must NOT carry the field (M-3 KV layer excluded).
- One stale doc comment in `countBySnapshot` updated from `RecipientScope.fromSnapshot` to `resolveScope` (keeps the exactly-1 grep receipt clean; call-site contract unchanged).
