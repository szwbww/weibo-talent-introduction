# Repair Plan: batch-filter-main

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-10-14/batch-filter-main.md
Verification report: Aggregate review, 2026-10-14 (controller destination: docs/plans/review/2026-10-14-batch-filter-main/machine-verification.md)
Implementation boundary: 9d353934b82ed3e36a193c70748e5efccb3d9e82..50dc450b9e3d68b4cc44ad3a660d8a00936bb662

## Objective

Make the diagnostics baseline complete for every selected level/tag target, and make the execution target path consume the same deterministic preflight decision while retaining send-time protections.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | MAIN M-2 and 02 I-1/implementation step 4 require a diagnostics baseline of only selected level/tags, with each filter outcome explainable. | `ManualInitialOutreachService.buildBaseEsFilters` adds `exists email`, silently excluding same-level/tag profiles before preflight; no decision/reason or correct diagnostic status can be produced. |
| V-2 | P1 | MAIN M-2/G-2 and 02 I-2/implementation step 4 require preview, new-ES targets, and NEW retry targets to use the same snapshot and final preflight. | Full `BatchRecipientPreflightService.preflight` is called from preview/diagnostics only; execution only delegates the template subcheck, so its deterministic target selection is not governed by the complete preflight result. |

## Findings Excluded

| Finding | Reason |
|---|---|
| 01-region stale `matchesDiscoveryOutreach` KDoc/dead predicate | RECORD_ONLY; no reachable behavior in this boundary. |
| 01-region discovered-tag versus pending/identity coarse-filter risk | RECORD_ONLY; no proven affected write path in this review. |
| 03-ui statusless error copy, retained gate phrase, legacy-note class, unused aggregate verification phase | RECORD_ONLY; no confirmed mandatory behavioral defect. |

## Unchanged Contract

- No product writes during preview/diagnostics; no supplier or SMTP call from them.
- Preserve manual admission, suppression, sent/permanent-failure, binding, dedupe, snapshot, template, and email-verification protections.
- Keep pre-send dynamic protection rechecks; a state change after preview may still cause a stated runtime skip.
- No schema/migration, ES-mapping, policy, person-merge, or UI/CSS change.
- Keep response compatibility: never fabricate legacy values or zeroes.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt | Use a level/tag-only base scan and route deterministic execution selection through the final preflight decision. |
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightService.kt | Express the deterministic no-email exclusion needed when the complete baseline is evaluated. |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | Prove base/preview/execution/diagnostic parity and retained dynamic protection. |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightServiceTest.kt | Prove the base-set member receives an explicit deterministic exclusion and partitioned counts. |

## Repair Tasks

### R-1: Complete and enforce the common preflight

- Resolves: V-1, V-2.
- Root cause: the level/tag base scan silently removes email-less targets and the full preflight result is advisory rather than the execution selector.
- Files: all and only the Authorized Files above.
- Change: include every selected level/tag profile in the diagnostic baseline; classify a profile with no usable recipient address as a deterministic, explicit exclusion. Make both ES-new and NEW-retry execution selection honor the same snapshot's deterministic preflight eligibility, without removing their existing send-time rechecks.
- Regression test: with one selected level/tag profile lacking email and one deterministically protected profile, assert the preview and doc-id diagnostic retain and explain each; neither reaches the executor/SMTP; counts retain `baseline = excludedUnique + eligibleBeforeLiveVerification`. Also assert a newly added suppression after preflight is still blocked at send time.
- Existing verification: preserve the G-2 10/4/6/3 and overlapping-six-hit fixture; preserve 01 Other-region/new-ES/NEW-retry parity and all four email-switch combinations.
- Must not change: all protections, old response meanings, true docId/ORCID dedupe, provider/SMTP zero calls in preview, and partial semantics.
- Prohibited: migrations, ES mapping/writer changes, admission-policy changes, live sends, state backfills, UI changes, or additional person-merge logic.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B test -Dtest='BatchRecipientPreflightServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,BatchEmailVerificationServiceTest,CountryContinentMappingTest,ExpertSearchServiceTest' -DfailIfNoTests=false`
2. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B test -Dtest='BatchRecipientReadRepositoryIT' -DmysqlIt=true -DargLine='-Dapi.version=1.44' -DfailIfNoTests=false`
3. `node --test src/test/js/recipientPreviewDiagnostics.test.js`
4. `node --test src/test/js/batchEmailVerification.test.js`
5. `node --test src/test/js/gateTemplateFilter.test.js`
6. `node --test src/test/js/*.test.js`
7. `node --check src/main/resources/static/app.js`

## Completion Criteria

- A selected-level/tag profile is never silently omitted from diagnostics because its email is absent; it has an explicit exclusion explanation and correct partitioned count.
- Preview, ES-new execution selection, and NEW-retry execution selection use the same snapshot's deterministic preflight eligibility; send-time protection rechecks remain effective.
- V-1 and V-2 regression tests and every listed command pass.
- Changed files remain inside the Authorized Files list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/batch-filter-main/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with the resolved product commit subject `fix(batch-filter): align recipient preflight` recorded in this plan.
3. Appending `docs/plans/review/2026-10-14-batch-filter-main/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with the resolved evidence commit subject `docs(review-fast-p): record repair execution` recorded in this plan.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
