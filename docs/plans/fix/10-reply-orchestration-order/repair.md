# Repair Plan: 10-reply-orchestration-order

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/2026-08-28/10-reply-orchestration-order.md` (sha256 `31d991f1dfaf75912153df79a3b738a9cb3b89ceafbe8e962783f34d9bb525be`)
Verification report: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/review/2026-08-28-reply-orchestration-order/machine-verification.md`, epoch 2 (`FAIL`, `PROGRESSING`)
Implementation boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..7f8b28d2f09c0df7551703d8037c2b521b189152`

## Objective

Make final assembly preserve the validated step-03 paragraph text/order and archive that final paragraph wording for every plan-authorized unsupported-answer shape.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | Plan 15 I-2 and final-assemble semantics: step-03 edits, merges, moves, pins, rearrangement, and operator-fact slots must enter the final letter; fully locked paragraphs must be used verbatim and in sequence. | `/assemble` accepts only versions/facts/frame/locked items and rebuilds paragraphs from item versions, so the authoritative step-03 draft is discarded. |
| V-2 | P1 | Plan 16 I-4: both existing archive triggers must admit the validator-approved four handling values × two generation kinds, with optional `operatorInstruction`, while retaining their approval/send gates. | Training and live callers apply narrower legacy filters before the widened service validator. |
| V-3 | P1 | Plan 12 IP-4 and plan 16 T-4: keep per-item `answerText` distinct from, and archive, the final closed paragraph wording. | Assembly exposes no per-topic final-paragraph seam, so archive documents substitute `version.answerText` for `finalParagraphText`. |

## Findings Excluded

| Finding | Reason |
|---|---|
| Flyway runtime integration gate | `HUMAN_EXCEPTION / NOT_RUN` applies to aggregate review epoch 2; it is not a product defect and grants no future execution waiver. |
| Fast-P `RECORD_ONLY` observations | Reassessed without a mandatory violation; no repair authority. |

## Unchanged Contract

- G-1..G-7, approved amendments A1/A2/A3, frozen IDs/bodies, controlled-fact exact-set behavior, and request-key identity remain unchanged.
- Step-01/02 local behavior, per-item version persistence, optimistic locks, explicit analysis start, and zero-request paragraph interactions remain unchanged.
- Server-side assembly remains authoritative: it must reject stale/foreign versions, facts, frames, invalid source closure, duplicated/omitted required facts, paragraph actions, and non-verbatim controlled/frozen or operator facts.
- `actionText` remains the single authorized CTA channel; no paragraph may create a second CTA.
- Archive remains gated by `MEETS_EXPECTATION` for training and successful live send/replay checks for live traffic; index content remains phrasing-only and never becomes grounding evidence.
- `answerText` remains the canonical per-item answer; `finalParagraphText` remains a separate final closed-paragraph sample.
- No schema migration, endpoint addition, retry/outbox/re-send behavior, cache-buster change, or UI redesign is authorized.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/resources/static/trust-reply-workbench.js` | Send the current authoritative step-03 paragraph state through final assemble. |
| `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` | Carry the final-paragraph request fields through the existing HTTP/domain boundary. |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | Validate and assemble from the submitted final paragraph state; expose validated final paragraphs to downstream archive paths. |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt` | Build `finalParagraphText` from the matching validated final paragraph while preserving per-item `answerText`; keep one canonical eligibility contract. |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt` | Admit the complete validator-approved archive shape after the existing training approval gate. |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | Admit the complete validator-approved archive shape after successful live-send/replay gates and pass final paragraphs to archive. |
| `src/test/js/trustReplyWorkbenchThreeStep.test.js` | Prove final assemble sends authoritative edited/reordered/pinned/operator-fact paragraphs. |
| `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt` | Prove the HTTP/domain assemble seam preserves all final-paragraph fields. |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | Prove validation and final composition use the submitted paragraph text/order verbatim and reject invalid/stale paragraph state. |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexServiceTest.kt` | Prove final paragraph mapping is distinct from item answer text and preserves validator rules. |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationServiceTest.kt` | Prove every allowed training shape reaches archive and approval gating remains. |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | Prove every allowed live shape reaches archive, including empty instruction, and send/replay isolation remains. |

## Repair Tasks

### R-1: Preserve authoritative final paragraphs across assemble and archive

