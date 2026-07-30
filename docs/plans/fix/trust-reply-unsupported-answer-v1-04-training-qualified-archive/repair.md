# Repair Plan: trust-reply-unsupported-answer-v1-04-training-qualified-archive

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-07-29/trust-reply-unsupported-answer-v1-04-training-qualified-archive.md
Verification report: review-p phase4, 2026-07-30, V-1
Implementation boundary: HEAD 3d565777083e84c13500ad32768573376e213358 → working-tree diff of the eight phase4 files

## Objective

When a training evaluation is saved but unsupported-answer archiving is PARTIAL or FAILED, show a non-blocking warning that directs the operator to the unsupported-answer index Tab.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-6: PARTIAL/FAILED must be non-blocking and prompt checking the index Tab. | `saveAiTrainingEvaluation` emits only the generic `训练评估已保存` toast for both archive warnings. |

## Findings Excluded

| Finding | Reason |
|---|---|
| N/A | No other confirmed phase4 violation. |

## Unchanged Contract

- A 2xx evaluation response permanently sets `context.saved=true` and keeps the button disabled as `已保存`.
- Evaluation success remains independent of archive status; no second POST, retry, forced Tab switch, or auto-refresh.
- Keep the specified status text, existing DOM/classes, existing `showStatus(..., "warn")` semantics, and no CSS changes.
- Do not change archive eligibility, action-log ordering/snapshot, ES documents, APIs, or cache-buster behavior.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/resources/static/app.js | Supply a warning toast that directs the operator to the index Tab for PARTIAL/FAILED only. |
| src/test/js/aiTrainingUnsupportedAnswers.test.js | Prove the distinct warning message while preserving the single-save/disabled-button behavior. |

## Repair Tasks

### R-1: Direct archive-warning operators to the index Tab

- Resolves: V-1.
- Root cause: `src/main/resources/static/app.js:3394` uses the same generic toast text for success and archive-warning states.
- Files: `src/main/resources/static/app.js`; `src/test/js/aiTrainingUnsupportedAnswers.test.js`.
- Change: For PARTIAL and FAILED, retain the existing warning level and show a message that states the evaluation was saved and directs the operator to the unsupported-answer index Tab. Keep SAVED and NOT_APPLICABLE unchanged.
- Regression test: Extend the existing four-status save test to assert the PARTIAL/FAILED warning message, one API call, `saved=true`, and a disabled `已保存` button.
- Existing verification: `node --check src/main/resources/static/app.js`; `node --test src/test/js/aiTrainingUnsupportedAnswers.test.js`.
- Must not change: status text contract; DOM/classes/CSS; archive counts; API payload; saved-state guard; no navigation or refetch.
- Prohibited: ES/backend changes, retry behavior, new controls, auto tab selection, or unrelated test changes.

## Verification Commands

1. `node --check src/main/resources/static/app.js`
2. `node --test src/test/js/aiTrainingUnsupportedAnswers.test.js`
3. `node --test src/test/js/*.test.js`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
5. `git diff --check`

## Completion Criteria

- PARTIAL and FAILED show a warning that explicitly directs the operator to the unsupported-answer index Tab.
- All four archive outcomes retain one-save, permanent-disabled-button behavior.
- Changed files remain inside the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
