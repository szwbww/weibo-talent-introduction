# Child Brief c8 — 08 发现后自动补全与人工补偿

## Identity

- Child ID: **c8**; approved plan: `docs/plans/2026-09-21/08-auto-enrichment.md` (plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`; read the file from disk in full — it is the complete approved contract).
- Master plan (design baseline, do not edit): `docs/plans/2026-09-21/00-discovery-enrichment-master.md`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master` (branch `fast/2026-09-21-discovery-enrichment-master`). Run every read/edit/test/commit there.
- `child_base_sha`: the c7 code head recorded in the ledger (`docs/plans/fast/2026-09-21-discovery-enrichment-master/ledger.md`, child c7 `Code head`); c1–c7 are complete and independently verified.
- Environment: JDK 11 mandatory; prefix Maven with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
- Execution: use `skill://execute-p`; report path `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c8/execution.md`.

## Authorized files (exactly these; nothing else)

| File | Role |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | production (enqueue seam) |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentWorker.kt` | production (new worker) |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt` | keep and extend the existing entry |
| `src/main/kotlin/com/weibo/talentintroduction/config/ExpertDiscoveryProperties.kt` | typed config |
| `src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt` | production |
| `src/main/resources/application.yml` | env vars + defaults |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertAcademicEnrichmentWorkerTest.kt` | test |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt` | test |

Helper DTOs/enums live inside these files (no extra files). No new external dependency. No frontend change (the existing task UI already renders `PARTIAL_SUCCESS` and source detail; extra counters go into the existing `details_json`/`message`). Never edit an applied migration. Do not touch the pre-existing unrelated working-tree changes of the primary checkout, and never `git add` `docs/plans/fast/**`.

## Required command

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentWorkerTest,ExpertDiscoveryControllerTest
```

Run it freshly after the final implementation state, in this worktree. The full `mvn test` suite is an integration-stage gate and must not be run for this child.

## Invariants to preserve (from the approved plan)

- **I-1 RAW先落地再入队**: both new-entry paths — the paper `consumeOutcome` path and the separate ORCID loop — enqueue idempotently only after the RAW write succeeded, and a failed enqueue must not advance the current paper page (c2's checkpoint rule). On replay, an already-indexed discovered expert gets its missing job created without rewriting the whole expert document and without counting as new. Experts imported by other flows are not silently re-identified by an equal email. Dedup results must carry the matched document's real `_id`.
- **I-2 自动并行且有界**: the worker checks every 30 seconds and claims at most 100 due jobs; fewer than 100 still runs. It is a single independent worker that does not occupy the mail-scheduler threads; on time-slice end/cancel it saves the unfinished state; a daily quota exhaustion defers only OpenAlex enrichment while other source discovery continues. The switch defaults to **false** and is enabled on the server after acceptance.
- **I-3 统一补全与复评**: both automatic and manual paths reuse the c6 core; after success, only RAW-only experts get the targeted re-evaluation. The initial discovery eligibility gate stays as it is — no extra mandatory academic threshold, and existing candidates are never auto-demoted.
- **I-4 审计归属**: the discovery success count remains the number of newly indexed experts and never absorbs enrichment counts. The job row records `discovery_execution_id`; automatic enrichment gets its own `EXPERT_ENRICHMENT` task record whose details carry per-source enqueued/succeeded/pending/unmatched counts. Historical task details keep showing their own snapshot.

## Master constraints that also apply

- M-1/M-2/M-3: existing eligibility/dedup/contact links unchanged; the manual three scopes keep working; existing name/email/affiliation/operator status never overwritten; no mail is sent by this run.
- M-4 default R&D scope unchanged. M-5 no paid API usage, never store or display the key, never modify an applied migration.
- Master I-1: pages (100), batches (100), source caps, global cap, account budget and wall-clock are separate constraints; each stop reports its own reason.
- `discovery.scheduling.enabled` / `talent-introduction.scheduling.enabled` gating parity: preserve the existing pattern where the scheduler beans are only active when scheduling is enabled, and the worker switch is separate and default false.

## Available outputs from earlier children

- c2: `SourceRunOutcome`/checkpoint codec — the enqueue failure must leave the page unadvanced.
- c5: `externalIds.openAlexAuthorId` trusted identity.
- c6: `enrichProfiles(profiles, requestKind)` + `ProfileEnrichmentOutcome` + `revalidateEnrichedRaw(docId): PromotionOutcome` (`AlreadyPresent` when CANDIDATE or APPLICATION exists).
- c7: `ExpertAcademicEnrichmentJobService.enqueue/claimDue/complete` with lease tokens and the retry classification; the manual `DISCOVERY_PENDING` scope reopens failed/unmatched jobs through the same service.
- c1: `RequestKind.NEW_ENRICHMENT` for automatic enrichment and `HISTORY_ENRICHMENT` for manual backfill, released in `try/finally`.

## Downstream interfaces later children consume (keep these exact)

- The worker acquires the same enrichment task mutex as the manual entry (`TaskProgressStore.tryStartWithToken`) under the `EXPERT_ENRICHMENT` task type, so automatic and manual enrichment exclude each other while discovery stays unaffected.
- The added manual scope enum value is `DISCOVERY_PENDING`; the existing three scopes must remain callable with their current names.
- Config keys for the new switch/batch size live in `ExpertDiscoveryProperties` + `application.yml` with env overrides; c9 reads the same properties object for quota knobs — keep adding fields additive.
- The worker is a Spring bean the scheduler can drive, with the `@Scheduled(fixedDelay = 30000)` shape named in the plan, and must be constructible/injectable even when the feature switch is off (then it does nothing).

## Commit

Commit the implementation locally as exactly `feat(fast-p): implement c8`. Exclude `docs/plans/fast/**` from the commit. Do not push, merge, rebase, amend, or squash.

## Stop conditions

Return `PLAN_CONFLICT` (do not improvise) if completion needs an unlisted file, a new behavioral decision, or a plan interpretation the approved bytes do not uniquely determine. Return `BLOCKED` with the smallest missing information/environment change otherwise.

## Return to controller (only this)

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, one-line command summary with exit codes/counts, report path.
