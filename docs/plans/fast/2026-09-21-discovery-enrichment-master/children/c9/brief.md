# Child Brief c9 — 09 公平额度与逐步扩量

## Identity

- Child ID: **c9**; approved plan: `docs/plans/2026-09-21/09-discovery-throughput.md` (plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`; read the file from disk in full — it is the complete approved contract).
- Master plan (design baseline, do not edit): `docs/plans/2026-09-21/00-discovery-enrichment-master.md`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master` (branch `fast/2026-09-21-discovery-enrichment-master`). Run every read/edit/test/commit there.
- `child_base_sha`: the c8 code head recorded in the ledger (`docs/plans/fast/2026-09-21-discovery-enrichment-master/ledger.md`, child c8 `Code head`); c1–c8 are complete and independently verified.
- Environment: JDK 11 mandatory; prefix Maven with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
- Execution: use `skill://execute-p`; report path `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c9/execution.md`.

## Authorized files (exactly these; nothing else)

| File | Role |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/config/ExpertDiscoveryProperties.kt` | typed config |
| `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexProperties.kt` | typed config |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | production |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SourceStats.kt` | data contract |
| `src/main/resources/application.yml` | env vars + defaults |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt` | test |
| `docs/plans/2026-09-21/discovery-enrichment-rollout.md` | rollout runbook + verification record (new) |

Helper DTOs/enums live inside these files (no extra files). No new external dependency. No frontend change and no new columns on the existing task tables (extra counters go into the existing `message`/`details_json`). Never edit an applied migration. Do not touch the pre-existing unrelated working-tree changes of the primary checkout, and never `git add` `docs/plans/fast/**`. The runbook must contain no API key and no credentials.

## Required command

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest
```

Run it freshly after the final implementation state, in this worktree. The full `mvn test` suite is an integration-stage gate and must not be run for this child.

## Invariants to preserve (from the approved plan)

- **I-1 批量不等于任务总量**: the enrichment batch (100) and the paper page (100) are per-batch sizes; the suggested run-level targets OpenAlex 10000 / all paper sources 15000 / authors 20000 are independently configurable. `0` must never silently mean unlimited, and when the global cap cannot cover every source's base share the run must fail its start validation with a clear configuration error instead of starving later sources.
- **I-2 公平且不越界**: give every enabled paper source at least one page of base share first, then run in per-source quota order; another source's reservation can never be consumed by OpenAlex; a dead or genuinely exhausted source releases its remainder for reuse inside the source caps. The ORCID record limit is independent (1000) and still bounded by the 20000 total author guard.
- **I-3 按明确停止原因续跑**: API budget, source cap, global cap, the 4-hour time budget and cancellation each record their own `stopReason` and all of them save the current page checkpoint. Never auto-skip an unknown page just because the duplicate rate is high. The daily quota resets on the UTC day; the 02:00 schedule stays Asia/Shanghai; the worker resumes enrichment at the real reset.
- **I-4 上线凭新增实测**: restore small end-to-end first, then step 2500 → 5000 → 10000, comparing unique new experts, enrichment success rate, elapsed time and failure distribution; never use searched-metadata counts or the nominal API maximum as the completion metric.

## Master constraints that also apply

- M-1/M-3: email validation, dedup, eligibility gates and real expert IDs unchanged; existing name/email/affiliation/operator status never overwritten; no mail sent by this run.
- M-4 default R&D scope unchanged (EuropePMC / PMC OA stay excluded from the default run). M-5 no paid API usage, never store or display the key, never modify an applied migration; the free budget ceiling stays a configuration knob, never a hardcoded claim of full-platform coverage.
- Master I-1: source cap, global cap, author cap, account budget and wall-clock remain separate constraints and each stop names itself.
- Keep `fetchConcurrency = 4` — this child does not raise concurrency.

## Available outputs from earlier children

- c1: `OpenAlexRequestPolicy` provides the real remaining budget and `resetAt`; the run-level budget stop must use it rather than a local guess.
- c2: checkpoint codec + `SourceRunOutcome`/stop reasons; every new stop reason must save the entering page's cursor.
- c3/c4: source adapters with the catalogue topics, CORE offset shards, ORCID paging.
- c8: `ExpertDiscoveryProperties` now also carries the worker switch/batch knobs; enrichment draws from OpenAlex with `NEW_ENRICHMENT` priority (`NEW_ENRICHMENT` is the only kind allowed inside the reserved share).

## Downstream interfaces later children consume (keep these exact)

- Per-source counters/reporting in `SourceStats` stay additive; the source summary must still separate paper counts from ORCID record counts (historical `papersSearched` semantics for ORCID stay compatible).
- Stop-reason strings are the cross-child vocabulary (`API_BUDGET`, source cap, `GLOBAL_CAP`, `TIME_BUDGET`, `CANCELLED`, `WINDOW_LIMIT`) consumed by the task summary and by c10's funnel reporting — keep them stable and explicit.
- Config knobs live in `ExpertDiscoveryProperties`/`OpenAlexProperties` with `application.yml` env overrides, so the rollout runbook and c10 can reference the same names.
- The runbook `docs/plans/2026-09-21/discovery-enrichment-rollout.md` is the release record the human review reads; it must state each step's measured numbers, the rollback switch, and the cursor/restore procedure — without keys.

## Commit

Commit the implementation locally as exactly `feat(fast-p): implement c9`. Exclude `docs/plans/fast/**` from the commit. Do not push, merge, rebase, amend, or squash.

## Stop conditions

Return `PLAN_CONFLICT` (do not improvise) if completion needs an unlisted file, a new behavioral decision, or a plan interpretation the approved bytes do not uniquely determine. Return `BLOCKED` with the smallest missing information/environment change otherwise.

## Return to controller (only this)

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, one-line command summary with exit codes/counts, report path.
