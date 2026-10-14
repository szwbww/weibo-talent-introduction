# Repair Plan: batch-filter-main

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-10-14/batch-filter-main.md
Verification report: Aggregate re-review, epoch 2 (controller destination: docs/plans/review/2026-10-14-batch-filter-main/machine-verification.md)
Implementation boundary: 9d353934b82ed3e36a193c70748e5efccb3d9e82..e6499a22c4696a8d98ac1ffe2be0e191db5dca97

## Objective

Ensure a partial, capped diagnostics preflight is never treated as an execution-wide allow-list: every execution candidate receives the same snapshot's deterministic preflight decision without silently omitting post-cap candidates.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-2 | P1, PERSISTENT | MAIN M-2/G-2 and 02 I-2 require preview, new-ES, and NEW-retry targets to use the same snapshot's final deterministic preflight. | `scanBaseCandidates` marks a scan partial at 20,000 (`ManualInitialOutreachService.kt:654-685`). `runIntroductionFromSnapshot` turns that partial decision list into `eligibleDocIds` (`:923-936`), and `preflightEligibleOnly` rejects every candidate absent from it (`:694-702`, `:941-942`, `:978-986`). An eligible ES candidate after the cap is silently omitted. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | RESOLVED: base scan no longer requires `email`; `NO_RECIPIENT_ADDRESS` gives an explicit deterministic exclusion and the fresh regression test passes. |
| 01-region dead predicate/KDoc and discovered-tag coarse-filter note | RECORD_ONLY; no reachable mandatory violation in this boundary. |
| 03-ui statusless error copy, retained gate wording, legacy-note class, unused aggregate verification phase | RECORD_ONLY; no confirmed mandatory defect. |

## Unchanged Contract

- Preview and per-docId diagnostics remain read-only; provider and SMTP calls remain zero.
- Keep `partial=true` explicit for bounded diagnostics; never represent incomplete statistics as complete totals.
- Preserve the frozen execution snapshot, real docId/ORCID de-duplication, admissions, suppression, sent/permanent-failure, binding, template, and email-verification protections.
- Preserve send-time dynamic protection rechecks; a fact added after preflight may still cause an explicit runtime skip.
- No schema, migration, ES mapping/writer, policy, UI/CSS, live-send, backfill, or person-merge change.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt | Keep bounded diagnostic partial semantics separate from execution eligibility, so a complete per-target deterministic decision governs execution rather than a truncated membership set. |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | Prove a post-cap eligible target is not silently dropped while a post-cap deterministically excluded target remains excluded. |

## Repair Tasks

### R-1: Do not use a truncated preflight as a global execution filter

- Resolves: V-2.
- Root cause: execution treats absence from the `partial=true` diagnostics decision list as deterministic ineligibility.
- Files: all and only the Authorized Files above.
- Change: retain the bounded, explicitly partial preview/diagnostic scan, but make execution obtain a complete deterministic decision for every NEW-retry and ES candidate under its frozen snapshot. A candidate absent only because the diagnostic scan was capped must not be silently filtered out.
- Regression test: force the diagnostics/base scan past its cap with a post-cap eligible target and a post-cap deterministically protected target. Assert the eligible target reaches the execution selection, the protected target does not, and no candidate is rejected solely for being absent from a partial decision list.
- Existing verification: preserve the MAIN 10/4/6/3 fixture, six overlapping reason hits, address-less explicit exclusion, Other-region/new-ES/NEW-retry parity, all four email-switch combinations, and dynamic suppression-after-preview protection.
- Must not change: all existing deterministic exclusions, send-time rechecks, partial preview semantics, response compatibility, or legacy progress accounting unless a required invariant proves it must change.
- Prohibited: a fallback that silently restores pre-repair execution semantics for partial scans; migrations; ES mapping/writer changes; admission-policy changes; UI changes; state backfills; extra person-merge logic; live sends.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B test -Dtest='BatchRecipientPreflightServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,BatchEmailVerificationServiceTest,CountryContinentMappingTest,ExpertSearchServiceTest' -DfailIfNoTests=false`
2. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B test -Dtest='BatchRecipientReadRepositoryIT' -DmysqlIt=true -DargLine='-Dapi.version=1.44' -DfailIfNoTests=false`
3. `node --test src/test/js/recipientPreviewDiagnostics.test.js`
4. `node --test src/test/js/batchEmailVerification.test.js`
5. `node --test src/test/js/gateTemplateFilter.test.js`
6. `node --test src/test/js/*.test.js`
7. `node --check src/main/resources/static/app.js`

## Completion Criteria

- A post-cap execution candidate is evaluated by the frozen snapshot's deterministic preflight; it is never omitted merely because diagnostics returned `partial=true`.
- A post-cap deterministic exclusion remains excluded, and all pre-send dynamic protections remain effective.
- Preview/diagnostic partial results stay explicit and never claim a complete total.
- V-2 regression and every listed command pass.
- Changed files remain inside the Authorized Files list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/batch-filter-main/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with the resolved product commit subject `fix(batch-filter): preserve post-cap preflight candidates` recorded in this plan.
3. Appending `docs/plans/review/2026-10-14-batch-filter-main/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with the resolved evidence commit subject `docs(review-fast-p): record repair execution` recorded in this plan.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
