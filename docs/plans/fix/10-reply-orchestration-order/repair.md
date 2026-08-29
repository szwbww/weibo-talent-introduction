# Repair Plan: 10-reply-orchestration-order

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/2026-08-28/10-reply-orchestration-order.md
Verification report: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/review/2026-08-28-reply-orchestration-order/machine-verification.md (epoch 4, independent aggregate re-review)
Implementation boundary: de228e17cc0134a7c11dea7cbf82054e8d249f99..0d45505d68261c14f3866e3f440b2ea08195f1de

## Objective

Final assembly accepts an operator fact only in the canonical `op<n>` identity space, while preserving the existing exact-once, source, verbatim, action, request-key, and final-paragraph mapping behavior.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-4 | P1 | Plan 15 I-1/I-2 and G-7: operator facts use the isolated `op<n>` protocol, never a second client-controlled identity space or request-key input. | `validateFinalParagraphState` accepts every nonblank `operatorFacts[].id`, then adds it to `requiredIds` and passes it as a known fact to `validateRearrangement`; neither layer requires the `op<n>` form. A caller can therefore submit an arbitrary ID with a body matching one locked operator-owned version and have it composed/mapped. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | RESOLVED in epoch 4: valid canonical `op<n>` facts bind to exactly one operator-owned unit, replace its synthetic `x<n>` identity in exact-once closure, compose verbatim, and map the owning request. |
| V-2 | RESOLVED in epoch 3: training and live archive callers delegate to the canonical plan-16 allow-list. |
| V-3 | RESOLVED in epoch 4: final archive mapping uses the validated unique final paragraph and fails closed when absent or ambiguous. |
| Flyway runtime integration gate | HUMAN_EXCEPTION / NOT_RUN for epoch 4 by the explicit instruction to ignore Flyway IT; not a repair finding. |
| Fast-P RECORD_ONLY observations | Re-evaluated: none proves a current mandatory violation or changes this root cause. |

## Unchanged Contract

- `requestKey` remains exactly the existing four-input hash; no operator ID or operator body enters it.
- Valid `op<n>` facts remain frozen, required, verbatim slots and bind by normalized body to exactly one `ANSWER_FROM_OPERATOR_INPUT` unit.
- Existing `f*` evidence identities, genuine `x*` identities, source/version/frame validation, exact-once coverage, action policy, fallback closer behavior, final paragraph order, and fail-closed mapping remain unchanged.
- No route, DTO shape, schema, index/archive eligibility, LLM call, or frontend behavior is changed.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | Reject noncanonical operator-fact identities before they can enter final-paragraph identity closure. |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | Add the discriminating malformed-ID regression without weakening valid operator-fact coverage. |

## Repair Tasks

### R-1: Enforce the canonical operator-fact ID namespace at final assembly

- Resolves: V-4.
- Root cause: final assembly treats a submitted operator fact's arbitrary nonblank ID as authoritative once its body can be bound to an operator-owned unit.
- Files: the two Authorized Files above.
- Change: before constructing the final required-ID set, reject every operator fact whose ID is not exactly `op` followed by a positive decimal sequence. Keep the existing duplicate, foreign, body-mismatch, multiple-owner, verbatim, and exact-once rejections intact.
- Regression test: submit an otherwise valid authoritative final paragraph and matching operator fact with a noncanonical ID such as `external-1`; assert `TRUST_REPLY_FINAL_PARAGRAPHS_INVALID` and that composition is not invoked. Retain a valid `op1` case proving the accepted path.
- Existing verification: rerun the focused workbench-service suite, then all aggregate verification commands.
- Must not change: `op<n>` facts remain independent from the hash; body-based ownership binding, legitimate `f*`/`x*` behavior, mapping, and archive eligibility retain their current semantics.
- Prohibited: accepting another operator-ID format, renumbering client IDs, deriving an ID from request/version hashes, trusting ID alone without the existing body-owner check, changing routes/DTOs/schema/index/archive logic, adding LLM calls, or touching files outside Authorized Files.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=TrustReplyWorkbenchServiceTest test`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
4. `node --test src/test/js/*.test.js`
5. `git diff --check`
6. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` — HUMAN_EXCEPTION / NOT_RUN for this approval epoch only.

## Completion Criteria

- A non-`op<n>` operator fact is rejected before composition or final-paragraph mapping.
- A valid `op<n>` operator fact still passes the existing exact-once/source/verbatim/action checks, composes in submitted order, and maps its owner deterministically.
- Existing evidence-only `f*`, non-operator `x*`, fallback, action, source/version/frame, final-paragraph mapping, eligibility, and archive-isolation regressions remain green.
- Changed files remain exactly inside the Authorized Files list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.

To approve and execute this repair, send:

`$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/fix/10-reply-orchestration-order/repair.md`

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/fix/10-reply-orchestration-order/repair.md` invocation authorizes:

1. Changes only to these Authorized Files:
   - `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`
   - `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt`
2. Running all required verification commands:
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=TrustReplyWorkbenchServiceTest test`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
   - `node --test src/test/js/*.test.js`
   - `git diff --check`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` — HUMAN_EXCEPTION / NOT_RUN only when the human explicitly re-approves that exception for this new approval epoch.
3. After all repair tasks and non-excepted required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only the two Authorized Files, with commit subject `fix(reply-orchestration): enforce operator fact IDs`.
4. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/review/2026-08-28-reply-orchestration-order/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
5. Exactly one docs-only evidence commit containing only that execution handoff, with commit subject `docs(review-fast-p): record repair execution`.
6. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the human invocation requests it, using `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/fast/2026-08-28-reply-orchestration-order/human-review-handoff.md` and the committed `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/review/2026-08-28-reply-orchestration-order/repair-execution.md`. Do not ask the human to relay executor metadata.

This authorizes no extra files, amendment, history rewrite, push, merge, deployment, or product repair beyond this plan.
