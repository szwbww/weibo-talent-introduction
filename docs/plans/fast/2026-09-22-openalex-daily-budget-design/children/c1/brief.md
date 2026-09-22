# Child Brief c1 — OpenAlex account free budget (fast-p)

## Identity

- Child id: `c1`
- Approved plan: `docs/plans/2026-09-22/01-openalex-account-budget.md` (identity `commit:ee1dfcd5439de54475c12ff51c9c713e984a82ec`) — read it first; it is the complete approved contract.
- Parent master plan: `docs/plans/2026-09-22/openalex-daily-budget-design.md` (same identity).
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design`
- Branch: `fast/2026-09-22-openalex-daily-budget-design`
- `child_base_sha`: `e2247680592603b091af791ef3629d70739a015b`
- Execution report to write: `docs/plans/fast/2026-09-22-openalex-daily-budget-design/children/c1/execution.md`
- Method: use the `execute-p` skill against this brief + the approved plan.

## Authorized files (exactly these 10; nothing else)

1. `src/main/resources/db/migration/V132__create_openalex_budget.sql` (new)
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/OpenAlexBudgetRepository.kt` (new)
3. `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexProperties.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt`
7. `src/main/resources/application.yml`
8. `src/test/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicyTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/OpenAlexBudgetRepositoryIT.kt` (new)

`V132` is unused at this base (highest applied migration is `V131__create_expert_academic_enrichment_job.sql`). Never edit an applied migration. If `V132` turns out to be taken, return `PLAN_CONFLICT` instead of renumbering.

## Global constraints

- JDK 11 only: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
- Kotlin + Spring Boot 2.7, Spring Data JDBC (no JPA), kotlin `spring` all-open plugin is on.
- No new Maven dependencies, no new module, no ES mapping change, no mail/conversation change, no frontend change.
- This worktree is clean at `child_base_sha`; the unrelated uncommitted `task-activity-center` work present in the primary `main` worktree is deliberately absent and out of scope.
- One local implementation commit only: `feat(fast-p): implement c1`. No push, merge, rebase, squash, amend, reset, or history rewrite.
- Exclude `docs/plans/fast/**` from the implementation commit (the controller commits fast-p evidence separately).
- Do not run formatters, linters, or the whole-project suite. Run the required commands below (plus focused reruns while developing).
- Product code must not silently degrade: a production bean that cannot obtain the shared JDBC budget store must fail loudly, never fall back to an in-memory budget. Unit tests may inject a fake store.

## Invariants that gate this child (full text in the plan)

