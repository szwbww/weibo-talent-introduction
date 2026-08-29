# Repair Plan: 10-reply-orchestration-order

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/2026-08-28/10-reply-orchestration-order.md` (sha256 `31d991f1dfaf75912153df79a3b738a9cb3b89ceafbe8e962783f34d9bb525be`)
Verification report: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/review/2026-08-28-reply-orchestration-order/machine-verification.md`, epoch 3 (`FAIL`, `PROGRESSING`; controller-owned report destination)
Implementation boundary: `7f8b28d2f09c0df7551703d8037c2b521b189152..6793ff948515e541969f76388e0af5bde1fd2f3a`

## Objective

Allow final assembly to accept, validate, compose, and archive authoritative step-03 paragraphs containing `op<n>` operator-fact slots, without weakening source closure or changing non-operator paragraph behavior.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | Plan 15 I-2 and final-assemble semantics: the step-03 draft, including operator-fact slots, must enter the final letter verbatim and in sequence. | `TrustReplyWorkbenchService.validateFinalParagraphState` rebuilds required IDs only from locked versions (`f*`/`x*`), but the canonical step-03 draft uses submitted `op<n>` IDs for operator facts. Because `submittedSet` is compared to the version-only set before operator facts are added to validation, every authoritative paragraph containing `op<n>` is rejected with `TRUST_REPLY_FINAL_PARAGRAPHS_INVALID`. |
| V-3 | P1 | Plan 12 IP-4 and plan 16 T-4: archive the final closed paragraph separately from per-item `answerText`. | The repaired mapping for operator facts runs only after the same invalid version-only ID equality check. Therefore the primary operator-directed path cannot produce `finalParagraphByRequestKey` or an archiveable `finalParagraphText`. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-2 | RESOLVED in epoch 3: both callers delegate to `UnsupportedAnswerIndexService.isArchiveEligible`, whose four-handling × two-generation allow-list matches document validation and keeps the training/live approval gates. |
| Flyway runtime integration gate | BLOCKED evidence, not a product finding: Docker is unavailable (`DOCKER_HOST` socket not listening; `/var/run/docker.sock` absent). No prior skip is inferred as a waiver. |
| Fast-P `RECORD_ONLY` observations | Reassessed without a mandatory violation; no repair authority. |

## Unchanged Contract

- G-1..G-7 and approved amendments A1/A2/A3/A4 remain unchanged; operator IDs never enter request/version/evidence hashes.
- Server-side assembly must still reject stale/foreign versions, facts, frames, duplicate or missing required facts, paragraph actions, and non-verbatim controlled/frozen/operator facts.
- The step-03 fact-ID universe remains the canonical `f*`/`x*`/`op<n>` protocol already returned by rearrange; do not create a second identity space.
- Non-operator evidence facts, standalone non-operator facts, all-locked closer fallback, per-item persistence, and the single authorized CTA channel remain unchanged.
- `answerText` remains the canonical per-item answer. `finalParagraphText` remains a separate final closed-paragraph sample and must fail closed on missing or ambiguous mapping.
- Training `MEETS_EXPECTATION`, successful live-send/replay gates, archive failure isolation, and the exact plan-16 eligibility set remain unchanged.
- No schema migration, endpoint addition, UI redesign, retry/outbox/re-send behavior, cache-buster change, or archive eligibility change is authorized.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | Reconcile canonical `op<n>` slots with their owning locked operator-directed versions during exact-once closure and deterministic final-paragraph mapping. |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | Add the discriminating server regression for authoritative operator-fact final assembly, composition, mapping, and fail-closed variants. |
| `src/test/js/trustReplyWorkbenchThreeStep.test.js` | Make the final-assemble browser regression carry the real `op<n>` paragraph/operatorFacts shape instead of a fixture that drops operator facts before assemble. |

## Repair Tasks

### R-1: Use one canonical identity closure for operator facts at final assemble

