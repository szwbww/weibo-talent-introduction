# Repair Plan: discovery-resilience-two-hour

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour/docs/plans/2026-09-28/discovery-resilience-two-hour.md
Verification report: aggregate/master review (this review; controller report not yet written)
Implementation boundary: 3f167a2820c3f9bdb344f19da2123d6a9ff2f12a..8700a605427aaedb4c31be63a72e22a657b94208

## Objective

After a scheduled OpenAlex recovery starts or the run ends, retain the retry round and sanitized reason for audit while clearing only `nextRetryAt`.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-5 requires every start, cancellation, timeout, and end path to clear `nextRetryAt` but retain round/reason for audit. | `discoverFromSource` replaces `SourceStats.retry` with `null` whenever a recovery completes without a new pending item; tests assert that prohibited result. |

## Findings Excluded

| Finding | Reason |
|---|---|
| O-2 | Test-profile cron is outside the approved eight-file scope and does not prove enabled runtime behavior. |
| O-3 | Reason-label and running-summary wording are permitted non-unique presentation choices. |
| O-4 | Historical red-test form does not alter delivered runtime behavior. |
| O-5 | Mixed elapsed-time clock is an observation; no proven failure of the required accumulated elapsed-time behavior. |

## Unchanged Contract

- Keep recovery state in per-run memory only; no persistence queue, schema, ES field, or task-execution state.
- Do not alter retry eligibility, 3×3 attempt bounds, cursors/checkpoints, budgets, cancellation, scheduler, pipeline, TLS validation, or progress batch semantics.
- Preserve `retry` as the single observation object; a non-null `nextRetryAt` alone means a retry is pending.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt | Preserve the final recovery audit object while clearing its planned time. |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt | Prove completed, exhausted, and budget-deferred recovery paths retain round/reason with a null planned time. |

## Repair Tasks

### R-1: Retain completed-recovery audit state

- Resolves: V-1
- Root cause: the final assignment at `ExpertDiscoveryService.kt:1026-1033` uses only the newly pending retry; a completed recovery has no pending retry and therefore erases the required audit fields.
- Files: `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`; `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`
- Change: on recovery completion, terminal retry exhaustion, or budget deferral, retain the already-known retry `round`, `maxRounds`, and sanitized `reason` in `SourceStats.retry`; set `nextRetryAt` to `null`. A source with no recovery history remains without a retry object.
- Regression test: exercise recovery success, all recovery groups exhausted, and recovery-time budget deferral; each must expose the prior round/reason and `nextRetryAt == null` rather than a null retry object.
- Existing verification: run the focused discovery tests and the required master suite.
- Must not change: all I-1 through I-8 and X-1 through X-6 behavior outside the audit-retention correction.
- Prohibited: changes to schedules, configuration, properties, persistence, checkpoints, request policy, TLS, task-progress storage, pipeline behavior, or unrelated cleanup.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest,ExpertDiscoveryPropertiesTest,DiscoveryCheckpointCodecTest,OpenAlexRequestPolicyTest,DiscoveryPromotionProgressServiceTest,TaskProgressStoreTest,TaskProgressStoreRebindTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest,DiscoveryPipelineServiceTest`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
4. `git diff --check`

## Completion Criteria

- V-1 regression tests demonstrate `nextRetryAt` is cleared while round/reason are retained on every in-scope end path.
- No new retry is created from the audit object.
- Changed files remain inside the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour/docs/plans/fix/discovery-resilience-two-hour/repair.md` invocation authorizes:

1. Only these Authorized Files and required verification commands:
   - `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
   - `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest,ExpertDiscoveryPropertiesTest,DiscoveryCheckpointCodecTest,OpenAlexRequestPolicyTest,DiscoveryPromotionProgressServiceTest,TaskProgressStoreTest,TaskProgressStoreRebindTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest,DiscoveryPipelineServiceTest`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
   - `git diff --check`
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only those Authorized Files, with subject `fix(discovery): retain recovery retry audit`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour/docs/plans/review/2026-09-28-discovery-resilience-two-hour/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the human invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
