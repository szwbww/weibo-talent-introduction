# Repair Plan: 03-expert-mail-summary-backend

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-08-31/03-expert-mail-summary-backend.md
Verification report: review-p re-verification, 2026-08-31
Implementation boundary: a28e769 → working tree; baseline's six files plus the user-approved guard-test amendment below.

## Objective

Restore the operator-status write-seam guard without changing mailbox behavior or operator-status write paths.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P2, mandatory gate | `mvn test` must pass | Exact `NoiseSite` line pins no longer match three unchanged read-only references after this plan adds lines. |

## Findings Excluded

| Finding | Reason |
|---|---|
| Docker/Testcontainers MySQL IT | User explicitly directed review to ignore Docker. |

## Unchanged Contract

- No mail, contact, migration, or write-path behavior changes.
- `expert_contact.operator_status` write-site whitelist remains unchanged.
- The three repaired exclusions remain exact path + line + context matches; no broadening of the scanner.
- Baseline's six implementation files remain behaviorally unchanged by this repair.

## Authorized Files

| File | Purpose |
|---|---|
| src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt | Update only the three stale `NoiseSite` line numbers for existing read-only references. |

## Repair Tasks

### R-1: Re-pin read-only operator-status exclusions

- Resolves: V-1
- Root cause: the repository projection/group-by and mailbox response mapping are unchanged read paths, but their positions moved from `537/585/165` to `538/594/168`.
- Files: `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
- Change: update exactly those three `NoiseSite` line numbers; retain paths and context strings unchanged.
- Regression test: the guard must pass while its whitelist remains exactly `ExpertOperatorStatusService.kt` and `ManualInitialOutreachService.kt`.
- Existing verification: run the focused guard, baseline mailbox unit tests, then full `mvn test` with JDK 11.
- Must not change: production code, whitelist entries, scanner matching logic, or any exclusion context.
- Prohibited: adding production files, weakening the guard, adding broad exclusions, or changing Docker configuration.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=OperatorStatusWriteSeamGuardTest test`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=MailboxControllerTest,MailboxServiceTest test`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
4. `git diff --check`

## Completion Criteria

- `OperatorStatusWriteSeamGuardTest` passes with only the existing two write-site whitelist entries.
- All three exclusions exactly match their current read-only source locations.
- Full test gate passes, excluding the user-waived Docker integration evidence.
- Changed files remain inside the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
