# Child Brief c2 — 02 发现进度保护与真实任务状态

## Identity

- Child ID: **c2**; approved plan: `docs/plans/2026-09-21/02-discovery-checkpoint.md` (plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`; read the file from disk in full — it is the complete approved contract).
- Master plan (design baseline, do not edit): `docs/plans/2026-09-21/00-discovery-enrichment-master.md`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master` (branch `fast/2026-09-21-discovery-enrichment-master`). Run every read/edit/test/commit there.
- `child_base_sha`: `147dc953a194a27ea73b7934a8e2bc334beca655` (child c1's code head; c1 is complete and verified separately).
- Environment: JDK 11 mandatory; prefix Maven with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
- Execution: use `skill://execute-p`; report path `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c2/execution.md`.

## Authorized files (exactly these; nothing else)

| File | Role |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | production (the single discovery pipeline) |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryStats.kt` | data contract |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SourceStats.kt` | data contract |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryResult.kt` | data contract |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodec.kt` | production (new) |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodecTest.kt` | test (new) |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryResultTest.kt` | test |

Helper DTOs/enums live inside these files (no extra files). No new external dependency. Never edit an applied Flyway migration (`discovery_source_cursor` schema is V32 and must not change). Do not touch the pre-existing unrelated working-tree changes of the primary checkout, and never `git add` `docs/plans/fast/**`.

## Required command

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,DiscoveryCheckpointCodecTest,DiscoveryResultTest
```

Run it freshly after the final implementation state, in this worktree. The full `mvn test` suite is an integration-stage gate and must not be run for this child.

## Invariants to preserve (from the approved plan)

- **I-1 检查点只在安全边界推进**: save `nextCursor` only after each fully consumed page; a first-request failure, a partial page, cancellation, or a budget stop keeps the cursor that entered that page. Exceptions are distinct from empty/exhausted results; a filtered-empty page that still has `nextCursor` keeps paging. A page whose RAW write (or later required enqueue) did not complete must not advance the checkpoint.
- **I-2 查询隔离**: `source_name` is `SOURCE:v2:<24-char SHA256>` and stays within `VARCHAR(50)`; the hash covers the normalized real criteria, scope, years, page size and query version — never a transient cursor. `cursor_value` stores a versioned envelope distinguishing `ACTIVE`/`EXHAUSTED`; `EXHAUSTED` may reopen on the next scheduled scan cycle, but `FAILED` never resets. Do not silently adopt legacy rows written for unknown criteria; keep them as backup only.
- **I-3 状态一致**: search-layer failures count as task failures; some sources succeeded = `PARTIAL_SUCCESS`, all sources failed = `FAILED`, a genuine empty result = `SUCCESS`, early stop on budget/time with pending work = `PARTIAL_SUCCESS`, user cancel = `CANCELLED`. Progress `COMPLETED` corresponds to record `SUCCESS`; all other states must agree. Result and progress must share one terminal-status decision function.
- **I-4 不混合失败单位**: keep `indexed` as `success_count`; `failure_count` adds terminal source errors and is explained separately in the summary — never count one retry as one failed expert. TLS/IO: at most 2 retries per page; 400 is not retried; cancellation exits immediately. Source failures must reach `DiscoveryResult.taskFailureCount` (today they do not — that is the defect).

## Master constraints that also apply

- M-1 email validation/dedup/eligibility gates and real expert IDs unchanged; no mail sent by this run. M-2 the manual "补充学术数据" entry and its three scopes keep working. M-3 existing name/email/affiliation/operator status never overwritten. M-4 default R&D scope unchanged (EuropePMC/PMC OA stay excluded). M-5 no paid API usage, never store or display the key, never modify an applied migration; no new column on shared ES indices or the existing task tables.
- Master I-1: paper pages (100), enrichment batches (100), source caps, global cap, account budget and wall-clock are separate constraints — each stop must name its own reason.
- Master I-5: day-quota waiting resumes at the real UTC reset; network failures are retryable; "no identity/no data" is listed separately; discovery counts never absorb enrichment counts.

## Available outputs from earlier children

- c1 (complete): `config/OpenAlexRequestPolicy.kt` owns the shared daily budget/rate for every OpenAlex call. When a call may not proceed it raises `OpenAlexBudgetDeferredException(resetAt: Instant)` (extends `IllegalStateException`), and the HTTP layer also surfaces 429/5xx. Treat that exception, when it reaches the discovery loop, as a **quota stop reason that preserves the entering cursor** — not as a search failure that clears progress, and never as `exhausted = true`.

## Downstream interfaces later children consume (keep these exact)

- `SourceRunOutcome(resumeCursor: String?, exhausted: Boolean, stopReason: String)` per source run, as named in the plan — a catch block must produce a failed/deferred outcome, never `exhausted = true`.
- `DiscoveryCheckpointCodec` is the only place that encodes/decodes the `SOURCE:v2:<24>` key and the versioned cursor envelope; c4 (CORE offset envelope, ORCID shard cursor) reuses it rather than inventing a second encoding.
- `nonPersistableCursorSources` must stop excluding `CORE` in a way that c4 can complete (c4's authorized set includes `ExpertDiscoveryService.kt` too); do not implement CORE's offset pagination here, only remove the structural blocker and keep the seam explicit.
- Source-name length must stay ≤ 50 characters for every source name the codec can produce (VARCHAR(50) in V32).
- Keep the existing public entry points (`discover(...)`, keyword and scheduled callers) and existing `DiscoveryResult`/`SourceStats` field names; additive-only changes. The task UI and `TaskProgressStore`/`TaskExecutionService` readers must keep working without frontend changes.

## Commit

Commit the implementation locally as exactly `feat(fast-p): implement c2`. Exclude `docs/plans/fast/**` from the commit. Do not push, merge, rebase, amend, or squash.

## Stop conditions

Return `PLAN_CONFLICT` (do not improvise) if completion needs an unlisted file, a new behavioral decision, or a plan interpretation the approved bytes do not uniquely determine. Return `BLOCKED` with the smallest missing information/environment change otherwise.

## Return to controller (only this)

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, one-line command summary with exit codes/counts, report path.