- Resolves: V-1, V-3.
- Root cause: The final assemble request/response has no authoritative paragraph seam; the service re-closes item versions and archive falls back to `answerText`.
- Files: `src/main/resources/static/trust-reply-workbench.js`; `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`; `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`; `src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt`; `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt`; `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`; and their five authorized focused test files.
- Change: Submit the current step-03 paragraph plan/text/order/pinning and operator-fact slots to the existing `/assemble` endpoint. Revalidate it server-side against canonical source, current versions/facts/frame, required-fact exact-once closure, controlled/frozen/operator verbatim constraints, and single-action rules. Compose the final letter from that validated sequence without regenerating or re-closing it. Return the validated final paragraphs so each archive-eligible item maps deterministically to its containing final paragraph; reject missing or ambiguous mappings. Store that paragraph in `finalParagraphText` while retaining item `answerText` separately.
- Regression test: Edit, merge, move, rearrange, and fully lock paragraphs; assert the final request carries the current authoritative state and the response/final letter retains exact text and sequence. Assert stale/foreign/invalid paragraph input fails closed. Assert an archived item whose answer differs from its closed paragraph stores both fields in their proper slots, including a merged paragraph mapped consistently to its contributing items.
- Existing verification: Run the focused JS/controller/workbench/index/training/live suites, then every command in Verification Commands.
- Must not change: Per-item answer/version semantics, locked-item persistence, canonical source/fact/frame validation, action channel, send/training gates, archive source modes/statuses, or index-as-phrasing-only behavior.
- Prohibited: Client-trusted bypasses, LLM calls during final assembly, final-text reconstruction from `answerText`, schema/migration changes, new routes, UI layout/style changes, cache-buster edits, or files outside Authorized Files.

### R-2: Align both archive callers with the approved eligibility set

- Resolves: V-2.
- Root cause: Caller-side legacy filters narrow the set before the canonical archive validator can evaluate it.
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt`; `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt`; `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`; `src/test/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexServiceTest.kt`; `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationServiceTest.kt`; `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`.
- Change: Ensure both existing triggers admit precisely the four approved handling values (`ANSWER_FROM_OPERATOR_INPUT`, `ANSWER_WITH_SAFE_TEMPLATE`, `OMIT_WITH_EXPLANATION`, `ESCALATE_TO_HUMAN`) crossed with `AI_GENERATED` and `SAFE_TEMPLATE`, without requiring a non-empty operator instruction. Keep one canonical eligibility definition consistent with document validation; continue excluding every unapproved shape.
- Regression test: At both training and live trigger points, prove each allowed handling/generation family—including `SAFE_TEMPLATE` and empty instruction—reaches archive; prove an unapproved shape does not. Retain explicit assertions that non-`MEETS_EXPECTATION`, unsuccessful/non-SENT, stale/replay-mismatched, and other existing isolation branches make zero archive writes.
- Existing verification: Run the focused index/training/live suites, then every command in Verification Commands.
- Must not change: Training rating gate, live successful-send/replay gate, source/qualification metadata, dedupe/document ID, archive failure isolation, or activation workflow.
- Prohibited: Broadening beyond the plan 16 allow-list, bypassing `UnsupportedAnswerIndexService` validation, changing send outcomes, adding retries/outbox/re-send, or touching files outside Authorized Files.

## Verification Commands

1. `node --test src/test/js/trustReplyWorkbenchThreeStep.test.js`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=TrustReplyWorkbenchControllerTest,TrustReplyWorkbenchServiceTest,UnsupportedAnswerIndexServiceTest,AiTrainingEvaluationServiceTest,PendingMailOperationServiceTest test`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
5. `node --test src/test/js/*.test.js`
6. `git diff --check`
7. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`

The epoch-2 Flyway human exception is review-epoch-specific and does not waive command 7 for repair execution; a new explicit human decision is required if its environment remains unavailable.

## Completion Criteria

- Final `/assemble` receives and server-validates the current step-03 paragraphs and emits their exact validated text/order; editing, merging, moving, rearranging, pinning, and all-locked use are visible in final output.
- Invalid/stale/foreign paragraph state, missing/duplicated required facts, non-verbatim protected facts, and paragraph CTA injection fail closed.
- `finalParagraphText` is the matching final closed paragraph, never a copy/fallback of item `answerText`; deterministic mapping is covered for one-to-one and merged paragraphs.
- Both training and live callers cover the exact four-handling × two-generation allow-list with optional operator instruction, while all prior qualification/send/replay gates remain effective.
- Focused and full verification commands pass, and changed files remain exactly within Authorized Files.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.

To approve and execute this repair, send:

`$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/fix/10-reply-orchestration-order/repair.md`

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/fix/10-reply-orchestration-order/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with commit subject `fix(reply-orchestration): preserve final paragraphs and archive eligibility`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/review/2026-08-28-reply-orchestration-order/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with commit subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it, using `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/fast/2026-08-28-reply-orchestration-order/human-review-handoff.md` and the committed `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/review/2026-08-28-reply-orchestration-order/repair-execution.md`. Do not ask the human to relay executor metadata.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
