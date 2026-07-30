# Repair Plan: trust-reply-unsupported-answer-v1-05-live-send-qualified-archive

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-07-29/trust-reply-unsupported-answer-v1-05-live-send-qualified-archive.md`
Verification report: `review-p Phase 5 verification, 2026-07-30`
Implementation boundary: `640968a` + current working-tree diff

## Objective

Preserve the canonical requested-fact matrix in the live archive assembly snapshot without substituting derived claim fact IDs.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-1: snapshot must carry canonical `requestedFactIds`; fallback to `canonicalFactIds` is prohibited. | `buildTrustReplyAssemblySnapshot` substitutes derived claim IDs when the canonical request matrix is absent. |
| V-2 | P2 (mandatory) | I-1/T1: regression evidence must distinguish request-matrix IDs from claim-derived IDs. | The JS contract checks only that a snapshot exists, not which field supplies `requestedFactIds`. |
| V-3 | P2 (mandatory) | I-2/I-3/T7: replay rejection and every non-SENT/finalize-failure branch require isolation evidence. | The Phase 5 service suite covers raw mismatch, ES failure, one delivery failure, and DEDUP, but not source mismatch, stale replay, rendered mismatch, no eligible version, or the remaining required zero-archive branches. |

## Findings Excluded

| Finding | Reason |
|---|---|
| N/A | N/A |

## Unchanged Contract

- Archive metadata is sent only when record, raw template, text, and HTML baselines match.
- Server-side replay remains authoritative; no mail-send, ES, DTO, DOM, CSS, or route behavior changes.
- Do not modify `canonicalFactIds` use for QA selection.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/resources/static/app.js` | Remove the prohibited request-matrix fallback in the archive snapshot builder. |
| `src/test/js/aiReplyReviewConfirmation.test.js` | Add a discriminating regression contract for canonical requested-fact preservation. |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | Complete the required Phase 5 replay-rejection and non-SENT archive-isolation coverage. |

## Repair Tasks

### R-1: Preserve canonical requested fact IDs

- Resolves: V-1, V-2.
- Root cause: The archive snapshot currently falls back from `assembly.requestedFactIds` to `assembly.canonicalFactIds`, which can alter the request matrix for OMIT or unclaimed facts.
- Files: `src/main/resources/static/app.js`; `src/test/js/aiReplyReviewConfirmation.test.js`.
- Change: Snapshot `requestedFactIds` only from the assembly's canonical requested-fact field; absence must remain an empty request matrix, never use claim-derived fact IDs.
- Regression test: Assert the snapshot builder retains `requestedFactIds` and rejects any `canonicalFactIds` fallback within that builder.
- Existing verification: Run the focused JS test and Phase 5 required regression commands.
- Must not change: The baseline send gate, QA `canonicalFactIds` state, server replay, archive status UI, or cache-buster.
- Prohibited: Backend changes, CSS/DOM changes, migration/outbox/retry/re-send additions, and production-code changes outside the two authorized files.

### R-2: Complete mandatory archive-isolation evidence

- Resolves: V-3.
- Root cause: The service test suite does not exercise several required rejection and non-SENT branches with a candidate assembly.
- Files: `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`.
- Change: Add focused tests for source mismatch, stale replay, rendered mismatch, no eligible canonical version, and required non-SENT/finalize-failure branches. Each test must assert the mandated SENT/FAILED or NOT_APPLICABLE result and zero gateway calls where required.
- Regression test: Verify all required rejection/non-SENT cases retain mail-state behavior and cannot reach the index gateway.
- Existing verification: Run the focused Maven test, then the Phase 5 regression commands.
- Must not change: Production send flow, delivery classification, Message-ID handling, finalize behavior, or ES document construction.
- Prohibited: Production-code changes, expanded behavior, migrations, outbox/retry/re-send additions, and test changes outside the named suite.

## Verification Commands

1. `node --test src/test/js/aiReplyReviewConfirmation.test.js`
2. `node --check src/main/resources/static/app.js`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=PendingMailOperationServiceTrustWorkbenchTest,UnmatchedInboundTrustWorkbenchTest,UnsupportedAnswerIndexApiTest test`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
5. `node --test src/test/js/*.test.js`
6. `git diff --check`

## Completion Criteria

- Archive snapshots cannot substitute `canonicalFactIds` for `requestedFactIds`.
- The new regression fails if that fallback returns.
- Rejection and non-SENT/finalize-failure tests prove the archive gateway is unreachable where Phase 5 requires it.
- Changed files remain within the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
