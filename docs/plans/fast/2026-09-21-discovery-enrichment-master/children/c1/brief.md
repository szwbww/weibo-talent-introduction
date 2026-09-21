# Child Brief c1 — 01 OpenAlex 认证与共享额度

## Identity

- Child ID: **c1**; approved plan: `docs/plans/2026-09-21/01-openalex-auth-budget.md` (plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`; read the file from disk in full — it is the complete approved contract).
- Master plan (design baseline, do not edit): `docs/plans/2026-09-21/00-discovery-enrichment-master.md`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master` (branch `fast/2026-09-21-discovery-enrichment-master`). Run every read/edit/test/commit in that worktree.
- `child_base_sha`: `f0c41271fc56d7455e14d28a71d563a5341dfdeb`.
- Environment: JDK 11 mandatory; prefix Maven with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`. JDK 21/8 will not build this project.
- Execution: use `skill://execute-p`; report path `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c1/execution.md`.

## Authorized files (exactly these; nothing else)

| File | Role |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexProperties.kt` | typed config |
| `src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt` | production (new) |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | production |
| `src/main/resources/application.yml` | env vars + defaults |
| `src/test/kotlin/com/weibo/talentintroduction/config/RestTemplateConfigTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicyTest.kt` | test (new) |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | test |

Missing files are created; helper DTOs/enums live inside these files (no extra files). No new external dependency. Never edit an applied Flyway migration. Do not touch the pre-existing unrelated working-tree changes of the primary checkout, and never `git add` `docs/plans/fast/**`.

## Required command

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RestTemplateConfigTest,OpenAlexRequestPolicyTest,OpenAlexDataSourceTest
```

Run it freshly after the final implementation state, in this worktree. The full `mvn test` suite is an integration-stage gate and must not be run for this child.

## Invariants to preserve (from the approved plan)

- **I-1 认证边界**: Bearer is added only for the configured OpenAlex API HTTPS origin; external fulltext sites and the generic `RestTemplate` never carry the key; blank key keeps anonymous compatibility. Never log the full `Authorization` header, the config object, or a key-bearing URL.
- **I-2 共享额度**: discovery and enrichment in the same JVM share one `OpenAlexRequestPolicy`; parse the real response quota headers; bounded backoff only for request-rate 429; day-budget exhaustion returns an identifiable `Deferred(resetAt)`. With no quota header, throttle conservatively against the configured free budget — never assume unlimited; probe the budget after startup.
- **I-3 请求量口径**: suggested max request rate 5/s, configurable, never above the official 100/s; list calls and fulltext downloads are counted separately; keep 20% of the available budget reserved for new-expert enrichment, with history backfill lowest priority.

## Master constraints that also apply

- M-1 email validation/dedup/eligibility gates and real expert IDs unchanged; no mail is sent by this run. M-2 the manual "补充学术数据" entry and its three scopes keep working. M-3 existing name/email/affiliation/operator status are never overwritten. M-4 default R&D scope unchanged (EuropePMC/PMC OA stay excluded). M-5 no paid API usage, never store or display the key, never modify an applied migration.
- Master I-6: public sources only, free budget only, no email guessing, no fuzzy merge, no mail-flow change.

## Downstream interfaces later children consume (keep these exact)

- `OpenAlexRequestPolicy` is the single shared quota/rate authority for **all** OpenAlex calls in the JVM. Child c6/c7/c8 enrichment entry points pass `RequestKind.NEW_ENRICHMENT` (auto) or `RequestKind.HISTORY_ENRICHMENT` (manual backfill) and release in `try/finally`; `OpenAlexDataSource.searchPapers` marks `RequestKind.DISCOVERY`. Context is passed explicitly — never inferred from thread names.
- Keep the plan's shape: `sealed class Permit { object Allowed; data class Deferred(val resetAt: Instant) }`, `fun beforeRequest(kind: RequestKind): Permit`, `fun recordResponse(headers: HttpHeaders): Unit`.
- The policy must be usable from a non-Spring-constructed call site as well as injected; do not change the public signature of existing `OpenAlexDataSource` methods that other modules already call.
- Free-budget defaults: with key `$1`/UTC-day, without key `$0.10`/UTC-day; effective limit = min(configured cap, provider remaining); paid balance is never consumed automatically.

## Commit

Commit the implementation locally as exactly `feat(fast-p): implement c1`. Exclude `docs/plans/fast/**` from the commit. Do not push, merge, rebase, amend, or squash.

## Stop conditions

Return `PLAN_CONFLICT` (do not improvise) if completion needs an unlisted file, a new behavioral decision, or a plan interpretation not uniquely determined by the approved bytes. Return `BLOCKED` with the smallest missing information/environment change otherwise.

## Return to controller (only this)

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, one-line command summary with exit codes/counts, report path.
