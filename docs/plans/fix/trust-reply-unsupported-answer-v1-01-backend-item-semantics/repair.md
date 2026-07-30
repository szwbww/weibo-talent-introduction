# Repair Plan: trust-reply-unsupported-answer-v1-01-backend-item-semantics

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-07-29/trust-reply-unsupported-answer-v1-01-backend-item-semantics.md`
Verification report: 2026-07-29 phase1 review — FAIL / INITIAL
Implementation boundary: `3472e789` plus the current working-tree diff. Product changes are limited to the eight baseline-listed Kotlin/test files; pre-existing documentation renames are excluded.

## Objective

Make all operator-directed and OMIT locked tuples fail closed under the approved phase1 contract.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---:|---|---|
| V-1 | P1 | I-1: invalid non-`UNSUPPORTED` use of `ANSWER_FROM_OPERATOR_INPUT` returns `TRUST_REPLY_HANDLING_INVALID`. | `adjustItem` validates a required operator instruction before validating whether the handling is allowed for the item's grounding status. |
| V-2 | P1 | I-2/I-5/I-8: assembled operator-directed answers remain subject to action policy. | The `ANSWER_FROM_OPERATOR_INPUT` locked-item branch checks tuple shape only; it does not apply the existing action-policy validation used for grounded locked items. |
| V-3 | P1 | I-7: OMIT has fixed empty instruction or ignores it. | The OMIT adjustment forwards the supplied instruction and `materializeVersion` preserves it and hashes it into the canonical version. |

## Findings Excluded

| Finding | Reason |
|---|---|
| N/A | No P2 or observations require repair. |

## Unchanged Contract

- Grounded/PARTIAL QA, claims, trust/action, source/evidence stale semantics stay unchanged.
- `ACKNOWLEDGE_PENDING` and its safe-template fallback stay unchanged.
- The composer keeps order, duplicates, and zero output for OMIT.
- No DB/ES write, SMTP call, action-log write, frontend change, or archive behavior is introduced.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | Restore validation ordering, canonical OMIT normalization, and assembled operator-directed action-policy enforcement. |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | Add discriminating regression coverage for V-1, V-2, and V-3. |

## Repair Tasks

### R-1: Restore fail-closed item and canonical-version validation

- Resolves: V-1, V-2, V-3.
- Root cause: `TrustReplyWorkbenchService` validates these three states inconsistently between adjustment, locked assembly, and version materialization.
- Files: the two Authorized Files above.
- Change:
  - Validate the handling/status matrix before the operator-instruction gate, so an unsupported handling always returns `TRUST_REPLY_HANDLING_INVALID`.
  - Reject an assembled operator-directed answer when it violates the existing action policy derived from the current inbound request.
  - Canonicalize every OMIT version to an empty instruction and the existing empty-instruction hash, regardless of any client-supplied instruction.
- Regression test:
  - An operator-directed handling on a non-`UNSUPPORTED` item with a blank instruction returns `TRUST_REPLY_HANDLING_INVALID`.
  - A shape-valid, version-ID-valid operator-directed locked tuple containing an action-policy violation is rejected with the stable 422 business error.
  - OMIT adjusted or assembled with a nonempty instruction returns the same empty-instruction canonical tuple as OMIT without one, and still emits no LLM call or body text.
- Existing verification:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=TrustReplyWorkbenchItemFlowTest test`
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=AiReplyDraftServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchControllerTest,AiTrainingSimulateTest test`
- Must not change: I-1 allowed/recommended matrix, I-2 prompt authority, I-4 tuple fields, I-6 initial-version behavior, and I-9 side-effect boundary.
- Prohibited: changing controllers, frontend code, LLM prompt/model/timeout behavior, QA/claim policy, persistence, or sending behavior.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=TrustReplyWorkbenchItemFlowTest test`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=AiReplyDraftServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchControllerTest,AiTrainingSimulateTest test`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
4. `git diff --check`

## Completion Criteria

- V-1 through V-3 have focused passing regression tests.
- Operator-directed tuples cannot bypass the action policy during assemble.
- OMIT tuples have empty instruction, empty-instruction hash, no LLM call, and no composed output.
- Changed files remain within the Authorized Files.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
