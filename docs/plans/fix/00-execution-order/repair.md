# Repair Plan: 00-execution-order

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/2026-08-26/00-execution-order.md
Verification report: aggregate Epoch 2 fresh verification, 2026-08-27
Implementation boundary: f2935072c819a9167e75220a6a959b0769462fde..7ce95dba4b01d559ce580cc964564cc648c292a4

## Objective

Every required `QaFactRetriever` fail-open outcome emits its own classified warn record without changing its fail-open result.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-2 | P1 | 01 I-8: every `QaFactRetriever.retrieve(...)` failure path both fails open and writes one classified `log.warn` | DISABLED, CLIENT_ABSENT, EMPTY_RESPONSE, one PARSE_ERROR branch, and ALL_REJECTED return outcomes without a retriever-level classified warn; caller logging cannot satisfy the retriever-scoped contract. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | Resolved by committed repair `7ce95dba4b01d559ce580cc964564cc648c292a4`: validation now uses the exact truncated `promptPool`. |
| RECORD_ONLY entries | Re-evaluated in aggregate verification; none proves an additional mandatory violation. |

## Unchanged Contract

- Keep all approved 00/01/02/03 invariants, A1 wording, result fields, cache key, temperature, caller-level `[FACT_RETRIEVAL]` logs, status rules, matrix bypass, request-key inputs, and UI behavior unchanged.
- Preserve `FactRetrieval(available = false, byRequestIndex = emptyMap())` and its established outcome/count values for every fail-open path.
- Do not modify production/test files outside the authorized list.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetriever.kt | Emit one retriever-level classified warn for each I-8 failure outcome. |
| src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetrieverTest.kt | Add discriminating logger assertions for every I-8 failure outcome. |

## Repair Tasks

### R-1: Make I-8 failure classification observable at its owning seam

- Resolves: V-2
- Root cause: return branches construct `FactRetrieval(available = false, ...)` directly, so multiple required failure outcomes have no `QaFactRetriever` warn record.
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetriever.kt`; `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetrieverTest.kt`
- Change: route every I-8 failure return through one local retriever helper that emits exactly one classified `log.warn` for that outcome, then returns the unchanged `FactRetrieval`; retain existing detailed transport, parse, invalid-id, and truncation diagnostics only where they already apply.
- Regression test: independently exercise DISABLED, CLIENT_ABSENT, TRANSPORT_ERROR, EMPTY_RESPONSE, PARSE_ERROR, and ALL_REJECTED; for each, assert `available=false`, empty result, unchanged outcome, and a retriever logger warn containing that exact outcome.
- Existing verification: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactRetrieverTest`; full required gates.
- Must not change: model invocation decisions, prompt contents/truncation, candidate validation, cache behavior, caller logs, result shape, error outcomes, or accepted rule ordering.
- Prohibited: moving the requirement solely to callers, suppressing existing detailed diagnostics, altering failure semantics, or expanding scope beyond the two authorized files.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactRetrieverTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionRetrievalTest`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
5. `node --test src/test/js/*.test.js`
6. `git diff --check`

## Completion Criteria

- Each of the six I-8 failure outcomes is fail-open and has one retriever-level classified warn record.
- V-1 remains resolved: only a rule present in the exact prompt payload can be accepted.
- Changed files are limited to the authorized list.
- All verification commands pass.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/fix/00-execution-order/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with product commit subject `fix(fast-p): classify retriever fail-open outcomes`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/review/2026-08-26-execution-order/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with evidence commit subject `docs(review-fast-p): record retriever failure logging repair execution`.
5. Returning to the already authorized `$review-fast-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/fast/2026-08-26-execution-order/human-review-handoff.md` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
