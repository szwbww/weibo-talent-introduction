# Repair Plan: trusted-reply-shared-workbench-01-shared-runtime-api

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-07-27/trusted-reply-shared-workbench-01-shared-runtime-api.md
Verification report: verify-p phase1 re-verification, 2026-07-28
Implementation boundary: working tree — the six untracked phase1 files named by the baseline plan

## Objective

Ensure every public workbench generation uses the shared SSE/cancellation coordinator.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-5 — one singleton coordinator manages both-source generations, including cancellation and lifecycle | The unplanned synchronous `POST /api/trust-reply/workbench/generations` calls `TrustReplyWorkbenchService.generate` directly. It bypasses coordinator registration, active limit, canonical generation ID, progress/SSE, and cancellation. |

## Findings Excluded

| Finding | Reason |
|---|---|
| N/A | No other confirmed repairable behavioral finding. |

## Unchanged Contract

- Bootstrap, stream generation, and scoped cancellation retain their approved paths and DTO contracts.
- Every generation remains routed through `TrustReplyWorkbenchService.generate` from the coordinator operation.
- No send, adoption, evaluation, repository, legacy-controller, or frontend behavior changes.
- Existing six-file phase1 boundary remains the only implementation boundary.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt | Remove the coordinator-bypassing synchronous generation endpoint. |
| src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt | Replace the direct-endpoint expectation with a regression assertion that the endpoint is unavailable while stream generation remains scoped. |

## Repair Tasks

### R-1: remove the uncoordinated public generation path

- Resolves: V-1
- Root cause: `TrustReplyWorkbenchController.kt:41-43` exposes `POST /generations` and calls the service directly; only `:45-71` enters `AiReplyGenerationCoordinator`.
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`, `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt`
- Change: Remove only the synchronous `POST /generations` mapping and its handler. Keep the approved `/generations/stream` route as the sole public generation route.
- Regression test: MockMvc proves `POST /api/trust-reply/workbench/generations` has no handler (404); existing stream/cancel test still proves the canonical source scope reaches the coordinator.
- Existing verification: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=TrustReplyWorkbenchControllerTest,AiReplyGenerationCoordinatorTest,TrustReplyWorkbenchServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest,GroundedAutoReplyDecisionServiceTest,PendingMailOperationServiceTest test`
- Must not change: service source resolution, `AiReplyGenerationCoordinator`, stream DTO fields, cancellation results, old routes, or send paths.
- Prohibited: Adding a replacement synchronous generation endpoint, changing non-workbench files, modifying product behavior beyond V-1, or editing this baseline plan.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=TrustReplyWorkbenchControllerTest test`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=TrustReplyWorkbenchControllerTest,AiReplyGenerationCoordinatorTest,TrustReplyWorkbenchServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest,GroundedAutoReplyDecisionServiceTest,PendingMailOperationServiceTest test`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`

## Completion Criteria

- `POST /api/trust-reply/workbench/generations` returns 404; no synchronous public generation handler remains.
- `/generations/stream` still scopes generation as `<sourceType>:<sourceId>` and delegates through `AiReplyGenerationCoordinator`.
- All three verification commands pass.
- Changed files remain inside the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
