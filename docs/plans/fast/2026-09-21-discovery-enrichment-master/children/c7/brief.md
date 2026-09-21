# Child Brief c7 — 07 可恢复的补全任务存储

## Identity

- Child ID: **c7**; approved plan: `docs/plans/2026-09-21/07-enrichment-job-store.md` (plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`; read the file from disk in full — it is the complete approved contract).
- Master plan (design baseline, do not edit): `docs/plans/2026-09-21/00-discovery-enrichment-master.md`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master` (branch `fast/2026-09-21-discovery-enrichment-master`). Run every read/edit/test/commit there.
- `child_base_sha`: the c6 code head recorded in the ledger (`docs/plans/fast/2026-09-21-discovery-enrichment-master/ledger.md`, child c6 `Code head`); c1–c6 are complete and independently verified.
- Environment: JDK 11 mandatory; prefix Maven with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`. Docker is available for the MySQL integration command.
- Execution: use `skill://execute-p`; report path `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c7/execution.md`.

## Authorized files (exactly these; nothing else)

| File | Role |
|---|---|
| `src/main/resources/db/migration/V131__create_expert_academic_enrichment_job.sql` | new migration (version to be confirmed against the repo, see below) |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/ExpertAcademicEnrichmentJob.kt` | data contract |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/ExpertAcademicEnrichmentJobRepository.kt` | persistence |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentJobService.kt` | production |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/ExpertAcademicEnrichmentJobRepositoryIT.kt` | integration test (real MySQL) |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentJobServiceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | pinned version update |

Helper DTOs/enums live inside these files (no extra files). No new external dependency. Spring Data JDBC + explicit queries only (no JPA). Do not touch the pre-existing unrelated working-tree changes of the primary checkout, and never `git add` `docs/plans/fast/**`.

**Migration version:** the plan proposes `V131` and states the local maximum is `V130`. Before writing the file, list `src/main/resources/db/migration`, confirm the real highest applied version, and confirm every place that pins the latest version (at least `FlywayMigrationIntegrationTest`) so the new migration is the next one in release order. If the real maximum differs from `V130`, use the correct next version and the same filename suffix; a version collision may be resolved by renaming the not-yet-published migration only. Never edit an applied migration and never enable out-of-order.

## Required command

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertAcademicEnrichmentJobServiceTest,ExpertAcademicEnrichmentJobRepositoryIT,FlywayMigrationIntegrationTest -DmigrationIt=true
```

The repository integration test must run against a real, isolated MySQL (follow the project's existing integration-test pattern for containers/URLs). H2 is not an acceptable substitute for the persistence-recovery contract. If the environment cannot run the container, say so explicitly with the exact error instead of silently dropping the test.

## Invariants to preserve (from the approved plan)

- **I-1 持久化唯一事实**: a new dedicated job table is the only lifecycle store; no new root-level status field in Elasticsearch. `UNIQUE(expert_doc_id)`; statuses `PENDING` / `RUNNING` / `RETRY_WAIT` / `SUCCEEDED` / `UNMATCHED` / `FAILED`; repeated enqueues for the same expert merge into one row; an expert that already succeeded within 30 days is not re-enqueued; a change of trusted identity may reopen an `UNMATCHED` row.
- **I-2 租约和竞争**: claim with a transactional conditional update plus a `lease_token`; lease 10 minutes; the worker renews per batch; completion must match the token. A crashed process's expired `RUNNING` row becomes claimable again. Do not hold a DB transaction across external HTTP calls. MySQL version is unknown — do not rely on `SKIP LOCKED`.
- **I-3 重试分类**: 429 / daily-budget exhaustion do not consume a failure attempt and set `next_attempt_at` from the reset; network/5xx back off 1m / 5m / 30m / 2h and reach `FAILED` after at most 5 attempts; no reliable id or no author found is `UNMATCHED` and must never be faked as `SUCCEEDED`. Manual retry may explicitly reopen a `FAILED` row.
- **I-4 迁移只前进**: new migration only, correct next version, never edit an applied migration, never enable `out-of-order`.

## Master constraints that also apply

- M-1/M-3: only real expert identities and existing contact links; the job table stores `expert_doc_id` (the real ES `_id`) and never a name/email-derived key. M-2 manual entries keep working. M-5 no paid API usage, never store or display the API key.
- Master I-5: the job's terminal semantics must agree with the task/progress record; day-quota waiting resumes at the real UTC reset.
- `last_error` must not contain the API key or a plaintext email; keep it bounded (`VARCHAR(1000)`).

## Available outputs from earlier children

- c6: `ProfileEnrichmentOutcome` variants (`Success, Partial, Deferred, NotFound, NoId, RetryableError`) and `LayerUpdateResult` — the service maps exactly those variants onto the job statuses; `Deferred` never increments attempts.

## Downstream interfaces later children consume (keep these exact)

- `enqueue(docId, source, executionId)`, `claimDue(limit, now)` and `complete(id, leaseToken, outcome)` as named in the plan are the c8 worker's only entry points; keep those names/shapes.
- `ExpertAcademicEnrichmentJob` field names are the persisted contract: `id`, `expertDocId`, `source`, `discoveryExecutionId`, `status`, `attempts`, `nextAttemptAt`, `leaseToken`, `leaseUntil`, `lastError`, `resultJson`, `createdAt`, `updatedAt`; index on `(status, next_attempt_at, id)`.
- `discovery_execution_id` is what c8 records for audit attribution; it must remain nullable.
- c8 must be able to run the worker with the job table as the only cross-process state (no in-memory queue), and must be able to disable the whole path with a config flag (default false) — keep the service injectable/optional accordingly (this child creates the store; c8 adds the scheduler and the switch).

## Commit

Commit the implementation locally as exactly `feat(fast-p): implement c7`. Exclude `docs/plans/fast/**` from the commit. Do not push, merge, rebase, amend, or squash.

## Stop conditions

Return `PLAN_CONFLICT` (do not improvise) if completion needs an unlisted file, a new behavioral decision, or a plan interpretation the approved bytes do not uniquely determine. Return `BLOCKED` with the smallest missing information/environment change otherwise.

## Return to controller (only this)

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, one-line command summary with exit codes/counts, report path.
