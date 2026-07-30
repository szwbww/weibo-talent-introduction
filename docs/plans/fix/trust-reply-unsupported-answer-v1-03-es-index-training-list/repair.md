# Repair Plan: trust-reply-unsupported-answer-v1-03-es-index-training-list

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-07-29/trust-reply-unsupported-answer-v1-03-es-index-training-list.md
Verification report: review-p phase3, 2026-07-30
Implementation boundary: HEAD `92ffef0` plus the phase3 ten-file working-tree change set; unrelated `docs/plans/fix/**` renames excluded.

## Objective

Rapid source filtering never allows an earlier unsupported-answer list response to replace the newest requested page.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-6: request token prevents an old response from overwriting a fast filter change | `loadAiTrainingUnsupportedAnswers(true)` returns while a request is loading, before it advances `unsupportedAnswersRequestToken`; the old request remains current and writes its result. |

## Findings Excluded

| Finding | Reason |
|---|---|
| O-1 | `mvn test` failed only in unchanged `UnmatchedInboundAiReplyTurnKnowledgeTest`; it is outside phase3 files and has no evidenced causal link to V-1. |

## Unchanged Contract

- The unsupported-answer list remains lazy and isolated from `loadAiTraining()`.
- Only the fixed GET list API, bounded parameters, existing translation flow, six-column read-only DOM, and existing CSS are used.
- No ES mapping, controller, route, index configuration, or expert-index behavior changes.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/resources/static/app.js` | Supersede an in-flight list request when a forced filter, refresh, or page request is issued. |
| `src/test/js/aiTrainingUnsupportedAnswers.test.js` | Add a deferred-response regression test proving stale results cannot overwrite the newest request. |

## Repair Tasks

### R-1: Supersede stale list requests

- Resolves: V-1.
- Root cause: `src/main/resources/static/app.js:3109-3111` returns before invalidating the active request token; `3120-3123` then accepts the old response.
- Files: exact authorized files above.
- Change: A forced load must issue the latest bounded request even if a prior request is pending, and only the latest request may update items, total, loaded/error, or rendered state.
- Regression test: Start an unresolved request, change `sourceMode`, force a second request, resolve the second response, then resolve the first; prove the state and rendered data stay from the second request.
- Existing verification: preserve lazy first-activation, failure-isolation, paging, filtering, escaping, translation, and read-only assertions.
- Must not change: no global loading/error status; no request from `loadAiTraining()`; no new CSS or mutation API.
- Prohibited: changing ES/API contracts, adding retries, debouncing, or editing unrelated test failures.

## Verification Commands

1. `node --check src/main/resources/static/app.js`
2. `node --test src/test/js/aiTrainingUnsupportedAnswers.test.js`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=UnsupportedAnswerIndexApiTest test`
4. `node --test src/test/js/*.test.js`
5. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
6. `git diff --check`

## Completion Criteria

- Forced rapid filtering leaves only the newest page/filter result in UI state.
- A stale success or failure cannot change current items, totals, loaded/error state, or panel rendering.
- Changed product files are limited to the authorized list.
- Full verification is rerun before PASS; O-1 remains excluded unless fresh evidence connects it to this repair.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
