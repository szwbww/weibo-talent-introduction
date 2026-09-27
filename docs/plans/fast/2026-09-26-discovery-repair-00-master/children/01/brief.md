# Child 01 Brief — Page replay after dedup failure

Approved child plan: `docs/plans/2026-09-26/discovery-repair-01-page-replay.md` (exact approved bytes committed as `b8789cb6062d9110218c08ce8099dba8dddd73e4`). Read the full child plan before implementation; it is authoritative for detailed tasks, fixtures, acceptance criteria, and command.

## Global constraints

- Master plan: `docs/plans/2026-09-26/discovery-repair-00-master.md`; exact plan set is in the plan-seed commit above.
- Master start/base SHA: `64c0394a940bd79c2ecc04e5c497650f045faa75`.
- This child runs first; no prior child outputs. Implement only this child, not later plans.
- Preserve master invariants M1–M4 and I-1–I-5. In particular: bind identity only with unique same-source evidence; no schema/persistence changes; no changes to eligibility, duplicate overwrite, sender rules, migration, production data, deploy, or scheduling; never conflate parse loss with ineligibility; do not claim projected counts as recovered production results.
- No scope expansion. If completion requires an unlisted file or behavioral choice, report PLAN_CONFLICT/BLOCKED rather than editing it.

## Authorized Files

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
2. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`

No other product/test files may change.

## Child requirements and invariants

- I-1: A page is complete only when dedup-error, RAW-write-failure, and enqueue-failure deltas are all zero and page processing was not interrupted by cancellation/limit/time. Apply to both synchronous page loops.
- I-2: Preserve the entering cursor on a pure dedup failure; set existing stop-reason value `DEDUP_INCOMPLETE`, `ACTIVE`, `pendingWork=true`, and terminal `PARTIAL_SUCCESS` for the single-source case. Cancellation remains `CANCELLED`; for simultaneous errors preserve precedence `RAW_WRITE_INCOMPLETE > ENQUEUE_INCOMPLETE > DEDUP_INCOMPLETE`, retaining all counts.
- I-3: Replay is idempotent: existing email dedup/create semantics and existing document identity remain unchanged; queue `DEDUP_LOOKUP_FAILED` behavior remains retryable.
- I-4: Preserve master M1–M3 business boundaries; no new persistence, admission, sending, or scheduling behavior.
- Required tests must cover page-cursor combinations, ORCID symmetry, interrupted/error combinations, replay after dedup failure, and existing eligibility/identity boundaries according to the child plan.
- Required acceptance output, if implemented per plan, must be based on actual isolated function/fixture output and must not fake a pass flag.

## Required command

`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,DiscoveryCheckpointCodecTest,DiscoveryResultTest,DiscoveryPipelineServiceTest,ExpertIndexWriterServiceTest`

Run from the worktree root. Record exact exit code and test counts. Do not run a project-wide suite in the fast-p child loop.

## Downstream interface

Child 02 onward consumes child 01's code head as its product base. Preserve existing `DiscoveryStopReason`/stop-reason string and status/checkpoint read contracts; new stop reason is only the string value `DEDUP_INCOMPLETE`, not a schema field.