- Resolves: V-1, V-3.
- Root cause: final assembly compares submitted paragraph IDs with a required set reconstructed as `f*`/`x*` from versions before considering submitted canonical `op<n>` facts. A locked `ANSWER_FROM_OPERATOR_INPUT` version represented by `op1` in the step-03 draft is reconstructed as `x<n>`, so the equality gate rejects the valid draft and prevents mapping/archive.
- Files: the three Authorized Files above.
- Change: Validate the submitted `op<n>` facts as part of the same canonical exact-once identity closure used by the step-03 draft. Bind each operator fact to exactly one compatible locked version using existing operator-fact semantics; do not simultaneously require the replaced synthetic `x<n>` identity. Then reuse the existing six-check rearrangement validator, compose submitted paragraphs verbatim/in order, and map the owning request key to its unique containing final paragraph. Reject foreign, duplicate, missing, body-mismatched, or ambiguously owned operator facts.
- Regression test: Submit a locked operator-directed version plus `operatorFacts=[op1]` and `finalParagraphs.factIds=[op1]`; assert exact composition and `requestKey -> final paragraph` mapping. Add mixed `f* + op<n>` coverage and fail-closed cases for missing/foreign/duplicate/non-verbatim/ambiguous operator facts. Update the browser test so its rearrange response retains `op<n>` and assert the final `/assemble` payload carries matching paragraph IDs and operator facts.
- Existing verification: rerun the focused workbench service and three-step browser suites, then all Verification Commands.
- Must not change: evidence-only `f*` behavior, genuine non-operator `x*` behavior, request-key hashing, stale/source/frame validation, action policy, final-paragraph ordering, mapping fail-closed behavior, or archive eligibility.
- Prohibited: trusting arbitrary client IDs/bodies, matching operator facts by ID alone, falling back to `answerText` for `finalParagraphText`, weakening required exactly-once checks, adding LLM calls, changing routes/DTOs/index schema, or touching files outside Authorized Files.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=TrustReplyWorkbenchServiceTest test`
2. `node --test src/test/js/trustReplyWorkbenchThreeStep.test.js`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
5. `node --test src/test/js/*.test.js`
6. `git diff --check`
7. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`

Command 7 remains mandatory for post-repair verification. If Docker remains unavailable, record exact evidence and obtain a new human decision; prior execution-specific skips do not waive it.

## Completion Criteria

- A valid authoritative final draft containing `op<n>` passes exact-once/source/verbatim validation and is composed byte-for-byte in submitted paragraph order.
- The owning operator-directed request maps deterministically to its unique final paragraph, and archive receives that paragraph separately from `answerText`.
- Mixed evidence/operator paragraphs work; missing, foreign, duplicate, body-mismatched, or ambiguous operator facts fail closed.
- Existing evidence-only `f*`, non-operator `x*`, fallback, action, source/version/frame, eligibility, and archive-isolation tests remain green.
- Changed files remain exactly inside the Authorized Files list.

## Human Approval

Execution is prohibited until the human explicitly approves this current plan.

To approve and execute this repair, send:

`$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/fix/10-reply-orchestration-order/repair.md`

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/fix/10-reply-orchestration-order/repair.md` invocation authorizes:

1. Changes only to these Authorized Files:
   - `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`
   - `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt`
   - `src/test/js/trustReplyWorkbenchThreeStep.test.js`
2. Running all required verification commands:
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=TrustReplyWorkbenchServiceTest test`
   - `node --test src/test/js/trustReplyWorkbenchThreeStep.test.js`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
   - `node --test src/test/js/*.test.js`
   - `git diff --check`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`
3. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only the Authorized Files, with commit subject `fix(reply-orchestration): preserve operator facts in final assembly`.
4. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/review/2026-08-28-reply-orchestration-order/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
5. Exactly one docs-only evidence commit containing only that execution handoff, with commit subject `docs(review-fast-p): record repair execution`.
6. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it, using `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/fast/2026-08-28-reply-orchestration-order/human-review-handoff.md` and the committed `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/review/2026-08-28-reply-orchestration-order/repair-execution.md`. Do not ask the human to relay executor metadata.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
