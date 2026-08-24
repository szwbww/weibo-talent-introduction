# Fast-P Child Brief — trust-reply-manual-authority-04

- Plan (exact approved contract): `docs/plans/2026-08-24/04-trust-reply-diagnostics-persistence.md` (approved identity `commit:8dc7c96`)
- Master plan: `docs/plans/2026-08-24/00-trust-reply-manual-authority-master.md`
- Depends on: trust-reply-manual-authority-01, trust-reply-manual-authority-02, trust-reply-manual-authority-03. Base = child 03's terminal code head (see dispatch). R3.

## Scope and master constraints

- Master I-2: diagnostics per-request manual ids must equal the matrix (identity chain).
- Master I-3: diagnostics are read-only description; never enter status/factRuleIds/handling/version/evidence hash/safety/SMTP/archive eligibility.
- Master I-5: diagnostics attach ONLY to final events — LIVE send success and TRAINING evaluation action rows; bootstrap/add/remove/move/generate/lock/preview write no diagnostics action.
- Master I-6: auto/legacy/null-assembly paths unchanged; no fake diagnostics.
- Master I-7: ONLY the 7 authorized files below; no new table/action type/API/UI/query.
- Master I-8: run directed tests + `git diff --check`; record exits.

## Authorized files (from plan 04)

| File | Change |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | diagnostics DTO, bounded builder, assemble response field |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt` | evaluation snapshot v2 embeds diagnostics |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | pass verified diagnostics on successful send (null when no assembly) |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` | existing action after-map gains nullable diagnostics |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationServiceTest.kt` | training write, bounds, privacy regression |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | LIVE final/non-final split |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt` | action after payload + best-effort semantics |

No DB migration, repository/controller, frontend/UI changes.

## Child-plan invariants (04 I-1..I-7)

- I-1: LIVE — diagnostics added to after_value of the send's existing action (`SEND_MANUAL_COMPOSED_REPLY` with facts, `SEND_MANUAL_RICH_REPLY` without) after SMTP success + `finalizeSuccess` committed; TRAINING — added to existing `AI_TRAINING_REPLY_EVALUATED` snapshot when rating submitted and assembly verified; no new action rows.
- I-2: computed only from verified assembly `selection + response.itemVersions + canonical matrix` (plan 03); never client-reported flags/ids/counts.
- I-3: diagnostics never participate in business authorization.
- I-4: bounded: fixed schema version; ≤50 request snapshots; ≤20 intent keys; ≤50 fact ids; strings ≤200 chars; ids/counts/short enums/flags/truncation marks only; NEVER inbound body, request quote, answerText, fact answerBody, operator instruction, phone/address raw text.
- I-5: stable flags `MANUAL_FACT_SELECTED`, `INTENT_MISMATCH`, `UNRECOGNIZED_ASK`, `MANUAL_FACT_ON_UNSUPPORTED`, `DUPLICATE_MANUAL_FACT_ASSIGNMENT` (duplicate computed from per-request matrix factRuleIds counts, NOT from the deduped canonical union).
- I-6: LIVE audit stays after-commit best-effort (failure → warn only, no send reversal); TRAINING evaluation remains action-log-row-as-record (record failure = evaluation not saved).
- I-7: no assembly → no fake diagnostics; existing after payload fields verbatim.

## Required commands

- `mvn -q -Dtest=AiTrainingEvaluationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,ManualReplySendAttemptServiceTest test` (JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home)
- `git diff --check`

## Downstream interfaces

- `trust-reply-diagnostics-v1` schema version string in after_value; same semantics across TRAINING and LIVE (04 acceptance).
- No new flag-indexed queries; existing `/api/operator-action-logs` retrieval must still work.
- Do not add diagnostics into `SendPayload` or attempt idempotency key; do not change before/note/subject/bodyPreview fields.

## Procedure

Same as brief 01: execute-p against the exact plan; 4 stages; run all required commands; commit locally ONLY the 7 authorized files as `feat(fast-p): implement trust-reply-manual-authority-04`; append result to child execution.md; return `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + command summary + report path. Skip formatters/linters/full suite; do not touch other children or rewrite history.
