# Repair Plan: 00-execution-order

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/2026-08-26/00-execution-order.md
Verification report: fresh aggregate verification, 2026-08-27
Implementation boundary: f2935072c819a9167e75220a6a959b0769462fde..cb30230970d12e649e9faac2835335345daac793

## Objective

Only facts actually shown to the model may enter retrieval results.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | 01 I-4: each returned id must be in the request's `promptPool` | Validation builds `poolById` from untruncated `pool`, while the prompt uses `pool.take(maxRulesInPrompt)`. |

## Findings Excluded

| Finding | Reason |
|---|---|
| Record-only: failure logging | The actual AUTO/WORKBENCH callers emit the classified `[FACT_RETRIEVAL]` line at `warn` when `available=false`; no confirmed I-8 violation. |

## Unchanged Contract

- Keep all approved 00/01/02/03 invariants, A1 wording, output shape, cache key, temperature, caller logs, status rules, matrix bypass, request-key inputs, and UI behavior unchanged.
- Preserve `FactRetrieval(available = false, byRequestIndex = emptyMap())` behavior for every fail-open path.
- Do not modify production/test files outside the authorized list.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetriever.kt | Validate candidates against the exact truncated prompt pool. |
| src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetrieverTest.kt | Add a discriminating prompt-pool regression test. |

## Repair Tasks

### R-1: Enforce prompt-pool authority

- Resolves: V-1
- Root cause: `promptPool` is truncated at `QaFactRetriever.kt:71`, but `poolById` is constructed from `pool` at `:119`.
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetriever.kt`; `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetrieverTest.kt`
- Change: Build the validation id map from the same exact `promptPool` supplied to `buildUserContent`; retain all remaining enabled/policy/body checks and result ordering.
- Regression test: Set `maxRulesInPrompt` below a valid rule's position; return that excluded id; assert it is rejected as not in pool and cannot enter `byRequestIndex`.
- Existing verification: `mvn test -Dtest=QaFactRetrieverTest`; full required gates.
- Must not change: prompt truncation, cache behavior, model call, accepted in-prompt ids, or error/result shape.
- Prohibited: expanding the prompt, changing `maxRulesInPrompt`, or accepting ids not present in the prompt.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactRetrieverTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionRetrievalTest`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
5. `node --test src/test/js/*.test.js`
6. `git diff --check`

## Completion Criteria

- An id absent from the exact prompt payload is rejected and never reaches any request's `factRuleIds`.
- Changed files are limited to the authorized list.
- All verification commands pass.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/fix/00-execution-order/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with product commit subject `fix(fast-p): enforce prompt-pool authority`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/review/2026-08-26-execution-order/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with evidence commit subject `docs(review-fast-p): record prompt-pool authority repair execution`.
5. Returning to the already authorized `$review-fast-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/fast/2026-08-26-execution-order/human-review-handoff.md` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
