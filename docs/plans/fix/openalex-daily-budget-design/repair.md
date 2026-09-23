# Repair Plan: OpenAlex Daily Budget Design

Status: DRAFT_READY — human approval required before execution.

## Baseline

- Approved master: `docs/plans/2026-09-22/openalex-daily-budget-design.md`
- Master identity: `sha256:120646415a1af9aad8c76fcfc7a072a551259df57844dd310bdd20c880e3b47e` at `ee1dfcd5439de54475c12ff51c9c713e984a82ec`
- Verified implementation boundary: `e2247680592603b091af791ef3629d70739a015b..13b82fde5d74836977d12e97b7f794a98803429d`
- Aggregate verification: 2026-09-23; result FAIL; convergence INITIAL.

## Objective

Repair the seven confirmed in-scope P1 failures without widening the approved OpenAlex budget, discovery pipeline, or continuous-run UI scope.

## Findings in Scope

| Finding | Failure | Repair outcome |
|---|---|---|
| V-1 | Pipeline-info load failure is written into a hidden launch modal. | Keep a visible configuration/status surface available before the modal opens. |
| V-2 | A rejected continuous launch leaves the page trigger in running state. | Restore the owning configuration controls and trigger state for all rejected launch paths. |
| V-3 | New source-detail rendering introduces inline styles. | Render with approved existing classes/status nodes only; add no inline styles. |
| V-4 | Rate-limit handling sleeps inline, potentially blocking HTTP/scheduler workers. | Return a deferred retry result immediately; retain retry metadata without sleeping. |
| V-5 | An untrusted/restarted budget ledger can present locally derived credits as available. | Persist/derive an explicit sync-deferred status and render unavailable budget fields as pending sync. |
| V-6 | A running pipeline with no progressable source can create recurring empty task executions. | Do not open/release a window or create task records when there is no progressable work. |
| V-7 | A deadline-ended window can be drained while its task is reported PARTIAL_SUCCESS. | Derive terminal task outcome from final pipeline state as well as the close reason. |

## Excluded Observations

| Observation | Reason excluded |
|---|---|
| V-8 | MySQL lifecycle coverage lacks an explicit reserved-floor branch; no runtime defect confirmed. |
| V-9 | Source error payloads may expose raw wait names; compatibility behavior remains defined. |
| V-10 | Unused lease-loss helper could desynchronize counters if later called; no reachable production path. |
| V-11 | Required MVC selector does not include every class-local test; selector itself is prescribed by the master. |

## Unchanged Contracts

- Request-cost classes, 403/429/error classification, atomic reservation/reclaim, and free endpoint behavior remain unchanged except for eliminating inline rate-limit waiting.
- Queue uniqueness, leases, durable extraction-before-consumption, source cursors, bounded retrieval, and fair scheduling remain unchanged.
- Pause/resume APIs, scheduler boundaries, migrations, ES, mail, and existing non-OpenAlex UI remain unchanged.
- No new CSS classes, stylesheet edits, migrations, routes, or configuration keys.

## Authorized Files

- `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt`
- `src/test/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicyTest.kt`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt`
- `src/main/resources/static/app.js`
- `src/test/js/discoveryContinuousRun.test.js`

## Repair Tasks

1. In the OpenAlex request policy, replace bounded inline rate-limit sleep with immediate `Deferred(retryAt)` behavior. Preserve rate-limit attempt metadata and ensure callers cannot issue an upstream request before retry time. Update policy tests to assert no sleep and deferred retry behavior. Resolves V-4.

2. Make an untrusted/restarted budget snapshot explicitly sync-deferred instead of locally usable. In the continuous-run budget renderer, show the applicable budget/available/reset fields as pending sync until a trusted ledger exists. Add focused Kotlin and Node coverage. Resolves V-5.

3. In pipeline scheduling, distinguish no progressable work from runnable work before creating a task execution. Derive final task terminal status from the final pipeline state so a drained window is successful even when its closing reason is deadline. Add service coverage for all-deferred/empty sources and deadline-drained completion. Resolves V-6 and V-7.

4. In the continuous-run UI, surface pipeline-info load failures in a visible pre-launch configuration/status area, restore the page trigger on every rejected launch path, and replace source-detail inline styling with existing approved presentation mechanisms. Add Node coverage for each recovery path and enforce no introduced inline style. Resolves V-1, V-2, and V-3.

## Required Verification

```sh
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=OpenAlexRequestPolicyTest,OpenAlexDataSourceTest,OpenAlexBudgetRepositoryIT -DmysqlIt=true -Dapi.version=1.40 test
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=DiscoveryPipelineServiceTest,DiscoveryPaperQueueRepositoryIT,ExpertDiscoveryServiceTest,RestTemplateConfigTest -DmysqlIt=true -Dapi.version=1.40 test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest test
node --check src/main/resources/static/app.js && node --test src/test/js/*.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -DskipTests clean package
```

## Acceptance Criteria

- No request-policy path sleeps or blocks a caller for rate-limit cooldown; it returns a deferred retry.
- Fresh/untrusted ledger status cannot be displayed as a confirmed remaining daily balance.
- Scheduler creates no empty recurring execution when every source is deferred or unavailable.
- Drained final state and reported task terminal status agree.
- Every continuous-launch failure leaves a visible error and restores actionable controls.
- The source-detail change contains no inline style attributes and uses only existing styling mechanisms.
- All required verification commands pass under Java 11; MySQL integration tests use the recorded Docker socket and API version.

## Approval Gate

This is a bounded repair draft. Do not execute or amend the master plan without human approval.

`$execute-p docs/plans/fix/openalex-daily-budget-design/repair.md`

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/openalex-daily-budget-design/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with product commit subject `fix(openalex): repair daily budget pipeline lifecycle`.
3. Appending `docs/plans/review/2026-09-22-openalex-daily-budget-design/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with evidence commit subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized aggregate re-review in the same task when the approval invocation requests it: `$review-fast-p docs/plans/fast/2026-09-22-openalex-daily-budget-design/human-review-handoff.md`.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
