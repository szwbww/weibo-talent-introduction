# Child 02 Brief — Bounded same-page OpenAlex retries

Approved child plan: `docs/plans/2026-09-26/discovery-repair-02-search-retry.md` (exact approved bytes committed as `b8789cb6062d9110218c08ce8099dba8dddd73e4`). Read the full plan before implementation; it is authoritative for exact tasks, fixtures, tests, and acceptance criteria.

## Global constraints

- Master plan: `docs/plans/2026-09-26/discovery-repair-00-master.md`; master base `64c0394a940bd79c2ecc04e5c497650f045faa75`.
- Prior child 01 terminal product/code head: `29db24e66b8cb98eceb782812da34d1acbd6da06`. Child 01 evidence commit `418ff1bab09083de4f95f8a169ffdeb4ef0aecba` and ledger binding commit `ec1e135e29495e55e536b509f15f83142b01f264` are ancestors and must stay before this implementation.
- Implement only child 02. Preserve master invariants M1–M4 and I-1–I-5. No schema/persistence changes, admission logic, sender configuration, deployment, production data modification, or scheduling.
- The child 01 downstream contract is fixed: dedup failures retain the existing entering cursor and checkpoint/status shape; `DEDUP_INCOMPLETE` is only a stop-reason string. Do not undo that behavior.
- No scope expansion: no retries for PDF or other sources, no change to non-OpenAlex 429/503 five-attempt policy, no change to queue's existing five-minute defer, no generic retry framework/config/service.

## Authorized Files

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
2. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`

No other product/test files may change.

## Child requirements and invariants

- I-1: Synchronous OpenAlex retries at most three actual attempts per page, only connection/read timeout, reset/EOF, HTTP 500/502/503/504, or observed remote handshake interruption. Never retry certificate/peer-verification/protocol-incompatibility, ordinary parsing errors, or HTTP 400/401/403.
- I-2: Backoff 1s/2s plus 0–200ms jitter; wait in <=100ms slices checking cancellation and deadline. Respect interruption; propagate `OpenAlexBudgetDeferredException` as `BUDGET_DEFERRED`; OA429 retains cursor and ends source as `BUDGET_DEFERRED` without long Retry-After sleep or `SEARCH_FAILED`. Preserve existing response cooling policy. Do not change non-OA policy.
- I-3: Each attempt re-enters existing `searchPapers`/`getJson`/reserve path; do not change `apiRequests` semantics or refund UNKNOWN credits. Do not count papers or advance cursor before success. Clear per-page failure streak after success including empty pages. Exhaustion records one `SEARCH_FAILED` and attempt/type logging without leaking keyed URLs.
- I-4: Preserve M1–M3 admission and identity invariants.
- Tests must cover same-cursor temporary failure then success; three timeouts; 503/502/500/504; certificate/403 single attempt; budget-deferred, interruption/cancellation during waits; success counts once; queue path remains single-return/deferred and existing reserve/UNKNOWN semantics remain unchanged.
- Emit actual isolated fixture output `target/discovery-plan-acceptance/02.json` from tested function/request state; no hard-coded pass flag.

## Required command

`JAVA_HOME=/Library/Java/VirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,OpenAlexDataSourceTest,OpenAlexRequestPolicyTest,DiscoveryPipelineServiceTest`

Use the absolute Maven launcher `/opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn` because plain `mvn` fails JAVA_HOME validation in this environment. Run from the worktree root and record exact exit/counts. Do not run a project-wide suite in the child loop.

## Downstream interfaces

Child 03 relies on unchanged request-policy reserve/UNKNOWN semantics, existing discovery source counters/checkpoint shape, and child 01's retained-cursor / `DEDUP_INCOMPLETE` contract. Preserve these interfaces exactly.
