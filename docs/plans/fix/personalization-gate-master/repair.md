# Repair Plan: personalization-gate-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-08-09/personalization-gate-master.md` (sha256 `cbae234bc59e9ae9fe67315bd86e4a86ee1d4ddd4ef54b94dbd14ebde13b8324`)
Verification report: aggregate/master `verify-p` report in this review
Implementation boundary: `ab5dcbb7fbb58f5e8a9b13b7e54022effd270b77..d848b8c3999fd7d67388be6d7b340ab48db43ff2`

## Objective

Gate rejections preserve correct batch accounting, and a failed change of template gate clears the prior gate filter rather than leaving stale fields active.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | P1 I-6 / master I-M5: gate rejection is a skip, does not terminate the batch, and preserves correct batch state transitions. | The new MATERIAL_REMINDER catch advances `processedTotal`, `roundSent`, and `roundProcessed`; common post-try bookkeeping advances those same counters again. |
| V-2 | P1 | P2 I-10 / master I-M3: a gate-fields request failure applies no filter and has no fallback field set. | On a failed switch from an already-selected template, the catch only shows a status; it leaves the prior template's active chips, gate state, and summary in place. |

## Findings Excluded

| Finding | Reason |
|---|---|
| O-1 | Test-only `mailVariableService == null` fallback is unreachable from Spring production injection; the mandatory production runtime path remains guarded. |
| O-2 | First-focus template-option population preserves master observable outcomes and the repository pre-auth no-network invariant; no master requirement mandates page-load timing. |

## Unchanged Contract

- Both sending paths remain independently gated at send time; ES filtering remains preview-only.
- A gate rejection records `PERSONALIZATION_INCOMPLETE` with the existing Chinese label and never falls into `recordFailure`.
- No SMTP-send, ES mapping/storage, placeholder-rendering, template-required-key, P2 UI, or unrelated batch behavior changes.
- Do not amend the master or child plans, alter production files outside this list, or change tests unrelated to V-1.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | Make the MATERIAL_REMINDER personalization-gate skip follow the loop's single bookkeeping path. |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | Add a discriminating regression test for one gate-blocked recipient's accounting and continued processing. |
| `src/main/resources/static/app.js` | Clear stale gate fields/state/summary when a requested template's gate-fields request fails. |
| `src/test/js/gateTemplateFilter.test.js` | Cover a failed template switch after a successful prior selection. |

## Repair Tasks

### R-1: Keep MATERIAL_REMINDER gate skips on one accounting path

- Resolves: V-1.
- Root cause: `ManualInitialOutreachService.kt:322-327` increments three progress counters before control falls through to the unconditional increments at `:342-344`.
- Files: exactly the two Authorized Files above.
- Change: record the skip/rejection once, then ensure exactly one path performs `processedTotal`, `roundSent`, and `roundProcessed` bookkeeping for that recipient; retain continuation of the batch and the existing update-progress flow.
- Regression test: exercise a MATERIAL_REMINDER recipient for which `sendManualMail` throws `PersonalizationGateException`, followed by another eligible recipient; assert one personalization skip, no failure for the blocked recipient, single progress/round advancement for that recipient, and continued processing of the following recipient.
- Existing verification: run the focused test and the master P1 impacted-test command.
- Must not change: INTRODUCTION's existing gate-exception path, reason-code label, non-gate exception path, send limits, or delivery behavior.
- Prohibited: adding retries, changing ES prefilter semantics, modifying template/mail services, or changing P2 files.

### R-2: Clear stale gate state after a failed template switch

- Resolves: V-2.
- Root cause: `app.js:11401-11404` reports a gate-fields error but does not undo the prior successful template's active chips or summary.
- Files: exactly `src/main/resources/static/app.js` and `src/test/js/gateTemplateFilter.test.js`.
- Change: on a gate-fields failure, leave no gate-derived `hasField` selection active, clear gate-owned state, hide the summary, and preserve the plan-required one failure notice with no hardcoded fallback fields.
- Regression test: first select a template whose gate-fields response activates chips, then select a second template whose response fails; assert no gate chip remains active, no `hasField` is sent by a later refresh, the summary is hidden, and exactly one failure notice is shown.
- Existing verification: run the single JS test and the master P2 impacted-test command.
- Must not change: a successful selection's server-provided fields, the manual-chip restoration on `不限`, the no-pre-auth-network invariant, or the `符合 N / M` wording.
- Prohibited: parsing placeholders in the client, adding a default required-field set, adding a backend endpoint, or changing ES query behavior.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ManualInitialOutreachServiceTest'`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='PersonalizationGateServiceTest,ManualExpertMailServiceGateTest,MailVariableServiceTest,IntroductionMailComposerTest,MailComposeTemplateServiceTest'`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ExpertSearchServiceTest,ComposeTemplateGateControllerTest'`
4. `node --test src/test/js/gateTemplateFilter.test.js`
5. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
6. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
7. `git diff --check`

## Completion Criteria

- A gate-blocked MATERIAL_REMINDER recipient contributes exactly one skip and exactly one unit to each relevant progress/round counter.
- The batch proceeds to the next recipient and the gate rejection is not recorded as a failure.
- A failed change from one gate template to another leaves no gate-derived field filter active and hides the gate summary.
- The regression test and all verification commands pass.
- Changed files remain inside the Authorized Files list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/personalization-gate/docs/plans/fix/personalization-gate-master/repair.md` invocation authorizes:

1. Only these Authorized Files and the verification commands in this plan:
   - `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
   - `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
   - `src/main/resources/static/app.js`
   - `src/test/js/gateTemplateFilter.test.js`
   Required verification commands:
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ManualInitialOutreachServiceTest'`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='PersonalizationGateServiceTest,ManualExpertMailServiceGateTest,MailVariableServiceTest,IntroductionMailComposerTest,MailComposeTemplateServiceTest'`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ExpertSearchServiceTest,ComposeTemplateGateControllerTest'`
   - `node --test src/test/js/gateTemplateFilter.test.js`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
   - `git diff --check`
2. After all repair tasks and required verification commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only those four Authorized Files, with subject `fix: correct personalization gate batch accounting and stale filter cleanup`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/personalization-gate/docs/plans/review/personalization-gate/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with subject `docs: record personalization gate repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the human requests it in that `$execute-p` invocation.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
