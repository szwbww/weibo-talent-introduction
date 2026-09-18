# Repair Plan: 00-university-email-template-main

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-university-email-template-main/docs/plans/2026-09-18/00-university-email-template-main.md
Verification report: aggregate review at evidence head `066c741365a6ae8546ed3011cfa67d4f6cb7dfb8`
Implementation boundary: `7f7b3a821f09d4255dc735c1e7b96eacf9c32164..decdb28dfe6431c9f238b76fa64fc9a1aad7939e`

## Objective

For the actually selected template subject/body, only bare placeholders gate sending; a placeholder with a non-empty default must render and send when its value is missing.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | Master M-1; child 1 I-2 | `PersonalizationGateService.evaluate` tests generic placeholder occurrence instead of whether the selected raw text contains a bare, required placeholder. |

## Findings Excluded

| Finding | Reason |
|---|---|
| O-2/O-3 and child-2 RECORD_ONLY notes | No confirmed mandatory violation. |

## Unchanged Contract

- Missing values for bare `${key}` in the actual selected subject/body still block sends; null, empty, and whitespace-only values are missing.
- `${key|non-empty default}` never creates a send gate and final placeholder-residue rejection remains.
- ES prefilter remains conservative; research-direction filtering, template CRUD, data-import, task creation, and SMTP behavior remain unchanged.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateService.kt` | Restrict the send gate to bare required tokens in the selected raw texts. |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateServiceTest.kt` | Replace the contradicting legacy expectation with the discriminating defaulted-selected-variant regression test while retaining the bare-token block test. |

## Repair Tasks

### R-1: Make selected-text gating token-semantic

- Resolves: V-1.
- Root cause: `evaluate` checks `placeholderKeysIn`, which includes defaulted tokens, against a template-wide required-key union.
- Files: exactly the two authorized files above.
- Change: derive the keys present in `rawTexts` with the canonical bare-token rule (`requiredKeysIn`), then intersect that result with the supplied required-key set before evaluating missing values.
- Regression test: with `requiredKeys` containing `researchFields` but selected raw text `${researchFields|Science}` and an empty value, the result is not blocked; a selected `${researchFields}` case remains blocked.
- Existing verification: `node --test src/test/js/composeTemplatePreview.test.js src/test/js/gateTemplateFilter.test.js src/test/js/batchSendTaskConsoleInteraction.test.js`; `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='MailComposeTemplateServiceTest,PersonalizationGateServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskConfigServiceTest,BatchSendTaskRuntimeIntegrationTest' -DfailIfNoTests=false`.
- Must not change: template-wide `effectiveRequiredKeys` for gate-fields/ES conservatism, CRUD, direction filtering, whitespace missing semantics, or residue protection.
- Prohibited: changes to composers, migration/schema, frontend, ES mapping/query construction, task data, expert data, or SMTP calls.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='PersonalizationGateServiceTest,MailComposeTemplateServiceTest' -DfailIfNoTests=false`
2. `node --test src/test/js/composeTemplatePreview.test.js src/test/js/gateTemplateFilter.test.js src/test/js/batchSendTaskConsoleInteraction.test.js`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='MailComposeTemplateServiceTest,PersonalizationGateServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskConfigServiceTest,BatchSendTaskRuntimeIntegrationTest' -DfailIfNoTests=false`
4. `git diff --check`

## Completion Criteria

- V-1 regression demonstrates that a selected defaulted token does not block an otherwise valid send.
- A selected bare token still blocks on null, empty, or whitespace-only values.
- All verification commands pass and changed files remain inside the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-university-email-template-main/docs/plans/fix/00-university-email-template-main/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with product commit subject `fix(fast-p): align selected template placeholder gate`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-university-email-template-main/docs/plans/review/university-email-template-main/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with evidence commit subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
