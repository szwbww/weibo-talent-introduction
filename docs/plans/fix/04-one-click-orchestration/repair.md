# Repair Plan: 04-one-click-orchestration

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-08-18/04-one-click-orchestration.md
Verification report: review-p / verify-p, 2026-08-19 (V-1)
Implementation boundary: `52380ab..working tree`, restricted to the nine baseline-plan files

## Objective

Ensure a server-composed operator instruction never includes a time commitment supplied through an adjacent QA-rule display name.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-0: a suggested instruction must contain no time promise. | `containsUnsafeNameContent()` recognizes only a finite subset of Chinese time phrases. A name such as `三天后答复` contains no ASCII/Unicode decimal digit, URL token, or listed phrase, so it is inserted verbatim into the operator instruction. |

## Findings Excluded

| Finding | Reason |
|---|---|
| Backend Maven results | BLOCKED by unrelated compile failures in campaign/expert files; no workbench test executed. |
| Prior provenance/body-overlap concerns | Resolved in the reviewed boundary: locked-item payloads contain no `autoFilled`, and adjacent display names are screened against answer-body fragments. |

## Unchanged Contract

- Suggested instructions remain server-composed and only UNSUPPORTED items receive them.
- Instructions remain at most 500 characters; adjacent rule bodies are never copied into them.
- Existing handling allowlists, `ANSWER_FROM_OPERATOR_INPUT` validation, state persistence, assembly, reset, and mail behavior do not change.
- No QA data, controller contract, schema, or frontend template changes.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt | Reject adjacent display names that express a time commitment before composing `suggestedInstruction`. |
| src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt | Prove time-commitment names cannot enter an UNSUPPORTED instruction while a safe neighboring name remains eligible. |

## Repair Tasks

### R-1: Block time-commitment display names

- Resolves: V-1.
- Root cause: the current lexical filter omits common forms such as `三天后答复`; `Char.isDigit()` does not classify the Chinese numeral `三` as a decimal digit, and the phrase does not include any current time-promise token.
- Files: `TrustReplyWorkbenchService.kt`, `TrustReplyWorkbenchServiceTest.kt`.
- Change: extend the server-side unsafe-name screening so an adjacent display name expressing a concrete future response/answer commitment is omitted from the suggested instruction. Preserve valid safe adjacent names and the non-empty fixed instruction structure.
- Regression test: bootstrap an UNSUPPORTED item with adjacent names `三天后答复` and a safe name; assert the instruction contains neither the commitment nor its time phrase, but retains the safe name.
- Existing verification: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchServiceTest`; then the baseline full Maven commands after the unrelated compiler failures are cleared.
- Must not change: the allowed/recommended handling mapping, 500-character cap, URL/body-fragment filtering, or non-overlapping safe-name inclusion.
- Prohibited: copying/rephrasing answer bodies, frontend-side templates, relaxing locked-item validation, changing QA rules, or adding automatic-send behavior.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchServiceTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
4. `git diff --check`

## Completion Criteria

- A time-commitment display name cannot appear in `suggestedInstruction`; a safe neighboring name can still appear.
- The fixed instruction remains non-empty and no longer than 500 characters.
- Changes remain inside the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