- **I-1 Operation cost vs purpose**: explicit `Operation` classification `LIST=1`, `SEARCH=10`, `SINGLETON=0`, `CONTENT=100`, `RATE_LIMIT=0`; `RequestKind` stays purpose (`DISCOVERY`/`HISTORY_ENRICHMENT`/`NEW_ENRICHMENT`). Operation is passed explicitly at construction and then validated against target host/path — never inferred by URL substring. Every HTTP retry re-reserves. New `CONTENT` calls are refused this phase; public PDF/PMC full text is not OpenAlex metered.
- **I-2 Account-cycle budget and credential isolation**: stable non-secret `accountScope` (never derived from the API key), one scope per real account across all instances; effective free limit = `min(official daily free limit, configured protection)`, prepaid balance never added; default protection `10000`; legacy `dailyBudgetUsd > 0` may only tighten, `0` means "use free-protection default" (never unlimited); negative config fails startup. Key rotation must not reset spent budget. No API key in any table, log, snapshot, or URL.
- **I-3 Reserve before the external call, conservative out-of-order handling**: reserve inside one transaction that locks the account row, then release the request; each permit is unique and settles exactly once; `UNKNOWN` is not refunded on timeout; a lower-than-estimated actual cost may only refund the evidenced difference; a higher actual cost is charged immediately and blocks further over-budget requests; a response can only tighten the current cycle's provider ceiling (older responses must never raise it); available = free limit − confirmed spent − all unsettled reservations.
- **I-4 Sync/cooldown never block for long**: sync official balance at startup, on official reset, on insufficient budget, on error recovery, and every 300 s; one calibrator per account (lease); before a trusted balance exists, or when the DB is unavailable, metered requests defer (`Deferred(reason, retryAt)`) — never fall back to a local 10000; 429 stores `Retry-After`/backoff into a shared `notBefore`; never sleep inside scheduler or HTTP threads until the next day; an exhausted budget must not block allowed 0-cost operations; day rollover must confirm the official new reset cycle first, and unfinished permits of the old cycle settle only the old row.
- **I-5 Enrichment reserve is evidence-based and borrowable**: initial reserve target = 20% of the effective free limit (2000 at 10000); reserve = `min(floor(effectiveFreeLimit × ratio), pendingCount × estimatedEnrichmentCost)`, `PENDING`/`RUNNING`/`RETRY_WAIT` all count as unfinished; with no samples estimate `10` credits per task, with samples use `max(10, P95 × 3)` of the last 100 `NEW_ENRICHMENT` request costs; keep at least one 10-credit request reserved while the target is sufficient; never reserve more than the actual remaining budget; the estimate is a fair-share allocation input only — every real request still reserves per I-1/I-3; with no pending work and 60 s without a `NEW_ENRICHMENT` request the reserve drops to 0 and is restored immediately when pending work appears (already-issued requests are not withdrawn); `HISTORY_ENRICHMENT` may not consume the new-enrichment reserve.
- **I-6 Every new column has a lifecycle**: `account` row unique on `account_scope`; `day` row unique on `(account_scope, reset_at)`; `reservation` unique on `permit_id` and linked to its cycle; reservation state `RESERVED → SETTLED | UNKNOWN` (`UNKNOWN` only archived by evidenced settlement or cycle close — never auto-treated as unspent); `amount_reserved`/`amount_actual` are separate; `request_kind`/`operation` are distinct; UTC timestamps; `rate_next_at`, `cooldown_until`, `sync_lease_token`/`sync_lease_until`, `last_synced_at` live on the account row and survive restarts; free limit, confirmed spend, unsettled reservations and provider ceiling are stored separately (a snapshot's "available" value must never be re-deducted as a raw balance); fixed lock order `account → day → permit`; no network call inside a transaction.

## Downstream interfaces (consumed by c2/c3 — keep exactly these shapes)

- The policy must expose an explicit-operation reserve path returning a permit, plus the existing compatibility entry points, and a `Deferred` result carrying a machine-readable reason from `DAILY_BUDGET`, `RATE_LIMIT`, `BUDGET_SYNC`, `BUDGET_STORE_UNAVAILABLE`, `ENRICHMENT_RESERVE` plus `retryAt`.
- Read-only budget snapshot with exactly these fields: `accountScope`, `resetAt`, `officialLimitCredits`, `officialRemainingCredits`, `confirmedSpentCredits`, `reservedCredits`, `effectiveRemainingCredits`, `enrichmentReserveCredits`, `lastSyncedAt`, `deferredReason`, `retryAt`. No key, no authenticated URL.
- Configuration keys added in `application.yml`: `accountScope` (default `primary`), `freeBudgetCredits` (default 10000), `budgetSyncInterval` (300s); existing 5/s rate and 0.2 reserve ratio preserved; all overridable by env vars.

## Required commands (paste exact commands, exit codes and counts into the execution report)

1. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=OpenAlexRequestPolicyTest,OpenAlexDataSourceTest,OpenAlexBudgetRepositoryIT -DmysqlIt=true -Dapi.version=1.40 test`
   - That exact environment is required: without `DOCKER_HOST` testcontainers reports "Could not find a valid Docker environment", and without `-Dapi.version=1.40` docker-java fails with "client version 1.32 is too old. Minimum supported API version is 1.40". The controller verified both at baseline (`baseline-java.txt`, smoke `Tests run: 14, Failures: 0, Errors: 0`); they are environment wiring, not plan deviations.
   - The IT must really execute against Testcontainers MySQL. `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/ExpertAcademicEnrichmentJobRepositoryIT.kt` is the in-repo pattern for container start + `@DynamicPropertySource`, but gate the new IT with `@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")` as the plan requires. "Surefire skipped the IT" is not a pass; if Docker/MySQL cannot run, say so explicitly as a blocker instead of silently passing.
   - The `test` phase also runs the repo's node JS suite through `exec-maven-plugin`; that is expected.
2. Baseline at this base for comparison (controller-verified, recorded in `docs/plans/fast/2026-09-22-openalex-daily-budget-design/baseline-java.txt`): JS `node --test src/test/js/*.test.js` → exit 0, tests 1037 / pass 1037 / fail 0; the existing named Java tests all pass; `ExpertAcademicEnrichmentJobRepositoryIT` passes with the environment above.

## Acceptance (from the plan, verbatim requirements)

- I-1: per-path assertions for 1/10/0 cost; simulated `CONTENT=100` does not activate real downloads; a retried request is charged twice; publisher requests produce zero ledger rows.
- I-2: limit 10000 with official used 227 → effective ≤ 9773; simulated prepaid 100 does not raise the effective limit; same scope across key rotation and two instances/restart does not reset.
- I-3: two connections racing for the last 10 credits with 10 LIST + 1 SEARCH approve total cost ≤ 10; repeated settle does not double-charge; timed-out reservation is not released; a lower balance followed by a higher balance does not increase availability.
- I-4: DB failure and sync failure both prevent metered requests; a 429 cooldown is shared across two instances; pre/post-reset responses cannot write the new cycle; no long sleep on the scheduler path.
- I-5: reserve active while pending work exists; borrowable 60 s after the last task ends; restored when work is re-enqueued; new enrichment may consume the reserve, history enrichment may not.
- I-6: unique constraints, state transitions, UTC time and lock order verified on real MySQL; ledger aggregate equals permit detail; an active `UNKNOWN` is not cleaned up.
- Publish/rollout: this child only switches the shared budget; it must not start the new queue or change discovery behavior beyond budget accounting. Original discovery and enrichment must still work against fixed fixtures.

## Report contract

Write `docs/plans/fast/2026-09-22-openalex-daily-budget-design/children/c1/execution.md` with: status, commit SHA, changed files, per-invariant evidence (`file:line` + test name), exact commands with exit codes/counts, deviations, and blockers.

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
Do not review later children, repair unrelated behavior, push, merge, or rewrite history.
